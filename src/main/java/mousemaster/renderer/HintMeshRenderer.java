package mousemaster.renderer;

import mousemaster.qt.*;

import io.qt.core.*;
import io.qt.gui.*;
import io.qt.widgets.*;
import mousemaster.*;
import mousemaster.HintGradientColor.HintGradientArea;
import mousemaster.HintGradientColor.HintGradientStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Cross-platform Qt rendering of the hint mesh: builds and caches the per-screen hint
 * boxes, labels and shadows, and runs the transition/match animations.
 */
public final class HintMeshRenderer {

    private static final Logger logger = LoggerFactory.getLogger(HintMeshRenderer.class);

    private static final long SLOW_PAINT_MS = 5;

    private final Map<HintMesh, PixmapAndPosition> hintMeshPixmaps = new HashMap<>();
    private final Map<HintMesh, Map<List<Key>, QRect>> hintBoxGeometriesByHintMeshKey = new HashMap<>();
    private boolean hintMeshEndAnimation;
    /**
     * Building the hint window is expensive and when it is done from the keyboard hook,
     * Windows will cancel the hook and the key press will go through to the other apps.
     * Windows won't wait for the keyboard hook to return if it's taking too long.
     */
    private final Map<TransparentWindow, Runnable> setUncachedHintMeshWindowRunnables =
            new LinkedHashMap<>();
    private final Map<TransparentWindow, Runnable> cacheQtHintWindowIntoPixmapRunnables =
            new LinkedHashMap<>();
    private boolean preWarming;
    private Runnable messagePump;
    /**
     * True when the build is running from update() (deferred), meaning we are
     * NOT inside a keyboard hook callback and can safely pump messages to keep
     * the hook responsive. False when running inline from the hook callback.
     */
    private boolean pumpDuringHintBuild;
    private final Runnable hintMeshEndAnimationEndedCallback;
    private final Map<Screen, HintMeshWindow> hintMeshWindows = new LinkedHashMap<>(); // Ordered for topmost handling.
    /** The live border layer per window (drawn above the cropped content so it can move). */
    private final Map<TransparentWindow, BorderMorph> borderMorphByWindow = new HashMap<>();
    /** The in-flight container crop and the container it animates, so a grid whose boxes cannot
     *  morph clips its border layer in lockstep with it. */
    private QVariantAnimation cropAnimation;
    private long cropAnimationStartNanos;
    private QWidget croppedContainer;
    private final TransitionMetrics transitionMetrics = new TransitionMetrics();
    private boolean showingHintMesh;
    private int hideCount;
    /** Set when a crop that was zooming into a selected hint's box is abandoned because the incoming
     *  grid does not continue that drill; the incoming grid then fades in instead of popping. */
    private boolean fadeIn;
    /** A transition resuming an interrupted one; it eases out only, so the motion does not restart. */
    private boolean resumedTransition;
    private HintMesh currentHintMesh;
    private FadeAnimator hintMeshFadeAnimator;
    /** Creates a styled native window — the single platform primitive the renderer needs. */
    private final Supplier<TransparentWindow> windowFactory;

    /** A per-screen hint mesh window (no HWND — the overlay derives the native handle from
     *  {@link #window()}). Both sides share these instances, so the renderer's mutations to
     *  the lists/reference are visible to the overlay. */
    private record HintMeshWindow(TransparentWindow window,
                                 List<Hint> hints,
                                 Zoom zoom,
                                 List<QVariantAnimation> animations,
                                 List<QMetaObject.AbstractSlot> animationCallbacks,
                                 AtomicReference<HintMesh> lastHintMeshKeyReference,
                                 AtomicBoolean lastWasMatchCrop) {
    }

    /** The current border layer and its running morph, so the next transition continues from it. */
    private static final class BorderMorph {
        private HintPaintLayer layer;
        private QVariantAnimation animation;
        private QMetaObject.AbstractSlot callback; // Kept referenced so Qt does not GC it.
        private List<Rectangle> targets; // Each layer box's target rect, to settle to on interruption.
        private List<Rectangle> enclosingInk; // In window coordinates, like targets.
    }

    /** A previous grid's borders, still drawn while its content shrinks, minus where the grid that
     *  superseded it draws its own: both colors are translucent, so drawing both on the outline they
     *  share would add up their opacity. A drill interrupted more than once stacks several of these. */
    private record OutgoingBorders(List<HintBox> boxes, List<Rectangle> enclosingInk,
                                   Rectangle bounds, Rectangle covered) {
    }

    /** What one transition's crop cost. Work on the main thread drops whole frames, so the largest
     *  gap between them, not the average frame rate, is what reads as a stutter. */
    private static final class TransitionMetrics {

        private int frameCount;
        private long startNanos;
        private long previousFrameNanos;
        private long maxGapMillis;
        private long paintMaxNanos;
        private int durationMillis;
        private String clipping;
        private final StringBuilder gaps = new StringBuilder();

        void started(int durationMillis, String clipping) {
            frameCount = 0;
            maxGapMillis = 0;
            paintMaxNanos = 0;
            gaps.setLength(0);
            this.durationMillis = durationMillis;
            this.clipping = clipping;
            startNanos = System.nanoTime();
            previousFrameNanos = startNanos;
        }

        void frameDrawn() {
            long now = System.nanoTime();
            long gapMillis = (long) ((now - previousFrameNanos) / 1e6);
            previousFrameNanos = now;
            frameCount++;
            maxGapMillis = Math.max(maxGapMillis, gapMillis);
            gaps.append(gaps.isEmpty() ? "" : ",").append(gapMillis);
        }

        /** The frame's paint; what is left of its gap is Qt's and DWM's, not ours. */
        void painted(long nanos) {
            paintMaxNanos = Math.max(paintMaxNanos, nanos);
        }

        void log() {
            if (!logger.isTraceEnabled())
                return;
            long elapsedMillis = (long) ((System.nanoTime() - startNanos) / 1e6);
            logger.trace("Hint transition: " + frameCount + " frames over " + elapsedMillis +
                         "ms (" + durationMillis + "ms requested), max gap " + maxGapMillis +
                         "ms, " + clipping + ", paint " + millis(paintMaxNanos) +
                         "ms max, gaps " + gaps);
        }

        private static String millis(long nanos) {
            return String.format("%.1f", nanos / 1e6);
        }
    }

    private static Rectangle lerp(Rectangle from, Rectangle to, double t) {
        return new Rectangle(
                (int) Math.round(from.x() + (to.x() - from.x()) * t),
                (int) Math.round(from.y() + (to.y() - from.y()) * t),
                (int) Math.round(from.width() + (to.width() - from.width()) * t),
                (int) Math.round(from.height() + (to.height() - from.height()) * t));
    }

    public HintMeshRenderer(Supplier<TransparentWindow> windowFactory,
                            Runnable hintMeshEndAnimationEndedCallback) {
        this.windowFactory = windowFactory;
        this.hintMeshEndAnimationEndedCallback = hintMeshEndAnimationEndedCallback;
    }

    public void setMessagePump(Runnable messagePump) {
        this.messagePump = messagePump;
    }

    public boolean isHintMeshEndAnimation() {
        return hintMeshEndAnimation;
    }

    private void setHintMeshEndAnimation(boolean hintMeshEndAnimation) {
        this.hintMeshEndAnimation = hintMeshEndAnimation;
    }

    public boolean showing() {
        return showingHintMesh;
    }

    public boolean transitionAnimating() {
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values())
            for (QVariantAnimation animation : hintMeshWindow.animations())
                if (animation.getState() == QAbstractAnimation.State.Running)
                    return true;
        return false;
    }

    /** The Qt windows, for the platform's magnification and capture-exclusion loops. */
    public Collection<TransparentWindow> windows() {
        List<TransparentWindow> windows = new ArrayList<>();
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values())
            windows.add(hintMeshWindow.window());
        return windows;
    }

    public boolean buildPending() {
        return !setUncachedHintMeshWindowRunnables.isEmpty();
    }

    /** Runs one unit of deferred work per frame: the build this frame, the pixmap cache the
     *  next. The platform overlay drives this once per frame. */
    public void runPendingWork() {
        advanceCropAnimationToFirstFrame();
        if (hintMeshFadeAnimator != null)
            hintMeshFadeAnimator.advanceToFirstFrame();
        if (!setUncachedHintMeshWindowRunnables.isEmpty()) {
            pumpDuringHintBuild = true;
            List<Runnable> pending =
                    List.copyOf(setUncachedHintMeshWindowRunnables.values());
            setUncachedHintMeshWindowRunnables.clear();
            pending.forEach(Runnable::run);
            pumpDuringHintBuild = false;
            // Don't run the cache grab in the same tick: Qt hasn't painted
            // the window yet (processEvents runs before update in the main
            // loop). Let the next tick's processEvents paint, then grab.
        }
        else if (!cacheQtHintWindowIntoPixmapRunnables.isEmpty()) {
            List<Runnable> pending =
                    List.copyOf(cacheQtHintWindowIntoPixmapRunnables.values());
            cacheQtHintWindowIntoPixmapRunnables.clear();
            pending.forEach(Runnable::run);
        }
    }

    /** Drops pending build/cache runnables: container.grab() on a destroyed widget crashes. */
    public void cancelPendingBuild() {
        setUncachedHintMeshWindowRunnables.clear();
        cacheQtHintWindowIntoPixmapRunnables.clear();
    }

    public void flushCache() {
        for (PixmapAndPosition pixmapAndPosition : hintMeshPixmaps.values())
            pixmapAndPosition.pixmap().dispose();
        hintMeshPixmaps.clear();
        for (Map<List<Key>, QRect> hintBoxGeometries : hintBoxGeometriesByHintMeshKey.values())
            for (QRect hintBoxGeometry : hintBoxGeometries.values())
                hintBoxGeometry.dispose();
        hintBoxGeometriesByHintMeshKey.clear();
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values())
            hintMeshWindow.lastHintMeshKeyReference().set(null);
        HintLabel.clearOutlineCache();
        HintLabel.clearLabelCache();
        QtHintFont.clearCaches();
        QtColorUtil.clearCaches();
    }

    public void preWarmHintMesh(HintMesh hintMesh, Zoom zoom, Set<Screen> screens) {
        if (!hintMesh.visible() || hintMesh.hints().isEmpty())
            return;
        if (hintMeshPixmaps.containsKey(hintMeshKey(hintMesh, hintMesh.hints())))
            return;
        preWarming = true;
        try {
            createOrUpdateHintMeshWindows(hintMesh, zoom, screens);
            runPendingWork();
        }
        finally {
            preWarming = false;
        }
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values())
            hintMeshWindow.hints().clear();
    }

    public boolean setHintMesh(HintMesh hintMesh, Zoom zoom, boolean hintMatch,
                               boolean allowFade, Set<Screen> screens) {
        boolean nonMatchShown = false;
        Objects.requireNonNull(hintMesh);
        if (!hintMesh.visible()) {
            hideHintMesh();
            return false;
        }
        if (showingHintMesh && currentHintMesh != null && currentHintMesh.equals(hintMesh))
            return false;
        fadeIn = false;
        boolean wasShowing = showingHintMesh;
        // If re-showing during a fade-out, cancel the fade-out.
        if (hintMeshFadeAnimator != null && hintMeshFadeAnimator.isFadingOut()) {
            hintMeshFadeAnimator.cancelAndResetOpacity();
        }
        if (hintMesh.hints().isEmpty()) {
            currentHintMesh = hintMesh;
            setHintMeshEndAnimation(false);
            createOrUpdateHintMeshWindows(currentHintMesh, zoom, screens);
            showingHintMesh = true;
            return false;
        }
        boolean isHintGrid = hintMesh.hints().getFirst().cellWidth() != -1;
        if (hintMatch) {
            if (isHintGrid)
                setHintMeshEndAnimation(true);
            else {
                // No animation for position history hints.
                // hideHintMesh() will be called by the switch mode command.
                return false;
            }
        }
        else {
            setHintMeshEndAnimation(false);
            nonMatchShown = true;
        }
        currentHintMesh = hintMesh;
        createOrUpdateHintMeshWindows(currentHintMesh, zoom, screens);
        showingHintMesh = true;
        if (allowFade && (!wasShowing || fadeIn) && !hintMeshWindows.isEmpty()) {
            hintMeshFadeAnimator = new FadeAnimator(
                    opacity -> {
                        for (HintMeshWindow w : hintMeshWindows.values())
                            w.window().setWindowOpacity(opacity);
                    },
                    this::doHideHintMesh);
            HintMeshStyle style = currentStyle();
            if (style.fadeAnimationEnabled()) {
                for (HintMeshWindow w : hintMeshWindows.values())
                    w.window().setWindowOpacity(0.0);
                hintMeshFadeAnimator.startFadeIn(style.fadeAnimationDuration());
            }
        }
        return nonMatchShown;
    }

    public void hideHintMesh() {
        if (!showingHintMesh)
            return;
        if (isHintMeshEndAnimation())
            return;
        if (hintMeshFadeAnimator != null && !hintMeshWindows.isEmpty()) {
            HintMeshStyle style = currentStyle();
            if (style.fadeAnimationEnabled() &&
                hintMeshFadeAnimator.shouldDeferHide(style.fadeAnimationDuration()))
                return;
        }
        doHideHintMesh();
    }

    private HintMeshStyle currentStyle() {
        return currentHintMesh.styleByFilter()
                              .get(ScreenFilter.of(hintMeshWindows.keySet().iterator().next()));
    }

    private void doHideHintMesh() {
        showingHintMesh = false;
        hideCount++;
        if (hintMeshFadeAnimator != null)
            hintMeshFadeAnimator.cancel();
        // Cancel any pending build/cache runnables that reference containers
        // about to be hidden. Otherwise container.grab() in the next update()
        // would paint a destroyed widget, causing a native _purecall crash.
        cancelPendingBuild();
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values()) {
            // Stop running animations and clear callbacks before hiding children.
            // Otherwise animation callbacks (HintContainerAnimationChanged) can fire
            // container.setMask() on a widget whose C++ object has been destroyed.
            for (QVariantAnimation animation : hintMeshWindow.animations()) {
                animation.stop();
                animation.dispose();
            }
            hintMeshWindow.animations().clear();
            hintMeshWindow.animationCallbacks().clear();
            cropAnimation = null;
            BorderMorph lineMorph = borderMorphByWindow.remove(hintMeshWindow.window());
            if (lineMorph != null)
                stopBorderMorph(lineMorph);
            hintMeshWindow.window().setBackground(null, null, 0);
            hintMeshWindow.window().hideChildren();
            hintMeshWindow.window().repaint();
            // Reset opacity after hiding so the window is ready for reuse.
            hintMeshWindow.window().setWindowOpacity(1.0);
        }
    }

    private void createOrUpdateHintMeshWindows(HintMesh hintMesh, Zoom zoom,
                                               Set<Screen> screens) {
        Map<Screen, List<Hint>> hintsByScreen = hintsByScreen(hintMesh.hints(), screens);
        if (hintsByScreen.isEmpty() && hintMesh.backgroundArea() != null) {
            Rectangle backgroundArea = hintMesh.backgroundArea();
            for (Screen screen : screens) {
                if (!screen.rectangle().intersection(backgroundArea).isEmpty())
                    hintsByScreen.put(screen, List.of());
            }
        }
        for (Map.Entry<Screen, HintMeshWindow> entry : hintMeshWindows.entrySet()) {
            Screen screen = entry.getKey();
            HintMeshWindow window = entry.getValue();
            if (!hintsByScreen.containsKey(screen))
                window.hints().clear();
        }
        for (Map.Entry<Screen, List<Hint>> entry : hintsByScreen.entrySet()) {
            Screen screen = entry.getKey();
            HintMeshStyle style =
                    hintMesh.styleByFilter().get(ScreenFilter.of(screen));
            List<Hint> hintsInScreen = entry.getValue();
            HintMeshWindow existingWindow = hintMeshWindows.get(screen);
            if (existingWindow == null) {
                HintMeshWindow hintMeshWindow =
                        createHintMeshWindow(screen, hintsInScreen, zoom);
                hintMeshWindows.put(screen, hintMeshWindow);
                setHintMeshWindow(hintMeshWindow, hintMesh,
                        screen.scale(), style, false);
            }
            else {
                HintMeshWindow hintMeshWindow = new HintMeshWindow(existingWindow.window(),
                        hintsInScreen, zoom, existingWindow.animations(),
                        existingWindow.animationCallbacks(),
                        existingWindow.lastHintMeshKeyReference(),
                        existingWindow.lastWasMatchCrop());
                boolean zoomChanged = existingWindow.zoom() == null || !existingWindow.zoom().equals(zoom);
                hintMeshWindows.put(screen, hintMeshWindow);
//                TransparentWindow window = existingWindow.window;
//                logger.debug("Showing hints " + hintsInScreen.size() + " for " + screen + ", window = " + existingWindow.window.x() + " " + existingWindow.window.y() + " " + existingWindow.window.width() + " " + existingWindow.window.height());
                setHintMeshWindow(hintMeshWindow, hintMesh,
                        screen.scale(), style, zoomChanged);
            }
        }
    }

    private Map<Screen, List<Hint>> hintsByScreen(List<Hint> hints, Set<Screen> screens) {
        Map<Screen, List<Hint>> hintsByScreen = new HashMap<>();
        for (Hint hint : hints) {
            for (Screen screen : screens) {
                if (hint.cellWidth() == -1) {
                    if (!screen.rectangle().contains((int) hint.centerX(), (int) hint.centerY()))
                        continue;
                }
                else {
                    int left = (int) Math.ceil(hint.centerX() - hint.cellWidth() / 2);
                    int right = (int) Math.floor(hint.centerX() + hint.cellWidth() / 2);
                    int top = (int) Math.ceil(hint.centerY() - hint.cellHeight() / 2);
                    int bottom = (int) Math.floor(hint.centerY() + hint.cellHeight() / 2);
                    if (left == screen.rectangle().x() + screen.rectangle().width() ||
                        right == screen.rectangle().x() ||
                        top == screen.rectangle().y() + screen.rectangle().height() ||
                        bottom == screen.rectangle().y())
                        // Assuming two screens: left and right, with right screen
                        // at x = 1024. Hint's left is 1024.
                        // Hint should be on second screen, not on left screen.
                        continue;
                    if (!screen.rectangle().contains(left, top) &&
                        !screen.rectangle().contains(right, top) &&
                        !screen.rectangle().contains(left, bottom) &&
                        !screen.rectangle().contains(right, bottom))
                        continue;
                }
                hintsByScreen.computeIfAbsent(screen, screen1 -> new ArrayList<>())
                              .add(hint);
                break;
            }
        }
        return hintsByScreen;
    }

    private HintMeshWindow createHintMeshWindow(Screen screen, List<Hint> hints, Zoom zoom) {
        TransparentWindow window = windowFactory.get();
        window.coverInPixels(screen);
        return new HintMeshWindow(window, hints, zoom,
                new ArrayList<>(), new ArrayList<>(), new AtomicReference<>(),
                new AtomicBoolean());
    }

    public void preWarmHintMeshWindows(Set<Screen> screens) {
        long before = System.nanoTime();
        for (Screen screen : screens) {
            if (hintMeshWindows.containsKey(screen))
                continue;
            HintMeshWindow hintMeshWindow =
                    createHintMeshWindow(screen, new ArrayList<>(), null);
            // A screen-sized translucent window costs Qt its native surface on the first show,
            // which the first hints would otherwise pay. It holds nothing to display yet.
            hintMeshWindow.window.show();
            hintMeshWindow.window.hide();
            hintMeshWindows.put(screen, hintMeshWindow);
        }
        logger.debug("Pre-warmed hint mesh windows for " + screens.size() +
                " screens in " + (long) ((System.nanoTime() - before) / 1e6) + "ms");
    }

    private class ClearBackgroundQLabel extends QLabel {
        /** Built in its final parent: adopting a populated container costs as much as showing it. */
        ClearBackgroundQLabel(QWidget parent) {
            super(parent);
        }

        private QColor clearColor = new QColor(0, 0, 0, 0);
        private Rectangle clearRect = new Rectangle(0, 0, 0, 0);
        private int clearRadius;
        /** Crop region, clipped in paintEvent instead of via setMask (which recomposites the whole
         *  window). Null paints the full pixmap. */
        private QRect crop;

        void setClearColor(QColor clearColor, Rectangle clearRect, int clearRadius) {
            if (this.clearColor != null)
                this.clearColor.dispose();
            this.clearColor = clearColor;
            this.clearRect = clearRect;
            this.clearRadius = clearRadius;
        }

        boolean cropCapable() {
            return pixmap() != null && !pixmap().isNull();
        }

        /** Crops to {@code r}, scheduling a paint of the band between the old and new crop (the whole
         *  label on the first crop, to clear any full paint before it). Uses update(), not repaint():
         *  repaint() flushes immediately, so a container's CompositionMode_Clear would reach the
         *  screen before the border layer repaints on top, blanking the border lines for a frame.
         *  update() coalesces the container and border paints into one composite (border on top). */
        void setCrop(QRect r) {
            QRect newCrop = new QRect(r);
            QRect dirty = crop != null ? newCrop.united(crop) : rect();
            if (crop != null)
                crop.dispose();
            crop = newCrop;
            update(dirty);
            dirty.dispose();
        }

        /** Drops the crop so the full pixmap shows again. */
        void clearCrop() {
            if (crop == null)
                return;
            crop.dispose();
            crop = null;
            repaint();
        }

        @Override
        protected void paintEvent(QPaintEvent event) {
            long before = System.nanoTime();
            paint(event);
            transitionMetrics.painted(System.nanoTime() - before);
        }

        private void paint(QPaintEvent event) {
            if (crop != null) {
                QPainter painter = new QPainter(this);
                painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Clear);
                painter.fillRect(event.rect(), clearColor);
                QRect drawRegion = crop.intersected(event.rect());
                if (!drawRegion.isEmpty()) {
                    painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
                    // The grab is in pixels, the label in points: the target is the whole label.
                    painter.setClipRect(drawRegion);
                    QRect r = rect();
                    painter.drawPixmap(r, pixmap());
                    r.dispose();
                }
                drawRegion.dispose();
                painter.end();
                painter.dispose();
                return;
            }
            QPainter painter = new QPainter(this);
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Clear);
            // Clear what's behind (when we're drawing the old container behind).
            QRect r = rect();
            painter.fillRect(r, clearColor);
            r.dispose();
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Source);
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, clearRadius > 0);
            painter.setPen(Qt.PenStyle.NoPen);
            painter.setBrush(QtColorUtil.qBrush(clearColor));
            painter.drawRoundedRect(clearRect.x() - x(), clearRect.y() - y(),
                    clearRect.width(), clearRect.height(), clearRadius, clearRadius);
            painter.end();
            painter.dispose();
            super.paintEvent(event);
        }
    }

    public void setHintMeshWindow(HintMeshWindow hintMeshWindow,
                                  HintMesh hintMesh, double screenScale,
                                  HintMeshStyle style,
                                  boolean zoomChanged) {
        setHintMeshWindow(hintMeshWindow, hintMesh, screenScale, style, zoomChanged, null);
    }

    private void setHintMeshWindow(HintMeshWindow hintMeshWindow,
                                          HintMesh hintMesh, double screenScale,
                                          HintMeshStyle style,
                                          boolean zoomChanged,
                                          PixmapAndPosition matchBoxPixmap) {
        TransparentWindow window = hintMeshWindow.window;
        cacheQtHintWindowIntoPixmapRunnables.remove(window);
        setUncachedHintMeshWindowRunnables.remove(window);
        // A match crop zooms into a just-typed hint's box (animateHintMatch seeds it with that box's
        // screenshot); it plus the content render that follows form a drill. Was this render one, or the last?
        boolean matchCropBefore = hintMeshWindow.lastWasMatchCrop.get();
        boolean matchCropNow = matchBoxPixmap != null;
        hintMeshWindow.lastWasMatchCrop.set(matchCropNow);
        // The new animation inherits the interrupted one's velocity by finishing in the time that one
        // had left (its own remaining duration, which is already shortened if it too was interrupted).
        Duration transitionAnimationDuration =
                hintMeshWindow.animations.stream()
                                         .filter(animation -> animation.getState() ==
                                                              QAbstractAnimation.State.Running)
                                         .map(animation -> Duration.ofMillis(Math.max(1,
                                                 animation.getDuration() - animation.getCurrentTime())))
                                         .findFirst()
                                         .orElse(style.transitionAnimationDuration());
        boolean cropAnimating = hintMeshWindow.animations.stream()
                .anyMatch(animation -> animation.getState() == QAbstractAnimation.State.Running);
        for (QVariantAnimation animation : hintMeshWindow.animations)
            animation.stop();
        List<QWidget> interruptedContainers = containers(window);
        // A drill continues from the current extent toward a same-sized or contained target. A prefix
        // select/deselect continues whenever the new mesh nests with the target either way (growing back
        // out or shrinking back in). A same-sized change (recolor, fresh grid) resolves instantly, unless
        // a crop is still animating — then it continues too, so a mid-crop recolor is not cut short.
        boolean partOfDrill = matchCropBefore || matchCropNow;
        boolean coversTarget = coversInProgressTarget(hintMeshWindow, hintMesh, interruptedContainers);
        boolean targetContainsNew = targetContainsNewMesh(hintMeshWindow, hintMesh, interruptedContainers);
        boolean newContainsTarget = newMeshContainsTarget(hintMeshWindow, hintMesh, interruptedContainers);
        boolean continueFromCurrentExtent = partOfDrill
                ? coversTarget || targetContainsNew
                : showingHintMesh && (newContainsTarget || targetContainsNew) && (!coversTarget || cropAnimating);
        resumedTransition = continueFromCurrentExtent;
        if (matchCropBefore && !continueFromCurrentExtent) {
            // Fresh grid after a match crop (different target): drop the crop and its morph so the new
            // mesh fades in instead of morphing from the selected cell.
            for (QWidget container : interruptedContainers) {
                container.setParent(null);
                disposeWidget(container);
            }
            BorderMorph morph = borderMorphByWindow.get(window);
            if (morph != null)
                stopBorderMorph(morph);
            fadeIn = true;
        }
        else if (continueFromCurrentExtent) {
            // Consolidate multiple old containers (a mid-drill interruption) into one at their union
            // visible extent, so the next transition has a single "old" to grow/shrink from. A lone
            // container is left as is, keeping its clip-capable pixmap.
            if (interruptedContainers.size() >= 2) {
                int mergedX = Integer.MAX_VALUE, mergedY = Integer.MAX_VALUE;
                int mergedRight = Integer.MIN_VALUE, mergedBottom = Integer.MIN_VALUE;
                for (QWidget container : interruptedContainers) {
                    container.setParent(null);
                    QRect visible = visibleRect(container);
                    mergedX = Math.min(mergedX, visible.x());
                    mergedY = Math.min(mergedY, visible.y());
                    mergedRight = Math.max(mergedRight, visible.x() + visible.width());
                    mergedBottom = Math.max(mergedBottom, visible.y() + visible.height());
                    visible.dispose();
                }
                QWidget mergedContainer = new QWidget(window);
                mergedContainer.setGeometry(mergedX, mergedY,
                        mergedRight - mergedX, mergedBottom - mergedY);
                for (QWidget container : interruptedContainers) {
                    container.move(container.x() - mergedX, container.y() - mergedY);
                    container.setParent(mergedContainer);
                }
                mergedContainer.show();
            }
        }
        else {
            // Insta-finish: keep the newest container fully shown at its target, drop the rest.
            for (int i = 0; i < interruptedContainers.size() - 1; i++) {
                interruptedContainers.get(i).setParent(null);
                disposeWidget(interruptedContainers.get(i));
            }
            if (!interruptedContainers.isEmpty()) {
                QWidget lastContainer = interruptedContainers.getLast();
                if (lastContainer instanceof ClearBackgroundQLabel label && label.cropCapable())
                    label.clearCrop();
                else
                    lastContainer.clearMask();
            }
            // Settle the border morph to its target too, so the boxes start the reversal from where
            // the crop does.
            BorderMorph morph = borderMorphByWindow.get(window);
            if (morph != null && morph.layer != null && morph.targets != null)
                for (int i = 0; i < morph.layer.boxes.size(); i++)
                    morph.layer.boxes.get(i).setGeometry(morph.targets.get(i));
        }
        for (QVariantAnimation animation : hintMeshWindow.animations)
            animation.dispose();
        hintMeshWindow.animations.clear();
        hintMeshWindow.animationCallbacks.clear();
        cropAnimation = null;
        double qtScaleFactor = qtScaleFactor(screenScale);
        List<QWidget> oldContainers = containers(window);
        QWidget oldContainer = oldContainers.isEmpty() ? null : oldContainers.getFirst();
        boolean oldContainerHidden = oldContainer == null || oldContainer.isHidden();
        window.clearWindow();
        // Compute background color for both the window and the container clear.
        Rectangle backgroundArea = hintMesh.backgroundArea();
        QColor backgroundColor = backgroundArea != null && style.backgroundOpacity() > 0 ?
                QtColorUtil.qColor(style.backgroundHexColor(), style.backgroundOpacity()) : null;
        Rectangle backgroundRect = backgroundColor == null ? null :
                backgroundRect(window, backgroundArea, qtScaleFactor);
        int backgroundRadius =
                scaledLength(style.backgroundBorderRadius(), screenScale / qtScaleFactor);
        if (hintMeshWindow.hints().isEmpty()) {
            showBackground(window, backgroundColor, backgroundRect, backgroundRadius);
            QWidget container = new QWidget(window);
            container.setGeometry(0, 0, 0, 0);
            container.show();
            window.show();
            return;
        }
        HintMesh hintMeshKey = matchBoxPixmap != null ? null :
                hintMeshKey(hintMesh, hintMeshWindow.hints());
        hintMeshWindow.lastHintMeshKeyReference.set(hintMeshKey); // Will be used by animateHintMatch.
        PixmapAndPosition pixmapAndPosition =
                matchBoxPixmap != null ? matchBoxPixmap :
                        hintMeshPixmaps.get(hintMeshKey);
        boolean isHintGrid = hintMeshWindow.hints().getFirst().cellWidth() != -1;
        QWidget newContainer;
        if (pixmapAndPosition != null) {
            logger.trace("Using cached hint mesh pixmap " + pixmapAndPosition);
            ClearBackgroundQLabel pixmapLabel = new ClearBackgroundQLabel(window);
            pixmapLabel.setPixmap(pixmapAndPosition.pixmap);
            Hint originalFirstHint = pixmapAndPosition.originalHintMesh.hints().getFirst();
            int originalWindowX = pixmapAndPosition.windowX;
            int originalWindowY = pixmapAndPosition.windowY;
            Hint newFirstHint = hintMesh.hints().getFirst();
            // Translate the original pixmap which may be at a different position than
            // the new hint mesh.
            // Hints are in pixels, the label and the window in points. The grab is in the pixels
            // of the screen it was taken on, which is not always this one: a mesh is cached by
            // its hints alone, and a screen of another scale grabs it at another ratio.
            double grabScale = pixmapAndPosition.pixmap().devicePixelRatio();
            pixmapLabel.setGeometry(pixmapAndPosition.x() + (int) Math.round(
                            (newFirstHint.centerX() - originalFirstHint.centerX()) / qtScaleFactor
                            - window.x() + originalWindowX),
                    pixmapAndPosition.y() + (int) Math.round(
                            (newFirstHint.centerY() - originalFirstHint.centerY()) / qtScaleFactor
                            - window.y() + originalWindowY),
                    (int) Math.round(pixmapAndPosition.pixmap().width() / grabScale),
                    (int) Math.round(pixmapAndPosition.pixmap().height() / grabScale));
            pixmapLabel.setScaledContents(true);
            newContainer = pixmapLabel;
            boolean animateTransition = style.transitionAnimationEnabled() && isHintGrid && !oldContainerHidden && !zoomChanged;
            showBackground(window, backgroundColor, backgroundRect, backgroundRadius);
            transitionHintContainers(animateTransition, oldContainer, newContainer,
                    window, hintMeshWindow, transitionAnimationDuration);
            if (pixmapAndPosition.boxes() != null)
                morphBorders(window, hintMeshWindow, pixmapLabel, pixmapAndPosition.boxes(),
                        pixmapAndPosition.enclosingInk(), animateTransition,
                        transitionAnimationDuration);
            else
                // A match crop carries no boxes, so morphBorders is skipped; clip the old border
                // layer in lockstep so the starting grid's borders shrink into the selected box too.
                clipMatchCropBorderLayer(window, hintMeshWindow);
        }
        else {
            // Uses ClearBackgroundQLabel because when in the mergedContainer,
            // the top-level container must override the container below.
            ClearBackgroundQLabel container = new ClearBackgroundQLabel(window);
            if (backgroundRect != null)
                container.setClearColor(backgroundColor, backgroundRect, backgroundRadius);
            container.setStyleSheet("background: transparent;");
            newContainer = container;
            int hideCountBeforeBuild = hideCount;
            Runnable setUncachedHintMeshWindowRunnable =
                    () -> {
                        long before = System.nanoTime();
                        List<Rectangle> enclosingInk = new ArrayList<>();
                        List<HintBox> boxes =
                                setUncachedHintMeshWindow(hintMeshWindow, hintMeshKey, hintMesh,
                                        screenScale, style, qtScaleFactor, container, enclosingInk);
                        if (!preWarming)
                            logger.debug("Built hint mesh window in " +
                                    (long) ((System.nanoTime() - before) / 1e6) + "ms");
                        if (preWarming) {
                            cacheQtHintWindowIntoPixmap(window, container, hintMeshKey, hintMesh,
                                    boxes, enclosingInk);
                            container.setParent(null);
                            disposeWidget(container);
                            return;
                        }
                        // A deferred build pumps messages, so the mesh may have been hidden
                        // while it ran.
                        if (hideCount != hideCountBeforeBuild) {
                            container.setParent(null);
                            disposeWidget(container);
                            return;
                        }
                        boolean animateTransition = style.transitionAnimationEnabled() && isHintGrid && !oldContainerHidden && !zoomChanged;
                        showBackground(window, backgroundColor, backgroundRect, backgroundRadius);
                        transitionHintContainers(animateTransition,
                                oldContainer, newContainer,
                                window, hintMeshWindow, transitionAnimationDuration);
                        // Non-grid hints draw their own borders and only need the window shown.
                        morphBorders(window, hintMeshWindow, container, boxes, enclosingInk,
                                animateTransition, transitionAnimationDuration);
                        // The mesh is on screen a frame sooner than waiting for the next pump.
                        if (!isHintGrid && messagePump != null)
                            messagePump.run();
                        if (isHintGrid) {
                            // Defer the pixmap cache grab to the next frame so the hint mesh is shown
                            // immediately; the grab is expensive (~90ms at 4K when cold).
                            cacheQtHintWindowIntoPixmapRunnables.put(window, () ->
                                cacheQtHintWindowIntoPixmap(window, container, hintMeshKey, hintMesh, boxes, enclosingInk));
                        }
                    };
            // Run immediately when hints are already visible (to avoid a
            // blank frame), or when the build is expected to be fast.
            // Defer the expensive initial build to update() where we can
            // pump messages without being inside the keyboard hook callback.
            if (!oldContainerHidden
                    || !hintMesh.selectedKeySequence().isEmpty()
                    || hintMesh.hints().size() < 100
            ) {
                setUncachedHintMeshWindowRunnable.run();
            }
            else {
                setUncachedHintMeshWindowRunnables.put(window,
                        setUncachedHintMeshWindowRunnable);
            }
        }
    }

    private static Rectangle backgroundRect(TransparentWindow window, Rectangle backgroundArea,
                                            double qtScaleFactor) {
        int backgroundX = (int) Math.round(
                (backgroundArea.x() - window.xInPixels()) / qtScaleFactor);
        int backgroundY = (int) Math.round(
                (backgroundArea.y() - window.yInPixels()) / qtScaleFactor);
        int left = Math.max(0, backgroundX);
        int top = Math.max(0, backgroundY);
        int right = Math.min(window.width(),
                backgroundX + (int) Math.round(backgroundArea.width() / qtScaleFactor));
        int bottom = Math.min(window.height(),
                backgroundY + (int) Math.round(backgroundArea.height() / qtScaleFactor));
        return right > left && bottom > top ?
                new Rectangle(left, top, right - left, bottom - top) : null;
    }

    private static void showBackground(TransparentWindow window, QColor backgroundColor,
                                       Rectangle backgroundRect, int backgroundRadius) {
        if (backgroundRect == null) {
            window.setBackground(null, null, 0);
            return;
        }
        QRect rect = new QRect(backgroundRect.x(), backgroundRect.y(),
                backgroundRect.width(), backgroundRect.height());
        window.setBackground(backgroundColor, rect, backgroundRadius);
        window.update(rect);
    }

    private void transitionHintContainers(boolean animateTransition, QWidget oldContainer,
                                                 QWidget newContainer, TransparentWindow window,
                                                 HintMeshWindow hintMeshWindow,
                                                 Duration animationDuration) {
        // TODO Should use .geometry() instead of .rect() which is relative to the widget
        //  itself, where geometry() is relative to the parent.
        cropAnimation = null;
        croppedContainer = null;
        if (oldContainer != null) {
            QRect oldRect = oldContainer.rect();
            QRect newRect = newContainer.rect();
            boolean containersEqual = oldRect.equals(newRect);
            QRect paddedOld = paddedRect(oldRect);
            boolean oldContainsNew = paddedOld.contains(newRect);
            paddedOld.dispose();
            QRect paddedNew = paddedRect(newRect);
            boolean newContainsOld = paddedNew.contains(oldRect);
            paddedNew.dispose();
            // A crop leaves the container its full size, so a same-sized change (a recolor) lands on
            // the instant swap below even mid-crop. The new container continues the crop instead, from
            // what is visible of the old one, so the change does not cut a growing crop short.
            QRect oldVisibleRect = visibleRect(oldContainer);
            boolean continueCropIntoNew = containersEqual
                                          && (oldVisibleRect.width() < oldRect.width()
                                              || oldVisibleRect.height() < oldRect.height());
            oldVisibleRect.dispose();
            oldRect.dispose();
            newRect.dispose();
            if (animateTransition && oldContainsNew && !continueCropIntoNew) {
                // Shrink old container until it reaches the position and size of new.
                oldContainer.setParent(window);
                oldContainer.show();
                // Re-attaching the old container placed it above the new one, already a child.
                newContainer.raise();
                newContainer.show();
                if (containersEqual) {
                    // Same-size swap (a color change, or a screen-selection hint end): the new
                    // container is already on top. Keep the old one beneath it until it is deleted
                    // (no detach), so it backs a still-painting uncached container instead of leaving
                    // a blank frame.
                    disposeWidget(oldContainer);
                    hintContainerAnimationEnded();
                }
                else {
                    // Start from the old container's visible (cropped) extent, not its full geometry,
                    // so a shrink that continues an interrupted crop resumes from where it is.
                    QRect oldVisible = visibleRect(oldContainer);
                    QRect beginRect =
                            new QRect(oldVisible.x() - oldContainer.x(),
                                    oldVisible.y() - oldContainer.y(),
                                    oldVisible.width(),
                                    oldVisible.height());
                    oldVisible.dispose();
                    QRect endRect =
                            new QRect(newContainer.x() - oldContainer.x(),
                                    newContainer.y() - oldContainer.y(),
                                    newContainer.width(),
                                    newContainer.height());
                    cropOrMask(oldContainer, beginRect);
                    QVariantAnimation animation =
                            hintContainerAnimation(beginRect, endRect, animationDuration);
                    beginRect.dispose();
                    HintContainerAnimationChanged animationChanged = new HintContainerAnimationChanged(
                            oldContainer, this);
                    animation.valueChanged.connect(animationChanged);
                    HintContainerAnimationFinished animationFinished =
                            new HintContainerAnimationFinished(oldContainer, oldContainer,
                                    endRect, this);
                    animation.finished.connect(animationFinished);
                    // It may be necessary to save those instances somewhere (HintMeshWindow),
                    // because they could get GC'd while they are still used by Qt (?).
                    // Same for HintContainerAnimationFinished.
                    hintMeshWindow.animations.add(animation);
                    hintMeshWindow.animationCallbacks.add(animationChanged);
                    hintMeshWindow.animationCallbacks.add(animationFinished);
                    cropAnimation = animation;
                    croppedContainer = oldContainer;
                    startCropAnimation(animation, oldContainer);
                }
            }
            else if (animateTransition && (newContainsOld || continueCropIntoNew)) {
                // Initially show new container with the position and size of old.
                // Then grow new container until it reaches its final position and size.
                newContainer.show();
                // Start from the old container's visible (cropped) extent, not its full geometry, so a
                // mid-animation reversal grows from where it is instead of snapping.
                QRect oldVisible = visibleRect(oldContainer);
                QRect beginRect =
                        new QRect(oldVisible.x() - newContainer.x(),
                                oldVisible.y() - newContainer.y(),
                                oldVisible.width(),
                                oldVisible.height());
                oldVisible.dispose();
                QRect endRect =
                        new QRect(0, 0,
                                newContainer.width(), newContainer.height());
                cropOrMask(newContainer, beginRect);
                QVariantAnimation animation = hintContainerAnimation(beginRect, endRect,
                        animationDuration);
                beginRect.dispose();
                HintContainerAnimationChanged animationChanged =
                        new HintContainerAnimationChanged(newContainer, this);
                animation.valueChanged.connect(animationChanged);
                HintContainerAnimationFinished animationFinished =
                        new HintContainerAnimationFinished(null, newContainer,
                                endRect, this);
                animation.finished.connect(animationFinished);
                hintMeshWindow.animations.add(animation);
                hintMeshWindow.animationCallbacks.add(animationChanged);
                hintMeshWindow.animationCallbacks.add(animationFinished);
                cropAnimation = animation;
                croppedContainer = newContainer;
                startCropAnimation(animation, newContainer);
                oldContainer.setParent(null);
                disposeWidget(oldContainer);
            }
            else {
                oldContainer.setParent(null);
                disposeWidget(oldContainer);
                newContainer.show();
                hintContainerAnimationEnded();
            }
        }
        else {
            newContainer.show();
        }
        window.show();
    }

    /** Draws the new grid's borders on a live layer above the cropped content. If the boxes map to the
     *  old grid's and move, it morphs them there; otherwise it clips the layer in lockstep with the crop. */
    private void morphBorders(TransparentWindow window, HintMeshWindow hintMeshWindow,
                            QWidget contentContainer,
                            List<HintBox> newBoxes, List<Rectangle> enclosingInk,
                            boolean animateTransition, Duration duration) {
        BorderMorph morph = borderMorphByWindow.computeIfAbsent(window, w -> new BorderMorph());
        boolean isHintGrid = hintMeshWindow.hints().getFirst().cellWidth() != -1;
        if (!isHintGrid) {
            stopBorderMorph(morph);
            window.show();
            return;
        }
        QRect containerGeometry = contentContainer.geometry();
        int originX = containerGeometry.x(), originY = containerGeometry.y();
        containerGeometry.dispose();
        // Where each old border sits now (window coords), so its box resumes from there without a jump.
        Map<List<Key>, Rectangle> startByKey = new HashMap<>();
        if (animateTransition && morph.layer != null)
            for (HintBox oldBox : morph.layer.boxes)
                startByKey.put(oldBox.hint.keySequence(), oldBox.rectangle());
        int boxCount = newBoxes.size();
        List<Rectangle> starts = new ArrayList<>(boxCount);
        List<Rectangle> targets = new ArrayList<>(boxCount);
        boolean boxesMove = false;
        for (HintBox box : newBoxes) {
            Rectangle target = new Rectangle(box.x() + originX, box.y() + originY, box.width(), box.height());
            Rectangle start = startByKey.getOrDefault(box.hint.keySequence(), target);
            targets.add(target);
            starts.add(start);
            boxesMove |= !start.equals(target);
        }
        // The boxes cannot morph (no matching old borders, or none moved): the borders follow their
        // content instead, clipped to the crop the way their content is.
        boolean growCrop = cropAnimation != null && croppedContainer == contentContainer;
        List<OutgoingBorders> outgoing = List.of();
        if (!boxesMove && cropAnimation != null && !growCrop && morph.layer != null) {
            // Carry over the borders of every grid whose content is still shrinking, dropping the ones
            // this grid covers: it draws their area itself now, and backing out of a drill returns to a
            // grid that is in the list, whose borders would then be drawn twice.
            Rectangle newBounds = Rectangle.union(targets);
            Rectangle previousBounds = Rectangle.union(morph.targets);
            outgoing = new ArrayList<>();
            for (OutgoingBorders previous : morph.layer.outgoing)
                if (!newBounds.contains(previous.bounds()))
                    outgoing.add(previous);
            if (!newBounds.contains(previousBounds))
                outgoing.add(new OutgoingBorders(morph.layer.boxes, morph.enclosingInk,
                        previousBounds, newBounds));
        }
        boolean lockstepCrop =
                !boxesMove && cropAnimation != null && (growCrop || !outgoing.isEmpty());
        stopBorderMorph(morph);
        // The layer animates its own copies, so the container's boxes keep their layout geometry.
        List<HintBox> borderBoxes = new ArrayList<>(boxCount);
        for (int i = 0; i < boxCount; i++) {
            HintBox copy = new HintBox(newBoxes.get(i));
            copy.setGeometry(boxesMove ? starts.get(i) : targets.get(i));
            borderBoxes.add(copy);
        }
        List<Rectangle> windowInk = new ArrayList<>(enclosingInk.size());
        for (Rectangle rectangle : enclosingInk)
            windowInk.add(new Rectangle(rectangle.x() + originX, rectangle.y() + originY,
                    rectangle.width(), rectangle.height()));
        HintPaintLayer layer = new HintPaintLayer(window, borderBoxes, List.of(), HintBox::paintBorder);
        layer.setGeometry(0, 0, window.width(), window.height());
        layer.setEnclosingInk(windowInk);
        layer.raise();
        layer.show();
        morph.layer = layer;
        morph.targets = targets;
        morph.enclosingInk = windowInk;
        window.show();
        if (lockstepCrop) {
            QMetaObject.Slot0 finish;
            if (growCrop) {
                followContentCrop(layer::setCrop, hintMeshWindow);
                finish = layer::clearCrop;
            }
            else {
                layer.setOutgoing(outgoing);
                followContentCrop(layer::setOutgoingCrop, hintMeshWindow);
                finish = () -> layer.setOutgoing(List.of());
            }
            cropAnimation.finished.connect(finish);
            hintMeshWindow.animationCallbacks.add(finish);
            return;
        }
        if (!boxesMove)
            return;
        // Region the borders travel through, so each frame repaints only that area.
        List<Rectangle> startsAndTargets = new ArrayList<>(starts);
        startsAndTargets.addAll(targets);
        Rectangle dirty = Rectangle.union(startsAndTargets);
        QVariantAnimation animation = new QVariantAnimation();
        // Same duration as the crop, so the morph stays in lockstep with it.
        animation.setDuration(Math.max(1, (int) duration.toMillis()));
        animation.setStartValue(0d);
        animation.setEndValue(1d);
        animation.setEasingCurve(resumedTransition ? QEasingCurve.Type.OutQuad :
                QEasingCurve.Type.InOutQuad);
        QMetaObject.Slot1<Object> callback = value -> {
            double progress = (Double) value;
            for (int i = 0; i < boxCount; i++)
                borderBoxes.get(i).setGeometry(lerp(starts.get(i), targets.get(i), progress));
            layer.update(dirty.x(), dirty.y(), dirty.width(), dirty.height());
        };
        animation.valueChanged.connect(callback);
        morph.animation = animation;
        morph.callback = callback;
        animation.start();
    }

    /** Hands the cropped container's visible region, in window coordinates, to {@code clip} each frame.
     *  Read from the container, not rebuilt from the animation's rects, so the borders clip to exactly
     *  what their content shows even when the crop animates a container consolidated from an
     *  interrupted drill, whose extent and position are not the grid's. The container is cropped first,
     *  its animation callback being connected first. */
    private void followContentCrop(Consumer<QRect> clip, HintMeshWindow hintMeshWindow) {
        QWidget container = croppedContainer;
        Runnable clipToVisible = () -> {
            QRect visible = visibleRect(container);
            clip.accept(visible);
            visible.dispose();
        };
        clipToVisible.run();
        QMetaObject.Slot1<Object> follow = value -> clipToVisible.run();
        cropAnimation.valueChanged.connect(follow);
        hintMeshWindow.animationCallbacks.add(follow);
    }

    /** A match crop skips morphBorders, so re-attach the (clearWindow-detached) border layer and clip
     *  it to the crop here, else the starting grid's borders vanish instead of shrinking into the
     *  selected box. hideHintMesh drops the layer when the crop ends. */
    private void clipMatchCropBorderLayer(TransparentWindow window, HintMeshWindow hintMeshWindow) {
        BorderMorph morph = borderMorphByWindow.get(window);
        if (morph == null || morph.layer == null || cropAnimation == null)
            return;
        morph.layer.setParent(window);
        morph.layer.raise();
        morph.layer.show();
        // A transition interrupted by the match leaves the previous grids' borders still shrinking:
        // they follow this crop too, so they keep shrinking into the selected box instead of vanishing.
        followContentCrop(morph.layer::clipAll, hintMeshWindow);
    }

    private void stopBorderMorph(BorderMorph morph) {
        if (morph.animation != null) {
            morph.animation.stop();
            QtDisposal.disposeLater(morph.animation);
            morph.animation = null;
        }
        morph.callback = null;
        if (morph.layer != null) {
            morph.layer.setParent(null);
            disposeWidget(morph.layer);
            morph.layer = null;
        }
    }

    /** Window children that hold rendered content — everything but the live border layer. */
    /** A shadow pixmap is a screen sized buffer that its widget does not own, so destroying
     *  the widget leaves it behind. */
    private static void disposeWidget(QWidget widget) {
        if (widget instanceof HintPaintLayer layer)
            layer.disposeBuffers();
        for (HintPaintLayer layer : widget.findChildren(HintPaintLayer.class))
            layer.disposeBuffers();
        QtDisposal.disposeLater(widget);
    }

    private List<QWidget> containers(TransparentWindow window) {
        List<QWidget> result = new ArrayList<>();
        for (QObject child : window.children())
            if (child instanceof QWidget widget && !(widget instanceof HintPaintLayer))
                result.add(widget);
        return result;
    }

    /** Whether the incoming mesh covers about the same extent as the in-progress crop's target (its
     *  newest container), i.e. it drills into the same cell and should continue the crop rather than
     *  replace it with a fresh grid. */
    private boolean coversInProgressTarget(HintMeshWindow hintMeshWindow, HintMesh hintMesh, List<QWidget> containers) {
        if (containers.isEmpty() || hintMeshWindow.hints().isEmpty())
            return false;
        QRect inProgressTarget = containers.getLast().geometry();
        QRect bounds = newMeshBounds(hintMeshWindow, hintMesh);
        boolean same = closeInSize(inProgressTarget.width(), bounds.width()) &&
                       closeInSize(inProgressTarget.height(), bounds.height());
        inProgressTarget.dispose();
        bounds.dispose();
        return same;
    }

    /** Whether the crop's target contains the incoming mesh: a drill going deeper (not a larger fresh
     *  grid after a click), so the crop keeps going toward it. */
    private boolean targetContainsNewMesh(HintMeshWindow hintMeshWindow, HintMesh hintMesh, List<QWidget> containers) {
        if (containers.isEmpty() || hintMeshWindow.hints().isEmpty())
            return false;
        QRect target = containers.getLast().geometry();
        QRect paddedTarget = paddedRect(target);
        QRect bounds = newMeshBounds(hintMeshWindow, hintMesh);
        boolean contains = paddedTarget.contains(bounds);
        target.dispose();
        paddedTarget.dispose();
        bounds.dispose();
        return contains;
    }

    /** Whether the incoming mesh contains the target: going back out (a reversal), so the crop grows
     *  toward the new mesh instead of insta-finishing. */
    private boolean newMeshContainsTarget(HintMeshWindow hintMeshWindow, HintMesh hintMesh, List<QWidget> containers) {
        if (containers.isEmpty() || hintMeshWindow.hints().isEmpty())
            return false;
        QRect target = containers.getLast().geometry();
        QRect bounds = newMeshBounds(hintMeshWindow, hintMesh);
        QRect paddedBounds = paddedRect(bounds);
        boolean contains = paddedBounds.contains(target);
        target.dispose();
        bounds.dispose();
        paddedBounds.dispose();
        return contains;
    }

    /** Bounding rectangle of the incoming mesh's selected (narrowed) cells, in window coordinates. */
    private QRect newMeshBounds(HintMeshWindow hintMeshWindow, HintMesh hintMesh) {
        double left = Double.MAX_VALUE, top = Double.MAX_VALUE;
        double right = -Double.MAX_VALUE, bottom = -Double.MAX_VALUE;
        for (Hint hint : hintMeshWindow.hints()) {
            if (!hint.startsWith(hintMesh.selectedKeySequence()))
                continue;
            left = Math.min(left, hint.centerX() - hint.cellWidth() / 2.0);
            right = Math.max(right, hint.centerX() + hint.cellWidth() / 2.0);
            top = Math.min(top, hint.centerY() - hint.cellHeight() / 2.0);
            bottom = Math.max(bottom, hint.centerY() + hint.cellHeight() / 2.0);
        }
        return new QRect((int) Math.round(left - hintMeshWindow.window.x()),
                (int) Math.round(top - hintMeshWindow.window.y()),
                (int) Math.round(right - left), (int) Math.round(bottom - top));
    }

    private static boolean closeInSize(double a, double b) {
        return Math.min(a, b) > 0.75 * Math.max(a, b);
    }

    /** A container's currently visible rectangle in window coordinates: its crop or mask bounds when
     *  mid-crop, otherwise its full geometry. Lets an interruption continue from what is on screen. */
    private QRect visibleRect(QWidget container) {
        QRect geometry = container.geometry();
        if (container instanceof ClearBackgroundQLabel label && label.crop != null) {
            QRect visible = new QRect(geometry.x() + label.crop.x(),
                    geometry.y() + label.crop.y(),
                    label.crop.width(), label.crop.height());
            geometry.dispose();
            return visible;
        }
        QRegion mask = container.mask();
        if (mask.isEmpty()) {
            mask.dispose();
            return geometry;
        }
        QRect maskBounds = mask.boundingRect();
        mask.dispose();
        QRect visible = new QRect(geometry.x() + maskBounds.x(), geometry.y() + maskBounds.y(),
                maskBounds.width(), maskBounds.height());
        geometry.dispose();
        maskBounds.dispose();
        return visible;
    }

    private QRect paddedRect(QRect rect) {
        int extraWidth = (int) (rect.width() * 0.05d);
        int extraHeight = (int) (rect.height() * 0.05d);
        return new QRect(
                rect.left() - extraWidth / 2,
                rect.top() - extraHeight / 2,
                rect.width() + extraWidth,
                rect.height() + extraHeight
        );
    }

    /** Crops a pixmap label to {@code r} by clipping (smooth), else masks the widget. */
    private static void cropOrMask(QWidget container, QRect r) {
        if (container instanceof ClearBackgroundQLabel label && label.cropCapable())
            label.setCrop(r);
        else {
            QRegion region = new QRegion(r);
            container.setMask(region);
            region.dispose();
        }
    }

    public static class HintContainerAnimationChanged implements QMetaObject.Slot1<Object> {

        private final QWidget container;
        private final HintMeshRenderer renderer;

        public HintContainerAnimationChanged(QWidget container, HintMeshRenderer renderer) {
            this.container = container;
            this.renderer = renderer;
        }

        @Override
        public void invoke(Object arg) {
            renderer.transitionMetrics.frameDrawn();
            cropOrMask(container, (QRect) arg);
        }
    }

    public static class HintContainerAnimationFinished implements QMetaObject.Slot0 {

        private final QWidget oldContainer;
        private final QWidget animatedContainer;
        private final QRect endRect;
        private final HintMeshRenderer renderer;

        public HintContainerAnimationFinished(QWidget oldContainer, QWidget animatedContainer,
                                              QRect endRect, HintMeshRenderer renderer) {
            this.oldContainer = oldContainer;
            this.animatedContainer = animatedContainer;
            this.endRect = endRect;
            this.renderer = renderer;
        }

        @Override
        public void invoke() {
            cropOrMask(animatedContainer, endRect); // animatedContainer can be the oldContainer.
            endRect.dispose();
            if (oldContainer != null) {
                oldContainer.setParent(null);
                disposeWidget(oldContainer);
            }
            renderer.transitionMetrics.log();
            renderer.hintContainerAnimationEnded();
        }
    }

    private void startCropAnimation(QVariantAnimation animation, QWidget container) {
        transitionMetrics.started(animation.getDuration(), clipping(container));
        cropAnimationStartNanos = System.nanoTime();
        animation.start();
    }

    /** Qt withholds a newly started animation's first value for about two timer intervals. */
    private void advanceCropAnimationToFirstFrame() {
        if (cropAnimation == null || cropAnimation.getCurrentTime() != 0 ||
            cropAnimation.getState() != QAbstractAnimation.State.Running)
            return;
        cropAnimation.setCurrentTime(
                (int) ((System.nanoTime() - cropAnimationStartNanos) / 1_000_000));
    }

    /** Why a container clips the way it does: a pixmap is cropped (cheap), anything else is masked
     *  (recomposites the whole window every frame). */
    private static String clipping(QWidget container) {
        if (container instanceof ClearBackgroundQLabel label && label.cropCapable())
            return "cropped";
        return "masked";
    }

    private void hintContainerAnimationEnded() {
        if (hintMeshEndAnimation) {
            hintMeshEndAnimation = false;
            hintMeshEndAnimationEndedCallback.run();
        }
    }


    private QVariantAnimation hintContainerAnimation(QRect beginRect,
                                                            QRect endRect,
                                                            Duration animationDuration) {
        QVariantAnimation animation = new QVariantAnimation();
//        double topLeftDistance = Math.hypot(beginRect.topLeft().x() - endRect.topLeft().x(), beginRect.topLeft().y() - endRect.topLeft().y());
//        double topRightDistance = Math.hypot(beginRect.topRight().x() - endRect.topRight().x(), beginRect.topRight().y() - endRect.topRight().y());
//        double bottomLeftDistance = Math.hypot(beginRect.bottomLeft().x() - endRect.bottomLeft().x(), beginRect.bottomLeft().y() - endRect.bottomLeft().y());
//        double bottomRightDistance = Math.hypot(beginRect.bottomRight().x() - endRect.bottomRight().x(), beginRect.bottomRight().y() - endRect.bottomRight().y());
//        double distance = Math.max(Math.max(Math.max(topLeftDistance, topRightDistance), bottomLeftDistance), bottomRightDistance);
//        int duration = (int) Math.round((distance / velocity) * 1000); // ms
        animation.setDuration((int) animationDuration.toMillis());
        animation.setStartValue(beginRect);
        animation.setEndValue(endRect);
        animation.setEasingCurve(resumedTransition ? QEasingCurve.Type.OutQuad :
                QEasingCurve.Type.InOutQuad);
        return animation;
    }

    private class HintGroup {

        double minHintCenterX = Double.MAX_VALUE;
        double minHintCenterY = Double.MAX_VALUE;
        double maxHintCenterX = -Double.MAX_VALUE;
        double maxHintCenterY = -Double.MAX_VALUE;
        boolean atLeastOneHintVisible = false;
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        HintBox prefixHintBox;
        HintLabel prefixHintLabel;
        int x, y;

        Rectangle centerArea() {
            return new Rectangle((int) Math.round(minHintCenterX),
                    (int) Math.round(minHintCenterY),
                    (int) Math.round(maxHintCenterX - minHintCenterX),
                    (int) Math.round(maxHintCenterY - minHintCenterY));
        }

    }

    private List<HintBox> setUncachedHintMeshWindow(HintMeshWindow hintMeshWindow, HintMesh hintMeshKey,
                                                              HintMesh hintMesh,
                                                              double screenScale, HintMeshStyle style,
                                                              double qtScaleFactor,
                                                              QWidget container,
                                                              List<Rectangle> enclosingInk) {
        long beforeMetrics = System.nanoTime();
        boolean isHintPartOfGrid = hintMeshWindow.hints().getFirst().cellWidth() != -1;
        double minHintCenterX = Double.MAX_VALUE;
        double minHintCenterY = Double.MAX_VALUE;
        double maxHintCenterX = -Double.MAX_VALUE;
        double maxHintCenterY = -Double.MAX_VALUE;
        Map<List<Key>, HintGroup> hintGroupByPrefix = new HashMap<>();
        for (Hint hint : hintMeshWindow.hints()) {
            if (hintMesh.prefixLength() != -1) {
                List<Key> prefix = hint.keySequence().subList(0, hintMesh.prefixLength());
                HintGroup hintGroup =
                        hintGroupByPrefix.computeIfAbsent(prefix,
                                key -> new HintGroup());
                hintGroup.minHintCenterX = Math.min(hintGroup.minHintCenterX, hint.centerX());
                hintGroup.minHintCenterY = Math.min(hintGroup.minHintCenterY, hint.centerY());
                hintGroup.maxHintCenterX = Math.max(hintGroup.maxHintCenterX, hint.centerX());
                hintGroup.maxHintCenterY = Math.max(hintGroup.maxHintCenterY, hint.centerY());
                hintGroup.atLeastOneHintVisible |= hint.startsWith(hintMesh.selectedKeySequence());
            }
            if (!hint.startsWith(hintMesh.selectedKeySequence()))
                continue;
            minHintCenterX = Math.min(minHintCenterX, hint.centerX());
            minHintCenterY = Math.min(minHintCenterY, hint.centerY());
            maxHintCenterX = Math.max(maxHintCenterX, hint.centerX());
            maxHintCenterY = Math.max(maxHintCenterY, hint.centerY());
        }
        List<Hint> hints = hintMeshWindow.hints;
        // A grid of one hint (the screen selection hint) has no neighboring cells: its box grows
        // to fit its text and its label centers on its ink, as a non-grid hint's does.
        boolean isHintPartOfTiledGrid = isHintPartOfGrid && hints.size() != 1;
        int minHintLeft = Integer.MAX_VALUE;
        int minHintTop = Integer.MAX_VALUE;
        int maxHintRight = Integer.MIN_VALUE;
        int maxHintBottom = Integer.MIN_VALUE;
        boolean hasSelectedKeys = !hintMesh.selectedKeySequence().isEmpty();
        // Background prefix is on a different layer.
        boolean hasForegroundPrefixKeys = !style.prefixInBackground() && hintMesh.prefixLength() != -1;
        HintFontStyle prefixFontStyle = hasForegroundPrefixKeys ? style.prefixFontStyle() : null;
        QtHintFontStyle labelFontStyle = QtHintFont.qtHintFontStyle(style.fontStyle(), prefixFontStyle, screenScale);
        HintGradientColor boxColor = style.boxColor();
        boolean boxSweptPerHint = style.boxOpacity() != 0 && boxColor.gradient() &&
                                  boxColor.area() != HintGradientArea.HINT;
        Rectangle boxGradientArea =
                boxSweptPerHint ? gradientArea(boxColor.area(), hintMeshWindow, hintMesh) : null;
        QBrush boxBrush = boxSweptPerHint ? null : fillBrush(boxColor, style.boxOpacity());
        QColor boxBorderColor = QtColorUtil.qColor(style.boxBorderHexColor(), style.boxBorderOpacity());
        QColor prefixBoxBorderColor = QtColorUtil.qColor(style.prefixBoxBorderHexColor(), style.prefixBoxBorderOpacity());
        int cellHorizontalPadding = scaledLength(style.cellHorizontalPadding(), screenScale);
        int cellVerticalPadding = scaledLength(style.cellVerticalPadding(), screenScale);
        int boxBorderThickness = scaledLength(style.boxBorderThickness(), screenScale);
        int boxBorderLength = scaledLength(style.boxBorderLength(), screenScale);
        int boxBorderRadius = scaledLength(style.boxBorderRadius(), screenScale);
        int prefixBoxBorderThickness =
                scaledLength(style.prefixBoxBorderThickness(), screenScale);
        int prefixBoxBorderLength = scaledLength(style.prefixBoxBorderLength(), screenScale);
        Shadow boxShadow = scaledShadow(style.boxShadow(), screenScale);
        // One entry per tiled depth: subdecoration at index 0, subsubdecoration at index 1.
        List<DecorationStyle> subDecorationStyles = new ArrayList<>();
        if (hintMesh.subDecoration() != null) {
            subDecorationStyles.add(decorationStyle(style.decorations().get(1), screenScale));
            if (hintMesh.subDecoration().subDecoration() != null)
                subDecorationStyles.add(decorationStyle(style.decorations().get(2), screenScale));
        }
        Map<String, Integer> xAdvancesByString = new HashMap<>();
        int hintKeyMaxXAdvance = 0;
        List<Key> labelOverride = style.labelOverride();
        boolean labelOverridden = labelOverride != null && !labelOverride.isEmpty();
        for (Hint hint : hints) {
            for (Key key : labelOverridden ? labelOverride : hint.keySequence()) {
                hintKeyMaxXAdvance = Math.max(hintKeyMaxXAdvance,
                        xAdvancesByString.computeIfAbsent(key.hintLabel(),
                                labelFontStyle.defaultStyle().metrics()::horizontalAdvance));
            }
        }
//            hintKeyMaxXAdvance = metrics.maxWidth();
        List<HintBox> hintBoxes = new ArrayList<>();
        List<HintLabel> hintLabels = new ArrayList<>();
        long beforeBoxes = System.nanoTime();
        long lastPumpTime = System.nanoTime();
        for (int hintIndex = 0; hintIndex < hints.size(); hintIndex++) {
            Hint hint = hints.get(hintIndex);
            if (!hint.startsWith(hintMesh.selectedKeySequence()))
                continue;
            List<Key> labelKeys = labelOverridden ? labelOverride : hint.keySequence();
            int totalXAdvance = labelFontStyle.defaultStyle()
                                              .metrics()
                                              .horizontalAdvance(labelKeys
                                                                     .stream()
                                                                     .map(Key::hintLabel)
                                                                     .collect(
                                                                             Collectors.joining()));
            int lineHeight = labelFontStyle.defaultStyle().metrics().height();
            // A cell is in pixels, as the padding and the grid's own cell are, but metrics are
            // in the points Qt draws text in.
            double advancePixels = totalXAdvance * qtScaleFactor;
            double lineHeightPixels = lineHeight * qtScaleFactor;
            // A grid cell is used as-is so boxes tile perfectly. Text that overflows is handled
            // by the label layer.
            double cellWidth = (isHintPartOfGrid ? hint.cellWidth() : advancePixels) +
                               2 * cellHorizontalPadding;
            double cellHeight = (isHintPartOfGrid ? hint.cellHeight() : lineHeightPixels) +
                                2 * cellVerticalPadding;
            int x = hintRoundedX(hint.centerX(), cellWidth, qtScaleFactor);
            int y = hintRoundedY(hint.centerY(), cellHeight, qtScaleFactor);
            int fullBoxWidth = (int) (cellWidth / qtScaleFactor);
            int fullBoxHeight = (int) (cellHeight / qtScaleFactor);
            if (isHintPartOfGrid) {
                // Size each box by its rounded cell boundaries so columns and rows tile exactly and
                // the last column/row reaches the cell's edge. Otherwise the grid falls a pixel short
                // of the cell it is cropped into, and the uncovered strip shows as a seam on the
                // right/bottom during the transition.
                fullBoxWidth = hintRoundedRight(hint.centerX(), cellWidth, qtScaleFactor) - x;
                fullBoxHeight = hintRoundedBottom(hint.centerY(), cellHeight, qtScaleFactor) - y;
            }
            List<Key> prefix = (labelOverridden || hintMesh.prefixLength() == -1) ?
                    List.of() : hint.keySequence().subList(0,
                    hintMesh.prefixLength());
            List<Key> suffix = labelOverridden ? labelKeys :
                    hint.keySequence().subList(prefix.size(), hint.keySequence().size());
            HintLabel hintLabel =
                    HintLabel.of(
                            labelOverridden ? labelKeys :
                                    (style.prefixInBackground() ? suffix : hint.keySequence()),
                            xAdvancesByString, fullBoxWidth,
                            fullBoxHeight, totalXAdvance,
                            labelOverridden ? -1 :
                                    (style.prefixInBackground() ? -1 : hintMesh.prefixLength()),
                            labelFontStyle,
                            hintKeyMaxXAdvance,
                            labelOverridden ? -1 :
                                    (hintMesh.selectedKeySequence().size() - 1
                                    - (style.prefixInBackground() && hintMesh.prefixLength() != -1 ? prefix.size() : 0)),
                            style.fontStyle().defaultFontStyle().verticalAlignment(),
                            isHintPartOfTiledGrid, qtScaleFactor);
            hintLabels.add(hintLabel);
            boolean gridLeftEdge = isHintPartOfGrid && hint.centerX() == minHintCenterX || style.boxWidthPercent() != 1;
            boolean gridTopEdge = isHintPartOfGrid && hint.centerY() == minHintCenterY || style.boxHeightPercent() != 1;
            boolean gridRightEdge = isHintPartOfGrid && hint.centerX() == maxHintCenterX || style.boxWidthPercent() != 1;
            boolean gridBottomEdge = isHintPartOfGrid && hint.centerY() == maxHintCenterY || style.boxHeightPercent() != 1;
            HintGroup hintGroup = hintGroupByPrefix.get(prefix);
            HintBox hintBox =
                    new HintBox(hint, boxBorderLength,
                            boxBorderThickness,
                            boxSweptPerHint &&
                            boxColor.step() != HintGradientStep.PIXEL ?
                                    sampledBrush(boxColor, style.boxOpacity(), boxGradientArea,
                                            hintGroup, hint) : boxBrush,
                            boxBorderColor,
                            isHintPartOfGrid,
                            gridLeftEdge, gridTopEdge, gridRightEdge, gridBottomEdge,
                            style.boxFramed(),
                            qtScaleFactor,
                            boxBorderRadius
                    );
            hintBoxes.add(hintBox);
            int scaledBoxWidth = (int) (fullBoxWidth * style.boxWidthPercent());
            int scaledBoxHeight = (int) (fullBoxHeight * style.boxHeightPercent());
            boolean boxSizedByLabel =
                    !isHintPartOfGrid && hintLabel.tightHintBoxWidth >= scaledBoxWidth;
            int boxWidth = isHintPartOfTiledGrid ? scaledBoxWidth :
                    Math.max(hintLabel.tightHintBoxWidth, scaledBoxWidth);
            int boxHeight = isHintPartOfTiledGrid ? scaledBoxHeight :
                    Math.max(hintLabel.tightHintBoxHeight, scaledBoxHeight);
            hintLabel.left = boxSizedByLabel ? hintLabel.tightHintBoxLeft :
                    (fullBoxWidth - boxWidth) / 2;
            hintLabel.top = (fullBoxHeight - boxHeight) / 2;
            x += hintLabel.left;
            y += hintLabel.top;
            // Not sure why required, but this help having the grid match the screen
            // right and bottom borders (pixel perfect).
            if (x + boxWidth == hintMeshWindow.window.x() + hintMeshWindow.window.width() - 1)
                boxWidth++;
            else if (x + boxWidth == hintMeshWindow.window.x() + hintMeshWindow.window.width() + 1)
                boxWidth--;
            if (y + boxHeight == hintMeshWindow.window.y() + hintMeshWindow.window.height() - 1)
                boxHeight++;
            else if (y + boxHeight == hintMeshWindow.window.y() + hintMeshWindow.window.height() + 1)
                boxHeight--;
//            logger.debug("x + boxWidth: " + (x+boxWidth) + ", (y+boxHeight): " + (y+boxHeight));
            minHintLeft = Math.min(minHintLeft, x);
            minHintTop = Math.min(minHintTop, y);
            maxHintRight = Math.max(maxHintRight, x + boxWidth);
            maxHintBottom = Math.max(maxHintBottom, y + boxHeight);
            hintBox.setGeometry(x - hintMeshWindow.window.x(),
                    y - hintMeshWindow.window.y(),
                    boxWidth,
                    boxHeight);
            hintLabel.setFixedSize(boxWidth, boxHeight);
            if (hintGroup != null) {
                hintGroup.left = Math.min(hintGroup.left, hintBox.x());
                hintGroup.top = Math.min(hintGroup.top, hintBox.y());
                hintGroup.right = Math.max(hintGroup.right, hintBox.x() + hintBox.width());
                hintGroup.bottom = Math.max(hintGroup.bottom, hintBox.y() + hintBox.height());
            }
            addDecorationBoxes(hintBox, boxWidth, boxHeight, hintMesh.subDecoration(),
                    subDecorationStyles, 0, qtScaleFactor);
            if (pumpDuringHintBuild && messagePump != null && (System.nanoTime() - lastPumpTime) > 30_000_000L) {
                messagePump.run();
                lastPumpTime = System.nanoTime();
            }
        }
        long beforeGroups = System.nanoTime();
        if (pumpDuringHintBuild && messagePump != null)
            messagePump.run();
        for (HintGroup hintGroup : hintGroupByPrefix.values()) {
            if (!hintGroup.atLeastOneHintVisible)
                continue;
            if (!style.prefixBoxEnabled())
                continue;
            boolean gridLeftEdge =
                    isHintPartOfGrid && hintGroup.minHintCenterX == minHintCenterX ||
                    style.boxWidthPercent() != 1;
            boolean gridTopEdge =
                    isHintPartOfGrid && hintGroup.minHintCenterY == minHintCenterY ||
                    style.boxHeightPercent() != 1;
            boolean gridRightEdge =
                    isHintPartOfGrid && hintGroup.maxHintCenterX == maxHintCenterX ||
                    style.boxWidthPercent() != 1;
            boolean gridBottomEdge =
                    isHintPartOfGrid && hintGroup.maxHintCenterY == maxHintCenterY ||
                    style.boxHeightPercent() != 1;
            HintBox prefixHintBox =
                    new HintBox(null, prefixBoxBorderLength,
                            prefixBoxBorderThickness,
                            null,
                            prefixBoxBorderColor,
                            isHintPartOfGrid,
                            gridLeftEdge, gridTopEdge, gridRightEdge, gridBottomEdge,
                            style.prefixBoxFramed(),
                            qtScaleFactor,
                            0
                    );
            prefixHintBox.setGeometry(hintGroup.left, hintGroup.top,
                    hintGroup.right - hintGroup.left,
                    hintGroup.bottom - hintGroup.top);
            hintGroup.prefixHintBox = prefixHintBox;
        }
        QtHintFontStyle prefixQtHintFontStyle = null;
        if (style.prefixInBackground()) {
            prefixQtHintFontStyle = QtHintFont.qtHintFontStyle(style.prefixFontStyle(), null, screenScale);
            Map<String, Integer> prefixXAdvancesByString = new HashMap<>();
            int prefixHintKeyMaxXAdvance = 0;
            for (List<Key> prefix : hintGroupByPrefix.keySet()) {
                for (Key key : prefix) {
                    prefixHintKeyMaxXAdvance = Math.max(prefixHintKeyMaxXAdvance,
                            prefixXAdvancesByString.computeIfAbsent(key.hintLabel(),
                                    prefixQtHintFontStyle.defaultStyle().metrics()::horizontalAdvance));
                }
            }
            for (Map.Entry<List<Key>, HintGroup> entry : hintGroupByPrefix.entrySet()) {
                List<Key> prefix = entry.getKey();
                HintGroup hintGroup = entry.getValue();
                if (!hintGroup.atLeastOneHintVisible)
                    continue;
                int totalXAdvance = prefixQtHintFontStyle.defaultStyle().metrics().horizontalAdvance(
                        prefix.stream()
                              .map(Key::hintLabel)
                              .collect(Collectors.joining()));
                int fullBoxWidth = hintGroup.right - hintGroup.left;
                int fullBoxHeight = hintGroup.bottom - hintGroup.top;
                HintLabel prefixHintLabel =
                        HintLabel.of(prefix, prefixXAdvancesByString, fullBoxWidth,
                                fullBoxHeight, totalXAdvance,
                                hintMesh.prefixLength(),
                                prefixQtHintFontStyle,
                                prefixHintKeyMaxXAdvance,
                                hintMesh.selectedKeySequence().size() - 1,
                                style.prefixFontStyle().defaultFontStyle().verticalAlignment(),
                                isHintPartOfTiledGrid, qtScaleFactor);
                // The group bounds come from boxes that are already scaled down.
                int x = hintRoundedX((hintGroup.left + hintGroup.right-1) / 2d, fullBoxWidth, 1);
                int y = hintRoundedY((hintGroup.top + hintGroup.bottom-1) / 2d, fullBoxHeight, 1);
                int boxWidth = Math.max(prefixHintLabel.tightHintBoxWidth, fullBoxWidth);
                int boxHeight = Math.max(prefixHintLabel.tightHintBoxHeight, fullBoxHeight);
                prefixHintLabel.left =
                        prefixHintLabel.tightHintBoxWidth >= fullBoxWidth ?
                                prefixHintLabel.tightHintBoxLeft :
                                (fullBoxWidth - boxWidth) / 2;
                prefixHintLabel.top = (fullBoxHeight - boxHeight) / 2;
                x += prefixHintLabel.left;
                y += prefixHintLabel.top;
                prefixHintLabel.move(
                        x - (minHintLeft - hintMeshWindow.window.x()),
                        y - (minHintTop - hintMeshWindow.window.y())
                );
                prefixHintLabel.setFixedSize(boxWidth, boxHeight);
                hintGroup.prefixHintLabel = prefixHintLabel;
            }
        }
        // Expand container bounds to accommodate the antialiased rounded
        // border stroke extending outside the box fill area.
        if (boxBorderRadius > 0 && boxBorderThickness > 0) {
            int borderPad = (int) Math.ceil(boxBorderThickness / 2.0 / qtScaleFactor);
            minHintLeft -= borderPad;
            minHintTop -= borderPad;
            maxHintRight += borderPad;
            maxHintBottom += borderPad;
        }
        // Expand container bounds to accommodate box shadow extent.
        if (boxShadow.opacity() > 0) {
            int shadowPadLeft = (int) Math.ceil((boxShadow.blurRadius() + Math.max(0, -boxShadow.horizontalOffset())) / qtScaleFactor);
            int shadowPadRight = (int) Math.ceil((boxShadow.blurRadius() + Math.max(0, boxShadow.horizontalOffset())) / qtScaleFactor);
            int shadowPadTop = (int) Math.ceil((boxShadow.blurRadius() + Math.max(0, -boxShadow.verticalOffset())) / qtScaleFactor);
            int shadowPadBottom = (int) Math.ceil((boxShadow.blurRadius() + Math.max(0, boxShadow.verticalOffset())) / qtScaleFactor);
            minHintLeft -= shadowPadLeft;
            minHintTop -= shadowPadTop;
            maxHintRight += shadowPadRight;
            maxHintBottom += shadowPadBottom;
        }
        int offsetX = minHintLeft - hintMeshWindow.window.x();
        int offsetY = minHintTop - hintMeshWindow.window.y();
        // The boxes are moved into the container at (offsetX, offsetY), so the sweep is too.
        Point sweepOrigin = new Point((hintMeshWindow.window.x() + offsetX) * qtScaleFactor,
                (hintMeshWindow.window.y() + offsetY) * qtScaleFactor);
        Map<List<Key>, QRect> hintBoxGeometries = new HashMap<>();
        for (int hintIndex = 0; hintIndex < hintBoxes.size(); hintIndex++) {
            HintBox hintBox = hintBoxes.get(hintIndex);
            hintBox.move(hintBox.x() - offsetX, hintBox.y() - offsetY);
            HintLabel hintLabel = hintLabels.get(hintIndex);
            hintLabel.move(hintBox.x(), hintBox.y());
            if (boxSweptPerHint && boxColor.step() == HintGradientStep.PIXEL)
                hintBox.setColor(sweptBrush(boxColor, style.boxOpacity(),
                        areaNarrowedToGroup(boxColor, boxGradientArea,
                                hintGroupOf(hintMesh, hintGroupByPrefix, hintBox.hint)),
                        sweepOrigin));
            if (hintMesh.selectedKeySequence().size() == hints.getFirst().keySequence().size() - 1) {
                hintBoxGeometries.put(hintBox.hint.keySequence(), hintBox.geometry());
            }
        }
        List<HintBox> prefixBoxes = new ArrayList<>();
        List<HintLabel> prefixLabels = new ArrayList<>();
        for (HintGroup hintGroup : hintGroupByPrefix.values()) {
            HintBox prefixHintBox = hintGroup.prefixHintBox;
            if (prefixHintBox == null)
                continue;
            prefixHintBox.move(
                    prefixHintBox.x() - offsetX,
                    prefixHintBox.y() - offsetY
            );
            prefixBoxes.add(prefixHintBox);
        }
        for (HintGroup hintGroup : hintGroupByPrefix.values()) {
            HintLabel prefixHintLabel = hintGroup.prefixHintLabel;
            if (prefixHintLabel == null)
                continue;
            prefixLabels.add(prefixHintLabel);
        }
        long beforeLayers = System.nanoTime();
        int containerWidth = maxHintRight - minHintLeft;
        int containerHeight = maxHintBottom - minHintTop;
        container.setGeometry(offsetX, offsetY, containerWidth, containerHeight);
        // Layer 1: Box shadow (painted underneath boxes; empty unless shadow is active).
        HintPaintLayer boxShadowLayer = new HintPaintLayer(container, List.of(), List.of());
        boxShadowLayer.setGeometry(0, 0, containerWidth, containerHeight);
        // Layer 2: the hint boxes. A grid's border moves on the live border layer instead.
        HintPaintLayer hintBoxLayer = new HintPaintLayer(container, hintBoxes, List.of(),
                isHintPartOfGrid ? HintBox::paintWithoutBorder : HintBox::paint);
        hintBoxLayer.setGeometry(0, 0, containerWidth, containerHeight);
        applyBoxShadow(boxShadowLayer, hintBoxes, boxShadow,
                containerWidth, containerHeight, qtScaleFactor);
        addDecorationLabelLayers(container, hintBoxes, subDecorationStyles,
                containerWidth, containerHeight);
        // Layer 3: Prefix boxes.
        HintPaintLayer prefixBoxLayer = new HintPaintLayer(container, prefixBoxes, List.of());
        prefixBoxLayer.setGeometry(0, 0, containerWidth, containerHeight);
        for (HintBox prefixBox : prefixBoxes)
            prefixBox.collectBorderInk(0, 0, enclosingInk);
        // Layer 3: Prefix labels.
        addLabelLayers(container, prefixLabels, prefixQtHintFontStyle, hasSelectedKeys,
                containerWidth, containerHeight, screenScale);
        // Layer 4: Hint labels.
        addLabelLayers(container, hintLabels, labelFontStyle, hasSelectedKeys,
                containerWidth, containerHeight, screenScale);
        // Layer 5: whole-area decoration (index 0), anchored to the grid area it was laid out in, not
        // to the container: a grid drilled past a screen edge has cells the container cannot show.
        HintBox areaBox = null;
        List<DecorationStyle> areaDecorationStyles = List.of();
        if (hintMesh.decoration() != null) {
            Rectangle area = hintMesh.decoration().backgroundArea();
            int areaLeft = (int) Math.round(area.x() / qtScaleFactor) - minHintLeft;
            int areaTop = (int) Math.round(area.y() / qtScaleFactor) - minHintTop;
            int areaWidth =
                    (int) Math.round((area.x() + area.width()) / qtScaleFactor) -
                    minHintLeft - areaLeft;
            int areaHeight =
                    (int) Math.round((area.y() + area.height()) / qtScaleFactor) -
                    minHintTop - areaTop;
            areaBox = new HintBox(null, 0, 0, null, QtColorUtil.qColor("#000000", 0),
                    true, false, false, false, false, false, qtScaleFactor, 0);
            areaBox.setGeometry(areaLeft, areaTop, areaWidth, areaHeight);
            areaDecorationStyles =
                    List.of(decorationStyle(style.decorations().get(0), screenScale));
            addDecorationBoxes(areaBox, areaWidth, areaHeight,
                    hintMesh.decoration(), areaDecorationStyles, 0, qtScaleFactor);
            List<Rectangle> areaInk = new ArrayList<>();
            areaBox.collectBorderInk(0, 0, areaInk);
            prefixBoxLayer.setEnclosingInk(areaInk);
            enclosingInk.addAll(areaInk);
            HintPaintLayer areaDecorationLayer =
                    new HintPaintLayer(container, List.of(areaBox), List.of());
            areaDecorationLayer.setGeometry(0, 0, containerWidth, containerHeight);
            addDecorationLabelLayers(container, List.of(areaBox), areaDecorationStyles,
                    containerWidth, containerHeight);
        }
        dropOverlappingDecorationLabels(hintBoxes, areaBox,
                style.fontStyle().defaultFontStyle().opacity() != 0,
                subDecorationStyles, areaDecorationStyles);
        hintBoxGeometriesByHintMeshKey.put(hintMeshKey, hintBoxGeometries);
        logger.debug(
                "Built {} hint boxes: metrics {}ms, boxes {}ms, groups {}ms, layers {}ms",
                hintBoxes.size(), (beforeBoxes - beforeMetrics) / 1_000_000,
                (beforeGroups - beforeBoxes) / 1_000_000,
                (beforeLayers - beforeGroups) / 1_000_000,
                (System.nanoTime() - beforeLayers) / 1_000_000);
        return hintBoxes;
    }

    /** A decoration label is not drawn where a label already is. Labels are centered in their box, so
     *  only a concentric box's label can hide one: an ancestor's, or the whole-area decoration's at the
     *  grid center, which is settled only once the area box is positioned. */
    private void dropOverlappingDecorationLabels(List<HintBox> hintBoxes, HintBox areaBox,
                                                 boolean hintLabelVisible,
                                                 List<DecorationStyle> subDecorationStyles,
                                                 List<DecorationStyle> areaDecorationStyles) {
        boolean areaLabelVisible = areaBox != null && !areaDecorationStyles.isEmpty()
                                   && areaDecorationStyles.getFirst().labelVisible();
        for (HintBox hintBox : hintBoxes)
            hintBox.dropDecorationLabelsOnOccupiedCenter(
                    hintLabelVisible || (areaLabelVisible && concentric(hintBox, areaBox)),
                    subDecorationStyles, 0);
    }

    /** Whether the box shares its parent's center, and so the position of its label. Tolerance absorbs
     *  the rounding of the cell rects. */
    private static boolean concentric(HintBox box, int parentWidth, int parentHeight) {
        return Math.abs(2 * box.x() + box.width() - parentWidth) <= 1
               && Math.abs(2 * box.y() + box.height() - parentHeight) <= 1;
    }

    /** Whether two boxes of the same parent share their center, and so the position of their labels. */
    private static boolean concentric(HintBox box, HintBox otherBox) {
        return Math.abs(2 * box.x() + box.width()
                        - (2 * otherBox.x() + otherBox.width())) <= 1
               && Math.abs(2 * box.y() + box.height()
                           - (2 * otherBox.y() + otherBox.height())) <= 1;
    }

    private static QBrush fillBrush(String hexColor, double opacity) {
        return opacity == 0 ? null : QtColorUtil.qBrush(QtColorUtil.rgba(hexColor, opacity));
    }

    private static QBrush fillBrush(HintGradientColor color, double opacity) {
        return opacity == 0 || !color.gradient() ? fillBrush(color.hexColor(), opacity) :
                QtColorUtil.qBrush(color, opacity);
    }

    private Rectangle gradientArea(HintGradientArea area, HintMeshWindow hintMeshWindow,
                                   HintMesh hintMesh) {
        return switch (area) {
            case SCREEN -> screenArea(hintMeshWindow.window());
            case ALL_SCREENS -> Rectangle.union(hintMeshWindows.values().stream()
                    .map(window -> screenArea(window.window())).toList());
            case AREA -> hintMesh.area();
            // A subgrid narrows this to its own bounds per box.
            case ALL_HINTS, SUBGRID -> centerArea(hintMeshWindow.hints());
            case HINT -> null;
        };
    }

    private static Rectangle screenArea(TransparentWindow window) {
        return new Rectangle(window.xInPixels(), window.yInPixels(), window.widthInPixels(),
                window.heightInPixels());
    }

    /** The extreme centers, so the first and last color land on the first and last hint. */
    private static Rectangle centerArea(List<Hint> hints) {
        double left = Double.MAX_VALUE, top = Double.MAX_VALUE;
        double right = -Double.MAX_VALUE, bottom = -Double.MAX_VALUE;
        for (Hint hint : hints) {
            left = Math.min(left, hint.centerX());
            top = Math.min(top, hint.centerY());
            right = Math.max(right, hint.centerX());
            bottom = Math.max(bottom, hint.centerY());
        }
        return new Rectangle((int) Math.round(left), (int) Math.round(top),
                (int) Math.round(right - left), (int) Math.round(bottom - top));
    }

    private static QBrush sweptBrush(HintGradientColor color, double opacity, Rectangle area,
                                     Point origin) {
        Point start = color.direction().start(area);
        Point end = color.direction().end(area);
        return QtColorUtil.qBrush(color, opacity,
                new Point(start.x() - origin.x(), start.y() - origin.y()),
                new Point(end.x() - origin.x(), end.y() - origin.y()));
    }

    private static HintGroup hintGroupOf(HintMesh hintMesh,
                                         Map<List<Key>, HintGroup> hintGroupByPrefix, Hint hint) {
        return hintMesh.prefixLength() == -1 ? null : hintGroupByPrefix.get(
                hint.keySequence().subList(0, hintMesh.prefixLength()));
    }

    private static Rectangle areaNarrowedToGroup(HintGradientColor color, Rectangle area,
                                                 HintGroup hintGroup) {
        return color.area() == HintGradientArea.SUBGRID && hintGroup != null ?
                hintGroup.centerArea() : area;
    }

    private static QBrush sampledBrush(HintGradientColor color, double opacity, Rectangle area,
                                       HintGroup hintGroup, Hint hint) {
        Rectangle sampledArea = areaNarrowedToGroup(color, area, hintGroup);
        Point sampled = color.step() == HintGradientStep.SUBGRID && hintGroup != null ?
                hintGroup.centerArea().center() :
                new Point(hint.centerX(), hint.centerY());
        return QtColorUtil.qBrush(color, opacity,
                color.sweepPosition(sampledArea, sampled.x(), sampled.y()));
    }

    /** The Qt drawing resources for one decoration. */
    private record DecorationStyle(QBrush boxColor, QColor boxBorderColor,
                                   int borderThicknessPx, int borderLengthPx,
                                   int borderRadiusPx, boolean boxFramed,
                                   boolean labelVisible,
                                   QtFontStyle labelStyle,
                                   List<Key> labelOverride,
                                   FontVerticalAlignment labelVerticalAlignment) {
    }

    private DecorationStyle decorationStyle(Decoration decoration, double screenScale) {
        FontStyle font = decoration.fontStyle().defaultFontStyle();
        return new DecorationStyle(
                fillBrush(decoration.boxHexColor(), decoration.boxOpacity()),
                QtColorUtil.qColor(decoration.boxBorderHexColor(), decoration.boxBorderOpacity()),
                scaledLength(decoration.boxBorderThickness(), screenScale),
                scaledLength(decoration.boxBorderLength(), screenScale),
                scaledLength(decoration.boxBorderRadius(), screenScale),
                decoration.boxFramed(),
                font.opacity() != 0,
                QtHintFont.qtFontStyle(font, screenScale),
                decoration.labelOverride(),
                font.verticalAlignment());
    }

    /** Maps a decoration mesh's cells proportionally into parentBox, recursing one depth
     *  deeper for each cell (subdecoration boxes, then subsubdecoration boxes, ...). */
    private void addDecorationBoxes(HintBox parentBox, int parentWidth, int parentHeight,
                                    HintMesh decorationMesh, List<DecorationStyle> decorationStyles,
                                    int depth, double qtScaleFactor) {
        if (decorationMesh == null || depth >= decorationStyles.size())
            return;
        DecorationStyle decorationStyle = decorationStyles.get(depth);
        Rectangle area = decorationMesh.backgroundArea();
        for (Hint cell : decorationMesh.hints()) {
            // Map the cell (in area coordinates) into the parent box proportionally,
            // so cells of any size tile cleanly.
            int decorationBoxLeft = (int) Math.round(
                    (cell.centerX() - cell.cellWidth() / 2 - area.x())
                    / area.width() * parentWidth);
            int decorationBoxTop = (int) Math.round(
                    (cell.centerY() - cell.cellHeight() / 2 - area.y())
                    / area.height() * parentHeight);
            int decorationBoxRight = (int) Math.round(
                    (cell.centerX() + cell.cellWidth() / 2 - area.x())
                    / area.width() * parentWidth);
            int decorationBoxBottom = (int) Math.round(
                    (cell.centerY() + cell.cellHeight() / 2 - area.y())
                    / area.height() * parentHeight);
            List<Key> labelKeys = decorationStyle.labelOverride().isEmpty() ?
                    cell.keySequence() : decorationStyle.labelOverride();
            HintBox decorationBox = new HintBox(null, decorationStyle.borderLengthPx(),
                    decorationStyle.borderThicknessPx(), decorationStyle.boxColor(),
                    decorationStyle.boxBorderColor(), true,
                    decorationBoxLeft == 0, decorationBoxTop == 0,
                    decorationBoxRight == parentWidth, decorationBoxBottom == parentHeight,
                    decorationStyle.boxFramed(), qtScaleFactor, decorationStyle.borderRadiusPx());
            decorationBox.setGeometry(decorationBoxLeft, decorationBoxTop,
                    decorationBoxRight - decorationBoxLeft, decorationBoxBottom - decorationBoxTop);
            decorationBox.setDecorationLabel(labelKeys.stream()
                            .map(Key::hintLabel).collect(Collectors.joining()),
                    decorationStyle.labelStyle(),
                    decorationStyle.labelVerticalAlignment());
            parentBox.decorationBoxes.add(decorationBox);
            addDecorationBoxes(decorationBox, decorationBoxRight - decorationBoxLeft,
                    decorationBoxBottom - decorationBoxTop,
                    decorationMesh.subDecoration(), decorationStyles, depth + 1,
                    qtScaleFactor);
        }
    }

    /**
     * Sets the QImage DPI to match the target screen so that point-size fonts
     * render at the correct pixel size. Without this, text painted into off-screen
     * QImages uses the primary screen's DPI, causing wrong-sized glyphs on
     * secondary screens with different scaling.
     */
    static void setQImageDpiForScreen(QImage image, double screenScale) {
        double dpi = QtHintFont.gdiFontEngine ? screenScale * 96.0 :
                QApplication.primaryScreen().logicalDotsPerInchX();
        int dotsPerMeter = (int) Math.round(dpi / 0.0254);
        image.setDotsPerMeterX(dotsPerMeter);
        image.setDotsPerMeterY(dotsPerMeter);
    }

    private void cacheQtHintWindowIntoPixmap(TransparentWindow window, ClearBackgroundQLabel container,
                                                    HintMesh hintMeshKey, HintMesh hintMesh,
                                                    List<HintBox> boxes, List<Rectangle> enclosingInk) {
        long before = System.nanoTime();
        // When morphing, the boxes are not in the container, so this grabs labels/shadows only.
        QPixmap pixmap = container.grab();
        PixmapAndPosition pixmapAndPosition =
                new PixmapAndPosition(pixmap, container.x(), container.y(), boxes, enclosingInk,
                        hintMesh, window.x(), window.y());
//         pixmap.save("screenshot.png", "PNG");
        hintMeshPixmaps.put(hintMeshKey, pixmapAndPosition);
        if (!preWarming)
            logger.debug("Cached hint mesh pixmap " + pixmapAndPosition + " in " +
                     (long) ((System.nanoTime() - before) / 1e6) + "ms, cache size is " +
                     hintMeshPixmaps.size());
        // Turn the live container into a pixmap label in place, so a later crop clips the pixmap
        // (smooth) rather than masking the live widget. In place means the shown widget never
        // changes, so there is no blank frame. The label layers are now in the pixmap, so drop them.
        container.setPixmap(pixmap);
        // If this grab happened mid-grow, the container was clipped with setMask (no pixmap yet, so
        // not crop-capable). With a pixmap it is crop-capable, so the remaining grow frames clip with
        // setCrop, which never touches the mask. Carry the mask's bounds over to the crop and clear
        // the mask, else it stays applied at the extent the grow had reached, clipping the pixmap to
        // only that region (the rest of the grid stays blank).
        QRegion mask = container.mask();
        if (!mask.isEmpty()) {
            QRect maskBounds = mask.boundingRect();
            container.setCrop(maskBounds);
            container.clearMask();
            maskBounds.dispose();
        }
        mask.dispose();
        for (QObject child : List.copyOf(container.children()))
            if (child instanceof HintPaintLayer layer) {
                layer.setParent(null);
                disposeWidget(layer);
            }
    }

    private HintMesh hintMeshKey(HintMesh hintMesh, List<Hint> hints) {
        return new HintMesh.HintMeshBuilder(hintMesh)
                .hints(trimmedHints(hints, hintMesh.selectedKeySequence()))
                .build();
    }

    private List<Hint> trimmedHints(List<Hint> hints,
                                           List<Key> selectedKeySequence) {
        double minHintCenterX = Double.MAX_VALUE;
        double minHintCenterY = Double.MAX_VALUE;
        for (Hint hint : hints) {
            if (!hint.startsWith(selectedKeySequence))
                continue;
            minHintCenterX = Math.min(minHintCenterX, hint.centerX());
            minHintCenterY = Math.min(minHintCenterY, hint.centerY());
        }
        if (minHintCenterX == 0 && minHintCenterY == 0)
            return hints;
        List<Hint> trimmedHints = new ArrayList<>();
        for (Hint hint : hints) {
            if (!hint.startsWith(selectedKeySequence))
                continue;
            trimmedHints.add(new Hint(hint.centerX() - minHintCenterX,
                    hint.centerY() - minHintCenterY,
                    hint.cellWidth(), hint.cellHeight(), hint.keySequence()));
        }
        return trimmedHints;
    }

    /** boxes is the live box layer's boxes for a morphing grid, redrawn on a cache hit; null otherwise.
     *  enclosingInk is where the borders enclosing them put ink, in container coordinates. */
    private record PixmapAndPosition(QPixmap pixmap, int x, int y, List<HintBox> boxes,
                                     List<Rectangle> enclosingInk,
                                     HintMesh originalHintMesh, int windowX, int windowY) {
        @Override
        public String toString() {
            return pixmap.width() + "x" + pixmap.height() + " at (" + x + ", " + y + ")";
        }
    }

    /**
     * When QT_ENABLE_HIGHDPI_SCALING is not 0 (e.g. Linux/macOS), then Qt draws in points and the
     * hints, which are in pixels, are scaled down by the screen they are on. Widget geometry is in
     * points, so it is divided by this; everything painted inside one is in pixels.
     */
    private static int scaledLength(double length, double screenScale) {
        return (int) Math.round(length * screenScale);
    }

    private static Shadow scaledShadow(Shadow shadow, double screenScale) {
        return new Shadow(shadow.blurRadius() * screenScale, shadow.hexColor(),
                shadow.opacity(), shadow.horizontalOffset() * screenScale,
                shadow.verticalOffset() * screenScale, shadow.stackCount());
    }

    private static double qtScaleFactor(double screenScale) {
        return Os.windows ? 1 : screenScale;
    }

    private int hintRoundedX(double centerX, double cellWidth,
                                    double qtScaleFactor) {
        return (int) Math.round((centerX - cellWidth / 2) / qtScaleFactor);
    }

    private int hintRoundedY(double centerY, double cellHeight,
                                    double qtScaleFactor) {
        return (int) Math.round((centerY - cellHeight / 2) / qtScaleFactor);
    }

    private int hintRoundedRight(double centerX, double cellWidth,
                                    double qtScaleFactor) {
        return (int) Math.round((centerX + cellWidth / 2) / qtScaleFactor);
    }

    private int hintRoundedBottom(double centerY, double cellHeight,
                                    double qtScaleFactor) {
        return (int) Math.round((centerY + cellHeight / 2) / qtScaleFactor);
    }

    public static class HintBox {

        private final Hint hint;
        private final boolean isHintPartOfGrid;
        private final boolean gridLeftEdge;
        private final boolean gridTopEdge;
        private final boolean gridRightEdge;
        private final boolean gridBottomEdge;
        private final boolean drawGridEdgeBorders;
        private final double qtScaleFactor;
        private final int borderLength;
        private final int borderThickness;
        private QBrush color;
        private final QColor borderColor;
        private final int borderRadius;
        private int x, y, width, height;
        /** Set while drawBorders records where it would paint instead of painting. */
        private List<Rectangle> ink;
        final List<HintBox> decorationBoxes = new ArrayList<>();
        private String decorationLabel;
        private QFont decorationLabelFont;
        private QColor decorationLabelColor;
        private int decorationLabelX;
        private double decorationLabelY;
        private int decorationLabelWidth, decorationLabelAscent, decorationLabelDescent;

        public HintBox(Hint hint, int borderLength, int borderThickness, QBrush color, QColor borderColor,
                       boolean isHintPartOfGrid,
                       boolean gridLeftEdge, boolean gridTopEdge, boolean gridRightEdge, boolean gridBottomEdge,
                       boolean drawGridEdgeBorders,
                       double qtScaleFactor,
                       int borderRadius) {
            this.hint = hint;
            this.isHintPartOfGrid = isHintPartOfGrid;
            this.gridLeftEdge = gridLeftEdge;
            this.gridTopEdge = gridTopEdge;
            this.gridRightEdge = gridRightEdge;
            this.gridBottomEdge = gridBottomEdge;
            this.drawGridEdgeBorders = drawGridEdgeBorders;
            this.qtScaleFactor = qtScaleFactor;
            this.borderLength = borderLength;
            this.borderThickness = borderThickness;
            this.color = color;
            this.borderColor = borderColor;
            this.borderRadius = borderRadius;
        }

        /** A geometry-only copy (style + geometry, no labels/decorations) for the border layer to animate. */
        HintBox(HintBox other) {
            this(other.hint, other.borderLength, other.borderThickness, other.color, other.borderColor,
                    other.isHintPartOfGrid, other.gridLeftEdge, other.gridTopEdge, other.gridRightEdge,
                    other.gridBottomEdge, other.drawGridEdgeBorders, other.qtScaleFactor, other.borderRadius);
            setGeometry(other.x, other.y, other.width, other.height);
        }

        void setColor(QBrush color) {
            this.color = color;
        }

        public void setGeometry(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        void setGeometry(Rectangle r) {
            setGeometry(r.x(), r.y(), r.width(), r.height());
        }

        public void setDecorationLabel(String label, QtFontStyle labelStyle,
                                       FontVerticalAlignment verticalAlignment) {
            this.decorationLabel = label;
            this.decorationLabelFont = labelStyle.font();
            this.decorationLabelColor = labelStyle.color();
            if (!label.isEmpty()) {
                QFontMetrics metrics = labelStyle.metrics();
                int advance = metrics.horizontalAdvance(label);
                this.decorationLabelX = (width - advance) / 2;
                QtHintFont.Ink ink = QtHintFont.ink(metrics, label);
                this.decorationLabelY =
                        baselineY(verticalAlignment, height, metrics, ink.top(), ink.height(),
                                qtScaleFactor);
                this.decorationLabelWidth = advance;
                this.decorationLabelAscent = metrics.ascent();
                this.decorationLabelDescent = metrics.descent();
            }
        }

        /** Where this depth's decoration labels put ink, in the coordinates they are painted in.
         *  Descends accumulating the offset paintDecorationLabels translates by. */
        void collectDecorationLabelBounds(int depth, int offsetX, int offsetY,
                                          List<Rectangle> bounds) {
            if (depth == 0) {
                if (decorationLabel != null && !decorationLabel.isEmpty()
                    && decorationLabelFont != null)
                    bounds.add(new Rectangle(offsetX + x + decorationLabelX,
                            offsetY + y + (int) Math.round(decorationLabelY) - decorationLabelAscent,
                            decorationLabelWidth,
                            decorationLabelAscent + decorationLabelDescent));
                return;
            }
            for (HintBox decorationBox : decorationBoxes)
                decorationBox.collectDecorationLabelBounds(depth - 1, offsetX + x,
                        offsetY + y, bounds);
        }

        public void move(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int x() { return x; }
        public int y() { return y; }
        public int width() { return width; }
        public int height() { return height; }

        public QRect geometry() {
            return new QRect(x, y, width, height);
        }

        Rectangle rectangle() {
            return new Rectangle(x, y, width, height);
        }

        /**
         * A thickness, radius or length is a device pixel count, but Qt paints in points on the
         * platforms where it scales for the screen itself, so the box paints in pixels.
         */
        private void scaleToPixels(QPainter painter) {
            if (qtScaleFactor != 1)
                painter.scale(1 / qtScaleFactor, 1 / qtScaleFactor);
        }

        private int pixels(int points) {
            return qtScaleFactor == 1 ? points :
                    (int) Math.round(points * qtScaleFactor);
        }

        public void paint(QPainter painter) {
            painter.save();
            scaleToPixels(painter);
            paintInPixels(painter);
            painter.restore();
        }

        /** The fill is drawn where it lands, so one gradient brush can span every box. */
        private void paintInPixels(QPainter painter) {
            painter.save();
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
            int left = pixels(x);
            int top = pixels(y);
            int pixelWidth = pixels(width);
            int pixelHeight = pixels(height);
            if (borderRadius > 0) {
                // Draw background and border as a single rounded rect so
                // the background does not bleed outside the border at corners.
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
                if (borderThickness != 0) {
                    painter.setBrush(color != null ? color : QtColorUtil.noBrush());
                    painter.setPen(createPen(borderColor, borderThickness));
                    double offset = borderThickness / 2d;
                    painter.drawRoundedRect(new QRectF(left + offset, top + offset,
                                    pixelWidth - borderThickness,
                                    pixelHeight - borderThickness),
                            borderRadius, borderRadius);
                }
                else if (color != null) {
                    painter.setBrush(color);
                    painter.setPen(Qt.PenStyle.NoPen);
                    painter.drawRoundedRect(left, top, pixelWidth, pixelHeight, borderRadius,
                            borderRadius);
                }
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, false);
            }
            else {
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, false);
                if (color != null) {
                    painter.setBrush(color);
                    painter.setPen(Qt.PenStyle.NoPen);
                    painter.drawRoundedRect(left, top, pixelWidth, pixelHeight, 0, 0);
                }
            }
            painter.translate(left, top);
            if (borderRadius == 0 && borderThickness != 0)
                drawBorders(painter);
            for (HintBox decorationBox : decorationBoxes) {
                decorationBox.paintInPixels(painter);
            }
            painter.restore();
        }

        /** Drops the decoration labels that would land on the center of this box when a label already
         *  occupies it, recursing so that each level's own label occupies its center for the level
         *  below. */
        void dropDecorationLabelsOnOccupiedCenter(boolean centerOccupied,
                                                  List<DecorationStyle> decorationStyles,
                                                  int depth) {
            if (depth >= decorationStyles.size())
                return;
            boolean labelVisible = decorationStyles.get(depth).labelVisible();
            for (HintBox decorationBox : decorationBoxes) {
                boolean onCenter = concentric(decorationBox, width, height);
                if (centerOccupied && onCenter && labelVisible)
                    decorationBox.decorationLabel = null;
                boolean labelDrawn = labelVisible && decorationBox.decorationLabel != null
                                     && !decorationBox.decorationLabel.isEmpty();
                decorationBox.dropDecorationLabelsOnOccupiedCenter(
                        labelDrawn || (centerOccupied && onCenter), decorationStyles, depth + 1);
            }
        }

        /** Draws the decoration labels nested {@code depth} levels down. They are painted on their own
         *  layer, one per depth, so a depth's shadow applies to the layer as hint label shadows do. */
        void paintDecorationLabels(QPainter painter, int depth) {
            painter.save();
            painter.translate(x, y);
            if (depth == 0) {
                if (decorationLabel != null && !decorationLabel.isEmpty()
                    && decorationLabelFont != null) {
                    painter.setFont(decorationLabelFont);
                    painter.setPen(decorationLabelColor);
                    painter.drawText(new QPointF(decorationLabelX, decorationLabelY), decorationLabel);
                }
            }
            else
                for (HintBox decorationBox : decorationBoxes)
                    decorationBox.paintDecorationLabels(painter, depth - 1);
            painter.restore();
        }

        /** Everything but the box's own border — the part that crops with the container. */
        void paintWithoutBorder(QPainter painter) {
            painter.save();
            scaleToPixels(painter);
            paintWithoutBorderInPixels(painter);
            painter.restore();
        }

        private void paintWithoutBorderInPixels(QPainter painter) {
            painter.save();
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
            int left = pixels(x);
            int top = pixels(y);
            if (color != null) {
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, borderRadius > 0);
                painter.setBrush(color);
                painter.setPen(Qt.PenStyle.NoPen);
                if (borderRadius > 0 && borderThickness != 0) {
                    // The border strokes this same inset rect on its own layer; filling it (not the
                    // full box) keeps the background from bleeding past the rounded border, the way
                    // paint() does by drawing both in one call.
                    double offset = borderThickness / 2d;
                    painter.drawRoundedRect(new QRectF(left + offset, top + offset,
                                    pixels(width) - borderThickness,
                                    pixels(height) - borderThickness),
                            borderRadius, borderRadius);
                }
                else
                    painter.drawRoundedRect(left, top, pixels(width), pixels(height),
                            borderRadius, borderRadius);
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, false);
            }
            painter.translate(left, top);
            for (HintBox decorationBox : decorationBoxes)
                decorationBox.paintInPixels(painter);
            painter.restore();
        }

        /** Where this box's border, and its decorations' in turn, put ink, in the coordinates its
         *  parent paints it in. */
        void collectBorderInk(int offsetX, int offsetY, List<Rectangle> ink) {
            if (borderThickness != 0 && borderColor.alpha() != 0) {
                this.ink = new ArrayList<>();
                drawBorders(null);
                for (Rectangle rectangle : this.ink) {
                    // Recorded in pixels, given back in the points the parent paints in, and
                    // grown to whole ones so it still covers what the border draws.
                    int left = (int) Math.floor(rectangle.x() / qtScaleFactor);
                    int top = (int) Math.floor(rectangle.y() / qtScaleFactor);
                    int right = (int) Math.ceil(
                            (rectangle.x() + rectangle.width()) / qtScaleFactor);
                    int bottom = (int) Math.ceil(
                            (rectangle.y() + rectangle.height()) / qtScaleFactor);
                    ink.add(new Rectangle(offsetX + x + left, offsetY + y + top,
                            right - left, bottom - top));
                }
                this.ink = null;
            }
            for (HintBox decorationBox : decorationBoxes)
                decorationBox.collectBorderInk(offsetX + x, offsetY + y, ink);
        }

        /** The box's own border only — the part the border layer draws and animates. */
        void paintBorder(QPainter painter) {
            if (borderThickness == 0)
                return;
            painter.save();
            scaleToPixels(painter);
            painter.translate(pixels(x), pixels(y));
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
            if (borderRadius > 0) {
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
                painter.setBrush(QtColorUtil.noBrush());
                painter.setPen(createPen(borderColor, borderThickness));
                // Half the pen, not half of it rounded down: a border one pixel thick strokes
                // half a pixel each side of the rect, and the box would keep only the inner half.
                double offset = borderThickness / 2d;
                painter.drawRoundedRect(new QRectF(offset, offset,
                        pixels(width) - borderThickness, pixels(height) - borderThickness),
                        borderRadius, borderRadius);
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, false);
            }
            else {
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, false);
                drawBorders(painter);
            }
            painter.restore();
        }

        /**
         * Paints the box shape with opaque white, used as the source image for shadow rendering.
         * The overall box silhouette (fill area including border thickness) is all that matters.
         * That image is in pixels, so this paints without the scale a widget needs.
         */
        public void paintOpaque(QPainter painter) {
            painter.save();
            painter.translate(pixels(x), pixels(y));
            painter.setBrush(QtColorUtil.opaqueWhiteBrush());
            painter.setPen(Qt.PenStyle.NoPen);
            if (borderRadius > 0) {
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
                painter.drawRoundedRect(0, 0, pixels(width), pixels(height), borderRadius,
                        borderRadius);
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, false);
            }
            else {
                painter.drawRect(0, 0, pixels(width), pixels(height));
            }
            painter.restore();
        }

        private void drawBorders(QPainter painter) {
            // Draw borders.
            // With QT_ENABLE_HIGHDPI_SCALING=0:
            // draw vertical line penwidth 1 at x=0: x=0 (0 is the widget's left)
            // draw vertical line penwidth 2 at x=0: x=0, x=-1
            // draw vertical line penwidth 3 at x=0: x=0, x=1, x=-1
            // draw vertical line penwidth 4 at x=0: x=0, x=1, x=-1, x=-2
            // draw vertical line penwidth 5 at x=0: x=0, x=1, x=2, x=-1, x=-2
            // Qt won't draw anything x < 0, but will draw x >= width().
            int top = 0;
            int bottom = pixels(height) - 1;
            int left = 0;
            int right = pixels(width) - 1;
            int edgeThickness = borderThickness;
            // Full thickness if grid edge.
            // Otherwise, half thickness: thickness/2 + thickness%2 for top and left, thickness/2 for bottom and right
            int topLeftInsideThickness = borderThickness / 2 + borderThickness % 2;
            int bottomRightInsideThickness = isHintPartOfGrid ? borderThickness / 2 : topLeftInsideThickness;
            QPen edgePen = createPen(borderColor, edgeThickness);
            QPen insidePen = createPen(borderColor, topLeftInsideThickness);
            // penOffset so that drawLine(x) draws at x, x+1, ... (no x-1, x-2, ...)
            // For grid edge borders, offset so the full border is inside the cell.
            // For non-edge borders, center the pen on the cell boundary (offset=0)
            // so that half the border is in each adjacent cell.
            int topEdgePenOffset = gridTopEdge ? edgeThickness / 2 : 0;
            int leftEdgePenOffset = gridLeftEdge ? edgeThickness / 2 : 0;
            int bottomEdgePenOffset = edgeThickness / 2;
            int rightEdgePenOffset = edgeThickness / 2;
            int insidePenOffset = borderThickness / 4;
            // Extra length for horizontal stubs: vertical pen coverage inside cell at each edge,
            // so that the visible arm past the vertical border = borderLength/2.
            int horzLeftExtra, horzRightExtra;
            if (drawGridEdgeBorders) {
                horzLeftExtra = gridLeftEdge ? borderThickness : (borderThickness + 1) / 2;
                horzRightExtra = gridRightEdge ? borderThickness : borderThickness / 2;
            } else {
                horzLeftExtra = gridLeftEdge ? edgeThickness / 2 : (borderThickness + 1) / 2;
                horzRightExtra = gridRightEdge ? edgeThickness / 2 : (borderThickness + 1) / 2;
            }
            // Compute segment endpoints for each border edge pair (top-left half + bottom-right half).
            // When borderLength is large, the two halves overlap; merge into a single draw.
            // Shorten vertical borders at corners where horizontal borders are drawn,
            // so each corner pixel is drawn exactly once (by the horizontal).
            // LEFT border: TL vertical (top half) and BL vertical (bottom half).
            boolean leftTopHorzDrawn = drawGridEdgeBorders || (!gridTopEdge && !gridLeftEdge);
            boolean leftBottomHorzDrawn = (drawGridEdgeBorders && gridBottomEdge) || (!drawGridEdgeBorders && !gridBottomEdge && !gridLeftEdge);
            int leftTopShortenAmount = leftTopHorzDrawn ? (gridTopEdge ? borderThickness : (borderThickness + 1) / 2) : 0;
            int leftBottomShortenAmount;
            if (leftBottomHorzDrawn) {
                boolean bottomIsEdge = drawGridEdgeBorders || gridBottomEdge;
                if (bottomIsEdge) {
                    leftBottomShortenAmount = borderThickness;
                } else {
                    // Non-edge bottom horizontal pen coverage inside this cell.
                    leftBottomShortenAmount = bottomRightInsideThickness - insidePenOffset + topLeftInsideThickness / 2;
                }
            } else if (drawGridEdgeBorders && !gridBottomEdge) {
                // Adjacent cell's horizontal pen extends borderThickness/2 into this cell.
                leftBottomShortenAmount = borderThickness / 2;
            } else {
                leftBottomShortenAmount = 0;
            }
            int leftTopStart = top + leftTopShortenAmount;
            int leftTopEnd = Math.min(bottom - borderThickness + 1, top + leftTopShortenAmount + borderLength / 2);
            int leftBottomStart = Math.max(top + borderThickness, bottom + 1 - leftBottomShortenAmount - borderLength / 2);
            int leftBottomEnd = bottom + 1 - leftBottomShortenAmount;
            boolean leftMerged = leftTopEnd >= leftBottomStart;
            if (leftMerged) {
                leftTopEnd = leftBottomEnd;
            }
            // TOP border: TL horizontal (left half) and TR horizontal (right half).
            int topLeftEnd = Math.min(right - borderThickness + 1, left + horzLeftExtra + borderLength / 2);
            int topRightStart = Math.max(left + borderThickness, right + 1 - horzRightExtra - borderLength / 2);
            boolean topMerged = topLeftEnd >= topRightStart;
            if (topMerged) {
                topLeftEnd = right + 1;
            }
            // RIGHT border: TR vertical (top half) and BR vertical (bottom half).
            boolean rightTopHorzDrawn = drawGridEdgeBorders || (!gridTopEdge && !gridRightEdge);
            boolean rightBottomHorzDrawn = (drawGridEdgeBorders && gridBottomEdge) || (!drawGridEdgeBorders && !gridBottomEdge && !gridRightEdge);
            int rightTopShortenAmount = rightTopHorzDrawn ? (gridTopEdge ? borderThickness : (borderThickness + 1) / 2) : 0;
            int rightBottomShortenAmount;
            if (rightBottomHorzDrawn) {
                boolean bottomIsEdge = drawGridEdgeBorders || gridBottomEdge;
                if (bottomIsEdge) {
                    rightBottomShortenAmount = borderThickness;
                } else {
                    // Non-edge bottom horizontal pen coverage inside this cell.
                    rightBottomShortenAmount = bottomRightInsideThickness - insidePenOffset + topLeftInsideThickness / 2;
                }
            } else if (drawGridEdgeBorders && !gridBottomEdge) {
                // Adjacent cell's horizontal pen extends borderThickness/2 into this cell.
                rightBottomShortenAmount = borderThickness / 2;
            } else {
                rightBottomShortenAmount = 0;
            }
            int rightTopStart = top + rightTopShortenAmount;
            int rightTopEnd = Math.min(bottom - borderThickness + 1, top + rightTopShortenAmount + borderLength / 2);
            int rightBottomStart = Math.max(top + borderThickness, bottom + 1 - rightBottomShortenAmount - borderLength / 2);
            int rightBottomEnd = bottom + 1 - rightBottomShortenAmount;
            boolean rightMerged = rightTopEnd >= rightBottomStart;
            if (rightMerged) {
                rightTopEnd = rightBottomEnd;
            }
            // BOTTOM border: BL horizontal (left half) and BR horizontal (right half).
            int bottomLeftEnd = Math.min(right - borderThickness + 1, left + horzLeftExtra + borderLength / 2);
            int bottomRightStart = Math.max(left + borderThickness, right + 1 - horzRightExtra - borderLength / 2);
            boolean bottomMerged = bottomLeftEnd >= bottomRightStart;
            if (bottomMerged) {
                bottomLeftEnd = right + 1;
            }
            // Top left corner.
            // Vertical line (LEFT top half, or full LEFT if merged).
            drawVerticalGridLine(painter,
                    drawGridEdgeBorders || (!gridLeftEdge && (!gridTopEdge || leftMerged)),
                    drawGridEdgeBorders || gridLeftEdge,
                    edgePen,
                    insidePen,
                    left,
                    leftEdgePenOffset,
                    insidePenOffset,
                    leftTopStart,
                    leftTopEnd
            );
            // Horizontal line (TOP left half, or full TOP if merged).
            drawHorizontalGridLine(painter,
                    drawGridEdgeBorders || (!gridTopEdge && (!gridLeftEdge || topMerged)),
                    drawGridEdgeBorders || gridTopEdge,
                    edgePen,
                    insidePen,
                    top,
                    topEdgePenOffset,
                    insidePenOffset,
                    left,
                    topLeftEnd
            );
            // Top right corner.
            // Vertical line (RIGHT top half, or full RIGHT if merged).
            drawVerticalGridLine(painter,
                    (drawGridEdgeBorders && gridRightEdge) || (!drawGridEdgeBorders && !gridRightEdge && (!gridTopEdge || rightMerged)),
                    drawGridEdgeBorders || gridRightEdge,
                    edgePen,
                    insidePen,
                    right,
                    rightEdgePenOffset - (edgeThickness - 1),
                    insidePenOffset - (bottomRightInsideThickness - 1),
                    rightTopStart,
                    rightTopEnd
            );
            // Horizontal line (TOP right half, skipped if merged).
            drawHorizontalGridLine(painter,
                    !topMerged && (drawGridEdgeBorders || (!gridTopEdge && !gridRightEdge)),
                    drawGridEdgeBorders || gridTopEdge,
                    edgePen,
                    insidePen,
                    top,
                    topEdgePenOffset,
                    insidePenOffset,
                    topRightStart,
                    right + 1
            );
            // Bottom left corner.
            // Vertical line (LEFT bottom half, skipped if merged).
            drawVerticalGridLine(painter,
                    !leftMerged && (drawGridEdgeBorders || (!gridLeftEdge && !gridBottomEdge)),
                    drawGridEdgeBorders || gridLeftEdge,
                    edgePen,
                    insidePen,
                    left,
                    leftEdgePenOffset,
                    insidePenOffset,
                    leftBottomStart,
                    leftBottomEnd
            );
            // Horizontal line (BOTTOM left half, or full BOTTOM if merged).
            drawHorizontalGridLine(painter,
                    (drawGridEdgeBorders && gridBottomEdge) || (!drawGridEdgeBorders && !gridBottomEdge && (!gridLeftEdge || bottomMerged)),
                    drawGridEdgeBorders || gridBottomEdge,
                    edgePen,
                    insidePen,
                    bottom,
                    bottomEdgePenOffset - (edgeThickness - 1),
                    insidePenOffset - (bottomRightInsideThickness - 1),
                    left,
                    bottomLeftEnd
            );
            // Bottom right corner.
            // Vertical line (RIGHT bottom half, skipped if merged).
            drawVerticalGridLine(painter,
                    !rightMerged && ((drawGridEdgeBorders && gridRightEdge) || (!drawGridEdgeBorders && !gridRightEdge && !gridBottomEdge)),
                    drawGridEdgeBorders || gridRightEdge,
                    edgePen,
                    insidePen,
                    right,
                    rightEdgePenOffset - (edgeThickness - 1),
                    insidePenOffset - (bottomRightInsideThickness - 1),
                    rightBottomStart,
                    rightBottomEnd
            );
            // Horizontal line (BOTTOM right half, skipped if merged).
            drawHorizontalGridLine(painter,
                    !bottomMerged && ((drawGridEdgeBorders && gridBottomEdge) || (!drawGridEdgeBorders && !gridBottomEdge && !gridRightEdge)),
                    drawGridEdgeBorders || gridBottomEdge,
                    edgePen,
                    insidePen,
                    bottom,
                    bottomEdgePenOffset - (edgeThickness - 1),
                    insidePenOffset - (bottomRightInsideThickness - 1),
                    bottomRightStart,
                    right + 1
            );
        }

        private void drawVerticalGridLine(
                QPainter painter,
                boolean condition,
                boolean isEdge,
                QPen edgePen,
                QPen insidePen,
                int xBase,
                int edgeOffset,
                int insideOffset,
                int y1,
                int y2
        ) {
            if (!condition)
                return;
            if (y1 > y2)
                return;
            QPen pen = isEdge ? edgePen : insidePen;
            if (pen.width() == 0)
                return;
            int x = xBase + (isEdge ? edgeOffset : insideOffset);
            if (ink != null) {
                ink.add(new Rectangle(x - pen.width() / 2, y1, pen.width(), y2 - y1 + 1));
                return;
            }
            painter.setPen(pen);
            painter.drawLine(x, y1, x, y2);
        }

        private void drawHorizontalGridLine(
                QPainter painter,
                boolean condition,
                boolean isEdge,
                QPen edgePen,
                QPen insidePen,
                int yBase,
                int edgeOffset,
                int insideOffset,
                int x1,
                int x2
        ) {
            if (!condition)
                return;
            if (x1 > x2)
                return;
            QPen pen = isEdge ? edgePen : insidePen;
            if (pen.width() == 0)
                return;
            int y = yBase + (isEdge ? edgeOffset : insideOffset);
            if (ink != null) {
                ink.add(new Rectangle(x1, y - pen.width() / 2, x2 - x1 + 1, pen.width()));
                return;
            }
            painter.setPen(pen);
            painter.drawLine(x1, y, x2, y);
        }

        private QPen createPen(QColor color, int penWidth) {
            // Default is square cap.
            return QtColorUtil.qPen(color, penWidth, Qt.PenCapStyle.FlatCap,
                    Qt.PenJoinStyle.BevelJoin);
        }

    }

    private static double baselineY(FontVerticalAlignment verticalAlignment, int boxHeight,
                                    QFontMetrics metrics, double inkTop, double inkHeight,
                                    double qtScaleFactor) {
        return switch (verticalAlignment) {
            // The ink starts on a whole pixel, a leftover one going above the text, not below.
            // A pixel, not a point: where Qt paints in points, half of one is still a pixel, and
            // rounding to the point would push the text off center by one.
            case MIDDLE -> topGap(boxHeight - inkHeight, qtScaleFactor) - inkTop;
            case CAP_HEIGHT -> topGap(boxHeight - metrics.capHeight(), qtScaleFactor) +
                               metrics.capHeight();
            case BASELINE -> (boxHeight + metrics.ascent() - metrics.descent()) / 2d;
        };
    }

    private static double topGap(double slack, double qtScaleFactor) {
        return Math.round(slack / 2 * qtScaleFactor) / qtScaleFactor;
    }

    public static class HintLabel {

        /** Refilled by every outline paint; painting is single-threaded. */
        private static final QPainterPath outlinePath = new QPainterPath();
        private static final Map<OutlineKey, OutlineImage> outlineImages = new HashMap<>();

        static void clearOutlineCache() {
            for (OutlineImage outline : outlineImages.values())
                outline.image().dispose();
            outlineImages.clear();
        }

        /** A label's glyph placement and tight bounds are identical every trigger once its
         *  screen position is stripped, so it is built once and copied. */
        private record LabelKey(String label, int boxWidth, int boxHeight, int totalXAdvance,
                                int prefixLength, int hintKeyMaxXAdvance, int selectedKeyEndIndex,
                                FontVerticalAlignment verticalAlignment,
                                boolean tiledGrid, double qtScaleFactor,
                                QtHintFontStyle labelFontStyle) {
        }

        private static final Map<LabelKey, HintLabel> labelCache = new HashMap<>();

        static void clearLabelCache() {
            labelCache.clear();
        }

        static HintLabel of(List<Key> keySequence, Map<String, Integer> xAdvancesByString,
                            int boxWidth, int boxHeight, int totalXAdvance, int prefixLength,
                            QtHintFontStyle labelFontStyle, int hintKeyMaxXAdvance,
                            int selectedKeyEndIndex, FontVerticalAlignment verticalAlignment,
                            boolean isHintPartOfTiledGrid, double qtScaleFactor) {
            StringBuilder label = new StringBuilder();
            for (Key key : keySequence)
                label.append(key.hintLabel()).append('\n');
            LabelKey key = new LabelKey(label.toString(), boxWidth, boxHeight, totalXAdvance,
                    prefixLength, hintKeyMaxXAdvance, selectedKeyEndIndex, verticalAlignment,
                    isHintPartOfTiledGrid, qtScaleFactor, labelFontStyle);
            HintLabel cached = labelCache.get(key);
            if (cached != null)
                return new HintLabel(cached);
            HintLabel built = new HintLabel(keySequence, xAdvancesByString, boxWidth, boxHeight,
                    totalXAdvance, prefixLength, labelFontStyle, hintKeyMaxXAdvance,
                    selectedKeyEndIndex, verticalAlignment, isHintPartOfTiledGrid,
                    qtScaleFactor);
            labelCache.put(key, built);
            return built;
        }

        /** A glyph and where it sits in the label it belongs to. */
        private record GlyphPlacement(String text, double x, double y) {
        }

        /** Everything the rasterized outline of a label depends on. */
        private record OutlineKey(List<GlyphPlacement> glyphs, int rgba, int thickness) {
        }

        private record OutlineImage(QImage image, int x, int y) {
        }

        private final QtHintFontStyle labelFontStyle;
        private final List<HintKeyText> keyTexts;
        /** Only a non-grid hint's box is sized and placed to fit its label; a grid hint's box is its
         *  cell, so its left is left at 0. */
        final int tightHintBoxLeft;
        final int tightHintBoxWidth;
        final int tightHintBoxHeight;
        int left;
        int top;
        int x, y, width, height;

        public HintLabel(List<Key> keySequence, Map<String, Integer> xAdvancesByString,
                         int boxWidth,
                         int boxHeight, int totalXAdvance, int prefixLength,
                         QtHintFontStyle labelFontStyle,
                         int hintKeyMaxXAdvance, int selectedKeyEndIndex,
                         FontVerticalAlignment verticalAlignment,
                         boolean isHintPartOfTiledGrid, double qtScaleFactor) {
            this.labelFontStyle = labelFontStyle;

            QFontMetrics labelMetrics = labelFontStyle.defaultStyle().metrics();
            double smallestColAlignedFontBoxWidth = hintKeyMaxXAdvance * keySequence.size();
            double smallestColAlignedFontBoxWidthPercent =
                    Math.min(1, smallestColAlignedFontBoxWidth / boxWidth);
            // We want font spacing percent 0.5 be the min spacing that keeps column alignment.
            double adjustedFontBoxWidthPercent = labelFontStyle.fontSpacingPercent() < 0.5d ?
                    (labelFontStyle.fontSpacingPercent() * 2) * smallestColAlignedFontBoxWidthPercent
                    : smallestColAlignedFontBoxWidthPercent + (labelFontStyle.fontSpacingPercent() - 0.5d) * 2 * (1 - smallestColAlignedFontBoxWidthPercent) ;
            boolean doNotColAlign = keySequence.size() != 1 &&
                                    adjustedFontBoxWidthPercent < smallestColAlignedFontBoxWidthPercent;
            double extraNotAlignedWidth = smallestColAlignedFontBoxWidth -
                                          totalXAdvance;
            extraNotAlignedWidth = adjustedFontBoxWidthPercent * extraNotAlignedWidth;

            keyTexts = new ArrayList<>(keySequence.size());
            // The ink of every key at once, so the label gets one baseline: measuring each key on
            // its own drops whatever overshoots the baseline, G and S in Helvetica Neue, a pixel
            // below the letters that stop at it.
            double inkTop = Double.MAX_VALUE;
            double inkBottom = -Double.MAX_VALUE;
            for (int keyIndex = 0; keyIndex < keySequence.size(); keyIndex++) {
                QtFontStyle keyStyle = keyStyle(keyIndex, prefixLength, selectedKeyEndIndex);
                QtHintFont.Ink ink = QtHintFont.ink(keyStyle.metrics(),
                        keySequence.get(keyIndex).hintLabel());
                inkTop = Math.min(inkTop, ink.top());
                inkBottom = Math.max(inkBottom, ink.bottom());
            }
            int xAdvance = 0;
            int smallestHintBoxLeft = 0;
            int smallestHintBoxWidth = 0;
            double tightLeft = Double.MAX_VALUE;
            double tightRight = -Double.MAX_VALUE;
            for (int keyIndex = 0; keyIndex < keySequence.size(); keyIndex++) {
                Key key = keySequence.get(keyIndex);
                String keyText = key.hintLabel();
                int textWidth = xAdvancesByString.get(keyText);
                int x;
                int keyWidth;
                if (doNotColAlign) {
                    // Extra is added between each letter (not to the left of the leftmost letter,
                    // nor to the right of the rightmost letter).
                    x = (int) (boxWidth / 2d - (totalXAdvance + extraNotAlignedWidth) / 2
                                                   + xAdvance
                    );
                    if (keyIndex == 0) {
                        smallestHintBoxLeft = x;
                    }
                    if (keyIndex == keySequence.size() - 1) {
                        smallestHintBoxWidth = x - smallestHintBoxLeft + textWidth;
                    }
                    xAdvance += textWidth;
                    if (keyIndex != keySequence.size() - 1)
                        xAdvance +=
                                (int) (extraNotAlignedWidth / (keySequence.size() - 1));
                    keyWidth = textWidth;
                }
                else {
                    // 0.8d adjustedFontBoxWidthPercent means characters spread over 80% of the cell width.
                    // If we are here, hint.keySequence().size() is 2 or more (else, doNotColAlign would be true).
                    double fontBoxWidth = boxWidth * adjustedFontBoxWidthPercent;
                    double unusedBoxWidth = boxWidth - fontBoxWidth;
                    double keyBoxWidth = fontBoxWidth / keySequence.size();
                    x = (int) (unusedBoxWidth / 2 + keyBoxWidth * keyIndex + (keyBoxWidth - textWidth) / 2);
                    keyWidth = (int) keyBoxWidth;
                    if (keyIndex == 0) {
                        smallestHintBoxLeft = x;
                    }
                    if (keyIndex == keySequence.size() - 1) {
                        smallestHintBoxWidth = x - smallestHintBoxLeft + textWidth;
                    }
                }
                boolean isPrefix = prefixLength != -1 && keyIndex <= prefixLength - 1;
                boolean isSelected = keyIndex <= selectedKeyEndIndex;
                boolean isFocused = keyIndex == selectedKeyEndIndex + 1;
                int textX = x;
                QFontMetrics keyMetrics =
                        keyMetrics(keyIndex, labelMetrics, prefixLength, selectedKeyEndIndex);
                if (labelFontStyle.perKeyFont()) {
                    int actualTextWidth = keyMetrics.horizontalAdvance(keyText);
                    textX += (textWidth - actualTextWidth) / 2;
                }
                double textY = baselineY(verticalAlignment, boxHeight, keyMetrics, inkTop,
                        inkBottom - inkTop, qtScaleFactor);
                if (!isHintPartOfTiledGrid) {
                    QtHintFont.Ink keyInk = QtHintFont.ink(keyMetrics, keyText);
                    tightLeft = Math.min(tightLeft, textX + keyInk.left());
                    tightRight = Math.max(tightRight, textX + keyInk.right());
                }
                keyTexts.add(new HintKeyText(keyText, textX, textY, keyWidth,
                        isSelected, isFocused, isPrefix));
            }
            // No columns to align to: center the label's tight bounds instead of per-key slots.
            if (!isHintPartOfTiledGrid && tightRight > tightLeft) {
                double shiftX = Math.round(((boxWidth - (tightRight - tightLeft)) / 2 - tightLeft)
                                           * qtScaleFactor) / qtScaleFactor;
                if (shiftX != 0) {
                    keyTexts.replaceAll(k -> new HintKeyText(k.text(), k.x() + shiftX, k.y(),
                            k.width(), k.isSelected(), k.isFocused(), k.isPrefix()));
                    smallestHintBoxLeft += shiftX;
                }
            }
            this.tightHintBoxLeft = smallestHintBoxLeft;
            this.tightHintBoxWidth = smallestHintBoxWidth;
            this.tightHintBoxHeight = labelMetrics.height();
        }

        /** Reuses a cached label's layout; only the screen position, set by the caller, differs. */
        private HintLabel(HintLabel template) {
            this.labelFontStyle = template.labelFontStyle;
            this.keyTexts = template.keyTexts;
            this.tightHintBoxLeft = template.tightHintBoxLeft;
            this.tightHintBoxWidth = template.tightHintBoxWidth;
            this.tightHintBoxHeight = template.tightHintBoxHeight;
        }

        private QFontMetrics keyMetrics(int keyIndex, QFontMetrics labelMetrics, int prefixLength,
                                        int selectedKeyEndIndex) {
            if (!labelFontStyle.perKeyFont())
                return labelMetrics;
            return keyStyle(keyIndex, prefixLength, selectedKeyEndIndex).metrics();
        }

        private QtFontStyle keyStyle(int keyIndex, int prefixLength, int selectedKeyEndIndex) {
            return resolveKeyQtFontStyle(prefixLength != -1 && keyIndex <= prefixLength - 1,
                    keyIndex <= selectedKeyEndIndex,
                    keyIndex == selectedKeyEndIndex + 1);
        }

        public void setFixedSize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public void move(int x, int y) {
            this.x = x;
            this.y = y;
        }

        /** Where the glyphs land, unlike the box, which for a grid hint is the whole cell. */
        Rectangle ink() {
            double inkLeft = Double.MAX_VALUE, inkTop = Double.MAX_VALUE;
            double inkRight = -Double.MAX_VALUE, inkBottom = -Double.MAX_VALUE;
            for (HintKeyText keyText : keyTexts) {
                QtFontStyle keyStyle = hintKeyTextQtFontStyle(keyText);
                QtHintFont.Ink ink = QtHintFont.ink(keyStyle.metrics(), keyText.text());
                int outlineThickness = keyStyle.outlineThickness();
                inkLeft = Math.min(inkLeft, keyText.x() + ink.left() - outlineThickness);
                inkTop = Math.min(inkTop, keyText.y() + ink.top() - outlineThickness);
                inkRight = Math.max(inkRight, keyText.x() + ink.right() + outlineThickness);
                inkBottom = Math.max(inkBottom, keyText.y() + ink.bottom() + outlineThickness);
            }
            int inkX = (int) Math.floor(inkLeft), inkY = (int) Math.floor(inkTop);
            return new Rectangle(x - left + inkX, y - top + inkY,
                    (int) Math.ceil(inkRight) - inkX, (int) Math.ceil(inkBottom) - inkY);
        }

        public void paint(QPainter painter) {
            paint(painter, false);
        }

        /**
         * Paints with all colors forced to fully opaque (alpha=255).
         * Used for shadow source rendering: the shadow effect generates
         * shadow strength from the source alpha, so the source must be
         * fully opaque to not get a weaker shadow when text is transparent.
         */
        void paintOpaque(QPainter painter) {
            paint(painter, true);
        }

        private static QColor opaqueColor(QColor c) {
            return QtColorUtil.opaque(c);
        }

        private QtFontStyle resolveKeyQtFontStyle(boolean isPrefix, boolean isSelected, boolean isFocused) {
            if (isPrefix && labelFontStyle.prefixDefaultStyle() != null) {
                if (isSelected)
                    return labelFontStyle.prefixSelectedStyle();
                if (isFocused)
                    return labelFontStyle.prefixFocusedStyle();
                return labelFontStyle.prefixDefaultStyle();
            }
            if (isSelected)
                return labelFontStyle.selectedStyle();
            if (isFocused)
                return labelFontStyle.focusedStyle();
            return labelFontStyle.defaultStyle();
        }

        private QtFontStyle hintKeyTextQtFontStyle(HintKeyText keyText) {
            return resolveKeyQtFontStyle(keyText.isPrefix(), keyText.isSelected(), keyText.isFocused());
        }

        private void paint(QPainter painter, boolean forceOpaque) {
            painter.save();
            painter.translate(x, y);
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            painter.setRenderHint(QPainter.RenderHint.TextAntialiasing, true);
            painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform, true);
            painter.setFont(labelFontStyle.defaultStyle().font());

            // Draw outlines per state (each state may have different outline settings).
            boolean hasPrefixStyle = labelFontStyle.prefixDefaultStyle() != null;
            paintOutlineForState(painter, forceOpaque, labelFontStyle.defaultStyle(),
                    k -> !k.isSelected() && !k.isFocused() && !(hasPrefixStyle && k.isPrefix()));
            paintOutlineForState(painter, forceOpaque, labelFontStyle.selectedStyle(),
                    k -> k.isSelected() && !(hasPrefixStyle && k.isPrefix()));
            paintOutlineForState(painter, forceOpaque, labelFontStyle.focusedStyle(),
                    k -> k.isFocused() && !(hasPrefixStyle && k.isPrefix()));
            if (hasPrefixStyle) {
                paintOutlineForState(painter, forceOpaque, labelFontStyle.prefixDefaultStyle(),
                        k -> k.isPrefix() && !k.isSelected() && !k.isFocused());
                paintOutlineForState(painter, forceOpaque, labelFontStyle.prefixSelectedStyle(),
                        k -> k.isPrefix() && k.isSelected());
                paintOutlineForState(painter, forceOpaque, labelFontStyle.prefixFocusedStyle(),
                        k -> k.isPrefix() && k.isFocused());
            }

            // Text punches through outline (and shadow, handled in pre-render) but blends over the box.
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
            for (HintKeyText keyText : keyTexts) {
                QtFontStyle qtFontStyle = hintKeyTextQtFontStyle(keyText);
                QColor color = qtFontStyle.color();
                if (!forceOpaque && color.alpha() == 0)
                    continue;
                if (labelFontStyle.perKeyFont())
                    painter.setFont(qtFontStyle.font());
                painter.setPen(forceOpaque ? opaqueColor(color) : color);
                painter.drawText(new QPointF(keyText.x() - left, keyText.y() - top), keyText.text());
            }
            painter.restore();
        }

        private void paintOutlineForState(QPainter painter, boolean forceOpaque,
                                          QtFontStyle qtFontStyle,
                                          Predicate<HintKeyText> filter) {
            if (qtFontStyle.outlineThickness() == 0 || qtFontStyle.outlineColor().alpha() == 0)
                return;
            boolean hasKeys = false;
            for (HintKeyText keyText : keyTexts) {
                if (filter.test(keyText)) {
                    hasKeys = true;
                    break;
                }
            }
            if (!hasKeys)
                return;
            QColor outlineColor = forceOpaque ?
                    opaqueColor(qtFontStyle.outlineColor()) : qtFontStyle.outlineColor();
            List<GlyphPlacement> glyphs = new ArrayList<>(keyTexts.size());
            for (HintKeyText keyText : keyTexts)
                if (filter.test(keyText))
                    glyphs.add(new GlyphPlacement(keyText.text(), keyText.x() - left,
                            keyText.y() - top));
            // Building and stroking a glyph outline costs ~85us, and a hint mesh draws the same
            // handful of labels over and over, so each is rasterized once and blitted after.
            OutlineImage outline = outlineImages.computeIfAbsent(
                    new OutlineKey(glyphs, outlineColor.rgba(),
                            qtFontStyle.outlineThickness()),
                    key -> {
                        // One path, stroked once: a path per glyph would stroke overlapping
                        // glyphs twice.
                        outlinePath.clear();
                        for (GlyphPlacement glyph : key.glyphs())
                            qtFontStyle.addTextPath(outlinePath, glyph.text(), glyph.x(),
                                    glyph.y());
                        return renderOutline(outlinePath, outlineColor,
                                qtFontStyle.outlineThickness());
                    });
            if (outline != null)
                painter.drawImage(outline.x(), outline.y(), outline.image());
        }

        /** Rasterizes the stroked path onto a transparent image, offset by whole pixels so the
         *  stroke lands on the same subpixel phase as painting it directly would. */
        private static OutlineImage renderOutline(QPainterPath path, QColor color,
                                                   int thickness) {
            QRectF strokeBounds = path.boundingRect();
            int left = (int) Math.floor(strokeBounds.x()) - thickness - 1;
            int top = (int) Math.floor(strokeBounds.y()) - thickness - 1;
            int width = (int) Math.ceil(strokeBounds.x() + strokeBounds.width()) - left +
                        thickness + 1;
            int height = (int) Math.ceil(strokeBounds.y() + strokeBounds.height()) - top +
                         thickness + 1;
            strokeBounds.dispose();
            if (width <= 0 || height <= 0)
                return null;
            QImage image = new QImage(width, height,
                    QImage.Format.Format_ARGB32_Premultiplied);
            QColor transparent = new QColor(0, 0, 0, 0);
            image.fill(transparent);
            transparent.dispose();
            QPainter painter = new QPainter(image);
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            painter.translate(-left, -top);
            painter.setPen(QtColorUtil.qPen(color, thickness, Qt.PenCapStyle.SquareCap,
                    Qt.PenJoinStyle.RoundJoin));
            painter.setBrush(Qt.BrushStyle.NoBrush);
            painter.drawPath(path);
            painter.end();
            painter.dispose();
            return new OutlineImage(image, left, top);
        }

        ShadowGroupKey shadowGroupKey(HintKeyText keyText) {
            QtFontStyle qtFontStyle = hintKeyTextQtFontStyle(keyText);
            QColor c = qtFontStyle.shadowColor();
            return new ShadowGroupKey(c.red(), c.green(), c.blue(), c.alpha(),
                                      qtFontStyle.shadowStackCount(), qtFontStyle.shadowBlurRadius(),
                                      qtFontStyle.shadowHorizontalOffset(), qtFontStyle.shadowVerticalOffset());
        }

        void paintOpaqueFiltered(QPainter painter,
                                 Predicate<HintKeyText> filter) {
            // Every label is offered to every shadow group, and most belong to none.
            if (keyTexts.stream().noneMatch(filter))
                return;
            painter.save();
            painter.translate(x, y);
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            painter.setRenderHint(QPainter.RenderHint.TextAntialiasing, true);
            painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform, true);
            painter.setFont(labelFontStyle.defaultStyle().font());
            boolean hasPrefixStyle = labelFontStyle.prefixDefaultStyle() != null;
            paintOutlineForState(painter, true, labelFontStyle.defaultStyle(),
                    k -> filter.test(k) && !k.isSelected() && !k.isFocused() && !(hasPrefixStyle && k.isPrefix()));
            paintOutlineForState(painter, true, labelFontStyle.selectedStyle(),
                    k -> filter.test(k) && k.isSelected() && !(hasPrefixStyle && k.isPrefix()));
            paintOutlineForState(painter, true, labelFontStyle.focusedStyle(),
                    k -> filter.test(k) && k.isFocused() && !(hasPrefixStyle && k.isPrefix()));
            if (hasPrefixStyle) {
                paintOutlineForState(painter, true, labelFontStyle.prefixDefaultStyle(),
                        k -> filter.test(k) && k.isPrefix() && !k.isSelected() && !k.isFocused());
                paintOutlineForState(painter, true, labelFontStyle.prefixSelectedStyle(),
                        k -> filter.test(k) && k.isPrefix() && k.isSelected());
                paintOutlineForState(painter, true, labelFontStyle.prefixFocusedStyle(),
                        k -> filter.test(k) && k.isPrefix() && k.isFocused());
            }
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Source);
            for (HintKeyText keyText : keyTexts) {
                if (!filter.test(keyText))
                    continue;
                QtFontStyle qtFontStyle = hintKeyTextQtFontStyle(keyText);
                QColor color = qtFontStyle.color();
                if (color.alpha() == 0)
                    continue;
                if (labelFontStyle.perKeyFont())
                    painter.setFont(qtFontStyle.font());
                painter.setPen(opaqueColor(color));
                painter.drawText(new QPointF(keyText.x() - left, keyText.y() - top), keyText.text());
            }
            painter.restore();
        }
    }

    private record ShadowGroupKey(int r, int g, int b, int a,
                                  int stackCount, double blurRadius,
                                  double horizontalOffset, double verticalOffset) {
    }

    private record HintKeyText(String text, double x, double y, int width, boolean isSelected,
                               boolean isFocused,
                               boolean isPrefix) {

    }

    /** Pre-renders the box shadow off-screen into the shadow layer, which crops with the content. */
    private void applyBoxShadow(HintPaintLayer boxShadowLayer,
                                       List<HintBox> hintBoxes,
                                       Shadow boxShadow,
                                       int containerWidth,
                                       int containerHeight,
                                       double qtScaleFactor) {
        QColor shadowColor = QtColorUtil.shadow(boxShadow);
        if (shadowColor.alpha() == 0) {
            shadowColor.dispose();
            return;
        }
        // The source is in pixels, as the blur radius and the offsets are: a box's shadow is as
        // many pixels as it is configured to be, whatever the screen.
        int sourceWidth = (int) Math.round(containerWidth * qtScaleFactor);
        int sourceHeight = (int) Math.round(containerHeight * qtScaleFactor);
        QImage sourceImage = new QImage(sourceWidth, sourceHeight,
                QImage.Format.Format_ARGB32_Premultiplied);
        QColor fillColor = new QColor(0, 0, 0, 0);
        sourceImage.fill(fillColor);
        fillColor.dispose();
        QPainter srcPainter = new QPainter(sourceImage);
        for (HintBox box : hintBoxes)
            box.paintOpaque(srcPainter);
        srcPainter.end();
        srcPainter.dispose();
        StackedShadowEffect.ShadowImage shadow = StackedShadowEffect.renderShadowOnly(sourceImage, shadowColor,
                boxShadow.blurRadius(), boxShadow.horizontalOffset(),
                boxShadow.verticalOffset(), sourceWidth, sourceHeight);
        shadowColor.dispose();
        QImage shadowImage = StackedShadowEffect.bakeStacking(
                shadow.image(), boxShadow.stackCount());
        QPixmap shadowPixmap = QPixmap.fromImage(shadowImage);
        shadowPixmap.setDevicePixelRatio(qtScaleFactor);
        boxShadowLayer.setShadowPixmap(shadowPixmap,
                (int) Math.round(shadow.x() / qtScaleFactor),
                (int) Math.round(shadow.y() / qtScaleFactor));
        shadowImage.dispose();
    }

    /** One layer per decoration depth, drawing that depth's labels above the boxes carrying them, so
     *  its shadow applies to the whole layer at once the way a hint label shadow does. */
    private void addDecorationLabelLayers(QWidget container, List<HintBox> boxes,
                                          List<DecorationStyle> decorationStyles,
                                          int containerWidth, int containerHeight) {
        for (int depth = 0; depth < decorationStyles.size(); depth++) {
            int labelDepth = depth + 1;
            HintPaintLayer layer = new HintPaintLayer(container, boxes, List.of(),
                    (box, painter) -> box.paintDecorationLabels(painter, labelDepth));
            layer.setGeometry(0, 0, containerWidth, containerHeight);
            QtFontStyle labelStyle = decorationStyles.get(depth).labelStyle();
            if (labelStyle.shadowColor().alpha() == 0 || labelStyle.invisible())
                continue;
            List<Rectangle> ink = new ArrayList<>();
            for (HintBox box : boxes)
                box.collectDecorationLabelBounds(labelDepth, 0, 0, ink);
            layer.fitToInk(ink, shadowPadding(labelStyle), containerWidth, containerHeight);
            StackedShadowEffect effect = new StackedShadowEffect();
            effect.setBlurRadius(labelStyle.shadowBlurRadius());
            effect.setOffset(labelStyle.shadowHorizontalOffset(),
                    labelStyle.shadowVerticalOffset());
            effect.setColor(labelStyle.shadowColor());
            effect.setStackCount(labelStyle.shadowStackCount());
            layer.setGraphicsEffect(effect);
        }
    }

    /** A shadow blurs its layer whole, and one layer over every label is mostly gaps. */
    private void addLabelLayers(QWidget container, List<HintLabel> labels,
                                QtHintFontStyle style,
                                boolean hasSelectedKeys,
                                int containerWidth,
                                int containerHeight,
                                double screenScale) {
        List<List<HintLabel>> labelLayers = layerPerLabel(labels, style) ?
                labels.stream().map(List::of).toList() : List.of(labels);
        for (List<HintLabel> layerLabels : labelLayers) {
            HintPaintLayer layer = new HintPaintLayer(container, List.of(), layerLabels);
            layer.setGeometry(0, 0, containerWidth, containerHeight);
            if (style != null)
                applyLabelShadow(layer, layerLabels, style, hasSelectedKeys,
                        containerWidth, containerHeight, screenScale);
        }
    }

    /** Whether a layer each blurs fewer pixels than one layer over them all. */
    private static boolean layerPerLabel(List<HintLabel> labels, QtHintFontStyle style) {
        if (style == null || labels.isEmpty())
            return false;
        if (!style.perKeyShadow() && style.defaultStyle().shadowColor().alpha() == 0)
            return false;
        int padding = shadowPadding(style.defaultStyle());
        List<Rectangle> ink = labelInk(labels);
        long paddedInkArea = 0;
        for (Rectangle rectangle : ink)
            paddedInkArea += (long) (rectangle.width() + 2 * padding) *
                             (rectangle.height() + 2 * padding);
        Rectangle union = Rectangle.union(ink);
        return paddedInkArea < (long) union.width() * union.height();
    }

    /**
     * Applies shadow to a label layer. When text is fully opaque, uses Qt's
     * QGraphicsDropShadowEffect directly on the widget (fast path). When text
     * has transparency, pre-renders the shadow off-screen and punches out the
     * text shape so shadow doesn't show through transparent text.
     */
    private void applyLabelShadow(HintPaintLayer layer,
                                         List<HintLabel> labels,
                                         QtHintFontStyle style,
                                         boolean hasSelectedKeys,
                                         int containerWidth,
                                         int containerHeight,
                                         double screenScale) {
        // Shadowing a layer that draws nothing still costs a blur of the whole layer, which for a
        // screen-sized hint mesh is tens of milliseconds.
        if (labels.isEmpty() || style.invisible(hasSelectedKeys))
            return;
        if (style.perKeyShadow()) {
            if (!preWarming)
                logger.debug("Hint label shadow: per-key shadow, pre-rendering per group");
            preRenderLabelShadow(layer, labels, style,
                    containerWidth, containerHeight, screenScale);
            return;
        }
        QtFontStyle defaultStyle = style.defaultStyle();
        if (defaultStyle.shadowColor().alpha() == 0)
            return;
        if (!style.hasTransparency() &&
            defaultStyle.shadowStackCount() == 1) {
            if (!preWarming)
                logger.debug("Hint label shadow: opaque text, applying effect directly");
            if (!layer.fitToInk(labelInk(labels), shadowPadding(defaultStyle),
                    containerWidth, containerHeight))
                return;
            StackedShadowEffect effect = new StackedShadowEffect();
            effect.setPreWarming(preWarming);
            effect.setBlurRadius(defaultStyle.shadowBlurRadius());
            effect.setOffset(defaultStyle.shadowHorizontalOffset(),
                    defaultStyle.shadowVerticalOffset());
            effect.setColor(defaultStyle.shadowColor());
            effect.setStackCount(defaultStyle.shadowStackCount());
            layer.setGraphicsEffect(effect);
        }
        else {
            if (defaultStyle.shadowStackCount() != 1)
                // Even though multiple stacks can be done with StackedShadowEffect, it
                // is faster to do it this way.
                logger.debug("Hint label shadow: shadow stack count is " +
                             defaultStyle.shadowStackCount() +
                             ", pre-rendering off-screen");
            else
                if (!preWarming)
                    logger.debug("Hint label shadow: transparent text, pre-rendering off-screen");
            preRenderLabelShadow(layer, labels, style,
                    containerWidth, containerHeight, screenScale);
        }
    }

    /** How far the shadow of a glyph reaches past it: the blur, plus how far it is offset. */
    private static int shadowPadding(double blurRadius, double horizontalOffset,
                                     double verticalOffset) {
        return (int) Math.ceil(blurRadius) +
               (int) Math.ceil(Math.max(Math.abs(horizontalOffset),
                       Math.abs(verticalOffset))) + 2;
    }

    private static int shadowPadding(QtFontStyle style) {
        return shadowPadding(style.shadowBlurRadius(), style.shadowHorizontalOffset(),
                style.shadowVerticalOffset());
    }

    private static List<Rectangle> labelInk(List<HintLabel> labels) {
        List<Rectangle> ink = new ArrayList<>();
        for (HintLabel label : labels)
            ink.add(label.ink());
        return ink;
    }

    private void preRenderLabelShadow(HintPaintLayer layer,
                                             List<HintLabel> labels,
                                             QtHintFontStyle style,
                                             int containerWidth,
                                             int containerHeight,
                                             double screenScale) {
        if (style.perKeyShadow()) {
            preRenderPerGroupShadow(layer, labels, containerWidth, containerHeight, screenScale);
            return;
        }
        QtFontStyle shadowStyle = style.defaultStyle();
        if (!layer.fitToInk(labelInk(labels), shadowPadding(shadowStyle),
                containerWidth, containerHeight))
            return;
        int left = layer.originX, top = layer.originY;
        int width = layer.width(), height = layer.height();
        // Render labels into a source image with forced opaque colors.
        QImage sourceImage = new QImage(width, height,
                QImage.Format.Format_ARGB32_Premultiplied);
        setQImageDpiForScreen(sourceImage, screenScale);
        QColor fillColor = new QColor(0, 0, 0, 0);
        sourceImage.fill(fillColor);
        fillColor.dispose();
        QPainter srcPainter = new QPainter(sourceImage);
        srcPainter.translate(-left, -top);
        for (HintLabel label : labels) {
            label.paintOpaque(srcPainter);
        }
        srcPainter.end();
        srcPainter.dispose();
        StackedShadowEffect.ShadowImage shadow = StackedShadowEffect.renderShadowOnly(sourceImage, shadowStyle.shadowColor(),
                shadowStyle.shadowBlurRadius(), shadowStyle.shadowHorizontalOffset(),
                shadowStyle.shadowVerticalOffset(), width, height);
        QImage shadowImage = StackedShadowEffect.bakeStacking(shadow.image(), shadowStyle.shadowStackCount());
        QPixmap shadowPixmap = QPixmap.fromImage(shadowImage);
        layer.setShadowPixmap(shadowPixmap,
                left + shadow.x(), top + shadow.y());
        shadowImage.dispose();
    }




    /**
     * Per-group shadow rendering: groups keys by their effective shadow
     * settings (state + prefix/non-prefix), renders each group separately,
     * bakes stacking, and composites into a single shadow pixmap.
     */
    private void preRenderPerGroupShadow(
            HintPaintLayer layer, List<HintLabel> labels,
            int containerWidth, int containerHeight,
            double screenScale) {
        // 1. Collect unique shadow groups.
        Set<ShadowGroupKey> groups = new LinkedHashSet<>();
        for (HintLabel label : labels) {
            for (HintKeyText keyText : label.keyTexts) {
                groups.add(label.shadowGroupKey(keyText));
            }
        }
        int padding = 0;
        for (ShadowGroupKey group : groups)
            padding = Math.max(padding, shadowPadding(group.blurRadius(),
                    group.horizontalOffset(), group.verticalOffset()));
        if (!layer.fitToInk(labelInk(labels), padding, containerWidth, containerHeight))
            return;
        int left = layer.originX, top = layer.originY;
        int width = layer.width(), height = layer.height();
        // 2. Render each group.
        QImage combinedShadow = null;
        int combinedX = 0, combinedY = 0;
        for (ShadowGroupKey group : groups) {
            if (group.a() == 0)
                continue;
            // Render source image with only keys matching this group.
            QImage sourceImage = new QImage(width, height,
                    QImage.Format.Format_ARGB32_Premultiplied);
            setQImageDpiForScreen(sourceImage, screenScale);
            QColor srcFillColor = new QColor(0, 0, 0, 0);
            sourceImage.fill(srcFillColor);
            srcFillColor.dispose();
            QPainter srcPainter = new QPainter(sourceImage);
            srcPainter.translate(-left, -top);
            for (HintLabel label : labels) {
                label.paintOpaqueFiltered(srcPainter,
                        keyText -> label.shadowGroupKey(keyText).equals(group));
            }
            srcPainter.end();
            srcPainter.dispose();
            QColor shadowColor = new QColor(group.r(), group.g(), group.b(), group.a());
            StackedShadowEffect.ShadowImage shadow = StackedShadowEffect.renderShadowOnly(sourceImage, shadowColor,
                    group.blurRadius(), group.horizontalOffset(), group.verticalOffset(),
                    width, height);
            shadowColor.dispose();
            QImage stackedShadow = StackedShadowEffect.bakeStacking(shadow.image(), group.stackCount());
            int boundsX = shadow.x();
            int boundsY = shadow.y();
            // Composite into final image.
            if (combinedShadow == null) {
                combinedShadow = stackedShadow;
                combinedX = boundsX;
                combinedY = boundsY;
            }
            else {
                int newX = Math.min(combinedX, boundsX);
                int newY = Math.min(combinedY, boundsY);
                int newRight = Math.max(combinedX + combinedShadow.width(),
                        boundsX + stackedShadow.width());
                int newBottom = Math.max(combinedY + combinedShadow.height(),
                        boundsY + stackedShadow.height());
                int newW = newRight - newX;
                int newH = newBottom - newY;
                QImage newCombined = new QImage(newW, newH,
                        QImage.Format.Format_ARGB32_Premultiplied);
                QColor combineFillColor = new QColor(0, 0, 0, 0);
                newCombined.fill(combineFillColor);
                combineFillColor.dispose();
                QPainter combinePainter = new QPainter(newCombined);
                combinePainter.drawImage(combinedX - newX, combinedY - newY,
                        combinedShadow);
                combinePainter.drawImage(boundsX - newX, boundsY - newY,
                        stackedShadow);
                combinePainter.end();
                combinePainter.dispose();
                combinedShadow.dispose();
                stackedShadow.dispose();
                combinedShadow = newCombined;
                combinedX = newX;
                combinedY = newY;
            }
        }
        if (combinedShadow != null) {
            QPixmap combinedPixmap = QPixmap.fromImage(combinedShadow);
            layer.setShadowPixmap(combinedPixmap,
                    left + combinedX, top + combinedY);
            combinedShadow.dispose();
        }
    }

    private class HintPaintLayer extends QWidget {

        private final List<HintBox> boxes;
        private final List<HintLabel> labels;
        private final BiConsumer<HintBox, QPainter> boxPainter;
        /** Where the layer sits when it covers only its ink; everything it paints shifts by it. */
        private int originX, originY;
        // Pre-rendered shadow-only pixmap (null if no shadow or opaque text).
        private QPixmap shadowPixmap;
        private int shadowPixmapX, shadowPixmapY;
        private QRect crop;
        // Where an enclosing border puts ink, kept out of what this layer paints. The outgoing
        // borders are kept out of their own grids' enclosing ink, which is still on screen too.
        private QRegion enclosingInk;
        private QRegion outgoingEnclosingInk;
        // The interrupted grids' borders, drawn beneath this layer's own so they can shrink with their
        // content (their own crop) while these boxes stay put.
        private List<OutgoingBorders> outgoing = List.of();
        private QRect outgoingCrop;

        HintPaintLayer(QWidget parent, List<HintBox> boxes, List<HintLabel> labels) {
            this(parent, boxes, labels, HintBox::paint);
        }

        HintPaintLayer(QWidget parent, List<HintBox> boxes, List<HintLabel> labels,
                       BiConsumer<HintBox, QPainter> boxPainter) {
            super(parent);
            this.boxes = boxes;
            this.labels = labels;
            this.boxPainter = boxPainter;
        }

        /**
         * Covers {@code ink} (in container coordinates, padded) instead of the whole container,
         * and shifts what it paints to match. A drop shadow effect blurs the layer's whole
         * surface, and a hint mesh spanning a screen puts very little ink on most of it.
         * Returns whether the layer was fitted: it is not when there is no ink to fit to.
         */
        boolean fitToInk(List<Rectangle> ink, int padding, int containerWidth,
                         int containerHeight) {
            if (ink.isEmpty())
                return false;
            Rectangle bounds = Rectangle.union(ink);
            int left = Math.max(0, bounds.x() - padding);
            int top = Math.max(0, bounds.y() - padding);
            int right = Math.min(containerWidth, bounds.x() + bounds.width() + padding);
            int bottom = Math.min(containerHeight, bounds.y() + bounds.height() + padding);
            if (right <= left || bottom <= top)
                return false;
            originX = left;
            originY = top;
            setGeometry(left, top, right - left, bottom - top);
            return true;
        }

        void setEnclosingInk(List<Rectangle> ink) {
            enclosingInk = region(ink);
        }

        private static QRegion region(List<Rectangle> rectangles) {
            if (rectangles.isEmpty())
                return null;
            QRegion region = new QRegion();
            for (Rectangle rectangle : rectangles) {
                QRect rect = new QRect(rectangle.x(), rectangle.y(),
                        rectangle.width(), rectangle.height());
                QRegion united = region.united(rect);
                region.dispose();
                region = united;
                rect.dispose();
            }
            return region;
        }

        void setOutgoing(List<OutgoingBorders> outgoing) {
            this.outgoing = outgoing;
            if (outgoingEnclosingInk != null) {
                outgoingEnclosingInk.dispose();
                outgoingEnclosingInk = null;
            }
            List<Rectangle> ink = new ArrayList<>();
            for (OutgoingBorders outgoingBorders : outgoing)
                ink.addAll(outgoingBorders.enclosingInk());
            outgoingEnclosingInk = region(ink);
            if (outgoing.isEmpty() && outgoingCrop != null) {
                outgoingCrop.dispose();
                outgoingCrop = null;
            }
            update();
        }

        /** Clips everything this layer draws, its own borders and the outgoing ones, to {@code r}. */
        void clipAll(QRect r) {
            setCrop(r);
            if (!outgoing.isEmpty())
                setOutgoingCrop(r);
        }

        void setOutgoingCrop(QRect r) {
            outgoingCrop = replaceCrop(outgoingCrop, r);
        }

        void setCrop(QRect r) {
            crop = replaceCrop(crop, r);
        }

        /** Disposes {@code current} and returns a copy of {@code r}, repainting the band between the
         *  two (the whole layer on the first crop, to clear any full paint before it). update(), not
         *  repaint(): repaint() flushes immediately, so a container's Clear would reach the screen
         *  before the border layer repaints on top, blanking the border lines for a frame. */
        private QRect replaceCrop(QRect current, QRect r) {
            QRect newCrop = new QRect(r);
            QRect dirty = current != null ? newCrop.united(current) : rect();
            if (current != null)
                current.dispose();
            update(dirty);
            dirty.dispose();
            return newCrop;
        }

        void clearCrop() {
            if (crop == null)
                return;
            crop.dispose();
            crop = null;
            repaint();
        }

        void setShadowPixmap(QPixmap shadowPixmap, int x, int y) {
            if (this.shadowPixmap != null)
                this.shadowPixmap.dispose();
            this.shadowPixmap = shadowPixmap;
            this.shadowPixmapX = x;
            this.shadowPixmapY = y;
        }

        void disposeBuffers() {
            if (shadowPixmap != null) {
                shadowPixmap.dispose();
                shadowPixmap = null;
            }
            if (enclosingInk != null) {
                enclosingInk.dispose();
                enclosingInk = null;
            }
            if (outgoingEnclosingInk != null) {
                outgoingEnclosingInk.dispose();
                outgoingEnclosingInk = null;
            }
        }

        @Override
        protected void paintEvent(QPaintEvent event) {
            long before = System.nanoTime();
            paintLayer(event);
            long durationMillis = (System.nanoTime() - before) / 1000000;
            if (durationMillis >= SLOW_PAINT_MS && !preWarming) {
                QRect dirty = event.rect();
                logger.debug("Painted a " + dirty.width() + "x" + dirty.height() +
                             " region at (" + dirty.x() + ", " + dirty.y() + ") of a " +
                             width() + "x" + height() + " hint layer of " + boxes.size() +
                             " boxes and " + labels.size() + " labels in " + durationMillis +
                             "ms");
            }
        }

        private void paintLayer(QPaintEvent event) {
            QPainter painter = new QPainter(this);
            if (originX != 0 || originY != 0)
                painter.translate(-originX, -originY);
            if (!outgoing.isEmpty() && outgoingCrop != null) {
                QRect outgoingDirty = outgoingCrop.intersected(event.rect());
                QRegion dirtyRegion = new QRegion(outgoingDirty);
                for (OutgoingBorders outgoingBorders : outgoing) {
                    Rectangle c = outgoingBorders.covered();
                    QRect coveredRect = new QRect(c.x(), c.y(), c.width(), c.height());
                    QRegion covered = new QRegion(coveredRect);
                    QRegion visible = dirtyRegion.subtracted(covered);
                    if (outgoingEnclosingInk != null) {
                        QRegion clipped = visible.subtracted(outgoingEnclosingInk);
                        visible.dispose();
                        visible = clipped;
                    }
                    painter.setClipRegion(visible);
                    for (HintBox box : outgoingBorders.boxes())
                        box.paintBorder(painter);
                    visible.dispose();
                    covered.dispose();
                    coveredRect.dispose();
                }
                painter.setClipping(false);
                dirtyRegion.dispose();
                outgoingDirty.dispose();
            }
            if (enclosingInk != null) {
                QRect clip = crop != null ? crop.intersected(event.rect()) : new QRect(event.rect());
                QRegion clipRegion = new QRegion(clip);
                QRegion visible = clipRegion.subtracted(enclosingInk);
                painter.setClipRegion(visible);
                visible.dispose();
                clipRegion.dispose();
                clip.dispose();
            }
            else if (crop != null) {
                QRect clip = crop.intersected(event.rect());
                painter.setClipRect(clip);
                clip.dispose();
            }
            for (HintBox box : boxes)
                boxPainter.accept(box, painter);
            if (shadowPixmap != null)
                painter.drawPixmap(shadowPixmapX, shadowPixmapY, shadowPixmap);
            for (HintLabel label : labels)
                label.paint(painter);
            painter.end();
            painter.dispose();
        }
    }

    /**
     * Returns the color that is drawn when a transparent color (the input color
     * with the opacity applied) is drawn on top of a white background.
     * This helps for improving the text antialiasing. Text antialiasing combines the
     * window's background color (which is ARGB transparent, but the antialiasing takes the
     * RGB non-transparent component).
     * We want the hint text to be antialiased with the effective color of the hint box
     * when the (transparent) hint box is above a white background.
     */
    private record HintSequenceText(Hint hint, List<HintKeyText> keyTexts) {

    }

    /** Rebuilds the window to show only the matched hint, morphing from a pixmap grabbed of
     *  the matched box. No-op unless the last mesh was an animatable hint grid. */
    public void animateHintMatch(Hint hint, Set<Screen> screens) {
        if (!showingHintMesh) // Invisible hint mesh.
            return;
        Map<Screen, List<Hint>> hintsByScreen = hintsByScreen(List.of(hint), screens);
        if (hintsByScreen.isEmpty()) // Matched hint is off-screen (grid drilled past an edge): nothing to animateTransition.
            return;
        Screen screen = hintsByScreen.keySet().iterator().next();
        HintMeshWindow hintMeshWindow = hintMeshWindows.get(screen);
        HintMesh lastHintMeshKey = hintMeshWindow.lastHintMeshKeyReference().get();
        HintMeshStyle style =
                lastHintMeshKey.styleByFilter().get(ScreenFilter.of(screen));
        if (!style.transitionAnimationEnabled())
            return;
        boolean isHintGrid = lastHintMeshKey.hints().getFirst().cellWidth() != -1 &&
                             lastHintMeshKey.hints().size() > 1;
        if (!isHintGrid)
            // No animation for position history hints.
            // hideHintMesh() will be called by the switch mode command.
            return;
        hintMeshEndAnimation = true;
        QRect hintBoxGeometry =
                hintBoxGeometriesByHintMeshKey.get(lastHintMeshKey).get(hint.keySequence());
        QWidget container = containers(hintMeshWindow.window).getLast();
        QRect containerGeom = container.geometry();
        int boxWindowX = containerGeom.x() + hintBoxGeometry.x();
        int boxWindowY = containerGeom.y() + hintBoxGeometry.y();
        containerGeom.dispose();
        // Grab the box's fill and label from the container, not the composited window: the border
        // layer is clipped in lockstep by clipMatchCropBorderLayer, so grabbing borders here too
        // would draw them twice (doubled opacity). Keep the extra right/bottom margin (a cell's
        // right/bottom border lines are its neighbours', just past its rect) so the crop's target
        // extent covers them and the clipped layer keeps all four borders.
        int boxBorderThickness = (int) Math.round(
                scaledLength(style.boxBorderThickness(), screen.scale()) /
                qtScaleFactor(screen.scale()));
        QRect containerBoxRect = new QRect(hintBoxGeometry.x(), hintBoxGeometry.y(),
                hintBoxGeometry.width() + boxBorderThickness,
                hintBoxGeometry.height() + boxBorderThickness);
        QPixmap pixmap = container.grab(containerBoxRect); // Expensive.
        containerBoxRect.dispose();
        HintMesh hintMesh =
                new HintMesh.HintMeshBuilder(lastHintMeshKey).hints(List.of(hint))
                                                             .build();
        PixmapAndPosition pixmapAndPosition =
                new PixmapAndPosition(pixmap, boxWindowX, boxWindowY, null, List.of(), hintMesh,
                        hintMeshWindow.window.x(), hintMeshWindow.window.y());
        setHintMeshWindow(hintMeshWindow, hintMesh, screen.scale(), style, false,
                pixmapAndPosition);
    }

}

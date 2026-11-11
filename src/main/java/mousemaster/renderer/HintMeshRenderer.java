package mousemaster.renderer;

import mousemaster.Color.LastSelectedHintColors;
import mousemaster.qt.*;

import io.qt.core.*;
import io.qt.gui.*;
import io.qt.widgets.*;
import mousemaster.*;
import mousemaster.GradientColor.GradientArea;
import mousemaster.GradientColor.GradientDirection;
import mousemaster.GradientColor.GradientStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Cross-platform Qt rendering of the hint mesh: builds and caches the per-screen hint
 * boxes, labels and shadows, and runs the transition/match animations.
 */
public final class HintMeshRenderer {

    private static final Logger logger = LoggerFactory.getLogger(HintMeshRenderer.class);

    private static final long SLOW_PAINT_MS = 5;
    /** What blurring an area apart costs besides its pixels, in pixels blurred in the same time. */
    private static final long AREA_COST_PIXELS = 100_000;
    /** QtJambi serializes creating and disposing its objects, so the threads that paint at once
     *  each reuse one. */
    private static final ThreadLocal<QPointF> paintedPoint = ThreadLocal.withInitial(QPointF::new);
    private static final ThreadLocal<QRectF> paintedRectangle =
            ThreadLocal.withInitial(QRectF::new);
    private static final ThreadLocal<QPointF> paintedLineEnd = ThreadLocal.withInitial(QPointF::new);

    private final Map<PixmapKey, PixmapsAndPosition> hintMeshPixmaps = new HashMap<>();
    private final Set<PixmapKey> preWarmedPixmapKeys = new HashSet<>();
    private final Map<ShadowKey, List<QPixmap>> boxShadowPixmaps = new HashMap<>();
    private final Map<HintMesh, Map<List<Key>, Rectangle>> hintBoxRectanglesByHintMeshKey =
            new HashMap<>();
    private boolean hintMeshEndAnimation;
    /**
     * Building the hint window is expensive and when it is done from the keyboard hook,
     * Windows will cancel the hook and the key press will go through to the other apps.
     * Windows won't wait for the keyboard hook to return if it's taking too long.
     */
    private final Map<TransparentWindow, Runnable> setUncachedHintMeshWindowRunnables =
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
    /** The container each window crops while its crop transitions, so a grid whose boxes cannot
     *  morph clips its border layer in lockstep with it. */
    private final Map<TransparentWindow, ContainerCrop> containerCropByWindow = new HashMap<>();
    private double frameDelta;
    private final TransitionMetrics transitionMetrics = new TransitionMetrics();
    private boolean showingHintMesh;
    private LastSelectedHintColors lastSelectedHintColors = new LastSelectedHintColors(
            GradientColor.parse("#000000"), GradientColor.parse("#000000"));
    private int hideCount;
    /** Set when a crop that was zooming into a selected hint's box is abandoned because the incoming
     *  grid does not continue that drill; the incoming grid then fades in instead of popping. */
    private boolean fadeIn;
    private final Map<TransparentWindow, TransitionedRectangle> transitionedCropByWindow =
            new HashMap<>();
    private HintMesh currentHintMesh;
    private FadeAnimator hintMeshFadeAnimator;
    /** Creates a styled native window. */
    private final Supplier<TransparentWindow> windowFactory;
    /** Null where the hint mesh windows draw their hotspots themselves. */
    private final Supplier<LayeredWindow> hotspotWindowFactory;
    private final Map<TransparentWindow, LayeredWindow> hotspotWindowByWindow =
            new HashMap<>();
    /** The container whose hotspots, and what is under them, each hotspot window draws. */
    private final Map<TransparentWindow, ClearBackgroundQLabel> hotspotContainerByWindow =
            new HashMap<>();

    /** A per-screen hint mesh window (no HWND — the overlay derives the native handle from
     *  {@link #window()}). Both sides share these instances, so the renderer's mutations to
     *  the lists/reference are visible to the overlay. */
    private record HintMeshWindow(TransparentWindow window,
                                 List<Hint> hints,
                                 Zoom zoom,
                                 AtomicReference<HintMesh> lastHintMeshKeyReference,
                                 AtomicBoolean lastWasMatchCrop) {
    }

    /** The current border layer and its running morph, so the next transition continues from it. */
    private static final class BorderMorph {
        private HintPaintLayer layer;
        private List<Rectangle> targets; // Each layer box's target rect, to settle to on interruption.
        private List<Rectangle> enclosingInk; // In window coordinates, like targets.
        private List<TransitionedRectangle> transitionedBoxes = List.of(); // One per layer box.
        private Map<List<Key>, TransitionedRectangle> transitionedBoxByKeySequence = Map.of();
    }

    /** The container a window crops, which is disposed at the end when it shrinks into the new
     *  one, and the callbacks that make the border layers follow its crop. */
    private record ContainerCrop(QWidget container, boolean shrinking,
                                 List<Consumer<QRect>> frameCallbacks,
                                 List<Runnable> endCallbacks) {
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

    public HintMeshRenderer(Supplier<TransparentWindow> windowFactory,
                            Supplier<LayeredWindow> hotspotWindowFactory,
                            Runnable hintMeshEndAnimationEndedCallback) {
        this.windowFactory = windowFactory;
        this.hotspotWindowFactory = hotspotWindowFactory;
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
        return !containerCropByWindow.isEmpty() ||
               borderMorphByWindow.values().stream().anyMatch(morph ->
                       morph.transitionedBoxes.stream()
                                              .anyMatch(TransitionedRectangle::transitioning)) ||
               hintMeshFadeAnimator != null && hintMeshFadeAnimator.transitioning();
    }

    public void update(double delta) {
        if (!transitionAnimating()) {
            frameDelta = 0;
            return;
        }
        frameDelta += delta;
        if (frameDelta < Mousemaster.FRAME_INTERVAL)
            return;
        for (TransparentWindow window : List.copyOf(containerCropByWindow.keySet())) {
            ContainerCrop containerCrop = containerCropByWindow.get(window);
            if (containerCrop != null)
                updateContainerCrop(window, containerCrop, frameDelta);
        }
        for (BorderMorph morph : borderMorphByWindow.values())
            updateBorderMorph(morph, frameDelta);
        if (hintMeshFadeAnimator != null)
            hintMeshFadeAnimator.update(frameDelta);
        frameDelta = 0;
    }

    private void updateContainerCrop(TransparentWindow window, ContainerCrop containerCrop,
                                     double delta) {
        TransitionedRectangle transitionedCrop = transitionedCropByWindow.get(window);
        transitionedCrop.update(delta);
        transitionMetrics.frameDrawn();
        cropOrMask(containerCrop.container(), transitionedCrop);
        QRect visible = visibleRect(containerCrop.container());
        for (Consumer<QRect> frameCallback : containerCrop.frameCallbacks())
            frameCallback.accept(visible);
        visible.dispose();
        if (transitionedCrop.transitioning())
            return;
        containerCropByWindow.remove(window);
        transitionedCropByWindow.remove(window);
        if (containerCrop.shrinking()) {
            containerCrop.container().setParent(null);
            disposeWidget(containerCrop.container());
        }
        containerCrop.endCallbacks().forEach(Runnable::run);
        transitionMetrics.log();
        hintContainerAnimationEnded();
    }

    private static void updateBorderMorph(BorderMorph morph, double delta) {
        if (morph.transitionedBoxes.stream().noneMatch(TransitionedRectangle::transitioning))
            return;
        // Where the borders were and are, so each frame repaints only that area.
        List<Rectangle> rectangles = new ArrayList<>(2 * morph.transitionedBoxes.size());
        for (int i = 0; i < morph.transitionedBoxes.size(); i++) {
            HintBox borderBox = morph.layer.boxes.get(i);
            TransitionedRectangle transitionedBox = morph.transitionedBoxes.get(i);
            transitionedBox.update(delta);
            rectangles.add(borderBox.rectangle());
            borderBox.setGeometry(transitionedBox.rectangle());
            rectangles.add(borderBox.rectangle());
        }
        Rectangle dirty = Rectangle.union(rectangles);
        morph.layer.update(dirty.x(), dirty.y(), dirty.width(), dirty.height());
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

    /** Runs the deferred builds. The platform overlay drives this once per frame. */
    public void runPendingWork() {
        if (setUncachedHintMeshWindowRunnables.isEmpty())
            return;
        pumpDuringHintBuild = true;
        List<Runnable> pending = List.copyOf(setUncachedHintMeshWindowRunnables.values());
        setUncachedHintMeshWindowRunnables.clear();
        pending.forEach(Runnable::run);
        pumpDuringHintBuild = false;
    }

    public void cancelPendingBuild() {
        setUncachedHintMeshWindowRunnables.clear();
    }

    public void flushCache() {
        for (PixmapsAndPosition pixmapsAndPosition : hintMeshPixmaps.values())
            pixmapsAndPosition.pixelRowChunks().forEach(QPixmap::dispose);
        hintMeshPixmaps.clear();
        preWarmedPixmapKeys.clear();
        clearBoxShadowPixmaps();
        hintBoxRectanglesByHintMeshKey.clear();
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
        List<PixmapKey> pixmapKeys = hintsByScreen(hintMesh.hints(), screens).entrySet()
                .stream()
                .map(entry -> pixmapKey(hintMeshKey(hintMesh, entry.getValue(),
                        hintMesh.styleByFilter().get(ScreenFilter.of(entry.getKey())),
                        entry.getKey().scale())))
                .toList();
        if (preWarmedPixmapKeys.containsAll(pixmapKeys))
            return;
        preWarming = true;
        try {
            createOrUpdateHintMeshWindows(hintMesh, zoom, true, screens);
            runPendingWork();
        }
        finally {
            preWarming = false;
            clearBoxShadowPixmaps();
        }
        preWarmedPixmapKeys.addAll(pixmapKeys);
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values())
            hintMeshWindow.hints().clear();
    }

    public void setHintMesh(HintMesh hintMesh, Zoom zoom, boolean animate, Set<Screen> screens) {
        Objects.requireNonNull(hintMesh);
        if (!hintMesh.visible()) {
            hideHintMesh();
            return;
        }
        if (hintMeshFadeAnimator != null && hintMeshFadeAnimator.isFadingOut())
            hintMeshFadeAnimator.fadeIn(style(hintMesh).fade());
        boolean onlyHotspotsChanged = showingHintMesh && currentHintMesh != null &&
                                    hintMesh.withTransitionsOf(currentHintMesh)
                                            .builder()
                                            .hotspotByKeySequence(
                                                    currentHintMesh.hotspotByKeySequence())
                                            .build()
                                            .equals(currentHintMesh);
        if (onlyHotspotsChanged &&
            (hintMesh.hotspotByKeySequence().equals(currentHintMesh.hotspotByKeySequence()) ||
             moveHotspots(hintMesh))) {
            currentHintMesh = hintMesh;
            return;
        }
        fadeIn = false;
        boolean wasShowing = showingHintMesh;
        if (hintMesh.hints().isEmpty()) {
            currentHintMesh = hintMesh;
            setHintMeshEndAnimation(false);
            createOrUpdateHintMeshWindows(currentHintMesh, zoom, animate, screens);
            showingHintMesh = true;
            return;
        }
        setHintMeshEndAnimation(false);
        currentHintMesh = hintMesh;
        createOrUpdateHintMeshWindows(currentHintMesh, zoom, animate && !onlyHotspotsChanged,
                screens);
        showingHintMesh = true;
        if (animate && (!wasShowing || fadeIn) && !hintMeshWindows.isEmpty()) {
            hintMeshFadeAnimator = new FadeAnimator(
                    opacity -> {
                        for (HintMeshWindow w : hintMeshWindows.values())
                            w.window().setWindowOpacity(opacity);
                        for (LayeredWindow hotspotWindow : hotspotWindowByWindow.values())
                            hotspotWindow.setOpacity(opacity);
                    },
                    this::doHideHintMesh);
            hintMeshFadeAnimator.fadeIn(style(currentHintMesh).fade());
        }
    }

    /** Moves the hotspots in the hotspot windows, unless a window has hotspots that its hotspot
     *  window does not draw, or a hint gains or loses its hotspot. */
    private boolean moveHotspots(HintMesh hintMesh) {
        if (!hintMesh.hotspotByKeySequence().keySet()
                     .equals(currentHintMesh.hotspotByKeySequence().keySet()))
            return false;
        for (Map.Entry<Screen, HintMeshWindow> entry : hintMeshWindows.entrySet()) {
            TransparentWindow window = entry.getValue().window();
            HintMeshStyle style = hintMesh.styleByFilter().get(ScreenFilter.of(entry.getKey()));
            if (!entry.getValue().hints().isEmpty() &&
                (hotspotBoxVisible(style) || hotspotBoxVisible(style.saved())) &&
                (!hotspotContainerByWindow.containsKey(window) ||
                 setUncachedHintMeshWindowRunnables.containsKey(window)))
                return false;
        }
        hotspotContainerByWindow.forEach((window, container) -> {
            Rectangle dirty = moveHotspots(container, hintMesh.hotspotByKeySequence());
            if (dirty != null)
                drawHotspots(window, container, dirty);
        });
        return true;
    }

    // Not in the container: the native image hangs while QtJambi resolves some widget members.
    /** Where the hotspots and their lines were and are, null when there are none. */
    private static Rectangle moveHotspots(ClearBackgroundQLabel container,
                                        Map<List<Key>, Point> hotspotByKeySequence) {
        List<Rectangle> inks = hotspotInks(container);
        placeHotspotBoxes(container, hotspotByKeySequence);
        inks.addAll(hotspotInks(container));
        return inks.isEmpty() ? null : Rectangle.union(inks);
    }

    private static List<Rectangle> hotspotInks(ClearBackgroundQLabel container) {
        List<Rectangle> inks = new ArrayList<>();
        for (HintBox hotspotBox : container.hotspotBoxes) {
            inks.add(hotspotBox.ink());
            if (hotspotBox.lineInk() != null)
                inks.add(hotspotBox.lineInk());
        }
        return inks;
    }

    private static void placeHotspotBoxes(ClearBackgroundQLabel container,
                                        Map<List<Key>, Point> hotspotByKeySequence) {
        int originX = container.window().x() + container.x();
        int originY = container.window().y() + container.y();
        for (HintBox hotspotBox : container.hotspotBoxes) {
            Point hotspot = hotspotByKeySequence.get(hotspotBox.hint.keySequence());
            double qtScaleFactor = hotspotBox.qtScaleFactor;
            int left = (int) Math.round(
                    (hotspot.x() - container.hotspotBoxWidth / 2d) / qtScaleFactor);
            int top = (int) Math.round(
                    (hotspot.y() - container.hotspotBoxHeight / 2d) / qtScaleFactor);
            int right = (int) Math.round(
                    (hotspot.x() + container.hotspotBoxWidth / 2d) / qtScaleFactor);
            int bottom = (int) Math.round(
                    (hotspot.y() + container.hotspotBoxHeight / 2d) / qtScaleFactor);
            hotspotBox.setGeometry(left - originX, top - originY, right - left, bottom - top);
        }
    }

    /** The hotspot window shows nothing when the container draws its hotspots itself. */
    private void showHotspots(TransparentWindow window, ClearBackgroundQLabel container) {
        LayeredWindow hotspotWindow = hotspotWindowByWindow.get(window);
        if (hotspotWindow == null)
            return;
        hotspotWindow.clear();
        if (container == null || !container.inHotspotWindow) {
            hotspotContainerByWindow.remove(window);
            return;
        }
        hotspotContainerByWindow.put(window, container);
        List<Rectangle> areas = hotspotInks(container);
        areas.add(container.belowHotspotArea);
        drawHotspots(window, container, Rectangle.union(areas));
    }

    /** dirty is in the coordinates of the container. */
    private void drawHotspots(TransparentWindow window, ClearBackgroundQLabel container,
                                Rectangle dirty) {
        int x = container.x(), y = container.y();
        Rectangle rectangle = new Rectangle(window.xInPixels(), window.yInPixels(),
                window.widthInPixels(), window.heightInPixels());
        hotspotWindowByWindow.get(window).draw(rectangle,
                new Rectangle(rectangle.x() + x + dirty.x(), rectangle.y() + y + dirty.y(),
                        dirty.width(), dirty.height()), (painter, area) -> {
                    painter.translate(x, y);
                    Rectangle containerArea = new Rectangle(area.x() - x, area.y() - y,
                            area.width(), area.height());
                    paintImages(painter, containerArea, container.belowHotspotArea.x(),
                            container.belowHotspotArea.y(), container.belowHotspotImages);
                    container.paintHotspotBoxes(painter, containerArea);
                    paintImages(painter, containerArea, 0, 0, container.aboveHotspotImages);
                });
    }

    /** The images are rows down from (x, y). */
    private static void paintImages(QPainter painter, Rectangle area, int x, int y,
                                    List<QImage> images) {
        for (QImage image : images) {
            int height = (int) Math.round(image.height() / image.devicePixelRatio());
            if (y < area.y() + area.height() && area.y() < y + height)
                painter.drawImage(x, y, image);
            y += height;
        }
    }

    public LastSelectedHintColors lastSelectedHintColors() {
        return lastSelectedHintColors;
    }

    /** The rectangle of the box drawn for the hint, in screen pixels, null when none is drawn. */
    public Rectangle hintBoxRectangle(Hint hint) {
        if (!showingHintMesh)
            return null;
        for (Map.Entry<Screen, HintMeshWindow> entry : hintMeshWindows.entrySet()) {
            HintMeshWindow hintMeshWindow = entry.getValue();
            Map<List<Key>, Rectangle> hintBoxRectangles = hintBoxRectanglesByHintMeshKey.get(
                    hintMeshWindow.lastHintMeshKeyReference().get());
            Rectangle rectangle =
                    hintBoxRectangles == null ? null : hintBoxRectangles.get(hint.keySequence());
            List<QWidget> containers = containers(hintMeshWindow.window);
            if (rectangle == null || containers.isEmpty())
                continue;
            double qtScaleFactor = qtScaleFactor(entry.getKey().scale());
            QWidget container = containers.getLast();
            int left = hintMeshWindow.window.x() + container.x() + rectangle.x();
            int top = hintMeshWindow.window.y() + container.y() + rectangle.y();
            return new Rectangle((int) Math.round(left * qtScaleFactor),
                    (int) Math.round(top * qtScaleFactor),
                    (int) Math.round(rectangle.width() * qtScaleFactor),
                    (int) Math.round(rectangle.height() * qtScaleFactor));
        }
        return null;
    }

    public void hideHintMesh() {
        if (!showingHintMesh)
            return;
        if (isHintMeshEndAnimation())
            return;
        if (hintMeshFadeAnimator != null && !hintMeshWindows.isEmpty() &&
            style(currentHintMesh).fade().enabled()) {
            hintMeshFadeAnimator.fadeOut(style(currentHintMesh).fade());
            return;
        }
        doHideHintMesh();
    }

    private HintMeshStyle style(HintMesh hintMesh) {
        return hintMesh.styleByFilter()
                       .get(ScreenFilter.of(hintMeshWindows.keySet().iterator().next()));
    }

    public void stopHintMatchAnimation() {
        if (!hintMeshEndAnimation)
            return;
        setHintMeshEndAnimation(false);
        doHideHintMesh();
    }

    private void doHideHintMesh() {
        showingHintMesh = false;
        hideCount++;
        if (hintMeshFadeAnimator != null)
            hintMeshFadeAnimator.cancel();
        cancelPendingBuild();
        clearBoxShadowPixmaps();
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values()) {
            boolean showedNothing = hintMeshWindow.window().showsNothing();
            // Drop the crop before hiding children, or update() would call container.setMask()
            // on a widget whose C++ object has been destroyed.
            containerCropByWindow.remove(hintMeshWindow.window());
            transitionedCropByWindow.remove(hintMeshWindow.window());
            BorderMorph lineMorph = borderMorphByWindow.remove(hintMeshWindow.window());
            if (lineMorph != null)
                stopBorderMorph(lineMorph);
            hintMeshWindow.window().setBackground(null, null, 0, null, 0);
            hintMeshWindow.window().hideChildren();
            showHotspots(hintMeshWindow.window(), null);
            // An opaque window is cleared with what changes with it, such as the indicator. A
            // faded one is cleared before it is made opaque again for reuse.
            if (!showedNothing && hintMeshWindow.window().windowOpacity() == 1)
                hintMeshWindow.window().update();
            else if (!showedNothing)
                hintMeshWindow.window().repaint();
            hintMeshWindow.window().setWindowOpacity(1.0);
        }
        for (LayeredWindow hotspotWindow : hotspotWindowByWindow.values())
            hotspotWindow.setOpacity(1);
    }

    private void createOrUpdateHintMeshWindows(HintMesh hintMesh, Zoom zoom, boolean animate,
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
            if (!hintsByScreen.containsKey(screen)) {
                window.hints().clear();
                showHotspots(window.window(), null);
            }
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
                        screen.scale(), style, animate);
            }
            else {
                HintMeshWindow hintMeshWindow = new HintMeshWindow(existingWindow.window(),
                        hintsInScreen, zoom, existingWindow.lastHintMeshKeyReference(),
                        existingWindow.lastWasMatchCrop());
                boolean zoomChanged = existingWindow.zoom() == null || !existingWindow.zoom().equals(zoom);
                hintMeshWindows.put(screen, hintMeshWindow);
//                TransparentWindow window = existingWindow.window;
//                logger.debug("Showing hints " + hintsInScreen.size() + " for " + screen + ", window = " + existingWindow.window.x() + " " + existingWindow.window.y() + " " + existingWindow.window.width() + " " + existingWindow.window.height());
                setHintMeshWindow(hintMeshWindow, hintMesh,
                        screen.scale(), style, animate && !zoomChanged);
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
        if (hotspotWindowFactory != null)
            hotspotWindowByWindow.put(window, hotspotWindowFactory.get());
        return new HintMeshWindow(window, hints, zoom, new AtomicReference<>(),
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

        private List<QPixmap> pixelRowChunks = List.of();
        /** Crop region, clipped in paintEvent instead of via setMask (which recomposites the whole
         *  window). Null paints the whole label. */
        private QRect crop;

        /** Layer 0, under the hotspots. */
        private List<HintPaintLayer> belowHotspotLayers = List.of();
        private List<HintBox> hotspotBoxes = List.of();
        /** Set when the hotspots, and what is under them, are drawn in the hotspot window. */
        private boolean inHotspotWindow;
        private int hotspotBoxWidth, hotspotBoxHeight;
        /** The container and its background, in the coordinates of the container. */
        private Rectangle belowHotspotArea;
        private List<QImage> belowHotspotImages = List.of();
        private List<QImage> aboveHotspotImages = List.of();

        void setPixelRowChunks(List<QPixmap> pixelRowChunks) {
            this.pixelRowChunks = pixelRowChunks;
        }

        /** All lines first: a painter that turns antialiasing on and off for each line paints
         *  them twice as slowly. */
        void paintHotspotBoxes(QPainter painter, Rectangle area) {
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            for (HintBox hotspotBox : hotspotBoxes)
                hotspotBox.paintLine(painter, area);
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, false);
            for (HintBox hotspotBox : hotspotBoxes)
                if (!hotspotBox.ink().intersection(area).isEmpty())
                    hotspotBox.paint(painter);
        }

        void disposeBuffers() {
            pixelRowChunks.forEach(QPixmap::dispose);
            belowHotspotImages.forEach(QImage::dispose);
            aboveHotspotImages.forEach(QImage::dispose);
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

        /** Drops the crop so the whole label shows again. */
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
            QPainter painter = new QPainter(this);
            // Clear what's behind (when we're drawing the old container behind).
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Clear);
            painter.fillRect(event.rect(), QtColorUtil.qBrush(0));
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
            QRect drawRegion = crop != null ? crop.intersected(event.rect()) : rect();
            if (!drawRegion.isEmpty()) {
                painter.setClipRect(drawRegion);
                int y = 0;
                for (QPixmap pixelRowChunk : pixelRowChunks) {
                    painter.drawPixmap(0, y, pixelRowChunk);
                    y += (int) Math.round(
                            pixelRowChunk.height() / pixelRowChunk.devicePixelRatio());
                }
            }
            drawRegion.dispose();
            painter.end();
            painter.dispose();
        }
    }

    public void setHintMeshWindow(HintMeshWindow hintMeshWindow,
                                  HintMesh hintMesh, double screenScale,
                                  HintMeshStyle style,
                                  boolean animate) {
        setHintMeshWindow(hintMeshWindow, hintMesh, screenScale, style, animate, null);
    }

    private void setHintMeshWindow(HintMeshWindow hintMeshWindow,
                                          HintMesh hintMesh, double screenScale,
                                          HintMeshStyle style,
                                          boolean animate,
                                          PixmapsAndPosition matchBoxPixmap) {
        TransparentWindow window = hintMeshWindow.window;
        setUncachedHintMeshWindowRunnables.remove(window);
        // A match crop zooms into a just-typed hint's box (animateHintMatch seeds it with that box's
        // screenshot); it plus the content render that follows form a drill. Was this render one, or the last?
        boolean matchCropBefore = hintMeshWindow.lastWasMatchCrop.get();
        boolean matchCropNow = matchBoxPixmap != null;
        hintMeshWindow.lastWasMatchCrop.set(matchCropNow);
        boolean cropAnimating = containerCropByWindow.remove(window) != null;
        List<QWidget> interruptedContainers = containers(window);
        // A drill continues from the current extent toward a same-sized or contained target, or grows
        // from the target out to a mesh around it while its crop still runs (a pick that keeps the grid). A prefix
        // select/deselect continues whenever the new mesh nests with the target either way (growing back
        // out or shrinking back in). A same-sized change (recolor, fresh grid) resolves instantly, unless
        // a crop is still animating — then it continues too, so a mid-crop recolor is not cut short.
        boolean partOfDrill = matchCropBefore || matchCropNow;
        double qtScaleFactor = qtScaleFactor(screenScale);
        boolean coversTarget = coversInProgressTarget(hintMeshWindow, hintMesh, interruptedContainers,
                qtScaleFactor);
        boolean targetContainsNew = targetContainsNewMesh(hintMeshWindow, hintMesh, interruptedContainers,
                qtScaleFactor);
        boolean newContainsTarget = newMeshContainsTarget(hintMeshWindow, hintMesh, interruptedContainers,
                qtScaleFactor);
        boolean continueFromCurrentExtent = partOfDrill
                ? coversTarget || targetContainsNew || cropAnimating && newContainsTarget
                : showingHintMesh && (newContainsTarget || targetContainsNew) && (!coversTarget || cropAnimating);
        if (!continueFromCurrentExtent)
            transitionedCropByWindow.remove(window);
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
            if (matchCropBefore && !coversTarget && !targetContainsNew)
                for (QWidget container : interruptedContainers.subList(0, interruptedContainers.size() - 1)) {
                    container.setParent(null);
                    disposeWidget(container);
                }
            else if (interruptedContainers.size() >= 2) {
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
                if (lastContainer instanceof ClearBackgroundQLabel label)
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
            if (morph != null)
                morph.transitionedBoxByKeySequence.values().forEach(TransitionedRectangle::settle);
        }
        List<QWidget> oldContainers = containers(window);
        QWidget oldContainer = oldContainers.isEmpty() ? null : oldContainers.getFirst();
        boolean oldContainerHidden = oldContainer == null || oldContainer.isHidden();
        window.clearWindow();
        // The window and the container clear paint the same background.
        Rectangle backgroundArea = hintMesh.backgroundArea();
        Rectangle backgroundRectangle = backgroundArea == null || style.backgroundOpacity() == 0 &&
                                   style.backgroundBorderOpacity() == 0 ? null :
                backgroundRectangle(window, backgroundArea, qtScaleFactor);
        QBrush backgroundColor = backgroundRectangle == null ? null :
                backgroundBrush(style.backgroundColor(), style.backgroundOpacity(),
                        backgroundRectangle, hintMeshWindow, hintMesh, qtScaleFactor);
        QBrush backgroundBorderColor = backgroundRectangle == null ? null :
                backgroundBrush(style.backgroundBorderColor(), style.backgroundBorderOpacity(),
                        backgroundRectangle, hintMeshWindow, hintMesh, qtScaleFactor);
        int backgroundRadius =
                scaledLength(style.backgroundBorderRadius(), screenScale / qtScaleFactor);
        int backgroundBorderThickness =
                scaledLength(style.backgroundBorderThickness(), screenScale / qtScaleFactor);
        if (hintMeshWindow.hints().isEmpty()) {
            showBackground(window, backgroundColor, backgroundRectangle, backgroundRadius,
                    backgroundBorderColor, backgroundBorderThickness);
            showHotspots(window, null);
            QWidget container = new QWidget(window);
            container.setGeometry(0, 0, 0, 0);
            container.show();
            window.show();
            return;
        }
        HintMesh hintMeshKey = matchBoxPixmap != null ? null :
                hintMeshKey(hintMesh, hintMeshWindow.hints(), style, screenScale);
        // The new key will be used by animateHintMatch.
        HintMesh lastHintMeshKey =
                hintMeshWindow.lastHintMeshKeyReference.getAndSet(hintMeshKey);
        forgetHintBoxRectangles(lastHintMeshKey);
        PixmapsAndPosition pixmapsAndPosition =
                matchBoxPixmap != null ? matchBoxPixmap :
                        hintMeshPixmaps.get(pixmapKey(hintMeshKey));
        boolean isHintGrid = hintMeshWindow.hints().getFirst().cellWidth() != -1;
        ClearBackgroundQLabel newContainer;
        if (pixmapsAndPosition != null) {
            logger.trace("Using cached hint mesh pixmaps " + pixmapsAndPosition);
            ClearBackgroundQLabel pixmapLabel = new ClearBackgroundQLabel(window);
            pixmapLabel.setPixelRowChunks(
                    pixmapsAndPosition.pixelRowChunks().stream().map(QPixmap::new).toList());
            Hint originalFirstHint = roundedHint(
                    pixmapsAndPosition.originalHintMesh.hints().getFirst(), style, screenScale);
            int originalWindowX = pixmapsAndPosition.windowX;
            int originalWindowY = pixmapsAndPosition.windowY;
            Hint newFirstHint = roundedHint(hintMesh.hints().getFirst(), style, screenScale);
            // Translate the original pixmaps which may be at a different position than
            // the new hint mesh.
            // Hints are in pixels, the label and the window in points. The pixmaps are in the
            // pixels of the screen they were painted on, which is not always this one: a mesh is
            // cached by its hints alone, and a screen of another scale paints it at another ratio.
            double pixmapScale =
                    pixmapsAndPosition.pixelRowChunks().getFirst().devicePixelRatio();
            pixmapLabel.setGeometry(pixmapsAndPosition.x() + (int) Math.round(
                            (newFirstHint.centerX() - originalFirstHint.centerX()) / qtScaleFactor
                            - window.x() + originalWindowX),
                    pixmapsAndPosition.y() + (int) Math.round(
                            (newFirstHint.centerY() - originalFirstHint.centerY()) / qtScaleFactor
                            - window.y() + originalWindowY),
                    (int) Math.round(pixmapsAndPosition.width() / pixmapScale),
                    (int) Math.round(pixmapsAndPosition.height() / pixmapScale));
            newContainer = pixmapLabel;
            boolean animateTransition = style.transition().enabled() && isHintGrid && !oldContainerHidden && animate;
            showBackground(window, backgroundColor, backgroundRectangle, backgroundRadius,
                    backgroundBorderColor, backgroundBorderThickness);
            transitionHintContainers(animateTransition, oldContainer, newContainer,
                    window, hintMeshWindow, style.transition());
            showHotspots(window, null);
            if (pixmapsAndPosition.boxes() != null)
                morphBorders(window, hintMeshWindow, pixmapLabel, pixmapsAndPosition.boxes(),
                        pixmapsAndPosition.enclosingInk(), animateTransition,
                        style.transition());
            else
                // A match crop carries no boxes, so morphBorders is skipped; clip the old border
                // layer in lockstep so the starting grid's borders shrink into the selected box too.
                clipMatchCropBorderLayer(window);
        }
        else {
            // Uses ClearBackgroundQLabel because when in the mergedContainer,
            // the top-level container must override the container below.
            ClearBackgroundQLabel container = new ClearBackgroundQLabel(window);
            newContainer = container;
            int hideCountBeforeBuild = hideCount;
            Runnable setUncachedHintMeshWindowRunnable =
                    () -> {
                        long before = System.nanoTime();
                        List<Rectangle> enclosingInk = new ArrayList<>();
                        List<HintBox> boxes =
                                setUncachedHintMeshWindow(hintMeshWindow, hintMeshKey, hintMesh,
                                        screenScale, style, qtScaleFactor, container, enclosingInk);
                        // A deferred build pumps messages, so the mesh may have been hidden
                        // while it ran.
                        if (hideCount != hideCountBeforeBuild) {
                            container.setParent(null);
                            disposeWidget(container);
                            return;
                        }
                        paintPixmaps(container, isHintGrid, backgroundColor, backgroundRectangle,
                                backgroundRadius, backgroundBorderColor, backgroundBorderThickness,
                                screenScale);
                        if (!preWarming)
                            logger.debug("Built hint mesh window in " +
                                    (long) ((System.nanoTime() - before) / 1e6) + "ms");
                        // Only a grid with labels and unmoved hotspots is drawn the same again.
                        if (isHintGrid && !hintMesh.hints().getFirst().keySequence().isEmpty() &&
                            hintMeshKey.hotspotByKeySequence().isEmpty())
                            cachePixmaps(window, container, hintMeshKey, hintMesh, boxes,
                                    enclosingInk);
                        if (preWarming) {
                            container.setParent(null);
                            disposeWidget(container);
                            return;
                        }
                        boolean animateTransition = style.transition().enabled() && isHintGrid && !oldContainerHidden && animate;
                        showBackground(window, backgroundColor,
                                container.inHotspotWindow ? null : backgroundRectangle,
                                backgroundRadius, backgroundBorderColor, backgroundBorderThickness);
                        transitionHintContainers(animateTransition,
                                oldContainer, newContainer,
                                window, hintMeshWindow, style.transition());
                        showHotspots(window, container);
                        // Non-grid hints draw their own borders and only need the window shown.
                        morphBorders(window, hintMeshWindow, container, boxes, enclosingInk,
                                animateTransition, style.transition());
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

    private static Rectangle backgroundRectangle(TransparentWindow window,
                                                 Rectangle backgroundArea, double qtScaleFactor) {
        Rectangle background = inWindowPoints(backgroundArea, window, qtScaleFactor);
        int left = Math.max(0, background.x());
        int top = Math.max(0, background.y());
        int right = Math.min(window.width(), background.x() + background.width());
        int bottom = Math.min(window.height(), background.y() + background.height());
        return right > left && bottom > top ?
                new Rectangle(left, top, right - left, bottom - top) : null;
    }

    private static Rectangle inWindowPoints(Rectangle pixelRectangle, TransparentWindow window,
                                            double qtScaleFactor) {
        return new Rectangle(
                (int) Math.round((pixelRectangle.x() - window.xInPixels()) / qtScaleFactor),
                (int) Math.round((pixelRectangle.y() - window.yInPixels()) / qtScaleFactor),
                (int) Math.round(pixelRectangle.width() / qtScaleFactor),
                (int) Math.round(pixelRectangle.height() / qtScaleFactor));
    }

    /** A background is drawn as a box is. Null when it is not drawn. */
    private QBrush backgroundBrush(Color color, double opacity, Rectangle backgroundRectangle,
                                   HintMeshWindow hintMeshWindow, HintMesh hintMesh,
                                   double qtScaleFactor) {
        if (opacity == 0)
            return null;
        GradientColor gradientColor = gradientColor(color);
        Rectangle gradientArea = gradientArea(gradientColor.area(), hintMeshWindow, hintMesh);
        return brush(gradientColor, opacity, gradientArea == null ? null :
                        inWindowPoints(gradientArea, hintMeshWindow.window(), qtScaleFactor),
                null, backgroundRectangle.center(), 0, backgroundRectangle, new Point(0, 0));
    }

    private static void showBackground(TransparentWindow window, QBrush backgroundColor,
                                       Rectangle backgroundRectangle, int backgroundRadius,
                                       QBrush backgroundBorderColor,
                                       int backgroundBorderThickness) {
        if (backgroundRectangle == null) {
            window.setBackground(null, null, 0, null, 0);
            return;
        }
        QRect rect = new QRect(backgroundRectangle.x(), backgroundRectangle.y(),
                backgroundRectangle.width(), backgroundRectangle.height());
        window.setBackground(backgroundColor, rect, backgroundRadius, backgroundBorderColor,
                backgroundBorderThickness);
    }

    private void transitionHintContainers(boolean animateTransition, QWidget oldContainer,
                                                 ClearBackgroundQLabel newContainer,
                                                 TransparentWindow window,
                                                 HintMeshWindow hintMeshWindow,
                                                 Transition transition) {
        // TODO Should use .geometry() instead of .rect() which is relative to the widget
        //  itself, where geometry() is relative to the parent.
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
                    // container is already on top.
                    oldContainer.setParent(null);
                    disposeWidget(oldContainer);
                    transitionedCropByWindow.remove(window);
                    hintContainerAnimationEnded();
                }
                else {
                    // Start from the old container's visible (cropped) extent, not its full geometry,
                    // so a shrink that continues an interrupted crop resumes from where it is.
                    QRect oldVisible = visibleRect(oldContainer);
                    TransitionedRectangle transitionedCrop =
                            transitionedCrop(window, oldVisible, newContainer, transition);
                    oldVisible.dispose();
                    cropOrMask(oldContainer, transitionedCrop);
                    startContainerCrop(window, oldContainer, true, transition);
                }
            }
            else if (animateTransition && (newContainsOld || continueCropIntoNew)) {
                // Initially show new container with the position and size of old.
                // Then grow new container until it reaches its final position and size.
                newContainer.show();
                // Start from the old container's visible (cropped) extent, not its full geometry, so a
                // mid-animation reversal grows from where it is instead of snapping.
                QRect oldVisible = visibleRect(oldContainer);
                TransitionedRectangle transitionedCrop =
                        transitionedCrop(window, oldVisible, newContainer, transition);
                oldVisible.dispose();
                cropOrMask(newContainer, transitionedCrop);
                startContainerCrop(window, newContainer, false, transition);
                oldContainer.setParent(null);
                disposeWidget(oldContainer);
            }
            else {
                oldContainer.setParent(null);
                disposeWidget(oldContainer);
                showInHintMeshWindow(newContainer);
                transitionedCropByWindow.remove(window);
                hintContainerAnimationEnded();
            }
        }
        else {
            showInHintMeshWindow(newContainer);
        }
        window.show();
    }

    /** A container that the hotspot window draws whole stays hidden: showing it even for a moment
     *  paints the hint mesh window. */
    private static void showInHintMeshWindow(ClearBackgroundQLabel container) {
        container.setVisible(container.aboveHotspotImages.isEmpty());
    }

    /** The visible extent of the window's containers, in window coordinates. */
    private TransitionedRectangle transitionedCrop(TransparentWindow window, QRect visible,
                                               QWidget newContainer, Transition transition) {
        TransitionedRectangle transitionedCrop = transitionedCropByWindow.get(window);
        if (transitionedCrop == null || !transitionedCrop.transitioning()) {
            transitionedCrop = new TransitionedRectangle(new Rectangle(visible.x(), visible.y(),
                    visible.width(), visible.height()));
            transitionedCropByWindow.put(window, transitionedCrop);
        }
        transitionedCrop.transitionTo(transition, new Rectangle(newContainer.x(),
                newContainer.y(), newContainer.width(), newContainer.height()));
        return transitionedCrop;
    }

    private void startContainerCrop(TransparentWindow window, QWidget container, boolean shrinking,
                                    Transition transition) {
        transitionMetrics.started((int) transition.duration().toMillis(), clipping(container));
        containerCropByWindow.put(window,
                new ContainerCrop(container, shrinking, new ArrayList<>(), new ArrayList<>()));
    }

    /** Draws the new grid's borders on a live layer above the cropped content. If the boxes map to the
     *  old grid's and move, it morphs them there; otherwise it clips the layer in lockstep with the crop. */
    private void morphBorders(TransparentWindow window, HintMeshWindow hintMeshWindow,
                            QWidget contentContainer,
                            List<HintBox> newBoxes, List<Rectangle> enclosingInk,
                            boolean animateTransition, Transition transition) {
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
        List<HintBox> oldBoxes =
                animateTransition && morph.layer != null ? morph.layer.boxes : List.of();
        Map<List<Key>, TransitionedRectangle> oldTransitionedBoxByKeySequence =
                animateTransition ? morph.transitionedBoxByKeySequence : Map.of();
        int boxCount = newBoxes.size();
        List<Rectangle> targets = new ArrayList<>(boxCount);
        for (HintBox box : newBoxes)
            targets.add(new Rectangle(box.x() + originX, box.y() + originY, box.width(), box.height()));
        Rectangle newBounds = Rectangle.union(targets);
        Rectangle oldBounds = oldBoxes.isEmpty() ? null :
                Rectangle.union(oldBoxes.stream().map(HintBox::rectangle).toList());
        List<TransitionedRectangle> transitionedBoxes = new ArrayList<>(boxCount);
        Map<List<Key>, TransitionedRectangle> transitionedBoxByKeySequence = new HashMap<>();
        boolean boxesMove = false;
        for (int boxIndex = 0; boxIndex < boxCount; boxIndex++) {
            List<Key> keySequence = newBoxes.get(boxIndex).hint.keySequence();
            Rectangle target = targets.get(boxIndex);
            TransitionedRectangle transitionedBox =
                    oldTransitionedBoxByKeySequence.get(keySequence);
            // A box without keys starts where it sits in the new grid, mapped onto the old one.
            if (transitionedBox == null)
                transitionedBox = new TransitionedRectangle(
                        !keySequence.isEmpty() || oldBounds == null ? target :
                                mappedRectangle(target, newBounds, oldBounds));
            transitionedBox.transitionTo(transition, target);
            transitionedBoxes.add(transitionedBox);
            if (!keySequence.isEmpty())
                transitionedBoxByKeySequence.put(keySequence, transitionedBox);
            boxesMove |= transitionedBox.transitioning();
        }
        // The boxes cannot morph (no matching old borders, or none moved): the borders follow their
        // content instead, clipped to the crop the way their content is.
        ContainerCrop containerCrop = containerCropByWindow.get(window);
        boolean growCrop = containerCrop != null && containerCrop.container() == contentContainer;
        List<OutgoingBorders> outgoing = List.of();
        if (!boxesMove && containerCrop != null && !growCrop && morph.layer != null) {
            // Carry over the borders of every grid whose content is still shrinking, dropping the ones
            // this grid covers: it draws their area itself now, and backing out of a drill returns to a
            // grid that is in the list, whose borders would then be drawn twice.
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
                !boxesMove && containerCrop != null && (growCrop || !outgoing.isEmpty());
        stopBorderMorph(morph);
        // The layer animates its own copies, so the container's boxes keep their layout geometry.
        List<HintBox> borderBoxes = new ArrayList<>(boxCount);
        for (int i = 0; i < boxCount; i++) {
            HintBox copy = new HintBox(newBoxes.get(i));
            copy.setGeometry(transitionedBoxes.get(i).rectangle());
            borderBoxes.add(copy);
        }
        List<Rectangle> windowInk = new ArrayList<>(enclosingInk.size());
        for (Rectangle rectangle : enclosingInk)
            windowInk.add(new Rectangle(rectangle.x() + originX, rectangle.y() + originY,
                    rectangle.width(), rectangle.height()));
        HintPaintLayer layer = new HintPaintLayer(window, borderBoxes, List.of(), HintBox::paintBorder);
        List<Rectangle> borderInk = new ArrayList<>();
        borderBoxes.forEach(box -> borderInk.add(box.ink()));
        outgoing.forEach(borders -> borders.boxes().forEach(box -> borderInk.add(box.ink())));
        // A box that morphs can go anywhere in the window.
        if (boxesMove || !layer.fitToInk(borderInk, 0, window.width(), window.height()))
            layer.setGeometry(0, 0, window.width(), window.height());
        layer.setEnclosingInk(windowInk);
        layer.raise();
        layer.show();
        morph.layer = layer;
        morph.targets = targets;
        morph.enclosingInk = windowInk;
        morph.transitionedBoxes = transitionedBoxes;
        morph.transitionedBoxByKeySequence = transitionedBoxByKeySequence;
        window.show();
        if (!lockstepCrop)
            return;
        if (growCrop) {
            followContentCrop(layer::setCrop, containerCrop);
            containerCrop.endCallbacks().add(layer::clearCrop);
        }
        else {
            layer.setOutgoing(outgoing);
            followContentCrop(layer::setOutgoingCrop, containerCrop);
            containerCrop.endCallbacks().add(() -> layer.setOutgoing(List.of()));
        }
    }

    /** The rectangle at the same place in {@code to} as it is in {@code from}. */
    private static Rectangle mappedRectangle(Rectangle rectangle, Rectangle from, Rectangle to) {
        double xScale = (double) to.width() / from.width();
        double yScale = (double) to.height() / from.height();
        int left = to.x() + (int) Math.round((rectangle.x() - from.x()) * xScale);
        int top = to.y() + (int) Math.round((rectangle.y() - from.y()) * yScale);
        return new Rectangle(left, top,
                to.x() + (int) Math.round((rectangle.x() + rectangle.width() - from.x()) * xScale) - left,
                to.y() + (int) Math.round((rectangle.y() + rectangle.height() - from.y()) * yScale) - top);
    }

    /** Hands the cropped container's visible region, in window coordinates, to {@code clip} each frame.
     *  Read from the container, not rebuilt from the animation's rects, so the borders clip to exactly
     *  what their content shows even when the crop animates a container consolidated from an
     *  interrupted drill, whose extent and position are not the grid's. The container is cropped
     *  first. */
    private void followContentCrop(Consumer<QRect> clip, ContainerCrop containerCrop) {
        QRect visible = visibleRect(containerCrop.container());
        clip.accept(visible);
        visible.dispose();
        containerCrop.frameCallbacks().add(clip);
    }

    /** A match crop skips morphBorders, so re-attach the (clearWindow-detached) border layer and clip
     *  it to the crop here, else the starting grid's borders vanish instead of shrinking into the
     *  selected box. hideHintMesh drops the layer when the crop ends. */
    private void clipMatchCropBorderLayer(TransparentWindow window) {
        BorderMorph morph = borderMorphByWindow.get(window);
        ContainerCrop containerCrop = containerCropByWindow.get(window);
        if (morph == null || morph.layer == null || containerCrop == null)
            return;
        morph.layer.setParent(window);
        morph.layer.raise();
        morph.layer.show();
        // A transition interrupted by the match leaves the previous grids' borders still shrinking:
        // they follow this crop too, so they keep shrinking into the selected box instead of vanishing.
        followContentCrop(morph.layer::clipAll, containerCrop);
    }

    private void stopBorderMorph(BorderMorph morph) {
        morph.transitionedBoxes = List.of();
        morph.transitionedBoxByKeySequence = Map.of();
        if (morph.layer != null) {
            morph.layer.setParent(null);
            disposeWidget(morph.layer);
            morph.layer = null;
        }
    }

    /** Window children that hold rendered content — everything but the live border layer. */
    /** A shadow pixmap or a container's pixmap is a screen sized buffer that its widget does not
     *  own, so destroying the widget leaves it behind. */
    private static void disposeWidget(QWidget widget) {
        if (widget instanceof HintPaintLayer layer)
            layer.disposeBuffers();
        if (widget instanceof ClearBackgroundQLabel label)
            label.disposeBuffers();
        for (HintPaintLayer layer : widget.findChildren(HintPaintLayer.class))
            layer.disposeBuffers();
        for (ClearBackgroundQLabel label : widget.findChildren(ClearBackgroundQLabel.class))
            label.disposeBuffers();
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
    private boolean coversInProgressTarget(HintMeshWindow hintMeshWindow, HintMesh hintMesh, List<QWidget> containers,
                                           double qtScaleFactor) {
        if (containers.isEmpty() || hintMeshWindow.hints().isEmpty())
            return false;
        QRect inProgressTarget = containers.getLast().geometry();
        QRect bounds = newMeshBounds(hintMeshWindow, hintMesh, qtScaleFactor);
        boolean same = closeInSize(inProgressTarget.width(), bounds.width()) &&
                       closeInSize(inProgressTarget.height(), bounds.height());
        inProgressTarget.dispose();
        bounds.dispose();
        return same;
    }

    /** Whether the crop's target contains the incoming mesh: a drill going deeper (not a larger fresh
     *  grid after a click), so the crop keeps going toward it. */
    private boolean targetContainsNewMesh(HintMeshWindow hintMeshWindow, HintMesh hintMesh, List<QWidget> containers,
                                          double qtScaleFactor) {
        if (containers.isEmpty() || hintMeshWindow.hints().isEmpty())
            return false;
        QRect target = containers.getLast().geometry();
        QRect paddedTarget = paddedRect(target);
        QRect bounds = newMeshBounds(hintMeshWindow, hintMesh, qtScaleFactor);
        boolean contains = paddedTarget.contains(bounds);
        target.dispose();
        paddedTarget.dispose();
        bounds.dispose();
        return contains;
    }

    /** Whether the incoming mesh contains the target: going back out (a reversal), so the crop grows
     *  toward the new mesh instead of insta-finishing. */
    private boolean newMeshContainsTarget(HintMeshWindow hintMeshWindow, HintMesh hintMesh, List<QWidget> containers,
                                          double qtScaleFactor) {
        if (containers.isEmpty() || hintMeshWindow.hints().isEmpty())
            return false;
        QRect target = containers.getLast().geometry();
        QRect bounds = newMeshBounds(hintMeshWindow, hintMesh, qtScaleFactor);
        QRect paddedBounds = paddedRect(bounds);
        boolean contains = paddedBounds.contains(target);
        target.dispose();
        bounds.dispose();
        paddedBounds.dispose();
        return contains;
    }

    /** Bounding rectangle of the incoming mesh's selected (narrowed) cells, in window coordinates. */
    private QRect newMeshBounds(HintMeshWindow hintMeshWindow, HintMesh hintMesh, double qtScaleFactor) {
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
        return new QRect((int) Math.round(left / qtScaleFactor - hintMeshWindow.window.x()),
                (int) Math.round(top / qtScaleFactor - hintMeshWindow.window.y()),
                (int) Math.round((right - left) / qtScaleFactor),
                (int) Math.round((bottom - top) / qtScaleFactor));
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
        if (container instanceof ClearBackgroundQLabel label)
            label.setCrop(r);
        else {
            QRegion region = new QRegion(r);
            container.setMask(region);
            region.dispose();
        }
    }

    private static void cropOrMask(QWidget container, TransitionedRectangle transitionedCrop) {
        Rectangle rectangle = transitionedCrop.rectangle();
        QRect rect = new QRect(rectangle.x() - container.x(), rectangle.y() - container.y(),
                rectangle.width(), rectangle.height());
        cropOrMask(container, rect);
        rect.dispose();
    }

    /** Why a container clips the way it does: a pixmap is cropped (cheap), anything else is masked
     *  (recomposites the whole window every frame). */
    private static String clipping(QWidget container) {
        if (container instanceof ClearBackgroundQLabel)
            return "cropped";
        return "masked";
    }

    private void hintContainerAnimationEnded() {
        if (hintMeshEndAnimation) {
            hintMeshEndAnimation = false;
            hintMeshEndAnimationEndedCallback.run();
        }
    }


    private class HintGroup {

        final int turn;
        double minHintCenterX = Double.MAX_VALUE;
        double minHintCenterY = Double.MAX_VALUE;
        double maxHintCenterX = -Double.MAX_VALUE;
        double maxHintCenterY = -Double.MAX_VALUE;
        boolean atLeastOneHintVisible = false;
        boolean atLeastOneHintSelectable = false;
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        HintBox prefixHintBox;
        HintLabel prefixHintLabel;
        int x, y;

        HintGroup(int turn) {
            this.turn = turn;
        }

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
                                                              ClearBackgroundQLabel container,
                                                              List<Rectangle> enclosingInk) {
        long beforeMetrics = System.nanoTime();
        boolean isHintPartOfGrid = hintMeshWindow.hints().getFirst().cellWidth() != -1;
        double minHintCenterX = Double.MAX_VALUE;
        double minHintCenterY = Double.MAX_VALUE;
        double maxHintCenterX = -Double.MAX_VALUE;
        double maxHintCenterY = -Double.MAX_VALUE;
        Map<List<Key>, Integer> turnByKeySequence =
                turnByKeySequence(hintMesh.hints(), Hint::keySequence);
        Map<List<Key>, Integer> turnByPrefix = turnByKeySequence(hintMesh.hints(),
                hint -> groupPrefix(hintMesh, hint));
        Map<List<Key>, HintGroup> hintGroupByPrefix = new HashMap<>();
        for (Hint hint : hintMeshWindow.hints()) {
            HintGroup hintGroup = hintGroupOf(hintMesh, hintGroupByPrefix, turnByPrefix, hint);
            hintGroup.minHintCenterX = Math.min(hintGroup.minHintCenterX, hint.centerX());
            hintGroup.minHintCenterY = Math.min(hintGroup.minHintCenterY, hint.centerY());
            hintGroup.maxHintCenterX = Math.max(hintGroup.maxHintCenterX, hint.centerX());
            hintGroup.maxHintCenterY = Math.max(hintGroup.maxHintCenterY, hint.centerY());
            hintGroup.atLeastOneHintVisible |= visible(hint, hintMesh);
            hintGroup.atLeastOneHintSelectable |= hint.startsWith(hintMesh.selectedKeySequence());
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
        QtHintFontStyle labelFontStyle = QtHintFont.qtHintFontStyle(style.fontStyle(), prefixFontStyle, screenScale, lastSelectedHintColors);
        GradientColor boxColor = style.boxColor();
        boolean boxSweptPerHint = style.boxOpacity() != 0 && boxColor.gradient() &&
                                  boxColor.area() != GradientArea.ELEMENT;
        Rectangle boxGradientArea =
                boxSweptPerHint ? gradientArea(boxColor.area(), hintMeshWindow, hintMesh) : null;
        QBrush boxBrush = boxSweptPerHint ? null : fillBrush(boxColor, style.boxOpacity());
        List<Hint> savedHints = hintMesh.savedHints();
        HintMeshStyle savedStyle = style.saved();
        QtHintFontStyle savedLabelFontStyle = savedHints.isEmpty() ? null :
                QtHintFont.qtHintFontStyle(savedStyle.fontStyle(),
                        hasForegroundPrefixKeys ? savedStyle.prefixFontStyle() : null,
                        screenScale, lastSelectedHintColors);
        Map<GradientArea, Rectangle> textGradientAreaByArea = new HashMap<>();
        Rectangle savedBoxGradientArea =
                gradientArea(savedStyle.boxColor().area(), hintMeshWindow, hintMesh);
        GradientColor savedBoxBorderColor = gradientColor(savedStyle.boxBorderColor());
        Rectangle savedBoxBorderGradientArea =
                gradientArea(savedBoxBorderColor.area(), hintMeshWindow, hintMesh);
        GradientColor boxBorderColor = gradientColor(style.boxBorderColor());
        Rectangle boxBorderGradientArea =
                gradientArea(boxBorderColor.area(), hintMeshWindow, hintMesh);
        GradientColor boxShadowColor = gradientColor(style.boxShadow().color());
        Rectangle boxShadowGradientArea =
                gradientArea(boxShadowColor.area(), hintMeshWindow, hintMesh);
        GradientColor prefixBoxBorderColor = gradientColor(style.prefixBoxBorderColor());
        Rectangle prefixBoxBorderGradientArea =
                gradientArea(prefixBoxBorderColor.area(), hintMeshWindow, hintMesh);
        int boxHorizontalPadding = scaledLength(style.boxHorizontalPadding(), screenScale);
        int boxVerticalPadding = scaledLength(style.boxVerticalPadding(), screenScale);
        int boxBorderThickness = scaledLength(style.boxBorderThickness(), screenScale);
        int boxBorderLength = scaledLength(style.boxBorderLength(), screenScale);
        int boxBorderRadius = scaledLength(style.boxBorderRadius(), screenScale);
        int prefixBoxBorderThickness =
                scaledLength(style.prefixBoxBorderThickness(), screenScale);
        int prefixBoxBorderLength = scaledLength(style.prefixBoxBorderLength(), screenScale);
        Shadow boxShadow = scaledShadow(style.boxShadow(), screenScale);
        Shadow elementBoxShadow = scaledShadow(style.uiElementBoxShadow(), screenScale);
        boolean elementBoxVisible =
                style.uiElementBoxOpacity() != 0 || style.uiElementBoxBorderOpacity() != 0 ||
                elementBoxShadow.opacity() != 0;
        boolean savedElementBoxVisible = savedStyle.uiElementBoxOpacity() != 0 ||
                                         savedStyle.uiElementBoxBorderOpacity() != 0;
        Rectangle savedElementBoxGradientArea =
                gradientArea(savedStyle.uiElementBoxColor().area(), hintMeshWindow, hintMesh);
        GradientColor savedElementBoxBorderColor =
                gradientColor(savedStyle.uiElementBoxBorderColor());
        Rectangle savedElementBoxBorderGradientArea =
                gradientArea(savedElementBoxBorderColor.area(), hintMeshWindow, hintMesh);
        Rectangle elementBoxGradientArea =
                gradientArea(style.uiElementBoxColor().area(), hintMeshWindow, hintMesh);
        GradientColor elementBoxBorderColor = gradientColor(style.uiElementBoxBorderColor());
        Rectangle elementBoxBorderGradientArea =
                gradientArea(elementBoxBorderColor.area(), hintMeshWindow, hintMesh);
        GradientColor elementBoxShadowColor = gradientColor(style.uiElementBoxShadow().color());
        Rectangle elementBoxShadowGradientArea =
                gradientArea(elementBoxShadowColor.area(), hintMeshWindow, hintMesh);
        int elementBoxBorderThickness =
                scaledLength(style.uiElementBoxBorderThickness(), screenScale);
        int elementBoxBorderLength = scaledLength(style.uiElementBoxBorderLength(), screenScale);
        int elementBoxBorderRadius = scaledLength(style.uiElementBoxBorderRadius(), screenScale);
        int elementBoxHorizontalPadding =
                scaledLength(style.uiElementBoxHorizontalPadding(), screenScale);
        int elementBoxVerticalPadding =
                scaledLength(style.uiElementBoxVerticalPadding(), screenScale);
        List<HintBox> elementBoxes = new ArrayList<>();
        boolean hotspotBoxVisible = hotspotBoxVisible(style);
        boolean savedHotspotBoxVisible = hotspotBoxVisible(savedStyle);
        Rectangle savedHotspotBoxGradientArea =
                gradientArea(savedStyle.hotspotBoxColor().area(), hintMeshWindow, hintMesh);
        GradientColor savedHotspotBoxBorderColor = gradientColor(savedStyle.hotspotBoxBorderColor());
        Rectangle savedHotspotBoxBorderGradientArea =
                gradientArea(savedHotspotBoxBorderColor.area(), hintMeshWindow, hintMesh);
        Rectangle hotspotBoxGradientArea =
                gradientArea(style.hotspotBoxColor().area(), hintMeshWindow, hintMesh);
        GradientColor hotspotBoxBorderColor = gradientColor(style.hotspotBoxBorderColor());
        Rectangle hotspotBoxBorderGradientArea =
                gradientArea(hotspotBoxBorderColor.area(), hintMeshWindow, hintMesh);
        GradientColor hotspotLineColor = gradientColor(style.hotspotLineColor());
        Rectangle hotspotLineGradientArea =
                gradientArea(hotspotLineColor.area(), hintMeshWindow, hintMesh);
        int hotspotBoxWidth = scaledLength(style.hotspotBoxWidth(), screenScale);
        int hotspotBoxHeight = scaledLength(style.hotspotBoxHeight(), screenScale);
        int hotspotBoxBorderThickness = scaledLength(style.hotspotBoxBorderThickness(), screenScale);
        int hotspotBoxBorderRadius = scaledLength(style.hotspotBoxBorderRadius(), screenScale);
        int hotspotLineThickness = scaledLength(style.hotspotLineThickness(), screenScale);
        // One entry per tiled depth: subdecoration at index 0, subsubdecoration at index 1.
        List<DecorationStyle> subDecorationStyles = new ArrayList<>();
        if (hintMesh.subDecoration() != null) {
            subDecorationStyles.add(decorationStyle(style.decorations().get(1), screenScale,
                    hintMeshWindow, hintMesh));
            if (hintMesh.subDecoration().subDecoration() != null)
                subDecorationStyles.add(decorationStyle(style.decorations().get(2),
                        screenScale, hintMeshWindow, hintMesh));
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
        List<HintLabel> unsavedHintLabels = new ArrayList<>();
        List<HintLabel> savedHintLabels = new ArrayList<>();
        long beforeBoxes = System.nanoTime();
        long lastPumpTime = System.nanoTime();
        for (int hintIndex = 0; hintIndex < hints.size(); hintIndex++) {
            Hint hint = hints.get(hintIndex);
            boolean selectable = hint.startsWith(hintMesh.selectedKeySequence());
            boolean hintSaved = savedHints.contains(hint);
            if (!selectable && !hintSaved)
                continue;
            List<Key> labelKeys = labelOverridden ? labelOverride : hint.keySequence();
            int totalXAdvance = xAdvancesByString.computeIfAbsent(
                    labelKeys.stream().map(Key::hintLabel).collect(Collectors.joining()),
                    labelFontStyle.defaultStyle().metrics()::horizontalAdvance);
            int lineHeight = labelFontStyle.defaultStyle().metrics().height();
            // A cell is in pixels, as the padding and the grid's own cell are, but metrics are
            // in the points Qt draws text in. Outside a grid, the label is its text, or its
            // columns.
            double advancePixels = (labelFontStyle.fontSpacingPercent() < 0.5 ? totalXAdvance :
                    hintKeyMaxXAdvance * labelKeys.size()) * qtScaleFactor;
            double lineHeightPixels = lineHeight * qtScaleFactor;
            // A grid cell is used as-is so boxes tile perfectly. Text that overflows is handled
            // by the label layer.
            double cellWidth = (isHintPartOfGrid ? hint.cellWidth() : advancePixels) +
                               2 * boxHorizontalPadding;
            double cellHeight = (isHintPartOfGrid ? hint.cellHeight() : lineHeightPixels) +
                                2 * boxVerticalPadding;
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
                            hintSaved ? savedLabelFontStyle : labelFontStyle,
                            hintKeyMaxXAdvance,
                            labelOverridden || !selectable ? -1 :
                                    (hintMesh.selectedKeySequence().size() - 1
                                    - (style.prefixInBackground() && hintMesh.prefixLength() != -1 ? prefix.size() : 0)),
                            style.fontStyle().defaultFontStyle().verticalAlignment(),
                            isHintPartOfTiledGrid, qtScaleFactor);
            hintLabels.add(hintLabel);
            if (hintSaved)
                savedHintLabels.add(hintLabel);
            else
                unsavedHintLabels.add(hintLabel);
            boolean gridLeftEdge = !isHintPartOfGrid || !selectable || hint.centerX() == minHintCenterX || style.boxWidthPercent() != 1;
            boolean gridTopEdge = !isHintPartOfGrid || !selectable || hint.centerY() == minHintCenterY || style.boxHeightPercent() != 1;
            boolean gridRightEdge = !isHintPartOfGrid || !selectable || hint.centerX() == maxHintCenterX || style.boxWidthPercent() != 1;
            boolean gridBottomEdge = !isHintPartOfGrid || !selectable || hint.centerY() == maxHintCenterY || style.boxHeightPercent() != 1;
            HintGroup hintGroup = hintGroupByPrefix.get(groupPrefix(hintMesh, hint));
            HintBox hintBox =
                    new HintBox(hint, boxBorderLength,
                            boxBorderThickness,
                            boxSweptPerHint &&
                            boxColor.step() != GradientStep.PIXEL ?
                                    sampledBrush(boxColor, style.boxOpacity(), boxGradientArea,
                                            hintGroup, new Point(hint.centerX(), hint.centerY()),
                                            turnByKeySequence.get(hint.keySequence())) :
                                    boxBrush,
                            null,
                            isHintPartOfGrid,
                            gridLeftEdge, gridTopEdge, gridRightEdge, gridBottomEdge,
                            style.boxFramed(),
                            qtScaleFactor,
                            boxBorderRadius
                    );
            hintBoxes.add(hintBox);
            int labelBoxWidth = (int) ((hintLabel.tightHintBoxWidth * qtScaleFactor +
                                        2 * boxHorizontalPadding) / qtScaleFactor);
            int scaledBoxWidth = style.boxWidthPercent() == 1 ? fullBoxWidth : Math.max(
                    (int) (fullBoxWidth * style.boxWidthPercent()), labelBoxWidth);
            int scaledBoxHeight = style.boxHeightPercent() == 1 ? fullBoxHeight : Math.max(
                    (int) (fullBoxHeight * style.boxHeightPercent()),
                    (int) ((lineHeightPixels + 2 * boxVerticalPadding) / qtScaleFactor));
            int boxWidth = !isHintPartOfGrid ? labelBoxWidth :
                    isHintPartOfTiledGrid ? scaledBoxWidth :
                            Math.max(hintLabel.tightHintBoxWidth, scaledBoxWidth);
            int boxHeight = isHintPartOfTiledGrid ? scaledBoxHeight :
                    Math.max(hintLabel.tightHintBoxHeight, scaledBoxHeight);
            hintLabel.left = isHintPartOfGrid ? (fullBoxWidth - boxWidth) / 2 :
                    hintLabel.tightHintBoxLeft - (boxWidth - hintLabel.tightHintBoxWidth) / 2;
            hintLabel.top = (fullBoxHeight - boxHeight) / 2;
            x = isHintPartOfGrid ? x + hintLabel.left :
                    hintRoundedX(hint.centerX(), boxWidth * qtScaleFactor, qtScaleFactor);
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
            if ((elementBoxVisible || savedElementBoxVisible && hintSaved) &&
                hint.elementRectangle() != null) {
                int elementBoxLeft = (int) Math.round(
                        (hint.elementRectangle().x() - elementBoxHorizontalPadding) / qtScaleFactor);
                int elementBoxTop = (int) Math.round(
                        (hint.elementRectangle().y() - elementBoxVerticalPadding) / qtScaleFactor);
                int elementBoxRight = (int) Math.round((hint.elementRectangle().x()
                        + hint.elementRectangle().width() + elementBoxHorizontalPadding) / qtScaleFactor);
                int elementBoxBottom = (int) Math.round((hint.elementRectangle().y()
                        + hint.elementRectangle().height() + elementBoxVerticalPadding) / qtScaleFactor);
                HintBox elementBox = new HintBox(hint, elementBoxBorderLength,
                        elementBoxBorderThickness, null, null,
                        false, true, true, true, true, true, qtScaleFactor,
                        elementBoxBorderRadius);
                elementBox.setGeometry(elementBoxLeft - hintMeshWindow.window.x(),
                        elementBoxTop - hintMeshWindow.window.y(),
                        elementBoxRight - elementBoxLeft, elementBoxBottom - elementBoxTop);
                elementBoxes.add(elementBox);
                minHintLeft = Math.min(minHintLeft, elementBoxLeft);
                minHintTop = Math.min(minHintTop, elementBoxTop);
                maxHintRight = Math.max(maxHintRight, elementBoxRight);
                maxHintBottom = Math.max(maxHintBottom, elementBoxBottom);
            }
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
            if (!style.prefixBoxEnabled() || hintMesh.prefixLength() == -1)
                continue;
            boolean gridLeftEdge =
                    !isHintPartOfGrid || hintGroup.minHintCenterX == minHintCenterX ||
                    style.boxWidthPercent() != 1;
            boolean gridTopEdge =
                    !isHintPartOfGrid || hintGroup.minHintCenterY == minHintCenterY ||
                    style.boxHeightPercent() != 1;
            boolean gridRightEdge =
                    !isHintPartOfGrid || hintGroup.maxHintCenterX == maxHintCenterX ||
                    style.boxWidthPercent() != 1;
            boolean gridBottomEdge =
                    !isHintPartOfGrid || hintGroup.maxHintCenterY == maxHintCenterY ||
                    style.boxHeightPercent() != 1;
            HintBox prefixHintBox =
                    new HintBox(null, prefixBoxBorderLength,
                            prefixBoxBorderThickness,
                            null,
                            null,
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
        if (style.prefixInBackground() && hintMesh.prefixLength() != -1) {
            prefixQtHintFontStyle = QtHintFont.qtHintFontStyle(style.prefixFontStyle(), null, screenScale, lastSelectedHintColors);
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
                int totalXAdvance = prefixXAdvancesByString.computeIfAbsent(
                        prefix.stream().map(Key::hintLabel).collect(Collectors.joining()),
                        prefixQtHintFontStyle.defaultStyle().metrics()::horizontalAdvance);
                int fullBoxWidth = hintGroup.right - hintGroup.left;
                int fullBoxHeight = hintGroup.bottom - hintGroup.top;
                HintLabel prefixHintLabel =
                        HintLabel.of(prefix, prefixXAdvancesByString, fullBoxWidth,
                                fullBoxHeight, totalXAdvance,
                                hintMesh.prefixLength(),
                                prefixQtHintFontStyle,
                                prefixHintKeyMaxXAdvance,
                                hintGroup.atLeastOneHintSelectable ?
                                        hintMesh.selectedKeySequence().size() - 1 : -1,
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
        int shadowPadLeft = 0, shadowPadRight = 0, shadowPadTop = 0, shadowPadBottom = 0;
        for (Shadow shadow : List.of(boxShadow, elementBoxShadow)) {
            if (shadow.opacity() == 0)
                continue;
            shadowPadLeft = Math.max(shadowPadLeft, (int) Math.ceil((shadow.blurRadius() + Math.max(0, -shadow.horizontalOffset())) / qtScaleFactor));
            shadowPadRight = Math.max(shadowPadRight, (int) Math.ceil((shadow.blurRadius() + Math.max(0, shadow.horizontalOffset())) / qtScaleFactor));
            shadowPadTop = Math.max(shadowPadTop, (int) Math.ceil((shadow.blurRadius() + Math.max(0, -shadow.verticalOffset())) / qtScaleFactor));
            shadowPadBottom = Math.max(shadowPadBottom, (int) Math.ceil((shadow.blurRadius() + Math.max(0, shadow.verticalOffset())) / qtScaleFactor));
        }
        minHintLeft -= shadowPadLeft;
        minHintTop -= shadowPadTop;
        maxHintRight += shadowPadRight;
        maxHintBottom += shadowPadBottom;
        if (hintMesh.decoration() != null) {
            Rectangle area = hintMesh.decoration().backgroundArea();
            int horizontalPadding =
                    scaledLength(style.decorations().getFirst().boxHorizontalPadding(), screenScale);
            int verticalPadding =
                    scaledLength(style.decorations().getFirst().boxVerticalPadding(), screenScale);
            minHintLeft = Math.min(minHintLeft,
                    (int) Math.round((area.x() - horizontalPadding) / qtScaleFactor));
            minHintTop = Math.min(minHintTop,
                    (int) Math.round((area.y() - verticalPadding) / qtScaleFactor));
            maxHintRight = Math.max(maxHintRight, (int) Math.round(
                    (area.x() + area.width() + horizontalPadding) / qtScaleFactor));
            maxHintBottom = Math.max(maxHintBottom, (int) Math.round(
                    (area.y() + area.height() + verticalPadding) / qtScaleFactor));
        }
        // One window until the hotspots move: two windows can change in different frames.
        boolean inHotspotWindow = !hintMeshKey.hotspotByKeySequence().isEmpty() &&
                                    hotspotWindowByWindow.containsKey(hintMeshWindow.window);
        if (!hintMeshKey.hotspotByKeySequence().isEmpty() && !inHotspotWindow) {
            minHintLeft = Math.min(minHintLeft, hintMeshWindow.window.x());
            minHintTop = Math.min(minHintTop, hintMeshWindow.window.y());
            maxHintRight = Math.max(maxHintRight,
                    hintMeshWindow.window.x() + hintMeshWindow.window.width());
            maxHintBottom = Math.max(maxHintBottom,
                    hintMeshWindow.window.y() + hintMeshWindow.window.height());
        }
        int offsetX = minHintLeft - hintMeshWindow.window.x();
        int offsetY = minHintTop - hintMeshWindow.window.y();
        // The boxes are moved into the container at (offsetX, offsetY), so the sweep is too.
        Point sweepOrigin = new Point((hintMeshWindow.window.x() + offsetX) * qtScaleFactor,
                (hintMeshWindow.window.y() + offsetY) * qtScaleFactor);
        Point windowOrigin = new Point(hintMeshWindow.window.x() * qtScaleFactor,
                hintMeshWindow.window.y() * qtScaleFactor);
        Map<List<Key>, Rectangle> hintBoxRectangles = new HashMap<>();
        for (int hintIndex = 0; hintIndex < hintBoxes.size(); hintIndex++) {
            HintBox hintBox = hintBoxes.get(hintIndex);
            Rectangle windowPixelRectangle = hintBox.pixelRectangle(0, 0);
            hintBox.move(hintBox.x() - offsetX, hintBox.y() - offsetY);
            HintLabel hintLabel = hintLabels.get(hintIndex);
            hintLabel.move(hintBox.x(), hintBox.y());
            HintGroup hintGroup =
                    hintGroupOf(hintMesh, hintGroupByPrefix, turnByPrefix, hintBox.hint);
            if (boxSweptPerHint && boxColor.step() == GradientStep.PIXEL)
                hintBox.setColor(sweptBrush(boxColor, style.boxOpacity(),
                        areaNarrowedToGroup(boxColor, boxGradientArea, hintGroup),
                        sweepOrigin));
            Point hintCenter = new Point(hintBox.hint.centerX(), hintBox.hint.centerY());
            int turn = turnByKeySequence.get(hintBox.hint.keySequence());
            // The live border layer paints a grid's border, and that layer starts at the window.
            hintBox.setBorderColor(brush(boxBorderColor, style.boxBorderOpacity(),
                    boxBorderGradientArea, hintGroup, hintCenter, turn,
                    isHintPartOfGrid ? windowPixelRectangle : hintBox.pixelRectangle(0, 0),
                    isHintPartOfGrid ? windowOrigin : sweepOrigin));
            boolean hintSaved = savedHints.contains(hintBox.hint);
            if (hintSaved) {
                hintBox.setColor(brush(savedStyle.boxColor(), savedStyle.boxOpacity(),
                        savedBoxGradientArea, hintGroup, hintCenter, turn,
                        hintBox.pixelRectangle(0, 0), sweepOrigin));
                hintBox.setBorderColor(brush(savedBoxBorderColor,
                        savedStyle.boxBorderOpacity(), savedBoxBorderGradientArea, hintGroup,
                        hintCenter, turn,
                        isHintPartOfGrid ? windowPixelRectangle : hintBox.pixelRectangle(0, 0),
                        isHintPartOfGrid ? windowOrigin : sweepOrigin));
            }
            hintBox.setShadowColor(brush(boxShadowColor, 1, boxShadowGradientArea, hintGroup,
                    hintCenter, turn, hintBox.pixelRectangle(0, 0), sweepOrigin));
            HintMeshStyle hintStyle = hintSaved ? savedStyle : style;
            if (sampledText(hintStyle, hasForegroundPrefixKeys)) {
                // On the screen, as the hint center is.
                Rectangle box = hintBox.pixelRectangle((int) Math.round(sweepOrigin.x()),
                        (int) Math.round(sweepOrigin.y()));
                hintLabel.labelFontStyle = sampledLabelFontStyle(
                        hintLabel.labelFontStyle, hintStyle.fontStyle(),
                        hintStyle.prefixFontStyle(),
                        color -> color.area() == GradientArea.ELEMENT ? box :
                                textGradientAreaByArea.computeIfAbsent(color.area(),
                                        area -> gradientArea(area, hintMeshWindow, hintMesh)),
                        hintGroup, hintCenter, turn);
            }
            setDecorationColors(hintBox, hintBox.pixelRectangle(0, 0),
                    subDecorationStyles, 0, hintGroup, turn, sweepOrigin);
            hintBoxRectangles.put(hintBox.hint.keySequence(),
                    new Rectangle(hintBox.x(), hintBox.y(), hintBox.width(), hintBox.height()));
        }
        for (HintBox elementBox : elementBoxes) {
            elementBox.move(elementBox.x() - offsetX, elementBox.y() - offsetY);
            HintGroup hintGroup = hintGroupOf(hintMesh, hintGroupByPrefix, turnByPrefix,
                    elementBox.hint);
            Point hintCenter = new Point(elementBox.hint.centerX(), elementBox.hint.centerY());
            int turn = turnByKeySequence.get(elementBox.hint.keySequence());
            Rectangle pixelRectangle = elementBox.pixelRectangle(0, 0);
            if (style.uiElementBoxOpacity() != 0)
                elementBox.setColor(brush(style.uiElementBoxColor(), style.uiElementBoxOpacity(),
                        elementBoxGradientArea, hintGroup, hintCenter, turn,
                        pixelRectangle, sweepOrigin));
            elementBox.setBorderColor(brush(elementBoxBorderColor,
                    style.uiElementBoxBorderOpacity(), elementBoxBorderGradientArea, hintGroup,
                    hintCenter, turn, pixelRectangle, sweepOrigin));
            elementBox.setShadowColor(brush(elementBoxShadowColor, 1,
                    elementBoxShadowGradientArea, hintGroup, hintCenter, turn, pixelRectangle,
                    sweepOrigin));
            if (savedHints.contains(elementBox.hint)) {
                elementBox.setColor(brush(savedStyle.uiElementBoxColor(),
                        savedStyle.uiElementBoxOpacity(), savedElementBoxGradientArea,
                        hintGroup, hintCenter, turn, pixelRectangle, sweepOrigin));
                elementBox.setBorderColor(brush(savedElementBoxBorderColor,
                        savedStyle.uiElementBoxBorderOpacity(),
                        savedElementBoxBorderGradientArea, hintGroup, hintCenter, turn,
                        pixelRectangle, sweepOrigin));
            }
        }
        List<HintBox> hotspotBoxes = new ArrayList<>();
        Rectangle hotspotBoxArea = new Rectangle(0, 0, hotspotBoxWidth, hotspotBoxHeight);
        for (Hint hint : hints) {
            boolean hintSaved = savedHints.contains(hint);
            if (!hintMesh.hotspotByKeySequence().containsKey(hint.keySequence()) ||
                !hotspotBoxVisible && !(savedHotspotBoxVisible && hintSaved) ||
                !hint.startsWith(hintMesh.selectedKeySequence()) && !hintSaved)
                continue;
            HintBox hotspotBox = new HintBox(hint, Integer.MAX_VALUE, hotspotBoxBorderThickness, null,
                    null, false, true, true, true, true, true, qtScaleFactor,
                    hotspotBoxBorderRadius);
            HintGroup hintGroup = hintGroupOf(hintMesh, hintGroupByPrefix, turnByPrefix, hint);
            Point hintCenter = new Point(hint.centerX(), hint.centerY());
            int turn = turnByKeySequence.get(hint.keySequence());
            if (style.hotspotBoxOpacity() != 0)
                hotspotBox.setColor(hotspotBrush(style.hotspotBoxColor(), style.hotspotBoxOpacity(),
                        hotspotBoxGradientArea, hintGroup, hintCenter, turn, sweepOrigin));
            hotspotBox.setBorderColor(hotspotBrush(hotspotBoxBorderColor, style.hotspotBoxBorderOpacity(),
                    hotspotBoxBorderGradientArea, hintGroup, hintCenter, turn, sweepOrigin));
            if (hintSaved) {
                hotspotBox.setColor(hotspotBrush(savedStyle.hotspotBoxColor(),
                        savedStyle.hotspotBoxOpacity(), savedHotspotBoxGradientArea, hintGroup,
                        hintCenter, turn, sweepOrigin));
                hotspotBox.setBorderColor(hotspotBrush(savedHotspotBoxBorderColor,
                        savedStyle.hotspotBoxBorderOpacity(), savedHotspotBoxBorderGradientArea,
                        hintGroup, hintCenter, turn, sweepOrigin));
            }
            // A line takes the color of the sweep of its box where it ends, at the center.
            if (style.hotspotLineOpacity() != 0)
                hotspotBox.setLine(hintBoxRectangles.get(hint.keySequence()),
                        hotspotLineColor.area() == GradientArea.ELEMENT ?
                                sampledBrush(hotspotLineColor, style.hotspotLineOpacity(),
                                        hotspotBoxArea, hintGroup,
                                        new Point(hotspotBoxWidth / 2d, hotspotBoxHeight / 2d), turn) :
                                brush(hotspotLineColor, style.hotspotLineOpacity(),
                                        hotspotLineGradientArea, hintGroup, hintCenter, turn, null,
                                        sweepOrigin),
                        hotspotLineThickness);
            hotspotBoxes.add(hotspotBox);
        }
        container.hotspotBoxes = hotspotBoxes;
        container.inHotspotWindow = inHotspotWindow;
        container.hotspotBoxWidth = hotspotBoxWidth;
        container.hotspotBoxHeight = hotspotBoxHeight;
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
            prefixHintBox.setBorderColor(brush(prefixBoxBorderColor,
                    style.prefixBoxBorderOpacity(), prefixBoxBorderGradientArea, hintGroup,
                    hintGroup.centerArea().center(), hintGroup.turn,
                    prefixHintBox.pixelRectangle(0, 0), sweepOrigin));
            prefixBoxes.add(prefixHintBox);
        }
        for (HintGroup hintGroup : hintGroupByPrefix.values()) {
            HintLabel prefixHintLabel = hintGroup.prefixHintLabel;
            if (prefixHintLabel == null)
                continue;
            if (sampledText(style.prefixFontStyle()))
                prefixHintLabel.labelFontStyle = sampledLabelFontStyle(
                        prefixHintLabel.labelFontStyle, style.prefixFontStyle(), null,
                        color -> color.area() == GradientArea.ELEMENT ? hintGroup.centerArea() :
                                textGradientAreaByArea.computeIfAbsent(color.area(),
                                        area -> gradientArea(area, hintMeshWindow, hintMesh)),
                        hintGroup, hintGroup.centerArea().center(), hintGroup.turn);
            prefixLabels.add(prefixHintLabel);
        }
        long beforeLayers = System.nanoTime();
        int containerWidth = maxHintRight - minHintLeft;
        int containerHeight = maxHintBottom - minHintTop;
        container.setGeometry(offsetX, offsetY, containerWidth, containerHeight);
        placeHotspotBoxes(container, hintMesh.hotspotByKeySequence());
        // Layer 0: Element box shadow and element boxes, under the hotspots.
        HintPaintLayer elementBoxShadowLayer = new HintPaintLayer(container, List.of(), List.of());
        elementBoxShadowLayer.setGeometry(0, 0, containerWidth, containerHeight);
        HintPaintLayer elementBoxLayer = new HintPaintLayer(container, elementBoxes, List.of());
        elementBoxLayer.setGeometry(0, 0, containerWidth, containerHeight);
        applyBoxShadow(elementBoxShadowLayer, elementBoxes, elementBoxShadow,
                containerWidth, containerHeight, screenScale, qtScaleFactor);
        if (!elementBoxes.isEmpty())
            container.belowHotspotLayers = List.of(elementBoxShadowLayer, elementBoxLayer);
        // Layer 1: Box shadow (painted underneath boxes; empty unless shadow is active).
        HintPaintLayer boxShadowLayer = new HintPaintLayer(container, List.of(), List.of());
        boxShadowLayer.setGeometry(0, 0, containerWidth, containerHeight);
        // Layer 2: the hint boxes. A grid's border moves on the live border layer instead.
        HintPaintLayer hintBoxLayer = new HintPaintLayer(container, hintBoxes, List.of(),
                isHintPartOfGrid ? HintBox::paintWithoutBorder : HintBox::paint);
        hintBoxLayer.setGeometry(0, 0, containerWidth, containerHeight);
        applyBoxShadow(boxShadowLayer, hintBoxes, boxShadow,
                containerWidth, containerHeight, screenScale, qtScaleFactor);
        List<HintPaintLayer> decorationLabelLayers =
                addDecorationLabelLayers(container, hintBoxes, subDecorationStyles,
                        containerWidth, containerHeight);
        // Layer 3: Prefix boxes.
        HintPaintLayer prefixBoxLayer = new HintPaintLayer(container, prefixBoxes, List.of());
        prefixBoxLayer.setGeometry(0, 0, containerWidth, containerHeight);
        for (HintBox prefixBox : prefixBoxes)
            prefixBox.collectBorderInk(0, 0, enclosingInk);
        // Layer 3: Prefix labels.
        addLabelLayer(container, prefixLabels, prefixQtHintFontStyle, hasSelectedKeys,
                containerWidth, containerHeight, screenScale);
        // Layer 4: Hint labels.
        addLabelLayer(container, unsavedHintLabels, labelFontStyle, hasSelectedKeys,
                containerWidth, containerHeight, screenScale);
        if (!savedHintLabels.isEmpty())
            addLabelLayer(container, savedHintLabels, savedLabelFontStyle, hasSelectedKeys,
                    containerWidth, containerHeight, screenScale);
        // Layer 5: whole-area decoration (index 0), anchored to the grid area it was laid out in, not
        // to the container: a grid drilled past a screen edge has cells the container cannot show.
        HintBox areaBox = null;
        List<DecorationStyle> areaDecorationStyles = List.of();
        List<HintPaintLayer> areaDecorationLabelLayers = List.of();
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
            areaBox = new HintBox(null, 0, 0, null, QtColorUtil.qBrush(0),
                    true, false, false, false, false, false, qtScaleFactor, 0);
            areaBox.setGeometry(areaLeft, areaTop, areaWidth, areaHeight);
            areaDecorationStyles = List.of(decorationStyle(style.decorations().get(0),
                    screenScale, hintMeshWindow, hintMesh));
            addDecorationBoxes(areaBox, areaWidth, areaHeight,
                    hintMesh.decoration(), areaDecorationStyles, 0, qtScaleFactor);
            setDecorationColors(areaBox, areaBox.pixelRectangle(0, 0),
                    areaDecorationStyles, 0, null, 0, sweepOrigin);
            List<Rectangle> areaInk = new ArrayList<>();
            areaBox.collectBorderInk(0, 0, areaInk);
            prefixBoxLayer.setEnclosingInk(areaInk);
            enclosingInk.addAll(areaInk);
            HintPaintLayer areaDecorationLayer =
                    new HintPaintLayer(container, List.of(areaBox), List.of());
            areaDecorationLayer.setGeometry(0, 0, containerWidth, containerHeight);
            areaDecorationLabelLayers = addDecorationLabelLayers(container, List.of(areaBox),
                    areaDecorationStyles, containerWidth, containerHeight);
        }
        dropOverlappingDecorationLabels(hintBoxes, areaBox,
                style.fontStyle().defaultFontStyle().opacity() != 0 &&
                !hints.getFirst().keySequence().isEmpty(),
                subDecorationStyles, areaDecorationStyles);
        addDecorationLabelShadows(decorationLabelLayers, hintBoxes, subDecorationStyles,
                containerWidth, containerHeight, screenScale);
        if (areaBox != null)
            addDecorationLabelShadows(areaDecorationLabelLayers, List.of(areaBox),
                    areaDecorationStyles, containerWidth, containerHeight, screenScale);
        hintBoxRectanglesByHintMeshKey.put(hintMeshKey, hintBoxRectangles);
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

    private static QBrush fillBrush(int rgb, double opacity) {
        return opacity == 0 ? null : QtColorUtil.qBrush(QtColorUtil.rgba(rgb, opacity));
    }

    private static QBrush fillBrush(GradientColor color, double opacity) {
        return opacity == 0 || !color.gradient() ? fillBrush(color.rgbAt(0), opacity) :
                QtColorUtil.qBrush(color, opacity);
    }

    private Rectangle gradientArea(GradientArea area, HintMeshWindow hintMeshWindow,
                                   HintMesh hintMesh) {
        return switch (area) {
            case SCREEN -> screenArea(hintMeshWindow.window());
            case ALL_SCREENS -> Rectangle.union(hintMeshWindows.values().stream()
                    .map(window -> screenArea(window.window())).toList());
            case AREA -> hintMesh.area();
            // A subgrid narrows this to its own bounds per box.
            case ALL_ELEMENTS, GROUP -> centerArea(hintMeshWindow.hints());
            case ELEMENT -> null;
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

    private static QBrush sweptBrush(GradientColor color, double opacity, Rectangle area,
                                     Point origin) {
        Point start = color.direction().start(area);
        Point end = color.direction().end(area);
        return QtColorUtil.qBrush(color, opacity,
                new Point(start.x() - origin.x(), start.y() - origin.y()),
                new Point(end.x() - origin.x(), end.y() - origin.y()));
    }

    private HintGroup hintGroupOf(HintMesh hintMesh, Map<List<Key>, HintGroup> hintGroupByPrefix,
                                  Map<List<Key>, Integer> turnByPrefix, Hint hint) {
        return hintGroupByPrefix.computeIfAbsent(groupPrefix(hintMesh, hint),
                prefix -> new HintGroup(turnByPrefix.get(prefix)));
    }

    /** Without prefixes, the whole grid is one group. */
    private static List<Key> groupPrefix(HintMesh hintMesh, Hint hint) {
        List<Key> keySequence = hint.keySequence();
        if (hint.cellWidth() != -1)
            return hintMesh.prefixLength() == -1 ? List.of() :
                    keySequence.subList(0, hintMesh.prefixLength());
        return keySequence.isEmpty() ? keySequence :
                keySequence.subList(0, keySequence.size() - 1);
    }

    private static Map<List<Key>, Integer> turnByKeySequence(
            List<Hint> hints, Function<Hint, List<Key>> keySequence) {
        Map<List<Key>, Integer> turnByKeySequence = new HashMap<>();
        for (Hint hint : hints)
            turnByKeySequence.putIfAbsent(keySequence.apply(hint), turnByKeySequence.size());
        return turnByKeySequence;
    }

    private static Rectangle areaNarrowedToGroup(GradientColor color, Rectangle area,
                                                 HintGroup hintGroup) {
        return color.area() == GradientArea.GROUP && hintGroup != null ?
                hintGroup.centerArea() : area;
    }

    private static QBrush sampledBrush(GradientColor color, double opacity, Rectangle area,
                                       HintGroup hintGroup, Point center, int turn) {
        if (color.direction() == GradientDirection.IN_TURN)
            return QtColorUtil.qBrush(QtColorUtil.rgba(
                    color.turnRgb(sampledTurn(color, hintGroup, turn)), opacity));
        return QtColorUtil.qBrush(color, opacity,
                sampledPosition(color, area, hintGroup, center));
    }

    private static int sampledTurn(GradientColor color, HintGroup hintGroup, int turn) {
        return color.step() == GradientStep.GROUP && hintGroup != null ?
                hintGroup.turn : turn;
    }

    private static double sampledPosition(GradientColor color, Rectangle area,
                                          HintGroup hintGroup, Point center) {
        Rectangle sampledArea = areaNarrowedToGroup(color, area, hintGroup);
        Point sampled = color.step() == GradientStep.GROUP && hintGroup != null ?
                hintGroup.centerArea().center() : center;
        return color.sweepPosition(sampledArea, sampled.x(), sampled.y());
    }

    private boolean sampledText(HintMeshStyle style, boolean prefix) {
        return sampledText(style.fontStyle()) || prefix && sampledText(style.prefixFontStyle());
    }

    private boolean sampledText(HintFontStyle fontStyle) {
        return sampledText(fontStyle.defaultFontStyle()) ||
               sampledText(fontStyle.selectedFontStyle()) ||
               sampledText(fontStyle.focusedFontStyle());
    }

    private boolean sampledText(FontStyle fontStyle) {
        return gradient(fontStyle.color()) || gradient(fontStyle.outlineColor()) ||
               gradient(fontStyle.shadow().color());
    }

    private boolean gradient(Color color) {
        return gradientColor(color).gradient();
    }

    /** A key's text, outline and shadow are each drawn in one color, so a gradient is sampled
     *  at the hint, the way a box samples a gradient. */
    private QtHintFontStyle sampledLabelFontStyle(
            QtHintFontStyle labelFontStyle, HintFontStyle fontStyle,
            HintFontStyle prefixFontStyle, Function<GradientColor, Rectangle> gradientArea,
            HintGroup hintGroup, Point center, int turn) {
        BiFunction<QtFontStyle, FontStyle, QtFontStyle> sampled =
                (qtFontStyle, stateFontStyle) -> sampledFontStyle(qtFontStyle, stateFontStyle,
                        gradientArea, hintGroup, center, turn);
        boolean prefix = labelFontStyle.prefixDefaultStyle() != null;
        return new QtHintFontStyle(
                sampled.apply(labelFontStyle.defaultStyle(), fontStyle.defaultFontStyle()),
                sampled.apply(labelFontStyle.selectedStyle(), fontStyle.selectedFontStyle()),
                sampled.apply(labelFontStyle.focusedStyle(), fontStyle.focusedFontStyle()),
                prefix ? sampled.apply(labelFontStyle.prefixDefaultStyle(),
                        prefixFontStyle.defaultFontStyle()) : null,
                prefix ? sampled.apply(labelFontStyle.prefixSelectedStyle(),
                        prefixFontStyle.selectedFontStyle()) : null,
                prefix ? sampled.apply(labelFontStyle.prefixFocusedStyle(),
                        prefixFontStyle.focusedFontStyle()) : null,
                labelFontStyle.perKeyFont(), labelFontStyle.perKeyShadow(),
                labelFontStyle.fontSpacingPercent());
    }

    private QtFontStyle sampledFontStyle(QtFontStyle qtFontStyle, FontStyle fontStyle,
                                                Function<GradientColor, Rectangle> gradientArea,
                                                HintGroup hintGroup, Point center, int turn) {
        if (!sampledText(fontStyle))
            return qtFontStyle;
        return qtFontStyle.withColors(
                sampledColor(qtFontStyle.color(), fontStyle.color(), fontStyle.opacity(),
                        gradientArea, hintGroup, center, turn),
                sampledColor(qtFontStyle.outlineColor(), fontStyle.outlineColor(),
                        fontStyle.outlineOpacity(), gradientArea, hintGroup, center, turn),
                sampledColor(qtFontStyle.shadowColor(), fontStyle.shadow().color(),
                        fontStyle.shadow().opacity(), gradientArea, hintGroup, center, turn));
    }

    /** unsampled when color is not a gradient. */
    private QColor sampledColor(QColor unsampled, Color color, double opacity,
                                Function<GradientColor, Rectangle> gradientArea,
                                HintGroup hintGroup, Point center, int turn) {
        GradientColor gradientColor = gradientColor(color);
        if (!gradientColor.gradient())
            return unsampled;
        if (gradientColor.direction() == GradientDirection.IN_TURN)
            return QColor.fromRgba(QtColorUtil.rgba(
                    gradientColor.turnRgb(sampledTurn(gradientColor, hintGroup, turn)), opacity));
        return QtColorUtil.qColor(gradientColor, opacity, sampledPosition(gradientColor,
                gradientArea.apply(gradientColor), hintGroup, center));
    }

    /** A saved hint stays when the selected keys hide the other hints. */
    static boolean visible(Hint hint, HintMesh hintMesh) {
        return hint.startsWith(hintMesh.selectedKeySequence()) ||
               hintMesh.savedHints().contains(hint);
    }

    /** box is in the pixels of the layer that paints it, whose top left is sweepOrigin. A border is
     *  stroked a line at a time, so a sweep across each box is laid over the box itself. */
    private static QBrush brush(GradientColor color, double opacity, Rectangle gradientArea,
                                HintGroup hintGroup, Point center, int turn,
                                Rectangle box, Point sweepOrigin) {
        if (!color.gradient())
            return QtColorUtil.qBrush(QtColorUtil.rgba(color.rgbAt(0), opacity));
        if (color.area() == GradientArea.ELEMENT)
            return QtColorUtil.qBrush(color, opacity, box);
        if (color.step() != GradientStep.PIXEL)
            return sampledBrush(color, opacity, gradientArea, hintGroup, center, turn);
        return sweptBrush(color, opacity, areaNarrowedToGroup(color, gradientArea, hintGroup),
                sweepOrigin);
    }

    /** A hotspot box moves with its brushes: an across-hint sweep fills each shape it paints. */
    private static QBrush hotspotBrush(GradientColor color, double opacity, Rectangle gradientArea,
                                     HintGroup hintGroup, Point center, int turn,
                                     Point sweepOrigin) {
        return color.area() == GradientArea.ELEMENT ? QtColorUtil.qBrush(color, opacity) :
                brush(color, opacity, gradientArea, hintGroup, center, turn, null, sweepOrigin);
    }

    private GradientColor gradientColor(Color color) {
        return color.gradientColor(lastSelectedHintColors);
    }

    /** Gives the decoration boxes of box, which lands on boxPixelRectangle, their colors, as a
     *  hint box and its label take theirs. */
    private void setDecorationColors(HintBox box, Rectangle boxPixelRectangle,
                                     List<DecorationStyle> decorationStyles,
                                     int depth, HintGroup hintGroup,
                                     int turn, Point sweepOrigin) {
        for (HintBox decorationBox : box.decorationBoxes) {
            DecorationStyle decorationStyle = decorationStyles.get(depth);
            Rectangle pixelRectangle =
                    decorationBox.pixelRectangle(boxPixelRectangle.x(), boxPixelRectangle.y());
            Point center = new Point(sweepOrigin.x() + pixelRectangle.center().x(),
                    sweepOrigin.y() + pixelRectangle.center().y());
            Rectangle screenRectangle = new Rectangle(
                    pixelRectangle.x() + (int) Math.round(sweepOrigin.x()),
                    pixelRectangle.y() + (int) Math.round(sweepOrigin.y()),
                    pixelRectangle.width(), pixelRectangle.height());
            Function<GradientColor, Rectangle> gradientArea = color ->
                    color.area() == GradientArea.ELEMENT ? screenRectangle :
                            decorationStyle.gradientAreaByArea().get(color.area());
            decorationBox.setColor(brush(decorationStyle.boxColor(),
                    decorationStyle.boxOpacity(), gradientArea.apply(decorationStyle.boxColor()),
                    hintGroup, center, turn, pixelRectangle, sweepOrigin));
            decorationBox.setBorderColor(brush(decorationStyle.boxBorderColor(),
                    decorationStyle.boxBorderOpacity(),
                    gradientArea.apply(decorationStyle.boxBorderColor()), hintGroup, center, turn,
                    pixelRectangle, sweepOrigin));
            decorationBox.setDecorationLabelColors(sampledFontStyle(decorationStyle.labelStyle(),
                    decorationStyle.labelFont(), gradientArea, hintGroup, center, turn));
            setDecorationColors(decorationBox, pixelRectangle, decorationStyles,
                    depth + 1, hintGroup, turn, sweepOrigin);
        }
    }

    /** The Qt drawing resources for one decoration. */
    private record DecorationStyle(GradientColor boxColor, double boxOpacity,
                                   GradientColor boxBorderColor, double boxBorderOpacity,
                                   Map<GradientArea, Rectangle> gradientAreaByArea,
                                   int borderThicknessPx, int borderLengthPx,
                                   int borderRadiusPx, int horizontalPaddingPx,
                                   int verticalPaddingPx, boolean boxFramed,
                                   boolean labelVisible,
                                   QtFontStyle labelStyle,
                                   FontStyle labelFont,
                                   List<Key> labelOverride,
                                   FontVerticalAlignment labelVerticalAlignment) {
    }

    private DecorationStyle decorationStyle(Decoration decoration, double screenScale,
                                            HintMeshWindow hintMeshWindow, HintMesh hintMesh) {
        FontStyle font = decoration.fontStyle().defaultFontStyle();
        Map<GradientArea, Rectangle> gradientAreaByArea = new EnumMap<>(GradientArea.class);
        for (GradientArea area : GradientArea.values())
            if (area != GradientArea.ELEMENT)
                gradientAreaByArea.put(area, gradientArea(area, hintMeshWindow, hintMesh));
        return new DecorationStyle(
                decoration.boxColor(), decoration.boxOpacity(),
                gradientColor(decoration.boxBorderColor()), decoration.boxBorderOpacity(),
                gradientAreaByArea,
                scaledLength(decoration.boxBorderThickness(), screenScale),
                scaledLength(decoration.boxBorderLength(), screenScale),
                scaledLength(decoration.boxBorderRadius(), screenScale),
                scaledLength(decoration.boxHorizontalPadding(), screenScale),
                scaledLength(decoration.boxVerticalPadding(), screenScale),
                decoration.boxFramed(),
                font.opacity() != 0,
                QtHintFont.qtFontStyle(font, screenScale, lastSelectedHintColors),
                font,
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
                    decorationStyle.borderThicknessPx(), QtColorUtil.qBrush(0),
                    null, true,
                    decorationBoxLeft == 0, decorationBoxTop == 0,
                    decorationBoxRight == parentWidth, decorationBoxBottom == parentHeight,
                    decorationStyle.boxFramed(), qtScaleFactor, decorationStyle.borderRadiusPx());
            int horizontalPadding =
                    (int) Math.round(decorationStyle.horizontalPaddingPx() / qtScaleFactor);
            int verticalPadding =
                    (int) Math.round(decorationStyle.verticalPaddingPx() / qtScaleFactor);
            int decorationBoxWidth =
                    decorationBoxRight - decorationBoxLeft + 2 * horizontalPadding;
            int decorationBoxHeight =
                    decorationBoxBottom - decorationBoxTop + 2 * verticalPadding;
            decorationBox.setGeometry(decorationBoxLeft - horizontalPadding,
                    decorationBoxTop - verticalPadding, decorationBoxWidth, decorationBoxHeight);
            decorationBox.setDecorationLabel(labelKeys.stream()
                            .map(Key::hintLabel).collect(Collectors.joining()),
                    decorationStyle.labelStyle(),
                    decorationStyle.labelVerticalAlignment());
            parentBox.decorationBoxes.add(decorationBox);
            addDecorationBoxes(decorationBox, decorationBoxWidth, decorationBoxHeight,
                    decorationMesh.subDecoration(), decorationStyles, depth + 1,
                    qtScaleFactor);
        }
    }

    /**
     * The QImage DPI that matches the target screen so that point-size fonts
     * render at the correct pixel size. Without this, text painted into off-screen
     * QImages uses the primary screen's DPI, causing wrong-sized glyphs on
     * secondary screens with different scaling.
     */
    static int dotsPerMeter(double screenScale) {
        double dpi = QtHintFont.gdiFontEngine ? screenScale * 96.0 :
                QApplication.primaryScreen().logicalDotsPerInchX();
        return (int) Math.round(dpi / 0.0254);
    }

    /** Paints the container's background and layers into its pixmaps, as Qt paints the widgets,
     *  then drops the layers. */
    private void paintPixmaps(ClearBackgroundQLabel container, boolean isHintGrid,
                              QBrush backgroundColor,
                              Rectangle backgroundRectangle, int backgroundRadius,
                              QBrush backgroundBorderColor, int backgroundBorderThickness,
                              double screenScale) {
        List<HintPaintLayer> layers = new ArrayList<>();
        Map<HintPaintLayer, Rectangle> rectangleByLayer = new HashMap<>();
        for (QObject child : container.children())
            if (child instanceof HintPaintLayer layer) {
                layers.add(layer);
                rectangleByLayer.put(layer,
                        new Rectangle(layer.x(), layer.y(), layer.width(), layer.height()));
            }
        List<HintPaintLayer> belowHotspotLayers = container.belowHotspotLayers;
        List<HintPaintLayer> aboveHotspotLayers =
                layers.stream().filter(layer -> !belowHotspotLayers.contains(layer)).toList();
        int containerX = container.x(), containerY = container.y();
        QRect background = backgroundRectangle == null ? null :
                new QRect(backgroundRectangle.x(), backgroundRectangle.y(),
                        backgroundRectangle.width(), backgroundRectangle.height());
        BiConsumer<QPainter, Rectangle> paintBelowHotspots = (painter, chunk) -> {
            // The background is in the coordinates of the window.
            if (background != null) {
                painter.save();
                painter.translate(-containerX, -containerY);
                TransparentWindow.paintBackground(painter, backgroundColor, background,
                        backgroundRadius, backgroundBorderColor, backgroundBorderThickness);
                painter.restore();
            }
            paintLayers(painter, chunk, belowHotspotLayers, rectangleByLayer);
        };
        Rectangle area = new Rectangle(0, 0, container.width(), container.height());
        double devicePixelRatio = container.devicePixelRatio();
        int dotsPerMeter = dotsPerMeter(screenScale);
        List<QImage> images;
        if (container.inHotspotWindow) {
            container.belowHotspotArea = background == null ? area : Rectangle.union(List.of(area,
                    new Rectangle(backgroundRectangle.x() - containerX,
                            backgroundRectangle.y() - containerY, backgroundRectangle.width(),
                            backgroundRectangle.height())));
            if (background != null || !belowHotspotLayers.isEmpty())
                container.belowHotspotImages = ImageChunks.paintChunks(
                        container.belowHotspotArea, devicePixelRatio, dotsPerMeter,
                        paintBelowHotspots);
            images = ImageChunks.paintChunks(area, devicePixelRatio, dotsPerMeter,
                    (painter, chunk) -> paintLayers(painter, chunk, aboveHotspotLayers,
                            rectangleByLayer));
            // The crop and border animations of a grid run in the hint mesh window.
            if (!isHintGrid) {
                container.aboveHotspotImages = images;
                images = List.of();
            }
        }
        else
            images = ImageChunks.paintChunks(area, devicePixelRatio, dotsPerMeter,
                    (painter, chunk) -> {
                        paintBelowHotspots.accept(painter, chunk);
                        container.paintHotspotBoxes(painter, chunk);
                        paintLayers(painter, chunk, aboveHotspotLayers, rectangleByLayer);
                    });
        List<QPixmap> pixelRowChunks = new ArrayList<>(images.size());
        for (QImage image : images) {
            pixelRowChunks.add(QPixmap.fromImage(image));
            image.dispose();
        }
        container.setPixelRowChunks(pixelRowChunks);
        if (background != null)
            background.dispose();
        container.belowHotspotLayers = List.of();
        for (HintPaintLayer layer : layers) {
            layer.setParent(null);
            disposeWidget(layer);
        }
    }

    private static void paintLayers(QPainter painter, Rectangle chunk,
                                    List<HintPaintLayer> layers,
                                    Map<HintPaintLayer, Rectangle> rectangleByLayer) {
        for (HintPaintLayer layer : layers) {
            Rectangle layerRectangle = rectangleByLayer.get(layer);
            Rectangle dirty = layerRectangle.intersection(chunk);
            if (dirty.isEmpty())
                continue;
            Rectangle layerDirty = new Rectangle(dirty.x() - layerRectangle.x(),
                    dirty.y() - layerRectangle.y(), dirty.width(), dirty.height());
            painter.save();
            painter.translate(layerRectangle.x(), layerRectangle.y());
            painter.setClipRect(layerDirty.x(), layerDirty.y(), layerDirty.width(),
                    layerDirty.height());
            layer.paint(painter, layerDirty);
            painter.restore();
        }
    }

    private void cachePixmaps(TransparentWindow window, ClearBackgroundQLabel container,
                              HintMesh hintMeshKey, HintMesh hintMesh, List<HintBox> boxes,
                              List<Rectangle> enclosingInk) {
        // Copies: the container disposes its own.
        PixmapsAndPosition pixmapsAndPosition = new PixmapsAndPosition(
                container.pixelRowChunks.stream().map(QPixmap::new).toList(), container.x(),
                container.y(), boxes, enclosingInk, hintMesh, window.x(), window.y());
        hintMeshPixmaps.put(pixmapKey(hintMeshKey), pixmapsAndPosition);
        if (!preWarming)
            logger.debug("Cached hint mesh pixmaps " + pixmapsAndPosition +
                         ", cache size is " + hintMeshPixmaps.size());
    }

    /** The box rectangles of a mesh are needed while a window shows it, and while its image is
     *  cached to show it again. */
    private void forgetHintBoxRectangles(HintMesh hintMeshKey) {
        if (hintMeshKey == null ||
            hintMeshWindows.values().stream().anyMatch(hintMeshWindow ->
                    hintMeshKey.equals(hintMeshWindow.lastHintMeshKeyReference().get())) ||
            hintMeshPixmaps.keySet().stream().anyMatch(pixmapKey ->
                    hintMeshKey.equals(pixmapKey.hintMeshKey())))
            return;
        hintBoxRectanglesByHintMeshKey.remove(hintMeshKey);
    }

    /** The key a drawn mesh is cached by: the mesh with its visible hints at the boxes they are
     *  drawn in, moved so that the smallest hint center x and y are 0 when it looks the same
     *  anywhere. A mesh that only moved has the same key. */
    /** The colors of the hint selected last change with each selection, so a mesh drawn in them
     *  is cached with them. */
    private record PixmapKey(HintMesh hintMeshKey, LastSelectedHintColors lastSelectedHintColors) {
    }

    private PixmapKey pixmapKey(HintMesh hintMeshKey) {
        boolean lastSelected = hintMeshKey.styleByFilter().map().values().stream().anyMatch(
                style -> Stream.concat(colors(style),
                                       Stream.of(style.backgroundColor(),
                                               style.backgroundBorderColor()))
                               .anyMatch(color -> !(color instanceof GradientColor)));
        return new PixmapKey(hintMeshKey, lastSelected ? lastSelectedHintColors : null);
    }

    private HintMesh hintMeshKey(HintMesh hintMesh, List<Hint> hints, HintMeshStyle style,
                                 double screenScale) {
        HintMesh visibleHintMesh = hintMesh.builder()
                .hints(hints.stream().filter(hint -> visible(hint, hintMesh)).toList())
                .hotspotByKeySequence(hotspotBoxVisible(style) || hotspotBoxVisible(style.saved()) ?
                        hintMesh.hotspotByKeySequence() : Map.of())
                .build();
        if (!looksTheSameAnywhere(hintMesh))
            return visibleHintMesh;
        double minHintCenterX = Double.MAX_VALUE;
        double minHintCenterY = Double.MAX_VALUE;
        for (Hint hint : hints) {
            if (!hint.startsWith(hintMesh.selectedKeySequence()))
                continue;
            Hint roundedHint = roundedHint(hint, style, screenScale);
            minHintCenterX = Math.min(minHintCenterX, roundedHint.centerX());
            minHintCenterY = Math.min(minHintCenterY, roundedHint.centerY());
        }
        return trimmedHintMesh(visibleHintMesh.builder().backgroundArea(null).build(), style,
                screenScale, minHintCenterX, minHintCenterY);
    }

    private static boolean hotspotBoxVisible(HintMeshStyle style) {
        return style.hotspotBoxOpacity() != 0 || style.hotspotBoxBorderOpacity() != 0 ||
               style.hotspotLineOpacity() != 0;
    }

    private HintMesh trimmedHintMesh(HintMesh hintMesh, HintMeshStyle style, double screenScale,
                                     double minHintCenterX, double minHintCenterY) {
        if (hintMesh == null)
            return null;
        return hintMesh.builder()
                       .hints(trimmedHints(hintMesh.hints(), style, screenScale, minHintCenterX,
                               minHintCenterY))
                       .savedHints(trimmedHints(hintMesh.savedHints(), style, screenScale,
                               minHintCenterX, minHintCenterY))
                       .area(trimmedRectangle(hintMesh.area(), minHintCenterX, minHintCenterY))
                       .backgroundArea(trimmedRectangle(hintMesh.backgroundArea(),
                               minHintCenterX, minHintCenterY))
                       .decoration(trimmedHintMesh(hintMesh.decoration(), style, screenScale,
                               minHintCenterX, minHintCenterY))
                       .subDecoration(trimmedHintMesh(hintMesh.subDecoration(), style,
                               screenScale, minHintCenterX, minHintCenterY))
                       .build();
    }

    private List<Hint> trimmedHints(List<Hint> hints, HintMeshStyle style, double screenScale,
                                    double minHintCenterX, double minHintCenterY) {
        return hints.stream()
                    .map(hint -> roundedHint(hint, style, screenScale))
                    .map(hint -> new Hint(hint.centerX() - minHintCenterX,
                            hint.centerY() - minHintCenterY, hint.cellWidth(),
                            hint.cellHeight(), hint.keySequence(),
                            trimmedRectangle(hint.elementRectangle(), minHintCenterX,
                                    minHintCenterY)))
                    .toList();
    }

    private static Rectangle trimmedRectangle(Rectangle rectangle, double minHintCenterX,
                                              double minHintCenterY) {
        if (rectangle == null)
            return null;
        return new Rectangle((int) Math.round(rectangle.x() - minHintCenterX),
                (int) Math.round(rectangle.y() - minHintCenterY), rectangle.width(),
                rectangle.height());
    }

    /** A grid hint at the box it is drawn in, whose edges are whole points. */
    private Hint roundedHint(Hint hint, HintMeshStyle style, double screenScale) {
        if (hint.cellWidth() == -1)
            return hint;
        double qtScaleFactor = qtScaleFactor(screenScale);
        double cellWidth =
                hint.cellWidth() + 2 * scaledLength(style.boxHorizontalPadding(), screenScale);
        double cellHeight =
                hint.cellHeight() + 2 * scaledLength(style.boxVerticalPadding(), screenScale);
        int left = hintRoundedX(hint.centerX(), cellWidth, qtScaleFactor);
        int top = hintRoundedY(hint.centerY(), cellHeight, qtScaleFactor);
        int right = hintRoundedRight(hint.centerX(), cellWidth, qtScaleFactor);
        int bottom = hintRoundedBottom(hint.centerY(), cellHeight, qtScaleFactor);
        return new Hint((left + right) / 2d * qtScaleFactor, (top + bottom) / 2d * qtScaleFactor,
                (right - left) * qtScaleFactor, (bottom - top) * qtScaleFactor,
                hint.keySequence(), hint.elementRectangle());
    }

    private boolean looksTheSameAnywhere(HintMesh hintMesh) {
        return hintMesh.styleByFilter().map().values().stream().allMatch(style ->
                style.backgroundOpacity() == 0 && style.backgroundBorderOpacity() == 0 &&
                colors(style).noneMatch(this::acrossTheScreen));
    }

    private static Stream<Color> colors(HintMeshStyle style) {
        return Stream.of(Stream.of(style.boxColor(), style.boxBorderColor(),
                                 style.boxShadow().color(), style.prefixBoxBorderColor(),
                                 style.uiElementBoxColor(), style.uiElementBoxBorderColor(),
                                 style.uiElementBoxShadow().color()),
                         Stream.of(style.savedBoxColor(), style.savedBoxBorderColor(),
                                   style.savedUiElementBoxColor(),
                                   style.savedUiElementBoxBorderColor())
                               .filter(Objects::nonNull),
                         colors(style.fontStyle()), colors(style.prefixFontStyle()),
                         style.decorations().stream().flatMap(decoration -> Stream.concat(
                                 Stream.of(decoration.boxColor(), decoration.boxBorderColor()),
                                 colors(decoration.fontStyle()))))
                     .flatMap(Function.identity());
    }

    private static Stream<Color> colors(HintFontStyle fontStyle) {
        return Stream.of(fontStyle.defaultFontStyle(), fontStyle.selectedFontStyle(),
                         fontStyle.focusedFontStyle(), fontStyle.savedFontStyle())
                     .flatMap(font -> Stream.of(font.color(), font.outlineColor(),
                             font.shadow().color()));
    }

    private boolean acrossTheScreen(Color color) {
        GradientColor gradientColor = gradientColor(color);
        return gradientColor.gradient() && (gradientColor.area() == GradientArea.SCREEN ||
                                            gradientColor.area() == GradientArea.ALL_SCREENS);
    }

    /** boxes is the live box layer's boxes for a morphing grid, redrawn on a cache hit; null otherwise.
     *  enclosingInk is where the borders enclosing them put ink, in container coordinates. */
    private record PixmapsAndPosition(List<QPixmap> pixelRowChunks, int x, int y,
                                      List<HintBox> boxes,
                                      List<Rectangle> enclosingInk,
                                      HintMesh originalHintMesh, int windowX, int windowY) {
        int width() {
            return pixelRowChunks.getFirst().width();
        }

        int height() {
            return pixelRowChunks.stream().mapToInt(QPixmap::height).sum();
        }

        @Override
        public String toString() {
            return width() + "x" + height() + " at (" + x + ", " + y + ")";
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
        return new Shadow(shadow.blurRadius() * screenScale, shadow.color(),
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
        private QBrush borderColor;
        private QBrush shadowColor;
        private final int borderRadius;
        private int x, y, width, height;
        private Rectangle lineFrom;
        private QPen linePen;
        private int lineThickness;
        /** Set while drawBorders records where it would paint instead of painting. */
        private List<Rectangle> ink;
        final List<HintBox> decorationBoxes = new ArrayList<>();
        private String decorationLabel;
        private QFont decorationLabelFont;
        private QColor decorationLabelColor;
        private QColor decorationLabelShadowColor;
        private int decorationLabelX;
        private double decorationLabelY;
        private int decorationLabelWidth, decorationLabelAscent, decorationLabelDescent;

        public HintBox(Hint hint, int borderLength, int borderThickness, QBrush color, QBrush borderColor,
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

        void setBorderColor(QBrush borderColor) {
            this.borderColor = borderColor;
        }

        void setShadowColor(QBrush shadowColor) {
            this.shadowColor = shadowColor;
        }

        void setLine(Rectangle lineFrom, QBrush lineColor, int lineThickness) {
            this.lineFrom = lineFrom;
            linePen = createPen(lineColor, lineThickness);
            this.lineThickness = lineThickness;
        }

        /** On the edge of the rectangle the line comes from, null when the box is in it. */
        private Point lineStart() {
            if (linePen == null)
                return null;
            Point fromCenter = lineFrom.center();
            Point center = rectangle().center();
            double exit = lineFrom.exit(fromCenter, center);
            return exit >= 1 ? null : new Point(
                    Math.round(fromCenter.x() + exit * (center.x() - fromCenter.x())) - x,
                    Math.round(fromCenter.y() + exit * (center.y() - fromCenter.y())) - y);
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

        /** Where painting the box can put ink: its border reaches past its edges, and the labels
         *  of its decorations past theirs. */
        Rectangle ink() {
            List<Rectangle> ink = new ArrayList<>();
            collectInk(0, 0, ink);
            return Rectangle.union(ink);
        }

        private void collectInk(int offsetX, int offsetY, List<Rectangle> ink) {
            int padding = (int) Math.ceil(borderThickness / qtScaleFactor);
            ink.add(new Rectangle(offsetX + x - padding, offsetY + y - padding,
                    width + 2 * padding, height + 2 * padding));
            collectDecorationLabelBounds(0, offsetX, offsetY, ink);
            for (HintBox decorationBox : decorationBoxes)
                decorationBox.collectInk(offsetX + x, offsetY + y, ink);
        }

        /** Where painting the line can put ink, null without a line. */
        Rectangle lineInk() {
            Point lineStart = lineStart();
            if (lineStart == null)
                return null;
            int lineStartX = (int) lineStart.x(), lineStartY = (int) lineStart.y();
            int linePadding = (int) Math.ceil(lineThickness / qtScaleFactor);
            return new Rectangle(x + Math.min(lineStartX, width / 2) - linePadding,
                    y + Math.min(lineStartY, height / 2) - linePadding,
                    Math.abs(lineStartX - width / 2) + 2 * linePadding,
                    Math.abs(lineStartY - height / 2) + 2 * linePadding);
        }

        /** Where this depth's decoration labels put ink, in the coordinates they are painted in.
         *  Descends accumulating the offset paintDecorationLabels translates by. */
        void collectDecorationLabelBounds(int depth, int offsetX, int offsetY,
                                          List<Rectangle> bounds) {
            if (depth == 0) {
                if (decorationLabelDrawn())
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

        Rectangle pixelRectangle(int parentLeft, int parentTop) {
            return new Rectangle(parentLeft + pixels(x), parentTop + pixels(y), pixels(width),
                    pixels(height));
        }

        CasterKey casterKey() {
            return new CasterKey(pixelRectangle(0, 0), borderRadius, borderThickness, shadowColor);
        }

        public void paint(QPainter painter) {
            painter.save();
            scaleToPixels(painter);
            paintInPixels(painter, 0, 0);
            painter.restore();
        }

        /** Paints the part of the line in area, which a long line crosses a small part of. The
         *  line is antialiased, which the painter is set to once for every line. */
        void paintLine(QPainter painter, Rectangle area) {
            Point lineStart = lineStart();
            if (lineStart == null)
                return;
            Point start = new Point(pixels(x) + pixels((int) lineStart.x()),
                    pixels(y) + pixels((int) lineStart.y()));
            Point end = new Point(pixels(x) + pixels(width) / 2, pixels(y) + pixels(height) / 2);
            int margin = lineThickness + 1;
            Rectangle clip = new Rectangle(pixels(area.x()) - margin, pixels(area.y()) - margin,
                    pixels(area.width()) + 2 * margin, pixels(area.height()) + 2 * margin);
            double enter = Math.max(0, 1 - clip.exit(end, start));
            double exit = Math.min(1, clip.exit(start, end));
            if (enter > exit)
                return;
            painter.save();
            scaleToPixels(painter);
            painter.setPen(linePen);
            painter.drawLine(paintedPoint(start.x() + enter * (end.x() - start.x()),
                            start.y() + enter * (end.y() - start.y())),
                    paintedLineEnd(start.x() + exit * (end.x() - start.x()),
                            start.y() + exit * (end.y() - start.y())));
            painter.restore();
        }

        /** The box is drawn where it lands, so one gradient brush can span every box. */
        private void paintInPixels(QPainter painter, int parentLeft, int parentTop) {
            painter.save();
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
            int left = parentLeft + pixels(x);
            int top = parentTop + pixels(y);
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
                    painter.drawRoundedRect(paintedRectangle(left + offset, top + offset,
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
            if (borderRadius == 0 && borderThickness != 0)
                drawBorders(painter, left, top);
            for (HintBox decorationBox : decorationBoxes) {
                decorationBox.paintInPixels(painter, left, top);
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

        void setDecorationLabelColors(QtFontStyle labelStyle) {
            decorationLabelColor = labelStyle.color();
            decorationLabelShadowColor = labelStyle.shadowColor();
        }

        /** Draws the decoration labels nested {@code depth} levels down. They are painted on their own
         *  layer, one per depth, so a depth's shadow applies to the layer as hint label shadows do.
         *  forceOpaque paints them in their opaque shadow colors, as the shape of their shadow. */
        void paintDecorationLabels(QPainter painter, int depth, boolean forceOpaque) {
            painter.save();
            painter.translate(x, y);
            if (depth == 0) {
                if (decorationLabelDrawn()) {
                    painter.setFont(decorationLabelFont);
                    painter.setPen(forceOpaque ? QtColorUtil.opaque(decorationLabelShadowColor) :
                            decorationLabelColor);
                    painter.drawText(paintedPoint(decorationLabelX, decorationLabelY), decorationLabel);
                }
            }
            else
                for (HintBox decorationBox : decorationBoxes)
                    decorationBox.paintDecorationLabels(painter, depth - 1, forceOpaque);
            painter.restore();
        }

        private boolean decorationLabelDrawn() {
            return decorationLabel != null && !decorationLabel.isEmpty() &&
                   decorationLabelFont != null;
        }

        void collectDecorationLabelShadowRgbs(int depth, Set<Integer> rgbs) {
            if (depth == 0) {
                if (decorationLabelDrawn())
                    rgbs.add(decorationLabelShadowColor.rgb());
                return;
            }
            for (HintBox decorationBox : decorationBoxes)
                decorationBox.collectDecorationLabelShadowRgbs(depth - 1, rgbs);
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
                    painter.drawRoundedRect(paintedRectangle(left + offset, top + offset,
                                    pixels(width) - borderThickness,
                                    pixels(height) - borderThickness),
                            borderRadius, borderRadius);
                }
                else
                    painter.drawRoundedRect(left, top, pixels(width), pixels(height),
                            borderRadius, borderRadius);
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, false);
            }
            for (HintBox decorationBox : decorationBoxes)
                decorationBox.paintInPixels(painter, left, top);
            painter.restore();
        }

        /** Where this box's border, and its decorations' in turn, put ink, in the coordinates its
         *  parent paints it in. */
        void collectBorderInk(int offsetX, int offsetY, List<Rectangle> ink) {
            if (borderThickness != 0 && borderColor.color().alpha() != 0) {
                this.ink = new ArrayList<>();
                drawBorders(null, 0, 0);
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
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
            if (borderRadius > 0) {
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
                painter.setBrush(QtColorUtil.noBrush());
                painter.setPen(createPen(borderColor, borderThickness));
                // Half the pen, not half of it rounded down: a border one pixel thick strokes
                // half a pixel each side of the rect, and the box would keep only the inner half.
                double offset = borderThickness / 2d;
                painter.drawRoundedRect(paintedRectangle(pixels(x) + offset, pixels(y) + offset,
                        pixels(width) - borderThickness, pixels(height) - borderThickness),
                        borderRadius, borderRadius);
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, false);
            }
            else {
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, false);
                drawBorders(painter, pixels(x), pixels(y));
            }
            painter.restore();
        }

        /**
         * Paints the box shape in its opaque shadow color, the source image for shadow rendering.
         * That image is in pixels, so this paints without the scale a widget needs.
         */
        public void paintOpaque(QPainter painter) {
            painter.save();
            painter.setBrush(shadowColor);
            painter.setPen(Qt.PenStyle.NoPen);
            if (borderRadius > 0) {
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
                painter.drawRoundedRect(pixels(x), pixels(y), pixels(width), pixels(height),
                        outerRadius(), outerRadius());
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, false);
            }
            else {
                painter.drawRect(pixels(x), pixels(y), pixels(width), pixels(height));
            }
            painter.restore();
        }

        /** The radius of the outer edge of a rounded box: its border strokes the box inset by half
         *  its thickness, which rounds the outer edge by that much more. */
        private double outerRadius() {
            return borderRadius + borderThickness / 2d;
        }

        private void drawBorders(QPainter painter, int left, int top) {
            // Draw borders.
            // With QT_ENABLE_HIGHDPI_SCALING=0:
            // draw vertical line penwidth 1 at x=0: x=0 (0 is the widget's left)
            // draw vertical line penwidth 2 at x=0: x=0, x=-1
            // draw vertical line penwidth 3 at x=0: x=0, x=1, x=-1
            // draw vertical line penwidth 4 at x=0: x=0, x=1, x=-1, x=-2
            // draw vertical line penwidth 5 at x=0: x=0, x=1, x=2, x=-1, x=-2
            // Qt won't draw anything x < 0, but will draw x >= width().
            int bottom = top + pixels(height) - 1;
            int right = left + pixels(width) - 1;
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

        private QPen createPen(QBrush brush, int penWidth) {
            // Default is square cap.
            return QtColorUtil.qPen(brush, penWidth, Qt.PenCapStyle.FlatCap,
                    Qt.PenJoinStyle.BevelJoin);
        }

    }

    private static QPointF paintedPoint(double x, double y) {
        QPointF point = paintedPoint.get();
        point.setX(x);
        point.setY(y);
        return point;
    }

    private static QRectF paintedRectangle(double x, double y, double width, double height) {
        QRectF rectangle = paintedRectangle.get();
        rectangle.setRect(x, y, width, height);
        return rectangle;
    }

    private static QPointF paintedLineEnd(double x, double y) {
        QPointF point = paintedLineEnd.get();
        point.setX(x);
        point.setY(y);
        return point;
    }

    static double baselineY(FontVerticalAlignment verticalAlignment, int boxHeight,
                            QFontMetrics metrics, double inkTop, double inkHeight,
                            double qtScaleFactor) {
        return switch (verticalAlignment) {
            // The ink starts on a whole pixel, a leftover one going above the text, not below.
            // A pixel, not a point: where Qt paints in points, half of one is still a pixel, and
            // rounding to the point would push the text off center by one.
            case MIDDLE -> topGap(boxHeight - inkHeight, qtScaleFactor) - inkTop;
            // A glyph lands on the pixel nearest its baseline, so the baseline is what is rounded.
            case CAP_HEIGHT -> Math.round((boxHeight + QtHintFont.capHeight(metrics,
                    qtScaleFactor)) / 2 * qtScaleFactor) / qtScaleFactor;
            case BASELINE -> (boxHeight + metrics.ascent() - metrics.descent()) / 2d;
        };
    }

    private static double topGap(double slack, double qtScaleFactor) {
        return Math.round(slack / 2 * qtScaleFactor) / qtScaleFactor;
    }

    public static class HintLabel {

        /** Refilled by every outline rendered. */
        private static final QPainterPath outlinePath = new QPainterPath();
        private static final Map<OutlineKey, OutlineImage> outlineImages = new HashMap<>();
        private static final Lock outlineLock = new ReentrantLock();

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
            return new HintLabel(built);
        }

        /** A glyph and where it sits in the label it belongs to. */
        private record GlyphPlacement(String text, double x, double y) {
        }

        /** Everything the rasterized outline of a label depends on. */
        private record OutlineKey(List<GlyphPlacement> glyphs, int rgba, int thickness) {
        }

        private record OutlineImage(QImage image, int x, int y) {
        }

        private QtHintFontStyle labelFontStyle;
        private final List<HintKeyText> keyTexts;
        /** Only a non-grid hint's box is sized and placed to fit its label; a grid hint's box is its
         *  cell, so its left is left at 0. */
        final int tightHintBoxLeft;
        final int tightHintBoxWidth;
        final int tightHintBoxHeight;
        /** Where the key texts put ink, from the top left of the label. */
        private final Rectangle keyTextsInk;
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
            // No columns to align to: a spacing above 0.5, which spreads the columns over the
            // cell, acts as 0.5.
            double fontSpacingPercent = isHintPartOfTiledGrid ?
                    labelFontStyle.fontSpacingPercent() :
                    Math.min(0.5d, labelFontStyle.fontSpacingPercent());
            // We want font spacing percent 0.5 be the min spacing that keeps column alignment.
            double adjustedFontBoxWidthPercent = fontSpacingPercent < 0.5d ?
                    (fontSpacingPercent * 2) * smallestColAlignedFontBoxWidthPercent
                    : smallestColAlignedFontBoxWidthPercent + (fontSpacingPercent - 0.5d) * 2 * (1 - smallestColAlignedFontBoxWidthPercent) ;
            boolean doNotColAlign = keySequence.size() != 1 &&
                                    adjustedFontBoxWidthPercent < smallestColAlignedFontBoxWidthPercent;
            double extraNotAlignedWidth = smallestColAlignedFontBoxWidth -
                                          totalXAdvance;
            extraNotAlignedWidth = adjustedFontBoxWidthPercent * extraNotAlignedWidth;
            double fontBoxWidth = boxWidth * adjustedFontBoxWidthPercent;

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
            int smallestHintBoxLeft = 0;
            int smallestHintBoxWidth = 0;
            String labelText = "";
            for (int keyIndex = 0; keyIndex < keySequence.size(); keyIndex++) {
                Key key = keySequence.get(keyIndex);
                String keyText = key.hintLabel();
                int textWidth = xAdvancesByString.get(keyText);
                int x;
                int keyWidth;
                if (doNotColAlign) {
                    labelText += keyText;
                    // The advance of the label's text up to this key has the kerning. Extra is
                    // added between each letter (not to the left of the leftmost letter, nor to
                    // the right of the rightmost letter).
                    int xAdvance = xAdvancesByString.computeIfAbsent(labelText,
                            labelMetrics::horizontalAdvance) - textWidth + keyIndex *
                            (int) (extraNotAlignedWidth / (keySequence.size() - 1));
                    x = (int) (boxWidth / 2d - (totalXAdvance + extraNotAlignedWidth) / 2
                                                   + xAdvance
                    );
                    if (keyIndex == 0) {
                        smallestHintBoxLeft = x;
                    }
                    if (keyIndex == keySequence.size() - 1) {
                        smallestHintBoxWidth = x - smallestHintBoxLeft + textWidth;
                    }
                    keyWidth = textWidth;
                }
                else {
                    // 0.8d adjustedFontBoxWidthPercent means characters spread over 80% of the cell width.
                    // If we are here, hint.keySequence().size() is 2 or more (else, doNotColAlign would be true).
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
                keyTexts.add(new HintKeyText(keyText, textX, textY, keyWidth,
                        isSelected, isFocused, isPrefix));
            }
            // No columns to align to: center the keys' advances instead of per-key slots.
            if (!isHintPartOfTiledGrid) {
                double glyphPositionsPerUnit = qtScaleFactor *
                        QtHintFont.glyphPositionsPerPixel(labelMetrics, qtScaleFactor);
                double shiftX = Math.round(
                        ((boxWidth - smallestHintBoxWidth) / 2d - smallestHintBoxLeft)
                        * glyphPositionsPerUnit) / glyphPositionsPerUnit;
                if (shiftX != 0) {
                    keyTexts.replaceAll(k -> new HintKeyText(k.text(), k.x() + shiftX, k.y(),
                            k.width(), k.isSelected(), k.isFocused(), k.isPrefix()));
                    smallestHintBoxLeft += shiftX;
                }
            }
            if (!doNotColAlign && !keySequence.isEmpty()) {
                // The box holds the columns, not only the keys.
                smallestHintBoxWidth =
                        Math.max(smallestHintBoxWidth, (int) Math.ceil(fontBoxWidth));
                smallestHintBoxLeft = (boxWidth - smallestHintBoxWidth) / 2;
            }
            this.tightHintBoxLeft = smallestHintBoxLeft;
            this.tightHintBoxWidth = smallestHintBoxWidth;
            this.tightHintBoxHeight = keySequence.isEmpty() ? 0 : labelMetrics.height();
            this.keyTextsInk = keyTextsInk();
        }

        /** Reuses a cached label's layout; only the screen position, set by the caller, differs. */
        private HintLabel(HintLabel template) {
            this.labelFontStyle = template.labelFontStyle;
            this.keyTexts = template.keyTexts;
            this.tightHintBoxLeft = template.tightHintBoxLeft;
            this.tightHintBoxWidth = template.tightHintBoxWidth;
            this.tightHintBoxHeight = template.tightHintBoxHeight;
            this.keyTextsInk = template.keyTextsInk;
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
            return new Rectangle(x - left + keyTextsInk.x(), y - top + keyTextsInk.y(),
                    keyTextsInk.width(), keyTextsInk.height());
        }

        private Rectangle keyTextsInk() {
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
            return new Rectangle(inkX, inkY, (int) Math.ceil(inkRight) - inkX,
                    (int) Math.ceil(inkBottom) - inkY);
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
                painter.setPen(forceOpaque ? opaqueColor(qtFontStyle.shadowColor()) : color);
                painter.drawText(paintedPoint(keyText.x() - left, keyText.y() - top),
                        keyText.text());
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
                    opaqueColor(qtFontStyle.shadowColor()) : qtFontStyle.outlineColor();
            List<GlyphPlacement> glyphs = new ArrayList<>(keyTexts.size());
            for (HintKeyText keyText : keyTexts)
                if (filter.test(keyText))
                    glyphs.add(new GlyphPlacement(keyText.text(), keyText.x() - left,
                            keyText.y() - top));
            // Building and stroking a glyph outline costs ~85us, and a hint mesh draws the same
            // handful of labels over and over, so each is rasterized once and blitted after.
            OutlineImage outline;
            outlineLock.lock();
            try {
                outline = outlineImages.computeIfAbsent(
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
            }
            finally {
                outlineLock.unlock();
            }
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
            return new ShadowGroupKey(qtFontStyle.shadowColor().alpha(),
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
                painter.setPen(opaqueColor(qtFontStyle.shadowColor()));
                painter.drawText(paintedPoint(keyText.x() - left, keyText.y() - top),
                        keyText.text());
            }
            painter.restore();
        }
    }

    private record ShadowPixmap(QPixmap pixmap, int x, int y, int stackCount) {
    }

    private record ShadowKey(Rectangle area, List<CasterKey> casters, Shadow shadow, int rgba,
                             boolean alphaOnly, int dotsPerMeter, double qtScaleFactor) {
    }

    private record CasterKey(Rectangle ink, int borderRadius, int borderThickness,
                             QBrush shadowColor) {
    }

    /** Shadows that differ only in color blur together, each in its own color. */
    private record ShadowGroupKey(int alpha,
                                  int stackCount, double blurRadius,
                                  double horizontalOffset, double verticalOffset) {
    }

    private record HintKeyText(String text, double x, double y, int width, boolean isSelected,
                               boolean isFocused,
                               boolean isPrefix) {

    }

    /** Pre-renders the box shadow off-screen into the shadow layer, which crops with the content. */
    private void applyBoxShadow(HintPaintLayer boxShadowLayer,
                                       List<HintBox> boxes,
                                       Shadow boxShadow,
                                       int containerWidth,
                                       int containerHeight,
                                       double screenScale,
                                       double qtScaleFactor) {
        QColor shadowColor = QtColorUtil.shadow(boxShadow, lastSelectedHintColors);
        if (shadowColor.alpha() == 0 || boxes.isEmpty()) {
            shadowColor.dispose();
            return;
        }
        // The source is in pixels, as the blur radius and the offsets are: a box's shadow is as
        // many pixels as it is configured to be, whatever the screen.
        Rectangle container = new Rectangle(0, 0, (int) Math.round(containerWidth * qtScaleFactor),
                (int) Math.round(containerHeight * qtScaleFactor));
        boolean alphaOnly = !gradientColor(boxShadow.color()).gradient();
        int padding = shadowPadding(boxShadow.blurRadius(), boxShadow.horizontalOffset(),
                boxShadow.verticalOffset());
        int dotsPerMeter = dotsPerMeter(screenScale);
        for (Map.Entry<Rectangle, Map<HintBox, Rectangle>> entry :
                byShadowArea(boxes, box -> box.pixelRectangle(0, 0), padding,
                        container).entrySet()) {
            Rectangle area = entry.getKey();
            ShadowKey shadowKey = new ShadowKey(area,
                    entry.getValue().keySet().stream().map(HintBox::casterKey).toList(),
                    boxShadow, shadowColor.rgba(), alphaOnly, dotsPerMeter, qtScaleFactor);
            List<QPixmap> shadowPixmaps = boxShadowPixmaps.computeIfAbsent(shadowKey,
                    key -> shadowPixmaps(Shadows.renderShadow(area, dotsPerMeter,
                            (painter, chunk) -> paintCasters(painter, chunk, entry.getValue(),
                                    padding, HintBox::paintOpaque),
                            shadowColor, boxShadow.blurRadius(), boxShadow.horizontalOffset(),
                            boxShadow.verticalOffset(), alphaOnly, true), qtScaleFactor));
            boxShadowLayer.addShadowPixmaps(shadowPixmaps.stream().map(QPixmap::new).toList(),
                    area.x(), area.y(), boxShadow.stackCount());
        }
        shadowColor.dispose();
    }

    /** Consumes the chunks. */
    private static List<QPixmap> shadowPixmaps(List<QImage> shadowImages, double scaleFactor) {
        List<QPixmap> shadowPixmaps = new ArrayList<>();
        for (QImage shadowImage : shadowImages) {
            QPixmap shadowPixmap = QPixmap.fromImage(shadowImage);
            shadowPixmap.setDevicePixelRatio(scaleFactor);
            shadowPixmaps.add(shadowPixmap);
            shadowImage.dispose();
        }
        return shadowPixmaps;
    }

    private void clearBoxShadowPixmaps() {
        boxShadowPixmaps.values().forEach(shadowPixmaps -> shadowPixmaps.forEach(QPixmap::dispose));
        boxShadowPixmaps.clear();
    }

    /** Boxes or labels, each with its ink, by the area their shadows land in: those whose shadows
     *  can meet share an area, and areas are merged until none overlap. Areas are blurred apart
     *  when that costs less than blurring their union. An area starts on an even pixel, as the
     *  halved blur does, so it blurs as the whole container would. */
    private static <T> Map<Rectangle, Map<T, Rectangle>> byShadowArea(List<T> casters,
                                                                      Function<T, Rectangle> ink,
                                                                      int padding,
                                                                      Rectangle container) {
        Map<T, Rectangle> inkByCaster = new LinkedHashMap<>();
        Map<Rectangle, Map<T, Rectangle>> inkByCasterByArea = new LinkedHashMap<>();
        for (T caster : casters) {
            Rectangle casterInk = ink.apply(caster);
            inkByCaster.put(caster, casterInk);
            inkByCasterByArea.computeIfAbsent(new Rectangle(casterInk.x() - padding,
                                      casterInk.y() - padding,
                                      casterInk.width() + 2 * padding,
                                      casterInk.height() + 2 * padding),
                                      area -> new LinkedHashMap<>())
                             .put(caster, casterInk);
        }
        Map<Rectangle, Map<T, Rectangle>> mergedInkByCasterByArea =
                byMergedArea(inkByCasterByArea);
        while (mergedInkByCasterByArea.size() < inkByCasterByArea.size()) {
            inkByCasterByArea = mergedInkByCasterByArea;
            mergedInkByCasterByArea = byMergedArea(inkByCasterByArea);
        }
        Rectangle union = Rectangle.union(inkByCasterByArea.keySet());
        long areasCost = (inkByCasterByArea.size() - 1) * AREA_COST_PIXELS;
        for (Rectangle area : inkByCasterByArea.keySet())
            areasCost += (long) area.width() * area.height();
        if (areasCost >= (long) union.width() * union.height())
            inkByCasterByArea = Map.of(union, inkByCaster);
        Map<Rectangle, Map<T, Rectangle>> inkByCasterByShadowArea = new LinkedHashMap<>();
        for (Map.Entry<Rectangle, Map<T, Rectangle>> entry : inkByCasterByArea.entrySet()) {
            Rectangle area = entry.getKey().intersection(container);
            int left = area.x() / 2 * 2, top = area.y() / 2 * 2;
            inkByCasterByShadowArea.put(new Rectangle(left, top, area.x() + area.width() - left,
                    area.y() + area.height() - top), entry.getValue());
        }
        return inkByCasterByShadowArea;
    }

    /** What overlapping areas hold together, by the union of those areas. */
    private static <T> Map<Rectangle, Map<T, Rectangle>> byMergedArea(
            Map<Rectangle, Map<T, Rectangle>> inkByCasterByArea) {
        List<Rectangle> areas = new ArrayList<>(inkByCasterByArea.keySet());
        int[] parents = new int[areas.size()];
        for (int index = 0; index < parents.length; index++)
            parents[index] = index;
        Integer[] indexesByLeft = new Integer[areas.size()];
        for (int index = 0; index < indexesByLeft.length; index++)
            indexesByLeft[index] = index;
        Arrays.sort(indexesByLeft, Comparator.comparingInt(index -> areas.get(index).x()));
        for (int i = 0; i < indexesByLeft.length; i++) {
            Rectangle area = areas.get(indexesByLeft[i]);
            for (int j = i + 1; j < indexesByLeft.length; j++) {
                Rectangle otherArea = areas.get(indexesByLeft[j]);
                if (otherArea.x() >= area.x() + area.width())
                    break;
                if (otherArea.y() < area.y() + area.height() &&
                    area.y() < otherArea.y() + otherArea.height())
                    parents[root(parents, indexesByLeft[i])] = root(parents, indexesByLeft[j]);
            }
        }
        Map<Integer, List<Rectangle>> areasByRoot = new LinkedHashMap<>();
        for (int index = 0; index < parents.length; index++)
            areasByRoot.computeIfAbsent(root(parents, index), groupRoot -> new ArrayList<>())
                       .add(areas.get(index));
        Map<Rectangle, Map<T, Rectangle>> inkByCasterByMergedArea = new LinkedHashMap<>();
        for (List<Rectangle> mergedAreas : areasByRoot.values()) {
            Map<T, Rectangle> mergedInkByCaster = new LinkedHashMap<>();
            for (Rectangle area : mergedAreas)
                mergedInkByCaster.putAll(inkByCasterByArea.get(area));
            inkByCasterByMergedArea.put(Rectangle.union(mergedAreas), mergedInkByCaster);
        }
        return inkByCasterByMergedArea;
    }

    private static int root(int[] parents, int index) {
        while (parents[index] != index)
            index = parents[index] = parents[parents[index]];
        return index;
    }

    /** Paints into a chunk of an area the casters whose shadows reach into it, the way
     *  byShadowArea puts them in the area. */
    private static <T> void paintCasters(QPainter painter, Rectangle chunk,
                                         Map<T, Rectangle> inkByCaster, int padding,
                                         BiConsumer<T, QPainter> paintCaster) {
        for (Map.Entry<T, Rectangle> entry : inkByCaster.entrySet()) {
            Rectangle casterInk = entry.getValue();
            if (casterInk.y() - padding < chunk.y() + chunk.height() &&
                chunk.y() < casterInk.y() + casterInk.height() + padding)
                paintCaster.accept(entry.getKey(), painter);
        }
    }

    /** One layer per decoration depth, drawing that depth's labels above the boxes carrying them, so
     *  its shadow applies to the whole layer at once the way a hint label shadow does. */
    private List<HintPaintLayer> addDecorationLabelLayers(QWidget container, List<HintBox> boxes,
                                                          List<DecorationStyle> decorationStyles,
                                                          int containerWidth,
                                                          int containerHeight) {
        List<HintPaintLayer> layers = new ArrayList<>();
        for (int depth = 0; depth < decorationStyles.size(); depth++) {
            int labelDepth = depth + 1;
            HintPaintLayer layer = new HintPaintLayer(container, boxes, List.of(),
                    (box, painter) -> box.paintDecorationLabels(painter, labelDepth, false));
            layer.setGeometry(0, 0, containerWidth, containerHeight);
            layers.add(layer);
        }
        return layers;
    }

    /** Pre-renders the shadow of each decoration label layer, as a hint label layer's. */
    private void addDecorationLabelShadows(List<HintPaintLayer> layers, List<HintBox> boxes,
                                           List<DecorationStyle> decorationStyles,
                                           int containerWidth, int containerHeight,
                                           double screenScale) {
        for (int depth = 0; depth < layers.size(); depth++) {
            int labelDepth = depth + 1;
            QtFontStyle labelStyle = decorationStyles.get(depth).labelStyle();
            if (labelStyle.shadowColor().alpha() == 0 || labelStyle.invisible())
                continue;
            Map<HintBox, Rectangle> inkByBox = new LinkedHashMap<>();
            Set<Integer> shadowRgbs = new HashSet<>();
            for (HintBox box : boxes) {
                List<Rectangle> ink = new ArrayList<>();
                box.collectDecorationLabelBounds(labelDepth, 0, 0, ink);
                if (ink.isEmpty())
                    continue;
                inkByBox.put(box, Rectangle.union(ink));
                box.collectDecorationLabelShadowRgbs(labelDepth, shadowRgbs);
            }
            int padding = shadowPadding(labelStyle);
            HintPaintLayer layer = layers.get(depth);
            if (inkByBox.isEmpty() || !layer.fitToInk(List.copyOf(inkByBox.values()), padding,
                    containerWidth, containerHeight))
                continue;
            Rectangle area = new Rectangle(layer.originX, layer.originY, layer.width(),
                    layer.height());
            QColor shadowColor = shadowColor(shadowRgbs, labelStyle.shadowColor().alpha());
            layer.addShadowPixmaps(shadowPixmaps(Shadows.renderShadow(area,
                    dotsPerMeter(screenScale),
                    (painter, chunk) -> paintCasters(painter, chunk, inkByBox, padding,
                            (box, boxPainter) -> box.paintDecorationLabels(boxPainter,
                                    labelDepth, true)),
                    shadowColor, labelStyle.shadowBlurRadius(),
                    labelStyle.shadowHorizontalOffset(), labelStyle.shadowVerticalOffset(),
                    shadowRgbs.size() == 1,
                    labelStyle.hasTransparency() || labelStyle.shadowStackCount() != 1), 1),
                    area.x(), area.y(), labelStyle.shadowStackCount());
            shadowColor.dispose();
        }
    }

    private void addLabelLayer(QWidget container, List<HintLabel> labels,
                               QtHintFontStyle style,
                               boolean hasSelectedKeys,
                               int containerWidth,
                               int containerHeight,
                               double screenScale) {
        HintPaintLayer layer = new HintPaintLayer(container, List.of(), labels);
        layer.setGeometry(0, 0, containerWidth, containerHeight);
        if (style != null)
            applyLabelShadow(layer, labels, style, hasSelectedKeys, containerWidth,
                    containerHeight, screenScale);
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

    /** Pre-renders the shadow of a label layer, punched out of transparent text that would show it. */
    private void applyLabelShadow(HintPaintLayer layer,
                                         List<HintLabel> labels,
                                         QtHintFontStyle style,
                                         boolean hasSelectedKeys,
                                         int containerWidth,
                                         int containerHeight,
                                         double screenScale) {
        // Shadowing a layer that draws nothing still costs a blur of the whole layer, which for a
        // screen-sized hint mesh is tens of milliseconds.
        if (labels.isEmpty() || labels.getFirst().keyTexts.isEmpty() ||
            style.invisible(hasSelectedKeys))
            return;
        if (style.perKeyShadow()) {
            preRenderPerGroupShadow(layer, labels, containerWidth, containerHeight, screenScale);
            return;
        }
        QtFontStyle shadowStyle = style.defaultStyle();
        if (shadowStyle.shadowColor().alpha() == 0)
            return;
        int padding = shadowPadding(shadowStyle);
        for (Map.Entry<Rectangle, Map<HintLabel, Rectangle>> entry : byShadowArea(labels,
                HintLabel::ink, padding,
                new Rectangle(0, 0, containerWidth, containerHeight)).entrySet()) {
            Rectangle area = entry.getKey();
            Set<Integer> shadowRgbs = new HashSet<>();
            for (HintLabel label : entry.getValue().keySet())
                shadowRgbs.add(label.labelFontStyle.defaultStyle().shadowColor().rgb());
            QColor shadowColor = shadowColor(shadowRgbs, shadowStyle.shadowColor().alpha());
            layer.addShadowPixmaps(shadowPixmaps(Shadows.renderShadow(area,
                    dotsPerMeter(screenScale),
                    (painter, chunk) -> paintCasters(painter, chunk, entry.getValue(), padding,
                            HintLabel::paintOpaque),
                    shadowColor, shadowStyle.shadowBlurRadius(),
                    shadowStyle.shadowHorizontalOffset(), shadowStyle.shadowVerticalOffset(),
                    shadowRgbs.size() == 1,
                    style.hasTransparency() || shadowStyle.shadowStackCount() != 1), 1),
                    area.x(), area.y(), shadowStyle.shadowStackCount());
            shadowColor.dispose();
        }
    }

    /** The color that tints the shadow of casters of one color. The shadow of casters of several
     *  blurs their own colors, and takes only the alpha. */
    private static QColor shadowColor(Set<Integer> rgbs, int alpha) {
        return QColor.fromRgba(rgbs.iterator().next() & 0xFFFFFF | alpha << 24);
    }




    /**
     * Per-group shadow rendering: groups keys by their effective shadow
     * settings (state + prefix/non-prefix) and renders each group separately.
     */
    private void preRenderPerGroupShadow(
            HintPaintLayer layer, List<HintLabel> labels,
            int containerWidth, int containerHeight,
            double screenScale) {
        // 1. Collect unique shadow groups.
        Map<ShadowGroupKey, Set<Integer>> shadowRgbsByGroup = new LinkedHashMap<>();
        for (HintLabel label : labels) {
            for (HintKeyText keyText : label.keyTexts) {
                shadowRgbsByGroup.computeIfAbsent(label.shadowGroupKey(keyText),
                                         group -> new HashSet<>())
                                 .add(label.hintKeyTextQtFontStyle(keyText).shadowColor().rgb());
            }
        }
        int padding = shadowRgbsByGroup.keySet().stream()
                            .mapToInt(group -> shadowPadding(group.blurRadius(),
                                    group.horizontalOffset(), group.verticalOffset()))
                            .max()
                            .orElse(0);
        Map<HintLabel, Rectangle> inkByLabel = new LinkedHashMap<>();
        for (HintLabel label : labels)
            inkByLabel.put(label, label.ink());
        if (!layer.fitToInk(List.copyOf(inkByLabel.values()), padding, containerWidth,
                containerHeight))
            return;
        Rectangle area = new Rectangle(layer.originX, layer.originY, layer.width(),
                layer.height());
        // 2. Render each group.
        for (Map.Entry<ShadowGroupKey, Set<Integer>> entry : shadowRgbsByGroup.entrySet()) {
            ShadowGroupKey group = entry.getKey();
            if (group.alpha() == 0)
                continue;
            QColor shadowColor = shadowColor(entry.getValue(), group.alpha());
            // Render source image with only keys matching this group.
            List<QImage> shadowImages = Shadows.renderShadow(area,
                    dotsPerMeter(screenScale),
                    (painter, chunk) -> paintCasters(painter, chunk, inkByLabel, padding,
                            (label, labelPainter) -> label.paintOpaqueFiltered(labelPainter,
                                    keyText -> label.shadowGroupKey(keyText).equals(group))),
                    shadowColor, group.blurRadius(), group.horizontalOffset(),
                    group.verticalOffset(), entry.getValue().size() == 1, true);
            shadowColor.dispose();
            layer.addShadowPixmaps(shadowPixmaps(shadowImages, 1), area.x(), area.y(),
                    group.stackCount());
        }
    }

    private class HintPaintLayer extends QWidget {

        private final List<HintBox> boxes;
        private final List<HintLabel> labels;
        private final BiConsumer<HintBox, QPainter> paintBox;
        /** Where the layer sits when it covers only its ink; everything it paints shifts by it. */
        private int originX, originY;
        private final List<ShadowPixmap> shadowPixmaps = new ArrayList<>();
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
                       BiConsumer<HintBox, QPainter> paintBox) {
            super(parent);
            this.boxes = boxes;
            this.labels = labels;
            this.paintBox = paintBox;
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
            if (current != null) {
                dirty.translate(-originX, -originY);
                current.dispose();
            }
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

        /** Adds the chunks of a shadow from (x, y) down, in pixels that their device pixel ratio
         *  turns into the layer's coordinates. */
        void addShadowPixmaps(List<QPixmap> shadowPixmaps, int x, int y, int stackCount) {
            int top = y;
            for (QPixmap shadowPixmap : shadowPixmaps) {
                double scaleFactor = shadowPixmap.devicePixelRatio();
                this.shadowPixmaps.add(new ShadowPixmap(shadowPixmap,
                        (int) Math.round(x / scaleFactor), (int) Math.round(top / scaleFactor),
                        stackCount));
                top += shadowPixmap.height();
            }
        }

        void disposeBuffers() {
            for (ShadowPixmap shadowPixmap : shadowPixmaps)
                shadowPixmap.pixmap().dispose();
            shadowPixmaps.clear();
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
            QPainter painter = new QPainter(this);
            QRect dirty = event.rect();
            paint(painter, new Rectangle(dirty.x(), dirty.y(), dirty.width(), dirty.height()));
            painter.end();
            painter.dispose();
            long durationMillis = (System.nanoTime() - before) / 1000000;
            if (durationMillis >= SLOW_PAINT_MS && !preWarming) {
                logger.debug("Painted a " + dirty.width() + "x" + dirty.height() +
                             " region at (" + dirty.x() + ", " + dirty.y() + ") of a " +
                             width() + "x" + height() + " hint layer of " + boxes.size() +
                             " boxes and " + labels.size() + " labels in " + durationMillis +
                             "ms");
            }
        }

        /** Paints what lands in dirty, in the layer's coordinates. */
        void paint(QPainter painter, Rectangle dirty) {
            if (originX != 0 || originY != 0)
                painter.translate(-originX, -originY);
            Rectangle painted = new Rectangle(dirty.x() + originX, dirty.y() + originY,
                    dirty.width(), dirty.height());
            if (!outgoing.isEmpty() && outgoingCrop != null) {
                painter.save();
                QRect dirtyRect = new QRect(painted.x(), painted.y(), painted.width(),
                        painted.height());
                QRect outgoingDirty = outgoingCrop.intersected(dirtyRect);
                dirtyRect.dispose();
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
                painter.restore();
                dirtyRegion.dispose();
                outgoingDirty.dispose();
            }
            if (enclosingInk != null) {
                QRect dirtyRect = new QRect(painted.x(), painted.y(), painted.width(),
                        painted.height());
                QRect clip = crop != null ? crop.intersected(dirtyRect) : new QRect(dirtyRect);
                dirtyRect.dispose();
                QRegion clipRegion = new QRegion(clip);
                QRegion visible = clipRegion.subtracted(enclosingInk);
                painter.setClipRegion(visible);
                visible.dispose();
                clipRegion.dispose();
                clip.dispose();
            }
            else if (crop != null) {
                QRect dirtyRect = new QRect(painted.x(), painted.y(), painted.width(),
                        painted.height());
                QRect clip = crop.intersected(dirtyRect);
                painter.setClipRect(clip);
                clip.dispose();
                dirtyRect.dispose();
            }
            for (ShadowPixmap shadowPixmap : shadowPixmaps)
                for (int stack = 0; stack < shadowPixmap.stackCount(); stack++)
                    painter.drawPixmap(shadowPixmap.x(), shadowPixmap.y(), shadowPixmap.pixmap());
            for (HintBox box : boxes)
                if (!box.ink().intersection(painted).isEmpty())
                    paintBox.accept(box, painter);
            for (HintLabel label : labels)
                if (!label.ink().intersection(painted).isEmpty())
                    label.paint(painter);
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
        GradientColor boxColor = style.boxColor();
        int centerRgb;
        if (boxColor.direction() == GradientDirection.IN_TURN) {
            Function<Hint, List<Key>> labelKeySequence = boxColor.step() == GradientStep.GROUP ?
                    labelHint -> groupPrefix(currentHintMesh, labelHint) : Hint::keySequence;
            centerRgb = boxColor.turnRgb(turnByKeySequence(currentHintMesh.hints(),
                    labelKeySequence).get(labelKeySequence.apply(hint)));
        }
        else {
            Rectangle boxColorArea =
                    gradientArea(boxColor.area(), hintMeshWindow, currentHintMesh);
            centerRgb = boxColorArea == null ?
                    boxColor.rgbAt(GradientColor.unitArea, 0.5, 0.5) :
                    boxColor.rgbAt(boxColorArea, hint.centerX(), hint.centerY());
        }
        GradientColor centerColor = GradientColor.parse(Color.hexColor(centerRgb));
        // Colors taken in turn are no sweep: the box is its own color.
        lastSelectedHintColors = new LastSelectedHintColors(
                boxColor.direction() == GradientDirection.IN_TURN ? centerColor : boxColor,
                centerColor);
        if (!currentHintMesh.styleByFilter().get(ScreenFilter.of(screen)).transition().enabled())
            return;
        boolean isHintGrid = lastHintMeshKey.hints().getFirst().cellWidth() != -1 &&
                             lastHintMeshKey.hints().size() > 1;
        if (!isHintGrid)
            // No animation for position history hints.
            // hideHintMesh() will be called by the switch mode command.
            return;
        hintMeshEndAnimation = true;
        Rectangle hintBoxRectangle =
                hintBoxRectanglesByHintMeshKey.get(lastHintMeshKey).get(hint.keySequence());
        QWidget container = containers(hintMeshWindow.window).getLast();
        QRect containerGeom = container.geometry();
        int boxWindowX = containerGeom.x() + hintBoxRectangle.x();
        int boxWindowY = containerGeom.y() + hintBoxRectangle.y();
        containerGeom.dispose();
        // Grab the box's fill and label from the container, not the composited window: the border
        // layer is clipped in lockstep by clipMatchCropBorderLayer, so grabbing borders here too
        // would draw them twice (doubled opacity). Keep the extra right/bottom margin (a cell's
        // right/bottom border lines are its neighbours', just past its rect) so the crop's target
        // extent covers them and the clipped layer keeps all four borders.
        int boxBorderThickness = (int) Math.round(
                scaledLength(style.boxBorderThickness(), screen.scale()) /
                qtScaleFactor(screen.scale()));
        QRect containerBoxRect = new QRect(hintBoxRectangle.x(), hintBoxRectangle.y(),
                hintBoxRectangle.width() + boxBorderThickness,
                hintBoxRectangle.height() + boxBorderThickness);
        QPixmap pixmap = container.grab(containerBoxRect); // Expensive.
        containerBoxRect.dispose();
        HintMesh hintMesh = currentHintMesh.builder().hints(List.of(hint)).build();
        PixmapsAndPosition pixmapsAndPosition =
                new PixmapsAndPosition(List.of(pixmap), boxWindowX, boxWindowY, null, List.of(),
                        hintMesh, hintMeshWindow.window.x(), hintMeshWindow.window.y());
        setHintMeshWindow(hintMeshWindow, hintMesh, screen.scale(), style, true,
                pixmapsAndPosition);
    }

}

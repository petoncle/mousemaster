package mousemaster.renderer;

import mousemaster.qt.*;

import io.qt.core.*;
import io.qt.gui.*;
import io.qt.widgets.*;
import mousemaster.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Cross-platform Qt rendering of the hint mesh: builds and caches the per-screen hint
 * boxes, labels and shadows, and runs the transition/match animations.
 */
public final class HintMeshRenderer {

    private static final Logger logger = LoggerFactory.getLogger(HintMeshRenderer.class);

    private final Map<HintMesh, PixmapAndPosition> hintMeshPixmaps = new HashMap<>();
    private final Map<HintMesh, Map<List<Key>, QRect>> hintBoxGeometriesByHintMeshKey = new HashMap<>();
    private boolean hintMeshEndAnimation;
    /**
     * Building the hint window is expensive and when it is done from the keyboard hook,
     * Windows will cancel the hook and the key press will go through to the other apps.
     * Windows won't wait for the keyboard hook to return if it's taking too long.
     */
    private Runnable setUncachedHintMeshWindowRunnable;
    private Runnable cacheQtHintWindowIntoPixmapRunnable;
    private Runnable messagePump;
    /**
     * True when the build is running from update() (deferred), meaning we are
     * NOT inside a keyboard hook callback and can safely pump messages to keep
     * the hook responsive. False when running inline from the hook callback.
     */
    private boolean pumpDuringHintBuild;
    private final Runnable hintMeshEndAnimationEndedCallback;
    private final Map<Screen, HintMeshWindow> hintMeshWindows = new LinkedHashMap<>(); // Ordered for topmost handling.
    private boolean showingHintMesh;
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
                                 AtomicReference<HintMesh> lastHintMeshKeyReference) {
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

    /** The Qt windows, for the platform's magnification and capture-exclusion loops. */
    public Collection<TransparentWindow> windows() {
        List<TransparentWindow> windows = new ArrayList<>();
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values())
            windows.add(hintMeshWindow.window());
        return windows;
    }

    /** Runs one unit of deferred work per frame: the build this frame, the pixmap cache the
     *  next. The platform overlay drives this once per frame. */
    public void runPendingWork() {
        if (setUncachedHintMeshWindowRunnable != null) {
            pumpDuringHintBuild = true;
            setUncachedHintMeshWindowRunnable.run();
            pumpDuringHintBuild = false;
            setUncachedHintMeshWindowRunnable = null;
            // Don't run the cache grab in the same tick: Qt hasn't painted
            // the window yet (processEvents runs before update in the main
            // loop). Let the next tick's processEvents paint, then grab.
        }
        else if (cacheQtHintWindowIntoPixmapRunnable != null) {
            cacheQtHintWindowIntoPixmapRunnable.run();
            cacheQtHintWindowIntoPixmapRunnable = null;
        }
    }

    /** Drops pending build/cache runnables: container.grab() on a destroyed widget crashes. */
    public void cancelPendingBuild() {
        setUncachedHintMeshWindowRunnable = null;
        cacheQtHintWindowIntoPixmapRunnable = null;
    }

    public void flushCache() {
        for (PixmapAndPosition pixmapAndPosition : hintMeshPixmaps.values())
            pixmapAndPosition.pixmap().dispose();
        hintMeshPixmaps.clear();
        hintBoxGeometriesByHintMeshKey.clear();
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values())
            hintMeshWindow.lastHintMeshKeyReference().set(null);
    }

    public boolean setHintMesh(HintMesh hintMesh, Zoom zoom, boolean hintMatch,
                               Set<Screen> screens) {
        boolean nonMatchShown = false;
        Objects.requireNonNull(hintMesh);
        if (!hintMesh.visible()) {
            hideHintMesh();
            return false;
        }
        if (showingHintMesh && currentHintMesh != null && currentHintMesh.equals(hintMesh))
            return false;
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
        if (!wasShowing && !hintMeshWindows.isEmpty()) {
            // Resolve fade settings from first screen's style.
            Map.Entry<Screen, HintMeshWindow> firstEntry =
                    hintMeshWindows.entrySet().iterator().next();
            HintMeshStyle style = currentHintMesh.styleByFilter()
                    .get(ViewportFilter.of(firstEntry.getKey()));
            hintMeshFadeAnimator = new FadeAnimator(
                    opacity -> {
                        for (HintMeshWindow w : hintMeshWindows.values())
                            w.window().setWindowOpacity(opacity);
                    },
                    this::doHideHintMesh,
                    style.fadeAnimationEnabled(),
                    style.fadeAnimationDuration());
            if (hintMeshFadeAnimator.isEnabled()) {
                for (HintMeshWindow w : hintMeshWindows.values())
                    w.window().setWindowOpacity(0.0);
                hintMeshFadeAnimator.startFadeIn();
            }
        }
        return nonMatchShown;
    }

    public void hideHintMesh() {
        if (!showingHintMesh)
            return;
        if (isHintMeshEndAnimation())
            return;
        if (hintMeshFadeAnimator != null && hintMeshFadeAnimator.shouldDeferHide())
            return;
        doHideHintMesh();
    }

    private void doHideHintMesh() {
        showingHintMesh = false;
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
            hintMeshWindow.window().setBackground(null, null);
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
                Rectangle screenRectangle = screen.rectangle();
                boolean intersects =
                        screenRectangle.x() < backgroundArea.x() + backgroundArea.width() &&
                        backgroundArea.x() < screenRectangle.x() + screenRectangle.width() &&
                        screenRectangle.y() < backgroundArea.y() + backgroundArea.height() &&
                        backgroundArea.y() < screenRectangle.y() + screenRectangle.height();
                if (intersects)
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
                    hintMesh.styleByFilter().get(ViewportFilter.of(screen));
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
                        existingWindow.lastHintMeshKeyReference());
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
        window.move(screen.rectangle().x(), screen.rectangle().y());
        window.resize(screen.rectangle().width(), screen.rectangle().height());
        return new HintMeshWindow(window, hints, zoom,
                new ArrayList<>(), new ArrayList<>(), new AtomicReference<>());
    }

    public void preWarmHintMeshWindows(Set<Screen> screens) {
        long before = System.nanoTime();
        for (Screen screen : screens) {
            if (hintMeshWindows.containsKey(screen))
                continue;
            hintMeshWindows.put(screen, createHintMeshWindow(screen, new ArrayList<>(), null));
        }
        logger.info("Pre-warmed hint mesh windows for " + screens.size() +
                " screens in " + (long) ((System.nanoTime() - before) / 1e6) + "ms");
    }

    private class ClearBackgroundQLabel extends QLabel {
        private QColor clearColor = new QColor(0, 0, 0, 0);

        void setClearColor(QColor clearColor) {
            if (this.clearColor != null)
                this.clearColor.dispose();
            this.clearColor = clearColor;
        }

        @Override
        protected void paintEvent(QPaintEvent event) {
            QPainter painter = new QPainter(this);
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Source);
            // Clear what's behind (when we're drawing the old container behind).
            QRect r = rect();
            painter.fillRect(r, clearColor);
            r.dispose();
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
                                          PixmapAndPosition forcedPixmapAndPosition) {
        setUncachedHintMeshWindowRunnable = null;
        cacheQtHintWindowIntoPixmapRunnable = null;
        int transitionAnimationCurrentTime =
                hintMeshWindow.animations.stream()
                                         .filter(animation -> animation.getState() ==
                                                              QAbstractAnimation.State.Running)
                                         .map(QAbstractAnimation::getCurrentTime)
                                         .findFirst()
                                         .orElse(0);
        TransparentWindow window = hintMeshWindow.window;
        for (QVariantAnimation animation : hintMeshWindow.animations) {
            if (window.children().size() < 2)
                continue;
            animation.stop();
            QWidget veryOldContainer = (QWidget) window.children().getFirst();
            // oldContainer is the container we were animating to (but a new container replaced it).
            QWidget oldContainer = (QWidget) window.children().getLast();
            veryOldContainer.setParent(null);
            oldContainer.setParent(null);
            QWidget mergedContainer = new QWidget(window);
            QRect veryOldGeom = veryOldContainer.geometry();
            QRect oldGeom = oldContainer.geometry();
            int mergedContainerX = Math.min(veryOldGeom.x(), oldGeom.x());
            int mergedContainerY = Math.min(veryOldGeom.y(), oldGeom.y());
            mergedContainer.setGeometry(
                    mergedContainerX,
                    mergedContainerY,
                    Math.max(veryOldGeom.right(), oldGeom.right()) - mergedContainerX,
                    Math.max(veryOldGeom.bottom(), oldGeom.bottom()) - mergedContainerY
            );
            veryOldGeom.dispose();
            oldGeom.dispose();
            veryOldContainer.move(veryOldContainer.x() - mergedContainerX, veryOldContainer.y() - mergedContainerY);
            oldContainer.move(oldContainer.x() - mergedContainerX, oldContainer.y() - mergedContainerY);
            veryOldContainer.setParent(mergedContainer);
            oldContainer.setParent(mergedContainer);
            mergedContainer.show();
        }
        for (QVariantAnimation animation : hintMeshWindow.animations)
            animation.dispose();
        hintMeshWindow.animations.clear();
        hintMeshWindow.animationCallbacks.clear();
        // When QT_ENABLE_HIGHDPI_SCALING is not 0 (e.g. Linux/macOS), then
        // devicePixelRatio will be the screen's scale.
        double qtScaleFactor = QApplication.primaryScreen().devicePixelRatio();
        QWidget oldContainer =
                window.children().isEmpty() ? null :
                        (QWidget) window.children().getFirst();
        boolean oldContainerHidden = oldContainer == null || oldContainer.isHidden();
        window.clearWindow();
        // Compute background color for both the window and the container clear.
        Rectangle backgroundArea = hintMesh.backgroundArea();
        QColor backgroundColor = backgroundArea != null && style.backgroundOpacity() > 0 ?
                QtColorUtil.qColor(style.backgroundHexColor(), style.backgroundOpacity()) : null;
        if (backgroundColor != null) {
            // Set background on the window itself (painted before child containers,
            // covers the area outside the container).
            int backgroundX = backgroundArea.x() - window.x();
            int backgroundY = backgroundArea.y() - window.y();
            int left = Math.max(0, backgroundX);
            int top = Math.max(0, backgroundY);
            int right = Math.min(window.width(), backgroundX + backgroundArea.width());
            int bottom = Math.min(window.height(), backgroundY + backgroundArea.height());
            if (right > left && bottom > top) {
                QRect backgroundRect = new QRect(left, top, right - left, bottom - top);
                window.setBackground(backgroundColor, backgroundRect);
                // Without this, Qt only repaints the container's area,
                // missing the background outside of it.
                QRect updateRect = new QRect(left, top, right - left, bottom - top);
                window.update(updateRect);
                updateRect.dispose();
            }
            else {
                window.setBackground(null, null);
            }
        }
        else {
            window.setBackground(null, null);
        }
        if (hintMeshWindow.hints().isEmpty()) {
            QWidget container = new QWidget(window);
            container.setGeometry(0, 0, 0, 0);
            container.show();
            window.show();
            return;
        }
        HintMesh hintMeshKey = forcedPixmapAndPosition != null ? null :
                new HintMesh.HintMeshBuilder(hintMesh)
                        .hints(trimmedHints(hintMeshWindow.hints(),
                                hintMesh.selectedKeySequence()))
                        .build();
        hintMeshWindow.lastHintMeshKeyReference.set(hintMeshKey); // Will be used by animateHintMatch.
        PixmapAndPosition pixmapAndPosition =
                forcedPixmapAndPosition != null ? forcedPixmapAndPosition :
                        hintMeshPixmaps.get(hintMeshKey);
        boolean isHintGrid = hintMeshWindow.hints().getFirst().cellWidth() != -1;
        QWidget newContainer;
        if (pixmapAndPosition != null) {
            logger.trace("Using cached pixmap " + pixmapAndPosition);
            QLabel pixmapLabel = new ClearBackgroundQLabel();
            pixmapLabel.setPixmap(pixmapAndPosition.pixmap);
            Hint originalFirstHint = pixmapAndPosition.originalHintMesh.hints().getFirst();
            int originalWindowX = pixmapAndPosition.windowX;
            int originalWindowY = pixmapAndPosition.windowY;
            Hint newFirstHint = hintMesh.hints().getFirst();
            // Translate the original pixmap which may be at a different position than
            // the new hint mesh.
            pixmapLabel.setGeometry(pixmapAndPosition.x() + (int) Math.round(newFirstHint.centerX() - window.x() - (originalFirstHint.centerX() - originalWindowX)),
                    pixmapAndPosition.y() + (int) Math.round(newFirstHint.centerY() - window.y() - (originalFirstHint.centerY() - originalWindowY)),
                    pixmapAndPosition.pixmap().width(), pixmapAndPosition.pixmap().height());
            newContainer = pixmapLabel;
            transitionHintContainers(
                    style.transitionAnimationEnabled() && isHintGrid && !oldContainerHidden && !zoomChanged,
                    oldContainer, newContainer,
                    window, hintMeshWindow,
                    style.transitionAnimationDuration(), transitionAnimationCurrentTime);
        }
        else {
            // Uses ClearBackgroundQLabel because when in the mergedContainer,
            // the top-level container must override the container below.
            ClearBackgroundQLabel container = new ClearBackgroundQLabel();
            if (backgroundColor != null)
                container.setClearColor(backgroundColor);
            container.setStyleSheet("background: transparent;");
            newContainer = container;
            setUncachedHintMeshWindowRunnable =
                    () -> {
                        long before = System.nanoTime();
                        Map<List<Key>, QRect> hintBoxGeometries =
                                setUncachedHintMeshWindow(hintMeshWindow, hintMesh,
                                        screenScale,
                                        style, qtScaleFactor,
                                        container
                                );
                        logger.debug("Built hint mesh window in " + (long) ((System.nanoTime() - before) / 1e6) + "ms");
                        hintBoxGeometriesByHintMeshKey.put(hintMeshKey,
                                hintBoxGeometries);
                        transitionHintContainers(
                                style.transitionAnimationEnabled() && isHintGrid && !oldContainerHidden && !zoomChanged,
                                oldContainer, newContainer,
                                window, hintMeshWindow,
                                style.transitionAnimationDuration(), transitionAnimationCurrentTime);
                        if (isHintGrid) {
                            // Defer the pixmap cache grab to the next frame so the hint mesh
                            // is shown immediately. The grab is expensive (~370ms at 4K) but
                            // only needed for caching subsequent renders.
                            cacheQtHintWindowIntoPixmapRunnable = () ->
                                cacheQtHintWindowIntoPixmap(window, container, hintMeshKey, hintMesh);
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
                setUncachedHintMeshWindowRunnable = null;
            }
        }
    }

    private void transitionHintContainers(boolean animateTransition, QWidget oldContainer,
                                                 QWidget newContainer, TransparentWindow window,
                                                 HintMeshWindow hintMeshWindow,
                                                 Duration animationDuration,
                                                 int animationCurrentTime) {
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
            oldRect.dispose();
            newRect.dispose();
            if (animateTransition && oldContainsNew) {
                // Shrink old container until it reaches the position and size of new.
                oldContainer.setParent(window);
                oldContainer.show();
                newContainer.setParent(window);
                newContainer.show();
                QRect beginRect =
                        new QRect(0, 0,
                                oldContainer.width(),
                                oldContainer.height());
                QRect endRect =
                        new QRect(newContainer.x() - oldContainer.x(),
                                newContainer.y() - oldContainer.y(),
                                newContainer.width(),
                                newContainer.height());
                QVariantAnimation animation =
                        hintContainerAnimation(beginRect, endRect, animationDuration);
                beginRect.dispose();
                HintContainerAnimationChanged animationChanged = new HintContainerAnimationChanged(
                        oldContainer);
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
                if (containersEqual)
                    // Screen selection hint end.
                    animationFinished.invoke();
                else {
                    animation.start();
                    animation.setCurrentTime(animationCurrentTime);
                }
            }
            else if (animateTransition && newContainsOld) {
                // Initially show new container with the position and size of old.
                // Then grow new container until it reaches its final position and size.
                newContainer.setParent(window);
                newContainer.show();
                QRect beginRect =
                        new QRect(oldContainer.x() - newContainer.x(),
                                oldContainer.y() - newContainer.y(),
                                oldContainer.width(),
                                oldContainer.height());
                QRect endRect =
                        new QRect(0, 0,
                                newContainer.width(), newContainer.height());
                QRegion beginRegion = new QRegion(beginRect);
                newContainer.setMask(beginRegion);
                beginRegion.dispose();
                QVariantAnimation animation = hintContainerAnimation(beginRect, endRect,
                        animationDuration);
                beginRect.dispose();
                HintContainerAnimationChanged animationChanged =
                        new HintContainerAnimationChanged(newContainer);
                animation.valueChanged.connect(animationChanged);
                HintContainerAnimationFinished animationFinished =
                        new HintContainerAnimationFinished(null, newContainer,
                                endRect, this);
                animation.finished.connect(animationFinished);
                hintMeshWindow.animations.add(animation);
                hintMeshWindow.animationCallbacks.add(animationChanged);
                hintMeshWindow.animationCallbacks.add(animationFinished);
                animation.start();
                animation.setCurrentTime(animationCurrentTime);
                oldContainer.setParent(null);
                oldContainer.disposeLater();
            }
            else {
                oldContainer.setParent(null);
                oldContainer.disposeLater();
                newContainer.setParent(window);
                newContainer.show();
                hintContainerAnimationEnded();
            }
        }
        else {
            newContainer.setParent(window);
            newContainer.show();
        }
        window.show();
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

    public static class HintContainerAnimationChanged implements QMetaObject.Slot1<Object> {

        private final QWidget container;

        public HintContainerAnimationChanged(QWidget container) {
            this.container = container;
        }

        @Override
        public void invoke(Object arg) {
            QRect r = (QRect) arg;
            QRegion region = new QRegion(r);
            container.setMask(region);
            region.dispose();
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
            QRegion region = new QRegion(endRect);
            animatedContainer.setMask(region); // animatedContainer can be the oldContainer.
            region.dispose();
            endRect.dispose();
            if (oldContainer != null) {
                oldContainer.setParent(null);
                oldContainer.disposeLater();
            }
            renderer.hintContainerAnimationEnded();
        }
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
        animation.setEasingCurve(QEasingCurve.Type.InOutQuad);
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

    }

    private Map<List<Key>, QRect> setUncachedHintMeshWindow(HintMeshWindow hintMeshWindow, HintMesh hintMesh,
                                                              double screenScale, HintMeshStyle style,
                                                              double qtScaleFactor,
                                                              QWidget container) {
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
        int minHintLeft = Integer.MAX_VALUE;
        int minHintTop = Integer.MAX_VALUE;
        int maxHintRight = Integer.MIN_VALUE;
        int maxHintBottom = Integer.MIN_VALUE;
        boolean hasSelectedKeys = !hintMesh.selectedKeySequence().isEmpty();
        // Background prefix is on a different layer.
        boolean hasForegroundPrefixKeys = !style.prefixInBackground() && hintMesh.prefixLength() != -1;
        HintFontStyle prefixFontStyle = hasForegroundPrefixKeys ? style.prefixFontStyle() : null;
        QtHintFontStyle labelFontStyle = QtHintFont.qtHintFontStyle(style.fontStyle(), prefixFontStyle, screenScale, hasSelectedKeys);
        QColor boxColor = QtColorUtil.qColor(style.boxHexColor(), style.boxOpacity());
        QColor boxBorderColor = QtColorUtil.qColor(style.boxBorderHexColor(), style.boxBorderOpacity());
        QColor prefixBoxBorderColor = QtColorUtil.qColor(style.prefixBoxBorderHexColor(), style.prefixBoxBorderOpacity());
        // One entry per nested depth: subgrid at index 0, subsubgrid at index 1, ...
        List<SubgridStyle> subgridStyles = new ArrayList<>();
        if (hintMesh.subgrid() != null) {
            subgridStyles.add(subgridStyle(style.subgridBorderHexColor(),
                    style.subgridBorderOpacity(), style.subgridBorderThickness(),
                    style.subgridBorderLength(), style.subgridClosed(),
                    style.subgridFontStyle().defaultFontStyle()));
            if (hintMesh.subgrid().subgrid() != null)
                subgridStyles.add(subgridStyle(style.subsubgridBorderHexColor(),
                        style.subsubgridBorderOpacity(), style.subsubgridBorderThickness(),
                        style.subsubgridBorderLength(), style.subsubgridClosed(),
                        style.subsubgridFontStyle().defaultFontStyle()));
        }
        int hintGridColumnCount = isHintPartOfGrid ? hintGridColumnCount(hintMeshWindow.hints()) : -1;
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
            // Size of cell for screen selection hint is not configured by user.
            // The default size is used and it is too small (and will be less than totalXAdvance).
            int cellHorizontalPadding = (int) Math.round(style.cellHorizontalPadding());
            int cellVerticalPadding = (int) Math.round(style.cellVerticalPadding());
            double cellWidth = (hint.cellWidth() != -1 ?
                    // For grid hints, use the grid cell width as-is so boxes tile
                    // perfectly. Text that overflows is handled by the label layer.
                    (isHintPartOfGrid ? hint.cellWidth() :
                            Math.max(totalXAdvance, hint.cellWidth())) :
                    totalXAdvance) + 2 * cellHorizontalPadding;
            int lineHeight = labelFontStyle.defaultStyle().metrics().height();
            double cellHeight = (hint.cellHeight() != -1 ?
                    (isHintPartOfGrid ? hint.cellHeight() :
                            Math.max(lineHeight, hint.cellHeight())) :
                    lineHeight) + 2 * cellVerticalPadding;
            int fullBoxWidth = (int) cellWidth;
            int fullBoxHeight = (int) cellHeight;
            int x = hintRoundedX(hint.centerX(), cellWidth, qtScaleFactor);
            int y = hintRoundedY(hint.centerY(), cellHeight, qtScaleFactor);
            if (isHintPartOfGrid
                && hintIndex + 1 < hints.size()
                && hintRoundedX(hints.get(hintIndex + 1).centerX(),
                    hints.get(hintIndex + 1).cellWidth(), qtScaleFactor)
                   > x + fullBoxWidth)
                fullBoxWidth++;
            if (isHintPartOfGrid
                && hintIndex + hintGridColumnCount < hints.size()
                && hintRoundedY(hints.get(hintIndex + hintGridColumnCount).centerY(),
                    hints.get(hintIndex + hintGridColumnCount).cellHeight(),
                    qtScaleFactor)
                   > y + fullBoxHeight) {
                fullBoxHeight++;
            }
            List<Key> prefix = (labelOverridden || hintMesh.prefixLength() == -1) ?
                    List.of() : hint.keySequence().subList(0,
                    hintMesh.prefixLength());
            List<Key> suffix = labelOverridden ? labelKeys :
                    hint.keySequence().subList(prefix.size(), hint.keySequence().size());
            HintLabel hintLabel =
                    new HintLabel(
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
                                    - (style.prefixInBackground() && hintMesh.prefixLength() != -1 ? prefix.size() : 0)));
            hintLabels.add(hintLabel);
            int boxBorderThickness = (int) Math.round(style.boxBorderThickness());
            boolean gridLeftEdge = isHintPartOfGrid && hint.centerX() == minHintCenterX || style.boxWidthPercent() != 1;
            boolean gridTopEdge = isHintPartOfGrid && hint.centerY() == minHintCenterY || style.boxHeightPercent() != 1;
            boolean gridRightEdge = isHintPartOfGrid && hint.centerX() == maxHintCenterX || style.boxWidthPercent() != 1;
            boolean gridBottomEdge = isHintPartOfGrid && hint.centerY() == maxHintCenterY || style.boxHeightPercent() != 1;
            HintBox hintBox =
                    new HintBox(hint, (int) Math.round(style.boxBorderLength()),
                            boxBorderThickness,
                            boxColor,
                            boxBorderColor,
                            isHintPartOfGrid,
                            gridLeftEdge, gridTopEdge, gridRightEdge, gridBottomEdge,
                            true,
                            qtScaleFactor,
                            (int) Math.round(style.boxBorderRadius())
                    );
            hintBoxes.add(hintBox);
            int boxWidth, boxHeight;
            if (isHintPartOfGrid
                // Exclude single-hint grids (e.g. screen selection hint) so the box
                // can expand to fit its text.
                && hints.size() != 1) {
                // For grid hints, box size is determined by the grid cell, not the text.
                boxWidth = (int) (fullBoxWidth * style.boxWidthPercent());
                boxHeight = (int) (fullBoxHeight * style.boxHeightPercent());
            }
            else {
                boxWidth = Math.max(hintLabel.tightHintBoxWidth, (int) (fullBoxWidth * style.boxWidthPercent()));
                boxHeight = Math.max(hintLabel.tightHintBoxHeight, (int) (fullBoxHeight * style.boxHeightPercent()));
            }
            hintLabel.left = !isHintPartOfGrid && boxWidth == hintLabel.tightHintBoxWidth ? hintLabel.tightHintBoxLeft : (fullBoxWidth - boxWidth) / 2;
            hintLabel.top = !isHintPartOfGrid && boxHeight == hintLabel.tightHintBoxHeight ? hintLabel.tightHintBoxTop : (fullBoxHeight - boxHeight) / 2;
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
            HintGroup hintGroup = hintGroupByPrefix.get(prefix);
            if (hintGroup != null) {
                hintGroup.left = Math.min(hintGroup.left, hintBox.x());
                hintGroup.top = Math.min(hintGroup.top, hintBox.y());
                hintGroup.right = Math.max(hintGroup.right, hintBox.x() + hintBox.width());
                hintGroup.bottom = Math.max(hintGroup.bottom, hintBox.y() + hintBox.height());
            }
            addSubgridBoxes(hintBox, boxWidth, boxHeight, hintMesh.subgrid(),
                    subgridStyles, 0, qtScaleFactor);
            if (pumpDuringHintBuild && messagePump != null && (System.nanoTime() - lastPumpTime) > 30_000_000L) {
                messagePump.run();
                lastPumpTime = System.nanoTime();
            }
        }
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
            int prefixBoxBorderThickness =
                    (int) Math.round(style.prefixBoxBorderThickness());
            HintBox prefixHintBox =
                    new HintBox(null, (int) Math.round(style.prefixBoxBorderLength()),
                            prefixBoxBorderThickness,
                            QtColorUtil.qColor("#000000", 0),
                            prefixBoxBorderColor,
                            isHintPartOfGrid,
                            gridLeftEdge, gridTopEdge, gridRightEdge, gridBottomEdge,
                            true,
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
            prefixQtHintFontStyle = QtHintFont.qtHintFontStyle(style.prefixFontStyle(), null, screenScale, hasSelectedKeys);
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
                        new HintLabel(prefix, prefixXAdvancesByString, fullBoxWidth,
                                fullBoxHeight, totalXAdvance,
                                hintMesh.prefixLength(),
                                prefixQtHintFontStyle,
                                prefixHintKeyMaxXAdvance,
                                hintMesh.selectedKeySequence().size() - 1);
                int x = hintRoundedX((hintGroup.left + hintGroup.right-1) / 2d, fullBoxWidth, qtScaleFactor);
                int y = hintRoundedY((hintGroup.top + hintGroup.bottom-1) / 2d, fullBoxHeight, qtScaleFactor);
                int boxWidth = Math.max(prefixHintLabel.tightHintBoxWidth, (int) (fullBoxWidth * 1d));
                int boxHeight = Math.max(prefixHintLabel.tightHintBoxHeight, (int) (fullBoxHeight * 1d));
                prefixHintLabel.left = boxWidth == prefixHintLabel.tightHintBoxWidth ? prefixHintLabel.tightHintBoxLeft : (fullBoxWidth - boxWidth) / 2;
                prefixHintLabel.top = boxHeight == prefixHintLabel.tightHintBoxHeight ? prefixHintLabel.tightHintBoxTop : (fullBoxHeight - boxHeight) / 2;
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
        if (style.boxBorderRadius() > 0 && style.boxBorderThickness() > 0) {
            int borderPad = (int) Math.ceil(style.boxBorderThickness() / 2.0);
            minHintLeft -= borderPad;
            minHintTop -= borderPad;
            maxHintRight += borderPad;
            maxHintBottom += borderPad;
        }
        // Expand container bounds to accommodate box shadow extent.
        Shadow boxShadow = style.boxShadow();
        if (boxShadow.opacity() > 0) {
            int shadowPadLeft = (int) Math.ceil(boxShadow.blurRadius() + Math.max(0, -boxShadow.horizontalOffset()));
            int shadowPadRight = (int) Math.ceil(boxShadow.blurRadius() + Math.max(0, boxShadow.horizontalOffset()));
            int shadowPadTop = (int) Math.ceil(boxShadow.blurRadius() + Math.max(0, -boxShadow.verticalOffset()));
            int shadowPadBottom = (int) Math.ceil(boxShadow.blurRadius() + Math.max(0, boxShadow.verticalOffset()));
            minHintLeft -= shadowPadLeft;
            minHintTop -= shadowPadTop;
            maxHintRight += shadowPadRight;
            maxHintBottom += shadowPadBottom;
        }
        int offsetX = minHintLeft - hintMeshWindow.window.x();
        int offsetY = minHintTop - hintMeshWindow.window.y();
        Map<List<Key>, QRect> hintBoxGeometries = new HashMap<>();
        for (int hintIndex = 0; hintIndex < hintBoxes.size(); hintIndex++) {
            HintBox hintBox = hintBoxes.get(hintIndex);
            hintBox.move(hintBox.x() - offsetX, hintBox.y() - offsetY);
            HintLabel hintLabel = hintLabels.get(hintIndex);
            hintLabel.move(hintBox.x(), hintBox.y());
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
        int containerWidth = maxHintRight - minHintLeft;
        int containerHeight = maxHintBottom - minHintTop;
        container.setGeometry(offsetX, offsetY, containerWidth, containerHeight);
        // Layer 1: Box shadow (painted underneath boxes; empty unless shadow is active).
        HintPaintLayer boxShadowLayer = new HintPaintLayer(container, List.of(), List.of());
        boxShadowLayer.setGeometry(0, 0, containerWidth, containerHeight);
        // Layer 2: Hint boxes (with subgrid children).
        HintPaintLayer boxLayer = new HintPaintLayer(container, hintBoxes, List.of());
        boxLayer.setGeometry(0, 0, containerWidth, containerHeight);
        applyBoxShadow(boxLayer, boxShadowLayer, hintBoxes, style.boxShadow(),
                boxColor, boxBorderColor,
                (int) Math.round(style.boxBorderThickness()),
                containerWidth, containerHeight);
        // Layer 3: Prefix boxes.
        HintPaintLayer prefixBoxLayer = new HintPaintLayer(container, prefixBoxes, List.of());
        prefixBoxLayer.setGeometry(0, 0, containerWidth, containerHeight);
        // Layer 3: Prefix labels.
        HintPaintLayer prefixLabelLayer =
                new HintPaintLayer(container, List.of(), prefixLabels);
        prefixLabelLayer.setGeometry(0, 0, containerWidth, containerHeight);
        if (prefixQtHintFontStyle != null) {
            applyLabelShadow(prefixLabelLayer, prefixLabels,
                    prefixQtHintFontStyle, hasSelectedKeys,
                    containerWidth, containerHeight, screenScale);
        }
        // Layer 4: Hint labels.
        HintPaintLayer hintLabelLayer =
                new HintPaintLayer(container, List.of(), hintLabels);
        hintLabelLayer.setGeometry(0, 0, containerWidth, containerHeight);
        applyLabelShadow(hintLabelLayer, hintLabels,
                labelFontStyle, hasSelectedKeys,
                containerWidth, containerHeight, screenScale);
        return hintBoxGeometries;
    }

    /** The Qt drawing resources for one nested grid depth (subgrid, subsubgrid, ...). */
    private record SubgridStyle(QColor boxColor, QColor boxBorderColor,
                                int borderThicknessPx, int borderLengthPx, boolean closed,
                                QFont labelFont, QColor labelColor) {
    }

    private SubgridStyle subgridStyle(String borderHexColor, double borderOpacity,
                                      double borderThickness, double borderLength,
                                      boolean closed, FontStyle font) {
        return new SubgridStyle(
                QtColorUtil.qColor("#000000", 0),
                QtColorUtil.qColor(borderHexColor, borderOpacity),
                (int) Math.round(borderThickness),
                (int) Math.round(borderLength),
                closed,
                QtHintFont.qFont(font.name(), font.size(), font.weight()),
                QtColorUtil.qColor(font.hexColor(), font.opacity()));
    }

    /** Maps a nested mesh's cells proportionally into parentBox, recursing one depth deeper
     *  for each cell (subgrid boxes, then subsubgrid boxes inside those, ...). */
    private void addSubgridBoxes(HintBox parentBox, int parentWidth, int parentHeight,
                                 HintMesh subgridMesh, List<SubgridStyle> subgridStyles,
                                 int depth, double qtScaleFactor) {
        if (subgridMesh == null || depth >= subgridStyles.size())
            return;
        SubgridStyle subgridStyle = subgridStyles.get(depth);
        Rectangle refCell = subgridMesh.backgroundArea();
        for (Hint subHint : subgridMesh.hints()) {
            // Map the sub-hint's cell (in reference-cell coordinates) into the parent
            // box proportionally, so cells of any size tile cleanly.
            int subBoxLeft = (int) Math.round(
                    (subHint.centerX() - subHint.cellWidth() / 2 - refCell.x())
                    / refCell.width() * parentWidth);
            int subBoxTop = (int) Math.round(
                    (subHint.centerY() - subHint.cellHeight() / 2 - refCell.y())
                    / refCell.height() * parentHeight);
            int subBoxRight = (int) Math.round(
                    (subHint.centerX() + subHint.cellWidth() / 2 - refCell.x())
                    / refCell.width() * parentWidth);
            int subBoxBottom = (int) Math.round(
                    (subHint.centerY() + subHint.cellHeight() / 2 - refCell.y())
                    / refCell.height() * parentHeight);
            HintBox subBox = new HintBox(null, subgridStyle.borderLengthPx(),
                    subgridStyle.borderThicknessPx(), subgridStyle.boxColor(),
                    subgridStyle.boxBorderColor(), true,
                    subBoxLeft == 0, subBoxTop == 0,
                    subBoxRight == parentWidth, subBoxBottom == parentHeight,
                    subgridStyle.closed(), qtScaleFactor, 0);
            subBox.setGeometry(subBoxLeft, subBoxTop,
                    subBoxRight - subBoxLeft, subBoxBottom - subBoxTop);
            subBox.setLabel(subHint.keySequence().stream()
                            .map(Key::hintLabel).collect(Collectors.joining()),
                    subgridStyle.labelFont(), subgridStyle.labelColor());
            parentBox.subgridBoxes.add(subBox);
            addSubgridBoxes(subBox, subBoxRight - subBoxLeft, subBoxBottom - subBoxTop,
                    subgridMesh.subgrid(), subgridStyles, depth + 1, qtScaleFactor);
        }
    }

    /**
     * Sets the QImage DPI to match the target screen so that point-size fonts
     * render at the correct pixel size. Without this, text painted into off-screen
     * QImages uses the primary screen's DPI, causing wrong-sized glyphs on
     * secondary screens with different scaling.
     */
    private void setQImageDpiForScreen(QImage image, double screenScale) {
        int dotsPerMeter = (int) Math.round(screenScale * 96.0 / 0.0254);
        image.setDotsPerMeterX(dotsPerMeter);
        image.setDotsPerMeterY(dotsPerMeter);
    }

    private void cacheQtHintWindowIntoPixmap(TransparentWindow window, QWidget container,
                                                    HintMesh hintMeshKey, HintMesh hintMesh) {
        long before = System.nanoTime();
        QPixmap pixmap = container.grab();
        PixmapAndPosition pixmapAndPosition =
                new PixmapAndPosition(pixmap, container.x(), container.y(), hintMesh,
                        window.x(), window.y());
        logger.debug("Cached " + pixmapAndPosition + " in " +
                     (long) ((System.nanoTime() - before) / 1e6) + "ms (cache size is " +
                     hintMeshPixmaps.size() + ")");
//         pixmap.save("screenshot.png", "PNG");
        hintMeshPixmaps.put(hintMeshKey, pixmapAndPosition);
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

    private record PixmapAndPosition(QPixmap pixmap, int x, int y, HintMesh originalHintMesh, int windowX, int windowY) {
        @Override
        public String toString() {
            return "PixmapAndPosition[" + x + ", " + y + ", "
                   + pixmap.width() + ", " + pixmap.height() + "]";
        }
    }

    private int hintGridColumnCount(List<Hint> hints) {
        if (hints.size() == 1)
            return 1;
        double left = hints.getFirst().centerX();
        for (int i = 1; i < hints.size(); i++) {
            if (Math.abs(left - hints.get(i).centerX()) < 0.01)
                return i;
        }
        return hints.size();
    }

    private int hintRoundedX(double centerX, double cellWidth,
                                    double qtScaleFactor) {
        return (int) Math.round((centerX - cellWidth / 2) / qtScaleFactor);
    }

    private int hintRoundedY(double centerY, double cellHeight,
                                    double qtScaleFactor) {
        return (int) Math.round((centerY - cellHeight / 2) / qtScaleFactor);
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
        private final QColor color;
        private final QColor borderColor;
        private final int borderRadius;
        private int x, y, width, height;
        final List<HintBox> subgridBoxes = new ArrayList<>();
        // Optional centered label (used by subgrid hints).
        private String label;
        private QFont labelFont;
        private QColor labelColor;

        public HintBox(Hint hint, int borderLength, int borderThickness, QColor color, QColor borderColor,
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

        public void setGeometry(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public void setLabel(String label, QFont labelFont, QColor labelColor) {
            this.label = label;
            this.labelFont = labelFont;
            this.labelColor = labelColor;
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

        public void paint(QPainter painter) {
            painter.save();
            painter.translate(x, y);
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
            if (borderRadius > 0) {
                // Draw background and border as a single rounded rect so
                // the background does not bleed outside the border at corners.
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
                if (borderThickness != 0) {
                    QBrush brush = color.alpha() != 0 ? new QBrush(color) : new QBrush(Qt.BrushStyle.NoBrush);
                    painter.setBrush(brush);
                    QPen pen = createPen(borderColor, borderThickness);
                    painter.setPen(pen);
                    int offset = borderThickness / 2;
                    painter.drawRoundedRect(offset, offset,
                            width - borderThickness, height - borderThickness,
                            borderRadius, borderRadius);
                    pen.dispose();
                    brush.dispose();
                }
                else if (color.alpha() != 0) {
                    QBrush brush = new QBrush(color);
                    painter.setBrush(brush);
                    painter.setPen(Qt.PenStyle.NoPen);
                    painter.drawRoundedRect(0, 0, width, height, borderRadius, borderRadius);
                    brush.dispose();
                }
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, false);
            }
            else {
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, false);
                if (color.alpha() != 0) {
                    QBrush brush = new QBrush(color);
                    painter.setBrush(brush);
                    painter.setPen(Qt.PenStyle.NoPen);
                    painter.drawRoundedRect(0, 0, width, height, 0, 0);
                    brush.dispose();
                }
                if (borderThickness != 0)
                    drawBorders(painter);
            }
            if (label != null && !label.isEmpty() && labelFont != null) {
                painter.setFont(labelFont);
                painter.setPen(labelColor);
                painter.drawText(new QRect(0, 0, width, height),
                        Qt.AlignmentFlag.AlignCenter.value(), label);
            }
            for (HintBox subBox : subgridBoxes) {
                subBox.paint(painter);
            }
            painter.restore();
        }

        /**
         * Paints the box shape with opaque white, used as the source
         * image for shadow rendering. The overall box silhouette
         * (fill area including border thickness) is all that matters.
         */
        public void paintOpaque(QPainter painter) {
            painter.save();
            painter.translate(x, y);
            QColor opaque = new QColor(255, 255, 255, 255);
            QBrush opaqueBrush = new QBrush(opaque);
            painter.setBrush(opaqueBrush);
            painter.setPen(Qt.PenStyle.NoPen);
            if (borderRadius > 0) {
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
                painter.drawRoundedRect(0, 0, width, height, borderRadius, borderRadius);
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, false);
            }
            else {
                painter.drawRect(0, 0, width, height);
            }
            opaqueBrush.dispose();
            opaque.dispose();
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
            int bottom = height() - 1;
            int left = 0;
            int right = width() - 1;
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
            edgePen.dispose();
            insidePen.dispose();
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
            painter.setPen(isEdge ? edgePen : insidePen);
            QPen currentPen = painter.pen();
            if (currentPen.width() == 0) {
                currentPen.dispose();
                return;
            }
            currentPen.dispose();
            int x = xBase + (isEdge ? edgeOffset : insideOffset);
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
            painter.setPen(isEdge ? edgePen : insidePen);
            QPen currentPen = painter.pen();
            if (currentPen.width() == 0) {
                currentPen.dispose();
                return;
            }
            currentPen.dispose();
            int y = yBase + (isEdge ? edgeOffset : insideOffset);
            painter.drawLine(x1, y, x2, y);
        }

        private QPen createPen(QColor color, int penWidth) {
            QPen pen = new QPen(color);
            // Default is square cap.
            pen.setCapStyle(Qt.PenCapStyle.FlatCap);
            pen.setWidth(penWidth);
            return pen;
        }

    }

    public static class HintLabel {

        private final QtHintFontStyle labelFontStyle;
        private final List<HintKeyText> keyTexts;
        final int tightHintBoxLeft;
        final int tightHintBoxTop;
        final int tightHintBoxWidth;
        final int tightHintBoxHeight;
        int left;
        int top;
        int x, y, width, height;

        public HintLabel(List<Key> keySequence, Map<String, Integer> xAdvancesByString,
                         int boxWidth,
                         int boxHeight, int totalXAdvance, int prefixLength,
                         QtHintFontStyle labelFontStyle,
                         int hintKeyMaxXAdvance, int selectedKeyEndIndex) {
            this.labelFontStyle = labelFontStyle;

            int y = (boxHeight + labelFontStyle.defaultStyle().metrics().ascent() - labelFontStyle.defaultStyle().metrics().descent()) / 2;

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
            int xAdvance = 0;
            int smallestHintBoxLeft = 0;
            int smallestHintBoxWidth = 0;
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
                int textY = y;
                if (labelFontStyle.perKeyFont()) {
                    QtFontStyle qtFontStyle = resolveKeyQtFontStyle(isPrefix, isSelected, isFocused);
                    int actualTextWidth = qtFontStyle.metrics().horizontalAdvance(keyText);
                    textX += (textWidth - actualTextWidth) / 2;
                    textY = (boxHeight + qtFontStyle.metrics().ascent() - qtFontStyle.metrics().descent()) / 2;
                }
                keyTexts.add(new HintKeyText(keyText, textX, textY, keyWidth,
                        isSelected, isFocused, isPrefix));
            }
            int smallestHintBoxTop = y - labelFontStyle.defaultStyle().metrics().ascent();
            int smallestHintBoxHeight = labelFontStyle.defaultStyle().metrics().height();
            this.tightHintBoxLeft = smallestHintBoxLeft;
            this.tightHintBoxTop = smallestHintBoxTop;
            this.tightHintBoxWidth = smallestHintBoxWidth;
            this.tightHintBoxHeight = smallestHintBoxHeight;
        }

        public void setFixedSize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public void move(int x, int y) {
            this.x = x;
            this.y = y;
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
            return c.alpha() == 255 ? c : new QColor(c.red(), c.green(), c.blue(), 255);
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
                if (forceOpaque) {
                    QColor opaque = opaqueColor(color);
                    painter.setPen(opaque);
                    if (opaque != color)
                        opaque.dispose();
                }
                else {
                    painter.setPen(color);
                }
                painter.drawText(keyText.x() - left, keyText.y() - top, keyText.text());
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
            QPen outlinePen = new QPen(outlineColor);
            outlinePen.setWidth(qtFontStyle.outlineThickness());
            outlinePen.setJoinStyle(Qt.PenJoinStyle.RoundJoin);
            painter.setPen(outlinePen);
            painter.setBrush(Qt.BrushStyle.NoBrush);
            QPainterPath outlinePath = new QPainterPath();
            for (HintKeyText keyText : keyTexts) {
                if (!filter.test(keyText))
                    continue;
                outlinePath.addText(keyText.x() - left, keyText.y() - top,
                        qtFontStyle.font(), keyText.text());
            }
            painter.drawPath(outlinePath);
            outlinePath.dispose();
            outlinePen.dispose();
            if (forceOpaque && outlineColor != qtFontStyle.outlineColor())
                outlineColor.dispose();
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
                QColor opaque = opaqueColor(color);
                painter.setPen(opaque);
                if (opaque != color)
                    opaque.dispose();
                painter.drawText(keyText.x() - left, keyText.y() - top, keyText.text());
            }
            painter.restore();
        }
    }

    private record ShadowGroupKey(int r, int g, int b, int a,
                                  int stackCount, double blurRadius,
                                  double horizontalOffset, double verticalOffset) {
    }

    private record HintKeyText(String text, int x, int y, int width, boolean isSelected,
                               boolean isFocused,
                               boolean isPrefix) {

    }

    /**
     * Applies shadow to the box layer. When boxes are fully opaque and
     * stackCount == 1, uses Qt's effect directly (fast path). Otherwise,
     * pre-renders the shadow off-screen into a separate layer.
     */
    private void applyBoxShadow(HintPaintLayer boxLayer,
                                       HintPaintLayer boxShadowLayer,
                                       List<HintBox> hintBoxes,
                                       Shadow boxShadow,
                                       QColor boxColor,
                                       QColor boxBorderColor,
                                       int boxBorderThickness,
                                       int containerWidth,
                                       int containerHeight) {
        QColor shadowColor = QtColorUtil.shadow(boxShadow);
        if (shadowColor.alpha() == 0) {
            shadowColor.dispose();
            return;
        }
        boolean opaqueBox = boxColor.alpha() == 255 &&
                            (boxBorderThickness == 0 || boxBorderColor.alpha() == 255);
        if (opaqueBox && boxShadow.stackCount() == 1) {
            logger.debug("Box shadow: opaque box, applying effect directly");
            StackedShadowEffect effect = new StackedShadowEffect();
            effect.setBlurRadius(boxShadow.blurRadius());
            effect.setOffset(boxShadow.horizontalOffset(), boxShadow.verticalOffset());
            effect.setColor(shadowColor);
            effect.setStackCount(1);
            boxLayer.setGraphicsEffect(effect);
            shadowColor.dispose();
        }
        else {
            if (boxShadow.stackCount() != 1)
                logger.debug("Box shadow: shadow stack count is " +
                             boxShadow.stackCount() +
                             ", pre-rendering off-screen");
            else
                logger.debug("Box shadow: transparent box, pre-rendering off-screen");
            QImage sourceImage = new QImage(containerWidth, containerHeight,
                    QImage.Format.Format_ARGB32_Premultiplied);
            QColor fillColor = new QColor(0, 0, 0, 0);
            sourceImage.fill(fillColor);
            fillColor.dispose();
            QPainter srcPainter = new QPainter(sourceImage);
            for (HintBox box : hintBoxes) {
                box.paintOpaque(srcPainter);
            }
            srcPainter.end();
            srcPainter.dispose();
            StackedShadowEffect.ShadowImage shadow = StackedShadowEffect.renderShadowOnly(sourceImage, shadowColor,
                    boxShadow.blurRadius(), boxShadow.horizontalOffset(),
                    boxShadow.verticalOffset(), containerWidth, containerHeight);
            shadowColor.dispose();
            QImage shadowImage = StackedShadowEffect.bakeStacking(
                    shadow.image(), boxShadow.stackCount());
            QPixmap shadowPixmap = QPixmap.fromImage(shadowImage);
            boxShadowLayer.setShadowPixmap(shadowPixmap,
                    shadow.x(), shadow.y());
            shadowImage.dispose();
        }
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
        if (style.perKeyShadow()) {
            logger.debug("Hint label shadow: per-key shadow, pre-rendering per group");
            preRenderLabelShadow(layer, labels, style,
                    containerWidth, containerHeight, screenScale);
            return;
        }
        QtFontStyle defaultStyle = style.defaultStyle();
        if (defaultStyle.shadowColor().alpha() == 0)
            return;
        if (!style.hasTransparency(hasSelectedKeys) &&
            defaultStyle.shadowStackCount() == 1) {
            logger.debug("Hint label shadow: opaque text, applying effect directly");
            StackedShadowEffect effect = new StackedShadowEffect();
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
                logger.debug("Hint label shadow: transparent text, pre-rendering off-screen");
            preRenderLabelShadow(layer, labels, style,
                    containerWidth, containerHeight, screenScale);
        }
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
        // Render labels into a source image with forced opaque colors.
        QImage sourceImage = new QImage(containerWidth, containerHeight,
                QImage.Format.Format_ARGB32_Premultiplied);
        setQImageDpiForScreen(sourceImage, screenScale);
        QColor fillColor = new QColor(0, 0, 0, 0);
        sourceImage.fill(fillColor);
        fillColor.dispose();
        QPainter srcPainter = new QPainter(sourceImage);
        for (HintLabel label : labels) {
            label.paintOpaque(srcPainter);
        }
        srcPainter.end();
        srcPainter.dispose();
        StackedShadowEffect.ShadowImage shadow = StackedShadowEffect.renderShadowOnly(sourceImage, shadowStyle.shadowColor(),
                shadowStyle.shadowBlurRadius(), shadowStyle.shadowHorizontalOffset(),
                shadowStyle.shadowVerticalOffset(), containerWidth, containerHeight);
        QImage shadowImage = StackedShadowEffect.bakeStacking(shadow.image(), shadowStyle.shadowStackCount());
        QPixmap shadowPixmap = QPixmap.fromImage(shadowImage);
        layer.setShadowPixmap(shadowPixmap,
                shadow.x(), shadow.y());
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
        // 2. Render each group.
        QImage combinedShadow = null;
        int combinedX = 0, combinedY = 0;
        for (ShadowGroupKey group : groups) {
            if (group.a() == 0)
                continue;
            // Render source image with only keys matching this group.
            QImage sourceImage = new QImage(containerWidth, containerHeight,
                    QImage.Format.Format_ARGB32_Premultiplied);
            setQImageDpiForScreen(sourceImage, screenScale);
            QColor srcFillColor = new QColor(0, 0, 0, 0);
            sourceImage.fill(srcFillColor);
            srcFillColor.dispose();
            QPainter srcPainter = new QPainter(sourceImage);
            for (HintLabel label : labels) {
                label.paintOpaqueFiltered(srcPainter,
                        keyText -> label.shadowGroupKey(keyText).equals(group));
            }
            srcPainter.end();
            srcPainter.dispose();
            QColor shadowColor = new QColor(group.r(), group.g(), group.b(), group.a());
            StackedShadowEffect.ShadowImage shadow = StackedShadowEffect.renderShadowOnly(sourceImage, shadowColor,
                    group.blurRadius(), group.horizontalOffset(), group.verticalOffset(),
                    containerWidth, containerHeight);
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
                    combinedX, combinedY);
            combinedShadow.dispose();
        }
    }

    private class HintPaintLayer extends QWidget {

        private final List<HintBox> boxes;
        private final List<HintLabel> labels;
        // Pre-rendered shadow-only pixmap (null if no shadow or opaque text).
        private QPixmap shadowPixmap;
        private int shadowPixmapX, shadowPixmapY;

        HintPaintLayer(QWidget parent, List<HintBox> boxes, List<HintLabel> labels) {
            super(parent);
            this.boxes = boxes;
            this.labels = labels;
        }

        void setShadowPixmap(QPixmap shadowPixmap, int x, int y) {
            if (this.shadowPixmap != null)
                this.shadowPixmap.dispose();
            this.shadowPixmap = shadowPixmap;
            this.shadowPixmapX = x;
            this.shadowPixmapY = y;
        }

        @Override
        protected void paintEvent(QPaintEvent event) {
            QPainter painter = new QPainter(this);
            for (HintBox box : boxes) {
                box.paint(painter);
            }
            if (shadowPixmap != null) {
                painter.drawPixmap(shadowPixmapX, shadowPixmapY, shadowPixmap);
            }
            for (HintLabel label : labels) {
                label.paint(painter);
            }
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
        if (hintsByScreen.isEmpty()) // Matched hint is off-screen (grid drilled past an edge): nothing to animate.
            return;
        Screen screen = hintsByScreen.keySet().iterator().next();
        HintMeshWindow hintMeshWindow = hintMeshWindows.get(screen);
        HintMesh lastHintMeshKey = hintMeshWindow.lastHintMeshKeyReference().get();
        HintMeshStyle style =
                lastHintMeshKey.styleByFilter().get(ViewportFilter.of(screen));
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
        QWidget container = (QWidget) hintMeshWindow.window.children().getLast();
        QPixmap pixmap = container.grab(hintBoxGeometry); // This is an expensive operation.
//         pixmap.save("screenshot.png", "PNG");
        HintMesh hintMesh =
                new HintMesh.HintMeshBuilder(lastHintMeshKey).hints(List.of(hint))
                                                             .build();
        QRect containerGeom = container.geometry();
        PixmapAndPosition pixmapAndPosition =
                new PixmapAndPosition(pixmap,
                        containerGeom.x() + hintBoxGeometry.x(),
                        containerGeom.y() + hintBoxGeometry.y(), hintMesh,
                        hintMeshWindow.window.x(), hintMeshWindow.window.y());
        containerGeom.dispose();
        setHintMeshWindow(hintMeshWindow, hintMesh, -1, style, false, pixmapAndPosition);
    }

}

package mousemaster;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import io.qt.core.*;
import io.qt.gui.*;
import io.qt.widgets.QApplication;
import io.qt.widgets.QGraphicsDropShadowEffect;
import io.qt.widgets.QLabel;
import io.qt.widgets.QWidget;
import mousemaster.WindowsMouse.MouseSize;
import mousemaster.qt.TransparentWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class WindowsOverlay {

    private static final Logger logger = LoggerFactory.getLogger(WindowsOverlay.class);

    private static final int indicatorEdgeThreshold = 100; // in pixels
    public static boolean waitForZoomBeforeRepainting;

    private static IndicatorWindow indicatorWindow;
    private static boolean showingIndicator;
    private static Indicator currentIndicator;
    private static GridWindow gridWindow, standByGridWindow;
    private static boolean standByGridCanBeHidden;
    private static boolean showingGrid;
    private static Grid currentGrid;
    private static final Map<Screen, HintMeshWindow> hintMeshWindows =
            new LinkedHashMap<>(); // Ordered for topmost handling.
    private static final Map<HintMesh, PixmapAndPosition> hintMeshPixmaps = new HashMap<>();
    private static boolean showingHintMesh;
    private static boolean hintMeshEndAnimation;
    private static boolean zoomAfterHintMeshEndAnimation;
    private static Zoom afterHintMeshEndAnimationZoom;
    private static HintMesh currentHintMesh;
    private static ZoomWindow zoomWindow, standByZoomWindow;
    private static Zoom currentZoom;
    private static boolean mustUpdateMagnifierSource;
    /**
     * Building the hint window is expensive and when it is done from the keyboard hook,
     * Windows will cancel the hook and the key press will go through to the other apps.
     * Windows won't wait for the keyboard hook to return if it's taking too long.
     */
    private static Runnable setUncachedHintMeshWindowRunnable;

    public static void update(double delta) {
        if (setUncachedHintMeshWindowRunnable != null) {
            setUncachedHintMeshWindowRunnable.run();
            setUncachedHintMeshWindowRunnable = null;
        }
        updateZoomWindow();
    }

    private static void updateZoomWindow() {
        if (currentZoom == null)
            return;
        if (mustUpdateMagnifierSource) {
            mustUpdateMagnifierSource = false;
            WinDef.RECT sourceRect = new WinDef.RECT();
            Zoom zoom = currentZoom;
            Rectangle screenRectangle = zoom.screenRectangle();
            double zoomPercent = zoom.percent();
            sourceRect.left = (int) (zoom.center().x() - screenRectangle.width() / zoomPercent / 2);
            sourceRect.top = (int) (zoom.center().y() - screenRectangle.height() / zoomPercent / 2);
            sourceRect.right = (int) (zoom.center().x() + screenRectangle.width() / zoomPercent / 2);
            sourceRect.bottom = (int) (zoom.center().y() + screenRectangle.height() / zoomPercent / 2);
            // Calls to MagSetWindowSource are expensive and last about 10-20ms.
            if (!Magnification.INSTANCE.MagSetWindowSource(zoomWindow.hwnd(),
                    sourceRect)) {
                logger.error("Failed MagSetWindowSource: " +
                             Integer.toHexString(Native.getLastError()));
            }
        }
        User32.INSTANCE.ShowWindow(zoomWindow.hostHwnd(), WinUser.SW_SHOWNORMAL);
        User32.INSTANCE.InvalidateRect(zoomWindow.hwnd(), null, true);
        if (standByZoomWindow != null)
            User32.INSTANCE.ShowWindow(standByZoomWindow.hostHwnd(), WinUser.SW_HIDE);
        // Without a setTopmost() call here, the Zoom window would be displayed on top
        // of the indicator window for a single frame.
        setTopmost();
    }

    public static void flushCache() {
        for (PixmapAndPosition pixmapAndPosition : hintMeshPixmaps.values())
            pixmapAndPosition.pixmap().dispose();
        hintMeshPixmaps.clear();
    }

    public static Rectangle activeWindowRectangle(double windowWidthPercent,
                                                  double windowHeightPercent,
                                                  int scaledTopInset,
                                                  int scaledBottomInset,
                                                  int scaledLeftInset,
                                                  int scaledRightInset) {
        WinDef.HWND foregroundWindow = User32.INSTANCE.GetForegroundWindow();
        // https://stackoverflow.com/a/65605845
        WinDef.RECT excludeShadow = windowRectExcludingShadow(foregroundWindow);
        int windowWidth = excludeShadow.right - excludeShadow.left;
        int windowHeight = excludeShadow.bottom - excludeShadow.top;
        int noInsetGridWidth = Math.max(1, (int) (windowWidth * windowWidthPercent));
        int gridWidth =
                Math.max(1, noInsetGridWidth - scaledLeftInset - scaledRightInset);
        int noInsetGridHeight = Math.max(1, (int) (windowHeight * windowHeightPercent));
        int gridHeight =
                Math.max(1, noInsetGridHeight - scaledTopInset - scaledBottomInset);
        return new Rectangle(Math.min(excludeShadow.right,
                excludeShadow.left + scaledLeftInset +
                (windowWidth - noInsetGridWidth) / 2), Math.min(excludeShadow.bottom,
                excludeShadow.top + scaledTopInset +
                (windowHeight - noInsetGridHeight) / 2), gridWidth, gridHeight);
    }

    private static WinDef.RECT windowRectExcludingShadow(WinDef.HWND hwnd) {
        // On Windows 10+, DwmGetWindowAttribute() returns the extended frame bounds excluding shadow.
        WinDef.RECT rect = new WinDef.RECT();
        Dwmapi.INSTANCE.DwmGetWindowAttribute(hwnd, Dwmapi.DWMWA_EXTENDED_FRAME_BOUNDS,
                rect, rect.size());
        return rect;
    }

    public static void setTopmost() {
        List<WinDef.HWND> hwnds = new ArrayList<>();
        // First in the hwnds list means drawn on top.
        if (gridWindow != null)
            hwnds.add(gridWindow.hwnd);
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values()) {
            hwnds.add(hintMeshWindow.hwnd);
        }
        if (indicatorWindow != null)
            hwnds.add(indicatorWindow.hwnd);
        if (zoomWindow != null) {
            hwnds.add(zoomWindow.hostHwnd);
        }
        if (hwnds.isEmpty())
            return;
        setWindowTopmost(hwnds.getFirst(), ExtendedUser32.HWND_TOPMOST);
        boolean allOtherWindowsAreBelowInOrder = true;
        for (int windowIndex = 0; windowIndex < hwnds.size() - 1; windowIndex++) {
            if (windowBelow(hwnds.get(windowIndex)).equals(hwnds.get(windowIndex + 1)))
                // For example, windowBelow(indicator).equals(grid).
                continue;
            allOtherWindowsAreBelowInOrder = false;
            break;
        }
        if (allOtherWindowsAreBelowInOrder)
            return;
        for (int windowIndex = hwnds.size() - 1; windowIndex >= 0; windowIndex--)
            setWindowTopmost(hwnds.get(windowIndex), ExtendedUser32.HWND_TOPMOST);
    }

    private static WinDef.HWND windowBelow(WinDef.HWND hwnd) {
        WinDef.HWND nextHwnd =
                User32.INSTANCE.GetWindow(hwnd, new WinDef.DWORD(User32.GW_HWNDNEXT));
        return nextHwnd;
    }

    private static void setWindowTopmost(WinDef.HWND hwnd, WinDef.HWND hwndTopmost) {
        User32.INSTANCE.SetWindowPos(hwnd, hwndTopmost, 0, 0, 0, 0,
                WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE);
    }

    private record IndicatorWindow(WinDef.HWND hwnd, WinUser.WindowProc callback, int transparentColor) {

    }

    private record GridWindow(WinDef.HWND hwnd, WinUser.WindowProc callback, int transparentColor) {

    }

    private record HintMeshWindow(WinDef.HWND hwnd,
                                  TransparentWindow window,
                                  List<Hint> hints, Zoom zoom,
                                  List<QVariantAnimation> animations,
                                  List<QMetaObject.AbstractSlot> animationCallbacks) {

    }

    private record ZoomWindow(WinDef.HWND hwnd, WinDef.HWND hostHwnd, WinUser.WindowProc callback) {

    }

    private static int indicatorSize() {
        return indicatorSize(WindowsMouse.findMousePosition());
    }

    private static int indicatorSize(WinDef.POINT mousePosition) {
        Screen activeScreen = WindowsScreen.findActiveScreen(mousePosition);
        return scaledPixels(currentIndicator.size(), activeScreen.scale());
    }

    private static int bestIndicatorX() {
        return bestIndicatorX(WindowsMouse.findMousePosition());
    }

    private static int bestIndicatorX(WinDef.POINT mousePosition) {
        Screen activeScreen = WindowsScreen.findActiveScreen(mousePosition);
        MouseSize mouseSize = WindowsMouse.mouseSize();
        return (int) Math.round(zoomedX(bestIndicatorX(mousePosition.x, mouseSize.width(),
                activeScreen.rectangle(), indicatorSize(mousePosition))));
    }

    private static double zoomedX(double x) {
        if (currentZoom == null)
            return x;
        return currentZoom.zoomedX(x);
    }

    private static double zoomedY(double y) {
        if (currentZoom == null)
            return y;
        return currentZoom.zoomedY(y);
    }

    private static int bestIndicatorY() {
        return bestIndicatorY(WindowsMouse.findMousePosition());
    }

    private static int bestIndicatorY(WinDef.POINT mousePosition) {
        Screen activeScreen = WindowsScreen.findActiveScreen(mousePosition);
        MouseSize mouseSize = WindowsMouse.mouseSize();
        return (int) Math.round(zoomedY(bestIndicatorY(mousePosition.y, mouseSize.height(),
                activeScreen.rectangle(), indicatorSize(mousePosition))));
    }

    private static int bestIndicatorX(int mouseX, int cursorWidth, Rectangle screenRectangle,
                                      int scaledIndicatorSize) {
        mouseX = Math.min(screenRectangle.x() + screenRectangle.width(),
                Math.max(screenRectangle.x(), mouseX));
        boolean isNearLeftEdge = mouseX <= (screenRectangle.x() + indicatorEdgeThreshold);
        boolean isNearRightEdge = mouseX >=
                                  (screenRectangle.x() + screenRectangle.width() -
                                   indicatorEdgeThreshold);
        if (isNearRightEdge)
            return mouseX - scaledIndicatorSize;
        return mouseX + cursorWidth / 2;
    }

    private static int bestIndicatorY(int mouseY, int cursorHeight, Rectangle screenRectangle,
                                      int scaledIndicatorSize) {
        mouseY = Math.min(screenRectangle.y() + screenRectangle.height(),
                Math.max(screenRectangle.y(), mouseY));
        boolean isNearBottomEdge = mouseY >=
                                   (screenRectangle.y() + screenRectangle.height() -
                                    indicatorEdgeThreshold);
        boolean isNearTopEdge = mouseY <= (screenRectangle.y() + indicatorEdgeThreshold);
        if (isNearBottomEdge)
            return mouseY - scaledIndicatorSize;
        return mouseY + cursorHeight / 2;
    }

    private static void createIndicatorWindow() {
        WinUser.WindowProc callback = WindowsOverlay::indicatorWindowCallback;
        // +1 width and height because no line can be drawn on y = windowHeight and y = windowWidth.
        WinDef.HWND hwnd = createWindow("Indicator",
                bestIndicatorX(),
                bestIndicatorY(),
                indicatorSize() + 1,
                indicatorSize() + 1, callback);
        indicatorWindow = new IndicatorWindow(hwnd, callback, 0);
        updateZoomExcludedWindows();
    }

    private static int scaledPixels(double originalInPixels, double scale) {
        return (int) Math.floor(originalInPixels * scale * zoomPercent());
    }

    private static double zoomPercent() {
        if (currentZoom == null)
            return 1;
        return currentZoom.percent();
    }

    private static void createGridWindow(int x, int y, int width, int height) {
        WinUser.WindowProc callback = WindowsOverlay::gridWindowCallback;
        WinDef.HWND hwnd =
                createWindow("Grid" + (gridWindow == null ? 1 : 2), x, y, width, height,
                        callback);
        gridWindow = new GridWindow(hwnd, callback, 0);
        updateZoomExcludedWindows();
    }

    private static void createOrUpdateHintMeshWindows(HintMesh hintMesh, Zoom zoom) {
        Set<Screen> screens = WindowsScreen.findScreens();
        Map<Screen, List<Hint>> hintsByScreen = new HashMap<>();
        for (Hint hint : hintMesh.hints()) {
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
        for (Map.Entry<Screen, HintMeshWindow> entry : hintMeshWindows.entrySet()) {
            Screen screen = entry.getKey();
            HintMeshWindow window = entry.getValue();
            if (!hintsByScreen.containsKey(screen))
                window.hints.clear();
        }
        boolean createdAtLeastOneWindow = false;
        for (Map.Entry<Screen, List<Hint>> entry : hintsByScreen.entrySet()) {
            Screen screen = entry.getKey();
            HintMeshStyle style =
                    hintMesh.styleByFilter().get(ViewportFilter.of(screen));
            List<Hint> hintsInScreen = entry.getValue();
            HintMeshWindow existingWindow = hintMeshWindows.get(screen);
            if (existingWindow == null) {
//                for (QScreen qScreen : QGuiApplication.screens()) {
//                    QRect availableGeometry = qScreen.availableGeometry();
//                    logger.info("QScreen " + availableGeometry.x() + " " + availableGeometry.y() + " " + availableGeometry.width() + " " + availableGeometry.height());
//                }
                TransparentWindow window = new TransparentWindow();
                WinDef.HWND hwnd = new WinDef.HWND(new Pointer(window.winId()));
                long currentStyle =
                        User32.INSTANCE.GetWindowLongPtr(hwnd, WinUser.GWL_EXSTYLE)
                                       .longValue();
                long newStyle = currentStyle | ExtendedUser32.WS_EX_NOACTIVATE |
                                ExtendedUser32.WS_EX_TOOLWINDOW |
                                ExtendedUser32.WS_EX_LAYERED | ExtendedUser32.WS_EX_TRANSPARENT;
                User32.INSTANCE.SetWindowLongPtr(hwnd, WinUser.GWL_EXSTYLE,
                        new Pointer(newStyle));
                window.move(screen.rectangle().x(), screen.rectangle().y());
                window.resize(screen.rectangle().width(), screen.rectangle().height());
                HintMeshWindow hintMeshWindow =
                        new HintMeshWindow(hwnd, window, hintsInScreen, zoom,
                                new ArrayList<>(), new ArrayList<>());
                hintMeshWindows.put(screen, hintMeshWindow);
                createdAtLeastOneWindow = true;
//                logger.debug("Showing hints " + hintsInScreen.size() + " for " + screen + ", window = " + window.x() + " " + window.y() + " " + window.width() + " " + window.height());
                setHintMeshWindow(hintMeshWindow, hintMesh, screen.scale(), style, false);
            }
            else {
                HintMeshWindow hintMeshWindow = new HintMeshWindow(existingWindow.hwnd,
                        existingWindow.window,
                        hintsInScreen, zoom, existingWindow.animations(),
                        existingWindow.animationCallbacks());
                boolean oldContainerIsHidden = !existingWindow.window.children()
                                                                     .isEmpty() &&
                                               ((QWidget) existingWindow.window.children().getFirst()).isHidden();
                boolean oldContainerHasSameHints = !oldContainerIsHidden &&
                                                   existingWindow.zoom.equals(zoom) &&
                                                   existingWindow.hints.equals(
                                                           hintsInScreen);
                hintMeshWindows.put(screen, hintMeshWindow);
//                TransparentWindow window = existingWindow.window;
//                logger.debug("Showing hints " + hintsInScreen.size() + " for " + screen + ", window = " + window.x() + " " + window.y() + " " + window.width() + " " + window.height());
                setHintMeshWindow(hintMeshWindow, hintMesh, screen.scale(), style, oldContainerHasSameHints);
            }
        }
        if (createdAtLeastOneWindow)
            updateZoomExcludedWindows();
    }

    private static class ClearBackgroundQLabel extends QLabel {
        @Override
        protected void paintEvent(QPaintEvent event) {
            QPainter painter = new QPainter(this);
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Source);
            // Clear what's behind (when we're drawing the old container behind).
            painter.fillRect(rect(), Qt.GlobalColor.transparent);
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
            super.paintEvent(event);
        }
    }

    private static void setHintMeshWindow(HintMeshWindow hintMeshWindow,
                                          HintMesh hintMesh, double screenScale,
                                          HintMeshStyle style,
                                          boolean oldContainerHasSameHints) {
        setUncachedHintMeshWindowRunnable = null;
        int transitionAnimationCurrentTime =
                hintMeshWindow.animations.stream()
                                         .filter(animation -> animation.getState() ==
                                                              QAbstractAnimation.State.Running)
                                         .map(QAbstractAnimation::getCurrentTime)
                                         .findFirst()
                                         .orElse(0);
        for (QVariantAnimation animation : hintMeshWindow.animations) {
            animation.stop();
        }
        hintMeshWindow.animations.clear();
        hintMeshWindow.animationCallbacks.clear();
        // When QT_ENABLE_HIGHDPI_SCALING is not 0 (e.g. Linux/macOS), then
        // devicePixelRatio will be the screen's scale.
        double qtScaleFactor = QApplication.primaryScreen().devicePixelRatio();
        TransparentWindow window = hintMeshWindow.window;
        QWidget oldContainer =
                window.children().isEmpty() ? null :
                        (QWidget) window.children().getFirst();
        window.clearWindow();
        HintMesh hintMeshKey = new HintMesh.HintMeshBuilder(hintMesh)
                .hints(trimmedHints(hintMeshWindow.hints(), hintMesh.focusedKeySequence()))
                .build();
        PixmapAndPosition pixmapAndPosition = hintMeshPixmaps.get(hintMeshKey);
        boolean isHintGrid = hintMeshWindow.hints().getFirst().cellWidth() != -1;
        QWidget newContainer;
        if (pixmapAndPosition != null) {
            logger.trace("Using cached pixmap " + pixmapAndPosition);
            QLabel pixmapLabel = new ClearBackgroundQLabel();
            pixmapLabel.setPixmap(pixmapAndPosition.pixmap);
            pixmapLabel.setGeometry(pixmapAndPosition.x(), pixmapAndPosition.y(), pixmapAndPosition.pixmap().width(), pixmapAndPosition.pixmap().height());
            newContainer = pixmapLabel;
            transitionHintContainers(
                    style.transitionAnimationEnabled() && isHintGrid && oldContainerHasSameHints,
                    oldContainer, newContainer,
                    window, hintMeshWindow,
                    style.transitionAnimationDuration(), transitionAnimationCurrentTime);
        }
        else {
            QWidget container = new QWidget();
            container.setStyleSheet("background: transparent;");
            newContainer = container;
            setUncachedHintMeshWindowRunnable =
                    () -> {
                        setUncachedHintMeshWindow(hintMeshWindow, hintMesh,
                                screenScale,
                                style, qtScaleFactor,
                                container
                        );
                        if (isHintGrid) {
                            cacheQtHintWindowIntoPixmap(container, hintMeshKey);
                        }
                        transitionHintContainers(
                                style.transitionAnimationEnabled() && isHintGrid && oldContainerHasSameHints,
                                oldContainer, newContainer,
                                window, hintMeshWindow,
                                style.transitionAnimationDuration(), transitionAnimationCurrentTime);
                    };
            if (!isHintGrid // They are not cached anyway.
                || !hintMesh.focusedKeySequence().isEmpty() // To avoid an empty frame.
                    || hintMesh.hints().size() < 100 // To avoid an empty frame.
            ) {
                setUncachedHintMeshWindowRunnable.run();
                setUncachedHintMeshWindowRunnable = null;
            }
        }
    }

    private static void transitionHintContainers(boolean animateTransition, QWidget oldContainer,
                                                 QWidget newContainer, TransparentWindow window,
                                                 HintMeshWindow hintMeshWindow,
                                                 Duration animationDuration,
                                                 int animationCurrentTime) {
        if (oldContainer != null) {
            boolean containersEqual = oldContainer.rect().equals(newContainer.rect());
            if (animateTransition && oldContainer.rect().contains(newContainer.rect())) {
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
                HintContainerAnimationChanged animationChanged = new HintContainerAnimationChanged(
                        oldContainer);
                animation.valueChanged.connect(animationChanged);
                HintContainerAnimationFinished animationFinished =
                        new HintContainerAnimationFinished(oldContainer, oldContainer,
                                endRect);
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
            else if (animateTransition && newContainer.rect().contains(oldContainer.rect())) {
                // Initially show new container with the position and size of old.
                // Then grow new container until it reaches its final position and size.
                newContainer.setParent(window);
                newContainer.show();
                QRect beginRect =
                        new QRect(oldContainer.x(), oldContainer.y(),
                                oldContainer.width(),
                                oldContainer.height());
                QRect endRect =
                        new QRect(newContainer.x(), newContainer.y(),
                                newContainer.width(),
                                newContainer.height());
                newContainer.setMask(new QRegion(beginRect));
                QVariantAnimation animation = hintContainerAnimation(beginRect, endRect,
                        animationDuration);
                HintContainerAnimationChanged animationChanged =
                        new HintContainerAnimationChanged(newContainer);
                animation.valueChanged.connect(animationChanged);
                HintContainerAnimationFinished animationFinished =
                        new HintContainerAnimationFinished(null, newContainer,
                                endRect);
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

    public static class HintContainerAnimationChanged implements QMetaObject.Slot1<Object> {

        private final QWidget container;

        public HintContainerAnimationChanged(QWidget container) {
            this.container = container;
        }

        @Override
        public void invoke(Object arg) {
            QRect r = (QRect) arg;
            container.setMask(new QRegion(r));
        }
    }

    public static class HintContainerAnimationFinished implements QMetaObject.Slot0 {

        private final QWidget oldContainer;
        private final QWidget animatedContainer;
        private final QRect endRect;

        public HintContainerAnimationFinished(QWidget oldContainer, QWidget animatedContainer,
                                              QRect endRect) {
            this.oldContainer = oldContainer;
            this.animatedContainer = animatedContainer;
            this.endRect = endRect;
        }

        @Override
        public void invoke() {
            animatedContainer.setMask(new QRegion(endRect)); // animatedContainer can be the oldContainer.
            if (oldContainer != null) {
                oldContainer.setParent(null);
                oldContainer.disposeLater();
            }
            hintContainerAnimationEnded();
        }
    }

    private static void hintContainerAnimationEnded() {
        if (hintMeshEndAnimation) {
            hintMeshEndAnimation = false;
            hideHintMesh();
            if (zoomAfterHintMeshEndAnimation) {
                zoomAfterHintMeshEndAnimation = false;
                setZoom(afterHintMeshEndAnimationZoom);
                afterHintMeshEndAnimationZoom = null;
            }
        }
    }

    private static QVariantAnimation hintContainerAnimation(QRect beginRect,
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

    private static class HintGroup {

        double minHintCenterX = Double.MAX_VALUE;
        double minHintCenterY = Double.MAX_VALUE;
        double maxHintCenterX = 0;
        double maxHintCenterY = 0;

    }

    private static void setUncachedHintMeshWindow(HintMeshWindow hintMeshWindow, HintMesh hintMesh,
                                                  double screenScale, HintMeshStyle style,
                                                  double qtScaleFactor,
                                                  QWidget container) {
        boolean isHintPartOfGrid = hintMeshWindow.hints().getFirst().cellWidth() != -1;
        double minHintCenterX = Double.MAX_VALUE;
        double minHintCenterY = Double.MAX_VALUE;
        double maxHintCenterX = 0;
        double maxHintCenterY = 0;
        Map<Key, HintGroup> hintGroupByPrefix = new HashMap<>();
        for (Hint hint : hintMeshWindow.hints()) {
            if (!hint.startsWith(hintMesh.focusedKeySequence()))
                continue;
            minHintCenterX = Math.min(minHintCenterX, hint.centerX());
            minHintCenterY = Math.min(minHintCenterY, hint.centerY());
            maxHintCenterX = Math.max(maxHintCenterX, hint.centerX());
            maxHintCenterY = Math.max(maxHintCenterY, hint.centerY());
            HintGroup hintGroup =
                    hintGroupByPrefix.computeIfAbsent(hint.keySequence().getFirst(),
                            key -> new HintGroup());
            hintGroup.minHintCenterX = Math.min(hintGroup.minHintCenterX, hint.centerX());
            hintGroup.minHintCenterY = Math.min(hintGroup.minHintCenterY, hint.centerY());
            hintGroup.maxHintCenterX = Math.max(hintGroup.maxHintCenterX, hint.centerX());
            hintGroup.maxHintCenterY = Math.max(hintGroup.maxHintCenterY, hint.centerY());
        }
        List<Hint> hints = hintMeshWindow.hints;
        int minHintLeft = Integer.MAX_VALUE;
        int minHintTop = Integer.MAX_VALUE;
        int maxHintRight = Integer.MIN_VALUE;
        int maxHintBottom = Integer.MIN_VALUE;
        Map<String, Integer> xAdvancesByString = new HashMap<>();
        QFont font =
                new QFont(style.fontName(), (int) Math.round(style.fontSize()),
                        style.fontWeight().qtWeight().value());
        font.setStyleStrategy(QFont.StyleStrategy.PreferAntialias);
        font.setHintingPreference(QFont.HintingPreference.PreferFullHinting); // Full hinting for crisp text
        QFontMetrics metrics = new QFontMetrics(font);
        int hintKeyMaxXAdvance = 0;
        for (Hint hint : hints) {
            List<Key> keySequence = hint.keySequence();
            for (Key key : keySequence) {
                String keyText = key.hintLabel();
                hintKeyMaxXAdvance = Math.max(hintKeyMaxXAdvance,
                        xAdvancesByString.computeIfAbsent(keyText,
                                metrics::horizontalAdvance));
            }
        }
//            hintKeyMaxXAdvance = metrics.maxWidth();
        QColor fontColor = qColor(style.fontHexColor(), style.fontOpacity());
        QColor prefixColor = qColor(style.prefixFontHexColor(), style.fontOpacity());
        QColor outlineColor = qColor(style.fontOutlineHexColor(), style.fontOutlineOpacity());
        QColor shadowColor = qColor(style.fontShadowHexColor(), style.fontShadowOpacity());
        QColor boxColor = qColor(style.boxHexColor(), style.boxOpacity());
        QColor boxBorderColor = qColor(style.boxBorderHexColor(),
                style.boxBorderOpacity());
        QColor subgridBoxColor = qColor("#000000", 0);
        QColor subgridBoxBorderColor = qColor(style.subgridBorderHexColor(),
                style.subgridBorderOpacity());
        int hintGridColumnCount = isHintPartOfGrid ? hintGridColumnCount(hintMeshWindow.hints()) : -1;
        for (int hintIndex = 0; hintIndex < hints.size(); hintIndex++) {
            Hint hint = hints.get(hintIndex);
            if (!hint.startsWith(hintMesh.focusedKeySequence()))
                continue;
            int totalXAdvance = metrics.horizontalAdvance(hint.keySequence()
                                                              .stream()
                                                              .map(Key::hintLabel)
                                                              .collect(
                                                                      Collectors.joining()));
            // Size of cell for screen selection hint is not configured by user.
            // The default size is used and it is too small (and will be less than totalXAdvance).
            double cellWidth = hint.cellWidth() != -1 ?
                    Math.max(totalXAdvance, hint.cellWidth()) :
                    totalXAdvance;
            int lineHeight = metrics.height();
            double cellHeight = hint.cellHeight() != -1 ?
                    Math.max(lineHeight, hint.cellHeight()) :
                    lineHeight;
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
            HintLabel hintLabel =
                    new HintLabel(hint, font, xAdvancesByString, fullBoxWidth,
                            fullBoxHeight, totalXAdvance,
                            fontColor,
                            prefixColor,
                            outlineColor,
                            (int) Math.round(style.fontOutlineThickness() * screenScale),
                            shadowColor,
                            style.fontShadowBlurRadius() * screenScale,
                            style.fontShadowHorizontalOffset() * screenScale,
                            style.fontShadowVerticalOffset() * screenScale,
                            hintMesh.focusedKeySequence(),
                            style.fontSpacingPercent(),
                            hintKeyMaxXAdvance, metrics);
            int boxBorderThickness = (int) Math.round(style.boxBorderThickness());
            HintGroup hintGroup = hintGroupByPrefix.get(hint.keySequence().getFirst());
            boolean groupLeftEdge = hint.centerX() == hintGroup.minHintCenterX;
            boolean groupTopEdge = hint.centerY() == hintGroup.minHintCenterY;
            boolean groupRightEdge = hint.centerX() == hintGroup.maxHintCenterX;
            boolean groupBottomEdge = hint.centerY() == hintGroup.maxHintCenterY;
            boolean gridLeftEdge = isHintPartOfGrid && hint.centerX() == minHintCenterX || style.boxWidthPercent() != 1;
            boolean gridTopEdge = isHintPartOfGrid && hint.centerY() == minHintCenterY || style.boxHeightPercent() != 1;
            boolean gridRightEdge = isHintPartOfGrid && hint.centerX() == maxHintCenterX || style.boxWidthPercent() != 1;
            boolean gridBottomEdge = isHintPartOfGrid && hint.centerY() == maxHintCenterY || style.boxHeightPercent() != 1;
            HintBox hintBox =
                    new HintBox((int) Math.round(style.boxBorderLength()),
                            boxBorderThickness,
                            boxColor,
                            boxBorderColor,
                            isHintPartOfGrid,
                            gridLeftEdge, gridTopEdge, gridRightEdge, gridBottomEdge,
                            true,
                            groupLeftEdge, groupTopEdge, groupRightEdge, groupBottomEdge,
                            qtScaleFactor
                    );
            HintBox[][] subgridBoxes = addSubgridBoxes(hintBox, qtScaleFactor,
                    subgridBoxColor,
                    subgridBoxBorderColor,
                    style.subgridRowCount(), style.subgridColumnCount(),
                    style.subgridBorderLength(), style.subgridBorderThickness());
            hintLabel.setParent(hintBox);
            hintLabel.move(0, 0);
            int boxWidth = Math.max(hintLabel.tightHintBoxWidth, (int) (fullBoxWidth * style.boxWidthPercent()));
            int boxHeight = Math.max(hintLabel.tightHintBoxHeight, (int) (fullBoxHeight * style.boxHeightPercent()));
            hintLabel.left = boxWidth == hintLabel.tightHintBoxWidth ? hintLabel.tightHintBoxLeft : (fullBoxWidth - boxWidth) / 2;
            hintLabel.top = boxHeight == hintLabel.tightHintBoxHeight ? hintLabel.tightHintBoxTop : (fullBoxHeight - boxHeight) / 2;
            x += hintLabel.left;
            y += hintLabel.top;
            minHintLeft = Math.min(minHintLeft, x);
            minHintTop = Math.min(minHintTop, y);
            maxHintRight = Math.max(maxHintRight, x + fullBoxWidth);
            maxHintBottom = Math.max(maxHintBottom, y + fullBoxHeight);
            hintBox.setParent(container);
            hintBox.setGeometry(x - hintMeshWindow.window().x(), y - hintMeshWindow.window.y(), boxWidth, boxHeight);
            hintLabel.setFixedSize(boxWidth, boxHeight);
            for (int subgridRowIndex = 0;
                 subgridRowIndex < style.subgridRowCount(); subgridRowIndex++) {
                for (int subgridColumnIndex = 0; subgridColumnIndex <
                                                 style.subgridColumnCount(); subgridColumnIndex++) {
                    HintBox subBox =
                            subgridBoxes[subgridRowIndex][subgridColumnIndex];
                    int subBoxWidth = boxWidth / style.subgridColumnCount();
                    int subBoxHeight = boxHeight / style.subgridRowCount();
                    int subBoxX = subBoxWidth * subgridColumnIndex;
                    int subBoxY = subBoxHeight * subgridRowIndex;
                    subBox.setGeometry(subBoxX, subBoxY, subBoxWidth, subBoxHeight);
                }
            }
            hintBox.show();
        }
        for (QObject child : container.children()) {
            HintBox hintBox = (HintBox) child;
            hintBox.move(
                    hintBox.x() - (minHintLeft - hintMeshWindow.window.x()),
                    hintBox.y() - (minHintTop - hintMeshWindow.window.y())
            );
        }
        container.setGeometry(minHintLeft - hintMeshWindow.window.x(),
                minHintTop - hintMeshWindow.window.y(),
                maxHintRight - minHintLeft + 1,
                maxHintBottom - minHintTop + 1);
    }

    private static void cacheQtHintWindowIntoPixmap(QWidget container,
                                                    HintMesh hintMeshKey) {
        QPixmap pixmap = container.grab();
        PixmapAndPosition pixmapAndPosition =
                new PixmapAndPosition(pixmap, container.x(), container.y());
        logger.trace("Caching " + pixmapAndPosition + ", cache size is " + hintMeshPixmaps.size());
        // pixmap.save("screenshot.png", "PNG");
        hintMeshPixmaps.put(hintMeshKey, pixmapAndPosition);
    }

    private static List<Hint> trimmedHints(List<Hint> hints,
                                           List<Key> focusedKeySequence) {
        double minHintCenterX = Double.MAX_VALUE;
        double minHintCenterY = Double.MAX_VALUE;
        for (Hint hint : hints) {
            if (!hint.startsWith(focusedKeySequence))
                continue;
            minHintCenterX = Math.min(minHintCenterX, hint.centerX());
            minHintCenterY = Math.min(minHintCenterY, hint.centerY());
        }
        if (minHintCenterX == 0 && minHintCenterY == 0)
            return hints;
        List<Hint> trimmedHints = new ArrayList<>();
        for (Hint hint : hints) {
            if (!hint.startsWith(focusedKeySequence))
                continue;
            trimmedHints.add(new Hint(hint.centerX() - minHintCenterX,
                    hint.centerY() - minHintCenterY,
                    hint.cellWidth(), hint.cellHeight(), hint.keySequence()));
        }
        return trimmedHints;
    }

    private static HintBox[][] addSubgridBoxes(HintBox hintBox,
                                               double qtScaleFactor, QColor subgridBoxColor,
                                               QColor subgridBoxBorderColor,
                                               int subgridRowCount,
                                               int subgridColumnCount,
                                               double subgridBorderLength,
                                               double subgridBorderThickness) {
        HintBox[][] hintBoxes = new HintBox[subgridRowCount][subgridColumnCount];
        for (int subgridRowIndex = 0; subgridRowIndex <
                                      subgridRowCount; subgridRowIndex++) {
            for (int subgridColumnIndex = 0; subgridColumnIndex <
                                             subgridColumnCount; subgridColumnIndex++) {
                boolean gridLeftEdge = subgridColumnIndex == 0;
                boolean gridTopEdge = subgridRowIndex == 0;
                boolean gridRightEdge = subgridColumnIndex == subgridColumnCount - 1;
                boolean gridBottomEdge = subgridRowIndex == subgridRowCount - 1;
                HintBox subBox = new HintBox(
                        (int) Math.round(subgridBorderLength),
                        (int) Math.round(subgridBorderThickness),
                        subgridBoxColor, // Transparent.
                        subgridBoxBorderColor,
                        true,
                        gridLeftEdge, gridTopEdge, gridRightEdge, gridBottomEdge,
                        false,
                        false, false, false, false,
                        qtScaleFactor
                );
                subBox.setParent(hintBox);
                hintBoxes[subgridRowIndex][subgridColumnIndex] = subBox;
            }
        }
        return hintBoxes;
    }

    private record PixmapAndPosition(QPixmap pixmap, int x, int y) {
        @Override
        public String toString() {
            return "PixmapAndPosition[" + x + ", " + y + ", "
                   + pixmap.width() + ", " + pixmap.height() + "]";
        }
    }

    private static int hintGridColumnCount(List<Hint> hints) {
        if (hints.size() == 1)
            return 1;
        double left = hints.getFirst().centerX();
        for (int i = 1; i < hints.size(); i++) {
            if (left == hints.get(i).centerX())
                return i;
        }
        throw new IllegalStateException();
    }

    private static int hintRoundedX(double centerX, double cellWidth,
                                    double qtScaleFactor) {
        return (int) Math.round((centerX - cellWidth / 2) / qtScaleFactor);
    }

    private static int hintRoundedY(double centerY, double cellHeight,
                                    double qtScaleFactor) {
        return (int) Math.round((centerY - cellHeight / 2) / qtScaleFactor);
    }

    public static class HintBox extends QWidget {

        private final boolean isHintPartOfGrid;
        private final boolean gridLeftEdge;
        private final boolean gridTopEdge;
        private final boolean gridRightEdge;
        private final boolean gridBottomEdge;
        private final boolean drawGridEdgeBorders;
        private final boolean groupLeftEdge;
        private final boolean groupTopEdge;
        private final boolean groupRightEdge;
        private final boolean groupBottomEdge;
        private final double qtScaleFactor;
        private final int borderLength;
        private final int borderThickness;
        private final int borderRadius = 0;
        private QColor color;
        private QColor borderColor;

        public HintBox(int borderLength, int borderThickness, QColor color,
                       QColor borderColor,
                       boolean isHintPartOfGrid,
                       boolean gridLeftEdge, boolean gridTopEdge, boolean gridRightEdge, boolean gridBottomEdge,
                       boolean drawGridEdgeBorders,
                       boolean groupLeftEdge, boolean groupTopEdge, boolean groupRightEdge, boolean groupBottomEdge,
                       double qtScaleFactor) {
            this.isHintPartOfGrid = isHintPartOfGrid;
            this.gridLeftEdge = gridLeftEdge;
            this.gridTopEdge = gridTopEdge;
            this.gridRightEdge = gridRightEdge;
            this.gridBottomEdge = gridBottomEdge;
            this.drawGridEdgeBorders = drawGridEdgeBorders;
            this.groupLeftEdge = groupLeftEdge;
            this.groupTopEdge = groupTopEdge;
            this.groupRightEdge = groupRightEdge;
            this.groupBottomEdge = groupBottomEdge;
            this.qtScaleFactor = qtScaleFactor;
            this.borderLength = borderLength;
            this.borderThickness = borderThickness;
            this.color = color;
            this.borderColor = borderColor;
        }

        @Override
        protected void paintEvent(QPaintEvent event) {
            QPainter painter = new QPainter(this);
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Source);
//            painter.setRenderHint(QPainter.RenderHint.Antialiasing, false);
            // Draw background.
            if (color.alpha() != 0) {
                painter.setBrush(new QBrush(color));
                painter.setPen(Qt.PenStyle.NoPen);
                painter.drawRoundedRect(0, 0, width(), height(), borderRadius,
                        borderRadius);
            }
            if (borderThickness != 0)
                drawBorders(painter);
            painter.end();
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
            int groupThickness = borderThickness * 4;
            // Full thickness if grid edge.
            // Otherwise, half thickness: thickness/2 + thickness%2 for top and left, thickness/2 for bottom and right
            int topLeftInsideThickness = borderThickness / 2 + borderThickness % 2;
            int bottomRightInsideThickness = isHintPartOfGrid ? borderThickness / 2 : topLeftInsideThickness;
            QPen edgePen = createPen(borderColor, edgeThickness);
            QPen topLeftInsidePen = createPen(borderColor, topLeftInsideThickness);
            QPen bottomRightInsidePen = createPen(borderColor, bottomRightInsideThickness);
            // penOffset so that drawLine(x) draws at x, x+1, ... (no x-1, x-2, ...)
            int edgePenOffset = edgeThickness / 2;
            int insidePenOffset = borderThickness / 4;
            int gridTopEdgeExtraVertical = gridTopEdge ? edgeThickness/2 : 0;
            int gridBottomEdgeExtraVertical = gridBottomEdge ? edgeThickness/2 : 0;
            int gridLeftEdgeExtraHorizontal = gridLeftEdge ? edgeThickness/2 : 0;
            int gridRightEdgeExtraHorizontal = gridRightEdge ? edgeThickness/2 : 0;
            // Top left corner.
            // Vertical line.
            drawVerticalGridLine(painter,
                    drawGridEdgeBorders || (!gridLeftEdge && !gridTopEdge),
                    gridLeftEdge,
                    edgePen,
                    topLeftInsidePen,
                    left,
                    edgePenOffset,
                    insidePenOffset,
                    top,
                    top + gridTopEdgeExtraVertical + borderLength / 2
            );
            // Horizontal line.
            drawHorizontalGridLine(painter,
                    drawGridEdgeBorders || (!gridTopEdge && !gridLeftEdge),
                    gridTopEdge,
                    edgePen,
                    topLeftInsidePen,
                    top,
                    edgePenOffset,
                    insidePenOffset,
                    left,
                    left + gridLeftEdgeExtraHorizontal + borderLength / 2
            );
            // Top right corner.
            // Vertical line.
            drawVerticalGridLine(painter,
                    drawGridEdgeBorders || (!gridRightEdge && !gridTopEdge),
                    gridRightEdge,
                    edgePen,
                    bottomRightInsidePen,
                    right,
                    edgePenOffset - (edgeThickness - 1),
                    insidePenOffset - (bottomRightInsideThickness - 1),
                    top,
                    top + gridTopEdgeExtraVertical + borderLength / 2
            );

            // Horizontal line.
            drawHorizontalGridLine(painter,
                    drawGridEdgeBorders || (!gridTopEdge && !gridRightEdge),
                    gridTopEdge,
                    edgePen,
                    topLeftInsidePen,
                    top,
                    edgePenOffset,
                    insidePenOffset,
                    right - (gridRightEdgeExtraHorizontal - 1) - borderLength / 2,
                    right + 1
            );
            // Bottom left corner.
            // Vertical line.
            drawVerticalGridLine(painter,
                    drawGridEdgeBorders || (!gridLeftEdge && !gridBottomEdge),
                    gridLeftEdge,
                    edgePen,
                    topLeftInsidePen,
                    left,
                    edgePenOffset,
                    insidePenOffset,
                    bottom - (gridBottomEdgeExtraVertical - 1) - borderLength / 2,
                    bottom + 1
            );
            // Horizontal line.
            drawHorizontalGridLine(painter,
                    drawGridEdgeBorders || (!gridBottomEdge && !gridLeftEdge),
                    gridBottomEdge,
                    edgePen,
                    bottomRightInsidePen,
                    bottom,
                    edgePenOffset - (edgeThickness - 1),
                    insidePenOffset - (bottomRightInsideThickness - 1),
                    left,
                    left + gridLeftEdgeExtraHorizontal + borderLength / 2
            );
            // Bottom right corner.
            // Vertical line.
            drawVerticalGridLine(painter,
                    drawGridEdgeBorders || (!gridRightEdge && !gridBottomEdge),
                    gridRightEdge,
                    edgePen,
                    bottomRightInsidePen,
                    right,
                    edgePenOffset - (edgeThickness - 1),
                    insidePenOffset - (bottomRightInsideThickness - 1),
                    bottom - (gridBottomEdgeExtraVertical - 1) - borderLength / 2,
                    bottom + 1
            );
            // Horizontal line.
            drawHorizontalGridLine(painter,
                    drawGridEdgeBorders || (!gridBottomEdge && !gridRightEdge),
                    gridBottomEdge,
                    edgePen,
                    bottomRightInsidePen,
                    bottom,
                    edgePenOffset - (edgeThickness - 1),
                    insidePenOffset - (bottomRightInsideThickness - 1),
                    right - (gridRightEdgeExtraHorizontal - 1) - borderLength / 2,
                    right + 1
            );
            topLeftInsidePen.dispose();
            bottomRightInsidePen.dispose();
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
            if (!condition) return;
            painter.setPen(isEdge ? edgePen : insidePen);
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
            if (!condition) return;
            painter.setPen(isEdge ? edgePen : insidePen);
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

        private void drawEdgeLine(
                QPainter painter,
                boolean isEdge,
                boolean shouldDraw,
                QPen edgePen,
                QPen insidePen,
                boolean isVertical,
                int x, int y,
                int edgeOffset,
                int insideOffset,
                int extraOffset,
                int length
        ) {
            if (!shouldDraw)
                return;
            painter.setPen(isEdge ? edgePen : insidePen);
            int offset = isEdge ? edgeOffset : insideOffset;

            if (isVertical) {
                int xPos = x + offset;
                painter.drawLine(xPos, y, xPos, y + extraOffset + length / 2);
            } else {
                int yPos = y + offset;
                painter.drawLine(x, yPos, x + extraOffset + length / 2, yPos);
            }
        }

    }

    private static QColor qColor(String hexColor, double opacity) {
        return QColor.fromRgba(hexColorStringToRgba(hexColor, opacity));
    }

    public static class HintLabel extends QLabel {

        private final QColor fontColor;
        private final QColor prefixColor;
        private final QColor outlineColor;
        private final int outlineThickness;
        private final List<HintKeyText> keyTexts;
        private final int tightHintBoxLeft;
        private final int tightHintBoxTop;
        private final int tightHintBoxWidth;
        private final int tightHintBoxHeight;
        private int left;
        private int top;

        public HintLabel(Hint hint, QFont font, Map<String, Integer> xAdvancesByString,
                         int boxWidth,
                         int boxHeight, int totalXAdvance, QColor fontColor, QColor prefixColor,
                         QColor outlineColor,
                         int outlineThickness, QColor shadowColor, double shadowBlurRadius,
                         double shadowHorizontalOffset, double shadowVerticalOffset, List<Key> focusedKeySequence, double fontSpacingPercent,
                         int hintKeyMaxXAdvance, QFontMetrics metrics) {
            super(hint.keySequence()
                      .stream()
                      .map(Key::hintLabel)
                      .collect(Collectors.joining()));
            this.fontColor = fontColor;
            this.prefixColor = prefixColor;
            this.outlineColor = outlineColor;
            this.outlineThickness = outlineThickness;
            setFont(font);
            QGraphicsDropShadowEffect shadow = new QGraphicsDropShadowEffect();
            shadow.setBlurRadius(shadowBlurRadius);
            shadow.setOffset(shadowHorizontalOffset, shadowVerticalOffset);
            shadow.setColor(shadowColor);
            setGraphicsEffect(shadow);

            int y = (boxHeight + metrics.ascent() - metrics.descent()) / 2;

            double smallestColAlignedFontBoxWidth = hintKeyMaxXAdvance * hint.keySequence().size();
            double smallestColAlignedFontBoxWidthPercent =
                    smallestColAlignedFontBoxWidth / boxWidth;
            // We want font spacing percent 0.5 be the min spacing that keeps column alignment.
            double adjustedFontBoxWidthPercent = fontSpacingPercent < 0.5d ?
                    (fontSpacingPercent * 2) * smallestColAlignedFontBoxWidthPercent
                    : smallestColAlignedFontBoxWidthPercent + (fontSpacingPercent - 0.5d) * 2 * (1 - smallestColAlignedFontBoxWidthPercent) ;
            boolean doNotColAlign = hint.keySequence().size() != 1 &&
                                    adjustedFontBoxWidthPercent < smallestColAlignedFontBoxWidthPercent;
            double extraNotAlignedWidth = smallestColAlignedFontBoxWidth -
                                          totalXAdvance;
            extraNotAlignedWidth = adjustedFontBoxWidthPercent * extraNotAlignedWidth;

            List<Key> keySequence = hint.keySequence();
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
                    if (keyIndex != hint.keySequence().size() - 1)
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
                keyTexts.add(new HintKeyText(keyText, x, y, keyWidth,
                        keyIndex <= focusedKeySequence.size() - 1));
            }
            int smallestHintBoxTop = y - metrics.ascent();
            int smallestHintBoxHeight = metrics.height();
            this.tightHintBoxLeft = smallestHintBoxLeft;
            this.tightHintBoxTop = smallestHintBoxTop;
            this.tightHintBoxWidth = smallestHintBoxWidth;
            this.tightHintBoxHeight = smallestHintBoxHeight;
        }

        @Override
        protected void paintEvent(QPaintEvent event) {
            QPainter painter = new QPainter(this);
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            painter.setRenderHint(QPainter.RenderHint.TextAntialiasing, true);
            painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform, true);

            if (outlineThickness != 0) {
                QPen outlinePen = new QPen(outlineColor);
                outlinePen.setWidth(outlineThickness);
                outlinePen.setJoinStyle(Qt.PenJoinStyle.RoundJoin);
                painter.setPen(outlinePen);
                painter.setBrush(Qt.BrushStyle.NoBrush); // No fill, only stroke
                QPainterPath outlinePath = new QPainterPath();
                for (HintKeyText keyText : keyTexts) {
                    outlinePath.addText(keyText.x() - left, keyText.y() - top, font(),
                            keyText.text());
                }
                painter.drawPath(outlinePath);
            }

            // Avoid blending the text with the outline. Text should override the outline.
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Source);
            for (HintKeyText keyText : keyTexts) {
                painter.setPen(keyText.isPrefix() ? prefixColor : fontColor);
                painter.drawText(keyText.x() - left, keyText.y() - top, keyText.text());
            }
            painter.end();
        }
    }

    private record HintKeyText(String text, int x, int y, int width, boolean isPrefix) {

    }

    private static WinDef.HWND createWindow(String windowName, int windowX, int windowY,
                                            int windowWidth, int windowHeight,
                                            WinUser.WindowProc windowCallback) {
        WinUser.WNDCLASSEX wClass = new WinUser.WNDCLASSEX();
        wClass.hbrBackground = null;
        wClass.lpszClassName = "Mousemaster" + windowName + "ClassName";
        wClass.lpfnWndProc = windowCallback;
        WinDef.ATOM registerClassExResult = User32.INSTANCE.RegisterClassEx(wClass);
        WinDef.HWND hwnd = User32.INSTANCE.CreateWindowEx(
                User32.WS_EX_TOPMOST | ExtendedUser32.WS_EX_TOOLWINDOW | ExtendedUser32.WS_EX_NOACTIVATE
                | ExtendedUser32.WS_EX_LAYERED | ExtendedUser32.WS_EX_TRANSPARENT,
                wClass.lpszClassName, "Mousemaster" + windowName + "WindowName",
                WinUser.WS_POPUP, windowX, windowY, windowWidth, windowHeight, null, null,
                wClass.hInstance, null);
        // Will be overwritten for hint mesh to something other than 0.
        User32.INSTANCE.SetLayeredWindowAttributes(hwnd, 0, (byte) 0, WinUser.LWA_COLORKEY);
        User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_SHOWNORMAL);
        return hwnd;
    }

    private static WinDef.HWND createZoomWindow() {
        if (!Magnification.INSTANCE.MagInitialize())
            logger.error("Failed MagInitialize: " +
                         Integer.toHexString(Native.getLastError()));
        WinUser.WNDCLASSEX wClass = new WinUser.WNDCLASSEX();
        WinUser.WindowProc callback = WindowsOverlay::zoomWindowCallback;
        wClass.hbrBackground = null;
        String WC_MAGNIFIER = "Magnifier";
        wClass.lpszClassName = "MagnifierWindow";
        wClass.lpfnWndProc = callback;
        WinDef.ATOM registerClassExResult = User32.INSTANCE.RegisterClassEx(wClass);
        int MS_SHOWMAGNIFIEDCURSOR = 0x0001;
        WinDef.HMODULE hInstance = Kernel32.INSTANCE.GetModuleHandle(null);
        WinDef.HWND hostHwnd = User32.INSTANCE.CreateWindowEx(
                User32.WS_EX_TOPMOST | ExtendedUser32.WS_EX_LAYERED |
                ExtendedUser32.WS_EX_TOOLWINDOW | ExtendedUser32.WS_EX_NOACTIVATE |
                ExtendedUser32.WS_EX_TRANSPARENT,
                wClass.lpszClassName, "MousemasterMagnifierHostName",
                WinUser.WS_POPUP,
                0, 0, 10, 10, null, null,
                hInstance, null);
        // When uncommenting this SetLayeredWindowAttributes call, a black frame is
        // drawn the first time the zoom is used.
//        User32.INSTANCE.SetLayeredWindowAttributes(hostHwnd, 0, (byte) 255,
//                WinUser.LWA_ALPHA);
        WinDef.HWND hwnd = User32.INSTANCE.CreateWindowEx(
                0,
                WC_MAGNIFIER, "MagnifierWindow",
                User32.WS_CHILD | MS_SHOWMAGNIFIEDCURSOR | ExtendedUser32.WS_VISIBLE,
                0, 0, 10, 10,
                hostHwnd, null,
                hInstance, null);
        zoomWindow = new ZoomWindow(hwnd, hostHwnd, callback);
        updateZoomExcludedWindows();
        return hostHwnd;
    }

    private static WinDef.LRESULT indicatorWindowCallback(WinDef.HWND hwnd, int uMsg,
                                                          WinDef.WPARAM wParam,
                                                          WinDef.LPARAM lParam) {
        switch (uMsg) {
            case WinUser.WM_PAINT:
                ExtendedUser32.PAINTSTRUCT ps = new ExtendedUser32.PAINTSTRUCT();
                WinDef.HDC hdc = ExtendedUser32.INSTANCE.BeginPaint(hwnd, ps);
                clearWindow(hdc, ps.rcPaint, 0);
                if (showingIndicator) {
                    clearWindow(hdc, ps.rcPaint,
                            hexColorStringToInt(currentIndicator.hexColor()));
                }
                ExtendedUser32.INSTANCE.EndPaint(hwnd, ps);
                break;
        }
        return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
    }

    private static WinDef.LRESULT gridWindowCallback(WinDef.HWND hwnd, int uMsg,
                                                     WinDef.WPARAM wParam,
                                                     WinDef.LPARAM lParam) {
        switch (uMsg) {
            case WinUser.WM_PAINT:
                boolean isStandByGridWindow = standByGridWindow != null &&
                                              hwnd.equals(standByGridWindow.hwnd());
                ExtendedUser32.PAINTSTRUCT ps = new ExtendedUser32.PAINTSTRUCT();
                if (!showingGrid || (isStandByGridWindow && standByGridCanBeHidden)) {
                    WinDef.HDC hdc = ExtendedUser32.INSTANCE.BeginPaint(hwnd, ps);
                    // The area has to be cleared otherwise the previous drawings will be drawn.
                    clearWindow(hdc, ps.rcPaint, 0);
                    ExtendedUser32.INSTANCE.EndPaint(hwnd, ps);
                    break;
                }
                WinDef.HDC hdc = ExtendedUser32.INSTANCE.BeginPaint(hwnd, ps);
                WinDef.HDC memDC = GDI32.INSTANCE.CreateCompatibleDC(hdc);
                // We may want to use the window's full dimension (GetClientRect) instead of rcPaint?
                int width = ps.rcPaint.right - ps.rcPaint.left;
                int height = ps.rcPaint.bottom - ps.rcPaint.top;
                WinDef.HBITMAP
                        hBitmap = GDI32.INSTANCE.CreateCompatibleBitmap(hdc, width, height);
                WinNT.HANDLE oldBitmap = GDI32.INSTANCE.SelectObject(memDC, hBitmap);
                clearWindow(memDC, ps.rcPaint, 0);
                drawGrid(memDC, ps.rcPaint);
                // Copy (blit) the off-screen buffer to the screen.
                GDI32.INSTANCE.BitBlt(hdc, 0, 0, width, height, memDC, 0, 0,
                        GDI32.SRCCOPY);
                GDI32.INSTANCE.SelectObject(memDC, oldBitmap);
                GDI32.INSTANCE.DeleteObject(hBitmap);
                GDI32.INSTANCE.DeleteDC(memDC);
                ExtendedUser32.INSTANCE.EndPaint(hwnd, ps);
                // Stand-by grid can be hidden right after the new grid is visible (drawn at least once).
                if (standByGridWindow != null && !standByGridCanBeHidden) {
                    standByGridCanBeHidden = true;
                    requestWindowRepaint(standByGridWindow.hwnd); // Drawings will be cleared.
                }
                break;
        }
        return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
    }

    private static void clearWindow(WinDef.HDC hdc, WinDef.RECT windowRect, int color) {
        WinDef.HBRUSH hbrBackground = ExtendedGDI32.INSTANCE.CreateSolidBrush(color);
        ExtendedUser32.INSTANCE.FillRect(hdc, windowRect, hbrBackground);
        GDI32.INSTANCE.DeleteObject(hbrBackground);
    }

    private static void drawGrid(WinDef.HDC hdc, WinDef.RECT windowRect) {
        int rowCount = currentGrid.rowCount();
        int columnCount = currentGrid.columnCount();
        int cellWidth = currentGrid.width() / columnCount;
        int cellHeight = currentGrid.height() / rowCount;
        int[] polyCounts = new int[rowCount + 1 + columnCount + 1];
        WinDef.POINT[] points =
                (WinDef.POINT[]) new WinDef.POINT().toArray(polyCounts.length * 2);
        int scaledLineThickness = scaledPixels(currentGrid.lineThickness(), 1);
        // Vertical lines
        for (int lineIndex = 0; lineIndex <= columnCount; lineIndex++) {
            int x = lineIndex == columnCount ? windowRect.right :
                    lineIndex * cellWidth;
            if (x == windowRect.left)
                x += scaledLineThickness / 2;
            else if (x == windowRect.right)
                x -= scaledLineThickness / 2 + scaledLineThickness % 2;
            points[2 * lineIndex].x = x;
            points[2 * lineIndex].y = 0;
            points[2 * lineIndex + 1].x = x;
            points[2 * lineIndex + 1].y = currentGrid.height();
            polyCounts[lineIndex] = 2;
        }
        // Horizontal lines
        int polyCountsOffset = columnCount + 1;
        int pointsOffset = 2 * polyCountsOffset;
        for (int lineIndex = 0; lineIndex <= rowCount; lineIndex++) {
            int y = lineIndex == rowCount ? windowRect.bottom :
                    lineIndex * cellHeight;
            if (y == windowRect.top)
                y += scaledLineThickness / 2;
            else if (y == windowRect.bottom)
                y -= scaledLineThickness / 2 + scaledLineThickness % 2;
            points[pointsOffset + 2 * lineIndex].x = 0;
            points[pointsOffset + 2 * lineIndex].y = y;
            points[pointsOffset + 2 * lineIndex + 1].x = currentGrid.width();
            points[pointsOffset + 2 * lineIndex + 1].y = y;
            polyCounts[polyCountsOffset + lineIndex] = 2;
        }
        String lineColor = currentGrid.lineHexColor();
        WinUser.HPEN gridPen =
                ExtendedGDI32.INSTANCE.CreatePen(ExtendedGDI32.PS_SOLID, scaledLineThickness,
                        hexColorStringToInt(lineColor));
        if (gridPen == null)
            throw new IllegalStateException("Unable to create grid pen");
        WinNT.HANDLE oldPen = GDI32.INSTANCE.SelectObject(hdc, gridPen);
        boolean polyPolylineResult = ExtendedGDI32.INSTANCE.PolyPolyline(hdc, points, polyCounts,
                polyCounts.length);
        if (!polyPolylineResult) {
            int lastError = Native.getLastError();
            throw new IllegalStateException(
                    "PolyPolyline failed with error code " + lastError);
        }
        GDI32.INSTANCE.SelectObject(hdc, oldPen);
        GDI32.INSTANCE.DeleteObject(gridPen);
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
    public static String blendColorOverWhite(String hexColor, double opacity) {
        if (hexColor.startsWith("#"))
            hexColor = hexColor.substring(1);
        int colorInt = Integer.parseUnsignedInt(hexColor, 16);
        int inputRed = (colorInt >> 16) & 0xFF;
        int inputGreen = (colorInt >> 8) & 0xFF;
        int inputBlue = colorInt & 0xFF;
        int whiteRed = 255;
        int whiteGreen = 255;
        int whiteBlue = 255;
        int blendedRed = (int) Math.round((inputRed * opacity) + (whiteRed * (1 - opacity)));
        int blendedGreen = (int) Math.round((inputGreen * opacity) + (whiteGreen * (1 - opacity)));
        int blendedBlue = (int) Math.round((inputBlue * opacity) + (whiteBlue * (1 - opacity)));
        return String.format("%02X%02X%02X", blendedRed, blendedGreen, blendedBlue);
    }

    // color1 is background, color2 is foreground
    private static int blend(int color1, int color2, double color2Opacity) {
        int red1 = (color1 >> 16) & 0xFF;
        int green1 = (color1 >> 8) & 0xFF;
        int blue1 = color1 & 0xFF;
        int red2 = (color2 >> 16) & 0xFF;
        int green2 = (color2 >> 8) & 0xFF;
        int blue2 = color2 & 0xFF;
        int blendedRed = (int) Math.round((red2 * color2Opacity) + (red1 * (1 - color2Opacity)));
        int blendedGreen = (int) Math.round((green2 * color2Opacity) + (green1 * (1 - color2Opacity)));
        int blendedBlue = (int) Math.round((blue2 * color2Opacity) + (blue1 * (1 - color2Opacity)));
        return (blendedRed << 16) | (blendedGreen << 8) | blendedBlue;
    }

    private record HintSequenceText(Hint hint, List<HintKeyText> keyTexts) {

    }

    private static int hexColorStringToInt(String hexColor) {
        if (hexColor.startsWith("#"))
            hexColor = hexColor.substring(1);
        int colorInt = Integer.parseUnsignedInt(hexColor, 16);
        // In COLORREF, the order is 0x00BBGGRR, so we need to reorder the components.
        int red = (colorInt >> 16) & 0xFF;
        int green = (colorInt >> 8) & 0xFF;
        int blue = colorInt & 0xFF;
        return (blue << 16) | (green << 8) | red;
    }

    private static int hexColorStringToRgba(String hexColor, double opacity) {
        if (hexColor.startsWith("#"))
            hexColor = hexColor.substring(1);
        int colorInt = Integer.parseUnsignedInt(hexColor, 16);
        int red = (colorInt >> 16) & 0xFF;
        int green = (colorInt >> 8) & 0xFF;
        int blue = colorInt & 0xFF;
        int alpha = (int) (opacity * 255) & 0xFF;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int hexColorStringToRgb(String hexColor, double opacity) {
        // https://learn.microsoft.com/en-us/windows/win32/api/wingdi/ns-wingdi-blendfunction
        // Note that the APIs use premultiplied alpha, which means that the red, green
        // and blue channel values in the bitmap must be premultiplied with the alpha channel value.
        if (hexColor.startsWith("#"))
            hexColor = hexColor.substring(1);
        int colorInt = Integer.parseUnsignedInt(hexColor, 16);
        // In COLORREF, the order is 0x00BBGGRR, so we need to reorder the components.
        int red = (int) (((colorInt >> 16) & 0xFF) * opacity);
        int green = (int) (((colorInt >> 8) & 0xFF) * opacity);
        int blue = (int) ((colorInt & 0xFF) * opacity);
        return (red << 16) | (green << 8) | blue;
    }

    private static int alphaMultipliedChannelsColor(int color, double opacity) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        return ((int) Math.round(red * opacity) << 16) | ((int) Math.round(green * opacity) << 8) |
               (int) Math.round(blue * opacity);
    }

    public static void setIndicator(Indicator indicator) {
        Objects.requireNonNull(indicator);
        if (showingIndicator && currentIndicator != null &&
            currentIndicator.equals(indicator))
            return;
        Indicator oldIndicator = currentIndicator;
        currentIndicator = indicator;
        if (indicatorWindow == null) {
            createIndicatorWindow();
        }
        else if (indicator.size() != oldIndicator.size()) {
            User32.INSTANCE.SetWindowPos(indicatorWindow.hwnd(), null, bestIndicatorX(),
                    bestIndicatorY(),
                    indicatorSize() + 1,
                    indicatorSize() + 1,
                    User32.SWP_NOZORDER);
        }
        showingIndicator = true;
        requestWindowRepaint(indicatorWindow.hwnd);
    }

    public static void setZoom(Zoom zoom) {
        if (currentZoom != null && currentZoom.equals(zoom))
            return;
        if (hintMeshEndAnimation) {
            if (!zoomAfterHintMeshEndAnimation) {
                zoomAfterHintMeshEndAnimation = true;
                afterHintMeshEndAnimationZoom = zoom;
                return;
            }
            else {
                // We skip the enqueued zoom.
                zoomAfterHintMeshEndAnimation = false;
                afterHintMeshEndAnimationZoom = null;
            }
        }
        if (zoomWindow == null) {
            createZoomWindow();
        }
        Zoom oldZoom = currentZoom;
        currentZoom = zoom;
        mustUpdateMagnifierSource = true;
        if (currentZoom == null) {
            User32.INSTANCE.ShowWindow(zoomWindow.hostHwnd(), WinUser.SW_HIDE);
        }
        else {
            // We use a second zoom window to keep the already open zoom window visible,
            // until the second zoom is ready.
            // Because MagSetWindowTransform() will immediately show the new zoom area,
            // except only the zoom percent has been set so far (the new source will be updated by
            // MagSetWindowSource, next frame only).
            Rectangle screenRectangle = zoom.screenRectangle();
            if (oldZoom == null || oldZoom.percent() != zoom.percent()) {
                if (standByZoomWindow == null) {
                    standByZoomWindow = zoomWindow;
                    createZoomWindow();
                }
                else {
                    ZoomWindow newStandByZoomWindow = zoomWindow;
                    zoomWindow = standByZoomWindow;
                    standByZoomWindow = newStandByZoomWindow;
                    updateZoomExcludedWindows();
                }
                // MagSetWindowTransform() can take 10-20ms.
                if (!Magnification.INSTANCE.MagSetWindowTransform(zoomWindow.hwnd(),
                        new Magnification.MAGTRANSFORM.ByReference(
                                (float) zoomPercent())))
                    logger.error("Failed MagSetWindowTransform: " +
                                 Integer.toHexString(Native.getLastError()));
            }
            User32.INSTANCE.SetWindowPos(zoomWindow.hostHwnd(), null,
                    screenRectangle.x(), screenRectangle.y(),
                    screenRectangle.width(), screenRectangle.height(),
                    User32.SWP_NOZORDER);
            User32.INSTANCE.SetWindowPos(zoomWindow.hwnd(), null,
                    0, 0,
                    screenRectangle.width(), screenRectangle.height(),
                    User32.SWP_NOZORDER);
        }
        if (indicatorWindow != null) {
            User32.INSTANCE.SetWindowPos(indicatorWindow.hwnd(), null, bestIndicatorX(),
                    bestIndicatorY(),
                    indicatorSize() + 1,
                    indicatorSize() + 1,
                    User32.SWP_NOZORDER);
            if (showingIndicator) {
                User32.INSTANCE.InvalidateRect(indicatorWindow.hwnd, null, true);
            }
        }
        if (showingHintMesh) {
            for (HintMeshWindow hintMeshWindow : hintMeshWindows.values()) {
                User32.INSTANCE.InvalidateRect(hintMeshWindow.hwnd, null, true);
            }
        }
        updateZoomWindow();
    }

    private static void updateZoomExcludedWindows() {
        if (zoomWindow == null)
            return;
        List<WinDef.HWND> hwnds = new ArrayList<>();
        if (gridWindow != null)
            hwnds.add(gridWindow.hwnd);
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values()) {
            hwnds.add(hintMeshWindow.hwnd);
        }
        if (indicatorWindow != null)
            hwnds.add(indicatorWindow.hwnd);
        if (standByZoomWindow != null)
            hwnds.add(standByZoomWindow.hwnd);
        if (hwnds.isEmpty())
            return;
        if (!Magnification.INSTANCE.MagSetWindowFilterList(zoomWindow.hwnd(),
                Magnification.MW_FILTERMODE_EXCLUDE, hwnds.size(),
                hwnds.toArray(new WinDef.HWND[0])))
            logger.error("Failed to set the zoom excluded window list: " +
                         Integer.toHexString(Native.getLastError()));
    }

    private static WinDef.LRESULT zoomWindowCallback(WinDef.HWND hwnd, int uMsg,
                                                     WinDef.WPARAM wParam,
                                                     WinDef.LPARAM lParam) {
//        switch (uMsg) {
//            case WinUser.WM_PAINT:
//                if (currentZoom == null) {
//                    ExtendedUser32.PAINTSTRUCT ps = new ExtendedUser32.PAINTSTRUCT();
//                    WinDef.HDC hdc = ExtendedUser32.INSTANCE.BeginPaint(hwnd, ps);
//                    clearWindow(hdc, ps.rcPaint, 0);
//                    ExtendedUser32.INSTANCE.EndPaint(hwnd, ps);
//                }
//                break;
//        }
        return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
    }

    public static void hideIndicator() {
        if (!showingIndicator)
            return;
        showingIndicator = false;
        requestWindowRepaint(indicatorWindow.hwnd);
    }

    public static void setGrid(Grid grid) {
        Objects.requireNonNull(grid);
        if (showingGrid && currentGrid != null && currentGrid.equals(grid))
            return;
        Grid oldGrid = currentGrid;
        currentGrid = grid;
        // +1 width and height because no line can be drawn on y = windowHeight and y = windowWidth.
        if (gridWindow == null)
            createGridWindow(currentGrid.x(), currentGrid.y(), currentGrid.width(),
                    currentGrid.height());
        else {
            if (grid.x() != oldGrid.x() || grid.y() != oldGrid.y() ||
                grid.width() != oldGrid.width() || grid.height() != oldGrid.height()) {
                // When going from a window grid to a screen grid, we don't want to:
                // 1. Resize. 2. Draw old grid in resized window. 3. Draw new grid. Instead, we want to:
                // 1. Clear old grid. 2. Resize. 3. Draw new grid.
                // However, clearing then resizing introduces a "blank" frame,
                // that is why we use 2 grid windows.
                if (standByGridWindow == null) {
                    standByGridWindow = gridWindow;
                    createGridWindow(currentGrid.x(), currentGrid.y(), currentGrid.width(),
                            currentGrid.height());
                    standByGridCanBeHidden = false;
                }
                else {
                    GridWindow newStandByGridWindow = gridWindow;
                    gridWindow = standByGridWindow;
                    standByGridWindow = newStandByGridWindow;
                    User32.INSTANCE.SetWindowPos(gridWindow.hwnd(), null, grid.x(), grid.y(),
                            grid.width(), grid.height(), User32.SWP_NOZORDER);
                    standByGridCanBeHidden = false;
                }
            }
        }
        showingGrid = true;
        requestWindowRepaint(gridWindow.hwnd);
    }

    public static void setHintMesh(HintMesh hintMesh, Zoom zoom) {
        setHintMesh(hintMesh, zoom, false);
    }

    public static void setHintMesh(HintMesh hintMesh, Zoom zoom, boolean hintMatch) {
        Objects.requireNonNull(hintMesh);
        if (!hintMesh.visible()) {
            hideHintMesh();
            return;
        }
        if (showingHintMesh && currentHintMesh != null && currentHintMesh.equals(hintMesh))
            return;
        boolean isHintGrid = hintMesh.hints().getFirst().cellWidth() != -1;
        if (hintMatch) {
            if (isHintGrid)
                hintMeshEndAnimation = true;
            else {
                // No animation for position history hints.
                // hideHintMesh() will be called by the switch mode command.
                return;
            }
        }
        else {
            hintMeshEndAnimation = false;
            if (zoomAfterHintMeshEndAnimation) {
                zoomAfterHintMeshEndAnimation = false;
                setZoom(afterHintMeshEndAnimationZoom);
                afterHintMeshEndAnimationZoom = null;
            }
        }
        currentHintMesh = hintMesh;
        createOrUpdateHintMeshWindows(currentHintMesh, zoom);
        showingHintMesh = true;
        if (!waitForZoomBeforeRepainting) {
            for (HintMeshWindow hintMeshWindow : hintMeshWindows.values()) {
//                requestWindowRepaint(hintMeshWindow.hwnd);
            }
        }
    }

    public static void hideGrid() {
        if (!showingGrid)
            return;
        showingGrid = false;
        requestWindowRepaint(gridWindow.hwnd);
    }

    public static void hideHintMesh() {
        if (!showingHintMesh)
            return;
        if (hintMeshEndAnimation)
            return;
        showingHintMesh = false;
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values()) {
            hintMeshWindow.window.hideChildren();
        }
    }

    private static void requestWindowRepaint(WinDef.HWND hwnd) {
        User32.INSTANCE.InvalidateRect(hwnd, null, true);
        User32.INSTANCE.UpdateWindow(hwnd);
    }

    static void mouseMoved(WinDef.POINT mousePosition) {
        if (indicatorWindow == null)
            return;
        User32.INSTANCE.MoveWindow(indicatorWindow.hwnd,
                bestIndicatorX(mousePosition),
                bestIndicatorY(mousePosition),
                indicatorSize(mousePosition) + 1,
                indicatorSize(mousePosition) + 1, false);
    }

}
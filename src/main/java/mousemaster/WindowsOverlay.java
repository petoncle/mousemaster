package mousemaster;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.PointerByReference;
import io.qt.core.*;
import io.qt.gui.*;
import io.qt.widgets.QApplication;
import io.qt.widgets.QGraphicsDropShadowEffect;
import io.qt.widgets.QGraphicsPixmapItem;
import io.qt.widgets.QGraphicsScene;
import io.qt.widgets.QLabel;
import io.qt.widgets.QWidget;
import mousemaster.WindowsMouse.MouseSize;
import mousemaster.qt.TransparentWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WindowsOverlay {

    private static final Logger logger = LoggerFactory.getLogger(WindowsOverlay.class);

    public static boolean waitForZoomBeforeRepainting;

    private static IndicatorWindow indicatorWindow;
    private static boolean showingIndicator;
    private static Indicator currentIndicator;
    private static int maxIndicatorShadowPadding;
    private static GridWindow gridWindow, standByGridWindow;
    private static boolean standByGridCanBeHidden;
    private static boolean showingGrid;
    private static Grid currentGrid;
    private static final Map<Screen, HintMeshWindow> hintMeshWindows =
            new LinkedHashMap<>(); // Ordered for topmost handling.
    private static final Map<HintMesh, PixmapAndPosition> hintMeshPixmaps = new HashMap<>();
    private static final Map<HintMesh, Map<List<Key>, QRect>> hintBoxGeometriesByHintMeshKey = new HashMap<>();
    private static boolean showingHintMesh;
    private static boolean hintMeshEndAnimation;
    private static boolean zoomAfterHintMeshEndAnimation;
    private static Zoom afterHintMeshEndAnimationZoom;
    private static HintMesh currentHintMesh;
    private static ZoomWindow zoomWindow, standByZoomWindow;
    private static Zoom currentZoom;
    private static boolean mustUpdateMagnifierSource;
    // Screenshot-based zoom animation fields.
    private static ScreenshotWidget screenshotWidget;
    private static WinDef.HWND screenshotHwnd;
    private static QPixmap screenshotPixmap;
    private static boolean screenshotAnimating;
    private static boolean screenshotPendingHide;
    /**
     * Building the hint window is expensive and when it is done from the keyboard hook,
     * Windows will cancel the hook and the key press will go through to the other apps.
     * Windows won't wait for the keyboard hook to return if it's taking too long.
     */
    private static Runnable setUncachedHintMeshWindowRunnable;
    private static Runnable cacheQtHintWindowIntoPixmapRunnable;
    private static Runnable messagePump;
    /**
     * True when the build is running from update() (deferred), meaning we are
     * NOT inside a keyboard hook callback and can safely pump messages to keep
     * the hook responsive. False when running inline from the hook callback.
     */
    private static boolean pumpDuringHintBuild;

    static void setMessagePump(Runnable pump) {
        messagePump = pump;
    }

    public static void update(double delta) {
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
        updateZoomWindow();
        // Deferred screenshot hide: the magnifier was shown by updateZoomWindow
        // on the previous frame (or by setZoom inside endScreenshotZoomAnimation).
        // Wait one frame so DWM composites the magnifier before removing
        // the screenshot that covers it.
        if (screenshotPendingHide) {
            screenshotPendingHide = false;
            // Don't hide() the widget: showing a hidden layered window
            // briefly exposes its stale surface. Instead, clear its content
            // so it becomes transparent (WA_TranslucentBackground).
            screenshotWidget.setZoom(null);
            screenshotWidget.repaint();
            if (screenshotPixmap != null) {
                screenshotWidget.setScreenshot(null, null);
                screenshotPixmap = null;
            }
        }
    }

    private static void updateZoomWindow() {
        if (screenshotAnimating)
            return;
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
        if (standByZoomWindow != null)
            User32.INSTANCE.ShowWindow(standByZoomWindow.hostHwnd(), WinUser.SW_HIDE);
        User32.INSTANCE.InvalidateRect(zoomWindow.hwnd(), null, true);
        setTopmost();
    }

    public static void flushCache() {
        for (PixmapAndPosition pixmapAndPosition : hintMeshPixmaps.values())
            pixmapAndPosition.pixmap().dispose();
        hintMeshPixmaps.clear();
        hintBoxGeometriesByHintMeshKey.clear();
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values()) {
            hintMeshWindow.lastHintMeshKeyReference().set(null);
        }
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

    static WinDef.RECT windowRectExcludingShadow(WinDef.HWND hwnd) {
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
        if (screenshotAnimating) {
            if (screenshotHwnd != null)
                hwnds.add(screenshotHwnd);
        }
        else {
            // During pending hide, keep screenshot above magnifier so it covers
            // the magnifier while it renders its first frame.
            if (screenshotPendingHide && screenshotHwnd != null)
                hwnds.add(screenshotHwnd);
            if (zoomWindow != null)
                hwnds.add(zoomWindow.hostHwnd);
        }
        if (hwnds.isEmpty())
            return;
        if (currentZoom != null || screenshotAnimating) {
            // During zoom, use relative positioning to maintain z-order.
            // Avoid SetWindowPos(hwnd, HWND_TOPMOST) which causes a DWM
            // recomposition glitch visible as a brief indicator flicker.
            for (int i = 1; i < hwnds.size(); i++)
                User32.INSTANCE.SetWindowPos(hwnds.get(i), hwnds.get(i - 1),
                        0, 0, 0, 0,
                        WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE | WinUser.SWP_NOACTIVATE);
            return;
        }
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

    private record IndicatorWindow(WinDef.HWND hwnd, TransparentWindow window,
                                   IndicatorWidget widget,
                                   IndicatorLabelWidget labelWidget) {
    }

    private static class IndicatorWidget extends QWidget {

        private QColor color;
        private int edgeCount;
        private double outerOutlineThickness;
        private QColor outerOutlineColor;
        private double outerOutlineFillPercent;
        private double outerOutlineFillStartAngle;
        private FillDirection outerOutlineFillDirection;
        private double innerOutlineThickness;
        private QColor innerOutlineColor;
        private double innerOutlineFillPercent;
        private double innerOutlineFillStartAngle;
        private FillDirection innerOutlineFillDirection;
        private double outlineScale;
        private IndicatorShadowEffect customGraphicsEffect;
        private boolean cleared;

        IndicatorWidget(QWidget parent) {
            super(parent);
        }

        void setOutlineScale(double outlineScale) {
            this.outlineScale = outlineScale;
        }

        void setColor(QColor color) {
            if (this.color != null)
                this.color.dispose();
            this.color = color;
        }

        void setEdgeCount(int edgeCount) {
            this.edgeCount = edgeCount;
        }

        void setOutlines(double outerOutlineThickness, QColor outerOutlineColor,
                         double outerOutlineFillPercent,
                         double outerOutlineFillStartAngle,
                         FillDirection outerOutlineFillDirection,
                         double innerOutlineThickness, QColor innerOutlineColor,
                         double innerOutlineFillPercent,
                         double innerOutlineFillStartAngle,
                         FillDirection innerOutlineFillDirection) {
            if (this.outerOutlineColor != null)
                this.outerOutlineColor.dispose();
            if (this.innerOutlineColor != null)
                this.innerOutlineColor.dispose();
            this.outerOutlineThickness = outerOutlineThickness;
            this.outerOutlineColor = outerOutlineColor;
            this.outerOutlineFillPercent = outerOutlineFillPercent;
            this.outerOutlineFillStartAngle = outerOutlineFillStartAngle;
            this.outerOutlineFillDirection = outerOutlineFillDirection;
            this.innerOutlineThickness = innerOutlineThickness;
            this.innerOutlineColor = innerOutlineColor;
            this.innerOutlineFillPercent = innerOutlineFillPercent;
            this.innerOutlineFillStartAngle = innerOutlineFillStartAngle;
            this.innerOutlineFillDirection = innerOutlineFillDirection;
        }

        /**
         * Radial distance from a fill vertex to the outline's miter tip,
         * measured along the circumradius direction.
         * The pen center path is at fillRadius + (corrected - 1) / 2 from center
         * (1 = inward overlap). For a regular n-gon, offsetting edges outward by
         * penWidth/2 gives a circumradius of R + penWidth / (2*cos(pi/n)),
         * where penWidth = corrected + 1 (includes inward overlap).
         */
        private static double radialMiterPadding(double visualThickness, int edgeCount) {
            double cos = Math.cos(Math.PI / edgeCount);
            double corrected = (2 * visualThickness - (1 - cos)) / (1 + cos);
            return (corrected - 1.0) / 2.0 + (corrected + 1.0) / (2.0 * cos);
        }

        /**
         * Axis-aligned padding needed around the fill's bounding box to fit
         * the outline's miter tips within a rectangular widget.
         * Projects the radial miter extension onto the x/y axes for each vertex
         * and returns the maximum.
         */
        static double miterPadding(double visualThickness, int edgeCount) {
            double radial = radialMiterPadding(visualThickness, edgeCount);
            double startAngle = polygonStartAngle(edgeCount);
            double maxProjection = 0;
            for (int i = 0; i < edgeCount; i++) {
                double angle = startAngle + 2.0 * Math.PI * i / edgeCount;
                maxProjection = Math.max(maxProjection,
                        Math.max(Math.abs(Math.cos(angle)), Math.abs(Math.sin(angle))));
            }
            return radial * maxProjection;
        }

        private double correctedOutlineThickness(double visualThickness) {
            double cos = Math.cos(Math.PI / edgeCount);
            return (2 * visualThickness - (1 - cos)) / (1 + cos);
        }

        double maxOutlineThickness() {
            double scaled = Math.max(outerOutlineThickness, innerOutlineThickness) * outlineScale;
            return miterPadding(scaled, edgeCount);
        }


        private static double polygonStartAngle(int edgeCount) {
            // Odd edge count: vertex at top (pointy top, e.g. triangle ▲).
            // Even edge count: flat edge at top (e.g. square □, hexagon ⬡).
            double startAngle = -Math.PI / 2;
            if (edgeCount % 2 == 0)
                startAngle += Math.PI / edgeCount;
            return startAngle;
        }

        static QPainterPath polygonPath(double centerX, double centerY,
                                       double radius, int edgeCount) {
            QPainterPath path = new QPainterPath();
            double startAngle = polygonStartAngle(edgeCount);
            for (int i = 0; i < edgeCount; i++) {
                double angle = startAngle + 2.0 * Math.PI * i / edgeCount;
                double x = centerX + radius * Math.cos(angle);
                double y = centerY + radius * Math.sin(angle);
                if (i == 0)
                    path.moveTo(x, y);
                else
                    path.lineTo(x, y);
            }
            path.closeSubpath();
            return path;
        }

        // Returns the circumradius such that the polygon's bounding box
        // largest dimension equals targetSize, and the offset to center
        // the bounding box (the polygon's BB may not be symmetric around
        // the circumcenter, e.g. triangle).
        record PolygonLayout(double radius, double offsetX, double offsetY) {}

        static PolygonLayout polygonLayout(double targetSize, int edgeCount) {
            double startAngle = polygonStartAngle(edgeCount);
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (int i = 0; i < edgeCount; i++) {
                double angle = startAngle + 2.0 * Math.PI * i / edgeCount;
                double cx = Math.cos(angle);
                double cy = Math.sin(angle);
                minX = Math.min(minX, cx);
                maxX = Math.max(maxX, cx);
                minY = Math.min(minY, cy);
                maxY = Math.max(maxY, cy);
            }
            double maxDimension = Math.max(maxX - minX, maxY - minY);
            double radius = targetSize / maxDimension;
            double offsetX = -(minX + maxX) / 2.0 * radius;
            double offsetY = -(minY + maxY) / 2.0 * radius;
            return new PolygonLayout(radius, offsetX, offsetY);
        }

        /**
         * Builds an open path tracing a portion of the polygon outline.
         * fillStartAngle: 0 = top (12 o'clock), increases clockwise, in degrees.
         * fillDirection: BOTH = expand symmetrically from anchor.
         */
        private static QPainterPath partialPolygonPath(double centerX, double centerY,
                                                       double radius, int edgeCount,
                                                       double fillPercent,
                                                       double fillStartAngle,
                                                       FillDirection fillDirection) {
            double polyStartAngle = polygonStartAngle(edgeCount);
            double[] vx = new double[edgeCount];
            double[] vy = new double[edgeCount];
            for (int i = 0; i < edgeCount; i++) {
                double angle = polyStartAngle + 2.0 * Math.PI * i / edgeCount;
                vx[i] = centerX + radius * Math.cos(angle);
                vy[i] = centerY + radius * Math.sin(angle);
            }
            double edgeLength = Math.hypot(vx[1] - vx[0], vy[1] - vy[0]);
            double totalLength = edgeCount * edgeLength;
            double fillLength = fillPercent * totalLength;
            // Convert fillStartAngle (0=top, CW) to math angle for ray intersection.
            // Math convention: 0=right, counter-clockwise positive.
            // Screen coords: y increases downward, so sin is negated.
            double mathAngle = Math.toRadians(90 - fillStartAngle);
            double rayDx = Math.cos(mathAngle);
            double rayDy = -Math.sin(mathAngle); // negate for screen coords
            // Find anchor position on perimeter by intersecting ray from center with polygon edges.
            double anchorPos = findAnchorPos(centerX, centerY, rayDx, rayDy,
                    vx, vy, edgeCount, edgeLength);
            // Build path(s) based on direction.
            // Vertex order is clockwise on screen. Forward = CW, backward = CCW.
            if (fillDirection == FillDirection.BOTH) {
                double halfLength = fillLength / 2.0;
                QPainterPath cwPath = traceAlongPerimeter(
                        vx, vy, edgeCount, edgeLength, totalLength, anchorPos, halfLength, true);
                QPainterPath ccwPath = traceAlongPerimeter(
                        vx, vy, edgeCount, edgeLength, totalLength, anchorPos, halfLength, false);
                QPainterPath combined = ccwPath.toReversed();
                combined.connectPath(cwPath);
                cwPath.dispose();
                ccwPath.dispose();
                return combined;
            }
            else {
                boolean forward = fillDirection == FillDirection.CLOCKWISE;
                return traceAlongPerimeter(
                        vx, vy, edgeCount, edgeLength, totalLength, anchorPos, fillLength, forward);
            }
        }

        /**
         * Finds the perimeter position (distance along polygon edges from vertex 0)
         * where a ray from center in direction (rayDx, rayDy) intersects the polygon.
         */
        private static double findAnchorPos(double centerX, double centerY,
                                            double rayDx, double rayDy,
                                            double[] vx, double[] vy,
                                            int edgeCount, double edgeLength) {
            double bestT = Double.MAX_VALUE;
            int bestEdge = 0;
            double bestFrac = 0;
            for (int i = 0; i < edgeCount; i++) {
                int j = (i + 1) % edgeCount;
                double ex = vx[j] - vx[i];
                double ey = vy[j] - vy[i];
                // Solve: center + t * ray = vertex[i] + s * edge
                double denom = rayDx * ey - rayDy * ex;
                if (Math.abs(denom) < 1e-12)
                    continue;
                double dx = vx[i] - centerX;
                double dy = vy[i] - centerY;
                double t = (dx * ey - dy * ex) / denom;
                double s = (dx * rayDy - dy * rayDx) / denom;
                if (t > 1e-9 && s >= -1e-9 && s <= 1 + 1e-9) {
                    if (t < bestT) {
                        bestT = t;
                        bestEdge = i;
                        bestFrac = Math.max(0, Math.min(1, s));
                    }
                }
            }
            return bestEdge * edgeLength + bestFrac * edgeLength;
        }

        /**
         * Traces a path along the polygon perimeter starting from anchorPos
         * for the given length, either forward (increasing vertex index) or
         * backward (decreasing vertex index).
         */
        private static QPainterPath traceAlongPerimeter(double[] vx, double[] vy,
                                                        int edgeCount, double edgeLength,
                                                        double totalLength, double anchorPos,
                                                        double length, boolean forward) {
            // Compute start point on the perimeter.
            double startPos = forward ? anchorPos : anchorPos;
            int startEdge = (int) (startPos / edgeLength);
            if (startEdge >= edgeCount)
                startEdge = edgeCount - 1;
            double startFrac = (startPos - startEdge * edgeLength) / edgeLength;
            startFrac = Math.max(0, Math.min(1, startFrac));
            int v0 = startEdge;
            int v1 = (startEdge + 1) % edgeCount;
            double sx = vx[v0] + startFrac * (vx[v1] - vx[v0]);
            double sy = vy[v0] + startFrac * (vy[v1] - vy[v0]);
            QPainterPath path = new QPainterPath();
            path.moveTo(sx, sy);
            double remaining = length;
            if (forward) {
                double distInCurrentEdge = (1 - startFrac) * edgeLength;
                int currentEdge = startEdge;
                while (remaining > 1e-6) {
                    int nextV = (currentEdge + 1) % edgeCount;
                    if (remaining >= distInCurrentEdge - 1e-6) {
                        path.lineTo(vx[nextV], vy[nextV]);
                        remaining -= distInCurrentEdge;
                        currentEdge = (currentEdge + 1) % edgeCount;
                        distInCurrentEdge = edgeLength;
                    }
                    else {
                        double frac = remaining / edgeLength;
                        int curV = currentEdge;
                        int nxtV = (currentEdge + 1) % edgeCount;
                        double ex = vx[curV] + frac * (vx[nxtV] - vx[curV]);
                        double ey = vy[curV] + frac * (vy[nxtV] - vy[curV]);
                        path.lineTo(ex, ey);
                        remaining = 0;
                    }
                }
            }
            else {
                // Backward: traverse edges in decreasing index order.
                double distInCurrentEdge = startFrac * edgeLength;
                int currentEdge = startEdge;
                while (remaining > 1e-6) {
                    int curV = currentEdge;
                    if (remaining >= distInCurrentEdge - 1e-6) {
                        path.lineTo(vx[curV], vy[curV]);
                        remaining -= distInCurrentEdge;
                        currentEdge = (currentEdge - 1 + edgeCount) % edgeCount;
                        distInCurrentEdge = edgeLength;
                    }
                    else {
                        int nextV = (currentEdge + 1) % edgeCount;
                        double frac = 1.0 - remaining / edgeLength;
                        double ex = vx[curV] + frac * (vx[nextV] - vx[curV]);
                        double ey = vy[curV] + frac * (vy[nextV] - vy[curV]);
                        path.lineTo(ex, ey);
                        remaining = 0;
                    }
                }
            }
            return path;
        }

        private void clearOutline(QPainter painter, double centerX, double centerY,
                                  double fillRadius, double thickness, double fillPercent,
                                  double fillStartAngle, FillDirection fillDirection) {
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Clear);
            QColor clearColor = new QColor(0, 0, 0);
            drawOutline(painter, centerX, centerY, fillRadius,
                    thickness, clearColor, fillPercent,
                    fillStartAngle, fillDirection, 1.0);
            clearColor.dispose();
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
        }

        private void drawOutline(QPainter painter, double centerX, double centerY,
                                 double fillRadius, double thickness, QColor color,
                                 double fillPercent, double fillStartAngle,
                                 FillDirection fillDirection, double inwardOverlap) {
            if (thickness <= 0 || color == null || color.alpha() == 0 || fillPercent <= 0)
                return;
            // Extend the inner edge inward by inwardOverlap so that the
            // antialiased inner pixels blend with the layer below (e.g. fill)
            // rather than with a different-colored outline underneath.
            double effectiveThickness = thickness + inwardOverlap;
            QPen pen = new QPen(color);
            pen.setWidthF(effectiveThickness);
            pen.setJoinStyle(Qt.PenJoinStyle.MiterJoin);
            painter.setBrush(Qt.BrushStyle.NoBrush);
            // Outer edge stays at fillRadius + thickness.
            // Inner edge moves to fillRadius - inwardOverlap.
            double outlineRadius = fillRadius + (thickness - inwardOverlap) / 2.0;
            if (fillPercent >= 1.0) {
                painter.setPen(pen);
                QPainterPath outlinePath = polygonPath(centerX, centerY, outlineRadius, edgeCount);
                painter.drawPath(outlinePath);
                outlinePath.dispose();
            }
            else {
                pen.setCapStyle(Qt.PenCapStyle.FlatCap);
                painter.setPen(pen);
                QPainterPath outlinePath = partialPolygonPath(
                        centerX, centerY, outlineRadius, edgeCount, fillPercent,
                        fillStartAngle, fillDirection);
                painter.drawPath(outlinePath);
                outlinePath.dispose();
            }
            pen.dispose();
        }

        void drawContent(QPainter painter, QColor fillColor,
                         QColor outerOutlineColor, QColor innerOutlineColor,
                         boolean clearFullArea) {
            double maxOutlinePadding = maxOutlineThickness();
            int outlinePadding = (int) Math.ceil(maxOutlinePadding);
            double availableSize = Math.min(width(), height()) - 2 * outlinePadding;
            PolygonLayout layout = polygonLayout(availableSize, edgeCount);
            double centerX = width() / 2.0 + layout.offsetX;
            double centerY = height() / 2.0 + layout.offsetY;
            double fillRadius = layout.radius;
            QPainterPath fillPath = polygonPath(centerX, centerY, fillRadius, edgeCount);
            double scaledOuter = outerOutlineThickness * outlineScale;
            double scaledInner = innerOutlineThickness * outlineScale;
            double correctedOuter = correctedOutlineThickness(scaledOuter);
            double correctedInner = correctedOutlineThickness(scaledInner);
            // If any part is transparent, clear the area first so the shadow
            // (composited by the effect) doesn't show through.
            if (indicatorHasTransparency()) {
                if (clearFullArea) {
                    // paintEvent: clear the entire indicator area (widget starts
                    // transparent, so this ensures a clean slate).
                    double maxScaled = Math.max(scaledOuter, scaledInner);
                    double radialPad = radialMiterPadding(maxScaled, edgeCount);
                    QPainterPath outerBoundary = polygonPath(centerX, centerY,
                            fillRadius + radialPad, edgeCount);
                    painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Clear);
                    painter.setPen(Qt.PenStyle.NoPen);
                    QColor clearBlack = new QColor(0, 0, 0);
                    QBrush clearBrush = new QBrush(clearBlack);
                    painter.setBrush(clearBrush);
                    painter.drawPath(outerBoundary);
                    clearBrush.dispose();
                    clearBlack.dispose();
                    outerBoundary.dispose();
                    painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
                }
             }
            // Draw fill first, then outlines on top. Outlines cover the fill
            // boundary with their inwardOverlap, preventing artifacts from
            // opacity differences between fill and outline.
            if (fillColor.alpha() != 0) {
                if (!clearFullArea && fillColor.alpha() < 255) {
                    // redrawSourceOverShadow with semi-transparent fill: use
                    // Source mode to replace the opaque content from drawSource.
                    painter.setCompositionMode(
                            QPainter.CompositionMode.CompositionMode_Source);
                    painter.setPen(Qt.PenStyle.NoPen);
                    QBrush fillBrush = new QBrush(fillColor);
                    painter.setBrush(fillBrush);
                    painter.drawPath(fillPath);
                    fillBrush.dispose();
                    painter.setCompositionMode(
                            QPainter.CompositionMode.CompositionMode_SourceOver);
                }
                else {
                    painter.setPen(Qt.PenStyle.NoPen);
                    QBrush fillBrush = new QBrush(fillColor);
                    painter.setBrush(fillBrush);
                    painter.drawPath(fillPath);
                    fillBrush.dispose();
                }
            }
            // Draw outer outline on top of fill.
            if (!clearFullArea && outerOutlineColor != null && outerOutlineColor.alpha() > 0
                    && outerOutlineColor.alpha() < 255) {
                // redrawSourceOverShadow: clear the outline area first to
                // remove the opaque outline from drawSource, preserving shadow
                // in gaps of partial outlines.
                clearOutline(painter, centerX, centerY, fillRadius,
                        correctedOuter, outerOutlineFillPercent,
                        outerOutlineFillStartAngle, outerOutlineFillDirection);
            }
            drawOutline(painter, centerX, centerY, fillRadius,
                    correctedOuter, outerOutlineColor, outerOutlineFillPercent,
                    outerOutlineFillStartAngle, outerOutlineFillDirection, 1.0);
            // Draw inner outline on top of outer outline. Compute a larger
            // inwardOverlap so the inner outline's inner miter tip extends
            // past the outer outline's inner miter tip by at least `margin`
            // pixels. Without this, the outer outline color bleeds through
            // the inner outline's antialiased inner edge, especially at
            // vertices of low-edge-count polygons (e.g. triangles).
            // Formula derived from equating the radial miter tip positions:
            //   tip = fillRadius + (corrected - overlap)/2
            //         - (corrected + overlap) / (2*cos(PI/n))
            double innerInwardOverlap;
            if (correctedOuter > 0 && correctedInner > 0) {
                double cos = Math.cos(Math.PI / edgeCount);
                double D = correctedOuter - correctedInner;
                double margin = 1.5;
                innerInwardOverlap = D * (1 - cos) / (1 + cos)
                        + 1.0 + 2.0 * margin * cos / (1 + cos);
                innerInwardOverlap = Math.max(innerInwardOverlap, 1.0);
            }
            else {
                innerInwardOverlap = 1.0;
            }
            if (!clearFullArea && innerOutlineColor != null && innerOutlineColor.alpha() > 0
                    && innerOutlineColor.alpha() < 255) {
                clearOutline(painter, centerX, centerY, fillRadius,
                        correctedInner, innerOutlineFillPercent,
                        innerOutlineFillStartAngle, innerOutlineFillDirection);
            }
            drawOutline(painter, centerX, centerY, fillRadius,
                    correctedInner, innerOutlineColor, innerOutlineFillPercent,
                    innerOutlineFillStartAngle, innerOutlineFillDirection, innerInwardOverlap);
            fillPath.dispose();
        }

        @Override
        protected void paintEvent(QPaintEvent event) {
            QPainter painter = new QPainter(this);
            if (cleared) {
                // Paint fully transparent so DWM's cached surface is blank.
                painter.setCompositionMode(
                        QPainter.CompositionMode.CompositionMode_Clear);
                QRect r = rect();
                QColor c = new QColor(0, 0, 0, 0);
                painter.fillRect(r, c);
                r.dispose();
                c.dispose();
                painter.end();
                painter.dispose();
                return;
            }
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            drawContent(painter, color, outerOutlineColor, innerOutlineColor, true);
            painter.end();
            painter.dispose();
        }
    }

    public static class StackedShadowEffect extends QGraphicsDropShadowEffect {

        private int stackCount;
        private boolean transparencyOnly;

        void setStackCount(int stackCount) {
            this.stackCount = stackCount;
        }

        void setTransparencyOnly(boolean transparencyOnly) {
            this.transparencyOnly = transparencyOnly;
        }

        @Override
        protected void draw(QPainter painter) {
            if (transparencyOnly) {
                redrawSourceOverShadow(painter);
                return;
            }
            if (stackCount <= 1) {
                super.draw(painter);
                redrawSourceOverShadow(painter);
                return;
            }
            // Pre-render the shadow separately, bake stacking, then draw
            // the stacked shadow and source independently.
            QPoint sourceOffset = new QPoint();
            QPixmap sourcePixmap = sourcePixmap(
                    Qt.CoordinateSystem.DeviceCoordinates, sourceOffset,
                    PixmapPadMode.PadToEffectiveBoundingRect);
            QImage sourceImage = sourcePixmap.toImage();
            int w = sourceImage.width();
            int h = sourceImage.height();
            QColor shadowColor = color();
            ShadowImage shadow = renderShadowOnly(sourceImage, shadowColor,
                    blurRadius(), xOffset(), yOffset(), w, h);
            shadowColor.dispose();
            QImage stackedShadow = bakeStacking(shadow.image(), stackCount);
            QTransform savedTransform = painter.worldTransform();
            QTransform identity = new QTransform();
            painter.setWorldTransform(identity);
            painter.drawImage(sourceOffset.x() + shadow.x(),
                    sourceOffset.y() + shadow.y(), stackedShadow);
            stackedShadow.dispose();
            painter.setWorldTransform(savedTransform);
            savedTransform.dispose();
            identity.dispose();
            sourceImage.dispose();
            sourcePixmap.dispose();
            sourceOffset.dispose();
            drawSource(painter);
            redrawSourceOverShadow(painter);
        }

        protected void redrawSourceOverShadow(QPainter painter) {
            // No-op by default. Subclasses override to clear and redraw
            // source content, preventing shadow from showing through
            // transparent parts.
        }

        /**
         * Intensifies an image by computing the closed-form result of compositing
         * it on top of itself stackCount times (premultiplied alpha geometric series).
         * Returns a new QImage (caller must dispose the original if different).
         */
        static QImage bakeStacking(QImage image, int stackCount) {
            if (stackCount <= 1)
                return image;
            int w = image.width();
            int h = image.height();
            int totalBytes = w * h * 4;
            ByteBuffer buf = image.bits();
            byte[] pixels = new byte[totalBytes];
            buf.position(0);
            buf.get(pixels);
            // Precompute multiplier for each possible alpha value.
            // For premultiplied alpha, stacking N times multiplies all channels by
            // (1 - t^N) / (1 - t) where t = 1 - a/255.
            double[] multiplier = new double[256];
            for (int a = 1; a <= 255; a++) {
                double t = 1.0 - a / 255.0;
                multiplier[a] = (1.0 - Math.pow(t, stackCount)) / (a / 255.0);
            }
            // ARGB32_Premultiplied, little-endian: B, G, R, A.
            for (int i = 0; i < totalBytes; i += 4) {
                int a = pixels[i + 3] & 0xFF;
                if (a == 0) continue;
                double m = multiplier[a];
                pixels[i]     = (byte) Math.min(255, (int) (((pixels[i]     & 0xFF) * m) + 0.5));
                pixels[i + 1] = (byte) Math.min(255, (int) (((pixels[i + 1] & 0xFF) * m) + 0.5));
                pixels[i + 2] = (byte) Math.min(255, (int) (((pixels[i + 2] & 0xFF) * m) + 0.5));
                pixels[i + 3] = (byte) Math.min(255, (int) ((a * m) + 0.5));
            }
            image.dispose();
            return new QImage(pixels, w, h, QImage.Format.Format_ARGB32_Premultiplied);
        }
    }

    public static class IndicatorShadowEffect extends StackedShadowEffect {

        private final IndicatorWidget widget;
        private QColor fillColor;
        private QColor outerOutlineColor;
        private QColor innerOutlineColor;

        IndicatorShadowEffect(IndicatorWidget widget) {
            this.widget = widget;
        }

        void setColors(QColor fillColor, QColor outerOutlineColor,
                       QColor innerOutlineColor) {
            if (this.fillColor != null)
                this.fillColor.dispose();
            if (this.outerOutlineColor != null)
                this.outerOutlineColor.dispose();
            if (this.innerOutlineColor != null)
                this.innerOutlineColor.dispose();
            this.fillColor = fillColor;
            this.outerOutlineColor = outerOutlineColor;
            this.innerOutlineColor = innerOutlineColor;
        }

        @Override
        protected void draw(QPainter painter) {
            if (widget.cleared) {
                // Just draw the source (triggers paintEvent which clears).
                // Skip shadow and redrawSourceOverShadow.
                drawSource(painter);
                return;
            }
            super.draw(painter);
        }

        @Override
        protected void redrawSourceOverShadow(QPainter painter) {
            if (!indicatorHasTransparency())
                return;
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            widget.drawContent(painter, fillColor, outerOutlineColor, innerOutlineColor, false);
        }
    }


    private static class IndicatorLabelWidget extends QWidget {

        private String labelText;
        private QFont labelFont;
        private double labelFontSize;
        private double labelFontScale;
        private QColor labelColor;
        private int outlineThickness;
        private QColor outlineColor;
        private int edgeCount;
        private int indicatorOutlinePadding;

        IndicatorLabelWidget(QWidget parent) {
            super(parent);
            setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents);
        }

        void setIndicatorOutlinePadding(int padding) {
            this.indicatorOutlinePadding = padding;
        }

        void setLabelFontScale(double scale) {
            this.labelFontScale = scale;
        }

        void setLabel(String labelText, QFont labelFont, double fontSize,
                      QColor labelColor,
                      int outlineThickness, QColor outlineColor,
                      int edgeCount) {
            if (this.labelFont != null)
                this.labelFont.dispose();
            if (this.labelColor != null)
                this.labelColor.dispose();
            if (this.outlineColor != null)
                this.outlineColor.dispose();
            this.labelText = labelText;
            this.labelFont = labelFont;
            this.labelFontSize = fontSize;
            this.labelColor = labelColor;
            this.outlineThickness = outlineThickness;
            this.outlineColor = outlineColor;
            this.edgeCount = edgeCount;
            update();
        }

        @Override
        protected void paintEvent(QPaintEvent event) {
            if (labelText == null || labelFont == null || labelColor == null)
                return;
            labelFont.setPointSize((int) Math.round(labelFontSize * labelFontScale));
            QPainter painter = new QPainter(this);
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            painter.setFont(labelFont);
            QFontMetrics fm = new QFontMetrics(labelFont);
            double availableSize = Math.min(width(), height()) - 2 * indicatorOutlinePadding;
            IndicatorWidget.PolygonLayout polygonLayout =
                    IndicatorWidget.polygonLayout(availableSize, edgeCount);
            double centerX = width() / 2.0 + polygonLayout.offsetX();
            double centerY = height() / 2.0 + polygonLayout.offsetY();
            int textX = (int) Math.round(centerX - fm.horizontalAdvance(labelText) / 2.0);
            QRect tightRect = fm.tightBoundingRect(labelText);
            int textY = (int) Math.round(centerY - tightRect.y() - tightRect.height() / 2.0);
            tightRect.dispose();
            // Outline: draw text path with outline pen.
            if (outlineThickness != 0 && outlineColor != null && outlineColor.alpha() != 0) {
                QPen outlinePen = new QPen(outlineColor);
                outlinePen.setWidth(outlineThickness);
                outlinePen.setJoinStyle(Qt.PenJoinStyle.RoundJoin);
                painter.setPen(outlinePen);
                painter.setBrush(Qt.BrushStyle.NoBrush);
                QPainterPath textPath = new QPainterPath();
                textPath.addText(textX, textY, labelFont, labelText);
                painter.drawPath(textPath);
                textPath.dispose();
                outlinePen.dispose();
            }
            // Fill: draw text on top of outline.
            if (labelColor.alpha() != 0) {
                painter.setPen(labelColor);
                painter.drawText(textX, textY, labelText);
            }
            fm.dispose();
            painter.end();
            painter.dispose();
        }
    }

    private record GridWindow(WinDef.HWND hwnd, WinUser.WindowProc callback, int transparentColor) {

    }

    private record HintMeshWindow(WinDef.HWND hwnd,
                                  TransparentWindow window,
                                  List<Hint> hints, Zoom zoom,
                                  List<QVariantAnimation> animations,
                                  List<QMetaObject.AbstractSlot> animationCallbacks,
                                  AtomicReference<HintMesh> lastHintMeshKeyReference) {

    }

    private record ZoomWindow(WinDef.HWND hwnd, WinDef.HWND hostHwnd, WinUser.WindowProc callback) {

    }

    private static class ScreenshotWidget extends QWidget {
        private QPixmap pixmap;
        private Zoom zoom;
        private Rectangle screenRect;

        ScreenshotWidget() {
            setWindowFlags(Qt.WindowType.FramelessWindowHint);
            setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground);
        }

        void setScreenshot(QPixmap pixmap, Rectangle screenRect) {
            this.pixmap = pixmap;
            this.screenRect = screenRect;
        }

        void setZoom(Zoom zoom) {
            this.zoom = zoom;
        }

        @Override
        protected void paintEvent(QPaintEvent event) {
            if (pixmap == null || zoom == null)
                return;
            double zoomPercent = zoom.percent();
            double localCenterX = zoom.center().x() - screenRect.x();
            double localCenterY = zoom.center().y() - screenRect.y();
            double sourceWidth = screenRect.width() / zoomPercent;
            double sourceHeight = screenRect.height() / zoomPercent;
            double sourceX = localCenterX - sourceWidth / 2;
            double sourceY = localCenterY - sourceHeight / 2;
            QPainter painter = new QPainter(this);
            painter.fillRect(0, 0, width(), height(), new QColor(0, 0, 0));
            painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform, true);
            QRectF sourceRect = new QRectF(sourceX, sourceY, sourceWidth, sourceHeight);
            QRectF targetRect = new QRectF(0, 0, width(), height());
            painter.drawPixmap(targetRect, pixmap, sourceRect);
            sourceRect.dispose();
            targetRect.dispose();
            painter.end();
            painter.dispose();
        }
    }

    private static int indicatorSize(double screenScale) {
        return scaledPixels(currentIndicator.size(), screenScale);
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

    private static final int indicatorEdgeThreshold = 100;

    /**
     * Returns the indicator top-left position for the given indicator size.
     * For CENTER, the indicator is centered on the cursor's visual center.
     * For corner positions, the indicator is placed in that corner relative to the cursor,
     * flipping to the opposite side when near the corresponding screen edge.
     */
    private static Point indicatorTopLeft(WinDef.POINT mousePosition,
                                          Screen activeScreen, int visualSize) {
        Rectangle screen = activeScreen.rectangle();
        if (currentIndicator.position() == IndicatorPosition.CENTER) {
            Point cursorCenter = WindowsMouse.cursorVisualCenter();
            double centerX = mousePosition.x + cursorCenter.x();
            double centerY = mousePosition.y + cursorCenter.y();
            centerX = Math.max(screen.x(), Math.min(centerX,
                    screen.x() + screen.width()));
            centerY = Math.max(screen.y(), Math.min(centerY,
                    screen.y() + screen.height()));
            return new Point(zoomedX(centerX) - visualSize / 2.0,
                    zoomedY(centerY) - visualSize / 2.0);
        }
        WindowsMouse.MouseSize mouseSize = WindowsMouse.mouseSize();
        int mouseX = Math.max(screen.x(), Math.min(mousePosition.x,
                screen.x() + screen.width()));
        int mouseY = Math.max(screen.y(), Math.min(mousePosition.y,
                screen.y() + screen.height()));
        IndicatorPosition position = currentIndicator.position();
        boolean defaultRight = position == IndicatorPosition.BOTTOM_RIGHT ||
                               position == IndicatorPosition.TOP_RIGHT;
        boolean defaultBottom = position == IndicatorPosition.BOTTOM_RIGHT ||
                                position == IndicatorPosition.BOTTOM_LEFT;
        boolean nearRightEdge = mouseX >=
                screen.x() + screen.width() - indicatorEdgeThreshold;
        boolean nearLeftEdge = mouseX <=
                screen.x() + indicatorEdgeThreshold;
        boolean placeRight = defaultRight ? !nearRightEdge : nearLeftEdge;
        int indicatorX = placeRight ?
                mouseX + mouseSize.width() / 2 : mouseX - visualSize;
        boolean nearBottomEdge = mouseY >=
                screen.y() + screen.height() - indicatorEdgeThreshold;
        boolean nearTopEdge = mouseY <=
                screen.y() + indicatorEdgeThreshold;
        boolean placeBottom = defaultBottom ? !nearBottomEdge : nearTopEdge;
        int indicatorY = placeBottom ?
                mouseY + mouseSize.height() / 2 : mouseY - visualSize;
        return new Point(zoomedX(indicatorX), zoomedY(indicatorY));
    }

    private static int indicatorOutlinePadding(double scale) {
        double scaled = Math.max(
                currentIndicator.outerOutline().thickness(),
                currentIndicator.innerOutline().thickness()) * scale * zoomPercent();
        return (int) Math.ceil(IndicatorWidget.miterPadding(scaled, currentIndicator.edgeCount()));
    }

    private static int indicatorShadowPadding(double scale) {
        if (currentIndicator.shadow().blurRadius() == 0)
            return 0;
        return (int) Math.ceil((currentIndicator.shadow().blurRadius() +
                Math.max(Math.abs(currentIndicator.shadow().horizontalOffset()),
                         Math.abs(currentIndicator.shadow().verticalOffset()))) * scale);
    }

    private static void moveAndResizeIndicatorWindow() {
        moveAndResizeIndicatorWindow(WindowsMouse.findMousePosition());
    }

    private static void moveAndResizeIndicatorWindow(WinDef.POINT mousePosition) {
        Screen activeScreen = WindowsScreen.findActiveScreen(mousePosition);
        double screenScale = activeScreen.scale();
        int size = indicatorSize(screenScale);
        int outlinePadding = indicatorOutlinePadding(screenScale);
        indicatorWindow.widget.setOutlineScale(screenScale * zoomPercent());
        int shadowPadding = indicatorShadowPadding(screenScale * zoomPercent());
        // When switching from a shadow indicator to a no-shadow indicator, the window
        // used to shrink dramatically (e.g., 62px to 26px).
        // The DWM compositor shows the old window surface at the new smaller size for
        // one frame before the new paint arrives, causing the indicator to appear
        // mispositioned. By never shrinking, the window stays at 62px — no resize means
        // no stale frame from the compositor.
        // - The window is larger than needed when the current indicator has no shadow.
        // The extra area is transparent and invisible.
        // - The indicator position is unaffected: the window is at bestX - shadowPadding,
        // the widget is at (shadowPadding, shadowPadding) within it, so the visible
        // indicator is always at bestX regardless of how large the shadow padding is.
        maxIndicatorShadowPadding = Math.max(maxIndicatorShadowPadding, shadowPadding);
        shadowPadding = maxIndicatorShadowPadding;
        int totalPadding = outlinePadding + shadowPadding;
        int widgetSize = size + 2 * outlinePadding;
        int windowSize = size + 2 * totalPadding;
        int visualSize = size + 2 * outlinePadding;
        Point topLeft = indicatorTopLeft(mousePosition, activeScreen, visualSize);
        int indicatorX = (int) Math.round(topLeft.x());
        int indicatorY = (int) Math.round(topLeft.y());
        indicatorWindow.window.move(indicatorX - shadowPadding,
                indicatorY - shadowPadding);
        indicatorWindow.window.resize(windowSize, windowSize);
        indicatorWindow.widget.move(shadowPadding, shadowPadding);
        indicatorWindow.widget.resize(widgetSize, widgetSize);
        indicatorWindow.labelWidget.move(shadowPadding, shadowPadding);
        indicatorWindow.labelWidget.resize(widgetSize, widgetSize);
        indicatorWindow.labelWidget.setIndicatorOutlinePadding(outlinePadding);
        indicatorWindow.labelWidget.setLabelFontScale(zoomPercent());
    }

    private static boolean indicatorHasTransparency() {
        if (currentIndicator.opacity() < 1.0)
            return true;
        IndicatorOutline outer = currentIndicator.outerOutline();
        IndicatorOutline inner = currentIndicator.innerOutline();
        return (outer.thickness() > 0 && outer.opacity() < 1.0) ||
               (inner.thickness() > 0 && inner.opacity() < 1.0);
    }

    private static boolean qtFontStyleHasTransparency(QtFontStyle qtFontStyle) {
        if (qtFontStyle.outlineThickness() != 0 &&
            qtFontStyle.outlineColor().alpha() < 255 &&
            // 0 means outline will not be rendered.
            qtFontStyle.outlineColor().alpha() != 0)
            return true;
        if (qtFontStyle.color().alpha() < 255 && qtFontStyle.color().alpha() != 0)
            return true;
        return false;
    }

    private static boolean qtHintFontStyleHasTransparency(QtHintFontStyle style,
                                                          boolean hasSelectedKeys) {
        if (qtFontStyleHasTransparency(style.defaultStyle()) ||
               (hasSelectedKeys && qtFontStyleHasTransparency(style.selectedStyle())) ||
               qtFontStyleHasTransparency(style.focusedStyle()))
            return true;
        if (style.prefixDefaultStyle() != null) {
            if (qtFontStyleHasTransparency(style.prefixDefaultStyle()) ||
                   (hasSelectedKeys && qtFontStyleHasTransparency(style.prefixSelectedStyle())) ||
                   qtFontStyleHasTransparency(style.prefixFocusedStyle()))
                return true;
        }
        return false;
    }

    private static void setIndicatorEffectColors(IndicatorShadowEffect effect) {
        IndicatorOutline outer = currentIndicator.outerOutline();
        IndicatorOutline inner = currentIndicator.innerOutline();
        effect.setColors(
                qColor(currentIndicator.hexColor(), currentIndicator.opacity()),
                qColor(outer.hexColor(), outer.opacity()),
                qColor(inner.hexColor(), inner.opacity()));
    }

    private static void applyIndicatorShadowEffect(double scale) {
        Shadow shadow = currentIndicator.shadow();
        QColor baseColor = qColor(shadow.hexColor(), 1.0);
        boolean hasShadow = shadow.opacity() > 0 && shadow.blurRadius() > 0;
        if (hasShadow) {
            IndicatorShadowEffect effect = new IndicatorShadowEffect(indicatorWindow.widget);
            effect.setBlurRadius(shadow.blurRadius() * scale);
            effect.setOffset(shadow.horizontalOffset() * scale,
                    shadow.verticalOffset() * scale);
            int alpha = (int) Math.round(shadow.opacity() * 255);
            QColor shadowColor = new QColor(baseColor.red(), baseColor.green(),
                    baseColor.blue(), alpha);
            effect.setColor(shadowColor);
            shadowColor.dispose();
            effect.setStackCount(shadow.stackCount());
            setIndicatorEffectColors(effect);
            indicatorWindow.widget.customGraphicsEffect = effect;
            indicatorWindow.widget.setGraphicsEffect(effect);
        }
        else if (indicatorHasTransparency()) {
            IndicatorShadowEffect effect = new IndicatorShadowEffect(indicatorWindow.widget);
            effect.setTransparencyOnly(true);
            setIndicatorEffectColors(effect);
            indicatorWindow.widget.customGraphicsEffect = effect;
            indicatorWindow.widget.setGraphicsEffect(effect);
        }
        else {
            indicatorWindow.widget.customGraphicsEffect = null;
            indicatorWindow.widget.setGraphicsEffect(null);
        }
        baseColor.dispose();
    }

    private static void createIndicatorWindow() {
        TransparentWindow window = new TransparentWindow();
        IndicatorWidget widget = new IndicatorWidget(window);
        WinDef.HWND hwnd = new WinDef.HWND(new Pointer(window.winId()));
        long currentStyle =
                User32.INSTANCE.GetWindowLongPtr(hwnd, WinUser.GWL_EXSTYLE)
                               .longValue();
        long newStyle = currentStyle | User32.WS_EX_TOPMOST |
                        ExtendedUser32.WS_EX_NOACTIVATE |
                        ExtendedUser32.WS_EX_TOOLWINDOW |
                        ExtendedUser32.WS_EX_LAYERED | ExtendedUser32.WS_EX_TRANSPARENT;
        User32.INSTANCE.SetWindowLongPtr(hwnd, WinUser.GWL_EXSTYLE,
                new Pointer(newStyle));
        // Label widget is a child of window (not widget) so it renders on top
        // of the shadow effect and can clear/redraw the fill area.
        IndicatorLabelWidget labelWidget = new IndicatorLabelWidget(window);
        indicatorWindow = new IndicatorWindow(hwnd, window, widget, labelWidget);
        WinDef.POINT mousePosition = WindowsMouse.findMousePosition();
        Screen activeScreen = WindowsScreen.findActiveScreen(mousePosition);
        applyIndicatorShadowEffect(activeScreen.scale() * zoomPercent());
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
        Map<Screen, List<Hint>> hintsByScreen = hintsByScreen(hintMesh.hints());
        if (hintsByScreen.isEmpty() && hintMesh.backgroundArea() != null) {
            Rectangle backgroundArea = hintMesh.backgroundArea();
            for (Screen screen : WindowsScreen.findScreens()) {
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
                HintMeshWindow hintMeshWindow =
                        createHintMeshWindow(screen, hintsInScreen, zoom);
                hintMeshWindows.put(screen, hintMeshWindow);
                createdAtLeastOneWindow = true;
                setHintMeshWindow(hintMeshWindow, hintMesh, screen.scale(), style, false,
                        null);
            }
            else {
                HintMeshWindow hintMeshWindow = new HintMeshWindow(existingWindow.hwnd,
                        existingWindow.window,
                        hintsInScreen, zoom, existingWindow.animations(),
                        existingWindow.animationCallbacks(),
                        existingWindow.lastHintMeshKeyReference());
                boolean zoomChanged = existingWindow.zoom == null || !existingWindow.zoom.equals(zoom);
                hintMeshWindows.put(screen, hintMeshWindow);
//                TransparentWindow window = existingWindow.window;
//                logger.debug("Showing hints " + hintsInScreen.size() + " for " + screen + ", window = " + existingWindow.window.x() + " " + existingWindow.window.y() + " " + existingWindow.window.width() + " " + existingWindow.window.height());
                setHintMeshWindow(hintMeshWindow, hintMesh, screen.scale(), style, zoomChanged,
                        null);
            }
        }
        if (createdAtLeastOneWindow)
            updateZoomExcludedWindows();
    }

    private static Map<Screen, List<Hint>> hintsByScreen(List<Hint> hints) {
        Set<Screen> screens = WindowsScreen.findScreens();
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

    private static class ClearBackgroundQLabel extends QLabel {
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

    private static void setHintMeshWindow(HintMeshWindow hintMeshWindow,
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
                qColor(style.backgroundHexColor(), style.backgroundOpacity()) : null;
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

    private static void transitionHintContainers(boolean animateTransition, QWidget oldContainer,
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

    private static QRect paddedRect(QRect rect) {
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

        public HintContainerAnimationFinished(QWidget oldContainer, QWidget animatedContainer,
                                              QRect endRect) {
            this.oldContainer = oldContainer;
            this.animatedContainer = animatedContainer;
            this.endRect = endRect;
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

    private static Map<List<Key>, QRect> setUncachedHintMeshWindow(HintMeshWindow hintMeshWindow, HintMesh hintMesh,
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
        QtHintFontStyle labelFontStyle = buildQtHintFontStyle(style.fontStyle(), prefixFontStyle, screenScale, hasSelectedKeys);
        QColor boxColor = qColor(style.boxHexColor(), style.boxOpacity());
        QColor boxBorderColor = qColor(style.boxBorderHexColor(), style.boxBorderOpacity());
        QColor prefixBoxBorderColor = qColor(style.prefixBoxBorderHexColor(), style.prefixBoxBorderOpacity());
        QColor subgridBoxColor = qColor("#000000", 0);
        QColor subgridBoxBorderColor = qColor(style.subgridBorderHexColor(),
                style.subgridBorderOpacity());
        int hintGridColumnCount = isHintPartOfGrid ? hintGridColumnCount(hintMeshWindow.hints()) : -1;
        Map<String, Integer> xAdvancesByString = new HashMap<>();
        int hintKeyMaxXAdvance = 0;
        for (Hint hint : hints) {
            for (Key key : hint.keySequence()) {
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
            int totalXAdvance = labelFontStyle.defaultStyle()
                                              .metrics()
                                              .horizontalAdvance(hint.keySequence()
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
            List<Key> prefix = hintMesh.prefixLength() == -1 ?
                    List.of() : hint.keySequence().subList(0,
                    hintMesh.prefixLength());
            List<Key> suffix = hint.keySequence().subList(prefix.size(), hint.keySequence().size());
            HintLabel hintLabel =
                    new HintLabel(
                            style.prefixInBackground() ? suffix : hint.keySequence(),
                            xAdvancesByString, fullBoxWidth,
                            fullBoxHeight, totalXAdvance,
                            style.prefixInBackground() ? -1 : hintMesh.prefixLength(),
                            labelFontStyle,
                            hintKeyMaxXAdvance,
                            hintMesh.selectedKeySequence().size() - 1
                            - (style.prefixInBackground() && hintMesh.prefixLength() != -1 ? prefix.size() : 0));
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
            HintBox[][] subgridBoxes = addSubgridBoxes(hintBox, qtScaleFactor,
                    subgridBoxColor,
                    subgridBoxBorderColor,
                    style.subgridRowCount(), style.subgridColumnCount(),
                    style.subgridBorderLength(), style.subgridBorderThickness());
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
                            qColor("#000000", 0),
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
            prefixQtHintFontStyle = buildQtHintFontStyle(style.prefixFontStyle(), null, screenScale, hasSelectedKeys);
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

    private static HintMeshWindow createHintMeshWindow(Screen screen, List<Hint> hints,
                                                       Zoom zoom) {
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
        return new HintMeshWindow(hwnd, window, hints, zoom,
                new ArrayList<>(), new ArrayList<>(), new AtomicReference<>());
    }

    static void preWarmHintMeshWindows() {
        long before = System.nanoTime();
        Set<Screen> screens = WindowsScreen.findScreens();
        for (Screen screen : screens) {
            if (hintMeshWindows.containsKey(screen))
                continue;
            hintMeshWindows.put(screen, createHintMeshWindow(screen, new ArrayList<>(), null));
        }
        logger.info("Pre-warmed hint mesh windows for " + screens.size() +
                " screens in " + (long) ((System.nanoTime() - before) / 1e6) + "ms");
        updateZoomExcludedWindows();
    }

    /**
     * Pre-warms the GDI font engine with all hint fonts from the configuration.
     * The first QFontMetrics.horizontalAdvance() call for a given font triggers lazy
     * GDI font engine initialization (~130ms). By doing this at startup, we shift that
     * cost away from the first hint mesh render.
     */
    static void preWarmFontStyles(Set<HintMeshConfiguration> hintMeshConfigurations) {
        long before = System.nanoTime();
        Set<HintFontStyle> fontStyles = new HashSet<>();
        for (HintMeshConfiguration hintMeshConfiguration : hintMeshConfigurations) {
            for (HintMeshStyle style : hintMeshConfiguration.styleByFilter().map().values()) {
                fontStyles.add(style.fontStyle());
                fontStyles.add(style.prefixFontStyle());
            }
        }
        for (HintFontStyle hintFontStyle : fontStyles) {
            QFont font = qFont(hintFontStyle.defaultFontStyle().name(), hintFontStyle.defaultFontStyle().size(), hintFontStyle.defaultFontStyle().weight());
            QFontMetrics fontMetrics = new QFontMetrics(font);
            fontMetrics.horizontalAdvance("x");
            fontMetrics.dispose();
            font.dispose();
            if (!fontShapeEquals(hintFontStyle.defaultFontStyle(), hintFontStyle.selectedFontStyle())) {
                QFont selectedFont = qFont(hintFontStyle.selectedFontStyle().name(), hintFontStyle.selectedFontStyle().size(), hintFontStyle.selectedFontStyle().weight());
                QFontMetrics selectedFontMetrics = new QFontMetrics(selectedFont);
                selectedFontMetrics.horizontalAdvance("x");
                selectedFontMetrics.dispose();
                selectedFont.dispose();
            }
            if (!fontShapeEquals(hintFontStyle.defaultFontStyle(), hintFontStyle.focusedFontStyle())) {
                QFont focusedFont = qFont(hintFontStyle.focusedFontStyle().name(), hintFontStyle.focusedFontStyle().size(), hintFontStyle.focusedFontStyle().weight());
                QFontMetrics focusedFontMetrics = new QFontMetrics(focusedFont);
                focusedFontMetrics.horizontalAdvance("x");
                focusedFontMetrics.dispose();
                focusedFont.dispose();
            }
        }
        logger.debug("Pre-warmed " + fontStyles.size() + " hint font styles in " +
                (long) ((System.nanoTime() - before) / 1e6) + "ms");
    }

    private static QFont qFont(String fontName, double fontSize, FontWeight fontWeight) {
        QFont font = new QFont(fontName, (int) Math.round(fontSize),
                fontWeight.qtWeight().value());
        font.setStyleStrategy(QFont.StyleStrategy.PreferAntialias);
        font.setHintingPreference(QFont.HintingPreference.PreferFullHinting);
        return font;
    }

    /**
     * GDI renders text at the monitor's actual DPI, but QFontMetrics uses Qt's
     * logical DPI (the primary screen's DPI when QT_ENABLE_HIGHDPI_SCALING=0).
     * On multi-monitor setups with different scales, create a pixel-size font
     * for metrics so they match the actual GDI rendering on the target screen.
     *
     * Note: we use the primary screen's DPI rather than window.logicalDpiX()
     * because QFontMetrics always resolves point-size fonts using the primary
     * screen's DPI, but window.logicalDpiX() can change after the window is
     * shown on a non-primary screen (Qt detects the monitor DPI), creating a
     * false "no correction needed" match on subsequent calls.
     */
    private static QFontMetrics correctedFontMetricsForScreenDpi(QFont renderFont, double fontSizePoints,
                                                double screenScale) {
        double primaryScreenDpi = QApplication.primaryScreen().logicalDotsPerInchX();
        double targetDpi = screenScale * 96.0;
        if (Math.abs(primaryScreenDpi - targetDpi) < 1) {
            // QFontMetrics already matches the target DPI, no correction needed.
            return new QFontMetrics(renderFont);
        }
        // Create a metrics-only font with the pixel size that GDI will use.
        int correctedPixelSize = (int) Math.round(fontSizePoints * targetDpi / 72.0);
        QFont metricsFont = new QFont(renderFont.family());
        metricsFont.setPixelSize(correctedPixelSize);
        metricsFont.setWeight(renderFont.weight());
        metricsFont.setStyleStrategy(QFont.StyleStrategy.PreferAntialias);
        metricsFont.setHintingPreference(QFont.HintingPreference.PreferFullHinting);
        QFontMetrics metrics = new QFontMetrics(metricsFont);
        metricsFont.dispose();
        return metrics;
    }

    /**
     * Sets the QImage DPI to match the target screen so that point-size fonts
     * render at the correct pixel size. Without this, text painted into off-screen
     * QImages uses the primary screen's DPI, causing wrong-sized glyphs on
     * secondary screens with different scaling.
     */
    private static void setQImageDpiForScreen(QImage image, double screenScale) {
        int dotsPerMeter = (int) Math.round(screenScale * 96.0 / 0.0254);
        image.setDotsPerMeterX(dotsPerMeter);
        image.setDotsPerMeterY(dotsPerMeter);
    }

    private static void cacheQtHintWindowIntoPixmap(TransparentWindow window, QWidget container,
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

    private static List<Hint> trimmedHints(List<Hint> hints,
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
                        null, (int) Math.round(subgridBorderLength),
                        (int) Math.round(subgridBorderThickness),
                        subgridBoxColor, // Transparent.
                        subgridBoxBorderColor,
                        true,
                        gridLeftEdge, gridTopEdge, gridRightEdge, gridBottomEdge,
                        false,
                        qtScaleFactor,
                        0
                );
                hintBox.subgridBoxes.add(subBox);
                hintBoxes[subgridRowIndex][subgridColumnIndex] = subBox;
            }
        }
        return hintBoxes;
    }

    private record PixmapAndPosition(QPixmap pixmap, int x, int y, HintMesh originalHintMesh, int windowX, int windowY) {
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
            if (Math.abs(left - hints.get(i).centerX()) < 0.01)
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
                    drawGridEdgeBorders || (!gridLeftEdge && !gridTopEdge),
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
                    drawGridEdgeBorders || (!gridTopEdge && !gridLeftEdge),
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
                    (drawGridEdgeBorders && gridRightEdge) || (!drawGridEdgeBorders && !gridRightEdge && !gridTopEdge),
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
                    (drawGridEdgeBorders && gridBottomEdge) || (!drawGridEdgeBorders && !gridBottomEdge && !gridLeftEdge),
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

    private static QColor qColor(String hexColor, double opacity) {
        return QColor.fromRgba(hexColorStringToRgba(hexColor, opacity));
    }

    private static QColor shadowColor(Shadow shadow) {
        return qColor(shadow.hexColor(), shadow.opacity());
    }

    private static QtFontStyle buildQtFontStyle(FontStyle fs, QFont font,
                                                            QFontMetrics metrics,
                                                            double screenScale) {
        return new QtFontStyle(
                font, metrics,
                qColor(fs.hexColor(), fs.opacity()),
                qColor(fs.outlineHexColor(), fs.outlineOpacity()),
                (int) Math.round(fs.outlineThickness() * screenScale),
                shadowColor(fs.shadow()),
                fs.shadow().stackCount(),
                fs.shadow().blurRadius() * screenScale,
                fs.shadow().horizontalOffset() * screenScale,
                fs.shadow().verticalOffset() * screenScale
        );
    }

    /**
     * Returns true if the two shadows differ and at least one has non-zero opacity.
     * When both are zero-opacity neither renders, so no per-key processing is needed.
     * When one is zero and the other is not, per-key processing is needed to exclude
     * the zero-opacity keys from the other's shadow.
     */
    private static boolean shadowsNeedPerKeyProcessing(Shadow a, Shadow b) {
        return (a.opacity() != 0 || b.opacity() != 0) && !a.equals(b);
    }

    private static boolean fontShapeEquals(FontStyle a, FontStyle b) {
        return a.name().equals(b.name()) &&
               a.weight() == b.weight() &&
               Double.compare(a.size(), b.size()) == 0;
    }


    private static QtHintFontStyle buildQtHintFontStyle(HintFontStyle hintFontStyle,
                                                       HintFontStyle prefixHintFontStyle,
                                                       double screenScale,
                                                       boolean hasSelectedKeys) {
        FontStyle defaultFontStyle = hintFontStyle.defaultFontStyle();
        FontStyle selectedFontStyle = hintFontStyle.selectedFontStyle();
        FontStyle focusedFontStyle = hintFontStyle.focusedFontStyle();
        boolean perKeyFont = (hasSelectedKeys && !fontShapeEquals(defaultFontStyle, selectedFontStyle)) ||
                             !fontShapeEquals(defaultFontStyle, focusedFontStyle);
        if (prefixHintFontStyle != null) {
            perKeyFont = perKeyFont ||
                         !fontShapeEquals(defaultFontStyle, prefixHintFontStyle.defaultFontStyle()) ||
                         (hasSelectedKeys && !fontShapeEquals(defaultFontStyle, prefixHintFontStyle.selectedFontStyle())) ||
                         !fontShapeEquals(defaultFontStyle, prefixHintFontStyle.focusedFontStyle());
        }
        Shadow defaultShadow = defaultFontStyle.shadow();
        boolean perKeyShadow =
                (hasSelectedKeys && shadowsNeedPerKeyProcessing(defaultShadow, selectedFontStyle.shadow())) ||
                shadowsNeedPerKeyProcessing(defaultShadow, focusedFontStyle.shadow());
        if (prefixHintFontStyle != null) {
            perKeyShadow = perKeyShadow ||
                           shadowsNeedPerKeyProcessing(defaultShadow, prefixHintFontStyle.defaultFontStyle().shadow()) ||
                           (hasSelectedKeys && shadowsNeedPerKeyProcessing(defaultShadow, prefixHintFontStyle.selectedFontStyle().shadow())) ||
                           shadowsNeedPerKeyProcessing(defaultShadow, prefixHintFontStyle.focusedFontStyle().shadow());
        }
        QFont defaultFont = qFont(defaultFontStyle.name(), defaultFontStyle.size(), defaultFontStyle.weight());
        QFontMetrics defaultMetrics = correctedFontMetricsForScreenDpi(defaultFont, defaultFontStyle.size(), screenScale);
        QtFontStyle defaultQtFontStyle = buildQtFontStyle(defaultFontStyle, defaultFont, defaultMetrics, screenScale);
        QtFontStyle selectedQtFontStyle;
        QtFontStyle focusedQtFontStyle;
        if (perKeyFont) {
            QFont selectedFont = qFont(selectedFontStyle.name(), selectedFontStyle.size(), selectedFontStyle.weight());
            QFontMetrics selectedMetrics = correctedFontMetricsForScreenDpi(selectedFont, selectedFontStyle.size(), screenScale);
            selectedQtFontStyle = buildQtFontStyle(selectedFontStyle, selectedFont, selectedMetrics, screenScale);
            QFont focusedFont = qFont(focusedFontStyle.name(), focusedFontStyle.size(), focusedFontStyle.weight());
            QFontMetrics focusedMetrics = correctedFontMetricsForScreenDpi(focusedFont, focusedFontStyle.size(), screenScale);
            focusedQtFontStyle = buildQtFontStyle(focusedFontStyle, focusedFont, focusedMetrics, screenScale);
        }
        else {
            selectedQtFontStyle = buildQtFontStyle(selectedFontStyle, defaultFont, defaultMetrics, screenScale);
            focusedQtFontStyle = buildQtFontStyle(focusedFontStyle, defaultFont, defaultMetrics, screenScale);
        }
        QtFontStyle prefixDefaultQtFontStyle = null;
        QtFontStyle prefixSelectedQtFontStyle = null;
        QtFontStyle prefixFocusedQtFontStyle = null;
        if (prefixHintFontStyle != null) {
            FontStyle prefixDefaultFs = prefixHintFontStyle.defaultFontStyle();
            FontStyle prefixSelectedFs = prefixHintFontStyle.selectedFontStyle();
            FontStyle prefixFocusedFs = prefixHintFontStyle.focusedFontStyle();
            if (perKeyFont) {
                QFont prefixDefaultFont = qFont(prefixDefaultFs.name(), prefixDefaultFs.size(), prefixDefaultFs.weight());
                QFontMetrics prefixDefaultMetrics = correctedFontMetricsForScreenDpi(prefixDefaultFont, prefixDefaultFs.size(), screenScale);
                prefixDefaultQtFontStyle = buildQtFontStyle(prefixDefaultFs, prefixDefaultFont, prefixDefaultMetrics, screenScale);
                QFont prefixSelectedFont = qFont(prefixSelectedFs.name(), prefixSelectedFs.size(), prefixSelectedFs.weight());
                QFontMetrics prefixSelectedMetrics = correctedFontMetricsForScreenDpi(prefixSelectedFont, prefixSelectedFs.size(), screenScale);
                prefixSelectedQtFontStyle = buildQtFontStyle(prefixSelectedFs, prefixSelectedFont, prefixSelectedMetrics, screenScale);
                QFont prefixFocusedFont = qFont(prefixFocusedFs.name(), prefixFocusedFs.size(), prefixFocusedFs.weight());
                QFontMetrics prefixFocusedMetrics = correctedFontMetricsForScreenDpi(prefixFocusedFont, prefixFocusedFs.size(), screenScale);
                prefixFocusedQtFontStyle = buildQtFontStyle(prefixFocusedFs, prefixFocusedFont, prefixFocusedMetrics, screenScale);
            }
            else {
                prefixDefaultQtFontStyle = buildQtFontStyle(prefixDefaultFs, defaultFont, defaultMetrics, screenScale);
                prefixSelectedQtFontStyle = buildQtFontStyle(prefixSelectedFs, defaultFont, defaultMetrics, screenScale);
                prefixFocusedQtFontStyle = buildQtFontStyle(prefixFocusedFs, defaultFont, defaultMetrics, screenScale);
            }
        }
        return new QtHintFontStyle(defaultQtFontStyle, selectedQtFontStyle, focusedQtFontStyle,
                prefixDefaultQtFontStyle, prefixSelectedQtFontStyle, prefixFocusedQtFontStyle,
                perKeyFont, perKeyShadow, hintFontStyle.spacingPercent());
    }

    record QtFontStyle(QFont font, QFontMetrics metrics,
                             QColor color,
                             QColor outlineColor, int outlineThickness,
                             QColor shadowColor, int shadowStackCount,
                             double shadowBlurRadius,
                             double shadowHorizontalOffset, double shadowVerticalOffset) {
    }

    public record QtHintFontStyle(QtFontStyle defaultStyle,
                                        QtFontStyle selectedStyle,
                                        QtFontStyle focusedStyle,
                                        QtFontStyle prefixDefaultStyle,
                                        QtFontStyle prefixSelectedStyle,
                                        QtFontStyle prefixFocusedStyle,
                                        boolean perKeyFont,
                                        boolean perKeyShadow,
                                        double fontSpacingPercent) {
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
    private static void applyBoxShadow(HintPaintLayer boxLayer,
                                       HintPaintLayer boxShadowLayer,
                                       List<HintBox> hintBoxes,
                                       Shadow boxShadow,
                                       QColor boxColor,
                                       QColor boxBorderColor,
                                       int boxBorderThickness,
                                       int containerWidth,
                                       int containerHeight) {
        QColor shadowColor = shadowColor(boxShadow);
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
            ShadowImage shadow = renderShadowOnly(sourceImage, shadowColor,
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
    private static void applyLabelShadow(HintPaintLayer layer,
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
        if (!qtHintFontStyleHasTransparency(style, hasSelectedKeys) &&
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

    private static void preRenderLabelShadow(HintPaintLayer layer,
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
        ShadowImage shadow = renderShadowOnly(sourceImage, shadowStyle.shadowColor(),
                shadowStyle.shadowBlurRadius(), shadowStyle.shadowHorizontalOffset(),
                shadowStyle.shadowVerticalOffset(), containerWidth, containerHeight);
        QImage shadowImage = StackedShadowEffect.bakeStacking(shadow.image(), shadowStyle.shadowStackCount());
        QPixmap shadowPixmap = QPixmap.fromImage(shadowImage);
        layer.setShadowPixmap(shadowPixmap,
                shadow.x(), shadow.y());
        shadowImage.dispose();
    }

    private record ShadowImage(QImage image, int x, int y) {
    }

    /**
     * Applies a shadow effect to the source image, then subtracts the source
     * pixels so only the shadow remains. Disposes the source image.
     */
    private static ShadowImage renderShadowOnly(
            QImage sourceImage, QColor shadowColor, double blurRadius,
            double horizontalOffset, double verticalOffset,
            int containerWidth, int containerHeight) {
        QGraphicsScene scene = new QGraphicsScene();
        QPixmap sourcePixmap = QPixmap.fromImage(sourceImage);
        QGraphicsPixmapItem item = scene.addPixmap(sourcePixmap);
        StackedShadowEffect effect = new StackedShadowEffect();
        effect.setBlurRadius(blurRadius);
        effect.setOffset(horizontalOffset, verticalOffset);
        effect.setColor(shadowColor);
        effect.setStackCount(1);
        item.setGraphicsEffect(effect);
        QRectF bounds = scene.itemsBoundingRect();
        int boundsX = (int) Math.floor(bounds.x());
        int boundsY = (int) Math.floor(bounds.y());
        int boundsW = (int) Math.ceil(bounds.x() + bounds.width()) - boundsX;
        int boundsH = (int) Math.ceil(bounds.y() + bounds.height()) - boundsY;
        bounds.dispose();
        QRectF intBounds = new QRectF(boundsX, boundsY, boundsW, boundsH);
        QImage resultImage = new QImage(boundsW, boundsH,
                QImage.Format.Format_ARGB32_Premultiplied);
        QColor fillColor = new QColor(0, 0, 0, 0);
        resultImage.fill(fillColor);
        fillColor.dispose();
        QPainter resultPainter = new QPainter(resultImage);
        QRect resultRect = resultImage.rect();
        QRectF targetRect = new QRectF(resultRect);
        scene.render(resultPainter, targetRect, intBounds);
        resultRect.dispose();
        targetRect.dispose();
        intBounds.dispose();
        resultPainter.end();
        resultPainter.dispose();
        scene.dispose();
        sourcePixmap.dispose();
        ByteBuffer combinedBuf = resultImage.bits();
        ByteBuffer sourceBuf = sourceImage.bits();
        int resultBytesPerLine = boundsW * 4;
        int sourceBytesPerLine = containerWidth * 4;
        int totalBytes = resultBytesPerLine * boundsH;
        byte[] shadowBytes = new byte[totalBytes];
        combinedBuf.get(0, shadowBytes, 0, totalBytes);
        int srcOffX = -boundsX;
        int srcOffY = -boundsY;
        int overlapW = Math.min(containerWidth, boundsW - srcOffX);
        int overlapH = Math.min(containerHeight, boundsH - srcOffY);
        for (int py = 0; py < overlapH; py++) {
            int resultRowStart = (py + srcOffY) * resultBytesPerLine + srcOffX * 4;
            int sourceRowStart = py * sourceBytesPerLine;
            for (int i = 0; i < overlapW * 4; i++) {
                int c = shadowBytes[resultRowStart + i] & 0xFF;
                int s = sourceBuf.get(sourceRowStart + i) & 0xFF;
                shadowBytes[resultRowStart + i] = (byte) Math.max(0, c - s);
            }
        }
        resultImage.dispose();
        sourceImage.dispose();
        QImage shadowImage = new QImage(shadowBytes, boundsW, boundsH,
                QImage.Format.Format_ARGB32_Premultiplied);
        return new ShadowImage(shadowImage, boundsX, boundsY);
    }



    /**
     * Per-group shadow rendering: groups keys by their effective shadow
     * settings (state + prefix/non-prefix), renders each group separately,
     * bakes stacking, and composites into a single shadow pixmap.
     */
    private static void preRenderPerGroupShadow(
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
            ShadowImage shadow = renderShadowOnly(sourceImage, shadowColor,
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

    private static class HintPaintLayer extends QWidget {

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
        int alpha = opacity > 0 ? Math.max(1, (int) (opacity * 255) & 0xFF) : 0;
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
        boolean created = indicatorWindow == null;
        boolean sizeOrShadowOrPositionChanged = false;
        if (created) {
            createIndicatorWindow();
        }
        else {
            boolean sizeOrShadowChanged = oldIndicator == null ||
                    indicator.size() != oldIndicator.size() ||
                    indicator.edgeCount() != oldIndicator.edgeCount() ||
                    indicator.outerOutline().thickness() != oldIndicator.outerOutline().thickness() ||
                    indicator.innerOutline().thickness() != oldIndicator.innerOutline().thickness() ||
                    !indicator.shadow().equals(oldIndicator.shadow()) ||
                    indicator.opacity() != oldIndicator.opacity() ||
                    indicator.outerOutline().opacity() != oldIndicator.outerOutline().opacity() ||
                    indicator.innerOutline().opacity() != oldIndicator.innerOutline().opacity();
            boolean positionChanged = oldIndicator == null ||
                    indicator.position() != oldIndicator.position();
            if (sizeOrShadowChanged) {
                WinDef.POINT mousePosition = WindowsMouse.findMousePosition();
                Screen activeScreen = WindowsScreen.findActiveScreen(mousePosition);
                applyIndicatorShadowEffect(activeScreen.scale() * zoomPercent());
            }
            sizeOrShadowOrPositionChanged = sizeOrShadowChanged || positionChanged;
        }
        indicatorWindow.widget.cleared = false;
        indicatorWindow.widget.setEdgeCount(indicator.edgeCount());
        indicatorWindow.widget.setColor(indicator.opacity() > 0
                ? new QColor(indicator.hexColor()) : new QColor(0, 0, 0, 0));
        IndicatorOutline outer = indicator.outerOutline();
        IndicatorOutline inner = indicator.innerOutline();
        indicatorWindow.widget.setOutlines(
                outer.thickness(),
                outer.opacity() > 0 ? new QColor(outer.hexColor()) : new QColor(0, 0, 0, 0),
                outer.fillPercent(),
                outer.fillStartAngle(),
                outer.fillDirection(),
                inner.thickness(),
                inner.opacity() > 0 ? new QColor(inner.hexColor()) : new QColor(0, 0, 0, 0),
                inner.fillPercent(),
                inner.fillStartAngle(),
                inner.fillDirection());
        if (indicatorWindow.widget.customGraphicsEffect != null) {
            setIndicatorEffectColors(indicatorWindow.widget.customGraphicsEffect);
        }
        if (created || sizeOrShadowOrPositionChanged) {
            moveAndResizeIndicatorWindow();
        }
        showingIndicator = true;
        if (indicator.labelEnabled() && indicator.labelText() != null && indicator.labelFontStyle() != null) {
            FontStyle labelFontStyle = indicator.labelFontStyle();
            QFont labelFont = qFont(labelFontStyle.name(), labelFontStyle.size(), labelFontStyle.weight());
            QColor labelColor = qColor(labelFontStyle.hexColor(), labelFontStyle.opacity());
            QColor labelOutlineColor = qColor(labelFontStyle.outlineHexColor(), labelFontStyle.outlineOpacity());
            indicatorWindow.labelWidget.setLabel(indicator.labelText(), labelFont, labelFontStyle.size(),
                    labelColor,
                    (int) Math.round(labelFontStyle.outlineThickness()), labelOutlineColor,
                    indicator.edgeCount());
            Shadow labelShadow = labelFontStyle.shadow();
            QColor labelShadowColor = qColor(labelShadow.hexColor(), labelShadow.opacity());
            if (labelShadowColor.alpha() != 0) {
                WinDef.POINT mousePosition = WindowsMouse.findMousePosition();
                Screen activeScreen = WindowsScreen.findActiveScreen(mousePosition);
                double labelShadowScale = activeScreen.scale() * zoomPercent();
                QGraphicsDropShadowEffect effect = new QGraphicsDropShadowEffect();
                effect.setBlurRadius(labelShadow.blurRadius() * labelShadowScale);
                effect.setOffset(labelShadow.horizontalOffset() * labelShadowScale,
                        labelShadow.verticalOffset() * labelShadowScale);
                effect.setColor(labelShadowColor);
                indicatorWindow.labelWidget.setGraphicsEffect(effect);
            }
            else {
                indicatorWindow.labelWidget.setGraphicsEffect(null);
            }
            labelShadowColor.dispose();
            indicatorWindow.labelWidget.show();
        }
        else {
            indicatorWindow.labelWidget.setLabel(null, null, 0, null, 0, null, 0);
            indicatorWindow.labelWidget.setGraphicsEffect(null);
            indicatorWindow.labelWidget.hide();
        }
        indicatorWindow.window.show();
        indicatorWindow.widget.repaint();
    }

    private static void createScreenshotWindow() {
        screenshotWidget = new ScreenshotWidget();
        screenshotHwnd = new WinDef.HWND(new Pointer(screenshotWidget.winId()));
        long currentStyle =
                User32.INSTANCE.GetWindowLongPtr(screenshotHwnd, WinUser.GWL_EXSTYLE)
                               .longValue();
        long newStyle = currentStyle | ExtendedUser32.WS_EX_NOACTIVATE |
                        ExtendedUser32.WS_EX_TOOLWINDOW |
                        ExtendedUser32.WS_EX_LAYERED |
                        ExtendedUser32.WS_EX_TRANSPARENT;
        User32.INSTANCE.SetWindowLongPtr(screenshotHwnd, WinUser.GWL_EXSTYLE,
                new Pointer(newStyle));
        // Make topmost so it's in the same z-band as the magnifier.
        User32.INSTANCE.SetWindowPos(screenshotHwnd, ExtendedUser32.HWND_TOPMOST,
                0, 0, 0, 0,
                WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE | WinUser.SWP_NOACTIVATE);
    }

    public static void startScreenshotZoomAnimation(Rectangle screenRect,
                                                      Zoom beginZoom) {
        boolean interruptingMidAnimation = screenshotAnimating;
        if (screenshotAnimating || screenshotPendingHide) {
            screenshotAnimating = false;
            screenshotPendingHide = false;
        }
        if (screenshotWidget == null)
            createScreenshotWindow();
        screenshotAnimating = true;
        screenshotWidget.move(screenRect.x(), screenRect.y());
        screenshotWidget.resize(screenRect.width(), screenRect.height());
        if (interruptingMidAnimation && screenshotPixmap != null) {
            // Reuse existing pixmap only during mid-animation interruption:
            // the screenshot widget is already visible with the correct
            // 1x desktop content.
            return;
        }
        // Exclude all our windows from capture via WDA so grabWindow
        // sees only the desktop content underneath.
        boolean magnifierVisible = zoomWindow != null && currentZoom != null;
        if (magnifierVisible) {
            ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                    zoomWindow.hostHwnd(),
                    ExtendedUser32.WDA_EXCLUDEFROMCAPTURE);
            if (standByZoomWindow != null)
                ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                        standByZoomWindow.hostHwnd(),
                        ExtendedUser32.WDA_EXCLUDEFROMCAPTURE);
        }
        if (showingIndicator)
            ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                    indicatorWindow.hwnd(),
                    ExtendedUser32.WDA_EXCLUDEFROMCAPTURE);
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values())
            ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                    hintMeshWindow.hwnd(),
                    ExtendedUser32.WDA_EXCLUDEFROMCAPTURE);
        if (screenshotHwnd != null)
            ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                    screenshotHwnd, ExtendedUser32.WDA_EXCLUDEFROMCAPTURE);
        QPixmap capture = QApplication.primaryScreen().grabWindow(
                0, screenRect.x(), screenRect.y(),
                screenRect.width(), screenRect.height());
        // Restore WDA on all windows.
        if (magnifierVisible) {
            ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                    zoomWindow.hostHwnd(), ExtendedUser32.WDA_NONE);
            if (standByZoomWindow != null)
                ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                        standByZoomWindow.hostHwnd(), ExtendedUser32.WDA_NONE);
        }
        if (showingIndicator)
            ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                    indicatorWindow.hwnd(), ExtendedUser32.WDA_NONE);
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values())
            ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                    hintMeshWindow.hwnd(), ExtendedUser32.WDA_NONE);
        if (screenshotHwnd != null)
            ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                    screenshotHwnd, ExtendedUser32.WDA_NONE);
        drawCursorOnto(capture, screenRect);
        screenshotPixmap = capture;
        screenshotWidget.setScreenshot(capture, screenRect);
        currentZoom = beginZoom;
        screenshotWidget.setZoom(beginZoom);
        if (!screenshotWidget.isVisible())
            screenshotWidget.show();
        screenshotWidget.repaint();
        setTopmost();
    }

    private static void drawCursorOnto(QPixmap pixmap, Rectangle screenRect) {
        ExtendedUser32.CURSORINFO cursorInfo = new ExtendedUser32.CURSORINFO();
        if (!ExtendedUser32.INSTANCE.GetCursorInfo(cursorInfo) ||
            cursorInfo.hCursor == null)
            return;
        WinGDI.ICONINFO iconInfo = new WinGDI.ICONINFO();
        if (!User32.INSTANCE.GetIconInfo(
                new WinDef.HICON(cursorInfo.hCursor), iconInfo))
            return;
        try {
            WinGDI.BITMAP bmpInfo = new WinGDI.BITMAP();
            WinDef.HBITMAP sizeBmp = iconInfo.hbmColor != null
                    ? iconInfo.hbmColor : iconInfo.hbmMask;
            GDI32.INSTANCE.GetObject(sizeBmp, bmpInfo.size(),
                    bmpInfo.getPointer());
            bmpInfo.read();
            int width = bmpInfo.bmWidth.intValue();
            int height = bmpInfo.bmHeight.intValue();
            if (iconInfo.hbmColor == null)
                height /= 2; // Monochrome: double-height (AND + XOR).
            if (width <= 0 || height <= 0)
                return;
            int drawX = cursorInfo.ptScreenPos.x - iconInfo.xHotspot
                    - screenRect.x();
            int drawY = cursorInfo.ptScreenPos.y - iconInfo.yHotspot
                    - screenRect.y();
            if (iconInfo.hbmColor != null) {
                // Read color bitmap as 32-bit BGRA.
                byte[] colorData = readBitmap32(iconInfo.hbmColor, width, height);
                if (colorData == null)
                    return;
                // Check if this is a standard alpha cursor or an XOR cursor.
                boolean hasAlpha = false;
                for (int i = 3; i < colorData.length; i += 4) {
                    if (colorData[i] != 0) {
                        hasAlpha = true;
                        break;
                    }
                }
                if (hasAlpha) {
                    // Standard alpha cursor — draw directly.
                    QImage cursorImage = new QImage(colorData, width, height,
                            QImage.Format.Format_ARGB32);
                    QPainter painter = new QPainter(pixmap);
                    painter.drawImage(drawX, drawY, cursorImage);
                    painter.end();
                    painter.dispose();
                    cursorImage.dispose();
                }
                else {
                    // XOR cursor (e.g. I-beam): alpha=0, non-black RGB is XOR mask.
                    // Read AND mask to determine opaque vs XOR pixels.
                    byte[] maskData = iconInfo.hbmMask != null
                            ? readBitmap32(iconInfo.hbmMask, width, height)
                            : null;
                    // Read background from pixmap for XOR blending.
                    QImage bgImage = pixmap.toImage();
                    byte[] resultData = new byte[width * height * 4];
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int idx = (y * width + x) * 4;
                            int cB = colorData[idx] & 0xFF;
                            int cG = colorData[idx + 1] & 0xFF;
                            int cR = colorData[idx + 2] & 0xFF;
                            // AND mask: 0x000000=opaque, 0xFFFFFF=transparent/XOR.
                            boolean andTransparent = true;
                            if (maskData != null) {
                                andTransparent =
                                        (maskData[idx] & 0xFF) != 0 ||
                                        (maskData[idx + 1] & 0xFF) != 0 ||
                                        (maskData[idx + 2] & 0xFF) != 0;
                            }
                            if (!andTransparent) {
                                // AND=0: opaque pixel, use color directly.
                                resultData[idx] = (byte) cB;
                                resultData[idx + 1] = (byte) cG;
                                resultData[idx + 2] = (byte) cR;
                                resultData[idx + 3] = (byte) 0xFF;
                            }
                            else if (cB != 0 || cG != 0 || cR != 0) {
                                // AND=1, color!=0: XOR with background.
                                int px = drawX + x;
                                int py = drawY + y;
                                if (px >= 0 && px < bgImage.width() &&
                                    py >= 0 && py < bgImage.height()) {
                                    int bgPixel = bgImage.pixel(px, py);
                                    int bgR = (bgPixel >> 16) & 0xFF;
                                    int bgG = (bgPixel >> 8) & 0xFF;
                                    int bgB = bgPixel & 0xFF;
                                    resultData[idx] = (byte) (bgB ^ cB);
                                    resultData[idx + 1] = (byte) (bgG ^ cG);
                                    resultData[idx + 2] = (byte) (bgR ^ cR);
                                    resultData[idx + 3] = (byte) 0xFF;
                                }
                            }
                            // else AND=1, color=0: transparent, leave as zero.
                        }
                    }
                    QImage resultImage = new QImage(resultData, width, height,
                            QImage.Format.Format_ARGB32);
                    QPainter painter = new QPainter(pixmap);
                    painter.drawImage(drawX, drawY, resultImage);
                    painter.end();
                    painter.dispose();
                    resultImage.dispose();
                    bgImage.dispose();
                }
            }
            // else: mask-only (monochrome) cursor — skip for now.
        }
        finally {
            if (iconInfo.hbmColor != null)
                GDI32.INSTANCE.DeleteObject(iconInfo.hbmColor);
            if (iconInfo.hbmMask != null)
                GDI32.INSTANCE.DeleteObject(iconInfo.hbmMask);
        }
    }

    private static byte[] readBitmap32(WinDef.HBITMAP bitmap, int width,
                                       int height) {
        WinGDI.BITMAPINFO bi = new WinGDI.BITMAPINFO();
        bi.bmiHeader.biWidth = width;
        bi.bmiHeader.biHeight = -height; // Top-down.
        bi.bmiHeader.biPlanes = 1;
        bi.bmiHeader.biBitCount = 32;
        Memory pixels = new Memory((long) width * height * 4);
        WinDef.HDC hdc = GDI32.INSTANCE.CreateCompatibleDC(null);
        int result = GDI32.INSTANCE.GetDIBits(hdc, bitmap, 0, height,
                pixels, bi, WinGDI.DIB_RGB_COLORS);
        GDI32.INSTANCE.DeleteDC(hdc);
        if (result == 0)
            return null;
        return pixels.getByteArray(0, width * height * 4);
    }

    public static void updateScreenshotZoom(Zoom zoom) {
        if (!screenshotAnimating)
            return;
        currentZoom = zoom;
        screenshotWidget.setZoom(zoom);
        screenshotWidget.repaint();
        if (indicatorWindow != null)
            moveAndResizeIndicatorWindow();
        setTopmost();
    }

    public static void endScreenshotZoomAnimation(Zoom finalZoom) {
        if (!screenshotAnimating)
            return;
        screenshotAnimating = false;
        // Reset so setZoom(null) doesn't early-return with stale currentZoom.
        currentZoom = null;
        if (finalZoom != null) {
            // Defer screenshot hide by one frame so magnifier renders first.
            screenshotPendingHide = true;
            setZoom(finalZoom);
        }
        else {
            setZoom(null);
            // Don't hide() the widget: showing a hidden layered window
            // briefly exposes its stale surface. Instead, clear its content
            // so it becomes transparent (WA_TranslucentBackground).
            screenshotWidget.setZoom(null);
            screenshotWidget.repaint();
            if (screenshotPixmap != null) {
                screenshotWidget.setScreenshot(null, null);
                screenshotPixmap = null;
            }
        }
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
            if (zoom == null)
                return;
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
            moveAndResizeIndicatorWindow();
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
        if (screenshotHwnd != null)
            hwnds.add(screenshotHwnd);
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
        // Paint the surface fully transparent before hiding.
        indicatorWindow.widget.cleared = true;
        indicatorWindow.widget.repaint();
        indicatorWindow.window.hide();
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

    /**
     * The reason we don't call setHintMesh with the match hint is because
     * that does not keep the prefix box borders of the previous hint mesh.
     */
    public static void animateHintMatch(Hint hint) {
        if (!showingHintMesh) // Invisible hint mesh.
            return;
        Map<Screen, List<Hint>> hintsByScreen = hintsByScreen(List.of(hint));
        Screen screen = hintsByScreen.keySet().iterator().next();
        HintMeshWindow hintMeshWindow = hintMeshWindows.get(screen);
        HintMesh lastHintMeshKey = hintMeshWindow.lastHintMeshKeyReference.get();
        HintMeshStyle style =
                lastHintMeshKey.styleByFilter().get(ViewportFilter.of(screen));
        if (!style.transitionAnimationEnabled())
            return;
        boolean isHintGrid = lastHintMeshKey.hints().getFirst().cellWidth() != -1 &&
                             lastHintMeshKey.hints().size() > 1;
        if (isHintGrid)
            hintMeshEndAnimation = true;
        else {
            // No animation for position history hints.
            // hideHintMesh() will be called by the switch mode command.
            return;
        }
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
        if (hintMesh.hints().isEmpty()) {
            currentHintMesh = hintMesh;
            hintMeshEndAnimation = false;
            createOrUpdateHintMeshWindows(currentHintMesh, zoom);
            showingHintMesh = true;
            return;
        }
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
        // Cancel any pending build/cache runnables that reference containers
        // about to be hidden. Otherwise container.grab() in the next update()
        // would paint a destroyed widget, causing a native _purecall crash.
        setUncachedHintMeshWindowRunnable = null;
        cacheQtHintWindowIntoPixmapRunnable = null;
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values()) {
            // Stop running animations and clear callbacks before hiding children.
            // Otherwise animation callbacks (HintContainerAnimationChanged) can fire
            // container.setMask() on a widget whose C++ object has been destroyed.
            for (QVariantAnimation animation : hintMeshWindow.animations) {
                animation.stop();
                animation.dispose();
            }
            hintMeshWindow.animations.clear();
            hintMeshWindow.animationCallbacks.clear();
            hintMeshWindow.window.setBackground(null, null);
            hintMeshWindow.window.hideChildren();
            hintMeshWindow.window.repaint();
        }
    }

    private static void requestWindowRepaint(WinDef.HWND hwnd) {
        User32.INSTANCE.InvalidateRect(hwnd, null, true);
        User32.INSTANCE.UpdateWindow(hwnd);
    }

    static void mouseMoved(WinDef.POINT mousePosition) {
        if (indicatorWindow == null)
             return;
        // During zoom, currentZoom still has the previous frame's zoom center
        // (it will be updated right after by ZoomManager in WindowsPlatform.sleep).
        // Positioning here would use that stale center, causing a brief
        // mispositioning until setZoom() corrects it.
        if (currentZoom != null)
            return;
        moveAndResizeIndicatorWindow(mousePosition);
    }

}
package mousemaster;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
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

    private static final int indicatorEdgeThreshold = 100; // in pixels
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
    /**
     * Building the hint window is expensive and when it is done from the keyboard hook,
     * Windows will cancel the hook and the key press will go through to the other apps.
     * Windows won't wait for the keyboard hook to return if it's taking too long.
     */
    private static Runnable setUncachedHintMeshWindowRunnable;
    private static Runnable cacheQtHintWindowIntoPixmapRunnable;

    public static void update(double delta) {
        if (setUncachedHintMeshWindowRunnable != null) {
            setUncachedHintMeshWindowRunnable.run();
            setUncachedHintMeshWindowRunnable = null;
        }
        if (cacheQtHintWindowIntoPixmapRunnable != null) {
            cacheQtHintWindowIntoPixmapRunnable.run();
            cacheQtHintWindowIntoPixmapRunnable = null;
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
        private double innerOutlineThickness;
        private QColor innerOutlineColor;
        private double innerOutlineFillPercent;
        private double outlineScale;
        private IndicatorShadowEffect customGraphicsEffect;

        IndicatorWidget(QWidget parent) {
            super(parent);
        }

        void setOutlineScale(double outlineScale) {
            this.outlineScale = outlineScale;
        }

        void setColor(QColor color) {
            this.color = color;
            update();
        }

        void setEdgeCount(int edgeCount) {
            this.edgeCount = edgeCount;
            update();
        }

        void setOutlines(double outerOutlineThickness, QColor outerOutlineColor,
                         double outerOutlineFillPercent,
                         double innerOutlineThickness, QColor innerOutlineColor,
                         double innerOutlineFillPercent) {
            this.outerOutlineThickness = outerOutlineThickness;
            this.outerOutlineColor = outerOutlineColor;
            this.outerOutlineFillPercent = outerOutlineFillPercent;
            this.innerOutlineThickness = innerOutlineThickness;
            this.innerOutlineColor = innerOutlineColor;
            this.innerOutlineFillPercent = innerOutlineFillPercent;
            update();
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
         * The visible portion starts at the bottom center and extends clockwise
         * (up the right side first), like a gauge being filled.
         */
        private static QPainterPath partialPolygonPath(double centerX, double centerY,
                                                       double radius, int edgeCount,
                                                       double fillPercent) {
            double startAngle = polygonStartAngle(edgeCount);
            double[] vx = new double[edgeCount];
            double[] vy = new double[edgeCount];
            for (int i = 0; i < edgeCount; i++) {
                double angle = startAngle + 2.0 * Math.PI * i / edgeCount;
                vx[i] = centerX + radius * Math.cos(angle);
                vy[i] = centerY + radius * Math.sin(angle);
            }
            double edgeLength = Math.hypot(vx[1] - vx[0], vy[1] - vy[0]);
            double totalLength = edgeCount * edgeLength;
            double fillLength = fillPercent * totalLength;
            // Bottom center = midpoint of the bottom edge.
            int bottomEdgeIndex = (edgeCount - 1) / 2;
            double bottomPos = (bottomEdgeIndex + 0.5) * edgeLength;
            // The fill goes clockwise from bottom (= backwards along the path).
            // In forward path terms, the visible segment is from startPos to bottomPos.
            double startPos = bottomPos - fillLength;
            if (startPos < 0) startPos += totalLength;
            // Find starting edge and fractional position within it.
            int startEdge = (int) (startPos / edgeLength);
            double startFrac = (startPos - startEdge * edgeLength) / edgeLength;
            int v0 = startEdge;
            int v1 = (startEdge + 1) % edgeCount;
            double sx = vx[v0] + startFrac * (vx[v1] - vx[v0]);
            double sy = vy[v0] + startFrac * (vy[v1] - vy[v0]);
            QPainterPath path = new QPainterPath();
            path.moveTo(sx, sy);
            double remaining = fillLength;
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
                    double ex = vx[curV] + frac * (vx[nextV] - vx[curV]);
                    double ey = vy[curV] + frac * (vy[nextV] - vy[curV]);
                    path.lineTo(ex, ey);
                    remaining = 0;
                }
            }
            return path;
        }

        private void clearOutline(QPainter painter, double centerX, double centerY,
                                  double fillRadius, double thickness, double fillPercent) {
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Clear);
            drawOutline(painter, centerX, centerY, fillRadius,
                    thickness, new QColor(0, 0, 0), fillPercent, 1.0);
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
        }

        private void drawOutline(QPainter painter, double centerX, double centerY,
                                 double fillRadius, double thickness, QColor color,
                                 double fillPercent, double inwardOverlap) {
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
            }
            else {
                pen.setCapStyle(Qt.PenCapStyle.FlatCap);
                painter.setPen(pen);
                QPainterPath outlinePath = partialPolygonPath(
                        centerX, centerY, outlineRadius, edgeCount, fillPercent);
                painter.drawPath(outlinePath);
            }
        }

        void drawContent(QPainter painter, QColor fillColor,
                         QColor outerOutlineColor, QColor innerOutlineColor) {
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
            // If any part is transparent, clear the entire indicator area first
            // so the shadow (composited by the effect) doesn't show through.
            if (indicatorHasTransparency()) {
                // Use radialMiterPadding (not miterPadding) so the clearing
                // polygon reaches every miter tip.
                double maxScaled = Math.max(scaledOuter, scaledInner);
                double radialPad = radialMiterPadding(maxScaled, edgeCount);
                QPainterPath outerBoundary = polygonPath(centerX, centerY,
                        fillRadius + radialPad, edgeCount);
                painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Clear);
                painter.setPen(Qt.PenStyle.NoPen);
                painter.setBrush(new QBrush(new QColor(0, 0, 0)));
                painter.drawPath(outerBoundary);
                painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
            }
            // Draw outer outline first (miter tips extend beyond fill polygon).
            drawOutline(painter, centerX, centerY, fillRadius,
                    correctedOuter, outerOutlineColor, outerOutlineFillPercent, 1.0);
            // Draw fill on top (covers inward overlap of outer outline).
            if (currentIndicator.opacity() < 1.0) {
                painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Clear);
                painter.setPen(Qt.PenStyle.NoPen);
                painter.setBrush(new QBrush(new QColor(0, 0, 0)));
                painter.drawPath(fillPath);
                painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
            }
            painter.setPen(Qt.PenStyle.NoPen);
            painter.setBrush(new QBrush(fillColor));
            painter.drawPath(fillPath);
            // Draw inner outline on top of fill.
            // Clear its area first if transparent, so outer outline doesn't show through.
            if (currentIndicator.innerOutline().opacity() < 1.0) {
                clearOutline(painter, centerX, centerY, fillRadius,
                        correctedInner, innerOutlineFillPercent);
            }
            drawOutline(painter, centerX, centerY, fillRadius,
                    correctedInner, innerOutlineColor, innerOutlineFillPercent, 1.0);
        }

        @Override
        protected void paintEvent(QPaintEvent event) {
            QPainter painter = new QPainter(this);
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            drawContent(painter, color, outerOutlineColor, innerOutlineColor);
            painter.end();
        }
    }

    public static class StackedShadowEffect extends QGraphicsDropShadowEffect {

        private double stackedOpacity = 1.0;

        void setStackedOpacity(double stackedOpacity) {
            this.stackedOpacity = stackedOpacity;
        }

        @Override
        protected void draw(QPainter painter) {
            // For opacity <= 1.0, the alpha is baked into the color.
            // For opacity > 1.0, stacked shadows: e.g. 1.5 = one full
            // shadow + one 0.5 opacity shadow. painter.setOpacity() is
            // used for the fractional layer (avoids calling setColor()
            // inside draw() which would trigger a repaint loop).
            if (stackedOpacity <= 1.0) {
                super.draw(painter);
            }
            else {
                int fullLayers = (int) stackedOpacity;
                double remainder = stackedOpacity - fullLayers;
                for (int i = 0; i < fullLayers; i++) {
                    super.draw(painter);
                }
                if (remainder > 1e-6) {
                    double beforeOpacity = painter.opacity();
                    painter.setOpacity(beforeOpacity * remainder);
                    super.draw(painter);
                    painter.setOpacity(beforeOpacity);
                }
            }
            redrawSourceOverShadow(painter);
        }

        protected void redrawSourceOverShadow(QPainter painter) {
            // No-op by default. Subclasses override to clear and redraw
            // source content, preventing shadow from showing through
            // transparent parts.
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
            this.fillColor = fillColor;
            this.outerOutlineColor = outerOutlineColor;
            this.innerOutlineColor = innerOutlineColor;
        }

        @Override
        protected void redrawSourceOverShadow(QPainter painter) {
            if (!indicatorHasTransparency())
                return;
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            widget.drawContent(painter, fillColor, outerOutlineColor, innerOutlineColor);
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
            }
            // Fill: draw text on top of outline.
            painter.setPen(labelColor);
            painter.drawText(textX, textY, labelText);
            painter.end();
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

    private static int indicatorSize(double screenScale) {
        return scaledPixels(currentIndicator.size(), screenScale);
    }

    private static int bestIndicatorX(WinDef.POINT mousePosition, Screen activeScreen, int indicatorSize) {
        MouseSize mouseSize = WindowsMouse.mouseSize();
        return (int) Math.round(zoomedX(bestIndicatorX(mousePosition.x, mouseSize.width(),
                activeScreen.rectangle(), indicatorSize)));
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

    private static int bestIndicatorY(WinDef.POINT mousePosition, Screen activeScreen, int indicatorSize) {
        MouseSize mouseSize = WindowsMouse.mouseSize();
        return (int) Math.round(zoomedY(bestIndicatorY(mousePosition.y, mouseSize.height(),
                activeScreen.rectangle(), indicatorSize)));
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
        indicatorWindow.window.move(bestIndicatorX(mousePosition, activeScreen, visualSize) - shadowPadding,
                bestIndicatorY(mousePosition, activeScreen, visualSize) - shadowPadding);
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
        if (qtFontStyle.outlineThickness() != 0 && qtFontStyle.outlineColor().alpha() < 255)
            return true;
        if (qtFontStyle.color().alpha() < 255)
            return true;
        if (qtFontStyle.prefixColor() != null && qtFontStyle.prefixColor().alpha() < 255)
            return true;
        return false;
    }

    private static boolean qtHintFontStyleHasTransparency(QtHintFontStyle style) {
        return qtFontStyleHasTransparency(style.defaultStyle()) ||
               qtFontStyleHasTransparency(style.selectedStyle()) ||
               qtFontStyleHasTransparency(style.focusedStyle());
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
            double stackedOpacity = shadow.opacity();
            // For opacity <= 1.0, bake alpha into the color.
            // For opacity > 1.0, use full alpha; stacking handled in draw().
            int alpha = (int) Math.round(Math.min(stackedOpacity, 1.0) * 255);
            effect.setColor(new QColor(baseColor.red(), baseColor.green(),
                    baseColor.blue(), alpha));
            effect.setStackedOpacity(stackedOpacity);
            setIndicatorEffectColors(effect);
            indicatorWindow.widget.customGraphicsEffect = effect;
            indicatorWindow.widget.setGraphicsEffect(effect);
        }
        else if (indicatorHasTransparency()) {
            IndicatorShadowEffect effect = new IndicatorShadowEffect(indicatorWindow.widget);
            effect.setBlurRadius(0);
            setIndicatorEffectColors(effect);
            indicatorWindow.widget.customGraphicsEffect = effect;
            indicatorWindow.widget.setGraphicsEffect(effect);
        }
        else {
            indicatorWindow.widget.customGraphicsEffect = null;
            indicatorWindow.widget.setGraphicsEffect(null);
        }
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
        moveAndResizeIndicatorWindow();
        WinDef.POINT mousePosition = WindowsMouse.findMousePosition();
        Screen activeScreen = WindowsScreen.findActiveScreen(mousePosition);
        applyIndicatorShadowEffect(activeScreen.scale() * zoomPercent());
        window.show();
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
        @Override
        protected void paintEvent(QPaintEvent event) {
            QPainter painter = new QPainter(this);
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Source);
            // Clear what's behind (when we're drawing the old container behind).
            painter.fillRect(rect(), qColor("#000000", 0));
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
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
            int mergedContainerX = Math.min(veryOldContainer.geometry().x(), oldContainer.geometry().x());
            int mergedContainerY = Math.min(veryOldContainer.geometry().y(), oldContainer.geometry().y());
            mergedContainer.setGeometry(
                    mergedContainerX,
                    mergedContainerY,
                    Math.max(veryOldContainer.geometry().right(), oldContainer.geometry().right()) - mergedContainerX,
                    Math.max(veryOldContainer.geometry().bottom(), oldContainer.geometry().bottom()) - mergedContainerY
            );
            veryOldContainer.move(veryOldContainer.x() - mergedContainerX, veryOldContainer.y() - mergedContainerY);
            oldContainer.move(oldContainer.x() - mergedContainerX, oldContainer.y() - mergedContainerY);
            veryOldContainer.setParent(mergedContainer);
            oldContainer.setParent(mergedContainer);
            mergedContainer.show();
        }
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
            QWidget container = new ClearBackgroundQLabel();
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
            if (true || !isHintGrid // They are not cached anyway.
                || !hintMesh.selectedKeySequence().isEmpty() // To avoid an empty frame.
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
        // TODO Should use .geometry() instead of .rect() which is relative to the widget
        //  itself, where geometry() is relative to the parent.
        if (oldContainer != null) {
            boolean containersEqual = oldContainer.rect().equals(newContainer.rect());
            if (animateTransition && paddedRect(oldContainer.rect()).contains(newContainer.rect())) {
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
            else if (animateTransition && paddedRect(newContainer.rect()).contains(oldContainer.rect())) {
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
        boolean atLeastOneHintVisible = false;
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = 0;
        int bottom = 0;
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
        double maxHintCenterX = 0;
        double maxHintCenterY = 0;
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
        QtHintFontStyle labelFontStyle = buildQtHintFontStyle(style.fontStyle(), style.prefixFontStyle(), screenScale);
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
            double cellWidth = hint.cellWidth() != -1 ?
                    Math.max(totalXAdvance, hint.cellWidth()) :
                    totalXAdvance;
            int lineHeight = labelFontStyle.defaultStyle().metrics().height();
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
            int boxWidth = Math.max(hintLabel.tightHintBoxWidth, (int) (fullBoxWidth * style.boxWidthPercent()));
            int boxHeight = Math.max(hintLabel.tightHintBoxHeight, (int) (fullBoxHeight * style.boxHeightPercent()));
            hintLabel.left = boxWidth == hintLabel.tightHintBoxWidth ? hintLabel.tightHintBoxLeft : (fullBoxWidth - boxWidth) / 2;
            hintLabel.top = boxHeight == hintLabel.tightHintBoxHeight ? hintLabel.tightHintBoxTop : (fullBoxHeight - boxHeight) / 2;
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
            int boxHorizontalPadding = (int) Math.round(style.boxHorizontalPadding());
            int boxVerticalPadding = (int) Math.round(style.boxVerticalPadding());
            minHintLeft = Math.min(minHintLeft, x - boxHorizontalPadding);
            minHintTop = Math.min(minHintTop, y - boxVerticalPadding);
            maxHintRight = Math.max(maxHintRight, x + boxWidth + boxHorizontalPadding);
            maxHintBottom = Math.max(maxHintBottom, y + boxHeight + boxVerticalPadding);
            hintBox.setGeometry(x - hintMeshWindow.window.x() - boxHorizontalPadding,
                    y - hintMeshWindow.window.y() - boxVerticalPadding,
                    boxWidth + 2 * boxHorizontalPadding,
                    boxHeight + 2 * boxVerticalPadding);
            hintLabel.left -= boxHorizontalPadding;
            hintLabel.top -= boxVerticalPadding;
            hintLabel.setFixedSize(boxWidth + 2 * boxHorizontalPadding,
                    boxHeight + 2 * boxVerticalPadding);
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
        }
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
        if (style.prefixInBackground() && style.prefixFontStyle().defaultFontStyle().opacity() != 0) {
            prefixQtHintFontStyle = buildQtHintFontStyle(style.prefixFontStyle(), null, screenScale);
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
        // Layer 1: Hint boxes (with subgrid children).
        HintPaintLayer boxLayer = new HintPaintLayer(container, hintBoxes, List.of());
        boxLayer.setGeometry(0, 0, containerWidth, containerHeight);
        // Layer 2: Prefix boxes.
        HintPaintLayer prefixBoxLayer = new HintPaintLayer(container, prefixBoxes, List.of());
        prefixBoxLayer.setGeometry(0, 0, containerWidth, containerHeight);
        // Layer 3: Prefix labels.
        HintPaintLayer prefixLabelLayer =
                new HintPaintLayer(container, List.of(), prefixLabels);
        prefixLabelLayer.setGeometry(0, 0, containerWidth, containerHeight);
        if (prefixQtHintFontStyle != null) {
            applyLabelShadow(prefixLabelLayer, prefixLabels,
                    prefixQtHintFontStyle, containerWidth, containerHeight);
        }
        // Layer 4: Hint labels.
        HintPaintLayer hintLabelLayer =
                new HintPaintLayer(container, List.of(), hintLabels);
        hintLabelLayer.setGeometry(0, 0, containerWidth, containerHeight);
        applyLabelShadow(hintLabelLayer, hintLabels,
                labelFontStyle, containerWidth, containerHeight);
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
            new QFontMetrics(font).horizontalAdvance("x");
            if (!fontShapeEquals(hintFontStyle.defaultFontStyle(), hintFontStyle.selectedFontStyle())) {
                QFont selectedFont = qFont(hintFontStyle.selectedFontStyle().name(), hintFontStyle.selectedFontStyle().size(), hintFontStyle.selectedFontStyle().weight());
                new QFontMetrics(selectedFont).horizontalAdvance("x");
            }
            if (!fontShapeEquals(hintFontStyle.defaultFontStyle(), hintFontStyle.focusedFontStyle())) {
                QFont focusedFont = qFont(hintFontStyle.focusedFontStyle().name(), hintFontStyle.focusedFontStyle().size(), hintFontStyle.focusedFontStyle().weight());
                new QFontMetrics(focusedFont).horizontalAdvance("x");
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
        return new QFontMetrics(metricsFont);
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
        // pixmap.save("screenshot.png", "PNG");
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
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Source);
            if (borderRadius > 0) {
                // Draw background and border as a single rounded rect so
                // the background does not bleed outside the border at corners.
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
                if (borderThickness != 0) {
                    painter.setBrush(color.alpha() != 0 ? new QBrush(color) : new QBrush(Qt.BrushStyle.NoBrush));
                    QPen pen = createPen(borderColor, borderThickness);
                    painter.setPen(pen);
                    int offset = borderThickness / 2;
                    painter.drawRoundedRect(offset, offset,
                            width - borderThickness, height - borderThickness,
                            borderRadius, borderRadius);
                    pen.dispose();
                } else if (color.alpha() != 0) {
                    painter.setBrush(new QBrush(color));
                    painter.setPen(Qt.PenStyle.NoPen);
                    painter.drawRoundedRect(0, 0, width, height, borderRadius, borderRadius);
                }
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, false);
            }
            else {
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, false);
                if (color.alpha() != 0) {
                    painter.setBrush(new QBrush(color));
                    painter.setPen(Qt.PenStyle.NoPen);
                    painter.drawRoundedRect(0, 0, width, height, 0, 0);
                }
                if (borderThickness != 0)
                    drawBorders(painter);
            }
            for (HintBox subBox : subgridBoxes) {
                subBox.paint(painter);
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
            int topEdgePenOffset = edgeThickness / 2;
            int leftEdgePenOffset = edgeThickness / 2;
            int bottomEdgePenOffset = edgeThickness / 2;
            int rightEdgePenOffset = edgeThickness / 2;
            int insidePenOffset = borderThickness / 4;
            int gridTopEdgeExtraVertical = gridTopEdge ? edgeThickness / 2 : 0;
            int gridBottomEdgeExtraVertical = gridBottomEdge ? edgeThickness / 2 : 0;
            int gridLeftEdgeExtraHorizontal = gridLeftEdge ? edgeThickness / 2 : 0;
            int gridRightEdgeExtraHorizontal = gridRightEdge ? edgeThickness / 2 : 0;
            // Top left corner.
            // Vertical line.
            drawVerticalGridLine(painter,
                    drawGridEdgeBorders || (!gridLeftEdge && !gridTopEdge),
                    gridLeftEdge,
                    edgePen,
                    insidePen,
                    left,
                    leftEdgePenOffset,
                    insidePenOffset,
                    top,
                    Math.min(bottom - borderThickness, top + gridTopEdgeExtraVertical + borderLength / 2)
            );
            // Horizontal line.
            drawHorizontalGridLine(painter,
                    drawGridEdgeBorders || (!gridTopEdge && !gridLeftEdge),
                    gridTopEdge,
                    edgePen,
                    insidePen,
                    top,
                    topEdgePenOffset,
                    insidePenOffset,
                    left,
                    Math.min(right - borderThickness, left + gridLeftEdgeExtraHorizontal + borderLength / 2)
            );
            // Top right corner.
            // Vertical line.
            drawVerticalGridLine(painter,
                    drawGridEdgeBorders || (!gridRightEdge && !gridTopEdge),
                    gridRightEdge,
                    edgePen,
                    insidePen,
                    right,
                    rightEdgePenOffset - (edgeThickness - 1),
                    insidePenOffset - (bottomRightInsideThickness - 1),
                    top,
                    Math.min(bottom - borderThickness, top + gridTopEdgeExtraVertical + borderLength / 2)
            );

            // Horizontal line.
            drawHorizontalGridLine(painter,
                    drawGridEdgeBorders || (!gridTopEdge && !gridRightEdge),
                    gridTopEdge,
                    edgePen,
                    insidePen,
                    top,
                    topEdgePenOffset,
                    insidePenOffset,
                    Math.max(left + borderThickness, right - (gridRightEdgeExtraHorizontal - 1) - borderLength / 2),
                    right + 1
            );
            // Bottom left corner.
            // Vertical line.
            drawVerticalGridLine(painter,
                    drawGridEdgeBorders || (!gridLeftEdge && !gridBottomEdge),
                    gridLeftEdge,
                    edgePen,
                    insidePen,
                    left,
                    leftEdgePenOffset,
                    insidePenOffset,
                    Math.max(top + borderThickness, bottom - (gridBottomEdgeExtraVertical - 1) - borderLength / 2),
                    bottom + 1
            );
            // Horizontal line.
            drawHorizontalGridLine(painter,
                    drawGridEdgeBorders || (!gridBottomEdge && !gridLeftEdge),
                    gridBottomEdge,
                    edgePen,
                    insidePen,
                    bottom,
                    bottomEdgePenOffset - (edgeThickness - 1),
                    insidePenOffset - (bottomRightInsideThickness - 1),
                    left,
                    Math.min(right - borderThickness, left + gridLeftEdgeExtraHorizontal + borderLength / 2)
            );
            // Bottom right corner.
            // Vertical line.
            drawVerticalGridLine(painter,
                    drawGridEdgeBorders || (!gridRightEdge && !gridBottomEdge),
                    gridRightEdge,
                    edgePen,
                    insidePen,
                    right,
                    rightEdgePenOffset - (edgeThickness - 1),
                    insidePenOffset - (bottomRightInsideThickness - 1),
                    Math.max(top + borderThickness, bottom - (gridBottomEdgeExtraVertical - 1) - borderLength / 2),
                    bottom + 1
            );
            // Horizontal line.
            drawHorizontalGridLine(painter,
                    drawGridEdgeBorders || (!gridBottomEdge && !gridRightEdge),
                    gridBottomEdge,
                    edgePen,
                    insidePen,
                    bottom,
                    bottomEdgePenOffset - (edgeThickness - 1),
                    insidePenOffset - (bottomRightInsideThickness - 1),
                    Math.max(left + borderThickness, right - (gridRightEdgeExtraHorizontal - 1) - borderLength / 2),
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
            painter.setPen(isEdge ? edgePen : insidePen);
            if (painter.pen().width() == 0)
                return;
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
            painter.setPen(isEdge ? edgePen : insidePen);
            if (painter.pen().width() == 0)
                return;
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

    /**
     * For opacity <= 1.0, bake alpha into the color.
     * For opacity > 1.0, use full alpha; stacking is handled in
     * StackedShadowEffect#draw(QPainter).
     */
    private static QColor stackedShadowColor(Shadow shadow) {
        double opacity = shadow.opacity();
        int alpha = (int) Math.round(Math.min(opacity, 1.0) * 255);
        QColor base = qColor(shadow.hexColor(), 1.0);
        return new QColor(base.red(), base.green(), base.blue(), alpha);
    }

    private static QtFontStyle buildQtFontStyle(FontStyle fs, QFont font,
                                                            QFontMetrics metrics,
                                                            QColor prefixColor,
                                                            Shadow prefixShadow,
                                                            double screenScale) {
        Shadow ps = prefixShadow != null ? prefixShadow : fs.shadow();
        return new QtFontStyle(
                font, metrics,
                qColor(fs.hexColor(), fs.opacity()),
                prefixColor,
                qColor(fs.outlineHexColor(), fs.outlineOpacity()),
                (int) Math.round(fs.outlineThickness() * screenScale),
                stackedShadowColor(fs.shadow()),
                fs.shadow().opacity(),
                fs.shadow().blurRadius() * screenScale,
                fs.shadow().horizontalOffset() * screenScale,
                fs.shadow().verticalOffset() * screenScale,
                stackedShadowColor(ps),
                ps.opacity(),
                ps.blurRadius() * screenScale,
                ps.horizontalOffset() * screenScale,
                ps.verticalOffset() * screenScale
        );
    }

    private static boolean fontShapeEquals(FontStyle a, FontStyle b) {
        return a.name().equals(b.name()) &&
               a.weight() == b.weight() &&
               Double.compare(a.size(), b.size()) == 0;
    }


    private static QtHintFontStyle buildQtHintFontStyle(HintFontStyle hintFontStyle,
                                                       HintFontStyle prefixHintFontStyle,
                                                       double screenScale) {
        FontStyle defaultFontStyle = hintFontStyle.defaultFontStyle();
        FontStyle selectedFontStyle = hintFontStyle.selectedFontStyle();
        FontStyle focusedFontStyle = hintFontStyle.focusedFontStyle();
        boolean perKeyFont = !fontShapeEquals(defaultFontStyle, selectedFontStyle) ||
                             !fontShapeEquals(defaultFontStyle, focusedFontStyle);
        Shadow defaultShadow = defaultFontStyle.shadow();
        boolean perKeyShadow = !defaultShadow.equals(selectedFontStyle.shadow()) ||
                               !defaultShadow.equals(focusedFontStyle.shadow());
        if (prefixHintFontStyle != null) {
            perKeyShadow = perKeyShadow ||
                           !defaultShadow.equals(prefixHintFontStyle.defaultFontStyle().shadow()) ||
                           !defaultShadow.equals(prefixHintFontStyle.selectedFontStyle().shadow()) ||
                           !defaultShadow.equals(prefixHintFontStyle.focusedFontStyle().shadow());
        }
        QFont defaultFont = qFont(defaultFontStyle.name(), defaultFontStyle.size(), defaultFontStyle.weight());
        QFontMetrics defaultMetrics = correctedFontMetricsForScreenDpi(defaultFont, defaultFontStyle.size(), screenScale);
        QColor defaultPrefixColor = prefixHintFontStyle != null ?
                qColor(prefixHintFontStyle.defaultFontStyle().hexColor(), prefixHintFontStyle.defaultFontStyle().opacity()) : null;
        QColor selectedPrefixColor = prefixHintFontStyle != null ?
                qColor(prefixHintFontStyle.selectedFontStyle().hexColor(), prefixHintFontStyle.selectedFontStyle().opacity()) : null;
        QColor focusedPrefixColor = prefixHintFontStyle != null ?
                qColor(prefixHintFontStyle.focusedFontStyle().hexColor(), prefixHintFontStyle.focusedFontStyle().opacity()) : null;
        Shadow defaultPrefixShadow = prefixHintFontStyle != null ? prefixHintFontStyle.defaultFontStyle().shadow() : null;
        Shadow selectedPrefixShadow = prefixHintFontStyle != null ? prefixHintFontStyle.selectedFontStyle().shadow() : null;
        Shadow focusedPrefixShadow = prefixHintFontStyle != null ? prefixHintFontStyle.focusedFontStyle().shadow() : null;
        QtFontStyle defaultQtFontStyle = buildQtFontStyle(defaultFontStyle, defaultFont, defaultMetrics,
                defaultPrefixColor, defaultPrefixShadow, screenScale);
        QtFontStyle selectedQtFontStyle;
        QtFontStyle focusedQtFontStyle;
        if (perKeyFont) {
            QFont selectedFont = qFont(selectedFontStyle.name(), selectedFontStyle.size(), selectedFontStyle.weight());
            QFontMetrics selectedMetrics = correctedFontMetricsForScreenDpi(selectedFont, selectedFontStyle.size(), screenScale);
            selectedQtFontStyle = buildQtFontStyle(selectedFontStyle, selectedFont, selectedMetrics,
                    selectedPrefixColor, selectedPrefixShadow, screenScale);
            QFont focusedFont = qFont(focusedFontStyle.name(), focusedFontStyle.size(), focusedFontStyle.weight());
            QFontMetrics focusedMetrics = correctedFontMetricsForScreenDpi(focusedFont, focusedFontStyle.size(), screenScale);
            focusedQtFontStyle = buildQtFontStyle(focusedFontStyle, focusedFont, focusedMetrics,
                    focusedPrefixColor, focusedPrefixShadow, screenScale);
        }
        else {
            // Share same QFont/QFontMetrics as default
            selectedQtFontStyle = buildQtFontStyle(selectedFontStyle, defaultFont, defaultMetrics,
                    selectedPrefixColor, selectedPrefixShadow, screenScale);
            focusedQtFontStyle = buildQtFontStyle(focusedFontStyle, defaultFont, defaultMetrics,
                    focusedPrefixColor, focusedPrefixShadow, screenScale);
        }
        return new QtHintFontStyle(defaultQtFontStyle, selectedQtFontStyle, focusedQtFontStyle,
                perKeyFont, perKeyShadow, hintFontStyle.spacingPercent());
    }

    record QtFontStyle(QFont font, QFontMetrics metrics,
                             QColor color, QColor prefixColor,
                             QColor outlineColor, int outlineThickness,
                             QColor shadowColor, double shadowOpacity,
                             double shadowBlurRadius,
                             double shadowHorizontalOffset, double shadowVerticalOffset,
                             QColor prefixShadowColor, double prefixShadowOpacity,
                             double prefixShadowBlurRadius,
                             double prefixShadowHorizontalOffset,
                             double prefixShadowVerticalOffset) {
    }

    public record QtHintFontStyle(QtFontStyle defaultStyle,
                                        QtFontStyle selectedStyle,
                                        QtFontStyle focusedStyle,
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
                int textX = x;
                int textY = y;
                if (labelFontStyle.perKeyFont()) {
                    QtFontStyle qtFontStyle;
                    if (keyIndex <= selectedKeyEndIndex)
                        qtFontStyle = labelFontStyle.selectedStyle();
                    else if (keyIndex == selectedKeyEndIndex + 1)
                        qtFontStyle = labelFontStyle.focusedStyle();
                    else
                        qtFontStyle = labelFontStyle.defaultStyle();
                    int actualTextWidth = qtFontStyle.metrics().horizontalAdvance(keyText);
                    textX += (textWidth - actualTextWidth) / 2;
                    textY = (boxHeight + qtFontStyle.metrics().ascent() - qtFontStyle.metrics().descent()) / 2;
                }
                keyTexts.add(new HintKeyText(keyText, textX, textY, keyWidth,
                        keyIndex <= selectedKeyEndIndex,
                        keyIndex == selectedKeyEndIndex + 1,
                        prefixLength != -1 && keyIndex <= prefixLength - 1));
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

        private QtFontStyle hintKeyTextQtFontStyle(HintKeyText keyText) {
            if (keyText.isSelected())
                return labelFontStyle.selectedStyle();
            if (keyText.isFocused())
                return labelFontStyle.focusedStyle();
            return labelFontStyle.defaultStyle();
        }

        private void paint(QPainter painter, boolean forceOpaque) {
            painter.save();
            painter.translate(x, y);
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            painter.setRenderHint(QPainter.RenderHint.TextAntialiasing, true);
            painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform, true);
            painter.setFont(labelFontStyle.defaultStyle().font());

            // Draw outlines per state (each state may have different outline settings).
            paintOutlineForState(painter, forceOpaque, labelFontStyle.defaultStyle(),
                    keyText -> !keyText.isSelected() && !keyText.isFocused());
            paintOutlineForState(painter, forceOpaque, labelFontStyle.selectedStyle(),
                    HintKeyText::isSelected);
            paintOutlineForState(painter, forceOpaque, labelFontStyle.focusedStyle(),
                    HintKeyText::isFocused);

            // Text should override outline and background (punches through with its alpha).
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Source);
            for (HintKeyText keyText : keyTexts) {
                QtFontStyle qtFontStyle = hintKeyTextQtFontStyle(keyText);
                QColor color = keyText.isPrefix() && qtFontStyle.prefixColor() != null ?
                        qtFontStyle.prefixColor() : qtFontStyle.color();
                if (labelFontStyle.perKeyFont())
                    painter.setFont(qtFontStyle.font());
                painter.setPen(forceOpaque ? opaqueColor(color) : color);
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
        }

        ShadowGroupKey shadowGroupKey(HintKeyText keyText) {
            QtFontStyle qtFontStyle = hintKeyTextQtFontStyle(keyText);
            QColor c;
            double opacity, blurRadius, horizontalOffset, verticalOffset;
            if (keyText.isPrefix()) {
                c = qtFontStyle.prefixShadowColor();
                opacity = qtFontStyle.prefixShadowOpacity();
                blurRadius = qtFontStyle.prefixShadowBlurRadius();
                horizontalOffset = qtFontStyle.prefixShadowHorizontalOffset();
                verticalOffset = qtFontStyle.prefixShadowVerticalOffset();
            }
            else {
                c = qtFontStyle.shadowColor();
                opacity = qtFontStyle.shadowOpacity();
                blurRadius = qtFontStyle.shadowBlurRadius();
                horizontalOffset = qtFontStyle.shadowHorizontalOffset();
                verticalOffset = qtFontStyle.shadowVerticalOffset();
            }
            return new ShadowGroupKey(c.red(), c.green(), c.blue(), c.alpha(),
                                      opacity, blurRadius, horizontalOffset, verticalOffset);
        }

        void paintOpaqueFiltered(QPainter painter,
                                 Predicate<HintKeyText> filter) {
            painter.save();
            painter.translate(x, y);
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            painter.setRenderHint(QPainter.RenderHint.TextAntialiasing, true);
            painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform, true);
            painter.setFont(labelFontStyle.defaultStyle().font());
            paintOutlineForState(painter, true, labelFontStyle.defaultStyle(),
                    k -> filter.test(k) && !k.isSelected() && !k.isFocused());
            paintOutlineForState(painter, true, labelFontStyle.selectedStyle(),
                    k -> filter.test(k) && k.isSelected());
            paintOutlineForState(painter, true, labelFontStyle.focusedStyle(),
                    k -> filter.test(k) && k.isFocused());
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Source);
            for (HintKeyText keyText : keyTexts) {
                if (!filter.test(keyText))
                    continue;
                QtFontStyle qtFontStyle = hintKeyTextQtFontStyle(keyText);
                QColor color = keyText.isPrefix() && qtFontStyle.prefixColor() != null ?
                        qtFontStyle.prefixColor() : qtFontStyle.color();
                if (labelFontStyle.perKeyFont())
                    painter.setFont(qtFontStyle.font());
                painter.setPen(opaqueColor(color));
                painter.drawText(keyText.x() - left, keyText.y() - top, keyText.text());
            }
            painter.restore();
        }
    }

    private record ShadowGroupKey(int r, int g, int b, int a,
                                  double opacity, double blurRadius,
                                  double horizontalOffset, double verticalOffset) {
    }

    private record HintKeyText(String text, int x, int y, int width, boolean isSelected,
                               boolean isFocused,
                               boolean isPrefix) {

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
                                         int containerWidth,
                                         int containerHeight) {
        if (style.perKeyShadow()) {
            // Per-key shadow: always pre-render with per-group passes.
            preRenderLabelShadow(layer, labels, style,
                    containerWidth, containerHeight);
            return;
        }
        QtFontStyle defaultStyle = style.defaultStyle();
        if (defaultStyle.shadowColor().alpha() == 0)
            return;
        if (!qtHintFontStyleHasTransparency(style)) {
            // Fast path: opaque text, use Qt's effect directly.
            StackedShadowEffect effect = new StackedShadowEffect();
            effect.setBlurRadius(defaultStyle.shadowBlurRadius());
            effect.setOffset(defaultStyle.shadowHorizontalOffset(),
                    defaultStyle.shadowVerticalOffset());
            effect.setColor(defaultStyle.shadowColor());
            effect.setStackedOpacity(defaultStyle.shadowOpacity());
            layer.setGraphicsEffect(effect);
        }
        else {
            // Slow path: transparent text, pre-render shadow off-screen.
            preRenderLabelShadow(layer, labels, style,
                    containerWidth, containerHeight);
        }
    }

    private static void preRenderLabelShadow(HintPaintLayer layer,
                                             List<HintLabel> labels,
                                             QtHintFontStyle style,
                                             int containerWidth,
                                             int containerHeight) {
        if (style.perKeyShadow()) {
            preRenderPerGroupShadow(layer, labels, containerWidth, containerHeight);
            return;
        }
        QtFontStyle shadowStyle = style.defaultStyle();
        // Render labels into a source image with forced opaque colors.
        QImage sourceImage = new QImage(containerWidth, containerHeight,
                QImage.Format.Format_ARGB32_Premultiplied);
        sourceImage.fill(new QColor(0, 0, 0, 0));
        QPainter srcPainter = new QPainter(sourceImage);
        for (HintLabel label : labels) {
            label.paintOpaque(srcPainter);
        }
        srcPainter.end();
        ShadowImage shadow = renderShadowOnly(sourceImage, shadowStyle.shadowColor(),
                shadowStyle.shadowBlurRadius(), shadowStyle.shadowHorizontalOffset(),
                shadowStyle.shadowVerticalOffset(), containerWidth, containerHeight);
        layer.setShadowPixmap(QPixmap.fromImage(shadow.image()),
                shadow.x(), shadow.y());
        layer.shadowOpacity = shadowStyle.shadowOpacity();
        shadow.image().dispose();
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
        QGraphicsPixmapItem item =
                scene.addPixmap(QPixmap.fromImage(sourceImage));
        StackedShadowEffect effect = new StackedShadowEffect();
        effect.setBlurRadius(blurRadius);
        effect.setOffset(horizontalOffset, verticalOffset);
        effect.setColor(shadowColor);
        effect.setStackedOpacity(1.0);
        item.setGraphicsEffect(effect);
        QRectF bounds = scene.itemsBoundingRect();
        int boundsX = (int) Math.floor(bounds.x());
        int boundsY = (int) Math.floor(bounds.y());
        int boundsW = (int) Math.ceil(bounds.x() + bounds.width()) - boundsX;
        int boundsH = (int) Math.ceil(bounds.y() + bounds.height()) - boundsY;
        QRectF intBounds = new QRectF(boundsX, boundsY, boundsW, boundsH);
        QImage resultImage = new QImage(boundsW, boundsH,
                QImage.Format.Format_ARGB32_Premultiplied);
        resultImage.fill(new QColor(0, 0, 0, 0));
        QPainter resultPainter = new QPainter(resultImage);
        scene.render(resultPainter, new QRectF(resultImage.rect()), intBounds);
        resultPainter.end();
        scene.dispose();
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
     * bakes stacking opacity, and composites into a single shadow pixmap.
     */
    private static void preRenderPerGroupShadow(
            HintPaintLayer layer, List<HintLabel> labels,
            int containerWidth, int containerHeight) {
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
            if (group.a() == 0 || group.opacity() == 0)
                continue;
            // Render source image with only keys matching this group.
            QImage sourceImage = new QImage(containerWidth, containerHeight,
                    QImage.Format.Format_ARGB32_Premultiplied);
            sourceImage.fill(new QColor(0, 0, 0, 0));
            QPainter srcPainter = new QPainter(sourceImage);
            for (HintLabel label : labels) {
                label.paintOpaqueFiltered(srcPainter,
                        keyText -> label.shadowGroupKey(keyText).equals(group));
            }
            srcPainter.end();
            QColor shadowColor = new QColor(group.r(), group.g(), group.b(), group.a());
            ShadowImage shadow = renderShadowOnly(sourceImage, shadowColor,
                    group.blurRadius(), group.horizontalOffset(), group.verticalOffset(),
                    containerWidth, containerHeight);
            QImage groupShadow = shadow.image();
            int boundsX = shadow.x();
            int boundsY = shadow.y();
            // Bake stacking opacity into the group shadow image.
            QImage stackedShadow;
            if (group.opacity() > 1.0) {
                stackedShadow = new QImage(groupShadow.width(), groupShadow.height(),
                        QImage.Format.Format_ARGB32_Premultiplied);
                stackedShadow.fill(new QColor(0, 0, 0, 0));
                QPainter stackPainter = new QPainter(stackedShadow);
                int fullLayers = (int) group.opacity();
                double remainder = group.opacity() - fullLayers;
                for (int i = 0; i < fullLayers; i++) {
                    stackPainter.drawImage(0, 0, groupShadow);
                }
                if (remainder > 1e-6) {
                    stackPainter.setOpacity(remainder);
                    stackPainter.drawImage(0, 0, groupShadow);
                }
                stackPainter.end();
                groupShadow.dispose();
            }
            else {
                stackedShadow = groupShadow;
            }
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
                newCombined.fill(new QColor(0, 0, 0, 0));
                QPainter combinePainter = new QPainter(newCombined);
                combinePainter.drawImage(combinedX - newX, combinedY - newY,
                        combinedShadow);
                combinePainter.drawImage(boundsX - newX, boundsY - newY,
                        stackedShadow);
                combinePainter.end();
                combinedShadow.dispose();
                stackedShadow.dispose();
                combinedShadow = newCombined;
                combinedX = newX;
                combinedY = newY;
            }
        }
        if (combinedShadow != null) {
            layer.setShadowPixmap(QPixmap.fromImage(combinedShadow),
                    combinedX, combinedY);
            layer.shadowOpacity = 1.0; // Stacking already baked in.
            combinedShadow.dispose();
        }
    }

    private static class HintPaintLayer extends QWidget {

        private final List<HintBox> boxes;
        private final List<HintLabel> labels;
        // Pre-rendered shadow-only pixmap (null if no shadow or opaque text).
        private QPixmap shadowPixmap;
        private int shadowPixmapX, shadowPixmapY;
        // Shadow stacking: opacity > 1 means multiple layers.
        private double shadowOpacity;

        HintPaintLayer(QWidget parent, List<HintBox> boxes, List<HintLabel> labels) {
            super(parent);
            this.boxes = boxes;
            this.labels = labels;
        }

        void setShadowPixmap(QPixmap shadowPixmap, int x, int y) {
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
                if (shadowOpacity <= 1.0) {
                    painter.drawPixmap(shadowPixmapX, shadowPixmapY,
                            shadowPixmap);
                }
                else {
                    int fullLayers = (int) shadowOpacity;
                    double remainder = shadowOpacity - fullLayers;
                    for (int i = 0; i < fullLayers; i++) {
                        painter.drawPixmap(shadowPixmapX, shadowPixmapY,
                                shadowPixmap);
                    }
                    if (remainder > 1e-6) {
                        double before = painter.opacity();
                        painter.setOpacity(before * remainder);
                        painter.drawPixmap(shadowPixmapX, shadowPixmapY,
                                shadowPixmap);
                        painter.setOpacity(before);
                    }
                }
            }
            for (HintLabel label : labels) {
                label.paint(painter);
            }
            painter.end();
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
            if (sizeOrShadowChanged) {
                WinDef.POINT mousePosition = WindowsMouse.findMousePosition();
                Screen activeScreen = WindowsScreen.findActiveScreen(mousePosition);
                applyIndicatorShadowEffect(activeScreen.scale() * zoomPercent());
                moveAndResizeIndicatorWindow();
            }
        }
        showingIndicator = true;
        indicatorWindow.widget.setEdgeCount(indicator.edgeCount());
        indicatorWindow.widget.setColor(new QColor(indicator.hexColor()));
        IndicatorOutline outer = indicator.outerOutline();
        IndicatorOutline inner = indicator.innerOutline();
        // Widget draws opaque; the shadow effect redraws with real opacity.
        indicatorWindow.widget.setOutlines(
                outer.thickness(), new QColor(outer.hexColor()), outer.fillPercent(),
                inner.thickness(), new QColor(inner.hexColor()), inner.fillPercent());
        if (indicatorWindow.widget.customGraphicsEffect != null) {
            setIndicatorEffectColors(indicatorWindow.widget.customGraphicsEffect);
        }
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
            indicatorWindow.labelWidget.show();
        }
        else {
            indicatorWindow.labelWidget.setLabel(null, null, 0, null, 0, null, 0);
            indicatorWindow.labelWidget.setGraphicsEffect(null);
            indicatorWindow.labelWidget.hide();
        }
        indicatorWindow.window.show();
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
        PixmapAndPosition pixmapAndPosition =
                new PixmapAndPosition(pixmap,
                        container.geometry().x() + hintBoxGeometry.x(),
                        container.geometry().y() + hintBoxGeometry.y(), hintMesh,
                        hintMeshWindow.window.x(), hintMeshWindow.window.y());
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
        moveAndResizeIndicatorWindow(mousePosition);
    }

}
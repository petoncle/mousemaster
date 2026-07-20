package mousemaster.renderer;

import mousemaster.qt.*;

import io.qt.core.*;
import io.qt.gui.*;
import io.qt.widgets.*;
import mousemaster.*;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cross-platform Qt rendering of the mouse indicator: owns the indicator widget, its
 * label widget and shadow effects, and computes where and how big to draw the indicator.
 * The platform overlay owns the native window (styles its handle) and supplies the cursor,
 * screen and zoom.
 */
public final class IndicatorRenderer {

    private TransparentWindow window;
    private IndicatorWidget widget;
    private IndicatorLabelWidget labelWidget;
    private Indicator currentIndicator;
    private int maxIndicatorShadowPadding;
    private FadeAnimator fadeAnimator;
    private boolean showing;

    /** Lazily creates the window and its widgets; the host styles winId() afterwards. */
    public TransparentWindow window() {
        if (window == null) {
            window = new TransparentWindow();
            widget = new IndicatorWidget(window);
            // Label widget is a child of window (not widget) so it renders on top
            // of the shadow effect and can clear/redraw the fill area.
            labelWidget = new IndicatorLabelWidget(window);
        }
        return window;
    }

    public boolean showing() {
        return showing;
    }

    public Indicator currentIndicator() {
        return currentIndicator;
    }

    private int indicatorSize(Indicator indicator, double screenScale, double zoomPercent) {
        return (int) Math.floor(indicator.size() * screenScale * zoomPercent);
    }

    private int indicatorOutlinePadding(Indicator indicator, double screenScale, double zoomPercent) {
        double scaled = Math.max(
                indicator.outerOutline().thickness(),
                indicator.innerOutline().thickness()) * screenScale * zoomPercent;
        return (int) Math.ceil(IndicatorWidget.miterPadding(scaled, indicator.edgeCount()));
    }

    private int indicatorShadowPadding(Indicator indicator, double scale) {
        if (indicator.shadow().blurRadius() == 0)
            return 0;
        return (int) Math.ceil((indicator.shadow().blurRadius() +
                Math.max(Math.abs(indicator.shadow().horizontalOffset()),
                         Math.abs(indicator.shadow().verticalOffset()))) * scale);
    }

    /** Shows/updates the indicator: detects what changed, repositions when needed, and
     *  renders. The overlay supplies the cursor rectangle, its visual center, and the
     *  active screen and zoom. */
    public void setIndicator(Indicator indicator, boolean fadeAnimationEnabled,
                             Duration fadeAnimationDuration, boolean allowFade,
                             Rectangle mouseRectangle, Point cursorVisualCenter,
                             Screen activeScreen, Zoom zoom) {
        Indicator oldIndicator = currentIndicator;
        if (showing && oldIndicator != null && oldIndicator.equals(indicator))
            return;
        boolean wasShowing = showing;
        // If re-showing during a fade-out, cancel the fade-out.
        cancelFadeOut();
        boolean created = oldIndicator == null;
        boolean applyShadow;
        boolean sizeOrShadowOrPositionChanged;
        if (created) {
            applyShadow = true;
            sizeOrShadowOrPositionChanged = true;
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
            applyShadow = sizeOrShadowChanged;
            sizeOrShadowOrPositionChanged = sizeOrShadowChanged || positionChanged;
        }
        // Position the (hidden) window before showIndicator shows it.
        if (created || sizeOrShadowOrPositionChanged)
            reposition(indicator, mouseRectangle, cursorVisualCenter, activeScreen, zoom);
        double shadowScale = activeScreen.scale() * zoomPercent(zoom);
        showIndicator(indicator, applyShadow, shadowScale, wasShowing,
                fadeAnimationEnabled, fadeAnimationDuration, allowFade);
    }

    /** Repositions/resizes the current indicator for the cursor, screen and zoom. */
    public void reposition(Rectangle mouseRectangle, Point cursorVisualCenter,
                           Screen activeScreen, Zoom zoom) {
        reposition(currentIndicator, mouseRectangle, cursorVisualCenter, activeScreen, zoom);
    }

    private void reposition(Indicator indicator, Rectangle mouseRectangle,
                            Point cursorVisualCenter, Screen activeScreen, Zoom zoom) {
        double screenScale = activeScreen.scale();
        double zoomPercent = zoomPercent(zoom);
        int size = indicatorSize(indicator, screenScale, zoomPercent);
        int outlinePadding = indicatorOutlinePadding(indicator, screenScale, zoomPercent);
        int shadowPadding = indicatorShadowPadding(indicator, screenScale * zoomPercent);
        int visualSize = size + 2 * outlinePadding;
        Point topLeft = indicatorTopLeft(mouseRectangle, cursorVisualCenter, activeScreen,
                zoom, indicator, visualSize);
        moveAndResize((int) Math.round(topLeft.x()), (int) Math.round(topLeft.y()),
                size, outlinePadding, shadowPadding, screenScale * zoomPercent, zoomPercent);
    }

    private static final int indicatorEdgeThreshold = 100;

    /**
     * Returns the indicator top-left position for the given indicator size.
     * For CENTER, the indicator is centered on the cursor's visual center.
     * For corner positions, the indicator is placed in that corner relative to the cursor,
     * flipping to the opposite side when near the corresponding screen edge.
     */
    private Point indicatorTopLeft(Rectangle mouseRectangle, Point cursorVisualCenter,
                                   Screen activeScreen, Zoom zoom, Indicator indicator,
                                   int visualSize) {
        Rectangle screen = activeScreen.rectangle();
        if (indicator.position() == IndicatorPosition.CENTER) {
            double centerX = mouseRectangle.x() + cursorVisualCenter.x();
            double centerY = mouseRectangle.y() + cursorVisualCenter.y();
            centerX = Math.max(screen.x(), Math.min(centerX,
                    screen.x() + screen.width()));
            centerY = Math.max(screen.y(), Math.min(centerY,
                    screen.y() + screen.height()));
            return new Point(zoomedX(centerX, zoom) - visualSize / 2.0,
                    zoomedY(centerY, zoom) - visualSize / 2.0);
        }
        int mouseX = Math.max(screen.x(), Math.min(mouseRectangle.x(),
                screen.x() + screen.width()));
        int mouseY = Math.max(screen.y(), Math.min(mouseRectangle.y(),
                screen.y() + screen.height()));
        IndicatorPosition position = indicator.position();
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
                mouseX + mouseRectangle.width() / 2 : mouseX - visualSize;
        boolean nearBottomEdge = mouseY >=
                screen.y() + screen.height() - indicatorEdgeThreshold;
        boolean nearTopEdge = mouseY <=
                screen.y() + indicatorEdgeThreshold;
        boolean placeBottom = defaultBottom ? !nearBottomEdge : nearTopEdge;
        int indicatorY = placeBottom ?
                mouseY + mouseRectangle.height() / 2 : mouseY - visualSize;
        return new Point(zoomedX(indicatorX, zoom), zoomedY(indicatorY, zoom));
    }

    private static double zoomedX(double x, Zoom zoom) {
        return zoom == null ? x : zoom.zoomedX(x);
    }

    private static double zoomedY(double y, Zoom zoom) {
        return zoom == null ? y : zoom.zoomedY(y);
    }

    private static double zoomPercent(Zoom zoom) {
        return zoom == null ? 1 : zoom.percent();
    }

    /** An offscreen-rendered indicator: premultiplied ARGB (0xAARRGGBB), row-major. */
    public record CursorImage(int[] argb, int width, int height) {}

    /** Renders the indicator's widget tree into a premultiplied-ARGB image for use as the
     *  system cursor, centered on the indicator's visual center. */
    public CursorImage renderCursorImage(Indicator indicator, double scale) {
        int size = indicatorSize(indicator, scale, 1);
        if (size <= 0)
            return null;
        int outlinePadding = indicatorOutlinePadding(indicator, scale, 1);
        int shadowPadding = indicatorShadowPadding(indicator, scale);
        int imageSize = size + 2 * (outlinePadding + shadowPadding);
        window();
        applyIndicator(indicator, true, scale);
        sizeWidgetsForRender(size, outlinePadding, shadowPadding, scale);
        QImage image = new QImage(imageSize, imageSize,
                QImage.Format.Format_ARGB32_Premultiplied);
        image.fill(0);
        // The label's point-size font resolves against the image's DPI; match the target
        // screen so it renders at the right size on any screen. QImage exposes only
        // dots-per-meter, hence the inch-to-meter conversion.
        int dotsPerMeter = (int) Math.round(scale * 96.0 / 0.0254);
        image.setDotsPerMeterX(dotsPerMeter);
        image.setDotsPerMeterY(dotsPerMeter);
        window.render(image);
        int total = imageSize * imageSize * 4;
        byte[] bytes = new byte[total];
        ByteBuffer buffer = image.bits();
        buffer.position(0);
        buffer.get(bytes);
        image.dispose();
        int[] argb = new int[imageSize * imageSize];
        for (int i = 0; i < argb.length; i++) {
            int o = i * 4;
            argb[i] = ((bytes[o + 3] & 0xFF) << 24) | ((bytes[o + 2] & 0xFF) << 16) |
                      ((bytes[o + 1] & 0xFF) << 8) | (bytes[o] & 0xFF);
        }
        return new CursorImage(argb, imageSize, imageSize);
    }

    /** Sizes the window and widgets for an offscreen render: widget/label sit inside the
     *  shadow padding, matching moveAndResize's layout but without on-screen positioning. */
    private void sizeWidgetsForRender(int size, int outlinePadding, int shadowPadding,
                                      double scale) {
        widget.setOutlineScale(scale);
        int widgetSize = size + 2 * outlinePadding;
        int windowSize = size + 2 * (outlinePadding + shadowPadding);
        window.resize(windowSize, windowSize);
        widget.move(shadowPadding, shadowPadding);
        widget.resize(widgetSize, widgetSize);
        labelWidget.move(shadowPadding, shadowPadding);
        labelWidget.resize(widgetSize, widgetSize);
        labelWidget.setIndicatorOutlinePadding(outlinePadding);
        labelWidget.setLabelFontScale(1);
    }

    /** Draws label text centered at (centerX, centerY): outline (if any) then fill, using the
     *  caller's already-sized font. Shared by the on-screen label widget and the cursor. */
    static void drawLabelText(QPainter painter, String text, QFont font, double centerX,
                              double centerY, int outlineThickness, QColor outlineColor,
                              QColor labelColor) {
        QFontMetrics fontMetrics = new QFontMetrics(font);
        int textX = (int) Math.round(centerX - fontMetrics.horizontalAdvance(text) / 2.0);
        QRect tightRect = fontMetrics.tightBoundingRect(text);
        int textY = (int) Math.round(centerY - tightRect.y() - tightRect.height() / 2.0);
        tightRect.dispose();
        fontMetrics.dispose();
        if (outlineThickness != 0 && outlineColor != null && outlineColor.alpha() != 0) {
            QPen outlinePen = new QPen(outlineColor);
            outlinePen.setWidth(outlineThickness);
            outlinePen.setJoinStyle(Qt.PenJoinStyle.RoundJoin);
            painter.setPen(outlinePen);
            painter.setBrush(Qt.BrushStyle.NoBrush);
            QPainterPath textPath = new QPainterPath();
            textPath.addText(textX, textY, font, text);
            painter.drawPath(textPath);
            textPath.dispose();
            outlinePen.dispose();
        }
        if (labelColor != null && labelColor.alpha() != 0) {
            painter.setPen(labelColor);
            painter.drawText(textX, textY, text);
        }
    }

    /** Applies the indicator to the widgets (shape, outlines, shadow effect, label) without
     *  showing or positioning. Shared by the on-screen path and the offscreen cursor render. */
    private void applyIndicator(Indicator indicator, boolean applyShadow, double shadowScale) {
        currentIndicator = indicator;
        if (applyShadow)
            applyShadowEffect(shadowScale);
        widget.cleared = false;
        widget.setEdgeCount(indicator.edgeCount());
        widget.setColor(indicator.opacity() > 0
                ? new QColor(indicator.hexColor()) : new QColor(0, 0, 0, 0));
        IndicatorOutline outer = indicator.outerOutline();
        IndicatorOutline inner = indicator.innerOutline();
        widget.setOutlines(
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
        if (widget.customGraphicsEffect != null)
            setIndicatorEffectColors(widget.customGraphicsEffect);
        if (indicator.labelEnabled() && indicator.labelText() != null &&
            indicator.labelFontStyle() != null) {
            FontStyle labelFontStyle = indicator.labelFontStyle();
            QFont labelFont = QtHintFont.qFont(labelFontStyle.name(), labelFontStyle.size(), labelFontStyle.weight());
            QColor labelColor = QtColorUtil.qColor(labelFontStyle.hexColor(), labelFontStyle.opacity());
            QColor labelOutlineColor = QtColorUtil.qColor(labelFontStyle.outlineHexColor(), labelFontStyle.outlineOpacity());
            labelWidget.setLabel(indicator.labelText(), labelFont, labelFontStyle.size(),
                    labelColor,
                    (int) Math.round(labelFontStyle.outlineThickness()), labelOutlineColor,
                    indicator.edgeCount());
            Shadow labelShadow = labelFontStyle.shadow();
            QColor labelShadowColor = QtColorUtil.qColor(labelShadow.hexColor(), labelShadow.opacity());
            if (labelShadowColor.alpha() != 0) {
                StackedShadowEffect effect = new StackedShadowEffect();
                effect.setBlurRadius(labelShadow.blurRadius() * shadowScale);
                effect.setOffset(labelShadow.horizontalOffset() * shadowScale,
                        labelShadow.verticalOffset() * shadowScale);
                effect.setColor(labelShadowColor);
                effect.setStackCount(labelShadow.stackCount());
                labelWidget.setGraphicsEffect(effect);
            }
            else {
                labelWidget.setGraphicsEffect(null);
            }
            labelShadowColor.dispose();
            labelWidget.show();
        }
        else {
            labelWidget.setLabel(null, null, 0, null, 0, null, 0);
            labelWidget.setGraphicsEffect(null);
            labelWidget.hide();
        }
    }

    /** Applies the indicator, then shows the window (with a fade-in on first appearance). */
    private void showIndicator(Indicator indicator, boolean applyShadow, double shadowScale,
                               boolean wasShowing, boolean fadeAnimationEnabled,
                               Duration fadeAnimationDuration, boolean allowFade) {
        applyIndicator(indicator, applyShadow, shadowScale);
        window.show();
        widget.repaint();
        showing = true;
        if (!wasShowing) {
            fadeAnimator = new FadeAnimator(
                    window::setWindowOpacity,
                    this::doHide,
                    fadeAnimationEnabled,
                    fadeAnimationDuration);
            if (allowFade && fadeAnimator.isEnabled()) {
                window.setWindowOpacity(0.0);
                fadeAnimator.startFadeIn();
            }
        }
    }

    /** Moves and resizes the window + widgets to the computed visual top-left and sizes. */
    private void moveAndResize(int visualTopLeftX, int visualTopLeftY, int size,
                              int outlinePadding, int shadowPadding,
                              double outlineScale, double labelFontScale) {
        widget.setOutlineScale(outlineScale);
        // Never shrink the window: the DWM compositor would show the old, larger surface
        // at the new smaller size for one frame, mispositioning the indicator. Extra area
        // is transparent; the visible indicator stays at visualTopLeft regardless.
        maxIndicatorShadowPadding = Math.max(maxIndicatorShadowPadding, shadowPadding);
        shadowPadding = maxIndicatorShadowPadding;
        int totalPadding = outlinePadding + shadowPadding;
        int widgetSize = size + 2 * outlinePadding;
        int windowSize = size + 2 * totalPadding;
        window.move(visualTopLeftX - shadowPadding, visualTopLeftY - shadowPadding);
        window.resize(windowSize, windowSize);
        widget.move(shadowPadding, shadowPadding);
        widget.resize(widgetSize, widgetSize);
        labelWidget.move(shadowPadding, shadowPadding);
        labelWidget.resize(widgetSize, widgetSize);
        labelWidget.setIndicatorOutlinePadding(outlinePadding);
        labelWidget.setLabelFontScale(labelFontScale);
    }

    public void hide(boolean allowFade) {
        if (!showing)
            return;
        if (allowFade && fadeAnimator != null && fadeAnimator.shouldDeferHide())
            return;
        doHide();
    }

    public void cancelFadeOut() {
        if (fadeAnimator != null && fadeAnimator.isFadingOut())
            fadeAnimator.cancelAndResetOpacity();
    }

    private void doHide() {
        showing = false;
        if (fadeAnimator != null)
            fadeAnimator.cancel();
        // Paint the surface fully transparent before hiding.
        widget.cleared = true;
        widget.repaint();
        window.hide();
        window.setWindowOpacity(1.0);
    }

    boolean indicatorHasTransparency() {
        if (currentIndicator.opacity() < 1.0)
            return true;
        IndicatorOutline outer = currentIndicator.outerOutline();
        IndicatorOutline inner = currentIndicator.innerOutline();
        return (outer.thickness() > 0 && outer.opacity() < 1.0) ||
               (inner.thickness() > 0 && inner.opacity() < 1.0);
    }

    private void setIndicatorEffectColors(IndicatorShadowEffect effect) {
        IndicatorOutline outer = currentIndicator.outerOutline();
        IndicatorOutline inner = currentIndicator.innerOutline();
        effect.setColors(
                QtColorUtil.qColor(currentIndicator.hexColor(), currentIndicator.opacity()),
                QtColorUtil.qColor(outer.hexColor(), outer.opacity()),
                QtColorUtil.qColor(inner.hexColor(), inner.opacity()));
    }

    private void applyShadowEffect(double scale) {
        Shadow shadow = currentIndicator.shadow();
        QColor baseColor = QtColorUtil.qColor(shadow.hexColor(), 1.0);
        boolean hasShadow = shadow.opacity() > 0 && shadow.blurRadius() > 0;
        if (hasShadow) {
            IndicatorShadowEffect effect = new IndicatorShadowEffect(widget, this);
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
            widget.customGraphicsEffect = effect;
            widget.setGraphicsEffect(effect);
        }
        else if (indicatorHasTransparency()) {
            IndicatorShadowEffect effect = new IndicatorShadowEffect(widget, this);
            effect.setTransparencyOnly(true);
            setIndicatorEffectColors(effect);
            widget.customGraphicsEffect = effect;
            widget.setGraphicsEffect(effect);
        }
        else {
            widget.customGraphicsEffect = null;
            widget.setGraphicsEffect(null);
        }
        baseColor.dispose();
    }

    private class IndicatorWidget extends QWidget {

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

    public static class IndicatorShadowEffect extends StackedShadowEffect {

        private final IndicatorWidget widget;
        private final IndicatorRenderer renderer;
        private QColor fillColor;
        private QColor outerOutlineColor;
        private QColor innerOutlineColor;

        IndicatorShadowEffect(IndicatorWidget widget, IndicatorRenderer renderer) {
            this.widget = widget;
            this.renderer = renderer;
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
            if (!renderer.indicatorHasTransparency())
                return;
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            widget.drawContent(painter, fillColor, outerOutlineColor, innerOutlineColor, false);
        }
    }


    private class IndicatorLabelWidget extends QWidget {

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
            double availableSize = Math.min(width(), height()) - 2 * indicatorOutlinePadding;
            IndicatorWidget.PolygonLayout polygonLayout =
                    IndicatorWidget.polygonLayout(availableSize, edgeCount);
            double centerX = width() / 2.0 + polygonLayout.offsetX();
            double centerY = height() / 2.0 + polygonLayout.offsetY();
            drawLabelText(painter, labelText, labelFont, centerX, centerY,
                    outlineThickness, outlineColor, labelColor);
            painter.end();
            painter.dispose();
        }
    }

}

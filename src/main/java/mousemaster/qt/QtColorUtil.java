package mousemaster.qt;

import io.qt.core.Qt;
import io.qt.gui.*;
import mousemaster.HintGradientColor;
import mousemaster.Point;
import mousemaster.Rectangle;
import mousemaster.Shadow;

import java.util.HashMap;
import java.util.Map;

public final class QtColorUtil {

    private QtColorUtil() {
    }

    public static QColor qColor(String hexColor, double opacity) {
        return QColor.fromRgba(rgba(hexColor, opacity));
    }

    /** Constructing a QtJambi object costs ~10us and a screen-sized hint grid paints tens of
     *  thousands of boxes sharing a few appearances. QPainter copies the state it is given, so one
     *  brush or pen can back any number of paints. */
    private static final Map<Integer, QBrush> brushByRgba = new HashMap<>();
    private static final Map<PenKey, QPen> penByKey = new HashMap<>();
    private static QBrush noBrush;
    private static QBrush opaqueWhiteBrush;

    private record PenKey(int rgba, int width, Qt.PenCapStyle capStyle,
                          Qt.PenJoinStyle joinStyle) {
    }

    public static QBrush qBrush(QColor color) {
        return qBrush(color.rgba());
    }

    public static QBrush qBrush(int rgba) {
        return brushByRgba.computeIfAbsent(rgba, key -> {
            QColor color = QColor.fromRgba(key);
            QBrush brush = new QBrush(color);
            color.dispose();
            return brush;
        });
    }

    private record GradientKey(HintGradientColor color, double opacity, double startX,
                               double startY, double endX, double endY) {
    }

    private static final Map<GradientKey, QBrush> brushByGradient = new HashMap<>();

    /** One sweep inside every shape it fills, whatever that shape's size and position. */
    public static QBrush qBrush(HintGradientColor color, double opacity) {
        Rectangle unit = new Rectangle(0, 0, 1, 1);
        return gradientQBrush(color, opacity, color.direction().start(unit),
                color.direction().end(unit), QGradient.CoordinateMode.ObjectBoundingMode);
    }

    /** A round sweep takes its extent from the second point: a radius, or two semi-axes. */
    private static QGradient gradient(HintGradientColor color, double startX, double startY,
                                      double endX, double endY) {
        return switch (color.direction().shape()) {
            case STRAIGHT -> new QLinearGradient(startX, startY, endX, endY);
            case CIRCLE -> new QRadialGradient(startX, startY,
                    Math.hypot(endX - startX, endY - startY));
            // An area flat on one axis leaves a circle of the other's extent.
            case ELLIPSE -> new QRadialGradient(startX, startY,
                    endX == startX ? endY - startY : endX - startX);
        };
    }

    /** Qt has no elliptical gradient, so a circular one is squashed onto the area's shape. */
    private static void fitToArea(QBrush brush, HintGradientColor color, double startX,
                                  double startY, double endX, double endY) {
        if (color.direction().shape() != HintGradientColor.HintGradientShape.ELLIPSE ||
            endX == startX || endY == startY)
            return;
        QTransform transform = new QTransform();
        transform.translate(startX, startY);
        transform.scale(1, (endY - startY) / (endX - startX));
        transform.translate(-startX, -startY);
        brush.setTransform(transform);
        transform.dispose();
    }

    /** One sweep between two points of the painter's space, which shapes take their slice of. */
    public static QBrush qBrush(HintGradientColor color, double opacity, Point start,
                                Point end) {
        return gradientQBrush(color, opacity, start, end,
                QGradient.CoordinateMode.LogicalMode);
    }

    private static QBrush gradientQBrush(HintGradientColor color, double opacity, Point start,
                                         Point end, QGradient.CoordinateMode coordinateMode) {
        return brushByGradient.computeIfAbsent(
                new GradientKey(color, opacity, start.x(), start.y(), end.x(), end.y()), key -> {
                    QGradient gradient = gradient(color, key.startX(), key.startY(), key.endX(),
                            key.endY());
                    gradient.setCoordinateMode(coordinateMode);
                    // Qt runs a round sweep outward whatever its points, so it reverses its colors.
                    boolean reversed = color.direction().inverted();
                    // Qt copies what it is given, so the temporaries are freed here rather than
                    // on the cleanup thread.
                    QColor stopColor = new QColor();
                    for (int step = 0; step < HintGradientColor.rampSteps; step++) {
                        double sweepPosition =
                                (double) step / (HintGradientColor.rampSteps - 1);
                        stopColor.setRgba(rgba(
                                color.rgbAt(reversed ? 1 - sweepPosition : sweepPosition),
                                opacity));
                        gradient.setColorAt(sweepPosition, stopColor);
                    }
                    stopColor.dispose();
                    QBrush brush = new QBrush(gradient);
                    gradient.dispose();
                    fitToArea(brush, color, key.startX(), key.startY(), key.endX(), key.endY());
                    return brush;
                });
    }

    private record SweepKey(HintGradientColor color, double opacity, int step) {
    }

    private static final Map<SweepKey, QBrush> brushBySweepStep = new HashMap<>();

    public static QBrush qBrush(HintGradientColor color, double opacity, double sweepPosition) {
        int step = (int) Math.round(Math.clamp(sweepPosition, 0, 1) *
                                   (HintGradientColor.rampSteps - 1));
        return brushBySweepStep.computeIfAbsent(new SweepKey(color, opacity, step),
                key -> qBrush(rgba(color.rgbAt(
                        (double) step / (HintGradientColor.rampSteps - 1)), opacity)));
    }

    /** Dropped rather than freed outright: shown hint boxes hold these, so Qt frees each only once
     *  nothing refers to it. */
    public static void clearCaches() {
        brushByRgba.clear();
        brushByGradient.clear();
        brushBySweepStep.clear();
        penByKey.clear();
        opaqueByRgba.clear();
    }

    private static final Map<Integer, QColor> opaqueByRgba = new HashMap<>();

    /** The color at full alpha. Shadow sources are drawn opaque so the shadow keeps its strength,
     *  which asks for the same few variants once per label. */
    public static QColor opaque(QColor color) {
        if (color.alpha() == 255)
            return color;
        return opaqueByRgba.computeIfAbsent(color.rgba(),
                rgba -> new QColor(color.red(), color.green(), color.blue(), 255));
    }

    public static QBrush noBrush() {
        if (noBrush == null)
            noBrush = new QBrush(Qt.BrushStyle.NoBrush);
        return noBrush;
    }

    public static QBrush opaqueWhiteBrush() {
        if (opaqueWhiteBrush == null)
            opaqueWhiteBrush = new QBrush(new QColor(255, 255, 255, 255));
        return opaqueWhiteBrush;
    }

    public static QPen qPen(QColor color, int width, Qt.PenCapStyle capStyle,
                            Qt.PenJoinStyle joinStyle) {
        return penByKey.computeIfAbsent(
                new PenKey(color.rgba(), width, capStyle, joinStyle), key -> {
                    QPen pen = new QPen(color);
                    pen.setCapStyle(capStyle);
                    pen.setJoinStyle(joinStyle);
                    pen.setWidth(width);
                    return pen;
                });
    }

    public static QColor shadow(Shadow shadow) {
        return qColor(shadow.hexColor(), shadow.opacity());
    }

    public static int rgba(String hexColor, double opacity) {
        if (hexColor.startsWith("#"))
            hexColor = hexColor.substring(1);
        return rgba(Integer.parseUnsignedInt(hexColor, 16), opacity);
    }

    public static int rgba(int rgb, double opacity) {
        int alpha = opacity > 0 ? Math.max(1, (int) (opacity * 255) & 0xFF) : 0;
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    public static int rgb(String hexColor, double opacity) {
        // https://learn.microsoft.com/en-us/windows/win32/api/wingdi/ns-wingdi-blendfunction
        // Note that the APIs use premultiplied alpha, which means that the red, green
        // and blue channel values in the bitmap must be premultiplied with the alpha channel value.
        if (hexColor.startsWith("#"))
            hexColor = hexColor.substring(1);
        int colorInt = Integer.parseUnsignedInt(hexColor, 16);
        int red = (int) (((colorInt >> 16) & 0xFF) * opacity);
        int green = (int) (((colorInt >> 8) & 0xFF) * opacity);
        int blue = (int) ((colorInt & 0xFF) * opacity);
        return (red << 16) | (green << 8) | blue;
    }

    public static int alphaMultiplied(int color, double opacity) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        return ((int) Math.round(red * opacity) << 16) | ((int) Math.round(green * opacity) << 8) |
               (int) Math.round(blue * opacity);
    }

    // base is background, over is foreground.
    public static int blend(int base, int over, double overOpacity) {
        int red1 = (base >> 16) & 0xFF;
        int green1 = (base >> 8) & 0xFF;
        int blue1 = base & 0xFF;
        int red2 = (over >> 16) & 0xFF;
        int green2 = (over >> 8) & 0xFF;
        int blue2 = over & 0xFF;
        int blendedRed = (int) Math.round((red2 * overOpacity) + (red1 * (1 - overOpacity)));
        int blendedGreen = (int) Math.round((green2 * overOpacity) + (green1 * (1 - overOpacity)));
        int blendedBlue = (int) Math.round((blue2 * overOpacity) + (blue1 * (1 - overOpacity)));
        return (blendedRed << 16) | (blendedGreen << 8) | blendedBlue;
    }

    public static String blendOverWhite(String hexColor, double opacity) {
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
}

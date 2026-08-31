package mousemaster;

import java.util.ArrayList;
import java.util.List;

/**
 * hint.box-color=#000000
 * hint.box-color=across-screen left-to-right #FF0000 #FFA500 #800080
 */
public record HintGradientColor(List<String> hexColors, HintGradientDirection direction,
                                HintGradientArea area, HintGradientStep step) {

    /** Qt's stops and the sampled colors both come from these, so the two agree. */
    public static final int rampSteps = 256;

    /** What an across-hint sweep runs over, every shape it fills being mapped onto it. */
    public static final Rectangle unitArea = new Rectangle(0, 0, 1, 1);

    public static HintGradientColor parse(String value) {
        List<String> hexColors = new ArrayList<>();
        HintGradientDirection direction = HintGradientDirection.TOP_TO_BOTTOM;
        HintGradientArea area = HintGradientArea.AREA;
        HintGradientStep step = HintGradientStep.HINT;
        String directionToken = null, areaToken = null, stepToken = null;
        for (String token : value.split("\\s+")) {
            // A direction reads X-to-Y and the other keywords name their own axis, so a token
            // says which one it is on and only one of each may be given.
            if (token.contains("-to-")) {
                directionToken = once(directionToken, token, value);
                direction = switch (token) {
                    case "top-to-bottom" -> HintGradientDirection.TOP_TO_BOTTOM;
                    case "bottom-to-top" -> HintGradientDirection.BOTTOM_TO_TOP;
                    case "left-to-right" -> HintGradientDirection.LEFT_TO_RIGHT;
                    case "right-to-left" -> HintGradientDirection.RIGHT_TO_LEFT;
                    case "top-left-to-bottom-right" ->
                            HintGradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT;
                    case "bottom-right-to-top-left" ->
                            HintGradientDirection.BOTTOM_RIGHT_TO_TOP_LEFT;
                    case "top-right-to-bottom-left" ->
                            HintGradientDirection.TOP_RIGHT_TO_BOTTOM_LEFT;
                    case "bottom-left-to-top-right" ->
                            HintGradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT;
                    case "center-to-corner" -> HintGradientDirection.CENTER_TO_CORNER;
                    case "corner-to-center" -> HintGradientDirection.CORNER_TO_CENTER;
                    case "center-to-edge" -> HintGradientDirection.CENTER_TO_EDGE;
                    case "edge-to-center" -> HintGradientDirection.EDGE_TO_CENTER;
                    default -> throw invalid(token);
                };
            }
            else if (token.startsWith("across-")) {
                areaToken = once(areaToken, token, value);
                area = switch (token) {
                    case "across-hint" -> HintGradientArea.HINT;
                    case "across-subgrid" -> HintGradientArea.SUBGRID;
                    case "across-all-hints" -> HintGradientArea.ALL_HINTS;
                    case "across-area" -> HintGradientArea.AREA;
                    case "across-screen" -> HintGradientArea.SCREEN;
                    case "across-all-screens" -> HintGradientArea.ALL_SCREENS;
                    default -> throw invalid(token);
                };
            }
            else if (token.startsWith("per-")) {
                stepToken = once(stepToken, token, value);
                step = switch (token) {
                    case "per-pixel" -> HintGradientStep.PIXEL;
                    case "per-hint" -> HintGradientStep.HINT;
                    case "per-subgrid" -> HintGradientStep.SUBGRID;
                    default -> throw invalid(token);
                };
            }
            else if (token.matches("^#?([a-fA-F0-9]{6})$"))
                hexColors.add(token);
            else
                throw invalid(token);
        }
        if (hexColors.isEmpty())
            throw new IllegalArgumentException("Invalid color " + value + ": no color given");
        if (step == HintGradientStep.SUBGRID && (area == HintGradientArea.HINT ||
                                                 area == HintGradientArea.SUBGRID))
            throw new IllegalArgumentException("Invalid color " + value +
                                               ": per-subgrid needs an area wider than a subgrid");
        return new HintGradientColor(List.copyOf(hexColors), direction, area, step);
    }

    private static String once(String given, String token, String value) {
        if (given != null)
            throw new IllegalArgumentException("Invalid color " + value + ": " + given + " and " +
                                               token + " cannot both be given");
        return token;
    }

    private static IllegalArgumentException invalid(String token) {
        return new IllegalArgumentException("Invalid color " + token +
                                           ": a color should be in the #FFFFFF format, or be an" +
                                           " across- or per- keyword or a direction");
    }

    public boolean gradient() {
        return hexColors.size() > 1;
    }

    public String hexColor() {
        return hexColors.getFirst();
    }

    public double sweepPosition(Rectangle area, double x, double y) {
        return direction.sweepPosition(area, x, y);
    }

    public int rgbAt(Rectangle area, double x, double y) {
        return rgbAt(sweepPosition(area, x, y));
    }

    public int rgbAt(double t) {
        if (!gradient())
            return Color.rgb(hexColor());
        double scaled = Math.clamp(t, 0, 1) * (hexColors.size() - 1);
        int index = Math.min((int) scaled, hexColors.size() - 2);
        double[] from = oklab(Color.rgb(hexColors.get(index)));
        double[] to = oklab(Color.rgb(hexColors.get(index + 1)));
        double segmentPosition = scaled - index;
        double[] mixed = new double[3];
        for (int i = 0; i < 3; i++)
            mixed[i] = from[i] + (to[i] - from[i]) * segmentPosition;
        return rgb(mixed);
    }

    private static double[] oklab(int rgb) {
        double red = linear(((rgb >> 16) & 0xFF) / 255d);
        double green = linear(((rgb >> 8) & 0xFF) / 255d);
        double blue = linear((rgb & 0xFF) / 255d);
        double longWave = Math.cbrt(
                0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue);
        double mediumWave = Math.cbrt(
                0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue);
        double shortWave = Math.cbrt(
                0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue);
        return new double[] {
                0.2104542553 * longWave + 0.7936177850 * mediumWave - 0.0040720468 * shortWave,
                1.9779984951 * longWave - 2.4285922050 * mediumWave + 0.4505937099 * shortWave,
                0.0259040371 * longWave + 0.7827717662 * mediumWave - 0.8086757660 * shortWave};
    }

    private static int rgb(double[] oklab) {
        double longWave = oklab[0] + 0.3963377774 * oklab[1] + 0.2158037573 * oklab[2];
        double mediumWave = oklab[0] - 0.1055613458 * oklab[1] - 0.0638541728 * oklab[2];
        double shortWave = oklab[0] - 0.0894841775 * oklab[1] - 1.2914855480 * oklab[2];
        longWave = longWave * longWave * longWave;
        mediumWave = mediumWave * mediumWave * mediumWave;
        shortWave = shortWave * shortWave * shortWave;
        return channel(4.0767416621 * longWave - 3.3077115913 * mediumWave +
                       0.2309699292 * shortWave) << 16 |
               channel(-1.2684380046 * longWave + 2.6097574011 * mediumWave -
                       0.3413193965 * shortWave) << 8 |
               channel(-0.0041960863 * longWave - 0.7034186147 * mediumWave +
                       1.7076147010 * shortWave);
    }

    private static double linear(double channel) {
        return channel <= 0.04045 ? channel / 12.92 :
                Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static int channel(double linear) {
        double srgb = linear <= 0.0031308 ? 12.92 * linear :
                1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
        return (int) Math.round(Math.clamp(srgb, 0, 1) * 255);
    }

    /** The two points a sweep runs between, as fractions of the area. Qt is given the same two. */
    public enum HintGradientDirection {

        TOP_TO_BOTTOM(0, 0, 0, 1, HintGradientShape.STRAIGHT, false),
        BOTTOM_TO_TOP(0, 1, 0, 0, HintGradientShape.STRAIGHT, false),
        LEFT_TO_RIGHT(0, 0, 1, 0, HintGradientShape.STRAIGHT, false),
        RIGHT_TO_LEFT(1, 0, 0, 0, HintGradientShape.STRAIGHT, false),
        TOP_LEFT_TO_BOTTOM_RIGHT(0, 0, 1, 1, HintGradientShape.STRAIGHT, false),
        BOTTOM_RIGHT_TO_TOP_LEFT(1, 1, 0, 0, HintGradientShape.STRAIGHT, false),
        TOP_RIGHT_TO_BOTTOM_LEFT(1, 0, 0, 1, HintGradientShape.STRAIGHT, false),
        BOTTOM_LEFT_TO_TOP_RIGHT(0, 1, 1, 0, HintGradientShape.STRAIGHT, false),
        CENTER_TO_CORNER(0.5, 0.5, 1, 1, HintGradientShape.CIRCLE, false),
        CORNER_TO_CENTER(0.5, 0.5, 1, 1, HintGradientShape.CIRCLE, true),
        CENTER_TO_EDGE(0.5, 0.5, 1, 1, HintGradientShape.ELLIPSE, false),
        EDGE_TO_CENTER(0.5, 0.5, 1, 1, HintGradientShape.ELLIPSE, true);

        private final double startXPercent, startYPercent, endXPercent, endYPercent;
        private final HintGradientShape shape;
        private final boolean inverted;

        HintGradientDirection(double startXPercent, double startYPercent, double endXPercent,
                              double endYPercent, HintGradientShape shape, boolean inverted) {
            this.startXPercent = startXPercent;
            this.startYPercent = startYPercent;
            this.endXPercent = endXPercent;
            this.endYPercent = endYPercent;
            this.shape = shape;
            this.inverted = inverted;
        }

        public HintGradientShape shape() {
            return shape;
        }

        public boolean inverted() {
            return inverted;
        }

        public Point start(Rectangle area) {
            return point(area, startXPercent, startYPercent);
        }

        public Point end(Rectangle area) {
            return point(area, endXPercent, endYPercent);
        }

        /** 0 at the sweep's first color, 1 at its last. */
        public double sweepPosition(Rectangle area, double x, double y) {
            Point start = start(area);
            Point end = end(area);
            double dx = end.x() - start.x();
            double dy = end.y() - start.y();
            double position = switch (shape) {
                case STRAIGHT -> divide(dx * (x - start.x()) + dy * (y - start.y()),
                        dx * dx + dy * dy);
                case CIRCLE -> divide(Math.hypot(x - start.x(), y - start.y()),
                        Math.hypot(dx, dy));
                case ELLIPSE -> Math.hypot(divide(x - start.x(), dx), divide(y - start.y(), dy));
            };
            return inverted ? 1 - position : position;
        }

        /** An area flat on an axis leaves that axis nothing to say. */
        private static double divide(double distance, double extent) {
            return extent == 0 ? 0 : distance / extent;
        }

        private static Point point(Rectangle area, double xPercent, double yPercent) {
            return new Point(area.x() + xPercent * area.width(),
                    area.y() + yPercent * area.height());
        }

    }

    /** A circle reaches its last color at the corners, an ellipse at every edge. */
    public enum HintGradientShape {
        STRAIGHT, CIRCLE, ELLIPSE
    }

    /** What one full sweep covers. */
    public enum HintGradientArea {
        HINT, SUBGRID, ALL_HINTS, AREA, SCREEN, ALL_SCREENS
    }

    /** The unit that shares one color. */
    public enum HintGradientStep {
        PIXEL, HINT, SUBGRID
    }

}

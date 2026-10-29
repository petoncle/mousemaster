package mousemaster;

import java.util.List;
import java.util.Set;

public sealed interface IndicatorPosition {

    enum PointIndicatorPosition implements IndicatorPosition {
        MOUSE,
        MOUSE_TOP_LEFT,
        MOUSE_TOP_RIGHT,
        MOUSE_BOTTOM_LEFT,
        MOUSE_BOTTOM_RIGHT,
        SCREEN_CENTER,
        TARGET,
        LAST_SELECTED_HINT
    }

    /** The positions saved in the history, in the order they were saved. */
    record PositionHistoryIndicatorPosition(String positionHistoryName)
            implements IndicatorPosition {
    }

    /** A layer runs through several positions, or through the positions of a history. */
    static boolean runThrough(List<IndicatorPosition> positions) {
        return positions.size() > 1 ||
               positions.getFirst() instanceof PositionHistoryIndicatorPosition;
    }

    static IndicatorPosition fromString(String value, Set<String> positionHistoryNames) {
        if (positionHistoryNames.contains(value))
            return new PositionHistoryIndicatorPosition(value);
        return switch (value) {
            case "mouse" -> PointIndicatorPosition.MOUSE;
            case "mouse-top-left" -> PointIndicatorPosition.MOUSE_TOP_LEFT;
            case "mouse-top-right" -> PointIndicatorPosition.MOUSE_TOP_RIGHT;
            case "mouse-bottom-left" -> PointIndicatorPosition.MOUSE_BOTTOM_LEFT;
            case "mouse-bottom-right" -> PointIndicatorPosition.MOUSE_BOTTOM_RIGHT;
            case "screen-center" -> PointIndicatorPosition.SCREEN_CENTER;
            case "target" -> PointIndicatorPosition.TARGET;
            case "last-selected-hint" -> PointIndicatorPosition.LAST_SELECTED_HINT;
            case "center" -> throw new IllegalArgumentException(
                    "Invalid indicator position: center has been renamed mouse");
            case "top-left", "top-right", "bottom-left", "bottom-right" ->
                    throw new IllegalArgumentException(
                            "Invalid indicator position: " + value + " has been renamed mouse-" +
                            value);
            default -> throw new IllegalArgumentException(
                    "Invalid indicator position: " + value +
                    ", must be 'mouse', 'mouse-top-left', 'mouse-top-right', 'mouse-bottom-left', 'mouse-bottom-right', 'screen-center', 'target', 'last-selected-hint' or a position history");
        };
    }
}

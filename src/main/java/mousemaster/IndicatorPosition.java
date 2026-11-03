package mousemaster;

import java.util.Set;

public sealed interface IndicatorPosition {

    enum PointIndicatorPosition implements IndicatorPosition {
        MOUSE,
        MOUSE_TOP_LEFT,
        MOUSE_TOP_RIGHT,
        MOUSE_BOTTOM_LEFT,
        MOUSE_BOTTOM_RIGHT,
        SCREEN_CENTER,
        ACTIVE_SCREEN_TOP_LEFT,
        ACTIVE_SCREEN_TOP_RIGHT,
        ACTIVE_SCREEN_BOTTOM_LEFT,
        ACTIVE_SCREEN_BOTTOM_RIGHT,
        TARGET,
        LAST_SELECTED_HINT
    }

    /** The positions saved in the history, in the order they were saved. */
    record PositionHistoryIndicatorPosition(String positionHistoryName)
            implements IndicatorPosition {
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
            case "active-screen-top-left" -> PointIndicatorPosition.ACTIVE_SCREEN_TOP_LEFT;
            case "active-screen-top-right" -> PointIndicatorPosition.ACTIVE_SCREEN_TOP_RIGHT;
            case "active-screen-bottom-left" -> PointIndicatorPosition.ACTIVE_SCREEN_BOTTOM_LEFT;
            case "active-screen-bottom-right" -> PointIndicatorPosition.ACTIVE_SCREEN_BOTTOM_RIGHT;
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
                    ", must be 'mouse', 'mouse-top-left', 'mouse-top-right', 'mouse-bottom-left', 'mouse-bottom-right', 'screen-center', 'active-screen-top-left', 'active-screen-top-right', 'active-screen-bottom-left', 'active-screen-bottom-right', 'target', 'last-selected-hint' or a position history");
        };
    }
}

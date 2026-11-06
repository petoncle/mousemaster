package mousemaster;

import org.slf4j.LoggerFactory;

import java.util.Set;

public sealed interface IndicatorPosition {

    enum PointIndicatorPosition implements IndicatorPosition {
        MOUSE,
        MOUSE_TOP_LEFT,
        MOUSE_TOP_RIGHT,
        MOUSE_BOTTOM_LEFT,
        MOUSE_BOTTOM_RIGHT,
        ACTIVE_SCREEN_CENTER,
        ACTIVE_SCREEN_TOP_LEFT,
        ACTIVE_SCREEN_TOP_RIGHT,
        ACTIVE_SCREEN_BOTTOM_LEFT,
        ACTIVE_SCREEN_BOTTOM_RIGHT,
        TARGET,
        LAST_SELECTED_HINT,
        ACTIVE_WINDOW_CENTER,
        LAST_OVERLAY_AREA_CENTER
    }

    /** The positions saved in the history, in the order they were saved. */
    record PositionHistoryIndicatorPosition(String positionHistoryName)
            implements IndicatorPosition {
    }

    static IndicatorPosition fromString(String propertyKey, String value,
                                        Set<String> positionHistoryNames) {
        if (positionHistoryNames.contains(value))
            return new PositionHistoryIndicatorPosition(value);
        return switch (value) {
            case "mouse" -> PointIndicatorPosition.MOUSE;
            case "mouse-top-left" -> PointIndicatorPosition.MOUSE_TOP_LEFT;
            case "mouse-top-right" -> PointIndicatorPosition.MOUSE_TOP_RIGHT;
            case "mouse-bottom-left" -> PointIndicatorPosition.MOUSE_BOTTOM_LEFT;
            case "mouse-bottom-right" -> PointIndicatorPosition.MOUSE_BOTTOM_RIGHT;
            case "active-screen-center" -> PointIndicatorPosition.ACTIVE_SCREEN_CENTER;
            case "active-screen-top-left" -> PointIndicatorPosition.ACTIVE_SCREEN_TOP_LEFT;
            case "active-screen-top-right" -> PointIndicatorPosition.ACTIVE_SCREEN_TOP_RIGHT;
            case "active-screen-bottom-left" -> PointIndicatorPosition.ACTIVE_SCREEN_BOTTOM_LEFT;
            case "active-screen-bottom-right" -> PointIndicatorPosition.ACTIVE_SCREEN_BOTTOM_RIGHT;
            case "screen-center" -> {
                LoggerFactory.getLogger(IndicatorPosition.class).warn(
                        propertyKey + "=screen-center has been deprecated: use " + propertyKey +
                        "=active-screen-center instead");
                yield PointIndicatorPosition.ACTIVE_SCREEN_CENTER;
            }
            case "target" -> PointIndicatorPosition.TARGET;
            case "last-selected-hint" -> PointIndicatorPosition.LAST_SELECTED_HINT;
            case "active-window-center" -> PointIndicatorPosition.ACTIVE_WINDOW_CENTER;
            case "last-overlay-area-center" -> PointIndicatorPosition.LAST_OVERLAY_AREA_CENTER;
            case "center" -> throw new IllegalArgumentException(
                    "Invalid indicator position: center has been renamed mouse");
            case "top-left", "top-right", "bottom-left", "bottom-right" ->
                    throw new IllegalArgumentException(
                            "Invalid indicator position: " + value + " has been renamed mouse-" +
                            value);
            default -> throw new IllegalArgumentException(
                    "Invalid indicator position: " + value +
                    ", must be 'mouse', 'mouse-top-left', 'mouse-top-right', 'mouse-bottom-left', 'mouse-bottom-right', 'active-screen-center', 'active-screen-top-left', 'active-screen-top-right', 'active-screen-bottom-left', 'active-screen-bottom-right', 'target', 'last-selected-hint', 'active-window-center', 'last-overlay-area-center' or a position history");
        };
    }
}

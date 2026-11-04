package mousemaster;

import mousemaster.renderer.IndicatorRenderer;
import mousemaster.renderer.IndicatorRenderer.WindowLayerGroup;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindowLayerGroupTest {

    private static ModeMap modeMap(String... lines) {
        return ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null)).modeMap();
    }

    private static IndicatorConfiguration indicator(String... lines) {
        return modeMap(lines).get(Mode.IDLE_MODE_NAME).indicator();
    }

    private static String windowLayerGroups(IndicatorConfiguration indicator,
                                            Map<IndicatorLayerCopyKey, IndicatorConfiguration> indicatorByCopyKey,
                                            int overlayZ) {
        List<String> below = new ArrayList<>();
        List<String> above = new ArrayList<>();
        for (WindowLayerGroup windowLayerGroup : IndicatorRenderer.windowLayerGroups(indicator,
                indicatorByCopyKey, List.of(), Set.of(), overlayZ)) {
            List<String> windowImages = new ArrayList<>();
            for (IndicatorLayerCopyKey copyKey : windowLayerGroup.copyKeys())
                windowImages.add("copy of " + copyKey.layerName());
            for (IndicatorConfiguration windowIndicator : windowLayerGroup.indicators())
                windowImages.add(String.join("+", windowIndicator.layerByName().keySet()));
            (windowLayerGroup.belowOverlay() ? below : above).add(
                    (windowLayerGroup.followMouse() ? "*" : "") + String.join(",", windowImages));
        }
        return String.join(" ", below) + " | " + String.join(" ", above);
    }

    @Test
    void aLayerBelowTheZOfTheOverlayIsDrawnBelowTheOverlay() {
        IndicatorConfiguration indicator = indicator(
                "idle-mode.a-indicator.follow=false", "idle-mode.a-indicator.z=-1",
                "idle-mode.b-indicator.follow=false");
        assertEquals("a-indicator | b-indicator", windowLayerGroups(indicator, Map.of(), 0));
    }

    @Test
    void theZOfTheOverlayMovesTheOverlay() {
        IndicatorConfiguration indicator = indicator(
                "idle-mode.a-indicator.follow=false", "idle-mode.a-indicator.z=-1",
                "idle-mode.b-indicator.follow=false");
        assertEquals("a-indicator+b-indicator | ", windowLayerGroups(indicator, Map.of(), 1));
        assertEquals(" | a-indicator+b-indicator", windowLayerGroups(indicator, Map.of(), -1));
    }

    @Test
    void aLayerThatFollowsTheMouseIsOrderedByItsZLikeTheOthers() {
        IndicatorConfiguration indicator = indicator(
                "idle-mode.mouse-indicator.z=1",
                "idle-mode.a-indicator.follow=false",
                "idle-mode.below-indicator.z=-1");
        assertEquals("*below-indicator | a-indicator *mouse-indicator",
                windowLayerGroups(indicator, Map.of(), 0));
    }

    @Test
    void atEqualZALayerThatDoesNotFollowTheMouseIsAboveOneThatDoes() {
        IndicatorConfiguration indicator = indicator(
                "idle-mode.a-indicator.follow=false", "idle-mode.mouse-indicator.z=0");
        assertEquals(" | *mouse-indicator a-indicator", windowLayerGroups(indicator, Map.of(), 0));
    }

    @Test
    void aCopyIsDrawnBelowTheLayersOfItsWindow() {
        IndicatorConfiguration indicator = indicator(
                "idle-mode.a-indicator.follow=false", "idle-mode.a-indicator.z=-1",
                "idle-mode.b-indicator.follow=false", "idle-mode.b-indicator.z=-1");
        assertEquals("copy of a-indicator,a-indicator+b-indicator | ",
                windowLayerGroups(indicator, indicatorByCopyKey(indicator, "a-indicator"), 0));
    }

    @Test
    void aCopyWithAHigherZIsDrawnAboveALayer() {
        IndicatorConfiguration indicator = indicator(
                "idle-mode.a-indicator.follow=false", "idle-mode.a-indicator.z=-1",
                "idle-mode.b-indicator.follow=false", "idle-mode.b-indicator.z=-2");
        assertEquals("b-indicator copy of a-indicator,a-indicator | ",
                windowLayerGroups(indicator, indicatorByCopyKey(indicator, "a-indicator"), 0));
    }

    private static Map<IndicatorLayerCopyKey, IndicatorConfiguration> indicatorByCopyKey(
            IndicatorConfiguration indicator, String layerName) {
        return Map.of(new IndicatorLayerCopyKey(layerName, 0), new IndicatorConfiguration(false,
                false, Map.of(layerName, indicator.layerByName().get(layerName))));
    }

    @Test
    void theZOfTheOverlayIsZeroByDefaultAndExtendedLikeTheOtherOverlayProperties() {
        ModeMap modeMap = modeMap("idle-mode.to.hint-mode=+f1", "hint-mode.to.idle-mode=+f2",
                "_base-mode.overlay.z=2", "hint-mode=_base-mode");
        assertEquals(0, modeMap.get(Mode.IDLE_MODE_NAME).hintMesh().z());
        assertEquals(2, modeMap.get("hint-mode").hintMesh().z());
    }

}

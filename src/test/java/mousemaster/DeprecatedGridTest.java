package mousemaster;

import mousemaster.ScreenFilter.AnyScreenFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The deprecated grid properties are rewritten as hint properties. */
class DeprecatedGridTest {

    private static HintMeshConfiguration hint(String modeName, String... lines) {
        return ConfigurationParser.parse(List.of(lines),
                                          KeyboardLayout.keyboardLayout("00000409", null))
                                  .modeMap().get(modeName).hintMesh();
    }

    private static HintGridLayout layout(HintMeshConfiguration hint) {
        return ((HintMeshType.GridHintMesh) hint.type())
                .layout(ScreenFilter.of(new Screen(new Rectangle(0, 0, 1920, 1080), 96, 1)));
    }

    private static HintMeshStyle style(HintMeshConfiguration hint) {
        return hint.styleByFilter().get(AnyScreenFilter.ANY_SCREEN_FILTER);
    }

    @Test
    void aGridModeKeepsTheGridDefaults() {
        HintMeshConfiguration hint = hint("grid-mode", "idle-mode.to.grid-mode=+g",
                "grid-mode.grid.area=active-window");
        assertTrue(hint.enabled());
        assertFalse(hint.visible());
        assertEquals(HintMouseMovement.NO_MOVEMENT, hint.mouseMovement());
        assertEquals(HintGridAreaSizeSource.ACTIVE_WINDOW,
                ((HintMeshType.GridHintMesh) hint.type()).area().size().source());
        assertInstanceOf(HintCellSizing.FitToArea.class, layout(hint).cellSizing());
        assertEquals(2, layout(hint).maxRowCount());
        assertEquals(2, layout(hint).maxColumnCount());
        assertEquals(List.of(), hint.keysByFilter().get(AnyScreenFilter.ANY_SCREEN_FILTER)
                                    .selectionKeys());
        assertEquals(GradientColor.parse("#FF0000"), style(hint).boxColor());
        assertEquals(0.1, style(hint).boxOpacity());
        assertEquals(Color.parse("#FF0000"), style(hint).boxBorderColor());
        assertEquals(1, style(hint).boxBorderOpacity());
    }

    @Test
    void theGridPropertiesBecomeHintProperties() {
        HintMeshConfiguration hint = hint("grid-mode", "idle-mode.to.grid-mode=+g",
                "grid-mode.grid.row-count=1",
                "grid-mode.grid.column-count=3",
                "grid-mode.grid.line-visible=true",
                "grid-mode.grid.line-color=#00FF00",
                "grid-mode.grid.background-opacity=0",
                "grid-mode.grid.synchronization=mouse-follows-grid-center");
        assertTrue(hint.visible());
        assertEquals(HintMouseMovement.MOUSE_FOLLOWS_HINT_GRID_CENTER, hint.mouseMovement());
        assertEquals(1, layout(hint).maxRowCount());
        assertEquals(3, layout(hint).maxColumnCount());
        assertEquals(Color.parse("#00FF00"), style(hint).boxBorderColor());
        assertEquals(0, style(hint).boxOpacity());
    }

    @Test
    void aGridCenteredOnTheMouseIsAHintAreaCenteredOnTheMouse() {
        assertEquals(HintGridAreaCenter.MOUSE, ((HintMeshType.GridHintMesh) hint("grid-mode",
                "idle-mode.to.grid-mode=+g",
                "grid-mode.grid.synchronization=grid-center-follows-mouse").type())
                .area().center());
    }

    @Test
    void aGridReferenceIsAHintReference() {
        HintMeshConfiguration hint = hint("other-grid-mode", "idle-mode.to.grid-mode=+g",
                "grid-mode.to.other-grid-mode=+o",
                "grid-mode.grid.row-count=1",
                "other-grid-mode.grid=grid-mode.grid");
        assertEquals(1, layout(hint).maxRowCount());
        assertEquals(2, layout(hint).maxColumnCount());
    }

    @Test
    void aGridModeExtendingAModeWithoutAGridKeepsTheGridDefaults() {
        HintMeshConfiguration hint = hint("grid-mode", "idle-mode.to.grid-mode=+g",
                "_base-mode.indicator.size=10",
                "grid-mode=_base-mode",
                "grid-mode.grid.line-visible=true");
        assertInstanceOf(HintCellSizing.FitToArea.class, layout(hint).cellSizing());
        assertEquals(2, layout(hint).maxRowCount());
    }

    @Test
    void aModeTakesTheGridOfTheModeItExtends() {
        HintMeshConfiguration hint = hint("grid-mode", "idle-mode.to.grid-mode=+g",
                "_base-mode.grid.line-visible=true",
                "grid-mode=_base-mode");
        assertTrue(hint.enabled());
        assertTrue(hint.visible());
        assertInstanceOf(HintCellSizing.FitToArea.class, layout(hint).cellSizing());
    }

    @Test
    void aGridCommandWithoutAHintRunsOnTheDefaultGrid() {
        HintMeshConfiguration hint = hint("normal-mode", "idle-mode.to.normal-mode=+n",
                "normal-mode.move-to-grid-center=+m");
        assertTrue(hint.enabled());
        assertEquals(2, layout(hint).maxRowCount());
    }

    @Test
    void aModeWithAHintOfItsOwnKeepsTheHintDefaults() {
        HintMeshConfiguration hint = hint("hint-mode", "idle-mode.to.hint-mode=+h",
                "hint-mode.hint.selection-keys=a b c d",
                "hint-mode.snap.up=+u");
        assertTrue(hint.visible());
        assertInstanceOf(HintCellSizing.FixedCellSize.class, layout(hint).cellSizing());
    }

    @Test
    void aKeyAliasNamedGridIsNotAGridProperty() {
        assertEquals(2, layout(hint("grid-mode", "key-alias.grid=g",
                "idle-mode.to.grid-mode=+grid",
                "grid-mode.snap.up=+uparrow")).maxRowCount());
    }

}

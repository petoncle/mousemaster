package mousemaster;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class UiHintAreaTest {

    private static Configuration parse(String properties) throws IOException {
        List<String> lines = PropertiesReader.readPropertiesFile(
                new BufferedReader(new StringReader(properties)));
        return ConfigurationParser.parse(lines,
                KeyboardLayout.keyboardLayoutByShortName.get("uk-qwerty"));
    }

    private static final String uiMode = """
            idle-mode.to.ui-hint-mode=+u
            ui-hint-mode.to.idle-mode=+esc
            ui-hint-mode.hint.selection-keys=a b c d
            ui-hint-mode.hint.type=ui
            """;

    private static UiHintArea uiHintArea(Configuration configuration) {
        HintMeshType type = configuration.modeMap()
                                         .get("ui-hint-mode")
                                         .hintMesh()
                                         .type();
        return ((HintMeshType.UiHintMesh) type).area();
    }

    /** Same default as a grid hint mesh: the area does not depend on the hint type. */
    @Test
    void uiHintAreaDefaultsToActiveScreen() throws IOException {
        assertEquals(UiHintArea.ACTIVE_SCREEN, uiHintArea(parse(uiMode)));
    }

    @Test
    void uiHintAreaCanBeAWindowOrEveryScreen() throws IOException {
        assertEquals(UiHintArea.ACTIVE_WINDOW, uiHintArea(
                parse(uiMode + "ui-hint-mode.hint.ui-area=active-window\n")));
        assertEquals(UiHintArea.ALL_SCREENS, uiHintArea(
                parse(uiMode + "ui-hint-mode.hint.ui-area=all-screens\n")));
    }

    @Test
    void uiHintAreaCannotBeTheLastSelectedHintCell() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> parse(
                        uiMode + "ui-hint-mode.hint.ui-area=last-selected-hint-cell\n"));
        assertTrue(exception.getMessage().contains("hint.ui-area"),
                exception.getMessage());
    }

    @Test
    void gridAreaDoesNotApplyToUiHints() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> parse(
                        uiMode + "ui-hint-mode.hint.grid-area=active-window\n"));
        assertTrue(exception.getMessage().contains("hint.ui-area"),
                exception.getMessage());
    }

    @Test
    void uiAreaDoesNotApplyToGridHints() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> parse("""
                        idle-mode.to.hint-mode=+h
                        hint-mode.to.idle-mode=+esc
                        hint-mode.hint.selection-keys=a b c d
                        hint-mode.hint.type=grid
                        hint-mode.hint.ui-area=active-window
                        """));
        assertTrue(exception.getMessage().contains("hint.ui-area"),
                exception.getMessage());
    }

    /** The area is a component of the ui hint mesh, so it is mutated in place. */
    @Test
    void uiHintAreaFollowsAVariablePrecondition() {
        Configuration configuration = ConfigurationParser.parse(List.of(
                        "idle-mode.hint.type=ui",
                        "idle-mode.hint.selection-keys=a b c d",
                        "idle-mode.hint.ui-area=active-window | _{isidling} -> all-screens"),
                KeyboardLayout.keyboardLayout("00000409", null));
        ComboWatcher comboWatcher =
                new ComboWatcher(null, null, () -> new App("test.exe"), null, Instant::now,
                        Set.of(), Set.of(), new KeyRedactor(KeyRedaction.NONE),
                        configuration.modeMap(),
                        configuration.initiallySetVariables(), configuration.virtualKeys(),
                        configuration.initiallyPressedVirtualKeys());
        comboWatcher.setModeListeners(List.of(new ModeListener() {
            @Override
            public void modeChanged(Mode newMode) {
            }

            @Override
            public void modeTimedOut() {
            }
        }));
        comboWatcher.modeChanged(configuration.modeMap().get(Mode.IDLE_MODE_NAME));
        comboWatcher.update(0.01);
        assertEquals(UiHintArea.ACTIVE_WINDOW, mutatedUiHintArea(comboWatcher));
        comboWatcher.setVirtualKeyPressed(BuiltInVirtualKey.IS_IDLING, true);
        comboWatcher.update(0.01);
        assertEquals(UiHintArea.ALL_SCREENS, mutatedUiHintArea(comboWatcher));
    }

    private static UiHintArea mutatedUiHintArea(ComboWatcher comboWatcher) {
        return ((HintMeshType.UiHintMesh) comboWatcher.getMutatedMode()
                                                      .hintMesh()
                                                      .type()).area();
    }

    /**
     * A mode can still carry the other type's area mutation, by inheriting it from a mode of
     * that type. The path lands on a component of another type, and does nothing.
     */
    @Test
    void anAreaMutationOfTheOtherTypeDoesNothing() throws IOException {
        Mode uiMode = parse(UiHintAreaTest.uiMode).modeMap().get("ui-hint-mode");
        Mode mutatedUiMode = uiMode.mutate(
                new ModePropertyPath(List.of("hintMesh", "type", "area", "size", "source")),
                HintGridAreaSizeSource.ALL_SCREENS);
        assertEquals(UiHintArea.ACTIVE_SCREEN,
                ((HintMeshType.UiHintMesh) mutatedUiMode.hintMesh().type()).area());
        Mode gridMode = parse("""
                idle-mode.to.hint-mode=+h
                hint-mode.to.idle-mode=+esc
                hint-mode.hint.selection-keys=a b c d
                hint-mode.hint.type=grid
                """).modeMap().get("hint-mode");
        Mode mutatedGridMode = gridMode.mutate(
                new ModePropertyPath(List.of("hintMesh", "type", "area")),
                UiHintArea.ALL_SCREENS);
        assertEquals(gridMode.hintMesh().type(), mutatedGridMode.hintMesh().type());
    }

    @Test
    void gridHintAreaStillDefaultsToActiveScreen() throws IOException {
        Configuration configuration = parse("""
                idle-mode.to.hint-mode=+h
                hint-mode.to.idle-mode=+esc
                hint-mode.hint.selection-keys=a b c d
                hint-mode.hint.type=grid
                """);
        HintMeshType type =
                configuration.modeMap().get("hint-mode").hintMesh().type();
        assertEquals(HintGridAreaSizeSource.ACTIVE_SCREEN,
                ((HintMeshType.HintGrid) type).area().size().source());
    }

}

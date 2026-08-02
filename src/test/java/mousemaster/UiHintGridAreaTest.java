package mousemaster;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class UiHintGridAreaTest {

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

    private static HintGridAreaSizeSource uiHintAreaSource(Configuration configuration) {
        HintMeshType type = configuration.modeMap()
                                         .get("ui-hint-mode")
                                         .hintMesh()
                                         .type();
        return ((HintMeshType.UiHintMesh) type).area().size().source();
    }

    /** Same default as a grid hint mesh: the area does not depend on the hint type. */
    @Test
    void uiHintAreaDefaultsToActiveScreen() throws IOException {
        assertEquals(HintGridAreaSizeSource.ACTIVE_SCREEN,
                uiHintAreaSource(parse(uiMode)));
    }

    @Test
    void uiHintAreaCanBeAWindowOrEveryScreen() throws IOException {
        assertEquals(HintGridAreaSizeSource.ACTIVE_WINDOW, uiHintAreaSource(
                parse(uiMode + "ui-hint-mode.hint.grid-area=active-window\n")));
        assertEquals(HintGridAreaSizeSource.ALL_SCREENS, uiHintAreaSource(
                parse(uiMode + "ui-hint-mode.hint.grid-area=all-screens\n")));
    }

    @Test
    void uiHintAreaCannotBeTheLastSelectedHintCell() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> parse(
                        uiMode +
                        "ui-hint-mode.hint.grid-area=last-selected-hint-cell\n"));
        assertTrue(exception.getMessage().contains("hint.type=ui"),
                exception.getMessage());
    }

    /** The area is mutated through the same property path as the grid area. */
    @Test
    void uiHintAreaFollowsAVariablePrecondition() {
        Configuration configuration = ConfigurationParser.parse(List.of(
                        "idle-mode.hint.type=ui",
                        "idle-mode.hint.selection-keys=a b c d",
                        "idle-mode.hint.grid-area=active-window | _{isidling} -> all-screens"),
                KeyboardLayout.keyboardLayout("00000409", null));
        ComboWatcher comboWatcher =
                new ComboWatcher(null, null, () -> new App("test.exe"), Instant::now,
                        Set.of(), Set.of(), false, configuration.modeMap(),
                        configuration.initiallySetVariables());
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
        assertEquals(HintGridAreaSizeSource.ACTIVE_WINDOW,
                mutatedUiHintAreaSource(comboWatcher));
        comboWatcher.setIdling(true);
        assertEquals(HintGridAreaSizeSource.ALL_SCREENS,
                mutatedUiHintAreaSource(comboWatcher));
    }

    private static HintGridAreaSizeSource mutatedUiHintAreaSource(
            ComboWatcher comboWatcher) {
        return ((HintMeshType.UiHintMesh) comboWatcher.getMutatedMode()
                                                      .hintMesh()
                                                      .type()).area().size().source();
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

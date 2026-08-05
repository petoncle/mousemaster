package mousemaster;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PositionHistoryTest {

    private static Configuration parse(String properties) throws IOException {
        List<String> lines = PropertiesReader.readPropertiesFile(
                new BufferedReader(new StringReader(properties)));
        return ConfigurationParser.parse(lines,
                KeyboardLayout.keyboardLayoutByShortName.get("uk-qwerty"));
    }

    private static final String twoHistories = """
            idle-mode.position-history.save-position=+f1
            idle-mode.browser-position-history.save-position=+f2
            idle-mode.browser-position-history.cycle-next=+f3
            idle-mode.to.browser-hint-mode=+m
            browser-hint-mode.hint.selection-keys=a b c d
            """;

    private static final String browserHints =
            "browser-hint-mode.hint.type=browser-position-history\n";

    private static String positionHistoryName(Configuration configuration,
                                              String modeName) {
        return positionHistoryName(configuration.modeMap().get(modeName));
    }

    private static String positionHistoryName(Mode mode) {
        return ((HintMeshType.HintPositionHistory) mode.hintMesh()
                                                       .type()).positionHistoryName();
    }

    private static List<Command> commands(Configuration configuration,
                                          String modeName) {
        List<Command> commands = new ArrayList<>();
        configuration.modeMap()
                     .get(modeName)
                     .comboMap()
                     .commandsByCombo()
                     .values()
                     .forEach(commands::addAll);
        return commands;
    }

    @Test
    void hintsComeFromTheNamedPositionHistory() throws IOException {
        assertEquals("browser-position-history",
                positionHistoryName(parse(twoHistories + browserHints), "browser-hint-mode"));
    }

    @Test
    void commandsNameTheirPositionHistory() throws IOException {
        List<Command> commands = commands(parse(twoHistories + browserHints), "idle-mode");
        assertTrue(commands.contains(
                new Command.SavePosition("position-history")), commands.toString());
        assertTrue(commands.contains(
                new Command.SavePosition("browser-position-history")),
                commands.toString());
        assertTrue(commands.contains(
                new Command.CycleNextPosition("browser-position-history")),
                commands.toString());
    }

    @Test
    void theActiveAppCanDriveTheHintPositionHistory() throws IOException {
        Configuration configuration = parse("""
                app-alias.browserapp=firefox.exe chrome.exe
                idle-mode.position-history.save-position=+f1
                idle-mode.browser-position-history.save-position=+f2
                idle-mode.hint.selection-keys=a b c d
                idle-mode.hint.type=position-history | _{browserapp} -> browser-position-history
                """);
        App[] activeApp = {new App("notepad.exe")};
        ComboWatcher comboWatcher =
                new ComboWatcher(null, null, () -> activeApp[0], Instant::now,
                        Set.of(), Set.of(), false, configuration.modeMap(),
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
        assertEquals("position-history",
                positionHistoryName(comboWatcher.getMutatedMode()));
        activeApp[0] = new App("chrome.exe");
        comboWatcher.update(0.01);
        assertEquals("browser-position-history",
                positionHistoryName(comboWatcher.getMutatedMode()));
    }

    @Test
    void maxSizeAndIsolationArePerPositionHistory() throws IOException {
        Configuration configuration = parse(twoHistories + browserHints + """
                position-history.isolation=none
                browser-position-history.max-size=4
                browser-position-history.isolation=active-app
                """);
        assertEquals(Map.of("position-history",
                        new PositionHistoryConfiguration(16,
                                PositionHistoryIsolation.NONE),
                        "browser-position-history",
                        new PositionHistoryConfiguration(4,
                                PositionHistoryIsolation.ACTIVE_APP)),
                configuration.positionHistoryConfigurationByName());
    }

    private static HintManager hintManager(Configuration configuration,
                                           App[] activeApp) {
        return new HintManager(configuration.positionHistoryConfigurationByName(), null,
                new MouseManager(null, null), null, null, () -> activeApp[0]);
    }

    @Test
    void anActiveAppIsolatedPositionHistoryHasOneListPerApp() throws IOException {
        Configuration configuration = parse(twoHistories + browserHints +
                                            "browser-position-history.isolation=active-app\n");
        App[] activeApp = {new App("chrome.exe")};
        HintManager hintManager = hintManager(configuration, activeApp);
        hintManager.mouseMoved(10, 10);
        hintManager.saveCurrentPosition("browser-position-history");
        activeApp[0] = new App("firefox.exe");
        hintManager.mouseMoved(20, 20);
        hintManager.saveCurrentPosition("browser-position-history");
        assertEquals(List.of(new Point(20, 20)),
                hintManager.positionHistory("browser-position-history").positions());
        activeApp[0] = new App("chrome.exe");
        assertEquals(List.of(new Point(10, 10)),
                hintManager.positionHistory("browser-position-history").positions());
    }

    @Test
    void theDefaultPositionHistoryCanBeActiveAppIsolated() throws IOException {
        Configuration configuration = parse(twoHistories + browserHints +
                                            "position-history.isolation=active-app\n");
        App[] activeApp = {new App("chrome.exe")};
        HintManager hintManager = hintManager(configuration, activeApp);
        hintManager.mouseMoved(10, 10);
        hintManager.saveCurrentPosition("position-history");
        activeApp[0] = new App("firefox.exe");
        assertEquals(List.of(),
                hintManager.positionHistory("position-history").positions());
        activeApp[0] = new App("chrome.exe");
        assertEquals(List.of(new Point(10, 10)),
                hintManager.positionHistory("position-history").positions());
    }

    @Test
    void aPositionHistoryIsSharedByEveryAppByDefault() throws IOException {
        Configuration configuration = parse(twoHistories + browserHints);
        App[] activeApp = {new App("chrome.exe")};
        HintManager hintManager = hintManager(configuration, activeApp);
        hintManager.mouseMoved(10, 10);
        hintManager.saveCurrentPosition("browser-position-history");
        activeApp[0] = new App("firefox.exe");
        hintManager.mouseMoved(20, 20);
        hintManager.saveCurrentPosition("browser-position-history");
        assertEquals(List.of(new Point(10, 10), new Point(20, 20)),
                hintManager.positionHistory("browser-position-history").positions());
    }

    @Test
    void anUnknownIsolationIsRejected() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> parse(
                        twoHistories + browserHints +
                        "browser-position-history.isolation=active-window\n"));
        assertTrue(exception.getMessage().contains("expected one of"),
                exception.getMessage());
    }

    @Test
    void anUnknownPositionHistoryPropertyIsRejected() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> parse(
                        twoHistories + browserHints +
                        "browser-position-history.maxsize=4\n"));
        assertTrue(exception.getMessage()
                            .contains("Invalid position history property key"),
                exception.getMessage());
    }

    @Test
    void hintsCannotComeFromAnUndefinedPositionHistory() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> parse(
                        twoHistories +
                        "browser-hint-mode.hint.type=browsre-position-history\n"));
        assertTrue(exception.getMessage().contains("undefined position history"),
                exception.getMessage());
    }

    @Test
    void anUndefinedPositionHistoryHasNoMaxSize() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> parse(
                        twoHistories + browserHints +
                        "browsre-position-history.max-size=4\n"));
        assertTrue(exception.getMessage().contains("undefined position history"),
                exception.getMessage());
    }

    @Test
    void maxPositionHistorySizeIsRemoved() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> parse(
                        twoHistories + browserHints +
                        "max-position-history-size=4\n"));
        assertTrue(exception.getMessage().contains("position-history.max-size"),
                exception.getMessage());
    }

    @Test
    void onlyTheDefaultPositionHistoryCanBeReferenced() throws IOException {
        parse(twoHistories + browserHints +
              "_other-mode.position-history=idle-mode.position-history\n");
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> parse(
                        twoHistories + browserHints +
                        "_other-mode.browser-position-history=idle-mode.browser-position-history\n"));
        assertTrue(exception.getMessage().contains("every position history"),
                exception.getMessage());
    }

}

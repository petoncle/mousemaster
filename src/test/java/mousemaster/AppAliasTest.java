package mousemaster;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppAliasTest {

    private static Set<String> apps(String appAliasValue) {
        Configuration configuration = ConfigurationParser.parse(
                List.of("app-alias.some=" + appAliasValue,
                        "idle-mode.to.other-mode=_{some} +a",
                        "other-mode.to.idle-mode=+esc"),
                KeyboardLayout.keyboardLayoutByShortName.get("us-qwerty"));
        return configuration.modeMap()
                            .get("idle-mode")
                            .comboMap()
                            .commandsByCombo()
                            .keySet()
                            .stream()
                            .flatMap(combo -> combo.precondition()
                                                   .appPrecondition()
                                                   .mustBeActiveApps()
                                                   .stream())
                            .map(App::executableName)
                            .collect(Collectors.toSet());
    }

    @Test
    void spaceSeparatesApps() {
        assertEquals(Set.of("firefox.exe", "chrome.exe"), apps("firefox.exe chrome.exe"));
    }

    /** macOS executable names have spaces in them, so they have to be quotable. */
    @Test
    void quotesHoldAnAppNameContainingASpace() {
        assertEquals(Set.of("System Settings"), apps("\"System Settings\""));
    }

    @Test
    void quotedAndUnquotedAppsMix() {
        assertEquals(Set.of("System Settings", "Finder", "Google Chrome"),
                apps("\"System Settings\" Finder \"Google Chrome\""));
    }

}

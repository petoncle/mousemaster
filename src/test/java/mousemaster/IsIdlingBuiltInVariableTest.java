package mousemaster;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parser-level checks for the built-in isidling variable. A full config parse
 * initializes Qt (font validation) which is unavailable headless, so these
 * exercise parseVariableNames directly — the early pass that decides which
 * names are treated as variables (vs keys) in combo preconditions.
 */
class IsIdlingBuiltInVariableTest {

    private static final KeyResolver identityKeyResolver = new KeyResolver(
            new KeyboardLayout("test", "test", "test", "test", List.of()),
            new KeyboardLayout("test", "test", "test", "test", List.of()));

    private static Set<String> parseVariableNames(String... lines) {
        return ConfigurationParser.parseVariableNames(List.of(lines), Map.of(),
                identityKeyResolver);
    }

    @Test
    void isidlingIsAlwaysAVariableNameEvenWithoutSetVariable() {
        // Seeded unconditionally, so _{isidling} in a combo is classified as a
        // variable precondition rather than an (invalid) key name.
        assertTrue(parseVariableNames().contains(BuiltInVariable.IS_IDLING));
    }

    @Test
    void userSetVariableIsidlingIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parseVariableNames("some-mode.set-variable.isidling=+a"));
        assertTrue(e.getMessage().contains("isidling"), e.getMessage());
    }

    @Test
    void userVariablesCoexistWithBuiltIns() {
        Set<String> names = parseVariableNames("some-mode.set-variable.myvar=+a");
        assertTrue(names.contains("myvar"));
        assertTrue(names.contains(BuiltInVariable.IS_IDLING));
    }
}

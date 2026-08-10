package mousemaster;

import mousemaster.platform.ActiveAppFinder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A screen filter in a combo property selects the screens the value applies to: it is
 * the same as the .3840x2160-300% suffix of a property key.
 */
class ComboScreenFilterTest {

    private ComboWatcher comboWatcher;

    private Configuration load(String... lines) {
        Configuration configuration = ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
        ActiveAppFinder noApp = () -> new App("test.exe");
        comboWatcher = new ComboWatcher(null, null, noApp, (Clock) Instant::now, Set.of(),
                Set.of(), false, configuration.modeMap(),
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
        return configuration;
    }

    private static double boxBorderRadius(Mode mode, int width, int height, double scale) {
        return mode.hintMesh()
                   .styleByFilter()
                   .get(new ScreenFilter.FixedScreenFilter(width, height, scale))
                   .boxBorderRadius();
    }

    private double mutatedBoxBorderRadius(int width, int height, double scale) {
        return boxBorderRadius(comboWatcher.getMutatedMode(), width, height, scale);
    }

    @Test
    void filterAloneIsTheSameAsThePropertyKeySuffix() {
        Mode combo = load("idle-mode.hint.selection-keys=a b c d",
                "idle-mode.hint.box-border-radius=1 | _{3840x2160-300%} -> 3")
                .modeMap()
                .get(Mode.IDLE_MODE_NAME);
        Mode suffix = load("idle-mode.hint.selection-keys=a b c d",
                "idle-mode.hint.box-border-radius=1",
                "idle-mode.hint.box-border-radius.3840x2160-300%=3")
                .modeMap()
                .get(Mode.IDLE_MODE_NAME);
        assertEquals(3, boxBorderRadius(combo, 3840, 2160, 3));
        assertEquals(1, boxBorderRadius(combo, 1920, 1080, 1));
        assertEquals(boxBorderRadius(suffix, 3840, 2160, 3),
                boxBorderRadius(combo, 3840, 2160, 3));
        assertEquals(boxBorderRadius(suffix, 1920, 1080, 1),
                boxBorderRadius(combo, 1920, 1080, 1));
    }

    @Test
    void filterMixedWithAKeyMutatesOnlyTheFiltersScreens() {
        load("idle-mode.hint.selection-keys=a b c d",
                "idle-mode.hint.box-border-radius=1 | _{3840x2160-300% isidling} -> 3");
        assertEquals(1, mutatedBoxBorderRadius(3840, 2160, 3));
        assertEquals(1, mutatedBoxBorderRadius(1920, 1080, 1));

        comboWatcher.setIdling(true);
        assertEquals(3, mutatedBoxBorderRadius(3840, 2160, 3));
        assertEquals(1, mutatedBoxBorderRadius(1920, 1080, 1));

        comboWatcher.setIdling(false);
        assertEquals(1, mutatedBoxBorderRadius(3840, 2160, 3));
    }

    @Test
    void aScreenAliasStandsForEveryFilterItNames() {
        Mode mode = load("screen-alias.big=3840x2160-300% 2560x1440-100%",
                "idle-mode.hint.selection-keys=a b c d",
                "idle-mode.hint.box-border-radius=1 | _{big} -> 3")
                .modeMap()
                .get(Mode.IDLE_MODE_NAME);
        assertEquals(3, boxBorderRadius(mode, 3840, 2160, 3));
        assertEquals(3, boxBorderRadius(mode, 2560, 1440, 1));
        assertEquals(1, boxBorderRadius(mode, 1920, 1080, 1));
    }

    /** A screen alias can name another one, like a key alias can. */
    @Test
    void aScreenAliasCanNameAnotherOne() {
        Mode mode = load("screen-alias.huge=3840x2160-300%",
                "screen-alias.big=2560x1440-100%",
                "screen-alias.dense=huge big",
                "idle-mode.hint.selection-keys=a b c d",
                "idle-mode.hint.box-border-radius=1 | _{dense} -> 3")
                .modeMap()
                .get(Mode.IDLE_MODE_NAME);
        assertEquals(3, boxBorderRadius(mode, 3840, 2160, 3));
        assertEquals(3, boxBorderRadius(mode, 2560, 1440, 1));
        assertEquals(1, boxBorderRadius(mode, 1920, 1080, 1));
    }

    /** Two filters are alternatives: | between them, or the *-list an alias expands to. */
    @Test
    void severalFiltersAreAlternatives() {
        for (String block : List.of("_{3840x2160-300% | 2560x1440-100%}",
                "_{3840x2160-300%*2560x1440-100%}")) {
            Mode mode = load("idle-mode.hint.selection-keys=a b c d",
                    "idle-mode.hint.box-border-radius=1 | " + block + " -> 3")
                    .modeMap()
                    .get(Mode.IDLE_MODE_NAME);
            assertEquals(3, boxBorderRadius(mode, 3840, 2160, 3), block);
            assertEquals(3, boxBorderRadius(mode, 2560, 1440, 1), block);
            assertEquals(1, boxBorderRadius(mode, 1920, 1080, 1), block);
        }
    }

    @Test
    void aSpaceBetweenFiltersIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> load("idle-mode.hint.selection-keys=a b c d",
                        "idle-mode.hint.box-border-radius=1 | _{3840x2160-300% 2560x1440-100%} -> 3"));
        assertTrue(e.getMessage().contains("can never be satisfied"), e.getMessage());
    }

    /** Alongside keys, a space means both screens at once and | an alternative to the keys. */
    @Test
    void severalFiltersMixedWithAKeyNeedOneBranchEach() {
        load("idle-mode.hint.selection-keys=a b c d",
                "idle-mode.hint.box-border-radius=1 | _{isidling 3840x2160-300%} -> 3 | _{isidling 2560x1440-100%} -> 3");
        comboWatcher.setIdling(true);
        assertEquals(3, mutatedBoxBorderRadius(3840, 2160, 3));
        assertEquals(3, mutatedBoxBorderRadius(2560, 1440, 1));
        assertEquals(1, mutatedBoxBorderRadius(1920, 1080, 1));

        for (String block : List.of("_{3840x2160-300% 2560x1440-100% isidling}",
                "_{3840x2160-300% | 2560x1440-100% isidling}")) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> load("idle-mode.hint.selection-keys=a b c d",
                            "idle-mode.hint.box-border-radius=1 | " + block + " -> 3"), block);
            assertTrue(e.getMessage().contains("its own branch"), e.getMessage());
        }
    }

    /** A tier value survives the OS mutation only if its own branch comes after it. */
    @Test
    void aFilteredMutationOverridesAnUnfilteredOne() {
        load("idle-mode.hint.selection-keys=a b c d",
                "idle-mode.hint.box-border-radius=1 | _{3840x2160-300%} -> 2 | _{isidling} -> 3 | _{isidling 3840x2160-300%} -> 4");
        assertEquals(2, mutatedBoxBorderRadius(3840, 2160, 3));
        assertEquals(1, mutatedBoxBorderRadius(1920, 1080, 1));

        comboWatcher.setIdling(true);
        assertEquals(4, mutatedBoxBorderRadius(3840, 2160, 3));
        assertEquals(3, mutatedBoxBorderRadius(1920, 1080, 1));
    }

    @Test
    void aFilterIsRejectedOnANonHintProperty() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> load("idle-mode.indicator.render-as-cursor=false | _{3840x2160-300%} -> true"));
        assertTrue(e.getMessage().contains("only supported for hint properties"),
                e.getMessage());
    }

}

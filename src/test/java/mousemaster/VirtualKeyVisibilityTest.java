package mousemaster;

import mousemaster.platform.ActiveAppFinder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A virtual key event is visible only to combos whose sequence names that key. Events are fed
 * straight into keyEvent, the way MacroPlayer feeds them.
 */
class VirtualKeyVisibilityTest {

    private Instant now = Instant.parse("2020-01-01T00:00:00Z");
    private ComboWatcher comboWatcher;

    private void load(String... lines) {
        Configuration configuration = ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
        ModeMap modeMap = configuration.modeMap();
        ActiveAppFinder noApp = () -> new App("test.exe");
        comboWatcher = new ComboWatcher(null, null, noApp, () -> now, Set.of(), Set.of(),
                false, modeMap, configuration.initiallySetVariables(),
                configuration.virtualKeys());
        comboWatcher.setModeListeners(List.of(new ModeListener() {
            @Override
            public void modeChanged(Mode newMode) {
            }

            @Override
            public void modeTimedOut() {
            }
        }));
        comboWatcher.modeChanged(modeMap.get(Mode.IDLE_MODE_NAME));
    }

    private void press(Key key) {
        comboWatcher.keyEvent(new KeyEvent.PressKeyEvent(now, key));
    }

    private void press(String keyName) {
        press(Key.ofName(keyName));
    }

    private void pressVirtual(String keyName) {
        press(new Key(keyName, null, null));
    }

    private void elapse(long millis) {
        now = now.plus(Duration.ofMillis(millis));
        comboWatcher.update(millis / 1000d);
    }

    private boolean fired() {
        return comboWatcher.getMutatedMode().indicator().renderAsCursor();
    }

    @Test
    void realKeyBetweenTwoMovesBreaksTheCombo() {
        load("idle-mode.indicator.render-as-cursor=false | +a +b -> true");
        press("a");
        press("z");
        press("b");
        assertFalse(fired());
    }

    @Test
    void virtualKeyBetweenTwoMovesDoesNotBreakTheCombo() {
        load("virtual-keys=flag",
                "idle-mode.indicator.render-as-cursor=false | +a +b -> true");
        press("a");
        pressVirtual("flag");
        press("b");
        assertTrue(fired());
    }

    @Test
    void comboNamingTheVirtualKeySeesIt() {
        load("virtual-keys=flag",
                "idle-mode.indicator.render-as-cursor=false | +flag -> true");
        pressVirtual("flag");
        assertTrue(fired());
    }

    @Test
    void virtualKeyDoesNotCancelAHoldItIsInvisibleTo() {
        // flag is a sequence key of the mode (+flag +q), but the held combo does not name it.
        load("virtual-keys=flag",
                "idle-mode.indicator.render-as-cursor=false | +a-250 -> true | +flag +q -> true");
        press("a");
        pressVirtual("flag");
        elapse(300);
        assertTrue(fired());
    }
}

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
    private ModeMap modeMap;

    private void load(String... lines) {
        Configuration configuration = ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
        modeMap = configuration.modeMap();
        ActiveAppFinder noApp = () -> new App("test.exe");
        comboWatcher = new ComboWatcher(null, null, noApp, null, () -> now, Set.of(), Set.of(),
                false, modeMap, configuration.initiallySetVariables(),
                configuration.virtualKeys(), configuration.initiallyPressedVirtualKeys());
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

    private void advance(long millis) {
        now = now.plus(Duration.ofMillis(millis));
    }

    private void elapse(long millis) {
        advance(millis);
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
    void virtualKeyDoesNotMatchACompletedComboAgain() {
        // The completed +a stays buffered, so a later event must not re-run its commands.
        load("virtual-keys=flag",
                "idle-mode.indicator.render-as-cursor=false | +a -> true");
        press("a");
        assertTrue(fired());
        comboWatcher.modeChanged(modeMap.get(Mode.IDLE_MODE_NAME));
        assertFalse(fired());
        pressVirtual("flag");
        assertFalse(fired(), "the buffered +a must not be matched again");
    }

    @Test
    void virtualKeyNamedElsewhereDoesNotMatchACompletedComboAgain() {
        // +flag +q names flag, so it reaches the preparation, and pressing flag alone does not
        // complete that branch.
        load("virtual-keys=flag",
                "idle-mode.indicator.render-as-cursor=false | +a -> true | +flag +q -> true");
        press("a");
        assertTrue(fired());
        comboWatcher.modeChanged(modeMap.get(Mode.IDLE_MODE_NAME));
        assertFalse(fired());
        pressVirtual("flag");
        assertFalse(fired(), "the buffered +a must not be matched again");
    }

    @Test
    void aVirtualKeyDeclaredWithPlusStartsPressed() {
        load("virtual-keys=+flag other",
                "idle-mode.indicator.render-as-cursor=false | _{flag} -> true");
        assertTrue(fired());
    }

    @Test
    void aVirtualKeyDeclaredWithMinusOrNothingStartsReleased() {
        load("virtual-keys=-flag",
                "idle-mode.indicator.render-as-cursor=false | _{flag} -> true");
        assertFalse(fired());
    }

    @Test
    void aPressedVirtualKeyDoesNotFalsifyNone() {
        load("virtual-keys=flag",
                "idle-mode.indicator.render-as-cursor=false | _{none} +a -> true");
        pressVirtual("flag");
        press("a");
        assertTrue(fired(), "_{none} asks what the user is holding, not which flags are set");
    }

    @Test
    void virtualKeyDoesNotDistortTheGapBetweenRealKeys() {
        // The gap that must satisfy +a's 50-300ms is a -> b (200ms), not flag -> b (10ms).
        load("virtual-keys=flag",
                "idle-mode.indicator.render-as-cursor=false | +a-50-300 +b -> true");
        press("a");
        advance(190);
        pressVirtual("flag");
        advance(10);
        press("b");
        assertTrue(fired());
    }

    @Test
    void virtualKeyDoesNotCancelAHoldItIsInvisibleTo() {
        // flag is a sequence key of the mode (+flag +q), but the waiting combo does not name it.
        load("virtual-keys=flag",
                "idle-mode.indicator.render-as-cursor=false | +a-250 -> true | +flag +q -> true");
        press("a");
        pressVirtual("flag");
        elapse(300);
        assertTrue(fired());
    }
}

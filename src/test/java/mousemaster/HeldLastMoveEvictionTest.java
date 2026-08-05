package mousemaster;

import mousemaster.platform.ActiveAppFinder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A combo waiting for its held last move to elapse is canceled by a key that some combo
 * sequence of the mode uses, not by an unrelated key.
 */
class HeldLastMoveEvictionTest {

    /** b and c are sequence keys, z is not. */
    private static final String combos =
            "idle-mode.indicator.render-as-cursor=false | +a-250 -> true | +b +c -> true";

    private Instant now = Instant.parse("2020-01-01T00:00:00Z");
    private ComboWatcher comboWatcher;

    private void load(String... lines) {
        Configuration configuration = ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
        ModeMap modeMap = configuration.modeMap();
        ActiveAppFinder noApp = () -> new App("test.exe");
        comboWatcher = new ComboWatcher(null, null, noApp, () -> now, Set.of(), Set.of(),
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

    private void press(String keyName) {
        comboWatcher.keyEvent(new KeyEvent.PressKeyEvent(now, Key.ofName(keyName)));
    }

    private void elapse(long millis) {
        now = now.plus(Duration.ofMillis(millis));
        comboWatcher.update(millis / 1000d);
    }

    private boolean heldComboFired() {
        return comboWatcher.getMutatedMode().indicator().renderAsCursor();
    }

    @Test
    void heldLastMoveFiresOnceTheHoldElapses() {
        load(combos);
        press("a");
        elapse(300);
        assertTrue(heldComboFired());
    }

    @Test
    void unrelatedKeyDoesNotCancelHeldLastMove() {
        load(combos);
        press("a");
        press("z");
        elapse(300);
        assertTrue(heldComboFired(), "z is used by no combo sequence of the mode");
    }

    @Test
    void sequenceKeyCancelsHeldLastMove() {
        load(combos);
        press("a");
        press("b");
        elapse(300);
        assertFalse(heldComboFired(), "b starts +b +c, so the hold is abandoned");
    }
}

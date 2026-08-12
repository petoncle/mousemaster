package mousemaster;

import mousemaster.platform.ActiveAppFinder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A precondition-only mutation is applied and reverted as its variable toggles,
 * across the update() ticks that run in between. The refresh rebuilds the mutated
 * mode only when the active mutations change, so a tick must not leave it stale.
 */
class PreconditionOnlyMutationTest {

    private final List<Mode> notifiedModes = new ArrayList<>();
    private ComboWatcher comboWatcher;

    private void load(String... lines) {
        Configuration configuration = ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
        ModeMap modeMap = configuration.modeMap();
        ActiveAppFinder noApp = () -> new App("test.exe");
        Clock clock = Instant::now;
        // A precondition key is only recorded as pressed if it is one of these, as in Mousemaster.
        Set<Key> unpressedPreconditionKeys = new HashSet<>();
        Set<Key> pressedPreconditionKeys = new HashSet<>();
        for (Mode mode : modeMap.modes()) {
            for (Combo combo : mode.comboMap().commandsByCombo().keySet()) {
                unpressedPreconditionKeys.addAll(
                        combo.precondition().keyPrecondition().unpressedKeySet());
                pressedPreconditionKeys.addAll(combo.precondition()
                                                    .keyPrecondition()
                                                    .pressedKeyPrecondition()
                                                    .allKeys());
            }
        }
        comboWatcher = new ComboWatcher(null, null, noApp, null, clock,
                unpressedPreconditionKeys, pressedPreconditionKeys,
                false, modeMap, configuration.initiallySetVariables(),
                configuration.virtualKeys(), configuration.initiallyPressedVirtualKeys());
        comboWatcher.setModeListeners(List.of(new ModeListener() {
            @Override
            public void modeChanged(Mode newMode) {
                notifiedModes.add(newMode);
            }

            @Override
            public void modeTimedOut() {
            }
        }));
        comboWatcher.modeChanged(modeMap.get(Mode.IDLE_MODE_NAME));
    }

    private void tick() {
        comboWatcher.update(0.01);
    }

    private boolean renderAsCursor() {
        return comboWatcher.getMutatedMode().indicator().renderAsCursor();
    }

    @Test
    void mutationFollowsItsVariableAcrossTicks() {
        load("idle-mode.indicator.render-as-cursor=false | _{isidling} -> true");
        tick();
        assertFalse(renderAsCursor());

        comboWatcher.setIdling(true);
        assertTrue(renderAsCursor());
        for (int i = 0; i < 5; i++)
            tick();
        assertTrue(renderAsCursor(), "a tick must not revert an applied mutation");

        comboWatcher.setIdling(false);
        assertFalse(renderAsCursor());
        for (int i = 0; i < 5; i++)
            tick();
        assertFalse(renderAsCursor(), "a tick must not re-apply a reverted mutation");
    }

    @Test
    void ticksWithoutAChangeDoNotNotifyListeners() {
        load("idle-mode.indicator.render-as-cursor=false | _{isidling} -> true");
        tick();
        notifiedModes.clear();
        for (int i = 0; i < 5; i++)
            tick();
        assertEquals(List.of(), notifiedModes);

        comboWatcher.setIdling(true);
        assertEquals(1, notifiedModes.size());
        assertTrue(notifiedModes.getFirst().indicator().renderAsCursor());
    }

    /** A key precondition is refreshed declaratively too, so the key events that neither
     *  press nor release it leave the mutation alone. */
    @Test
    void aKeyPreconditionMutationFollowsThatKeyOnly() {
        load("idle-mode.indicator.render-as-cursor=false | _{leftshift} -> true");
        Instant now = Instant.now();
        assertFalse(renderAsCursor());

        comboWatcher.keyEvent(new KeyEvent.PressKeyEvent(now, Key.leftshift));
        assertTrue(renderAsCursor());

        notifiedModes.clear();
        comboWatcher.keyEvent(new KeyEvent.PressKeyEvent(now, Key.ofName("a")));
        comboWatcher.keyEvent(new KeyEvent.ReleaseKeyEvent(now, Key.ofName("a")));
        assertTrue(renderAsCursor());
        assertEquals(List.of(), notifiedModes, "an unrelated key must not rebuild the mode");

        comboWatcher.keyEvent(new KeyEvent.ReleaseKeyEvent(now, Key.leftshift));
        assertFalse(renderAsCursor());
    }
}

package mousemaster;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyAliasCharacterTest {

    private static final List<Command> PRESS = List.of(new Command.PressMiddle());

    private final List<Command> ranCommands = new ArrayList<>();
    private Instant now = Instant.parse("2020-01-01T00:00:00Z");
    private ComboWatcher comboWatcher;

    private void load(String activeKeyboardLayout, String... lines) {
        Configuration configuration = ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayoutByShortName.get(activeKeyboardLayout));
        ModeMap modeMap = configuration.modeMap();
        Set<Key> unpressedPreconditionKeys = new HashSet<>();
        Set<Key> pressedPreconditionKeys = new HashSet<>();
        for (Mode mode : modeMap.modes())
            for (Combo combo : mode.comboMap().commandsByCombo().keySet()) {
                unpressedPreconditionKeys.addAll(combo.precondition()
                                                      .keyPrecondition()
                                                      .unpressedKeySet());
                pressedPreconditionKeys.addAll(combo.precondition()
                                                    .keyPrecondition()
                                                    .pressedKeyPrecondition()
                                                    .allKeys());
            }
        CommandRunner commandRunner = new CommandRunner(null, null, null) {
            @Override
            public boolean runningAtomicCommand() {
                return false;
            }

            @Override
            public void run(Command command, Key eventKey) {
                ranCommands.add(command);
            }
        };
        comboWatcher = new ComboWatcher(commandRunner, null, () -> new App("test.exe"), null,
                () -> now, unpressedPreconditionKeys, pressedPreconditionKeys,
                new KeyRedactor(KeyRedaction.NONE),
                modeMap, configuration.initiallySetVariables(), configuration.virtualKeys(),
                configuration.initiallyPressedVirtualKeys(),
                configuration.activeKeyboardLayout());
        comboWatcher.setModeListeners(List.of());
        comboWatcher.modeChanged(modeMap.get(Mode.IDLE_MODE_NAME));
    }

    private void press(String keyName) {
        comboWatcher.keyEvent(new KeyEvent.PressKeyEvent(now, Key.ofName(keyName)));
    }

    private void release(String keyName) {
        comboWatcher.keyEvent(new KeyEvent.ReleaseKeyEvent(now, Key.ofName(keyName)));
    }

    private void tick(long millis) {
        for (long elapsed = 0; elapsed < millis; elapsed += 10) {
            now = now.plusMillis(10);
            comboWatcher.update(0.01);
        }
    }

    @Test
    void shiftTypesTheCharacter() {
        load("us-qwerty", "key-alias.help.us-qwerty=questionmark", "idle-mode.press.middle=+help");
        press("rightshift");
        press("/");
        assertEquals(PRESS, ranCommands);
    }

    @Test
    void theKeyAloneDoesNotTypeTheCharacter() {
        load("us-qwerty", "key-alias.help.us-qwerty=questionmark", "idle-mode.press.middle=+help");
        press("/");
        assertEquals(List.of(), ranCommands);
    }

    @Test
    void theCharacterFollowsAKey() {
        load("us-qwerty", "idle-mode.press.middle=+a +questionmark");
        press("a");
        press("leftshift");
        press("/");
        assertEquals(PRESS, ranCommands);
    }

    @Test
    void aKeyFollowsTheCharacter() {
        load("us-qwerty", "idle-mode.press.middle=+questionmark +a");
        press("leftshift");
        press("/");
        release("leftshift");
        press("a");
        assertEquals(PRESS, ranCommands);
    }

    @Test
    void shiftPressedBeforeTheKeyTypesTheCharacter() {
        load("us-qwerty", "idle-mode.press.middle=+a +questionmark");
        press("leftshift");
        press("a");
        press("/");
        assertEquals(PRESS, ranCommands);
    }

    @Test
    void shiftDoesNotShortenTheGapBeforeTheCharacter() {
        load("us-qwerty", "idle-mode.press.middle=+a-50-300 +questionmark");
        press("a");
        now = now.plusMillis(40);
        press("leftshift");
        now = now.plusMillis(20);
        press("/");
        assertEquals(PRESS, ranCommands);
    }

    @Test
    void anotherPressedKeyDoesNotTypeTheCharacter() {
        load("us-qwerty", "key-alias.help.us-qwerty=questionmark", "idle-mode.press.middle=+help",
                "idle-mode.press.left=_{leftctrl} +x");
        press("leftctrl");
        press("leftshift");
        press("/");
        assertEquals(List.of(), ranCommands);
    }

    @Test
    void theCharacterIsTypedWhereTheActiveLayoutTypesIt() {
        load("fr-azerty", "key-alias.help.us-qwerty=questionmark", "idle-mode.press.middle=+help");
        press("leftshift");
        press(",");
        assertEquals(PRESS, ranCommands);
    }

    @Test
    void altgrTypesTheCharacter() {
        load("de-qwertz", "key-alias.at.us-qwerty=@", "idle-mode.press.middle=+at");
        press("rightalt");
        press("q");
        assertEquals(PRESS, ranCommands);
    }

    @Test
    void anAliasMixesCharactersAndKeys() {
        load("us-qwerty", "key-alias.help.us-qwerty=questionmark f1",
                "idle-mode.press.middle=+help");
        press("f1");
        release("f1");
        press("leftshift");
        press("/");
        assertEquals(List.of(PRESS.getFirst(), PRESS.getFirst()), ranCommands);
    }

    @Test
    void aKeyNameTypesTheCharacter() {
        load("us-qwerty", "idle-mode.press.middle=+A");
        press("a");
        press("leftshift");
        press("a");
        assertEquals(PRESS, ranCommands);
    }

    @Test
    void aStaticKeyNameTypesTheCharacter() {
        load("us-qwerty", "idle-mode.press.middle=+plus");
        press("leftshift");
        press("=");
        assertEquals(PRESS, ranCommands);
    }

    @Test
    void anExpandedAliasTypesTheCharacter() {
        load("us-qwerty", "key-alias.help.us-qwerty=questionmark", "idle-mode.press.middle=*help");
        press("leftshift");
        press("/");
        release("/");
        assertEquals(PRESS, ranCommands);
    }

    @Test
    void aPreconditionHoldsTheCharacter() {
        load("us-qwerty", "key-alias.help.us-qwerty=questionmark",
                "idle-mode.press.middle=_{help} +b");
        press("leftshift");
        press("/");
        press("b");
        assertEquals(PRESS, ranCommands);
    }

    @Test
    void aPreconditionDoesNotHoldTheKeyAlone() {
        load("us-qwerty", "key-alias.help.us-qwerty=questionmark",
                "idle-mode.press.middle=_{help} +b");
        press("/");
        press("b");
        assertEquals(List.of(), ranCommands);
    }

    @Test
    void aPreconditionHoldsTheKeyOrTheCharacter() {
        load("us-qwerty", "idle-mode.press.middle=_{questionmark*a} +b");
        press("a");
        press("b");
        assertEquals(PRESS, ranCommands);
    }

    @Test
    void anUnpressedPreconditionExcludesTheCharacter() {
        load("us-qwerty", "idle-mode.press.middle=^{questionmark} +b");
        press("leftshift");
        press("/");
        press("b");
        assertEquals(List.of(), ranCommands);
    }

    @Test
    void anUnpressedPreconditionDoesNotExcludeTheReleasedCharacter() {
        load("us-qwerty", "idle-mode.press.middle=^{questionmark} +b");
        press("leftshift");
        press("/");
        release("/");
        press("b");
        assertEquals(PRESS, ranCommands);
    }

    @Test
    void anUnpressedPreconditionHoldsBackAWait() {
        load("us-qwerty", "idle-mode.noop.eatquestionmark=+questionmark",
                "idle-mode.to.normal-mode=^{questionmark} wait-500",
                "normal-mode.to.idle-mode=+esc");
        Command switchToNormalMode = new Command.SwitchMode("normal-mode");
        press("leftshift");
        press("/");
        tick(1000);
        assertFalse(ranCommands.contains(switchToNormalMode));
        release("/");
        tick(1000);
        assertTrue(ranCommands.contains(switchToNormalMode));
    }
    @Test
    void aPreconditionMutationFollowsTheCharacter() {
        load("us-qwerty",
                "idle-mode.indicator.render-as-cursor=false | _{questionmark} -> true");
        press("leftshift");
        press("/");
        assertTrue(comboWatcher.getMutatedMode().indicator().renderAsCursor());
        release("/");
        assertFalse(comboWatcher.getMutatedMode().indicator().renderAsCursor());
    }

    @Test
    void aPreconditionMutationDoesNotFollowTheKeyAlone() {
        load("us-qwerty",
                "idle-mode.indicator.render-as-cursor=false | _{questionmark} -> true");
        press("/");
        assertFalse(comboWatcher.getMutatedMode().indicator().renderAsCursor());
    }
}

package mousemaster;

import mousemaster.KeyEvent.PressKeyEvent;
import mousemaster.KeyEvent.ReleaseKeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MacroPlayer {

    private static final Logger logger = LoggerFactory.getLogger(MacroPlayer.class);

    private final PlatformClock clock;
    private final ComboWatcher comboWatcher;
    private final KeyboardManager keyboardManager;
    private final List<ResolvedMacro> macrosToExecute = new ArrayList<>();
    private MacroInProgress macroInProgress;
    private final Set<Key> keysPressedByMacro = new HashSet<>();
    /**
     * Used to prevent keyReleasedNotEaten from removing a key that was just pressed
     * by a macro during the same event processing (e.g. capsword: user's -leftshift
     * completes combo which triggers macro +leftshift, then keyReleasedNotEaten is
     * called for the same -leftshift event: we must not remove leftshift from
     * keysPressedByMacro because the macro just pressed it).
     */
    private final Set<Key> keysPressedByMacroDuringCurrentTick = new HashSet<>();

    public MacroPlayer(PlatformClock clock, ComboWatcher comboWatcher,
                       KeyboardManager keyboardManager) {
        this.clock = clock;
        this.comboWatcher = comboWatcher;
        this.keyboardManager = keyboardManager;
    }

    private static class MacroInProgress {
        private final ResolvedMacro macro;
        private int currentIndex = -1;
        private double remainingWait;

        private MacroInProgress(ResolvedMacro macro) {
            this.macro = macro;
        }

        public ResolvedMacroParallel currentParallel() {
            return macro.output().parallels().get(currentIndex);
        }
    }

    public void submit(ResolvedMacro macro) {
        macrosToExecute.add(macro);
        // Execute the first parallel immediately (without waiting for the next update tick)
        // if it contains only OS-destination key moves. ComboWatcher-destination moves are
        // deferred because the combo watcher state may not be fully settled yet.
        tryExecuteFirstParallelImmediately();
    }

    private void tryExecuteFirstParallelImmediately() {
        if (macroInProgress != null)
            return;
        ResolvedMacro macro = macrosToExecute.getFirst();
        ResolvedMacroParallel firstParallel = macro.output().parallels().getFirst();
        if (firstParallel.moves().isEmpty())
            return;
        boolean allOsMoves = firstParallel.moves().stream().allMatch(
                move -> move instanceof StringMacroMove ||
                        (move instanceof ResolvedKeyMacroMove keyMove &&
                         keyMove.destination() == MacroMoveDestination.OS));
        if (!allOsMoves)
            return;
        macroInProgress = new MacroInProgress(macrosToExecute.removeFirst());
        macroInProgress.currentIndex = 0;
        macroInProgress.remainingWait = firstParallel.duration().toNanos() / 1e9;
        logger.debug("Executing macro parallel immediately: " + firstParallel);
        executeParallel(firstParallel);
        if (macroInProgress.currentIndex ==
            macroInProgress.macro.output().parallels().size() - 1) {
            macroInProgress = null;
        }
    }

    public boolean macroInProgress() {
        return macroInProgress != null;
    }

    public void reset() {
        keysPressedByMacro.clear();
    }

    public void keyReleasedNotEaten(Key key) {
        if (!keysPressedByMacroDuringCurrentTick.contains(key))
            keysPressedByMacro.remove(key);
        // Scenario where user-press-eaten, then macro-press (uneats the key), then user-release (not eaten as per rule 1).
        // The macro-press is a repeating SendInput that should be stopped when user releases the key.
        WindowsKeyboard.keyReleasedNotEaten(key);
    }

    public boolean isKeyPressedByMacro(Key key) {
        return keysPressedByMacro.contains(key) &&
               !keysPressedByMacroDuringCurrentTick.contains(key);
    }


    public void update(double delta) {
        keysPressedByMacroDuringCurrentTick.clear();
        if (macroInProgress == null && !macrosToExecute.isEmpty()) {
            macroInProgress = new MacroInProgress(macrosToExecute.removeFirst());
            ResolvedMacroParallel firstParallel = macroInProgress.macro.output().parallels().getFirst();
            if (firstParallel.moves().isEmpty())
                macroInProgress.remainingWait = firstParallel.duration().toNanos() / 1e9;
        }
        if (macroInProgress == null)
            return;
        macroInProgress.remainingWait -= delta;
        if (macroInProgress.remainingWait <= 0) {
            if (macroInProgress.currentIndex ==
                macroInProgress.macro.output().parallels().size() - 1) {
                macroInProgress = null;
                return;
            }
            macroInProgress.currentIndex++;
            macroInProgress.remainingWait =
                    macroInProgress.currentParallel().duration().toNanos() / 1e9;
            ResolvedMacroParallel parallel = macroInProgress.currentParallel();
            logger.debug("Executing macro parallel: " + parallel);
            if (!parallel.moves().isEmpty())
                executeParallel(parallel);
        }
    }

    private void executeParallel(ResolvedMacroParallel parallel) {
        // Batch OS moves (key moves and string moves).
        List<ResolvedMacroMove> osMoves = new ArrayList<>();
        for (ResolvedMacroMove move : parallel.moves()) {
            switch (move) {
                case StringMacroMove stringMove -> {
                    osMoves.add(stringMove);
                }
                case ResolvedKeyMacroMove keyMove -> {
                    if (keyMove.destination() == MacroMoveDestination.OS) {
                        // 10 scenarios for macro press/release vs user press/release:
                        // 1. user-press-eaten, macro-press, user-release(not eat), macro-release(noop)
                        // 2. user-press-eaten, macro-press, macro-release, user-release(eat)
                        // 3. user-press-eaten, macro-release(noop), user-release(eat)
                        // 4. user-press-noneaten, macro-press(noop), user-release(not eat), macro-release(noop)
                        // 5. user-press-noneaten, macro-press(noop), macro-release, user-release(extra release)
                        // 6. user-press-noneaten, macro-release, user-release(extra release)
                        // 7. macro-press, user-press(eat), user-release(not eat), macro-release(noop)
                        // 8. macro-press, user-press(eat), macro-release, user-release(extra release)
                        // 9. macro-press, macro-release
                        // 10. macro-release(noop)
                        // 11. user-press-eaten, user-release(eat)
                        // 12. user-press-noneaten, user-release(not eat)

                        // To decide if user-press should be eaten:
                        // processingSet.mustBeEaten || macroPressed
                        // To decide if user-release should be eaten:
                        // !macroPressed && processingSet.mustBeEaten
                        // The macro loses ownership when user releases: this allows the user
                        // to release a key the macro pressed (e.g. +leftalt +f -f, user releases alt).
                        if (keyMove.press()) {
                            // Macro-press is noop if user already has the key pressed at OS level.
                            boolean macroPressIsNoop =
                                    keyboardManager.isPressedKeyNotEaten(keyMove.key());
                            keysPressedByMacro.add(keyMove.key());
                            keysPressedByMacroDuringCurrentTick.add(keyMove.key());
                            if (!macroPressIsNoop)
                                osMoves.add(keyMove);
                        }
                        else {
                            // Macro-release is noop if key is not pressed at OS level.
                            // keysPressedByMacro is kept in sync by keyReleasedNotEaten().
                            boolean macroReleaseIsNoop =
                                    !keysPressedByMacro.contains(keyMove.key()) &&
                                    !keyboardManager.isPressedKeyNotEaten(keyMove.key());
                            keysPressedByMacro.remove(keyMove.key());
                            if (!macroReleaseIsNoop)
                                osMoves.add(keyMove);
                        }
                    }
                    else {
                        // Flush any pending OS moves before sending to ComboWatcher.
                        if (!osMoves.isEmpty()) {
                            WindowsKeyboard.sendInputMoves(osMoves, true);
                            osMoves.clear();
                        }
                        KeyEvent event = keyMove.press()
                                ? new PressKeyEvent(clock.now(), keyMove.key())
                                : new ReleaseKeyEvent(clock.now(), keyMove.key());
                        comboWatcher.keyEvent(event);
                    }
                }
            }
        }
        // Flush remaining OS moves.
        if (!osMoves.isEmpty()) {
            WindowsKeyboard.sendInputMoves(osMoves, true);
        }
    }

}

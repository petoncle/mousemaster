package mousemaster;

import mousemaster.KeyEvent.PressKeyEvent;
import mousemaster.KeyEvent.ReleaseKeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class MacroPlayer {

    private static final Logger logger = LoggerFactory.getLogger(MacroPlayer.class);

    private final PlatformClock clock;
    private final ComboWatcher comboWatcher;
    private final List<ResolvedMacro> macrosToExecute = new ArrayList<>();
    private MacroInProgress macroInProgress;

    public MacroPlayer(PlatformClock clock, ComboWatcher comboWatcher) {
        this.clock = clock;
        this.comboWatcher = comboWatcher;
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
    }

    public boolean macroInProgress() {
        return macroInProgress != null;
    }

    public void update(double delta) {
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
        // Batch OS key moves.
        List<ResolvedKeyMacroMove> osKeyMoves = new ArrayList<>();
        for (ResolvedMacroMove move : parallel.moves()) {
            switch (move) {
                case StringMacroMove stringMove -> {
                    // Flush any pending OS key moves before sending string.
                    if (!osKeyMoves.isEmpty()) {
                        WindowsKeyboard.sendInputKeys(osKeyMoves, true, true);
                        osKeyMoves.clear();
                    }
                    WindowsKeyboard.sendInputString(stringMove.string());
                }
                case ResolvedKeyMacroMove keyMove -> {
                    if (keyMove.destination() == MacroMoveDestination.OS) {
                        osKeyMoves.add(keyMove);
                    }
                    else {
                        // Flush any pending OS key moves before sending to ComboWatcher.
                        if (!osKeyMoves.isEmpty()) {
                            WindowsKeyboard.sendInputKeys(osKeyMoves, true, true);
                            osKeyMoves.clear();
                        }
                        KeyEvent event = keyMove.press()
                                ? new PressKeyEvent(clock.now(), keyMove.key())
                                : new ReleaseKeyEvent(clock.now(), keyMove.key());
                        comboWatcher.keyEvent(event);
                    }
                }
            }
        }
        // Flush remaining OS key moves.
        if (!osKeyMoves.isEmpty()) {
            WindowsKeyboard.sendInputKeys(osKeyMoves, true, true);
        }
    }

}

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
    private final List<Macro> macrosToExecute = new ArrayList<>();
    private MacroInProgress macroInProgress;

    public MacroPlayer(PlatformClock clock, ComboWatcher comboWatcher) {
        this.clock = clock;
        this.comboWatcher = comboWatcher;
    }

    private static class MacroInProgress {
        private final Macro macro;
        private int currentIndex = -1;
        private double remainingWait;

        private MacroInProgress(Macro macro) {
            this.macro = macro;
        }

        public MacroParallel currentParallel() {
            return macro.output().parallels().get(currentIndex);
        }
    }

    public void submit(Macro macro) {
        macrosToExecute.add(macro);
    }

    public boolean macroInProgress() {
        return macroInProgress != null;
    }

    public void update(double delta) {
        if (macroInProgress == null && !macrosToExecute.isEmpty()) {
            macroInProgress = new MacroInProgress(macrosToExecute.removeFirst());
            MacroParallel firstParallel = macroInProgress.macro.output().parallels().getFirst();
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
            MacroParallel parallel = macroInProgress.currentParallel();
            logger.debug("Executing macro parallel: " + parallel);
            if (!parallel.moves().isEmpty())
                executeParallel(parallel);
        }
    }

    private void executeParallel(MacroParallel parallel) {
        // Batch OS moves.
        List<MacroMove> osMoves = new ArrayList<>();
        for (MacroMove move : parallel.moves()) {
            if (move.destination() == MacroMoveDestination.OS) {
                osMoves.add(move);
            } else {
                // Flush any pending OS moves before sending to ComboWatcher.
                if (!osMoves.isEmpty()) {
                    WindowsKeyboard.sendInput(osMoves, true, true);
                    osMoves.clear();
                }
                KeyEvent event = move.press()
                        ? new PressKeyEvent(clock.now(), move.key())
                        : new ReleaseKeyEvent(clock.now(), move.key());
                comboWatcher.keyEvent(event);
            }
        }
        // Flush remaining OS moves.
        if (!osMoves.isEmpty()) {
            WindowsKeyboard.sendInput(osMoves, true, true);
        }
    }

}

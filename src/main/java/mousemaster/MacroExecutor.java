package mousemaster;

import mousemaster.KeyEvent.PressKeyEvent;
import mousemaster.KeyEvent.ReleaseKeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MacroExecutor {

    private static final Logger logger = LoggerFactory.getLogger(MacroExecutor.class);
    private static final MacroExecutor remapper = new MacroExecutor("remapping",
            parallel -> WindowsKeyboard.sendInput(parallel.moves(), true, true),
            () -> false);

    private final String name;
    private final Consumer<MacroParallel> executeParallel;
    private final Supplier<Boolean> pauseExecution;
    private final List<Macro> macrosToExecute = new ArrayList<>();
    private MacroInProgress macroInProgress;

    private MacroExecutor(String name, Consumer<MacroParallel> executeParallel,
                          Supplier<Boolean> pauseExecution) {
        this.name = name;
        this.executeParallel = executeParallel;
        this.pauseExecution = pauseExecution;
    }

    public static MacroExecutor remapper() {
        return remapper;
    }

    public static MacroExecutor macroPlayer(PlatformClock clock, ComboWatcher comboWatcher) {
        return new MacroExecutor("macro", parallel -> {
            for (MacroMove move : parallel.moves()) {
                KeyEvent event = move.press()
                        ? new PressKeyEvent(clock.now(), move.key())
                        : new ReleaseKeyEvent(clock.now(), move.key());
                comboWatcher.keyEvent(event);
            }
        }, remapper::macroInProgress); // Pauses macro execution when executing a remapping.
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
        if (pauseExecution.get())
            return;
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
            logger.debug("Executing " + name + " parallel: " + macroInProgress.currentParallel());
            if (!macroInProgress.currentParallel().moves().isEmpty())
                executeParallel.accept(macroInProgress.currentParallel());
        }
    }

}

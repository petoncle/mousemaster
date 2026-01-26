package mousemaster;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Remapper {

    private static final Logger logger = LoggerFactory.getLogger(Remapper.class);

    private final List<Macro> macrosToExecute = new ArrayList<>();
    private MacroInProgress macroInProgress;

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

    public void update(double delta) {
        if (macroInProgress == null && !macrosToExecute.isEmpty()) {
            macroInProgress =
                    new MacroInProgress(macrosToExecute.removeFirst());
            MacroParallel firstParallel =
                    macroInProgress.macro.output().parallels().getFirst();
            if (firstParallel.moves().isEmpty())
                macroInProgress.remainingWait =
                        firstParallel.duration().toNanos() / 1e9;
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
            // Execute moves of this parallel.
            logger.debug("Executing remapping parallel: " +
                        macroInProgress.currentParallel());
            if (!macroInProgress.currentParallel().moves().isEmpty())
                WindowsKeyboard.sendInput(macroInProgress.currentParallel().moves(),
                        true, true);
        }
    }

}

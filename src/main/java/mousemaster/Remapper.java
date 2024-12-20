package mousemaster;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Remapper {

    private static final Logger logger = LoggerFactory.getLogger(Remapper.class);

    private final List<Remapping> remappingsToExecute = new ArrayList<>();
    private RemappingInProgress remappingInProgress;

    private static class RemappingInProgress {

        private final Remapping remapping;
        private int currentIndex = -1;
        private double remainingWait;

        private RemappingInProgress(Remapping remapping) {
            this.remapping = remapping;
        }

        public RemappingParallel currentParallel() {
            return remapping.output().parallels().get(currentIndex);
        }

    }

    public void submitRemapping(Remapping remapping) {
        remappingsToExecute.add(remapping);
    }

    public void update(double delta) {
        if (remappingInProgress == null && !remappingsToExecute.isEmpty())
            remappingInProgress =
                    new RemappingInProgress(remappingsToExecute.removeFirst());
        if (remappingInProgress == null)
            return;
        remappingInProgress.remainingWait -= delta;
        if (remappingInProgress.remainingWait <= 0) {
            if (remappingInProgress.currentIndex ==
                remappingInProgress.remapping.output().parallels().size() - 1) {
                remappingInProgress = null;
                return;
            }
            remappingInProgress.currentIndex++;
            remappingInProgress.remainingWait =
                    remappingInProgress.currentParallel().duration().toNanos() / 10e9;
            // Execute moves of this parallel.
            logger.info("Executing remapping parallel: " + remappingInProgress.currentParallel().moves());
            WindowsKeyboard.sendInput(remappingInProgress.currentParallel().moves());
        }
    }

}

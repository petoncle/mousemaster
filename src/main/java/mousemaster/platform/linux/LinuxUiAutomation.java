package mousemaster.platform.linux;

import mousemaster.platform.UiAutomation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Stub implementation of UiAutomation for Milestone 1.
 * TODO: Implement using AT-SPI2 via D-Bus for Milestone 4.
 */
public class LinuxUiAutomation implements UiAutomation {

    private static final Logger logger = LoggerFactory.getLogger(LinuxUiAutomation.class);

    @Override
    public Future<List<UiElement>> startFindInteractiveUiElements() {
        // TODO: Query AT-SPI2 for accessible UI elements via D-Bus
        logger.debug("startFindInteractiveUiElements() called - returning empty list");
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

}

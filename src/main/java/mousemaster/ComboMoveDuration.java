package mousemaster;

import java.time.Duration;
import java.time.Instant;

/**
 * Null max means infinite max.
 */
public record ComboMoveDuration(Duration min, Duration max) {

    public boolean satisfied(Instant previousEventTime, Instant currentEventTime) {
        if (tooMuchTimeHasPassed(previousEventTime, currentEventTime))
            // Previous move broke the combo because too much time has passed.
            return false;
        if (previousEventTime.plus(min).isAfter(currentEventTime))
            // Previous move broke the combo because not enough time has passed.
            return false;
        return true;
    }

    public boolean tooMuchTimeHasPassed(Instant previousEventTime, Instant currentEventTime) {
        return max != null && previousEventTime.plus(max).isBefore(currentEventTime);
    }

}

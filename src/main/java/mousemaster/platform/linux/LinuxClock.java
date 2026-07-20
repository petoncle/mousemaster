package mousemaster.platform.linux;

import mousemaster.Clock;

import java.time.Instant;

public class LinuxClock implements Clock {

    @Override
    public Instant now() {
        return Instant.now();
    }

}

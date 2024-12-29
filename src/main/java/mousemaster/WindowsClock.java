package mousemaster;

import java.time.Instant;

public class WindowsClock implements PlatformClock {

    private static final Instant systemStartTime;

    static {
        long uptimeMillis = ExtendedKernel32.INSTANCE.GetTickCount64();
        Instant now = Instant.now();
        systemStartTime = now.minusMillis(uptimeMillis);
    }

    private long lastKeyboardHookEventRelativeTimeMillis;

    @Override
    public Instant now() {
        return systemStartTime.plusMillis(lastKeyboardHookEventRelativeTimeMillis);
    }

    public void setLastKeyboardHookEventRelativeTimeMillis(long lastKeyboardHookEventRelativeTimeMillis) {
        this.lastKeyboardHookEventRelativeTimeMillis = lastKeyboardHookEventRelativeTimeMillis;
    }

}

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
    private boolean inKeyboardHookEvent;

    @Override
    public Instant now() {
        if (inKeyboardHookEvent)
            return systemStartTime.plusMillis(lastKeyboardHookEventRelativeTimeMillis);
        return systemStartTime.plusMillis(ExtendedKernel32.INSTANCE.GetTickCount64());
    }

    public void setLastKeyboardHookEventRelativeTimeMillis(long lastKeyboardHookEventRelativeTimeMillis) {
        this.lastKeyboardHookEventRelativeTimeMillis = lastKeyboardHookEventRelativeTimeMillis;
        inKeyboardHookEvent = true;
    }

    public void keyboardHookEventHandled() {
        inKeyboardHookEvent = false;
    }

}

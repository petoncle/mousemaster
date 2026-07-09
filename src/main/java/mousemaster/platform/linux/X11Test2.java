package mousemaster.platform.linux;

import com.sun.jna.Pointer;

/**
 * Test X11 keyboard events with sync and better debugging
 */
public class X11Test2 {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("X11 Keyboard Test v2");
        System.out.println("After keyboard is grabbed, press some keys...");
        System.out.println("Press Escape to exit early\n");

        Pointer display = LibX11.INSTANCE.XOpenDisplay(null);
        if (display == null) {
            System.err.println("Failed to open X11 display");
            return;
        }
        System.out.println("Display opened");

        long rootWindow = LibX11.INSTANCE.XDefaultRootWindow(display);
        System.out.println("Root window: " + rootWindow);

        LibX11.INSTANCE.XSync(display, false);

        Thread.sleep(1000);

        System.out.println("\nGrabbing keyboard...");
        int grabResult = LibX11.INSTANCE.XGrabKeyboard(
            display,
            rootWindow,
            0,
            LibX11.GrabModeAsync,
            LibX11.GrabModeAsync,
            LibX11.CurrentTime
        );

        if (grabResult != LibX11.GrabSuccess) {
            System.err.println("Failed to grab keyboard: " + grabResult);
            LibX11.INSTANCE.XCloseDisplay(display);
            return;
        }

        System.out.println("Keyboard grabbed! Press keys now...\n");

        LibX11.INSTANCE.XSync(display, false);

        boolean running = true;
        int totalEvents = 0;
        long startTime = System.currentTimeMillis();

        while (running && (System.currentTimeMillis() - startTime < 15000)) {
            LibX11.INSTANCE.XSync(display, false);
            int pending = LibX11.INSTANCE.XPending(display);

            if (pending > 0) {
                System.out.println(">>> " + pending + " events pending");
            }

            while (LibX11.INSTANCE.XPending(display) > 0) {
                LibX11.XEvent event = new LibX11.XEvent();
                LibX11.INSTANCE.XNextEvent(display, event);
                totalEvents++;

                System.out.println("Event #" + totalEvents + " - Type: " + event.type);

                if (event.type == LibX11.KeyPress) {
                    LibX11.XKeyEvent keyEvent = event.getKeyEvent();
                    long keysym = LibX11.INSTANCE.XLookupKeysym(keyEvent, 0);
                    String keyString = LibX11.INSTANCE.XKeysymToString(keysym);

                    System.out.println("  KeyPress: " + keyString +
                        " (keycode=" + keyEvent.keycode +
                        ", state=" + keyEvent.state +
                        ", window=" + keyEvent.window + ")");

                    if ("Escape".equals(keyString)) {
                        System.out.println("  Escape pressed - exiting");
                        running = false;
                    }

                } else if (event.type == LibX11.KeyRelease) {
                    LibX11.XKeyEvent keyEvent = event.getKeyEvent();
                    long keysym = LibX11.INSTANCE.XLookupKeysym(keyEvent, 0);
                    String keyString = LibX11.INSTANCE.XKeysymToString(keysym);

                    System.out.println("  KeyRelease: " + keyString +
                        " (keycode=" + keyEvent.keycode + ")");
                }
            }

            Thread.sleep(10);
        }

        System.out.println("\n=========================");
        System.out.println("Total events received: " + totalEvents);

        LibX11.INSTANCE.XUngrabKeyboard(display, LibX11.CurrentTime);
        LibX11.INSTANCE.XSync(display, false);
        System.out.println("Keyboard ungrabbed");

        LibX11.INSTANCE.XCloseDisplay(display);
        System.out.println("Display closed");
    }
}

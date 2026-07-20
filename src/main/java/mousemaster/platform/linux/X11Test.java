package mousemaster.platform.linux;

import com.sun.jna.Pointer;

/**
 * Simple test to verify X11 event handling is working
 */
public class X11Test {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("X11 Event Test - Press keys to test detection...");

        Pointer display = LibX11.INSTANCE.XOpenDisplay(null);
        if (display == null) {
            System.err.println("Failed to open X11 display");
            return;
        }
        System.out.println("Display opened successfully");

        long rootWindow = LibX11.INSTANCE.XDefaultRootWindow(display);
        System.out.println("Root window: " + rootWindow);

        LibX11.INSTANCE.XSelectInput(display, rootWindow,
            LibX11.KeyPressMask | LibX11.KeyReleaseMask);
        System.out.println("XSelectInput called with mask: " +
            (LibX11.KeyPressMask | LibX11.KeyReleaseMask));

        LibX11.INSTANCE.XFlush(display);

        System.out.println("\nAttempting to grab keyboard...");
        int grabResult = LibX11.INSTANCE.XGrabKeyboard(display, rootWindow, 1,
            LibX11.GrabModeAsync, LibX11.GrabModeAsync, LibX11.CurrentTime);
        System.out.println("XGrabKeyboard result: " + grabResult);

        if (grabResult == LibX11.GrabSuccess) {
            System.out.println("Keyboard grabbed successfully!");
        } else {
            System.out.println("Failed to grab keyboard: " + grabResult);
        }

        LibX11.INSTANCE.XFlush(display);

        System.out.println("\nListening for events for 10 seconds...");
        long startTime = System.currentTimeMillis();
        int totalEvents = 0;

        while (System.currentTimeMillis() - startTime < 10000) {
            int pending = LibX11.INSTANCE.XPending(display);
            if (pending > 0) {
                System.out.println("Events pending: " + pending);
            }

            while (LibX11.INSTANCE.XPending(display) > 0) {
                LibX11.XEvent event = new LibX11.XEvent();
                LibX11.INSTANCE.XNextEvent(display, event);
                totalEvents++;

                System.out.println("Event type: " + event.type);

                if (event.type == LibX11.KeyPress) {
                    LibX11.XKeyEvent keyEvent = event.getKeyEvent();
                    long keysym = LibX11.INSTANCE.XLookupKeysym(keyEvent, 0);
                    String keyString = LibX11.INSTANCE.XKeysymToString(keysym);
                    System.out.println("KeyPress: " + keyString + " (keycode: " + keyEvent.keycode + ")");
                } else if (event.type == LibX11.KeyRelease) {
                    LibX11.XKeyEvent keyEvent = event.getKeyEvent();
                    long keysym = LibX11.INSTANCE.XLookupKeysym(keyEvent, 0);
                    String keyString = LibX11.INSTANCE.XKeysymToString(keysym);
                    System.out.println("KeyRelease: " + keyString + " (keycode: " + keyEvent.keycode + ")");
                }
            }

            Thread.sleep(10);
        }

        System.out.println("\nTotal events received: " + totalEvents);

        if (grabResult == LibX11.GrabSuccess) {
            LibX11.INSTANCE.XUngrabKeyboard(display, LibX11.CurrentTime);
            System.out.println("Keyboard ungrabbed");
        }

        LibX11.INSTANCE.XCloseDisplay(display);
        System.out.println("Display closed");
    }
}

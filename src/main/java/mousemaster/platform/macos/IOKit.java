package mousemaster.platform.macos;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

/**
 * IOKit, for the caps lock state. macOS sends the led report to the device the key came from,
 * and the virtual keyboard has no led, so the usage has to be turned into a state change here.
 */
public interface IOKit extends Library {

    IOKit INSTANCE = Native.load("IOKit", IOKit.class);

    int paramConnectType = 1;
    int capsLockStateSelector = 1;

    Pointer IOServiceMatching(String name);

    /** The main port is 0. Consumes the matching dictionary. */
    int IOServiceGetMatchingService(int mainPort, Pointer matching);

    int IOServiceOpen(int service, int owningTask, int type, IntByReference connect);

    int IOObjectRelease(int object);

    int IOHIDGetModifierLockState(int connect, int selector, byte[] state);

    int IOHIDSetModifierLockState(int connect, int selector, byte state);

}

package mousemaster.platform.macos;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

public interface CoreFoundation extends Library {

    CoreFoundation INSTANCE = Native.load("CoreFoundation", CoreFoundation.class);

    int utf8Encoding = 0x08000100;

    Pointer CFStringCreateWithCString(Pointer allocator, String string, int encoding);

    Pointer CFDictionaryGetValue(Pointer dictionary, Pointer key);

    boolean CFBooleanGetValue(Pointer value);

    int doubleType = 13;
    int intType = 9;

    boolean CFNumberGetValue(Pointer number, int type, double[] value);

    boolean CFNumberGetValue(Pointer number, int type, int[] value);

    long CFArrayGetCount(Pointer array);

    Pointer CFArrayGetValueAtIndex(Pointer array, long index);

    boolean CFStringGetCString(Pointer string, byte[] buffer, long size, int encoding);

    Pointer CFDataGetBytePtr(Pointer data);

    /** kCFPreferencesAnyHost is a global variable, not a function. */
    Pointer anyHost = NativeLibrary.getInstance("CoreFoundation")
                                   .getGlobalVariableAddress("kCFPreferencesAnyHost")
                                   .getPointer(0);

    /**
     * Reading another user's preference: running as root, the current user is root, whose
     * keyboard layout is not the one being typed on.
     */
    Pointer CFPreferencesCopyValue(Pointer key, Pointer applicationId, Pointer userName,
                                   Pointer hostName);

    Pointer CFRetain(Pointer ref);

    void CFRelease(Pointer ref);

}

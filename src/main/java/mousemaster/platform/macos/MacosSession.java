package mousemaster.platform.macos;

import com.sun.jna.Pointer;

/** The grab is global because mousemaster is root: it outlives this session otherwise. */
public final class MacosSession {

    private static final Pointer screenIsLocked =
            string("CGSSessionScreenIsLocked");
    private static final Pointer onConsole = string("kCGSSessionOnConsoleKey");

    private MacosSession() {
    }

    private static Pointer string(String value) {
        return CoreFoundation.INSTANCE.CFStringCreateWithCString(null, value,
                CoreFoundation.utf8Encoding);
    }

    public static boolean grabPaused() {
        Pointer session = CoreGraphics.INSTANCE.CGSessionCopyCurrentDictionary();
        // No session at all, which is a root daemon started before anyone logged in.
        if (session == null)
            return false;
        try {
            // Absent while unlocked, present and true while locked.
            if (booleanValue(session, screenIsLocked, false))
                return true;
            // False once another user has taken the console.
            return !booleanValue(session, onConsole, true);
        }
        finally {
            CoreFoundation.INSTANCE.CFRelease(session);
        }
    }

    private static boolean booleanValue(Pointer dictionary, Pointer key,
                                        boolean absentValue) {
        Pointer value = CoreFoundation.INSTANCE.CFDictionaryGetValue(dictionary, key);
        return value == null ? absentValue :
                CoreFoundation.INSTANCE.CFBooleanGetValue(value);
    }

}

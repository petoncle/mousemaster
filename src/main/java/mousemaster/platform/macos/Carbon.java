package mousemaster.platform.macos;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

public interface Carbon extends Library {

    Carbon INSTANCE = Native.load("Carbon", Carbon.class);

    /** Global variables, not functions. */
    NativeLibrary library = NativeLibrary.getInstance("Carbon");

    Pointer unicodeKeyLayoutData =
            library.getGlobalVariableAddress("kTISPropertyUnicodeKeyLayoutData")
                   .getPointer(0);

    Pointer inputSourceId =
            library.getGlobalVariableAddress("kTISPropertyInputSourceID").getPointer(0);

    int keyActionDown = 0;
    int translateNoDeadKeys = 1;

    Pointer TISCopyCurrentKeyboardInputSource();

    /** A null filter lists every enabled source, which is enough to find one by id. */
    Pointer TISCreateInputSourceList(Pointer properties, boolean includeAllInstalled);

    Pointer TISGetInputSourceProperty(Pointer inputSource, Pointer key);

    int LMGetKbdType();

    /** Array out parameters: JNA instantiates a pointer holder reflectively. */
    int UCKeyTranslate(Pointer keyLayout, short virtualKeyCode, short keyAction,
                       int modifierKeyState, int keyboardType, int options,
                       int[] deadKeyState, long maxStringLength,
                       long[] actualStringLength, char[] unicodeString);

}

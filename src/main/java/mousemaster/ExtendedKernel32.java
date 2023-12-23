package mousemaster;

import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;

public interface ExtendedKernel32 extends StdCallLibrary {
    ExtendedKernel32 INSTANCE = Native.load("kernel32", ExtendedKernel32.class);

    long GetTickCount64();
}
package mousemaster;

import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;

public interface Winmm extends StdCallLibrary {

    Winmm INSTANCE = Native.load("winmm", Winmm.class);

    int timeBeginPeriod(int uPeriod);

}

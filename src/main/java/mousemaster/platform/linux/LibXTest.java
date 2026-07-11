package mousemaster.platform.linux;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public interface LibXTest extends Library {
    LibXTest INSTANCE = Native.load("Xtst", LibXTest.class);

    // Bool is_press: 1 = press, 0 = release
    // delay: CurrentTime = 0
    int XTestFakeButtonEvent(Pointer display, int button, int isPress, long delay);
}

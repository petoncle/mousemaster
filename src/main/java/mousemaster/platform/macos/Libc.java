package mousemaster.platform.macos;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public interface Libc extends Library {

    Libc INSTANCE = Native.load("c", Libc.class);

    /** Ends the process without the C++ destructors that are Qt tearing itself down. */
    void _exit(int status);

    Pointer dispatch_queue_create(String label, Pointer attributes);

}

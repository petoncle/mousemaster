package mousemaster.platform.macos;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;

/**
 * Karabiner-DriverKit-VirtualHIDDevice client, built from the karabiner-driverkit crate.
 * Call order: driver_activated, register_device, grab, wait_key, release.
 */
public interface Driverkit extends Library {

    Driverkit INSTANCE = Native.load("driverkit", Driverkit.class);

    @Structure.FieldOrder({"value", "page", "code", "deviceHash"})
    class DKEvent extends Structure {
        public long value;
        public int page;
        public int code;
        public long deviceHash;

        public boolean isPress() {
            return value == 1;
        }

        public boolean isRelease() {
            return value == 0;
        }
    }

    boolean driver_activated();

    boolean register_device(String productKey);

    void list_keyboards();

    /** 0 on success, 1 when no device was registered. */
    int grab();

    /** Blocks. 1 when an event was written, 0 when the input was released. */
    int wait_key(DKEvent event);

    /** 0 on success, 1 on a bad usage page, 2 when the sink is not ready. */
    int send_key(DKEvent event);

    boolean is_sink_ready();

    /** Regrabs after a device was plugged in or the session changed. */
    boolean regrab_input();

    void release_input_only();

    void release();

}

package mousemaster.platform.linux;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic, protocol-agnostic JNA bindings for libwayland-client.so: connection
 * lifecycle and the wire-level proxy marshalling API. Protocol-specific requests
 * (e.g. zwlr_virtual_pointer_v1) are built on top of this in LibWlrVirtualPointer.
 */
public interface LibWaylandClient extends Library {

    LibWaylandClient INSTANCE = Native.load("wayland-client", LibWaylandClient.class);

    int WL_MARSHAL_FLAG_DESTROY = 1;

    // Core wl_display/wl_registry opcodes (wayland.xml), not specific to any extension.
    int WL_DISPLAY_GET_REGISTRY = 1; // signature "n"
    int WL_REGISTRY_BIND = 0;        // signature "usun" (special-cased dynamic bind)

    Pointer wl_display_connect(String name);
    void wl_display_disconnect(Pointer display);
    int wl_display_dispatch(Pointer display);
    int wl_display_roundtrip(Pointer display);
    int wl_display_flush(Pointer display);

    Pointer wl_proxy_marshal_array_flags(Pointer proxy, int opcode, Pointer interface_,
                                         int version, int flags, Pointer args);
    int wl_proxy_add_listener(Pointer proxy, Pointer implementation, Pointer data);
    void wl_proxy_destroy(Pointer proxy);
    int wl_proxy_get_version(Pointer proxy);

    @Structure.FieldOrder({"name", "signature", "types"})
    class WlMessage extends Structure {
        public String name;
        public String signature;
        public Pointer types;

        public WlMessage() {
            super();
        }

        public WlMessage(Pointer p) {
            super(p);
        }
    }

    @Structure.FieldOrder({"name", "version", "method_count", "methods", "event_count", "events"})
    class WlInterface extends Structure {
        public String name;
        public int version;
        public int method_count;
        public Pointer methods;
        public int event_count;
        public Pointer events;
    }

    interface WlRegistryGlobalCallback extends Callback {
        void invoke(Pointer data, Pointer registry, int name, String interfaceName, int version);
    }

    interface WlRegistryGlobalRemoveCallback extends Callback {
        void invoke(Pointer data, Pointer registry, int name);
    }

    @Structure.FieldOrder({"global", "globalRemove"})
    class WlRegistryListener extends Structure {
        public WlRegistryGlobalCallback global;
        public WlRegistryGlobalRemoveCallback globalRemove;
    }

    /**
     * Raw union wl_argument[] builder (8-byte slots on x86_64, since the union's
     * widest members are pointers). Every slot in a message's signature needs an
     * entry here, including the new_id slot native code fills in automatically.
     */
    class WlArgs {
        private static final int SLOT_SIZE = 8;
        private final Memory mem;
        private final List<Memory> stringBacking = new ArrayList<>();

        public WlArgs(int slotCount) {
            mem = new Memory((long) SLOT_SIZE * Math.max(slotCount, 1));
            mem.clear();
        }

        public WlArgs setUint(int slot, int value) {
            mem.setInt((long) slot * SLOT_SIZE, value);
            return this;
        }

        public WlArgs setInt(int slot, int value) {
            mem.setInt((long) slot * SLOT_SIZE, value);
            return this;
        }

        public WlArgs setFixed(int slot, int fixedValue) {
            mem.setInt((long) slot * SLOT_SIZE, fixedValue);
            return this;
        }

        public WlArgs setString(int slot, String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            Memory str = new Memory(bytes.length + 1L);
            str.write(0, bytes, 0, bytes.length);
            str.setByte(bytes.length, (byte) 0);
            stringBacking.add(str);
            mem.setPointer((long) slot * SLOT_SIZE, str);
            return this;
        }

        public WlArgs setObject(int slot, Pointer value) {
            mem.setPointer((long) slot * SLOT_SIZE, value);
            return this;
        }

        public Pointer pointer() {
            return mem;
        }
    }

    static int fixedFromInt(int value) {
        return value * 256;
    }

    static int fixedFromDouble(double value) {
        return (int) Math.round(value * 256.0);
    }

    static int fixedToInt(int fixedValue) {
        return fixedValue / 256;
    }

    static double fixedToDouble(int fixedValue) {
        return fixedValue / 256.0;
    }

    static Pointer globalInterfacePointer(String symbolName) {
        return NativeLibrary.getInstance("wayland-client").getGlobalVariableAddress(symbolName);
    }
}

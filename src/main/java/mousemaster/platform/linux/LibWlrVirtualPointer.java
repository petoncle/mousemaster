package mousemaster.platform.linux;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;

/**
 * Hand-rolled wl_interface/wl_message data for zwlr_virtual_pointer_manager_v1 and
 * zwlr_virtual_pointer_v1 (wlr-protocols, version 2 of each). Neither interface has
 * any events, so the "types" arrays below are populated for correctness/parity with
 * real wayland-scanner output but are never actually read by outbound marshalling.
 */
final class LibWlrVirtualPointer {

    static final int VP_OP_MOTION          = 0; // "uff"
    static final int VP_OP_MOTION_ABSOLUTE = 1; // "uuuuu"
    static final int VP_OP_BUTTON          = 2; // "uuu"  (unused, see WaylandMouse)
    static final int VP_OP_AXIS            = 3; // "uuf"  (unused)
    static final int VP_OP_FRAME           = 4; // ""
    static final int VP_OP_AXIS_SOURCE     = 5; // "u"    (unused)
    static final int VP_OP_AXIS_STOP       = 6; // "uu"   (unused)
    static final int VP_OP_AXIS_DISCRETE   = 7; // "uufi" (unused)
    static final int VP_OP_DESTROY         = 8; // ""

    static final int MGR_OP_CREATE_VIRTUAL_POINTER             = 0; // "?on"
    static final int MGR_OP_DESTROY                             = 1; // ""
    static final int MGR_OP_CREATE_VIRTUAL_POINTER_WITH_OUTPUT = 2; // "?o?on" (unused)

    static final LibWaylandClient.WlInterface ZWLR_VIRTUAL_POINTER_V1_INTERFACE =
            buildVirtualPointerV1Interface();
    static final LibWaylandClient.WlInterface ZWLR_VIRTUAL_POINTER_MANAGER_V1_INTERFACE =
            buildManagerInterface();

    // Must stay reachable for the process lifetime: iface.methods only retains the raw
    // native pointer to element 0, not the individual WlMessage Java objects, which are
    // what keep their name/signature string-backing Memory alive. Without these fields,
    // methods[1..] get GC'd once the builders below return, their string memory gets
    // freed and later reused by unrelated allocations, and native code ends up reading
    // corrupted method names/signatures out of the (still-contiguous) methods array.
    private static LibWaylandClient.WlMessage[] virtualPointerV1Methods;
    private static LibWaylandClient.WlMessage[] managerMethods;

    private LibWlrVirtualPointer() {
    }

    private static LibWaylandClient.WlMessage[] buildMessages(int count) {
        LibWaylandClient.WlMessage first = new LibWaylandClient.WlMessage();
        return (LibWaylandClient.WlMessage[]) first.toArray(count);
    }

    private static Pointer typesArray(Pointer... interfacePointers) {
        Memory mem = new Memory(8L * interfacePointers.length);
        for (int i = 0; i < interfacePointers.length; i++)
            mem.setPointer(8L * i, interfacePointers[i]);
        return mem;
    }

    private static LibWaylandClient.WlInterface buildVirtualPointerV1Interface() {
        LibWaylandClient.WlMessage[] methods = buildMessages(9);
        methods[VP_OP_MOTION].name = "motion";
        methods[VP_OP_MOTION].signature = "uff";
        methods[VP_OP_MOTION_ABSOLUTE].name = "motion_absolute";
        methods[VP_OP_MOTION_ABSOLUTE].signature = "uuuuu";
        methods[VP_OP_BUTTON].name = "button";
        methods[VP_OP_BUTTON].signature = "uuu";
        methods[VP_OP_AXIS].name = "axis";
        methods[VP_OP_AXIS].signature = "uuf";
        methods[VP_OP_FRAME].name = "frame";
        methods[VP_OP_FRAME].signature = "";
        methods[VP_OP_AXIS_SOURCE].name = "axis_source";
        methods[VP_OP_AXIS_SOURCE].signature = "u";
        methods[VP_OP_AXIS_STOP].name = "axis_stop";
        methods[VP_OP_AXIS_STOP].signature = "uu";
        methods[VP_OP_AXIS_DISCRETE].name = "axis_discrete";
        methods[VP_OP_AXIS_DISCRETE].signature = "uufi";
        methods[VP_OP_DESTROY].name = "destroy";
        methods[VP_OP_DESTROY].signature = "";
        for (LibWaylandClient.WlMessage m : methods) {
            m.types = Pointer.NULL;
            m.write();
        }

        LibWaylandClient.WlInterface iface = new LibWaylandClient.WlInterface();
        iface.name = "zwlr_virtual_pointer_v1";
        iface.version = 2;
        iface.method_count = methods.length;
        iface.methods = methods[0].getPointer();
        iface.event_count = 0;
        iface.events = Pointer.NULL;
        iface.write();
        virtualPointerV1Methods = methods;
        return iface;
    }

    private static LibWaylandClient.WlInterface buildManagerInterface() {
        Pointer wlSeatInterface = LibWaylandClient.globalInterfacePointer("wl_seat_interface");
        Pointer wlOutputInterface = LibWaylandClient.globalInterfacePointer("wl_output_interface");
        Pointer vp1Interface = ZWLR_VIRTUAL_POINTER_V1_INTERFACE.getPointer();

        LibWaylandClient.WlMessage[] methods = buildMessages(3);
        methods[MGR_OP_CREATE_VIRTUAL_POINTER].name = "create_virtual_pointer";
        methods[MGR_OP_CREATE_VIRTUAL_POINTER].signature = "?on";
        methods[MGR_OP_CREATE_VIRTUAL_POINTER].types = typesArray(wlSeatInterface, vp1Interface);
        methods[MGR_OP_DESTROY].name = "destroy";
        methods[MGR_OP_DESTROY].signature = "";
        methods[MGR_OP_DESTROY].types = Pointer.NULL;
        methods[MGR_OP_CREATE_VIRTUAL_POINTER_WITH_OUTPUT].name = "create_virtual_pointer_with_output";
        methods[MGR_OP_CREATE_VIRTUAL_POINTER_WITH_OUTPUT].signature = "?o?on";
        methods[MGR_OP_CREATE_VIRTUAL_POINTER_WITH_OUTPUT].types =
                typesArray(wlSeatInterface, wlOutputInterface, vp1Interface);
        for (LibWaylandClient.WlMessage m : methods)
            m.write();

        LibWaylandClient.WlInterface iface = new LibWaylandClient.WlInterface();
        iface.name = "zwlr_virtual_pointer_manager_v1";
        iface.version = 2;
        iface.method_count = methods.length;
        iface.methods = methods[0].getPointer();
        iface.event_count = 0;
        iface.events = Pointer.NULL;
        iface.write();
        managerMethods = methods;
        return iface;
    }
}

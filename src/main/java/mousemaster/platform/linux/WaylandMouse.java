package mousemaster.platform.linux;

import com.sun.jna.Pointer;
import mousemaster.Rectangle;
import mousemaster.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Wayland implementation of MouseController, using the wlr-protocols
 * zwlr_virtual_pointer_v1 extension for cursor motion. Only supported on
 * wlroots-based compositors (Hyprland, Sway) that advertise
 * zwlr_virtual_pointer_manager_v1 - see markUnavailable() for the fallback
 * behavior on compositors that don't (GNOME, KDE Plasma).
 */
public class WaylandMouse extends LinuxMouse {

    private static final Logger logger = LoggerFactory.getLogger(WaylandMouse.class);

    // MouseManager passes wheel delta in Windows' WHEEL_DELTA convention (120 units = one
    // notch, see WindowsMouseController), since that's the shared unit the velocity config
    // is tuned against. uinput's REL_WHEEL has no fractional-notch concept - each event is
    // a discrete notch - so accumulate sub-notch deltas here and only fire once a full
    // notch's worth has built up, carrying the remainder forward (same pattern MouseManager
    // itself uses for cursor movement's deltaDistanceX/Y).
    private static final double WHEEL_DELTA = 120;

    private final LinuxScreens screens;
    private final int uinputMouseFd;
    private final LibWaylandClient.WlRegistryListener registryListener;
    private double verticalWheelAccumulator;
    private double horizontalWheelAccumulator;

    private Pointer display;
    private Pointer registry;
    private Pointer seatProxy;
    private Pointer managerProxy;
    private Pointer virtualPointerProxy;
    private boolean protocolAvailable;

    private boolean managerFound;
    private int managerName;
    private int managerVersion;
    private boolean seatFound;
    private int seatName;
    private int seatVersion;

    private volatile Integer lastX;
    private volatile Integer lastY;

    @Override
    public Integer lastSyntheticX() {
        return lastX;
    }

    @Override
    public Integer lastSyntheticY() {
        return lastY;
    }

    public WaylandMouse(LinuxScreens screens) {
        this.screens = screens;
        this.uinputMouseFd = LibUinput.createMouseDevice();
        this.registryListener = new LibWaylandClient.WlRegistryListener();
        registryListener.global = this::onGlobal;
        registryListener.globalRemove = this::onGlobalRemove;
        try {
            connectAndBootstrap();
        } catch (Throwable e) {
            // Catches Throwable, not just Exception: a failure to load libwayland-client.so
            // surfaces as UnsatisfiedLinkError/NoClassDefFoundError, which are Errors, not
            // Exceptions - without this, that would crash the whole app instead of just
            // disabling cursor movement.
            logger.error("Failed to initialize Wayland virtual pointer, cursor movement disabled", e);
            protocolAvailable = false;
        }
    }

    private void connectAndBootstrap() {
        display = LibWaylandClient.INSTANCE.wl_display_connect(null);
        if (display == null) {
            markUnavailable("wl_display_connect failed (no $WAYLAND_DISPLAY socket)");
            return;
        }

        LibWaylandClient.WlArgs getRegistryArgs = new LibWaylandClient.WlArgs(1);
        registry = LibWaylandClient.INSTANCE.wl_proxy_marshal_array_flags(display,
                LibWaylandClient.WL_DISPLAY_GET_REGISTRY,
                LibWaylandClient.globalInterfacePointer("wl_registry_interface"),
                LibWaylandClient.INSTANCE.wl_proxy_get_version(display), 0, getRegistryArgs.pointer());

        registryListener.write();
        LibWaylandClient.INSTANCE.wl_proxy_add_listener(registry, registryListener.getPointer(), null);
        LibWaylandClient.INSTANCE.wl_display_roundtrip(display); // collects the initial batch of `global` events

        if (!managerFound) {
            markUnavailable("compositor did not advertise zwlr_virtual_pointer_manager_v1");
            return;
        }
        managerProxy = bind(managerName, managerVersion,
                LibWlrVirtualPointer.ZWLR_VIRTUAL_POINTER_MANAGER_V1_INTERFACE.getPointer(),
                "zwlr_virtual_pointer_manager_v1");
        if (seatFound)
            seatProxy = bind(seatName, seatVersion,
                    LibWaylandClient.globalInterfacePointer("wl_seat_interface"), "wl_seat");

        virtualPointerProxy = createVirtualPointer();
        LibWaylandClient.INSTANCE.wl_display_flush(display);
        protocolAvailable = virtualPointerProxy != null;
        logger.info("Wayland virtual pointer bootstrapped (manager v{}, seat {})",
                managerVersion, seatProxy != null ? "bound" : "none");
    }

    private void onGlobal(Pointer data, Pointer registryProxy, int name, String interfaceName, int version) {
        if ("zwlr_virtual_pointer_manager_v1".equals(interfaceName)) {
            managerName = name;
            managerVersion = Math.min(version, 2);
            managerFound = true;
        } else if ("wl_seat".equals(interfaceName) && !seatFound) {
            seatName = name;
            seatVersion = version;
            seatFound = true;
        }
    }

    private void onGlobalRemove(Pointer data, Pointer registryProxy, int name) {
    }

    private Pointer bind(int name, int version, Pointer interfacePointer, String interfaceName) {
        LibWaylandClient.WlArgs args = new LibWaylandClient.WlArgs(4);
        args.setUint(0, name);
        args.setString(1, interfaceName);
        args.setUint(2, version);
        return LibWaylandClient.INSTANCE.wl_proxy_marshal_array_flags(registry,
                LibWaylandClient.WL_REGISTRY_BIND, interfacePointer, version, 0, args.pointer());
    }

    private Pointer createVirtualPointer() {
        LibWaylandClient.WlArgs args = new LibWaylandClient.WlArgs(2);
        if (seatProxy != null)
            args.setObject(0, seatProxy);
        return LibWaylandClient.INSTANCE.wl_proxy_marshal_array_flags(managerProxy,
                LibWlrVirtualPointer.MGR_OP_CREATE_VIRTUAL_POINTER,
                LibWlrVirtualPointer.ZWLR_VIRTUAL_POINTER_V1_INTERFACE.getPointer(),
                managerVersion, 0, args.pointer());
    }

    private void markUnavailable(String reason) {
        protocolAvailable = false;
        String desktop = System.getenv("XDG_CURRENT_DESKTOP");
        logger.warn("Wayland cursor movement unavailable: {}. zwlr_virtual_pointer_v1 is supported by " +
                "wlroots-based compositors (Hyprland, Sway) but not GNOME or KDE Plasma. Cursor movement " +
                "will be disabled for this session; keyboard capture and hint navigation are unaffected.{}",
                reason, desktop != null ? " Detected desktop: " + desktop + "." : "");
    }

    private boolean ensureAvailable() {
        return protocolAvailable;
    }

    // ---- MouseController: cursor motion, via zwlr_virtual_pointer_v1 ----

    @Override
    public void beginMove() {
    }

    @Override
    public void endMove() {
        if (protocolAvailable)
            LibWaylandClient.INSTANCE.wl_display_flush(display);
    }

    @Override
    public void moveBy(boolean xForward, double dx, boolean yForward, double dy) {
        if (!ensureAvailable() || (dx == 0 && dy == 0))
            return;
        double signedDx = xForward ? dx : -dx;
        double signedDy = yForward ? dy : -dy;
        LibWaylandClient.WlArgs args = new LibWaylandClient.WlArgs(3);
        args.setUint(0, (int) System.currentTimeMillis());
        args.setFixed(1, LibWaylandClient.fixedFromDouble(signedDx));
        args.setFixed(2, LibWaylandClient.fixedFromDouble(signedDy));
        marshalPointerRequest(LibWlrVirtualPointer.VP_OP_MOTION, args.pointer());
        frame();
        LibWaylandClient.INSTANCE.wl_display_flush(display);
        if (lastX != null && lastY != null) {
            lastX += (int) Math.round(signedDx);
            lastY += (int) Math.round(signedDy);
        }
    }

    @Override
    public void synchronousMoveTo(int x, int y) {
        if (!ensureAvailable())
            return;
        Rectangle bounds = screenBounds();
        LibWaylandClient.WlArgs args = new LibWaylandClient.WlArgs(5);
        args.setUint(0, (int) System.currentTimeMillis());
        args.setUint(1, x - bounds.x());
        args.setUint(2, y - bounds.y());
        args.setUint(3, bounds.width());
        args.setUint(4, bounds.height());
        marshalPointerRequest(LibWlrVirtualPointer.VP_OP_MOTION_ABSOLUTE, args.pointer());
        frame();
        LibWaylandClient.INSTANCE.wl_display_flush(display);
        lastX = x;
        lastY = y;
    }

    // Uncached on purpose, matching how ScreenManager/HintManager/GridManager already
    // call Screens.findScreens() uncached at similar frequency elsewhere in the app.
    private Rectangle screenBounds() {
        Set<Screen> allScreens = screens.findScreens();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Screen screen : allScreens) {
            Rectangle r = screen.rectangle();
            minX = Math.min(minX, r.x());
            minY = Math.min(minY, r.y());
            maxX = Math.max(maxX, r.x() + r.width());
            maxY = Math.max(maxY, r.y() + r.height());
        }
        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    private void marshalPointerRequest(int opcode, Pointer argsPointer) {
        LibWaylandClient.INSTANCE.wl_proxy_marshal_array_flags(virtualPointerProxy, opcode, Pointer.NULL,
                LibWaylandClient.INSTANCE.wl_proxy_get_version(virtualPointerProxy), 0, argsPointer);
    }

    private void frame() {
        marshalPointerRequest(LibWlrVirtualPointer.VP_OP_FRAME, Pointer.NULL);
    }

    // ---- MouseController: buttons/wheel ----
    //
    // Route A (chosen over adding button/axis/frame calls to the Wayland protocol above):
    // clicks and scroll go through the kernel uinput mouse device (LibUinput.createMouseDevice(),
    // already built and unused elsewhere) instead of zwlr_virtual_pointer_v1's own button/axis
    // opcodes. That device's own docstring already describes it as working on "both X11 and
    // native Wayland", and reusing it is far less new/risky code than hand-marshalling three more
    // wire opcodes. This mirrors how keyboard input already works on Linux (evdev capture + uinput
    // injection, independent of display server) - the deliberate trade-off is that WaylandMouse
    // ends up using two transports (Wayland wire protocol for motion, kernel uinput for clicks and
    // wheel), both of which are ultimately resolved by the same compositor.

    @Override
    public void pressLeft() {
        uinputButton(LibUinput.BTN_LEFT, 1);
    }

    @Override
    public void releaseLeft() {
        uinputButton(LibUinput.BTN_LEFT, 0);
    }

    @Override
    public void pressMiddle() {
        uinputButton(LibUinput.BTN_MIDDLE, 1);
    }

    @Override
    public void releaseMiddle() {
        uinputButton(LibUinput.BTN_MIDDLE, 0);
    }

    @Override
    public void pressRight() {
        uinputButton(LibUinput.BTN_RIGHT, 1);
    }

    @Override
    public void releaseRight() {
        uinputButton(LibUinput.BTN_RIGHT, 0);
    }

    @Override
    public void wheelVerticallyBy(boolean forward, double delta) {
        verticalWheelAccumulator += delta;
        int notches = (int) (verticalWheelAccumulator / WHEEL_DELTA);
        if (notches <= 0)
            return;
        verticalWheelAccumulator -= notches * WHEEL_DELTA;
        uinputWheel(LibUinput.REL_WHEEL, forward ? 1 : -1, notches);
    }

    @Override
    public void wheelHorizontallyBy(boolean forward, double delta) {
        horizontalWheelAccumulator += delta;
        int notches = (int) (horizontalWheelAccumulator / WHEEL_DELTA);
        if (notches <= 0)
            return;
        horizontalWheelAccumulator -= notches * WHEEL_DELTA;
        uinputWheel(LibUinput.REL_HWHEEL, forward ? 1 : -1, notches);
    }

    private void uinputButton(int code, int value) {
        LibUinput.writeInputEvent(uinputMouseFd, LibUinput.EV_KEY, code, value);
        LibUinput.writeInputEvent(uinputMouseFd, LibUinput.EV_SYN, LibUinput.SYN_REPORT, 0);
    }

    private void uinputWheel(int axisCode, int sign, int notches) {
        for (int i = 0; i < notches; i++)
            LibUinput.writeInputEvent(uinputMouseFd, LibUinput.EV_REL, axisCode, sign);
        LibUinput.writeInputEvent(uinputMouseFd, LibUinput.EV_SYN, LibUinput.SYN_REPORT, 0);
    }

    // ---- MouseController: cursor visibility ----
    //
    // Permanent scope boundary, not a bug to fix later: zwlr_virtual_pointer_v1 has no
    // cursor-image request. Wayland cursor sprites are normally controlled via
    // wl_pointer.set_cursor, which requires holding pointer focus over one of the client's
    // own surfaces - a pure input-injection virtual pointer has no surface and no pointer
    // focus of its own, so there is no way to hide/change the system cursor from here.

    @Override
    public void showCursor() {
        logger.debug("showCursor() is a no-op under Wayland - see WaylandMouse class comment");
    }

    @Override
    public void hideCursor() {
        logger.debug("hideCursor() is a no-op under Wayland - see WaylandMouse class comment");
    }

    @Override
    public void destroy() {
        if (virtualPointerProxy != null)
            destroyRemoteObject(virtualPointerProxy, LibWlrVirtualPointer.VP_OP_DESTROY);
        if (managerProxy != null)
            destroyRemoteObject(managerProxy, LibWlrVirtualPointer.MGR_OP_DESTROY);
        if (seatProxy != null)
            LibWaylandClient.INSTANCE.wl_proxy_destroy(seatProxy);
        if (registry != null)
            LibWaylandClient.INSTANCE.wl_proxy_destroy(registry);
        if (display != null)
            LibWaylandClient.INSTANCE.wl_display_disconnect(display);
        LibUinput.destroyDevice(uinputMouseFd);
    }

    private void destroyRemoteObject(Pointer proxy, int destroyOpcode) {
        LibWaylandClient.INSTANCE.wl_proxy_marshal_array_flags(proxy, destroyOpcode, Pointer.NULL,
                LibWaylandClient.INSTANCE.wl_proxy_get_version(proxy),
                LibWaylandClient.WL_MARSHAL_FLAG_DESTROY, Pointer.NULL);
    }
}

package mousemaster.platform.windows;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import mousemaster.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Hands the on-screen desktop to {@link WindowsZoomRenderer} with DXGI Desktop Duplication,
 * texture to texture, never through the CPU. Windows marked WDA_EXCLUDEFROMCAPTURE are left
 * out of the frame.
 */
final class WindowsDesktopDuplication {

    private static final Logger logger =
            LoggerFactory.getLogger(WindowsDesktopDuplication.class);

    private static final Guid.IID IID_IDXGIFactory1 =
            new Guid.IID("770aae78-f26f-4dba-a829-253c83d1b387");
    private static final Guid.IID IID_IDXGIOutput1 =
            new Guid.IID("00cddea8-939b-4b83-a340-a685226666cc");
    private static final Guid.IID IID_ID3D11Texture2D =
            new Guid.IID("6f15aaf2-d208-4e89-9ab4-489535d34f9c");

    // Vtable indices (stable COM ABI). IUnknown: 0 QueryInterface, 1 AddRef, 2 Release.
    private static final int RELEASE = 2;
    private static final int IDXGIFACTORY1_ENUMADAPTERS1 = 12;
    private static final int IDXGIADAPTER_ENUMOUTPUTS = 7;
    private static final int IDXGIOUTPUT_GETDESC = 7;
    private static final int IDXGIOUTPUT1_DUPLICATEOUTPUT = 22;
    private static final int IDXGIOUTPUTDUPLICATION_GETDESC = 7;
    private static final int IDXGIOUTPUTDUPLICATION_ACQUIRENEXTFRAME = 8;
    private static final int IDXGIOUTPUTDUPLICATION_RELEASEFRAME = 14;
    private static final int ID3D11DEVICECONTEXT_COPYRESOURCE = 47;

    private static final int D3D_DRIVER_TYPE_UNKNOWN = 0;
    private static final int D3D11_SDK_VERSION = 7;
    private static final int D3D11_CREATE_DEVICE_BGRA_SUPPORT = 0x20;

    private static final int DXGI_FORMAT_B8G8R8A8_UNORM = 87;
    private static final int DXGI_MODE_ROTATION_UNSPECIFIED = 0;
    private static final int DXGI_MODE_ROTATION_IDENTITY = 1;

    private static final int S_OK = 0;
    private static final int DXGI_ERROR_NOT_FOUND = 0x887A0002;
    private static final int DXGI_ERROR_WAIT_TIMEOUT = 0x887A0027;

    private static final int FRAME_INFO_SIZE = 48; // DXGI_OUTDUPL_FRAME_INFO, unread
    private static final int ACQUIRE_TIMEOUT_MILLIS = 60;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    private interface D3D11 extends Library {
        D3D11 INSTANCE = Native.load("d3d11", D3D11.class);

        HRESULT D3D11CreateDevice(Pointer adapter, int driverType, Pointer software,
                                  int flags, Pointer featureLevels, int numFeatureLevels,
                                  int sdkVersion, PointerByReference device,
                                  IntByReference featureLevel, PointerByReference context);
    }

    private interface Dxgi extends Library {
        Dxgi INSTANCE = Native.load("dxgi", Dxgi.class);

        HRESULT CreateDXGIFactory1(Guid.IID riid, PointerByReference factory);
    }

    private Pointer device;
    private Pointer context;
    private Pointer duplication;
    private Rectangle outputBounds;
    private boolean copied;
    private boolean unavailable;
    private long retryAtNanos;

    Pointer device() {
        return device;
    }

    Pointer context() {
        return context;
    }

    /** The output the duplication covers, in desktop coordinates. */
    Rectangle outputBounds() {
        return outputBounds;
    }

    /** Makes the next copy wait for a new frame instead of reporting the screen unchanged. */
    void discardFrame() {
        copied = false;
    }

    boolean ensureInitialized(Rectangle bounds) {
        if (unavailable || System.nanoTime() < retryAtNanos)
            return false;
        try {
            if (duplication != null && !outputBounds.contains(bounds))
                releaseDuplication();
            if (duplication == null)
                initialize(bounds);
            return true;
        }
        catch (Throwable e) {
            logger.debug("Desktop Duplication unavailable: " + e.getMessage());
            releaseDuplication();
            // Enumerating costs a device creation, and a display that cannot be duplicated
            // stays that way until it is reconfigured: not worth retrying every frame.
            retryAtNanos = System.nanoTime() + RETRY_DELAY.toNanos();
            // A lost duplication comes back on the next call; a missing DLL does not.
            if (e instanceof UnsatisfiedLinkError || e instanceof NoClassDefFoundError)
                unavailable = true;
            return false;
        }
    }

    /** False means the screen has not changed and destination still holds the last frame. */
    boolean copyFrameInto(Rectangle bounds, Pointer destination) {
        if (!ensureInitialized(bounds))
            return false;
        try {
            PointerByReference resourceOut = new PointerByReference();
            // Only the first frame is worth waiting for; without it there is nothing to show.
            HRESULT hr = call(duplication, IDXGIOUTPUTDUPLICATION_ACQUIRENEXTFRAME,
                    copied ? 0 : ACQUIRE_TIMEOUT_MILLIS, new Memory(FRAME_INFO_SIZE),
                    resourceOut);
            if (hr.intValue() == DXGI_ERROR_WAIT_TIMEOUT)
                return false;
            check(hr, "AcquireNextFrame");
            Pointer resource = resourceOut.getValue();
            try {
                Pointer texture = queryInterface(resource, IID_ID3D11Texture2D);
                callVoid(context, ID3D11DEVICECONTEXT_COPYRESOURCE, destination, texture);
                release(texture);
                copied = true;
                return true;
            }
            finally {
                release(resource);
                call(duplication, IDXGIOUTPUTDUPLICATION_RELEASEFRAME);
            }
        }
        catch (Throwable e) {
            logger.debug("Desktop Duplication frame copy failed: " + e.getMessage());
            releaseDuplication();
            return false;
        }
    }

    private void initialize(Rectangle bounds) {
        PointerByReference factoryOut = new PointerByReference();
        check(Dxgi.INSTANCE.CreateDXGIFactory1(IID_IDXGIFactory1, factoryOut),
                "CreateDXGIFactory1");
        Pointer factory = factoryOut.getValue();
        Pointer adapter = null;
        Pointer output = null;
        try {
            for (int adapterIndex = 0; output == null; adapterIndex++) {
                PointerByReference adapterOut = new PointerByReference();
                HRESULT hr = call(factory, IDXGIFACTORY1_ENUMADAPTERS1, adapterIndex,
                        adapterOut);
                if (hr.intValue() == DXGI_ERROR_NOT_FOUND)
                    throw new IllegalStateException("no output covers " + bounds);
                check(hr, "EnumAdapters1");
                adapter = adapterOut.getValue();
                output = findOutput(adapter, bounds);
                if (output == null) {
                    release(adapter);
                    adapter = null;
                }
            }
            createDevice(adapter);
            Pointer output1 = queryInterface(output, IID_IDXGIOutput1);
            PointerByReference duplicationOut = new PointerByReference();
            HRESULT hr = call(output1, IDXGIOUTPUT1_DUPLICATEOUTPUT, device,
                    duplicationOut);
            release(output1);
            check(hr, "DuplicateOutput");
            duplication = duplicationOut.getValue();
            OutduplDesc desc = new OutduplDesc();
            callVoid(duplication, IDXGIOUTPUTDUPLICATION_GETDESC, desc.getPointer());
            desc.read();
            // An HDR desktop duplicates as float16, which the sample texture cannot be
            // copied from. Refusing here leaves the zoom off rather than unmagnified.
            if (desc.format != DXGI_FORMAT_B8G8R8A8_UNORM)
                throw new IllegalStateException("desktop format " + desc.format);
            logger.debug("Initialized Desktop Duplication on " + outputBounds);
        }
        finally {
            release(output);
            release(adapter);
            release(factory);
        }
    }

    /** The adapter's output covering bounds, or null. Also sets outputBounds. */
    private Pointer findOutput(Pointer adapter, Rectangle bounds) {
        for (int outputIndex = 0; ; outputIndex++) {
            PointerByReference outputOut = new PointerByReference();
            HRESULT hr = call(adapter, IDXGIADAPTER_ENUMOUTPUTS, outputIndex, outputOut);
            if (hr.intValue() == DXGI_ERROR_NOT_FOUND)
                return null;
            check(hr, "EnumOutputs");
            Pointer output = outputOut.getValue();
            OutputDesc desc = new OutputDesc();
            check(call(output, IDXGIOUTPUT_GETDESC, desc.getPointer()), "GetDesc");
            desc.read();
            Rectangle rectangle = new Rectangle(desc.left, desc.top,
                    desc.right - desc.left, desc.bottom - desc.top);
            // A rotated output duplicates in the unrotated orientation.
            if (desc.attachedToDesktop != 0 &&
                (desc.rotation == DXGI_MODE_ROTATION_IDENTITY ||
                 desc.rotation == DXGI_MODE_ROTATION_UNSPECIFIED) &&
                rectangle.contains(bounds)) {
                outputBounds = rectangle;
                return output;
            }
            release(output);
        }
    }

    /** The default adapter of a hybrid-GPU laptop often drives no output. */
    private void createDevice(Pointer adapter) {
        PointerByReference deviceOut = new PointerByReference();
        PointerByReference contextOut = new PointerByReference();
        // An explicit adapter requires the driver type to be UNKNOWN.
        check(D3D11.INSTANCE.D3D11CreateDevice(adapter, D3D_DRIVER_TYPE_UNKNOWN,
                Pointer.NULL, D3D11_CREATE_DEVICE_BGRA_SUPPORT, Pointer.NULL, 0,
                D3D11_SDK_VERSION, deviceOut, null, contextOut), "D3D11CreateDevice");
        device = deviceOut.getValue();
        context = contextOut.getValue();
    }

    private void releaseDuplication() {
        release(duplication);
        duplication = null;
        outputBounds = null;
        copied = false;
        // The device goes with it: it can only duplicate an output of the adapter it was
        // created on, and the next output may be on another one.
        release(context);
        context = null;
        release(device);
        device = null;
    }

    /** Exposes {@code _invokeNativeObject}, which is protected to Unknown's subclasses. */
    private static final class Com extends Unknown {
        Com(Pointer p) {
            super(p);
        }

        HRESULT hr(int vtableIndex, Object... args) {
            return (HRESULT) _invokeNativeObject(vtableIndex, prepend(args), HRESULT.class);
        }

        void voidHr(int vtableIndex, Object... args) {
            _invokeNativeObject(vtableIndex, prepend(args), Integer.class);
        }

        Object invoke(int vtableIndex, Class<?> returnType, Object... args) {
            return _invokeNativeObject(vtableIndex, prepend(args), returnType);
        }

        private Object[] prepend(Object[] args) {
            Object[] all = new Object[args.length + 1];
            all[0] = getPointer();
            System.arraycopy(args, 0, all, 1, args.length);
            return all;
        }
    }

    static Pointer queryInterface(Pointer iface, Guid.IID iid) {
        PointerByReference out = new PointerByReference();
        check(new Com(iface).hr(0, iid, out), "QueryInterface");
        return out.getValue();
    }

    static HRESULT call(Pointer iface, int vtableIndex, Object... args) {
        return new Com(iface).hr(vtableIndex, args);
    }

    static Object invoke(Pointer iface, int vtableIndex, Class<?> returnType,
                         Object... args) {
        return new Com(iface).invoke(vtableIndex, returnType, args);
    }

    static void callVoid(Pointer iface, int vtableIndex, Object... args) {
        new Com(iface).voidHr(vtableIndex, args);
    }

    static void release(Pointer iface) {
        if (iface != null)
            new Com(iface).voidHr(RELEASE);
    }

    static void check(HRESULT hr, String what) {
        if (hr.intValue() != S_OK)
            throw new IllegalStateException(
                    what + " failed: 0x" + Integer.toHexString(hr.intValue()));
    }

    public static class OutputDesc extends Structure {
        public byte[] deviceName = new byte[64]; // WCHAR[32], unused
        public int left, top, right, bottom;     // RECT DesktopCoordinates inlined
        public int attachedToDesktop;
        public int rotation;
        public Pointer monitor;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("deviceName", "left", "top", "right", "bottom",
                    "attachedToDesktop", "rotation", "monitor");
        }
    }

    public static class OutduplDesc extends Structure {
        public int width, height;                  // DXGI_MODE_DESC inlined
        public int refreshRateNumerator, refreshRateDenominator;
        public int format;
        public int scanlineOrdering, scaling;
        public int rotation;
        public int desktopImageInSystemMemory;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("width", "height", "refreshRateNumerator",
                    "refreshRateDenominator", "format", "scanlineOrdering", "scaling",
                    "rotation", "desktopImageInSystemMemory");
        }
    }

    public static class Texture2DDesc extends Structure {
        public int width, height, mipLevels, arraySize, format;
        public int sampleCount, sampleQuality; // DXGI_SAMPLE_DESC inlined
        public int usage, bindFlags, cpuAccessFlags, miscFlags;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("width", "height", "mipLevels", "arraySize", "format",
                    "sampleCount", "sampleQuality", "usage", "bindFlags",
                    "cpuAccessFlags", "miscFlags");
        }
    }

}

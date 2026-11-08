package mousemaster.platform.windows;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.PointerByReference;
import mousemaster.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.LockSupport;

import static mousemaster.platform.windows.WindowsDesktopDuplication.call;
import static mousemaster.platform.windows.WindowsDesktopDuplication.check;
import static mousemaster.platform.windows.WindowsDesktopDuplication.out;
import static mousemaster.platform.windows.WindowsDesktopDuplication.queryInterface;
import static mousemaster.platform.windows.WindowsDesktopDuplication.release;
import static mousemaster.platform.windows.WindowsDesktopDuplication.DXGI_FORMAT_B8G8R8A8_UNORM;
import static mousemaster.platform.windows.WindowsDesktopDuplication.DXGI_FORMAT_R16G16B16A16_FLOAT;
import static mousemaster.platform.windows.WindowsDesktopDuplication.IID_ID3D11Texture2D;

/**
 * Captures with Windows Graphics Capture, which leaves out the cursor even where the GPU does not
 * draw it: Desktop Duplication then shows it. Each capture starts a session, whose first frame is
 * the desktop as it is.
 */
final class WindowsGraphicsCapture extends WindowsDesktopFrameCapture {

    private static final Logger logger = LoggerFactory.getLogger(WindowsGraphicsCapture.class);

    private interface Combase extends Library {
        Combase INSTANCE = Native.load("combase", Combase.class);

        HRESULT RoGetActivationFactory(Pointer activatableClassId, Guid.IID iid,
                                       PointerByReference factory);

        HRESULT WindowsCreateString(WString sourceString, int length, PointerByReference string);

        HRESULT WindowsDeleteString(Pointer string);
    }

    private static final String GRAPHICS_CAPTURE_ITEM = "Windows.Graphics.Capture.GraphicsCaptureItem";
    private static final String GRAPHICS_CAPTURE_SESSION =
            "Windows.Graphics.Capture.GraphicsCaptureSession";
    private static final String DIRECT3D11_CAPTURE_FRAME_POOL =
            "Windows.Graphics.Capture.Direct3D11CaptureFramePool";
    private static final String API_INFORMATION = "Windows.Foundation.Metadata.ApiInformation";

    private static final Guid.IID IID_IDXGIDevice =
            new Guid.IID("54ec77fa-1377-44e6-8c32-88fd5f44c84c");
    private static final Guid.IID IID_IGraphicsCaptureItemInterop =
            new Guid.IID("3628e81b-3cac-4c60-b7f4-23ce0e0c3356");
    private static final Guid.IID IID_IGraphicsCaptureItem =
            new Guid.IID("79c3f95b-31f7-4ec2-a464-632ef5d30760");
    private static final Guid.IID IID_IGraphicsCaptureSessionStatics =
            new Guid.IID("2224a540-5974-49aa-b232-0882536f4cb5");
    private static final Guid.IID IID_IGraphicsCaptureSession2 =
            new Guid.IID("2c39ae40-7d2e-5044-804e-8b6799d4cf9e");
    private static final Guid.IID IID_IGraphicsCaptureSession3 =
            new Guid.IID("f2cdd966-22ae-5ea1-9596-3a289344c3be");
    private static final Guid.IID IID_IDirect3D11CaptureFramePoolStatics2 =
            new Guid.IID("589b103f-6bbc-5df5-a991-02e28b3b66d5");
    private static final Guid.IID IID_IDirect3DDxgiInterfaceAccess =
            new Guid.IID("a9b3d012-3df2-4ee3-b8d1-8695f457d3c1");
    private static final Guid.IID IID_IClosable =
            new Guid.IID("30d5a829-7fa4-4026-83bb-d75bae4ea99e");
    private static final Guid.IID IID_IApiInformationStatics =
            new Guid.IID("997439fe-f681-4a11-b416-c13a47e8ba36");

    // Vtable indices. IInspectable: 3 GetIids, 4 GetRuntimeClassName, 5 GetTrustLevel.
    private static final int IGRAPHICSCAPTUREITEMINTEROP_CREATEFORMONITOR = 4;
    private static final int IGRAPHICSCAPTURESESSIONSTATICS_ISSUPPORTED = 6;
    private static final int IGRAPHICSCAPTURESESSION_STARTCAPTURE = 6;
    private static final int IGRAPHICSCAPTURESESSION2_PUT_ISCURSORCAPTUREENABLED = 7;
    private static final int IGRAPHICSCAPTURESESSION3_PUT_ISBORDERREQUIRED = 7;
    private static final int IDIRECT3D11CAPTUREFRAMEPOOLSTATICS2_CREATEFREETHREADED = 6;
    private static final int IDIRECT3D11CAPTUREFRAMEPOOL_TRYGETNEXTFRAME = 7;
    private static final int IDIRECT3D11CAPTUREFRAMEPOOL_CREATECAPTURESESSION = 10;
    private static final int IDIRECT3D11CAPTUREFRAME_GET_SURFACE = 6;
    private static final int IDIRECT3DDXGIINTERFACEACCESS_GETINTERFACE = 3;
    private static final int ICLOSABLE_CLOSE = 6;
    private static final int IAPIINFORMATIONSTATICS_ISPROPERTYPRESENT = 10;

    private static Boolean supported;

    private WinUser.HMONITOR monitor;
    private Pointer graphicsDevice;
    private Pointer item;
    private Pointer framePoolStatics;
    private WindowsBoxDownscale boxDownscale;

    WindowsGraphicsCapture(Rectangle bounds) {
        super(bounds);
    }

    /** Windows 10 only captures with a border around the screen. */
    static boolean supported() {
        if (supported == null)
            supported = onCaptureThread(() -> {
                try {
                    return sessionSupported() &&
                           propertyPresent(GRAPHICS_CAPTURE_SESSION, "IsBorderRequired");
                }
                catch (Throwable e) {
                    logger.debug("Windows Graphics Capture unavailable: " + e.getMessage());
                    return false;
                }
            });
        return supported;
    }

    private static boolean sessionSupported() {
        Pointer statics = factory(GRAPHICS_CAPTURE_SESSION, IID_IGraphicsCaptureSessionStatics);
        try {
            ByteByReference result = new ByteByReference();
            check(call(statics, IGRAPHICSCAPTURESESSIONSTATICS_ISSUPPORTED, result),
                    "IsSupported");
            return result.getValue() != 0;
        }
        finally {
            release(statics);
        }
    }

    private static boolean propertyPresent(String typeName, String propertyName) {
        Pointer statics = factory(API_INFORMATION, IID_IApiInformationStatics);
        Pointer type = string(typeName);
        Pointer property = string(propertyName);
        try {
            ByteByReference result = new ByteByReference();
            check(call(statics, IAPIINFORMATIONSTATICS_ISPROPERTYPRESENT, type, property,
                    result), "IsPropertyPresent");
            return result.getValue() != 0;
        }
        finally {
            Combase.INSTANCE.WindowsDeleteString(property);
            Combase.INSTANCE.WindowsDeleteString(type);
            release(statics);
        }
    }

    @Override
    void initialize(WindowsDesktopDuplication.Output output) {
        int format = WindowsDesktopDuplication.advancedColorEnabled(output.deviceName()) ?
                DXGI_FORMAT_R16G16B16A16_FLOAT : DXGI_FORMAT_B8G8R8A8_UNORM;
        setFormat(format, format == DXGI_FORMAT_R16G16B16A16_FLOAT ?
                WindowsDesktopDuplication.sdrWhiteLevel(output.deviceName()) : 1);
        monitor = new WinUser.HMONITOR(output.monitor());
        Pointer dxgiDevice = queryInterface(device, IID_IDXGIDevice);
        try {
            PointerByReference graphicsDeviceOut = new PointerByReference();
            check(WindowsDesktopDuplication.D3D11.INSTANCE.CreateDirect3D11DeviceFromDXGIDevice(
                    dxgiDevice, graphicsDeviceOut), "CreateDirect3D11DeviceFromDXGIDevice");
            graphicsDevice = graphicsDeviceOut.getValue();
        }
        finally {
            release(dxgiDevice);
        }
        Pointer interop = factory(GRAPHICS_CAPTURE_ITEM, IID_IGraphicsCaptureItemInterop);
        try {
            item = out(interop, IGRAPHICSCAPTUREITEMINTEROP_CREATEFORMONITOR, "CreateForMonitor",
                    monitor.getPointer(), IID_IGraphicsCaptureItem);
        }
        finally {
            release(interop);
        }
        framePoolStatics = factory(DIRECT3D11_CAPTURE_FRAME_POOL,
                IID_IDirect3D11CaptureFramePoolStatics2);
        boxDownscale = new WindowsBoxDownscale(device, context, srgbByHalf);
    }

    /** A frame pool takes one session only. */
    @Override
    byte[] acquire(Rectangle bounds, int scaledWidth, int scaledHeight) {
        Rectangle outputBounds = outputBounds();
        if (!outputBounds.equals(monitorBounds()))
            throw new IllegalStateException("display changed");
        // SizeInt32 is passed by value, in one register.
        Pointer framePool = out(framePoolStatics,
                IDIRECT3D11CAPTUREFRAMEPOOLSTATICS2_CREATEFREETHREADED, "CreateFreeThreaded",
                graphicsDevice, format, 1,
                (long) outputBounds.height() << 32 | outputBounds.width());
        try {
            Pointer session = out(framePool, IDIRECT3D11CAPTUREFRAMEPOOL_CREATECAPTURESESSION,
                    "CreateCaptureSession", item);
            try {
                start(session);
                return readFrame(awaitFrame(framePool), bounds, scaledWidth, scaledHeight);
            }
            finally {
                closeAndRelease(session);
            }
        }
        finally {
            closeAndRelease(framePool);
        }
    }

    private static void start(Pointer session) {
        Pointer session2 = queryInterface(session, IID_IGraphicsCaptureSession2);
        HRESULT hr = call(session2, IGRAPHICSCAPTURESESSION2_PUT_ISCURSORCAPTUREENABLED,
                (byte) 0);
        release(session2);
        check(hr, "put_IsCursorCaptureEnabled");
        Pointer session3 = queryInterface(session, IID_IGraphicsCaptureSession3);
        hr = call(session3, IGRAPHICSCAPTURESESSION3_PUT_ISBORDERREQUIRED, (byte) 0);
        release(session3);
        check(hr, "put_IsBorderRequired");
        check(call(session, IGRAPHICSCAPTURESESSION_STARTCAPTURE), "StartCapture");
    }

    private static Pointer awaitFrame(Pointer framePool) {
        long deadline = System.currentTimeMillis() + acquireTimeoutMillis;
        Pointer frame;
        while ((frame = out(framePool, IDIRECT3D11CAPTUREFRAMEPOOL_TRYGETNEXTFRAME,
                "TryGetNextFrame")) == null) {
            if (System.currentTimeMillis() > deadline)
                throw new IllegalStateException("no frame");
            LockSupport.parkNanos(1_000_000);
        }
        return frame;
    }

    private byte[] readFrame(Pointer frame, Rectangle bounds, int scaledWidth,
                             int scaledHeight) {
        try {
            Pointer surface = out(frame, IDIRECT3D11CAPTUREFRAME_GET_SURFACE, "get_Surface");
            Pointer access = queryInterface(surface, IID_IDirect3DDxgiInterfaceAccess);
            release(surface);
            Pointer texture = out(access, IDIRECT3DDXGIINTERFACEACCESS_GETINTERFACE,
                    "GetInterface", IID_ID3D11Texture2D);
            release(access);
            try {
                return boxDownscale.downscaledRgb(texture, bounds, scaledWidth, scaledHeight);
            }
            finally {
                release(texture);
            }
        }
        finally {
            closeAndRelease(frame);
        }
    }

    private Rectangle monitorBounds() {
        WinUser.MONITORINFO monitorInfo = new WinUser.MONITORINFO();
        if (!User32.INSTANCE.GetMonitorInfo(monitor, monitorInfo).booleanValue())
            throw new IllegalStateException("monitor gone");
        WinDef.RECT rect = monitorInfo.rcMonitor;
        return new Rectangle(rect.left, rect.top, rect.right - rect.left,
                rect.bottom - rect.top);
    }

    private static Pointer factory(String className, Guid.IID iid) {
        Pointer string = string(className);
        try {
            PointerByReference factory = new PointerByReference();
            check(Combase.INSTANCE.RoGetActivationFactory(string, iid, factory),
                    "RoGetActivationFactory " + className);
            return factory.getValue();
        }
        finally {
            Combase.INSTANCE.WindowsDeleteString(string);
        }
    }

    private static Pointer string(String text) {
        PointerByReference string = new PointerByReference();
        check(Combase.INSTANCE.WindowsCreateString(new WString(text), text.length(), string),
                "WindowsCreateString");
        return string.getValue();
    }

    private static void closeAndRelease(Pointer object) {
        Pointer closable = queryInterface(object, IID_IClosable);
        call(closable, ICLOSABLE_CLOSE);
        release(closable);
        release(object);
    }

    @Override
    public void close() {
        if (boxDownscale != null) {
            boxDownscale.close();
            boxDownscale = null;
        }
        release(framePoolStatics);
        framePoolStatics = null;
        release(item);
        item = null;
        release(graphicsDevice);
        graphicsDevice = null;
        super.close();
    }

}

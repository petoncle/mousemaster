package mousemaster.platform.windows;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.PointerByReference;
import mousemaster.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static mousemaster.platform.windows.WindowsDesktopDuplication.call;
import static mousemaster.platform.windows.WindowsDesktopDuplication.callVoid;
import static mousemaster.platform.windows.WindowsDesktopDuplication.check;
import static mousemaster.platform.windows.WindowsDesktopDuplication.release;
import static mousemaster.platform.windows.WindowsDesktopDuplication.D3D11_CREATE_DEVICE_BGRA_SUPPORT;
import static mousemaster.platform.windows.WindowsDesktopDuplication.D3D11_SDK_VERSION;
import static mousemaster.platform.windows.WindowsDesktopDuplication.DXGI_FORMAT_B8G8R8A8_UNORM;
import static mousemaster.platform.windows.WindowsDesktopDuplication.DXGI_FORMAT_R16G16B16A16_FLOAT;
import static mousemaster.platform.windows.WindowsDesktopDuplication.ID3D11DEVICECONTEXT_COPYRESOURCE;
import static mousemaster.platform.windows.WindowsDesktopDuplication.S_OK;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Captures the desktop of an output, read back to the CPU. Any failure closes the capture and
 * returns null so the caller falls back to the Qt capture.
 */
abstract class WindowsDesktopFrameCapture implements AutoCloseable {

    private static final Logger logger =
            LoggerFactory.getLogger(WindowsDesktopFrameCapture.class);

    // Vtable indices (stable COM ABI). IUnknown: 0 QueryInterface, 1 AddRef, 2 Release.
    private static final int ID3D11DEVICE_CREATETEXTURE2D = 5;
    private static final int ID3D11TEXTURE2D_GETDESC = 10;
    private static final int ID3D11DEVICECONTEXT_MAP = 14;
    private static final int ID3D11DEVICECONTEXT_UNMAP = 15;

    private static final int D3D_DRIVER_TYPE_UNKNOWN = 0;
    private static final int D3D11_USAGE_STAGING = 3;
    private static final int D3D11_CPU_ACCESS_READ = 0x20000;
    private static final int D3D11_MAP_READ = 1;

    private static final ExecutorService captureThread =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(() -> {
                    Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED);
                    runnable.run();
                }, "desktop-capture");
                thread.setDaemon(true);
                return thread;
            });

    static final int acquireTimeoutMillis = 100;
    static final List<int[]> wholeFrame =
            List.of(new int[] {0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE});

    /** The captured frame: B8G8R8A8 pixels. */
    record Frame(byte[] bgra, int width, int height, int rowPitch) {
    }

    Pointer device;
    Pointer context;
    Frame lastFrame;
    int format;
    private Pointer staging;
    private int width;
    private int height;
    private byte[] desktop;
    /** The sRGB byte of each FP16 channel value. */
    private byte[] srgbByHalf;
    private final Rectangle bounds;
    private Rectangle outputBounds;
    private boolean unavailable;

    WindowsDesktopFrameCapture(Rectangle bounds) {
        this.bounds = bounds;
    }

    /** The desktop coordinates of the output being captured. */
    Rectangle outputBounds() {
        return outputBounds;
    }

    /** Before the first capture the output is unknown, so the bounds asked for stand in. */
    boolean covers(Rectangle bounds) {
        return outputBounds == null ? this.bounds.equals(bounds)
                                    : outputBounds.contains(bounds);
    }

    /**
     * Returns the desktop with the window changes made before the call, or null if the capture
     * is unavailable (the caller should then fall back). Reinitializes transparently after a
     * failure, such as the desktop switching.
     */
    Frame capture() {
        if (unavailable)
            return null;
        return onCaptureThread(() -> {
            try {
                if (device == null)
                    initialize();
                return acquire();
            }
            catch (Throwable e) {
                logger.debug(getClass().getSimpleName() + " failed, falling back: " +
                             e.getMessage());
                close();
                return null;
            }
        });
    }

    /** COM refuses the calls of Windows Graphics Capture from the keyboard hook. */
    static <T> T onCaptureThread(Callable<T> task) {
        try {
            return captureThread.submit(task).get();
        }
        catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    private void initialize() {
        WindowsDesktopDuplication.Output found =
                WindowsDesktopDuplication.findOutputCovering(bounds);
        outputBounds = found.bounds();
        try {
            createDevice(found.adapter());
            initialize(found);
        }
        finally {
            release(found.output());
            release(found.adapter());
        }
    }

    abstract void initialize(WindowsDesktopDuplication.Output output);

    abstract Frame acquire();

    void setFormat(int format, float sdrWhiteLevel) {
        this.format = format;
        srgbByHalf = format == DXGI_FORMAT_R16G16B16A16_FLOAT ? srgbByHalf(sdrWhiteLevel) : null;
    }

    /** An explicit adapter requires the driver type to be UNKNOWN. */
    private void createDevice(Pointer adapter) {
        PointerByReference devOut = new PointerByReference();
        PointerByReference ctxOut = new PointerByReference();
        HRESULT hr = WindowsDesktopDuplication.D3D11.INSTANCE.D3D11CreateDevice(
                adapter, D3D_DRIVER_TYPE_UNKNOWN, Pointer.NULL,
                D3D11_CREATE_DEVICE_BGRA_SUPPORT, Pointer.NULL, 0,
                D3D11_SDK_VERSION, devOut, null, ctxOut);
        if (hr.intValue() != S_OK)
            unavailable = true; // No adapter, and none will turn up later.
        check(hr, "D3D11CreateDevice");
        device = devOut.getValue();
        context = ctxOut.getValue();
    }

    /** Reads the areas that changed into a desktop kept across frames. */
    Frame readTexture(Pointer texture, List<int[]> changedRectangles) {
        WindowsDesktopDuplication.Texture2DDesc desc = new WindowsDesktopDuplication.Texture2DDesc();
        callVoid(texture, ID3D11TEXTURE2D_GETDESC, desc.getPointer());
        desc.read();
        ensureStaging(desc);
        callVoid(context, ID3D11DEVICECONTEXT_COPYRESOURCE, staging, texture);

        MappedSubresource mapped = new MappedSubresource();
        HRESULT hr = call(context, ID3D11DEVICECONTEXT_MAP, staging, 0, D3D11_MAP_READ, 0,
                mapped.getPointer());
        check(hr, "Map");
        mapped.read();
        try {
            int rowPitch = mapped.rowPitch;
            if (desktop == null || desktop.length != width * 4 * height) {
                desktop = new byte[width * 4 * height];
                readRectangle(mapped.pData, rowPitch, wholeFrame.getFirst());
            }
            else {
                for (int[] rectangle : changedRectangles)
                    readRectangle(mapped.pData, rowPitch, rectangle);
            }
            lastFrame = new Frame(desktop, width, height, width * 4);
            return lastFrame;
        }
        finally {
            callVoid(context, ID3D11DEVICECONTEXT_UNMAP, staging, 0);
        }
    }

    private void readRectangle(Pointer pixels, int rowPitch, int[] rectangle) {
        int left = Math.clamp(rectangle[0], 0, width);
        int top = Math.clamp(rectangle[1], 0, height);
        int right = Math.clamp(rectangle[2], left, width);
        int bottom = Math.clamp(rectangle[3], top, height);
        int pixelCount = right - left;
        if (pixelCount <= 0)
            return;
        if (format == DXGI_FORMAT_B8G8R8A8_UNORM) {
            for (int y = top; y < bottom; y++)
                pixels.read(y * rowPitch + left * 4L, desktop, (y * width + left) * 4,
                        pixelCount * 4);
            return;
        }
        short[] halves = new short[pixelCount * 4];
        for (int y = top; y < bottom; y++) {
            int at = (y * width + left) * 4;
            pixels.read(y * rowPitch + left * 8L, halves, 0, halves.length);
            for (int half = 0; half < halves.length; half += 4, at += 4) {
                desktop[at] = srgbByHalf[halves[half + 2] & 0xffff];
                desktop[at + 1] = srgbByHalf[halves[half + 1] & 0xffff];
                desktop[at + 2] = srgbByHalf[halves[half] & 0xffff];
                desktop[at + 3] = (byte) 255;
            }
        }
    }

    private static byte[] srgbByHalf(float sdrWhiteLevel) {
        byte[] srgbByHalf = new byte[1 << 16];
        for (int half = 0; half < srgbByHalf.length; half++) {
            double linear = Math.clamp(Float.float16ToFloat((short) half) / sdrWhiteLevel, 0, 1);
            double srgb = linear <= 0.0031308 ? 12.92 * linear
                    : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
            srgbByHalf[half] = (byte) Math.round(srgb * 255);
        }
        return srgbByHalf;
    }

    private void ensureStaging(WindowsDesktopDuplication.Texture2DDesc frameDesc) {
        if (staging != null && frameDesc.width == width && frameDesc.height == height)
            return;
        if (staging != null) {
            release(staging);
            staging = null;
        }
        width = frameDesc.width;
        height = frameDesc.height;
        WindowsDesktopDuplication.Texture2DDesc desc = new WindowsDesktopDuplication.Texture2DDesc();
        desc.width = width;
        desc.height = height;
        desc.mipLevels = 1;
        desc.arraySize = 1;
        desc.format = frameDesc.format;
        desc.sampleCount = 1;
        desc.sampleQuality = 0;
        desc.usage = D3D11_USAGE_STAGING;
        desc.bindFlags = 0;
        desc.cpuAccessFlags = D3D11_CPU_ACCESS_READ;
        desc.miscFlags = 0;
        desc.write();
        PointerByReference out = new PointerByReference();
        HRESULT hr = call(device, ID3D11DEVICE_CREATETEXTURE2D, desc.getPointer(),
                Pointer.NULL, out);
        check(hr, "CreateTexture2D (staging)");
        staging = out.getValue();
    }

    @Override
    public void close() {
        release(staging);
        staging = null;
        // The device goes with it: it can only capture an output of the adapter it was
        // created on, and the next output may be on another one.
        release(context);
        context = null;
        release(device);
        device = null;
    }

    public static class MappedSubresource extends Structure {
        public Pointer pData;
        public int rowPitch, depthPitch;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("pData", "rowPitch", "depthPitch");
        }
    }

}

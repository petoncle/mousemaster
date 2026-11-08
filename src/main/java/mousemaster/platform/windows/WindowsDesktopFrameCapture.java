package mousemaster.platform.windows;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.PointerByReference;
import mousemaster.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static mousemaster.platform.windows.WindowsDesktopDuplication.check;
import static mousemaster.platform.windows.WindowsDesktopDuplication.release;
import static mousemaster.platform.windows.WindowsDesktopDuplication.D3D11_CREATE_DEVICE_BGRA_SUPPORT;
import static mousemaster.platform.windows.WindowsDesktopDuplication.D3D11_SDK_VERSION;
import static mousemaster.platform.windows.WindowsDesktopDuplication.DXGI_FORMAT_R16G16B16A16_FLOAT;
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

    private static final int D3D_DRIVER_TYPE_UNKNOWN = 0;

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

    Pointer device;
    Pointer context;
    int format;
    /** The sRGB byte of each FP16 channel value. */
    byte[] srgbByHalf;
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
     * Returns the desktop inside bounds with the window changes made before the call, scaled as
     * DesktopCapture.boxDownscaledRgb does, or null if the capture is unavailable (the
     * caller should then fall back). Reinitializes transparently after a failure, such as the
     * desktop switching.
     */
    byte[] capture(Rectangle bounds, int scaledWidth, int scaledHeight) {
        if (unavailable)
            return null;
        return onCaptureThread(() -> {
            try {
                if (device == null)
                    initialize();
                return acquire(new Rectangle(bounds.x() - outputBounds.x(),
                        bounds.y() - outputBounds.y(), bounds.width(), bounds.height()),
                        scaledWidth, scaledHeight);
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

    /** Bounds are in the coordinates of the output's own frame. */
    abstract byte[] acquire(Rectangle bounds, int scaledWidth, int scaledHeight);

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

    @Override
    public void close() {
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

package mousemaster.platform.windows;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import mousemaster.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static mousemaster.platform.windows.WindowsDesktopDuplication.call;
import static mousemaster.platform.windows.WindowsDesktopDuplication.callVoid;
import static mousemaster.platform.windows.WindowsDesktopDuplication.check;
import static mousemaster.platform.windows.WindowsDesktopDuplication.queryInterface;
import static mousemaster.platform.windows.WindowsDesktopDuplication.release;
import static mousemaster.platform.windows.WindowsDesktopDuplication.D3D11_CREATE_DEVICE_BGRA_SUPPORT;
import static mousemaster.platform.windows.WindowsDesktopDuplication.D3D11_SDK_VERSION;
import static mousemaster.platform.windows.WindowsDesktopDuplication.DXGI_ERROR_WAIT_TIMEOUT;
import static mousemaster.platform.windows.WindowsDesktopDuplication.DXGI_FORMAT_B8G8R8A8_UNORM;
import static mousemaster.platform.windows.WindowsDesktopDuplication.ID3D11DEVICECONTEXT_COPYRESOURCE;
import static mousemaster.platform.windows.WindowsDesktopDuplication.IDXGIADAPTER_ENUMOUTPUTS;
import static mousemaster.platform.windows.WindowsDesktopDuplication.IDXGIOUTPUT1_DUPLICATEOUTPUT;
import static mousemaster.platform.windows.WindowsDesktopDuplication.IDXGIOUTPUTDUPLICATION_ACQUIRENEXTFRAME;
import static mousemaster.platform.windows.WindowsDesktopDuplication.IDXGIOUTPUTDUPLICATION_RELEASEFRAME;
import static mousemaster.platform.windows.WindowsDesktopDuplication.IID_ID3D11Texture2D;
import static mousemaster.platform.windows.WindowsDesktopDuplication.IID_IDXGIOutput1;
import static mousemaster.platform.windows.WindowsDesktopDuplication.RELEASE;
import static mousemaster.platform.windows.WindowsDesktopDuplication.S_OK;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures the desktop with DXGI Desktop Duplication as {@link WindowsDesktopDuplication}
 * does for the zoom, except read back to the CPU. Any failure closes the duplication and
 * returns null so the caller falls back to the Qt capture. Duplication is change-driven:
 * a frame that times out means the screen still holds the one before it.
 */
final class WindowsDesktopFrameCapture implements AutoCloseable {

    private static final Logger logger =
            LoggerFactory.getLogger(WindowsDesktopFrameCapture.class);

    // COM interface IIDs.
    private static final Guid.IID IID_IDXGIDevice =
            new Guid.IID("54ec77fa-1377-44e6-8c32-88fd5f44c84c");

    // Vtable indices (stable COM ABI). IUnknown: 0 QueryInterface, 1 AddRef, 2 Release.
    private static final int IDXGIOBJECT_GETPARENT = 6;
    private static final int IDXGIDEVICE_GETADAPTER = 7;
    private static final int IDXGIOUTPUTDUPLICATION_GETFRAMEDIRTYRECTS = 9;
    private static final int IDXGIOUTPUTDUPLICATION_GETFRAMEMOVERECTS = 10;
    private static final int RECT_SIZE = 16;
    private static final int MOVE_RECT_SIZE = 24;
    private static final int MOVE_RECT_DESTINATION = 8;
    private static final int TOTAL_METADATA_BUFFER_SIZE = 40;
    private static final int ID3D11DEVICE_CREATETEXTURE2D = 5;
    private static final int ID3D11TEXTURE2D_GETDESC = 10;
    private static final int ID3D11DEVICECONTEXT_MAP = 14;
    private static final int ID3D11DEVICECONTEXT_UNMAP = 15;

    private static final int D3D_DRIVER_TYPE_UNKNOWN = 0;
    private static final int D3D11_USAGE_STAGING = 3;
    private static final int D3D11_CPU_ACCESS_READ = 0x20000;
    private static final int D3D11_MAP_READ = 1;

    private static final int acquireTimeoutMillis = 100;
    private static final int DXGI_OUTDUPL_FRAME_INFO_SIZE = 48;
    private static final int LAST_PRESENT_TIME = 0;

    private static final int DXGI_ERROR_ACCESS_LOST = 0x887A0026;

    /** The captured frame: B8G8R8A8 pixels with a row stride that may exceed width*4. */
    record Frame(byte[] bgra, int width, int height, int rowPitch) {
    }

    private Pointer device;
    private Pointer context;
    private Pointer duplication;
    private Pointer staging;
    private int width;
    private int height;
    private Frame lastFrame;
    private byte[] desktop;
    private final Rectangle bounds;
    private Rectangle outputBounds;
    private boolean unavailable;

    WindowsDesktopFrameCapture(Rectangle bounds) {
        this.bounds = bounds;
    }

    /** The desktop coordinates of the output being duplicated. */
    Rectangle outputBounds() {
        return outputBounds;
    }

    /** Before the first capture the output is unknown, so the bounds asked for stand in. */
    boolean covers(Rectangle bounds) {
        return outputBounds == null ? this.bounds.equals(bounds)
                                    : outputBounds.contains(bounds);
    }

    /**
     * Returns the current desktop frame, or null if duplication is unavailable (the
     * caller should then fall back). Reinitializes transparently after the desktop
     * switches (which invalidates the duplication).
     */
    Frame capture() {
        if (unavailable)
            return null;
        try {
            if (duplication == null)
                initialize();
            return acquire();
        }
        catch (Throwable e) {
            logger.debug("Desktop Duplication capture failed, falling back: " +
                         e.getMessage());
            close();
            return null;
        }
    }

    private void initialize() {
        WindowsDesktopDuplication.Output found =
                WindowsDesktopDuplication.findOutputCovering(bounds);
        outputBounds = found.bounds();
        try {
            createDevice(found.adapter());
            Pointer output1 = queryInterface(found.output(), IID_IDXGIOutput1);
            PointerByReference dupOut = new PointerByReference();
            HRESULT hr = call(output1, IDXGIOUTPUT1_DUPLICATEOUTPUT, device, dupOut);
            release(output1);
            check(hr, "DuplicateOutput");
            duplication = dupOut.getValue();
        }
        finally {
            release(found.output());
            release(found.adapter());
        }
        discardEmptyFirstFrame();
        logger.debug("Initialized Desktop Duplication on " + outputBounds);
    }

    private void discardEmptyFirstFrame() {
        PointerByReference resourceOut = new PointerByReference();
        HRESULT hr = call(duplication, IDXGIOUTPUTDUPLICATION_ACQUIRENEXTFRAME,
                acquireTimeoutMillis, new Memory(DXGI_OUTDUPL_FRAME_INFO_SIZE), resourceOut);
        if (hr.intValue() != S_OK)
            return;
        release(resourceOut.getValue());
        call(duplication, IDXGIOUTPUTDUPLICATION_RELEASEFRAME);
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

    private Frame acquire() {
        while (true) {
            PointerByReference resourceOut = new PointerByReference();
            Memory frameInfo = new Memory(DXGI_OUTDUPL_FRAME_INFO_SIZE);
            HRESULT hr = call(duplication, IDXGIOUTPUTDUPLICATION_ACQUIRENEXTFRAME,
                    acquireTimeoutMillis, frameInfo, resourceOut);
            int code = hr.intValue();
            if (code == DXGI_ERROR_WAIT_TIMEOUT)
                return lastFrame; // screen unchanged since the last capture
            if (code == DXGI_ERROR_ACCESS_LOST)
                throw new IllegalStateException("desktop switched (access lost)");
            check(hr, "AcquireNextFrame");
            Pointer resource = resourceOut.getValue();
            try {
                // Without a present the desktop image is not updated, and holds whatever
                // it held before. The screen is then what the last capture already read.
                if (frameInfo.getLong(LAST_PRESENT_TIME) == 0) {
                    if (lastFrame != null)
                        return lastFrame;
                    continue;
                }
                Pointer texture = queryInterface(resource, IID_ID3D11Texture2D);
                try {
                    return readTexture(texture, frameInfo);
                }
                finally {
                    release(texture);
                }
            }
            finally {
                release(resource);
                call(duplication, IDXGIOUTPUTDUPLICATION_RELEASEFRAME);
            }
        }
    }

    /**
     * Duplication hands back one of several surfaces it rotates through, and each holds
     * the desktop as of the last time it was written: areas that have not changed since
     * are stale in it. Only the areas it reports as changed are read, into a desktop kept
     * across frames.
     */
    private Frame readTexture(Pointer texture, Memory frameInfo) {
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
            if (desktop == null || desktop.length != rowPitch * height) {
                desktop = mapped.pData.getByteArray(0, rowPitch * height);
            }
            else {
                List<int[]> changed = changedRectangles(frameInfo);
                logger.debug("Frame metadata " + frameInfo.getInt(TOTAL_METADATA_BUFFER_SIZE)
                             + " bytes, " + changed.size() + " changed rectangles"
                             + (changed.size() == 1 ? " " + java.util.Arrays.toString(
                                     changed.getFirst()) : ""));
                for (int[] rectangle : changed)
                    readRectangle(mapped.pData, rowPitch, rectangle);
            }
            lastFrame = new Frame(desktop, width, height, rowPitch);
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
        int bytes = (right - left) * 4;
        if (bytes <= 0)
            return;
        for (int y = top; y < bottom; y++) {
            int at = y * rowPitch + left * 4;
            pixels.read(at, desktop, at, bytes);
        }
    }

    /** The areas the frame changed: what moved, plus what was redrawn. */
    private List<int[]> changedRectangles(Memory frameInfo) {
        List<int[]> rectangles = new ArrayList<>();
        int metadataSize = frameInfo.getInt(TOTAL_METADATA_BUFFER_SIZE);
        if (metadataSize <= 0)
            return List.of(new int[] {0, 0, width, height});
        Memory metadata = new Memory(metadataSize);
        IntByReference written = new IntByReference();
        HRESULT hr = call(duplication, IDXGIOUTPUTDUPLICATION_GETFRAMEMOVERECTS,
                metadataSize, metadata, written);
        if (hr.intValue() != S_OK)
            return List.of(new int[] {0, 0, width, height});
        int moved = written.getValue();
        for (int at = 0; at + MOVE_RECT_SIZE <= moved; at += MOVE_RECT_SIZE)
            rectangles.add(readRect(metadata, at + MOVE_RECT_DESTINATION));
        hr = call(duplication, IDXGIOUTPUTDUPLICATION_GETFRAMEDIRTYRECTS,
                metadataSize - moved, metadata.share(moved), written);
        if (hr.intValue() != S_OK)
            return List.of(new int[] {0, 0, width, height});
        for (int at = 0; at + RECT_SIZE <= written.getValue(); at += RECT_SIZE)
            rectangles.add(readRect(metadata.share(moved), at));
        return rectangles;
    }

    private static int[] readRect(Pointer metadata, int at) {
        return new int[] {metadata.getInt(at), metadata.getInt(at + 4),
                          metadata.getInt(at + 8), metadata.getInt(at + 12)};
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
        desc.format = DXGI_FORMAT_B8G8R8A8_UNORM;
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
        release(duplication);
        duplication = null;
        release(staging);
        staging = null;
        // The device goes with it: it can only duplicate an output of the adapter it was
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

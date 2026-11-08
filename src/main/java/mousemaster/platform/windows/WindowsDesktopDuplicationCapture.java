package mousemaster.platform.windows;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import mousemaster.Rectangle;
import mousemaster.platform.DesktopCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static mousemaster.platform.windows.WindowsDesktopDuplication.call;
import static mousemaster.platform.windows.WindowsDesktopDuplication.check;
import static mousemaster.platform.windows.WindowsDesktopDuplication.duplicateOutput;
import static mousemaster.platform.windows.WindowsDesktopDuplication.queryInterface;
import static mousemaster.platform.windows.WindowsDesktopDuplication.release;
import static mousemaster.platform.windows.WindowsDesktopDuplication.DXGI_ERROR_WAIT_TIMEOUT;
import static mousemaster.platform.windows.WindowsDesktopDuplication.IDXGIOUTPUTDUPLICATION_ACQUIRENEXTFRAME;
import static mousemaster.platform.windows.WindowsDesktopDuplication.IDXGIOUTPUTDUPLICATION_RELEASEFRAME;
import static mousemaster.platform.windows.WindowsDesktopDuplication.IID_ID3D11Texture2D;
import static mousemaster.platform.windows.WindowsDesktopDuplication.S_OK;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures with DXGI Desktop Duplication as {@link WindowsDesktopDuplication} does for the zoom.
 * Duplication is change-driven: a frame that times out means the screen still holds the one
 * before it.
 */
final class WindowsDesktopDuplicationCapture extends WindowsDesktopFrameCapture {

    private static final Logger logger =
            LoggerFactory.getLogger(WindowsDesktopDuplicationCapture.class);

    private static final int IDXGIOUTPUTDUPLICATION_GETFRAMEDIRTYRECTS = 9;
    private static final int IDXGIOUTPUTDUPLICATION_GETFRAMEMOVERECTS = 10;
    private static final int RECT_SIZE = 16;
    private static final int MOVE_RECT_SIZE = 24;
    private static final int MOVE_RECT_DESTINATION = 8;
    private static final int TOTAL_METADATA_BUFFER_SIZE = 40;

    private static final int DXGI_OUTDUPL_FRAME_INFO_SIZE = 48;
    private static final int LAST_PRESENT_TIME = 0;

    private static final int DXGI_ERROR_ACCESS_LOST = 0x887A0026;

    private static final List<int[]> wholeFrame =
            List.of(new int[] {0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE});

    private Pointer duplication;
    private byte[] desktop;

    WindowsDesktopDuplicationCapture(Rectangle bounds) {
        super(bounds);
    }

    @Override
    void initialize(WindowsDesktopDuplication.Output output) {
        WindowsDesktopDuplication.Duplication outputDuplication =
                duplicateOutput(output, device);
        duplication = outputDuplication.pointer();
        setFormat(outputDuplication.format(), outputDuplication.sdrWhiteLevel());
        discardEmptyFirstFrame();
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

    @Override
    byte[] acquire(Rectangle bounds, int scaledWidth, int scaledHeight) {
        Dwmapi.INSTANCE.DwmFlush();
        while (true) {
            PointerByReference resourceOut = new PointerByReference();
            Memory frameInfo = new Memory(DXGI_OUTDUPL_FRAME_INFO_SIZE);
            HRESULT hr = call(duplication, IDXGIOUTPUTDUPLICATION_ACQUIRENEXTFRAME,
                    acquireTimeoutMillis, frameInfo, resourceOut);
            int code = hr.intValue();
            if (code == DXGI_ERROR_WAIT_TIMEOUT) // screen unchanged since the last capture
                return desktop == null ? null : desktopRgb(bounds, scaledWidth, scaledHeight);
            if (code == DXGI_ERROR_ACCESS_LOST)
                throw new IllegalStateException("desktop switched (access lost)");
            check(hr, "AcquireNextFrame");
            Pointer resource = resourceOut.getValue();
            try {
                // Without a present the desktop image is not updated, and holds whatever
                // it held before. The screen is then what the last capture already read.
                if (frameInfo.getLong(LAST_PRESENT_TIME) == 0) {
                    if (desktop != null)
                        return desktopRgb(bounds, scaledWidth, scaledHeight);
                    continue;
                }
                Pointer texture = queryInterface(resource, IID_ID3D11Texture2D);
                try {
                    readTexture(texture, changedRectangles(frameInfo));
                }
                finally {
                    release(texture);
                }
            }
            finally {
                release(resource);
                call(duplication, IDXGIOUTPUTDUPLICATION_RELEASEFRAME);
            }
            return desktopRgb(bounds, scaledWidth, scaledHeight);
        }
    }

    /** Reads the areas that changed into a desktop kept across frames. */
    private void readTexture(Pointer texture, List<int[]> changedRectangles) {
        readMapped(texture, (pixels, rowPitch) -> {
            if (desktop == null || desktop.length != width * 4 * height) {
                desktop = new byte[width * 4 * height];
                readRectangle(pixels, rowPitch, wholeFrame.getFirst());
            }
            else {
                for (int[] rectangle : changedRectangles)
                    readRectangle(pixels, rowPitch, rectangle);
            }
            return null;
        });
    }

    private void readRectangle(Pointer pixels, int rowPitch, int[] rectangle) {
        int left = Math.clamp(rectangle[0], 0, width);
        int top = Math.clamp(rectangle[1], 0, height);
        int right = Math.clamp(rectangle[2], left, width);
        int bottom = Math.clamp(rectangle[3], top, height);
        if (right <= left)
            return;
        readPixels(pixels, rowPitch, left, right, top, bottom, desktop, (top * width + left) * 4,
                width * 4);
    }

    private byte[] desktopRgb(Rectangle bounds, int scaledWidth, int scaledHeight) {
        return DesktopCapture.boxDownscaledRgb((x, bandWidth, fromY, toY, band) -> {
            for (int y = fromY; y < toY; y++)
                System.arraycopy(desktop, (y * width + x) * 4, band, (y - fromY) * bandWidth * 4,
                        bandWidth * 4);
        }, bounds, scaledWidth, scaledHeight);
    }

    /**
     * Duplication hands back one of several surfaces it rotates through, and each holds the
     * desktop as of the last time it was written: areas that have not changed since are stale
     * in it. Those that changed are what moved, plus what was redrawn.
     */
    private List<int[]> changedRectangles(Memory frameInfo) {
        List<int[]> rectangles = new ArrayList<>();
        int metadataSize = frameInfo.getInt(TOTAL_METADATA_BUFFER_SIZE);
        if (metadataSize <= 0)
            return wholeFrame;
        Memory metadata = new Memory(metadataSize);
        IntByReference written = new IntByReference();
        HRESULT hr = call(duplication, IDXGIOUTPUTDUPLICATION_GETFRAMEMOVERECTS,
                metadataSize, metadata, written);
        if (hr.intValue() != S_OK)
            return wholeFrame;
        int moved = written.getValue();
        for (int at = 0; at + MOVE_RECT_SIZE <= moved; at += MOVE_RECT_SIZE)
            rectangles.add(readRect(metadata, at + MOVE_RECT_DESTINATION));
        hr = call(duplication, IDXGIOUTPUTDUPLICATION_GETFRAMEDIRTYRECTS,
                metadataSize - moved, metadata.share(moved), written);
        if (hr.intValue() != S_OK)
            return wholeFrame;
        for (int at = 0; at + RECT_SIZE <= written.getValue(); at += RECT_SIZE)
            rectangles.add(readRect(metadata.share(moved), at));
        logger.debug("Frame metadata " + metadataSize + " bytes, " + rectangles.size() +
                     " changed rectangles" + (rectangles.size() == 1 ?
                " " + java.util.Arrays.toString(rectangles.getFirst()) : ""));
        return rectangles;
    }

    private static int[] readRect(Pointer metadata, int at) {
        return new int[] {metadata.getInt(at), metadata.getInt(at + 4),
                          metadata.getInt(at + 8), metadata.getInt(at + 12)};
    }

    @Override
    public void close() {
        release(duplication);
        duplication = null;
        super.close();
    }

}

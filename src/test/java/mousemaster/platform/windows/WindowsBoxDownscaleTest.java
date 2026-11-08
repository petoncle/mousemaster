package mousemaster.platform.windows;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.PointerByReference;
import mousemaster.Rectangle;
import mousemaster.platform.DesktopCapture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.List;
import java.util.Random;

import static mousemaster.platform.windows.WindowsDesktopDuplication.D3D11_SDK_VERSION;
import static mousemaster.platform.windows.WindowsDesktopDuplication.DXGI_FORMAT_B8G8R8A8_UNORM;
import static mousemaster.platform.windows.WindowsDesktopDuplication.DXGI_FORMAT_R16G16B16A16_FLOAT;
import static mousemaster.platform.windows.WindowsDesktopDuplication.S_OK;
import static mousemaster.platform.windows.WindowsDesktopDuplication.out;
import static mousemaster.platform.windows.WindowsDesktopDuplication.release;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The GPU downscale gives the bytes of the CPU one. */
@EnabledOnOs(OS.WINDOWS)
class WindowsBoxDownscaleTest {

    private static final int width = 97, height = 61;
    private static final int D3D_DRIVER_TYPE_HARDWARE = 1;
    private static final int ID3D11DEVICE_CREATETEXTURE2D = 5;
    private static final int D3D11_BIND_SHADER_RESOURCE = 0x8;

    /** Blocks of 3 by 3, uneven blocks, the whole frame, and blocks of less than a pixel. */
    private static final List<Rectangle> boundsList = List.of(new Rectangle(2, 3, 93, 57),
            new Rectangle(1, 1, 90, 55), new Rectangle(0, 0, width, height),
            new Rectangle(3, 2, 7, 5));
    private static final List<int[]> scaledSizes =
            List.of(new int[] {31, 19}, new int[] {23, 17}, new int[] {40, 25},
                    new int[] {16, 11});

    private static Pointer device;
    private static Pointer context;

    @BeforeAll
    static void createDevice() {
        PointerByReference deviceOut = new PointerByReference();
        PointerByReference contextOut = new PointerByReference();
        HRESULT hr = WindowsDesktopDuplication.D3D11.INSTANCE.D3D11CreateDevice(Pointer.NULL,
                D3D_DRIVER_TYPE_HARDWARE, Pointer.NULL, 0, Pointer.NULL, 0, D3D11_SDK_VERSION,
                deviceOut, null, contextOut);
        assumeTrue(hr.intValue() == S_OK, "no Direct3D 11 device here");
        device = deviceOut.getValue();
        context = contextOut.getValue();
    }

    @AfterAll
    static void releaseDevice() {
        release(context);
        release(device);
    }

    private static Pointer frame(int format, byte[] pixels, int rowPitch) {
        WindowsDesktopDuplication.Texture2DDesc desc =
                new WindowsDesktopDuplication.Texture2DDesc();
        desc.width = width;
        desc.height = height;
        desc.mipLevels = 1;
        desc.arraySize = 1;
        desc.format = format;
        desc.sampleCount = 1;
        desc.bindFlags = D3D11_BIND_SHADER_RESOURCE;
        desc.write();
        Memory data = new Memory(pixels.length);
        data.write(0, pixels, 0, pixels.length);
        Memory initialData = new Memory(16);
        initialData.setPointer(0, data);
        initialData.setInt(8, rowPitch);
        initialData.setInt(12, 0);
        Pointer frame = out(device, ID3D11DEVICE_CREATETEXTURE2D, "CreateTexture2D",
                desc.getPointer(), initialData);
        data.close();
        return frame;
    }

    private static void assertDownscalesAsTheCpu(Pointer frame, byte[] bgra,
                                                 byte[] srgbByHalf) {
        try (WindowsBoxDownscale downscale =
                     new WindowsBoxDownscale(device, context, srgbByHalf)) {
            for (int index = 0; index < boundsList.size(); index++) {
                Rectangle bounds = boundsList.get(index);
                int scaledWidth = scaledSizes.get(index)[0];
                int scaledHeight = scaledSizes.get(index)[1];
                assertArrayEquals(DesktopCapture.boxDownscaledRgb(
                                (x, bandWidth, fromY, toY, band) -> {
                                    for (int y = fromY; y < toY; y++)
                                        System.arraycopy(bgra, (y * width + x) * 4, band,
                                                (y - fromY) * bandWidth * 4, bandWidth * 4);
                                }, bounds, scaledWidth, scaledHeight),
                        downscale.downscaledRgb(frame, bounds, scaledWidth, scaledHeight),
                        bounds + " to " + scaledWidth + "x" + scaledHeight);
            }
        }
        finally {
            release(frame);
        }
    }

    @Test
    void bgraFrames() {
        byte[] bgra = new byte[width * height * 4];
        new Random(11).nextBytes(bgra);
        assertDownscalesAsTheCpu(frame(DXGI_FORMAT_B8G8R8A8_UNORM, bgra, width * 4), bgra,
                null);
    }

    /** Each channel of an FP16 frame goes through the table, as the CPU reads it. */
    @Test
    void halfFloatFrames() {
        Random random = new Random(13);
        byte[] srgbByHalf = new byte[1 << 16];
        random.nextBytes(srgbByHalf);
        byte[] halves = new byte[width * height * 8];
        byte[] bgra = new byte[width * height * 4];
        for (int pixel = 0; pixel < width * height; pixel++) {
            int[] channels = new int[4];
            for (int channel = 0; channel < 4; channel++) {
                int bits = random.nextInt(1 << 16);
                // A NaN has no one set of bits: the GPU need not keep its payload.
                if ((bits & 0x7c00) == 0x7c00)
                    bits &= ~0x3ff;
                channels[channel] = bits;
                halves[pixel * 8 + channel * 2] = (byte) bits;
                halves[pixel * 8 + channel * 2 + 1] = (byte) (bits >> 8);
            }
            bgra[pixel * 4] = srgbByHalf[channels[2]];
            bgra[pixel * 4 + 1] = srgbByHalf[channels[1]];
            bgra[pixel * 4 + 2] = srgbByHalf[channels[0]];
            bgra[pixel * 4 + 3] = (byte) 255;
        }
        assertDownscalesAsTheCpu(frame(DXGI_FORMAT_R16G16B16A16_FLOAT, halves, width * 8),
                bgra, srgbByHalf);
    }
}

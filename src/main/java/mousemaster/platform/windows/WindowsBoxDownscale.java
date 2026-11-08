package mousemaster.platform.windows;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import mousemaster.Rectangle;
import mousemaster.qt.ImageChunks;

import static mousemaster.platform.windows.WindowsDesktopDuplication.blobData;
import static mousemaster.platform.windows.WindowsDesktopDuplication.blobSize;
import static mousemaster.platform.windows.WindowsDesktopDuplication.call;
import static mousemaster.platform.windows.WindowsDesktopDuplication.callVoid;
import static mousemaster.platform.windows.WindowsDesktopDuplication.check;
import static mousemaster.platform.windows.WindowsDesktopDuplication.compile;
import static mousemaster.platform.windows.WindowsDesktopDuplication.out;
import static mousemaster.platform.windows.WindowsDesktopDuplication.pointerTo;
import static mousemaster.platform.windows.WindowsDesktopDuplication.release;
import static mousemaster.platform.windows.WindowsDesktopDuplication.ID3D11DEVICECONTEXT_COPYRESOURCE;

/**
 * Averages each source block of a frame into one rgb pixel on the GPU, as
 * DesktopCapture.boxDownscaledRgb does on the CPU, so that only the scaled frame is read back.
 */
final class WindowsBoxDownscale implements AutoCloseable {

    private static final int ID3D11DEVICE_CREATEBUFFER = 3;
    private static final int ID3D11DEVICE_CREATETEXTURE2D = 5;
    private static final int ID3D11DEVICE_CREATESHADERRESOURCEVIEW = 7;
    private static final int ID3D11DEVICE_CREATEUNORDEREDACCESSVIEW = 8;
    private static final int ID3D11DEVICE_CREATECOMPUTESHADER = 18;
    private static final int CTX_MAP = 14;
    private static final int CTX_UNMAP = 15;
    private static final int CTX_DISPATCH = 41;
    private static final int CTX_UPDATESUBRESOURCE = 48;
    private static final int CTX_CSSETSHADERRESOURCES = 67;
    private static final int CTX_CSSETUNORDEREDACCESSVIEWS = 68;
    private static final int CTX_CSSETSHADER = 69;
    private static final int CTX_CSSETCONSTANTBUFFERS = 71;

    private static final int D3D11_USAGE_DEFAULT = 0;
    private static final int D3D11_USAGE_STAGING = 3;
    private static final int D3D11_BIND_CONSTANT_BUFFER = 0x4;
    private static final int D3D11_BIND_SHADER_RESOURCE = 0x8;
    private static final int D3D11_BIND_UNORDERED_ACCESS = 0x80;
    private static final int D3D11_CPU_ACCESS_READ = 0x20000;
    private static final int D3D11_MAP_READ = 1;
    private static final int DXGI_FORMAT_R8G8B8A8_UINT = 30;
    private static final int DXGI_FORMAT_R8_UINT = 62;
    private static final int GROUP_SIZE = 8;

    private static final String SHADER = """
            cbuffer C : register(b0) { uint4 bounds; uint2 scaledSize; uint halfFloat; };
            Texture2D<float4> frame : register(t0);
            Texture2D<uint> srgbByHalf : register(t1);
            RWTexture2D<uint4> scaled : register(u0);
            uint srgb(float channel) {
                uint bits = f32tof16(channel);
                return srgbByHalf.Load(int3(bits & 255, bits >> 8, 0));
            }
            uint3 rgb(uint x, uint y) {
                float4 pixel = frame.Load(int3(x, y, 0));
                if (halfFloat != 0)
                    return uint3(srgb(pixel.r), srgb(pixel.g), srgb(pixel.b));
                return uint3(round(pixel.rgb * 255));
            }
            [numthreads(8, 8, 1)]
            void cs(uint3 id : SV_DispatchThreadID) {
                if (id.x >= scaledSize.x || id.y >= scaledSize.y)
                    return;
                uint fromX = bounds.x + id.x * bounds.z / scaledSize.x;
                uint toX = max(fromX + 1, bounds.x + (id.x + 1) * bounds.z / scaledSize.x);
                uint fromY = bounds.y + id.y * bounds.w / scaledSize.y;
                uint toY = max(fromY + 1, bounds.y + (id.y + 1) * bounds.w / scaledSize.y);
                uint3 sum = 0;
                for (uint y = fromY; y < toY; y++)
                    for (uint x = fromX; x < toX; x++)
                        sum += rgb(x, y);
                scaled[id.xy] = uint4(sum / ((toX - fromX) * (toY - fromY)), 0);
            }
            """;

    private final Pointer device;
    private final Pointer context;
    private Pointer computeShader;
    private Pointer constantBuffer;
    private Pointer srgbByHalfTexture;
    private Pointer srgbByHalfView;
    private Pointer scaledTexture;
    private Pointer scaledView;
    private Pointer staging;
    private int scaledWidth;
    private int scaledHeight;

    /** srgbByHalf is the sRGB byte of each FP16 channel value, or null for B8G8R8A8 frames. */
    WindowsBoxDownscale(Pointer device, Pointer context, byte[] srgbByHalf) {
        this.device = device;
        this.context = context;
        Pointer blob = compile(SHADER, "downscale.hlsl", "cs", "cs_5_0");
        try {
            computeShader = out(device, ID3D11DEVICE_CREATECOMPUTESHADER, "CreateComputeShader",
                    blobData(blob), blobSize(blob), Pointer.NULL);
        }
        finally {
            release(blob);
        }
        WindowsDesktopDuplication.BufferDesc bufferDesc =
                new WindowsDesktopDuplication.BufferDesc();
        bufferDesc.byteWidth = 32;
        bufferDesc.usage = D3D11_USAGE_DEFAULT;
        bufferDesc.bindFlags = D3D11_BIND_CONSTANT_BUFFER;
        bufferDesc.write();
        constantBuffer = out(device, ID3D11DEVICE_CREATEBUFFER, "CreateBuffer",
                bufferDesc.getPointer(), Pointer.NULL);
        if (srgbByHalf == null)
            return;
        // A 256 by 256 texture indexed by the low and the high byte of the FP16 bits.
        Memory table = new Memory(srgbByHalf.length);
        table.write(0, srgbByHalf, 0, srgbByHalf.length);
        Memory initialData = new Memory(16);
        initialData.setPointer(0, table);
        initialData.setInt(8, 256);
        initialData.setInt(12, 0);
        srgbByHalfTexture = out(device, ID3D11DEVICE_CREATETEXTURE2D, "CreateTexture2D",
                textureDesc(256, 256, DXGI_FORMAT_R8_UINT, D3D11_USAGE_DEFAULT,
                        D3D11_BIND_SHADER_RESOURCE, 0).getPointer(), initialData);
        table.close();
        srgbByHalfView = out(device, ID3D11DEVICE_CREATESHADERRESOURCEVIEW,
                "CreateShaderResourceView", srgbByHalfTexture, Pointer.NULL);
    }

    /** The frame texture must be bindable as a shader resource. */
    byte[] downscaledRgb(Pointer frame, Rectangle bounds, int scaledWidth, int scaledHeight) {
        ensureScaledTextures(scaledWidth, scaledHeight);
        Memory constants = new Memory(32);
        constants.setInt(0, bounds.x());
        constants.setInt(4, bounds.y());
        constants.setInt(8, bounds.width());
        constants.setInt(12, bounds.height());
        constants.setInt(16, scaledWidth);
        constants.setInt(20, scaledHeight);
        constants.setInt(24, srgbByHalfView == null ? 0 : 1);
        constants.setInt(28, 0);
        callVoid(context, CTX_UPDATESUBRESOURCE, constantBuffer, 0, Pointer.NULL, constants, 0,
                0);
        Pointer frameView = out(device, ID3D11DEVICE_CREATESHADERRESOURCEVIEW,
                "CreateShaderResourceView", frame, Pointer.NULL);
        try {
            callVoid(context, CTX_CSSETSHADER, computeShader, Pointer.NULL, 0);
            callVoid(context, CTX_CSSETCONSTANTBUFFERS, 0, 1, pointerTo(constantBuffer));
            callVoid(context, CTX_CSSETSHADERRESOURCES, 0, 2,
                    pointerTo(frameView, srgbByHalfView));
            callVoid(context, CTX_CSSETUNORDEREDACCESSVIEWS, 0, 1, pointerTo(scaledView),
                    Pointer.NULL);
            callVoid(context, CTX_DISPATCH, (scaledWidth + GROUP_SIZE - 1) / GROUP_SIZE,
                    (scaledHeight + GROUP_SIZE - 1) / GROUP_SIZE, 1);
            // Unbound, the frame view can go and the scaled texture can be copied.
            callVoid(context, CTX_CSSETSHADERRESOURCES, 0, 2, pointerTo(null, null));
            callVoid(context, CTX_CSSETUNORDEREDACCESSVIEWS, 0, 1, pointerTo((Pointer) null),
                    Pointer.NULL);
        }
        finally {
            release(frameView);
        }
        callVoid(context, ID3D11DEVICECONTEXT_COPYRESOURCE, staging, scaledTexture);
        WindowsDesktopFrameCapture.MappedSubresource mapped =
                new WindowsDesktopFrameCapture.MappedSubresource();
        check(call(context, CTX_MAP, staging, 0, D3D11_MAP_READ, 0, mapped.getPointer()),
                "Map");
        mapped.read();
        try {
            byte[] rgb = new byte[scaledWidth * scaledHeight * 3];
            Pointer pixels = mapped.pData;
            int rowPitch = mapped.rowPitch;
            ImageChunks.overRange(scaledHeight, scaledWidth, (from, to) -> {
                byte[] row = new byte[scaledWidth * 4];
                for (int y = from; y < to; y++) {
                    pixels.read((long) y * rowPitch, row, 0, row.length);
                    for (int x = 0, at = y * scaledWidth * 3; x < scaledWidth; x++, at += 3) {
                        rgb[at] = row[x * 4];
                        rgb[at + 1] = row[x * 4 + 1];
                        rgb[at + 2] = row[x * 4 + 2];
                    }
                }
            });
            return rgb;
        }
        finally {
            callVoid(context, CTX_UNMAP, staging, 0);
        }
    }

    private void ensureScaledTextures(int scaledWidth, int scaledHeight) {
        if (scaledTexture != null && scaledWidth == this.scaledWidth &&
            scaledHeight == this.scaledHeight)
            return;
        releaseScaledTextures();
        this.scaledWidth = scaledWidth;
        this.scaledHeight = scaledHeight;
        scaledTexture = out(device, ID3D11DEVICE_CREATETEXTURE2D, "CreateTexture2D",
                textureDesc(scaledWidth, scaledHeight, DXGI_FORMAT_R8G8B8A8_UINT,
                        D3D11_USAGE_DEFAULT, D3D11_BIND_UNORDERED_ACCESS, 0).getPointer(),
                Pointer.NULL);
        scaledView = out(device, ID3D11DEVICE_CREATEUNORDEREDACCESSVIEW,
                "CreateUnorderedAccessView", scaledTexture, Pointer.NULL);
        staging = out(device, ID3D11DEVICE_CREATETEXTURE2D, "CreateTexture2D",
                textureDesc(scaledWidth, scaledHeight, DXGI_FORMAT_R8G8B8A8_UINT,
                        D3D11_USAGE_STAGING, 0, D3D11_CPU_ACCESS_READ).getPointer(),
                Pointer.NULL);
    }

    private static WindowsDesktopDuplication.Texture2DDesc textureDesc(int width, int height,
                                                                       int format, int usage,
                                                                       int bindFlags,
                                                                       int cpuAccessFlags) {
        WindowsDesktopDuplication.Texture2DDesc desc =
                new WindowsDesktopDuplication.Texture2DDesc();
        desc.width = width;
        desc.height = height;
        desc.mipLevels = 1;
        desc.arraySize = 1;
        desc.format = format;
        desc.sampleCount = 1;
        desc.usage = usage;
        desc.bindFlags = bindFlags;
        desc.cpuAccessFlags = cpuAccessFlags;
        desc.write();
        return desc;
    }

    private void releaseScaledTextures() {
        release(staging);
        staging = null;
        release(scaledView);
        scaledView = null;
        release(scaledTexture);
        scaledTexture = null;
    }

    @Override
    public void close() {
        releaseScaledTextures();
        release(srgbByHalfView);
        srgbByHalfView = null;
        release(srgbByHalfTexture);
        srgbByHalfTexture = null;
        release(constantBuffer);
        constantBuffer = null;
        release(computeShader);
        computeShader = null;
    }
}

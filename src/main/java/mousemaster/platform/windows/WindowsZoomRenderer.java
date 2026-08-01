package mousemaster.platform.windows;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.PointerByReference;
import mousemaster.Rectangle;
import mousemaster.Zoom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static mousemaster.platform.windows.WindowsDesktopDuplication.call;
import static mousemaster.platform.windows.WindowsDesktopDuplication.callVoid;
import static mousemaster.platform.windows.WindowsDesktopDuplication.check;
import static mousemaster.platform.windows.WindowsDesktopDuplication.queryInterface;
import static mousemaster.platform.windows.WindowsDesktopDuplication.release;

/**
 * Draws a zoomed desktop into a window with Direct3D 11. It renders on
 * {@link WindowsDesktopDuplication}'s device: an output can only be duplicated once per
 * application, and only that device can read the frame.
 */
final class WindowsZoomRenderer {

    private static final Logger logger =
            LoggerFactory.getLogger(WindowsZoomRenderer.class);

    private static final Guid.IID IID_IDXGIFactory2 =
            new Guid.IID("50c83a1c-e072-4c48-87b0-3630fa36a6d0");
    private static final Guid.IID IID_IDXGIDevice =
            new Guid.IID("54ec77fa-1377-44e6-8c32-88fd5f44c84c");
    private static final Guid.IID IID_ID3D11Texture2D =
            new Guid.IID("6f15aaf2-d208-4e89-9ab4-489535d34f9c");

    private static final int IDXGIOBJECT_GETPARENT = 6;
    private static final int IDXGIDEVICE_GETADAPTER = 7;
    private static final int IDXGIFACTORY2_CREATESWAPCHAINFORHWND = 15;
    private static final int IDXGISWAPCHAIN_PRESENT = 8;
    private static final int IDXGISWAPCHAIN_GETBUFFER = 9;

    private static final int ID3D11DEVICE_CREATEBUFFER = 3;
    private static final int ID3D11DEVICE_CREATETEXTURE2D = 5;
    private static final int ID3D11DEVICE_CREATESHADERRESOURCEVIEW = 7;
    private static final int ID3D11DEVICE_CREATERENDERTARGETVIEW = 9;
    private static final int ID3D11DEVICE_CREATEVERTEXSHADER = 12;
    private static final int ID3D11DEVICE_CREATEPIXELSHADER = 15;
    private static final int ID3D11DEVICE_CREATESAMPLERSTATE = 23;

    private static final int CTX_VSSETCONSTANTBUFFERS = 7;
    private static final int CTX_PSSETSHADERRESOURCES = 8;
    private static final int CTX_PSSETSHADER = 9;
    private static final int CTX_PSSETSAMPLERS = 10;
    private static final int CTX_VSSETSHADER = 11;
    private static final int CTX_DRAW = 13;
    private static final int CTX_IASETPRIMITIVETOPOLOGY = 24;
    private static final int CTX_OMSETRENDERTARGETS = 33;
    private static final int CTX_RSSETVIEWPORTS = 44;
    private static final int CTX_UPDATESUBRESOURCE = 48;

    private static final int BLOB_GETBUFFERPOINTER = 3;
    private static final int BLOB_GETBUFFERSIZE = 4;

    private static final int D3D11_USAGE_DEFAULT = 0;
    private static final int D3D11_BIND_SHADER_RESOURCE = 0x8;
    private static final int D3D11_BIND_CONSTANT_BUFFER = 0x4;
    private static final int DXGI_FORMAT_B8G8R8A8_UNORM = 87;
    private static final int DXGI_USAGE_RENDER_TARGET_OUTPUT = 0x20;
    private static final int DXGI_SCALING_STRETCH = 0;
    private static final int DXGI_SWAP_EFFECT_DISCARD = 0;
    private static final int DXGI_ALPHA_MODE_IGNORE = 3;
    private static final int D3D11_FILTER_MIN_MAG_MIP_LINEAR = 0x15;
    private static final int D3D11_TEXTURE_ADDRESS_BORDER = 4;
    private static final int D3D11_COMPARISON_NEVER = 1;
    private static final int TRIANGLELIST = 4;

    private static final int S_OK = 0;

    private static final String SHADER = """
            cbuffer C : register(b0) { float4 uvRect; };
            struct VSOut { float4 pos : SV_POSITION; float2 uv : TEXCOORD; };
            VSOut vs(uint id : SV_VertexID) {
                float2 t = float2((id << 1) & 2, id & 2);
                VSOut o;
                o.pos = float4(t * float2(2, -2) + float2(-1, 1), 0, 1);
                o.uv = uvRect.xy + t * uvRect.zw;
                return o;
            }
            Texture2D tex : register(t0);
            SamplerState smp : register(s0);
            float4 ps(VSOut i) : SV_TARGET { return tex.Sample(smp, i.uv); }
            """;

    private interface D3DCompiler extends Library {
        D3DCompiler INSTANCE = Native.load("d3dcompiler_47", D3DCompiler.class);

        HRESULT D3DCompile(byte[] src, int srcSize, String sourceName, Pointer defines,
                           Pointer include, String entrypoint, String target, int flags1,
                           int flags2, PointerByReference code, PointerByReference errors);
    }

    private final WindowsDesktopDuplication duplication;

    private Pointer swapChain, renderTargetView;
    private Pointer sampleTexture, shaderResourceView, sampler, constantBuffer;
    private Pointer vertexShader, pixelShader;
    private Rectangle outputBounds;
    private Rectangle windowBounds;
    private WinDef.HWND hwnd;
    /** The device the resources above belong to. */
    private Pointer device;
    private boolean sampled;
    private boolean unavailable;

    WindowsZoomRenderer(WindowsDesktopDuplication duplication) {
        this.duplication = duplication;
    }

    /** False means Direct3D is unusable. */
    boolean prepare(WinDef.HWND hwnd, Rectangle screenRect) {
        if (unavailable)
            return false;
        try {
            if (!duplication.ensureInitialized(screenRect))
                return false;
            // The duplication takes its device down with it, and these resources are the
            // device's children.
            if (hwnd.equals(this.hwnd) && screenRect.equals(windowBounds) &&
                duplication.device().equals(device))
                return true;
            releaseResources();
            this.hwnd = hwnd;
            windowBounds = screenRect;
            device = duplication.device();
            outputBounds = duplication.outputBounds();
            createSwapChain(screenRect);
            createPipeline();
            logger.debug("Initialized the zoom renderer on " + screenRect);
            return true;
        }
        catch (Throwable e) {
            logger.error("Failed to initialize the zoom renderer: " + e.getMessage());
            releaseResources();
            unavailable = true;
            return false;
        }
    }

    void discardFrame() {
        sampled = false;
        duplication.discardFrame();
    }

    boolean render(Zoom zoom) {
        if (unavailable || swapChain == null)
            return false;
        try {
            sampled |= duplication.copyFrameInto(outputBounds, sampleTexture);
            // The texture holds nothing until a frame has landed.
            if (!sampled)
                return false;
            draw(zoom);
            // No vsync: blocking here would stall the loop that paces the frames.
            check(call(swapChain, IDXGISWAPCHAIN_PRESENT, 0, 0), "Present");
            return true;
        }
        catch (Throwable e) {
            logger.error("Failed to render the zoom: " + e.getMessage());
            unavailable = true;
            return false;
        }
    }

    private void draw(Zoom zoom) {
        int width = outputBounds.width();
        int height = outputBounds.height();
        float sourceWidth = (float) (width / zoom.percent());
        float sourceHeight = (float) (height / zoom.percent());
        // Unclamped: hints and the indicator are placed from the same center, so shifting
        // the view near a screen edge would disagree with them.
        float left = (float) (zoom.center().x() - outputBounds.x()) - sourceWidth / 2;
        float top = (float) (zoom.center().y() - outputBounds.y()) - sourceHeight / 2;
        Memory uvRect = new Memory(16);
        uvRect.setFloat(0, left / width);
        uvRect.setFloat(4, top / height);
        uvRect.setFloat(8, sourceWidth / width);
        uvRect.setFloat(12, sourceHeight / height);
        callVoid(duplication.context(), CTX_UPDATESUBRESOURCE, constantBuffer, 0,
                Pointer.NULL, uvRect, 0, 0);

        Viewport viewport = new Viewport();
        viewport.width = windowBounds.width();
        viewport.height = windowBounds.height();
        viewport.maxDepth = 1;
        viewport.write();
        Pointer context = duplication.context();
        callVoid(context, CTX_RSSETVIEWPORTS, 1, viewport.getPointer());
        callVoid(context, CTX_OMSETRENDERTARGETS, 1, pointerTo(renderTargetView),
                Pointer.NULL);
        callVoid(context, CTX_IASETPRIMITIVETOPOLOGY, TRIANGLELIST);
        callVoid(context, CTX_VSSETSHADER, vertexShader, Pointer.NULL, 0);
        callVoid(context, CTX_VSSETCONSTANTBUFFERS, 0, 1, pointerTo(constantBuffer));
        callVoid(context, CTX_PSSETSHADER, pixelShader, Pointer.NULL, 0);
        callVoid(context, CTX_PSSETSHADERRESOURCES, 0, 1, pointerTo(shaderResourceView));
        callVoid(context, CTX_PSSETSAMPLERS, 0, 1, pointerTo(sampler));
        callVoid(context, CTX_DRAW, 3, 0);
    }

    private void createSwapChain(Rectangle screenRect) {
        Pointer dxgiDevice = queryInterface(device, IID_IDXGIDevice);
        PointerByReference adapterOut = new PointerByReference();
        check(call(dxgiDevice, IDXGIDEVICE_GETADAPTER, adapterOut), "GetAdapter");
        release(dxgiDevice);
        Pointer adapter = adapterOut.getValue();
        PointerByReference factoryOut = new PointerByReference();
        check(call(adapter, IDXGIOBJECT_GETPARENT, IID_IDXGIFactory2, factoryOut),
                "GetParent(IDXGIFactory2)");
        release(adapter);
        Pointer factory = factoryOut.getValue();

        SwapChainDesc1 desc = new SwapChainDesc1();
        desc.width = screenRect.width();
        desc.height = screenRect.height();
        desc.format = DXGI_FORMAT_B8G8R8A8_UNORM;
        desc.sampleCount = 1;
        desc.bufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
        desc.bufferCount = 1;
        desc.scaling = DXGI_SCALING_STRETCH;
        // Bitblt, not flip: a flip model window bypasses DWM's redirection surface, and
        // the translucent overlays drawn on top of it then composite unreliably.
        desc.swapEffect = DXGI_SWAP_EFFECT_DISCARD;
        desc.alphaMode = DXGI_ALPHA_MODE_IGNORE;
        desc.write();
        PointerByReference swapChainOut = new PointerByReference();
        check(call(factory, IDXGIFACTORY2_CREATESWAPCHAINFORHWND, device, hwnd,
                        desc.getPointer(), Pointer.NULL, Pointer.NULL, swapChainOut),
                "CreateSwapChainForHwnd");
        release(factory);
        swapChain = swapChainOut.getValue();

        PointerByReference backBufferOut = new PointerByReference();
        check(call(swapChain, IDXGISWAPCHAIN_GETBUFFER, 0, IID_ID3D11Texture2D,
                backBufferOut), "GetBuffer");
        Pointer backBuffer = backBufferOut.getValue();
        PointerByReference viewOut = new PointerByReference();
        check(call(device, ID3D11DEVICE_CREATERENDERTARGETVIEW, backBuffer, Pointer.NULL,
                viewOut), "CreateRenderTargetView");
        release(backBuffer);
        renderTargetView = viewOut.getValue();
    }

    private void createPipeline() {
        // The duplicated texture is not bindable as a shader resource, hence this copy.
        WindowsDesktopDuplication.Texture2DDesc texture =
                new WindowsDesktopDuplication.Texture2DDesc();
        texture.width = outputBounds.width();
        texture.height = outputBounds.height();
        texture.mipLevels = 1;
        texture.arraySize = 1;
        texture.format = DXGI_FORMAT_B8G8R8A8_UNORM;
        texture.sampleCount = 1;
        texture.usage = D3D11_USAGE_DEFAULT;
        texture.bindFlags = D3D11_BIND_SHADER_RESOURCE;
        texture.write();
        PointerByReference textureOut = new PointerByReference();
        check(call(device, ID3D11DEVICE_CREATETEXTURE2D, texture.getPointer(),
                Pointer.NULL, textureOut), "CreateTexture2D");
        sampleTexture = textureOut.getValue();
        PointerByReference viewOut = new PointerByReference();
        check(call(device, ID3D11DEVICE_CREATESHADERRESOURCEVIEW, sampleTexture,
                Pointer.NULL, viewOut), "CreateShaderResourceView");
        shaderResourceView = viewOut.getValue();

        SamplerDesc samplerDesc = new SamplerDesc();
        samplerDesc.filter = D3D11_FILTER_MIN_MAG_MIP_LINEAR;
        // Border, not clamp: past the desktop edge this gives black rather than smearing
        // the edge pixels.
        samplerDesc.addressU = D3D11_TEXTURE_ADDRESS_BORDER;
        samplerDesc.addressV = D3D11_TEXTURE_ADDRESS_BORDER;
        samplerDesc.addressW = D3D11_TEXTURE_ADDRESS_BORDER;
        samplerDesc.comparisonFunc = D3D11_COMPARISON_NEVER;
        samplerDesc.maxLod = Float.MAX_VALUE;
        samplerDesc.write();
        PointerByReference samplerOut = new PointerByReference();
        check(call(device, ID3D11DEVICE_CREATESAMPLERSTATE, samplerDesc.getPointer(),
                samplerOut), "CreateSamplerState");
        sampler = samplerOut.getValue();

        BufferDesc bufferDesc = new BufferDesc();
        bufferDesc.byteWidth = 16;
        bufferDesc.usage = D3D11_USAGE_DEFAULT;
        bufferDesc.bindFlags = D3D11_BIND_CONSTANT_BUFFER;
        bufferDesc.write();
        PointerByReference bufferOut = new PointerByReference();
        check(call(device, ID3D11DEVICE_CREATEBUFFER, bufferDesc.getPointer(),
                Pointer.NULL, bufferOut), "CreateBuffer");
        constantBuffer = bufferOut.getValue();

        Pointer vertexBlob = compile("vs", "vs_4_0");
        PointerByReference vertexOut = new PointerByReference();
        check(call(device, ID3D11DEVICE_CREATEVERTEXSHADER, blobData(vertexBlob),
                blobSize(vertexBlob), Pointer.NULL, vertexOut), "CreateVertexShader");
        vertexShader = vertexOut.getValue();
        release(vertexBlob);
        Pointer pixelBlob = compile("ps", "ps_4_0");
        PointerByReference pixelOut = new PointerByReference();
        check(call(device, ID3D11DEVICE_CREATEPIXELSHADER, blobData(pixelBlob),
                blobSize(pixelBlob), Pointer.NULL, pixelOut), "CreatePixelShader");
        pixelShader = pixelOut.getValue();
        release(pixelBlob);
    }

    private static Pointer compile(String entrypoint, String target) {
        byte[] source = SHADER.getBytes();
        PointerByReference code = new PointerByReference();
        PointerByReference errors = new PointerByReference();
        HRESULT hr = D3DCompiler.INSTANCE.D3DCompile(source, source.length, "zoom.hlsl",
                Pointer.NULL, Pointer.NULL, entrypoint, target, 0, 0, code, errors);
        if (hr.intValue() != S_OK) {
            Pointer blob = errors.getValue();
            throw new IllegalStateException("D3DCompile " + entrypoint + ": " +
                                            (blob == null ? "" :
                                                    blobData(blob).getString(0)));
        }
        return code.getValue();
    }

    private void releaseResources() {
        release(renderTargetView);
        renderTargetView = null;
        release(swapChain);
        swapChain = null;
        release(shaderResourceView);
        shaderResourceView = null;
        release(sampleTexture);
        sampleTexture = null;
        release(sampler);
        sampler = null;
        release(constantBuffer);
        constantBuffer = null;
        release(vertexShader);
        vertexShader = null;
        release(pixelShader);
        pixelShader = null;
        outputBounds = null;
        windowBounds = null;
        hwnd = null;
        device = null;
        sampled = false;
    }

    private static Memory pointerTo(Pointer value) {
        Memory memory = new Memory(Native.POINTER_SIZE);
        memory.setPointer(0, value);
        return memory;
    }

    private static Pointer blobData(Pointer blob) {
        return (Pointer) WindowsDesktopDuplication.invoke(blob, BLOB_GETBUFFERPOINTER,
                Pointer.class);
    }

    private static int blobSize(Pointer blob) {
        return ((Number) WindowsDesktopDuplication.invoke(blob, BLOB_GETBUFFERSIZE,
                Long.class)).intValue();
    }

    public static class SwapChainDesc1 extends Structure {
        public int width, height, format, stereo;
        public int sampleCount, sampleQuality;
        public int bufferUsage, bufferCount, scaling, swapEffect, alphaMode, flags;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("width", "height", "format", "stereo", "sampleCount",
                    "sampleQuality", "bufferUsage", "bufferCount", "scaling",
                    "swapEffect", "alphaMode", "flags");
        }
    }

    public static class SamplerDesc extends Structure {
        public int filter, addressU, addressV, addressW;
        public float mipLodBias;
        public int maxAnisotropy, comparisonFunc;
        public float[] borderColor = new float[4];
        public float minLod, maxLod;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("filter", "addressU", "addressV", "addressW", "mipLodBias",
                    "maxAnisotropy", "comparisonFunc", "borderColor", "minLod", "maxLod");
        }
    }

    public static class BufferDesc extends Structure {
        public int byteWidth, usage, bindFlags, cpuAccessFlags, miscFlags, stride;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("byteWidth", "usage", "bindFlags", "cpuAccessFlags",
                    "miscFlags", "stride");
        }
    }

    public static class Viewport extends Structure {
        public float topLeftX, topLeftY, width, height, minDepth, maxDepth;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("topLeftX", "topLeftY", "width", "height", "minDepth",
                    "maxDepth");
        }
    }

}

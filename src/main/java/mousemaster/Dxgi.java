package mousemaster;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * See:
 * com.sun.jna.platform.win32.COM.Wbemcli.IWbemLocator
 * https://github.com/diederickh/screen_capture/blob/master/src/test/test_win_api_directx_research.cpp (contains useful comments)
 * https://github.com/roman380/DuplicationAndMediaFoundation/tree/master
 */
public interface Dxgi extends StdCallLibrary {

    Logger logger = LoggerFactory.getLogger(Dxgi.class);

    Dxgi INSTANCE = Native.load("dxgi", Dxgi.class);

    // https://learn.microsoft.com/en-us/windows/win32/direct3ddxgi/dxgi-error
    int DXGI_ERROR_NOT_FOUND = 0x887A0002;

    WinNT.HRESULT CreateDXGIFactory1(Guid.REFIID riid, PointerByReference ppFactory);

    static IDXGIFactory1 factory() {
        COMUtils.comIsInitialized();
        PointerByReference pFactory = new PointerByReference();
        WinNT.HRESULT hr = Dxgi.INSTANCE.CreateDXGIFactory1(
                new Guid.REFIID(IDXGIFactory1.IID_IDXGIFactory1), pFactory);
        COMUtils.checkRC(hr);
        return new IDXGIFactory1(pFactory.getValue());
    }

    class IDXGIFactory1 extends Unknown {

        // https://github.com/apitrace/dxsdk/blob/master/Include/dxgi.h
        public static final Guid.IID IID_IDXGIFactory1 =
                new Guid.IID("770aae78-f26f-4dba-a829-253c83d1b387");

        public IDXGIFactory1(Pointer pvInstance) {
            super(pvInstance);
        }

        public List<IDXGIAdapter> adapters() {
            List<IDXGIAdapter> adapters = new ArrayList<>();
            for (int adapterIndex = 0; ; adapterIndex++) {
                PointerByReference pAdapter = new PointerByReference();
                WinNT.HRESULT adapterHr = EnumAdapters1(adapterIndex, pAdapter);
                if (adapterHr.intValue() == DXGI_ERROR_NOT_FOUND)
                    break;
                COMUtils.checkRC(adapterHr);
                IDXGIAdapter adapter = new IDXGIAdapter(pAdapter.getValue());
                DXGI_ADAPTER_DESC adapterDesc = new DXGI_ADAPTER_DESC();
                COMUtils.checkRC(adapter.GetDesc(adapterDesc));
                logger.debug("Adapter " + adapterIndex + ": " +
                             String.valueOf(adapterDesc.Description));
                for (int outputIndex = 0; ; outputIndex++) {
                    PointerByReference pOutput = new PointerByReference();
                    WinNT.HRESULT outputHr = adapter.EnumOutputs(outputIndex, pOutput);
                    if (outputHr.intValue() == DXGI_ERROR_NOT_FOUND)
                        break;
                    COMUtils.checkRC(outputHr);
                    IDXGIOutput output = new IDXGIOutput(pOutput.getValue());
                    DXGI_OUTPUT_DESC outputDesc = new DXGI_OUTPUT_DESC();
                    COMUtils.checkRC(output.GetDesc(outputDesc));
                    logger.debug("Output " + outputIndex + ": " +
                                 String.valueOf(outputDesc.DeviceName) +
                                 ", attached to desktop: " +
                                 outputDesc.AttachedToDesktop);
                    if (!outputDesc.AttachedToDesktop.booleanValue())
                        continue;
                    PointerByReference pD3dDevice = new PointerByReference();
                    int D3D_DRIVER_TYPE_UNKNOWN = 0;
                    // You use D3D_DRIVER_TYPE_UNKNOWN when you are creating a Direct3D device and
                    // you already have a DXGI adapter (IDXGIAdapter) that you want to use for that device.
                    // In this scenario, you pass D3D_DRIVER_TYPE_UNKNOWN as the driver type and provide a non-null IDXGIAdapter
                    // pointer as the first argument to D3D11CreateDevice.
                    // The function will then create a device using the provided adapter, which represents a specific GPU
                    // or hardware device.
                    IntByReference pFeatureLevel = new IntByReference();
                    PointerByReference pContext = new PointerByReference();
                    WinNT.HRESULT createDeviceHr =
                            D3d11.INSTANCE.D3D11CreateDevice(adapter.getPointer(),
                                    D3D_DRIVER_TYPE_UNKNOWN, null, new WinDef.UINT(0),
                                    null, new WinDef.UINT(0), D3d11.D3D11_SDK_VERSION,
                                    pD3dDevice, pFeatureLevel, pContext);
                    COMUtils.checkRC(createDeviceHr);
                    ID3D11Device device = new ID3D11Device(pD3dDevice.getValue());
                    WinDef.UINT featureLevel = device.GetFeatureLevel(); // This works, so it means the device is correct.
                    PointerByReference pOutput1 = new PointerByReference();
                    COMUtils.checkRC(output.QueryInterface(
                            new Guid.REFIID(IDXGIOutput1.IID_IDXGIOutput1), pOutput1));
                    IDXGIOutput1 output1 = new IDXGIOutput1(pOutput1.getValue());
                    DXGI_OUTPUT_DESC output1Desc = new DXGI_OUTPUT_DESC();
                    COMUtils.checkRC(output1.GetDesc(output1Desc)); // This works, can be removed.
                    PointerByReference pOutputDuplication = new PointerByReference();
                    COMUtils.checkRC(output1.DuplicateOutput(device, pOutputDuplication));
                    IDXGIOutputDuplication outputDuplication =
                            new IDXGIOutputDuplication(pOutputDuplication.getValue());
                    DXGI_OUTDUPL_DESC duplicationDesc = new DXGI_OUTDUPL_DESC();
                    COMUtils.checkRC(outputDuplication.GetDesc(duplicationDesc));
                    DXGI_OUTDUPL_FRAME_INFO frameInfo = new DXGI_OUTDUPL_FRAME_INFO();
                    logger.debug("DesktopImageInSystemMemory = " +
                                 duplicationDesc.DesktopImageInSystemMemory);
                    PointerByReference pDesktopResource = new PointerByReference();
                    COMUtils.checkRC(
                            outputDuplication.AcquireNextFrame(new WinDef.UINT(0),
                                    frameInfo, pDesktopResource));
                    IDXGIResource desktopResource =
                            new IDXGIResource(pDesktopResource.getValue());
                    PointerByReference pCapturedTexture = new PointerByReference();
                    COMUtils.checkRC(desktopResource.QueryInterface(
                            new Guid.REFIID(ID3D11Texture2D.IID_ID3D11Texture2D),
                            pCapturedTexture));
                    ID3D11Texture2D capturedTexture =
                            new ID3D11Texture2D(pCapturedTexture.getValue());
                    D3D11_TEXTURE2D_DESC capturedTextureDesc = new D3D11_TEXTURE2D_DESC();
                    COMUtils.checkRC(capturedTexture.GetDesc(capturedTextureDesc));
                    ID3D11RenderTargetView renderTargetView =
                            createRenderTargetView(device,
                                    capturedTextureDesc.Width.intValue(),
                                    capturedTextureDesc.Height.intValue());
                    Shaders shaders = setupShaders(device);
                    ID3D11InputLayout shaderInputLayout =
                            createShaderInputLayout(device, shaders.vertexShaderBlob);
                    ID3D11Buffer vertexBuffer = createVertexBuffer(device);
                    ID3D11ShaderResourceView textureView =
                            createTextureView(device, capturedTexture,
                                    capturedTextureDesc);
//                    Zoom zoom = createZoomSettingsBuffer(device);
//                    ID3D11DeviceContext context =
//                            new ID3D11DeviceContext(pContext.getValue());
//                    updateZoomSettingsBuffer(context, zoom);
//                    prepareContextForRendering(context, shaderInputLayout, vertexBuffer,
//                            shaders.vertexShader, shaders.pixelShader,
//                            capturedTextureDesc.Width.intValue(),
//                            capturedTextureDesc.Height.intValue(), textureView,
//                            renderTargetView, zoom.zoomSettingsBuffer);
                    desktopResource.Release();
                    COMUtils.checkRC(outputDuplication.ReleaseFrame());
                    System.out.println("IDXGIFactory1.adapters");
                }
            }
            return adapters;
        }

        private WinNT.HRESULT EnumAdapters1(int Adapter, PointerByReference ppAdapter) {
            // 12-th method of IDXGIFactory1 in dxgi.h
            return (WinNT.HRESULT) _invokeNativeObject(7,
                    new Object[]{getPointer(), Adapter, ppAdapter}, WinNT.HRESULT.class);
        }
    }

    class IDXGIAdapter extends Unknown {

        public IDXGIAdapter(Pointer pvInstance) {
            super(pvInstance);
        }

        private WinNT.HRESULT EnumOutputs(int Output, PointerByReference ppOutput) {
            // 7-th method in dxgi.h
            return (WinNT.HRESULT) _invokeNativeObject(7,
                    new Object[]{getPointer(), Output, ppOutput}, WinNT.HRESULT.class);
        }

        public WinNT.HRESULT GetDesc(DXGI_ADAPTER_DESC desc) {
            // 8-th method in dxgi.h
            return (WinNT.HRESULT) _invokeNativeObject(8,
                    new Object[]{getPointer(), desc}, WinNT.HRESULT.class);
        }

    }

    class DXGI_ADAPTER_DESC extends Structure {

        public char[] Description = new char[128];
        public WinDef.UINT VendorId;
        public WinDef.UINT DeviceId;
        public WinDef.UINT SubSysId;
        public WinDef.UINT Revision;
        public BaseTSD.SIZE_T DedicatedVideoMemory;
        public BaseTSD.SIZE_T DedicatedSystemMemory;
        public BaseTSD.SIZE_T SharedSystemMemory;
        public WinNT.LUID AdapterLuid;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("Description", "VendorId", "DeviceId", "SubSysId", "Revision", "DedicatedVideoMemory", "DedicatedSystemMemory", "SharedSystemMemory", "AdapterLuid");
        }

    }

    class IDXGIOutput extends Unknown {

        public IDXGIOutput(Pointer pvInstance) {
            super(pvInstance);
        }

        public WinNT.HRESULT GetDesc(DXGI_OUTPUT_DESC desc) {
            // 7-th method in dxgi.h
            return (WinNT.HRESULT) _invokeNativeObject(7,
                    new Object[]{getPointer(), desc}, WinNT.HRESULT.class);
        }

    }

    /**
     * Output1 has the DuplicateOutput method.
     */
    class IDXGIOutput1 extends Unknown {

        // https://github.com/apitrace/dxsdk/blob/master/Include/dxgi1_2.h
        public static final Guid.IID IID_IDXGIOutput1 =
                new Guid.IID("00cddea8-939b-4b83-a340-a685226666cc");

        public IDXGIOutput1(Pointer pvInstance) {
            super(pvInstance);
        }

        public WinNT.HRESULT GetDesc(DXGI_OUTPUT_DESC desc) {
            // 7-th method in dxgi.h
            return (WinNT.HRESULT) _invokeNativeObject(7,
                    new Object[]{getPointer(), desc}, WinNT.HRESULT.class);
        }

        public WinNT.HRESULT DuplicateOutput(ID3D11Device device, PointerByReference ppOutputDuplication) {
            return (WinNT.HRESULT) _invokeNativeObject(22,
                    new Object[]{getPointer(), device, ppOutputDuplication},
                    WinNT.HRESULT.class);
        }

    }

    class DXGI_OUTPUT_DESC extends Structure {
        public char[] DeviceName = new char[32];
        public WinDef.RECT DesktopCoordinates;
        public WinDef.BOOL AttachedToDesktop;
        public int Rotation; // DXGI_MODE_ROTATION is typically an enum, mapped here as int
        public WinNT.HANDLE Monitor; // HMONITOR is a handle, mapped to HANDLE type in JNA

        @Override
        protected List<String> getFieldOrder() {
            return List.of("DeviceName", "DesktopCoordinates", "AttachedToDesktop", "Rotation", "Monitor");
        }

    }

    // https://github.com/apitrace/dxsdk/blob/master/Include/d3d11.h
    class ID3D11Device extends Unknown {

        public ID3D11Device() {
        }

        public ID3D11Device(Pointer pvInstance) {
            super(pvInstance);
        }

        public WinNT.HRESULT CreateBuffer(D3D11_BUFFER_DESC pDesc, D3D11_SUBRESOURCE_DATA pInitialData,
                                        PointerByReference ppBuffer) {
            return (WinNT.HRESULT) _invokeNativeObject(3,
                    new Object[]{getPointer(), pDesc, pInitialData, ppBuffer},
                    WinNT.HRESULT.class);
        }

        public WinNT.HRESULT CreateTexture2D(D3D11_TEXTURE2D_DESC desc,
                                             D3D11_SUBRESOURCE_DATA subresourceData,
                                             PointerByReference ppTexture) {
            return (WinNT.HRESULT) _invokeNativeObject(5,
                    new Object[]{getPointer(), desc, subresourceData, ppTexture},
                    WinNT.HRESULT.class);
        }

        public WinNT.HRESULT CreateShaderResourceView(Pointer texturePointer,
                                                      D3D11_SHADER_RESOURCE_VIEW_DESC pDesc,
                                             PointerByReference ppShaderResourceView) {
            return (WinNT.HRESULT) _invokeNativeObject(7,
                    new Object[]{getPointer(), texturePointer, pDesc,
                            ppShaderResourceView}, WinNT.HRESULT.class);
        }

        public WinNT.HRESULT CreateRenderTargetView(Pointer pResource, Pointer pDesc,
                                                    PointerByReference ppRenderTargetView) {
            return (WinNT.HRESULT) _invokeNativeObject(9,
                    new Object[]{getPointer(), pResource, pDesc, ppRenderTargetView},
                    WinNT.HRESULT.class);
        }

        public WinNT.HRESULT CreateInputLayout(Pointer pInputElementDescs,
                                               WinDef.UINT numElements,
                                               Pointer shaderBytecode,
                                               BaseTSD.SIZE_T shaderBytecodeLength,
                                               PointerByReference ppVertexShader) {
            return (WinNT.HRESULT) _invokeNativeObject(11,
                    new Object[]{getPointer(), pInputElementDescs, numElements,
                            shaderBytecode, shaderBytecodeLength, ppVertexShader},
                    WinNT.HRESULT.class);
        }

        public WinNT.HRESULT CreateVertexShader(Pointer bytecode,
                                                BaseTSD.SIZE_T bytecodeLength,
                                                Pointer classLinkage,
                                                PointerByReference ppVertexShader) {
            return (WinNT.HRESULT) _invokeNativeObject(12,
                    new Object[]{getPointer(), bytecode, bytecodeLength, classLinkage,
                            ppVertexShader}, WinNT.HRESULT.class);
        }

        public WinNT.HRESULT CreatePixelShader(Pointer bytecode,
                                               BaseTSD.SIZE_T bytecodeLength,
                                               Pointer classLinkage,
                                               PointerByReference ppPixelShader) {
            return (WinNT.HRESULT) _invokeNativeObject(15,
                    new Object[]{getPointer(), bytecode, bytecodeLength, classLinkage,
                            ppPixelShader}, WinNT.HRESULT.class);
        }

        public WinDef.UINT GetFeatureLevel() {
            return (WinDef.UINT) _invokeNativeObject(37,
                    new Object[]{getPointer()},
                    WinDef.UINT.class);
        }

    }

    static Shaders setupShaders(ID3D11Device device) {
        String vertexShaderCode = """
                    float4 main(float4 position : POSITION, float2 texCoord : TEXCOORD) : SV_POSITION
                    {
                        return position; // Pass-through
                    }
                    """;
        String pixelShaderCode = """
                    cbuffer ZoomSettings
                    {
                        float2 ZoomTopLeft; // Top-left point of the zoomed section
                        float ZoomFactor;   // Zoom factor
                    };

                    Texture2D shaderTexture;
                    SamplerState samplerState;

                    float4 main(float2 texCoord : TEXCOORD) : SV_TARGET
                    {
                        // Adjust texture coordinates based on zoom factor and top-left point
                        float2 adjustedCoord = (texCoord / ZoomFactor) + ZoomTopLeft;
                        // Sample the texture
                        float4 color = shaderTexture.Sample(samplerState, adjustedCoord);
                        return color;
                    }
                    """;
        // Compile Vertex Shader
        PointerByReference pVertexShaderBlob = new PointerByReference();
        Memory vertexShaderCodeMemory = new Memory(vertexShaderCode.length() + 1);
        vertexShaderCodeMemory.write(0, vertexShaderCode.getBytes(), 0, vertexShaderCode.length());
        WinNT.HRESULT hr = D3dCompiler.INSTANCE.D3DCompile(
                vertexShaderCodeMemory,
                new BaseTSD.SIZE_T(vertexShaderCode.length()),
                null,
                null,
                null,
                "main",
                "vs_5_0",
                new WinDef.UINT(0),
                new WinDef.UINT(0),
                pVertexShaderBlob,
                null
        );
        COMUtils.checkRC(hr);
        ID3DBlob vertexShaderBlob = new ID3DBlob(pVertexShaderBlob.getValue());
        // Create the vertex shader object
        PointerByReference pVertexShader = new PointerByReference();
        hr = device.CreateVertexShader(vertexShaderBlob.GetBufferPointer(),
                vertexShaderBlob.GetBufferSize(), null, pVertexShader);
        COMUtils.checkRC(hr);
        // Compile Pixel Shader
        PointerByReference pPixelShaderBlob = new PointerByReference();
        Memory pixelShaderCodeMemory = new Memory(pixelShaderCode.length() + 1);
        pixelShaderCodeMemory.write(0, pixelShaderCode.getBytes(), 0, pixelShaderCode.length());
        hr = D3dCompiler.INSTANCE.D3DCompile(
                pixelShaderCodeMemory,
                new BaseTSD.SIZE_T(pixelShaderCode.length()),
                null,
                null,
                null,
                "main",
                "ps_5_0",
                new WinDef.UINT(0),
                new WinDef.UINT(0),
                pPixelShaderBlob,
                null
        );
        COMUtils.checkRC(hr);
        ID3DBlob pixelShaderBlob = new ID3DBlob(pPixelShaderBlob.getValue());
        // Create the pixel shader object
        PointerByReference pPixelShader = new PointerByReference();
        hr = device.CreatePixelShader(pixelShaderBlob.GetBufferPointer(),
                pixelShaderBlob.GetBufferSize(), null, pPixelShader);
        COMUtils.checkRC(hr);
        // Now you have pVertexShader and pPixelShader that you can set in your device context
        return new Shaders(vertexShaderBlob, new ID3D11VertexShader(pVertexShader.getValue()),
                new ID3D11PixelShader(pPixelShader.getValue()));
    }

    record Shaders(ID3DBlob vertexShaderBlob, ID3D11VertexShader vertexShader,
                   ID3D11PixelShader pixelShader) {

    }

    private static ID3D11InputLayout createShaderInputLayout(ID3D11Device device,
                                                             ID3DBlob vertexShaderBlob) {
        int DXGI_FORMAT_R32G32B32A32_FLOAT = 2;
        int D3D11_INPUT_PER_VERTEX_DATA = 0;
        int DXGI_FORMAT_R32G32_FLOAT = 16;
        D3D11_INPUT_ELEMENT_DESC[] layoutArray = {
                new D3D11_INPUT_ELEMENT_DESC(
                        "POSITION", 0, DXGI_FORMAT_R32G32B32A32_FLOAT, 0, 0,
                        D3D11_INPUT_PER_VERTEX_DATA, 0
                ),
                new D3D11_INPUT_ELEMENT_DESC(
                        "TEXCOORD", 0, DXGI_FORMAT_R32G32_FLOAT, 0, 16,
                        D3D11_INPUT_PER_VERTEX_DATA, 0
                )
        };
        // Calculate the total size of the layout
        int layoutSize = 0;
        for (D3D11_INPUT_ELEMENT_DESC desc : layoutArray)
            layoutSize += desc.size();
        // Allocate contiguous memory for the layout
        Memory layoutMemory = new Memory(layoutSize);
        int offset = 0;
        for (D3D11_INPUT_ELEMENT_DESC desc : layoutArray) {
            desc.write();
            byte[] data = desc.getPointer().getByteArray(0, desc.size());
            layoutMemory.write(offset, data, 0, desc.size());
            offset += desc.size();
        }
        PointerByReference pInputLayout = new PointerByReference();
        WinNT.HRESULT hr = device.CreateInputLayout(
                layoutMemory,
                new WinDef.UINT(layoutArray.length),
                vertexShaderBlob.GetBufferPointer(),
                vertexShaderBlob.GetBufferSize(),
                pInputLayout
        );
        COMUtils.checkRC(hr);
        return new ID3D11InputLayout(pInputLayout.getValue());
    }

    class D3D11_INPUT_ELEMENT_DESC extends Structure {

        public String SemanticName;
        public WinDef.UINT SemanticIndex;
        public int Format; // DXGI_FORMAT is typically represented as an integer in JNA
        public WinDef.UINT InputSlot;
        public WinDef.UINT AlignedByteOffset;
        public int InputSlotClass; // D3D11_INPUT_CLASSIFICATION can be represented as an integer
        public WinDef.UINT InstanceDataStepRate;

        public D3D11_INPUT_ELEMENT_DESC(String SemanticName, int SemanticIndex,
                                        int Format, int InputSlot,
                                        int AlignedByteOffset, int InputSlotClass,
                                        int InstanceDataStepRate) {
            this.SemanticName = SemanticName;
            this.SemanticIndex = new WinDef.UINT(SemanticIndex);
            this.Format = Format;
            this.InputSlot = new WinDef.UINT(InputSlot);
            this.AlignedByteOffset = new WinDef.UINT(AlignedByteOffset);
            this.InputSlotClass = InputSlotClass;
            this.InstanceDataStepRate = new WinDef.UINT(InstanceDataStepRate);
        }

        @Override
        protected List<String> getFieldOrder() {
            return List.of("SemanticName", "SemanticIndex", "Format", "InputSlot",
                    "AlignedByteOffset", "InputSlotClass", "InstanceDataStepRate");
        }

    }

    static ID3D11Buffer createVertexBuffer(ID3D11Device device) {
        // Full-screen quad
        Vertex[] vertices = {
                new Vertex(-1.0f, -1.0f, 0.0f, 0.0f, 1.0f), // Bottom left
                new Vertex(-1.0f,  1.0f, 0.0f, 0.0f, 0.0f), // Top left
                new Vertex( 1.0f, -1.0f, 0.0f, 1.0f, 1.0f), // Bottom right
                new Vertex( 1.0f,  1.0f, 0.0f, 1.0f, 0.0f)  // Top right
        };
        // Define the vertex buffer
        D3D11_BUFFER_DESC bufferDesc = new D3D11_BUFFER_DESC();
        bufferDesc.Usage = D3d11.D3D11_USAGE_DEFAULT;
        bufferDesc.ByteWidth = new WinDef.UINT((long) vertices.length * vertices[0].size());
        bufferDesc.BindFlags = new WinDef.UINT(D3d11.D3D11_BIND_VERTEX_BUFFER);
        bufferDesc.CPUAccessFlags = new WinDef.UINT(0);
        // Write vertex data to a contiguous memory block
        Memory vertexData = new Memory(bufferDesc.ByteWidth.longValue());
        for (int i = 0; i < vertices.length; i++) {
            vertices[i].write();
            byte[] vertexBytes = vertices[i].getPointer().getByteArray(0, vertices[i].size());
            vertexData.write((long) i * vertices[i].size(), vertexBytes, 0, vertices[i].size());
        }
        // Initialize the subresource data
        D3D11_SUBRESOURCE_DATA subresourceData = new D3D11_SUBRESOURCE_DATA();
        subresourceData.pSysMem = vertexData;
        // Create the vertex buffer
        PointerByReference pBuffer = new PointerByReference();
        WinNT.HRESULT hr = device.CreateBuffer(bufferDesc, subresourceData, pBuffer);
        COMUtils.checkRC(hr);
        return new ID3D11Buffer(pBuffer.getValue());
    }

    class D3D11_SUBRESOURCE_DATA extends Structure {
        public Pointer pSysMem;
        public WinDef.UINT SysMemPitch;
        public WinDef.UINT SysMemSlicePitch;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("pSysMem", "SysMemPitch", "SysMemSlicePitch");
        }
    }

    static ID3D11ShaderResourceView createTextureView(ID3D11Device device,
                                                      ID3D11Texture2D capturedTexture,
                                                      D3D11_TEXTURE2D_DESC capturedTextureDesc) {
        D3D11_SHADER_RESOURCE_VIEW_DESC srvDesc = new D3D11_SHADER_RESOURCE_VIEW_DESC();
        srvDesc.Format = capturedTextureDesc.Format;
        int D3D11_SRV_DIMENSION_TEXTURE2D = 4;
        srvDesc.ViewDimension = D3D11_SRV_DIMENSION_TEXTURE2D;
        srvDesc.Texture2D.MostDetailedMip = new WinDef.UINT(0);
        srvDesc.Texture2D.MipLevels = new WinDef.UINT(-1); // Use all MIP levels

        PointerByReference pShaderResourceView = new PointerByReference();
        WinNT.HRESULT hr =
                device.CreateShaderResourceView(capturedTexture.getPointer(), srvDesc,
                        pShaderResourceView);
        COMUtils.checkRC(hr);
        return new ID3D11ShaderResourceView(pShaderResourceView.getValue());
    }

    static void prepareContextForRendering(ID3D11DeviceContext context,
                                           ID3D11InputLayout shaderInputLayout,
                                           ID3D11Buffer vertexBuffer,
                                           ID3D11VertexShader vertexShader,
                                           ID3D11PixelShader pixelShader, int renderWidth,
                                           int renderHeight,
                                           ID3D11ShaderResourceView textureView,
                                           ID3D11RenderTargetView renderTargetView,
                                           ID3D11Buffer zoomSettingsBuffer) {
        context.IASetInputLayout(shaderInputLayout);
        WinDef.UINT stride = new WinDef.UINT(3 * Float.SIZE / 8 + 2 * Float.SIZE /
                                                                  8); // 3 floats for position, 2 floats for texture coordinates
        WinDef.UINT offset = new WinDef.UINT(0);
        context.IASetVertexBuffers(new WinDef.UINT(0), new WinDef.UINT(1),
                new ID3D11Buffer[]{vertexBuffer}, new WinDef.UINT[]{stride},
                new WinDef.UINT[]{offset});
        context.VSSetShader(vertexShader, null, new WinDef.UINT(0));
        context.PSSetShader(pixelShader, null, new WinDef.UINT(0));
        int D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST = 4;
        context.IASetPrimitiveTopology(D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST); // For drawing a quad

        D3D11_VIEWPORT viewport = new D3D11_VIEWPORT();
        viewport.Width = renderWidth;
        viewport.Height = renderHeight;
        viewport.MinDepth = 0.0f;
        viewport.MaxDepth = 1.0f;
        viewport.TopLeftX = 0;
        viewport.TopLeftY = 0;
        context.RSSetViewports(1, new D3D11_VIEWPORT[]{viewport});
        context.PSSetShaderResources(new WinDef.UINT(0), new WinDef.UINT(1),
                new ID3D11ShaderResourceView[]{textureView});
        context.OMSetRenderTargets(new WinDef.UINT(1),
                new ID3D11RenderTargetView[]{renderTargetView}, null);
        // Bind the constant buffer to the pixel shader
        context.PSSetConstantBuffers(new WinDef.UINT(0), new WinDef.UINT(1),
                new ID3D11Buffer[]{zoomSettingsBuffer});
        context.Draw(new WinDef.UINT(6), new WinDef.UINT(0)); // 6 vertices for two triangles forming a quad
    }

    static Zoom createZoomSettingsBuffer(ID3D11Device device) {
        ZoomSettings zoomSettings = new ZoomSettings();
        zoomSettings.zoomFactor = 2.0f; // Default zoom factor
        zoomSettings.topLeftX = 0.0f; // Default top left X
        zoomSettings.topLeftY = 0.0f; // Default top left Y

        // Create a constant buffer
        D3D11_BUFFER_DESC constantBufferDesc = new D3D11_BUFFER_DESC();
        constantBufferDesc.Usage = D3d11.D3D11_USAGE_DYNAMIC;
        int byteWidth = zoomSettings.size();
        // TODO how does this alignment work?
        byteWidth = (byteWidth + 15) & ~15; // Align to 16 bytes
        constantBufferDesc.ByteWidth = new WinDef.UINT(byteWidth);
        long D3D11_BIND_CONSTANT_BUFFER = 0x4;
        constantBufferDesc.BindFlags = new WinDef.UINT(D3D11_BIND_CONSTANT_BUFFER);
        long D3D11_CPU_ACCESS_WRITE	= 0x10000;
        constantBufferDesc.CPUAccessFlags = new WinDef.UINT(D3D11_CPU_ACCESS_WRITE);

        PointerByReference pBuffer = new PointerByReference();
        WinNT.HRESULT hr = device.CreateBuffer(constantBufferDesc, null, pBuffer);
        COMUtils.checkRC(hr);
        return new Zoom(zoomSettings, new ID3D11Buffer(pBuffer.getValue()));
    }

    record Zoom(ZoomSettings zoomSettings, ID3D11Buffer zoomSettingsBuffer) {

    }

    static void updateZoomSettingsBuffer(ID3D11DeviceContext context, Zoom zoom) {
        ZoomSettings zoomSettings = zoom.zoomSettings;
        ID3D11Buffer zoomSettingsBuffer = zoom.zoomSettingsBuffer;
        // Map the buffer
        D3D11_MAPPED_SUBRESOURCE mappedResource = new D3D11_MAPPED_SUBRESOURCE();
        int D3D11_MAP_WRITE_DISCARD = 4;
        WinNT.HRESULT hr = context.Map(zoomSettingsBuffer.getPointer(), new WinDef.UINT(0),
                D3D11_MAP_WRITE_DISCARD, new WinDef.UINT(0), mappedResource);
        COMUtils.checkRC(hr);

        // Copy the zoom settings data
        Memory mem = new Memory(zoomSettings.size());
        zoomSettings.write();
        mem.write(0, zoomSettings.getPointer().getByteArray(0, zoomSettings.size()), 0,
                zoomSettings.size());
        mappedResource.pData.write(0, mem.getByteArray(0, (int) mem.size()), 0,
                (int) mem.size());

        // Unmap the buffer
        context.Unmap(zoomSettingsBuffer.getPointer(), new WinDef.UINT(0));
    }

    class ZoomSettings extends Structure {
        public float zoomFactor;
        public float topLeftX;
        public float topLeftY;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("zoomFactor", "topLeftX", "topLeftY");
        }
    }

    class D3D11_MAPPED_SUBRESOURCE extends Structure {

        public Pointer pData; // void* is represented as Pointer in JNA
        public WinDef.UINT RowPitch;
        public WinDef.UINT DepthPitch;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("pData", "RowPitch", "DepthPitch");
        }
    }

    class IDXGIOutputDuplication extends Unknown {

        // https://github.com/apitrace/dxsdk/blob/master/Include/dxgi1_2.h

        public IDXGIOutputDuplication(Pointer pvInstance) {
            super(pvInstance);
        }

        public WinNT.HRESULT GetDesc(DXGI_OUTDUPL_DESC desc) {
            return (WinNT.HRESULT) _invokeNativeObject(7,
                    new Object[]{getPointer(), desc}, WinNT.HRESULT.class);
        }

        public WinNT.HRESULT AcquireNextFrame(WinDef.UINT TimeoutInMilliseconds, DXGI_OUTDUPL_FRAME_INFO frameInfo,
                                              PointerByReference ppDesktopResource) {
            return (WinNT.HRESULT) _invokeNativeObject(8,
                    new Object[]{getPointer(), TimeoutInMilliseconds, frameInfo,
                            ppDesktopResource}, WinNT.HRESULT.class);
        }

        public WinNT.HRESULT ReleaseFrame() {
            return (WinNT.HRESULT) _invokeNativeObject(14,
                    new Object[]{getPointer()}, WinNT.HRESULT.class);
        }

    }

    class DXGI_OUTDUPL_DESC extends Structure {
        public DXGI_MODE_DESC ModeDesc;
        public int Rotation; // Assuming DXGI_MODE_ROTATION is an enum represented as int
        public WinDef.BOOL DesktopImageInSystemMemory;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("ModeDesc", "Rotation", "DesktopImageInSystemMemory");
        }
    }

    class DXGI_MODE_DESC extends Structure {
        public WinDef.UINT Width;
        public WinDef.UINT Height;
        public DXGI_RATIONAL RefreshRate;
        public int Format;
        public int ScanlineOrdering;
        public int Scaling;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("Width", "Height", "RefreshRate", "Format", "ScanlineOrdering", "Scaling");
        }

        public static class DXGI_RATIONAL extends Structure {
            public WinDef.UINT Numerator;
            public WinDef.UINT Denominator;

            @Override
            protected List<String> getFieldOrder() {
                return List.of("Numerator", "Denominator");
            }
        }

    }

    class DXGI_OUTDUPL_FRAME_INFO extends Structure {
        public WinNT.LARGE_INTEGER LastPresentTime;
        public WinNT.LARGE_INTEGER LastMouseUpdateTime;
        public WinDef.UINT AccumulatedFrames;
        public WinDef.BOOL RectsCoalesced;
        public WinDef.BOOL ProtectedContentMaskedOut;
        public DXGI_OUTDUPL_POINTER_POSITION PointerPosition;
        public WinDef.UINT TotalMetadataBufferSize;
        public WinDef.UINT PointerShapeBufferSize;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("LastPresentTime", "LastMouseUpdateTime",
                    "AccumulatedFrames", "RectsCoalesced", "ProtectedContentMaskedOut",
                    "PointerPosition", "TotalMetadataBufferSize",
                    "PointerShapeBufferSize");
        }
    }

    class DXGI_OUTDUPL_POINTER_POSITION extends Structure {
        public POINT Position;
        public WinDef.BOOL Visible;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("Position", "Visible");
        }
    }

    class POINT extends Structure {
        public WinDef.LONG x;
        public WinDef.LONG y;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("x", "y");
        }
    }

    class IDXGIResource extends Unknown {

        public IDXGIResource(Pointer pvInstance) {
            super(pvInstance);
        }

    }

    class ID3D11Texture2D extends Unknown {

        public static final Guid.IID IID_ID3D11Texture2D =
                new Guid.IID("6f15aaf2-d208-4e89-9ab4-489535d34f9c");

        public ID3D11Texture2D(Pointer pvInstance) {
            super(pvInstance);
        }

        public WinNT.HRESULT GetDesc(D3D11_TEXTURE2D_DESC desc) {
            return (WinNT.HRESULT) _invokeNativeObject(10,
                    new Object[]{getPointer(), desc}, WinNT.HRESULT.class);
        }

    }

    class D3D11_TEXTURE2D_DESC extends Structure {
        public WinDef.UINT Width;
        public WinDef.UINT Height;
        public WinDef.UINT MipLevels;
        public WinDef.UINT ArraySize;
        public int Format; // DXGI_FORMAT is typically an enum, so it's represented as an int
        public DXGI_SAMPLE_DESC SampleDesc; // Assuming DXGI_SAMPLE_DESC is another structure
        public int Usage; // D3D11_USAGE is an enum, so it's represented as an int
        public WinDef.UINT BindFlags;
        public WinDef.UINT CPUAccessFlags;
        public WinDef.UINT MiscFlags;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("Width", "Height", "MipLevels", "ArraySize", "Format",
                    "SampleDesc", "Usage", "BindFlags", "CPUAccessFlags", "MiscFlags");
        }

        public static class DXGI_SAMPLE_DESC extends Structure {
            public WinDef.UINT Count;
            public WinDef.UINT Quality;

            @Override
            protected List<String> getFieldOrder() {
                return List.of("Count", "Quality");
            }
        }
    }

    int DXGI_FORMAT_R8G8B8A8_UNORM = 28;

    // Creates the texture that will contain the zoomed-in screen.
    static ID3D11RenderTargetView createRenderTargetView(ID3D11Device device, int screenWidth, int screenHeight) {
        // Define texture description
        D3D11_TEXTURE2D_DESC textureDesc = new D3D11_TEXTURE2D_DESC();
        textureDesc.Width = new WinDef.UINT(screenWidth);
        textureDesc.Height = new WinDef.UINT(screenHeight);
        textureDesc.MipLevels = new WinDef.UINT(1);
        textureDesc.ArraySize = new WinDef.UINT(1);
        textureDesc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
        textureDesc.SampleDesc = new D3D11_TEXTURE2D_DESC.DXGI_SAMPLE_DESC();
        textureDesc.SampleDesc.Count = new WinDef.UINT(1);
        textureDesc.SampleDesc.Quality = new WinDef.UINT(0);
        textureDesc.Usage = D3d11.D3D11_USAGE_DEFAULT;
        textureDesc.BindFlags = new WinDef.UINT(D3d11.D3D11_BIND_RENDER_TARGET);
        textureDesc.CPUAccessFlags = new WinDef.UINT(0);
        textureDesc.MiscFlags = new WinDef.UINT(0);
        // Create the texture
        PointerByReference pCopiedTexture = new PointerByReference();
        COMUtils.checkRC(device.CreateTexture2D(textureDesc, null, pCopiedTexture));
        // Retrieve the texture object
        ID3D11Texture2D copiedTexture = new ID3D11Texture2D(pCopiedTexture.getValue());
        // Create the render target view
        PointerByReference pRenderTargetView = new PointerByReference();
        COMUtils.checkRC(device.CreateRenderTargetView(copiedTexture.getPointer(), null,
                pRenderTargetView));
        // TODO copiedTexture.Release(); when COMUtils.FAILED(hr)?
        return new ID3D11RenderTargetView(pRenderTargetView.getValue());
    }

    class ID3D11RenderTargetView extends Unknown {

        public ID3D11RenderTargetView(Pointer pvInstance) {
            super(pvInstance);
        }

    }

    // https://github.com/apitrace/dxsdk/blob/master/Include/d3dcommon.h
    class ID3DBlob extends Unknown {

        public ID3DBlob(Pointer pvInstance) {
            super(pvInstance);
        }

        public Pointer GetBufferPointer() {
            return (Pointer) _invokeNativeObject(3,
                    new Object[]{getPointer()}, Pointer.class);
        }

        public BaseTSD.SIZE_T GetBufferSize() {
            return (BaseTSD.SIZE_T) _invokeNativeObject(4,
                    new Object[]{getPointer()}, BaseTSD.SIZE_T.class);
        }

    }

    class ID3D11DeviceContext extends Unknown {

        public ID3D11DeviceContext(Pointer pvInstance) {
            super(pvInstance);
        }

        public void PSSetShaderResources(WinDef.UINT StartSlot,
                                         WinDef.UINT NumViews,
                                         ID3D11ShaderResourceView[] shaderResourceViews) {
            _invokeNativeVoid(8,
                    new Object[]{getPointer(), StartSlot, NumViews, shaderResourceViews});
        }

        public void PSSetShader(ID3D11PixelShader pixelShader, Pointer pClassInstances,
                                WinDef.UINT numClassInstances) {
            _invokeNativeVoid(9, new Object[]{getPointer(), pixelShader, pClassInstances,
                    numClassInstances});
        }

        public void VSSetShader(ID3D11VertexShader vertexShader, Pointer pClassInstances,
                                WinDef.UINT numClassInstances) {
            _invokeNativeVoid(11,
                    new Object[]{getPointer(), vertexShader, pClassInstances,
                            numClassInstances});
        }

        public void Draw(WinDef.UINT VertexCount, WinDef.UINT StartVertexLocation) {
            _invokeNativeVoid(13,
                    new Object[]{getPointer(), VertexCount, StartVertexLocation});
        }

        public WinNT.HRESULT Map(Pointer pResource, WinDef.UINT Subresource,
                                 int MapType, WinDef.UINT MapFlags,
                                 D3D11_MAPPED_SUBRESOURCE pMappedResource) {
            return (WinNT.HRESULT) _invokeNativeObject(14,
                    new Object[]{getPointer(), pResource, Subresource, MapType, MapFlags,
                            pMappedResource}, WinNT.HRESULT.class);
        }

        public void Unmap(Pointer pResource, WinDef.UINT Subresource) {
            _invokeNativeVoid(15, new Object[]{getPointer(), pResource, Subresource});
        }

        public void PSSetConstantBuffers(WinDef.UINT StartSlot,
                                         WinDef.UINT NumBuffers, ID3D11Buffer[] buffers) {
            _invokeNativeVoid(16, new Object[]{getPointer(), StartSlot, NumBuffers, buffers});
        }

        public void IASetInputLayout(ID3D11InputLayout pInputLayout) {
            _invokeNativeVoid(17, new Object[]{getPointer(), pInputLayout});
        }

        public void IASetVertexBuffers(WinDef.UINT StartSlot, WinDef.UINT NumBuffers,
                                       ID3D11Buffer[] ppVertexBuffers,
                                       WinDef.UINT[] pStrides, WinDef.UINT[] pOffsets) {

            _invokeNativeVoid(18,
                    new Object[]{getPointer(), StartSlot, NumBuffers, ppVertexBuffers,
                            pStrides, pOffsets});
        }

        public void IASetPrimitiveTopology(int Topology) {
            _invokeNativeVoid(24, new Object[]{getPointer(), Topology});
        }

        public void OMSetRenderTargets(WinDef.UINT NumViews,
                                       ID3D11RenderTargetView[] renderTargetViews,
                                       Pointer pDepthStencilView) {
            _invokeNativeVoid(33,
                    new Object[]{getPointer(), NumViews, renderTargetViews, pDepthStencilView});
        }

        public void RSSetViewports(int Topology, D3D11_VIEWPORT[] viewports) {
            _invokeNativeVoid(44, new Object[]{getPointer(), Topology, viewports});
        }

    }

    class ID3D11VertexShader extends Unknown {

        public ID3D11VertexShader(Pointer pvInstance) {
            super(pvInstance);
        }

    }

    class ID3D11PixelShader extends Unknown {

        public ID3D11PixelShader(Pointer pvInstance) {
            super(pvInstance);
        }

    }

    class ID3D11InputLayout extends Unknown {

        public ID3D11InputLayout(Pointer pvInstance) {
            super(pvInstance);
        }

    }

    class Vertex extends Structure {

        public float x, y, z; // Position
        public float u, v;    // Texture coordinates

        public Vertex(float x, float y, float z, float u, float v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = u;
            this.v = v;
        }

        @Override
        protected List<String> getFieldOrder() {
            return List.of("x", "y", "z", "u", "v");
        }
    }

    class D3D11_BUFFER_DESC extends Structure {

        public WinDef.UINT ByteWidth;
        public int Usage; // D3D11_USAGE as integer
        public WinDef.UINT BindFlags;
        public WinDef.UINT CPUAccessFlags;
        public WinDef.UINT MiscFlags;
        public WinDef.UINT StructureByteStride;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("ByteWidth", "Usage", "BindFlags", "CPUAccessFlags",
                    "MiscFlags", "StructureByteStride");
        }
    }

    class ID3D11Buffer extends Unknown {

        public ID3D11Buffer(Pointer pvInstance) {
            super(pvInstance);
        }

    }

    class D3D11_SHADER_RESOURCE_VIEW_DESC extends Structure {

        public int Format; // DXGI_FORMAT as integer
        public int ViewDimension; // D3D11_SRV_DIMENSION as integer

        public Texture2D Texture2D;
        // Note: the original D3D11_SHADER_RESOURCE_VIEW_DESC contains a union instead of just a Texture2D.

        @Override
        protected List<String> getFieldOrder() {
            return List.of("Format", "ViewDimension", "Texture2D");
        }
    }

    class Texture2D extends Structure {
        public WinDef.UINT MostDetailedMip;
        public WinDef.UINT MipLevels;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("MostDetailedMip", "MipLevels");
        }
    }

    class ID3D11ShaderResourceView extends Unknown {

        public ID3D11ShaderResourceView(Pointer pvInstance) {
            super(pvInstance);
        }

    }

    class D3D11_VIEWPORT extends Structure {

        public float TopLeftX;
        public float TopLeftY;
        public float Width;
        public float Height;
        public float MinDepth;
        public float MaxDepth;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("TopLeftX", "TopLeftY", "Width", "Height", "MinDepth", "MaxDepth");
        }
    }

}

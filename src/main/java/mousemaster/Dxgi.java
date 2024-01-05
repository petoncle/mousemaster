package mousemaster;

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

        public WinDef.UINT GetFeatureLevel() {
            return (WinDef.UINT) _invokeNativeObject(37,
                    new Object[]{getPointer()},
                    WinDef.UINT.class);
        }

    }

    class IDXGIOutputDuplication extends Unknown {

        // https://github.com/apitrace/dxsdk/blob/master/Include/dxgi1_2.h

        public IDXGIOutputDuplication(Pointer pvInstance) {
            super(pvInstance);
        }
    }

}

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
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * See:
 * com.sun.jna.platform.win32.COM.Wbemcli.IWbemLocator
 * https://github.com/roman380/DuplicationAndMediaFoundation/tree/master
 * https://github.com/diederickh/screen_capture/blob/master/src/test/test_win_api_directx_research.cpp (contains useful comments)
 */
public interface Dxgi extends StdCallLibrary {

    Logger logger = LoggerFactory.getLogger(Dxgi.class);

    Dxgi INSTANCE = Native.load("dxgi", Dxgi.class);

    // https://learn.microsoft.com/en-us/windows/win32/direct3ddxgi/dxgi-error
    int DXGI_ERROR_NOT_FOUND = 0x887A0002;

    WinNT.HRESULT CreateDXGIFactory(Guid.REFIID riid, PointerByReference ppFactory);

    static IDXGIFactory factory() {
        COMUtils.comIsInitialized();
        PointerByReference pFactoryRef = new PointerByReference();
        WinNT.HRESULT hr = Dxgi.INSTANCE.CreateDXGIFactory(
                new Guid.REFIID(IDXGIFactory.IID_IDXGIFactory), pFactoryRef);
        COMUtils.checkRC(hr);
        return new IDXGIFactory(pFactoryRef.getValue());
    }

    class IDXGIFactory extends Unknown {

        // https://github.com/apitrace/dxsdk/blob/master/Include/dxgi.h
        public static final Guid.IID IID_IDXGIFactory =
                new Guid.IID("7b7166ec-21c7-44ae-b21a-c9ae321ae369");

        public IDXGIFactory(Pointer pvInstance) {
            super(pvInstance);
        }

        public List<IDXGIAdapter> adapters() {
            List<IDXGIAdapter> adapters = new ArrayList<>();
            for (int adapterIndex = 0; ; adapterIndex++) {
                PointerByReference pAdapter = new PointerByReference();
                WinNT.HRESULT adapterHr = EnumAdapters(adapterIndex, pAdapter);
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
                }
            }
            return adapters;
        }

        private WinNT.HRESULT EnumAdapters(int Adapter, PointerByReference ppAdapter) {
            // 7-th method of IDXGIFactory in dxgi.h
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

    class IDXGIOutputDuplication extends Unknown {

        // https://github.com/apitrace/dxsdk/blob/master/Include/dxgi1_2.h
        public static final Guid.IID IID_IDXGIOutputDuplication =
                new Guid.IID("191cfac3-a341-470d-b26e-a864f428319c");

    }

}

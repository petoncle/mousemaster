package mousemaster;

import com.sun.jna.*;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinDef.HMODULE;
import com.sun.jna.platform.win32.WinDef.UINT;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

public interface D3d11 extends StdCallLibrary {

    D3d11 INSTANCE = Native.load("d3d11", D3d11.class);

    WinDef.UINT D3D11_SDK_VERSION = new WinDef.UINT(7);
    int D3D11_USAGE_DEFAULT = 0;
    int D3D11_BIND_VERTEX_BUFFER = 0x1;
    int D3D11_USAGE_DYNAMIC = 2;
    int D3D11_BIND_RENDER_TARGET = 0x20;

    HRESULT D3D11CreateDevice(
        Pointer pAdapter, // IDXGIAdapter* is represented as Pointer
        int DriverType, // D3D_DRIVER_TYPE is an enum, represented as int
        HMODULE Software,
        UINT Flags,
        Pointer pFeatureLevels, // const D3D_FEATURE_LEVEL* is represented as Pointer
        UINT FeatureLevels,
        UINT SDKVersion,
        PointerByReference ppDevice, // ID3D11Device** is represented as PointerByReference
        IntByReference ppFeatureLevel,
        PointerByReference ppImmediateContext // ID3D11DeviceContext** is represented as PointerByReference
    );

}

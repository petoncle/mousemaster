package mousemaster;

import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.PointerByReference;

/**
 * Inspired from com.sun.jna.platform.win32.COM.Wbemcli.IWbemLocator
 * and
 * https://github.com/roman380/DuplicationAndMediaFoundation/tree/master
 */
public class IDXGIOutputDuplication extends Unknown {

    // https://github.com/apitrace/dxsdk/blob/master/Include/dxgi1_2.h
    public static final Guid.GUID IID_IDXGIOutputDuplication =
            new Guid.GUID("191cfac3-a341-470d-b26e-a864f428319c");

}

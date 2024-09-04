package mousemaster;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;

public interface ExtendedPsapi extends Psapi {

    ExtendedPsapi INSTANCE = Native.load("psapi", ExtendedPsapi.class);

    int GetModuleBaseNameA(WinNT.HANDLE hProcess, WinDef.HMODULE hModule, byte[] lpBaseName, int nSize);

}
package mousemaster;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

public interface ExtendedAdvapi32 extends StdCallLibrary {

    ExtendedAdvapi32 INSTANCE = Native.load("Advapi32", ExtendedAdvapi32.class, W32APIOptions.DEFAULT_OPTIONS);


    // Unlike the original from Advapi32, here tokenInformation is Object.
    boolean GetTokenInformation(WinNT.HANDLE tokenHandle,
                                int tokenInformationClass, Object tokenInformation,
                                int tokenInformationLength, IntByReference returnLength);

    boolean SetTokenInformation(WinNT.HANDLE tokenHandle, int tokenInformationClass,
                                Object tokenInformation,
                                int tokenInformationLength);

    boolean PrivilegeCheck(WinNT.HANDLE clientToken,
                           WinNT.PRIVILEGE_SET requiredPrivileges,
                           IntByReference result);

}
package mousemaster;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WindowsUiAccess {

    private static final Logger logger = LoggerFactory.getLogger(WindowsUiAccess.class);

    /**
     * Tries to get UIAccess so that the mousemaster windows can be displayed
     * on top of the Windows start menu, the Windows notifications, the Win + Tab menu, etc.
     */
    public static void checkAndTryToGetUiAccess() {
        String commandLine = ExtendedKernel32.INSTANCE.GetCommandLineA();
        String respawnFlag = "--windows-ui-access-respawn=true";
        if (commandLine.contains(respawnFlag)) {
            if (!currentProcessHasUiAccess()) {
                logger.error("Current process is the respawned process but still does not have UI access");
                return;
            }
            else {
                logger.trace("Current process is the respawned process and has UI access");
                return;
            }
        }
        if (currentProcessHasUiAccess()) {
            logger.trace("Current process has UI access");
            return;
        }
        WinNT.HANDLEByReference uiAccessToken = createUiAccessToken();
        if (uiAccessToken == null) {
            return;
        }
        WinBase.STARTUPINFO startupInfo = new WinBase.STARTUPINFO();
        ExtendedKernel32.INSTANCE.GetStartupInfoW(startupInfo);
        WinBase.PROCESS_INFORMATION processInformation =
                new WinBase.PROCESS_INFORMATION();
        char[] currentDirectoryChars = new char[1024];
        int currentDirectoryLength = ExtendedKernel32.INSTANCE.GetCurrentDirectoryW(
                currentDirectoryChars.length, currentDirectoryChars);
        String currentDirectory = new String(currentDirectoryChars, 0, currentDirectoryLength);
        String newCommandLine = commandLine + " " + respawnFlag;
        if (commandLine.contains("java"))
            // We don't want to exit the process running in the IDE.
            return;
        if (Advapi32.INSTANCE.CreateProcessAsUser(uiAccessToken.getValue(), null, newCommandLine,
                null, null, false, 0, null, currentDirectory,
                startupInfo, processInformation)) {
            logger.trace("Successfully created new mousemaster process with UI access, exiting.");
            Kernel32Util.closeHandle(processInformation.hProcess);
            Kernel32Util.closeHandle(processInformation.hThread);
            System.exit(0);
        }
        else {
            logger.info("Failed to create new mousemaster process with UI access " +
                        Integer.toHexString(Native.getLastError()));
        }
    }

    private static boolean currentProcessHasUiAccess() {
        WinNT.HANDLEByReference phTokenMousemaster = new WinNT.HANDLEByReference();
        try {
            WinNT.HANDLE processHandle = Kernel32.INSTANCE.GetCurrentProcess();
            if (!Advapi32.INSTANCE.OpenProcessToken(processHandle, WinNT.TOKEN_QUERY, phTokenMousemaster)) {
                logger.error("Failed OpenProcessToken mousemaster " + Integer.toHexString(Native.getLastError()));
                return false;
            }
            IntByReference hasUiAccess = new IntByReference();
            IntByReference tokenInformationLength = new IntByReference();
            if (!ExtendedAdvapi32.INSTANCE.GetTokenInformation(phTokenMousemaster.getValue(),
                    WinNT.TOKEN_INFORMATION_CLASS.TokenUIAccess,
                    hasUiAccess, 4, tokenInformationLength)) {
                logger.error("Failed GetTokenInformation TokenUIAccess " + Integer.toHexString(Native.getLastError()));
            }
            return hasUiAccess.getValue() != 0;
        } finally {
            Kernel32Util.closeHandleRef(phTokenMousemaster);
        }
    }

    private static WinNT.HANDLEByReference createUiAccessToken() {
        WinNT.HANDLEByReference phTokenMousemaster = new WinNT.HANDLEByReference();
        WinNT.HANDLEByReference phDuplicatedWinlogonToken = null;
        try {
            WinNT.HANDLE processHandle = Kernel32.INSTANCE.GetCurrentProcess();
            if (!Advapi32.INSTANCE.OpenProcessToken(processHandle,
                    WinNT.TOKEN_QUERY | WinNT.TOKEN_DUPLICATE, phTokenMousemaster)) {
                logger.error("Failed OpenProcessToken mousemaster " +
                             Integer.toHexString(Native.getLastError()));
                return null;
            }
            IntByReference mousemasterSessionId = new IntByReference();
            IntByReference tokenInformationLength = new IntByReference();
            if (!ExtendedAdvapi32.INSTANCE.GetTokenInformation(phTokenMousemaster.getValue(),
                    WinNT.TOKEN_INFORMATION_CLASS.TokenSessionId,
                    mousemasterSessionId, 4, tokenInformationLength)) {
                logger.error("Failed GetTokenInformation TokenSessionId mousemaster " + Integer.toHexString(Native.getLastError()));
                return null;
            }
            phDuplicatedWinlogonToken = duplicateWinloginToken(mousemasterSessionId);
            if (phDuplicatedWinlogonToken == null)
                return null;
            if (!Advapi32.INSTANCE.SetThreadToken(null, phDuplicatedWinlogonToken.getValue())) {
                logger.error("Failed SetThreadToken " + Integer.toHexString(Native.getLastError()));
                return null;
            }
            WinNT.HANDLEByReference phCreatedToken = new WinNT.HANDLEByReference();
            if (!Advapi32.INSTANCE.DuplicateTokenEx(phTokenMousemaster.getValue(),
                    WinNT.TOKEN_QUERY | WinNT.TOKEN_DUPLICATE |
                    WinNT.TOKEN_ASSIGN_PRIMARY | WinNT.TOKEN_ADJUST_DEFAULT,
                    null, WinNT.SECURITY_IMPERSONATION_LEVEL.SecurityAnonymous,
                    WinNT.TOKEN_TYPE.TokenPrimary, phCreatedToken)) {
                logger.error("Failed DuplicateTokenEx " + Integer.toHexString(Native.getLastError()));
                return null;
            }
            IntByReference uiAccess = new IntByReference(1);
            if (!ExtendedAdvapi32.INSTANCE.SetTokenInformation(phCreatedToken.getValue(),
                    WinNT.TOKEN_INFORMATION_CLASS.TokenUIAccess, uiAccess, 4)) {
                logger.error("Failed SetTokenInformation " + Integer.toHexString(Native.getLastError()));
                Kernel32Util.closeHandleRef(phCreatedToken);
                return null;
            }
            return phCreatedToken;
        } finally {
            Kernel32Util.closeHandleRef(phTokenMousemaster);
            if (phDuplicatedWinlogonToken != null)
                Kernel32Util.closeHandleRef(phDuplicatedWinlogonToken);
        }
    }

    private static WinNT.HANDLEByReference duplicateWinloginToken(
            IntByReference mousemasterSessionId) {
        int privilegeCount = 1;
        WinNT.LUID[] pLuids = new WinNT.LUID[privilegeCount];
        pLuids[0] = new WinNT.LUID();
        if (!Advapi32.INSTANCE.LookupPrivilegeValue(null, WinNT.SE_TCB_NAME, pLuids[0])) {
            logger.error("Failed LookupPrivilegeValue " + Integer.toHexString(Native.getLastError()));
            return null;
        }
        WinNT.HANDLE hSnapshot =
                Kernel32.INSTANCE.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS,
                        new WinDef.DWORD(0));
        int snapshotError = Native.getLastError();
        if (hSnapshot == null || hSnapshot.equals(WinBase.INVALID_HANDLE_VALUE)) { //snapshotError == 0x7F) { // ERROR_PROC_NOT_FOUND
            logger.error("Failed CreateToolhelp32Snapshot " + Integer.toHexString(snapshotError));
            return null;
        }
        Tlhelp32.PROCESSENTRY32 processEntry = new Tlhelp32.PROCESSENTRY32();
        boolean processResult = Kernel32.INSTANCE.Process32First(hSnapshot, processEntry);
        boolean winlogonFound = false;
        do {
            if (new String(processEntry.szExeFile, 0, 12).equals("winlogon.exe")) {
                winlogonFound = true;
                break;
            }
            processResult = Kernel32.INSTANCE.Process32Next(hSnapshot, processEntry);
        } while (processResult);
        Kernel32Util.closeHandle(hSnapshot);
        if (!winlogonFound) {
            logger.error("Unable to find winlogon.exe");
            return null;
        }
        WinNT.HANDLE winlogonHandle = Kernel32.INSTANCE.OpenProcess(
                WinNT.PROCESS_QUERY_LIMITED_INFORMATION,
                false,
                processEntry.th32ProcessID.intValue());
        WinNT.HANDLEByReference phTokenWinlogon = new WinNT.HANDLEByReference();
        try {
            if (winlogonHandle == null) {
                logger.error("Failed OpenProcess winlogon " +
                             Integer.toHexString(Native.getLastError()));
                logger.info(
                        "Run mousemaster as administrator if you want the mousemaster overlay to be displayed on top of everything else.");
                return null;
            }
            if (!Advapi32.INSTANCE.OpenProcessToken(winlogonHandle,
                    WinNT.TOKEN_QUERY | WinNT.TOKEN_DUPLICATE, phTokenWinlogon)) {
                logger.error("Failed OpenProcessToken winlogon " +
                             Integer.toHexString(Native.getLastError()));
                return null;
            }
            WinNT.PRIVILEGE_SET requiredPrivileges = new WinNT.PRIVILEGE_SET(privilegeCount);
            requiredPrivileges.Privileges[0] =
                    new WinNT.LUID_AND_ATTRIBUTES(pLuids[0], new WinDef.DWORD(0));
            requiredPrivileges.Control =
                    new WinDef.DWORD(1); // PRIVILEGE_SET_ALL_NECESSARY
            IntByReference privilegeCheckResult = new IntByReference();
            if (!ExtendedAdvapi32.INSTANCE.PrivilegeCheck(phTokenWinlogon.getValue(),
                    requiredPrivileges, privilegeCheckResult)) {
                logger.error("Failed PrivilegeCheck " +
                             Integer.toHexString(Native.getLastError()));
                return null;
            }
            if (privilegeCheckResult.getValue() != 1) {
                logger.error(
                        "PrivilegeCheck returned " + privilegeCheckResult.getValue());
                return null;
            }
            IntByReference winlogonSessionId = new IntByReference();
            IntByReference tokenInformationLength = new IntByReference();
            if (!ExtendedAdvapi32.INSTANCE.GetTokenInformation(phTokenWinlogon.getValue(),
                    WinNT.TOKEN_INFORMATION_CLASS.TokenSessionId,
                    winlogonSessionId, 4, tokenInformationLength)) {
                logger.error("Failed GetTokenInformation TokenSessionId winlogon " + Integer.toHexString(Native.getLastError()));
                return null;
            }
            if (mousemasterSessionId.getValue() != winlogonSessionId.getValue()) {
                logger.error("mousemaster and winlogon session ids are different: " +
                             mousemasterSessionId.getValue() + " " +
                             winlogonSessionId.getValue());
                return null;
            }
            WinNT.HANDLEByReference phDuplicatedWinlogonToken = new WinNT.HANDLEByReference();
            if (!Advapi32.INSTANCE.DuplicateTokenEx(phTokenWinlogon.getValue(),
                    WinNT.TOKEN_IMPERSONATE,
                    null,
                    WinNT.SECURITY_IMPERSONATION_LEVEL.SecurityImpersonation,
                    WinNT.TOKEN_TYPE.TokenImpersonation, phDuplicatedWinlogonToken)) {
                logger.error("Failed DuplicateTokenImpersonation " + Integer.toHexString(Native.getLastError()));
            }
            return phDuplicatedWinlogonToken;
        } finally {
            if (winlogonHandle != null)
                Kernel32Util.closeHandle(winlogonHandle);
            Kernel32Util.closeHandleRef(phTokenWinlogon);
        }
    }

}

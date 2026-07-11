package mousemaster.platform.windows;

import mousemaster.*;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Windows-specific keyboard layout and event translation.
 * <p>
 * Shared code may carry Windows key identifiers as data, but all Win32 calls and
 * low-level event interpretation stay here so the rest of the application can
 * remain platform-neutral.
 */
public class WindowsKeys {

    private static final Logger logger = LoggerFactory.getLogger(WindowsKeys.class);

    private static KeyboardLayout lastPolledActiveKeyboardLayout;
    private static int keyboardLayoutSeenCount;
    private static boolean lastActiveKeyboardLayoutFailed;

    /**
     * When changing the layout with win + space:
     * when opening the Win start menu, the layout of that hwnd would be the old layout for
     * a few milliseconds. The workaround here waits for the layout to show up twice before
     * confirming it has changed.
     */
    public static KeyboardLayout activeKeyboardLayout() {
        KeyboardLayout foregroundWindowKeyboardLayout = foregroundWindowKeyboardLayout();
        if (foregroundWindowKeyboardLayout != null) {
            if (!foregroundWindowKeyboardLayout.equals(lastPolledActiveKeyboardLayout)) {
                if (lastPolledActiveKeyboardLayout != null && keyboardLayoutSeenCount++ < 2)
                    return lastPolledActiveKeyboardLayout;
                // New layout confirmed.
                keyboardLayoutSeenCount = 0;
                WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
                String hwndString = hwnd == null ? null :
                        String.format("0x%X", Pointer.nativeValue(hwnd.getPointer()));
                logger.trace("Found foreground window's keyboard layout for hwnd " + hwndString + ": " +
                            foregroundWindowKeyboardLayout);
            }
            lastPolledActiveKeyboardLayout = foregroundWindowKeyboardLayout;
            lastActiveKeyboardLayoutFailed = false;
            return foregroundWindowKeyboardLayout;
        }
        // When changing the active window, the foreground window may be null for a short period of time (?).
        if (lastPolledActiveKeyboardLayout != null) {
            if (!lastActiveKeyboardLayoutFailed)
                logger.trace(
                        "Unable to find the foreground window's keyboard layout, using last known keyboard layout " +
                        lastPolledActiveKeyboardLayout);
            lastActiveKeyboardLayoutFailed = true;
            return lastPolledActiveKeyboardLayout;
        }
        KeyboardLayout startupKeyboardLayout = startupKeyboardLayout();
        if (!lastActiveKeyboardLayoutFailed)
            logger.trace(
                    "Unable to find the foreground window's keyboard layout, using start up keyboard layout " +
                    startupKeyboardLayout);
        lastPolledActiveKeyboardLayout = startupKeyboardLayout;
        lastActiveKeyboardLayoutFailed = true;
        return startupKeyboardLayout;
    }

    private static int lastFailedGetKeyboardLayoutThreadId = -1;

    private static WinDef.HKL foregroundWindowHkl() {
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null) {
            // GetForegroundWindow does not set the last error value.
            logger.trace("GetForegroundWindow failed");
        }
        else {
            int thread = User32.INSTANCE.GetWindowThreadProcessId(hwnd, null);
            if (thread == 0) {
                logger.error("GetWindowThreadProcessId failed: " + Integer.toHexString(
                        Native.getLastError()));
            }
            else {
                WinDef.HKL hkl = User32.INSTANCE.GetKeyboardLayout(thread);
                if (hkl == null) {
                    if (lastFailedGetKeyboardLayoutThreadId != thread) {
                        // GetKeyboardLayout does not set the last error value.
                        logger.error("GetKeyboardLayout failed");
                        // Avoid flooding the logs with the same error.
                        lastFailedGetKeyboardLayoutThreadId = thread;
                    }
                }
                else {
                    lastFailedGetKeyboardLayoutThreadId = -1;
                }
                return hkl;
            }
        }
        return null;
    }

    private static KeyboardLayout foregroundWindowKeyboardLayout() {
        WinDef.HKL hkl = foregroundWindowHkl();
        if (hkl != null) {
            // The mousemaster.exe command line window does not handle the WM_INPUTLANGCHANGE message.
            // Therefore, when the user changes the layout, the command line window keeps the old layout.
            // We call ActivateKeyboardLayout to change the layout of the command line window.
            ExtendedUser32.INSTANCE.ActivateKeyboardLayout(hkl, 0);
            int languageIdentifier = hkl.getLanguageIdentifier();
            KeyboardLayout keyboardLayout = KeyboardLayout.keyboardLayoutByIdentifier.get(
                    String.format("%08X", languageIdentifier));
//            logger.debug("Found active window keyboard layout: " + keyboardLayout);
            return keyboardLayout;
        }
        return null;
    }

    private static KeyboardLayout startupKeyboardLayout() {
        // GetKeyboardLayoutName returns the layout at the time of when the app was started.
        // If the system layout is changed after the app is started, GetKeyboardLayoutName
        // still returns the old layout.
        char[] nameBuffer = new char[User32.KL_NAMELENGTH];
        User32.INSTANCE.GetKeyboardLayoutName(nameBuffer);
        int nameLength = nameBuffer.length;
        for (int i = 0; i < nameBuffer.length; i++) {
            if (nameBuffer[i] == 0) {
                nameLength = i;
                break;
            }
        }
        return KeyboardLayout.keyboardLayoutByIdentifier.get(
                new String(nameBuffer, 0, nameLength));
    }

    public static Key keyFromWindowsEvent(WindowsVirtualKey windowsVirtualKey, int scanCode,
                                          int flags, KeyboardLayout activeKeyboardLayout) {
        if (scanCode == 0) {
            // Injected key event have scanCode 0.
            return activeKeyboardLayout().keyFromVirtualKey(windowsVirtualKey);
        }
        // When pressing rightctrl the scanCode should be E01D but is 1D (which is leftctrl's scanCode).
        // rightctrl:
        // Received key event: vkCode = 0xa3 (VK_RCONTROL), scanCode = 0x1d, flags = 0x1, wParam = WM_KEYDOWN
        // leftctrl:
        // Received key event: vkCode = 0xa2 (VK_LCONTROL), scanCode = 0x1d, flags = 0x0, wParam = WM_KEYDOWN
        // For rightshift, flag is 1 but it is not an extended key (scanCode is not E036 and really is 36):
        // Received key event: vkCode = 0xa1 (VK_RSHIFT), scanCode = 0x36, flags = 0x1, wParam = WM_KEYDOWN
        boolean isExtended = (flags & 0x1) != 0;
        if (isExtended) {
            int extendedKeyScanCode = 0xE000 | scanCode;
            Key extendedKey = activeKeyboardLayout.keyFromScanCode(extendedKeyScanCode);
            if (extendedKey != null)
                return extendedKey;
        }
        return activeKeyboardLayout.keyFromScanCode(scanCode);
    }

    public static WindowsVirtualKey windowsVirtualKeyFromKey(Key key,
                                                             KeyboardLayout keyboardLayout) {
        WindowsVirtualKey virtualKey = keyboardLayout.virtualKey(key);
        if (virtualKey == null) {
            logger.debug("Unable to map key " + key + " to a Windows virtual key using " +
                         keyboardLayout);
        }
        return virtualKey;
    }

}

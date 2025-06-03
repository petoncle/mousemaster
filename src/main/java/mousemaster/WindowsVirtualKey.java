package mousemaster;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum WindowsVirtualKey {

    VK_LBUTTON(0x01),
    VK_MOUSE1(0x01),
    VK_MB1(0x01),
    VK_RBUTTON(0x02),
    VK_MOUSE2(0x02),
    VK_MB2(0x02),
    VK_CANCEL(0x03),
    VK_MBUTTON(0x04),
    VK_MOUSE3(0x04),
    VK_MB3(0x04),
    VK_XBUTTON1(0x05),
    VK_MOUSE4(0x05),
    VK_MB4(0x05),
    VK_XBUTTON2(0x06),
    VK_MOUSE5(0x06),
    VK_MB5(0x06),
    VK_RESERVED_07(0x07),
    VK_BACK(0x08),
    VK_BACKSPACE(0x08),
    VK_TAB(0x09),
    VK_RESERVED_0A(0x0A),
    VK_RESERVED_0B(0x0B),
    VK_CLEAR(0x0C),
    VK_RETURN(0x0D),
    VK_ENTER(0x0D),
    VK_RESERVED_0E(0x0E),
    VK_RESERVED_0F(0x0F),
    VK_SHIFT(0x10),
    VK_CONTROL(0x11),
    VK_CTRL(0x11),
    VK_MENU(0x12),
    VK_ALT(0x12),
    VK_PAUSE(0x13),
    VK_CAPITAL(0x14),
    VK_CAPSLK(0x14),
    VK_CAPSLOCK(0x14),
    VK_CAPS(0x14),
    VK_HANGEUL(0x15),
    VK_HANGUL(0x15),
    VK_KANA(0x15),
    VK_IME_ON(0x16),
    VK_JUNJA(0x17),
    VK_FINAL(0x18),
    VK_HANJA(0x19),
    VK_KANJI(0x19),
    VK_IME_OFF(0x1A),
    VK_ESCAPE(0x1B),
    VK_ESC(0x1B),
    VK_CONVERT(0x1C),
    VK_NONCONVERT(0x1D),
    VK_ACCEPT(0x1E),
    VK_MODECHANGE(0x1F),
    VK_SPACE(0x20),
    VK_PRIOR(0x21),
    VK_PGUP(0x21),
    VK_PAGEUP(0x21),
    VK_NEXT(0x22),
    VK_PGDN(0x22),
    VK_PAGEDOWN(0x22),
    VK_END(0x23),
    VK_HOME(0x24),
    VK_LEFT(0x25),
    VK_UP(0x26),
    VK_RIGHT(0x27),
    VK_DOWN(0x28),
    VK_SELECT(0x29),
    VK_PRINT(0x2A),
    VK_EXECUTE(0x2B),
    VK_SNAPSHOT(0x2C),
    VK_PRTSCN(0x2C),
    VK_PRINTSCREEN(0x2C),
    VK_INSERT(0x2D),
    VK_INS(0x2D),
    VK_DELETE(0x2E),
    VK_DEL(0x2E),
    VK_HELP(0x2F),
    VK_0(0x30),
    VK_1(0x31),
    VK_2(0x32),
    VK_3(0x33),
    VK_4(0x34),
    VK_5(0x35),
    VK_6(0x36),
    VK_7(0x37),
    VK_8(0x38),
    VK_9(0x39),
    VK_RESERVED_3A(0x3A),
    VK_RESERVED_3B(0x3B),
    VK_RESERVED_3C(0x3C),
    VK_RESERVED_3D(0x3D),
    VK_RESERVED_3E(0x3E),
    VK_RESERVED_3F(0x3F),
    VK_RESERVED_40(0x40),
    VK_A(0x41),
    VK_B(0x42),
    VK_C(0x43),
    VK_D(0x44),
    VK_E(0x45),
    VK_F(0x46),
    VK_G(0x47),
    VK_H(0x48),
    VK_I(0x49),
    VK_J(0x4A),
    VK_K(0x4B),
    VK_L(0x4C),
    VK_M(0x4D),
    VK_N(0x4E),
    VK_O(0x4F),
    VK_P(0x50),
    VK_Q(0x51),
    VK_R(0x52),
    VK_S(0x53),
    VK_T(0x54),
    VK_U(0x55),
    VK_V(0x56),
    VK_W(0x57),
    VK_X(0x58),
    VK_Y(0x59),
    VK_Z(0x5A),
    VK_LWIN(0x5B),
    VK_RWIN(0x5C),
    VK_APPS(0x5D),
    VK_CONTEXT(0x5D),
    VK_CONTEXTMENU(0x5D),
    VK_RESERVED_5E(0x5E),
    VK_SLEEP(0x5F),
    VK_NUMPAD0(0x60),
    VK_NUM0(0x60),
    VK_NUMPAD1(0x61),
    VK_NUM1(0x61),
    VK_NUMPAD2(0x62),
    VK_NUM2(0x62),
    VK_NUMPAD3(0x63),
    VK_NUM3(0x63),
    VK_NUMPAD4(0x64),
    VK_NUM4(0x64),
    VK_NUMPAD5(0x65),
    VK_NUM5(0x65),
    VK_NUMPAD6(0x66),
    VK_NUM6(0x66),
    VK_NUMPAD7(0x67),
    VK_NUM7(0x67),
    VK_NUMPAD8(0x68),
    VK_NUM8(0x68),
    VK_NUMPAD9(0x69),
    VK_NUM9(0x69),
    VK_MULTIPLY(0x6A),
    VK_ADD(0x6B),
    VK_SEPARATOR(0x6C),
    VK_SUBTRACT(0x6D),
    VK_DECIMAL(0x6E),
    VK_DIVIDE(0x6F),
    VK_F1(0x70),
    VK_F2(0x71),
    VK_F3(0x72),
    VK_F4(0x73),
    VK_F5(0x74),
    VK_F6(0x75),
    VK_F7(0x76),
    VK_F8(0x77),
    VK_F9(0x78),
    VK_F10(0x79),
    VK_F11(0x7A),
    VK_F12(0x7B),
    VK_F13(0x7C),
    VK_F14(0x7D),
    VK_F15(0x7E),
    VK_F16(0x7F),
    VK_F17(0x80),
    VK_F18(0x81),
    VK_F19(0x82),
    VK_F20(0x83),
    VK_F21(0x84),
    VK_F22(0x85),
    VK_F23(0x86),
    VK_F24(0x87),
    VK_NAVIGATION_VIEW(136),
    VK_NAVIGATION_MENU(137),
    VK_NAVIGATION_UP(138),
    VK_NAVIGATION_DOWN(139),
    VK_NAVIGATION_LEFT(140),
    VK_NAVIGATION_RIGHT(141),
    VK_NAVIGATION_ACCEPT(142),
    VK_NAVIGATION_CANCEL(143),
    VK_NUMLOCK(0x90),
    VK_NUMLK(0x90),
    VK_SCROLL(0x91),
    VK_SCRLK(0x91),
    VK_OEM_FJ_JISHO(146),
    VK_OEM_NEC_EQUAL(146),
    VK_OEM_FJ_MASSHOU(147),
    VK_OEM_FJ_TOUROKU(148),
    VK_OEM_FJ_LOYA(149),
    VK_OEM_FJ_ROYA(150),
    VK_RESERVED_97(0x97),
    VK_RESERVED_98(0x98),
    VK_RESERVED_99(0x99),
    VK_RESERVED_9A(0x9A),
    VK_RESERVED_9B(0x9B),
    VK_RESERVED_9C(0x9C),
    VK_RESERVED_9D(0x9D),
    VK_RESERVED_9E(0x9E),
    VK_RESERVED_9F(0x9F),
    VK_LSHIFT(0xA0),
    VK_RSHIFT(0xA1),
    VK_LCONTROL(0xA2),
    VK_LCTRL(0xA2),
    VK_RCONTROL(0xA3),
    VK_RCTRL(0xA3),
    VK_LMENU(0xA4),
    VK_LALT(0xA4),
    VK_RMENU(0xA5),
    VK_RALT(0xA5),
    VK_BROWSER_BACK(0xA6),
    VK_BROWSER_FORWARD(0xA7),
    VK_BROWSER_REFRESH(0xA8),
    VK_BROWSER_STOP(0xA9),
    VK_BROWSER_SEARCH(0xAA),
    VK_BROWSER_FAVORITES(0xAB),
    VK_BROWSER_HOME(0xAC),
    VK_VOLUME_MUTE(0xAD),
    VK_VOLUME_DOWN(0xAE),
    VK_VOLUME_UP(0xAF),
    VK_MEDIA_NEXT_TRACK(0xB0),
    VK_MEDIA_PREV_TRACK(0xB1),
    VK_MEDIA_STOP(0xB2),
    VK_MEDIA_PLAY_PAUSE(0xB3),
    VK_LAUNCH_MAIL(0xB4),
    VK_LAUNCH_MEDIA_SELECT(0xB5),
    VK_LAUNCH_APP1(0xB6),
    VK_LAUNCH_APP2(0xB7),
    VK_RESERVED_B8(0xB8),
    VK_RESERVED_B9(0xB9),
    VK_OEM_1(0xBA),
    VK_OEM_PLUS(0xBB),
    VK_OEM_COMMA(0xBC),
    VK_OEM_MINUS(0xBD),
    VK_OEM_PERIOD(0xBE),
    VK_OEM_2(0xBF),
    VK_OEM_3(0xC0),
    VK_RESERVED_C1(0xC1),
    VK_ABNT_C1(0xC1),
    VK_RESERVED_C2(0xC2),
    VK_ABNT_C2(0xC2),
    VK_GAMEPAD_A(0xC3),
    VK_GAMEPAD_B(0xC4),
    VK_GAMEPAD_X(0xC5),
    VK_GAMEPAD_Y(0xC6),
    VK_GAMEPAD_RIGHT_SHOULDER(0xC7),
    VK_GAMEPAD_LEFT_SHOULDER(0xC8),
    VK_GAMEPAD_LEFT_TRIGGER(0xC9),
    VK_GAMEPAD_RIGHT_TRIGGER(0xCA),
    VK_GAMEPAD_DPAD_UP(0xCB),
    VK_GAMEPAD_DPAD_DOWN(0xCC),
    VK_GAMEPAD_DPAD_LEFT(0xCD),
    VK_GAMEPAD_DPAD_RIGHT(0xCE),
    VK_GAMEPAD_MENU(0xCF),
    VK_GAMEPAD_VIEW(0xD0),
    VK_GAMEPAD_LEFT_THUMBSTICK_BUTTON(0xD1),
    VK_GAMEPAD_RIGHT_THUMBSTICK_BUTTON(0xD2),
    VK_GAMEPAD_LEFT_THUMBSTICK_UP(0xD3),
    VK_GAMEPAD_LEFT_THUMBSTICK_DOWN(0xD4),
    VK_GAMEPAD_LEFT_THUMBSTICK_RIGHT(0xD5),
    VK_GAMEPAD_LEFT_THUMBSTICK_LEFT(0xD6),
    VK_GAMEPAD_RIGHT_THUMBSTICK_UP(0xD7),
    VK_GAMEPAD_RIGHT_THUMBSTICK_DOWN(0xD8),
    VK_GAMEPAD_RIGHT_THUMBSTICK_RIGHT(0xD9),
    VK_GAMEPAD_RIGHT_THUMBSTICK_LEFT(0xDA),
    VK_OEM_4(0xDB),
    VK_OEM_5(0xDC),
    VK_OEM_6(0xDD),
    VK_OEM_7(0xDE),
    VK_OEM_8(0xDF),
    VK_RESERVED_E0(0xE0),
    VK_OEM_AX(0xE1),
    VK_OEM_102(0xE2),
    VK_ICO_HELP(0xE3),
    VK_ICO_00(0xE4),
    VK_PROCESSKEY(0xE5),
    VK_ICO_CLEAR(0xE6),
    VK_PACKET(0xE7),
    VK_RESERVED_E8(0xE8),
    VK_OEM_RESET(0xE9),
    VK_OEM_JUMP(0xEA),
    VK_OEM_PA1(0xEB),
    VK_OEM_PA2(0xEC),
    VK_OEM_PA3(0xED),
    VK_OEM_WSCTRL(0xEE),
    VK_OEM_CUSEL(0xEF),
    VK_OEM_ATTN(0xF0),
    VK_DBE_ALPHANUMERIC(0xF0),
    VK_OEM_FINISH(0xF1),
    VK_DBE_KATAKANA(0xF1),
    VK_OEM_COPY(0xF2),
    VK_DBE_HIRAGANA(0xF2),
    VK_OEM_AUTO(0xF3),
    VK_DBE_SBCSCHAR(0xF3),
    VK_OEM_ENLW(0xF4),
    VK_DBE_DBCSCHAR(0xF4),
    VK_OEM_BACKTAB(0xF5),
    VK_DBE_ROMAN(0xF5),
    VK_ATTN(0xF6),
    VK_DBE_NOROMAN(0xF6),
    VK_CRSEL(0xF7),
    VK_DBE_ENTERWORDREGISTERMODE(0xF7),
    VK_EXSEL(0xF8),
    VK_DBE_ENTERCONFIGMODE(0xF8),
    VK_EREOF(0xF9),
    VK_DBE_FLUSHSTRING(0xF9),
    VK_PLAY(0xFA),
    VK_DBE_CODEINPUT(0xFA),
    VK_ZOOM(0xFB),
    VK_DBE_NOCODEINPUT(0xFB),
    VK_NONAME(0xFC),
    VK_DBE_DETERINESTRING(0xFC),
    VK_PA1(0xFD),
    VK_DBE_ENTERDLGCONVERSIONMODE(0xFD),
    VK_OEM_CLEAR(0xFE),
    VK_NONE(0xFF),
    ;

    public final int virtualKeyCode;

    WindowsVirtualKey(int virtualKeyCode) {
        this.virtualKeyCode = virtualKeyCode;
    }

    public static final List<WindowsVirtualKey> values;

    private static final Logger logger = LoggerFactory.getLogger(WindowsVirtualKey.class);

    static {
        WindowsVirtualKey[] valueArrayWithDuplicateCodes = values();
        Map<Integer, WindowsVirtualKey> valueMap = new HashMap<>();
        for (WindowsVirtualKey windowsVirtualKey : valueArrayWithDuplicateCodes)
            valueMap.putIfAbsent(windowsVirtualKey.virtualKeyCode, windowsVirtualKey);
        WindowsVirtualKey[] valueArrayWithoutDuplicateCodes = new WindowsVirtualKey[256];
        for (Map.Entry<Integer, WindowsVirtualKey> entry : valueMap.entrySet())
            valueArrayWithoutDuplicateCodes[entry.getKey()] = entry.getValue();
        values = Arrays.stream(valueArrayWithoutDuplicateCodes).toList();
    }

    private static KeyboardLayout lastPolledActiveKeyboardLayout;
    private static int keyboardLayoutSeenCount;

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
                String hwnd = String.format("0x%X", Pointer.nativeValue(
                        User32.INSTANCE.GetForegroundWindow().getPointer()));
                logger.trace("Found foreground window's keyboard layout for hwnd " + hwnd + ": " +
                            foregroundWindowKeyboardLayout);
            }
            lastPolledActiveKeyboardLayout = foregroundWindowKeyboardLayout;
            return foregroundWindowKeyboardLayout;
        }
        // When changing the active window, the foreground window may be null for a short period of time (?).
        if (lastPolledActiveKeyboardLayout != null) {
            logger.trace(
                    "Unable to find the foreground window's keyboard layout, using last known keyboard layout " +
                    lastPolledActiveKeyboardLayout);
            return lastPolledActiveKeyboardLayout;
        }
        KeyboardLayout startupKeyboardLayout = startupKeyboardLayout();
        logger.trace(
                "Unable to find the foreground window's keyboard layout, using start up keyboard layout " +
                startupKeyboardLayout);
        lastPolledActiveKeyboardLayout = startupKeyboardLayout;
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

    public static Key keyFromWindowsEvent(WindowsVirtualKey windowsVirtualKey, int scanCode, int flags) {
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
            Key extendedKey = WindowsKeyboard.activeKeyboardLayout.keyFromScanCode(extendedKeyScanCode);
            if (extendedKey != null)
                return extendedKey;
        }
        return WindowsKeyboard.activeKeyboardLayout.keyFromScanCode(scanCode);
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

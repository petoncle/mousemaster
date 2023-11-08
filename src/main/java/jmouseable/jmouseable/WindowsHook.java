package jmouseable.jmouseable;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WindowsHook {

    private static final Logger logger = LoggerFactory.getLogger(WindowsHook.class);

    private final MouseMover mouseMover;
    private WinUser.HHOOK keyboardHook;
    private WinUser.HHOOK mouseHook;

    public WindowsHook(MouseMover mouseMover) {
        this.mouseMover = mouseMover;
    }

    public void installHooks() {
        WinDef.HMODULE hMod = Kernel32.INSTANCE.GetModuleHandle(null);
        keyboardHook = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL,
                (WinUser.LowLevelKeyboardProc) this::keyboardHookCallback, hMod,
                0);
        mouseHook = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_MOUSE_LL,
                (WinUser.LowLevelMouseProc) this::mouseHookCallback, hMod, 0);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            User32.INSTANCE.UnhookWindowsHookEx(keyboardHook);
            User32.INSTANCE.UnhookWindowsHookEx(mouseHook);
            logger.info("Keyboard and mouse hooks uninstalled");
        }));
        logger.info("Keyboard and mouse hooks installed");
        WindowsIndicator.showIndicatorWindow();
        WinUser.MSG msg = new WinUser.MSG();
        while (User32.INSTANCE.GetMessage(msg, null, 0, 0) != 0) {
            User32.INSTANCE.TranslateMessage(msg);
            User32.INSTANCE.DispatchMessage(msg);
        }
    }

    private WinDef.LRESULT keyboardHookCallback(int nCode, WinDef.WPARAM wParam,
                                                       WinUser.KBDLLHOOKSTRUCT info) {
        if (nCode >= 0) {
            switch (wParam.intValue()) {
                case WinUser.WM_KEYUP:
                case WinUser.WM_KEYDOWN:
                case WinUser.WM_SYSKEYUP:
                case WinUser.WM_SYSKEYDOWN:
                    logger.info("Key action: " + wParam + ", " + info.vkCode);
                    break;
            }
        }
        return User32.INSTANCE.CallNextHookEx(keyboardHook, nCode, wParam,
                new WinDef.LPARAM(Pointer.nativeValue(info.getPointer())));
    }

    private WinDef.LRESULT mouseHookCallback(int nCode, WinDef.WPARAM wParam,
                                                    WinUser.MSLLHOOKSTRUCT info) {
        if (nCode >= 0) {
            WinDef.POINT mousePosition = info.pt;
            WindowsIndicator.mouseMoved(mousePosition);
            mouseMover.mouseMoved(mousePosition.x, mousePosition.y);
        }
        return User32.INSTANCE.CallNextHookEx(mouseHook, nCode, wParam,
                new WinDef.LPARAM(Pointer.nativeValue(info.getPointer())));
    }


}

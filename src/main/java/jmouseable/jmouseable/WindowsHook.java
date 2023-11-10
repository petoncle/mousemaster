package jmouseable.jmouseable;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class WindowsHook {

    private static final Logger logger = LoggerFactory.getLogger(WindowsHook.class);
    private static final Instant systemStartTime;

    static {
        long uptimeMillis = ExtendedKernel32.INSTANCE.GetTickCount64();
        Instant now = Instant.now();
        systemStartTime = now.minusMillis(uptimeMillis);
    }

    private final MouseMover mouseMover;
    private final ComboWatcher comboWatcher;
    private WinUser.HHOOK keyboardHook;
    private WinUser.HHOOK mouseHook;

    public WindowsHook(MouseMover mouseMover, ComboWatcher comboWatcher) {
        this.mouseMover = mouseMover;
        this.comboWatcher = comboWatcher;
    }

    public void installHooks() throws InterruptedException {
        WinDef.HMODULE hMod = Kernel32.INSTANCE.GetModuleHandle(null);
        keyboardHook = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL,
                (WinUser.LowLevelKeyboardProc) this::keyboardHookCallback, hMod, 0);
        mouseHook = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_MOUSE_LL,
                (WinUser.LowLevelMouseProc) this::mouseHookCallback, hMod, 0);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            boolean keyboardHookUnhooked =
                    User32.INSTANCE.UnhookWindowsHookEx(keyboardHook);
            boolean mouseHookUnhooked = User32.INSTANCE.UnhookWindowsHookEx(mouseHook);
            logger.info(
                    "Keyboard and mouse hooks uninstalled " + keyboardHookUnhooked + " " +
                    mouseHookUnhooked);
        }));
        logger.info("Keyboard and mouse hooks installed");
        WindowsIndicator.showIndicatorWindow();
        WinUser.MSG msg = new WinUser.MSG();
        long previousNanoTime = System.nanoTime();
        while (true) {
            while (User32.INSTANCE.PeekMessage(msg, null, 0, 0, 1)) {
                User32.INSTANCE.TranslateMessage(msg);
                User32.INSTANCE.DispatchMessage(msg);
            }
            long currentNanoTime = System.nanoTime();
            long deltaNanos = currentNanoTime - previousNanoTime;
            previousNanoTime = currentNanoTime;
            double delta = deltaNanos / 1e9d;
            update(delta);
            Thread.sleep(10L);
        }
    }


    private void update(double delta) {
        mouseMover.update(delta);
    }

    private WinDef.LRESULT keyboardHookCallback(int nCode, WinDef.WPARAM wParam,
                                                WinUser.KBDLLHOOKSTRUCT info) {
        if (nCode >= 0) {
            switch (wParam.intValue()) {
                case WinUser.WM_KEYUP:
                case WinUser.WM_KEYDOWN:
                case WinUser.WM_SYSKEYUP:
                case WinUser.WM_SYSKEYDOWN:
                    String wParamString = switch (wParam.intValue()) {
                        case WinUser.WM_KEYUP -> "WM_KEYUP";
                        case WinUser.WM_KEYDOWN -> "WM_KEYDOWN";
                        case WinUser.WM_SYSKEYUP -> "WM_SYSKEYUP";
                        case WinUser.WM_SYSKEYDOWN -> "WM_SYSKEYDOWN";
                        default -> throw new IllegalStateException();
                    };
                    if (info.vkCode == WindowsVirtualKey.VK_LMENU.virtualKeyCode &&
                        (info.flags & 0b10000) == 0b10000) {
                        // 0b10000 means alt is pressed. This avoids getting two consecutive duplicate alt press,release events.
                    }
                    else {
                        KeyState state = wParam.intValue() == WinUser.WM_KEYUP ||
                                         wParam.intValue() == WinUser.WM_SYSKEYUP ?
                                KeyState.RELEASED : KeyState.PRESSED;
                        KeyAction action = new KeyAction(
                                WindowsVirtualKey.keyFromWindowsEvent(
                                        WindowsVirtualKey.values.get(info.vkCode),
                                        info.scanCode, info.flags), state);
                        KeyEvent keyEvent =
                                new KeyEvent(systemStartTime.plusMillis(info.time),
                                        action);
                        logger.debug("Received key event: " + keyEvent + ", vk = " +
                                     WindowsVirtualKey.values.get(info.vkCode) +
                                     ", scanCode = " + info.scanCode + ", flags = 0x" +
                                     Integer.toHexString(info.flags) + ", wParam = " +
                                     wParamString);
                        if (comboWatcher.keyEvent(keyEvent))
                            return new WinDef.LRESULT(1);
                    }
                    break;
                default:
                    logger.debug("Received unexpected key event wParam: " + wParam.intValue());
            }
        }
        return ExtendedUser32.INSTANCE.CallNextHookEx(keyboardHook, nCode, wParam, info);
    }

    private WinDef.LRESULT mouseHookCallback(int nCode, WinDef.WPARAM wParam,
                                             WinUser.MSLLHOOKSTRUCT info) {
        if (nCode >= 0) {
            WinDef.POINT mousePosition = info.pt;
            WindowsIndicator.mouseMoved(mousePosition);
            mouseMover.mouseMoved(mousePosition.x, mousePosition.y);
        }
        return ExtendedUser32.INSTANCE.CallNextHookEx(mouseHook, nCode, wParam, info);
    }


}

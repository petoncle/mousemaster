package jmouseable.jmouseable;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
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

    public void installHooks() {
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
        int timerId = 1;
        // Timer every ~10ms (it is inaccurate and is usually called every 15-20ms).
        ExtendedUser32.INSTANCE.SetTimer(null, timerId, 10, this::update);
        WinUser.MSG msg = new WinUser.MSG();
        while (User32.INSTANCE.GetMessage(msg, null, 0, 0) != 0) {
            User32.INSTANCE.TranslateMessage(msg);
            User32.INSTANCE.DispatchMessage(msg);
        }
        ExtendedUser32.INSTANCE.KillTimer(null, timerId);
    }

    private long previousNanoTime = System.nanoTime();

    private void update(Pointer hWnd, int uMsg, Pointer nIDEvent, int dwTime) {
        long currentNanoTime = System.nanoTime();
        long deltaNanos = currentNanoTime - previousNanoTime;
        previousNanoTime = currentNanoTime;
        double delta = deltaNanos / 1e9d;
        mouseMover.update(delta);
    }

    private WinDef.LRESULT keyboardHookCallback(int nCode, WinDef.WPARAM wParam,
                                                WinUser.KBDLLHOOKSTRUCT info) {
        if (nCode >= 0) {
            switch (wParam.intValue()) {
                case WinUser.WM_KEYUP:
                case WinUser.WM_KEYDOWN:
                    KeyState state = wParam.intValue() == WinUser.WM_KEYUP ||
                                     wParam.intValue() == WinUser.WM_SYSKEYUP ?
                            KeyState.RELEASED : KeyState.PRESSED;
                    logger.debug("Received key event: vk = " +
                                WindowsVirtualKey.values.get(info.vkCode) + ", scanCode = " +
                                info.scanCode + ", flags = 0x" + Integer.toHexString(info.flags));
                    KeyAction action = new KeyAction(
                            WindowsVirtualKey.keyFromWindowsEvent(
                                    WindowsVirtualKey.values.get(info.vkCode),
                                    info.scanCode, info.flags), state);
                    if (comboWatcher.keyEvent(
                            new KeyEvent(systemStartTime.plusMillis(info.time),
                                    action)))
                        return new WinDef.LRESULT(1);
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

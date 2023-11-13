package jmouseable.jmouseable;

import com.sun.jna.platform.win32.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

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
    private final Ticker ticker;
    private WinUser.HHOOK keyboardHook;
    private WinUser.HHOOK mouseHook;
    /**
     * Keep a reference of the Windows callback.
     * Without these references, they seem to be garbage collected and are not called
     * anymore after about 30 minutes.
     */
    private WinUser.LowLevelMouseProc mouseHookCallback;
    private WinUser.LowLevelKeyboardProc keyboardHookCallback;
    private WinNT.HANDLE singleInstanceMutex;

    public WindowsHook(MouseMover mouseMover, ComboWatcher comboWatcher, Ticker ticker) {
        this.mouseMover = mouseMover;
        this.comboWatcher = comboWatcher;
        this.ticker = ticker;
    }

    public void installHooks() throws InterruptedException {
        if (!acquireSingleInstanceMutex())
            throw new IllegalStateException("Another instance is already running");
        WinDef.HMODULE hMod = Kernel32.INSTANCE.GetModuleHandle(null);
        keyboardHookCallback = WindowsHook.this::keyboardHookCallback;
        keyboardHook = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL,
                keyboardHookCallback, hMod, 0);
        mouseHookCallback = WindowsHook.this::mouseHookCallback;
        mouseHook = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_MOUSE_LL,
                mouseHookCallback, hMod, 0);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            boolean keyboardHookUnhooked =
                    User32.INSTANCE.UnhookWindowsHookEx(keyboardHook);
            boolean mouseHookUnhooked = User32.INSTANCE.UnhookWindowsHookEx(mouseHook);
            logger.info(
                    "Keyboard and mouse hooks uninstalled " + keyboardHookUnhooked + " " +
                    mouseHookUnhooked);
            releaseSingleInstanceMutex();
            logger.info("Single instance mutex released");
        }));
        logger.info("Keyboard and mouse hooks installed");
        WindowsIndicator.createIndicatorWindow();
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
            ticker.update(delta);
            Thread.sleep(10L);
        }
    }

    private boolean acquireSingleInstanceMutex() {
        singleInstanceMutex = Kernel32.INSTANCE.CreateMutex(null, true,
                "e133df8f8434f57e65f4276f6fc761ab356687b3");
        int lastError = Kernel32.INSTANCE.GetLastError();
        return lastError != 183; // ERROR_ALREADY_EXISTS
    }

    private void releaseSingleInstanceMutex() {
        Kernel32.INSTANCE.ReleaseMutex(singleInstanceMutex);
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
                        boolean eventMustBeEaten =
                                keyEvent(keyEvent, info, wParamString);
                        if (eventMustBeEaten)
                            return new WinDef.LRESULT(1);
                    }
                    break;
                default:
                    logger.debug("Received unexpected key event wParam: " + wParam.intValue());
            }
        }
        return ExtendedUser32.INSTANCE.CallNextHookEx(keyboardHook, nCode, wParam, info);
    }

    private final Map<Key, KeyEventProcessing> currentlyPressedKeys = new HashMap<>();

    private boolean keyEvent(KeyEvent keyEvent, WinUser.KBDLLHOOKSTRUCT info, String wParamString) {
        Key key = keyEvent.action().key();
        if (keyEvent.action().state().pressed()) {
            KeyEventProcessing keyEventProcessing = currentlyPressedKeys.get(key);
            if (keyEventProcessing == null) {
                if (currentlyPressedKeys.values().stream().allMatch(KeyEventProcessing::mustBeEaten)) {
                    logKeyEvent(keyEvent, info, wParamString);
                    keyEventProcessing = comboWatcher.keyEvent(keyEvent);
                }
                else {
                    comboWatcher.interrupt();
                    keyEventProcessing = new KeyEventProcessing(false, false);
                }
                currentlyPressedKeys.put(key, keyEventProcessing);
            }
            return keyEventProcessing.mustBeEaten();
        }
        else {
            KeyEventProcessing keyEventProcessing = currentlyPressedKeys.remove(key);
            if (keyEventProcessing != null) {
                logKeyEvent(keyEvent, info, wParamString);
                if (keyEventProcessing.partOfCombo())
                    return comboWatcher.keyEvent(keyEvent).mustBeEaten();
                else
                    return false;
            }
            else
                return false;
        }
    }

    private static void logKeyEvent(KeyEvent keyEvent, WinUser.KBDLLHOOKSTRUCT info,
                                  String wParamString) {
        logger.debug("Received key event: " + keyEvent + ", vk = " +
                     WindowsVirtualKey.values.get(info.vkCode) +
                     ", scanCode = " + info.scanCode + ", flags = 0x" +
                     Integer.toHexString(info.flags) + ", wParam = " + wParamString);
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

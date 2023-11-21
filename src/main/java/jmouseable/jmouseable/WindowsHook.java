package jmouseable.jmouseable;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import jmouseable.jmouseable.KeyEvent.PressKeyEvent;
import jmouseable.jmouseable.KeyEvent.ReleaseKeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class WindowsHook {

    private static final Logger logger = LoggerFactory.getLogger(WindowsHook.class);
    private static final Instant systemStartTime;

    static {
        long uptimeMillis = ExtendedKernel32.INSTANCE.GetTickCount64();
        Instant now = Instant.now();
        systemStartTime = now.minusMillis(uptimeMillis);
    }

    private final MouseManager mouseManager;
    private final KeyboardManager keyboardManager;
    private final Ticker ticker;
    private final Map<Key, AtomicReference<Double>> currentlyPressedKeys = new HashMap<>();
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

    public WindowsHook(MouseManager mouseManager, KeyboardManager keyboardManager, Ticker ticker) {
        this.mouseManager = mouseManager;
        this.keyboardManager = keyboardManager;
        this.ticker = ticker;
    }

    public void installHooks() throws InterruptedException {
        // When running as a graalvm native image, we need to set the DPI awareness
        // (otherwise mouse coordinates are wrong on scaled displays).
        // It is recommended to do it with a manifest file instead but I am unsure
        // how to include it in the native image.
        // When running as a java app (non-native image), the DPI awareness already
        // seemed to be set, and SetProcessDpiAwarenessContext() returns error code 5.
        boolean setProcessDpiAwarenessContextResult =
                ExtendedUser32.INSTANCE.SetProcessDpiAwarenessContext(
                        new WinNT.HANDLE(Pointer.createConstant(-4L)));
        int dpiAwarenessErrorCode = Kernel32.INSTANCE.GetLastError();
        logger.info("SetProcessDpiAwarenessContext returned " +
                    setProcessDpiAwarenessContextResult +
                    (setProcessDpiAwarenessContextResult ? "" :
                            ", error code = " + dpiAwarenessErrorCode));
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
            sanityCheckCurrentlyPressedKeys(delta);
            ticker.update(delta);
            Thread.sleep(10L);
        }
    }

    /**
     * On the Windows lock screen, hit space then enter the pin. Space press is recorded by the app but the
     * corresponding release is never received. That is why we need to double-check if the key is still pressed
     * with GetAsyncKeyState.
     * Sometimes, it is the Win key (from Win + L) for which we do not receive the release event.
     */
    private void sanityCheckCurrentlyPressedKeys(double delta) {
        for (AtomicReference<Double> pressDuration : currentlyPressedKeys.values())
            pressDuration.set(pressDuration.get() + delta);
        Set<Key> keysThatDoNotSeemToBePressedAnymore = new HashSet<>();
        for (Map.Entry<Key, AtomicReference<Double>> entry : currentlyPressedKeys.entrySet()) {
            Key key = entry.getKey();
            AtomicReference<Double> pressDuration = entry.getValue();
            if (pressDuration.get() < 10)
                continue;
            short getAsyncKeyStateResult = User32.INSTANCE.GetAsyncKeyState(
                    WindowsVirtualKey.windowsVirtualKeyFromKey(key).virtualKeyCode);
            boolean pressed = (getAsyncKeyStateResult & 0x8000) != 0;
            if (!pressed)
                keysThatDoNotSeemToBePressedAnymore.add(key);
            else
                // The key was legitimately pressed for 10s.
                pressDuration.set(0d);
        }
        if (!keysThatDoNotSeemToBePressedAnymore.isEmpty()) {
            logger.info(
                    "Resetting KeyManager since the following currentlyPressedKeys are not pressed anymore according to GetAsyncKeyState: " +
                    keysThatDoNotSeemToBePressedAnymore);
            currentlyPressedKeys.clear();
            keyboardManager.reset();
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
                        boolean release = wParam.intValue() == WinUser.WM_KEYUP ||
                                          wParam.intValue() == WinUser.WM_SYSKEYUP;
                        Key key = WindowsVirtualKey.keyFromWindowsEvent(
                                WindowsVirtualKey.values.get(info.vkCode), info.scanCode,
                                info.flags);
                        if (key != null) {
                            Instant time = systemStartTime.plusMillis(info.time);
                            KeyEvent keyEvent = release ? new ReleaseKeyEvent(time, key) :
                                    new PressKeyEvent(time, key);
                            boolean eventMustBeEaten = keyEvent(keyEvent, info, wParamString);
                            if (eventMustBeEaten)
                                return new WinDef.LRESULT(1);
                        }
                    }
                    break;
                default:
                    logger.debug("Received unexpected key event wParam: " + wParam.intValue());
            }
        }
        return ExtendedUser32.INSTANCE.CallNextHookEx(keyboardHook, nCode, wParam, info);
    }

    private boolean keyEvent(KeyEvent keyEvent, WinUser.KBDLLHOOKSTRUCT info, String wParamString) {
        if (keyEvent.isPress()) {
            if (!currentlyPressedKeys.containsKey(keyEvent.key()))
                currentlyPressedKeys.put(keyEvent.key(), new AtomicReference<>(0d));
            if (!keyboardManager.currentlyPressed(keyEvent.key()) &&
                keyboardManager.allCurrentlyPressedArePartOfCombo()) {
                logKeyEvent(keyEvent, info, wParamString);
            }
        }
        else {
            currentlyPressedKeys.remove(keyEvent.key());
            if (keyboardManager.currentlyPressed(keyEvent.key()))
                logKeyEvent(keyEvent, info, wParamString);
        }
        return keyboardManager.keyEvent(keyEvent);
    }

    private static void logKeyEvent(KeyEvent keyEvent, WinUser.KBDLLHOOKSTRUCT info,
                                    String wParamString) {
        logger.debug("Received key event: " + keyEvent + ", vk = " +
                     WindowsVirtualKey.values.get(info.vkCode) + ", scanCode = " +
                     info.scanCode + ", flags = 0x" + Integer.toHexString(info.flags) +
                     ", wParam = " + wParamString);
    }

    private WinDef.LRESULT mouseHookCallback(int nCode, WinDef.WPARAM wParam,
                                             WinUser.MSLLHOOKSTRUCT info) {
        if (nCode >= 0) {
            WinDef.POINT mousePosition = info.pt;
            WindowsIndicator.mouseMoved(mousePosition);
            mouseManager.mouseMoved(mousePosition.x, mousePosition.y);
        }
        return ExtendedUser32.INSTANCE.CallNextHookEx(mouseHook, nCode, wParam, info);
    }

}

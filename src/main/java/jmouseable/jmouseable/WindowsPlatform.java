package jmouseable.jmouseable;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import jmouseable.jmouseable.KeyEvent.PressKeyEvent;
import jmouseable.jmouseable.KeyEvent.ReleaseKeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class WindowsPlatform implements Platform {

    private static final Logger logger = LoggerFactory.getLogger(WindowsPlatform.class);
    private static final Instant systemStartTime;

    static {
        long uptimeMillis = ExtendedKernel32.INSTANCE.GetTickCount64();
        Instant now = Instant.now();
        systemStartTime = now.minusMillis(uptimeMillis);
    }

    private MouseController mouseController;
    private KeyboardManager keyboardManager;
    private List<MousePositionListener> mousePositionListeners;
    private final Map<Key, AtomicReference<Double>> currentlyPressedNotEatenKeys = new HashMap<>();
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
    private final WinUser.MSG msg = new WinUser.MSG();
    private double enforceWindowsTopmostTimer;

    public WindowsPlatform() {
        WindowsMouse.windowsPlatform = this; // TODO Get rid of this.
        if (!acquireSingleInstanceMutex())
            throw new IllegalStateException("Another instance is already running");
        setDpiAwareness();
        installHooks();
    }

    @Override
    public void update(double delta) {
        while (User32.INSTANCE.PeekMessage(msg, null, 0, 0, 1)) {
            User32.INSTANCE.TranslateMessage(msg);
            User32.INSTANCE.DispatchMessage(msg);
        }
        sanityCheckCurrentlyPressedKeys(delta);
        enforceWindowsTopmostTimer -= delta;
        if (enforceWindowsTopmostTimer < 0) {
            // Every 200ms.
            enforceWindowsTopmostTimer = 0.2;
            WindowsOverlay.setTopmost();
        }
    }

    @Override
    public void reset(MouseController mouseController, KeyboardManager keyboardManager,
                      KeyboardLayout keyboardLayout, ModeMap modeMap,
                      List<MousePositionListener> mousePositionListeners) {
        this.mouseController = mouseController;
        this.keyboardManager = keyboardManager;
        this.mousePositionListeners = mousePositionListeners;
        Set<Key> allComboKeys = new HashSet<>();
        for (Mode mode : modeMap.modes()) {
            for (Combo combo : mode.comboMap().commandsByCombo().keySet()) {
                combo.precondition()
                     .mustBePressedKeySets()
                     .stream()
                     .flatMap(Collection::stream)
                     .forEach(allComboKeys::add);
                combo.precondition()
                     .mustNotBePressedKeySets()
                     .stream()
                     .flatMap(Collection::stream)
                     .forEach(allComboKeys::add);
                combo.sequence()
                     .moves()
                     .stream()
                     .map(ComboMove::key)
                     .forEach(allComboKeys::add);
            }
        }
        WindowsVirtualKey.mapKeysToVirtualKeysUsingLayout(allComboKeys, keyboardLayout);
        WinDef.POINT mousePosition = WindowsMouse.cursorPositionAndSize().position();
        mousePositionListeners.forEach(
                mousePositionListener -> mousePositionListener.mouseMoved(mousePosition.x,
                        mousePosition.y));
    }

    /**
     * When running as a graalvm native image, we need to set the DPI awareness
     * (otherwise mouse coordinates are wrong on scaled displays).
     * It is recommended to do it with a manifest file instead but I am unsure
     * how to include it in the native image.
     * When running as a java app (non-native image), the DPI awareness already
     * seemed to be set, and SetProcessDpiAwarenessContext() returns error code 5.
     */
    private void setDpiAwareness() {
        boolean setProcessDpiAwarenessContextResult =
                ExtendedUser32.INSTANCE.SetProcessDpiAwarenessContext(
                        new WinNT.HANDLE(Pointer.createConstant(-4L)));
        int dpiAwarenessErrorCode = Kernel32.INSTANCE.GetLastError();
        logger.info("SetProcessDpiAwarenessContext returned " +
                    setProcessDpiAwarenessContextResult +
                    (setProcessDpiAwarenessContextResult ? "" :
                            ", error code = " + dpiAwarenessErrorCode));
    }

    private void installHooks() {
        WinDef.HMODULE hMod = Kernel32.INSTANCE.GetModuleHandle(null);
        keyboardHookCallback = WindowsPlatform.this::keyboardHookCallback;
        keyboardHook = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL,
                keyboardHookCallback, hMod, 0);
        mouseHookCallback = WindowsPlatform.this::mouseHookCallback;
        mouseHook = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_MOUSE_LL,
                mouseHookCallback, hMod, 0);
        addJvmShutdownHook();
        logger.info("Keyboard and mouse hooks installed");
    }

    private void addJvmShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            WindowsMouse.showCursor(); // Just in case we are shutting down while cursor is hidden.
            boolean keyboardHookUnhooked =
                    User32.INSTANCE.UnhookWindowsHookEx(keyboardHook);
            boolean mouseHookUnhooked = User32.INSTANCE.UnhookWindowsHookEx(mouseHook);
            logger.info(
                    "Keyboard and mouse hooks uninstalled " + keyboardHookUnhooked + " " +
                    mouseHookUnhooked);
            releaseSingleInstanceMutex();
            logger.info("Single instance mutex released");
        }));
    }

    /**
     * On the Windows lock screen, hit space then enter the pin. Space press is recorded by the app but the
     * corresponding release is never received. That is why we need to double-check if the key is still pressed
     * with GetAsyncKeyState.
     * Sometimes, it is the Win key (from Win + L) for which we do not receive the release event.
     * getAsyncKeyStateResult is not working the way I expected: it returns not pressed for keys pressed
     * after other keys: press left button, then move mouse: the key for press left shows as not pressed
     * according to getAsyncKeyStateResult. That is why we consider not eaten keys only.
     * The getAsyncKeyStateResult call could probably be taken out (it is useless) and replaced with
     * a simple 10s expiration time.
     */
    private void sanityCheckCurrentlyPressedKeys(double delta) {
        for (AtomicReference<Double> pressDuration : currentlyPressedNotEatenKeys.values())
            pressDuration.set(pressDuration.get() + delta);
        Set<Key> keysThatDoNotSeemToBePressedAnymore = new HashSet<>();
        for (Map.Entry<Key, AtomicReference<Double>> entry : currentlyPressedNotEatenKeys.entrySet()) {
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
                    "Resetting KeyboardManager and MouseController since the following currentlyPressedKeys are not pressed anymore according to GetAsyncKeyState: " +
                    keysThatDoNotSeemToBePressedAnymore);
            currentlyPressedNotEatenKeys.clear();
            keyboardManager.reset();
            mouseController.reset();
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
                    if ((info.flags & ExtendedUser32.LLKHF_INJECTED) ==
                        ExtendedUser32.LLKHF_INJECTED) {
                        // SendInput from another app.
                    }
                    else if (info.vkCode == WindowsVirtualKey.VK_LMENU.virtualKeyCode &&
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

    private boolean keyEvent(KeyEvent keyEvent, WinUser.KBDLLHOOKSTRUCT info,
                             String wParamString) {
        if (keyEvent.isPress()) {
            if (!keyboardManager.currentlyPressed(keyEvent.key()) &&
                keyboardManager.allCurrentlyPressedKeysArePartOfCombo()) {
                logKeyEvent(keyEvent, info, wParamString);
            }
        }
        else {
            currentlyPressedNotEatenKeys.remove(keyEvent.key());
            if (keyboardManager.currentlyPressed(keyEvent.key()))
                logKeyEvent(keyEvent, info, wParamString);
        }
        boolean mustBeEaten = keyboardManager.keyEvent(keyEvent);
        if (keyEvent.isPress() && !mustBeEaten) {
            currentlyPressedNotEatenKeys.computeIfAbsent(keyEvent.key(),
                    key -> new AtomicReference<>(0d)).set(0d);
        }
        return mustBeEaten;
    }

    private static void logKeyEvent(KeyEvent keyEvent, WinUser.KBDLLHOOKSTRUCT info,
                                    String wParamString) {
        logger.trace("Received key event: " + keyEvent + ", vk = " +
                     WindowsVirtualKey.values.get(info.vkCode) + ", scanCode = " +
                     info.scanCode + ", flags = 0x" + Integer.toHexString(info.flags) +
                     ", wParam = " + wParamString);
    }

    private WinDef.LRESULT mouseHookCallback(int nCode, WinDef.WPARAM wParam,
                                             WinUser.MSLLHOOKSTRUCT info) {
        if (nCode >= 0) {
            WinDef.POINT mousePosition = info.pt;
            WindowsOverlay.mouseMoved(mousePosition);
            mousePositionListeners.forEach(
                    listener -> listener.mouseMoved(mousePosition.x, mousePosition.y));
        }
        return ExtendedUser32.INSTANCE.CallNextHookEx(mouseHook, nCode, wParam, info);
    }

    /**
     * mouseHookCallback is not called when we call the SetMousePos() API.
     */
    public void mousePositionSet(WinDef.POINT mousePosition) {
        WindowsOverlay.mouseMoved(mousePosition);
        mousePositionListeners.forEach(
                listener -> listener.mouseMoved(mousePosition.x, mousePosition.y));
    }

}

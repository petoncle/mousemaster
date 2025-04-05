package mousemaster;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import mousemaster.KeyEvent.PressKeyEvent;
import mousemaster.KeyEvent.ReleaseKeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class WindowsPlatform implements Platform {

    private static final Logger logger = LoggerFactory.getLogger(WindowsPlatform.class);

    private final boolean keyRegurgitationEnabled;
    private final KeyRegurgitator keyRegurgitator = new KeyRegurgitator();
    private final WindowsClock clock = new WindowsClock();

    private MouseController mouseController;
    private KeyboardManager keyboardManager;
    private List<MousePositionListener> mousePositionListeners;
    private ModeMap modeMap;
    private final Map<Key, AtomicReference<Double>> currentlyPressedNotEatenKeys = new HashMap<>();
    private WinUser.HHOOK keyboardHook;
    private WinUser.HHOOK mouseHook;
    private final BlockingQueue<WinDef.POINT> mousePositionQueue = new LinkedBlockingDeque<>();
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
    private boolean mustEatNextReleaseOfRightalt = false;
    private Set<Key> extendedKeys = new HashSet<>();
    private Mode currentMode;

    public WindowsPlatform(boolean multipleInstancesAllowed, boolean keyRegurgitationEnabled) {
        this.keyRegurgitationEnabled = keyRegurgitationEnabled;
        WindowsMouse.windowsPlatform = this; // TODO Get rid of this.
        WindowsUiAccess.checkAndTryToGetUiAccess(); // Done before acquiring the single instance mutex.
        if (!multipleInstancesAllowed && !acquireSingleInstanceMutex())
            throw new IllegalStateException("Another instance is already running");
        setDpiAwareness();
    }

    @Override
    public void update(double delta) {
        WindowsOverlay.waitForZoomBeforeRepainting = false;
        WindowsKeyboard.update(delta);
        sanityCheckCurrentlyPressedKeys(delta);
        enforceWindowsTopmostTimer -= delta;
        if (enforceWindowsTopmostTimer < 0) {
            // Every 200ms.
            enforceWindowsTopmostTimer = 0.2;
            WindowsOverlay.setTopmost();
        }
        WindowsOverlay.update(delta);
    }

    @Override
    public void windowsMessagePump() {
        while (User32.INSTANCE.PeekMessage(msg, null, 0, 0, 1)) {
            User32.INSTANCE.TranslateMessage(msg);
            User32.INSTANCE.DispatchMessage(msg);
        }
    }

    @Override
    public void sleep() throws InterruptedException {
        windowsMessagePump();
        while (true) {
            WinDef.POINT mousePosition = null;
            WinDef.POINT polledMousePosition;
            while ((polledMousePosition = mousePositionQueue.poll()) != null) {
                mousePosition = polledMousePosition;
            }
            if (mousePosition == null)
                break;
            WindowsOverlay.mouseMoved(mousePosition);
            for (MousePositionListener listener : mousePositionListeners) {
                // ZoomListener can take up to 30ms.
                listener.mouseMoved(mousePosition.x, mousePosition.y);
            }
        }
        windowsMessagePump();
        long beforeTime = System.nanoTime();
        while (true) {
            long currentTime = System.nanoTime();
            if ((currentTime - beforeTime) / 1e6 >= 10)
                break;
            Thread.sleep(1);
            windowsMessagePump();
        }
    }

    @Override
    public void reset(MouseController mouseController, KeyboardManager keyboardManager,
                      ModeMap newModeMap,
                      List<MousePositionListener> mousePositionListeners,
                      KeyboardLayout activeKeyboardLayout) {
        ModeMap oldModeMap = this.modeMap;
        Set<HintMeshConfiguration> oldHintMeshConfigurations = new HashSet<>();
        if (oldModeMap != null) {
            oldModeMap.modes()
                      .stream()
                      .map(Mode::hintMesh)
                      .forEach(oldHintMeshConfigurations::add);
        }
        this.mouseController = mouseController;
        this.keyboardManager = keyboardManager;
        this.mousePositionListeners = mousePositionListeners;
        WindowsKeyboard.activeKeyboardLayout = activeKeyboardLayout;
        Set<HintMeshConfiguration> newHintMeshConfigurations = new HashSet<>();
        for (Mode mode : newModeMap.modes()) {
            newHintMeshConfigurations.add(mode.hintMesh());
        }
        if (oldModeMap != null && !newHintMeshConfigurations.equals(oldHintMeshConfigurations)) {
            logger.debug(
                    "Flushing overlay cache because hint mesh configurations have changed");
            WindowsOverlay.flushCache();
        }
        this.modeMap = newModeMap;
        WinDef.POINT mousePosition = WindowsMouse.findMousePosition();
        mousePositionListeners.forEach(
                mousePositionListener -> mousePositionListener.mouseMoved(mousePosition.x,
                        mousePosition.y));
        if (keyboardHookCallback == null)
            installHooks();
    }

    /**
     * When running as a graalvm native image, we need to set the DPI awareness
     * (otherwise mouse coordinates are wrong on scaled displays).
     * It is recommended to do it with a manifest file instead but I am unsure
     * how to include it in the native image.
     * When running as a java app (non-native image), the DPI awareness seems to
     * already be set, and SetProcessDpiAwarenessContext() returns error code 5.
     */
    private void setDpiAwareness() {
        boolean setProcessDpiAwarenessContextResult =
                ExtendedUser32.INSTANCE.SetProcessDpiAwarenessContext(new WinNT.HANDLE(
                        Pointer.createConstant(
                                ExtendedUser32.DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2)));
        int dpiAwarenessErrorCode = Kernel32.INSTANCE.GetLastError();
        if (!setProcessDpiAwarenessContextResult)
            logger.info("Unable to SetProcessDpiAwarenessContext: error code = " +
                        dpiAwarenessErrorCode);
    }

    private void installHooks() {
        WinDef.HMODULE hMod = Kernel32.INSTANCE.GetModuleHandle(null);
        keyboardHookCallback = WindowsPlatform.this::keyboardHookCallback;
        keyboardHook = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL,
                keyboardHookCallback, hMod, 0);
        if (keyboardHook == null)
            throw new IllegalStateException(
                    "Unable to install keyboard hook " + Native.getLastError());
        logger.trace("Installed keyboard hook successfully");
        // Run mouse hook in a separate thread to avoid lags:
        // https://www.linkedin.com/pulse/windows-mouse-hook-lagging-simone-galleni
        // https://stackoverflow.com/a/52201983
        Thread mouseHookThread = new Thread(this::mouseHook);
        mouseHookThread.setName("mouse-hook");
        mouseHookThread.start();
        addJvmShutdownHook();
    }

    private void mouseHook() {
        WinDef.HMODULE hMod = Kernel32.INSTANCE.GetModuleHandle(null);
        mouseHookCallback = WindowsPlatform.this::mouseHookCallback;
        mouseHook = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_MOUSE_LL,
                mouseHookCallback, hMod, 0);
        if (mouseHook == null)
            throw new IllegalStateException(
                    "Unable to install mouse hook " + Native.getLastError());
        logger.trace("Installed mouse hook successfully");
        WinUser.MSG msg = new WinUser.MSG();
        while (User32.INSTANCE.GetMessage(msg, null, 0, 0) > 0) {
            User32.INSTANCE.TranslateMessage(msg);
            User32.INSTANCE.DispatchMessage(msg);
        }
    }

    private void addJvmShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    private static boolean shutdown = false;

    @Override
    public void shutdown() {
        if (shutdown)
            return;
        shutdown = true;
        WindowsMouse.showCursor(); // Just in case we are shutting down while cursor is hidden.
        boolean keyboardHookUnhooked =
                User32.INSTANCE.UnhookWindowsHookEx(keyboardHook);
        boolean mouseHookUnhooked = User32.INSTANCE.UnhookWindowsHookEx(mouseHook);
        logger.info(
                "Uninstalled Keyboard and mouse hooks " +
                (keyboardHookUnhooked && mouseHookUnhooked ? "successfully" : "unsuccessfully"));
        releaseSingleInstanceMutex();
        logger.trace("Released single instance mutex");
    }

    @Override
    public KeyRegurgitator keyRegurgitator() {
        return keyRegurgitator;
    }

    @Override
    public PlatformClock clock() {
        return clock;
    }

    @Override
    public KeyboardLayout activeKeyboardLayout() {
        return WindowsVirtualKey.activeKeyboardLayout();
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
                    "Resetting KeyboardManager and MouseController because the following currentlyPressedKeys are not pressed anymore according to GetAsyncKeyState: " +
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
        clock.setLastKeyboardHookEventRelativeTimeMillis(info.time);
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
                    logKeyEvent(info, wParamString);
                    if ((info.flags & ExtendedUser32.LLKHF_INJECTED) ==
                        ExtendedUser32.LLKHF_INJECTED) {
                        // SendInput from another app (or from mousemaster).
                    }
                    else if (info.vkCode == WindowsVirtualKey.VK_LMENU.virtualKeyCode &&
                        (info.flags & 0b10000) == 0b10000) {
                        // 0b10000 means alt is pressed. This avoids getting two consecutive duplicate alt press,release events.
                    }
                    else {
                        // Pressing altgr corresponds to the following sequence
                        // vkCode = 0xa2 (VK_LCONTROL), scanCode = 0x21d, flags = 0x20, wParam = WM_SYSKEYDOWN
                        // vkCode = 0xa5 (VK_RMENU), scanCode = 0x38, flags = 0x21, wParam = WM_SYSKEYDOWN
                        // vkCode = 0xa2 (VK_LCONTROL), scanCode = 0x21d, flags = 0x80, wParam = WM_KEYUP
                        // vkCode = 0xa5 (VK_RMENU), scanCode = 0x38, flags = 0x81, wParam = WM_KEYUP
                        // Pressing leftctrl corresponds to:
                        // vkCode = 0xa2 (VK_LCONTROL), scanCode = 0x1d, flags = 0x0, wParam = WM_KEYDOWN
                        // vkCode = 0xa2 (VK_LCONTROL), scanCode = 0x1d, flags = 0x80, wParam = WM_KEYUP
                        // We ignore that leftctrl in altgr.
                        boolean altgrLeftctrl = info.vkCode ==
                                                WindowsVirtualKey.VK_LCONTROL.virtualKeyCode &&
                                                info.scanCode == 0x21d;
                        boolean release = wParam.intValue() == WinUser.WM_KEYUP ||
                                          wParam.intValue() == WinUser.WM_SYSKEYUP;
                        Key key;
                        if (altgrLeftctrl)
                            // Consider altgr's leftctrl and altgr's rightalt as the same key: Key.rightalt.
                            key = Key.rightalt;
                        else
                            key = WindowsVirtualKey.keyFromWindowsEvent(
                                    WindowsVirtualKey.values.get(info.vkCode),
                                    info.scanCode, info.flags);
                        if (key != null) {
                            Instant time = clock.now();
                            if (release)
                                WindowsKeyboard.keyReleasedByUser(key);
                            else
                                WindowsKeyboard.keyPressedByUser(key);
                            KeyEvent keyEvent = release ? new ReleaseKeyEvent(time, key) :
                                    new PressKeyEvent(time, key);
                            boolean eventMustBeEaten = keyEvent(keyEvent, info, wParamString);
                            if (release && eventMustBeEaten && altgrLeftctrl)
                                mustEatNextReleaseOfRightalt = true;
                            else if (release && key.equals(Key.rightalt) && mustEatNextReleaseOfRightalt) {
                                eventMustBeEaten = true;
                                mustEatNextReleaseOfRightalt = false;
                            }
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
        if (!keyEvent.isPress())
            currentlyPressedNotEatenKeys.remove(keyEvent.key());
        KeyboardManager.EatAndRegurgitates eatAndRegurgitates = keyboardManager.keyEvent(keyEvent);
        if (keyEvent.isPress() && !eatAndRegurgitates.mustBeEaten()) {
            currentlyPressedNotEatenKeys.computeIfAbsent(keyEvent.key(),
                    key -> new AtomicReference<>(0d)).set(0d);
        }
        boolean keyEventIsExtendedKey = (info.flags & 1) == 1;
        if (keyEventIsExtendedKey)
            // The Windows callback tells us if a key is an extended key.
            // This is a simple way to keep that knowledge, which we might need later (regurgitation).
            // Another way could be to hardcode the virtual codes corresponding to extended keys.
            extendedKeys.add(keyEvent.key());
        if (keyRegurgitationEnabled && !eatAndRegurgitates.keysToRegurgitate().isEmpty()) {
            for (Key keyToRegurgitate : eatAndRegurgitates.keysToRegurgitate()) {
                // If the following combo is defined: +leftwin-0 +e,
                // Then, when pressing leftwin + v,
                // And if non-eaten key (v) is pressed then regurgitation should not start repeating.
                keyRegurgitator.regurgitate(keyToRegurgitate,
                        !keyEvent.isRelease() && currentlyPressedNotEatenKeys.isEmpty(),
                        keyEvent.isRelease() && keyEvent.key().equals(keyToRegurgitate));
            }
        }
        return eatAndRegurgitates.mustBeEaten();
    }

    private static void logKeyEvent(WinUser.KBDLLHOOKSTRUCT info,
                                    String wParamString) {
        if (logger.isTraceEnabled())
            logger.trace(
                    "Received key event: vkCode = 0x" + Integer.toHexString(info.vkCode) +
                    " (" + WindowsVirtualKey.values.get(info.vkCode) +
                    "), scanCode = 0x" + Integer.toHexString(info.scanCode) +
                    ", flags = 0x" + Integer.toHexString(info.flags) + ", wParam = " +
                    wParamString);
    }

    /**
     * Note: this method is called from the mouse-hook thread.
     */
    private WinDef.LRESULT mouseHookCallback(int nCode, WinDef.WPARAM wParam,
                                             WinUser.MSLLHOOKSTRUCT info) {
        if (nCode >= 0) {
            WinDef.POINT mousePosition = info.pt;
            mousePositionQueue.add(mousePosition);
            if ((info.flags & ExtendedUser32.LLMHF_INJECTED) ==
                ExtendedUser32.LLMHF_INJECTED) {
                // SendInput from another app (or from mousemaster).
            }
            else {
                int WM_MOUSEMOVE = 0x0200;
                if (wParam.intValue() != WM_MOUSEMOVE) {
                    // This is racy, but regurgitating from the event loop (where
                    // mousePositionQueue is consumed) is too late and a shift click
                    // cannot be done if shift is the key being eaten.
                    if (keyboardManager.pressingKeys()) {
                        logger.info(
                                "Regurgitating pressed keys because physical mouse buttons are being used");
                        keyboardManager.regurgitatePressedKeys();
                    }
                }
            }
        }
        return ExtendedUser32.INSTANCE.CallNextHookEx(mouseHook, nCode, wParam, info);
    }

    public void mousePositionSet(WinDef.POINT mousePosition) {
        mousePositionQueue.add(mousePosition);
    }

    @Override
    public void modeChanged(Mode newMode) {
        // If zoom is going to change, WindowsOverlay.setZoom() will repaint the hint mesh
        // after the zoom window is initialized.
        WindowsOverlay.waitForZoomBeforeRepainting =
                currentMode != null && !currentMode.zoom().equals(newMode.zoom());
        currentMode = newMode;
    }

    @Override
    public void modeTimedOut() {
        // No op.
    }
}

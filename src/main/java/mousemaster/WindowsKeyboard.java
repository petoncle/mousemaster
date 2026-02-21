package mousemaster;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class WindowsKeyboard {

    private static final Logger logger = LoggerFactory.getLogger(WindowsKeyboard.class);

    /**
     * https://learn.microsoft.com/en-us/windows/win32/inputdev/about-keyboard-input
     */
    public static Set<Key> extendedKeys = Set.of(
            Key.rightalt,
            Key.rightctrl,
            Key.insert,
            Key.del,
            Key.home,
            Key.end,
            Key.pageup,
            Key.pagedown,
            Key.leftarrow,
            Key.uparrow,
            Key.rightarrow,
            Key.downarrow,
            Key.numlock,
            Key.break_,
            Key.printscreen,
            Key.numpaddivide, // No numpadmultiply?
            Key.enter // Only enter from numpad?
    );

    public static KeyboardLayout activeKeyboardLayout;
    /**
     * When sending -leftalt +uparrow, leftalt is interleaved with uparrow,
     * and that prevent leftalt from being properly released:
     * 2026-02-21T23:30:38.338 [main] DEBUG mousemaster.MacroPlayer - Executing macro parallel: -leftalt +uparrow PT0S
     * 2026-02-21T23:30:38.338 [main] TRACE mousemaster.WindowsPlatform - Received key event: vkCode = 0xa4 (VK_LMENU), scanCode = 0x0, flags = 0x90, wParam = WM_KEYUP
     * 2026-02-21T23:30:38.339 [main] TRACE mousemaster.WindowsPlatform - Received key event: vkCode = 0x26 (VK_UP), scanCode = 0x0, flags = 0x11, wParam = WM_KEYDOWN
     * 2026-02-21T23:30:38.341 [main] TRACE mousemaster.WindowsPlatform - Received key event: vkCode = 0xa4 (VK_LMENU), scanCode = 0x38, flags = 0x30, wParam = WM_SYSKEYDOWN
     */
    private record SendInputMove(ResolvedKeyMacroMove move, boolean startRepeat) {}
    private static final List<SendInputMove> sendInputQueue = new LinkedList<>();
    private static ResolvedKeyMacroMove moveWaitingForKeyboardHookCallbackAcknowledgment;

    public static void sendInputKeys(List<ResolvedKeyMacroMove> moves, boolean startRepeat, boolean oneInputPerCall) {
        if (oneInputPerCall) {
            boolean sendInputQueueWasEmpty = sendInputQueue.isEmpty();
            for (ResolvedKeyMacroMove move : moves)
                sendInputQueue.add(new SendInputMove(move, startRepeat));
            if (moveWaitingForKeyboardHookCallbackAcknowledgment == null &&
                sendInputQueueWasEmpty)
                processOneSendInputMove();
        }
        else
            sendInputKeys(moves, startRepeat);
    }

    private static final Set<Key> pressedKeys = new HashSet<>();
    private static Key pressedKeyToRepeat;
    private static double durationUntilNextKeyPressRepeat;

    public static void keyReleasedByUser(Key key) {
        if (key == pressedKeyToRepeat)
            pressedKeyToRepeat = null;
        pressedKeys.remove(key);
    }

    public static void keyPressedByUser(Key key) {
        pressedKeys.add(key);
    }

    public static void keyboardHookCallback(WinUser.KBDLLHOOKSTRUCT info,
                                            WinDef.WPARAM wParam, String wParamString, KeyEvent keyEvent,
                                            boolean injected, boolean altgrLeftctrl) {
        if (!injected)
            return;
        if (moveWaitingForKeyboardHookCallbackAcknowledgment == null)
            return;
        if (keyEvent == null)
            return;
        if (keyEvent.key()
                    .equals(moveWaitingForKeyboardHookCallbackAcknowledgment.key()) &&
            keyEvent.isPress() ==
            moveWaitingForKeyboardHookCallbackAcknowledgment.press()) {
            if (keyEvent.key() == Key.leftalt) {
                if (keyEvent.isPress() && wParam.intValue() != WinUser.WM_SYSKEYDOWN)
                    return;
            }
            moveWaitingForKeyboardHookCallbackAcknowledgment = null;
            // TODO consume sendInputQueue's next element (but only if this hook is not being called from sendInputKeys())?
        }
    }

    public static void update(double delta) {
        if (moveWaitingForKeyboardHookCallbackAcknowledgment != null)
            return;
        if (processOneSendInputMove())
            return;
        if (pressedKeyToRepeat == null)
            return;
        durationUntilNextKeyPressRepeat -= delta;
        if (durationUntilNextKeyPressRepeat <= 0) {
            sendInputKeys(List.of(new ResolvedKeyMacroMove(pressedKeyToRepeat, true, MacroMoveDestination.OS)), true);
            durationUntilNextKeyPressRepeat = 0.025d;
        }
    }

    private static boolean processOneSendInputMove() {
        if (!sendInputQueue.isEmpty()) {
            SendInputMove sendInputMove = sendInputQueue.removeFirst();
            logger.trace("Waiting for ackowledgment of " + sendInputMove.move());
            moveWaitingForKeyboardHookCallbackAcknowledgment = sendInputMove.move();
            // The following sendInput call will call keyboardHookCallback.
            // That is why the moveWaitingForKeyboardHookCallbackAcknowledgment
            // is set before calling sendInputKeys.
            // Goal is to block any further processing until the keyboard hook receives the event for
            // the move (and for leftalt, there are two events: WM_KEYDOWN then WM_SYSKEYDOWN).
            sendInputKeys(List.of(sendInputMove.move()), sendInputMove.startRepeat());
            return true;
        }
        return false;
    }

    private static void sendInputKeys(List<ResolvedKeyMacroMove> moves, boolean triggerKeyRepeating) {
        // Send a press event for the key to regurgitate.
        WinUser.INPUT[] pInputs =
                (WinUser.INPUT[]) new WinUser.INPUT().toArray(moves.size());
        if (moves.stream()
                 .map(move -> WindowsVirtualKey.windowsVirtualKeyFromKey(move.key(),
                         activeKeyboardLayout))
                 .anyMatch(Objects::isNull)) {
            // Happens when a macro output contains a key not in the active keyboard layout.
            return;
        }
        for (int moveIndex = 0; moveIndex < moves.size(); moveIndex++) {
            ResolvedKeyMacroMove move = moves.get(moveIndex);
            WindowsVirtualKey windowsVirtualKey =
                    WindowsVirtualKey.windowsVirtualKeyFromKey(move.key(),
                            activeKeyboardLayout);
            // Key already pressed.
            if (move.press()) {
                if (triggerKeyRepeating) {
                    pressedKeyToRepeat = move.key();
                    durationUntilNextKeyPressRepeat = 0.5d;
                }
                pressedKeys.add(move.key());
            }
            else if (pressedKeyToRepeat == move.key()) {
                pressedKeyToRepeat = null;
                pressedKeys.remove(move.key());
            }
            pInputs[moveIndex].type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
            pInputs[moveIndex].input.setType(WinUser.KEYBDINPUT.class);
            // Some keys are extended keys and need dwFlag:
            // https://stackoverflow.com/questions/44924962/sendinput-on-c-doesnt-take-ctrl-and-shift-in-account
            pInputs[moveIndex].input.ki.wVk =
                    new WinDef.WORD(windowsVirtualKey.virtualKeyCode);
            int flag = (extendedKeys.contains(move.key()) ? 1 : 0) | (!move.press() ? 2 : 0);
            // If KEYEVENTF_EXTENDEDKEY dwFlag is not set,
            // rightalt + f7 in IntelliJ gets stuck: it expects alt to be released (press-and-release leftalt to unstuck).
            pInputs[moveIndex].input.ki.dwFlags = new WinDef.DWORD(flag);
        }
        WinDef.DWORD sendInput =
                User32.INSTANCE.SendInput(new WinDef.DWORD(moves.size()), pInputs,
                        pInputs[0].size());
    }

    public static void sendInputString(String string) {
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            // Key down.
            WinUser.INPUT[] pInputs =
                    (WinUser.INPUT[]) new WinUser.INPUT().toArray(1);
            pInputs[0].type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
            pInputs[0].input.setType(WinUser.KEYBDINPUT.class);
            pInputs[0].input.ki.wVk = new WinDef.WORD(0);
            pInputs[0].input.ki.wScan = new WinDef.WORD(c);
            pInputs[0].input.ki.dwFlags = new WinDef.DWORD(WinUser.KEYBDINPUT.KEYEVENTF_UNICODE);
            User32.INSTANCE.SendInput(new WinDef.DWORD(1), pInputs,
                    pInputs[0].size());
            // Key up.
            pInputs =
                    (WinUser.INPUT[]) new WinUser.INPUT().toArray(1);
            pInputs[0].type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
            pInputs[0].input.setType(WinUser.KEYBDINPUT.class);
            pInputs[0].input.ki.wVk = new WinDef.WORD(0);
            pInputs[0].input.ki.wScan = new WinDef.WORD(c);
            pInputs[0].input.ki.dwFlags = new WinDef.DWORD(
                    WinUser.KEYBDINPUT.KEYEVENTF_UNICODE |
                    WinUser.KEYBDINPUT.KEYEVENTF_KEYUP);
            User32.INSTANCE.SendInput(new WinDef.DWORD(1), pInputs,
                    pInputs[0].size());
        }
    }

}

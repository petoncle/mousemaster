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

    public static void reset() {
        sendInputQueue.clear();
        moveWaitingForKeyboardHookCallbackAcknowledgment = null;
        ticksWaitingForAcknowledgment = 0;
        pressedKeyToRepeat = null;
        durationUntilNextKeyPressRepeat = 0;
        repeatStartedDuringCurrentTick = false;
    }

    /**
     * When sending -leftalt +uparrow, leftalt is interleaved with uparrow,
     * and that prevent leftalt from being properly released:
     * 2026-02-21T23:30:38.338 [main] DEBUG mousemaster.MacroPlayer - Executing macro parallel: -leftalt +uparrow PT0S
     * 2026-02-21T23:30:38.338 [main] TRACE mousemaster.WindowsPlatform - Received key event: vkCode = 0xa4 (VK_LMENU), scanCode = 0x0, flags = 0x90, wParam = WM_KEYUP
     * 2026-02-21T23:30:38.339 [main] TRACE mousemaster.WindowsPlatform - Received key event: vkCode = 0x26 (VK_UP), scanCode = 0x0, flags = 0x11, wParam = WM_KEYDOWN
     * 2026-02-21T23:30:38.341 [main] TRACE mousemaster.WindowsPlatform - Received key event: vkCode = 0xa4 (VK_LMENU), scanCode = 0x38, flags = 0x30, wParam = WM_SYSKEYDOWN
     */
    private record SendInputMove(ResolvedMacroMove move, boolean startRepeat) {}
    private static final List<SendInputMove> sendInputQueue = new LinkedList<>();
    private static ResolvedKeyMacroMove moveWaitingForKeyboardHookCallbackAcknowledgment;
    private static int ticksWaitingForAcknowledgment;

    public static void sendInputMoves(List<ResolvedMacroMove> moves, boolean startRepeat) {
        boolean sendInputQueueWasEmpty = sendInputQueue.isEmpty();
        for (ResolvedMacroMove move : moves)
            sendInputQueue.add(new SendInputMove(move, startRepeat));
        if (moveWaitingForKeyboardHookCallbackAcknowledgment == null &&
            sendInputQueueWasEmpty)
            processOneSendInputMove();
    }

    private static Key pressedKeyToRepeat;
    private static double durationUntilNextKeyPressRepeat;
    /**
     * Used to prevent keyPressedNotEaten from killing a repeat that was just
     * started by a macro during the same event processing (e.g. combo triggers
     * macro +a which starts repeating, then keyPressedNotEaten is called for
     * the triggering key: we must not stop the repeat).
     */
    private static boolean repeatStartedDuringCurrentTick;

    public static void keyPressedNotEaten(Key key) {
        if (!repeatStartedDuringCurrentTick || !key.equals(pressedKeyToRepeat))
            pressedKeyToRepeat = null;
    }

    public static void keyReleasedNotEaten(Key key) {
        if (key.equals(pressedKeyToRepeat))
            pressedKeyToRepeat = null;
    }

    private static final Set<Key> userPressedKeys = new HashSet<>();

    private static final Set<Key> modifierKeys =
            Set.of(Key.leftshift, Key.rightshift, Key.leftctrl, Key.rightctrl,
                    Key.leftalt, Key.rightalt, Key.leftwin, Key.rightwin);

    public static void keyboardHookCallback(WinUser.KBDLLHOOKSTRUCT info,
                                            WinDef.WPARAM wParam, String wParamString, KeyEvent keyEvent,
                                            boolean injected, boolean altgrLeftctrl) {
        if (!injected) {
            if (keyEvent.isRelease())
                userPressedKeys.remove(keyEvent.key());
            else
                userPressedKeys.add(keyEvent.key());
            return;
        }
        if (moveWaitingForKeyboardHookCallbackAcknowledgment == null)
            return;
        if (keyEvent == null)
            return;
        if (keyEvent.key()
                    .equals(moveWaitingForKeyboardHookCallbackAcknowledgment.key()) &&
            keyEvent.isPress() ==
            moveWaitingForKeyboardHookCallbackAcknowledgment.press()) {
            if (keyEvent.key().equals(Key.leftalt)) {
//                if (keyEvent.isPress()
//                    && (
//                    wParam.intValue() != WinUser.WM_SYSKEYDOWN
                    // Received key event: +leftalt, altgrLeftctrl = false, injected = true, vkCode = 0xa4 (VK_LMENU), scanCode = 0x0, flags = 0x30, wParam = WM_SYSKEYDOWN
                    // Received key event: +leftalt, altgrLeftctrl = false, injected = true, vkCode = 0xa4 (VK_LMENU), scanCode = 0x38, flags = 0x30, wParam = WM_SYSKEYDOWN
//                    || info.scanCode != 0x38
//                    )
//                )
//                    return;
                if (keyEvent.isRelease() &&
                    userPressedKeys.contains(keyEvent.key()) &&
                    info.scanCode != 0x38)
                    // 0x38 is the phantom event that happens after the normal event (scanCode 0x0), only when the user is actually pressing leftalt.
                    // We need to wait for this phantom event to be received, otherwise leftalt is still being pressed.
                    return;
            }
            if (logger.isTraceEnabled())
                logger.trace("Received ackowledgment of " +
                             moveWaitingForKeyboardHookCallbackAcknowledgment +
                             ", scanCode = 0x" + Integer.toHexString(info.scanCode));
            moveWaitingForKeyboardHookCallbackAcknowledgment = null;
            // Continue processing next move (this is done synchronously before WindowsPlatform#keyboardHookCallback returns)
            // idle-mode.macro.gitc={+g +i -g -i} +c -> 'git checkout'
            // If user presses g, i, then a, the regurgitation of g then i will happen on
            // the +a event (and before WindowsPlatform#keyboardHookCallback returns, i.e. before 'a' will be typed)
            // User will see 'gia' (and not 'gai').
            if (!modifierKeys.contains(keyEvent.key()))
                processOneSendInputMove();
        }
    }

    public static void update(double delta) {
        repeatStartedDuringCurrentTick = false;
        if (moveWaitingForKeyboardHookCallbackAcknowledgment != null) {
            ticksWaitingForAcknowledgment++;
            if (ticksWaitingForAcknowledgment >= 5) {
                logger.info("Acknowledgment timeout for " +
                            moveWaitingForKeyboardHookCallbackAcknowledgment +
                            " after " + ticksWaitingForAcknowledgment + " ticks");
                moveWaitingForKeyboardHookCallbackAcknowledgment = null;
            }
            else
                return;
        }
        if (processOneSendInputMove())
            return;
        if (pressedKeyToRepeat == null)
            return;
        durationUntilNextKeyPressRepeat -= delta;
        if (durationUntilNextKeyPressRepeat <= 0) {
            sendInputMoves(List.of(new ResolvedKeyMacroMove(pressedKeyToRepeat, true, MacroMoveDestination.OS)), true);
            durationUntilNextKeyPressRepeat = 0.025d;
        }
    }

    private static boolean processOneSendInputMove() {
        while (!sendInputQueue.isEmpty()) {
            SendInputMove sendInputMove = sendInputQueue.removeFirst();
            switch (sendInputMove.move()) {
                case ResolvedKeyMacroMove keyMove -> {
                    // Batch consecutive key moves into a single SendInput call. A single
                    // SendInput guarantees atomic, in-order insertion (no interleaving
                    // with user events). This also avoids nested hook callbacks: without
                    // batching, regurgitating +leftwin, +leftwin's ack handler would
                    // send -leftwin from inside +leftwin's hook callback, and Windows
                    // would not fully commit the press before seeing the release, leaving
                    // leftwin stuck.
                    List<ResolvedKeyMacroMove> batch = new ArrayList<>();
                    batch.add(keyMove);
                    // Batching leftalt does not work because we need to wait for the phantom events to be acknowledged.
                    while (!keyMove.key().equals(Key.leftalt) &&
                           !sendInputQueue.isEmpty() &&
                           sendInputQueue.getFirst().move() instanceof ResolvedKeyMacroMove next
//                           && next.key().equals(keyMove.key())
                    ) {
                        batch.add(next);
                        sendInputQueue.removeFirst();
                        if (next.key().equals(Key.leftalt))
                            break;
//                        break;
                    }
                    sendInputKeys(batch, sendInputMove.startRepeat());
                    return true;
                }
                case StringMacroMove stringMove -> {
                    // No acknowledgment needed for Unicode events.
                    sendInputString(stringMove.string());
                    // Continue processing the next queued move.
                }
            }
        }
        return false;
    }

    private static void sendInputKeys(List<ResolvedKeyMacroMove> moves, boolean triggerKeyRepeating) {
//        triggerKeyRepeating = false;
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
                    repeatStartedDuringCurrentTick = true;
                }
            }
            else if (pressedKeyToRepeat == move.key()) {
                pressedKeyToRepeat = null;
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
        if (logger.isTraceEnabled()) {
            logger.trace("Sending " + moves + ", triggerKeyRepeating = " + triggerKeyRepeating);
            logger.trace("Waiting for ackowledgment of " + moves.getLast());
        }
        moveWaitingForKeyboardHookCallbackAcknowledgment = moves.getLast();
        ticksWaitingForAcknowledgment = 0;
        WinDef.DWORD sendInput =
                User32.INSTANCE.SendInput(new WinDef.DWORD(moves.size()), pInputs,
                        pInputs[0].size());
    }

    public static void sendInputString(String string) {
        logger.trace("Sending input string: " + string);
        int inputCount = string.length() * 2; // down + up per character
        WinUser.INPUT[] pInputs =
                (WinUser.INPUT[]) new WinUser.INPUT().toArray(inputCount);
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            int downIndex = i * 2;
            int upIndex = downIndex + 1;
            // Key down.
            pInputs[downIndex].type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
            pInputs[downIndex].input.setType(WinUser.KEYBDINPUT.class);
            pInputs[downIndex].input.ki.wVk = new WinDef.WORD(0);
            pInputs[downIndex].input.ki.wScan = new WinDef.WORD(c);
            pInputs[downIndex].input.ki.dwFlags = new WinDef.DWORD(WinUser.KEYBDINPUT.KEYEVENTF_UNICODE);
            // Key up.
            pInputs[upIndex].type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
            pInputs[upIndex].input.setType(WinUser.KEYBDINPUT.class);
            pInputs[upIndex].input.ki.wVk = new WinDef.WORD(0);
            pInputs[upIndex].input.ki.wScan = new WinDef.WORD(c);
            pInputs[upIndex].input.ki.dwFlags = new WinDef.DWORD(
                    WinUser.KEYBDINPUT.KEYEVENTF_UNICODE |
                    WinUser.KEYBDINPUT.KEYEVENTF_KEYUP);
        }
        User32.INSTANCE.SendInput(new WinDef.DWORD(inputCount), pInputs,
                pInputs[0].size());
    }

}

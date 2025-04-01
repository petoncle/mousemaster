package mousemaster;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
     * For some reason, sending more than one input per SendInput call rarely work (?).
     */
    public static void sendInput(List<RemappingMove> moves, boolean startRepeat, boolean oneInputPerCall) {
        if (oneInputPerCall) {
            for (RemappingMove move : moves) {
                sendInput(List.of(move), startRepeat);
            }
        }
        else
            sendInput(moves, startRepeat);
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
        if (pressedKeys.add(key)
            // If user is holding key, no need to repeat is ourselves.
            || key.equals(pressedKeyToRepeat))
            pressedKeyToRepeat = null;
    }

    public static void update(double delta) {
        if (pressedKeyToRepeat == null)
            return;
        durationUntilNextKeyPressRepeat -= delta;
        if (durationUntilNextKeyPressRepeat <= 0) {
            sendInput(List.of(new RemappingMove(pressedKeyToRepeat, true)), true);
            durationUntilNextKeyPressRepeat = 0.025d;
        }
    }

    private static void sendInput(List<RemappingMove> moves, boolean triggerKeyRepeating) {
        // Send a press event for the key to regurgitate.
        WinUser.INPUT[] pInputs =
                (WinUser.INPUT[]) new WinUser.INPUT().toArray(moves.size());
        for (int moveIndex = 0; moveIndex < moves.size(); moveIndex++) {
            RemappingMove move = moves.get(moveIndex);
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
                    new WinDef.WORD(WindowsVirtualKey.windowsVirtualKeyFromKey(
                            move.key()).virtualKeyCode);
            int flag = (extendedKeys.contains(move.key()) ? 1 : 0) | (!move.press() ? 2 : 0);
            // If KEYEVENTF_EXTENDEDKEY dwFlag is not set,
            // rightalt + f7 in IntelliJ gets stuck: it expects alt to be released (press-and-release leftalt to unstuck).
            pInputs[moveIndex].input.ki.dwFlags = new WinDef.DWORD(flag);
        }
        WinDef.DWORD sendInput =
                User32.INSTANCE.SendInput(new WinDef.DWORD(moves.size()), pInputs,
                        pInputs[0].size() * moves.size());
    }

}

package mousemaster;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

import java.util.List;
import java.util.Set;

public class WindowsKeyboard {

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
            // Key.numlock,
            Key.break_,
            Key.printscreen,
            Key.numpaddivide, // No numpadmultiply?
            Key.enter // Only enter from numpad?
    );

    /**
     * For some reason, sending more than one input per SendInput call rarely work (?).
     */
    public static void sendInput(List<RemappingMove> moves, boolean oneInputPerCall) {
        if (oneInputPerCall) {
            for (RemappingMove move : moves) {
                sendInput(List.of(move));
            }
        }
        else
            sendInput(moves);
    }

    private static void sendInput(List<RemappingMove> moves) {
        // Send a press event for the key to regurgitate.
        WinUser.INPUT[] pInputs =
                (WinUser.INPUT[]) new WinUser.INPUT().toArray(moves.size());
        for (int moveIndex = 0; moveIndex < moves.size(); moveIndex++) {
            RemappingMove move = moves.get(moveIndex);
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

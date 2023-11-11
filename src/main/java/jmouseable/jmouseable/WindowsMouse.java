package jmouseable.jmouseable;

import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

public class WindowsMouse {

    public static void moveBy(boolean xForward, double deltaX, boolean yForward,
                              double deltaY) {
        if (((long) deltaX) == 0 && ((long) deltaY) == 0)
            return;
        WinUser.INPUT input = new WinUser.INPUT();
        input.type = new WinDef.DWORD(WinUser.INPUT.INPUT_MOUSE);
        WinUser.MOUSEINPUT mouseInput = new WinUser.MOUSEINPUT();
        input.input.mi = mouseInput;
        input.input.setType(WinUser.MOUSEINPUT.class);
        mouseInput.dx = new WinDef.LONG((long) deltaX * (xForward ? 1 : -1));
        mouseInput.dy = new WinDef.LONG((long) deltaY * (yForward ? 1 : -1));
        mouseInput.mouseData = new WinDef.DWORD(0);
        mouseInput.dwFlags = new WinDef.DWORD(ExtendedUser32.MOUSEEVENTF_MOVE);
        mouseInput.time = new WinDef.DWORD(0);
        mouseInput.dwExtraInfo = new BaseTSD.ULONG_PTR(0L);
        User32.INSTANCE.SendInput(new WinDef.DWORD(1), new WinUser.INPUT[]{input},
                input.size());
    }

}

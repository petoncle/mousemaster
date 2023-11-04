package jmouseable.jmouseable;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JmouseableApplication implements CommandLineRunner {

    private static final Logger logger =
            LoggerFactory.getLogger(JmouseableApplication.class);


    public static void main(String[] args) {
        SpringApplication.run(JmouseableApplication.class, args);
    }

    private WinUser.HHOOK keyboardHookHandle;

    @Override
    public void run(String... args) throws Exception {
        WinDef.HMODULE hMod = Kernel32.INSTANCE.GetModuleHandle(null);
        WinUser.LowLevelKeyboardProc keyboardHook = new WinUser.LowLevelKeyboardProc() {
            public WinDef.LRESULT callback(int nCode, WinDef.WPARAM wParam,
                                           WinUser.KBDLLHOOKSTRUCT info) {
                if (nCode >= 0) {
                    switch (wParam.intValue()) {
                        case WinUser.WM_KEYUP:
                        case WinUser.WM_KEYDOWN:
                        case WinUser.WM_SYSKEYUP:
                        case WinUser.WM_SYSKEYDOWN:
                            logger.info("In callback, key state: " + wParam + ", " +
                                        info.vkCode);
                            // If you want to stop the event from continuing you would do so here
                            break;
                    }
                }
                return User32.INSTANCE.CallNextHookEx(keyboardHookHandle, nCode, wParam,
                        new WinDef.LPARAM(Pointer.nativeValue(info.getPointer())));
            }
        };
        keyboardHookHandle =
                User32.INSTANCE.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL, keyboardHook,
                        hMod, 0);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Running shutdown hook");
            User32.INSTANCE.UnhookWindowsHookEx(keyboardHookHandle);
        }));
        logger.info("Keyboard hook installed");
        WinUser.MSG msg = new WinUser.MSG();
        while (User32.INSTANCE.GetMessage(msg, null, 0, 0) != 0) {
            User32.INSTANCE.TranslateMessage(msg);
            User32.INSTANCE.DispatchMessage(msg);
        }
    }
}

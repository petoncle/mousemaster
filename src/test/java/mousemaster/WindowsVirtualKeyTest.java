package mousemaster;

import mousemaster.platform.windows.WindowsVirtualKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindowsVirtualKeyTest {

    private static final KeyboardLayout usQwerty =
            KeyboardLayout.keyboardLayout("00000409", null);

    @Test
    void aNumpadKeyIsANumpadKeyWithNumlockOnOnly() {
        assertEquals(Key.numpad1,
                WindowsVirtualKey.keyFromWindowsEvent(WindowsVirtualKey.VK_NUMPAD1, 0x4F, 0,
                        usQwerty));
        assertEquals(Key.numpaddecimal,
                WindowsVirtualKey.keyFromWindowsEvent(WindowsVirtualKey.VK_DECIMAL, 0x53, 0,
                        usQwerty));
        assertEquals(Key.end,
                WindowsVirtualKey.keyFromWindowsEvent(WindowsVirtualKey.VK_END, 0x4F, 0,
                        usQwerty));
        assertEquals(Key.end,
                WindowsVirtualKey.keyFromWindowsEvent(WindowsVirtualKey.VK_END, 0x4F, 1,
                        usQwerty));
    }

    @Test
    void aNumpadKeyIsSentAsItsVirtualKey() {
        assertEquals(WindowsVirtualKey.VK_NUMPAD1,
                WindowsVirtualKey.windowsVirtualKeyFromKey(Key.numpad1, usQwerty));
        assertEquals(WindowsVirtualKey.VK_DECIMAL,
                WindowsVirtualKey.windowsVirtualKeyFromKey(Key.numpaddecimal, usQwerty));
    }

}

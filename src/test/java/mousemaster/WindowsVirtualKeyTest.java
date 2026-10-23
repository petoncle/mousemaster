package mousemaster;

import mousemaster.platform.windows.WindowsKeyboardController;
import mousemaster.platform.windows.WindowsVirtualKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void theNumpadEnterIsTheExtendedEnter() {
        assertEquals(Key.enter,
                WindowsVirtualKey.keyFromWindowsEvent(WindowsVirtualKey.VK_RETURN, 0x1C, 0,
                        usQwerty));
        assertEquals(Key.numpadenter,
                WindowsVirtualKey.keyFromWindowsEvent(WindowsVirtualKey.VK_RETURN, 0x1C, 1,
                        usQwerty));
        assertFalse(WindowsKeyboardController.extendedKeys.contains(Key.enter));
        assertTrue(WindowsKeyboardController.extendedKeys.contains(Key.numpadenter));
    }

}

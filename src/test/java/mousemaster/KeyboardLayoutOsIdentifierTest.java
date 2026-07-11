package mousemaster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeyboardLayoutOsIdentifierTest {

    @Test
    void exposesOsIdentifiersAsBoundaryData() {
        KeyboardLayout layout = new KeyboardLayout("test", "Test", "test", "test",
                List.of(new KeyboardLayout.KeyboardLayoutKey(30, WindowsVirtualKey.VK_A,
                                Key.ofCharacter("a"), "a", "A"),
                        new KeyboardLayout.KeyboardLayoutKey(42, WindowsVirtualKey.VK_LSHIFT,
                                Key.leftshift, null, "Left Shift")));

        assertEquals(Key.ofCharacter("a"), layout.keyFromScanCode(30));
        assertEquals(Key.ofCharacter("a"), layout.keyFromVirtualKey(WindowsVirtualKey.VK_A));
        assertEquals(30, layout.scanCode(Key.ofCharacter("a")));
        assertEquals(WindowsVirtualKey.VK_LSHIFT, layout.virtualKey(Key.leftshift));
    }

    @Test
    void bundledLayoutsStillExposeWindowsIdentifiersAfterJsonLoad() {
        KeyboardLayout usQwerty = KeyboardLayout.keyboardLayoutByShortName.get("us-qwerty");

        assertNotNull(usQwerty);
        assertEquals(Key.esc, usQwerty.keyFromVirtualKey(WindowsVirtualKey.VK_ESCAPE));
        assertEquals(WindowsVirtualKey.VK_SPACE, usQwerty.virtualKey(Key.space));
    }
}

package mousemaster;

import mousemaster.platform.macos.MacosHidUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MacosHidUsageTest {

    /** Scan codes and keys are repeated on purpose, and the first declaration is what wins. */
    @Test
    void everyUsageIsUnique() {
        Map<Integer, MacosHidUsage> first = new HashMap<>();
        List<String> repeated = new ArrayList<>();
        for (MacosHidUsage hidUsage : MacosHidUsage.values()) {
            MacosHidUsage other = first.put(hidUsage.usage, hidUsage);
            if (other != null)
                repeated.add(other + " and " + hidUsage + " share " + hidUsage.usage);
        }
        assertEquals(List.of(), repeated);
    }

    @Test
    void theKeypadEnterIsNotEnter() {
        KeyboardLayout usQwerty = KeyboardLayout.keyboardLayout("00000409", null);
        assertEquals(Key.enter,
                MacosHidUsage.keyFromHidEvent(MacosHidUsage.keyboardPage, 0x28, usQwerty));
        assertEquals(Key.numpadenter,
                MacosHidUsage.keyFromHidEvent(MacosHidUsage.keyboardPage, 0x58, usQwerty));
        assertEquals(MacosHidUsage.enter, MacosHidUsage.hidUsageFromKey(Key.enter, usQwerty));
        assertEquals(MacosHidUsage.numpadenter,
                MacosHidUsage.hidUsageFromKey(Key.numpadenter, usQwerty));
    }

}

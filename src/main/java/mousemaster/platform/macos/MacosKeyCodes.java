package mousemaster.platform.macos;

import mousemaster.Key;
import mousemaster.KeyboardLayout;

import java.util.Map;

/**
 * Physical-key mapping between macOS {@code CGKeyCode}s and the scan codes used
 * by bundled keyboard layouts.
 * <p>
 * A macOS keycode and a Windows scan code both identify a key position, not the
 * character produced by the active layout. That makes the mapping global: macOS
 * code can translate {@code macKeyCode -> scanCode -> Key} against the current
 * {@link KeyboardLayout}, while the layout file remains focused on Windows
 * layout metadata and mousemaster's logical {@link Key}s.
 * <p>
 * This table is intentionally empty until it can be generated reproducibly from
 * a pinned Chromium {@code dom_code_data.inc}. A partial hand-written table
 * would create hard-to-debug missing-key behavior.
 */
public final class MacosKeyCodes {

    private static final Map<Integer, Integer> scanCodeByMacKeyCode = Map.of();
    private static final Map<Integer, Integer> macKeyCodeByScanCode = Map.of();

    private MacosKeyCodes() {
    }

    public static Integer scanCode(int macKeyCode) {
        return scanCodeByMacKeyCode.get(macKeyCode);
    }

    public static Integer macKeyCode(int scanCode) {
        return macKeyCodeByScanCode.get(scanCode);
    }

    public static Key keyFromMacKeyCode(int macKeyCode, KeyboardLayout keyboardLayout) {
        Integer scanCode = scanCode(macKeyCode);
        return scanCode == null ? null : keyboardLayout.keyFromScanCode(scanCode);
    }

    public static Integer macKeyCode(Key key, KeyboardLayout keyboardLayout) {
        int scanCode = keyboardLayout.scanCode(key);
        return scanCode == -1 ? null : macKeyCode(scanCode);
    }

}

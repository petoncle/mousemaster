package mousemaster.platform.macos;

import mousemaster.KeyboardLayout;

public final class MacosKeyboardLayout {

    /**
     * Temporary scaffold layout. Production macOS support must build an
     * in-memory layout from the active input source via TIS/UCKeyTranslate and
     * use this only as an explicit fallback for unsupported input sources.
     */
    private static final String FALLBACK_LAYOUT_SHORT_NAME = "us-qwerty";

    private MacosKeyboardLayout() {
    }

    public static KeyboardLayout activeKeyboardLayout() {
        KeyboardLayout fallback =
                KeyboardLayout.keyboardLayoutByShortName.get(FALLBACK_LAYOUT_SHORT_NAME);
        if (fallback == null)
            throw new IllegalStateException("Bundled fallback keyboard layout not found: " +
                                            FALLBACK_LAYOUT_SHORT_NAME);
        return fallback;
    }
}

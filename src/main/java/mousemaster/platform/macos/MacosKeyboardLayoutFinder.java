package mousemaster.platform.macos;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import mousemaster.Key;
import mousemaster.KeyboardLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The layout is identified by what its keys type: nothing maps an input source id like
 * com.apple.keylayout.German onto the Windows identifiers the shipped layouts are keyed by.
 */
public final class MacosKeyboardLayoutFinder {

    private static final Logger logger =
            LoggerFactory.getLogger(MacosKeyboardLayoutFinder.class);

    private static final Carbon carbon = Carbon.INSTANCE;

    /** macOS virtual key codes against the PS/2 scan codes of the same physical keys. */
    private static final Map<Short, Integer> scanCodeByVirtualKeyCode = new LinkedHashMap<>();

    static {
        int[][] pairs = {
                {0x00, 0x1E}, {0x01, 0x1F}, {0x02, 0x20}, {0x03, 0x21}, {0x04, 0x23},
                {0x05, 0x22}, {0x06, 0x2C}, {0x07, 0x2D}, {0x08, 0x2E}, {0x09, 0x2F},
                {0x0A, 0x56}, {0x0B, 0x30}, {0x0C, 0x10}, {0x0D, 0x11}, {0x0E, 0x12},
                {0x0F, 0x13}, {0x10, 0x15}, {0x11, 0x14}, {0x12, 0x02}, {0x13, 0x03},
                {0x14, 0x04}, {0x15, 0x05}, {0x16, 0x07}, {0x17, 0x06}, {0x18, 0x0D},
                {0x19, 0x0A}, {0x1A, 0x08}, {0x1B, 0x0C}, {0x1C, 0x09}, {0x1D, 0x0B},
                {0x1E, 0x1B}, {0x1F, 0x18}, {0x20, 0x16}, {0x21, 0x1A}, {0x22, 0x17},
                {0x23, 0x19}, {0x25, 0x26}, {0x26, 0x24}, {0x27, 0x28}, {0x28, 0x25},
                {0x29, 0x27}, {0x2A, 0x2B}, {0x2B, 0x33}, {0x2C, 0x35}, {0x2D, 0x31},
                {0x2E, 0x32}, {0x2F, 0x34}, {0x32, 0x29}
        };
        for (int[] pair : pairs)
            scanCodeByVirtualKeyCode.put((short) pair[0], pair[1]);
    }

    private MacosKeyboardLayoutFinder() {
    }

    private static final long recheckNanos = 500_000_000L;

    // Created once: this runs twice a second, and a CFString is owned by whoever created it.
    private static final Pointer inputSourceIdKey =
            cfString("AppleCurrentKeyboardLayoutInputSourceID");
    private static final Pointer hiToolboxKey = cfString("com.apple.HIToolbox");
    private static String consoleUser;
    private static Pointer consoleUserKey;

    private static KeyboardLayout active;
    private static String activeSourceId;
    private static long lastCheckNanos;

    /** Mousemaster asks once per loop iteration, and matching scores every shipped layout. */
    public static KeyboardLayout active() {
        long now = System.nanoTime();
        if (active != null && now - lastCheckNanos < recheckNanos)
            return active;
        lastCheckNanos = now;
        String sourceId = selectedInputSourceId();
        if (active != null && Objects.equals(sourceId, activeSourceId))
            return active;
        activeSourceId = sourceId;
        active = match(sourceId);
        return active;
    }

    private static KeyboardLayout match(String sourceId) {
        Map<Integer, String> typedByScanCode = typedCharactersByScanCode(sourceId);
        if (typedByScanCode.isEmpty()) {
            logger.debug("Falling back to us-qwerty, no layout data for " + sourceId);
            return KeyboardLayout.keyboardLayoutByShortName.get("us-qwerty");
        }
        // us-qwerty and zh-qwerty-pinyin are the same keyboard, so the source id breaks the tie.
        KeyboardLayout best = null;
        int bestScore = -1;
        boolean bestNamed = false;
        for (KeyboardLayout layout : KeyboardLayout.keyboardLayoutByShortName.values()) {
            int score = score(layout, typedByScanCode);
            boolean named = namedBy(layout, sourceId);
            if (score > bestScore || (score == bestScore &&
                                      (named && !bestNamed ||
                                       named == bestNamed && best != null &&
                                       layout.shortName().compareTo(best.shortName()) < 0))) {
                bestScore = score;
                bestNamed = named;
                best = layout;
            }
        }
        logger.debug("Matched " + sourceId + " to the keyboard layout " + best.shortName() +
                     " on " + bestScore + " of " + typedByScanCode.size() + " keys");
        return best;
    }

    /** com.apple.keylayout.German against the German layout. */
    private static boolean namedBy(KeyboardLayout layout, String sourceId) {
        if (sourceId == null)
            return false;
        String name = sourceId.substring(sourceId.lastIndexOf('.') + 1).toLowerCase();
        return layout.displayName() != null &&
               layout.displayName().toLowerCase().startsWith(name) ||
               layout.shortName().startsWith(name + "-");
    }

    private static String consoleUser() {
        try {
            return Files.getOwner(Path.of("/dev/console")).getName();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Asking for the current input source would answer for root, not for the user. */
    private static String selectedInputSourceId() {
        CoreFoundation coreFoundation = CoreFoundation.INSTANCE;
        String user = consoleUser();
        if (!user.equals(consoleUser)) {
            consoleUser = user;
            if (consoleUserKey != null)
                coreFoundation.CFRelease(consoleUserKey);
            consoleUserKey = cfString(user);
        }
        Pointer value = coreFoundation.CFPreferencesCopyValue(inputSourceIdKey,
                hiToolboxKey, consoleUserKey, CoreFoundation.anyHost);
        if (value == null)
            return null;
        try {
            return javaString(value);
        }
        finally {
            coreFoundation.CFRelease(value);
        }
    }

    private static Pointer cfString(String string) {
        return CoreFoundation.INSTANCE.CFStringCreateWithCString(null, string,
                CoreFoundation.utf8Encoding);
    }

    private static String javaString(Pointer cfString) {
        byte[] buffer = new byte[512];
        CoreFoundation.INSTANCE.CFStringGetCString(cfString, buffer, buffer.length,
                CoreFoundation.utf8Encoding);
        return Native.toString(buffer);
    }

    private static int score(KeyboardLayout layout, Map<Integer, String> typedByScanCode) {
        int score = 0;
        for (Map.Entry<Integer, String> entry : typedByScanCode.entrySet()) {
            Key key = layout.keyFromScanCode(entry.getKey());
            if (key != null && entry.getValue().equals(key.character()))
                score++;
        }
        return score;
    }

    /** What the given layout types on each character key, unmodified. */
    private static Map<Integer, String> typedCharactersByScanCode(String sourceId) {
        Map<Integer, String> typedByScanCode = new LinkedHashMap<>();
        CoreFoundation coreFoundation = CoreFoundation.INSTANCE;
        // Released only once the layout data it lends out has been read.
        Pointer sources = carbon.TISCreateInputSourceList(null, false);
        try {
            Pointer layoutData = layoutDataOf(sources, sourceId);
            if (layoutData == null)
                return typedByScanCode;
            Pointer layout = coreFoundation.CFDataGetBytePtr(layoutData);
            int keyboardType = carbon.LMGetKbdType();
            for (Map.Entry<Short, Integer> entry : scanCodeByVirtualKeyCode.entrySet()) {
                int[] deadKeyState = new int[1];
                long[] length = new long[1];
                char[] characters = new char[8];
                int status = carbon.UCKeyTranslate(layout, entry.getKey(),
                        (short) Carbon.keyActionDown, 0, keyboardType,
                        Carbon.translateNoDeadKeys, deadKeyState, characters.length, length,
                        characters);
                if (status != 0 || length[0] < 1)
                    continue;
                typedByScanCode.put(entry.getValue(),
                        new String(characters, 0, (int) length[0]));
            }
            return typedByScanCode;
        }
        finally {
            coreFoundation.CFRelease(sources);
        }
    }

    /** An input method carries no layout data. */
    private static Pointer layoutDataOf(Pointer sources, String sourceId) {
        CoreFoundation coreFoundation = CoreFoundation.INSTANCE;
        for (long i = 0, count = coreFoundation.CFArrayGetCount(sources); i < count; i++) {
            Pointer source = coreFoundation.CFArrayGetValueAtIndex(sources, i);
            Pointer id = carbon.TISGetInputSourceProperty(source, Carbon.inputSourceId);
            if (id == null || !javaString(id).equals(sourceId))
                continue;
            return carbon.TISGetInputSourceProperty(source, Carbon.unicodeKeyLayoutData);
        }
        return null;
    }

}

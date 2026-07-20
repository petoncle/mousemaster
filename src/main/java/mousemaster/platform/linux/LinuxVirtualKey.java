package mousemaster.platform.linux;

import mousemaster.Key;

import java.util.HashMap;
import java.util.Map;

public class LinuxVirtualKey {

    private static final Map<String, Key> KEYSYM_TO_KEY = Map.ofEntries(
            // Modifiers
            Map.entry("Alt_L",        Key.leftalt),
            Map.entry("Alt_R",        Key.rightalt),
            Map.entry("Shift_L",      Key.leftshift),
            Map.entry("Shift_R",      Key.rightshift),
            Map.entry("Control_L",    Key.leftctrl),
            Map.entry("Control_R",    Key.rightctrl),
            Map.entry("Super_L",      Key.leftwin),
            Map.entry("Super_R",      Key.rightwin),
            // Navigation / editing
            Map.entry("Return",       Key.enter),
            Map.entry("Escape",       Key.esc),
            Map.entry("BackSpace",    Key.backspace),
            Map.entry("Tab",          Key.tab),
            Map.entry("space",        Key.space),
            Map.entry("Delete",       Key.del),
            Map.entry("Insert",       Key.insert),
            Map.entry("Home",         Key.home),
            Map.entry("End",          Key.end),
            Map.entry("Prior",        Key.pageup),
            Map.entry("Next",         Key.pagedown),
            Map.entry("Up",           Key.uparrow),
            Map.entry("Down",         Key.downarrow),
            Map.entry("Left",         Key.leftarrow),
            Map.entry("Right",        Key.rightarrow),
            // Locks / misc
            Map.entry("Caps_Lock",    Key.capslock),
            Map.entry("Num_Lock",     Key.numlock),
            Map.entry("Scroll_Lock",  Key.scrolllock),
            Map.entry("Pause",        Key.pause),
            Map.entry("Print",        Key.printscreen),
            Map.entry("Menu",         Key.menu),
            // Function keys
            Map.entry("F1",  Key.f1),
            Map.entry("F2",  Key.f2),
            Map.entry("F3",  Key.f3),
            Map.entry("F4",  Key.f4),
            Map.entry("F5",  Key.f5),
            Map.entry("F6",  Key.f6),
            Map.entry("F7",  Key.f7),
            Map.entry("F8",  Key.f8),
            Map.entry("F9",  Key.f9),
            Map.entry("F10", Key.f10),
            Map.entry("F11", Key.f11),
            Map.entry("F12", Key.f12),
            Map.entry("F13", Key.f13),
            Map.entry("F14", Key.f14),
            Map.entry("F15", Key.f15),
            Map.entry("F16", Key.f16),
            Map.entry("F17", Key.f17),
            Map.entry("F18", Key.f18),
            Map.entry("F19", Key.f19),
            Map.entry("F20", Key.f20),
            Map.entry("F21", Key.f21),
            Map.entry("F22", Key.f22),
            Map.entry("F23", Key.f23),
            Map.entry("F24", Key.f24),
            // Numpad
            Map.entry("KP_0",        Key.numpad0),
            Map.entry("KP_1",        Key.numpad1),
            Map.entry("KP_2",        Key.numpad2),
            Map.entry("KP_3",        Key.numpad3),
            Map.entry("KP_4",        Key.numpad4),
            Map.entry("KP_5",        Key.numpad5),
            Map.entry("KP_6",        Key.numpad6),
            Map.entry("KP_7",        Key.numpad7),
            Map.entry("KP_8",        Key.numpad8),
            Map.entry("KP_9",        Key.numpad9),
            Map.entry("KP_Multiply", Key.numpadmultiply),
            Map.entry("KP_Add",      Key.numpadadd),
            Map.entry("KP_Subtract", Key.numpadsubtract),
            Map.entry("KP_Decimal",  Key.numpaddecimal),
            Map.entry("KP_Divide",   Key.numpaddivide),
            Map.entry("KP_Enter",    Key.enter),
            // Punctuation with static Key constants
            Map.entry("plus",        Key.plus),
            Map.entry("minus",       Key.minus),
            Map.entry("underscore",  Key.underscore),
            Map.entry("bar",         Key.pipe),
            Map.entry("asciicircum", Key.caret),
            Map.entry("braceleft",   Key.leftcurlybrace),
            Map.entry("braceright",  Key.rightcurlybrace),
            Map.entry("backslash",   Key.backslash),
            Map.entry("numbersign",  Key.hash)
    );

    // Evdev keycode → Key (assumes QWERTY physical layout for character keys)
    private static final Map<Integer, Key> EVDEV_TO_KEY = new HashMap<>();
    // Key → evdev keycode (reverse of EVDEV_TO_KEY; built after EVDEV_TO_KEY is populated)
    private static final Map<Key, Integer> KEY_TO_EVDEV = new HashMap<>();

    static {
        // Special / static keys
        EVDEV_TO_KEY.put(1,   Key.esc);
        EVDEV_TO_KEY.put(14,  Key.backspace);
        EVDEV_TO_KEY.put(15,  Key.tab);
        EVDEV_TO_KEY.put(28,  Key.enter);
        EVDEV_TO_KEY.put(29,  Key.leftctrl);
        EVDEV_TO_KEY.put(42,  Key.leftshift);
        EVDEV_TO_KEY.put(54,  Key.rightshift);
        EVDEV_TO_KEY.put(56,  Key.leftalt);
        EVDEV_TO_KEY.put(57,  Key.space);
        EVDEV_TO_KEY.put(58,  Key.capslock);
        EVDEV_TO_KEY.put(59,  Key.f1);
        EVDEV_TO_KEY.put(60,  Key.f2);
        EVDEV_TO_KEY.put(61,  Key.f3);
        EVDEV_TO_KEY.put(62,  Key.f4);
        EVDEV_TO_KEY.put(63,  Key.f5);
        EVDEV_TO_KEY.put(64,  Key.f6);
        EVDEV_TO_KEY.put(65,  Key.f7);
        EVDEV_TO_KEY.put(66,  Key.f8);
        EVDEV_TO_KEY.put(67,  Key.f9);
        EVDEV_TO_KEY.put(68,  Key.f10);
        EVDEV_TO_KEY.put(69,  Key.numlock);
        EVDEV_TO_KEY.put(70,  Key.scrolllock);
        EVDEV_TO_KEY.put(71,  Key.numpad7);
        EVDEV_TO_KEY.put(72,  Key.numpad8);
        EVDEV_TO_KEY.put(73,  Key.numpad9);
        EVDEV_TO_KEY.put(74,  Key.numpadsubtract);
        EVDEV_TO_KEY.put(75,  Key.numpad4);
        EVDEV_TO_KEY.put(76,  Key.numpad5);
        EVDEV_TO_KEY.put(77,  Key.numpad6);
        EVDEV_TO_KEY.put(78,  Key.numpadadd);
        EVDEV_TO_KEY.put(79,  Key.numpad1);
        EVDEV_TO_KEY.put(80,  Key.numpad2);
        EVDEV_TO_KEY.put(81,  Key.numpad3);
        EVDEV_TO_KEY.put(82,  Key.numpad0);
        EVDEV_TO_KEY.put(83,  Key.numpaddecimal);
        EVDEV_TO_KEY.put(87,  Key.f11);
        EVDEV_TO_KEY.put(88,  Key.f12);
        EVDEV_TO_KEY.put(96,  Key.enter);      // KP_ENTER
        EVDEV_TO_KEY.put(97,  Key.rightctrl);
        EVDEV_TO_KEY.put(98,  Key.numpaddivide);
        EVDEV_TO_KEY.put(99,  Key.printscreen);
        EVDEV_TO_KEY.put(100, Key.rightalt);
        EVDEV_TO_KEY.put(102, Key.home);
        EVDEV_TO_KEY.put(103, Key.uparrow);
        EVDEV_TO_KEY.put(104, Key.pageup);
        EVDEV_TO_KEY.put(105, Key.leftarrow);
        EVDEV_TO_KEY.put(106, Key.rightarrow);
        EVDEV_TO_KEY.put(107, Key.end);
        EVDEV_TO_KEY.put(108, Key.downarrow);
        EVDEV_TO_KEY.put(109, Key.pagedown);
        EVDEV_TO_KEY.put(110, Key.insert);
        EVDEV_TO_KEY.put(111, Key.del);
        EVDEV_TO_KEY.put(119, Key.pause);
        EVDEV_TO_KEY.put(125, Key.leftwin);
        EVDEV_TO_KEY.put(126, Key.rightwin);
        EVDEV_TO_KEY.put(127, Key.menu);
        EVDEV_TO_KEY.put(183, Key.f13);
        EVDEV_TO_KEY.put(184, Key.f14);
        EVDEV_TO_KEY.put(185, Key.f15);
        EVDEV_TO_KEY.put(186, Key.f16);
        EVDEV_TO_KEY.put(187, Key.f17);
        EVDEV_TO_KEY.put(188, Key.f18);
        EVDEV_TO_KEY.put(189, Key.f19);
        EVDEV_TO_KEY.put(190, Key.f20);
        EVDEV_TO_KEY.put(191, Key.f21);
        EVDEV_TO_KEY.put(192, Key.f22);
        EVDEV_TO_KEY.put(193, Key.f23);
        EVDEV_TO_KEY.put(194, Key.f24);
        EVDEV_TO_KEY.put(55,  Key.numpadmultiply);

        // Character keys — physical QWERTY positions mapped to unshifted characters
        int[] digitCodes = {11, 2, 3, 4, 5, 6, 7, 8, 9, 10}; // 0-9
        for (int i = 0; i < digitCodes.length; i++)
            EVDEV_TO_KEY.put(digitCodes[i], Key.ofCharacter(String.valueOf((char)('0' + i))));

        String qwerty = "qwertyuiopasdfghjklzxcvbnm";
        int[] letterCodes = {
            16,17,18,19,20,21,22,23,24,25, // q w e r t y u i o p
            30,31,32,33,34,35,36,37,38,    // a s d f g h j k l
            44,45,46,47,48,49,50           // z x c v b n m
        };
        for (int i = 0; i < letterCodes.length; i++)
            EVDEV_TO_KEY.put(letterCodes[i], Key.ofCharacter(String.valueOf(qwerty.charAt(i))));

        // Punctuation
        EVDEV_TO_KEY.put(12, Key.minus);
        EVDEV_TO_KEY.put(13, Key.ofCharacter("="));
        EVDEV_TO_KEY.put(26, Key.ofCharacter("["));
        EVDEV_TO_KEY.put(27, Key.ofCharacter("]"));
        EVDEV_TO_KEY.put(39, Key.ofCharacter(";"));
        EVDEV_TO_KEY.put(40, Key.ofCharacter("'"));
        EVDEV_TO_KEY.put(41, Key.ofCharacter("`"));
        EVDEV_TO_KEY.put(43, Key.backslash);
        EVDEV_TO_KEY.put(51, Key.ofCharacter(","));
        EVDEV_TO_KEY.put(52, Key.ofCharacter("."));
        EVDEV_TO_KEY.put(53, Key.ofCharacter("/"));

        // Build reverse map; lower code wins for keys that share a mapping (e.g. enter=28, kp_enter=96)
        EVDEV_TO_KEY.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> KEY_TO_EVDEV.putIfAbsent(e.getValue(), e.getKey()));
    }

    /**
     * Maps a Linux evdev keycode (EV_KEY code) to a Key.
     * Character keys assume a QWERTY physical layout.
     */
    public static Key fromEvdevCode(int code) {
        return EVDEV_TO_KEY.get(code);
    }

    /**
     * Maps a Key back to its canonical evdev keycode for uinput injection.
     * Returns null if no evdev code is known for the key.
     */
    public static Integer toEvdevCode(Key key) {
        return KEY_TO_EVDEV.get(key);
    }

    /**
     * Maps an X11 keysym name (from XKeysymToString) to a Key.
     * Single-character keysym names (e.g. "a", "b", "1") are resolved via Key.ofCharacter.
     * Returns null if the keysym has no mapping.
     */
    public static Key fromKeysym(String keysym) {
        if (keysym == null) {
            return null;
        }
        Key mapped = KEYSYM_TO_KEY.get(keysym);
        if (mapped != null) {
            return mapped;
        }
        if (keysym.length() == 1) {
            return Key.ofCharacter(keysym);
        }
        return null;
    }

}

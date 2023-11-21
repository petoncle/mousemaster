package jmouseable.jmouseable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum WindowsVirtualKey {

    VK_LBUTTON(0x01),
    VK_MOUSE1(0x01),
    VK_MB1(0x01),
    VK_RBUTTON(0x02),
    VK_MOUSE2(0x02),
    VK_MB2(0x02),
    VK_CANCEL(0x03),
    VK_MBUTTON(0x04),
    VK_MOUSE3(0x04),
    VK_MB3(0x04),
    VK_XBUTTON1(0x05),
    VK_MOUSE4(0x05),
    VK_MB4(0x05),
    VK_XBUTTON2(0x06),
    VK_MOUSE5(0x06),
    VK_MB5(0x06),
    VK_RESERVED_07(0x07),
    VK_BACK(0x08),
    VK_BACKSPACE(0x08),
    VK_TAB(0x09),
    VK_RESERVED_0A(0x0A),
    VK_RESERVED_0B(0x0B),
    VK_CLEAR(0x0C),
    VK_RETURN(0x0D),
    VK_ENTER(0x0D),
    VK_RESERVED_0E(0x0E),
    VK_RESERVED_0F(0x0F),
    VK_SHIFT(0x10),
    VK_CONTROL(0x11),
    VK_CTRL(0x11),
    VK_MENU(0x12),
    VK_ALT(0x12),
    VK_PAUSE(0x13),
    VK_CAPITAL(0x14),
    VK_CAPSLK(0x14),
    VK_CAPSLOCK(0x14),
    VK_CAPS(0x14),
    VK_HANGEUL(0x15),
    VK_HANGUL(0x15),
    VK_KANA(0x15),
    VK_IME_ON(0x16),
    VK_JUNJA(0x17),
    VK_FINAL(0x18),
    VK_HANJA(0x19),
    VK_KANJI(0x19),
    VK_IME_OFF(0x1A),
    VK_ESCAPE(0x1B),
    VK_ESC(0x1B),
    VK_CONVERT(0x1C),
    VK_NONCONVERT(0x1D),
    VK_ACCEPT(0x1E),
    VK_MODECHANGE(0x1F),
    VK_SPACE(0x20),
    VK_PRIOR(0x21),
    VK_PGUP(0x21),
    VK_PAGEUP(0x21),
    VK_NEXT(0x22),
    VK_PGDN(0x22),
    VK_PAGEDOWN(0x22),
    VK_END(0x23),
    VK_HOME(0x24),
    VK_LEFT(0x25),
    VK_UP(0x26),
    VK_RIGHT(0x27),
    VK_DOWN(0x28),
    VK_SELECT(0x29),
    VK_PRINT(0x2A),
    VK_EXECUTE(0x2B),
    VK_SNAPSHOT(0x2C),
    VK_PRTSCN(0x2C),
    VK_PRINTSCREEN(0x2C),
    VK_INSERT(0x2D),
    VK_INS(0x2D),
    VK_DELETE(0x2E),
    VK_DEL(0x2E),
    VK_HELP(0x2F),
    VK_0(0x30),
    VK_1(0x31),
    VK_2(0x32),
    VK_3(0x33),
    VK_4(0x34),
    VK_5(0x35),
    VK_6(0x36),
    VK_7(0x37),
    VK_8(0x38),
    VK_9(0x39),
    VK_RESERVED_3A(0x3A),
    VK_RESERVED_3B(0x3B),
    VK_RESERVED_3C(0x3C),
    VK_RESERVED_3D(0x3D),
    VK_RESERVED_3E(0x3E),
    VK_RESERVED_3F(0x3F),
    VK_RESERVED_40(0x40),
    VK_A(0x41),
    VK_B(0x42),
    VK_C(0x43),
    VK_D(0x44),
    VK_E(0x45),
    VK_F(0x46),
    VK_G(0x47),
    VK_H(0x48),
    VK_I(0x49),
    VK_J(0x4A),
    VK_K(0x4B),
    VK_L(0x4C),
    VK_M(0x4D),
    VK_N(0x4E),
    VK_O(0x4F),
    VK_P(0x50),
    VK_Q(0x51),
    VK_R(0x52),
    VK_S(0x53),
    VK_T(0x54),
    VK_U(0x55),
    VK_V(0x56),
    VK_W(0x57),
    VK_X(0x58),
    VK_Y(0x59),
    VK_Z(0x5A),
    VK_LWIN(0x5B),
    VK_RWIN(0x5C),
    VK_APPS(0x5D),
    VK_CONTEXT(0x5D),
    VK_CONTEXTMENU(0x5D),
    VK_RESERVED_5E(0x5E),
    VK_SLEEP(0x5F),
    VK_NUMPAD0(0x60),
    VK_NUM0(0x60),
    VK_NUMPAD1(0x61),
    VK_NUM1(0x61),
    VK_NUMPAD2(0x62),
    VK_NUM2(0x62),
    VK_NUMPAD3(0x63),
    VK_NUM3(0x63),
    VK_NUMPAD4(0x64),
    VK_NUM4(0x64),
    VK_NUMPAD5(0x65),
    VK_NUM5(0x65),
    VK_NUMPAD6(0x66),
    VK_NUM6(0x66),
    VK_NUMPAD7(0x67),
    VK_NUM7(0x67),
    VK_NUMPAD8(0x68),
    VK_NUM8(0x68),
    VK_NUMPAD9(0x69),
    VK_NUM9(0x69),
    VK_MULTIPLY(0x6A),
    VK_ADD(0x6B),
    VK_SEPARATOR(0x6C),
    VK_SUBTRACT(0x6D),
    VK_DECIMAL(0x6E),
    VK_DIVIDE(0x6F),
    VK_F1(0x70),
    VK_F2(0x71),
    VK_F3(0x72),
    VK_F4(0x73),
    VK_F5(0x74),
    VK_F6(0x75),
    VK_F7(0x76),
    VK_F8(0x77),
    VK_F9(0x78),
    VK_F10(0x79),
    VK_F11(0x7A),
    VK_F12(0x7B),
    VK_F13(0x7C),
    VK_F14(0x7D),
    VK_F15(0x7E),
    VK_F16(0x7F),
    VK_F17(0x80),
    VK_F18(0x81),
    VK_F19(0x82),
    VK_F20(0x83),
    VK_F21(0x84),
    VK_F22(0x85),
    VK_F23(0x86),
    VK_F24(0x87),
    VK_NAVIGATION_VIEW(136),
    VK_NAVIGATION_MENU(137),
    VK_NAVIGATION_UP(138),
    VK_NAVIGATION_DOWN(139),
    VK_NAVIGATION_LEFT(140),
    VK_NAVIGATION_RIGHT(141),
    VK_NAVIGATION_ACCEPT(142),
    VK_NAVIGATION_CANCEL(143),
    VK_NUMLOCK(0x90),
    VK_NUMLK(0x90),
    VK_SCROLL(0x91),
    VK_SCRLK(0x91),
    VK_OEM_FJ_JISHO(146),
    VK_OEM_NEC_EQUAL(146),
    VK_OEM_FJ_MASSHOU(147),
    VK_OEM_FJ_TOUROKU(148),
    VK_OEM_FJ_LOYA(149),
    VK_OEM_FJ_ROYA(150),
    VK_RESERVED_97(0x97),
    VK_RESERVED_98(0x98),
    VK_RESERVED_99(0x99),
    VK_RESERVED_9A(0x9A),
    VK_RESERVED_9B(0x9B),
    VK_RESERVED_9C(0x9C),
    VK_RESERVED_9D(0x9D),
    VK_RESERVED_9E(0x9E),
    VK_RESERVED_9F(0x9F),
    VK_LSHIFT(0xA0),
    VK_RSHIFT(0xA1),
    VK_LCONTROL(0xA2),
    VK_LCTRL(0xA2),
    VK_RCONTROL(0xA3),
    VK_RCTRL(0xA3),
    VK_LMENU(0xA4),
    VK_LALT(0xA4),
    VK_RMENU(0xA5),
    VK_RALT(0xA5),
    VK_BROWSER_BACK(0xA6),
    VK_BROWSER_FORWARD(0xA7),
    VK_BROWSER_REFRESH(0xA8),
    VK_BROWSER_STOP(0xA9),
    VK_BROWSER_SEARCH(0xAA),
    VK_BROWSER_FAVORITES(0xAB),
    VK_BROWSER_HOME(0xAC),
    VK_VOLUME_MUTE(0xAD),
    VK_VOLUME_DOWN(0xAE),
    VK_VOLUME_UP(0xAF),
    VK_MEDIA_NEXT_TRACK(0xB0),
    VK_MEDIA_PREV_TRACK(0xB1),
    VK_MEDIA_STOP(0xB2),
    VK_MEDIA_PLAY_PAUSE(0xB3),
    VK_LAUNCH_MAIL(0xB4),
    VK_LAUNCH_MEDIA_SELECT(0xB5),
    VK_LAUNCH_APP1(0xB6),
    VK_LAUNCH_APP2(0xB7),
    VK_RESERVED_B8(0xB8),
    VK_RESERVED_B9(0xB9),
    VK_OEM_1(0xBA),
    VK_OEM_PLUS(0xBB),
    VK_OEM_COMMA(0xBC),
    VK_OEM_MINUS(0xBD),
    VK_OEM_PERIOD(0xBE),
    VK_OEM_2(0xBF),
    VK_OEM_3(0xC0),
    VK_RESERVED_C1(0xC1),
    VK_RESERVED_C2(0xC2),
    VK_GAMEPAD_A(195),
    VK_GAMEPAD_B(196),
    VK_GAMEPAD_X(197),
    VK_GAMEPAD_Y(198),
    VK_GAMEPAD_RIGHT_SHOULDER(199),
    VK_GAMEPAD_LEFT_SHOULDER(200),
    VK_GAMEPAD_LEFT_TRIGGER(201),
    VK_GAMEPAD_RIGHT_TRIGGER(202),
    VK_GAMEPAD_DPAD_UP(203),
    VK_GAMEPAD_DPAD_DOWN(204),
    VK_GAMEPAD_DPAD_LEFT(205),
    VK_GAMEPAD_DPAD_RIGHT(206),
    VK_GAMEPAD_MENU(207),
    VK_GAMEPAD_VIEW(208),
    VK_GAMEPAD_LEFT_THUMBSTICK_BUTTON(209),
    VK_GAMEPAD_RIGHT_THUMBSTICK_BUTTON(210),
    VK_GAMEPAD_LEFT_THUMBSTICK_UP(211),
    VK_GAMEPAD_LEFT_THUMBSTICK_DOWN(212),
    VK_GAMEPAD_LEFT_THUMBSTICK_RIGHT(213),
    VK_GAMEPAD_LEFT_THUMBSTICK_LEFT(214),
    VK_GAMEPAD_RIGHT_THUMBSTICK_UP(215),
    VK_GAMEPAD_RIGHT_THUMBSTICK_DOWN(216),
    VK_GAMEPAD_RIGHT_THUMBSTICK_RIGHT(217),
    VK_GAMEPAD_RIGHT_THUMBSTICK_LEFT(218),
    VK_OEM_4(0xDB),
    VK_OEM_5(0xDC),
    VK_OEM_6(0xDD),
    VK_OEM_7(0xDE),
    VK_OEM_8(0xDF),
    VK_RESERVED_E0(0xE0),
    VK_OEM_AX(225),
    VK_OEM_102(0xE2),
    VK_ICO_HELP(227),
    VK_ICO_00(228),
    VK_PROCESSKEY(0xE5),
    VK_ICO_CLEAR(230),
    VK_PACKET(0xE7),
    VK_RESERVED_E8(0xE8),
    VK_OEM_RESET(233),
    VK_OEM_JUMP(234),
    VK_OEM_PA1(235),
    VK_OEM_PA2(236),
    VK_OEM_PA3(237),
    VK_OEM_WSCTRL(238),
    VK_OEM_CUSEL(239),
    VK_OEM_ATTN(240),
    VK_OEM_FINISH(241),
    VK_OEM_COPY(242),
    VK_OEM_AUTO(243),
    VK_OEM_ENLW(244),
    VK_OEM_BACKTAB(245),
    VK_ATTN(0xF6),
    VK_CRSEL(0xF7),
    VK_EXSEL(0xF8),
    VK_EREOF(0xF9),
    VK_PLAY(0xFA),
    VK_ZOOM(0xFB),
    VK_NONAME(0xFC),
    VK_PA1(0xFD),
    VK_OEM_CLEAR(0xFE),
    VK_NONE(0xFF),
    ;

    public final int virtualKeyCode;

    WindowsVirtualKey(int virtualKeyCode) {
        this.virtualKeyCode = virtualKeyCode;
    }

    public static final List<WindowsVirtualKey> values;

    static {
        WindowsVirtualKey[] valueArrayWithDuplicateCodes = values();
        Map<Integer, WindowsVirtualKey> valueMap = new HashMap<>();
        for (WindowsVirtualKey windowsVirtualKey : valueArrayWithDuplicateCodes)
            valueMap.putIfAbsent(windowsVirtualKey.virtualKeyCode, windowsVirtualKey);
        WindowsVirtualKey[] valueArrayWithoutDuplicateCodes = new WindowsVirtualKey[256];
        for (Map.Entry<Integer, WindowsVirtualKey> entry : valueMap.entrySet())
            valueArrayWithoutDuplicateCodes[entry.getKey()] = entry.getValue();
        values = Arrays.stream(valueArrayWithoutDuplicateCodes).toList();
    }

    /**
     * There are keyboard layout specific mappings here (e.g. VK_OEM_PLUS -> Key.equal, VK_OEM_7 -> Key.pound).
     */
    public static Key keyFromWindowsEvent(WindowsVirtualKey windowsVirtualKey, int scanCode, int flags) {
        return switch (windowsVirtualKey) {
            case VK_LBUTTON -> null;
            case VK_MOUSE1 -> null;
            case VK_MB1 -> null;
            case VK_RBUTTON -> null;
            case VK_MOUSE2 -> null;
            case VK_MB2 -> null;
            case VK_CANCEL -> null;
            case VK_MBUTTON -> null;
            case VK_MOUSE3 -> null;
            case VK_MB3 -> null;
            case VK_XBUTTON1 -> null;
            case VK_MOUSE4 -> null;
            case VK_MB4 -> null;
            case VK_XBUTTON2 -> null;
            case VK_MOUSE5 -> null;
            case VK_MB5 -> null;
            case VK_RESERVED_07 -> null;
            case VK_BACK -> Key.backspace;
            case VK_BACKSPACE -> Key.backspace;
            case VK_TAB -> Key.tab;
            case VK_RESERVED_0A -> null;
            case VK_RESERVED_0B -> null;
            case VK_CLEAR -> null;
            case VK_RETURN -> Key.enter;
            case VK_ENTER -> Key.enter;
            case VK_RESERVED_0E -> null;
            case VK_RESERVED_0F -> null;
            case VK_SHIFT -> (flags & 0x01000000) == 0 ? Key.leftshift : Key.rightshift;
            case VK_CONTROL -> (flags & 0x01000000) == 0 ? Key.leftctrl : Key.rightctrl;
            case VK_CTRL -> (flags & 0x01000000) == 0 ? Key.leftctrl : Key.rightctrl;
            case VK_MENU -> (flags & 0x01000000) == 0 ? Key.leftalt : Key.rightalt;
            case VK_ALT -> (flags & 0x01000000) == 0 ? Key.leftalt : Key.rightalt;
            case VK_PAUSE -> Key.pause;
            case VK_CAPITAL -> Key.capslock;
            case VK_CAPSLK -> Key.capslock;
            case VK_CAPSLOCK -> Key.capslock;
            case VK_CAPS -> Key.capslock;
            case VK_HANGEUL -> null;
            case VK_HANGUL -> null;
            case VK_KANA -> null;
            case VK_IME_ON -> null;
            case VK_JUNJA -> null;
            case VK_FINAL -> null;
            case VK_HANJA -> null;
            case VK_KANJI -> null;
            case VK_IME_OFF -> null;
            case VK_ESCAPE -> Key.esc;
            case VK_ESC -> Key.esc;
            case VK_CONVERT -> null;
            case VK_NONCONVERT -> null;
            case VK_ACCEPT -> null;
            case VK_MODECHANGE -> null;
            case VK_SPACE -> Key.space;
            case VK_PRIOR -> null;
            case VK_PGUP -> Key.pageup;
            case VK_PAGEUP -> Key.pageup;
            case VK_NEXT -> null;
            case VK_PGDN -> Key.pagedown;
            case VK_PAGEDOWN -> Key.pagedown;
            case VK_END -> Key.end;
            case VK_HOME -> Key.home;
            case VK_LEFT -> Key.left;
            case VK_UP -> Key.up;
            case VK_RIGHT -> Key.right;
            case VK_DOWN -> Key.down;
            case VK_SELECT -> null;
            case VK_PRINT -> null;
            case VK_EXECUTE -> null;
            case VK_SNAPSHOT -> null;
            case VK_PRTSCN -> Key.printscreen;
            case VK_PRINTSCREEN -> Key.printscreen;
            case VK_INSERT -> Key.insert;
            case VK_INS -> Key.insert;
            case VK_DELETE -> Key.del;
            case VK_DEL -> Key.del;
            case VK_HELP -> null;
            case VK_0 -> Key._0;
            case VK_1 -> Key._1;
            case VK_2 -> Key._2;
            case VK_3 -> Key._3;
            case VK_4 -> Key._4;
            case VK_5 -> Key._5;
            case VK_6 -> Key._6;
            case VK_7 -> Key._7;
            case VK_8 -> Key._8;
            case VK_9 -> Key._9;
            case VK_RESERVED_3A -> null;
            case VK_RESERVED_3B -> null;
            case VK_RESERVED_3C -> null;
            case VK_RESERVED_3D -> null;
            case VK_RESERVED_3E -> null;
            case VK_RESERVED_3F -> null;
            case VK_RESERVED_40 -> null;
            case VK_A -> Key.a;
            case VK_B -> Key.b;
            case VK_C -> Key.c;
            case VK_D -> Key.d;
            case VK_E -> Key.e;
            case VK_F -> Key.f;
            case VK_G -> Key.g;
            case VK_H -> Key.h;
            case VK_I -> Key.i;
            case VK_J -> Key.j;
            case VK_K -> Key.k;
            case VK_L -> Key.l;
            case VK_M -> Key.m;
            case VK_N -> Key.n;
            case VK_O -> Key.o;
            case VK_P -> Key.p;
            case VK_Q -> Key.q;
            case VK_R -> Key.r;
            case VK_S -> Key.s;
            case VK_T -> Key.t;
            case VK_U -> Key.u;
            case VK_V -> Key.v;
            case VK_W -> Key.w;
            case VK_X -> Key.x;
            case VK_Y -> Key.y;
            case VK_Z -> Key.z;
            case VK_LWIN -> Key.win;
            case VK_RWIN -> Key.win;
            case VK_APPS -> null;
            case VK_CONTEXT -> null;
            case VK_CONTEXTMENU -> null;
            case VK_RESERVED_5E -> null;
            case VK_SLEEP -> null;
            case VK_NUMPAD0 -> Key.numpad0;
            case VK_NUM0 -> Key.numpad0;
            case VK_NUMPAD1 -> Key.numpad1;
            case VK_NUM1 -> Key.numpad1;
            case VK_NUMPAD2 -> Key.numpad2;
            case VK_NUM2 -> Key.numpad2;
            case VK_NUMPAD3 -> Key.numpad3;
            case VK_NUM3 -> Key.numpad3;
            case VK_NUMPAD4 -> Key.numpad4;
            case VK_NUM4 -> Key.numpad4;
            case VK_NUMPAD5 -> Key.numpad5;
            case VK_NUM5 -> Key.numpad5;
            case VK_NUMPAD6 -> Key.numpad6;
            case VK_NUM6 -> Key.numpad6;
            case VK_NUMPAD7 -> Key.numpad7;
            case VK_NUM7 -> Key.numpad7;
            case VK_NUMPAD8 -> Key.numpad8;
            case VK_NUM8 -> Key.numpad8;
            case VK_NUMPAD9 -> Key.numpad9;
            case VK_NUM9 -> Key.numpad9;
            case VK_MULTIPLY -> null;
            case VK_ADD -> null;
            case VK_SEPARATOR -> null;
            case VK_SUBTRACT -> null;
            case VK_DECIMAL -> null;
            case VK_DIVIDE -> null;
            case VK_F1 -> Key.f1;
            case VK_F2 -> Key.f2;
            case VK_F3 -> Key.f3;
            case VK_F4 -> Key.f4;
            case VK_F5 -> Key.f5;
            case VK_F6 -> Key.f6;
            case VK_F7 -> Key.f7;
            case VK_F8 -> Key.f8;
            case VK_F9 -> Key.f9;
            case VK_F10 -> Key.f10;
            case VK_F11 -> Key.f11;
            case VK_F12 -> Key.f12;
            case VK_F13 -> null;
            case VK_F14 -> null;
            case VK_F15 -> null;
            case VK_F16 -> null;
            case VK_F17 -> null;
            case VK_F18 -> null;
            case VK_F19 -> null;
            case VK_F20 -> null;
            case VK_F21 -> null;
            case VK_F22 -> null;
            case VK_F23 -> null;
            case VK_F24 -> null;
            case VK_NAVIGATION_VIEW -> null;
            case VK_NAVIGATION_MENU -> null;
            case VK_NAVIGATION_UP -> null;
            case VK_NAVIGATION_DOWN -> null;
            case VK_NAVIGATION_LEFT -> null;
            case VK_NAVIGATION_RIGHT -> null;
            case VK_NAVIGATION_ACCEPT -> null;
            case VK_NAVIGATION_CANCEL -> null;
            case VK_NUMLOCK -> null;
            case VK_NUMLK -> null;
            case VK_SCROLL -> null;
            case VK_SCRLK -> null;
            case VK_OEM_FJ_JISHO -> null;
            case VK_OEM_NEC_EQUAL -> null;
            case VK_OEM_FJ_MASSHOU -> null;
            case VK_OEM_FJ_TOUROKU -> null;
            case VK_OEM_FJ_LOYA -> null;
            case VK_OEM_FJ_ROYA -> null;
            case VK_RESERVED_97 -> null;
            case VK_RESERVED_98 -> null;
            case VK_RESERVED_99 -> null;
            case VK_RESERVED_9A -> null;
            case VK_RESERVED_9B -> null;
            case VK_RESERVED_9C -> null;
            case VK_RESERVED_9D -> null;
            case VK_RESERVED_9E -> null;
            case VK_RESERVED_9F -> null;
            case VK_LSHIFT -> Key.leftshift;
            case VK_RSHIFT -> Key.rightshift;
            case VK_LCONTROL -> Key.leftctrl;
            case VK_LCTRL -> Key.leftctrl;
            case VK_RCONTROL -> Key.rightctrl;
            case VK_RCTRL -> Key.rightctrl;
            case VK_LMENU -> Key.leftalt;
            case VK_LALT -> Key.leftalt;
            case VK_RMENU -> Key.rightalt;
            case VK_RALT -> Key.rightalt;
            case VK_BROWSER_BACK -> null;
            case VK_BROWSER_FORWARD -> null;
            case VK_BROWSER_REFRESH -> null;
            case VK_BROWSER_STOP -> null;
            case VK_BROWSER_SEARCH -> null;
            case VK_BROWSER_FAVORITES -> null;
            case VK_BROWSER_HOME -> null;
            case VK_VOLUME_MUTE -> null;
            case VK_VOLUME_DOWN -> null;
            case VK_VOLUME_UP -> null;
            case VK_MEDIA_NEXT_TRACK -> null;
            case VK_MEDIA_PREV_TRACK -> null;
            case VK_MEDIA_STOP -> null;
            case VK_MEDIA_PLAY_PAUSE -> null;
            case VK_LAUNCH_MAIL -> null;
            case VK_LAUNCH_MEDIA_SELECT -> null;
            case VK_LAUNCH_APP1 -> null;
            case VK_LAUNCH_APP2 -> null;
            case VK_RESERVED_B8 -> null;
            case VK_RESERVED_B9 -> null;
            case VK_OEM_1 -> Key.semicolumn;
            case VK_OEM_PLUS -> Key.equal;
            case VK_OEM_COMMA -> Key.comma;
            case VK_OEM_MINUS -> Key.minus;
            case VK_OEM_PERIOD -> Key.period;
            case VK_OEM_2 -> Key.forwardslash;
            case VK_OEM_3 -> Key.quote;
            case VK_RESERVED_C1 -> null;
            case VK_RESERVED_C2 -> null;
            case VK_GAMEPAD_A -> null;
            case VK_GAMEPAD_B -> null;
            case VK_GAMEPAD_X -> null;
            case VK_GAMEPAD_Y -> null;
            case VK_GAMEPAD_RIGHT_SHOULDER -> null;
            case VK_GAMEPAD_LEFT_SHOULDER -> null;
            case VK_GAMEPAD_LEFT_TRIGGER -> null;
            case VK_GAMEPAD_RIGHT_TRIGGER -> null;
            case VK_GAMEPAD_DPAD_UP -> null;
            case VK_GAMEPAD_DPAD_DOWN -> null;
            case VK_GAMEPAD_DPAD_LEFT -> null;
            case VK_GAMEPAD_DPAD_RIGHT -> null;
            case VK_GAMEPAD_MENU -> null;
            case VK_GAMEPAD_VIEW -> null;
            case VK_GAMEPAD_LEFT_THUMBSTICK_BUTTON -> null;
            case VK_GAMEPAD_RIGHT_THUMBSTICK_BUTTON -> null;
            case VK_GAMEPAD_LEFT_THUMBSTICK_UP -> null;
            case VK_GAMEPAD_LEFT_THUMBSTICK_DOWN -> null;
            case VK_GAMEPAD_LEFT_THUMBSTICK_RIGHT -> null;
            case VK_GAMEPAD_LEFT_THUMBSTICK_LEFT -> null;
            case VK_GAMEPAD_RIGHT_THUMBSTICK_UP -> null;
            case VK_GAMEPAD_RIGHT_THUMBSTICK_DOWN -> null;
            case VK_GAMEPAD_RIGHT_THUMBSTICK_RIGHT -> null;
            case VK_GAMEPAD_RIGHT_THUMBSTICK_LEFT -> null;
            case VK_OEM_4 -> Key.openingbracket;
            case VK_OEM_5 -> Key.backslash;
            case VK_OEM_6 -> Key.closingbracket;
            case VK_OEM_7 -> Key.pound;
            case VK_OEM_8 -> Key.backtick;
            case VK_RESERVED_E0 -> null;
            case VK_OEM_AX -> null;
            case VK_OEM_102 -> null;
            case VK_ICO_HELP -> null;
            case VK_ICO_00 -> null;
            case VK_PROCESSKEY -> null;
            case VK_ICO_CLEAR -> null;
            case VK_PACKET -> null;
            case VK_RESERVED_E8 -> null;
            case VK_OEM_RESET -> null;
            case VK_OEM_JUMP -> null;
            case VK_OEM_PA1 -> null;
            case VK_OEM_PA2 -> null;
            case VK_OEM_PA3 -> null;
            case VK_OEM_WSCTRL -> null;
            case VK_OEM_CUSEL -> null;
            case VK_OEM_ATTN -> null;
            case VK_OEM_FINISH -> null;
            case VK_OEM_COPY -> null;
            case VK_OEM_AUTO -> null;
            case VK_OEM_ENLW -> null;
            case VK_OEM_BACKTAB -> null;
            case VK_ATTN -> null;
            case VK_CRSEL -> null;
            case VK_EXSEL -> null;
            case VK_EREOF -> null;
            case VK_PLAY -> null;
            case VK_ZOOM -> null;
            case VK_NONAME -> null;
            case VK_PA1 -> null;
            case VK_OEM_CLEAR -> null;
            case VK_NONE -> null;
        };
    }

    public static WindowsVirtualKey windowsVirtualKeyFromKey(Key key) {
        return switch (key) {
            case Key.backspace -> VK_BACK;
            case Key.tab -> VK_TAB;
            case Key.enter -> VK_RETURN;
            case Key.pause -> VK_PAUSE;
            case Key.capslock -> VK_CAPITAL;
            case Key.esc -> VK_ESCAPE;
            case Key.space -> VK_SPACE;
            case Key.pageup -> VK_PGUP;
            case Key.pagedown -> VK_PGDN;
            case Key.end -> VK_END;
            case Key.home -> VK_HOME;
            case Key.left -> VK_LEFT;
            case Key.up -> VK_UP;
            case Key.right -> VK_RIGHT;
            case Key.down -> VK_DOWN;
            case Key.printscreen -> VK_PRTSCN;
            case Key.insert -> VK_INSERT;
            case Key.del -> VK_DELETE;
            case Key._0 -> VK_0;
            case Key._1 -> VK_1;
            case Key._2 -> VK_2;
            case Key._3 -> VK_3;
            case Key._4 -> VK_4;
            case Key._5 -> VK_5;
            case Key._6 -> VK_6;
            case Key._7 -> VK_7;
            case Key._8 -> VK_8;
            case Key._9 -> VK_9;
            case Key.a -> VK_A;
            case Key.b -> VK_B;
            case Key.c -> VK_C;
            case Key.d -> VK_D;
            case Key.e -> VK_E;
            case Key.f -> VK_F;
            case Key.g -> VK_G;
            case Key.h -> VK_H;
            case Key.i -> VK_I;
            case Key.j -> VK_J;
            case Key.k -> VK_K;
            case Key.l -> VK_L;
            case Key.m -> VK_M;
            case Key.n -> VK_N;
            case Key.o -> VK_O;
            case Key.p -> VK_P;
            case Key.q -> VK_Q;
            case Key.r -> VK_R;
            case Key.s -> VK_S;
            case Key.t -> VK_T;
            case Key.u -> VK_U;
            case Key.v -> VK_V;
            case Key.w -> VK_W;
            case Key.x -> VK_X;
            case Key.y -> VK_Y;
            case Key.z -> VK_Z;
            case Key.win -> VK_LWIN;
            case Key.numpad0 -> VK_NUMPAD0;
            case Key.numpad1 -> VK_NUMPAD1;
            case Key.numpad2 -> VK_NUMPAD2;
            case Key.numpad3 -> VK_NUMPAD3;
            case Key.numpad4 -> VK_NUMPAD4;
            case Key.numpad5 -> VK_NUMPAD5;
            case Key.numpad6 -> VK_NUMPAD6;
            case Key.numpad7 -> VK_NUMPAD7;
            case Key.numpad8 -> VK_NUMPAD8;
            case Key.numpad9 -> VK_NUMPAD9;
            case Key.f1 -> VK_F1;
            case Key.f2 -> VK_F2;
            case Key.f3 -> VK_F3;
            case Key.f4 -> VK_F4;
            case Key.f5 -> VK_F5;
            case Key.f6 -> VK_F6;
            case Key.f7 -> VK_F7;
            case Key.f8 -> VK_F8;
            case Key.f9 -> VK_F9;
            case Key.f10 -> VK_F10;
            case Key.f11 -> VK_F11;
            case Key.f12 -> VK_F12;
            case Key.leftshift -> VK_LSHIFT;
            case Key.rightshift -> VK_RSHIFT;
            case Key.leftctrl -> VK_LCONTROL;
            case Key.rightctrl -> VK_RCONTROL;
            case Key.leftalt -> VK_LMENU;
            case Key.rightalt -> VK_RMENU;
            case Key.semicolumn -> VK_OEM_1;
            case Key.equal -> VK_OEM_PLUS;
            case Key.comma -> VK_OEM_COMMA;
            case Key.minus -> VK_OEM_MINUS;
            case Key.period -> VK_OEM_PERIOD;
            case Key.forwardslash -> VK_OEM_2;
            case Key.quote -> VK_OEM_3;
            case Key.openingbracket -> VK_OEM_4;
            case Key.backslash -> VK_OEM_5;
            case Key.closingbracket -> VK_OEM_6;
            case Key.pound -> VK_OEM_7;
            case Key.backtick -> VK_OEM_8;
        };
    }

}

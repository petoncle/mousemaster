package mousemaster.platform.macos;

import mousemaster.Key;
import mousemaster.KeyboardLayout;

import java.util.HashMap;
import java.util.Map;

/**
 * HID keyboard usages: a layout independent one carries its key, the others the PS/2 scan
 * code, since the layout maps the numpad scan codes to what numlock off produces.
 */
public enum MacosHidUsage {

    // @formatter:off
    a(0x04, 0x1E),
    b(0x05, 0x30),
    c(0x06, 0x2E),
    d(0x07, 0x20),
    e(0x08, 0x12),
    f(0x09, 0x21),
    g(0x0A, 0x22),
    h(0x0B, 0x23),
    i(0x0C, 0x17),
    j(0x0D, 0x24),
    k(0x0E, 0x25),
    l(0x0F, 0x26),
    m(0x10, 0x32),
    n(0x11, 0x31),
    o(0x12, 0x18),
    p(0x13, 0x19),
    q(0x14, 0x10),
    r(0x15, 0x13),
    s(0x16, 0x1F),
    t(0x17, 0x14),
    u(0x18, 0x16),
    v(0x19, 0x2F),
    w(0x1A, 0x11),
    x(0x1B, 0x2D),
    y(0x1C, 0x15),
    z(0x1D, 0x2C),
    one(0x1E, 0x02),
    two(0x1F, 0x03),
    three(0x20, 0x04),
    four(0x21, 0x05),
    five(0x22, 0x06),
    six(0x23, 0x07),
    seven(0x24, 0x08),
    eight(0x25, 0x09),
    nine(0x26, 0x0A),
    zero(0x27, 0x0B),
    enter(0x28, Key.enter),
    esc(0x29, Key.esc),
    backspace(0x2A, Key.backspace),
    tab(0x2B, Key.tab),
    space(0x2C, Key.space),
    minus(0x2D, 0x0C),
    equal(0x2E, 0x0D),
    leftbracket(0x2F, 0x1A),
    rightbracket(0x30, 0x1B),
    backslash(0x31, 0x2B),
    nonushash(0x32, 0x2B),
    semicolon(0x33, 0x27),
    quote(0x34, 0x28),
    grave(0x35, 0x29),
    comma(0x36, 0x33),
    period(0x37, 0x34),
    slash(0x38, 0x35),
    capslock(0x39, Key.capslock),
    f1(0x3A, Key.f1),
    f2(0x3B, Key.f2),
    f3(0x3C, Key.f3),
    f4(0x3D, Key.f4),
    f5(0x3E, Key.f5),
    f6(0x3F, Key.f6),
    f7(0x40, Key.f7),
    f8(0x41, Key.f8),
    f9(0x42, Key.f9),
    f10(0x43, Key.f10),
    f11(0x44, Key.f11),
    f12(0x45, Key.f12),
    printscreen(0x46, Key.printscreen),
    scrolllock(0x47, Key.scrolllock),
    pause(0x48, Key.pause),
    insert(0x49, Key.insert),
    home(0x4A, Key.home),
    pageup(0x4B, Key.pageup),
    del(0x4C, Key.del),
    end(0x4D, Key.end),
    pagedown(0x4E, Key.pagedown),
    rightarrow(0x4F, Key.rightarrow),
    leftarrow(0x50, Key.leftarrow),
    downarrow(0x51, Key.downarrow),
    uparrow(0x52, Key.uparrow),
    numlock(0x53, Key.numlock),
    numpaddivide(0x54, Key.numpaddivide),
    numpadmultiply(0x55, Key.numpadmultiply),
    numpadsubtract(0x56, Key.numpadsubtract),
    numpadadd(0x57, Key.numpadadd),
    numpadenter(0x58, Key.enter),
    numpad1(0x59, Key.numpad1),
    numpad2(0x5A, Key.numpad2),
    numpad3(0x5B, Key.numpad3),
    numpad4(0x5C, Key.numpad4),
    numpad5(0x5D, Key.numpad5),
    numpad6(0x5E, Key.numpad6),
    numpad7(0x5F, Key.numpad7),
    numpad8(0x60, Key.numpad8),
    numpad9(0x61, Key.numpad9),
    numpad0(0x62, Key.numpad0),
    numpaddecimal(0x63, Key.numpaddecimal),
    nonusbackslash(0x64, 0x56),
    menu(0x65, Key.menu),
    f13(0x68, Key.f13),
    f14(0x69, Key.f14),
    f15(0x6A, Key.f15),
    f16(0x6B, Key.f16),
    f17(0x6C, Key.f17),
    f18(0x6D, Key.f18),
    f19(0x6E, Key.f19),
    f20(0x6F, Key.f20),
    f21(0x70, Key.f21),
    f22(0x71, Key.f22),
    f23(0x72, Key.f23),
    f24(0x73, Key.f24),
    leftctrl(0xE0, Key.leftctrl),
    leftshift(0xE1, Key.leftshift),
    leftalt(0xE2, Key.leftalt),
    leftwin(0xE3, Key.leftwin),
    rightctrl(0xE4, Key.rightctrl),
    rightshift(0xE5, Key.rightshift),
    rightalt(0xE6, Key.rightalt),
    rightwin(0xE7, Key.rightwin);
    // @formatter:on

    public static final int keyboardPage = 0x07;

    private static final Map<Integer, MacosHidUsage> byUsage = new HashMap<>();
    private static final Map<Integer, MacosHidUsage> byScanCode = new HashMap<>();
    private static final Map<Key, MacosHidUsage> byKey = new HashMap<>();

    static {
        for (MacosHidUsage hidUsage : values()) {
            byUsage.put(hidUsage.usage, hidUsage);
            if (hidUsage.key != null)
                byKey.putIfAbsent(hidUsage.key, hidUsage);
            else
                byScanCode.putIfAbsent(hidUsage.scanCode, hidUsage);
        }
    }

    public final int usage;
    public final int scanCode;
    public final Key key;

    MacosHidUsage(int usage, int scanCode) {
        this.usage = usage;
        this.scanCode = scanCode;
        this.key = null;
    }

    MacosHidUsage(int usage, Key key) {
        this.usage = usage;
        this.scanCode = -1;
        this.key = key;
    }

    /**
     * Returns null for a usage that is not a key: another usage page, a reserved usage
     * (error roll over), or a usage outside the table.
     */
    public static Key keyFromHidEvent(int page, int usage,
                                      KeyboardLayout activeKeyboardLayout) {
        if (page != keyboardPage)
            return null;
        MacosHidUsage hidUsage = byUsage.get(usage);
        if (hidUsage == null)
            return null;
        return hidUsage.key != null ? hidUsage.key :
                activeKeyboardLayout.keyFromScanCode(hidUsage.scanCode);
    }

    public static MacosHidUsage hidUsageFromKey(Key key,
                                                KeyboardLayout keyboardLayout) {
        MacosHidUsage hidUsage = byKey.get(key);
        return hidUsage != null ? hidUsage :
                byScanCode.get(keyboardLayout.scanCode(key));
    }

}

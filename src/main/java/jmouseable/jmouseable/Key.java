 package jmouseable.jmouseable;

 import java.util.Arrays;
 import java.util.Map;
 import java.util.function.Function;
 import java.util.stream.Collectors;

 public enum Key {

    tab,
    enter,
    leftshift,
    leftctrl,
    leftalt,
    rightshift,
    rightctrl,
    rightalt,
    pause,
    capslock,
    esc,
    space,
    pageup,
    pagedown,
    end,
    home,
    left,
    up,
    right,
    down,
    printscreen,
    insert,
    del,
    backspace,
    _0,
    _1,
    _2,
    _3,
    _4,
    _5,
    _6,
    _7,
    _8,
    _9,
    a,
    b,
    c,
    d,
    e,
    f,
    g,
    h,
    i,
    j,
    k,
    l,
    m,
    n,
    o,
    p,
    q,
    r,
    s,
    t,
    u,
    v,
    w,
    x,
    y,
    z,
    win,
    numpad0,
    numpad1,
    numpad2,
    numpad3,
    numpad4,
    numpad5,
    numpad6,
    numpad7,
    numpad8,
    numpad9,
    f1,
    f2,
    f3,
    f4,
    f5,
    f6,
    f7,
    f8,
    f9,
    f10,
    f11,
    f12,
    equal,
    minus,
    comma,
    period,
    forwardslash,
    backslash,
    openingbracket,
    closingbracket,
    pound,
    quote,
    backtick
    ;

     public static final Map<String, Key> keyByName = //
             Arrays.stream(values())
                   .collect(Collectors.toMap(Key::keyName, Function.identity()));

    public String keyName() {
        String name = name();
        if (name.startsWith("_"))
            return name.substring(1);
        return name;
    }

}

package jmouseable.jmouseable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public record Key(String staticName, String character) {

    public static final Key tab = new Key("tab", null);
    public static final Key enter = new Key("enter", null);
    public static final Key leftshift = new Key("leftshift", null);
    public static final Key leftctrl = new Key("leftctrl", null);
    public static final Key leftalt = new Key("leftalt", null);
    public static final Key rightshift = new Key("rightshift", null);
    public static final Key rightctrl = new Key("rightctrl", null);
    public static final Key rightalt = new Key("rightalt", null);
    public static final Key pause = new Key("pause", null);
    public static final Key capslock = new Key("capslock", null);
    public static final Key esc = new Key("esc", null);
    public static final Key space = new Key("space", null);
    public static final Key pageup = new Key("pageup", null);
    public static final Key pagedown = new Key("pagedown", null);
    public static final Key end = new Key("end", null);
    public static final Key home = new Key("home", null);
    public static final Key left = new Key("left", null);
    public static final Key up = new Key("up", null);
    public static final Key right = new Key("right", null);
    public static final Key down = new Key("down", null);
    public static final Key printscreen = new Key("printscreen", null);
    public static final Key insert = new Key("insert", null);
    public static final Key del = new Key("del", null);
    public static final Key backspace = new Key("backspace", null);
    public static final Key win = new Key("win", null);
    public static final Key numpad0 = new Key("numpad0", null);
    public static final Key numpad1 = new Key("numpad1", null);
    public static final Key numpad2 = new Key("numpad2", null);
    public static final Key numpad3 = new Key("numpad3", null);
    public static final Key numpad4 = new Key("numpad4", null);
    public static final Key numpad5 = new Key("numpad5", null);
    public static final Key numpad6 = new Key("numpad6", null);
    public static final Key numpad7 = new Key("numpad7", null);
    public static final Key numpad8 = new Key("numpad8", null);
    public static final Key numpad9 = new Key("numpad9", null);
    public static final Key numpadmultiply = new Key("numpadmultiply", null);
    public static final Key numpadadd = new Key("numpadadd", null);
    public static final Key numpadsubtract = new Key("numpadsubtract", null);
    public static final Key numpaddecimal = new Key("numpaddecimal", null);
    public static final Key numpaddivide = new Key("numpaddivide", null);
    public static final Key f1 = new Key("f1", null);
    public static final Key f2 = new Key("f2", null);
    public static final Key f3 = new Key("f3", null);
    public static final Key f4 = new Key("f4", null);
    public static final Key f5 = new Key("f5", null);
    public static final Key f6 = new Key("f6", null);
    public static final Key f7 = new Key("f7", null);
    public static final Key f8 = new Key("f8", null);
    public static final Key f9 = new Key("f9", null);
    public static final Key f10 = new Key("f10", null);
    public static final Key f11 = new Key("f11", null);
    public static final Key f12 = new Key("f12", null);
    public static final Key leftcurlybrace = new Key("leftcurlybrace", "{");
    public static final Key rightcurlybrace = new Key("rightcurlybrace", "}");
    public static final Key caret = new Key("caret", "^");
    public static final Key pipe = new Key("pipe", "|");
    public static final Key underscore = new Key("underscore", "_");
    public static final Key plus = new Key("plus", "+");
    public static final Key minus = new Key("minus", "-");

    public static final Set<Key> keyboardLayoutIndependentKeys = Set.of(
            // @formatter:off
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
            numpadmultiply,
            numpadadd,
            numpadsubtract,
            numpaddecimal,
            numpaddivide,
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
            f12
            // @formatter:on
    );

    private static final Set<Key> keyboardLayoutDependentAliasedKeys = Set.of(
            // @formatter:off
            leftcurlybrace,
            rightcurlybrace,
            caret,
            pipe,
            underscore,
            plus,
            minus
            // @formatter:on
    );

    private static final Map<String, Key> staticallyNamedKeys = new HashMap<>();
    private static final Map<String, Key> keyboardLayoutDependentKeyByCharacter =
            new HashMap<>();

    static {
        for (Key key : keyboardLayoutIndependentKeys)
            staticallyNamedKeys.put(key.staticName, key);
        for (Key key : keyboardLayoutDependentAliasedKeys)
            staticallyNamedKeys.put(key.staticName, key);
    }

    private static Key ofStaticName(String staticName) {
        return staticallyNamedKeys.get(staticName);
    }

    public static Key ofName(String name) {
        Key staticallyNamedKey = ofStaticName(name);
        if (staticallyNamedKey != null)
            return staticallyNamedKey;
        String character = name;
        if (character.length() != 1)
            throw new IllegalArgumentException("Invalid key character: " + character);
        return ofCharacter(character);
    }

    public static Key ofCharacter(String character) {
        for (Key aliasedKey : keyboardLayoutDependentAliasedKeys) {
            if (aliasedKey.character.equals(character))
                return aliasedKey;
        }
        return keyboardLayoutDependentKeyByCharacter.computeIfAbsent(character,
                c -> new Key(null, c));
    }

    public String name() {
        return staticName != null ? staticName : character;
    }

}

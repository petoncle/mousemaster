package mousemaster;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public record Key(String staticName, String staticSingleCharacterName, String character) {

    public static final Key tab = new Key("tab", "⭾", null);
    public static final Key enter = new Key("enter", "⏎", null);
    public static final Key leftshift = new Key("leftshift", "⇧", null);
    public static final Key leftctrl = new Key("leftctrl", "⌃", null);
    public static final Key leftalt = new Key("leftalt", "⎇", null);
    public static final Key rightshift = new Key("rightshift", "⇧", null);
    public static final Key rightctrl = new Key("rightctrl", "⌃", null);
    public static final Key rightalt = new Key("rightalt", "⎇", null);
    public static final Key pause = new Key("pause", null, null);
    public static final Key scrolllock = new Key("scrolllock", null, null);
    public static final Key capslock = new Key("capslock", "⇪", null);
    public static final Key esc = new Key("esc", "⎋", null);
    public static final Key space = new Key("space", "␣", null);
    public static final Key pageup = new Key("pageup", "⇞", null);
    public static final Key pagedown = new Key("pagedown", "⇟", null);
    public static final Key end = new Key("end", "⇲", null);
    public static final Key home = new Key("home", "⇱", null);
    public static final Key leftarrow = new Key("leftarrow", "←", null);
    public static final Key uparrow = new Key("uparrow", "↑", null);
    public static final Key rightarrow = new Key("rightarrow", "→", null);
    public static final Key downarrow = new Key("downarrow", "↓", null);
    public static final Key printscreen = new Key("printscreen", null, null);
    public static final Key insert = new Key("insert", null, null);
    public static final Key del = new Key("del", "⌦", null);
    public static final Key break_ = new Key("break", null, null);
    public static final Key backspace = new Key("backspace", "⌫", null);
    public static final Key leftwin = new Key("leftwin", "⊞", null);
    public static final Key rightwin = new Key("rightwin", "⊞", null);
    public static final Key menu = new Key("menu", null, null);
    public static final Key numpad0 = new Key("numpad0", "0", null);
    public static final Key numpad1 = new Key("numpad1", "1", null);
    public static final Key numpad2 = new Key("numpad2", "2", null);
    public static final Key numpad3 = new Key("numpad3", "3", null);
    public static final Key numpad4 = new Key("numpad4", "4", null);
    public static final Key numpad5 = new Key("numpad5", "5", null);
    public static final Key numpad6 = new Key("numpad6", "6", null);
    public static final Key numpad7 = new Key("numpad7", "7", null);
    public static final Key numpad8 = new Key("numpad8", "8", null);
    public static final Key numpad9 = new Key("numpad9", "9", null);
    public static final Key numpadmultiply = new Key("numpadmultiply", "*", null);
    public static final Key numpadadd = new Key("numpadadd", "+", null);
    public static final Key numpadsubtract = new Key("numpadsubtract", "-", null);
    public static final Key numpaddecimal = new Key("numpaddecimal", ".", null);
    public static final Key numpaddivide = new Key("numpaddivide", "/", null);
    public static final Key numlock = new Key("numlock", null, null);
    public static final Key f1 = new Key("f1", null, null);
    public static final Key f2 = new Key("f2", null, null);
    public static final Key f3 = new Key("f3", null, null);
    public static final Key f4 = new Key("f4", null, null);
    public static final Key f5 = new Key("f5", null, null);
    public static final Key f6 = new Key("f6", null, null);
    public static final Key f7 = new Key("f7", null, null);
    public static final Key f8 = new Key("f8", null, null);
    public static final Key f9 = new Key("f9", null, null);
    public static final Key f10 = new Key("f10", null, null);
    public static final Key f11 = new Key("f11", null, null);
    public static final Key f12 = new Key("f12", null, null);
    public static final Key f13 = new Key("f13", null, null);
    public static final Key f14 = new Key("f14", null, null);
    public static final Key f15 = new Key("f15", null, null);
    public static final Key f16 = new Key("f16", null, null);
    public static final Key f17 = new Key("f17", null, null);
    public static final Key f18 = new Key("f18", null, null);
    public static final Key f19 = new Key("f19", null, null);
    public static final Key f20 = new Key("f20", null, null);
    public static final Key f21 = new Key("f21", null, null);
    public static final Key f22 = new Key("f22", null, null);
    public static final Key f23 = new Key("f23", null, null);
    public static final Key f24 = new Key("f24", null, null);
    public static final Key leftcurlybrace = new Key("leftcurlybrace", null, "{");
    public static final Key rightcurlybrace = new Key("rightcurlybrace", null, "}");
    public static final Key caret = new Key("caret", null, "^");
    public static final Key pipe = new Key("pipe", null, "|");
    public static final Key underscore = new Key("underscore", null, "_");
    public static final Key plus = new Key("plus", null, "+");
    public static final Key minus = new Key("minus", null, "-");
    public static final Key hash = new Key("hash", null, "#");
    public static final Key backslash = new Key("backslash", null, "\\");

    public static final Set<Key> keyboardLayoutIndependentKeys = Set.of(
            // @formatter:off
            break_,
            tab,
            enter,
            leftshift,
            leftctrl,
            leftalt,
            rightshift,
            rightctrl,
            rightalt,
            pause,
            scrolllock,
            capslock,
            esc,
            space,
            pageup,
            pagedown,
            end,
            home,
            leftarrow,
            uparrow,
            rightarrow,
            downarrow,
            printscreen,
            insert,
            del,
            backspace,
            leftwin,
            rightwin,
            menu,
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
            numlock,
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
            f13,
            f14,
            f15,
            f16,
            f17,
            f18,
            f19,
            f20,
            f21,
            f22,
            f23,
            f24
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
            minus,
            hash,
            backslash
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
                c -> new Key(null, null, c));
    }

    public String name() {
        return staticName != null ? staticName : character;
    }

    public String hintLabel() {
        // toUpperCase() could be problematic since it uses Locale.default().
        // In azerty, ù should be displayed as ù and not CARET.
        if (staticSingleCharacterName != null)
            return staticSingleCharacterName;
        String label = character != null ? character : staticName;
        return label.toUpperCase();
    }

    @Override
    public String toString() {
        return name();
    }

}

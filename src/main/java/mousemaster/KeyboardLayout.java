package mousemaster;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import mousemaster.platform.windows.WindowsVirtualKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class KeyboardLayout {

    private static final Logger logger = LoggerFactory.getLogger(KeyboardLayout.class);

    private static final Map<KeyboardIdentifierAndType, KeyboardLayout> keyboardLayoutByKeyboardIdentifierAndType = new HashMap<>();
    public static final Map<String, KeyboardLayout> keyboardLayoutByShortName = new HashMap<>();

    static {
        InputStreamReader reader = new InputStreamReader(
                MousemasterApplication.resourceStream("keyboard-layouts.json"),
                StandardCharsets.UTF_8
        );
        Type listType = new TypeToken<List<KeyboardLayout>>() {}.getType();
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Key.class, new KeyDeserializer())
                .create();
        long before = System.nanoTime();
        List<KeyboardLayout> keyboardLayouts = gson.fromJson(reader, listType);
        for (KeyboardLayout keyboardLayout : keyboardLayouts) {
            if (keyboardLayout.identifier != null)
                keyboardLayoutByKeyboardIdentifierAndType.put(
                        new KeyboardIdentifierAndType(keyboardLayout.identifier,
                                keyboardLayout.keyboardType), keyboardLayout);
            if (keyboardLayout.shortName != null)
                keyboardLayoutByShortName.put(keyboardLayout.shortName, keyboardLayout);
        }
        long after = System.nanoTime();
        logger.trace("Loaded " + keyboardLayouts.size() + " keyboard layouts in " +
                     (after - before) / 1000_000 + "ms");
    }

    public static KeyboardLayout keyboardLayout(String keyboardIdentifier,
                                                Integer keyboardType) {
        KeyboardLayout keyboardLayout = keyboardLayoutByKeyboardIdentifierAndType.get(
                new KeyboardIdentifierAndType(keyboardIdentifier, keyboardType));
        if (keyboardLayout != null)
            return keyboardLayout;
        return keyboardLayoutByKeyboardIdentifierAndType.get(
                new KeyboardIdentifierAndType(keyboardIdentifier, null));
    }

    public static class KeySerializer implements JsonSerializer<Key> {

        @Override
        public JsonElement serialize(Key key, Type typeOfSrc,
                                     JsonSerializationContext context) {
            JsonObject keyObject = new JsonObject();
            if (key.staticName() != null)
                keyObject.addProperty("staticName", key.staticName());
            if (key.character() != null)
                keyObject.addProperty("character", key.character());
            return keyObject;
        }
    }

    public static class KeyDeserializer implements JsonDeserializer<Key> {

        @Override
        public Key deserialize(JsonElement json, Type typeOfT,
                               JsonDeserializationContext context) throws JsonParseException {
            JsonObject keyObject = json.getAsJsonObject();
            if (keyObject.has("staticName"))
                return Key.ofName(keyObject.get("staticName").getAsString());
            return Key.ofCharacter(keyObject.get("character").getAsString());
        }
    }

    // Gson deserializes the "virtualKey" JSON field into the enum by name automatically.
    public record KeyboardLayoutKey(int scanCode, WindowsVirtualKey virtualKey, Key key,
                                    String text, String shiftText, String altgrText,
                                    String name) {
    }

    /**
     * A null keyboardType matches any keyboard type.
     */
    private record KeyboardIdentifierAndType(String keyboardIdentifier,
                                             Integer keyboardType) {
    }

    private final String identifier;
    private final String displayName;
    private final String driverName;
    private final String shortName;
    private final Integer keyboardType;
    private final List<KeyboardLayoutKey> keys;

    public KeyboardLayout(String identifier, String displayName, String driverName,
                          String shortName, Integer keyboardType,
                          List<KeyboardLayoutKey> keys) {
        this.identifier = identifier;
        this.displayName = displayName;
        this.driverName = driverName;
        this.shortName = shortName;
        this.keyboardType = keyboardType;
        this.keys = keys;
    }

    public String identifier() {
        return identifier;
    }

    public String displayName() {
        return displayName;
    }

    public String driverName() {
        return driverName;
    }

    public String shortName() {
        return shortName;
    }

    public Integer keyboardType() {
        return keyboardType;
    }

    public List<KeyboardLayoutKey> keys() {
        return keys;
    }

    public boolean containsKey(Key key) {
        return scanCode(key) != -1;
    }

    public Key keyFromScanCode(int scanCode) {
        for (KeyboardLayoutKey keyboardLayoutKey : keys) {
            if (keyboardLayoutKey.scanCode == scanCode)
                return keyboardLayoutKey.key();
        }
        return null;
    }

    public WindowsVirtualKey virtualKeyFromScanCode(int scanCode) {
        for (KeyboardLayoutKey keyboardLayoutKey : keys) {
            if (keyboardLayoutKey.scanCode == scanCode)
                return keyboardLayoutKey.virtualKey();
        }
        return null;
    }

    public Key keyFromVirtualKey(WindowsVirtualKey virtualKey) {
        for (KeyboardLayoutKey keyboardLayoutKey : keys) {
            if (keyboardLayoutKey.virtualKey == virtualKey)
                return keyboardLayoutKey.key();
        }
        return null;
    }

    public Key keyFromShiftText(String shiftText) {
        for (KeyboardLayoutKey keyboardLayoutKey : keys) {
            if (shiftText.equals(keyboardLayoutKey.shiftText))
                return keyboardLayoutKey.key();
        }
        return null;
    }

    public Key keyFromAltgrText(String altgrText) {
        for (KeyboardLayoutKey keyboardLayoutKey : keys) {
            if (altgrText.equals(keyboardLayoutKey.altgrText))
                return keyboardLayoutKey.key();
        }
        return null;
    }

    /**
     * If the layout is us-qwerty and the typed key is ?, then the modified key press will be /
     * with leftshift or rightshift.
     */
    public ModifiedKeyPress modifiedKeyPressFromTypedKey(Key typedKey) {
        if (typedKey.character() == null || containsKey(typedKey))
            return null;
        Key shiftKey = keyFromShiftText(typedKey.character());
        if (shiftKey != null)
            return new ModifiedKeyPress(shiftKey, Set.of(Key.leftshift, Key.rightshift), typedKey);
        Key altgrKey = keyFromAltgrText(typedKey.character());
        if (altgrKey != null)
            return new ModifiedKeyPress(altgrKey, Set.of(Key.rightalt), typedKey);
        return null;
    }

    /**
     * If the layout is us-qwerty, the candidate modified key is / and the candidate modifier
     * keys include a shift, then the modified typed key will be ?.
     */
    public Key modifiedTypedKeyFromPress(Key candidateModifiedKey, Set<Key> candidateModifierKeys) {
        boolean shift = candidateModifierKeys.contains(Key.leftshift) ||
                        candidateModifierKeys.contains(Key.rightshift);
        boolean altgr = candidateModifierKeys.contains(Key.rightalt);
        if (shift == altgr || candidateModifierKeys.contains(Key.leftctrl) ||
            candidateModifierKeys.contains(Key.rightctrl) ||
            candidateModifierKeys.contains(Key.leftalt) ||
            candidateModifierKeys.contains(Key.leftwin) ||
            candidateModifierKeys.contains(Key.rightwin))
            return null;
        for (KeyboardLayoutKey keyboardLayoutKey : keys) {
            if (keyboardLayoutKey.key.equals(candidateModifiedKey)) {
                String text = shift ? keyboardLayoutKey.shiftText : keyboardLayoutKey.altgrText;
                return text == null ? null : Key.ofCharacter(text);
            }
        }
        return null;
    }

    public int scanCode(Key key) {
        for (KeyboardLayoutKey keyboardLayoutKey : keys) {
            if (keyboardLayoutKey.key.equals(key))
                return keyboardLayoutKey.scanCode();
        }
        return -1;
    }

    public WindowsVirtualKey virtualKey(Key key) {
        for (KeyboardLayoutKey keyboardLayoutKey : keys) {
            if (keyboardLayoutKey.key.equals(key))
                return keyboardLayoutKey.virtualKey();
        }
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof KeyboardLayout that))
            return false;
        return Objects.equals(this.identifier, that.identifier) &&
               Objects.equals(this.displayName, that.displayName) &&
               Objects.equals(this.driverName, that.driverName) &&
               Objects.equals(this.keyboardType, that.keyboardType) &&
               Objects.equals(this.keys, that.keys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, displayName, driverName, keyboardType, keys);
    }

    @Override
    public String toString() {
        return "KeyboardLayout[" +
               "identifier=" + identifier + ", " +
               "displayName=" + displayName + ", " +
               "driverName=" + driverName + ", " +
               "shortName=" + shortName + ", " +
               "keyboardType=" + keyboardType + ']';
    }
}

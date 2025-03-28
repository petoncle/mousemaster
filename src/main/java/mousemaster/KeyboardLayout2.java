package mousemaster;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class KeyboardLayout2 {

    private static final Logger logger = LoggerFactory.getLogger(KeyboardLayout2.class);

    public static final Map<String, KeyboardLayout2> keyboardLayoutByIdentifier = new HashMap<>();
    public static final Map<String, KeyboardLayout2> keyboardLayoutByShortName = new HashMap<>();

    static {
        InputStreamReader reader = new InputStreamReader(
                KeyboardLayout2.class.getClassLoader().getResourceAsStream("keyboard-layouts.json")
        );
        Type listType = new TypeToken<List<KeyboardLayout2>>() {}.getType();
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Key.class, new KeyDeserializer())
                .create();
        long before = System.nanoTime();
        List<KeyboardLayout2> keyboardLayouts = gson.fromJson(reader, listType);
        for (KeyboardLayout2 keyboardLayout : keyboardLayouts) {
            keyboardLayoutByIdentifier.put(keyboardLayout.identifier, keyboardLayout);
            if (keyboardLayout.shortName != null)
                keyboardLayoutByShortName.put(keyboardLayout.shortName, keyboardLayout);
        }
        long after = System.nanoTime();
        logger.trace("Loaded " + keyboardLayouts.size() + " keyboard layouts in " + (after - before) / 1000_000 + "ms");
    }


    public static class KeyDeserializer implements JsonDeserializer<Key> {

        private final Map<Key, Key> cache = new HashMap<>();

        @Override
        public Key deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject keyObject = json.getAsJsonObject();
            String staticName = !keyObject.has("staticName") ? null : keyObject.get("staticName").getAsString();
            String staticSingleCharacterName = !keyObject.has("staticSingleCharacterName") ? null : keyObject.get("staticSingleCharacterName").getAsString();
            String character = !keyObject.has("character") ? null : keyObject.get("character").getAsString();
            Key temp = new Key(staticName, staticSingleCharacterName, character);
            return cache.computeIfAbsent(temp, Function.identity());
        }
    }

    public record KeyboardLayoutKey(int scanCode, WindowsVirtualKey virtualKey, Key key,
                                    String text, String name) {

    }

    private final String identifier;
    private final String displayName;
    private final String driverName;
    private final String shortName;
    private final List<KeyboardLayoutKey> keys;

    public KeyboardLayout2(String identifier, String displayName, String driverName,
                           String shortName,
                           List<KeyboardLayoutKey> keys) {
        this.identifier = identifier;
        this.displayName = displayName;
        this.driverName = driverName;
        this.shortName = shortName;
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
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (KeyboardLayout2) obj;
        return Objects.equals(this.identifier, that.identifier) &&
               Objects.equals(this.displayName, that.displayName) &&
               Objects.equals(this.driverName, that.driverName) &&
               Objects.equals(this.keys, that.keys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, displayName, driverName, keys);
    }

    @Override
    public String toString() {
        return "KeyboardLayout[" +
               "identifier=" + identifier + ", " +
               "displayName=" + displayName + ", " +
               "driverName=" + driverName + ", " +
               "shortName=" + shortName + ']';
    }

}

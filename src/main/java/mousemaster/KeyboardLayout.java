package mousemaster;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class KeyboardLayout {

    private static final Logger logger = LoggerFactory.getLogger(KeyboardLayout.class);

    public static final Map<String, KeyboardLayout> keyboardLayoutByIdentifier = new HashMap<>();
    public static final Map<String, KeyboardLayout> keyboardLayoutByShortName = new HashMap<>();

    static {
        InputStreamReader reader = new InputStreamReader(
                KeyboardLayout.class.getClassLoader()
                                   .getResourceAsStream("keyboard-layouts.json"),
                StandardCharsets.UTF_8
        );
        Type listType = new TypeToken<List<KeyboardLayout>>() {}.getType();
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Key.class, new KeyDeserializer())
                .create();
        long before = System.nanoTime();
        List<KeyboardLayout> keyboardLayouts = gson.fromJson(reader, listType);
        for (KeyboardLayout keyboardLayout : keyboardLayouts) {
            keyboardLayoutByIdentifier.put(keyboardLayout.identifier, keyboardLayout);
            if (keyboardLayout.shortName != null)
                keyboardLayoutByShortName.put(keyboardLayout.shortName, keyboardLayout);
        }
        long after = System.nanoTime();
        logger.trace("Loaded " + keyboardLayouts.size() + " keyboard layouts in " +
                     (after - before) / 1000_000 + "ms");
    }

    public static class KeyDeserializer implements JsonDeserializer<Key> {

        private final Map<Key, Key> cache = new HashMap<>();

        @Override
        public Key deserialize(JsonElement json, Type typeOfT,
                               JsonDeserializationContext context) throws JsonParseException {
            JsonObject keyObject = json.getAsJsonObject();
            String staticName = !keyObject.has("staticName") ? null :
                    keyObject.get("staticName").getAsString();
            String staticSingleCharacterName =
                    !keyObject.has("staticSingleCharacterName") ? null :
                            keyObject.get("staticSingleCharacterName").getAsString();
            String character = !keyObject.has("character") ? null :
                    keyObject.get("character").getAsString();
            Key temp = new Key(staticName, staticSingleCharacterName, character);
            return cache.computeIfAbsent(temp, Function.identity());
        }
    }

    // Gson deserializes the "virtualKey" JSON field into the enum by name automatically.
    public record KeyboardLayoutKey(int scanCode, WindowsVirtualKey virtualKey, Key key,
                                    String text, String name) {
    }

    private final String identifier;
    private final String displayName;
    private final String driverName;
    private final String shortName;
    private final List<KeyboardLayoutKey> keys;
    private transient Map<Integer, Key> keyByScanCode;
    private transient Map<WindowsVirtualKey, Key> keyByVirtualKey;
    private transient Map<Key, KeyboardLayoutKey> layoutKeyByKey;

    public KeyboardLayout(String identifier, String displayName, String driverName,
                          String shortName, List<KeyboardLayoutKey> keys) {
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
        buildLookupMaps();
        return keyByScanCode.get(scanCode);
    }

    public Key keyFromVirtualKey(WindowsVirtualKey virtualKey) {
        buildLookupMaps();
        return keyByVirtualKey.get(virtualKey);
    }

    public int scanCode(Key key) {
        buildLookupMaps();
        KeyboardLayoutKey layoutKey = layoutKeyByKey.get(key);
        return layoutKey == null ? -1 : layoutKey.scanCode();
    }

    public WindowsVirtualKey virtualKey(Key key) {
        buildLookupMaps();
        KeyboardLayoutKey layoutKey = layoutKeyByKey.get(key);
        return layoutKey == null ? null : layoutKey.virtualKey();
    }

    private void buildLookupMaps() {
        if (layoutKeyByKey != null)
            return;
        Map<Integer, Key> byScanCode = new HashMap<>();
        Map<WindowsVirtualKey, Key> byVirtualKey = new HashMap<>();
        Map<Key, KeyboardLayoutKey> byKey = new HashMap<>();
        for (KeyboardLayoutKey keyboardLayoutKey : keys) {
            byScanCode.putIfAbsent(keyboardLayoutKey.scanCode(), keyboardLayoutKey.key());
            if (keyboardLayoutKey.virtualKey() != null)
                byVirtualKey.putIfAbsent(keyboardLayoutKey.virtualKey(),
                        keyboardLayoutKey.key());
            byKey.putIfAbsent(keyboardLayoutKey.key(), keyboardLayoutKey);
        }
        keyByScanCode = Collections.unmodifiableMap(byScanCode);
        keyByVirtualKey = Collections.unmodifiableMap(byVirtualKey);
        layoutKeyByKey = Collections.unmodifiableMap(byKey);
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

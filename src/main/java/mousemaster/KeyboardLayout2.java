package mousemaster;

import java.util.List;
import java.util.Objects;

public final class KeyboardLayout2 {

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
               "keys=" + keys + ']';
    }

}

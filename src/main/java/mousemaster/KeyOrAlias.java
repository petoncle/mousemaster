package mousemaster;

import java.util.Set;

public record KeyOrAlias(Key key, KeyAlias alias) {

    public static KeyOrAlias ofKey(Key key) {
        return new KeyOrAlias(key, null);
    }

    public static KeyOrAlias ofAlias(KeyAlias alias) {
        return new KeyOrAlias(null, alias);
    }

    public boolean isAlias() {
        return alias != null;
    }

    public String aliasName() {
        return alias == null ? null : alias.name();
    }

    public boolean matchesKey(Key k) {
        return alias != null ? alias.keys().contains(k) : key.equals(k);
    }

    /**
     * The event's modified typed key if this matches it, else the event's key if this
     * matches it, else null.
     */
    public Key matchedKey(KeyEvent event) {
        if (event.modifiedTypedKey() != null && matchesKey(event.modifiedTypedKey()))
            return event.modifiedTypedKey();
        return matchesKey(event.key()) ? event.key() : null;
    }

    public Set<Key> possibleKeys() {
        return alias != null ? Set.copyOf(alias.keys()) : Set.of(key);
    }

    @Override
    public String toString() {
        return isAlias() ? alias.name() : key.name();
    }

}

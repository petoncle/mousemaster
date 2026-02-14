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

    public Set<Key> possibleKeys() {
        return alias != null ? Set.copyOf(alias.keys()) : Set.of(key);
    }

    @Override
    public String toString() {
        return isAlias() ? alias.name() : key.name();
    }

}

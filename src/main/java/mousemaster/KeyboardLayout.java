package mousemaster;

public interface KeyboardLayout {

    String identifier();

    String displayName();

    String shortName();

    boolean containsKey(Key key);

    Key keyFromScanCode(int scanCode);

    int scanCode(Key key);
}

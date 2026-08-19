package mousemaster;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** A typed character logs as a run-stable name, so a press still pairs with its release. */
public class KeyRedaction {

    private final boolean redactKeys;
    private final Map<Key, Key> redactedKeyByKey = new ConcurrentHashMap<>();
    private final AtomicInteger redactedKeyCount = new AtomicInteger();

    public KeyRedaction(boolean redactKeys) {
        this.redactKeys = redactKeys;
    }

    public boolean redactKeys() {
        return redactKeys;
    }

    public Key key(Key key) {
        if (!redactKeys || !key.typesCharacter())
            return key;
        return redactedKeyByKey.computeIfAbsent(key, _ -> new Key(
                "key#" + redactedKeyCount.incrementAndGet(), null, null));
    }

    public List<Key> keys(Collection<Key> keys) {
        return keys.stream().map(this::key).toList();
    }

    public KeyEvent event(KeyEvent event) {
        // Null when the Windows virtual key maps to no key.
        if (event == null)
            return null;
        Key key = key(event.key());
        return event.isPress() ? new KeyEvent.PressKeyEvent(event.time(), key) :
                new KeyEvent.ReleaseKeyEvent(event.time(), key);
    }

    public ComboPreparation events(ComboPreparation comboPreparation) {
        return new ComboPreparation(
                comboPreparation.events().stream().map(this::event).toList());
    }

    public ResolvedMacroMove move(ResolvedMacroMove move) {
        return switch (move) {
            case ResolvedKeyMacroMove keyMove ->
                    new ResolvedKeyMacroMove(key(keyMove.key()), keyMove.press(),
                            keyMove.destination());
            case StringMacroMove stringMove -> stringMove;
        };
    }

    public List<ResolvedMacroMove> moves(List<? extends ResolvedMacroMove> moves) {
        return moves.stream().map(this::move).toList();
    }

}

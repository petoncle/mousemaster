package mousemaster;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class KeyRedactor {

    private static final Key anonymousKey = new Key("<redacted>", null, null);

    private final KeyRedaction keyRedaction;
    private final Map<Key, Key> pseudonymByKey = new ConcurrentHashMap<>();
    private final AtomicInteger pseudonymCount = new AtomicInteger();

    public KeyRedactor(KeyRedaction keyRedaction) {
        this.keyRedaction = keyRedaction;
    }

    public boolean redactKeys() {
        return keyRedaction != KeyRedaction.NONE;
    }

    public Key key(Key key) {
        if (!key.typesCharacter())
            return key;
        return switch (keyRedaction) {
            case NONE -> key;
            case PSEUDONYMIZE -> pseudonymByKey.computeIfAbsent(key, keyToPseudonymize ->
                    new Key("key#" + pseudonymCount.incrementAndGet(), null, null));
            case ANONYMIZE -> anonymousKey;
        };
    }

    public List<Key> keys(Collection<Key> keys) {
        return keys.stream().map(this::key).toList();
    }

    public KeyEvent event(KeyEvent event) {
        // Null when the virtual key maps to no key (VK_PACKET for a typed string).
        if (event == null)
            return null;
        Key key = key(event.key());
        return event.isPress() ? new KeyEvent.PressKeyEvent(event.time(), key) :
                new KeyEvent.ReleaseKeyEvent(event.time(), key);
    }

    public ComboPreparation comboPreparation(ComboPreparation comboPreparation) {
        return new ComboPreparation(
                comboPreparation.events().stream().map(this::event).toList());
    }

    public String keyEventAndEatens(
            List<KeyboardManager.KeyEventAndEaten> keyEventAndEatens) {
        StringBuilder message = new StringBuilder("[");
        for (int eventIndex = 0; eventIndex < keyEventAndEatens.size(); eventIndex++) {
            KeyboardManager.KeyEventAndEaten keyEventAndEaten =
                    keyEventAndEatens.get(eventIndex);
            if (eventIndex > 0)
                message.append('-')
                       .append(Duration.between(
                               keyEventAndEatens.get(eventIndex - 1).event().time(),
                               keyEventAndEaten.event().time()).toMillis())
                       .append(' ');
            message.append(new KeyboardManager.KeyEventAndEaten(
                    event(keyEventAndEaten.event()), keyEventAndEaten.eaten()));
        }
        return message.append(']').toString();
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

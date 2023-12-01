package jmouseable.jmouseable;

import java.util.List;

public record Hint(List<Key> keySequence) {

    boolean startsWith(List<Key> focusedHintKeySequence) {
        if (focusedHintKeySequence.isEmpty())
            return true;
        return keySequence.subList(0, focusedHintKeySequence.size())
                          .equals(focusedHintKeySequence);
    }

}

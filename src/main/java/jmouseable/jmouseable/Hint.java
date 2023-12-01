package jmouseable.jmouseable;

import java.util.Collections;
import java.util.List;

public record Hint(List<Key> keySequence) {

    boolean containsSequence(List<Key> focusedHintKeySequence) {
        if (focusedHintKeySequence.isEmpty())
            return true;
        return Collections.indexOfSubList(keySequence, focusedHintKeySequence) != -1;
    }

}

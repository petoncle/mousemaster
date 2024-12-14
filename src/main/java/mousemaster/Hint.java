package mousemaster;

import java.util.List;

public record Hint(int centerX, int centerY, int cellWidth, int cellHeight, List<Key> keySequence) {

    boolean startsWith(List<Key> focusedHintKeySequence) {
        if (focusedHintKeySequence.isEmpty())
            return true;
        return keySequence.subList(0, focusedHintKeySequence.size())
                          .equals(focusedHintKeySequence);
    }

}

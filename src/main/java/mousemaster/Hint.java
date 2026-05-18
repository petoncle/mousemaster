package mousemaster;

import java.util.List;

public record Hint(double centerX, double centerY, double cellWidth, double cellHeight,
                   List<Key> keySequence) {

    public boolean startsWith(List<Key> selectedHintKeySequence) {
        if (selectedHintKeySequence.isEmpty())
            return true;
        return keySequence.subList(0, selectedHintKeySequence.size())
                          .equals(selectedHintKeySequence);
    }

}

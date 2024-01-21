package mousemaster;

import java.util.List;
import java.util.stream.Collectors;

public record ComboSequence(List<ComboMove> moves) {

    @Override
    public String toString() {
        return moves.stream().map(Object::toString).collect(Collectors.joining(" "));
    }

}

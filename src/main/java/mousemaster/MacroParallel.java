package mousemaster;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

public record MacroParallel(List<MacroMove> moves, Duration duration) {

    @Override
    public String toString() {
        return moves.stream().map(Object::toString).collect(Collectors.joining(" ")) + " " + duration;
    }

}

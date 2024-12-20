package mousemaster;

import java.util.List;
import java.util.stream.Collectors;

public record RemappingSequence(List<RemappingParallel> parallels) {

    @Override
    public String toString() {
        return parallels.stream().map(Object::toString).collect(Collectors.joining(" "));
    }

}

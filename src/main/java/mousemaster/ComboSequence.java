package mousemaster;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record ComboSequence(List<MoveSet> moveSets) {

    public boolean isEmpty() {
        return moveSets.isEmpty();
    }

    public Set<Key> allKeys() {
        return moveSets.stream()
                       .flatMap(moveSet -> Stream.concat(
                               moveSet.requiredMoves().stream(),
                               moveSet.optionalMoves().stream()))
                       .map(ComboMove::key)
                       .collect(Collectors.toSet());
    }

    public static ComboSequence ofMoves(List<ComboMove> moves) {
        List<MoveSet> moveSets = new ArrayList<>();
        for (ComboMove move : moves) {
            moveSets.add(new MoveSet(List.of(move), List.of()));
        }
        return new ComboSequence(moveSets);
    }

    @Override
    public String toString() {
        return moveSets.stream().map(Object::toString).collect(Collectors.joining(" "));
    }

}

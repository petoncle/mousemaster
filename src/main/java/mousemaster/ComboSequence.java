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
        Set<Key> keys = new java.util.HashSet<>();
        for (MoveSet moveSet : moveSets) {
            if (moveSet.isWaitMoveSet()) {
                keys.addAll(((ComboMove.WaitComboMove) moveSet.requiredMoves().getFirst()).keys());
                continue;
            }
            Stream.concat(moveSet.requiredMoves().stream(),
                          moveSet.optionalMoves().stream())
                  .flatMap(move -> move.keyOrAlias().possibleKeys().stream())
                  .forEach(keys::add);
        }
        return keys;
    }

    public Set<String> aliasNames() {
        return moveSets.stream()
                       .filter(moveSet -> !moveSet.isWaitMoveSet())
                       .flatMap(moveSet -> Stream.concat(
                               moveSet.requiredMoves().stream(),
                               moveSet.optionalMoves().stream()))
                       .filter(move -> move.keyOrAlias().isAlias())
                       .map(move -> move.keyOrAlias().aliasName())
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

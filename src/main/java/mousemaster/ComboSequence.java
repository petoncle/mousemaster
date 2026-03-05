package mousemaster;

import mousemaster.ComboMove.KeyComboMove;
import mousemaster.ComboMove.TapComboMove;
import mousemaster.MoveSet.KeyMoveSet;
import mousemaster.MoveSet.WaitMoveSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record ComboSequence(List<MoveSet> moveSets) {

    public boolean isEmpty() {
        return moveSets.isEmpty();
    }

    public Set<Key> allKeys() {
        Set<Key> keys = new HashSet<>();
        for (MoveSet moveSet : moveSets) {
            switch (moveSet) {
                case WaitMoveSet waitMoveSet ->
                    addWaitMoveKeys(waitMoveSet.waitMove(), keys);
                case KeyMoveSet keyMoveSet -> {
                    Stream.concat(keyMoveSet.requiredMoves().stream(),
                                  keyMoveSet.optionalMoves().stream())
                          .flatMap(move -> move.keyOrAlias().possibleKeys().stream())
                          .forEach(keys::add);
                    if (keyMoveSet.waitMove() != null)
                        addWaitMoveKeys(keyMoveSet.waitMove(), keys);
                }
            }
        }
        return keys;
    }

    private static void addWaitMoveKeys(ComboMove.WaitComboMove waitMove, Set<Key> keys) {
        if (waitMove instanceof ComboMove.WaitComboMove.KeyWaitComboMove kwm) {
            switch (kwm.ignoredKeySet()) {
                case KeySet.Only only -> keys.addAll(only.keys());
                case KeySet.AllExcept allExcept -> keys.addAll(allExcept.keys());
            }
        }
        // PressWaitComboMove / ReleaseWaitComboMove: no specific keys to add.
    }

    public Set<String> aliasNames() {
        Set<String> names = moveSets.stream()
                       .filter(moveSet -> moveSet instanceof KeyMoveSet)
                       .map(moveSet -> (KeyMoveSet) moveSet)
                       .flatMap(moveSet -> Stream.concat(
                               moveSet.requiredMoves().stream(),
                               moveSet.optionalMoves().stream()))
                       .filter(move -> !move.negated() && move.keyOrAlias().isAlias())
                       .map(move -> move.keyOrAlias().aliasName())
                       .collect(Collectors.toCollection(HashSet::new));
        // Include aliases that tap moves were expanded from.
        for (MoveSet moveSet : moveSets) {
            if (moveSet instanceof KeyMoveSet kms) {
                for (KeyComboMove move : kms.requiredMoves())
                    if (move instanceof TapComboMove tap && tap.expandedFromAlias() != null)
                        names.add(tap.expandedFromAlias());
                for (KeyComboMove move : kms.optionalMoves())
                    if (move instanceof TapComboMove tap && tap.expandedFromAlias() != null)
                        names.add(tap.expandedFromAlias());
            }
        }
        return names;
    }

    /**
     * Returns names used in negated moves (alias name or key name).
     */
    public Set<String> negatedNames() {
        return moveSets.stream()
                       .filter(moveSet -> moveSet instanceof KeyMoveSet)
                       .map(moveSet -> (KeyMoveSet) moveSet)
                       .flatMap(moveSet -> Stream.concat(
                               moveSet.requiredMoves().stream(),
                               moveSet.optionalMoves().stream()))
                       .filter(KeyComboMove::negated)
                       .map(move -> {
                           KeyOrAlias koa = move.keyOrAlias();
                           return koa.isAlias() ? koa.aliasName() : koa.key().name();
                       })
                       .collect(Collectors.toSet());
    }

    public static ComboSequence ofMoves(List<KeyComboMove> moves) {
        List<MoveSet> moveSets = new ArrayList<>();
        for (KeyComboMove move : moves) {
            moveSets.add(new KeyMoveSet(List.of(move), List.of()));
        }
        return new ComboSequence(moveSets);
    }

    @Override
    public String toString() {
        return moveSets.stream().map(Object::toString).collect(Collectors.joining(" "));
    }

}

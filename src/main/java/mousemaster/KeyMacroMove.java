package mousemaster;

public record KeyMacroMove(KeyOrAlias keyOrAlias, boolean negated,
                          boolean press, MacroMoveDestination destination) implements MacroMove {

    @Override
    public String toString() {
        String neg = negated ? "!" : "";
        return switch (destination) {
            case OS -> (press ? "+" : "-") + neg + keyOrAlias;
            case COMBO_WATCHER -> (press ? "#" : "~") + neg + keyOrAlias;
        };
    }

}

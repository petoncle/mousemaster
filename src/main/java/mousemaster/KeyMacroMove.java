package mousemaster;

public record KeyMacroMove(KeyOrAlias keyOrAlias, boolean press, MacroMoveDestination destination) implements MacroMove {

    @Override
    public String toString() {
        return switch (destination) {
            case OS -> (press ? "+" : "-") + keyOrAlias;
            case COMBO_WATCHER -> (press ? "#" : "~") + keyOrAlias;
        };
    }

}

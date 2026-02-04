package mousemaster;

public record MacroMove(Key key, boolean press, MacroMoveDestination destination) {

    @Override
    public String toString() {
        return switch (destination) {
            case OS -> (press ? "+" : "-") + key.name();
            case COMBO_WATCHER -> (press ? "#" : "~") + key.name();
        };
    }

}

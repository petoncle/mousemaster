package mousemaster;

public record KeyMacroMove(Key key, boolean press, MacroMoveDestination destination) implements MacroMove {

    @Override
    public String toString() {
        return switch (destination) {
            case OS -> (press ? "+" : "-") + key.name();
            case COMBO_WATCHER -> (press ? "#" : "~") + key.name();
        };
    }

}

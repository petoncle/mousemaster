package mousemaster;

public record ResolvedKeyMacroMove(Key key, boolean press, MacroMoveDestination destination)
        implements ResolvedMacroMove {

    @Override
    public String toString() {
        return switch (destination) {
            case OS -> (press ? "+" : "-") + key;
            case COMBO_WATCHER -> (press ? "#" : "~") + key;
        };
    }

}

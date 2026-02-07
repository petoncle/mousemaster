package mousemaster;

public record StringMacroMove(String string) implements MacroMove {

    @Override
    public MacroMoveDestination destination() {
        return MacroMoveDestination.OS;
    }

    @Override
    public String toString() {
        return "'" + string + "'";
    }

}

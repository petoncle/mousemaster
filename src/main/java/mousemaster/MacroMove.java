package mousemaster;

public sealed interface MacroMove permits KeyMacroMove, StringMacroMove {

    MacroMoveDestination destination();

}

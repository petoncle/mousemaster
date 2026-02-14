package mousemaster;

public sealed interface ResolvedMacroMove permits ResolvedKeyMacroMove, StringMacroMove {

    MacroMoveDestination destination();

}

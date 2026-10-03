package mousemaster;

public sealed interface Command {

    record SwitchMode(String modeName) implements Command {}

    record StartMoveUp() implements Command {}
    record StartMoveDown() implements Command {}
    record StartMoveLeft() implements Command {}
    record StartMoveRight() implements Command {}

    record StopMoveUp() implements Command {}
    record StopMoveDown() implements Command {}
    record StopMoveLeft() implements Command {}
    record StopMoveRight() implements Command {}

    record PressLeft() implements Command {}
    record PressMiddle() implements Command {}
    record PressRight() implements Command {}

    record ReleaseLeft() implements Command {}
    record ReleaseMiddle() implements Command {}
    record ReleaseRight() implements Command {}

    record ToggleLeft() implements Command {}
    record ToggleMiddle() implements Command {}
    record ToggleRight() implements Command {}

    record StartWheelUp() implements Command {}
    record StartWheelDown() implements Command {}
    record StartWheelLeft() implements Command {}
    record StartWheelRight() implements Command {}

    record StopWheelUp() implements Command {}
    record StopWheelDown() implements Command {}
    record StopWheelLeft() implements Command {}
    record StopWheelRight() implements Command {}

    record SnapUp() implements Command {}
    record SnapDown() implements Command {}
    record SnapLeft() implements Command {}
    record SnapRight() implements Command {}

    record ShrinkGridUp() implements Command {}
    record ShrinkGridDown() implements Command {}
    record ShrinkGridLeft() implements Command {}
    record ShrinkGridRight() implements Command {}

    record MoveGridUp() implements Command {}
    record MoveGridDown() implements Command {}
    record MoveGridLeft() implements Command {}
    record MoveGridRight() implements Command {}

    record MoveToGridCenter() implements Command {}

    record MoveToLastSelectedHint() implements Command {}

    record SavePosition(String positionHistoryName) implements Command {}
    record UnsavePosition(String positionHistoryName) implements Command {}
    record ClearPositionHistory(String positionHistoryName) implements Command {}
    record CycleNextPosition(String positionHistoryName) implements Command {}
    record CyclePreviousPosition(String positionHistoryName) implements Command {}

    record MacroCommand(Macro macro, AliasResolution aliasResolution) implements Command {
        @Override
        public String toString() {
            return "MacroCommand[" + macro.name() + "]";
        }
    }

    record BreakComboPreparation() implements Command {}

    record BreakMacro() implements Command {}

    record Noop(String name) implements Command {}

    record SelectHintKey() implements Command {}
    record UnselectHintKey() implements Command {}

    record MutateMode(String modeName, ModePropertyPath propertyPath, Object newPropertyValue, Combo combo) implements Command {
        @Override
        public boolean equals(Object o) {
            // Ignore combo.
            if (this == o) return true;
            if (!(o instanceof MutateMode m)) return false;
            return modeName.equals(m.modeName) &&
                   propertyPath.equals(m.propertyPath) &&
                   newPropertyValue.equals(m.newPropertyValue);
        }

        @Override
        public int hashCode() {
            int h = modeName.hashCode();
            h = 31 * h + propertyPath.hashCode();
            h = 31 * h + newPropertyValue.hashCode();
            return h;
        }

        @Override
        public String toString() {
            return "MutateMode[" + propertyPath + "=" + newPropertyValue + "]";
        }
    }

    record StartEffect(String effectName) implements Command {}
    record StopEffect(String effectName) implements Command {}

    record SetVariable(String variableName) implements Command {}
    record UnsetVariable(String variableName) implements Command {}
    record ResetVariables() implements Command {}

}

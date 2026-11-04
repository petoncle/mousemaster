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

    record StartMoveTargetUp() implements Command {}
    record StartMoveTargetDown() implements Command {}
    record StartMoveTargetLeft() implements Command {}
    record StartMoveTargetRight() implements Command {}

    record StopMoveTargetUp() implements Command {}
    record StopMoveTargetDown() implements Command {}
    record StopMoveTargetLeft() implements Command {}
    record StopMoveTargetRight() implements Command {}

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

    record ShrinkOverlayUp() implements Command {}
    record ShrinkOverlayDown() implements Command {}
    record ShrinkOverlayLeft() implements Command {}
    record ShrinkOverlayRight() implements Command {}

    record MoveOverlayToAreaAbove() implements Command {}
    record MoveOverlayToAreaBelow() implements Command {}
    record MoveOverlayToAreaLeft() implements Command {}
    record MoveOverlayToAreaRight() implements Command {}

    record MoveToTarget() implements Command {}
    record MoveToLastSelectedHint() implements Command {}
    record MoveToOverlayCenter() implements Command {}
    record MoveToCellCenter() implements Command {}
    record MoveToCellTop() implements Command {}
    record MoveToCellBottom() implements Command {}
    record MoveToCellLeft() implements Command {}
    record MoveToCellRight() implements Command {}

    record MoveTargetToMouse() implements Command {}
    record MoveTargetToLastSelectedHint() implements Command {}
    record MoveTargetToOverlayCenter() implements Command {}
    record MoveTargetToCellCenter() implements Command {}
    record MoveTargetToCellTop() implements Command {}
    record MoveTargetToCellBottom() implements Command {}
    record MoveTargetToCellLeft() implements Command {}
    record MoveTargetToCellRight() implements Command {}

    record SavePosition(String positionHistoryName) implements Command {}
    record UnsavePosition(String positionHistoryName) implements Command {}
    record ToggleSavePosition(String positionHistoryName) implements Command {}
    record ClearPositionHistory(String positionHistoryName) implements Command {}
    record CycleNextPosition(String positionHistoryName) implements Command {}
    record CyclePreviousPosition(String positionHistoryName) implements Command {}

    record StartAnimation(String animationName) implements Command {}
    record StopAnimation(String animationName) implements Command {}

    record MacroCommand(Macro macro, AliasResolution aliasResolution) implements Command {
        @Override
        public String toString() {
            return "MacroCommand[" + macro.name() + "]";
        }
    }

    record BreakComboPreparation() implements Command {}

    record BreakMacro() implements Command {}

    record Exit() implements Command {}

    record Restart() implements Command {}

    record Noop(String name) implements Command {}

    record SelectHintKey() implements Command {}
    record DeselectHintKey() implements Command {}
    record DeselectAllHintKeys() implements Command {}

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

    record SetVariable(String variableName) implements Command {}
    record UnsetVariable(String variableName) implements Command {}
    record ResetVariables() implements Command {}

}

package mousemaster;

public enum PressKeyEventProcessing {

    UNHANDLED_MUST_NOT_BE_EATEN,
    UNHANDLED_MUST_BE_EATEN,
    PART_OF_COMBO_SEQUENCE_MUST_NOT_BE_EATEN,
    PART_OF_COMBO_SEQUENCE_MUST_BE_EATEN,
    PART_OF_MUST_REMAIN_PRESSED_COMBO_PRECONDITION_ONLY_MUST_NOT_BE_EATEN, // "Only" means it is not part of a combo sequence (it is just part of a combo precondition).
    PART_OF_MUST_REMAIN_PRESSED_COMBO_PRECONDITION_ONLY_MUST_BE_EATEN,
    PART_OF_MUST_REMAIN_UNPRESSED_COMBO_PRECONDITION_ONLY_MUST_NOT_BE_EATEN,
    PART_OF_MUST_REMAIN_UNPRESSED_COMBO_PRECONDITION_ONLY_MUST_BE_EATEN,
    PART_OF_HINT_PREFIX_MUST_BE_EATEN,
    HINT_UNDO_MUST_BE_EATEN, UNSWALLOWED_HINT_END_MUST_BE_EATEN, SWALLOWED_HINT_END_MUST_BE_EATEN;

    public boolean mustBeEaten() {
        return this == PART_OF_COMBO_SEQUENCE_MUST_BE_EATEN ||
               this == PART_OF_HINT_PREFIX_MUST_BE_EATEN ||
               this == HINT_UNDO_MUST_BE_EATEN ||
               this == UNSWALLOWED_HINT_END_MUST_BE_EATEN ||
               this == SWALLOWED_HINT_END_MUST_BE_EATEN ||
               this == UNHANDLED_MUST_BE_EATEN ||
               this == PART_OF_MUST_REMAIN_PRESSED_COMBO_PRECONDITION_ONLY_MUST_BE_EATEN ||
               this == PART_OF_MUST_REMAIN_UNPRESSED_COMBO_PRECONDITION_ONLY_MUST_BE_EATEN;
    }

    public boolean handled() {
        return this == PART_OF_COMBO_SEQUENCE_MUST_NOT_BE_EATEN ||
               this == PART_OF_COMBO_SEQUENCE_MUST_BE_EATEN ||
               this == PART_OF_HINT_PREFIX_MUST_BE_EATEN ||
               this == HINT_UNDO_MUST_BE_EATEN ||
               this == UNSWALLOWED_HINT_END_MUST_BE_EATEN ||
               this == SWALLOWED_HINT_END_MUST_BE_EATEN ||
               this == PART_OF_MUST_REMAIN_PRESSED_COMBO_PRECONDITION_ONLY_MUST_NOT_BE_EATEN ||
               this == PART_OF_MUST_REMAIN_PRESSED_COMBO_PRECONDITION_ONLY_MUST_BE_EATEN;
    }

    public boolean isPartOfComboSequence() {
        return this == PART_OF_COMBO_SEQUENCE_MUST_NOT_BE_EATEN ||
               this == PART_OF_COMBO_SEQUENCE_MUST_BE_EATEN;
    }

    public boolean isPartOfMustRemainPressedComboPreconditionOnly() {
        return this ==
               PART_OF_MUST_REMAIN_PRESSED_COMBO_PRECONDITION_ONLY_MUST_NOT_BE_EATEN ||
               this == PART_OF_MUST_REMAIN_PRESSED_COMBO_PRECONDITION_ONLY_MUST_BE_EATEN;
    }

    public boolean isPartOfMustRemainUnpressedComboPreconditionOnly() {
        return this ==
               PART_OF_MUST_REMAIN_UNPRESSED_COMBO_PRECONDITION_ONLY_MUST_NOT_BE_EATEN ||
               this == PART_OF_MUST_REMAIN_UNPRESSED_COMBO_PRECONDITION_ONLY_MUST_BE_EATEN;
    }

    public boolean isPartOfCombo() {
        return isPartOfComboSequence() ||
               isPartOfMustRemainPressedComboPreconditionOnly() ||
               isPartOfMustRemainUnpressedComboPreconditionOnly();
    }

    public boolean isPartOfHintPrefix() {
        return this == PART_OF_HINT_PREFIX_MUST_BE_EATEN;
    }

    public boolean isHintUndo() {
        return this == HINT_UNDO_MUST_BE_EATEN;
    }

    public boolean isUnswallowedHintEnd() {
        return this == UNSWALLOWED_HINT_END_MUST_BE_EATEN;
    }

    public static PressKeyEventProcessing unhandledMustNotBeEaten() {
        return UNHANDLED_MUST_NOT_BE_EATEN;
    }

    public static PressKeyEventProcessing unhandledMustBeEaten() {
        return UNHANDLED_MUST_BE_EATEN;
    }

    public static PressKeyEventProcessing partOfComboSequence(boolean mustBeEaten) {
        return mustBeEaten ? PART_OF_COMBO_SEQUENCE_MUST_BE_EATEN :
                PART_OF_COMBO_SEQUENCE_MUST_NOT_BE_EATEN;
    }

    public static PressKeyEventProcessing partOfHintPrefix() {
        return PART_OF_HINT_PREFIX_MUST_BE_EATEN;
    }

    public static PressKeyEventProcessing hintUndo() {
        return HINT_UNDO_MUST_BE_EATEN;
    }

    public static PressKeyEventProcessing unswallowedHintEnd() {
        return UNSWALLOWED_HINT_END_MUST_BE_EATEN;
    }

    public static PressKeyEventProcessing swallowedHintEnd() {
        return SWALLOWED_HINT_END_MUST_BE_EATEN;
    }

    public static PressKeyEventProcessing partOfMustRemainPressedComboPreconditionOnlyMustNotBeEaten() {
        return PART_OF_MUST_REMAIN_PRESSED_COMBO_PRECONDITION_ONLY_MUST_NOT_BE_EATEN;
    }

    public static PressKeyEventProcessing partOfMustRemainPressedComboPreconditionOnlyMustBeEaten() {
        return PART_OF_MUST_REMAIN_PRESSED_COMBO_PRECONDITION_ONLY_MUST_BE_EATEN;
    }

    public static PressKeyEventProcessing partOfMustRemainUnpressedComboPreconditionOnlyMustNotBeEaten() {
        return PART_OF_MUST_REMAIN_UNPRESSED_COMBO_PRECONDITION_ONLY_MUST_NOT_BE_EATEN;
    }

    public static PressKeyEventProcessing partOfMustRemainUnpressedComboPreconditionOnlyMustBeEaten() {
        return PART_OF_MUST_REMAIN_UNPRESSED_COMBO_PRECONDITION_ONLY_MUST_BE_EATEN;
    }

}

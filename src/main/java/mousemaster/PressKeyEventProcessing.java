package mousemaster;

public enum PressKeyEventProcessing {

    UNHANDLED,
    PART_OF_COMBO_SEQUENCE_MUST_NOT_BE_EATEN,
    PART_OF_COMBO_SEQUENCE_MUST_BE_EATEN,
    PART_OF_COMPLETED_COMBO_SEQUENCE_MUST_NOT_BE_EATEN,
    PART_OF_COMPLETED_COMBO_SEQUENCE_MUST_BE_EATEN,
    PART_OF_PRESSED_COMBO_PRECONDITION_ONLY, // "Only" means it is not part of a combo sequence (it is just part of a combo precondition).
    PART_OF_UNPRESSED_COMBO_PRECONDITION_ONLY,
    PART_OF_HINT_PREFIX_MUST_BE_EATEN,
    HINT_UNDO_MUST_BE_EATEN, UNSWALLOWED_HINT_END_MUST_BE_EATEN, SWALLOWED_HINT_END_MUST_BE_EATEN;

    public boolean mustBeEaten() {
        return this == PART_OF_COMBO_SEQUENCE_MUST_BE_EATEN ||
               this == PART_OF_COMPLETED_COMBO_SEQUENCE_MUST_BE_EATEN ||
               this == PART_OF_HINT_PREFIX_MUST_BE_EATEN ||
               this == HINT_UNDO_MUST_BE_EATEN ||
               this == UNSWALLOWED_HINT_END_MUST_BE_EATEN ||
               this == SWALLOWED_HINT_END_MUST_BE_EATEN;
    }

    public boolean handled() {
        return this == PART_OF_COMBO_SEQUENCE_MUST_NOT_BE_EATEN ||
               this == PART_OF_COMBO_SEQUENCE_MUST_BE_EATEN ||
               this == PART_OF_COMPLETED_COMBO_SEQUENCE_MUST_BE_EATEN ||
               this == PART_OF_COMPLETED_COMBO_SEQUENCE_MUST_NOT_BE_EATEN ||
               this == PART_OF_HINT_PREFIX_MUST_BE_EATEN ||
               this == HINT_UNDO_MUST_BE_EATEN ||
               this == UNSWALLOWED_HINT_END_MUST_BE_EATEN ||
               this == SWALLOWED_HINT_END_MUST_BE_EATEN ||
               this == PART_OF_PRESSED_COMBO_PRECONDITION_ONLY;
    }

    public boolean isPartOfComboSequence() {
        return this == PART_OF_COMBO_SEQUENCE_MUST_NOT_BE_EATEN ||
               this == PART_OF_COMBO_SEQUENCE_MUST_BE_EATEN ||
               this == PART_OF_COMPLETED_COMBO_SEQUENCE_MUST_NOT_BE_EATEN ||
               this == PART_OF_COMPLETED_COMBO_SEQUENCE_MUST_BE_EATEN;
    }

    public boolean isPartOfComboSequenceMustBeEaten() {
        return this == PART_OF_COMBO_SEQUENCE_MUST_BE_EATEN;
    }

    public boolean isPartOfCompletedComboSequenceMustBeEaten() {
        return this == PART_OF_COMPLETED_COMBO_SEQUENCE_MUST_BE_EATEN;
    }

    public boolean isPartOfCompletedComboSequence() {
        return this == PART_OF_COMPLETED_COMBO_SEQUENCE_MUST_BE_EATEN ||
               this == PART_OF_COMPLETED_COMBO_SEQUENCE_MUST_NOT_BE_EATEN;
    }

    public boolean isPartOfPressedComboPreconditionOnly() {
        return this == PART_OF_PRESSED_COMBO_PRECONDITION_ONLY;
    }

    public boolean isPartOfUnpressedComboPreconditionOnly() {
        return this == PART_OF_UNPRESSED_COMBO_PRECONDITION_ONLY;
    }

    public boolean isPartOfCombo() {
        return isPartOfComboSequence() ||
               isPartOfPressedComboPreconditionOnly() ||
               isPartOfUnpressedComboPreconditionOnly();
    }

    public boolean isUnswallowedHintEnd() {
        return this == UNSWALLOWED_HINT_END_MUST_BE_EATEN;
    }

    public boolean isSwallowedHintEnd() {
        return this == SWALLOWED_HINT_END_MUST_BE_EATEN;
    }

    public boolean isPartOfHintPrefix() {
        return this == PART_OF_HINT_PREFIX_MUST_BE_EATEN;
    }

    public boolean isHintEnd() {
        return isUnswallowedHintEnd() || isSwallowedHintEnd();
    }

    public boolean isHint() {
        return isHintEnd() || isPartOfHintPrefix() || this == HINT_UNDO_MUST_BE_EATEN;
    }

    public static PressKeyEventProcessing unhandled() {
        return UNHANDLED;
    }

    public static PressKeyEventProcessing partOfComboSequence(boolean mustBeEaten,
                                                              boolean completedCombo) {
        if (completedCombo)
            return mustBeEaten ? PART_OF_COMPLETED_COMBO_SEQUENCE_MUST_BE_EATEN :
                    PART_OF_COMPLETED_COMBO_SEQUENCE_MUST_NOT_BE_EATEN;
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

    public static PressKeyEventProcessing partOfPressedComboPreconditionOnly() {
        return PART_OF_PRESSED_COMBO_PRECONDITION_ONLY;
    }

    public static PressKeyEventProcessing partOfUnpressedComboPreconditionOnly() {
        return PART_OF_UNPRESSED_COMBO_PRECONDITION_ONLY;
    }

}

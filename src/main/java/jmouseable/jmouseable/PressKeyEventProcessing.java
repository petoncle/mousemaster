package jmouseable.jmouseable;

public enum PressKeyEventProcessing {

    UNHANDLED,
    PART_OF_COMBO_SEQUENCE_MUST_NOT_BE_EATEN,
    PART_OF_COMBO_SEQUENCE_MUST_BE_EATEN,
    PART_OF_COMBO_PRECONDITION_ONLY, // "Only" means it is not part of a combo sequence (it is just part of a combo precondition).
    PART_OF_HINT_MUST_BE_EATEN;

    public boolean mustBeEaten() {
        return this == PART_OF_COMBO_SEQUENCE_MUST_BE_EATEN || this == PART_OF_HINT_MUST_BE_EATEN;
    }

    public boolean handled() {
        return this == PART_OF_COMBO_SEQUENCE_MUST_NOT_BE_EATEN ||
               this == PART_OF_COMBO_SEQUENCE_MUST_BE_EATEN ||
               this == PART_OF_HINT_MUST_BE_EATEN ||
               this == PART_OF_COMBO_PRECONDITION_ONLY;
    }

    public boolean isPartOfComboSequence() {
        return this == PART_OF_COMBO_SEQUENCE_MUST_NOT_BE_EATEN ||
               this == PART_OF_COMBO_SEQUENCE_MUST_BE_EATEN;
    }

    public boolean isPartOfComboPreconditionOnly() {
        return this == PART_OF_COMBO_PRECONDITION_ONLY;
    }

    public boolean isPartOfCombo() {
        return isPartOfComboSequence() || isPartOfComboPreconditionOnly();
    }

    public static PressKeyEventProcessing unhandled() {
        return UNHANDLED;
    }

    public static PressKeyEventProcessing partOfComboSequence(boolean mustBeEaten) {
        return mustBeEaten ? PART_OF_COMBO_SEQUENCE_MUST_BE_EATEN :
                PART_OF_COMBO_SEQUENCE_MUST_NOT_BE_EATEN;
    }

    public static PressKeyEventProcessing partOfHint() {
        return PART_OF_HINT_MUST_BE_EATEN;
    }

    public static PressKeyEventProcessing partOfComboPreconditionOnly() {
        return PART_OF_COMBO_PRECONDITION_ONLY;
    }

}

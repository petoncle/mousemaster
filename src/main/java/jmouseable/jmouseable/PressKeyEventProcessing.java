package jmouseable.jmouseable;

public enum PressKeyEventProcessing {

    NOT_HANDLED,
    PART_OF_COMBO_MUST_NOT_BE_EATEN,
    PART_OF_COMBO_MUST_BE_EATEN,
    PART_OF_HINT_MUST_BE_EATEN;

    public boolean mustBeEaten() {
        return this == PART_OF_COMBO_MUST_BE_EATEN || this == PART_OF_HINT_MUST_BE_EATEN;
    }

    public boolean handled() {
        return this == PART_OF_COMBO_MUST_NOT_BE_EATEN ||
               this == PART_OF_COMBO_MUST_BE_EATEN ||
               this == PART_OF_HINT_MUST_BE_EATEN;
    }

    public boolean isPartOfCombo() {
        return this == PART_OF_COMBO_MUST_NOT_BE_EATEN ||
               this == PART_OF_COMBO_MUST_BE_EATEN;
    }

    public static PressKeyEventProcessing unhandled() {
        return NOT_HANDLED;
    }

    public static PressKeyEventProcessing partOfCombo(boolean mustBeEaten) {
        return mustBeEaten ? PART_OF_COMBO_MUST_BE_EATEN : PART_OF_COMBO_MUST_NOT_BE_EATEN;
    }

    public static PressKeyEventProcessing partOfHint() {
        return PART_OF_HINT_MUST_BE_EATEN;
    }

}

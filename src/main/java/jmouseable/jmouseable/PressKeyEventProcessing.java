package jmouseable.jmouseable;

public enum PressKeyEventProcessing {

    NOT_PART_OF_COMBO,
    PART_OF_COMBO_MUST_NOT_BE_EATEN,
    PART_OF_COMBO_MUST_BE_EATEN;

    public boolean mustBeEaten() {
        return this == PART_OF_COMBO_MUST_BE_EATEN;
    }

    public boolean partOfCombo() {
        return this == PART_OF_COMBO_MUST_NOT_BE_EATEN ||
               this == PART_OF_COMBO_MUST_BE_EATEN;
    }

    public static PressKeyEventProcessing of(boolean partOfCombo, boolean mustBeEaten) {
        if (!partOfCombo)
            return NOT_PART_OF_COMBO;
        return mustBeEaten ? PART_OF_COMBO_MUST_BE_EATEN :
                PART_OF_COMBO_MUST_NOT_BE_EATEN;
    }

}

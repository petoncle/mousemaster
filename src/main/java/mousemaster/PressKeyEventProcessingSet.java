package mousemaster;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record PressKeyEventProcessingSet(
        Map<Combo, PressKeyEventProcessing> processingByCombo) {

    /**
     * Used for hints and unknown combo (combo of another mode, for which the key is a precondition key).
     */
    public static final Combo dummyCombo = new Combo(new ComboPrecondition(
            new ComboPrecondition.ComboKeyPrecondition(Set.of(), Set.of()),
            new ComboPrecondition.ComboAppPrecondition(Set.of(), Set.of())),
            new ComboSequence(List.of()));

    public boolean mustBeEaten() {
        return processingByCombo.values().stream().anyMatch(
                PressKeyEventProcessing::mustBeEaten);
    }

    public boolean handled() {
        return processingByCombo.values()
                                .stream()
                                .anyMatch(PressKeyEventProcessing::handled);
    }

    public boolean isPartOfComboSequence() {
        return processingByCombo.values()
                                .stream()
                                .anyMatch(PressKeyEventProcessing::isPartOfComboSequence);
    }

    public boolean isPartOfCompletedComboSequenceMustBeEaten() {
        return processingByCombo.values().stream()
                                .anyMatch(
                                        PressKeyEventProcessing::isPartOfCompletedComboSequenceMustBeEaten);
    }

    public boolean isPartOfCompletedComboSequence() {
        return processingByCombo.values().stream()
                                .anyMatch(
                                        PressKeyEventProcessing::isPartOfCompletedComboSequence);
    }

    public Set<Combo> completedCombos() {
        Set<Combo> completedCombos = new HashSet<>();
        for (Map.Entry<Combo, PressKeyEventProcessing> entry : processingByCombo.entrySet()) {
            if (entry.getValue().isPartOfCompletedComboSequence()) {
                completedCombos.add(entry.getKey());
            }
        }
        return completedCombos;
    }

    public boolean isPartOfMustRemainPressedComboPreconditionOnly() {
        return processingByCombo.values().stream()
                                .anyMatch(
                                        PressKeyEventProcessing::isPartOfMustRemainPressedComboPreconditionOnly);
    }

    public boolean isPartOfMustRemainUnpressedComboPreconditionOnly() {
        return processingByCombo.values()
                                .stream()
                                .anyMatch(
                                        PressKeyEventProcessing::isPartOfMustRemainUnpressedComboPreconditionOnly);
    }

    public boolean isPartOfCombo() {
        return processingByCombo.values()
                                .stream()
                                .anyMatch(PressKeyEventProcessing::isPartOfCombo);
    }

    public boolean isUnswallowedHintEnd() {
        return processingByCombo.values()
                                .stream()
                                .anyMatch(PressKeyEventProcessing::isUnswallowedHintEnd);
    }

}

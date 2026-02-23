package mousemaster;

import mousemaster.ComboPrecondition.PressedKeyPrecondition;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record PressKeyEventProcessingSet(
        Map<Combo, PressKeyEventProcessing> processingByCombo,
        Map<Combo, ComboSequenceMatch> matchByCombo) {

    /**
     * Used for hints and unknown combo (combo of another mode, for which the key is a precondition key).
     */
    public static final Combo dummyCombo = new Combo(new ComboPrecondition(
            new ComboPrecondition.ComboKeyPrecondition(Set.of(),
                    new PressedKeyPrecondition(List.of())),
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

    public boolean isPartOfCompletedComboSequenceAndMustBeEaten() {
        return processingByCombo.values().stream()
                                .anyMatch(
                                        PressKeyEventProcessing::isPartOfCompletedComboSequenceAndMustBeEaten);
    }

    public boolean isPartOfCompletedComboSequence() {
        return processingByCombo.values().stream()
                                .anyMatch(
                                        PressKeyEventProcessing::isPartOfCompletedComboSequence);
    }

    public List<ComboAndMatch> partOfCompletedComboSequenceCombosWithMatches() {
        List<ComboAndMatch> result = new java.util.ArrayList<>();
        for (Map.Entry<Combo, PressKeyEventProcessing> entry : processingByCombo.entrySet()) {
            if (entry.getValue().isPartOfCompletedComboSequence()) {
                Combo combo = entry.getKey();
                ComboSequenceMatch match = matchByCombo.getOrDefault(combo, ComboSequenceMatch.noMatch());
                result.add(new ComboAndMatch(combo, match));
            }
        }
        return result;
    }

    public boolean isPartOfPressedComboPreconditionOnly() {
        return processingByCombo.values().stream()
                                .anyMatch(
                                        PressKeyEventProcessing::isPartOfPressedComboPreconditionOnly);
    }

    public boolean isPartOfUnpressedComboPreconditionOnly() {
        return processingByCombo.values()
                                .stream()
                                .anyMatch(
                                        PressKeyEventProcessing::isPartOfUnpressedComboPreconditionOnly);
    }

    public boolean isPartOfCombo() {
        return processingByCombo.values()
                                .stream()
                                .anyMatch(PressKeyEventProcessing::isPartOfCombo);
    }

    public boolean isIgnoredByLeadingWait() {
        return processingByCombo.values()
                                .stream()
                                .anyMatch(PressKeyEventProcessing::isIgnoredByLeadingWait);
    }

    public boolean isComboPreparationBreaker() {
        return processingByCombo.values()
                                .stream()
                                .anyMatch(PressKeyEventProcessing::isComboPreparationBreaker);
    }

    public boolean isPartOfHintPrefix() {
        return processingByCombo.values()
                                .stream()
                                .anyMatch(PressKeyEventProcessing::isPartOfHintPrefix);
    }

    public boolean isHintEnd() {
        return processingByCombo.values()
                                .stream()
                                .anyMatch(PressKeyEventProcessing::isHintEnd);
    }

    public boolean isUnswallowedHintEnd() {
        return processingByCombo.values()
                                .stream()
                                .anyMatch(PressKeyEventProcessing::isUnswallowedHintEnd);
    }

    public boolean isHint() {
        return processingByCombo.values()
                                .stream()
                                .anyMatch(PressKeyEventProcessing::isHint);
    }

}

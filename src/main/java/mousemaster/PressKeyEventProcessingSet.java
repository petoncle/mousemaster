package mousemaster;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record PressKeyEventProcessingSet(
        Map<Combo, PressKeyEventProcessing> processingByCombo,
        Map<Combo, MatchResult> matchResultByCombo) {

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

    public List<ComboWithMatchResult> partOfCompletedComboSequenceCombosWithMatchResults() {
        List<ComboWithMatchResult> result = new java.util.ArrayList<>();
        for (Map.Entry<Combo, PressKeyEventProcessing> entry : processingByCombo.entrySet()) {
            if (entry.getValue().isPartOfCompletedComboSequence()) {
                Combo combo = entry.getKey();
                MatchResult matchResult = matchResultByCombo.getOrDefault(combo, MatchResult.noMatch());
                result.add(new ComboWithMatchResult(combo, matchResult));
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

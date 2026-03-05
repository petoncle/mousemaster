package mousemaster;

import mousemaster.ComboMove.WaitComboMove;
import mousemaster.ResolvedKeyComboMove.ResolvedPressComboMove;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mousemaster.ComboPrecondition.PressedKeyGroup;
import mousemaster.ComboPrecondition.PressedKeyPrecondition;
import mousemaster.MoveSet.KeyMoveSet;
import mousemaster.MoveSet.WaitMoveSet;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ComboWatcher implements ModeListener {

    private static final Logger logger = LoggerFactory.getLogger(ComboWatcher.class);

    private final CommandRunner commandRunner;
    private final HintManager hintManager;
    private final ActiveAppFinder activeAppFinder;
    private final PlatformClock clock;
    private final Set<Key> pressedComboPreconditionKeys;
    private final boolean logRedactKeys;
    private final Set<Key> unpressedComboPreconditionKeys;
    private List<ComboListener> listeners;
    private Mode currentMode;
    private boolean modeJustTimedOut;
    private ComboPreparation comboPreparation;
    private PressKeyEventProcessingSet lastProcessingSet;
    private ComboMoveDuration previousComboMoveDuration;
    /**
     * Union of ignored wait moves across all partially-matching combos (they do not reset the preparation).
     * An event is ignored if it is matched by the wait in any matching combo.
     */
    private List<WaitComboMove> ignoredWaitMoves;
    private List<ComboWaitingForLastMoveToComplete> combosWaitingForLastMoveToComplete = new ArrayList<>();
    /**
     * Combos that have fired from combosWaitingForLastMoveToComplete and should
     * not re-fire until a non-absorbed key event arrives (for the combo's
     * trailing wait). Prevents absorbing trailing waits from causing re-firing.
     */
    private Set<Combo> combosBlockedFromRerunningCommand = new HashSet<>();
    private List<Command> commandsWaitingForAtomicCommandToComplete = new ArrayList<>();

    private Set<Key> currentlyPressedCompletedComboKeys = new HashSet<>();
    private Set<Key> currentlyPressedComboKeys = new HashSet<>();
    private KeyEvent lastKeyEvent;
    /**
     * Last event time per key, used to find the last non-ignored event time
     * when initializing a wait's begin time.
     */
    private final Map<Key, Instant> lastEventTimeByKey = new HashMap<>();
    private Instant lastPressEventTime;
    private Instant lastReleaseEventTime;
    /**
     * Per combo with a leading wait, the time the wait began. Reset when a non-ignored key event occurs.
     */
    private Map<Combo, Instant> leadingWaitBeginTimeByCombo = new HashMap<>();
    private App lastActiveApp;

    public ComboWatcher(CommandRunner commandRunner, HintManager hintManager,
                        ActiveAppFinder activeAppFinder,
                        PlatformClock clock,
                        Set<Key> unpressedComboPreconditionKeys,
                        Set<Key> pressedComboPreconditionKeys, boolean logRedactKeys) {
        this.commandRunner = commandRunner;
        this.hintManager = hintManager;
        this.activeAppFinder = activeAppFinder;
        this.clock = clock;
        this.unpressedComboPreconditionKeys =
                unpressedComboPreconditionKeys;
        this.pressedComboPreconditionKeys =
                pressedComboPreconditionKeys;
        this.logRedactKeys = logRedactKeys;
        this.comboPreparation = ComboPreparation.empty();
    }

    public void setListeners(List<ComboListener> listeners) {
        this.listeners = listeners;
    }

    public record ComboWatcherUpdateResult(List<ComboAndMatch> completedCombos,
                                           boolean preparationIsNotPrefixAnymore,
                                           boolean hasComboPreparationBreaker,
                                           Key comboPreparationBreakerKey) {

    }

    public ComboWatcherUpdateResult update(double delta) {
        List<ComboAndCommands> completedComboAndCommands = new ArrayList<>();
        // Handle combos that should be run when active app changes (no combo move).
        App activeApp = activeAppFinder.activeApp();
        boolean activeAppChanged = !Objects.equals(activeApp, lastActiveApp);
        if (activeAppChanged) {
            lastActiveApp = activeApp;
            for (Map.Entry<Combo, List<Command>> entry : currentMode.comboMap()
                                                                    .commandsByCombo()
                                                                    .entrySet()) {
                Combo combo = entry.getKey();
                if (!combo.precondition().appPrecondition().satisfied(activeApp))
                    continue;
                if (!combo.sequence().isEmpty())
                    continue;
                List<ResolvedKeyComboMove> noMatchedKeyMoves = List.of();
                Set<Key> currentlyPressedKeys = currentlyPressedComboKeys;
                if (findSatisfiedPressedPreconditionKeys(combo, currentlyPressedKeys,
                            currentlyPressedCompletedComboKeys, noMatchedKeyMoves, Set.of()) == null)
                    continue;
                if (!comboUnpressedPreconditionSatisfied(combo, currentlyPressedComboKeys, noMatchedKeyMoves))
                    continue;
                completedComboAndCommands.add(
                        new ComboAndCommands(combo, entry.getValue(), ComboSequenceMatch.noMatch()));
            }
        }
        boolean preparationIsNotPrefixAnymore = false;
        if (lastProcessingSet != null) {
            // Check if the preparation is still a prefix of at least one combo.
            // If it is not, then it means a key is being pressed for longer than what the combo expects,
            // and the key can be regurgitated (just like it is regurgitated upon key release).
            // (Regurgitate only +key, not #key.)
            boolean atLeastOneProcessingIsPartOfComboSequence = false;
            boolean preparationIsStillPrefixOfAtLeastOneCombo = false;
            Instant currentTime = clock.now();
            for (var entry : lastProcessingSet.processingByCombo().entrySet()) {
                Combo combo = entry.getKey();
                PressKeyEventProcessing processing = entry.getValue();
                if (processing.isPartOfComboSequence()) {
                    atLeastOneProcessingIsPartOfComboSequence = true;
                    ComboSequenceMatch match = comboPreparation.match(combo.sequence());
                    if (match.hasMatch()) {
                        ResolvedKeyComboMove currentKeyMove = match.lastMatchedKeyMove();
                        KeyEvent lastMatchedEvent = comboPreparation.events()
                                .get(match.lastMatchedKeyMoveEventIndex());
                        // If the last matched MoveSet is an absorbing wait,
                        // use the wait's max duration instead of the move's.
                        ComboMoveDuration effectiveDuration = currentKeyMove.duration();
                        if (match.matchedMoveSetCount() > 0) {
                            MoveSet lastMatchedMoveSet = combo.sequence().moveSets()
                                    .get(match.matchedMoveSetCount() - 1);
                            if (lastMatchedMoveSet instanceof WaitMoveSet waitMoveSet
                                && waitMoveSet.canAbsorbEvents()) {
                                WaitComboMove wm = waitMoveSet.waitMove();
                                effectiveDuration = new ComboMoveDuration(
                                        effectiveDuration.min(), wm.duration().max());
                            }
                            else if (lastMatchedMoveSet instanceof KeyMoveSet keyMoveSet
                                     && keyMoveSet.canAbsorbEvents()) {
                                effectiveDuration = new ComboMoveDuration(
                                        effectiveDuration.min(),
                                        keyMoveSet.waitMove().duration().max());
                            }
                        }
                        boolean tooMuch = effectiveDuration.tooMuchTimeHasPassed(
                                lastMatchedEvent.time(), currentTime);
                        if (!tooMuch) {
                            preparationIsStillPrefixOfAtLeastOneCombo = true;
                        }
                    }
                }
            }
            if (atLeastOneProcessingIsPartOfComboSequence && !preparationIsStillPrefixOfAtLeastOneCombo) {
                preparationIsNotPrefixAnymore = true;
                comboPreparation = ComboPreparation.empty();
                leadingWaitBeginTimeByCombo.clear();
                combosBlockedFromRerunningCommand.clear();
                lastProcessingSet = null;
            }
        }
        // For a given waiting combo, we know that its precondition has to be satisfied still, because otherwise it
        // would mean that currentlyPressedComboPreconditionKeys has changed. But when currentlyPressedComboPreconditionKeys is changed,
        // combosWaitingForLastMoveToComplete is always reset.
        List<ComboWaitingForLastMoveToComplete> completedCombosWaitingForLastMoveToComplete = new ArrayList<>();
        Key comboPreparationBreakerKey = null;
        // Remove waiting combos that don't belong to the current mode.
        combosWaitingForLastMoveToComplete.removeIf(
                comboWaitingForLastMoveToComplete -> !comboWaitingForLastMoveToComplete.comboMode.equals(
                        currentMode));
        for (ComboWaitingForLastMoveToComplete comboWaitingForLastMoveToComplete : combosWaitingForLastMoveToComplete) {
            comboWaitingForLastMoveToComplete.remainingWait -= delta;
            if (comboWaitingForLastMoveToComplete.remainingWait < 0) {
                completedCombosWaitingForLastMoveToComplete.add(comboWaitingForLastMoveToComplete);
                Combo combo = comboWaitingForLastMoveToComplete.comboAndCommands.combo;
                ComboSequenceMatch waitingMatch = comboWaitingForLastMoveToComplete.comboAndCommands.match;
                if (waitingMatch.hasMatch() &&
                    comboWaitingForLastMoveToComplete.comboAndCommands.commands.stream()
                                                                               .anyMatch(
                                                                                       Command.BreakComboPreparation.class::isInstance))
                    comboPreparationBreakerKey = waitingMatch.lastMatchedKeyMove().key();
                addCurrentlyPressedCompletedComboKeys(combo, currentlyPressedComboKeys,
                        comboWaitingForLastMoveToComplete.comboAndCommands.match);
                // We tell KeyboardManager that a combo was completed,
                // and all the currently pressed keys are part of a completed combo,
                // and they should not be regurgitated.

                completedComboAndCommands.add(comboWaitingForLastMoveToComplete.comboAndCommands);
                combosBlockedFromRerunningCommand.add(combo);
            }
        }
        // Handle bare/first wait-X combos (no preceding key-event moves).
        for (Map.Entry<Combo, List<Command>> entry : currentMode.comboMap()
                                                                .commandsByCombo()
                                                                .entrySet()) {
            Combo combo = entry.getKey();
            if (!combo.precondition().appPrecondition().satisfied(activeAppFinder.activeApp()))
                continue;
            ComboSequence comboSequence = combo.sequence();
            if (comboSequence.isEmpty())
                continue;
            // Check: are all MoveSets wait moves (no key moves)?
            boolean allWait = comboSequence.moveSets().stream()
                    .allMatch(ms -> ms instanceof WaitMoveSet);
            if (!allWait)
                continue;
            // Preconditions must be satisfied.
            Set<Key> currentlyPressedKeys = currentlyPressedComboKeys;
            List<ResolvedKeyComboMove> noMatchedKeyMoves = List.of();
            if (findSatisfiedPressedPreconditionKeys(combo, currentlyPressedKeys,
                    currentlyPressedCompletedComboKeys, noMatchedKeyMoves, Set.of()) == null)
                continue;
            if (!comboUnpressedPreconditionSatisfied(combo, currentlyPressedComboKeys, noMatchedKeyMoves))
                continue;
            // Begin time: last non-ignored key event time, or now if none.
            WaitComboMove waitMove = ((WaitMoveSet) comboSequence.moveSets().getFirst()).waitMove();
            Instant currentTime = clock.now();
            Instant waitBeginTime = leadingWaitBeginTimeByCombo.computeIfAbsent(
                    combo, k -> lastNonIgnoredEventTime(waitMove, currentTime));
            if (waitBeginTime.plus(waitMove.duration().min()).isAfter(currentTime))
                continue;
            if (waitMove.duration().max() != null &&
                waitBeginTime.plus(waitMove.duration().max()).isBefore(currentTime))
                continue;
            if (combosBlockedFromRerunningCommand.contains(combo))
                continue;
            completedComboAndCommands.add(
                    new ComboAndCommands(combo, entry.getValue(), ComboSequenceMatch.noMatch()));
            combosBlockedFromRerunningCommand.add(combo);
        }
        if (!completedComboAndCommands.isEmpty()) {
            listeners.forEach(ComboListener::completedCombo);
        }
        List<Command> commandsToRun = new ArrayList<>(commandsWaitingForAtomicCommandToComplete);
        commandsWaitingForAtomicCommandToComplete.clear();
        List<Command> completedCombosCommands = longestComboCommandsLastAndDeduplicate(completedComboAndCommands);
        commandsToRun.addAll(completedCombosCommands);
        List<ComboAndMatch> completedCombos =
                completedComboAndCommands.stream()
                                         .map(cac -> new ComboAndMatch(cac.combo, cac.match))
                                         .collect(Collectors.toCollection(ArrayList::new));
        if (!completedCombosCommands.isEmpty()) {
            logger.debug(
                    "Completed asynchronous combos, currentMode = " +
                    currentMode.name() + ", completedCombos = " +
                    completedCombos.stream().map(ComboAndMatch::combo).toList() +
                    ", commandsToRun = " + commandsToRun);
        }
        boolean hasComboPreparationBreaker =
                // Can be from a combo finishing with a wait move.
                commandsToRun.stream()
                             .anyMatch(Command.BreakComboPreparation.class::isInstance);
        Mode beforeMode = currentMode;
        runCommands(commandsToRun);
        combosWaitingForLastMoveToComplete.removeAll(completedCombosWaitingForLastMoveToComplete);
        if (currentMode != beforeMode && !hasComboPreparationBreaker) {
            PressKeyEventProcessingSet processingSet =
                    processKeyEventForCurrentMode(null, true);
            completedCombos.addAll(processingSet.partOfCompletedComboSequenceCombosWithMatches());
        }
        return new ComboWatcherUpdateResult(completedCombos, preparationIsNotPrefixAnymore, hasComboPreparationBreaker, comboPreparationBreakerKey);
    }

    public PressKeyEventProcessingSet keyEvent(KeyEvent event) {
        lastKeyEvent = event;
        // Update wait begin times: only reset for non-ignored key events.
        for (Map.Entry<Combo, Instant> entry : leadingWaitBeginTimeByCombo.entrySet()) {
            Combo combo = entry.getKey();
            WaitComboMove waitMove = ((WaitMoveSet) combo.sequence().moveSets().getFirst()).waitMove();
            if (waitMove.matchesEvent(event))
                continue;
            boolean allWait = combo.sequence().moveSets().stream()
                    .allMatch(ms -> ms instanceof WaitMoveSet);
            if (allWait) {
                // All-wait combos: always reset (they fire continuously from update()).
                logger.debug("Resetting leading wait (all-wait, non-ignored key " + event.key().name() +
                        "): " + combo);
                entry.setValue(event.time());
            }
            else {
                // Leading wait followed by event-based moves: only reset if the
                // wait hasn't elapsed yet (key interrupts the countdown).
                // If min has elapsed, only let through keys that could match a
                // subsequent move. Reset for all others (unrelated keys).
                Instant beginTime = entry.getValue();
                if (beginTime.plus(waitMove.duration().min()).isAfter(event.time())) {
                    logger.debug("Resetting leading wait (min not elapsed, non-ignored key " +
                            event.key().name() + "): " + combo);
                    entry.setValue(event.time());
                }
                else {
                    // Only let through keys that could match the first
                    // event-based MoveSet after the leading wait.
                    boolean couldMatchNextMove = false;
                    for (MoveSet ms : combo.sequence().moveSets()) {
                        if (ms instanceof WaitMoveSet)
                            continue;
                        KeyMoveSet keyMs = (KeyMoveSet) ms;
                        couldMatchNextMove = keyMs.requiredMoves().stream()
                                .anyMatch(m -> m.keyOrAlias().matchesKey(event.key())) ||
                                keyMs.optionalMoves().stream()
                                .anyMatch(m -> m.keyOrAlias().matchesKey(event.key()));
                        break;
                    }
                    if (!couldMatchNextMove) {
                        logger.debug("Resetting leading wait (unrelated key " +
                                event.key().name() + "): " + combo);
                        entry.setValue(event.time());
                    }
                }
            }
        }
        modeJustTimedOut = false;
        boolean isUnpressedComboPreconditionKey =
                unpressedComboPreconditionKeys.contains(event.key());
        boolean isPressedComboPreconditionKey =
                pressedComboPreconditionKeys.contains(event.key());
        boolean isComboPreconditionKey =
                isUnpressedComboPreconditionKey ||
                isPressedComboPreconditionKey;
        if (event.isRelease()) {
            // The corresponding press event was either part of a combo sequence or part of a combo precondition,
            // otherwise this method would not have been called.
            currentlyPressedCompletedComboKeys.remove(event.key());
            currentlyPressedComboKeys.remove(event.key());
        }
        else {
            if (isComboPreconditionKey) {
                currentlyPressedComboKeys.add(event.key());
            }
        }
        if (!combosWaitingForLastMoveToComplete.isEmpty()) {
            combosWaitingForLastMoveToComplete.removeIf(waiting -> {
                WaitComboMove waitMove = waiting.lastWaitMove();
                if (waitMove == null)
                    return true; // Non-wait waiting combos: any key cancels.
                return !waitMove.matchesEvent(event);
            });
        }
        // Clear combos blocked from rerunning whose trailing wait does not absorb this key.
        // idle-mode.macro.tap1=#!{f1}-250 +f1 #!{f1}-0-250 -f1 #!{f1}-250 -> +b -b
        if (!combosBlockedFromRerunningCommand.isEmpty()) {
            combosBlockedFromRerunningCommand.removeIf(combo -> {
                MoveSet lastMoveSet = combo.sequence().moveSets().getLast();
                if (!(lastMoveSet instanceof WaitMoveSet waitMoveSet))
                    return true;
                return !waitMoveSet.waitMove().matchesEvent(event);
            });
        }
        KeyEvent previousEvent = comboPreparation.events().isEmpty() ? null :
                comboPreparation.events().getLast();
        if (previousEvent != null &&
            previousComboMoveDuration != null) {
            // If there are ignored keys (from a trailing wait),
            // and this key is ignored in all matching combos, don't reset the preparation.
            boolean skipDurationCheck = ignoredWaitMoves != null &&
                    ignoredWaitMoves.stream().anyMatch(wm -> wm.matchesEvent(event));
            if (!skipDurationCheck &&
                !previousComboMoveDuration.satisfied(previousEvent.time(), event.time())) {
                comboPreparation = ComboPreparation.empty();
                leadingWaitBeginTimeByCombo.clear();
                combosBlockedFromRerunningCommand.clear();
            }
        }
        comboPreparation.events().add(event);
        Mode beforeMode = currentMode;
        PressKeyEventProcessingSet processingSet =
                processKeyEventForCurrentMode(event, false);
        if (currentMode != beforeMode && !processingSet.isComboPreparationBreaker()) {
            // Second pass to give a chance to new mode's combos to run now.
            PressKeyEventProcessingSet secondPass = processKeyEventForCurrentMode(event, true);
            processingSet.processingByCombo().putAll(secondPass.processingByCombo());
            processingSet.matchByCombo().putAll(secondPass.matchByCombo());
        }
        if (!processingSet.isPartOfComboSequence()) {
            boolean couldMatchOptional = event.isPress() &&
                                         keyPressCouldMatchOptionalMoveInFirstKeyMoveSet(
                                                 event.key());
            boolean couldBePartOfOptionalTap =
                    keyCouldBePartOfOptionalTapInAnyCombo(event.key());
            boolean ignoredByKeyMoveSet = keyEventIgnoredByFirstKeyMoveSetInAnyCombo(event);
            if (!couldMatchOptional && !couldBePartOfOptionalTap && !ignoredByKeyMoveSet) {
                comboPreparation = ComboPreparation.empty();
                leadingWaitBeginTimeByCombo.clear();
                combosBlockedFromRerunningCommand.clear();
            }
            // Check if the event is ignored by a leading wait in any combo.
            // Track whether at least one such wait eats events (+{...}).
            boolean isIgnoredByLeadingWait = false;
            boolean leadingWaitEatsEvents = false;
            if (event.isPress()) {
                for (Combo combo : leadingWaitBeginTimeByCombo.keySet()) {
                    WaitComboMove waitMove = ((WaitMoveSet) combo.sequence().moveSets().getFirst()).waitMove();
                    if (waitMove.matchesEvent(event)) {
                        isIgnoredByLeadingWait = true;
                        if (waitMove.ignoredKeysEatEvents())
                            leadingWaitEatsEvents = true;
                    }
                }
            }
            if (event.isPress() && (isComboPreconditionKey || isIgnoredByLeadingWait)) {
                // We don't really need to know which combo(s) this is for, that is why
                // we use dummyCombo instead. But it would be cleaner if we knew the combos.
                PressKeyEventProcessing processing;
                // Leading wait always takes priority over precondition keys:
                // the key is specifically part of a leading wait's ignored set,
                // so it should be treated as handled (not as an unrelated
                // precondition key from another combo).
                if (isIgnoredByLeadingWait)
                    processing = PressKeyEventProcessing.ignoredByLeadingWait(leadingWaitEatsEvents);
                else // isComboPreconditionKey must be true
                    processing = isPressedComboPreconditionKey ?
                            PressKeyEventProcessing.partOfPressedComboPreconditionOnly() :
                            PressKeyEventProcessing.partOfUnpressedComboPreconditionOnly();
                processingSet = new PressKeyEventProcessingSet(
                        new HashMap<>(Map.of(PressKeyEventProcessingSet.dummyCombo, processing)),
                        new HashMap<>());
            }
        }
        else {
            if (event.isPress())
                currentlyPressedComboKeys.add(event.key());
        }
        // Update lastEventTimeByKey after all processing, so that
        // lastNonIgnoredEventTime() during processKeyEventForCurrentMode
        // reflects the state before the current event arrived.
        lastEventTimeByKey.put(event.key(), event.time());
        if (event.isPress())
            lastPressEventTime = event.time();
        else
            lastReleaseEventTime = event.time();
        lastProcessingSet = processingSet;
        return processingSet;
    }

    private PressKeyEventProcessingSet processKeyEventForCurrentMode(
            KeyEvent event,
            boolean ignoreSwitchModeAndHintCommands) {
        long before = System.nanoTime();
        Mode beforeCommandsMode = currentMode;
        Map<Combo, PressKeyEventProcessing> processingByCombo = new HashMap<>();
        Map<Combo, ComboSequenceMatch> matchByCombo = new HashMap<>();
        List<ComboAndCommands> comboAndCommandsToRun = new ArrayList<>();
        Set<Key> currentlyPressedKeys = new HashSet<>(currentlyPressedComboKeys);
        if (event != null && event.isPress())
            currentlyPressedKeys.add(event.key());
        ComboMoveDuration newComboDuration = null;
        // Union of ignored wait moves across all partially-matching combos.
        List<WaitComboMove> newIgnoredWaitMoves = null;
        for (Map.Entry<Combo, List<Command>> entry : currentMode.comboMap()
                                                                .commandsByCombo()
                                                                .entrySet()) {
            // When a precondition key is pressed, and another key is pressed,
            // that other key should be processed only for combos that
            // contains the pressed precondition key.
            Combo combo = entry.getKey();
            if (!combo.precondition()
                      .appPrecondition()
                      .satisfied(activeAppFinder.activeApp()))
                continue;
            if (!combo.precondition().appPrecondition().isEmpty() && combo.sequence().isEmpty())
                continue; // Active app changes are handled in ComboWatcher#update.
            // Bare wait combos (all MoveSets are wait) are handled in update().
            if (!combo.sequence().isEmpty() &&
                combo.sequence().moveSets().stream().allMatch(ms -> ms instanceof WaitMoveSet))
                continue;
            // Leading wait: skip until the wait duration has elapsed.
            if (!combo.sequence().isEmpty() &&
                combo.sequence().moveSets().getFirst() instanceof WaitMoveSet) {
                // If the combo's precondition is not currently satisfied,
                // remove stale wait entry and skip. This ensures the wait
                // restarts fresh when the precondition is re-satisfied.
                if (!combo.precondition().keyPrecondition().pressedKeyPrecondition().isEmpty()) {
                    boolean anySatisfied = combo.precondition().keyPrecondition()
                            .pressedKeyPrecondition().groups().stream()
                            .anyMatch(g -> currentlyPressedKeys.containsAll(g.allKeys()));
                    if (!anySatisfied) {
                        if (leadingWaitBeginTimeByCombo.remove(combo) != null)
                            logger.debug("Removing leading wait (pressed precondition unsatisfied): " + combo);
                        continue;
                    }
                }
                if (combo.precondition().keyPrecondition().unpressedKeySet().stream()
                        .anyMatch(currentlyPressedKeys::contains)) {
                    if (leadingWaitBeginTimeByCombo.remove(combo) != null)
                        logger.debug("Removing leading wait (unpressed precondition unsatisfied): " + combo);
                    continue;
                }
                WaitComboMove leadingWait =
                        ((WaitMoveSet) combo.sequence().moveSets().getFirst()).waitMove();
                Instant now = clock.now();
                // Fallback: no prior non-ignored events means the wait is
                // trivially satisfied (no interrupting keys ever occurred).
                // Use now minus min duration so beginTime + min <= now.
                Instant noEventsFallback = now.minus(leadingWait.duration().min());
                Instant beginTime = leadingWaitBeginTimeByCombo.computeIfAbsent(combo,
                        k -> lastNonIgnoredEventTime(leadingWait, noEventsFallback));
                if (beginTime.plus(leadingWait.duration().min()).isAfter(now))
                    continue;
                if (leadingWait.duration().max() != null &&
                    beginTime.plus(leadingWait.duration().max()).isBefore(now)) {
                    leadingWaitBeginTimeByCombo.remove(combo);
                    logger.debug("Removing leading wait (max expired): " + combo);
                    continue;
                }
            }
            ComboSequenceMatch match = comboPreparation.match(combo.sequence());
            ResolvedKeyComboMove currentKeyMove = match.hasMatch() ?
                    match.lastMatchedKeyMove() : null;
            // If the combo has no explicit pressed precondition, we ignore the
            // currently pressed keys: the combo fires regardless of what's held.
            // Only combos with an explicit _{...} precondition check pressed keys.
            if (!combo.precondition().keyPrecondition().pressedKeyPrecondition().isEmpty() &&
                findSatisfiedPressedPreconditionKeys(combo, currentlyPressedKeys,
                        currentlyPressedCompletedComboKeys, match.matchedKeyMoves(),
                        match.absorbedPressedKeys()) == null) {
                // Then it's as if the currently pressed precondition key is an unhandled key:
                // other keys that are pressed should not even be considered but passed onto other apps.
                // logger.info("currentlyPressedPressedComboKeysNotPartOfAlreadyCompletedCombo = " +
                //             currentlyPressedPressedComboKeysNotPartOfAlreadyCompletedCombo +
                //             ", skipping combo: " + combo);
                continue;
            }
            if (!comboUnpressedPreconditionSatisfied(combo, currentlyPressedComboKeys, match.matchedKeyMoves())) {
                continue;
            }
            boolean mustBeEaten = false;
            boolean partOfComboSequence = false;
            if (match.hasMatch()) {
                boolean currentKeyMoveMustBeEaten =
                        currentKeyMove instanceof ResolvedPressComboMove pressMove &&
                        pressMove.eventMustBeEaten();
                mustBeEaten = currentKeyMoveMustBeEaten;
                partOfComboSequence = true;
                // Compute effective duration for this combo's match,
                // including any absorbing wait override.
                ComboMoveDuration effectiveDuration = currentKeyMove.duration();
                List<MoveSet> moveSets = combo.sequence().moveSets();
                MoveSet lastMatchedMoveSet = match.matchedMoveSetCount() > 0 ?
                        moveSets.get(match.matchedMoveSetCount() - 1) : null;
                if (lastMatchedMoveSet instanceof WaitMoveSet waitMoveSet
                    && waitMoveSet.canAbsorbEvents()) {
                    WaitComboMove wm = waitMoveSet.waitMove();
                    effectiveDuration = new ComboMoveDuration(
                            effectiveDuration.min(), wm.duration().max());
                    if (match.lastEventAbsorbedByWait()) {
                        mustBeEaten = wm.ignoredKeysEatEvents();
                    }
                }
                else if (lastMatchedMoveSet instanceof KeyMoveSet keyMoveSet
                         && keyMoveSet.canAbsorbEvents()) {
                    effectiveDuration = new ComboMoveDuration(
                            effectiveDuration.min(),
                            keyMoveSet.waitMove().duration().max());
                    if (match.lastEventAbsorbedByWait()) {
                        mustBeEaten = keyMoveSet.waitMove().ignoredKeysEatEvents();
                    }
                }
                // Partially-matched MoveSet: the event was absorbed by the
                // MoveSet currently being partially matched (at index
                // matchedMoveSetCount, not matchedMoveSetCount - 1).
                else if (match.lastEventAbsorbedByWait()
                         && match.matchedMoveSetCount() < moveSets.size()) {
                    MoveSet partialMoveSet = moveSets.get(match.matchedMoveSetCount());
                    if (partialMoveSet instanceof KeyMoveSet partialKeyMoveSet
                        && partialKeyMoveSet.canAbsorbEvents()) {
                        mustBeEaten = partialKeyMoveSet.waitMove().ignoredKeysEatEvents();
                        effectiveDuration = new ComboMoveDuration(
                                effectiveDuration.min(),
                                partialKeyMoveSet.waitMove().duration().max());
                    }
                }
                // Merge this combo's effective duration into the accumulated
                // duration using smallest min and largest max (null = no limit).
                if (newComboDuration == null) {
                    newComboDuration = effectiveDuration;
                }
                else {
                    Duration min = newComboDuration.min().compareTo(effectiveDuration.min()) > 0 ?
                            effectiveDuration.min() : newComboDuration.min();
                    Duration max;
                    if (newComboDuration.max() == null || effectiveDuration.max() == null)
                        max = null;
                    else
                        max = newComboDuration.max().compareTo(effectiveDuration.max()) < 0 ?
                                effectiveDuration.max() : newComboDuration.max();
                    newComboDuration = new ComboMoveDuration(min, max);
                }
                // Compute ignored wait move from the last matched or partially-matched MoveSet.
                // If it's an absorbing MoveSet, its wait move should not reset the preparation.
                WaitComboMove comboIgnoredWaitMove;
                if (lastMatchedMoveSet instanceof WaitMoveSet waitMoveSet2
                    && waitMoveSet2.canAbsorbEvents()) {
                    comboIgnoredWaitMove = waitMoveSet2.waitMove();
                }
                else if (lastMatchedMoveSet instanceof KeyMoveSet keyMoveSet2
                         && keyMoveSet2.canAbsorbEvents()) {
                    comboIgnoredWaitMove = keyMoveSet2.waitMove();
                }
                else if (match.lastEventAbsorbedByWait()
                         && match.matchedMoveSetCount() < moveSets.size()
                         && moveSets.get(match.matchedMoveSetCount()) instanceof KeyMoveSet partialKms
                         && partialKms.canAbsorbEvents()) {
                    comboIgnoredWaitMove = partialKms.waitMove();
                }
                else {
                    comboIgnoredWaitMove = null;
                }
                // Union: an event is ignored if matched by any combo's wait move.
                if (comboIgnoredWaitMove != null) {
                    if (newIgnoredWaitMoves == null)
                        newIgnoredWaitMoves = new ArrayList<>();
                    newIgnoredWaitMoves.add(comboIgnoredWaitMove);
                }
            }
            boolean preparationComplete = match.complete();
            ResolvedKeyComboMove comboLastMove = match.lastMatchedKeyMove();
            boolean lastMoveIsWaitingMove = comboLastMove != null &&
                                        !comboLastMove.duration().min().equals(Duration.ZERO);
            // Check if the last MoveSet in the sequence is a WaitMoveSet.
            boolean lastMoveSetIsWait = !combo.sequence().isEmpty() &&
                    combo.sequence().moveSets().getLast() instanceof WaitMoveSet;
            if (event != null) {
                if (partOfComboSequence) {
                    boolean comboPreparationBreaker = entry.getValue()
                                                           .stream()
                                                           .anyMatch(
                                                                   Command.BreakComboPreparation.class::isInstance);
                    PressKeyEventProcessing processing =
                            PressKeyEventProcessing.partOfComboSequence(mustBeEaten,
                                    preparationComplete && !lastMoveIsWaitingMove && !lastMoveSetIsWait,
                                    comboPreparationBreaker);
                    // This processingByCombo does not need to have entries about
                    // non-combo sequences (i.e. combo preconditions).
                    // That is because preconditions are managed by the caller (keyEvent)
                    // which checks across all modes, not just the current one. (isComboPreconditionKey)
                    processingByCombo.put(combo, processing);
                    matchByCombo.put(combo, match);
                }
            }
            if (!preparationComplete)
                continue;
            if (combosBlockedFromRerunningCommand.contains(combo))
                continue;
            if (lastMoveSetIsWait &&
                combo.sequence().moveSets().getLast() instanceof WaitMoveSet lastWaitMoveSet) {
                // Wait as last move: all event-based moves matched, now wait.
                WaitComboMove waitMove = lastWaitMoveSet.waitMove();
                List<Command> commands = entry.getValue();
                ComboAndCommands comboAndCommands = new ComboAndCommands(combo, commands, match);
                combosWaitingForLastMoveToComplete.add(
                        new ComboWaitingForLastMoveToComplete(currentMode, comboAndCommands,
                                waitMove.duration().min().toNanos() / 1e9d));
            }
            else if (lastMoveIsWaitingMove) {
                List<Command> commands = entry.getValue();
                ComboAndCommands comboAndCommands = new ComboAndCommands(combo, commands, match);
                combosWaitingForLastMoveToComplete.add(
                        new ComboWaitingForLastMoveToComplete(currentMode, comboAndCommands,
                                comboLastMove.duration().min().toNanos() / 1e9d));
            }
            else {
                List<Command> commands = entry.getValue();
                // We never want to execute (un)select hint key if the key event just
                // switched the mode to a hint mode.
                Predicate<Command> switchModeOrHintPredicate =
                        c -> c instanceof Command.SwitchMode ||
                             c instanceof Command.SelectHintKey ||
                             c instanceof Command.UnselectHintKey;
                if (ignoreSwitchModeAndHintCommands &&
                    commands.stream()
                            .anyMatch(switchModeOrHintPredicate)) {
                    logger.debug(
                            "Ignoring the following commands because the mode was just changed to " +
                            currentMode.name() + ": " + commands.stream()
                                                                .filter(switchModeOrHintPredicate)
                                                                .toList());
                    commands = commands.stream()
                                       .filter(Predicate.not(
                                               switchModeOrHintPredicate))
                                       .toList();
                }
                ComboAndCommands comboAndCommands = new ComboAndCommands(combo, commands, match);
                comboAndCommandsToRun.add(comboAndCommands);
            }
        }
        if (newComboDuration != null) {
            previousComboMoveDuration = newComboDuration;
            ignoredWaitMoves = newIgnoredWaitMoves;
        }
        List<Command> commandsToRun = new ArrayList<>(commandsWaitingForAtomicCommandToComplete);
        commandsWaitingForAtomicCommandToComplete.clear();
        List<Command> completeCombosCommandsToRun =
                longestComboCommandsLastAndDeduplicate(comboAndCommandsToRun);
        commandsToRun.addAll(completeCombosCommandsToRun);
        PressKeyEventProcessingSet processingSet =
                new PressKeyEventProcessingSet(processingByCombo, matchByCombo);
        long processKeyEventDurationMs = (long) ((System.nanoTime() - before) / 1e6);
        List<String> comboLabels = processingByCombo.entrySet().stream()
                                                    .filter(e -> e.getValue()
                                                                  .isPartOfComboSequence())
                                                    .map(e -> e.getKey().label())
                                                    .toList();
        logger.debug("processKeyEventForCurrentMode ran in " + processKeyEventDurationMs +
                     "ms, mode = " + beforeCommandsMode.name() +
                     ", comboCount = " + beforeCommandsMode.comboMap().commandsByCombo().size() +
                     ", event = " + (logRedactKeys ? "<redacted>" : event) +
                     ", currentlyPressedComboKeys = " + (logRedactKeys ? "<redacted>" : currentlyPressedComboKeys) +
                     ", comboPreparation = " + (logRedactKeys ? "<redacted>" : comboPreparation.toString()) +
                     ", partOfComboSequence = " + processingSet.isPartOfComboSequence() +
                     ", combos = " + comboLabels +
                     ", mustBeEaten = " + processingSet.mustBeEaten() + ", commandsToRun = " +
                     completeCombosCommandsToRun);
        if (!comboAndCommandsToRun.isEmpty()) {
            listeners.forEach(ComboListener::completedCombo);
        }
        runCommands(commandsToRun);
        boolean atLeastOneComboCompleted = !comboAndCommandsToRun.isEmpty();
        if (atLeastOneComboCompleted) {
            for (ComboAndCommands comboAndCommands : comboAndCommandsToRun) {
                addCurrentlyPressedCompletedComboKeys(comboAndCommands.combo,
                        currentlyPressedKeys, comboAndCommands.match);
            }
        }
        return processingSet;
    }


    private void runCommands(List<Command> commandsToRun) {
        if (commandsToRun.isEmpty())
            return;
        Key lastEventKey = lastKeyEvent == null ? null : lastKeyEvent.key();
        List<Command> commands = new ArrayList<>(commandsToRun);
        while (!commands.isEmpty() && !commandRunner.runningAtomicCommand()) {
            Command command = commands.removeFirst();
            commandRunner.run(command, lastEventKey);
            if (hintManager.pollLastHintCommandSupercedesOtherCommands())
                return;
        }
        // Run SwitchMode commands now to avoid losing key events meant for the next mode.
        for (int commandIndex = 0; commandIndex < commands.size(); commandIndex++) {
            Command command = commands.get(commandIndex);
            if (command instanceof Command.SelectHintKey) {
                commandRunner.run(command, lastEventKey);
                commands.remove(commandIndex);
                commandIndex--;
                if (hintManager.pollLastHintCommandSupercedesOtherCommands())
                    return;
            }
            if (command instanceof Command.BreakComboPreparation ||
                command instanceof Command.SwitchMode) {
                commandRunner.run(command, lastEventKey);
                commands.remove(commandIndex);
                commandIndex--;
                if (hintManager.pollLastHintCommandSupercedesOtherCommands())
                    return;
            }
        }
        commandsWaitingForAtomicCommandToComplete.addAll(commands);
    }

    private Set<Key> findSatisfiedPressedPreconditionKeys(Combo combo,
                                                       Set<Key> currentlyPressedKeys,
                                                       Set<Key> currentlyPressedCompletedComboKeys,
                                                       List<ResolvedKeyComboMove> matchedKeyMoves,
                                                       Set<Key> absorbedPressedKeys) {
        PressedKeyPrecondition precondition = combo.precondition()
                                                   .keyPrecondition()
                                                   .pressedKeyPrecondition();
        // If the combo has a leading wait, keys ignored by that wait
        // could have been pressed before the combo started. Remove them
        // from candidates since they are unrelated to this combo.
        WaitComboMove leadingWait = null;
        if (!combo.sequence().isEmpty() &&
            combo.sequence().moveSets().getFirst() instanceof WaitMoveSet leadingWaitMoveSet)
            leadingWait = leadingWaitMoveSet.waitMove();
        // If the combo has a trailing wait, keys ignored by that wait
        // could have been pressed during the wait period. Remove them
        // from candidates since they are unrelated to this combo.
        WaitComboMove trailingWait = null;
        if (!combo.sequence().isEmpty() &&
            combo.sequence().moveSets().getLast() instanceof WaitMoveSet trailingWaitMoveSet)
            trailingWait = trailingWaitMoveSet.waitMove();
        List<PressedKeyGroup> groups = precondition.groups();
        if (groups.isEmpty())
            groups = List.of(new PressedKeyGroup(List.of()));
        for (PressedKeyGroup group : groups) {
            Set<Key> allGroupKeys = group.allKeys();
            // Start with currently pressed keys.
            Set<Key> candidatePressedPreconditionKeys = new HashSet<>(currentlyPressedKeys);
            // Remove keys that were absorbed by a wait (pressed during the wait, not before).
            candidatePressedPreconditionKeys.removeAll(absorbedPressedKeys);
            if (leadingWait != null)
                removeIgnoredPressedKeys(leadingWait, candidatePressedPreconditionKeys);
            if (trailingWait != null)
                removeIgnoredPressedKeys(trailingWait, candidatePressedPreconditionKeys);
            // Remove completed combo keys that are NOT in allGroupKeys.
            for (Key completedKey : currentlyPressedCompletedComboKeys) {
                if (!allGroupKeys.contains(completedKey))
                    candidatePressedPreconditionKeys.remove(completedKey);
            }
            // Reverse-apply matched key moves: remove pressed keys, add released keys.
            for (int i = matchedKeyMoves.size() - 1; i >= 0; i--) {
                ResolvedKeyComboMove move = matchedKeyMoves.get(i);
                if (move.isPress())
                    candidatePressedPreconditionKeys.remove(move.key());
                else if (allGroupKeys.contains(move.key()))
                    // Only add back keys that are precondition keys to avoid polluting
                    // candidates with non-precondition keys.
                    candidatePressedPreconditionKeys.add(move.key());
            }
            // Remove keys that could match press moves (required or optional)
            // in the combo's sequence. These keys are "explained" by the combo
            // even if not yet matched (e.g. partial match of a multi-move MoveSet).
            // For fully matched moves, the reverse-apply step above already removed
            // them, so this is redundant but harmless for those.
            candidatePressedPreconditionKeys.removeIf(candidateKey ->
                    combo.sequence().moveSets().stream()
                            .filter(ms -> ms instanceof KeyMoveSet)
                            .map(ms -> (KeyMoveSet) ms)
                            .anyMatch(kms ->
                                    kms.requiredMoves().stream()
                                            .anyMatch(move -> (move.isPress() || move.isTap()) &&
                                                      optionalMoveMatchesKey(move, candidateKey)) ||
                                    kms.optionalMoves().stream()
                                            .anyMatch(move -> (move.isPress() || move.isTap()) &&
                                                      optionalMoveMatchesKey(move, candidateKey))));
            if (group.satisfiedBy(candidatePressedPreconditionKeys))
                return candidatePressedPreconditionKeys;
        }
        return null;
    }

    private boolean comboUnpressedPreconditionSatisfied(Combo combo,
                                                        Set<Key> currentlyPressedComboKeys,
                                                        List<ResolvedKeyComboMove> matchedKeyMoves) {
        for (Key unpressedPreconditionKey : combo.precondition()
                                                 .keyPrecondition()
                                                 .unpressedKeySet()) {
            if (currentlyPressedComboKeys.contains(unpressedPreconditionKey) &&
                !combo.keysPressedAfterMoves(Set.of(),
                        matchedKeyMoves).contains(unpressedPreconditionKey))
                return false;
        }
        return true;
    }

    private void addCurrentlyPressedCompletedComboKeys(Combo combo,
                                                       Set<Key> currentlyPressedKeys,
                                                       ComboSequenceMatch match) {
        Set<Key> satisfiedPressPreconditionKeys;
        if (combo.precondition().keyPrecondition().pressedKeyPrecondition().isEmpty()) {
            satisfiedPressPreconditionKeys = currentlyPressedKeys;
        }
        else {
            satisfiedPressPreconditionKeys =
                    findSatisfiedPressedPreconditionKeys(combo, currentlyPressedKeys,
                            currentlyPressedCompletedComboKeys,
                            match.matchedKeyMoves(), match.absorbedPressedKeys());
            if (satisfiedPressPreconditionKeys == null)
                throw new IllegalStateException();
        }
        Set<Key> keys =
                combo.keysPressedAfterMoves(
                        satisfiedPressPreconditionKeys,
                        match.matchedKeyMoves());
        // logger.info("Combo completed, pressed keys: " + keys);
        currentlyPressedCompletedComboKeys.addAll(keys);
    }

    private enum CommandOrder {
        RUN_FIRST,
        RUN_SECOND,
        RUN_LAST;
    }

    private static CommandOrder commandOrder(Command command) {
        // Hint commands are executed first because they can cancel other commands.
        // Run BreakComboPreparation last, just before SwitchMode.
        return switch (command) {
            case Command.SelectHintKey c -> CommandOrder.RUN_FIRST;
            case Command.UnselectHintKey c -> CommandOrder.RUN_FIRST;
            case Command.BreakComboPreparation c -> CommandOrder.RUN_LAST;
            case Command.SwitchMode c -> CommandOrder.RUN_LAST;
            default -> CommandOrder.RUN_SECOND;
        };
    }

    /**
     * Assuming the following configuration:
     * - +up: start move up
     * - -up|+up -up +up: stop move up
     * - +up -up +up: start wheel up
     * When up is pressed, the move starts. Then, when up is released then pressed,
     * the wheel starts.
     * The 3 combos are completed, but we ultimately want the move to stop,
     * i.e. the stop move command (+up -up +up) should be run after the
     * start move command (+up).
     * Longest combos "have the last word".
     * Also deduplicate commands: if start-move-up is +up|#rightctrl +up: holding rightctrl
     * then up should not trigger two commands.
     * - Move the Switch commands last: useful for saving a mouse position then switching to position-history mode
     */
    private List<Command> longestComboCommandsLastAndDeduplicate(List<ComboAndCommands> commandsToRun) {
        // Resolve macro aliases before deduplication: each macro command's
        // aliases are resolved using the alias bindings from its combo match.
        attachMacroAliasResolutions(commandsToRun);
        // Suppress combos whose matched key moves are a strict subset of
        // another completing combo's matched key moves.
        commandsToRun.removeIf(candidate -> {
            List<ResolvedKeyComboMove> candidateMoves = candidate.match.matchedKeyMoves();
            if (candidateMoves.isEmpty())
                return false;
            return commandsToRun.stream().anyMatch(other ->
                    other != candidate &&
                    other.match.matchedKeyMoves().size() > candidateMoves.size() &&
                    other.match.matchedKeyMoves().containsAll(candidateMoves));
        });
        return commandsToRun.stream()
                            .sorted(Comparator.comparingInt(
                                    cac -> cac.match.matchedKeyMoves().size()))
                            .map(ComboAndCommands::commands)
                            .flatMap(Collection::stream)
                            .distinct()
                            .sorted(Comparator.comparing(ComboWatcher::commandOrder))
                            .toList();
    }

    private static void attachMacroAliasResolutions(List<ComboAndCommands> comboAndCommandsList) {
        for (int i = 0; i < comboAndCommandsList.size(); i++) {
            ComboAndCommands cac = comboAndCommandsList.get(i);
            AliasResolution resolution = cac.match.aliasResolution();
            List<Command> resolvedCommands = new ArrayList<>();
            boolean changed = false;
            for (Command command : cac.commands) {
                if (command instanceof Command.MacroCommand(Macro macro, var __)) {
                    // Filter resolution to only aliases used in the macro output,
                    // so that combos with different input aliases but identical
                    // resolved output are deduplicated.
                    Set<String> usedAliases = macro.outputAliasNames();
                    Map<String, Key> filteredMap = new HashMap<>();
                    Map<String, Key> filteredNegatedMap = new HashMap<>();
                    Map<String, List<Key>> filteredTapMap = new HashMap<>();
                    for (String aliasName : usedAliases) {
                        Key key = resolution.keyByAliasName().get(aliasName);
                        if (key != null)
                            filteredMap.put(aliasName, key);
                        List<Key> tapKeys = resolution.keysByTapExpandedFromAlias().get(aliasName);
                        if (tapKeys != null)
                            filteredTapMap.put(aliasName, tapKeys);
                    }
                    for (String name : macro.outputNegatedNames()) {
                        Key key = resolution.negatedKeyByName().get(name);
                        if (key != null)
                            filteredNegatedMap.put(name, key);
                    }
                    AliasResolution filteredResolution = new AliasResolution(
                            filteredMap, filteredNegatedMap, filteredTapMap);
                    resolvedCommands.add(new Command.MacroCommand(macro, filteredResolution));
                    changed = true;
                }
                else {
                    resolvedCommands.add(command);
                }
            }
            if (changed) {
                comboAndCommandsList.set(i,
                        new ComboAndCommands(cac.combo, resolvedCommands, cac.match));
            }
        }
    }

    public void breakComboPreparation() {
        logger.debug("Breaking combos, comboPreparation = " +
                     (logRedactKeys ? "<redacted>" : comboPreparation.toString()) +
                     ", combosWaitingForLastMoveToComplete = " +
                     combosWaitingForLastMoveToComplete);
        comboPreparation = ComboPreparation.empty();
        combosWaitingForLastMoveToComplete.clear();
        leadingWaitBeginTimeByCombo.clear();
        combosBlockedFromRerunningCommand.clear();
        lastEventTimeByKey.clear();
        lastPressEventTime = null;
        lastReleaseEventTime = null;
        lastProcessingSet = null;
    }

    public void reset() {
        breakComboPreparation();
        // When a mode times out to a new mode, the currentlyPressedComboKeys should not be reset.
        currentlyPressedCompletedComboKeys.clear();
        currentlyPressedComboKeys.clear();
    }

    public void reset(Key comboPreparationBreakerKey) {
        breakComboPreparation();
        // KeyManager won't notify ComboWatcher of the release of the comboPreparationBreakerKey.
        currentlyPressedCompletedComboKeys.remove(comboPreparationBreakerKey);
        currentlyPressedComboKeys.remove(comboPreparationBreakerKey);
    }

    @Override
    public void modeChanged(Mode newMode) {
        currentMode = newMode;
        leadingWaitBeginTimeByCombo.clear();
        combosBlockedFromRerunningCommand.clear();
        if (modeJustTimedOut) {
            modeJustTimedOut = false;
            processKeyEventForCurrentMode(null, false);
        }
    }

    /**
     * Returns the time of the last non-ignored key event, or fallback if none found.
     * For PressWaitComboMove, ignored events are presses, so the last non-ignored is lastReleaseEventTime.
     * For ReleaseWaitComboMove, ignored events are releases, so the last non-ignored is lastPressEventTime.
     */
    private Instant lastNonIgnoredEventTime(WaitComboMove waitMove, Instant fallback) {
        return switch (waitMove) {
            case WaitComboMove.KeyWaitComboMove kwm -> {
                Instant latest = null;
                for (Map.Entry<Key, Instant> entry : lastEventTimeByKey.entrySet()) {
                    if (!kwm.ignoredKeySet().contains(entry.getKey()) &&
                        (latest == null || entry.getValue().isAfter(latest)))
                        latest = entry.getValue();
                }
                yield latest != null ? latest : fallback;
            }
            case WaitComboMove.PressWaitComboMove ignored ->
                    lastReleaseEventTime != null ? lastReleaseEventTime : fallback;
            case WaitComboMove.ReleaseWaitComboMove ignored ->
                    lastPressEventTime != null ? lastPressEventTime : fallback;
        };
    }

    @Override
    public void modeTimedOut() {
        modeJustTimedOut = true;
        breakComboPreparation();
    }

    /**
     * Returns true if the given key press could match an optional press move in
     * the first KeyMoveSet of any combo in the current mode. This is used to
     * avoid clearing the preparation when a key is pressed that doesn't satisfy
     * any required move but could be an optional part of an any-order MoveSet.
     */
    private boolean keyPressCouldMatchOptionalMoveInFirstKeyMoveSet(Key key) {
        for (Combo combo : currentMode.comboMap().commandsByCombo().keySet()) {
            if (combo.sequence().isEmpty())
                continue;
            MoveSet firstMoveSet = combo.sequence().moveSets().getFirst();
            if (!(firstMoveSet instanceof KeyMoveSet keyMoveSet))
                continue;
            for (ComboMove.KeyComboMove optMove : keyMoveSet.optionalMoves()) {
                if ((optMove.isPress() || optMove.isTap()) && optionalMoveMatchesKey(optMove, key))
                    return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the given key could match an optional tap move in the
     * first KeyMoveSet of any combo (skipping leading WaitMoveSets).
     */
    private boolean keyCouldBePartOfOptionalTapInAnyCombo(Key key) {
        for (Combo combo : currentMode.comboMap().commandsByCombo().keySet()) {
            if (combo.sequence().isEmpty())
                continue;
            for (MoveSet moveSet : combo.sequence().moveSets()) {
                if (moveSet instanceof WaitMoveSet)
                    continue;
                if (moveSet instanceof KeyMoveSet keyMoveSet) {
                    for (ComboMove.KeyComboMove optMove : keyMoveSet.optionalMoves()) {
                        if (optMove.isTap() && optionalMoveMatchesKey(optMove, key))
                            return true;
                    }
                    break; // Only check the first KeyMoveSet.
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the given event is ignored by the first absorbing
     * KeyMoveSet in any combo of the current mode, or could match one of
     * its moves. This prevents the preparation from being cleared.
     */
    private boolean keyEventIgnoredByFirstKeyMoveSetInAnyCombo(KeyEvent event) {
        for (Combo combo : currentMode.comboMap().commandsByCombo().keySet()) {
            if (combo.sequence().isEmpty())
                continue;
            MoveSet first = combo.sequence().moveSets().getFirst();
            if (!(first instanceof KeyMoveSet kms) || !kms.canAbsorbEvents())
                continue;
            if (kms.waitMove().matchesEvent(event))
                return true;
            if (ComboPreparation.anyMoveCouldMatchEvent(kms, event, Map.of(), Map.of()))
                return true;
        }
        return false;
    }

    /**
     * Removes pressed keys from candidates based on the wait move type.
     * KeyWaitComboMove: removes keys in the ignored key set.
     * PressWaitComboMove: all presses are ignored, so remove all candidates.
     * ReleaseWaitComboMove: only releases are ignored, pressed keys are fine.
     */
    private static void removeIgnoredPressedKeys(WaitComboMove waitMove, Set<Key> candidates) {
        switch (waitMove) {
            case WaitComboMove.KeyWaitComboMove kwm ->
                candidates.removeIf(kwm.ignoredKeySet()::contains);
            case WaitComboMove.PressWaitComboMove ignored -> candidates.clear();
            case WaitComboMove.ReleaseWaitComboMove ignored -> {} // no-op
        }
    }

    private static boolean optionalMoveMatchesKey(ComboMove.KeyComboMove optMove, Key key) {
        boolean matches = optMove.keyOrAlias().matchesKey(key);
        return optMove.negated() ? !matches : matches;
    }

     private static final class ComboWaitingForLastMoveToComplete {
         private final Mode comboMode;
         private final ComboAndCommands comboAndCommands;
         private double remainingWait;

         private ComboWaitingForLastMoveToComplete(Mode currentMode,
                                                   ComboAndCommands comboAndCommands,
                                                   double remainingWait) {
             comboMode = currentMode;
             this.comboAndCommands = comboAndCommands;
             this.remainingWait = remainingWait;
         }

         /** Returns the wait move if the last MoveSet is a wait, null otherwise. */
         WaitComboMove lastWaitMove() {
             MoveSet lastMoveSet = comboAndCommands.combo.sequence().moveSets().getLast();
             return lastMoveSet instanceof WaitMoveSet waitMoveSet ?
                     waitMoveSet.waitMove() : null;
         }

        public ComboAndCommands comboAndCommands() {
            return comboAndCommands;
        }

        @Override
        public String toString() {
            return "ComboWaitingForLastMoveToComplete[" + "comboAndCommands=" +
                   comboAndCommands + ", remainingWait=" + remainingWait + ']';
        }
    }

    private record ComboAndCommands(Combo combo, List<Command> commands,
                                    ComboSequenceMatch match) {
    }

}

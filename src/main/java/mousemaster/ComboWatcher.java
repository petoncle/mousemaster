package mousemaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mousemaster.ComboPrecondition.PressedKeyGroup;
import mousemaster.ComboPrecondition.PressedKeyPrecondition;

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
    private List<ComboWaitingForLastMoveToComplete> combosWaitingForLastMoveToComplete = new ArrayList<>();
    private List<Command> commandsWaitingForAtomicCommandToComplete = new ArrayList<>();

    private Set<Key> currentlyPressedCompletedComboKeys = new HashSet<>();
    private Set<Key> currentlyPressedComboKeys = new HashSet<>();
    private KeyEvent lastKeyEvent;
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
                List<ComboMove> noMatchedMoves = List.of();
                Set<Key> currentlyPressedKeys = currentlyPressedComboKeys;
                if (findSatisfiedPressedPreconditionKeys(combo, currentlyPressedKeys,
                            currentlyPressedCompletedComboKeys, noMatchedMoves) == null)
                    continue;
                if (!comboUnpressedPreconditionSatisfied(combo, currentlyPressedComboKeys, noMatchedMoves))
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
            boolean atLeastOneProcessingIsComboSequenceMustBeEaten = false;
            boolean preparationIsStillPrefixOfAtLeastOneCombo = false;
            Instant currentTime = clock.now();
            for (var entry : lastProcessingSet.processingByCombo().entrySet()) {
                Combo combo = entry.getKey();
                PressKeyEventProcessing processing = entry.getValue();
                if (processing.isPartOfComboSequenceMustBeEaten()) {
                    atLeastOneProcessingIsComboSequenceMustBeEaten = true;
                    ComboSequenceMatch match = comboPreparation.match(combo.sequence());
                    if (match.hasMatch()) {
                        ComboMove currentMove = match.lastMatchedMove();
                        KeyEvent currentKeyEvent = comboPreparation.events().getLast();
                        if (!currentMove.duration()
                                        .tooMuchTimeHasPassed(currentKeyEvent.time(),
                                                currentTime)) {
                            preparationIsStillPrefixOfAtLeastOneCombo = true;
                        }
                    }
                }
            }
            if (atLeastOneProcessingIsComboSequenceMustBeEaten && !preparationIsStillPrefixOfAtLeastOneCombo) {
                preparationIsNotPrefixAnymore = true;
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
                    comboPreparationBreakerKey = waitingMatch.lastMatchedMove().key();
                addCurrentlyPressedCompletedComboKeys(combo, currentlyPressedComboKeys,
                        comboWaitingForLastMoveToComplete.comboAndCommands.match);
                // We tell KeyboardManager that a combo was completed,
                // and all the currently pressed keys are part of a completed combo,
                // and they should not be regurgitated.

                completedComboAndCommands.add(comboWaitingForLastMoveToComplete.comboAndCommands);
            }
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
        Mode beforeMode = currentMode;
        runCommands(commandsToRun);
        combosWaitingForLastMoveToComplete.removeAll(completedCombosWaitingForLastMoveToComplete);
        if (currentMode != beforeMode && comboPreparationBreakerKey == null) {
            PressKeyEventProcessingSet processingSet =
                    processKeyEventForCurrentMode(null, true);
            completedCombos.addAll(processingSet.partOfCompletedComboSequenceCombosWithMatches());
        }
        return new ComboWatcherUpdateResult(completedCombos, preparationIsNotPrefixAnymore, comboPreparationBreakerKey);
    }

    public PressKeyEventProcessingSet keyEvent(KeyEvent event) {
        lastKeyEvent = event;
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
        if (!combosWaitingForLastMoveToComplete.isEmpty())
            combosWaitingForLastMoveToComplete.clear();
        KeyEvent previousEvent = comboPreparation.events().isEmpty() ? null :
                comboPreparation.events().getLast();
        if (previousEvent != null &&
            !previousComboMoveDuration.satisfied(previousEvent.time(), event.time()))
            comboPreparation = ComboPreparation.empty();
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
            comboPreparation = ComboPreparation.empty();
            if (event.isPress() && isComboPreconditionKey) {
                // We don't really need to know which combo(s) this is for, that is why
                // we use dummyCombo instead. But it would be cleaner if we knew the combos.
                // If we are here, it means the key is used as a precondition key,
                // possibly for a mode different from the current mode.
                processingSet = new PressKeyEventProcessingSet(
                        new HashMap<>(Map.of(PressKeyEventProcessingSet.dummyCombo,
                                isPressedComboPreconditionKey ?
                                        PressKeyEventProcessing.partOfPressedComboPreconditionOnly() :
                                        PressKeyEventProcessing.partOfUnpressedComboPreconditionOnly())),
                        new HashMap<>());
            }
        }
        else {
            if (event.isPress())
                currentlyPressedComboKeys.add(event.key());
        }
        lastProcessingSet = processingSet;
        return processingSet;
    }

    private PressKeyEventProcessingSet processKeyEventForCurrentMode(
            KeyEvent event,
            boolean ignoreSwitchModeAndHintCommands) {
//        logger.info("currentMode = " + currentMode.name() + ", processKeyEventForCurrentMode event " + event + ", ignoreSwitchModeCommands = " + ignoreSwitchModeCommands, new Throwable());
        long before = System.nanoTime();
        Map<Combo, PressKeyEventProcessing> processingByCombo = new HashMap<>();
        Map<Combo, ComboSequenceMatch> matchByCombo = new HashMap<>();
        List<ComboAndCommands> comboAndCommandsToRun = new ArrayList<>();
        Set<Key> currentlyPressedKeys = new HashSet<>(currentlyPressedComboKeys);
        if (event != null && event.isPress())
            currentlyPressedKeys.add(event.key());
        ComboMoveDuration newComboDuration = null;
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
            ComboSequenceMatch match = comboPreparation.match(combo.sequence());
            ComboMove currentMove = match.hasMatch() ?
                    match.lastMatchedMove() : null;
            // For release combos (like -up), we ignore the currently pressed keys:
            // even if there is a pressed key that is not in the combo precondition, we
            // still consider the combo as completed.
            if (!isReleaseCombo(combo, match.matchedMoves()) &&
                findSatisfiedPressedPreconditionKeys(combo, currentlyPressedKeys,
                        currentlyPressedCompletedComboKeys, match.matchedMoves()) == null) {
                // Then it's as if the currently pressed precondition key is an unhandled key:
                // other keys that are pressed should not even be considered but passed onto other apps.
                // logger.info("currentlyPressedPressedComboKeysNotPartOfAlreadyCompletedCombo = " +
                //             currentlyPressedPressedComboKeysNotPartOfAlreadyCompletedCombo +
                //             ", skipping combo: " + combo);
                continue;
            }
            if (!comboUnpressedPreconditionSatisfied(combo, currentlyPressedComboKeys, match.matchedMoves())) {
                continue;
            }
            boolean mustBeEaten = false;
            boolean partOfComboSequence = false;
            if (match.hasMatch()) {
                boolean currentMoveMustBeEaten =
                        currentMove instanceof ComboMove.PressComboMove pressComboMove &&
                        pressComboMove.eventMustBeEaten();
                mustBeEaten = currentMoveMustBeEaten;
                partOfComboSequence = true;
                if (newComboDuration == null) {
                    newComboDuration = currentMove.duration();
                }
                else {
                    if (newComboDuration.min().compareTo(currentMove.duration().min()) >
                        0)
                        newComboDuration =
                                new ComboMoveDuration(currentMove.duration().min(),
                                        newComboDuration.max());
                    if (newComboDuration.max() != null &&
                        (currentMove.duration().max() == null ||
                         newComboDuration.max().compareTo(currentMove.duration().max()) <
                         0))
                        newComboDuration = new ComboMoveDuration(newComboDuration.min(),
                                currentMove.duration().max());
                }
            }
            boolean preparationComplete = match.complete();
            ComboMove comboLastMove = match.lastMatchedMove();
            boolean lastMoveIsWaitingMove = comboLastMove != null &&
                                        !comboLastMove.duration().min().equals(Duration.ZERO);
            if (event != null) {
                if (partOfComboSequence) {
                    boolean comboPreparationBreaker = entry.getValue()
                                                           .stream()
                                                           .anyMatch(
                                                                   Command.BreakComboPreparation.class::isInstance);
                    PressKeyEventProcessing processing =
                            PressKeyEventProcessing.partOfComboSequence(mustBeEaten,
                                    preparationComplete && !lastMoveIsWaitingMove,
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
            if (lastMoveIsWaitingMove) {
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
        if (newComboDuration != null)
            previousComboMoveDuration = newComboDuration;
        List<Command> commandsToRun = new ArrayList<>(commandsWaitingForAtomicCommandToComplete);
        commandsWaitingForAtomicCommandToComplete.clear();
        List<Command> completeCombosCommandsToRun =
                longestComboCommandsLastAndDeduplicate(comboAndCommandsToRun);
        commandsToRun.addAll(completeCombosCommandsToRun);
        PressKeyEventProcessingSet processingSet =
                new PressKeyEventProcessingSet(processingByCombo, matchByCombo);
        logger.debug("currentMode = " + currentMode.name() +
                     ", currentlyPressedComboKeys = " + (logRedactKeys ? "<redacted>" : currentlyPressedComboKeys) +
                     ", comboPreparation = " +
                     (logRedactKeys ? "<redacted>" : comboPreparation.toString()) +
                     ", partOfComboSequence = " + processingSet.isPartOfComboSequence() +
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
        long processKeyEventDurationMs = (long) ((System.nanoTime() - before) / 1e6);
        if (processKeyEventDurationMs > 0)
            logger.info("processKeyEventForCurrentMode " + processKeyEventDurationMs +
                    "ms, mode=" + currentMode.name() +
                    ", comboCount=" + currentMode.comboMap().commandsByCombo().size() +
                    ", event=" + (logRedactKeys ? "<redacted>" : event));
        return processingSet;
    }

    private static boolean isReleaseCombo(Combo combo, List<ComboMove> matchedMoves) {
        return combo.precondition()
                    .keyPrecondition()
                    .pressedKeyPrecondition()
                    .isEmpty() &&
               (combo.sequence().isEmpty() ||
                (!matchedMoves.isEmpty() && matchedMoves.stream().allMatch(ComboMove::isRelease)));
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
                                                       List<ComboMove> matchedMoves) {
        PressedKeyPrecondition precondition = combo.precondition()
                                                   .keyPrecondition()
                                                   .pressedKeyPrecondition();
        List<PressedKeyGroup> groups = precondition.groups();
        if (groups.isEmpty())
            groups = List.of(new PressedKeyGroup(List.of()));
        for (PressedKeyGroup group : groups) {
            Set<Key> allGroupKeys = group.allKeys();
            // Start with currently pressed keys.
            Set<Key> candidatePressedPreconditionKeys = new HashSet<>(currentlyPressedKeys);
            // Remove completed combo keys that are NOT in allGroupKeys.
            for (Key completedKey : currentlyPressedCompletedComboKeys) {
                if (!allGroupKeys.contains(completedKey))
                    candidatePressedPreconditionKeys.remove(completedKey);
            }
            // Reverse-apply matched moves: remove pressed keys, add released keys.
            for (int i = matchedMoves.size() - 1; i >= 0; i--) {
                ComboMove move = matchedMoves.get(i);
                if (move.isPress())
                    candidatePressedPreconditionKeys.remove(move.key());
                else
                    candidatePressedPreconditionKeys.add(move.key());
            }
            if (group.satisfiedBy(candidatePressedPreconditionKeys))
                return candidatePressedPreconditionKeys;
        }
        return null;
    }

    private boolean comboUnpressedPreconditionSatisfied(Combo combo,
                                                        Set<Key> currentlyPressedComboKeys,
                                                        List<ComboMove> matchedMoves) {
        for (Key unpressedPreconditionKey : combo.precondition()
                                                 .keyPrecondition()
                                                 .unpressedKeySet()) {
            if (currentlyPressedComboKeys.contains(unpressedPreconditionKey) &&
                !combo.keysPressedAfterMoves(Set.of(),
                        matchedMoves).contains(unpressedPreconditionKey))
                return false;
        }
        return true;
    }

    private void addCurrentlyPressedCompletedComboKeys(Combo combo,
                                                       Set<Key> currentlyPressedKeys,
                                                       ComboSequenceMatch match) {
        if (isReleaseCombo(combo, match.matchedMoves()))
            return;
        Set<Key> satisfiedPressPreconditionKeys =
                findSatisfiedPressedPreconditionKeys(combo, currentlyPressedKeys,
                        currentlyPressedCompletedComboKeys,
                        match.matchedMoves());
        if (satisfiedPressPreconditionKeys == null)
            throw new IllegalStateException();
        Set<Key> keys =
                combo.keysPressedAfterMoves(
                        satisfiedPressPreconditionKeys,
                        match.matchedMoves());
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
        return commandsToRun.stream()
                            .sorted(Comparator.comparingInt(
                                    cac -> cac.match.matchedMoves().size()))
                            .map(ComboAndCommands::commands)
                            .flatMap(Collection::stream)
                            .distinct()
                            .sorted(Comparator.comparing(ComboWatcher::commandOrder))
                            .toList();
    }

    public void breakComboPreparation() {
        logger.debug("Breaking combos, comboPreparation = " +
                     (logRedactKeys ? "<redacted>" : comboPreparation.toString()) +
                     ", combosWaitingForLastMoveToComplete = " +
                     combosWaitingForLastMoveToComplete);
        comboPreparation = ComboPreparation.empty();
        combosWaitingForLastMoveToComplete.clear();
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
        if (modeJustTimedOut) {
            modeJustTimedOut = false;
            processKeyEventForCurrentMode(null, false);
        }
    }

    @Override
    public void modeTimedOut() {
        modeJustTimedOut = true;
        breakComboPreparation();
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

package mousemaster;

import io.qt.gui.QFontDatabase;
import mousemaster.FontStyle.FontStyleBuilder;
import mousemaster.GridArea.GridAreaType;
import mousemaster.GridConfiguration.GridConfigurationBuilder;
import mousemaster.HideCursor.HideCursorBuilder;
import mousemaster.HintGridArea.HintGridAreaType;
import mousemaster.HintGridLayout.HintGridLayoutBuilder;
import mousemaster.HintMeshConfiguration.HintMeshConfigurationBuilder;
import mousemaster.HintMeshKeys.HintMeshKeysBuilder;
import mousemaster.HintMeshStyle.HintMeshStyleBuilder;
import mousemaster.IndicatorConfiguration.IndicatorConfigurationBuilder;
import mousemaster.ModeTimeout.ModeTimeoutBuilder;
import mousemaster.Mouse.MouseBuilder;
import mousemaster.ViewportFilterMap.ViewportFilterMapBuilder;
import mousemaster.Wheel.WheelBuilder;
import mousemaster.ZoomConfiguration.ZoomConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static mousemaster.Command.*;
import static mousemaster.ViewportFilter.AnyViewportFilter;
import static mousemaster.ViewportFilter.FixedViewportFilter;

public class ConfigurationParser {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationParser.class);

    private static final Pattern propertyLinePattern = Pattern.compile("(.+?)=(.+)");
    private static final Map<String, Property<?>> defaultPropertyByName = defaultPropertyByName();

    private static Map<String, Property<?>> defaultPropertyByName() {
        AtomicReference<Boolean> pushModeToHistoryStack = new AtomicReference<>(false);
        AtomicReference<Boolean> stopCommandsFromPreviousMode = new AtomicReference<>(false);
        AtomicReference<String> modeAfterPressingUnhandledKeysOnly = new AtomicReference<>();
        MouseBuilder mouse = new MouseBuilder().initialVelocity(1600)
                                               .maxVelocity(2200)
                                               .acceleration(1500)
                                               .smoothJumpEnabled(true)
                                               .smoothJumpVelocity(30000);
        WheelBuilder wheel = new WheelBuilder().initialVelocity(1000).maxVelocity(1000).acceleration(500);
        GridConfigurationBuilder grid =
                new GridConfigurationBuilder();
        grid.synchronization(Synchronization.MOUSE_AND_GRID_CENTER_UNSYNCHRONIZED)
            .rowCount(2)
            .columnCount(2)
            .lineVisible(false)
            .lineHexColor("#FF0000")
            .lineThickness(1);
        GridArea.GridAreaBuilder gridAreaBuilder = grid.area();
        gridAreaBuilder.type(GridAreaType.ACTIVE_SCREEN)
                       .widthPercent(1)
                       .heightPercent(1)
                       .topInset(0)
                       .bottomInset(0)
                       .leftInset(0)
                       .rightInset(0);
        HintMeshConfigurationBuilder hintMesh =
                new HintMeshConfigurationBuilder();
        hintMesh.enabled(false)
                .visible(true)
                .mouseMovement(HintMouseMovement.MOUSE_FOLLOWS_SELECTED_HINT);
        hintMesh.keys(AnyViewportFilter.ANY_VIEWPORT_FILTER)
                .selectionKeys(IntStream.rangeClosed('a', 'z')
                                        .mapToObj(c -> String.valueOf((char) c))
                                        .map(Key::ofName)
                                        .toList())
                .rowKeyOffset(0);
        HintMeshStyleBuilder hintMeshStyleBuilder =
                hintMesh.style(AnyViewportFilter.ANY_VIEWPORT_FILTER);
        hintMeshStyleBuilder.fontStyle()
                            .name("Consolas")
                            .weight(FontWeight.NORMAL)
                            .size(18d)
                            .spacingPercent(0.7d)
                            .hexColor("#FFFFFF")
                            .opacity(1d)
                            .selectedFontHexColor("#CCCCCC")
                            .outlineThickness(0d)
                            .outlineHexColor("#000000")
                            .outlineOpacity(0.5d)
                            .shadowBlurRadius(10d)
                            .shadowHexColor("#FFFFFF")
                            .shadowOpacity(1d)
                            .shadowHorizontalOffset(0d)
                            .shadowVerticalOffset(0d);
        hintMeshStyleBuilder
                .prefixInBackground(false)
                .boxHexColor("#000000")
                .boxOpacity(0.3d)
                .boxBorderThickness(1d)
                .boxBorderLength(10_000d)
                .boxBorderHexColor("#FFFFFF")
                .boxBorderOpacity(0.4d)
                .prefixBoxEnabled(false)
                .prefixBoxBorderThickness(4d)
                .prefixBoxBorderLength(10_000d)
                .prefixBoxBorderHexColor("#FFD93D")
                .prefixBoxBorderOpacity(0.8d)
                .boxWidthPercent(1d)
                .boxHeightPercent(1d)
                .subgridRowCount(1)
                .subgridColumnCount(1)
                .subgridBorderThickness(1d)
                .subgridBorderLength(10_000d)
                .subgridBorderHexColor("#FFFFFF")
                .subgridBorderOpacity(1d)
                .transitionAnimationEnabled(true)
                .transitionAnimationDuration(Duration.ofMillis(100));
        hintMesh.eatUnusedSelectionKeys(true);
        HintMeshType.HintMeshTypeBuilder hintMeshTypeBuilder = hintMesh.type();
        hintMeshTypeBuilder.type(HintMeshType.HintMeshTypeType.GRID)
                           .gridLayout(AnyViewportFilter.ANY_VIEWPORT_FILTER)
                           .maxRowCount(200)
                           .maxColumnCount(200)
                           .cellWidth(73d)
                           .cellHeight(36d)
                           .layoutRowCount(1_000_000)
                           .layoutColumnCount(1)
                           .layoutRowOriented(true);
        HintGridArea.HintGridAreaBuilder hintGridAreaBuilder =
                hintMesh.type().gridArea();
        hintGridAreaBuilder.type(HintGridAreaType.ACTIVE_SCREEN)
                           .activeScreenHintGridAreaCenter(
                                   ActiveScreenHintGridAreaCenter.SCREEN_CENTER);
        ModeTimeoutBuilder timeout =
                new ModeTimeoutBuilder().enabled(false).onlyIfIdle(true);
        IndicatorConfigurationBuilder indicator =
                new IndicatorConfigurationBuilder();
        indicator.enabled(false)
                 .size(12)
                 .idleHexColor("#FF0000")
                 .moveHexColor("#FF0000")
                 .wheelHexColor("#FFFF00")
                 .mousePressHexColor("#00FF00");
        HideCursorBuilder hideCursor =
                new HideCursorBuilder().enabled(false).idleDuration(Duration.ZERO);
        ZoomConfigurationBuilder zoom = new ZoomConfigurationBuilder();
        zoom.percent(1.0)
            .center(ZoomCenter.SCREEN_CENTER);
        // @formatter:off
        return Stream.of( //
                new Property<>("stop-commands-from-previous-mode", stopCommandsFromPreviousMode),
                new Property<>("push-mode-to-history-stack", pushModeToHistoryStack),
                new Property<>("mode-after-unhandled-key-press", modeAfterPressingUnhandledKeysOnly),
                new Property<>("mouse", mouse),
                new Property<>("wheel", wheel), 
                new Property<>("grid", grid), 
                new Property<>("hint", hintMesh), 
                new Property<>("timeout", timeout), 
                new Property<>("indicator", indicator), 
                new Property<>("hide-cursor", hideCursor),
                new Property<>("zoom", zoom),
                new Property<>("to", Map.of()),
                new Property<>("start-move", Map.of()),
                new Property<>("stop-move", Map.of()),
                new Property<>("press", Map.of()),
                new Property<>("release", Map.of()),
                new Property<>("toggle", Map.of()),
                new Property<>("start-wheel", Map.of()),
                new Property<>("stop-wheel", Map.of()),
                new Property<>("snap", Map.of()),
                new Property<>("shrink-grid", Map.of()),
                new Property<>("move-grid", Map.of()),
                new Property<>("move-grid-to-center", Map.of()),
                new Property<>("move-to-last-selected-hint", Map.of()),
                new Property<>("save-position", Map.of()),
                new Property<>("unsave-position", Map.of()),
                new Property<>("clear", Map.of()),
                new Property<>("cycle-next", Map.of()),
                new Property<>("cycle-previous", Map.of()),
                new Property<>("break-combo-preparation", Map.of()),
                new Property<>("remapping", Map.of())
        ).collect(Collectors.toMap(property -> property.propertyKey.propertyName, Function.identity()));
        // @formatter:on
    }

    private record Aliases(Map<String, LayoutKeyAlias> layoutKeyAliasByName,
                           Map<String, AppAlias> appAliasByName) {

    }

    private static class LayoutKeyAlias {

        KeyAlias noLayoutAlias = null;
        Map<KeyboardLayout, KeyAlias> aliasByLayout = new HashMap<>();

    }

    public static Configuration parse(List<String> properties,
                                      KeyboardLayout activeKeyboardLayout) {
        KeyboardLayout configurationKeyboardLayout = parseKeyboardLayout(properties);
        KeyboardLayout keyboardLayout =
                configurationKeyboardLayout == null ? activeKeyboardLayout :
                        configurationKeyboardLayout;
        Aliases configurationAliases = parseAliases(properties);
        Map<String, KeyAlias> keyAliases = buildKeyAliasesForActiveKeyboardLayout(
                configurationAliases.layoutKeyAliasByName, keyboardLayout);
        Map<String, AppAlias> appAliases = configurationAliases.appAliasByName;
        String logLevel = null;
        boolean logRedactKeys = false;
        boolean logToFile = false;
        boolean hideConsole = false;
        ComboMoveDuration defaultComboMoveDuration =
                new ComboMoveDuration(Duration.ZERO, Duration.ofMillis(150));
        int maxPositionHistorySize = 16;
        Map<String, ModeBuilder> modeByName = new HashMap<>();
        Map<PropertyKey, Property<?>> propertyByKey = new HashMap<>();
        Set<PropertyKey> nonRootPropertyKeys = new HashSet<>();
        Set<String> modeReferences = new HashSet<>();
        Map<String, Set<String>> referencedModesByReferencerMode = new HashMap<>();
        Map<PropertyKey, Set<PropertyKey>> childPropertiesByParentProperty =
                new HashMap<>();
        Set<String> nonRootModes = new HashSet<>();
        Map<String, Set<String>> childModesByParentMode = new HashMap<>();
        Set<String> visitedPropertyKeys = new HashSet<>();
        for (String property : properties) {
            checkPropertyLineCorrectness(property, visitedPropertyKeys);
            Matcher lineMatcher = propertyLinePattern.matcher(property);
            //noinspection ResultOfMethodCallIgnored
            lineMatcher.matches();
            String propertyKey = lineMatcher.group(1).strip();
            String propertyValue = lineMatcher.group(2).strip();
            if (propertyKey.equals("logging.level")) {
                logLevel = propertyValue;
                continue;
            }
            else if (propertyKey.equals("logging.redact-keys")) {
                logRedactKeys = Boolean.parseBoolean(propertyValue);
                continue;
            }
            else if (propertyKey.equals("logging.to-file")) {
                logToFile = Boolean.parseBoolean(propertyValue);
                continue;
            }
            else if (propertyKey.equals("default-combo-move-duration-millis")) {
                defaultComboMoveDuration = parseComboMoveDuration(propertyKey, propertyValue);
                continue;
            }
            else if (propertyKey.equals("max-position-history-size")) {
                maxPositionHistorySize =
                        parseUnsignedInteger(propertyValue, 1, 100);
                continue;
            }
            else if (propertyKey.equals("hide-console")) {
                hideConsole = Boolean.parseBoolean(propertyValue);
                continue;
            }
            // a-mode.hint.grid-cell-width
            // a-mode.hint.grid-cell-width.1920x1080-100%
            Pattern modeKeyPattern =
                    Pattern.compile("([^.]+-mode)(\\.([^.]+)(\\.([^.]+))?(\\.([^.]+))?)?");
            Matcher keyMatcher = modeKeyPattern.matcher(propertyKey);
            if (!keyMatcher.matches())
                continue;
            String modeName = keyMatcher.group(1);
            if (!modeName.endsWith("-mode"))
                throw new IllegalArgumentException(
                        "Invalid mode name in property key " + propertyKey +
                        ": mode names should end with -mode");
            if (modeName.equals(Mode.PREVIOUS_MODE_FROM_HISTORY_STACK_IDENTIFIER))
                throw new IllegalArgumentException(
                        "Invalid mode name in property key " + propertyKey + ": " +
                        Mode.PREVIOUS_MODE_FROM_HISTORY_STACK_IDENTIFIER +
                        " is a reserved mode name");
            if (modeName.contains(".") || modeName.contains("="))
                throw new IllegalArgumentException(
                        "Invalid mode name in property key " + propertyKey +
                        ": mode names cannot contains . or =");
            ModeBuilder mode =
                    modeByName.computeIfAbsent(modeName,
                            modeName1 -> new ModeBuilder(modeName1, propertyByKey));
            String group2 = keyMatcher.group(3);
            ComboMoveDuration finalDefaultComboMoveDuration = defaultComboMoveDuration;
            try {
                parseLine(group2, mode, propertyKey, propertyValue,
                        childModesByParentMode, nonRootModes,
                        childPropertiesByParentProperty, nonRootPropertyKeys,
                        referencedModesByReferencerMode, modeName, keyMatcher, keyAliases,
                        modeReferences, defaultComboMoveDuration, appAliases,
                        finalDefaultComboMoveDuration, QFontDatabase::hasFamily);
            } catch (IllegalArgumentException e) {
                IllegalArgumentException e2 =
                        new IllegalArgumentException("[" + propertyKey + "] " + e.getMessage());
                e2.setStackTrace(e.getStackTrace());
                throw e2;
            }
        }
        // Verify mode name references are valid.
        for (String modeNameReference : modeReferences) {
            if (modeNameReference.equals(Mode.PREVIOUS_MODE_FROM_HISTORY_STACK_IDENTIFIER))
                continue;
            if (!modeByName.containsKey(modeNameReference))
                throw new IllegalArgumentException(
                        "Definition of mode " + modeNameReference + " is missing");
        }
        ModeBuilder idleMode = modeByName.get(Mode.IDLE_MODE_NAME);
        // Default stop-commands-from-previous-mode for idle mode is true.
        if (idleMode.stopCommandsFromPreviousMode.builder.get() == null)
            idleMode.stopCommandsFromPreviousMode.builder.set(true);
        Set<PropertyKey> rootPropertyKeys = propertyByKey.keySet()
                                                         .stream()
                                                         .filter(propertyKey -> !nonRootModes.contains(
                                                                 propertyKey.modeName()))
                                                         .filter(Predicate.not(
                                                                 nonRootPropertyKeys::contains))
                                                         .collect(Collectors.toSet());;
        Set<PropertyKey> alreadyBuiltPropertyNodeKeys = new HashSet<>();
        Set<PropertyNode> rootPropertyNodes = new HashSet<>();
        for (PropertyKey rootPropertyKey : rootPropertyKeys)
            rootPropertyNodes.add(recursivelyBuildPropertyNode(rootPropertyKey,
                    childPropertiesByParentProperty,
                    childModesByParentMode, nonRootPropertyKeys,
                    alreadyBuiltPropertyNodeKeys));
        for (PropertyNode rootPropertyNode : rootPropertyNodes) {
            recursivelyExtendProperty(
                    defaultPropertyByName.get(rootPropertyNode.propertyKey.propertyName),
                    rootPropertyNode, propertyByKey, referencedModesByReferencerMode);
        }
        for (ModeBuilder mode : modeByName.values()) {
            Set<String> referencedModes =
                    referencedModesByReferencerMode.computeIfAbsent(mode.modeName,
                            modeName_ -> new HashSet<>());
            mode.comboMap.to.builder.values()
                                    .stream()
                                    .flatMap(Collection::stream)
                                    .filter(SwitchMode.class::isInstance)
                                    .map(SwitchMode.class::cast)
                                    .map(SwitchMode::modeName)
                                    .forEach(referencedModes::add);
            if (mode.modeAfterUnhandledKeyPress.builder.get() != null)
                referencedModes.add(mode.modeAfterUnhandledKeyPress.builder.get());
            if (mode.hintMesh.builder.modeAfterSelection() != null)
                referencedModes.add(mode.hintMesh.builder.modeAfterSelection());
            if (mode.timeout.builder.modeName() != null)
                referencedModes.add(mode.timeout.builder.modeName());
        }

        Map<String, ModeNode> referenceNodeByName = new HashMap<>();
        ModeNode rootReferenceNode = recursivelyBuildReferenceNode(Mode.IDLE_MODE_NAME,
                referencedModesByReferencerMode, referenceNodeByName);
        if (modeByName.size() != 1 || !modeByName.containsKey(Mode.IDLE_MODE_NAME)) {
            for (String modeName : modeByName.keySet()) {
                if (modeName.equals(Mode.IDLE_MODE_NAME))
                    continue;
                if (!referenceNodeByName.containsKey(modeName) && !modeName.startsWith("_"))
                    throw new IllegalArgumentException("Mode " + modeName +
                                                    " is not referenced anywhere, if this is an abstract mode then its name should start with _");

            }
        }
        Set<Key> allComboAndRemappingKeys = new HashSet<>();
        for (ModeBuilder mode : modeByName.values()) {
            checkMissingProperties(mode);
            if (mode.hintMesh.builder.enabled() &&
                mode.hintMesh.builder.selectCombos() == null) {
                logger.trace(mode.modeName + ".hint.select is missing, using selection keys");
                mode.hintMesh.builder.selectCombos(
                        deriveSelectCombosFromHintSelectionKeys(mode,
                                defaultComboMoveDuration));
            }
            mode.comboMap.hintSelectCombos(mode.hintMesh.builder.selectCombos());
            mode.comboMap.hintUnselectCombos(mode.hintMesh.builder.unselectCombos());
            usedKeys(mode.comboMap, allComboAndRemappingKeys);
        }
        List<Key> missingKeys = allComboAndRemappingKeys.stream()
                                                        .filter(Predicate.not(
                                                                keyboardLayout::containsKey))
                                                        .toList();
        if (!missingKeys.isEmpty())
            ;
        // The disable combo #/ only works for layouts that have the / key, but that's fine.
//            throw new IllegalStateException(
//                    "Unable to find the following keys in " + keyboardLayout + ": " +
//                    missingKeys);
        Set<Mode> modes = modeByName.values()
                                    .stream()
                                    .map(ModeBuilder::build)
                                    .collect(Collectors.toSet());
        return new Configuration(maxPositionHistorySize,
                new ModeMap(modes), logLevel, logRedactKeys, logToFile, hideConsole,
                configurationKeyboardLayout);
    }

    private static List<Combo> deriveSelectCombosFromHintSelectionKeys(ModeBuilder mode,
                                                                       ComboMoveDuration defaultComboMoveDuration) {
        Set<Key> hintSelectionKeys =
                mode.hintMesh.builder.keysByFilter()
                                     .map()
                                     .values()
                                     .stream()
                                     .map(HintMeshKeysBuilder::selectionKeys)
                                     .filter(Objects::nonNull)
                                     .flatMap(Collection::stream)
                                     .collect(Collectors.toSet());
        ComboPrecondition emptyComboPrecondition = new ComboPrecondition(
                new ComboPrecondition.ComboKeyPrecondition(Set.of(), Set.of()),
                new ComboPrecondition.ComboAppPrecondition(Set.of(), Set.of()));
        return hintSelectionKeys.stream()
                                .map(key -> new Combo(emptyComboPrecondition,
                                        new ComboSequence(
                                                List.of(new ComboMove.PressComboMove(
                                                        key, true,
                                                        defaultComboMoveDuration)))))
                                .toList();
    }

    private static KeyboardLayout parseKeyboardLayout(List<String> properties) {
        Set<String> visitedPropertyKeys = new HashSet<>();
        for (String property : properties) {
            checkPropertyLineCorrectness(property, visitedPropertyKeys);
            Matcher lineMatcher = propertyLinePattern.matcher(property);
            //noinspection ResultOfMethodCallIgnored
            lineMatcher.matches();
            String propertyKey = lineMatcher.group(1).strip();
            String propertyValue = lineMatcher.group(2).strip();
            if (propertyKey.equals("keyboard-layout")) {
                return parseKeyboardLayout(propertyValue);
            }
        }
        return null;
    }

    private static void usedKeys(ComboMapConfigurationBuilder comboMap,
                                 Set<Key> allComboAndRemappingKeys) {
        for (Map.Entry<Combo, List<Command>> entry : comboMap.commandsByCombo().entrySet()) {
            Combo combo = entry.getKey();
            List<Command> commands = entry.getValue();
            combo.precondition()
                 .keyPrecondition()
                 .pressedKeySets()
                 .stream()
                 .flatMap(Collection::stream)
                 .forEach(allComboAndRemappingKeys::add);
            allComboAndRemappingKeys.addAll(combo.precondition()
                                                 .keyPrecondition()
                                                 .unpressedKeySet());
            combo.sequence()
                 .moves()
                 .stream()
                 .map(ComboMove::key)
                 .forEach(allComboAndRemappingKeys::add);
            for (Command command : commands) {
                if (command instanceof Command.RemappingCommand(Remapping remapping)) {
                    for (RemappingParallel parallel : remapping.output()
                                                               .parallels()) {
                        for (RemappingMove move : parallel.moves()) {
                            allComboAndRemappingKeys.add(move.key());
                        }
                    }
                }
            }
        }
    }

    private static void parseLine(String group2, ModeBuilder mode, String propertyKey,
                                  String propertyValue,
                                  Map<String, Set<String>> childModesByParentMode,
                                  Set<String> nonRootModes,
                                  Map<PropertyKey, Set<PropertyKey>> childPropertiesByParentProperty,
                                  Set<PropertyKey> nonRootPropertyKeys,
                                  Map<String, Set<String>> referencedModesByReferencerMode,
                                  String modeName, Matcher keyMatcher,
                                  Map<String, KeyAlias> keyAliases,
                                  Set<String> modeReferences,
                                  ComboMoveDuration defaultComboMoveDuration,
                                  Map<String, AppAlias> appAliases,
                                  ComboMoveDuration finalDefaultComboMoveDuration,
                                  Predicate<String> fontAvailability) {
        if (group2 == null) {
            // Mode reference.
            parseModeReference(propertyKey, propertyValue, childModesByParentMode,
                    nonRootModes);
            return;
        }
        final int group3 = 4;
        final int group4 = 5;
        final int group5 = 7;
        switch (group2) {
            case "stop-commands-from-previous-mode" ->
                    mode.stopCommandsFromPreviousMode.parseReferenceOr(propertyKey,
                            propertyValue, builder -> builder.set(
                                    Boolean.parseBoolean(propertyValue)),
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
            case "push-mode-to-history-stack" ->
                    mode.pushModeToHistoryStack.parseReferenceOr(propertyKey,
                            propertyValue, builder -> builder.set(
                                    Boolean.parseBoolean(propertyValue)),
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
            case "mode-after-unhandled-key-press" -> {
                String modeAfterPressingUnhandledKeysOnly =
                        checkModeReference(propertyValue);
                mode.modeAfterUnhandledKeyPress.parseReferenceOr(propertyKey,
                        modeAfterPressingUnhandledKeysOnly, builder ->
                                builder.set(modeAfterPressingUnhandledKeysOnly),
                        childPropertiesByParentProperty, nonRootPropertyKeys);
            }
            case "mouse" -> {
                if (keyMatcher.group(group3) == null)
                    mode.mouse.parsePropertyReference(propertyKey, propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid mouse property key");
                else {
                    switch (keyMatcher.group(group4)) {
                        case "initial-velocity" -> mode.mouse.builder.initialVelocity(
                                Double.parseDouble(propertyValue));
                        case "max-velocity" -> mode.mouse.builder.maxVelocity(
                                Double.parseDouble(propertyValue));
                        case "acceleration" -> mode.mouse.builder.acceleration(
                                Double.parseDouble(propertyValue));
                        case "smooth-jump-enabled" -> mode.mouse.builder.smoothJumpEnabled(
                                Boolean.parseBoolean(propertyValue));
                        case "smooth-jump-velocity" -> mode.mouse.builder.smoothJumpVelocity(
                                Double.parseDouble(propertyValue));
                        default -> throw new IllegalArgumentException(
                                "Invalid mouse property key");
                    }
                }
            }
            case "wheel" -> {
                if (keyMatcher.group(group3) == null)
                    mode.wheel.parsePropertyReference(propertyKey, propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid wheel property key");
                else {
                    switch (keyMatcher.group(group4)) {
                        case "acceleration" -> mode.wheel.builder.acceleration(
                                Double.parseDouble(propertyValue));
                        case "initial-velocity" -> mode.wheel.builder.initialVelocity(
                                Double.parseDouble(propertyValue));
                        case "max-velocity" -> mode.wheel.builder.maxVelocity(
                                Double.parseDouble(propertyValue));
                        default -> throw new IllegalArgumentException(
                                "Invalid wheel property key");
                    }
                }
            }
            case "grid" -> {
                if (keyMatcher.group(group3) == null)
                    mode.grid.parsePropertyReference(propertyKey, propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid grid property key");
                else {
                    switch (keyMatcher.group(group4)) {
                        case "area" -> mode.grid.builder.area()
                                                        .type(parseGridAreaType(
                                                                propertyKey,
                                                                propertyValue));
                        case "area-width-percent" -> mode.grid.builder.area()
                                                                      .widthPercent(
                                                                              parseNonZeroPercent(
                                                                                      propertyValue,
                                                                                      2));
                        case "area-height-percent" -> mode.grid.builder.area()
                                                                       .heightPercent(
                                                                               parseNonZeroPercent(
                                                                                       propertyValue,
                                                                                       2));
                        case "area-top-inset" -> mode.grid.builder.area()
                                                                  .topInset(
                                                                          parseUnsignedInteger(
                                                                                  propertyValue,
                                                                                  0,
                                                                                  10_000));
                        case "area-bottom-inset" -> mode.grid.builder.area()
                                                                     .bottomInset(
                                                                          parseUnsignedInteger(
                                                                                  propertyValue,
                                                                                  0,
                                                                                  10_000));
                        case "area-left-inset" -> mode.grid.builder.area()
                                                                   .leftInset(
                                                                          parseUnsignedInteger(
                                                                                  propertyValue,
                                                                                  0,
                                                                                  10_000));
                        case "area-right-inset" -> mode.grid.builder.area()
                                                                    .rightInset(
                                                                          parseUnsignedInteger(
                                                                                  propertyValue,
                                                                                  0,
                                                                                  10_000));
                        case "synchronization" -> mode.grid.builder.synchronization(
                                parseSynchronization(propertyKey, propertyValue));
                        case "row-count" -> mode.grid.builder.rowCount(
                                parseUnsignedInteger(propertyValue, 1, 50));
                        case "column-count" -> mode.grid.builder.columnCount(
                                parseUnsignedInteger(propertyValue, 1, 50));
                        case "line-visible" -> mode.grid.builder.lineVisible(
                                Boolean.parseBoolean(propertyValue));
                        case "line-color" -> mode.grid.builder.lineHexColor(
                                checkColorFormat(propertyValue));
                        case "line-thickness" -> mode.grid.builder.lineThickness(
                                parseDouble(propertyValue, false, 0, 1000));
                        default -> throw new IllegalArgumentException(
                                "Invalid grid property key");
                    }
                }
            }
            case "hint" -> {
                if (keyMatcher.group(group3) == null)
                    mode.hintMesh.parsePropertyReference(propertyKey, propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid hint property key");
                else {
                    if (mode.hintMesh.builder.enabled() == null)
                        mode.hintMesh.builder.enabled(true);
                    ViewportFilter viewportFilter = keyMatcher.group(group5) == null ?
                            AnyViewportFilter.ANY_VIEWPORT_FILTER :
                            parseViewportFilter(keyMatcher.group(group5));
                    switch (keyMatcher.group(group4)) {
                        case "enabled" -> mode.hintMesh.builder.enabled(
                                Boolean.parseBoolean(propertyValue));
                        case "visible" -> mode.hintMesh.builder.visible(
                                Boolean.parseBoolean(propertyValue));
                        case "move-mouse" -> {
                            boolean moveMouse = Boolean.parseBoolean(propertyValue);
                            logger.warn(
                                    "hint.move-mouse has been deprecated: use " +
                                    modeName +
                                    ".hint.mouse-movement=" +
                                    (moveMouse ? "mouse-follows-selected-hint" :
                                            "no-movement") + " instead");
                            if (mode.hintMesh.builder.mouseMovement() == null) {
                                mode.hintMesh.builder.mouseMovement(
                                        moveMouse ?
                                                HintMouseMovement.MOUSE_FOLLOWS_SELECTED_HINT :
                                                HintMouseMovement.NO_MOVEMENT);
                            }
                        }
                        case "mouse-movement" -> mode.hintMesh.builder.mouseMovement(
                                parseHintMouseMovement(propertyKey, propertyValue));
                        case "type" -> mode.hintMesh.builder.type()
                                                            .type(parseHintMeshTypeType(
                                                                    propertyKey,
                                                                    propertyValue));
                        case "grid-area" -> mode.hintMesh.builder.type()
                                                                 .gridArea()
                                                                 .type(parseHintGridAreaType(
                                                                         propertyKey,
                                                                         propertyValue));
                        case "active-screen-grid-area-center" ->
                                mode.hintMesh.builder.type()
                                                     .gridArea()
                                                     .activeScreenHintGridAreaCenter(
                                                             parseActiveScreenHintGridAreaCenter(
                                                                     propertyKey,
                                                                     propertyValue));
                        case "grid-max-row-count" -> mode.hintMesh.builder.type()
                                                                          .gridLayout(viewportFilter)
                                                                          .maxRowCount(
                                                                                       parseUnsignedInteger(
                                                                                               propertyValue, 1, 200));
                        case "grid-max-column-count" -> mode.hintMesh.builder.type()
                                                                             .gridLayout(viewportFilter)
                                                                             .maxColumnCount(
                                                                                     parseUnsignedInteger(
                                                                                             propertyValue,
                                                                                             1,
                                                                                             200));
                        case "grid-cell-width" -> mode.hintMesh.builder.type()
                                                                       .gridLayout(viewportFilter)
                                                                       .cellWidth(
                                                                               parseDouble(
                                                                                       propertyValue,
                                                                                       false,
                                                                                       0,
                                                                                       10_000
                                                                               ));
                        case "grid-cell-height" -> mode.hintMesh.builder.type()
                                                                        .gridLayout(viewportFilter)
                                                                        .cellHeight(
                                                                                 parseDouble(
                                                                                         propertyValue,
                                                                                         false,
                                                                                         0,
                                                                                         10_000
                                                                                 ));
                        case "layout-row-count" -> mode.hintMesh.builder.type()
                                                                        .gridLayout(viewportFilter)
                                                                        .layoutRowCount(parseUnsignedInteger(propertyValue, 1, 1_000_000_000));
                        case "layout-column-count" -> mode.hintMesh.builder.type()
                                                                           .gridLayout(viewportFilter)
                                                                           .layoutColumnCount(parseUnsignedInteger(propertyValue, 1, 1_000_000_000));
                        case "layout-row-oriented" -> mode.hintMesh.builder.type()
                                                                           .gridLayout(viewportFilter)
                                                                           .layoutRowOriented(Boolean.parseBoolean(propertyValue));
                        case "selection-keys" -> mode.hintMesh.builder.keys(viewportFilter).selectionKeys(parseHintKeys(propertyValue, keyAliases));
                        case "row-key-offset" -> mode.hintMesh.builder.keys(viewportFilter).rowKeyOffset(parseUnsignedInteger(propertyValue, 0, 1_000));
                        case "font-weight" -> mode.hintMesh.builder.style(viewportFilter).fontStyle().weight(FontWeight.of(propertyValue));
                        case "font-name" -> mode.hintMesh.builder.style(viewportFilter).fontStyle().name(parseFontName(propertyValue, fontAvailability));
                        case "font-size" -> mode.hintMesh.builder.style(viewportFilter).fontStyle().size(parseDouble(propertyValue, false, 0, 1000));
                        case "font-spacing-percent" -> mode.hintMesh.builder.style(viewportFilter).fontStyle().spacingPercent(parseDouble(propertyValue, true, 0, 1));
                        case "font-color" -> mode.hintMesh.builder.style(viewportFilter).fontStyle().hexColor(checkColorFormat(propertyValue));
                        case "font-opacity" -> mode.hintMesh.builder.style(viewportFilter).fontStyle().opacity(parseDouble(propertyValue, true, 0, 1));
                        case "selected-font-color" -> mode.hintMesh.builder.style(viewportFilter).fontStyle().selectedFontHexColor(checkColorFormat(propertyValue));
                        case "selected-font-opacity" -> mode.hintMesh.builder.style(viewportFilter).fontStyle().selectedFontOpacity(parseDouble(propertyValue, true, 0, 1));
                        case "focused-font-color" -> mode.hintMesh.builder.style(viewportFilter).fontStyle().focusedFontHexColor(checkColorFormat(propertyValue));
                        case "focused-font-opacity" -> mode.hintMesh.builder.style(viewportFilter).fontStyle().focusedFontOpacity(parseDouble(propertyValue, true, 0, 1));
                        case "font-outline-thickness" -> mode.hintMesh.builder.style(viewportFilter).fontStyle().outlineThickness(parseDouble(propertyValue, true, 0, 1000));
                        case "font-outline-color" -> mode.hintMesh.builder.style(viewportFilter).fontStyle().outlineHexColor(checkColorFormat(propertyValue));
                        case "font-outline-opacity" -> mode.hintMesh.builder.style(viewportFilter).fontStyle().outlineOpacity(parseDouble(propertyValue, true, 0, 1));
                        case "font-shadow-blur-radius" -> mode.hintMesh.builder.style(viewportFilter).fontStyle().shadowBlurRadius(parseDouble(propertyValue, true, 0, 1000));
                        case "font-shadow-color" -> mode.hintMesh.builder.style(viewportFilter).fontStyle().shadowHexColor(checkColorFormat(propertyValue));
                        case "font-shadow-opacity" -> mode.hintMesh.builder.style(viewportFilter).fontStyle().shadowOpacity(parseDouble(propertyValue, true, 0, 1));
                        case "font-shadow-horizontal-offset" -> mode.hintMesh.builder.style(viewportFilter).fontStyle().shadowHorizontalOffset(parseDouble(propertyValue, true, -100, 100));
                        case "font-shadow-vertical-offset" -> mode.hintMesh.builder.style(viewportFilter).fontStyle().shadowVerticalOffset(parseDouble(propertyValue, true, -100, 100));
                        case "prefix-in-background" ->
                                mode.hintMesh.builder.style(viewportFilter)
                                                     .prefixInBackground(Boolean.parseBoolean(propertyValue));
                        case "prefix-font-weight" -> mode.hintMesh.builder.style(viewportFilter).prefixFontStyle().weight(FontWeight.of(propertyValue));
                        case "prefix-font-name" -> mode.hintMesh.builder.style(viewportFilter).prefixFontStyle().name(parseFontName(propertyValue, fontAvailability));
                        case "prefix-font-size" -> mode.hintMesh.builder.style(viewportFilter).prefixFontStyle().size(parseDouble(propertyValue, false, 0, 1000));
                        case "prefix-font-spacing-percent" -> mode.hintMesh.builder.style(viewportFilter).prefixFontStyle().spacingPercent(parseDouble(propertyValue, true, 0, 1));
                        case "prefix-font-color" -> mode.hintMesh.builder.style(viewportFilter).prefixFontStyle().hexColor(checkColorFormat(propertyValue));
                        case "prefix-font-opacity" -> mode.hintMesh.builder.style(viewportFilter).prefixFontStyle().opacity(parseDouble(propertyValue, true, 0, 1));
                        case "prefix-selected-font-color" -> mode.hintMesh.builder.style(viewportFilter).prefixFontStyle().selectedFontHexColor(checkColorFormat(propertyValue));
                        case "prefix-selected-font-opacity" -> mode.hintMesh.builder.style(viewportFilter).prefixFontStyle().selectedFontOpacity(parseDouble(propertyValue, true, 0, 1));
                        case "prefix-focused-font-color" -> mode.hintMesh.builder.style(viewportFilter).prefixFontStyle().focusedFontHexColor(checkColorFormat(propertyValue));
                        case "prefix-focused-font-opacity" -> mode.hintMesh.builder.style(viewportFilter).prefixFontStyle().focusedFontOpacity(parseDouble(propertyValue, true, 0, 1));
                        case "prefix-font-outline-thickness" -> mode.hintMesh.builder.style(viewportFilter).prefixFontStyle().outlineThickness(parseDouble(propertyValue, true, 0, 1000));
                        case "prefix-font-outline-color" -> mode.hintMesh.builder.style(viewportFilter).prefixFontStyle().outlineHexColor(checkColorFormat(propertyValue));
                        case "prefix-font-outline-opacity" -> mode.hintMesh.builder.style(viewportFilter).prefixFontStyle().outlineOpacity(parseDouble(propertyValue, true, 0, 1));
                        case "prefix-font-shadow-blur-radius" -> mode.hintMesh.builder.style(viewportFilter).prefixFontStyle().shadowBlurRadius(parseDouble(propertyValue, true, 0, 1000));
                        case "prefix-font-shadow-color" -> mode.hintMesh.builder.style(viewportFilter).prefixFontStyle().shadowHexColor(checkColorFormat(propertyValue));
                        case "prefix-font-shadow-opacity" -> mode.hintMesh.builder.style(viewportFilter).prefixFontStyle().shadowOpacity(parseDouble(propertyValue, true, 0, 1));
                        case "prefix-font-shadow-horizontal-offset" -> mode.hintMesh.builder.style(viewportFilter).prefixFontStyle().shadowHorizontalOffset(parseDouble(propertyValue, true, -100, 100));
                        case "prefix-font-shadow-vertical-offset" -> mode.hintMesh.builder.style(viewportFilter).prefixFontStyle().shadowVerticalOffset(parseDouble(propertyValue, true, -100, 100));
                        case "box-color" -> mode.hintMesh.builder.style(viewportFilter).boxHexColor(
                                checkColorFormat(propertyValue));
                        case "box-opacity" -> mode.hintMesh.builder.style(viewportFilter).boxOpacity(
                                parseDouble(propertyValue, true, 0, 1));
                        // Allow for box grow percent > 1: even with 1, I would get empty pixels
                        // between the cells due to the way we distribute spare pixels.
                        // See HintManager#distributeTrueUniformly.
                        case "box-border-thickness" -> mode.hintMesh.builder.style(viewportFilter).boxBorderThickness(
                                parseDouble(propertyValue, true, 0, 10_000));
                        case "box-border-length" -> mode.hintMesh.builder.style(viewportFilter).boxBorderLength(
                                parseDouble(propertyValue, true, 0, 10_000));
                        case "box-border-color" -> mode.hintMesh.builder.style(viewportFilter).boxBorderHexColor(
                                checkColorFormat(propertyValue));
                        case "box-border-opacity" -> mode.hintMesh.builder.style(viewportFilter).boxBorderOpacity(
                                parseDouble(propertyValue, true, 0, 1));
                        case "prefix-box-enabled" -> mode.hintMesh.builder.style(viewportFilter).prefixBoxEnabled(Boolean.parseBoolean(propertyValue));
                        case "prefix-box-border-thickness" -> {
                            if (mode.hintMesh.builder.style(viewportFilter).prefixBoxEnabled() == null)
                                mode.hintMesh.builder.style(viewportFilter).prefixBoxEnabled(true);
                            mode.hintMesh.builder.style(viewportFilter)
                                                 .prefixBoxBorderThickness(
                                                         parseDouble(propertyValue, true,
                                                                 0, 10_000));
                        }
                        case "prefix-box-border-length" -> {
                            if (mode.hintMesh.builder.style(viewportFilter).prefixBoxEnabled() == null)
                                mode.hintMesh.builder.style(viewportFilter).prefixBoxEnabled(true);
                            mode.hintMesh.builder.style(viewportFilter)
                                                 .prefixBoxBorderLength(
                                                         parseDouble(propertyValue, true,
                                                                 0, 10_000));
                        }
                        case "prefix-box-border-color" -> {
                            if (mode.hintMesh.builder.style(viewportFilter).prefixBoxEnabled() == null)
                                mode.hintMesh.builder.style(viewportFilter).prefixBoxEnabled(true);
                            mode.hintMesh.builder.style(viewportFilter)
                                                 .prefixBoxBorderHexColor(
                                                         checkColorFormat(propertyValue));
                        }
                        case "prefix-box-border-opacity" -> {
                            if (mode.hintMesh.builder.style(viewportFilter).prefixBoxEnabled() == null)
                                mode.hintMesh.builder.style(viewportFilter).prefixBoxEnabled(true);
                            mode.hintMesh.builder.style(viewportFilter)
                                                 .prefixBoxBorderOpacity(
                                                         parseDouble(propertyValue, true,
                                                                 0, 1));
                        }
                        case "box-width-percent" -> mode.hintMesh.builder.style(viewportFilter).boxWidthPercent(
                                parseDouble(propertyValue, true, 0, 1));
                        case "box-height-percent" -> mode.hintMesh.builder.style(viewportFilter).boxHeightPercent(
                                parseDouble(propertyValue, true, 0, 1));
                        case "subgrid-row-count" -> mode.hintMesh.builder.style(viewportFilter).subgridRowCount(parseUnsignedInteger(
                                propertyValue, 1, 1_000));
                        case "subgrid-column-count" -> mode.hintMesh.builder.style(viewportFilter).subgridColumnCount(parseUnsignedInteger(
                                propertyValue, 1, 1_000));
                        case "subgrid-border-thickness" -> mode.hintMesh.builder.style(viewportFilter).subgridBorderThickness(
                                parseDouble(propertyValue, true, 0, 10_000));
                        case "subgrid-border-length" -> mode.hintMesh.builder.style(viewportFilter).subgridBorderLength(
                                parseDouble(propertyValue, true, 0, 10_000));
                        case "subgrid-border-color" -> mode.hintMesh.builder.style(viewportFilter).subgridBorderHexColor(
                                checkColorFormat(propertyValue));
                        case "subgrid-border-opacity" -> mode.hintMesh.builder.style(viewportFilter).subgridBorderOpacity(
                                parseDouble(propertyValue, true, 0, 1));
                        case "transition-animation-enabled" -> mode.hintMesh.builder.style(viewportFilter).transitionAnimationEnabled(Boolean.parseBoolean(propertyValue));
                        case "transition-animation-duration-millis" -> mode.hintMesh.builder.style(viewportFilter).transitionAnimationDuration(parseDuration(propertyValue));
                        case "mode-after-selection" -> {
                            String modeAfterSelection = propertyValue;
                            modeReferences.add(
                                    checkModeReference(modeAfterSelection));
                            mode.hintMesh.builder.modeAfterSelection(modeAfterSelection);
                            logger.warn(
                                    "hint.mode-after-selection has been deprecated: use " +
                                    modeName + ".to." + modeAfterSelection +
                                    "=<combo> instead, along with " + modeName +
                                    ".break-combo-preparation=<combo>");
                        }
                        case "swallow-hint-end-key-press" -> {
                            throw new IllegalArgumentException(
                                    "hint.swallow-hint-end-key-press has been deprecated and removed: use break-combo-preparation instead");
                        }
                        case "save-position-after-selection" -> {
                            throw new IllegalArgumentException(
                                    "hint.save-position-after-selection has been deprecated and removed: use position-history.save-position instead");
                        }
                        case "eat-unused-selection-keys" -> mode.hintMesh.builder.eatUnusedSelectionKeys(Boolean.parseBoolean(propertyValue));
                        case "select" -> {
                            mode.hintMesh.builder.selectCombos(
                                    parseCombos(propertyValue, defaultComboMoveDuration,
                                            keyAliases, appAliases));
                        }
                        case "undo" -> {
                            mode.hintMesh.builder.unselectCombos(
                                    parseCombos(propertyValue, defaultComboMoveDuration,
                                            keyAliases, appAliases));
                        }
                        default -> throw new IllegalArgumentException(
                                "Invalid hint property key");
                    }
                }
            }
            case "to" -> {
                if (keyMatcher.group(group3) == null)
                    mode.comboMap.to.parsePropertyReference(propertyKey, propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid to (mode switch) property key");
                else {
                    String newModeName = keyMatcher.group(group4);
                    modeReferences.add(checkModeReference(newModeName));
                    setCommand(mode.comboMap.to.builder, propertyValue,
                            new SwitchMode(newModeName), defaultComboMoveDuration,
                            keyAliases, appAliases);
                }
            }
            case "remapping" -> {
                if (keyMatcher.group(group3) == null)
                    mode.comboMap.remapping.parsePropertyReference(propertyKey,
                            propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid remapping property key");
                else {
                    String remappingName = keyMatcher.group(group4);
                    String remappingString = propertyValue;
                    String[] split = remappingString.split("\\s*->\\s*");
                    if (split.length != 2)
                        throw new IllegalArgumentException(
                                "Invalid remapping: " + propertyValue);
                    List<AliasResolvedCombo> aliasResolvedCombos =
                            Combo.multiCombo(split[0], defaultComboMoveDuration,
                                    keyAliases,
                                    appAliases);
                    // Aliases used in the remapping must be used in all of the
                    // combos of that multi combo.
                    Set<String> comboAliasNameIntersection = new HashSet<>(
                            aliasResolvedCombos.getFirst()
                                               .aliasResolution()
                                               .keyByAliasName()
                                               .keySet());
                    for (AliasResolvedCombo aliasResolvedCombo : aliasResolvedCombos) {
                        comboAliasNameIntersection.retainAll(
                                aliasResolvedCombo.aliasResolution()
                                                  .keyByAliasName()
                                                  .keySet());
                    }
                    for (AliasResolvedCombo aliasResolvedCombo : aliasResolvedCombos) {
                        String remappingOutput = split[1];
                        Set<String> aliasNamesUsedInRemappingOutput =
                                Remapping.aliasNamesUsedInRemappingOutput(remappingOutput,
                                        keyAliases.keySet());
                        if (!comboAliasNameIntersection.containsAll(
                                aliasNamesUsedInRemappingOutput)) {
                            Set<String> aliasesNotUsedInComboSequence =
                                    new HashSet<>(aliasNamesUsedInRemappingOutput);
                            aliasNamesUsedInRemappingOutput.removeAll(
                                    comboAliasNameIntersection);
                            throw new IllegalArgumentException(
                                    "Key aliases " + aliasesNotUsedInComboSequence +
                                    " cannot be used in the remapping output because they are not used in the combo sequence");
                        }
                        Remapping remapping = Remapping.of(remappingName, remappingOutput,
                                    aliasResolvedCombo.aliasResolution());
                        // One remapping command per resolved alias.
                        Command command = new RemappingCommand(remapping);
                        for (Combo combo : List.of(aliasResolvedCombo.combo()))
                            mode.comboMap.remapping.builder.computeIfAbsent(combo, combo1 -> new ArrayList<>())
                                                           .add(command);
                    }
                }
            }
            case "timeout" -> {
                if (keyMatcher.group(group3) == null)
                    mode.timeout.parsePropertyReference(propertyKey, propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid timeout property key");
                else {
                    if (mode.timeout.builder.enabled() == null)
                        mode.timeout.builder.enabled(true);
                    switch (keyMatcher.group(group4)) {
                        case "enabled" -> mode.timeout.builder.enabled(
                                Boolean.parseBoolean(propertyValue));
                        case "duration-millis" -> mode.timeout.builder.duration(
                                parseDuration(propertyValue));
                        case "mode" -> {
                            String timeoutModeName = propertyValue;
                            mode.timeout.builder.modeName(timeoutModeName);
                            modeReferences.add(
                                    checkModeReference(timeoutModeName));
                        }
                        case "only-if-idle" -> mode.timeout.builder.onlyIfIdle(
                                Boolean.parseBoolean(propertyValue));
                        default -> throw new IllegalArgumentException(
                                "Invalid timeout property key");
                    }
                }
            }
            case "indicator" -> {
                if (keyMatcher.group(group3) == null)
                    mode.indicator.parsePropertyReference(propertyKey, propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid indicator property key");
                else {
                    if (mode.indicator.builder.enabled() == null)
                        mode.indicator.builder.enabled(true);
                    switch (keyMatcher.group(group4)) {
                        case "enabled" -> mode.indicator.builder.enabled(
                                Boolean.parseBoolean(propertyValue));
                        case "size" -> mode.indicator.builder.size(
                                parseUnsignedInteger(propertyValue, 1, 100));
                        case "idle-color" -> mode.indicator.builder.idleHexColor(
                                checkColorFormat(propertyValue));
                        case "move-color" -> mode.indicator.builder.moveHexColor(
                                checkColorFormat(propertyValue));
                        case "wheel-color" -> mode.indicator.builder.wheelHexColor(
                                checkColorFormat(propertyValue));
                        case "mouse-press-color" ->
                                mode.indicator.builder.mousePressHexColor(
                                        checkColorFormat(propertyValue));
                        case "left-mouse-press-color" ->
                                mode.indicator.builder.leftMousePressHexColor(
                                        checkColorFormat(propertyValue));
                        case "middle-mouse-press-color" ->
                                mode.indicator.builder.middleMousePressHexColor(
                                        checkColorFormat(propertyValue));
                        case "right-mouse-press-color" ->
                                mode.indicator.builder.rightMousePressHexColor(
                                        checkColorFormat(propertyValue));
                        case "unhandled-key-press-color" ->
                                mode.indicator.builder.unhandledKeyPressHexColor(
                                        checkColorFormat(propertyValue));
                        default -> throw new IllegalArgumentException(
                                "Invalid indicator property key");
                    }
                }
            }
            case "hide-cursor" -> {
                if (keyMatcher.group(group3) == null)
                    mode.hideCursor.parsePropertyReference(propertyKey, propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid hide-cursor property key");
                else {
                    if (mode.hideCursor.builder.enabled() == null)
                        mode.hideCursor.builder.enabled(true);
                    switch (keyMatcher.group(group4)) {
                        case "enabled" -> mode.hideCursor.builder.enabled(
                                Boolean.parseBoolean(propertyValue));
                        case "idle-duration-millis" ->
                                mode.hideCursor.builder.idleDuration(
                                        parseDuration(propertyValue));
                        default -> throw new IllegalArgumentException(
                                "Invalid hide-cursor configuration");
                    }
                }
            }
            case "zoom" -> {
                if (keyMatcher.group(group3) == null)
                    mode.zoom.parsePropertyReference(propertyKey, propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid zoom property key");
                else {
                    switch (keyMatcher.group(group4)) {
                        case "percent" -> mode.zoom.builder.percent(
                                parseDouble(propertyValue, true, 1, 100));
                        case "center" ->
                                mode.zoom.builder.center(
                                        parseZoomCenter(propertyKey, propertyValue));
                        default -> throw new IllegalArgumentException(
                                "Invalid zoom property key");
                    }
                }
            }
            case "start-move" -> {
                if (keyMatcher.group(group3) == null)
                    mode.comboMap.startMove.parsePropertyReference(propertyKey,
                            propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid start-move property key");
                else {
                    switch (keyMatcher.group(group4)) {
                        // @formatter:off
                        case "up" -> setCommand(mode.comboMap.startMove.builder,  propertyValue, new StartMoveUp(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "down" -> setCommand(mode.comboMap.startMove.builder,  propertyValue, new StartMoveDown(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "left" -> setCommand(mode.comboMap.startMove.builder,  propertyValue, new StartMoveLeft(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "right" -> setCommand(mode.comboMap.startMove.builder,  propertyValue, new StartMoveRight(), defaultComboMoveDuration, keyAliases, appAliases);
                        // @formatter:on
                    }
                }
            }
            case "stop-move" -> {
                if (keyMatcher.group(group3) == null)
                    mode.comboMap.stopMove.parsePropertyReference(propertyKey,
                            propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid stop-move property key");
                else {
                    switch (keyMatcher.group(group4)) {
                        // @formatter:off
                        case "up" -> setCommand(mode.comboMap.stopMove.builder,  propertyValue, new StopMoveUp(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "down" -> setCommand(mode.comboMap.stopMove.builder,  propertyValue, new StopMoveDown(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "left" -> setCommand(mode.comboMap.stopMove.builder,  propertyValue, new StopMoveLeft(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "right" -> setCommand(mode.comboMap.stopMove.builder,  propertyValue, new StopMoveRight(), defaultComboMoveDuration, keyAliases, appAliases);
                        // @formatter:on
                    }
                }
            }
            case "press" -> {
                if (keyMatcher.group(group3) == null)
                    mode.comboMap.press.parsePropertyReference(propertyKey, propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid press property key");
                else {
                    switch (keyMatcher.group(group4)) {
                        // @formatter:off
                        case "left" -> setCommand(mode.comboMap.press.builder,  propertyValue, new PressLeft(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "middle" -> setCommand(mode.comboMap.press.builder,  propertyValue, new PressMiddle(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "right" -> setCommand(mode.comboMap.press.builder,  propertyValue, new PressRight(), defaultComboMoveDuration, keyAliases, appAliases);
                        // @formatter:on
                    }
                }
            }
            case "release" -> {
                if (keyMatcher.group(group3) == null)
                    mode.comboMap.release.parsePropertyReference(propertyKey,
                            propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid release property key");
                else {
                    switch (keyMatcher.group(group4)) {
                        // @formatter:off
                        case "left" -> setCommand(mode.comboMap.release.builder,  propertyValue, new ReleaseLeft(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "middle" -> setCommand(mode.comboMap.release.builder,  propertyValue, new ReleaseMiddle(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "right" -> setCommand(mode.comboMap.release.builder,  propertyValue, new ReleaseRight(), defaultComboMoveDuration, keyAliases, appAliases);
                        // @formatter:on
                    }
                }
            }
            case "toggle" -> {
                if (keyMatcher.group(group3) == null)
                    mode.comboMap.toggle.parsePropertyReference(propertyKey,
                            propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid toggle property key");
                else {
                    switch (keyMatcher.group(group4)) {
                        // @formatter:off
                        case "left" -> setCommand(mode.comboMap.toggle.builder,  propertyValue, new ToggleLeft(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "middle" -> setCommand(mode.comboMap.toggle.builder,  propertyValue, new ToggleMiddle(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "right" -> setCommand(mode.comboMap.toggle.builder,  propertyValue, new ToggleRight(), defaultComboMoveDuration, keyAliases, appAliases);
                        // @formatter:on
                    }
                }
            }
            case "start-wheel" -> {
                if (keyMatcher.group(group3) == null)
                    mode.comboMap.startWheel.parsePropertyReference(propertyKey,
                            propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid start-wheel property key");
                else {
                    switch (keyMatcher.group(group4)) {
                        // @formatter:off
                        case "up" -> setCommand(mode.comboMap.startWheel.builder,  propertyValue, new StartWheelUp(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "down" -> setCommand(mode.comboMap.startWheel.builder,  propertyValue, new StartWheelDown(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "left" -> setCommand(mode.comboMap.startWheel.builder,  propertyValue, new StartWheelLeft(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "right" -> setCommand(mode.comboMap.startWheel.builder,  propertyValue, new StartWheelRight(), defaultComboMoveDuration, keyAliases, appAliases);
                        // @formatter:on
                    }
                }
            }
            case "stop-wheel" -> {
                if (keyMatcher.group(group3) == null)
                    mode.comboMap.stopWheel.parsePropertyReference(propertyKey,
                            propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid stop-wheel property key");
                else {
                    switch (keyMatcher.group(group4)) {
                        // @formatter:off
                        case "up" -> setCommand(mode.comboMap.stopWheel.builder,  propertyValue, new StopWheelUp(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "down" -> setCommand(mode.comboMap.stopWheel.builder,  propertyValue, new StopWheelDown(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "left" -> setCommand(mode.comboMap.stopWheel.builder,  propertyValue, new StopWheelLeft(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "right" -> setCommand(mode.comboMap.stopWheel.builder,  propertyValue, new StopWheelRight(), defaultComboMoveDuration, keyAliases, appAliases);
                        // @formatter:on
                    }
                }
            }
            case "snap" -> {
                if (keyMatcher.group(group3) == null)
                    mode.comboMap.snap.parsePropertyReference(propertyKey, propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid snap property key");
                else {
                    switch (keyMatcher.group(group4)) {
                        // @formatter:off
                        case "up" -> setCommand(mode.comboMap.snap.builder,  propertyValue, new SnapUp(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "down" -> setCommand(mode.comboMap.snap.builder,  propertyValue, new SnapDown(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "left" -> setCommand(mode.comboMap.snap.builder,  propertyValue, new SnapLeft(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "right" -> setCommand(mode.comboMap.snap.builder,  propertyValue, new SnapRight(), defaultComboMoveDuration, keyAliases, appAliases);
                        // @formatter:on
                    }
                }
            }
            case "shrink-grid" -> {
                if (keyMatcher.group(group3) == null)
                    mode.comboMap.shrinkGrid.parsePropertyReference(propertyKey,
                            propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid shrink-grid property key");
                else {
                    switch (keyMatcher.group(group4)) {
                        // @formatter:off
                        case "up" -> setCommand(mode.comboMap.shrinkGrid.builder,  propertyValue, new ShrinkGridUp(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "down" -> setCommand(mode.comboMap.shrinkGrid.builder,  propertyValue, new ShrinkGridDown(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "left" -> setCommand(mode.comboMap.shrinkGrid.builder,  propertyValue, new ShrinkGridLeft(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "right" -> setCommand(mode.comboMap.shrinkGrid.builder,  propertyValue, new ShrinkGridRight(), defaultComboMoveDuration, keyAliases, appAliases);
                        // @formatter:on
                    }
                }
            }
            case "move-grid" -> {
                if (keyMatcher.group(group3) == null)
                    mode.comboMap.moveGrid.parsePropertyReference(propertyKey,
                            propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid move-grid property key");
                else {
                    switch (keyMatcher.group(group4)) {
                        // @formatter:off
                        case "up" -> setCommand(mode.comboMap.moveGrid.builder,  propertyValue, new MoveGridUp(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "down" -> setCommand(mode.comboMap.moveGrid.builder,  propertyValue, new MoveGridDown(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "left" -> setCommand(mode.comboMap.moveGrid.builder,  propertyValue, new MoveGridLeft(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "right" -> setCommand(mode.comboMap.moveGrid.builder,  propertyValue, new MoveGridRight(), defaultComboMoveDuration, keyAliases, appAliases);
                        // @formatter:on
                    }
                }
            }
            case "position-history" -> {
                if (keyMatcher.group(group3) == null)
                    mode.wheel.parsePropertyReference(propertyKey, propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid position-history property key");
                else {
                    switch (keyMatcher.group(group4)) {
                        // @formatter:off
                        case "save-position" -> setCommand(mode.comboMap.savePosition.builder,  propertyValue, new SavePosition(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "unsave-position" -> setCommand(mode.comboMap.unsavePosition.builder,  propertyValue, new UnsavePosition(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "clear" -> setCommand(mode.comboMap.clearPositionHistory.builder,  propertyValue, new ClearPositionHistory(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "cycle-next" -> setCommand(mode.comboMap.cycleNextPosition.builder,  propertyValue, new CycleNextPosition(), defaultComboMoveDuration, keyAliases, appAliases);
                        case "cycle-previous" -> setCommand(mode.comboMap.cyclePreviousPosition.builder,  propertyValue, new CyclePreviousPosition(), defaultComboMoveDuration, keyAliases, appAliases);
                        // @formatter:on
                        default -> throw new IllegalArgumentException(
                                "Invalid position-history property key");
                    }
                }
            }
            // @formatter:off
            case "move-to-grid-center" -> {
                mode.comboMap.moveToGridCenter.parseReferenceOr(propertyKey, propertyValue,
                        commandsByCombo -> setCommand(mode.comboMap.moveToGridCenter.builder,  propertyValue, new MoveToGridCenter(), finalDefaultComboMoveDuration, keyAliases, appAliases),
                        childPropertiesByParentProperty, nonRootPropertyKeys);
            }
            case "move-to-last-selected-hint" -> {
                mode.comboMap.moveToLastSelectedHint.parseReferenceOr(propertyKey, propertyValue,
                        commandsByCombo -> setCommand(mode.comboMap.moveToLastSelectedHint.builder,  propertyValue, new MoveToLastSelectedHint(), finalDefaultComboMoveDuration, keyAliases, appAliases),
                        childPropertiesByParentProperty, nonRootPropertyKeys);
            }
            case "break-combo-preparation" -> mode.comboMap.breakComboPreparation.parseReferenceOr(propertyKey, propertyValue,
                    commandsByCombo -> setCommand(mode.comboMap.breakComboPreparation.builder,  propertyValue, new BreakComboPreparation(), defaultComboMoveDuration, keyAliases, appAliases),
                    childPropertiesByParentProperty, nonRootPropertyKeys);
            // @formatter:on
            default -> throw new IllegalArgumentException(
                    "Invalid mode property key");
        }
    }

    private static HintMouseMovement parseHintMouseMovement(String propertyKey, String propertyValue) {
        return switch (propertyValue) {
            case "no-movement" -> HintMouseMovement.NO_MOVEMENT;
            case "mouse-follows-selected-hint" -> HintMouseMovement.MOUSE_FOLLOWS_SELECTED_HINT;
            case "mouse-follows-hint-grid-center" -> HintMouseMovement.MOUSE_FOLLOWS_HINT_GRID_CENTER;
            default -> throw new IllegalArgumentException(
                    "Invalid property value in " + propertyKey + "=" + propertyValue +
                    ": mouse-movement should be one of " +
                    List.of("no-movement", "mouse-follows-selected-hint",
                            "mouse-follows-hint-grid-center"));
        };
    }

    private static ViewportFilter parseViewportFilter(String string) {
        if (string == null)
            return AnyViewportFilter.ANY_VIEWPORT_FILTER;
        // 1920x1080
        Matcher resolutionMatcher = Pattern.compile("(\\d+)x(\\d+)").matcher(string);
        if (resolutionMatcher.matches()) {
            return new FixedViewportFilter(new Viewport(
                    Integer.parseUnsignedInt(resolutionMatcher.group(1)),
                    Integer.parseUnsignedInt(resolutionMatcher.group(2)),
                    -1f
            ));
        }
        // 1920x1080-100%
        Matcher resolutionScaleMatcher = Pattern.compile("(\\d+)x(\\d+)-(\\d+)%").matcher(string);
        if (!resolutionScaleMatcher.matches()) {
            throw new IllegalArgumentException("Invalid viewport filter " + string +
                                               ": expected formats 1920x1080 or 1920x1080-100%");
        }
        return new FixedViewportFilter(new Viewport(
                Integer.parseUnsignedInt(resolutionScaleMatcher.group(1)),
                Integer.parseUnsignedInt(resolutionScaleMatcher.group(2)),
                Integer.parseUnsignedInt(resolutionScaleMatcher.group(3)) / 100d
        ));
    }

    private static void parseModeReference(String propertyKey, String propertyValue,
                                           Map<String, Set<String>> childModesByParentMode,
                                           Set<String> nonRootModes) {
        String propertyKeyMode = propertyKey;
        // x-mode=normal-mode
        String propertyValueMode = propertyValue;
        if (propertyValueMode.equals(propertyKeyMode))
            throw new IllegalArgumentException(
                    "Invalid mode reference " + propertyKey + "=" +
                    propertyValue + ": a mode cannot reference itself");
        if (!propertyValueMode.endsWith("-mode"))
            throw new IllegalArgumentException(
                    "Invalid parent mode name " + propertyValueMode +
                    ": mode names should end with -mode");
        childModesByParentMode.computeIfAbsent(propertyValueMode,
                mode -> new HashSet<>()).add(propertyKeyMode);
        nonRootModes.add(propertyKeyMode);
    }

    private static String parseFontName(String fontName, Predicate<String> fontAvailability) {
        if (!fontAvailability.test(fontName)) {
            logger.info("Font " + fontName + " not found, falling back to Consolas");
            return "Consolas";
        }
        return fontName;
    }

    private static Map<String, KeyAlias> buildKeyAliasesForActiveKeyboardLayout(
            Map<String, LayoutKeyAlias> configurationLayoutKeyAliasByName,
            KeyboardLayout activeKeyboardLayout) {
        Map<String, KeyAlias> keyAliases = new HashMap<>();
        for (Map.Entry<String, LayoutKeyAlias> entry : configurationLayoutKeyAliasByName.entrySet()) {
            String aliasName = entry.getKey();
            LayoutKeyAlias layoutKeyAlias = entry.getValue();
            KeyAlias alias = findKeyAliasForLayout(activeKeyboardLayout, layoutKeyAlias,
                    aliasName);
            keyAliases.put(aliasName, alias);
        }
        return keyAliases;
    }

    private static KeyAlias findKeyAliasForLayout(KeyboardLayout activeKeyboardLayout,
                                                  LayoutKeyAlias layoutKeyAlias, String aliasName) {
        KeyAlias keyAlias = layoutKeyAlias.aliasByLayout.get(activeKeyboardLayout);
        if (keyAlias == null) {
            keyAlias = layoutKeyAlias.noLayoutAlias;
            if (keyAlias == null) {
                KeyboardLayout layoutForWhichAliasIsDefined =
                        layoutKeyAlias.aliasByLayout.keySet()
                                                    .stream()
                                                    .sorted(Comparator.comparing(
                                                            KeyboardLayout::displayName))
                                                    .findFirst()
                                                    .orElseThrow();
                KeyAlias keyAliasForOtherLayout =
                        layoutKeyAlias.aliasByLayout.get(layoutForWhichAliasIsDefined);
                List<Key> keys = new ArrayList<>();
                for (Key keyForOtherLayout : keyAliasForOtherLayout.keys()) {
                    int scanCode = layoutForWhichAliasIsDefined.scanCode(
                            keyForOtherLayout);
                    Key key = activeKeyboardLayout.keyFromScanCode(scanCode);
                    if (key == null)
                        // This key from the other layout does not have an equivalent in the active layout.
                        throw new IllegalArgumentException("Key alias " + aliasName +
                                                           " is not defined for the active keyboard layout " +
                                                           activeKeyboardLayout);
                    keys.add(key);
                }
                keyAlias = new KeyAlias(keyAliasForOtherLayout.name(), keys);
            }
        }
        return keyAlias;
    }

    private static Aliases parseAliases(List<String> properties) {
        Map<String, LayoutKeyAlias> layoutKeyAliasByName = new HashMap<>();
        Map<String, AppAlias> appAliasByName = new HashMap<>();
        Set<String> visitedPropertyKeys = new HashSet<>();
        for (String line : properties) {
            checkPropertyLineCorrectness(line, visitedPropertyKeys);
            Matcher lineMatcher = propertyLinePattern.matcher(line);
            //noinspection ResultOfMethodCallIgnored
            lineMatcher.matches();
            String propertyKey = lineMatcher.group(1).strip();
            String propertyValue = lineMatcher.group(2).strip();
            try {
                parseAlias(propertyKey, propertyValue, appAliasByName, layoutKeyAliasByName);
            } catch (IllegalArgumentException e) {
                IllegalArgumentException e2 =
                        new IllegalArgumentException("[" + propertyKey + "] " + e.getMessage());
                e2.setStackTrace(e.getStackTrace());
                throw e2;
            }
        }
        return new Aliases(layoutKeyAliasByName, appAliasByName);
    }

    private static void parseAlias(String propertyKey, String propertyValue,
                                  Map<String, AppAlias> appAliasByName,
                                  Map<String, LayoutKeyAlias> layoutKeyAliasByName) {
        if (propertyKey.startsWith("app-alias.")) {
            String aliasName = propertyKey.substring("app-alias.".length());
            Set<App> apps = Arrays.stream(propertyValue.split("\\s+"))
                                  .map(App::new)
                                  .collect(Collectors.toSet());
            appAliasByName.put(aliasName, new AppAlias(aliasName, apps));
        }
        else if (propertyKey.startsWith("key-alias.")) {
            // key-alias.left=a
            // key-alias.left.us-qwerty=a
            Pattern modeKeyPattern = Pattern.compile("key-alias\\.([^.]+)(\\.([^.]+))?");
            Matcher keyMatcher = modeKeyPattern.matcher(propertyKey);
            if (!keyMatcher.matches())
                throw new IllegalArgumentException(
                        "Invalid key-alias property key");
            // List and not Set because hint.selection-keys=hintkeys needs ordering.
            List<Key> keys = Arrays.stream(propertyValue.split("\\s+"))
                                   .map(Key::ofName)
                                   .toList();
            String aliasName = keyMatcher.group(1);
            if (keyMatcher.group(2) == null) {
                layoutKeyAliasByName.computeIfAbsent(aliasName,
                        name -> new LayoutKeyAlias()).noLayoutAlias =
                        new KeyAlias(aliasName, keys);
            }
            else {
                String layoutName = keyMatcher.group(3);
                KeyboardLayout layout = parseKeyboardLayout(layoutName);
                layoutKeyAliasByName.computeIfAbsent(aliasName,
                                            name -> new LayoutKeyAlias())
                        .aliasByLayout.put(layout, new KeyAlias(aliasName, keys));
            }
        }
    }

    private static KeyboardLayout parseKeyboardLayout(String layoutName) {
        KeyboardLayout layout =
                KeyboardLayout.keyboardLayoutByShortName.get(layoutName);
        if (layout == null) {
            String layoutIdentifier = layoutName;
            layout = KeyboardLayout.keyboardLayoutByIdentifier.get(layoutIdentifier);
            if (layout == null)
                throw new IllegalArgumentException(
                        "Invalid keyboard layout: " + layoutName +
                        ", available keyboard layouts: " +
                        KeyboardLayout.keyboardLayoutByShortName.keySet());
        }
        return layout;
    }

    private static void checkPropertyLineCorrectness(String property, Set<String> visitedPropertyKeys) {
        Matcher lineMatcher = propertyLinePattern.matcher(property);
        if (!lineMatcher.matches())
            throw new IllegalArgumentException("Invalid property " + property +
                                               ": expected <property key>=<property value>");
        String propertyKey = lineMatcher.group(1).strip();
        String propertyValue = lineMatcher.group(2).strip();
        if (propertyKey.isEmpty())
            throw new IllegalArgumentException(
                    "Invalid property " + property + ": property key cannot be blank");
        if (propertyValue.isEmpty())
            throw new IllegalArgumentException(
                    "Invalid property " + property + ": property value cannot be blank");
        if (!visitedPropertyKeys.add(propertyKey))
            throw new IllegalArgumentException(
                    "Property " + propertyKey + " is defined twice");
    }

    private static void checkMissingProperties(ModeBuilder mode) {
        if (mode.timeout.builder.enabled() != null && mode.timeout.builder.enabled() &&
            (mode.timeout.builder.duration() == null ||
             mode.timeout.builder.modeName() == null ||
             mode.timeout.builder.onlyIfIdle() == null))
            throw new IllegalArgumentException(
                    "Definition of timeout for " + mode.modeName +
                    " is incomplete: expected " +
                    List.of("enabled", "duration", "mode", "only-if-idle"));
        if (mode.hideCursor.builder.enabled() != null && mode.hideCursor.builder.enabled() &&
            mode.hideCursor.builder.idleDuration() == null)
            throw new IllegalArgumentException(
                    "Definition of hide-cursor for " + mode.modeName +
                    " is incomplete: expected " + List.of("enabled", "duration"));
        GridArea.GridAreaBuilder gridArea = mode.grid.builder.area();
        if (gridArea.widthPercent() == null || gridArea.heightPercent() == null)
            throw new IllegalArgumentException(
                    "Definition of grid for " + mode.modeName +
                    " is incomplete: expected " +
                    List.of("row-count", "column-count"));
        HintMeshType.HintMeshTypeBuilder hintMeshType =
                mode.hintMesh.builder.type();
        if (hintMeshType.type() != null) {
            switch (hintMeshType.type()) {
                case GRID -> {
                    HintGridLayoutBuilder defaultLayout =
                            hintMeshType.gridLayout(AnyViewportFilter.ANY_VIEWPORT_FILTER);
                    if (defaultLayout.maxRowCount() == null ||
                        defaultLayout.maxColumnCount() == null ||
                        defaultLayout.cellWidth() == null ||
                        defaultLayout.cellHeight() == null ||
                        defaultLayout.layoutRowCount() == null ||
                        defaultLayout.layoutColumnCount() == null ||
                        defaultLayout.layoutRowOriented() == null
                    )
                        throw new IllegalArgumentException(
                                "Definition of hint for " + mode.modeName +
                                " is incomplete: expected " +
                                List.of("grid-max-row-count", "grid-max-column-count",
                                        "grid-cell-width", "grid-cell-height",
                                        "layout-row-count", "layout-column-count",
                                        "layout-row-oriented"));
                }
                case POSITION_HISTORY -> {
                    // No op.
                }
            }
        }
        HintGridArea.HintGridAreaBuilder hintGridArea = hintMeshType.gridArea();
        if (hintGridArea.type() != null) {
            switch (hintGridArea.type()) {
                case ACTIVE_SCREEN -> {
                    if (hintGridArea.activeScreenHintGridAreaCenter() == null)
                        throw new IllegalArgumentException(
                                "Definition of active-screen hint.grid-area for " +
                                mode.modeName + " is incomplete: expected " +
                                List.of("active-screen-grid-area-center"));
                }
                case ACTIVE_WINDOW -> {
                    // No op.
                }
                case ALL_SCREENS -> {
                    // No op.
                }
            }
        }
    }

    private static String checkModeReference(String modeNameReference) {
        if (modeNameReference.startsWith("_"))
            throw new IllegalArgumentException(
                    "Referencing an abstract mode (a mode starting with _) is not allowed: " +
                    modeNameReference);
        return modeNameReference;
    }

    private static void recursivelyExtendProperty(Property<?> parentProperty, PropertyNode propertyNode,
                                                  Map<PropertyKey, Property<?>> propertyByKey,
                                                  Map<String, Set<String>> referencedModesByReferencerMode) {
        Property<?> property = propertyByKey.get(propertyNode.propertyKey);
        // I believe there are some valid use cases for inheritance chains:
        // hint2-2-then-click-mode.hint -> hint2-2-mode.hint -> hint1.mode.hint
/*
        if (parentProperty.parentPropertyKey != null) {
            throw new IllegalArgumentException(
                    "Referencing a mode's property that itself references another mode's property is not allowed: " +
                    Stream.of(property.propertyKey, parentProperty.propertyKey,
                                  parentProperty.parentPropertyKey)
                          .map(PropertyKey::toString)
                          .collect(Collectors.joining(" -> ")));
        }
*/
        property.extend(parentProperty.builder);
        for (PropertyNode childPropertyNode : propertyNode.childProperties)
            recursivelyExtendProperty(property, childPropertyNode, propertyByKey,
                    referencedModesByReferencerMode);
    }

    private static PropertyNode recursivelyBuildPropertyNode(PropertyKey propertyKey,
                                                             Map<PropertyKey, Set<PropertyKey>> childPropertiesByParentProperty,
                                                             Map<String, Set<String>> childModesByParentMode,
                                                             Set<PropertyKey> nonRootPropertyKeys,
                                                             Set<PropertyKey> alreadyBuiltPropertyNodeKeys) {
        if (!alreadyBuiltPropertyNodeKeys.add(propertyKey))
            throw new IllegalArgumentException(
                    "Found property dependency cycle involving property key " +
                    propertyKey);
        List<PropertyNode> childrenProperties = new ArrayList<>();
        Set<PropertyKey> childPropertyKeys = new HashSet<>();
        Set<String> childModes = childModesByParentMode.getOrDefault(propertyKey.modeName, Set.of());
        for (String childMode : childModes) {
            // x2-mode=x1-mode
            // x2-mode.to=y-mode.to
            // x2-mode should not have any of the x1-mode.to. Instead, x2-mode.to should be exactly y-mode.to.
            // Assuming that here, propertyKey.modeName == x1-mode,
            // Only create childPropertyKey(x2-mode, to) if there is no x2-mode.to=y-mode.to
            PropertyKey childPropertyKey = new PropertyKey(childMode, propertyKey.propertyName);
            if (!nonRootPropertyKeys.contains(childPropertyKey))
                childPropertyKeys.add(childPropertyKey);
        }
        childPropertyKeys.addAll(childPropertiesByParentProperty.getOrDefault(propertyKey, Set.of()));
        for (PropertyKey childPropertyKey : childPropertyKeys)
            childrenProperties.add(recursivelyBuildPropertyNode(childPropertyKey,
                    childPropertiesByParentProperty, childModesByParentMode,
                    nonRootPropertyKeys, alreadyBuiltPropertyNodeKeys));
        return new PropertyNode(propertyKey, childrenProperties);
    }

    private static ModeNode recursivelyBuildReferenceNode(String modeName,
                                                            Map<String, Set<String>> referencedModesByReferencerMode,
                                                            Map<String, ModeNode> nodeByName) {
        ModeNode modeNode = nodeByName.computeIfAbsent(modeName,
                modeName_ -> new ModeNode(modeName, new ArrayList<>()));
        List<ModeNode> referencedModes = modeNode.referencedModes;
        Set<String> referencedModeNames = referencedModesByReferencerMode.get(modeName);
        if (referencedModeNames != null) {
            for (String referencedModeName : referencedModeNames) {
                if (referencedModeName.equals(Mode.PREVIOUS_MODE_FROM_HISTORY_STACK_IDENTIFIER))
                    continue;
                ModeNode referencedNode = nodeByName.get(referencedModeName);
                if (referencedNode == null)
                    referencedNode = recursivelyBuildReferenceNode(referencedModeName,
                            referencedModesByReferencerMode, nodeByName);
                referencedModes.add(referencedNode);
            }
        }
        return new ModeNode(modeName, referencedModes);
    }

    private static String checkColorFormat(String propertyValue) {
        if (!propertyValue.matches("^#?([a-fA-F0-9]{6})$"))
            throw new IllegalArgumentException(
                    "Invalid color " + propertyValue +
                    ": a color should be in the #FFFFFF format");
        return propertyValue;
    }

    private static int parseUnsignedInteger(String propertyValue, int min, int max) {
        int integer = Integer.parseUnsignedInt(propertyValue);
        if (integer < min)
            throw new IllegalArgumentException(
                    "Invalid property value " + integer + ": " +
                    " must greater than or equal to " + min);
        if (integer > max)
            throw new IllegalArgumentException(
                    "Invalid property value in " + integer + ": " +
                    " must be less than or equal to " + max);
        return integer;
    }

    private static double parseNonZeroPercent(String propertyValue,
                                              double max) {
        return parseDouble(propertyValue, false, 0, max);
    }

    private static double parseDouble(String propertyValue, boolean minIncluded,
                                      double min, double max) {
        double percent = Double.parseDouble(propertyValue);
        if (percent < min || percent == min && !minIncluded)
            throw new IllegalArgumentException(
                    "Invalid property value " + percent +
                    ": must greater than " + min);
        if (percent > max)
            throw new IllegalArgumentException(
                    "Invalid property value " + percent +
                    ": must be less than or equal to " + max);
        return percent;
    }

    private static List<Key> parseHintKeys(String propertyValue,
                                           Map<String, KeyAlias> keyAliases) {
        KeyAlias alias = keyAliases.get(propertyValue);
        if (alias != null)
            return List.copyOf(alias.keys());
        String[] split = propertyValue.split("\\s+");
        List<Key> hintKeys = Arrays.stream(split).map(Key::ofName).toList();
        if (hintKeys.size() <= 1)
            // Even 1 key is not enough because we use fixed-length hints.
            throw new IllegalArgumentException(
                    "Invalid hint keys " + propertyValue +
                    ": at least two keys are required");
        return hintKeys;
    }

    private static Set<Key> parseKeyOrAlias(String propertyValue,
                                             Map<String, KeyAlias> keyAliases) {
        KeyAlias alias = keyAliases.get(propertyValue);
        if (alias != null)
            return Set.copyOf(alias.keys());
        return Set.of(Key.ofName(propertyValue));
    }

    private static HintMeshType.HintMeshTypeType parseHintMeshTypeType(String propertyKey,
                                                                       String propertyValue) {
        return switch (propertyValue) {
            case "grid" -> HintMeshType.HintMeshTypeType.GRID;
            case "position-history" ->
                    HintMeshType.HintMeshTypeType.POSITION_HISTORY;
            default -> throw new IllegalArgumentException(
                    "Invalid property value in " + propertyKey + "=" + propertyValue +
                    ": type should be one of " +
                    List.of("grid", "position-history"));
        };
    }

    private static HintGridAreaType parseHintGridAreaType(String propertyKey, String propertyValue) {
        return switch (propertyValue) {
            case "active-screen" -> HintGridAreaType.ACTIVE_SCREEN;
            case "active-window" -> HintGridAreaType.ACTIVE_WINDOW;
            case "all-screens" -> HintGridAreaType.ALL_SCREENS;
            default -> throw new IllegalArgumentException(
                    "Invalid property value in " + propertyKey + "=" + propertyValue +
                    ": type should be one of " +
                    List.of("active-screen", "active-window", "all-screens"));
        };
    }

    private static ActiveScreenHintGridAreaCenter parseActiveScreenHintGridAreaCenter(
            String propertyKey, String propertyValue) {
        return switch (propertyValue) {
            case "screen-center" -> ActiveScreenHintGridAreaCenter.SCREEN_CENTER;
            case "mouse" -> ActiveScreenHintGridAreaCenter.MOUSE;
            case "last-selected-hint" -> ActiveScreenHintGridAreaCenter.LAST_SELECTED_HINT;
            default -> throw new IllegalArgumentException(
                    "Invalid property value in " + propertyKey + "=" + propertyValue +
                    ": expected one of " + List.of("screen-center", "mouse", "last-selected-hint"));
        };
    }

    private static GridAreaType parseGridAreaType(String propertyKey,
                                                  String propertyValue) {
        return switch (propertyValue) {
            case "active-screen" -> GridAreaType.ACTIVE_SCREEN;
            case "active-window" -> GridAreaType.ACTIVE_WINDOW;
            default -> throw new IllegalArgumentException(
                    "Invalid property value in " + propertyKey + "=" + propertyValue +
                    ": type should be one of " +
                    List.of("active-screen", "active-window"));
        };
    }

    private static Synchronization parseSynchronization(String propertyKey, String propertyValue) {
        return switch (propertyValue) {
            case "mouse-and-grid-center-unsynchronized" ->
                    Synchronization.MOUSE_AND_GRID_CENTER_UNSYNCHRONIZED;
            case "mouse-follows-grid-center" -> Synchronization.MOUSE_FOLLOWS_GRID_CENTER;
            case "grid-center-follows-mouse" -> Synchronization.GRID_CENTER_FOLLOWS_MOUSE;
            default -> throw new IllegalArgumentException(
                    "Invalid property value in " + propertyKey + "=" + propertyValue +
                    ": expected one of " + List.of("mouse-and-grid-center-unsynchronized",
                            "mouse-follows-grid-center", "grid-center-follows-mouse"));
        };
    }

    private static ZoomCenter parseZoomCenter(String propertyKey, String propertyValue) {
        return switch (propertyValue) {
            case "screen-center" -> ZoomCenter.SCREEN_CENTER;
            case "mouse" -> ZoomCenter.MOUSE;
            case "last-selected-hint" -> ZoomCenter.LAST_SELECTED_HINT;
            default -> throw new IllegalArgumentException(
                    "Invalid property value in " + propertyKey + "=" + propertyValue +
                    ": expected one of " +
                    List.of("screen-center", "mouse", "last-selected-hint"));
        };
    }

    private static ComboMoveDuration parseComboMoveDuration(String propertyKey, String propertyValue) {
        String[] split = propertyValue.split("-");
        if (split.length != 2)
            throw new IllegalArgumentException(
                    "Invalid property value in " + propertyKey + "=" + propertyValue +
                    ": expected <min millis>-<max millis>");
        return new ComboMoveDuration(
                Duration.ofMillis(Integer.parseUnsignedInt(split[0])),
                Duration.ofMillis(Integer.parseUnsignedInt(split[1])));
    }

    private static Duration parseDuration(String string) {
        return Duration.ofMillis(Integer.parseUnsignedInt(string));
    }

    private static void setCommand(Map<Combo, List<Command>> commandsByCombo,
                                          String multiComboString, Command command,
                                          ComboMoveDuration defaultComboMoveDuration,
                                          Map<String, KeyAlias> keyAliases,
                                          Map<String, AppAlias> appAliases) {
        List<Combo> combos =
                parseCombos(multiComboString, defaultComboMoveDuration, keyAliases,
                        appAliases);
        for (Combo combo : combos)
            commandsByCombo.computeIfAbsent(combo, combo1 -> new ArrayList<>())
                           .add(command);
    }

    private static List<Combo> parseCombos(String multiComboString,
                                         ComboMoveDuration defaultComboMoveDuration,
                                         Map<String, KeyAlias> keyAliases,
                                         Map<String, AppAlias> appAliases) {
        List<AliasResolvedCombo> aliasResolvedCombos =
                Combo.multiCombo(multiComboString, defaultComboMoveDuration, keyAliases,
                        appAliases);
        List<Combo> combos =
                aliasResolvedCombos.stream().map(AliasResolvedCombo::combo).toList();
        return combos;
    }

    /**
     * Dependency tree.
     */
    private record ModeNode(String modeName, List<ModeNode> referencedModes) {
    }

    private record PropertyNode(PropertyKey propertyKey,
                                List<PropertyNode> childProperties) {
    }

    @SuppressWarnings("unchecked")
    private static final class ModeBuilder {
        final String modeName;
        Property<AtomicReference<Boolean>> stopCommandsFromPreviousMode;
        Property<AtomicReference<Boolean>> pushModeToHistoryStack;
        Property<AtomicReference<String>> modeAfterUnhandledKeyPress;
        ComboMapConfigurationBuilder comboMap;
        Property<MouseBuilder> mouse;
        Property<WheelBuilder> wheel;
        Property<GridConfigurationBuilder> grid;
        Property<HintMeshConfigurationBuilder> hintMesh;
        Property<ModeTimeoutBuilder> timeout;
        Property<IndicatorConfigurationBuilder> indicator;
        Property<HideCursorBuilder> hideCursor;
        Property<ZoomConfigurationBuilder> zoom;

        private ModeBuilder(String modeName,
                            Map<PropertyKey, Property<?>> propertyByKey) {
            this.modeName = modeName;
            comboMap = new ComboMapConfigurationBuilder(modeName, propertyByKey);
            stopCommandsFromPreviousMode = new Property<>("stop-commands-from-previous-mode", modeName,
                    propertyByKey, new AtomicReference<>()) {
                @Override
                void extend(Object parent_) {
                    AtomicReference<Boolean> parent = (AtomicReference<Boolean>) parent_;
                    if (builder.get() == null)
                        builder.set(parent.get());
                }
            };
            pushModeToHistoryStack = new Property<>("push-mode-to-history-stack", modeName,
                    propertyByKey, new AtomicReference<>()) {
                @Override
                void extend(Object parent_) {
                    AtomicReference<Boolean> parent = (AtomicReference<Boolean>) parent_;
                    if (builder.get() == null)
                        builder.set(parent.get());
                }
            };
            modeAfterUnhandledKeyPress = new Property<>("mode-after-unhandled-key-press", modeName,
                    propertyByKey, new AtomicReference<>()) {
                @Override
                void extend(Object parent_) {
                    AtomicReference<String> parent = (AtomicReference<String>) parent_;
                    if (builder.get() == null)
                        builder.set(parent.get());
                }
            };
            mouse = new Property<>("mouse", modeName, propertyByKey, new MouseBuilder()) {
                @Override
                void extend(Object parent_) {
                    MouseBuilder parent = (MouseBuilder) parent_;
                    if (builder.initialVelocity() == null)
                        builder.initialVelocity(parent.initialVelocity());
                    if (builder.maxVelocity() == null)
                        builder.maxVelocity(parent.maxVelocity());
                    if (builder.acceleration() == null)
                        builder.acceleration(parent.acceleration());
                    if (builder.smoothJumpEnabled() == null)
                        builder.smoothJumpEnabled(parent.smoothJumpEnabled());
                    if (builder.smoothJumpVelocity() == null)
                        builder.smoothJumpVelocity(parent.smoothJumpVelocity());
                }
            };
            wheel = new Property<>("wheel", modeName, propertyByKey, new WheelBuilder()) {
                @Override
                void extend(Object parent_) {
                    WheelBuilder parent = (WheelBuilder) parent_;
                    if (builder.initialVelocity() == null)
                        builder.initialVelocity(parent.initialVelocity());
                    if (builder.maxVelocity() == null)
                        builder.maxVelocity(parent.maxVelocity());
                    if (builder.acceleration() == null)
                        builder.acceleration(parent.acceleration());
                }
            };
            grid = new Property<>("grid", modeName, propertyByKey,
                    new GridConfigurationBuilder()) {
                @Override
                void extend(Object parent_) {
                    GridConfigurationBuilder parent = (GridConfigurationBuilder) parent_;
                    if (builder.area().type() == null)
                        builder.area().type(parent.area().type());
                    if (builder.area().widthPercent() == null)
                        builder.area().widthPercent(parent.area().widthPercent());
                    if (builder.area().heightPercent() == null)
                        builder.area().heightPercent(parent.area().heightPercent());
                    if (builder.area().topInset() == null)
                        builder.area().topInset(parent.area().topInset());
                    if (builder.area().bottomInset() == null)
                        builder.area().bottomInset(parent.area().bottomInset());
                    if (builder.area().leftInset() == null)
                        builder.area().leftInset(parent.area().leftInset());
                    if (builder.area().rightInset() == null)
                        builder.area().rightInset(parent.area().rightInset());
                    if (builder.synchronization() == null)
                        builder.synchronization(parent.synchronization());
                    if (builder.rowCount() == null)
                        builder.rowCount(parent.rowCount());
                    if (builder.columnCount() == null)
                        builder.columnCount(parent.columnCount());
                    if (builder.lineVisible() == null)
                        builder.lineVisible(parent.lineVisible());
                    if (builder.lineHexColor() == null)
                        builder.lineHexColor(parent.lineHexColor());
                    if (builder.lineThickness() == null)
                        builder.lineThickness(parent.lineThickness());
                }
            };
            hintMesh = new HintMeshProperty(modeName, propertyByKey);
            timeout = new Property<>("timeout", modeName, propertyByKey,
                    new ModeTimeoutBuilder()) {
                @Override
                void extend(Object parent_) {
                    ModeTimeoutBuilder parent = (ModeTimeoutBuilder) parent_;
                    if (builder.enabled() == null)
                        builder.enabled(parent.enabled());
                    if (builder.duration() == null)
                        builder.duration(parent.duration());
                    if (builder.modeName() == null)
                        builder.modeName(parent.modeName());
                    if (builder.onlyIfIdle() == null)
                        builder.onlyIfIdle(parent.onlyIfIdle());
                }
            };
            indicator = new Property<>("indicator", modeName, propertyByKey,
                    new IndicatorConfigurationBuilder()) {
                @Override
                void extend(Object parent_) {
                    IndicatorConfigurationBuilder parent =
                            (IndicatorConfigurationBuilder) parent_;
                    if (builder.enabled() == null)
                        builder.enabled(parent.enabled());
                    if (builder.size() == null)
                        builder.size(parent.size());
                    if (builder.idleHexColor() == null)
                        builder.idleHexColor(parent.idleHexColor());
                    if (builder.moveHexColor() == null)
                        builder.moveHexColor(parent.moveHexColor());
                    if (builder.wheelHexColor() == null)
                        builder.wheelHexColor(parent.wheelHexColor());
                    if (builder.mousePressHexColor() == null)
                        builder.mousePressHexColor(parent.mousePressHexColor());
                    if (builder.leftMousePressHexColor() == null)
                        builder.leftMousePressHexColor(parent.leftMousePressHexColor());
                    if (builder.middleMousePressHexColor() == null)
                        builder.middleMousePressHexColor(parent.middleMousePressHexColor());
                    if (builder.rightMousePressHexColor() == null)
                        builder.rightMousePressHexColor(parent.rightMousePressHexColor());
                    if (builder.unhandledKeyPressHexColor() == null)
                        builder.unhandledKeyPressHexColor(parent.unhandledKeyPressHexColor());
                }
            };
            hideCursor = new Property<>("hide-cursor", modeName, propertyByKey,
                    new HideCursorBuilder()) {
                @Override
                void extend(Object parent_) {
                    HideCursorBuilder parent = (HideCursorBuilder) parent_;
                    if (builder.enabled() == null)
                        builder.enabled(parent.enabled());
                    if (builder.idleDuration() == null)
                        builder.idleDuration(parent.idleDuration());
                }
            };
            zoom = new Property<>("zoom", modeName, propertyByKey,
                    new ZoomConfigurationBuilder()) {
                @Override
                void extend(Object parent_) {
                    ZoomConfigurationBuilder parent = (ZoomConfigurationBuilder) parent_;
                    if (builder.percent() == null)
                        builder.percent(parent.percent());
                    if (builder.center() == null)
                        builder.center(parent.center());
                }
            };
        }

        public Mode build() {
            return new Mode(modeName, stopCommandsFromPreviousMode.builder.get(),
                    pushModeToHistoryStack.builder.get(),
                    modeAfterUnhandledKeyPress.builder.get(),
                    comboMap.build(),
                    mouse.builder.build(), wheel.builder.build(), grid.builder.build(),
                    hintMesh.builder.build(), timeout.builder.build(),
                    indicator.builder.build(), hideCursor.builder.build(),
                    zoom.builder.build());
        }

        private static class HintMeshProperty
                extends Property<HintMeshConfigurationBuilder> {

            public HintMeshProperty(String modeName,
                                    Map<PropertyKey, Property<?>> propertyByKey) {
                super("hint", modeName, propertyByKey,
                        new HintMeshConfigurationBuilder());
            }

            @Override
            void extend(Object parent_) {
                HintMeshConfigurationBuilder parent =
                        (HintMeshConfigurationBuilder) parent_;
                if (builder.enabled() == null)
                    builder.enabled(parent.enabled());
                if (builder.visible() == null)
                    builder.visible(parent.visible());
                if (builder.mouseMovement() == null)
                    builder.mouseMovement(parent.mouseMovement());
                if (builder.type().type() == null)
                    builder.type().type(parent.type().type());
                if (builder.type().gridArea().type() == null)
                    builder.type().gridArea().type(parent.type().gridArea().type());
                if (builder.type().gridArea().activeScreenHintGridAreaCenter() == null)
                    builder.type().gridArea().activeScreenHintGridAreaCenter(parent.type().gridArea().activeScreenHintGridAreaCenter());
                for (var parentEntry : parent.type()
                                             .gridLayoutByFilter()
                                             .map()
                                             .entrySet()
                                             .stream()
                                             .sorted(defaultFilterLastComparator())
                                             .toList()) {
                    HintGridLayoutBuilder parentLayout =
                            parentEntry.getValue();
                    ViewportFilter filter = parentEntry.getKey();
                    HintGridLayoutBuilder childLayout =
                            builder.type().gridLayout(filter);
                    ViewportFilterMapBuilder<HintGridLayoutBuilder, HintGridLayout>
                            childLayoutByFilter = builder.type().gridLayoutByFilter();
                    if (!childDoesNotNeedParentProperty(
                            HintGridLayoutBuilder::maxRowCount, childLayoutByFilter,
                            filter))
                        childLayout.maxRowCount(parentLayout.maxRowCount());
                    if (!childDoesNotNeedParentProperty(
                            HintGridLayoutBuilder::maxColumnCount,
                            childLayoutByFilter, filter))
                        childLayout.maxColumnCount(parentLayout.maxColumnCount());
                    if (!childDoesNotNeedParentProperty(
                            HintGridLayoutBuilder::cellWidth, childLayoutByFilter,
                            filter))
                        childLayout.cellWidth(parentLayout.cellWidth());
                    if (!childDoesNotNeedParentProperty(
                            HintGridLayoutBuilder::cellHeight, childLayoutByFilter,
                            filter))
                        childLayout.cellHeight(parentLayout.cellHeight());
                    if (!childDoesNotNeedParentProperty(
                            HintGridLayoutBuilder::layoutRowCount,
                            childLayoutByFilter, filter))
                        childLayout.layoutRowCount(parentLayout.layoutRowCount());
                    if (!childDoesNotNeedParentProperty(
                            HintGridLayoutBuilder::layoutColumnCount,
                            childLayoutByFilter, filter))
                        childLayout.layoutColumnCount(
                                parentLayout.layoutColumnCount());
                    if (!childDoesNotNeedParentProperty(
                            HintGridLayoutBuilder::layoutRowOriented,
                            childLayoutByFilter, filter))
                        childLayout.layoutRowOriented(
                                parentLayout.layoutRowOriented());
                }
                for (var parentEntry : parent.keysByFilter()
                                             .map()
                                             .entrySet().stream()
                                             .sorted(defaultFilterLastComparator())
                                             .toList()) {
                    HintMeshKeysBuilder parentKeys = parentEntry.getValue();
                    ViewportFilter filter = parentEntry.getKey();
                    HintMeshKeysBuilder childKeys = builder.keys(filter);
                    ViewportFilterMapBuilder<HintMeshKeysBuilder, HintMeshKeys>
                            childKeysByFilter = builder.keysByFilter();
                    if (!childDoesNotNeedParentProperty(
                            HintMeshKeysBuilder::selectionKeys, childKeysByFilter,
                            filter))
                        childKeys.selectionKeys(parentKeys.selectionKeys());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshKeysBuilder::rowKeyOffset, childKeysByFilter,
                            filter))
                        childKeys.rowKeyOffset(parentKeys.rowKeyOffset());
                }
                for (var parentEntry : parent.styleByFilter().map()
                                             .entrySet().stream()
                                             .sorted(defaultFilterLastComparator())
                                             .toList()) {
                    HintMeshStyleBuilder parentStyle =
                            parentEntry.getValue();
                    ViewportFilter filter = parentEntry.getKey();
                    HintMeshStyleBuilder childStyle = builder.style(filter);
                    ViewportFilterMapBuilder<HintMeshStyleBuilder, HintMeshStyle>
                            childStyleByFilter = builder.styleByFilter();
                    extendFontStyleProperties(HintMeshStyleBuilder::fontStyle, childStyle.fontStyle(), parentStyle.fontStyle(), childStyleByFilter, filter);
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::prefixInBackground,
                            childStyleByFilter, filter))
                        childStyle.prefixInBackground(
                                parentStyle.prefixInBackground());
                    extendFontStyleProperties(HintMeshStyleBuilder::prefixFontStyle, childStyle.prefixFontStyle(), parentStyle.prefixFontStyle(), childStyleByFilter, filter);
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::boxHexColor, childStyleByFilter,
                            filter))
                        childStyle.boxHexColor(parentStyle.boxHexColor());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::boxOpacity, childStyleByFilter,
                            filter))
                        childStyle.boxOpacity(parentStyle.boxOpacity());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::boxBorderThickness,
                            childStyleByFilter, filter))
                        childStyle.boxBorderThickness(
                                parentStyle.boxBorderThickness());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::boxBorderLength, childStyleByFilter,
                            filter))
                        childStyle.boxBorderLength(parentStyle.boxBorderLength());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::boxBorderHexColor,
                            childStyleByFilter, filter))
                        childStyle.boxBorderHexColor(parentStyle.boxBorderHexColor());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::boxBorderOpacity,
                            childStyleByFilter, filter))
                        childStyle.boxBorderOpacity(parentStyle.boxBorderOpacity());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::prefixBoxEnabled,
                            childStyleByFilter, filter))
                        childStyle.prefixBoxEnabled(
                                parentStyle.prefixBoxEnabled());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::prefixBoxBorderThickness,
                            childStyleByFilter, filter))
                        childStyle.prefixBoxBorderThickness(
                                parentStyle.prefixBoxBorderThickness());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::prefixBoxBorderLength, childStyleByFilter,
                            filter))
                        childStyle.prefixBoxBorderLength(parentStyle.prefixBoxBorderLength());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::prefixBoxBorderHexColor,
                            childStyleByFilter, filter))
                        childStyle.prefixBoxBorderHexColor(parentStyle.prefixBoxBorderHexColor());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::prefixBoxBorderOpacity,
                            childStyleByFilter, filter))
                        childStyle.prefixBoxBorderOpacity(parentStyle.prefixBoxBorderOpacity());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::boxWidthPercent, childStyleByFilter,
                            filter))
                        childStyle.boxWidthPercent(parentStyle.boxWidthPercent());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::boxHeightPercent,
                            childStyleByFilter, filter))
                        childStyle.boxHeightPercent(parentStyle.boxHeightPercent());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::subgridRowCount, childStyleByFilter,
                            filter))
                        childStyle.subgridRowCount(parentStyle.subgridRowCount());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::subgridColumnCount,
                            childStyleByFilter, filter))
                        childStyle.subgridColumnCount(
                                parentStyle.subgridColumnCount());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::subgridBorderThickness,
                            childStyleByFilter, filter))
                        childStyle.subgridBorderThickness(
                                parentStyle.subgridBorderThickness());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::subgridBorderLength,
                            childStyleByFilter, filter))
                        childStyle.subgridBorderLength(
                                parentStyle.subgridBorderLength());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::subgridBorderHexColor,
                            childStyleByFilter, filter))
                        childStyle.subgridBorderHexColor(
                                parentStyle.subgridBorderHexColor());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::subgridBorderOpacity,
                            childStyleByFilter, filter))
                        childStyle.subgridBorderOpacity(
                                parentStyle.subgridBorderOpacity());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::transitionAnimationEnabled,
                            childStyleByFilter, filter))
                        childStyle.transitionAnimationEnabled(
                                parentStyle.transitionAnimationEnabled());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::transitionAnimationDuration,
                            childStyleByFilter, filter))
                        childStyle.transitionAnimationDuration(
                                parentStyle.transitionAnimationDuration());
                }
                if (builder.modeAfterSelection() == null)
                    builder.modeAfterSelection(parent.modeAfterSelection());
                if (builder.eatUnusedSelectionKeys() == null)
                    builder.eatUnusedSelectionKeys(parent.eatUnusedSelectionKeys());
                if (builder.selectCombos() == null)
                    builder.selectCombos(parent.selectCombos());
                if (builder.unselectCombos() == null)
                    builder.unselectCombos(parent.unselectCombos());
            }

            private void extendFontStyleProperties(
                    Function<HintMeshStyleBuilder, FontStyleBuilder> fontStyleBuilderFunction,
                    FontStyleBuilder childFontStyle, FontStyleBuilder parentFontStyle,
                    ViewportFilterMapBuilder<HintMeshStyleBuilder, HintMeshStyle> childStyleByFilter,
                    ViewportFilter filter) {
                if (!fontStyleChildDoesNotNeedParentProperty(
                        fontStyleBuilderFunction,
                        FontStyleBuilder::name, childStyleByFilter,
                        filter))
                    childFontStyle.name(parentFontStyle.name());
                if (!fontStyleChildDoesNotNeedParentProperty(
                        fontStyleBuilderFunction,
                        FontStyleBuilder::weight, childStyleByFilter,
                        filter))
                    childFontStyle.weight(parentFontStyle.weight());
                if (!fontStyleChildDoesNotNeedParentProperty(
                        fontStyleBuilderFunction,
                        FontStyleBuilder::size, childStyleByFilter,
                        filter))
                    childFontStyle.size(parentFontStyle.size());
                if (!fontStyleChildDoesNotNeedParentProperty(
                        fontStyleBuilderFunction,
                        FontStyleBuilder::spacingPercent,
                        childStyleByFilter, filter))
                    childFontStyle.spacingPercent(parentFontStyle.spacingPercent());
                if (!fontStyleChildDoesNotNeedParentProperty(
                        fontStyleBuilderFunction,
                        FontStyleBuilder::hexColor, childStyleByFilter,
                        filter))
                    childFontStyle.hexColor(parentFontStyle.hexColor());
                if (!fontStyleChildDoesNotNeedParentProperty(
                        fontStyleBuilderFunction,
                        FontStyleBuilder::opacity, childStyleByFilter,
                        filter))
                    childFontStyle.opacity(parentFontStyle.opacity());
                if (!fontStyleChildDoesNotNeedParentProperty(
                        fontStyleBuilderFunction,
                        FontStyleBuilder::selectedFontHexColor, childStyleByFilter,
                        filter))
                    childFontStyle.selectedFontHexColor(parentFontStyle.selectedFontHexColor());
                if (!fontStyleChildDoesNotNeedParentProperty(
                        fontStyleBuilderFunction,
                        FontStyleBuilder::selectedFontOpacity, childStyleByFilter,
                        filter))
                    childFontStyle.selectedFontOpacity(parentFontStyle.selectedFontOpacity());
                if (!fontStyleChildDoesNotNeedParentProperty(
                        fontStyleBuilderFunction,
                        FontStyleBuilder::focusedFontHexColor, childStyleByFilter,
                        filter))
                    childFontStyle.focusedFontHexColor(parentFontStyle.focusedFontHexColor());
                if (!fontStyleChildDoesNotNeedParentProperty(
                        fontStyleBuilderFunction,
                        FontStyleBuilder::focusedFontOpacity, childStyleByFilter,
                        filter))
                    childFontStyle.focusedFontOpacity(parentFontStyle.focusedFontOpacity());
                if (!fontStyleChildDoesNotNeedParentProperty(
                        fontStyleBuilderFunction,
                        FontStyleBuilder::outlineThickness,
                        childStyleByFilter, filter))
                    childFontStyle.outlineThickness(parentFontStyle.outlineThickness());
                if (!fontStyleChildDoesNotNeedParentProperty(
                        fontStyleBuilderFunction,
                        FontStyleBuilder::outlineHexColor,
                        childStyleByFilter, filter))
                    childFontStyle.outlineHexColor(parentFontStyle.outlineHexColor());
                if (!fontStyleChildDoesNotNeedParentProperty(
                        fontStyleBuilderFunction,
                        FontStyleBuilder::outlineOpacity,
                        childStyleByFilter, filter))
                    childFontStyle.outlineOpacity(parentFontStyle.outlineOpacity());
                if (!fontStyleChildDoesNotNeedParentProperty(
                        fontStyleBuilderFunction,
                        FontStyleBuilder::shadowBlurRadius,
                        childStyleByFilter, filter))
                    childFontStyle.shadowBlurRadius(parentFontStyle.shadowBlurRadius());
                if (!fontStyleChildDoesNotNeedParentProperty(
                        fontStyleBuilderFunction,
                        FontStyleBuilder::shadowHexColor,
                        childStyleByFilter, filter))
                    childFontStyle.shadowHexColor(parentFontStyle.shadowHexColor());
                if (!fontStyleChildDoesNotNeedParentProperty(
                        fontStyleBuilderFunction,
                        FontStyleBuilder::shadowOpacity,
                        childStyleByFilter, filter))
                    childFontStyle.shadowOpacity(parentFontStyle.shadowOpacity());
                if (!fontStyleChildDoesNotNeedParentProperty(
                        fontStyleBuilderFunction,
                        FontStyleBuilder::shadowHorizontalOffset,
                        childStyleByFilter, filter))
                    childFontStyle.shadowHorizontalOffset(parentFontStyle.shadowHorizontalOffset());
                if (!fontStyleChildDoesNotNeedParentProperty(
                        fontStyleBuilderFunction,
                        FontStyleBuilder::shadowVerticalOffset,
                        childStyleByFilter, filter))
                    childFontStyle.shadowVerticalOffset(parentFontStyle.shadowVerticalOffset());
            }
        }
    }

    /**
     * We populate the child using the parent's default only after other filters have been
     * populated into the child, because childDoesNotNeedParentProperty() needs the child's
     * default to be unchanged.
     */
    private static <B> Comparator<Map.Entry<ViewportFilter, B>> defaultFilterLastComparator() {
        return Map.Entry.comparingByKey(
                Comparator.comparing(Object::getClass,
                        Comparator.comparingInt(
                                c -> c == AnyViewportFilter.class ? 1 : 0)));
    }

    private static <B, V> boolean childDoesNotNeedParentProperty(
            Function<B, Object> fieldFunction,
            ViewportFilterMapBuilder<B, V> childMapBuilder, ViewportFilter filter) {
        // Don't inherit parent property if child is defining the default.
        // For example:
        // position-history-mode.hint=hint1-mode.hint
        // position-history-mode.hint.font-size=20
        // If hint1-mode defines font-size.3840x2160-300%, we don't want to have it
        // in position-history-mode because we want position-history-mode.hint.font-size
        // to be used instead.
        Map<ViewportFilter, B> childMap = childMapBuilder.map();
        B defaultElement = childMap.get(AnyViewportFilter.ANY_VIEWPORT_FILTER);
        B filterElement = childMap.get(filter);
        return defaultElement != null && fieldFunction.apply(defaultElement) != null
               || filterElement != null && fieldFunction.apply(filterElement) != null;
    }

    private static <B, V> boolean fontStyleChildDoesNotNeedParentProperty(
            Function<B, FontStyleBuilder> fontStyleBuilderFunction,
            Function<FontStyleBuilder, Object> fieldFunction,
            ViewportFilterMapBuilder<B, V> childMapBuilder, ViewportFilter filter) {
        Map<ViewportFilter, B> childMap = childMapBuilder.map();
        FontStyleBuilder defaultElement =
                childMap.get(AnyViewportFilter.ANY_VIEWPORT_FILTER) == null ? null :
                        fontStyleBuilderFunction.apply(
                                childMap.get(AnyViewportFilter.ANY_VIEWPORT_FILTER));
        FontStyleBuilder filterElement = fontStyleBuilderFunction.apply(childMap.get(filter));
        return defaultElement != null && fieldFunction.apply(defaultElement) != null
               || filterElement != null && fieldFunction.apply(filterElement) != null;
    }

      @SuppressWarnings("unchecked")
    private static class ComboMapConfigurationBuilder {
        Property<Map<Combo, List<Command>>> to;
        Property<Map<Combo, List<Command>>> startMove;
        Property<Map<Combo, List<Command>>> stopMove;
        Property<Map<Combo, List<Command>>> press;
        Property<Map<Combo, List<Command>>> release;
        Property<Map<Combo, List<Command>>> toggle;
        Property<Map<Combo, List<Command>>> startWheel;
        Property<Map<Combo, List<Command>>> stopWheel;
        Property<Map<Combo, List<Command>>> snap;
        Property<Map<Combo, List<Command>>> shrinkGrid;
        Property<Map<Combo, List<Command>>> moveGrid;
        Property<Map<Combo, List<Command>>> moveToGridCenter;
        Property<Map<Combo, List<Command>>> moveToLastSelectedHint;
        Property<Map<Combo, List<Command>>> savePosition;
        Property<Map<Combo, List<Command>>> unsavePosition;
        Property<Map<Combo, List<Command>>> clearPositionHistory;
        Property<Map<Combo, List<Command>>> cycleNextPosition;
        Property<Map<Combo, List<Command>>> cyclePreviousPosition;
        Property<Map<Combo, List<Command>>> breakComboPreparation;
        Property<Map<Combo, List<Command>>> remapping;

        List<Combo> hintSelectCombos;
        List<Combo> hintUnselectCombos;

        public ComboMapConfigurationBuilder(String modeName,
                                            Map<PropertyKey, Property<?>> propertyByKey) {
            to = new ComboMapProperty("to", modeName, propertyByKey);
            startMove = new ComboMapProperty("start-move", modeName, propertyByKey);
            stopMove = new ComboMapProperty("stop-move", modeName, propertyByKey);
            press = new ComboMapProperty("press", modeName, propertyByKey);
            release = new ComboMapProperty("release", modeName, propertyByKey);
            toggle = new ComboMapProperty("toggle", modeName, propertyByKey);
            startWheel = new ComboMapProperty("start-wheel", modeName, propertyByKey);
            stopWheel = new ComboMapProperty("stop-wheel", modeName, propertyByKey);
            snap = new ComboMapProperty("snap", modeName, propertyByKey);
            shrinkGrid = new ComboMapProperty("shrink-grid", modeName, propertyByKey);
            moveGrid = new ComboMapProperty("move-grid", modeName, propertyByKey);
            moveToGridCenter = new ComboMapProperty("move-grid-to-center", modeName, propertyByKey);
            moveToLastSelectedHint = new ComboMapProperty("move-to-last-selected-hint", modeName, propertyByKey);
            savePosition = new ComboMapProperty("save-position", modeName, propertyByKey);
            unsavePosition = new ComboMapProperty("unsave-position", modeName, propertyByKey);
            clearPositionHistory = new ComboMapProperty("clear", modeName, propertyByKey);
            cycleNextPosition = new ComboMapProperty("cycle-next", modeName, propertyByKey);
            cyclePreviousPosition = new ComboMapProperty("cycle-previous", modeName, propertyByKey);
            breakComboPreparation = new ComboMapProperty("break-combo-preparation", modeName, propertyByKey);
            remapping = new ComboMapProperty("remapping", modeName, propertyByKey);
        }

          public void hintSelectCombos(List<Combo> combos) {
              hintSelectCombos = combos;
          }

          public void hintUnselectCombos(List<Combo> combos) {
              hintUnselectCombos = combos;
          }

          private static class ComboMapProperty extends Property<Map<Combo, List<Command>>> {

            private ComboMapProperty(String name, String mode,
                                     Map<PropertyKey, Property<?>> propertyByKey) {
                super(name, mode, propertyByKey, new HashMap<>());
            }

            @Override
            void extend(Object parent_) {
                Map<Combo, List<Command>> parent =
                        (Map<Combo, List<Command>>) parent_;
                for (Map.Entry<Combo, List<Command>> parentEntry : parent.entrySet()) {
                    // mode1.start-move.up=x
                    // mode2.start-move=mode1.start-move
                    // mode2.start-move.up=y
                    if (!builder.containsKey(parentEntry.getKey()))
                        builder.put(parentEntry.getKey(), parentEntry.getValue());
                }
            }
        }

        public Map<Combo, List<Command>> commandsByCombo() {
            Map<Combo, List<Command>> commandsByCombo = new HashMap<>();
            if (hintSelectCombos != null)
                add(commandsByCombo, hintSelectCombos.stream().collect(Collectors.toMap(Function.identity(), combo -> List.of(new SelectHintKey()))));
            if (hintUnselectCombos != null)
                add(commandsByCombo, hintUnselectCombos.stream().collect(Collectors.toMap(Function.identity(), combo -> List.of(new UnselectHintKey()))));
            add(commandsByCombo, to.builder);
            add(commandsByCombo, startMove.builder);
            add(commandsByCombo, stopMove.builder);
            add(commandsByCombo, press.builder);
            add(commandsByCombo, release.builder);
            add(commandsByCombo, toggle.builder);
            add(commandsByCombo, startWheel.builder);
            add(commandsByCombo, stopWheel.builder);
            add(commandsByCombo, snap.builder);
            add(commandsByCombo, shrinkGrid.builder);
            add(commandsByCombo, moveGrid.builder);
            add(commandsByCombo, moveToGridCenter.builder);
            add(commandsByCombo, moveToLastSelectedHint.builder);
            add(commandsByCombo, savePosition.builder);
            add(commandsByCombo, unsavePosition.builder);
            add(commandsByCombo, clearPositionHistory.builder);
            add(commandsByCombo, cycleNextPosition.builder);
            add(commandsByCombo, cyclePreviousPosition.builder);
            add(commandsByCombo, breakComboPreparation.builder);
            add(commandsByCombo, remapping.builder);
            return commandsByCombo;
        }

          private static void add(Map<Combo, List<Command>> commandsByCombo,
                                  Map<Combo, List<Command>> otherCommandsByCombo) {
              for (Map.Entry<Combo, List<Command>> entry : otherCommandsByCombo.entrySet()) {
                  Combo combo = entry.getKey();
                  List<Command> commands = entry.getValue();
                  commandsByCombo.computeIfAbsent(combo, combo1 -> new ArrayList<>())
                                 .addAll(commands);
              }
          }

        public ComboMap build() {
            return new ComboMap(commandsByCombo());
        }

    }

    private record PropertyKey(String modeName, String propertyName) {
        @Override
        public String toString() {
            return modeName + "." + propertyName;
        }
    }

    private static class Property<T> {
        final PropertyKey propertyKey;
        final T builder;
        PropertyKey parentPropertyKey;

        private Property(String name, String mode,
                         Map<PropertyKey, Property<?>> propertyByKey, T builder) {
            this.propertyKey = new PropertyKey(mode, name);
            this.builder = builder;
            propertyByKey.put(propertyKey, this);
        }

        /**
         * For default properties only.
         */
        private Property(String name, T builder) {
            this.propertyKey = new PropertyKey(null, name);
            this.builder = builder;
        }

        void parsePropertyReference(String propertyKey, String propertyValue,
                                    Map<PropertyKey, Set<PropertyKey>> childPropertiesByParentProperty,
                                    Set<PropertyKey> nonRootPropertyKeys) {
            String[] propertyKeySplit = propertyKey.split("\\.");
            String propertyKeyMode = propertyKeySplit[0];
            String property = propertyKeySplit[1]; // "indicator"
            if (propertyValue.endsWith(property)) {
                // snap-mode.indicator=normal-mode.indicator
                String propertyValueMode =
                        propertyValue.substring(0, propertyValue.indexOf('.'));
                if (propertyValueMode.equals(propertyKeyMode))
                    throw new IllegalArgumentException(
                            "Invalid property reference " + propertyKey + "=" +
                            propertyValue + ": a property cannot reference itself");
                parentPropertyKey =
                        new PropertyKey(propertyValueMode, this.propertyKey.propertyName);
                childPropertiesByParentProperty.computeIfAbsent(
                                                                parentPropertyKey, propertyReference -> new HashSet<>())
                                                        .add(this.propertyKey);
                nonRootPropertyKeys.add(this.propertyKey);
            }
            else
                throw new IllegalArgumentException(
                        "Invalid property reference " + propertyKey + "=" +
                        propertyValue + ": expected " + propertyKey +
                        "=<reference mode>." + property);
        }

        void parseReferenceOr(String propertyKey, String propertyValue,
                              Consumer<T> parser,
                              Map<PropertyKey, Set<PropertyKey>> childPropertiesByParentProperty,
                              Set<PropertyKey> nonRootPropertyKeys) {
            if (propertyValue.endsWith(propertyKey.substring(propertyKey.indexOf('.'))))
                // grid-mode.position-history.save=hint-mode.position-history.save
                parsePropertyReference(propertyKey, propertyValue,
                        childPropertiesByParentProperty, nonRootPropertyKeys);
            else
                parser.accept(builder);
        }

        void extend(Object parent_) {
            throw new IllegalStateException();
        };

    }

}

package mousemaster;

import io.qt.gui.QFontDatabase;
import mousemaster.ComboMove.KeyComboMove;
import mousemaster.ComboMove.PressComboMove;
import mousemaster.GridArea.GridAreaType;
import mousemaster.GridConfiguration.GridConfigurationBuilder;
import mousemaster.HideCursor.HideCursorBuilder;
import mousemaster.HintGridArea.HintGridAreaType;
import mousemaster.HintGridLayout.HintGridLayoutBuilder;
import mousemaster.HintMeshConfiguration.HintMeshConfigurationBuilder;
import mousemaster.HintMeshKeys.HintMeshKeysBuilder;
import mousemaster.HintMeshStyle.HintMeshStyleBuilder;
import mousemaster.Indicator.IndicatorBuilder;
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
        MouseBuilder mouse = new MouseBuilder();
        mouse.velocity().initialVelocity(1000)
                        .maxVelocity(2200)
                        .acceleration(3000)
                        .accelerationEasing(new Easing.Smootherstep())
                        .deceleration(20);
        mouse.smoothJumpEnabled(true)
             .smoothJumpVelocity(30000);
        WheelBuilder wheel = new WheelBuilder();
        wheel.velocity().initialVelocity(1000)
                        .maxVelocity(1000)
                        .acceleration(500)
                        .accelerationEasing(new Easing.Polynomial(1))
                        .deceleration(0);
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
        hintMeshStyleBuilder.fontStyle().defaultFontStyle()
                            .name("Consolas")
                            .weight(FontWeight.NORMAL)
                            .size(18d)
                            .hexColor("#FFFFFF")
                            .opacity(1d)
                            .outlineThickness(0d)
                            .outlineHexColor("#000000")
                            .outlineOpacity(0.5d);
        hintMeshStyleBuilder.fontStyle()
                            .spacingPercent(0.7d);
        hintMeshStyleBuilder.fontStyle().defaultFontStyle().shadow()
                            .blurRadius(10d)
                            .hexColor("#FFFFFF")
                            .opacity(1d)
                            .horizontalOffset(0d)
                            .verticalOffset(0d)
                            .stackCount(1);
        hintMeshStyleBuilder
                .prefixInBackground(false)
                .boxHexColor("#000000")
                .boxOpacity(0.3d)
                .boxBorderThickness(1d)
                .boxBorderLength(10_000d)
                .boxBorderHexColor("#FFFFFF")
                .boxBorderOpacity(0.4d)
                .boxBorderRadius(0d);
        hintMeshStyleBuilder.boxShadow()
                .blurRadius(10d)
                .hexColor("#000000")
                .opacity(0d)
                .horizontalOffset(2d)
                .verticalOffset(2d)
                .stackCount(1);
        hintMeshStyleBuilder
                .prefixBoxEnabled(false)
                .prefixBoxBorderThickness(4d)
                .prefixBoxBorderLength(10_000d)
                .prefixBoxBorderHexColor("#FFD93D")
                .prefixBoxBorderOpacity(0.8d)
                .boxWidthPercent(1d)
                .boxHeightPercent(1d)
                .cellHorizontalPadding(0d)
                .cellVerticalPadding(0d)
                .subgridRowCount(1)
                .subgridColumnCount(1)
                .subgridBorderThickness(1d)
                .subgridBorderLength(10_000d)
                .subgridBorderHexColor("#FFFFFF")
                .subgridBorderOpacity(1d)
                .transitionAnimationEnabled(true)
                .transitionAnimationDuration(Duration.ofMillis(100))
                .backgroundHexColor("#000000")
                .backgroundOpacity(0d);
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
        indicator.enabled(false);
        // Defaults are set on idle indicator; other states inherit during build.
        IndicatorBuilder idleIndicator = indicator.idleIndicator();
        idleIndicator.size(26)
                     .edgeCount(300)
                     .hexColor("#FF0000")
                     .opacity(0.2)
                     .position(IndicatorPosition.CENTER);
        idleIndicator.outerOutline()
                     .thickness(0)
                     .hexColor("#FF0000")
                     .opacity(1.0)
                     .fillPercent(1.0)
                     .fillStartAngle(180)
                     .fillDirection(FillDirection.COUNTERCLOCKWISE);
        idleIndicator.innerOutline()
                     .thickness(0.5)
                     .hexColor("#FF0000")
                     .opacity(1.0)
                     .fillPercent(1.0)
                     .fillStartAngle(180)
                     .fillDirection(FillDirection.COUNTERCLOCKWISE);
        idleIndicator.shadow()
                     .blurRadius(10d)
                     .hexColor("#000000")
                     .opacity(0d)
                     .horizontalOffset(0d)
                     .verticalOffset(0d)
                     .stackCount(1);
        indicator.wheelIndicator()
                 .hexColor("#FFFF00")
                 .innerOutline().hexColor("#FFFF00");
        indicator.wheelIndicator()
                 .shadow().opacity(1d).hexColor("#FFFF00");
        indicator.leftMousePressIndicator()
                 .hexColor("#00FF00")
                 .innerOutline().hexColor("#00FF00");
        indicator.leftMousePressIndicator()
                 .shadow().opacity(1d).hexColor("#00FF00");
        indicator.middleMousePressIndicator()
                 .hexColor("#FF00FF")
                 .innerOutline().hexColor("#FF00FF");
        indicator.middleMousePressIndicator()
                 .shadow().opacity(1d).hexColor("#FF00FF");
        indicator.rightMousePressIndicator()
                 .hexColor("#00FFFF")
                 .innerOutline().hexColor("#00FFFF");
        indicator.rightMousePressIndicator()
                 .shadow().opacity(1d).hexColor("#00FFFF");
        idleIndicator.labelEnabled(false);
        idleIndicator.labelFontStyle()
                     .name("Arial")
                     .weight(FontWeight.NORMAL)
                     .size(8d)
                     .hexColor("#FFFFFF")
                     .opacity(1.0)
                     .outlineThickness(0d)
                     .outlineHexColor("#000000")
                     .outlineOpacity(0d);
        idleIndicator.labelFontStyle().shadow()
                     .blurRadius(10d)
                     .hexColor("#000000")
                     .opacity(0d)
                     .horizontalOffset(0d)
                     .verticalOffset(0d)
                     .stackCount(1);
        HideCursorBuilder hideCursor =
                new HideCursorBuilder().enabled(false).idleDuration(Duration.ZERO);
        ZoomConfigurationBuilder zoom = new ZoomConfigurationBuilder();
        zoom.percent(1.0)
            .center(ZoomCenter.SCREEN_CENTER)
            .animationEnabled(false)
            .animationEasing(new Easing.Smootherstep())
            .animationDurationMillis(200);
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
                new Property<>("break-macro", Map.of()),
                new Property<>("macro", Map.of()),
                new Property<>("mutate-mode", Map.of()),
                new Property<>("noop", Map.of())
        ).collect(Collectors.toMap(property -> property.propertyKey.propertyName, Function.identity()));
        // @formatter:on
    }

    private record Aliases(Map<String, LayoutKeyAlias> layoutKeyAliasByName,
                           Map<String, AppAlias> appAliasByName) {

    }

    private static class LayoutKeyAlias {

        // Raw tokens from the property value, before alias reference expansion.
        List<String> noLayoutTokens = null;
        Map<KeyboardLayout, List<String>> tokensByLayout = new HashMap<>();

    }

    private record ForcedActiveAndConfigurationKeyboardLayouts(
            KeyboardLayout forcedActiveKeyboardLayout,
            KeyboardLayout configurationKeyboardLayout) {

    }

    public static Configuration parse(List<String> properties,
                                      KeyboardLayout platformActiveKeyboardLayout) {
        ForcedActiveAndConfigurationKeyboardLayouts
                forcedActiveAndConfigurationKeyboardLayouts = parseForcedAndConfigurationLayouts(properties);
        KeyboardLayout activeKeyboardLayout =
                forcedActiveAndConfigurationKeyboardLayouts.forcedActiveKeyboardLayout == null ?
                        platformActiveKeyboardLayout :
                        forcedActiveAndConfigurationKeyboardLayouts.forcedActiveKeyboardLayout;
        KeyboardLayout configurationKeyboardLayout =
                forcedActiveAndConfigurationKeyboardLayouts.configurationKeyboardLayout == null ?
                        activeKeyboardLayout :
                        forcedActiveAndConfigurationKeyboardLayouts.configurationKeyboardLayout;
        KeyResolver keyResolver =
                new KeyResolver(activeKeyboardLayout, configurationKeyboardLayout);
        Aliases configurationAliases = parseAliases(properties);
        Map<String, KeyAlias> keyAliases = buildKeyAliasesForActiveKeyboardLayout(
                configurationAliases.layoutKeyAliasByName, activeKeyboardLayout,
                configurationKeyboardLayout);
        Map<String, AppAlias> appAliases = configurationAliases.appAliasByName;


        String logLevel = null;
        boolean logRedactKeys = false;
        boolean logToFile = false;
        boolean hideConsole = false;
        ComboMoveDuration defaultComboMoveDuration =
                new ComboMoveDuration(Duration.ZERO, null);
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
                        referencedModesByReferencerMode, modeName, keyMatcher, keyAliases, keyResolver,
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
        // Aggregate mutateModeCommands from individual properties into
        // each mode's comboMap.mutateMode so they are included in the ComboMap.
        for (ModeBuilder mode : modeByName.values()) {
            for (Property<?> property : List.of(
                    mode.stopCommandsFromPreviousMode,
                    mode.pushModeToHistoryStack,
                    mode.modeAfterUnhandledKeyPress,
                    mode.mouse, mode.wheel,
                    mode.grid, mode.hintMesh, mode.timeout, mode.indicator,
                    mode.hideCursor, mode.zoom)) {
                for (Map.Entry<Combo, List<Command>> entry :
                        property.mutateModeCommands.entrySet()) {
                    mode.comboMap.mutateMode.builder
                            .computeIfAbsent(entry.getKey(),
                                    c -> new ArrayList<>())
                            .addAll(entry.getValue());
                }
            }
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
        Set<Key> allComboAndMacroKeys = new HashSet<>();
        for (ModeBuilder mode : modeByName.values()) {
            checkMissingProperties(mode);
            if (mode.hintMesh.builder.enabled() &&
                mode.hintMesh.builder.selectCombos() == null) {
                logger.trace(mode.modeName + ".hint.select is missing, using selection keys");
                mode.hintMesh.builder.selectCombos(
                        deriveSelectCombosFromHintSelectionKeys(mode,
                                defaultComboMoveDuration, mode.modeName));
            }
            mode.comboMap.hintSelectCombos(mode.hintMesh.builder.selectCombos());
            mode.comboMap.hintUnselectCombos(mode.hintMesh.builder.unselectCombos());
            usedKeys(mode.comboMap, allComboAndMacroKeys);
        }
        List<Key> missingKeys = allComboAndMacroKeys.stream()
                                                        .filter(Predicate.not(
                                                                activeKeyboardLayout::containsKey))
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
                forcedActiveAndConfigurationKeyboardLayouts.forcedActiveKeyboardLayout);
    }

    private static List<Combo> deriveSelectCombosFromHintSelectionKeys(ModeBuilder mode,
                                                                       ComboMoveDuration defaultComboMoveDuration,
                                                                       String modeName) {
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
                new ComboPrecondition.ComboKeyPrecondition(Set.of(),
                        new ComboPrecondition.PressedKeyPrecondition(List.of())),
                new ComboPrecondition.ComboAppPrecondition(Set.of(), Set.of()));
        String label = modeName + ".hint.select";
        return hintSelectionKeys.stream()
                                .map(key -> new Combo(label, emptyComboPrecondition,
                                        ComboSequence.ofMoves(
                                                List.<KeyComboMove>of(new PressComboMove(
                                                        KeyOrAlias.ofKey(key), false, true,
                                                        defaultComboMoveDuration, null)))))
                                .toList();
    }

    private static ForcedActiveAndConfigurationKeyboardLayouts parseForcedAndConfigurationLayouts(
            List<String> properties) {
        Set<String> visitedPropertyKeys = new HashSet<>();
        KeyboardLayout forcedActiveKeyboardLayout = null;
        KeyboardLayout configurationKeyboardLayout = null;
        for (String property : properties) {
            checkPropertyLineCorrectness(property, visitedPropertyKeys);
            Matcher lineMatcher = propertyLinePattern.matcher(property);
            //noinspection ResultOfMethodCallIgnored
            lineMatcher.matches();
            String propertyKey = lineMatcher.group(1).strip();
            String propertyValue = lineMatcher.group(2).strip();
            if (propertyKey.equals("keyboard-layout")) {
                logger.warn(
                        "key-layout has been deprecated: use forced-active-keyboard-layout instead");
                forcedActiveKeyboardLayout = parseKeyboardLayout(propertyValue);
            }
            if (propertyKey.equals("forced-active-keyboard-layout")) {
                forcedActiveKeyboardLayout = parseKeyboardLayout(propertyValue);
            }
            if (propertyKey.equals("configuration-keyboard-layout")) {
                configurationKeyboardLayout = parseKeyboardLayout(propertyValue);
            }
        }
        return new ForcedActiveAndConfigurationKeyboardLayouts(forcedActiveKeyboardLayout,
                configurationKeyboardLayout);
    }

    private static void usedKeys(ComboMapConfigurationBuilder comboMap,
                                 Set<Key> allComboAndMacroKeys) {
        for (Map.Entry<Combo, List<Command>> entry : comboMap.commandsByCombo().entrySet()) {
            Combo combo = entry.getKey();
            List<Command> commands = entry.getValue();
            allComboAndMacroKeys.addAll(combo.precondition()
                                             .keyPrecondition()
                                             .pressedKeyPrecondition()
                                             .allKeys());
            allComboAndMacroKeys.addAll(combo.precondition()
                                                 .keyPrecondition()
                                                 .unpressedKeySet());
            allComboAndMacroKeys.addAll(combo.sequence().allKeys());
            for (Command command : commands) {
                if (command instanceof Command.MacroCommand(Macro macro, var __)) {
                    for (MacroParallel parallel : macro.output().parallels()) {
                        for (MacroMove move : parallel.moves()) {
                            if (move instanceof KeyMacroMove keyMacroMove)
                                allComboAndMacroKeys.addAll(keyMacroMove.keyOrAlias().possibleKeys());
                        }
                    }
                }
            }
        }
    }

    private static void parseMacroMapping(
            String propertyType,
            String name,
            String label,
            String propertyValue,
            ComboMoveDuration defaultComboMoveDuration,
            Map<String, KeyAlias> keyAliases,
            Map<String, AppAlias> appAliases,
            Map<Combo, List<Command>> builder, KeyResolver keyResolver) {
        String[] split = propertyValue.split("\\s*->\\s*");
        if (split.length < 2 || split.length > 3)
            throw new IllegalArgumentException(
                    "Invalid " + propertyType + ": " + propertyValue);
        String comboString = split[0];
        String remapString = split.length == 3 ? split[1] : null;
        String outputString = split.length == 3 ? split[2] : split[1];
        List<Combo> combos =
                Combo.multiCombo(label, comboString, defaultComboMoveDuration,
                        keyAliases,
                        appAliases, keyResolver);
        // Aliases used in the macro output must be used in all of the
        // combos of that multi combo.
        Set<String> comboAliasNameIntersection = new HashSet<>(
                combos.getFirst().sequence().aliasNames());
        for (Combo combo : combos) {
            comboAliasNameIntersection.retainAll(combo.sequence().aliasNames());
        }
        Set<String> aliasNamesUsedInOutput =
                Macro.aliasNamesUsedInOutput(outputString, keyAliases.keySet());
        if (!comboAliasNameIntersection.containsAll(aliasNamesUsedInOutput)) {
            Set<String> aliasesNotUsedInComboSequence =
                    new HashSet<>(aliasNamesUsedInOutput);
            aliasesNotUsedInComboSequence.removeAll(comboAliasNameIntersection);
            throw new IllegalArgumentException(
                    "Key aliases " + aliasesNotUsedInComboSequence +
                    " cannot be used in the " + propertyType + " output because they are not used in the combo sequence");
        }
        // Negated names used in the macro output must be negated in all combos.
        Set<String> comboNegatedNameIntersection = new HashSet<>(
                combos.getFirst().sequence().negatedNames());
        for (Combo combo : combos) {
            comboNegatedNameIntersection.retainAll(combo.sequence().negatedNames());
        }
        Set<String> negatedNamesUsedInOutput =
                Macro.negatedNamesUsedInOutput(outputString);
        if (!comboNegatedNameIntersection.containsAll(negatedNamesUsedInOutput)) {
            Set<String> negatedNotInCombo =
                    new HashSet<>(negatedNamesUsedInOutput);
            negatedNotInCombo.removeAll(comboNegatedNameIntersection);
            throw new IllegalArgumentException(
                    "Negated names " + negatedNotInCombo +
                    " cannot be used in the " + propertyType + " output because they are not used as negated in the combo sequence");
        }
        Map<String, AliasRemap<Key>> aliasRemapByAliasName;
        if (remapString != null) {
            aliasRemapByAliasName = AliasRemap.keyValues(
                    remapString, keyAliases, keyResolver, propertyType);
            // Remap aliases must be used in the combo.
            for (String remapAliasName : aliasRemapByAliasName.keySet()) {
                if (!comboAliasNameIntersection.contains(remapAliasName))
                    throw new IllegalArgumentException(
                            "Remap alias " + remapAliasName +
                            " must be used in the " + propertyType +
                            " combo sequence");
            }
            // Remap aliases must be used in the output.
            for (String remapAliasName : aliasRemapByAliasName.keySet()) {
                if (!aliasNamesUsedInOutput.contains(remapAliasName))
                    throw new IllegalArgumentException(
                            "Remap alias " + remapAliasName +
                            " must be used in the " + propertyType + " output");
            }
        }
        else {
            aliasRemapByAliasName = Map.of();
        }
        Macro macro = Macro.of(name, outputString, keyAliases, keyResolver,
                aliasRemapByAliasName);
        Command command = new MacroCommand(macro, null);
        for (Combo combo : combos)
            builder.computeIfAbsent(combo, combo1 -> new ArrayList<>())
                   .add(command);
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
                                  KeyResolver keyResolver, Set<String> modeReferences,
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
                            propertyValue, builder -> {
                                if (!tryParseComboProperty(propertyValue, modeName,
                                        new ModePropertyPath(List.of("stopCommandsFromPreviousMode")),
                                        v -> Boolean.parseBoolean(v),
                                        v -> builder.set(Boolean.parseBoolean(v)),
                                        mode.stopCommandsFromPreviousMode.mutateModeCommands,
                                        defaultComboMoveDuration, keyAliases, appAliases,
                                        keyResolver))
                                    builder.set(Boolean.parseBoolean(propertyValue));
                            },
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
            case "push-mode-to-history-stack" ->
                    mode.pushModeToHistoryStack.parseReferenceOr(propertyKey,
                            propertyValue, builder -> {
                                if (!tryParseComboProperty(propertyValue, modeName,
                                        new ModePropertyPath(List.of("pushModeToHistoryStack")),
                                        v -> Boolean.parseBoolean(v),
                                        v -> builder.set(Boolean.parseBoolean(v)),
                                        mode.pushModeToHistoryStack.mutateModeCommands,
                                        defaultComboMoveDuration, keyAliases, appAliases,
                                        keyResolver))
                                    builder.set(Boolean.parseBoolean(propertyValue));
                            },
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
            case "mode-after-unhandled-key-press" ->
                    mode.modeAfterUnhandledKeyPress.parseReferenceOr(propertyKey,
                            propertyValue, builder -> {
                                if (!tryParseComboProperty(propertyValue, modeName,
                                        new ModePropertyPath(List.of("modeAfterUnhandledKeyPress")),
                                        v -> { modeReferences.add(checkModeReference(v)); return v; },
                                        v -> { modeReferences.add(checkModeReference(v)); builder.set(v); },
                                        mode.modeAfterUnhandledKeyPress.mutateModeCommands,
                                        defaultComboMoveDuration, keyAliases, appAliases,
                                        keyResolver)) {
                                    modeReferences.add(checkModeReference(propertyValue));
                                    builder.set(propertyValue);
                                }
                            },
                            childPropertiesByParentProperty, nonRootPropertyKeys);
            case "mouse" -> {
                if (keyMatcher.group(group3) == null)
                    mode.mouse.parsePropertyReference(propertyKey, propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid mouse property key");
                else {
                    ModePropertyHandler handler = mouseHandler(
                            new ModePropertyPath(List.of("mouse")),
                            mode.mouse.builder, keyMatcher.group(group4));
                    if (handler == null)
                        throw new IllegalArgumentException(
                                "Invalid mouse property key");
                    if (!tryParseComboProperty(propertyValue, modeName,
                            handler.propertyPath(), handler.valueParser(),
                            handler.modeBuilderSetter(),
                            mode.mouse.mutateModeCommands,
                            defaultComboMoveDuration, keyAliases, appAliases,
                            keyResolver))
                        handler.modeBuilderSetter().accept(propertyValue);
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
                    ModePropertyHandler handler = wheelHandler(
                            new ModePropertyPath(List.of("wheel")),
                            mode.wheel.builder, keyMatcher.group(group4));
                    if (handler == null)
                        throw new IllegalArgumentException(
                                "Invalid wheel property key");
                    if (!tryParseComboProperty(propertyValue, modeName,
                            handler.propertyPath(), handler.valueParser(),
                            handler.modeBuilderSetter(),
                            mode.wheel.mutateModeCommands,
                            defaultComboMoveDuration, keyAliases, appAliases,
                            keyResolver))
                        handler.modeBuilderSetter().accept(propertyValue);
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
                    ModePropertyHandler handler = gridHandler(
                            new ModePropertyPath(List.of("grid")),
                            mode.grid.builder, keyMatcher.group(group4));
                    if (handler == null)
                        throw new IllegalArgumentException(
                                "Invalid grid property key");
                    if (!tryParseComboProperty(propertyValue, modeName,
                            handler.propertyPath(), handler.valueParser(),
                            handler.modeBuilderSetter(),
                            mode.grid.mutateModeCommands,
                            defaultComboMoveDuration, keyAliases, appAliases,
                            keyResolver))
                        handler.modeBuilderSetter().accept(propertyValue);
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
                    String hintKey = keyMatcher.group(group4);
                    ModePropertyHandler handler = hintHandler(
                            new ModePropertyPath(List.of("hintMesh")),
                            mode.hintMesh.builder, viewportFilter, hintKey,
                            fontAvailability, keyAliases, keyResolver);
                    if (handler != null) {
                        if (!tryParseComboProperty(propertyValue, modeName,
                                handler.propertyPath(), handler.valueParser(),
                                handler.modeBuilderSetter(),
                                mode.hintMesh.mutateModeCommands,
                                defaultComboMoveDuration, keyAliases, appAliases,
                                keyResolver))
                            handler.modeBuilderSetter().accept(propertyValue);
                        return;
                    }
                    switch (hintKey) {
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
                        case "select" -> {
                            mode.hintMesh.builder.selectCombos(
                                    parseCombos(propertyValue, propertyKey,
                                            defaultComboMoveDuration,
                                            keyAliases, appAliases, keyResolver));
                        }
                        case "undo" -> {
                            mode.hintMesh.builder.unselectCombos(
                                    parseCombos(propertyValue, propertyKey,
                                            defaultComboMoveDuration,
                                            keyAliases, appAliases, keyResolver));
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
                            new SwitchMode(newModeName), propertyKey,
                            defaultComboMoveDuration,
                            keyAliases, appAliases, keyResolver);
                }
            }
            case "remapping" -> {
                if (keyMatcher.group(group3) == null) {
                    mode.comboMap.macro.parsePropertyReference(propertyKey,
                            propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                }
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid remapping property key");
                else {
                    parseMacroMapping("remapping", keyMatcher.group(group4),
                            propertyKey, propertyValue, defaultComboMoveDuration,
                            keyAliases, appAliases,
                            mode.comboMap.macro.builder, keyResolver);
                }
            }
            case "macro" -> {
                if (keyMatcher.group(group3) == null)
                    mode.comboMap.macro.parsePropertyReference(propertyKey,
                            propertyValue,
                            childPropertiesByParentProperty,
                            nonRootPropertyKeys);
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid macro property key");
                else {
                    parseMacroMapping("macro", keyMatcher.group(group4),
                            propertyKey, propertyValue, defaultComboMoveDuration,
                            keyAliases, appAliases,
                            mode.comboMap.macro.builder, keyResolver);
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
                    ModePropertyHandler handler = timeoutHandler(
                            new ModePropertyPath(List.of("timeout")),
                            mode.timeout.builder, keyMatcher.group(group4),
                            modeReferences);
                    if (handler == null)
                        throw new IllegalArgumentException(
                                "Invalid timeout property key");
                    if (!tryParseComboProperty(propertyValue, modeName,
                            handler.propertyPath(), handler.valueParser(),
                            handler.modeBuilderSetter(),
                            mode.timeout.mutateModeCommands,
                            defaultComboMoveDuration, keyAliases, appAliases,
                            keyResolver))
                        handler.modeBuilderSetter().accept(propertyValue);
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
                    // group4 is the state name (idle, move, etc.) or "enabled".
                    // group5 is the property name (color, size, etc.).
                    String stateOrKey = keyMatcher.group(group4);
                    if (stateOrKey.equals("enabled")) {
                        ModePropertyHandler handler = ModePropertyHandler.of(
                                new ModePropertyPath(List.of("indicator", "enabled")),
                                Boolean::parseBoolean,
                                v -> mode.indicator.builder.enabled(v));
                        if (!tryParseComboProperty(propertyValue, modeName,
                                handler.propertyPath(), handler.valueParser(),
                                handler.modeBuilderSetter(),
                                mode.indicator.mutateModeCommands,
                                defaultComboMoveDuration, keyAliases, appAliases,
                                keyResolver))
                            handler.modeBuilderSetter().accept(propertyValue);
                    }
                    else {
                        String subKey = keyMatcher.group(group5);
                        if (subKey == null) {
                            // Backward compatibility: idle-color (old) -> idle.color (new)
                            for (String state : List.of(
                                    "unhandled-key-press", "middle-mouse-press", "right-mouse-press",
                                    "left-mouse-press", "mouse-press", "wheel", "idle", "move")) {
                                if (stateOrKey.startsWith(state + "-")) {
                                    subKey = stateOrKey.substring(state.length() + 1);
                                    stateOrKey = state;
                                    break;
                                }
                            }
                            if (subKey == null)
                                throw new IllegalArgumentException(
                                        "Invalid indicator property key: " + stateOrKey);
                            logger.warn(propertyKey + " is deprecated, use " +
                                    propertyKey.replace("." + stateOrKey + "-" + subKey,
                                            "." + stateOrKey + "." + subKey) +
                                    " instead");
                        }
                        IndicatorBuilder targetIndicator = switch (stateOrKey) {
                            case "idle" -> mode.indicator.builder.idleIndicator();
                            case "move" -> mode.indicator.builder.moveIndicator();
                            case "wheel" -> mode.indicator.builder.wheelIndicator();
                            case "mouse-press" -> mode.indicator.builder.mousePressIndicator();
                            case "left-mouse-press" -> mode.indicator.builder.leftMousePressIndicator();
                            case "middle-mouse-press" -> mode.indicator.builder.middleMousePressIndicator();
                            case "right-mouse-press" -> mode.indicator.builder.rightMousePressIndicator();
                            case "unhandled-key-press" -> mode.indicator.builder.unhandledKeyPressIndicator();
                            default -> throw new IllegalArgumentException(
                                    "Invalid indicator state: " + stateOrKey);
                        };
                        if (subKey.startsWith("label-") &&
                            targetIndicator.labelEnabled() == null)
                            targetIndicator.labelEnabled(true);
                        String indicatorFieldName = switch (stateOrKey) {
                            case "idle" -> "idleIndicator";
                            case "move" -> "moveIndicator";
                            case "wheel" -> "wheelIndicator";
                            case "mouse-press" -> "mousePressIndicator";
                            case "left-mouse-press" -> "leftMousePressIndicator";
                            case "middle-mouse-press" -> "middleMousePressIndicator";
                            case "right-mouse-press" -> "rightMousePressIndicator";
                            case "unhandled-key-press" -> "unhandledKeyPressIndicator";
                            default -> throw new IllegalStateException();
                        };
                        ModePropertyPath indicatorPropertyPathPrefix =
                                new ModePropertyPath(List.of("indicator", indicatorFieldName));
                        parseIndicatorProperty(targetIndicator, subKey,
                                propertyValue, fontAvailability,
                                indicatorPropertyPathPrefix,
                                mode.indicator.mutateModeCommands,
                                modeName, defaultComboMoveDuration,
                                keyAliases, appAliases, keyResolver);
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
                    ModePropertyHandler handler = hideCursorHandler(
                            new ModePropertyPath(List.of("hideCursor")),
                            mode.hideCursor.builder, keyMatcher.group(group4));
                    if (handler == null)
                        throw new IllegalArgumentException(
                                "Invalid hide-cursor property key");
                    if (!tryParseComboProperty(propertyValue, modeName,
                            handler.propertyPath(), handler.valueParser(),
                            handler.modeBuilderSetter(),
                            mode.hideCursor.mutateModeCommands,
                            defaultComboMoveDuration, keyAliases, appAliases,
                            keyResolver))
                        handler.modeBuilderSetter().accept(propertyValue);
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
                    ModePropertyHandler handler = zoomHandler(
                            new ModePropertyPath(List.of("zoom")),
                            mode.zoom.builder, keyMatcher.group(group4));
                    if (handler == null)
                        throw new IllegalArgumentException(
                                "Invalid zoom property key");
                    if (!tryParseComboProperty(propertyValue, modeName,
                            handler.propertyPath(), handler.valueParser(),
                            handler.modeBuilderSetter(),
                            mode.zoom.mutateModeCommands,
                            defaultComboMoveDuration, keyAliases, appAliases,
                            keyResolver))
                        handler.modeBuilderSetter().accept(propertyValue);
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
                        case "up" -> setCommand(mode.comboMap.startMove.builder,  propertyValue, new StartMoveUp(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "down" -> setCommand(mode.comboMap.startMove.builder,  propertyValue, new StartMoveDown(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "left" -> setCommand(mode.comboMap.startMove.builder,  propertyValue, new StartMoveLeft(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "right" -> setCommand(mode.comboMap.startMove.builder,  propertyValue, new StartMoveRight(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
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
                        case "up" -> setCommand(mode.comboMap.stopMove.builder,  propertyValue, new StopMoveUp(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "down" -> setCommand(mode.comboMap.stopMove.builder,  propertyValue, new StopMoveDown(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "left" -> setCommand(mode.comboMap.stopMove.builder,  propertyValue, new StopMoveLeft(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "right" -> setCommand(mode.comboMap.stopMove.builder,  propertyValue, new StopMoveRight(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
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
                        case "left" -> setCommand(mode.comboMap.press.builder,  propertyValue, new PressLeft(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "middle" -> setCommand(mode.comboMap.press.builder,  propertyValue, new PressMiddle(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "right" -> setCommand(mode.comboMap.press.builder,  propertyValue, new PressRight(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
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
                        case "left" -> setCommand(mode.comboMap.release.builder,  propertyValue, new ReleaseLeft(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "middle" -> setCommand(mode.comboMap.release.builder,  propertyValue, new ReleaseMiddle(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "right" -> setCommand(mode.comboMap.release.builder,  propertyValue, new ReleaseRight(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
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
                        case "left" -> setCommand(mode.comboMap.toggle.builder,  propertyValue, new ToggleLeft(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "middle" -> setCommand(mode.comboMap.toggle.builder,  propertyValue, new ToggleMiddle(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "right" -> setCommand(mode.comboMap.toggle.builder,  propertyValue, new ToggleRight(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
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
                        case "up" -> setCommand(mode.comboMap.startWheel.builder,  propertyValue, new StartWheelUp(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "down" -> setCommand(mode.comboMap.startWheel.builder,  propertyValue, new StartWheelDown(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "left" -> setCommand(mode.comboMap.startWheel.builder,  propertyValue, new StartWheelLeft(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "right" -> setCommand(mode.comboMap.startWheel.builder,  propertyValue, new StartWheelRight(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
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
                        case "up" -> setCommand(mode.comboMap.stopWheel.builder,  propertyValue, new StopWheelUp(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "down" -> setCommand(mode.comboMap.stopWheel.builder,  propertyValue, new StopWheelDown(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "left" -> setCommand(mode.comboMap.stopWheel.builder,  propertyValue, new StopWheelLeft(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "right" -> setCommand(mode.comboMap.stopWheel.builder,  propertyValue, new StopWheelRight(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
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
                        case "up" -> setCommand(mode.comboMap.snap.builder,  propertyValue, new SnapUp(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "down" -> setCommand(mode.comboMap.snap.builder,  propertyValue, new SnapDown(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "left" -> setCommand(mode.comboMap.snap.builder,  propertyValue, new SnapLeft(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "right" -> setCommand(mode.comboMap.snap.builder,  propertyValue, new SnapRight(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
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
                        case "up" -> setCommand(mode.comboMap.shrinkGrid.builder,  propertyValue, new ShrinkGridUp(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "down" -> setCommand(mode.comboMap.shrinkGrid.builder,  propertyValue, new ShrinkGridDown(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "left" -> setCommand(mode.comboMap.shrinkGrid.builder,  propertyValue, new ShrinkGridLeft(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "right" -> setCommand(mode.comboMap.shrinkGrid.builder,  propertyValue, new ShrinkGridRight(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
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
                        case "up" -> setCommand(mode.comboMap.moveGrid.builder,  propertyValue, new MoveGridUp(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "down" -> setCommand(mode.comboMap.moveGrid.builder,  propertyValue, new MoveGridDown(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "left" -> setCommand(mode.comboMap.moveGrid.builder,  propertyValue, new MoveGridLeft(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "right" -> setCommand(mode.comboMap.moveGrid.builder,  propertyValue, new MoveGridRight(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        // @formatter:on
                    }
                }
            }
            case "position-history" -> {
                if (keyMatcher.group(group3) == null) {
                    mode.comboMap.savePosition.parsePropertyReference(propertyKey, propertyValue,
                            childPropertiesByParentProperty, nonRootPropertyKeys);
                    mode.comboMap.unsavePosition.parsePropertyReference(propertyKey, propertyValue,
                            childPropertiesByParentProperty, nonRootPropertyKeys);
                    mode.comboMap.clearPositionHistory.parsePropertyReference(propertyKey, propertyValue,
                            childPropertiesByParentProperty, nonRootPropertyKeys);
                    mode.comboMap.cycleNextPosition.parsePropertyReference(propertyKey, propertyValue,
                            childPropertiesByParentProperty, nonRootPropertyKeys);
                    mode.comboMap.cyclePreviousPosition.parsePropertyReference(propertyKey, propertyValue,
                            childPropertiesByParentProperty, nonRootPropertyKeys);
                }
                else if (keyMatcher.group(group4) == null)
                    throw new IllegalArgumentException(
                            "Invalid position-history property key");
                else {
                    switch (keyMatcher.group(group4)) {
                        // @formatter:off
                        case "save-position" -> setCommand(mode.comboMap.savePosition.builder,  propertyValue, new SavePosition(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "unsave-position" -> setCommand(mode.comboMap.unsavePosition.builder,  propertyValue, new UnsavePosition(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "clear" -> setCommand(mode.comboMap.clearPositionHistory.builder,  propertyValue, new ClearPositionHistory(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "cycle-next" -> setCommand(mode.comboMap.cycleNextPosition.builder,  propertyValue, new CycleNextPosition(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        case "cycle-previous" -> setCommand(mode.comboMap.cyclePreviousPosition.builder,  propertyValue, new CyclePreviousPosition(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
                        // @formatter:on
                        default -> throw new IllegalArgumentException(
                                "Invalid position-history property key");
                    }
                }
            }
            // @formatter:off
            case "move-to-grid-center" -> {
                mode.comboMap.moveToGridCenter.parseReferenceOr(propertyKey, propertyValue,
                        commandsByCombo -> setCommand(mode.comboMap.moveToGridCenter.builder,  propertyValue, new MoveToGridCenter(), propertyKey, finalDefaultComboMoveDuration, keyAliases, appAliases, keyResolver),
                        childPropertiesByParentProperty, nonRootPropertyKeys);
            }
            case "move-to-last-selected-hint" -> {
                mode.comboMap.moveToLastSelectedHint.parseReferenceOr(propertyKey, propertyValue,
                        commandsByCombo -> setCommand(mode.comboMap.moveToLastSelectedHint.builder,  propertyValue, new MoveToLastSelectedHint(), propertyKey, finalDefaultComboMoveDuration, keyAliases, appAliases, keyResolver),
                        childPropertiesByParentProperty, nonRootPropertyKeys);
            }
            case "break-combo-preparation" -> mode.comboMap.breakComboPreparation.parseReferenceOr(propertyKey, propertyValue,
                    commandsByCombo -> setCommand(mode.comboMap.breakComboPreparation.builder,  propertyValue, new BreakComboPreparation(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver),
                    childPropertiesByParentProperty, nonRootPropertyKeys);
            case "break-macro" -> mode.comboMap.breakMacro.parseReferenceOr(propertyKey, propertyValue,
                    commandsByCombo -> setCommand(mode.comboMap.breakMacro.builder, propertyValue, new BreakMacro(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver),
                    childPropertiesByParentProperty, nonRootPropertyKeys);
            case "noop" -> mode.comboMap.noop.parseReferenceOr(propertyKey, propertyValue,
                    commandsByCombo -> setCommand(mode.comboMap.noop.builder, propertyValue, new Noop(), propertyKey, defaultComboMoveDuration, keyAliases, appAliases, keyResolver),
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

    private record ResolvedAliasWithRefs(KeyAlias alias, List<String> referencedAliasNames) {
    }

    private static Map<String, KeyAlias> buildKeyAliasesForActiveKeyboardLayout(
            Map<String, LayoutKeyAlias> configurationLayoutKeyAliasByName,
            KeyboardLayout activeKeyboardLayout,
            KeyboardLayout configurationKeyboardLayout) {
        // First pass: resolve each alias's key tokens to concrete keys for the active layout
        // (without expanding alias references, those are collected as referencedAliasNames).
        Set<String> allAliasNames = configurationLayoutKeyAliasByName.keySet();
        Map<String, ResolvedAliasWithRefs> resolvedAliases = new HashMap<>();
        for (Map.Entry<String, LayoutKeyAlias> entry : configurationLayoutKeyAliasByName.entrySet()) {
            String aliasName = entry.getKey();
            LayoutKeyAlias layoutKeyAlias = entry.getValue();
            ResolvedAliasWithRefs resolved = resolveAliasForLayout(
                    activeKeyboardLayout, configurationKeyboardLayout,
                    layoutKeyAlias, aliasName, allAliasNames);
            resolvedAliases.put(aliasName, resolved);
        }
        // Second pass: recursively expand alias references with cycle detection.
        Map<String, KeyAlias> expandedAliases = new HashMap<>();
        Set<String> expanding = new LinkedHashSet<>(); // Tracks current DFS path for cycle detection.
        for (String aliasName : resolvedAliases.keySet()) {
            expandAlias(aliasName, resolvedAliases, expandedAliases, expanding);
        }
        return expandedAliases;
    }

    private static KeyAlias expandAlias(
            String aliasName,
            Map<String, ResolvedAliasWithRefs> resolvedAliases,
            Map<String, KeyAlias> expandedAliases,
            Set<String> expanding) {
        KeyAlias alreadyExpanded = expandedAliases.get(aliasName);
        if (alreadyExpanded != null)
            return alreadyExpanded;
        if (!expanding.add(aliasName))
            throw new IllegalArgumentException(
                    "Cycle detected in key alias references: " + expanding);
        ResolvedAliasWithRefs resolved = resolvedAliases.get(aliasName);
        if (resolved.referencedAliasNames.isEmpty()) {
            expandedAliases.put(aliasName, resolved.alias);
            expanding.remove(aliasName);
            return resolved.alias;
        }
        List<Key> expandedKeys = new ArrayList<>(resolved.alias.keys());
        for (String referencedAliasName : resolved.referencedAliasNames) {
            if (!resolvedAliases.containsKey(referencedAliasName))
                throw new IllegalArgumentException(
                        "Key alias " + aliasName + " references unknown alias " + referencedAliasName);
            KeyAlias expandedReferencedAlias = expandAlias(referencedAliasName,
                    resolvedAliases, expandedAliases, expanding);
            expandedKeys.addAll(expandedReferencedAlias.keys());
        }
        KeyAlias expanded = new KeyAlias(aliasName, List.copyOf(expandedKeys));
        expandedAliases.put(aliasName, expanded);
        expanding.remove(aliasName);
        return expanded;
    }

    private static ResolvedAliasWithRefs resolveAliasForLayout(
            KeyboardLayout activeKeyboardLayout,
            KeyboardLayout configurationKeyboardLayout,
            LayoutKeyAlias layoutKeyAlias, String aliasName,
            Set<String> allAliasNames) {
        List<String> tokens;
        // The layout the key tokens are named for.
        KeyboardLayout tokenLayout;
        if (layoutKeyAlias.tokensByLayout.containsKey(activeKeyboardLayout)) {
            tokens = layoutKeyAlias.tokensByLayout.get(activeKeyboardLayout);
            tokenLayout = activeKeyboardLayout;
        }
        else if (layoutKeyAlias.noLayoutTokens != null) {
            tokens = layoutKeyAlias.noLayoutTokens;
            tokenLayout = configurationKeyboardLayout;
        }
        else {
            // Cross-layout fallback: pick another layout's tokens.
            KeyboardLayout layoutForWhichAliasIsDefined =
                    layoutKeyAlias.tokensByLayout.keySet()
                                                 .stream()
                                                 .sorted(Comparator.comparing(
                                                         KeyboardLayout::displayName))
                                                 .findFirst()
                                                 .orElseThrow();
            tokens = layoutKeyAlias.tokensByLayout.get(layoutForWhichAliasIsDefined);
            tokenLayout = layoutForWhichAliasIsDefined;
        }
        List<Key> keys = new ArrayList<>();
        List<String> referencedAliasNames = new ArrayList<>();
        for (String token : tokens) {
            if (allAliasNames.contains(token)) {
                referencedAliasNames.add(token);
            }
            else {
                Key key = Key.ofName(token);
                if (!tokenLayout.equals(activeKeyboardLayout)) {
                    int scanCode = tokenLayout.scanCode(key);
                    Key activeKey = activeKeyboardLayout.keyFromScanCode(scanCode);
                    if (activeKey == null)
                        // This key from the other layout does not have an equivalent in the active layout.
                        throw new IllegalArgumentException(
                                "Key " + key + " in alias " + aliasName +
                                " is not defined for the active keyboard layout " +
                                activeKeyboardLayout);
                    key = activeKey;
                }
                keys.add(key);
            }
        }
        return new ResolvedAliasWithRefs(new KeyAlias(aliasName, keys), referencedAliasNames);
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
            List<String> tokens = List.of(propertyValue.split("\\s+"));
            String aliasName = keyMatcher.group(1);
            LayoutKeyAlias layoutKeyAlias = layoutKeyAliasByName.computeIfAbsent(
                    aliasName, name -> new LayoutKeyAlias());
            if (keyMatcher.group(2) == null) {
                layoutKeyAlias.noLayoutTokens = tokens;
            }
            else {
                String layoutName = keyMatcher.group(3);
                KeyboardLayout layout = parseKeyboardLayout(layoutName);
                layoutKeyAlias.tokensByLayout.put(layout, tokens);
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
                    List.of("enabled", "duration-millis", "mode", "only-if-idle"));
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
                case UI -> {
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
        // Copy MutateMode commands from the parent property to the child,
        // adjusting the modeName to the child mode.
        String childModeName = property.propertyKey.modeName();
        if (childModeName != null) {
            for (Map.Entry<Combo, List<Command>> entry :
                    parentProperty.mutateModeCommands.entrySet()) {
                for (Command command : entry.getValue()) {
                    Command adjusted = command;
                    if (command instanceof Command.MutateMode mutateMode) {
                        adjusted = new Command.MutateMode(
                                childModeName, mutateMode.propertyPath(),
                                mutateMode.newPropertyValue(), mutateMode.combo());
                    }
                    property.mutateModeCommands
                            .computeIfAbsent(entry.getKey(),
                                    c -> new ArrayList<>())
                            .add(adjusted);
                }
            }
        }
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

    private static void parseIndicatorProperty(IndicatorBuilder indicator,
                                                String key, String propertyValue,
                                                Predicate<String> fontAvailability,
                                                ModePropertyPath propertyPathPrefix,
                                                Map<Combo, List<Command>> mutateModeCommandMap,
                                                String modeName,
                                                ComboMoveDuration defaultComboMoveDuration,
                                                Map<String, KeyAlias> keyAliases,
                                                Map<String, AppAlias> appAliases,
                                                KeyResolver keyResolver) {
        ModePropertyHandler handler =
                indicatorHandler(propertyPathPrefix, indicator, key, fontAvailability);
        if (handler == null)
            throw new IllegalArgumentException("Invalid indicator property key: " + key);
        if (!tryParseComboProperty(propertyValue, modeName, handler.propertyPath(),
                handler.valueParser(), handler.modeBuilderSetter(), mutateModeCommandMap,
                defaultComboMoveDuration, keyAliases, appAliases, keyResolver))
            handler.modeBuilderSetter().accept(propertyValue);
    }

    private record ModePropertyHandler(
            ModePropertyPath propertyPath,
            Function<String, Object> valueParser,
            Consumer<String> modeBuilderSetter
    ) {
        static <T> ModePropertyHandler of(
                ModePropertyPath path,
                Function<String, T> parser,
                Consumer<T> setter) {
            return new ModePropertyHandler(
                    path,
                    parser::apply,
                    s -> setter.accept(parser.apply(s)));
        }
    }

    private static ModePropertyHandler mouseHandler(
            ModePropertyPath prefix, Mouse.MouseBuilder mouse, String key) {
        return switch (key) {
            case "initial-velocity" -> ModePropertyHandler.of(prefix.append("velocity", "initialVelocity"), v -> Double.parseDouble(v), v -> mouse.velocity().initialVelocity(v));
            case "max-velocity" -> ModePropertyHandler.of(prefix.append("velocity", "maxVelocity"), v -> Double.parseDouble(v), v -> mouse.velocity().maxVelocity(v));
            case "acceleration" -> ModePropertyHandler.of(prefix.append("velocity", "acceleration"), v -> Double.parseDouble(v), v -> mouse.velocity().acceleration(v));
            case "acceleration-easing" -> ModePropertyHandler.of(prefix.append("velocity", "accelerationEasing"), v -> parseEasing(v), v -> mouse.velocity().accelerationEasing(v));
            case "deceleration" -> ModePropertyHandler.of(prefix.append("velocity", "deceleration"), v -> Double.parseDouble(v), v -> mouse.velocity().deceleration(v));
            case "smooth-jump-enabled" -> ModePropertyHandler.of(prefix.append("smoothJumpEnabled"), v -> Boolean.parseBoolean(v), v -> mouse.smoothJumpEnabled(v));
            case "smooth-jump-velocity" -> ModePropertyHandler.of(prefix.append("smoothJumpVelocity"), v -> Double.parseDouble(v), v -> mouse.smoothJumpVelocity(v));
            default -> null;
        };
    }

    private static ModePropertyHandler wheelHandler(
            ModePropertyPath prefix, Wheel.WheelBuilder wheel, String key) {
        return switch (key) {
            case "initial-velocity" -> ModePropertyHandler.of(prefix.append("velocity", "initialVelocity"), v -> Double.parseDouble(v), v -> wheel.velocity().initialVelocity(v));
            case "max-velocity" -> ModePropertyHandler.of(prefix.append("velocity", "maxVelocity"), v -> Double.parseDouble(v), v -> wheel.velocity().maxVelocity(v));
            case "acceleration" -> ModePropertyHandler.of(prefix.append("velocity", "acceleration"), v -> Double.parseDouble(v), v -> wheel.velocity().acceleration(v));
            case "acceleration-easing" -> ModePropertyHandler.of(prefix.append("velocity", "accelerationEasing"), v -> parseEasing(v), v -> wheel.velocity().accelerationEasing(v));
            case "deceleration" -> ModePropertyHandler.of(prefix.append("velocity", "deceleration"), v -> Double.parseDouble(v), v -> wheel.velocity().deceleration(v));
            default -> null;
        };
    }

    private static ModePropertyHandler gridHandler(
            ModePropertyPath prefix,
            GridConfiguration.GridConfigurationBuilder grid, String key) {
        return switch (key) {
            // @formatter:off
            case "area" -> new ModePropertyHandler(prefix.append("area"),
                    v -> {
                        GridArea.GridAreaType type = parseGridAreaType("grid.area", v);
                        return (Function<GridArea, GridArea>) currentArea -> switch (type) {
                            case ACTIVE_SCREEN -> new GridArea.ActiveScreenGridArea(
                                    currentArea.widthPercent(), currentArea.heightPercent(),
                                    currentArea.topInset(), currentArea.bottomInset(),
                                    currentArea.leftInset(), currentArea.rightInset());
                            case ACTIVE_WINDOW -> new GridArea.ActiveWindowGridArea(
                                    currentArea.widthPercent(), currentArea.heightPercent(),
                                    currentArea.topInset(), currentArea.bottomInset(),
                                    currentArea.leftInset(), currentArea.rightInset());
                        };
                    },
                    v -> grid.area().type(parseGridAreaType("grid.area", v)));
            case "area-width-percent" -> ModePropertyHandler.of(prefix.append("area", "widthPercent"), v -> parseNonZeroPercent(v, 2), v -> grid.area().widthPercent(v));
            case "area-height-percent" -> ModePropertyHandler.of(prefix.append("area", "heightPercent"), v -> parseNonZeroPercent(v, 2), v -> grid.area().heightPercent(v));
            case "area-top-inset" -> ModePropertyHandler.of(prefix.append("area", "topInset"), v -> parseUnsignedInteger(v, 0, 10_000), v -> grid.area().topInset(v));
            case "area-bottom-inset" -> ModePropertyHandler.of(prefix.append("area", "bottomInset"), v -> parseUnsignedInteger(v, 0, 10_000), v -> grid.area().bottomInset(v));
            case "area-left-inset" -> ModePropertyHandler.of(prefix.append("area", "leftInset"), v -> parseUnsignedInteger(v, 0, 10_000), v -> grid.area().leftInset(v));
            case "area-right-inset" -> ModePropertyHandler.of(prefix.append("area", "rightInset"), v -> parseUnsignedInteger(v, 0, 10_000), v -> grid.area().rightInset(v));
            case "synchronization" -> ModePropertyHandler.of(prefix.append("synchronization"), v -> parseSynchronization("grid.synchronization", v), v -> grid.synchronization(v));
            case "row-count" -> ModePropertyHandler.of(prefix.append("rowCount"), v -> parseUnsignedInteger(v, 1, 50), v -> grid.rowCount(v));
            case "column-count" -> ModePropertyHandler.of(prefix.append("columnCount"), v -> parseUnsignedInteger(v, 1, 50), v -> grid.columnCount(v));
            case "line-visible" -> ModePropertyHandler.of(prefix.append("lineVisible"), v -> Boolean.parseBoolean(v), v -> grid.lineVisible(v));
            case "line-color" -> ModePropertyHandler.of(prefix.append("lineHexColor"), v -> checkColorFormat(v), v -> grid.lineHexColor(v));
            case "line-thickness" -> ModePropertyHandler.of(prefix.append("lineThickness"), v -> parseDouble(v, false, 0, 1000), v -> grid.lineThickness(v));
            // @formatter:on
            default -> null;
        };
    }

    private static ModePropertyHandler timeoutHandler(
            ModePropertyPath prefix,
            ModeTimeout.ModeTimeoutBuilder timeout, String key,
            Set<String> modeReferences) {
        return switch (key) {
            // @formatter:off
            case "enabled" -> ModePropertyHandler.of(prefix.append("enabled"), v -> Boolean.parseBoolean(v), v -> timeout.enabled(v));
            case "duration-millis" -> ModePropertyHandler.of(prefix.append("duration"), v -> parseDuration(v), v -> timeout.duration(v));
            case "mode" -> ModePropertyHandler.of(prefix.append("modeName"), v -> { modeReferences.add(checkModeReference(v)); return v; }, v -> timeout.modeName(v));
            case "only-if-idle" -> ModePropertyHandler.of(prefix.append("onlyIfIdle"), v -> Boolean.parseBoolean(v), v -> timeout.onlyIfIdle(v));
            // @formatter:on
            default -> null;
        };
    }

    private static ModePropertyHandler hideCursorHandler(
            ModePropertyPath prefix,
            HideCursor.HideCursorBuilder hideCursor, String key) {
        return switch (key) {
            // @formatter:off
            case "enabled" -> ModePropertyHandler.of(prefix.append("enabled"), v -> Boolean.parseBoolean(v), v -> hideCursor.enabled(v));
            case "idle-duration-millis" -> ModePropertyHandler.of(prefix.append("idleDuration"), v -> parseDuration(v), v -> hideCursor.idleDuration(v));
            // @formatter:on
            default -> null;
        };
    }

    private static ModePropertyHandler zoomHandler(
            ModePropertyPath prefix,
            ZoomConfiguration.ZoomConfigurationBuilder zoom, String key) {
        return switch (key) {
            // @formatter:off
            case "percent" -> ModePropertyHandler.of(prefix.append("percent"), v -> parseDouble(v, true, 1, 100), v -> zoom.percent(v));
            case "center" -> ModePropertyHandler.of(prefix.append("center"), v -> parseZoomCenter("zoom.center", v), v -> zoom.center(v));
            case "animation-enabled" -> ModePropertyHandler.of(prefix.append("animationEnabled"), v -> Boolean.parseBoolean(v), v -> zoom.animationEnabled(v));
            case "animation-easing" -> ModePropertyHandler.of(prefix.append("animationEasing"), v -> parseEasing(v), v -> zoom.animationEasing(v));
            case "animation-duration-millis" -> ModePropertyHandler.of(prefix.append("animationDurationMillis"), v -> parseDouble(v, true, 0, 10000), v -> zoom.animationDurationMillis(v));
            // @formatter:on
            default -> null;
        };
    }

    private static ModePropertyHandler indicatorHandler(
            ModePropertyPath prefix, IndicatorBuilder indicator,
            String key, Predicate<String> fontAvailability) {
        return switch (key) {
            // @formatter:off
            case "size" -> ModePropertyHandler.of(prefix.append("size"), v -> parseUnsignedInteger(v, 1, 100), v -> indicator.size(v));
            case "edge-count" -> ModePropertyHandler.of(prefix.append("edgeCount"), v -> parseUnsignedInteger(v, 3, 1000), v -> indicator.edgeCount(v));
            case "color" -> ModePropertyHandler.of(prefix.append("hexColor"), v -> checkColorFormat(v), v -> indicator.hexColor(v));
            case "opacity" -> ModePropertyHandler.of(prefix.append("opacity"), v -> parseDouble(v, true, 0, 1), v -> indicator.opacity(v));
            case "outer-outline-thickness", "outline-thickness" -> ModePropertyHandler.of(prefix.append("outerOutline").append("thickness"), v -> parseDouble(v, true, 0, 100), v -> indicator.outerOutline().thickness(v));
            case "outer-outline-color", "outline-color" -> ModePropertyHandler.of(prefix.append("outerOutline").append("hexColor"), v -> checkColorFormat(v), v -> indicator.outerOutline().hexColor(v));
            case "outer-outline-opacity", "outline-opacity" -> ModePropertyHandler.of(prefix.append("outerOutline").append("opacity"), v -> parseDouble(v, true, 0, 1), v -> indicator.outerOutline().opacity(v));
            case "outer-outline-fill-percent", "outline-fill-percent" -> ModePropertyHandler.of(prefix.append("outerOutline").append("fillPercent"), v -> parseDouble(v, true, 0, 1), v -> indicator.outerOutline().fillPercent(v));
            case "outer-outline-fill-start-angle", "outline-fill-start-angle" -> ModePropertyHandler.of(prefix.append("outerOutline").append("fillStartAngle"), v -> parseDouble(v, true, 0, 360), v -> indicator.outerOutline().fillStartAngle(v));
            case "outer-outline-fill-direction", "outline-fill-direction" -> ModePropertyHandler.of(prefix.append("outerOutline").append("fillDirection"), v -> FillDirection.fromString(v), v -> indicator.outerOutline().fillDirection(v));
            case "inner-outline-thickness" -> ModePropertyHandler.of(prefix.append("innerOutline").append("thickness"), v -> parseDouble(v, true, 0, 100), v -> indicator.innerOutline().thickness(v));
            case "inner-outline-color" -> ModePropertyHandler.of(prefix.append("innerOutline").append("hexColor"), v -> checkColorFormat(v), v -> indicator.innerOutline().hexColor(v));
            case "inner-outline-opacity" -> ModePropertyHandler.of(prefix.append("innerOutline").append("opacity"), v -> parseDouble(v, true, 0, 1), v -> indicator.innerOutline().opacity(v));
            case "inner-outline-fill-percent" -> ModePropertyHandler.of(prefix.append("innerOutline").append("fillPercent"), v -> parseDouble(v, true, 0, 1), v -> indicator.innerOutline().fillPercent(v));
            case "inner-outline-fill-start-angle" -> ModePropertyHandler.of(prefix.append("innerOutline").append("fillStartAngle"), v -> parseDouble(v, true, 0, 360), v -> indicator.innerOutline().fillStartAngle(v));
            case "inner-outline-fill-direction" -> ModePropertyHandler.of(prefix.append("innerOutline").append("fillDirection"), v -> FillDirection.fromString(v), v -> indicator.innerOutline().fillDirection(v));
            case "shadow-blur-radius" -> ModePropertyHandler.of(prefix.append("shadow").append("blurRadius"), v -> parseDouble(v, true, 0, 1000), v -> indicator.shadow().blurRadius(v));
            case "shadow-color" -> ModePropertyHandler.of(prefix.append("shadow").append("hexColor"), v -> checkColorFormat(v), v -> indicator.shadow().hexColor(v));
            case "shadow-opacity" -> ModePropertyHandler.of(prefix.append("shadow").append("opacity"), v -> parseDouble(v, true, 0, 1), v -> indicator.shadow().opacity(v));
            case "shadow-stack-count" -> ModePropertyHandler.of(prefix.append("shadow").append("stackCount"), v -> parseUnsignedInteger(v, 1, 100), v -> indicator.shadow().stackCount(v));
            case "shadow-horizontal-offset" -> ModePropertyHandler.of(prefix.append("shadow").append("horizontalOffset"), v -> parseDouble(v, true, -100, 100), v -> indicator.shadow().horizontalOffset(v));
            case "shadow-vertical-offset" -> ModePropertyHandler.of(prefix.append("shadow").append("verticalOffset"), v -> parseDouble(v, true, -100, 100), v -> indicator.shadow().verticalOffset(v));
            case "label-enabled" -> ModePropertyHandler.of(prefix.append("labelEnabled"), v -> Boolean.parseBoolean(v), v -> indicator.labelEnabled(v));
            case "label-text" -> ModePropertyHandler.of(prefix.append("labelText"), v -> v, v -> indicator.labelText(v));
            case "label-font-name" -> ModePropertyHandler.of(prefix.append("labelFontStyle").append("name"), v -> parseFontName(v, fontAvailability), v -> indicator.labelFontStyle().name(v));
            case "label-font-size" -> ModePropertyHandler.of(prefix.append("labelFontStyle").append("size"), v -> parseDouble(v, false, 0, 1000), v -> indicator.labelFontStyle().size(v));
            case "label-font-color" -> ModePropertyHandler.of(prefix.append("labelFontStyle").append("hexColor"), v -> checkColorFormat(v), v -> indicator.labelFontStyle().hexColor(v));
            case "label-font-weight" -> ModePropertyHandler.of(prefix.append("labelFontStyle").append("weight"), v -> FontWeight.of(v), v -> indicator.labelFontStyle().weight(v));
            case "label-font-opacity" -> ModePropertyHandler.of(prefix.append("labelFontStyle").append("opacity"), v -> parseDouble(v, true, 0, 1), v -> indicator.labelFontStyle().opacity(v));
            case "label-font-outline-thickness" -> ModePropertyHandler.of(prefix.append("labelFontStyle").append("outlineThickness"), v -> parseDouble(v, true, 0, 1000), v -> indicator.labelFontStyle().outlineThickness(v));
            case "label-font-outline-color" -> ModePropertyHandler.of(prefix.append("labelFontStyle").append("outlineHexColor"), v -> checkColorFormat(v), v -> indicator.labelFontStyle().outlineHexColor(v));
            case "label-font-outline-opacity" -> ModePropertyHandler.of(prefix.append("labelFontStyle").append("outlineOpacity"), v -> parseDouble(v, true, 0, 1), v -> indicator.labelFontStyle().outlineOpacity(v));
            case "label-font-shadow-blur-radius" -> ModePropertyHandler.of(prefix.append("labelFontStyle").append("shadow").append("blurRadius"), v -> parseDouble(v, true, 0, 1000), v -> indicator.labelFontStyle().shadow().blurRadius(v));
            case "label-font-shadow-color" -> ModePropertyHandler.of(prefix.append("labelFontStyle").append("shadow").append("hexColor"), v -> checkColorFormat(v), v -> indicator.labelFontStyle().shadow().hexColor(v));
            case "label-font-shadow-opacity" -> ModePropertyHandler.of(prefix.append("labelFontStyle").append("shadow").append("opacity"), v -> parseDouble(v, true, 0, 1), v -> indicator.labelFontStyle().shadow().opacity(v));
            case "label-font-shadow-stack-count" -> ModePropertyHandler.of(prefix.append("labelFontStyle").append("shadow").append("stackCount"), v -> parseUnsignedInteger(v, 1, 100), v -> indicator.labelFontStyle().shadow().stackCount(v));
            case "label-font-shadow-horizontal-offset" -> ModePropertyHandler.of(prefix.append("labelFontStyle").append("shadow").append("horizontalOffset"), v -> parseDouble(v, true, -100, 100), v -> indicator.labelFontStyle().shadow().horizontalOffset(v));
            case "label-font-shadow-vertical-offset" -> ModePropertyHandler.of(prefix.append("labelFontStyle").append("shadow").append("verticalOffset"), v -> parseDouble(v, true, -100, 100), v -> indicator.labelFontStyle().shadow().verticalOffset(v));
            case "position" -> ModePropertyHandler.of(prefix.append("position"), v -> IndicatorPosition.fromString(v), v -> indicator.position(v));
            // @formatter:on
            default -> null;
        };
    }


    private static ModePropertyHandler hintHandler(
            ModePropertyPath prefix,
            HintMeshConfigurationBuilder hintMeshBuilder,
            ViewportFilter viewportFilter,
            String key, Predicate<String> fontAvailability,
            Map<String, KeyAlias> keyAliases, KeyResolver keyResolver) {
        return switch (key) {
            // @formatter:off
            // Top-level properties
            case "enabled" -> ModePropertyHandler.of(prefix.append("enabled"), v -> Boolean.parseBoolean(v), v -> hintMeshBuilder.enabled(v));
            case "visible" -> ModePropertyHandler.of(prefix.append("visible"), v -> Boolean.parseBoolean(v), v -> hintMeshBuilder.visible(v));
            case "mouse-movement" -> ModePropertyHandler.of(prefix.append("mouseMovement"), v -> parseHintMouseMovement("hint.mouse-movement", v), v -> hintMeshBuilder.mouseMovement(v));
            case "eat-unused-selection-keys" -> ModePropertyHandler.of(prefix.append("eatUnusedSelectionKeys"), v -> Boolean.parseBoolean(v), v -> hintMeshBuilder.eatUnusedSelectionKeys(v));
            // Type property (sealed variant): uses Function for GRID because it
            // needs the current area/gridLayout at mutation time, not parse time.
            case "type" -> new ModePropertyHandler(prefix.append("type"), v -> {
                HintMeshType.HintMeshTypeType typeType = parseHintMeshTypeType("hint.type", v);
                return switch (typeType) {
                    case GRID -> (Object) (Function<Object, Object>) currentType -> {
                        if (currentType instanceof HintMeshType.HintGrid)
                            return currentType;
                        throw new IllegalArgumentException(
                                "Cannot combo-trigger hint type to GRID: current type is " +
                                currentType.getClass().getSimpleName());
                    };
                    case POSITION_HISTORY -> new HintMeshType.HintPositionHistory();
                    case UI -> new HintMeshType.UiHintMesh();
                };
            }, v -> hintMeshBuilder.type().type(parseHintMeshTypeType("hint.type", v)));
            // Grid area properties: uses Function for ACTIVE_SCREEN because it
            // needs the current center at mutation time, not parse time.
            case "grid-area" -> new ModePropertyHandler(prefix.append("type", "area"), v -> {
                HintGridAreaType areaType = parseHintGridAreaType("hint.grid-area", v);
                return switch (areaType) {
                    case ACTIVE_SCREEN -> (Object) (Function<Object, Object>) currentArea -> {
                        ActiveScreenHintGridAreaCenter center =
                                currentArea instanceof HintGridArea.ActiveScreenHintGridArea activeScreen
                                        ? activeScreen.center()
                                        : ActiveScreenHintGridAreaCenter.MOUSE;
                        return new HintGridArea.ActiveScreenHintGridArea(center);
                    };
                    case ACTIVE_WINDOW -> new HintGridArea.ActiveWindowHintGridArea();
                    case ALL_SCREENS -> new HintGridArea.AllScreensHintGridArea();
                };
            }, v -> hintMeshBuilder.type().gridArea().type(parseHintGridAreaType("hint.grid-area", v)));
            case "active-screen-grid-area-center" -> ModePropertyHandler.of(prefix.append("type", "area", "center"), v -> parseActiveScreenHintGridAreaCenter("hint.active-screen-grid-area-center", v), v -> hintMeshBuilder.type().gridArea().activeScreenHintGridAreaCenter(v));
            // Grid layout properties (viewport-filtered)
            case "grid-max-row-count" -> ModePropertyHandler.of(prefix.append("type", "gridLayoutByFilter", "maxRowCount"), v -> parseUnsignedInteger(v, 1, 200), v -> hintMeshBuilder.type().gridLayout(viewportFilter).maxRowCount(v));
            case "grid-max-column-count" -> ModePropertyHandler.of(prefix.append("type", "gridLayoutByFilter", "maxColumnCount"), v -> parseUnsignedInteger(v, 1, 200), v -> hintMeshBuilder.type().gridLayout(viewportFilter).maxColumnCount(v));
            case "grid-cell-width" -> ModePropertyHandler.of(prefix.append("type", "gridLayoutByFilter", "cellWidth"), v -> parseDouble(v, false, 0, 10_000), v -> hintMeshBuilder.type().gridLayout(viewportFilter).cellWidth(v));
            case "grid-cell-height" -> ModePropertyHandler.of(prefix.append("type", "gridLayoutByFilter", "cellHeight"), v -> parseDouble(v, false, 0, 10_000), v -> hintMeshBuilder.type().gridLayout(viewportFilter).cellHeight(v));
            case "layout-row-count" -> ModePropertyHandler.of(prefix.append("type", "gridLayoutByFilter", "layoutRowCount"), v -> parseUnsignedInteger(v, 1, 1_000_000_000), v -> hintMeshBuilder.type().gridLayout(viewportFilter).layoutRowCount(v));
            case "layout-column-count" -> ModePropertyHandler.of(prefix.append("type", "gridLayoutByFilter", "layoutColumnCount"), v -> parseUnsignedInteger(v, 1, 1_000_000_000), v -> hintMeshBuilder.type().gridLayout(viewportFilter).layoutColumnCount(v));
            case "layout-row-oriented" -> ModePropertyHandler.of(prefix.append("type", "gridLayoutByFilter", "layoutRowOriented"), v -> Boolean.parseBoolean(v), v -> hintMeshBuilder.type().gridLayout(viewportFilter).layoutRowOriented(v));
            // Key properties (viewport-filtered)
            case "selection-keys" -> ModePropertyHandler.of(prefix.append("keysByFilter", "selectionKeys"), v -> parseHintKeys(v, keyAliases, keyResolver), v -> hintMeshBuilder.keys(viewportFilter).selectionKeys(v));
            case "row-key-offset" -> ModePropertyHandler.of(prefix.append("keysByFilter", "rowKeyOffset"), v -> parseUnsignedInteger(v, 0, 1_000), v -> hintMeshBuilder.keys(viewportFilter).rowKeyOffset(v));
            // Style: main font
            case "font-name" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "defaultFontStyle", "name"), v -> parseFontName(v, fontAvailability), v -> hintMeshBuilder.style(viewportFilter).fontStyle().defaultFontStyle().name(v));
            case "font-weight" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "defaultFontStyle", "weight"), v -> FontWeight.of(v), v -> hintMeshBuilder.style(viewportFilter).fontStyle().defaultFontStyle().weight(v));
            case "font-size" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "defaultFontStyle", "size"), v -> parseDouble(v, false, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).fontStyle().defaultFontStyle().size(v));
            case "font-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "defaultFontStyle", "hexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).fontStyle().defaultFontStyle().hexColor(v));
            case "font-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "defaultFontStyle", "opacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).fontStyle().defaultFontStyle().opacity(v));
            case "font-spacing-percent" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "spacingPercent"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).fontStyle().spacingPercent(v));
            case "font-outline-thickness" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "defaultFontStyle", "outlineThickness"), v -> parseDouble(v, true, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).fontStyle().defaultFontStyle().outlineThickness(v));
            case "font-outline-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "defaultFontStyle", "outlineHexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).fontStyle().defaultFontStyle().outlineHexColor(v));
            case "font-outline-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "defaultFontStyle", "outlineOpacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).fontStyle().defaultFontStyle().outlineOpacity(v));
            case "font-shadow-blur-radius" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "defaultFontStyle", "shadow", "blurRadius"), v -> parseDouble(v, true, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).fontStyle().defaultFontStyle().shadow().blurRadius(v));
            case "font-shadow-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "defaultFontStyle", "shadow", "hexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).fontStyle().defaultFontStyle().shadow().hexColor(v));
            case "font-shadow-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "defaultFontStyle", "shadow", "opacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).fontStyle().defaultFontStyle().shadow().opacity(v));
            case "font-shadow-stack-count" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "defaultFontStyle", "shadow", "stackCount"), v -> parseUnsignedInteger(v, 1, 100), v -> hintMeshBuilder.style(viewportFilter).fontStyle().defaultFontStyle().shadow().stackCount(v));
            case "font-shadow-horizontal-offset" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "defaultFontStyle", "shadow", "horizontalOffset"), v -> parseDouble(v, true, -100, 100), v -> hintMeshBuilder.style(viewportFilter).fontStyle().defaultFontStyle().shadow().horizontalOffset(v));
            case "font-shadow-vertical-offset" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "defaultFontStyle", "shadow", "verticalOffset"), v -> parseDouble(v, true, -100, 100), v -> hintMeshBuilder.style(viewportFilter).fontStyle().defaultFontStyle().shadow().verticalOffset(v));
            // Style: selected font
            case "selected-font-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "selectedFontStyle", "hexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).fontStyle().selectedFontStyle().hexColor(v));
            case "selected-font-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "selectedFontStyle", "opacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).fontStyle().selectedFontStyle().opacity(v));
            case "selected-font-name" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "selectedFontStyle", "name"), v -> parseFontName(v, fontAvailability), v -> hintMeshBuilder.style(viewportFilter).fontStyle().selectedFontStyle().name(v));
            case "selected-font-weight" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "selectedFontStyle", "weight"), v -> FontWeight.of(v), v -> hintMeshBuilder.style(viewportFilter).fontStyle().selectedFontStyle().weight(v));
            case "selected-font-size" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "selectedFontStyle", "size"), v -> parseDouble(v, false, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).fontStyle().selectedFontStyle().size(v));
            case "selected-font-outline-thickness" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "selectedFontStyle", "outlineThickness"), v -> parseDouble(v, true, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).fontStyle().selectedFontStyle().outlineThickness(v));
            case "selected-font-outline-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "selectedFontStyle", "outlineHexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).fontStyle().selectedFontStyle().outlineHexColor(v));
            case "selected-font-outline-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "selectedFontStyle", "outlineOpacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).fontStyle().selectedFontStyle().outlineOpacity(v));
            case "selected-font-shadow-blur-radius" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "selectedFontStyle", "shadow", "blurRadius"), v -> parseDouble(v, true, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).fontStyle().selectedFontStyle().shadow().blurRadius(v));
            case "selected-font-shadow-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "selectedFontStyle", "shadow", "hexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).fontStyle().selectedFontStyle().shadow().hexColor(v));
            case "selected-font-shadow-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "selectedFontStyle", "shadow", "opacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).fontStyle().selectedFontStyle().shadow().opacity(v));
            case "selected-font-shadow-stack-count" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "selectedFontStyle", "shadow", "stackCount"), v -> parseUnsignedInteger(v, 1, 100), v -> hintMeshBuilder.style(viewportFilter).fontStyle().selectedFontStyle().shadow().stackCount(v));
            case "selected-font-shadow-horizontal-offset" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "selectedFontStyle", "shadow", "horizontalOffset"), v -> parseDouble(v, true, -100, 100), v -> hintMeshBuilder.style(viewportFilter).fontStyle().selectedFontStyle().shadow().horizontalOffset(v));
            case "selected-font-shadow-vertical-offset" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "selectedFontStyle", "shadow", "verticalOffset"), v -> parseDouble(v, true, -100, 100), v -> hintMeshBuilder.style(viewportFilter).fontStyle().selectedFontStyle().shadow().verticalOffset(v));
            // Style: focused font
            case "focused-font-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "focusedFontStyle", "hexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).fontStyle().focusedFontStyle().hexColor(v));
            case "focused-font-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "focusedFontStyle", "opacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).fontStyle().focusedFontStyle().opacity(v));
            case "focused-font-name" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "focusedFontStyle", "name"), v -> parseFontName(v, fontAvailability), v -> hintMeshBuilder.style(viewportFilter).fontStyle().focusedFontStyle().name(v));
            case "focused-font-weight" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "focusedFontStyle", "weight"), v -> FontWeight.of(v), v -> hintMeshBuilder.style(viewportFilter).fontStyle().focusedFontStyle().weight(v));
            case "focused-font-size" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "focusedFontStyle", "size"), v -> parseDouble(v, false, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).fontStyle().focusedFontStyle().size(v));
            case "focused-font-outline-thickness" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "focusedFontStyle", "outlineThickness"), v -> parseDouble(v, true, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).fontStyle().focusedFontStyle().outlineThickness(v));
            case "focused-font-outline-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "focusedFontStyle", "outlineHexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).fontStyle().focusedFontStyle().outlineHexColor(v));
            case "focused-font-outline-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "focusedFontStyle", "outlineOpacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).fontStyle().focusedFontStyle().outlineOpacity(v));
            case "focused-font-shadow-blur-radius" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "focusedFontStyle", "shadow", "blurRadius"), v -> parseDouble(v, true, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).fontStyle().focusedFontStyle().shadow().blurRadius(v));
            case "focused-font-shadow-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "focusedFontStyle", "shadow", "hexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).fontStyle().focusedFontStyle().shadow().hexColor(v));
            case "focused-font-shadow-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "focusedFontStyle", "shadow", "opacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).fontStyle().focusedFontStyle().shadow().opacity(v));
            case "focused-font-shadow-stack-count" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "focusedFontStyle", "shadow", "stackCount"), v -> parseUnsignedInteger(v, 1, 100), v -> hintMeshBuilder.style(viewportFilter).fontStyle().focusedFontStyle().shadow().stackCount(v));
            case "focused-font-shadow-horizontal-offset" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "focusedFontStyle", "shadow", "horizontalOffset"), v -> parseDouble(v, true, -100, 100), v -> hintMeshBuilder.style(viewportFilter).fontStyle().focusedFontStyle().shadow().horizontalOffset(v));
            case "focused-font-shadow-vertical-offset" -> ModePropertyHandler.of(prefix.append("styleByFilter", "fontStyle", "focusedFontStyle", "shadow", "verticalOffset"), v -> parseDouble(v, true, -100, 100), v -> hintMeshBuilder.style(viewportFilter).fontStyle().focusedFontStyle().shadow().verticalOffset(v));
            // Style: prefix font
            case "prefix-in-background" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixInBackground"), v -> Boolean.parseBoolean(v), v -> hintMeshBuilder.style(viewportFilter).prefixInBackground(v));
            case "prefix-font-name" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "defaultFontStyle", "name"), v -> parseFontName(v, fontAvailability), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().defaultFontStyle().name(v));
            case "prefix-font-weight" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "defaultFontStyle", "weight"), v -> FontWeight.of(v), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().defaultFontStyle().weight(v));
            case "prefix-font-size" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "defaultFontStyle", "size"), v -> parseDouble(v, false, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().defaultFontStyle().size(v));
            case "prefix-font-spacing-percent" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "spacingPercent"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().spacingPercent(v));
            case "prefix-font-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "defaultFontStyle", "hexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().defaultFontStyle().hexColor(v));
            case "prefix-font-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "defaultFontStyle", "opacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().defaultFontStyle().opacity(v));
            case "prefix-font-outline-thickness" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "defaultFontStyle", "outlineThickness"), v -> parseDouble(v, true, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().defaultFontStyle().outlineThickness(v));
            case "prefix-font-outline-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "defaultFontStyle", "outlineHexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().defaultFontStyle().outlineHexColor(v));
            case "prefix-font-outline-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "defaultFontStyle", "outlineOpacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().defaultFontStyle().outlineOpacity(v));
            case "prefix-font-shadow-blur-radius" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "defaultFontStyle", "shadow", "blurRadius"), v -> parseDouble(v, true, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().defaultFontStyle().shadow().blurRadius(v));
            case "prefix-font-shadow-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "defaultFontStyle", "shadow", "hexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().defaultFontStyle().shadow().hexColor(v));
            case "prefix-font-shadow-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "defaultFontStyle", "shadow", "opacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().defaultFontStyle().shadow().opacity(v));
            case "prefix-font-shadow-stack-count" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "defaultFontStyle", "shadow", "stackCount"), v -> parseUnsignedInteger(v, 1, 100), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().defaultFontStyle().shadow().stackCount(v));
            case "prefix-font-shadow-horizontal-offset" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "defaultFontStyle", "shadow", "horizontalOffset"), v -> parseDouble(v, true, -100, 100), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().defaultFontStyle().shadow().horizontalOffset(v));
            case "prefix-font-shadow-vertical-offset" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "defaultFontStyle", "shadow", "verticalOffset"), v -> parseDouble(v, true, -100, 100), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().defaultFontStyle().shadow().verticalOffset(v));
            // Style: prefix selected font
            case "prefix-selected-font-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "selectedFontStyle", "hexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().selectedFontStyle().hexColor(v));
            case "prefix-selected-font-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "selectedFontStyle", "opacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().selectedFontStyle().opacity(v));
            case "prefix-selected-font-name" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "selectedFontStyle", "name"), v -> parseFontName(v, fontAvailability), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().selectedFontStyle().name(v));
            case "prefix-selected-font-weight" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "selectedFontStyle", "weight"), v -> FontWeight.of(v), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().selectedFontStyle().weight(v));
            case "prefix-selected-font-size" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "selectedFontStyle", "size"), v -> parseDouble(v, false, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().selectedFontStyle().size(v));
            case "prefix-selected-font-outline-thickness" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "selectedFontStyle", "outlineThickness"), v -> parseDouble(v, true, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().selectedFontStyle().outlineThickness(v));
            case "prefix-selected-font-outline-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "selectedFontStyle", "outlineHexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().selectedFontStyle().outlineHexColor(v));
            case "prefix-selected-font-outline-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "selectedFontStyle", "outlineOpacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().selectedFontStyle().outlineOpacity(v));
            case "prefix-selected-font-shadow-blur-radius" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "selectedFontStyle", "shadow", "blurRadius"), v -> parseDouble(v, true, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().selectedFontStyle().shadow().blurRadius(v));
            case "prefix-selected-font-shadow-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "selectedFontStyle", "shadow", "hexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().selectedFontStyle().shadow().hexColor(v));
            case "prefix-selected-font-shadow-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "selectedFontStyle", "shadow", "opacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().selectedFontStyle().shadow().opacity(v));
            case "prefix-selected-font-shadow-stack-count" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "selectedFontStyle", "shadow", "stackCount"), v -> parseUnsignedInteger(v, 1, 100), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().selectedFontStyle().shadow().stackCount(v));
            case "prefix-selected-font-shadow-horizontal-offset" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "selectedFontStyle", "shadow", "horizontalOffset"), v -> parseDouble(v, true, -100, 100), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().selectedFontStyle().shadow().horizontalOffset(v));
            case "prefix-selected-font-shadow-vertical-offset" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "selectedFontStyle", "shadow", "verticalOffset"), v -> parseDouble(v, true, -100, 100), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().selectedFontStyle().shadow().verticalOffset(v));
            // Style: prefix focused font
            case "prefix-focused-font-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "focusedFontStyle", "hexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().focusedFontStyle().hexColor(v));
            case "prefix-focused-font-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "focusedFontStyle", "opacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().focusedFontStyle().opacity(v));
            case "prefix-focused-font-name" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "focusedFontStyle", "name"), v -> parseFontName(v, fontAvailability), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().focusedFontStyle().name(v));
            case "prefix-focused-font-weight" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "focusedFontStyle", "weight"), v -> FontWeight.of(v), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().focusedFontStyle().weight(v));
            case "prefix-focused-font-size" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "focusedFontStyle", "size"), v -> parseDouble(v, false, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().focusedFontStyle().size(v));
            case "prefix-focused-font-outline-thickness" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "focusedFontStyle", "outlineThickness"), v -> parseDouble(v, true, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().focusedFontStyle().outlineThickness(v));
            case "prefix-focused-font-outline-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "focusedFontStyle", "outlineHexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().focusedFontStyle().outlineHexColor(v));
            case "prefix-focused-font-outline-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "focusedFontStyle", "outlineOpacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().focusedFontStyle().outlineOpacity(v));
            case "prefix-focused-font-shadow-blur-radius" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "focusedFontStyle", "shadow", "blurRadius"), v -> parseDouble(v, true, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().focusedFontStyle().shadow().blurRadius(v));
            case "prefix-focused-font-shadow-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "focusedFontStyle", "shadow", "hexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().focusedFontStyle().shadow().hexColor(v));
            case "prefix-focused-font-shadow-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "focusedFontStyle", "shadow", "opacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().focusedFontStyle().shadow().opacity(v));
            case "prefix-focused-font-shadow-stack-count" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "focusedFontStyle", "shadow", "stackCount"), v -> parseUnsignedInteger(v, 1, 100), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().focusedFontStyle().shadow().stackCount(v));
            case "prefix-focused-font-shadow-horizontal-offset" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "focusedFontStyle", "shadow", "horizontalOffset"), v -> parseDouble(v, true, -100, 100), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().focusedFontStyle().shadow().horizontalOffset(v));
            case "prefix-focused-font-shadow-vertical-offset" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixFontStyle", "focusedFontStyle", "shadow", "verticalOffset"), v -> parseDouble(v, true, -100, 100), v -> hintMeshBuilder.style(viewportFilter).prefixFontStyle().focusedFontStyle().shadow().verticalOffset(v));
            // Style: box
            case "box-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "boxHexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).boxHexColor(v));
            case "box-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "boxOpacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).boxOpacity(v));
            case "box-border-thickness" -> ModePropertyHandler.of(prefix.append("styleByFilter", "boxBorderThickness"), v -> parseDouble(v, true, 0, 10_000), v -> hintMeshBuilder.style(viewportFilter).boxBorderThickness(v));
            case "box-border-length" -> ModePropertyHandler.of(prefix.append("styleByFilter", "boxBorderLength"), v -> parseDouble(v, true, 0, 10_000), v -> hintMeshBuilder.style(viewportFilter).boxBorderLength(v));
            case "box-border-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "boxBorderHexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).boxBorderHexColor(v));
            case "box-border-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "boxBorderOpacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).boxBorderOpacity(v));
            case "box-border-radius" -> ModePropertyHandler.of(prefix.append("styleByFilter", "boxBorderRadius"), v -> parseDouble(v, true, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).boxBorderRadius(v));
            case "box-shadow-blur-radius" -> ModePropertyHandler.of(prefix.append("styleByFilter", "boxShadow", "blurRadius"), v -> parseDouble(v, true, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).boxShadow().blurRadius(v));
            case "box-shadow-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "boxShadow", "hexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).boxShadow().hexColor(v));
            case "box-shadow-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "boxShadow", "opacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).boxShadow().opacity(v));
            case "box-shadow-stack-count" -> ModePropertyHandler.of(prefix.append("styleByFilter", "boxShadow", "stackCount"), v -> parseUnsignedInteger(v, 1, 100), v -> hintMeshBuilder.style(viewportFilter).boxShadow().stackCount(v));
            case "box-shadow-horizontal-offset" -> ModePropertyHandler.of(prefix.append("styleByFilter", "boxShadow", "horizontalOffset"), v -> parseDouble(v, true, -100, 100), v -> hintMeshBuilder.style(viewportFilter).boxShadow().horizontalOffset(v));
            case "box-shadow-vertical-offset" -> ModePropertyHandler.of(prefix.append("styleByFilter", "boxShadow", "verticalOffset"), v -> parseDouble(v, true, -100, 100), v -> hintMeshBuilder.style(viewportFilter).boxShadow().verticalOffset(v));
            // Style: prefix box
            case "prefix-box-enabled" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixBoxEnabled"), v -> Boolean.parseBoolean(v), v -> hintMeshBuilder.style(viewportFilter).prefixBoxEnabled(v));
            case "prefix-box-border-thickness" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixBoxBorderThickness"), v -> parseDouble(v, true, 0, 10_000), v -> { if (hintMeshBuilder.style(viewportFilter).prefixBoxEnabled() == null) hintMeshBuilder.style(viewportFilter).prefixBoxEnabled(true); hintMeshBuilder.style(viewportFilter).prefixBoxBorderThickness(v); });
            case "prefix-box-border-length" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixBoxBorderLength"), v -> parseDouble(v, true, 0, 10_000), v -> { if (hintMeshBuilder.style(viewportFilter).prefixBoxEnabled() == null) hintMeshBuilder.style(viewportFilter).prefixBoxEnabled(true); hintMeshBuilder.style(viewportFilter).prefixBoxBorderLength(v); });
            case "prefix-box-border-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixBoxBorderHexColor"), v -> checkColorFormat(v), v -> { if (hintMeshBuilder.style(viewportFilter).prefixBoxEnabled() == null) hintMeshBuilder.style(viewportFilter).prefixBoxEnabled(true); hintMeshBuilder.style(viewportFilter).prefixBoxBorderHexColor(v); });
            case "prefix-box-border-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "prefixBoxBorderOpacity"), v -> parseDouble(v, true, 0, 1), v -> { if (hintMeshBuilder.style(viewportFilter).prefixBoxEnabled() == null) hintMeshBuilder.style(viewportFilter).prefixBoxEnabled(true); hintMeshBuilder.style(viewportFilter).prefixBoxBorderOpacity(v); });
            // Style: box dimensions
            case "box-width-percent" -> ModePropertyHandler.of(prefix.append("styleByFilter", "boxWidthPercent"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).boxWidthPercent(v));
            case "box-height-percent" -> ModePropertyHandler.of(prefix.append("styleByFilter", "boxHeightPercent"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).boxHeightPercent(v));
            case "cell-horizontal-padding", "box-horizontal-padding" -> ModePropertyHandler.of(prefix.append("styleByFilter", "cellHorizontalPadding"), v -> parseDouble(v, true, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).cellHorizontalPadding(v));
            case "cell-vertical-padding", "box-vertical-padding" -> ModePropertyHandler.of(prefix.append("styleByFilter", "cellVerticalPadding"), v -> parseDouble(v, true, 0, 1000), v -> hintMeshBuilder.style(viewportFilter).cellVerticalPadding(v));
            // Style: subgrid
            case "subgrid-row-count" -> ModePropertyHandler.of(prefix.append("styleByFilter", "subgridRowCount"), v -> parseUnsignedInteger(v, 1, 1_000), v -> hintMeshBuilder.style(viewportFilter).subgridRowCount(v));
            case "subgrid-column-count" -> ModePropertyHandler.of(prefix.append("styleByFilter", "subgridColumnCount"), v -> parseUnsignedInteger(v, 1, 1_000), v -> hintMeshBuilder.style(viewportFilter).subgridColumnCount(v));
            case "subgrid-border-thickness" -> ModePropertyHandler.of(prefix.append("styleByFilter", "subgridBorderThickness"), v -> parseDouble(v, true, 0, 10_000), v -> hintMeshBuilder.style(viewportFilter).subgridBorderThickness(v));
            case "subgrid-border-length" -> ModePropertyHandler.of(prefix.append("styleByFilter", "subgridBorderLength"), v -> parseDouble(v, true, 0, 10_000), v -> hintMeshBuilder.style(viewportFilter).subgridBorderLength(v));
            case "subgrid-border-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "subgridBorderHexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).subgridBorderHexColor(v));
            case "subgrid-border-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "subgridBorderOpacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).subgridBorderOpacity(v));
            // Style: animation & background
            case "transition-animation-enabled" -> ModePropertyHandler.of(prefix.append("styleByFilter", "transitionAnimationEnabled"), v -> Boolean.parseBoolean(v), v -> hintMeshBuilder.style(viewportFilter).transitionAnimationEnabled(v));
            case "transition-animation-duration-millis" -> ModePropertyHandler.of(prefix.append("styleByFilter", "transitionAnimationDuration"), v -> parseDuration(v), v -> hintMeshBuilder.style(viewportFilter).transitionAnimationDuration(v));
            case "background-color" -> ModePropertyHandler.of(prefix.append("styleByFilter", "backgroundHexColor"), v -> checkColorFormat(v), v -> hintMeshBuilder.style(viewportFilter).backgroundHexColor(v));
            case "background-opacity" -> ModePropertyHandler.of(prefix.append("styleByFilter", "backgroundOpacity"), v -> parseDouble(v, true, 0, 1), v -> hintMeshBuilder.style(viewportFilter).backgroundOpacity(v));
            // @formatter:on
            default -> null;
        };
    }

    private static String checkColorFormat(String propertyValue) {
        if (!propertyValue.matches("^#?([a-fA-F0-9]{6})$"))
            throw new IllegalArgumentException(
                    "Invalid color " + propertyValue +
                    ": a color should be in the #FFFFFF format");
        return propertyValue;
    }


    private record SplitComboProperty(String defaultValue,
                                         List<ComboPropertyValue> comboValues) {
        record ComboPropertyValue(String comboString, String remapString,
                                  String valueString) {}
    }

    /**
     * Splits a property value on top-level {@code |} (brace-depth-aware), then
     * classifies each segment as either a default value or a combo-triggered
     * value (contains {@code ->}).
     *
     * @return {@code null} if the value has no combo syntax.
     */
    private static SplitComboProperty splitComboProperties(String value) {
        int segmentBeginIndex = 0;
        int braceDepth = 0;
        List<String> segments = new ArrayList<>();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '{')
                braceDepth++;
            else if (c == '}')
                braceDepth--;
            else if (c == '|' && braceDepth == 0) {
                segments.add(value.substring(segmentBeginIndex, i).strip());
                segmentBeginIndex = i + 1;
            }
        }
        segments.add(value.substring(segmentBeginIndex).strip());
        boolean hasComboProperty = segments.stream().anyMatch(s -> s.contains("->"));
        if (!hasComboProperty)
            return null;
        String defaultValue = null;
        List<SplitComboProperty.ComboPropertyValue> comboValues = new ArrayList<>();
        for (String segment : segments) {
            if (segment.contains("->")) {
                String[] parts = segment.split("\\s*->\\s*", 3);
                if (parts.length == 3)
                    comboValues.add(new SplitComboProperty.ComboPropertyValue(
                            parts[0].strip(), parts[1].strip(), parts[2].strip()));
                else
                    comboValues.add(new SplitComboProperty.ComboPropertyValue(
                            parts[0].strip(), null, parts[1].strip()));
            }
            else {
                if (defaultValue != null)
                    throw new IllegalArgumentException(
                            "Multiple default values in combo property: " + value);
                defaultValue = segment;
            }
        }
        return new SplitComboProperty(defaultValue, comboValues);
    }

    /**
     * Tries to parse {@code propertyValue} as a combo-triggered property.
     * If it contains {@code | ... -> ...} syntax, the default
     * value is passed to {@code modeBuilderSetter}, each combo produces
     * a {@link Command.MutateMode} in {@code mutateModeCommandMap}, and
     * {@code true} is returned. Otherwise returns {@code false} and the
     * caller should parse normally.
     */
    private static boolean tryParseComboProperty(
            String propertyValue,
            String modeName,
            ModePropertyPath propertyPath,
            Function<String, Object> valueParser,
            Consumer<String> modeBuilderSetter,
            Map<Combo, List<Command>> mutateModeCommandMap,
            ComboMoveDuration defaultComboMoveDuration,
            Map<String, KeyAlias> keyAliases,
            Map<String, AppAlias> appAliases,
            KeyResolver keyResolver) {
        SplitComboProperty splitComboProperty = splitComboProperties(propertyValue);
        if (splitComboProperty == null)
            return false;
        if (splitComboProperty.defaultValue() != null)
            modeBuilderSetter.accept(splitComboProperty.defaultValue());
        String label = modeName + "." + String.join(".", propertyPath.fieldNames());
        for (SplitComboProperty.ComboPropertyValue comboPropertyValue : splitComboProperty.comboValues()) {
            List<Combo> combos = parseCombos(comboPropertyValue.comboString(), label,
                    defaultComboMoveDuration, keyAliases, appAliases, keyResolver);
            for (Combo combo : combos) {
                Object parsedValue;
                Set<String> aliasNames = combo.sequence().aliasNames();
                List<String> valueTokens = List.of(
                        comboPropertyValue.valueString().split(" "));
                if (valueTokens.stream().anyMatch(aliasNames::contains)) {
                    Map<String, AliasRemap<String>> remap =
                            comboPropertyValue.remapString() != null ?
                                    AliasRemap.stringValues(
                                            comboPropertyValue.remapString(),
                                            keyAliases, keyResolver,
                                            "mutation") :
                                    Map.of();
                    parsedValue = new UnresolvedAliasComboPropertyValue(
                            valueTokens.stream()
                                       .filter(aliasNames::contains)
                                       .toList(),
                            remap);
                }
                else {
                    parsedValue = valueParser.apply(comboPropertyValue.valueString());
                }
                Command command = new Command.MutateMode(modeName, propertyPath,
                        parsedValue, combo);
                mutateModeCommandMap
                        .computeIfAbsent(combo, c -> new ArrayList<>())
                        .add(command);
            }
        }
        return true;
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
                                           Map<String, KeyAlias> keyAliases,
                                           KeyResolver keyResolver) {
        KeyAlias alias = keyAliases.get(propertyValue);
        if (alias != null)
            return List.copyOf(alias.keys());
        String[] split = propertyValue.split("\\s+");
        List<Key> hintKeys = Arrays.stream(split).map(keyResolver::resolve).toList();
        if (hintKeys.size() <= 1)
            // Even 1 key is not enough because we use fixed-length hints.
            throw new IllegalArgumentException(
                    "Invalid hint keys " + propertyValue +
                    ": at least two keys are required");
        return hintKeys;
    }

    private static HintMeshType.HintMeshTypeType parseHintMeshTypeType(String propertyKey,
                                                                       String propertyValue) {
        return switch (propertyValue) {
            case "grid" -> HintMeshType.HintMeshTypeType.GRID;
            case "position-history" ->
                    HintMeshType.HintMeshTypeType.POSITION_HISTORY;
            case "ui" -> HintMeshType.HintMeshTypeType.UI;
            default -> throw new IllegalArgumentException(
                    "Invalid property value in " + propertyKey + "=" + propertyValue +
                    ": type should be one of " +
                    List.of("grid", "position-history", "ui"));
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

    private static void extendVelocity(
            VelocityConfiguration.VelocityConfigurationBuilder builder,
            VelocityConfiguration.VelocityConfigurationBuilder parent) {
        if (builder.initialVelocity() == null)
            builder.initialVelocity(parent.initialVelocity());
        if (builder.maxVelocity() == null)
            builder.maxVelocity(parent.maxVelocity());
        if (builder.acceleration() == null)
            builder.acceleration(parent.acceleration());
        if (builder.accelerationEasing() == null)
            builder.accelerationEasing(parent.accelerationEasing());
        if (builder.deceleration() == null)
            builder.deceleration(parent.deceleration());
    }

    private static Easing parseEasing(String propertyValue) {
        return switch (propertyValue.trim()) {
            case "smoothstep" -> new Easing.Smoothstep();
            case "smootherstep" -> new Easing.Smootherstep();
            case "logarithmic" -> new Easing.Logarithmic();
            case "exponential" -> new Easing.Exponential();
            default -> new Easing.Polynomial(Double.parseDouble(propertyValue));
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
                                   String label,
                                   ComboMoveDuration defaultComboMoveDuration,
                                   Map<String, KeyAlias> keyAliases,
                                   Map<String, AppAlias> appAliases,
                                   KeyResolver keyResolver) {
        List<Combo> combos =
                parseCombos(multiComboString, label, defaultComboMoveDuration,
                        keyAliases, appAliases, keyResolver);
        for (Combo combo : combos)
            commandsByCombo.computeIfAbsent(combo, combo1 -> new ArrayList<>())
                           .add(command);
    }

    private static List<Combo> parseCombos(String multiComboString,
                                           String label,
                                           ComboMoveDuration defaultComboMoveDuration,
                                           Map<String, KeyAlias> keyAliases,
                                           Map<String, AppAlias> appAliases,
                                           KeyResolver keyResolver) {
        return Combo.multiCombo(label, multiComboString, defaultComboMoveDuration,
                keyAliases, appAliases, keyResolver);
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
                    extendVelocity(builder.velocity(), parent.velocity());
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
                    extendVelocity(builder.velocity(), parent.velocity());
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
                    builder.extend(parent);
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
                    if (builder.animationEnabled() == null)
                        builder.animationEnabled(parent.animationEnabled());
                    if (builder.animationEasing() == null)
                        builder.animationEasing(parent.animationEasing());
                    if (builder.animationDurationMillis() == null)
                        builder.animationDurationMillis(parent.animationDurationMillis());
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
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::prefixInBackground,
                            childStyleByFilter, filter))
                        childStyle.prefixInBackground(
                                parentStyle.prefixInBackground());
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
                            HintMeshStyleBuilder::boxBorderRadius,
                            childStyleByFilter, filter))
                        childStyle.boxBorderRadius(parentStyle.boxBorderRadius());
                    childStyle.boxShadow().extend(parentStyle.boxShadow());
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
                            HintMeshStyleBuilder::cellHorizontalPadding,
                            childStyleByFilter, filter))
                        childStyle.cellHorizontalPadding(parentStyle.cellHorizontalPadding());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::cellVerticalPadding,
                            childStyleByFilter, filter))
                        childStyle.cellVerticalPadding(parentStyle.cellVerticalPadding());
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
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::backgroundHexColor,
                            childStyleByFilter, filter))
                        childStyle.backgroundHexColor(
                                parentStyle.backgroundHexColor());
                    if (!childDoesNotNeedParentProperty(
                            HintMeshStyleBuilder::backgroundOpacity,
                            childStyleByFilter, filter))
                        childStyle.backgroundOpacity(
                                parentStyle.backgroundOpacity());
                }
                // Font-style cascade: for each viewport filter, resolve all font
                // properties (child.X, child.any, parent.X, parent.any).
                for (ViewportFilter filter : builder.styleByFilter().map().keySet()
                        .stream()
                        .sorted(Comparator.comparing(
                                f -> f instanceof AnyViewportFilter ? 1 : 0))
                        .toList()) {
                    HintMeshStyleBuilder childX = builder.styleByFilter().map().get(filter);
                    HintMeshStyleBuilder childAny = builder.styleByFilter().map()
                            .get(AnyViewportFilter.ANY_VIEWPORT_FILTER);
                    HintMeshStyleBuilder parentX = parent == null ? null :
                            parent.styleByFilter().map().get(filter);
                    HintMeshStyleBuilder parentAny = parent == null ? null :
                            parent.styleByFilter().map()
                                    .get(AnyViewportFilter.ANY_VIEWPORT_FILTER);
                    cascadeAllFontStyles(childX, childAny, parentX, parentAny);
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

    private static void cascadeFontStyle(FontStyle.FontStyleBuilder target,
            FontStyle.FontStyleBuilder... sources) {
        String name = null, hexColor = null, outlineHexColor = null;
        FontWeight weight = null;
        Double size = null, opacity = null, outlineThickness = null, outlineOpacity = null;
        Double shadowBlur = null, shadowOpacity = null, shadowHOff = null, shadowVOff = null;
        String shadowHexColor = null;
        Integer shadowStackCount = null;
        for (FontStyle.FontStyleBuilder source : sources) {
            if (source == null) continue;
            if (name == null) name = source.name();
            if (weight == null) weight = source.weight();
            if (size == null) size = source.size();
            if (hexColor == null) hexColor = source.hexColor();
            if (opacity == null) opacity = source.opacity();
            if (outlineThickness == null) outlineThickness = source.outlineThickness();
            if (outlineHexColor == null) outlineHexColor = source.outlineHexColor();
            if (outlineOpacity == null) outlineOpacity = source.outlineOpacity();
            Shadow.ShadowBuilder sh = source.shadow();
            if (shadowBlur == null) shadowBlur = sh.blurRadius();
            if (shadowHexColor == null) shadowHexColor = sh.hexColor();
            if (shadowOpacity == null) shadowOpacity = sh.opacity();
            if (shadowHOff == null) shadowHOff = sh.horizontalOffset();
            if (shadowVOff == null) shadowVOff = sh.verticalOffset();
            if (shadowStackCount == null) shadowStackCount = sh.stackCount();
        }
        target.name(name); target.weight(weight); target.size(size);
        target.hexColor(hexColor); target.opacity(opacity);
        target.outlineThickness(outlineThickness); target.outlineHexColor(outlineHexColor);
        target.outlineOpacity(outlineOpacity);
        target.shadow().blurRadius(shadowBlur); target.shadow().hexColor(shadowHexColor);
        target.shadow().opacity(shadowOpacity); target.shadow().horizontalOffset(shadowHOff);
        target.shadow().verticalOffset(shadowVOff); target.shadow().stackCount(shadowStackCount);
    }

    private static void cascadeAllFontStyles(
            HintMeshStyleBuilder childX, HintMeshStyleBuilder childAny,
            HintMeshStyleBuilder parentX, HintMeshStyleBuilder parentAny) {
        // fontStyle sources
        FontStyle.FontStyleBuilder cxSel = childX.fontStyle().selectedFontStyle();
        FontStyle.FontStyleBuilder caSel = childAny == null ? null : childAny.fontStyle().selectedFontStyle();
        FontStyle.FontStyleBuilder pxSel = parentX == null ? null : parentX.fontStyle().selectedFontStyle();
        FontStyle.FontStyleBuilder paSel = parentAny == null ? null : parentAny.fontStyle().selectedFontStyle();
        FontStyle.FontStyleBuilder cxDef = childX.fontStyle().defaultFontStyle();
        FontStyle.FontStyleBuilder caDef = childAny == null ? null : childAny.fontStyle().defaultFontStyle();
        FontStyle.FontStyleBuilder pxDef = parentX == null ? null : parentX.fontStyle().defaultFontStyle();
        FontStyle.FontStyleBuilder paDef = parentAny == null ? null : parentAny.fontStyle().defaultFontStyle();
        FontStyle.FontStyleBuilder cxFoc = childX.fontStyle().focusedFontStyle();
        FontStyle.FontStyleBuilder caFoc = childAny == null ? null : childAny.fontStyle().focusedFontStyle();
        FontStyle.FontStyleBuilder pxFoc = parentX == null ? null : parentX.fontStyle().focusedFontStyle();
        FontStyle.FontStyleBuilder paFoc = parentAny == null ? null : parentAny.fontStyle().focusedFontStyle();
        // prefixFontStyle sources
        FontStyle.FontStyleBuilder cxPSel = childX.prefixFontStyle().selectedFontStyle();
        FontStyle.FontStyleBuilder caPSel = childAny == null ? null : childAny.prefixFontStyle().selectedFontStyle();
        FontStyle.FontStyleBuilder pxPSel = parentX == null ? null : parentX.prefixFontStyle().selectedFontStyle();
        FontStyle.FontStyleBuilder paPSel = parentAny == null ? null : parentAny.prefixFontStyle().selectedFontStyle();
        FontStyle.FontStyleBuilder cxPDef = childX.prefixFontStyle().defaultFontStyle();
        FontStyle.FontStyleBuilder caPDef = childAny == null ? null : childAny.prefixFontStyle().defaultFontStyle();
        FontStyle.FontStyleBuilder pxPDef = parentX == null ? null : parentX.prefixFontStyle().defaultFontStyle();
        FontStyle.FontStyleBuilder paPDef = parentAny == null ? null : parentAny.prefixFontStyle().defaultFontStyle();
        FontStyle.FontStyleBuilder cxPFoc = childX.prefixFontStyle().focusedFontStyle();
        FontStyle.FontStyleBuilder caPFoc = childAny == null ? null : childAny.prefixFontStyle().focusedFontStyle();
        FontStyle.FontStyleBuilder pxPFoc = parentX == null ? null : parentX.prefixFontStyle().focusedFontStyle();
        FontStyle.FontStyleBuilder paPFoc = parentAny == null ? null : parentAny.prefixFontStyle().focusedFontStyle();
        // Cascade prefix font styles, then font styles.
        // Child sources are exhausted before parent sources.
        // prefix-selected: child(prefix-selected, prefix-default, selected, default),
        //                   parent(prefix-selected, prefix-default, selected, default)
        cascadeFontStyle(cxPSel,
                cxPSel, caPSel, cxPDef, caPDef, cxSel, caSel, cxDef, caDef,
                pxPSel, paPSel, pxPDef, paPDef, pxSel, paSel, pxDef, paDef);
        // prefix-focused: child(prefix-focused, prefix-default, focused, default),
        //                  parent(prefix-focused, prefix-default, focused, default)
        cascadeFontStyle(cxPFoc,
                cxPFoc, caPFoc, cxPDef, caPDef, cxFoc, caFoc, cxDef, caDef,
                pxPFoc, paPFoc, pxPDef, paPDef, pxFoc, paFoc, pxDef, paDef);
        // prefix-default: child(prefix-default, default), parent(prefix-default, default)
        cascadeFontStyle(cxPDef,
                cxPDef, caPDef, cxDef, caDef,
                pxPDef, paPDef, pxDef, paDef);
        // prefix spacingPercent: child(prefix, font), parent(prefix, font)
        Double prefixSpacing = childX.prefixFontStyle().spacingPercent();
        if (prefixSpacing == null && childAny != null) prefixSpacing = childAny.prefixFontStyle().spacingPercent();
        if (prefixSpacing == null) prefixSpacing = childX.fontStyle().spacingPercent();
        if (prefixSpacing == null && childAny != null) prefixSpacing = childAny.fontStyle().spacingPercent();
        if (prefixSpacing == null && parentX != null) prefixSpacing = parentX.prefixFontStyle().spacingPercent();
        if (prefixSpacing == null && parentAny != null) prefixSpacing = parentAny.prefixFontStyle().spacingPercent();
        if (prefixSpacing == null && parentX != null) prefixSpacing = parentX.fontStyle().spacingPercent();
        if (prefixSpacing == null && parentAny != null) prefixSpacing = parentAny.fontStyle().spacingPercent();
        childX.prefixFontStyle().spacingPercent(prefixSpacing);
        // fontStyle selected: child(selected, default), parent(selected, default)
        cascadeFontStyle(cxSel,
                cxSel, caSel, cxDef, caDef,
                pxSel, paSel, pxDef, paDef);
        // fontStyle focused: child(focused, default), parent(focused, default)
        cascadeFontStyle(cxFoc,
                cxFoc, caFoc, cxDef, caDef,
                pxFoc, paFoc, pxDef, paDef);
        // fontStyle default: child, parent
        cascadeFontStyle(cxDef, cxDef, caDef, pxDef, paDef);
        // fontStyle spacingPercent: child, parent
        Double fontSpacing = childX.fontStyle().spacingPercent();
        if (fontSpacing == null && childAny != null) fontSpacing = childAny.fontStyle().spacingPercent();
        if (fontSpacing == null && parentX != null) fontSpacing = parentX.fontStyle().spacingPercent();
        if (fontSpacing == null && parentAny != null) fontSpacing = parentAny.fontStyle().spacingPercent();
        childX.fontStyle().spacingPercent(fontSpacing);
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
        Property<Map<Combo, List<Command>>> breakMacro;
        Property<Map<Combo, List<Command>>> macro;
        Property<Map<Combo, List<Command>>> mutateMode;
        Property<Map<Combo, List<Command>>> noop;

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
            breakMacro = new ComboMapProperty("break-macro", modeName, propertyByKey);
            macro = new ComboMapProperty("macro", modeName, propertyByKey);
            mutateMode = new ComboMapProperty("mutate-mode", modeName, propertyByKey);
            noop = new ComboMapProperty("noop", modeName, propertyByKey);
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
                Set<Command> childCommands = new HashSet<>();
                for (List<Command> commands : builder.values())
                    childCommands.addAll(commands);
                for (Map.Entry<Combo, List<Command>> parentEntry : parent.entrySet()) {
                    // mode1.start-move.up=x
                    // mode2.start-move=mode1.start-move
                    // mode2.start-move.up=y
                    if (builder.containsKey(parentEntry.getKey()))
                        continue;
                    if (parentEntry.getValue().stream().anyMatch(childCommands::contains))
                        continue;
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
            add(commandsByCombo, breakMacro.builder);
            add(commandsByCombo, macro.builder);
            add(commandsByCombo, mutateMode.builder);
            add(commandsByCombo, noop.builder);
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
        final Map<Combo, List<Command>> mutateModeCommands = new HashMap<>();
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

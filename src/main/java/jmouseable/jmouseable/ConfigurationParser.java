package jmouseable.jmouseable;

import jmouseable.jmouseable.ComboMap.ComboMapBuilder;
import jmouseable.jmouseable.GridArea.GridAreaType;
import jmouseable.jmouseable.HintGridArea.HintGridAreaType;
import jmouseable.jmouseable.Mode.ModeBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static jmouseable.jmouseable.Command.*;

public class ConfigurationParser {

    private static final ModeBuilder defaultMode;

    static {
        defaultMode = defaultMode();
    }

    private static ModeBuilder defaultMode() {
        ModeBuilder builder = new ModeBuilder(null);
        builder.pushModeToHistoryStack(false);
        builder.mouse().initialVelocity(200).maxVelocity(750).acceleration(1000);
        builder.wheel().initialVelocity(1000).maxVelocity(1000).acceleration(500);
        builder.grid()
               .synchronization(Synchronization.MOUSE_AND_GRID_CENTER_UNSYNCHRONIZED)
               .rowCount(2)
               .columnCount(2)
               .lineVisible(false)
               .lineHexColor("#FF0000")
               .lineThickness(1);
        GridArea.GridAreaBuilder gridAreaBuilder = builder.grid().area();
        gridAreaBuilder.type(GridAreaType.ACTIVE_SCREEN)
                       .widthPercent(1)
                       .heightPercent(1);
        builder.hintMesh()
               .enabled(false)
               .selectionKeys(IntStream.rangeClosed('a', 'z')
                                       .mapToObj(c -> String.valueOf((char) c))
                                       .map(Key::ofName)
                                       .toList())
               .fontName("Arial")
               .fontSize(20)
               .fontHexColor("#FFFFFF")
               .selectedPrefixFontHexColor("#8FA6C4")
               .boxHexColor("#204E8A")
               .saveMousePositionAfterSelection(false);
        HintMeshType.HintMeshTypeBuilder hintMeshTypeBuilder = builder.hintMesh().type();
        hintMeshTypeBuilder.type(HintMeshType.HintMeshTypeType.GRID)
                           .gridRowCount(20)
                           .gridColumnCount(20);
        HintGridArea.HintGridAreaBuilder hintGridAreaBuilder =
                builder.hintMesh().type().gridArea();
        hintGridAreaBuilder.type(HintGridAreaType.ACTIVE_SCREEN)
                           .widthPercent(1)
                           .heightPercent(1)
                           .activeScreenHintGridAreaCenter(
                                   ActiveScreenHintGridAreaCenter.SCREEN_CENTER);
        builder.timeout().enabled(false);
        builder.indicator()
               .enabled(false)
               .size(12)
               .idleHexColor("#FF0000")
               .moveHexColor("#FF0000")
               .wheelHexColor("#FFFF00")
               .mousePressHexColor("#00FF00")
               .nonComboKeyPressHexColor("#0000FF");
        builder.hideCursor().enabled(false);
        return builder;
    }

    public static Configuration parse(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        ComboMoveDuration defaultComboMoveDuration =
                new ComboMoveDuration(Duration.ZERO, Duration.ofMillis(150));
        KeyboardLayout keyboardLayout = null;
        int maxMousePositionHistorySize = 16;
        Pattern modeKeyPattern = Pattern.compile("([^.]+-mode)\\.([^.]+)(\\.([^.]+))?");
        Map<String, ModeBuilder> modeByName = new HashMap<>();
        Set<String> modeNameReferences = new HashSet<>();
        Map<String, Set<String>> childrenModeNamesByParentMode = new HashMap<>();
        Map<String, Set<String>> referencedModeNamesByReferencerMode = new HashMap<>();
        Set<String> nonRootModeNames = new HashSet<>();
        Pattern linePattern = Pattern.compile("(.+?)=(.+)");
        for (String line : lines) {
            if (line.startsWith("#"))
                continue;
            Matcher lineMatcher = linePattern.matcher(line);
            if (!lineMatcher.matches())
                throw new IllegalArgumentException("Invalid property " + line);
            String propertyKey = lineMatcher.group(1);
            String propertyValue = lineMatcher.group(2);
            if (propertyKey.isBlank())
                throw new IllegalArgumentException(
                        "Invalid property " + line + ": property key cannot be blank");
            if (propertyValue.isBlank())
                throw new IllegalArgumentException(
                        "Invalid property " + line + ": property value cannot be blank");
            if (propertyKey.equals("default-combo-move-duration-millis")) {
                defaultComboMoveDuration = parseComboMoveDuration(propertyKey, propertyValue);
                continue;
            }
            else if (propertyKey.equals("keyboard-layout")) {
                keyboardLayout = KeyboardLayout.keyboardLayoutByName.get(propertyValue);
                if (keyboardLayout == null)
                    throw new IllegalArgumentException(
                            "Invalid keyboard layout: " + propertyValue +
                            ", available keyboard layouts: " +
                            KeyboardLayout.keyboardLayoutByName.keySet());
                continue;
            }
            else if (propertyKey.equals("max-mouse-position-history-size")) {
                maxMousePositionHistorySize =
                        parseUnsignedInteger(propertyKey, propertyValue, 1, 100);
                continue;
            }
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
            ModeBuilder mode = modeByName.computeIfAbsent(modeName, ModeBuilder::new);
            String group2 = keyMatcher.group(2);
            switch (group2) {
                case "push-mode-to-history-stack" ->
                        mode.pushModeToHistoryStack(Boolean.parseBoolean(propertyValue));
                case "mouse" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid mouse property key: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        case "initial-velocity" -> mode.mouse()
                                                       .initialVelocity(
                                                               Double.parseDouble(
                                                                       propertyValue));
                        case "max-velocity" -> mode.mouse()
                                                   .maxVelocity(Double.parseDouble(
                                                           propertyValue));
                        case "acceleration" -> mode.mouse()
                                                   .acceleration(Double.parseDouble(
                                                           propertyValue));
                        default -> throw new IllegalArgumentException(
                                "Invalid mouse property key: " + propertyKey);
                    }
                }
                case "wheel" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid wheel property key: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        case "acceleration" -> mode.wheel()
                                                   .initialVelocity(Double.parseDouble(
                                                           propertyValue));
                        case "initial-velocity" -> mode.wheel()
                                                       .maxVelocity(Double.parseDouble(
                                                               propertyValue));
                        case "max-velocity" -> mode.wheel()
                                                   .acceleration(Double.parseDouble(
                                                           propertyValue));
                        default -> throw new IllegalArgumentException(
                                "Invalid wheel property key: " + propertyKey);
                    }
                }
                case "grid" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid grid property key: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        case "area" -> mode.grid()
                                           .area()
                                           .type(parseGridAreaType(propertyKey,
                                                   propertyValue));
                        case "area-width-percent" -> mode.grid()
                                                         .area()
                                                         .widthPercent(
                                                                 parseNonZeroPercent(
                                                                         propertyKey,
                                                                         propertyValue));
                        case "area-height-percent" -> mode.grid()
                                                          .area()
                                                          .heightPercent(
                                                                  parseNonZeroPercent(
                                                                          propertyKey,
                                                                          propertyValue));
                        case "synchronization" -> mode.grid().synchronization(
                                                              parseSynchronization(
                                                                      propertyKey,
                                                                      propertyValue));
                        case "row-count" -> mode.grid()
                                                     .rowCount(parseUnsignedInteger(
                                                             propertyKey,
                                                             propertyValue, 1, 50));
                        case "column-count" -> mode.grid()
                                                   .columnCount(parseUnsignedInteger(
                                                           propertyKey, propertyValue, 1,
                                                           50));
                        case "line-visible" ->
                                mode.grid().lineVisible(Boolean.parseBoolean(propertyValue));
                        case "line-color" ->
                                mode.grid().lineHexColor(checkColorFormat(propertyKey,
                                        propertyValue));
                        case "line-thickness" -> mode.grid()
                                                     .lineThickness(
                                                             Integer.parseUnsignedInt(
                                                                     propertyValue));
                        default -> throw new IllegalArgumentException(
                                "Invalid grid property key: " + propertyKey);
                    }
                }
                case "hint" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid hint property key: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        case "enabled" -> mode.hintMesh()
                                              .enabled(Boolean.parseBoolean(
                                                      propertyValue));
                        case "type" -> mode.hintMesh()
                                           .type()
                                           .type(parseHintMeshTypeType(propertyKey,
                                                   propertyValue));
                        case "grid-area" -> mode.hintMesh()
                                                .type()
                                                .gridArea()
                                                .type(parseHintGridAreaType(propertyKey,
                                                        propertyValue));
                        case "grid-area-width-percent" -> mode.hintMesh()
                                                              .type()
                                                              .gridArea()
                                                              .widthPercent(
                                                                      parseNonZeroPercent(
                                                                              propertyKey,
                                                                              propertyValue));
                        case "grid-area-height-percent" -> mode.hintMesh()
                                                              .type()
                                                              .gridArea()
                                                              .heightPercent(
                                                                      parseNonZeroPercent(
                                                                              propertyKey,
                                                                              propertyValue));
                        case "active-screen-grid-area-center" -> mode.hintMesh()
                                                                     .type()
                                                                     .gridArea()
                                                                     .activeScreenHintGridAreaCenter(
                                                                             parseActiveScreenHintGridAreaCenter(
                                                                                     propertyKey,
                                                                                     propertyValue));
                        case "grid-row-count" -> mode.hintMesh()
                                                     .type()
                                                     .gridRowCount(parseUnsignedInteger(
                                                             propertyKey, propertyValue,
                                                             1, 50));
                        case "grid-column-count" -> mode.hintMesh()
                                                        .type()
                                                        .gridColumnCount(
                                                                parseUnsignedInteger(
                                                                        propertyKey,
                                                                        propertyValue, 1,
                                                                        50));
                        case "selection-keys" -> mode.hintMesh()
                                                     .selectionKeys(
                                                             parseHintKeys(propertyKey,
                                                                     propertyValue));
                        case "undo" -> mode.hintMesh().undoKey(Key.ofName(propertyValue));
                        case "font-name" -> mode.hintMesh().fontName(propertyValue);
                        case "font-size" -> mode.hintMesh()
                                                .fontSize(
                                                        parseUnsignedInteger(propertyKey,
                                                                propertyValue, 1, 1000));
                        case "font-color" -> mode.hintMesh()
                                                 .fontHexColor(
                                                         checkColorFormat(propertyKey,
                                                                 propertyValue));
                        case "selected-prefix-font-color" -> mode.hintMesh()
                                                                 .selectedPrefixFontHexColor(
                                                                         checkColorFormat(
                                                                                 propertyKey,
                                                                                 propertyValue));
                        case "box-color" -> mode.hintMesh()
                                                .boxHexColor(
                                                        checkColorFormat(propertyKey,
                                                                propertyValue));
                        case "next-mode-after-selection" -> {
                            String nextModeAfterSelection = propertyValue;
                            modeNameReferences.add(
                                    checkNonExtendModeReference(nextModeAfterSelection));
                            referencedModeNamesByReferencerMode.computeIfAbsent(modeName,
                                                                       modeName_ -> new HashSet<>())
                                                               .add(nextModeAfterSelection);
                            mode.hintMesh().nextModeAfterSelection(nextModeAfterSelection);
                        }
                        case "click-button-after-selection" -> mode.hintMesh()
                                                                        .clickButtonAfterSelection(
                                                                                parseButton(propertyKey,
                                                                                        propertyValue));
                        case "save-mouse-position-after-selection" -> mode.hintMesh()
                                                                               .saveMousePositionAfterSelection(
                                                                                       Boolean.parseBoolean(
                                                                                               propertyValue));
                        default -> throw new IllegalArgumentException(
                                "Invalid hint property key: " + propertyKey);
                    }
                }
                case "to" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid to (mode switch) property key: " + propertyKey);
                    String newModeName = keyMatcher.group(4);
                    modeNameReferences.add(checkNonExtendModeReference(newModeName));
                    referencedModeNamesByReferencerMode.computeIfAbsent(modeName,
                                                               modeName_ -> new HashSet<>())
                                                       .add(newModeName);
                    addCommand(mode.comboMap(), propertyValue,
                            new SwitchMode(newModeName), defaultComboMoveDuration);
                }
                case "extend" -> {
                    String parentModeName = propertyValue;
                    if (parentModeName.equals(modeName))
                        throw new IllegalArgumentException(
                                "Invalid extend property " + propertyKey + "=" +
                                propertyValue + ": a mode cannot extend itself");
                    childrenModeNamesByParentMode.computeIfAbsent(parentModeName,
                            parentModeName_ -> new HashSet<>()).add(modeName);
                    nonRootModeNames.add(modeName);
                    modeNameReferences.add(parentModeName);
                }
                case "timeout" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid timeout property key: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        case "enabled" -> mode.timeout()
                                              .enabled(Boolean.parseBoolean(
                                                      propertyValue));
                        case "idle-duration-millis" ->
                                mode.timeout().idleDuration(parseDuration(propertyValue));
                        case "next-mode" -> {
                            String nextModeName = propertyValue;
                            mode.timeout().nextModeName(nextModeName);
                            modeNameReferences.add(
                                    checkNonExtendModeReference(nextModeName));
                            referencedModeNamesByReferencerMode.computeIfAbsent(modeName,
                                                                       modeName_ -> new HashSet<>())
                                                               .add(nextModeName);
                        }
                        default -> throw new IllegalArgumentException(
                                "Invalid timeout property key: " + propertyKey);
                    }
                }
                case "indicator" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid indicator property key: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        case "enabled" -> mode.indicator()
                                              .enabled(Boolean.parseBoolean(
                                                      propertyValue));
                        case "size" -> mode.indicator()
                                           .size(parseUnsignedInteger(propertyKey,
                                                   propertyValue, 1, 100));
                        case "idle-color" -> mode.indicator()
                                                 .idleHexColor(
                                                         checkColorFormat(propertyKey,
                                                                 propertyValue));
                        case "move-color" -> mode.indicator()
                                                 .moveHexColor(
                                                         checkColorFormat(propertyKey,
                                                                 propertyValue));
                        case "wheel-color" -> mode.indicator()
                                                  .wheelHexColor(checkColorFormat(
                                                          propertyKey, propertyValue));
                        case "mouse-press-color" -> mode.indicator()
                                                        .mousePressHexColor(
                                                                checkColorFormat(
                                                                        propertyKey,
                                                                        propertyValue));
                        case "non-combo-key-press-color" -> mode.indicator()
                                                                .nonComboKeyPressHexColor(
                                                                        checkColorFormat(
                                                                                propertyKey,
                                                                                propertyValue));
                        default -> throw new IllegalArgumentException(
                                "Invalid indicator property key: " + propertyKey);
                    }
                }
                case "hide-cursor" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid hide-cursor property key: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        case "enabled" -> mode.hideCursor()
                                              .enabled(Boolean.parseBoolean(
                                                      propertyValue));
                        case "idle-duration-millis" -> mode.hideCursor()
                                                           .idleDuration(parseDuration(
                                                                   propertyValue));
                        default -> throw new IllegalArgumentException(
                                "Invalid hide-cursor configuration: " + propertyKey);
                    }
                }
                case "start-move" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid start-move property key: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        // @formatter:off
                        case "up" -> addCommand(mode.comboMap(), propertyValue, new StartMoveUp(), defaultComboMoveDuration);
                        case "down" -> addCommand(mode.comboMap(), propertyValue, new StartMoveDown(), defaultComboMoveDuration);
                        case "left" -> addCommand(mode.comboMap(), propertyValue, new StartMoveLeft(), defaultComboMoveDuration);
                        case "right" -> addCommand(mode.comboMap(), propertyValue, new StartMoveRight(), defaultComboMoveDuration);
                        // @formatter:on
                    }
                }
                case "stop-move" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid stop-move property key: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        // @formatter:off
                        case "up" -> addCommand(mode.comboMap(), propertyValue, new StopMoveUp(), defaultComboMoveDuration);
                        case "down" -> addCommand(mode.comboMap(), propertyValue, new StopMoveDown(), defaultComboMoveDuration);
                        case "left" -> addCommand(mode.comboMap(), propertyValue, new StopMoveLeft(), defaultComboMoveDuration);
                        case "right" -> addCommand(mode.comboMap(), propertyValue, new StopMoveRight(), defaultComboMoveDuration);
                        // @formatter:on
                    }
                }
                case "press" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid press property key: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        // @formatter:off
                        case "left" -> addCommand(mode.comboMap(), propertyValue, new PressLeft(), defaultComboMoveDuration);
                        case "middle" -> addCommand(mode.comboMap(), propertyValue, new PressMiddle(), defaultComboMoveDuration);
                        case "right" -> addCommand(mode.comboMap(), propertyValue, new PressRight(), defaultComboMoveDuration);
                        // @formatter:on
                    }
                }
                case "release" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid release property key: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        // @formatter:off
                        case "left" -> addCommand(mode.comboMap(), propertyValue, new ReleaseLeft(), defaultComboMoveDuration);
                        case "middle" -> addCommand(mode.comboMap(), propertyValue, new ReleaseMiddle(), defaultComboMoveDuration);
                        case "right" -> addCommand(mode.comboMap(), propertyValue, new ReleaseRight(), defaultComboMoveDuration);
                        // @formatter:on
                    }
                }
                case "start-wheel" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid start-wheel property key: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        // @formatter:off
                        case "up" -> addCommand(mode.comboMap(), propertyValue, new StartWheelUp(), defaultComboMoveDuration);
                        case "down" -> addCommand(mode.comboMap(), propertyValue, new StartWheelDown(), defaultComboMoveDuration);
                        case "left" -> addCommand(mode.comboMap(), propertyValue, new StartWheelLeft(), defaultComboMoveDuration);
                        case "right" -> addCommand(mode.comboMap(), propertyValue, new StartWheelRight(), defaultComboMoveDuration);
                        // @formatter:on
                    }
                }
                case "stop-wheel" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid stop-wheel property key: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        // @formatter:off
                        case "up" -> addCommand(mode.comboMap(), propertyValue, new StopWheelUp(), defaultComboMoveDuration);
                        case "down" -> addCommand(mode.comboMap(), propertyValue, new StopWheelDown(), defaultComboMoveDuration);
                        case "left" -> addCommand(mode.comboMap(), propertyValue, new StopWheelLeft(), defaultComboMoveDuration);
                        case "right" -> addCommand(mode.comboMap(), propertyValue, new StopWheelRight(), defaultComboMoveDuration);
                        // @formatter:on
                    }
                }
                case "snap" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid snap property key: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        // @formatter:off
                        case "up" -> addCommand(mode.comboMap(), propertyValue, new SnapUp(), defaultComboMoveDuration);
                        case "down" -> addCommand(mode.comboMap(), propertyValue, new SnapDown(), defaultComboMoveDuration);
                        case "left" -> addCommand(mode.comboMap(), propertyValue, new SnapLeft(), defaultComboMoveDuration);
                        case "right" -> addCommand(mode.comboMap(), propertyValue, new SnapRight(), defaultComboMoveDuration);
                        // @formatter:on
                    }
                }
                case "shrink-grid" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid shrink-grid property key: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        // @formatter:off
                        case "up" -> addCommand(mode.comboMap(), propertyValue, new ShrinkGridUp(), defaultComboMoveDuration);
                        case "down" -> addCommand(mode.comboMap(), propertyValue, new ShrinkGridDown(), defaultComboMoveDuration);
                        case "left" -> addCommand(mode.comboMap(), propertyValue, new ShrinkGridLeft(), defaultComboMoveDuration);
                        case "right" -> addCommand(mode.comboMap(), propertyValue, new ShrinkGridRight(), defaultComboMoveDuration);
                        // @formatter:on
                    }
                }
                case "move-grid" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid move-grid property key: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        // @formatter:off
                        case "up" -> addCommand(mode.comboMap(), propertyValue, new MoveGridUp(), defaultComboMoveDuration);
                        case "down" -> addCommand(mode.comboMap(), propertyValue, new MoveGridDown(), defaultComboMoveDuration);
                        case "left" -> addCommand(mode.comboMap(), propertyValue, new MoveGridLeft(), defaultComboMoveDuration);
                        case "right" -> addCommand(mode.comboMap(), propertyValue, new MoveGridRight(), defaultComboMoveDuration);
                        // @formatter:on
                    }
                }
                // @formatter:off
                case "move-to-grid-center" -> addCommand(mode.comboMap(), propertyValue, new MoveToGridCenter(), defaultComboMoveDuration);
                case "save-mouse-position" -> addCommand(mode.comboMap(), propertyValue, new SaveMousePosition(), defaultComboMoveDuration);
                case "clear-mouse-position-history" -> addCommand(mode.comboMap(), propertyValue, new ClearMousePositionHistory(), defaultComboMoveDuration);
                // @formatter:on
                default -> throw new IllegalArgumentException(
                        "Invalid mode property key: " + propertyKey);
            }
        }
        // Verify mode name references are valid.
        for (String modeNameReference : modeNameReferences) {
            if (modeNameReference.equals(Mode.PREVIOUS_MODE_FROM_HISTORY_STACK_IDENTIFIER))
                continue;
            if (!modeByName.containsKey(modeNameReference))
                throw new IllegalStateException(
                        "Definition of mode " + modeNameReference + " is missing");
        }
        Set<String> rootInheritanceModeNames = modeByName.keySet()
                                                         .stream()
                                                         .filter(Predicate.not(
                                                                 nonRootModeNames::contains))
                                                         .collect(Collectors.toSet());
        Set<ModeNode> rootInheritanceNodes = new HashSet<>();
        Set<String> inheritanceNodeNames = new HashSet<>();
        for (String rootModeName : rootInheritanceModeNames)
            rootInheritanceNodes.add(recursivelyBuildInheritanceNode(rootModeName,
                    childrenModeNamesByParentMode, inheritanceNodeNames));
        Map<String, ModeNode> referenceNodeByName = new HashMap<>();
        ModeNode rootReferenceNode = recursivelyBuildReferenceNode(Mode.IDLE_MODE_NAME,
                referencedModeNamesByReferencerMode, referenceNodeByName);
        if (modeByName.size() != 1 || !modeByName.containsKey(Mode.IDLE_MODE_NAME)) {
            for (String modeName : modeByName.keySet()) {
                if (modeName.equals(Mode.IDLE_MODE_NAME))
                    continue;
                if (!referenceNodeByName.containsKey(modeName) && !modeName.startsWith("_"))
                    throw new IllegalStateException("Mode " + modeName +
                                                    " is not referenced anywhere, if this is an abstract mode then its name should start with _");

            }
        }
        for (ModeNode rootInheritanceNode : rootInheritanceNodes)
            recursivelyExtendMode(defaultMode, rootInheritanceNode, modeByName);
        for (ModeBuilder mode : modeByName.values()) {
            if (mode.timeout().enabled() && (mode.timeout().idleDuration() == null ||
                                             mode.timeout().nextModeName() == null))
                throw new IllegalStateException(
                        "Definition of mode timeout for " + mode.name() +
                        " is incomplete");
            if (mode.hideCursor().enabled() && mode.hideCursor().idleDuration() == null)
                throw new IllegalStateException(
                        "Definition of hide cursor for " + mode.name() +
                        " is incomplete");
        }
        Set<Mode> modes = modeByName.values()
                                    .stream()
                                    .map(ModeBuilder::build)
                                    .collect(Collectors.toSet());
        return new Configuration(keyboardLayout, maxMousePositionHistorySize,
                new ModeMap(modes));
    }

    private static String checkNonExtendModeReference(String modeNameReference) {
        if (modeNameReference.startsWith("_"))
            throw new IllegalArgumentException(
                    "Referencing an abstract mode (a mode starting with _) is not allowed: " +
                    modeNameReference);
        return modeNameReference;
    }

    private static void recursivelyExtendMode(ModeBuilder parentMode, ModeNode modeNode,
                                              Map<String, ModeBuilder> modeByName) {
        ModeBuilder mode = modeByName.get(modeNode.modeName);
        extendMode(parentMode, mode);
        for (ModeNode subModeNode : modeNode.subModes)
            recursivelyExtendMode(mode, subModeNode, modeByName);
    }

    private static ModeNode recursivelyBuildInheritanceNode(String modeName,
                                                            Map<String, Set<String>> childrenModeNamesByParentMode,
                                                            Set<String> alreadyBuiltModeNodeNames) {
        if (!alreadyBuiltModeNodeNames.add(modeName))
            throw new IllegalStateException(
                    "Found extend dependency cycle involving mode " + modeName);
        List<ModeNode> subModes = new ArrayList<>();
        Set<String> childrenModeNames = childrenModeNamesByParentMode.get(modeName);
        if (childrenModeNames != null) {
            for (String childModeName : childrenModeNames)
                subModes.add(
                        recursivelyBuildInheritanceNode(childModeName, childrenModeNamesByParentMode,
                        alreadyBuiltModeNodeNames));
        }
        return new ModeNode(modeName, subModes);
    }

    private static ModeNode recursivelyBuildReferenceNode(String modeName,
                                                            Map<String, Set<String>> childrenModeNamesByParentMode,
                                                            Map<String, ModeNode> nodeByName) {
        ModeNode modeNode = nodeByName.computeIfAbsent(modeName,
                modeName_ -> new ModeNode(modeName, new ArrayList<>()));
        List<ModeNode> subModes = modeNode.subModes;
        Set<String> childrenModeNames = childrenModeNamesByParentMode.get(modeName);
        if (childrenModeNames != null) {
            for (String childModeName : childrenModeNames) {
                if (childModeName.equals(Mode.PREVIOUS_MODE_FROM_HISTORY_STACK_IDENTIFIER))
                    continue;
                ModeNode childNode = nodeByName.get(childModeName);
                if (childNode == null)
                    childNode = recursivelyBuildReferenceNode(childModeName,
                            childrenModeNamesByParentMode, nodeByName);
                subModes.add(childNode);
            }
        }
        return new ModeNode(modeName, subModes);
    }

    /**
     * Copy everything except the following from the parent mode:
     * - pushModeToHistoryStack
     * - timeout configuration
     * - switch mode commands
     * - mode after hint selection
     * - switch mode after hint selection
     * - clickButton after hint selection
     * - save mouse position after hint selection
     */
    private static void extendMode(ModeBuilder parentMode, ModeBuilder childMode) {
        for (Map.Entry<Combo, List<Command>> entry : parentMode.comboMap()
                                                               .commandsByCombo()
                                                               .entrySet()) {
            Combo combo = entry.getKey();
            List<Command> commands = entry.getValue();
            for (Command command : commands) {
                if (command instanceof SwitchMode)
                    continue;
                childMode.comboMap().add(combo, command);
            }
        }
        if (childMode.mouse().initialVelocity() == null)
            childMode.mouse().initialVelocity(parentMode.mouse().initialVelocity());
        if (childMode.mouse().maxVelocity() == null)
            childMode.mouse().maxVelocity(parentMode.mouse().maxVelocity());
        if (childMode.mouse().acceleration() == null)
            childMode.mouse().acceleration(parentMode.mouse().acceleration());
        if (childMode.wheel().initialVelocity() == null)
            childMode.wheel().initialVelocity(parentMode.wheel().initialVelocity());
        if (childMode.wheel().maxVelocity() == null)
            childMode.wheel().maxVelocity(parentMode.wheel().maxVelocity());
        if (childMode.wheel().acceleration() == null)
            childMode.wheel().acceleration(parentMode.wheel().acceleration());
        if (childMode.grid().area().type() == null)
            childMode.grid().area().type(parentMode.grid().area().type());
        if (childMode.grid().area().widthPercent() == null)
            childMode.grid().area().widthPercent(parentMode.grid().area().widthPercent());
        if (childMode.grid().area().heightPercent() == null)
            childMode.grid().area().heightPercent(parentMode.grid().area().heightPercent());
        if (childMode.grid().synchronization() == null)
            childMode.grid().synchronization(parentMode.grid().synchronization());
        if (childMode.grid().rowCount() == null)
            childMode.grid().rowCount(parentMode.grid().rowCount());
        if (childMode.grid().columnCount() == null)
            childMode.grid().columnCount(parentMode.grid().columnCount());
        if (childMode.grid().lineVisible() == null)
            childMode.grid().lineVisible(parentMode.grid().lineVisible());
        if (childMode.grid().lineHexColor() == null)
            childMode.grid().lineHexColor(parentMode.grid().lineHexColor());
        if (childMode.grid().lineThickness() == null)
            childMode.grid().lineThickness(parentMode.grid().lineThickness());
        if (childMode.hintMesh().enabled() == null)
            childMode.hintMesh().enabled(parentMode.hintMesh().enabled());
        if (childMode.hintMesh().type().type() == null)
            childMode.hintMesh().type().type(parentMode.hintMesh().type().type());
        if (childMode.hintMesh().type().gridArea().type() == null)
            childMode.hintMesh().type().gridArea().type(parentMode.hintMesh().type().gridArea().type());
        if (childMode.hintMesh().type().gridArea().widthPercent() == null)
            childMode.hintMesh().type().gridArea().widthPercent(parentMode.hintMesh().type().gridArea().widthPercent());
        if (childMode.hintMesh().type().gridArea().heightPercent() == null)
            childMode.hintMesh().type().gridArea().heightPercent(parentMode.hintMesh().type().gridArea().heightPercent());
        if (childMode.hintMesh().type().gridArea().activeScreenHintGridAreaCenter() == null)
            childMode.hintMesh().type().gridArea().activeScreenHintGridAreaCenter(parentMode.hintMesh().type().gridArea().activeScreenHintGridAreaCenter());
        if (childMode.hintMesh().type().gridRowCount() == null)
            childMode.hintMesh().type().gridRowCount(parentMode.hintMesh().type().gridRowCount());
        if (childMode.hintMesh().type().gridColumnCount() == null)
            childMode.hintMesh().type().gridColumnCount(parentMode.hintMesh().type().gridColumnCount());
        if (childMode.hintMesh().selectionKeys() == null)
            childMode.hintMesh().selectionKeys(parentMode.hintMesh().selectionKeys());
        if (childMode.hintMesh().undoKey() == null)
            childMode.hintMesh().undoKey(parentMode.hintMesh().undoKey());
        if (childMode.hintMesh().fontName() == null)
            childMode.hintMesh().fontName(parentMode.hintMesh().fontName());
        if (childMode.hintMesh().fontSize() == null)
            childMode.hintMesh().fontSize(parentMode.hintMesh().fontSize());
        if (childMode.hintMesh().fontHexColor() == null)
            childMode.hintMesh().fontHexColor(parentMode.hintMesh().fontHexColor());
        if (childMode.hintMesh().selectedPrefixFontHexColor() == null)
            childMode.hintMesh().selectedPrefixFontHexColor(parentMode.hintMesh().selectedPrefixFontHexColor());
        if (childMode.hintMesh().boxHexColor() == null)
            childMode.hintMesh().boxHexColor(parentMode.hintMesh().boxHexColor());
        if (childMode.indicator().enabled() == null)
            childMode.indicator().enabled(parentMode.indicator().enabled());
        if (childMode.indicator().size() == null)
            childMode.indicator().size(parentMode.indicator().size());
        if (childMode.indicator().idleHexColor() == null)
            childMode.indicator().idleHexColor(parentMode.indicator().idleHexColor());
        if (childMode.indicator().moveHexColor() == null)
            childMode.indicator().moveHexColor(parentMode.indicator().moveHexColor());
        if (childMode.indicator().wheelHexColor() == null)
            childMode.indicator().wheelHexColor(parentMode.indicator().wheelHexColor());
        if (childMode.indicator().mousePressHexColor() == null)
            childMode.indicator().mousePressHexColor(parentMode.indicator().mousePressHexColor());
        if (childMode.indicator().nonComboKeyPressHexColor() == null)
            childMode.indicator().nonComboKeyPressHexColor(parentMode.indicator().nonComboKeyPressHexColor());
        if (childMode.hideCursor().enabled() == null)
            childMode.hideCursor().enabled(parentMode.hideCursor().enabled());
        if (childMode.hideCursor().idleDuration() == null)
            childMode.hideCursor().idleDuration(parentMode.hideCursor().idleDuration());
    }

    private static String checkColorFormat(String propertyKey, String propertyValue) {
        if (!propertyValue.matches("^#?([a-fA-F0-9]{6})$"))
            throw new IllegalArgumentException(
                    "Invalid color " + propertyKey + "=" + propertyValue +
                    ": a color should be in the #FFFFFF format");
        return propertyValue;
    }

    private static int parseUnsignedInteger(String propertyKey, String propertyValue, int min, int max) {
        int integer = Integer.parseUnsignedInt(propertyValue);
        if (integer < min)
            throw new IllegalArgumentException(
                    "Invalid property value in " + propertyKey + ": " + integer +
                    " must greater than or equal to " + min);
        if (integer > max)
            throw new IllegalArgumentException(
                    "Invalid property value in " + propertyKey + ": " + integer +
                    " must be less than or equal to " + max);
        return integer;
    }

    private static double parseNonZeroPercent(String propertyKey, String propertyValue) {
        double percent = Double.parseDouble(propertyValue);
        if (percent <= 0)
            throw new IllegalArgumentException(
                    "Invalid property value in " + propertyKey +
                    ": percent must greater than 0.0");
        if (percent > 1)
            throw new IllegalArgumentException(
                    "Invalid property value in " + propertyKey +
                    ": percent must be less than or equal to 1.0");
        return percent;
    }

    private static Button parseButton(String propertyKey, String string) {
        return switch (string) {
            case "left" -> Button.LEFT_BUTTON;
            case "middle" -> Button.MIDDLE_BUTTON;
            case "right" -> Button.RIGHT_BUTTON;
            default -> throw new IllegalArgumentException(
                    "Invalid property value in " + propertyKey + "=" + string + ": button must be one of " +
                    List.of("left", "middle", "right"));
        };
    }

    private static List<Key> parseHintKeys(String propertyKey, String propertyValue) {
        String[] split = propertyValue.split("\\s+");
        if (split.length <= 1)
            // Even 1 key is not enough because we use fixed-length hints.
            throw new IllegalArgumentException(
                    "Invalid property value in " + propertyKey + "=" + propertyValue +
                    ": at least two keys are required");
        return Arrays.stream(split).map(Key::ofName).toList();
    }

    private static HintMeshType.HintMeshTypeType parseHintMeshTypeType(String propertyKey,
                                                                       String propertyValue) {
        return switch (propertyValue) {
            case "grid" -> HintMeshType.HintMeshTypeType.GRID;
            case "mouse-position-history" ->
                    HintMeshType.HintMeshTypeType.MOUSE_POSITION_HISTORY;
            default -> throw new IllegalArgumentException(
                    "Invalid property value in " + propertyKey + "=" + propertyValue +
                    ": type should be one of " +
                    List.of("grid", "mouse-position-history"));
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
            default -> throw new IllegalArgumentException(
                    "Invalid property value in " + propertyKey + "=" + propertyValue +
                    ": expected one of " + List.of("screen-center", "mouse"));
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

    private static void addCommand(ComboMapBuilder comboMap,
                                   String multiComboString, Command command,
                                   ComboMoveDuration defaultComboMoveDuration) {
        List<Combo> combos = Combo.multiCombo(multiComboString, defaultComboMoveDuration);
        for (Combo combo : combos)
            comboMap.add(combo, command);
    }

    /**
     * Dependency tree.
     */
    private record ModeNode(String modeName, List<ModeNode> subModes) {
    }

}

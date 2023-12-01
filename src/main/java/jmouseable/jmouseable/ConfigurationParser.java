package jmouseable.jmouseable;

import jmouseable.jmouseable.ComboMap.ComboMapBuilder;
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

import static jmouseable.jmouseable.Command.*;

public class ConfigurationParser {

    private static final Mode defaultMode;

    static {
        ModeBuilder builder = new ModeBuilder(null);
        builder.pauseComboProcessingWhenModeActivated(false);
        builder.mouse().initialVelocity(200).maxVelocity(750).acceleration(1000);
        builder.wheel().initialVelocity(1000).maxVelocity(1000).acceleration(500);
        builder.grid()
               .area(new Area.ActiveScreen(1, 1))
               .synchronization(Synchronization.MOUSE_AND_GRID_CENTER_UNSYNCHRONIZED)
               .rowCount(2)
               .columnCount(2)
               .visible(false)
               .lineHexColor(null)
               .lineThickness(1);
        builder.timeout().enabled(false);
        builder.indicator().enabled(false);
        builder.hideCursor().enabled(false);
        defaultMode = builder.build();
    }

    public static Configuration parse(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        ComboMoveDuration defaultComboMoveDuration = defaultComboMoveDuration();
        KeyboardLayout keyboardLayout = null;
        Pattern modeKeyPattern = Pattern.compile("([^.]+-mode)\\.([^.]+)(\\.([^.]+))?");
        Map<String, ModeBuilder> modeByName = new HashMap<>();
        Set<String> modeNameReferences = new HashSet<>();
        Map<String, Set<String>> childrenModeNamesByParentMode = new HashMap<>();
        Set<String> nonRootModeNames = new HashSet<>();
        Pattern linePattern = Pattern.compile("(.+?)=(.+)");
        for (String line : lines) {
            if (line.startsWith("#"))
                continue;
            Matcher lineMatcher = linePattern.matcher(line);
            if (!lineMatcher.matches())
                throw new IllegalArgumentException("Invalid property key=value: " + line);
            String propertyKey = lineMatcher.group(1);
            String propertyValue = lineMatcher.group(2);
            if (propertyKey.equals("default-combo-move-duration-millis")) {
                defaultComboMoveDuration = parseComboMoveDuration(propertyValue);
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
            Matcher keyMatcher = modeKeyPattern.matcher(propertyKey);
            if (!keyMatcher.matches())
                continue;
            String modeName = keyMatcher.group(1);
            ModeBuilder mode = modeByName.computeIfAbsent(modeName, ModeBuilder::new);
            String group2 = keyMatcher.group(2);
            switch (group2) {
                case "pause-combo-processing-when-mode-activated" ->
                        mode.pauseComboProcessingWhenModeActivated(
                                Boolean.parseBoolean(propertyValue));
                case "mouse" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid mouse configuration: " + propertyKey);
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
                                "Invalid mouse configuration: " + propertyKey);
                    }
                }
                case "wheel" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid wheel configuration: " + propertyKey);
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
                                "Invalid wheel configuration: " + propertyKey);
                    }
                }
                case "grid" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid grid configuration: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        case "area" -> mode.grid().area(parseArea(propertyValue));
                        case "synchronization" -> mode.grid()
                                                               .synchronization(
                                                                      parseSynchronization(
                                                                               propertyValue));
                        case "row-count" -> mode.grid()
                                                     .rowCount(parseUnsignedInteger(
                                                             "grid row-count",
                                                             propertyValue, 1, 10));
                        case "column-count" -> mode.grid()
                                                        .columnCount(
                                                                parseUnsignedInteger(
                                                                        "grid column-count",
                                                                        propertyValue, 1,
                                                                        10));
                        case "visible" ->
                                mode.grid().visible(Boolean.parseBoolean(propertyValue));
                        case "line-color" ->
                                mode.grid().lineHexColor(checkColorFormat(propertyValue));
                        case "line-thickness" -> mode.grid()
                                                     .lineThickness(
                                                             Integer.parseUnsignedInt(
                                                                     propertyValue));
                        default -> throw new IllegalArgumentException(
                                "Invalid grid configuration: " + propertyKey);
                    }
                }
                case "to" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid to configuration: " + propertyKey);
                    String newModeName = keyMatcher.group(4);
                    modeNameReferences.add(newModeName);
                    addCommand(mode.comboMap(), propertyValue,
                            new SwitchMode(newModeName), defaultComboMoveDuration);
                }
                case "extend" -> {
                    String parentModeName = propertyValue;
                    if (parentModeName.equals(modeName))
                        throw new IllegalArgumentException(
                                "Invalid extend parent mode " + parentModeName +
                                " for mode " + modeName);
                    childrenModeNamesByParentMode.computeIfAbsent(parentModeName,
                            parentModeName_ -> new HashSet<>()).add(modeName);
                    nonRootModeNames.add(modeName);
                    modeNameReferences.add(parentModeName);
                }
                case "timeout" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid timeout configuration: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        case "enabled" -> mode.timeout()
                                              .enabled(Boolean.parseBoolean(
                                                      propertyValue));
                        case "idle-duration-millis" ->
                                mode.timeout().idleDuration(parseDuration(propertyValue));
                        case "next-mode" -> {
                            String nextModeName = propertyValue;
                            if (nextModeName.equals(modeName))
                                throw new IllegalArgumentException(
                                        "Invalid timeout next mode " + nextModeName +
                                        " for mode " + modeName);
                            mode.timeout().nextModeName(nextModeName);
                            modeNameReferences.add(nextModeName);
                        }
                        default -> throw new IllegalArgumentException(
                                "Invalid timeout configuration: " + propertyKey);
                    }
                }
                case "indicator" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid indicator configuration: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        case "enabled" -> mode.indicator()
                                              .enabled(Boolean.parseBoolean(
                                                      propertyValue));
                        case "idle-color" -> mode.indicator()
                                                 .idleHexColor(
                                                         checkColorFormat(propertyValue));
                        case "move-color" -> mode.indicator()
                                                 .moveHexColor(
                                                         checkColorFormat(propertyValue));
                        case "wheel-color" -> mode.indicator()
                                                  .wheelHexColor(checkColorFormat(
                                                          propertyValue));
                        case "mouse-press-color" -> mode.indicator()
                                                        .mousePressHexColor(
                                                                checkColorFormat(
                                                                        propertyValue));
                        case "non-combo-key-press-color" -> mode.indicator()
                                                                .nonComboKeyPressHexColor(
                                                                        checkColorFormat(
                                                                                propertyValue));
                        default -> throw new IllegalArgumentException(
                                "Invalid indicator configuration: " + propertyKey);
                    }
                }
                case "hide-cursor" -> {
                    if (keyMatcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid hide cursor configuration: " + propertyKey);
                    switch (keyMatcher.group(4)) {
                        case "enabled" -> mode.hideCursor()
                                              .enabled(Boolean.parseBoolean(
                                                      propertyValue));
                        case "idle-duration-millis" -> mode.hideCursor()
                                                           .idleDuration(parseDuration(
                                                                   propertyValue));
                        default -> throw new IllegalArgumentException(
                                "Invalid hide cursor configuration: " + propertyKey);
                    }
                }
                // @formatter:off
                case "start-move-up" -> addCommand(mode.comboMap(), propertyValue, new StartMoveUp(), defaultComboMoveDuration);
                case "start-move-down" -> addCommand(mode.comboMap(), propertyValue, new StartMoveDown(), defaultComboMoveDuration);
                case "start-move-left" -> addCommand(mode.comboMap(), propertyValue, new StartMoveLeft(), defaultComboMoveDuration);
                case "start-move-right" -> addCommand(mode.comboMap(), propertyValue, new StartMoveRight(), defaultComboMoveDuration);

                case "stop-move-up" -> addCommand(mode.comboMap(), propertyValue, new StopMoveUp(), defaultComboMoveDuration);
                case "stop-move-down" -> addCommand(mode.comboMap(), propertyValue, new StopMoveDown(), defaultComboMoveDuration);
                case "stop-move-left" -> addCommand(mode.comboMap(), propertyValue, new StopMoveLeft(), defaultComboMoveDuration);
                case "stop-move-right" -> addCommand(mode.comboMap(), propertyValue, new StopMoveRight(), defaultComboMoveDuration);

                case "press-left" -> addCommand(mode.comboMap(), propertyValue, new PressLeft(), defaultComboMoveDuration);
                case "press-middle" -> addCommand(mode.comboMap(), propertyValue, new PressMiddle(), defaultComboMoveDuration);
                case "press-right" -> addCommand(mode.comboMap(), propertyValue, new PressRight(), defaultComboMoveDuration);

                case "release-left" -> addCommand(mode.comboMap(), propertyValue, new ReleaseLeft(), defaultComboMoveDuration);
                case "release-middle" -> addCommand(mode.comboMap(), propertyValue, new ReleaseMiddle(), defaultComboMoveDuration);
                case "release-right" -> addCommand(mode.comboMap(), propertyValue, new ReleaseRight(), defaultComboMoveDuration);

                case "start-wheel-up" -> addCommand(mode.comboMap(), propertyValue, new StartWheelUp(), defaultComboMoveDuration);
                case "start-wheel-down" -> addCommand(mode.comboMap(), propertyValue, new StartWheelDown(), defaultComboMoveDuration);
                case "start-wheel-left" -> addCommand(mode.comboMap(), propertyValue, new StartWheelLeft(), defaultComboMoveDuration);
                case "start-wheel-right" -> addCommand(mode.comboMap(), propertyValue, new StartWheelRight(), defaultComboMoveDuration);

                case "stop-wheel-up" -> addCommand(mode.comboMap(), propertyValue, new StopWheelUp(), defaultComboMoveDuration);
                case "stop-wheel-down" -> addCommand(mode.comboMap(), propertyValue, new StopWheelDown(), defaultComboMoveDuration);
                case "stop-wheel-left" -> addCommand(mode.comboMap(), propertyValue, new StopWheelLeft(), defaultComboMoveDuration);
                case "stop-wheel-right" -> addCommand(mode.comboMap(), propertyValue, new StopWheelRight(), defaultComboMoveDuration);

                case "snap-up" -> addCommand(mode.comboMap(), propertyValue, new SnapUp(), defaultComboMoveDuration);
                case "snap-down" -> addCommand(mode.comboMap(), propertyValue, new SnapDown(), defaultComboMoveDuration);
                case "snap-left" -> addCommand(mode.comboMap(), propertyValue, new SnapLeft(), defaultComboMoveDuration);
                case "snap-right" -> addCommand(mode.comboMap(), propertyValue, new SnapRight(), defaultComboMoveDuration);

                case "cut-grid-top" -> addCommand(mode.comboMap(), propertyValue, new CutGridTop(), defaultComboMoveDuration);
                case "cut-grid-bottom" -> addCommand(mode.comboMap(), propertyValue, new CutGridBottom(), defaultComboMoveDuration);
                case "cut-grid-left" -> addCommand(mode.comboMap(), propertyValue, new CutGridLeft(), defaultComboMoveDuration);
                case "cut-grid-right" -> addCommand(mode.comboMap(), propertyValue, new CutGridRight(), defaultComboMoveDuration);

                case "move-grid-top" -> addCommand(mode.comboMap(), propertyValue, new MoveGridTop(), defaultComboMoveDuration);
                case "move-grid-bottom" -> addCommand(mode.comboMap(), propertyValue, new MoveGridBottom(), defaultComboMoveDuration);
                case "move-grid-left" -> addCommand(mode.comboMap(), propertyValue, new MoveGridLeft(), defaultComboMoveDuration);
                case "move-grid-right" -> addCommand(mode.comboMap(), propertyValue, new MoveGridRight(), defaultComboMoveDuration);
                // @formatter:on
                default -> throw new IllegalArgumentException(
                        "Invalid configuration: " + propertyKey);
            }
        }
        // Verify mode name references are valid.
        for (String modeNameReference : modeNameReferences)
            if (!modeByName.containsKey(modeNameReference))
                throw new IllegalStateException(
                        "Definition of mode " + modeNameReference + " is missing");
        Set<String> rootModeNames = modeByName.keySet()
                                              .stream()
                                              .filter(Predicate.not(
                                                      nonRootModeNames::contains))
                                              .collect(Collectors.toSet());
        Set<ModeNode> rootModeNodes = new HashSet<>();
        Set<String> alreadyBuiltModeNodeNames = new HashSet<>();
        for (String rootModeName : rootModeNames)
            rootModeNodes.add(
                    recursivelyBuildModeNode(rootModeName, childrenModeNamesByParentMode,
                    alreadyBuiltModeNodeNames));
        for (ModeNode rootModeNode : rootModeNodes)
            recursivelyExtendMode(defaultMode, rootModeNode, modeByName);
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
        return new Configuration(keyboardLayout, new ModeMap(modes));
    }

    private static void recursivelyExtendMode(Mode parentMode, ModeNode modeNode,
                                              Map<String, ModeBuilder> modeByName) {
        ModeBuilder mode = modeByName.get(modeNode.modeName);
        extendMode(parentMode, mode);
        for (ModeNode subModeNode : modeNode.subModes)
            recursivelyExtendMode(mode.build(), subModeNode, modeByName);
    }

    private static ModeNode recursivelyBuildModeNode(String modeName,
                                                     Map<String, Set<String>> childrenModeNamesByParentMode,
                                                     Set<String> alreadyBuiltModeNodeNames) {
        if (!alreadyBuiltModeNodeNames.add(modeName))
            throw new IllegalStateException(
                    "Found extend dependency cycle involving mode " + modeName);
        Set<ModeNode> subModes = new HashSet<>();
        Set<String> childrenModeNames = childrenModeNamesByParentMode.get(modeName);
        if (childrenModeNames != null) {
            for (String childModeName : childrenModeNames)
                subModes.add(
                        recursivelyBuildModeNode(childModeName, childrenModeNamesByParentMode,
                        alreadyBuiltModeNodeNames));
        }
        return new ModeNode(modeName, subModes);
    }

    /**
     * Copy everything except the following from the parent mode:
     * - pauseComboProcessingWhenModeActivated
     * - timeout configuration
     * - switch mode commands
     */
    private static void extendMode(Mode parentMode, ModeBuilder childMode) {
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
        if (childMode.grid().area() == null)
            childMode.grid().area(parentMode.gridConfiguration().area());
        if (childMode.grid().synchronization() == null)
            childMode.grid().synchronization(parentMode.gridConfiguration().synchronization());
        if (childMode.grid().rowCount() == null)
            childMode.grid().rowCount(parentMode.gridConfiguration().rowCount());
        if (childMode.grid().columnCount() == null)
            childMode.grid().columnCount(parentMode.gridConfiguration().columnCount());
        if (childMode.grid().visible() == null)
            childMode.grid().visible(parentMode.gridConfiguration().visible());
        if (childMode.grid().lineHexColor() == null)
            childMode.grid().lineHexColor(parentMode.gridConfiguration().lineHexColor());
        if (childMode.grid().lineThickness() == null)
            childMode.grid().lineThickness(parentMode.gridConfiguration().lineThickness());
        if (childMode.indicator().enabled() == null)
            childMode.indicator().enabled(parentMode.indicator().enabled());
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

    private static String checkColorFormat(String propertyValue) {
        if (!propertyValue.matches("^#?([a-fA-F0-9]{6})$"))
            throw new IllegalArgumentException("Invalid hex color: " + propertyValue);
        return propertyValue;
    }

    private static int parseUnsignedInteger(String configurationName, String string, int min, int max) {
        int integer = Integer.parseUnsignedInt(string);
        if (integer < min)
            throw new IllegalArgumentException(
                    "Invalid " + configurationName + " configuration: " + integer +
                    " is not greater than or equal to " + min);
        if (integer > max)
            throw new IllegalArgumentException(
                    "Invalid " + configurationName + " configuration: " + integer +
                    " is not less than or equal to " + max);
        return integer;
    }

    private static Area parseArea(String string) {
        String[] split = string.split("\\s+");
        double width, height;
        if (split.length == 1)
            width = height = 1;
        else if (split.length == 3) {
            width = Double.parseDouble(split[1]);
            height = Double.parseDouble(split[2]);
            if (width < 0 || width > 1 || height < 0 || height > 1)
                throw new IllegalArgumentException(
                        "Invalid grid area configuration: " + string);
        }
        else
            throw new IllegalArgumentException("Invalid grid area configuration: " + string);
        return switch (split[0]) {
            case "active-screen" -> new Area.ActiveScreen(width, height);
            case "active-window" -> new Area.ActiveWindow(width, height);
            default -> throw new IllegalArgumentException(
                    "Invalid grid area configuration: " + string);
        };
    }

    private static Synchronization parseSynchronization(String string) {
        return switch (string) {
            case "mouse-and-grid-center-unsynchronized" ->
                    Synchronization.MOUSE_AND_GRID_CENTER_UNSYNCHRONIZED;
            case "mouse-follows-grid-center" -> Synchronization.MOUSE_FOLLOWS_GRID_CENTER;
            case "grid-center-follows-mouse" -> Synchronization.GRID_CENTER_FOLLOWS_MOUSE;
            default -> throw new IllegalArgumentException(
                    "Invalid grid synchronization configuration: " + string);
        };
    }

    private static ComboMoveDuration parseComboMoveDuration(String string) {
        String[] split = string.split("-");
        return new ComboMoveDuration(
                Duration.ofMillis(Integer.parseUnsignedInt(split[0])),
                Duration.ofMillis(Integer.parseUnsignedInt(split[1])));
    }

    private static Duration parseDuration(String string) {
        return Duration.ofMillis(Integer.parseUnsignedInt(string));
    }

    private static ComboMoveDuration defaultComboMoveDuration() {
        return new ComboMoveDuration(Duration.ZERO, Duration.ofMillis(150));
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
    private record ModeNode(String modeName, Set<ModeNode> subModes) {
    }

}

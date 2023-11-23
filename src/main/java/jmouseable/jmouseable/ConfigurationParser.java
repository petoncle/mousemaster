package jmouseable.jmouseable;

import jmouseable.jmouseable.ComboMap.ComboMapBuilder;
import jmouseable.jmouseable.Mode.ModeBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static jmouseable.jmouseable.Command.*;

public class ConfigurationParser {

    private static final Mode defaultMode =
            new Mode(null, new ComboMap(Map.of()), new Mouse(200, 750, 1000),
                    new Wheel(1000, 1000, 500), new Attach(1, 1), null,
                    new Indicator(false, null, null, null, null, null));

    public static Configuration parse(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        ComboMoveDuration defaultComboMoveDuration = defaultComboMoveDuration();
        Pattern modeKeyPattern = Pattern.compile("([^.]+-mode)\\.([^.]+)(\\.([^.]+))?");
        Map<String, ModeBuilder> modeByName = new HashMap<>();
        Set<String> modeNameReferences = new HashSet<>();
        for (String line : lines) {
            if (line.startsWith("#"))
                continue;
            if (!line.matches("[^=]+=[^=]+")) {
                throw new IllegalArgumentException("Invalid property key=value: " + line);
            }
            String propertyKey = line.split("=")[0];
            String propertyValue = line.split("=")[1];
            if (propertyKey.equals("default-combo-move-duration-millis")) {
                defaultComboMoveDuration = parseComboMoveDuration(propertyValue);
                continue;
            }
            Matcher matcher = modeKeyPattern.matcher(propertyKey);
            if (!matcher.matches())
                continue;
            String modeName = matcher.group(1);
            ModeBuilder mode = modeByName.computeIfAbsent(modeName, ModeBuilder::new);
            String group2 = matcher.group(2);
            switch (group2) {
                case "mouse" -> {
                    if (matcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid mouse configuration: " + propertyKey);
                    switch (matcher.group(4)) {
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
                    if (matcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid wheel configuration: " + propertyKey);
                    switch (matcher.group(4)) {
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
                case "attach" -> {
                    if (matcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid attach configuration: " + propertyKey);
                    switch (matcher.group(4)) {
                        case "grid-row-count" -> mode.attach()
                                                     .gridRowCount(
                                                             Integer.parseUnsignedInt(
                                                                     propertyValue));
                        case "grid-column-count" -> mode.attach()
                                                        .gridColumnCount(
                                                                Integer.parseUnsignedInt(
                                                                        propertyValue));
                        default -> throw new IllegalArgumentException(
                                "Invalid attach configuration: " + propertyKey);
                    }
                }
                case "to" -> {
                    if (matcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid to configuration: " + propertyKey);
                    String newModeName = matcher.group(4);
                    modeNameReferences.add(newModeName);
                    addCommand(mode.comboMap(), propertyValue,
                            new ChangeMode(newModeName), defaultComboMoveDuration);
                }
                case "timeout" -> {
                    if (matcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid timeout configuration: " + propertyKey);
                    switch (matcher.group(4)) {
                        case "duration-millis" ->
                                mode.timeout().duration(parseDuration(propertyValue));
                        case "next-mode" -> {
                            mode.timeout().nextModeName(propertyValue);
                            modeNameReferences.add(propertyValue);
                        }
                        default -> throw new IllegalArgumentException(
                                "Invalid timeout configuration: " + propertyKey);
                    }
                }
                case "indicator" -> {
                    if (matcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid indicator configuration: " + propertyKey);
                    switch (matcher.group(4)) {
                        case "enabled" -> mode.indicator()
                                              .enabled(Boolean.parseBoolean(
                                                      propertyValue));
                        case "idle-color" -> {
                            checkColorFormat(propertyValue);
                            mode.indicator().idleHexColor(propertyValue);
                        }
                        case "move-color" -> {
                            checkColorFormat(propertyValue);
                            mode.indicator().moveHexColor(propertyValue);
                        }
                        case "wheel-color" -> {
                            checkColorFormat(propertyValue);
                            mode.indicator().wheelHexColor(propertyValue);
                        }
                        case "mouse-press-color" -> {
                            checkColorFormat(propertyValue);
                            mode.indicator().mousePressHexColor(propertyValue);
                        }
                        case "non-combo-key-press-color" -> {
                            checkColorFormat(propertyValue);
                            mode.indicator().nonComboKeyPressHexColor(propertyValue);
                        }
                        default -> throw new IllegalArgumentException(
                                "Invalid indicator configuration: " + propertyKey);
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

                case "attach-up" -> addCommand(mode.comboMap(), propertyValue, new AttachUp(), defaultComboMoveDuration);
                case "attach-down" -> addCommand(mode.comboMap(), propertyValue, new AttachDown(), defaultComboMoveDuration);
                case "attach-left" -> addCommand(mode.comboMap(), propertyValue, new AttachLeft(), defaultComboMoveDuration);
                case "attach-right" -> addCommand(mode.comboMap(), propertyValue, new AttachRight(), defaultComboMoveDuration);
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
        for (ModeBuilder mode : modeByName.values()) {
            if (mode.timeout().duration() == null ^ mode.timeout().nextModeName() == null)
                throw new IllegalStateException(
                        "Definition of mode timeout for " + mode.name() +
                        " is incomplete");
        }
        for (ModeBuilder mode : modeByName.values())
            extendMode(defaultMode, mode);
        Set<Mode> modes = modeByName.values()
                                    .stream()
                                    .map(ModeBuilder::build)
                                    .collect(Collectors.toSet());
        return new Configuration(new ModeMap(modes));
    }

    /**
     * Copy combo map, mouse, wheel, attach, and indicator configuration from the parent mode.
     */
    private static void extendMode(Mode parentMode, ModeBuilder childMode) {
        for (Map.Entry<Combo, List<Command>> entry : parentMode.comboMap()
                                                               .commandsByCombo()
                                                               .entrySet()) {
            Combo combo = entry.getKey();
            List<Command> commands = entry.getValue();
            for (Command command : commands)
                childMode.comboMap().add(combo, command);
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
        if (childMode.attach().gridRowCount() == null)
            childMode.attach().gridRowCount(parentMode.attach().gridRowCount());
        if (childMode.attach().gridColumnCount() == null)
            childMode.attach().gridColumnCount(parentMode.attach().gridColumnCount());
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
    }

    private static void checkColorFormat(String propertyValue) {
        if (!propertyValue.matches("^#?([a-fA-F0-9]{6}|[a-fA-F0-9]{3})$"))
            throw new IllegalArgumentException("Invalid hex color: " + propertyValue);
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

}

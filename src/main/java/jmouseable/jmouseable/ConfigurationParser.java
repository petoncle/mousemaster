package jmouseable.jmouseable;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static jmouseable.jmouseable.Command.*;

@Component
public class ConfigurationParser {

    private final Environment environment;

    public ConfigurationParser(Environment environment) {
        this.environment = environment;
    }

    public Configuration parse() {
        ComboMoveDuration defaultComboMoveDuration = defaultComboMoveDuration();
        Pattern modeKeyPattern = Pattern.compile("([^.]+-mode)\\.([^.]+)(\\.([^.]+))?");
        Map<String, Mode> modeByName = new HashMap<>();
        Set<String> modeNameReferences = new HashSet<>();
        for (PropertySource<?> propertySource : ((ConfigurableEnvironment) environment).getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource<?> source))
                continue;
            for (String propertyKey : source.getPropertyNames()) {
                String propertyValue = (String) source.getProperty(propertyKey);
                Objects.requireNonNull(propertyValue);
                if (propertyKey.equals("default-combo-move-duration")) {
                    defaultComboMoveDuration = parseComboMoveDuration(propertyValue);
                    continue;
                }
                Matcher matcher = modeKeyPattern.matcher(propertyKey);
                if (!matcher.matches())
                    continue;
                String modeName = matcher.group(1);
                Mode mode =
                        modeByName.computeIfAbsent(modeName, name -> newMode(modeName));
                Map<Combo, List<Command>> commandsByCombo =
                        mode.comboMap().commandsByCombo();
                String group2 = matcher.group(2);
                switch (group2) {
                    case "mouse" -> {
                        if (matcher.group(4) == null)
                            throw new IllegalArgumentException(
                                    "Invalid mouse configuration: " + propertyKey);
                        double acceleration = matcher.group(4).equals("acceleration") ?
                                Double.parseDouble(propertyValue) :
                                mode.mouse().acceleration();
                        double maxVelocity = matcher.group(4).equals("max-velocity") ?
                                Double.parseDouble(propertyValue) :
                                mode.mouse().maxVelocity();
                        modeByName.put(modeName, new Mode(modeName, mode.comboMap(),
                                new Mouse(acceleration, maxVelocity), mode.wheel(),
                                mode.timeout(), mode.indicator()));
                    }
                    case "wheel" -> {
                        if (matcher.group(4) == null)
                            throw new IllegalArgumentException(
                                    "Invalid wheel configuration: " + propertyKey);
                        double acceleration = matcher.group(4).equals("acceleration") ?
                                Double.parseDouble(propertyValue) :
                                mode.wheel().acceleration();
                        double maxVelocity = matcher.group(4).equals("max-velocity") ?
                                Double.parseDouble(propertyValue) :
                                mode.wheel().maxVelocity();
                        modeByName.put(modeName,
                                new Mode(modeName, mode.comboMap(), mode.mouse(),
                                        new Wheel(acceleration, maxVelocity),
                                        mode.timeout(), mode.indicator()));
                    }
                    case "to" -> {
                        if (matcher.group(4) == null)
                            throw new IllegalArgumentException(
                                    "Invalid to configuration: " + propertyKey);
                        String newModeName = matcher.group(4);
                        modeNameReferences.add(newModeName);
                        addCommand(commandsByCombo, propertyValue,
                                new ChangeMode(newModeName), defaultComboMoveDuration);
                    }
                    case "timeout" -> {
                        if (matcher.group(4) == null)
                            throw new IllegalArgumentException(
                                    "Invalid to configuration: " + propertyKey);
                        ModeTimeout modeTimeout = switch (matcher.group(4)) {
                            case "duration-millis" ->
                                    new ModeTimeout(parseDuration(propertyValue),
                                            mode.timeout() == null ? null :
                                                    mode.timeout().nextModeName());
                            case "next-mode" -> new ModeTimeout(
                                    mode.timeout() == null ? null :
                                            mode.timeout().duration(), propertyValue);
                            default -> throw new IllegalArgumentException(
                                    "Invalid timeout configuration: " + propertyKey);
                        };
                        if (modeTimeout.nextModeName() != null)
                            modeNameReferences.add(modeTimeout.nextModeName());
                        modeByName.put(modeName,
                                new Mode(modeName, mode.comboMap(), mode.mouse(),
                                        mode.wheel(), modeTimeout, mode.indicator()));
                    }
                    case "indicator" -> {
                        if (matcher.group(4) == null)
                            throw new IllegalArgumentException(
                                    "Invalid to configuration: " + propertyKey);
                        Indicator indicator = switch (matcher.group(4)) {
                            case "enabled" ->
                                    new Indicator(Boolean.parseBoolean(propertyValue));
                            default -> throw new IllegalArgumentException(
                                    "Invalid indicator configuration: " + propertyKey);
                        };
                        modeByName.put(modeName,
                                new Mode(modeName, mode.comboMap(), mode.mouse(),
                                        mode.wheel(), mode.timeout(), indicator));
                    }
                    // @formatter:off
                    case "start-move-up" -> addCommand(commandsByCombo, propertyValue, new StartMoveUp(), defaultComboMoveDuration);
                    case "start-move-down" -> addCommand(commandsByCombo, propertyValue, new StartMoveDown(), defaultComboMoveDuration);
                    case "start-move-left" -> addCommand(commandsByCombo, propertyValue, new StartMoveLeft(), defaultComboMoveDuration);
                    case "start-move-right" -> addCommand(commandsByCombo, propertyValue, new StartMoveRight(), defaultComboMoveDuration);

                    case "stop-move-up" -> addCommand(commandsByCombo, propertyValue, new StopMoveUp(), defaultComboMoveDuration);
                    case "stop-move-down" -> addCommand(commandsByCombo, propertyValue, new StopMoveDown(), defaultComboMoveDuration);
                    case "stop-move-left" -> addCommand(commandsByCombo, propertyValue, new StopMoveLeft(), defaultComboMoveDuration);
                    case "stop-move-right" -> addCommand(commandsByCombo, propertyValue, new StopMoveRight(), defaultComboMoveDuration);

                    case "press-left" -> addCommand(commandsByCombo, propertyValue, new PressLeft(), defaultComboMoveDuration);
                    case "press-middle" -> addCommand(commandsByCombo, propertyValue, new PressMiddle(), defaultComboMoveDuration);
                    case "press-right" -> addCommand(commandsByCombo, propertyValue, new PressRight(), defaultComboMoveDuration);

                    case "release-left" -> addCommand(commandsByCombo, propertyValue, new ReleaseLeft(), defaultComboMoveDuration);
                    case "release-middle" -> addCommand(commandsByCombo, propertyValue, new ReleaseMiddle(), defaultComboMoveDuration);
                    case "release-right" -> addCommand(commandsByCombo, propertyValue, new ReleaseRight(), defaultComboMoveDuration);

                    case "start-wheel-up" -> addCommand(commandsByCombo, propertyValue, new StartWheelUp(), defaultComboMoveDuration);
                    case "start-wheel-down" -> addCommand(commandsByCombo, propertyValue, new StartWheelDown(), defaultComboMoveDuration);
                    case "start-wheel-left" -> addCommand(commandsByCombo, propertyValue, new StartWheelLeft(), defaultComboMoveDuration);
                    case "start-wheel-right" -> addCommand(commandsByCombo, propertyValue, new StartWheelRight(), defaultComboMoveDuration);

                    case "stop-wheel-up" -> addCommand(commandsByCombo, propertyValue, new StopWheelUp(), defaultComboMoveDuration);
                    case "stop-wheel-down" -> addCommand(commandsByCombo, propertyValue, new StopWheelDown(), defaultComboMoveDuration);
                    case "stop-wheel-left" -> addCommand(commandsByCombo, propertyValue, new StopWheelLeft(), defaultComboMoveDuration);
                    case "stop-wheel-right" -> addCommand(commandsByCombo, propertyValue, new StopWheelRight(), defaultComboMoveDuration);
                    // @formatter:on
                    default -> throw new IllegalArgumentException(
                            "Invalid configuration: " + propertyKey);
                }
            }
        }
        // Verify mode name references are valid.
        for (String modeNameReference : modeNameReferences)
            if (!modeByName.containsKey(modeNameReference))
                throw new IllegalStateException(
                        "Definition of mode " + modeNameReference + " is missing");
        for (Mode mode : modeByName.values()) {
            if (mode.timeout() != null && (mode.timeout().duration() == null ||
                                           mode.timeout().nextModeName() == null))
                throw new IllegalStateException(
                        "Definition of mode timeout for " + mode.name() +
                        " is incomplete");
        }
        return new Configuration(new ModeMap(Set.copyOf(modeByName.values())));
    }

    private ComboMoveDuration parseComboMoveDuration(String string) {
        String[] split = string.split("-");
        return new ComboMoveDuration(
                Duration.ofMillis(Integer.parseUnsignedInt(split[0])),
                Duration.ofMillis(Integer.parseUnsignedInt(split[1])));
    }

    private static Duration parseDuration(String string) {
        return Duration.ofMillis(Integer.parseUnsignedInt(string));
    }

    private ComboMoveDuration defaultComboMoveDuration() {
        return new ComboMoveDuration(Duration.ZERO, Duration.ofMillis(150));
    }

    private static Mode newMode(String modeName) {
        // Keep order of commands, so that start-wheel-up after start-move-up means former will cancel latter.
        // Should we reset the combo preparation once it is complete, instead of relying on command order?
        // TODO revert
        return new Mode(modeName, new ComboMap(new LinkedHashMap<>()),
                new Mouse(50, 1000), new Wheel(100, 100), null, new Indicator(false));
    }

    private static void addCommand(Map<Combo, List<Command>> commandsByCombo,
                                   String multiComboString, Command command,
                                   ComboMoveDuration defaultComboMoveDuration) {
        List<Combo> combos = Combo.multiCombo(multiComboString, defaultComboMoveDuration);
        for (Combo combo : combos)
            commandsByCombo.computeIfAbsent(combo, combo1 -> new ArrayList<>())
                           .add(command);
    }

}

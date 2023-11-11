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
        Duration defaultComboBreakingTimeout = defaultComboBreakingTimeout();
        Pattern modeKeyPattern = Pattern.compile("([^.]+-mode)\\.([^.]+)(\\.([^.]+))?");
        Map<String, Mode> modeByName = new HashMap<>();
        Set<String> modeNameReferences = new HashSet<>();
        for (PropertySource<?> propertySource : ((ConfigurableEnvironment) environment).getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource<?> source))
                continue;
            for (String propertyKey : source.getPropertyNames()) {
                String propertyValue = (String) source.getProperty(propertyKey);
                Objects.requireNonNull(propertyValue);
                if (propertyKey.equals("default-combo-breaking-timeout")) {
                    defaultComboBreakingTimeout = parseDuration(propertyValue);
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
                                mode.timeout()));
                    }
                    case "wheel" -> {
                        if (matcher.group(4) == null)
                            throw new IllegalArgumentException(
                                    "Invalid wheel configuration: " + propertyKey);
                        double acceleration = matcher.group(4).equals("acceleration") ?
                                Double.parseDouble(propertyValue) :
                                mode.mouse().acceleration();
                        double maxVelocity = matcher.group(4).equals("max-velocity") ?
                                Double.parseDouble(propertyValue) :
                                mode.mouse().maxVelocity();
                        modeByName.put(modeName,
                                new Mode(modeName, mode.comboMap(), mode.mouse(),
                                        new Wheel(acceleration, maxVelocity),
                                        mode.timeout()));
                    }
                    case "to" -> {
                        if (matcher.group(4) == null)
                            throw new IllegalArgumentException(
                                    "Invalid to configuration: " + propertyKey);
                        String newModeName = matcher.group(4);
                        modeNameReferences.add(newModeName);
                        addCommand(commandsByCombo, propertyValue,
                                new ChangeMode(newModeName));
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
                                        mode.wheel(), modeTimeout));
                    }
                    // @formatter:off
                    case "start-move-up" -> addCommand(commandsByCombo, propertyValue, new StartMoveUp());
                    case "start-move-down" -> addCommand(commandsByCombo, propertyValue, new StartMoveDown());
                    case "start-move-left" -> addCommand(commandsByCombo, propertyValue, new StartMoveLeft());
                    case "start-move-right" -> addCommand(commandsByCombo, propertyValue, new StartMoveRight());

                    case "stop-move-up" -> addCommand(commandsByCombo, propertyValue, new StopMoveUp());
                    case "stop-move-down" -> addCommand(commandsByCombo, propertyValue, new StopMoveDown());
                    case "stop-move-left" -> addCommand(commandsByCombo, propertyValue, new StopMoveLeft());
                    case "stop-move-right" -> addCommand(commandsByCombo, propertyValue, new StopMoveRight());

                    case "press-left" -> addCommand(commandsByCombo, propertyValue, new PressLeft());
                    case "press-middle" -> addCommand(commandsByCombo, propertyValue, new PressMiddle());
                    case "press-right" -> addCommand(commandsByCombo, propertyValue, new PressRight());

                    case "release-left" -> addCommand(commandsByCombo, propertyValue, new ReleaseLeft());
                    case "release-middle" -> addCommand(commandsByCombo, propertyValue, new ReleaseMiddle());
                    case "release-right" -> addCommand(commandsByCombo, propertyValue, new ReleaseRight());
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
        return new Configuration(defaultComboBreakingTimeout,
                new ModeMap(Set.copyOf(modeByName.values())));
    }

    private static Duration parseDuration(String propertyValue) {
        return Duration.ofMillis(Integer.parseUnsignedInt(propertyValue));
    }

    private Duration defaultComboBreakingTimeout() {
        return Duration.ofMillis(150);
    }

    private static Mode newMode(String modeName) {
        return new Mode(modeName, new ComboMap(new HashMap<>()), new Mouse(50, 1000),
                new Wheel(100, 100), null);
    }

    private static void addCommand(Map<Combo, List<Command>> commandsByCombo,
                                   String multiComboString, Command command) {
        List<Combo> combos = Combo.multiCombo(multiComboString);
        for (Combo combo : combos)
            commandsByCombo.computeIfAbsent(combo, combo1 -> new ArrayList<>())
                           .add(command);
    }

}

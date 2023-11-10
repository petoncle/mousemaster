package jmouseable.jmouseable;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static jmouseable.jmouseable.Command.*;

@Component
public class ModeMapParser {

    private final Environment environment;

    public ModeMapParser(Environment environment) {
        this.environment = environment;
    }

    public ModeMap parse() {
        Pattern keyPattern = Pattern.compile("([^.]+-mode)\\.([^.]+)(\\.([^.]+))?");
        Map<String, Mode> modeByName = new HashMap<>();
        Set<String> modeNameReferences = new HashSet<>();
        for (PropertySource<?> propertySource : ((ConfigurableEnvironment) environment).getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource<?> source))
                continue;
            for (String propertyKey : source.getPropertyNames()) {
                Matcher matcher = keyPattern.matcher(propertyKey);
                if (!matcher.matches())
                    continue;
                String modeName = matcher.group(1);
                Mode mode =
                        modeByName.computeIfAbsent(modeName, name -> newMode(modeName));
                String propertyValue = (String) source.getProperty(propertyKey);
                Objects.requireNonNull(propertyValue);
                String group2 = matcher.group(2);
                if (group2.equals("mouse")) {
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
                            new Mouse(acceleration, maxVelocity), mode.wheel()));
                }
                else if (group2.equals("wheel")) {
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
                                    new Wheel(acceleration, maxVelocity)));
                }
                else if (group2.equals("to")) {
                    if (matcher.group(4) == null)
                        throw new IllegalArgumentException(
                                "Invalid to configuration: " + propertyKey);
                    String newModeName = matcher.group(4);
                    modeNameReferences.add(newModeName);
                    Map<Combo, List<Command>> commandsByCombo =
                            mode.comboMap().commandsByCombo();
                    commandsByCombo.computeIfAbsent(Combo.of(propertyValue),
                            combo -> new ArrayList<>()).add(new ChangeMode(newModeName));
                }
                else if (matcher.group(3) != null)
                    throw new IllegalArgumentException(
                            "Invalid configuration: " + propertyKey);
                switch (group2) {
                    // @formatter:off
                    case "start-move-up" -> addCommand(mode, new StartMoveUp(), propertyValue);
                    case "start-move-down" -> addCommand(mode, new StartMoveDown(), propertyValue);
                    case "start-move-left" -> addCommand(mode, new StartMoveLeft(), propertyValue);
                    case "start-move-right" -> addCommand(mode, new StartMoveRight(), propertyValue);

                    case "stop-move-up" -> addCommand(mode, new StopMoveUp(), propertyValue);
                    case "stop-move-down" -> addCommand(mode, new StopMoveDown(), propertyValue);
                    case "stop-move-left" -> addCommand(mode, new StopMoveLeft(), propertyValue);
                    case "stop-move-right" -> addCommand(mode, new StopMoveRight(), propertyValue);

                    case "press-left" -> addCommand(mode, new PressLeft(), propertyValue);
                    case "press-middle" -> addCommand(mode, new PressMiddle(), propertyValue);
                    case "press-right" -> addCommand(mode, new PressRight(), propertyValue);

                    case "release-left" -> addCommand(mode, new ReleaseLeft(), propertyValue);
                    case "release-middle" -> addCommand(mode, new ReleaseMiddle(), propertyValue);
                    case "release-right" -> addCommand(mode, new ReleaseRight(), propertyValue);
                    // @formatter:on
                }

            }
        }
        // Verify mode name references are valid.
        for (String modeNameReference : modeNameReferences)
            if (!modeByName.containsKey(modeNameReference))
                throw new IllegalStateException(
                        "Definition of mode " + modeNameReference + " is missing ");
        return new ModeMap(Set.copyOf(modeByName.values()));
    }

    private static Mode newMode(String modeName) {
        return new Mode(modeName, new ComboMap(new HashMap<>()), new Mouse(10, 100),
                new Wheel(10, 100));
    }

    private static void addCommand(Mode mode, Command command, String comboString) {
        mode.comboMap()
            .commandsByCombo()
            .computeIfAbsent(Combo.of(comboString), combo -> new ArrayList<>())
            .add(command);
    }

}

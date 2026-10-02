package mousemaster;

import mousemaster.platform.Overlay;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AnimationTest {

    private ComboWatcher comboWatcher;
    private MacroPlayer macroPlayer;
    private IndicatorManager indicatorManager;
    private final List<IndicatorConfiguration> drawn = new ArrayList<>();
    private Map<Combo, List<Command>> commandsByCombo;

    private void load(String... lines) {
        Configuration configuration = ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
        ModeMap modeMap = configuration.modeMap();
        Set<Key> pressedPreconditionKeys = new HashSet<>();
        for (Mode mode : modeMap.modes())
            for (Combo combo : mode.comboMap().commandsByCombo().keySet())
                pressedPreconditionKeys.addAll(combo.precondition()
                                                    .keyPrecondition()
                                                    .pressedKeyPrecondition()
                                                    .allKeys());
        comboWatcher = new ComboWatcher(null, null, () -> new App("test.exe"), null,
                Instant::now, Set.of(), pressedPreconditionKeys, new KeyRedactor(KeyRedaction.NONE), modeMap,
                configuration.initiallySetVariables(), configuration.virtualKeys(),
                configuration.initiallyPressedVirtualKeys());
        macroPlayer = new MacroPlayer(Instant::now, comboWatcher, null, null,
                new KeyRedactor(KeyRedaction.NONE));
        Overlay overlay = (Overlay) Proxy.newProxyInstance(
                Overlay.class.getClassLoader(), new Class<?>[] {Overlay.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("setIndicator"))
                        drawn.add((IndicatorConfiguration) args[0]);
                    return null;
                });
        indicatorManager = new IndicatorManager(overlay, macroPlayer);
        comboWatcher.setModeListeners(List.of(indicatorManager));
        comboWatcher.modeChanged(modeMap.get(Mode.IDLE_MODE_NAME));
        commandsByCombo = modeMap.get(Mode.IDLE_MODE_NAME).comboMap().commandsByCombo();
    }

    private void run(String combo) {
        commandsByCombo.entrySet()
                       .stream()
                       .filter(entry -> entry.getKey().toString().contains(combo))
                       .flatMap(entry -> entry.getValue().stream())
                       .map(command -> ((Command.MacroCommand) command).macro())
                       .forEach(macro -> macroPlayer.submit(
                               macro.resolve(new AliasResolution(Map.of()))));
    }

    private void tick(double delta) {
        macroPlayer.update(delta);
        indicatorManager.update(delta);
    }

    private int drawnSize() {
        return drawn.getLast().size();
    }

    private int size() {
        return comboWatcher.getMutatedMode().indicator().size();
    }

    @Test
    void anAnimationReadsAsPressedForItsDuration() {
        load("ripple-animation.duration-millis=100",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.indicator.size=26 | _{ripple-animation} -> 42");
        run("+a");
        macroPlayer.update(0.01);
        assertEquals(42, size());

        macroPlayer.update(0.1);
        assertEquals(26, size());
    }

    @Test
    void startingARunningAnimationStartsItOver() {
        load("ripple-animation.duration-millis=100",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.indicator.size=26 | _{ripple-animation} -> 42");
        run("+a");
        macroPlayer.update(0.01);
        macroPlayer.update(0.05);
        run("+a");
        macroPlayer.update(0.06);
        assertEquals(42, size());

        macroPlayer.update(0.1);
        assertEquals(26, size());
    }

    @Test
    void stoppingAnAnimationEndsIt() {
        load("ripple-animation.duration-millis=100",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.ripple-animation.stop=+b",
                "idle-mode.indicator.size=26 | _{ripple-animation} -> 42");
        run("+a");
        macroPlayer.update(0.01);
        run("+b");
        macroPlayer.update(0.01);
        assertEquals(26, size());
    }

    @Test
    void anAnimationNeedsADuration() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> load("idle-mode.ripple-animation.start=+a"));
        assertTrue(exception.getMessage().contains("ripple-animation.duration-millis"));
    }

    @Test
    void anAnimationHasNoOtherProperty() {
        assertThrows(IllegalArgumentException.class,
                () -> load("ripple-animation.duration=100"));
        assertThrows(IllegalArgumentException.class,
                () -> load("ripple-animation.duration-millis=100",
                        "idle-mode.ripple-animation.restart=+a"));
    }

    @Test
    void keyframesFollowTheAnimation() {
        load("ripple-animation.duration-millis=100",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.indicator.size=26 | _{ripple-animation} -> 0% 10; 100% 36");
        tick(0);
        run("+a");
        tick(0);
        assertEquals(10, drawnSize());
        tick(0.05);
        assertEquals(23, drawnSize());
        tick(0.06);
        assertEquals(26, drawnSize());
    }

    @Test
    void currentIsWhatWasDrawnWhenTheAnimationStarted() {
        load("ripple-animation.duration-millis=100",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.indicator.size=26 | _{ripple-animation} -> 100% 36");
        tick(0);
        run("+a");
        tick(0);
        assertEquals(26, drawnSize());
        tick(0.05);
        assertEquals(31, drawnSize());
    }

    @Test
    void keyframesAtTheSamePercentJump() {
        load("ripple-animation.duration-millis=100",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.indicator.size=26 | _{ripple-animation} -> 0% 10; 50% 10; 50% 40; 100% 40");
        tick(0);
        run("+a");
        tick(0);
        tick(0.049);
        assertEquals(10, drawnSize());
        tick(0.002);
        assertEquals(40, drawnSize());
    }

    @Test
    void keyframesNeedABranchOnOneAnimation() {
        assertThrows(IllegalArgumentException.class,
                () -> load("idle-mode.indicator.size=0% 10; 100% 36"));
        assertThrows(IllegalArgumentException.class,
                () -> load("idle-mode.indicator.size=26 | +a -> 0% 10; 100% 36"));
        assertThrows(IllegalArgumentException.class,
                () -> load("a-animation.duration-millis=100",
                        "b-animation.duration-millis=100",
                        "idle-mode.indicator.size=26 | _{a-animation b-animation} -> 0% 10; 100% 36"));
    }

}

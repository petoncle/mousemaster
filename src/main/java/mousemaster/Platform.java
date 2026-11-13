package mousemaster;

import mousemaster.KeyEvent.PressKeyEvent;
import mousemaster.KeyEvent.ReleaseKeyEvent;
import mousemaster.platform.ActiveAppFinder;
import mousemaster.platform.Console;
import mousemaster.platform.KeyboardController;
import mousemaster.platform.Overlay;
import mousemaster.platform.UiAutomation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public interface Platform extends ModeListener {

    void update(double delta);

    void pumpEvents();

    void sleep() throws InterruptedException;

    void reset(MouseManager mouseManager, KeyboardManager keyboardManager,
               KeyRegurgitator keyRegurgitator, MacroPlayer macroPlayer,
               KeyRedactor keyRedactor,
               boolean logLastKeyEventsOnExit,
               ModeMap modeMap,
               ZoomManager zoomManager,
               IndicatorManager indicatorManager,
               HintManager hintManager,
               List<MousePositionListener> mousePositionListeners,
               KeyboardLayout activeKeyboardLayout);

    void shutdown();

    /**
     * Shuts down, then kills the process instead of exiting it: Qt's and the CRT's teardown
     * warns and can hang for seconds when it runs off the main thread.
     */
    void killProcess(int exitCode);

    void requestKillProcess();

    void requestRestartProcess();

    Clock clock();

    KeyboardLayout activeKeyboardLayout();

    KeyboardController keyboard();

    mousemaster.platform.MouseController mouse();

    Set<Screen> screens();

    Overlay overlay();

    UiAutomation uiAutomation();

    Vision vision();

    ActiveAppFinder activeAppFinder();

    Console console();

    /** A token is +key, -key, a bare key for a tap, or a number of milliseconds to wait. */
    default Thread startSimulationThread(String simulationKeyEvents,
                                         Consumer<KeyEvent> keyEvents) {
        Logger logger = LoggerFactory.getLogger(getClass());
        Thread simulationThread = new Thread(() -> {
            for (String token : simulationKeyEvents.trim().split("\\s+")) {
                try {
                    if (token.matches("\\d+")) {
                        Thread.sleep(Integer.parseInt(token));
                        continue;
                    }
                }
                catch (InterruptedException e) {
                    return;
                }
                boolean press = !token.startsWith("-");
                boolean tap = !token.startsWith("+") && !token.startsWith("-");
                Key key = Key.ofName(tap ? token : token.substring(1));
                logger.info("Simulating " + (tap ? "" : token.substring(0, 1)) + key);
                keyEvents.accept(press ? new PressKeyEvent(clock().now(), key) :
                        new ReleaseKeyEvent(clock().now(), key));
                if (tap)
                    keyEvents.accept(new ReleaseKeyEvent(clock().now(), key));
            }
            logger.info("Finished simulating keys");
        });
        simulationThread.setName("simulated-keys");
        simulationThread.setDaemon(true);
        simulationThread.start();
        return simulationThread;
    }

}

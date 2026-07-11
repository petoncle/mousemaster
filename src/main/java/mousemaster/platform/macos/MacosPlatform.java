package mousemaster.platform.macos;

import mousemaster.*;
import mousemaster.platform.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class MacosPlatform implements Platform {

    private static final Logger logger = LoggerFactory.getLogger(MacosPlatform.class);

    private final MacosKeyboardController keyboard = new MacosKeyboardController();
    private final MouseController mouse = new UnsupportedMouseController();
    private final Screens screens = new MacosScreens();
    private final Overlay overlay = new UnsupportedOverlay();
    private final UiAutomation uiAutomation = new UnsupportedUiAutomation();
    private final ActiveAppFinder activeAppFinder = new MacosActiveAppFinder();
    private final Console console = new MacosConsole();
    private final KeyRegurgitator keyRegurgitator = new KeyRegurgitator(keyboard);
    // Temporary scaffold clock. Once CGEvent timestamps are handled, replace
    // this with a monotonic clock that anchors native event time to Instant.
    private final Clock clock = Instant::now;

    public MacosPlatform(boolean multipleInstancesAllowed, boolean keyRegurgitationEnabled) {
        logger.info("macOS platform scaffold initialized. Native input and overlay support are not wired yet.");
        if (!multipleInstancesAllowed)
            logger.warn("macOS single-instance locking is not implemented yet");
        if (!keyRegurgitationEnabled)
            logger.warn("macOS key regurgitation disable flag is accepted but keyboard support is not implemented yet");
    }

    @Override
    public void update(double delta) {
        keyboard.update(delta);
    }

    @Override
    public void pumpEvents() {
        QtManager.processEvents();
    }

    @Override
    public void sleep() throws InterruptedException {
        Thread.sleep(10);
    }

    @Override
    public void reset(MouseManager mouseManager, KeyboardManager keyboardManager,
                      ModeMap modeMap, List<MousePositionListener> mousePositionListeners,
                      KeyboardLayout activeKeyboardLayout) {
        keyboard.activeKeyboardLayout(activeKeyboardLayout);
        throw unsupported("macOS native event tap, mouse controller, and overlay are not implemented yet");
    }

    @Override
    public void shutdown() {
        QtManager.stop();
    }

    @Override
    public QtManager.DeploymentStrategy qtDeploymentStrategy() {
        return QtManager.DeploymentStrategy.QTJAMBI_DEFAULT;
    }

    @Override
    public KeyRegurgitator keyRegurgitator() {
        return keyRegurgitator;
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public KeyboardLayout activeKeyboardLayout() {
        return MacosKeyboardLayout.activeKeyboardLayout();
    }

    @Override
    public KeyboardController keyboard() {
        return keyboard;
    }

    @Override
    public MouseController mouse() {
        return mouse;
    }

    @Override
    public Screens screens() {
        return screens;
    }

    @Override
    public Overlay overlay() {
        return overlay;
    }

    @Override
    public UiAutomation uiAutomation() {
        return uiAutomation;
    }

    @Override
    public ActiveAppFinder activeAppFinder() {
        return activeAppFinder;
    }

    @Override
    public Console console() {
        return console;
    }

    @Override
    public void modeChanged(Mode newMode) {
    }

    @Override
    public void modeTimedOut() {
    }

    private static UnsupportedOperationException unsupported(String message) {
        return new UnsupportedOperationException(message);
    }

    private static final class UnsupportedMouseController implements MouseController {
        @Override public void beginMove() { throw unsupported("macOS mouse movement is not implemented yet"); }
        @Override public void endMove() { throw unsupported("macOS mouse movement is not implemented yet"); }
        @Override public void moveBy(boolean xForward, double dx, boolean yForward, double dy) { throw unsupported("macOS mouse movement is not implemented yet"); }
        @Override public void synchronousMoveTo(int x, int y) { throw unsupported("macOS mouse movement is not implemented yet"); }
        @Override public void pressLeft() { throw unsupported("macOS mouse buttons are not implemented yet"); }
        @Override public void pressMiddle() { throw unsupported("macOS mouse buttons are not implemented yet"); }
        @Override public void pressRight() { throw unsupported("macOS mouse buttons are not implemented yet"); }
        @Override public void releaseLeft() { throw unsupported("macOS mouse buttons are not implemented yet"); }
        @Override public void releaseMiddle() { throw unsupported("macOS mouse buttons are not implemented yet"); }
        @Override public void releaseRight() { throw unsupported("macOS mouse buttons are not implemented yet"); }
        @Override public void wheelHorizontallyBy(boolean forward, double delta) { throw unsupported("macOS scroll is not implemented yet"); }
        @Override public void wheelVerticallyBy(boolean forward, double delta) { throw unsupported("macOS scroll is not implemented yet"); }
        @Override public void showCursor() { }
        @Override public void hideCursor() { logger.warn("hide-cursor is unsupported on macOS with public APIs while mousemaster is backgrounded"); }
    }

    private static final class UnsupportedOverlay implements Overlay {
        @Override public void update(double delta) { }
        @Override public void flushCache() { }
        @Override public void setTopmost() { }
        @Override public void setMessagePump(Runnable pump) { }
        @Override public void preWarmFontStyles(Set<HintMeshConfiguration> configs) { }
        @Override public void preWarmHintMeshWindows() { }
        @Override public Rectangle activeWindowRectangle(double widthPct, double heightPct, int topInset, int bottomInset, int leftInset, int rightInset) { throw unsupported("macOS overlay/window geometry is not implemented yet"); }
        @Override public void setIndicator(Indicator indicator, boolean fadeAnimationEnabled, Duration fadeAnimationDuration, boolean allowFade) { throw unsupported("macOS overlay is not implemented yet"); }
        @Override public void hideIndicator(boolean allowFade) { }
        @Override public void setGrid(Grid grid) { throw unsupported("macOS overlay is not implemented yet"); }
        @Override public void hideGrid() { }
        @Override public void setHintMesh(HintMesh hintMesh, Zoom zoom) { throw unsupported("macOS overlay is not implemented yet"); }
        @Override public void setHintMesh(HintMesh hintMesh, Zoom zoom, boolean hintMatch) { throw unsupported("macOS overlay is not implemented yet"); }
        @Override public void hideHintMesh() { }
        @Override public void animateHintMatch(Hint hint) { throw unsupported("macOS overlay is not implemented yet"); }
        @Override public void setZoom(Zoom zoom) { throw unsupported("macOS zoom is not implemented yet"); }
        @Override public void startScreenshotZoomAnimation(Rectangle screenRect, Zoom beginZoom) { throw unsupported("macOS zoom is not implemented yet"); }
        @Override public void updateScreenshotZoom(Zoom zoom) { throw unsupported("macOS zoom is not implemented yet"); }
        @Override public void endScreenshotZoomAnimation(Zoom finalZoom) { throw unsupported("macOS zoom is not implemented yet"); }
        @Override public boolean waitForZoomBeforeRepainting() { return false; }
        @Override public void setWaitForZoomBeforeRepainting(boolean value) { }
    }

    private static final class UnsupportedUiAutomation implements UiAutomation {
        @Override
        public Future<List<UiElement>> startFindInteractiveUiElements() {
            return CompletableFuture.completedFuture(List.of());
        }
    }
}

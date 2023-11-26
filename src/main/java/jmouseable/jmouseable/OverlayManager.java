package jmouseable.jmouseable;

public class OverlayManager {

    private final ModeManager modeManager;
    private final MouseManager mouseManager;
    private final KeyboardManager keyboardManager;
    private double enforceTopmostTimer;

    public OverlayManager(ModeManager modeManager, MouseManager mouseManager,
                          KeyboardManager keyboardManager) {
        this.modeManager = modeManager;
        this.mouseManager = mouseManager;
        this.keyboardManager = keyboardManager;
    }

    public void update(double delta) {
        if (modeManager.currentMode().indicator().enabled())
            WindowsOverlay.setIndicatorColor(indicatorHexColor());
        else
            WindowsOverlay.hideIndicator();
        if (modeManager.currentMode().grid().visible())
            WindowsOverlay.setGrid(modeManager.currentMode().grid());
        else
            WindowsOverlay.hideGrid();
        WindowsOverlay.setMousePosition(mouseManager.mouseX(), mouseManager.mouseY());
        enforceTopmostTimer -= delta;
        if (enforceTopmostTimer < 0) {
            // Every 200ms.
            enforceTopmostTimer = 0.2;
            WindowsOverlay.setTopmost();
        }
    }

    private String indicatorHexColor() {
        Indicator indicator = modeManager.currentMode().indicator();
        if (keyboardManager.pressingNonHandledKey() && indicator.nonComboKeyPressHexColor() != null)
            return indicator.nonComboKeyPressHexColor();
        if (mouseManager.pressing() && indicator.mousePressHexColor() != null)
            return indicator.mousePressHexColor();
        if (mouseManager.wheeling() && indicator.wheelHexColor() != null)
            return indicator.wheelHexColor();
        if (mouseManager.moving() && indicator.moveHexColor() != null)
            return indicator.moveHexColor();
        return indicator.idleHexColor();
    }

}

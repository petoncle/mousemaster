package jmouseable.jmouseable;

public class IndicatorManager {

    private final ModeManager modeManager;
    private final MouseManager mouseManager;
    private final KeyboardManager keyboardManager;

    public IndicatorManager(ModeManager modeManager, MouseManager mouseManager,
                            KeyboardManager keyboardManager) {
        this.modeManager = modeManager;
        this.mouseManager = mouseManager;
        this.keyboardManager = keyboardManager;
    }

    public void update(double delta) {
        if (modeManager.currentMode().indicator().enabled())
            WindowsIndicator.show(indicatorHexColor());
        else
            WindowsIndicator.hide();
        WindowsIndicator.mousePosition(mouseManager.mouseX(), mouseManager.mouseY());
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

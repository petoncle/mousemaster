package jmouseable.jmouseable;

public record Mode(String name, ComboMap comboMap, Mouse mouse, Wheel wheel,
                   Attach attach, ModeTimeout timeout, Indicator indicator) {

    public static final String NORMAL_MODE_NAME = "normal-mode";

}

package jmouseable.jmouseable;

public record Mode(String name, ComboMap comboMap, Mouse mouse, Wheel wheel,
                   ModeTimeout timeout) {

    public static final String NORMAL_MODE_NAME = "normal-mode";

}

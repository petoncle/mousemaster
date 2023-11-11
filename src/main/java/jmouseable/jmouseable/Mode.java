package jmouseable.jmouseable;

public record Mode(String name, ComboMap comboMap, Mouse mouse, Wheel wheel) {

    public static final String NORMAL_MODE_NAME = "normal-mode";

}

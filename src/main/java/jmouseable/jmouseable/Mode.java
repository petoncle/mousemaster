package jmouseable.jmouseable;

public record Mode(String name, ComboMap comboMap, Mouse mouse, Wheel wheel) {

    public static final String DEFAULT_MODE_NAME = "default-mode";

}

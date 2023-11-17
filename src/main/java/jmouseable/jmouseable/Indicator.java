package jmouseable.jmouseable;

public record Indicator(boolean enabled, String idleHexColor, String moveHexColor,
                        String wheelHexColor, String mousePressHexColor,
                        String nonComboKeyPressHexColor) {
}

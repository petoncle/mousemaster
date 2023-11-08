package jmouseable.jmouseable;

import java.util.List;

public record ComboPreparation(List<KeyEvent> events) {

    public static ComboPreparation empty() {
        return new ComboPreparation(List.of());
    }

}

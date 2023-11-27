package jmouseable.jmouseable;

public sealed interface GridType {

    record FullScreen() implements GridType {
    }

    record ActiveWindow() implements GridType {
    }

    record AroundMouse(int width, int height) implements GridType {
    }

}

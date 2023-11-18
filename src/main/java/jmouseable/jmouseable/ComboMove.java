package jmouseable.jmouseable;

public record ComboMove(KeyAction action, ComboMoveDuration duration,
                        boolean eventMustBeEaten) {
    @Override
    public String toString() {
        return (eventMustBeEaten ? "" : ";") + action;
    }
}

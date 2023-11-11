package jmouseable.jmouseable;

import java.time.Duration;

public record ComboMove(KeyAction action, Duration breakingTimeout, boolean eventMustBeEaten) {
}

package jmouseable.jmouseable;

import java.time.Instant;

public record KeyEvent(Instant time, KeyAction action) {
}

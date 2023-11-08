package jmouseable.jmouseable;

public sealed interface Command {

    record ChangeMode(Mode newMode) implements Command {}

    record StartMoveUp() implements Command {}

    record StartMoveDown() implements Command {}

    record StartMoveLeft() implements Command {}

    record StartMoveRight() implements Command {}

    record StopMoveUp() implements Command {}

    record StopMoveDown() implements Command {}

    record StopMoveLeft() implements Command {}

    record StopMoveRight() implements Command {}

}

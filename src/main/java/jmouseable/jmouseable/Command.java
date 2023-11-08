package jmouseable.jmouseable;

public sealed interface Command {

    record ChangeMode(Mode newMode) implements Command {
    }

    record MoveUp() implements Command {

    }

    record MoveDown() implements Command {

    }

    record MoveLeft() implements Command {

    }

    record MoveRight() implements Command {

    }

}

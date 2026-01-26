package mousemaster;

public record MacroMove(Key key, boolean press) {

    @Override
    public String toString() {
        return (press ? "+" : "-") + key.name();
    }

}

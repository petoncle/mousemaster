package mousemaster;

public record RemappingMove(Key key, boolean press) {

    @Override
    public String toString() {
        return (press ? "+" : "-") + key.name();
    }

}

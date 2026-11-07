package mousemaster;

public interface ModeListener {

    void modeChanged(Mode newMode);

    default void combosRan() {
    }

    void modeTimedOut();

}

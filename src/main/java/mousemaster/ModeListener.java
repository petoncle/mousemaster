package mousemaster;

public interface ModeListener {

    void modeChanged(Mode newMode);

    void modeTimedOut();

}

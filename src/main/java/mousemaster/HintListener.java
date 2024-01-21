package mousemaster;

public interface HintListener {

    /**
     * Only called if the mode is about to be changed by hint.mode-after-selection.
     */
    void hintSelected();

}

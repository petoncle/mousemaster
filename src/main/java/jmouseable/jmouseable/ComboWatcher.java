package jmouseable.jmouseable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComboWatcher {

    private static final Logger logger = LoggerFactory.getLogger(ComboWatcher.class);

    private final ModeMap modeMap;
    private ComboMap currentComboMap;
    private ComboPreparation comboPreparation;

    public ComboWatcher(ModeMap modeMap) {
        this.modeMap = modeMap;
        this.currentComboMap = modeMap.get(Mode.defaultMode());
        this.comboPreparation = ComboPreparation.empty();
    }

    /**
     * @return true if the event should be eaten.
     */
    public boolean keyEvent(KeyEvent keyEvent) {
        return false;
    }

}

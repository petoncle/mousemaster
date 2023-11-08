package jmouseable.jmouseable;

public class ComboWatcher {

    private final ModeMap modeMap;
    private ComboMap currentComboMap;
    private ComboPreparation comboPreparation;

    public ComboWatcher(ModeMap modeMap) {
        this.modeMap = modeMap;
        this.currentComboMap = modeMap.get(Mode.defaultMode());
        this.comboPreparation = ComboPreparation.empty();
    }

    public void keyEvent(KeyEvent keyEvent) {

    }

}

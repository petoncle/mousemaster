package jmouseable.jmouseable;

import jmouseable.jmouseable.ComboMap.ComboMapBuilder;
import jmouseable.jmouseable.GridConfiguration.GridConfigurationBuilder;
import jmouseable.jmouseable.HideCursor.HideCursorBuilder;
import jmouseable.jmouseable.HintMeshConfiguration.HintMeshConfigurationBuilder;
import jmouseable.jmouseable.IndicatorConfiguration.IndicatorConfigurationBuilder;
import jmouseable.jmouseable.ModeTimeout.ModeTimeoutBuilder;
import jmouseable.jmouseable.Mouse.MouseBuilder;
import jmouseable.jmouseable.Wheel.WheelBuilder;

public record Mode(String name, boolean pushModeToHistoryStack, ComboMap comboMap,
                   Mouse mouse, Wheel wheel, GridConfiguration grid,
                   HintMeshConfiguration hintMesh, ModeTimeout timeout,
                   IndicatorConfiguration indicator, HideCursor hideCursor) {
    public static final String IDLE_MODE_NAME = "idle-mode";
    public static final String PREVIOUS_MODE_FROM_HISTORY_STACK_IDENTIFIER =
            "previous-mode-from-history-stack";

    public static class ModeBuilder {
        private String name;
        private boolean pushModeToHistoryStack;
        private ComboMapBuilder comboMap = new ComboMapBuilder();
        private MouseBuilder mouse = new MouseBuilder();
        private WheelBuilder wheel = new WheelBuilder();
        private GridConfigurationBuilder grid = new GridConfigurationBuilder();
        private HintMeshConfigurationBuilder hintMesh =
                new HintMeshConfigurationBuilder();
        private ModeTimeoutBuilder timeout = new ModeTimeoutBuilder();
        private IndicatorConfigurationBuilder
                indicator = new IndicatorConfigurationBuilder();
        private HideCursorBuilder hideCursor = new HideCursorBuilder();

        public ModeBuilder(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }
        public ModeBuilder pushModeToHistoryStack(boolean pushModeToHistoryStack) {
            this.pushModeToHistoryStack = pushModeToHistoryStack;
            return this;
        }

        public ComboMapBuilder comboMap() {
            return comboMap;
        }

        public MouseBuilder mouse() {
            return mouse;
        }

        public WheelBuilder wheel() {
            return wheel;
        }

        public GridConfigurationBuilder grid() {
            return grid;
        }

        public HintMeshConfigurationBuilder hintMesh() {
            return hintMesh;
        }

        public ModeTimeoutBuilder timeout() {
            return timeout;
        }

        public IndicatorConfigurationBuilder indicator() {
            return indicator;
        }

        public HideCursorBuilder hideCursor() {
            return hideCursor;
        }

        public Mode build() {
            return new Mode(name, pushModeToHistoryStack, comboMap.build(), mouse.build(),
                    wheel.build(), grid.build(), hintMesh.build(), timeout.build(),
                    indicator.build(), hideCursor.build());
        }

    }

}

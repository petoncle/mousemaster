package jmouseable.jmouseable;

import jmouseable.jmouseable.ComboMap.ComboMapBuilder;
import jmouseable.jmouseable.GridConfiguration.GridBuilder;
import jmouseable.jmouseable.HideCursor.HideCursorBuilder;
import jmouseable.jmouseable.Indicator.IndicatorBuilder;
import jmouseable.jmouseable.ModeTimeout.ModeTimeoutBuilder;
import jmouseable.jmouseable.Mouse.MouseBuilder;
import jmouseable.jmouseable.Wheel.WheelBuilder;

public record Mode(String name, boolean pauseComboProcessingWhenModeActivated,
                   ComboMap comboMap, Mouse mouse, Wheel wheel,
                   GridConfiguration gridConfiguration, ModeTimeout timeout,
                   Indicator indicator, HideCursor hideCursor) {
    public static final String NORMAL_MODE_NAME = "normal-mode";

    public static class ModeBuilder {
        private String name;
        private boolean pauseComboProcessingWhenModeActivated = false;
        private ComboMapBuilder comboMap = new ComboMapBuilder();
        private MouseBuilder mouse = new MouseBuilder();
        private WheelBuilder wheel = new WheelBuilder();
        private GridBuilder grid = new GridBuilder();
        private ModeTimeoutBuilder timeout = new ModeTimeoutBuilder();
        private IndicatorBuilder indicator = new IndicatorBuilder();
        private HideCursorBuilder hideCursor = new HideCursorBuilder();

        public ModeBuilder(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        public ModeBuilder pauseComboProcessingWhenModeActivated(
                boolean pauseComboProcessingWhenModeActivated) {
            this.pauseComboProcessingWhenModeActivated = pauseComboProcessingWhenModeActivated;
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

        public GridBuilder grid() {
            return grid;
        }

        public ModeTimeoutBuilder timeout() {
            return timeout;
        }

        public IndicatorBuilder indicator() {
            return indicator;
        }

        public HideCursorBuilder hideCursor() {
            return hideCursor;
        }

        public Mode build() {
            return new Mode(name, pauseComboProcessingWhenModeActivated, comboMap.build(),
                    mouse.build(), wheel.build(), grid.build(), timeout.build(),
                    indicator.build(), hideCursor.build());
        }

    }

}

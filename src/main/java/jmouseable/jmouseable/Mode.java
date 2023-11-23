package jmouseable.jmouseable;

import jmouseable.jmouseable.Attach.AttachBuilder;
import jmouseable.jmouseable.ComboMap.ComboMapBuilder;
import jmouseable.jmouseable.Indicator.IndicatorBuilder;
import jmouseable.jmouseable.ModeTimeout.ModeTimeoutBuilder;
import jmouseable.jmouseable.Mouse.MouseBuilder;
import jmouseable.jmouseable.Wheel.WheelBuilder;

public record Mode(String name, ComboMap comboMap, Mouse mouse, Wheel wheel,
                   Attach attach, ModeTimeout timeout, Indicator indicator) {
    public static final String NORMAL_MODE_NAME = "normal-mode";

    public static class ModeBuilder {
        private String name;
        private ComboMapBuilder comboMap = new ComboMapBuilder();
        private MouseBuilder mouse = new MouseBuilder();
        private WheelBuilder wheel = new WheelBuilder();
        private AttachBuilder attach = new AttachBuilder();
        private ModeTimeoutBuilder timeout = new ModeTimeoutBuilder();
        private IndicatorBuilder indicator = new IndicatorBuilder();

        public ModeBuilder(String name) {
            this.name = name;
        }

        public String name() {
            return name;
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

        public AttachBuilder attach() {
            return attach;
        }

        public ModeTimeoutBuilder timeout() {
            return timeout;
        }

        public IndicatorBuilder indicator() {
            return indicator;
        }

        public Mode build() {
            return new Mode(name, comboMap.build(), mouse.build(), wheel.build(),
                    attach.build(), timeout.build(), indicator.build());
        }

    }

}

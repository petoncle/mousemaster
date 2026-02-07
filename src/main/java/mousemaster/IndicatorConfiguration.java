package mousemaster;

import mousemaster.Indicator.IndicatorBuilder;

public record IndicatorConfiguration(boolean enabled,
                                     Indicator idleIndicator,
                                     Indicator moveIndicator,
                                     Indicator wheelIndicator,
                                     Indicator mousePressIndicator,
                                     Indicator leftMousePressIndicator,
                                     Indicator middleMousePressIndicator,
                                     Indicator rightMousePressIndicator,
                                     Indicator unhandledKeyPressIndicator) {

    public static class IndicatorConfigurationBuilder {
        private Boolean enabled;
        private IndicatorBuilder idleIndicator = new IndicatorBuilder();
        private IndicatorBuilder moveIndicator = new IndicatorBuilder();
        private IndicatorBuilder wheelIndicator = new IndicatorBuilder();
        private IndicatorBuilder mousePressIndicator = new IndicatorBuilder();
        private IndicatorBuilder leftMousePressIndicator = new IndicatorBuilder();
        private IndicatorBuilder middleMousePressIndicator = new IndicatorBuilder();
        private IndicatorBuilder rightMousePressIndicator = new IndicatorBuilder();
        private IndicatorBuilder unhandledKeyPressIndicator = new IndicatorBuilder();

        public IndicatorConfigurationBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Boolean enabled() {
            return enabled;
        }

        public IndicatorBuilder idleIndicator() {
            return idleIndicator;
        }

        public IndicatorBuilder moveIndicator() {
            return moveIndicator;
        }

        public IndicatorBuilder wheelIndicator() {
            return wheelIndicator;
        }

        public IndicatorBuilder mousePressIndicator() {
            return mousePressIndicator;
        }

        public IndicatorBuilder leftMousePressIndicator() {
            return leftMousePressIndicator;
        }

        public IndicatorBuilder middleMousePressIndicator() {
            return middleMousePressIndicator;
        }

        public IndicatorBuilder rightMousePressIndicator() {
            return rightMousePressIndicator;
        }

        public IndicatorBuilder unhandledKeyPressIndicator() {
            return unhandledKeyPressIndicator;
        }

        public void extend(IndicatorConfigurationBuilder parent) {
            if (enabled == null) enabled = parent.enabled;
            idleIndicator.extend(parent.idleIndicator);
            moveIndicator.extend(parent.moveIndicator);
            wheelIndicator.extend(parent.wheelIndicator);
            mousePressIndicator.extend(parent.mousePressIndicator);
            leftMousePressIndicator.extend(parent.leftMousePressIndicator);
            middleMousePressIndicator.extend(parent.middleMousePressIndicator);
            rightMousePressIndicator.extend(parent.rightMousePressIndicator);
            unhandledKeyPressIndicator.extend(parent.unhandledKeyPressIndicator);
        }

        public IndicatorConfiguration build() {
            // State inheritance: non-idle states inherit unset fields from idle.
            // left/middle/right-mouse-press inherit from mouse-press first.
            mousePressIndicator.extend(idleIndicator);
            moveIndicator.extend(idleIndicator);
            wheelIndicator.extend(idleIndicator);
            unhandledKeyPressIndicator.extend(idleIndicator);
            leftMousePressIndicator.extend(mousePressIndicator);
            middleMousePressIndicator.extend(mousePressIndicator);
            rightMousePressIndicator.extend(mousePressIndicator);
            return new IndicatorConfiguration(enabled,
                    idleIndicator.build(),
                    moveIndicator.build(),
                    wheelIndicator.build(),
                    mousePressIndicator.build(),
                    leftMousePressIndicator.build(),
                    middleMousePressIndicator.build(),
                    rightMousePressIndicator.build(),
                    unhandledKeyPressIndicator.build());
        }
    }
}

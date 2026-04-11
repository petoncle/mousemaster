package mousemaster;

import mousemaster.Indicator.IndicatorBuilder;

import java.time.Duration;
import java.util.List;

public record IndicatorConfiguration(boolean enabled,
                                     boolean fadeAnimationEnabled,
                                     Duration fadeAnimationDuration,
                                     Indicator idleIndicator,
                                     Indicator moveIndicator,
                                     Indicator wheelIndicator,
                                     Indicator mousePressIndicator,
                                     Indicator leftMousePressIndicator,
                                     Indicator middleMousePressIndicator,
                                     Indicator rightMousePressIndicator,
                                     Indicator unhandledKeyPressIndicator) {

    public static class IndicatorConfigurationBuilder {

        /**
         * Cascade relationships: target extends source.
         * Order matters: idle → mousePress must come before mousePress → left/middle/right.
         * Used by both build() (value cascading) and ConfigurationParser (mutation cascading).
         */
        static final List<CascadeRule> CASCADE_RULES = List.of(
                new CascadeRule(List.of("idleIndicator"), List.of("mousePressIndicator")),
                new CascadeRule(List.of("idleIndicator"), List.of("moveIndicator")),
                new CascadeRule(List.of("idleIndicator"), List.of("wheelIndicator")),
                new CascadeRule(List.of("idleIndicator"), List.of("unhandledKeyPressIndicator")),
                new CascadeRule(List.of("mousePressIndicator"), List.of("leftMousePressIndicator")),
                new CascadeRule(List.of("mousePressIndicator"), List.of("middleMousePressIndicator")),
                new CascadeRule(List.of("mousePressIndicator"), List.of("rightMousePressIndicator"))
        );

        private Boolean enabled;
        private Boolean fadeAnimationEnabled;
        private Duration fadeAnimationDuration;
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

        public IndicatorConfigurationBuilder fadeAnimationEnabled(boolean fadeAnimationEnabled) {
            this.fadeAnimationEnabled = fadeAnimationEnabled;
            return this;
        }

        public Boolean fadeAnimationEnabled() {
            return fadeAnimationEnabled;
        }

        public IndicatorConfigurationBuilder fadeAnimationDuration(Duration fadeAnimationDuration) {
            this.fadeAnimationDuration = fadeAnimationDuration;
            return this;
        }

        public Duration fadeAnimationDuration() {
            return fadeAnimationDuration;
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
            if (fadeAnimationEnabled == null) fadeAnimationEnabled = parent.fadeAnimationEnabled;
            if (fadeAnimationDuration == null) fadeAnimationDuration = parent.fadeAnimationDuration;
            idleIndicator.extend(parent.idleIndicator);
            moveIndicator.extend(parent.moveIndicator);
            wheelIndicator.extend(parent.wheelIndicator);
            mousePressIndicator.extend(parent.mousePressIndicator);
            leftMousePressIndicator.extend(parent.leftMousePressIndicator);
            middleMousePressIndicator.extend(parent.middleMousePressIndicator);
            rightMousePressIndicator.extend(parent.rightMousePressIndicator);
            unhandledKeyPressIndicator.extend(parent.unhandledKeyPressIndicator);
        }

        private IndicatorBuilder builderByName(String name) {
            return switch (name) {
                case "idleIndicator" -> idleIndicator;
                case "moveIndicator" -> moveIndicator;
                case "wheelIndicator" -> wheelIndicator;
                case "mousePressIndicator" -> mousePressIndicator;
                case "leftMousePressIndicator" -> leftMousePressIndicator;
                case "middleMousePressIndicator" -> middleMousePressIndicator;
                case "rightMousePressIndicator" -> rightMousePressIndicator;
                case "unhandledKeyPressIndicator" -> unhandledKeyPressIndicator;
                default -> throw new IllegalArgumentException(name);
            };
        }

        public IndicatorConfiguration build() {
            for (CascadeRule rule : CASCADE_RULES)
                builderByName(rule.targetFieldNames().getFirst())
                        .extend(builderByName(rule.sourceFieldNames().getFirst()));
            return new IndicatorConfiguration(enabled,
                    fadeAnimationEnabled,
                    fadeAnimationDuration,
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

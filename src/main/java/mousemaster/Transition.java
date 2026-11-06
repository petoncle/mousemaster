package mousemaster;

import java.time.Duration;

public record Transition(boolean enabled, Duration duration, Easing easing) {

    public static class TransitionBuilder {

        private Boolean enabled;
        private Duration duration;
        private Easing easing;

        public TransitionBuilder() {
        }

        public TransitionBuilder(Transition transition) {
            this.enabled = transition.enabled;
            this.duration = transition.duration;
            this.easing = transition.easing;
        }

        public Boolean enabled() {
            return enabled;
        }

        public Duration duration() {
            return duration;
        }

        public Easing easing() {
            return easing;
        }

        public TransitionBuilder enabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public TransitionBuilder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public TransitionBuilder easing(Easing easing) {
            this.easing = easing;
            return this;
        }

        public void extend(TransitionBuilder parent) {
            if (enabled == null) enabled = parent.enabled;
            if (duration == null) duration = parent.duration;
            if (easing == null) easing = parent.easing;
        }

        public Transition build() {
            return new Transition(enabled, duration, easing);
        }

    }

}

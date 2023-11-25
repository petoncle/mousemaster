package jmouseable.jmouseable;

import java.time.Duration;

public record ModeTimeout(boolean enabled, Duration idleDuration, String nextModeName) {

    public static class ModeTimeoutBuilder {
        private Boolean enabled;
        private Duration idleDuration;
        private String nextModeName;

        public ModeTimeoutBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public ModeTimeoutBuilder idleDuration(Duration idleDuration) {
            this.idleDuration = idleDuration;
            return this;
        }

        public ModeTimeoutBuilder nextModeName(String nextModeName) {
            this.nextModeName = nextModeName;
            return this;
        }

        public Boolean enabled() {
            return enabled;
        }

        public Duration idleDuration() {
            return idleDuration;
        }

        public String nextModeName() {
            return nextModeName;
        }

        public ModeTimeout build() {
            return new ModeTimeout(enabled, idleDuration, nextModeName);
        }
    }

}

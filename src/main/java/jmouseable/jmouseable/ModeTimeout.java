package jmouseable.jmouseable;

import java.time.Duration;

public record ModeTimeout(boolean enabled, Duration idleDuration, String modeName) {

    public static class ModeTimeoutBuilder {
        private Boolean enabled;
        private Duration idleDuration;
        private String modeName;

        public ModeTimeoutBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public ModeTimeoutBuilder idleDuration(Duration idleDuration) {
            this.idleDuration = idleDuration;
            return this;
        }

        public ModeTimeoutBuilder modeName(String modeName) {
            this.modeName = modeName;
            return this;
        }

        public Boolean enabled() {
            return enabled;
        }

        public Duration idleDuration() {
            return idleDuration;
        }

        public String modeName() {
            return modeName;
        }

        public ModeTimeout build() {
            return new ModeTimeout(enabled, idleDuration, modeName);
        }
    }

}

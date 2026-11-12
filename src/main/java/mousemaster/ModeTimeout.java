package mousemaster;

import java.time.Duration;

public record ModeTimeout(boolean enabled, Duration duration, String modeName,
                          boolean onlyIfIdle) {

    public static class ModeTimeoutBuilder {
        private Boolean enabled;
        private Duration duration;
        private String modeName;
        private Boolean onlyIfIdle;

        public ModeTimeoutBuilder enabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public ModeTimeoutBuilder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public ModeTimeoutBuilder modeName(String modeName) {
            this.modeName = modeName;
            return this;
        }

        public ModeTimeoutBuilder onlyIfIdle(Boolean onlyIfIdle) {
            this.onlyIfIdle = onlyIfIdle;
            return this;
        }

        public Boolean enabled() {
            return enabled;
        }
        public Duration duration() {
            return duration;
        }

        public String modeName() {
            return modeName;
        }

        public Boolean onlyIfIdle() {
            return onlyIfIdle;
        }

        public ModeTimeout build() {
            return new ModeTimeout(enabled, duration, modeName, onlyIfIdle);
        }
    }

}

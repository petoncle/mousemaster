package jmouseable.jmouseable;

import java.time.Duration;

public record HideCursor(boolean enabled, Duration idleDuration) {

    public static class HideCursorBuilder {
        private Boolean enabled;
        private Duration idleDuration;

        public HideCursorBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public HideCursorBuilder idleDuration(Duration idleDuration) {
            this.idleDuration = idleDuration;
            return this;
        }

        public Boolean enabled() {
            return enabled;
        }

        public Duration idleDuration() {
            return idleDuration;
        }

        public HideCursor build() {
            return new HideCursor(enabled, idleDuration);
        }
    }

}

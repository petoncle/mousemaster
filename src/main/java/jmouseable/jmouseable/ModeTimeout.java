package jmouseable.jmouseable;

import java.time.Duration;

public record ModeTimeout(Duration idleDuration, String nextModeName) {

    public static class ModeTimeoutBuilder {
        private Duration idleDuration;
        private String nextModeName;

        public ModeTimeoutBuilder idleDuration(Duration idleDuration) {
            this.idleDuration = idleDuration;
            return this;
        }

        public ModeTimeoutBuilder nextModeName(String nextModeName) {
            this.nextModeName = nextModeName;
            return this;
        }

        public Duration idleDuration() {
            return idleDuration;
        }

        public String nextModeName() {
            return nextModeName;
        }

        public ModeTimeout build() {
            return new ModeTimeout(idleDuration, nextModeName);
        }
    }


}

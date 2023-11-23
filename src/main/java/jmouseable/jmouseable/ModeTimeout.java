package jmouseable.jmouseable;

import java.time.Duration;

public record ModeTimeout(Duration duration, String nextModeName) {

    public static class ModeTimeoutBuilder {
        private Duration duration;
        private String nextModeName;

        public ModeTimeoutBuilder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public ModeTimeoutBuilder nextModeName(String nextModeName) {
            this.nextModeName = nextModeName;
            return this;
        }

        public Duration duration() {
            return duration;
        }

        public String nextModeName() {
            return nextModeName;
        }

        public ModeTimeout build() {
            return new ModeTimeout(duration, nextModeName);
        }
    }


}

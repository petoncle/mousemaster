package mousemaster;

import java.time.Duration;

public record ModeTimeout(boolean enabled, Duration idleDuration, String modeName,
                          boolean buttonPressCountsAsActivity) {

    public static class ModeTimeoutBuilder {
        private Boolean enabled;
        private Duration idleDuration;
        private String modeName;
        private Boolean buttonPressCountsAsActivity;

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

        public ModeTimeoutBuilder buttonPressCountsAsActivity(boolean buttonPressCountsAsActivity) {
            this.buttonPressCountsAsActivity = buttonPressCountsAsActivity;
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

        public Boolean buttonPressCountsAsActivity() {
            return buttonPressCountsAsActivity;
        }

        public ModeTimeout build() {
            return new ModeTimeout(enabled, idleDuration, modeName, buttonPressCountsAsActivity);
        }
    }

}

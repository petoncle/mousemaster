package mousemaster;

import mousemaster.VelocityConfiguration.VelocityConfigurationBuilder;

public record Mouse(VelocityConfiguration velocity,
                    boolean smoothJumpEnabled, double smoothJumpVelocity) {

    public static class MouseBuilder {

        private final VelocityConfigurationBuilder velocity = new VelocityConfigurationBuilder();
        private Boolean smoothJumpEnabled;
        private Double smoothJumpVelocity;

        public VelocityConfigurationBuilder velocity() {
            return velocity;
        }

        public MouseBuilder smoothJumpEnabled(boolean smoothJumpEnabled) {
            this.smoothJumpEnabled = smoothJumpEnabled;
            return this;
        }

        public MouseBuilder smoothJumpVelocity(double smoothJumpVelocity) {
            this.smoothJumpVelocity = smoothJumpVelocity;
            return this;
        }

        public Double smoothJumpVelocity() {
            return smoothJumpVelocity;
        }

        public Boolean smoothJumpEnabled() {
            return smoothJumpEnabled;
        }

        public Mouse build() {
            return new Mouse(velocity.build(), smoothJumpEnabled, smoothJumpVelocity);
        }
    }
}

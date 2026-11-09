package mousemaster;

import mousemaster.VelocityConfiguration.VelocityConfigurationBuilder;

public record MovementConfiguration(VelocityConfiguration velocity,
                                    boolean smoothJumpEnabled, double smoothJumpVelocity,
                                    double nudgeDistance) {

    public static class MovementConfigurationBuilder {

        private final VelocityConfigurationBuilder velocity = new VelocityConfigurationBuilder();
        private Boolean smoothJumpEnabled;
        private Double smoothJumpVelocity;
        private Double nudgeDistance;

        public VelocityConfigurationBuilder velocity() {
            return velocity;
        }

        public MovementConfigurationBuilder smoothJumpEnabled(boolean smoothJumpEnabled) {
            this.smoothJumpEnabled = smoothJumpEnabled;
            return this;
        }

        public MovementConfigurationBuilder smoothJumpVelocity(double smoothJumpVelocity) {
            this.smoothJumpVelocity = smoothJumpVelocity;
            return this;
        }

        public MovementConfigurationBuilder nudgeDistance(double nudgeDistance) {
            this.nudgeDistance = nudgeDistance;
            return this;
        }

        public Double smoothJumpVelocity() {
            return smoothJumpVelocity;
        }

        public Boolean smoothJumpEnabled() {
            return smoothJumpEnabled;
        }

        public Double nudgeDistance() {
            return nudgeDistance;
        }

        public MovementConfiguration build() {
            return new MovementConfiguration(velocity.build(), smoothJumpEnabled,
                    smoothJumpVelocity, nudgeDistance);
        }
    }
}

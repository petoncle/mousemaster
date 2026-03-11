package mousemaster;

public record Mouse(double initialVelocity, double maxVelocity, double acceleration,
                    double accelerationCurve, double deceleration,
                    boolean smoothJumpEnabled, double smoothJumpVelocity) {


    public static class MouseBuilder {

        private Double initialVelocity;
        private Double maxVelocity;
        private Double acceleration;
        private Double accelerationCurve;
        private Double deceleration;
        private Boolean smoothJumpEnabled;
        private Double smoothJumpVelocity;

        public MouseBuilder initialVelocity(double initialVelocity) {
            this.initialVelocity = initialVelocity;
            return this;
        }

        public MouseBuilder maxVelocity(double maxVelocity) {
            this.maxVelocity = maxVelocity;
            return this;
        }

        public MouseBuilder acceleration(double acceleration) {
            this.acceleration = acceleration;
            return this;
        }

        public MouseBuilder accelerationCurve(double accelerationCurve) {
            this.accelerationCurve = accelerationCurve;
            return this;
        }

        public MouseBuilder deceleration(double deceleration) {
            this.deceleration = deceleration;
            return this;
        }

        public MouseBuilder smoothJumpEnabled(boolean smoothJumpEnabled) {
            this.smoothJumpEnabled = smoothJumpEnabled;
            return this;
        }

        public MouseBuilder smoothJumpVelocity(double smoothJumpVelocity) {
            this.smoothJumpVelocity = smoothJumpVelocity;
            return this;
        }

        public Double initialVelocity() {
            return initialVelocity;
        }

        public Double maxVelocity() {
            return maxVelocity;
        }

        public Double acceleration() {
            return acceleration;
        }

        public Double accelerationCurve() {
            return accelerationCurve;
        }

        public Double deceleration() {
            return deceleration;
        }

        public Double smoothJumpVelocity() {
            return smoothJumpVelocity;
        }

        public Boolean smoothJumpEnabled() {
            return smoothJumpEnabled;
        }

        public Mouse build() {
            return new Mouse(initialVelocity, maxVelocity, acceleration,
                    accelerationCurve, deceleration,
                    smoothJumpEnabled, smoothJumpVelocity);
        }
    }
}

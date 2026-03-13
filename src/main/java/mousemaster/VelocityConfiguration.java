package mousemaster;

public record VelocityConfiguration(double initialVelocity, double maxVelocity,
                                    double acceleration, Easing accelerationEasing,
                                    double deceleration) {

    public static class VelocityConfigurationBuilder {
        private Double initialVelocity;
        private Double maxVelocity;
        private Double acceleration;
        private Easing accelerationEasing;
        private Double deceleration;

        public VelocityConfigurationBuilder initialVelocity(double initialVelocity) {
            this.initialVelocity = initialVelocity;
            return this;
        }

        public VelocityConfigurationBuilder maxVelocity(double maxVelocity) {
            this.maxVelocity = maxVelocity;
            return this;
        }

        public VelocityConfigurationBuilder acceleration(double acceleration) {
            this.acceleration = acceleration;
            return this;
        }

        public VelocityConfigurationBuilder accelerationEasing(Easing accelerationEasing) {
            this.accelerationEasing = accelerationEasing;
            return this;
        }

        public VelocityConfigurationBuilder deceleration(double deceleration) {
            this.deceleration = deceleration;
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

        public Easing accelerationEasing() {
            return accelerationEasing;
        }

        public Double deceleration() {
            return deceleration;
        }

        public VelocityConfiguration build() {
            return new VelocityConfiguration(initialVelocity, maxVelocity, acceleration,
                    accelerationEasing, deceleration);
        }
    }
}

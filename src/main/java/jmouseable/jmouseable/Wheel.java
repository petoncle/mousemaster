package jmouseable.jmouseable;

public record Wheel(double initialVelocity, double maxVelocity, double acceleration) {

    public static class WheelBuilder {
        private Double initialVelocity;
        private Double maxVelocity;
        private Double acceleration;

        public WheelBuilder initialVelocity(double initialVelocity) {
            this.initialVelocity = initialVelocity;
            return this;
        }

        public WheelBuilder maxVelocity(double maxVelocity) {
            this.maxVelocity = maxVelocity;
            return this;
        }

        public WheelBuilder acceleration(double acceleration) {
            this.acceleration = acceleration;
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

        public Wheel build() {
            return new Wheel(initialVelocity, maxVelocity, acceleration);
        }
    }

}

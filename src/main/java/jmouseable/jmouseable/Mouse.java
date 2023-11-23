package jmouseable.jmouseable;

public record Mouse(double initialVelocity, double maxVelocity, double acceleration) {

    public static class MouseBuilder {

        private Double initialVelocity;
        private Double maxVelocity;
        private Double acceleration;

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

        public Double initialVelocity() {
            return initialVelocity;
        }

        public Double maxVelocity() {
            return maxVelocity;
        }

        public Double acceleration() {
            return acceleration;
        }

        public Mouse build() {
            return new Mouse(initialVelocity, maxVelocity, acceleration);
        }
    }
}

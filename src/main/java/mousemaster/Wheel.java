package mousemaster;

public record Wheel(VelocityConfiguration velocity) {

    public static class WheelBuilder {

        private final VelocityConfiguration.VelocityConfigurationBuilder velocity =
                new VelocityConfiguration.VelocityConfigurationBuilder();

        public VelocityConfiguration.VelocityConfigurationBuilder velocity() {
            return velocity;
        }

        public Wheel build() {
            return new Wheel(velocity.build());
        }
    }

}

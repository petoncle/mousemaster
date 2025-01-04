package mousemaster;

public record ZoomConfiguration(double percent, ZoomCenter center) {
    public static class ZoomConfigurationBuilder {
        private Double percent;
        private ZoomCenter center;

        public ZoomConfigurationBuilder percent(double percent) {
            this.percent = percent;
            return this;
        }

        public ZoomConfigurationBuilder center(ZoomCenter center) {
            this.center = center;
            return this;
        }

        public Double percent() {
            return percent;
        }

        public ZoomCenter center() {
            return center;
        }

        public ZoomConfiguration build() {
            return new ZoomConfiguration(percent, center);
        }

    }
}

package mousemaster;

public record ZoomConfiguration(double percent, ZoomCenter center,
                                boolean animationEnabled, Easing animationEasing,
                                double animationDurationMillis) {
    public static class ZoomConfigurationBuilder {
        private Double percent;
        private ZoomCenter center;
        private Boolean animationEnabled;
        private Easing animationEasing;
        private Double animationDurationMillis;

        public ZoomConfigurationBuilder percent(double percent) {
            this.percent = percent;
            return this;
        }

        public ZoomConfigurationBuilder center(ZoomCenter center) {
            this.center = center;
            return this;
        }

        public ZoomConfigurationBuilder animationEnabled(boolean animationEnabled) {
            this.animationEnabled = animationEnabled;
            return this;
        }

        public ZoomConfigurationBuilder animationEasing(Easing animationEasing) {
            this.animationEasing = animationEasing;
            return this;
        }

        public ZoomConfigurationBuilder animationDurationMillis(double animationDurationMillis) {
            this.animationDurationMillis = animationDurationMillis;
            return this;
        }

        public Double percent() {
            return percent;
        }

        public ZoomCenter center() {
            return center;
        }

        public Boolean animationEnabled() {
            return animationEnabled;
        }

        public Easing animationEasing() {
            return animationEasing;
        }

        public Double animationDurationMillis() {
            return animationDurationMillis;
        }

        public ZoomConfiguration build() {
            return new ZoomConfiguration(percent, center,
                    animationEnabled, animationEasing, animationDurationMillis);
        }

    }
}

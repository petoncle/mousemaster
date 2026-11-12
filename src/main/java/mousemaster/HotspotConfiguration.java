package mousemaster;

import mousemaster.MovementConfiguration.MovementConfigurationBuilder;

public record HotspotConfiguration(double cellXPercent, double cellYPercent,
                                     MovementConfiguration movement) {

    public static class HotspotConfigurationBuilder {

        private Double cellXPercent;
        private Double cellYPercent;
        private final MovementConfigurationBuilder movement = new MovementConfigurationBuilder();

        public HotspotConfigurationBuilder cellXPercent(Double cellXPercent) {
            this.cellXPercent = cellXPercent;
            return this;
        }

        public HotspotConfigurationBuilder cellYPercent(Double cellYPercent) {
            this.cellYPercent = cellYPercent;
            return this;
        }

        public Double cellXPercent() {
            return cellXPercent;
        }

        public Double cellYPercent() {
            return cellYPercent;
        }

        public MovementConfigurationBuilder movement() {
            return movement;
        }

        public HotspotConfiguration build() {
            return new HotspotConfiguration(cellXPercent, cellYPercent, movement.build());
        }
    }

}

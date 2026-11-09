package mousemaster;

import mousemaster.MovementConfiguration.MovementConfigurationBuilder;

public record HintPointConfiguration(double cellXPercent, double cellYPercent,
                                     MovementConfiguration movement) {

    public static class HintPointConfigurationBuilder {

        private Double cellXPercent;
        private Double cellYPercent;
        private final MovementConfigurationBuilder movement = new MovementConfigurationBuilder();

        public HintPointConfigurationBuilder cellXPercent(double cellXPercent) {
            this.cellXPercent = cellXPercent;
            return this;
        }

        public HintPointConfigurationBuilder cellYPercent(double cellYPercent) {
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

        public HintPointConfiguration build() {
            return new HintPointConfiguration(cellXPercent, cellYPercent, movement.build());
        }
    }

}

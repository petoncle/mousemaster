package mousemaster;

import java.time.Duration;

public record GridConfiguration(GridArea area, Synchronization synchronization, int rowCount,
                                int columnCount, boolean lineVisible,
                                String lineHexColor, double lineThickness,
                                double lineOpacity, String backgroundHexColor,
                                double backgroundOpacity,
                                boolean transitionAnimationEnabled,
                                Duration transitionAnimationDuration,
                                boolean fadeAnimationEnabled,
                                Duration fadeAnimationDuration) {

    public static class GridConfigurationBuilder {
        private GridArea.GridAreaBuilder area = new GridArea.GridAreaBuilder();
        private Synchronization synchronization;
        private Integer rowCount;
        private Integer columnCount;
        private Boolean lineVisible;
        private String lineHexColor;
        private Double lineThickness;
        private Double lineOpacity;
        private String backgroundHexColor;
        private Double backgroundOpacity;
        private Boolean transitionAnimationEnabled;
        private Duration transitionAnimationDuration;
        private Boolean fadeAnimationEnabled;
        private Duration fadeAnimationDuration;

        public GridConfigurationBuilder synchronization(Synchronization synchronization) {
            this.synchronization = synchronization;
            return this;
        }

        public GridConfigurationBuilder rowCount(int rowCount) {
            this.rowCount = rowCount;
            return this;
        }

        public GridConfigurationBuilder columnCount(int columnCount) {
            this.columnCount = columnCount;
            return this;
        }

        public GridConfigurationBuilder lineVisible(boolean visible) {
            this.lineVisible = visible;
            return this;
        }

        public GridConfigurationBuilder lineHexColor(String lineHexColor) {
            this.lineHexColor = lineHexColor;
            return this;
        }

        public GridConfigurationBuilder lineThickness(double lineThickness) {
            this.lineThickness = lineThickness;
            return this;
        }

        public GridConfigurationBuilder lineOpacity(double lineOpacity) {
            this.lineOpacity = lineOpacity;
            return this;
        }

        public GridConfigurationBuilder backgroundHexColor(String backgroundHexColor) {
            this.backgroundHexColor = backgroundHexColor;
            return this;
        }

        public GridConfigurationBuilder backgroundOpacity(double backgroundOpacity) {
            this.backgroundOpacity = backgroundOpacity;
            return this;
        }

        public GridConfigurationBuilder transitionAnimationEnabled(boolean transitionAnimationEnabled) {
            this.transitionAnimationEnabled = transitionAnimationEnabled;
            return this;
        }

        public GridConfigurationBuilder transitionAnimationDuration(Duration transitionAnimationDuration) {
            this.transitionAnimationDuration = transitionAnimationDuration;
            return this;
        }

        public GridConfigurationBuilder fadeAnimationEnabled(boolean fadeAnimationEnabled) {
            this.fadeAnimationEnabled = fadeAnimationEnabled;
            return this;
        }

        public GridConfigurationBuilder fadeAnimationDuration(Duration fadeAnimationDuration) {
            this.fadeAnimationDuration = fadeAnimationDuration;
            return this;
        }

        public GridArea.GridAreaBuilder area() {
            return area;
        }

        public Synchronization synchronization() {
            return synchronization;
        }

        public Integer rowCount() {
            return rowCount;
        }

        public Integer columnCount() {
            return columnCount;
        }

        public Boolean lineVisible() {
            return lineVisible;
        }

        public String lineHexColor() {
            return lineHexColor;
        }

        public Double lineThickness() {
            return lineThickness;
        }

        public Double lineOpacity() {
            return lineOpacity;
        }

        public String backgroundHexColor() {
            return backgroundHexColor;
        }

        public Double backgroundOpacity() {
            return backgroundOpacity;
        }

        public Boolean transitionAnimationEnabled() {
            return transitionAnimationEnabled;
        }

        public Duration transitionAnimationDuration() {
            return transitionAnimationDuration;
        }

        public Boolean fadeAnimationEnabled() {
            return fadeAnimationEnabled;
        }

        public Duration fadeAnimationDuration() {
            return fadeAnimationDuration;
        }

        public GridConfiguration build() {
            return new GridConfiguration(area.build(), synchronization, rowCount,
                    columnCount, lineVisible, lineHexColor, lineThickness, lineOpacity,
                    backgroundHexColor, backgroundOpacity, transitionAnimationEnabled,
                    transitionAnimationDuration, fadeAnimationEnabled, fadeAnimationDuration);
        }

    }

}

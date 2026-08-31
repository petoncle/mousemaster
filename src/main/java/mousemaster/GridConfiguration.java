package mousemaster;

import java.time.Duration;

public record GridConfiguration(GridArea area, Synchronization synchronization, int rowCount,
                                int columnCount, boolean lineVisible,
                                Color lineColor, double lineThickness,
                                double lineOpacity, Color backgroundColor,
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
        private Color lineColor;
        private Double lineThickness;
        private Double lineOpacity;
        private Color backgroundColor;
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

        public GridConfigurationBuilder lineColor(Color lineColor) {
            this.lineColor = lineColor;
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

        public GridConfigurationBuilder backgroundColor(Color backgroundColor) {
            this.backgroundColor = backgroundColor;
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

        public Color lineColor() {
            return lineColor;
        }

        public Double lineThickness() {
            return lineThickness;
        }

        public Double lineOpacity() {
            return lineOpacity;
        }

        public Color backgroundColor() {
            return backgroundColor;
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
                    columnCount, lineVisible, lineColor, lineThickness, lineOpacity,
                    backgroundColor, backgroundOpacity, transitionAnimationEnabled,
                    transitionAnimationDuration, fadeAnimationEnabled, fadeAnimationDuration);
        }

    }

}

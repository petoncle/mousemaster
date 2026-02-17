package mousemaster;

import mousemaster.FontStyle.FontStyleBuilder;
import mousemaster.IndicatorOutline.IndicatorOutlineBuilder;
import mousemaster.Shadow.ShadowBuilder;

public record Indicator(int size, int edgeCount, String hexColor, double opacity,
                        IndicatorOutline outerOutline, IndicatorOutline innerOutline,
                        Shadow shadow,
                        boolean labelEnabled, String labelText,
                        FontStyle labelFontStyle,
                        double horizontalOffset, double verticalOffset) {

    public static class IndicatorBuilder {
        private Integer size;
        private Integer edgeCount;
        private String hexColor;
        private Double opacity;
        private IndicatorOutlineBuilder outerOutline = new IndicatorOutlineBuilder();
        private IndicatorOutlineBuilder innerOutline = new IndicatorOutlineBuilder();
        private ShadowBuilder shadow = new ShadowBuilder();
        private Boolean labelEnabled;
        private String labelText;
        private FontStyleBuilder labelFontStyle = new FontStyleBuilder();
        private Double horizontalOffset;
        private Double verticalOffset;

        public IndicatorBuilder size(int size) {
            this.size = size;
            return this;
        }

        public Integer size() {
            return size;
        }

        public IndicatorBuilder edgeCount(int edgeCount) {
            this.edgeCount = edgeCount;
            return this;
        }

        public Integer edgeCount() {
            return edgeCount;
        }

        public IndicatorBuilder hexColor(String hexColor) {
            this.hexColor = hexColor;
            return this;
        }

        public String hexColor() {
            return hexColor;
        }

        public IndicatorBuilder opacity(double opacity) {
            this.opacity = opacity;
            return this;
        }

        public Double opacity() {
            return opacity;
        }

        public IndicatorOutlineBuilder outerOutline() {
            return outerOutline;
        }

        public IndicatorOutlineBuilder innerOutline() {
            return innerOutline;
        }

        public ShadowBuilder shadow() {
            return shadow;
        }

        public IndicatorBuilder labelEnabled(boolean labelEnabled) {
            this.labelEnabled = labelEnabled;
            return this;
        }

        public Boolean labelEnabled() {
            return labelEnabled;
        }

        public IndicatorBuilder labelText(String labelText) {
            this.labelText = labelText;
            return this;
        }

        public String labelText() {
            return labelText;
        }

        public FontStyleBuilder labelFontStyle() {
            return labelFontStyle;
        }

        public IndicatorBuilder horizontalOffset(double horizontalOffset) {
            this.horizontalOffset = horizontalOffset;
            return this;
        }

        public Double horizontalOffset() {
            return horizontalOffset;
        }

        public IndicatorBuilder verticalOffset(double verticalOffset) {
            this.verticalOffset = verticalOffset;
            return this;
        }

        public Double verticalOffset() {
            return verticalOffset;
        }

        public void extend(IndicatorBuilder parent) {
            if (size == null) size = parent.size;
            if (edgeCount == null) edgeCount = parent.edgeCount;
            if (hexColor == null) hexColor = parent.hexColor;
            if (opacity == null) opacity = parent.opacity;
            outerOutline.extend(parent.outerOutline);
            innerOutline.extend(parent.innerOutline);
            shadow.extend(parent.shadow);
            if (labelEnabled == null) labelEnabled = parent.labelEnabled;
            if (labelText == null) labelText = parent.labelText;
            labelFontStyle.extend(parent.labelFontStyle);
            if (horizontalOffset == null) horizontalOffset = parent.horizontalOffset;
            if (verticalOffset == null) verticalOffset = parent.verticalOffset;
        }

        public Indicator build() {
            return new Indicator(size, edgeCount, hexColor, opacity,
                    outerOutline.build(), innerOutline.build(),
                    shadow.build(),
                    labelEnabled, labelText, labelFontStyle.build(),
                    horizontalOffset, verticalOffset);
        }
    }
}

package mousemaster;

import mousemaster.FontStyle.FontStyleBuilder;
import mousemaster.IndicatorOutline.IndicatorOutlineBuilder;
import mousemaster.Shadow.ShadowBuilder;

public record Indicator(int size, int edgeCount, String hexColor, double opacity,
                        IndicatorOutline firstOutline, IndicatorOutline secondOutline,
                        Shadow shadow,
                        boolean labelEnabled, String labelText,
                        FontStyle labelFontStyle) {

    public static class IndicatorBuilder {
        private Integer size;
        private Integer edgeCount;
        private String hexColor;
        private Double opacity;
        private IndicatorOutlineBuilder firstOutline = new IndicatorOutlineBuilder();
        private IndicatorOutlineBuilder secondOutline = new IndicatorOutlineBuilder();
        private ShadowBuilder shadow = new ShadowBuilder();
        private Boolean labelEnabled;
        private String labelText;
        private FontStyleBuilder labelFontStyle = new FontStyleBuilder();

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

        public IndicatorOutlineBuilder firstOutline() {
            return firstOutline;
        }

        public IndicatorOutlineBuilder secondOutline() {
            return secondOutline;
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

        public void extend(IndicatorBuilder parent) {
            if (size == null) size = parent.size;
            if (edgeCount == null) edgeCount = parent.edgeCount;
            if (hexColor == null) hexColor = parent.hexColor;
            if (opacity == null) opacity = parent.opacity;
            firstOutline.extend(parent.firstOutline);
            secondOutline.extend(parent.secondOutline);
            shadow.extend(parent.shadow);
            if (labelEnabled == null) labelEnabled = parent.labelEnabled;
            if (labelText == null) labelText = parent.labelText;
            labelFontStyle.extend(parent.labelFontStyle);
        }

        public Indicator build() {
            return new Indicator(size, edgeCount, hexColor, opacity,
                    firstOutline.build(), secondOutline.build(),
                    shadow.build(),
                    labelEnabled, labelText, labelFontStyle.build());
        }
    }
}

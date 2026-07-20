package mousemaster;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public record HintMeshStyle(HintFontStyle fontStyle,
                            boolean prefixInBackground,
                            HintFontStyle prefixFontStyle,
                            String boxHexColor,
                            double boxOpacity,
                            double boxBorderThickness,
                            double boxBorderLength,
                            String boxBorderHexColor,
                            double boxBorderOpacity,
                            double boxBorderRadius,
                            Shadow boxShadow,
                            boolean prefixBoxEnabled,
                            double prefixBoxBorderThickness,
                            double prefixBoxBorderLength,
                            String prefixBoxBorderHexColor,
                            double prefixBoxBorderOpacity,
                            double boxWidthPercent,
                            double boxHeightPercent,
                            double cellHorizontalPadding,
                            double cellVerticalPadding,
                            List<Decoration> decorations,
                            boolean transitionAnimationEnabled,
                            Duration transitionAnimationDuration,
                            boolean fadeAnimationEnabled,
                            Duration fadeAnimationDuration,
                            String backgroundHexColor,
                            double backgroundOpacity,
                            List<Key> labelOverride) {

    public HintMeshStyleBuilder builder() {
        return new HintMeshStyleBuilder(this);
    }

    public static class HintMeshStyleBuilder {
        private HintFontStyle.HintFontStyleBuilder fontStyle = new HintFontStyle.HintFontStyleBuilder();
        private Boolean prefixInBackground;
        private HintFontStyle.HintFontStyleBuilder prefixFontStyle = new HintFontStyle.HintFontStyleBuilder();
        private String boxHexColor;
        private Double boxOpacity;
        private Double boxBorderThickness;
        private Double boxBorderLength;
        private String boxBorderHexColor;
        private Double boxBorderOpacity;
        private Double boxBorderRadius;
        private Shadow.ShadowBuilder boxShadow = new Shadow.ShadowBuilder();
        private Boolean prefixBoxEnabled;
        private Double prefixBoxBorderThickness;
        private Double prefixBoxBorderLength;
        private String prefixBoxBorderHexColor;
        private Double prefixBoxBorderOpacity;
        private Double boxWidthPercent;
        private Double boxHeightPercent;
        private Double cellHorizontalPadding;
        private Double cellVerticalPadding;
        // Index 0 = whole-area decoration, 1 = subdecoration, 2 = subsubdecoration.
        private final List<Decoration.DecorationBuilder> decorations = new ArrayList<>(
                List.of(new Decoration.DecorationBuilder(),
                        new Decoration.DecorationBuilder(),
                        new Decoration.DecorationBuilder()));
        private Boolean transitionAnimationEnabled;
        private Duration transitionAnimationDuration;
        private Boolean fadeAnimationEnabled;
        private Duration fadeAnimationDuration;
        private String backgroundHexColor;
        private Double backgroundOpacity;
        private List<Key> labelOverride;

        public HintMeshStyleBuilder() {

        }

        public HintMeshStyleBuilder(HintMeshStyle style) {
            this.fontStyle = style.fontStyle.builder();
            this.prefixInBackground = style.prefixInBackground;
            this.prefixFontStyle = style.prefixFontStyle.builder();
            this.boxHexColor = style.boxHexColor;
            this.boxOpacity = style.boxOpacity;
            this.boxBorderThickness = style.boxBorderThickness;
            this.boxBorderLength = style.boxBorderLength;
            this.boxBorderHexColor = style.boxBorderHexColor;
            this.boxBorderOpacity = style.boxBorderOpacity;
            this.boxBorderRadius = style.boxBorderRadius;
            this.boxShadow = new Shadow.ShadowBuilder(style.boxShadow);
            this.prefixBoxEnabled = style.prefixBoxEnabled;
            this.prefixBoxBorderThickness = style.prefixBoxBorderThickness;
            this.prefixBoxBorderLength = style.prefixBoxBorderLength;
            this.prefixBoxBorderHexColor = style.prefixBoxBorderHexColor;
            this.prefixBoxBorderOpacity = style.prefixBoxBorderOpacity;
            this.boxWidthPercent = style.boxWidthPercent;
            this.boxHeightPercent = style.boxHeightPercent;
            this.cellHorizontalPadding = style.cellHorizontalPadding;
            this.cellVerticalPadding = style.cellVerticalPadding;
            this.decorations.clear();
            for (Decoration decoration : style.decorations)
                this.decorations.add(decoration.builder());
            this.transitionAnimationEnabled = style.transitionAnimationEnabled;
            this.transitionAnimationDuration = style.transitionAnimationDuration;
            this.fadeAnimationEnabled = style.fadeAnimationEnabled;
            this.fadeAnimationDuration = style.fadeAnimationDuration;
            this.backgroundHexColor = style.backgroundHexColor;
            this.backgroundOpacity = style.backgroundOpacity;
            this.labelOverride = style.labelOverride;
        }

        public HintMeshStyleBuilder prefixInBackground(
                Boolean prefixInBackground) {
            this.prefixInBackground = prefixInBackground;
            return this;
        }

        public HintMeshStyleBuilder boxHexColor(String boxHexColor) {
            this.boxHexColor = boxHexColor;
            return this;
        }

        public HintMeshStyleBuilder boxOpacity(Double boxOpacity) {
            this.boxOpacity = boxOpacity;
            return this;
        }

        public HintMeshStyleBuilder boxBorderThickness(Double boxBorderThickness) {
            this.boxBorderThickness = boxBorderThickness;
            return this;
        }

        public HintMeshStyleBuilder boxBorderLength(Double boxBorderLength) {
            this.boxBorderLength = boxBorderLength;
            return this;
        }

        public HintMeshStyleBuilder boxBorderHexColor(String boxBorderHexColor) {
            this.boxBorderHexColor = boxBorderHexColor;
            return this;
        }

        public HintMeshStyleBuilder boxBorderOpacity(Double boxBorderOpacity) {
            this.boxBorderOpacity = boxBorderOpacity;
            return this;
        }

        public HintMeshStyleBuilder boxBorderRadius(Double boxBorderRadius) {
            this.boxBorderRadius = boxBorderRadius;
            return this;
        }

        public HintMeshStyleBuilder prefixBoxEnabled(Boolean prefixBoxEnabled) {
            this.prefixBoxEnabled = prefixBoxEnabled;
            return this;
        }

        public HintMeshStyleBuilder prefixBoxBorderThickness(Double prefixBoxBorderThickness) {
            this.prefixBoxBorderThickness = prefixBoxBorderThickness;
            return this;
        }

        public HintMeshStyleBuilder prefixBoxBorderLength(Double prefixBoxBorderLength) {
            this.prefixBoxBorderLength = prefixBoxBorderLength;
            return this;
        }

        public HintMeshStyleBuilder prefixBoxBorderHexColor(String prefixBoxBorderHexColor) {
            this.prefixBoxBorderHexColor = prefixBoxBorderHexColor;
            return this;
        }

        public HintMeshStyleBuilder prefixBoxBorderOpacity(Double prefixBoxBorderOpacity) {
            this.prefixBoxBorderOpacity = prefixBoxBorderOpacity;
            return this;
        }

        public HintMeshStyleBuilder boxWidthPercent(Double boxWidthPercent) {
            this.boxWidthPercent = boxWidthPercent;
            return this;
        }

        public HintMeshStyleBuilder boxHeightPercent(Double boxHeightPercent) {
            this.boxHeightPercent = boxHeightPercent;
            return this;
        }

        public HintMeshStyleBuilder cellHorizontalPadding(Double cellHorizontalPadding) {
            this.cellHorizontalPadding = cellHorizontalPadding;
            return this;
        }

        public HintMeshStyleBuilder cellVerticalPadding(Double cellVerticalPadding) {
            this.cellVerticalPadding = cellVerticalPadding;
            return this;
        }

        public Decoration.DecorationBuilder decoration(int index) {
            return decorations.get(index);
        }

        public List<Decoration.DecorationBuilder> decorations() {
            return decorations;
        }

        public HintMeshStyleBuilder transitionAnimationEnabled(Boolean transitionAnimationEnabled) {
            this.transitionAnimationEnabled = transitionAnimationEnabled;
            return this;
        }

        public HintMeshStyleBuilder transitionAnimationDuration(Duration transitionAnimationDuration) {
            this.transitionAnimationDuration = transitionAnimationDuration;
            return this;
        }

        public HintMeshStyleBuilder fadeAnimationEnabled(Boolean fadeAnimationEnabled) {
            this.fadeAnimationEnabled = fadeAnimationEnabled;
            return this;
        }

        public HintMeshStyleBuilder fadeAnimationDuration(Duration fadeAnimationDuration) {
            this.fadeAnimationDuration = fadeAnimationDuration;
            return this;
        }

        public HintMeshStyleBuilder backgroundHexColor(String backgroundHexColor) {
            this.backgroundHexColor = backgroundHexColor;
            return this;
        }

        public HintMeshStyleBuilder backgroundOpacity(Double backgroundOpacity) {
            this.backgroundOpacity = backgroundOpacity;
            return this;
        }

        public HintMeshStyleBuilder labelOverride(List<Key> labelOverride) {
            this.labelOverride = labelOverride;
            return this;
        }

        public HintFontStyle.HintFontStyleBuilder fontStyle() {
            return fontStyle;
        }

        public HintMeshStyleBuilder fontStyle(HintFontStyle fontStyle) {
            this.fontStyle = fontStyle.builder();
            return this;
        }

        public Boolean prefixInBackground() {
            return prefixInBackground;
        }

        public HintFontStyle.HintFontStyleBuilder prefixFontStyle() {
            return prefixFontStyle;
        }

        public String boxHexColor() {
            return boxHexColor;
        }

        public Double boxOpacity() {
            return boxOpacity;
        }

        public Double boxBorderThickness() {
            return boxBorderThickness;
        }

        public Double boxBorderLength() {
            return boxBorderLength;
        }

        public String boxBorderHexColor() {
            return boxBorderHexColor;
        }

        public Double boxBorderOpacity() {
            return boxBorderOpacity;
        }

        public Double boxBorderRadius() {
            return boxBorderRadius;
        }

        public Shadow.ShadowBuilder boxShadow() {
            return boxShadow;
        }

        public Boolean prefixBoxEnabled() {
            return prefixBoxEnabled;
        }

        public Double prefixBoxBorderThickness() {
            return prefixBoxBorderThickness;
        }

        public Double prefixBoxBorderLength() {
            return prefixBoxBorderLength;
        }

        public String prefixBoxBorderHexColor() {
            return prefixBoxBorderHexColor;
        }

        public Double prefixBoxBorderOpacity() {
            return prefixBoxBorderOpacity;
        }

        public Double boxWidthPercent() {
            return boxWidthPercent;
        }

        public Double boxHeightPercent() {
            return boxHeightPercent;
        }

        public Double cellHorizontalPadding() {
            return cellHorizontalPadding;
        }

        public Double cellVerticalPadding() {
            return cellVerticalPadding;
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

        public String backgroundHexColor() {
            return backgroundHexColor;
        }

        public Double backgroundOpacity() {
            return backgroundOpacity;
        }

        public List<Key> labelOverride() {
            return labelOverride;
        }

        public HintMeshStyle build(HintMeshStyle defaultStyle) {
            if (defaultStyle != null) {
                boxShadow.extend(new Shadow.ShadowBuilder(defaultStyle.boxShadow));
            }
            List<Decoration> builtDecorations = new ArrayList<>();
            for (int i = 0; i < decorations.size(); i++)
                builtDecorations.add(decorations.get(i).build(
                        defaultStyle == null ? null : defaultStyle.decorations.get(i)));
            return new HintMeshStyle(
                    fontStyle.build(),
                    prefixInBackground == null ? defaultStyle.prefixInBackground : prefixInBackground,
                    prefixFontStyle.build(),
                    boxHexColor == null ? defaultStyle.boxHexColor : boxHexColor,
                    boxOpacity == null ? defaultStyle.boxOpacity : boxOpacity,
                    boxBorderThickness == null ? defaultStyle.boxBorderThickness : boxBorderThickness,
                    boxBorderLength == null ? defaultStyle.boxBorderLength : boxBorderLength,
                    boxBorderHexColor == null ? defaultStyle.boxBorderHexColor : boxBorderHexColor,
                    boxBorderOpacity == null ? defaultStyle.boxBorderOpacity : boxBorderOpacity,
                    boxBorderRadius == null ? defaultStyle.boxBorderRadius : boxBorderRadius,
                    boxShadow.build(),
                    prefixBoxEnabled == null ? defaultStyle.prefixBoxEnabled : prefixBoxEnabled,
                    prefixBoxBorderThickness == null ? defaultStyle.prefixBoxBorderThickness : prefixBoxBorderThickness,
                    prefixBoxBorderLength == null ? defaultStyle.prefixBoxBorderLength : prefixBoxBorderLength,
                    prefixBoxBorderHexColor == null ? defaultStyle.prefixBoxBorderHexColor : prefixBoxBorderHexColor,
                    prefixBoxBorderOpacity == null ? defaultStyle.prefixBoxBorderOpacity : prefixBoxBorderOpacity,
                    boxWidthPercent == null ? defaultStyle.boxWidthPercent : boxWidthPercent,
                    boxHeightPercent == null ? defaultStyle.boxHeightPercent : boxHeightPercent,
                    cellHorizontalPadding == null ? defaultStyle.cellHorizontalPadding : cellHorizontalPadding,
                    cellVerticalPadding == null ? defaultStyle.cellVerticalPadding : cellVerticalPadding,
                    builtDecorations,
                    transitionAnimationEnabled == null ? defaultStyle.transitionAnimationEnabled : transitionAnimationEnabled,
                    transitionAnimationDuration == null ? defaultStyle.transitionAnimationDuration : transitionAnimationDuration,
                    fadeAnimationEnabled == null ? defaultStyle.fadeAnimationEnabled : fadeAnimationEnabled,
                    fadeAnimationDuration == null ? defaultStyle.fadeAnimationDuration : fadeAnimationDuration,
                    backgroundHexColor == null ? defaultStyle.backgroundHexColor : backgroundHexColor,
                    backgroundOpacity == null ? defaultStyle.backgroundOpacity : backgroundOpacity,
                    labelOverride == null ? defaultStyle.labelOverride : labelOverride
            );
        }

    }

}

package mousemaster;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public record HintMeshStyle(HintFontStyle fontStyle,
                            boolean prefixInBackground,
                            HintFontStyle prefixFontStyle,
                            GradientColor boxColor,
                            double boxOpacity,
                            double boxBorderThickness,
                            double boxBorderLength,
                            Color boxBorderColor,
                            double boxBorderOpacity,
                            double boxBorderRadius,
                            boolean boxFramed,
                            Shadow boxShadow,
                            boolean prefixBoxEnabled,
                            double prefixBoxBorderThickness,
                            double prefixBoxBorderLength,
                            Color prefixBoxBorderColor,
                            double prefixBoxBorderOpacity,
                            boolean prefixBoxFramed,
                            GradientColor uiElementBoxColor,
                            double uiElementBoxOpacity,
                            double uiElementBoxBorderThickness,
                            double uiElementBoxBorderLength,
                            Color uiElementBoxBorderColor,
                            double uiElementBoxBorderOpacity,
                            double uiElementBoxBorderRadius,
                            double uiElementBoxHorizontalPadding,
                            double uiElementBoxVerticalPadding,
                            double boxWidthPercent,
                            double boxHeightPercent,
                            double boxHorizontalPadding,
                            double boxVerticalPadding,
                            List<Decoration> decorations,
                            boolean transitionAnimationEnabled,
                            Duration transitionAnimationDuration,
                            boolean fadeAnimationEnabled,
                            Duration fadeAnimationDuration,
                            Color backgroundColor,
                            double backgroundOpacity,
                            double backgroundBorderRadius,
                            List<Key> labelOverride) {

    public HintMeshStyleBuilder builder() {
        return new HintMeshStyleBuilder(this);
    }

    public static class HintMeshStyleBuilder {
        private HintFontStyle.HintFontStyleBuilder fontStyle = new HintFontStyle.HintFontStyleBuilder();
        private Boolean prefixInBackground;
        private HintFontStyle.HintFontStyleBuilder prefixFontStyle = new HintFontStyle.HintFontStyleBuilder();
        private GradientColor boxColor;
        private Double boxOpacity;
        private Double boxBorderThickness;
        private Double boxBorderLength;
        private Color boxBorderColor;
        private Double boxBorderOpacity;
        private Double boxBorderRadius;
        private Boolean boxFramed;
        private Shadow.ShadowBuilder boxShadow = new Shadow.ShadowBuilder();
        private Boolean prefixBoxEnabled;
        private Double prefixBoxBorderThickness;
        private Double prefixBoxBorderLength;
        private Color prefixBoxBorderColor;
        private Double prefixBoxBorderOpacity;
        private Boolean prefixBoxFramed;
        private GradientColor uiElementBoxColor;
        private Double uiElementBoxOpacity;
        private Double uiElementBoxBorderThickness;
        private Double uiElementBoxBorderLength;
        private Color uiElementBoxBorderColor;
        private Double uiElementBoxBorderOpacity;
        private Double uiElementBoxBorderRadius;
        private Double uiElementBoxHorizontalPadding;
        private Double uiElementBoxVerticalPadding;
        private Double boxWidthPercent;
        private Double boxHeightPercent;
        private Double boxHorizontalPadding;
        private Double boxVerticalPadding;
        // Index 0 = whole-area decoration, 1 = subdecoration, 2 = subsubdecoration.
        private final List<Decoration.DecorationBuilder> decorations = new ArrayList<>(
                List.of(new Decoration.DecorationBuilder(),
                        new Decoration.DecorationBuilder(),
                        new Decoration.DecorationBuilder()));
        private Boolean transitionAnimationEnabled;
        private Duration transitionAnimationDuration;
        private Boolean fadeAnimationEnabled;
        private Duration fadeAnimationDuration;
        private Color backgroundColor;
        private Double backgroundOpacity;
        private Double backgroundBorderRadius;
        private List<Key> labelOverride;

        public HintMeshStyleBuilder() {

        }

        public HintMeshStyleBuilder(HintMeshStyle style) {
            this.fontStyle = style.fontStyle.builder();
            this.prefixInBackground = style.prefixInBackground;
            this.prefixFontStyle = style.prefixFontStyle.builder();
            this.boxColor = style.boxColor;
            this.boxOpacity = style.boxOpacity;
            this.boxBorderThickness = style.boxBorderThickness;
            this.boxBorderLength = style.boxBorderLength;
            this.boxBorderColor = style.boxBorderColor;
            this.boxBorderOpacity = style.boxBorderOpacity;
            this.boxBorderRadius = style.boxBorderRadius;
            this.boxFramed = style.boxFramed;
            this.boxShadow = new Shadow.ShadowBuilder(style.boxShadow);
            this.prefixBoxEnabled = style.prefixBoxEnabled;
            this.prefixBoxBorderThickness = style.prefixBoxBorderThickness;
            this.prefixBoxBorderLength = style.prefixBoxBorderLength;
            this.prefixBoxBorderColor = style.prefixBoxBorderColor;
            this.prefixBoxBorderOpacity = style.prefixBoxBorderOpacity;
            this.prefixBoxFramed = style.prefixBoxFramed;
            this.uiElementBoxColor = style.uiElementBoxColor;
            this.uiElementBoxOpacity = style.uiElementBoxOpacity;
            this.uiElementBoxBorderThickness = style.uiElementBoxBorderThickness;
            this.uiElementBoxBorderLength = style.uiElementBoxBorderLength;
            this.uiElementBoxBorderColor = style.uiElementBoxBorderColor;
            this.uiElementBoxBorderOpacity = style.uiElementBoxBorderOpacity;
            this.uiElementBoxBorderRadius = style.uiElementBoxBorderRadius;
            this.uiElementBoxHorizontalPadding = style.uiElementBoxHorizontalPadding;
            this.uiElementBoxVerticalPadding = style.uiElementBoxVerticalPadding;
            this.boxWidthPercent = style.boxWidthPercent;
            this.boxHeightPercent = style.boxHeightPercent;
            this.boxHorizontalPadding = style.boxHorizontalPadding;
            this.boxVerticalPadding = style.boxVerticalPadding;
            this.decorations.clear();
            for (Decoration decoration : style.decorations)
                this.decorations.add(decoration.builder());
            this.transitionAnimationEnabled = style.transitionAnimationEnabled;
            this.transitionAnimationDuration = style.transitionAnimationDuration;
            this.fadeAnimationEnabled = style.fadeAnimationEnabled;
            this.fadeAnimationDuration = style.fadeAnimationDuration;
            this.backgroundColor = style.backgroundColor;
            this.backgroundOpacity = style.backgroundOpacity;
            this.backgroundBorderRadius = style.backgroundBorderRadius;
            this.labelOverride = style.labelOverride;
        }

        public HintMeshStyleBuilder prefixInBackground(
                Boolean prefixInBackground) {
            this.prefixInBackground = prefixInBackground;
            return this;
        }

        public HintMeshStyleBuilder boxColor(GradientColor boxColor) {
            this.boxColor = boxColor;
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

        public HintMeshStyleBuilder boxBorderColor(Color boxBorderColor) {
            this.boxBorderColor = boxBorderColor;
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

        public HintMeshStyleBuilder boxFramed(Boolean boxFramed) {
            this.boxFramed = boxFramed;
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

        public HintMeshStyleBuilder prefixBoxBorderColor(Color prefixBoxBorderColor) {
            this.prefixBoxBorderColor = prefixBoxBorderColor;
            return this;
        }

        public HintMeshStyleBuilder prefixBoxBorderOpacity(Double prefixBoxBorderOpacity) {
            this.prefixBoxBorderOpacity = prefixBoxBorderOpacity;
            return this;
        }

        public HintMeshStyleBuilder prefixBoxFramed(Boolean prefixBoxFramed) {
            this.prefixBoxFramed = prefixBoxFramed;
            return this;
        }

        public HintMeshStyleBuilder uiElementBoxColor(GradientColor uiElementBoxColor) {
            this.uiElementBoxColor = uiElementBoxColor;
            return this;
        }

        public HintMeshStyleBuilder uiElementBoxOpacity(Double uiElementBoxOpacity) {
            this.uiElementBoxOpacity = uiElementBoxOpacity;
            return this;
        }

        public HintMeshStyleBuilder uiElementBoxBorderThickness(Double uiElementBoxBorderThickness) {
            this.uiElementBoxBorderThickness = uiElementBoxBorderThickness;
            return this;
        }

        public HintMeshStyleBuilder uiElementBoxBorderLength(Double uiElementBoxBorderLength) {
            this.uiElementBoxBorderLength = uiElementBoxBorderLength;
            return this;
        }

        public HintMeshStyleBuilder uiElementBoxBorderColor(Color uiElementBoxBorderColor) {
            this.uiElementBoxBorderColor = uiElementBoxBorderColor;
            return this;
        }

        public HintMeshStyleBuilder uiElementBoxBorderOpacity(Double uiElementBoxBorderOpacity) {
            this.uiElementBoxBorderOpacity = uiElementBoxBorderOpacity;
            return this;
        }

        public HintMeshStyleBuilder uiElementBoxBorderRadius(Double uiElementBoxBorderRadius) {
            this.uiElementBoxBorderRadius = uiElementBoxBorderRadius;
            return this;
        }

        public HintMeshStyleBuilder uiElementBoxHorizontalPadding(Double uiElementBoxHorizontalPadding) {
            this.uiElementBoxHorizontalPadding = uiElementBoxHorizontalPadding;
            return this;
        }

        public HintMeshStyleBuilder uiElementBoxVerticalPadding(Double uiElementBoxVerticalPadding) {
            this.uiElementBoxVerticalPadding = uiElementBoxVerticalPadding;
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

        public HintMeshStyleBuilder boxHorizontalPadding(Double boxHorizontalPadding) {
            this.boxHorizontalPadding = boxHorizontalPadding;
            return this;
        }

        public HintMeshStyleBuilder boxVerticalPadding(Double boxVerticalPadding) {
            this.boxVerticalPadding = boxVerticalPadding;
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

        public HintMeshStyleBuilder backgroundColor(Color backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        public HintMeshStyleBuilder backgroundOpacity(Double backgroundOpacity) {
            this.backgroundOpacity = backgroundOpacity;
            return this;
        }

        public HintMeshStyleBuilder backgroundBorderRadius(Double backgroundBorderRadius) {
            this.backgroundBorderRadius = backgroundBorderRadius;
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

        public GradientColor boxColor() {
            return boxColor;
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

        public Color boxBorderColor() {
            return boxBorderColor;
        }

        public Double boxBorderOpacity() {
            return boxBorderOpacity;
        }

        public Double boxBorderRadius() {
            return boxBorderRadius;
        }

        public Boolean boxFramed() {
            return boxFramed;
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

        public Color prefixBoxBorderColor() {
            return prefixBoxBorderColor;
        }

        public Double prefixBoxBorderOpacity() {
            return prefixBoxBorderOpacity;
        }

        public Boolean prefixBoxFramed() {
            return prefixBoxFramed;
        }

        public GradientColor uiElementBoxColor() {
            return uiElementBoxColor;
        }

        public Double uiElementBoxOpacity() {
            return uiElementBoxOpacity;
        }

        public Double uiElementBoxBorderThickness() {
            return uiElementBoxBorderThickness;
        }

        public Double uiElementBoxBorderLength() {
            return uiElementBoxBorderLength;
        }

        public Color uiElementBoxBorderColor() {
            return uiElementBoxBorderColor;
        }

        public Double uiElementBoxBorderOpacity() {
            return uiElementBoxBorderOpacity;
        }

        public Double uiElementBoxBorderRadius() {
            return uiElementBoxBorderRadius;
        }

        public Double uiElementBoxHorizontalPadding() {
            return uiElementBoxHorizontalPadding;
        }

        public Double uiElementBoxVerticalPadding() {
            return uiElementBoxVerticalPadding;
        }

        public Double boxWidthPercent() {
            return boxWidthPercent;
        }

        public Double boxHeightPercent() {
            return boxHeightPercent;
        }

        public Double boxHorizontalPadding() {
            return boxHorizontalPadding;
        }

        public Double boxVerticalPadding() {
            return boxVerticalPadding;
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

        public Color backgroundColor() {
            return backgroundColor;
        }

        public Double backgroundOpacity() {
            return backgroundOpacity;
        }

        public Double backgroundBorderRadius() {
            return backgroundBorderRadius;
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
                    boxColor == null ? defaultStyle.boxColor : boxColor,
                    boxOpacity == null ? defaultStyle.boxOpacity : boxOpacity,
                    boxBorderThickness == null ? defaultStyle.boxBorderThickness : boxBorderThickness,
                    boxBorderLength == null ? defaultStyle.boxBorderLength : boxBorderLength,
                    boxBorderColor == null ? defaultStyle.boxBorderColor : boxBorderColor,
                    boxBorderOpacity == null ? defaultStyle.boxBorderOpacity : boxBorderOpacity,
                    boxBorderRadius == null ? defaultStyle.boxBorderRadius : boxBorderRadius,
                    boxFramed == null ? defaultStyle.boxFramed : boxFramed,
                    boxShadow.build(),
                    prefixBoxEnabled == null ? defaultStyle.prefixBoxEnabled : prefixBoxEnabled,
                    prefixBoxBorderThickness == null ? defaultStyle.prefixBoxBorderThickness : prefixBoxBorderThickness,
                    prefixBoxBorderLength == null ? defaultStyle.prefixBoxBorderLength : prefixBoxBorderLength,
                    prefixBoxBorderColor == null ? defaultStyle.prefixBoxBorderColor : prefixBoxBorderColor,
                    prefixBoxBorderOpacity == null ? defaultStyle.prefixBoxBorderOpacity : prefixBoxBorderOpacity,
                    prefixBoxFramed == null ? defaultStyle.prefixBoxFramed : prefixBoxFramed,
                    uiElementBoxColor == null ? defaultStyle.uiElementBoxColor : uiElementBoxColor,
                    uiElementBoxOpacity == null ? defaultStyle.uiElementBoxOpacity : uiElementBoxOpacity,
                    uiElementBoxBorderThickness == null ? defaultStyle.uiElementBoxBorderThickness : uiElementBoxBorderThickness,
                    uiElementBoxBorderLength == null ? defaultStyle.uiElementBoxBorderLength : uiElementBoxBorderLength,
                    uiElementBoxBorderColor == null ? defaultStyle.uiElementBoxBorderColor : uiElementBoxBorderColor,
                    uiElementBoxBorderOpacity == null ? defaultStyle.uiElementBoxBorderOpacity : uiElementBoxBorderOpacity,
                    uiElementBoxBorderRadius == null ? defaultStyle.uiElementBoxBorderRadius : uiElementBoxBorderRadius,
                    uiElementBoxHorizontalPadding == null ? defaultStyle.uiElementBoxHorizontalPadding : uiElementBoxHorizontalPadding,
                    uiElementBoxVerticalPadding == null ? defaultStyle.uiElementBoxVerticalPadding : uiElementBoxVerticalPadding,
                    boxWidthPercent == null ? defaultStyle.boxWidthPercent : boxWidthPercent,
                    boxHeightPercent == null ? defaultStyle.boxHeightPercent : boxHeightPercent,
                    boxHorizontalPadding == null ? defaultStyle.boxHorizontalPadding : boxHorizontalPadding,
                    boxVerticalPadding == null ? defaultStyle.boxVerticalPadding : boxVerticalPadding,
                    builtDecorations,
                    transitionAnimationEnabled == null ? defaultStyle.transitionAnimationEnabled : transitionAnimationEnabled,
                    transitionAnimationDuration == null ? defaultStyle.transitionAnimationDuration : transitionAnimationDuration,
                    fadeAnimationEnabled == null ? defaultStyle.fadeAnimationEnabled : fadeAnimationEnabled,
                    fadeAnimationDuration == null ? defaultStyle.fadeAnimationDuration : fadeAnimationDuration,
                    backgroundColor == null ? defaultStyle.backgroundColor : backgroundColor,
                    backgroundOpacity == null ? defaultStyle.backgroundOpacity : backgroundOpacity,
                    backgroundBorderRadius == null ? defaultStyle.backgroundBorderRadius : backgroundBorderRadius,
                    labelOverride == null ? defaultStyle.labelOverride : labelOverride
            );
        }

    }

}

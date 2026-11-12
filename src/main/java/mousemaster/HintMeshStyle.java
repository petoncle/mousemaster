package mousemaster;

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
                            boolean groupBoxEnabled,
                            double groupBoxBorderThickness,
                            double groupBoxBorderLength,
                            Color groupBoxBorderColor,
                            double groupBoxBorderOpacity,
                            boolean groupBoxFramed,
                            GradientColor uiElementBoxColor,
                            double uiElementBoxOpacity,
                            double uiElementBoxBorderThickness,
                            double uiElementBoxBorderLength,
                            Color uiElementBoxBorderColor,
                            double uiElementBoxBorderOpacity,
                            double uiElementBoxBorderRadius,
                            double uiElementBoxHorizontalPadding,
                            double uiElementBoxVerticalPadding,
                            Shadow uiElementBoxShadow,
                            double hotspotBoxWidth,
                            double hotspotBoxHeight,
                            GradientColor hotspotBoxColor,
                            double hotspotBoxOpacity,
                            double hotspotBoxBorderThickness,
                            Color hotspotBoxBorderColor,
                            double hotspotBoxBorderOpacity,
                            double hotspotBoxBorderRadius,
                            Shadow hotspotBoxShadow,
                            double hotspotLineThickness,
                            Color hotspotLineColor,
                            double hotspotLineOpacity,
                            Shadow hotspotLineShadow,
                            GradientColor savedBoxColor,
                            Double savedBoxOpacity,
                            Color savedBoxBorderColor,
                            Double savedBoxBorderOpacity,
                            GradientColor savedUiElementBoxColor,
                            Double savedUiElementBoxOpacity,
                            Color savedUiElementBoxBorderColor,
                            Double savedUiElementBoxBorderOpacity,
                            GradientColor savedHotspotBoxColor,
                            Double savedHotspotBoxOpacity,
                            Color savedHotspotBoxBorderColor,
                            Double savedHotspotBoxBorderOpacity,
                            double boxWidthPercent,
                            double boxHeightPercent,
                            double boxHorizontalPadding,
                            double boxVerticalPadding,
                            List<Decoration> decorations,
                            Transition transition,
                            Transition fade,
                            Color backgroundColor,
                            double backgroundOpacity,
                            double backgroundBorderThickness,
                            Color backgroundBorderColor,
                            double backgroundBorderOpacity,
                            double backgroundBorderRadius,
                            List<Key> labelOverride) {

    public HintMeshStyleBuilder builder() {
        return new HintMeshStyleBuilder(this);
    }

    /** The style of a hint at a saved position: the saved values where they are set. */
    public HintMeshStyle saved() {
        HintMeshStyleBuilder saved = new HintMeshStyleBuilder(this)
                .fontStyle(fontStyle.saved())
                .prefixFontStyle(prefixFontStyle.saved());
        if (savedBoxColor != null)
            saved.boxColor(savedBoxColor);
        if (savedBoxOpacity != null)
            saved.boxOpacity(savedBoxOpacity);
        if (savedBoxBorderColor != null)
            saved.boxBorderColor(savedBoxBorderColor);
        if (savedBoxBorderOpacity != null)
            saved.boxBorderOpacity(savedBoxBorderOpacity);
        if (savedUiElementBoxColor != null)
            saved.uiElementBoxColor(savedUiElementBoxColor);
        if (savedUiElementBoxOpacity != null)
            saved.uiElementBoxOpacity(savedUiElementBoxOpacity);
        if (savedUiElementBoxBorderColor != null)
            saved.uiElementBoxBorderColor(savedUiElementBoxBorderColor);
        if (savedUiElementBoxBorderOpacity != null)
            saved.uiElementBoxBorderOpacity(savedUiElementBoxBorderOpacity);
        if (savedHotspotBoxColor != null)
            saved.hotspotBoxColor(savedHotspotBoxColor);
        if (savedHotspotBoxOpacity != null)
            saved.hotspotBoxOpacity(savedHotspotBoxOpacity);
        if (savedHotspotBoxBorderColor != null)
            saved.hotspotBoxBorderColor(savedHotspotBoxBorderColor);
        if (savedHotspotBoxBorderOpacity != null)
            saved.hotspotBoxBorderOpacity(savedHotspotBoxBorderOpacity);
        return saved.build(null);
    }

    public HintMeshStyle withTransitionsOf(HintMeshStyle other) {
        if (transition.equals(other.transition) && fade.equals(other.fade))
            return this;
        HintMeshStyleBuilder builder = new HintMeshStyleBuilder(this);
        builder.transition = new Transition.TransitionBuilder(other.transition);
        builder.fade = new Transition.TransitionBuilder(other.fade);
        return builder.build(null);
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
        private Boolean groupBoxEnabled;
        private Double groupBoxBorderThickness;
        private Double groupBoxBorderLength;
        private Color groupBoxBorderColor;
        private Double groupBoxBorderOpacity;
        private Boolean groupBoxFramed;
        private GradientColor uiElementBoxColor;
        private Double uiElementBoxOpacity;
        private Double uiElementBoxBorderThickness;
        private Double uiElementBoxBorderLength;
        private Color uiElementBoxBorderColor;
        private Double uiElementBoxBorderOpacity;
        private Double uiElementBoxBorderRadius;
        private Double uiElementBoxHorizontalPadding;
        private Double uiElementBoxVerticalPadding;
        private Shadow.ShadowBuilder uiElementBoxShadow = new Shadow.ShadowBuilder();
        private Double hotspotBoxWidth;
        private Double hotspotBoxHeight;
        private GradientColor hotspotBoxColor;
        private Double hotspotBoxOpacity;
        private Double hotspotBoxBorderThickness;
        private Color hotspotBoxBorderColor;
        private Double hotspotBoxBorderOpacity;
        private Double hotspotBoxBorderRadius;
        private Shadow.ShadowBuilder hotspotBoxShadow = new Shadow.ShadowBuilder();
        private Double hotspotLineThickness;
        private Color hotspotLineColor;
        private Double hotspotLineOpacity;
        private Shadow.ShadowBuilder hotspotLineShadow = new Shadow.ShadowBuilder();
        private GradientColor savedBoxColor;
        private Double savedBoxOpacity;
        private Color savedBoxBorderColor;
        private Double savedBoxBorderOpacity;
        private GradientColor savedUiElementBoxColor;
        private Double savedUiElementBoxOpacity;
        private Color savedUiElementBoxBorderColor;
        private Double savedUiElementBoxBorderOpacity;
        private GradientColor savedHotspotBoxColor;
        private Double savedHotspotBoxOpacity;
        private Color savedHotspotBoxBorderColor;
        private Double savedHotspotBoxBorderOpacity;
        private Double boxWidthPercent;
        private Double boxHeightPercent;
        private Double boxHorizontalPadding;
        private Double boxVerticalPadding;
        // Index 0 = whole-area decoration, 1 = subdecoration, 2 = subsubdecoration.
        private final List<Decoration.DecorationBuilder> decorations = new ArrayList<>(
                List.of(new Decoration.DecorationBuilder(),
                        new Decoration.DecorationBuilder(),
                        new Decoration.DecorationBuilder()));
        private Transition.TransitionBuilder transition = new Transition.TransitionBuilder();
        private Transition.TransitionBuilder fade = new Transition.TransitionBuilder();
        private Color backgroundColor;
        private Double backgroundOpacity;
        private Double backgroundBorderThickness;
        private Color backgroundBorderColor;
        private Double backgroundBorderOpacity;
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
            this.groupBoxEnabled = style.groupBoxEnabled;
            this.groupBoxBorderThickness = style.groupBoxBorderThickness;
            this.groupBoxBorderLength = style.groupBoxBorderLength;
            this.groupBoxBorderColor = style.groupBoxBorderColor;
            this.groupBoxBorderOpacity = style.groupBoxBorderOpacity;
            this.groupBoxFramed = style.groupBoxFramed;
            this.uiElementBoxColor = style.uiElementBoxColor;
            this.uiElementBoxOpacity = style.uiElementBoxOpacity;
            this.uiElementBoxBorderThickness = style.uiElementBoxBorderThickness;
            this.uiElementBoxBorderLength = style.uiElementBoxBorderLength;
            this.uiElementBoxBorderColor = style.uiElementBoxBorderColor;
            this.uiElementBoxBorderOpacity = style.uiElementBoxBorderOpacity;
            this.uiElementBoxBorderRadius = style.uiElementBoxBorderRadius;
            this.uiElementBoxHorizontalPadding = style.uiElementBoxHorizontalPadding;
            this.uiElementBoxVerticalPadding = style.uiElementBoxVerticalPadding;
            this.uiElementBoxShadow = new Shadow.ShadowBuilder(style.uiElementBoxShadow);
            this.hotspotBoxWidth = style.hotspotBoxWidth;
            this.hotspotBoxHeight = style.hotspotBoxHeight;
            this.hotspotBoxColor = style.hotspotBoxColor;
            this.hotspotBoxOpacity = style.hotspotBoxOpacity;
            this.hotspotBoxBorderThickness = style.hotspotBoxBorderThickness;
            this.hotspotBoxBorderColor = style.hotspotBoxBorderColor;
            this.hotspotBoxBorderOpacity = style.hotspotBoxBorderOpacity;
            this.hotspotBoxBorderRadius = style.hotspotBoxBorderRadius;
            this.hotspotBoxShadow = new Shadow.ShadowBuilder(style.hotspotBoxShadow);
            this.hotspotLineThickness = style.hotspotLineThickness;
            this.hotspotLineColor = style.hotspotLineColor;
            this.hotspotLineOpacity = style.hotspotLineOpacity;
            this.hotspotLineShadow = new Shadow.ShadowBuilder(style.hotspotLineShadow);
            this.savedBoxColor = style.savedBoxColor;
            this.savedBoxOpacity = style.savedBoxOpacity;
            this.savedBoxBorderColor = style.savedBoxBorderColor;
            this.savedBoxBorderOpacity = style.savedBoxBorderOpacity;
            this.savedUiElementBoxColor = style.savedUiElementBoxColor;
            this.savedUiElementBoxOpacity = style.savedUiElementBoxOpacity;
            this.savedUiElementBoxBorderColor = style.savedUiElementBoxBorderColor;
            this.savedUiElementBoxBorderOpacity = style.savedUiElementBoxBorderOpacity;
            this.savedHotspotBoxColor = style.savedHotspotBoxColor;
            this.savedHotspotBoxOpacity = style.savedHotspotBoxOpacity;
            this.savedHotspotBoxBorderColor = style.savedHotspotBoxBorderColor;
            this.savedHotspotBoxBorderOpacity = style.savedHotspotBoxBorderOpacity;
            this.boxWidthPercent = style.boxWidthPercent;
            this.boxHeightPercent = style.boxHeightPercent;
            this.boxHorizontalPadding = style.boxHorizontalPadding;
            this.boxVerticalPadding = style.boxVerticalPadding;
            this.decorations.clear();
            for (Decoration decoration : style.decorations)
                this.decorations.add(decoration.builder());
            this.transition = new Transition.TransitionBuilder(style.transition);
            this.fade = new Transition.TransitionBuilder(style.fade);
            this.backgroundColor = style.backgroundColor;
            this.backgroundOpacity = style.backgroundOpacity;
            this.backgroundBorderThickness = style.backgroundBorderThickness;
            this.backgroundBorderColor = style.backgroundBorderColor;
            this.backgroundBorderOpacity = style.backgroundBorderOpacity;
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

        public HintMeshStyleBuilder groupBoxEnabled(Boolean groupBoxEnabled) {
            this.groupBoxEnabled = groupBoxEnabled;
            return this;
        }

        public HintMeshStyleBuilder groupBoxBorderThickness(Double groupBoxBorderThickness) {
            this.groupBoxBorderThickness = groupBoxBorderThickness;
            return this;
        }

        public HintMeshStyleBuilder groupBoxBorderLength(Double groupBoxBorderLength) {
            this.groupBoxBorderLength = groupBoxBorderLength;
            return this;
        }

        public HintMeshStyleBuilder groupBoxBorderColor(Color groupBoxBorderColor) {
            this.groupBoxBorderColor = groupBoxBorderColor;
            return this;
        }

        public HintMeshStyleBuilder groupBoxBorderOpacity(Double groupBoxBorderOpacity) {
            this.groupBoxBorderOpacity = groupBoxBorderOpacity;
            return this;
        }

        public HintMeshStyleBuilder groupBoxFramed(Boolean groupBoxFramed) {
            this.groupBoxFramed = groupBoxFramed;
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

        public HintMeshStyleBuilder savedBoxColor(GradientColor savedBoxColor) {
            this.savedBoxColor = savedBoxColor;
            return this;
        }

        public HintMeshStyleBuilder savedBoxOpacity(Double savedBoxOpacity) {
            this.savedBoxOpacity = savedBoxOpacity;
            return this;
        }

        public HintMeshStyleBuilder savedBoxBorderColor(Color savedBoxBorderColor) {
            this.savedBoxBorderColor = savedBoxBorderColor;
            return this;
        }

        public HintMeshStyleBuilder savedBoxBorderOpacity(Double savedBoxBorderOpacity) {
            this.savedBoxBorderOpacity = savedBoxBorderOpacity;
            return this;
        }

        public HintMeshStyleBuilder hotspotBoxWidth(Double hotspotBoxWidth) {
            this.hotspotBoxWidth = hotspotBoxWidth;
            return this;
        }

        public HintMeshStyleBuilder hotspotBoxHeight(Double hotspotBoxHeight) {
            this.hotspotBoxHeight = hotspotBoxHeight;
            return this;
        }

        public HintMeshStyleBuilder hotspotBoxColor(GradientColor hotspotBoxColor) {
            this.hotspotBoxColor = hotspotBoxColor;
            return this;
        }

        public HintMeshStyleBuilder hotspotBoxOpacity(Double hotspotBoxOpacity) {
            this.hotspotBoxOpacity = hotspotBoxOpacity;
            return this;
        }

        public HintMeshStyleBuilder hotspotBoxBorderThickness(Double hotspotBoxBorderThickness) {
            this.hotspotBoxBorderThickness = hotspotBoxBorderThickness;
            return this;
        }

        public HintMeshStyleBuilder hotspotBoxBorderColor(Color hotspotBoxBorderColor) {
            this.hotspotBoxBorderColor = hotspotBoxBorderColor;
            return this;
        }

        public HintMeshStyleBuilder hotspotBoxBorderOpacity(Double hotspotBoxBorderOpacity) {
            this.hotspotBoxBorderOpacity = hotspotBoxBorderOpacity;
            return this;
        }

        public HintMeshStyleBuilder hotspotBoxBorderRadius(Double hotspotBoxBorderRadius) {
            this.hotspotBoxBorderRadius = hotspotBoxBorderRadius;
            return this;
        }

        public HintMeshStyleBuilder hotspotLineThickness(Double hotspotLineThickness) {
            this.hotspotLineThickness = hotspotLineThickness;
            return this;
        }

        public HintMeshStyleBuilder hotspotLineColor(Color hotspotLineColor) {
            this.hotspotLineColor = hotspotLineColor;
            return this;
        }

        public HintMeshStyleBuilder hotspotLineOpacity(Double hotspotLineOpacity) {
            this.hotspotLineOpacity = hotspotLineOpacity;
            return this;
        }

        public HintMeshStyleBuilder savedHotspotBoxColor(GradientColor savedHotspotBoxColor) {
            this.savedHotspotBoxColor = savedHotspotBoxColor;
            return this;
        }

        public HintMeshStyleBuilder savedHotspotBoxOpacity(Double savedHotspotBoxOpacity) {
            this.savedHotspotBoxOpacity = savedHotspotBoxOpacity;
            return this;
        }

        public HintMeshStyleBuilder savedHotspotBoxBorderColor(Color savedHotspotBoxBorderColor) {
            this.savedHotspotBoxBorderColor = savedHotspotBoxBorderColor;
            return this;
        }

        public HintMeshStyleBuilder savedHotspotBoxBorderOpacity(Double savedHotspotBoxBorderOpacity) {
            this.savedHotspotBoxBorderOpacity = savedHotspotBoxBorderOpacity;
            return this;
        }

        public HintMeshStyleBuilder savedUiElementBoxColor(GradientColor savedUiElementBoxColor) {
            this.savedUiElementBoxColor = savedUiElementBoxColor;
            return this;
        }

        public HintMeshStyleBuilder savedUiElementBoxOpacity(Double savedUiElementBoxOpacity) {
            this.savedUiElementBoxOpacity = savedUiElementBoxOpacity;
            return this;
        }

        public HintMeshStyleBuilder savedUiElementBoxBorderColor(Color savedUiElementBoxBorderColor) {
            this.savedUiElementBoxBorderColor = savedUiElementBoxBorderColor;
            return this;
        }

        public HintMeshStyleBuilder savedUiElementBoxBorderOpacity(Double savedUiElementBoxBorderOpacity) {
            this.savedUiElementBoxBorderOpacity = savedUiElementBoxBorderOpacity;
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

        public HintMeshStyleBuilder backgroundColor(Color backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        public HintMeshStyleBuilder backgroundOpacity(Double backgroundOpacity) {
            this.backgroundOpacity = backgroundOpacity;
            return this;
        }

        public HintMeshStyleBuilder backgroundBorderThickness(Double backgroundBorderThickness) {
            this.backgroundBorderThickness = backgroundBorderThickness;
            return this;
        }

        public HintMeshStyleBuilder backgroundBorderColor(Color backgroundBorderColor) {
            this.backgroundBorderColor = backgroundBorderColor;
            return this;
        }

        public HintMeshStyleBuilder backgroundBorderOpacity(Double backgroundBorderOpacity) {
            this.backgroundBorderOpacity = backgroundBorderOpacity;
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

        public HintMeshStyleBuilder prefixFontStyle(HintFontStyle prefixFontStyle) {
            this.prefixFontStyle = prefixFontStyle.builder();
            return this;
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

        public Boolean groupBoxEnabled() {
            return groupBoxEnabled;
        }

        public Double groupBoxBorderThickness() {
            return groupBoxBorderThickness;
        }

        public Double groupBoxBorderLength() {
            return groupBoxBorderLength;
        }

        public Color groupBoxBorderColor() {
            return groupBoxBorderColor;
        }

        public Double groupBoxBorderOpacity() {
            return groupBoxBorderOpacity;
        }

        public Boolean groupBoxFramed() {
            return groupBoxFramed;
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

        public Shadow.ShadowBuilder uiElementBoxShadow() {
            return uiElementBoxShadow;
        }

        public Double hotspotBoxWidth() {
            return hotspotBoxWidth;
        }

        public Double hotspotBoxHeight() {
            return hotspotBoxHeight;
        }

        public GradientColor hotspotBoxColor() {
            return hotspotBoxColor;
        }

        public Double hotspotBoxOpacity() {
            return hotspotBoxOpacity;
        }

        public Double hotspotBoxBorderThickness() {
            return hotspotBoxBorderThickness;
        }

        public Color hotspotBoxBorderColor() {
            return hotspotBoxBorderColor;
        }

        public Double hotspotBoxBorderOpacity() {
            return hotspotBoxBorderOpacity;
        }

        public Double hotspotBoxBorderRadius() {
            return hotspotBoxBorderRadius;
        }

        public Shadow.ShadowBuilder hotspotBoxShadow() {
            return hotspotBoxShadow;
        }

        public Double hotspotLineThickness() {
            return hotspotLineThickness;
        }

        public Color hotspotLineColor() {
            return hotspotLineColor;
        }

        public Double hotspotLineOpacity() {
            return hotspotLineOpacity;
        }

        public Shadow.ShadowBuilder hotspotLineShadow() {
            return hotspotLineShadow;
        }

        public GradientColor savedBoxColor() {
            return savedBoxColor;
        }

        public Double savedBoxOpacity() {
            return savedBoxOpacity;
        }

        public Color savedBoxBorderColor() {
            return savedBoxBorderColor;
        }

        public Double savedBoxBorderOpacity() {
            return savedBoxBorderOpacity;
        }

        public GradientColor savedUiElementBoxColor() {
            return savedUiElementBoxColor;
        }

        public Double savedUiElementBoxOpacity() {
            return savedUiElementBoxOpacity;
        }

        public Color savedUiElementBoxBorderColor() {
            return savedUiElementBoxBorderColor;
        }

        public Double savedUiElementBoxBorderOpacity() {
            return savedUiElementBoxBorderOpacity;
        }

        public GradientColor savedHotspotBoxColor() {
            return savedHotspotBoxColor;
        }

        public Double savedHotspotBoxOpacity() {
            return savedHotspotBoxOpacity;
        }

        public Color savedHotspotBoxBorderColor() {
            return savedHotspotBoxBorderColor;
        }

        public Double savedHotspotBoxBorderOpacity() {
            return savedHotspotBoxBorderOpacity;
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

        public Transition.TransitionBuilder transition() {
            return transition;
        }

        public Transition.TransitionBuilder fade() {
            return fade;
        }

        public Color backgroundColor() {
            return backgroundColor;
        }

        public Double backgroundOpacity() {
            return backgroundOpacity;
        }

        public Double backgroundBorderThickness() {
            return backgroundBorderThickness;
        }

        public Color backgroundBorderColor() {
            return backgroundBorderColor;
        }

        public Double backgroundBorderOpacity() {
            return backgroundBorderOpacity;
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
                uiElementBoxShadow.extend(new Shadow.ShadowBuilder(defaultStyle.uiElementBoxShadow));
                hotspotBoxShadow.extend(new Shadow.ShadowBuilder(defaultStyle.hotspotBoxShadow));
                hotspotLineShadow.extend(new Shadow.ShadowBuilder(defaultStyle.hotspotLineShadow));
                transition.extend(new Transition.TransitionBuilder(defaultStyle.transition));
                fade.extend(new Transition.TransitionBuilder(defaultStyle.fade));
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
                    groupBoxEnabled == null ? defaultStyle.groupBoxEnabled : groupBoxEnabled,
                    groupBoxBorderThickness == null ? defaultStyle.groupBoxBorderThickness : groupBoxBorderThickness,
                    groupBoxBorderLength == null ? defaultStyle.groupBoxBorderLength : groupBoxBorderLength,
                    groupBoxBorderColor == null ? defaultStyle.groupBoxBorderColor : groupBoxBorderColor,
                    groupBoxBorderOpacity == null ? defaultStyle.groupBoxBorderOpacity : groupBoxBorderOpacity,
                    groupBoxFramed == null ? defaultStyle.groupBoxFramed : groupBoxFramed,
                    uiElementBoxColor == null ? defaultStyle.uiElementBoxColor : uiElementBoxColor,
                    uiElementBoxOpacity == null ? defaultStyle.uiElementBoxOpacity : uiElementBoxOpacity,
                    uiElementBoxBorderThickness == null ? defaultStyle.uiElementBoxBorderThickness : uiElementBoxBorderThickness,
                    uiElementBoxBorderLength == null ? defaultStyle.uiElementBoxBorderLength : uiElementBoxBorderLength,
                    uiElementBoxBorderColor == null ? defaultStyle.uiElementBoxBorderColor : uiElementBoxBorderColor,
                    uiElementBoxBorderOpacity == null ? defaultStyle.uiElementBoxBorderOpacity : uiElementBoxBorderOpacity,
                    uiElementBoxBorderRadius == null ? defaultStyle.uiElementBoxBorderRadius : uiElementBoxBorderRadius,
                    uiElementBoxHorizontalPadding == null ? defaultStyle.uiElementBoxHorizontalPadding : uiElementBoxHorizontalPadding,
                    uiElementBoxVerticalPadding == null ? defaultStyle.uiElementBoxVerticalPadding : uiElementBoxVerticalPadding,
                    uiElementBoxShadow.build(),
                    hotspotBoxWidth == null ? defaultStyle.hotspotBoxWidth : hotspotBoxWidth,
                    hotspotBoxHeight == null ? defaultStyle.hotspotBoxHeight : hotspotBoxHeight,
                    hotspotBoxColor == null ? defaultStyle.hotspotBoxColor : hotspotBoxColor,
                    hotspotBoxOpacity == null ? defaultStyle.hotspotBoxOpacity : hotspotBoxOpacity,
                    hotspotBoxBorderThickness == null ? defaultStyle.hotspotBoxBorderThickness : hotspotBoxBorderThickness,
                    hotspotBoxBorderColor == null ? defaultStyle.hotspotBoxBorderColor : hotspotBoxBorderColor,
                    hotspotBoxBorderOpacity == null ? defaultStyle.hotspotBoxBorderOpacity : hotspotBoxBorderOpacity,
                    hotspotBoxBorderRadius == null ? defaultStyle.hotspotBoxBorderRadius : hotspotBoxBorderRadius,
                    hotspotBoxShadow.build(),
                    hotspotLineThickness == null ? defaultStyle.hotspotLineThickness : hotspotLineThickness,
                    hotspotLineColor == null ? defaultStyle.hotspotLineColor : hotspotLineColor,
                    hotspotLineOpacity == null ? defaultStyle.hotspotLineOpacity : hotspotLineOpacity,
                    hotspotLineShadow.build(),
                    savedBoxColor == null && defaultStyle != null ? defaultStyle.savedBoxColor : savedBoxColor,
                    savedBoxOpacity == null && defaultStyle != null ? defaultStyle.savedBoxOpacity : savedBoxOpacity,
                    savedBoxBorderColor == null && defaultStyle != null ? defaultStyle.savedBoxBorderColor : savedBoxBorderColor,
                    savedBoxBorderOpacity == null && defaultStyle != null ? defaultStyle.savedBoxBorderOpacity : savedBoxBorderOpacity,
                    savedUiElementBoxColor == null && defaultStyle != null ? defaultStyle.savedUiElementBoxColor : savedUiElementBoxColor,
                    savedUiElementBoxOpacity == null && defaultStyle != null ? defaultStyle.savedUiElementBoxOpacity : savedUiElementBoxOpacity,
                    savedUiElementBoxBorderColor == null && defaultStyle != null ? defaultStyle.savedUiElementBoxBorderColor : savedUiElementBoxBorderColor,
                    savedUiElementBoxBorderOpacity == null && defaultStyle != null ? defaultStyle.savedUiElementBoxBorderOpacity : savedUiElementBoxBorderOpacity,
                    savedHotspotBoxColor == null && defaultStyle != null ? defaultStyle.savedHotspotBoxColor : savedHotspotBoxColor,
                    savedHotspotBoxOpacity == null && defaultStyle != null ? defaultStyle.savedHotspotBoxOpacity : savedHotspotBoxOpacity,
                    savedHotspotBoxBorderColor == null && defaultStyle != null ? defaultStyle.savedHotspotBoxBorderColor : savedHotspotBoxBorderColor,
                    savedHotspotBoxBorderOpacity == null && defaultStyle != null ? defaultStyle.savedHotspotBoxBorderOpacity : savedHotspotBoxBorderOpacity,
                    boxWidthPercent == null ? defaultStyle.boxWidthPercent : boxWidthPercent,
                    boxHeightPercent == null ? defaultStyle.boxHeightPercent : boxHeightPercent,
                    boxHorizontalPadding == null ? defaultStyle.boxHorizontalPadding : boxHorizontalPadding,
                    boxVerticalPadding == null ? defaultStyle.boxVerticalPadding : boxVerticalPadding,
                    builtDecorations,
                    transition.build(),
                    fade.build(),
                    backgroundColor == null ? defaultStyle.backgroundColor : backgroundColor,
                    backgroundOpacity == null ? defaultStyle.backgroundOpacity : backgroundOpacity,
                    backgroundBorderThickness == null ? defaultStyle.backgroundBorderThickness : backgroundBorderThickness,
                    backgroundBorderColor == null ? defaultStyle.backgroundBorderColor : backgroundBorderColor,
                    backgroundBorderOpacity == null ? defaultStyle.backgroundBorderOpacity : backgroundBorderOpacity,
                    backgroundBorderRadius == null ? defaultStyle.backgroundBorderRadius : backgroundBorderRadius,
                    labelOverride == null ? defaultStyle.labelOverride : labelOverride
            );
        }

    }

}

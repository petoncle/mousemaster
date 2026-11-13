# Overlay styles

Each style is a [parent mode](configuration-reference.md#inheritance) in a file of
[configuration/overlay-styles](../configuration/overlay-styles). A style sets only
[appearance properties](configuration-reference.md#hint-appearance), not the layout or the keys.

To use a style:

1. Add the lines of its file to your configuration.
2. In the parent modes of your hint mode, put the mode of the style in place of the style mode
   (`_hint-style-mode`, `_ui-hint-style-mode` or `_recursive-hint-style-mode`):

   ```properties
   ui-hint-mode=_indicator-mode _violet-style-mode
   ```

The properties of a mode come before those of its parent modes. Thus a property that you set in your
hint mode changes the style: `ui-hint-mode.overlay.font-size=12`. If your hint mode has no style
mode, add the mode of the style to its parent modes, and remove the properties of your hint mode
that the style also sets.

[Hint grid](#hint-grid) • [UI hints](#ui-hints) • [Recursive hint grid](#recursive-hint-grid)

## Hint grid

### [rainbow-grid](../configuration/overlay-styles/hint-grid/rainbow-grid.properties)

![rainbow-grid](assets/overlay-styles/rainbow-grid.webp)

### [two-tone-grid](../configuration/overlay-styles/hint-grid/two-tone-grid.properties)

![two-tone-grid](assets/overlay-styles/two-tone-grid.webp)

### [yellow](../configuration/overlay-styles/hint-grid/yellow.properties)

![yellow](assets/overlay-styles/yellow.webp)

### [rainbow-chips](../configuration/overlay-styles/hint-grid/rainbow-chips.properties)

![rainbow-chips](assets/overlay-styles/rainbow-chips.webp)

### [dark-tiles](../configuration/overlay-styles/hint-grid/dark-tiles.properties)

![dark-tiles](assets/overlay-styles/dark-tiles.webp)

### [neon-grid](../configuration/overlay-styles/hint-grid/neon-grid.properties)

![neon-grid](assets/overlay-styles/neon-grid.webp)

### [radial](../configuration/overlay-styles/hint-grid/radial.properties)

![radial](assets/overlay-styles/radial.webp)

### [hollow](../configuration/overlay-styles/hint-grid/hollow.properties)

![hollow](assets/overlay-styles/hollow.webp)

### [keycap-grid](../configuration/overlay-styles/hint-grid/keycap-grid.properties)

![keycap-grid](assets/overlay-styles/keycap-grid.webp)

### [chips-glow](../configuration/overlay-styles/hint-grid/chips-glow.properties)

![chips-glow](assets/overlay-styles/chips-glow.webp)

### [spectrum-lines](../configuration/overlay-styles/hint-grid/spectrum-lines.properties)

![spectrum-lines](assets/overlay-styles/spectrum-lines.webp)

### [spectrum-glow](../configuration/overlay-styles/hint-grid/spectrum-glow.properties)

![spectrum-glow](assets/overlay-styles/spectrum-glow.webp)

### [tint](../configuration/overlay-styles/hint-grid/tint.properties)

![tint](assets/overlay-styles/tint.webp)

### [thirds](../configuration/overlay-styles/hint-grid/thirds.properties)

![thirds](assets/overlay-styles/thirds.webp)

### [groups](../configuration/overlay-styles/hint-grid/groups.properties)

![groups](assets/overlay-styles/groups.webp)

### [night-grid](../configuration/overlay-styles/hint-grid/night-grid.properties)

![night-grid](assets/overlay-styles/night-grid.webp)

### [tiles](../configuration/overlay-styles/hint-grid/tiles.properties)

![tiles](assets/overlay-styles/tiles.webp)

### [ember-grid](../configuration/overlay-styles/hint-grid/ember-grid.properties)

![ember-grid](assets/overlay-styles/ember-grid.webp)

### [gold-grid](../configuration/overlay-styles/hint-grid/gold-grid.properties)

![gold-grid](assets/overlay-styles/gold-grid.webp)

### [rim-grid](../configuration/overlay-styles/hint-grid/rim-grid.properties)

![rim-grid](assets/overlay-styles/rim-grid.webp)

### [violet-grid](../configuration/overlay-styles/hint-grid/violet-grid.properties)

![violet-grid](assets/overlay-styles/violet-grid.webp)

### [prefix-glow](../configuration/overlay-styles/hint-grid/prefix-glow.properties)

![prefix-glow](assets/overlay-styles/prefix-glow.webp)

### [halo](../configuration/overlay-styles/hint-grid/halo.properties)

![halo](assets/overlay-styles/halo.webp)

### [blue-groups](../configuration/overlay-styles/hint-grid/blue-groups.properties)

![blue-groups](assets/overlay-styles/blue-groups.webp)

### [mono](../configuration/overlay-styles/hint-grid/mono.properties)

![mono](assets/overlay-styles/mono.webp)

### [blueprint](../configuration/overlay-styles/hint-grid/blueprint.properties)

![blueprint](assets/overlay-styles/blueprint.webp)

### [dots](../configuration/overlay-styles/hint-grid/dots.properties)

![dots](assets/overlay-styles/dots.webp)

### [crosses](../configuration/overlay-styles/hint-grid/crosses.properties)

![crosses](assets/overlay-styles/crosses.webp)

## UI hints

### [rainbow](../configuration/overlay-styles/ui-hints/rainbow.properties)

![rainbow](assets/overlay-styles/rainbow.webp)

### [rainbow-glow-rim](../configuration/overlay-styles/ui-hints/rainbow-glow-rim.properties)

![rainbow-glow-rim](assets/overlay-styles/rainbow-glow-rim.webp)

### [crayon](../configuration/overlay-styles/ui-hints/crayon.properties)

![crayon](assets/overlay-styles/crayon.webp)

### [keycap](../configuration/overlay-styles/ui-hints/keycap.properties)

![keycap](assets/overlay-styles/keycap.webp)

### [rainbow-glow](../configuration/overlay-styles/ui-hints/rainbow-glow.properties)

![rainbow-glow](assets/overlay-styles/rainbow-glow.webp)

### [rim](../configuration/overlay-styles/ui-hints/rim.properties)

![rim](assets/overlay-styles/rim.webp)

### [paper](../configuration/overlay-styles/ui-hints/paper.properties)

![paper](assets/overlay-styles/paper.webp)

### [gradient-rim](../configuration/overlay-styles/ui-hints/gradient-rim.properties)

![gradient-rim](assets/overlay-styles/gradient-rim.webp)

### [dusk](../configuration/overlay-styles/ui-hints/dusk.properties)

![dusk](assets/overlay-styles/dusk.webp)

### [terminal](../configuration/overlay-styles/ui-hints/terminal.properties)

![terminal](assets/overlay-styles/terminal.webp)

### [viewfinder](../configuration/overlay-styles/ui-hints/viewfinder.properties)

![viewfinder](assets/overlay-styles/viewfinder.webp)

### [highlighter](../configuration/overlay-styles/ui-hints/highlighter.properties)

![highlighter](assets/overlay-styles/highlighter.webp)

### [sorbet](../configuration/overlay-styles/ui-hints/sorbet.properties)

![sorbet](assets/overlay-styles/sorbet.webp)

### [violet](../configuration/overlay-styles/ui-hints/violet.properties)

![violet](assets/overlay-styles/violet.webp)

### [aurora-glow](../configuration/overlay-styles/ui-hints/aurora-glow.properties)

![aurora-glow](assets/overlay-styles/aurora-glow.webp)

### [ghost](../configuration/overlay-styles/ui-hints/ghost.properties)

![ghost](assets/overlay-styles/ghost.webp)

### [meadow](../configuration/overlay-styles/ui-hints/meadow.properties)

![meadow](assets/overlay-styles/meadow.webp)

### [cream](../configuration/overlay-styles/ui-hints/cream.properties)

![cream](assets/overlay-styles/cream.webp)

### [gold](../configuration/overlay-styles/ui-hints/gold.properties)

![gold](assets/overlay-styles/gold.webp)

### [neon-glow](../configuration/overlay-styles/ui-hints/neon-glow.properties)

![neon-glow](assets/overlay-styles/neon-glow.webp)

### [spotlight](../configuration/overlay-styles/ui-hints/spotlight.properties)

![spotlight](assets/overlay-styles/spotlight.webp)

### [dark-keycap](../configuration/overlay-styles/ui-hints/dark-keycap.properties)

![dark-keycap](assets/overlay-styles/dark-keycap.webp)

### [metal](../configuration/overlay-styles/ui-hints/metal.properties)

![metal](assets/overlay-styles/metal.webp)

### [tropical](../configuration/overlay-styles/ui-hints/tropical.properties)

![tropical](assets/overlay-styles/tropical.webp)

### [glass](../configuration/overlay-styles/ui-hints/glass.properties)

![glass](assets/overlay-styles/glass.webp)

### [retro](../configuration/overlay-styles/ui-hints/retro.properties)

![retro](assets/overlay-styles/retro.webp)

### [sunset-shadow](../configuration/overlay-styles/ui-hints/sunset-shadow.properties)

![sunset-shadow](assets/overlay-styles/sunset-shadow.webp)

### [autumn](../configuration/overlay-styles/ui-hints/autumn.properties)

![autumn](assets/overlay-styles/autumn.webp)

### [wash](../configuration/overlay-styles/ui-hints/wash.properties)

![wash](assets/overlay-styles/wash.webp)

### [sticker](../configuration/overlay-styles/ui-hints/sticker.properties)

![sticker](assets/overlay-styles/sticker.webp)

### [sunset-text](../configuration/overlay-styles/ui-hints/sunset-text.properties)

![sunset-text](assets/overlay-styles/sunset-text.webp)

### [brutalist](../configuration/overlay-styles/ui-hints/brutalist.properties)

![brutalist](assets/overlay-styles/brutalist.webp)

### [rose](../configuration/overlay-styles/ui-hints/rose.properties)

![rose](assets/overlay-styles/rose.webp)

### [hairline](../configuration/overlay-styles/ui-hints/hairline.properties)

![hairline](assets/overlay-styles/hairline.webp)

### [berry](../configuration/overlay-styles/ui-hints/berry.properties)

![berry](assets/overlay-styles/berry.webp)

### [pastel-pills](../configuration/overlay-styles/ui-hints/pastel-pills.properties)

![pastel-pills](assets/overlay-styles/pastel-pills.webp)

### [chalk](../configuration/overlay-styles/ui-hints/chalk.properties)

![chalk](assets/overlay-styles/chalk.webp)

### [duotone](../configuration/overlay-styles/ui-hints/duotone.properties)

![duotone](assets/overlay-styles/duotone.webp)

### [ice](../configuration/overlay-styles/ui-hints/ice.properties)

![ice](assets/overlay-styles/ice.webp)

### [radial-glow](../configuration/overlay-styles/ui-hints/radial-glow.properties)

![radial-glow](assets/overlay-styles/radial-glow.webp)

### [synthwave](../configuration/overlay-styles/ui-hints/synthwave.properties)

![synthwave](assets/overlay-styles/synthwave.webp)

## Recursive hint grid

### [cross-dots](../configuration/overlay-styles/recursive-hint-grid/cross-dots.properties)

![cross-dots](assets/overlay-styles/cross-dots.webp)

### [sparkle](../configuration/overlay-styles/recursive-hint-grid/sparkle.properties)

![sparkle](assets/overlay-styles/sparkle.webp)

### [spectrum](../configuration/overlay-styles/recursive-hint-grid/spectrum.properties)

![spectrum](assets/overlay-styles/spectrum.webp)

### [thin-keys](../configuration/overlay-styles/recursive-hint-grid/thin-keys.properties)

![thin-keys](assets/overlay-styles/thin-keys.webp)

### [rings](../configuration/overlay-styles/recursive-hint-grid/rings.properties)

![rings](assets/overlay-styles/rings.webp)

### [fractal](../configuration/overlay-styles/recursive-hint-grid/fractal.properties)

![fractal](assets/overlay-styles/fractal.webp)

### [large-keycaps](../configuration/overlay-styles/recursive-hint-grid/large-keycaps.properties)

![large-keycaps](assets/overlay-styles/large-keycaps.webp)

### [tinted-marks](../configuration/overlay-styles/recursive-hint-grid/tinted-marks.properties)

![tinted-marks](assets/overlay-styles/tinted-marks.webp)

### [gradient-cells](../configuration/overlay-styles/recursive-hint-grid/gradient-cells.properties)

![gradient-cells](assets/overlay-styles/gradient-cells.webp)

### [frosted-cells](../configuration/overlay-styles/recursive-hint-grid/frosted-cells.properties)

![frosted-cells](assets/overlay-styles/frosted-cells.webp)

### [accent-previews](../configuration/overlay-styles/recursive-hint-grid/accent-previews.properties)

![accent-previews](assets/overlay-styles/accent-previews.webp)

### [cell-marks](../configuration/overlay-styles/recursive-hint-grid/cell-marks.properties)

![cell-marks](assets/overlay-styles/cell-marks.webp)

### [ticks](../configuration/overlay-styles/recursive-hint-grid/ticks.properties)

![ticks](assets/overlay-styles/ticks.webp)

### [serif](../configuration/overlay-styles/recursive-hint-grid/serif.properties)

![serif](assets/overlay-styles/serif.webp)

### [gold-serif](../configuration/overlay-styles/recursive-hint-grid/gold-serif.properties)

![gold-serif](assets/overlay-styles/gold-serif.webp)

### [gradient-crosses](../configuration/overlay-styles/recursive-hint-grid/gradient-crosses.properties)

![gradient-crosses](assets/overlay-styles/gradient-crosses.webp)

### [center-cross](../configuration/overlay-styles/recursive-hint-grid/center-cross.properties)

![center-cross](assets/overlay-styles/center-cross.webp)

### [spectrum-chips](../configuration/overlay-styles/recursive-hint-grid/spectrum-chips.properties)

![spectrum-chips](assets/overlay-styles/spectrum-chips.webp)

### [tinted-cells](../configuration/overlay-styles/recursive-hint-grid/tinted-cells.properties)

![tinted-cells](assets/overlay-styles/tinted-cells.webp)

# mousemaster configuration reference

A configuration file is a list of `<name>=<value>` properties, one on each line. A line that starts
with `#` is a comment. A line that ends with `\` continues on the next line. This reference
describes each property. For the combo syntax, see [combo-reference.md](combo-reference.md).

## Contents
- [Key aliases](#key-aliases)
- [Combos](#combos)
- [Modes](#modes)
- [Mode property mutation](#mode-property-mutation)
- [Target](#target)
- [Movement properties](#movement-properties)
- [Mouse commands](#mouse-commands)
- [Indicator properties](#indicator-properties)
- [Animations](#animations)
- [Cursor properties](#cursor-properties)
- [Zoom properties](#zoom-properties)
- [Transitions](#transitions)
- [Easings](#easings)
- [Overlay properties](#overlay-properties)
- [App aliases](#app-aliases)
- [Position history](#position-history)
- [Macros](#macros)
- [Virtual keys](#virtual-keys)
- [Console window, exit and restart](#console-window-exit-and-restart)
- [Logging](#logging)
- [Keyboard layout](#keyboard-layout)

## Key aliases

A key alias gives a name to one or more keys:

```properties
key-alias.exit=q p
key-alias.up=w
```

A combo can use the alias in place of a key. This combo completes when you press `q` or `p`:

```properties
normal-mode.to.idle-mode=+exit
```

- `key-alias.<name>.<layout>` declares the alias for one keyboard layout:
  `key-alias.up.us-qwerty=w`. mousemaster uses the declaration of the active layout. If no
  declaration has the active layout, mousemaster uses the declaration without a layout. For the
  layout names, see [Keyboard layout](#keyboard-layout).
- For the key names, see [key-list.md](key-list.md).

## Combos

A combo is a sequence of key presses and releases that runs a command. This table gives the basic
syntax. For the full syntax, see [combo-reference.md](combo-reference.md).

| Syntax             | Meaning                                                    |
|--------------------|------------------------------------------------------------|
| `+key`             | Press. mousemaster eats the press: the OS does not get it. |
| `#key`             | Press. The OS also gets the press.                         |
| `-key`             | Release.                                                   |
| `key`              | Tap: press, then release.                                  |
| `{+a +b}`          | Key presses and releases in any order.                     |
| `_{keys}`          | Precondition: the keys are pressed.                        |
| `^{keys}`          | Precondition: the keys are not pressed.                    |
| `combo1 \| combo2` | Alternative combos.                                        |
| `none`             | No combo.                                                  |

```properties
# Press e while leftctrl is pressed.
normal-mode.to.hint-mode=_{leftctrl} +e
# Press and release leftshift.
normal-mode.move-target-to.overlay-center=+leftshift -leftshift
# Two alternative combos for one command.
normal-mode.to.idle-mode=+q | +leftctrl +e
# Press a and b in any order (a chord).
normal-mode.to.grid-mode={+a +b}
```

## Modes

A mode is a state of mousemaster with its own combos and properties. A mode name ends with `-mode`.
A mode property has the format `<mode>.<property>=<value>`:

```properties
normal-mode.indicator.enabled=true
normal-mode.mouse.initial-velocity=1600
ui-hint-mode.overlay.type=ui-vision
```

- mousemaster starts in `idle-mode`. The configuration must give `idle-mode` at least one property.
- Each mode other than `idle-mode` must be reachable from `idle-mode` with mode switches, unless its
  name starts with `_`. Such a mode is only a [parent mode](#inheritance): no mode switch can go to
  it.

### Inheritance

A mode can take a set of properties from another mode:

```properties
slow-mode.indicator=normal-mode.indicator
slow-mode.indicator.fill-color=#0000FF
```

`slow-mode` takes all the `indicator` properties of `normal-mode`. Its own `fill-color` replaces the
`fill-color` of `normal-mode`.

A mode can also take all the properties of one or more parent modes:

```properties
slow-mode=_indicator-mode _clipboard-mode
```

- The properties of the mode come first, then each parent in order. The first one that sets a
  property gives its value.
- A set that names its source (`slow-mode.indicator=normal-mode.indicator`) takes its values from
  that source, not from a parent.
- A command set to `none` has no combo. The mode does not take the combos of the parent for that
  command: `slow-mode.press.middle=none`.

### Mode switches

`to.<mode>` switches to the mode:

```properties
idle-mode.to.normal-mode=_{leftctrl} +e
normal-mode.to.idle-mode=+q
```

A combo can start in one mode and continue in another:

```properties
normal-mode.press.left=+a
normal-mode.to.click-mode=+a
click-mode.release.left=+a -a
```

A press of `a` presses the left button and switches to `click-mode`. In `click-mode`, the release of
`a` releases the left button.

`break-combo-preparation` stops the combos that started before its own combo completed:

```properties
normal-mode.break-combo-preparation=+a
```

With this property, the `+a` of `normal-mode` does not continue in `click-mode`. `click-mode`
releases the left button only after a new press and release of `a`.

### Mode history

```properties
hint1-mode.push-mode-to-history-stack=true
hint2-mode.to.previous-mode-from-history-stack=+rightalt -rightalt
```

- **`push-mode-to-history-stack`**: If `true`, a switch from the mode to another mode adds the mode
  to the history stack. The stack holds each mode one time only. Default: `false`.
- **`to.previous-mode-from-history-stack`**: Switches to the last mode of the history stack.
- A switch to a mode that is in the stack removes that mode, and the modes added after it, from the
  stack.

### Other mode properties

```properties
idle-mode.stop-commands-from-previous-mode=true
normal-mode.mode-after-unhandled-key-press=idle-mode
```

- **`stop-commands-from-previous-mode`**: If `true`, a switch to the mode stops the commands of the
  previous mode. The moves and the wheel scrolls stop, and mousemaster releases the mouse buttons.
  Default: `true` for `idle-mode`, `false` for the other modes.
- **`mode-after-unhandled-key-press`**: The mode to switch to when you press a key that no combo of
  the mode can use at that time.

## Mode property mutation

A property can change its value while keys are pressed, without a mode switch:

```properties
<mode>.<property>=<default> | <combo1> -> <value1> | <combo2> -> <value2>
```

- The part without `->` is the default value.
- Each `<combo> -> <value>` part is a branch. A branch gives its value when its combo completes.
- If more than one branch applies at the same time, the rightmost branch gives the value.

```properties
hint-mode.overlay.box-border-color=#FFFFFF | _{leftshift} -> #00FFFF
```

The border is white. While `leftshift` is pressed, the border is cyan.

- If each branch of the property has only a precondition, such as `_{leftshift}`, the property
  returns to its default value when no precondition is true.
- If the property has a branch with a key sequence, such as `+move -> ...`, a value stays until
  another branch gives a value.
- After a mode switch, the value stays if the new mode has the same branch: the same combo with the
  same value. Two modes with the same parent mode can have the same branch. Any other mode switch
  sets the default value again.

A [virtual key](#virtual-keys) can control a mutation. A virtual key keeps its state after a mode
switch:

```properties
# z toggles iszoom. The zoom follows iszoom.
virtual-key.iszoom=released
hint-mode.macro.setiszoom=^{iszoom} +z -> #iszoom
hint-mode.macro.unsetiszoom=_{iszoom} +z -> ~iszoom
hint-mode.zoom.percent=1 | _{iszoom} -> 30
```

## Target

The target is the point where the mouse goes. These move the target: a hint selection,
`move-target-to`, `nudge-target`, `start-move-target`, `cycle-next`, `cycle-previous` and
`overlay.target-follows-overlay-center`.

- **`overlay.mouse-follows-target`**: If `true`, the target is at the cursor, and a command that
  moves the target moves the mouse. If `false`, the target is a separate point, and the mouse stays
  where it is. A change from `true` to `false` puts the target at the cursor. `move-to.target` moves
  the mouse to the target. Use `false` when the next mode shows another grid. Then
  `move-to.last-selected-hint` moves the mouse there. Default: `true`.

## Movement properties

Four sets of properties set the speed of a move:

| Set        | What moves                                                                                |
|------------|-------------------------------------------------------------------------------------------|
| `mouse`    | The mouse: `start-move`, `nudge` and the jumps.                                           |
| `target`   | The [target](#target), when the mouse does not follow it: `start-move-target`, `nudge-target`. |
| `hotspots` | The [hotspots](#hotspots): `start-move-hotspots`, `nudge-hotspots`.                       |
| `wheel`    | The wheel: `start-wheel`.                                                                 |

```properties
normal-mode.mouse.initial-velocity=1600
normal-mode.wheel.max-velocity=2000
```

| Property               | Default: `mouse`, `target`, `hotspots` | Default: `wheel` |
|------------------------|----------------------------------------|------------------|
| `initial-velocity`     | `1000`                                 | `1000`           |
| `max-velocity`         | `2200`                                 | `1000`           |
| `acceleration`         | `3000`                                 | `500`            |
| `acceleration-easing`  | `smootherstep`                         | `1`              |
| `deceleration`         | `20`                                   | `0`              |
| `nudge-distance`       | `1`                                    | Does not apply   |
| `smooth-jump-enabled`  | `true` (`mouse` only)                  | Does not apply   |
| `smooth-jump-velocity` | `30000` (`mouse` only)                 | Does not apply   |

- **Units**: For `mouse`, `target` and `hotspots`, a velocity is in pixels per second and a distance
  is in pixels, at a 100% scale. On a 200% screen, the mouse moves two times more pixels. For
  `wheel`, the unit is a scroll unit, and the screen scale does not apply. On Windows, 120 scroll
  units are one wheel notch. On macOS, a scroll unit is a scroll pixel.
- **Speed**: The speed starts at `initial-velocity` and increases to `max-velocity`. `t` is the
  time in seconds since the move started:

  | `acceleration-easing`                                       | Speed, up to `max-velocity`                   |
  |-------------------------------------------------------------|-----------------------------------------------|
  | A number `p`                                                | `initial-velocity + acceleration × tᵖ`        |
  | `logarithmic`                                               | `initial-velocity + acceleration × ln(1 + t)` |
  | `exponential`                                               | `initial-velocity + acceleration × (eᵗ − 1)`  |
  | `smoothstep`, `smootherstep`, `exponential-out-<n>`, `step` | The [easing](#easings) from `initial-velocity` to `max-velocity`, in `(max-velocity − initial-velocity) / acceleration` seconds |

  If `initial-velocity` is equal to or more than `max-velocity`, the speed is immediately
  `max-velocity`.
- **`deceleration`**: After you release the move keys, the speed decreases to 0 as
  `speed × e^(−deceleration × t)`. `0` stops the move immediately.
- **`nudge-distance`**: The distance of a nudge.
- **`smooth-jump-enabled`**: When mousemaster puts the mouse at a new position, for example after a
  hint selection, the mouse jumps there. If `true`, the mouse moves there with a
  [`smootherstep`](#easings) curve. If `false`, the mouse goes there immediately.
- **`smooth-jump-velocity`**: The average speed of a smooth jump: the jump takes
  `distance / smooth-jump-velocity` seconds.

## Mouse commands

```properties
normal-mode.start-move.up=+i
normal-mode.stop-move.up=-i
normal-mode.nudge.up=_{leftalt} +i
# _{none | leftctrl}: the combo also completes while leftctrl is pressed, for a ctrl-click.
normal-mode.press.left=_{none | leftctrl} +f
normal-mode.release.left=-f
normal-mode.toggle.right=_{none | leftctrl} +g
normal-mode.start-wheel.down=+n
normal-mode.stop-wheel.down=-n
```

- **`start-move.<direction>`** / **`stop-move.<direction>`**: Start and stop a move of the mouse,
  with the `mouse` speed. The directions are `up`, `down`, `left` and `right`.
- **`nudge.<direction>`**: Moves the mouse by `mouse.nudge-distance`.
- **`press.<button>`** / **`release.<button>`** / **`toggle.<button>`**: Press, release or toggle
  a mouse button. The buttons are `left`, `middle` and `right`.
- **`start-wheel.<direction>`** / **`stop-wheel.<direction>`**: Start and stop a wheel scroll, with
  the `wheel` speed. The directions are `up`, `down`, `left` and `right`.

## Indicator properties

The indicator is one or more shapes that mousemaster draws on the screen, by default at the cursor.
Each shape is a layer, with a fill and a stroke. `<mode>.indicator.<property>` sets the `indicator`
layer. `<mode>.<name>-indicator.<property>` adds a layer with that name:

```properties
normal-mode.indicator.enabled=true
normal-mode.ring-indicator.size=40
normal-mode.ring-indicator.fill-opacity=0
```

- Each layer starts from the same defaults. A layer does not take the values of the `indicator`
  layer.
- A mode that sets a property of a layer, and not its `enabled`, enables that layer.
- A layer takes the properties of the layer with the same name in the parent modes.
- `<mode>.indicator=<other-mode>.indicator` takes all the layers of the other mode.
- A length is in pixels at a 100% scale. On a 200% screen, it is two times larger.
- An opacity is from `0` to `1`.
- `render-as-cursor` and `exclude-from-capture` apply to the whole indicator. Set them on
  `<mode>.indicator`. They do not enable a layer.

### Colors

A color property takes:

- A hex color, such as `#FF0000`.
- A [gradient](#gradients), but not `in-turn`, `per-group`, `across-group` or `across-all-hints`.
  The gradient spans the active screen. A layer takes the color of the gradient at its center. With
  `per-pixel`, the gradient shows inside the layer. With `across-box`, the gradient spans the layer.
- A [color alias](#gradients).
- `last-selected-hint-box-color`: the `box-color` of the last selected hint, gradient included.
- `last-selected-hint-box-center-color`: the one color at the center of the box of the last selected
  hint.

Before the first hint selection, the last two are black.

```properties
# A click shows in the color of the hint that you selected.
_hint-indicator-mode.indicator.fill-color=last-selected-hint-box-center-color
```

### Mouse state

A [mutation](#mode-property-mutation) on a
[built-in virtual key](combo-reference.md#built-in-virtual-keys) gives a property a value for a
mouse state:

```properties
normal-mode.indicator.fill-color=#FF0000 | _{iswheeling} -> #FFFF00 | _{ismousepressing} -> #00FF00
normal-mode.indicator.fill-opacity=0 | _{isleftmousepressing ismoving} -> 1
```

### Shape

| Property         | Default  | Description |
|------------------|----------|-------------|
| `enabled`        | `false`  | If `true`, the layer shows. |
| `z`              | `0`      | The order of the layers and the overlay. A higher `z` is on top. |
| `shape`          | `circle` | `circle`, `triangle`, `rectangle`, `star`, `line`, `cross`, `path` or `text`. |
| `size`           | `26`     | The longest side of the box of the shape. |
| `aspect-ratio`   | `1`      | The width of the box divided by its height. |
| `border-radius`  | `0`      | The radius of the corners of a `rectangle`, a `triangle`, a `star` or a `path`. |
| `points`         | –        | The points of a `path`, such as `0,0 10,0 5,8`. Give at least three points. They cannot all have the same x, or all have the same y. The unit does not matter: mousemaster scales the points to the box. |
| `x` / `y`        | `0`      | An offset from the position of the layer. A positive `x` goes right. A positive `y` goes down. |
| `distance`       | `0`      | An offset in the direction of `angle`. |
| `angle`          | `0`      | The direction of `distance`, in degrees clockwise from 12 o'clock. |
| `rotation`       | `0`      | A rotation around the center of the layer, in degrees clockwise. |

- The overlay has its own `z` (`overlay.z`). A layer with a lower `z` is behind the overlay. At the
  same `z`, a layer is above the overlay. At the same `z`, a layer that follows the cursor or the
  screen with the cursor is below the other layers.
- On a layer with one position, a `triangle`, a `star` and a `path` keep the proportions of their
  points. `aspect-ratio` then stretches them.
- An animation of `angle` moves the layer around its position. `rotation` turns the layer where it
  is. `distance=30` with `angle=0 | _{orbitanimation} -> 0% 0; 100% 360` turns the layer around the
  cursor (see [Keyframes](#keyframes)).

### Fill and stroke

| Property                | Default   | Description |
|-------------------------|-----------|-------------|
| `fill-color`            | `#FF0000` | The color of the inside of the shape. |
| `fill-opacity`          | `0.2`     | The opacity of the inside. |
| `stroke-thickness`      | `1`       | The thickness of the stroke. The stroke is centered on the edge of the shape. |
| `stroke-color`          | `#FF0000` | The color of the stroke. |
| `stroke-opacity`        | `1`       | The opacity of the stroke. |
| `stroke-length-percent` | `1`       | The part of the stroke that shows, from `-1` to `1`. A negative value goes counterclockwise. |
| `stroke-start-angle`    | `0`       | The start of a partial stroke, in degrees clockwise from 12 o'clock. |
| `stroke-anchor`         | `start`   | `start` grows a partial stroke from its start angle. `middle` grows it in the two directions. |
| `stroke-dash-length`    | `0`       | The length of a dash. |
| `stroke-dash-gap`       | `0`       | The gap between two dashes. The dashes show only if the length and the gap are more than `0`. |
| `stroke-dash-offset`    | `0`       | The offset of the dashes along the stroke. |
| `stroke-cap`            | `flat`    | The ends of the stroke and of the dashes: `flat`, `round` or `square`. |

- For a second stroke, add a second layer.
- A `line`, a `cross` and the letters of a `text` always have their full stroke.

### Text

| Property           | Default  | Description |
|--------------------|----------|-------------|
| `text`             | –        | The text. |
| `font-name`        | `Arial`  | The font. |
| `font-size`        | `8`      | The size of the font, in points. |
| `font-weight`      | `normal` | `thin`, `extra-light`, `light`, `normal`, `medium`, `demi-bold`, `bold`, `extra-bold` or `black`. |
| `text-style-sheet` | –        | CSS for a rich text (see below). |

With `shape=text`, the letters are the shape. The fill colors them and the stroke draws their edges.
The font sets their size: `size` does not apply. An `aspect-ratio` other than `1` cuts the letters.

```properties
typing-mode.text-indicator.shape=text
typing-mode.text-indicator.text=n
typing-mode.text-indicator.font-name=Consolas | _{ismacos} -> Menlo
```

With another shape, the text is rich text: the HTML subset that Qt draws. The text shows over the
shape. The shape takes the size of the text: `size` and `aspect-ratio` do not apply. The fill, the
stroke and `border-radius` style the box around the text. `text-style-sheet` styles the text with
CSS:

```properties
normal-mode.cheatsheet-indicator.shape=rectangle
normal-mode.cheatsheet-indicator.position=active-screen-center
normal-mode.cheatsheet-indicator.text-style-sheet=td { color: #A9B1D6; } .key { color: #E0AF68; }
normal-mode.cheatsheet-indicator.text=<table cellpadding=14><tr><td class=key>{exit}<td>exit</table>
normal-mode.cheatsheet-indicator.border-radius=14
normal-mode.cheatsheet-indicator.fill-color=#1A1B26
```

- The letters take their color from the style sheet. Without it, they take the text color of the
  system, usually black. Qt ignores a `body` rule: put a default color on the elements of the text,
  such as `td`.
- `font-size` sets the size of the whole text. In the style sheet, use relative sizes: `small`,
  `medium`, `large`, `x-large` and `xx-large`. An absolute size ignores `font-size`.
- The padding of an outer table is the margin between the text and its box. The width of the table
  is where the text wraps: `<table width=200>`.

Keys in a text:

- `{alias}` shows the names of the keys of a [key alias](#key-aliases): `{exit}` shows `q p`.
- If the value comes from a combo, `{alias}` shows the symbols of the alias keys that the combo
  pressed: `I`, `⇧`, `␣`. `{!alias}` shows the key that a `+!alias` pressed.
- A remap between the combo and the value gives each key its own text: with
  `| +move -> move=up left down right -> {move}`, a press of `j` shows `left` (see
  [alias remap](combo-reference.md#alias-remap)).
- A value from a combo stays after the release of its keys. A text can show the last key that you
  pressed while an animation runs:

  ```properties
  key-alias.move=i j k l
  keycast-animation.duration-millis=1000
  normal-mode.start.keycast-animation=+move
  normal-mode.keycast-indicator.enabled=false | _{keycastanimation} -> true
  normal-mode.keycast-indicator.shape=rectangle
  normal-mode.keycast-indicator.text-style-sheet=td { color: #FFFFFF; }
  normal-mode.keycast-indicator.text= | +move -> <table cellpadding=6><tr><td>You pressed <b>{move}</b></table>
  ```

### Position

| Property              | Default | Description |
|-----------------------|---------|-------------|
| `position`            | `mouse` | Where the layer is (see the values below). |
| `end-position`        | –       | Where a layer that runs between two points stops. It takes the values of `position`. |
| `position-anchor`     | `point` | Where the layer starts on a hint (see below). |
| `end-position-anchor` | `point` | Where the layer stops on a hint (see below). |
| `follow`              | `true`  | If `false`, the layer stays where it was (see below). |
| `area`                | –       | The area that the layer covers (see below). |

| `position`      | Position |
|-----------------|----------|
| `mouse` | The cursor. The layer is behind the cursor: use a low opacity. |
| `mouse-top-left`, `mouse-top-right`, `mouse-bottom-left`, `mouse-bottom-right` | That corner of the cursor. Near a screen edge, the layer goes to the other side of the cursor. A layer with a text goes inward. |
| `active-screen-center` | The center of the screen with the cursor. |
| `active-screen-top-left`, `active-screen-top-right`, `active-screen-bottom-left`, `active-screen-bottom-right` | That corner of the screen with the cursor. |
| `target` | The [target](#target). |
| `last-selected-hint` | The hotspot of the last selected hint. Before the first selection, the cursor. |
| `active-window-center` | The center of the active window. |
| `last-overlay-area-center` | The center of the area of the overlay of the previous mode. |
| `position-history`, `<name>-position-history` | Each position of the [history](#position-history), in the order of the saves. With no position, the layer does not show. |

While the mouse follows the target, the target is at the cursor. mousemaster draws a layer at
`target` again at each mouse move. To draw at the cursor, use `mouse`.

**`end-position`**: The layer starts at `position` and stops at `end-position`. `size` is its
thickness, and the two points give its length. `rotation` turns it from the line between the two
points. `aspect-ratio` cannot be set.

- The layer runs from each point of `position` to each point of `end-position`.
- From a position to the same position, the layer runs from each point to the next point.
- Without two points, the layer does not show.

```properties
# A line from the last selected hint to the target.
grid-mode.line-indicator.shape=line
grid-mode.line-indicator.position=last-selected-hint
grid-mode.line-indicator.end-position=target
# A line from each saved position to the next one.
ui-hint-mode.save.click-position-history=+f1
ui-hint-mode.line-indicator.shape=line
ui-hint-mode.line-indicator.position=click-position-history
ui-hint-mode.line-indicator.end-position=click-position-history
```

**`position-anchor`** and **`end-position-anchor`**: Some positions have a hint.
`last-selected-hint` has one after the first selection. A saved position has one if you saved it at
the hotspot of the last selected hint.

- `point`: the layer starts or stops at the point. The hint does not apply.
- `hint-box-edge`: at the edge of the hint box. If the overlay does not draw the box, at the edge of
  the cell.
- `hint-cell-edge`: at the edge of the hint cell.
- At a position without a hint, or outside its hint box or cell, the layer starts or stops at the
  point.
- Without `end-position`, a hint anchor puts the layer over the hint box or the hint cell, as an
  `area` does. The `area-*-inset` properties of the layer apply.

```properties
# Lines from the box of each saved hint to the box of the next one.
ui-hint-mode.line-indicator.position-anchor=hint-box-edge
ui-hint-mode.line-indicator.end-position-anchor=hint-box-edge
```

**`follow`**: With `follow=true`, a layer moves with what it is on. This is the cursor, the screen
with the cursor, the target, the last selected hint, the saved positions or the area. A layer with an
`end-position` follows its two positions. With `false`, the layer stays where it was at the last of
these events:

- The layer was enabled.
- Its keyframes started again.
- Its `position`, `end-position`, anchors or `area` changed.

**`area`**: The layer covers an area. It takes the values of the overlay `area`, with
`area-width-percent`, `area-height-percent`, the `area-*-inset` properties and `area-center` (see
[Overlay area and grid](#overlay-area-and-grid)). The area sets the position and the size of the
layer: `position`, `end-position`, `size` and `aspect-ratio` cannot be set. The stroke is centered on
the edge of the area. An inset of minus half the stroke thickness puts the stroke outside the area:

```properties
# A box around the element of the last selected UI hint.
grid-mode.element-indicator.shape=rectangle
grid-mode.element-indicator.area=last-selected-hint-cell
grid-mode.element-indicator.area-top-inset=-0.5
grid-mode.element-indicator.area-bottom-inset=-0.5
grid-mode.element-indicator.area-left-inset=-0.5
grid-mode.element-indicator.area-right-inset=-0.5
grid-mode.element-indicator.fill-opacity=0
```

### Shadow

| Property                   | Default   | Description |
|----------------------------|-----------|-------------|
| `shadow-opacity`           | `0`       | The opacity of the shadow. |
| `shadow-blur-radius`       | `10`      | The blur radius, from `0` to `1000`. |
| `shadow-color`             | `#000000` | The color of the shadow. |
| `shadow-stack-count`       | `1`       | How many times mousemaster draws the shadow on itself, from `1` to `100`. More times make it darker. |
| `shadow-horizontal-offset` | `0`       | The horizontal offset, from `-100` to `100`. |
| `shadow-vertical-offset`   | `0`       | The vertical offset, from `-100` to `100`. |

- A shadow shows only if `shadow-opacity` and `shadow-blur-radius` are more than `0`.
- Each layer casts its own shadow from its whole shape, whatever its fill and stroke opacities.
- Layers with the same shadow cast it as one shape.
- mousemaster never draws a shadow over a layer. This is also true for a transparent layer.

### System cursor

```properties
normal-mode.indicator.render-as-cursor=true
```

**`render-as-cursor`**: If `true`, mousemaster draws some layers into the image of the system
cursor. These layers are at `mouse`, follow it, and have no text and no `end-position`. The cursor
is on top of these layers. They move with the cursor without delay. The other layers stay in
windows. Windows only.
Default: `false`.

- While `hide-cursor` is enabled, the cursor image has the layers without the cursor.
- The indicator is then the cursor: what hides the cursor also hides the indicator. The Windows
  option "Hide pointer while typing" hides the cursor at each key press until the mouse moves. To
  keep the indicator, clear this option in Mouse Properties > Pointer Options > Visibility. See
  [#86](https://github.com/petoncle/mousemaster/issues/86).

### Capture exclusion

**`exclude-from-capture`**: If `true`, the indicator does not show in screenshots and screen
recordings. It stays on the screen. It applies to the windows of the indicator. The layers in the
cursor image follow the cursor option of the capture tool. Default: `false`.

```properties
# Exclude the indicator only while the screenshot flash shows.
screenshot-animation.duration-millis=300
normal-mode.start.screenshot-animation=#printscreen
normal-mode.flash-indicator.enabled=false | _{screenshotanimation} -> true
normal-mode.indicator.exclude-from-capture=false | _{screenshotanimation} -> true
```

## Animations

An animation is a named clock. You define it one time for the whole configuration. Combos start and
stop it:

```properties
click-burst-animation.duration-millis=500
_indicator-mode.start.click-burst-animation=+isleftmousepressing | +isrightmousepressing
_indicator-mode.stop.click-burst-animation=_{clickburstanimation ismousepressing} wait-250
```

| Property                           | Default   | Description |
|------------------------------------|-----------|-------------|
| `<name>-animation.duration-millis` | –         | The length of one cycle. Required. |
| `<name>-animation.repeat`          | `1`       | The number of cycles, from `1` to `10000`, or `loop`. |
| `<name>-animation.direction`       | `forward` | `forward`, or `alternate`: each even-numbered cycle plays backward. |
| `<name>-animation.overlap`         | `false`   | If `true`, a restart lets a copy of the old run play to its end (see below). A loop cannot overlap. |

The commands:

- **`start.<name>-animation`**: Starts the animation. A start of an animation that runs starts it
  again. To start an animation on each switch to the mode, use
  `<mode>.start.<name>-animation=#{*}-0`.
- **`stop.<name>-animation`**: Stops the animation. A `loop` runs until you stop it.

Rules:

- While an animation runs, a key with its name without hyphens is pressed: `clickburstanimation`.
  `_{clickburstanimation}` is true while it runs. `-clickburstanimation` occurs when it ends or
  stops.
- With `overlap=true`, the restart leaves a copy of the layers that the animation changes. The copy
  is below the layers with the same `z`. It stays where it is and plays its keyframes to the end.
  The copy does not press the animation key: combos and `stop` see only the new run.
- An animation continues after a mode switch. Then the keyframes of the new mode apply: a branch
  of the new mode on the animation plays from the time that the animation has reached.

### Keyframes

A branch on an animation can give a property keyframes in place of a value:

```properties
click-rest-animation.duration-millis=80
_indicator-mode.start.click-rest-animation=-clickburstanimation
_indicator-mode.indicator.size=26 | _{clickrestanimation} -> 0% current; 100% 26 0.7 | _{clickburstanimation} -> 0% 13; 100% 78 exponential-out-10
_indicator-mode.indicator.stroke-opacity=1 | _{clickburstanimation} -> 0% 1; 250ms 1; 500ms 0 0.7
```

- A keyframe is a time, a value and an optional [easing](#easings). The time is a percent of one
  cycle, or milliseconds in the cycle (`250ms`).
- The easing shapes the change that ends at the keyframe. Default: `1` (linear).
- mousemaster draws the frame at 100% before the animation ends.
- `current` is the value of the property when the branch started.
- A number follows the easing to the value of the next keyframe.
- A color follows the easing to the color of the next keyframe. A [gradient](#gradients) follows it
  to a gradient with the same number of colors: each color goes to the color at the same place. A
  keyframe value is one word, so a gradient needs a color alias.
- Other values change at their keyframe: a shape, a text, `follow`, or a color and a gradient with
  different numbers of colors.
- Keyframes need a branch with exactly one animation. Only indicator properties and `hide-cursor`
  properties take keyframes. The overlay and the zoom change with [transitions](#transitions).

## Cursor properties

```properties
hint1-mode.hide-cursor.enabled=true
hint1-mode.hide-cursor.idle-duration-millis=0
```

- **`hide-cursor.enabled`**: If `true`, mousemaster hides the cursor in the mode. A mode that sets
  another `hide-cursor` property, and not `hide-cursor.enabled`, enables it. Default: `false`.
- **`hide-cursor.idle-duration-millis`**: The cursor hides after this time without a move, a wheel
  scroll or a button press from mousemaster. While the overlay shows, the time starts again at each
  frame. `0` hides the cursor immediately, also while the overlay shows. Default: `0`.
- While the indicator is the cursor (`indicator.render-as-cursor=true`), the cursor image shows only
  the layers, immediately. `idle-duration-millis` does not apply.

## Zoom properties

```properties
hint2-mode.zoom.percent=5
hint2-mode.zoom.center=last-selected-hint
```

- **`zoom.percent`**: The magnification, from `1` (no zoom) to `100`. With `5`, the content shows 5
  times larger. Default: `1`.
- **`zoom.center`**: The point at the center of the zoom. It takes the values of
  [`area-center`](#overlay-area-and-grid). With `mouse` or `target`, the zoom follows that point.
  Default: `active-screen-center`.
- **`zoom.area-size-source`**: What sets the zoom. Default: `percent`.
  - `percent`: `zoom.percent`.
  - `last-selected-hint-cell`: the largest zoom that keeps the cell of the last selected hint on the
    screen. Before the first hint selection, `zoom.percent` applies.
- **`zoom.area-width-percent`** / **`zoom.area-height-percent`**: With `last-selected-hint-cell`,
  scale the cell before the zoom fits it. More than `0`, up to `100`. Default: `1`.
- The zoom is always from `1` to `100`.
- **`zoom.transition-*`**: See [Transitions](#transitions).
- On macOS, the zoom needs the Screen Recording permission.

## Transitions

A transition moves what the overlay or the zoom shows to its new state. You set its duration and its
easing. Each transition has three properties: `-enabled`, `-duration-millis` and `-easing` (see
[Easings](#easings)).

| Properties             | What moves | Defaults: `-enabled`, `-duration-millis`, `-easing` |
|------------------------|------------|-----------------------------------------------------|
| `overlay.transition-*` | The cells of a grid when its hints change, and the box of the selected hint at the end of a selection. | `true`, `100`, `smootherstep` |
| `overlay.fade-*`       | The overlay when it shows or hides. | `true`, `100`, `smootherstep` |
| `zoom.transition-*`    | The zoom when it changes. | `false`, `200`, `smootherstep` |

```properties
zoom-mode.zoom.transition-enabled=true
zoom-mode.zoom.transition-duration-millis=300
zoom-mode.zoom.transition-easing=smootherstep
```

- A transition uses the settings of the state it goes to. When the overlay hides, or the zoom returns
  to `1`, the transition uses the settings of the state it leaves.
- `overlay.transition-*` moves only the hints of a grid. UI hints and position history hints change
  immediately.
- A new transition does not stop a transition that runs. The old transition does its part of the
  change, and the new transition adds the rest. What shows keeps its position. With an easing that
  starts at rest, such as `smootherstep`, it also keeps its speed.
- A change without a transition (`-enabled=false` or `-duration-millis=0`) stops the transitions
  that run.

## Easings

An easing is the shape of a change from 0 to 1. `u` is the part of the duration that is done, from 0
to 1:

| Easing                | Value at `u`                       | Shape                                  |
|-----------------------|------------------------------------|----------------------------------------|
| A number `p`          | `uᵖ`                               | `1` is linear. More than `1` starts slowly. |
| `smoothstep`          | `3u² − 2u³`                        | Slow at the start and at the end.      |
| `smootherstep`        | `6u⁵ − 15u⁴ + 10u³`                | Slower at the start and at the end.    |
| `logarithmic`         | `log₂(1 + u)`                      | Fast at the start.                     |
| `exponential`         | `(eᵘ − 1) / (e − 1)`               | Slow at the start.                     |
| `exponential-out-<n>` | `(1 − 2^(−n·u)) / (1 − 2^(−n))`    | Very fast at the start. A larger `n` is faster. |
| `step`                | `0` until `u = 1`, then `1`        | No change until the end.               |

## Overlay properties

The overlay is what a mode shows over the screen to pick a place: a grid, boxes on UI elements or
saved positions. Each grid cell, UI element or saved position gets a hint. `selection-keys` labels
the hints with the keys to type. The cell of a UI hint is its element.

An overlay property has the format `<mode>.overlay.<property>=<value>`. This section writes the
overlay properties without `overlay.`. The `hotspots.*` properties have the format
`<mode>.hotspots.<property>=<value>`.

| Property               | Default | Description |
|------------------------|---------|-------------|
| `visible`              | `true`  | If `false`, the overlay does not show. The indicator and the zoom stay. |
| `exclude-from-capture` | `false` | If `true`, the overlay does not show in screenshots and screen recordings. It stays on the screen. |
| `z`                    | `0`     | The place of the overlay in the order of the indicator layers (see [Shape](#shape)). |

### Overlay types

```properties
ui-hint-mode.overlay.type=ui-vision
```

**`type`**: The hints that the overlay shows. Default: `grid`.

| Value | Hints |
|-------|-------|
| `grid` | A grid of cells over the area. |
| `position-history`, `<name>-position-history` | The saved positions of the [history](#position-history). |
| `ui-accessibility` | The UI elements that the accessibility API finds (see [Accessibility UI hints](#accessibility-ui-hints)). |
| `ui-vision` | The elements that a screen capture shows (see [Vision UI hints](#vision-ui-hints)). |

A mode can have settings for each type. A branch on `type` shows the hints of that type, with the
settings of the mode for that type:

```properties
virtual-key.isgrid=released
ui-hint-mode.overlay.type=ui-vision | _{isgrid} -> grid
ui-hint-mode.overlay.vision-density=6
ui-hint-mode.overlay.grid-max-row-count=20
```

### Overlay area and grid

```properties
hint-mode.overlay.area=active-screen
hint-mode.overlay.area-center=active-screen-center
hint-mode.overlay.grid-cell-width=74
hint-mode.overlay.grid-cell-height=36
hint-mode.overlay.group-row-count=6
hint-mode.overlay.group-column-count=5
```

`area` sets the size of the area. `area-center` sets the point at its center.

**`area`**: Default: `active-screen`.

| Value | Area |
|-------|------|
| `active-screen` | The screen with the cursor. |
| `active-window` | The active window. |
| `all-screens` | Each screen, with one grid on each screen. `area-center` does not apply. |
| `last-selected-hint-cell` | The cell of the last selected hint, for [multi-level hints](#multi-level-hints). |
| `last-overlay-area` | The area of the overlay of the previous mode, when this mode started. It is the area that the [overlay commands](#overlay-commands) left, or the cells that the typed selection keys keep. |

```properties
# Shrink a grid, then show a grid with labels in what stays of it.
screen-grid-mode.to.hint-mode=+h
hint-mode.overlay.area=last-overlay-area
hint-mode.overlay.selection-keys=selectionkey
```

**`area-width-percent`** / **`area-height-percent`**: Scale the area around `area-center`, more than
`0`, up to `100`. `0.5` gives half the width. A value more than `1` makes the area larger. Default:
`1`.

**`area-top-inset`** / **`area-bottom-inset`** / **`area-left-inset`** / **`area-right-inset`**:
Margins cut from the scaled area, in pixels at a 100% scale. A negative inset makes the area larger.
Default: `0`.

**`area-center`**: The point at the center of the area. The default depends on `area`.

| Value | Point | Default for |
|-------|-------|-------------|
| `active-screen-center` | The center of the screen with the cursor. | `active-screen` |
| `mouse` | The cursor. | |
| `target` | The [target](#target). | |
| `last-selected-hint` | The hotspot of the last selected hint. Before the first selection, the center of the screen with the cursor. | `last-selected-hint-cell` |
| `active-window-center` | The center of the active window. | `active-window` |
| `last-overlay-area-center` | The center of `last-overlay-area`. | `last-overlay-area` |

- You can use any `area-center` with any `area` except `all-screens`. `area=active-window` with
  `area-center=mouse` is a grid with the size of the window, at the cursor.
- With `mouse` or `target`, a grid follows that point.

**`grid-cell-width`** / **`grid-cell-height`**: The size of a cell, in pixels at a 100% scale.
Default: `73` / `36`.

- The zoom also scales the cells. In an `active-window`, `last-overlay-area` or
  `last-selected-hint-cell` area, a cell covers the same content at each zoom. With
  `zoom.percent=5`, a cell with `grid-cell-width=10` is 50 pixels wide on the screen. The grid keeps
  its shape and its labels.
- An `active-screen` or `all-screens` area does not change with the zoom. With `zoom.percent=5`, the
  cells are 5 times larger and there are 5 times fewer cells on each line.

**`grid-max-row-count`** / **`grid-max-column-count`**: The maximum number of rows and columns, from
`1` to `200`. Default: `200`.

**`grid-cell-sizing`**: How the grid gets its number of cells and their size. Default: `fixed`.

- `fixed`: the number of cells is the area divided by the cell size, at least 1, up to the maximum
  counts. The cells then stretch to fill the area. Exception: if a maximum count limits the grid, and
  the space that stays is at least one cell, the cells keep their size. The grid is then at the
  center of the area.
- `fit`: the grid has `grid-max-row-count` × `grid-max-column-count` cells, and the cells fill the
  area. `grid-cell-width` and `grid-cell-height` do not apply. Use it for a grid with a fixed shape,
  such as 3×3, on any screen.

**`group-row-count`** / **`group-column-count`**: A group is a block of `group-row-count` ×
`group-column-count` cells. The grid is made of groups. Default: `1000000` / `1`, one group for
each column.

- The [prefix](#hint-prefix) of a label names the group of the hint. All the hints of a group have
  the same prefix. The rest of the label names the cell of the hint in its group.
- The prefix has one key if there are no more groups than selection keys. The rest of the label has
  one key if a group has no more cells than selection keys.
- If the grid has 100 hints or more, the prefix or the rest of the label can have two keys. Both can
  have two keys only with 6 selection keys or fewer.
- If this does not fit, the labels are the shortest labels, without a prefix.
- A grid with no more cells than selection keys gives each hint one key, with no prefix.
- The groups do not change the number of rows and columns of the grid. `grid-cell-sizing` sets
  them.
- `group-row-count=1` with a column count larger than the grid gives one prefix for each row.

**`grid-order`**: The order of the groups, and of the cells in each group. The first ones get the
first keys. Default: `top-to-bottom`.

- `top-to-bottom`: in rows from the top. Each row goes from left to right.
- `left-to-right`: in columns from the left. Each column goes from top to bottom.

### Screen-specific values

Some overlay properties can have a different value on each screen. Use the screen as a precondition
of a [mutation](#mode-property-mutation):

```properties
# Larger cells on a 4K screen at 100%.
hint1-mode.overlay.grid-cell-width=74 | _{3840x2160-100%} -> 96
hint1-mode.overlay.grid-cell-height=41 | _{3840x2160-100%} -> 54
```

- These overlay properties can have a value for each screen: `selection-keys`, `grid-*`, `group-*`
  and the [Hint appearance](#hint-appearance) properties, such as `box-*`, `font-*` and
  `ui-element-box-*`.
- A screen filter is a resolution (`3840x2160`), a scale (`150%`), or both (`3840x2160-150%`). A
  filter without a scale matches the resolution at each scale. Give the scale on each filter to keep
  each filter exact.
- The most specific filter that matches a screen gives the values: a resolution and a scale first,
  then a resolution, then a scale. A screen that no filter matches takes the default values.
- A property that a filter does not set takes the default value, not the value of a less specific
  filter.
- The appearance properties take the value of the screen where mousemaster draws them.
- With `all-screens`, the grids take their `grid-cell-sizing` values from the active screen, and
  their groups, `grid-order` and selection keys from the screen on the left.
- One grid on more than one screen takes its `selection-keys`, `grid-*` and `group-*` values from
  one screen. This is the active screen, or with `active-window`, the screen nearest to the center
  of the window.
- A length (a padding, a border thickness, a radius, a border length, a shadow blur or offset) is in
  pixels at a 100% scale. The scale of the screen makes it larger, as it does a font size. A hint
  keeps its proportions on a 200% screen. To get an exact pixel count at one scale, divide:
  `box-border-thickness=1 | _{150%} -> 0.67` draws one pixel at 150%.

```properties
# Two screens. A space means "and", so two filters must be alternatives.
hint1-mode.overlay.box-border-radius=1 | _{3840x2160-300% | 1366x768-100%} -> 3
# Two screens with a key: one filter in each branch.
hint1-mode.overlay.box-border-radius=1 | _{leftshift 3840x2160-300%} -> 3 \
    | _{leftshift 1366x768-100%} -> 3
```

On each other property, a screen filter is a
[built-in virtual key](combo-reference.md#built-in-virtual-keys). mousemaster presses it while the
active screen matches it:

```properties
normal-mode.mouse.smooth-jump-velocity=30000 | _{300%} -> 20000
normal-mode.indicator.size=12 | ^{300%} -> 8 | _{300% leftshift} -> 24
normal-mode.press.left=_{3840x2160-300%} +f
```

`screen-alias.<name>` gives a name to a set of screen filters. The alias matches a screen that one of
its filters matches. A screen alias cannot have the name of a key alias. An alias can name another
alias:

```properties
screen-alias.largescreen=3840x2160-100% 3440x1440-100%
screen-alias.mediumscreen=3840x2160-150% 2560x1440-100%
screen-alias.smallscreen=1920x1080-125% 1366x768-100%
screen-alias.tunedscreen=largescreen mediumscreen smallscreen

hint1-mode.overlay.grid-cell-sizing=fit
hint1-mode.overlay.grid-max-column-count=25 | _{mediumscreen | largescreen} -> 40
hint1-mode.overlay.grid-max-row-count=30 | _{smallscreen} -> 20 | _{mediumscreen | largescreen} -> 40
```

With aliases, a property names a set of screens. To change the values of a screen, move it to
another set.

### UI hints

UI hints are the `ui-accessibility` and `ui-vision` types. They share these properties.

`area`, `area-width-percent`, `area-height-percent` and `area-center` set where mousemaster finds
the elements (see [Overlay area and grid](#overlay-area-and-grid)). With `all-screens`,
mousemaster scales each screen around its own center, and the scan covers the rectangle around the
scaled screens.

```properties
# Hints only near the cursor.
ui-hint-mode.overlay.area-width-percent=0.33
ui-hint-mode.overlay.area-height-percent=0.33
ui-hint-mode.overlay.area-center=mouse
```

**`ui-order`**: The order of the elements. The first elements get the shortest labels. Default:
`top-to-bottom`.

- An edge: `top-to-bottom`, `bottom-to-top`, `left-to-right` or `right-to-left`.
  - With `top-to-bottom` and `bottom-to-top`, the elements are in rows from that edge. Each row goes
    from left to right.
  - With `left-to-right` and `right-to-left`, the elements are in columns from that edge. Each column
    goes from top to bottom.
  - A row or a column continues while each next center is at most 6 pixels further from the edge
    than the center before it. The 6 pixels are at a 100% scale. For example, with
    `left-to-right`, the icons of a taskbar on the left edge are one column.
- A value of [`area-center`](#overlay-area-and-grid): the elements nearest to that point come
  first. The point is where it was when the scan started.
- `random`: an order that looks random but does not change. The same elements always get the same
  labels.

**`label-length`**: The length of the labels. It also applies to position history hints. Default:
`shortest`.

- `shortest`: the first elements get labels one key shorter than the others. The labels need the
  fewest key presses.
- `equal`: all the labels have the same length: the shortest length that gives each element a label.
  The first elements get the labels with the first keys. With the keys `a b c d`, the order is
  `aa ab ba bb ac ca bc cb cc ad`. mousemaster sorts the labels by:
  1. The last selection key that the label uses.
  2. The sum of the positions of its keys in `selection-keys`.
  3. The order of `selection-keys`.

If the scan finds no element, the mode shows no hint, and the built-in virtual key `isoverlayempty`
is pressed. A combo can then leave the mode:

```properties
ui-hint-mode.to.normal-mode=_{isoverlayempty}
```

**`ui-element-box-*`**: The box of each element, behind its hint box. These properties are the same
as the `box-*` properties (see [Hint appearance](#hint-appearance)):

| Property                            | Default   |
|-------------------------------------|-----------|
| `ui-element-box-color`              | `#000000` |
| `ui-element-box-opacity`            | `0`       |
| `ui-element-box-border-thickness`   | `1`       |
| `ui-element-box-border-length`      | `10000`   |
| `ui-element-box-border-color`       | `#FFFFFF` |
| `ui-element-box-border-opacity`     | `0`       |
| `ui-element-box-border-radius`      | `0`       |
| `ui-element-box-horizontal-padding` | `0`       |
| `ui-element-box-vertical-padding`   | `0`       |
| `ui-element-box-shadow-*`           | The `box-shadow-*` defaults |

- The box is the element. Its border is inside it, as in a hint box.
- The paddings make the box larger than the element. With paddings equal to the border thickness, the
  border goes around the element and does not cover it.
- The shadow is only outside the box, so it does not cover the element.

```properties
ui-hint-mode.overlay.ui-element-box-border-color=#2EA043
ui-hint-mode.overlay.ui-element-box-border-opacity=1
ui-hint-mode.overlay.ui-element-box-border-thickness=2
ui-hint-mode.overlay.ui-element-box-horizontal-padding=2
ui-hint-mode.overlay.ui-element-box-vertical-padding=2
```

### Accessibility UI hints

Accessibility UI hints are on the UI elements: buttons, links, text fields, checkboxes. On Windows,
mousemaster finds the elements with UI Automation. On macOS, it finds them with the Accessibility
API, and needs the Accessibility permission.

```properties
ui-hint-mode.overlay.type=ui-accessibility
ui-hint-mode.overlay.area=active-screen
ui-hint-mode.overlay.selection-keys=selectionkey
```

**`area`**: Where mousemaster finds the elements:

| Value | Area |
|-------|------|
| `active-screen` | Each visible window on the screen with the cursor. |
| `all-screens` | Each visible window on each screen. |
| `active-window` | The active window. On Windows, the area also has its popups (menus, lists, dialogs), with their parts outside the window. On macOS, only the active window. |
| `last-selected-hint-cell` | The cell of the last selected hint. |
| `last-overlay-area` | The area of the overlay of the previous mode. |

- If you scale, move or inset an `active-window` area, mousemaster scans it as an `active-screen`
  area: each visible window in the rectangle.
- A scan of more than one window takes more time. The hints show when the scan ends. mousemaster
  continues to respond during the scan.
- On Windows, an element gets a hint if its center is in its window and in the area, and no other
  window covers its center. The element keeps its full size. mousemaster ignores minimized windows
  and windows that let clicks through.
- On macOS, mousemaster cuts each element to the area. An element that is fully under a window in
  front gets no hint. Menus, the Dock and the menu bar do not count as windows.
- mousemaster also ignores the windows of other virtual desktops and of suspended apps.

### Vision UI hints

mousemaster finds vision UI hints in a screen capture, not with the accessibility API. It finds the
ink: the pixels that are different from their background. Ink that is close together gets one hint.
Vision UI hints also work on games, canvases and remote desktops. They do not know what an element
is.

```properties
ui-hint-mode.overlay.type=ui-vision
ui-hint-mode.overlay.area=active-screen
ui-hint-mode.overlay.vision-density=6
```

- The capture is the rectangle of the area. The accessibility rules for popups, covered windows and
  cuts do not apply: with `active-window`, the capture includes what is on top of the window.
- On macOS, the capture needs the Screen Recording permission.

**`vision-density`**: How much the detection finds, from `1` to `8`. A higher value gives more hints,
on smaller things. Default: `6`.

- `1` ignores plain text. It finds the ink that has no gap: an icon, a filled button, a checkbox, a
  picture. It also finds what a border surrounds on all sides: a field, a button with a border.
- `2` to `7` find bounded regions: a button, a field, a panel. Each step accepts a smaller margin
  between the border of a region and its content.
- `8` adds the ink that no region holds, cut at its gaps.
- At each density, a gap between ink of two colors cuts a region. From density `2`, a link in a
  sentence, or gray text next to black text, gets its own hint.

A hint shows that something is drawn there, not that you can click it. Decrease the density when the
labels hide what you read. Increase it when something that you can click has no hint.

**`vision-min-size`**: The minimum size of an element, from `0` to `1000`, in pixels at a 100%
scale. An element narrower or shorter than this size gets no hint. mousemaster measures the element
before it trims the element to its ink. Use it to ignore thin lines, such as separators. Default:
`0`.

```properties
ui-hint-mode.overlay.vision-min-size=6
```

### Hint selection

```properties
key-alias.selectionkey=a b c d e f g h i j k l m n o p q r s t u v w x y z
hint-mode.overlay.selection-keys=selectionkey
hint-mode.select=+selectionkey
hint-mode.deselect=backspace
hint-mode.to.normal-mode=+selectionkey
```

| Property | Default | Description |
|----------|---------|-------------|
| `selection-keys` | – | The keys that select the hints. Without selection keys, the hints have no labels, and `select`, `deselect` and `deselect-all` are errors. `none` gives no selection keys. |
| `target-follows-overlay-center` | `false` | If `true`, the [target](#target) goes to the center of the overlay when the overlay shows, and when its hints or its area change. |
| `eat-unused-selection-keys` | `true` | If `false`, a selection key that no hint uses can complete other combos. |
| `label-override` | – | A fixed label in each hint, in place of the keys of the hint. Give a key name: `plus` shows `+`. You still type the real keys. |

The commands:

- **`select`**: The combo that selects a hint. To select while `leftshift` is pressed, use
  `hint-mode.select=_{none | leftshift} +selectionkey`.
- **`deselect`**: Removes the last typed selection key.
- **`deselect-all`**: Removes all the typed selection keys. A selected hint that leads to a mode
  with its own hints keeps the keys before its last key when its hints show again. You can then
  select a neighbor hint with one key. With `deselect-all` in the mode that shows the hints again,
  no key stays typed: `pick-mode.deselect-all=wait-0`.
- **`to.<mode>`**: Switches to the mode after a selection: `hint-mode.to.normal-mode=+selectionkey`.
- **`move-to.<point>`** / **`move-target-to.<point>`**: Move the mouse or the target to a point:
  - `target` (`move-to` only) and `mouse` (`move-target-to` only).
  - `last-selected-hint`: the [hotspot](#hotspots) of the last selected hint.
  - `overlay-center`: the center of the hints that the typed selection keys keep. It is the point of
    `target-follows-overlay-center`.
  - `cell-center`: the center of the cell that the mouse or the target is in, or of the nearest cell.
    If it is in more than one cell, such as nested UI elements, the smallest cell applies.
  - `cell-top`, `cell-bottom`, `cell-left` and `cell-right`: the nearest point of a cell edge on that
    side of the mouse or the target. The edge of the cell that it is in comes first, the smallest
    cell as for `cell-center`. A right or bottom edge is the last pixel of its cell, so the point
    stays in the cell.

`selection-keys=none` with a mutation shows the labels only while a key is pressed. `select` then
works with the keys of each branch:

```properties
virtual-key.labels=released
grid-mode.overlay.selection-keys=none | _{labels} -> selectionkey
```

`target-follows-overlay-center` does nothing for a grid that follows the target: a grid with
`area-center=target`, or with `area-center=mouse` while the mouse follows the target.

### Hotspots

The hotspot of a hint is the point where a selection of the hint puts the target. (The hotspot of a
cursor is the point that clicks.) The box of a hint stays at the center of its cell.

```properties
hint-mode.hotspots.cell-x-percent=0.5 | _{ishotspotscentered} +leftarrow -> 0 \
    | +1 -> 0 | +2 -> 0.125 | +3 -> 0.25
hint-mode.nudge-hotspots.left=^{ishotspotscentered} +leftarrow
hint-mode.hotspots.nudge-distance=4 | _{leftctrl} -> 2
```

Here, the first press of `leftarrow` moves the hotspots to the left edges of their cells. The next
presses nudge them. A press of `3` moves them a quarter across.

| Property | Default | Description |
|----------|---------|-------------|
| `hotspots.cell-x-percent` | `0.5` | The place of the hotspot across its cell, from `0` to `1`. `1` is the last pixel of the cell. |
| `hotspots.cell-y-percent` | `0.5` | The place of the hotspot down its cell, from `0` to `1`. |

The speed and nudge properties of `hotspots` are in [Movement properties](#movement-properties).

- **`nudge-hotspots.<direction>`**: Moves the hotspots by `hotspots.nudge-distance`.
- **`start-move-hotspots.<direction>`** / **`stop-move-hotspots.<direction>`**: Start and stop a
  move of the hotspots, with the `hotspots` speed.
- A hotspot can go out of its cell, but not out of the screen of its hint. When each hotspot is at
  the edge of its screen, a nudge or a move in that direction does nothing.
- A new `cell-x-percent` cancels the horizontal nudges and moves. A new `cell-y-percent` cancels the
  vertical ones.
- A mode without hints cancels the nudges and moves, and stops a move.
- Two modes have the same overlay settings if they have the same `type`, `area`, `area-*`, `grid-*`,
  `group-*`, `ui-order`, `label-length` and `vision-*` values. A mode with the same overlay
  settings keeps the hotspots where the commands put them: `pick-mode.overlay=hint-mode.overlay`
  with `pick-mode.hotspots=hint-mode.hotspots`.
- A hint without a cell, such as a saved position, has its hotspot at its center.
- A [saved hint](#saved-hints) has its hotspot at the position where you saved it. It does not move
  with the other hotspots.
- `ishotspotscentered` is pressed while the hotspots are at the centers. `ishotspotsmoving` is
  pressed while they move. See [built-in virtual keys](combo-reference.md#built-in-virtual-keys).
- When the hotspots are not at the centers, the overlay draws the hotspot of each hint behind the
  boxes, with the `hotspot-box-*` and `hotspot-line-*` properties. Their opacities are `0` by
  default: set them to show the hotspots. While the hotspots are at the centers, the overlay draws
  only the hotspot of a saved hint that is not at its center.

### Hint appearance

Each color property takes a hex color, a [gradient](#gradients) or a color alias. An opacity is from
`0` to `1`.

```properties
hint-mode.overlay.box-opacity=0.4
hint-mode.overlay.font-size=18
```

#### Box

| Property | Default | Description |
|----------|---------|-------------|
| `box-color` | `#000000` | The fill of a hint box. |
| `box-opacity` | `0.3` | The opacity of the fill. |
| `box-border-thickness` | `1` | The thickness of the border. |
| `box-border-length` | `10000` | The length of the border from each corner of a box. A low value draws short marks at the corners. |
| `box-border-color` | `#FFFFFF` | The color of the border. |
| `box-border-opacity` | `0.4` | The opacity of the border. |
| `box-border-radius` | `0` | The radius of the corners, from `0` to `1000`. |
| `box-framed` | `true` | If `false`, only the lines between the boxes show, with no frame around the grid. |
| `box-width-percent` / `box-height-percent` | `1` | The size of a box in its grid cell. Less than `1`, a box is at least the size of its label and padding: `0` fits each box to its label. Grid only. |
| `box-horizontal-padding` / `box-vertical-padding` | `0` | For a hint that is not in a grid, the space between the label and the edge of the box. In a grid, each box is larger than its cell by the padding on each side. |

#### Box shadow

| Property | Default | Description |
|----------|---------|-------------|
| `box-shadow-opacity` | `0` | The opacity of the shadow. `0` shows no shadow. |
| `box-shadow-blur-radius` | `10` | The blur radius, from `0` to `1000`. |
| `box-shadow-color` | `#000000` | The color of the shadow. Where two shadows meet, their colors mix. |
| `box-shadow-stack-count` | `1` | How many times mousemaster draws the shadow on itself, from `1` to `100`. |
| `box-shadow-horizontal-offset` / `box-shadow-vertical-offset` | `0` | The offset, from `-100` to `100`. |

#### Font

| Property | Default | Description |
|----------|---------|-------------|
| `font-name` | `Consolas` on Windows, `Menlo` on macOS | The font of the labels. |
| `font-weight` | `normal` | `thin`, `extra-light`, `light`, `normal`, `medium`, `demi-bold`, `bold`, `extra-bold` or `black`. |
| `font-size` | `18` | The size, in points. |
| `font-color` | `#FFFFFF` | The color of the letters. |
| `font-opacity` | `1` | The opacity of the letters. |
| `font-spacing-percent` | `0.7` | The space between the letters: `0` is no space, `1` spreads the letters evenly across the box, `0.5` is a small space with alignment. For hints that are not in a grid, a value more than `0.5` acts as `0.5`. |
| `font-vertical-alignment` | `cap-height` | `baseline`, `middle` or `cap-height`. |
| `font-outline-thickness` | `0` | The thickness of the outline of the letters. |
| `font-outline-color` | `#000000` | The color of the outline. |
| `font-outline-opacity` | `0.5` | The opacity of the outline. |
| `font-shadow-opacity` | `1` | The opacity of the shadow of the letters. |
| `font-shadow-blur-radius` | `10` | The blur radius of the shadow, from `0` to `1000`. |
| `font-shadow-color` | `#FFFFFF` | The color of the shadow. |
| `font-shadow-stack-count` | `1` | How many times mousemaster draws the shadow on itself, from `1` to `100`. |
| `font-shadow-horizontal-offset` / `font-shadow-vertical-offset` | `0` | The offset of the shadow, from `-100` to `100`. |

For the selected, focused, saved and prefix keys, see [Hint font states](#hint-font-states).

#### Background

The background is one box behind the hints. It covers the overlay area, not only the hints.

| Property | Default | Description |
|----------|---------|-------------|
| `background-color` | `#000000` | The color of the background. |
| `background-opacity` | `0` | The opacity. `0` shows no background. |
| `background-border-thickness` | `1` | The thickness of the border. The border is inside the edge of the background. It also shows without a background. |
| `background-border-color` | `#FFFFFF` | The color of the border. |
| `background-border-opacity` | `0` | The opacity of the border. `0` shows no border. |
| `background-border-radius` | `8` | The radius of the corners, from `0` to `1000`. |

#### Group box

The group box is a box around the hints of each [group](#overlay-area-and-grid). Grid only.

| Property | Default | Description |
|----------|---------|-------------|
| `group-box-enabled` | `false` | If `true`, the group boxes show. A mode that sets another `group-box-*` property, and not `group-box-enabled`, enables the group boxes. |
| `group-box-border-thickness` | `2` | The thickness of the border. |
| `group-box-border-length` | `10000` | The length of the border from each corner. |
| `group-box-border-color` | `#FFD93D` | The color of the border. |
| `group-box-border-opacity` | `0.8` | The opacity of the border. |
| `group-box-framed` | `true` | If `false`, only the lines between the group boxes show, with no frame around the grid. |

A hint box draws no border where the group box draws one.

#### Hotspot box and line

The hotspot box shows the [hotspot](#hotspots) of a hint that is not at the center of its cell. The
hotspot line goes from the edge of the hint box to a hotspot that is out of the hint box.

| Property | Default |
|----------|---------|
| `hotspot-box-width` / `hotspot-box-height` | `8` |
| `hotspot-box-color` | `#000000` |
| `hotspot-box-opacity` | `0` |
| `hotspot-box-border-thickness` | `1` |
| `hotspot-box-border-color` | `#FFFFFF` |
| `hotspot-box-border-opacity` | `0` |
| `hotspot-box-border-radius` | `0` |
| `hotspot-box-shadow-*` | The `box-shadow-*` defaults |
| `hotspot-line-thickness` | `1` |
| `hotspot-line-color` | `#FFFFFF` |
| `hotspot-line-opacity` | `0` |
| `hotspot-line-shadow-*` | The `box-shadow-*` defaults |

A radius of half the size makes the hotspot box a dot.

- Each hotspot box casts its own shadow, only outside the box. The hotspot lines cast one shadow
  together: where the shadows of two lines meet, their colors mix. A line is drawn over its shadow.
- The shadow of a hotspot box has one color: the color of the gradient at the center of the box.

The `saved-` box properties are in [Saved hints](#saved-hints). The transition and fade properties
are in [Transitions](#transitions).

### Gradients

A color can be a gradient: two or more colors with equal spaces between them. The colors blend in
the OkLab color space. An opacity applies to the whole gradient.

```properties
hint-mode.overlay.box-color=#FF0000 #FFA500 #FFFF00 #00FF00 #800080
hint-mode.overlay.background-color=across-screen left-to-right #FF6B00 #FF006E
hint-mode.overlay.box-border-color=across-screen per-pixel center-to-edge #F97316 #3B82F6
```

Keywords can be anywhere among the colors, in any order:

- **Direction**: `top-to-bottom` (default), `bottom-to-top`, `left-to-right`, `right-to-left`, the
  four diagonals (`top-left-to-bottom-right` and the others), `center-to-corner`, `corner-to-center`,
  `center-to-edge` and `edge-to-center`. `center-to-corner` and `center-to-edge` go from the first
  color at the center to the last color at the corners or at the edges. `corner-to-center` and
  `edge-to-center` go the other way.
- **Span**: the area of one sweep of the gradient. From the smallest:
  - `across-box`: each box. The colors always blend inside the box.
  - `across-group`: each [group](#overlay-area-and-grid). The gradient repeats.
  - `across-all-hints`: the hints on the screen.
  - `across-area` (default): the area of `area`, also where there is no hint.
  - `across-screen` and `across-all-screens`.
- **Step**:
  - `per-hint` (default): each hint has one color.
  - `per-group`: each group has one color. It needs a span larger than a group: not
    `across-group` or `across-box`.
  - `per-pixel`: the colors blend inside each box, and continue into the next box.
- **`in-turn`**, in place of a direction: the colors do not blend. Each hint (with `per-hint`) or
  each group (with `per-group`) takes the next color. The order is the order of the hints: a
  grid row by row from the top, UI hints in their `ui-order`. After the last color, the first color
  comes again. You cannot use `in-turn` with an `across-` keyword, with `per-pixel` or in an
  indicator color.

```properties
hint-mode.overlay.box-color=per-group in-turn #E06C75 #98C379 #61AFEF
```

- With `per-hint`, each hint takes the color at its center. Its boxes and its hotspot line show that
  color. With `top-to-bottom`, a grid row has one color.
- A grid without groups, such as a grid without `selection-keys`, is one group. With `per-group`,
  all its boxes have one color.
- Hints without a grid (UI hints and position history hints) are in groups too. The hints with
  labels that differ only in the last key are one group. With the labels `a b c da db dc`, `a b c`
  is a group and `da db dc` is another. With `per-group in-turn` and labels of 2 keys, each first
  key then gets its own color.
- mousemaster draws a decoration cell and the background as boxes. A shadow takes its color at each
  box, as a box does.
- A label takes one font, outline and shadow color: the color at its hint (`per-hint`), of its
  group (`per-group`), or its turn color (`in-turn`). The letters never blend, and `per-pixel`
  does not change this. A decoration label takes the color at its cell.
- Where the shadows of labels of different colors meet, their colors mix.

`color-alias.<name>` gives a name to a color or a gradient. Each color property can use the name as
its value:

```properties
color-alias.accent=left-to-right #FF6B00 #FF006E
hint-mode.overlay.box-border-color=accent
```

### Decorations

Decorations are grids that mousemaster draws over the hints. They do not select. There are three
levels, with the same properties. Each property name starts with the level:

- `decoration-`: one grid over the whole hint grid, as one big cell.
- `subdecoration-`: a grid in each hint cell, such as a preview of the next level.
- `subsubdecoration-`: a grid in each subdecoration cell.

Grid only. You can use the three levels at the same time.

```properties
hint-mode.overlay.subdecoration-max-row-count=3
hint-mode.overlay.subdecoration-max-column-count=3
hint-mode.overlay.subdecoration-box-border-opacity=0.5
```

| Property | Default | Description |
|----------|---------|-------------|
| `<level>-max-row-count` / `<level>-max-column-count` | `1` | The number of rows and columns. |
| `<level>-label-keys` | `none` | The keys of the labels in the cells, or a key alias. Give at least 2 keys. The labels follow the rules of grid labels. |
| `<level>-label-override` | – | A fixed label in each cell. Give a key name: `plus` shows `+`. It replaces `label-keys`. |
| `<level>-box-color` / `<level>-box-opacity` | `#000000` / `0` | The fill of the cells. |
| `<level>-box-border-thickness` | `1` | The thickness of the lines. |
| `<level>-box-border-length` | `10000` | The length of the lines from each corner. A low value draws short marks, such as a `+` at the center of a 2×2 decoration. |
| `<level>-box-border-color` / `<level>-box-border-opacity` | `#FFFFFF` / `1` | The color and the opacity of the lines. |
| `<level>-box-border-radius` | `0` | The radius of the corners. |
| `<level>-box-framed` | `false` | If `true`, the level draws its own frame. A decoration has the same size as its parent box, so its frame is on the border of the parent. |
| `<level>-box-horizontal-padding` / `<level>-box-vertical-padding` | `0` | Make each box larger than its cell, as `box-horizontal-padding` does for a hint box in a grid. |
| `<level>-font-*` | Size `10`, spacing `0`, shadow opacity `0`. The other defaults are the `font-*` defaults. | The font of the labels: `-font-name`, `-font-weight`, `-font-size`, `-font-color`, `-font-opacity`, `-font-spacing-percent`, `-font-vertical-alignment`, `-font-outline-*` and `-font-shadow-*`. |

A level with one row and one column shows only if it has label keys, a label override, a frame or a
fill.

```properties
# A frame around the grid, where the element box of a UI hint is.
refine-ui-hint-mode.overlay.box-framed=false
refine-ui-hint-mode.overlay.decoration-box-framed=true
refine-ui-hint-mode.overlay.decoration-box-border-radius=1
refine-ui-hint-mode.overlay.decoration-box-horizontal-padding=1
refine-ui-hint-mode.overlay.decoration-box-vertical-padding=1
```

### Hint prefix

The prefix of a label is its first keys, which all the hints of its [group](#overlay-area-and-grid)
share. For example, the prefix of `JKK` is `J` or `JK`, by the number of groups. Grid only.

- **`prefix-in-background`**: If `true`, the overlay shows the prefix of each group one time, in
  the background of the group. Default: `false`.
- The [group box](#group-box) draws a box around each group.

### Hint font states

Each key of a label has a state:

- **Focused**: the next key to type.
- **Selected**: a key that you typed.
- **Normal**: the other keys.

At the start, `J` of `JKK` is focused. After you type `J`, `J` is selected and the first `K` is
focused. After you type `K`, `J` and the first `K` are selected, and the last `K` is focused.

Each state has its own font properties:

| Keys | Properties | Fallback when not set |
|------|------------|-----------------------|
| Normal | `font-*` | – |
| Selected | `selected-font-*` | `font-*` |
| Focused | `focused-font-*` | `font-*` |
| Prefix, normal | `prefix-font-*` | `font-*` |
| Prefix, selected | `prefix-selected-font-*` | `prefix-font-*`, `selected-font-*`, `font-*` |
| Prefix, focused | `prefix-focused-font-*` | `prefix-font-*`, `focused-font-*`, `font-*` |
| [Saved hint](#saved-hints), all states | `saved-font-*` | `font-*` |
| Saved hint, prefix | – | `prefix-font-*`, `saved-font-*`, `font-*` |

- Each state has these properties: `font-name`, `font-weight`, `font-size`, `font-color`,
  `font-opacity`, `font-outline-thickness`, `font-outline-color`, `font-outline-opacity`,
  `font-shadow-blur-radius`, `font-shadow-color`, `font-shadow-opacity`, `font-shadow-stack-count`,
  `font-shadow-horizontal-offset` and `font-shadow-vertical-offset`.
- `font-spacing-percent` and `font-vertical-alignment` have no states. `prefix-font-spacing-percent`
  and `prefix-font-vertical-alignment` apply only to a prefix in the background.
- The fallback looks at the properties of the mode first, then at the properties of its parent.
- A [mutation](#mode-property-mutation) of a font property also changes the properties that fall
  back to it, if they are not set. For example, a mutation of `font-color` also changes
  `selected-font-color`, if `selected-font-color` is not set.

### Multi-level hints

The first level selects an area. The second level shows a smaller grid there:

```properties
key-alias.selectionkey=a b c d e f g h i j k l m n o p q r s t u v w x y z
hint1-mode.overlay.selection-keys=selectionkey
hint1-mode.to.hint2-mode=+selectionkey
# The mouse does not move after the first selection.
hint1-mode.overlay.mouse-follows-target=false

hint2-mode.overlay.area-center=last-selected-hint
hint2-mode.overlay.grid-max-row-count=3
hint2-mode.overlay.grid-max-column-count=8
hint2-mode.overlay.grid-cell-width=30
hint2-mode.overlay.grid-cell-height=20
hint2-mode.overlay.font-size=7
hint2-mode.overlay.selection-keys=selectionkey
hint2-mode.to.normal-mode=+selectionkey
```

### Overlay commands

These commands act on the overlay of the mode, whatever its type. The overlay area is the area of
the overlay, or the cells that the typed selection keys keep. An overlay without `selection-keys` has
no labels and selects nothing: it is a grid to shrink and to move.

```properties
grid-mode.overlay.grid-cell-sizing=fit
grid-mode.overlay.grid-max-row-count=2
grid-mode.overlay.grid-max-column-count=2
grid-mode.overlay.target-follows-overlay-center=true
# Keep the half of the grid in that direction.
grid-mode.shrink-overlay.up=+uparrow
# Move the grid to the area of the same size above it.
grid-mode.move-overlay-to.area-above=_{leftshift} +uparrow
# Move the target to the nearest cell edge above it.
grid-mode.move-target-to.cell-top=_{leftctrl} +uparrow
grid-mode.move-target-to.overlay-center=+space
grid-mode.start-move-target.up=_{leftalt} +uparrow
grid-mode.stop-move-target.up=-uparrow
grid-mode.nudge-target.up=_{leftalt leftshift} +uparrow
```

- **`shrink-overlay.<direction>`**: Keeps the half of the overlay area on that side.
- **`move-overlay-to.<area>`**: Moves the overlay area to `area-above`, `area-below`, `area-left` or
  `area-right`: the area of the same size next to it on that side. The center of the area stays on a
  screen.
- **`move-target-to.<point>`** / **`move-to.<point>`**: See [Hint selection](#hint-selection).
- **`start-move-target.<direction>`** / **`stop-move-target.<direction>`**: Start and stop a move of
  the target, with the `target` speed. While the mouse follows the target, they move the mouse, with
  the `mouse` speed. A switch to a mode where the mouse follows the target stops a move of the
  target. `istargetmoving` is pressed while the target moves.
- **`nudge-target.<direction>`**: Moves the target by `target.nudge-distance`. While the mouse
  follows the target, it moves the mouse by `mouse.nudge-distance`.
- **`nudge-hotspots`**, **`start-move-hotspots`**, **`stop-move-hotspots`**: See
  [Hotspots](#hotspots).

The name of a command tells what it moves:

| What moves | Jump to a place | Move by a distance | Start and stop a move |
|------------|-----------------|--------------------|-----------------------|
| The mouse | `move-to` | `nudge` | `start-move`, `stop-move` |
| The target | `move-target-to` | `nudge-target` | `start-move-target`, `stop-move-target` |
| The hotspots | – | `nudge-hotspots` | `start-move-hotspots`, `stop-move-hotspots` |
| The overlay area | `move-overlay-to`, `shrink-overlay` | – | – |

After `shrink-overlay` or `move-overlay-to`, the overlay shows what is in its new area:

- A grid puts its cells in it.
- UI hints find the elements in it, with a new scan.
- Position history hints show the positions in it.

A mode with the same overlay settings (see [Hotspots](#hotspots)) keeps the overlay area where the
commands put it.

## App aliases

An app alias gives a name to one or more apps, by their executable names:

```properties
app-alias.browserapp=firefox.exe chrome.exe msedge.exe
```

- The names must match exactly, with the same uppercase and lowercase letters.
- On macOS, put a name that has a space in double quotes: `app-alias.settingsapp="System Settings"`.
  On Windows, write the name without its spaces.

A precondition can use the alias. `_{browserapp}` is true when one of the apps is the active app.
`^{browserapp}` is true when another app is the active app:

```properties
idle-mode.to.browser-mode=_{browserapp}
browser-mode.to.idle-mode=^{browserapp}
```

## Position history

A position history keeps positions of the [target](#target). While the mouse follows the target,
these are mouse positions. A hint mode can show the saved
positions as hints.

```properties
position-history.max-size=16
normal-mode.save.position-history=+f1
normal-mode.clear.position-history=+f2
normal-mode.to.position-hint-mode=+p
position-hint-mode.overlay.type=position-history
position-hint-mode.overlay.selection-keys=selectionkey
```

The name of a history ends with `-position-history`, or is `position-history`. The history exists
when a mode has a command for it. Each command names its history.

| Property | Default | Description |
|----------|---------|-------------|
| `<history>.max-size` | `16` | The maximum number of positions, from `1` to `100`. When the history is full, a save removes the oldest position. |
| `<history>.isolation` | `none` | `active-app` keeps a separate list of positions for each app, by its executable name. `none` keeps one list for all apps. |

The commands:

- **`save.<history>`**: Saves the position of the target. If the target is on the hotspot of the
  last selected hint, the history also keeps that hint (see [Saved hints](#saved-hints) and the
  indicator [`position-anchor`](#position)).
- **`unsave.<history>`**: Removes the saved position that the target is on.
- **`toggle-save.<history>`**: Removes the saved position that the target is on. If the target is
  not on a saved position, saves the position.
- **`clear.<history>`**: Removes all the positions.
- **`cycle-next.<history>`** / **`cycle-previous.<history>`**: Move the target to the next or the
  previous saved position. The mouse follows, unless `mouse-follows-target=false`.

Rules:

- Each history has its own positions and its own cycle position.
- With `isolation=active-app`, the commands and the hints use the list of the active app.
  `max-size` applies to each list. `clear` removes only the positions of the active app.
- If the history has no position, a built-in virtual key is pressed: `is`, the name of the history
  without its hyphens, then `empty`. `_{isdragpositionhistoryempty}` is true while
  `drag-position-history` is empty. With `isolation=active-app`, it is the list of the active app.

```properties
# Firefox, Chrome and Edge each keep their own 4 positions. F4 clears only those of the active app.
browser-position-history.isolation=active-app
browser-position-history.max-size=4
normal-mode.save.browser-position-history=_{browserapp} +f3
normal-mode.clear.browser-position-history=+f4
```

### Position history hints

A hint mode with the history as its `overlay.type` shows one hint for each saved position, on the
screen of that position.

- `area` does not apply. `shrink-overlay` and `move-overlay-to` can limit the hints to an area.
- A selection of a hint moves the target to the position.
- If the history has no position, the mode saves the target first, so its hint shows.
- A mutation on `overlay.type` lets one mode show different histories:

  ```properties
  position-hint-mode.overlay.type=position-history | _{browserapp} -> browser-position-history
  ```

### Saved hints

An overlay can show which hints are saved. `saved-hints` names the position history that saves
them. The `saved-` properties style them:

```properties
ui-hint-mode.save.drag-position-history=+f5
ui-hint-mode.overlay.saved-hints=drag-position-history
ui-hint-mode.overlay.saved-box-color=#00FF00
ui-hint-mode.overlay.saved-box-opacity=0.5
ui-hint-mode.overlay.saved-ui-element-box-border-color=#00FF00
ui-hint-mode.overlay.saved-font-color=#000000
```

- A hint is saved if you saved a position of the history on its hotspot, after you selected the
  hint. The overlay finds the hint again by its element (UI hints), by its cell (grids), or else by
  its center. A position saved at another place marks no hint.
- These properties replace the values of the box for a saved hint: `saved-box-color`,
  `saved-box-opacity`, `saved-box-border-color`, `saved-box-border-opacity` and the same four
  properties with `saved-ui-element-box-` and `saved-hotspot-box-`. A value that is not set is the
  value of the box, with its mutations.
- The `saved-font-*` properties replace the font of all the keys of a saved hint (see
  [Hint font states](#hint-font-states)).
- A saved hint stays when the typed keys hide the other hints.
- A save, an unsave or a clear of the history shows on the overlay immediately.

## Macros

A macro sends a sequence of key presses and releases when its combo completes:

```properties
<mode>.macro.<name>=<combo> -> <output>
```

The output sends keys to the OS or to mousemaster:

| Output   | Meaning                                                             | Example     |
|----------|---------------------------------------------------------------------|-------------|
| `+key`   | Press the key. The OS gets it.                                      | `+leftctrl` |
| `-key`   | Release the key. The OS gets it.                                    | `-leftctrl` |
| `'text'` | Type the text, whatever the active keyboard layout. The OS gets it. | `'hello'`   |
| `#key`   | Press the key. mousemaster gets it, and its combos can complete.    | `#rightalt` |
| `~key`   | Release the key. mousemaster gets it.                               | `~rightalt` |
| `wait-N` | Wait N milliseconds.                                                | `wait-50`   |
| `key`    | Tap: `#key ~key`.                                                   | `rightalt`  |

For the full output syntax, see [combo-reference.md](combo-reference.md#macro-output-syntax).

```properties
# v sends ctrl+v.
normal-mode.macro.paste=+v -> +leftctrl +v -v -leftctrl
# win+f1 opens notepad.
idle-mode.macro.notepad=_{leftwin} +f1 -> +leftwin +r -r -leftwin wait-100 'notepad' +enter -enter
# f12 taps c, then k. In neo-mousekeys-ijkl, c shows the screen hints, and k selects screen 2.
normal-mode.macro.selectsecondscreen=+f12 -> c k
```

## Virtual keys

A virtual key is a key that only mousemaster knows. Declare it with its start state, `pressed` or
`released`:

```properties
virtual-key.drilling=released
```

- No keyboard can press a virtual key, and the OS never gets it. A macro presses and releases it with
  `#` and `~`. On a virtual key, `+` and `-` do the same as `#` and `~`.
- A combo, a precondition and a [mutation](#mode-property-mutation) can use it as a key.
- A virtual key keeps its state after a mode switch. A configuration reload returns it to its start
  state.
- A key name that is not declared and is not a real key is an error when the configuration loads.

```properties
# drilling is pressed while the macro runs. The overlay hides while drilling is pressed.
virtual-key.drilling=released
grid-mode.macro.drill=+g -> #drilling k k ~drilling
grid-mode.overlay.visible=true | _{drilling} -> false
```

For more, see [virtual keys](combo-reference.md#virtual-keys).

## Console window, exit and restart

```properties
hide-console=true
```

- **`hide-console`**: If `true`, mousemaster hides its console window. Windows only. Default:
  `false`.
- Without the console, you do not see the errors and the logs. `logging.to-file=true` writes them to
  a file (see [Logging](#logging)).
- **`exit`**: Stops mousemaster: `normal-mode.exit=+leftctrl +leftalt +q`. On Windows, you can also
  stop mousemaster in the Task Manager, or with `taskkill /F /IM mousemaster.exe`.
- **`restart`**: Restarts mousemaster with the same arguments:
  `normal-mode.restart=+leftctrl +leftalt +r`.

## Logging

```properties
logging.level=INFO
logging.key-redaction=pseudonymize
logging.to-file=true
log-last-key-events-on-exit=true
```

| Property | Default | Description |
|----------|---------|-------------|
| `logging.level` | `DEBUG` | `ERROR`, `WARN`, `INFO`, `DEBUG` or `TRACE`. `DEBUG` and `TRACE` log more, and can make mousemaster slower. |
| `logging.key-redaction` | `none` | How the log shows a key that types a character (see below). |
| `logging.to-file` | `false` | If `true`, mousemaster also writes the log to `mousemaster.log`, in the working directory. |
| `log-last-key-events-on-exit` | `false` | If `true`, mousemaster logs the last key events when it stops (see below). |

**`logging.key-redaction`**:

- `none`: the name of the key.
- `pseudonymize`: a name such as `key#1`. The name stays the same until the configuration loads
  again, so a press and its release have the same name.
- `anonymize`: `<redacted>` for all keys.
- A key that types no character, such as a modifier, an arrow or a function key, always shows its
  name.

**`log-last-key-events-on-exit`**:

- They are the key events of the last 15 seconds, and at least the last 128 events.
- The format is the combo syntax, with the milliseconds between the events:
  `[+a-377 -a-32 #leftwin]`. `+` is a press that mousemaster ate. `#` is a press that the OS also
  got.
- On Windows, mousemaster also logs them when it finds a stuck key.

## Keyboard layout

On Windows, mousemaster uses the keyboard layout of the active window. On macOS, it uses the
selected input source. mousemaster follows the layout when it changes.
`forced-active-keyboard-layout` replaces the layout that mousemaster finds:

```properties
forced-active-keyboard-layout=us-dvorak
```

The layout names are `de-qwertz`, `es-qwerty`, `fr-azerty`, `it-qwerty`, `jp-kana-ansi`,
`jp-kana-jis`, `pt-qwerty-abnt2`, `ru-jcuken`, `sv-qwerty`, `uk-qwerty`, `us-dvorak`, `us-halmak`,
`us-qwerty` and `zh-qwerty-pinyin`. A layout can also be a Windows keyboard layout identifier, such
as `00000409`.

`configuration-keyboard-layout` is the layout of the keys in the configuration. mousemaster changes
each key to the key at the same place (the same scan code) on the active layout. For example, with
`configuration-keyboard-layout=us-qwerty`, a combo with `0` completes on the `à` key of `fr-azerty`.
Default: the active layout when the configuration loads.

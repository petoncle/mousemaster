# mousemaster configuration reference

## Contents
- [Key aliases](#key-aliases)
- [Combos](#combos)
- [Modes](#modes)
- [Mode switching](#switching-modes)
- [Mode history](#mode-history)
- [Standalone mode properties](#standalone-mode-properties)
- [Mode property mutation](#mode-property-mutation)
- [Mouse properties](#mouse-properties)
- [Wheel properties](#wheel-scrolling-properties)
- [Indicator properties](#indicator-properties)
- [Cursor properties](#cursor-properties)
- [Zoom properties](#zoom-properties)
- [Mouse move commands](#mouse-move-commands)
- [Mouse button click commands](#mouse-button-click-commands)
- [Wheel commands](#wheel-scrolling-commands)
- [Overlay properties](#overlay-properties)
- [App aliases](#app-aliases)
- [Position history](#position-history)
- [Macros](#macros)
- [Console window](#console-window)
- [Logging](#logging)
- [Keyboard layout](#keyboard-layout)

## Key aliases

A key alias is a way to give a name to one or more keys:

```properties
key-alias.exit=q p
key-alias.clickthendisable=.
key-alias.up=w
```

The alias can be then used as part of a combo:

```properties
normal-mode.to.idle-mode=+exit
```

This combo can be completed by either pressing q or p.

An alias can be declared per keyboard layout, `key-alias.<name>.<layout>=...`, and mousemaster picks
the one matching the active layout (`key-alias.up.us-qwerty=w`). A declaration with no layout is the
fallback. See [Keyboard layout](#keyboard-layout) for the layout names.

The complete available key list can be found in [key-list.md](key-list.md). 

## Combos

Combos are sequences of key presses and releases that trigger commands in mousemaster. For the full syntax reference (duration, wait moves, aliases, optionality, negated moves, macros, etc.), see [combo-reference.md](combo-reference.md).

### Basic combo syntax

| Syntax             | Meaning                                       |
|--------------------|-----------------------------------------------|
| `+key`             | Key press (eaten — not passed to the OS)      |
| `#key`             | Key press (not eaten — passed to the OS)      |
| `-key`             | Key release                                   |
| `key`              | Tap (press then release)                      |
| `{+a +b}`          | Any-order move set                            |
| `_{keys}`          | Precondition: listed keys must be pressed     |
| `^{keys}`          | Precondition: listed keys must not be pressed |
| `combo1 \| combo2` | Multiple alternative combos                   |
| `none`             | No combo                                      |

### Examples

```properties
# Press e while holding leftctrl
normal-mode.to.hint-mode=_{leftctrl} +e

# Press and release leftshift
normal-mode.move-target-to.grid-center=+leftshift -leftshift

# Multiple combos for one command
normal-mode.to.idle-mode=+q | +leftctrl +e

# Press a and b in any order (chord)
normal-mode.to.idle-mode={+a +b}
```

## Modes

Modes are a fundamental concept in mousemaster. Each mode represents a different state of the application with its own set of key bindings and behaviors.

### Mode basics

- Most properties are defined per-mode (e.g., `normal-mode.indicator.enabled=true`)
- The only mode that exists by default is `idle-mode` (when mousemaster is inactive)
- You must define all other modes yourself
- Each mode can have its own key combos, mouse settings, and visual indicators

### Mode properties

Properties for a mode are defined using the format:
```
mode-name.property-category.property-name=value
```

For example:
```properties
normal-mode.indicator.enabled=true
normal-mode.mouse.initial-velocity=1600
ui-hint-mode.overlay.type=ui-vision
```

### Property inheritance

Modes can inherit properties from other modes to avoid duplication. For example, if `normal-mode` has defined indicator settings:

```properties
normal-mode.indicator.enabled=true
normal-mode.indicator.fill-color=#FF0000
normal-mode.indicator.size=26
```

You can make another mode inherit these settings:
```properties
slow-mode.indicator=normal-mode.indicator
```

This applies all indicator properties from `normal-mode` to `slow-mode`. You can still override specific properties:
```properties
slow-mode.indicator=normal-mode.indicator
slow-mode.indicator.fill-color=#0000FF  # Override just this property
```

A mode can also inherit everything from one or more other modes:
```properties
slow-mode=_indicator-mode _clipboard-mode
```

The mode's own properties come first, then each parent in turn: the first one to define a
property keeps it. A property that names its own source (`slow-mode.indicator=other-mode.indicator`)
takes it from there instead of from any parent.

A command set to `none` has no combo, and the mode does not take the parent's combos for it:
```properties
slow-mode.press.middle=none
```

### Common mode types

While you can create any modes you need, these are common in many configurations:

- `idle-mode`: mousemaster is inactive (default mode when mousemaster starts)
- `normal-mode`: Basic mouse control mode
- `grid-mode`: For grid-based navigation
- `hint1-mode` and `hint2-mode`: For hint-based navigation

### Example mode configuration

```properties
# Define how to enter normal-mode from idle-mode
idle-mode.to.normal-mode=_{leftctrl} +e

# Define mouse movement in normal-mode
normal-mode.start-move.up=+i
normal-mode.start-move.down=+k
normal-mode.start-move.left=+j
normal-mode.start-move.right=+l
normal-mode.stop-move.up=-i
normal-mode.stop-move.down=-k
normal-mode.stop-move.left=-j
normal-mode.stop-move.right=-l

# Define mouse speed in normal-mode
normal-mode.mouse.initial-velocity=1600
normal-mode.mouse.max-velocity=2200
normal-mode.mouse.acceleration=1500

# Define how to exit back to idle-mode
normal-mode.to.idle-mode=+q
```

### Switching modes
```properties
idle-mode.to.normal-mode=_{leftctrl} +e
normal-mode.to.idle-mode=+q
```

Combos can be initiated in one mode and continued in another.
```properties
normal-mode.press.left=+a
normal-mode.to.click-mode=+a
click-mode.release.left=+a -a
```
In the above example, pressing _a_ triggers a press of the left mouse button and switches
to the mode named click-mode. In click-mode, releasing _a_ triggers a release of the left mouse button.
If we add the property:
```properties
normal-mode.break-combo-preparation=+a
```
Then, the release command in click-mode will only be triggered if the user presses _a_
again then releases it. This happens because the `+a` combo preparation initiated in normal-mode was broken.

### Mode history

```properties
idle-mode.push-mode-to-history-stack=true
hint2-2-mode.to.previous-mode-from-history-stack=+rightalt -rightalt
```
- If `push-mode-to-history-stack` is true for a mode, the mode will be added to the mode
  history as soon as the current mode is switched to that mode.
- The last mode added to the history stack can be referred to in a mode switch (`to`) command with `previous-mode-from-history-stack`.

### Standalone mode properties

```properties
idle-mode.stop-commands-from-previous-mode=true
normal-mode.mode-after-unhandled-key-press=idle-mode
```

- If `stop-commands-from-previous-mode` is true for a mode, and if the previous mode was executing
  a command such as a mouse button press, then the command will be stopped (the mouse
  button will be released) as soon as the current mode is switched to that mode.
- `mode-after-unhandled-key-press` is for switching to a mode whenever a key that is not
  part of any combo is pressed.

### Mode property mutation

Mode property mutation allows a mode property to change its value dynamically based on which keys are currently pressed, without switching modes.

```
mode.property=default | combo1 -> value1 | combo2 -> value2
```

The `|` separates the default value from combo-triggered mutations. Each `combo -> value` pair applies its value when the combo is satisfied. A part without `->` is the default value.

For example, change the hint box border color while shift is held, to signal that a different action will follow:

```properties
hint-mode.overlay.box-border-color=#FFFFFF | _{leftshift} -> #00FFFF
```

- Default border color: `#FFFFFF` (white)
- While shift is held: `#00FFFF` (cyan)

When shift is released, the property reverts to its default. Mutations are most useful with precondition-only combos (combos that have no key sequence, only a precondition like `_{key}`). These combos activate when the precondition becomes true and revert when it becomes false.

```properties
# Change the indicator color while leftctrl is held
normal-mode.indicator.fill-color=#00FF00 | _{leftctrl} -> #FF0000
```

Mutations can also be driven by [virtual keys](combo-reference.md#virtual-keys) instead of held keys, so the value
persists across mode switches and survives key releases:

```properties
# Toggle iszoom on/off with z, change zoom percent based on the virtual key
virtual-key.iszoom=released
hint-mode.macro.setiszoom=^{iszoom} +z -> #iszoom
hint-mode.macro.unsetiszoom=_{iszoom} +z -> ~iszoom
hint-mode.zoom.percent=1 | _{iszoom} -> 30
```

A virtual key starts in its declared state, and reloading the configuration returns every one to it.

When mutating a hint font property, related properties are automatically updated. For example, mutating `font-color` also mutates `selected-font-color`, `focused-font-color`, `saved-font-color`, and the corresponding prefix variants (unless they were explicitly set in the configuration).

### Mouse properties

```properties
normal-mode.mouse.initial-velocity=1600
normal-mode.mouse.max-velocity=2200
normal-mode.mouse.acceleration=1500
normal-mode.mouse.acceleration-easing=1
normal-mode.mouse.deceleration=0
normal-mode.mouse.smooth-jump-enabled=true
normal-mode.mouse.smooth-jump-velocity=30000
```
- `acceleration-easing` controls the shape of the acceleration curve. A number sets the polynomial exponent: 1 = linear, 2 = quadratic (slow start, fast ramp), 0.5 = square root (fast start, slow ramp). Named curves: `smoothstep` (S-curve), `smootherstep` (S-curve with longer precision zone, the default), `logarithmic` (fast start, aggressive flatten), `exponential` (very slow start, explosive ramp).
- When movement keys are released, the cursor coasts to a stop. Higher deceleration = shorter coast. Set to 0 for instant stop (no coast).
- The velocity and acceleration are defined in pixel per second and pixel per square second.
- Whenever mousemaster sets the position of the mouse, the mouse will be teleported to the
  target position. Smooth jumping is for avoiding the instantaneous teleportation and move
  the mouse with a continuous movement instead. The jump speed is controlled with `smooth-jump-velocity`.

### Wheel (scrolling) properties

```properties
normal-mode.wheel.initial-velocity=1500
normal-mode.wheel.max-velocity=2000
normal-mode.wheel.acceleration=500
normal-mode.wheel.acceleration-easing=1
normal-mode.wheel.deceleration=0
```

The properties mean what the mouse ones do, except that `acceleration-easing` defaults to `1`
(linear) rather than `smootherstep`, and `deceleration` to `0` (no coast).

### Indicator properties

The indicator is drawn around the cursor as one or more layers, each a shape with a fill and a
stroke. `<mode>.indicator.<property>` sets the main layer, and `<mode>.<name>-indicator.<property>`
adds a layer of that name:

```properties
normal-mode.indicator.enabled=true
normal-mode.ring-indicator.enabled=true
normal-mode.ring-indicator.size=40
normal-mode.ring-indicator.fill-opacity=0
```

Every layer starts from the same defaults (a disabled 26-pixel red circle), not from the mode's
`indicator`, and a mode that extends another extends each of its layers by name.
`render-as-cursor` and `exclude-from-capture` belong to the whole indicator, so they are set on
`<mode>.indicator` only.

Any color property takes a hex color (`#FFFFFF`), a gradient (see [hint appearance](#hint-appearance)),
or `last-selected-hint-box-color`, the color at the center of the box of the hint selected last,
which is black until one has been selected. It plays a click in the color of the hint that was
clicked:

```properties
_hint-indicator-mode.indicator.fill-color=last-selected-hint-box-color
```

#### Following the mouse state

Any indicator property can take a different value per mouse state, with a
[mutation](#mode-property-mutation) on the [built-in virtual key](combo-reference.md#built-in-virtual-keys)
of that state. The rightmost matching branch wins:

```properties
normal-mode.indicator.fill-color=#FF0000 | _{iswheeling} -> #FFFF00 | _{ismousepressing} -> #00FF00 | _{isunhandledkeypressing} -> #0000FF
normal-mode.indicator.fill-opacity=0 | _{isleftmousepressing ismoving} -> 1
```

#### Layers

- **`enabled`**: Whether the layer is drawn (default `false`).
- **`z`**: The layer's order, higher on top (default `0`).

#### Shape

```properties
normal-mode.indicator.shape=rectangle
normal-mode.indicator.size=30
normal-mode.indicator.aspect-ratio=2
normal-mode.indicator.border-radius=4
normal-mode.indicator.rotation=45
```

- **`shape`**: `circle` (default), `triangle`, `rectangle`, `star`, `line`, `cross`, `path` or `text`.
- **`size`**: The largest side of the shape's box, in pixels (default `26`).
- **`aspect-ratio`**: How much wider than tall the box is (default `1`). A triangle, a star and a
  path keep the proportions of their points, which `aspect-ratio` then stretches.
- **`border-radius`**: The radius of the corners of a rectangle, a triangle, a star or a path, in
  pixels (default `0`).
- **`points`**: The points of a `path`, like `0,0 10,0 5,8`: at least three, spanning a width and
  a height, in any units, since they are scaled to the box.
- **`x`** / **`y`**: An offset from the layer's position, in pixels (default `0`).
- **`distance`** / **`angle`**: A further offset of `distance` pixels in the direction of `angle`,
  in degrees clockwise from 12 o'clock like `stroke-start-angle` (default `0`). Animating `angle`
  moves the layer around its position, and `rotation` turns it where it is:
  `distance=30` and `angle=0 | _{orbitanimation} -> 0% 0; 100% 360` orbit the cursor.
- **`rotation`**: In degrees, clockwise, around the layer's own center (default `0`).

#### Fill and stroke

```properties
normal-mode.indicator.fill-color=#FF0000
normal-mode.indicator.fill-opacity=0.2
normal-mode.indicator.stroke-thickness=2
normal-mode.indicator.stroke-color=#FFFFFF
normal-mode.indicator.stroke-length-percent=0.6
normal-mode.indicator.stroke-start-angle=180
```

- **`fill-color`** / **`fill-opacity`**: The inside of the shape (default `#FF0000` at `0.2`).
- **`stroke-thickness`** / **`stroke-color`** / **`stroke-opacity`**: The outline, centered on the
  shape's edge (default `1` pixel of `#FF0000` at `1`). A second outline is a second layer.
- **`stroke-length-percent`**: How much of the outline is drawn, from `-1` to `1` (default `1`).
  A negative length goes counterclockwise.
- **`stroke-start-angle`**: Where a partial outline starts, in degrees clockwise from 12 o'clock
  (default `0`).
- **`stroke-anchor`**: `start` (default) grows a partial outline from its start angle, `middle`
  grows it both ways.
- **`stroke-dash-length`** / **`stroke-dash-gap`** / **`stroke-dash-offset`**: Dashes, in pixels
  (default `0`, no dashes).
- **`stroke-cap`**: The ends of the outline and of its dashes: `flat` (default), `round` or
  `square`.

A line, a cross and the letters of a text have no single outline, so they are always stroked in
full.

#### Text

With `shape=text`, the letters are the shape: the fill colors them, the stroke outlines them, and
their size comes from the font rather than from `size` and `aspect-ratio`.

```properties
typing-mode.text-indicator.enabled=true
typing-mode.text-indicator.shape=text
typing-mode.text-indicator.text=n
typing-mode.text-indicator.font-name=Consolas | _{ismacos} -> Menlo
typing-mode.text-indicator.font-size=9
typing-mode.text-indicator.font-weight=bold
```

- **`text`**: The text. `{alias}` is replaced with the names of the keys of a
  [key alias](#key-aliases), like `{exit}` for `q p`. When the value comes from a combo that
  pressed keys of that alias, it is instead the labels of the keys it pressed (`I`, `⇧`, `␣`...),
  and `{!alias}` the key a `+!alias` pressed. A remap between the combo and the value gives
  each key a text of its own instead of its label: with
  `| +move -> move=up left down right -> {move}`, pressing `j` shows `left` (see
  [alias remap](combo-reference.md#alias-remap)).
- **`font-name`** / **`font-size`** / **`font-weight`**: The font, its size in points, and its weight
  from `thin` to `black` (default Arial, `8`, `normal`).

On any other shape, the text is rich text (the subset of HTML that Qt draws) drawn over the shape,
and the shape takes the text's size: `size` and `aspect-ratio` no longer apply. The fill, the
stroke and the `border-radius` style the box around the text, and `text-style-sheet` styles the
text with CSS. The neo-mousekeys-ijkl cheatsheet is such a layer:

```properties
normal-mode.cheatsheet-indicator.shape=rectangle
normal-mode.cheatsheet-indicator.position=screen-center
normal-mode.cheatsheet-indicator.text-style-sheet=\
td { color: #A9B1D6; } \
.title { font-family: 'Segoe UI', 'Helvetica Neue'; font-size: x-large; color: #C0CAF5; } \
.key { color: #E0AF68; }
normal-mode.cheatsheet-indicator.text=<table cellpadding=14><tr><td><table>\
<tr><td colspan=2 class=title>neo-mousekeys-ijkl\
<tr><td class=key>{up} {left} {down} {right}<td>move\
</table></table>
normal-mode.cheatsheet-indicator.font-name=Consolas | _{ismacos} -> Menlo
normal-mode.cheatsheet-indicator.font-size=9
normal-mode.cheatsheet-indicator.border-radius=14
normal-mode.cheatsheet-indicator.fill-color=#1A1B26
```

- The letters take their color from the style sheet, or the system's text color, usually black.
  Qt ignores a `body` rule, so a default color goes on the elements the text is made of, like `td`.
- `font-size` sets the size of the whole text, and the style sheet sizes parts of it relative to
  that: `small`, `medium`, `large`, `x-large` and `xx-large`. An absolute size in the style sheet
  would ignore `font-size` and its per-screen values.
- The padding of an outer table is the margin between the text and its box, and its width is
  where the text wraps: `<table width=200>`.

A value that comes from a combo stays after its keys are released, so a text can show the last
key pressed for as long as an animation runs:

```properties
key-alias.move=i j k l
keycast-animation.duration-millis=1000
normal-mode.keycast-animation.start=+move
normal-mode.keycast-indicator.enabled=false | _{keycastanimation} -> true
normal-mode.keycast-indicator.shape=rectangle
normal-mode.keycast-indicator.text-style-sheet=td { color: #FFFFFF; }
normal-mode.keycast-indicator.text= | +move -> <table cellpadding=6><tr><td>You pressed <b>{move}</b></table>
```

#### Position

- **`position`**: `mouse` (default), `mouse-top-left`, `mouse-top-right`, `mouse-bottom-left`,
  `mouse-bottom-right`, `screen-center`, `target` or `last-selected-hint`. `mouse` centers the
  layer behind the cursor, best with a low opacity so the cursor stays visible. Near a screen
  edge, a corner position moves to the other side of the cursor, and a layer with a text moves
  inward instead of being cut. `target` and `last-selected-hint` center the layer on those
  points of the overlay (see `mouse-follows-target`), and the layer moves with them, not with
  the cursor. While the mouse follows the target, the target is the mouse, and a layer on it
  is drawn again at each mouse move: to draw at the cursor, use `mouse`.

  With two positions, the layer runs from the first to the second: their distance is its width,
  and `rotation` turns it from their direction. `aspect-ratio` still sets its height, and `size`
  cannot be set. A line from the last selected hint to the target, with a dot at the
  target:
  ```properties
  grid-mode.line-indicator.shape=line
  grid-mode.line-indicator.position=last-selected-hint target
  grid-mode.dot-indicator.position=target
  ```
- **`follow-mouse`**: Whether the layer moves with the cursor (default `true`). With `false`, the
  layer stays where it was when it was enabled, or when its keyframes started over.
- **`area`**: The area the layer covers. It takes the values of the overlay's `area`, with
  `area-width-percent`, `area-height-percent`, the `area-*-inset` and `area-center` (see
  [Overlay area and layout](#overlay-area-and-layout)). The layer takes its position and its size
  from the area, so `position`, `size` and `aspect-ratio` cannot be set, and it does not follow
  the mouse. The stroke is centered on the edge of the area: an inset of minus half the stroke
  thickness puts the stroke just outside it. A box around the element of the last selected UI
  hint:
  ```properties
  grid-mode.element-indicator.shape=rectangle
  grid-mode.element-indicator.area=last-selected-hint-cell
  grid-mode.element-indicator.area-top-inset=-0.5
  grid-mode.element-indicator.area-bottom-inset=-0.5
  grid-mode.element-indicator.area-left-inset=-0.5
  grid-mode.element-indicator.area-right-inset=-0.5
  grid-mode.element-indicator.fill-opacity=0
  ```

#### Shadow

```properties
normal-mode.indicator.shadow-blur-radius=10
normal-mode.indicator.shadow-color=#000000
normal-mode.indicator.shadow-opacity=0.5
normal-mode.indicator.shadow-horizontal-offset=2
normal-mode.indicator.shadow-vertical-offset=2
```

- **`shadow-blur-radius`**: Blur radius in pixels (0-1000, default 10)
- **`shadow-color`**: Color of the shadow (default #000000)
- **`shadow-opacity`**: Opacity (0.0-1.0, default 0 = no shadow). Set to > 0 to enable.
- **`shadow-stack-count`**: Number of times the shadow is composited on itself for a stronger effect (1-100, default 1)
- **`shadow-horizontal-offset`** / **`shadow-vertical-offset`**: Shadow offset in pixels (-100 to 100, default 0)

Each layer casts its own shadow. Layers with the same shadow cast it as one shape, and a shadow is
never drawn over a layer, even a translucent one.

#### Rendering as the system cursor

```properties
normal-mode.indicator.render-as-cursor=true
```

`render-as-cursor` draws the layers that follow the mouse into the system cursor image, with the
real cursor glyph composited on top, instead of into a window that follows the pointer.
Nothing can lag behind, since there is nothing to follow. Windows only (default false); it is
ignored on macOS, where the indicator is always a window.

Because the indicator then *is* the cursor, anything that hides the cursor hides the indicator
too. In particular, the Windows pointer option "Hide pointer while typing" (on by default)
hides the cursor on every keystroke until the mouse moves, so with `render-as-cursor` the
indicator disappears right after a key press and only comes back once the pointer is moved.
Turn that option off (Mouse Properties > Pointer Options > Visibility) if you see this; the
windowed indicator is not affected. See [#86](https://github.com/petoncle/mousemaster/issues/86).

#### Excluding from capture

```properties
screenshot-animation.duration-millis=300
normal-mode.screenshot-animation.start=#printscreen
normal-mode.flash-indicator.enabled=false | _{screenshotanimation} -> true
normal-mode.indicator.exclude-from-capture=false | _{screenshotanimation} -> true
```

`exclude-from-capture` keeps the indicator out of screenshots and screen recordings while it
stays on screen (default false), here only while a screenshot flash plays. It applies to the
indicator's windows; drawn as the cursor, the indicator follows the capture tool's own cursor
setting.

An animation is a named clock, defined once for the whole configuration and started by combos:

```properties
click-burst-animation.duration-millis=500
_indicator-mode.click-burst-animation.start=+isleftmousepressing | +ismiddlemousepressing | +isrightmousepressing
_indicator-mode.click-burst-animation.stop=_{clickburstanimation ismousepressing} wait-250
```

- **`<name>-animation.duration-millis`**: The length of one cycle. Required.
- **`<name>-animation.repeat`**: A number of cycles (default `1`) or `loop`.
- **`<name>-animation.direction`**: `forward` (default) or `alternate`, which plays every other
  cycle backwards.
- **`<mode>.<name>-animation.start`** / **`stop`**: The combos that start and stop it in that mode.
  Starting a running animation starts it over.

While it runs, an animation reads as a pressed key named without its hyphens
(`clickburstanimation`), so `_{clickburstanimation}` matches while it runs and
`-clickburstanimation` when it ends or is stopped. A loop runs until it is stopped. An animation
keeps running across mode changes, but the layers it animates belong to their mode. To start one on
entering a mode, use `start=#{*}-0`.

A branch on an animation can give a property keyframes instead of a value:

```properties
click-rest-animation.duration-millis=80
_indicator-mode.click-rest-animation.start=-clickburstanimation
_indicator-mode.indicator.size=26 | _{clickrestanimation} -> 0% current; 100% 26 0.7 | _{clickburstanimation} -> 0% 13; 100% 78 exponential-out-10
_indicator-mode.indicator.stroke-opacity=1 | _{clickburstanimation} -> 0% 1; 250ms 1; 500ms 0 0.7
```

- A keyframe is a position, a value and an optional easing. The position is a percent of the
  animation or a time (`250ms`).
- `current` is the value the property had when the branch took over, so an animation can start
  from wherever the previous one left it.
- The easing shapes the change that ends at the keyframe: a polynomial exponent (`1`, the default,
  is linear), `smoothstep`, `smootherstep`, `logarithmic`, `exponential`, `exponential-out-<n>`, or
  `step`, which holds the previous value until the keyframe.
- Numbers ease, and so do colors with as many colors on both sides. Anything else (a shape, a
  text, `follow-mouse`...) switches at its keyframe.
- The frame at 100% is drawn before the animation ends.
- Keyframes need a branch that names exactly one animation, and only indicator and `hide-cursor`
  properties take them.

### Cursor properties

The cursor can be hidden after a certain duration of inactivity:
```properties
hint1-mode.hide-cursor.enabled=true
hint1-mode.hide-cursor.idle-duration-millis=0
```

### Zoom properties

Automatic zooming can be defined with:
```properties
hint2-2-mode.zoom.percent=5.0
hint2-2-mode.zoom.center=last-selected-hint
```

- `zoom.percent` is between 1.0 (100%, no magnification) and 100.
- `zoom.center` can either be `screen-center` (default), `mouse`
  or `last-selected-hint`.
- `zoom.area-size-source` is what the magnified area is sized from: `percent` (default), scaled by
  `zoom.area-width-percent` / `zoom.area-height-percent` (default `1.0`), or `last-selected-hint-cell`.
- On macOS, zooming needs the Screen Recording permission to capture what it magnifies.

Optional animation can be enabled to smoothly transition in and out of zoom:
```properties
zoom-mode.zoom.animation-enabled=true
zoom-mode.zoom.animation-easing=smootherstep
zoom-mode.zoom.animation-duration-millis=300
```

- `zoom.animation-enabled` defaults to `false`.
- `zoom.animation-easing` can be `smoothstep`, `smootherstep`, `logarithmic`, `exponential`, or a number (the polynomial exponent, `1` = linear). Defaults to `smootherstep`.
- `zoom.animation-duration-millis` is the duration of a full zoom transition (from 1x to the configured percent). Partial transitions (e.g. interrupted animations) are proportionally shorter. Defaults to `200`.

### Mouse move commands

```properties
normal-mode.start-move.up=+up
normal-mode.start-move.down=+down
normal-mode.start-move.left=+left
normal-mode.start-move.right=+right
normal-mode.stop-move.up=-up
normal-mode.stop-move.down=-down
normal-mode.stop-move.left=-left
normal-mode.stop-move.right=-right
```

### Mouse button click commands

```properties
# Capture leftctrl (i.e. do not exit to idle mode when leftctrl is pressed) to be able to ctrl-click.
normal-mode.press.left=_{none | leftctrl} +leftbutton | _{none | leftctrl} +clickthendisable
normal-mode.press.middle=+middlebutton
normal-mode.press.right=_{none | leftctrl} +rightbutton
normal-mode.release.left=-leftbutton
normal-mode.release.middle=-middlebutton
normal-mode.release.right=-rightbutton
normal-mode.toggle.left=_{none | leftctrl} +toggleleft
normal-mode.toggle.right=_{none | leftctrl} +toggleright
```

### Wheel (scrolling) commands
```properties
normal-mode.start-wheel.up=+wheelup
normal-mode.start-wheel.down=+wheeldown
normal-mode.start-wheel.left=+wheelleft
normal-mode.start-wheel.right=+wheelright
normal-mode.stop-wheel.up=-wheelup
normal-mode.stop-wheel.down=-wheeldown
normal-mode.stop-wheel.left=-wheelleft
normal-mode.stop-wheel.right=-wheelright
```

## Overlay properties

The overlay is what a mode lays over the screen to pick a place: a grid, boxes on UI elements, or
saved positions. Each box is a hint, and `selection-keys` labels the hints with the keys to type.
The indicator and the zoom are configured on their own: `overlay.visible=false` hides the overlay
only.

The hint system in mousemaster displays labels on the screen that you can select with keystrokes to move the mouse to specific locations. This is similar to the hint systems in browser extensions like Vimium, but works system-wide.

### Overlay types

The type of overlay is configured with the following property:

```properties
ui-hint-mode.overlay.type=ui-vision
```

The **`type`** property determines what hints are displayed:
- `grid` (default): Displays a grid of hints laid out across the screen
- `position-history`, or any `<name>-position-history`: Displays discrete absolutely positioned hints at the positions saved in that history (see [Position history](#position-history))
- `ui-accessibility`: Displays hints at interactive UI elements (see [Accessibility UI hints](#accessibility-ui-hints))
- `ui-vision`: Displays hints at elements detected on screen (see [Vision UI hints](#vision-ui-hints))

### Accessibility UI hints

Accessibility UI hints detect interactive elements (buttons, links, text fields, checkboxes,
etc.) and display a hint label on each one, with the UI Automation API on Windows and the
Accessibility API on macOS. On macOS this needs the Accessibility permission, without which no
element is found. Unlike grid hints, accessibility UI hints are positioned based on the UI
elements' locations.

```properties
ui-hint-mode.overlay.type=ui-accessibility
ui-hint-mode.overlay.area=active-screen
ui-hint-mode.overlay.selection-keys=selectionkey
ui-hint-mode.overlay.select=+selectionkey
ui-hint-mode.overlay.deselect=backspace
ui-hint-mode.overlay.mouse-follows-target=true
ui-hint-mode.overlay.font-size=10
ui-hint-mode.overlay.font-weight=bold
ui-hint-mode.overlay.font-color=#FFFFFF
ui-hint-mode.overlay.box-color=#204E8A
ui-hint-mode.overlay.box-opacity=1
ui-hint-mode.overlay.box-horizontal-padding=8
ui-hint-mode.overlay.box-vertical-padding=0
ui-hint-mode.overlay.box-border-radius=3
```

**`area`** (see [Overlay area and layout](#overlay-area-and-layout)) selects the
windows the elements are looked for in:
- `active-screen`: Every visible window on the screen with the mouse cursor
- `all-screens`: Every visible window on every connected screen
- `active-window`: The active window, and its popups (menus, dropdowns, dialogs). A popup
  is hinted even where it extends past the window.
- `last-selected-hint-cell`: The cell of the last selected hint, the element of a UI hint
- `last-overlay-area`: The area of the overlay before this mode, e.g. a grid shrunk by `shrink-grid`

With the other regions, elements are cropped to the region, and elements
covered by a window drawn over theirs are left out, so a hint is only shown where the
click it performs would reach the element. Windows of another virtual desktop and
suspended apps are left out. Scanning several windows takes longer than scanning one: the
hints appear once the scan is over, mousemaster stays responsive in the meantime.

**`area-width-percent`**, **`area-height-percent`** and **`area-center`** shrink and move that
region as they do a grid's, so that only the elements around a point get a hint. With
`all-screens`, each screen is scaled around its own center. An `active-window` region that is
scaled or moved is scanned like any other region, without the window's popups:

```properties
ui-hint-mode.overlay.area-width-percent=0.33
ui-hint-mode.overlay.area-height-percent=0.33
ui-hint-mode.overlay.area-center=mouse
```

**`ui-order`** is the order the elements are labeled in, the first ones getting the shortest
labels (default `top-to-bottom`): `top-to-bottom`, `bottom-to-top`, `left-to-right`,
`right-to-left` or `mouse`. With `mouse`, the elements nearest the mouse cursor come first, from
where it was when the scan started. With an edge, elements are labeled in rows from it, each
read from left to right (in columns read from top to bottom for `left-to-right` and
`right-to-left`). A row goes on as long as the next element's center is at most 6 pixels
further (at 100% scaling), so with `left-to-right` a taskbar on the left edge, whose icons are a
few pixels apart horizontally, is one column labeled from top to bottom:

```properties
ui-hint-mode.overlay.ui-order=left-to-right
```

`ui-order` only applies to `overlay.type=ui-accessibility` and `overlay.type=ui-vision`, and setting it
on another hint type is rejected.

A scan that finds no element leaves the mode showing nothing. The built-in virtual key
`ishintmeshempty` is pressed then, so a combo can leave the mode:

```properties
ui-hint-mode.to.normal-mode=_{ishintmeshempty}
```

Accessibility UI hints use the same appearance properties as other hint types (`box-*`,
`font-*`, etc.). The `box-horizontal-padding` and `box-vertical-padding` properties are
particularly useful for UI hints since the hint boxes are sized to fit the label text rather than
a fixed grid cell.

The **`ui-element-box-*`** properties draw the box of each element, behind its hint box. They are
the same as the `box-*` properties: `ui-element-box-color`, `ui-element-box-opacity`,
`ui-element-box-border-thickness`, `ui-element-box-border-length`, `ui-element-box-border-color`,
`ui-element-box-border-opacity`, `ui-element-box-border-radius`,
`ui-element-box-horizontal-padding` and `ui-element-box-vertical-padding`. The box is the element,
and its border is inside it, as in a hint box. The paddings grow the box around the element: with
paddings equal to the border thickness, the border goes around the element and does not cover it.
The two opacities are 0 by default, so no element box is drawn:

```properties
ui-hint-mode.overlay.ui-element-box-border-color=#2EA043
ui-hint-mode.overlay.ui-element-box-border-opacity=1
ui-hint-mode.overlay.ui-element-box-border-thickness=2
ui-hint-mode.overlay.ui-element-box-border-radius=3
ui-hint-mode.overlay.ui-element-box-horizontal-padding=2
ui-hint-mode.overlay.ui-element-box-vertical-padding=2
```

`ui-element-box-*` only applies to `overlay.type=ui-accessibility` and `overlay.type=ui-vision`, and
setting it on another hint type is rejected.

### Vision UI hints

Vision UI hints detect elements by their appearance rather than through the accessibility tree:
the screen is captured, the content that stands out from its background is found, and each
group of neighbouring content gets a hint. They therefore also cover games, canvas, remote
desktops and anything else that exposes no accessibility information, at the cost of not
knowing what an element is.

```properties
ui-hint-mode.overlay.type=ui-vision
ui-hint-mode.overlay.area=active-screen
ui-hint-mode.overlay.vision-density=6
```

Vision UI hints read the same **`area`** and **`ui-order`** as accessibility UI hints, and
share every selection, click and appearance property with them. Detection runs off the keyboard
hook, so mousemaster stays responsive while a scan is in progress, and `ishintmeshempty` is
pressed when a scan finds nothing, exactly as for accessibility UI hints.

- **`vision-density`**: How hard the detection looks, 1 to 8 (default 6). A higher value gives
  more hints, on smaller things. It only applies to `overlay.type=ui-vision`, and setting it on
  another hint type is rejected.
  - 1 leaves **plain text** out: it hints only what the ink draws without a gap (an icon, a
    filled button, a checkbox, a picture) or what a border surrounds on every side (a field,
    an outlined button).
  - 2 to 7 look for **bounded regions** — a button, a field, a panel. Each step accepts a
    narrower margin between a region's border and what it encloses, so it finds what the step
    before it was too strict for. A region is parted where its ink changes colour, so a link
    in a sentence, or grey text beside black, gets a hint of its own.
  - 8 adds to 7 the **ink** that no region holds, parted at its gaps.

  A hint means something was drawn there, not that it can be clicked: on a page of prose, most
  of the labels at density 6 land on body text. Lower the density when the labels crowd what
  you are reading, raise it when something clickable goes unhinted.

- **`vision-min-size`**: An element narrower or shorter than this, in pixels at 100% scaling,
  gets no hint (default 0). It leaves out the bars, separators and underlines detected beside
  what they belong to, such as the bar a taskbar draws by the icon of an open app. It also
  leaves out what is detected only by a thin part of it: at 6, a checkbox found by one edge of
  its outline or a three-dot button loses its hint. It only applies to `overlay.type=ui-vision`,
  and setting it on another hint type is rejected.

  ```properties
  ui-hint-mode.overlay.vision-min-size=6
  ```

On macOS the capture asks for the Screen Recording permission the first time a scan runs;
hints appear once it is granted.

### Overlay area and layout

The following properties control where hints appear and how they're arranged:

```properties
# Hint area configuration
hint-mode.overlay.area=active-screen
hint-mode.overlay.area-center=screen-center

# Grid layout configuration
hint-mode.overlay.grid-cell-width=74
hint-mode.overlay.grid-cell-height=36
hint-mode.overlay.grid-cell-sizing=fixed
hint-mode.overlay.layout-row-count=6
hint-mode.overlay.layout-column-count=5
```

The hint area is two independent settings: **`area`** sets its *size*, **`area-center`** sets
the point it is *centered* on. For UI hints it is the region the elements are looked for in.

- **`area`**:
  - `active-screen`: The screen with the mouse cursor
  - `active-window`: The currently active window
  - `all-screens`: Every connected screen (one grid per screen — `area-center` does not apply)
  - `last-selected-hint-cell`: The cell of the last selected hint (for a recursive grid), the
    element of a UI hint
  - `last-overlay-area`: The area of the overlay of the mode before, as it was when this mode
    started: the area the grid commands left, or, when keys of a grid are selected, the cells
    they leave. E.g. shrink a grid, then show a grid with labels in what is left of it:
    ```properties
    screen-grid-mode.to.hint-mode=+h
    hint-mode.overlay.area=last-overlay-area
    hint-mode.overlay.selection-keys=hintkey
    ```

  `active-screen` is the default. What each region scans for UI hints is described in
  [Accessibility UI hints](#accessibility-ui-hints).

- **`area-width-percent`** / **`area-height-percent`**: Scale the area to a
  fraction of that region, keeping it centered on `area-center` (default `1.0`).
  E.g. `area=active-screen` + `area-width-percent=0.5` covers the middle 50%
  of the screen's width. Works with both `grid-cell-sizing=fit` and `=fixed`.

- **`area-top-inset`** / **`area-bottom-inset`** / **`area-left-inset`** /
  **`area-right-inset`**: Margins cut from the scaled area, in pixels at 100% scaling (default
  `0`). E.g. `area=active-window` + `area-top-inset=15` leaves out the window's title bar. A
  negative inset grows the area.

- **`area-center`** (point the area is centered on):
  - `screen-center`: Center of the screen
  - `mouse`: Current mouse position
  - `last-selected-hint`: Position of the last selected hint
  - `active-window-center`: Center of the active window
  - `last-overlay-area-center`: Center of the area `last-overlay-area` names

  When omitted, the center defaults per size: `active-screen`/`all-screens` →
  `screen-center`, `active-window` → `active-window-center`, `last-selected-hint-cell` →
  `last-selected-hint` (grid stays over the cell), `last-overlay-area` →
  `last-overlay-area-center`. The size and center compose freely — e.g. an
  `active-window`-sized grid with `area-center=mouse` is a window-sized grid centered on the
  cursor.

- **Grid dimensions**: Control the size of each hint cell:
  - `grid-cell-width`: Width of each hint cell in pixels
  - `grid-cell-height`: Height of each hint cell in pixels

  The sizes are unzoomed pixels, so the zoom scales them: under a `zoom.percent` of 5, a
  cell of width 10 is drawn 50 pixels wide and still covers 10 pixels of the magnified
  content. A grid therefore keeps its shape, and its hint keys, whatever the zoom.

- **`grid-cell-sizing`**: How cell size is determined:
  - `fixed` (default): the cell count is the area divided by `grid-cell-width` x `grid-cell-height`, capped by `grid-max-row-count`/`grid-max-column-count`. The cells are then stretched to fill the area, so the pixel sizes pick the count rather than the size drawn.
  - `fit`: cells are sized to fill the area with exactly `grid-max-row-count` x `grid-max-column-count` cells (the pixel sizes are ignored). Useful for a fixed grid shape (e.g. 3x3) that adapts to any screen, and for a recursive grid via `area=last-selected-hint-cell`.

- **Subgrid layout**: `layout-row-count` x `layout-column-count` is the subgrid, the block of cells
  that shares a hint prefix. The grid is tiled with subgrids; a hint's subgrid prefix says which
  subgrid it is in and its cell keys where it sits inside that subgrid. They do not change the grid's
  own row and column counts, which come from `grid-cell-sizing`.
  - `layout-row-count=6` and `layout-column-count=5`: 6x5 subgrids, as the shipped configurations use
  - `layout-row-count=1` and a column count wider than the grid: one prefix per row
  - `layout-column-count=1` and a row count taller than the grid: one prefix per column, the default
  - `layout-row-oriented=false` (default `true`) numbers the subgrids down columns instead of across rows

  A grid with no more cells than there are selection keys gives every hint a single key and no prefix.

### Screen-specific overlay configurations

Any hint property can hold a different value per screen: give the screen as a precondition of a [combo property](#mode-property-mutation).

```properties
# A 4K screen gets denser hints than the others:
hint1-mode.overlay.grid-cell-width=74 | _{3840x2160-100%} -> 96
hint1-mode.overlay.grid-cell-height=41 | _{3840x2160-100%} -> 54
hint1-mode.overlay.layout-row-count=6 | _{3840x2160-100%} -> 4
hint1-mode.overlay.layout-column-count=5 | _{3840x2160-100%} -> 10
```

A screen filter is a screen resolution (`3840x2160`), a screen scale (`150%`), or both (`3840x2160-150%`). Each screen resolves its own value when the hints are drawn, so a mesh covering several screens gets a value per screen. The most specific matching filter wins: resolution and scale first, then resolution, then scale. A screen matching none of them takes the default value.

A length — a padding, a border thickness, radius or length, a shadow blur or offset — is in the pixels of a 100% screen, and the screen's scale grows it, exactly as it grows a font size. A hint therefore keeps its proportions on a 200% or 300% screen with no per-screen value at all. To pin a length to a pixel count on one scale, override it there and divide: `hint1-mode.overlay.box-border-thickness=1 | _{150%} -> 0.67` draws a single pixel at 150%.

```properties
# Every screen scaled at 300%, whatever its resolution.
hint1-mode.overlay.box-border-radius=1 | _{300%} -> 3

# Mixed with a key precondition: on 300% screens only, and only while
# leftshift is pressed.
hint1-mode.overlay.box-border-radius=1 | _{300% leftshift} -> 3

# Either of two screens. A space means AND, as it does between keys, so two
# filters have to be alternatives: no screen is both at once.
hint1-mode.overlay.box-border-radius=1 | _{3840x2160-300% | 1366x768-100%} -> 3

# Alongside a key, one filter per branch: a | in that block would read as an
# alternative to the key, which no screen can satisfy.
hint1-mode.overlay.box-border-radius=1 | _{leftshift 3840x2160-300%} -> 3 \
    | _{leftshift 1366x768-100%} -> 3
```

Only a hint property is filtered per screen. Everywhere else — another property, a command,
a mode switch — a screen filter is a [virtual key](combo-reference.md#built-in-virtual-keys)
that mousemaster presses while the active screen matches it, so it reads like any other key:

```properties
# A slower jump on a 300% screen.
normal-mode.mouse.smooth-jump-velocity=30000 | _{300%} -> 20000

# ^{} negates it, and it can share a block with a real key.
normal-mode.indicator.size=12 | ^{300%} -> 8 | _{300% leftshift} -> 24

# In a combo, like any other key.
normal-mode.press.left=_{3840x2160-300%} +leftbutton
```

A set of screens can be named once with `screen-alias`, which behaves like a key alias: it
stands for any one of the filters it lists, so `_{largescreen}` is a named `*`-list. A screen
is then written down in one place instead of once per property:

```properties
screen-alias.largescreen=3840x2160-100% 3440x1440-100%
screen-alias.mediumscreen=3840x2160-150% 2560x1440-100%
screen-alias.smallscreen=1920x1080-125% 1366x768-100%
# An alias can name another one, like a key alias can.
screen-alias.tunedscreen=largescreen mediumscreen smallscreen

hint1-mode.overlay.grid-cell-sizing=fit
hint1-mode.overlay.grid-max-column-count=25 | _{mediumscreen | largescreen} -> 40
hint1-mode.overlay.grid-max-row-count=30 | _{smallscreen} -> 20 | _{mediumscreen | largescreen} -> 40
hint1-mode.overlay.selection-keys=hintkey | _{mediumscreen | largescreen} -> extendedhintkey
```

Aliases that split the screens into sets let each property name a set instead of a screen, so
adding a screen to a set is all it takes to tune it.

Note that a filter with no scale matches that resolution at *every* scale, so
`3840x2160` also covers `3840x2160-150%`. Spelling the scale out on every filter keeps each
one exact, and lets an unlisted scale fall back to the default value.

### Hint selection and behavior

```properties
# Selection configuration
key-alias.selectionkey=a b c d e f g h i j k l m n o p q r s t u v w x y z
hint-mode.overlay.selection-keys=selectionkey
hint-mode.overlay.select=+selectionkey
hint-mode.overlay.deselect=backspace
hint-mode.to.normal-mode=+selectionkey
hint-mode.overlay.mouse-follows-target=true
hint-mode.overlay.eat-unused-selection-keys=true
hint-mode.overlay.label-override=plus
```

- **`selection-keys`**: Keys that can be pressed to select hints. Without selection keys (the
  default), the hints have no labels and cannot be selected, and `select` and `deselect` are
  rejected. `none` gives no selection keys, for example in a mutation that shows the labels only
  while a key is pressed: `grid-mode.overlay.selection-keys=none | _{labels} -> hintkey`.
  `select` then works with the keys of every branch.
- **`select`**: Combo that triggers hint selection. For example, if you want to be able to hold leftshift while pressing a hint selection key, then use `hint-mode.overlay.select=_{none | leftshift} +selectionkey`
- **`deselect`**: Combo that deselects the last selection key
- **`to.<mode-name>`**: Use this syntax to specify which mode to switch to after a hint is selected (e.g., `hint-mode.to.normal-mode=+selectionkey`)
- **`mouse-follows-target`**: Whether the mouse follows the target (default `true`). The
  target is a position that follows the mouse. Selecting a hint and the grid commands move it
  to the center of the hint, the edge a `snap-target` goes to or the center of the grid, and
  the mouse goes there. When it becomes `false`, the target stops where it is: only those
  commands move it, and the mouse stays. Use `false` when the next mode will be another hint
  grid.
- **`target-follows-overlay-center`**: Whether the target goes to the center of the overlay
  when the overlay shows, and when a selection key or `deselect` changes its hints (default
  `false`). The mouse goes there too, unless `mouse-follows-target=false`.
- **`move-to.last-selected-hint`**: Like `to.<mode-name>`, a command of the mode, not an overlay
  property. It moves the mouse to the last selected hint, for example after
  `mouse-follows-target=false` left the mouse where it was:
  `normal-mode.move-to.last-selected-hint=+m`
- **`move-to.target`**: The same, to the target: `grid-mode.move-to.target=+space`
- **`eat-unused-selection-keys`**: If `false`, selection keys not used in the current hint display can be used in combos
- **`label-override`**: Draws a fixed marker in every hint cell instead of each hint's own keys — visual only, you still press the real keys to select. The value is a key sequence rendered as the label: `plus` shows `+`, `plus plus` shows `++`. Useful for positional grids (e.g. a 3×3 recursive grid) where the per-cell letters are just clutter. Default: unset (each hint shows its own key sequence).

### Hint appearance

```properties
# Box appearance
# One color, or several for a gradient through them.
hint-mode.overlay.box-color=#000000
hint-mode.overlay.box-opacity=0.4
hint-mode.overlay.box-border-thickness=1
hint-mode.overlay.box-border-length=1000
hint-mode.overlay.box-border-color=#FFFFFF
hint-mode.overlay.box-border-opacity=0.4
hint-mode.overlay.box-border-radius=0
# false draws the lines between the boxes only, no frame around the grid
hint-mode.overlay.box-framed=true

# Box shadow, mostly useful for UI hints and position history hints
hint-mode.overlay.box-shadow-blur-radius=10
hint-mode.overlay.box-shadow-color=#000000
hint-mode.overlay.box-shadow-opacity=0
hint-mode.overlay.box-shadow-stack-count=1
hint-mode.overlay.box-shadow-horizontal-offset=2
hint-mode.overlay.box-shadow-vertical-offset=2

# Box size relative to the grid cell (for the hint grid only)
hint-mode.overlay.box-width-percent=1.0
hint-mode.overlay.box-height-percent=1.0

# Decorations: purely visual grids drawn over the hints. subdecoration is drawn inside each
# hint cell (a preview of the next level), subsubdecoration inside each of those, and
# decoration spans the whole hint grid as one big cell.
# Set subdecoration-label-keys to label the cells.
hint-mode.overlay.subdecoration-max-row-count=1
hint-mode.overlay.subdecoration-max-column-count=1
hint-mode.overlay.subdecoration-label-keys=
hint-mode.overlay.subdecoration-box-border-thickness=1
hint-mode.overlay.subdecoration-box-border-length=10000
hint-mode.overlay.subdecoration-box-border-color=#FFFFFF
hint-mode.overlay.subdecoration-box-border-opacity=1.0
hint-mode.overlay.subdecoration-box-framed=false
hint-mode.overlay.subdecoration-font-size=10
hint-mode.overlay.subdecoration-font-color=#FFFFFF
hint-mode.overlay.subdecoration-font-opacity=1.0

# Box padding, mostly useful for UI hints and position history hints
hint-mode.overlay.box-horizontal-padding=0
hint-mode.overlay.box-vertical-padding=0

# Font appearance
hint-mode.overlay.font-name=Consolas
hint-mode.overlay.font-size=18
hint-mode.overlay.font-spacing-percent=0.7
hint-mode.overlay.font-color=#FFFFFF
hint-mode.overlay.font-opacity=1.0

# Font effects
hint-mode.overlay.font-outline-thickness=0
hint-mode.overlay.font-outline-color=#000000
hint-mode.overlay.font-outline-opacity=0.5
hint-mode.overlay.font-shadow-blur-radius=0
hint-mode.overlay.font-shadow-color=#000000
hint-mode.overlay.font-shadow-opacity=1.0
hint-mode.overlay.font-shadow-horizontal-offset=0
hint-mode.overlay.font-shadow-vertical-offset=0

# Selected hint appearance
hint-mode.overlay.selected-font-color=#A3A3A3
hint-mode.overlay.selected-font-opacity=1.0

# Prefix box (for multi-character hints). A border is never drawn where an enclosing one
# already draws, so the hint boxes leave the prefix box its own.
hint-mode.overlay.prefix-box-enabled=true
hint-mode.overlay.prefix-box-border-thickness=2
hint-mode.overlay.prefix-box-border-color=#FFD93D
hint-mode.overlay.prefix-box-border-opacity=0.8
# false draws the lines between the prefix boxes only, no frame around the grid
hint-mode.overlay.prefix-box-framed=true
hint-mode.overlay.prefix-in-background=true

# Animation
hint-mode.overlay.transition-animation-enabled=true
hint-mode.overlay.transition-animation-duration-millis=100

# Fade animation (when hints appear or disappear)
hint-mode.overlay.fade-animation-enabled=true
hint-mode.overlay.fade-animation-duration-millis=100

# Background, mostly useful for UI hints and position history hints
hint-mode.overlay.background-color=#000000
hint-mode.overlay.background-opacity=0
```

- Box appearance: controls the background and border of hint boxes
  - `box-color`: One color, or several for a gradient through them, evenly spaced and interpolated
    through OkLab. Each box takes the color at its own position, so with `top-to-bottom` a grid row
    is one color. `box-opacity` applies to the whole gradient.
    ```properties
    hint-mode.overlay.box-color=#FF0000 #FFA500 #FFFF00 #00FF00 #800080
    hint-mode.overlay.box-color=across-screen left-to-right #FF6B00 #FF006E
    ```
    Keywords go anywhere among the colors, in any order:
    - Direction: `top-to-bottom` (default), `bottom-to-top`, `left-to-right`, `right-to-left`, the
      four diagonals (`top-left-to-bottom-right` and so on), and four round ones: `center-to-corner`
      and `corner-to-center` reach the last color at the corners, `center-to-edge` and
      `edge-to-center` at every edge.
    - What one sweep covers, narrowest first: `across-hint` (each box), `across-subgrid` (each
      [subgrid](#overlay-area-and-layout), so the gradient repeats), `across-all-hints` (the
      hints on that screen), `across-area` (default, the region `area` names, so
      the sweep spans it even where no hint sits), `across-screen`, `across-all-screens` (one sweep
      over the whole arrangement).
    - What shares one color: `per-hint` (default), `per-subgrid` (one color per subgrid),
      `per-pixel` (none: the gradient ramps inside a box and continues into the next).
      `per-subgrid` needs an area wider than a subgrid, so not with `across-subgrid` or
      `across-hint`.
    - A grid without subgrids, such as one without `selection-keys`, is one subgrid: with
      `per-subgrid`, all its boxes share one color.
  - `box-border-color`: One color, or a gradient as for `box-color`. So are
    `prefix-box-border-color`, `ui-element-box-color`, `ui-element-box-border-color` and
    `<level>-box-border-color`.
    ```properties
    hint-mode.overlay.box-border-color=across-screen per-pixel center-to-edge #F97316 #3B82F6
    ```
  - `saved-box-color`, `saved-box-opacity`, `saved-box-border-color`, `saved-box-border-opacity` and
    the same four `saved-ui-element-box-` properties: the box of a hint at a saved position (see
    [Saved hints](#saved-hints)).
  - `box-border-length`: Higher values create continuous lines between hint boxes, lower values create dotted separators
  - `box-border-radius`: Radius for rounded corners (0-1000, default 0).
  - `box-shadow-blur-radius`: Shadow blur radius (0-1000, default 10).
  - `box-shadow-color`: Shadow color (hex, default #000000).
  - `box-shadow-opacity`: Shadow opacity (0-1, default 0 = disabled).
  - `box-shadow-stack-count`: Number of times the shadow is composited on itself for stronger effect (1-100, default 1).
  - `box-shadow-horizontal-offset`: Horizontal shadow offset (-100 to 100, default 2).
  - `box-shadow-vertical-offset`: Vertical shadow offset (-100 to 100, default 2).
  - `background-color`: Background color behind all hint boxes, mostly useful for UI hints (hex, default #000000).
  - `background-opacity`: Background opacity, mostly useful for UI hints (0-1, default 0 = no background).
  - `background-border-radius`: Radius of the background's rounded corners (0-1000, default 8, matching
    Windows' own window corners).

- Decorations: purely visual grids drawn over the hints. Three levels share one set of
  properties, prefixed by how deep they sit: `decoration-` spans the whole hint grid as one
  big cell; `subdecoration-` is drawn inside each hint cell (a preview of the next level);
  `subsubdecoration-` is drawn inside each subdecoration cell. All levels can be used at the same time.
  - `<level>-max-row-count` / `<level>-max-column-count`: How many rows/columns to divide the area into (default 1 = disabled).
  - `<level>-label-keys`: Keys whose hint-style labels are drawn in the cells (accepts a key-alias; empty = lines only, no labels). Labels are generated like the main grid, so they are 1- or 2-char depending on cell count vs. key count. (These are previews, not a selection.)
  - `<level>-label-override`: A fixed label drawn in every cell (e.g. `plus` for a `+`), overriding label-keys.
  - `<level>-font-size` / `<level>-font-color` / `<level>-font-opacity` / `<level>-font-spacing-percent` (and `-font-name`, `-font-weight`, `-font-outline-*`, `-font-shadow-*`): Font of the cell labels.
  - `<level>-box-color` / `<level>-box-opacity`: Cell fill.
  - `<level>-box-border-thickness` / `<level>-box-border-color` / `<level>-box-border-opacity` / `<level>-box-border-radius`: The lines. (Names mirror the top-level `box-border-*`.)
  - `<level>-box-border-length`: Length of each line; high values (default 10000) draw continuous lines, low values draw short marks (e.g. a `+` at the center of a 2x2 decoration).
  - `<level>-box-framed`: Whether this level draws its own frame (default false). A decoration spans its parent box exactly, so its frame would land on the parent's border and double it; false draws the interior lines only. Same property as the top-level `box-framed` and `prefix-box-framed`, which default to true since nothing above them draws that frame.
  - `<level>-box-horizontal-padding` / `<level>-box-vertical-padding`: Grow each box around its cell, as `box-horizontal-padding` grows a hint box (default 0). With `box-framed=false`, a framed `decoration-` with paddings equal to its border thickness draws its frame around the grid, where a UI hint's element box draws:
    ```properties
    refine-ui-hint-mode.overlay.box-framed=false
    refine-ui-hint-mode.overlay.decoration-box-framed=true
    refine-ui-hint-mode.overlay.decoration-box-border-radius=1
    refine-ui-hint-mode.overlay.decoration-box-horizontal-padding=1
    refine-ui-hint-mode.overlay.decoration-box-vertical-padding=1
    ```

- Font appearance: controls how hint labels appear
    - `font-spacing-percent`: Controls character spacing (0=touching, 1=evenly distributed, 0.5=minimal spacing with alignment)

- Animation:
    - `transition-animation-enabled` / `transition-animation-duration-millis`: animates hint boxes sliding to their new positions when the hint set changes (e.g. after typing a key in a multi-level hint mode). Default enabled, 100ms.
    - `fade-animation-enabled` / `fade-animation-duration-millis`: fades hints in when they first appear and out when they disappear. Default enabled, 100ms. Suppressed when the surrounding mode change also changes zoom.

### Hint prefix

A hint is made of several keys (letters). For example, hint JKK is made of 3 keys: J, K and K.
- The hint prefix is JK.
- The prefix (JK) can be displayed "in the background" with `prefix-in-background=true`.

- Hints in the same subgrid have the same prefix (see
  [Subgrid layout](#overlay-area-and-layout)), so a subgrid one row tall gives one prefix per row
  and one column wide one prefix per column.

Initially:
- None of the hint keys are selected.
- The hint key J is focused.

Then, when typing J on the keyboard:
- J becomes selected.
- The first K becomes focused.
- The last K is neither focused nor selected.

Then, when typing K:
- J and the first K are selected.
- The second K is focused.

Each hint key can be in one of three states: default, selected, or focused.
Font styles are configured per state and per category (non-prefix and prefix).

The `font-*` properties define the default state. The `selected-font-*` and `focused-font-*` properties override specific values for selected and focused keys. Unset selected/focused properties inherit from the default state. The `prefix-` variants follow the same pattern, with unset prefix properties inheriting from the corresponding non-prefix properties.

The `saved-font-*` properties replace the default and focused states in the keys of a saved hint
(see [Saved hints](#saved-hints)). Unset saved properties inherit from the default state.

Each state supports the following font properties:
- `font-name`
- `font-weight`
- `font-size`
- `font-color`
- `font-opacity`
- `font-outline-thickness`
- `font-outline-color`
- `font-outline-opacity`
- `font-shadow-blur-radius`
- `font-shadow-color`
- `font-shadow-opacity`
- `font-shadow-stack-count`
- `font-shadow-horizontal-offset`
- `font-shadow-vertical-offset`

`font-spacing-percent` and `font-vertical-alignment` are the exceptions: there is one of each per
category (`font-` and `prefix-font-`), shared across all 3 states.

The property naming pattern is:
- Default: `overlay.font-*`
- Selected: `overlay.selected-font-*`
- Focused: `overlay.focused-font-*`
- Prefix default: `overlay.prefix-font-*`
- Prefix selected: `overlay.prefix-selected-font-*`
- Prefix focused: `overlay.prefix-focused-font-*`
- Saved: `overlay.saved-font-*`

### Multi-level hint example

This example shows how to create a two-level hint system, where the first level selects a general area, and the second level provides more precise targeting:

```properties
key-alias.selectionkey=a b c d e f g h i j k l m n o p q r s t u v w x y z
# First level hint mode
hint1-mode.overlay.area=active-screen
hint1-mode.overlay.selection-keys=selectionkey
hint1-mode.to.hint2-mode=+selectionkey
# Don't move mouse after first selection
hint1-mode.overlay.mouse-follows-target=false

# Second level hint mode
hint2-mode.overlay.area=active-screen
# Center on last selected hint
hint2-mode.overlay.area-center=last-selected-hint
# 3x8 grid
hint2-mode.overlay.grid-max-row-count=3
hint2-mode.overlay.grid-max-column-count=8
# Smaller cells for precision
hint2-mode.overlay.grid-cell-width=30
hint2-mode.overlay.grid-cell-height=20
hint2-mode.overlay.font-size=7
hint2-mode.overlay.selection-keys=selectionkey
hint2-mode.to.normal-mode=+selectionkey
# Move mouse after second selection
hint2-mode.overlay.mouse-follows-target=true
```

### Grid commands

The grid commands act on the grid of the overlay. An overlay without `selection-keys` has no
labels and selects nothing: it is a grid to shrink, move and snap to.

```properties
normal-mode.to.grid-mode=+g
grid-mode.to.normal-mode=+esc
grid-mode.overlay.grid-cell-sizing=fit
grid-mode.overlay.grid-max-row-count=2
grid-mode.overlay.grid-max-column-count=2
grid-mode.overlay.target-follows-overlay-center=true
grid-mode.overlay.box-color=#FF0000
grid-mode.overlay.box-opacity=0.1
grid-mode.overlay.box-border-color=#FF0000
grid-mode.overlay.box-border-opacity=1

# Halve the grid, keeping the half in the direction
grid-mode.shrink-grid.up=+uparrow
grid-mode.shrink-grid.down=+downarrow
grid-mode.shrink-grid.left=+leftarrow
grid-mode.shrink-grid.right=+rightarrow

# Move the grid by its own size
grid-mode.move-grid.up=_{leftshift} +uparrow
grid-mode.move-grid.down=_{leftshift} +downarrow
grid-mode.move-grid.left=_{leftshift} +leftarrow
grid-mode.move-grid.right=_{leftshift} +rightarrow

# Snap the target to the next cell edge
grid-mode.snap-target.up=_{leftctrl} +uparrow
grid-mode.snap-target.down=_{leftctrl} +downarrow
grid-mode.snap-target.left=_{leftctrl} +leftarrow
grid-mode.snap-target.right=_{leftctrl} +rightarrow

# Move the target to the grid center
grid-mode.move-target-to.grid-center=+space
```

- **`shrink-grid.<direction>`**: Halves the grid, keeping the half on that side, whatever its
  number of cells.
- **`move-grid.<direction>`**: Moves the grid by its own width or height. The center of the grid
  stays on a screen.
- **`snap-target.<direction>`**: Moves the target to the next cell edge in the direction. At
  the right or bottom edge of the grid, it stops on the last pixel of the grid.
- **`move-target-to.grid-center`**: Moves the target to the center of the grid.

A command name says what it moves: `snap-target` and `move-target-to` move the target,
`move-grid` and `shrink-grid` the grid. A name that says nothing, such as `start-move` or
`move-to`, moves the mouse. `<verb>-to.<point>` jumps to a point, and `start-move` and
`stop-move` are the only continuous movement.

A mode with the same `overlay.type`, area and layout keeps the grid where the commands left it. With
`area-center=mouse`, a command first centers the grid on the mouse. With
`target-follows-overlay-center=true`, the target goes to the center of the grid after each
change. The mouse follows the target, unless `mouse-follows-target=false`: then it stays
where it is.

## App aliases

App aliases allow you to group applications together and create mode switches based on which application is currently active. This is useful for creating app-specific behaviors or disabling mousemaster for certain applications.

### Defining app aliases

```properties
app-alias.browserapp=firefox.exe chrome.exe edge.exe

# An executable name containing a space, as macOS ones often do, goes in double quotes:
app-alias.settingsapp="System Settings" "Activity Monitor"
```

App aliases work similarly to key aliases, but instead of grouping keys, they group application executable names.

### Using app aliases in combos

App aliases can be used in combo preconditions to create app-specific behavior:

```properties
# Switch to browser-mode when a browser becomes active
idle-mode.to.browser-mode=_{browserapp}
normal-mode.to.browser-mode=_{browserapp}

# Switch back to idle-mode when no browser is active
browser-mode.to.idle-mode=^{browserapp}
```

In this example:
- `_{browserapp}` is a precondition that is satisfied when any of the specified browsers is the active application
- `^{browserapp}` is a precondition that is satisfied when none of the specified browsers is the active application

## Position history

Position history allows you to save positions of the target (see `mouse-follows-target`) and quickly return to them later. Unless the mode has `mouse-follows-target=false`, the target follows the mouse, so these are mouse positions. This feature can be used for frequently accessed UI elements or for creating custom navigation patterns.

### Basic configuration

```properties
# Set the maximum number of positions to remember
position-history.max-size=16

# Define a key to save the position of the target
normal-mode.position-history.save-position=+f1

# Define a key to clear all saved positions
normal-mode.position-history.clear=+f2
```

- **`max-size`**: Maximum number of positions that can be stored, 1-100 (older positions are removed when this limit is reached). Default 16.
- **`save-position`**: Command to save the position of the target to history
- **`unsave-position`**: Command to remove the saved position the target is on
- **`clear`**: Command to clear all saved positions
- **`cycle-next`** / **`cycle-previous`**: Commands to move the target to the next or previous saved position, continuing from the one it is on. The mouse follows, unless the mode has `mouse-follows-target=false`

While a history holds a position, it reads as a pressed key named without its hyphens, like an
animation: `_{dragpositionhistory}` matches while `drag-position-history` holds a position, and
`^{dragpositionhistory}` while it is empty. With `isolation=active-app`, it is the list of the
active app.

### Several position histories

Position history names end with `-position-history`, the same way mode names end with
`-mode`. Every command and every hint mode names the history it acts on, and
`position-history` alone is the default one:

```properties
# Two isolated histories, with their own sizes
browser-position-history.max-size=4
normal-mode.position-history.save-position=+f1
normal-mode.browser-position-history.save-position=_{browserapp} +f2
normal-mode.browser-position-history.clear=+f3
```

A history exists as soon as a mode defines a command for it. Positions saved in one history are invisible to the others, and each history has its own cycling position.

### Isolating a position history

A history can keep a separate list of positions for each app, so that saving a position in
one app never shows it in another:

```properties
browser-position-history.isolation=active-app
browser-position-history.max-size=4
```

- **`isolation`**: What the history keeps a separate list of positions for. `active-app`
  isolates by the active app, identified by its executable name. `none` (default): one list
  shared by every app.

Every command and the hints act on the list of the app that is active when they run, so
`max-size` applies to each app's list, and `clear` only clears the active app's list. Unlike
naming a history per app, this needs no knowledge of which apps exist.

### Using position history with hints

Position history works in conjunction with a hint mode whose type is the name of the history:

```properties
# Configure a hint mode to use position history
position-hint-mode.overlay.type=position-history
position-hint-mode.overlay.selection-keys=a b c d e f g h i j k l m n o p q r s t u v w x y z

# Switch to position hint mode
normal-mode.to.position-hint-mode=+p

# A hint mode for the browser history
browser-hint-mode.overlay.type=browser-position-history
```

Because `overlay.type` can be combo-triggered, one hint mode can show different histories:

```properties
position-hint-mode.overlay.type=position-history | _{browserapp} -> browser-position-history
```

When you enter a position history hint mode:
1. One hint box will be displayed for each saved position
2. Selecting a hint moves the mouse to that saved position
3. If no positions are saved, the target is saved first, so its hint is displayed

### Saved hints

An overlay can show which of its hints are at a saved position. `saved-position-history` names the
history, and the `saved-` properties style those hints:

```properties
ui-hint-mode.overlay.saved-position-history=drag-position-history
ui-hint-mode.overlay.saved-box-color=#00FF00
ui-hint-mode.overlay.saved-box-opacity=0.5
ui-hint-mode.overlay.saved-ui-element-box-border-color=#00FF00
ui-hint-mode.overlay.saved-font-color=#000000
```

- A hint is saved when a saved position is in its element (UI hints) or its cell (grids), or on its
  center when it has neither.
- `saved-box-color`, `saved-box-opacity`, `saved-box-border-color`, `saved-box-border-opacity`,
  `saved-ui-element-box-color`, `saved-ui-element-box-opacity`, `saved-ui-element-box-border-color`
  and `saved-ui-element-box-border-opacity` replace the values of the box for a saved hint. A value
  that is not set is the value of the box, mutations included.
- The `saved-font-*` properties replace every font state in the keys of a saved hint: default,
  selected and focused.
- A saved hint stays shown when the selected keys hide the other hints.
- A save, an unsave or a clear of that history shows on the overlay at once.

### Example workflow

```properties
# Save positions in normal mode
normal-mode.position-history.save-position=+f1

# Access saved positions through hint mode
normal-mode.to.position-hint-mode=+p

# Clear all saved positions if needed
normal-mode.position-history.clear=+f2
```

Typical usage:
1. Navigate to important locations and press F1 at each one to save them
2. Press P to enter position hint mode
3. Select a hint to instantly move to that saved position
4. Press F2 if you want to clear all saved positions and start over

This feature is especially useful for:
- Frequently accessed UI elements in complex applications
- Navigating between multiple work areas
- Creating custom navigation patterns for repeated tasks

## Macros

Macros allow you to define sequences of key presses and releases that are triggered by a
combo. Macros can send keys to the operating system (for controlling other applications)
or to mousemaster itself (for triggering mousemaster commands).

### Basic macro syntax

A macro is defined with:
```properties
mode-name.macro.macro-name=combo -> output
```

Where:
- `combo` is the trigger combo (same syntax as other combos)
- `output` is the sequence of key moves to execute

### Macro output syntax

The output uses the following syntax:

|          | Meaning                                                                                          | Example     |
|----------|--------------------------------------------------------------------------------------------------|-------------|
| `+key`   | Press key (sent to OS)                                                                           | `+leftctrl` |
| `-key`   | Release key (sent to OS)                                                                         | `-leftctrl` |
| `'text'` | Type the string character by character, independently of the active keyboard layout (sent to OS) | `'hello'`   |
| `#key`   | Press key (sent to mousemaster)                                                                  | `#rightalt` |
| `~key`   | Release key (sent to mousemaster)                                                                | `~rightalt` |
| `wait-N` | Wait N milliseconds before continuing                                                            | `wait-50`   |
| `key`    | Shorthand for `#key ~key`                                                                        | `rightalt`  |

### OS-bound vs internal keys

- OS-bound keys (`+`/`-`): These keys are sent to the operating system.
  Other applications will receive these key events. Use this for controlling other
  applications, typing text, or triggering system shortcuts.

- Internal keys (`#`/`~` or unprefixed): These keys are fed back into mousemaster's
  combo system. They can trigger other mousemaster commands (including mode switches). Use this for
  automating mousemaster itself.

### Virtual keys

Sometimes a macro needs a key purely as an internal signal — e.g. to bracket a region
of the macro and gate a mode property on it — without any risk of colliding with a real
key on the keyboard. Declare such keys on their own line, stating whether the key starts
pressed or released:

```properties
virtual-key.drilling=released
```

A virtual key can never be produced by hardware, is never sent to the
OS, and is only ever pressed/released by a macro via `#`/`~`. It can be used anywhere a
real key can in the combo system — in a macro's `#`/`~` moves, and in `_{}`/`^{}`
preconditions. It has no OS side, so `+`/`-` on it mean the same thing as `#`/`~`.

Declaring it (rather than relying on a name that happens not to exist) keeps typo
detection: an undeclared unknown key name is still rejected at load time.

```properties
# "drilling" is held only for the span of the macro; gate a property on it.
virtual-key.drilling=released
some-mode.macro.example=+g -> #drilling k k ~drilling
some-mode.overlay.visible=true | _{drilling} -> false
```

A macro can also leave a virtual key pressed past its own end, making it persistent state that
combos and property values can be gated on. See
[virtual keys](combo-reference.md#virtual-keys) for reading them in preconditions and for
driving property mutation.

### Examples

#### Simple key remapping

Remap V to Ctrl+V in normal mode:
```properties
normal-mode.macro.paste=+v -> +leftctrl +v -v -leftctrl
```

#### Open an app

Open the Windows notepad when pressing Win+F1:
```properties
idle-mode.macro.notepad=_{leftwin} +f1 -> +leftwin +r -r -leftwin wait-100 'notepad' +enter -enter
```

#### Automating mousemaster

Select the second screen when pressing F12:
```properties
# In the neo-mousekeys-ijkl configuration, pressing c shows the screen selection hints,
# then pressing k will select the second screen (because the hint keys are j k l ; a s d f g).
normal-mode.macro.selectsecondscreen=+f12 -> c k
# Equivalent to:
#normal-mode.macro.selectsecondscreen=+f12 -> #c ~c #k ~k
```

## Console window

mousemaster runs with a console (command line) window by default. You can hide this window for a cleaner experience.

```properties
# Hide the console window
hide-console=true
```

### Important considerations

- When the console window is hidden, you won't see any error messages or logs
- If the console is hidden, you can stop mousemaster by:
  - Pressing a combo bound to the `exit` command, for example `normal-mode.exit=+leftctrl +leftalt +q`
  - Using the Task Manager to end the mousemaster.exe process
  - Using Win+R and typing: `taskkill /F /IM mousemaster.exe`
- Pressing a combo bound to the `restart` command restarts mousemaster with the same arguments, for example `normal-mode.restart=+leftctrl +leftalt +r`

## Logging

mousemaster provides configurable logging to help with troubleshooting and debugging.

```properties
# Logging configuration
logging.level=INFO
logging.key-redaction=pseudonymize
logging.to-file=true
log-last-key-events-on-exit=true
```

### Logging options

- **`logging.level`**: Controls the verbosity of logging
  - Valid values: `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`
  - Higher levels (DEBUG, TRACE) provide more detailed information but may impact performance

- **`logging.key-redaction`**: How a key that types a character is logged
  - `none` (default): as itself, for troubleshooting keyboard issues
  - `pseudonymize`: as `key#1`, a name that is stable for this run only, so a press and its
    release get the same name and a log still shows a key that was never released
  - `anonymize`: as `<redacted>`, the same name for every key, so keys cannot be told apart
  - Keys that type no character (modifiers, arrows, function keys) are always logged as themselves

- **`logging.to-file`**: When set to `true`, logs are written to `mousemaster.log` in the working
  directory, in addition to the console
  - Useful when `hide-console=true` or for persistent logging

- **`log-last-key-events-on-exit`**: When set to `true`, the last key events are logged when mousemaster exits
  - They are always logged when a stuck key is detected
  - The format is combo syntax, with the milliseconds between events: `[+a-377 -a-32 +leftwin]`
  - Useful for reporting a combo that stopped working, without leaving `logging.level=TRACE` on

## Keyboard layout

mousemaster reads the active keyboard layout from the foreground window and follows it as it changes.
`forced-active-keyboard-layout` overrides that detection, for a layout mousemaster gets wrong:
```properties
forced-active-keyboard-layout=us-dvorak
```
The following keyboard layouts are currently exposed and can be referenced in the configuration file:
- zh-qwerty-pinyin
- fr-azerty
- de-qwertz
- it-qwerty
- jp-kana-jis
- jp-kana-ansi
- pt-qwerty-abnt2
- ru-jcuken
- es-qwerty
- us-qwerty
- uk-qwerty
- us-dvorak
- us-halmak
- sv-qwerty

If the property `configuration-keyboard-layout` is defined, then mousemaster will consider
that the keys used in the configuration file are for that layout. If the active layout is 
different from the `configuration-keyboard-layout`, then the key will be converted to the active layout.
For example:
- `configuration-keyboard-layout=us-qwerty`
- A combo uses the key - (`minus`)
- The active layout is fr-azerty

In that case, the combo will be triggered when the user presses the à key because - in 
us-qwerty and à in fr-azerty correspond to the same physical key (same scan code).
If the active layout is us-qwerty then the combo will be triggered when the user presses the - key (`minus`).

If `configuration-keyboard-layout` is not defined, then its default value is the active 
layout at the time the configuration file is loaded.
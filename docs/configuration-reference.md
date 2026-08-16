# mousemaster configuration reference

## Contents
- [Key aliases](#key-aliases)
- [Combos](#combos)
- [Modes](#modes)
- [Mode switching](#switching-modes)
- [Mode history](#mode-history)
- [Mode timeout](#mode-timeout)
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
- [Hint properties](#hint-properties)
- [Grid properties](#grid-properties)
- [Grid commands](#grid-commands)
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

### Examples

```properties
# Press e while holding leftctrl
normal-mode.to.hint-mode=_{leftctrl} +e

# Press and release leftshift
normal-mode.move-to-grid-center=+leftshift -leftshift

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
hint-mode.hint.type=grid
```

### Property inheritance

Modes can inherit properties from other modes to avoid duplication. For example, if `normal-mode` has defined indicator settings:

```properties
normal-mode.indicator.enabled=true
normal-mode.indicator.color=#FF0000
normal-mode.indicator.size=26
```

You can make another mode inherit these settings:
```properties
slow-mode.indicator=normal-mode.indicator
```

This applies all indicator properties from `normal-mode` to `slow-mode`. You can still override specific properties:
```properties
slow-mode.indicator=normal-mode.indicator
slow-mode.indicator.color=#0000FF  # Override just this property
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

### Mode timeout (deprecated)

**Deprecated**: use wait moves instead. See [combo reference](combo-reference.md#wait-moves) for details.

```properties
# Old (deprecated):
normal-mode.timeout.duration-millis=5000
normal-mode.timeout.mode=idle-mode
normal-mode.timeout.only-if-idle=true

# New (equivalent):
normal-mode.to.idle-mode=wait-5000
```
- The current mode can be automatically changed to another mode after a certain duration.
- If `only-if-idle` is true, then the timeout will be triggered only if the mouse is not being used.

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
hint-mode.hint.box-border-color=#FFFFFF | _{leftshift} -> #00FFFF
```

- Default border color: `#FFFFFF` (white)
- While shift is held: `#00FFFF` (cyan)

When shift is released, the property reverts to its default. Mutations are most useful with precondition-only combos (combos that have no key sequence, only a precondition like `_{key}`). These combos activate when the precondition becomes true and revert when it becomes false.

```properties
# Change the indicator color while leftctrl is held
normal-mode.indicator.color=#00FF00 | _{leftctrl} -> #FF0000
```

Mutations can also be driven by [virtual keys](combo-reference.md#virtual-keys) instead of held keys, so the value
persists across mode switches and survives key releases:

```properties
# Toggle iszoom on/off with z, change zoom percent based on the virtual key
virtual-keys=iszoom
hint-mode.macro.setiszoom=^{iszoom} +z -> #iszoom
hint-mode.macro.unsetiszoom=_{iszoom} +z -> ~iszoom
hint-mode.zoom.percent=1 | _{iszoom} -> 30
```

A virtual key starts released, and reloading the configuration returns every one to released.

When mutating a hint font property, related properties are automatically updated. For example, mutating `font-color` also mutates `selected-font-color`, `focused-font-color`, and the corresponding prefix variants (unless they were explicitly set in the configuration).

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
- `acceleration-easing` controls the shape of the acceleration curve. Numeric values set the exponent: 1 = linear (default), 2 = quadratic (slow start, fast ramp), 0.5 = square root (fast start, slow ramp). Named curves: `smoothstep` (S-curve), `smootherstep` (S-curve with longer precision zone), `logarithmic` (fast start, aggressive flatten), `exponential` (very slow start, explosive ramp).
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
```

### Indicator properties

The indicator is a small polygon displayed next to the cursor. Properties use the format `<mode>.indicator.<property>`.

#### Following the mouse state

Any indicator property can take a different value per mouse state, with a
[mutation](#mode-property-mutation) on the [built-in virtual key](combo-reference.md#built-in-virtual-keys)
of that state. The rightmost matching branch wins:

```properties
normal-mode.indicator.color=#FF0000 | _{iswheeling} -> #FFFF00 | _{ismousepressing} -> #00FF00 | _{isunhandledkeypressing} -> #0000FF
normal-mode.indicator.opacity=0 | _{isleftmousepressing ismoving} -> 1
```

#### Deprecated per-state properties

The states the indicator used to be configured per (`idle`, `move`, `wheel`, `mouse-press`, `left-mouse-press`, `middle-mouse-press`, `right-mouse-press`, `unhandled-key-press`) are gone. `<mode>.indicator.<state>.<property>` is still accepted, with a warning: `idle` becomes the property's default value and every other state a branch on its key.

```properties
# These two lines
normal-mode.indicator.idle.color=#FF0000
normal-mode.indicator.wheel.color=#FFFF00
# now mean
normal-mode.indicator.color=#FF0000 | _{iswheeling} -> #FFFF00
```

A property cannot be given both with and without a state, and a state cannot carry branches of its own.

#### Polygon shape and appearance

The polygon shape, size, color, and opacity:

```properties
normal-mode.indicator.size=12
normal-mode.indicator.edge-count=4
normal-mode.indicator.position=bottom-right
normal-mode.indicator.color=#FF0000
normal-mode.indicator.opacity=1.0
```

- **`size`**: Size of the indicator in pixels (1-100, default 12)
- **`edge-count`**: Number of polygon edges (3-100, default 4). 3 = triangle, 4 = square, 6 = hexagon, 30+ = circle
- **`position`**: Position relative to the mouse cursor: `center`, `top-left`, `top-right`, `bottom-left`, `bottom-right` (default `bottom-right`). `center` places the indicator behind the cursor, centered on it (best used with a low opacity so the cursor remains visible).

#### Outlines

Two optional outlines can be drawn around the polygon: an outer outline and an inner outline.

```properties
# Outer outline
normal-mode.indicator.outer-outline-thickness=3
normal-mode.indicator.outer-outline-color=#FFFFFF
normal-mode.indicator.outer-outline-opacity=1
normal-mode.indicator.outer-outline-fill-percent=1.0
normal-mode.indicator.outer-outline-fill-start-angle=180
normal-mode.indicator.outer-outline-fill-direction=counterclockwise

# Inner outline
normal-mode.indicator.inner-outline-thickness=1
normal-mode.indicator.inner-outline-color=#CC0000
normal-mode.indicator.inner-outline-opacity=1
normal-mode.indicator.inner-outline-fill-percent=1
normal-mode.indicator.inner-outline-fill-start-angle=180
normal-mode.indicator.inner-outline-fill-direction=counterclockwise
```

- **`outer-outline-fill-percent`** / **`inner-outline-fill-percent`**: How much of the outline is filled (0.0-1.0, default 1.0)
- **`outer-outline-fill-start-angle`** / **`inner-outline-fill-start-angle`**: Angle where the fill starts, in degrees (0-360, default 180). 0 = top (12 o'clock), 90 = 3 o'clock, 180 = bottom (6 o'clock), 270 = 9 o'clock. Increases clockwise.
- **`outer-outline-fill-direction`** / **`inner-outline-fill-direction`**: Direction the fill grows from the start angle (`clockwise`, `counterclockwise`, or `both`, default `counterclockwise`). `both` = expands symmetrically.

`outline-*` is equivalent to `outer-outline-*`.

#### Shadow

An optional drop shadow can be added to the indicator:

```properties
normal-mode.indicator.shadow-blur-radius=10
normal-mode.indicator.shadow-color=#000000
normal-mode.indicator.shadow-opacity=0.5
normal-mode.indicator.shadow-horizontal-offset=2
normal-mode.indicator.shadow-vertical-offset=2
```

- **`shadow-blur-radius`**: Blur radius in pixels (0-1000, default 0). Set to > 0 to enable.
- **`shadow-color`**: Hex color of the shadow
- **`shadow-opacity`**: Opacity (0.0-1.0)
- **`shadow-horizontal-offset`** / **`shadow-vertical-offset`**: Shadow offset in pixels (-100 to 100)

#### Label

An optional text label can be displayed inside the indicator:

```properties
normal-mode.indicator.label-enabled=false
normal-mode.indicator.label-text=N
normal-mode.indicator.label-font-name=Arial
normal-mode.indicator.label-font-size=8
normal-mode.indicator.label-font-weight=normal
normal-mode.indicator.label-font-color=#FFFFFF
normal-mode.indicator.label-font-opacity=1.0

# Label font outline
normal-mode.indicator.label-font-outline-thickness=0
normal-mode.indicator.label-font-outline-color=#000000
normal-mode.indicator.label-font-outline-opacity=0.0

# Label font shadow
normal-mode.indicator.label-font-shadow-blur-radius=0
normal-mode.indicator.label-font-shadow-color=#000000
normal-mode.indicator.label-font-shadow-opacity=0.0
normal-mode.indicator.label-font-shadow-horizontal-offset=0
normal-mode.indicator.label-font-shadow-vertical-offset=0
```

- **`label-enabled`**: Whether to show a text label (default false). Automatically set to true if any `label-` property is set.
- **`label-text`**: The text to display
- **`label-font-name`**: Font family name (default Arial)
- **`label-font-size`**: Font size in points
- **`label-font-weight`**: Font weight (e.g. normal, bold)
- **`label-font-color`**: Hex color of the label text
- **`label-font-opacity`**: Opacity of the label text (0.0-1.0)

#### Fade animation

When the indicator appears, disappears, or changes color (e.g. when switching modes), it fades in and out instead of popping abruptly.

```properties
normal-mode.indicator.fade-animation-enabled=true
normal-mode.indicator.fade-animation-duration-millis=100
```

- **`fade-animation-enabled`**: Whether to fade the indicator (default `true`).
- **`fade-animation-duration-millis`**: Duration of the fade in/out in milliseconds (default `100`).

The fade animation is automatically suppressed when the surrounding mode change also changes zoom, to avoid the faded indicator clashing with the zoom screenshot.

#### Transition animation

When the indicator changes, it eases to the new one instead of jumping to it. Sizes, edge counts, thicknesses, opacities, blur radii, offsets and fill percents are interpolated. The edge count morphs two edges at a time, keeping the parity it starts from, because an odd count puts a vertex at the top of the polygon and an even one a flat edge.

Everything else switches rather than eases, because it has no meaningful in-between: the colors, the fill start angle and direction, the shadow stack count, the label, the font name and weight, the position. `transition-animation-switch-at` decides whether they switch at the start of the transition or at its end. Like the duration and the easing, it is read from the indicator being transitioned to, so each direction chooses for itself. Giving the indicator a larger size while a mouse button is pressed makes a click stand out:

```properties
normal-mode.indicator.size=26 | _{ismousepressing} -> 39
normal-mode.indicator.transition-animation-duration-millis=200 | _{ismousepressing} -> 50
normal-mode.indicator.transition-animation-easing=smootherstep
normal-mode.indicator.transition-animation-switch-at=end | _{ismousepressing} -> start
```

- **`transition-animation-duration-millis`**: How long the indicator takes to reach the new one, in milliseconds (default `100`). `0` makes the change immediate.
- **`transition-animation-easing`**: `smoothstep`, `smootherstep`, `logarithmic`, `exponential`, or a polynomial exponent (default `smootherstep`).
- **`transition-animation-overshoot`**: What the size swells to, relative to the size it is easing to (default `1`, which is no swell). The swell rises over the duration of the indicator being transitioned to and falls over the duration of the one it came from, so two indicators of the same size still pulse. It runs to its end even if the indicator changes again while it is rising, so a click that is over before the next frame pulses in full.
- **`transition-animation-switch-at`**: When the values that cannot be eased take the new indicator's, `start` or `end` (default `start`). Above, pressing a mouse button colors the indicator at once, and releasing it keeps that color until the indicator has finished shrinking.

The animation belongs to the indicator that is transitioned to, so each direction has its own duration: above, the indicator turns green and grows in 50ms when a mouse button is pressed, then takes 200ms to shrink back when it is released, turning red once it has. The indicator that appears first is not transitioned to. A change that interrupts a running animation eases from the indicator it had reached, so nothing snaps.

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

- `zoom.percent` must be greater than or equal to 1.0 (100%).
- `zoom.center` can either be `screen-center`, `mouse`
  or `last-selected-hint`.
- On macOS, zooming needs the Screen Recording permission to capture what it magnifies.

Optional animation can be enabled to smoothly transition in and out of zoom:
```properties
zoom-mode.zoom.animation-enabled=true
zoom-mode.zoom.animation-easing=smootherstep
zoom-mode.zoom.animation-duration-millis=300
```

- `zoom.animation-enabled` defaults to `false`.
- `zoom.animation-easing` can be `linear`, `smoothstep`, `smootherstep`, `logarithmic`, `exponential`, or `polynomial-N` (where N is the exponent). Defaults to `smootherstep`.
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

## Hint properties

The hint system in mousemaster displays labels on the screen that you can select with keystrokes to move the mouse to specific locations. This is similar to the hint systems in browser extensions like Vimium, but works system-wide.

### Hint types

The type of hint system to use is configured with the following property:

```properties
hint-mode.hint.type=grid
```

The **`type`** property determines what hints are displayed:
- `grid`: Displays a grid of hints laid out across the screen
- `position-history`, or any `<name>-position-history`: Displays discrete absolutely positioned hints at the positions saved in that history (see [Position history](#position-history))
- `ui`: Displays hints at interactive UI elements (see [UI hints](#ui-hints))

### UI hints

UI hints detect interactive elements (buttons, links, text fields, checkboxes, etc.) and display
a hint label on each one, with the UI Automation API on Windows and the Accessibility API on macOS.
On macOS this needs the Accessibility permission, without which no element is found.
Unlike grid hints, UI hints are positioned based on the UI elements' locations.

```properties
ui-hint-mode.hint.type=ui
ui-hint-mode.hint.ui-area=active-screen
ui-hint-mode.hint.selection-keys=selectionkey
ui-hint-mode.hint.select=+selectionkey
ui-hint-mode.hint.undo=backspace
ui-hint-mode.hint.mouse-movement=mouse-follows-selected-hint
ui-hint-mode.hint.font-size=10
ui-hint-mode.hint.font-weight=bold
ui-hint-mode.hint.font-color=#FFFFFF
ui-hint-mode.hint.box-color=#204E8A
ui-hint-mode.hint.box-opacity=1
ui-hint-mode.hint.cell-horizontal-padding=8
ui-hint-mode.hint.cell-vertical-padding=0
ui-hint-mode.hint.box-border-radius=3
```

**`ui-area`** selects the windows the elements are looked for in (default
`active-screen`, the same default as the grid area):
- `active-screen`: Every visible window on the screen with the mouse cursor
- `all-screens`: Every visible window on every connected screen
- `active-window`: The active window, and its popups (menus, dropdowns, dialogs). A popup
  is hinted even where it extends past the window.

With `active-screen` and `all-screens`, elements are cropped to that region, and elements
covered by a window drawn over theirs are left out, so a hint is only shown where the
click it performs would reach the element. Windows of another virtual desktop and
suspended apps are left out. Scanning several windows takes longer than scanning one: the
hints appear once the scan is over, mousemaster stays responsive in the meantime.

`ui-area` and the `grid-area*` properties are exclusive: `ui-area` only applies to
`hint.type=ui`, and setting `grid-area` on a UI hint mode is rejected.

UI hints use the same appearance properties as other hint types (`box-*`, `font-*`, etc.).
The `cell-horizontal-padding` and `cell-vertical-padding` properties are particularly useful
for UI hints since the hint boxes are sized to fit the label text rather than a fixed grid
cell.

### Hint layout and positioning

The following properties control where hints appear and how they're arranged:

```properties
# Hint area configuration
hint-mode.hint.grid-area=active-screen
hint-mode.hint.grid-area-center=screen-center

# Grid layout configuration
hint-mode.hint.grid-cell-width=74
hint-mode.hint.grid-cell-height=36
hint-mode.hint.grid-cell-sizing=fixed
hint-mode.hint.layout-row-count=6
hint-mode.hint.layout-column-count=5
```

The grid area is two independent settings: **`grid-area`** sets the area's *size*,
**`grid-area-center`** sets the point the area is *centered* on.

- **`grid-area`**:
  - `active-screen`: The screen with the mouse cursor
  - `active-window`: The currently active window
  - `all-screens`: Every connected screen (one grid per screen — `grid-area-center` does not apply)
  - `last-selected-hint-cell`: The cell of the last selected hint (for a recursive grid)

  `grid-area` only applies to `hint.type=grid`. What `hint.type=ui` scans is selected by
  `ui-area` (see [UI hints](#ui-hints)).

- **`grid-area-width-percent`** / **`grid-area-height-percent`**: Scale the area to a
  fraction of that region, keeping it centered on `grid-area-center` (default `1.0`).
  E.g. `grid-area=active-screen` + `grid-area-width-percent=0.5` covers the middle 50%
  of the screen's width. Works with both `grid-cell-sizing=fit` and `=fixed`.

- **`grid-area-center`** (point the area is centered on):
  - `screen-center`: Center of the screen
  - `mouse`: Current mouse position
  - `last-selected-hint`: Position of the last selected hint
  - `active-window-center`: Center of the active window

  When omitted, the center defaults per size: `active-screen`/`all-screens` →
  `screen-center`, `active-window` → `active-window-center`, `last-selected-hint-cell` →
  `last-selected-hint` (grid stays over the cell). The size and center compose
  freely — e.g. an `active-window`-sized grid with `grid-area-center=mouse` is a
  window-sized grid centered on the cursor.

  `active-screen-grid-area-center` is a deprecated alias for `grid-area-center`.

- **Grid dimensions**: Control the size of each hint cell:
  - `grid-cell-width`: Width of each hint cell in pixels
  - `grid-cell-height`: Height of each hint cell in pixels

  The sizes are unzoomed pixels, so the zoom scales them: under a `zoom.percent` of 5, a
  cell of width 10 is drawn 50 pixels wide and still covers 10 pixels of the magnified
  content. A grid therefore keeps its shape, and its hint keys, whatever the zoom.

- **`grid-cell-sizing`**: How cell size is determined:
  - `fixed` (default): cells are `grid-cell-width` x `grid-cell-height` pixels, and `grid-max-row-count`/`grid-max-column-count` cap how many fit.
  - `fit`: cells are sized to fill the area with exactly `grid-max-row-count` x `grid-max-column-count` cells (the pixel sizes are ignored). Useful for a fixed grid shape (e.g. 3x3) that adapts to any screen, and for a recursive grid via `grid-area=last-selected-hint-cell`.

- **Grid arrangement**: Control the number of rows and columns:
  - `layout-row-count`: Number of rows in the hint grid
  - `layout-column-count`: Number of columns in the hint grid

You can create different layouts by adjusting the row and column counts:
  - For column layout: `layout-row-count=1` and `layout-column-count=1000`
  - For row layout: `layout-row-count=1000` and `layout-column-count=1`
  - For grid layout (10x3 subgrid): `layout-row-count=10` and `layout-column-count=3`

### Screen-specific hint configurations

Any hint property can hold a different value per screen: give the screen as a precondition of a [combo property](#mode-property-mutation).

```properties
# A 4K screen gets denser hints than the others:
hint1-mode.hint.grid-cell-width=74 | _{3840x2160-100%} -> 96
hint1-mode.hint.grid-cell-height=41 | _{3840x2160-100%} -> 54
hint1-mode.hint.layout-row-count=6 | _{3840x2160-100%} -> 4
hint1-mode.hint.layout-column-count=5 | _{3840x2160-100%} -> 10
```

A screen filter is a screen resolution (`3840x2160`), a screen scale (`150%`), or both (`3840x2160-150%`). Each screen resolves its own value when the hints are drawn, so a mesh covering several screens gets a value per screen. The most specific matching filter wins: resolution and scale first, then resolution, then scale. A screen matching none of them takes the default value.

A length — a padding, a border thickness, radius or length, a shadow blur or offset, a grid line thickness — is in the pixels of a 100% screen, and the screen's scale grows it, exactly as it grows a font size. A hint therefore keeps its proportions on a 200% or 300% screen with no per-screen value at all. To pin a length to a pixel count on one scale, override it there and divide: `hint1-mode.hint.box-border-thickness=1 | _{150%} -> 0.67` draws a single pixel at 150%.

```properties
# Every screen scaled at 300%, whatever its resolution.
hint1-mode.hint.box-border-radius=1 | _{300%} -> 3

# Mixed with a key precondition: on 300% screens only, and only while
# leftshift is pressed.
hint1-mode.hint.box-border-radius=1 | _{300% leftshift} -> 3

# Either of two screens. A space means AND, as it does between keys, so two
# filters have to be alternatives: no screen is both at once.
hint1-mode.hint.box-border-radius=1 | _{3840x2160-300% | 1366x768-100%} -> 3

# Alongside a key, one filter per branch: a | in that block would read as an
# alternative to the key, which no screen can satisfy.
hint1-mode.hint.box-border-radius=1 | _{leftshift 3840x2160-300%} -> 3 \
    | _{leftshift 1366x768-100%} -> 3
```

Only a hint property is filtered per screen. Everywhere else — another property, a command,
a mode switch — a screen filter is a [virtual key](combo-reference.md#built-in-virtual-keys)
that mousemaster presses while the active screen matches it, so it reads like any other key:

```properties
# A grid line is one pixel wide on a 300% screen.
grid-mode.grid.line-thickness=1 | _{300%} -> 0.66667

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

hint1-mode.hint.grid-cell-sizing=fit
hint1-mode.hint.grid-max-column-count=25 | _{mediumscreen | largescreen} -> 40
hint1-mode.hint.grid-max-row-count=30 | _{smallscreen} -> 20 | _{mediumscreen | largescreen} -> 40
hint1-mode.hint.selection-keys=hintkey | _{mediumscreen | largescreen} -> extendedhintkey
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
hint-mode.hint.selection-keys=selectionkey
hint-mode.hint.select=+selectionkey
hint-mode.hint.undo=backspace
hint-mode.to.normal-mode=+selectionkey
hint-mode.hint.mouse-movement=mouse-follows-selected-hint
hint-mode.hint.eat-unused-selection-keys=true
hint-mode.hint.label-override=plus
```

- **`selection-keys`**: Keys that can be pressed to select hints
- **`select`**: Combo that triggers hint selection. For example, if you want to be able to hold leftshift while pressing a hint selection key, then use `hint-mode.hint.select=_{none | leftshift} +selectionkey`
- **`undo`**: Key combo to undo the last hint selection keystroke
- **`to.<mode-name>`**: Use this syntax to specify which mode to switch to after a hint is selected (e.g., `hint-mode.to.normal-mode=+selectionkey`)
- **`mouse-movement`**: Controls how the mouse moves when interacting with hints
  - `no-movement`: Mouse stays in its current position
  - `mouse-follows-selected-hint`: Mouse moves to the selected hint (default behavior)
  - `mouse-follows-hint-grid-center`: Mouse moves to the center of the hint grid
  - Use `no-movement` when the next mode will be another hint grid
- **`eat-unused-selection-keys`**: If `false`, selection keys not used in the current hint display can be used in combos
- **`label-override`**: Draws a fixed marker in every hint cell instead of each hint's own keys — visual only, you still press the real keys to select. The value is a key sequence rendered as the label: `plus` shows `+`, `plus plus` shows `++`. Useful for positional grids (e.g. a 3×3 recursive grid) where the per-cell letters are just clutter. Default: unset (each hint shows its own key sequence).

### Hint appearance

```properties
# Box appearance
hint-mode.hint.box-color=#000000
hint-mode.hint.box-opacity=0.4
hint-mode.hint.box-border-thickness=1
hint-mode.hint.box-border-length=1000
hint-mode.hint.box-border-color=#FFFFFF
hint-mode.hint.box-border-opacity=0.4
hint-mode.hint.box-border-radius=0
# false draws the lines between the boxes only, no frame around the grid
hint-mode.hint.box-framed=true

# Box shadow for UI hints and position history hints only
hint-mode.hint.box-shadow-blur-radius=10
hint-mode.hint.box-shadow-color=#000000
hint-mode.hint.box-shadow-opacity=0
hint-mode.hint.box-shadow-stack-count=1
hint-mode.hint.box-shadow-horizontal-offset=2
hint-mode.hint.box-shadow-vertical-offset=2

# Box size relative to the grid cell (for the hint grid only)
hint-mode.hint.box-width-percent=1.0
hint-mode.hint.box-height-percent=1.0

# Decorations: purely visual grids drawn over the hints. subdecoration is drawn inside
# each hint cell (a preview of the next level; was subgrid), subsubdecoration inside each
# of those (was subsubgrid), and decoration spans the whole hint grid as one big cell.
# Set subdecoration-label-keys to label the cells.
hint-mode.hint.subdecoration-max-row-count=1
hint-mode.hint.subdecoration-max-column-count=1
hint-mode.hint.subdecoration-label-keys=
hint-mode.hint.subdecoration-box-border-thickness=1
hint-mode.hint.subdecoration-box-border-length=10000
hint-mode.hint.subdecoration-box-border-color=#FFFFFF
hint-mode.hint.subdecoration-box-border-opacity=1.0
hint-mode.hint.subdecoration-box-framed=false
hint-mode.hint.subdecoration-font-size=10
hint-mode.hint.subdecoration-font-color=#FFFFFF
hint-mode.hint.subdecoration-font-opacity=1.0

# Cell padding for UI hints and position history hints only
hint-mode.hint.cell-horizontal-padding=0
hint-mode.hint.cell-vertical-padding=0

# Font appearance
hint-mode.hint.font-name=Consolas
hint-mode.hint.font-size=18
hint-mode.hint.font-spacing-percent=0.7
hint-mode.hint.font-color=#FFFFFF
hint-mode.hint.font-opacity=1.0

# Font effects
hint-mode.hint.font-outline-thickness=0
hint-mode.hint.font-outline-color=#000000
hint-mode.hint.font-outline-opacity=0.5
hint-mode.hint.font-shadow-blur-radius=0
hint-mode.hint.font-shadow-color=#000000
hint-mode.hint.font-shadow-opacity=1.0
hint-mode.hint.font-shadow-horizontal-offset=0
hint-mode.hint.font-shadow-vertical-offset=0

# Selected hint appearance
hint-mode.hint.selected-font-color=#A3A3A3
hint-mode.hint.selected-font-opacity=1.0

# Prefix box (for multi-character hints). A border is never drawn where an enclosing one
# already draws, so the hint boxes leave the prefix box its own.
hint-mode.hint.prefix-box-enabled=true
hint-mode.hint.prefix-box-border-thickness=2
hint-mode.hint.prefix-box-border-color=#FFD93D
hint-mode.hint.prefix-box-border-opacity=0.8
# false draws the lines between the prefix boxes only, no frame around the grid
hint-mode.hint.prefix-box-framed=true
hint-mode.hint.prefix-in-background=true

# Animation
hint-mode.hint.transition-animation-enabled=true
hint-mode.hint.transition-animation-duration-millis=100

# Fade animation (when hints appear or disappear)
hint-mode.hint.fade-animation-enabled=true
hint-mode.hint.fade-animation-duration-millis=100

# Background (for UI hints and position history hints only)
hint-mode.hint.background-color=#000000
hint-mode.hint.background-opacity=0
```

- Box appearance: controls the background and border of hint boxes
  - `box-border-length`: Higher values create continuous lines between hint boxes, lower values create dotted separators
  - `box-border-radius`: Radius for rounded corners (0-1000, default 0).
  - `box-shadow-blur-radius`: Shadow blur radius (0-1000, default 10).
  - `box-shadow-color`: Shadow color (hex, default #000000).
  - `box-shadow-opacity`: Shadow opacity (0-1, default 0 = disabled).
  - `box-shadow-stack-count`: Number of times the shadow is composited on itself for stronger effect (1-100, default 1).
  - `box-shadow-horizontal-offset`: Horizontal shadow offset (-100 to 100, default 0).
  - `box-shadow-vertical-offset`: Vertical shadow offset (-100 to 100, default 0).
  - `background-color`: Background color behind all hint boxes, mostly useful for UI hints (hex, default #000000).
  - `background-opacity`: Background opacity, mostly useful for UI hints (0-1, default 0 = no background).

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

- Font appearance: controls how hint labels appear
    - `font-spacing-percent`: Controls character spacing (0=touching, 1=evenly distributed, 0.5=minimal spacing with alignment)

- Animation:
    - `transition-animation-enabled` / `transition-animation-duration-millis`: animates hint boxes sliding to their new positions when the hint set changes (e.g. after typing a key in a multi-level hint mode). Default enabled, 100ms.
    - `fade-animation-enabled` / `fade-animation-duration-millis`: fades hints in when they first appear and out when they disappear. Default enabled, 100ms. Suppressed when the surrounding mode change also changes zoom.

### Hint prefix

A hint is made of several keys (letters). For example, hint JKK is made of 3 keys: J, K and K.
- The hint prefix is JK.
- The prefix (JK) can be displayed "in the background" with `prefix-in-background=true`.

- In a column layout, hints in the same column have the same prefix.
- In a row layout, hints in the same column have the same prefix.
- In a grid layout (e.g. 5x6 subgrid), hints in the same subgrid have the same prefix.

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

There is only one `font-spacing-percent` (and one `prefix-font-spacing-percent`), shared across all 3 states.

The property naming pattern is:
- Default: `hint.font-*`
- Selected: `hint.selected-font-*`
- Focused: `hint.focused-font-*`
- Prefix default: `hint.prefix-font-*`
- Prefix selected: `hint.prefix-selected-font-*`
- Prefix focused: `hint.prefix-focused-font-*`

### Multi-level hint example

This example shows how to create a two-level hint system, where the first level selects a general area, and the second level provides more precise targeting:

```properties
key-alias.selectionkey=a b c d e f g h i j k l m n o p q r s t u v w x y z
# First level hint mode
hint1-mode.hint.type=grid
hint1-mode.hint.grid-area=active-screen
hint1-mode.hint.selection-keys=selectionkey
hint1-mode.to.hint2-mode=+selectionkey
# Don't move mouse after first selection
hint1-mode.hint.mouse-movement=no-movement

# Second level hint mode
hint2-mode.hint.type=grid
hint2-mode.hint.grid-area=active-screen
# Center on last selected hint
hint2-mode.hint.grid-area-center=last-selected-hint
# 3x8 grid
hint2-mode.hint.grid-max-row-count=3
hint2-mode.hint.grid-max-column-count=8
# Smaller cells for precision
hint2-mode.hint.grid-cell-width=30
hint2-mode.hint.grid-cell-height=20
hint2-mode.hint.font-size=7
hint2-mode.hint.selection-keys=selectionkey
hint2-mode.to.normal-mode=+selectionkey
# Move mouse after second selection
hint2-mode.hint.mouse-movement=mouse-follows-selected-hint
```

## Grid properties

The grid system in mousemaster divides the screen or window into a configurable grid. It is
different from the hint grid system which shows hints (a group of letters) to select.

### Basic grid configuration

```properties
# Grid area and dimensions
grid-mode.grid.area=active-screen
grid-mode.grid.area-width-percent=1.0
grid-mode.grid.area-height-percent=1.0

# Grid divisions
grid-mode.grid.row-count=2
grid-mode.grid.column-count=2

# Grid appearance
grid-mode.grid.line-visible=true
grid-mode.grid.line-color=#FF0000
grid-mode.grid.line-thickness=1
grid-mode.grid.line-opacity=1.0
grid-mode.grid.background-color=#FF0000
grid-mode.grid.background-opacity=0.1

# Grid transition and fade animations
grid-mode.grid.transition-animation-enabled=true
grid-mode.grid.transition-animation-duration-millis=100
grid-mode.grid.fade-animation-enabled=true
grid-mode.grid.fade-animation-duration-millis=100
```

- **`grid-area`**: Determines where the grid is displayed:
  - `active-screen`: Covers the screen with the mouse cursor
  - `active-window`: Covers only the currently active window

- Grid size: control the grid's coverage with:
  - `area-width-percent`: Width as a percentage of the area (1.0 = 100%)
  - `area-height-percent`: Height as a percentage of the area

- Grid divisions: set the number of cells with:
  - `row-count`: Number of horizontal divisions
  - `column-count`: Number of vertical divisions

- Grid appearance: control the grid's visual style with:
  - `line-visible`: Whether to show grid lines
  - `line-color`: Color of grid lines (hex format)
  - `line-thickness`: Thickness of grid lines in pixels
  - `line-opacity`: Opacity of grid lines (0.0 = transparent, 1.0 = opaque). Default 1.0.
  - `background-color`: Fill color of the grid area, behind the lines (default #FF0000).
  - `background-opacity`: Opacity of the background fill (default 0.1).

- Grid transition animation: eases the grid to its new position and size when it changes (e.g. after `shrink-grid` or `move-grid`):
  - `transition-animation-enabled`: Whether to animate grid transitions. Default enabled.
  - `transition-animation-duration-millis`: Animation duration in milliseconds. Default 100.

- Grid fade animation: fades the grid in and out when it appears and disappears:
  - `fade-animation-enabled`: Whether to fade the grid in/out. Default enabled.
  - `fade-animation-duration-millis`: Fade duration in milliseconds. Default 100.

### Grid positioning and insets

```properties
# Grid insets (margins)
grid-mode.area-top-inset=15
grid-mode.area-bottom-inset=0
grid-mode.area-left-inset=0
grid-mode.area-right-inset=0

# Grid synchronization with mouse
grid-mode.grid.synchronization=mouse-follows-grid-center
```

- Insets: control margins from the edges of the area:
  - Useful for avoiding UI elements like window title bars
  - Example: `area-top-inset=15` creates a 15-pixel margin at the top

- Synchronization: control how the grid and mouse interact:
  - `mouse-follows-grid-center`: Mouse moves to the center of the grid
  - `grid-center-follows-mouse`: Grid centers on the mouse position
  - `mouse-and-grid-center-unsynchronized`: Grid and mouse move independently

### Grid usage example

This example shows a complete grid mode configuration:

```properties
# Define grid mode
grid-mode.grid.area=active-screen
grid-mode.grid.row-count=2
grid-mode.grid.column-count=2
grid-mode.grid.line-visible=true
grid-mode.grid.line-color=#00FF00
grid-mode.grid.synchronization=mouse-follows-grid-center

# Grid commands
grid-mode.shrink-grid.up=+i
grid-mode.shrink-grid.down=+k
grid-mode.shrink-grid.left=+j
grid-mode.shrink-grid.right=+l
grid-mode.move-to-grid-center=+space

# Exit grid mode
grid-mode.to.normal-mode=+escape
```

With this configuration:
1. The screen is divided into a 2×2 grid with green lines
2. Pressing I/J/K/L shrinks the grid in that direction
3. Pressing Space moves the mouse to the grid center
4. Pressing Escape returns to normal mode

## Grid commands

Grid commands allow you to interact with the grid system, manipulating both the grid itself and the mouse position relative to the grid.

### Types of grid commands

```properties
# Move the entire grid
grid-mode.move-grid.up=_{leftshift} +uparrow
grid-mode.move-grid.down=_{leftshift} +downarrow
grid-mode.move-grid.left=_{leftshift} +leftarrow
grid-mode.move-grid.right=_{leftshift} +rightarrow

# Snap mouse to grid cell edges
grid-mode.snap.up=_{leftctrl} +uparrow
grid-mode.snap.down=_{leftctrl} +downarrow
grid-mode.snap.left=_{leftctrl} +leftarrow
grid-mode.snap.right=_{leftctrl} +rightarrow

# Shrink the grid (refine selection)
grid-mode.shrink-grid.up=+uparrow
grid-mode.shrink-grid.down=+downarrow
grid-mode.shrink-grid.left=+leftarrow
grid-mode.shrink-grid.right=+rightarrow

# Center the mouse
grid-mode.move-to-grid-center=+space
```

- Move grid commands: shift the entire grid in a direction
  - Useful for repositioning the grid without changing its size
  - Example: `move-grid.up` moves the entire grid upward

- Snap commands: move the mouse to the edge of the closest grid cell
  - Useful for precise positioning along grid lines
  - Example: `snap.left` moves the mouse to the left edge of its current cell

- Shrink grid commands: divide the grid size by 2 in the specified direction
  - Allows for increasingly precise targeting
  - Example: `shrink-grid.up` cuts the grid height in half, keeping only the top portion

- Center command: moves the mouse to the center of the grid
  - Useful for quick centering after grid manipulation
  - Example: `move-to-grid-center` places the mouse at the grid's center point

### Recursive grid navigation workflow

A typical recursive grid navigation workflow:

1. Enter grid mode (divides screen into a 2×2 grid)
2. Use shrink commands to narrow down to the target area
3. Use snap commands for edge-precise positioning or center command for center positioning
4. Exit grid mode when the mouse is at the desired position

### Example grid command configuration

```properties
# Enter grid mode from normal mode
normal-mode.to.grid-mode=+g

# Option 1: Recursive grid navigation with WASD keys
grid-mode.shrink-grid.up=+w
grid-mode.shrink-grid.down=+s
grid-mode.shrink-grid.left=+a
grid-mode.shrink-grid.right=+d

# Snap to edges with leftctrl + direction
grid-mode.snap.up=_{leftctrl} +w
grid-mode.snap.down=_{leftctrl} +s
grid-mode.snap.left=_{leftctrl} +a
grid-mode.snap.right=_{leftctrl} +d

# Move grid with leftshift + direction
grid-mode.move-grid.up=_{leftshift} +w
grid-mode.move-grid.down=_{leftshift} +s
grid-mode.move-grid.left=_{leftshift} +a
grid-mode.move-grid.right=_{leftshift} +d

# Center and exit
grid-mode.move-to-grid-center=+space
grid-mode.to.normal-mode=+escape
```

Alternatively, you could use arrow keys instead of WASD:

```properties
# Option 2: Recursive grid navigation with arrow keys
grid-mode.shrink-grid.up=+uparrow
grid-mode.shrink-grid.down=+downarrow
grid-mode.shrink-grid.left=+leftarrow
grid-mode.shrink-grid.right=+rightarrow

# Snap and move commands would be configured similarly
grid-mode.snap.up=_{leftctrl} +uparrow
# etc.
```

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

Position history allows you to save mouse positions and quickly return to them later. This feature can be used for frequently accessed UI elements or for creating custom navigation patterns.

### Basic configuration

```properties
# Set the maximum number of positions to remember
position-history.max-size=16

# Define a key to save the current mouse position
normal-mode.position-history.save-position=+f1

# Define a key to clear all saved positions
normal-mode.position-history.clear=+f2
```

- **`max-size`**: Maximum number of positions that can be stored (older positions are removed when this limit is reached). Default 16.
- **`save-position`**: Command to save the current mouse position to history
- **`clear`**: Command to clear all saved positions

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
position-hint-mode.hint.type=position-history
position-hint-mode.hint.selection-keys=a b c d e f g h i j k l m n o p q r s t u v w x y z

# Switch to position hint mode
normal-mode.to.position-hint-mode=+p

# A hint mode for the browser history
browser-hint-mode.hint.type=browser-position-history
```

Because `hint.type` can be combo-triggered, one hint mode can show different histories:

```properties
position-hint-mode.hint.type=position-history | _{browserapp} -> browser-position-history
```

When you enter a position history hint mode:
1. One hint box will be displayed for each saved position
2. Selecting a hint moves the mouse to that saved position
3. If no positions are saved, no hints will be displayed

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

`macro` and `remapping` mean the same thing and can be used interchangeably (e.g. `mode-name.remapping.macro-name=...`).

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
key on the keyboard. Declare such keys with `virtual-keys` (a global, space-separated
list):

```properties
virtual-keys=drilling
```

A virtual key can never be produced by hardware, is never sent to the
OS, and is only ever pressed/released by a macro via `#`/`~`. It can be used anywhere a
real key can in the combo system — in a macro's `#`/`~` moves, and in `_{}`/`^{}`
preconditions — but using it as an OS move (`+`/`-`) is a configuration error.

Declaring it (rather than relying on a name that happens not to exist) keeps typo
detection: an undeclared unknown key name is still rejected at load time.

```properties
# "drilling" is held only for the span of the macro; gate a property on it.
virtual-keys=drilling
some-mode.macro.example=+g -> #drilling k k ~drilling
some-mode.hint.visible=true | _{drilling} -> false
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
  - Using the Task Manager to end the mousemaster.exe process
  - Using Win+R and typing: `taskkill /F /IM mousemaster.exe`

## Logging

mousemaster provides configurable logging to help with troubleshooting and debugging.

```properties
# Logging configuration
logging.level=INFO
logging.redact-keys=true
logging.to-file=true
logging.file-path=mousemaster.log
```

### Logging options

- **`logging.level`**: Controls the verbosity of logging
  - Valid values: `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`
  - Higher levels (DEBUG, TRACE) provide more detailed information but may impact performance

- **`logging.redact-keys`**: When set to `true`, pressed keys will be redacted from logs
  - Useful for privacy
  - Set to `false` for full key logging when troubleshooting keyboard issues

- **`logging.to-file`**: When set to `true`, logs are written to a file in addition to the console
  - Useful when `hide-console=true` or for persistent logging

- **`logging.file-path`**: Path to the log file when `logging.to-file=true`
  - Default is `mousemaster.log` in the application directory

## Keyboard layout

There is currently a limitation with keyboard layouts (#37): I have not found a reliable way to get the active keyboard layout using the Windows API.
The workaround is to explicitly tell mousemaster which keyboard layout to use:
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
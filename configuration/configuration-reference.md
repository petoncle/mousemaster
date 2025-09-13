# mousemaster configuration reference

This document provides a comprehensive reference for all configuration options available in mousemaster.

## Contents
- [Key aliases](#key-aliases)
- [Combos](#combos)
- [Multi-combos](#multi-combo)
- [Modes](#modes)
- [Mode switching](#switching-modes)
- [Mode history](#mode-history)
- [Mode timeout](#mode-timeout)
- [Standalone mode properties](#standalone-mode-properties)
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

Combos are sequences of key presses and releases that trigger commands in mousemaster. Understanding combo syntax is essential for creating effective configurations.

### Basic combo syntax

1. Key press: `+key` indicates a key press. For example, `+leftctrl +e` means leftctrl must be pressed, then e must be pressed.
   ```properties
   normal-mode.to.hint-mode=+f
   ```

2. Key release: `-key` indicates a key release. For example, `-leftctrl` corresponds to releasing the leftctrl key.
   ```properties
   normal-mode.release.left=-leftbutton
   ```

3. Non-eaten key press: `#key` is similar to `+key` but the key press event is not "eaten" by mousemaster - other applications will also receive the event.
   ```properties
   normal-mode.press.left=#leftbutton
   ```

### Combo duration

1. Default duration: `+leftctrl` is equivalent to `+leftctrl-0-250`, meaning leftctrl must be pressed for a duration between 0 and 250 milliseconds. mousemaster won't consider the combo complete if you press leftctrl for longer than 250ms.

2. Custom duration: you can specify custom durations:
   - `+leftctrl-0-500`: Press leftctrl for 0-500ms
   - `+leftctrl-0`: Press leftctrl for at least 0ms, with no upper limit

3. Default duration configuration: change the default combo move duration with:
   ```properties
   default-combo-move-duration-millis=0-250
   ```

### Combo preconditions

1. Pressed key precondition: `_{leftctrl} +e` has two parts:
   - Precondition (`_{leftctrl}`): leftctrl must be pressed before the combo sequence
   - Sequence (`+e`): press e

   The precondition key (leftctrl) will not be eaten by mousemaster.

   ```properties
   normal-mode.press.left=_{leftctrl} +leftbutton
   ```

2. Not-pressed key precondition: `^{leftctrl} +e` means leftctrl must *not* be pressed when the combo sequence is performed.
   ```properties
   normal-mode.to.idle-mode=^{leftctrl} +q
   ```

3. Multiple keys in precondition: you can specify multiple keys in a precondition using the pipe symbol:
   ```properties
   normal-mode.press.left=_{none | leftctrl | leftshift leftalt} +leftbutton
   ```
   This means either no modifier key is pressed, or leftctrl is pressed, or leftshift and leftalt are pressed.

4. Keys from previous combos: keys that are part of a previous combo don't need to be specified again in subsequent combos.

   ```properties
   # This combo eats leftctrl, leftalt, and space when pressed
   normal-mode.to.normal-mode=#leftshift | +leftctrl | +leftalt | +space

   # This combo requires space to be held, then hint key to be pressed
   # It works even if leftctrl, leftalt, or leftshift are also being held
   normal-mode.to.hint-mode=_{space} +hint
   ```

   In this example:
   - The first combo doesn't change the mode but "eats" leftctrl, leftalt and space.
   - Any key press that is part of a previous combo doesn't need to be specified again in subsequent combos
   - As long as those keys remain pressed, they don't need to be specified in combos that don't care whether they are pressed

   This behavior allows for complex key combinations where modifier keys can be pressed in any order without breaking the combo sequence.

### Complex combo examples

```properties
# Press e while holding leftctrl
normal-mode.to.hint-mode=_{leftctrl} +e

# Press and release leftshift
normal-mode.move-to-grid-center=+leftshift -leftshift

# Press a, then press b while still holding a, then release a
normal-mode.to.idle-mode=+a +b -b -a
```

### Multi-combo
One command can be assigned multiple combo. As soon as one of the combos is completed, then the command is executed. The combos must be separated by `|`:
```properties
normal-mode.to.idle-mode=+exit | -clickthendisable
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
normal-mode.indicator.idle-color=#FF0000
normal-mode.indicator.move-color=#FF0000
normal-mode.indicator.wheel-color=#FFFF00
normal-mode.indicator.mouse-press-color=#00FF00
normal-mode.indicator.left-mouse-press-color=#00FF00
normal-mode.indicator.middle-mouse-press-color=#00FF00
normal-mode.indicator.right-mouse-press-color=#00FF00
```

You can make another mode inherit these settings:
```properties
slow-mode.indicator=normal-mode.indicator
```

This applies all indicator properties from `normal-mode` to `slow-mode`. You can still override specific properties:
```properties
slow-mode.indicator=normal-mode.indicator
slow-mode.indicator.idle-color=#0000FF  # Override just this property
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

### Mode timeout

```properties
normal-mode.timeout.duration-millis=5000
normal-mode.timeout.mode=idle-mode
normal-mode.timeout.only-if-idle=true
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

### Mouse properties

```properties
normal-mode.mouse.initial-velocity=1600
normal-mode.mouse.max-velocity=2200
normal-mode.mouse.acceleration=1500
normal-mode.mouse.smooth-jump-enabled=true
normal-mode.mouse.smooth-jump-velocity=30000
```
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

```properties
normal-mode.indicator.enabled=true
normal-mode.indicator.idle-color=#FF0000
normal-mode.indicator.move-color=#FF0000
normal-mode.indicator.wheel-color=#FFFF00
normal-mode.indicator.mouse-press-color=#00FF00
normal-mode.indicator.unhandled-key-press-color=#0000FF
```
The color of the indicator can be changed depending on the current action being performed.

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
- `position-history`: Displays discrete absolutely positioned hints at previously saved positions (see [Position history](#position-history))

### Hint layout and positioning

The following properties control where hints appear and how they're arranged:

```properties
# Hint area configuration
hint-mode.hint.grid-area=active-screen
hint-mode.hint.active-screen-grid-area-center=screen-center

# Grid layout configuration
hint-mode.hint.grid-cell-width=74
hint-mode.hint.grid-cell-height=36
hint-mode.hint.layout-row-count=6
hint-mode.hint.layout-column-count=5
```

- **`grid-area`**: Determines where hints are displayed:
  - `active-screen`: Only on the screen with the mouse cursor
  - `active-window`: Only in the currently active window
  - `all-screens`: Across all connected screens

- **`active-screen-grid-area-center`**: Sets the center point for the hint grid:
  - `screen-center`: Center of the screen
  - `mouse`: Current mouse position
  - `last-selected-hint`: Position of the last selected hint

- **Grid dimensions**: Control the size of each hint cell:
  - `grid-cell-width`: Width of each hint cell in pixels
  - `grid-cell-height`: Height of each hint cell in pixels

- **Grid arrangement**: Control the number of rows and columns:
  - `layout-row-count`: Number of rows in the hint grid
  - `layout-column-count`: Number of columns in the hint grid

You can create different layouts by adjusting the row and column counts:
  - For column layout: `layout-row-count=1` and `layout-column-count=1000`
  - For row layout: `layout-row-count=1000` and `layout-column-count=1`
  - For grid layout (10x3 subgrid): `layout-row-count=10` and `layout-column-count=3`

### Screen-specific hint configurations

You can optimize hint configurations for different screen resolutions by appending the resolution to the property name:

```properties
# Default hint configuration (applies to all screens unless overridden):
hint1-mode.hint.grid-cell-width=74
hint1-mode.hint.grid-cell-height=41
hint1-mode.hint.layout-row-count=6
hint1-mode.hint.layout-column-count=5

# Override for a 4K screen (3840×2160):
hint1-mode.hint.grid-cell-width.3840x2160=96
hint1-mode.hint.grid-cell-height.3840x2160=54
hint1-mode.hint.layout-row-count.3840x2160=4
hint1-mode.hint.layout-column-count.3840x2160=10
```

The syntax is `property-name.resolution=value`, where:
- `property-name` is the standard property name (e.g., `hint1-mode.hint.grid-cell-width`)
- `resolution` is the screen resolution in the format `widthxheight` (e.g., `3840x2160` for 4K)
- `value` is the property value specific to that resolution

mousemaster automatically uses the appropriate configuration based on the screen where the hints are displayed. Any hint property can be customized per screen resolution. If a screen-specific property is not defined, mousemaster falls back to the default property value.

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

### Hint appearance

```properties
# Box appearance
hint-mode.hint.box-color=#000000
hint-mode.hint.box-opacity=0.4
hint-mode.hint.box-border-thickness=1
hint-mode.hint.box-border-length=1000
hint-mode.hint.box-border-color=#FFFFFF
hint-mode.hint.box-border-opacity=0.4
hint-mode.hint.box-width-percent=1.0
hint-mode.hint.box-height-percent=1.0

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

# Prefix box (for multi-character hints)
hint-mode.hint.prefix-box-enabled=true
hint-mode.hint.prefix-box-border-thickness=4
hint-mode.hint.prefix-box-border-color=#FFD93D
hint-mode.hint.prefix-box-border-opacity=0.8
hint-mode.hint.prefix-in-background=true

# Animation
hint-mode.hint.transition-animation-enabled=true
hint-mode.hint.transition-animation-duration-millis=100
```

- Box appearance: controls the background and border of hint boxes
  - `box-border-length`: Higher values create continuous lines between hint boxes, lower values create dotted separators

- Font appearance: controls how hint labels appear
    - `font-spacing-percent`: Controls character spacing (0=touching, 1=evenly distributed, 0.5=minimal spacing with alignment)
 
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

The font styles are divided into two categories: the main (default) font style and the optional prefix font style.  
The main font style has the following properties:
- hint.font-color
- hint.font-opacity
- hint.selected-font-color
- hint.selected-font-opacity
- hint.focused-font-color
- hint.focused-font-opacity

- hint.font-name
- hint.font-weight
- hint.font-size
- hint.font-spacing-percent
- hint.font-outline-thickness
- hint.font-outline-color
- hint.font-outline-opacity
- hint.font-shadow-blur-radius
- hint.font-shadow-color
- hint.font-shadow-opacity
- hint.font-shadow-horizontal-offset
- hint.font-shadow-vertical-offset

The prefix font style has the following properties:
- hint.prefix-font-color
- hint.prefix-font-opacity
- hint.prefix-selected-font-color
- hint.prefix-selected-font-opacity
- hint.prefix-focused-font-color
- hint.prefix-focused-font-opacity

If the prefix is in the background, then the prefix font style has additional properties:
- hint.prefix-font-name
- hint.prefix-font-weight
- hint.prefix-font-size
- hint.prefix-font-spacing-percent
- hint.prefix-font-outline-thickness
- hint.prefix-font-outline-color
- hint.prefix-font-outline-opacity
- hint.prefix-font-shadow-blur-radius
- hint.prefix-font-shadow-color
- hint.prefix-font-shadow-opacity
- hint.prefix-font-shadow-horizontal-offset
- hint.prefix-font-shadow-vertical-offset

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
hint2-mode.hint.active-screen-grid-area-center=last-selected-hint
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

### Grid navigation workflow

A typical grid navigation workflow:

1. Enter grid mode (divides screen into a 2×2 grid)
2. Use shrink commands to narrow down to the target area
3. Use snap commands for edge-precise positioning or center command for center positioning
4. Exit grid mode when the mouse is at the desired position

### Example grid command configuration

```properties
# Enter grid mode from normal mode
normal-mode.to.grid-mode=+g

# Option 1: Grid navigation with WASD keys
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
# Option 2: Grid navigation with arrow keys
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
max-position-history-size=16

# Define a key to save the current mouse position
normal-mode.position-history.save-position=+f1

# Define a key to clear all saved positions
normal-mode.position-history.clear-positions=+f2
```

- **`max-position-history-size`**: Maximum number of positions that can be stored (older positions are removed when this limit is reached)
- **`save-position`**: Command to save the current mouse position to history
- **`clear-positions`**: Command to clear all saved positions

### Using position history with hints

Position history works in conjunction with a hint mode of type `position-history`:

```properties
# Configure a hint mode to use position history
position-hint-mode.hint.type=position-history
position-hint-mode.hint.selection-keys=a b c d e f g h i j k l m n o p q r s t u v w x y z
position-hint-mode.to.normal-mode=+selection

# Switch to position hint mode
normal-mode.to.position-hint-mode=+p
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
normal-mode.position-history.clear-positions=+f2
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
keyboard-layout=us-dvorak
```
The following keyboard layouts are currently exposed and can be referenced in the configuration file:
- zh-qwerty-pinyin
- fr-azerty
- de-qwertz
- it-qwerty
- jp-kana
- pt-qwerty-abnt2
- ru-jcuken
- es-qwerty
- us-qwerty
- uk-qwerty
- us-dvorak
- us-halmak
- sv-qwerty
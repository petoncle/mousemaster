### Key aliases

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

### Combos

1. The combo sequence `+leftctrl +e` means leftctrl must be pressed then e must be
   pressed.
2. The `+` in `+leftctrl` and `+e` means that the key press should be eaten by
   mousemaster: it will not be sent to the other applications.
3. `+leftctrl` is the same as, and a shorter version of `+leftctrl-0-250` which means that
   leftctrl must be pressed for a duration between 0 and 250 milliseconds. This means that
   mousemaster should not consider the combo as complete if you press leftctrl for longer
   than 250ms. The default combo move duration can be changed with:
    ```properties
    default-combo-move-duration-millis=0-250
    ```
4. `+leftctrl-0` means that leftctrl must be pressed for at least 0ms, with no upper
   limit (it can be pressed for as long as you want).
5. `-leftctrl` corresponds to a release of a leftctrl key press.
6. `#leftctrl` is similar to `+leftctrl` because it corresponds to a key press.
   Unlike `+`, `#` tells mousemaster not to eat the key press event: other apps can
   receive the event.
7. The combo `_{leftctrl} +e` has two parts: a precondition (`_{leftctrl}`), and a
   sequence (`+e`). The precondition part means that (a) leftctrl must be pressed before
   the combo sequence is performed, (b) leftctrl must remain pressed during the entire
   combo sequence, and (c) leftctrl will not be eaten by mousemaster.
8. There is another type of combo precondition, `^{leftctrl}`, which means that (a)
   leftctrl must *not* be pressed before the combo sequence is performed and (b) leftctrl
   must remain *un*pressed during the entire combo sequence.

### Multi-combo
One command can be assigned multiple combo. As soon as one of the combos is completed, then the command is executed. The combos must be separated by `|`:
```properties
normal-mode.to.idle-mode=+exit | -clickthendisable
```

### Modes

Most properties are defined per-mode. The exceptions are key aliases, app aliases and a
couple of mode-independent properties.  
Modes can share properties. For example, let's assume the normal-mode has defined what the indicator should look like:
```properties
normal-mode.indicator.enabled=true
normal-mode.indicator.idle-color=#FF0000
normal-mode.indicator.move-color=#FF0000
normal-mode.indicator.wheel-color=#FFFF00
normal-mode.indicator.mouse-press-color=#00FF00
```

If you are creating another mode and want this new mode to have the same indicator configuration, it can be done like so:
```properties
slow-mode.indicator=normal-mode.indicator
```

### Switching modes
```properties
idle-mode.to.normal-mode=_{leftctrl} +e
normal-mode.to.idle-mode=+q
```

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

### Hint properties

```properties
hint1-mode.hint.type=grid
hint1-mode.hint.grid-area=active-screen
hint1-mode.hint.active-screen-grid-area-center=screen-center
hint1-mode.hint.grid-cell-width=73
hint1-mode.hint.grid-cell-height=41
hint1-mode.hint.selection-keys=a b c d e f g h i j k l m n o p q r s t u v w x y z
hint1-mode.hint.undo=backspace
hint1-mode.hint.mode-after-selection=normal-mode
hint1-mode.hint.move-mouse=false
hint1-mode.hint.swallow-hint-end-key-press=true
hint1-mode.font-name=Consolas
hint1-mode.font-size=18
hint1-mode.font-color=#FFFFFF
hint1-mode.font-opacity=1
hint1-mode.prefix-font-color=#A3A3A3
hint1-mode.highlight-font-scale=1
hint1-mode.box-color=#000000
hint1-mode.box-opacity=0.4
hint1-mode.box-border-thickness=1
hint1-mode.box-outline-color=#FFFFFF
hint1-mode.box-outline-opacity=0.4
```

- `type` can either be `grid` or `position-history`.
- `grid-area` can either be `active-screen`, `active-window` or `all-screens`.
- `active-screen-grid-area-center` can either be `screen-center`, `mouse`
  or `last-selected-hint`.
- `move-mouse` can be set to false to not move the mouse once a hint is selected. This can
  be used when the next mode is going to be a smaller hint grid, and the mouse should not
  be moved yet (it should be moved only once a hint of the second, smaller hint grid is selected).
- If `swallow-hint-end-key-press` is false, then the last key press of the selection of a
  hint (e.g. B in AB) will be passed to the next mode which can trigger a command.
- `prefix-font-color` is the color of the letters of the hint that have already been selected.
- `highlight-font-scale` can be used to make the next letter that needs to be pressed bigger.

### Grid properties (not to be confused with the hint grid)

```properties
screen-grid-mode.grid.area=active-screen
screen-grid-mode.grid.area-width-percent=1.0
screen-grid-mode.grid.area-height-percent=1.0
screen-grid-mode.area-top-inset=15
screen-grid-mode.area-bottom-inset=0
screen-grid-mode.area-left-inset=0
screen-grid-mode.area-right-inset=0
screen-grid-mode.grid.synchronization=mouse-follows-grid-center
screen-grid-mode.grid.row-count=2
screen-grid-mode.grid.column-count=2
screen-grid-mode.grid.line-visible=true
screen-grid-mode.grid.line-color=#FF0000
screen-grid-mode.grid.line-thickness=1
```

- `grid-area` can either be `active-screen` or `active-window`.
- `synchronization` can be:
  - `mouse-follows-grid-center`
  - `grid-center-follows-mouse`
  - `mouse-and-grid-center-unsynchronized`
- Insets can be used to reduce the size of the grid from the edges. For example, a top
  inset in a window grid allows quick access to a window's title bar (to potentially grab
  it and move the window).

### Grid commands

```properties
screen-grid-mode.move-grid.up=^{leftctrl} _{leftshift} +up
screen-grid-mode.move-grid.down=^{leftctrl} _{leftshift} +down
screen-grid-mode.move-grid.left=^{leftctrl} _{leftshift} +left
screen-grid-mode.move-grid.right=^{leftctrl} _{leftshift} +right
screen-grid-mode.snap.up=^{leftshift} _{leftctrl} +up
screen-grid-mode.snap.down=^{leftshift} _{leftctrl} +down
screen-grid-mode.snap.left=^{leftshift} _{leftctrl} +left
screen-grid-mode.snap.right=^{leftshift} _{leftctrl} +right
screen-grid-mode.shrink-grid.up=^{leftshift leftctrl} +up
screen-grid-mode.shrink-grid.down=^{leftshift leftctrl} +down
screen-grid-mode.shrink-grid.left=^{leftshift leftctrl} +left
screen-grid-mode.shrink-grid.right=^{leftshift leftctrl} +right
screen-grid-mode.move-to-grid-center=+leftshift -leftshift
```
- Grid move commands shift the position of the entire grid in one direction.
- Grid snap commands move the mouse to edge of the closest grid cell.
- Grid shrink commands divide the size of the grid by 2.
- `move-to-grid-center` is for moving the mouse to the center of the grid.

### App aliases
App aliases can be defined just like key aliases. They can be used as part of a combo:
```properties
app-alias.hibernateapp=firefox.exe chrome.exe
idle-mode.to.hibernate-mode=_{hibernateapp}
normal-mode.to.hibernate-mode=_{hibernateapp}
hibernate-mode.to.idle-mode=^{hibernateapp}
```

This creates a mode called hibernate-mode that will be switched to whenever either Firefox
or Chrome is the active application. Once neither is the active application, the mode is
switched to idle-mode.

### Position history

```properties
max-position-history-size=16
```

This is used in conjunction with a hint mode of type `position-history`. For each position
saved in the position history, one hint box will be displayed.

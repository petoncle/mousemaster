# Author's configuration of mousemaster ([author.properties](author.properties))

(Refer to [configuration-reference.md](configuration-reference.md) for documentation on the complete list of configuration properties.)

## Overview

- Designed for controlling the mouse with the right hand only.
- Normal mode, grid mode, and hint mode can be used entirely with the right hand, without moving it.
- Normal mode automatically exits (to idle mode) after 5s of inactivity.
- Normal mode automatically exits when any non-normal mode key is pressed (e.g. when starting to type).
- To be used in conjunction with Alt Tab Terminator for a better Win+Tab task view.
- (The author uses rightalt + space as the PowerToys Run activation shortcut.)

## Key Aliases

| Alias | Keys |
|-------|------|
| Direction keys (up/down/left/right) | _i_ / _k_ / _j_ / _l_ |
| Left button | _space_ |
| Middle button | _;_ _'_ _#_ |
| Right button | _p_ _\[_ _\]_ |
| Hint keys (level 1) | _i_ _j_ _k_ _l_ _m_ _o_ |
| Hint back | _backspace_ _h_ |
| Surgical/slow key | _n_ |
| Hint no-move key | _/_ _n_ |
| Hint scroll | _b_ |
| Position history key | _m_ |

## Idle mode (starting mode)

- Tap _rightalt_ → normal mode.
- Hold _rightalt_ for 250ms → screen grid mode.
- _space_ then _l_ → close active window (via alt+space menu).
- _space_ then _k_ → minimize active window.
- _space_ then _i_ → Win+Tab task view.
- _rightshift_ tap → UI hint mode.
- _; (middlebutton)_ + _m_ or _n_ → UI hint mode.
- Start typing any letter → typing mode (prevents interference with normal typing).

## Normal mode (tap _rightalt_ from idle)

### Mouse movement
- Move mouse with _i_, _j_, _k_, _l_.
- Hold _rightalt_ while moving → fast mouse mode (higher velocity).
- Hold _n_ → surgical/slow mode (very slow movement + 2x zoom).

### Mouse buttons
- _space_ = left click, _;_ _'_ _#_ = middle click, _p_ _[_ _]_ = right click.
- Left and right buttons toggle (remain pressed) if held for more than 250ms.
- Ctrl-click: hold _leftctrl_ then click.

### Scrolling
- Double-tap a direction key → wheel mode (continuous scrolling in that direction).
- In wheel mode: hold _rightalt_ → fast wheel mode; hold _space_ → superfast wheel mode.

### Screen navigation
- Press _rightalt_ (no direction key held) → enters a transient snap state:
  - Release _rightalt_ within 250ms → hint mode (hint3-1-then-click).
  - Press a direction key → screen snap mode (snap to screen edges).
  - Press a middle button key → window snap mode (snap within active window).
  - Wait 250ms → screen grid mode.
- Hold _m_ → position history mode (jump to saved positions).
- Copy with _c_ (sends Ctrl+C), paste with _v_ (sends Ctrl+V).

### Exiting
- _rightctrl_ or _backspace_/_h_ → idle mode.
- Pressing any unhandled key → idle mode.
- 5s timeout with no activity → idle mode.

## Hint mode (3-level progressive zoom)

The hint system uses a 3-level zoom progression. At each level, selecting a hint key zooms into a
smaller area centered on the selection, with finer-grained hints.

### Level 1 — large grid (~6x6 cells covering the screen)
- Grid cells: 320x180px (scaled per screen DPI).
- Each cell has a 2x2 subgrid with visible borders.
- Hint keys: _i_, _j_, _k_, _l_, _m_, _o_ (right hand only).
- Font: Consolas 72pt, yellow on semi-transparent black boxes.
- Selecting a hint key → level 2.

### Level 2 — medium grid (centered on level 1 selection)
- Grid cells: ~53x30px, font size 15, no shadow.
- Up to 6x6 hints centered on the last selected hint.
- Selecting a hint key → level 3.
- _backspace_/_h_ → back to level 1.

### Level 3 — fine grid (centered on level 2 selection, with 30x zoom)
- Same grid dimensions as level 2, with 30x zoom overlay.
- Selecting a hint key → performs a left click and enters click-after-hint state.
- _backspace_/_h_ → back to level 2.

### Click behavior
- By default, selecting a final hint performs a left click.
- Hold a mouse button key before/during hint selection to change click type:
  - _space_ (left), _;_ (middle), _p_ (right). The hint box border color changes to indicate:
    yellow = left, magenta = middle, cyan = right.
- After clicking, the hint key can be pressed again within 250ms to double-click.
- Holding the hint key for 250ms after selection toggles the button (drag mode).
- Hold _/_ or _n_ (no-move key) to prevent the mouse from following the grid center during
  selection; mouse jumps only on final selection.

### Scrolling in hint mode
- Hold _b_ + direction key → scroll while hints are displayed.

### Exiting
- _esc_, _backspace_/_h_ (at level 1), or _rightctrl_ → idle mode.
- _rightalt_ tap → normal mode.

## Grid mode (hold _rightalt_ for 250ms from idle or normal)

### Screen grid
- Divides the active screen into a 2x2 grid, mouse follows grid center.
- Press a direction key → refine grid (select quadrant in that direction).
- _leftshift_ + direction → move grid.
- _leftctrl_ + direction → snap mouse to grid edge.
- Direction key only → shrink grid in that direction.
- _leftshift_ tap → move mouse to grid center.
- Middle button key → switch to window grid.
- Right button key → switch to screen snap.

### Window grid
- Same as screen grid but confined to the active window.
- Middle button key → switch to screen grid.
- Right button key → switch to screen snap.

### Exiting
- Release _rightalt_ → normal mode (if a direction key is held, starts moving).

## Snap modes

### Screen snap
- Enter from normal mode: press _rightalt_, then a direction key. Or press _rightalt_ + _p_ from idle.
- Quickly snap the mouse to screen edges using direction keys.
- Right button key → screen grid mode.
- Middle button key → window snap mode.

### Window snap
- Enter from normal mode: press _rightalt_ + middle button key. Or press _rightalt_ + middle button key from idle.
- Snap within the active window (with 15px top inset to reach title bars).
- Middle button key → window grid mode.
- Right button key → screen snap mode.

## Surgical mode (hold _n_ in normal mode)

- Very slow mouse movement (max velocity 75 vs normal 1125).
- 2x zoom centered on the mouse cursor.
- Mouse buttons work the same as normal mode.
- Release _n_ → normal mode.

## Position history mode (hold _m_ in normal mode)

- Displays saved mouse positions as hints (up to 16).
- Uses Consolas 20pt font, 4-key selection (_i_ _j_ _k_ _l_).
- _rightalt_ tap → save current position.
- _rightalt_ hold 500ms → clear all positions.
- Hint key tap → jump to saved position.
- Hint key hold 500ms → remove saved position.
- Release _m_ → return to previous mode.

## UI hint mode (_rightshift_ tap, or _;_ + _m_/_n_)

- Displays labels on detected UI elements (buttons, links, etc.) for direct targeting.
- Selection keys: _i_ _j_ _k_ _l_ _m_ _o_ _n_.
- Font: Consolas 10pt bold, white on blue boxes (#204E8A) with yellow border.
- Mouse follows selected hint.
- Selecting a hint performs the appropriate click (left/middle/right depending on held button).
- Hold _rightalt_ to browse hints without clicking.
- After clicking, pressing the hint key again within 250ms repeats the click.
- Holding the hint key for 250ms toggles the button (drag mode).

## Arrow key mode (_;_ + _i_/_j_/_k_/_l_)

- Hold _;_ (middlebutton) and press _i_/_j_/_k_/_l_ → emits arrow keys.
- _space_ in this mode → Enter key.
- Modifiers (_leftshift_, _leftctrl_, etc.) work normally for Shift+Arrow, Ctrl+Arrow, etc.
- Release _;_ → return to previous mode.

## Typing mode (auto-detected)

- Triggered when typing any letter in idle mode.
- Prevents interference with macros that use _space_ + direction keys.
- Automatically returns to idle mode after 500ms of no typing.
- _rightalt_ tap → normal mode.
- _;_ → idle mode.

## Macro hint mode

- Invisible hint grid used by macros.
- 4x10 layout, 40 hint keys covering the full keyboard.

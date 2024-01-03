# mouseable-style configuration for mousemaster ([mouseable.properties](mouseable.properties))

## Overview

- Designed to mimic the features of the [mouseable](https://github.com/wirekang/mouseable/) project.
- 3 main modes: normal, hint, and grid mode.
- Normal mode uses vi-like bindings for local mouse movement.
- Hint and grid modes assist in larger screen movements and are used with normal mode for comprehensive navigation.

## Normal Mode (_leftctrl, leftctrl_)

- Used for short-distance mouse manipulation.
- Useful for manipulating menus and selecting text.
- Vi-like behavior with directional keys (_h_, _j_, _k_, _l_) for continuous mouse movement.
- Press mouse buttons with _a_ (left button), _s_ (middle button), _d_ (right button).
- Warp to screen edges with _leftalt + h_, _leftalt + j_, _leftalt + k_, _leftalt + l_.
- Warp to screen center with _leftshift + m_.
- Scroll (wheel) with _y_, _u_, _i_, _o_.
- Jump forward (teleport) by holding _f_ while moving the mouse.
- Slow mouse movement by holding _space_ (sniper mode).
- Exit with _;_ or _esc_.

## Hint Mode (_leftalt, leftalt_)

- Display labels on the screen for direct mouse warping.
- Similar to Vimium-like browser extensions, but applicable to the entire screen.
- Switch back to the previous mode (idle mode or normal mode) after hint selection.
- Trigger a left button click by holding _leftshift_ while selecting a hint.
- Trigger a second hint pass with a smaller hint grid centered around the mouse by holding _leftctrl_ while selecting a hint.
- A balance between hint size, number and screen space is crucial and can be configured: see `hint.font-size`, `hint.grid-max-column-count`, and `hint.grid-cell-width` in [mouseable.properties](configuration/mouseable.properties).
- Exit with _leftalt_, _esc_, or _backspace_.

## Grid Mode (_leftshift, leftshift_)

- Divide screen into a 2x2 grid, refining target area with each key press.
- Move mouse to the middle of the targeted grid section.
- Shrink the grid in one direction with _h_, _j_, _k_, _l_.
- Move the grid in one direction with _leftshift + h_, _leftshift + j_, _leftshift + k_, _leftshift + l_.
- Exit with _leftshift_, _;_, or _esc_.
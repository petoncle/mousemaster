# neo-mousekeys configuration for mousemaster ([neo-mousekeys.properties](neo-mousekeys.properties))

## Overview

- Press _leftctrl + e_ to activate.
- Press _w_, _a_, _s_, _d_ to move the mouse.
- Press _q_ or _p_ to deactivate.

![neo-mousekeys layout](https://github.com/petoncle/mousemaster/assets/39304282/c1648953-8acb-49e3-abb5-8106b7502105)

## Normal Mode (_leftctrl + e_)

- Press mouse buttons with _k_ (left button), _;_ (middle button), _l_ (right button).
- Toggle mouse buttons with _n_ (left button) and _y_ (right button).
- Left click then deactivate with _._
- Jump to screen edges with _rightalt + w_, _rightalt + a_, _rightalt + s_, _rightalt + d_.
- Jump to screen center with _h_.
- Scroll vertically or horizontally (wheel) with _m_, _,_ (comma), _i_, _o_.
- Jump forward (teleport) by holding _j_ while moving.
- Slow down mouse and scroll movement by holding _leftshift_ while moving.
- Accelerate mouse movement by holding _u_ while moving.
- Accelerate scroll movement by holding _v_ or _b_ while scrolling.

## Grid Mode (_g_ in normal mode)

- Divide screen into a 2x2 grid, refining target area with each key press.
- Move mouse to the middle of the targeted grid section.
- Shrink the grid in one direction with _w_, _a_, _s_, _d_.
- Go back to normal mode with _g_ or _esc_.

## Hint Mode (_f_ in normal mode)

- Display labels on the screen for direct mouse warping.
- Similar to Vimium-like browser extensions, but applicable to the entire screen.
- Trigger a second hint pass with a smaller hint grid centered around the mouse by holding _leftshift_ while selecting a hint.
- A balance between hint size, number and screen space is crucial and can be configured: see `hint.font-size`, `hint.grid-max-column-count`, and `hint.grid-cell-width` in [neo-mousekeys.properties](configuration/neo-mousekeys.properties).
- Go back to normal mode with _esc_ or _backspace_.

## Screen Selection Mode (_c_ in normal mode)

- Display one large hint label on each screen for quickly moving from one screen to another.
- Go back to normal mode with _c_, _esc_ or _backspace.
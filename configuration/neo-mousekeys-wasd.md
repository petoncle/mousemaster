# neo-mousekeys-wasd configuration for mousemaster ([neo-mousekeys-wasd.properties](neo-mousekeys-wasd.properties))

(Refer to [configuration-reference.md](configuration-reference.md) for documentation on the complete list of configuration properties.)

## Overview

- Press _leftalt + e_ or _leftalt + capslock_ to activate.
- Press _w_, _a_, _s_, _d_ to move the mouse.
- Press _q_ or _p_ to deactivate.

![neo-mousekeys-wasd layout](https://github.com/user-attachments/assets/4aff87f3-7724-440c-aff2-ae58396e4bb9)

## Normal Mode (hold _leftalt_ then press _e_, or hold _leftalt_ then press _capslock_)

- Press mouse buttons with _k_ (left button), _;_ (middle button), _l_ (right button).
- Toggle left mouse button with _n_.
- Left click then deactivate with _._
- Jump to screen edges with _rightalt + w_, _rightalt + a_, _rightalt + s_, _rightalt + d_.
- Scroll vertically or horizontally (wheel) with _m_, _,_ (comma), _i_, _o_.
- Jump forward (teleport) by holding _j_ while moving.
- Slow down mouse and scroll movement by holding _leftshift_ while moving.
- Super slow down mouse and scroll movement by holding _capslock_ while moving.
- Accelerate mouse movement by holding _u_ while moving.
- Accelerate scroll movement by holding _v_ or _b_ while scrolling.

## Key remappings
- Press _leftalt + ijkl_ to simulate the arrow keys.
- Navigate back and forward using _h_ (back) and _y_ (forward). These keys send 
_leftalt + leftarrow_ (for back) and _leftalt + rightarrow_ (for forward) to the active application. 
- Press _tab + jl_ to switch to the virtual desktop on the left (_j_) or right (_l_).

## Grid Mode (_g_ in normal mode)

- Divide screen into a 2x2 grid, refining target area with each key press.
- Move mouse to the middle of the targeted grid section.
- Shrink the grid in one direction with _w_, _a_, _s_, _d_.
- Go back to normal mode with _g_ or _esc_.

## Window Mode (hold _leftshift_ then press _g_ in normal mode)

- Move mouse to the active window's edges with direction keys.
- Move mouse to the center of the active window with _g_.
- Go back to normal mode by releasing _leftshift_.

## Hint Mode (_f_ in normal mode)

- Display labels on the screen for direct mouse warping.
- Similar to Vimium-like browser extensions, but applicable to the entire screen.
- Trigger a second hint pass with a smaller hint grid centered around the mouse by holding _leftshift_ while selecting a hint.
- Undo an accidental key press with _backspace_.
- A balance between hint size, number and screen space is crucial and can be configured: see `hint.font-size`, `hint.grid-max-column-count`, and `hint.grid-cell-width` in [neo-mousekeys-wasd.properties](neo-mousekeys-wasd.properties).
- Go back to normal mode with _esc_ or _backspace_.

## Screen Selection Mode (_c_ in normal mode)

- Display one large hint label on each screen for quickly moving from one screen to another.
- Go back to normal mode with _c_, _esc_ or _backspace_.
# warpd configuration for mousemaster ([warpd.properties](warpd.properties))

(Refer to [configuration-reference.md](configuration-reference.md) for documentation on the complete list of configuration properties.)

## Overview

- Designed to mimic the features of the [warpd](https://github.com/rvaiya/warpd) project.
- 3 main modes: normal, hint, and grid mode.
- Normal mode uses vi-like bindings for local mouse movement.
- Hint and grid modes assist in larger screen movements and are used with normal mode for comprehensive navigation.

## Normal Mode (_leftalt + c_)

- Used for short-distance mouse manipulation.
- Useful for manipulating menus and selecting text.
- Vi-like behavior with directional keys (_h_, _j_, _k_, _l_) for continuous mouse movement.
- Press mouse buttons with _m_ (left button), _,_ (middle button), _._ (right button).
- Press mouse buttons then exit ("oneshot" clicks) with _n_ (left button), _-_ (middle button), _/_ (right button).
- Warp to screen edges with _leftshift + h_, _leftshift + j_, _leftshift + k_, _leftshift + l_.
- Scroll vertically or horizontally (wheel) with _w_, _e_, _r_, _t_.
- Slow down mouse movement (decelerate) by holding _d_ while moving the mouse.
- Accelerate mouse movement by holding _a_ while moving the mouse.
- Exit with _esc_.

## Hint Mode (_leftalt + x_ or _x_ in normal mode)

- Display labels on the screen for direct mouse warping.
- Similar to Vimium-like browser extensions, but applicable to the entire screen.
- Automatically switch back to the previous mode (idle mode or normal mode) after hint selection.
- Trigger a second hint pass with a smaller hint grid centered around the mouse with _leftalt + leftshift + x_ (or _leftshift + x_ in normal mode).
- A balance between hint size, number and screen space is crucial and can be configured: see `hint.font-size`, `hint.grid-max-column-count`, and `hint.grid-cell-width` in [warpd.properties](warpd.properties).
- Exit with _esc_.

## History Mode (_;_ in normal mode)

- Similar to hint mode, but shows hints over previous mouse positions.
- Exit with _esc_.

## Grid Mode (_leftalt + g_ or _g_ in normal mode)

- Divide screen into a 2x2 grid, refining target area with each key press.
- Move mouse to the middle of the targeted grid section.
- Shrink the grid to one quadrant with _u_, _i_, _j_, _k_ (see illustration below).
- Move the grid in one direction with _w_, _a_, _s_, _d_.
- Switch to normal mode with _c_.
- Exit (to idle mode) with _esc_.

```
                 +--------+--------+            +--------+--------+
                 |        |        |            |  u |  i |       |
                 |   u    |   i    |            |----m----+       |
 leftalt + g     |        |        |     u      |  j |  k |       |
------------->   +--------m--------+   ----->   +---------+       |
                 |        |        |            |                 |
                 |   j    |   k    |            |                 |
                 |        |        |            |                 |
                 +--------+--------+            +--------+--------+
```

## Screen Selection Mode (_leftalt + s_ or _s_ in normal mode)

- Provide hints for moving the mouse to the center of a monitor.
- Suitable for multi-screen setups.
- Exit with _esc_.

## Drag Mode (_v_ in normal mode)

- Allow for selecting text and dragging objects around.
- Switch to normal mode with _v_ or a mouse button click (_m_, _,_, _._).
- Exit (to idle mode) with _esc_.
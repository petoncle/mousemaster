# warpd-style configuration for mousemaster (warpd.properties)

## Overview

- 3 main modes: normal, hint, and grid mode.
- Normal mode uses vi-like bindings for local mouse movement.
- Hint and grid modes assist in larger screen movements and are used with normal mode for comprehensive navigation.
- See [warpd.properties](warpd.properties)

## Normal Mode (_leftalt + c_)

- Designed for short-distance mouse manipulation.
- Useful for manipulating menus and selecting text.
- Vi-like behavior with directional keys (_h_, _j_, _k_, _l_) for continuous mouse movement.
- Warps to screen edges with top (_leftshift + h_), middle (_leftshift + m_), bottom (_leftshift + l_), left (_0_), and right (_leftshift + 4_) combos.
- Scrolls (wheel) vertically with _r_ and _e_.
- Scrolls (wheel) horizontally with _w_ and _t_.

## Hint Mode (_leftalt + x_ or _x_ in normal mode)

- Displays labels on the screen for direct mouse warping.
- Similar to Vimium-like browser extensions, but applicable to the entire screen.
- Switches back to the previous mode (idle mode or normal mode) after hint selection.
- Balance between hint size, number and screen space is crucial and can be configured (see `hint.font-size` and `hint.grid-cell-width` in [warpd.properties](configuration/warpd.properties)).
- Two-phase hint mode available with _leftalt + leftshift + x_ (or _leftshift + x_ in normal mode).

## History Mode (_;_ in normal mode)

- Similar to hint mode, but only shows hints over previously selected targets.

## Grid Mode (_leftalt + g_ or _g_ in normal mode)

- Divides screen into a 2x2 grid, refining target area with each key press.
- Mouse moves to the middle of the targeted grid section.

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

- Provides hints for moving the mouse to the center of a monitor.
- Suitable for multi-screen setups.

## Drag Mode

- Activated by pressing _v_ in normal mode.
- Allows for dragging text or objects around the screen.
- The operation ends with a second press of the drag key or a mouse button click.
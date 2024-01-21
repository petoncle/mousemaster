# Author's configuration of mousemaster ([author.properties](author.properties))

## Overview

- Designed for controlling the mouse with a single hand (right hand).
- 3 main modes: normal, hint, and grid mode.
- Normal mode uses arrow keys for local mouse movement.
- Normal mode, grid mode, and hint mode can be used (almost) entirely with a single hand.
- Normal mode automatically exits (to idle mode) after 5s of inactivity.
- Normal mode automatically exits when any non-normal mode key is pressed. For example,
  when starting to type text while normal mode is active, the normal mode will automatically exit.
- The author is using this configuration with a small QWERTY keyboard and one 1920x1080 screen with a 125% display scale.
- This single hand configuration works well only if the following keys are all within the
  reach of the right hand: arrow keys, right shift, right ctrl, right alt.

## Normal Mode (_rightctrl_)

- Move mouse with arrow keys.
- Press mouse buttons with _rightshift_ (left button), _._ (middle button), _/_ (right
  button).
- Left and right mouse buttons will be toggled (and remain pressed) if _rightshift_ (left
  button) or _/_ (right button) are held for more than 250ms. 
- Snap to screen edges by holding _rightctrl_, then quickly pressing an arrow key.
- Snap to screen center by holding _rightctrl_ for 250ms.
- Scroll vertically or horizontally (wheel) by double pressing the arrow keys.
- Jump forward by pressing _rightctrl_ while moving the mouse.
- Slow down mouse movement (surgical mode) by holding _rightalt_ while moving the mouse or while scrolling.
- Exit with _rightctrl_ or _esc_.

## Hint Mode (_rightalt_)

- Display labels on the screen for direct mouse warping.
- Similar to Vimium-like browser extensions, but applicable to the entire screen.
- Automatically switch back to the previous mode (idle mode or normal mode) after hint selection.
- Trigger a left button click by holding _space_ while selecting a hint.
- Trigger a second hint pass with a smaller hint grid centered around the mouse by holding _rightalt_ while selecting a hint. 
Release _rightalt_ during the second hint pass to not trigger a click after hint selection.
- Double click by pressing the hint key a second time after selecting the hint.
- Only the characters _u, i, h, j, k, n, m_ are used for hint labels to be able to select hints with one hand.
- Exit with _rightalt_, _esc_ or _backspace_.

## Position History Mode (_leftalt_)

- Similar to hint mode, but shows hints over previous mouse positions.
- Only the last two positions are saved (it seems I do not need more).
- Clear the position history by holding _leftalt_ for 1000ms (1s).
- Cycle through the position history with _rightalt + left/right_.
- Exit with _leftalt_, _esc_ or _backspace_.

## Grid Mode (hold _rightctrl_)

- Divide screen into a 2x2 grid, refining target area with each key press.
- Switch between screen grid and window grid by pressing _rightshift_.
- Shrink the grid in one direction with the arrow keys.
- Move the grid in one direction with _leftshift + \<arrow key>_.
- Snap to the grid edges with _leftctrl + \<arrow key>_.
- Switch to normal mode by releasing _rightctrl_.

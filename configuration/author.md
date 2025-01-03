# Author's configuration of mousemaster ([author.properties](author.properties))

(Refer to [configuration-reference.md](configuration-reference.md) for documentation on the complete list of configuration properties.)

## Overview

- Designed for controlling the mouse with the right hand only.
- 3 main modes: normal, hint, and grid mode.
- Normal mode uses _i_, _j_, _k_, _l_ for local mouse movement.
- Normal mode, grid mode, and hint mode can be used entirely with the right hand, without moving it.
- Normal mode automatically exits (to idle mode) after 5s of inactivity.
- Normal mode automatically exits when any non-normal mode key is pressed. For example,
  when starting to type text while normal mode is active, the normal mode will automatically exit.
- The author is using this configuration with a small QWERTY keyboard and one 1920x1080 screen.
- (The author uses rightalt + space as the PowerToys Run activation shortcut.)

## Normal Mode (_rightalt_)

- Move mouse with _i_, _j_, _k_, _l_.
- Press mouse buttons with _space_ (left button), _;_ or _\'_ or _#_ (middle button), _p_ or _\[_ or _\]_ (right
  button).
- Left and right mouse buttons will be toggled (and will remain pressed) if the left button key 
  or the right button key are held for more than 250ms. 
- Snap to screen edges by holding _rightalt_, then quickly pressing a direction key.
- Snap to screen center by holding _rightalt_ for 250ms.
- Scroll vertically or horizontally (wheel) by double pressing the direction keys.
- Jump forward by pressing _rightalt_ while moving the mouse.
- Slow down mouse movement (slow mode) by holding _n_ while moving the mouse or while scrolling.
- Exit with _rightctrl_ or by pressing any key than is not handled by this configuration.

## Hint Mode (_rightalt_ in normal mode)

- Display labels on the screen for direct mouse warping.
- Similar to Vimium-like browser extensions, but applicable to the entire screen.
- Automatically switch back to the previous mode (idle mode or normal mode) after hint selection.
- Trigger a left button click by holding _space_ while selecting a hint.
- Trigger a second hint pass with a smaller hint grid centered around the mouse by holding _rightalt_ while selecting a hint. 
Release _rightalt_ during the second hint pass to not trigger a click after hint selection.
- Double click by pressing the hint key a second time after selecting the hint.
- Only the characters _i, j, k, l, m_ are used for hint labels to be able to select hints without moving the hand.
- Exit with _rightalt_, _esc_ or _backspace_.

### Auto-zoom in hint mode
https://github.com/user-attachments/assets/c4caa091-dc1d-40d7-8ea5-f77318a9b1b3

The Windows Magnifier is used to zoom in when the second hint pass is displayed. In order
for this to work, the Magnifier settings must be changed. Go to Windows System 
settings > Magnifier settings, and change the following:
1. Zoom increment: 400%
2. View: Full screen
3. View > Have magnifier follow my: Mouse pointer
4. View > Keep the mouse pointer: Centered on screen
5. Reading shortcut: anything other than Ctrl + Alt which would interfere with _rightalt_ 
(I set it to Insert and I never use it anyway) 
6. Appearance: Smooth edges of images and text (optional)

Finally, you need to run the Windows Magnifier, unzoom it (so the zoom is 100%)
and minimize its window.  
mousemaster will trigger zoom in and zoom out commands when the second hint pass is
displayed and hidden. The Windows Magnifier must keep running in the
background (minimized) for this to work.

## Grid Mode (hold _rightalt_)

- Divide screen into a 2x2 grid, refining target area with each key press.
- Switch to window grid by pressing one of the middle button keys.
- Switch to screen grid by pressing one of the right button keys.
- Shrink the grid in one direction with the arrow keys.
- Switch to normal mode by releasing _rightalt_.

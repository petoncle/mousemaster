# neru configuration for mousemaster ([neru.properties](neru.properties))

(See [configuration-reference.md](configuration-reference.md) for the full list of properties.)

Designed to mimic the features of the [neru](https://github.com/y3owk1n/neru) project.

A keyboard-only mouse driven by hints: launch a mode, jump the cursor onto a target, click. The pattern is always **Ctrl+Shift** to start a mode, then **unmodified keys** to pick a cell, and **Shift+L/R/M** to click.

## Launch

From idle — or to switch between modes — hold **Ctrl+Shift** and press:

| Key     | Mode            |
|---------|-----------------|
| `C`     | Recursive hints |
| `S`     | Scroll          |
| `G`     | Grid hints      |
| `Space` | UI hints        |
| `M`     | Monitor         |

`Esc` returns to idle, where all keys pass through to the operating system as normal.

## In any mode

- **Move** the cursor: hold the **arrow keys** (speeds up while held).
- **Click**: `Shift+L` left, `Shift+R` right, `Shift+M` middle.
- **Drag**: hold `Shift+L` while moving — or `Shift+I` to press the left button and `Shift+U` to release it.

Clicks use Shift so they don't clash with the keys used to pick hints.

The indicator is a translucent disc centered on the cursor, ringed in the same color: red idle and moving, yellow scrolling, and while a button is held, green for left, magenta for middle, cyan for right — the last three also cast a glow in their color. The hint modes hide it while you pick, so only the hints are on screen, and show it again while a button is held.

## Recursive hints — Ctrl+Shift+C

https://github.com/user-attachments/assets/9140b3be-6109-4a90-a842-0a22c7e0e562

A 3×3 grid you drill into: each keypress narrows to that ninth of the current region. Keys map to screen position:

```
r t y
f g h      (g = center)
v b n
```

- Each cell shows a 3×3 of dots where the next level's cells will fall, under a `+` spanning the whole region. Set `variable.showrecursivehintkeys=true` in [neru.properties](neru.properties) to label those cells with the next level's keys instead.
- Five levels deep; the fifth keypress lands on the target.
- The cursor tracks the current region's center as you drill; `` ` `` (backtick) toggles that off/on.
- `Space` resets to the full screen, `Backspace` steps back one level (and exits at the top), `Esc` exits.

## Scroll — Ctrl+Shift+S

Hold to keep scrolling (speeds up while held): `k` up, `j` down, `h` left, `l` right. `Esc` exits.

## Grid hints — Ctrl+Shift+G

https://github.com/user-attachments/assets/bed8750a-928a-4989-8e5a-0bad420c4e42

Two grids, so three keystrokes land the cursor within a few pixels of anything on screen; the arrow keys cover the rest.

1. A labeled grid covers the screen — type a cell's label (two keys on most screens). Density and label length adapt to your resolution; after the first key the grid narrows to a sub-grid, with the typed key shown large behind it.
2. That cell immediately fills with a 3×8 sub-grid. One more key lands the cursor:

```
q w e r   u i o p
a s d f   j k l ;
z x c v   m , . /
```

Each sub-grid row is one keyboard row, left hand then right.

`Backspace` in the first grid undoes a keystroke, or exits if nothing is typed; in the sub-grid it returns to the first grid. `Esc` exits.

## UI hints — Ctrl+Shift+Space

https://github.com/user-attachments/assets/91eef554-bf68-44a7-8d16-5e4d5d353fe0

Labels appear on the active window's clickable elements (buttons, links, …).

- Type a label (`a s d f g h j k l`) to warp the cursor onto that element.
- `Backspace` undoes a keystroke; `Esc` or `Backspace` exits.

## Monitor — Ctrl+Shift+M

https://github.com/user-attachments/assets/e6032b9c-c674-461a-a559-2dc5afb9815f

A big label (`a s d f g h j k l`) sits on each monitor — press one to warp the cursor to that screen, handy for crossing monitors before hinting. `Esc` or `Backspace` exits.

## After a jump

Completing any hint leaves you on the target, click-ready: click with `Shift+L/R/M`, nudge with the arrows, launch another mode with `Ctrl+Shift+<key>`, or `Esc` to idle.

## Rebinding

Every key is a named alias at the top of [neru.properties](neru.properties), e.g. `key-alias.moveupkey.us-qwerty=uparrow` — change the value to remap. The selection sets (`hintkey`, `hint2key`, `recursivehintkey`, …) are aliases too; each is laid out to match the grid it fills, so resizing a grid means resizing its key set.

# Recursive hint grid

A design for a neru-style recursive grid, built out of the existing hint system
rather than the `grid-mode` shrink commands.

## Idea

A hint grid (e.g. 3x3) where selecting a cell narrows the grid to that cell and
shows the same grid again, drilling down until the cursor is on target. Each
level is a distinct mode, so the deeper levels can restyle (smaller font, hidden
labels, different colors).

This reuses the machinery mousemaster already has for multi-level hints (the
author's `hint2`/`hint3` chain), and needs only two new declarative concepts
plus a small amount of state.

## Concept 1: `HintGridArea.LastSelectedHintCellGridArea`

Today `ActiveScreenHintGridAreaCenter.LAST_SELECTED_HINT` positions a grid's
*center* on the last selected hint, but its *size* is still a fixed pixel span.
A recursive level needs the grid to cover exactly the selected cell.

`LastSelectedHintCellGridArea` is a fourth `HintGridArea`, selected with
`grid-area=last-selected-hint-cell`. Its rectangle is the last selected hint's
cell (position and size), falling back to the active screen when there is no
selection yet — mirroring how the `LAST_SELECTED_HINT` center falls back to the
mouse.

## Concept 2: `HintCellSizing` (fixed vs fit)

`HintGridLayout` today is cell-size-primary: you pin `cellWidth`/`cellHeight`
and the counts fall out of `area / cellSize`. A recursive level is the dual: you
pin the counts (3x3) and the cell size falls out of `area / count`.

Modelled as a sealed interface, mirroring `HintMeshType`:

```
sealed interface HintCellSizing {
    record FixedCellSize(double cellWidth, double cellHeight) implements HintCellSizing {}
    record FitToArea() implements HintCellSizing {}
}
```

`HintGridLayout` holds a `HintCellSizing` instead of the two top-level doubles,
so the pixel fields cannot be set in fit mode. The builder stays flat (an
`HintCellSizingType` enum plus the existing nullable `cellWidth`/`cellHeight`,
assembled in `build`), so per-property setting, inheritance, and per-resolution
overrides keep working. `maxRowCount`/`maxColumnCount` stay on `HintGridLayout`:
caps in fixed mode, exact counts in fit mode.

Selected with `grid-cell-sizing=fit` (default `fixed`).

## State: selected cell stack

`HintManager` keeps a `Deque<Rectangle> selectedCellStack`.

- On a grid hint selection, push the selected cell.
- Entering a `last-selected-hint-cell` mode: if the transition is not a forward
  selection (i.e. it is a backward move via `+backspace`), pop first; then the
  grid area is the top of the stack.
- Entering any other grid mode clears the stack.

Forward vs backward is distinguished by whether a hint was just selected
(`hintJustSelected`). This leaves `HintMeshKey` and its cross-mode grid sharing
untouched.

## Mode pattern (author.properties)

One mode per layer, inheriting a shared base:

```
_recursive-hint-mode.hint.type=grid
_recursive-hint-mode.hint.selection-keys=recgridkey
_recursive-hint-mode.hint.grid-max-row-count=3
_recursive-hint-mode.hint.grid-max-column-count=3
_recursive-hint-mode.hint.grid-cell-sizing=fit

recursive-hint1-mode=_recursive-hint-mode
recursive-hint1-mode.hint.grid-area=active-screen
idle-mode.to.recursive-hint1-mode=+f10
recursive-hint1-mode.to.recursive-hint2-mode=+recgridkey
recursive-hint1-mode.break-combo-preparation=+recgridkey
recursive-hint1-mode.to.idle-mode=+esc

recursive-hint2-mode=_recursive-hint-mode
recursive-hint2-mode.hint.grid-area=last-selected-hint-cell
recursive-hint2-mode.to.recursive-hint3-mode=+recgridkey
recursive-hint2-mode.break-combo-preparation=+recgridkey
recursive-hint2-mode.to.recursive-hint1-mode=+backspace
recursive-hint2-mode.to.idle-mode=+esc

recursive-hint3-mode=_recursive-hint-mode
recursive-hint3-mode.hint.grid-area=last-selected-hint-cell
recursive-hint3-mode.to.recursive-hint2-mode=+backspace
recursive-hint3-mode.to.idle-mode=+esc
```

Back-navigation goes one level at a time.

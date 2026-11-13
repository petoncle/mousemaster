# Neverclick configuration for mousemaster ([neverclick.properties](../configuration/neverclick.properties))

[Neverclick](https://www.neverclick.com), the mousemaster way.

https://github.com/user-attachments/assets/83378b27-45ff-47c8-bf0f-a980e5dd7b4f

## Hotkeys

From any app, a hotkey activates a tool:

| Hotkey                 | Tool                                                             |
|------------------------|------------------------------------------------------------------|
| `Ctrl+Enter`           | Left Click Tool (again in the tool: double, then triple click)   |
| `Alt+Enter`            | Left Click Tool with Ctrl held                                   |
| `Ctrl+Shift+Enter`     | Right Click Tool                                                 |
| `Alt+Shift+Enter`      | Middle Click Tool                                                |
| `Ctrl+Alt+Enter`       | Text Selection Tool: the first word, then the last               |
| `Ctrl+Alt+Shift+Enter` | Drag Tool: what to grab, then where to drop it                   |
| `Ctrl+\`               | Sticky Hints Tool                                                |

The hints mark what computer vision finds on every screen. All labels have the same length, and the labels of the first hint keys (`j f k d l s a …`) go near the center of the active window. A hotkey pressed in a tool changes the tool without a new scan.

While a tool is active, every key but Print Screen goes to mousemaster. A toast at the bottom left names each change.

![neverclick cheatsheet](assets/neverclick-cheatsheet.png)

Hold `?` in a tool to show this cheatsheet.

## Hints

- `Backspace` clears the last letter, or exits the tool. `Esc` exits the tool.
- `Enter` runs the mouse action on the picks, or again where it last ran.
- `Shift+R` scans again.
- Hint source: `;` a grid, `[` clairvoyance (the accessibility elements of the active window), `]` denser vision hints (`Shift+]` the densest), `\` the sticky spots. The same key again goes back to the vision hints, or for the grid, to the hints under it.
- Field of vision: `F1` the active window, `F2` the monitor of the pointer, `F3` every monitor, `Tab` window or all, `Shift+F` the next one. It is kept for the next activations.

## Mouse action

- `0` moves the pointer without a click. The arrows then scroll, and any other key leaves.
- `-` leaves the pointer where the tool clicked (kept for the next activations).
- `Ctrl+A`, `Ctrl+C`, `Ctrl+S` (or `,` `.` `/`) hold Alt, Ctrl, Shift during the mouse action.
- In a drag, `` ` `` makes it slow, and `Ctrl+E` drags each pick to the next one instead of all to the last (kept for the next activations).

## Picks

- `Ctrl+D` (or `'`): dual select. Two picks, then the mouse action runs.
- `Ctrl+M` (or `Shift+'`): multi select. Any number of picks, then `Enter` runs the mouse action on each one in order. A drag drags them to the last pick.
- `Ctrl+Z` undoes the last pick.

A pick stays on screen, outlined, with a dashed line to the next one.

## Targets

Each hint acts at its target, at the center of its element at first. Before you type a hint, these keys move the targets of all the hints together. A moved target shows as a dot in the color of its hint, with a line from its label.

- Arrows (or `Shift+I J K L`): the first one to the edge of the elements, then 4px steps. Hold an arrow to move the targets on after half a second. An arrow pressed during the move joins it. Hold `Ctrl` for 2px steps and half the speed.
- `1` to `9`: across the elements in eighths, from the left edge (`1`) through the center (`5`) to the right edge (`9`). `Shift` and a digit: down them.
- Numpad: the nine points of the elements.

A pick keeps its target. The next activation starts the targets at the centers again.

## Sticky Hints Tool

- `=` in a tool: the hints typed next become sticky spots (26 at most). `Enter` or `=` shows them.
- `Ctrl+\` activates the Sticky Hints Tool. It shows the sticky spots, or starts to pick them when there are none. Type the hint of a spot to click it: the tool stays active for the next click.
- In the Sticky Hints Tool, `=` goes back to the hints to add more, and `Delete` forgets them all.

## Rebinding

Every key is an alias at the top of [neverclick.properties](../configuration/neverclick.properties), e.g. `key-alias.toolkey.us-qwerty=enter`: change the value to remap, and the cheatsheet shows the new key. `hintkey` lists the hint keys in priority order. The numpad Enter does what `toolkey` does.

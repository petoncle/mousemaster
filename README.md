# mousemaster

- Control your mouse from the keyboard and remap your keys
- Heavily inspired by [warpd](https://github.com/rvaiya/warpd), [mouseable](https://github.com/wirekang/mouseable/), [neru](https://github.com/y3owk1n/neru) and [kanata](https://github.com/jtroo/kanata)

<div align="center">
  <a href="https://discord.gg/GSB6MaKb2R">
    <img src="https://img.shields.io/badge/Join%20%20The%20%20Discord%20%20Server-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Join the mousemaster Discord Server"/>
  </a>
</div>

<p align="center">
<a href="#demo">Demo</a> •
<a href="#installation">Installation</a> •
<a href="#usage">Usage</a> •
<a href="#features">Features</a> •
<a href="#contributing">Contributing</a>
</p>

## Demo

### Hints
https://github.com/user-attachments/assets/cfba2c70-7edd-44bf-b63e-ded1613368da

### Recursive hints
https://github.com/user-attachments/assets/9140b3be-6109-4a90-a842-0a22c7e0e562

### UI hints
https://github.com/user-attachments/assets/91eef554-bf68-44a7-8d16-5e4d5d353fe0

### Recursive grid
https://github.com/user-attachments/assets/b395505e-9a06-4ec8-b361-81323c7b3fb4

### Continuous mouse movement
https://github.com/petoncle/mousemaster/assets/39304282/2dadbfa0-1270-41ff-9e18-3fb3a28d5b6f

## Installation

1. Download **mousemaster.exe** (a portable executable) from the [Release page](https://github.com/petoncle/mousemaster/releases/latest), or build it from source.
2. In the same Release page, choose and download one of the existing configuration files:
   - **neo-mousekeys-ijkl.properties** (***recommended***): an IJKL configuration ([see documentation](configuration/neo-mousekeys-ijkl.md))
   - **neru.properties**: a recursive hint configuration ([see documentation](configuration/neru.md))
   - **neo-mousekeys-wasd.properties**: a WASD configuration ([see documentation](configuration/neo-mousekeys-wasd.md))
   - **warpd.properties**: an HKJL configuration ([see documentation](configuration/warpd.md))
   - **mouseable.properties**: another HKJL configuration ([see documentation](configuration/mouseable.md))
   - **author.properties**: an IJKL configuration designed to control everything with the right hand only ([see documentation](configuration/author.md))
3. Place the executable and the configuration file of your choice in the same directory.
4. Rename the configuration file to **mousemaster.properties**.
5. Run **mousemaster.exe**: make sure to run it as administrator if you want the
   mousemaster overlay to be displayed on top of everything else.
6. Feel free to open a [GitHub Issue](https://github.com/petoncle/mousemaster/issues)
   or join the [Discord](https://discord.gg/GSB6MaKb2R) if you need help creating your own
   configuration. If you have ideas for a better configuration that
   you would like to share, I'd love to hear from you.

### Optional Windows GUI configurator

A community-maintained WinForms configurator is available in
[`configurator/`](configurator/README.md). It can edit 37 keyboard and mouse actions,
mouse and wheel motion values, focus-mode input swallowing, and Alt-Tab centering while
preserving any properties and comments it does not manage. The UI is available in
Simplified Chinese and English. The configurator builds with the .NET Framework C#
compiler only (no NuGet packages) and is not part of the official MouseMaster release.

## Usage

### Default configuration (neo-mousekeys-ijkl)

The recommended configuration uses the following key bindings:

- **Activate**: Press _leftalt + e_ or _leftalt + capslock_
- **Deactivate**: Press _q_ or _p_
- **Mouse movement**: Use _i_ (up), _j_ (left), _k_ (down), _l_ (right)
- **Mouse buttons**: _;_ (left), _rightshift_ (middle), _'_ (right)
- **Grid mode**: Press _g_
- **Hint mode**: Press _f_
- **UI Hint mode**: Press _leftalt + f_
- **Screen selection**: Press _c_

![neo-mousekeys-ijkl layout](https://github.com/user-attachments/assets/5e0aa96d-96f2-4349-9b2f-26dcca4933c0)

For a complete reference, see the [neo-mousekeys-ijkl documentation](configuration/neo-mousekeys-ijkl.md).

## Features

1. **Combos and key remapping**: Define combos (key sequences, chords, timed holds, taps, tap-dances, and more) to trigger commands, switch modes, or remap keys. See the [combo reference](configuration/combo-reference.md).
2. **Modes**: Each mode has its own combos and its own hint, grid, indicator and zoom settings, and can inherit properties from other modes.
3. **Continuous mouse movement**: Move the cursor, click, scroll, and drag, all from the keyboard.
4. **Hint navigation**: Cover the screen with a grid of labeled hints, type a label to jump the cursor there.
5. **UI hint navigation**: Label buttons, links, and text fields in the active window.
6. **Recursive grid navigation**: Divide the screen into 2x2 sections, shrink with each key press or snap to the grid's edges to reach a precise position.
7. **Zoom**.
8. **App-specific behavior**: Restrict combos to specific apps, so the same keys behave differently depending on the focused app.
9. **Cursor indicator**: Custom shape, color, outline, shadow, text label, changes per mouse state.
10. **Position history**: Save cursor positions, jump back to them later.
11. **Live configuration**: All configuration lives in a single file that is automatically reloaded when saved.

mousemaster provides low-level primitives (modes, combos, commands, macros, key aliases) that you compose to build the exact behavior you want. See the [configuration reference](configuration/configuration-reference.md).

## Contributing

Contributions to mousemaster are welcome! 

- **Share a configuration**: If you have ideas for a new or improved configuration that you would like to share, open an issue or join the [Discord](https://discord.gg/GSB6MaKb2R).

- **Cross-platform support:** mousemaster currently supports Windows only. That said, the overlay has already been reimplemented to be cross-platform. The remaining work is keyboard/mouse input handling and sending inputs on macOS and Linux.  
  If you're interested in helping extend mousemaster to these platforms, your contributions are very welcome. Please open an issue or join the Discord to get involved.

If you enjoy mousemaster, consider making a [donation](https://ko-fi.com/petoncle) or stop by the Discord to show your support!

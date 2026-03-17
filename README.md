# mousemaster

<a href="https://discord.gg/GSB6MaKb2R"><img src="https://img.shields.io/discord/854326924402622474?color=%235865F2&label=discord" alt="Join Discord Chat"></a>

- Keyboard driven mouseless mouse control and advanced keyboard customization
- Heavily inspired by [warpd](https://github.com/rvaiya/warpd), [mouseable](https://github.com/wirekang/mouseable/) and [kanata](https://github.com/jtroo/kanata)

<p align="center">
<a href="#demo">Demo</a> •
<a href="#overview">Overview</a> •
<a href="#installation">Installation</a> •
<a href="#usage">Usage</a> •
<a href="#features">Features</a> •
<a href="#contributing">Contributing</a>
</p>

## Demo

### Hints Demo
https://github.com/user-attachments/assets/cfba2c70-7edd-44bf-b63e-ded1613368da

### Hint Styling Showcase
<p align="center">
  <img src="https://github.com/user-attachments/assets/6b2fb130-3213-4338-bda5-0c7c4969a433" style="width: 100%; height: 100%;" />
</p>

### UI Hints Demo
<p align="center">
  <img src="https://github.com/user-attachments/assets/adada1bb-faaa-4946-bacc-aaa6b1807597" style="width: 100%; height: 100%;" />
</p>

### Grid Demo
https://github.com/petoncle/mousemaster/assets/39304282/12677e9e-3126-4694-b4bc-5a18e9438bc9

### Mouse Movements Demo
https://github.com/petoncle/mousemaster/assets/39304282/2dadbfa0-1270-41ff-9e18-3fb3a28d5b6f

## Overview

mousemaster lets you control your mouse from the keyboard and remap your keys.

- **Continuous mouse movement**: Move the cursor with keyboard keys (IJKL by default), click, scroll, and drag
- **Hint navigation**: Cover the screen with a grid of labeled hints, type a label to jump the cursor there
- **UI hint navigation**: Label buttons, links, and text fields in the active window
- **Grid navigation**: Divide the screen into a grid that you progressively refine to narrow down to a specific area
- **Key remapping**: Define combos to remap keys, send key sequences, or type text

## Installation

1. Download **mousemaster.exe** (a portable executable) from the [Release page](https://github.com/petoncle/mousemaster/releases/latest), or build it from source.
2. In the same Release page, choose and download one of the existing configuration files:
   - **neo-mousekeys-ijkl.properties** (***recommended***): an IJKL configuration ([see documentation](configuration/neo-mousekeys-ijkl.md))
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

## Usage

### Default Configuration (neo-mousekeys-ijkl)

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

1. **Combos and key remapping**: Define combos (key sequences, chords, timed holds, taps, tap-dances, and more) to trigger commands or remap keys. See the [combo reference](configuration/combo-reference.md).
2. **Continuous mouse movement**: Move the cursor, click, scroll, and drag, all from the keyboard.
3. **Hint navigation**: Cover the screen with a grid of labeled hints, type a label to jump the cursor there.
4. **UI hint navigation**: Labels buttons, links, and text fields in the active window.
5. **Grid navigation**: Divide the screen into 2x2 sections, shrink with each key press to reach a precise position.
6. **Zoom**.
7. **App-specific modes**: Auto-switch modes based on the focused app.
8. **Cursor indicator**: Custom shape, color, outline, shadow, text label, changes per mouse state.
9. **Position history**: Save cursor positions, jump back to them later.
10. **Live configuration**: All configuration lives in a single file that is automatically reloaded when saved.

mousemaster provides low-level primitives (modes, combos, commands, macros, key aliases) that you compose to build the exact behavior you want. See the [configuration reference](configuration/configuration-reference.md).

## Contributing

Contributions to mousemaster are welcome! 

- **Share a configuration**: If you have ideas for a new or improved configuration that you would like to share, open an issue or join the [Discord](https://discord.gg/GSB6MaKb2R).

- **Cross-platform support:** mousemaster currently supports Windows only. That said, most of the overlay has been reimplemented to be cross-platform. The remaining work is keyboard/mouse input handling and sending inputs on macOS and Linux.

  If you're interested in helping extend mousemaster to these platforms, your contributions are very welcome. Please open an issue or join the Discord to get involved.

If you enjoy mousemaster, consider making a [donation](https://ko-fi.com/petoncle) or stop by the Discord to show your support!

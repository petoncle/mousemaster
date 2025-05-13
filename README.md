# mousemaster

<a href="https://discord.gg/GSB6MaKb2R"><img src="https://img.shields.io/discord/854326924402622474?color=%235865F2&label=discord" alt="Join Discord Chat"></a>

- A keyboard driven interface for mouseless mouse manipulation
- Heavily inspired by [warpd](https://github.com/rvaiya/warpd)
  and [mouseable](https://github.com/wirekang/mouseable/)

<p align="center">
<a href="#demo">Demo</a> •
<a href="#overview">Overview</a> •
<a href="#installation">Installation</a> •
<a href="#usage">Usage</a> •
<a href="#features">Features</a> •
<a href="#configuration">Configuration</a> •
<a href="#troubleshooting">Troubleshooting</a> •
<a href="#contributing">Contributing</a>
</p>

## Demo

### Hints Demo
https://github.com/user-attachments/assets/cfba2c70-7edd-44bf-b63e-ded1613368da

### Hint Styling Showcase
<p align="center">
  <img src="https://github.com/user-attachments/assets/6b2fb130-3213-4338-bda5-0c7c4969a433" style="width: 100%; height: 100%;" />
</p>

### Grid Demo
https://github.com/petoncle/mousemaster/assets/39304282/12677e9e-3126-4694-b4bc-5a18e9438bc9

### Mouse Movements Demo
https://github.com/petoncle/mousemaster/assets/39304282/2dadbfa0-1270-41ff-9e18-3fb3a28d5b6f

## Overview

mousemaster allows you to control your mouse cursor using only your keyboard. It provides multiple navigation methods:

- **Normal mode**: Move the mouse cursor with keyboard keys (IJKL by default)
- **Hint mode**: Display labels across the screen for direct cursor warping
- **Screen selection mode**: Quickly move between multiple monitors
- **Grid mode**: Divide the screen into a grid and refine your selection to move the cursor

All modes and key bindings are fully customizable through a configuration file.

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
- **Screen selection**: Press _c_

![neo-mousekeys-ijkl layout](https://github.com/user-attachments/assets/5e0aa96d-96f2-4349-9b2f-26dcca4933c0)

For a complete reference, see the [neo-mousekeys-ijkl documentation](configuration/neo-mousekeys-ijkl.md).

## Features

1. **Key combination system**: Supports complex key combinations including timed holds (e.g., "hold alt for 1 second"), sequential presses (e.g., "press then release alt, twice in a row"), and nested combinations (e.g., "hold alt while pressing A then B").

2. **Mode-based architecture**: Implements a mode system where each mode has its own key-to-command mappings and mouse settings. The default "idle-mode" represents the disabled state with no active key mappings.

3. **Many-to-many command mapping**: Supports multiple key combinations triggering the same command and single key combinations triggering multiple commands.

4. **Multi-monitor support**.

5. **Grid navigation**: Implements a grid-based cursor positioning system that can target either the active screen or the active window.

6. **Hint-based navigation**: Features a Vimium-style hint system that can target the active screen, active window, or all screens.

7. **Key remapping system**: Includes a way to remap keys and activate the remapping only in specific modes. For example, you can make it so pressing a single key is equivalent to sending the combo _⊞ Win + down arrow_, which will minimize the current window.

8. **Zoom functionality**: Supports configurable zoom behavior per mode.

9. **Application-specific configurations**: Provides context-aware mode switching based on the focused application, allowing for application-specific behavior (e.g., disabling mousemaster shortcuts in games).

10. **Live configuration**: Implements automatic configuration reloading when the configuration file is modified.

11. **Visual feedback system**: Provides an optional mouse indicator that changes color based on cursor state (idle, moving, clicking, scrolling) and repositions to remain visible near screen edges.

12. **Position history hints**: absolute positions can be saved on the fly to make it possible to move the mouse to them later.

## Configuration

mousemaster is highly configurable through its configuration file. The configuration file is automatically reloaded when saved.
For a complete reference of all configuration concepts and options, see the [configuration reference](configuration/configuration-reference.md). The reference document covers key aliases, combos, modes, mouse properties, hint properties, grid properties, app aliases, position history, logging, keyboard layout, and more.

## Troubleshooting

### Common Issues

1. **Overlay not visible in some applications**:
   - Run mousemaster.exe as administrator to ensure the overlay appears on top of all windows

2. **Keyboard layout issues**:
   - If your keyboard layout isn't automatically detected, specify it in the configuration:
     ```properties
     keyboard-layout=us-dvorak
     ```
     See [Keyboard layout](configuration/configuration-reference.md#keyboard-layout)
     for more details.

4. **Conflicts with other applications**:
   - Create app-specific modes to disable mousemaster in certain applications:
     ```properties
     app-alias.gameapp=game.exe
     idle-mode.to.game-mode=_{gameapp}
     game-mode.to.idle-mode=^{gameapp}
     ``` 
     See [App Aliases](configuration/configuration-reference.md#app-aliases) for more details.
### Logging

Enable detailed logging for troubleshooting:

```properties
logging.level=DEBUG
logging.to-file=true
logging.file-path=mousemaster.log
```

## Contributing

Contributions to mousemaster are welcome! If you have ideas for a better configuration that you would like to share, open an issue or join the [Discord](https://discord.gg/GSB6MaKb2R).

If you enjoy mousemaster, consider making a [donation](https://ko-fi.com/petoncle) or stop by the Discord to show your support!

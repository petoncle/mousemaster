# mousemaster

- Control your mouse from the keyboard and remap your keys, on Windows and macOS
- Heavily inspired by [warpd](https://github.com/rvaiya/warpd), [mouseable](https://github.com/wirekang/mouseable/), [neru](https://github.com/y3owk1n/neru) and [kanata](https://github.com/jtroo/kanata)

<div align="center">
  <a href="#windows">
    <img src="https://img.shields.io/badge/Windows-supported-2ea043?style=for-the-badge&logo=data:image/svg%2Bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZmlsbD0iI2ZmZiIgZD0iTTAgMy40NDlMOS43NSAyLjF2OS40NTFIMG0xMC45NDktOS42MDJMMjQgMHYxMS40SDEwLjk0OU0wIDEyLjZoOS43NXY5LjQ1MUwwIDIwLjY5OU0xMC45NDkgMTIuNkgyNFYyNGwtMTIuOS0xLjgwMSIvPjwvc3ZnPg==" alt="Windows: supported"/>
  </a>
  <a href="#macos">
    <img src="https://img.shields.io/badge/macOS-supported-2ea043?style=for-the-badge&logo=apple&logoColor=white" alt="macOS: supported"/>
  </a>
  <a href="#contributing">
    <img src="https://img.shields.io/badge/Linux-help%20wanted-6e7681?style=for-the-badge&logo=linux&logoColor=white" alt="Linux: help wanted"/>
  </a>
</div>

<div align="center">
  <a href="https://discord.gg/GSB6MaKb2R">
    <img src="https://img.shields.io/badge/Join%20%20The%20%20Discord%20%20Server-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Join the mousemaster Discord Server"/>
  </a>
</div>

<p align="center">
<a href="#demo">Demo</a> •
<a href="#windows">Install on Windows</a> •
<a href="#macos">Install on macOS</a> •
<a href="#usage">Usage</a> •
<a href="#features">Features</a> •
<a href="#contributing">Contributing</a>
</p>

## Demo

### Hint grid
https://github.com/user-attachments/assets/cfba2c70-7edd-44bf-b63e-ded1613368da

### Recursive hint grid
https://github.com/user-attachments/assets/9140b3be-6109-4a90-a842-0a22c7e0e562

### Recursive grid
https://github.com/user-attachments/assets/b395505e-9a06-4ec8-b361-81323c7b3fb4

### UI hints
https://github.com/user-attachments/assets/91eef554-bf68-44a7-8d16-5e4d5d353fe0

### Continuous mouse movement
https://github.com/user-attachments/assets/7300bf36-6a02-4615-b068-30c38571fc24

## Installation

### Windows

1. Download **mousemaster.exe** (a portable executable) from the [Release page](https://github.com/petoncle/mousemaster/releases/latest), or build it from source.
2. In the same Release page, choose and download one of the existing configuration files:
   - **neo-mousekeys-ijkl.properties** (***recommended***): an IJKL configuration ([see documentation](docs/neo-mousekeys-ijkl.md))
   - **neru.properties**: a recursive hint configuration ([see documentation](docs/neru.md))
   - **neo-mousekeys-wasd.properties**: a WASD configuration ([see documentation](docs/neo-mousekeys-wasd.md))
   - **warpd.properties**: an HJKL configuration ([see documentation](docs/warpd.md))
   - **mouseable.properties**: another HJKL configuration ([see documentation](docs/mouseable.md))
   - **author.properties**: an IJKL configuration designed to control everything with the right hand only ([see documentation](docs/author.md))
3. Place the executable and the configuration file of your choice in the same directory.
4. Rename the configuration file to **mousemaster.properties**.
5. Run **mousemaster.exe**: make sure to run it as administrator if you want the
   mousemaster overlay to be displayed on top of everything else.
6. Feel free to open a [GitHub Issue](https://github.com/petoncle/mousemaster/issues)
   or join the [Discord](https://discord.gg/GSB6MaKb2R) if you need help creating your own
   configuration. If you have ideas for a better configuration that
   you would like to share, I'd love to hear from you.

### macOS

macOS support is new and testers are wanted: if you try it, please report what does and does not
work in an [issue](https://github.com/petoncle/mousemaster/issues).

The setup below is more involved than it should be. mousemaster is not signed or notarized, which
needs an Apple developer account I do not have, so macOS treats the app as unidentified and it
has to be allowed by hand.

mousemaster reads the keyboard through the Karabiner virtual HID device, so that driver and a few
permissions are needed.

1. Install [Karabiner-Elements](https://karabiner-elements.pqrs.org), which installs the virtual HID
   device, and allow its system extension when macOS asks.
2. Download and unzip **mousemaster-macos.zip** into **/Applications**:
   ```
   curl -L -o mousemaster-macos.zip https://github.com/petoncle/mousemaster/releases/latest/download/mousemaster-macos.zip
   unzip mousemaster-macos.zip -d /Applications
   ```
   Downloading it in a browser instead marks the app as quarantined, and macOS refuses to start a
   quarantined app that is not notarized. Clear it before the first run:
   ```
   xattr -dr com.apple.quarantine /Applications/mousemaster.app
   ```
   Or let macOS refuse it once, then allow it under **System Settings > Privacy & Security >
   Security**, where an **Open Anyway** button appears for it.
3. In the [Release page](https://github.com/petoncle/mousemaster/releases/latest), choose and
   download one of the configuration files listed above, place it in **/Applications** next to
   **mousemaster.app**, and rename it to **mousemaster.properties**.
4. Run it from a terminal, as root, which the virtual HID device requires. The configuration is
   read from the working directory, so start it from where you put it:
   ```
   cd /Applications && sudo mousemaster.app/Contents/MacOS/mousemaster
   ```
5. Grant the permissions macOS asks for, under **System Settings > Privacy & Security**. They are
   granted to the terminal you run mousemaster from rather than to mousemaster itself, so they
   survive replacing mousemaster.app with a new build. Quit and reopen that terminal after
   granting, then run mousemaster again.
   - **Input Monitoring**, to read the keys you press. Always needed.
   - **Accessibility**, only for UI hints (`hint.type=ui`).
   - **Screen Recording**, only for zoom (`mode.zoom`).

## Usage

### neo-mousekeys-ijkl configuration

- **Activate**: Press _leftalt + e_ or _leftalt + capslock_
- **Deactivate**: Press _q_ or _p_
- **Mouse movement**: Use _i_ (up), _j_ (left), _k_ (down), _l_ (right)
- **Mouse buttons**: _;_ (left), _rightshift_ (middle), _'_ (right)
- **Grid mode**: Press _g_
- **Hint mode**: Press _f_
- **Recursive hint mode**: Press _r_
- **UI Hint mode**: Press _leftalt + f_
- **Screen selection**: Press _c_

![neo-mousekeys-ijkl layout](docs/assets/neo-mousekeys-ijkl-layout.png)

For a complete reference, see the [neo-mousekeys-ijkl documentation](docs/neo-mousekeys-ijkl.md).
The other configurations are documented too: [neru](docs/neru.md),
[neo-mousekeys-wasd](docs/neo-mousekeys-wasd.md), [warpd](docs/warpd.md),
[mouseable](docs/mouseable.md), [author](docs/author.md).

## Features

1. **Combos and key remapping**: Define combos (key sequences, chords, timed holds, taps, tap-dances, and more) to trigger commands, switch modes, or remap keys. See the [combo reference](docs/combo-reference.md).
2. **Modes**: Each mode has its own combos and its own hint, grid, indicator and zoom settings, and can inherit properties from other modes.
3. **Conditional property values**: Any mode property can take a different value depending on the current state: which keys are held, which app is active, what the mouse is doing, which screen the cursor is on, and more.
4. **Continuous mouse movement**: Move the cursor, click, scroll, and drag, all from the keyboard.
5. **Hint navigation**: Cover the screen with a grid of labeled hints, type a label to jump the cursor there.
6. **UI hint navigation**: Label buttons, links, and text fields in the active window.
7. **Recursive grid navigation**: Divide the screen into 2x2 sections, shrink with each key press or snap to the grid's edges to reach a precise position.
8. **Zoom**.
9. **App-specific behavior**: Restrict combos to specific apps, so the same keys behave differently depending on the focused app.
10. **Cursor indicator**: A polygon drawn next to the cursor, with its own size, edge count, position, color, opacity, outlines, shadow and text label.
11. **Position history**: Save cursor positions, jump back to them later. Keep several named histories, each with its own keys, and have a history hold a separate list of positions for each app.
12. **Live configuration**: All configuration lives in a single file that is automatically reloaded when saved.

mousemaster provides low-level primitives (modes, combos, commands, macros, key aliases) that you compose to build the exact behavior you want. See the [configuration reference](docs/configuration-reference.md).

## Contributing

Contributions to mousemaster are welcome! 

- **Share a configuration**: If you have ideas for a new or improved configuration that you would like to share, open an issue or join the [Discord](https://discord.gg/GSB6MaKb2R).
- **Cross-platform support:** mousemaster supports Windows and macOS.  
  If you're interested in helping extend mousemaster to Linux, your contributions are very welcome. Please open an issue or join the Discord to get involved.

If you enjoy mousemaster, consider making a [donation](https://ko-fi.com/petoncle) or stop by the Discord to show your support!

# mousemaster

<a href="https://discord.gg/GSB6MaKb2R"><img src="https://img.shields.io/discord/854326924402622474?color=%235865F2&label=discord" alt="Join Discord Chat"></a>

- A keyboard driven interface for mouse manipulation
- Heavily inspired by [warpd](https://github.com/rvaiya/warpd)
  and [mouseable](https://github.com/wirekang/mouseable/)

<p align="center">
<a href="#demo">Demo</a> •
<a href="#installation">Installation</a> •
<a href="#features">Features</a> •
<a href="#notes">Notes</a>
</p>

# Demo

## Hints Demo
https://github.com/petoncle/mousemaster/assets/39304282/841216ac-08a9-408a-8cd7-6558941afd3c

## Grid Demo
https://github.com/petoncle/mousemaster/assets/39304282/12677e9e-3126-4694-b4bc-5a18e9438bc9

## Mouse Movements Demo (1/2)
https://github.com/petoncle/mousemaster/assets/39304282/2dadbfa0-1270-41ff-9e18-3fb3a28d5b6f

## Mouse Movements Demo (2/2)
https://github.com/petoncle/mousemaster/assets/39304282/a7e82696-a5c8-45c2-b100-d905a8daba30

# Installation

1. Download mousemaster.exe (a portable executable) from
   the [Release page](https://github.com/petoncle/mousemaster/releases/latest), or build
   it from source.
2. In the same Release page, choose then download one of the existing configuration files:
   - [warpd.properties](configuration/warpd.properties): warpd-style configuration (documentation: [warpd-style configuration for mousemaster](configuration/warpd.md))
   - [mouseable.properties](configuration/mouseable.properties): mouseable-style configuration (documentation: [mouseable-style configuration for mousemaster](configuration/mouseable.md))
   - [author.properties](configuration/author.properties): the author's configuration
     which suits the author's specific needs; it can be used as a reference and
     showcase of mousemaster's features
3. Place the executable and the configuration file of your choice in the same directory.
4. Rename the configuration file to mousemaster.properties.
5. Run mousemaster.exe.
6. Don't hesitate to open a [GitHub Issue](https://github.com/petoncle/mousemaster/issues)
   or join the [Discord](https://discord.gg/GSB6MaKb2R) if you need help creating your own
   configuration.

# Features

1. **Command-Line Only:** mousemaster operates solely through the command line, with
   configuration loaded from a file (mousemaster.properties).

2. **Dynamic Configuration:** The configuration file is live, automatically reloading upon
   any saved changes.

3. **Adaptive Mouse Indicator:** The optional and customizable mouse indicator (a
   plain-color square drawn next to the mouse cursor) changes colors based on mouse
   activity—idle, moving, button pressing, or scrolling. It repositions itself to stay
   visible when the mouse is near screen edges.

4. **Seamless Multi-Screen Navigation:** Like mouseable, it allows for seamless mouse
   movement across multiple screens.

5. **(Very) Advanced Key Combinations (Key Combos):** mousemaster offers an advanced level
   of customization for key combinations. It allows for intricate key combos like "hold
   alt for 1 second" or "press then release alt, twice in a row".

6. **Left/Right Key Distinction:** mousemaster distinguishes between left and right
   alt/ctrl/shift keys, meaning that left alt and right alt can be used to trigger
   different commands.

7. **No Key Is Special:** All keys are treated the same way, meaning that for example, you
   can implement a "hold Z then press X" combo or "hold capslock then press enter" combo
   just like you would implement a "hold alt then press X" combo.

8. **Does Not Conflict With Other Applications:** mousemaster tries not to clash with
   other apps using shortcuts. Key events that are not part of any combos are seamlessly
   passed onto other apps.

9. **Many-to-Many Key Combo-to-Command Mapping:** The same key combo can be used to
   trigger multiple commands. Multiple key combos can be used to trigger the same command.

10. **Custom Modes:** Users have to define their own modes, with each mode
    having its own key combo-to-command map and mouse settings. There is only one
    predefined mode, the "idle-mode", which has no key combos predefined (this is the
    "mousemaster is disabled mode").

11. **Mode Timeout:** A timeout can be set on a mode to switch to another mode after some
    idle time. This can be used for automatically disabling mousemaster after a while.

12. **Grids:** mousemaster incorporates a warpd-like grid feature. A grid can cover the
    active screen or the active window.

13. **Hints:** mousemaster incorporates a hint-based navigation system similar
    to Vimium's. A hint grid can cover the active screen, the active window, or all
    screens (that last one can be used for screen selection hints). A mouse button click
    can be automatically triggered after a hint is selected.

14. **Position History Hints:** mousemaster incorporates position history hints (a warpd
    feature). Unlike warpd, the position history hints are not limited to a single screen.

# Notes

- By using mousemaster along with [PowerToys](https://github.com/microsoft/PowerToys)
  and the Vimium-like [Tridactyl](https://github.com/tridactyl/tridactyl) extension for
  Firefox, I am able to not touch my mouse at all anymore. 
- The two major differences between mousemaster and the existing alternatives (warpd and
  mouseable), are the way key combos are defined (key presses and key releases instead
  of "just keys"), and the "create your own modes" approach.
- The configuration of mousemaster is significantly more complex than warpd and mouseable,
  mostly because you have to think in terms of key presses and key releases. This is a
  conscious trade-off for greater flexibility. Detailed debug logs are provided in the
  console to assist in configuration and troubleshooting.
- Currently, mousemaster is Windows-only. I have not looked at making it cross-platform
  simply because all my desktops are Windows based. Besides, I believe the Linux and macOS
  APIs offer less control than the Windows API when it comes to handling mouse and
  keyboard events, and for that reason some of the features of mousemaster may not
  currently be implementable on Linux and macOS.

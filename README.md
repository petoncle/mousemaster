# mousemaster

<a href="https://discord.gg/GSB6MaKb2R"><img src="https://img.shields.io/discord/854326924402622474?color=%235865F2&label=discord" alt="Join Discord Chat"></a>

- A keyboard driven interface for mouseless mouse manipulation
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
(This video shows transparent hint grid lines. Now, the lines are opaque (white) by default.)

https://github.com/user-attachments/assets/2c0765eb-5fa2-4604-a868-df7e9f25e32a

## Grid Demo (1/2)
https://github.com/petoncle/mousemaster/assets/39304282/12677e9e-3126-4694-b4bc-5a18e9438bc9

## Grid Demo (2/2)
https://github.com/petoncle/mousemaster/assets/39304282/105c6a2d-2849-4d23-b706-1fc9410ae680

## Mouse Movements Demo (1/2)
https://github.com/petoncle/mousemaster/assets/39304282/2dadbfa0-1270-41ff-9e18-3fb3a28d5b6f

## Mouse Movements Demo (2/2)
https://github.com/petoncle/mousemaster/assets/39304282/a7e82696-a5c8-45c2-b100-d905a8daba30

# Installation

1. Download **mousemaster.exe** (a portable executable) from
   the [Release page](https://github.com/petoncle/mousemaster/releases/latest), or build
   it from source.
2. In the same Release page, choose then download one of the existing configuration files:
    - **neo-mousekeys.properties** (***recommended***): a WASD configuration ([see documentation](configuration/neo-mousekeys.md))
    - **warpd.properties**: an HKJL configuration ([see documentation](configuration/warpd.md))
    - **mouseable.properties**: another HKJL configuration ([see documentation](configuration/mouseable.md))
    - **author.properties**: an IJKL configuration designed to control everything with the right hand only. ([see documentation](configuration/author.md))
3. Place the executable and the configuration file of your choice in the same directory.
4. Rename the configuration file to **mousemaster.properties**.
5. Run **mousemaster.exe**: make sure to run it as administrator if you want the 
   mousemaster overlay to be displayed on top of everything else. 
6. Feel free to open a [GitHub Issue](https://github.com/petoncle/mousemaster/issues)
   or join the [Discord](https://discord.gg/GSB6MaKb2R) if you need help creating your own
   configuration. If you have ideas for a better configuration that
   you would like to share, I'd love to hear from you.
7. If you enjoy mousemaster, consider making a [donation](https://ko-fi.com/petoncle) or stop by the Discord to show your support! 

# Features

1. **Advanced key combinations (key combos):** mousemaster offers a high level
   of customization for key combinations. It allows for advanced key combos like "hold
   alt for 1 second" or "press then release alt, twice in a row".
2. **Custom modes:** Users have to define their own modes, with each mode
   having its own key combo-to-command map and mouse settings. There is only one
   predefined mode, the "idle-mode", which has no key combos predefined (this is the
   "mousemaster is disabled mode").
3. **Flexible key combo to command mappings:** The same key combo can be used to
   trigger multiple commands. Multiple key combos can be used to trigger the same command.
4. **Multi-screen navigation:** mousemaster allows for mouse movement across multiple screens.
5. **Grids:** mousemaster includes a grid-based navigation system. A grid can cover the
   active screen or the active window.
6. **Hints:** mousemaster includes a hint-based navigation system similar
   to Vimium's. A hint grid can cover the active screen, the active window, or all
   screens (which can be used for screen selection hints). A mouse button click
   can be automatically triggered after a hint is selected.
7. **Key remapping:** mousemaster includes a way to remap keys and activate the remapping
   only in specific modes. For example, you can make it so pressing a single key is
   equivalent to sending the combo _⊞ Win + down arrow_, which will minimize the current
   window.
8. **App-specific modes:** it is possible to create modes that are active only when a specific
   app is in focus. For example, you can disable all mousemaster shortcuts while
   playing a game.
9. **Dynamic configuration:** The configuration file is live, mousemaster automatically
   reloads it whenever the file is saved.
10. **Visual mouse indicator:** The optional and customizable mouse indicator (a
    plain-color square drawn next to the mouse cursor) changes colors based on mouse
    activity—idle, moving, button pressing, or scrolling. It repositions itself to stay
    visible when the mouse is near screen edges.
11. **Left/right key distinction:** mousemaster distinguishes between left and right
    alt/ctrl/shift keys, meaning that left alt and right alt can be used to trigger
    different commands.
12. **Position history hints:** absolute positions can be saved on the fly to make 
   it possible to move the mouse to them later.

# Notes

- I use mousemaster on a daily basis, along with [PowerToys](https://github.com/microsoft/PowerToys)
  and the [Tridactyl](https://github.com/tridactyl/tridactyl) extension for
  Firefox. I no longer use my physical mouse except for certain tasks like drawing. 
- The two major differences between mousemaster and the existing alternatives (warpd and
  mouseable), are the way key combos are defined (i.e. the distinction between key
  presses and key releases), and the "create your own modes" approach.
- The configuration of mousemaster is significantly more complex than warpd and mouseable,
  mostly because you have to think in terms of key presses and key releases. This is a
  conscious trade-off for greater flexibility.
- Currently, mousemaster is Windows-only. I have not looked at making it cross-platform
  only because my desktops are Windows based.
- If you need help, have questions, or suggestions, 
  don't hesitate to open a [GitHub Issue](https://github.com/petoncle/mousemaster/issues) or join the [Discord](https://discord.gg/GSB6MaKb2R).

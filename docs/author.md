# Author's configuration of mousemaster ([author.properties](../configuration/author.properties))

(See [configuration-reference.md](configuration-reference.md) for the property list and
[combo-reference.md](combo-reference.md) for the combo syntax.)

A right-hand-only pointer: `i` `j` `k` `l` move, the keys around them click, and every mode is reached
without the hand leaving the home row. This document covers the bindings whose shape is not obvious from
the file.

Two external tools are assumed: **[Alt+Tab Terminator](https://www.ntwind.com/software/alttabter.html)**
(a Win+Tab switcher with a type-to-search box, which several macros type into) and **PowerToys Run** on
Ctrl+Alt+Space.

## Design premises

1. **The right hand does not move.** Movement, all three buttons, the hint grid, the wheel and "back"
   are all under the same fingers.
2. **One key, several meanings, resolved by what comes next.** `rightalt` tapped enters normal mode;
   held, the grid; tapped again, the hint grid; with a direction key, "faster"; with `p` or `;`, a snap.
3. **Typing must never be damaged.** Normal mode exits on any unhandled key press, `rightctrl` and
   `capslock` are matched without being eaten, `leftalt`+`space` is carved out of the left-click
   binding, and `typing-mode` exists to get the `space` leader out of the way while typing.
4. **State lives in virtual keys, not modes.** A tap of `;` presses `ismclick`, which recolors every
   hint box magenta *and* rewrites which combo commits the click, with no mode switch — so the hint mesh
   is not rebuilt.

Seventeen virtual keys are declared, each next to the macros that press it. Eleven hold state:
`islclick` `ismclick` `isrclick` `isnohintclick` `islongpresshintkey` `isnomove` `mouseanchored`
`zooming` `autodrilling` `never`, and `showrecursivehintkeys`, the only one declared `pressed`. The
other six — `mousefill` `mouseburst` `mouseanimation` and `leftmouseanimation`/`middlemouseanimation`/
`rightmouseanimation` — drive the indicator's click animation.

## Key aliases

| Alias | Keys | Role |
|---|---|---|
| `up` `down` `left` `right` / `directionkey` | `i` `k` `j` `l` | movement, grid refinement, wheel |
| `leftbutton` | `space` | left click, click arming, superfast wheel |
| `middlebutton` | `;` `'` `#` | middle click, window scope, arrow mode, UI hints |
| `rightbutton` | `p` `[` `]` | right click, screen scope |
| `idletoscreensnap` | `p` | screen snap from `p` only, so `rightalt`+`[`/`]` reaches IntelliJ |
| `recursivehintkey` | `u i o j k l m , .` | the 3×3 hint grid |
| `recursivehintwheel` | `7` `8` `9` `0` | scrolling inside the hint grid |
| `fasttrackkey` | `7` `8` `9` | opens the hint grid pre-drilled, from normal mode |
| `hintandrecursivehintkey` | `i j k l m o` + `u , .` | keys a hint commit can come from |
| `anybutton` | `space` + `;` `'` `#` + `p` `[` `]` | with the above, "the commit key is released" |
| `hintback` | `backspace` `h` | up one level / leave |
| `hintnomove` | `/` `n` | toggles whether the cursor follows the drill |
| `positionhistorykey` | `m` | holds the position-history hints open |
| `positionhistoryhintkey` | `i j k l` | picks a saved position |
| `uihintkey` | `i j k l m o n` | picks a UI element |
| `uihint` | `m` `n` | opens UI hints after `;` |
| `copy` / `paste` | `c` / `v` | Ctrl+C / Ctrl+V in every pointer mode |
| `modifierkey` | `leftshift leftctrl leftalt` | admitted by most preconditions |
| `oneshotkeys` | `leftshift leftctrl leftalt rightshift` | one-shot modifier taps |
| `accidentalhintkey` | most other letters/digits | swallowed inside the hint grid |

`accidentalhintkey` omits `h` (it is `hintback`) and `c`/`v` (copy/paste): an alias is trimmed wherever
it would shadow a real binding.

Every alias is declared with the layout its key *names* belong to (`key-alias.hintkey.uk-qwerty=…`,
plus `configuration-keyboard-layout=uk-qwerty`). mousemaster maps those names through scan codes to the
active layout, so switching between UK QWERTY and FR AZERTY keeps every combo under the same finger.

## The mode map

Only idle and normal mode are pushed onto the mode history stack, so every transient mode's
`to.previous-mode-from-history-stack` lands on one of those two.

| Mode | What it is |
|---|---|
| `idle-mode` | keys belong to the applications; `rightalt`, `space`, `;` and modifier taps are leaders |
| `normal-mode` | the pointer mode: movement, buttons, wheel, hints |
| `normal-timeout4..1-mode` | a visible 4-3-2-1 countdown out of normal mode |
| `typing-mode` | idle mode with the `space` leader disabled |
| `oneshot-mode` | a tapped modifier waiting for the key it applies to |
| `fast-mouse-mode` / `wheel-mode` | `rightalt` held while moving / a double-tapped direction key |
| `surgical-mode` / `zoom-mode` | `;` held (slow) / `n` held (slow + magnifier) |
| `temp-screen-snap-mode` | the 250 ms window after `rightalt` in which the next key decides |
| `screen-snap` / `window-snap` / `screen-grid` / `window-grid` | jump to edges / bisect a region |
| `center-mouse-mode` | invisible; recenters the pointer on an application switch |
| `recursive-hint1..4-mode` | the 3×3 drill-down hint grid |
| `click-after-hint-mode` | the click a hint commit produces, or the held button when the key was held |
| `ui-hint-mode` (+ `click-after-`) | hints on UI elements detected from the screen |
| `position-history-mode` | saved positions, shown while `m` is held |
| `arrowbasekey-mode` | `i j k l` emit arrow keys |
| `macro-hint-mode` | an invisible ~10 px hint grid macros use to click fixed pixels |

Abstract modes carry what several of those share: `_indicator-mode` (and `_hint-indicator-mode` for the
hint modes), `_clipboard-mode`, `_normal-timeout-mode`, `_recursive-hint-mode`.

## Idle mode — the launcher

Nothing is remapped; four keys are leaders.

**`rightalt`** — tap → normal mode; hold 250 ms → screen grid; `+;` → window snap; `+p` → screen snap.
The first two carry `_{none | modifierkey}`, so Shift+`rightalt` still reaches applications.

**`space`** — hold and press a second key within a second:

| Combo | Effect |
|---|---|
| `space` `l` / `space` `k` | close / minimize the active window (`leftalt`+`space` then `c` / `n`) |
| `space` `i` | Win+Tab task view |
| `space` `n` | Firefox tab search: clicks a toolbar button, Ctrl+L twice, clears, types `% ` |
| `space` `8` / `space` `9` | switch to VLC / Firefox by typing into the Alt+Tab Terminator search box |

The app-switch macros type a throwaway `q` then `backspace`: typing anything moves focus into the
switcher's search box, and the `backspace` clears it before the real query. All are guarded with
`^{oneshotkeys}`, so a real modifier makes the macro yield.

**`;`** — hold and press `i j k l` for arrow keys, or `m`/`n` for UI hints. The entry combo
`{+middlebutton #arrowkeymodifier?} +anyarrowbasekey` is an any-order set, so Ctrl+`;`+`j` is Ctrl+Left
whichever key goes down first.

**A tapped modifier** (`leftshift`, `leftctrl`, `leftalt`, `rightshift`, `rightctrl`) enters one-shot mode.

## One-shot modifiers

Tap a modifier — do not hold it — and it applies to the next key, for up to two seconds. Taps stack.

```properties
oneshot-mode.macro.oneshot={*oneshotkeysandrightctrl+} #!{moddablekey oneshotkeysandrightctrl space}-0-2000 +moddablekey \
  -> +oneshotkeysandrightctrl +moddablekey -oneshotkeysandrightctrl
```

`{*alias+}` expands to one tap move per modifier and requires at least one, in any order — that is what
turns three taps into Ctrl+Shift+A. `#!{…}-0-2000` ignores everything that is not a modifier or a
moddable key, so a stray release does not break the chain, and expires after 2 s.

Two escape hatches: **double-tap** within 200 ms (`macro.redo-doubletap`) genuinely presses the modifier
and leaves it down; and `oneshot-mode.to.typing-mode` has six branches that all mean "the user is typing
normally now". `macro.alttick` inserts a `wait-0` between `leftalt` and the key in IntelliJ only, which
does not accept them in the same input batch.

## Typing mode, and why it has to exist

`space` being a leader means mousemaster must hold its press back until it knows whether `space` `l` is
coming, then replay it. Replayed input is dropped inside VirtualBox, and typing `space` `i` in a
sentence would fire the task-view macro.

```properties
idle-mode.to.typing-mode=_{none | modifierkey} ^{space middlebutton} #typingkeys
typing-mode.to=idle-mode.to
typing-mode.to.typing-mode=+never
typing-mode.to.idle-mode=#!typingkeysandspaceandoneshotkeys | ^{typingkeys} wait-500
```

Typing mode is idle mode *without* the `space` macros. Any letter, digit, punctuation, arrow, function
key or `backspace` enters it; `^{space middlebutton}` leaves a leader sequence already in progress
alone. `typing-mode.to=idle-mode.to` copies idle mode's transitions but not its macros, so `space` is an
ordinary character with no delay. 500 ms after the last typing key, or immediately on any non-typing
key, the leaders come back.

## Normal mode

### Moving

200 px/s to 1200 over 3000 px/s² on a smootherstep curve, deceleration 20. Jumps — grid snaps, hint
commits, history jumps — are animated at 30000 px/s rather than teleporting.

`start-move` carries `^{rightalt}`, so with `rightalt` held a direction key means something else.
`_{none | modifierkey}` allows moving with Shift or Ctrl already down, ready for the click that ends the
movement.

### `rightalt` decides late

With a direction key already held → **fast mouse mode** (2000 px/s initial, 3000 max); release
`rightalt` and normal speed resumes mid-glide. Otherwise → `temp-screen-snap-mode`, which outlines the
active screen in red (a 1×1 grid, unsynchronized so the pointer stays put) and waits:

| Next event | Result |
|---|---|
| release `rightalt` (a second tap) | **the recursive hint grid** |
| a direction key, or `p` | screen snap |
| `;` | window snap |
| `space` | PowerToys Run |
| `,` | idle mode |
| nothing for 250 ms | screen grid |

```properties
temp-screen-snap-mode.to.screen-grid-mode=#{*}-250
temp-screen-snap-mode.to.recursive-hint1-mode=+rightalt -rightalt
```

`#{*}-250` is a leading wait that ignores every key, so the 250 ms runs from mode entry and nothing you
press restarts it; a plain `wait-250` would measure from the last key event.

The hint-grid entry is written as press *and* release so the press is eaten — with a menu open, letting
`alt` through would close the very thing you opened the hints to click.

### Buttons, dragging, and the `leftalt`+`space` carve-out

```properties
normal-mode.press.left=^{leftalt} _{none | leftshift*leftctrl | positionhistorykey} +leftbutton \
                     | _{none | leftshift*leftctrl} #leftalt-250 +leftbutton
normal-mode.release.left=^{leftalt} _{none | leftshift*leftctrl | positionhistorykey} +leftbutton-0-250 -leftbutton
```

- **Shift-click and Ctrl-click work** — the precondition admits them rather than demanding an empty
  keyboard.
- **`leftalt`+`space` is protected.** Branch one requires `leftalt` unpressed, so Alt+Space still opens
  the Windows system menu the close/minimize macros depend on. Branch two restores clicking once
  `leftalt` has been held 250 ms. `#leftalt` does not eat, so the OS sees the Alt press either way.
- **`m` may be held** (`positionhistorykey`), which is what lets you click while the position-history
  hints are up.
- **Holding `space` leaves the button pressed.** The release only matches a press shorter than 250 ms —
  grab a scrollbar, move with `i j k l`, tap `space` to drop. Right button behaves the same.

Middle click is special because `;` is also the surgical key:

```properties
normal-mode.press.middle=+middlebutton-0-150 -middlebutton
normal-mode.release.middle=+middlebutton-0-150 -middlebutton-1
normal-mode.break-combo-preparation=+middlebutton-0-150 -middlebutton-1
normal-mode.to.surgical-mode=+;
surgical-mode.press.middle=+never
```

The `;` press enters surgical mode at once, where middle click is disabled. Release within 150 ms and
`surgical-mode.to.normal-mode=-;` returns, where the combo preparation — still holding `+;` `-;` —
completes both middle-click combos. So a **tap** of `;` is a middle click and a **hold** is surgical
mode, with no timeout to sit through. The trailing `-1` makes `release.middle` a millisecond longer than
`press.middle` so they fire in order, and `break-combo-preparation` stops them re-matching. `'` and `#`
are middle click only.

### Wheel mode

**Double-tap a direction key** and the second press starts continuous scrolling. The first tap already
started a movement, so `stop-move` and `start-wheel` both react to the same triple. `start-move` stays
bound, so a *different* direction key still moves the pointer while the first scrolls.
`wheel-mode.to.normal-mode=^{directionkey}` is precondition-only: it fires the instant no direction key
is held, with no event at all.

```properties
wheel-mode.wheel.max-velocity=1000 | _{rightalt} -> 10000 | _{leftbutton} -> 100000000
wheel-mode.wheel.acceleration=500 | _{rightalt} -> 10000
wheel-mode.noop.eatfastandsuperfast=+rightalt | +leftbutton
```

`rightalt` gives 10× the speed and 20× the acceleration; `space` enough to reach the end of the
document. The `noop` eats both presses so they cannot mean anything else while scrolling.

### Copy, paste, and leaving

```properties
_clipboard-mode.macro.copy=^{oneshotkeys} +copy -> +leftctrl +copy -copy -leftctrl
normal-mode=_indicator-mode _clipboard-mode
arrowbasekey-mode=_clipboard-mode
_recursive-hint-mode=_indicator-mode _clipboard-mode
```

`c` and `v` send Ctrl+C and Ctrl+V, guarded with `^{oneshotkeys}` so Shift+`c` still types a capital C.
They live on an abstract `_clipboard-mode` that the pointer modes extend, and inheritance fans it out to
their children. A mode may extend several: each parent in turn fills what is still unset, so the first
to define a property keeps it — and for the macro map that filling **merges by macro name**, skipping
any parent macro whose name the child already defines. Idle and typing mode are excluded because `c`
must type a `c` there, and `macro-hint-mode` because `c` is one of its selection keys.

```properties
normal-mode.to.idle-mode=#rightctrl | #hintback
normal-mode.mode-after-unhandled-key-press=idle-mode
normal-mode.noop.capslocknoop=#capslock
```

`rightctrl`, `h` and `backspace` leave immediately, matched with `#` so the key still reaches the
application — pressing `h` exits normal mode *and* types an `h`. Everything else is caught by
`mode-after-unhandled-key-press`, which is why `capslock` needs a `noop`: it belongs to no combo, so
without that line it would kick you out.

```properties
normal-mode.to.normal-timeout4-mode=_{isidling} wait-1000
_normal-timeout-mode=normal-mode
_normal-timeout-mode.hide-cursor.enabled=true
_normal-timeout-mode.to.normal-timeout4-mode=+never
_normal-timeout-mode.to.normal-mode=^{isidling}
normal-timeout4-mode.to.normal-timeout3-mode=wait-1000
```

The fourth exit is a watchable timeout: one second of `isidling` (built-in — not moving, not wheeling,
no button held) enters a copy of normal mode labeled `4`, each further second counts down, and
`normal-timeout1-mode` drops to idle. Any mouse activity satisfies `^{isidling}` and restarts the count.
`hide-cursor` drops the arrow glyph, leaving a colored dot with a digit in it.

## Surgical and zoom

```properties
normal-mode.to.surgical-mode=+;
surgical-mode.mouse.max-velocity=200
zoom-mode=surgical-mode
zoom-mode.zoom.percent=2.0 | _{middlebutton} -> 5.0
zoom-mode.mouse.max-velocity=200 | _{middlebutton} -> 50
```

Two graded slow modes, each held: `;` for 200 px/s instead of 1200, `n` for the same plus a 2×
magnifier on the cursor. Add `;` inside zoom mode for 5× and 50 px/s; `zoom-mode.noop=+middlebutton`
eats it so it only drives the two mutations, and surgical mode's disabled middle click is inherited.
`zoom-mode.indicator.render-as-cursor=false` — a true-size system cursor would not match the magnified
content beneath it.

## Position history

```properties
normal-mode.to.position-history-mode=^{directionkey} +positionhistorykey-0
position-history-mode.to.position-history-mode=-rightalt | +rightalt-500 | +positionhistoryhintkey | +positionhistoryhintkey-500
```

Hold `m` and the saved positions appear as hints labeled `i j k l` (`max-size=16` over 4 keys: at most
two keystrokes). Release `m` to return. `^{directionkey}` keeps it from opening mid-movement.

| While `m` is held | Effect |
|---|---|
| tap `rightalt` (< 500 ms) | save the current position |
| hold `rightalt` 500 ms | clear the list |
| tap a hint key | jump there |
| hold a hint key 500 ms | delete that position |

Each of those four re-enters the mode through the self-transition, which rebuilds the mesh; without it a
save would not appear as a hint and a jump would leave a stale selection.

`position-history.isolation=active-app` gives every application its own list, and both click-follow-up
modes have a `position-history.save-position`, so **every hint click records where it landed**, per
application. `press`/`release` are inherited from normal mode, so you can click without releasing `m` —
hence `positionhistorykey` in normal mode's button preconditions.

## The recursive hint grid

Nine keys form a 3×3 grid under the right hand, and each keypress narrows the region to that ninth:

```
u i o
j k l      (k = center)
m , .
```

Four levels; the fourth is commit-only.

**Getting in:** `rightalt` then `rightalt` from normal mode (works with a menu open — the `alt` is
eaten); `7`, `8` or `9` for a pre-drilled grid ([below](#skipping-levels-7--8--9)); or automatically
after any hint click, since `click-after-hint-mode.to.recursive-hint1-mode` drops you back at level 1
rather than out.

Entry resets what a previous session left behind, as seven one-line macros on `+autodrilling` rather
than one bulk reset — a bulk reset would also clear `showrecursivehintkeys`.

### Geometry

```properties
_recursive-hint-mode.hint.grid-area=last-selected-hint-cell
_recursive-hint-mode.hint.grid-area-center=last-selected-hint | _{mouseanchored} -> mouse
_recursive-hint-mode.hint.grid-max-row-count=3
_recursive-hint-mode.hint.grid-cell-sizing=fit
recursive-hint1-mode.hint.grid-area=active-screen
```

`grid-area=last-selected-hint-cell` is what makes it recursive: each level's area *is* the cell picked
above. `fit` computes cell sizes to fill that area exactly, so the pixel dimensions are ignored. Level 1
overrides the area to the whole screen.

Cell labels are 60, 20, 7 and 3 point at levels 1 to 4, shadow blur 10, 3, 1, none. A hint font size is
given in 100 %-screen pixels and grown by the screen's scale, so none of it needs a per-screen override.

### Two looks, toggled by `capslock`

```properties
_recursive-hint-mode.hint.font-opacity=1 | ^{showrecursivehintkeys} -> 0
_recursive-hint-mode.hint.decoration-label-override=plus
_recursive-hint-mode.hint.decoration-font-opacity=0 | ^{showrecursivehintkeys} -> 1
_recursive-hint-mode.hint.subdecoration-label-keys=recursivehintkey
_recursive-hint-mode.hint.subdecoration-label-override=^{showrecursivehintkeys} -> .
```

`showrecursivehintkeys` is declared `pressed`, so the default is the **keys** look: every cell shows its
letter, and each carries a 3×3 preview labeled with the nine keys of the next level.

`capslock` releases it for the **dots** look: cell letters hidden, one large `+` over the region (a 1×1
decoration whose label is overridden to `plus`) marking where a commit with no cell pick lands, and the
previews reduced to dots. Every mutation hangs off `^{showrecursivehintkeys}` — *not* pressed — so the
branches all describe the dots look.

### Drilling does not move the pointer

`isnomove` is pressed on entry. Whether the pointer follows is a four-branch mutation:

```properties
_recursive-hint-mode.hint.mouse-movement=mouse-follows-hint-grid-center \
  | ^{recursivehintwheel} _{isnomove rightalt} -> mouse-follows-selected-hint \
  | _{isnomove islclick | isnomove ismclick | isnomove isrclick} ^{islongpresshintkey isnohintclick rightalt} -> mouse-follows-selected-hint \
  | _{isnomove} ^{islclick ismclick isrclick islongpresshintkey rightalt} -> no-movement
```

The three values differ in *when* they move:

| Value | On arriving at a level | On the final cell pick |
|---|---|---|
| `mouse-follows-hint-grid-center` | yes, to the region's center | yes, to the cell |
| `mouse-follows-selected-hint` | no | yes, to the cell |
| `no-movement` | no | no |

Rightmost matching branch wins and the four are mutually exclusive, so it reads as a decision table:

- **plain drilling** → `no-movement`, so hover states and tooltips survive the narrowing.
- **a click armed** → `mouse-follows-selected-hint`: no movement while drilling, the final pick lands on
  the cell.
- **`rightalt` held** → the same, since `rightalt` + a pick means "move there without clicking".
- **anything else** — `isnomove` toggled off, a wheel key held, `islongpresshintkey` or `isnohintclick`
  set — falls through to grid-center. Those last two are commits carrying *no* cell pick, and the
  pointer has to arrive before `click-after-hint-mode` clicks.

`/` or `n` toggles `isnomove`; `hintback` re-presses it, so going back up restores the default.

### Arming a click

```properties
_recursive-hint-mode.macro.setislclick=^{islclick recursivehintwheel} +leftbutton -> #islclick
_recursive-hint-mode.hint.box-color=#000000 | _{islclick} -> #00FF00 | _{ismclick} -> #FF00FF | _{isrclick} -> #00FFFF
recursive-hint1-mode.to.recursive-hint2-mode=^{islclick ismclick isrclick rightalt} +recursivehintkey
_recursive-hint-mode.to.click-after-hint-mode=_{islclick | ismclick | isrclick} ^{rightalt} +recursivehintkey | ...
```

A button key *arms* its click type instead of clicking: cells and borders turn **green** (left),
**magenta** (middle) or **cyan** (right). The same key again disarms; a different one switches.

The drill transition requires no click armed and the commit requires one, so a hint key either goes a
level deeper or clicks, decided by whether you tapped a button key first. There is no confirm key. A tap
of `;` or `p` does not move the pointer; `space` is the exception.

### The three ways `space` commits

```properties
_recursive-hint-mode.macro.setisnohintclick=^{recursivehintwheel} +leftbutton | +middlebutton-250 | +rightbutton-250 -> #isnohintclick
_recursive-hint-mode.macro.unsetisnohintclick=#recursivehintkey -> ~isnohintclick
_recursive-hint-mode.to.click-after-hint-mode=\
    _{islclick | ismclick | isrclick} ^{rightalt} +recursivehintkey \
  | ^{recursivehintwheel} +leftbutton-250 \
  | ^{recursivehintwheel} +leftbutton-0-250 #{recursivehintkey}-0-500 -leftbutton \
  | +middlebutton-250 | +rightbutton-250 | +recursivehintkey-250
```

Pressing `space` presses both `islclick` (arming, cells green) and `isnohintclick`, which pushes
movement to grid-center — snapping the pointer to the middle of the region, previewing where a click
with no cell pick would land. From there:

1. **Tap `space`** (third branch) → a click at the region center. `#{recursivehintkey}` ignores hint-key
   events inside the window so the combo survives a keypress handled elsewhere.
2. **`space` then a hint key** (first branch) → the hint-key press clears `isnohintclick`, flipping
   movement back to `mouse-follows-selected-hint`; a virtual key pressed at the start of a macro's
   output takes effect before the combo's other commands, so the pick already sees the new value.
   `space` must still be down and the key must arrive within 250 ms, or the long-press branch has fired.
3. **Long-press `space`** (second branch) → at 250 ms the commit fires at the region center with no cell
   pick, and since `space` is past 150 ms the button stays pressed, which is how a drag starts.

Middle and right are the same without the tap-to-commit branch: a **tap** arms, a **250 ms hold**
commits at the region center.

**Long-pressing a hint key** (`+recursivehintkey-250`) is a fourth path. Its press has already drilled a
level, so the commit happens in the *deeper* grid, and `setislongpresshintkey` flips movement to
grid-center: hold a hint key to mean "go in there and click the middle of it".

### Click or drag

```properties
click-after-hint-mode.press.left=^{ismclick isrclick} _{none | modifierkey} +hintandrecursivehintkey \
  | ^{ismclick isrclick} +leftbutton \
  | _{isnohintclick} ^{ismclick isrclick} +leftbutton-0-150 -leftbutton
click-after-hint-mode.release.left=^{ismclick isrclick} +hintandrecursivehintkey-0-150 -hintandrecursivehintkey \
  | ^{ismclick isrclick hintandrecursivehintkey} +leftbutton-0-150 -leftbutton
click-after-hint-mode.to.recursive-hint1-mode=^{hintandrecursivehintkey anybutton} wait-150
```

The three `press.left` branches are the three shapes a commit can have: the hint key that picked the
cell, the `space` still held, or the `space` already tapped and released. Both `release.left` branches
carry the same `-0-150` window, which is the whole rule: **released inside it** and the button comes
back up (a click); **held longer** and nothing releases the button, so it stays pressed.

That is how you drag: let go of the key and you return to level 1 with the button still pressed, bare
cell-key taps drill to the destination, and a quick commit there releases it at that cell. *Tapping*
`space` mid-drag releases the button straight away, being itself a commit at the grid center.

`^{hintandrecursivehintkey anybutton} wait-150` is "every key that could have committed is now up", one
combo for every path. Its leading wait is measured from the last key event, so each tap restarts it —
that is the window to tap the cell key again for a **double or triple click**. The same combo drives the
seven `unset*` macros, `break-combo-preparation` and `position-history.save-position`, so "the click is
over" is stated once instead of per commit path. Note `anybutton` is the *keys*, not the mouse-button
state, so a held button never blocks the return.

### `rightalt`, going back, scrolling, magnifying

```properties
_recursive-hint-mode.to.normal-mode=_{rightalt} +recursivehintkey
_recursive-hint-mode.to.previous-mode-from-history-stack=^{recursivehintwheel} +rightalt-0-250 -rightalt
```

Hold `rightalt` and pick a cell → the pointer moves there and you land in normal mode, no click; the
drill and commit transitions are gated `^{rightalt}` so they yield. Tap `rightalt` → back out.

`backspace`/`h` goes up one level, and at level 1 leaves to idle. mousemaster keeps a stack of the region
each level rendered, so going back reproduces the grid you saw rather than recomputing it from a pointer
that has since moved.

```properties
_recursive-hint-mode.wheel=wheel-mode.wheel
_recursive-hint-mode.start-wheel.left=+7
_recursive-hint-mode.macro.unsetisnomove=_{isnomove} +hintnomove | +recursivehintwheel -> ~isnomove
_recursive-hint-mode.noop.eatrightaltandspaceforwheel=+rightalt | +leftbutton
```

`7 8 9 0` scroll with the hints still up, so you can bring a page into view and then pick a cell on it.
A wheel key also releases `isnomove`, because the wheel goes to the window under the pointer — the
pointer must move onto the region first.

The wheel config is inherited whole from wheel mode, bringing the `rightalt` = fast and `space` =
superfast mutations with it. That is why `^{recursivehintwheel}` guards `setislclick`, the two `space`
commit branches and the `rightalt` exits: **while a wheel key is held, `space` and `rightalt` are speed
modifiers, not click and cancel.** The `noop` eats their presses.

```properties
_recursive-hint-mode.macro.setzooming=+b -> #zooming
_recursive-hint-mode.zoom.center=_{zooming} -> last-selected-hint
_recursive-hint-mode.zoom.area-size-source=_{zooming} -> last-selected-hint-cell
```

Hold `b` and the current region fills the screen. `area-size-source=last-selected-hint-cell` names no
factor — it asks for the one that makes the last selected cell (which *is* this level's region) fit the
screen — so the magnification is right at every depth with no per-level number. Cell sizes are unzoomed
pixels, so the grid keeps its shape and its keys. Level 1 has no selected cell to compute from, so `b`
does nothing there.

### Skipping levels: `7` / `8` / `9`

```properties
normal-mode.macro.fasttrackrecursivehint2=^{leftalt} +7 -> #autodrilling k ~autodrilling
normal-mode.to.recursive-hint1-mode=+autodrilling
normal-mode.macro.setmouseanchored=+autodrilling -> #mouseanchored
_recursive-hint-mode.hint.visible=true | _{autodrilling} -> false
_recursive-hint-mode.macro.unsetmouseanchored=^{autodrilling} +recursivehintkey -> ~mouseanchored
normal-mode.break-combo-preparation=+middlebutton-0-150 -middlebutton-1 | ^{leftalt} +fasttrackkey
```

The pointer is usually already roughly on target, so `7`, `8` and `9` open the grid pre-drilled to one
third, one ninth or one twenty-seventh of the screen, **centered on the cursor**:

- `autodrilling` is a virtual key, and pressing it *is* the trigger for `to.recursive-hint1-mode` — the
  macro opens the grid by pressing a key that does not exist.
- It then types `k`, the center cell, once, twice or three times; each drills a level.
- `hint.visible` is false while `autodrilling` is held, so the intermediate grids are never drawn.
- `mouseanchored` rewrites `grid-area-center` from `last-selected-hint` to `mouse`, so each level
  recenters on the cursor. It survives into the destination and is released by the first *real* hint key
  press, so the grid stays on the cursor until you aim.
- `break-combo-preparation` drops the `7`/`8`/`9` press. Without it that press is still the newest event
  when the grid opens, and `7 8 9` are also `recursivehintwheel` there, so `start-wheel` would match it
  and the page would scroll. Only `SwitchMode` and the hint commands are suppressed on a mode-switch
  re-evaluation; a wheel command is not.

`^{leftalt}` keeps Alt+7/8/9 (browser tabs, IntelliJ tool windows) working.

`_recursive-hint-mode.noop=+accidentalhintkey` swallows every other letter and digit, so a mistyped key
neither dismisses the hints nor leaks into the application. Middle and right button keys are left out so
they can still arm a click.

## UI hints

```properties
ui-hint-mode.hint.type=ui-vision
ui-hint-mode.hint.box-border-color=#FFFF00 | _{isrclick} -> #00FFFF | _{ismclick} -> #FF00FF | _{rightalt} -> #FFFFFF
click-after-ui-hint-mode.release.left=^{ismclick isrclick} +uihintkey-0-150 -uihintkey | +hintback | +rightctrl
click-after-ui-hint-mode.to.ui-hint-mode=^{uihintkey anybutton mouseanimation} wait-300
```

mousemaster detects the elements on the active screen by their edges and labels each with the
`uihintkey` alphabet. Where the recursive grid is for arbitrary pixels, this is for buttons, links and
menu items, including in windows that expose no accessibility tree.

Entry: `;` + `m`/`n` from idle or surgical mode, `m`/`n` alone from arrow mode, or a **`rightshift` tap**
from normal mode (matched with `#`, so `rightshift` still reaches the application). `esc`,
`backspace`/`h`, `rightctrl`, a second `rightshift` tap, or a **`rightalt` tap** leave.

Border color is the click type, same convention as the recursive grid: **yellow** left, **magenta**
middle, **cyan** right, **white** while `rightalt` is held (move only).

`rightalt` works as in the grid: hold it and pick to move there without clicking (`^{rightalt}` gates
the commit so it yields, `noop.eatrightalt` keeps the press off the application).

Click or drag is the grid's rule too — `release.left` carries the same `-0-150` window, so a quick pick
clicks and a key held past 150 ms stays pressed, with the indicator as the mid-drag cue.
`to.ui-hint-mode` also waits for `^{mouseanimation}`, so the burst is never cut short, and its wait runs
from the last key event, which makes it the re-press window for a double or triple click: 150 ms is
shorter than a human double click, hence 300.

## Grid and snap modes

Four modes on two axes: **snap** throws the pointer at the edges of a region, **grid** bisects it; each
works on the **screen** or the **active window**.

```properties
screen-grid-mode.shrink-grid.up=^{leftshift leftctrl} +up
screen-grid-mode.move-grid.up=_{leftshift} +up
screen-grid-mode.snap.up=_{leftctrl} +up
screen-grid-mode.move-to-grid-center=+leftshift -leftshift
screen-grid-mode.grid.line-thickness=1 | _{300%} -> 0.66667
screen-grid-mode.start-move.up=_{up} -rightalt
```

The region is quartered with the pointer at its center; a bare direction key shrinks into that quadrant,
halving the distance each press. `leftshift`+direction pans the grid, `leftctrl`+direction snaps the
pointer to that edge, a `leftshift` tap recenters it.

`line-thickness=1 | _{300%} -> 0.66667` is the idiom for pinning a length to a pixel count: lengths are
100 %-screen pixels multiplied by the screen's scale, so 0.66667 × 3 is a crisp 2 px where a plain `1`
would be a blurry 3.

Releasing `rightalt` returns to normal mode, and `start-move` means a still-held direction key starts
moving in the same instant, with no gap.

Snap mode is the same region as a 1×1 grid: only the outer boundary exists, so a direction key throws
the pointer to that edge or corner. Screen snap keeps the pointer where it is
(`mouse-and-grid-center-unsynchronized`); window snap recenters it (`mouse-follows-grid-center`), which
makes `rightalt`+`;` the shortest way to say "put the pointer in this window".

`window-snap-mode.grid.area-top-inset=15` keeps snapping up on the **title bar** rather than the
invisible resize border above it. With a held `space` that is the whole window-drag workflow:
`rightalt`+`;` puts the pointer in the window, `i` on the title bar, release both keys, hold `space` to
grab, `i j k l` to move, tap `space` to drop.

The four modes cycle among themselves, and **the key that took you into a scope is the one that cycles
within it**:

| In | `p` | `;` |
|---|---|---|
| screen snap | screen grid | window snap |
| screen grid | screen snap | window grid |
| window snap | screen snap | window grid |
| window grid | screen grid | window snap |

The other key switches scope, keeping snap-ness.

`space` opens PowerToys Run, defined both in the screen grid and in the 250 ms `rightalt` window before
it, so `rightalt`+`space` works whether you were quick or slow.

### Recentering on an app switch

```properties
center-mouse-mode.grid.area=active-window
center-mouse-mode.grid.synchronization=mouse-follows-grid-center
center-mouse-mode.to.previous-mode-from-history-stack=_{isidling} wait-0
idle-mode.to.center-mouse-mode=^{nonexistingapp.exe}
```

A fifth, invisible grid mode: switching applications puts the pointer in the middle of the window you
switched to.

`^{nonexistingapp.exe}` is easy to misread. It is an app precondition on a combo with *no key moves*,
and mousemaster runs those only when the active application changes; since no process bears that name
the precondition is always true, so it means "on any app change", not "never". The mode does its work on
entry — no `line-visible` is set, so the grid is computed but never drawn — and `wait-0` leaves again on
the next frame.

## Arrow-key mode

```properties
arrowbasekey-mode.macro.pressuparrow=^{leftbutton} _{none | arrowkeymodifier} +uparrowbasekey -> +uparrow
arrowbasekey-mode.macro.releaseuparrow=-uparrowbasekey | _{uparrowbasekey} -middlebutton -> -uparrow
arrowbasekey-mode.macro.pressenter=+leftbutton -leftbutton -> +enter
```

Hold `;` and `i j k l` become arrow keys, `space` becomes Enter. The arrows are pressed and held, not
tapped, so OS auto-repeat works; the modifiers in `arrowkeymodifier` pass through, so Shift+arrow
selects and Ctrl+arrow jumps by word.

The release macro's second branch, `_{uparrowbasekey} -middlebutton`, covers letting go of `;` *first* —
without it, releasing the leader while a direction key was down would leave an arrow key stuck.

## The indicator as a status display

`_indicator-mode.indicator.render-as-cursor=true | _{rustdesk.exe} -> false` makes the indicator *be*
the system cursor image, with the real arrow glyph composited on top, instead of an overlay window
chasing the pointer. RustDesk draws the remote cursor itself, so it is excluded.

**Base color is the mode:** red normal, orange surgical and zoom, cyan typing, spring green one-shot,
magenta arrow mode. **Mouse state overrides it**, applied identically to fill, inner outline and shadow:

```properties
_indicator-mode.indicator.color=#FF0000 | _{iswheeling} -> #FFFF00 \
  | _{isleftmousepressing | leftmouseanimation} -> #00FF00 \
  | _{ismiddlemousepressing | middlemouseanimation} -> #FF00FF \
  | _{isrightmousepressing | rightmouseanimation} -> #00FFFF
_indicator-mode.indicator.shadow-opacity=0 | _{mouseburst} -> 1 | _{ismousepressing | iswheeling} ^{mouseanimation} -> 1
```

Yellow wheeling, green/magenta/cyan for a held left/middle/right button — the same three colors the hint
boxes use for the same three things. The glow exists only while something is happening.

**A click plays a burst**, built out of three virtual keys pressed together and released in sequence:

```properties
_indicator-mode.macro.clickanimation=+isleftmousepressing | +ismiddlemousepressing | +isrightmousepressing -> #mousefill #mouseburst #mouseanimation wait-0 \
  ~mousefill wait-55 ~mouseburst wait-150 wait-100 ~mouseanimation ~leftmouseanimation ~middlemouseanimation ~rightmouseanimation
_indicator-mode.indicator.size=26 | _{mouseburst} -> 2 | _{mouseanimation} ^{mouseburst} -> 44
_indicator-mode.indicator.transition-animation-duration-millis=80 | _{mousefill} -> 0 | _{mouseburst} ^{mousefill} -> 55 | _{mouseanimation} ^{mouseburst} -> 150
```

Each key is a stage, mutating `size`, `opacity` and `inner-outline-thickness`, and the matching
`transition-animation-duration-millis` branch is that stage's length — so the animation is a chain of
transitions rather than a frame loop. The three `*mouseanimation` keys hold the button's color for the
whole burst, after `isleftmousepressing` is already gone; clicking again mid-burst restarts it.
`_hint-indicator-mode` overrides `opacity` and `inner-outline-thickness` to keep the burst but drop the
resting disk, so over a hint mesh the indicator shows only while a click is ongoing.

**The label is context.** The timeout chain counts `4 3 2 1`. Typing mode shows the last one or two keys
pressed — mousemaster renders non-printable keys as symbols (⌫, ⇧, ␣, ⇞, ↑), hence the Segoe UI Symbol
font (Apple Symbols on macOS):

```properties
key-alias.typingkeys1=typingkeysandspaceandoneshotkeys
key-alias.typingkeys2=typingkeysandspaceandoneshotkeys
typing-mode.indicator.label-text= | #typingkeys1 -> typingkeys1 \
  | #typingkeys1 #{-}-0-500 #typingkeys2 -> typingkeys1 typingkeys2 \
  | #typingkeys1 #{-}-0-500 #typingkeys2 #{-}-250 -> typingkeys2
```

`typingkeys1` and `typingkeys2` are the *same key list under two names*: an alias binds to one key per
combo, so one alias used twice would insist both moves matched the same key. Two names let the label
show two different keys. `#{-}` ignores releases, so presses drive the display, and the third branch
collapses the pair to the latest key after 250 ms of quiet. `bigtypingkeys` (function keys, `backspace`,
`pageup`, …) drop a point because their glyphs draw larger, with the same two-name trick.

One-shot mode shows the stacked modifiers:
`label-text= | {#*oneshotkeysandrightctrl+ #{-}-0-2000} -> oneshotkeysandrightctrl` — at least one press
from the expanded alias in any order, the label being the keys that matched.

`hide-cursor.enabled=true` in the timeout chain, typing mode and one-shot mode drops the arrow glyph, so
the pointer is *only* the colored badge with its label.

## App-specific macros

A few macros reach into specific applications, all through `macro-hint-mode`, an invisible hint grid of
~10 × 10 px cells over the active screen:

```properties
idle-mode.to.macro-hint-mode=+f13
macro-hint-mode.hint.visible=false
macro-hint-mode.hint.grid-cell-width=9.8
macro-hint-mode.hint.layout-row-count=4
macro-hint-mode.hint.layout-column-count=10
macro-hint-mode.press.left=+space
```

Forty selection keys over a 4×10 subgrid layout label every cell with a subgrid prefix plus one cell key
— three keystrokes at the author's resolution. So `f13`, three keys, `space` clicks any point on screen.
That is how the Firefox proxy toggles (`f7`/`f6`) click two toolbar buttons 300 ms apart, and how
`space`+`i` deals with RustDesk:

```properties
idle-mode.macro.wintabrustdesk=_{rustdesk.exe} ^{oneshotkeys} +space-0-1000 +i | _{rustdesk.exe} _{space} +i -> f13 1 7 u space
macro-hint-mode.to.minimize-rustdesk-mode=_{rustdesk.exe} +1 -1 +7 -7 +u -u +space -space
minimize-rustdesk-mode.macro.wintab=^{rustdesk.exe} ^{oneshotkeys} -> wait-100 +leftwin +tab -tab -leftwin wait-100 +uparrow -uparrow
minimize-rustdesk-mode.to.idle-mode=^{rustdesk.exe} | _{isidling} #{*}-500
```

A full-screen remote desktop swallows Win+Tab, so the macro clicks RustDesk's own minimize button via
the pixel grid, and a second mode waits for RustDesk to stop being active before sending Win+Tab. The
`^{rustdesk.exe}` precondition *is* the wait — no polling, no fixed delay — and the second branch of the
same property is the failsafe: 500 ms of nothing and it gives up.

`minecraft-mode` is a small game remap in the same spirit: with `javaw2.exe` active, `;`+`space`, `;`+`j`
and `;`+`k`/`i` become F13, F15 and the wheel.

## Two conventions used everywhere

**`+never` deletes an inherited combo.** `never` is declared with `virtual-key.never=released` and
pressed by no macro, so a combo requiring it can never complete. Used wherever a mode inherits nearly
everything from another but must not inherit one line.

**No implicit combo timeout.** `default-combo-move-duration-millis` is left commented out, so moves have
no maximum gap unless one is written down — every 150, 250, 500, 1000 and 2000 in the file is deliberate
and local.

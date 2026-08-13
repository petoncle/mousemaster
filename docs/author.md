# Author's configuration of mousemaster ([author.properties](../configuration/author.properties))

(See [configuration-reference.md](configuration-reference.md) for the property list and
[combo-reference.md](combo-reference.md) for the combo syntax.)

A right-hand-only pointer: `i` `j` `k` `l` move, the keys around them click, and every mode is reached
without the hand leaving the home row. The left hand stays free for modifiers. This document explains
*why* the bindings are shaped the way they are; the obvious ones are left to the file.

Two external tools are assumed: **[Alt+Tab Terminator](https://www.ntwind.com/software/alttabter.html)**
(a Win+Tab switcher with a type-to-search box, which several macros type into) and **PowerToys Run** on
Ctrl+Alt+Space.

## Design premises

1. **The right hand does not move.** `i k j l` are up/down/left/right; `space` (left button), `;` `'` `#`
   (middle), `p` `[` `]` (right), `u o m , .` (hint grid), `7 8 9 0` (scroll, and `7 8 9` the hint-grid
   fast-track), `h`/`backspace` (back) are all within reach of the same fingers.
2. **One key, several meanings, resolved by what comes next.** `rightalt` tapped enters normal mode;
   held, the grid; tapped again, the hint grid; with a direction key, "faster"; with `p` or `;`, a snap.
   Nothing is decided on the press.
3. **Typing must never be damaged.** Normal mode exits on any unhandled key press; `rightctrl` and
   `capslock` are matched without being eaten; `leftalt`+`space` is carved out of the left-click binding
   so the Windows system menu still opens; and `typing-mode` exists solely to get the `space` leader out
   of the way while typing.
4. **State lives in virtual keys, not modes.** A tap of `;` presses `ismclick`, which recolors every
   hint box magenta *and* rewrites which combo commits the click — with no mode switch, so the hint mesh
   is not rebuilt.

```properties
virtual-keys=autodrilling islclick ismclick isrclick isnomove isnohintclick islongpresshintkey \
             mouseanchored showrecursivehintkeys zooming never
```

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
it would shadow a real binding. Note `x` is *not* trimmed, so there is no cut to shadow.

Every alias is declared with the layout its key *names* belong to (`key-alias.hintkey.uk-qwerty=…`,
plus `configuration-keyboard-layout=uk-qwerty`). mousemaster maps those names through scan codes to the
active layout, so switching between UK QWERTY and FR AZERTY keeps every combo under the same finger.

## The mode map

Only idle and normal mode are pushed onto the mode history stack, so every transient mode's
`to.previous-mode-from-history-stack` lands on one of those two and never on another transient mode.
`oneshot-mode.push-mode-to-history-stack=false` keeps a modifier tap out of the stack.

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
| `ui-hint-mode` (+ `click-after-` / `hold-after-`) | UI Automation hints |
| `position-history-mode` | saved positions, shown while `m` is held |
| `arrowbasekey-mode` | `i j k l` emit arrow keys |
| `macro-hint-mode` | an invisible ~10 px hint grid macros use to click fixed pixels |

## Idle mode — the launcher

Nothing is remapped; four keys are leaders.

**`rightalt`** — tap → normal mode; hold 250 ms → screen grid; `+;` → window snap (pointer to the
window center); `+p` → screen snap. The first two carry `_{none | modifierkey}`, so Shift+`rightalt`
and friends still reach applications.

**`space`** — hold and press a second key within a second:

| Combo | Effect |
|---|---|
| `space` `l` / `space` `k` | close / minimize the active window (`leftalt`+`space` then `c` / `n`) |
| `space` `i` | Win+Tab task view |
| `space` `n` | Firefox tab search: clicks a toolbar button, Ctrl+L twice, clears, types `% ` |
| `space` `8` / `space` `9` | switch to VLC / Firefox by typing into the Alt+Tab Terminator search box |

The app-switch macros contain a small trick — a throwaway `q` followed by `backspace` focuses the
switcher's search box (typing anything moves focus into it) and then clears it before the real query.
All of them are guarded with `^{oneshotkeys}`: if a real modifier is held, the macro yields.

**`;`** — hold and press `i j k l` for arrow keys, or `m`/`n` for UI hints. The entry combo
`{+middlebutton #arrowkeymodifier?} +anyarrowbasekey` is an any-order set, so Ctrl+`;`+`j` is Ctrl+Left
whichever of the two keys goes down first.

**A tapped modifier** (`leftshift`, `leftctrl`, `leftalt`, `rightshift`, `rightctrl`) enters one-shot mode.

## One-shot modifiers

Tap a modifier — do not hold it — and it applies to the next key, for up to two seconds. Taps stack.

```properties
oneshot-mode.macro.oneshot={*oneshotkeysandrightctrl+} #!{moddablekey oneshotkeysandrightctrl space}-0-2000 +moddablekey \
  -> +oneshotkeysandrightctrl +moddablekey -oneshotkeysandrightctrl
```

`{*alias+}` expands to one tap move per modifier and requires at least one, in any order — that is what
turns three separate taps into Ctrl+Shift+A. `#!{…}-0-2000` then ignores everything that is not a
modifier or a moddable key, so a stray release does not break the chain, and the whole thing expires
after 2 s.

Two escape hatches: **double-tap** a modifier within 200 ms (`macro.redo-doubletap`) and it is genuinely
pressed and left down; and `oneshot-mode.to.typing-mode` has five branches that all mean "the user is
typing normally now" — a modifier re-pressed after release, a modifier held while another arrives, a
non-moddable key, or two seconds of silence.

`oneshot-mode.macro.alttick` inserts a `wait-0` between `leftalt` and the key in IntelliJ only, which
does not accept them in the same input batch.

## Typing mode, and why it has to exist

`space` being a leader costs something: mousemaster must hold the `space` press back until it knows
whether `space` `l` is coming, then replay it. Replayed input does not work everywhere — inside
VirtualBox it is dropped — and typing `space` `i` quickly in a sentence would fire the task-view macro.

Typing mode is idle mode *without* the `space` macros:

```properties
idle-mode.to.typing-mode=_{none | modifierkey} ^{space middlebutton} #typingkeys
typing-mode.to=idle-mode.to
typing-mode.to.typing-mode=+never
typing-mode.to.idle-mode=#!typingkeysandspaceandoneshotkeys | ^{typingkeys} wait-500
```

Any letter, digit, punctuation, arrow, function key or `backspace` enters it; `^{space middlebutton}`
means a leader sequence already in progress is not interrupted. `typing-mode.to=idle-mode.to` copies
idle mode's transitions but not its `macro` properties, so `space` is an ordinary character again with
no delay. `+never` is the idiom for deleting an inherited transition (`never` is a declared virtual key
no macro presses), here so typing mode does not re-enter itself. 500 ms after the last typing key, or
immediately on any non-typing key, the leaders come back.

The `tts1`/`tts2` macros (`capslock`+`space`, `tab`+`space` → F14/F15) are defined identically in idle,
normal *and* typing mode precisely because typing mode does not inherit macros.

## Normal mode

### Moving

The pointer eases from 200 px/s to 1200 over 3000 px/s² with a smootherstep curve, so taps are precise
and holds cross the screen; deceleration is 20, leaving a slight glide. Jumps — grid snaps, hint
commits, history jumps — are animated at 30000 px/s rather than teleporting, which is what makes them
followable.

`start-move` carries `^{rightalt}`, so with `rightalt` held a direction key does *not* start moving —
that combination means something else. `_{none | modifierkey}` allows moving with Shift or Ctrl already
down, ready for the click that ends the movement.

### `rightalt` decides late

With a direction key already held → **fast mouse mode** (2000 px/s initial, 3000 max); release
`rightalt` and normal speed resumes mid-glide. Otherwise → `temp-screen-snap-mode`, a holding pattern
that outlines the active screen in red (a 1×1 grid, no background, unsynchronized so the pointer stays
put) and waits:

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
press restarts it — a plain `wait-250` would measure from the last key event. With no `_{isidling}` the
grid appears even while a mouse button is held.

So a **`rightalt` tap in normal mode opens the hint grid**: the press enters the holding pattern, the
release completes `+rightalt -rightalt`. It is written as press *and* release so the press is eaten —
with a menu open, letting `alt` through would close the very thing you opened the hints to click. The
red outline earns its keep on a multi-monitor setup: it says which screen all of this applies to.

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
  `leftalt` has been held 250 ms — long enough to prove you were not going for the shortcut. `#leftalt`
  does not eat, so the OS sees the Alt press either way.
- **`m` may be held** (`positionhistorykey`), which is what lets you click while the position-history
  hints are up.
- **Holding `space` leaves the button pressed.** The release only matches a press shorter than 250 ms;
  hold longer and the button stays pressed after you let go — grab a scrollbar, move with `i j k l`, tap
  `space` to drop. Right button behaves the same.

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
mode, with no ambiguity and no timeout to sit through. The trailing `-1` makes `release.middle` one
millisecond longer than `press.middle` so they fire in order, and `break-combo-preparation` clears the
events so they cannot re-match. `'` and `#` are middle click only, with no surgical detour.

### Wheel mode

**Double-tap a direction key** — press, release, press — and that second press starts continuous
scrolling. The first tap already started a pointer movement, so `stop-move` and `start-wheel` both react
to the same triple, one cancelling and one starting. `start-move` stays bound, so a *different*
direction key still moves the pointer while the first scrolls.
`wheel-mode.to.normal-mode=^{directionkey}` is precondition-only: it fires the instant no direction key
is held, with no event at all.

```properties
wheel-mode.wheel.max-velocity=1000 | _{rightalt} -> 10000 | _{leftbutton} -> 100000000
wheel-mode.wheel.acceleration=500 | _{rightalt} -> 10000
wheel-mode.noop.eatfastandsuperfast=+rightalt | +leftbutton
```

`rightalt` gives 10× the speed and 20× the acceleration; `space` a velocity large enough to reach the
end of the document. The `noop` eats both presses so they cannot mean anything else while scrolling.

### Copy, paste, and leaving

```properties
_clipboard-mode.macro.copy=^{oneshotkeys} +copy -> +leftctrl +copy -copy -leftctrl
_clipboard-mode.macro.paste=^{oneshotkeys} +paste -> +leftctrl +paste -paste -leftctrl
normal-mode.macro=_clipboard-mode.macro
surgical-mode.macro=_clipboard-mode.macro
arrowbasekey-mode.macro=_clipboard-mode.macro
_recursive-hint-mode.macro=_clipboard-mode.macro
```

`c` and `v` send Ctrl+C and Ctrl+V, guarded with `^{oneshotkeys}` so Shift+`c` still types a capital C.
They live on an abstract `_clipboard-mode` so every mode that should have them can reference it in one
line: a referenced macro map **merges by macro name**, keeping the referencing mode's own macros and
skipping any parent macro whose name it already defines. Referencing `normal-mode.macro` instead would
drag in `fasttrackrecursivehint2`, whose `+7` is the hint grid's scroll key — which is why those
references sit commented out in the file.

Mode inheritance then fans it out: the four countdown modes get it from `normal-mode`, `zoom-mode` from
`surgical-mode`, and `recursive-hint1..4-mode` from `_recursive-hint-mode`. The line is **every mode
where mousemaster owns the pointer and a selection can be in progress**. Idle and typing mode are
excluded because `c` must type a `c` there, and `macro-hint-mode` because `c` is one of its selection
keys. The held positioning modes (snap, grid, position history) are excluded too: releasing the key that
holds them already lands you in normal mode, where copy works.

```properties
normal-mode.to.idle-mode=#rightctrl | #hintback
normal-mode.mode-after-unhandled-key-press=idle-mode
normal-mode.noop.capslocknoop=#capslock
```

`rightctrl`, `h` and `backspace` leave immediately, all matched with `#` so the key still reaches the
application — pressing `h` exits normal mode *and* types an `h`, which is what you want when you started
typing without thinking. Everything else is caught by `mode-after-unhandled-key-press`. Which is why
`capslock` needs a `noop`: it belongs to no combo, so without that line it would kick you out; the
`noop` marks it handled while `#` still passes it through.

The fourth exit is the timeout, built as a chain so it can be *watched*:

```properties
normal-mode.to.normal-timeout4-mode=_{isidling} wait-1000
_normal-timeout-mode=normal-mode
_normal-timeout-mode.hide-cursor.enabled=true
_normal-timeout-mode.to.normal-timeout4-mode=+never
_normal-timeout-mode.to.normal-mode=^{isidling}
normal-timeout4-mode.indicator.label-text=4
normal-timeout4-mode.to.normal-timeout3-mode=wait-1000
```

One second of `isidling` (built-in: not moving, not wheeling, no button held) enters a full copy of
normal mode showing `4`; each further second counts down, and `normal-timeout1-mode` drops to idle.
Any mouse activity satisfies `^{isidling}` and restarts the count from scratch. `hide-cursor` drops the
arrow glyph, leaving a bare colored dot with a digit in it. A mode that leaves on its own is one you
stop trusting unless you can watch it about to happen — and stop it with a nudge.

## Surgical and zoom

```properties
normal-mode.to.surgical-mode=+;
surgical-mode.mouse.max-velocity=200
zoom-mode=surgical-mode
normal-mode.to.zoom-mode=+n
zoom-mode.zoom.percent=2.0 | _{middlebutton} -> 5.0
zoom-mode.mouse.max-velocity=200 | _{middlebutton} -> 50
```

Two graded slow modes, each held: `;` for 200 px/s instead of 1200 with no magnifier, `n` for the same
slow pointer plus a 2× magnifier on the cursor (animated over 300 ms). Add `;` inside zoom mode for 5×
and 50 px/s; `zoom-mode.noop=+middlebutton` eats it so it only drives the two mutations, and surgical
mode's disabled middle click is inherited. `zoom-mode.indicator.render-as-cursor=false` — a true-size
system cursor would not match the magnified content beneath it.

## Position history

```properties
normal-mode.to.position-history-mode=^{directionkey} +positionhistorykey-0
position-history-mode.to.position-history-mode=-rightalt | +rightalt-500 | +positionhistoryhintkey | +positionhistoryhintkey-500
```

Hold `m` and the saved positions appear as hints labeled with `i j k l` (`max-size=16` over 4 keys: at
most two keystrokes). Release `m` to return. `^{directionkey}` keeps it from opening mid-movement.

| While `m` is held | Effect |
|---|---|
| tap `rightalt` (< 500 ms) | save the current position |
| hold `rightalt` 500 ms | clear the list |
| tap a hint key | jump there |
| hold a hint key 500 ms | delete that position |

The self-transition above is the load-bearing line: each of those four actions re-enters the mode, which
rebuilds the mesh. Without it a save would not appear as a hint and a jump would leave a stale
selection.

`position-history.isolation=active-app` gives every application its own list, and both click-follow-up
modes have a `position-history.save-position`. So the list is not curated by hand: **every hint click
records where it landed**, per application. `press`/`release` are inherited from normal mode, so you can
click without releasing `m` — hence `positionhistorykey` in normal mode's button preconditions.

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
rather than out — a run of clicks in different places is one uninterrupted flow.

Entry resets what a previous session left behind, as six one-line macros on `+autodrilling` rather than
one bulk reset — a bulk reset would also clear `showrecursivehintkeys`, and the look you chose is meant
to survive.

### Geometry

```properties
_recursive-hint-mode.hint.grid-area=last-selected-hint-cell
_recursive-hint-mode.hint.grid-area-center=last-selected-hint | _{mouseanchored} -> mouse
_recursive-hint-mode.hint.grid-max-row-count=3
_recursive-hint-mode.hint.grid-cell-sizing=fit
recursive-hint1-mode.hint.grid-area=active-screen
```

`grid-area=last-selected-hint-cell` is what makes it recursive: each level's area *is* the cell picked
above. `fit` computes cell sizes to fill that area exactly, whatever its size, so the pixel dimensions
are ignored. Level 1 overrides the area to the whole screen.

Text shrinks with the region — 60, 20, 7 and 3 point cell labels at levels 1 to 4, shadow blur 10, 3, 1,
none. Since a hint font size is given in 100 %-screen pixels and grown by the screen's scale, none of
that needs a per-screen override.

### Two looks, toggled by `capslock`

```properties
_recursive-hint-mode.hint.font-opacity=1 | ^{showrecursivehintkeys} -> 0
_recursive-hint-mode.hint.decoration-label-override=plus
_recursive-hint-mode.hint.decoration-font-opacity=0 | ^{showrecursivehintkeys} -> 1
_recursive-hint-mode.hint.subdecoration-label-keys=recursivehintkey
_recursive-hint-mode.hint.subdecoration-label-override=^{showrecursivehintkeys} -> .
```

The virtual key starts released, so the default is the **dots** look: cell letters hidden, one large `+`
centered over the region (a 1×1 decoration whose label is overridden to `plus`), and a 3×3 lattice of
dots in each cell previewing where the next level's cells will be. The crosshair says where a commit
with no cell pick lands; the dots say what one more keypress buys.

Tap `capslock` for the **keys** look: every cell shows its letter, the crosshair goes, and each cell's
3×3 preview is labeled with the nine next-level keys. Note the polarity — the interesting mutations
hang off `^{showrecursivehintkeys}` (*not* pressed), because the minimal look is the one you live in.

### Drilling does not move the pointer

`isnomove` is pressed on entry. Whether the pointer follows is a four-branch mutation:

```properties
_recursive-hint-mode.hint.mouse-movement=mouse-follows-hint-grid-center \
  | ^{recursivehintwheel} _{isnomove rightalt} -> mouse-follows-selected-hint \
  | _{isnomove islclick | isnomove ismclick | isnomove isrclick} ^{islongpresshintkey isnohintclick rightalt} -> mouse-follows-selected-hint \
  | _{isnomove} ^{islclick ismclick isrclick islongpresshintkey rightalt} -> no-movement
```

The three values differ in *when* they move, which is the key to reading it:

| Value | On arriving at a level | On the final cell pick |
|---|---|---|
| `mouse-follows-hint-grid-center` | yes, to the region's center | yes, to the cell |
| `mouse-follows-selected-hint` | no | yes, to the cell |
| `no-movement` | no | no |

Rightmost matching branch wins and the four are mutually exclusive, so it reads as a decision table:

- **plain drilling** → `no-movement`. The pointer stays put while you narrow down, so hover states,
  tooltips and whatever you were pointing at survive and nothing jumps around.
- **a click armed** → `mouse-follows-selected-hint`: still no movement while drilling, but the final
  pick lands on the cell.
- **`rightalt` held** → the same, since `rightalt` + a pick means "move there without clicking".
- **anything else** — `isnomove` toggled off, a wheel key held, `islongpresshintkey` or `isnohintclick`
  set — falls through to grid-center, so the pointer rides the middle of the region. Those last two are
  commits carrying *no* cell pick, and the pointer has to arrive before `click-after-hint-mode` clicks.

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
level deeper or clicks — decided by whether you tapped a button key first. There is no confirm key.

A tap of `;` or `p` does not move the pointer: arm, drill as deep as you like, and the pointer moves at
the pick. `space` is the exception.

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

**Pressing `space` does two things at once**: it presses `islclick` (arming, cells green) *and*
`isnohintclick`, which pushes the movement mutation to grid-center — re-rendering the mesh and
**snapping the pointer to the middle of the current region**. That is not a side effect; it is the
preview of where a click with no cell pick would land. From there:

1. **Tap `space` and let go quickly** (third branch) → a click at the region center. `#{recursivehintkey}`
   ignores hint-key events inside the window so the combo survives a keypress handled elsewhere.
2. **`space` then a hint key** (first branch) → the hint-key press clears `isnohintclick`, flipping
   movement back to `mouse-follows-selected-hint`; because a virtual key pressed at the start of a
   macro's output takes effect before the combo's other commands, the pick already sees the new value.
   The pointer jumps to that cell and clicks. **`space` must still be down and the hint key must arrive
   within 250 ms** — after that the long-press branch has fired and the button is left pressed at the
   center instead.
3. **Long-press `space`** (second branch) → at 250 ms the commit fires with no cell pick, at the region
   center, and since `space` has been held past 150 ms the button stays pressed, which is how a drag
   starts.

Middle and right are the same shape without a tap-to-commit branch: a **tap** of `;` or `p` arms, a
**250 ms hold** commits at the region center.

**Long-pressing a hint key** (`+recursivehintkey-250`) is a fourth path. Its press has already drilled a
level, so the commit happens in the *deeper* grid, and `setislongpresshintkey` flips movement to
grid-center so the pointer arrives at that region's center first. In effect: hold a hint key to mean
"go in there and click the middle of it".

### Click or drag

```properties
click-after-hint-mode.press.left=^{ismclick isrclick} _{none | modifierkey} +hintandrecursivehintkey \
  | ^{ismclick isrclick} +leftbutton \
  | _{isnohintclick} ^{ismclick isrclick} +leftbutton-0-150 -leftbutton
click-after-hint-mode.release.left=^{ismclick isrclick} +hintandrecursivehintkey-0-150 -hintandrecursivehintkey \
  | ^{ismclick isrclick hintandrecursivehintkey} +leftbutton-0-150 -leftbutton
click-after-hint-mode.to.recursive-hint1-mode=^{hintandrecursivehintkey anybutton} wait-150
```

The three `press.left` branches cover the three shapes a commit can have: the hint key that picked the
cell, the `space` still held, or the `space` already tapped and released. Both `release.left` branches
then carry the same `-0-150` window, which is the whole click-or-drag rule:

- **released within 150 ms** — a quick cell pick, or a `space` tap — the window matches, the button comes
  back up, and you get a **click**.
- **held longer** — a cell key held past the 250 ms long-press commit, or `space` held past it — the
  window has closed and nothing releases the button, so it **stays pressed**.

A button left pressed is how you drag: let go of the key and you return to level 1 with it still
pressed, bare cell-key taps drill to the destination without disturbing it, and a quick commit there
(hold `space`, pick the cell, release) releases the button at that cell. Note that *tapping* `space`
mid-drag releases the button straight away, because a `space` tap is itself a commit at the grid center.

Returning to level 1 is one combo for every path: `^{hintandrecursivehintkey anybutton}` is "every key
that could have committed is now up", and its leading `wait-150` restarts whenever one of those keys
goes down — which is what gives you the window to tap the cell key again for a **double or triple
click** without the grid coming back. The same combo drives the seven `unset*` macros,
`break-combo-preparation` and `position-history.save-position`, so "the click is over" is stated once
instead of enumerated per commit path.

The cue for being mid-drag is the indicator, invisible in the hint grid unless a button is held
(`indicator.opacity=0 | _{ismousepressing} -> 0.2`). Note `anybutton` is the *keys* `space ; ' # p [ ]`,
not the mouse-button state, so a held button never blocks the return. And `_{none | modifierkey}` on
`press.left` means **Shift-click and Ctrl-click work on a hint cell**.

### `rightalt`, going back, scrolling, magnifying

```properties
_recursive-hint-mode.to.normal-mode=_{rightalt} +recursivehintkey
_recursive-hint-mode.to.previous-mode-from-history-stack=^{recursivehintwheel} +rightalt-0-250 -rightalt
```

Hold `rightalt` and pick a cell → the pointer moves there and you land in normal mode, no click; the
drill and commit transitions are gated `^{rightalt}` so they yield. Tap `rightalt` → back out.

`backspace`/`h` goes up one level, and at level 1 leaves to idle. Going back reproduces the grid you saw
exactly: mousemaster keeps a stack of the region each level rendered, so the area is not recomputed from
a pointer that has since moved.

```properties
_recursive-hint-mode.wheel=wheel-mode.wheel
_recursive-hint-mode.start-wheel.left=+7
_recursive-hint-mode.macro.unsetisnomove=_{isnomove} +hintnomove | +recursivehintwheel -> ~isnomove
_recursive-hint-mode.noop.eatrightaltandspaceforwheel=+rightalt | +leftbutton
```

`7 8 9 0` scroll left, down, up, right with the hints still up, so you can bring a page into view and
then pick a cell on it. A wheel key also releases `isnomove` — necessary, not cosmetic: the wheel goes
to the window under the pointer, so the pointer must move onto the region first.

The wheel config is inherited whole from wheel mode, bringing the `rightalt` = fast and `space` =
superfast mutations with it. That is why `^{recursivehintwheel}` guards `setislclick`, the two `space`
commit branches and the `rightalt` exits: **while a wheel key is held, `space` and `rightalt` are speed
modifiers, not click and cancel.** The `noop` eats their presses.

```properties
_recursive-hint-mode.macro.setzooming=+b -> #zooming
_recursive-hint-mode.zoom.center=_{zooming} -> last-selected-hint
_recursive-hint-mode.zoom.area-size-source=_{zooming} -> last-selected-hint-cell
```

Hold `b` and the current region blows up to fill the screen. `area-size-source=last-selected-hint-cell`
names no factor — it asks for the one that makes the last selected cell (which *is* this level's region)
fit the screen — so the magnification is right at every depth with no per-level number. Cell sizes are
unzoomed pixels, so the grid keeps its shape and its keys. Level 1 has nothing to magnify and no
selected cell to compute from, so `b` does nothing there.

### Skipping levels: `7` / `8` / `9`

```properties
key-alias.fasttrackkey=7 8 9
normal-mode.macro.fasttrackrecursivehint2=^{leftalt} +7 -> #autodrilling k ~autodrilling
normal-mode.to.recursive-hint1-mode=+autodrilling
normal-mode.macro.setmouseanchored=+autodrilling -> #mouseanchored
_recursive-hint-mode.hint.visible=true | _{autodrilling} -> false
_recursive-hint-mode.macro.unsetmouseanchored=^{autodrilling} +recursivehintkey -> ~mouseanchored
normal-mode.break-combo-preparation=+middlebutton-0-150 -middlebutton-1 | ^{leftalt} +fasttrackkey
```

The pointer is usually already roughly on target, so starting from a whole-screen grid is wasted work.
`7`, `8` and `9` open the grid pre-drilled to one third, one ninth or one twenty-seventh of the screen,
**centered on the cursor**. Five features at once:

- `autodrilling` is a virtual key, and pressing it *is* the trigger for `to.recursive-hint1-mode` — the
  macro opens the grid by pressing a key that does not exist.
- It then types `k`, the center cell, once, twice or three times (`+7`/`+8`/`+9`); each drills a level.
- `hint.visible` is false while `autodrilling` is held, so the intermediate grids are never drawn.
- `mouseanchored` rewrites `grid-area-center` from `last-selected-hint` to `mouse`, so each level
  recenters on the cursor instead of on the picked cell. It survives into the destination and is
  released only by the first *real* hint key press, so the grid stays on the cursor until you aim.
- `break-combo-preparation` drops the `7`/`8`/`9` press. Without it that press is still the newest event
  in the preparation when the grid opens, and `7 8 9` are also `recursivehintwheel` there, so
  `start-wheel` would match it and the page would scroll until you let go. Only `SwitchMode` and the
  hint commands are suppressed on a mode-switch re-evaluation; a wheel command is not.

`^{leftalt}` keeps Alt+7/8/9 (browser tabs, IntelliJ tool windows) working.

`_recursive-hint-mode.noop=+accidentalhintkey` swallows every other letter and digit, so a mistyped key
does nothing at all — it neither dismisses the hints nor leaks into the application. Middle and right
button keys are left out so they can still arm a click.

## UI hints

```properties
ui-hint-mode.hint.type=ui
ui-hint-mode.hint.box-border-color=#FFFF00 | _{isrclick} -> #00FFFF | _{ismclick} -> #FF00FF | _{rightalt} -> #FFFFFF
click-after-ui-hint-mode.release.left=^{ismclick isrclick uihintkey} | +hintback | +rightctrl
click-after-ui-hint-mode.to.ui-hint-mode=wait-250
```

Instead of a geometric grid, mousemaster asks UI Automation for the clickable elements of the active
window and its popups (`hint.ui-area=active-window`) and labels each with `i j k l m o n`. Where the
recursive grid is for arbitrary pixels, this is for buttons, links and menu items.

Entry: `;` + `m`/`n` from idle or surgical mode, `m`/`n` alone from arrow mode, or a **`rightshift` tap**
from normal mode (matched with `#`, so `rightshift` still reaches the application). `esc`,
`backspace`/`h`, `rightctrl` or a second `rightshift` tap leave.

Border color is the click type, same convention as the recursive grid: **yellow** left, **magenta**
middle, **cyan** right, **white** while `rightalt` is held (move only). Labels are white Consolas on
`#204E8A` so they read over any background.

The click fires on the hint key press and releases as soon as all hint keys are up — `release.left` is
precondition-only, needing no event of its own. Hold the key 250 ms and the button stays pressed. And
250 ms after a click the UI is queried again and the hints return, so three menu items in a row is
three keystrokes.

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

The region is quartered (`row-count`/`column-count=2`) with the pointer at its center; a bare direction
key shrinks into that quadrant, halving the distance each press, with a 100 ms animation to follow. The left hand adds variants:
`leftshift`+direction pans the grid, `leftctrl`+direction snaps the pointer to that edge of it, and a
`leftshift` tap recenters the pointer.

`line-thickness=1 | _{300%} -> 0.66667` is the idiom for pinning a length to a pixel count: lengths are
100 %-screen pixels multiplied by the screen's scale, so 0.66667 × 3 is a crisp 2 px where a plain `1`
would be a blurry 3.

Releasing `rightalt` returns to normal mode, and `start-move` means a still-held direction key starts
moving in the same instant — the grid hands off to free movement with no gap.

Snap mode is the same region as a 1×1 grid: only the outer boundary exists, so a direction key throws
the pointer to that edge or corner. Screen snap keeps the pointer where it is
(`mouse-and-grid-center-unsynchronized`); window snap recenters it (`mouse-follows-grid-center`), which
makes `rightalt`+`;` the shortest way to say "put the pointer in this window".

`window-snap-mode.grid.area-top-inset=15` keeps snapping up on the **title bar** rather than the
invisible resize border above it. With a held `space` that is the whole window-drag workflow:
`rightalt`+`;` puts the pointer in the window, `i` on the title bar, releasing both keys returns to
normal mode, then hold `space` to grab, `i j k l` to move, tap `space` to drop.

The four modes cycle among themselves, and **the key that took you into a scope is the one that cycles
within it**:

| In | `p` | `;` |
|---|---|---|
| screen snap | screen grid | window snap |
| screen grid | screen snap | window grid |
| window snap | screen snap | window grid |
| window grid | screen grid | window snap |

You entered screen scope with `p`, so `p` keeps toggling snap and grid there; you entered window scope
with `;`, so `;` does the same on that side. The other key switches scope, keeping snap-ness.

`space` in the screen grid, and in the 250 ms `rightalt` window before it, opens PowerToys Run — defined
in both so `rightalt`+`space` works whether you were quick or slow.

### Recentering on an app switch

```properties
center-mouse-mode.grid.area=active-window
center-mouse-mode.grid.synchronization=mouse-follows-grid-center
center-mouse-mode.to.previous-mode-from-history-stack=_{isidling} wait-0
idle-mode.to.center-mouse-mode=^{nonexistingapp.exe}
```

A fifth, invisible grid mode: switching applications puts the pointer in the middle of the window you
switched to, so it is never left behind on the one you came from.

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
selects and Ctrl+arrow jumps by word. Release `;` to return.

The release macro's second branch, `_{uparrowbasekey} -middlebutton`, covers letting go of `;` *first* —
without it, releasing the leader while a direction key was down would leave an arrow key stuck.

## The indicator as a status display

`normal-mode.indicator.render-as-cursor=true | _{rustdesk.exe} -> false` makes the indicator *be* the
system cursor image, with the real arrow glyph composited on top, instead of an overlay window chasing
the pointer — nothing can lag behind because there is nothing to chase. RustDesk draws the remote cursor
itself, so it is excluded.

**Base color is the mode:** red normal, orange surgical and zoom, cyan typing, spring green one-shot,
magenta arrow mode. **Mouse state overrides it**, applied identically to fill, inner outline and shadow:

```properties
normal-mode.indicator.color=#FF0000 | _{iswheeling} -> #FFFF00 \
  | _{isleftmousepressing} -> #00FF00 | _{ismiddlemousepressing} -> #FF00FF | _{isrightmousepressing} -> #00FFFF
normal-mode.indicator.shadow-opacity=0 | _{iswheeling | ismousepressing} -> 1
```

Yellow wheeling, green/magenta/cyan for a held left/middle/right button — the same three colors the hint
boxes use for the same three things. The glow exists only while something is happening, so an idle
pointer is a flat dot.

**The label is context.** The timeout chain counts `4 3 2 1` at 16 pt. Typing mode shows the last one or
two keys pressed — mousemaster renders non-printable keys as symbols (⌫, ⇧, ␣, ⇞, ↑), hence the Segoe UI
Symbol font (Apple Symbols on macOS):

```properties
key-alias.typingkeys1=typingkeysandspaceandoneshotkeys
key-alias.typingkeys2=typingkeysandspaceandoneshotkeys
typing-mode.indicator.label-text= | #typingkeys1 -> typingkeys1 \
  | #typingkeys1 #{-}-0-500 #typingkeys2 -> typingkeys1 typingkeys2 \
  | #typingkeys1 #{-}-0-500 #typingkeys2 #{-}-250 -> typingkeys2
```

`typingkeys1` and `typingkeys2` are the *same key list under two names*, and that is the trick: an alias
binds to one key per combo, so one alias used twice would insist both moves matched the same key. Two
names let the label show two different keys. `#{-}` ignores releases, so presses drive the display, and
the third branch collapses the pair to the latest key after 250 ms of quiet. `bigtypingkeys` (function
keys, `backspace`, `pageup`, …) drop a point because their glyphs draw larger, with
`bigtypingkeys1`/`bigtypingkeys2` doing the same two-name trick.

One-shot mode shows the stacked modifiers:
`label-text= | {#*oneshotkeysandrightctrl+ #{-}-0-2000} -> oneshotkeysandrightctrl` — at least one press
from the expanded alias in any order, with the label being the keys that matched.

`hide-cursor.enabled=true` in the timeout chain, typing mode and one-shot mode drops the arrow glyph from
the composited cursor, so the pointer is *only* the colored badge with its label: a digit during the
countdown, the key you just pressed while typing, the pending modifiers in one-shot mode.

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

A full-screen remote desktop swallows Win+Tab, so the macro clicks RustDesk's own minimize button via the
pixel grid, and a second mode waits for RustDesk to stop being active before sending Win+Tab. The
`^{rustdesk.exe}` precondition *is* the wait — no polling, no fixed delay — and the second branch of the
same property is the failsafe: 500 ms of nothing and it gives up.

`minecraft-mode` is a small game remap in the same spirit: with `javaw2.exe` active, `;`+`space`, `;`+`j`
and `;`+`k`/`i` become F13, F15 and the wheel.

## Conventions worth copying

**`+never` deletes an inherited combo.** `never` is declared in `virtual-keys` and pressed by no macro,
so a combo requiring it can never complete. Used wherever a mode inherits nearly everything from another
but must not inherit one line. A never-pressed virtual key is the honest form of this; `+f24` used to
serve the same purpose, but F24 is a real key that merely happens to be absent from most keyboards.

**`#` where the key belongs to someone else.** `#rightctrl`, `#capslock`, `#rightshift`, `#leftalt`,
`#hintback`, `#typingkeys` — matched but not eaten, so applications keep receiving them.

**Mutations, not modes.** Twelve virtual keys and a set of `| _{key} -> value` branches replace what
would be a mode per click type per movement policy per level. The cost is having to remember that the
rightmost matching branch wins; the benefit is that arming a click recolors the mesh without rebuilding
it.

**Targeted resets, never bulk.** Every exit path unsets exactly the virtual keys it owns — up to seven
near-identical lines per exit point — because a bulk reset would also clear `showrecursivehintkeys`, and
the choice between the two looks is meant to outlive the hint session.

**Preconditions carry the yielding.** `^{oneshotkeys}` on every macro using a bare letter, `^{rightalt}`
on `start-move`, `^{recursivehintwheel}` on everything `space` and `rightalt` mean in the hint grid,
`^{leftalt}` on the fast-track digits, `_{none | modifierkey}` almost everywhere. The question a
precondition answers is not "is this combo valid" but "should this combo step aside".

**No implicit combo timeout.** `default-combo-move-duration-millis` is left commented out, so moves have
no maximum gap unless one is written down — every 150, 250, 500, 1000 and 2000 in the file is deliberate
and local.

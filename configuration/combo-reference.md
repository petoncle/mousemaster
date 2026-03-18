# mousemaster combo reference

## Contents
- [Overview](#overview)
- [Key moves](#key-moves)
- [Move sets and ordering](#move-sets-and-ordering)
- [Duration constraints](#duration-constraints)
- [Wait moves](#wait-moves)
- [Aliases](#aliases)
- [Negated moves](#negated-moves)
- [Optionality](#optionality)
- [Preconditions](#preconditions)
- [Combo preparation](#combo-preparation)
- [Multi-combo](#multi-combo)
- [Macro output syntax](#macro-output-syntax)
- [Examples](#examples)
- [Quick reference](#quick-reference)

## Overview

A **combo** is a pattern of keyboard events (presses and releases) that triggers an action. Combos are defined as property values in mode configuration:

```properties
idle-mode.to.normal-mode=combo              mode switch
normal-mode.press.left=combo                mouse press
normal-mode.release.left=combo              mouse release
normal-mode.macro.copy=combo -> output      macro
```

A combo expression has two parts: optional **preconditions** followed by a **move sequence**:

```
[preconditions...] move-sequence
```

## Key moves

A **move** is a single expected keyboard event. Moves are the building blocks of combo sequences.

### Press: `+key`

Matches a key press. The event is **eaten** (consumed, not passed to the OS).

```
+leftctrl       press leftctrl, eat the event
+a              press a, eat the event
```

### Non-eating press: `#key`

Matches a key press. The event is **not eaten** (still passed to the OS).

```
#rightctrl      press rightctrl, let it through to the OS
#a              press a, let it through
```

### Release: `-key`

Matches a key release. The release event is **eaten** if the corresponding press was eaten.

```
-leftctrl       release leftctrl
-a              release a
```

### Tap: `key`

Shorthand for press then release of the same key. Requires two events: a press followed by its matching release. The press and release events are **eaten**.

```
a               press a, then release a
leftctrl        press leftctrl, then release leftctrl
```

---

## Move sets and ordering

### Sequential moves (space-separated)

Moves separated by spaces form sequential **move sets**, each containing a single move. They must match in order.

```
+a +b           press a, then press b (strict order)
+a -a +b -b     press a, release a, press b, release b (strict order)
```

### Any-order moves (braces)

Moves inside `{}` form a single move set. All moves in the set must match, but in **any order**.

```
{+a +b}         press a and press b, in any order
{+a +b +c}      press a, press b, and press c, in any order
{-a -b -c}      release a, release b, and release c, in any order
```

### Combining sequential and any-order

```
+a {+b +c} -a     press a, then press b and c in any order, then release a
{+a +b} {-a -b}  press a+b in any order, then release a+b in any order
```

---

## Duration constraints

Duration constraints limit the time between consecutive moves in a combo. They are specified as suffixes on moves, in milliseconds. A duration suffix is shorthand for a plain `wait` between the move and the next:

```
+a-100-500 +b       shorthand for: +a wait-100-500 +b
+a-100 +b           shorthand for: +a wait-100 +b
```

### Minimum duration: `-min`

The next event must arrive **at least** `min` ms after this one.

```
+a-100 +b       after pressing and holding a, at least 100ms must pass before pressing b
```

### Min-max duration: `-min-max`

The next event must arrive between `min` and `max` ms after this one.

```
+a-0-250 -a     press a, then release within 250ms (a quick tap)
+a-100-500 +b   press a, then press b between 100ms and 500ms later
```

### Any-order move set duration: `{...}-min-max`

A duration suffix on an any-order move set applies to all moves inside. Each move in the set must arrive within the specified time window of the previous event.

```
{+a +b}-0-200       press a and b in any order, all within 200ms
{+a +b}-0-500 +c    press a and b within 500ms, then press c
```

This is equivalent to giving each move inside the set the same duration suffix. `wait` or ignored-key moves with a duration inside the set also set the move set duration:

```
{+a +b wait-0-200}      equivalent to: {+a +b}-0-200
{+a +b #{*}-0-200}      same duration as {+a +b}-0-200, and also ignores interleaved keys
```

### Default duration

Moves without an explicit duration suffix use the default: `0` (minimum 0ms, no maximum). This means the next event can arrive at any time.

A global default can be set to override this:

```properties
default-combo-move-duration-millis=0-250
```

This applies to all moves that don't have an explicit duration suffix.

---

## Wait moves

Wait moves insert a time window between other moves in a combo. The `#{...}` and `+{...}` wait moves can **ignore** interleaved events during that window, while plain `wait` enforces a time gap without ignoring any events.

A standalone wait (outside braces) is its own move set, placed between the preceding and following move sets.

### Ignore specific keys: `#{keys}`

Ignores events for the listed keys only. Events for unlisted keys are not ignored.

```
+a #{x} +b           press a, press b, ignore x events in between
+a #{x y}-0-500 +b   press a, press b within 500ms, ignore x and y events in between
```

### Ignore all keys: `#{*}`

Ignores events for any key.

```
+a #{*}-0-500 +b     press a, press b within 500ms, ignore all key events in between
```

### Ignore all presses: `#{+}`

Ignores press events only. Release events are not ignored.

```
+a #{+}-0-500 +b     press a, press b within 500ms, ignore press events in between
```

### Ignore all releases: `#{-}`

Ignores release events only. Press events are not ignored.

```
+a #{-}-0-500 +b     press a, press b within 500ms, ignore release events in between
```

### Ignore all except: `#!{keys}`

Ignores events for all keys **except** the listed ones. Events for the listed keys are not ignored (they pass through to subsequent moves or stop the wait).

```
+a #!{leftctrl}-0-200 +b      press a, press b within 200ms, ignore non-leftctrl events in between
+a #!{myalias}-0 {*myalias+}  press a, tap from myalias, ignore non-myalias events in between
```

### Eating ignored events: `+{...}`

With `#`, ignored events are **not eaten** (they still pass to the OS). Use `+` instead of `#` to eat them.

```
+a +{*}-0-500 +b     same as #{*}, but ignored events are eaten
+a +{+}-0-500 +b     same as #{+}, but ignored events are eaten
+a +{-}-0-500 +b     same as #{-}, but ignored events are eaten
+a +{x y} +b         same as #{x y}, but ignored events are eaten
+a +!{leftctrl} +b   same as #!{leftctrl}, but ignored events are eaten
```

### Plain wait: `wait`

A wait with no ignored keys. Just enforces a time gap.

```
+a wait-100 +b       press a, wait at least 100ms, press b
+a wait-100-500 +b   press a, wait 100-500ms, press b
```

### Duration on waits

```
#{*}                 ignore all, no time constraint (wait indefinitely)
#{*}-0               ignore all, minimum 0ms (trivially satisfied)
#{*}-100             ignore all, at least 100ms must pass
#{*}-100-500         ignore all, between 100-500ms
#!{myalias}-0-2000  ignore non-myalias keys for up to 2000ms
```

### Wait inside any-order move sets

Inside braces, the wait applies to events interleaved between the other moves in the same set.

```
{-a -b #{*}}         -a and -b in any order, ignoring interleaved events
{+a +b #{x}-0-500}   +a and +b in any order, ignoring x events for up to 500ms
```

### Leading waits

When a combo starts with a wait move set, it is a **leading wait**. Leading waits enforce a time gap between the last non-ignored event and the first key move of the combo.

The minimum duration of a leading wait enforces a **cooldown**: the combo cannot match unless at least that much time has passed since the last non-ignored event. This is useful for preventing double/triple-taps from triggering the combo.

```
#!{leftshift}-200 +leftshift
```

Here `#!{leftshift}` ignores all keys except leftshift. Since leftshift events are **not** ignored, they reset the wait timer. The `-200` requires 200ms to pass since the last leftshift event. So if the user double-taps leftshift quickly (under 200ms between taps), the wait's minimum is not satisfied and the combo does not match. Only a press after a 200ms gap proceeds to `+leftshift`.

---

## Aliases

Key aliases are named groups of keys defined in the configuration:

```properties
key-alias.modifierkey.us-qwerty=leftshift leftctrl leftalt
key-alias.directionkey.us-qwerty=i k j l
key-alias.oneshotkey=leftshift leftctrl leftalt
```

### Single-key alias reference: `+alias`

An alias reference matches **any one key** from the alias.

```
+modifierkey         press leftshift, leftctrl, or leftalt
-directionkey        release i, k, j, or l
```

### Expanded alias: `+*alias` or `*alias`

With `*`, the alias expands into individual moves for **each key** in the alias.

**Outside braces** (sequential): expands into separate sequential move sets.

```
+*directionkey       equivalent to: +i +k +j +l (strict order, 4 move sets)
*directionkey        equivalent to: i k j l (4 sequential taps)
```

**Inside braces** (any-order): expands into moves within a single move set.

```
{+*modifierkey}      equivalent to: {+leftshift +leftctrl +leftalt} (any order)
{*directionkey}      equivalent to: {i k j l} (taps in any order)
```

### Aliases in wait moves

Aliases can be used in wait moves:

```
#{modifierkey}             ignore events for leftshift, leftctrl, leftalt
#!{oneshotkey}            ignore all events except oneshotkey
#!{letterkey oneshotkey}  ignore all except letterkey and oneshotkey
```

---

## Negated moves

The `!` modifier matches any key **except** the specified one from an alias.

```
+!modifierkey        press any key that is NOT in the modifierkey alias
-!directionkey       release any key that is NOT in the directionkey alias
```

---

## Optionality

### Optional: `?`

An optional move may or may not match. The combo succeeds either way. The `?` suffix works with any move type: press (`+key?`, `#key?`), release (`-key?`), and tap (`key?`).

```
{+a #b?}            +a is required, #b is optional
{+a +b? -c?}        +a required, +b and -c optional
{#a?}               all optional (0 or 1 events)
```

With alias expansion:

```
{+*myalias?}         all expanded press moves are optional (0 or more)
{*myalias?}          all expanded taps are optional
```

### At-least-one: `+` (suffix)

Requires **at least one** of the optional moves to match. Used with expanded aliases where individual moves are optional but the group as a whole must match at least once.

```
{*myalias+}          at least one key from the alias must complete a tap
{+*myalias+}         at least one key from the alias must be pressed
{*myalias+ +c}       at least one tap from myalias, plus required +c
```

### Summary

| Suffix | Meaning | Matches |
|--------|---------|---------|
| (none) | Required | All |
| `?` | Optional | 0 or more |
| `+` | At-least-one | 1 or more |

---

## Preconditions

Preconditions filter when a combo can match based on the current state of held keys or the active application. They appear before the move sequence.

### Unpressed precondition: `^{keys}`

All listed keys must **not** be pressed.

```
^{rightalt} +up              rightalt must be unpressed
^{rightalt leftalt} +up      both rightalt and leftalt must be unpressed
^{directionkey} +left        no key from directionkey alias may be pressed
```

### Pressed precondition: `_{keys}`

Specifies keys that must be pressed. Supports groups separated by `|` (OR logic).

**Single group**: each space-separated token is a key set, and at least one key from **each** set must be pressed (AND logic).

```
_{leftctrl} +a               leftctrl must be pressed
_{leftctrl leftshift} +a     both leftctrl and leftshift must be pressed
_{modifierkey} +a            at least one key from the modifierkey alias must be pressed
```

**Multiple groups** (OR): at least one group must be satisfied.

```
_{none | modifierkey} +a     either no keys pressed, or a modifier key pressed
_{leftctrl | leftshift} +a   either leftctrl or leftshift pressed
```

**The `none` keyword**: matches when no keys are pressed.

```
_{none} +a                   no keys may be pressed
_{none | leftctrl} +a        either no keys, or leftctrl pressed
```

**The `*` separator**: combines keys/aliases into a single key set (at least one must be pressed).

```
_{leftshift*leftctrl} +a     leftshift or leftctrl must be pressed
```

### Combining preconditions

Multiple preconditions can appear together. All must be satisfied.

```
^{rightalt} _{none | modifierkey} +up
  rightalt must not be pressed, AND (no keys or a modifier must be pressed)
```

### App preconditions

Match based on the active application's process name.

```
^{rustdesk.exe} +space       combo only works when rustdesk is NOT active
_{firefox.exe | chrome.exe}  combo only works when Firefox or Chrome is active
```

---

## Combo preparation

As key events arrive, mousemaster builds up a **combo preparation**, the sequence of recent events that might match a combo. This preparation carries across mode switches, allowing a combo to be initiated in one mode and completed in another.

```properties
normal-mode.press.left=+a
normal-mode.to.click-mode=+a
click-mode.release.left=+a -a
```

Here, pressing `a` in normal-mode triggers a left mouse press and switches to click-mode. In click-mode, the preparation already contains `+a`, so releasing `a` completes the `+a -a` combo and triggers a left mouse release.

### Keys from previous combos

Keys that are part of a previous combo don't need to be specified again in subsequent combos.

```properties
# This combo eats leftctrl, leftalt, and space when pressed
normal-mode.to.normal-mode=#leftshift | +leftctrl | +leftalt | +space

# This combo requires space to be held, then hint key to be pressed
# It works even if leftctrl, leftalt, or leftshift are also being held
normal-mode.to.hint-mode=_{space} +hint
```

In this example:
- The first combo doesn't change the mode but "eats" leftctrl, leftalt and space.
- Any key press that is part of a previous combo doesn't need to be specified again in subsequent combos.
- As long as those keys remain pressed, they don't need to be specified in combos that don't care whether they are pressed.

### Breaking combo preparation

The `break-combo-preparation` property resets the combo preparation when a specified combo is matched. This forces subsequent combos to start fresh rather than continuing from the previous preparation.

```properties
normal-mode.press.left=+a
normal-mode.to.click-mode=+a
normal-mode.break-combo-preparation=+a
click-mode.release.left=+a -a
```

With `break-combo-preparation=+a`, the preparation is reset after `+a` matches in normal-mode. In click-mode, the `+a -a` combo now requires the user to press `a` again then release it, because the previous `+a` was cleared from the preparation.

---

## Multi-combo

Multiple alternative combos can be defined in a single property, separated by `|` at the top level (outside braces).

```properties
normal-mode.to.idle-mode=+exit | -clickthendisable | +leftalt +enablebasekey
```

This defines three separate combos. The first one that matches triggers the action.

---

## Macro output syntax

For `macro` properties, the output after `->` defines what to send.

### Output moves

```
+key              press key (send to OS)
-key              release key (send to OS)
#key              press key (send to ComboWatcher, triggers other combos)
~key              release key (send to ComboWatcher)
key               press key, then release key (send to ComboWatcher)
```

### Typed text

Single-quoted strings send literal text:

```
'Hello'           types "Hello"
'% '              types "% "
```

### Wait in output

```
wait-50           pause 50ms between output events
wait-100          pause 100ms
```

### Alias references in output

Aliases used in the combo can be referenced in the output. The alias resolves to whichever key was actually matched.

```properties
key-alias.letters=a b c d e f

normal-mode.macro.ctrlkey=+letters -> +leftctrl +letters -letters -leftctrl
```

Here `letters` in the output resolves to the specific key that was matched in the combo. If the user presses `c`, the output sends Ctrl+C.

### Alias remap

Macros support an alias remap between combo and output: `combo -> remap -> output`.

**Broadcast** (one target for all alias keys):

```properties
key-alias.myalias=a b c
mode.macro.x=+myalias -> myalias=z -> +myalias
```

Any key from myalias is remapped to `z` in the output.

**Positional** (zip alias keys with target keys):

```properties
key-alias.myalias=i j k l
mode.macro.x=+myalias -> myalias=uparrow leftarrow downarrow rightarrow -> +myalias
```

`i` maps to `uparrow`, `j` to `leftarrow`, `k` to `downarrow`, `l` to `rightarrow`.

**Per-key**:

```properties
key-alias.myalias=a b c
mode.macro.x=+myalias -> myalias.a=x myalias.b=y -> +myalias
```

`a` is remapped to `x`, `b` is remapped to `y`, `c` is not remapped.

---

## Examples

### Simple mode toggle

```properties
idle-mode.to.normal-mode=_{none | modifierkey} +rightalt -rightalt
```

When no keys are pressed (or a modifier is held), tap right-alt to enter normal mode.

### Quick-tap detection with duration

```properties
normal-mode.press.left=+leftbutton
normal-mode.release.left=+leftbutton-0-250 -leftbutton
```

Press left mouse button to trigger press-left. Release within 250ms to trigger release-left.

### Ctrl+C macro

```properties
key-alias.copy=c
normal-mode.macro.copy=_{none} +copy -> +leftctrl +copy -copy -leftctrl
```

When no other keys are pressed, pressing the `copy` alias key sends Ctrl+C.

### Oneshot modifier

```properties
key-alias.oneshotkey=leftshift leftctrl leftalt
key-alias.letterkey=a b c d e f g h i j k l m n o p q r s t u v w x y z

idle-mode.macro.oneshot=#!{oneshotkey}-200 {*oneshotkey+} \
  #!{letterkey oneshotkey}-0-2000 +letterkey \
  -> +oneshotkey +letterkey -oneshotkey
```

Tap a modifier (e.g., leftshift), then press a letter within 2 seconds. The modifier is applied to the letter as if held.

Breakdown:
- `#!{oneshotkey}-200`: leading wait, requires 200ms since the last oneshotkey event. This prevents double/triple-tapping a modifier from triggering the oneshot. If the user taps leftshift twice quickly, the second tap resets the 200ms timer and the combo does not match.
- `{*oneshotkey+}`: tap at least one oneshotkey (e.g., leftshift press + release).
- `#!{letterkey oneshotkey}-0-2000`: wait up to 2 seconds, ignoring any non-letterkey/non-oneshotkey events.
- `+letterkey`: press a letter.
- `-> +oneshotkey +letterkey -oneshotkey`: output: press the matched modifier, press the matched letter, release the modifier.

### Tap-dance

Single tap F1 → b, double tap → c, triple tap → d. Each tap must happen within 200ms of the previous one.

```properties
idle-mode.macro.singletapf1=#!{f1}-200 +f1 -f1-200 -> +b -b
idle-mode.macro.doubletapf1=#!{f1}-200 +f1 -f1-0-200 +f1 -f1-200 -> +c -c
idle-mode.macro.tripletapf1=+f1 -f1-0-200 +f1 -f1-0-200 +f1 -f1 -> +d -d
idle-mode.break-combo-preparation=+f1 -f1-0-200 +f1 -f1-0-200 +f1 -f1
```

Breakdown:
- `#!{f1}-200`: leading wait, requires 200ms since the last F1 event. Prevents rapid re-triggering.
- `-f1-0-200`: release F1 within 200ms (quick tap).
- `-f1-200`: release F1, then wait at least 200ms. This is what makes the single and double tap combos wait before firing, giving the longer combos a chance to match.
- `break-combo-preparation` on the triple tap resets the preparation so subsequent taps start fresh.

### Tap-dance (eager)

Fires on each tap immediately without waiting. Double tap deletes the output of the single tap first.

```properties
idle-mode.macro.eagersingletapf1=#!{f1}-200 +f1 -f1 -> +b -b
idle-mode.macro.eagerdoubletapf1=#!{f1}-200 +f1 -f1-0-200 +f1 -f1 -> +backspace -backspace +c -c
idle-mode.break-combo-preparation=+f1 -f1-0-200 +f1 -f1
```

The single tap fires immediately on release. If a second tap follows within 200ms, the double tap fires and sends backspace to delete the single tap's output before sending `c`.

### Tap-hold (press variant)

Capslock: tap → Escape, hold → Ctrl. The hold threshold is 200ms.

```properties
idle-mode.macro.caps-hold=+capslock #!{capslock}-200 -> +leftctrl
idle-mode.macro.caps-tap=+capslock #!{capslock}-0-200 -capslock -> +esc -esc
idle-mode.macro.caps-release=-capslock -> -leftctrl
```

Breakdown:
- `caps-hold`: press capslock, wait 200ms (ignoring non-capslock events) → send Ctrl press.
- `caps-tap`: press capslock, release within 200ms → send Escape tap.
- `caps-release`: release capslock → release Ctrl (cleans up after hold).

### Tap-hold (release variant)

Same as above, but `caps-hold` fires on the next key press/release while capslock is held, rather than after a fixed timeout.

```properties
idle-mode.macro.caps-hold=+capslock +!capslock -!capslock -> +leftctrl +!capslock -!capslock
idle-mode.macro.caps-tap=+capslock #!capslock? -capslock -> +esc -esc
idle-mode.macro.caps-release=-capslock -> -leftctrl
```

Breakdown:
- `caps-hold`: press capslock, then tap any other key → send Ctrl press and forward the tapped key.
- `caps-tap`: press capslock, optionally ignore non-capslock events, release capslock → send Escape.
- `caps-release`: same as press variant.

### Repeatable sequence

Hold `a` to type 'abc', then repeat every 1 second while still held.

```properties
normal-mode.to.abc-mode=+a
abc-mode.to.normal-mode=-a
abc-mode.macro.abc=_{a} -> 'abc'
abc-mode.to.abc-mode=wait-1000
```

Pressing `a` switches to abc-mode, which immediately runs the macro (since `a` is held, the `_{a}` precondition is satisfied). The self-switch `abc-mode.to.abc-mode=wait-1000` waits 1 second then re-enters the mode, re-triggering the macro.

### Caps-word

Typing letters while caps-word is active sends them shifted. Exits on space, punctuation, or 2 seconds of inactivity.

```properties
key-alias.letter=a b c d e f g h i j k l m n o p q r s t u v w x y z
idle-mode.to.capsword-mode=+capslock -capslock
capsword-mode.macro.capsword=+letter -> +leftshift +letter -letter -leftshift
capsword-mode.to.capsword-mode=+letter | +capslock
capsword-mode.to.idle-mode=#space | #. | #, | #; | wait-2000
```

Breakdown:
- Tap capslock to enter capsword-mode.
- Each letter press sends Shift+letter.
- Pressing a letter or capslock re-enters the mode, keeping it active.
- Space, period, comma, semicolon, or 2 seconds of inactivity exits back to idle.

### Chord

Press `a` and `b` together (within 200ms) to hold Tab. Release both to release Tab.

```properties
idle-mode.macro.presstab={+a +b wait-0-200} -> +tab
idle-mode.macro.releasetab={+a +b wait-0-200} wait-0 {-a -b} -> -tab
```

Breakdown:
- `{+a +b wait-0-200}`: press `a` and `b` in any order within 200ms.
- `wait-0` between the press and release sets decouples the duration, allowing the keys to be held for any length of time before releasing.
- `{-a -b}`: release both keys in any order.

### Sequence

Press capslock then type `g`, `s`, `t` (each within 1 second of the previous) to output 'git status'.

```properties
idle-mode.macro.gitstatus=+capslock #{-}-0-1000 +g #{-}-0-1000 +s #{-}-0-1000 +t -> 'git status'
```

`#{-}-0-1000` between each key ignores release events (so releasing capslock or previous keys doesn't break the sequence) and allows up to 1 second between presses.

### Zippy chord

Press `g` and `i` together to output 'git '. Follow with `c` for 'git checkout ', or punctuation for 'git.'.

```properties
key-alias.punctuation=. , ;
idle-mode.macro.git={+g +i}-0-500 -> 'git '
idle-mode.macro.git-punctuation={+g +i}-0-500 #{-} +punctuation -> +backspace -backspace +punctuation
idle-mode.macro.gitcheckout={+g +i}-0-500 #{-} +c -> 'checkout '
idle-mode.macro.gitcheckout-punctuation={+g +i}-0-500 #{-} +c -c +punctuation -> +backspace -backspace +punctuation
idle-mode.macro.gitcheckoutb={+g +i}-0-500 #{-} +c -c +b -> '-b'
```

Breakdown:
- `{+g +i}-0-500`: chord g+i pressed within 500ms.
- `git-punctuation`: if punctuation follows the chord, delete the trailing space and output the punctuation directly (e.g., 'git.').
- `gitcheckout`: if `c` follows, append 'checkout '.
- `gitcheckoutb`: if `b` follows `c`, append '-b'.
- `#{-}` between moves ignores release events so releasing `g`/`i` doesn't break the sequence.

---

## Quick reference

| Syntax | Meaning |
|--------|---------|
| `+key` | Press key, eat event |
| `#key` | Press key, don't eat event |
| `-key` | Release key |
| `key` | Tap (press + release) |
| `{a b c}` | Any-order move set |
| `+key-100` | Move with min 100ms duration |
| `+key-0-250` | Move with 0-250ms duration |
| `#{keys}` | Ignore listed keys (don't eat) |
| `+{keys}` | Ignore listed keys (eat) |
| `#!{keys}` | Ignore all except listed (don't eat) |
| `+!{keys}` | Ignore all except listed (eat) |
| `#{*}` | Ignore all keys (don't eat) |
| `+{*}` | Ignore all keys (eat) |
| `#{+}` | Ignore all presses (don't eat) |
| `+{+}` | Ignore all presses (eat) |
| `#{-}` | Ignore all releases (don't eat) |
| `+{-}` | Ignore all releases (eat) |
| `wait-N` | Plain wait, min N ms |
| `wait-N-M` | Plain wait, N-M ms |
| `+alias` | Press any key in alias |
| `+*alias` | Expand alias into sequential press moves |
| `*alias` | Expand alias into sequential taps |
| `{+*alias}` | Expand alias into any-order press moves |
| `{*alias}` | Expand alias into any-order taps |
| `key?` | Optional tap |
| `{*myalias?}` | Tap any keys from alias (zero or more) |
| `{*myalias+}` | Tap any keys from alias (one or more) |
| `+!key` | Negated press (any key except this) |
| `^{keys}` | Precondition: keys must be unpressed |
| `_{keys}` | Precondition: keys must be pressed |
| `_{none}` | Precondition: no keys pressed |
| `_{a \| b}` | Precondition: a or b pressed |
| `combo1 \| combo2` | Alternative combos |
| `combo -> output` | Macro output |
| `-> +key` | Output: press key to OS |
| `-> -key` | Output: release key to OS |
| `-> #key` | Output: press key to ComboWatcher |
| `-> ~key` | Output: release key to ComboWatcher |
| `-> key` | Output: tap to ComboWatcher (`#key ~key`) |
| `-> 'text'` | Output: typed text |
| `-> wait-N` | Output: pause N ms |
| `combo -> remap -> output` | Macro with alias remap |

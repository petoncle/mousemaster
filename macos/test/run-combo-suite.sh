#!/bin/bash
# Integration suite for input handling, combos and macros on macos. Drives real key events
# through the same queue the hid thread feeds (--simulate) and asserts on the log.
#
# Phase 1 asserts each feature drives the mode switch it should. Phase 2 asserts the
# near-miss of each one drives nothing: without it a suite cannot tell correct matching
# apart from matching everything. Phase 3 covers the cases a single key at a time misses:
# overlapping presses, combos sharing a prefix, and a key held across a mode switch.
#
#   QT_LIB=~/Qt/6.8.2/macos/lib ./macos/test/run-combo-suite.sh
#
# Needs the karabiner driver activated, root, and Input Monitoring. A locked screen makes
# every case vacuous (keys are passed through), which the sanity check below catches.
set -u
here=$(cd "$(dirname "$0")" && pwd)
CONFIG=$here/combo-suite.properties
cd "$here/../.." || exit 1
. "$here/mousemaster.sh"
FAILED=0

run() { # $1=output file, $2=simulation key events
    run_mousemaster "$1" "$2"
    assert_ran "$1" || FAILED=1
    assert_no_exception "$1" || FAILED=1
}

modes() { grep -oE 'Switching to [a-z0-9-]+' "$1" | sed 's/Switching to //' | tr '\n' ' '; }

echo "== phase 1: each feature drives its mode switch =="
P1="3000 t 300"
P1="$P1 +a +b 150 -a -b 200 z 200"                    # any-order move set
P1="$P1 +c 250 -c 200 z 200"                          # duration >= 150ms
P1="$P1 +leftshift 100 d 100 -leftshift 200 z 200"    # precondition pressed
P1="$P1 e 200 z 200"                                  # precondition unpressed
P1="$P1 f 200 z 200"                                  # alternative combos
P1="$P1 n 200 z 200"                                  # alias
P1="$P1 k 200 z 200"                                  # non-eating press
P1="$P1 j 400"                                        # macro: wait then typed text
P1="$P1 h 200 i 200 z 200"                            # macro sets a virtual key, gated combo
run /tmp/suite-p1.out "$P1"
EXPECTED="idle-mode hub-mode s1-mode hub-mode s2-mode hub-mode s3-mode hub-mode s4-mode hub-mode s5-mode hub-mode s9-mode hub-mode s8-mode hub-mode s6-mode hub-mode "
ACTUAL=$(modes /tmp/suite-p1.out)
for s in s1 s2 s3 s4 s5 s6 s8 s9; do
    grep -q "Switching to $s-mode" /tmp/suite-p1.out && echo "  ok   $s" \
        || { echo "  FAIL $s never matched"; FAILED=1; }
done
grep -q 'matching \[macro.waited\]' /tmp/suite-p1.out && echo "  ok   macro wait+text" \
    || { echo "  FAIL macro wait+text"; FAILED=1; }
grep -q 'pressed \[vk' /tmp/suite-p1.out && echo "  ok   virtual key set by macro" \
    || { echo "  FAIL virtual key never set"; FAILED=1; }
if [ "$EXPECTED" = "$ACTUAL" ]; then echo "  ok   mode sequence exact"
else echo "  FAIL mode sequence"; echo "    expected: $EXPECTED"; echo "    actual:   $ACTUAL"; FAILED=1; fi

echo "== phase 2: the near-miss of each drives nothing =="
P2="3000 t 300"
P2="$P2 i 300"                                  # virtual key not set yet
P2="$P2 d 300"                                  # precondition key not held
P2="$P2 +leftctrl 100 e 100 -leftctrl 300"      # forbidden key held
P2="$P2 +a 200 -a 300"                          # move set left incomplete
P2="$P2 +c 40 -c 300"                           # duration not reached
run /tmp/suite-p2.out "$P2"
for s in s1 s2 s3 s4 s6; do
    grep -q "Switching to $s-mode" /tmp/suite-p2.out \
        && { echo "  FAIL $s matched a near-miss"; FAILED=1; } || echo "  ok   $s stayed put"
done
[ "$(modes /tmp/suite-p2.out)" = "idle-mode hub-mode " ] && echo "  ok   never left hub-mode" \
    || { echo "  FAIL left hub-mode: $(modes /tmp/suite-p2.out)"; FAILED=1; }
# Only a genuine partial match is eaten, so only those two come back. A precondition that
# fails means the key was never eaten and must not be regurgitated.
REG=$(grep -oE 'Regurgitating \+[a-z]+' /tmp/suite-p2.out | sed 's/Regurgitating //' | sort | tr '\n' ' ')
[ "$REG" = "+a +c " ] && echo "  ok   regurgitated exactly the eaten partial matches (+a +c)" \
    || { echo "  FAIL regurgitated [$REG], expected [+a +c ]"; FAILED=1; }

echo "== phase 3: interleaved keys, shared prefixes, and state across a mode switch =="
P3="3000 t 300"
P3="$P3 +b +a 150 -b -a 250 z 250"          # the any-order set, pressed the other way round
P3="$P3 +o 150 +p 150 -p 120 -o 300 z 250"  # sequential, in order and with no release between
P3="$P3 +q 150 +r 150 -r 120 -q 300 z 250"  # shared prefix, first branch
P3="$P3 +q 150 +s 150 -s 120 -q 300 z 250"  # shared prefix, second branch
P3="$P3 +leftalt 200 +v 150 -v 300"         # leftalt is still held across the switch
P3="$P3 +w 150 -w 300 -leftalt 250 z 250"
P3="$P3 +p 150 +o 150 -o 120 -p 300"        # sequential reversed: must match nothing
run /tmp/suite-p3.out "$P3"
for s in s1 s10 s11 s12 s13 s14; do
    grep -q "Switching to $s-mode" /tmp/suite-p3.out && echo "  ok   $s" \
        || { echo "  FAIL $s never matched"; FAILED=1; }
done
EXPECTED3="idle-mode hub-mode s1-mode hub-mode s10-mode hub-mode s11-mode hub-mode s12-mode hub-mode s13-mode s14-mode hub-mode "
ACTUAL3=$(modes /tmp/suite-p3.out)
if [ "$EXPECTED3" = "$ACTUAL3" ]; then echo "  ok   mode sequence exact (reversed sequential matched nothing)"
else echo "  FAIL mode sequence"; echo "    expected: $EXPECTED3"; echo "    actual:   $ACTUAL3"; FAILED=1; fi

echo "== phase 4: durations, waits and optionality =="
P4="3000 t 300"
P4="$P4 +l 120 -l 350 z 300"                        # released inside the 250ms window
P4="$P4 +x 80 +y 80 -x 60 -y 350 z 300"             # both presses inside the set's 200ms
P4="$P4 +1 300 +2 150 -1 60 -2 350 z 300"           # the wait's 200ms elapsed before the second press
P4="$P4 +3 100 +v 100 +4 150 -3 60 -4 60 -v 350 z 300"  # v pressed in between is ignored
P4="$P4 +5 100 +w 100 +6 150 -5 60 -6 60 -w 350 z 300"  # w in between is ignored and eaten
P4="$P4 +7 200 -7 350 z 300"                        # the optional 8 is never pressed
P4="$P4 +l 400 -l 300"                              # too slow for the 250ms window: no match
P4="$P4 +x 400 +y 150 -x 60 -y 300"                 # too far apart for the set: no match
P4="$P4 +1 60 +2 150 -1 60 -2 300"                  # too soon after the wait: no match
run /tmp/suite-p4.out "$P4"
for s in s15 s16 s17 s18 s19 s20; do
    grep -q "Switching to $s-mode" /tmp/suite-p4.out && echo "  ok   $s" \
        || { echo "  FAIL $s never matched"; FAILED=1; }
done
EXPECTED4="idle-mode hub-mode s15-mode hub-mode s16-mode hub-mode s17-mode hub-mode s18-mode hub-mode s19-mode hub-mode s20-mode hub-mode "
ACTUAL4=$(modes /tmp/suite-p4.out)
if [ "$EXPECTED4" = "$ACTUAL4" ]; then echo "  ok   mode sequence exact (the three near misses matched nothing)"
else echo "  FAIL mode sequence"; echo "    expected: $EXPECTED4"; echo "    actual:   $ACTUAL4"; FAILED=1; fi
# Whether the ignored key is eaten is the only difference between s18 and s19. A key that is not
# eaten reaches the OS straight away, so it is never regurgitated either.
grep -q 'Key +v, pressed' /tmp/suite-p4.out && echo "  ok   the ignored key of #{*} is not eaten" \
    || { echo "  FAIL #{*} ate the ignored key"; FAILED=1; }
grep -q 'Key +w, eaten' /tmp/suite-p4.out && echo "  ok   the ignored key of +{*} is eaten" \
    || { echo "  FAIL +{*} did not eat the ignored key"; FAILED=1; }

echo "== phase 5: which events an ignore list actually ignores =="
P5="3000 t 300"
P5="$P5 +9 100 +u 100 +0 150 -9 60 -u 60 -0 350 z 300"     # u is the listed key, so it is ignored
P5="$P5 +; 100 +u 100 +' 150 -; 60 -u 60 -' 350 z 300"     # a press in between, which #{+} ignores
P5="$P5 +[ 100 +, 100 +] 150 -[ 60 -, 60 -] 350 z 300"     # , is not the excepted key, so it is ignored
P5="$P5 +9 100 +, 100 +0 150 -9 60 -, 60 -0 300"           # , is not listed: stops the wait
P5="$P5 +; 100 u 100 +' 150 -; 60 -' 300"                  # a release in between: #{+} does not ignore it
P5="$P5 +[ 100 +u 100 +] 150 -[ 60 -u 60 -] 300"           # u is excepted: stops the wait
run /tmp/suite-p5.out "$P5"
for s in s21 s22 s23; do
    grep -q "Switching to $s-mode" /tmp/suite-p5.out && echo "  ok   $s" \
        || { echo "  FAIL $s never matched"; FAILED=1; }
done
EXPECTED5="idle-mode hub-mode s21-mode hub-mode s22-mode hub-mode s23-mode hub-mode "
ACTUAL5=$(modes /tmp/suite-p5.out)
if [ "$EXPECTED5" = "$ACTUAL5" ]; then echo "  ok   mode sequence exact (each near miss matched nothing)"
else echo "  FAIL mode sequence"; echo "    expected: $EXPECTED5"; echo "    actual:   $ACTUAL5"; FAILED=1; fi

echo "== phase 6: a set nested in a sequence, and an optional release =="
P6="3000 t 300"
P6="$P6 +/ 100 += 80 +. 80 -/ 350 z 300"   # the set is pressed in the other order, inside the sequence
P6="$P6 +tab 150 -tab 350 z 300"           # the optional release does not block the match
P6="$P6 +/ 100 +. 80 -/ 300"               # only half the set: no match
run /tmp/suite-p6.out "$P6"
for s in s24 s25; do
    grep -q "Switching to $s-mode" /tmp/suite-p6.out && echo "  ok   $s" \
        || { echo "  FAIL $s never matched"; FAILED=1; }
done
EXPECTED6="idle-mode hub-mode s24-mode hub-mode s25-mode hub-mode "
ACTUAL6=$(modes /tmp/suite-p6.out)
if [ "$EXPECTED6" = "$ACTUAL6" ]; then echo "  ok   mode sequence exact (half a set matched nothing)"
else echo "  FAIL mode sequence"; echo "    expected: $EXPECTED6"; echo "    actual:   $ACTUAL6"; FAILED=1; fi

echo "== phase 7: eating ignore lists, and alias expansion under optionality =="
P7="3000 t 300"
P7="$P7 +f1 100 +u 100 +f2 150 -f1 60 -u 60 -f2 350 z 300"        # u is listed, so it is ignored and eaten
P7="$P7 +f3 100 +space 100 +f4 150 -f3 60 -space 60 -f4 350 z 300"  # a press in between, which +{+} eats
P7="$P7 +f7 100 +backspace 100 +f8 150 -f7 60 -backspace 60 -f8 350 z 300"  # not the excepted key: ignored and eaten
P7="$P7 +f5 100 -f5 100 +f6 150 -f6 350 z 300"                    # the release of f5 itself, which #{-} ignores
P7="$P7 +f9 100 -f9 100 +f10 150 -f10 350 z 300"                  # the same, and +{-} also eats the release
P7="$P7 +uparrow 150 -uparrow 350 z 300"                          # one of the two alias presses is enough
P7="$P7 +f12 150 -f12 350 z 300"                                  # neither optional alias press is made
P7="$P7 +f12 100 +leftarrow 100 -f12 60 -leftarrow 350 z 300"     # an optional alias press is accepted too
P7="$P7 +f1 100 +backspace 100 +f2 150 -f1 60 -backspace 60 -f2 300"  # backspace is not listed: no match
P7="$P7 +f3 100 -f3 100 +f4 150 -f4 300"                          # a release in between: +{+} does not ignore it
P7="$P7 +f7 100 +f11 100 +f8 150 -f7 60 -f11 60 -f8 300"          # f11 is excepted: no match
P7="$P7 +f5 700 -f5 100 +f6 150 -f6 300"                          # past the ignore window's 500ms: no match
run /tmp/suite-p7.out "$P7"
for s in s26 s27 s28 s29 s30 s31 s32; do
    grep -q "Switching to $s-mode" /tmp/suite-p7.out && echo "  ok   $s" \
        || { echo "  FAIL $s never matched"; FAILED=1; }
done
EXPECTED7="idle-mode hub-mode s26-mode hub-mode s27-mode hub-mode s29-mode hub-mode s28-mode hub-mode s32-mode hub-mode s30-mode hub-mode s31-mode hub-mode s31-mode hub-mode "
ACTUAL7=$(modes /tmp/suite-p7.out)
if [ "$EXPECTED7" = "$ACTUAL7" ]; then echo "  ok   mode sequence exact (each near miss matched nothing)"
else echo "  FAIL mode sequence"; echo "    expected: $EXPECTED7"; echo "    actual:   $ACTUAL7"; FAILED=1; fi
# Each form ignores a different key, so the eaten flag says which one absorbed the event.
for e in '+u' '+space' '+backspace' '-f9'; do
    grep -q "Key $e, eaten" /tmp/suite-p7.out && echo "  ok   the ignored $e is eaten" \
        || { echo "  FAIL the ignored $e was not eaten"; FAILED=1; }
done
grep -q 'Key -f5, pressed' /tmp/suite-p7.out && echo "  ok   the ignored -f5 of #{-} is not eaten" \
    || { echo "  FAIL #{-} ate the ignored release"; FAILED=1; }

echo "== phase 8: the alias expansion forms and a plain tap =="
P8="3000 t 300"
P8="$P8 +end 100 +pageup 150 -end 60 -pageup 350 z 300"   # expanded into a sequence, so in that order
P8="$P8 pagedown 150 insert 350 z 300"                    # expanded into taps, in that order
P8="$P8 enter 150 del 350 z 300"                          # expanded into an any-order set: the other order
P8="$P8 numpad1 350 z 300"                                # one tap is enough for the at-least-one form
P8="$P8 +numpad5 150 -numpad5 350 z 300"                  # neither optional tap is made
P8="$P8 home 350 z 300"
P8="$P8 +pageup 100 +end 150 -pageup 60 -end 300"         # the sequence reversed: no match
P8="$P8 insert 150 pagedown 300"                          # the taps reversed: no match
run /tmp/suite-p8.out "$P8"
for s in s33 s34 s35 s36 s37 s38; do
    grep -q "Switching to $s-mode" /tmp/suite-p8.out && echo "  ok   $s" \
        || { echo "  FAIL $s never matched"; FAILED=1; }
done
EXPECTED8="idle-mode hub-mode s33-mode hub-mode s34-mode hub-mode s35-mode hub-mode s36-mode hub-mode s37-mode hub-mode s38-mode hub-mode "
ACTUAL8=$(modes /tmp/suite-p8.out)
if [ "$EXPECTED8" = "$ACTUAL8" ]; then echo "  ok   mode sequence exact (both reversals matched nothing)"
else echo "  FAIL mode sequence"; echo "    expected: $EXPECTED8"; echo "    actual:   $ACTUAL8"; FAILED=1; fi

echo "== phase 9: negation, the remaining preconditions, and macro output =="
P9="3000 t 300"
P9="$P9 +numpad6 150 -numpad6 250"                        # into the mode where the negated press lives
P9="$P9 +numpad7 150 -numpad7 250"                        # the excluded key: no match
P9="$P9 +numpad9 150 -numpad9 300 z 300"                  # any other key matches the negated press
P9="$P9 +numpadadd 150 -numpadadd 350 z 300"              # nothing else is pressed
P9="$P9 +leftctrl 100 +numpadsubtract 150 -numpadsubtract 60 -leftctrl 350 z 300"  # the second alternative
P9="$P9 +numpaddivide 150 -numpaddivide 350 z 300"        # the virtual key declared as pressed from the start
P9="$P9 +h 150 -h 250 +i 150 -i 350 z 300"                # a macro presses the virtual key, so 6 matches
P9="$P9 +numpadmultiply 150 -numpadmultiply 250 +i 150 -i 300"  # released again: 6 no longer matches
P9="$P9 +numpaddecimal 150 -numpaddecimal 400 z 300"      # the macro taps home into the combo watcher
P9="$P9 +numpad8 150 -numpad8 400 z 300"                  # remapped to numpad0, which 45 taps
P9="$P9 +leftshift 100 +numpadadd 150 -numpadadd 60 -leftshift 300"  # a key is pressed: _{none} fails
P9="$P9 +leftalt 100 +numpadsubtract 150 -numpadsubtract 60 -leftalt 300"  # neither alternative is pressed
run /tmp/suite-p9.out "$P9"
for s in s39 s40 s41 s43 s45; do
    grep -q "Switching to $s-mode" /tmp/suite-p9.out && echo "  ok   $s" \
        || { echo "  FAIL $s never matched"; FAILED=1; }
done
EXPECTED9="idle-mode hub-mode neg-mode s39-mode hub-mode s40-mode hub-mode s41-mode hub-mode s43-mode hub-mode s6-mode hub-mode s38-mode hub-mode s45-mode hub-mode "
ACTUAL9=$(modes /tmp/suite-p9.out)
if [ "$EXPECTED9" = "$ACTUAL9" ]; then echo "  ok   mode sequence exact (the virtual key release and both near misses matched nothing)"
else echo "  FAIL mode sequence"; echo "    expected: $EXPECTED9"; echo "    actual:   $ACTUAL9"; FAILED=1; fi

echo "== phase 10: the mode history stack and the deprecated timeout =="
P10="3000 t 300"
P10="$P10 minus 300"        # into the mode that pushes itself onto the history stack
P10="$P10 backslash 300"
P10="$P10 underscore 400"   # back to whichever mode the stack held, without naming it
P10="$P10 z 300"
P10="$P10 pipe 900"         # the timeout returns to hub-mode with no key pressed
run /tmp/suite-p10.out "$P10"
for s in s46 s47 s48; do
    grep -q "Switching to $s-mode" /tmp/suite-p10.out && echo "  ok   $s" \
        || { echo "  FAIL $s never matched"; FAILED=1; }
done
EXPECTED10="idle-mode hub-mode s46-mode s47-mode s46-mode hub-mode s48-mode hub-mode "
ACTUAL10=$(modes /tmp/suite-p10.out)
if [ "$EXPECTED10" = "$ACTUAL10" ]; then
    echo "  ok   mode sequence exact (the stack came back to s46, and the timeout left s48)"
else echo "  FAIL mode sequence"; echo "    expected: $EXPECTED10"; echo "    actual:   $ACTUAL10"; FAILED=1; fi

echo "== phase 11: a timeout that only fires when the mouse is idle =="
P11="3000 t 300"
P11="$P11 caret 300"        # into the mode whose timeout is 400ms
P11="$P11 +plus 1500 -plus" # moving the mouse for far longer than that must hold the mode
P11="$P11 100 hash 300"     # only reachable from that mode, so reaching it proves it held
P11="$P11 z 300"
P11="$P11 caret 300 900"    # nothing pressed this time, so the timeout does fire
run /tmp/suite-p11.out "$P11"
grep -q "Switching to s50-mode" /tmp/suite-p11.out \
    && echo "  ok   the mode held while the mouse was moving" \
    || { echo "  FAIL the timeout fired while the mouse was moving"; FAILED=1; }
EXPECTED11="idle-mode hub-mode s49-mode s50-mode hub-mode s49-mode hub-mode "
ACTUAL11=$(modes /tmp/suite-p11.out)
if [ "$EXPECTED11" = "$ACTUAL11" ]; then
    echo "  ok   mode sequence exact (and the timeout did fire once the mouse was idle)"
else echo "  FAIL mode sequence"; echo "    expected: $EXPECTED11"; echo "    actual:   $ACTUAL11"; FAILED=1; fi

echo "== phase 12: a wait bounded at both ends =="
P12="3000 t 300"
P12="$P12 +f13 300 +f14 150 -f13 60 -f14 350 z 300"   # inside the 200 to 500 window
P12="$P12 +f13 80 +f14 150 -f13 60 -f14 350"          # too soon
P12="$P12 +f13 800 +f14 150 -f13 60 -f14 300"         # too late
run /tmp/suite-p12.out "$P12"
grep -q "Switching to s51-mode" /tmp/suite-p12.out && echo "  ok   s51" \
    || { echo "  FAIL s51 never matched"; FAILED=1; }
EXPECTED12="idle-mode hub-mode s51-mode hub-mode "
ACTUAL12=$(modes /tmp/suite-p12.out)
if [ "$EXPECTED12" = "$ACTUAL12" ]; then
    echo "  ok   mode sequence exact (neither too soon nor too late matched)"
else echo "  FAIL mode sequence"; echo "    expected: $EXPECTED12"; echo "    actual:   $ACTUAL12"; FAILED=1; fi

echo "== phase 13: the modifiers no other phase presses =="
P13="3000 t 300"
P13="$P13 +leftwin 100 f15 100 -leftwin 200 z 200"
P13="$P13 +rightctrl 100 f16 100 -rightctrl 200 z 200"
P13="$P13 +rightshift 100 f17 100 -rightshift 200 z 200"
P13="$P13 +rightalt 100 f18 100 -rightalt 200 z 200"
P13="$P13 +rightwin 100 f19 100 -rightwin 200 z 200"
run /tmp/suite-p13.out "$P13"
for s in s52 s53 s54 s55 s56; do
    grep -q "Switching to $s-mode" /tmp/suite-p13.out && echo "  ok   $s" \
        || { echo "  FAIL $s never matched"; FAILED=1; }
done
EXPECTED13="idle-mode hub-mode s52-mode hub-mode s53-mode hub-mode s54-mode hub-mode s55-mode hub-mode s56-mode hub-mode "
ACTUAL13=$(modes /tmp/suite-p13.out)
if [ "$EXPECTED13" = "$ACTUAL13" ]; then
    echo "  ok   mode sequence exact (each modifier gated only its own mode)"
else echo "  FAIL mode sequence"; echo "    expected: $EXPECTED13"; echo "    actual:   $ACTUAL13"; FAILED=1; fi

echo
[ $FAILED -eq 0 ] && echo "COMBO SUITE PASSED" || echo "COMBO SUITE FAILED"
exit $FAILED

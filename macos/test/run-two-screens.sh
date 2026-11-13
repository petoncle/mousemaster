#!/bin/bash
# Checks what needs two screens: a grid on each screen and on the active screen, the mouse, the
# target and a grid that follows the mouse across the edge between the screens, the velocity on
# each screen, saved positions on both screens, screen filters, UI hints on the second screen, and
# what the overlay and the indicator draw on each screen. The expected values come from the screens
# that mousemaster reports, so the checks follow the display arrangement: run them again after each
# change of the arrangement.
#
#   ./macos/test/run-two-screens.sh
#
# Needs two screens with the main screen at 0,0, the karabiner driver activated, root, Input
# Monitoring, Accessibility, Screen Recording, and Finder for a window.
set -u
here=$(cd "$(dirname "$0")" && pwd)
CONFIG=$here/two-screens.properties
cd "$here/../.." || exit 1
. "$here/mousemaster.sh"
FAILED=0
TOOL_JAVA=${JAVA_HOME:-$HOME/tools/jdk25/Contents/Home}/bin/java
PROBE=/tmp/two-screens-probe
clang -framework AppKit -framework ApplicationServices -o "$PROBE" "$here/probe.m" || exit 1

run() { # $1=output file, $2=simulation key events
    run_mousemaster "$1" "$2"
    assert_ran "$1" || FAILED=1
    assert_no_exception "$1" || FAILED=1
}

check() { # $1=description, then a condition for awk
    local description=$1
    shift
    if awk "BEGIN { exit !($*) }"; then
        echo "  ok   $description"
    else
        echo "  FAIL $description"
        FAILED=1
        return 1
    fi
}

moves() { grep -oE 'Moving mouse to \(-?[0-9]+, -?[0-9]+\)' "$1" \
              | sed 's/Moving mouse to (//;s/)//;s/, /,/'; }

saved() { # $1=output file, $2=history name
    grep -oE "Saved position \(-?[0-9.]+, -?[0-9.]+\) to $2-position-history\$" "$1" \
        | sed -E 's/Saved position \(//;s/\) to .*//;s/, /,/;s/\.0(,|$)/\1/g'
}

# "x y width height scale" of each screen mousemaster reported, in pixels.
screens() { grep -m1 'Screens \[' "$1" \
    | grep -oE 'x=-?[0-9]+, y=-?[0-9]+, width=[0-9]+, height=[0-9]+\], dpi=[0-9]+, scale=[0-9.]+' \
    | sed -E 's/[a-z]+=//g;s/[],]//g' | awk '{ print $1, $2, $3, $4, $6 }'; }

# The screen that holds a point: A for the main screen, B for the other, - for none.
screen_of() { # $1=x,y
    echo "$1" | awk -F, -v a="$A" -v b="$B" '{
        split(a, s, " "); if ($1 >= s[1] && $1 < s[1] + s[3] && $2 >= s[2] && $2 < s[2] + s[4]) { print "A"; exit }
        split(b, s, " "); if ($1 >= s[1] && $1 < s[1] + s[3] && $2 >= s[2] && $2 < s[2] + s[4]) { print "B"; exit }
        print "-" }'
}

center() { echo "$1" | awk '{ printf "%d,%d\n", $1 + $3 / 2, $2 + $4 / 2 }'; }
field() { echo "$1" | cut -d' ' -f"$2"; }
x() { echo "${1%,*}"; }
y() { echo "${1#*,}"; }

echo "== layout and geometry: a grid on each screen, and on the active screen =="
D="4000 n 300 p 300 a 400 esc 300 p 300 b 400 esc 300"
for key in a b c d e f g h; do D="$D g 300 $key 400 esc 300"; done
for screen in a b; do
    D="$D p 300 $screen 400 esc 300"
    for key in a b c d; do D="$D y 300 $key 400 esc 300"; done
done
OUT=/tmp/two-screens-geometry.out
run $OUT "$D"
LAYOUT=$(screens $OUT)
A=$(echo "$LAYOUT" | awk '$1 == 0 && $2 == 0')
B=$(echo "$LAYOUT" | awk '!($1 == 0 && $2 == 0)')
if [ "$(echo "$LAYOUT" | wc -l | tr -d ' ')" != "2" ] || [ -z "$A" ] || [ -z "$B" ]; then
    echo "  ABORT: needs two screens with the main screen at 0,0, mousemaster reported: $LAYOUT"
    exit 1
fi
# The arrangement macOS has, in points.
PA=$("$PROBE" displays | sed -n 1p)
PB=$("$PROBE" displays | sed -n 2p)
echo "  screens in pixels: A [$A] B [$B], in points: A [$PA] B [$PB]"
# The direction from A to B, and the time at 1000 points a second from the center of A to the
# center of B.
read -r DAB DBA HOLD <<< "$(echo "$PA $PB" | awk '{
    if ($5 >= $1 + $3) print "l j", int($3 / 2 + $7 / 2)
    else if ($5 + $7 <= $1) print "j l", int($3 / 2 + $7 / 2)
    else if ($6 + $8 <= $2) print "i k", int($4 / 2 + $8 / 2)
    else print "k i", int($4 / 2 + $8 / 2) }')"
echo "  B is $(echo "$DAB" | awk '{ print ($1 == "l" ? "right" : ($1 == "j" ? "left" : ($1 == "i" ? "above" : "below"))) }') of A"
M=()
while read -r move; do M+=("$move"); done < <(moves $OUT)
check "20 hint selections moved the mouse (got ${#M[@]})" "${#M[@]} == 20"
# The keys of the place hints of A and B, and of the first hint of B in odd-mode.
if [ "$(screen_of "${M[0]}")" = "A" ]; then KA=a; KB=b; KOB=j; else KA=b; KB=a; KOB=a; fi
check "the place hints are the centers of A and B: ${M[0]} ${M[1]}" \
    "\"$(printf '%s\n' "${M[0]}" "${M[1]}" | sort | tr '\n' ' ')\" == \"$(printf '%s\n' "$(center "$A")" "$(center "$B")" | sort | tr '\n' ' ')\""
quadrants() { echo "$1" | awk '{ for (r = 0; r < 2; r++) for (c = 0; c < 2; c++)
                                     print $1 + $3 * (2 * c + 1) / 4 "," $2 + $4 * (2 * r + 1) / 4 }'; }
EXPECTED=$( (quadrants "$A"; quadrants "$B") | sort | tr '\n' ' ')
ACTUAL=$(printf '%s\n' "${M[@]:2:8}" | sort | tr '\n' ' ')
check "each hint of the grid on each screen is a quadrant center: $ACTUAL" "\"$EXPECTED\" == \"$ACTUAL\""
for i in 10 15; do
    SCREEN=$(screen_of "${M[$i]}")
    [ "$SCREEN" = "A" ] && RECT=$A || RECT=$B
    EXPECTED=$(quadrants "$RECT" | sort | tr '\n' ' ')
    ACTUAL=$(printf '%s\n' "${M[@]:$((i + 1)):4}" | sort | tr '\n' ' ')
    check "with the mouse on $SCREEN, the active screen grid is on $SCREEN: $ACTUAL" \
        "\"$EXPECTED\" == \"$ACTUAL\""
done

echo "== the mouse, the target and a grid that follows the mouse, across the edge =="
D="4000 n 300 p 300 $KA 400 esc 300 f1 300 +$DAB $HOLD -$DAB 400 f2 300 +$DBA $HOLD -$DBA 400 f3 300"
D="$D p 300 $KA 400 esc 300 f4 300 +$DAB 200 -$DAB 400 f5 300"
D="$D p 300 $KB 400 esc 300 f6 300 +$DBA 200 -$DBA 400 f7 300"
D="$D p 300 $KA 400 esc 300 r 300 f1 300 +$DAB $HOLD -$DAB 400 f2 300 m 600 esc 300 f8 300"
D="$D p 300 $KA 400 esc 300 f 300 +$DAB $HOLD -$DAB 600 f1 300 a 600 esc 300"
OUT=/tmp/two-screens-cross.out
run $OUT "$D"
S1=$(saved $OUT s1); S2=$(saved $OUT s2); S3=$(saved $OUT s3)
echo "  mouse: from $S1 to $S2 and back to $S3"
check "the mouse starts on A" "\"$(screen_of "$S1")\" == \"A\""
check "the mouse crossed from A to B" "\"$(screen_of "$S2")\" == \"B\""
check "the mouse crossed back from B to A" "\"$(screen_of "$S3")\" == \"A\""
along() { # $1=from x,y, $2=to x,y: the distance in the direction from A to B, and across it
    echo "$1 $2" | tr ',' ' ' | awk -v d="$DAB" '{
        if (d == "l") print $3 - $1, $4 - $2; else if (d == "j") print $1 - $3, $4 - $2
        else if (d == "k") print $4 - $2, $3 - $1; else print $2 - $4, $3 - $1 }'; }
# A point in points: its offset into its screen, unscaled, from the origin of that screen in points.
in_points() { # $1=x,y
    [ "$(screen_of "$1")" = A ] && set -- "$1" "$A" "$PA" || set -- "$1" "$B" "$PB"
    echo "$1 $2 $3" | tr ',' ' ' | awk '{ printf "%d,%d", $8 + ($1 - $3) / $7, $9 + ($2 - $4) / $7 }'; }
# A straight move does not go sideways where it crosses the edge.
read -r _ ACROSS <<< "$(along "$(in_points "$S1")" "$(in_points "$S2")")"
check "the mouse went straight across the edge: from $(in_points "$S1") to $(in_points "$S2") in points" \
    "\"$(screen_of "$S2")\" == \"B\" && $ACROSS ^ 2 <= 1"
for pair in "A $(field "$A" 5) $(saved $OUT s4) $(saved $OUT s5) 1" \
            "B $(field "$B" 5) $(saved $OUT s6) $(saved $OUT s7) -1"; do
    read -r SCREEN SCALE FROM TO SIGN <<< "$pair"
    read -r DISTANCE ACROSS <<< "$(along "$FROM" "$TO")"
    check "200ms at 1000 moved the mouse $DISTANCE pixels on $SCREEN, scale $SCALE (expected $(awk "BEGIN { print 200 * $SCALE }") within 15%)" \
        "$SIGN * $DISTANCE > 170 * $SCALE && $SIGN * $DISTANCE < 230 * $SCALE && $ACROSS == 0"
done
T1=$(saved $OUT t1); T2=$(saved $OUT t2); S8=$(saved $OUT s8)
echo "  target: from $T1 to $T2, mouse moved to $S8"
check "the target starts on A" "\"$(screen_of "$T1")\" == \"A\""
check "the target crossed from A to B" "\"$(screen_of "$T2")\" == \"B\""
read -r _ ACROSS <<< "$(along "$(in_points "$T1")" "$(in_points "$T2")")"
check "the target went straight across the edge: from $(in_points "$T1") to $(in_points "$T2") in points" \
    "\"$(screen_of "$T2")\" == \"B\" && $ACROSS ^ 2 <= 1"
check "move-to.target put the mouse on the target" "\"$S8\" == \"$T2\""
F1=$(saved $OUT f1)
LAST=$(moves $OUT | tail -1)
[ "$(screen_of "$F1")" = "B" ] && RECT=$B || RECT=$A
EXPECTED=$(echo "$F1 $RECT" | tr ',' ' ' | awk '{ printf "%d,%d", $1 - $5 / 8, $2 - $6 / 8 }')
echo "  follow grid: mouse at $F1, top left hint at $LAST"
check "the grid followed the mouse onto $(screen_of "$F1") with the size of that screen (expected $EXPECTED)" \
    "\"$(screen_of "$F1")\" == \"B\" && ($(x "$LAST") - $(x "$EXPECTED")) ^ 2 <= 4 && ($(y "$LAST") - $(y "$EXPECTED")) ^ 2 <= 4"

echo "== saved positions on both screens, and screen filters =="
D="4000 n 300 o 300 a 400 esc 300 w 300 o 300 j 400 esc 300 w 300"
D="$D h 300 a 400 esc 300 f3 300 h 300 b 400 esc 300 f4 300 c 400 f5 300 c 400 f6 300"
D="$D p 300 a 400 esc 300 x 300 z 300 p 300 b 400 esc 300 x 300 z 300"
D="$D p 300 a 400 esc 300 q 300 a 400 esc 300 f7 300 p 300 b 400 esc 300 q 300 a 400 esc 300 f8 300"
OUT=/tmp/two-screens-saved.out
run $OUT "$D"
PINS=$(saved $OUT pin | sort | tr '\n' ' ')
echo "  pins: $PINS"
check "one pin on each screen" \
    "\"$(for p in $PINS; do screen_of "$p"; done | sort | tr '\n' ' ')\" == \"A B \""
check "the pin hints put the mouse on the pins: $(saved $OUT s3) $(saved $OUT s4)" \
    "\"$( (saved $OUT s3; saved $OUT s4) | sort | tr '\n' ' ')\" == \"$PINS\""
check "cycle-next put the mouse on the pins: $(saved $OUT s5) $(saved $OUT s6)" \
    "\"$( (saved $OUT s5; saved $OUT s6) | sort | tr '\n' ' ')\" == \"$PINS\""
for history in onex twox; do
    [ $history = onex ] && WANTED=1 || WANTED=2
    POSITIONS=$(saved $OUT $history)
    COUNT=$(echo "$POSITIONS" | grep -c ,)
    SCALE=$( [ "$COUNT" = 1 ] && { [ "$(screen_of "$POSITIONS")" = A ] && field "$A" 5 || field "$B" 5; })
    check "_{${WANTED}00%} saved only on the ${WANTED}00% screen: $COUNT saved, at $POSITIONS, scale ${SCALE:-none}" \
        "$COUNT == 1 && \"$SCALE\" + 0 == $WANTED"
done
for history in s7 s8; do
    POSITION=$(saved $OUT $history)
    [ "$(screen_of "$POSITION")" = A ] && RECT=$A || RECT=$B
    EXPECTED=$(echo "$RECT" | awk '{ columns = $5 == 2 ? 4 : 2
                                     printf "%d,%d", $1 + $3 / columns / 2, $2 + $4 / 2 }')
    check "the active screen grid of $(screen_of "$POSITION") has the columns of its scale: first hint $POSITION (expected $EXPECTED)" \
        "($(x "$POSITION") - $(x "$EXPECTED")) ^ 2 <= 1 && $(y "$POSITION") == $(y "$EXPECTED")"
done

echo "== UI hints in a window on B =="
open /System/Library/Frameworks
sleep 3
read -r WX WY WW WH <<< "$("$PROBE" window "$(field "$PB" 1 | awk '{ print $1 + 60 }')" \
                                            "$(field "$PB" 2 | awk '{ print $1 + 60 }')")"
# The window in pixels: its offset into B in points, scaled, from the origin of B.
WINDOW=$(echo "$WX $WY $WW $WH $B $PB" | awk '{
    printf "%d %d %d %d", $5 + ($1 - $10) * $9, $6 + ($2 - $11) * $9, $3 * $9, $4 * $9 }')
echo "  Finder window: $WX $WY ${WW}x$WH points, $WINDOW in pixels"
OUT=/tmp/two-screens-ui.out
run $OUT "4000 n 300 e 300 m 400 f1 300 esc 300 u 2000 a 600 a 600 esc 300 f2 300"
WIN=$(saved $OUT win)
EXPECTED=$(center "$WINDOW")
check "the active window center $WIN is the Finder window center $EXPECTED" \
    "($(x "$WIN") - $(x "$EXPECTED")) ^ 2 <= 4 && ($(y "$WIN") - $(y "$EXPECTED")) ^ 2 <= 4"
UI=$(saved $OUT s2)
check "a UI hint put the mouse in the window: $UI" "$(echo "$UI $WINDOW" | tr ',' ' ' | awk '{
    print ($1 >= $3 && $1 < $3 + $5 && $2 >= $4 && $2 < $4 + $6) }') == 1"

echo "== what each screen shows =="
# One file per screen, the main screen first.
capture() { # $1=name
    rm -f "/tmp/two-screens-$1-A.png" "/tmp/two-screens-$1-B.png"
    screencapture -x "/tmp/two-screens-$1-A.png" "/tmp/two-screens-$1-B.png"
}
# The capture of a screen before the run holds the colors the desktop already has, which then do
# not count.
box() { local file=$1; BEFORE="/tmp/two-screens-before-${file: -5:1}.png" \
    "$TOOL_JAVA" "$here/Pixels.java" box "$@"; }
runs() { local file=$1; BEFORE="/tmp/two-screens-before-${file: -5:1}.png" \
    "$TOOL_JAVA" "$here/Pixels.java" runs "$@"; }
count() { box "$@" | cut -d' ' -f1; }
# A box that sits on a point, its size in device pixels: "centerX,centerY width height".
measured() { box "$@" | awk 'NF == 5 { printf "%d,%d %d %d", ($2 + $4 + 1) / 2, ($3 + $5 + 1) / 2, $4 - $2 + 1, $5 - $3 + 1 }'; }
# A point in pixels on its screen's capture.
local_point() { # $1=x,y $2=screen rectangle
    echo "$1 $2" | tr ',' ' ' | awk '{ printf "%d,%d", $1 - $3, $2 - $4 }'; }
near() { # $1=x,y $2=x,y $3=pixels
    awk "BEGIN { exit !(($(x "$1") - $(x "$2")) ^ 2 + ($(y "$1") - $(y "$2")) ^ 2 <= $3 ^ 2) }"; }

capture before
OUT=/tmp/two-screens-show.out
D="4000 n 300 p 300 $KA 400 esc 300 w 300 p 300 $KB 400 esc 300 f1 300 v 4000 esc 300 g 4000 esc 300"
D="$D p 300 $KA 400 esc 300 f 300 +$DAB $HOLD -$DAB 4000 esc 300"
D="$D o 300 $KOB 400 esc 300 w 300 h 4000 esc 4000"
start_mousemaster $OUT "$D"
wait_for $OUT "Switching to show-mode"; sleep 1.5; capture show
CURSOR=$("$PROBE" cursor)
wait_for $OUT "Switching to geometry-mode"; sleep 1.5; capture geometry
wait_for $OUT "Switching to follow-mode"; sleep "$(awk "BEGIN { print $HOLD / 1000 + 1.5 }")"; capture follow
wait_for $OUT "Switching to pin-hint-mode"; sleep 1.5; capture pins
wait_for $OUT "Finished simulating keys"; capture after
sleep 1
stop_mousemaster
assert_ran $OUT || FAILED=1
assert_no_exception $OUT || FAILED=1

MOUSE=$(saved $OUT s1)
PIN=$(saved $OUT pin | head -1)
SA=$(field "$A" 5); SB=$(field "$B" 5)
echo "  show-mode: mouse $MOUSE on $(screen_of "$MOUSE"), pin $PIN on $(screen_of "$PIN"), the pointer at $CURSOR points"
# The offset of the mouse into B, in points, from the origin of B in points.
WANTED=$(echo "$MOUSE $B $PB" | tr ',' ' ' | awk '{ printf "%d %d", $8 + ($1 - $3) / $7, $9 + ($2 - $4) / $7 }')
check "the pointer is at the mouse position, $WANTED in points" "\"$CURSOR\" == \"$WANTED\""
read -r AT W H <<< "$(measured /tmp/two-screens-show-B.png magenta)"
WANTED=$(local_point "$MOUSE" "$B")
check "the indicator is on B at the mouse $WANTED, ${W:-0}x${H:-0} for 40 at scale $SB: at ${AT:-nothing}" \
    "\"${AT:-x}\" != \"x\" && (${W:-0} - 40 * $SB) ^ 2 <= 16 && (${H:-0} - 40 * $SB) ^ 2 <= 16" \
    && { near "$AT" "$WANTED" "$(awk "BEGIN { print 12 * $SB }")" \
         || { echo "  FAIL the indicator is not at the mouse"; FAILED=1; }; }
check "no indicator on A" "$(count /tmp/two-screens-show-A.png magenta) < 20"
read -r AT W H <<< "$(measured /tmp/two-screens-show-A.png cyan)"
WANTED=$(local_point "$PIN" "$A")
check "the layer at the pin is on A at $WANTED, ${W:-0}x${H:-0} for 40 at scale $SA: at ${AT:-nothing}" \
    "\"${AT:-x}\" != \"x\" && (${W:-0} - 40 * $SA) ^ 2 <= 16 && (${H:-0} - 40 * $SA) ^ 2 <= 16"
check "the line from the pin to the mouse shows on A ($(count /tmp/two-screens-show-A.png blue) pixels)" \
    "$(count /tmp/two-screens-show-A.png blue) > 100"
check "the line from the pin to the mouse shows on B ($(count /tmp/two-screens-show-B.png blue) pixels)" \
    "$(count /tmp/two-screens-show-B.png blue) > 100"

for screen in A B; do
    [ $screen = A ] && RECT=$A || RECT=$B
    FILE=/tmp/two-screens-geometry-$screen.png
    read -r N X0 Y0 X1 Y1 <<< "$(box $FILE green)"
    W=$(field "$RECT" 3); H=$(field "$RECT" 4)
    check "the grid covers $screen: green from $X0,$Y0 to $X1,$Y1 on ${W}x$H" \
        "${N:-0} > 0 && $X0 <= 4 && $Y0 <= 4 && $X1 >= $W - 5 && $Y1 >= $H - 5"
    eval "RUNS_$screen=\"$(runs $FILE green row $((H * 3 / 8)))\""
    eval "LABEL_$screen=\"$(measured $FILE yellow 0 0 $((W / 2)) $((H / 2)))\""
done
echo "  vertical lines on a row: A [$RUNS_A] B [$RUNS_B]"
check "B has as many vertical grid lines as A" "$(echo "$RUNS_A" | wc -w) == $(echo "$RUNS_B" | wc -w)"
thicknesses() { echo "$1" | tr ' ' '\n' | cut -d: -f2 | tr '\n' ' '; }
TA=$(thicknesses "$RUNS_A"); TB=$(thicknesses "$RUNS_B")
check "each line of B is $SB/$SA as thick as on A: [$TB] and [$TA]" "$(echo "$TA|$TB" | awk -F'|' -v ratio="$(awk "BEGIN { print $SB / $SA }")" '{
    n = split($1, a, " "); m = split($2, b, " "); ok = n == m && n > 0
    for (i = 1; i <= n; i++) if ((b[i] - a[i] * ratio) ^ 2 > 1) ok = 0
    print ok }') == 1"
LABEL_HEIGHT_A=$(field "$LABEL_A" 3); LABEL_HEIGHT_B=$(field "$LABEL_B" 3)
check "a label of B is $SB/$SA as tall as on A: ${LABEL_HEIGHT_B:-0} and ${LABEL_HEIGHT_A:-0} pixels" \
    "${LABEL_HEIGHT_A:-0} > 0 && ${LABEL_HEIGHT_B:-0} / ${LABEL_HEIGHT_A:-1} > 0.9 * $SB / $SA && ${LABEL_HEIGHT_B:-0} / ${LABEL_HEIGHT_A:-1} < 1.1 * $SB / $SA"

check "the grid that followed the mouse left nothing on A" "$(count /tmp/two-screens-follow-A.png green) < 20"
read -r N X0 Y0 X1 Y1 <<< "$(box /tmp/two-screens-follow-B.png green)"
check "the grid that followed the mouse is half of B: green ${N:+$((X1 - X0 + 1))x$((Y1 - Y0 + 1))} for $(($(field "$B" 3) / 2))x$(($(field "$B" 4) / 2))" \
    "${N:-0} > 0 && ($X1 - $X0 + 1 - $(field "$B" 3) / 2) ^ 2 <= 36 && ($Y1 - $Y0 + 1 - $(field "$B" 4) / 2) ^ 2 <= 36"
# The label of the top left cell of the grid.
LABEL_FOLLOW=$(measured /tmp/two-screens-follow-B.png yellow ${X0:-0} ${Y0:-0} $(((${X1:-0} - ${X0:-0}) / 2)) $(((${Y1:-0} - ${Y0:-0}) / 2)))
LABEL_HEIGHT_FOLLOW=$(field "$LABEL_FOLLOW" 3)
check "its labels are as tall as the labels of the grid on B: ${LABEL_HEIGHT_FOLLOW:-0} and $LABEL_HEIGHT_B pixels" \
    "(${LABEL_HEIGHT_FOLLOW:-0} - ${LABEL_HEIGHT_B:-0}) ^ 2 <= 4"

for pin in $(saved $OUT pin); do
    SCREEN=$(screen_of "$pin")
    [ "$SCREEN" = A ] && RECT=$A || RECT=$B
    LOCAL=$(local_point "$pin" "$RECT")
    S=$(field "$RECT" 5)
    N=$(count "/tmp/two-screens-pins-$SCREEN.png" yellow \
            "$(awk "BEGIN { print int($(x "$LOCAL") - 60 * $S) }")" \
            "$(awk "BEGIN { print int($(y "$LOCAL") - 60 * $S) }")" \
            "$(awk "BEGIN { print int(120 * $S) }")" "$(awk "BEGIN { print int(120 * $S) }")")
    check "the hint of the pin $pin shows on $SCREEN ($N label pixels)" "$N > 20"
done
for color in green yellow magenta cyan blue; do
    for screen in A B; do
        N=$(count "/tmp/two-screens-after-$screen.png" $color)
        [ "$N" -gt 20 ] &&{ echo "  FAIL $N $color pixels left on $screen after the modes ended"; FAILED=1; }
    done
done

echo
[ $FAILED -eq 0 ] && echo "TWO SCREENS PASSED" || echo "TWO SCREENS FAILED"
exit $FAILED

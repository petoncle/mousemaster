#!/bin/bash
# Asserts that the cursor moves the distance and the way the mouse properties say it should. The
# wheel had its two axes swapped and both directions inverted on macos, and nothing would have
# caught the same mistake in the move commands.
#
#   QT_LIB=~/Qt/6.8.2/macos/lib ./macos/test/run-mouse-move.sh
#
# Needs the karabiner driver activated, root, and Input Monitoring.
set -u
here=$(cd "$(dirname "$0")" && pwd)
CONFIG=$here/mouse-move.properties
cd "$here/../.." || exit 1
. "$here/mousemaster.sh"
FAILED=0

run() { # $1=output file, $2=simulation key events
    run_mousemaster "$1" "$2"
    assert_ran "$1" || FAILED=1
    assert_no_exception "$1" || FAILED=1
}

moves() { grep -oE 'Moving mouse to \(-?[0-9]+, -?[0-9]+\)' "$1" \
              | sed 's/Moving mouse to (//;s/)//;s/, /,/'; }

# The scale of the screen mousemaster reported around a position: a velocity is in pixels at 100%.
scale_at() { # $1=output file, $2=x,y
    grep -m1 'Screens \[' "$1" \
        | grep -oE 'x=-?[0-9]+, y=-?[0-9]+, width=[0-9]+, height=[0-9]+\], dpi=[0-9]+, scale=[0-9.]+' \
        | sed -E 's/[a-z]+=//g;s/[],]//g' \
        | awk -v p="$2" '{ split(p, q, ",")
              if (q[1] >= $1 && q[1] < $1 + $3 && q[2] >= $2 && q[2] < $2 + $4) print $6 }'
}

# One round centers the cursor, holds a direction, then reads the position back, so the log holds
# the center and the moved position as consecutive pairs.
round() { echo "y 300 a 600 esc 300 t 300 +$1 $2 -$1 400 s 300 h 300 a 600 esc 400"; }

echo "== a velocity of 400 held for one second =="
run /tmp/move-p1.out "4000 $(round k 1000)"
PAIR=$(moves /tmp/move-p1.out | tr '\n' ' ')
SCALE=$(scale_at /tmp/move-p1.out "${PAIR%% *}")
echo "  positions: $PAIR, scale $SCALE"
echo "$PAIR" | awk -v scale="$SCALE" '{ split($1, a, ","); split($2, b, ",")
    if (NF != 2) { print "  FAIL expected 2 positions, got " NF; exit 1 }
    if (a[1] != b[1]) { print "  FAIL x drifted from " a[1] " to " b[1]; exit 1 }
    delta = b[2] - a[2]; want = 400 * scale
    if (delta > want * 0.85 && delta < want * 1.15)
        print "  ok   moved " delta " pixels down, within 15% of " want
    else { print "  FAIL moved " delta " pixels down, expected about " want; exit 1 } }' || FAILED=1

echo "== each direction moves along its own axis, the right way =="
P2="4000"
for key in i k j l; do P2="$P2 $(round "$key" 500)"; done
run /tmp/move-p2.out "$P2"
SCALE=$(scale_at /tmp/move-p2.out "$(moves /tmp/move-p2.out | head -1)")
moves /tmp/move-p2.out | tr '\n' ' ' \
    | awk -v scale="$SCALE" '{ if (NF != 8) { print "  FAIL expected 8 positions, got " NF; exit 1 }
    want = 200 * scale
    split("i k j l", key, " "); split("0 0 -1 1", wantX, " "); split("-1 1 0 0", wantY, " ")
    for (i = 1; i <= 4; i++) {
        split($(2 * i - 1), from, ","); split($(2 * i), to, ",")
        dx = to[1] - from[1]; dy = to[2] - from[2]
        okX = (wantX[i] == 0) ? (dx == 0) : (dx * wantX[i] > want * 0.85 && dx * wantX[i] < want * 1.15)
        okY = (wantY[i] == 0) ? (dy == 0) : (dy * wantY[i] > want * 0.85 && dy * wantY[i] < want * 1.15)
        if (okX && okY) print "  ok   " key[i] " moved (" dx ", " dy ")"
        else { print "  FAIL " key[i] " moved (" dx ", " dy "), expected about (" \
                     wantX[i] * want ", " wantY[i] * want ")"; bad = 1 }
    }
    exit bad ? 1 : 0 }' || FAILED=1

echo
[ $FAILED -eq 0 ] && echo "MOUSE MOVE PASSED" || echo "MOUSE MOVE FAILED"
exit $FAILED

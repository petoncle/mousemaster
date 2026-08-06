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

# One round centers the cursor, holds a direction, then reads the position back, so the log holds
# the center and the moved position as consecutive pairs.
round() { echo "y 300 a 600 esc 300 t 300 +$1 $2 -$1 400 s 300 h 300 a 600 esc 400"; }

echo "== 400 pixels per second held for one second =="
run /tmp/move-p1.out "4000 $(round k 1000)"
PAIR=$(moves /tmp/move-p1.out | tr '\n' ' ')
echo "  positions: $PAIR"
echo "$PAIR" | awk '{ split($1, a, ","); split($2, b, ",")
    if (NF != 2) { print "  FAIL expected 2 positions, got " NF; exit 1 }
    if (a[1] != b[1]) { print "  FAIL x drifted from " a[1] " to " b[1]; exit 1 }
    delta = b[2] - a[2]
    if (delta > 340 && delta < 460) print "  ok   moved " delta " pixels down, within 15% of 400"
    else { print "  FAIL moved " delta " pixels down, expected about 400"; exit 1 } }' || FAILED=1

echo "== each direction moves along its own axis, the right way =="
P2="4000"
for key in i k j l; do P2="$P2 $(round "$key" 500)"; done
run /tmp/move-p2.out "$P2"
moves /tmp/move-p2.out | tr '\n' ' ' \
    | awk '{ if (NF != 8) { print "  FAIL expected 8 positions, got " NF; exit 1 }
    split("i k j l", key, " "); split("0 0 -1 1", wantX, " "); split("-1 1 0 0", wantY, " ")
    for (i = 1; i <= 4; i++) {
        split($(2 * i - 1), from, ","); split($(2 * i), to, ",")
        dx = to[1] - from[1]; dy = to[2] - from[2]
        okX = (wantX[i] == 0) ? (dx == 0) : (dx * wantX[i] > 170 && dx * wantX[i] < 230)
        okY = (wantY[i] == 0) ? (dy == 0) : (dy * wantY[i] > 170 && dy * wantY[i] < 230)
        if (okX && okY) print "  ok   " key[i] " moved (" dx ", " dy ")"
        else { print "  FAIL " key[i] " moved (" dx ", " dy "), expected about (" \
                     wantX[i] * 200 ", " wantY[i] * 200 ")"; bad = 1 }
    }
    exit bad ? 1 : 0 }' || FAILED=1

echo
[ $FAILED -eq 0 ] && echo "MOUSE MOVE PASSED" || echo "MOUSE MOVE FAILED"
exit $FAILED

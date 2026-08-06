#!/bin/bash
# Asserts that shrinking the grid keeps the half the direction names and that its center lands
# where the arithmetic says, which is the grid's side of the screen geometry the hint test covers.
#
#   QT_LIB=~/Qt/6.8.2/macos/lib ./macos/test/run-grid-geometry.sh
#
# Needs the karabiner driver activated, root, and Input Monitoring.
set -u
here=$(cd "$(dirname "$0")" && pwd)
CONFIG=$here/grid-geometry.properties
OUT=/tmp/grid-geometry.out
cd "$here/../.." || exit 1
. "$here/mousemaster.sh"
FAILED=0

# Center the mouse on the grid, shrink to the top half and center again, then to the left half of
# that, saving each center. Then read the three back, one hint selection at a time.
SIMULATE="4000 t 300 space 400 s 400 i 400 space 400 s 400 j 400 space 400 s 400 esc 400"
for key in a b c; do SIMULATE="$SIMULATE h 300 $key 600 esc 400"; done
run_mousemaster "$OUT" "$SIMULATE"
assert_ran "$OUT" || exit 1
assert_no_exception "$OUT" || exit 1

ACTUAL=$(grep -oE 'Moving mouse to \(-?[0-9]+, -?[0-9]+\)' "$OUT" \
             | sed 's/Moving mouse to (//;s/)//;s/, /,/' | sort -u)
COUNT=$(echo "$ACTUAL" | wc -l | tr -d ' ')
echo "  positions: $(echo "$ACTUAL" | tr '\n' ' ')"
[ "$COUNT" = "3" ] && echo "  ok   three distinct grid centers were saved and read back" \
    || { echo "  FAIL expected 3 distinct positions, got $COUNT"; FAILED=1; }
# The first center is the screen's own center, the one both later steps are measured from, and it is
# the largest in both axes since every shrink keeps the top left half.
SIZE=$(grep -m1 'Screens \[' "$OUT" | grep -oE 'width=[0-9]+, height=[0-9]+' | head -1 \
           | sed -E 's/[a-z]+=//g;s/, / /')
echo "$ACTUAL" | tr '\n' ' ' | awk -v size="$SIZE" '{
    split(size, s, " ")
    for (i = 1; i <= NF; i++) {
        split($i, p, ",")
        if (i == 1 || p[1] + 0 > cx + 0) cx = p[1] + 0
        if (i == 1 || p[2] + 0 > cy + 0) cy = p[2] + 0
    }
    split("whole-screen top-half top-left-quarter", step, " ")
    want[1] = cx "," cy
    want[2] = cx "," cy - s[2] / 4
    want[3] = cx - s[1] / 4 "," cy - s[2] / 4
    for (i = 1; i <= 3; i++) {
        found = 0
        for (j = 1; j <= NF; j++) if ($j == want[i]) found = 1
        if (found) print "  ok   the " step[i] " grid centered on " want[i]
        else { print "  FAIL the " step[i] " grid never centered on " want[i]; bad = 1 }
    }
    exit bad ? 1 : 0 }' || FAILED=1

# Snapping goes to the edge of the cell the mouse is in, and one cell covering the screen makes
# that edge the screen's own. Moving the grid shifts it by its whole width, so a grid shrunk to a
# quarter lands on the quarter beside it.
echo "== snapping to a cell edge, and moving the grid =="
SIMULATE2="4000 t 300 space 400 n 400 u 400 s 400 esc 400 h 300 a 600 esc 400"
SIMULATE2="$SIMULATE2 t 300 space 300 i 300 j 300 space 300 p 300 space 400 s 400 esc 400"
SIMULATE2="$SIMULATE2 h 300 b 600 esc 400"
run_mousemaster "$OUT" "$SIMULATE2"
assert_ran "$OUT" || exit 1
assert_no_exception "$OUT" || exit 1
grep -m1 'Screens \[' "$OUT" | grep -oE 'x=-?[0-9]+, y=-?[0-9]+, width=[0-9]+, height=[0-9]+' \
    | sed -E 's/[a-z]+=//g;s/, / /g' > /tmp/grid-screens.txt
MOVED=$(grep -oE 'Moving mouse to \(-?[0-9]+, -?[0-9]+\)' "$OUT" \
            | sed 's/Moving mouse to (//;s/)//;s/, /,/' | sort -u | tr '\n' ' ')
echo "  positions: $MOVED"
awk -v moved="$MOVED" 'BEGIN { split(moved, seen, " ") }
    { corner[$1 "," $2] = 1; moved3[$1 + 3 * $3 / 4 "," $2 + $4 / 4] = 1 }
    END {
        for (i in seen) {
            if (seen[i] in corner) foundCorner = seen[i]
            if (seen[i] in moved3) foundMoved = seen[i]
        }
        if (foundCorner) print "  ok   snapping twice reached the screen corner " foundCorner
        else { print "  FAIL no position is a screen corner"; bad = 1 }
        if (foundMoved) print "  ok   the moved grid centered on " foundMoved
        else { print "  FAIL no position is the moved quarter center"; bad = 1 }
        exit bad ? 1 : 0 }' /tmp/grid-screens.txt || FAILED=1

# Cycling from the second of two saved positions goes back to the first. Where it landed is read by
# saving again into a history that is still empty, since saving a position twice logs nothing.
echo "== cycling to the previous position =="
SIMULATE3="4000 t 300 space 400 s 400 i 300 j 300 space 400 s 400 w 400 x 400 esc 400"
run_mousemaster "$OUT" "$SIMULATE3"
assert_ran "$OUT" || exit 1
assert_no_exception "$OUT" || exit 1
saved() { # $1=history name, $2=which
    grep -oE "Saved position \(-?[0-9.]+, -?[0-9.]+\) to $1\$" "$OUT" \
        | sed 's/Saved position (//;s/) to .*//;s/, /,/' | sed -n "$2p"
}
FIRST=$(saved position-history 1)
SECOND=$(saved position-history 2)
CYCLED=$(saved probe-position-history 1)
echo "  saved: $FIRST then $SECOND, cycled to: $CYCLED"
[ -n "$FIRST" ] && [ "$FIRST" != "$SECOND" ] \
    && echo "  ok   two different positions were saved" \
    || { echo "  FAIL expected two different saved positions"; FAILED=1; }
[ -n "$CYCLED" ] && [ "$CYCLED" = "$FIRST" ] \
    && echo "  ok   cycle-next went back to the first position" \
    || { echo "  FAIL cycle-next landed on $CYCLED, expected $FIRST"; FAILED=1; }

echo
[ $FAILED -eq 0 ] && echo "GRID GEOMETRY PASSED" || echo "GRID GEOMETRY FAILED"
exit $FAILED

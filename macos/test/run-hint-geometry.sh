#!/bin/bash
# Asserts that a hint lands the cursor exactly where the grid says it should, which is what pins
# down screen enumeration, the direction of the y axis and the scale factor on macos. The expected
# positions are derived from the screens mousemaster reports, so the test follows the display setup.
#
#   QT_LIB=~/Qt/6.8.2/macos/lib ./macos/test/run-hint-geometry.sh
#
# Needs the karabiner driver activated, root, and Input Monitoring.
set -u
here=$(cd "$(dirname "$0")" && pwd)
CONFIG=$here/hint-geometry.properties
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

# The center of each quadrant of each screen mousemaster reported, which is where a 2 by 2 grid of
# hints puts them.
expected() {
    grep -m1 'Screens \[' "$1" \
        | grep -oE 'x=-?[0-9]+, y=-?[0-9]+, width=[0-9]+, height=[0-9]+' \
        | sed -E 's/[a-z]+=//g;s/, / /g' \
        | awk '{ for (r = 0; r < 2; r++) for (c = 0; c < 2; c++)
                     print $1 + $3 * (2 * c + 1) / 4 "," $2 + $4 * (2 * r + 1) / 4 }'
}

echo "== every screen: each hint of a 2 by 2 grid per screen =="
P1="4000"
for key in a b c d e f g h; do P1="$P1 t 300 $key 400 esc 300"; done
run /tmp/geometry-p1.out "$P1"
EXPECTED1=$(expected /tmp/geometry-p1.out | sort -u)
ACTUAL1=$(moves /tmp/geometry-p1.out | sort -u)
COUNT=$(moves /tmp/geometry-p1.out | wc -l | tr -d ' ')
# Four cells per screen, so how many hints there are follows the display setup. The eight
# selection keys the configuration lists are what caps this at two screens.
WANTED=$(echo "$EXPECTED1" | wc -l | tr -d ' ')
[ "$COUNT" = "$WANTED" ] && echo "  ok   all $WANTED hints moved the mouse" \
    || { echo "  FAIL $COUNT of $WANTED hints moved the mouse"; FAILED=1; }
if [ "$EXPECTED1" = "$ACTUAL1" ]; then
    echo "  ok   every hint landed on its cell center: $(echo "$ACTUAL1" | tr '\n' ' ')"
else
    echo "  FAIL hint positions"
    echo "    expected: $(echo "$EXPECTED1" | tr '\n' ' ')"
    echo "    actual:   $(echo "$ACTUAL1" | tr '\n' ' ')"
    FAILED=1
fi

echo "== active screen: the cursor really moved to the screen the hint named =="
# One hint of the every-screen grid picks a screen, then the active-screen grid has to agree.
P2="4000 t 300 a 400 esc 300"
for key in a b c d; do P2="$P2 y 300 $key 400 esc 300"; done
run /tmp/geometry-p2.out "$P2"
FIRST=$(moves /tmp/geometry-p2.out | head -1)
REST=$(moves /tmp/geometry-p2.out | tail -n +2 | sort -u)
[ "$(echo "$REST" | wc -l | tr -d ' ')" = "4" ] && echo "  ok   all 4 hints of the active screen moved the mouse" \
    || { echo "  FAIL the active screen grid did not produce 4 positions"; FAILED=1; }
# Same side of the origin means the same screen, since the screens meet at x=0.
SIDE=$(echo "$FIRST" | awk -F, '{print ($1 < 0) ? "left" : "right"}')
WRONG=$(echo "$REST" | awk -F, -v side="$SIDE" \
        '{ if ((($1 < 0) ? "left" : "right") != side) print }' | wc -l | tr -d ' ')
[ "$WRONG" = "0" ] && echo "  ok   the active screen is the $SIDE one, where the hint put the cursor" \
    || { echo "  FAIL $WRONG of the 4 landed on another screen than $FIRST"; FAILED=1; }
# The active-screen grid covers one screen, so its cells are cells of the every-screen grid too.
MISSING=$(comm -23 <(echo "$REST") <(expected /tmp/geometry-p2.out | sort -u) | wc -l | tr -d ' ')
[ "$MISSING" = "0" ] && echo "  ok   each one is a cell center of that screen" \
    || { echo "  FAIL $MISSING positions are not cell centers"; FAILED=1; }

echo
[ $FAILED -eq 0 ] && echo "GEOMETRY PASSED" || echo "GEOMETRY FAILED"
exit $FAILED

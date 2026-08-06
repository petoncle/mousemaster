#!/bin/bash
# Asserts that the wheel scrolls the way its command is named. macOS takes a scroll delta as the
# movement of the content, so the axis 1 sign is the opposite of the direction the view goes.
#
#   QT_LIB=~/Qt/6.8.2/macos/lib ./macos/test/run-wheel.sh
#
# A scroll event goes to the window under the cursor rather than the focused one, so this runs
# headless. Finder on a folder of many items is the target, scrolled by a window-centered grid.
set -u
here=$(cd "$(dirname "$0")" && pwd)
CONFIG=$here/wheel.properties
cd "$here/../.." || exit 1
. "$here/mousemaster.sh"
FAILED=0

scrolled() {
    sudo "$JAVA" -cp "target/classes:target/test-classes:$(cat cp.txt)" \
        mousemaster.platform.macos.ScrollBarReader "$(pgrep -x Finder)" 2>/dev/null |
        sed -n 's/^scrolled=//p'
}

wheel() { # $1=key, $2=hold in milliseconds
    run_mousemaster /tmp/wheel.out "5000 t 400 space 800 +$1 ${2:-1200} -$1 1200"
    sleep 1
    assert_ran /tmp/wheel.out || exit 1
    assert_no_exception /tmp/wheel.out || exit 1
}

sudo killall java mousemaster 2>/dev/null
# Not killed first: a restarted Finder restores whatever windows it feels like, and the grid
# centers on whichever ends up focused. Opening the folder focuses that window and only that one.
open /System/Library/Frameworks
sleep 4
echo "  scrolling the window of Finder $(pgrep -x Finder)"
# Wound up to the top first: the window keeps whatever position it was left in, and the wheel down
# below measures nothing if it starts at the bottom.
wheel i 6000
BEFORE=$(scrolled)
wheel k
MIDDLE=$(scrolled)
wheel i
AFTER=$(scrolled)
echo "  scrolled: $BEFORE then $MIDDLE after wheel down then $AFTER after wheel up"

awk -v before="$BEFORE" -v middle="$MIDDLE" -v after="$AFTER" 'BEGIN {
    if (before == middle) { print "  FAIL nothing scrolled, so nothing was tested"; exit 1 }
    print "  ok   the window scrolled, so the check below means something"
    if (middle > before) print "  ok   wheel down scrolled down, from " before " to " middle
    else { print "  FAIL wheel down scrolled up, from " before " to " middle; bad = 1 }
    if (after < middle) print "  ok   wheel up scrolled up, from " middle " to " after
    else { print "  FAIL wheel up scrolled down, from " middle " to " after; bad = 1 }
    exit bad ? 1 : 0 }' || FAILED=1

echo
[ $FAILED -eq 0 ] && echo "WHEEL PASSED" || echo "WHEEL FAILED"
exit $FAILED

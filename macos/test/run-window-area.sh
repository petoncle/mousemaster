#!/bin/bash
# Asserts that a grid on the active window is inset and sized the way its properties say. Every
# check below is a difference between two centers, so none of them needs to know where the window is.
#
#   QT_LIB=~/Qt/6.8.2/macos/lib ./macos/test/run-window-area.sh
#
# Needs the karabiner driver activated, root, Input Monitoring, and Finder for a window to measure.
set -u
here=$(cd "$(dirname "$0")" && pwd)
CONFIG=$here/window-area.properties
OUT=/tmp/window-area.out
cd "$here/../.." || exit 1
. "$here/mousemaster.sh"
FAILED=0

# Not killed first: a restarted Finder restores whatever windows it feels like, and the grid
# centers on whichever ends up focused. Opening the folder focuses that window and only that one.
open /System/Library/Frameworks
sleep 4
SIMULATE="4000"
for key in t y i u o; do SIMULATE="$SIMULATE $key 300 space 400 s 400 esc 300"; done
run_mousemaster "$OUT" "$SIMULATE"
assert_ran "$OUT" || exit 1
assert_no_exception "$OUT" || exit 1

center() { # $1=history name
    grep -oE "Saved position \(-?[0-9.]+, -?[0-9.]+\) to $1-position-history\$" "$OUT" \
        | sed 's/Saved position (//;s/) to .*//;s/, /,/'
}
WINDOW=$(center window)
echo "  centers: window $WINDOW, left $(center left), top $(center top), \
half $(center half), screen $(center screen)"
awk -F, -v window="$WINDOW" -v left="$(center left)" -v top="$(center top)" \
    -v half="$(center half)" -v screen="$(center screen)" 'BEGIN {
    if (window == "" || left == "" || top == "" || half == "" || screen == "") {
        print "  FAIL a mode saved no position, so there is nothing to compare"; exit 1 }
    split(window, w, ","); split(left, l, ","); split(top, t, ",")
    split(half, h, ","); split(screen, s, ",")
    if (l[1] - w[1] == 50 && l[2] == w[2])
        print "  ok   a 100 left inset moved the center right by half of it"
    else { print "  FAIL the 100 left inset moved the center by " \
                 l[1] - w[1] "," l[2] - w[2]; bad = 1 }
    if (t[2] - w[2] == 40 && t[1] == w[1])
        print "  ok   an 80 top inset moved the center down by half of it"
    else { print "  FAIL the 80 top inset moved the center by " \
                 t[1] - w[1] "," t[2] - w[2]; bad = 1 }
    if (h[1] == w[1] && h[2] == w[2])
        print "  ok   halving the area kept the center where it was"
    else { print "  FAIL halving the area moved the center by " \
                 h[1] - w[1] "," h[2] - w[2]; bad = 1 }
    if (s[1] != w[1] || s[2] != w[2])
        print "  ok   the window center is not the screen center, so the window was found"
    else { print "  FAIL the window center is the screen center, so every check above passed " \
                 "on the screen that a missing window falls back to"; bad = 1 }
    exit bad ? 1 : 0 }' || FAILED=1

echo
[ $FAILED -eq 0 ] && echo "WINDOW AREA PASSED" || echo "WINDOW AREA FAILED"
exit $FAILED

#!/bin/bash
# Asserts that holding a zoom does not wedge the loop. Nothing in the zoom path logs, so the mode
# switch made after several seconds of zooming is the observable: a capture that blocks the main
# thread stops the loop, and that switch never arrives.
#
# What is not asserted is that a frame was captured at all, which nothing here can see: consent for
# screen recording is asked again per capture over ssh, and an unanswered prompt blocks every one.
#
#   QT_LIB=~/Qt/6.8.2/macos/lib ./macos/test/run-zoom.sh
#
# Needs the karabiner driver activated, root, Input Monitoring and Screen Recording.
set -u
here=$(cd "$(dirname "$0")" && pwd)
CONFIG=$here/zoom.properties
OUT=/tmp/zoom.out
cd "$here/../.." || exit 1
. "$here/mousemaster.sh"
FAILED=0

# Four seconds of zoom is far more frames than it used to take to deadlock.
run_mousemaster "$OUT" "4000 t 4000 y 600 esc 600"
assert_ran "$OUT" || exit 1
assert_no_exception "$OUT" || exit 1

modes() { grep -oE 'Switching to [a-z0-9-]+' "$OUT" | sed 's/Switching to //' | tr '\n' ' '; }
EXPECTED="idle-mode zoom-mode after-mode idle-mode "
ACTUAL=$(modes)
if [ "$EXPECTED" = "$ACTUAL" ]; then
    echo "  ok   the loop kept running through the zoom and left it"
else
    echo "  FAIL mode sequence"
    echo "    expected: $EXPECTED"
    echo "    actual:   $ACTUAL"
    FAILED=1
fi

echo
[ $FAILED -eq 0 ] && echo "ZOOM PASSED" || echo "ZOOM FAILED"
exit $FAILED

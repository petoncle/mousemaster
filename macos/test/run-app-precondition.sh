#!/bin/bash
# Asserts that an app precondition follows the frontmost application on macOS: the same key reaches
# a different mode once another application comes to the front.
#
#   QT_LIB=~/Qt/6.8.2/macos/lib ./macos/test/run-app-precondition.sh
#
# Needs the karabiner driver activated, root, and Input Monitoring.
set -u
here=$(cd "$(dirname "$0")" && pwd)
CONFIG=$here/app-precondition.properties
OUT=/tmp/app-precondition.out
cd "$here/../.." || exit 1
. "$here/mousemaster.sh"
FAILED=0

stop_mousemaster
open -a Finder
sleep 3
start_mousemaster "$OUT" "4000 t 400 esc 400 12000 t 400 esc 1000"
# Brought to the front only once the first mode switch is in the log, so each press sees one app.
wait_for "$OUT" "Switching to finder-mode"
open -a "System Settings"
wait_for "$OUT" "Finished simulating keys"
sleep 1
stop_mousemaster
killall "System Settings" 2>/dev/null
assert_ran "$OUT" || exit 1
assert_no_exception "$OUT" || exit 1

grep -q "Detected active app change from Finder to System Settings" "$OUT" \
    && echo "  ok   the frontmost application change was detected" \
    || { echo "  FAIL the application change was never detected"; FAILED=1; }
MODES=$(grep -oE 'Switching to [a-z0-9-]+' "$OUT" | sed 's/Switching to //' | tr '\n' ' ')
EXPECTED="idle-mode finder-mode idle-mode settings-mode idle-mode "
if [ "$MODES" = "$EXPECTED" ]; then
    echo "  ok   the same key reached the mode of whichever application was frontmost"
else
    echo "  FAIL mode sequence"
    echo "    expected: $EXPECTED"
    echo "    actual:   $MODES"
    FAILED=1
fi

echo
[ $FAILED -eq 0 ] && echo "APP PRECONDITION PASSED" || echo "APP PRECONDITION FAILED"
exit $FAILED

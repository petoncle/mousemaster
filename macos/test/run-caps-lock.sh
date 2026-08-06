#!/bin/bash
# Asserts that the caps lock usage is turned into a state change. macOS sends the led report to the
# device a key came from, so sending the usage through the virtual keyboard, which has no led,
# leaves both the led and the state alone.
#
#   QT_LIB=~/Qt/6.8.2/macos/lib ./macos/test/run-caps-lock.sh
#
# Needs the karabiner driver activated, root, and Input Monitoring. What the state ends up as is
# not asserted: a keyboard shared over a kvm re-syncs it, so the state read back a second later is
# whichever won. Reaching the IOKit call and having it succeed is the part that stays put, and it
# is also the part a native image breaks when the proxy is not registered as reachable.
set -u
here=$(cd "$(dirname "$0")" && pwd)
CONFIG=$here/caps-lock.properties
OUT=/tmp/caps-lock.out
cd "$here/../.." || exit 1
. "$here/mousemaster.sh"
FAILED=0

run_mousemaster "$OUT" "4000 f1 1000 f1 1000"
assert_ran "$OUT" || exit 1
assert_no_exception "$OUT" || exit 1
[ "$(grep -c 'Executing macro' "$OUT")" = "2" ] && echo "  ok   the macro ran twice" \
    || { echo "  FAIL the macro did not run twice"; FAILED=1; }
# Logged once per press, and only once the state has been read back.
[ "$(grep -c 'Turning caps lock' "$OUT")" = "2" ] \
    && echo "  ok   both presses read the state and set it" \
    || { echo "  FAIL the state was set $(grep -c 'Turning caps lock' "$OUT") times, expected 2";
         FAILED=1; }
grep -q 'Unable to set the caps lock state' "$OUT" \
    && { echo "  FAIL IOHIDSetModifierLockState failed"; FAILED=1; } \
    || echo "  ok   IOHIDSetModifierLockState succeeded"

echo
[ $FAILED -eq 0 ] && echo "CAPS LOCK PASSED" || echo "CAPS LOCK FAILED"
exit $FAILED

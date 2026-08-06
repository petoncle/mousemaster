#!/bin/bash
# Asserts that ui hints find the elements of a real window through the Accessibility API. The
# painted box count is the observable: one box per element, so finding none paints none.
#
#   QT_LIB=~/Qt/6.8.2/macos/lib ./macos/test/run-ui-hint.sh
#
# Needs the karabiner driver activated, root, Input Monitoring and Accessibility. Finder on a
# folder of many items is the window the elements are looked for in.
set -u
here=$(cd "$(dirname "$0")" && pwd)
CONFIG=$here/ui-hint.properties
OUT=/tmp/ui-hint.out
cd "$here/../.." || exit 1
. "$here/mousemaster.sh"
FAILED=0

boxes() { grep -oE 'hint layer of [0-9]+ boxes' "$OUT" | grep -oE '[0-9]+' | sort -rn | head -1; }

check() { # $1=area name
    COUNT=$(boxes)
    # A window always carries more than a handful: without the permission there would be none.
    if [ -n "$COUNT" ] && [ "$COUNT" -ge 5 ]; then
        echo "  ok   $1 found $COUNT elements"
    else
        echo "  FAIL $1 found ${COUNT:-no} elements"
        FAILED=1
    fi
}

stop_mousemaster
# Finder is not killed first: a restarted one restores whatever windows it feels like, which the
# suites after this one then have to pick from. Opening the folder focuses that window alone.
open /System/Library/Frameworks
sleep 3

echo "== the active screen =="
run_mousemaster "$OUT" "4000 t 2500 esc 800"
assert_ran "$OUT" || exit 1
assert_no_exception "$OUT" || exit 1
check "the active screen"

echo "== the active window =="
run_mousemaster "$OUT" "4000 y 2500 esc 800"
assert_ran "$OUT" || exit 1
assert_no_exception "$OUT" || exit 1
check "the active window"

echo
[ $FAILED -eq 0 ] && echo "UI HINT PASSED" || echo "UI HINT FAILED"
exit $FAILED

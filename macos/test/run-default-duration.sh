#!/bin/bash
# The global default duration applies to moves without a suffix, so the same combo matches when its
# presses are close together and not when they are far apart.
set -u
here=$(cd "$(dirname "$0")" && pwd)
CONFIG=$here/default-duration-suite.properties
cd "$here/../.." || exit 1
. "$here/mousemaster.sh"
FAILED=0

run() { # $1=output file, $2=simulation key events
    run_mousemaster "$1" "$2"
    assert_ran "$1" || FAILED=1
    assert_no_exception "$1" || FAILED=1
}

modes() { grep -oE 'Switching to [a-z0-9-]+' "$1" | sed 's/Switching to //' | tr '\n' ' '; }

echo "== the global default duration applies to unsuffixed moves =="
run /tmp/default-duration.out "3000 t 300 +a 100 +b 150 -a 60 -b 350 z 300 +a 500 +b 150 -a 60 -b 300"
EXPECTED="idle-mode hub-mode fast-mode hub-mode "
ACTUAL=$(modes /tmp/default-duration.out)
if [ "$EXPECTED" = "$ACTUAL" ]; then
    echo "  ok   matched inside 250ms, and not after 500ms"
else
    echo "  FAIL mode sequence"; echo "    expected: $EXPECTED"; echo "    actual:   $ACTUAL"; FAILED=1
fi

echo
[ $FAILED -eq 0 ] && echo "DEFAULT DURATION PASSED" || echo "DEFAULT DURATION FAILED"
exit $FAILED

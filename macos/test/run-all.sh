#!/bin/bash
# Runs every macOS suite and prints one line per suite, so a whole parity check is one command.
#
#   QT_LIB=~/Qt/6.8.2/macos/lib ./macos/test/run-all.sh
#
# BINARY=target/mousemaster.app/Contents/MacOS/mousemaster runs every suite against the built app,
# which is what ships and resolves the Objective-C classes at a different time.
#
# Running the classes needs a cp.txt at the root, holding the dependency classpath:
#
#   ./mvnw dependency:build-classpath -Dmdep.outputFile=cp.txt
#
# Write it as the user who owns the maven repository the paths point at, not as root: the startup
# suite launches mousemaster as a plain user, which cannot read another user's repository.
#
# Needs the karabiner driver activated, root, and Input Monitoring. The zoom suite also needs
# Screen Recording, and the wheel and window area suites need Finder. The whole set needs an
# unlocked console: a locked screen releases the keyboard, which each suite aborts on rather than
# passing vacuously.
set -u
here=$(cd "$(dirname "$0")" && pwd)
FAILED=0

# Wheel first: it scrolls whichever window is active, and the suites below leave Finder with more
# than one open. Then the cheapest, so a broken setup shows up before the long ones.
for suite in wheel startup caps-lock reload zoom app-precondition ui-hint mouse-move \
             hint-geometry grid-geometry window-area wheel-axis default-duration \
             combo-suite; do
    echo "== $suite =="
    if "$here/run-$suite.sh"; then
        RESULTS="${RESULTS:-}  $suite ok"$'\n'
    else
        RESULTS="${RESULTS:-}  $suite FAILED"$'\n'
        FAILED=1
    fi
done

echo
echo "summary"
printf '%s' "$RESULTS"
[ $FAILED -eq 0 ] && echo "ALL PASSED" || echo "SOME FAILED"
exit $FAILED

#!/bin/bash
# Asserts that editing the configuration file reloads it. The java watch service has no native
# backend on macOS, so it polls: the edit below is noticed about a second later.
#
#   QT_LIB=~/Qt/6.8.2/macos/lib ./macos/test/run-reload.sh
#
# The appended mode is reached by a key the original configuration does not bind, so the switch is
# proof the new file was read rather than just noticed.
set -u
here=$(cd "$(dirname "$0")" && pwd)
CONFIG=/tmp/mousemaster-reload.properties
OUT=/tmp/reload.out
cd "$here/../.." || exit 1
. "$here/mousemaster.sh"
FAILED=0

# Aborted rather than run on whatever is already there, which would be another run's configuration.
cp "$here/reload.properties" "$CONFIG" || exit 1
start_mousemaster "$OUT" "3000 t 400 esc 400 12000 y 400 esc 400 12000 t 400 esc 1000"
# Appending only once the first mode switch is in the log keeps the edit inside the run.
wait_for "$OUT" "Switching to a-mode"
# Appended rather than rewritten: an atomic rename is not an ENTRY_MODIFY, so it is never noticed.
printf 'idle-mode.to.b-mode=+y\nb-mode.to.idle-mode=+esc\n' >> "$CONFIG"
# Then a line that does not parse, which must leave the configuration already in force alone.
wait_for "$OUT" "Switching to b-mode"
printf 'idle-mode.to.c-mode=+notakey\n' >> "$CONFIG"
wait_for "$OUT" "Finished simulating keys"
sleep 1
stop_mousemaster
assert_ran "$OUT" || exit 1
grep -q "Switching to a-mode" "$OUT" && echo "  ok   the original configuration was in force" \
    || { echo "  FAIL the original configuration never worked"; FAILED=1; }
grep -q "has changed" "$OUT" && echo "  ok   the edit was noticed" \
    || { echo "  FAIL the edit was never noticed"; FAILED=1; }
grep -q "Switching to b-mode" "$OUT" && echo "  ok   the appended mode is reachable, so the file was re-read" \
    || { echo "  FAIL the appended mode was never reached"; FAILED=1; }
grep -q "Unable to load configuration file" "$OUT" \
    && echo "  ok   the line that does not parse was rejected" \
    || { echo "  FAIL the unparseable line was accepted"; FAILED=1; }
# a-mode is switched to twice, once before either edit and once after the bad one.
[ "$(grep -c 'Switching to a-mode' "$OUT")" = "2" ] \
    && echo "  ok   the configuration in force survived the bad edit" \
    || { echo "  FAIL a-mode was reached $(grep -c 'Switching to a-mode' "$OUT") times, expected 2"; FAILED=1; }

echo
[ $FAILED -eq 0 ] && echo "RELOAD PASSED" || echo "RELOAD FAILED"
exit $FAILED

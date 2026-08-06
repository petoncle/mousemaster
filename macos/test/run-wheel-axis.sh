#!/bin/bash
# Asserts that each wheel direction fills the axis it should, with the sign it should. run-wheel.sh
# checks the vertical axis by its effect on a window; nothing reachable over ssh scrolls
# horizontally, so the horizontal axis is read from the posted event instead.
#
#   QT_LIB=~/Qt/6.8.2/macos/lib ./macos/test/run-wheel-axis.sh
#
# Needs the karabiner driver activated, root, Input Monitoring and Accessibility.
set -u
here=$(cd "$(dirname "$0")" && pwd)
CONFIG=$here/wheel-axis.properties
OUT=/tmp/wheel-axis.out
cd "$here/../.." || exit 1
. "$here/mousemaster.sh"
FAILED=0

# One run per direction, so an event needs no attributing: every event in a capture is that
# direction's. macOS takes a scroll delta as the movement of the content, so up and left are
# positive and the pair run-wheel.sh already proved by its effect anchors the other pair.
capture() { # $1=key, $2=direction
    rm -f /tmp/scroll-events.txt
    # The reader is started after mousemaster and stopped by name: both are java, so the killall
    # that starts and ends a run would take the reader with it.
    start_mousemaster "$OUT" "8000 t 300 +$1 800 -$1 400 esc 300"
    wait_for "$OUT" "Switching to idle-mode"
    sudo "$JAVA" -cp "target/classes:target/test-classes:$(cat cp.txt)" \
        mousemaster.platform.macos.ScrollEventReader 30 > /tmp/scroll-events.txt 2>&1 &
    disown
    for _ in $(seq 1 50); do grep -q "tap ready\|no tap" /tmp/scroll-events.txt 2>/dev/null && break; sleep 0.2; done
    if grep -q "no tap" /tmp/scroll-events.txt; then
        echo "  ABORT: the event tap was refused, so nothing can be read"
        sudo pkill -f ScrollEventReader; stop_mousemaster; return 1
    fi
    wait_for "$OUT" "Finished simulating keys"
    sleep 1
    sudo pkill -f ScrollEventReader 2>/dev/null
    stop_mousemaster
    assert_ran "$OUT" || return 1
    awk -v direction="$2" '
        /^scroll / {
            for (i = 2; i <= NF; i++) { split($i, kv, "="); v[kv[1]] = kv[2] + 0 }
            count++
            if (v["pointAxis1"] != 0) vertical++
            if (v["pointAxis2"] != 0) horizontal++
            if (v["pointAxis1"] < 0) up_neg++;  if (v["pointAxis1"] > 0) up_pos++
            if (v["pointAxis2"] < 0) left_neg++; if (v["pointAxis2"] > 0) left_pos++
        }
        END {
            if (count == 0) { print "  FAIL " direction ": no scroll event was posted"; exit 1 }
            printf "  %-5s %d events, vertical axis on %d, horizontal axis on %d\n",
                   direction, count, vertical, horizontal
            wantVertical = (direction == "down" || direction == "up")
            if (wantVertical && horizontal > 0) {
                print "  FAIL " direction " also filled the horizontal axis"; bad = 1 }
            if (!wantVertical && vertical > 0) {
                print "  FAIL " direction " also filled the vertical axis"; bad = 1 }
            if (direction == "down" && (up_neg == 0 || up_pos > 0)) {
                print "  FAIL down is not a negative axis 1"; bad = 1 }
            if (direction == "up" && (up_pos == 0 || up_neg > 0)) {
                print "  FAIL up is not a positive axis 1"; bad = 1 }
            if (direction == "right" && (left_neg == 0 || left_pos > 0)) {
                print "  FAIL right is not a negative axis 2"; bad = 1 }
            if (direction == "left" && (left_pos == 0 || left_neg > 0)) {
                print "  FAIL left is not a positive axis 2"; bad = 1 }
            exit bad ? 1 : 0 }' /tmp/scroll-events.txt || return 1
}

for pair in k:down i:up l:right j:left; do
    capture "${pair%:*}" "${pair#*:}" || FAILED=1
done

echo
[ $FAILED -eq 0 ] && echo "WHEEL AXIS PASSED" || echo "WHEEL AXIS FAILED"
exit $FAILED

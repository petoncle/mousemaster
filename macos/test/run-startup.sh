#!/bin/bash
# Asserts what mousemaster refuses to start on. These checks run before it grabs anything, so no
# other suite ever reaches them.
#
#   QT_LIB=~/Qt/6.8.2/macos/lib ./macos/test/run-startup.sh
#
# Needs the karabiner driver activated, root, and Input Monitoring. A second user is needed too:
# whoever owns /dev/console, which is who the not-root case runs as.
set -u
here=$(cd "$(dirname "$0")" && pwd)
CONFIG=$here/caps-lock.properties
cd "$here/../.." || exit 1
. "$here/mousemaster.sh"
FAILED=0
CONSOLE_USER=$(stat -f %Su /dev/console)

# Launches without stopping what is already running, which is the point of two of the cases.
launch_as() { # $1=output file, $2=user, rest=extra arguments
    local out=$1 user=$2
    shift 2
    rm -f "$out"
    if [ -n "$BINARY" ]; then
        sudo -u "$user" "$BINARY" --configuration-file="$CONFIG" "$@" \
            --log-level=debug --pause-on-error=false > "$out" 2>&1 &
    else
        sudo -u "$user" -E env QT_ENABLE_HIGHDPI_SCALING=0 "$JAVA" -XstartOnFirstThread \
            -Djava.library.path="$QT_LIB" \
            -cp "target/classes:target/test-classes:$(cat cp.txt)" \
            mousemaster.platform.macos.MacosMain \
            --configuration-file="$CONFIG" "$@" \
            --log-level=debug --pause-on-error=false > "$out" 2>&1 &
    fi
}

refused() { # $1=output file, $2=message
    grep -q "$2" "$1"
}

echo "== not run as root =="
stop_mousemaster
sleep 1
launch_as /tmp/startup-user.out "$CONSOLE_USER"
sleep 10
stop_mousemaster
if refused /tmp/startup-user.out "must run as root"; then
    echo "  ok   refused to run as $CONSOLE_USER"
else
    echo "  FAIL started as $CONSOLE_USER, or failed for another reason:"
    head -3 /tmp/startup-user.out | sed 's/^/    /'
    FAILED=1
fi

echo "== a second instance =="
stop_mousemaster
sleep 1
launch_as /tmp/startup-first.out root --simulation-key-events="30000"
wait_for /tmp/startup-first.out "Switching to idle-mode"
launch_as /tmp/startup-second.out root
sleep 8
if refused /tmp/startup-second.out "Another instance is already running"; then
    echo "  ok   the second instance refused to start"
else
    echo "  FAIL the second instance did not refuse"
    head -3 /tmp/startup-second.out | sed 's/^/    /'
    FAILED=1
fi

echo "== a second instance that is allowed =="
launch_as /tmp/startup-allowed.out root --multiple-instances-allowed=true
sleep 8
stop_mousemaster
if refused /tmp/startup-allowed.out "Another instance is already running"; then
    echo "  FAIL the lock was taken even though multiple instances are allowed"
    FAILED=1
else
    echo "  ok   the lock was not taken"
fi

echo
[ $FAILED -eq 0 ] && echo "STARTUP PASSED" || echo "STARTUP FAILED"
exit $FAILED

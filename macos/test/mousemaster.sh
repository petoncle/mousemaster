# Sourced by every suite. Runs mousemaster on $CONFIG with a simulation script, from BINARY if that
# is set so a suite can run against the built app, which resolves the Objective-C classes at a
# different time than the classes do.
QT_LIB=${QT_LIB:-$HOME/Qt/6.8.2/macos/lib}
JAVA=${JAVA_HOME:-$HOME/tools/jdk25/Contents/Home}/bin/java
BINARY=${BINARY:-}

# Terminated rather than killed, so the shutdown hook releases the keyboard: grabbing it again
# while a previous instance still holds it leaves the next run receiving nothing.
stop_mousemaster() {
    sudo killall java mousemaster 2>/dev/null
    for _ in $(seq 1 40); do
        pgrep -f 'MacosMain|mousemaster.app' > /dev/null || break
        sleep 0.2
    done
    sudo killall -9 java mousemaster 2>/dev/null
}

start_mousemaster() { # $1=output file, $2=simulation key events
    stop_mousemaster
    sleep 2
    # A file that survives is a previous run's, left by a run under another user, and every check
    # would read it and pass.
    rm -f "$1"
    if [ -e "$1" ]; then echo "  ABORT: $1 is left over and cannot be removed"; exit 1; fi
    if [ -n "$BINARY" ]; then
        sudo "$BINARY" --configuration-file="$CONFIG" --simulation-key-events="$2" \
            --log-level=debug --pause-on-error=false > "$1" 2>&1 &
    else
        sudo -E env QT_ENABLE_HIGHDPI_SCALING=0 "$JAVA" -XstartOnFirstThread \
            --enable-native-access=ALL-UNNAMED \
            -Djava.library.path="$QT_LIB" \
            -cp "target/classes:target/test-classes:$(cat cp.txt)" \
            mousemaster.platform.macos.MacosMain \
            --configuration-file="$CONFIG" --simulation-key-events="$2" \
            --log-level=debug --pause-on-error=false > "$1" 2>&1 &
    fi
}

wait_for() { # $1=output file, $2=text
    for _ in $(seq 1 900); do
        grep -q "$2" "$1" 2>/dev/null && break
        sleep 0.1
    done
}

run_mousemaster() { # $1=output file, $2=simulation key events
    start_mousemaster "$1" "$2"
    wait_for "$1" "Finished simulating keys"
    sleep 1
    stop_mousemaster
}

# Without this, every check that asserts nothing happened passes on a run that never happened.
assert_ran() { # $1=output file
    if [ "$(grep -c 'Finished simulating keys' "$1")" = "0" ]; then
        echo "  ABORT: the run never finished simulating keys, so every check below is meaningless"
        return 1
    fi
    if [ "$(grep -c 'Releasing the keyboard' "$1")" != "0" ]; then
        echo "  ABORT: the keyboard was released, so nothing was tested (screen locked?)"
        return 1
    fi
}

# Errors and segfaults are matched too, not just exceptions: a native image reports a missing
# registration as a MissingReflectionRegistrationError and a crash as a segfault, and neither of
# those words is exception.
assert_no_exception() { # $1=output file
    if [ "$(grep -icE 'exception|Caused by|Error\b|Segfault' "$1")" != "0" ]; then
        echo "  FAIL: exception in the run"
        grep -iE 'exception|Caused by|Error\b|Segfault' "$1" | head -3
        return 1
    fi
}

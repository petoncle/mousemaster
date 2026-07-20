package mousemaster.platform.linux;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import mousemaster.Key;
import mousemaster.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Reads raw keyboard input from /dev/input/eventX via evdev.
 * Uses EVIOCGRAB for exclusive capture so events don't also reach X11.
 * Events are queued and consumed on the main thread via pollEvent().
 */
public class LinuxEvdev {

    private static final Logger logger = LoggerFactory.getLogger(LinuxEvdev.class);

    // _IOW('E', 0x90, int) = (1<<30)|(0x45<<8)|0x90|(4<<16)
    private static final long EVIOCGRAB = 0x40044590L;
    // _IOC(_IOC_READ=2, 'E', 0x06, 256) = (2<<30)|(0x45<<8)|0x06|(256<<16)
    private static final long EVIOCGNAME_256 = 0x81004506L;

    private static final int INPUT_EVENT_SIZE = 24; // timeval(16) + type(2) + code(2) + value(4)
    private static final short EV_KEY = 1;
    private static final int KEY_RELEASE = 0;
    private static final int KEY_PRESS   = 1;
    // KEY_REPEAT = 2, ignored

    private static final int O_RDONLY = 0;

    // linux/input-event-codes.h KEY_Q. Devices that report this are real typing
    // keyboards; devices that merely have a "kbd" handler but not this bit are things
    // like ACPI power/lid/brightness button arrays, which must never be grabbed
    // exclusively (doing so silently swallows e.g. the power button).
    private static final int KEY_Q = 16;

    interface CLib extends Library {
        CLib INSTANCE = Native.load("c", CLib.class);
        int open(String path, int flags);
        int close(int fd);
        NativeLong read(int fd, Pointer buf, NativeLong count);
        int ioctl(int fd, NativeLong request, int arg);
        int ioctl(int fd, NativeLong request, Pointer buf);
    }

    private final ConcurrentLinkedQueue<KeyEvent> eventQueue = new ConcurrentLinkedQueue<>();
    private final List<Integer> openFds = new ArrayList<>();
    private final int uinputFd;
    private volatile boolean running = true;

    public LinuxEvdev(int uinputFd) {
        this.uinputFd = uinputFd;
        List<String> devices = findKeyboardDevices();
        if (devices.isEmpty()) {
            logger.warn("No keyboard devices found — run as root or add yourself to the 'input' group");
            return;
        }
        for (String path : devices) {
            openAndGrab(path);
        }
    }

    private List<String> findKeyboardDevices() {
        List<String> result = new ArrayList<>();
        try {
            String content = Files.readString(Path.of("/proc/bus/input/devices"));
            boolean hasKbd = false;
            boolean isVirtual = false;
            boolean hasLetterKeys = false;
            String eventNode = null;
            for (String line : content.lines().toList()) {
                if (line.isBlank()) {
                    if (hasKbd && !isVirtual && hasLetterKeys && eventNode != null)
                        result.add("/dev/input/" + eventNode);
                    hasKbd = false;
                    isVirtual = false;
                    hasLetterKeys = false;
                    eventNode = null;
                } else if (line.startsWith("S: Sysfs=")) {
                    // uinput-created devices live under /devices/virtual/
                    isVirtual = line.contains("/devices/virtual/");
                } else if (line.startsWith("H: Handlers=")) {
                    String handlers = line.substring("H: Handlers=".length());
                    hasKbd = handlers.contains("kbd");
                    for (String token : handlers.split("\\s+")) {
                        if (token.startsWith("event"))
                            eventNode = token;
                    }
                } else if (line.startsWith("B: KEY=")) {
                    hasLetterKeys = hasKeyBit(line.substring("B: KEY=".length()), KEY_Q);
                }
            }
            // Handle last block if file doesn't end with blank line
            if (hasKbd && !isVirtual && hasLetterKeys && eventNode != null)
                result.add("/dev/input/" + eventNode);
        } catch (IOException e) {
            logger.error("Cannot read /proc/bus/input/devices: {}", e.getMessage());
        }
        logger.info("Found {} keyboard device(s): {}", result.size(), result);
        return result;
    }

    /**
     * The kernel prints the KEY= capability bitmask as space-separated hex words,
     * most-significant word first. The last (rightmost) word covers bits 0-63, which is
     * all we need to test for a representative letter key like KEY_Q.
     */
    private static boolean hasKeyBit(String bitmaskLine, int bit) {
        String[] tokens = bitmaskLine.trim().split("\\s+");
        if (tokens.length == 0)
            return false;
        try {
            long lastWord = Long.parseUnsignedLong(tokens[tokens.length - 1], 16);
            return ((lastWord >> bit) & 1L) != 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void openAndGrab(String path) {
        int fd = CLib.INSTANCE.open(path, O_RDONLY);
        if (fd < 0) {
            logger.warn("Cannot open {} (errno={}) — skipping", path, Native.getLastError());
            return;
        }

        Memory nameBuf = new Memory(256);
        nameBuf.clear();
        String name = path;
        if (CLib.INSTANCE.ioctl(fd, new NativeLong(EVIOCGNAME_256), nameBuf) >= 0)
            name = nameBuf.getString(0);

        if (LibUinput.DEVICE_NAME.equals(name) || LibUinput.KEYBOARD_DEVICE_NAME.equals(name)) {
            CLib.INSTANCE.close(fd);
            return;
        }

        int grabResult = CLib.INSTANCE.ioctl(fd, new NativeLong(EVIOCGRAB), 1);
        if (grabResult < 0) {
            logger.error("EVIOCGRAB failed for {} '{}' (errno={}). Run as root.",
                    path, name, Native.getLastError());
            CLib.INSTANCE.close(fd);
            return;
        }

        logger.info("Grabbed keyboard '{}' ({})", name, path);
        openFds.add(fd);

        final String deviceName = name;
        Thread t = new Thread(() -> readLoop(fd, deviceName), "evdev-" + deviceName);
        t.setDaemon(true);
        t.start();
    }

    private void readLoop(int fd, String deviceName) {
        Memory buf = new Memory(INPUT_EVENT_SIZE);
        while (running) {
            buf.clear();
            long n = CLib.INSTANCE.read(fd, buf, new NativeLong(INPUT_EVENT_SIZE)).longValue();
            if (n != INPUT_EVENT_SIZE) {
                if (running)
                    logger.warn("evdev read returned {} for '{}', stopping", n, deviceName);
                break;
            }

            short type  = buf.getShort(16);
            short code  = buf.getShort(18);
            int   value = buf.getInt(20);

            if (type == EV_KEY && (value == KEY_PRESS || value == KEY_RELEASE)) {
                int keycode = code & 0xFFFF;
                Key key = LinuxVirtualKey.fromEvdevCode(keycode);
                if (key != null) {
                    Instant now = Instant.now();
                    eventQueue.add(value == KEY_PRESS
                            ? new KeyEvent.PressKeyEvent(now, key)
                            : new KeyEvent.ReleaseKeyEvent(now, key));
                } else {
                    // No Key enum equivalent, so the combo engine can never act on this
                    // code - regurgitate it as-is instead of silently dropping it, since
                    // this device is exclusively grabbed and nothing else will ever see
                    // the event otherwise (e.g. KEY_POWER on an ACPI button-array device).
                    logger.debug("No Key mapping for evdev code {} on '{}', regurgitating raw",
                            keycode, deviceName);
                    LibUinput.writeInputEvent(uinputFd, LibUinput.EV_KEY, keycode, value);
                    LibUinput.writeInputEvent(uinputFd, LibUinput.EV_SYN, LibUinput.SYN_REPORT, 0);
                }
            }
        }
    }

    /** Called from the main thread in pumpEvents(). */
    public KeyEvent pollEvent() {
        return eventQueue.poll();
    }

    public void destroy() {
        running = false;
        for (int fd : openFds) {
            CLib.INSTANCE.ioctl(fd, new NativeLong(EVIOCGRAB), 0);
            CLib.INSTANCE.close(fd); // causes blocking read() to return -1, ending the thread
        }
        openFds.clear();
    }
}

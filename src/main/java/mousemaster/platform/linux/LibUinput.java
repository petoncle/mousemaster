package mousemaster.platform.linux;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Helper for Linux uinput virtual input device.
 * Provides mouse button clicks and scroll wheel injection that works on
 * both X11 and native Wayland (unlike XTest which only reaches X11 clients).
 */
public class LibUinput {

    private static final Logger logger = LoggerFactory.getLogger(LibUinput.class);

    // ioctl numbers for uinput on x86_64 Linux
    // Computed via _IOW('U', nr, int) and _IO('U', nr)
    static final long UI_SET_EVBIT  = 0x40045564L; // _IOW('U', 100, int)
    static final long UI_SET_KEYBIT = 0x40045565L; // _IOW('U', 101, int)
    static final long UI_SET_RELBIT = 0x40045566L; // _IOW('U', 102, int)
    static final long UI_DEV_CREATE  = 0x5501L;    // _IO('U', 1)
    static final long UI_DEV_DESTROY = 0x5502L;    // _IO('U', 2)

    // Event types (linux/input-event-codes.h)
    static final int EV_SYN = 0;
    static final int EV_KEY = 1;
    static final int EV_REL = 2;

    static final int SYN_REPORT = 0;

    // Mouse button codes
    static final int BTN_LEFT   = 0x110;
    static final int BTN_RIGHT  = 0x111;
    static final int BTN_MIDDLE = 0x112;

    // Relative axis codes
    static final int REL_X      = 0;
    static final int REL_Y      = 1;
    static final int REL_HWHEEL = 6;
    static final int REL_WHEEL  = 8;

    static final short BUS_VIRTUAL = 0x06;

    // open() flags on Linux x86_64
    static final int O_WRONLY   = 1;
    static final int O_NONBLOCK = 0x800;

    // sizeof(uinput_user_dev): 80 (name) + 8 (input_id) + 4 (ff_effects_max) + 4*256 (abs arrays) = 1116
    private static final int UINPUT_USER_DEV_SIZE = 1116;

    // sizeof(input_event) on x86_64: 16 (timeval) + 2 (type) + 2 (code) + 4 (value) = 24
    static final int INPUT_EVENT_SIZE = 24;

    static final String UINPUT_PATH = "/dev/uinput";
    // Distinct names let the evdev read-loop filter out these virtual devices
    static final String DEVICE_NAME = "mousemaster-mouse";
    static final String KEYBOARD_DEVICE_NAME = "mousemaster-kb";

    interface CLib extends Library {
        CLib INSTANCE = Native.load("c", CLib.class);

        int open(String path, int flags);
        int close(int fd);
        NativeLong write(int fd, Pointer buf, NativeLong count);
        int ioctl(int fd, NativeLong request, int arg);
    }

    static int createKeyboardDevice() {
        int fd = CLib.INSTANCE.open(UINPUT_PATH, O_WRONLY | O_NONBLOCK);
        if (fd < 0) {
            logger.error("Cannot open {} for keyboard device (errno={})", UINPUT_PATH, Native.getLastError());
            return -1;
        }

        requireIoctl(fd, UI_SET_EVBIT, EV_KEY, "UI_SET_EVBIT(EV_KEY)");
        requireIoctl(fd, UI_SET_EVBIT, EV_SYN, "UI_SET_EVBIT(EV_SYN)");
        // Enable all standard key codes (1–255 covers the full QWERTY + function + numpad range)
        for (int code = 1; code <= 255; code++)
            CLib.INSTANCE.ioctl(fd, new NativeLong(UI_SET_KEYBIT), code);

        Memory userDev = new Memory(UINPUT_USER_DEV_SIZE);
        userDev.clear();
        byte[] nameBytes = KEYBOARD_DEVICE_NAME.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < nameBytes.length && i < 79; i++)
            userDev.setByte(i, nameBytes[i]);
        userDev.setShort(80, BUS_VIRTUAL);
        NativeLong written = CLib.INSTANCE.write(fd, userDev, new NativeLong(UINPUT_USER_DEV_SIZE));
        if (written.longValue() != UINPUT_USER_DEV_SIZE) {
            logger.error("uinput_user_dev write failed for keyboard device: wrote {} of {} bytes (errno={})",
                    written.longValue(), UINPUT_USER_DEV_SIZE, Native.getLastError());
            CLib.INSTANCE.close(fd);
            return -1;
        }

        int result = CLib.INSTANCE.ioctl(fd, new NativeLong(UI_DEV_CREATE), 0);
        if (result < 0) {
            logger.error("UI_DEV_CREATE failed for keyboard device (errno={})", Native.getLastError());
            CLib.INSTANCE.close(fd);
            return -1;
        }

        logger.info("uinput keyboard device '{}' created (fd={})", KEYBOARD_DEVICE_NAME, fd);
        return fd;
    }

    static int createMouseDevice() {
        int fd = CLib.INSTANCE.open(UINPUT_PATH, O_WRONLY | O_NONBLOCK);
        if (fd < 0) {
            logger.error("Cannot open {} (errno likely EACCES or ENOENT).\n" +
                         "Add yourself to the 'uinput' group:\n" +
                         "  sudo usermod -aG uinput $USER  (then log out and back in)",
                         UINPUT_PATH);
            return -1;
        }

        requireIoctl(fd, UI_SET_EVBIT,  EV_KEY,    "UI_SET_EVBIT(EV_KEY)");
        requireIoctl(fd, UI_SET_EVBIT,  EV_SYN,    "UI_SET_EVBIT(EV_SYN)");
        requireIoctl(fd, UI_SET_EVBIT,  EV_REL,    "UI_SET_EVBIT(EV_REL)");
        requireIoctl(fd, UI_SET_KEYBIT, BTN_LEFT,  "UI_SET_KEYBIT(BTN_LEFT)");
        requireIoctl(fd, UI_SET_KEYBIT, BTN_RIGHT, "UI_SET_KEYBIT(BTN_RIGHT)");
        requireIoctl(fd, UI_SET_KEYBIT, BTN_MIDDLE,"UI_SET_KEYBIT(BTN_MIDDLE)");
        requireIoctl(fd, UI_SET_RELBIT, REL_X,     "UI_SET_RELBIT(REL_X)");
        requireIoctl(fd, UI_SET_RELBIT, REL_Y,     "UI_SET_RELBIT(REL_Y)");
        requireIoctl(fd, UI_SET_RELBIT, REL_WHEEL, "UI_SET_RELBIT(REL_WHEEL)");
        requireIoctl(fd, UI_SET_RELBIT, REL_HWHEEL,"UI_SET_RELBIT(REL_HWHEEL)");

        // Write uinput_user_dev to configure device name and bus type
        Memory userDev = new Memory(UINPUT_USER_DEV_SIZE);
        userDev.clear();
        byte[] nameBytes = DEVICE_NAME.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < nameBytes.length && i < 79; i++) {
            userDev.setByte(i, nameBytes[i]);
        }
        userDev.setShort(80, BUS_VIRTUAL); // input_id.bustype at offset 80
        NativeLong written = CLib.INSTANCE.write(fd, userDev, new NativeLong(UINPUT_USER_DEV_SIZE));
        if (written.longValue() != UINPUT_USER_DEV_SIZE) {
            logger.error("uinput_user_dev write failed: wrote {} of {} bytes (errno={})",
                    written.longValue(), UINPUT_USER_DEV_SIZE, Native.getLastError());
            CLib.INSTANCE.close(fd);
            return -1;
        }
        logger.debug("uinput_user_dev write ok ({} bytes)", written.longValue());

        int result = CLib.INSTANCE.ioctl(fd, new NativeLong(UI_DEV_CREATE), 0);
        if (result < 0) {
            logger.error("UI_DEV_CREATE ioctl failed (result={})", result);
            CLib.INSTANCE.close(fd);
            return -1;
        }

        logger.info("uinput mouse device '{}' created (fd={})", DEVICE_NAME, fd);
        return fd;
    }

    static void destroyDevice(int fd) {
        if (fd >= 0) {
            CLib.INSTANCE.ioctl(fd, new NativeLong(UI_DEV_DESTROY), 0);
            CLib.INSTANCE.close(fd);
            logger.info("uinput device destroyed (fd={})", fd);
        }
    }

    private static void requireIoctl(int fd, long request, int arg, String name) {
        int r = CLib.INSTANCE.ioctl(fd, new NativeLong(request), arg);
        if (r < 0) {
            int errno = Native.getLastError();
            logger.error("ioctl {} failed: result={} errno={}", name, r, errno);
        } else {
            logger.debug("ioctl {} ok", name);
        }
    }

    static NativeLong writeInputEvent(int fd, int type, int code, int value) {
        if (fd < 0) return new NativeLong(-1);
        Memory event = new Memory(INPUT_EVENT_SIZE);
        event.clear(); // timeval is zeroed; kernel accepts zero timestamps for synthetic events
        event.setShort(16, (short) type);
        event.setShort(18, (short) code);
        event.setInt(20, value);
        NativeLong written = CLib.INSTANCE.write(fd, event, new NativeLong(INPUT_EVENT_SIZE));
        if (written.longValue() != INPUT_EVENT_SIZE) {
            int errno = Native.getLastError();
            logger.warn("write() for input_event (type={}, code={}, value={}) wrote {} of {} bytes (errno={})",
                    type, code, value, written.longValue(), INPUT_EVENT_SIZE, errno);
        }
        return written;
    }
}

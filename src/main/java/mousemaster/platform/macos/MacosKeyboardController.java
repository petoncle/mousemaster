package mousemaster.platform.macos;

import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import mousemaster.Key;
import mousemaster.KeyboardLayout;
import mousemaster.MacroMoveDestination;
import mousemaster.ResolvedKeyMacroMove;
import mousemaster.ResolvedMacroMove;
import mousemaster.StringMacroMove;
import mousemaster.platform.KeyboardController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MacosKeyboardController implements KeyboardController {

    private static final Logger logger =
            LoggerFactory.getLogger(MacosKeyboardController.class);

    public KeyboardLayout activeKeyboardLayout;

    private record SendInputMove(ResolvedMacroMove move, boolean startRepeat) {}

    private final List<SendInputMove> sendInputQueue = new ArrayList<>();
    private final Set<Key> earlyReleasedQueuedKeys = new HashSet<>();
    private int hidSystemConnect;
    private Key pressedKeyToRepeat;
    private double durationUntilNextKeyPressRepeat;
    private boolean repeatStartedDuringCurrentTick;

    @Override
    public void reset() {
        sendInputQueue.clear();
        earlyReleasedQueuedKeys.clear();
        pressedKeyToRepeat = null;
        durationUntilNextKeyPressRepeat = 0;
        repeatStartedDuringCurrentTick = false;
    }

    @Override
    public void sendInputMoves(List<ResolvedMacroMove> moves, boolean startRepeat) {
        for (ResolvedMacroMove move : moves)
            sendInputQueue.add(new SendInputMove(move, startRepeat));
    }

    @Override
    public void keyPressedNotEaten(Key key) {
        if (!repeatStartedDuringCurrentTick || !key.equals(pressedKeyToRepeat))
            pressedKeyToRepeat = null;
    }

    @Override
    public void keyReleasedNotEaten(Key key) {
        if (key.equals(pressedKeyToRepeat))
            pressedKeyToRepeat = null;
    }

    @Override
    public void recordEarlyReleaseForQueuedPress(Key key) {
        for (SendInputMove sendInputMove : sendInputQueue) {
            if (sendInputMove.move instanceof ResolvedKeyMacroMove keyMove &&
                keyMove.press() && keyMove.key().equals(key) &&
                keyMove.destination() == MacroMoveDestination.OS) {
                earlyReleasedQueuedKeys.add(key);
                return;
            }
        }
    }

    /** Re-pressing the key means it is held after all, so the queued press stands. */
    @Override
    public void clearEarlyReleaseForQueuedPress(Key key) {
        earlyReleasedQueuedKeys.remove(key);
    }

    @Override
    public void update(double delta) {
        repeatStartedDuringCurrentTick = false;
        for (SendInputMove sendInputMove : sendInputQueue)
            send(sendInputMove);
        sendInputQueue.clear();
        if (pressedKeyToRepeat == null)
            return;
        durationUntilNextKeyPressRepeat -= delta;
        if (durationUntilNextKeyPressRepeat <= 0) {
            send(new SendInputMove(new ResolvedKeyMacroMove(pressedKeyToRepeat, true,
                    MacroMoveDestination.OS), true));
            durationUntilNextKeyPressRepeat = 0.025d;
        }
    }

    private void send(SendInputMove sendInputMove) {
        if (sendInputMove.move instanceof StringMacroMove stringMove) {
            type(stringMove.string());
            return;
        }
        ResolvedKeyMacroMove move = (ResolvedKeyMacroMove) sendInputMove.move;
        if (!send(move.key(), move.press()))
            return;
        // The user released the key while its press was still queued, so send the release
        // too: the key is tapped instead of left held down, and nothing repeats it.
        if (move.press() && earlyReleasedQueuedKeys.remove(move.key())) {
            send(move.key(), false);
            return;
        }
        if (sendInputMove.startRepeat && move.press()) {
            pressedKeyToRepeat = move.key();
            durationUntilNextKeyPressRepeat = 0.025d;
            repeatStartedDuringCurrentTick = true;
        }
        else if (!move.press() && move.key().equals(pressedKeyToRepeat))
            pressedKeyToRepeat = null;
    }

    /**
     * The virtual keyboard carries HID usages, which have no character, so typed text
     * goes out as Unicode Core Graphics events instead.
     */
    private void type(String string) {
        logger.trace("Typing " + string);
        for (int i = 0; i < string.length(); i++) {
            char[] characters = { string.charAt(i) };
            post(characters, true);
            post(characters, false);
        }
    }

    private void post(char[] characters, boolean press) {
        Pointer event = CoreGraphics.INSTANCE.CGEventCreateKeyboardEvent(null, (short) 0,
                press);
        CoreGraphics.INSTANCE.CGEventKeyboardSetUnicodeString(event, characters.length,
                characters);
        CoreGraphics.INSTANCE.CGEventPost(CoreGraphics.hidEventTap, event);
        CoreFoundation.INSTANCE.CFRelease(event);
    }

    /**
     * The state the caps lock usage would set if the keyboard it came from had a led, which
     * only the physical keyboard has. Releasing it changes nothing, as on a real keyboard.
     */
    private boolean setCapsLock(boolean press) {
        int connect = hidSystemConnect();
        if (connect == 0)
            return false;
        byte[] state = new byte[1];
        if (!press || IOKit.INSTANCE.IOHIDGetModifierLockState(connect,
                IOKit.capsLockStateSelector, state) != 0)
            return true;
        logger.debug("Turning caps lock " + (state[0] == 0 ? "on" : "off"));
        int result = IOKit.INSTANCE.IOHIDSetModifierLockState(connect,
                IOKit.capsLockStateSelector, (byte) (state[0] == 0 ? 1 : 0));
        if (result != 0)
            logger.debug("Unable to set the caps lock state, IOHIDSetModifierLockState " +
                         "returned " + result);
        return true;
    }

    private int hidSystemConnect() {
        if (hidSystemConnect != 0)
            return hidSystemConnect;
        Pointer matching = IOKit.INSTANCE.IOServiceMatching("IOHIDSystem");
        int service = IOKit.INSTANCE.IOServiceGetMatchingService(0, matching);
        if (service == 0) {
            logger.debug("No IOHIDSystem service, so caps lock is sent as a usage instead");
            return 0;
        }
        IntByReference connect = new IntByReference();
        // The native image would bake in a build time value, so it is read on first use.
        int machTaskSelf = NativeLibrary.getInstance("System")
                                        .getGlobalVariableAddress("mach_task_self_")
                                        .getInt(0);
        int result = IOKit.INSTANCE.IOServiceOpen(service, machTaskSelf,
                IOKit.paramConnectType, connect);
        IOKit.INSTANCE.IOObjectRelease(service);
        if (result != 0)
            logger.debug("Unable to open IOHIDSystem, IOServiceOpen returned " + result);
        return hidSystemConnect = connect.getValue();
    }

    /** False when no usage was sent, so nothing is left held that could repeat. */
    public boolean send(Key key, boolean press) {
        if (key.equals(Key.capslock) && setCapsLock(press))
            return false;
        MacosHidUsage hidUsage =
                MacosHidUsage.hidUsageFromKey(key, activeKeyboardLayout);
        if (hidUsage == null) {
            logger.debug("Unable to map key " + key + " to a hid usage using " +
                         activeKeyboardLayout);
            return false;
        }
        Driverkit.DKEvent event = new Driverkit.DKEvent();
        event.value = press ? 1 : 0;
        event.page = MacosHidUsage.keyboardPage;
        event.code = hidUsage.usage;
        int result = Driverkit.INSTANCE.send_key(event);
        if (result != 0)
            logger.debug("Unable to send " + (press ? "+" : "-") + key +
                         ", send_key returned " + result);
        return result == 0;
    }

}

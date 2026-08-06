package mousemaster.platform.macos;

import com.sun.jna.Pointer;
import io.qt.core.QPoint;
import io.qt.core.QRect;
import io.qt.gui.QCursor;
import io.qt.gui.QGuiApplication;
import io.qt.gui.QScreen;
import mousemaster.platform.MouseController;

import java.util.LinkedHashSet;
import java.util.Set;

public class MacosMouseController implements MouseController {

    private final CoreGraphics coreGraphics = CoreGraphics.INSTANCE;
    private final Set<Integer> pressedButtons = new LinkedHashSet<>();

    public QPoint findMousePosition() {
        return QCursor.pos();
    }

    /**
     * A button down at the HID level that mousemaster did not press itself, which the
     * Windows mouse hook recognizes as a not injected event.
     */
    public boolean pressingPhysicalButton() {
        for (int button : new int[]{CoreGraphics.leftButton, CoreGraphics.rightButton,
                CoreGraphics.centerButton}) {
            if (!pressedButtons.contains(button) &&
                coreGraphics.CGEventSourceButtonState(CoreGraphics.hidSystemState, button))
                return true;
        }
        return false;
    }

    /** Warping bypasses pointer acceleration, so there is nothing to neutralize. */
    @Override
    public void beginMove() {
    }

    @Override
    public void endMove() {
    }

    @Override
    public void moveBy(boolean xForward, double dx, boolean yForward, double dy) {
        long deltaX = (long) dx * (xForward ? 1 : -1);
        long deltaY = (long) dy * (yForward ? 1 : -1);
        if (deltaX == 0 && deltaY == 0)
            return;
        QPoint position = findMousePosition();
        moveTo(position.x() + deltaX, position.y() + deltaY);
    }

    @Override
    public void synchronousMoveTo(int x, int y) {
        moveTo(x, y);
    }

    /**
     * Warping alone leaves apps tracking the pointer unaware and does not extend a
     * selection, so the matching move event is posted too.
     */
    private void moveTo(long x, long y) {
        QPoint target = onScreen(x, y);
        CoreGraphics.CGPoint.ByValue point =
                new CoreGraphics.CGPoint.ByValue(target.x(), target.y());
        coreGraphics.CGWarpMouseCursorPosition(point);
        post(coreGraphics.CGEventCreateMouseEvent(null, moveEventType(), point,
                pressedButtons.isEmpty() ? CoreGraphics.leftButton :
                        pressedButtons.iterator().next()));
    }

    /** Warping accepts coordinates on no screen, so an off-screen target is brought back. */
    private QPoint onScreen(long x, long y) {
        QPoint target = new QPoint((int) x, (int) y);
        if (QGuiApplication.screenAt(target) != null)
            return target;
        QScreen screen = QGuiApplication.screenAt(findMousePosition());
        if (screen == null)
            screen = QGuiApplication.primaryScreen();
        QRect geometry = screen.geometry();
        return new QPoint(
                Math.clamp(target.x(), geometry.left(), geometry.right()),
                Math.clamp(target.y(), geometry.top(), geometry.bottom()));
    }

    private int moveEventType() {
        if (pressedButtons.contains(CoreGraphics.leftButton))
            return CoreGraphics.leftMouseDragged;
        if (pressedButtons.contains(CoreGraphics.rightButton))
            return CoreGraphics.rightMouseDragged;
        if (pressedButtons.contains(CoreGraphics.centerButton))
            return CoreGraphics.otherMouseDragged;
        return CoreGraphics.mouseMoved;
    }

    @Override
    public void pressLeft() {
        button(CoreGraphics.leftMouseDown, CoreGraphics.leftButton, true);
    }

    @Override
    public void pressMiddle() {
        button(CoreGraphics.otherMouseDown, CoreGraphics.centerButton, true);
    }

    @Override
    public void pressRight() {
        button(CoreGraphics.rightMouseDown, CoreGraphics.rightButton, true);
    }

    @Override
    public void releaseLeft() {
        button(CoreGraphics.leftMouseUp, CoreGraphics.leftButton, false);
    }

    @Override
    public void releaseMiddle() {
        button(CoreGraphics.otherMouseUp, CoreGraphics.centerButton, false);
    }

    @Override
    public void releaseRight() {
        button(CoreGraphics.rightMouseUp, CoreGraphics.rightButton, false);
    }

    private void button(int type, int button, boolean press) {
        if (press)
            pressedButtons.add(button);
        else
            pressedButtons.remove(button);
        QPoint position = findMousePosition();
        post(coreGraphics.CGEventCreateMouseEvent(null, type,
                new CoreGraphics.CGPoint.ByValue(position.x(), position.y()), button));
    }

    @Override
    public void wheelHorizontallyBy(boolean forward, double delta) {
        wheelBy(0, forward ? -(int) delta : (int) delta);
    }

    @Override
    public void wheelVerticallyBy(boolean forward, double delta) {
        wheelBy(forward ? -(int) delta : (int) delta, 0);
    }

    /** Wheel 1 is the vertical axis and wheel 2 the horizontal one, and both grow the
     * opposite way from Windows: a positive delta scrolls up and left. */
    private void wheelBy(int vertical, int horizontal) {
        if (vertical == 0 && horizontal == 0)
            return;
        post(coreGraphics.CGEventCreateScrollWheelEvent2(null,
                CoreGraphics.scrollEventUnitPixel, 2, vertical, horizontal, 0));
    }

    @Override
    public void showCursor() {
        coreGraphics.CGDisplayShowCursor(coreGraphics.CGMainDisplayID());
    }

    @Override
    public void hideCursor() {
        coreGraphics.CGDisplayHideCursor(coreGraphics.CGMainDisplayID());
    }

    /** The private CGS cursor API is inert on macOS 26, so the indicator is drawn in a window. */
    @Override
    public boolean supportsRenderAsCursor() {
        return false;
    }

    private void post(Pointer event) {
        coreGraphics.CGEventPost(CoreGraphics.hidEventTap, event);
        CoreFoundation.INSTANCE.CFRelease(event);
    }

}

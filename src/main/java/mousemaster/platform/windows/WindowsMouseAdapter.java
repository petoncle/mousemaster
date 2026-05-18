package mousemaster.platform.windows;

import mousemaster.*;

import mousemaster.platform.Mouse;

public class WindowsMouseAdapter implements Mouse {

    @Override
    public void beginMove() {
        WindowsMouse.beginMove();
    }

    @Override
    public void endMove() {
        WindowsMouse.endMove();
    }

    @Override
    public void moveBy(boolean xForward, double dx, boolean yForward, double dy) {
        WindowsMouse.moveBy(xForward, dx, yForward, dy);
    }

    @Override
    public void synchronousMoveTo(int x, int y) {
        WindowsMouse.synchronousMoveTo(x, y);
    }

    @Override
    public void pressLeft() {
        WindowsMouse.pressLeft();
    }

    @Override
    public void pressMiddle() {
        WindowsMouse.pressMiddle();
    }

    @Override
    public void pressRight() {
        WindowsMouse.pressRight();
    }

    @Override
    public void releaseLeft() {
        WindowsMouse.releaseLeft();
    }

    @Override
    public void releaseMiddle() {
        WindowsMouse.releaseMiddle();
    }

    @Override
    public void releaseRight() {
        WindowsMouse.releaseRight();
    }

    @Override
    public void wheelHorizontallyBy(boolean forward, double delta) {
        WindowsMouse.wheelHorizontallyBy(forward, delta);
    }

    @Override
    public void wheelVerticallyBy(boolean forward, double delta) {
        WindowsMouse.wheelVerticallyBy(forward, delta);
    }

    @Override
    public void showCursor() {
        WindowsMouse.showCursor();
    }

    @Override
    public void hideCursor() {
        WindowsMouse.hideCursor();
    }

}

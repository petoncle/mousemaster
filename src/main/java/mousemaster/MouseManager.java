package mousemaster;



import mousemaster.platform.MouseController;

import java.util.ArrayDeque;
import java.util.Deque;

public class MouseManager implements ModeListener, MousePositionListener {

    private final ScreenManager screenManager;
    private final MouseController mouseController;
    private final Mover mover = new Mover();
    private Wheel wheel;
    private boolean leftPressing, middlePressing, rightPressing;
    private boolean leftJustPressed, middleJustPressed, rightJustPressed;
    private double wheelDuration;
    private final Deque<Boolean> xWheelForwardStack = new ArrayDeque<>();
    private final Deque<Boolean> yWheelForwardStack = new ArrayDeque<>();

    private boolean wheelDecelerating;
    private double wheelDecelerationDuration;
    private double wheelVelocityAtDecelerationStart;
    private boolean wheelDecelerateXActive, wheelDecelerateXForward;
    private boolean wheelDecelerateYActive, wheelDecelerateYForward;
    private boolean lastWheelXActive, lastWheelXForward;
    private boolean lastWheelYActive, lastWheelYForward;

    public MouseManager(ScreenManager screenManager,
                        mousemaster.platform.MouseController mouseController) {
        this.screenManager = screenManager;
        this.mouseController = mouseController;
    }

    public void reset() {
        mover.reset();
        leftPressing = middlePressing = rightPressing = false;
        leftJustPressed = middleJustPressed = rightJustPressed = false;
        wheelDuration = 0;
        xWheelForwardStack.clear();
        yWheelForwardStack.clear();
        wheelDecelerating = false;
        wheelDecelerationDuration = 0;
    }

    public void setWheel(Wheel wheel) {
        this.wheel = wheel;
    }

    boolean moving() {
        return mover.moving();
    }

    public boolean leftPressing() {
        return leftPressing || leftJustPressed;
    }

    public boolean middlePressing() {
        return middlePressing || middleJustPressed;
    }

    public boolean rightPressing() {
        return rightPressing || rightJustPressed;
    }

    boolean wheeling() {
        return !xWheelForwardStack.isEmpty() || !yWheelForwardStack.isEmpty() ||
               wheelDecelerating;
    }

    private boolean activelyWheeling() {
        return !xWheelForwardStack.isEmpty() || !yWheelForwardStack.isEmpty();
    }

    public void update(double delta) {
        leftJustPressed = middleJustPressed = rightJustPressed = false;
        if (mover.moving() || mover.jumping())
            mouseController.beginMove();
        else
            mouseController.endMove();
        Point move = mover.move(delta);
        if (move != null)
            mouseController.moveBy(move.x() > 0, Math.abs(move.x()), move.y() > 0,
                    Math.abs(move.y()));
        Point jump = mover.jump(delta, leftPressing || middlePressing || rightPressing);
        if (jump != null)
            mouseController.synchronousMoveTo((int) jump.x(), (int) jump.y());
        if (activelyWheeling()) {
            wheelDuration += delta;
            double wheelVelocity = Mover.velocity(wheel.velocity(), wheelDuration);
            double deltaDistance = wheelVelocity * delta;
            // Save direction for potential deceleration.
            lastWheelXActive = !xWheelForwardStack.isEmpty();
            if (lastWheelXActive)
                lastWheelXForward = xWheelForwardStack.peek();
            lastWheelYActive = !yWheelForwardStack.isEmpty();
            if (lastWheelYActive)
                lastWheelYForward = yWheelForwardStack.peek();
            if (!xWheelForwardStack.isEmpty())
                mouseController.wheelHorizontallyBy(xWheelForwardStack.peek(), deltaDistance);
            if (!yWheelForwardStack.isEmpty())
                mouseController.wheelVerticallyBy(yWheelForwardStack.peek(), deltaDistance);
        }
        else if (wheelDecelerating) {
            wheelDecelerationDuration += delta;
            double velocity = Mover.decelerationVelocity(wheel.velocity(),
                    wheelVelocityAtDecelerationStart, wheelDecelerationDuration);
            double deltaDistance = velocity * delta;
            if (deltaDistance < 1) {
                wheelDecelerating = false;
            }
            else {
                if (wheelDecelerateXActive)
                    mouseController.wheelHorizontallyBy(wheelDecelerateXForward, deltaDistance);
                if (wheelDecelerateYActive)
                    mouseController.wheelVerticallyBy(wheelDecelerateYForward, deltaDistance);
            }
        }
    }

    private void startWheelDeceleration() {
        if (wheelDuration == 0)
            return;
        if (wheel.velocity().deceleration() <= 0) {
            wheelDuration = 0;
            return;
        }
        double velocity = Mover.velocity(wheel.velocity(), wheelDuration);
        if (velocity <= 0)
            return;
        wheelDecelerating = true;
        wheelDecelerationDuration = 0;
        wheelVelocityAtDecelerationStart = velocity;
        wheelDecelerateXActive = lastWheelXActive;
        wheelDecelerateXForward = lastWheelXForward;
        wheelDecelerateYActive = lastWheelYActive;
        wheelDecelerateYForward = lastWheelYForward;
        wheelDuration = 0;
    }

    private void cancelWheelDeceleration() {
        double currentVelocity = Mover.decelerationVelocity(wheel.velocity(),
                wheelVelocityAtDecelerationStart, wheelDecelerationDuration);
        wheelDecelerating = false;
        wheelDecelerationDuration = 0;
        wheelDuration = Mover.inverseVelocityDuration(wheel.velocity(), currentVelocity);
    }

    public void startMoveUp() {
        mover.startMoveUp();
    }

    public void startMoveDown() {
        mover.startMoveDown();
    }

    public void startMoveLeft() {
        mover.startMoveLeft();
    }

    public void startMoveRight() {
        mover.startMoveRight();
    }

    public void stopMoveUp() {
        mover.stopMoveUp();
    }

    public void stopMoveDown() {
        mover.stopMoveDown();
    }

    public void stopMoveLeft() {
        mover.stopMoveLeft();
    }

    public void stopMoveRight() {
        mover.stopMoveRight();
    }

    public void clickLeft() {
        if (!leftPressing)
            mouseController.pressLeft();
        mouseController.releaseLeft();
    }

    public void clickMiddle() {
        if (!middlePressing)
            mouseController.pressMiddle();
        mouseController.releaseMiddle();
    }

    public void clickRight() {
        if (!rightPressing)
            mouseController.pressRight();
        mouseController.releaseRight();
    }

    public void pressLeft() {
        if (leftPressing)
            return;
        releaseAll();
        leftPressing = leftJustPressed = true;
        mouseController.pressLeft();
    }

    public void pressMiddle() {
        if (middlePressing)
            return;
        releaseAll();
        middlePressing = middleJustPressed = true;
        mouseController.pressMiddle();
    }

    public void pressRight() {
        if (rightPressing)
            return;
        releaseAll();
        rightPressing = rightJustPressed = true;
        mouseController.pressRight();
    }

    public void releaseLeft() {
        if (leftPressing)
            mouseController.releaseLeft();
        leftPressing = false;
    }

    public void releaseMiddle() {
        if (middlePressing)
            mouseController.releaseMiddle();
        middlePressing = false;
    }

    public void releaseRight() {
        if (rightPressing)
            mouseController.releaseRight();
        rightPressing = false;
    }

    public void releaseAll() {
        releaseLeft();
        releaseMiddle();
        releaseRight();
    }

    public void toggleLeft() {
        if (leftPressing)
            releaseLeft();
        else
            pressLeft();
    }

    public void toggleMiddle() {
        if (middlePressing)
            releaseMiddle();
        else
            pressMiddle();
    }

    public void toggleRight() {
        if (rightPressing)
            releaseRight();
        else
            pressRight();
    }

    public void startWheelUp() {
        if (wheelDecelerating)
            cancelWheelDeceleration();
        if (!yWheelForwardStack.isEmpty() && yWheelForwardStack.peek() == false)
            return;
        yWheelForwardStack.push(false);
    }

    public void startWheelDown() {
        if (wheelDecelerating)
            cancelWheelDeceleration();
        if (!yWheelForwardStack.isEmpty() && yWheelForwardStack.peek() == true)
            return;
        yWheelForwardStack.push(true);
    }

    public void startWheelLeft() {
        if (wheelDecelerating)
            cancelWheelDeceleration();
        if (!xWheelForwardStack.isEmpty() && xWheelForwardStack.peek() == false)
            return;
        xWheelForwardStack.push(false);
    }

    public void startWheelRight() {
        if (wheelDecelerating)
            cancelWheelDeceleration();
        if (!xWheelForwardStack.isEmpty() && xWheelForwardStack.peek() == true)
            return;
        xWheelForwardStack.push(true);
    }

    public void stopWheelUp() {
        Mover.removeFirst(yWheelForwardStack, false);
        if (xWheelForwardStack.isEmpty() && yWheelForwardStack.isEmpty())
            startWheelDeceleration();
    }

    public void stopWheelDown() {
        Mover.removeFirst(yWheelForwardStack, true);
        if (xWheelForwardStack.isEmpty() && yWheelForwardStack.isEmpty())
            startWheelDeceleration();
    }

    public void stopWheelLeft() {
        Mover.removeFirst(xWheelForwardStack, false);
        if (xWheelForwardStack.isEmpty() && yWheelForwardStack.isEmpty())
            startWheelDeceleration();
    }

    public void stopWheelRight() {
        Mover.removeFirst(xWheelForwardStack, true);
        if (xWheelForwardStack.isEmpty() && yWheelForwardStack.isEmpty())
            startWheelDeceleration();
    }

    public void showCursor() {
        mouseController.showCursor();
    }

    public void hideCursor() {
        mouseController.hideCursor();
    }

    public boolean supportsRenderAsCursor() {
        return mouseController.supportsRenderAsCursor();
    }

    public void moveTo(int x, int y) {
        Point position = mover.moveTo(x, y,
                screenManager.nearestScreenContaining(x, y).scale());
        if (position == null)
            return;
        mouseController.beginMove();
        mouseController.synchronousMoveTo((int) position.x(), (int) position.y());
    }

    public boolean jumping() {
        return mover.jumping();
    }

    public boolean jumping(boolean horizontal, boolean forward) {
        return mover.jumping(horizontal, forward);
    }

    public int jumpEndX() {
        return mover.jumpEndX();
    }

    public int jumpEndY() {
        return mover.jumpEndY();
    }

    @Override
    public void modeChanged(Mode newMode) {
        if (mover.jumping() && !newMode.mouse().smoothJumpEnabled()) {
            mouseController.beginMove();
            mouseController.synchronousMoveTo(mover.jumpEndX(), mover.jumpEndY());
        }
        mover.modeChanged(newMode.mouse(), newMode.stopCommandsFromPreviousMode());
        if (wheel != null && wheelDuration != 0 && activelyWheeling()) {
            double wheelVelocity = Mover.velocity(wheel.velocity(), wheelDuration);
            wheelDuration = Mover.inverseVelocityDuration(newMode.wheel().velocity(), wheelVelocity);
        }
        if (wheelDecelerating) {
            double currentVelocity = Mover.decelerationVelocity(wheel.velocity(),
                    wheelVelocityAtDecelerationStart, wheelDecelerationDuration);
            if (currentVelocity <= 0) {
                wheelDecelerating = false;
            }
            else {
                wheelVelocityAtDecelerationStart = currentVelocity;
                wheelDecelerationDuration = 0;
            }
        }
        setWheel(newMode.wheel());
        if (newMode.stopCommandsFromPreviousMode()) {
            stopWheelDown();
            stopWheelUp();
            stopWheelLeft();
            stopWheelRight();
            wheelDecelerating = false;
            releaseAll();
        }
    }

    @Override
    public void modeTimedOut() {
        // No op.
    }

    @Override
    public void mouseMoved(int x, int y) {
        mover.moved(x, y);
    }

}

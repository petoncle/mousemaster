package jmouseable.jmouseable;

import java.util.Iterator;
import java.util.Objects;
import java.util.Stack;

public class MouseManager {

    private Mouse mouse;
    private Wheel wheel;
    private Attach attach;
    private double mouseX, mouseY;
    private double moveDuration;
    private double deltaDistanceX, deltaDistanceY;
    // Forward means right or down.
    private final Stack<Boolean> xMoveForwardStack = new Stack<>();
    private final Stack<Boolean> yMoveForwardStack = new Stack<>();
    private boolean leftPressing, middlePressing, rightPressing;
    private double wheelDuration;
    private final Stack<Boolean> xWheelForwardStack = new Stack<>();
    private final Stack<Boolean> yWheelForwardStack = new Stack<>();
    private boolean justAttached;

    public void reset() {
        moveDuration = 0;
        deltaDistanceX = deltaDistanceY = 0;
        xMoveForwardStack.clear();
        yMoveForwardStack.clear();
        leftPressing = middlePressing = rightPressing = false;
        wheelDuration = 0;
        xWheelForwardStack.clear();
        yWheelForwardStack.clear();
        justAttached = false;
    }

    public void setMouse(Mouse mouse) {
        this.mouse = mouse;
    }

    public void setWheel(Wheel wheel) {
        this.wheel = wheel;
    }

    public void setAttach(Attach attach) {
        this.attach = attach;
    }

    public boolean moving() {
        return !xMoveForwardStack.isEmpty() || !yMoveForwardStack.isEmpty();
    }

    public boolean pressing() {
        return leftPressing || middlePressing || rightPressing;
    }

    public boolean wheeling() {
        return !xWheelForwardStack.isEmpty() || !yWheelForwardStack.isEmpty();
    }

    public boolean pollJustAttached() {
        try {
            return justAttached;
        } finally {
            justAttached = false;
        }
    }

    public double mouseX() {
        return mouseX;
    }

    public double mouseY() {
        return mouseY;
    }

    public void update(double delta) {
        if (moving()) {
            moveDuration += delta;
            double moveVelocity = Math.min(mouse.maxVelocity(), mouse.initialVelocity() +
                                                                mouse.acceleration() *
                                                                Math.pow(moveDuration,
                                                                        1));
            boolean deltaBigEnough;
            if (!xMoveForwardStack.isEmpty() && !yMoveForwardStack.isEmpty()) {
                deltaDistanceX += moveVelocity * delta / Math.sqrt(2);
                deltaDistanceY += moveVelocity * delta / Math.sqrt(2);
                deltaBigEnough = deltaDistanceX >= 1 && deltaDistanceY >= 1;
            }
            else if (!xMoveForwardStack.isEmpty()) {
                deltaDistanceX += moveVelocity * delta;
                deltaDistanceY = 0;
                deltaBigEnough = deltaDistanceX >= 1;
            }
            else {
                deltaDistanceX = 0;
                deltaDistanceY += moveVelocity * delta;
                deltaBigEnough = deltaDistanceY >= 1;
            }
            if (deltaBigEnough) {
                WindowsMouse.moveBy(!xMoveForwardStack.isEmpty() && xMoveForwardStack.peek(),
                        deltaDistanceX,
                        !yMoveForwardStack.isEmpty() && yMoveForwardStack.peek(),
                        deltaDistanceY);
                deltaDistanceX = deltaDistanceY = 0;
            }
        }
        if (wheeling()) {
            wheelDuration += delta;
            double wheelVelocity = Math.min(wheel.maxVelocity(),
                    wheel.initialVelocity() + wheel.acceleration() * wheelDuration);
            double deltaDistance = wheelVelocity * delta;
            if (!xWheelForwardStack.isEmpty())
                WindowsMouse.wheelHorizontallyBy(xWheelForwardStack.peek(), deltaDistance);
            if (!yWheelForwardStack.isEmpty())
                WindowsMouse.wheelVerticallyBy(yWheelForwardStack.peek(), deltaDistance);
        }
    }

    public void mouseMoved(double x, double y) {
        this.mouseX = x;
        this.mouseY = y;
    }

    public void startMoveUp() {
        yMoveForwardStack.push(false);
    }

    public void startMoveDown() {
        yMoveForwardStack.push(true);
    }

    public void startMoveLeft() {
        xMoveForwardStack.push(false);
    }

    public void startMoveRight() {
        xMoveForwardStack.push(true);
    }

    public void stopMoveUp() {
        removeFirst(yMoveForwardStack, false);
        if (!Objects.equals(yMoveForwardStack.peek(), false))
            deltaDistanceY = 0;
        if (xMoveForwardStack.isEmpty() && yMoveForwardStack.isEmpty())
            moveDuration = 0;
    }

    private static void removeFirst(Stack<Boolean> moveForward, boolean forward) {
        Iterator<Boolean> iterator = moveForward.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() == forward) {
                iterator.remove();
                break;
            }
        }
    }

    public void stopMoveDown() {
        removeFirst(yMoveForwardStack, true);
        if (!Objects.equals(yMoveForwardStack.peek(), true))
            deltaDistanceY = 0;
        if (xMoveForwardStack.isEmpty() && yMoveForwardStack.isEmpty())
            moveDuration = 0;
    }

    public void stopMoveLeft() {
        removeFirst(xMoveForwardStack, false);
        if (!Objects.equals(xMoveForwardStack.peek(), false))
            deltaDistanceX = 0;
        if (xMoveForwardStack.isEmpty() && yMoveForwardStack.isEmpty())
            moveDuration = 0;
    }

    public void stopMoveRight() {
        removeFirst(xMoveForwardStack, true);
        if (!Objects.equals(xMoveForwardStack.peek(), true))
            deltaDistanceX = 0;
        if (xMoveForwardStack.isEmpty() && yMoveForwardStack.isEmpty())
            moveDuration = 0;
    }

    public void pressLeft() {
        leftPressing = true;
        WindowsMouse.pressLeft();
    }

    public void pressMiddle() {
        middlePressing = true;
        WindowsMouse.pressMiddle();
    }

    public void pressRight() {
        rightPressing = true;
        WindowsMouse.pressRight();
    }

    public void releaseLeft() {
        if (leftPressing)
            WindowsMouse.releaseLeft();
        leftPressing = false;
    }

    public void releaseMiddle() {
        if (middlePressing)
            WindowsMouse.releaseMiddle();
        middlePressing = false;
    }

    public void releaseRight() {
        if (rightPressing)
            WindowsMouse.releaseRight();
        rightPressing = false;
    }

    public void startWheelUp() {
        yWheelForwardStack.push(false);
    }

    public void startWheelDown() {
        yWheelForwardStack.push(true);
    }

    public void startWheelLeft() {
        xWheelForwardStack.push(false);
    }

    public void startWheelRight() {
        xWheelForwardStack.push(true);
    }

    public void stopWheelUp() {
        removeFirst(yWheelForwardStack, false);
        if (xWheelForwardStack.isEmpty() && yWheelForwardStack.isEmpty())
            wheelDuration = 0;
    }

    public void stopWheelDown() {
        removeFirst(yWheelForwardStack, true);
        if (xWheelForwardStack.isEmpty() && yWheelForwardStack.isEmpty())
            wheelDuration = 0;
    }

    public void stopWheelLeft() {
        removeFirst(xWheelForwardStack, false);
        if (xWheelForwardStack.isEmpty() && yWheelForwardStack.isEmpty())
            wheelDuration = 0;
    }

    public void stopWheelRight() {
        removeFirst(xWheelForwardStack, true);
        if (xWheelForwardStack.isEmpty() && yWheelForwardStack.isEmpty())
            wheelDuration = 0;
    }

    public void attachUp() {
        WindowsMouse.attachUp(attach);
        justAttached = true;
    }

    public void attachDown() {
        WindowsMouse.attachDown(attach);
        justAttached = true;
    }

    public void attachLeft() {
        WindowsMouse.attachLeft(attach);
        justAttached = true;
    }

    public void attachRight() {
        WindowsMouse.attachRight(attach);
        justAttached = true;
    }

    public void showCursor() {
        WindowsMouse.showCursor();
    }

    public void hideCursor() {
        WindowsMouse.hideCursor();
    }
}

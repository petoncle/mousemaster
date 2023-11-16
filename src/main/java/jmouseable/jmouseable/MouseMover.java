package jmouseable.jmouseable;

import java.util.Iterator;
import java.util.Stack;

public class MouseMover {

    private Mouse mouse;
    private Wheel wheel;
    private double x, y;
    private double moveVelocity;
    // Forward means right or down.
    private final Stack<Boolean> xMoveForwardStack = new Stack<>();
    private final Stack<Boolean> yMoveForwardStack = new Stack<>();
    private boolean leftPressing, middlePressing, rightPressing;
    private double wheelVelocity;
    private final Stack<Boolean> xWheelForwardStack = new Stack<>();
    private final Stack<Boolean> yWheelForwardStack = new Stack<>();

    public MouseMover(Mouse mouse, Wheel wheel) {
        this.mouse = mouse;
        this.wheel = wheel;
    }

    public void changeMouse(Mouse mouse) {
        this.mouse = mouse;
    }

    public void changeWheel(Wheel wheel) {
        this.wheel = wheel;
    }

    public boolean moving() {
        return moveVelocity != 0;
    }

    public boolean pressing() {
        return leftPressing || middlePressing || rightPressing;
    }

    public boolean wheeling() {
        return wheelVelocity != 0;
    }

    public void update(double delta) {
        if (moving()) {
            moveVelocity = Math.min(mouse.maxVelocity(),
                    moveVelocity + Math.pow(mouse.acceleration(), 2) * delta);
            double deltaDistanceX, deltaDistanceY;
            if (!xMoveForwardStack.isEmpty() && !yMoveForwardStack.isEmpty()) {
                deltaDistanceX = moveVelocity * delta / Math.sqrt(2);
                deltaDistanceY = moveVelocity * delta / Math.sqrt(2);
            }
            else if (!xMoveForwardStack.isEmpty()) {
                deltaDistanceX = moveVelocity * delta;
                deltaDistanceY = 0;
            }
            else {
                deltaDistanceX = 0;
                deltaDistanceY = moveVelocity * delta;
            }
            WindowsMouse.moveBy(!xMoveForwardStack.isEmpty() && xMoveForwardStack.peek(),
                    deltaDistanceX,
                    !yMoveForwardStack.isEmpty() && yMoveForwardStack.peek(),
                    deltaDistanceY);
        }
        if (wheeling()) {
            wheelVelocity = Math.min(wheel.maxVelocity(),
                    wheelVelocity + wheel.acceleration() * delta);
            double deltaDistance = wheelVelocity * delta;
            if (!xWheelForwardStack.isEmpty())
                WindowsMouse.wheelHorizontallyBy(xWheelForwardStack.peek(), deltaDistance);
            if (!yWheelForwardStack.isEmpty())
                WindowsMouse.wheelVerticallyBy(yWheelForwardStack.peek(), deltaDistance);
        }
    }

    public void mouseMoved(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void startMoveUp() {
        yMoveForwardStack.push(false);
        moveVelocity = Math.max(moveVelocity, mouse.acceleration());
    }

    public void startMoveDown() {
        yMoveForwardStack.push(true);
        moveVelocity = Math.max(moveVelocity, mouse.acceleration());
    }

    public void startMoveLeft() {
        xMoveForwardStack.push(false);
        moveVelocity = Math.max(moveVelocity, mouse.acceleration());
    }

    public void startMoveRight() {
        xMoveForwardStack.push(true);
        moveVelocity = Math.max(moveVelocity, mouse.acceleration());
    }

    public void stopMoveUp() {
        removeFirst(yMoveForwardStack, false);
        if (xMoveForwardStack.isEmpty() && yMoveForwardStack.isEmpty())
            moveVelocity = 0;
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
        if (xMoveForwardStack.isEmpty() && yMoveForwardStack.isEmpty())
            moveVelocity = 0;
    }

    public void stopMoveLeft() {
        removeFirst(xMoveForwardStack, false);
        if (xMoveForwardStack.isEmpty() && yMoveForwardStack.isEmpty())
            moveVelocity = 0;
    }

    public void stopMoveRight() {
        removeFirst(xMoveForwardStack, true);
        if (xMoveForwardStack.isEmpty() && yMoveForwardStack.isEmpty())
            moveVelocity = 0;
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
        wheelVelocity = Math.max(wheelVelocity, wheel.acceleration());
    }

    public void startWheelDown() {
        yWheelForwardStack.push(true);
        wheelVelocity = Math.max(wheelVelocity, wheel.acceleration());
    }

    public void startWheelLeft() {
        xWheelForwardStack.push(false);
        wheelVelocity = Math.max(wheelVelocity, wheel.acceleration());
    }

    public void startWheelRight() {
        xWheelForwardStack.push(true);
        wheelVelocity = Math.max(wheelVelocity, wheel.acceleration());
    }

    public void stopWheelUp() {
        removeFirst(yWheelForwardStack, false);
        if (xWheelForwardStack.isEmpty() && yWheelForwardStack.isEmpty())
            wheelVelocity = 0;
    }

    public void stopWheelDown() {
        removeFirst(yWheelForwardStack, true);
        if (xWheelForwardStack.isEmpty() && yWheelForwardStack.isEmpty())
            wheelVelocity = 0;
    }

    public void stopWheelLeft() {
        removeFirst(xWheelForwardStack, false);
        if (xWheelForwardStack.isEmpty() && yWheelForwardStack.isEmpty())
            wheelVelocity = 0;
    }

    public void stopWheelRight() {
        removeFirst(xWheelForwardStack, true);
        if (xWheelForwardStack.isEmpty() && yWheelForwardStack.isEmpty())
            wheelVelocity = 0;
    }

}

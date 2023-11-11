package jmouseable.jmouseable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MouseMover {

    private static final Logger logger = LoggerFactory.getLogger(MouseMover.class);

    private Mouse mouse;
    private Wheel wheel;
    private double x, y;
    private double moveVelocity;
    private boolean xMoving, yMoving;
    private boolean xMoveForward, yMoveForward; // Forward means right or down.
    private boolean leftPressing, middlePressing, rightPressing;
    private double wheelVelocity;
    private boolean xWheeling, yWheeling;
    private boolean xWheelForward, yWheelForward;

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
            if (xMoving && yMoving) {
                deltaDistanceX = moveVelocity * delta / Math.sqrt(2);
                deltaDistanceY = moveVelocity * delta / Math.sqrt(2);
            }
            else if (xMoving) {
                deltaDistanceX = moveVelocity * delta;
                deltaDistanceY = 0;
            }
            else {
                deltaDistanceX = 0;
                deltaDistanceY = moveVelocity * delta;
            }
            WindowsMouse.moveBy(xMoveForward, deltaDistanceX, yMoveForward,
                    deltaDistanceY);
        }
        if (wheeling()) {
            wheelVelocity = Math.min(wheel.maxVelocity(),
                    wheelVelocity + wheel.acceleration() * delta);
            double deltaDistance = wheelVelocity * delta;
            if (xWheeling)
                WindowsMouse.wheelHorizontallyBy(xWheelForward, deltaDistance);
            if (yWheeling)
                WindowsMouse.wheelVerticallyBy(yWheelForward, deltaDistance);
        }
    }

    public void mouseMoved(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void startMoveUp() {
        stopWheel();
        yMoving = true;
        yMoveForward = false;
        moveVelocity = Math.max(moveVelocity, mouse.acceleration());
    }

    private void stopWheel() { // TODO Remove, and reset combo prep instead
        wheelVelocity = 0;
        xWheeling = false;
        yWheeling = false;
    }

    private void stopMove() {
        moveVelocity = 0;
        xMoving = false;
        yMoving = false;
    }

    public void startMoveDown() {
        stopWheel();
        yMoving = true;
        yMoveForward = true;
        moveVelocity = Math.max(moveVelocity, mouse.acceleration());
    }

    public void startMoveLeft() {
        stopWheel();
        xMoving = true;
        xMoveForward = false;
        moveVelocity = Math.max(moveVelocity, mouse.acceleration());
    }

    public void startMoveRight() {
        stopWheel();
        xMoving = true;
        xMoveForward = true;
        moveVelocity = Math.max(moveVelocity, mouse.acceleration());
    }

    public void stopMoveUp() {
        if (!yMoveForward) {
            yMoving = false;
            if (!xMoving)
                moveVelocity = 0;
        }
    }

    public void stopMoveDown() {
        if (yMoveForward) {
            yMoving = false;
            if (!xMoving)
                moveVelocity = 0;
        }
    }

    public void stopMoveLeft() {
        if (!xMoveForward) {
            xMoving = false;
            if (!yMoving)
                moveVelocity = 0;
        }
    }

    public void stopMoveRight() {
        if (xMoveForward) {
            xMoving = false;
            if (!yMoving)
                moveVelocity = 0;
        }
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
        leftPressing = false;
        WindowsMouse.releaseLeft();
    }

    public void releaseMiddle() {
        middlePressing = false;
        WindowsMouse.releaseMiddle();
    }

    public void releaseRight() {
        rightPressing = false;
        WindowsMouse.releaseRight();
    }

    public void startWheelUp() {
        stopMove();
        yWheeling = true;
        yWheelForward = false;
        wheelVelocity = Math.max(wheelVelocity, wheel.acceleration());
    }

    public void startWheelDown() {
        stopMove();
        yWheeling = true;
        yWheelForward = true;
        wheelVelocity = Math.max(wheelVelocity, wheel.acceleration());
    }

    public void startWheelLeft() {
        stopMove();
        xWheeling = true;
        xWheelForward = false;
        wheelVelocity = Math.max(wheelVelocity, wheel.acceleration());
    }

    public void startWheelRight() {
        stopMove();
        xWheeling = true;
        xWheelForward = true;
        wheelVelocity = Math.max(wheelVelocity, wheel.acceleration());
    }

    public void stopWheelUp() {
        if (!yWheelForward) {
            yWheeling = false;
            if (!xWheeling)
                wheelVelocity = 0;
        }
    }

    public void stopWheelDown() {
        if (yWheelForward) {
            yWheeling = false;
            if (!xWheeling)
                wheelVelocity = 0;
        }
    }

    public void stopWheelLeft() {
        if (!xWheelForward) {
            xWheeling = false;
            if (!yWheeling)
                wheelVelocity = 0;
        }
    }

    public void stopWheelRight() {
        if (xWheelForward) {
            xWheeling = false;
            if (!yWheeling)
                wheelVelocity = 0;
        }
    }

}

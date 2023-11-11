package jmouseable.jmouseable;

public class MouseMover {

    private Mouse mouse;
    private double x, y;
    private double velocity;
    private boolean xMoving, yMoving;
    private boolean xForward, yForward; // forward means right or down
    private boolean leftPressing, middlePressing, rightPressing;

    public MouseMover(Mouse mouse) {
        this.mouse = mouse;
    }

    public void changeMouse(Mouse mouse) {
        this.mouse = mouse;
    }

    public boolean moving() {
        return velocity != 0;
    }

    public boolean pressing() {
        return leftPressing || middlePressing || rightPressing;
    }

    public void update(double delta) {
        if (!moving())
            return;
        velocity = Math.min(mouse.maxVelocity(),
                velocity + Math.pow(mouse.acceleration(), 2) * delta);
        double deltaDistanceX, deltaDistanceY;
        if (xMoving && yMoving) {
            deltaDistanceX = velocity * delta / Math.sqrt(2);
            deltaDistanceY = velocity * delta / Math.sqrt(2);
        }
        else if (xMoving) {
            deltaDistanceX = velocity * delta;
            deltaDistanceY = 0;
        }
        else {
            deltaDistanceX = 0;
            deltaDistanceY = velocity * delta;
        }
        WindowsMouse.moveBy(xForward, deltaDistanceX, yForward, deltaDistanceY);
    }

    public void mouseMoved(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void startMoveUp() {
        yMoving = true;
        yForward = false;
        velocity = Math.max(velocity, mouse.acceleration());
    }

    public void startMoveDown() {
        yMoving = true;
        yForward = true;
        velocity = Math.max(velocity, mouse.acceleration());
    }

    public void startMoveLeft() {
        xMoving = true;
        xForward = false;
        velocity = Math.max(velocity, mouse.acceleration());
    }

    public void startMoveRight() {
        xMoving = true;
        xForward = true;
        velocity = Math.max(velocity, mouse.acceleration());
    }

    public void stopMoveUp() {
        if (!yForward) {
            yMoving = false;
            if (!xMoving)
                velocity = 0;
        }
    }

    public void stopMoveDown() {
        if (yForward) {
            yMoving = false;
            if (!xMoving)
                velocity = 0;
        }
    }

    public void stopMoveLeft() {
        if (!xForward) {
            xMoving = false;
            if (!yMoving)
                velocity = 0;
        }
    }

    public void stopMoveRight() {
        if (xForward) {
            xMoving = false;
            if (!yMoving)
                velocity = 0;
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

}

package jmouseable.jmouseable;

public class MouseMover {

    private final double maxVelocity; // pixels per second
    private final double acceleration;
    private double x, y;
    private double xVelocity, yVelocity;
    private boolean xForward, yForward; // forward means right or down

    public MouseMover(double acceleration, double maxVelocity) {
        this.maxVelocity = maxVelocity;
        this.acceleration = acceleration;
    }

    public void update(double delta) {
        if (xVelocity != 0)
            xVelocity = Math.min(maxVelocity, xVelocity + acceleration * delta);
        if (yVelocity != 0)
            yVelocity = Math.min(maxVelocity, yVelocity + acceleration * delta);
        WindowsMouse.moveBy(xForward, xVelocity * delta, yForward, yVelocity * delta);
    }

    public void mouseMoved(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void startMoveUp() {
        yForward = false;
        yVelocity = Math.max(yVelocity, acceleration);
    }

    public void startMoveDown() {
        yForward = true;
        yVelocity = Math.max(yVelocity, acceleration);
    }

    public void startMoveLeft() {
        xForward = false;
        xVelocity = Math.max(xVelocity, acceleration);
    }

    public void startMoveRight() {
        xForward = true;
        xVelocity = Math.max(xVelocity, acceleration);
    }

    public void stopMoveUp() {
        if (!yForward)
            yVelocity = 0;
    }

    public void stopMoveDown() {
        if (yForward)
            yVelocity = 0;
    }

    public void stopMoveLeft() {
        if (!xForward)
            xVelocity = 0;
    }

    public void stopMoveRight() {
        if (xForward)
            xVelocity = 0;
    }

}

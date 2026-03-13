package mousemaster;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public class MouseController implements ModeListener, MousePositionListener {

    private final ScreenManager screenManager;
    private Mouse mouse;
    private Wheel wheel;
    private double moveDuration;
    private double deltaDistanceX, deltaDistanceY;
    // Forward means right or down.
    private final Deque<Boolean> xMoveForwardStack = new ArrayDeque<>();
    private final Deque<Boolean> yMoveForwardStack = new ArrayDeque<>();
    private boolean leftPressing, middlePressing, rightPressing;
    private double wheelDuration;
    private final Deque<Boolean> xWheelForwardStack = new ArrayDeque<>();
    private final Deque<Boolean> yWheelForwardStack = new ArrayDeque<>();

    private boolean decelerating;
    private double decelerationDuration;
    private double velocityAtDecelerationStart;
    private boolean decelerateXActive, decelerateXForward;
    private boolean decelerateYActive, decelerateYForward;
    // Saved direction from last update frame (used when starting deceleration).
    private boolean lastMoveXActive, lastMoveXForward;
    private boolean lastMoveYActive, lastMoveYForward;

    private boolean wheelDecelerating;
    private double wheelDecelerationDuration;
    private double wheelVelocityAtDecelerationStart;
    private boolean wheelDecelerateXActive, wheelDecelerateXForward;
    private boolean wheelDecelerateYActive, wheelDecelerateYForward;
    private boolean lastWheelXActive, lastWheelXForward;
    private boolean lastWheelYActive, lastWheelYForward;

    private int mouseX, mouseY;
    private boolean jumping;
    private double jumpDuration;
    private int jumpX, jumpY;
    private int jumpBeginX, jumpBeginY;
    private int jumpEndX, jumpEndY;

    public MouseController(ScreenManager screenManager) {
        this.screenManager = screenManager;
    }

    public void reset() {
        moveDuration = 0;
        deltaDistanceX = deltaDistanceY = 0;
        xMoveForwardStack.clear();
        yMoveForwardStack.clear();
        leftPressing = middlePressing = rightPressing = false;
        wheelDuration = 0;
        xWheelForwardStack.clear();
        yWheelForwardStack.clear();
        decelerating = false;
        decelerationDuration = 0;
        wheelDecelerating = false;
        wheelDecelerationDuration = 0;
        jumping = false;
        jumpDuration = 0;
    }

    public void setMouse(Mouse mouse) {
        this.mouse = mouse;
    }

    public void setWheel(Wheel wheel) {
        this.wheel = wheel;
    }

    boolean moving() {
        return !xMoveForwardStack.isEmpty() || !yMoveForwardStack.isEmpty() ||
               decelerating;
    }

    public boolean leftPressing() {
        return leftPressing;
    }

    public boolean middlePressing() {
        return middlePressing;
    }

    public boolean rightPressing() {
        return rightPressing;
    }

    boolean wheeling() {
        return !xWheelForwardStack.isEmpty() || !yWheelForwardStack.isEmpty() ||
               wheelDecelerating;
    }

    private boolean activelyMoving() {
        return !xMoveForwardStack.isEmpty() || !yMoveForwardStack.isEmpty();
    }

    private boolean activelyWheeling() {
        return !xWheelForwardStack.isEmpty() || !yWheelForwardStack.isEmpty();
    }

    public void update(double delta) {
        if (activelyMoving()) {
            WindowsMouse.beginMove();
            moveDuration += delta;
            double moveVelocity = moveVelocity();
            // Save direction for potential deceleration.
            lastMoveXActive = !xMoveForwardStack.isEmpty();
            if (lastMoveXActive)
                lastMoveXForward = xMoveForwardStack.peek();
            lastMoveYActive = !yMoveForwardStack.isEmpty();
            if (lastMoveYActive)
                lastMoveYForward = yMoveForwardStack.peek();
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
            if (deltaBigEnough && !jumping) {
                WindowsMouse.moveBy(
                        !xMoveForwardStack.isEmpty() && xMoveForwardStack.peek(),
                        deltaDistanceX,
                        !yMoveForwardStack.isEmpty() && yMoveForwardStack.peek(),
                        deltaDistanceY);
                deltaDistanceX = deltaDistanceY = 0;
            }
        }
        else if (decelerating) {
            WindowsMouse.beginMove();
            decelerationDuration += delta;
            double velocity = decelerationVelocity(mouse.velocity(),
                    velocityAtDecelerationStart, decelerationDuration);
            if (velocity < 1) {
                decelerating = false;
                deltaDistanceX = deltaDistanceY = 0;
            }
            else {
                boolean deltaBigEnough;
                if (decelerateXActive && decelerateYActive) {
                    deltaDistanceX += velocity * delta / Math.sqrt(2);
                    deltaDistanceY += velocity * delta / Math.sqrt(2);
                    deltaBigEnough = deltaDistanceX >= 1 && deltaDistanceY >= 1;
                }
                else if (decelerateXActive) {
                    deltaDistanceX += velocity * delta;
                    deltaDistanceY = 0;
                    deltaBigEnough = deltaDistanceX >= 1;
                }
                else {
                    deltaDistanceX = 0;
                    deltaDistanceY += velocity * delta;
                    deltaBigEnough = deltaDistanceY >= 1;
                }
                if (deltaBigEnough && !jumping) {
                    WindowsMouse.moveBy(
                            decelerateXActive && decelerateXForward,
                            deltaDistanceX,
                            decelerateYActive && decelerateYForward,
                            deltaDistanceY);
                    deltaDistanceX = deltaDistanceY = 0;
                }
            }
        }
        else if (!jumping)
            WindowsMouse.endMove();
        if (jumping) {
            jumpDuration += delta;
            double jumpVelocity = mouse.smoothJumpVelocity(); // Pixels per second.
            double jumpTotalDuration =
                    Math.hypot(jumpEndX - jumpBeginX, jumpEndY - jumpBeginY) /
                    jumpVelocity;
            double percent = Math.min(1, jumpDuration / jumpTotalDuration);
            // Smooth.
            // percent = percent * percent * (3 - 2 * percent);
            // Smoother (Ken Perlin).
            percent = percent * percent * percent * (percent * (percent * 6 - 15) + 10);
            int nextJumpX = (int) (jumpBeginX + (jumpEndX - jumpBeginX) * percent);
            int nextJumpY = (int) (jumpBeginY + (jumpEndY - jumpBeginY) * percent);
            // Merge the user movement in.
            if (percent != 1) {
                if (jumpX == jumpEndX) {
                    int movingDeltaX = xMoveForwardStack.isEmpty() ? 0 :
                            (int) (deltaDistanceX * (xMoveForwardStack.peek() ? 1 : -1));
                    deltaDistanceX -= (int) deltaDistanceX;
                    nextJumpX += movingDeltaX;
                    jumpEndX += movingDeltaX;
                }
                if (jumpY == jumpEndY) {
                    int movingDeltaY = yMoveForwardStack.isEmpty() ? 0 :
                            (int) (deltaDistanceY * (yMoveForwardStack.peek() ? 1 : -1));
                    deltaDistanceY -= (int) deltaDistanceY;
                    nextJumpY += movingDeltaY;
                    jumpEndY += movingDeltaY;
                }
            }
            if (nextJumpX != jumpX || nextJumpY != jumpY) {
                WindowsMouse.synchronousMoveTo(nextJumpX, nextJumpY);
                jumpX = nextJumpX;
                jumpY = nextJumpY;
            }
        }
        if (activelyWheeling()) {
            wheelDuration += delta;
            double wheelVelocity = velocity(wheel.velocity(), wheelDuration);
            double deltaDistance = wheelVelocity * delta;
            // Save direction for potential deceleration.
            lastWheelXActive = !xWheelForwardStack.isEmpty();
            if (lastWheelXActive)
                lastWheelXForward = xWheelForwardStack.peek();
            lastWheelYActive = !yWheelForwardStack.isEmpty();
            if (lastWheelYActive)
                lastWheelYForward = yWheelForwardStack.peek();
            if (!xWheelForwardStack.isEmpty())
                WindowsMouse.wheelHorizontallyBy(xWheelForwardStack.peek(), deltaDistance);
            if (!yWheelForwardStack.isEmpty())
                WindowsMouse.wheelVerticallyBy(yWheelForwardStack.peek(), deltaDistance);
        }
        else if (wheelDecelerating) {
            wheelDecelerationDuration += delta;
            double velocity = decelerationVelocity(wheel.velocity(),
                    wheelVelocityAtDecelerationStart, wheelDecelerationDuration);
            double deltaDistance = velocity * delta;
            if (deltaDistance < 1) {
                wheelDecelerating = false;
            }
            else {
                if (wheelDecelerateXActive)
                    WindowsMouse.wheelHorizontallyBy(wheelDecelerateXForward, deltaDistance);
                if (wheelDecelerateYActive)
                    WindowsMouse.wheelVerticallyBy(wheelDecelerateYForward, deltaDistance);
            }
        }
    }

    private double moveVelocity() {
        return velocity(mouse.velocity(), moveDuration);
    }

    static double velocity(VelocityConfiguration config, double duration) {
        double maxVelocity = config.maxVelocity();
        double initialVelocity = config.initialVelocity();
        if (initialVelocity >= maxVelocity)
            return maxVelocity;
        double acceleration = config.acceleration();
        double range = maxVelocity - initialVelocity;
        return switch (config.accelerationEasing()) {
            case Easing.Polynomial p ->
                    Math.min(maxVelocity,
                            initialVelocity + acceleration * Math.pow(duration, p.exponent()));
            case Easing.Logarithmic l ->
                    Math.min(maxVelocity,
                            initialVelocity + acceleration * Math.log(1 + duration));
            case Easing.Exponential e ->
                    Math.min(maxVelocity,
                            initialVelocity + acceleration * (Math.exp(duration) - 1));
            case Easing.Smoothstep s -> {
                double T = range / acceleration;
                double u = Math.min(1, duration / T);
                yield initialVelocity + range * (3 * u * u - 2 * u * u * u);
            }
            case Easing.Smootherstep s -> {
                double T = range / acceleration;
                double u = Math.min(1, duration / T);
                yield initialVelocity + range * (6 * u * u * u * u * u - 15 * u * u * u * u + 10 * u * u * u);
            }
        };
    }

    /**
     * Find the duration that would produce the given velocity with the given velocityConfiguration.
     */
    static double inverseVelocityDuration(VelocityConfiguration velocityConfiguration, double velocity) {
        double initialVelocity = velocityConfiguration.initialVelocity();
        double maxVelocity = velocityConfiguration.maxVelocity();
        double acceleration = velocityConfiguration.acceleration();
        if (velocity <= initialVelocity || acceleration <= 0)
            return 0;
        double x = (velocity - initialVelocity) / acceleration;
        return switch (velocityConfiguration.accelerationEasing()) {
            case Easing.Polynomial p -> Math.pow(x, 1.0 / p.exponent());
            case Easing.Logarithmic l -> Math.exp(x) - 1;
            case Easing.Exponential e -> Math.log(1 + x);
            case Easing.Smoothstep s -> {
                double range = maxVelocity - initialVelocity;
                double T = range / acceleration;
                double normalizedV = Math.min(1, (velocity - initialVelocity) / range);
                // Newton's method to invert smoothstep: solve 3u² - 2u³ = normalizedV
                double u = 0.5;
                for (int i = 0; i < 10; i++) {
                    double g = 3 * u * u - 2 * u * u * u - normalizedV;
                    double gPrime = 6 * u - 6 * u * u;
                    if (Math.abs(gPrime) < 1e-10) break;
                    u -= g / gPrime;
                    u = Math.max(0, Math.min(1, u));
                }
                yield u * T;
            }
            case Easing.Smootherstep s -> {
                double range = maxVelocity - initialVelocity;
                double T = range / acceleration;
                double normalizedV = Math.min(1, (velocity - initialVelocity) / range);
                // Newton's method to invert smootherstep: solve 6u⁵ - 15u⁴ + 10u³ = normalizedV
                double u = 0.5;
                for (int i = 0; i < 10; i++) {
                    double u2 = u * u, u3 = u2 * u, u4 = u3 * u, u5 = u4 * u;
                    double g = 6 * u5 - 15 * u4 + 10 * u3 - normalizedV;
                    double gPrime = 30 * u4 - 60 * u3 + 30 * u2;
                    if (Math.abs(gPrime) < 1e-10) break;
                    u -= g / gPrime;
                    u = Math.max(0, Math.min(1, u));
                }
                yield u * T;
            }
        };
    }

    private static double decelerationVelocity(VelocityConfiguration velocityConfiguration,
            double velocityAtStart, double duration) {
        return velocityAtStart *
               Math.exp(-velocityConfiguration.deceleration() * duration);
    }

    private void startDeceleration() {
        if (moveDuration == 0)
            return;
        if (mouse.velocity().deceleration() <= 0) {
            moveDuration = 0;
            return;
        }
        double velocity = moveVelocity();
        if (velocity <= 0)
            return;
        decelerating = true;
        decelerationDuration = 0;
        velocityAtDecelerationStart = velocity;
        decelerateXActive = lastMoveXActive;
        decelerateXForward = lastMoveXForward;
        decelerateYActive = lastMoveYActive;
        decelerateYForward = lastMoveYForward;
        moveDuration = 0;
    }

    private void cancelDeceleration() {
        double currentVelocity = decelerationVelocity(mouse.velocity(),
                velocityAtDecelerationStart, decelerationDuration);
        decelerating = false;
        decelerationDuration = 0;
        moveDuration = inverseVelocityDuration(mouse.velocity(), currentVelocity);
        deltaDistanceX = deltaDistanceY = 0;
    }

    private void startWheelDeceleration() {
        if (wheelDuration == 0)
            return;
        if (wheel.velocity().deceleration() <= 0) {
            wheelDuration = 0;
            return;
        }
        double velocity = velocity(wheel.velocity(), wheelDuration);
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
        double currentVelocity = decelerationVelocity(wheel.velocity(),
                wheelVelocityAtDecelerationStart, wheelDecelerationDuration);
        wheelDecelerating = false;
        wheelDecelerationDuration = 0;
        wheelDuration = inverseVelocityDuration(wheel.velocity(), currentVelocity);
    }

    public void startMoveUp() {
        if (decelerating)
            cancelDeceleration();
        if (!yMoveForwardStack.isEmpty() && yMoveForwardStack.contains(false))
            return;
        yMoveForwardStack.push(false);
    }

    public void startMoveDown() {
        if (decelerating)
            cancelDeceleration();
        if (!yMoveForwardStack.isEmpty() && yMoveForwardStack.contains(true))
            return;
        yMoveForwardStack.push(true);
    }

    public void startMoveLeft() {
        if (decelerating)
            cancelDeceleration();
        if (!xMoveForwardStack.isEmpty() && xMoveForwardStack.contains(false))
            return;
        xMoveForwardStack.push(false);
    }

    public void startMoveRight() {
        if (decelerating)
            cancelDeceleration();
        if (!xMoveForwardStack.isEmpty() && xMoveForwardStack.contains(true))
            return;
        xMoveForwardStack.push(true);
    }

    public void stopMoveUp() {
        removeFirst(yMoveForwardStack, false);
        if (yMoveForwardStack.isEmpty() || yMoveForwardStack.peek() != false)
            deltaDistanceY = 0;
        if (xMoveForwardStack.isEmpty() && yMoveForwardStack.isEmpty())
            startDeceleration();
    }

    private static void removeFirst(Deque<Boolean> moveForward, boolean forward) {
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
        if (yMoveForwardStack.isEmpty() || yMoveForwardStack.peek() != true)
            deltaDistanceY = 0;
        if (xMoveForwardStack.isEmpty() && yMoveForwardStack.isEmpty())
            startDeceleration();
    }

    public void stopMoveLeft() {
        removeFirst(xMoveForwardStack, false);
        if (xMoveForwardStack.isEmpty() || xMoveForwardStack.peek() != false)
            deltaDistanceX = 0;
        if (xMoveForwardStack.isEmpty() && yMoveForwardStack.isEmpty())
            startDeceleration();
    }

    public void stopMoveRight() {
        removeFirst(xMoveForwardStack, true);
        if (xMoveForwardStack.isEmpty() || xMoveForwardStack.peek() != true)
            deltaDistanceX = 0;
        if (xMoveForwardStack.isEmpty() && yMoveForwardStack.isEmpty())
            startDeceleration();
    }

    public void clickLeft() {
        if (!leftPressing)
            WindowsMouse.pressLeft();
        WindowsMouse.releaseLeft();
    }

    public void clickMiddle() {
        if (!middlePressing)
            WindowsMouse.pressMiddle();
        WindowsMouse.releaseMiddle();
    }

    public void clickRight() {
        if (!rightPressing)
            WindowsMouse.pressRight();
        WindowsMouse.releaseRight();
    }

    public void pressLeft() {
        if (leftPressing)
            return;
        releaseAll();
        leftPressing = true;
        WindowsMouse.pressLeft();
    }

    public void pressMiddle() {
        if (middlePressing)
            return;
        releaseAll();
        middlePressing = true;
        WindowsMouse.pressMiddle();
    }

    public void pressRight() {
        if (rightPressing)
            return;
        releaseAll();
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

    private void releaseAll() {
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
        removeFirst(yWheelForwardStack, false);
        if (xWheelForwardStack.isEmpty() && yWheelForwardStack.isEmpty())
            startWheelDeceleration();
    }

    public void stopWheelDown() {
        removeFirst(yWheelForwardStack, true);
        if (xWheelForwardStack.isEmpty() && yWheelForwardStack.isEmpty())
            startWheelDeceleration();
    }

    public void stopWheelLeft() {
        removeFirst(xWheelForwardStack, false);
        if (xWheelForwardStack.isEmpty() && yWheelForwardStack.isEmpty())
            startWheelDeceleration();
    }

    public void stopWheelRight() {
        removeFirst(xWheelForwardStack, true);
        if (xWheelForwardStack.isEmpty() && yWheelForwardStack.isEmpty())
            startWheelDeceleration();
    }

    public void showCursor() {
        WindowsMouse.showCursor();
    }

    public void hideCursor() {
        WindowsMouse.hideCursor();
    }

    public void moveTo(int x, int y) {
        if (x == mouseX && y == mouseY) {
            jumping = false;
            jumpDuration = 0;
            return;
        }
        if (jumping && x == jumpEndX && y == jumpEndY)
            return;
        // Move a single pixel. Skype's titlebar does not like being dragged too quick too far.
        if (!mouse.smoothJumpEnabled()) {
            WindowsMouse.beginMove();
            WindowsMouse.synchronousMoveTo(x, y);
            return;
        }
        // If already jumping but one direction changes, then reset velocity.
        if (jumping(true, true) && x < jumpEndX || jumping(true, false) && x > jumpEndX ||
            jumping(false, true) && y < jumpEndY ||
            jumping(false, false) && y > jumpEndY) {
            jumpDuration = 0;
        }
        jumpBeginX = mouseX;
        jumpBeginY = mouseY;
        jumpX = mouseX;
        jumpY = mouseY;
        jumping = true;
        WindowsMouse.beginMove();
        jumpEndX = x;
        jumpEndY = y;
    }

    public boolean jumping() {
        return jumping;
    }

    public boolean jumping(boolean horizontal, boolean forward) {
        if (!jumping)
            return false;
        if (horizontal)
            return forward ? jumpX < jumpEndX : jumpX > jumpEndX;
        return forward ? jumpY < jumpEndY : jumpY > jumpEndY;
    }

    public int jumpEndX() {
        return jumpEndX;
    }

    public int jumpEndY() {
        return jumpEndY;
    }

    @Override
    public void modeChanged(Mode newMode) {
        if (mouse != null && moveDuration != 0 && activelyMoving()) {
            double moveVelocity = moveVelocity();
            moveDuration = inverseVelocityDuration(newMode.mouse().velocity(), moveVelocity);
            deltaDistanceX = deltaDistanceY = 0;
        }
        if (decelerating) {
            double currentVelocity = decelerationVelocity(mouse.velocity(),
                    velocityAtDecelerationStart, decelerationDuration);
            if (currentVelocity <= 0) {
                decelerating = false;
            }
            else {
                velocityAtDecelerationStart = currentVelocity;
                decelerationDuration = 0;
            }
        }
        if (wheel != null && wheelDuration != 0 && activelyWheeling()) {
            double wheelVelocity = velocity(wheel.velocity(), wheelDuration);
            wheelDuration = inverseVelocityDuration(newMode.wheel().velocity(), wheelVelocity);
        }
        if (wheelDecelerating) {
            double currentVelocity = decelerationVelocity(wheel.velocity(),
                    wheelVelocityAtDecelerationStart, wheelDecelerationDuration);
            if (currentVelocity <= 0) {
                wheelDecelerating = false;
            }
            else {
                wheelVelocityAtDecelerationStart = currentVelocity;
                wheelDecelerationDuration = 0;
            }
        }
        setMouse(newMode.mouse());
        setWheel(newMode.wheel());
        if (jumping && !mouse.smoothJumpEnabled()) {
            jumping = false;
            jumpDuration = 0;
            WindowsMouse.beginMove();
            WindowsMouse.synchronousMoveTo(jumpEndX, jumpEndY);
        }
        if (newMode.stopCommandsFromPreviousMode()) {
            stopMoveDown();
            stopMoveUp();
            stopMoveLeft();
            stopMoveRight();
            decelerating = false; // Force instant stop when mode stops commands.
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
        mouseX = x;
        mouseY = y;
        jumpX = x;
        jumpY = y;
        if (mouseX == jumpEndX && mouseY == jumpEndY) {
            jumping = false;
            jumpDuration = 0;
        }
    }

}

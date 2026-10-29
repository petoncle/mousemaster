package mousemaster;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/** Moves a point in a direction, with an acceleration and a deceleration, or in a smooth jump
 *  to a position. */
public class Mover {

    private static final double DRAG_START_DURATION = 0.03;
    private static final int DRAG_START_DISTANCE = 4;

    private MovementConfiguration configuration;
    private double moveDuration;
    private double deltaDistanceX, deltaDistanceY;
    // Forward means right or down.
    private final Deque<Boolean> xMoveForwardStack = new ArrayDeque<>();
    private final Deque<Boolean> yMoveForwardStack = new ArrayDeque<>();

    private boolean decelerating;
    private double decelerationDuration;
    private double velocityAtDecelerationStart;
    private boolean decelerateXActive, decelerateXForward;
    private boolean decelerateYActive, decelerateYForward;
    // Saved direction from last update frame (used when starting deceleration).
    private boolean lastMoveXActive, lastMoveXForward;
    private boolean lastMoveYActive, lastMoveYForward;

    private int x, y;
    private boolean jumping;
    private double jumpDuration;
    private int jumpX, jumpY;
    private int jumpBeginX, jumpBeginY;
    private int jumpEndX, jumpEndY;

    public void reset() {
        moveDuration = 0;
        deltaDistanceX = deltaDistanceY = 0;
        xMoveForwardStack.clear();
        yMoveForwardStack.clear();
        decelerating = false;
        decelerationDuration = 0;
        jumping = false;
        jumpDuration = 0;
    }

    boolean moving() {
        return !xMoveForwardStack.isEmpty() || !yMoveForwardStack.isEmpty() ||
               decelerating;
    }

    private boolean activelyMoving() {
        return !xMoveForwardStack.isEmpty() || !yMoveForwardStack.isEmpty();
    }

    /** The distance to move by in this frame, or null when it does not move. */
    public Point move(double delta) {
        if (activelyMoving()) {
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
            if (deltaBigEnough && !jumping)
                return distance(!xMoveForwardStack.isEmpty() && xMoveForwardStack.peek(),
                        !yMoveForwardStack.isEmpty() && yMoveForwardStack.peek());
        }
        else if (decelerating) {
            decelerationDuration += delta;
            double velocity = decelerationVelocity(configuration.velocity(),
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
                if (deltaBigEnough && !jumping)
                    return distance(decelerateXActive && decelerateXForward,
                            decelerateYActive && decelerateYForward);
            }
        }
        return null;
    }

    private Point distance(boolean xForward, boolean yForward) {
        Point distance = new Point(xForward ? deltaDistanceX : -deltaDistanceX,
                yForward ? deltaDistanceY : -deltaDistanceY);
        deltaDistanceX = deltaDistanceY = 0;
        return distance;
    }

    /** The position of the jump in this frame, or null when it does not move. */
    public Point jump(double delta, boolean dragging) {
        if (!jumping)
            return null;
        jumpDuration += delta;
        double jumpVelocity = configuration.smoothJumpVelocity(); // Pixels per second.
        double jumpDistance =
                Math.hypot(jumpEndX - jumpBeginX, jumpEndY - jumpBeginY);
        double percent = Math.min(1, jumpDuration / (jumpDistance / jumpVelocity));
        percent = new Easing.Smootherstep().apply(percent);
        // A dragged window follows the cursor only once the app has anchored on it.
        if (jumpDuration < DRAG_START_DURATION && dragging)
            percent = Math.min(percent, DRAG_START_DISTANCE / jumpDistance);
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
        Point position = null;
        if (nextJumpX != jumpX || nextJumpY != jumpY) {
            position = new Point(nextJumpX, nextJumpY);
            jumpX = nextJumpX;
            jumpY = nextJumpY;
        }
        // An odd pixel never comes back and the jump would warp the mouse to it
        // forever (Retina screen).
        if (percent == 1) {
            jumping = false;
            jumpDuration = 0;
        }
        return position;
    }

    private double moveVelocity() {
        return velocity(configuration.velocity(), moveDuration);
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
                yield initialVelocity + range * s.apply(u);
            }
            case Easing.Smootherstep s -> {
                double T = range / acceleration;
                double u = Math.min(1, duration / T);
                yield initialVelocity + range * s.apply(u);
            }
            case Easing.ExponentialOut e -> {
                double T = range / acceleration;
                double u = Math.min(1, duration / T);
                yield initialVelocity + range * e.apply(u);
            }
            case Easing.Step s -> {
                double T = range / acceleration;
                double u = Math.min(1, duration / T);
                yield initialVelocity + range * s.apply(u);
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
            case Easing.ExponentialOut e -> {
                double range = maxVelocity - initialVelocity;
                double T = range / acceleration;
                double normalizedV = Math.min(1, (velocity - initialVelocity) / range);
                yield Math.min(T, -Math.log(1 - normalizedV *
                                            (1 - Math.pow(2, -e.halvings()))) /
                                  (Math.log(2) * e.halvings()) * T);
            }
            case Easing.Step s -> (maxVelocity - initialVelocity) / acceleration;
        };
    }

    static double decelerationVelocity(VelocityConfiguration velocityConfiguration,
            double velocityAtStart, double duration) {
        return velocityAtStart *
               Math.exp(-velocityConfiguration.deceleration() * duration);
    }

    private void startDeceleration() {
        if (moveDuration == 0)
            return;
        if (configuration.velocity().deceleration() <= 0) {
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
        double currentVelocity = decelerationVelocity(configuration.velocity(),
                velocityAtDecelerationStart, decelerationDuration);
        decelerating = false;
        decelerationDuration = 0;
        moveDuration = inverseVelocityDuration(configuration.velocity(), currentVelocity);
        deltaDistanceX = deltaDistanceY = 0;
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

    static void removeFirst(Deque<Boolean> moveForward, boolean forward) {
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

    /** Starts a smooth jump, or returns the position to go to at once. */
    public Point moveTo(int x, int y) {
        if (x == this.x && y == this.y) {
            jumping = false;
            jumpDuration = 0;
            return null;
        }
        if (jumping && x == jumpEndX && y == jumpEndY)
            return null;
        if (!configuration.smoothJumpEnabled())
            return new Point(x, y);
        // If already jumping but one direction changes, then reset velocity.
        if (jumping(true, true) && x < jumpEndX || jumping(true, false) && x > jumpEndX ||
            jumping(false, true) && y < jumpEndY ||
            jumping(false, false) && y > jumpEndY) {
            jumpDuration = 0;
        }
        jumpBeginX = this.x;
        jumpBeginY = this.y;
        jumpX = this.x;
        jumpY = this.y;
        jumping = true;
        jumpEndX = x;
        jumpEndY = y;
        return null;
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

    public void modeChanged(MovementConfiguration newConfiguration,
                            boolean stopCommandsFromPreviousMode) {
        if (configuration != null && moveDuration != 0 && activelyMoving()) {
            double moveVelocity = moveVelocity();
            moveDuration = inverseVelocityDuration(newConfiguration.velocity(), moveVelocity);
            deltaDistanceX = deltaDistanceY = 0;
        }
        if (decelerating) {
            double currentVelocity = decelerationVelocity(configuration.velocity(),
                    velocityAtDecelerationStart, decelerationDuration);
            if (currentVelocity <= 0) {
                decelerating = false;
            }
            else {
                velocityAtDecelerationStart = currentVelocity;
                decelerationDuration = 0;
            }
        }
        configuration = newConfiguration;
        if (jumping && !configuration.smoothJumpEnabled()) {
            jumping = false;
            jumpDuration = 0;
        }
        if (stopCommandsFromPreviousMode) {
            stopMoveDown();
            stopMoveUp();
            stopMoveLeft();
            stopMoveRight();
            decelerating = false; // Force instant stop when mode stops commands.
        }
    }

    public void moved(int x, int y) {
        this.x = x;
        this.y = y;
        jumpX = x;
        jumpY = y;
        if (x == jumpEndX && y == jumpEndY) {
            jumping = false;
            jumpDuration = 0;
        }
    }

}

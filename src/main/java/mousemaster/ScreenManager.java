package mousemaster;


import java.util.Set;
import java.util.function.Supplier;

public class ScreenManager implements MousePositionListener {

    private final Supplier<Set<Screen>> screens;
    private int mouseX;
    private int mouseY;

    public ScreenManager(Supplier<Set<Screen>> screens) {
        this.screens = screens;
    }

    public Screen activeScreen() {
        return nearestScreenContaining(mouseX, mouseY);
    }

    public Screen nearestScreenContaining(double pointX, double pointY) {
        return nearestScreenContaining(screens(), pointX, pointY);
    }

    public static Screen nearestScreenContaining(Set<Screen> screens, double pointX,
                                                 double pointY) {
        // Mouse position can be 0 -4 even when there is only one screen.
        if (screens.isEmpty())
            throw new IllegalStateException("No screens found");
        for (Screen screen : screens) {
            if (screen.rectangle().contains(pointX, pointY))
                return screen;
        }
        double minDistance = Double.MAX_VALUE;
        Screen nearestScreen = null;
        for (Screen screen : screens) {
            double distance = Rectangle.rectangleEdgeDistanceTo(screen.rectangle().x(),
                    screen.rectangle().y(), screen.rectangle().width(),
                    screen.rectangle().height(), pointX, pointY);
            if (distance < minDistance) {
                minDistance = distance;
                nearestScreen = screen;
            }
        }
        return nearestScreen;
    }

    /** Where a move of x and y pixels from a point stops. */
    public Point movedPoint(Point point, double x, double y) {
        return movedPoint(screens(), point, x, y);
    }

    public static Point movedPoint(Set<Screen> screens, Point point, double x, double y) {
        Screen screen = nearestScreenContaining(screens, point.x(), point.y());
        Point movedPoint = new Point(point.x() + x, point.y() + y);
        Screen movedScreen = nearestScreenContaining(screens, movedPoint.x(), movedPoint.y());
        if (!movedScreen.equals(screen)) {
            movedPoint = inOtherScreen(movedPoint, screen, movedScreen);
            movedScreen = nearestScreenContaining(screens, movedPoint.x(), movedPoint.y());
        }
        return movedScreen.rectangle().nearestPoint(movedPoint.x(), movedPoint.y());
    }

    /**
     * A point in the pixels of a screen, in the pixels of another screen. On macOS, the screens
     * are arranged in points: past the edge of a screen, a point goes on in the points of the
     * other screen, from where the two screens meet.
     */
    public static Point inOtherScreen(Point point, Screen screen, Screen otherScreen) {
        if (!Os.macos)
            return point;
        Rectangle rectangle = screen.rectangle();
        Rectangle otherRectangle = otherScreen.rectangle();
        double ratio = otherScreen.scale() / screen.scale();
        return new Point(
                coordinateInOtherScreen(point.x(), rectangle.x(), rectangle.width(),
                        otherRectangle.x(), otherRectangle.width(), ratio),
                coordinateInOtherScreen(point.y(), rectangle.y(), rectangle.height(),
                        otherRectangle.y(), otherRectangle.height(), ratio));
    }

    private static double coordinateInOtherScreen(double coordinate, int start, int size,
                                                  int otherStart, int otherSize, double ratio) {
        double meeting = otherStart >= start + size ? otherStart :
                otherStart + otherSize <= start ? start : Math.max(start, otherStart);
        return meeting + (coordinate - meeting) * ratio;
    }

    public Set<Screen> screens() {
        return screens.get();
    }

    public Screen screenContaining(double x, double y) {
        for (Screen screen : screens()) {
            if (screen.rectangle().contains(x, y))
                return screen;
        }
        return null;
    }

    @Override
    public void mouseMoved(int x, int y) {
        this.mouseX = x;
        this.mouseY = y;
    }

}

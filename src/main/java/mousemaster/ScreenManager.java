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

    /**
     * Where a move of x and y pixels from a point stops. On macOS, the screens are arranged in
     * points: past the edge of its screen, the move goes on in the points of the next screen,
     * from where the two screens meet.
     */
    public Point movedPoint(Point point, double x, double y) {
        return movedPoint(screens(), point, x, y);
    }

    public static Point movedPoint(Set<Screen> screens, Point point, double x, double y) {
        Screen screen = nearestScreenContaining(screens, point.x(), point.y());
        double movedX = point.x() + x;
        double movedY = point.y() + y;
        Screen movedScreen = nearestScreenContaining(screens, movedX, movedY);
        if (Os.macos && !movedScreen.equals(screen)) {
            Rectangle rectangle = screen.rectangle();
            Rectangle movedRectangle = movedScreen.rectangle();
            double ratio = movedScreen.scale() / screen.scale();
            movedX = inMovedScreen(movedX, rectangle.x(), rectangle.width(),
                    movedRectangle.x(), movedRectangle.width(), ratio);
            movedY = inMovedScreen(movedY, rectangle.y(), rectangle.height(),
                    movedRectangle.y(), movedRectangle.height(), ratio);
            movedScreen = nearestScreenContaining(screens, movedX, movedY);
        }
        return movedScreen.rectangle().nearestPoint(movedX, movedY);
    }

    private static double inMovedScreen(double coordinate, int start, int size, int movedStart,
                                        int movedSize, double ratio) {
        double meeting = movedStart >= start + size ? movedStart :
                movedStart + movedSize <= start ? start : Math.max(start, movedStart);
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

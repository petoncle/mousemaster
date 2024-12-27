package mousemaster;

import java.util.Set;

public class ScreenManager implements MousePositionListener {

    private int mouseX;
    private int mouseY;

    public Screen activeScreen() {
        return nearestScreenContaining(mouseX, mouseY);
    }

    public Screen nearestScreenContaining(int pointX, int pointY) {
        // Mouse position can be 0 -4 even when there is only one screen.
        Set<Screen> screens = screens();
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

    public Set<Screen> screens() {
        return WindowsScreen.findScreens();
    }

    public Screen screenContaining(int x, int y) {
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

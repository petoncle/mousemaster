package mousemaster.platform.macos;

import io.qt.core.QPoint;
import io.qt.core.QRect;
import io.qt.gui.QGuiApplication;
import io.qt.gui.QScreen;
import mousemaster.Rectangle;
import mousemaster.Screen;
import mousemaster.platform.Screens;

import java.util.HashSet;
import java.util.Set;

public class MacosScreens implements Screens {

    @Override
    public Set<Screen> findScreens() {
        return screens();
    }

    public static Set<Screen> screens() {
        Set<Screen> screens = new HashSet<>();
        for (QScreen qScreen : QGuiApplication.screens())
            screens.add(screen(qScreen));
        return screens;
    }

    public static Rectangle virtualDesktopBounds() {
        return Rectangle.union(screens().stream().map(Screen::rectangle).toList());
    }

    public static Screen findActiveScreen(QPoint point) {
        QScreen qScreen = QGuiApplication.screenAt(point);
        return screen(qScreen == null ? QGuiApplication.primaryScreen() : qScreen);
    }

    private static Screen screen(QScreen qScreen) {
        QRect geometry = qScreen.geometry();
        return new Screen(
                new Rectangle(geometry.x(), geometry.y(), geometry.width(),
                        geometry.height()),
                (int) qScreen.logicalDotsPerInch(), qScreen.devicePixelRatio());
    }

}

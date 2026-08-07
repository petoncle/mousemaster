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
        return screen(qScreenAt(point));
    }

    /** Each screen scales by its own amount, so a point is converted on the screen it is on. */
    public static QPoint logicalPoint(QPoint point) {
        QScreen qScreen = qScreenAt(point);
        double scale = qScreen.devicePixelRatio();
        QRect geometry = qScreen.geometry();
        return new QPoint(
                geometry.x() + (int) Math.round((point.x() - geometry.x() * scale) / scale),
                geometry.y() + (int) Math.round((point.y() - geometry.y() * scale) / scale));
    }

    public static QPoint pixelPoint(QPoint logicalPoint) {
        QScreen qScreen = QGuiApplication.screenAt(logicalPoint);
        if (qScreen == null)
            qScreen = QGuiApplication.primaryScreen();
        double scale = qScreen.devicePixelRatio();
        QRect geometry = qScreen.geometry();
        return new QPoint(
                (int) Math.round((geometry.x() + (logicalPoint.x() - geometry.x())) * scale),
                (int) Math.round((geometry.y() + (logicalPoint.y() - geometry.y())) * scale));
    }

    private static QScreen qScreenAt(QPoint point) {
        for (QScreen qScreen : QGuiApplication.screens()) {
            if (screen(qScreen).rectangle().contains(point.x(), point.y()))
                return qScreen;
        }
        return QGuiApplication.primaryScreen();
    }

    private static Screen screen(QScreen qScreen) {
        QRect geometry = qScreen.geometry();
        double scale = qScreen.devicePixelRatio();
        return new Screen(
                new Rectangle((int) Math.round(geometry.x() * scale),
                        (int) Math.round(geometry.y() * scale),
                        (int) Math.round(geometry.width() * scale),
                        (int) Math.round(geometry.height() * scale)),
                (int) qScreen.logicalDotsPerInch(), scale);
    }

}

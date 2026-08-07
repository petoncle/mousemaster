package mousemaster.platform.macos;

import io.qt.core.QPoint;
import io.qt.core.QRect;
import io.qt.gui.QGuiApplication;
import io.qt.gui.QScreen;
import mousemaster.Rectangle;
import mousemaster.Screen;

import java.util.HashSet;
import java.util.Set;

public class MacosScreens {

    public static Set<Screen> screens() {
        Set<Screen> screens = new HashSet<>();
        for (QScreen qScreen : QGuiApplication.screens())
            screens.add(screen(qScreen));
        return screens;
    }

    /** Where Qt lays out the screen the given pixel point is on. */
    public static Rectangle logicalScreenBounds(QPoint pixelPoint) {
        QRect geometry = qScreenAt(pixelPoint).geometry();
        return new Rectangle(geometry.x(), geometry.y(), geometry.width(), geometry.height());
    }

    public static double scaleAt(QPoint pixelPoint) {
        return qScreenAt(pixelPoint).devicePixelRatio();
    }

    public static Screen findActiveScreen(QPoint point) {
        return screen(qScreenAt(point));
    }

    /**
     * A screen sits at a different place in each space, so a point is its offset into the screen
     * it is on, scaled, from that screen's origin in the other space.
     */
    public static QPoint logicalPoint(QPoint pixelPoint) {
        QScreen qScreen = qScreenAt(pixelPoint);
        double scale = qScreen.devicePixelRatio();
        QRect geometry = qScreen.geometry();
        Rectangle rectangle = screen(qScreen).rectangle();
        return new QPoint(
                geometry.x() + (int) Math.round((pixelPoint.x() - rectangle.x()) / scale),
                geometry.y() + (int) Math.round((pixelPoint.y() - rectangle.y()) / scale));
    }

    public static QPoint pixelPoint(QPoint logicalPoint) {
        QScreen qScreen = QGuiApplication.screenAt(logicalPoint);
        if (qScreen == null)
            qScreen = QGuiApplication.primaryScreen();
        double scale = qScreen.devicePixelRatio();
        QRect geometry = qScreen.geometry();
        Rectangle rectangle = screen(qScreen).rectangle();
        return new QPoint(
                rectangle.x() + (int) Math.round((logicalPoint.x() - geometry.x()) * scale),
                rectangle.y() + (int) Math.round((logicalPoint.y() - geometry.y()) * scale));
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

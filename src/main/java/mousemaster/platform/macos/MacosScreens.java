package mousemaster.platform.macos;

import io.qt.core.QPoint;
import io.qt.core.QRect;
import io.qt.gui.QGuiApplication;
import io.qt.gui.QScreen;
import mousemaster.Rectangle;
import mousemaster.Screen;
import mousemaster.qt.TransparentWindow;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MacosScreens {

    public static Set<Screen> screens() {
        Set<Screen> screens = new HashSet<>();
        TransparentWindow.rectangleByQScreen()
                         .forEach((qScreen, rectangle) -> screens.add(screen(qScreen, rectangle)));
        return screens;
    }

    public static double scaleAt(QPoint pixelPoint) {
        return qScreenAt(TransparentWindow.rectangleByQScreen(), pixelPoint).devicePixelRatio();
    }

    public static Screen findActiveScreen(QPoint point) {
        Map<QScreen, Rectangle> rectangleByQScreen = TransparentWindow.rectangleByQScreen();
        QScreen qScreen = qScreenAt(rectangleByQScreen, point);
        return screen(qScreen, rectangleByQScreen.get(qScreen));
    }

    /**
     * A screen sits at a different place in each space, so a point is its offset into the screen
     * it is on, scaled, from that screen's origin in the other space.
     */
    public static QPoint logicalPoint(QPoint pixelPoint) {
        Map<QScreen, Rectangle> rectangleByQScreen = TransparentWindow.rectangleByQScreen();
        QScreen qScreen = qScreenAt(rectangleByQScreen, pixelPoint);
        double scale = qScreen.devicePixelRatio();
        QRect geometry = qScreen.geometry();
        Rectangle rectangle = rectangleByQScreen.get(qScreen);
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
        Rectangle rectangle = TransparentWindow.rectangleByQScreen().get(qScreen);
        return new QPoint(
                rectangle.x() + (int) Math.round((logicalPoint.x() - geometry.x()) * scale),
                rectangle.y() + (int) Math.round((logicalPoint.y() - geometry.y()) * scale));
    }

    private static QScreen qScreenAt(Map<QScreen, Rectangle> rectangleByQScreen, QPoint point) {
        for (Map.Entry<QScreen, Rectangle> entry : rectangleByQScreen.entrySet())
            if (entry.getValue().contains(point.x(), point.y()))
                return entry.getKey();
        return QGuiApplication.primaryScreen();
    }

    private static Screen screen(QScreen qScreen, Rectangle rectangle) {
        return new Screen(rectangle, (int) qScreen.logicalDotsPerInch(),
                qScreen.devicePixelRatio());
    }

}

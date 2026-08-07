package mousemaster.platform.macos;

import io.qt.core.QPoint;
import io.qt.core.QRect;
import io.qt.gui.QGuiApplication;
import io.qt.gui.QScreen;
import mousemaster.Grid;
import mousemaster.Rectangle;
import mousemaster.Screen;
import mousemaster.platform.Screens;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    /** Where Qt lays out the screen the given pixel point is on. */
    public static Rectangle logicalScreenBounds(QPoint pixelPoint) {
        QRect geometry = qScreenAt(pixelPoint).geometry();
        return new Rectangle(geometry.x(), geometry.y(), geometry.width(), geometry.height());
    }

    /** The grid spans screens that scale differently, so it is drawn in the space Qt lays out. */
    public static Rectangle logicalVirtualDesktopBounds() {
        List<Rectangle> geometries = new ArrayList<>();
        for (QScreen qScreen : QGuiApplication.screens()) {
            QRect geometry = qScreen.geometry();
            geometries.add(new Rectangle(geometry.x(), geometry.y(), geometry.width(),
                    geometry.height()));
        }
        return Rectangle.union(geometries);
    }

    public static Grid logicalGrid(Grid grid) {
        QPoint topLeft = logicalPoint(new QPoint(grid.x(), grid.y()));
        // The far corner is on the screen's exclusive edge, so the size scales by the screen the
        // grid starts on rather than by whichever screen that corner lands in.
        double scale = qScreenAt(new QPoint(grid.x(), grid.y())).devicePixelRatio();
        return grid.builder()
                   .x(topLeft.x())
                   .y(topLeft.y())
                   .width((int) Math.round(grid.width() / scale))
                   .height((int) Math.round(grid.height() / scale))
                   .build();
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

package mousemaster.qt;

import io.qt.core.QObject;
import io.qt.core.QRect;
import io.qt.core.QRectF;
import io.qt.core.Qt;
import io.qt.gui.QBrush;
import io.qt.gui.QPaintEvent;
import io.qt.gui.QPainter;
import io.qt.gui.QPen;
import io.qt.gui.QGuiApplication;
import io.qt.gui.QScreen;
import io.qt.widgets.QWidget;
import mousemaster.Os;
import mousemaster.Rectangle;
import mousemaster.Screen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TransparentWindow extends QWidget {

    private QBrush backgroundColor;
    private QRect backgroundRect;
    private int backgroundRadius;
    private QBrush backgroundBorderColor;
    private int backgroundBorderThickness;
    private double pointsPerPixel = 1;
    private Rectangle screenRectangle;
    private QRect screenGeometry;
    private Runnable beforePaint = () -> {};

    public TransparentWindow() {
        // WindowDoesNotAcceptFocus is not implemented for Windows.
        setWindowFlags(Qt.WindowType.FramelessWindowHint);
        setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground);
    }

    /** Runs before each paint of the window, which Qt then puts on the screen. */
    public void setBeforePaint(Runnable beforePaint) {
        this.beforePaint = beforePaint;
    }

    /** A null color paints nothing. */
    public void setBackground(QBrush color, QRect rect, int radius, QBrush borderColor,
                              int borderThickness) {
        if (Objects.equals(color, backgroundColor) && Objects.equals(rect, backgroundRect) &&
            radius == backgroundRadius && Objects.equals(borderColor, backgroundBorderColor) &&
            borderThickness == backgroundBorderThickness) {
            if (rect != null)
                rect.dispose();
            return;
        }
        if (this.backgroundRect != null) {
            update(this.backgroundRect);
            this.backgroundRect.dispose();
        }
        if (rect != null)
            update(rect);
        this.backgroundColor = color;
        this.backgroundRect = rect;
        this.backgroundRadius = radius;
        this.backgroundBorderColor = borderColor;
        this.backgroundBorderThickness = borderThickness;
    }

    /** Inside the edge of the background, so that a background the size of the screen shows all
     *  of it. */
    public static void paintBorder(QPainter painter, QBrush color, int thickness, int x, int y,
                                   int width, int height, int radius) {
        if (color == null || thickness == 0)
            return;
        QPen pen = new QPen();
        pen.setBrush(color);
        pen.setWidth(thickness);
        painter.setPen(pen);
        painter.setBrush(Qt.BrushStyle.NoBrush);
        double inset = thickness / 2.0;
        QRectF rect = new QRectF(x + inset, y + inset, width - thickness, height - thickness);
        painter.drawRoundedRect(rect, Math.max(0, radius - inset), Math.max(0, radius - inset));
        rect.dispose();
        pen.dispose();
    }

    /**
     * Qt positions windows in points, which are pixels except on macOS. Each screen scales by
     * its own amount, so the window is told which screen it covers.
     */
    public void coverInPixels(Screen screen) {
        Rectangle rectangle = screen.rectangle();
        moveAndResizeInPixels(screen, rectangle.x(), rectangle.y(), rectangle.width(),
                rectangle.height());
    }

    /** Sizes scale, but a position is an offset into the screen: only that offset scales. */
    public void moveAndResizeInPixels(Screen screen, int x, int y, int width, int height) {
        pointsPerPixel = Os.macos ? screen.scale() : 1;
        screenRectangle = screen.rectangle();
        screenGeometry = logicalGeometry(screenRectangle);
        move(screenGeometry.x() + toPoints(x - screenRectangle.x()),
                screenGeometry.y() + toPoints(y - screenRectangle.y()));
        resize(toPoints(width), toPoints(height));
    }

    /** Where Qt itself puts the screen, rather than a second guess at its arrangement. */
    private static QRect logicalGeometry(Rectangle screenRectangle) {
        for (Map.Entry<QScreen, Rectangle> entry : rectangleByQScreen().entrySet())
            if (entry.getValue().x() == screenRectangle.x() &&
                entry.getValue().y() == screenRectangle.y())
                return entry.getKey().geometry();
        return QGuiApplication.primaryScreen().geometry();
    }

    /**
     * The screens in pixels. The main screen is at its origin, and each other screen is placed
     * against the nearest screen already placed, from the start of the edge they share.
     */
    public static Map<QScreen, Rectangle> rectangleByQScreen() {
        List<QScreen> qScreens = QGuiApplication.screens();
        QScreen primaryQScreen = QGuiApplication.primaryScreen();
        QRect primaryGeometry = primaryQScreen.geometry();
        double primaryScale = primaryQScreen.devicePixelRatio();
        Map<QScreen, Rectangle> rectangleByQScreen = new HashMap<>();
        rectangleByQScreen.put(primaryQScreen,
                new Rectangle((int) Math.round(primaryGeometry.x() * primaryScale),
                        (int) Math.round(primaryGeometry.y() * primaryScale),
                        (int) Math.round(primaryGeometry.width() * primaryScale),
                        (int) Math.round(primaryGeometry.height() * primaryScale)));
        while (rectangleByQScreen.size() < qScreens.size()) {
            QScreen nearestQScreen = null;
            QScreen nearestPlacedQScreen = null;
            int nearestDistance = Integer.MAX_VALUE;
            for (QScreen qScreen : qScreens) {
                if (rectangleByQScreen.containsKey(qScreen))
                    continue;
                for (QScreen placedQScreen : rectangleByQScreen.keySet()) {
                    int distance = distance(qScreen.geometry(), placedQScreen.geometry());
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearestQScreen = qScreen;
                        nearestPlacedQScreen = placedQScreen;
                    }
                }
            }
            QRect geometry = nearestQScreen.geometry();
            QRect placedGeometry = nearestPlacedQScreen.geometry();
            Rectangle placedRectangle = rectangleByQScreen.get(nearestPlacedQScreen);
            double scale = nearestQScreen.devicePixelRatio();
            double placedScale = nearestPlacedQScreen.devicePixelRatio();
            rectangleByQScreen.put(nearestQScreen, new Rectangle(
                    start(geometry.x(), geometry.width(), scale, placedGeometry.x(),
                            placedGeometry.width(), placedRectangle.x(),
                            placedRectangle.width(), placedScale),
                    start(geometry.y(), geometry.height(), scale, placedGeometry.y(),
                            placedGeometry.height(), placedRectangle.y(),
                            placedRectangle.height(), placedScale),
                    (int) Math.round(geometry.width() * scale),
                    (int) Math.round(geometry.height() * scale)));
        }
        return rectangleByQScreen;
    }

    private static int distance(QRect geometry, QRect otherGeometry) {
        return Math.max(
                Math.max(geometry.x() - otherGeometry.x() - otherGeometry.width(),
                        otherGeometry.x() - geometry.x() - geometry.width()),
                Math.max(geometry.y() - otherGeometry.y() - otherGeometry.height(),
                        otherGeometry.y() - geometry.y() - geometry.height()));
    }

    private static int start(int start, int size, double scale, int placedStart, int placedSize,
                             int placedPixelStart, int placedPixelSize, double placedScale) {
        if (start >= placedStart + placedSize)
            return placedPixelStart + placedPixelSize;
        if (start + size <= placedStart)
            return placedPixelStart - (int) Math.round(size * scale);
        int sharedStart = Math.max(start, placedStart);
        return placedPixelStart + (int) Math.round((sharedStart - placedStart) * placedScale) -
               (int) Math.round((sharedStart - start) * scale);
    }

    public int xInPixels() {
        return screenRectangle.x() + toPixels(x() - screenGeometry.x());
    }

    public int yInPixels() {
        return screenRectangle.y() + toPixels(y() - screenGeometry.y());
    }

    public int widthInPixels() {
        return toPixels(width());
    }

    public int heightInPixels() {
        return toPixels(height());
    }

    private int toPoints(int pixels) {
        return (int) Math.round(pixels / pointsPerPixel);
    }

    private int toPixels(int points) {
        return (int) Math.round(points * pointsPerPixel);
    }

    @Override
    protected void paintEvent(QPaintEvent event) {
        beforePaint.run();
        if (backgroundRect != null) {
            QPainter painter = new QPainter(this);
            paintBackground(painter, backgroundColor, backgroundRect, backgroundRadius,
                    backgroundBorderColor, backgroundBorderThickness);
            painter.end();
            painter.dispose();
        }
    }

    public static void paintBackground(QPainter painter, QBrush color, QRect rect, int radius,
                                       QBrush borderColor, int borderThickness) {
        painter.setRenderHint(QPainter.RenderHint.Antialiasing, radius > 0);
        if (color != null) {
            painter.setPen(Qt.PenStyle.NoPen);
            painter.setBrush(color);
            painter.drawRoundedRect(rect.x(), rect.y(), rect.width(), rect.height(), radius,
                    radius);
        }
        paintBorder(painter, borderColor, borderThickness, rect.x(), rect.y(), rect.width(),
                rect.height(), radius);
    }

    public boolean showsNothing() {
        if (backgroundRect != null)
            return false;
        for (QObject child : children())
            if (child instanceof QWidget widget && !widget.isHidden())
                return false;
        return true;
    }

    public void hideChildren() {
        for (QObject child : children()) {
            if (child instanceof QWidget widget) {
                widget.hide();
            }
        }

    }

    public void clearWindow() {
        for (QObject child : children()) {
            if (child instanceof QWidget widget) {
                widget.setParent(null);
            }
        }
    }

}

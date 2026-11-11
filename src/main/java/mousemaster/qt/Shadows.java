package mousemaster.qt;

import io.qt.gui.QBrush;
import io.qt.gui.QColor;
import io.qt.gui.QImage;
import io.qt.gui.QPainter;
import mousemaster.Rectangle;

import java.util.List;
import java.util.function.BiConsumer;

public class Shadows {

    /** Draws the shadow of the source at x, y, stackCount times. Consumes the source. */
    public static void drawShadow(QPainter painter, QImage source, int x, int y,
                                  double blurRadius, double xOffset, double yOffset,
                                  QColor color, QBrush brush, int stackCount, boolean alphaOnly) {
        QImage shadow = QtDropShadowFilter.shadows(List.of(source), source.height(), xOffset,
                yOffset, blurRadius, color, brush, alphaOnly, false).getFirst();
        for (int stack = 0; stack < stackCount; stack++)
            painter.drawImage(x, y, shadow);
        shadow.dispose();
    }

    /**
     * Renders the shadow of what paintChunk paints in the area, in chunks of rows from the top,
     * with the source cut out when cutOut. The source is painted by ImageChunks.paintChunks.
     */
    public static List<QImage> renderShadow(Rectangle area, int dotsPerMeter,
                                            BiConsumer<QPainter, Rectangle> paintChunk,
                                            QColor shadowColor, double blurRadius,
                                            double horizontalOffset, double verticalOffset,
                                            boolean alphaOnly, boolean cutOut) {
        return QtDropShadowFilter.shadows(
                ImageChunks.paintChunks(area, 1, dotsPerMeter, paintChunk),
                ImageChunks.chunkHeight(area.width(), area.height()), horizontalOffset,
                verticalOffset, blurRadius, shadowColor, null, alphaOnly, cutOut);
    }
}

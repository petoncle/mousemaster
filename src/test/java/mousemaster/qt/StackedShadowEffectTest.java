package mousemaster.qt;

import io.qt.gui.QBrush;
import io.qt.gui.QColor;
import io.qt.gui.QImage;
import io.qt.gui.QPainter;
import mousemaster.MousemasterApplication;
import mousemaster.QtManager;
import mousemaster.Rectangle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StackedShadowEffectTest {

    /** Odd, so the halved blur leaves a last column and row to fill. */
    private static final int WIDTH = 901, HEIGHT = 701;

    private static boolean qtAvailable;

    @BeforeAll
    static void initializeQt() {
        MousemasterApplication.tempDirectory =
                System.getProperty("java.io.tmpdir") + "/mousemaster-shadow-test";
        try {
            QtManager.initialize();
        } catch (Throwable alreadyInitializedByAnotherTest) {
        }
        try {
            new QImage(1, 1, QImage.Format.Format_ARGB32_Premultiplied).dispose();
            qtAvailable = true;
        } catch (Throwable e) {
            qtAvailable = false;
        }
    }

    /** The hint mesh renders a shadow in chunks of rows, which are the rows of the shadow
     *  rendered in one piece. */
    @Test
    void shadowInChunksIsTheShadowInOnePiece() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        QImage source = source();
        QColor color = new QColor(200, 30, 30, 128);
        List<String> mismatches = new ArrayList<>();
        for (double radius : new double[]{2, 12})
            for (boolean alphaOnly : new boolean[]{true, false}) {
                QImage shadow = QtDropShadowEffect.shadows(List.of(source()), HEIGHT, -2, 3,
                        radius, color, null, alphaOnly, true).getFirst();
                byte[] shadowBytes = bytes(shadow);
                List<QImage> chunks = StackedShadowEffect.renderShadow(
                        new Rectangle(0, 0, WIDTH, HEIGHT), 3780,
                        (painter, chunk) -> painter.drawImage(0, 0, source), color, radius, -2,
                        3, alphaOnly, true);
                int top = 0, differing = 0;
                for (QImage chunk : chunks) {
                    byte[] chunkBytes = bytes(chunk);
                    for (int i = 0; i < chunkBytes.length; i++)
                        if (chunkBytes[i] != shadowBytes[top * WIDTH * 4 + i])
                            differing++;
                    top += chunk.height();
                    chunk.dispose();
                }
                if (chunks.size() < 2 || top != HEIGHT || differing != 0)
                    mismatches.add(String.format(
                            "radius=%s alphaOnly=%s: %d chunks, %d bytes differ", radius,
                            alphaOnly, chunks.size(), differing));
            }
        assertTrue(mismatches.isEmpty(), String.join("\n", mismatches));
    }

    private static byte[] bytes(QImage image) {
        byte[] bytes = new byte[image.width() * image.height() * 4];
        image.bits().get(0, bytes, 0, bytes.length);
        return bytes;
    }

    /** Antialiased shapes in colors and partial alpha, across the rows where chunks meet. */
    private static QImage source() {
        QImage image = new QImage(WIDTH, HEIGHT, QImage.Format.Format_ARGB32_Premultiplied);
        image.fill(new QColor(0, 0, 0, 0));
        QPainter painter = new QPainter(image);
        painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
        for (int i = 0; i < 24; i++) {
            QColor color = QColor.fromHsv(i * 15, 220, 230, 60 + i * 8);
            painter.setPen(color);
            painter.setBrush(new QBrush(color));
            painter.drawEllipse(20 + i * 36, 10 + i * 28, 70, 51);
        }
        painter.end();
        return image;
    }
}

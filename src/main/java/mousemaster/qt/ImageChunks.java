package mousemaster.qt;

import io.qt.gui.QImage;
import io.qt.gui.QPainter;
import mousemaster.Rectangle;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/** An image worked on across cores, in chunks of rows, square chunks or ranges of any of its
 *  units. */
public final class ImageChunks {

    /** Fewer pixels than this per thread cost more to hand over than to process. */
    private static final int CHUNK_PIXELS = 1 << 16;
    /** A line crosses about as many square chunks in any direction. */
    private static final int CHUNK_SIDE = (int) Math.sqrt(CHUNK_PIXELS);
    /** QtJambi serializes creating and disposing its objects, so each thread reuses its own. */
    private static final ThreadLocal<QImage> chunkImage = ThreadLocal.withInitial(
            () -> new QImage(CHUNK_SIDE, CHUNK_SIDE, QImage.Format.Format_ARGB32_Premultiplied));
    private static final ThreadLocal<QPainter> chunkImagePainter =
            ThreadLocal.withInitial(QPainter::new);

    private static final int threadCount = Runtime.getRuntime().availableProcessors();

    private static final ExecutorService pool = Executors.newFixedThreadPool(threadCount,
            runnable -> {
                Thread thread = new Thread(runnable, "image-chunks");
                thread.setDaemon(true);
                return thread;
            });

    private ImageChunks() {
    }

    /** The rows a thread takes of an image: each chunk but the last has this many, an even number
     *  so it starts where a row of the halved image does. */
    static int chunkHeight(int width, int height) {
        int chunks = Math.max(1, chunks((long) width * height));
        int chunkHeight = (height + chunks - 1) / chunks;
        return chunkHeight + chunkHeight % 2;
    }

    private static int chunks(long pixels) {
        return (int) Math.min(threadCount, pixels / CHUNK_PIXELS);
    }

    public interface Range {
        void run(int from, int to);
    }

    public static void overRange(int count, int pixelsPerUnit, Range range) {
        int chunks = chunks((long) count * pixelsPerUnit);
        if (chunks <= 1) {
            range.run(0, count);
            return;
        }
        List<Callable<Void>> chunked = new ArrayList<>(chunks);
        for (int chunk = 0; chunk < chunks; chunk++) {
            int from = chunk * count / chunks, to = (chunk + 1) * count / chunks;
            chunked.add(() -> {
                range.run(from, to);
                return null;
            });
        }
        try {
            for (Future<Void> chunk : pool.invokeAll(chunked))
                chunk.get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed on the image chunk threads", e);
        }
    }

    /**
     * Paints the area in chunks of rows from the top, one per thread, at devicePixelRatio and
     * dotsPerMeter: paintChunk paints each chunk, given the part of the area it covers.
     */
    public static List<QImage> paintChunks(Rectangle area, double devicePixelRatio,
                                           int dotsPerMeter,
                                           BiConsumer<QPainter, Rectangle> paintChunk) {
        int width = (int) Math.round(area.width() * devicePixelRatio);
        int height = (int) Math.round(area.height() * devicePixelRatio);
        int chunkHeight = chunkHeight(width, height);
        QImage[] images = new QImage[(height + chunkHeight - 1) / chunkHeight];
        overRange(images.length, width * chunkHeight, (from, to) -> {
            for (int chunk = from; chunk < to; chunk++) {
                QImage image = new QImage(width,
                        Math.min(chunkHeight, height - chunk * chunkHeight),
                        QImage.Format.Format_ARGB32_Premultiplied);
                image.setDevicePixelRatio(devicePixelRatio);
                image.setDotsPerMeterX(dotsPerMeter);
                image.setDotsPerMeterY(dotsPerMeter);
                image.fill(0);
                Rectangle chunkArea = new Rectangle(area.x(),
                        area.y() + (int) Math.round(chunk * chunkHeight / devicePixelRatio),
                        area.width(), (int) Math.round(image.height() / devicePixelRatio));
                QPainter painter = new QPainter(image);
                painter.translate(-chunkArea.x(), -chunkArea.y());
                paintChunk.accept(painter, chunkArea);
                painter.end();
                painter.dispose();
                images[chunk] = image;
            }
        });
        return List.of(images);
    }

    /**
     * Paints the area of pixels, the premultiplied ARGB of an image width pixels wide, in square
     * chunks that each thread takes one after the other until none is left, so that a thread
     * with little to paint takes more: paintChunk paints each chunk, given the part of the area
     * it covers.
     */
    public static void paintChunks(ByteBuffer pixels, int width, Rectangle area,
                                   BiConsumer<QPainter, Rectangle> paintChunk) {
        List<Rectangle> chunkAreas = new ArrayList<>();
        for (int y = area.y(); y < area.y() + area.height(); y += CHUNK_SIDE)
            for (int x = area.x(); x < area.x() + area.width(); x += CHUNK_SIDE)
                chunkAreas.add(new Rectangle(x, y,
                        Math.min(CHUNK_SIDE, area.x() + area.width() - x),
                        Math.min(CHUNK_SIDE, area.y() + area.height() - y)));
        AtomicInteger nextChunk = new AtomicInteger();
        overRange(chunkAreas.size(), CHUNK_PIXELS, (from, to) -> {
            QImage image = chunkImage.get();
            QPainter painter = chunkImagePainter.get();
            for (int chunk; (chunk = nextChunk.getAndIncrement()) < chunkAreas.size(); ) {
                Rectangle chunkArea = chunkAreas.get(chunk);
                image.fill(0);
                painter.begin(image);
                painter.translate(-chunkArea.x(), -chunkArea.y());
                paintChunk.accept(painter, chunkArea);
                painter.end();
                ByteBuffer bits = image.bits();
                for (int row = 0; row < chunkArea.height(); row++)
                    pixels.put(4 * ((chunkArea.y() + row) * width + chunkArea.x()), bits,
                            4 * row * CHUNK_SIDE, 4 * chunkArea.width());
            }
        });
    }
}

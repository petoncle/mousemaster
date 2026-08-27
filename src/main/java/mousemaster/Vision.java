package mousemaster;

import mousemaster.platform.Overlay;
import mousemaster.platform.DesktopCapture;
import mousemaster.platform.UiAutomation;
import mousemaster.platform.UiAutomation.UiElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Runs the stages: ink, dilate, find components, cut each one into elements. */
public class Vision {

    private static final Logger logger = LoggerFactory.getLogger(Vision.class);

    private static final int downsampleFactor = 2;
    /** Detection pixels, about the smallest glyph a screen draws. */
    private static final int minimumElementPixels = 44;
    /** Detection pixels, past which the lighter midpoint has found the pane itself. */
    private static final int maximumLightElementPixels = 500;
    /** Bigger than this is a pane, not something to click. */
    private static final double maximumElementAreaRatio = 0.25;
    /** No hint on the very edge of the screen, where a click can miss. */
    private static final int screenEdgeInset = 2;

    private static final int minimumBoundedSide = 2;
    private static final int minimumBoundedAreaFactor = 30;
    private static final int[] cornerOutsets = {0, 2, 5, 10};
    private static final int maximumCornerColors = 3;
    private static final int widestEdgeMargin = 3;
    private static final int tallestEdgeMargin = 2;
    private static final int widestInkGap = 4;
    private static final int narrowestInkGap = 3;
    /** One for each margin pair and each depth the ink is cut to. */
    static final int densities =
            widestEdgeMargin * tallestEdgeMargin + widestInkGap - narrowestInkGap + 1;
    private static final String dumpPath = System.getProperty("vision.dump");

    private final Overlay overlay;
    private final ExecutorService detectExecutor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "vision-detect");
                thread.setDaemon(true);
                return thread;
            });

    public Vision(Overlay overlay) {
        this.overlay = overlay;
    }

    public Future<List<UiElement>> startFindElements(Set<Screen> screens,
                                                     Rectangle area, int density) {
        List<Supplier<List<UiElement>>> detections = new ArrayList<>();
        for (Screen screen : screens) {
            Rectangle part = area.intersection(screen.rectangle());
            if (part.isEmpty())
                continue;
            double downscale = Math.max(1, screen.scale());
            DesktopCapture capture;
            try {
                capture = overlay.captureDesktop(part,
                        Math.max(1, (int) Math.ceil(part.width() / downscale)),
                        Math.max(1, (int) Math.ceil(part.height() / downscale)));
            }
            catch (Throwable e) {
                logger.warn("Desktop capture failed, no vision hints on that screen", e);
                continue;
            }
            detections.add(() -> findElements(capture, screen.scale(), density));
        }
        if (detections.isEmpty())
            return CompletableFuture.completedFuture(List.of());
        return detectExecutor.submit(() -> {
            long begin = System.nanoTime();
            List<UiElement> elements = new ArrayList<>();
            for (Supplier<List<UiElement>> detection : detections)
                elements.addAll(detection.get());
            elements.sort(Comparator.comparingDouble(UiElement::centerY)
                                    .thenComparingDouble(UiElement::centerX));
            logger.debug("Vision found " + elements.size() + " elements on " +
                         detections.size() + " screen(s) in " +
                         (System.nanoTime() - begin) / 1_000_000 + "ms");
            return elements;
        });
    }

    public void preWarm(Screen screen, int density) {
        startFindElements(Set.of(screen), screen.rectangle(), density);
    }

    List<UiElement> findElements(DesktopCapture capture, double scale, int density) {
        int width = capture.scaledWidth();
        int height = capture.scaledHeight();
        // The edge margins narrow one pair at a time, then the ink parts at ever
        // narrower gaps.
        int edgeSteps = widestEdgeMargin * tallestEdgeMargin;
        int step = density - 1;
        List<Rectangle> boxes;
        if (step < edgeSteps) {
            List<List<Rectangle>> byMidpoint =
                    IntStream.range(0, EdgeDetector.contrastMidpoints.length)
                             .parallel()
                             .mapToObj(midpoint -> boundedRegions(capture.scaledRgb(),
                                     width, height, scale,
                                     widestEdgeMargin - step / tallestEdgeMargin,
                                     tallestEdgeMargin - step % tallestEdgeMargin,
                                     midpoint))
                             .toList();
            boxes = new ArrayList<>(byMidpoint.getFirst());
            // A lighter midpoint answers only where the first one found nothing.
            for (int midpoint = 1; midpoint < byMidpoint.size(); midpoint++)
                for (Rectangle region : byMidpoint.get(midpoint))
                    if (boxes.stream().noneMatch(box -> box.contains(region)))
                        boxes.add(region);
        }
        else
            boxes = inkBoxes(capture.scaledRgb(), width, height,
                    widestInkGap - step + edgeSteps);
        if (dumpPath != null)
            dump(capture.scaledRgb(),
                    InkDetector.ink(capture.scaledRgb(), width, height), boxes, width,
                    height);
        // Greedy suppression keeps whichever element comes first, so order by area.
        boxes.sort(Comparator.comparingLong(box -> -(long) box.width() * box.height()));
        Rectangle bounds = capture.bounds();
        double screenScaleX = (double) bounds.width() / width;
        double screenScaleY = (double) bounds.height() / height;
        List<UiElement> elements = new ArrayList<>(boxes.size());
        for (Rectangle box : boxes) {
            double centerX = Math.clamp(
                    bounds.x() + (box.x() + box.width() / 2d) * screenScaleX,
                    bounds.x() + screenEdgeInset,
                    bounds.x() + bounds.width() - 1 - screenEdgeInset);
            double centerY = Math.clamp(
                    bounds.y() + (box.y() + box.height() / 2d) * screenScaleY,
                    bounds.y() + screenEdgeInset,
                    bounds.y() + bounds.height() - 1 - screenEdgeInset);
            if (!UiAutomation.isTooCloseToExistingUiElements(elements, centerX, centerY,
                    scale))
                elements.add(new UiElement(centerX, centerY));
        }
        return elements;
    }

    /** What the ink splits into once cut apart: one hint each. */
    private static List<Rectangle> inkBoxes(byte[] rgb, int width, int height,
                                           int minimumGapColumns) {
        int downsampledWidth = (width + downsampleFactor - 1) / downsampleFactor;
        int downsampledHeight = (height + downsampleFactor - 1) / downsampleFactor;
        boolean[] ink = InkDetector.ink(rgb, width, height);
        boolean[] mask = InkDetector.downsampledInk(
                InkDetector.dilated(ink, width, height), width, height, downsampleFactor,
                downsampledWidth, downsampledHeight);
        SummedAreaTable inkTable = new SummedAreaTable(ink, width, height);
        double maximumElementArea = maximumElementAreaRatio * width * height;
        return ConnectedComponentFinder.boundingBoxes(mask, downsampledWidth,
                        downsampledHeight,
                        minimumElementPixels / (downsampleFactor * downsampleFactor))
                .parallelStream()
                .flatMap(box -> {
                    List<Rectangle> parts = new ArrayList<>();
                    XyCut.cut(ink, inkTable, width,
                            detectionBox(box, width, height), maximumElementArea,
                            minimumGapColumns, parts);
                    return parts.stream();
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** What an edge draws a boundary around: one hint for what the ink inside splits up. */
    private static List<Rectangle> boundedRegions(byte[] rgb, int width, int height,
                                                  double scale, int edgeDilation,
                                                  int verticalDilation, int midpoint) {
        boolean[] edges = EdgeDetector.edges(rgb, width, height, edgeDilation,
                verticalDilation, midpoint);
        List<Rectangle> bounded = new ArrayList<>();
        // No floor: the dilation already makes every component wider than one.
        for (Rectangle region : ConnectedComponentFinder.boundingBoxes(edges, width,
                height, 1)) {
            // Back to source pixels, less the margins the dilation added.
            Rectangle inside = new Rectangle(region.x() + edgeDilation,
                    region.y() + verticalDilation,
                    region.width() - 2 * edgeDilation,
                    region.height() - 2 * verticalDilation);
            if (midpoint > 0 && (long) inside.width() * inside.height()
                                > maximumLightElementPixels)
                continue;
            if (isTargetShaped((int) (inside.width() * scale),
                    (int) (inside.height() * scale))
                && cornersShareABackground(rgb, width, height, inside))
                bounded.add(inside);
        }
        return bounded;
    }

    private static boolean isTargetShaped(int width, int height) {
        return width >= minimumBoundedSide && height >= minimumBoundedSide
               && (long) width * height
                  >= (long) minimumBoundedSide * minimumBoundedAreaFactor;
    }

    /** A box straddling unrelated content has corners of its own at every outset. */
    private static boolean cornersShareABackground(byte[] rgb, int width, int height,
                                                   Rectangle box) {
        for (int outset : cornerOutsets) {
            int x0 = box.x() - outset;
            int x1 = box.x() + box.width() + outset;
            int y0 = box.y();
            int y1 = box.y() + box.height();
            if (x0 < 0 || y0 < 0 || x1 >= width || y1 >= height)
                continue;
            int topLeft = color(rgb, width, x0, y0);
            int topRight = color(rgb, width, x1, y0);
            int bottomLeft = color(rgb, width, x0, y1);
            int bottomRight = color(rgb, width, x1, y1);
            int colors = 1;
            if (topRight != topLeft)
                colors++;
            if (bottomLeft != topLeft && bottomLeft != topRight)
                colors++;
            if (bottomRight != topLeft && bottomRight != topRight
                && bottomRight != bottomLeft)
                colors++;
            if (colors <= maximumCornerColors)
                return true;
        }
        return false;
    }

    private static int color(byte[] rgb, int width, int x, int y) {
        int channel = (y * width + x) * 3;
        return (rgb[channel] & 0xff) << 16 | (rgb[channel + 1] & 0xff) << 8 |
               rgb[channel + 2] & 0xff;
    }

    /** A grid box in the detected frame's pixels, clamped to it. */
    private static Rectangle detectionBox(Rectangle box, int width, int height) {
        int left = box.x() * downsampleFactor;
        int top = box.y() * downsampleFactor;
        return new Rectangle(left, top,
                Math.min(width, (box.x() + box.width()) * downsampleFactor) - left,
                Math.min(height, (box.y() + box.height()) * downsampleFactor) - top);
    }

    /** The capture with its ink in red and every emitted box outlined in green. */
    private static void dump(byte[] rgb, boolean[] ink, List<Rectangle> boxes, int width,
                             int height) {
        byte[] image = rgb.clone();
        for (int pixel = 0; pixel < ink.length; pixel++)
            if (ink[pixel])
                paint(image, pixel, 255, 0, 0);
        for (Rectangle box : boxes) {
            for (int x = box.x(); x < box.x() + box.width(); x++) {
                paint(image, box.y() * width + x, 0, 255, 0);
                paint(image, (box.y() + box.height() - 1) * width + x, 0, 255, 0);
            }
            for (int y = box.y(); y < box.y() + box.height(); y++) {
                paint(image, y * width + box.x(), 0, 255, 0);
                paint(image, y * width + box.x() + box.width() - 1, 0, 255, 0);
            }
        }
        try (OutputStream out = Files.newOutputStream(Path.of(dumpPath))) {
            out.write(("P6 " + width + " " + height + " 255 ").getBytes());
            out.write(image);
        }
        catch (IOException e) {
            logger.warn("Vision dump failed", e);
        }
    }

    private static void paint(byte[] image, int pixel, int red, int green, int blue) {
        image[pixel * 3] = (byte) red;
        image[pixel * 3 + 1] = (byte) green;
        image[pixel * 3 + 2] = (byte) blue;
    }

}

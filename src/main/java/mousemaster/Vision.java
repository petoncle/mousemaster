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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.List;
import java.util.Map;
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
    private static final int minimumBoundedArea = 30;
    private static final int[] cornerOutsets = {0, 2, 5, 10};
    /** Antialiasing shortens ink towards the background without turning it. */
    private static final double minimumInkCosine = 0.97;
    /** What tells grey ink from black. */
    private static final double minimumInkLengthRatio = 0.5;
    private static final int widestEdgeMargin = 6;
    private static final int narrowestEdgeMargin = 1;
    private static final int edgeSteps = widestEdgeMargin - narrowestEdgeMargin + 1;
    private static final int verticalEdgeMargin = 1;
    private static final int firstDensityEdgeMargin = 3;
    private static final int inkGap = 3;
    /** Plain text left out, one for each edge margin, then one for the ink. */
    static final int densities = edgeSteps + 2;
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
        // Plain text left out, then the edge margin narrows, then the ink no region holds.
        int step = Math.clamp(density - 2, 0, edgeSteps - 1);
        int edgeMargin = density == 1 ? firstDensityEdgeMargin : widestEdgeMargin - step;
        CompletableFuture<List<Rectangle>> ink = density == densities
                ? CompletableFuture.supplyAsync(
                        () -> inkBoxes(capture.scaledRgb(), width, height, inkGap))
                : CompletableFuture.completedFuture(List.of());
        List<List<Rectangle>> byMidpoint =
                IntStream.range(0, EdgeDetector.contrastMidpoints.length)
                         .parallel()
                         .mapToObj(midpoint -> boundedRegions(capture.scaledRgb(),
                                 width, height, scale,
                                 edgeMargin, verticalEdgeMargin, midpoint))
                         .toList();
        List<Rectangle> boxes = new ArrayList<>(byMidpoint.getFirst());
        // A lighter midpoint answers only where the first one found nothing.
        for (int midpoint = 1; midpoint < byMidpoint.size(); midpoint++)
            addUncontained(boxes, byMidpoint.get(midpoint));
        if (density == 1)
            boxes = boxes.parallelStream()
                         .filter(box -> isGaplessOrBordered(capture.scaledRgb(), width,
                                 height, box))
                         .collect(Collectors.toCollection(ArrayList::new));
        addUncontained(boxes, ink.join());
        if (dumpPath != null)
            dump(capture.scaledRgb(),
                    InkDetector.ink(capture.scaledRgb(), width, height), boxes, width,
                    height);
        // Greedy suppression keeps whichever element comes first, so order by area.
        boxes.sort(Comparator.comparingLong(box -> -area(box)));
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

    /** A pane, a box around other boxes, hides nothing. */
    private static void addUncontained(List<Rectangle> boxes, List<Rectangle> others) {
        List<Rectangle> sortedBoxes = boxes.stream().sorted(Comparator.comparingLong(Vision::area))
                                           .toList();
        Map<Rectangle, Boolean> isPaneByBox = new HashMap<>();
        for (Rectangle other : others)
            if (boxes.stream().noneMatch(box -> box.contains(other)
                    && !isPaneByBox.computeIfAbsent(box, pane -> isPane(pane, sortedBoxes))))
                boxes.add(other);
    }

    private static boolean isPane(Rectangle box, List<Rectangle> sortedBoxes) {
        for (Rectangle sortedBox : sortedBoxes) {
            if (area(sortedBox) > area(box))
                return false;
            if (sortedBox != box && box.contains(sortedBox))
                return true;
        }
        return false;
    }

    private static long area(Rectangle box) {
        return (long) box.width() * box.height();
    }

    /** What the ink splits into once cut apart: one hint each. */
    private static List<Rectangle> inkBoxes(byte[] rgb, int width, int height,
                                           int minimumGapColumns) {
        int downsampledWidth = (width + downsampleFactor - 1) / downsampleFactor;
        int downsampledHeight = (height + downsampleFactor - 1) / downsampleFactor;
        long[] ink = InkDetector.ink(rgb, width, height);
        long[] mask = InkDetector.downsampledInk(
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
        long[] edges = EdgeDetector.edges(rgb, width, height, edgeDilation,
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
            if (midpoint > 0 && area(inside) > maximumLightElementPixels)
                continue;
            if (!isTargetShaped((int) (inside.width() * scale),
                    (int) (inside.height() * scale)))
                continue;
            int background = cornerBackground(rgb, width, height, inside);
            if (background < 0)
                continue;
            for (Rectangle part : inkParts(rgb, width, inside, background))
                if (isTargetShaped((int) (part.width() * scale),
                        (int) (part.height() * scale)))
                    bounded.add(part);
        }
        return bounded;
    }

    /** Ink without a gap, as an icon or a filled button draws, or a border on every side.
     *  A column under half the strongest is a gap: antialiasing only blurs a word's. */
    private static boolean isGaplessOrBordered(byte[] rgb, int width, int height,
                                               Rectangle part) {
        int background = cornerBackground(rgb, width, height, part);
        if (background < 0)
            return false;
        int[] distances = new int[part.width()];
        for (int column = 0; column < part.width(); column++)
            for (int y = part.y(); y < part.y() + part.height(); y++)
                distances[column] = Math.max(distances[column],
                        distance(color(rgb, width, part.x() + column, y), background));
        int strongest = Arrays.stream(distances).max().orElse(0);
        int runs = 0;
        boolean inked = false;
        for (int distance : distances) {
            boolean ink = isInk(distance) && distance * 2 >= strongest;
            if (ink && !inked)
                runs++;
            inked = ink;
        }
        return runs == 1
               || isBorder(rgb, width, height, part, background, 0, -1)
                  && isBorder(rgb, width, height, part, background, 0, 1)
                  && isBorder(rgb, width, height, part, background, -1, 0)
                  && isBorder(rgb, width, height, part, background, 1, 0);
    }

    /** Whether the nearest ink on that side of the part is a line along all of it. */
    private static boolean isBorder(byte[] rgb, int width, int height, Rectangle part,
                                    int background, int dx, int dy) {
        for (int outset = 1; ; outset++) {
            Rectangle line = dy == 0
                    ? new Rectangle(dx < 0 ? part.x() - outset
                                           : part.x() + part.width() - 1 + outset,
                            part.y(), 1, part.height())
                    : new Rectangle(part.x(), dy < 0 ? part.y() - outset
                                                     : part.y() + part.height() - 1 + outset,
                            part.width(), 1);
            if (line.x() < 0 || line.y() < 0 || line.x() + line.width() > width
                || line.y() + line.height() > height)
                return false;
            int ink = inkPixels(rgb, width, line, background);
            if (ink > 0)
                return ink == line.width() * line.height();
        }
    }

    private static int inkPixels(byte[] rgb, int width, Rectangle area, int background) {
        int pixels = 0;
        for (int y = area.y(); y < area.y() + area.height(); y++)
            for (int x = area.x(); x < area.x() + area.width(); x++)
                if (isInk(distance(color(rgb, width, x, y), background)))
                    pixels++;
        return pixels;
    }

    /** A quarter of the ink threshold, which the faintest outlines reach once scaled down. */
    private static boolean isInk(int distance) {
        return distance > InkDetector.inkThreshold / 4;
    }

    /** The box parted at each gap between ink of two colours. */
    private static List<Rectangle> inkParts(byte[] rgb, int width, Rectangle box,
                                            int background) {
        int[] inks = new int[box.width()];
        int[] distances = new int[box.width()];
        for (int y = box.y(); y < box.y() + box.height(); y++) {
            for (int column = 0; column < box.width(); column++) {
                int color = color(rgb, width, box.x() + column, y);
                int distance = distance(color, background);
                if (distance > distances[column]) {
                    inks[column] = color;
                    distances[column] = distance;
                }
            }
        }
        List<Rectangle> parts = new ArrayList<>();
        int left = 0, right = -1, ink = 0;
        for (int column = 0; column < box.width(); column++) {
            if (distances[column] <= InkDetector.inkThreshold)
                continue;
            int runLeft = column, farthest = column;
            while (column + 1 < box.width()
                   && distances[column + 1] > InkDetector.inkThreshold)
                if (distances[++column] > distances[farthest])
                    farthest = column;
            if (right < 0)
                left = runLeft;
            else if (!sameInk(background, ink, inks[farthest])) {
                parts.add(new Rectangle(box.x() + left, box.y(), right - left + 1,
                        box.height()));
                left = runLeft;
            }
            right = column;
            ink = inks[farthest];
        }
        if (parts.isEmpty())
            return List.of(box);
        parts.add(new Rectangle(box.x() + left, box.y(), right - left + 1, box.height()));
        return parts;
    }

    private static boolean sameInk(int background, int ink, int otherInk) {
        double dot = 0, length = 0, otherLength = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            int channel = (ink >> shift & 0xff) - (background >> shift & 0xff);
            int otherChannel = (otherInk >> shift & 0xff) - (background >> shift & 0xff);
            dot += channel * otherChannel;
            length += channel * channel;
            otherLength += otherChannel * otherChannel;
        }
        length = Math.sqrt(length);
        otherLength = Math.sqrt(otherLength);
        return dot >= minimumInkCosine * length * otherLength
               && Math.min(length, otherLength)
                  >= minimumInkLengthRatio * Math.max(length, otherLength);
    }

    private static int distance(int color, int otherColor) {
        int distance = 0;
        for (int shift = 0; shift <= 16; shift += 8)
            distance = Math.max(distance,
                    Math.abs((color >> shift & 0xff) - (otherColor >> shift & 0xff)));
        return distance;
    }

    private static boolean isTargetShaped(int width, int height) {
        return width >= minimumBoundedSide && height >= minimumBoundedSide
               && (long) width * height >= minimumBoundedArea;
    }

    /** A box straddling unrelated content has corners of its own at every outset: -1. */
    private static int cornerBackground(byte[] rgb, int width, int height, Rectangle box) {
        for (int outset : cornerOutsets) {
            int x0 = box.x() - outset;
            int x1 = box.x() + box.width() + outset;
            int y0 = box.y();
            int y1 = box.y() + box.height();
            if (x0 < 0 || y0 < 0 || x1 >= width || y1 >= height)
                continue;
            int[] corners = {color(rgb, width, x0, y0), color(rgb, width, x1, y0),
                             color(rgb, width, x0, y1), color(rgb, width, x1, y1)};
            for (int corner = 0; corner < corners.length; corner++)
                for (int other = corner + 1; other < corners.length; other++)
                    if (corners[corner] == corners[other])
                        return corners[corner];
        }
        return -1;
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
    private static void dump(byte[] rgb, long[] ink, List<Rectangle> boxes, int width,
                             int height) {
        byte[] image = rgb.clone();
        int words = Dilation.words(width);
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (Dilation.isSet(ink, words, x, y))
                    paint(image, y * width + x, 255, 0, 0);
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

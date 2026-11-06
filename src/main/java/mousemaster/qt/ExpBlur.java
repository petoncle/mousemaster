// Copyright (C) 2016 The Qt Company Ltd.
// SPDX-License-Identifier: LGPL-3.0-only
//
// A Java port, made in 2026, of expblur, qt_blurrow, qt_blurImage and qt_halfScaled, from
// qtbase/src/gui/painting/qimageeffects.cpp, and of qt_memrotate90 and qt_memrotate270, from
// qtbase/src/gui/painting/qmemrotate.cpp, both at Qt 6.8.2. Upstream is available under
// LicenseRef-Qt-Commercial OR LGPL-3.0-only OR GPL-2.0-only OR GPL-3.0-only; this port elects
// LGPL-3.0-only. See THIRD-PARTY-NOTICES.md.
package mousemaster.qt;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Qt's exponential blur, ported so it can run off the GUI thread and across cores. Qt's own blur
 * goes through QPixmap, which is GUI-thread only and silently produces garbage anywhere else.
 *
 * <p>The blur is a pair of one-dimensional passes with a transpose between them, and every row of a
 * pass is independent of the others, so spreading rows over threads computes exactly what one
 * thread would. A channel is blurred on its own, as a one-byte-per-pixel plane.
 */
public final class ExpBlur {

    /** expblur's fixed-point precisions, as qt_blurImage instantiates them. */
    private static final int APREC = 12, ZPREC = 10;

    /** Transposing a tile at a time keeps both sides of the copy in cache. */
    private static final int TILE = 32;

    /** Fewer pixels than this per thread cost more to hand over than to process. */
    private static final int CHUNK_PIXELS = 1 << 16;

    private static final int threadCount = Runtime.getRuntime().availableProcessors();

    private static final ExecutorService rowPool = Executors.newFixedThreadPool(threadCount,
            runnable -> {
                Thread thread = new Thread(runnable, "blur-rows");
                thread.setDaemon(true);
                return thread;
            });

    private ExpBlur() {
    }

    /**
     * One channel of an ARGB32_Premultiplied image, 3 being alpha, moved by (dx, dy) and, when
     * scale is 2, halved as qt_halfScaled halves it: each value is the average of a 2x2 block,
     * rounded down.
     */
    public static byte[] plane(IntBuffer pixels, int width, int height, int channel, int dx,
                               int dy, int scale) {
        int planeWidth = width / scale, planeHeight = height / scale;
        byte[] plane = new byte[planeWidth * planeHeight];
        overRange(planeHeight, width, (from, to) -> {
            int[] sourceRow = new int[width];
            int[] top = new int[width];
            int[] bottom = new int[width];
            for (int y = from; y < to; y++) {
                movedRow(pixels, width, height, channel, dx, scale * y - dy, sourceRow, top);
                if (scale == 1) {
                    for (int x = 0; x < planeWidth; x++)
                        plane[y * planeWidth + x] = (byte) top[x];
                    continue;
                }
                movedRow(pixels, width, height, channel, dx, scale * y + 1 - dy, sourceRow,
                        bottom);
                for (int x = 0; x < planeWidth; x++)
                    plane[y * planeWidth + x] = (byte) (((top[2 * x] + top[2 * x + 1] >> 1) +
                                                         (bottom[2 * x] + bottom[2 * x + 1] >> 1))
                                                        >> 1);
            }
        });
        return plane;
    }

    /** The channel of the image's row y, moved right by dx, with zero where nothing lands. */
    private static void movedRow(IntBuffer pixels, int width, int height, int channel, int dx,
                                 int y, int[] sourceRow, int[] row) {
        Arrays.fill(row, 0);
        if (y < 0 || y >= height)
            return;
        pixels.get(y * width, sourceRow);
        for (int x = Math.max(0, dx); x < Math.min(width, width + dx); x++)
            row[x] = sourceRow[x - dx] >>> 8 * channel & 0xFF;
    }

    /** Puts the blurred plane in one channel of ARGB32_Premultiplied pixels. */
    public static void setPlane(byte[] pixels, byte[] plane, int width, int height, int channel) {
        overRange(height, width, (from, to) -> {
            for (int i = from * width; i < to * width; i++)
                pixels[i * 4 + channel] = plane[i];
        });
    }

    public static void blurPlane(byte[] plane, int width, int height, double radius) {
        int alpha = radius <= 1e-5 ? (1 << APREC) - 1
                : (int) Math.round((1 << APREC) * (1 - Math.pow(2 * (1 / 255.0), 1 / radius)));
        byte[] rotated = new byte[width * height];
        overRange(height, width, (from, to) -> blurRows(plane, width, from, to, alpha));
        // qt_memrotate270: dest(x, height - 1 - y) = src(y, x).
        overRange(tiles(width), TILE * height, (from, to) -> {
            for (int tile = from; tile < to; tile++)
                for (int y0 = 0; y0 < height; y0 += TILE)
                    for (int x = tile * TILE, xEnd = Math.min(x + TILE, width); x < xEnd; x++)
                        for (int y = y0, yEnd = Math.min(y0 + TILE, height); y < yEnd; y++)
                            rotated[x * height + height - 1 - y] = plane[y * width + x];
        });
        overRange(width, height, (from, to) -> blurRows(rotated, height, from, to, alpha));
        // qt_memrotate90: dest(height - 1 - x, y) = src(y, x), over the rotated plane.
        overRange(tiles(width), TILE * height, (from, to) -> {
            for (int tile = from; tile < to; tile++)
                for (int x0 = 0; x0 < height; x0 += TILE)
                    for (int y = tile * TILE, yEnd = Math.min(y + TILE, width); y < yEnd; y++)
                        for (int x = x0, xEnd = Math.min(x0 + TILE, height); x < xEnd; x++)
                            plane[(height - 1 - x) * width + y] = rotated[y * height + x];
        });
    }

    private static int tiles(int count) {
        return (count + TILE - 1) / TILE;
    }

    private interface Range {
        void run(int from, int to);
    }

    private static void overRange(int count, int pixelsPerUnit, Range range) {
        int chunks = (int) Math.min(threadCount, (long) count * pixelsPerUnit / CHUNK_PIXELS);
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
            for (Future<Void> chunk : rowPool.invokeAll(chunked))
                chunk.get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to blur the shadow", e);
        }
    }

    /**
     * qt_blurrow over rows [from, to): a forward then a backward pass, the filter state carrying
     * between them. Four rows go at once because the state is a serial multiply-add chain that
     * leaves the pipeline waiting, and rows do not depend on each other.
     */
    private static void blurRows(byte[] plane, int width, int from, int to, int alpha) {
        int row = from;
        for (; row + 4 <= to; row += 4) {
            int a = row * width, b = a + width, c = b + width, d = c + width;
            int za = 0, zb = 0, zc = 0, zd = 0;
            for (int i = 0; i < width; i++) {
                za += alpha * (((plane[a + i] & 0xFF) << ZPREC) - (za >> APREC));
                zb += alpha * (((plane[b + i] & 0xFF) << ZPREC) - (zb >> APREC));
                zc += alpha * (((plane[c + i] & 0xFF) << ZPREC) - (zc >> APREC));
                zd += alpha * (((plane[d + i] & 0xFF) << ZPREC) - (zd >> APREC));
                plane[a + i] = (byte) (za >> (ZPREC + APREC));
                plane[b + i] = (byte) (zb >> (ZPREC + APREC));
                plane[c + i] = (byte) (zc >> (ZPREC + APREC));
                plane[d + i] = (byte) (zd >> (ZPREC + APREC));
            }
            for (int i = width - 2; i >= 0; i--) {
                za += alpha * (((plane[a + i] & 0xFF) << ZPREC) - (za >> APREC));
                zb += alpha * (((plane[b + i] & 0xFF) << ZPREC) - (zb >> APREC));
                zc += alpha * (((plane[c + i] & 0xFF) << ZPREC) - (zc >> APREC));
                zd += alpha * (((plane[d + i] & 0xFF) << ZPREC) - (zd >> APREC));
                plane[a + i] = (byte) (za >> (ZPREC + APREC));
                plane[b + i] = (byte) (zb >> (ZPREC + APREC));
                plane[c + i] = (byte) (zc >> (ZPREC + APREC));
                plane[d + i] = (byte) (zd >> (ZPREC + APREC));
            }
        }
        for (; row < to; row++)
            blurRow(plane, row * width, width, alpha);
    }

    private static void blurRow(byte[] plane, int rowStart, int width, int alpha) {
        int z = 0;
        int end = rowStart + width;
        for (int at = rowStart; at < end; at++) {
            z += alpha * (((plane[at] & 0xFF) << ZPREC) - (z >> APREC));
            plane[at] = (byte) (z >> (ZPREC + APREC));
        }
        for (int at = end - 2; at >= rowStart; at--) {
            z += alpha * (((plane[at] & 0xFF) << ZPREC) - (z >> APREC));
            plane[at] = (byte) (z >> (ZPREC + APREC));
        }
    }
}

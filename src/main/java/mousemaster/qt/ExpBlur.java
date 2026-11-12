// Copyright (C) 2016 The Qt Company Ltd.
// SPDX-License-Identifier: LGPL-3.0-only
//
// A Java port, made in 2026, of expblur, qt_blurrow, qt_blurImage and qt_halfScaled, from
// qtbase/src/gui/painting/qimageeffects.cpp at Qt 6.8.2. Upstream is available under
// LicenseRef-Qt-Commercial OR LGPL-3.0-only OR GPL-2.0-only OR GPL-3.0-only; this port elects
// LGPL-3.0-only. See THIRD-PARTY-NOTICES.md.
package mousemaster.qt;

import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * Qt's exponential blur, ported so it can run off the GUI thread and across cores. Qt's own blur
 * goes through QPixmap, which is GUI-thread only and silently produces garbage anywhere else.
 *
 * <p>The blur is a pair of one-dimensional passes, along the rows and then down the columns, and
 * every row or column of a pass is independent of the others, so spreading them over threads
 * computes exactly what one thread would. Each channel is blurred on its own, in place in the
 * pixels of the blurred image.
 */
public final class ExpBlur {

    /** expblur's fixed-point precisions, as qt_blurImage instantiates them. */
    private static final int APREC = 12, ZPREC = 10;

    private ExpBlur() {
    }

    /**
     * An ARGB32_Premultiplied image moved by (dx, dy), halved when scale is 2 as qt_halfScaled
     * halves it, each value the average of a 2x2 block rounded down, and blurred: the pixels of an
     * ARGB32_Premultiplied image, or of an Alpha8 image of its alpha when alphaOnly. The image
     * comes in chunks of chunkHeight rows, the last one shorter.
     */
    public static byte[] blur(List<IntBuffer> chunks, int chunkHeight, int width, int height,
                              int dx, int dy, int scale, double radius, boolean alphaOnly) {
        int blurWidth = width / scale, blurHeight = height / scale;
        int channels = alphaOnly ? 1 : 4, firstChannel = 4 - channels;
        // QImage starts each row on a multiple of 4 bytes.
        int stride = (blurWidth * channels + 3) / 4 * 4;
        int alpha = radius <= 1e-5 ? (1 << APREC) - 1
                : (int) Math.round((1 << APREC) * (1 - Math.pow(2 * (1 / 255.0), 1 / radius)));
        byte[] pixels = new byte[stride * blurHeight];
        ImageChunks.overRange(blurHeight, width * channels, (from, to) -> {
            int[] top = new int[width];
            int[] bottom = new int[width];
            for (int y = from; y < to; y++) {
                movedRow(chunks, chunkHeight, width, height, dx, scale * y - dy, top);
                if (scale == 2)
                    movedRow(chunks, chunkHeight, width, height, dx, scale * y + 1 - dy, bottom);
                for (int x = 0, at = y * stride; x < blurWidth; x++) {
                    int pixel = scale == 1 ? top[x] :
                            average(average(top[2 * x], top[2 * x + 1]),
                                    average(bottom[2 * x], bottom[2 * x + 1]));
                    for (int channel = firstChannel; channel < 4; channel++)
                        pixels[at++] = (byte) (pixel >>> 8 * channel);
                }
            }
            blurRows(pixels, stride, blurWidth, channels, from, to, alpha);
        });
        ImageChunks.overRange(blurWidth, blurHeight * channels, (from, to) ->
                blurColumns(pixels, stride, blurHeight, from * channels, to * channels, alpha));
        return pixels;
    }

    /** The image's row y, moved right by dx, with zero where nothing lands. */
    private static void movedRow(List<IntBuffer> chunks, int chunkHeight, int width, int height,
                                 int dx, int y, int[] row) {
        Arrays.fill(row, 0);
        if (y < 0 || y >= height)
            return;
        chunks.get(y / chunkHeight).get(y % chunkHeight * width + Math.max(0, -dx), row,
                Math.max(0, dx), width - Math.abs(dx));
    }

    /** qt_halfScaled's AVG: each channel of the two pixels averaged, rounded down. */
    private static int average(int pixel, int otherPixel) {
        return (((pixel ^ otherPixel) & 0xFEFEFEFE) >>> 1) + (pixel & otherPixel);
    }

    /**
     * qt_blurrow down columns [from, to), as expblur runs it over the image qt_memrotate270
     * rotates: from the bottom up, then back down, the filter state carrying between them.
     */
    private static void blurColumns(byte[] pixels, int stride, int height, int from, int to,
                                    int alpha) {
        int[] states = new int[to - from];
        for (int y = height - 1; y >= 0; y--)
            blurColumnsAt(pixels, y * stride, from, to, alpha, states);
        for (int y = 1; y < height; y++)
            blurColumnsAt(pixels, y * stride, from, to, alpha, states);
    }

    private static void blurColumnsAt(byte[] pixels, int rowStart, int from, int to, int alpha,
                                      int[] states) {
        for (int x = from; x < to; x++) {
            int z = states[x - from];
            z += alpha * (((pixels[rowStart + x] & 0xFF) << ZPREC) - (z >> APREC));
            pixels[rowStart + x] = (byte) (z >> (ZPREC + APREC));
            states[x - from] = z;
        }
    }

    /**
     * qt_blurrow over rows [from, to) of each channel: a forward then a backward pass, the filter
     * state carrying between them. Four go at once, the channels of a row or four rows of alpha,
     * because the state is a serial multiply-add chain that leaves the pipeline waiting, and they
     * do not depend on each other.
     */
    private static void blurRows(byte[] pixels, int stride, int width, int channels, int from,
                                 int to, int alpha) {
        int row = from;
        if (channels == 4) {
            for (; row < to; row++)
                blurFour(pixels, row * stride, 1, 4, width, alpha);
            return;
        }
        for (; row + 4 <= to; row += 4)
            blurFour(pixels, row * stride, stride, 1, width, alpha);
        for (; row < to; row++)
            blurRow(pixels, row * stride, width, alpha);
    }

    /** qt_blurrow over four rows of count values, step apart, the rows starting gap apart. */
    private static void blurFour(byte[] pixels, int start, int gap, int step, int count,
                                 int alpha) {
        int a = start, b = a + gap, c = b + gap, d = c + gap;
        int za = 0, zb = 0, zc = 0, zd = 0;
        for (int i = 0; i < count * step; i += step) {
            za += alpha * (((pixels[a + i] & 0xFF) << ZPREC) - (za >> APREC));
            zb += alpha * (((pixels[b + i] & 0xFF) << ZPREC) - (zb >> APREC));
            zc += alpha * (((pixels[c + i] & 0xFF) << ZPREC) - (zc >> APREC));
            zd += alpha * (((pixels[d + i] & 0xFF) << ZPREC) - (zd >> APREC));
            pixels[a + i] = (byte) (za >> (ZPREC + APREC));
            pixels[b + i] = (byte) (zb >> (ZPREC + APREC));
            pixels[c + i] = (byte) (zc >> (ZPREC + APREC));
            pixels[d + i] = (byte) (zd >> (ZPREC + APREC));
        }
        for (int i = (count - 2) * step; i >= 0; i -= step) {
            za += alpha * (((pixels[a + i] & 0xFF) << ZPREC) - (za >> APREC));
            zb += alpha * (((pixels[b + i] & 0xFF) << ZPREC) - (zb >> APREC));
            zc += alpha * (((pixels[c + i] & 0xFF) << ZPREC) - (zc >> APREC));
            zd += alpha * (((pixels[d + i] & 0xFF) << ZPREC) - (zd >> APREC));
            pixels[a + i] = (byte) (za >> (ZPREC + APREC));
            pixels[b + i] = (byte) (zb >> (ZPREC + APREC));
            pixels[c + i] = (byte) (zc >> (ZPREC + APREC));
            pixels[d + i] = (byte) (zd >> (ZPREC + APREC));
        }
    }

    private static void blurRow(byte[] pixels, int rowStart, int width, int alpha) {
        int z = 0;
        int end = rowStart + width;
        for (int at = rowStart; at < end; at++) {
            z += alpha * (((pixels[at] & 0xFF) << ZPREC) - (z >> APREC));
            pixels[at] = (byte) (z >> (ZPREC + APREC));
        }
        for (int at = end - 2; at >= rowStart; at--) {
            z += alpha * (((pixels[at] & 0xFF) << ZPREC) - (z >> APREC));
            pixels[at] = (byte) (z >> (ZPREC + APREC));
        }
    }
}

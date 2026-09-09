package com.specskart.catalog;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

/**
 * Zero-dependency background remover for product shots taken on a plain, light
 * backdrop — the usual eyewear catalogue photo. Flood-fills from the image
 * borders and drops every pixel that is BOTH close to the sampled backdrop
 * colour AND connected to the edge, so a white glare on a lens in the middle of
 * the frame is kept while the surrounding sweep is cut.
 *
 * It is not a segmentation model: on a busy or dark background it will remove
 * little, and the caller should let the user upload a hand-made cut-out instead.
 *
 * ponytail: flood-fill chroma key. Swap for an ONNX matting model only if the
 * product photos stop being shot on plain backdrops.
 */
final class BackgroundKeyer {

    private BackgroundKeyer() {}

    /** RGB euclidean-distance tolerance for "this pixel is the backdrop". Tuneable per photo. */
    static final int DEFAULT_TOLERANCE = 44;

    /** @return a new ARGB image with the connected border background made transparent. */
    static BufferedImage keyOut(BufferedImage src, int tolerance) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.getGraphics().drawImage(src, 0, 0, null);
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);

        // Backdrop colour = mean of the four corner pixels.
        long r = 0, g = 0, b = 0;
        for (int[] c : new int[][]{{1, 1}, {w - 2, 1}, {1, h - 2}, {w - 2, h - 2}}) {
            int p = px[c[1] * w + c[0]];
            r += (p >> 16) & 0xff; g += (p >> 8) & 0xff; b += p & 0xff;
        }
        int br = (int) (r / 4), bg = (int) (g / 4), bb = (int) (b / 4);
        long tol2 = (long) tolerance * tolerance;

        boolean[] mask = new boolean[w * h]; // true = background
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int x = 0; x < w; x++) { seed(x, 0, w, px, mask, q, br, bg, bb, tol2); seed(x, h - 1, w, px, mask, q, br, bg, bb, tol2); }
        for (int y = 0; y < h; y++) { seed(0, y, w, px, mask, q, br, bg, bb, tol2); seed(w - 1, y, w, px, mask, q, br, bg, bb, tol2); }

        while (!q.isEmpty()) {
            int i = q.poll();
            int x = i % w, y = i / w;
            if (x + 1 < w) push(x + 1, y, w, px, mask, q, br, bg, bb, tol2);
            if (x - 1 >= 0) push(x - 1, y, w, px, mask, q, br, bg, bb, tol2);
            if (y + 1 < h) push(x, y + 1, w, px, mask, q, br, bg, bb, tol2);
            if (y - 1 >= 0) push(x, y - 1, w, px, mask, q, br, bg, bb, tol2);
        }

        for (int i = 0; i < px.length; i++) {
            if (mask[i]) { px[i] &= 0x00ffffff; continue; }
            int x = i % w, y = i / w, near = 0;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = x + dx, ny = y + dy;
                    if (nx >= 0 && ny >= 0 && nx < w && ny < h && mask[ny * w + nx]) near++;
                }
            }
            if (near >= 3) px[i] = (px[i] & 0x00ffffff) | (0x80 << 24); // 1px feather at the edge
        }
        img.setRGB(0, 0, w, h, px, 0, w);
        return img;
    }

    /** Crop to the bounding box of pixels with alpha &gt; 8, plus {@code margin} px of breathing room. */
    static BufferedImage autoCrop(BufferedImage img, int margin) {
        int w = img.getWidth(), h = img.getHeight();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int i = 0; i < px.length; i++) {
            if (((px[i] >>> 24) & 0xff) <= 8) continue;
            int x = i % w, y = i / w;
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        if (maxX < 0) return img; // fully transparent — nothing to crop to
        minX = Math.max(0, minX - margin);
        minY = Math.max(0, minY - margin);
        maxX = Math.min(w - 1, maxX + margin);
        maxY = Math.min(h - 1, maxY + margin);
        return img.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static void seed(int x, int y, int w, int[] px, boolean[] mask, ArrayDeque<Integer> q,
                             int br, int bg, int bb, long tol2) {
        push(x, y, w, px, mask, q, br, bg, bb, tol2);
    }

    private static void push(int x, int y, int w, int[] px, boolean[] mask, ArrayDeque<Integer> q,
                             int br, int bg, int bb, long tol2) {
        int i = y * w + x;
        if (mask[i]) return;
        int p = px[i];
        int dr = ((p >> 16) & 0xff) - br, dg = ((p >> 8) & 0xff) - bg, db = (p & 0xff) - bb;
        if ((long) dr * dr + (long) dg * dg + (long) db * db > tol2) return;
        mask[i] = true;
        q.add(i);
    }
}

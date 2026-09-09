package com.specskart.catalog;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class BackgroundKeyerTest {

    @Test
    void keysOutTheConnectedBackdropButKeepsTheSubjectAndInteriorHighlights() {
        int n = 60;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(new Color(245, 245, 245)); // near-white backdrop
        g.fillRect(0, 0, n, n);
        g.setColor(new Color(20, 20, 25));    // dark "frame"
        g.fillRect(15, 20, 30, 18);
        g.setColor(Color.WHITE);              // a glare fully inside the frame
        g.fillRect(25, 26, 6, 4);
        g.dispose();

        BufferedImage out = BackgroundKeyer.keyOut(img, BackgroundKeyer.DEFAULT_TOLERANCE);

        assertThat(alpha(out, 1, 1)).isZero();            // corner backdrop -> transparent
        assertThat(alpha(out, 30, 10)).isZero();          // backdrop above the frame -> transparent
        assertThat(alpha(out, 25, 30)).isEqualTo(255);    // frame body -> opaque
        assertThat(alpha(out, 27, 27)).isEqualTo(255);    // interior glare -> kept (not edge-connected)
    }

    @Test
    void autoCropShrinksToTheOpaqueBoundingBoxPlusMargin() {
        BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        for (int y = 12; y < 20; y++) {
            for (int x = 10; x < 30; x++) img.setRGB(x, y, 0xff112233);
        }

        BufferedImage cropped = BackgroundKeyer.autoCrop(img, 2);

        assertThat(cropped.getWidth()).isEqualTo(20 + 4);
        assertThat(cropped.getHeight()).isEqualTo(8 + 4);
    }

    private static int alpha(BufferedImage img, int x, int y) {
        return (img.getRGB(x, y) >>> 24) & 0xff;
    }
}

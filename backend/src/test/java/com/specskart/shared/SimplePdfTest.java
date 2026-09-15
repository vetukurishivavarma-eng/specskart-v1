package com.specskart.shared;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SimplePdfTest {

    @Test
    void everyXrefOffsetPointsAtItsObjectAcrossPageBreaks() {
        List<String> lines = new ArrayList<>();
        lines.add("# Order SK-TEST — 🛍️");
        for (int i = 0; i < 120; i++) lines.add("` R   -1.25   -0.50   90   (row " + i + ")");
        String s = new String(SimplePdf.render(lines), StandardCharsets.ISO_8859_1);

        assertThat(s).startsWith("%PDF-1.4").endsWith("%%EOF\n").contains("(Order SK-TEST -) Tj");
        Matcher count = Pattern.compile("/Count (\\d+)").matcher(s);
        assertThat(count.find()).isTrue();
        int pages = Integer.parseInt(count.group(1));
        assertThat(pages).isGreaterThan(1);

        int startxref = Integer.parseInt(s.substring(s.lastIndexOf("startxref\n") + 10, s.lastIndexOf("\n%%EOF")).trim());
        assertThat(s.substring(startxref)).startsWith("xref");
        Matcher off = Pattern.compile("(\\d{10}) 00000 n ").matcher(s.substring(startxref));
        int obj = 1;
        while (off.find()) {
            assertThat(s.substring(Integer.parseInt(off.group(1)))).startsWith(obj + " 0 obj");
            obj++;
        }
        assertThat(obj - 1).isEqualTo(5 + 2 * pages);
    }
}

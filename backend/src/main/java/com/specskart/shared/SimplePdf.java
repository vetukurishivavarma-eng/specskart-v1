package com.specskart.shared;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Tiny text-only A4 PDF writer for staff hand-outs (order slip, prescription card) — no library.
 * Line prefixes: "# " bold heading, "` " monospace (for the Rx table), anything else body text.
 * ponytail: Latin-1 only (emoji and typographic dashes are flattened/dropped), naive word wrap by
 * character count, no images or borders. Swap for OpenPDF if these ever need a logo or real tables.
 */
public final class SimplePdf {

    private static final int W = 595, H = 842, MARGIN = 50;

    private SimplePdf() {}

    public static byte[] render(List<String> lines) {
        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        int y = H - MARGIN;
        for (String raw : lines) {
            String font = "F1", text = raw;
            int size = 11, wrap = 90;
            if (raw.startsWith("# ")) { font = "F2"; size = 14; wrap = 70; text = raw.substring(2); }
            else if (raw.startsWith("` ")) { font = "F3"; size = 10; wrap = 82; text = raw.substring(2); }
            if (font.equals("F2") && y < H - MARGIN) y -= 8; // air above a heading
            for (String part : wrap(ascii(text), wrap)) {
                if (y < MARGIN + size) {
                    pages.add(page.toString());
                    page = new StringBuilder();
                    y = H - MARGIN;
                }
                page.append("BT /").append(font).append(' ').append(size).append(" Tf ")
                        .append(MARGIN).append(' ').append(y).append(" Td (").append(escape(part)).append(") Tj ET\n");
                y -= size + 5;
            }
        }
        pages.add(page.toString());

        int n = pages.size();
        List<String> objs = new ArrayList<>();
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < n; i++) kids.append(6 + 2 * i).append(" 0 R ");
        objs.add("<< /Type /Catalog /Pages 2 0 R >>");
        objs.add("<< /Type /Pages /Kids [" + kids + "] /Count " + n + " >>");
        objs.add(font("Helvetica"));
        objs.add(font("Helvetica-Bold"));
        objs.add(font("Courier"));
        for (int i = 0; i < n; i++) {
            objs.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + W + " " + H + "] "
                    + "/Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R >> >> /Contents " + (7 + 2 * i) + " 0 R >>");
            String s = pages.get(i);
            objs.add("<< /Length " + s.length() + " >>\nstream\n" + s + "endstream");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n");
        int[] offsets = new int[objs.size()];
        for (int i = 0; i < objs.size(); i++) {
            offsets[i] = out.size();
            write(out, (i + 1) + " 0 obj\n" + objs.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        StringBuilder x = new StringBuilder("xref\n0 " + (objs.size() + 1) + "\n0000000000 65535 f \n");
        for (int off : offsets) x.append(String.format("%010d 00000 n \n", off));
        x.append("trailer\n<< /Size ").append(objs.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        write(out, x.toString());
        return out.toByteArray();
    }

    private static String font(String name) {
        return "<< /Type /Font /Subtype /Type1 /BaseFont /" + name + " /Encoding /WinAnsiEncoding >>";
    }

    /** One byte per char from here on, so a stream's /Length is just its string length. */
    static String ascii(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '—' || c == '–' || c == '−' || c == '•') b.append('-');
            else if (c == '‘' || c == '’') b.append('\'');
            else if (c == '“' || c == '”') b.append('"');
            else if (c == '…') b.append("...");
            else if ((c >= 0x20 && c <= 0x7e) || (c >= 0xa0 && c <= 0xff)) b.append(c);
            // anything else (emoji, variation selectors) has no glyph in the base-14 fonts: dropped
        }
        return b.toString().strip();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static List<String> wrap(String s, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ", -1)) {
            while (word.length() > max) { // e.g. a long maps URL: hard-break it rather than run off the page
                if (line.length() > 0) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                out.add(word.substring(0, max));
                word = word.substring(max);
            }
            if (line.length() > 0 && line.length() + 1 + word.length() > max) {
                out.add(line.toString());
                line.setLength(0);
            } else if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        out.add(line.toString());
        return out;
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
    }
}

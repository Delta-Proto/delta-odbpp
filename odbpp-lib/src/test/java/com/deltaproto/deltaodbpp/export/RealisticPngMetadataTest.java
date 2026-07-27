package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@link MultiLayerSvgRenderer#renderRealisticJobPng} produces a self-describing
 * transparent PNG whose embedded metadata (pHYs + tEXt) matches delta-gerber's contract and
 * round-trips through the exact consumer parsing logic.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealisticPngMetadataTest {

    private Job job;

    @BeforeAll
    void loadSample(@TempDir Path tempDir) throws IOException {
        job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
    }

    private MultiLayerSvgRenderer newRenderer() {
        return new MultiLayerSvgRenderer(
                new SvgRenderOptions().withOutputUnit(SvgRenderOptions.OutputUnit.MM));
    }

    @Test
    void metadataChunksPresentBeforeIdat() throws IOException {
        byte[] png = renderTopPng(1200);
        Chunks c = readChunksBeforeIdat(png);

        // pHYs present, before IDAT, unit = 1 (metre), ppm == round(pxPerMm*1000), never a
        // default encoder value (72/96 dpi).
        assertTrue(c.hasPhys, "pHYs must be present before IDAT");
        assertEquals(1, c.physUnit, "pHYs unit specifier must be 1 (metre)");
        assertNotEquals(2835, c.physPpm, "pHYs must not be the 72-dpi encoder default");
        assertNotEquals(3780, c.physPpm, "pHYs must not be the 96-dpi encoder default");

        // tEXt keywords present before IDAT.
        assertTrue(c.text.containsKey("pxPerMm"), "tEXt pxPerMm must be present");
        assertTrue(c.text.containsKey("boardGeometryMm"), "tEXt boardGeometryMm must be present");
        assertTrue(c.text.containsKey("side"), "tEXt side must be present");
        assertEquals("top", c.text.get("side"));

        double pxPerMm = Double.parseDouble(c.text.get("pxPerMm"));
        assertTrue(pxPerMm > 0, "pxPerMm must be positive");
        assertEquals(Math.round(pxPerMm * 1000.0), c.physPpm,
                "pHYs ppm must equal round(pxPerMm*1000)");
    }

    @Test
    void mmRectEqualsSvgViewBox() throws IOException {
        byte[] png = renderTopPng(1000);
        Chunks c = readChunksBeforeIdat(png);
        JsonNode geo = new ObjectMapper().readTree(c.text.get("boardGeometryMm"));
        JsonNode mmRect = geo.get("mmRect");

        // The SVG viewBox for the same job/side is the source of truth for the mm frame.
        StringWriter sw = new StringWriter();
        newRenderer().renderRealisticJob(job, true, sw);
        double[] vb = parseViewBox(sw.toString());
        assertNotNull(vb, "realistic SVG must carry a viewBox");

        double tol = 1e-3; // mm
        assertEquals(vb[0], mmRect.get(0).asDouble(), tol, "mmRect.minX == viewBox minX");
        assertEquals(vb[1], mmRect.get(1).asDouble(), tol, "mmRect.minY == viewBox minY");
        assertEquals(vb[2], mmRect.get(2).asDouble(), tol, "mmRect.width == viewBox width");
        assertEquals(vb[3], mmRect.get(3).asDouble(), tol, "mmRect.height == viewBox height");

        // pxPerMm in the JSON is widthPx / mmRect.width.
        assertEquals(1000.0 / vb[2], geo.get("pxPerMm").asDouble(), 1e-3,
                "boardGeometryMm.pxPerMm == widthPx / mmRect.width");
        assertFalse(geo.get("mirrored").asBoolean(), "top side is not mirrored");
    }

    @Test
    void consumerContractRoundTrips() throws IOException {
        byte[] png = renderTopPng(1000);
        ParsedScale s = parseLikeConsumer(png);
        assertNotNull(s, "consumer parser must yield a scale");

        StringWriter sw = new StringWriter();
        newRenderer().renderRealisticJob(job, true, sw);
        double[] vb = parseViewBox(sw.toString());

        assertEquals(vb[0], s.minXmm, 1e-3);
        assertEquals(vb[1], s.minYmm, 1e-3);
        assertEquals(vb[2], s.widthMm, 1e-3);
        assertEquals(vb[3], s.heightMm, 1e-3);
        assertEquals(1000.0 / vb[2], s.pxPerMm, 1e-3);
        // originX must land inside the image.
        assertTrue(s.originX >= -1 && s.originX <= 1001, "origin X within image: " + s.originX);
    }

    @Test
    void bottomIsMirroredAndXFlippedVsTop() throws IOException {
        int width = 800;
        byte[] top = renderPng(true, width);
        byte[] bottom = renderPng(false, width);

        ParsedScale t = parseLikeConsumer(top);
        ParsedScale b = parseLikeConsumer(bottom);
        Chunks tc = readChunksBeforeIdat(top);
        Chunks bc = readChunksBeforeIdat(bottom);

        assertEquals("top", tc.text.get("side"));
        assertEquals("bottom", bc.text.get("side"));
        assertFalse(new ObjectMapper().readTree(tc.text.get("boardGeometryMm")).get("mirrored").asBoolean());
        assertTrue(new ObjectMapper().readTree(bc.text.get("boardGeometryMm")).get("mirrored").asBoolean(),
                "bottom side must be mirrored");

        // The X-mirror reflects the datum across the image's vertical centreline: the top and
        // bottom origin-X pixels are mirror images, so they sum to ~the image width.
        assertEquals(width, t.originX + b.originX, 1.0,
                "bottom origin X is the mirror of top's across the image width");

        // And the rasters genuinely differ (the mirror is applied to pixels, not just metadata).
        assertFalse(java.util.Arrays.equals(pixels(top), pixels(bottom)),
                "top and bottom rasters must differ");
    }

    @Test
    void pngIsTransparentRgba() throws IOException {
        byte[] png = renderTopPng(600);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(img);
        assertTrue(img.getColorModel().hasAlpha(), "PNG must carry an alpha channel");

        // A corner (in the padding margin around the outline) is fully transparent.
        int cornerAlpha = (img.getRGB(0, 0) >>> 24) & 0xFF;
        assertEquals(0, cornerAlpha, "board margin must be transparent (alpha 0)");

        // Somewhere the board geometry is drawn fully opaque (the outline needn't cover the
        // bounding-box centre for irregular/flex boards, so scan rather than sampling one point).
        int[] px = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        boolean anyOpaque = false, anyTransparent = false;
        for (int argb : px) {
            int a = (argb >>> 24) & 0xFF;
            // Batik anti-aliases the whole raster, so "opaque" interior tops out a hair below
            // 255; treat near-full alpha as opaque board geometry.
            if (a >= 250) anyOpaque = true;
            else if (a == 0) anyTransparent = true;
            if (anyOpaque && anyTransparent) break;
        }
        assertTrue(anyOpaque, "board geometry must be opaque somewhere");
        assertTrue(anyTransparent, "board margin must be transparent somewhere");
    }

    // ---- render helpers ----

    private byte[] renderTopPng(int widthPx) throws IOException {
        return renderPng(true, widthPx);
    }

    private byte[] renderPng(boolean topSide, int widthPx) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        newRenderer().renderRealisticJobPng(job, topSide, widthPx, out);
        return out.toByteArray();
    }

    private static int[] pixels(byte[] png) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    }

    // ---- PNG chunk reader ----

    private static final class Chunks {
        boolean hasPhys;
        long physPpm;
        int physUnit;
        final Map<String, String> text = new LinkedHashMap<>();
    }

    /** Read pHYs + tEXt chunks appearing before the first IDAT (fails if any come after). */
    private static Chunks readChunksBeforeIdat(byte[] png) {
        Chunks c = new Chunks();
        int pos = 8; // skip signature
        boolean seenIdat = false;
        while (pos + 8 <= png.length) {
            long len = readUInt32(png, pos);
            int dataStart = pos + 8;
            String type = new String(png, pos + 4, 4, StandardCharsets.US_ASCII);
            if ("IDAT".equals(type)) { seenIdat = true; break; }
            if ("pHYs".equals(type)) {
                c.hasPhys = true;
                c.physPpm = readUInt32(png, dataStart);
                c.physUnit = png[dataStart + 8] & 0xFF;
            } else if ("tEXt".equals(type)) {
                int end = (int) (dataStart + len);
                int nul = dataStart;
                while (nul < end && png[nul] != 0) nul++;
                String key = new String(png, dataStart, nul - dataStart, StandardCharsets.ISO_8859_1);
                String val = new String(png, nul + 1, end - nul - 1, StandardCharsets.ISO_8859_1);
                c.text.put(key, val);
            }
            pos = (int) (dataStart + len + 4);
        }
        assertTrue(seenIdat, "PNG must contain an IDAT chunk");
        return c;
    }

    // ---- port of the JS consumer parsing logic ----

    private record ParsedScale(double pxPerMm, double widthMm, double heightMm,
                               double minXmm, double minYmm, double originX) {}

    /**
     * Mirrors the frontend contract:
     * pxPerMm precedence tEXt pxPerMm &gt; boardGeometryMm.pxPerMm &gt; pHYs(ppm/1000, ignoring
     * 2835/3780); returns null if none yields pxPerMm &gt; 0.
     */
    private static ParsedScale parseLikeConsumer(byte[] png) throws IOException {
        Chunks c = readChunksBeforeIdat(png);
        JsonNode geo = c.text.containsKey("boardGeometryMm")
                ? new ObjectMapper().readTree(c.text.get("boardGeometryMm")) : null;

        double pxPerMm = 0;
        if (c.text.containsKey("pxPerMm")) {
            pxPerMm = safeDouble(c.text.get("pxPerMm"));
        }
        if (pxPerMm <= 0 && geo != null && geo.has("pxPerMm")) {
            pxPerMm = geo.get("pxPerMm").asDouble(0);
        }
        if (pxPerMm <= 0 && c.hasPhys && c.physPpm != 2835 && c.physPpm != 3780) {
            pxPerMm = c.physPpm / 1000.0;
        }
        if (pxPerMm <= 0) return null;

        double widthMm = 0, heightMm = 0, minXmm = 0, minYmm = 0, originX = 0;
        if (geo != null) {
            JsonNode r = geo.get("mmRect");
            if (r != null && r.size() == 4) {
                minXmm = r.get(0).asDouble();
                minYmm = r.get(1).asDouble();
                widthMm = r.get(2).asDouble();
                heightMm = r.get(3).asDouble();
            }
            JsonNode o = geo.get("originPx");
            if (o != null && o.size() == 2) originX = o.get(0).asDouble();
        }
        return new ParsedScale(pxPerMm, widthMm, heightMm, minXmm, minYmm, originX);
    }

    private static double safeDouble(String s) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
    }

    private static long readUInt32(byte[] buf, int off) {
        return ((long) (buf[off] & 0xFF) << 24) | ((buf[off + 1] & 0xFF) << 16)
                | ((buf[off + 2] & 0xFF) << 8) | (buf[off + 3] & 0xFF);
    }

    private static double[] parseViewBox(String svg) {
        int i = svg.indexOf("viewBox=\"");
        if (i < 0) return null;
        int start = i + "viewBox=\"".length();
        int end = svg.indexOf('"', start);
        String[] parts = svg.substring(start, end).trim().split("\\s+");
        return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]), Double.parseDouble(parts[3])};
    }
}

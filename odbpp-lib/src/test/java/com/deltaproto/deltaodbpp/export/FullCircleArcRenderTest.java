package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Features;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Layer;
import com.deltaproto.deltaodbpp.model.Matrix;
import com.deltaproto.deltaodbpp.model.MatrixLayer;
import com.deltaproto.deltaodbpp.model.Step;
import com.deltaproto.deltaodbpp.parser.FeaturesFileParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ODB++ writes a full circle as one arc whose start and end coincide (e.g. a plane's via
 * clearance as a single {@code OC} hole). SVG omits an arc with identical endpoints, so a naive
 * translation drops the hole and the plane renders solid. This renders such a surface through
 * the web viewer's path ({@link MultiLayerSvgRenderer#renderJob} with the viewer's options),
 * rasterises it with Batik and checks the hole is actually cut out.
 */
class FullCircleArcRenderTest {

    private static final String LAYER = "gnd";

    /**
     * A 10 x 10 mm POWER_GROUND island with a 2 mm-radius hole at its centre, written as a single
     * full-circle {@code OC} (start = end = (7, 5), centre (5, 5)), plus a full-circle arc feature
     * on the same circle.
     */
    private static final String FEATURES = String.join("\n",
            "UNITS=MM",
            "#",
            "#Feature symbol names",
            "#",
            "$0 r100",
            "#",
            "#Layer features",
            "#",
            "S P 0",
            "OB 0 0 I",
            "OS 0 10",
            "OS 10 10",
            "OS 10 0",
            "OS 0 0",
            "OE",
            "OB 7 5 H",
            "OC 7 5 5 5 Y",
            "OE",
            "SE",
            "");

    private static final String FEATURES_ARC = String.join("\n",
            "UNITS=MM",
            "$0 r100",
            "A 7 5 7 5 5 5 0 P 0 Y",
            "");

    @Test
    void fullCircleHole_isCutOutOfSurface(@TempDir Path tempDir) throws IOException {
        String svg = renderViewerSvg(parseFeatures(tempDir, FEATURES));

        int width = 400;
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(
                MultiLayerSvgRenderer.rasterizeSvgToPng(svg, width)));
        assertNotNull(img);
        double[] vb = parseViewBox(svg);

        // The viewer sets no background colour, so unpainted pixels stay fully transparent.
        assertBackground(img, vb, 5.0, 5.0, "hole centre");
        assertBackground(img, vb, 6.5, 5.0, "inside hole edge (r = 1.5)");
        assertBackground(img, vb, 5.0, 3.5, "inside hole edge, below centre");
        assertPainted(img, vb, 1.5, 5.0, "island, left of hole");
        assertPainted(img, vb, 8.0, 5.0, "island, just outside hole (r = 3)");
        assertPainted(img, vb, 5.0, 8.5, "island, above hole");

        // The hole is drawn as two semicircles via the point opposite the start, (3, 5).
        assertTrue(svg.contains("M 7.0000 5.0000 A 2.0000 2.0000 0 0 0 3.0000 5.0000"
                        + " A 2.0000 2.0000 0 0 0 7.0000 5.0000 Z"),
                "full-circle hole must be split into two arcs:\n" + svg);
    }

    @Test
    void fullCircleArcFeature_isDrawnAsTwoArcs(@TempDir Path tempDir) throws IOException {
        String svg = renderViewerSvg(parseFeatures(tempDir, FEATURES_ARC));
        assertTrue(svg.contains("d=\"M 7.0000 5.0000 A 2.0000 2.0000 0 0 0 3.0000 5.0000"
                        + " A 2.0000 2.0000 0 0 0 7.0000 5.0000\""),
                "full-circle arc feature must be split into two arcs:\n" + svg);
    }

    // ---- helpers ----

    private static Features parseFeatures(Path tempDir, String text) throws IOException {
        Path file = tempDir.resolve("features");
        Files.writeString(file, text, StandardCharsets.ISO_8859_1);
        return new FeaturesFileParser().parse(file);
    }

    /** Render a one-layer job exactly as the web viewer's upload endpoint does. */
    private static String renderViewerSvg(Features features) throws IOException {
        MatrixLayer ml = new MatrixLayer();
        ml.setRow(1);
        ml.setContext("BOARD");
        ml.setType("POWER_GROUND");
        ml.setName(LAYER);
        Matrix matrix = new Matrix();
        matrix.setLayers(List.of(ml));

        Layer layer = new Layer();
        layer.setFeatures(features);
        Map<String, Layer> layers = new LinkedHashMap<>();
        layers.put(LAYER, layer);

        Step step = new Step();
        step.setName("pcb");
        step.setLayersByName(layers);
        Map<String, Step> steps = new LinkedHashMap<>();
        steps.put("pcb", step);

        Job job = new Job();
        job.setMatrix(matrix);
        job.setSteps(steps);

        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer(
                new SvgRenderOptions().withOutputUnit(SvgRenderOptions.OutputUnit.MM));
        StringWriter sw = new StringWriter();
        renderer.renderJob(job, sw);
        return sw.toString();
    }

    private static double[] parseViewBox(String svg) {
        Matcher m = Pattern.compile("viewBox=\"([-\\d.]+) ([-\\d.]+) ([-\\d.]+) ([-\\d.]+)\"").matcher(svg);
        assertTrue(m.find(), "SVG must carry a viewBox");
        return new double[]{Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
                Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4))};
    }

    /**
     * Pixel for a model point (mm, Y up). The layer group flips Y about the bounds' centre, so
     * the user-space Y is {@code (vbMinY + vbMaxY) - y}; the viewBox then maps linearly onto
     * the image (the height follows the aspect ratio, so there is no letterboxing).
     */
    private static int argbAt(BufferedImage img, double[] vb, double x, double y) {
        double userY = (2 * vb[1] + vb[3]) - y;
        int px = (int) Math.floor((x - vb[0]) / vb[2] * img.getWidth());
        int py = (int) Math.floor((userY - vb[1]) / vb[3] * img.getHeight());
        return img.getRGB(px, py);
    }

    private static void assertBackground(BufferedImage img, double[] vb, double x, double y, String what) {
        int argb = argbAt(img, vb, x, y);
        assertEquals(0, (argb >>> 24) & 0xFF,
                what + " (" + x + ", " + y + ") must be unpainted, was #" + Integer.toHexString(argb));
    }

    private static void assertPainted(BufferedImage img, double[] vb, double x, double y, String what) {
        int argb = argbAt(img, vb, x, y);
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        // POWER_GROUND layers are filled #0000CC; allow for Batik's anti-aliasing.
        assertTrue(a >= 250 && r < 8 && g < 8 && Math.abs(b - 0xCC) < 8,
                what + " (" + x + ", " + y + ") must be painted #0000CC, was #" + Integer.toHexString(argb));
    }
}

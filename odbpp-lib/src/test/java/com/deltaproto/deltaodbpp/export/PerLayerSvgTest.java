package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Layer;
import com.deltaproto.deltaodbpp.model.Step;
import com.deltaproto.deltaodbpp.parser.OdbParser;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the per-layer SVG API: {@code renderLayerSvg} (full) and
 * {@code renderLayerPreviewSvg} (lightweight thumbnail).
 */
class PerLayerSvgTest {

    @TempDir
    Path tempDir;

    private static boolean isValidSvg(String svg) {
        return svg != null
                && svg.contains("<?xml")
                && svg.contains("<svg")
                && svg.trim().endsWith("</svg>")
                && svg.contains("xmlns=\"http://www.w3.org/2000/svg\"");
    }

    @Test
    void minimalOdb_topLayer_rendersValidNonEmptySvg() throws IOException {
        Job job = new OdbParser().parse(Fixtures.MINIMAL_ODB);
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer();

        String svg = renderer.renderLayerSvg(job, "pcb", "top");
        assertTrue(isValidSvg(svg), "top layer SVG must be well-formed");
        // minimal-odb 'top' has pads (circles) + lines/arc.
        assertTrue(svg.contains("<circle") || svg.contains("<path"),
                "top layer should contain geometry");
    }

    @Test
    void unknownLayer_returnsEmptySvg() throws IOException {
        Job job = new OdbParser().parse(Fixtures.MINIMAL_ODB);
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer();
        String svg = renderer.renderLayerSvg(job, "pcb", "does-not-exist");
        assertTrue(isValidSvg(svg));
        assertTrue(svg.contains("Empty design"), "missing layer should yield the empty SVG");
    }

    @Test
    void preview_isSmallerThanFull_forMinimalOdb() throws IOException {
        Job job = new OdbParser().parse(Fixtures.MINIMAL_ODB);
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer();

        String full = renderer.renderLayerSvg(job, "pcb", "top");
        String preview = renderer.renderLayerPreviewSvg(job, "pcb", "top");

        assertTrue(isValidSvg(full));
        assertTrue(isValidSvg(preview));
        assertTrue(preview.length() < full.length(),
                "preview (" + preview.length() + ") should be smaller than full ("
                        + full.length() + ")");
        assertTrue(preview.contains("data-preview=\"true\""),
                "preview should be marked data-preview");
    }

    @Test
    void preview_capsPixelDimensions() throws IOException {
        Job job = new OdbParser().parse(Fixtures.MINIMAL_ODB);
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer();
        String preview = renderer.renderLayerPreviewSvg(job, "pcb", "top");

        double[] wh = parseWidthHeight(preview);
        assertNotNull(wh, "preview must declare width/height");
        assertTrue(Math.max(wh[0], wh[1]) <= 256.5,
                "preview longest dimension must be capped near 256px, got "
                        + wh[0] + "x" + wh[1]);
    }

    @Test
    void everyLayerType_inMultilayerSample_rendersThroughThisPath() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer(
                new SvgRenderOptions().withOutputUnit(SvgRenderOptions.OutputUnit.MM));

        String stepName = findStepName(job);
        Step step = job.getSteps().get(stepName);
        int rendered = 0;
        int withGeometry = 0;
        for (Map.Entry<String, Layer> e : step.getLayersByName().entrySet()) {
            Layer layer = e.getValue();
            boolean hasFeatures = layer.getFeatures() != null
                    && !layer.getFeatures().getFeatures().isEmpty();

            String full = renderer.renderLayerSvg(job, stepName, e.getKey());
            assertTrue(isValidSvg(full), "layer " + e.getKey() + " must render valid SVG");
            rendered++;

            String preview = renderer.renderLayerPreviewSvg(job, stepName, e.getKey());
            assertTrue(isValidSvg(preview), "layer " + e.getKey() + " preview must be valid");

            if (hasFeatures) {
                // Layers with features must produce non-empty geometry.
                boolean hasGeom = full.contains("<circle") || full.contains("<path")
                        || full.contains("<text") || full.contains("<rect");
                if (hasGeom) withGeometry++;
                // Preview should not be larger than the full render.
                assertTrue(preview.length() <= full.length(),
                        "preview of " + e.getKey() + " should not exceed full render");
            }
        }
        assertTrue(rendered >= 5, "multilayer sample should have rendered many layers");
        assertTrue(withGeometry > 0, "at least some layers should carry geometry");
    }

    private static String findStepName(Job job) {
        return job.getSteps().keySet().iterator().next();
    }

    private static double[] parseWidthHeight(String svg) {
        Double w = parseAttr(svg, "width");
        Double h = parseAttr(svg, "height");
        if (w == null || h == null) return null;
        return new double[]{w, h};
    }

    private static Double parseAttr(String svg, String attr) {
        String needle = attr + "=\"";
        int i = svg.indexOf("<svg");
        if (i < 0) return null;
        int a = svg.indexOf(needle, i);
        if (a < 0) return null;
        int start = a + needle.length();
        int end = svg.indexOf('"', start);
        if (end < 0) return null;
        try {
            return Double.parseDouble(svg.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

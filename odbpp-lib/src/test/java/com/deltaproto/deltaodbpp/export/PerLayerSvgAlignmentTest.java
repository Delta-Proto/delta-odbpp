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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Per-layer SVGs of one step must all share a single coordinate frame, so a caller can stack
 * them and have the geometry register.
 *
 * <h2>What this guards</h2>
 * {@code MultiLayerSvgRenderer.renderLayerFromStep} used to frame each layer on that layer's
 * <em>own</em> bounds unioned with the profile. On a board where every layer fits inside the
 * outline, that happens to give every layer the profile's frame — until one layer reaches
 * outside it. A fabrication-drawing layer always does: its dimension and annotation lines sit
 * beyond the board edge. That layer alone then got a wider viewBox, and rendered at the same
 * on-screen size its board content shrank and slid sideways relative to every other layer.
 *
 * <p>{@link Fixtures#KICAD_FAB_OUTSIDE_PROFILE} is that board: 28 layers, all inside a
 * 40&nbsp;&times;&nbsp;60&nbsp;mm profile except {@code f.fab}, which reaches ~59&nbsp;mm left
 * and ~12&nbsp;mm right of it. Before the fix {@code f.fab} was framed
 * {@code [41.0362 -141.5158 110.9265 72.1319]} while its siblings got
 * {@code [99.9 -140.1 40.2 60.2]}.
 */
class PerLayerSvgAlignmentTest {

    /** Extent of the fab layer's annotations, in mm; the profile spans only x 100..140. */
    private static final double FAB_MIN_X = 41.136, FAB_MAX_X = 151.863;
    private static final double FAB_MIN_Y = -141.416, FAB_MAX_Y = -69.484;

    @TempDir
    Path tempDir;

    @Test
    void fabLayerReachingOutsideProfile_isFramedLikeItsSiblings() throws IOException {
        Job job = new OdbParser().parse(Fixtures.KICAD_FAB_OUTSIDE_PROFILE);
        MultiLayerSvgRenderer renderer = mmRenderer();

        double[] silkscreen = viewBox(renderer.renderLayerSvg(job, "pcb", "f.silkscreen"));
        double[] fab = viewBox(renderer.renderLayerSvg(job, "pcb", "f.fab"));

        assertNotNull(silkscreen, "f.silkscreen must declare a viewBox");
        assertNotNull(fab, "f.fab must declare a viewBox");
        assertArrayEquals(silkscreen, fab, 1e-6,
                "f.fab must share the frame of a layer that fits inside the profile; "
                        + "f.silkscreen=" + fmt(silkscreen) + " f.fab=" + fmt(fab));
    }

    @Test
    void everyNonEmptyLayer_sharesOneFrame() throws IOException {
        assertAllLayersShareOneFrame(new OdbParser().parse(Fixtures.KICAD_FAB_OUTSIDE_PROFILE));
    }

    @Test
    void sharedFrame_coversGeometryOutsideTheProfile() throws IOException {
        Job job = new OdbParser().parse(Fixtures.KICAD_FAB_OUTSIDE_PROFILE);
        double[] vb = viewBox(mmRenderer().renderLayerSvg(job, "pcb", "f.cu"));

        assertNotNull(vb);
        // Alignment must not be bought by cropping to the profile: the fab layer's annotations
        // have to stay inside the shared frame.
        assertTrue(vb[0] <= FAB_MIN_X,
                "frame must start at or left of x=" + FAB_MIN_X + ", was x=" + vb[0]);
        assertTrue(vb[0] + vb[2] >= FAB_MAX_X,
                "frame must extend to at least x=" + FAB_MAX_X + ", ended at x=" + (vb[0] + vb[2]));
        assertTrue(vb[1] <= FAB_MIN_Y,
                "frame must reach y=" + FAB_MIN_Y + ", started at y=" + vb[1]);
        assertTrue(vb[1] + vb[3] >= FAB_MAX_Y,
                "frame must reach y=" + FAB_MAX_Y + ", ended at y=" + (vb[1] + vb[3]));
    }

    @Test
    void preview_sharesTheFullRenderFrame() throws IOException {
        Job job = new OdbParser().parse(Fixtures.KICAD_FAB_OUTSIDE_PROFILE);
        MultiLayerSvgRenderer renderer = mmRenderer();

        for (String layer : List.of("f.cu", "f.fab")) {
            double[] full = viewBox(renderer.renderLayerSvg(job, "pcb", layer));
            double[] preview = viewBox(renderer.renderLayerPreviewSvg(job, "pcb", layer));
            assertArrayEquals(full, preview, 1e-6,
                    "preview of " + layer + " must use the full render's viewBox");
        }
    }

    @Test
    void openSample_everyNonEmptyLayerSharesOneFrame() throws IOException {
        assumeTrue(Fixtures.hasSample(Fixtures.MULTILAYER_SAMPLE));
        assertAllLayersShareOneFrame(Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir));
    }

    // ------------------------------------------------------------------

    private void assertAllLayersShareOneFrame(Job job) throws IOException {
        MultiLayerSvgRenderer renderer = mmRenderer();
        String stepName = job.getSteps().keySet().iterator().next();
        Step step = job.getSteps().get(stepName);

        Map<String, double[]> frames = new LinkedHashMap<>();
        for (Map.Entry<String, Layer> e : step.getLayersByName().entrySet()) {
            Layer layer = e.getValue();
            if (layer.getFeatures() == null || layer.getFeatures().getFeatures().isEmpty()) {
                continue; // empty layers render the placeholder SVG, which has no board frame
            }
            double[] vb = viewBox(renderer.renderLayerSvg(job, stepName, e.getKey()));
            assertNotNull(vb, "layer " + e.getKey() + " must declare a viewBox");
            frames.put(e.getKey(), vb);
        }
        assertTrue(frames.size() >= 2, "need at least two non-empty layers to compare");

        Map.Entry<String, double[]> first = frames.entrySet().iterator().next();
        List<String> mismatched = new ArrayList<>();
        for (Map.Entry<String, double[]> e : frames.entrySet()) {
            if (!sameFrame(first.getValue(), e.getValue())) {
                mismatched.add(e.getKey() + "=" + fmt(e.getValue()));
            }
        }
        assertTrue(mismatched.isEmpty(),
                "every layer must share " + first.getKey() + "'s frame " + fmt(first.getValue())
                        + " but these differ: " + mismatched);
    }

    private static boolean sameFrame(double[] a, double[] b) {
        for (int i = 0; i < 4; i++) {
            if (Math.abs(a[i] - b[i]) > 1e-6) return false;
        }
        return true;
    }

    private static MultiLayerSvgRenderer mmRenderer() {
        return new MultiLayerSvgRenderer(
                new SvgRenderOptions().withOutputUnit(SvgRenderOptions.OutputUnit.MM));
    }

    /** The four viewBox numbers (min-x, min-y, width, height), or null if there is no viewBox. */
    private static double[] viewBox(String svg) {
        if (svg == null) return null;
        int i = svg.indexOf("viewBox=\"");
        if (i < 0) return null;
        int start = i + "viewBox=\"".length();
        int end = svg.indexOf('"', start);
        if (end < 0) return null;
        String[] parts = svg.substring(start, end).trim().split("[\\s,]+");
        if (parts.length != 4) return null;
        double[] vb = new double[4];
        for (int k = 0; k < 4; k++) {
            vb[k] = Double.parseDouble(parts[k]);
        }
        return vb;
    }

    private static String fmt(double[] vb) {
        return String.format(Locale.US, "[%.4f %.4f %.4f %.4f]", vb[0], vb[1], vb[2], vb[3]);
    }
}

package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Features;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Layer;
import com.deltaproto.deltaodbpp.model.Line;
import com.deltaproto.deltaodbpp.model.Matrix;
import com.deltaproto.deltaodbpp.model.MatrixLayer;
import com.deltaproto.deltaodbpp.model.Step;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When only one PNG dimension is requested, the other is derived from the SVG viewBox aspect. A
 * degenerate viewBox — zero width or height, as produced by a point-like / collinear board profile
 * with no padding — would leave the missing dimension 0 and make Batik fail with an opaque error.
 * {@code renderRealisticSidePngWithScale} must instead fail fast with a clear message.
 */
class RealisticDegenerateExtentTest {

    @Test
    void degenerateViewBox_throwsClearException() {
        // A horizontal-line profile has zero height; with padding forced to 0 the viewBox height
        // collapses to 0, so deriving height from a requested width is impossible.
        SvgRenderOptions options = new SvgRenderOptions()
                .withOutputUnit(SvgRenderOptions.OutputUnit.MM);
        options.setPadding(0.0);
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer(options);

        Job job = degenerateJob();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> renderer.renderRealisticSidePngWithScale(job, true, 800, 0));
        assertTrue(ex.getMessage().contains("no renderable extent"),
                "message should name the degenerate extent, was: " + ex.getMessage());
    }

    /**
     * A job whose profile is a single horizontal line (zero height) plus a copper layer, so the
     * realistic renderer produces a valid non-empty SVG that nonetheless has a zero-height viewBox.
     */
    private static Job degenerateJob() {
        Matrix matrix = new Matrix();
        MatrixLayer top = new MatrixLayer();
        top.setRow(1);
        top.setContext("BOARD");
        top.setType("SIGNAL");
        top.setName("top");
        matrix.setLayers(List.of(top));

        // Profile: a single horizontal line from (0,0) to (10,0) — non-empty path, zero height.
        Features profile = new Features();
        Line line = new Line();
        line.setXs(0.0);
        line.setYs(0.0);
        line.setXe(10.0);
        line.setYe(0.0);
        profile.getFeatures().add(line);

        // Copper layer needs at least one feature or the renderer skips it and bails empty.
        Features copperFeatures = new Features();
        copperFeatures.getSymbolTable().put(0, "r100");
        com.deltaproto.deltaodbpp.model.Pad copperPad = new com.deltaproto.deltaodbpp.model.Pad();
        copperPad.setX(5.0);
        copperPad.setY(0.0);
        copperPad.setSymbolNumber(0);
        copperPad.setPolarity("P");
        copperFeatures.getFeatures().add(copperPad);
        Layer copper = new Layer();
        copper.setFeatures(copperFeatures);

        Step step = new Step();
        step.setName("pcb");
        step.setProfile(profile);
        Map<String, Layer> layers = new LinkedHashMap<>();
        layers.put("top", copper);
        step.setLayersByName(layers);

        Map<String, Step> steps = new LinkedHashMap<>();
        steps.put("pcb", step);

        Job job = new Job();
        job.setMatrix(matrix);
        job.setSteps(steps);
        return job;
    }
}

package com.deltaproto.deltaodbpp.spec;

import com.deltaproto.deltaodbpp.model.Features;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Layer;
import com.deltaproto.deltaodbpp.model.Matrix;
import com.deltaproto.deltaodbpp.model.MatrixLayer;
import com.deltaproto.deltaodbpp.model.Pad;
import com.deltaproto.deltaodbpp.model.Step;
import com.deltaproto.deltaodbpp.spec.dfm.DrillHole;
import com.deltaproto.deltaodbpp.spec.dfm.PastePad;
import com.deltaproto.deltaodbpp.spec.dfm.ViaInPadDetector;
import com.deltaproto.deltaodbpp.spec.dfm.ViaInPadResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused via-in-pad tests with exact ground truth: the geometry is built in memory (either the
 * detector's inputs directly, or a small hand-built {@link Job}), so the expected hits are known.
 */
class ViaInPadTest {

    // ---- Detector-level: containment geometry -------------------------------------------------

    @Test
    void holeInsideRoundPadIsAVia() {
        // A 1 mm round top pad at (5,5); a hole at its centre is inside, a hole 2 mm away is not.
        PastePad pad = new PastePad(PastePad.Shape.ROUND, 5, 5, 1.0, 1.0, 0, true, false);
        List<PastePad> pads = List.of(pad);
        List<DrillHole> holes = List.of(
                new DrillHole(5.0, 5.0, 0.3),   // dead centre → via
                new DrillHole(7.0, 5.0, 0.3));  // 2 mm away → not in the 0.5 mm-radius pad

        ViaInPadResult r = ViaInPadDetector.detect(pads, holes);
        assertTrue(r.hasViaInPad());
        assertEquals(1, r.getCount());
        assertTrue(r.isOnTop());
        assertFalse(r.isOnBottom());
        assertEquals(5.0, r.getViaInPads().get(0).getXMm(), 1e-9);
    }

    @Test
    void holeJustOutsideRoundPadEdgeIsNotAVia() {
        // 1 mm round pad → 0.5 mm radius; a hole at 0.6 mm from centre is outside.
        PastePad pad = new PastePad(PastePad.Shape.ROUND, 0, 0, 1.0, 1.0, 0, true, false);
        ViaInPadResult r = ViaInPadDetector.detect(List.of(pad),
                List.of(new DrillHole(0.6, 0.0, 0.2)));
        assertFalse(r.hasViaInPad());
        assertEquals(0, r.getCount());
    }

    @Test
    void rectPadRespectsRotation() {
        // A 2x0.5 mm rectangle rotated 90° becomes 0.5 wide x 2 tall.
        PastePad pad = new PastePad(PastePad.Shape.RECT, 0, 0, 2.0, 0.5, 90, false, true);
        // (0, 0.8) is inside the rotated (tall) pad but outside the un-rotated (wide) one.
        ViaInPadResult in = ViaInPadDetector.detect(List.of(pad),
                List.of(new DrillHole(0.0, 0.8, 0.2)));
        assertTrue(in.hasViaInPad());
        assertTrue(in.isOnBottom());
        // (0.8, 0) is outside the rotated pad (only 0.25 mm half-width now).
        ViaInPadResult out = ViaInPadDetector.detect(List.of(pad),
                List.of(new DrillHole(0.8, 0.0, 0.2)));
        assertFalse(out.hasViaInPad());
    }

    @Test
    void emptyInputsGiveEmptyResult() {
        assertFalse(ViaInPadDetector.detect(List.of(), List.of(new DrillHole(0, 0, 0.2)))
                .hasViaInPad());
        assertFalse(ViaInPadDetector.detect(
                List.of(new PastePad(PastePad.Shape.ROUND, 0, 0, 1, 1, 0, true, false)), List.of())
                .hasViaInPad());
    }

    // ---- Analyzer-level: end-to-end on a hand-built Job ---------------------------------------

    @Test
    void analyzerFindsViaInPadUnderPasteWithDrill() {
        Job job = buildJob(/*drillInside*/ true);
        BoardSpecification spec = new OdbAnalyzer().analyze(job, "pcb");
        assertEquals(Boolean.TRUE, spec.hasViaInPad());
        assertEquals(1, spec.getViaInPadCount());
        assertEquals(BoardSide.TOP, spec.getViaInPadSide());
    }

    @Test
    void analyzerReportsNoViaWhenDrillMissesThePad() {
        Job job = buildJob(/*drillInside*/ false);
        BoardSpecification spec = new OdbAnalyzer().analyze(job, "pcb");
        assertEquals(Boolean.FALSE, spec.hasViaInPad());
        assertEquals(0, spec.getViaInPadCount());
        assertEquals(BoardSide.NONE, spec.getViaInPadSide());
    }

    @Test
    void analyzerLeavesViaUndeterminedWithoutPaste() {
        Job job = buildJob(true);
        // Remove the paste layer from both matrix and step → cannot judge via-in-pad.
        job.getMatrix().getLayers().removeIf(l -> "SOLDER_PASTE".equals(l.getType()));
        job.getSteps().get("pcb").getLayersByName().remove("spt");
        BoardSpecification spec = new OdbAnalyzer().analyze(job, "pcb");
        assertNull(spec.hasViaInPad());
    }

    @Test
    void naSidePasteCountsForBothSides() {
        // A paste layer named so it classifies as NA (no side clue) still carries a via-in-pad,
        // and the side attribution is conservative: the pad matches whether the drill was called
        // out as top or bottom, so hasViaInPad stays TRUE regardless.
        Job job = buildJob(/*drillInside*/ true);
        // Rename the paste layer to a name that yields LayerSide.NA (no top/bot clue).
        job.getMatrix().getLayers().stream()
                .filter(l -> "SOLDER_PASTE".equals(l.getType()))
                .forEach(l -> l.setName("paste"));
        Map<String, Layer> layers = job.getSteps().get("pcb").getLayersByName();
        layers.put("paste", layers.remove("spt"));

        BoardSpecification spec = new OdbAnalyzer().analyze(job, "pcb");
        assertEquals(Boolean.TRUE, spec.hasViaInPad());
        assertEquals(1, spec.getViaInPadCount());
        // NA paste is attributed to both sides, so the via reports on both.
        assertTrue(spec.getViaInPadSide() == BoardSide.BOTH,
                "NA-side via-in-pad should report on both sides, was " + spec.getViaInPadSide());
    }

    @Test
    void analyzerDerivesMinDrillFromHoleSymbolsWithoutToolsFile() {
        // A drill layer with no tools file, whose holes are flashed hole<d> symbols. The analyzer
        // must recognise the HOLE family and derive min drill from it (previously it saw null).
        Matrix matrix = new Matrix();
        List<MatrixLayer> mls = new ArrayList<>();
        mls.add(matrixLayer(1, "SIGNAL", "top"));
        mls.add(matrixLayer(2, "DRILL", "drill"));
        mls.add(matrixLayer(3, "SIGNAL", "bot"));
        matrix.setLayers(mls);

        Map<String, Layer> layers = new LinkedHashMap<>();
        layers.put("top", copperLayer());
        layers.put("drill", holeSymbolDrillLayer());
        layers.put("bot", copperLayer());

        Step step = new Step();
        step.setName("pcb");
        step.setLayersByName(layers);
        com.deltaproto.deltaodbpp.model.StepHdr hdr = new com.deltaproto.deltaodbpp.model.StepHdr();
        hdr.setUnits("MM"); // symbol dims are microns → hole300 = 0.3 mm
        step.setStepHdr(hdr);

        Map<String, Step> steps = new LinkedHashMap<>();
        steps.put("pcb", step);
        Job job = new Job();
        job.setMatrix(matrix);
        job.setSteps(steps);

        BoardSpecification spec = new OdbAnalyzer().analyze(job, "pcb");
        assertTrue(spec.hasDrill());
        assertNotNull(spec.getMinDrillDiameterMm(), "hole symbols should drive min drill");
        // holes are hole300 (0.3 mm) and hole500 (0.5 mm) → min 0.3 mm, attributed plated.
        assertEquals(0.3, spec.getMinDrillDiameterMm(), 1e-9);
        assertEquals(0.3, spec.getMinPlatedDrillMm(), 1e-9);
    }

    private static Layer holeSymbolDrillLayer() {
        Layer layer = new Layer();
        Features f = new Features();
        f.setUnits("MM");
        f.getSymbolTable().put(0, "hole300");     // 0.3 mm plated hole
        f.getSymbolTable().put(1, "hole500x1x0x0"); // 0.5 mm, full spec form
        Pad h1 = new Pad();
        h1.setX(5.0);
        h1.setY(5.0);
        h1.setSymbolNumber(0);
        h1.setPolarity("P");
        f.getFeatures().add(h1);
        Pad h2 = new Pad();
        h2.setX(6.0);
        h2.setY(6.0);
        h2.setSymbolNumber(1);
        h2.setPolarity("P");
        f.getFeatures().add(h2);
        layer.setFeatures(f);
        return layer;
    }

    /**
     * A minimal two-copper-layer board with a top paste pad (round, 1 mm, at 10,10) and a drill
     * layer with one hole either inside that pad (10,10) or well away from it (30,30).
     */
    private static Job buildJob(boolean drillInside) {
        Matrix matrix = new Matrix();
        List<MatrixLayer> mls = new ArrayList<>();
        mls.add(matrixLayer(1, "SIGNAL", "top"));
        mls.add(matrixLayer(2, "SOLDER_PASTE", "spt"));
        mls.add(matrixLayer(3, "DRILL", "drill"));
        mls.add(matrixLayer(4, "SIGNAL", "bot"));
        matrix.setLayers(mls);

        Map<String, Layer> layers = new LinkedHashMap<>();
        layers.put("top", copperLayer());
        layers.put("spt", pasteLayer());
        layers.put("drill", drillLayer(drillInside ? 10.0 : 30.0, drillInside ? 10.0 : 30.0));
        layers.put("bot", copperLayer());

        Step step = new Step();
        step.setName("pcb");
        step.setLayersByName(layers);
        com.deltaproto.deltaodbpp.model.StepHdr hdr = new com.deltaproto.deltaodbpp.model.StepHdr();
        hdr.setUnits("MM"); // symbol dims (r1000) are microns → 1 mm pad
        step.setStepHdr(hdr);

        Map<String, Step> steps = new LinkedHashMap<>();
        steps.put("pcb", step);

        Job job = new Job();
        job.setMatrix(matrix);
        job.setSteps(steps);
        return job;
    }

    private static MatrixLayer matrixLayer(int row, String type, String name) {
        MatrixLayer ml = new MatrixLayer();
        ml.setRow(row);
        ml.setContext("BOARD");
        ml.setType(type);
        ml.setName(name);
        return ml;
    }

    private static Layer copperLayer() {
        Layer layer = new Layer();
        Features f = new Features();
        f.setUnits("MM");
        layer.setFeatures(f);
        return layer;
    }

    private static Layer pasteLayer() {
        Layer layer = new Layer();
        Features f = new Features();
        f.setUnits("MM");
        f.getSymbolTable().put(0, "r1000"); // 1 mm round pad (microns for UNITS=MM)
        Pad pad = new Pad();
        pad.setX(10.0);
        pad.setY(10.0);
        pad.setSymbolNumber(0);
        pad.setPolarity("P");
        f.getFeatures().add(pad);
        layer.setFeatures(f);
        return layer;
    }

    private static Layer drillLayer(double x, double y) {
        Layer layer = new Layer();
        Features f = new Features();
        f.setUnits("MM");
        f.getSymbolTable().put(0, "r300"); // 0.3 mm hole
        Pad hole = new Pad();
        hole.setX(x);
        hole.setY(y);
        hole.setSymbolNumber(0);
        hole.setPolarity("P");
        f.getFeatures().add(hole);
        layer.setFeatures(f);
        return layer;
    }
}

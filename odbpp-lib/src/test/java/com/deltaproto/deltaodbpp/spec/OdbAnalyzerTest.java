package com.deltaproto.deltaodbpp.spec;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.parser.OdbParser;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exact-assertion unit tests for {@link OdbAnalyzer} against the committed synthetic
 * {@code minimal-odb} fixture, whose contents are known:
 *
 * <ul>
 *   <li>profile = 100 mm x 80 mm rectangle;</li>
 *   <li>one copper layer {@code top} (SIGNAL, row 1);</li>
 *   <li>tracks stroked with {@code r1000} (1 mm) and {@code r2000} (2 mm) — min track 1000 µm;</li>
 *   <li>no drill, mask, silk, paste, components or BOM.</li>
 * </ul>
 */
class OdbAnalyzerTest {

    private static BoardSpecification spec;

    @BeforeAll
    static void analyze() throws IOException {
        Job job = new OdbParser().parse(Fixtures.MINIMAL_ODB);
        spec = new OdbAnalyzer().analyze(job);
        assertNotNull(spec);
    }

    @Test
    void picksTheBoardStep() {
        assertEquals("pcb", spec.getStepName());
    }

    @Test
    void boardSizeFromProfile() {
        assertNotNull(spec.getSizeXMm());
        assertNotNull(spec.getSizeYMm());
        assertEquals(100.0, spec.getSizeXMm(), 1e-6);
        assertEquals(80.0, spec.getSizeYMm(), 1e-6);
        assertTrue(spec.hasProfile());
        assertNotNull(spec.getBounds());
        assertEquals(0.0, spec.getBounds().getMinX(), 1e-6);
        assertEquals(100.0, spec.getBounds().getMaxX(), 1e-6);
    }

    @Test
    void copperLayerCount() {
        assertEquals(1, spec.getCopperLayerCount());
        assertTrue(spec.hasCopper());
    }

    @Test
    void minTrackWidthIsSmallestRoundStroke() {
        // r1000 = 1000 microns = 1 mm = 1000 µm; the r2000 stroke is wider and doesn't win.
        assertNotNull(spec.getMinTrackWidthUm());
        assertEquals(1000.0, spec.getMinTrackWidthUm(), 1e-3);
    }

    @Test
    void noDrillMeansNullDrillAndFalseFlag() {
        assertFalse(spec.hasDrill());
        assertNull(spec.getMinDrillDiameterMm());
        assertNull(spec.getMinPlatedDrillMm());
        assertNull(spec.getMinNonPlatedDrillMm());
    }

    @Test
    void noMaskSilkPasteMeansNone() {
        assertEquals(BoardSide.NONE, spec.getSolderMaskSide());
        assertEquals(BoardSide.NONE, spec.getSilkscreenSide());
        assertEquals(BoardSide.NONE, spec.getStencilSide());
    }

    @Test
    void viaInPadUndeterminedWithoutPasteOrDrill() {
        // No paste layer and no drill → cannot judge via-in-pad, so it is null (not FALSE).
        assertNull(spec.hasViaInPad());
        assertNull(spec.getViaInPadSide());
        assertEquals(0, spec.getViaInPadCount());
    }

    @Test
    void extrasAreNullWhenAbsent() {
        assertNull(spec.getTotalThicknessMm());
        assertNull(spec.getImpedanceControl());
        assertNull(spec.getComponentCountTop());
        assertNull(spec.getComponentCountBottom());
        assertNull(spec.getBomLineCount());
    }

    @Test
    void oneCopperLayerAnalyzed() {
        assertEquals(1, spec.getLayers().size());
        AnalyzedLayer top = spec.getLayers().get(0);
        assertEquals("top", top.getName());
        assertEquals("SIGNAL", top.getType());
        assertEquals(LayerSide.TOP, top.getSide());
        assertEquals(Integer.valueOf(1), top.getMatrixRow());
        assertTrue(Boolean.TRUE.equals(top.getHasGeometry()));
        assertNotNull(top.getMinTrackWidthUm());
        assertEquals(1000.0, top.getMinTrackWidthUm(), 1e-3);
        assertNull(top.getMinDrillDiameterMm());
    }

    @Test
    void analyzingAMissingStepYieldsEmptySpec() {
        Job job;
        try {
            job = new OdbParser().parse(Fixtures.MINIMAL_ODB);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        BoardSpecification empty = new OdbAnalyzer().analyze(job, "does-not-exist");
        assertNull(empty.getSizeXMm());
        assertNull(empty.getCopperLayerCount());
        assertFalse(empty.hasCopper());
        assertTrue(empty.getLayers().isEmpty());
    }
}

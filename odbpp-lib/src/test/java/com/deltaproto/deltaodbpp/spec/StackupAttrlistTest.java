package com.deltaproto.deltaodbpp.spec;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.parser.OdbParser;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the stackup source that archives in the wild actually carry: the per-layer
 * {@code attrlist} files and the product model's {@code .board_thickness}.
 *
 * <p>{@code matrix/stackup.xml} is optional and, in practice, close to nonexistent — the writer
 * behind most ODB++ in circulation does not emit one. What it does emit is
 * {@code .layer_dielectric}, {@code .dielectric_constant}, {@code .loss_tangent}, {@code .comment}
 * and {@code .copper_weight} per layer, which is a real stackup and is what these tests pin.
 *
 * <p>The awkward parts are the point of the fixture: an attribute that means the adjacent layer on
 * copper, a sentinel value on layers the writer had nothing for, a loss tangent of zero standing in
 * for "not filled in", a dielectric row with no type, and units that are stated in one archive and
 * merely implied in another.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StackupAttrlistTest {

    private BoardSpecification spec;
    private List<StackupLayer> stack;

    @BeforeAll
    void analyze() throws IOException {
        Job job = new OdbParser().parse(Fixtures.STACKUP_ATTRLIST_MM);
        spec = new OdbAnalyzer().analyze(job);
        stack = spec.getStackup();
    }

    private StackupLayer named(String name) {
        return stack.stream()
                .filter(l -> name.equals(l.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no stackup entry named " + name));
    }

    // ------------------------------------------------------------------------
    // Dielectrics — the layers the attrlist actually describes
    // ------------------------------------------------------------------------

    @Test
    void dielectric_takesThicknessDkDfAndMaterialFromItsAttrlist() {
        StackupLayer prepreg = named("dielectric_1");
        assertEquals(0.0994, prepreg.getThicknessMm(), 1e-9);
        assertTrue(prepreg.isThicknessMeasured());
        assertEquals(4.3, prepreg.getDielectricConstant().doubleValue());
        assertFalse(prepreg.isDielectricConstantEstimated());
        assertEquals(0.013, prepreg.getLossTangent().doubleValue());
        assertFalse(prepreg.isLossTangentEstimated());
        assertEquals("PP-1080", prepreg.getMaterial());
        assertFalse(prepreg.isMaterialEstimated());
    }

    @Test
    void dielectric_thicknessIsExactInPicometres() {
        assertEquals(99_400_000L, named("dielectric_1").getThicknessPm());
        assertEquals(1_200_000_000L, named("dielectric_2").getThicknessPm());
    }

    /**
     * A written {@code .loss_tangent = 0} is an unset field, not a lossless laminate — no dielectric
     * has zero loss. It must not reach a caller as a measured 0.
     */
    @Test
    void lossTangentOfZero_isTreatedAsUnsetNotAsMeasuredZero() {
        StackupLayer core = named("dielectric_2");
        assertEquals(0.02, core.getLossTangent().doubleValue(), "falls back to the FR-4 typical");
        assertTrue(core.isLossTangentEstimated(), "and says the number is not the archive's");

        // Its neighbours on the same layer are still the archive's own.
        assertTrue(core.isThicknessMeasured());
        assertFalse(core.isDielectricConstantEstimated());
    }

    /** A DIELECTRIC row with no DIELECTRIC_TYPE is still a dielectric; the type was never needed. */
    @Test
    void dielectricWithNoType_isResolvedLikeAnyOther() {
        StackupLayer untyped = named("dielectric_3");
        assertTrue(untyped.isDielectric());
        assertTrue(untyped.isThicknessMeasured());
        assertEquals(0.0994, untyped.getThicknessMm(), 1e-9);
    }

    /** With no {@code .comment} on the layer, the material name falls back to the matrix's own. */
    @Test
    void materialName_fallsBackFromAttrlistToTheMatrixRow() {
        StackupLayer untyped = named("dielectric_3");
        assertEquals("PP-1080", untyped.getMaterial());
        assertFalse(untyped.isMaterialEstimated(), "the matrix's DIELECTRIC_NAME is archive data too");
    }

    // ------------------------------------------------------------------------
    // The attribute means different things on different layers
    // ------------------------------------------------------------------------

    /**
     * Copper layers carry a {@code .layer_dielectric} holding the <em>adjacent</em> dielectric's
     * thickness. Reading it as the copper's own would make a 35 µm foil 99 µm or 1.2 mm thick.
     */
    @Test
    void copperLayer_doesNotTakeItsThicknessFromLayerDielectric() {
        for (String name : List.of("top", "gnd", "pwr", "bot")) {
            StackupLayer copper = named(name);
            assertTrue(copper.isThicknessEstimated(),
                    name + " has no copper thickness of its own in the archive");
            assertNotEquals(0.0994, copper.getThicknessMm(), 1e-9);
            assertNotEquals(1.2, copper.getThicknessMm(), 1e-9);
        }
        assertEquals(0.035, named("top").getThicknessMm(), 1e-9, "outer copper typical");
        assertEquals(0.018, named("gnd").getThicknessMm(), 1e-9, "inner copper typical");
    }

    @Test
    void copperLayer_takesItsWeightFromCopperWeight() {
        assertEquals(1.42851959, named("top").getCopperWeightOz().doubleValue());
        assertFalse(named("top").isCopperWeightEstimated());
        assertEquals(1.0, named("gnd").getCopperWeightOz().doubleValue());
        assertFalse(named("gnd").isCopperWeightEstimated());
    }

    @Test
    void solderMask_takesItsOwnThicknessDkAndMaterial() {
        StackupLayer mask = named("top_mask");
        assertEquals(0.025, mask.getThicknessMm(), 1e-9);
        assertTrue(mask.isThicknessMeasured());
        assertEquals(3.9, mask.getDielectricConstant().doubleValue());
        assertEquals("Solder Resist", mask.getMaterial());
        assertFalse(mask.isMaterialEstimated());
    }

    /**
     * Silk and paste carry a sentinel the writer parks there when it has no value — 2.54 µm, which
     * is no stencil and no legend anyone ever printed. Taking it at face value would report a
     * placeholder as measured.
     */
    @Test
    void sentinelThickness_isRefusedInFavourOfATypical() {
        for (String name : List.of("top_silk", "bot_silk", "top_paste")) {
            StackupLayer l = named(name);
            assertTrue(l.isThicknessEstimated(), name + " states no usable thickness");
            assertEquals(0.005, l.getThicknessMm(), 1e-9);
        }
    }

    // ------------------------------------------------------------------------
    // Board thickness
    // ------------------------------------------------------------------------

    @Test
    void totalThickness_comesFromBoardThickness() {
        assertEquals(1.55, spec.getTotalThicknessMm(), 1e-9);
    }

    /** The declared total and the stack it describes should agree to within a rounding fudge. */
    @Test
    void totalThickness_agreesWithTheStackItDescribes() {
        double summed = 0;
        for (StackupLayer l : stack) {
            summed += l.getThicknessMm() == null ? 0 : l.getThicknessMm();
        }
        assertEquals(spec.getTotalThicknessMm(), summed, 0.05,
                "declared board thickness and the resolved stack should be in the same place");
    }

    /**
     * An archive with no {@code UNITS} line anywhere is imperial, per the spec's own default, and
     * its {@code .board_thickness} is in inches — 0.0492126 in being a 1.25 mm board. Guessing
     * millimetres here would quote a 0.05 mm board.
     */
    @Test
    void boardThickness_readsAsInchesWhenTheArchiveNeverSaysUnits(@TempDir Path tempDir)
            throws IOException {
        Job job = Fixtures.parseSample(Fixtures.SMALL_SAMPLE, tempDir);
        assertEquals(1.25, new OdbAnalyzer().analyze(job).getTotalThicknessMm(), 1e-6);
    }

    /**
     * A writer that follows the spec's {@code MIL_MICRON} rule literally writes 1570 under
     * {@code UNITS=MM}. Read as millimetres that is a 1.57 metre board, so the micron reading is the
     * only one that can be meant.
     */
    @Test
    void boardThickness_fallsBackToTheSpecUnitWhenBaseUnitsAreImpossible() throws IOException {
        Job job = new OdbParser().parse(Fixtures.STACKUP_BOARD_THICKNESS_MICRON);
        assertEquals(1.57, new OdbAnalyzer().analyze(job).getTotalThicknessMm(), 1e-9);
    }

    // ------------------------------------------------------------------------
    // Shape
    // ------------------------------------------------------------------------

    @Test
    void stack_ordersTheWholeBuildAndDropsTheDrill() {
        assertEquals(List.of("top_paste", "top_silk", "top_mask", "top", "dielectric_1", "gnd",
                        "dielectric_2", "pwr", "dielectric_3", "bot", "bot_mask", "bot_silk"),
                stack.stream().map(StackupLayer::getName).toList());
        for (int i = 0; i < stack.size(); i++) {
            assertEquals(i, stack.get(i).getOrdinal());
        }
    }

    /** The drill row is gone from the stack but still analyzed, span and all. */
    @Test
    void drillSpan_survivesOnTheAnalyzedLayers() {
        AnalyzedLayer drill = spec.getLayers().stream()
                .filter(l -> "DRILL".equals(l.getType()))
                .findFirst()
                .orElseThrow();
        assertEquals("top", drill.getStartName());
        assertEquals("bot", drill.getEndName());
    }
}

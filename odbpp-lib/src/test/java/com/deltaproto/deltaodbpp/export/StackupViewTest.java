package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.stackup.StackupFile;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import com.deltaproto.deltaodbpp.testutil.StackupFixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link StackupView} from two directions.
 *
 * <p>Against the committed multilayer sample (designodb rigid-flex) it asserts structural
 * invariants — non-empty stack, top-to-bottom ordering, conductor/dielectric presence, exclusion of
 * drill/component/document layers — rather than specific thickness values or exact row counts. That
 * sample ships no stackup.xml, as most archives do not, but it does carry per-layer {@code attrlist}
 * attributes, so what it stands for is a stack resolved from those; {@code StackupAttrlistTest}
 * covers that source in its own right.
 *
 * <p>Against hand-built jobs (see {@link StackupFixtures}) it pins the three states a stackup file
 * can be in: fully populated, absent, and partially populated — the case that decides whether a
 * caller can tell a stated thickness from an invented one.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StackupViewTest {

    private List<StackupView.Entry> stackup;

    @BeforeAll
    void load(@TempDir Path tempDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        stackup = StackupView.build(job);
    }

    @Test
    void build_returnsAtLeastOneEntry() {
        assertNotNull(stackup);
        assertFalse(stackup.isEmpty(),
                "a multilayer board should yield at least one stackup row");
    }

    @Test
    void build_isOrderedTopToBottomByStructure() {
        // The build orders entries by matrix row, so the first conductor we meet is
        // an outer/top conductor and the last is an outer/bottom conductor.
        int firstConductor = -1;
        int lastConductor = -1;
        for (int i = 0; i < stackup.size(); i++) {
            if (stackup.get(i).conductor) {
                if (firstConductor < 0) firstConductor = i;
                lastConductor = i;
            }
        }
        assertTrue(firstConductor >= 0, "stackup should contain at least one conductor");
        assertTrue(lastConductor >= firstConductor);
        assertEquals("TOP", stackup.get(firstConductor).side,
                "first conductor in the stack should be the top side");
        assertEquals("BOTTOM", stackup.get(lastConductor).side,
                "last conductor in the stack should be the bottom side");
    }

    @Test
    void build_containsConductorLayers() {
        long conductors = stackup.stream().filter(e -> e.conductor).count();
        assertTrue(conductors >= 2,
                "a multilayer board should have at least two conductor layers, got " + conductors);
        for (StackupView.Entry e : stackup) {
            if (e.conductor) {
                assertEquals("Copper", e.material, e.name + " conductor should be Copper");
                assertNotNull(e.copperWeightOz, e.name + " conductor should carry a copper weight");
                assertTrue(e.copperWeightOz > 0);
                assertTrue(e.thicknessMm > 0, e.name + " conductor should have positive thickness");
            }
        }
    }

    @Test
    void build_innerConductorsClassifiedAsInner() {
        long inner = stackup.stream()
                .filter(e -> e.conductor && "INNER".equals(e.side))
                .count();
        assertTrue(inner >= 1,
                "a board with >2 conductor layers should have inner conductors, got " + inner);
    }

    /**
     * This sample states {@code .copper_weight} on its copper layers, so the weights are the
     * archive's own rather than the 1 oz / 0.5 oz typicals — which is the whole point of reading the
     * attrlist. The typicals are asserted where they do apply, in
     * {@code stackupAbsent_fallsBackToTypicalsAndSaysSo} below.
     */
    @Test
    void build_conductorWeightsComeFromTheArchiveWhenItStatesThem() {
        for (StackupView.Entry e : stackup) {
            if (e.conductor) {
                assertNotNull(e.copperWeightOz, e.name + " should carry a copper weight");
                assertTrue(e.copperWeightOz > 0.1 && e.copperWeightOz < 10,
                        e.name + " weight should be a plausible foil, got " + e.copperWeightOz);
            }
        }
        // Not every layer the same, and none of them the 0.5 oz inner typical.
        assertTrue(stackup.stream().filter(e -> e.conductor)
                        .anyMatch(e -> e.copperWeightOz != 0.5),
                "the archive's own weights should be showing through");
    }

    @Test
    void build_containsDielectricLayers() {
        long dielectrics = stackup.stream().filter(e -> e.dielectric).count();
        assertTrue(dielectrics >= 1,
                "a multilayer board should have at least one dielectric layer");
        for (StackupView.Entry e : stackup) {
            if (e.dielectric) {
                assertEquals("DIELECTRIC", e.type);
                assertNotNull(e.dielectricConstant, e.name + " dielectric should carry a Dk");
                assertNotNull(e.lossTangent, e.name + " dielectric should carry a Df");
                assertNotNull(e.material);
                assertFalse(e.material.isBlank(), e.name + " dielectric should name a material");
            }
        }
    }

    @Test
    void build_drillsAndComponentsAndDocuments_excluded() {
        for (StackupView.Entry e : stackup) {
            assertNotEquals("DRILL", e.type, "DRILL " + e.name + " leaked into stackup");
            assertNotEquals("ROUT", e.type, "ROUT " + e.name + " leaked into stackup");
            assertNotEquals("COMPONENT", e.type, "COMPONENT " + e.name + " leaked into stackup");
            assertNotEquals("DOCUMENT", e.type, "DOCUMENT " + e.name + " leaked into stackup");
        }
    }

    @Test
    void totalThickness_isPositive() {
        double total = StackupView.totalThicknessMm(stackup);
        assertTrue(total > 0,
                "total stackup thickness should be positive, got " + total + " mm");
    }

    // ------------------------------------------------------------------------
    // stackup.xml present, absent, and half-answered
    // ------------------------------------------------------------------------

    /** 1 mil is exactly 25 400 000 pm, so a nominal mil thickness lands on a whole picometre count. */
    private static long mils(double mils) {
        return Math.round(mils * 25_400_000L);
    }

    private static StackupView.Entry entry(List<StackupView.Entry> stack, String name) {
        return stack.stream()
                .filter(e -> name.equals(e.name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no stackup entry named " + name));
    }

    @Test
    void stackupPresent_usesTheFilesOwnValues(@TempDir Path tempDir) throws IOException {
        StackupFile stackup = StackupFixtures.parseStackup(StackupFixtures.fullStackupXml(), tempDir);
        List<StackupView.Entry> stack = StackupView.build(
                StackupFixtures.job(StackupFixtures.fourLayerMatrix(), stackup));

        for (StackupView.Entry e : stack) {
            assertFalse(e.estimated, e.name + " should carry the file's own thickness");
        }

        StackupView.Entry top = entry(stack, "top");
        assertEquals(mils(1.4), top.thicknessPm.longValue(), "top copper is the 1.4 mil the file states");
        assertEquals(1.0, top.copperWeightOz.doubleValue());
        assertEquals("1.0 oz CU", top.material);

        // The inner copper is heavier than the outers here — the opposite of the typicals, which is
        // the point: a real file must beat the guess.
        StackupView.Entry gnd = entry(stack, "gnd");
        assertEquals(mils(2.8), gnd.thicknessPm.longValue());
        assertEquals(2.0, gnd.copperWeightOz.doubleValue());

        StackupView.Entry core = entry(stack, "dielectric_2");
        assertEquals(mils(8), core.thicknessPm.longValue());
        assertEquals(4.5, core.dielectricConstant.doubleValue());
        assertEquals(0.017, core.lossTangent.doubleValue());
        assertEquals("FR4 Core", core.material);

        StackupView.Entry mask = entry(stack, "smt");
        assertEquals(mils(0.6), mask.thicknessPm.longValue());
        assertEquals(3.9, mask.dielectricConstant.doubleValue());
    }

    @Test
    void stackupAbsent_fallsBackToTypicalsAndSaysSo(@TempDir Path tempDir) {
        List<StackupView.Entry> stack = StackupView.build(
                StackupFixtures.job(StackupFixtures.fourLayerMatrix(), null));

        for (StackupView.Entry e : stack) {
            assertTrue(e.estimated, e.name + " has no stated thickness and must be flagged estimated");
        }

        StackupView.Entry top = entry(stack, "top");
        assertEquals(1.0, top.copperWeightOz.doubleValue(), "outer copper defaults to 1 oz");
        assertEquals(0.035, top.thicknessMm, 1e-9);
        assertEquals("Copper", top.material);

        StackupView.Entry gnd = entry(stack, "gnd");
        assertEquals(0.5, gnd.copperWeightOz.doubleValue(), "inner copper defaults to 0.5 oz");

        // Every dielectric gets the same typical: the library does not guess core from prepreg.
        assertEquals(0.2, entry(stack, "dielectric_1").thicknessMm, 1e-9);
        assertEquals(0.2, entry(stack, "dielectric_2").thicknessMm, 1e-9);
        assertEquals(4.3, entry(stack, "dielectric_1").dielectricConstant.doubleValue(), "FR-4 typical Dk");
    }

    @Test
    void stackupPartiallyPopulated_mixesStatedAndTypicalPerValue(@TempDir Path tempDir)
            throws IOException {
        StackupFile stackup = StackupFixtures.parseStackup(StackupFixtures.partialStackupXml(), tempDir);
        List<StackupView.Entry> stack = StackupView.build(
                StackupFixtures.job(StackupFixtures.fourLayerMatrix(), stackup));

        // Stated: the two outer copper layers, in millimetres this time.
        StackupView.Entry top = entry(stack, "top");
        assertFalse(top.estimated);
        assertEquals(34_800_000L, top.thicknessPm.longValue());
        assertEquals("Foil 1oz", top.material);

        // The core is named and its Dk/Df are stated, but its thickness is not — so the thickness is
        // a typical and the entry says so, while Dk and Df are the file's own.
        StackupView.Entry core = entry(stack, "dielectric_2");
        assertTrue(core.estimated, "the file states no thickness for the core");
        assertEquals(0.2, core.thicknessMm, 1e-9);
        assertEquals(4.06, core.dielectricConstant.doubleValue());
        assertEquals(0.021, core.lossTangent.doubleValue());
        assertEquals("Core 370HR", core.material);

        // Never mentioned by the file at all.
        StackupView.Entry gnd = entry(stack, "gnd");
        assertTrue(gnd.estimated);
        assertEquals(0.5, gnd.copperWeightOz.doubleValue());
        assertEquals("Copper", gnd.material);
    }

    @Test
    void everyEntryAgreesWithItselfOnThickness(@TempDir Path tempDir) throws IOException {
        StackupFile stackup = StackupFixtures.parseStackup(StackupFixtures.fullStackupXml(), tempDir);
        for (StackupView.Entry e : StackupView.build(
                StackupFixtures.job(StackupFixtures.fourLayerMatrix(), stackup))) {
            assertNotNull(e.thicknessPm, e.name + " should carry a picometre thickness");
            assertEquals(e.thicknessPm / 1_000_000_000.0, e.thicknessMm, 1e-12,
                    e.name + " states two thicknesses that disagree");
        }
    }
}

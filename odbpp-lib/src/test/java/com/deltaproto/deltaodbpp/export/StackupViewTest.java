package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Job;
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
 * Sanity-checks {@link StackupView} against the committed multilayer sample
 * (designodb rigid-flex).
 *
 * <p>The sample ships no stackup.xml, so the view is derived from the matrix
 * alone with industry-default thicknesses. Tests assert structural invariants
 * (non-empty stack, top-to-bottom ordering, conductor/dielectric presence,
 * exclusion of drill/component/document layers) rather than specific thickness
 * values or exact row counts.
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
        for (StackupView.Entry e : stackup) {
            if (e.conductor && "INNER".equals(e.side)) {
                assertEquals(0.5, e.copperWeightOz,
                        e.name + " inner conductor should default to 0.5 oz");
            }
        }
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
}

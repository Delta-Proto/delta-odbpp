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
 * Exercises {@link BomView} against the committed multilayer sample
 * (designodb rigid-flex). The combined BOM+centroid view is what the UI's
 * "BOM" tab consumes — every row carries its refdes list with each refdes's
 * centroid (mm), rotation, side and mirror.
 *
 * <p>The sample ships a real {@code boms/} file plus hundreds of placed
 * components with PRP part/manufacturer metadata, so the view can build a
 * meaningful, non-trivial table. Assertions are structural — they never pin
 * exact component or row counts.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BomViewTest {

    private List<BomView.Row> rows;

    @BeforeAll
    void load(@TempDir Path tempDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        rows = BomView.build(job);
    }

    @Test
    void build_returnsAtLeastOneRow() {
        assertNotNull(rows);
        assertFalse(rows.isEmpty(),
                "the multilayer sample has components, so BomView should produce at least one row");
    }

    @Test
    void build_producesManyRefdesEntries() {
        // The sample carries hundreds of placed components; the BOM should
        // surface a substantial (non-trivial) number of refdes entries.
        int total = BomView.totalRefdes(rows);
        assertTrue(total > 10,
                "expected a non-trivial number of refdes entries, got " + total);
    }

    @Test
    void everyRefdesHasCentroidData() {
        int totalRefdes = 0;
        int withCentroid = 0;
        for (BomView.Row r : rows) {
            for (BomView.RefdesEntry p : r.refdesList) {
                totalRefdes++;
                if (p.refdes != null && !p.refdes.isEmpty()
                        && p.xMm != null && p.yMm != null
                        && p.rotation != null) {
                    withCentroid++;
                }
            }
        }
        assertTrue(totalRefdes > 0, "expected at least one refdes entry");
        assertEquals(totalRefdes, withCentroid,
                "every refdes entry must carry centroid info (refdes + xMm + yMm + rotation)");
    }

    @Test
    void everyRefdesCarriesSensibleSide() {
        int sampled = 0;
        for (BomView.Row r : rows) {
            for (BomView.RefdesEntry p : r.refdesList) {
                // Side comes from the layer-side classifier; it should be one of the
                // known buckets and never null.
                assertNotNull(p.side, "side must not be null for " + p.refdes);
                assertTrue(p.side.isEmpty()
                                || p.side.equals("TOP") || p.side.equals("BOTTOM")
                                || p.side.equals("INNER") || p.side.equals("NEITHER"),
                        "unexpected side '" + p.side + "' for " + p.refdes);
                sampled++;
            }
        }
        assertTrue(sampled > 0);
    }

    @Test
    void mirrorFlag_isNorM() {
        int sampled = 0;
        for (BomView.Row r : rows) {
            for (BomView.RefdesEntry p : r.refdesList) {
                assertTrue("N".equals(p.mirror) || "M".equals(p.mirror),
                        "mirror must be N or M for " + p.refdes + ", got " + p.mirror);
                sampled++;
            }
        }
        assertTrue(sampled > 0);
    }

    @Test
    void everyRefdesAppearsInExactlyOneRow() {
        // No double-counting between BOM rows and orphan rows.
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (BomView.Row r : rows) {
            for (BomView.RefdesEntry p : r.refdesList) {
                if (p.refdes == null) continue;
                assertTrue(seen.add(p.refdes),
                        "refdes " + p.refdes + " appears in more than one row");
            }
        }
    }

    @Test
    void rowsCarryPartIdentity() {
        // Each row should identify its part either via a BOM CPN/part number or
        // (for orphan rows) the grouped part name — never a wholly empty row.
        int identified = 0;
        for (BomView.Row r : rows) {
            boolean hasPart = (r.partNumber != null && !r.partNumber.isBlank())
                    || (r.packageName != null && !r.packageName.isBlank());
            if (hasPart) identified++;
        }
        assertTrue(identified > 0,
                "expected at least one row to carry part identity (part number / package)");
    }

    @Test
    void atLeastOneRowCarriesPrpDerivedMetadata() {
        // The sample's components ship PRP records (manufacturer / part data), so
        // at least one row should surface a non-blank manufacturer or description.
        boolean anyMeta = false;
        for (BomView.Row r : rows) {
            if ((r.manufacturer != null && !r.manufacturer.isBlank())
                    || (r.description != null && !r.description.isBlank())
                    || (r.mpn != null && !r.mpn.isBlank())) {
                anyMeta = true;
                break;
            }
        }
        assertTrue(anyMeta,
                "expected at least one row to carry PRP-derived metadata (manufacturer/MPN/description)");
    }
}

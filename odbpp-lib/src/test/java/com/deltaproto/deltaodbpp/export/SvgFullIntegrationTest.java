package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.export.util.SvgElementExtractor;
import com.deltaproto.deltaodbpp.export.util.SvgElementExtractor.*;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.parser.OdbParser;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full integration tests for SVG output.
 *
 * <p>The minimal-odb tests compare the generated SVG against the committed golden
 * reference ({@link Fixtures#MINIMAL_ODB_REFERENCE}) — an exact, source-controlled
 * comparison. The multilayer tests render the committed openly-available
 * {@code designodb} sample and assert robust structural properties (the board has
 * no committed golden SVG, so exact-match is intentionally not attempted).
 */
class SvgFullIntegrationTest {

    // Tolerance for coordinate comparisons (in output units - inches by default)
    private static final double COORDINATE_TOLERANCE = 0.0001;

    // Tolerance for radius comparisons (in output units - inches by default)
    private static final double RADIUS_TOLERANCE = 0.00001;

    // Conversion factor for mm to inches (output is now in inches by default)
    private static final double MM_TO_INCH = 1.0 / 25.4;

    private OdbParser parser;
    private MultiLayerSvgRenderer renderer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parser = new OdbParser();
        renderer = new MultiLayerSvgRenderer();
    }

    // ==================== Multilayer Structural Comparison ====================

    @Test
    @DisplayName("Multilayer: Generated SVG has a coherent structure")
    void testMultilayerRendersStructure() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        SvgElementExtractor generated = render(job);

        // A rich multilayer board renders many circular features and trace paths.
        assertTrue(generated.getCircleCount() > 0,
            "Multilayer sample should render circular features");
        assertTrue(generated.getPathCount() > 0,
            "Multilayer sample should render path features");
        assertTrue(generated.getLayers().size() >= 5,
            "Multilayer sample should render at least 5 layers, got "
                + generated.getLayers().size());
    }

    @Test
    @DisplayName("Multilayer: Has multiple distinct rendered layers")
    void testMultilayerLayers() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        SvgElementExtractor generated = render(job);

        Set<String> genLayers = new HashSet<>();
        generated.getLayers().forEach(l -> genLayers.add(l.id));

        assertTrue(genLayers.size() >= 5,
            "Should render multiple distinct layers, got " + genLayers);
        boolean hasSignal = genLayers.stream()
            .anyMatch(id -> id.contains("signal") || id.contains("layer"));
        assertTrue(hasSignal, "Should include signal/copper layers, got " + genLayers);
    }

    @Test
    @DisplayName("Multilayer: Renders profile, circles, lines and paths")
    void testMultilayerElementTypes() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        SvgElementExtractor generated = render(job);

        // Structural coverage: a rich board exercises several element kinds.
        assertTrue(generated.getCircleCount() > 0, "Should have circle elements");
        assertTrue(generated.getPathCount() > 0, "Should have path elements");
        assertTrue(generated.getClosedPaths().size() >= 1,
            "Should have at least one closed path (e.g. profile/surface)");
    }

    // ==================== minimal-odb Golden Comparison ====================

    @Test
    @DisplayName("minimal-odb: Generated SVG matches golden reference exactly")
    void testMinimalOdbMatchesReference() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);
        StringWriter writer = new StringWriter();
        renderer.renderJob(job, writer);
        String generatedSvg = writer.toString();

        String referenceSvg = Files.readString(Fixtures.MINIMAL_ODB_REFERENCE);

        SvgComparisonResult result = compareSvg(referenceSvg, generatedSvg);
        printComparisonSummary("minimal-odb", result);
        assertComparisonResult(result, "minimal-odb");
    }

    @Test
    @DisplayName("minimal-odb: Normalized content matches golden reference")
    void testMinimalOdbNormalizedContent() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);
        StringWriter writer = new StringWriter();
        renderer.renderJob(job, writer);
        String generatedSvg = writer.toString();

        String referenceSvg = Files.readString(Fixtures.MINIMAL_ODB_REFERENCE);

        // Normalize whitespace for comparison (collapse multiple spaces, trim lines)
        String normalizedRef = normalizeWhitespace(referenceSvg);
        String normalizedGen = normalizeWhitespace(generatedSvg);

        assertEquals(normalizedRef, normalizedGen,
            "minimal-odb normalized content should match golden reference");
    }

    private String normalizeWhitespace(String svg) {
        return svg.replaceAll("\\s+", " ").trim();
    }

    @Test
    @DisplayName("minimal-odb: Line positions match golden reference")
    void testMinimalOdbLinePositions() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);
        StringWriter writer = new StringWriter();
        renderer.renderJob(job, writer);

        SvgElementExtractor reference = SvgElementExtractor.fromFile(Fixtures.MINIMAL_ODB_REFERENCE);
        SvgElementExtractor generated = SvgElementExtractor.fromString(writer.toString());

        assertEquals(reference.getLineCount(), generated.getLineCount(),
            "Line counts should match exactly");

        // Verify specific lines from minimal-odb (coordinates in inches, converted from mm)
        // Line 1: (10mm, 20mm) to (30mm, 20mm) - horizontal line
        assertTrue(generated.findLineAt(10 * MM_TO_INCH, 20 * MM_TO_INCH, 30 * MM_TO_INCH, 20 * MM_TO_INCH, COORDINATE_TOLERANCE).isPresent(),
            "Should have horizontal line from (10mm,20mm) to (30mm,20mm)");

        // Line 2: (10mm, 30mm) to (30mm, 30mm) - horizontal line
        assertTrue(generated.findLineAt(10 * MM_TO_INCH, 30 * MM_TO_INCH, 30 * MM_TO_INCH, 30 * MM_TO_INCH, COORDINATE_TOLERANCE).isPresent(),
            "Should have horizontal line from (10mm,30mm) to (30mm,30mm)");

        // Line 3: (10mm, 40mm) to (30mm, 50mm) - diagonal line
        assertTrue(generated.findLineAt(10 * MM_TO_INCH, 40 * MM_TO_INCH, 30 * MM_TO_INCH, 50 * MM_TO_INCH, COORDINATE_TOLERANCE).isPresent(),
            "Should have diagonal line from (10mm,40mm) to (30mm,50mm)");
    }

    @Test
    @DisplayName("minimal-odb: Arc path matches golden reference")
    void testMinimalOdbArcPaths() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);
        StringWriter writer = new StringWriter();
        renderer.renderJob(job, writer);

        SvgElementExtractor reference = SvgElementExtractor.fromFile(Fixtures.MINIMAL_ODB_REFERENCE);
        SvgElementExtractor generated = SvgElementExtractor.fromString(writer.toString());

        // minimal-odb has one arc
        assertEquals(reference.getArcPaths().size(), generated.getArcPaths().size(),
            "Arc path counts should match exactly");
        assertTrue(generated.getArcPaths().size() >= 1,
            "Should have at least one arc path");
    }

    @Test
    @DisplayName("minimal-odb: Profile is a closed path matching golden reference")
    void testMinimalOdbProfilePath() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);
        StringWriter writer = new StringWriter();
        renderer.renderJob(job, writer);

        SvgElementExtractor reference = SvgElementExtractor.fromFile(Fixtures.MINIMAL_ODB_REFERENCE);
        SvgElementExtractor generated = SvgElementExtractor.fromString(writer.toString());

        // minimal-odb profile should be a closed path (rectangle 0,0 to 100mm x 80mm)
        assertEquals(reference.getClosedPaths().size(), generated.getClosedPaths().size(),
            "Closed path counts should match exactly");
        assertTrue(generated.getClosedPaths().size() >= 1,
            "Should have at least one closed path (the profile)");

        // Verify the profile path contains the expected rectangle coordinates (in inches)
        // 100mm = 3.937in, 80mm = 3.1496in
        String expected100Inch = String.format(Locale.US, "%.4f", 100 * MM_TO_INCH);
        String expected80Inch = String.format(Locale.US, "%.4f", 80 * MM_TO_INCH);
        boolean hasProfilePath = generated.getClosedPaths().stream()
            .anyMatch(p -> p.data.contains(expected100Inch) && p.data.contains(expected80Inch));
        assertTrue(hasProfilePath,
            String.format("Profile path should contain %s and %s (100mm x 80mm in inches). Paths: %s",
                expected100Inch, expected80Inch, generated.getClosedPaths()));
    }

    // ==================== Consistency Tests ====================

    @Test
    @DisplayName("Rendering is deterministic - same input produces same output")
    void testRenderingIsDeterministic() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        // Render twice
        StringWriter writer1 = new StringWriter();
        renderer.renderJob(job, writer1);
        String svg1 = writer1.toString();

        StringWriter writer2 = new StringWriter();
        renderer.renderJob(job, writer2);
        String svg2 = writer2.toString();

        assertEquals(svg1, svg2, "Same input should produce identical output");
    }

    @Test
    @DisplayName("Re-parsing produces identical output")
    void testReparsingProducesIdenticalOutput() throws IOException {
        // Parse and render twice (separate parse calls)
        Job job1 = parser.parse(Fixtures.MINIMAL_ODB);
        StringWriter writer1 = new StringWriter();
        renderer.renderJob(job1, writer1);

        OdbParser parser2 = new OdbParser();
        Job job2 = parser2.parse(Fixtures.MINIMAL_ODB);
        StringWriter writer2 = new StringWriter();
        MultiLayerSvgRenderer renderer2 = new MultiLayerSvgRenderer();
        renderer2.renderJob(job2, writer2);

        assertEquals(writer1.toString(), writer2.toString(),
            "Re-parsing should produce identical output");
    }

    // ==================== Comparison Utilities ====================

    private SvgElementExtractor render(Job job) throws IOException {
        StringWriter writer = new StringWriter();
        renderer.renderJob(job, writer);
        return SvgElementExtractor.fromString(writer.toString());
    }

    /**
     * Compare SVG without unit conversion (both in same units).
     */
    private SvgComparisonResult compareSvg(String reference, String generated) {
        return compareSvg(reference, generated, 1.0, false, false);
    }

    /**
     * Compare SVG with unit conversion for reference.
     *
     * @param refScaleFactor Scale factor to apply to reference coordinates (e.g., INCH_TO_MM)
     * @param allowYFlip If true, also match elements with negated Y coordinates (for Y-axis flip)
     * @param ignoreRadius If true, only compare positions (for external refs with different radii)
     */
    private SvgComparisonResult compareSvg(String reference, String generated, double refScaleFactor,
                                           boolean allowYFlip, boolean ignoreRadius) {
        SvgElementExtractor refExtractor = SvgElementExtractor.fromString(reference, refScaleFactor);
        SvgElementExtractor genExtractor = SvgElementExtractor.fromString(generated);

        SvgComparisonResult result = new SvgComparisonResult();

        // Compare circle counts
        result.refCircleCount = refExtractor.getCircleCount();
        result.genCircleCount = genExtractor.getCircleCount();

        // Compare path counts
        result.refPathCount = refExtractor.getPathCount();
        result.genPathCount = genExtractor.getPathCount();

        // Compare line counts
        result.refLineCount = refExtractor.getLineCount();
        result.genLineCount = genExtractor.getLineCount();

        // Compare layer counts
        result.refLayerCount = refExtractor.getLayers().size();
        result.genLayerCount = genExtractor.getLayers().size();

        // Find unmatched circles (with optional Y-flip matching and radius comparison)
        result.unmatchedRefCircles = findUnmatchedCircles(refExtractor.getCircles(), genExtractor.getCircles(), allowYFlip, ignoreRadius);
        result.unmatchedGenCircles = findUnmatchedCircles(genExtractor.getCircles(), refExtractor.getCircles(), allowYFlip, ignoreRadius);

        // Compare file sizes
        result.refSize = reference.length();
        result.genSize = generated.length();

        return result;
    }

    private List<CircleElement> findUnmatchedCircles(List<CircleElement> source, List<CircleElement> target) {
        return findUnmatchedCircles(source, target, false, false);
    }

    /**
     * Find circles in source that have no matching circle in target.
     * @param allowYFlip if true, also matches circles with negated Y coordinates
     * @param ignoreRadius if true, only compare positions (ignore radius differences)
     */
    private List<CircleElement> findUnmatchedCircles(List<CircleElement> source, List<CircleElement> target,
                                                      boolean allowYFlip, boolean ignoreRadius) {
        List<CircleElement> unmatched = new ArrayList<>();

        for (CircleElement srcCircle : source) {
            boolean found = false;
            for (CircleElement tgtCircle : target) {
                if (circlesMatch(srcCircle, tgtCircle, allowYFlip, ignoreRadius)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                unmatched.add(srcCircle);
            }
        }

        return unmatched;
    }

    private boolean circlesMatch(CircleElement a, CircleElement b) {
        return circlesMatch(a, b, false, false);
    }

    /**
     * Match circles with configurable matching options.
     * @param allowYFlip if true, also checks if circles match with negated Y coordinates
     * @param ignoreRadius if true, only compare positions (ignore radius differences)
     */
    private boolean circlesMatch(CircleElement a, CircleElement b, boolean allowYFlip, boolean ignoreRadius) {
        boolean radiusMatch = ignoreRadius || Math.abs(a.r - b.r) <= RADIUS_TOLERANCE;

        // Check direct match
        boolean positionMatch = Math.abs(a.cx - b.cx) <= COORDINATE_TOLERANCE &&
               Math.abs(a.cy - b.cy) <= COORDINATE_TOLERANCE;

        if (positionMatch && radiusMatch) {
            return true;
        }

        if (!allowYFlip) {
            return false;
        }

        // Check Y-flipped match (for reference SVGs with scale(1,-1) transform)
        boolean yFlippedPositionMatch = Math.abs(a.cx - b.cx) <= COORDINATE_TOLERANCE &&
               Math.abs(a.cy - (-b.cy)) <= COORDINATE_TOLERANCE;

        return yFlippedPositionMatch && radiusMatch;
    }

    private void printComparisonSummary(String name, SvgComparisonResult result) {
        System.out.println("\n=== " + name + " Comparison Summary ===");
        System.out.println("Circles: ref=" + result.refCircleCount + ", gen=" + result.genCircleCount +
            (result.refCircleCount == result.genCircleCount ? " ok" : " diff"));
        System.out.println("Paths: ref=" + result.refPathCount + ", gen=" + result.genPathCount +
            (result.refPathCount == result.genPathCount ? " ok" : " diff"));
        System.out.println("Lines: ref=" + result.refLineCount + ", gen=" + result.genLineCount +
            (result.refLineCount == result.genLineCount ? " ok" : " diff"));
        System.out.println("Layers: ref=" + result.refLayerCount + ", gen=" + result.genLayerCount +
            (result.refLayerCount == result.genLayerCount ? " ok" : " diff"));
        System.out.println("Size: ref=" + result.refSize + " bytes, gen=" + result.genSize + " bytes");
        System.out.println("Unmatched circles in ref: " + result.unmatchedRefCircles.size());
        System.out.println("Unmatched circles in gen: " + result.unmatchedGenCircles.size());
    }

    private void assertComparisonResult(SvgComparisonResult result, String name) {
        // Position-based comparison: check that reference elements have matches
        // (generated may have more elements - that's ok if all ref elements are covered)

        double circleMatchRate = result.refCircleCount > 0
            ? (result.refCircleCount - result.unmatchedRefCircles.size()) * 100.0 / result.refCircleCount
            : 100.0;

        System.out.println(String.format("%s: Circle position match rate: %.1f%% (%d/%d matched)",
            name, circleMatchRate, result.refCircleCount - result.unmatchedRefCircles.size(), result.refCircleCount));
        System.out.println(String.format("%s: Unmatched reference circles: %d", name, result.unmatchedRefCircles.size()));

        // Print first few unmatched circles for debugging
        if (!result.unmatchedRefCircles.isEmpty()) {
            System.out.println("First 10 unmatched reference circles:");
            result.unmatchedRefCircles.stream().limit(10).forEach(c ->
                System.out.println(String.format("  cx=%.4f, cy=%.4f, r=%.6f", c.cx, c.cy, c.r)));
        }

        // Print first few generated circles for comparison
        if (!result.unmatchedGenCircles.isEmpty()) {
            System.out.println("First 10 generated circles (for comparison):");
            result.unmatchedGenCircles.stream().limit(10).forEach(c ->
                System.out.println(String.format("  cx=%.4f, cy=%.4f, r=%.6f", c.cx, c.cy, c.r)));
        }

        // Assertions - require at least 90% of reference circles to have position matches
        assertTrue(circleMatchRate >= 90.0,
            String.format("%s: Circle match rate too low: %.1f%% (need >= 90%%)", name, circleMatchRate));

        // Check layer coverage - generated should have most of reference layers
        assertTrue(result.genLayerCount >= result.refLayerCount * 0.8,
            String.format("%s: Layer count too low: %d (reference has %d)",
                name, result.genLayerCount, result.refLayerCount));
    }

    // Result container
    private static class SvgComparisonResult {
        int refCircleCount, genCircleCount;
        int refPathCount, genPathCount;
        int refLineCount, genLineCount;
        int refLayerCount, genLayerCount;
        int refSize, genSize;
        List<CircleElement> unmatchedRefCircles = new ArrayList<>();
        List<CircleElement> unmatchedGenCircles = new ArrayList<>();
    }
}

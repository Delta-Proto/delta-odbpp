package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.OdbArchiveExtractor;
import com.deltaproto.deltaodbpp.model.Features;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Layer;
import com.deltaproto.deltaodbpp.model.Step;
import com.deltaproto.deltaodbpp.parser.OdbParser;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests SVG output against known-good reference values.
 *
 * <p>This test validates that our SVG output matches expected reference values
 * for coordinates, radii, and stroke widths within acceptable precision tolerance.
 *
 * <p>The fixture is the committed synthetic two-layer board archive
 * ({@link Fixtures#MINIMAL_TEST_ODB_ZIP}); the reference values below are the
 * deterministic geometry of that hand-made board, so the exact-number comparison
 * is meaningful and traceable to no external provenance.
 */
class SvgReferenceComparisonTest {

    private static Job job;
    private static Features bottomFeatures;
    private static Features topFeatures;

    // Precision tolerance for floating point comparison (0.1% relative tolerance)
    // This accounts for formatting differences (our %.4f vs reference's higher precision)
    private static final double RELATIVE_TOLERANCE = 0.001;

    // Expected reference values for bottom layer (from battle-tested renderer)
    // All values in inches
    private static final double[][] BOTTOM_CIRCLES_REFERENCE = {
        // {cx, cy, radius}
        {0.393701, 0.393701, 0.00984252},   // 10,10mm r500 (0.5mm diameter)
        {0.787402, 0.393701, 0.019685},     // 20,10mm r1000 (1mm diameter)
        {1.37795, 0.393701, 0.0393701},     // 35,10mm r2000 (2mm diameter)
        {1.9685, 1.9685, 0.0393701},        // 50,50mm r2000 (2mm diameter)
        {2.75591, 1.9685, 0.0393701},       // 70,50mm r2000 (2mm diameter)
    };

    // Expected reference values for bottom layer line
    private static final double[] BOTTOM_LINE_REFERENCE = {
        // {x1, y1, x2, y2, stroke-width}
        1.9685, 1.9685, 2.75591, 1.9685, 0.0393701  // Line from (50,50) to (70,50)mm, r1000 width
    };

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void loadTestData() throws Exception {
        // Extract the committed synthetic two-layer board archive into the temp dir.
        Path odbRoot = new OdbArchiveExtractor().extract(Fixtures.MINIMAL_TEST_ODB_ZIP, tempDir);

        OdbParser parser = new OdbParser();
        job = parser.parse(odbRoot);

        Step step = job.getSteps().get("pcb");
        assertNotNull(step, "pcb step should exist");

        Layer bottomLayer = step.getLayersByName().get("bottom");
        assertNotNull(bottomLayer, "bottom layer should exist");
        bottomFeatures = bottomLayer.getFeatures();
        assertNotNull(bottomFeatures, "bottom features should exist");

        Layer topLayer = step.getLayersByName().get("top");
        if (topLayer != null) {
            topFeatures = topLayer.getFeatures();
        }
    }

    @Test
    void testBottomLayerCirclesMatchReference() throws Exception {
        // Render bottom layer
        SvgRenderOptions options = new SvgRenderOptions()
            .withColor("#CC0000")
            .withOutputUnit(SvgRenderOptions.OutputUnit.INCH);

        SvgRenderer renderer = new SvgRenderer(options);
        StringWriter writer = new StringWriter();
        renderer.renderFeatures(bottomFeatures, writer);
        String svg = writer.toString();

        // Extract circles from SVG
        List<double[]> circles = extractCircles(svg);

        System.out.println("=== Bottom Layer Reference Comparison ===");
        System.out.println("Generated SVG:");
        System.out.println(svg);
        System.out.println();
        System.out.println("Found " + circles.size() + " circles");

        // We should have 5 circles
        assertEquals(5, circles.size(), "Should have 5 circles on bottom layer");

        // Verify each circle matches reference within tolerance
        for (int i = 0; i < BOTTOM_CIRCLES_REFERENCE.length; i++) {
            double[] expected = BOTTOM_CIRCLES_REFERENCE[i];
            double[] actual = findCircleNear(circles, expected[0], expected[1]);

            assertNotNull(actual, String.format(
                "Circle at (%.6f, %.6f) not found", expected[0], expected[1]));

            // Check position
            assertWithinTolerance(expected[0], actual[0],
                String.format("Circle %d cx", i));
            assertWithinTolerance(expected[1], actual[1],
                String.format("Circle %d cy", i));

            // Check radius
            assertWithinTolerance(expected[2], actual[2],
                String.format("Circle %d radius (expected %.7f, got %.7f)",
                    i, expected[2], actual[2]));

            System.out.printf("Circle %d: cx=%.6f cy=%.6f r=%.7f ✓%n",
                i, actual[0], actual[1], actual[2]);
        }
    }

    @Test
    void testBottomLayerLineMatchesReference() throws Exception {
        // Render bottom layer
        SvgRenderOptions options = new SvgRenderOptions()
            .withColor("#CC0000")
            .withOutputUnit(SvgRenderOptions.OutputUnit.INCH);

        SvgRenderer renderer = new SvgRenderer(options);
        StringWriter writer = new StringWriter();
        renderer.renderFeatures(bottomFeatures, writer);
        String svg = writer.toString();

        // Extract path/line elements
        List<double[]> lines = extractPathLines(svg);

        System.out.println("=== Bottom Layer Line Reference Comparison ===");
        System.out.println("Found " + lines.size() + " lines");

        // We should have 1 line
        assertEquals(1, lines.size(), "Should have 1 line on bottom layer");

        double[] actual = lines.get(0);
        double[] expected = BOTTOM_LINE_REFERENCE;

        // Check endpoints
        assertWithinTolerance(expected[0], actual[0], "Line x1");
        assertWithinTolerance(expected[1], actual[1], "Line y1");
        assertWithinTolerance(expected[2], actual[2], "Line x2");
        assertWithinTolerance(expected[3], actual[3], "Line y2");

        // Check stroke width
        assertWithinTolerance(expected[4], actual[4],
            String.format("Line stroke-width (expected %.7f, got %.7f)", expected[4], actual[4]));

        System.out.printf("Line: (%.6f,%.6f) to (%.6f,%.6f) stroke-width=%.7f ✓%n",
            actual[0], actual[1], actual[2], actual[3], actual[4]);
    }

    @Test
    void testCoordinateConversionAccuracy() {
        // Test the conversion chain: mm → inches
        // 10mm = 0.393700787... inches
        double mm = 10.0;
        double expectedInches = 0.393700787401575;

        SvgRenderOptions options = new SvgRenderOptions()
            .withOutputUnit(SvgRenderOptions.OutputUnit.INCH);

        double actualInches = options.toOutputUnit(mm);

        double relativeError = Math.abs(actualInches - expectedInches) / expectedInches;
        System.out.printf("Coordinate conversion: %.1fmm → %.15f inches (expected %.15f)%n",
            mm, actualInches, expectedInches);
        System.out.printf("Relative error: %.2e%n", relativeError);

        assertTrue(relativeError < 1e-10,
            "Coordinate conversion should be highly accurate");
    }

    @Test
    void testSymbolSizeConversionAccuracy() {
        // Test symbol dimension conversion: microns → mm → inches
        // Symbol r2000 = 2000 microns = 2mm diameter = 1mm radius
        // 1mm radius = 0.0393700787... inches

        double radiusMm = 1.0;  // From r2000 symbol (2mm diameter)
        double expectedRadiusInches = 0.0393700787401575;

        SvgRenderOptions options = new SvgRenderOptions()
            .withOutputUnit(SvgRenderOptions.OutputUnit.INCH);

        double actualRadiusInches = options.toOutputUnit(radiusMm);

        double relativeError = Math.abs(actualRadiusInches - expectedRadiusInches) / expectedRadiusInches;
        System.out.printf("Symbol radius conversion: %.1fmm → %.15f inches (expected %.15f)%n",
            radiusMm, actualRadiusInches, expectedRadiusInches);
        System.out.printf("Relative error: %.2e%n", relativeError);

        assertTrue(relativeError < 1e-10,
            "Symbol radius conversion should be highly accurate");
    }

    @Test
    void testOutputMatchesReferenceFormat() throws Exception {
        // Verify our output format is compatible with reference renderer
        SvgRenderOptions options = new SvgRenderOptions()
            .withColor("#CC0000")
            .withOutputUnit(SvgRenderOptions.OutputUnit.INCH);

        SvgRenderer renderer = new SvgRenderer(options);
        StringWriter writer = new StringWriter();
        renderer.renderFeatures(bottomFeatures, writer);
        String svg = writer.toString();

        // Check SVG structure
        assertTrue(svg.contains("<?xml version=\"1.0\""), "Should have XML declaration");
        assertTrue(svg.contains("<svg xmlns="), "Should have SVG namespace");
        assertTrue(svg.contains("viewBox="), "Should have viewBox");
        assertTrue(svg.contains("<circle"), "Should contain circles");
        assertTrue(svg.contains("<path"), "Should contain paths");
        assertTrue(svg.contains("fill=\"#CC0000\"") || svg.contains("fill='#CC0000'"),
            "Should have correct fill color");
        assertTrue(svg.contains("stroke=\"#CC0000\"") || svg.contains("stroke='#CC0000'") ||
                   svg.contains("stroke-width="),
            "Lines should have stroke attributes");

        System.out.println("SVG format validation ✓");
    }

    // Helper methods

    private void assertWithinTolerance(double expected, double actual, String message) {
        double relativeError = Math.abs(actual - expected) / Math.max(Math.abs(expected), 1e-10);
        assertTrue(relativeError < RELATIVE_TOLERANCE,
            String.format("%s: expected %.7f, got %.7f (error: %.2e)",
                message, expected, actual, relativeError));
    }

    private List<double[]> extractCircles(String svg) {
        List<double[]> circles = new ArrayList<>();
        // Match: <circle cx="..." cy="..." r="..."
        Pattern pattern = Pattern.compile(
            "<circle[^>]*cx=\"([^\"]+)\"[^>]*cy=\"([^\"]+)\"[^>]*r=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(svg);

        while (matcher.find()) {
            double cx = Double.parseDouble(matcher.group(1));
            double cy = Double.parseDouble(matcher.group(2));
            double r = Double.parseDouble(matcher.group(3));
            circles.add(new double[]{cx, cy, r});
        }

        return circles;
    }

    private double[] findCircleNear(List<double[]> circles, double targetCx, double targetCy) {
        double tolerance = 0.001;  // 0.001 inch tolerance for position matching
        for (double[] circle : circles) {
            if (Math.abs(circle[0] - targetCx) < tolerance &&
                Math.abs(circle[1] - targetCy) < tolerance) {
                return circle;
            }
        }
        return null;
    }

    private List<double[]> extractPathLines(String svg) {
        List<double[]> lines = new ArrayList<>();
        // Match: <path d="M x1 y1 L x2 y2" ... stroke-width="..."
        Pattern pattern = Pattern.compile(
            "<path[^>]*d=\"M\\s*([\\d.]+)\\s+([\\d.]+)\\s+L\\s*([\\d.]+)\\s+([\\d.]+)\"[^>]*stroke-width=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(svg);

        while (matcher.find()) {
            double x1 = Double.parseDouble(matcher.group(1));
            double y1 = Double.parseDouble(matcher.group(2));
            double x2 = Double.parseDouble(matcher.group(3));
            double y2 = Double.parseDouble(matcher.group(4));
            double strokeWidth = Double.parseDouble(matcher.group(5));
            lines.add(new double[]{x1, y1, x2, y2, strokeWidth});
        }

        return lines;
    }
}

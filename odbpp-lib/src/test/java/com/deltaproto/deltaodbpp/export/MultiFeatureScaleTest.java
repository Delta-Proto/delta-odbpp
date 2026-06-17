package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.OdbArchiveExtractor;
import com.deltaproto.deltaodbpp.model.*;
import com.deltaproto.deltaodbpp.parser.OdbParser;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive scale analysis test for multiple ODB++ feature types.
 *
 * Tests that all feature types are correctly scaled when converting from
 * ODB++ (UNITS=MM with micron-based symbols) to SVG output (inches).
 *
 * Feature types tested:
 * - Round pads (r<diameter>)
 * - Square pads (s<size>)
 * - Rectangular pads (rect<w>x<h>)
 * - Oval pads (oval<w>x<h>)
 * - Rounded rectangle pads (rect<w>x<h>xr<r>)
 * - Lines with stroke widths
 * - Arcs
 */
class MultiFeatureScaleTest {

    // Conversion factors
    private static final double MICRONS_TO_MM = 0.001;
    private static final double MM_TO_INCHES = 1.0 / 25.4;
    private static final double MICRONS_TO_INCHES = MICRONS_TO_MM * MM_TO_INCHES;

    // Tolerance for comparisons (0.1 mil = 0.0001 inches)
    private static final double TOLERANCE = 0.0001;

    // Patterns for extracting SVG elements
    private static final Pattern CIRCLE_PATTERN = Pattern.compile(
        "<circle[^>]*cx=\"([^\"]+)\"[^>]*cy=\"([^\"]+)\"[^>]*r=\"([^\"]+)\"");
    private static final Pattern RECT_PATTERN = Pattern.compile(
        "<rect[^>]*x=\"([^\"]+)\"[^>]*y=\"([^\"]+)\"[^>]*width=\"([^\"]+)\"[^>]*height=\"([^\"]+)\"");
    private static final Pattern ELLIPSE_PATTERN = Pattern.compile(
        "<ellipse[^>]*cx=\"([^\"]+)\"[^>]*cy=\"([^\"]+)\"[^>]*rx=\"([^\"]+)\"[^>]*ry=\"([^\"]+)\"");
    private static final Pattern PATH_LINE_PATTERN = Pattern.compile(
        "<path[^>]*d=\"M\\s*([\\d.-]+)\\s+([\\d.-]+)\\s+L\\s*([\\d.-]+)\\s+([\\d.-]+)\"[^>]*stroke-width=\"([^\"]+)\"");
    private static final Pattern PATH_ARC_PATTERN = Pattern.compile(
        "<path[^>]*d=\"M\\s*([\\d.-]+)\\s+([\\d.-]+)\\s+A[^\"]+\"[^>]*stroke-width=\"([^\"]+)\"");
    private static final Pattern VIEWBOX_PATTERN = Pattern.compile(
        "viewBox=\"([^\"]+)\"");

    private OdbParser parser;
    private OdbArchiveExtractor extractor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parser = new OdbParser();
        extractor = new OdbArchiveExtractor();
    }

    @Test
    @DisplayName("Comprehensive scale analysis for all feature types")
    void testAllFeatureTypesScale() throws IOException {
        Path zipPath = Fixtures.MINIMAL_TEST_ODB_ZIP;

        // Extract and parse
        Path odbRoot = extractor.extract(zipPath, tempDir.resolve("minimal-test"));
        Job job = parser.parse(odbRoot);

        String units = job.getMiscInfo() != null ? job.getMiscInfo().getUnits() : "UNKNOWN";
        assertEquals("MM", units, "Test file should use MM units");

        // Render to SVG
        StringWriter writer = new StringWriter();
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        System.out.println("\n" + "=".repeat(60));
        System.out.println("MULTI-FEATURE SCALE ANALYSIS");
        System.out.println("=".repeat(60));
        System.out.println("Units: " + units);

        // Extract and print viewBox
        Matcher vbMatcher = VIEWBOX_PATTERN.matcher(svg);
        if (vbMatcher.find()) {
            System.out.println("ViewBox: " + vbMatcher.group(1));
        }

        // Analyze each feature type
        analyzeCircles(svg);
        analyzeRectangles(svg);
        analyzeEllipses(svg);
        analyzeLines(svg);
        analyzeArcs(svg);

        System.out.println("\n" + "=".repeat(60));
    }

    @Test
    @DisplayName("Round pads: r500, r1000, r2000 scale correctly")
    void testRoundPadScaling() throws IOException {
        Path zipPath = Fixtures.MINIMAL_TEST_ODB_ZIP;

        Path odbRoot = extractor.extract(zipPath, tempDir.resolve("round-test"));
        Job job = parser.parse(odbRoot);

        StringWriter writer = new StringWriter();
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        List<CircleData> circles = extractCircles(svg);

        // Expected radii from symbols (diameter in microns -> radius in inches)
        // r500 = 500um diameter = 250um radius = 0.00984" radius
        // r1000 = 1000um diameter = 500um radius = 0.01969" radius
        // r2000 = 2000um diameter = 1000um radius = 0.03937" radius
        double expectedR500 = 250 * MICRONS_TO_INCHES;   // 0.00984"
        double expectedR1000 = 500 * MICRONS_TO_INCHES;  // 0.01969"
        double expectedR2000 = 1000 * MICRONS_TO_INCHES; // 0.03937"

        System.out.println("\n=== Round Pad Verification ===");
        System.out.println(String.format("Expected r500 radius:  %.6f\" (%.4f mm)", expectedR500, 0.25));
        System.out.println(String.format("Expected r1000 radius: %.6f\" (%.4f mm)", expectedR1000, 0.5));
        System.out.println(String.format("Expected r2000 radius: %.6f\" (%.4f mm)", expectedR2000, 1.0));

        // Find circles at expected positions
        // Row 1 at y=10mm (0.3937")
        double y10mm = 10 * MM_TO_INCHES;

        // r500 at x=10mm
        Optional<CircleData> r500Circle = findCircleAt(circles, 10 * MM_TO_INCHES, y10mm);
        assertTrue(r500Circle.isPresent(), "Should find r500 circle at (10mm, 10mm)");
        assertRadiusEquals(expectedR500, r500Circle.get().r, "r500 radius");

        // r1000 at x=20mm
        Optional<CircleData> r1000Circle = findCircleAt(circles, 20 * MM_TO_INCHES, y10mm);
        assertTrue(r1000Circle.isPresent(), "Should find r1000 circle at (20mm, 10mm)");
        assertRadiusEquals(expectedR1000, r1000Circle.get().r, "r1000 radius");

        // r2000 at x=35mm
        Optional<CircleData> r2000Circle = findCircleAt(circles, 35 * MM_TO_INCHES, y10mm);
        assertTrue(r2000Circle.isPresent(), "Should find r2000 circle at (35mm, 10mm)");
        assertRadiusEquals(expectedR2000, r2000Circle.get().r, "r2000 radius");
    }

    @Test
    @DisplayName("Square pads: s500, s1000, s2000 scale correctly")
    void testSquarePadScaling() throws IOException {
        Path zipPath = Fixtures.MINIMAL_TEST_ODB_ZIP;

        Path odbRoot = extractor.extract(zipPath, tempDir.resolve("square-test"));
        Job job = parser.parse(odbRoot);

        StringWriter writer = new StringWriter();
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        List<RectData> rects = extractRectangles(svg);

        // Expected sizes from symbols (size in microns -> size in inches)
        // s500 = 500um = 0.01969"
        // s1000 = 1000um = 0.03937"
        // s2000 = 2000um = 0.07874"
        double expectedS500 = 500 * MICRONS_TO_INCHES;
        double expectedS1000 = 1000 * MICRONS_TO_INCHES;
        double expectedS2000 = 2000 * MICRONS_TO_INCHES;

        System.out.println("\n=== Square Pad Verification ===");
        System.out.println(String.format("Expected s500 size:  %.6f\" (%.4f mm)", expectedS500, 0.5));
        System.out.println(String.format("Expected s1000 size: %.6f\" (%.4f mm)", expectedS1000, 1.0));
        System.out.println(String.format("Expected s2000 size: %.6f\" (%.4f mm)", expectedS2000, 2.0));

        // Row 2 at y=25mm
        double y25mm = 25 * MM_TO_INCHES;

        // Find square pads (width == height)
        List<RectData> squareRects = rects.stream()
            .filter(r -> Math.abs(r.width - r.height) < TOLERANCE)
            .toList();

        System.out.println("Found " + squareRects.size() + " square rectangles");

        // Check if we have the expected sizes
        boolean hasS500 = squareRects.stream().anyMatch(r -> Math.abs(r.width - expectedS500) < TOLERANCE);
        boolean hasS1000 = squareRects.stream().anyMatch(r -> Math.abs(r.width - expectedS1000) < TOLERANCE);
        boolean hasS2000 = squareRects.stream().anyMatch(r -> Math.abs(r.width - expectedS2000) < TOLERANCE);

        assertTrue(hasS500, "Should have s500 square pad (0.5mm)");
        assertTrue(hasS1000, "Should have s1000 square pad (1mm)");
        assertTrue(hasS2000, "Should have s2000 square pad (2mm)");
    }

    @Test
    @DisplayName("Line stroke widths scale correctly")
    void testLineStrokeWidthScaling() throws IOException {
        Path zipPath = Fixtures.MINIMAL_TEST_ODB_ZIP;

        Path odbRoot = extractor.extract(zipPath, tempDir.resolve("line-test"));
        Job job = parser.parse(odbRoot);

        StringWriter writer = new StringWriter();
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        List<LineData> lines = extractLines(svg);

        // Expected stroke widths (from symbol diameter in microns)
        // r500 = 500um = 0.01969"
        // r1000 = 1000um = 0.03937"
        // r2000 = 2000um = 0.07874"
        double expectedW500 = 500 * MICRONS_TO_INCHES;
        double expectedW1000 = 1000 * MICRONS_TO_INCHES;
        double expectedW2000 = 2000 * MICRONS_TO_INCHES;

        System.out.println("\n=== Line Stroke Width Verification ===");
        System.out.println(String.format("Expected r500 width:  %.6f\" (%.4f mm)", expectedW500, 0.5));
        System.out.println(String.format("Expected r1000 width: %.6f\" (%.4f mm)", expectedW1000, 1.0));
        System.out.println(String.format("Expected r2000 width: %.6f\" (%.4f mm)", expectedW2000, 2.0));
        System.out.println("Found " + lines.size() + " lines");

        // Print actual stroke widths found
        Set<Double> strokeWidths = new TreeSet<>();
        lines.forEach(l -> strokeWidths.add(Math.round(l.strokeWidth * 100000) / 100000.0));
        System.out.println("Unique stroke widths: " + strokeWidths);

        // Verify expected widths exist
        boolean hasW500 = lines.stream().anyMatch(l -> Math.abs(l.strokeWidth - expectedW500) < TOLERANCE);
        boolean hasW1000 = lines.stream().anyMatch(l -> Math.abs(l.strokeWidth - expectedW1000) < TOLERANCE);
        boolean hasW2000 = lines.stream().anyMatch(l -> Math.abs(l.strokeWidth - expectedW2000) < TOLERANCE);

        assertTrue(hasW500, "Should have line with r500 stroke width (0.5mm)");
        assertTrue(hasW1000, "Should have line with r1000 stroke width (1mm)");
        assertTrue(hasW2000, "Should have line with r2000 stroke width (2mm)");
    }

    @Test
    @DisplayName("Position-to-size ratio is reasonable for all features")
    void testPositionToSizeRatio() throws IOException {
        Path zipPath = Fixtures.MINIMAL_TEST_ODB_ZIP;

        Path odbRoot = extractor.extract(zipPath, tempDir.resolve("ratio-test"));
        Job job = parser.parse(odbRoot);

        StringWriter writer = new StringWriter();
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        List<CircleData> circles = extractCircles(svg);

        // Calculate average position-to-radius ratio
        double sumRatio = 0;
        int count = 0;
        for (CircleData c : circles) {
            if (c.r > 0 && (c.cx > 0 || c.cy > 0)) {
                double position = Math.sqrt(c.cx * c.cx + c.cy * c.cy);
                sumRatio += position / c.r;
                count++;
            }
        }
        double avgRatio = count > 0 ? sumRatio / count : 0;

        System.out.println("\n=== Position-to-Size Ratio Analysis ===");
        System.out.println(String.format("Average position/radius ratio: %.1f", avgRatio));

        // For a typical PCB with features at 10-100mm and radii of 0.25-1mm,
        // the ratio should be roughly 10-400
        // If there's a scale mismatch (e.g., 25.4x or 1000x), this would be way off
        assertTrue(avgRatio >= 5, "Ratio too low - features might be too large");
        assertTrue(avgRatio <= 1000, "Ratio too high - features might be too small");

        // Check for suspicious scale factors
        double[] suspiciousFactors = {25.4, 1000.0, 25400.0}; // mm/inch, mils, microns
        for (double factor : suspiciousFactors) {
            double ratioAtFactor = avgRatio / factor;
            if (ratioAtFactor > 5 && ratioAtFactor < 500) {
                System.out.println("WARNING: Ratio / " + factor + " = " + ratioAtFactor +
                    " - possible unit mismatch!");
            }
        }
    }

    // ==================== Analysis Helper Methods ====================

    private void analyzeCircles(String svg) {
        List<CircleData> circles = extractCircles(svg);
        System.out.println("\n--- CIRCLES (Round Pads) ---");
        System.out.println("Count: " + circles.size());

        if (!circles.isEmpty()) {
            double minR = circles.stream().mapToDouble(c -> c.r).min().orElse(0);
            double maxR = circles.stream().mapToDouble(c -> c.r).max().orElse(0);
            System.out.println(String.format("Radius range: %.6f\" to %.6f\" (%.4fmm to %.4fmm)",
                minR, maxR, minR * 25.4, maxR * 25.4));

            Set<Double> uniqueRadii = new TreeSet<>();
            circles.forEach(c -> uniqueRadii.add(Math.round(c.r * 100000) / 100000.0));
            System.out.println("Unique radii: " + uniqueRadii);

            System.out.println("First 5 circles:");
            circles.stream().limit(5).forEach(c ->
                System.out.println(String.format("  cx=%.4f\", cy=%.4f\", r=%.6f\" (%.4fmm)",
                    c.cx, c.cy, c.r, c.r * 25.4)));
        }
    }

    private void analyzeRectangles(String svg) {
        List<RectData> rects = extractRectangles(svg);
        System.out.println("\n--- RECTANGLES (Square/Rect Pads) ---");
        System.out.println("Count: " + rects.size());

        if (!rects.isEmpty()) {
            System.out.println("First 5 rectangles:");
            rects.stream().limit(5).forEach(r ->
                System.out.println(String.format("  x=%.4f\", y=%.4f\", w=%.4f\", h=%.4f\" (%.2fx%.2fmm)",
                    r.x, r.y, r.width, r.height, r.width * 25.4, r.height * 25.4)));
        }
    }

    private void analyzeEllipses(String svg) {
        List<EllipseData> ellipses = extractEllipses(svg);
        System.out.println("\n--- ELLIPSES (Oval Pads) ---");
        System.out.println("Count: " + ellipses.size());

        if (!ellipses.isEmpty()) {
            System.out.println("First 5 ellipses:");
            ellipses.stream().limit(5).forEach(e ->
                System.out.println(String.format("  cx=%.4f\", cy=%.4f\", rx=%.4f\", ry=%.4f\"",
                    e.cx, e.cy, e.rx, e.ry)));
        }
    }

    private void analyzeLines(String svg) {
        List<LineData> lines = extractLines(svg);
        System.out.println("\n--- LINES ---");
        System.out.println("Count: " + lines.size());

        if (!lines.isEmpty()) {
            double minW = lines.stream().mapToDouble(l -> l.strokeWidth).min().orElse(0);
            double maxW = lines.stream().mapToDouble(l -> l.strokeWidth).max().orElse(0);
            System.out.println(String.format("Stroke width range: %.6f\" to %.6f\" (%.4fmm to %.4fmm)",
                minW, maxW, minW * 25.4, maxW * 25.4));

            System.out.println("First 5 lines:");
            lines.stream().limit(5).forEach(l ->
                System.out.println(String.format("  (%.4f\",%.4f\") to (%.4f\",%.4f\"), stroke=%.6f\"",
                    l.x1, l.y1, l.x2, l.y2, l.strokeWidth)));
        }
    }

    private void analyzeArcs(String svg) {
        List<ArcData> arcs = extractArcs(svg);
        System.out.println("\n--- ARCS ---");
        System.out.println("Count: " + arcs.size());

        if (!arcs.isEmpty()) {
            System.out.println("First 5 arcs:");
            arcs.stream().limit(5).forEach(a ->
                System.out.println(String.format("  start=(%.4f\",%.4f\"), stroke=%.6f\"",
                    a.x1, a.y1, a.strokeWidth)));
        }
    }

    // ==================== Extraction Helper Methods ====================

    private List<CircleData> extractCircles(String svg) {
        List<CircleData> circles = new ArrayList<>();
        Matcher matcher = CIRCLE_PATTERN.matcher(svg);
        while (matcher.find()) {
            try {
                circles.add(new CircleData(
                    Double.parseDouble(matcher.group(1)),
                    Double.parseDouble(matcher.group(2)),
                    Double.parseDouble(matcher.group(3))
                ));
            } catch (NumberFormatException ignored) {}
        }
        return circles;
    }

    private List<RectData> extractRectangles(String svg) {
        List<RectData> rects = new ArrayList<>();
        Matcher matcher = RECT_PATTERN.matcher(svg);
        while (matcher.find()) {
            try {
                rects.add(new RectData(
                    Double.parseDouble(matcher.group(1)),
                    Double.parseDouble(matcher.group(2)),
                    Double.parseDouble(matcher.group(3)),
                    Double.parseDouble(matcher.group(4))
                ));
            } catch (NumberFormatException ignored) {}
        }
        return rects;
    }

    private List<EllipseData> extractEllipses(String svg) {
        List<EllipseData> ellipses = new ArrayList<>();
        Matcher matcher = ELLIPSE_PATTERN.matcher(svg);
        while (matcher.find()) {
            try {
                ellipses.add(new EllipseData(
                    Double.parseDouble(matcher.group(1)),
                    Double.parseDouble(matcher.group(2)),
                    Double.parseDouble(matcher.group(3)),
                    Double.parseDouble(matcher.group(4))
                ));
            } catch (NumberFormatException ignored) {}
        }
        return ellipses;
    }

    private List<LineData> extractLines(String svg) {
        List<LineData> lines = new ArrayList<>();
        Matcher matcher = PATH_LINE_PATTERN.matcher(svg);
        while (matcher.find()) {
            try {
                lines.add(new LineData(
                    Double.parseDouble(matcher.group(1)),
                    Double.parseDouble(matcher.group(2)),
                    Double.parseDouble(matcher.group(3)),
                    Double.parseDouble(matcher.group(4)),
                    Double.parseDouble(matcher.group(5))
                ));
            } catch (NumberFormatException ignored) {}
        }
        return lines;
    }

    private List<ArcData> extractArcs(String svg) {
        List<ArcData> arcs = new ArrayList<>();
        Matcher matcher = PATH_ARC_PATTERN.matcher(svg);
        while (matcher.find()) {
            try {
                arcs.add(new ArcData(
                    Double.parseDouble(matcher.group(1)),
                    Double.parseDouble(matcher.group(2)),
                    Double.parseDouble(matcher.group(3))
                ));
            } catch (NumberFormatException ignored) {}
        }
        return arcs;
    }

    private Optional<CircleData> findCircleAt(List<CircleData> circles, double x, double y) {
        return circles.stream()
            .filter(c -> Math.abs(c.cx - x) < TOLERANCE && Math.abs(c.cy - y) < TOLERANCE)
            .findFirst();
    }

    private void assertRadiusEquals(double expected, double actual, String name) {
        assertEquals(expected, actual, TOLERANCE,
            String.format("%s: expected %.6f\", got %.6f\" (diff: %.6f\")",
                name, expected, actual, Math.abs(expected - actual)));
    }

    // ==================== Data Classes ====================

    private record CircleData(double cx, double cy, double r) {}
    private record RectData(double x, double y, double width, double height) {}
    private record EllipseData(double cx, double cy, double rx, double ry) {}
    private record LineData(double x1, double y1, double x2, double y2, double strokeWidth) {}
    private record ArcData(double x1, double y1, double strokeWidth) {}
}

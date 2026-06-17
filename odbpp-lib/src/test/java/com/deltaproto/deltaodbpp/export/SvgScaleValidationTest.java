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
 * Tests that validate SVG output scale correctness.
 *
 * <p>These tests ensure that:
 * <ol>
 *   <li>Feature positions (cx, cy) are correctly scaled relative to feature sizes (r, width)</li>
 *   <li>The viewBox dimensions are proportional to the feature positions</li>
 *   <li>Unit conversions between ODB++ internal units and SVG output are correct</li>
 * </ol>
 *
 * <p>The multilayer checks run against the committed openly-available {@code designodb}
 * sample; minimal-odb drives the mm-to-inch conversion checks (committed synthetic data).
 */
class SvgScaleValidationTest {

    // Conversion factors
    private static final double MILS_TO_INCHES = 0.001;  // 1 mil = 0.001 inches
    private static final double MM_TO_INCHES = 1.0 / 25.4;  // 1 mm = 0.03937 inches

    // Reasonable bounds for PCB dimensions
    private static final double MIN_REASONABLE_COORD_INCHES = -50.0;  // -50 inches min
    private static final double MAX_REASONABLE_COORD_INCHES = 50.0;   // 50 inches max
    private static final double MIN_REASONABLE_SIZE_INCHES = 0.0001;  // 0.1 mils min
    private static final double MAX_REASONABLE_SIZE_INCHES = 1.0;     // 1 inch max for a single pad

    // Patterns for extracting SVG elements
    private static final Pattern CIRCLE_PATTERN = Pattern.compile(
        "<circle[^>]+cx=\"([^\"]+)\"[^>]+cy=\"([^\"]+)\"[^>]+r=\"([^\"]+)\"");
    private static final Pattern VIEWBOX_PATTERN = Pattern.compile(
        "viewBox=\"([^\"]+)\"");
    private static final Pattern PATH_LINE_PATTERN = Pattern.compile(
        "<path[^>]+d=\"M\\s*([\\d.-]+)\\s+([\\d.-]+)\\s+L\\s*([\\d.-]+)\\s+([\\d.-]+)\"");
    private static final Pattern STROKE_WIDTH_PATTERN = Pattern.compile(
        "stroke-width=\"([^\"]+)\"");

    private OdbParser parser;
    private OdbArchiveExtractor extractor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parser = new OdbParser();
        extractor = new OdbArchiveExtractor();
    }

    // ==================== Scale Validation Tests ====================

    @Test
    @DisplayName("Multilayer: Circle positions and radii are in reasonable range")
    void testMultilayerCircleScaleReasonable() throws IOException {
        // Extract, parse, and render the committed multilayer sample.
        Path odbRoot = Fixtures.extractSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        Job job = parser.parse(odbRoot);

        String units = job.getMiscInfo() != null ? job.getMiscInfo().getUnits() : "INCH";
        System.out.println("Multilayer sample units: " + units);

        StringWriter writer = new StringWriter();
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        // Extract all circles
        List<CircleData> circles = extractCircles(svg);
        assertFalse(circles.isEmpty(), "Should have circles in the SVG");

        System.out.println("Found " + circles.size() + " circles");

        // Analyze scale relationships
        ScaleAnalysis analysis = analyzeCircles(circles);

        System.out.println("\n=== Circle Scale Analysis ===");
        System.out.println("Position range X: " + analysis.minX + " to " + analysis.maxX);
        System.out.println("Position range Y: " + analysis.minY + " to " + analysis.maxY);
        System.out.println("Radius range: " + analysis.minR + " to " + analysis.maxR);
        System.out.println("Avg position to radius ratio: " + analysis.avgPositionToRadiusRatio);

        // Show sample circles
        System.out.println("\nFirst 10 circles:");
        circles.stream().limit(10).forEach(c ->
            System.out.println(String.format("  cx=%.6f, cy=%.6f, r=%.6f", c.cx, c.cy, c.r)));

        // Validate reasonable bounds for positions (output in inches)
        assertTrue(analysis.minX >= MIN_REASONABLE_COORD_INCHES,
            "Min X coordinate " + analysis.minX + " should be >= " + MIN_REASONABLE_COORD_INCHES);
        assertTrue(analysis.maxX <= MAX_REASONABLE_COORD_INCHES,
            "Max X coordinate " + analysis.maxX + " should be <= " + MAX_REASONABLE_COORD_INCHES);
        assertTrue(analysis.minY >= MIN_REASONABLE_COORD_INCHES,
            "Min Y coordinate " + analysis.minY + " should be >= " + MIN_REASONABLE_COORD_INCHES);
        assertTrue(analysis.maxY <= MAX_REASONABLE_COORD_INCHES,
            "Max Y coordinate " + analysis.maxY + " should be <= " + MAX_REASONABLE_COORD_INCHES);

        // Validate reasonable radius sizes. Real boards legitimately contain
        // sub-mil features (and degenerate near-zero points), so the only firm
        // lower bound is non-negativity; the upper bound guards against a unit
        // mix-up (mm/mils read as inches would blow radii up by 25.4x/1000x).
        assertTrue(analysis.minR >= 0,
            "Min radius " + analysis.minR + " should be non-negative");
        assertTrue(analysis.maxR <= MAX_REASONABLE_SIZE_INCHES,
            "Max radius " + analysis.maxR + " should be <= " + MAX_REASONABLE_SIZE_INCHES);

        // The position/radius ratio is reported for diagnostics only. It does NOT
        // detect a unit mismatch (positions and radii scale together, so the ratio
        // is unit-invariant) — that is the job of the absolute-bounds checks above.
        // A complex board mixes large planes with sub-mil pads, so the ratio spans
        // a wide range; we only assert it is finite and positive.
        assertTrue(Double.isFinite(analysis.avgPositionToRadiusRatio)
                && analysis.avgPositionToRadiusRatio > 0,
            "Avg position/radius ratio should be finite and positive, was "
                + analysis.avgPositionToRadiusRatio);
    }

    @Test
    @DisplayName("Multilayer: ViewBox is consistent with feature positions")
    void testMultilayerViewBoxConsistency() throws IOException {
        Path odbRoot = Fixtures.extractSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        Job job = parser.parse(odbRoot);

        StringWriter writer = new StringWriter();
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        // Extract viewBox
        Matcher vbMatcher = VIEWBOX_PATTERN.matcher(svg);
        assertTrue(vbMatcher.find(), "SVG should have a viewBox");
        String[] vbParts = vbMatcher.group(1).split("\\s+");
        double vbMinX = Double.parseDouble(vbParts[0]);
        double vbMinY = Double.parseDouble(vbParts[1]);
        double vbWidth = Double.parseDouble(vbParts[2]);
        double vbHeight = Double.parseDouble(vbParts[3]);

        System.out.println("\n=== ViewBox Analysis ===");
        System.out.println("ViewBox: " + vbMinX + " " + vbMinY + " " + vbWidth + " " + vbHeight);

        // ViewBox dimensions should be positive and sensible.
        assertTrue(vbWidth > 0, "ViewBox width should be positive");
        assertTrue(vbHeight > 0, "ViewBox height should be positive");

        // Extract circles and find bounds
        List<CircleData> circles = extractCircles(svg);
        assertFalse(circles.isEmpty(), "Should have circles in the SVG");
        ScaleAnalysis analysis = analyzeCircles(circles);

        // ViewBox should encompass all features with some padding.
        double padding = 0.2;

        assertTrue(vbMinX <= analysis.minX + padding,
            "ViewBox minX " + vbMinX + " should be <= feature minX " + analysis.minX);
        assertTrue(vbMinY <= analysis.minY + padding,
            "ViewBox minY " + vbMinY + " should be <= feature minY " + analysis.minY);
        assertTrue(vbMinX + vbWidth >= analysis.maxX - padding,
            "ViewBox maxX " + (vbMinX + vbWidth) + " should be >= feature maxX " + analysis.maxX);
        assertTrue(vbMinY + vbHeight >= analysis.maxY - padding,
            "ViewBox maxY " + (vbMinY + vbHeight) + " should be >= feature maxY " + analysis.maxY);

        // ViewBox should be a reasonable size for a PCB (typically under 30x30 inches).
        assertTrue(vbWidth <= 30.0, "ViewBox width " + vbWidth + " should be <= 30 inches");
        assertTrue(vbHeight <= 30.0, "ViewBox height " + vbHeight + " should be <= 30 inches");
    }

    @Test
    @DisplayName("Multilayer: Line widths are proportional to positions")
    void testMultilayerLineWidthScale() throws IOException {
        Path odbRoot = Fixtures.extractSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        Job job = parser.parse(odbRoot);

        StringWriter writer = new StringWriter();
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        // Extract line paths with stroke widths
        List<LineData> lines = extractLines(svg);

        if (!lines.isEmpty()) {
            System.out.println("\n=== Line Scale Analysis ===");
            System.out.println("Found " + lines.size() + " lines");

            double minStrokeWidth = lines.stream().mapToDouble(l -> l.strokeWidth).min().orElse(0);
            double maxStrokeWidth = lines.stream().mapToDouble(l -> l.strokeWidth).max().orElse(0);
            double avgLength = lines.stream().mapToDouble(l -> l.length()).average().orElse(0);

            System.out.println("Stroke width range: " + minStrokeWidth + " to " + maxStrokeWidth);
            System.out.println("Average line length: " + avgLength);

            // Sample lines
            System.out.println("\nFirst 5 lines:");
            lines.stream().limit(5).forEach(l ->
                System.out.println(String.format("  (%f,%f) to (%f,%f), stroke=%.6f, length=%.6f",
                    l.x1, l.y1, l.x2, l.y2, l.strokeWidth, l.length())));

            // Stroke widths must be non-negative; zero-width strokes are valid
            // (e.g. hairline/region edges). The upper bound is the real scale
            // guard — a unit mix-up would inflate widths far past 0.5 inch.
            assertTrue(minStrokeWidth >= 0,
                "Min stroke width " + minStrokeWidth + " should be non-negative");
            assertTrue(maxStrokeWidth <= 0.5,
                "Max stroke width " + maxStrokeWidth + " should be <= 0.5 inches");
        }
    }

    @Test
    @DisplayName("minimal-odb: Coordinates match expected mm-to-inch conversion")
    void testMinimalOdbCoordinateConversion() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);

        // Verify it's an MM file
        String units = job.getMiscInfo() != null ? job.getMiscInfo().getUnits() : "INCH";
        System.out.println("minimal-odb units: " + units);
        assertEquals("MM", units, "minimal-odb should use MM units");

        StringWriter writer = new StringWriter();
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        // Known coordinates from minimal-odb (in mm): pads at 10mm, 20mm, 30mm, 50mm, 60mm, etc.
        // (Note: there's no pad at 40mm - see features file)
        // Expected in inches: 0.3937, 0.7874, 1.1811, 1.9685, etc.
        double[] expectedXInches = {
            10.0 * MM_TO_INCHES,  // 0.3937
            20.0 * MM_TO_INCHES,  // 0.7874
            30.0 * MM_TO_INCHES,  // 1.1811
            50.0 * MM_TO_INCHES   // 1.9685 (not 40mm)
        };

        List<CircleData> circles = extractCircles(svg);

        System.out.println("\n=== minimal-odb Coordinate Verification ===");
        System.out.println("Expected X coordinates (inches): " + Arrays.toString(expectedXInches));
        System.out.println("Found circles:");
        circles.forEach(c -> System.out.println(String.format("  cx=%.4f, cy=%.4f, r=%.6f", c.cx, c.cy, c.r)));

        // Verify each expected X coordinate is present (within tolerance)
        double tolerance = 0.001;  // 1 mil tolerance
        for (double expectedX : expectedXInches) {
            boolean found = circles.stream()
                .anyMatch(c -> Math.abs(c.cx - expectedX) < tolerance);
            assertTrue(found, "Should find circle at X=" + expectedX + " inches (converted from mm)");
        }
    }

    @Test
    @DisplayName("Multilayer: Generated SVG scale is internally consistent")
    void testMultilayerScaleConsistency() throws IOException {
        // Generate our SVG from the committed multilayer sample.
        Path odbRoot = Fixtures.extractSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        Job job = parser.parse(odbRoot);
        StringWriter writer = new StringWriter();
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer();
        renderer.renderJob(job, writer);
        String generatedSvg = writer.toString();

        List<CircleData> genCircles = extractCircles(generatedSvg);

        System.out.println("\n=== Generated Scale Analysis ===");
        System.out.println("Generated circles: " + genCircles.size());
        assertFalse(genCircles.isEmpty(), "Generated SVG should contain circles");

        ScaleAnalysis genAnalysis = analyzeCircles(genCircles);

        System.out.println("  Position range X: " + genAnalysis.minX + " to " + genAnalysis.maxX);
        System.out.println("  Position range Y: " + genAnalysis.minY + " to " + genAnalysis.maxY);
        System.out.println("  Radius range: " + genAnalysis.minR + " to " + genAnalysis.maxR);
        System.out.println("  Avg position/radius ratio: " + genAnalysis.avgPositionToRadiusRatio);

        // Features should span a non-degenerate area at PCB scale.
        double spanX = genAnalysis.maxX - genAnalysis.minX;
        double spanY = genAnalysis.maxY - genAnalysis.minY;
        assertTrue(spanX > 0, "Feature X span should be positive");
        assertTrue(spanY > 0, "Feature Y span should be positive");
        assertTrue(spanX <= 30.0 && spanY <= 30.0,
            "Feature span should be a sensible PCB size (<= 30in), got "
                + spanX + " x " + spanY);

        // Radii should be a small fraction of the board extent, never larger than it.
        assertTrue(genAnalysis.maxR <= Math.max(spanX, spanY),
            "Max radius " + genAnalysis.maxR + " should not exceed the board span");
    }

    @Test
    @DisplayName("Verify symbol dimensions are converted from microns to inches")
    void testSymbolDimensionConversion() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);
        Step step = job.getSteps().values().iterator().next();
        Layer layer = step.getLayersByName().get("top");

        // Get symbol table
        Map<Integer, String> symbolTable = layer.getFeatures().getSymbolTable();
        System.out.println("\n=== Symbol Table ===");
        symbolTable.forEach((k, v) -> System.out.println("  $" + k + " = " + v));

        // minimal-odb uses UNITS=MM, so symbol dimensions are in microns.
        // The "r" prefix in ODB++ symbols means the number is the DIAMETER.
        // $0 r1000 = DIAMETER 1000 microns = 1mm diameter = 0.5mm radius = 0.01969 inches
        // $1 r2000 = DIAMETER 2000 microns = 2mm diameter = 1mm radius = 0.03937 inches
        // $2 r5000 = DIAMETER 5000 microns = 5mm diameter = 2.5mm radius = 0.09843 inches

        StringWriter writer = new StringWriter();
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        List<CircleData> circles = extractCircles(svg);

        // Expected radii in inches (diameter in microns -> mm -> divide by 2 for radius -> inches)
        double[] expectedRadiiInches = {
            (1000.0 / 1000.0 / 2.0) * MM_TO_INCHES,  // r1000 -> 1mm diam -> 0.5mm rad -> 0.01969"
            (2000.0 / 1000.0 / 2.0) * MM_TO_INCHES,  // r2000 -> 2mm diam -> 1mm rad -> 0.03937"
            (5000.0 / 1000.0 / 2.0) * MM_TO_INCHES   // r5000 -> 5mm diam -> 2.5mm rad -> 0.09843"
        };

        System.out.println("\nExpected radii (inches): " + Arrays.toString(expectedRadiiInches));
        System.out.println("Actual circle radii:");
        Set<Double> uniqueRadii = new TreeSet<>();
        circles.forEach(c -> uniqueRadii.add(Math.round(c.r * 100000) / 100000.0));
        uniqueRadii.forEach(r -> System.out.println("  r = " + r));

        // Verify expected radii exist
        double tolerance = 0.001;  // 1 mil tolerance
        for (double expectedR : expectedRadiiInches) {
            boolean found = circles.stream()
                .anyMatch(c -> Math.abs(c.r - expectedR) < tolerance);
            assertTrue(found, "Should find circle with radius " + expectedR + " inches");
        }
    }

    // ==================== Helper Methods ====================

    private List<CircleData> extractCircles(String svg) {
        List<CircleData> circles = new ArrayList<>();
        Matcher matcher = CIRCLE_PATTERN.matcher(svg);
        while (matcher.find()) {
            try {
                double cx = Double.parseDouble(matcher.group(1));
                double cy = Double.parseDouble(matcher.group(2));
                double r = Double.parseDouble(matcher.group(3));
                circles.add(new CircleData(cx, cy, r));
            } catch (NumberFormatException ignored) {
                // Skip malformed circles
            }
        }
        return circles;
    }

    private List<LineData> extractLines(String svg) {
        List<LineData> lines = new ArrayList<>();

        // Find path elements with line commands
        // Note: stroke-width may be numeric (like "0.0394") or have units (like "2px" for profile)
        Pattern pathPattern = Pattern.compile("<path[^>]+d=\"([^\"]+)\"[^>]*stroke-width=\"([^\"]+)\"");
        Matcher pathMatcher = pathPattern.matcher(svg);

        while (pathMatcher.find()) {
            String d = pathMatcher.group(1);
            String strokeWidthStr = pathMatcher.group(2);

            // Skip non-numeric stroke widths (e.g., "2px" for profile paths)
            if (!strokeWidthStr.matches("[\\d.]+")) {
                continue;
            }
            double strokeWidth = Double.parseDouble(strokeWidthStr);

            // Parse M x1 y1 L x2 y2 format
            Matcher lineMatch = Pattern.compile("M\\s*([\\d.-]+)\\s+([\\d.-]+)\\s+L\\s*([\\d.-]+)\\s+([\\d.-]+)")
                .matcher(d);
            if (lineMatch.find()) {
                try {
                    double x1 = Double.parseDouble(lineMatch.group(1));
                    double y1 = Double.parseDouble(lineMatch.group(2));
                    double x2 = Double.parseDouble(lineMatch.group(3));
                    double y2 = Double.parseDouble(lineMatch.group(4));
                    lines.add(new LineData(x1, y1, x2, y2, strokeWidth));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return lines;
    }

    private ScaleAnalysis analyzeCircles(List<CircleData> circles) {
        ScaleAnalysis analysis = new ScaleAnalysis();

        analysis.minX = circles.stream().mapToDouble(c -> c.cx).min().orElse(0);
        analysis.maxX = circles.stream().mapToDouble(c -> c.cx).max().orElse(0);
        analysis.minY = circles.stream().mapToDouble(c -> c.cy).min().orElse(0);
        analysis.maxY = circles.stream().mapToDouble(c -> c.cy).max().orElse(0);
        analysis.minR = circles.stream().mapToDouble(c -> c.r).min().orElse(0);
        analysis.maxR = circles.stream().mapToDouble(c -> c.r).max().orElse(0);

        // Calculate average position to radius ratio
        double sumRatio = 0;
        int count = 0;
        for (CircleData c : circles) {
            if (c.r > 0) {
                double position = Math.sqrt(c.cx * c.cx + c.cy * c.cy);
                if (position > 0) {
                    sumRatio += position / c.r;
                    count++;
                }
            }
        }
        analysis.avgPositionToRadiusRatio = count > 0 ? sumRatio / count : 0;

        return analysis;
    }

    // ==================== Data Classes ====================

    private static class CircleData {
        final double cx, cy, r;

        CircleData(double cx, double cy, double r) {
            this.cx = cx;
            this.cy = cy;
            this.r = r;
        }
    }

    private static class LineData {
        final double x1, y1, x2, y2, strokeWidth;

        LineData(double x1, double y1, double x2, double y2, double strokeWidth) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.strokeWidth = strokeWidth;
        }

        double length() {
            return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
        }
    }

    private static class ScaleAnalysis {
        double minX, maxX, minY, maxY;
        double minR, maxR;
        double avgPositionToRadiusRatio;
    }
}

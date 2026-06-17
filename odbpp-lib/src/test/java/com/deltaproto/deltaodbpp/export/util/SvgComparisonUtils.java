package com.deltaproto.deltaodbpp.export.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

/**
 * Utility class with assertion methods for comparing SVG elements.
 * Provides tolerance-based coordinate matching for floating point comparisons.
 */
public class SvgComparisonUtils {

    /** Default tolerance for coordinate comparisons (in the SVG coordinate units) */
    public static final double DEFAULT_TOLERANCE = 0.0001;

    /** Conversion factor from mm to inches */
    public static final double MM_TO_INCH = 1.0 / 25.4;

    /**
     * Convert mm to inches (SVG typically uses inches in this project)
     */
    public static double mmToInch(double mm) {
        return mm * MM_TO_INCH;
    }

    /**
     * Assert that a circle exists at the given position within tolerance.
     */
    public static void assertCircleAt(SvgElementExtractor extractor, double x, double y, double tolerance) {
        Optional<SvgElementExtractor.CircleElement> circle = extractor.findCircleAt(x, y, tolerance);
        assertTrue(circle.isPresent(),
            String.format("Expected circle at (%.6f, %.6f) with tolerance %.6f, but none found. " +
                "Available circles: %d", x, y, tolerance, extractor.getCircleCount()));
    }

    /**
     * Assert that a circle exists at the given position (using direct coordinates, no unit conversion).
     */
    public static void assertCircleAtDirect(SvgElementExtractor extractor, double x, double y, double tolerance) {
        Optional<SvgElementExtractor.CircleElement> circle = extractor.findCircleAt(x, y, tolerance);
        assertTrue(circle.isPresent(),
            String.format("Expected circle at (%.2f, %.2f) with tolerance %.2f, but none found. " +
                "Available circles: %d", x, y, tolerance, extractor.getCircleCount()));
    }

    /**
     * Assert that a circle exists at the given position (mm) with the expected radius (mm).
     * Coordinates are converted to inches for comparison.
     */
    public static void assertCircleAtMm(SvgElementExtractor extractor,
                                        double xMm, double yMm, double radiusMm, double toleranceMm) {
        double xInch = mmToInch(xMm);
        double yInch = mmToInch(yMm);
        double rInch = mmToInch(radiusMm);
        double tolInch = mmToInch(toleranceMm);

        Optional<SvgElementExtractor.CircleElement> circle = extractor.findCircleAt(xInch, yInch, tolInch);
        assertTrue(circle.isPresent(),
            String.format("Expected circle at (%.2fmm, %.2fmm) = (%.6fin, %.6fin), but none found. " +
                "Available circles: %d", xMm, yMm, xInch, yInch, extractor.getCircleCount()));

        if (circle.isPresent()) {
            assertEquals(rInch, circle.get().r, tolInch,
                String.format("Circle at (%.2fmm, %.2fmm) has wrong radius. Expected %.2fmm (%.6fin), got %.6fin",
                    xMm, yMm, radiusMm, rInch, circle.get().r));
        }
    }

    /**
     * Assert that the extractor found the expected number of circles.
     */
    public static void assertCircleCount(SvgElementExtractor extractor, int expectedCount) {
        assertEquals(expectedCount, extractor.getCircleCount(),
            String.format("Expected %d circles, found %d", expectedCount, extractor.getCircleCount()));
    }

    /**
     * Assert that the extractor found at least the expected number of circles.
     */
    public static void assertMinCircleCount(SvgElementExtractor extractor, int minCount) {
        assertTrue(extractor.getCircleCount() >= minCount,
            String.format("Expected at least %d circles, found %d", minCount, extractor.getCircleCount()));
    }

    /**
     * Assert that the extractor found the expected number of paths.
     */
    public static void assertPathCount(SvgElementExtractor extractor, int expectedCount) {
        assertEquals(expectedCount, extractor.getPathCount(),
            String.format("Expected %d paths, found %d", expectedCount, extractor.getPathCount()));
    }

    /**
     * Assert that the extractor found at least the expected number of paths.
     */
    public static void assertMinPathCount(SvgElementExtractor extractor, int minCount) {
        assertTrue(extractor.getPathCount() >= minCount,
            String.format("Expected at least %d paths, found %d", minCount, extractor.getPathCount()));
    }

    /**
     * Assert that there are paths containing arc commands (A).
     */
    public static void assertHasArcPaths(SvgElementExtractor extractor) {
        int arcPathCount = extractor.countPathsWithCommand("A");
        assertTrue(arcPathCount > 0,
            "Expected at least one path with arc command (A), but found none");
    }

    /**
     * Assert that there are closed paths (with Z command).
     */
    public static void assertHasClosedPaths(SvgElementExtractor extractor) {
        int closedPathCount = extractor.countPathsWithCommand("Z");
        assertTrue(closedPathCount > 0,
            "Expected at least one closed path (with Z command), but found none");
    }

    /**
     * Assert that the extractor found paths with M (moveto), L (lineto) commands.
     */
    public static void assertHasLinePaths(SvgElementExtractor extractor) {
        int linePathCount = extractor.countPathsWithCommand("L");
        assertTrue(linePathCount > 0,
            "Expected at least one path with line command (L), but found none");
    }

    /**
     * Assert that a layer with the given ID exists.
     */
    public static void assertLayerExists(SvgElementExtractor extractor, String layerId) {
        boolean found = extractor.getLayers().stream()
            .anyMatch(l -> l.id.equals(layerId));
        assertTrue(found,
            String.format("Expected layer with id '%s', but not found. Available layers: %s",
                layerId, extractor.getLayers()));
    }

    /**
     * Assert that the number of circles with a specific radius matches expectation.
     */
    public static void assertCircleCountWithRadiusMm(SvgElementExtractor extractor,
                                                      double radiusMm, int expectedCount, double toleranceMm) {
        double rInch = mmToInch(radiusMm);
        double tolInch = mmToInch(toleranceMm);
        int count = extractor.findCirclesWithRadius(rInch, tolInch).size();
        assertEquals(expectedCount, count,
            String.format("Expected %d circles with radius %.2fmm, found %d",
                expectedCount, radiusMm, count));
    }

    /**
     * Compare two SVG element extractors for similar structure.
     * Allows for tolerance in element counts.
     */
    public static void assertSimilarStructure(SvgElementExtractor expected,
                                               SvgElementExtractor actual,
                                               double countTolerance) {
        // Compare circle counts within tolerance
        int expectedCircles = expected.getCircleCount();
        int actualCircles = actual.getCircleCount();
        int circleDiff = Math.abs(expectedCircles - actualCircles);
        assertTrue(circleDiff <= expectedCircles * countTolerance,
            String.format("Circle count differs too much. Expected %d, got %d (tolerance %.0f%%)",
                expectedCircles, actualCircles, countTolerance * 100));

        // Compare path counts within tolerance
        int expectedPaths = expected.getPathCount();
        int actualPaths = actual.getPathCount();
        int pathDiff = Math.abs(expectedPaths - actualPaths);
        assertTrue(pathDiff <= expectedPaths * countTolerance,
            String.format("Path count differs too much. Expected %d, got %d (tolerance %.0f%%)",
                expectedPaths, actualPaths, countTolerance * 100));
    }

    /**
     * Assert that a line exists at the given endpoints within tolerance.
     */
    public static void assertLineAt(SvgElementExtractor extractor,
                                    double x1, double y1, double x2, double y2, double tolerance) {
        Optional<SvgElementExtractor.LineElement> line = extractor.findLineAt(x1, y1, x2, y2, tolerance);
        assertTrue(line.isPresent(),
            String.format("Expected line from (%.2f, %.2f) to (%.2f, %.2f) with tolerance %.2f, but none found. " +
                "Available lines: %d", x1, y1, x2, y2, tolerance, extractor.getLineCount()));
    }

    /**
     * Assert that the extractor found the expected number of lines.
     */
    public static void assertLineCount(SvgElementExtractor extractor, int expectedCount) {
        assertEquals(expectedCount, extractor.getLineCount(),
            String.format("Expected %d lines, found %d", expectedCount, extractor.getLineCount()));
    }

    /**
     * Assert that the extractor found at least the expected number of lines.
     */
    public static void assertMinLineCount(SvgElementExtractor extractor, int minCount) {
        assertTrue(extractor.getLineCount() >= minCount,
            String.format("Expected at least %d lines, found %d", minCount, extractor.getLineCount()));
    }

    /**
     * Assert that the extractor found the expected number of rectangles.
     */
    public static void assertRectCount(SvgElementExtractor extractor, int expectedCount) {
        assertEquals(expectedCount, extractor.getRectCount(),
            String.format("Expected %d rectangles, found %d", expectedCount, extractor.getRectCount()));
    }

    /**
     * Assert that the extractor found at least the expected number of rectangles.
     */
    public static void assertMinRectCount(SvgElementExtractor extractor, int minCount) {
        assertTrue(extractor.getRectCount() >= minCount,
            String.format("Expected at least %d rectangles, found %d", minCount, extractor.getRectCount()));
    }

    /**
     * Assert that a rectangle exists at the given position within tolerance.
     */
    public static void assertRectAt(SvgElementExtractor extractor, double x, double y, double tolerance) {
        Optional<SvgElementExtractor.RectElement> rect = extractor.findRectAt(x, y, tolerance);
        assertTrue(rect.isPresent(),
            String.format("Expected rectangle at (%.2f, %.2f) with tolerance %.2f, but none found. " +
                "Available rects: %d", x, y, tolerance, extractor.getRectCount()));
    }

    /**
     * Assert that the expected number of arc paths exists.
     */
    public static void assertArcPathCount(SvgElementExtractor extractor, int expectedCount) {
        int arcCount = extractor.getArcPaths().size();
        assertEquals(expectedCount, arcCount,
            String.format("Expected %d arc paths, found %d", expectedCount, arcCount));
    }

    /**
     * Assert that the expected number of closed paths exists.
     */
    public static void assertClosedPathCount(SvgElementExtractor extractor, int expectedCount) {
        int closedCount = extractor.getClosedPaths().size();
        assertEquals(expectedCount, closedCount,
            String.format("Expected %d closed paths, found %d", expectedCount, closedCount));
    }

    /**
     * Print summary of extracted elements for debugging.
     */
    public static void printSummary(SvgElementExtractor extractor) {
        System.out.println("=== SVG Element Summary ===");
        System.out.println("Circles: " + extractor.getCircleCount());
        System.out.println("Lines: " + extractor.getLineCount());
        System.out.println("Paths: " + extractor.getPathCount());
        System.out.println("Rects: " + extractor.getRectCount());
        System.out.println("Layers: " + extractor.getLayers().size());
        System.out.println("Arc paths: " + extractor.getArcPaths().size());
        System.out.println("Closed paths: " + extractor.getClosedPaths().size());

        if (!extractor.getLayers().isEmpty()) {
            System.out.println("Layer IDs: " + extractor.getLayers());
        }

        // Print first few circles for debugging
        if (!extractor.getCircles().isEmpty()) {
            System.out.println("First 5 circles:");
            extractor.getCircles().stream().limit(5).forEach(c ->
                System.out.println("  " + c));
        }

        // Print first few lines for debugging
        if (!extractor.getLines().isEmpty()) {
            System.out.println("First 5 lines:");
            extractor.getLines().stream().limit(5).forEach(l ->
                System.out.println("  " + l));
        }
    }
}

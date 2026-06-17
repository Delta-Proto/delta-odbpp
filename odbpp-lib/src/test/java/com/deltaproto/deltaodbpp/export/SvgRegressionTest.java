package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.export.util.SvgElementExtractor;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.parser.OdbParser;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for SVG export.
 *
 * <p>The minimal-odb baselines use the committed synthetic fixture, so they assert
 * exact element counts and positions. The multilayer baselines use the committed
 * openly-available {@code designodb} sample and assert robust structural properties
 * (counts &gt; 0, expected layer/element types present) rather than board-specific
 * magic numbers.
 */
class SvgRegressionTest {

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

    // ==================== minimal-odb Baselines ====================

    /**
     * Baseline values for minimal-odb:
     * - 13 circles (pads)
     * - 3 line elements
     * - 2 path elements (arc + profile)
     * - 2 layers (profile + top)
     */
    @Test
    @DisplayName("Regression: minimal-odb element counts match baseline")
    void testMinimalOdbBaseline() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);
        StringWriter writer = new StringWriter();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        SvgElementExtractor extractor = SvgElementExtractor.fromString(svg);

        // Circle count: exactly 13 pads
        assertEquals(13, extractor.getCircleCount(),
            "minimal-odb should have exactly 13 circles (pads)");

        // Line count: 3 lines (rendered as <line> elements or paths)
        int lineAndPathCount = extractor.getLineCount() + extractor.getPathCount();
        assertTrue(lineAndPathCount >= 4,
            "minimal-odb should have at least 4 lines+paths (3 lines + 1 arc + profile)");

        // Layer count: 2 layers (profile + top)
        assertEquals(2, extractor.getLayers().size(),
            "minimal-odb should have exactly 2 layers (profile + top)");

        // Validate layer names
        assertTrue(extractor.getLayers().stream().anyMatch(l -> l.id.contains("profile")),
            "Should have profile layer");
        assertTrue(extractor.getLayers().stream().anyMatch(l -> l.id.contains("top")),
            "Should have top layer");
    }

    @Test
    @DisplayName("Regression: minimal-odb pad positions are stable")
    void testMinimalOdbPadPositions() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);
        StringWriter writer = new StringWriter();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        SvgElementExtractor extractor = SvgElementExtractor.fromString(svg);

        // Main 3 pads at (10mm,10mm), (20mm,10mm), (30mm,10mm) - converted to inches
        double tolerance = 0.01;  // tolerance in inches
        assertTrue(extractor.findCircleAt(10 * MM_TO_INCH, 10 * MM_TO_INCH, tolerance).isPresent(),
            "Should have pad at (10, 10)");
        assertTrue(extractor.findCircleAt(20 * MM_TO_INCH, 10 * MM_TO_INCH, tolerance).isPresent(),
            "Should have pad at (20, 10)");
        assertTrue(extractor.findCircleAt(30 * MM_TO_INCH, 10 * MM_TO_INCH, tolerance).isPresent(),
            "Should have pad at (30, 10)");

        // Grid row 1: y=10mm
        for (int x = 50; x <= 90; x += 10) {
            assertTrue(extractor.findCircleAt(x * MM_TO_INCH, 10 * MM_TO_INCH, tolerance).isPresent(),
                "Should have pad at (" + x + ", 10)");
        }

        // Grid row 2: y=20mm
        for (int x = 50; x <= 90; x += 10) {
            assertTrue(extractor.findCircleAt(x * MM_TO_INCH, 20 * MM_TO_INCH, tolerance).isPresent(),
                "Should have pad at (" + x + ", 20)");
        }
    }

    // ==================== Multilayer Baselines (designodb sample) ====================

    /**
     * Structural baseline for the rich multilayer sample: it must render a
     * non-trivial number of features across many layers without throwing.
     */
    @Test
    @DisplayName("Regression: multilayer sample renders a non-trivial structure")
    void testMultilayerBaseline() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        StringWriter writer = new StringWriter();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        SvgElementExtractor extractor = SvgElementExtractor.fromString(svg);

        // A rich multilayer board renders many circular features (pads/vias).
        assertTrue(extractor.getCircleCount() > 0,
            "Multilayer sample should render circular features (pads/vias)");

        // Should have multiple layers.
        assertTrue(extractor.getLayers().size() >= 5,
            "Multilayer sample should have at least 5 rendered layers, got "
                + extractor.getLayers().size());

        // Should have paths (for traces and surfaces).
        assertTrue(extractor.getPathCount() > 100,
            "Multilayer sample should have many path elements for traces");
    }

    @Test
    @DisplayName("Regression: multilayer sample has expected layer types")
    void testMultilayerLayerStructure() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        StringWriter writer = new StringWriter();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        SvgElementExtractor extractor = SvgElementExtractor.fromString(svg);

        // Print layer names for debugging
        System.out.println("Multilayer sample layers: " + extractor.getLayers());

        // Should have signal/copper layers.
        boolean hasSignalLayer = extractor.getLayers().stream()
            .anyMatch(l -> l.id.contains("signal") || l.id.contains("layer"));
        assertTrue(hasSignalLayer, "Should have signal/copper layers");

        // Should have a profile layer.
        boolean hasProfile = extractor.getLayers().stream()
            .anyMatch(l -> l.id.contains("profile"));
        assertTrue(hasProfile, "Should have profile layer");
    }

    // ==================== SVG Structure Tests ====================

    @Test
    @DisplayName("Regression: SVG has valid structure")
    void testSvgStructure() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);
        StringWriter writer = new StringWriter();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        // Has XML declaration
        assertTrue(svg.contains("<?xml"), "SVG should have XML declaration");

        // Has SVG namespace
        assertTrue(svg.contains("xmlns=\"http://www.w3.org/2000/svg\""),
            "SVG should have correct namespace");

        // Has opening and closing svg tags
        assertTrue(svg.contains("<svg"), "SVG should have opening svg tag");
        assertTrue(svg.contains("</svg>"), "SVG should have closing svg tag");

        // No locale issues (dots not commas for decimals in coordinates)
        assertFalse(svg.matches(".*cx=\"[0-9]+,[0-9]+\".*"),
            "SVG should not use comma as decimal separator");
    }

    @Test
    @DisplayName("Regression: SVG viewBox is set correctly")
    void testSvgViewBox() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);
        StringWriter writer = new StringWriter();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        // Should have viewBox attribute
        assertTrue(svg.contains("viewBox="),
            "SVG should have viewBox attribute");
    }

    // ==================== Performance Baseline ====================

    @Test
    @DisplayName("Regression: minimal-odb renders quickly")
    void testMinimalOdbPerformance() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);

        long start = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            StringWriter writer = new StringWriter();
            renderer.renderJob(job, writer);
        }
        long elapsed = System.currentTimeMillis() - start;

        // Should render 10 times in under 2 seconds
        assertTrue(elapsed < 2000,
            String.format("Rendering 10x took %dms, should be under 2000ms", elapsed));
    }

    @Test
    @DisplayName("Regression: multilayer sample renders in reasonable time")
    void testMultilayerPerformance() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        long start = System.currentTimeMillis();
        StringWriter writer = new StringWriter();
        renderer.renderJob(job, writer);
        long elapsed = System.currentTimeMillis() - start;

        // Should render in under 30 seconds even for a rich multilayer board.
        assertTrue(elapsed < 30000,
            String.format("Rendering took %dms, should be under 30000ms", elapsed));

        System.out.println("Multilayer sample rendering time: " + elapsed + "ms");
    }
}

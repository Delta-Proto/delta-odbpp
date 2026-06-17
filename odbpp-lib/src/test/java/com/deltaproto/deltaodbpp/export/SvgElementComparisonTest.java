package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.export.util.SvgComparisonUtils;
import com.deltaproto.deltaodbpp.export.util.SvgElementExtractor;
import com.deltaproto.deltaodbpp.parser.OdbParser;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static com.deltaproto.deltaodbpp.export.util.SvgComparisonUtils.*;

/**
 * Element-level comparison tests for SVG export.
 *
 * <p>These tests verify that individual elements are rendered at the correct
 * coordinates. minimal-odb assertions use the committed synthetic fixture (exact
 * positions); multilayer assertions use the committed openly-available
 * {@code designodb} sample and assert robust structural properties.
 */
class SvgElementComparisonTest {

    // Tolerance for coordinate comparisons (in output units - inches)
    private static final double TOLERANCE = 0.01; // ~0.25mm tolerance in inches

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

    // ==================== Rendered Structure Analysis Tests ====================

    @Test
    @DisplayName("Analyze minimal-odb generated SVG structure")
    void testMinimalOdbGeneratedStructure() throws IOException {
        SvgElementExtractor extractor = renderToExtractor(Fixtures.MINIMAL_ODB);

        // Print summary for analysis
        SvgComparisonUtils.printSummary(extractor);

        // Basic structure assertions
        assertMinCircleCount(extractor, 10);  // At least 10 pads
        assertMinPathCount(extractor, 1);      // At least profile path
    }

    @Test
    @DisplayName("Analyze multilayer generated SVG structure")
    void testMultilayerGeneratedStructure() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        SvgElementExtractor extractor = render(job);

        // Print summary for analysis
        SvgComparisonUtils.printSummary(extractor);

        // A rich multilayer board renders many features.
        assertMinCircleCount(extractor, 100);
        assertMinPathCount(extractor, 10);
    }

    // ==================== minimal-odb Element Tests ====================

    @Test
    @DisplayName("minimal-odb: Generated SVG has correct circle count")
    void testMinimalOdbCircleCount() throws IOException {
        SvgElementExtractor generated = renderToExtractor(Fixtures.MINIMAL_ODB);

        // minimal-odb has 13 pads total:
        // 3 main pads at (10,10), (20,10), (30,10)
        // 10 grid pads: 5 at y=10 (50-90) and 5 at y=20 (50-90)
        assertMinCircleCount(generated, 13);
    }

    @Test
    @DisplayName("minimal-odb: Pad at (10mm, 10mm)")
    void testMinimalOdbPad1() throws IOException {
        SvgElementExtractor generated = renderToExtractor(Fixtures.MINIMAL_ODB);

        // Generated SVG uses inch coordinates (converted from mm)
        // Pad at (10mm, 10mm) - verify circle exists at this position in inches
        assertCircleAtDirect(generated, 10 * MM_TO_INCH, 10 * MM_TO_INCH, TOLERANCE);
    }

    @Test
    @DisplayName("minimal-odb: Pad at (20mm, 10mm)")
    void testMinimalOdbPad2() throws IOException {
        SvgElementExtractor generated = renderToExtractor(Fixtures.MINIMAL_ODB);

        // Generated SVG uses inch coordinates (converted from mm)
        assertCircleAtDirect(generated, 20 * MM_TO_INCH, 10 * MM_TO_INCH, TOLERANCE);
    }

    @Test
    @DisplayName("minimal-odb: Pad at (30mm, 10mm)")
    void testMinimalOdbPad3() throws IOException {
        SvgElementExtractor generated = renderToExtractor(Fixtures.MINIMAL_ODB);

        // Generated SVG uses inch coordinates (converted from mm)
        assertCircleAtDirect(generated, 30 * MM_TO_INCH, 10 * MM_TO_INCH, TOLERANCE);
    }

    @Test
    @DisplayName("minimal-odb: Grid pads at y=10mm row")
    void testMinimalOdbGridPadsRow1() throws IOException {
        SvgElementExtractor generated = renderToExtractor(Fixtures.MINIMAL_ODB);

        // Grid pads at y=10mm: (50,10), (60,10), (70,10), (80,10), (90,10) - in inches
        for (int x = 50; x <= 90; x += 10) {
            assertCircleAtDirect(generated, x * MM_TO_INCH, 10 * MM_TO_INCH, TOLERANCE);
        }
    }

    @Test
    @DisplayName("minimal-odb: Grid pads at y=20mm row")
    void testMinimalOdbGridPadsRow2() throws IOException {
        SvgElementExtractor generated = renderToExtractor(Fixtures.MINIMAL_ODB);

        // Grid pads at y=20mm: (50,20), (60,20), (70,20), (80,20), (90,20) - in inches
        for (int x = 50; x <= 90; x += 10) {
            assertCircleAtDirect(generated, x * MM_TO_INCH, 20 * MM_TO_INCH, TOLERANCE);
        }
    }

    @Test
    @DisplayName("minimal-odb: Has line paths")
    void testMinimalOdbHasLines() throws IOException {
        SvgElementExtractor generated = renderToExtractor(Fixtures.MINIMAL_ODB);

        // Lines are rendered as paths with L command or as <line> elements
        // The features file has 3 lines + 1 arc
        assertTrue(generated.getPathCount() >= 3 || generated.getLineCount() >= 3,
            "Expected at least 3 lines (as paths or line elements)");
    }

    @Test
    @DisplayName("minimal-odb: Has arc path")
    void testMinimalOdbHasArc() throws IOException {
        SvgElementExtractor generated = renderToExtractor(Fixtures.MINIMAL_ODB);

        // Arc should be rendered as path with A command
        assertHasArcPaths(generated);
    }

    // ==================== Generated Structure Comparison ====================

    @Test
    @DisplayName("minimal-odb: Generated renders expected element baseline")
    void testMinimalOdbStructureBaseline() throws IOException {
        SvgElementExtractor generated = renderToExtractor(Fixtures.MINIMAL_ODB);

        System.out.println("=== minimal-odb Generated ===");
        SvgComparisonUtils.printSummary(generated);

        // Key assertion: generated should have at least as many circles as pads in source.
        assertMinCircleCount(generated, 13);
    }

    @Test
    @DisplayName("Multilayer: Generated has rich element counts")
    void testMultilayerElementCounts() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        SvgElementExtractor generated = render(job);

        System.out.println("=== Multilayer Generated ===");
        SvgComparisonUtils.printSummary(generated);

        // A rich multilayer board renders many circular features and trace paths.
        assertTrue(generated.getCircleCount() > 0,
            "Multilayer sample should render circular features");
        assertMinPathCount(generated, 10);
        assertTrue(generated.getLayers().size() >= 5,
            "Multilayer sample should render at least 5 layers, got "
                + generated.getLayers().size());
    }

    // ==================== Helper Methods ====================

    private SvgElementExtractor renderToExtractor(Path odbPath) throws IOException {
        Job job = parser.parse(odbPath);
        return render(job);
    }

    private SvgElementExtractor render(Job job) throws IOException {
        StringWriter writer = new StringWriter();
        renderer.renderJob(job, writer);
        return SvgElementExtractor.fromString(writer.toString());
    }
}

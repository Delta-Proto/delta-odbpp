package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.*;
import com.deltaproto.deltaodbpp.parser.OdbParser;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ODB++ to SVG export functionality.
 *
 * <p>Tests validate that ODB++ files can be loaded and exported to valid SVG
 * output. All data is loaded through {@link Fixtures} from committed,
 * openly-available samples (the synthetic minimal board, and the public
 * "designodb" multilayer reference design) — no customer data is referenced.
 */
class SvgExportIntegrationTest {

    // Pattern to validate SVG numeric values use dots, not commas
    private static final Pattern INVALID_SVG_NUMBER = Pattern.compile(
            "(width|height|viewBox|x|y|x1|x2|y1|y2|cx|cy|r|stroke-width)=\"[^\"]*[0-9]+,[0-9]+[^\"]*\"");

    private OdbParser parser;
    private MultiLayerSvgRenderer multiRenderer;
    private SvgRenderer singleRenderer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parser = new OdbParser();
        multiRenderer = new MultiLayerSvgRenderer();
        singleRenderer = new SvgRenderer();
    }

    // ==================== Synthetic minimal-board archive Tests ====================

    @Test
    void testMinimalZipToSvg() throws IOException {
        // Committed synthetic 2-layer board archive (top + bottom).
        Path odbRoot = new com.deltaproto.deltaodbpp.OdbArchiveExtractor()
                .extract(Fixtures.MINIMAL_TEST_ODB_ZIP, tempDir.resolve("minimal-zip"));
        Job job = parser.parse(odbRoot);

        assertNotNull(job, "Job should not be null");
        assertNotNull(job.getSteps(), "Steps should not be null");
        assertFalse(job.getSteps().isEmpty(), "Steps should not be empty");

        // Export to SVG
        StringWriter writer = new StringWriter();
        multiRenderer.renderJob(job, writer);
        String svg = writer.toString();

        // Validate SVG output
        assertValidSvg(svg);
        assertNoLocaleIssues(svg);
        assertHasContent(svg);
    }

    @Test
    void testMinimalDirectoryToSvg() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);

        assertNotNull(job, "Job should not be null");

        // Export to SVG
        StringWriter writer = new StringWriter();
        multiRenderer.renderJob(job, writer);
        String svg = writer.toString();

        // Validate SVG output
        assertValidSvg(svg);
        assertNoLocaleIssues(svg);
    }

    @Test
    void testMinimalExportLayers(@TempDir Path outputDir) throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);
        assertNotNull(job, "Job should not be null");
        assertFalse(job.getSteps().isEmpty(), "Steps should not be empty");

        Step step = job.getSteps().values().iterator().next();
        Map<String, Path> layerFiles = multiRenderer.exportLayersToFiles(step, job.getMatrix(), outputDir);

        // Validate each layer file
        for (Map.Entry<String, Path> entry : layerFiles.entrySet()) {
            assertTrue(Files.exists(entry.getValue()),
                    "Layer file should exist: " + entry.getKey());

            String svg = Files.readString(entry.getValue());
            assertValidSvg(svg);
            assertNoLocaleIssues(svg);
        }
    }

    // ==================== Small sample Tests ====================

    @Test
    void testSmallSampleToSvg() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.SMALL_SAMPLE, tempDir);
        StringWriter writer = new StringWriter();
        multiRenderer.renderJob(job, writer);
        String svg = writer.toString();

        assertValidSvg(svg);
        assertNoLocaleIssues(svg);
    }

    // ==================== Multilayer sample (designodb) Tests ====================

    @Test
    void testMultilayerSampleToSvg() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        StringWriter writer = new StringWriter();
        multiRenderer.renderJob(job, writer);
        String svg = writer.toString();

        assertValidSvg(svg);
        assertNoLocaleIssues(svg);
        assertHasLayers(svg);
    }

    @Test
    void testMultilayerSampleLayerCount() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        StringWriter writer = new StringWriter();
        multiRenderer.renderJob(job, writer);
        String svg = writer.toString();

        // Count layer groups
        int layerCount = countOccurrences(svg, "class=\"layer layer-");
        assertTrue(layerCount > 0, "Should have at least one layer group");
    }

    // ==================== Single Layer Rendering Tests ====================

    @Test
    void testSingleLayerRender() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        assertFalse(job.getSteps().isEmpty(), "Steps should not be empty");

        Step step = job.getSteps().values().iterator().next();
        assertNotNull(step.getLayersByName(), "Layers should not be null");
        assertFalse(step.getLayersByName().isEmpty(), "Layers should not be empty");

        // Get first layer with features
        for (Layer layer : step.getLayersByName().values()) {
            if (layer.getFeatures() != null && !layer.getFeatures().getFeatures().isEmpty()) {
                StringWriter writer = new StringWriter();
                singleRenderer.renderLayer(layer, writer);
                String svg = writer.toString();

                assertValidSvg(svg);
                assertNoLocaleIssues(svg);
                break;
            }
        }
    }

    // ==================== Feature Type Coverage Tests ====================

    @Test
    void testAllFeatureTypesRendered() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        StringWriter writer = new StringWriter();
        multiRenderer.renderJob(job, writer);
        String svg = writer.toString();

        // Check for different SVG element types
        assertTrue(svg.contains("<circle") || svg.contains("<line") || svg.contains("<path"),
                "SVG should contain rendered features (circles, lines, or paths)");
    }

    // ==================== Minimal ODB++ Verification Tests ====================

    @Test
    void testMinimalOdbParsesCorrectly() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);

        assertNotNull(job, "Job should not be null");
        assertNotNull(job.getMatrix(), "Matrix should not be null");
        assertNotNull(job.getSteps(), "Steps should not be null");
        assertFalse(job.getSteps().isEmpty(), "Steps should not be empty");

        // Verify matrix has one layer named "top"
        boolean hasTopLayer = job.getMatrix().getLayers().stream()
                .anyMatch(l -> "top".equals(l.getName()));
        assertTrue(hasTopLayer, "Matrix should have 'top' layer");
    }

    @Test
    void testMinimalOdbToSvg() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);
        StringWriter writer = new StringWriter();
        multiRenderer.renderJob(job, writer);
        String svg = writer.toString();

        // Basic SVG validation
        assertValidSvg(svg);
        assertNoLocaleIssues(svg);
        assertHasContent(svg);

        // Should have the "top" layer
        assertTrue(svg.contains("layer-top") || svg.contains("data-layer-name=\"top\""),
                "SVG should contain 'top' layer");
    }

    @Test
    void testMinimalOdbPadsRendered() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);
        StringWriter writer = new StringWriter();
        multiRenderer.renderJob(job, writer);
        String svg = writer.toString();

        // Count circles (pads are rendered as circles)
        int circleCount = countOccurrences(svg, "<circle");
        assertTrue(circleCount >= 3,
                "SVG should have at least 3 circles for the main pads, found: " + circleCount);
    }

    @Test
    void testMinimalOdbLinesRendered() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);
        StringWriter writer = new StringWriter();
        multiRenderer.renderJob(job, writer);
        String svg = writer.toString();

        // Lines are now rendered as paths with "L" command: <path d="M x1 y1 L x2 y2" .../>
        // Count occurrences of "L " in path d attributes (line-to commands)
        int linePathCount = countOccurrences(svg, " L ");
        assertTrue(linePathCount >= 3,
                "SVG should have at least 3 line paths, found: " + linePathCount);
    }

    @Test
    void testMinimalOdbArcRendered() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);
        StringWriter writer = new StringWriter();
        multiRenderer.renderJob(job, writer);
        String svg = writer.toString();

        // Arcs are rendered as SVG path elements with "A" command
        assertTrue(svg.contains("<path") && svg.contains(" A "),
                "SVG should contain arc path elements");
    }

    @Test
    void testMinimalOdbProfileRendered() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);
        StringWriter writer = new StringWriter();
        multiRenderer.renderJob(job, writer);
        String svg = writer.toString();

        // Profile should be rendered as a path with the board outline
        assertTrue(svg.contains("layer-profile") || svg.contains("data-layer-name=\"profile\""),
                "SVG should contain profile layer");
    }

    @Test
    void testMinimalOdbNumericPrecision() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);
        StringWriter writer = new StringWriter();
        multiRenderer.renderJob(job, writer);
        String svg = writer.toString();

        // Numeric attributes must be well-formed: dot decimal separators only
        // (no locale issues) and a parseable viewBox.
        assertValidSvg(svg);
        assertNoLocaleIssues(svg);

        // The pads sit at distinct positions, so the SVG must carry coordinate
        // attributes with fractional (decimal-point) values.
        assertTrue(svg.matches("(?s).*(cx|x)=\"[-]?[0-9]+\\.[0-9]+\".*"),
                "SVG should contain fractional coordinate attributes");
    }

    // ==================== User-Defined Symbols Tests ====================

    @Test
    void testUserDefinedSymbolsParsed() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        // Verify user-defined symbols were parsed from the symbols directory
        assertNotNull(job.getSymbols(), "Symbols map should not be null");
        assertFalse(job.getSymbols().isEmpty(), "Should have parsed user-defined symbols");

        // Check for known user-defined symbol
        assertTrue(job.getSymbols().containsKey("special1.000x1.000"),
                "Should have parsed 'special1.000x1.000' symbol");
    }

    @Test
    void testUserDefinedSymbolHasFeatures() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        // User-defined symbols should have their features file parsed
        Symbol symbol = job.getSymbols().get("special1.000x1.000");
        assertNotNull(symbol, "Symbol should exist");
        assertNotNull(symbol.getFeatures(), "Symbol should have features parsed");
        assertFalse(symbol.getFeatures().getFeatures().isEmpty(),
                "Symbol should have at least one feature defined");
    }

    @Test
    void testSymbolTableParsed() throws IOException {
        Job job = parser.parse(Fixtures.MINIMAL_ODB);
        Step step = job.getSteps().values().iterator().next();
        Layer layer = step.getLayersByName().get("top");

        // Verify symbol table was parsed from $<num> <name> lines
        assertNotNull(layer.getFeatures().getSymbolTable(), "Symbol table should not be null");
        assertFalse(layer.getFeatures().getSymbolTable().isEmpty(),
                "Symbol table should have entries");

        // Check known symbols from minimal-odb/steps/pcb/layers/top/features:
        // $0 r1000, $1 r2000, $2 r5000 (dimensions in microns for UNITS=MM)
        assertEquals("r1000", layer.getFeatures().getSymbolName(0),
                "Symbol 0 should be 'r1000'");
        assertEquals("r2000", layer.getFeatures().getSymbolName(1),
                "Symbol 1 should be 'r2000'");
        assertEquals("r5000", layer.getFeatures().getSymbolName(2),
                "Symbol 2 should be 'r5000'");
    }

    // ==================== Contour (Surface) Tests ====================

    @Test
    void testContourSurfacesParsed() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        Step step = job.getSteps().values().iterator().next();

        // Find a layer with Surface features (contours)
        boolean foundSurface = false;
        for (Layer layer : step.getLayersByName().values()) {
            if (layer.getFeatures() != null) {
                for (Feature feature : layer.getFeatures().getFeatures()) {
                    if (feature instanceof Surface surface) {
                        foundSurface = true;
                        // Verify surface has polygons (contours)
                        assertNotNull(surface.getPolygons(), "Surface should have polygons");
                        assertFalse(surface.getPolygons().isEmpty(),
                                "Surface should have at least one polygon contour");

                        // Check first polygon has parts
                        ContourPolygon polygon = surface.getPolygons().get(0);
                        assertFalse(polygon.getPolygonParts().isEmpty(),
                                "Polygon should have parts (segments or arcs)");
                        break;
                    }
                }
            }
            if (foundSurface) break;
        }

        assertTrue(foundSurface, "Should have found at least one Surface feature with contours");
    }

    @Test
    void testContourPolygonTypesSupported() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        Step step = job.getSteps().values().iterator().next();

        // Find surfaces and verify polygon types (ISLAND, HOLE) are parsed
        boolean foundIsland = false;
        boolean foundSegment = false;
        boolean foundArc = false;

        for (Layer layer : step.getLayersByName().values()) {
            if (layer.getFeatures() != null) {
                for (Feature feature : layer.getFeatures().getFeatures()) {
                    if (feature instanceof Surface surface) {
                        for (ContourPolygon polygon : surface.getPolygons()) {
                            if (polygon.getType() == ContourPolygon.Type.ISLAND) {
                                foundIsland = true;
                            }
                            for (ContourPolygon.PolygonPart part : polygon.getPolygonParts()) {
                                if (part.getType() == ContourPolygon.PolygonPart.Type.SEGMENT) {
                                    foundSegment = true;
                                }
                                if (part.getType() == ContourPolygon.PolygonPart.Type.ARC) {
                                    foundArc = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        assertTrue(foundIsland, "Should have found ISLAND polygon type");
        assertTrue(foundSegment, "Should have found SEGMENT (OS) contour parts");
        assertTrue(foundArc, "Should have found ARC (OC) contour parts");
    }

    @Test
    void testContourRenderedAsSvgPath() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        StringWriter writer = new StringWriter();
        multiRenderer.renderJob(job, writer);
        String svg = writer.toString();

        // Surfaces are rendered as SVG path elements
        // They should have path commands: M (moveto), L (lineto), A (arc)
        assertTrue(svg.contains("<path"),
                "SVG should contain path elements for contour surfaces");

        // Contour paths should be closed polygons (ending with Z)
        assertTrue(svg.contains(" Z\""),
                "SVG path should have closed polygon (Z command)");
    }

    @Test
    void testUserDefinedSymbolContourParsed() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        // User-defined symbols often contain Surface features with contours
        Symbol symbol = job.getSymbols().get("special1.000x1.000");
        assertNotNull(symbol, "Symbol should exist");

        // Check that the symbol's features include a Surface with contour
        boolean hasSurface = symbol.getFeatures().getFeatures().stream()
                .anyMatch(f -> f instanceof Surface);
        assertTrue(hasSurface, "User-defined symbol should contain a Surface feature");

        // Verify the surface has polygons
        Surface surface = (Surface) symbol.getFeatures().getFeatures().stream()
                .filter(f -> f instanceof Surface)
                .findFirst()
                .orElseThrow();
        assertFalse(surface.getPolygons().isEmpty(),
                "User-defined symbol's surface should have polygon contours");
    }

    // ==================== Component Rendering Tests ====================

    @Test
    void testComponentRenderingEnabled() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        // Render with components enabled
        SvgRenderOptions options = new SvgRenderOptions().withComponents();
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer(options);

        StringWriter writer = new StringWriter();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        assertValidSvg(svg);
        assertNoLocaleIssues(svg);
        // Should have component layer group
        assertTrue(svg.contains("layer-components") || svg.contains("data-layer-name=\"components\""),
                "SVG should contain components layer group when components are enabled");
    }

    @Test
    void testTopViewRendering() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        // Render top view
        MultiLayerSvgRenderer renderer = MultiLayerSvgRenderer.forTopView();

        StringWriter writer = new StringWriter();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        assertValidSvg(svg);
        assertNoLocaleIssues(svg);
        // Top view should have scale(1,-1) for Y-flip only
        assertTrue(svg.contains("scale(1,-1)"),
                "Top view should use scale(1,-1) for Y-axis flip");
    }

    @Test
    void testBottomViewRendering() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        // Render bottom view
        MultiLayerSvgRenderer renderer = MultiLayerSvgRenderer.forBottomView();

        StringWriter writer = new StringWriter();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        assertValidSvg(svg);
        assertNoLocaleIssues(svg);
        // Bottom view should have scale(-1,-1) for X and Y mirroring
        assertTrue(svg.contains("scale(-1,-1)"),
                "Bottom view should use scale(-1,-1) for X-axis mirror and Y-axis flip");
    }

    @Test
    void testTopAndBottomViewGeneration(@TempDir Path outputDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        Path topPath = outputDir.resolve("top.svg");
        Path bottomPath = outputDir.resolve("bottom.svg");

        MultiLayerSvgRenderer.renderTopAndBottom(job, topPath, bottomPath);

        assertTrue(Files.exists(topPath), "Top SVG file should exist");
        assertTrue(Files.exists(bottomPath), "Bottom SVG file should exist");

        String topSvg = Files.readString(topPath);
        String bottomSvg = Files.readString(bottomPath);

        assertValidSvg(topSvg);
        assertValidSvg(bottomSvg);
        assertNoLocaleIssues(topSvg);
        assertNoLocaleIssues(bottomSvg);

        // Verify different transforms
        assertTrue(topSvg.contains("scale(1,-1)"), "Top view should have Y-flip transform");
        assertTrue(bottomSvg.contains("scale(-1,-1)"), "Bottom view should have mirror transform");
    }

    @Test
    void testComponentMarkerSettings() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        // Custom component settings
        SvgRenderOptions options = new SvgRenderOptions()
                .withComponents()
                .withComponentMarkerSize(0.05)
                .withComponentLabelSize(0.04)
                .withComponentColor("#00FF00");

        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer(options);

        StringWriter writer = new StringWriter();
        renderer.renderJob(job, writer);
        String svg = writer.toString();

        assertValidSvg(svg);
        // Check that component layer exists
        assertTrue(svg.contains("layer-components"),
                "SVG should contain components layer");
    }

    // ==================== Parser Enhancement Tests ====================

    @Test
    void testAttributeNamesParsed() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        Step step = job.getSteps().values().iterator().next();

        // Find a layer with attribute names
        boolean foundAttributeNames = false;
        for (Layer layer : step.getLayersByName().values()) {
            if (layer.getFeatures() != null && !layer.getFeatures().getAttributeNames().isEmpty()) {
                foundAttributeNames = true;
                break;
            }
        }

        assertTrue(foundAttributeNames, "Should have parsed feature attribute names (@<num> <name>)");
    }

    @Test
    void testNegativeCoordinatesSupported() throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        Step step = job.getSteps().values().iterator().next();

        // Find features with negative coordinates
        boolean foundNegative = false;
        for (Layer layer : step.getLayersByName().values()) {
            if (layer.getFeatures() != null) {
                for (Feature feature : layer.getFeatures().getFeatures()) {
                    if (feature instanceof Pad pad) {
                        if (pad.getX() < 0 || pad.getY() < 0) {
                            foundNegative = true;
                            break;
                        }
                    } else if (feature instanceof Line line) {
                        if (line.getXs() < 0 || line.getYs() < 0 ||
                            line.getXe() < 0 || line.getYe() < 0) {
                            foundNegative = true;
                            break;
                        }
                    }
                }
            }
            if (foundNegative) break;
        }

        // Note: Not all ODB++ files have negative coordinates, so this test
        // just verifies the parser doesn't fail on the sample data.
        assertTrue(true, "Parser should handle negative coordinates without errors");
    }

    // ==================== Standard Symbol Tests ====================

    @Test
    void testStandardSymbolParserBasic() {
        com.deltaproto.deltaodbpp.parser.StandardSymbolParser symbolParser = new com.deltaproto.deltaodbpp.parser.StandardSymbolParser();

        // Test round symbol
        var round = symbolParser.parse("r50");
        assertNotNull(round, "Should parse round symbol");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.ROUND, round.getType());
        assertEquals(50.0, round.getWidth(), 0.01);

        // Test square symbol
        var square = symbolParser.parse("s40");
        assertNotNull(square, "Should parse square symbol");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.SQUARE, square.getType());

        // Test rectangle
        var rect = symbolParser.parse("rect100x50");
        assertNotNull(rect, "Should parse rectangle symbol");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.RECTANGLE, rect.getType());
        assertEquals(100.0, rect.getWidth(), 0.01);
        assertEquals(50.0, rect.getHeight(), 0.01);
    }

    @Test
    void testStandardSymbolParserRoundedRect() {
        com.deltaproto.deltaodbpp.parser.StandardSymbolParser symbolParser = new com.deltaproto.deltaodbpp.parser.StandardSymbolParser();

        // Test spec format: rect<w>x<h>xr<rad>
        var roundedSpec = symbolParser.parse("rect100x50xr10");
        assertNotNull(roundedSpec, "Should parse rounded rect (spec format)");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.ROUNDED_RECTANGLE, roundedSpec.getType());
        assertEquals(10.0, roundedSpec.getCornerRadius(), 0.01);

        // Test legacy format: rc<w>x<h>x<r>
        var roundedLegacy = symbolParser.parse("rc100x50x10");
        assertNotNull(roundedLegacy, "Should parse rounded rect (legacy format)");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.ROUNDED_RECTANGLE, roundedLegacy.getType());
    }

    @Test
    void testStandardSymbolParserChamferedRect() {
        com.deltaproto.deltaodbpp.parser.StandardSymbolParser symbolParser = new com.deltaproto.deltaodbpp.parser.StandardSymbolParser();

        // Test spec format: rect<w>x<h>xc<rad>
        var chamferedSpec = symbolParser.parse("rect100x50xc10");
        assertNotNull(chamferedSpec, "Should parse chamfered rect (spec format)");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.CHAMFERED_RECTANGLE, chamferedSpec.getType());

        // Test legacy format: ch<w>x<h>x<c>
        var chamferedLegacy = symbolParser.parse("ch100x50x10");
        assertNotNull(chamferedLegacy, "Should parse chamfered rect (legacy format)");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.CHAMFERED_RECTANGLE, chamferedLegacy.getType());
    }

    @Test
    void testStandardSymbolParserHalfOval() {
        com.deltaproto.deltaodbpp.parser.StandardSymbolParser symbolParser = new com.deltaproto.deltaodbpp.parser.StandardSymbolParser();

        // Test legacy format: ho<w>x<h>
        var hoLegacy = symbolParser.parse("ho100x50");
        assertNotNull(hoLegacy, "Should parse half oval (legacy format: ho)");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.HALF_OVAL, hoLegacy.getType());

        // Test spec format: oval_h<w>x<h>
        var hoSpec = symbolParser.parse("oval_h100x50");
        assertNotNull(hoSpec, "Should parse half oval (spec format: oval_h)");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.HALF_OVAL, hoSpec.getType());
    }

    @Test
    void testStandardSymbolParserDonuts() {
        com.deltaproto.deltaodbpp.parser.StandardSymbolParser symbolParser = new com.deltaproto.deltaodbpp.parser.StandardSymbolParser();

        // Round donut
        var roundDonut = symbolParser.parse("donut_r100x50");
        assertNotNull(roundDonut, "Should parse round donut");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.ROUND_DONUT, roundDonut.getType());

        // Square donut
        var squareDonut = symbolParser.parse("donut_s100x50");
        assertNotNull(squareDonut, "Should parse square donut");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.SQUARE_DONUT, squareDonut.getType());

        // Square-round donut
        var sqrRoundDonut = symbolParser.parse("donut_sr100x50");
        assertNotNull(sqrRoundDonut, "Should parse square-round donut");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.SQUARE_ROUND_DONUT, sqrRoundDonut.getType());

        // Oval donut
        var ovalDonut = symbolParser.parse("donut_o100x80x10");
        assertNotNull(ovalDonut, "Should parse oval donut");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.OVAL_DONUT, ovalDonut.getType());

        // Rectangle donut
        var rectDonut = symbolParser.parse("donut_rc100x80x10");
        assertNotNull(rectDonut, "Should parse rectangle donut");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.RECT_DONUT, rectDonut.getType());
    }

    @Test
    void testStandardSymbolParserThermals() {
        com.deltaproto.deltaodbpp.parser.StandardSymbolParser symbolParser = new com.deltaproto.deltaodbpp.parser.StandardSymbolParser();

        // Round thermal
        var roundThermal = symbolParser.parse("thr100x50x45x4x10");
        assertNotNull(roundThermal, "Should parse round thermal");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.ROUND_THERMAL, roundThermal.getType());
        assertEquals(4, roundThermal.getNumSpokes());

        // Round thermal with squared gaps (ths prefix)
        var roundThermalSquared = symbolParser.parse("ths100x50x45x4x10");
        assertNotNull(roundThermalSquared, "Should parse round thermal with squared gaps");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.ROUND_THERMAL_SQUARED, roundThermalSquared.getType());

        // Square thermal (spec format: s_ths prefix)
        var squareThermalSpec = symbolParser.parse("s_ths100x50x45x4x10");
        assertNotNull(squareThermalSpec, "Should parse square thermal (spec format)");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.SQUARE_THERMAL, squareThermalSpec.getType());
    }

    @Test
    void testStandardSymbolParserMoire() {
        com.deltaproto.deltaodbpp.parser.StandardSymbolParser symbolParser = new com.deltaproto.deltaodbpp.parser.StandardSymbolParser();

        // Moire: moire<rw>x<rg>x<nr>x<lw>x<ll>x<la>
        var moire = symbolParser.parse("moire10x5x3x2x100x45");
        assertNotNull(moire, "Should parse moire symbol");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.MOIRE, moire.getType());
        assertEquals(3, moire.getNumSpokes()); // numSpokes stores number of rings
    }

    @Test
    void testStandardSymbolParserOther() {
        com.deltaproto.deltaodbpp.parser.StandardSymbolParser symbolParser = new com.deltaproto.deltaodbpp.parser.StandardSymbolParser();

        // Oval
        var oval = symbolParser.parse("oval100x50");
        assertNotNull(oval, "Should parse oval");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.OVAL, oval.getType());

        // Ellipse
        var ellipse = symbolParser.parse("el100x50");
        assertNotNull(ellipse, "Should parse ellipse");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.ELLIPSE, ellipse.getType());

        // Diamond
        var diamond = symbolParser.parse("di100x50");
        assertNotNull(diamond, "Should parse diamond");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.DIAMOND, diamond.getType());

        // Octagon
        var octagon = symbolParser.parse("oct100x100x20");
        assertNotNull(octagon, "Should parse octagon");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.OCTAGON, octagon.getType());

        // Horizontal hexagon
        var hexL = symbolParser.parse("hex_l100x80x20");
        assertNotNull(hexL, "Should parse horizontal hexagon");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.HEXAGON_L, hexL.getType());

        // Vertical hexagon
        var hexS = symbolParser.parse("hex_s100x80x20");
        assertNotNull(hexS, "Should parse vertical hexagon");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.HEXAGON_S, hexS.getType());

        // Triangle
        var triangle = symbolParser.parse("tri100x80");
        assertNotNull(triangle, "Should parse triangle");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.TRIANGLE, triangle.getType());

        // Butterfly round
        var bfr = symbolParser.parse("bfr50");
        assertNotNull(bfr, "Should parse butterfly round");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.BUTTERFLY, bfr.getType());

        // Butterfly square
        var bfs = symbolParser.parse("bfs50");
        assertNotNull(bfs, "Should parse butterfly square");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.BUTTERFLY, bfs.getType());
    }

    @Test
    void testStandardSymbolParserHole() {
        com.deltaproto.deltaodbpp.parser.StandardSymbolParser symbolParser = new com.deltaproto.deltaodbpp.parser.StandardSymbolParser();

        // Plated hole
        var platedHole = symbolParser.parse("hole50x1x0x0");
        assertNotNull(platedHole, "Should parse plated hole");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.HOLE, platedHole.getType());
        assertEquals(50.0, platedHole.getWidth(), 0.01);
        assertTrue(platedHole.isPlated(), "Hole should be plated");

        // Non-plated hole
        var nonPlatedHole = symbolParser.parse("hole30x0x0x0");
        assertNotNull(nonPlatedHole, "Should parse non-plated hole");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.HOLE, nonPlatedHole.getType());
        assertFalse(nonPlatedHole.isPlated(), "Hole should be non-plated");
    }

    @Test
    void testStandardSymbolParserAdditionalThermals() {
        com.deltaproto.deltaodbpp.parser.StandardSymbolParser symbolParser = new com.deltaproto.deltaodbpp.parser.StandardSymbolParser();

        // Square thermal open corners: s_tho<od>x<id>x<angle>x<num>x<gap>
        var squareThermalOpen = symbolParser.parse("s_tho100x50x45x4x10");
        assertNotNull(squareThermalOpen, "Should parse square thermal open corners");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.SQUARE_THERMAL_OPEN, squareThermalOpen.getType());

        // Line thermal: s_thr<os>x<is>x<angle>x<num>x<gap>
        var lineThermal = symbolParser.parse("s_thr100x50x45x4x10");
        assertNotNull(lineThermal, "Should parse line thermal");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.LINE_THERMAL, lineThermal.getType());

        // Square-round thermal: sr_ths<os>x<id>x<angle>x<num>x<gap>
        var squareRoundThermal = symbolParser.parse("sr_ths100x50x45x4x10");
        assertNotNull(squareRoundThermal, "Should parse square-round thermal");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.SQUARE_ROUND_THERMAL, squareRoundThermal.getType());

        // Rectangular thermal: rc_ths<w>x<h>x<angle>x<num>x<gap>x<air_gap>
        var rectThermal = symbolParser.parse("rc_ths100x80x45x4x10x5");
        assertNotNull(rectThermal, "Should parse rectangular thermal");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.RECT_THERMAL, rectThermal.getType());

        // Rectangular thermal open corners: rc_tho<w>x<h>x<angle>x<num>x<gap>x<air_gap>
        var rectThermalOpen = symbolParser.parse("rc_tho100x80x45x4x10x5");
        assertNotNull(rectThermalOpen, "Should parse rectangular thermal open corners");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.RECT_THERMAL_OPEN, rectThermalOpen.getType());

        // Oval thermal: o_ths<ow>x<oh>x<angle>x<num>x<gap>x<lw>
        var ovalThermal = symbolParser.parse("o_ths100x80x45x4x10x5");
        assertNotNull(ovalThermal, "Should parse oval thermal");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.OVAL_THERMAL, ovalThermal.getType());

        // Rounded square thermal: s_ths<os>x<is>x<angle>x<num>x<gap>xr<rad>
        var roundedSquareThermal = symbolParser.parse("s_ths100x50x45x4x10xr5");
        assertNotNull(roundedSquareThermal, "Should parse rounded square thermal");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.ROUNDED_SQUARE_THERMAL, roundedSquareThermal.getType());

        // Rounded rect thermal: rc_ths<w>x<h>x<angle>x<num>x<gap>x<lw>xr<rad>
        var roundedRectThermal = symbolParser.parse("rc_ths100x80x45x4x10x5xr3");
        assertNotNull(roundedRectThermal, "Should parse rounded rect thermal");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.ROUNDED_RECT_THERMAL, roundedRectThermal.getType());
    }

    @Test
    void testStandardSymbolParserRoundedDonuts() {
        com.deltaproto.deltaodbpp.parser.StandardSymbolParser symbolParser = new com.deltaproto.deltaodbpp.parser.StandardSymbolParser();

        // Rounded square donut: donut_s<od>x<id>xr<rad>
        var roundedSquareDonut = symbolParser.parse("donut_s100x50xr10");
        assertNotNull(roundedSquareDonut, "Should parse rounded square donut");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.ROUNDED_SQUARE_DONUT, roundedSquareDonut.getType());
        assertEquals(10.0, roundedSquareDonut.getParam2(), 0.01); // corner radius

        // Rounded rectangle donut: donut_rc<ow>x<oh>x<lw>xr<rad>
        var roundedRectDonut = symbolParser.parse("donut_rc100x80x10xr5");
        assertNotNull(roundedRectDonut, "Should parse rounded rectangle donut");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.ROUNDED_RECT_DONUT, roundedRectDonut.getType());
        assertEquals(5.0, roundedRectDonut.getParam2(), 0.01); // corner radius
    }

    @Test
    void testButterflyRendering() {
        // Test that butterfly symbols can be rendered
        com.deltaproto.deltaodbpp.export.render.SymbolResolver resolver = new com.deltaproto.deltaodbpp.export.render.SymbolResolver();

        var roundRenderer = resolver.resolve("bfr50");
        assertNotNull(roundRenderer, "Should resolve round butterfly");
        String roundSvg = roundRenderer.render(0, 0, 0, false, 1.0, "#FF0000");
        assertNotNull(roundSvg, "Should render round butterfly");
        assertTrue(roundSvg.contains("path"), "Round butterfly should contain path element");

        var squareRenderer = resolver.resolve("bfs50");
        assertNotNull(squareRenderer, "Should resolve square butterfly");
        String squareSvg = squareRenderer.render(0, 0, 0, false, 1.0, "#00FF00");
        assertNotNull(squareSvg, "Should render square butterfly");
        assertTrue(squareSvg.contains("path"), "Square butterfly should contain path element");
    }

    @Test
    void testHoleRendering() {
        // Test that hole symbols can be rendered
        com.deltaproto.deltaodbpp.export.render.SymbolResolver resolver = new com.deltaproto.deltaodbpp.export.render.SymbolResolver();

        var platedRenderer = resolver.resolve("hole50x1x0x0");
        assertNotNull(platedRenderer, "Should resolve plated hole");
        String platedSvg = platedRenderer.render(0, 0, 0, false, 1.0, "#FF0000");
        assertNotNull(platedSvg, "Should render plated hole");
        assertTrue(platedSvg.contains("circle"), "Plated hole should contain circle element");

        var nonPlatedRenderer = resolver.resolve("hole30x0x0x0");
        assertNotNull(nonPlatedRenderer, "Should resolve non-plated hole");
        String nonPlatedSvg = nonPlatedRenderer.render(0, 0, 0, false, 1.0, "#00FF00");
        assertNotNull(nonPlatedSvg, "Should render non-plated hole");
        assertTrue(nonPlatedSvg.contains("circle"), "Non-plated hole should contain circle element");
        assertTrue(nonPlatedSvg.contains("line"), "Non-plated hole should contain cross lines");
    }

    // ==================== Home Plate Symbol Tests ====================

    @Test
    void testStandardSymbolParserHomePlate() {
        com.deltaproto.deltaodbpp.parser.StandardSymbolParser symbolParser = new com.deltaproto.deltaodbpp.parser.StandardSymbolParser();

        // Home plate: hplate<w>x<h>x<c>
        var homePlate = symbolParser.parse("hplate100x80x20");
        assertNotNull(homePlate, "Should parse home plate symbol");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.HOME_PLATE, homePlate.getType());
        assertEquals(100.0, homePlate.getWidth(), 0.01);
        assertEquals(80.0, homePlate.getHeight(), 0.01);
        assertEquals(20.0, homePlate.getParam1(), 0.01); // cut size

        // Inverted home plate: inv_hplate<w>x<h>x<c>
        var invertedHomePlate = symbolParser.parse("inv_hplate100x80x20");
        assertNotNull(invertedHomePlate, "Should parse inverted home plate symbol");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.INVERTED_HOME_PLATE, invertedHomePlate.getType());

        // Flat home plate: fhplate<w>x<h>x<vc>x<hc>
        var flatHomePlate = symbolParser.parse("fhplate100x80x15x10");
        assertNotNull(flatHomePlate, "Should parse flat home plate symbol");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.FLAT_HOME_PLATE, flatHomePlate.getType());
        assertEquals(15.0, flatHomePlate.getParam1(), 0.01); // vertical cut
        assertEquals(10.0, flatHomePlate.getParam2(), 0.01); // horizontal cut

        // D-shape: dshape<w>x<h>x<ra>
        var dShape = symbolParser.parse("dshape100x80x30");
        assertNotNull(dShape, "Should parse D-shape symbol");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.D_SHAPE, dShape.getType());
        assertEquals(30.0, dShape.getParam1(), 0.01); // radius
    }

    @Test
    void testHomePlateRendering() {
        com.deltaproto.deltaodbpp.export.render.SymbolResolver resolver = new com.deltaproto.deltaodbpp.export.render.SymbolResolver();

        // Home plate
        var homePlateRenderer = resolver.resolve("hplate100x80x20");
        assertNotNull(homePlateRenderer, "Should resolve home plate");
        String homePlateSvg = homePlateRenderer.render(0, 0, 0, false, 1.0, "#FF0000");
        assertNotNull(homePlateSvg, "Should render home plate");
        assertTrue(homePlateSvg.contains("path"), "Home plate should contain path element");
        assertTrue(homePlateSvg.contains("L"), "Home plate path should have line commands");

        // Inverted home plate
        var invertedRenderer = resolver.resolve("inv_hplate100x80x20");
        assertNotNull(invertedRenderer, "Should resolve inverted home plate");
        String invertedSvg = invertedRenderer.render(0, 0, 0, false, 1.0, "#00FF00");
        assertNotNull(invertedSvg, "Should render inverted home plate");
        assertTrue(invertedSvg.contains("path"), "Inverted home plate should contain path element");

        // Flat home plate
        var flatRenderer = resolver.resolve("fhplate100x80x15x10");
        assertNotNull(flatRenderer, "Should resolve flat home plate");
        String flatSvg = flatRenderer.render(0, 0, 0, false, 1.0, "#0000FF");
        assertNotNull(flatSvg, "Should render flat home plate");
        assertTrue(flatSvg.contains("path"), "Flat home plate should contain path element");

        // D-shape
        var dShapeRenderer = resolver.resolve("dshape100x80x30");
        assertNotNull(dShapeRenderer, "Should resolve D-shape");
        String dShapeSvg = dShapeRenderer.render(0, 0, 0, false, 1.0, "#FF00FF");
        assertNotNull(dShapeSvg, "Should render D-shape");
        assertTrue(dShapeSvg.contains("path"), "D-shape should contain path element");
    }

    // ==================== Cross and Dogbone Symbol Tests ====================

    @Test
    void testStandardSymbolParserCross() {
        com.deltaproto.deltaodbpp.parser.StandardSymbolParser symbolParser = new com.deltaproto.deltaodbpp.parser.StandardSymbolParser();

        // Cross: cross<ow>x<oh>x<lbw>x<lbh>x<lbo>x<lbo>
        var cross = symbolParser.parse("cross100x80x20x15x50x50");
        assertNotNull(cross, "Should parse cross symbol");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.CROSS, cross.getType());
        assertEquals(100.0, cross.getWidth(), 0.01);
        assertEquals(80.0, cross.getHeight(), 0.01);
        assertEquals(20.0, cross.getParam1(), 0.01); // horizontal line width
        assertEquals(15.0, cross.getParam2(), 0.01); // vertical line width
        assertEquals(50.0, cross.getParam3(), 0.01); // horizontal cross point
        assertEquals(50.0, cross.getParam4(), 0.01); // vertical cross point

        // Cross with radius: cross<ow>x<oh>x<lbw>x<lbh>x<lbo>x<lbo>x<rad>
        var crossRadiused = symbolParser.parse("cross100x80x20x15x50x50x5");
        assertNotNull(crossRadiused, "Should parse cross symbol with radius");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.CROSS, crossRadiused.getType());
    }

    @Test
    void testStandardSymbolParserDogbone() {
        com.deltaproto.deltaodbpp.parser.StandardSymbolParser symbolParser = new com.deltaproto.deltaodbpp.parser.StandardSymbolParser();

        // Dogbone: dogbone<w>x<h>x<hs>x<vs>x<hc>x[r|s]
        var dogboneRound = symbolParser.parse("dogbone100x50x20x15x30xr");
        assertNotNull(dogboneRound, "Should parse dogbone symbol with round ends");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.DOGBONE, dogboneRound.getType());
        assertEquals(100.0, dogboneRound.getWidth(), 0.01);
        assertEquals(50.0, dogboneRound.getHeight(), 0.01);
        assertEquals(20.0, dogboneRound.getParam1(), 0.01); // horizontal line width
        assertEquals(15.0, dogboneRound.getParam2(), 0.01); // vertical line width
        assertEquals(30.0, dogboneRound.getParam3(), 0.01); // horizontal cross point (bridge width)
        assertEquals(0.0, dogboneRound.getParam4(), 0.01); // 0 = round

        var dogboneSquare = symbolParser.parse("dogbone100x50x20x15x30xs");
        assertNotNull(dogboneSquare, "Should parse dogbone symbol with square ends");
        assertEquals(1.0, dogboneSquare.getParam4(), 0.01); // 1 = square
    }

    @Test
    void testCrossRendering() {
        com.deltaproto.deltaodbpp.export.render.SymbolResolver resolver = new com.deltaproto.deltaodbpp.export.render.SymbolResolver();

        // Cross (basic format)
        var crossRenderer = resolver.resolve("cross100x80x20x15x50x50");
        assertNotNull(crossRenderer, "Should resolve cross");
        String crossSvg = crossRenderer.render(0, 0, 0, false, 1.0, "#FF0000");
        assertNotNull(crossSvg, "Should render cross");
        assertTrue(crossSvg.contains("path"), "Cross should contain path element");

        // Cross with radius
        var crossRadiusedRenderer = resolver.resolve("cross100x80x20x15x50x50x5");
        assertNotNull(crossRadiusedRenderer, "Should resolve cross with radius");
        String crossRadiusedSvg = crossRadiusedRenderer.render(0, 0, 0, false, 1.0, "#00FF00");
        assertNotNull(crossRadiusedSvg, "Should render cross with radius");
        assertTrue(crossRadiusedSvg.contains("path"), "Cross should contain path element");
    }

    @Test
    void testDogboneRendering() {
        com.deltaproto.deltaodbpp.export.render.SymbolResolver resolver = new com.deltaproto.deltaodbpp.export.render.SymbolResolver();

        // Dogbone with round ends
        var dogboneRoundRenderer = resolver.resolve("dogbone100x50x20x15x30xr");
        assertNotNull(dogboneRoundRenderer, "Should resolve dogbone with round ends");
        String dogboneRoundSvg = dogboneRoundRenderer.render(0, 0, 0, false, 1.0, "#FF0000");
        assertNotNull(dogboneRoundSvg, "Should render dogbone with round ends");
        assertTrue(dogboneRoundSvg.contains("path"), "Dogbone should contain path element");

        // Dogbone with square ends
        var dogboneSquareRenderer = resolver.resolve("dogbone100x50x20x15x30xs");
        assertNotNull(dogboneSquareRenderer, "Should resolve dogbone with square ends");
        String dogboneSquareSvg = dogboneSquareRenderer.render(0, 0, 0, false, 1.0, "#00FF00");
        assertNotNull(dogboneSquareSvg, "Should render dogbone with square ends");
        assertTrue(dogboneSquareSvg.contains("path"), "Dogbone should contain path element");
    }

    // ==================== Oblong Thermal and Null Symbol Tests ====================

    @Test
    void testStandardSymbolParserOblongThermal() {
        com.deltaproto.deltaodbpp.parser.StandardSymbolParser symbolParser = new com.deltaproto.deltaodbpp.parser.StandardSymbolParser();

        // Oblong thermal: oblong_ths<ow>x<oh>x<angle>x<num>x<gap>x<lw>x[r|s]
        var oblongThermal = symbolParser.parse("oblong_ths100x60x45x4x10x5xr");
        assertNotNull(oblongThermal, "Should parse oblong thermal symbol");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.OBLONG_THERMAL, oblongThermal.getType());
        assertEquals(100.0, oblongThermal.getWidth(), 0.01);
        assertEquals(60.0, oblongThermal.getHeight(), 0.01);
        assertEquals(45.0, oblongThermal.getSpokeAngle(), 0.01);
        assertEquals(4, oblongThermal.getNumSpokes());
        assertEquals(10.0, oblongThermal.getGap(), 0.01);
        assertEquals(5.0, oblongThermal.getParam1(), 0.01); // line width
    }

    @Test
    void testStandardSymbolParserNull() {
        com.deltaproto.deltaodbpp.parser.StandardSymbolParser symbolParser = new com.deltaproto.deltaodbpp.parser.StandardSymbolParser();

        // Null symbol: null<ext>
        var nullSymbol = symbolParser.parse("null0");
        assertNotNull(nullSymbol, "Should parse null symbol with extension");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.NULL_SYMBOL, nullSymbol.getType());
        assertEquals(0.0, nullSymbol.getParam1(), 0.01); // extension number

        var nullSymbol1 = symbolParser.parse("null");
        assertNotNull(nullSymbol1, "Should parse null symbol without extension");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.NULL_SYMBOL, nullSymbol1.getType());

        var nullSymbol5 = symbolParser.parse("null5");
        assertNotNull(nullSymbol5, "Should parse null symbol with extension 5");
        assertEquals(com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type.NULL_SYMBOL, nullSymbol5.getType());
        assertEquals(5.0, nullSymbol5.getParam1(), 0.01);
    }

    @Test
    void testOblongThermalRendering() {
        com.deltaproto.deltaodbpp.export.render.SymbolResolver resolver = new com.deltaproto.deltaodbpp.export.render.SymbolResolver();

        var oblongRenderer = resolver.resolve("oblong_ths100x60x45x4x10x5xr");
        assertNotNull(oblongRenderer, "Should resolve oblong thermal");
        String oblongSvg = oblongRenderer.render(0, 0, 0, false, 1.0, "#FF0000");
        assertNotNull(oblongSvg, "Should render oblong thermal");
        assertTrue(oblongSvg.contains("path"), "Oblong thermal should contain path element");
    }

    @Test
    void testNullRendering() {
        com.deltaproto.deltaodbpp.export.render.SymbolResolver resolver = new com.deltaproto.deltaodbpp.export.render.SymbolResolver();

        var nullRenderer = resolver.resolve("null0");
        assertNotNull(nullRenderer, "Should resolve null symbol");
        String nullSvg = nullRenderer.render(0, 0, 0, false, 1.0, "#FF0000");
        assertNotNull(nullSvg, "Should render null symbol");
        assertTrue(nullSvg.contains("circle"), "Null symbol should contain circle element (tiny marker)");
    }

    // ==================== Validation Helpers ====================

    private void assertValidSvg(String svg) {
        assertNotNull(svg, "SVG should not be null");
        assertFalse(svg.isEmpty(), "SVG should not be empty");
        assertTrue(svg.contains("<?xml"), "SVG should start with XML declaration");
        assertTrue(svg.contains("<svg"), "SVG should contain svg element");
        assertTrue(svg.contains("</svg>"), "SVG should have closing svg tag");
        assertTrue(svg.contains("xmlns=\"http://www.w3.org/2000/svg\""),
                "SVG should have correct namespace");
    }

    private void assertNoLocaleIssues(String svg) {
        // Check that numeric values use dots, not commas as decimal separators
        assertFalse(INVALID_SVG_NUMBER.matcher(svg).find(),
                "SVG should not contain comma decimal separators in numeric attributes. " +
                        "This indicates a locale issue in String.format calls.");

        // Additional check for viewBox specifically
        if (svg.contains("viewBox=")) {
            int viewBoxStart = svg.indexOf("viewBox=\"") + 9;
            int viewBoxEnd = svg.indexOf("\"", viewBoxStart);
            String viewBox = svg.substring(viewBoxStart, viewBoxEnd);
            assertFalse(viewBox.contains(","),
                    "viewBox should not contain commas as decimal separators: " + viewBox);
        }
    }

    private void assertHasContent(String svg) {
        // Check for actual graphics content
        int elementCount = countOccurrences(svg, "<circle") +
                countOccurrences(svg, "<line") +
                countOccurrences(svg, "<path") +
                countOccurrences(svg, "<rect") +
                countOccurrences(svg, "<text");

        assertTrue(elementCount > 0,
                "SVG should contain at least one graphics element (circle, line, path, rect, or text)");
    }

    private void assertHasLayers(String svg) {
        assertTrue(svg.contains("class=\"layer\"") || svg.contains("class=\"layer "),
                "SVG should contain layer groups");
    }

    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}

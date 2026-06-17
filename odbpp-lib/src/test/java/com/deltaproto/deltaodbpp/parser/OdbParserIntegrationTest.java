package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.*;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for parsing complete ODB++ archives.
 *
 * <p>All tests run against committed, openly-available sample archives loaded
 * through {@link Fixtures} — no customer data is referenced. Small structural
 * checks use the compact {@code SMALL_SAMPLE}; richer checks (components, EDA,
 * feature variety) use the {@code MULTILAYER_SAMPLE} (the "designodb" reference
 * design, whose single step is named {@code cellular_flip-phone}).
 */
class OdbParserIntegrationTest {

    // ==================== Small sample Tests ====================

    @Test
    void testParseSmallSample(@TempDir Path tempDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.SMALL_SAMPLE, tempDir);

        assertNotNull(job, "Job should not be null");
        // The small sample is a partial ODB++ that may be missing misc/info.
        assertNotNull(job.getMatrix(), "Matrix should not be null");
        assertNotNull(job.getSteps(), "Steps should not be null");
        assertFalse(job.getSteps().isEmpty(), "Steps should not be empty");
    }

    @Test
    void testSmallSampleMiscInfo(@TempDir Path tempDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.SMALL_SAMPLE, tempDir);

        // The small sample may be missing the misc/info file.
        MiscInfo miscInfo = job.getMiscInfo();
        if (miscInfo != null) {
            // If present, verify basic MiscInfo fields are parsed.
            assertNotNull(miscInfo.getProductModelName(), "Product model name should be parsed");
        }
    }

    @Test
    void testSmallSampleMatrix(@TempDir Path tempDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.SMALL_SAMPLE, tempDir);

        Matrix matrix = job.getMatrix();
        assertNotNull(matrix, "Matrix should not be null");
        assertNotNull(matrix.getLayers(), "Matrix layers should not be null");
        assertFalse(matrix.getLayers().isEmpty(), "Matrix should have layers");

        // Verify each layer has required fields.
        for (MatrixLayer layer : matrix.getLayers()) {
            assertNotNull(layer.getName(), "Layer name should not be null");
            assertNotNull(layer.getType(), "Layer type should not be null");
        }
    }

    @Test
    void testSmallSampleSteps(@TempDir Path tempDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.SMALL_SAMPLE, tempDir);

        assertNotNull(job.getSteps(), "Steps should not be null");
        assertFalse(job.getSteps().isEmpty(), "Steps should not be empty");

        for (Step step : job.getSteps().values()) {
            assertNotNull(step.getName(), "Step name should not be null");
            // StepHdr is REQUIRED.
            assertNotNull(step.getStepHdr(), "StepHdr should not be null for step: " + step.getName());
        }
    }

    @Test
    void testSmallSampleLayers(@TempDir Path tempDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.SMALL_SAMPLE, tempDir);

        for (Step step : job.getSteps().values()) {
            if (step.getLayersByName() != null) {
                for (Layer layer : step.getLayersByName().values()) {
                    assertNotNull(layer.getName(), "Layer name should not be null");
                    // Features are REQUIRED for each layer.
                    if (layer.getFeatures() != null) {
                        assertNotNull(layer.getFeatures().getFeatures(),
                                "Features list should not be null for layer: " + layer.getName());
                    }
                }
            }
        }
    }

    // ==================== Multilayer sample (designodb) Tests ====================

    @Test
    void testParseMultilayerSample(@TempDir Path tempDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        assertNotNull(job, "Job should not be null");
        assertNotNull(job.getMiscInfo(), "MiscInfo should not be null");
        assertNotNull(job.getMatrix(), "Matrix should not be null");
        assertNotNull(job.getSteps(), "Steps should not be null");
        assertFalse(job.getSteps().isEmpty(), "Steps should not be empty");
    }

    @Test
    void testMultilayerSampleSymbols(@TempDir Path tempDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        if (job.getSymbols() != null && !job.getSymbols().isEmpty()) {
            for (Symbol symbol : job.getSymbols().values()) {
                assertNotNull(symbol.getName(), "Symbol name should not be null");
                // Symbols should have features.
                if (symbol.getFeatures() != null) {
                    assertNotNull(symbol.getFeatures().getFeatures(),
                            "Symbol features list should not be null for symbol: " + symbol.getName());
                }
            }
        }
    }

    @Test
    void testMultilayerSampleLayerFeatures(@TempDir Path tempDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        int totalFeatures = 0;
        for (Step step : job.getSteps().values()) {
            if (step.getLayersByName() != null) {
                for (Layer layer : step.getLayersByName().values()) {
                    if (layer.getFeatures() != null && layer.getFeatures().getFeatures() != null) {
                        totalFeatures += layer.getFeatures().getFeatures().size();
                    }
                }
            }
        }

        assertTrue(totalFeatures > 0, "Should have parsed some features");
    }

    @Test
    void testMultilayerSampleAllLayerTypes(@TempDir Path tempDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        // Verify matrix has various layer types.
        Matrix matrix = job.getMatrix();
        assertNotNull(matrix, "Matrix should not be null");

        boolean hasSignalLayer = matrix.getLayers().stream()
                .anyMatch(layer -> "SIGNAL".equalsIgnoreCase(layer.getType()));

        assertTrue(hasSignalLayer, "Should have at least one signal layer");
    }

    @Test
    void testMultilayerSampleFeatureTypes(@TempDir Path tempDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        boolean hasPads = false;
        boolean hasLines = false;
        boolean hasSurfaces = false;

        for (Step step : job.getSteps().values()) {
            if (step.getLayersByName() != null) {
                for (Layer layer : step.getLayersByName().values()) {
                    if (layer.getFeatures() != null && layer.getFeatures().getFeatures() != null) {
                        for (Feature feature : layer.getFeatures().getFeatures()) {
                            if (feature instanceof Pad) hasPads = true;
                            if (feature instanceof Line) hasLines = true;
                            if (feature instanceof Surface) hasSurfaces = true;
                        }
                    }
                }
            }
        }

        assertTrue(hasPads, "Should have parsed Pad features");
        assertTrue(hasLines, "Should have parsed Line features");
        assertTrue(hasSurfaces, "Should have parsed Surface features");
    }

    @Test
    void testMultilayerSampleEdaData(@TempDir Path tempDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        boolean hasEdaData = false;
        for (Step step : job.getSteps().values()) {
            if (step.getEdaData() != null) {
                hasEdaData = true;
                EdaData edaData = step.getEdaData();
                // EDA data typically contains nets, packages, and components.
                if (edaData.getNetRecords() != null) {
                    assertFalse(edaData.getNetRecords().isEmpty(), "EDA data should have nets");
                }
            }
        }

        assertTrue(hasEdaData, "At least one step should have EDA data");
    }

    @Test
    void testMultilayerSampleComponents(@TempDir Path tempDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        int totalComponents = 0;
        for (Step step : job.getSteps().values()) {
            if (step.getLayersByName() != null) {
                for (Layer layer : step.getLayersByName().values()) {
                    if (layer.getComponents() != null && layer.getComponents().getComponents() != null) {
                        totalComponents += layer.getComponents().getComponents().size();
                    }
                }
            }
        }

        assertTrue(totalComponents > 0, "Should have parsed component data");
    }

    // ==================== Performance/Stress Tests ====================

    @Test
    void testParsePerformance(@TempDir Path tempDir) throws IOException {
        Path odbRoot = Fixtures.extractSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        long startTime = System.currentTimeMillis();
        Job job = new OdbParser().parse(odbRoot);
        long endTime = System.currentTimeMillis();

        assertNotNull(job, "Job should not be null");

        long parseTime = endTime - startTime;
        // Parsing should complete in reasonable time (< 30 seconds for this sample).
        assertTrue(parseTime < 30000,
                "Parsing should complete in under 30 seconds, took: " + parseTime + "ms");
    }
}

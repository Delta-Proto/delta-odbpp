package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Step;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for exporting ODB++ archives to SVG files.
 *
 * <p>Runs against committed, openly-available sample archives via {@link Fixtures}.
 */
class OdbToSvgExportTest {

    private MultiLayerSvgRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new MultiLayerSvgRenderer();
    }

    @Test
    void testExtractAndExportSmallSample(@TempDir Path tempDir) throws IOException {
        // Extract + parse the committed small sample.
        Job job = Fixtures.parseSample(Fixtures.SMALL_SAMPLE, tempDir);
        assertNotNull(job, "Job should be parsed");
        assertNotNull(job.getMatrix(), "Matrix should exist");

        // Get the first step.
        Step step = job.getSteps().values().iterator().next();
        assertNotNull(step, "Step should exist");
        assertFalse(step.getLayersByName().isEmpty(), "Should have layers");

        // Export composite SVG.
        Path compositeSvg = tempDir.resolve("composite.svg");
        renderer.renderJob(job, compositeSvg);
        assertTrue(Files.exists(compositeSvg), "Composite SVG should be created");
        assertTrue(Files.size(compositeSvg) > 100, "SVG should have content");

        // Verify SVG content.
        String svgContent = Files.readString(compositeSvg);
        assertTrue(svgContent.contains("<svg"), "Should contain SVG element");
        assertTrue(svgContent.contains("data-layer-name="), "Should contain layer data attributes");
        // Layer classes can have additional classes like "layer profile" or "layer layer-top".
        assertTrue(svgContent.contains("class=\"layer"), "Should contain layer classes");

        // Export individual layers.
        Path layersDir = tempDir.resolve("layers");
        Map<String, Path> layerFiles = renderer.exportLayersToFiles(step, job.getMatrix(), layersDir);
        assertFalse(layerFiles.isEmpty(), "Should export layer files");

        for (Map.Entry<String, Path> entry : layerFiles.entrySet()) {
            assertTrue(Files.exists(entry.getValue()),
                    "Layer file should exist: " + entry.getKey());
            assertTrue(Files.size(entry.getValue()) > 50,
                    "Layer SVG should have content: " + entry.getKey());
        }
    }

    @Test
    void testExportMultilayerComposite(@TempDir Path tempDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        assertNotNull(job);

        // Export to SVG.
        Path outputSvg = tempDir.resolve("multilayer.svg");
        renderer.renderJob(job, outputSvg);
        assertTrue(Files.exists(outputSvg));

        String content = Files.readString(outputSvg);
        assertTrue(content.contains("<svg"));
    }

    @Test
    void testExportMultilayerLayers(@TempDir Path tempDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        assertNotNull(job);

        // Export composite.
        Path outputSvg = tempDir.resolve("multilayer-composite.svg");
        renderer.renderJob(job, outputSvg);
        assertTrue(Files.exists(outputSvg));

        // Export layers.
        Step step = job.getSteps().values().iterator().next();
        Path layersDir = tempDir.resolve("layers");
        Map<String, Path> files = renderer.exportLayersToFiles(step, job.getMatrix(), layersDir);

        assertFalse(files.isEmpty(), "Should export at least one layer SVG");
    }

    @Test
    void testRenderEmptyJob() throws IOException {
        Job job = new Job();

        StringWriter writer = new StringWriter();
        renderer.renderJob(job, writer);

        String svg = writer.toString();
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("Empty design"));
    }

    @Test
    void testArchiveExtractorWithZip(@TempDir Path tempDir) throws IOException {
        // Create a simple test zip file.
        Path zipFile = tempDir.resolve("test.zip");
        Path testContent = tempDir.resolve("content");
        Files.createDirectories(testContent.resolve("matrix"));
        Files.writeString(testContent.resolve("matrix/matrix"), "# Test matrix\n");

        // Use java.util.zip to create archive.
        try (java.util.zip.ZipOutputStream zos =
                     new java.util.zip.ZipOutputStream(Files.newOutputStream(zipFile))) {
            java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("testboard/matrix/matrix");
            zos.putNextEntry(entry);
            zos.write("# Test matrix\n".getBytes());
            zos.closeEntry();
        }

        // Extract.
        Path extractDir = tempDir.resolve("extracted");
        Path odbRoot = new com.deltaproto.deltaodbpp.OdbArchiveExtractor().extract(zipFile, extractDir);

        assertTrue(Files.isDirectory(odbRoot), "ODB root should exist");
    }

    @Test
    void testMultiLayerRendererWithOptions() throws IOException {
        SvgRenderOptions options = new SvgRenderOptions()
                .withScale(200.0)
                .withBackground("#F0F0F0");

        MultiLayerSvgRenderer customRenderer = new MultiLayerSvgRenderer(options);

        Job job = new Job();
        StringWriter writer = new StringWriter();
        customRenderer.renderJob(job, writer);

        String svg = writer.toString();
        assertTrue(svg.contains("<svg"));
    }
}

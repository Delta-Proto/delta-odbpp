package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the realistic view rasterises to a valid PNG via Apache Batik,
 * using the committed multilayer sample (designodb rigid-flex).
 *
 * <p>Writes {@code target/realistic-multilayer/top.png} and {@code bottom.png}
 * for manual inspection.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealisticPngRasterisationTest {

    private static final Path OUTPUT_DIR = Paths.get("target", "realistic-multilayer");

    private Job job;

    @BeforeAll
    void loadSample(@TempDir Path tempDir) throws IOException {
        job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        Files.createDirectories(OUTPUT_DIR);
    }

    @Test
    void rasteriseTop_producesValidPng() throws IOException {
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer(
                new SvgRenderOptions().withOutputUnit(SvgRenderOptions.OutputUnit.MM));
        byte[] png = renderer.renderRealisticSidePng(job, /*topSide*/ true, 1200, 0);

        assertNotNull(png);
        assertTrue(png.length > 1000,
                "PNG suspiciously small: " + png.length + " bytes");
        assertPngMagic(png);

        Files.write(OUTPUT_DIR.resolve("top.png"), png);
    }

    @Test
    void rasteriseBottom_producesValidPng() throws IOException {
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer(
                new SvgRenderOptions().withOutputUnit(SvgRenderOptions.OutputUnit.MM));
        byte[] png = renderer.renderRealisticSidePng(job, /*topSide*/ false, 1200, 0);

        assertNotNull(png);
        assertTrue(png.length > 1000, "PNG suspiciously small: " + png.length + " bytes");
        assertPngMagic(png);

        Files.write(OUTPUT_DIR.resolve("bottom.png"), png);
    }

    @Test
    void rasterise_widthAndHeightSpecified_producesExactSize() throws IOException {
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer(
                new SvgRenderOptions().withOutputUnit(SvgRenderOptions.OutputUnit.MM));
        byte[] png = renderer.renderRealisticSidePng(job, true, 400, 300);
        assertPngMagic(png);
        int[] dims = readPngDimensions(png);
        assertEquals(400, dims[0], "requested width 400");
        assertEquals(300, dims[1], "requested height 300");
    }

    @Test
    void rasterise_zeroWidthAndZeroHeight_throws() {
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer(
                new SvgRenderOptions().withOutputUnit(SvgRenderOptions.OutputUnit.MM));
        assertThrows(IllegalArgumentException.class,
                () -> renderer.renderRealisticSidePng(job, true, 0, 0));
    }

    // ---- helpers ----

    private static void assertPngMagic(byte[] png) {
        // PNG magic: 89 50 4E 47 0D 0A 1A 0A
        assertTrue(png.length >= 8, "PNG too short to contain magic");
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 0x50, png[1]);
        assertEquals((byte) 0x4E, png[2]);
        assertEquals((byte) 0x47, png[3]);
        assertEquals((byte) 0x0D, png[4]);
        assertEquals((byte) 0x0A, png[5]);
        assertEquals((byte) 0x1A, png[6]);
        assertEquals((byte) 0x0A, png[7]);
    }

    /** Return {width, height} parsed from a PNG's IHDR chunk. */
    private static int[] readPngDimensions(byte[] png) {
        // IHDR starts at byte offset 16 after the magic (8) + length (4) + "IHDR" (4)
        // width = bytes 16..19 (big endian), height = bytes 20..23
        int w = ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16)
                | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF);
        int h = ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16)
                | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF);
        return new int[]{w, h};
    }
}

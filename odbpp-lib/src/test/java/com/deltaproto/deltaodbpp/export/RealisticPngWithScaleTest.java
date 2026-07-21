package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@code renderRealisticSidePngWithScale}: a PNG plus the px↔mm mapping,
 * mirroring delta-gerber's {@code PngWithScale} field semantics.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealisticPngWithScaleTest {

    private Job job;

    @BeforeAll
    void loadSample(@TempDir Path tempDir) throws IOException {
        job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
    }

    private MultiLayerSvgRenderer newRenderer() {
        return new MultiLayerSvgRenderer(
                new SvgRenderOptions().withOutputUnit(SvgRenderOptions.OutputUnit.MM));
    }

    @Test
    void top_producesPngWithPlausibleScale() throws IOException {
        MultiLayerSvgRenderer.PngWithScale r =
                newRenderer().renderRealisticSidePngWithScale(job, true, 1200, 0);

        assertNotNull(r);
        assertNotNull(r.png);
        assertPngMagic(r.png);
        assertEquals(MultiLayerSvgRenderer.Side.TOP, r.side);
        assertFalse(r.mirrored, "top side is not mirrored");

        // Reported PNG dimensions match the actual IHDR.
        int[] dims = readPngDimensions(r.png);
        assertEquals(dims[0], r.widthPx, "reported width must match PNG IHDR");
        assertEquals(dims[1], r.heightPx, "reported height must match PNG IHDR");
        assertEquals(1200, r.widthPx, "requested width honoured");

        // scale × board-size-mm ≈ pixel size.
        assertTrue(r.pxPerMm > 0, "scale must be positive");
        double tol = 1.0; // within 1px
        assertEquals(r.widthPx, r.contentOffsetXpx * 2 + r.widthMm * r.pxPerMm, tol,
                "content width + letterbox should fill the PNG width");
        assertEquals(r.widthMm * r.pxPerMm, r.contentWidthPx, 1e-6);
        // The aspect-matched (single-dimension) call fills at least one axis exactly.
        assertTrue(Math.abs(r.contentWidthPx - r.widthPx) < tol
                        || Math.abs(r.contentHeightPx - r.heightPx) < tol,
                "aspect-matched PNG should fill one axis");
    }

    @Test
    void scaleTimesBoardSize_approximatesPixelSize() throws IOException {
        MultiLayerSvgRenderer.PngWithScale r =
                newRenderer().renderRealisticSidePngWithScale(job, true, 800, 0);
        // width in mm × pxPerMm should be ~ the content width in px (fills the width axis here
        // or is letterboxed), and never exceed the PNG.
        double predicted = r.widthMm * r.pxPerMm;
        assertTrue(predicted <= r.widthPx + 1.0,
                "predicted content px must fit the PNG width");
        assertTrue(predicted > r.widthPx * 0.5,
                "board should occupy a substantial fraction of the image");
        // mmPerPx is the exact inverse.
        assertEquals(1.0 / r.pxPerMm, r.mmPerPx(), 1e-9);
    }

    @Test
    void bottom_isMarkedMirrored() throws IOException {
        MultiLayerSvgRenderer.PngWithScale r =
                newRenderer().renderRealisticSidePngWithScale(job, false, 600, 0);
        assertPngMagic(r.png);
        assertEquals(MultiLayerSvgRenderer.Side.BOTTOM, r.side);
        assertTrue(r.mirrored, "bottom side is X-mirrored (physical underside)");
    }

    @Test
    void bothDimensionsZero_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> newRenderer().renderRealisticSidePngWithScale(job, true, 0, 0));
    }

    // ---- helpers ----

    private static void assertPngMagic(byte[] png) {
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

    private static int[] readPngDimensions(byte[] png) {
        int w = ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16)
                | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF);
        int h = ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16)
                | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF);
        return new int[]{w, h};
    }
}

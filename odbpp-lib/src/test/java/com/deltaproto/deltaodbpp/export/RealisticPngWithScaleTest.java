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

    /**
     * The mm-named fields of {@link MultiLayerSvgRenderer.PngWithScale} must always be true
     * millimetres, whatever the renderer's {@link SvgRenderOptions.OutputUnit}. Historically the
     * viewBox — and hence these fields — carried the output unit (inches by default), so an
     * INCH-configured renderer reported ~1/25.4 of the real mm size. Render the same board with
     * MM output (default) and INCH output and assert the mm fields agree.
     */
    @Test
    void mmFields_areMillimetres_regardlessOfOutputUnit() throws IOException {
        MultiLayerSvgRenderer.PngWithScale mm =
                renderer(SvgRenderOptions.OutputUnit.MM)
                        .renderRealisticSidePngWithScale(job, true, 1000, 0);
        MultiLayerSvgRenderer.PngWithScale inch =
                renderer(SvgRenderOptions.OutputUnit.INCH)
                        .renderRealisticSidePngWithScale(job, true, 1000, 0);

        // Tolerance covers the last-digit rounding of the viewBox string: MM output writes the
        // rectangle in mm at %.4f, INCH output writes it in inches at %.4f then we scale by 25.4,
        // so 0.0001 inch ≈ 0.0025 mm of formatting slack. The old bug was off by a factor of 25.4.
        double tol = 0.01; // mm
        assertEquals(mm.widthMm, inch.widthMm, tol, "widthMm must match across output units");
        assertEquals(mm.heightMm, inch.heightMm, tol, "heightMm must match across output units");
        assertEquals(mm.minXmm, inch.minXmm, tol, "minXmm must match across output units");
        assertEquals(mm.minYmm, inch.minYmm, tol, "minYmm must match across output units");
        // pxPerMm follows the same rectangle at the same pixel size, so it must agree too.
        assertEquals(mm.pxPerMm, inch.pxPerMm, 1e-3, "pxPerMm must match across output units");
    }

    /**
     * The reported mm size matches the board's known physical extent. Uses the small openly
     * available sandbox-odb_wifi sample whose {@code pcb} step is ≈30.0 × 26.8 mm; the viewBox
     * adds a small padding on each side, so the reported rectangle is a little larger than the
     * bare board but must be within a few mm and clearly on the order of tens of mm (not tenths
     * of an inch, which the old inch-valued fields would have been ≈1.18 × 1.06).
     */
    @Test
    void mmFields_matchKnownBoardSize(@TempDir Path tempDir) throws IOException {
        assertTrue(Fixtures.hasSample(Fixtures.SMALL_SAMPLE),
                "sandbox-odb_wifi sample must be present");
        Job wifi = Fixtures.parseSample(Fixtures.SMALL_SAMPLE, tempDir);

        MultiLayerSvgRenderer.PngWithScale mm =
                renderer(SvgRenderOptions.OutputUnit.MM)
                        .renderRealisticSidePngWithScale(wifi, true, 1000, 0);
        MultiLayerSvgRenderer.PngWithScale inch =
                renderer(SvgRenderOptions.OutputUnit.INCH)
                        .renderRealisticSidePngWithScale(wifi, true, 1000, 0);

        // Board is ~30.0 x 26.8 mm; viewBox adds padding so allow a few mm of slack.
        assertEquals(30.0, mm.widthMm, 6.0, "widthMm should reflect the ~30 mm board width");
        assertEquals(26.8, mm.heightMm, 6.0, "heightMm should reflect the ~26.8 mm board height");

        // And the two output units agree (the core guarantee).
        assertEquals(mm.widthMm, inch.widthMm, 1e-3);
        assertEquals(mm.heightMm, inch.heightMm, 1e-3);
    }

    private MultiLayerSvgRenderer renderer(SvgRenderOptions.OutputUnit unit) {
        return new MultiLayerSvgRenderer(new SvgRenderOptions().withOutputUnit(unit));
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

package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.MatrixLayer;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringWriter;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The opt-in layer palette: every layer of the matrix gets the colour of its role and side,
 * and the combined render paints with whatever palette it is handed — and with its built-in
 * colours when it is handed none.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LayerPaletteTest {

    private Job job;
    private Map<String, String> palette;

    @BeforeAll
    void loadSample(@TempDir Path tempDir) throws Exception {
        job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        palette = LayerPalette.kicad(job.getMatrix());
    }

    @Test
    void everyArtworkLayerGetsAColourAndDielectricsGetNone() {
        for (MatrixLayer ml : job.getMatrix().getLayers()) {
            String key = ml.getName().toLowerCase(Locale.ROOT);
            if ("DIELECTRIC".equalsIgnoreCase(ml.getType())) {
                assertFalse(palette.containsKey(key), "dielectric " + ml.getName() + " has no artwork to colour");
            } else {
                String color = palette.get(key);
                assertNotNull(color, "no colour for " + ml.getName());
                assertTrue(color.matches("#[0-9a-f]{6}"), "not a hex colour: " + color);
            }
        }
    }

    @Test
    void copperIsColouredBySideAndInnerLayersDiffer() {
        LayerSideClassifier sides = new LayerSideClassifier(job.getMatrix());
        Set<String> inner = new HashSet<>();
        int innerCount = 0, top = 0, bottom = 0;
        for (MatrixLayer ml : job.getMatrix().getLayers()) {
            String type = ml.getType() == null ? "" : ml.getType().toUpperCase(Locale.ROOT);
            if (!type.equals("SIGNAL") && !type.equals("POWER_GROUND") && !type.equals("MIXED")) continue;
            String color = palette.get(ml.getName().toLowerCase(Locale.ROOT));
            LayerSide side = sides.sideOf(ml.getName());
            if (side == LayerSide.TOP) { assertEquals(LayerPalette.COPPER_TOP, color); top++; }
            else if (side == LayerSide.BOTTOM) { assertEquals(LayerPalette.COPPER_BOTTOM, color); bottom++; }
            else { inner.add(color); innerCount++; }
        }
        assertEquals(1, top, "the sample has one top copper layer");
        assertEquals(1, bottom, "the sample has one bottom copper layer");
        assertTrue(innerCount > 0, "the multilayer sample has inner copper");
        assertEquals(Math.min(innerCount, 8), inner.size(), "inner layers take distinct colours");
        assertFalse(inner.contains(LayerPalette.COPPER_TOP));
        assertFalse(inner.contains(LayerPalette.COPPER_BOTTOM));
    }

    @Test
    void innerCopperColoursCycle() {
        assertEquals(LayerPalette.innerCopper(1), LayerPalette.innerCopper(9));
        assertNotEquals(LayerPalette.innerCopper(1), LayerPalette.innerCopper(2));
    }

    @Test
    void noMatrixGivesAnEmptyPalette() {
        assertTrue(LayerPalette.kicad(null).isEmpty());
    }

    @Test
    void combinedRenderPaintsWithTheGivenPalette() throws Exception {
        String svg = render(new MultiLayerSvgRenderer(mm()).setLayerColors(palette));
        assertTrue(svg.contains(LayerPalette.COPPER_TOP), "top copper in the palette's colour");
        assertTrue(svg.contains(LayerPalette.COPPER_BOTTOM), "bottom copper in the palette's colour");
        assertFalse(svg.contains("#CC0000"), "the built-in signal colour is overridden");
    }

    @Test
    void combinedRenderKeepsItsOwnColoursWithoutAPalette() throws Exception {
        String plain = render(new MultiLayerSvgRenderer(mm()));
        assertTrue(plain.contains("#CC0000"), "built-in signal colour");
        assertFalse(plain.contains(LayerPalette.COPPER_TOP));
        // Handing the palette back restores the defaults exactly.
        String reset = render(new MultiLayerSvgRenderer(mm()).setLayerColors(palette).setLayerColors(null));
        assertEquals(plain, reset);
    }

    private static SvgRenderOptions mm() {
        return new SvgRenderOptions().withOutputUnit(SvgRenderOptions.OutputUnit.MM);
    }

    private String render(MultiLayerSvgRenderer renderer) throws Exception {
        StringWriter w = new StringWriter();
        renderer.renderJob(job, w);
        return w.toString();
    }
}

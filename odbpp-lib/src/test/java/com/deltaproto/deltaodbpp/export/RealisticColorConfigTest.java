package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the configurable soldermask / silkscreen colours for the realistic render.
 *
 * <p>Uses {@code flat_hierarchy-odb.tgz}, a committed openly-available sample that carries
 * both soldermask and silkscreen layers on its top side, so both finishes are exercised.
 * The palette and pairings mirror delta-gerber's {@code SoldermaskColor} /
 * {@code SilkscreenColor} so an ODB++ render and a Gerber render of the same board match.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealisticColorConfigTest {

    private static final String SAMPLE = "flat_hierarchy-odb.tgz";

    private Job job;

    @BeforeAll
    void loadSample(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(Fixtures.hasSample(SAMPLE),
                "sample " + SAMPLE + " not present");
        job = Fixtures.parseSample(SAMPLE, tempDir);
    }

    private String renderTop(MultiLayerSvgRenderer renderer) throws IOException {
        StringWriter w = new StringWriter();
        renderer.renderRealisticJob(job, true, w);
        return w.toString();
    }

    private MultiLayerSvgRenderer newRenderer() {
        return new MultiLayerSvgRenderer(
                new SvgRenderOptions().withOutputUnit(SvgRenderOptions.OutputUnit.MM));
    }

    /** Extract the fill of the pcb-soldermask sheet rect. */
    private static String maskFill(String svg) {
        int i = svg.indexOf("class=\"pcb-soldermask\"");
        if (i < 0) return null;
        int f = svg.indexOf("fill=\"", i);
        if (f < 0) return null;
        int s = f + "fill=\"".length();
        return svg.substring(s, svg.indexOf('"', s));
    }

    /** Extract the fill of the first pcb-silkscreen group. */
    private static String silkFill(String svg) {
        int i = svg.indexOf("class=\"pcb-silkscreen\"");
        if (i < 0) return null;
        int f = svg.indexOf("fill=\"", i);
        if (f < 0) return null;
        int s = f + "fill=\"".length();
        return svg.substring(s, svg.indexOf('"', s));
    }

    @Test
    void defaultRender_isGreenMaskWhiteSilk() throws IOException {
        String svg = renderTop(newRenderer());
        assertEquals("#004200", maskFill(svg), "default soldermask must be green #004200");
        assertEquals("#ffffff", silkFill(svg), "default silkscreen must be white #ffffff");
    }

    @Test
    void allPaletteColors_renderTheirMaskHex() throws IOException {
        assertMaskHex(SoldermaskColor.GREEN, "#004200");
        assertMaskHex(SoldermaskColor.PURPLE, "#ac13a6");
        assertMaskHex(SoldermaskColor.RED, "#bf0100");
        assertMaskHex(SoldermaskColor.YELLOW, "#ffaa16");
        assertMaskHex(SoldermaskColor.BLUE, "#002d8c");
        assertMaskHex(SoldermaskColor.WHITE, "#f7f9fe");
        assertMaskHex(SoldermaskColor.BLACK, "#0f1010");
    }

    private void assertMaskHex(SoldermaskColor color, String hex) throws IOException {
        MultiLayerSvgRenderer r = newRenderer().setSoldermaskColor(color);
        assertEquals(hex, maskFill(renderTop(r)),
                "soldermask " + color + " must render its hex " + hex);
    }

    @Test
    void purpleMask_pairsWithWhiteSilk() throws IOException {
        MultiLayerSvgRenderer r = newRenderer().setSoldermaskColor(SoldermaskColor.PURPLE);
        String svg = renderTop(r);
        assertEquals("#ac13a6", maskFill(svg));
        assertEquals("#ffffff", silkFill(svg), "purple pairs with white silkscreen");
    }

    @Test
    void whiteMask_pairsWithBlackSilk() throws IOException {
        MultiLayerSvgRenderer r = newRenderer().setSoldermaskColor(SoldermaskColor.WHITE);
        String svg = renderTop(r);
        assertEquals("#f7f9fe", maskFill(svg));
        assertEquals("#000000", silkFill(svg), "white mask pairs with black silkscreen");
    }

    @Test
    void silkscreenOverride_isOrderIndependent() throws IOException {
        // Choosing silk YELLOW then mask BLUE must keep yellow silk (not re-pair to white).
        MultiLayerSvgRenderer r = newRenderer()
                .setSilkscreenColor(SilkscreenColor.YELLOW)
                .setSoldermaskColor(SoldermaskColor.BLUE);
        String svg = renderTop(r);
        assertEquals("#002d8c", maskFill(svg));
        assertEquals("#ffdd00", silkFill(svg),
                "yellow silkscreen override must survive a later mask set");
    }

    @Test
    void customHexOverride_isHonored() throws IOException {
        MultiLayerSvgRenderer r = newRenderer().setSoldermaskColor("#123456", "#abcdef");
        String svg = renderTop(r);
        assertEquals("#123456", maskFill(svg), "custom mask hex missing");
        assertEquals("#abcdef", silkFill(svg), "custom silk hex missing");
    }

    @Test
    void soldermaskNone_drawsNoMaskSheet() throws IOException {
        MultiLayerSvgRenderer r = newRenderer().setSoldermaskColor(SoldermaskColor.NONE);
        String svg = renderTop(r);
        assertNull(maskFill(svg), "SoldermaskColor.NONE must not draw a mask sheet rect");
    }

    @Test
    void silkscreenNone_printsNoLegend() throws IOException {
        MultiLayerSvgRenderer r = newRenderer().setSilkscreenColor(SilkscreenColor.NONE);
        String svg = renderTop(r);
        assertNull(silkFill(svg), "SilkscreenColor.NONE must print no legend");
        // Mask sheet is still drawn.
        assertEquals("#004200", maskFill(svg));
    }

    @Test
    void enumFromString_matchesGerberSemantics() {
        assertEquals(SoldermaskColor.GREEN, SoldermaskColor.fromString(null));
        assertEquals(SoldermaskColor.GREEN, SoldermaskColor.fromString("bogus"));
        assertEquals(SoldermaskColor.RED, SoldermaskColor.fromString("red"));
        assertEquals(SoldermaskColor.NONE, SoldermaskColor.fromString("none"));
        assertEquals("#ffffff", SoldermaskColor.GREEN.getSilkscreenColor());
        assertEquals("#000000", SoldermaskColor.WHITE.getSilkscreenColor());
        assertEquals(SilkscreenColor.WHITE, SilkscreenColor.fromString("bogus"));
        assertEquals(SilkscreenColor.NONE, SilkscreenColor.fromString("none"));
    }
}

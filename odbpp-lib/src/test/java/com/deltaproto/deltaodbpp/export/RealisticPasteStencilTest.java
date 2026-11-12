package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The realistic render carries the side's solder paste twice — as deposits and as a stencil
 * foil with the apertures cut out — both hidden, for a viewer to switch on. Hidden means
 * hidden: the picture, and the PNG rasterised from it, are those of the bare board.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealisticPasteStencilTest {

    private Job job;
    private Document top;
    private Document bottom;

    @BeforeAll
    void loadSample(@TempDir Path tempDir) throws Exception {
        job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        top = parse(render(job, true));
        bottom = parse(render(job, false));
    }

    @Test
    void bothSidesCarryHiddenPasteAndStencilGroups() {
        for (Document doc : new Document[] {top, bottom}) {
            Element paste = byClass(doc, "pcb-paste");
            Element stencil = byClass(doc, "pcb-stencil");
            assertNotNull(paste, "paste group");
            assertNotNull(stencil, "stencil group");
            assertEquals("none", paste.getAttribute("display"));
            assertEquals("none", stencil.getAttribute("display"));
            assertEquals("paste", paste.getAttribute("data-stack-layer"));
            assertEquals("stencil", stencil.getAttribute("data-stack-layer"));
            assertTrue(paste.getChildNodes().getLength() > 0, "the paste group draws the paste artwork");
        }
    }

    @Test
    void stencilIsAFoilLargerThanTheBoardWithTheApertureMask() {
        Element stencil = byClass(top, "pcb-stencil");
        Element foil = (Element) stencil.getElementsByTagName("rect").item(0);
        assertNotNull(foil, "the foil is one rectangle");
        assertEquals("url(#stencil-mask)", foil.getAttribute("mask"));

        Element mask = byId(top, "mask", "stencil-mask");
        assertNotNull(mask, "the aperture mask is defined");
        assertEquals("userSpaceOnUse", mask.getAttribute("maskUnits"));

        String[] vb = top.getDocumentElement().getAttribute("viewBox").trim().split("\\s+");
        double boardW = Double.parseDouble(vb[2]), boardH = Double.parseDouble(vb[3]);
        double foilW = Double.parseDouble(foil.getAttribute("width"));
        double foilH = Double.parseDouble(foil.getAttribute("height"));
        assertEquals(foilW, foilH, 1e-6, "a square foil");
        assertTrue(foilW >= Math.max(boardW, boardH) + 2 * MultiLayerSvgRenderer.STENCIL_MARGIN_MM - 1,
                "the foil reaches far past the board on every side: " + foilW);
    }

    @Test
    void pasteAndStencilComeAfterTheBoardSoTheyLieOnTop() {
        Element paste = byClass(top, "pcb-paste");
        Element stencil = byClass(top, "pcb-stencil");
        Element viewport = byId(top, "g", "viewport");
        assertNotNull(viewport);
        assertSame(viewport, paste.getParentNode(), "paste shares the board's viewport transform");
        assertSame(viewport, stencil.getParentNode(), "stencil shares the board's viewport transform");
        // Last two children of the viewport, paste under the stencil.
        Element last = null, secondLast = null;
        NodeList kids = viewport.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element e) { secondLast = last; last = e; }
        }
        assertSame(stencil, last);
        assertSame(paste, secondLast);
    }

    @Test
    void hiddenGroupsStayOutOfThePng() throws Exception {
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer(mm());
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(renderer.renderRealisticSidePng(job, true, 600)));
        int foil = 0xb7bcc1, pixels = img.getWidth() * img.getHeight(), foilPixels = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0xffffff) == foil) foilPixels++;
            }
        }
        // Shown, the foil would cover everything but the apertures.
        assertTrue(foilPixels < pixels / 100, foilPixels + " of " + pixels + " pixels are stencil foil");
    }

    private static SvgRenderOptions mm() {
        return new SvgRenderOptions().withOutputUnit(SvgRenderOptions.OutputUnit.MM);
    }

    private static String render(Job job, boolean topSide) throws Exception {
        StringWriter w = new StringWriter();
        new MultiLayerSvgRenderer(mm()).renderRealisticJob(job, topSide, w);
        return w.toString();
    }

    private static Document parse(String svg) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
    }

    private static Element byClass(Document doc, String cls) {
        NodeList gs = doc.getElementsByTagNameNS("*", "g");
        for (int i = 0; i < gs.getLength(); i++) {
            Element g = (Element) gs.item(i);
            if ((" " + g.getAttribute("class") + " ").contains(" " + cls + " ")) return g;
        }
        return null;
    }

    private static Element byId(Document doc, String tag, String id) {
        NodeList els = doc.getElementsByTagNameNS("*", tag);
        for (int i = 0; i < els.getLength(); i++) {
            Element e = (Element) els.item(i);
            if (id.equals(e.getAttribute("id"))) return e;
        }
        return null;
    }
}

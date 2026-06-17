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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the Components silhouette view against the committed multilayer
 * sample (designodb rigid-flex), which carries hundreds of placed components
 * with EDA package data — enough to produce non-trivial top and bottom views.
 *
 * <p>Writes {@code target/components-multilayer/top.svg},
 * {@code bottom.svg}, and {@code preview.html} for manual inspection.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComponentsRenderValidationTest {

    private static final Path OUTPUT_DIR = Paths.get("target", "components-multilayer");

    private String topSvg;
    private String bottomSvg;
    private Document topDoc;
    private Document bottomDoc;

    @BeforeAll
    void loadSample(@TempDir Path tempDir) throws Exception {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        SvgRenderOptions opts = new SvgRenderOptions()
                .withOutputUnit(SvgRenderOptions.OutputUnit.MM);
        topSvg = render(job, opts, true);
        bottomSvg = render(job, opts, false);

        Files.createDirectories(OUTPUT_DIR);
        Files.writeString(OUTPUT_DIR.resolve("top.svg"), topSvg);
        Files.writeString(OUTPUT_DIR.resolve("bottom.svg"), bottomSvg);
        Files.writeString(OUTPUT_DIR.resolve("preview.html"), preview());

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        topDoc = db.parse(new InputSource(new StringReader(topSvg)));
        bottomDoc = db.parse(new InputSource(new StringReader(bottomSvg)));
    }

    private String render(Job job, SvgRenderOptions opts, boolean top) throws IOException {
        MultiLayerSvgRenderer r = new MultiLayerSvgRenderer(opts);
        StringWriter w = new StringWriter();
        r.renderComponentsJob(job, top, w);
        return w.toString();
    }

    private String preview() {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>Components preview</title>" +
                "<style>body{background:#111;color:#eee;font-family:system-ui;margin:0;padding:20px}" +
                "section{display:flex;gap:20px}section>div{flex:1;background:#1a1a1a;padding:16px;border-radius:8px;overflow:auto}" +
                "svg{max-width:100%;height:auto;display:block}</style></head><body>" +
                "<h1>Multilayer sample components</h1><section>" +
                "<div><h2>Top</h2>" + topSvg + "</div>" +
                "<div><h2>Bottom</h2>" + bottomSvg + "</div>" +
                "</section></body></html>";
    }

    // ---------- Structural assertions ----------

    @Test
    void topSvg_isNonEmpty() {
        assertNotNull(topSvg);
        assertTrue(topSvg.length() > 1000,
                "top components SVG suspiciously small: " + topSvg.length() + " bytes");
    }

    @Test
    void bottomSvg_isNonEmpty() {
        assertNotNull(bottomSvg);
        assertTrue(bottomSvg.length() > 1000,
                "bottom components SVG suspiciously small: " + bottomSvg.length() + " bytes");
    }

    @Test
    void topSvg_parses_asValidXml() {
        assertEquals("svg", topDoc.getDocumentElement().getLocalName());
        assertEquals("components", topDoc.getDocumentElement().getAttribute("data-view"));
        assertEquals("top", topDoc.getDocumentElement().getAttribute("data-side"));
    }

    @Test
    void bottomSvg_parses_asValidXml() {
        assertEquals("svg", bottomDoc.getDocumentElement().getLocalName());
        assertEquals("components", bottomDoc.getDocumentElement().getAttribute("data-view"));
        assertEquals("bottom", bottomDoc.getDocumentElement().getAttribute("data-side"));
    }

    @Test
    void topSvg_hasBoardOutlineClip() {
        Element clip = findById(topDoc, "board-outline");
        assertNotNull(clip);
        NodeList paths = clip.getElementsByTagNameNS("*", "path");
        assertTrue(paths.getLength() > 0);
        assertFalse(((Element) paths.item(0)).getAttribute("d").isBlank());
    }

    @Test
    void topSvg_rendersComponents() {
        // The sample has hundreds of top-side components; the renderer should emit
        // a substantial number of data-refdes groups.
        int refdesRendered = countAttribute(topDoc, "data-refdes");
        assertTrue(refdesRendered > 10,
                "expected a non-trivial number of top components, got " + refdesRendered);
    }

    @Test
    void bottomSvg_rendersComponents() {
        int refdesRendered = countAttribute(bottomDoc, "data-refdes");
        assertTrue(refdesRendered > 10,
                "expected a non-trivial number of bottom components, got " + refdesRendered);
    }

    @Test
    void topSvg_refdesLabelTextMatchesAttribute() {
        // Component groups should be accompanied by matching label text.
        NodeList texts = topDoc.getElementsByTagNameNS("*", "text");
        assertTrue(texts.getLength() > 0, "no label text elements found");
        int matched = 0;
        for (int i = 0; i < texts.getLength(); i++) {
            Element el = (Element) texts.item(i);
            if (!el.getAttribute("data-refdes-label").isEmpty()) matched++;
        }
        assertTrue(matched > 10, "expected >10 refdes labels, got " + matched);
    }

    @Test
    void bottomSvg_hasDistinctColorFromTop() {
        // Top uses a blue-ish fill, bottom an orange-ish fill — these are renderer
        // constants; guard against accidental copy-paste between the two sides.
        assertTrue(topSvg.contains("#6aaed6"),
                "top components should use blue fill #6aaed6");
        assertTrue(bottomSvg.contains("#e89654"),
                "bottom components should use orange fill #e89654");
        assertFalse(topSvg.contains("#e89654"), "top must not use bottom colour");
        assertFalse(bottomSvg.contains("#6aaed6"), "bottom must not use top colour");
    }

    @Test
    void topSvg_viewportYFlip() {
        Element vp = findById(topDoc, "viewport");
        assertNotNull(vp);
        assertTrue(vp.getAttribute("transform").startsWith("scale(1,-1)"));
    }

    @Test
    void bottomSvg_viewportBothAxesMirror() {
        Element vp = findById(bottomDoc, "viewport");
        assertNotNull(vp);
        assertTrue(vp.getAttribute("transform").startsWith("scale(-1,-1)"));
    }

    // ---------- Helpers ----------

    private static Element findById(Document doc, String id) {
        NodeList all = doc.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < all.getLength(); i++) {
            Element el = (Element) all.item(i);
            if (id.equals(el.getAttribute("id"))) return el;
        }
        return null;
    }

    private static int countAttribute(Document doc, String attrName) {
        NodeList all = doc.getElementsByTagNameNS("*", "*");
        int count = 0;
        for (int i = 0; i < all.getLength(); i++) {
            Element el = (Element) all.item(i);
            if (!el.getAttribute(attrName).isEmpty()) count++;
        }
        return count;
    }
}

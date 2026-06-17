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
 * End-to-end validation of the realistic PCB render against the committed
 * multilayer sample (designodb rigid-flex). Validates the rendered SVG's
 * structural properties by parsing it as XML — copper/substrate/soldermask
 * compositing, board-outline clip, drill cutout mask, stack ordering and
 * the viewport mirror transforms.
 *
 * <p>Debug artefacts are written to {@code odbpp-lib/target/realistic-multilayer/}:
 * <ul>
 *   <li>{@code top.svg} — rendered top side</li>
 *   <li>{@code bottom.svg} — rendered bottom side</li>
 *   <li>{@code preview.html} — side-by-side preview page</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealisticRenderValidationTest {

    private static final Path OUTPUT_DIR = Paths.get("target", "realistic-multilayer");

    private String topSvg;
    private String bottomSvg;
    private Document topDoc;
    private Document bottomDoc;

    @BeforeAll
    void loadSample(@TempDir Path tempDir) throws Exception {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        assertNotNull(job.getMatrix(), "the multilayer sample must have a matrix");

        // The model is mm-normalised at parse time, so OutputUnit.MM is a true
        // identity and the viewBox lands in millimetres.
        SvgRenderOptions opts = new SvgRenderOptions()
                .withOutputUnit(SvgRenderOptions.OutputUnit.MM);
        topSvg = renderSide(job, opts, true);
        bottomSvg = renderSide(job, opts, false);

        Files.createDirectories(OUTPUT_DIR);
        Files.writeString(OUTPUT_DIR.resolve("top.svg"), topSvg);
        Files.writeString(OUTPUT_DIR.resolve("bottom.svg"), bottomSvg);
        Files.writeString(OUTPUT_DIR.resolve("preview.html"), makePreviewHtml());

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        topDoc = db.parse(new InputSource(new StringReader(topSvg)));
        bottomDoc = db.parse(new InputSource(new StringReader(bottomSvg)));
    }

    private String renderSide(Job job, SvgRenderOptions opts, boolean topSide) throws IOException {
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer(opts);
        StringWriter w = new StringWriter();
        renderer.renderRealisticJob(job, topSide, w);
        return w.toString();
    }

    private String makePreviewHtml() {
        return "<!doctype html><html><head><meta charset=\"utf-8\">\n" +
                "<title>Multilayer sample realistic preview</title>\n" +
                "<style>body{background:#1a1a1a;color:#eee;font-family:system-ui;margin:0;padding:20px}\n" +
                "section{display:flex;gap:20px}\n" +
                "section>div{background:#222;padding:16px;border-radius:8px;flex:1;overflow:auto}\n" +
                "h1,h2{margin:0 0 12px}\n" +
                "svg{max-width:100%;height:auto;background:#1a1a1a;display:block}</style></head><body>\n" +
                "<h1>Multilayer sample realistic render</h1>\n" +
                "<section>\n" +
                "<div><h2>Top</h2>" + topSvg + "</div>\n" +
                "<div><h2>Bottom</h2>" + bottomSvg + "</div>\n" +
                "</section></body></html>\n";
    }

    // ------------------------------------------------------------------
    // SVG structural validation — parse as XML and assert required shape
    // ------------------------------------------------------------------

    @Test
    void topSvg_parsesAsValidNamespacedXml() {
        assertNotNull(topDoc);
        Element root = topDoc.getDocumentElement();
        assertEquals("svg", root.getLocalName());
        assertEquals("http://www.w3.org/2000/svg", root.getNamespaceURI());
    }

    @Test
    void topSvg_rootHasRequiredAttributes() {
        Element root = topDoc.getDocumentElement();
        assertEquals("realistic", root.getAttribute("data-view"),
                "root must carry data-view=realistic");
        assertEquals("top", root.getAttribute("data-side"));
        assertFalse(root.getAttribute("viewBox").isBlank(), "viewBox required");
        assertFalse(root.getAttribute("width").isBlank(),
                "width required so Batik can rasterise without guessing");
        assertFalse(root.getAttribute("height").isBlank(), "height required");
    }

    @Test
    void bottomSvg_rootMarkedAsBottomSide() {
        assertEquals("bottom", bottomDoc.getDocumentElement().getAttribute("data-side"));
    }

    @Test
    void topSvg_hasBoardOutlineClipPath_withNonEmptyPath() {
        Element clip = findById(topDoc, "board-outline");
        assertNotNull(clip, "missing <clipPath id=board-outline>");
        assertEquals("clipPath", clip.getLocalName());
        NodeList paths = clip.getElementsByTagNameNS("*", "path");
        assertTrue(paths.getLength() > 0, "board-outline must contain at least one <path>");
        Element path = (Element) paths.item(0);
        assertFalse(path.getAttribute("d").isBlank(),
                "board-outline <path d> must be non-empty");
    }

    @Test
    void topSvg_hasSoldermaskAndCopperFinishMasks() {
        // The sample has soldermask layers, so the mask-compositing machinery emits
        // the soldermask mask and the copper-finish (gold pad) mask.
        assertNotNull(findById(topDoc, "sm-top-mask"),
                "soldermask mask (sm-top-mask) missing — mask compositing won't work");
        assertNotNull(findById(topDoc, "cf-top-mask"),
                "copper-finish mask (cf-top-mask) missing — gold pads won't show");
    }

    @Test
    void topSvg_hasDrillCutoutMask() {
        // The sample has drill layers, so a mechanical cutout mask should punch
        // drill holes through the board.
        assertNotNull(findById(topDoc, "mech-mask"),
                "mech-mask missing — the board has drills and they should punch through");
    }

    @Test
    void bottomSvg_hasSoldermaskAndCopperFinishMasks() {
        assertNotNull(findById(bottomDoc, "sm-bottom-mask"));
        assertNotNull(findById(bottomDoc, "cf-bottom-mask"));
        assertNotNull(findById(bottomDoc, "mech-mask"));
    }

    @Test
    void topSvg_hasCoreStackCompositionGroups() {
        assertTrue(hasElementWithAttr(topDoc, "g", "data-stack-layer", "substrate"),
                "substrate group missing");
        assertTrue(hasElementWithAttr(topDoc, "g", "data-stack-layer", "copper"),
                "copper group missing");
        assertTrue(hasElementWithAttr(topDoc, "g", "data-stack-layer", "soldermask"),
                "soldermask group missing");
    }

    @Test
    void topSvg_stackOrder_substrateBeforeCopperBeforeMask() {
        Element board = findById(topDoc, "board");
        assertNotNull(board, "board group missing");
        int substrateIdx = -1, copperIdx = -1, maskIdx = -1;
        NodeList kids = board.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element el) {
                String stack = el.getAttribute("data-stack-layer");
                if ("substrate".equals(stack)) substrateIdx = i;
                else if ("copper".equals(stack)) copperIdx = i;
                else if ("soldermask".equals(stack)) maskIdx = i;
            }
        }
        assertTrue(substrateIdx >= 0, "substrate not found in board group");
        assertTrue(copperIdx > substrateIdx,
                "copper (" + copperIdx + ") must come after substrate (" + substrateIdx + ")");
        assertTrue(maskIdx > copperIdx,
                "soldermask (" + maskIdx + ") must come after copper (" + copperIdx + ")");
    }

    @Test
    void topSvg_copperGroup_containsAtLeastOneNamedLayer() {
        Element copper = findElementByAttr(topDoc, "g", "data-stack-layer", "copper");
        assertNotNull(copper);
        Element inner = firstChildElementWithNonEmptyAttr(copper, "data-layer-name");
        assertNotNull(inner,
                "copper group should contain a child <g> tagged with data-layer-name");
        assertFalse(inner.getAttribute("data-layer-name").isBlank(),
                "copper child layer must carry a non-empty data-layer-name");
    }

    @Test
    void bottomSvg_copperGroup_containsAtLeastOneNamedLayer() {
        Element copper = findElementByAttr(bottomDoc, "g", "data-stack-layer", "copper");
        assertNotNull(copper);
        Element inner = firstChildElementWithNonEmptyAttr(copper, "data-layer-name");
        assertNotNull(inner,
                "bottom copper group should contain a child <g> tagged with data-layer-name");
        assertFalse(inner.getAttribute("data-layer-name").isBlank());
    }

    @Test
    void topSvg_viewportTransform_isYFlipOnly() {
        Element vp = findById(topDoc, "viewport");
        assertNotNull(vp);
        String t = vp.getAttribute("transform");
        assertTrue(t.startsWith("scale(1,-1)"),
                "top viewport must be Y-flip only, got: " + t);
    }

    @Test
    void bottomSvg_viewportTransform_mirrorsBothAxes() {
        Element vp = findById(bottomDoc, "viewport");
        assertNotNull(vp);
        String t = vp.getAttribute("transform");
        assertTrue(t.startsWith("scale(-1,-1)"),
                "bottom viewport must be both-axes mirror, got: " + t);
    }

    @Test
    void topSvg_hasSubstantialContent() {
        assertTrue(topSvg.length() > 5000,
                "top SVG suspiciously small: " + topSvg.length() + " bytes");
    }

    @Test
    void bottomSvg_hasSubstantialContent() {
        assertTrue(bottomSvg.length() > 5000,
                "bottom SVG suspiciously small: " + bottomSvg.length() + " bytes");
    }

    @Test
    void topSvg_containsRealisticColors() {
        assertTrue(topSvg.contains("#666666"), "FR4 substrate colour #666666 missing");
        assertTrue(topSvg.contains("#cccccc") || topSvg.contains("#CCCCCC"),
                "copper silver colour #cccccc missing");
        assertTrue(topSvg.contains("#004200"), "soldermask green #004200 missing");
    }

    @Test
    void topSvg_viewBoxAreaIsPositive() {
        Element root = topDoc.getDocumentElement();
        String[] vb = root.getAttribute("viewBox").trim().split("\\s+");
        double w = Double.parseDouble(vb[2]);
        double h = Double.parseDouble(vb[3]);
        assertTrue(w > 0 && h > 0,
                String.format("viewBox dimensions must be positive, got %f x %f", w, h));
    }

    // ------------------------------------------------------------------
    // DOM helpers
    // ------------------------------------------------------------------

    private static Element findById(Document doc, String id) {
        NodeList all = doc.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < all.getLength(); i++) {
            Element el = (Element) all.item(i);
            if (id.equals(el.getAttribute("id"))) return el;
        }
        return null;
    }

    private static boolean hasElementWithAttr(Document doc, String tag, String attr, String value) {
        return findElementByAttr(doc, tag, attr, value) != null;
    }

    private static Element findElementByAttr(Document doc, String tag, String attr, String value) {
        NodeList els = doc.getElementsByTagNameNS("*", tag);
        for (int i = 0; i < els.getLength(); i++) {
            Element el = (Element) els.item(i);
            if (value.equals(el.getAttribute(attr))) return el;
        }
        return null;
    }

    private static Element firstChildElementWithNonEmptyAttr(Element parent, String attr) {
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element el && !el.getAttribute(attr).isBlank()) {
                return el;
            }
        }
        return null;
    }
}

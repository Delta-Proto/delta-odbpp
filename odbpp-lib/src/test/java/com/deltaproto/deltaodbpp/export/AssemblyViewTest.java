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

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The assembly view is the realistic board with that side's components drawn on it, as one
 * self-contained SVG or PNG, using the committed multilayer sample (designodb rigid-flex).
 *
 * <p>Writes {@code target/assembly-view/{top,bottom}.{svg,png}} for manual inspection.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AssemblyViewTest {

    private static final Path OUTPUT_DIR = Paths.get("target", "assembly-view");

    private Job job;
    private MultiLayerSvgRenderer renderer;

    @BeforeAll
    void loadSample(@TempDir Path tempDir) throws IOException {
        job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        renderer = new MultiLayerSvgRenderer(
                new SvgRenderOptions().withOutputUnit(SvgRenderOptions.OutputUnit.MM));
        Files.createDirectories(OUTPUT_DIR);
    }

    @Test
    void svg_isTheRealisticBoardWithTheComponentsInsideItsViewport() throws Exception {
        for (boolean top : new boolean[] {true, false}) {
            String svg = renderer.renderAssemblySvg(job, top);
            Files.writeString(OUTPUT_DIR.resolve(top ? "top.svg" : "bottom.svg"), svg);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
            Element root = doc.getDocumentElement();
            assertEquals("realistic", root.getAttribute("data-view"));
            assertEquals(top ? "top" : "bottom", root.getAttribute("data-side"));

            Element components = null;
            int refdes = 0, viewports = 0;
            NodeList all = doc.getElementsByTagNameNS("*", "*");
            for (int i = 0; i < all.getLength(); i++) {
                Element el = (Element) all.item(i);
                if ("components".equals(el.getAttribute("data-stack-layer"))) components = el;
                if (!el.getAttribute("data-refdes").isEmpty()) refdes++;
                if ("viewport".equals(el.getAttribute("id"))) viewports++;
            }
            assertNotNull(components, "components group missing");
            assertEquals("viewport", ((Element) components.getParentNode()).getAttribute("id"),
                    "components must share the board's viewport transform");
            assertEquals(1, viewports);
            assertTrue(refdes > 10, "expected components, got " + refdes);
            assertFalse(svg.substring(svg.indexOf("data-stack-layer=\"components\""))
                            .contains("non-scaling-stroke"),
                    "component outlines need a real width to rasterise");
            assertEquals(renderer.renderAssemblySvg(job, top), svg, "rendering is repeatable");
        }
    }

    @Test
    void png_rasterisesBothSides() throws IOException {
        for (boolean top : new boolean[] {true, false}) {
            byte[] png = renderer.renderAssemblySidePng(job, top, 1200);
            Files.write(OUTPUT_DIR.resolve(top ? "top.png" : "bottom.png"), png);
            assertTrue(png.length > 1000, "PNG suspiciously small: " + png.length + " bytes");
            assertEquals((byte) 0x89, png[0]);
            assertEquals('P', png[1]);
            assertEquals('N', png[2]);
            assertEquals('G', png[3]);
            byte[] boardOnly = renderer.renderRealisticSidePng(job, top, 1200);
            assertFalse(java.util.Arrays.equals(png, boardOnly), "components must change the picture");
        }
    }
}

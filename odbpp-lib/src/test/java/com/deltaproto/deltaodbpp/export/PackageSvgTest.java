package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Component;
import com.deltaproto.deltaodbpp.model.EdaData;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Layer;
import com.deltaproto.deltaodbpp.model.MirrorType;
import com.deltaproto.deltaodbpp.model.Step;
import com.deltaproto.deltaodbpp.parser.EdaDataParser;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link PackageSvg} draws a single package exactly as the components and assembly views draw
 * it inside a component's group, in millimetres whatever the views' output unit.
 */
class PackageSvgTest {

    private static final List<String> CHIP = List.of(
            "UNITS=MM",
            "PKG CHIP 3 -2 -1 2 1",
            "RC -2 -1 4 2",
            "PIN 2 S 1.5 0 0 U U",
            "RC 1 -0.5 1 1",
            "PIN 1 S -1.5 0 0 U U",
            "RC -2 -0.5 1 1");

    /** A component group of the views: transform, refdes, package index, and its content. */
    private static final Pattern GROUP = Pattern.compile(
            "    <g transform=\"([^\"]*)\" data-refdes=\"([^\"]*)\" data-package-ref=\"(\\d+)\">\n(.*?)    </g>\n",
            Pattern.DOTALL);

    @Test
    void fragment_isExactlyWhatTheViewsDrawInsideEachComponent(@TempDir Path tmp) throws IOException {
        assumeTrue(Fixtures.hasSample(Fixtures.MULTILAYER_SAMPLE));
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tmp);
        Step step = job.getSteps().values().iterator().next();
        List<EdaData.PackageRecord> packages = step.getEdaData().getPackageRecords();
        Map<String, Component> byRefdes = new HashMap<>();
        for (Layer layer : step.getLayersByName().values()) {
            if (layer.getComponents() == null || layer.getComponents().getComponents() == null) continue;
            for (Component c : layer.getComponents().getComponents()) byRefdes.putIfAbsent(c.getCompName(), c);
        }
        // The fragment is always in mm, so it is compared against views rendered in mm.
        MultiLayerSvgRenderer mm = new MultiLayerSvgRenderer(
                new SvgRenderOptions().withOutputUnit(SvgRenderOptions.OutputUnit.MM));

        int compared = 0;
        for (boolean top : new boolean[] {true, false}) {
            StringWriter components = new StringWriter();
            mm.renderComponentsJob(job, top, components);
            String assembly = mm.renderAssemblySvg(job, top);
            for (String[] view : new String[][] {{components.toString(), "COMPONENTS"}, {assembly, "ASSEMBLY"}}) {
                Matcher m = GROUP.matcher(view[0]);
                while (m.find()) {
                    String inner = m.group(4).replace("\n      ", "\n");
                    inner = inner.startsWith("      ") ? inner.substring(6) : inner;
                    if (inner.contains("data-outline=\"fallback\"")) continue;
                    EdaData.PackageRecord pkg = packages.get(Integer.parseInt(m.group(3)));
                    assertEquals(inner, PackageSvg.fragment(pkg, PackageSvg.Style.valueOf(view[1])),
                            view[1] + " " + m.group(2));
                    Component c = byRefdes.get(m.group(2));
                    if (c != null && c.getPkgRef() == Integer.parseInt(m.group(3))) {
                        assertEquals(m.group(1),
                                PackageSvg.placement(c, pkg, top).svgTransform(c.getX(), c.getY()),
                                "placement of " + m.group(2));
                    }
                    compared++;
                }
            }
        }
        assertTrue(compared > 40, "expected many components, compared " + compared);
    }

    @Test
    void fragment_isInMillimetres_withStableAttributes() throws IOException {
        String svg = PackageSvg.fragment(pkg(CHIP), PackageSvg.Style.COMPONENTS);
        String[] lines = svg.split("\n");
        assertEquals(3, lines.length, svg);
        assertTrue(lines[0].startsWith("<path d=\"M -2.0000 -1.0000") && lines[0].contains("data-outline=\"contour\""), lines[0]);
        assertTrue(lines[0].contains("fill=\"#6b6b6b\"") && lines[0].contains("vector-effect=\"non-scaling-stroke\""), lines[0]);
        assertTrue(lines[1].startsWith("<path ") && lines[1].contains("data-pins=\"2\""), lines[1]);
        // Pin 1 is the second pin listed; its 1 x 1 pad centred on (-1.5, 0): inlay 0.6 mm, capped at 0.45.
        assertEquals("<circle cx=\"-1.5000\" cy=\"0.0000\" r=\"0.2250\" fill=\"#e5392b\" data-pin1=\"1\"/>", lines[2]);
        assertFalse(svg.contains("<svg") || svg.contains("transform"), "a bare fragment");

        String assembly = PackageSvg.fragment(pkg(CHIP), PackageSvg.Style.ASSEMBLY);
        assertFalse(assembly.contains("non-scaling-stroke"));
        assertTrue(assembly.contains("stroke-width=\"0.0300\" fill-opacity=\"0.80\" data-outline=\"contour\""), assembly);
        assertTrue(assembly.contains("stroke-width=\"0.0300\" data-pins=\"2\""), assembly);
    }

    @Test
    void fragment_fallsBackToTheBoundingBox_andIsEmptyWithoutGeometry() throws IOException {
        String box = PackageSvg.fragment(pkg(List.of("UNITS=MM", "PKG BOX 0 1 -1 5 1")), PackageSvg.Style.COMPONENTS);
        assertEquals("<rect x=\"1.0000\" y=\"-1.0000\" width=\"4.0000\" height=\"2.0000\" fill=\"#6b6b6b\" "
                + "stroke=\"#141414\" stroke-width=\"0.5\" vector-effect=\"non-scaling-stroke\" data-outline=\"bbox\"/>\n", box);

        assertEquals("", PackageSvg.fragment(pkg(List.of("UNITS=MM", "PKG NONE 0 0 0 0 0")), PackageSvg.Style.ASSEMBLY));
        assertEquals("", PackageSvg.fragment(null, PackageSvg.Style.COMPONENTS));
    }

    @Test
    void pinOne_prefersPinNamed1_thenA1_thenTheFirst() throws IOException {
        assertEquals("1", PackageSvg.pinOne(pkg(CHIP)).getName());
        EdaData.PackageRecord bga = pkg(List.of("UNITS=MM", "PKG BGA 1 -1 -1 1 1",
                "PIN B2 S 0.5 0.5 0 U U", "PIN a1 S -0.5 0.5 0 U U"));
        assertEquals("a1", PackageSvg.pinOne(bga).getName());
        EdaData.PackageRecord other = pkg(List.of("UNITS=MM", "PKG X 1 -1 -1 1 1",
                "PIN G S 0.5 0.5 0 U U", "PIN D S -0.5 0.5 0 U U"));
        assertEquals("G", PackageSvg.pinOne(other).getName());
        assertNull(PackageSvg.pinOne(pkg(List.of("UNITS=MM", "PKG NOPINS 0 -1 -1 1 1"))));
    }

    @Test
    void placement_turnsCounterClockwiseAfterTheMirror() {
        Component c = new Component();
        c.setRotation(90);
        c.setMirror(MirrorType.NOT_MIRRORED);
        PackageSvg.Placement top = PackageSvg.placement(c, null, true);
        assertFalse(top.flipped());
        assertEquals(-90, top.angleDeg());
        assertEquals("translate(1.0000 2.0000) rotate(-90.00)", top.svgTransform(1, 2));

        PackageSvg.Placement bottom = PackageSvg.placement(c, null, false);
        assertTrue(bottom.flipped());
        assertEquals(90, bottom.angleDeg());
        assertEquals("translate(1.0000 2.0000) rotate(90.00) scale(-1,1)", bottom.svgTransform(1, 2));

        c.setMirror(MirrorType.MIRRORED);
        assertFalse(PackageSvg.isFlipped(c, null, false), "mirror flag M: stored as placed");
    }

    private static EdaData.PackageRecord pkg(List<String> lines) throws IOException {
        return new EdaDataParser().parse(lines, 1.0).getPackageRecords().get(0);
    }
}

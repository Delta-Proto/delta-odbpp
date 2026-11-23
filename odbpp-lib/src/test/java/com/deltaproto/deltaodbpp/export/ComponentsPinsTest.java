package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Component;
import com.deltaproto.deltaodbpp.model.Components;
import com.deltaproto.deltaodbpp.model.EdaData;
import com.deltaproto.deltaodbpp.model.Features;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Layer;
import com.deltaproto.deltaodbpp.model.Line;
import com.deltaproto.deltaodbpp.model.Matrix;
import com.deltaproto.deltaodbpp.model.MatrixLayer;
import com.deltaproto.deltaodbpp.model.MirrorType;
import com.deltaproto.deltaodbpp.model.Step;
import com.deltaproto.deltaodbpp.model.ToeprintRecord;
import com.deltaproto.deltaodbpp.parser.EdaDataParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Components view draws each package's pads on its body and marks pin 1 with an inlay
 * inside its pad, and places the package the way the component's toeprints say it sits.
 */
class ComponentsPinsTest {

    private static final Pattern INLAY = Pattern.compile(
            "<circle cx=\"(-?[\\d.]+)\" cy=\"(-?[\\d.]+)\" r=\"([\\d.]+)\"[^>]*data-pin1=\"([^\"]*)\"");
    private static final Pattern TRANSFORM = Pattern.compile("<g transform=\"([^\"]+)\" data-refdes=\"U1\"");

    /** Body 4 x 2 mm, two 1 x 1 mm pads; pin 1 is listed second, on the left. */
    private static final List<String> TWO_PIN = List.of(
            "UNITS=MM",
            "PKG CHIP 3 -2 -1 2 1",
            "RC -2 -1 4 2",
            "PIN 2 S 1.5 0 0 U U",
            "RC 1 -0.5 1 1",
            "PIN 1 S -1.5 0 0 U U",
            "RC -2 -0.5 1 1");

    @Test
    void padsAreDrawn_andPinOneGetsAnInlayInsideItsPad() throws IOException {
        String svg = render(pkg(TWO_PIN), component(0, MirrorType.NOT_MIRRORED), true);

        assertTrue(svg.contains("data-pins=\"2\""), "both pads belong in the pads path:\n" + svg);
        Matcher m = INLAY.matcher(svg);
        assertTrue(m.find(), "expected a pin-1 inlay, got:\n" + svg);
        assertEquals("1", m.group(4), "the pin named 1 is marked, not the first one listed");
        // Pad spans x -2..-1, y -0.5..0.5. The 20 % margin would leave a 0.6 mm circle; the
        // inlay is capped at 0.45 mm across and stays on the pad's centre.
        assertEquals(-1.5, Double.parseDouble(m.group(1)), 1e-6);
        assertEquals(0.0, Double.parseDouble(m.group(2)), 1e-6);
        assertEquals(0.225, Double.parseDouble(m.group(3)), 1e-6);
        assertFalse(m.find(), "only pin 1 is marked");
    }

    @Test
    void smallPad_keepsAMarginOfAFifthOfItsShortSide() throws IOException {
        EdaData.PackageRecord pkg = pkg(List.of(
                "UNITS=MM",
                "PKG TINY 0 -1 -1 1 1",
                "RC -1 -1 2 2",
                "PIN 1 S 0.25 0.15 0 U U",
                "RC 0 0 0.5 0.3"));

        Matcher m = INLAY.matcher(render(pkg, component(0, MirrorType.NOT_MIRRORED), true));
        assertTrue(m.find());
        // Margin = 20 % of the 0.3 mm short side = 0.06 mm, leaving a 0.18 mm circle.
        assertEquals(0.25, Double.parseDouble(m.group(1)), 1e-6);
        assertEquals(0.15, Double.parseDouble(m.group(2)), 1e-6);
        assertEquals(0.09, Double.parseDouble(m.group(3)), 1e-6);
    }

    @Test
    void roundPad_isMeasuredByTheCircleItSweeps() throws IOException {
        EdaData.PackageRecord pkg = pkg(List.of(
                "UNITS=MM",
                "PKG TP 0 -1 -1 1 1",
                "CR 0 0 1",
                "PIN 1 T 0 0 0 U U",
                "CR 0 0 0.2"));

        Matcher m = INLAY.matcher(render(pkg, component(0, MirrorType.NOT_MIRRORED), true));
        assertTrue(m.find());
        assertEquals(0.0, Double.parseDouble(m.group(1)), 1e-6);
        assertEquals(0.0, Double.parseDouble(m.group(2)), 1e-6);
        // Pad 0.4 mm across: 60 % of it is a 0.24 mm circle.
        assertEquals(0.12, Double.parseDouble(m.group(3)), 1e-6);
    }

    @Test
    void packageWithoutPins_drawsNoPads() throws IOException {
        EdaData.PackageRecord pkg = pkg(List.of("UNITS=MM", "PKG BARE 0 -1 -1 1 1", "RC -1 -1 2 2"));
        String svg = render(pkg, component(0, MirrorType.NOT_MIRRORED), true);
        assertFalse(svg.contains("data-pins"));
        assertFalse(svg.contains("data-pin1"));
    }

    @Test
    void topSide_rotatesClockwise() throws IOException {
        // ODB++ rotation is clockwise; in the model's Y-up frame that is a negative angle.
        assertEquals("translate(10.0000 5.0000) rotate(-90.00)",
                transform(render(pkg(TWO_PIN), component(90, MirrorType.NOT_MIRRORED), true)));
    }

    @Test
    void bottomSide_unmirroredPackageIsFlipped() throws IOException {
        Component c = component(90, MirrorType.NOT_MIRRORED);
        // Pin 2 at (1.5, 0): mirrored in X, then turned counter-clockwise by 90 degrees.
        c.addToeprintRecord(toeprint(0, 10, 3.5));
        c.addToeprintRecord(toeprint(1, 10, 6.5));
        assertEquals("translate(10.0000 5.0000) rotate(90.00) scale(-1,1)",
                transform(render(pkg(TWO_PIN), c, false)));
    }

    @Test
    void bottomSide_toeprintsOverruleTheFlip_whenThePackageIsStoredAsPlaced() throws IOException {
        Component c = component(90, MirrorType.MIRRORED);
        // Pin 2 at (1.5, 0) turned clockwise by 90 degrees, no mirroring.
        c.addToeprintRecord(toeprint(0, 10, 3.5));
        c.addToeprintRecord(toeprint(1, 10, 6.5));
        // A two-pin part on the axis cannot tell the readings apart: the flag decides.
        assertEquals("translate(10.0000 5.0000) rotate(-90.00)",
                transform(render(pkg(TWO_PIN), c, false)));

        // An off-axis pin can: these toeprints only fit the flipped reading, flag or not.
        EdaData.PackageRecord offAxis = pkg(List.of(
                "UNITS=MM",
                "PKG SOT 1 -1 -1 1 1",
                "RC -1 -1 2 2",
                "PIN 1 S 1 0.5 0 U U",
                "RC 0.8 0.3 0.4 0.4"));
        Component flipped = component(90, MirrorType.MIRRORED);
        flipped.addToeprintRecord(toeprint(0, 9.5, 4));
        assertEquals("translate(10.0000 5.0000) rotate(90.00) scale(-1,1)",
                transform(render(offAxis, flipped, false)));
        Component plain = component(90, MirrorType.NOT_MIRRORED);
        plain.addToeprintRecord(toeprint(0, 10.5, 4));
        assertEquals("translate(10.0000 5.0000) rotate(-90.00)",
                transform(render(offAxis, plain, false)));
    }

    @Test
    void label_isCentredOnTheBody_andFitsInsideIt() throws IOException {
        // Body offset from the placement origin: x 1..5, y -1..1, centre (3, 0).
        EdaData.PackageRecord pkg = pkg(List.of("UNITS=MM", "PKG OFF 0 1 -1 5 1", "RC 1 -1 4 2"));
        String svg = render(pkg, component(0, MirrorType.NOT_MIRRORED), true);

        Matcher m = Pattern.compile("<text x=\"(-?[\\d.]+)\" y=\"(-?[\\d.]+)\" font-size=\"([\\d.]+)\"").matcher(svg);
        assertTrue(m.find());
        assertEquals(13.0, Double.parseDouble(m.group(1)), 1e-6, "label x = component x + body centre");
        double size = Double.parseDouble(m.group(3));
        assertTrue(size > 0.5 && size < 2.0, "label must fit the 2 mm tall body, size=" + size);
    }

    // ---- fixture ----

    private static String transform(String svg) {
        Matcher m = TRANSFORM.matcher(svg);
        assertTrue(m.find(), "no component group in:\n" + svg);
        return m.group(1);
    }

    private static EdaData.PackageRecord pkg(List<String> lines) throws IOException {
        return new EdaDataParser().parse(lines, 1.0).getPackageRecords().get(0);
    }

    private static Component component(double rotation, MirrorType mirror) {
        Component c = new Component();
        c.setPkgRef(0);
        c.setX(10);
        c.setY(5);
        c.setRotation(rotation);
        c.setMirror(mirror);
        c.setCompName("U1");
        return c;
    }

    private static ToeprintRecord toeprint(int pin, double x, double y) {
        ToeprintRecord top = new ToeprintRecord();
        top.setPinNumber(pin);
        top.setX(x);
        top.setY(y);
        return top;
    }

    private static String render(EdaData.PackageRecord pkg, Component c, boolean topSide) throws IOException {
        Matrix matrix = new Matrix();
        String[][] rows = {
                {"comp_+_top", "COMPONENT"}, {"top", "SIGNAL"}, {"bottom", "SIGNAL"}, {"comp_+_bot", "COMPONENT"}};
        List<MatrixLayer> matrixLayers = new ArrayList<>();
        Map<String, Layer> layers = new LinkedHashMap<>();
        for (int i = 0; i < rows.length; i++) {
            MatrixLayer ml = new MatrixLayer();
            ml.setRow(i + 1);
            ml.setContext("BOARD");
            ml.setType(rows[i][1]);
            ml.setName(rows[i][0]);
            matrixLayers.add(ml);
            Layer layer = new Layer();
            layer.setName(rows[i][0]);
            layer.setFeatures(new Features());
            layers.put(rows[i][0], layer);
        }
        matrix.setLayers(matrixLayers);

        Components components = new Components();
        components.getComponents().add(c);
        layers.get(topSide ? "comp_+_top" : "comp_+_bot").setComponents(components);

        Features profile = new Features();
        profile.getFeatures().add(line(0, 0, 20, 0));
        profile.getFeatures().add(line(20, 0, 20, 10));
        profile.getFeatures().add(line(20, 10, 0, 10));
        profile.getFeatures().add(line(0, 10, 0, 0));

        EdaData eda = new EdaData();
        eda.setPackageRecords(new ArrayList<>(List.of(pkg)));

        Step step = new Step();
        step.setName("pcb");
        step.setProfile(profile);
        step.setEdaData(eda);
        step.setLayersByName(layers);
        Map<String, Step> steps = new LinkedHashMap<>();
        steps.put("pcb", step);
        Job job = new Job();
        job.setMatrix(matrix);
        job.setSteps(steps);

        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer(
                new SvgRenderOptions().withOutputUnit(SvgRenderOptions.OutputUnit.MM));
        StringWriter w = new StringWriter();
        renderer.renderComponentsJob(job, topSide, w);
        return w.toString();
    }

    private static Line line(double x1, double y1, double x2, double y2) {
        Line l = new Line();
        l.setXs(x1);
        l.setYs(y1);
        l.setXe(x2);
        l.setYe(y2);
        return l;
    }
}

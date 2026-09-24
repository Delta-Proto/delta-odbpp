package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Component;
import com.deltaproto.deltaodbpp.model.Components;
import com.deltaproto.deltaodbpp.model.ContourPolygon;
import com.deltaproto.deltaodbpp.model.EdaData;
import com.deltaproto.deltaodbpp.model.Features;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Layer;
import com.deltaproto.deltaodbpp.model.Line;
import com.deltaproto.deltaodbpp.model.Matrix;
import com.deltaproto.deltaodbpp.model.MatrixLayer;
import com.deltaproto.deltaodbpp.model.MirrorType;
import com.deltaproto.deltaodbpp.model.Step;
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
 * The Components view draws each part from its EDA package outline, not its bounding box.
 *
 * <p>Some writers (KiCad) size the {@code PKG} bounding box to cover the footprint's reference
 * and value text, so a 0402 came out as a 6 mm block and a 20-pin header as a 13 mm wide slab.
 * The {@code CT} outline that follows the record is the part itself; the box is only the
 * fallback when there is none, and a size-less record still gets a visible stub.
 */
class ComponentsOutlineSilhouetteTest {

    private static final Pattern PATH_NUMBERS = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    @Test
    void outlineWins_overAnOversizedBoundingBox() throws IOException {
        EdaData.PackageRecord pkg = record("R_0402", -3.0, -0.5, 3.0, 0.5);
        pkg.getOutline().add(box(-0.925, -0.465, 0.925, 0.465));

        String svg = render(pkg);

        Matcher m = Pattern.compile("<path d=\"([^\"]+)\"[^>]*data-outline=\"contour\"").matcher(svg);
        assertTrue(m.find(), "expected a contour silhouette, got:\n" + svg);
        double[] ext = extent(m.group(1));
        assertEquals(1.85, ext[2] - ext[0], 1e-3, "silhouette width must be the outline's, not the box's");
        assertEquals(0.93, ext[3] - ext[1], 1e-3);
        assertFalse(svg.contains("data-outline=\"bbox\""));
    }

    @Test
    void boundingBox_isUsedAtItsOwnOffsets_whenThereIsNoOutline() throws IOException {
        // Box is deliberately off-centre from the placement origin: x from -1 to 3.
        String svg = render(record("SOT", -1.0, -0.5, 3.0, 0.5));

        Matcher m = Pattern.compile(
                "<rect x=\"(-?[\\d.]+)\" y=\"(-?[\\d.]+)\" width=\"([\\d.]+)\" height=\"([\\d.]+)\"[^>]*data-outline=\"bbox\"")
                .matcher(svg);
        assertTrue(m.find(), "expected a bounding-box silhouette, got:\n" + svg);
        assertEquals(-1.0, Double.parseDouble(m.group(1)), 1e-6);
        assertEquals(-0.5, Double.parseDouble(m.group(2)), 1e-6);
        assertEquals(4.0, Double.parseDouble(m.group(3)), 1e-6);
        assertEquals(1.0, Double.parseDouble(m.group(4)), 1e-6);
    }

    @Test
    void sizelessRecord_stillGetsAVisibleStub() throws IOException {
        String svg = render(record("BARE", 0, 0, 0, 0));
        assertTrue(svg.contains("data-outline=\"fallback\""));
        assertTrue(svg.contains("1 component(s) used fallback outline"));
    }

    @Test
    void circularOutline_rendersAsArcs() throws IOException {
        EdaData.PackageRecord pkg = record("CAN", -2, -2, 2, 2);
        ContourPolygon circle = new ContourPolygon();
        circle.setType(ContourPolygon.Type.ISLAND);
        circle.setXStart(1.5);
        circle.setYStart(0);
        circle.getPolygonParts().add(arc(-1.5, 0, 0, 0));
        circle.getPolygonParts().add(arc(1.5, 0, 0, 0));
        pkg.getOutline().add(circle);

        String svg = render(pkg);

        Matcher m = Pattern.compile("<path d=\"([^\"]+)\"[^>]*data-outline=\"contour\"").matcher(svg);
        assertTrue(m.find());
        String d = m.group(1);
        assertTrue(d.contains(" A 1.5000 1.5000 "), "arc radius should be 1.5 mm: " + d);
        assertEquals(2, d.split(" A ").length - 1, "two half-circle arcs");
    }

    // ---- fixture ----

    private static String render(EdaData.PackageRecord pkg) throws IOException {
        Job job = job(pkg);
        SvgRenderOptions options = new SvgRenderOptions()
                .withOutputUnit(SvgRenderOptions.OutputUnit.MM);
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer(options);
        StringWriter w = new StringWriter();
        renderer.renderComponentsJob(job, true, w);
        return w.toString();
    }

    /** One top-side component at (10, 5), unrotated, referencing the single package. */
    private static Job job(EdaData.PackageRecord pkg) {
        Matrix matrix = new Matrix();
        MatrixLayer comp = new MatrixLayer();
        comp.setRow(1);
        comp.setContext("BOARD");
        comp.setType("COMPONENT");
        comp.setName("comp_+_top");
        MatrixLayer top = new MatrixLayer();
        top.setRow(2);
        top.setContext("BOARD");
        top.setType("SIGNAL");
        top.setName("top");
        matrix.setLayers(List.of(comp, top));

        Features profile = new Features();
        profile.getFeatures().add(line(0, 0, 20, 0));
        profile.getFeatures().add(line(20, 0, 20, 10));
        profile.getFeatures().add(line(20, 10, 0, 10));
        profile.getFeatures().add(line(0, 10, 0, 0));

        Component c = new Component();
        c.setPkgRef(0);
        c.setX(10);
        c.setY(5);
        c.setRotation(0);
        c.setMirror(MirrorType.NOT_MIRRORED);
        c.setCompName("R1");
        Components components = new Components();
        components.getComponents().add(c);
        Layer compLayer = new Layer();
        compLayer.setName("comp_+_top");
        compLayer.setComponents(components);
        compLayer.setFeatures(new Features());

        Layer copper = new Layer();
        copper.setName("top");
        copper.setFeatures(new Features());

        EdaData eda = new EdaData();
        eda.setPackageRecords(new ArrayList<>(List.of(pkg)));

        Step step = new Step();
        step.setName("pcb");
        step.setProfile(profile);
        step.setEdaData(eda);
        Map<String, Layer> layers = new LinkedHashMap<>();
        layers.put("comp_+_top", compLayer);
        layers.put("top", copper);
        step.setLayersByName(layers);

        Map<String, Step> steps = new LinkedHashMap<>();
        steps.put("pcb", step);
        Job job = new Job();
        job.setMatrix(matrix);
        job.setSteps(steps);
        return job;
    }

    private static EdaData.PackageRecord record(String name, double x0, double y0, double x1, double y1) {
        EdaData.PackageRecord pkg = new EdaData.PackageRecord();
        pkg.setName(name);
        pkg.setIndex(0);
        pkg.setXMin(x0);
        pkg.setYMin(y0);
        pkg.setXMax(x1);
        pkg.setYMax(y1);
        return pkg;
    }

    private static ContourPolygon box(double x0, double y0, double x1, double y1) {
        ContourPolygon p = new ContourPolygon();
        p.setType(ContourPolygon.Type.ISLAND);
        p.setXStart(x0);
        p.setYStart(y0);
        p.getPolygonParts().add(segment(x1, y0));
        p.getPolygonParts().add(segment(x1, y1));
        p.getPolygonParts().add(segment(x0, y1));
        p.getPolygonParts().add(segment(x0, y0));
        return p;
    }

    private static ContourPolygon.PolygonPart segment(double x, double y) {
        ContourPolygon.PolygonPart part = new ContourPolygon.PolygonPart();
        part.setType(ContourPolygon.PolygonPart.Type.SEGMENT);
        part.setEndX(x);
        part.setEndY(y);
        return part;
    }

    private static ContourPolygon.PolygonPart arc(double x, double y, double cx, double cy) {
        ContourPolygon.PolygonPart part = new ContourPolygon.PolygonPart();
        part.setType(ContourPolygon.PolygonPart.Type.ARC);
        part.setEndX(x);
        part.setEndY(y);
        part.setXCenter(cx);
        part.setYCenter(cy);
        part.setClockwise(true);
        return part;
    }

    private static Line line(double x1, double y1, double x2, double y2) {
        Line l = new Line();
        l.setXs(x1);
        l.setYs(y1);
        l.setXe(x2);
        l.setYe(y2);
        return l;
    }

    /** Min/max x and y over the M/L coordinates of a straight-segment path. */
    private static double[] extent(String d) {
        List<Double> nums = new ArrayList<>();
        Matcher m = PATH_NUMBERS.matcher(d);
        while (m.find()) nums.add(Double.parseDouble(m.group()));
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i + 1 < nums.size(); i += 2) {
            minX = Math.min(minX, nums.get(i));
            maxX = Math.max(maxX, nums.get(i));
            minY = Math.min(minY, nums.get(i + 1));
            maxY = Math.max(maxY, nums.get(i + 1));
        }
        return new double[] {minX, minY, maxX, maxY};
    }
}

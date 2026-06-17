package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SvgRendererTest {

    private SvgRenderer renderer;
    private Features features;

    @BeforeEach
    void setUp() {
        renderer = new SvgRenderer();
        features = new Features();
    }

    @Test
    void testRenderEmptyFeatures() throws IOException {
        StringWriter writer = new StringWriter();
        renderer.renderFeatures(features, writer);

        String svg = writer.toString();
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("</svg>"));
        assertTrue(svg.contains("Empty layer"));
    }

    @Test
    void testRenderPad() throws IOException {
        Pad pad = new Pad();
        pad.setX(1.0);
        pad.setY(2.0);
        pad.setSymbolNumber(0);
        features.getFeatures().add(pad);

        StringWriter writer = new StringWriter();
        renderer.renderFeatures(features, writer);

        String svg = writer.toString();
        assertTrue(svg.contains("<svg"), "Should contain svg element");
        assertTrue(svg.contains("<circle"), "Should contain circle element");
        assertTrue(svg.contains("cx="), "Should contain cx attribute");
        assertTrue(svg.contains("cy="), "Should contain cy attribute");
    }

    @Test
    void testRenderLine() throws IOException {
        Line line = new Line();
        line.setXs(0.0);
        line.setYs(0.0);
        line.setXe(1.0);
        line.setYe(1.0);
        line.setSymbolNumber(0);
        features.getFeatures().add(line);

        StringWriter writer = new StringWriter();
        renderer.renderFeatures(features, writer);

        String svg = writer.toString();
        assertTrue(svg.contains("<svg"), "Should contain svg element");
        // Lines are now rendered as paths: <path d="M x1 y1 L x2 y2" .../>
        assertTrue(svg.contains("<path d=\"M"), "Should contain path element for line");
        assertTrue(svg.contains(" L "), "Should contain L command for line");
        assertTrue(svg.contains("stroke-linecap=\"round\""), "Should have round linecap");
    }

    @Test
    void testRenderArc() throws IOException {
        Arc arc = new Arc();
        arc.setXs(0.0);
        arc.setYs(1.0);
        arc.setXe(1.0);
        arc.setYe(0.0);
        arc.setXc(0.0);
        arc.setYc(0.0);
        arc.setCw("Y");
        arc.setSymbolNumber(0);
        features.getFeatures().add(arc);

        StringWriter writer = new StringWriter();
        renderer.renderFeatures(features, writer);

        String svg = writer.toString();
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("<path"));
        assertTrue(svg.contains("d=\"M"));
    }

    @Test
    void testRenderSurface() throws IOException {
        Surface surface = new Surface();
        surface.setPolarity(Polarity.POSITIVE);

        ContourPolygon polygon = new ContourPolygon();
        polygon.setXStart(0.0);
        polygon.setYStart(0.0);
        polygon.setType(ContourPolygon.Type.ISLAND);

        ContourPolygon.PolygonPart part1 = new ContourPolygon.PolygonPart();
        part1.setType(ContourPolygon.PolygonPart.Type.SEGMENT);
        part1.setEndX(1.0);
        part1.setEndY(0.0);
        polygon.getPolygonParts().add(part1);

        ContourPolygon.PolygonPart part2 = new ContourPolygon.PolygonPart();
        part2.setType(ContourPolygon.PolygonPart.Type.SEGMENT);
        part2.setEndX(1.0);
        part2.setEndY(1.0);
        polygon.getPolygonParts().add(part2);

        ContourPolygon.PolygonPart part3 = new ContourPolygon.PolygonPart();
        part3.setType(ContourPolygon.PolygonPart.Type.SEGMENT);
        part3.setEndX(0.0);
        part3.setEndY(0.0);
        polygon.getPolygonParts().add(part3);

        surface.getPolygons().add(polygon);
        features.getFeatures().add(surface);

        StringWriter writer = new StringWriter();
        renderer.renderFeatures(features, writer);

        String svg = writer.toString();
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("<path"));
        assertTrue(svg.contains("Z")); // Closed path
    }

    @Test
    void testRenderText() throws IOException {
        Text text = new Text();
        text.setX(1.0);
        text.setY(2.0);
        text.setText("Hello");
        text.setYsize(0.1);
        features.getFeatures().add(text);

        StringWriter writer = new StringWriter();
        renderer.renderFeatures(features, writer);

        String svg = writer.toString();
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("<text"));
        assertTrue(svg.contains("Hello"));
    }

    @Test
    void testRenderWithCustomOptions() throws IOException {
        Pad pad = new Pad();
        pad.setX(1.0);
        pad.setY(2.0);
        features.getFeatures().add(pad);

        SvgRenderOptions options = new SvgRenderOptions()
                .withColor("#FF0000")
                .withScale(200.0)
                .withBackground("#FFFFFF");

        SvgRenderer customRenderer = new SvgRenderer(options);
        StringWriter writer = new StringWriter();
        customRenderer.renderFeatures(features, writer);

        String svg = writer.toString();
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("#FF0000"));
    }

    @Test
    void testRenderToFile(@TempDir Path tempDir) throws IOException {
        Pad pad = new Pad();
        pad.setX(1.0);
        pad.setY(2.0);
        features.getFeatures().add(pad);

        Path outputPath = tempDir.resolve("test.svg");
        renderer.renderFeatures(features, outputPath);

        assertTrue(Files.exists(outputPath));
        String content = Files.readString(outputPath);
        assertTrue(content.contains("<svg"));
        assertTrue(content.contains("<circle"));
    }

    @Test
    void testRenderLayer() throws IOException {
        Layer layer = new Layer();
        layer.setName("test_layer");
        layer.setFeatures(features);

        Pad pad = new Pad();
        pad.setX(1.0);
        pad.setY(2.0);
        features.getFeatures().add(pad);

        StringWriter writer = new StringWriter();
        renderer.renderLayer(layer, writer);

        String svg = writer.toString();
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("<circle"));
    }

    @Test
    void testRenderMultipleFeatures() throws IOException {
        // Add multiple different features
        Pad pad = new Pad();
        pad.setX(0.0);
        pad.setY(0.0);
        features.getFeatures().add(pad);

        Line line = new Line();
        line.setXs(0.0);
        line.setYs(0.0);
        line.setXe(1.0);
        line.setYe(1.0);
        features.getFeatures().add(line);

        StringWriter writer = new StringWriter();
        renderer.renderFeatures(features, writer);

        String svg = writer.toString();
        assertTrue(svg.contains("<circle"));
        // Lines are now rendered as paths
        assertTrue(svg.contains("<path d=\"M"), "Should contain path element for line");
    }

    @Test
    void testRenderBarcode() throws IOException {
        Barcode barcode = new Barcode();
        barcode.setX(1.0);
        barcode.setY(2.0);
        barcode.setWidth(0.5);
        barcode.setHeight(0.2);
        barcode.setOrientDefRotation(0);
        barcode.setText("123456");
        features.getFeatures().add(barcode);

        StringWriter writer = new StringWriter();
        renderer.renderFeatures(features, writer);

        String svg = writer.toString();
        assertTrue(svg.contains("<svg"), "Should contain svg element");
        assertTrue(svg.contains("<rect"), "Should contain rect element for barcode");
        assertTrue(svg.contains("<g"), "Should contain group for barcode");
    }

    @Test
    void testRenderBarcodeWithRotation() throws IOException {
        Barcode barcode = new Barcode();
        barcode.setX(1.0);
        barcode.setY(2.0);
        barcode.setWidth(0.5);
        barcode.setHeight(0.2);
        barcode.setOrientDefRotation(45.0);
        features.getFeatures().add(barcode);

        StringWriter writer = new StringWriter();
        renderer.renderFeatures(features, writer);

        String svg = writer.toString();
        // Check for rotation transform - angle and center point
        assertTrue(svg.contains("transform=") && svg.contains("rotate(45"),
                "Should contain rotation transform. Got: " + svg);
    }

    @Test
    void testRenderProfile() throws IOException {
        Profile profile = new Profile();

        Surface surface = new Surface();
        surface.setPolarity(Polarity.POSITIVE);

        ContourPolygon polygon = new ContourPolygon();
        polygon.setXStart(0.0);
        polygon.setYStart(0.0);
        polygon.setType(ContourPolygon.Type.ISLAND);

        // Create a rectangular outline
        ContourPolygon.PolygonPart part1 = new ContourPolygon.PolygonPart();
        part1.setType(ContourPolygon.PolygonPart.Type.SEGMENT);
        part1.setEndX(2.0);
        part1.setEndY(0.0);
        polygon.getPolygonParts().add(part1);

        ContourPolygon.PolygonPart part2 = new ContourPolygon.PolygonPart();
        part2.setType(ContourPolygon.PolygonPart.Type.SEGMENT);
        part2.setEndX(2.0);
        part2.setEndY(1.5);
        polygon.getPolygonParts().add(part2);

        ContourPolygon.PolygonPart part3 = new ContourPolygon.PolygonPart();
        part3.setType(ContourPolygon.PolygonPart.Type.SEGMENT);
        part3.setEndX(0.0);
        part3.setEndY(1.5);
        polygon.getPolygonParts().add(part3);

        ContourPolygon.PolygonPart part4 = new ContourPolygon.PolygonPart();
        part4.setType(ContourPolygon.PolygonPart.Type.SEGMENT);
        part4.setEndX(0.0);
        part4.setEndY(0.0);
        polygon.getPolygonParts().add(part4);

        surface.getPolygons().add(polygon);
        profile.getSurfaces().add(surface);

        StringWriter writer = new StringWriter();
        renderer.renderProfile(profile, writer);

        String svg = writer.toString();
        assertTrue(svg.contains("<svg"), "Should contain svg element");
        assertTrue(svg.contains("<path"), "Should contain path element for profile outline");
        assertTrue(svg.contains("fill=\"none\""), "Profile should be outline only");
        assertTrue(svg.contains("stroke="), "Should have stroke for visibility");
    }

    @Test
    void testRenderEmptyProfile() throws IOException {
        Profile profile = new Profile();

        StringWriter writer = new StringWriter();
        renderer.renderProfile(profile, writer);

        String svg = writer.toString();
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("Empty layer"));
    }

    @Test
    void testRenderDrillLayer() throws IOException {
        Features drillFeatures = new Features();

        // Add some drill holes
        Pad hole1 = new Pad();
        hole1.setX(0.5);
        hole1.setY(0.5);
        hole1.setSymbolNumber(1);
        drillFeatures.getFeatures().add(hole1);

        Pad hole2 = new Pad();
        hole2.setX(1.5);
        hole2.setY(0.5);
        hole2.setSymbolNumber(2);
        drillFeatures.getFeatures().add(hole2);

        // Create drill tools
        java.util.List<Tool> tools = new java.util.ArrayList<>();
        Tool tool1 = new Tool();
        tool1.setNum(1);
        tool1.setFinishSize(0.020); // 20 mil hole
        tools.add(tool1);

        Tool tool2 = new Tool();
        tool2.setNum(2);
        tool2.setFinishSize(0.040); // 40 mil hole
        tools.add(tool2);

        StringWriter writer = new StringWriter();
        renderer.renderDrillLayer(drillFeatures, tools, writer);

        String svg = writer.toString();
        assertTrue(svg.contains("<svg"), "Should contain svg element");
        assertTrue(svg.contains("<circle"), "Should contain circle elements for holes");
        assertTrue(svg.contains("fill=\"white\""), "Drill holes should have white fill");
        assertTrue(svg.contains("<line"), "Should contain crosshairs");
    }

    @Test
    void testRenderDrillLayerWithoutTools() throws IOException {
        Features drillFeatures = new Features();

        Pad hole = new Pad();
        hole.setX(1.0);
        hole.setY(1.0);
        hole.setSymbolNumber(1);
        drillFeatures.getFeatures().add(hole);

        StringWriter writer = new StringWriter();
        renderer.renderDrillLayer(drillFeatures, null, writer);

        String svg = writer.toString();
        assertTrue(svg.contains("<svg"), "Should contain svg element");
        assertTrue(svg.contains("<circle"), "Should still render holes with default size");
    }
}

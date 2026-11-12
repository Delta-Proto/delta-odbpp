package com.deltaproto.deltaodbpp.export.gerber;

import com.deltaproto.deltagerber.model.gerber.GerberDocument;
import com.deltaproto.deltagerber.parser.GerberParser;
import com.deltaproto.deltaodbpp.model.ContourPolygon;
import com.deltaproto.deltaodbpp.model.Features;
import com.deltaproto.deltaodbpp.model.Pad;
import com.deltaproto.deltaodbpp.model.Polarity;
import com.deltaproto.deltaodbpp.model.Surface;
import com.deltaproto.deltaodbpp.testutil.GerberRaster;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GerberLayerExporterTest {

    private static ContourPolygon.PolygonPart segment(double x, double y) {
        ContourPolygon.PolygonPart p = new ContourPolygon.PolygonPart();
        p.setType(ContourPolygon.PolygonPart.Type.SEGMENT);
        p.setEndX(x);
        p.setEndY(y);
        return p;
    }

    private static ContourPolygon.PolygonPart arc(double x, double y, double cx, double cy, boolean cw) {
        ContourPolygon.PolygonPart p = new ContourPolygon.PolygonPart();
        p.setType(ContourPolygon.PolygonPart.Type.ARC);
        p.setEndX(x);
        p.setEndY(y);
        p.setXCenter(cx);
        p.setYCenter(cy);
        p.setClockwise(cw);
        return p;
    }

    private static ContourPolygon square(double x0, double y0, double size) {
        ContourPolygon c = new ContourPolygon();
        c.setType(ContourPolygon.Type.ISLAND);
        c.setXStart(x0);
        c.setYStart(y0);
        c.getPolygonParts().add(segment(x0 + size, y0));
        c.getPolygonParts().add(segment(x0 + size, y0 + size));
        c.getPolygonParts().add(segment(x0, y0 + size));
        c.getPolygonParts().add(segment(x0, y0));
        return c;
    }

    /** A contour that is one full-circle arc: start point on the circle, one OC back to it. */
    private static ContourPolygon circle(ContourPolygon.Type type, double cx, double cy, double r) {
        ContourPolygon c = new ContourPolygon();
        c.setType(type);
        c.setXStart(cx + r);
        c.setYStart(cy);
        c.getPolygonParts().add(arc(cx + r, cy, cx, cy, false));
        return c;
    }

    private static BufferedImage render(Surface surface) {
        Features features = new Features();
        features.setUnits("MM");
        features.getFeatures().add(surface);
        GerberWriter writer = new GerberWriter();
        ApertureRegistry registry = new ApertureRegistry();
        writer.setApertureRegistry(registry);
        GerberLayerExporter exporter = new GerberLayerExporter(null, null);
        exporter.export(features, writer, registry);
        assertTrue(exporter.getWarnings().isEmpty(), exporter.getWarnings().toString());
        GerberDocument doc = new GerberParser().parse(writer.build());
        assertTrue(doc.getWarnings().isEmpty(), doc.getWarnings().toString());
        return GerberRaster.rasterise(doc, new Rectangle2D.Double(-1, -1, 12, 12), 10);
    }

    private static Pad pad(double x, double y, int symbol) {
        Pad pad = new Pad();
        pad.setX(x);
        pad.setY(y);
        pad.setSymbolNumber(symbol);
        pad.setPolarity("P");
        return pad;
    }

    /** Converts a metric layer whose symbol table is the given names, in order. */
    private static BufferedImage render(List<String> symbols, List<Pad> pads, List<String> warnings) {
        Features features = new Features();
        features.setUnits("MM");
        for (int i = 0; i < symbols.size(); i++) {
            features.getSymbolTable().put(i, symbols.get(i));
        }
        features.getFeatures().addAll(pads);
        GerberWriter writer = new GerberWriter();
        ApertureRegistry registry = new ApertureRegistry();
        writer.setApertureRegistry(registry);
        GerberLayerExporter exporter = new GerberLayerExporter(null, null);
        exporter.export(features, writer, registry);
        warnings.addAll(exporter.getWarnings());
        GerberDocument doc = new GerberParser().parse(writer.build());
        assertTrue(doc.getWarnings().isEmpty(), doc.getWarnings().toString());
        return GerberRaster.rasterise(doc, new Rectangle2D.Double(-1, -1, 12, 12), 10);
    }

    /** Pixel value at a point in mm of the 12 x 12 mm window rendered at 10 px/mm, Y up. */
    private static int ink(BufferedImage img, double x, double y) {
        return img.getRaster().getSample((int) Math.round((x + 1) * 10), (int) Math.round((11 - y) * 10), 0);
    }

    /**
     * Cadence writes the clearance around a mounting hole in a plane as a hole contour made of
     * a single full-circle arc. Such a ring has one vertex; it must be cut out, not dropped.
     */
    @Test
    void fullCircleHoleInASurfaceIsCutOut() {
        Surface surface = new Surface();
        surface.setPolarity(Polarity.POSITIVE);
        surface.getPolygons().add(square(0, 0, 10));
        surface.getPolygons().add(circle(ContourPolygon.Type.HOLE, 5, 5, 2));
        BufferedImage img = render(surface);
        assertEquals(255, ink(img, 1, 1), "island copper");
        assertEquals(255, ink(img, 5, 8), "copper between hole and edge");
        assertEquals(0, ink(img, 5, 5), "centre of the round hole");
        assertEquals(0, ink(img, 6.5, 5), "inside the round hole");
    }

    @Test
    void fullCircleIslandIsFilled() {
        Surface surface = new Surface();
        surface.setPolarity(Polarity.POSITIVE);
        surface.getPolygons().add(circle(ContourPolygon.Type.ISLAND, 5, 5, 3));
        BufferedImage img = render(surface);
        assertEquals(255, ink(img, 5, 5));
        assertEquals(255, ink(img, 7, 5));
        assertEquals(0, ink(img, 9, 5));
    }

    /**
     * A standard symbol no Gerber aperture describes is drawn from its outline, as regions
     * cut in around the openings, rather than replaced by a dot.
     */
    @Test
    void standardSymbolWithoutAnApertureIsDrawnAsRegions() {
        List<String> warnings = new java.util.ArrayList<>();
        BufferedImage img = render(List.of("donut_rc6000x4000x500"), List.of(pad(5, 5, 0)), warnings);

        assertTrue(warnings.isEmpty(), warnings.toString());
        assertTrue(ink(img, 5 + 2.75, 5) > 0, "right bar");
        assertTrue(ink(img, 5 - 2.75, 5) > 0, "left bar");
        assertTrue(ink(img, 5, 5 + 1.75) > 0, "top bar");
        assertEquals(0, ink(img, 5, 5), "open in the middle");
        assertEquals(0, ink(img, 5 + 2.2, 5 + 1.2), "inside the ring");
        assertEquals(0, ink(img, 5 + 3.3, 5), "nothing outside");
    }

    @Test
    void outlineSymbolFollowsThePadsOrientationAndResize() {
        // A triangle stands on its base; half a turn stands it on its apex.
        List<String> warnings = new java.util.ArrayList<>();
        Pad turned = pad(3, 5, 0);
        turned.setOrientationType(2);
        // A half oval is flat on its left; mirrored, on its right. Resized to twice its size.
        Pad mirrored = pad(8, 5, 1);
        mirrored.setOrientationType(4);
        mirrored.setResizeFactor(2.0);
        BufferedImage img = render(List.of("tri4000x4000", "oval_h1500x1000"),
                List.of(turned, mirrored), warnings);

        assertTrue(warnings.isEmpty(), warnings.toString());
        assertTrue(ink(img, 3 + 1.5, 5 + 1.7) > 0, "the base is at the top");
        assertEquals(0, ink(img, 3 + 1.5, 5 - 1.7));
        assertTrue(ink(img, 3, 5 - 1.5) > 0, "the apex points down");
        assertTrue(ink(img, 8 + 1.3, 5 + 0.8) > 0, "square corner on the right");
        assertEquals(0, ink(img, 8 - 1.4, 5 + 0.85), "round on the left");
    }

    @Test
    void symbolOfSeparateIslandsKeepsThemApart() {
        // Four columns by two rows of pads with 0.5 mm gaps, in an 8 x 4 mm frame.
        List<String> warnings = new java.util.ArrayList<>();
        BufferedImage img = render(List.of("dpack8000x4000x500x500x4x2", "null500"),
                List.of(pad(5, 5, 0), pad(5, 9, 1)), warnings);

        assertTrue(warnings.isEmpty(), warnings.toString());
        assertTrue(ink(img, 5 - 3.2, 5 - 1.2) > 0, "first pad");
        assertTrue(ink(img, 5 + 3.2, 5 + 1.2) > 0, "last pad");
        assertEquals(0, ink(img, 5, 5), "gap between the rows and the middle columns");
        assertEquals(0, ink(img, 5, 9), "the null symbol draws nothing");
    }

    @Test
    void unknownSymbolIsReportedAndMarkedWithADot() {
        List<String> warnings = new java.util.ArrayList<>();
        BufferedImage img = render(List.of("no_such_symbol"), List.of(pad(5, 5, 0)), warnings);

        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("no_such_symbol"));
        assertEquals(0, ink(img, 5 + 0.3, 5));
    }
}

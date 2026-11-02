package com.deltaproto.deltaodbpp.export.gerber;

import com.deltaproto.deltagerber.model.gerber.GerberDocument;
import com.deltaproto.deltagerber.parser.GerberParser;
import com.deltaproto.deltaodbpp.model.ContourPolygon;
import com.deltaproto.deltaodbpp.model.Features;
import com.deltaproto.deltaodbpp.model.Polarity;
import com.deltaproto.deltaodbpp.model.Surface;
import com.deltaproto.deltaodbpp.testutil.GerberRaster;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

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
}

package com.deltaproto.deltaodbpp.export.render;

import com.deltaproto.deltaodbpp.model.Arc;
import com.deltaproto.deltaodbpp.model.Barcode;
import com.deltaproto.deltaodbpp.model.ContourPolygon;
import com.deltaproto.deltaodbpp.model.Feature;
import com.deltaproto.deltaodbpp.model.Features;
import com.deltaproto.deltaodbpp.model.Line;
import com.deltaproto.deltaodbpp.model.Pad;
import com.deltaproto.deltaodbpp.model.Surface;
import com.deltaproto.deltaodbpp.model.Text;

/**
 * The bounding box of feature geometry, in model units (mm).
 *
 * <p>Measures the centre lines of features: a pad counts as its position and a line as
 * its two end points, so the box of a layer does not depend on symbol sizes. Arcs count
 * with the part of the circle they actually sweep — neither their centre, which a
 * shallow arc has far away, nor only their end points, which would cut a round outline
 * in half.
 */
public final class Extent {

    private double minX = Double.POSITIVE_INFINITY;
    private double minY = Double.POSITIVE_INFINITY;
    private double maxX = Double.NEGATIVE_INFINITY;
    private double maxY = Double.NEGATIVE_INFINITY;

    public boolean isEmpty() {
        return minX > maxX;
    }

    public double minX() {
        return minX;
    }

    public double minY() {
        return minY;
    }

    public double maxX() {
        return maxX;
    }

    public double maxY() {
        return maxY;
    }

    public void reset() {
        minX = Double.POSITIVE_INFINITY;
        minY = Double.POSITIVE_INFINITY;
        maxX = Double.NEGATIVE_INFINITY;
        maxY = Double.NEGATIVE_INFINITY;
    }

    public void add(double x, double y) {
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return;
        }
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
    }

    public void add(Features features) {
        if (features == null) {
            return;
        }
        for (Feature feature : features.getFeatures()) {
            add(feature);
        }
    }

    public void add(Feature feature) {
        if (feature instanceof Pad pad) {
            add(pad.getX(), pad.getY());
        } else if (feature instanceof Line line) {
            add(line.getXs(), line.getYs());
            add(line.getXe(), line.getYe());
        } else if (feature instanceof Arc arc) {
            addArc(arc.getXs(), arc.getYs(), arc.getXe(), arc.getYe(), arc.getXc(), arc.getYc(),
                    "Y".equalsIgnoreCase(arc.getCw()));
        } else if (feature instanceof Surface surface) {
            if (surface.getPolygons() == null) {
                return;
            }
            for (ContourPolygon polygon : surface.getPolygons()) {
                double x = polygon.getXStart();
                double y = polygon.getYStart();
                add(x, y);
                if (polygon.getPolygonParts() == null) {
                    continue;
                }
                for (ContourPolygon.PolygonPart part : polygon.getPolygonParts()) {
                    if (part.getType() == ContourPolygon.PolygonPart.Type.ARC) {
                        addArc(x, y, part.getEndX(), part.getEndY(),
                                part.getXCenter(), part.getYCenter(), part.isClockwise());
                    } else {
                        add(part.getEndX(), part.getEndY());
                    }
                    x = part.getEndX();
                    y = part.getEndY();
                }
            }
        } else if (feature instanceof Text text) {
            add(text.getX(), text.getY());
            String value = text.getText();
            if (value != null && text.getXsize() > 0 && text.getYsize() > 0) {
                double[] orient = FeaturePainter.orientation(text.getOrientDef());
                addBox(text.getX(), text.getY(), value.length() * text.getXsize(), text.getYsize(),
                        orient[0], orient[1] != 0);
            }
        } else if (feature instanceof Barcode barcode) {
            add(barcode.getX(), barcode.getY());
        }
    }

    /** A box with its lower left corner at (x, y), turned clockwise and then mirrored about it. */
    private void addBox(double x, double y, double w, double h, double rotCw, boolean mirror) {
        double a = Math.toRadians(rotCw);
        double cos = Math.cos(a);
        double sin = Math.sin(a);
        double[][] corners = {{w, 0}, {w, h}, {0, h}};
        for (double[] c : corners) {
            double rx = c[0] * cos + c[1] * sin;
            double ry = -c[0] * sin + c[1] * cos;
            add(x + (mirror ? -rx : rx), y + ry);
        }
    }

    /**
     * An arc from (xs, ys) to (xe, ye) about (xc, yc). Equal end points mean a full circle.
     */
    public void addArc(double xs, double ys, double xe, double ye, double xc, double yc,
                       boolean clockwise) {
        add(xs, ys);
        add(xe, ye);
        double r = (Math.hypot(xs - xc, ys - yc) + Math.hypot(xe - xc, ye - yc)) / 2;
        if (!(r > 0) || Double.isInfinite(r)) {
            return;
        }
        if (xs == xe && ys == ye) {
            add(xc - r, yc - r);
            add(xc + r, yc + r);
            return;
        }
        double a0 = Math.atan2(ys - yc, xs - xc);
        double a1 = Math.atan2(ye - yc, xe - xc);
        // Walk counter-clockwise from 'from' over 'sweep' radians.
        double from = clockwise ? a1 : a0;
        double sweep = clockwise ? a0 - a1 : a1 - a0;
        while (sweep <= 0) {
            sweep += 2 * Math.PI;
        }
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            double axis = quadrant * Math.PI / 2;
            double offset = axis - from;
            while (offset < 0) {
                offset += 2 * Math.PI;
            }
            while (offset >= 2 * Math.PI) {
                offset -= 2 * Math.PI;
            }
            if (offset <= sweep) {
                add(xc + r * Math.cos(axis), yc + r * Math.sin(axis));
            }
        }
    }
}

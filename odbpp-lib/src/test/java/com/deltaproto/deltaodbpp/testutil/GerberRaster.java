package com.deltaproto.deltaodbpp.testutil;

import com.deltaproto.deltagerber.model.gerber.BoundingBox;
import com.deltaproto.deltagerber.model.gerber.GerberDocument;
import com.deltaproto.deltagerber.model.gerber.Polarity;
import com.deltaproto.deltagerber.model.gerber.aperture.Aperture;
import com.deltaproto.deltagerber.model.gerber.aperture.CircleAperture;
import com.deltaproto.deltagerber.model.gerber.aperture.MacroAperture;
import com.deltaproto.deltagerber.model.gerber.aperture.ObroundAperture;
import com.deltaproto.deltagerber.model.gerber.aperture.PolygonAperture;
import com.deltaproto.deltagerber.model.gerber.aperture.RectangleAperture;
import com.deltaproto.deltagerber.model.gerber.operation.Arc;
import com.deltaproto.deltagerber.model.gerber.operation.Contour;
import com.deltaproto.deltagerber.model.gerber.operation.Draw;
import com.deltaproto.deltagerber.model.gerber.operation.Flash;
import com.deltaproto.deltagerber.model.gerber.operation.GraphicsObject;
import com.deltaproto.deltagerber.model.gerber.operation.Region;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Rasterises a parsed Gerber image (delta-gerber model) into a binary bitmap and compares two
 * such bitmaps. This is the referee used to check that a Gerber generated from ODB++ inks the
 * same area as a reference Gerber for the same design, regardless of how either file chose to
 * express it (standard aperture vs. macro, region vs. flash, arc vs. polyline, ...).
 *
 * <p>Everything is exact Java2D geometry in millimetres, Y up; arcs are flattened to a
 * 2&nbsp;µm chord tolerance, far below the pixel size used by the comparisons.
 */
public final class GerberRaster {

    /** Chord tolerance (mm) when flattening arcs. */
    private static final double ARC_TOLERANCE_MM = 0.002;

    private GerberRaster() {
    }

    /** Result of {@link #compare}. Pixel counts. */
    public record Comparison(long inkA, long inkB, long onlyA, long onlyB) {
        /** Pixels inked by either image (a lower bound; the union is at most inkA + inkB). */
        public long union() {
            return Math.max(inkA, inkB) + Math.min(onlyA, onlyB);
        }

        /** Mismatching pixels as a fraction of the inked union; 0 when both are empty. */
        public double mismatchFraction() {
            long u = union();
            return u == 0 ? 0 : (double) (onlyA + onlyB) / u;
        }
    }

    /** Window in mm (Y up) covering both documents plus a margin. */
    public static Rectangle2D window(GerberDocument a, GerberDocument b, double marginMm) {
        BoundingBox ba = a.calculateBoundingBox();
        BoundingBox bb = b.calculateBoundingBox();
        BoundingBox u = new BoundingBox();
        if (ba != null && ba.isValid()) {
            u.include(ba);
        }
        if (bb != null && bb.isValid()) {
            u.include(bb);
        }
        if (!u.isValid()) {
            return null;
        }
        return new Rectangle2D.Double(u.getMinX() - marginMm, u.getMinY() - marginMm,
                u.getWidth() + 2 * marginMm, u.getHeight() + 2 * marginMm);
    }

    /** Rasterises {@code doc} over {@code windowMm} at {@code pxPerMm}; white = dark (inked). */
    public static BufferedImage rasterise(GerberDocument doc, Rectangle2D windowMm, double pxPerMm) {
        int w = Math.max(1, (int) Math.ceil(windowMm.getWidth() * pxPerMm));
        int h = Math.max(1, (int) Math.ceil(windowMm.getHeight() * pxPerMm));
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, w, h);
            AffineTransform t = new AffineTransform();
            t.scale(pxPerMm, -pxPerMm);
            t.translate(-windowMm.getMinX(), -windowMm.getMaxY());
            g.setTransform(t);
            for (GraphicsObject obj : doc.getObjects()) {
                Shape s = shapeOf(obj);
                if (s == null) {
                    continue;
                }
                g.setColor(obj.getPolarity() == Polarity.CLEAR ? Color.BLACK : Color.WHITE);
                g.fill(s);
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    /**
     * Compares two equally sized bitmaps. A pixel inked in one image counts as a mismatch only
     * when the other image has no inked pixel within {@code tolerancePx} (Chebyshev distance),
     * so sub-pixel edge jitter between two representations of the same geometry is ignored.
     */
    public static Comparison compare(BufferedImage a, BufferedImage b, int tolerancePx) {
        int w = a.getWidth();
        int h = a.getHeight();
        if (b.getWidth() != w || b.getHeight() != h) {
            throw new IllegalArgumentException("bitmap sizes differ");
        }
        boolean[] ia = ink(a);
        boolean[] ib = ink(b);
        boolean[] da = dilate(ia, w, h, tolerancePx);
        boolean[] db = dilate(ib, w, h, tolerancePx);
        long inkA = 0;
        long inkB = 0;
        long onlyA = 0;
        long onlyB = 0;
        for (int i = 0; i < ia.length; i++) {
            if (ia[i]) {
                inkA++;
                if (!db[i]) {
                    onlyA++;
                }
            }
            if (ib[i]) {
                inkB++;
                if (!da[i]) {
                    onlyB++;
                }
            }
        }
        return new Comparison(inkA, inkB, onlyA, onlyB);
    }

    /**
     * Writes a colour diff: grey where both ink, red where only {@code a} inks, blue where only
     * {@code b} inks (beyond the tolerance band, which is drawn dark grey).
     */
    public static void writeDiff(BufferedImage a, BufferedImage b, int tolerancePx, Path out)
            throws IOException {
        int w = a.getWidth();
        int h = a.getHeight();
        boolean[] ia = ink(a);
        boolean[] ib = ink(b);
        boolean[] da = dilate(ia, w, h, tolerancePx);
        boolean[] db = dilate(ib, w, h, tolerancePx);
        BufferedImage diff = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int rgb;
                if (ia[i] && ib[i]) {
                    rgb = 0x909090;
                } else if (ia[i]) {
                    rgb = db[i] ? 0x404040 : 0xFF2020;
                } else if (ib[i]) {
                    rgb = da[i] ? 0x404040 : 0x2040FF;
                } else {
                    rgb = 0x000000;
                }
                diff.setRGB(x, y, rgb);
            }
        }
        Files.createDirectories(out.getParent());
        ImageIO.write(diff, "png", out.toFile());
    }

    // ---------------------------------------------------------------- geometry

    /** The inked footprint of one graphics object (mm, Y up), or null when it inks nothing. */
    public static Shape shapeOf(GraphicsObject obj) {
        if (obj instanceof Flash flash) {
            return flashShape(flash);
        }
        if (obj instanceof Draw draw) {
            return drawShape(draw);
        }
        if (obj instanceof Arc arc) {
            return arcShape(arc);
        }
        if (obj instanceof Region region) {
            return regionShape(region);
        }
        return null;
    }

    private static Shape flashShape(Flash flash) {
        Shape ap = apertureShape(flash.getAperture());
        if (ap == null) {
            return null;
        }
        AffineTransform at = AffineTransform.getTranslateInstance(flash.getX(), flash.getY());
        if (flash.getRotation() != 0) {
            at.rotate(Math.toRadians(flash.getRotation()));
        }
        if (flash.getScale() != 1) {
            at.scale(flash.getScale(), flash.getScale());
        }
        if (flash.isMirrorX()) {
            at.scale(-1, 1);
        }
        if (flash.isMirrorY()) {
            at.scale(1, -1);
        }
        return at.createTransformedShape(ap);
    }

    /** Aperture footprint centred on the origin. */
    public static Shape apertureShape(Aperture ap) {
        Shape outer;
        double hole = 0;
        if (ap instanceof CircleAperture c) {
            double d = c.getDiameter();
            if (d <= 0) {
                return null;
            }
            outer = new Ellipse2D.Double(-d / 2, -d / 2, d, d);
            hole = c.hasHole() ? c.getHoleDiameter() : 0;
        } else if (ap instanceof RectangleAperture r) {
            outer = new Rectangle2D.Double(-r.getWidth() / 2, -r.getHeight() / 2,
                    r.getWidth(), r.getHeight());
            hole = r.hasHole() ? r.getHoleDiameter() : 0;
        } else if (ap instanceof ObroundAperture o) {
            double m = Math.min(o.getWidth(), o.getHeight());
            outer = new RoundRectangle2D.Double(-o.getWidth() / 2, -o.getHeight() / 2,
                    o.getWidth(), o.getHeight(), m, m);
            hole = o.hasHole() ? o.getHoleDiameter() : 0;
        } else if (ap instanceof PolygonAperture p) {
            Path2D.Double path = new Path2D.Double();
            double r = p.getOuterDiameter() / 2;
            for (int i = 0; i < p.getNumVertices(); i++) {
                double a = Math.toRadians(p.getRotation()) + 2 * Math.PI * i / p.getNumVertices();
                double x = r * Math.cos(a);
                double y = r * Math.sin(a);
                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            path.closePath();
            outer = path;
            hole = p.hasHole() ? p.getHoleDiameter() : 0;
        } else if (ap instanceof MacroAperture m) {
            outer = m.getShape();
        } else {
            return null;
        }
        if (hole > 0) {
            Area area = new Area(outer);
            area.subtract(new Area(new Ellipse2D.Double(-hole / 2, -hole / 2, hole, hole)));
            return area;
        }
        return outer;
    }

    private static Shape drawShape(Draw draw) {
        double width = strokeWidth(draw.getAperture()) * draw.getStrokeScale();
        if (width <= 0) {
            return null;
        }
        return stroke(width).createStrokedShape(new Line2D.Double(
                draw.getStartX(), draw.getStartY(), draw.getEndX(), draw.getEndY()));
    }

    private static Shape arcShape(Arc arc) {
        double width = strokeWidth(arc.getAperture()) * arc.getStrokeScale();
        if (width <= 0) {
            return null;
        }
        Path2D.Double path = new Path2D.Double();
        path.moveTo(arc.getStartX(), arc.getStartY());
        appendArc(path, arc.getStartX(), arc.getStartY(), arc.getEndX(), arc.getEndY(),
                arc.getCenterX(), arc.getCenterY(), arc.isClockwise());
        return stroke(width).createStrokedShape(path);
    }

    private static Shape regionShape(Region region) {
        Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO);
        for (Contour contour : region.getContours()) {
            double cx = contour.getStartX();
            double cy = contour.getStartY();
            path.moveTo(cx, cy);
            for (Contour.ContourSegment seg : contour.getSegments()) {
                if (seg.isArc()) {
                    appendArc(path, cx, cy, seg.getX(), seg.getY(),
                            seg.getCenterX(), seg.getCenterY(), seg.isClockwise());
                } else {
                    path.lineTo(seg.getX(), seg.getY());
                }
                cx = seg.getX();
                cy = seg.getY();
            }
            path.closePath();
        }
        return path;
    }

    private static double strokeWidth(Aperture ap) {
        if (ap instanceof CircleAperture c) {
            return c.getDiameter();
        }
        if (ap instanceof RectangleAperture r) {
            return Math.min(r.getWidth(), r.getHeight());
        }
        if (ap instanceof ObroundAperture o) {
            return Math.min(o.getWidth(), o.getHeight());
        }
        if (ap instanceof PolygonAperture p) {
            return p.getOuterDiameter();
        }
        if (ap != null) {
            BoundingBox bb = ap.getBoundingBox();
            if (bb != null && bb.isValid()) {
                return Math.min(bb.getWidth(), bb.getHeight());
            }
        }
        return 0;
    }

    private static BasicStroke stroke(double width) {
        return new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    /**
     * Appends a circular arc from the path's current point (sx, sy) to (ex, ey) around
     * (cx, cy) as a polyline. Start == end is a full circle (multi-quadrant convention).
     */
    private static void appendArc(Path2D.Double path, double sx, double sy, double ex, double ey,
                                  double cx, double cy, boolean clockwise) {
        double r = (Math.hypot(sx - cx, sy - cy) + Math.hypot(ex - cx, ey - cy)) / 2;
        if (r <= 0) {
            path.lineTo(ex, ey);
            return;
        }
        double a0 = Math.atan2(sy - cy, sx - cx);
        double a1 = Math.atan2(ey - cy, ex - cx);
        double sweep;
        boolean fullCircle = Math.abs(sx - ex) < 1e-9 && Math.abs(sy - ey) < 1e-9;
        if (fullCircle) {
            sweep = clockwise ? -2 * Math.PI : 2 * Math.PI;
        } else if (clockwise) {
            sweep = a1 - a0;
            while (sweep >= 0) {
                sweep -= 2 * Math.PI;
            }
        } else {
            sweep = a1 - a0;
            while (sweep <= 0) {
                sweep += 2 * Math.PI;
            }
        }
        double maxStep = 2 * Math.acos(Math.max(0, 1 - ARC_TOLERANCE_MM / r));
        int n = Math.max(4, (int) Math.ceil(Math.abs(sweep) / Math.max(maxStep, 1e-4)));
        for (int i = 1; i < n; i++) {
            double a = a0 + sweep * i / n;
            path.lineTo(cx + r * Math.cos(a), cy + r * Math.sin(a));
        }
        path.lineTo(ex, ey);
    }

    // ---------------------------------------------------------------- bitmaps

    private static boolean[] ink(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        boolean[] out = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[y * w + x] = (img.getRaster().getSample(x, y, 0) & 0xFF) > 127;
            }
        }
        return out;
    }

    /** Separable Chebyshev dilation by {@code r} pixels. */
    private static boolean[] dilate(boolean[] in, int w, int h, int r) {
        if (r <= 0) {
            return in;
        }
        boolean[] tmp = new boolean[in.length];
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                boolean v = false;
                for (int k = Math.max(0, x - r); k <= Math.min(w - 1, x + r) && !v; k++) {
                    v = in[row + k];
                }
                tmp[row + x] = v;
            }
        }
        boolean[] out = new boolean[in.length];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                boolean v = false;
                for (int k = Math.max(0, y - r); k <= Math.min(h - 1, y + r) && !v; k++) {
                    v = tmp[k * w + x];
                }
                out[y * w + x] = v;
            }
        }
        return out;
    }
}

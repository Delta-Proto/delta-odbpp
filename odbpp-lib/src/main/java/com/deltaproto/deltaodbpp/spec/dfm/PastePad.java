package com.deltaproto.deltaodbpp.spec.dfm;

/**
 * One solder-paste opening as a point-in-shape test plus a rotation-invariant bounding box for the
 * spatial grid. Built by the analyzer from a paste-layer {@code Pad} feature whose symbol has been
 * resolved to a shape and size in millimetres.
 *
 * <p>Where the shape is known (round, rectangle/square, oval) containment is exact, honouring the
 * pad rotation; otherwise the pad falls back to its bounding box — which can over-count a concave
 * footprint but never misses a hole that is truly inside, matching delta-gerber.
 */
public final class PastePad {

    /** The footprint families the analyzer can test exactly; anything else uses the bounding box. */
    public enum Shape { ROUND, RECT, OVAL, BOX }

    private final Shape shape;
    private final double cx;
    private final double cy;
    private final double halfW; // mm, along the pad's local X before rotation
    private final double halfH; // mm, along the pad's local Y before rotation
    private final double rotationDeg;
    private final boolean top;
    private final boolean bottom;

    private final double minX;
    private final double minY;
    private final double maxX;
    private final double maxY;

    /**
     * @param shape       footprint family (BOX for an unresolved/other shape)
     * @param cx          pad centre X in mm
     * @param cy          pad centre Y in mm
     * @param widthMm     pad width (diameter for ROUND) in mm
     * @param heightMm    pad height in mm; for ROUND/BOX square, pass the same as width
     * @param rotationDeg pad rotation in degrees, clockwise
     */
    public PastePad(Shape shape, double cx, double cy, double widthMm, double heightMm,
                    double rotationDeg, boolean top, boolean bottom) {
        this.shape = shape;
        this.cx = cx;
        this.cy = cy;
        this.halfW = Math.abs(widthMm) / 2.0;
        this.halfH = Math.abs(heightMm) / 2.0;
        this.rotationDeg = rotationDeg;
        this.top = top;
        this.bottom = bottom;
        // Circumscribe with a circle of the pad's half-diagonal so the box holds it at any rotation.
        double halfDiag = Math.hypot(halfW, halfH);
        this.minX = cx - halfDiag;
        this.maxX = cx + halfDiag;
        this.minY = cy - halfDiag;
        this.maxY = cy + halfDiag;
    }

    boolean top() {
        return top;
    }

    boolean bottom() {
        return bottom;
    }

    double minX() {
        return minX;
    }

    double minY() {
        return minY;
    }

    double maxX() {
        return maxX;
    }

    double maxY() {
        return maxY;
    }

    /** Whether {@code (x, y)} in mm lies inside this pad. */
    boolean contains(double x, double y) {
        if (x < minX || x > maxX || y < minY || y > maxY) {
            return false;
        }
        // Map the point into the pad's local, un-rotated frame centred on the pad.
        double lx = x - cx;
        double ly = y - cy;
        if (rotationDeg != 0) {
            double rad = -Math.toRadians(rotationDeg);
            double c = Math.cos(rad);
            double s = Math.sin(rad);
            double rx = lx * c - ly * s;
            ly = lx * s + ly * c;
            lx = rx;
        }
        return switch (shape) {
            case ROUND -> {
                double r = halfW;
                yield lx * lx + ly * ly <= r * r;
            }
            case RECT, BOX -> Math.abs(lx) <= halfW && Math.abs(ly) <= halfH;
            case OVAL -> {
                double r = Math.min(halfW, halfH);
                double dx = Math.max(Math.abs(lx) - (halfW - r), 0);
                double dy = Math.max(Math.abs(ly) - (halfH - r), 0);
                yield dx * dx + dy * dy <= r * r;
            }
        };
    }
}

package com.deltaproto.deltaodbpp.spec;

/**
 * A mutable axis-aligned bounding box in millimetres — the model is mm-normalised at parse time, so
 * every coordinate fed in here is already mm.
 *
 * <p>The ODB++ model carries no bounding-box type of its own (unlike delta-gerber's
 * {@code BoundingBox}), so the spec package keeps this small one. A box that never had a point
 * included is {@linkplain #isValid() invalid} and reports zero size.
 */
public final class Bounds {

    private double minX = Double.POSITIVE_INFINITY;
    private double minY = Double.POSITIVE_INFINITY;
    private double maxX = Double.NEGATIVE_INFINITY;
    private double maxY = Double.NEGATIVE_INFINITY;

    public Bounds() {
    }

    public Bounds(double minX, double minY, double maxX, double maxY) {
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
    }

    /** Grow the box to include a point. */
    public void include(double x, double y) {
        if (x < minX) minX = x;
        if (y < minY) minY = y;
        if (x > maxX) maxX = x;
        if (y > maxY) maxY = y;
    }

    /** Grow the box to include another box; a null or invalid box is ignored. */
    public void include(Bounds other) {
        if (other == null || !other.isValid()) {
            return;
        }
        include(other.minX, other.minY);
        include(other.maxX, other.maxY);
    }

    /** True once at least one point has been included (so min/max are ordered and finite). */
    public boolean isValid() {
        return maxX >= minX && maxY >= minY
                && Double.isFinite(minX) && Double.isFinite(minY)
                && Double.isFinite(maxX) && Double.isFinite(maxY);
    }

    public double getMinX() {
        return minX;
    }

    public double getMinY() {
        return minY;
    }

    public double getMaxX() {
        return maxX;
    }

    public double getMaxY() {
        return maxY;
    }

    /** Width in mm, or 0 when the box holds no points. */
    public double getWidth() {
        return isValid() ? maxX - minX : 0.0;
    }

    /** Height in mm, or 0 when the box holds no points. */
    public double getHeight() {
        return isValid() ? maxY - minY : 0.0;
    }

    @Override
    public String toString() {
        return String.format("Bounds[%.3f,%.3f %.3f,%.3f]", minX, minY, maxX, maxY);
    }
}

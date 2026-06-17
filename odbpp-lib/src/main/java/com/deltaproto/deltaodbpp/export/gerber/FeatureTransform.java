package com.deltaproto.deltaodbpp.export.gerber;

/**
 * Rigid transform applied when flattening user-defined symbols into a layer:
 * local symbol coordinates are rotated (clockwise, per ODB++ convention),
 * optionally mirrored along the x axis ("first rotated, then mirrored",
 * spec p.35), then translated to the pad position. Transforms compose for
 * nested symbols.
 */
final class FeatureTransform {

    static final FeatureTransform IDENTITY = new FeatureTransform(0, 0, 0, false);

    final double dx;
    final double dy;
    final double rotationCwDeg;
    final boolean mirrored;

    private FeatureTransform(double dx, double dy, double rotationCwDeg, boolean mirrored) {
        this.dx = dx;
        this.dy = dy;
        this.rotationCwDeg = rotationCwDeg;
        this.mirrored = mirrored;
    }

    /** The transform placing a symbol at (x, y) with the given orientation. */
    static FeatureTransform forPlacement(double x, double y, double rotationCwDeg, boolean mirrored) {
        return new FeatureTransform(x, y, rotationCwDeg, mirrored);
    }

    /** Applies this transform to a local point; result is {x, y}. */
    double[] apply(double x, double y) {
        double rad = Math.toRadians(-rotationCwDeg); // CW rotation = negative CCW angle
        double rx = x * Math.cos(rad) - y * Math.sin(rad);
        double ry = x * Math.sin(rad) + y * Math.cos(rad);
        if (mirrored) {
            rx = -rx;
        }
        return new double[] {rx + dx, ry + dy};
    }

    /** Composes an inner placement (within this transformed frame). */
    FeatureTransform compose(double x, double y, double rotationCwDeg, boolean mirrored) {
        double[] origin = apply(x, y);
        boolean newMirror = this.mirrored ^ mirrored;
        double newRotation = this.mirrored
                ? this.rotationCwDeg - rotationCwDeg
                : this.rotationCwDeg + rotationCwDeg;
        return new FeatureTransform(origin[0], origin[1], newRotation, newMirror);
    }

    /** Arc sweep direction flips under mirroring. */
    boolean transformClockwise(boolean clockwise) {
        return mirrored != clockwise;
    }

    boolean isIdentity() {
        return dx == 0 && dy == 0 && rotationCwDeg == 0 && !mirrored;
    }
}

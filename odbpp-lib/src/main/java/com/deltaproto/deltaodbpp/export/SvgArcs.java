package com.deltaproto.deltaodbpp.export;

import java.util.Locale;

/**
 * Builds SVG path {@code A} commands for ODB++ circular arcs — surface contour arcs ({@code OC})
 * and arc features ({@code A}).
 *
 * <p>Two ODB++ conventions do not map one-to-one onto a single SVG elliptical-arc command:
 * <ul>
 *   <li><b>Full circles.</b> In ODB++ an arc whose start and end points coincide is a 360° circle.
 *       SVG treats an arc with identical endpoints as omitted, so it would draw nothing (a plane
 *       clearance written as one full-circle {@code OC} would vanish). A full circle is therefore
 *       emitted as two semicircles via the point diametrically opposite the start.</li>
 *   <li><b>Large-arc flag.</b> SVG needs to be told whether to take the long way round; it is
 *       derived from the swept angle in the direction of travel.</li>
 * </ul>
 *
 * <p>All coordinates are expected already converted to the output unit (the conversion is a plain
 * linear scale, so the geometry is unchanged), in the ODB++ model frame (Y up). The viewport flips
 * Y, so a clockwise model arc is written with sweep flag 0 and a counter-clockwise one with 1.
 */
public final class SvgArcs {

    /**
     * Start/end points closer than this (output units) are an ODB++ full circle; a radius at or
     * below it is degenerate. Far below the 4-decimal precision the path data is written with.
     */
    static final double EPSILON = 1e-6;

    private SvgArcs() {
    }

    /**
     * SVG path data for an arc from the current point {@code (sx, sy)} to {@code (ex, ey)} about
     * {@code (cx, cy)}, as one or two {@code " A ..."} commands (each with a leading space). The
     * caller must already have moved/drawn to {@code (sx, sy)}.
     *
     * @param clockwise the arc direction in the ODB++ model frame (Y up)
     */
    public static String arcTo(double sx, double sy, double ex, double ey,
                               double cx, double cy, boolean clockwise) {
        double r = Math.hypot(sx - cx, sy - cy);
        if (r <= EPSILON) {
            // Degenerate: the centre sits on the start point, so there is no circle to follow.
            // SVG itself treats a zero-radius arc as a straight line to the end point, so an L is
            // what a conforming renderer would draw anyway; writing it explicitly keeps the
            // current point at (ex, ey) for the following commands and, when start == end, leaves
            // a zero-length segment that adds no fill area (a stroked one shows a round-capped
            // dot, as a zero-length line feature does).
            return String.format(Locale.US, " L %.4f %.4f", ex, ey);
        }

        int sweep = clockwise ? 0 : 1;

        if (Math.hypot(ex - sx, ey - sy) <= EPSILON) {
            // Full circle: two semicircles via the opposite point. Each spans exactly 180°, so
            // the large-arc flag is irrelevant; 0 is used.
            double mx = 2 * cx - sx;
            double my = 2 * cy - sy;
            return arcCommand(r, 0, sweep, mx, my) + arcCommand(r, 0, sweep, ex, ey);
        }

        double a0 = Math.atan2(sy - cy, sx - cx);
        double a1 = Math.atan2(ey - cy, ex - cx);
        // Swept angle in the direction of travel (model frame, Y up), normalised to [0, 2π).
        double swept = clockwise ? a0 - a1 : a1 - a0;
        swept = ((swept % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
        int largeArc = swept > Math.PI ? 1 : 0;
        return arcCommand(r, largeArc, sweep, ex, ey);
    }

    private static String arcCommand(double r, int largeArc, int sweep, double x, double y) {
        return String.format(Locale.US, " A %.4f %.4f 0 %d %d %.4f %.4f", r, r, largeArc, sweep, x, y);
    }
}

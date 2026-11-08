package com.deltaproto.deltaodbpp.export;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SvgArcs}: full circles are split in two, the large-arc flag follows the
 * swept angle, and the sweep flag follows the ODB++ direction (model frame, Y up).
 *
 * <p>Besides checking the flags literally, each arc is decoded back to its centre with the SVG
 * endpoint-to-centre conversion (SVG 1.1 F.6.5) to confirm the emitted command really describes
 * the ODB++ arc rather than its mirror image about the chord.
 */
class SvgArcsTest {

    private static final Pattern ARC = Pattern.compile(
            " A (-?\\d+\\.\\d{4}) (-?\\d+\\.\\d{4}) 0 ([01]) ([01]) (-?\\d+\\.\\d{4}) (-?\\d+\\.\\d{4})");

    /** One parsed {@code A} command. */
    private record ArcCmd(double r, int largeArc, int sweep, double x, double y) {
    }

    private static List<ArcCmd> parse(String d) {
        List<ArcCmd> out = new ArrayList<>();
        Matcher m = ARC.matcher(d);
        int end = 0;
        while (m.find()) {
            assertEquals(end, m.start(), "unexpected text between arc commands in: '" + d + "'");
            assertEquals(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)), 0.0,
                    "rx must equal ry");
            out.add(new ArcCmd(Double.parseDouble(m.group(1)), Integer.parseInt(m.group(3)),
                    Integer.parseInt(m.group(4)), Double.parseDouble(m.group(5)),
                    Double.parseDouble(m.group(6))));
            end = m.end();
        }
        assertEquals(d.length(), end, "trailing text after arc commands in: '" + d + "'");
        return out;
    }

    /**
     * Centre of the SVG arc from (x1, y1) with the given command, in the path's own coordinate
     * system (here the ODB++ model frame), per the SVG endpoint-to-centre conversion.
     */
    private static double[] svgCentre(double x1, double y1, ArcCmd a) {
        double hx = (x1 - a.x()) / 2;
        double hy = (y1 - a.y()) / 2;
        double h2 = hx * hx + hy * hy;
        double rad = Math.max(0, (a.r() * a.r() - h2) / h2);
        double coef = (a.largeArc() != a.sweep() ? 1 : -1) * Math.sqrt(rad);
        return new double[]{coef * hy + (x1 + a.x()) / 2, -coef * hx + (y1 + a.y()) / 2};
    }

    private static void assertCentre(double x1, double y1, ArcCmd a, double cx, double cy) {
        double[] c = svgCentre(x1, y1, a);
        assertEquals(cx, c[0], 1e-3, "decoded centre X");
        assertEquals(cy, c[1], 1e-3, "decoded centre Y");
    }

    // ---- (a) full circle ----

    @Test
    void fullCircle_isTwoSemicirclesViaOppositePoint() {
        // Start = end = (7, 5) about (5, 5): the ODB++ "OC 7 5 5 5 Y" full circle.
        String d = SvgArcs.arcTo(7, 5, 7, 5, 5, 5, true);
        List<ArcCmd> arcs = parse(d);
        assertEquals(2, arcs.size(), "full circle must be two arcs: " + d);

        ArcCmd first = arcs.get(0);
        ArcCmd second = arcs.get(1);
        assertEquals(2.0, first.r(), 1e-9);
        assertEquals(2.0, second.r(), 1e-9);
        // First half ends on the point diametrically opposite the start.
        assertEquals(3.0, first.x(), 1e-9);
        assertEquals(5.0, first.y(), 1e-9);
        // Second half returns to the start/end point.
        assertEquals(7.0, second.x(), 1e-9);
        assertEquals(5.0, second.y(), 1e-9);
        assertEquals(0, first.largeArc());
        assertEquals(0, second.largeArc());
        assertEquals(0, first.sweep(), "clockwise -> sweep 0");
        assertEquals(0, second.sweep(), "both halves share the sweep flag");
    }

    @Test
    void fullCircle_counterClockwise_usesSweepOne() {
        List<ArcCmd> arcs = parse(SvgArcs.arcTo(0, 3, 0, 3, 0, 0, false));
        assertEquals(2, arcs.size());
        assertEquals(0.0, arcs.get(0).x(), 1e-9);
        assertEquals(-3.0, arcs.get(0).y(), 1e-9);
        assertEquals(1, arcs.get(0).sweep());
        assertEquals(1, arcs.get(1).sweep());
    }

    @Test
    void nearlyCoincidentEndpoints_areAFullCircle() {
        // Within the 1e-6 tolerance -> still a full circle, not an omitted zero-length arc.
        List<ArcCmd> arcs = parse(SvgArcs.arcTo(1, 0, 1 + 5e-7, 0, 0, 0, false));
        assertEquals(2, arcs.size());
        assertEquals(-1.0, arcs.get(0).x(), 1e-9);
    }

    // ---- (b) 90° arcs ----

    @Test
    void quarterArc_counterClockwise_isSmallArc() {
        // (1,0) -> (0,1) CCW about the origin: 90°.
        ArcCmd a = single(SvgArcs.arcTo(1, 0, 0, 1, 0, 0, false));
        assertEquals(1.0, a.r(), 1e-9);
        assertEquals(0, a.largeArc());
        assertEquals(1, a.sweep());
        assertCentre(1, 0, a, 0, 0);
    }

    @Test
    void quarterArc_clockwise_isSmallArc() {
        // (0,1) -> (1,0) CW about the origin: 90°.
        ArcCmd a = single(SvgArcs.arcTo(0, 1, 1, 0, 0, 0, true));
        assertEquals(0, a.largeArc());
        assertEquals(0, a.sweep());
        assertCentre(0, 1, a, 0, 0);
    }

    // ---- (c) 270° arcs ----

    @Test
    void threeQuarterArc_counterClockwise_isLargeArc() {
        // (1,0) -> (0,-1) CCW about the origin: 270°.
        ArcCmd a = single(SvgArcs.arcTo(1, 0, 0, -1, 0, 0, false));
        assertEquals(1, a.largeArc());
        assertEquals(1, a.sweep());
        assertCentre(1, 0, a, 0, 0);
    }

    @Test
    void threeQuarterArc_clockwise_isLargeArc() {
        // (1,0) -> (0,1) CW about the origin: 270°.
        ArcCmd a = single(SvgArcs.arcTo(1, 0, 0, 1, 0, 0, true));
        assertEquals(1, a.largeArc());
        assertEquals(0, a.sweep());
        assertCentre(1, 0, a, 0, 0);
    }

    @Test
    void threeQuarterArc_offOrigin_decodesToItsCentre() {
        // Same 270° CW shape translated to centre (10, -4), radius 2.5.
        ArcCmd a = single(SvgArcs.arcTo(12.5, -4, 10, -1.5, 10, -4, true));
        assertEquals(2.5, a.r(), 1e-9);
        assertEquals(1, a.largeArc());
        assertCentre(12.5, -4, a, 10, -4);
    }

    // ---- (d) sweep direction ----

    @Test
    void sweepFlag_followsOdbDirection() {
        // SVG sweep 1 = increasing angle in the path's coordinate system, which is the Y-up
        // model frame (the viewport flip is applied outside the path), i.e. counter-clockwise.
        assertEquals(0, single(SvgArcs.arcTo(1, 0, -1, 0.0001, 0, 0, true)).sweep(), "CW -> 0");
        assertEquals(1, single(SvgArcs.arcTo(1, 0, -1, 0.0001, 0, 0, false)).sweep(), "CCW -> 1");
    }

    @Test
    void sameEndpoints_oppositeDirections_decodeToSameCentre() {
        // (1,0) -> (0,1): CCW is the 90° arc, CW the 270° one. Both must sit on the same centre;
        // with a fixed large-arc flag one of them would land on the mirrored centre (1, 1).
        ArcCmd ccw = single(SvgArcs.arcTo(1, 0, 0, 1, 0, 0, false));
        ArcCmd cw = single(SvgArcs.arcTo(1, 0, 0, 1, 0, 0, true));
        assertCentre(1, 0, ccw, 0, 0);
        assertCentre(1, 0, cw, 0, 0);
    }

    // ---- degenerate / format ----

    @Test
    void zeroRadius_isStraightLineToEnd() {
        assertEquals(" L 3.0000 4.0000", SvgArcs.arcTo(1, 2, 3, 4, 1, 2, false));
    }

    @Test
    void outputFormat_matchesExistingPathData() {
        assertEquals(" A 1.0000 1.0000 0 0 1 0.0000 1.0000", SvgArcs.arcTo(1, 0, 0, 1, 0, 0, false));
        assertTrue(SvgArcs.arcTo(1.23456, 0, -1.23456, 0, 0, 0, true).startsWith(" A 1.2346 1.2346 "));
    }

    private static ArcCmd single(String d) {
        List<ArcCmd> arcs = parse(d);
        assertEquals(1, arcs.size(), "expected one arc command: " + d);
        return arcs.get(0);
    }
}

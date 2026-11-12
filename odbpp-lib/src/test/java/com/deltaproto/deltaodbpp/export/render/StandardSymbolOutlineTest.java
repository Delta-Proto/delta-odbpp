package com.deltaproto.deltaodbpp.export.render;

import org.junit.jupiter.api.Test;

import java.awt.Shape;
import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The geometry of standard symbols, probed point by point. Names are in microns unless a
 * test says otherwise; coordinates are millimetres, Y up.
 */
class StandardSymbolOutlineTest {

    private static final double MICRON = 0.001;
    private static final double MIL = 0.0254;

    private static Shape outline(String name) {
        Shape shape = StandardSymbolOutline.of(name, MICRON);
        assertNotNull(shape, name + " is a standard symbol");
        return shape;
    }

    private static void assertBox(Shape shape, double width, double height) {
        Rectangle2D box = shape.getBounds2D();
        assertEquals(width, box.getWidth(), 1e-6, "width");
        assertEquals(height, box.getHeight(), 1e-6, "height");
        assertEquals(0, box.getCenterX(), 1e-6, "centred in x");
        assertEquals(0, box.getCenterY(), 1e-6, "centred in y");
    }

    @Test
    void dimensionsFollowTheUnitOfTheName() {
        assertBox(StandardSymbolOutline.of("donut_s100x60", MIL), 2.54, 2.54);
        assertBox(StandardSymbolOutline.of("donut_s100x60", MICRON), 0.1, 0.1);
    }

    @Test
    void namesThatAreNotStandardSymbolsGiveNothing() {
        assertNull(StandardSymbolOutline.of("my_logo", MICRON));
        assertNull(StandardSymbolOutline.of("special+1", MICRON));
        assertNull(StandardSymbolOutline.of("donut_rc1000", MICRON), "too few parameters");
        assertNull(StandardSymbolOutline.of("", MICRON));
        assertNull(StandardSymbolOutline.of(null, MICRON));
    }

    @Test
    void nullSymbolDrawsNothing() {
        Shape shape = outline("null1");
        assertTrue(shape.getBounds2D().isEmpty());
    }

    @Test
    void roundedRectangleDonutIsARing() {
        Shape ring = outline("donut_rc3000x2000x200");
        assertBox(ring, 3, 2);
        assertFalse(ring.contains(0, 0), "open in the middle");
        assertTrue(ring.contains(1.4, 0), "right bar");
        assertTrue(ring.contains(0, 0.9), "top bar");
        assertFalse(ring.contains(1.2, 0), "just inside the right bar");
        assertTrue(ring.contains(1.45, 0.95), "square corner");
    }

    @Test
    void roundedRectangleDonutRoundsOnlyTheListedCorners() {
        // Corners count counter-clockwise from the top right.
        Shape ring = outline("donut_rc3000x2000x200xr400x12");
        assertBox(ring, 3, 2);
        assertFalse(ring.contains(1.47, 0.97), "corner 1, top right, is rounded");
        assertFalse(ring.contains(-1.47, 0.97), "corner 2, top left, is rounded");
        assertTrue(ring.contains(-1.47, -0.97), "corner 3, bottom left, stays square");
        assertTrue(ring.contains(1.47, -0.97), "corner 4, bottom right, stays square");
        assertFalse(ring.contains(0, 0));
    }

    @Test
    void ovalDonutIsAStadiumRing() {
        Shape ring = outline("donut_o3000x1000x200");
        assertBox(ring, 3, 1);
        assertFalse(ring.contains(0, 0));
        assertTrue(ring.contains(1.4, 0), "end cap");
        assertTrue(ring.contains(0, 0.4), "straight side");
        assertFalse(ring.contains(1.45, 0.45), "the ends are round");
    }

    @Test
    void squareDonutWithRoundHole() {
        Shape ring = outline("donut_sr2000x1000");
        assertBox(ring, 2, 2);
        assertFalse(ring.contains(0.45, 0));
        assertTrue(ring.contains(0.45, 0.45), "outside the round hole, inside the square");
        assertTrue(ring.contains(0.95, 0.95));
    }

    @Test
    void triangleStandsOnItsBase() {
        Shape tri = outline("tri2000x1000");
        assertBox(tri, 2, 1);
        assertTrue(tri.contains(0, 0.4), "apex at the top");
        assertFalse(tri.contains(0.8, 0.4));
        assertTrue(tri.contains(0.8, -0.45), "base at the bottom");
    }

    @Test
    void butterfliesFillTwoOppositeQuadrants() {
        Shape round = outline("bfr2000");
        assertTrue(round.contains(-0.5, 0.5), "upper left");
        assertTrue(round.contains(0.5, -0.5), "lower right");
        assertFalse(round.contains(0.5, 0.5));
        assertFalse(round.contains(-0.5, -0.5));
        assertFalse(round.contains(-0.9, 0.9), "the round butterfly stays within its circle");

        Shape square = outline("bfs2000");
        assertTrue(square.contains(-0.9, 0.9));
        assertTrue(square.contains(0.9, -0.9));
        assertFalse(square.contains(0.9, 0.9));
    }

    @Test
    void halfOvalIsFlatOnTheLeft() {
        Shape half = outline("oval_h2000x1000");
        assertBox(half, 2, 1);
        assertTrue(half.contains(-0.97, 0.47), "square corner on the left");
        assertFalse(half.contains(0.97, 0.47), "round on the right");
    }

    @Test
    void hexagonsPointAlongTheirNamedAxis() {
        Shape horizontal = outline("hex_l2000x1000x300");
        assertTrue(horizontal.contains(0.95, 0), "vertex on the x axis");
        assertFalse(horizontal.contains(0.95, 0.4));
        assertTrue(horizontal.contains(0.6, 0.45), "flat top");

        Shape vertical = outline("hex_s1000x2000x300");
        assertTrue(vertical.contains(0, 0.95), "vertex on the y axis");
        assertFalse(vertical.contains(0.4, 0.95));
    }

    @Test
    void roundThermalHasItsGapsAtTheSpokeAngle() {
        Shape thermal = outline("ths2000x1400x45x4x200");
        double mid = 0.85;
        assertTrue(thermal.contains(mid, 0), "copper on the axes");
        assertTrue(thermal.contains(0, mid));
        double d = mid / Math.sqrt(2);
        assertFalse(thermal.contains(d, d), "gap on the diagonal");
        assertFalse(thermal.contains(-d, -d));
        assertFalse(thermal.contains(0, 0));
    }

    @Test
    void threeSpokeThermalStartsAtItsAngle() {
        Shape thermal = outline("thr2000x1400x90x3x200");
        assertFalse(thermal.contains(0, 0.85), "first gap points up");
        assertTrue(thermal.contains(0, -0.85), "no gap opposite an odd spoke");
        assertFalse(thermal.contains(0.85 * Math.cos(Math.toRadians(210)),
                0.85 * Math.sin(Math.toRadians(210))));
    }

    @Test
    void squareThermalStaysSquareAndIsCutThroughItsCorners() {
        Shape thermal = outline("s_ths2000x1400x45x4x200");
        assertBox(thermal, 2, 2);
        assertTrue(thermal.contains(0.85, 0), "bars on the axes, not turned with the spokes");
        assertTrue(thermal.contains(0, -0.85));
        assertFalse(thermal.contains(0.85, 0.85), "gap through the corner");
        assertTrue(thermal.contains(0.95, 0.6), "the bar runs on towards the corner");
        assertFalse(thermal.contains(0, 0));
    }

    @Test
    void squareThermalWithOpenCornersLosesThemEntirely() {
        Shape thermal = outline("s_tho2000x1400x45x4x200");
        assertTrue(thermal.contains(0.85, 0));
        assertFalse(thermal.contains(0.95, 0.6), "the bar ends short of the corner");
        assertFalse(thermal.contains(0.85, 0.85));
    }

    @Test
    void squareThermalWithStraightSpokesCutsTheBars() {
        Shape thermal = outline("s_ths2000x1400x0x4x200");
        assertFalse(thermal.contains(0.85, 0), "gap in the middle of each bar");
        assertFalse(thermal.contains(0, 0.85));
        assertTrue(thermal.contains(0.85, 0.85), "corners stay");
    }

    @Test
    void rectangularThermalIsNotARoundOne() {
        Shape thermal = outline("rc_ths3000x2000x45x4x200x150");
        assertBox(thermal, 3, 2);
        assertTrue(thermal.contains(1.42, 0), "right bar");
        assertTrue(thermal.contains(0, 0.92), "top bar");
        assertFalse(thermal.contains(1.42, 0.92), "gap at the corner");
        assertFalse(thermal.contains(0, 0));
        assertFalse(thermal.contains(1.2, 0), "the ring is as wide as the air gap parameter");
    }

    @Test
    void ovalThermalFollowsTheStadium() {
        Shape thermal = outline("o_ths3000x1000x0x2x200x150");
        // The gaps take the tips of the stadium away, so it ends a little short of 3 mm.
        Rectangle2D box = thermal.getBounds2D();
        assertEquals(1, box.getHeight(), 1e-6);
        assertEquals(3, box.getWidth(), 0.05);
        assertTrue(box.getWidth() < 3);
        assertEquals(0, box.getCenterX(), 1e-6);
        assertFalse(thermal.contains(1.42, 0), "gaps on the x axis");
        assertFalse(thermal.contains(-1.42, 0));
        assertTrue(thermal.contains(0, 0.42), "straight sides intact");
        assertTrue(outline("oblong_ths3000x1000x0x2x200x150xr").contains(0, -0.42));
    }

    @Test
    void homePlatesPointRight() {
        Shape plate = outline("hplate2000x1000x400");
        assertBox(plate, 2, 1);
        assertTrue(plate.contains(0.95, 0), "the tip");
        assertFalse(plate.contains(0.95, 0.4), "cut corners");
        assertTrue(plate.contains(-0.95, 0.45), "square at the back");

        Shape inverted = outline("rhplate2000x1000x400");
        assertFalse(inverted.contains(0.9, 0), "a notch where the tip was");
        assertTrue(inverted.contains(0.9, 0.45));

        Shape flat = outline("fhplate2000x1000x200x400");
        assertTrue(flat.contains(0.95, 0.2), "flat nose");
        assertFalse(flat.contains(0.95, 0.45));
    }

    @Test
    void crossAndDogbone() {
        // 2 x 1.5 mm, bars 0.3 and 0.2 thick, crossing 0.5 from the left and 0.75 from the bottom.
        Shape cross = outline("cross2000x1500x300x200x500x750xs");
        assertBox(cross, 2, 1.5);
        assertTrue(cross.contains(0.9, 0), "horizontal bar");
        assertTrue(cross.contains(-0.5, 0.7), "vertical bar");
        assertFalse(cross.contains(0.5, 0.5));

        Shape bone = outline("dogbone2000x1500x300x200x1000xs");
        assertBox(bone, 2, 1.5);
        assertTrue(bone.contains(0.9, 0.65), "top bar");
        assertTrue(bone.contains(-0.9, -0.65), "bottom bar");
        assertTrue(bone.contains(0, 0), "neck");
        assertFalse(bone.contains(0.5, 0));
    }

    @Test
    void dpackIsAGridOfPads() {
        // 4 columns by 2 rows with 0.2 mm gaps.
        Shape pack = outline("dpack2000x1000x200x200x4x2");
        assertBox(pack, 2, 1);
        assertTrue(pack.contains(-0.8, -0.3), "first pad");
        assertTrue(pack.contains(0.8, 0.3), "last pad");
        assertFalse(pack.contains(0, 0), "gap between the rows and the middle columns");
        assertFalse(pack.contains(-0.5, 0.3), "gap between two columns");
    }

    @Test
    void holeIgnoresItsPlatingAndTolerances() {
        Shape hole = outline("hole800xpx50x50");
        assertBox(hole, 0.8, 0.8);
        assertTrue(hole.contains(0, 0));
    }

    @Test
    void moireHasRingsAndCrossHairs() {
        Shape moire = outline("moire100x100x3x50x1500x0");
        assertTrue(moire.contains(0.1, 0.11), "first ring");
        assertFalse(moire.contains(0.18, 0.18), "gap between the first and the second ring");
        assertTrue(moire.contains(0.7, 0), "cross hair beyond the rings");
        assertFalse(moire.contains(0.7, 0.2));
    }
}

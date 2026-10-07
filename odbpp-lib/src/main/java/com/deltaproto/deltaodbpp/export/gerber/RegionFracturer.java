package com.deltaproto.deltaodbpp.export.gerber;

import com.deltaproto.deltaodbpp.model.ContourPolygon;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns an ODB++ surface polygon (one island plus its holes) into a single
 * Gerber region contour by <em>fracturing</em>: every hole is joined to the
 * enclosing contour through a zero-width cut-in, traversed in the opposite
 * winding, so the region encloses the island minus its holes.
 *
 * <p>This is what every EDA Gerber plotter does and what CAM tools expect.
 * The alternative — painting holes as clear-polarity regions after the
 * island — erases whatever was drawn underneath earlier (traces running
 * through a plane's clearance, for instance), which is wrong.
 *
 * <p>The cut-in for a hole runs between the closest pair of boundary points
 * of the hole and the contour built so far (vertex-to-edge for straight
 * edges, vertex-to-vertex for arcs). Any boundary edge the cut-in crossed
 * would contain a closer pair, so cut-ins cannot cross edges; they may touch,
 * which Gerber tolerates. Arc segments are preserved as arcs.
 */
final class RegionFracturer {

    /** A closed ring: vertex k is joined to vertex k+1 (mod n) by edge k. */
    static final class Ring {
        final List<double[]> vertices = new ArrayList<>();   // {x, y}
        final List<double[]> edges = new ArrayList<>();      // null = straight, else {cx, cy, cw?1:0}

        int size() {
            return vertices.size();
        }

        void add(double x, double y, double[] edgeToNext) {
            vertices.add(new double[] {x, y});
            edges.add(edgeToNext);
        }
    }

    private RegionFracturer() {
    }

    /** Builds a ring from a contour, applying the transform to points and centres. */
    static Ring ring(ContourPolygon polygon, FeatureTransform transform) {
        Ring ring = new Ring();
        double[] start = transform.apply(polygon.getXStart(), polygon.getYStart());
        List<double[]> pts = new ArrayList<>();
        List<double[]> arcs = new ArrayList<>();   // arc info of the edge arriving at pts[i]
        pts.add(start);
        arcs.add(null);
        for (ContourPolygon.PolygonPart part : polygon.getPolygonParts()) {
            double[] end = transform.apply(part.getEndX(), part.getEndY());
            double[] arc = null;
            if (part.getType() == ContourPolygon.PolygonPart.Type.ARC) {
                double[] c = transform.apply(part.getXCenter(), part.getYCenter());
                arc = new double[] {c[0], c[1], transform.transformClockwise(part.isClockwise()) ? 1 : 0};
            }
            pts.add(end);
            arcs.add(arc);
        }
        // The last part normally returns to the start: fold it into the closing edge.
        int n = pts.size();
        double[] closing = null;
        if (n > 1 && same(pts.get(n - 1), pts.get(0))) {
            closing = arcs.get(n - 1);
            n--;
        }
        for (int i = 0; i < n; i++) {
            double[] edge = i + 1 < n ? arcs.get(i + 1) : closing;
            ring.add(pts.get(i)[0], pts.get(i)[1], edge);
        }
        return ring;
    }

    /**
     * Fractures a surface's islands and holes into one ring per island. Each
     * hole goes to the smallest island that contains it, or to the largest
     * island when none does (a numerically borderline hole on an edge).
     */
    static List<Ring> fractureGroup(List<Ring> islands, List<Ring> holes) {
        List<List<Ring>> holesByIsland = new ArrayList<>();
        List<double[]> areas = new ArrayList<>();
        int largest = 0;
        for (int i = 0; i < islands.size(); i++) {
            holesByIsland.add(new ArrayList<>());
            areas.add(new double[] {Math.abs(signedArea(islands.get(i)))});
            if (areas.get(i)[0] > areas.get(largest)[0]) {
                largest = i;
            }
        }
        List<List<double[]>> outlines = new ArrayList<>();
        for (Ring island : islands) {
            outlines.add(flatten(island));
        }
        for (Ring hole : holes) {
            if (hole.size() == 0) {
                continue;
            }
            double[] probe = hole.vertices.get(0);
            int best = -1;
            for (int i = 0; i < islands.size(); i++) {
                if (contains(outlines.get(i), probe)
                        && (best < 0 || areas.get(i)[0] < areas.get(best)[0])) {
                    best = i;
                }
            }
            holesByIsland.get(best < 0 ? largest : best).add(hole);
        }
        List<Ring> out = new ArrayList<>();
        for (int i = 0; i < islands.size(); i++) {
            List<Ring> mine = holesByIsland.get(i);
            out.add(mine.isEmpty() ? islands.get(i) : fracture(islands.get(i), mine));
        }
        return out;
    }

    /** Ray-casting point-in-polygon on a flattened outline. */
    private static boolean contains(List<double[]> poly, double[] p) {
        boolean inside = false;
        int n = poly.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double[] a = poly.get(i);
            double[] b = poly.get(j);
            if ((a[1] > p[1]) != (b[1] > p[1])) {
                double x = a[0] + (p[1] - a[1]) * (b[0] - a[0]) / (b[1] - a[1]);
                if (p[0] < x) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    /** The ring's vertices with arcs coarsely flattened (22.5° steps). */
    private static List<double[]> flatten(Ring ring) {
        return flatten(ring, 0);
    }

    /**
     * The ring's vertices with arcs flattened to the given chord tolerance in
     * mm (0 = coarse 22.5° steps, enough for areas and containment).
     */
    static List<double[]> flatten(Ring ring, double toleranceMm) {
        List<double[]> pts = new ArrayList<>();
        int n = ring.size();
        for (int k = 0; k < n; k++) {
            double[] a = ring.vertices.get(k);
            double[] b = ring.vertices.get((k + 1) % n);
            pts.add(a);
            double[] e = ring.edges.get(k);
            if (e != null) {
                appendArcPoints(pts, a, b, e[0], e[1], e[2] == 1, toleranceMm);
            }
        }
        return pts;
    }

    /** Merges the holes into the island by cut-ins; the result is one ring. */
    static Ring fracture(Ring island, List<Ring> holes) {
        Ring merged = island;
        boolean outerCcw = signedArea(island) >= 0;
        for (Ring hole : holes) {
            if (hole.size() == 0) {
                continue;
            }
            Ring h = (signedArea(hole) >= 0) == outerCcw ? reverse(hole) : hole;
            merged = bridge(merged, h);
        }
        return merged;
    }

    // ---------------------------------------------------------------- bridging

    private static Ring bridge(Ring outer, Ring hole) {
        // Closest pair: (outer vertex/edge point, hole vertex/edge point)
        double best = Double.MAX_VALUE;
        int bo = 0;
        double to = 0;
        int bh = 0;
        double th = 0;
        for (int j = 0; j < hole.size(); j++) {
            double[] p = hole.vertices.get(j);
            for (int k = 0; k < outer.size(); k++) {
                double[] r = nearestOnEdge(outer, k, p);
                if (r[0] < best) {
                    best = r[0];
                    bo = k;
                    to = r[1];
                    bh = j;
                    th = 0;
                }
            }
        }
        for (int k = 0; k < outer.size(); k++) {
            double[] p = outer.vertices.get(k);
            for (int j = 0; j < hole.size(); j++) {
                double[] r = nearestOnEdge(hole, j, p);
                if (r[0] < best) {
                    best = r[0];
                    bo = k;
                    to = 0;
                    bh = j;
                    th = r[1];
                }
            }
        }
        // A nearest point at an edge's end is that edge's end vertex.
        if (to >= 1) {
            bo = (bo + 1) % outer.size();
            to = 0;
        }
        if (th >= 1) {
            bh = (bh + 1) % hole.size();
            th = 0;
        }
        Ring o = split(outer, bo, to);
        int oi = to > 0 ? bo + 1 : bo;
        Ring h = split(hole, bh, th);
        int hi = th > 0 ? bh + 1 : bh;

        Ring out = new Ring();
        for (int k = 0; k <= oi; k++) {
            out.add(o.vertices.get(k)[0], o.vertices.get(k)[1],
                    k < oi ? o.edges.get(k) : null);          // last: straight cut-in to the hole
        }
        int hn = h.size();
        for (int s = 0; s < hn; s++) {
            int j = (hi + s) % hn;
            out.add(h.vertices.get(j)[0], h.vertices.get(j)[1], h.edges.get(j));
        }
        out.add(h.vertices.get(hi)[0], h.vertices.get(hi)[1], null); // back across the cut-in
        for (int k = oi; k < o.size(); k++) {
            out.add(o.vertices.get(k)[0], o.vertices.get(k)[1], o.edges.get(k));
        }
        return out;
    }

    /**
     * Distance from p to edge k of the ring and the parameter (0..1) of the
     * nearest point along it. Arc edges are measured at their end points only.
     */
    private static double[] nearestOnEdge(Ring ring, int k, double[] p) {
        double[] a = ring.vertices.get(k);
        double[] b = ring.vertices.get((k + 1) % ring.size());
        double da = Math.hypot(p[0] - a[0], p[1] - a[1]);
        if (ring.edges.get(k) != null) {
            return new double[] {da, 0};
        }
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double len2 = dx * dx + dy * dy;
        if (len2 <= 0) {
            return new double[] {da, 0};
        }
        double t = ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / len2;
        if (t <= 1e-9) {
            return new double[] {da, 0};
        }
        if (t >= 1 - 1e-9) {
            return new double[] {Math.hypot(p[0] - b[0], p[1] - b[1]), 1};
        }
        double qx = a[0] + t * dx;
        double qy = a[1] + t * dy;
        return new double[] {Math.hypot(p[0] - qx, p[1] - qy), t};
    }

    /** Inserts a vertex at parameter 0 &lt; t &lt; 1 along straight edge k (no-op otherwise). */
    private static Ring split(Ring ring, int k, double t) {
        if (t <= 0 || t >= 1) {
            return ring;
        }
        double[] a = ring.vertices.get(k);
        double[] b = ring.vertices.get((k + 1) % ring.size());
        Ring r = new Ring();
        for (int i = 0; i < ring.size(); i++) {
            r.add(ring.vertices.get(i)[0], ring.vertices.get(i)[1], ring.edges.get(i));
            if (i == k) {
                r.add(a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1]), null);
            }
        }
        return r;
    }

    private static Ring reverse(Ring ring) {
        int n = ring.size();
        Ring r = new Ring();
        for (int j = 0; j < n; j++) {
            double[] v = ring.vertices.get(n - 1 - j);
            double[] e = ring.edges.get(((n - 2 - j) % n + n) % n);
            r.add(v[0], v[1], e == null ? null : new double[] {e[0], e[1], e[2] == 1 ? 0 : 1});
        }
        return r;
    }

    /** Shoelace area of the ring with arcs coarsely flattened; positive = counter-clockwise. */
    static double signedArea(Ring ring) {
        List<double[]> pts = flatten(ring);
        double area = 0;
        for (int i = 0; i < pts.size(); i++) {
            double[] p = pts.get(i);
            double[] q = pts.get((i + 1) % pts.size());
            area += p[0] * q[1] - q[0] * p[1];
        }
        return area / 2;
    }

    private static void appendArcPoints(List<double[]> out, double[] a, double[] b,
                                        double cx, double cy, boolean clockwise) {
        appendArcPoints(out, a, b, cx, cy, clockwise, 0);
    }

    private static void appendArcPoints(List<double[]> out, double[] a, double[] b,
                                        double cx, double cy, boolean clockwise, double toleranceMm) {
        double r = Math.hypot(a[0] - cx, a[1] - cy);
        if (r <= 0) {
            return;
        }
        double a0 = Math.atan2(a[1] - cy, a[0] - cx);
        double a1 = Math.atan2(b[1] - cy, b[0] - cx);
        double sweep = a1 - a0;
        if (same(a, b)) {
            sweep = clockwise ? -2 * Math.PI : 2 * Math.PI;
        } else if (clockwise) {
            while (sweep >= 0) {
                sweep -= 2 * Math.PI;
            }
        } else {
            while (sweep <= 0) {
                sweep += 2 * Math.PI;
            }
        }
        double maxStep = toleranceMm > 0
                ? 2 * Math.acos(Math.max(0, 1 - toleranceMm / r))
                : Math.PI / 8;
        int steps = Math.max(2, (int) Math.ceil(Math.abs(sweep) / Math.max(maxStep, 1e-4)));
        for (int i = 1; i < steps; i++) {
            double ang = a0 + sweep * i / steps;
            out.add(new double[] {cx + r * Math.cos(ang), cy + r * Math.sin(ang)});
        }
    }

    private static boolean same(double[] a, double[] b) {
        return Math.abs(a[0] - b[0]) < 1e-7 && Math.abs(a[1] - b[1]) < 1e-7;
    }
}

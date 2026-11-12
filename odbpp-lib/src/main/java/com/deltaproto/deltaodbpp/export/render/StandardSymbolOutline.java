package com.deltaproto.deltaodbpp.export.render;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The outline of an ODB++ standard symbol, built from its name.
 *
 * <p>Covers the whole standard symbol table — donuts, polygons, butterflies, the thermal
 * families, stencil shapes, targets — as plain geometry: millimetres, origin at the
 * symbol's own origin, Y up, unrotated. The filled area is what the symbol exposes;
 * rings and thermals come out as outlines with their openings already cut, so the shape
 * is drawn with a single fill and no knowledge of what lies beneath it.
 *
 * <p>Spoke angles of thermals are counter-clockwise from the positive X axis. Corners are
 * numbered counter-clockwise starting top right, as in the symbol names.
 */
public final class StandardSymbolOutline {

    private static final double KAPPA = 0.5522847498307936;
    private static final int ALL_CORNERS = 0b1111;

    private StandardSymbolOutline() {
    }

    /**
     * @param name     the symbol name, e.g. {@code donut_rc3250x3250x50xr25}
     * @param unitToMm millimetres per unit of the name's dimensions (0.0254 for mils,
     *                 0.001 for microns)
     * @return the outline, empty for a symbol that draws nothing ({@code null<ext>}), or
     *         null when the name is not a standard symbol
     */
    public static Shape of(String name, double unitToMm) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        int split = 0;
        while (split < lower.length() && !isNumberStart(lower, split)) {
            split++;
        }
        String family = lower.substring(0, split);
        Args args = Args.parse(lower.substring(split), unitToMm);
        if (args == null) {
            return null;
        }
        try {
            return build(family, args);
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            return null; // too few parameters for the family
        }
    }

    private static boolean isNumberStart(String s, int i) {
        char c = s.charAt(i);
        return (c >= '0' && c <= '9') || c == '.'
                || (c == '-' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1)) && i > 0
                && s.charAt(i - 1) != '_' && !Character.isLetter(s.charAt(i - 1)));
    }

    private static Shape build(String family, Args a) {
        switch (family) {
            case "r":
                return circle(a.len(0));
            case "s":
                return rect(a.len(0), a.len(0));
            case "rect":
                return cornerRect(a.len(0), a.len(1), a.cornerRadius, a.corners, a.chamfer);
            case "oval":
                return stadium(a.len(0), a.len(1));
            case "el":
                return new Ellipse2D.Double(-a.len(0) / 2, -a.len(1) / 2, a.len(0), a.len(1));
            case "di":
                return polygon(a.len(0) / 2, 0, 0, a.len(1) / 2, -a.len(0) / 2, 0, 0, -a.len(1) / 2);
            case "oct":
                return cornerRect(a.len(0), a.len(1), a.len(2), ALL_CORNERS, true);
            case "hex_l": {
                double w = a.len(0) / 2;
                double h = a.len(1) / 2;
                double r = a.len(2);
                return polygon(w, 0, w - r, h, -w + r, h, -w, 0, -w + r, -h, w - r, -h);
            }
            case "hex_s": {
                double w = a.len(0) / 2;
                double h = a.len(1) / 2;
                double r = a.len(2);
                return polygon(0, h, -w, h - r, -w, -h + r, 0, -h, w, -h + r, w, h - r);
            }
            case "tri":
                return polygon(-a.len(0) / 2, -a.len(1) / 2, a.len(0) / 2, -a.len(1) / 2, 0, a.len(1) / 2);
            case "oval_h": {
                // Flat on the left, a half circle on the right.
                double w = a.len(0);
                double h = a.len(1);
                return cornerRect(w, h, Math.min(h / 2, w), 0b1001, false);
            }
            case "bfr": {
                double r = a.len(0) / 2;
                Area disc = new Area(circle(2 * r));
                Area quadrants = new Area(new Rectangle2D.Double(-r, 0, r, r));
                quadrants.add(new Area(new Rectangle2D.Double(0, -r, r, r)));
                disc.intersect(quadrants);
                return disc;
            }
            case "bfs": {
                double h = a.len(0) / 2;
                Path2D.Double p = new Path2D.Double();
                p.append(new Rectangle2D.Double(-h, 0, h, h), false);
                p.append(new Rectangle2D.Double(0, -h, h, h), false);
                return p;
            }
            case "hole":
                return circle(a.len(0));
            case "null":
                return new Path2D.Double();
            case "donut_r":
                return ring(circle(a.len(0)), a.len(1) > 0 ? circle(a.len(1)) : null);
            case "donut_s": {
                double lw = (a.len(0) - a.len(1)) / 2;
                return ring(cornerRect(a.len(0), a.len(0), a.cornerRadius, a.corners, false),
                        a.len(1) > 0 ? cornerRect(a.len(1), a.len(1),
                                Math.max(a.cornerRadius - lw, 0), a.corners, false) : null);
            }
            case "donut_sr":
                return ring(rect(a.len(0), a.len(0)), a.len(1) > 0 ? circle(a.len(1)) : null);
            case "donut_rc": {
                double w = a.len(0);
                double h = a.len(1);
                double lw = a.len(2);
                boolean open = w > 2 * lw && h > 2 * lw;
                return ring(cornerRect(w, h, a.cornerRadius, a.corners, false),
                        open ? cornerRect(w - 2 * lw, h - 2 * lw,
                                Math.max(a.cornerRadius - lw, 0), a.corners, false) : null);
            }
            case "donut_o": {
                double w = a.len(0);
                double h = a.len(1);
                double lw = a.len(2);
                boolean open = w > 2 * lw && h > 2 * lw;
                return ring(stadium(w, h), open ? stadium(w - 2 * lw, h - 2 * lw) : null);
            }
            case "thr":
            case "ths": {
                Area ring = ring(circle(a.len(0)), a.len(1) > 0 ? circle(a.len(1)) : null);
                return spokes(ring, a.len(0), a.len(0), a.len(1), a.len(1),
                        a.num(2), (int) a.num(3), a.len(4), false);
            }
            case "s_ths":
            case "s_tho":
            case "s_thr": {
                double os = a.len(0);
                double is = a.len(1);
                double lw = (os - is) / 2;
                Area ring = ring(cornerRect(os, os, a.cornerRadius, a.corners, false),
                        is > 0 ? cornerRect(is, is, Math.max(a.cornerRadius - lw, 0), a.corners, false)
                                : null);
                return spokes(ring, os, os, is, is, a.num(2), (int) a.num(3), a.len(4),
                        !family.equals("s_ths"));
            }
            case "sr_ths": {
                Area ring = ring(rect(a.len(0), a.len(0)), a.len(1) > 0 ? circle(a.len(1)) : null);
                return spokes(ring, a.len(0), a.len(0), a.len(1), a.len(1),
                        a.num(2), (int) a.num(3), a.len(4), false);
            }
            case "rc_ths":
            case "rc_tho": {
                double w = a.len(0);
                double h = a.len(1);
                double air = a.len(5);
                boolean open = w > 2 * air && h > 2 * air;
                Area ring = ring(cornerRect(w, h, a.cornerRadius, a.corners, false),
                        open ? cornerRect(w - 2 * air, h - 2 * air,
                                Math.max(a.cornerRadius - air, 0), a.corners, false) : null);
                return spokes(ring, w, h, w - 2 * air, h - 2 * air,
                        a.num(2), (int) a.num(3), a.len(4), family.equals("rc_tho"));
            }
            case "o_ths":
            case "oblong_ths": {
                double w = a.len(0);
                double h = a.len(1);
                double lw = a.len(5);
                boolean open = w > 2 * lw && h > 2 * lw;
                Area ring = ring(stadium(w, h), open ? stadium(w - 2 * lw, h - 2 * lw) : null);
                // The spokes of an oval thermal all run through its centre.
                return spokes(ring, w, w, 0, 0, a.num(2), (int) a.num(3), a.len(4), false);
            }
            case "moire":
                return moire(a.len(0), a.len(1), (int) a.num(2), a.len(3), a.len(4), a.num(5));
            case "hplate": {
                double w = a.len(0) / 2;
                double h = a.len(1) / 2;
                double c = a.len(2);
                return polygon(-w, -h, w - c, -h, w, 0, w - c, h, -w, h);
            }
            case "rhplate": {
                double w = a.len(0) / 2;
                double h = a.len(1) / 2;
                double c = a.len(2);
                return polygon(-w, -h, w, -h, w - c, 0, w, h, -w, h);
            }
            case "fhplate": {
                double w = a.len(0) / 2;
                double h = a.len(1) / 2;
                double vc = a.len(2);
                double hc = a.len(3);
                return polygon(-w, -h, w - hc, -h, w, -h + vc, w, h - vc, w - hc, h, -w, h);
            }
            case "radhplate":
                return radiusedInvertedHomePlate(a.len(0), a.len(1), a.len(2));
            case "dshape":
                return cornerRect(a.len(0), a.len(1), Math.min(a.len(2), Math.min(a.len(0), a.len(1) / 2)),
                        0b1001, false);
            case "cross": {
                double w = a.len(0);
                double h = a.len(1);
                Area cross = new Area(bar(w, a.len(2), 0, -h / 2 + a.len(5), a.rounded));
                cross.add(new Area(bar(a.len(3), h, -w / 2 + a.len(4), 0, a.rounded)));
                return cross;
            }
            case "dogbone": {
                // Two bars across the ends, joined by a neck.
                double w = a.len(0);
                double h = a.len(1);
                double hs = a.len(2);
                Area bone = new Area(bar(w, hs, 0, h / 2 - hs / 2, a.rounded));
                bone.add(new Area(bar(w, hs, 0, -h / 2 + hs / 2, a.rounded)));
                bone.add(new Area(bar(a.len(3), h - hs, -w / 2 + a.len(4), 0, false)));
                return bone;
            }
            case "dpack": {
                double w = a.len(0);
                double h = a.len(1);
                double hg = a.len(2);
                double vg = a.len(3);
                int hn = Math.max(1, (int) a.num(4));
                int vn = Math.max(1, (int) a.num(5));
                double cw = (w - (hn - 1) * hg) / hn;
                double ch = (h - (vn - 1) * vg) / vn;
                if (!(cw > 0) || !(ch > 0)) {
                    return rect(w, h);
                }
                Path2D.Double p = new Path2D.Double();
                for (int i = 0; i < hn; i++) {
                    for (int j = 0; j < vn; j++) {
                        p.append(new Rectangle2D.Double(-w / 2 + i * (cw + hg), -h / 2 + j * (ch + vg),
                                cw, ch), false);
                    }
                }
                return p;
            }
            default:
                return null;
        }
    }

    // ---- building blocks -----------------------------------------------------------------------

    private static Shape circle(double d) {
        return new Ellipse2D.Double(-d / 2, -d / 2, d, d);
    }

    private static Shape rect(double w, double h) {
        return new Rectangle2D.Double(-w / 2, -h / 2, w, h);
    }

    private static Shape stadium(double w, double h) {
        return cornerRect(w, h, Math.min(w, h) / 2, ALL_CORNERS, false);
    }

    private static Shape polygon(double... xy) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            p.lineTo(xy[i], xy[i + 1]);
        }
        p.closePath();
        return p;
    }

    /** A bar of the given size centred on (cx, cy), optionally with rounded ends. */
    private static Shape bar(double w, double h, double cx, double cy, boolean rounded) {
        Shape s = rounded ? stadium(w, h) : rect(w, h);
        return AffineTransform.getTranslateInstance(cx, cy).createTransformedShape(s);
    }

    /**
     * A rectangle whose selected corners are rounded or chamfered by r. Bit 0 of the mask
     * is corner 1 (top right), then counter-clockwise: top left, bottom left, bottom right.
     */
    static Shape cornerRect(double w, double h, double r, int mask, boolean chamfer) {
        double hw = w / 2;
        double hh = h / 2;
        r = Math.max(0, Math.min(r, Math.min(hw, hh)));
        double r1 = (mask & 1) != 0 ? r : 0;
        double r2 = (mask & 2) != 0 ? r : 0;
        double r3 = (mask & 4) != 0 ? r : 0;
        double r4 = (mask & 8) != 0 ? r : 0;
        Path2D.Double p = new Path2D.Double();
        p.moveTo(hw, -hh + r4);
        p.lineTo(hw, hh - r1);
        corner(p, hw, hh, hw - r1, hh, r1, chamfer);
        p.lineTo(-hw + r2, hh);
        corner(p, -hw, hh, -hw, hh - r2, r2, chamfer);
        p.lineTo(-hw, -hh + r3);
        corner(p, -hw, -hh, -hw + r3, -hh, r3, chamfer);
        p.lineTo(hw - r4, -hh);
        corner(p, hw, -hh, hw, -hh + r4, r4, chamfer);
        p.closePath();
        return p;
    }

    /** Turns the corner at (cx, cy) from the current point to (x, y). */
    private static void corner(Path2D.Double p, double cx, double cy, double x, double y,
                               double r, boolean chamfer) {
        if (!(r > 0)) {
            return;
        }
        if (chamfer) {
            p.lineTo(x, y);
            return;
        }
        double x0 = p.getCurrentPoint().getX();
        double y0 = p.getCurrentPoint().getY();
        p.curveTo(x0 + KAPPA * (cx - x0), y0 + KAPPA * (cy - y0),
                x + KAPPA * (cx - x), y + KAPPA * (cy - y), x, y);
    }

    private static Area ring(Shape outer, Shape inner) {
        Area area = new Area(outer);
        if (inner != null) {
            area.subtract(new Area(inner));
        }
        return area;
    }

    /**
     * Cuts the gaps of a thermal into its ring. A gap is a band of the given width running
     * outward from the centre; on a diagonal of a rectangular thermal it runs through the
     * corner instead, and with open corners the whole corner is left away.
     *
     * @param ow outer width, oh outer height, iw inner width, ih inner height
     */
    private static Area spokes(Area ring, double ow, double oh, double iw, double ih,
                               double angle, int count, double gap, boolean openCorners) {
        if (count <= 0 || !(gap > 0)) {
            return ring;
        }
        double reach = ow + oh;
        for (int k = 0; k < count; k++) {
            double deg = ((angle + k * 360.0 / count) % 360 + 360) % 360;
            boolean diagonal = Math.abs(deg % 90 - 45) < 1e-6;
            Shape cut;
            if (diagonal && (openCorners || ow != oh)) {
                int sx = deg > 90 && deg < 270 ? -1 : 1;
                int sy = deg > 180 ? -1 : 1;
                if (openCorners) {
                    double x = Math.max(0, iw / 2 - gap / Math.sqrt(2));
                    double y = Math.max(0, ih / 2 - gap / Math.sqrt(2));
                    cut = new Rectangle2D.Double(sx > 0 ? x : -x - reach, sy > 0 ? y : -y - reach,
                            reach, reach);
                } else {
                    double thickness = (ow - iw) / 2;
                    double length = 2 * (thickness * Math.sqrt(2) + gap);
                    AffineTransform t = AffineTransform.getTranslateInstance(
                            sx * (ow + iw) / 4, sy * (oh + ih) / 4);
                    t.rotate(Math.toRadians(sx * sy > 0 ? 45 : 135));
                    cut = t.createTransformedShape(
                            new Rectangle2D.Double(-length / 2, -gap / 2, length, gap));
                }
            } else {
                cut = AffineTransform.getRotateInstance(Math.toRadians(deg)).createTransformedShape(
                        new Rectangle2D.Double(0, -gap / 2, reach, gap));
            }
            ring.subtract(new Area(cut));
        }
        return ring;
    }

    /** Concentric rings counted outward from the centre, with a pair of crossing lines. */
    private static Shape moire(double ringWidth, double ringGap, int rings, double lineWidth,
                               double lineLength, double lineAngle) {
        Area area = new Area();
        for (int k = 1; k <= rings; k++) {
            double outer = k * (ringWidth + ringGap);
            area.add(ring(circle(2 * outer), circle(2 * (outer - ringWidth))));
        }
        if (lineWidth > 0 && lineLength > 0) {
            for (int k = 0; k < 2; k++) {
                area.add(new Area(AffineTransform.getRotateInstance(Math.toRadians(lineAngle + 90 * k))
                        .createTransformedShape(rect(lineLength, lineWidth))));
            }
        }
        return area;
    }

    /** A rectangle whose right side is hollowed by an arc, leaving the given width in the middle. */
    private static Shape radiusedInvertedHomePlate(double w, double h, double middle) {
        double sagitta = w - middle;
        if (!(sagitta > 0) || !(h > 0)) {
            return rect(w, h);
        }
        double radius = (h * h / 4 + sagitta * sagitta) / (2 * sagitta);
        Area area = new Area(rect(w, h));
        double cx = w / 2 - sagitta + radius;
        area.subtract(new Area(new Ellipse2D.Double(cx - radius, -radius, 2 * radius, 2 * radius)));
        return area;
    }

    // ---- name parameters -----------------------------------------------------------------------

    /** The 'x'-separated parameters of a symbol name. */
    private static final class Args {
        private final List<Double> values = new ArrayList<>();
        private double unitToMm;
        double cornerRadius;
        int corners = ALL_CORNERS;
        boolean chamfer;
        /** A trailing {@code r} asks for rounded ends, {@code s} for square ones. */
        boolean rounded;

        static Args parse(String text, double unitToMm) {
            Args args = new Args();
            args.unitToMm = unitToMm;
            if (text.isEmpty()) {
                return args;
            }
            String[] tokens = text.split("x", -1);
            for (int i = 0; i < tokens.length; i++) {
                String token = tokens[i];
                if (token.isEmpty()) {
                    return null;
                }
                char c = token.charAt(0);
                try {
                    if (token.length() == 1 && Character.isLetter(c)) {
                        // A flag: r|s for the line ends, p|n|v for a hole's plating.
                        args.rounded = c == 'r';
                        continue;
                    }
                    if (c == 'r' || c == 'c' || c == 's') {
                        if (c == 's') {
                            return null;
                        }
                        // xr<rad> / xc<rad>, optionally followed by the corners it applies to.
                        args.cornerRadius = Double.parseDouble(token.substring(1)) * unitToMm;
                        args.chamfer = c == 'c';
                        if (i + 1 < tokens.length && tokens[i + 1].matches("[1-4]{1,4}")) {
                            args.corners = 0;
                            for (char d : tokens[++i].toCharArray()) {
                                args.corners |= 1 << (d - '1');
                            }
                        }
                    } else {
                        args.values.add(Double.parseDouble(token));
                    }
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return args;
        }

        /** A length parameter, in mm. */
        double len(int index) {
            double v = values.get(index) * unitToMm;
            if (v < 0) {
                return 0;
            }
            return v;
        }

        /** A plain number: an angle or a count. */
        double num(int index) {
            return values.get(index);
        }
    }
}

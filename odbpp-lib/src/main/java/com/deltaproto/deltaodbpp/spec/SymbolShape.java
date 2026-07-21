package com.deltaproto.deltaodbpp.spec;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed ODB++ standard-symbol name (e.g. {@code r1000}, {@code rect100x50}, {@code oval80x40})
 * with all dimensions converted to millimetres.
 *
 * <p>This duplicates the minimal name-parsing the spec package needs so it does not depend on the
 * {@code export/} package (which a sibling change touches). Symbol-name dimensions are in
 * <strong>microns</strong> for {@code UNITS=MM} feature files and <strong>mils</strong> for
 * {@code UNITS=INCH} — the caller passes the matching {@code unitToMm} factor (0.001 or 0.0254).
 * Only the families the analyzer measures (round/square/rect/oval/donut and hole) are modelled;
 * anything else parses to {@code null} and the caller falls back to an approximation.
 */
final class SymbolShape {

    enum Kind { ROUND, SQUARE, RECT, ROUNDED_RECT, OVAL, DONUT }

    private static final Pattern ROUND = Pattern.compile("r([0-9.]+)");
    private static final Pattern SQUARE = Pattern.compile("s([0-9.]+)");
    private static final Pattern RECT = Pattern.compile("rect([0-9.]+)x([0-9.]+)");
    private static final Pattern ROUNDED_RECT =
            Pattern.compile("rect([0-9.]+)x([0-9.]+)xr([0-9.]+)(?:x([1-4]+))?");
    private static final Pattern OVAL = Pattern.compile("oval([0-9.]+)x([0-9.]+)");
    private static final Pattern DONUT = Pattern.compile("donut_r([0-9.]+)x([0-9.]+)");
    // A round drilled hole symbol, plated or non-plated. The spec form is
    // hole<d>x<p>x<tp>x<tm> (diameter, plating 0/1, type, mark); the leading diameter is the drill
    // size and the only field the analyzer measures. An optional v/r prefix (vrhole, rhole) and a
    // bare "hole300" without the trailing fields are tolerated, so a variety of exports round-trip.
    private static final Pattern HOLE = Pattern.compile("(?:v?r?)?hole([0-9.]+)(?:x[0-9.]+)*");

    final Kind kind;
    final double width;         // mm; diameter for ROUND/SQUARE/DONUT outer
    final double height;        // mm; 0 where not applicable
    final double innerDiameter; // mm; DONUT only

    private SymbolShape(Kind kind, double width, double height, double innerDiameter) {
        this.kind = kind;
        this.width = width;
        this.height = height;
        this.innerDiameter = innerDiameter;
    }

    /**
     * Parse a standard-symbol name into a shape with mm dimensions, or {@code null} when the family
     * is not one the analyzer measures.
     *
     * @param unitToMm 0.001 for UNITS=MM (microns) or 0.0254 for UNITS=INCH (mils)
     */
    static SymbolShape parse(String name, double unitToMm) {
        if (name == null) {
            return null;
        }
        String n = name.toLowerCase(Locale.ROOT).trim();

        Matcher m = ROUNDED_RECT.matcher(n);
        if (m.matches()) {
            return new SymbolShape(Kind.ROUNDED_RECT,
                    Double.parseDouble(m.group(1)) * unitToMm,
                    Double.parseDouble(m.group(2)) * unitToMm, 0);
        }
        m = RECT.matcher(n);
        if (m.matches()) {
            return new SymbolShape(Kind.RECT,
                    Double.parseDouble(m.group(1)) * unitToMm,
                    Double.parseDouble(m.group(2)) * unitToMm, 0);
        }
        m = OVAL.matcher(n);
        if (m.matches()) {
            return new SymbolShape(Kind.OVAL,
                    Double.parseDouble(m.group(1)) * unitToMm,
                    Double.parseDouble(m.group(2)) * unitToMm, 0);
        }
        m = DONUT.matcher(n);
        if (m.matches()) {
            return new SymbolShape(Kind.DONUT,
                    Double.parseDouble(m.group(1)) * unitToMm, 0,
                    Double.parseDouble(m.group(2)) * unitToMm);
        }
        m = HOLE.matcher(n);
        if (m.matches()) {
            // A drilled hole is a round opening; its leading dimension is the drill diameter.
            return new SymbolShape(Kind.ROUND, Double.parseDouble(m.group(1)) * unitToMm, 0, 0);
        }
        m = ROUND.matcher(n);
        if (m.matches()) {
            return new SymbolShape(Kind.ROUND, Double.parseDouble(m.group(1)) * unitToMm, 0, 0);
        }
        m = SQUARE.matcher(n);
        if (m.matches()) {
            double d = Double.parseDouble(m.group(1)) * unitToMm;
            return new SymbolShape(Kind.SQUARE, d, d, 0);
        }
        return null;
    }

    /**
     * The stroke width when this symbol draws a line or arc, in mm. Only round symbols are a true
     * stroke width; non-round symbols are approximated by their smaller dimension, matching the
     * delta-gerber circular-aperture convention.
     */
    double strokeWidthMm() {
        return switch (kind) {
            case ROUND, DONUT, SQUARE -> width;
            default -> height > 0 ? Math.min(width, height) : width;
        };
    }

    /** True for a round stroke — the only kind delta-gerber counts toward min track width. */
    boolean isRound() {
        return kind == Kind.ROUND;
    }
}

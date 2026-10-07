package com.deltaproto.deltaodbpp.export.gerber;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed ODB++ standard symbol, with all dimensions converted to
 * millimetres. Symbol-name dimensions are in mils for UNITS=INCH feature
 * files and microns for UNITS=MM files (spec, Appendix A).
 *
 * Only the symbol families needed for Gerber aperture mapping are modelled
 * here; anything else returns {@code null} from {@link #parse} and callers
 * fall back to an approximation.
 */
public final class OdbSymbolShape {

    public enum Kind { ROUND, SQUARE, RECT, ROUNDED_RECT, CHAMFERED_RECT, OVAL, DONUT, THERMAL }

    /** Round thermal relief: {@code thr<od>x<id>x<angle>x<spokes>x<gap>} (rounded or squared gap ends). */
    private static final Pattern THERMAL =
            Pattern.compile("th[rs]([0-9.]+)x([0-9.]+)x([0-9.]+)x(\\d+)x([0-9.]+)");

    /** THERMAL only: angle (degrees, counter-clockwise) of the first gap. */
    public final double gapAngle;
    /** THERMAL only: number of gaps/spokes. */
    public final int spokes;
    /** THERMAL only: gap width in mm. */
    public final double gap;

    private static final String NUM = "([0-9.]+)";
    private static final Pattern ROUND = Pattern.compile("r" + NUM);
    private static final Pattern SQUARE = Pattern.compile("s" + NUM);
    private static final Pattern RECT = Pattern.compile("rect" + NUM + "x" + NUM);
    private static final Pattern ROUNDED_RECT =
            Pattern.compile("rect" + NUM + "x" + NUM + "xr" + NUM + "(?:x([1-4]+))?");
    private static final Pattern CHAMFERED_RECT =
            Pattern.compile("rect" + NUM + "x" + NUM + "xc" + NUM + "(?:x([1-4]+))?");
    private static final Pattern OVAL = Pattern.compile("oval" + NUM + "x" + NUM);
    private static final Pattern DONUT = Pattern.compile("donut_r" + NUM + "x" + NUM);

    public final Kind kind;
    public final double width;   // mm; diameter for ROUND/SQUARE/DONUT outer
    public final double height;  // mm; 0 where not applicable
    /** mm; corner radius (ROUNDED_RECT) or chamfer size (CHAMFERED_RECT). */
    public final double cornerSize;
    public final double innerDiameter; // mm; DONUT only
    /**
     * Which corners get the corner treatment, as the spec's digit string
     * (1 = top right, 2 = top left, 3 = bottom left, 4 = bottom right), or
     * null for all four.
     */
    public final String corners;

    private OdbSymbolShape(Kind kind, double width, double height,
                           double cornerSize, double innerDiameter, String corners) {
        this(kind, width, height, cornerSize, innerDiameter, corners, 0, 0, 0);
    }

    private OdbSymbolShape(Kind kind, double width, double height,
                           double cornerSize, double innerDiameter, String corners,
                           double gapAngle, int spokes, double gap) {
        this.kind = kind;
        this.width = width;
        this.height = height;
        this.cornerSize = cornerSize;
        this.innerDiameter = innerDiameter;
        this.corners = corners;
        this.gapAngle = gapAngle;
        this.spokes = spokes;
        this.gap = gap;
    }

    /**
     * Parses a standard symbol name.
     *
     * @param name      symbol name, e.g. {@code r99.9998} or {@code rect100x200xr20}
     * @param unitToMm  0.001 for UNITS=MM (microns), 0.0254 for UNITS=INCH (mils)
     * @return the parsed shape, or null when the symbol family is not supported
     */
    public static OdbSymbolShape parse(String name, double unitToMm) {
        if (name == null) {
            return null;
        }
        String n = name.toLowerCase(Locale.ROOT).trim();

        Matcher m = ROUNDED_RECT.matcher(n);
        if (m.matches()) {
            return new OdbSymbolShape(Kind.ROUNDED_RECT,
                    Double.parseDouble(m.group(1)) * unitToMm,
                    Double.parseDouble(m.group(2)) * unitToMm,
                    Double.parseDouble(m.group(3)) * unitToMm, 0, m.group(4));
        }
        m = CHAMFERED_RECT.matcher(n);
        if (m.matches()) {
            return new OdbSymbolShape(Kind.CHAMFERED_RECT,
                    Double.parseDouble(m.group(1)) * unitToMm,
                    Double.parseDouble(m.group(2)) * unitToMm,
                    Double.parseDouble(m.group(3)) * unitToMm, 0, m.group(4));
        }
        m = RECT.matcher(n);
        if (m.matches()) {
            return new OdbSymbolShape(Kind.RECT,
                    Double.parseDouble(m.group(1)) * unitToMm,
                    Double.parseDouble(m.group(2)) * unitToMm, 0, 0, null);
        }
        m = OVAL.matcher(n);
        if (m.matches()) {
            return new OdbSymbolShape(Kind.OVAL,
                    Double.parseDouble(m.group(1)) * unitToMm,
                    Double.parseDouble(m.group(2)) * unitToMm, 0, 0, null);
        }
        m = DONUT.matcher(n);
        if (m.matches()) {
            return new OdbSymbolShape(Kind.DONUT,
                    Double.parseDouble(m.group(1)) * unitToMm, 0, 0,
                    Double.parseDouble(m.group(2)) * unitToMm, null);
        }
        m = THERMAL.matcher(n);
        if (m.matches()) {
            return new OdbSymbolShape(Kind.THERMAL,
                    Double.parseDouble(m.group(1)) * unitToMm, 0, 0,
                    Double.parseDouble(m.group(2)) * unitToMm, null,
                    Double.parseDouble(m.group(3)), Integer.parseInt(m.group(4)),
                    Double.parseDouble(m.group(5)) * unitToMm);
        }
        m = ROUND.matcher(n);
        if (m.matches()) {
            return new OdbSymbolShape(Kind.ROUND,
                    Double.parseDouble(m.group(1)) * unitToMm, 0, 0, 0, null);
        }
        m = SQUARE.matcher(n);
        if (m.matches()) {
            double d = Double.parseDouble(m.group(1)) * unitToMm;
            return new OdbSymbolShape(Kind.SQUARE, d, d, 0, 0, null);
        }
        return null;
    }

    /** Whether corner {@code n} (1-4, see {@link #corners}) gets the corner treatment. */
    public boolean hasCorner(int n) {
        return corners == null || corners.indexOf((char) ('0' + n)) >= 0;
    }

    /** Whether all four corners get the corner treatment. */
    public boolean allCorners() {
        return hasCorner(1) && hasCorner(2) && hasCorner(3) && hasCorner(4);
    }

    /**
     * The diameter to use when this symbol strokes a line or arc. Gerber
     * draws are only defined for circular apertures, so non-round symbols
     * are approximated by their smaller dimension.
     */
    public double strokeDiameter() {
        return switch (kind) {
            case ROUND, DONUT, THERMAL -> width;
            case SQUARE -> width;
            default -> height > 0 ? Math.min(width, height) : width;
        };
    }
}

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

    public enum Kind { ROUND, SQUARE, RECT, ROUNDED_RECT, OVAL, DONUT }

    private static final Pattern ROUND = Pattern.compile("r([0-9.]+)");
    private static final Pattern SQUARE = Pattern.compile("s([0-9.]+)");
    private static final Pattern RECT = Pattern.compile("rect([0-9.]+)x([0-9.]+)");
    private static final Pattern ROUNDED_RECT =
            Pattern.compile("rect([0-9.]+)x([0-9.]+)xr([0-9.]+)(?:x([1-4]+))?");
    private static final Pattern OVAL = Pattern.compile("oval([0-9.]+)x([0-9.]+)");
    private static final Pattern DONUT = Pattern.compile("donut_r([0-9.]+)x([0-9.]+)");

    public final Kind kind;
    public final double width;   // mm; diameter for ROUND/SQUARE/DONUT outer
    public final double height;  // mm; 0 where not applicable
    public final double cornerRadius; // mm; ROUNDED_RECT only
    public final double innerDiameter; // mm; DONUT only
    public final String corners; // ROUNDED_RECT corner spec, null = all corners

    private OdbSymbolShape(Kind kind, double width, double height,
                           double cornerRadius, double innerDiameter, String corners) {
        this.kind = kind;
        this.width = width;
        this.height = height;
        this.cornerRadius = cornerRadius;
        this.innerDiameter = innerDiameter;
        this.corners = corners;
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

    /**
     * The diameter to use when this symbol strokes a line or arc. Gerber
     * draws are only defined for circular apertures, so non-round symbols
     * are approximated by their smaller dimension.
     */
    public double strokeDiameter() {
        return switch (kind) {
            case ROUND, DONUT -> width;
            case SQUARE -> width;
            default -> height > 0 ? Math.min(width, height) : width;
        };
    }
}

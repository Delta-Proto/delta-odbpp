package com.deltaproto.deltaodbpp.export.gerber;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Assigns Gerber D-codes to the apertures a layer needs and emits the
 * corresponding %AD / %AM definitions.
 *
 * Standard shapes map directly (C, R, O). Shapes Gerber cannot express as a
 * standard aperture — rounded rectangles, donuts (annuli), and arbitrary
 * rotations of rectangles/obrounds — become aperture macros. Macro rotation
 * is counterclockwise in Gerber while ODB++ rotation is clockwise, so the
 * angle is negated at emission.
 */
public class ApertureRegistry {

    private static final int FIRST_DCODE = 10;

    private final Map<String, Integer> dcodeByKey = new LinkedHashMap<>();
    private final List<String> definitions = new ArrayList<>();
    private final List<String> macroDefinitions = new ArrayList<>();
    private int nextDcode = FIRST_DCODE;
    private int nextMacroId = 0;

    /** D-code for a plain circle of the given diameter (also used for strokes). */
    public int circle(double diameterMm) {
        String key = "C:" + fmt(diameterMm);
        return register(key, dcode ->
                "%ADD" + dcode + "C," + fmt(diameterMm) + "*%");
    }

    /**
     * D-code for a pad flash of the given symbol with clockwise rotation
     * {@code rotationCwDeg}. Mirroring is irrelevant for every supported
     * symbol family (all are symmetric about both axes).
     */
    public int forShape(OdbSymbolShape shape, double rotationCwDeg) {
        double rot = normalize(rotationCwDeg);
        switch (shape.kind) {
            case ROUND:
                return circle(shape.width);
            case SQUARE:
            case RECT: {
                double w = shape.width;
                double h = shape.kind == OdbSymbolShape.Kind.SQUARE ? shape.width : shape.height;
                if (rot == 0 || rot == 180) {
                    return standardRect(w, h);
                }
                if (rot == 90 || rot == 270) {
                    return standardRect(h, w);
                }
                return rotatedRectMacro(w, h, rot);
            }
            case OVAL: {
                if (rot == 0 || rot == 180) {
                    return standardObround(shape.width, shape.height);
                }
                if (rot == 90 || rot == 270) {
                    return standardObround(shape.height, shape.width);
                }
                return rotatedObroundMacro(shape.width, shape.height, rot);
            }
            case ROUNDED_RECT:
                return roundedRectMacro(shape.width, shape.height, shape.cornerRadius, rot);
            case DONUT:
                return donutMacro(shape.width, shape.innerDiameter);
            default:
                throw new IllegalArgumentException("Unsupported shape: " + shape.kind);
        }
    }

    private int standardRect(double w, double h) {
        String key = "R:" + fmt(w) + "x" + fmt(h);
        return register(key, dcode ->
                "%ADD" + dcode + "R," + fmt(w) + "X" + fmt(h) + "*%");
    }

    private int standardObround(double w, double h) {
        String key = "O:" + fmt(w) + "x" + fmt(h);
        return register(key, dcode ->
                "%ADD" + dcode + "O," + fmt(w) + "X" + fmt(h) + "*%");
    }

    private int rotatedRectMacro(double w, double h, double rotCw) {
        String key = "RR:" + fmt(w) + "x" + fmt(h) + "@" + fmt(rotCw);
        return register(key, dcode -> {
            String macro = newMacroName();
            // Center line primitive (21): exposure, width, height, cx, cy, rotation(CCW)
            macroDefinitions.add("%AM" + macro + "*21,1," + fmt(w) + "," + fmt(h)
                    + ",0,0," + fmt(-rotCw) + "*%");
            return "%ADD" + dcode + macro + "*%";
        });
    }

    private int rotatedObroundMacro(double w, double h, double rotCw) {
        String key = "RO:" + fmt(w) + "x" + fmt(h) + "@" + fmt(rotCw);
        return register(key, dcode -> {
            String macro = newMacroName();
            // Obround = central rect plus a circle at each end. The circle
            // centres are rotated in Java; only the rect uses macro rotation.
            boolean wide = w >= h;
            double len = Math.abs(w - h);
            double d = Math.min(w, h);
            double rad = Math.toRadians(-rotCw); // CCW for the math below
            double ex = (wide ? len / 2 : 0);
            double ey = (wide ? 0 : len / 2);
            double c1x = ex * Math.cos(rad) - ey * Math.sin(rad);
            double c1y = ex * Math.sin(rad) + ey * Math.cos(rad);
            StringBuilder body = new StringBuilder();
            body.append("21,1,").append(wide ? fmt(len) : fmt(d)).append(',')
                    .append(wide ? fmt(d) : fmt(len)).append(",0,0,").append(fmt(-rotCw)).append('*');
            body.append("1,1,").append(fmt(d)).append(',').append(fmt(c1x)).append(',').append(fmt(c1y)).append('*');
            body.append("1,1,").append(fmt(d)).append(',').append(fmt(-c1x)).append(',').append(fmt(-c1y));
            String macroDef = "%AM" + macro + "*" + body + "*%";
            macroDefinitions.add(macroDef);
            return "%ADD" + dcode + macro + "*%";
        });
    }

    private int roundedRectMacro(double w, double h, double r, double rotCw) {
        String key = "RC:" + fmt(w) + "x" + fmt(h) + "r" + fmt(r) + "@" + fmt(rotCw);
        return register(key, dcode -> {
            String macro = newMacroName();
            // Two crossed center lines plus four corner circles.
            double rad = Math.toRadians(-rotCw);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            StringBuilder body = new StringBuilder();
            body.append("21,1,").append(fmt(w - 2 * r)).append(',').append(fmt(h))
                    .append(",0,0,").append(fmt(-rotCw)).append('*');
            body.append("21,1,").append(fmt(w)).append(',').append(fmt(h - 2 * r))
                    .append(",0,0,").append(fmt(-rotCw)).append('*');
            double cx = w / 2 - r;
            double cy = h / 2 - r;
            for (int[] sgn : new int[][] {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}}) {
                double px = sgn[0] * cx;
                double py = sgn[1] * cy;
                double rx = px * cos - py * sin;
                double ry = px * sin + py * cos;
                body.append("1,1,").append(fmt(2 * r)).append(',')
                        .append(fmt(rx)).append(',').append(fmt(ry)).append('*');
            }
            body.setLength(body.length() - 1); // drop trailing '*' (added back below)
            macroDefinitions.add("%AM" + macro + "*" + body + "*%");
            return "%ADD" + dcode + macro + "*%";
        });
    }

    private int donutMacro(double outer, double inner) {
        String key = "DN:" + fmt(outer) + "x" + fmt(inner);
        return register(key, dcode -> {
            String macro = newMacroName();
            macroDefinitions.add("%AM" + macro + "*1,1," + fmt(outer)
                    + ",0,0*1,0," + fmt(inner) + ",0,0*%");
            return "%ADD" + dcode + macro + "*%";
        });
    }

    /** Writes all %AM and %AD definitions in declaration order. */
    public void emitDefinitions(StringBuilder out) {
        for (String macro : macroDefinitions) {
            out.append(macro).append('\n');
        }
        for (String def : definitions) {
            out.append(def).append('\n');
        }
    }

    public int apertureCount() {
        return dcodeByKey.size();
    }

    private interface DefinitionFactory {
        String create(int dcode);
    }

    private int register(String key, DefinitionFactory factory) {
        Integer existing = dcodeByKey.get(key);
        if (existing != null) {
            return existing;
        }
        int dcode = nextDcode++;
        dcodeByKey.put(key, dcode);
        definitions.add(factory.create(dcode));
        return dcode;
    }

    private String newMacroName() {
        return "ODBM" + (nextMacroId++);
    }

    private static double normalize(double deg) {
        double d = deg % 360;
        return d < 0 ? d + 360 : d;
    }

    private static String fmt(double v) {
        String s = String.format(Locale.ROOT, "%.6f", v);
        // Trim trailing zeros but keep at least one decimal digit
        s = s.replaceAll("0+$", "");
        if (s.endsWith(".")) {
            s += "0";
        }
        return s;
    }
}

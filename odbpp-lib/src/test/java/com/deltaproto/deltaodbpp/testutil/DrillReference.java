package com.deltaproto.deltaodbpp.testutil;

import com.deltaproto.deltagerber.model.drill.DrillDocument;
import com.deltaproto.deltagerber.model.drill.DrillHit;
import com.deltaproto.deltagerber.model.drill.DrillOperation;
import com.deltaproto.deltagerber.model.drill.DrillSlot;
import com.deltaproto.deltagerber.model.gerber.GerberDocument;
import com.deltaproto.deltagerber.model.gerber.Unit;
import com.deltaproto.deltagerber.model.gerber.aperture.CircleAperture;
import com.deltaproto.deltagerber.model.gerber.aperture.ObroundAperture;
import com.deltaproto.deltagerber.model.gerber.operation.Arc;
import com.deltaproto.deltagerber.model.gerber.operation.Draw;
import com.deltaproto.deltagerber.model.gerber.operation.Flash;
import com.deltaproto.deltagerber.model.gerber.operation.GraphicsObject;
import com.deltaproto.deltagerber.parser.ExcellonParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads drill references of every dialect the EDA tools write into one list of operations in
 * millimetres, and matches two such lists. Used to check the converter's Excellon output
 * against the tool's own drill files.
 *
 * <p>Beyond what delta-gerber's parsers do on their own, this handles:
 * <ul>
 *   <li>Cadence "non-enhanced" Excellon without {@code M48} or a tool table: the number format
 *       lives in the sidecar {@code nc_param.txt}, which is turned into a header
 *       ({@link #prepare});</li>
 *   <li>files that declare no number format at all but write fixed-width coordinates
 *       (Cadence 2.5, 3.5 and 4.4 metric, 3.5 inch): the format is inferred from the digit
 *       count and chosen by comparing the resulting extent with the generated drill data
 *       ({@link ExcellonReader});</li>
 *   <li>PADS files that re-state the tool definition in the body ({@code T1C.00984F0S0}), which
 *       the parser would read as a definition and not a selection, including files whose
 *       header was cut off and that start at {@code %};</li>
 *   <li>Altium X2 drill Gerbers that flash each slot once with an obround aperture
 *       ({@code %ADD230O,12.0X4.5*%}), honouring {@code %LR} rotation;</li>
 *   <li>plating: a hole matches only a hole of the same plating when both files state it
 *       (by name, {@code ;TYPE=} comment or X2 file function);</li>
 *   <li>routed paths ({@code M15 … G01 × n … M16}): one segment is a slot, several form a
 *       contour (a cut-out or a cutter-compensated rectangular hole), which is compared by its
 *       bounding box with a tool-radius slack because {@code G41/G42} compensation is applied
 *       by the machine, not written into the path.</li>
 * </ul>
 */
public final class DrillReference {

    /** Coordinate and diameter tolerance for references written with four decimals. */
    public static final double TOLERANCE_MM = 0.0015;
    /** Extra slack on contour bounding boxes: routing kerf allowances are a few hundredths. */
    public static final double CONTOUR_SLACK_MM = 0.05;
    /** Two consecutive route segments belong to one path when they share an end point. */
    private static final double CHAIN_TOLERANCE_MM = 1e-6;

    private DrillReference() {
    }

    public enum Kind { HIT, SLOT, CONTOUR }

    /**
     * One drill operation. A hit has {@code x2 == x1, y2 == y1}; a slot is normalised so the
     * smaller end point comes first; a contour keeps its bounding box and segment count.
     */
    public record Op(Kind kind, double diameter, double x1, double y1, double x2, double y2,
                     double[] bbox, int segments, String source, Boolean plated) {

        public Op(Kind kind, double diameter, double x1, double y1, double x2, double y2, double[] bbox, int segments) {
            this(kind, diameter, x1, y1, x2, y2, bbox, segments, null, null);
        }

        public static Op hit(double d, double x, double y) {
            return new Op(Kind.HIT, d, x, y, x, y, new double[] {x - d / 2, y - d / 2, x + d / 2, y + d / 2}, 0);
        }

        public static Op slot(double d, double x1, double y1, double x2, double y2) {
            if (x1 > x2 || (x1 == x2 && y1 > y2)) {
                double tx = x1, ty = y1;
                x1 = x2;
                y1 = y2;
                x2 = tx;
                y2 = ty;
            }
            double[] bbox = {Math.min(x1, x2) - d / 2, Math.min(y1, y2) - d / 2,
                             Math.max(x1, x2) + d / 2, Math.max(y1, y2) + d / 2};
            return new Op(Kind.SLOT, d, x1, y1, x2, y2, bbox, 1);
        }

        public static Op contour(double d, double[] bbox, int segments) {
            return new Op(Kind.CONTOUR, d, bbox[0], bbox[1], bbox[2], bbox[3], bbox, segments);
        }

        public Op translate(double dx, double dy) {
            return new Op(kind, diameter, x1 + dx, y1 + dy, x2 + dx, y2 + dy,
                    new double[] {bbox[0] + dx, bbox[1] + dy, bbox[2] + dx, bbox[3] + dy}, segments, source, plated);
        }

        /** The same operation tagged with the file it came from and that file's plating. */
        public Op from(String source, Boolean plated) {
            return new Op(kind, diameter, x1, y1, x2, y2, bbox, segments, source, plated);
        }

        public String describe() {
            String geometry = switch (kind) {
                case HIT -> String.format(Locale.ROOT, "hit d=%.3f (%.3f,%.3f)", diameter, x1, y1);
                case SLOT -> String.format(Locale.ROOT, "slot d=%.3f (%.3f,%.3f)-(%.3f,%.3f)",
                        diameter, x1, y1, x2, y2);
                case CONTOUR -> String.format(Locale.ROOT, "contour tool=%.3f %d segs bbox [%.3f %.3f %.3f %.3f]",
                        diameter, segments, bbox[0], bbox[1], bbox[2], bbox[3]);
            };
            String plating = plated == null ? "" : plated ? " plated" : " non-plated";
            return geometry + plating + (source == null ? "" : " [" + source + "]");
        }
    }

    /** Every operation tagged with its source file and that file's plating (see {@link #platingOf}). */
    public static List<Op> tagged(List<Op> ops, String source, Boolean plated) {
        return ops.stream().map(o -> o.from(source, plated)).toList();
    }

    private static final Pattern NON_PLATED_NAME = Pattern.compile(
            "non[-_ ]?plated|npth|-np\\.|unplated|\\.uth$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLATED_NAME = Pattern.compile("plated|pth|\\.psh$", Pattern.CASE_INSENSITIVE);

    /**
     * Whether a drill file holds plated holes, from its name (Altium {@code -NonPlated.TXT},
     * KiCad {@code -NPTH.drl}, Cadence {@code -np.drl}, Mentor {@code ThruHoleNonPlated.ncd},
     * PADS {@code drill_nonplated.drl}, {@code .uth}/{@code .pth}/{@code .psh}) or, for an X2
     * drill Gerber, its file function; null when the name says nothing.
     */
    public static Boolean platingOf(String fileName, GerberDocument x2) {
        if (x2 != null && x2.getFileFunctionValues() != null && !x2.getFileFunctionValues().isEmpty()) {
            String kind = x2.getFileFunctionValues().get(0);
            if (kind.equalsIgnoreCase("NonPlated")) {
                return false;
            }
            if (kind.equalsIgnoreCase("Plated")) {
                return true;
            }
        }
        if (NON_PLATED_NAME.matcher(fileName).find()) {
            return false;
        }
        if (PLATED_NAME.matcher(fileName).find()) {
            return true;
        }
        return null;
    }

    // ------------------------------------------------------------------ Excellon dialects

    private static final Pattern NC_PARAM = Pattern.compile("^\\s*([A-Z_-]+)\\s+(\\S+)", Pattern.MULTILINE);
    private static final Pattern BODY_TOOL_DEF = Pattern.compile("^T(\\d+)C([\\d.]+)(?:[FSB]\\d*)*$");
    private static final Pattern M48 = Pattern.compile("^M48\\s*$", Pattern.MULTILINE);
    private static final Pattern EXPLICIT_FORMAT = Pattern.compile(
            "FILE_FORMAT\\s*=\\s*\\d:\\d|^%?\\d\\.\\d\\s*$|[XY][+-]?\\d*\\.\\d", Pattern.MULTILINE);
    private static final Pattern UNIT_LINE = Pattern.compile("^(METRIC|INCH)(?:,(LZ|TZ))?\\s*$", Pattern.MULTILINE);
    private static final Pattern HOLESIZE_UNIT = Pattern.compile("Holesize.*?(PLATED|NON_PLATED)\\s+(MM|INCH)");
    private static final Pattern COORD_WORD = Pattern.compile("[XY]([+-]?)(\\d+)(?![.\\d])");

    /** Parses an Excellon file, with the Cadence {@code nc_param.txt} text when there is one. */
    public static DrillDocument parseExcellon(String content, String ncParam) {
        return new ExcellonParser().parse(prepare(content, ncParam));
    }

    /**
     * Rewrites a drill file into what delta-gerber's parser understands: a format header
     * synthesised from {@code nc_param.txt} for a headerless Cadence file, a tool table
     * synthesised from body tool definitions for a header-less PADS file, and body tool
     * re-definitions turned into tool selections.
     */
    static String prepare(String content, String ncParam) {
        String text = content.startsWith("\uFEFF") ? content.substring(1) : content;
        boolean hasHeader = M48.matcher(text).find();
        StringBuilder out = new StringBuilder();
        if (!hasHeader && ncParam != null) {
            Map<String, String> p = new HashMap<>();
            Matcher m = NC_PARAM.matcher(ncParam);
            while (m.find()) {
                p.put(m.group(1), m.group(2));
            }
            String intPlaces = p.get("INTEGER-PLACES");
            String decPlaces = p.get("DECIMAL-PLACES");
            if (intPlaces != null && decPlaces != null) {
                out.append(";FILE_FORMAT=").append(intPlaces).append(':').append(decPlaces).append('\n');
            }
            String units = p.getOrDefault("OUTPUT-UNITS", "METRIC").toUpperCase(Locale.ROOT);
            // Leading zeros suppressed -> trailing zeros are what remain (TZ) and vice versa.
            // With neither suppressed the digits are complete and either reading is right.
            boolean suppressLead = "YES".equalsIgnoreCase(p.getOrDefault("SUPPRESS-LEAD-ZEROES", "NO"));
            out.append(units.startsWith("METRIC") ? "METRIC" : "INCH")
                    .append(suppressLead ? ",TZ" : ",LZ").append('\n');
        } else if (!hasHeader && !text.contains("Holesize")) {
            // Header cut off: rebuild the tool table from the body's own definitions.
            Map<Integer, String> defs = new LinkedHashMap<>();
            double maxDiameter = 0;
            for (String line : text.split("\n")) {
                Matcher m = BODY_TOOL_DEF.matcher(line.trim());
                if (m.matches()) {
                    defs.putIfAbsent(Integer.parseInt(m.group(1)), m.group(2));
                    maxDiameter = Math.max(maxDiameter, Double.parseDouble(m.group(2)));
                }
            }
            if (!defs.isEmpty()) {
                out.append("M48\n");
                if (!UNIT_LINE.matcher(text).find()) {
                    // Drill diameters under 0.3 are inches: no board uses a 0.3 mm maximum drill.
                    out.append(maxDiameter < 0.3 ? "INCH" : "METRIC").append('\n');
                }
                for (Map.Entry<Integer, String> e : defs.entrySet()) {
                    out.append('T').append(e.getKey()).append('C').append(e.getValue()).append('\n');
                }
                out.append("%\n");
            }
        }
        boolean inBody = !hasHeader;
        for (String line : text.split("\n")) {
            String s = line.trim();
            if (inBody) {
                Matcher m = BODY_TOOL_DEF.matcher(s);
                if (m.matches()) {
                    out.append('T').append(m.group(1)).append('\n');
                    continue;
                }
            } else if (s.equals("%") || s.startsWith("M95")) {
                inBody = true;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /** Whether the file states its number format (or writes decimal points). */
    static boolean hasExplicitFormat(String content) {
        return EXPLICIT_FORMAT.matcher(content).find();
    }

    /** A candidate number format for a file that declares none. */
    public record Format(int integerDigits, int decimalDigits, boolean leadingZeros) {
        @Override
        public String toString() {
            return integerDigits + "." + decimalDigits + (leadingZeros ? " LZ" : " TZ");
        }
    }

    /**
     * Number formats a fixed-width file might be in: the widest coordinate word gives the
     * digit count, and the integer part may be two, three or four digits. When word widths
     * vary, one kind of zero is suppressed, and both readings are candidates.
     */
    static List<Format> formatCandidates(String content) {
        int max = 0;
        int min = Integer.MAX_VALUE;
        Matcher m = COORD_WORD.matcher(content);
        while (m.find()) {
            int n = m.group(2).length();
            max = Math.max(max, n);
            min = Math.min(min, n);
        }
        List<Format> out = new ArrayList<>();
        if (max < 6) {
            return out;
        }
        Matcher u = UNIT_LINE.matcher(content);
        String declaredZeros = u.find() ? u.group(2) : null;
        List<Boolean> zeroModes = declaredZeros != null ? List.of(declaredZeros.equals("LZ"))
                : min == max ? List.of(true) : List.of(true, false);
        for (int integer : new int[] {2, 3, 4}) {
            if (max - integer >= 2) {
                for (boolean lz : zeroModes) {
                    out.add(new Format(integer, max - integer, lz));
                }
            }
        }
        return out;
    }

    /** The unit the file declares, in its header or its Cadence {@code Holesize} comments. */
    private static String declaredUnit(String content) {
        Matcher u = UNIT_LINE.matcher(content);
        if (u.find()) {
            return u.group(1);
        }
        Matcher h = HOLESIZE_UNIT.matcher(content);
        if (h.find()) {
            return h.group(2).equals("MM") ? "METRIC" : "INCH";
        }
        return "METRIC";
    }

    /**
     * Reads the Excellon references of one set. A file that declares its format is parsed as
     * is; a fixed-width file without one is parsed under every candidate format and the one
     * whose drill extent matches the generated drill extent wins. The winning format is kept
     * for sibling files of the same set that hold too few hits to judge (route files).
     */
    public static final class ExcellonReader {
        private final double[] expectedSpan;
        private Format remembered;
        private final List<String> notes = new ArrayList<>();

        /** @param expectedSpan {width, height} of the generated drill operations, or null */
        public ExcellonReader(double[] expectedSpan) {
            this.expectedSpan = expectedSpan;
        }

        public List<String> notes() {
            return notes;
        }

        public DrillDocument parse(String fileName, String content, String ncParam) {
            String text = prepare(content, ncParam);
            if (ncParam != null && !M48.matcher(content).find() || hasExplicitFormat(text)) {
                return new ExcellonParser().parse(text);
            }
            List<Format> candidates = formatCandidates(text);
            if (candidates.isEmpty()) {
                return new ExcellonParser().parse(text);
            }
            String unit = declaredUnit(text);
            Format best = null;
            DrillDocument bestDoc = null;
            double bestScore = Double.MAX_VALUE;
            for (Format f : candidates) {
                DrillDocument doc = new ExcellonParser().parse(withFormat(text, f, unit));
                double[] span = span(ops(doc));
                if (expectedSpan == null || span == null || doc.getOperations().size() < 10) {
                    continue;
                }
                double score = Math.abs(Math.log((span[0] + 0.01) / (expectedSpan[0] + 0.01)))
                        + Math.abs(Math.log((span[1] + 0.01) / (expectedSpan[1] + 0.01)));
                if (score < bestScore) {
                    bestScore = score;
                    best = f;
                    bestDoc = doc;
                }
            }
            if (best != null && bestScore < 0.5) {
                remembered = best;
                notes.add(fileName + ": no format declared; " + best + " fits the generated extent");
                return bestDoc;
            }
            Format fallback = remembered != null ? remembered
                    : unit.equals("INCH") ? new Format(2, candidates.get(0).integerDigits + candidates.get(0).decimalDigits - 2, true)
                    : new Format(3, candidates.get(0).integerDigits + candidates.get(0).decimalDigits - 3, true);
            notes.add(fileName + ": no format declared; assuming " + fallback
                    + (remembered != null ? " (from a sibling file)" : " (convention)"));
            return new ExcellonParser().parse(withFormat(text, fallback, unit));
        }

        private static String withFormat(String text, Format f, String unit) {
            return ";FILE_FORMAT=" + f.integerDigits + ":" + f.decimalDigits + "\n"
                    + unit + (f.leadingZeros ? ",LZ" : ",TZ") + "\n" + text;
        }
    }

    /**
     * Half the coordinate resolution a reference was written with, plus the base tolerance:
     * inch 2:4 files quantise to 2.54 µm, Altium's metric 4:2 files to 10 µm.
     */
    public static double tolerance(DrillDocument doc) {
        double resolution;
        if (doc.hasDecimalPointCoordinates()) {
            resolution = 0.001;
        } else {
            double unit = doc.getSourceUnit() == Unit.INCH ? 25.4 : 1.0;
            resolution = unit * Math.pow(10, -doc.getDecimalDigits());
        }
        return TOLERANCE_MM + resolution / 2;
    }

    // ------------------------------------------------------------------ operations

    /** Hits, slots and routed contours of a parsed drill file. */
    public static List<Op> ops(DrillDocument doc) {
        List<Op> ops = new ArrayList<>();
        Chain chain = null;
        for (DrillOperation op : doc.getOperations()) {
            double d = op.getTool() == null ? 0 : op.getTool().getDiameter();
            if (op instanceof DrillHit hit) {
                if (chain != null) {
                    ops.add(chain.toOp(false));
                    chain = null;
                }
                ops.add(Op.hit(d, hit.getX(), hit.getY()));
            } else if (op instanceof DrillSlot slot) {
                if (chain == null || !chain.continues(d, slot.getStartX(), slot.getStartY())) {
                    if (chain != null) {
                        ops.add(chain.toOp(false));
                    }
                    chain = new Chain(d, slot.getStartX(), slot.getStartY());
                }
                chain.add(slot.getEndX(), slot.getEndY());
            }
        }
        if (chain != null) {
            ops.add(chain.toOp(false));
        }
        return ops;
    }

    /**
     * Drill operations of an X2 drill Gerber: circle flashes are hits, obround flashes and
     * circle draws are slots.
     */
    public static List<Op> ops(GerberDocument doc) {
        List<Op> ops = new ArrayList<>();
        for (GraphicsObject obj : doc.getObjects()) {
            if (obj instanceof Flash flash && flash.getAperture() instanceof CircleAperture c) {
                ops.add(Op.hit(c.getDiameter(), flash.getX(), flash.getY()));
            } else if (obj instanceof Flash flash && flash.getAperture() instanceof ObroundAperture o) {
                ops.add(obroundSlot(flash.getX(), flash.getY(), o.getWidth(), o.getHeight(), flash.getRotation()));
            } else if (obj instanceof Draw draw && draw.getAperture() instanceof CircleAperture c) {
                ops.add(Op.slot(c.getDiameter(), draw.getStartX(), draw.getStartY(), draw.getEndX(), draw.getEndY()));
            }
        }
        return ops;
    }

    /**
     * An obround flash of {@code w × h}: a slot whose hole is the smaller dimension, running
     * along the larger one, rotated by {@code rotationDeg} (counter-clockwise, {@code %LR}).
     * A circular obround is a hit.
     */
    static Op obroundSlot(double x, double y, double w, double h, double rotationDeg) {
        if (Math.abs(w - h) <= TOLERANCE_MM) {
            return Op.hit(Math.min(w, h), x, y);
        }
        double d = Math.min(w, h);
        double half = Math.abs(w - h) / 2;
        double lx = w > h ? half : 0;
        double ly = w > h ? 0 : half;
        double rad = Math.toRadians(rotationDeg);
        double dx = lx * Math.cos(rad) - ly * Math.sin(rad);
        double dy = lx * Math.sin(rad) + ly * Math.cos(rad);
        return Op.slot(d, x - dx, y - dy, x + dx, y + dy);
    }

    /**
     * Paths drawn in a Gerber (the converter's {@code Rout.gbr} and outline) as contours: chains
     * of draws and arcs with a round aperture that share end points. Every chain is a contour,
     * a single segment included, since what a rout layer draws is milled, not drilled.
     */
    public static List<Op> contoursOf(GerberDocument doc) {
        List<Op> ops = new ArrayList<>();
        Chain chain = null;
        for (GraphicsObject obj : doc.getObjects()) {
            double d;
            double sx, sy, ex, ey;
            double[] extra = null;
            if (obj instanceof Draw draw && draw.getAperture() instanceof CircleAperture c) {
                d = c.getDiameter();
                sx = draw.getStartX();
                sy = draw.getStartY();
                ex = draw.getEndX();
                ey = draw.getEndY();
            } else if (obj instanceof Arc arc && arc.getAperture() instanceof CircleAperture c) {
                d = c.getDiameter();
                sx = arc.getStartX();
                sy = arc.getStartY();
                ex = arc.getEndX();
                ey = arc.getEndY();
                extra = arcExtremes(arc);
            } else {
                continue;
            }
            if (chain == null || !chain.continues(d, sx, sy)) {
                if (chain != null) {
                    ops.add(chain.toOp(true));
                }
                chain = new Chain(d, sx, sy);
            }
            if (extra != null) {
                chain.extend(extra);
            }
            chain.add(ex, ey);
        }
        if (chain != null) {
            ops.add(chain.toOp(true));
        }
        return ops;
    }

    /** Points of the arc's path lying on its bounding circle's axes, for a tight bbox. */
    private static double[] arcExtremes(Arc arc) {
        double cx = arc.getCenterX();
        double cy = arc.getCenterY();
        double r = arc.getRadius();
        double a0 = Math.atan2(arc.getStartY() - cy, arc.getStartX() - cx);
        double a1 = Math.atan2(arc.getEndY() - cy, arc.getEndX() - cx);
        boolean full = Math.hypot(arc.getStartX() - arc.getEndX(), arc.getStartY() - arc.getEndY()) < CHAIN_TOLERANCE_MM;
        double sweep = arc.isClockwise() ? a0 - a1 : a1 - a0;
        if (sweep <= 0) {
            sweep += 2 * Math.PI;
        }
        if (full) {
            sweep = 2 * Math.PI;
        }
        List<Double> pts = new ArrayList<>();
        for (int q = 0; q < 4; q++) {
            double axis = q * Math.PI / 2;
            double rel = arc.isClockwise() ? a0 - axis : axis - a0;
            rel = ((rel % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
            if (rel <= sweep) {
                pts.add(cx + r * Math.cos(axis));
                pts.add(cy + r * Math.sin(axis));
            }
        }
        double[] out = new double[pts.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = pts.get(i);
        }
        return out;
    }

    /** A routed path being assembled from consecutive segments. */
    private static final class Chain {
        final double diameter;
        double lastX, lastY;
        final double startX, startY;
        int segments;
        final double[] bbox;

        Chain(double diameter, double x, double y) {
            this.diameter = diameter;
            this.startX = this.lastX = x;
            this.startY = this.lastY = y;
            this.bbox = new double[] {x, y, x, y};
        }

        boolean continues(double d, double x, double y) {
            return Math.abs(d - diameter) <= TOLERANCE_MM
                    && Math.abs(x - lastX) <= CHAIN_TOLERANCE_MM && Math.abs(y - lastY) <= CHAIN_TOLERANCE_MM;
        }

        void add(double x, double y) {
            extend(new double[] {x, y});
            lastX = x;
            lastY = y;
            segments++;
        }

        void extend(double[] xy) {
            for (int i = 0; i + 1 < xy.length; i += 2) {
                bbox[0] = Math.min(bbox[0], xy[i]);
                bbox[1] = Math.min(bbox[1], xy[i + 1]);
                bbox[2] = Math.max(bbox[2], xy[i]);
                bbox[3] = Math.max(bbox[3], xy[i + 1]);
            }
        }

        Op toOp(boolean alwaysContour) {
            if (segments == 1 && !alwaysContour) {
                return Op.slot(diameter, startX, startY, lastX, lastY);
            }
            return Op.contour(diameter, bbox, segments);
        }
    }

    // ------------------------------------------------------------------ matching

    /** {width, height} of the hits and slots in a list, or null when there are none. */
    public static double[] span(List<Op> ops) {
        double[] b = bbox(ops);
        return b == null ? null : new double[] {b[2] - b[0], b[3] - b[1]};
    }

    /** Bounding box {minX, minY, maxX, maxY} of the hit and slot centres, or null. */
    public static double[] bbox(List<Op> ops) {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        int n = 0;
        for (Op o : ops) {
            if (o.kind == Kind.CONTOUR) {
                continue;
            }
            b[0] = Math.min(b[0], Math.min(o.x1, o.x2));
            b[1] = Math.min(b[1], Math.min(o.y1, o.y2));
            b[2] = Math.max(b[2], Math.max(o.x1, o.x2));
            b[3] = Math.max(b[3], Math.max(o.y1, o.y2));
            n++;
        }
        return n == 0 ? null : b;
    }

    /** Diameter histogram, for diagnostics: diameter (3 decimals) to count. */
    public static Map<Double, Integer> diameters(List<Op> ops) {
        Map<Double, Integer> out = new TreeMap<>();
        for (Op o : ops) {
            if (o.kind != Kind.CONTOUR) {
                out.merge(Math.round(o.diameter * 1000) / 1000.0, 1, Integer::sum);
            }
        }
        return out;
    }

    /** A translation between the reference and the generated coordinates. */
    public record Offset(double dx, double dy, int supporting, int total) {
        /** Whether the shift exceeds the comparison tolerance (below it, it is quantisation noise). */
        public boolean isSignificant(double tolerance) {
            return Math.hypot(dx, dy) > tolerance;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "(%.4f, %.4f) backed by %d/%d hits", dx, dy, supporting, total);
        }
    }

    /**
     * Estimates the translation that maps the reference hits onto ours, from the hits of the
     * most common reference diameter: starting from no shift and from the shifts that align
     * the two bounding boxes (centre and corners), each reference hit is paired with its
     * nearest generated hit and the median pair offset taken; the start with the most hits
     * then coinciding wins. Accepted only when most of those hits coincide; otherwise the
     * offset is zero.
     */
    public static Offset estimateOffset(List<Op> ref, List<Op> ours, double tolerance) {
        Map<Long, Integer> count = new HashMap<>();
        for (Op op : ref) {
            if (op.kind == Kind.HIT) {
                count.merge(Math.round(op.diameter * 1000), 1, Integer::sum);
            }
        }
        if (count.isEmpty()) {
            return new Offset(0, 0, 0, 0);
        }
        long key = count.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
        List<Op> r = ref.stream().filter(o -> o.kind == Kind.HIT && Math.round(o.diameter * 1000) == key).toList();
        double diameter = r.get(0).diameter;
        List<Op> o = ours.stream().filter(x -> x.kind == Kind.HIT && Math.abs(x.diameter - diameter) <= tolerance).toList();
        if (o.isEmpty()) {
            return new Offset(0, 0, 0, r.size());
        }
        // Starting shifts: none, and the ones that align the bounding boxes' centres and
        // corners. On a periodic via grid a few extra holes on one side move the centre by
        // whole pitches, so the corners are tried too; the most-supported result wins.
        double[] rb = bbox(r);
        double[] ob = bbox(o);
        double[][] starts = {
                {0, 0},
                {(ob[0] + ob[2]) / 2 - (rb[0] + rb[2]) / 2, (ob[1] + ob[3]) / 2 - (rb[1] + rb[3]) / 2},
                {ob[0] - rb[0], ob[1] - rb[1]},
                {ob[2] - rb[2], ob[3] - rb[3]},
                {ob[0] - rb[0], ob[3] - rb[3]},
                {ob[2] - rb[2], ob[1] - rb[1]}};
        Offset best = new Offset(0, 0, 0, r.size());
        for (double[] start : starts) {
            double[] dxs = new double[r.size()];
            double[] dys = new double[r.size()];
            for (int i = 0; i < r.size(); i++) {
                Op a = r.get(i);
                Op near = null;
                double nearD = Double.MAX_VALUE;
                for (Op b : o) {
                    double dist = Math.hypot(b.x1 - a.x1 - start[0], b.y1 - a.y1 - start[1]);
                    if (dist < nearD) {
                        nearD = dist;
                        near = b;
                    }
                }
                dxs[i] = near.x1 - a.x1;
                dys[i] = near.y1 - a.y1;
            }
            double dx = median(dxs);
            double dy = median(dys);
            int supporting = 0;
            for (int i = 0; i < r.size(); i++) {
                if (Math.abs(dxs[i] - dx) <= tolerance && Math.abs(dys[i] - dy) <= tolerance) {
                    supporting++;
                }
            }
            if (supporting > best.supporting) {
                best = new Offset(dx, dy, supporting, r.size());
            }
        }
        if (best.supporting * 2 < r.size()) {
            return new Offset(0, 0, best.supporting, r.size());
        }
        return best;
    }

    private static double median(double[] values) {
        double[] s = values.clone();
        Arrays.sort(s);
        int n = s.length;
        return n % 2 == 1 ? s[n / 2] : (s[n / 2 - 1] + s[n / 2]) / 2;
    }

    /** Outcome of {@link #match}. */
    public record Match(List<Op> unmatchedRef, List<Op> unmatchedOurs, int matchedHits, int matchedSlots,
                        int matchedContours) {
        public boolean isExact() {
            return unmatchedRef.isEmpty() && unmatchedOurs.isEmpty();
        }
    }

    /**
     * Matches reference operations to generated ones: hits by position and diameter, slots by
     * end points and width, contours by bounding box with a tool-radius slack (against either
     * a generated contour or a generated slot, since a cutter-compensated rectangle routed
     * around a hole is the ODB++ slot of that hole). A reference slot that has no generated
     * slot may still be a path the converter wrote to its rout layer, so it is also compared
     * against generated contours by bounding box.
     */
    public static Match match(List<Op> ref, List<Op> ours, double tolerance) {
        List<Op> remaining = new ArrayList<>(ours);
        List<Op> unmatched = new ArrayList<>();
        int hits = 0, slots = 0, contours = 0;
        for (Op r : ref) {
            int found = -1;
            for (int i = 0; i < remaining.size() && found < 0; i++) {
                if (matches(r, remaining.get(i), tolerance, false)) {
                    found = i;
                }
            }
            if (found < 0 && r.kind == Kind.SLOT) {
                for (int i = 0; i < remaining.size() && found < 0; i++) {
                    if (matches(r, remaining.get(i), tolerance, true)) {
                        found = i;
                    }
                }
            }
            if (found < 0) {
                unmatched.add(r);
            } else {
                remaining.remove(found);
                switch (r.kind) {
                    case HIT -> hits++;
                    case SLOT -> slots++;
                    case CONTOUR -> contours++;
                }
            }
        }
        return new Match(unmatched, remaining, hits, slots, contours);
    }

    private static boolean matches(Op r, Op o, double tolerance, boolean slotAsContour) {
        if (r.kind == Kind.CONTOUR || slotAsContour) {
            if (o.kind == Kind.HIT) {
                return false;
            }
            double slack = r.diameter / 2 + CONTOUR_SLACK_MM + tolerance;
            for (int i = 0; i < 4; i++) {
                if (Math.abs(r.bbox[i] - o.bbox[i]) > slack) {
                    return false;
                }
            }
            return true;
        }
        if (r.kind != o.kind || Math.abs(r.diameter - o.diameter) > tolerance) {
            return false;
        }
        if (r.plated != null && o.plated != null && !r.plated.equals(o.plated)) {
            return false;
        }
        return Math.abs(r.x1 - o.x1) <= tolerance && Math.abs(r.y1 - o.y1) <= tolerance
                && Math.abs(r.x2 - o.x2) <= tolerance && Math.abs(r.y2 - o.y2) <= tolerance;
    }

    /**
     * For each generated file, how many of its operations matched operations of each
     * reference file: {@code PTH-L1-L2.drl -> {Plated.TX3=76}}. Shows which reference file a
     * generated plated/non-plated/span file corresponds to.
     */
    public static Map<String, Map<String, Integer>> sourceMapping(List<Op> ref, List<Op> ours, double tolerance) {
        Map<String, Map<String, Integer>> out = new TreeMap<>();
        List<Op> remaining = new ArrayList<>(ours);
        for (Op r : ref) {
            for (int i = 0; i < remaining.size(); i++) {
                Op o = remaining.get(i);
                if (matches(r, o, tolerance, false) || r.kind == Kind.SLOT && matches(r, o, tolerance, true)) {
                    if (o.source != null) {
                        out.computeIfAbsent(o.source, k -> new TreeMap<>())
                                .merge(r.source == null ? "?" : r.source, 1, Integer::sum);
                    }
                    remaining.remove(i);
                    break;
                }
            }
        }
        return out;
    }

    /** The generated operation closest to a reference one, for diagnostics. */
    public static String nearest(Op r, List<Op> candidates) {
        Op best = null;
        double bestD = Double.MAX_VALUE;
        for (Op o : candidates) {
            double dist = Math.hypot(o.x1 - r.x1, o.y1 - r.y1) + Math.hypot(o.x2 - r.x2, o.y2 - r.y2);
            if (dist < bestD) {
                bestD = dist;
                best = o;
            }
        }
        return best == null ? "nothing" : best.describe() + String.format(Locale.ROOT, " (%.3f mm away)", bestD / 2);
    }
}

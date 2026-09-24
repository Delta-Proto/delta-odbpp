package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.ContourPolygon;
import com.deltaproto.deltaodbpp.model.EdaData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Parses the {@code steps/<step>/eda/data} file.
 *
 * <p>Handles {@code NET} records (name only) and {@code PKG} records. A package
 * record carries the component's <em>bounding box</em> in the package-local frame
 * (the placement origin is at {@code 0,0}):
 *
 * <pre>PKG &lt;name&gt; &lt;pitch&gt; &lt;xmin&gt; &lt;ymin&gt; &lt;xmax&gt; &lt;ymax&gt;[;&lt;attrs&gt;]</pre>
 *
 * <p>followed by the package <em>outline</em>, one of
 *
 * <pre>
 * RC &lt;xl&gt; &lt;yl&gt; &lt;w&gt; &lt;h&gt;      rectangle, lower-left corner + size
 * CR &lt;xc&gt; &lt;yc&gt; &lt;r&gt;          circle
 * SQ &lt;xc&gt; &lt;yc&gt; &lt;half_side&gt;  square
 * CT … OB/OS/OC/OE … CE      free contour (islands and holes)
 * </pre>
 *
 * <p>Only the outline between the {@code PKG} line and its first {@code PIN} is the
 * package's own; each pin has an outline of its own in the same forms, which this parser
 * skips. The outline is kept as {@link ContourPolygon}s whatever form it was written in.
 *
 * <p>Coordinates are normalised to millimetres using the step's unit scale (ODB++
 * defaults to INCH). A {@code UNITS=} directive inside the eda/data file itself,
 * when present, overrides that default.
 */
public class EdaDataParser {

    /** Back-compat entry point; assumes INCH units unless the file declares otherwise. */
    public EdaData parse(Path dataFile) throws IOException {
        return parse(dataFile, 25.4);
    }

    /**
     * @param mmScale multiplier that converts the step's native units to millimetres
     *                (1.0 when the step is already in MM, 25.4 for INCH). Overridden by a
     *                {@code UNITS=} directive in the file if one is present.
     */
    public EdaData parse(Path dataFile, double mmScale) throws IOException {
        return parse(Files.readAllLines(dataFile), mmScale);
    }

    /** Parse already-read lines; see {@link #parse(Path, double)}. */
    public EdaData parse(List<String> rawLines, double mmScale) throws IOException {
        EdaData edaData = new EdaData();
        edaData.setNetRecords(new ArrayList<>());
        edaData.setPackageRecords(new ArrayList<>());
        edaData.setNetRecordsByName(new HashMap<>());
        edaData.setPackageRecordsByName(new HashMap<>());

        double scale = mmScale;
        int netIndex = 0;
        int pkgIndex = 0;

        // The package whose outline records we are still inside: set by PKG, cleared by the
        // first PIN (whose own outline must not be mistaken for the body's) or the next record.
        EdaData.PackageRecord outlineTarget = null;

        for (int i = 0; i < rawLines.size(); i++) {
            String line = rawLines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("UNITS=")) {
                String units = line.substring("UNITS=".length()).trim();
                if ("MM".equalsIgnoreCase(units)) {
                    scale = 1.0;
                } else if ("INCH".equalsIgnoreCase(units)) {
                    scale = 25.4;
                }
                continue;
            }
            if (line.startsWith("NET ")) {
                outlineTarget = null;
                EdaData.NetRecord net = new EdaData.NetRecord();
                net.setName(field(line, 1));
                net.setIndex(netIndex++);
                edaData.getNetRecords().add(net);
                edaData.getNetRecordsByName().put(net.getName(), net);
            } else if (line.startsWith("PKG ")) {
                EdaData.PackageRecord pkg = parsePackage(line, scale, pkgIndex++);
                edaData.getPackageRecords().add(pkg);
                edaData.getPackageRecordsByName().put(pkg.getName(), pkg);
                outlineTarget = pkg;
            } else if (line.startsWith("PIN ")) {
                outlineTarget = null;
            } else if (outlineTarget != null) {
                if (line.startsWith("RC ")) {
                    ContourPolygon rc = rectangle(line, scale);
                    if (rc != null) outlineTarget.getOutline().add(rc);
                } else if (line.startsWith("CR ")) {
                    ContourPolygon cr = circle(line, scale);
                    if (cr != null) outlineTarget.getOutline().add(cr);
                } else if (line.startsWith("SQ ")) {
                    ContourPolygon sq = square(line, scale);
                    if (sq != null) outlineTarget.getOutline().add(sq);
                } else if (line.equals("CT")) {
                    i = parseContour(rawLines, i, scale, outlineTarget.getOutline());
                }
            }
        }
        return edaData;
    }

    /**
     * Parse a {@code PKG} record. The full form carries pitch and a bounding box; a
     * bare {@code PKG <name>} (or any short/garbled line) still yields a named record
     * with a zero bounding box so callers degrade gracefully.
     */
    private static EdaData.PackageRecord parsePackage(String line, double scale, int index) {
        String[] tok = line.split("\\s+");
        EdaData.PackageRecord pkg = new EdaData.PackageRecord();
        pkg.setName(tok.length > 1 ? tok[1] : "");
        pkg.setIndex(index);
        // PKG <name> <pitch> <xmin> <ymin> <xmax> <ymax>
        if (tok.length >= 7) {
            try {
                pkg.setPitch(num(tok[2]) * scale);
                pkg.setXMin(num(tok[3]) * scale);
                pkg.setYMin(num(tok[4]) * scale);
                pkg.setXMax(num(tok[5]) * scale);
                pkg.setYMax(num(tok[6]) * scale);
            } catch (NumberFormatException ignored) {
                // Leave the geometry at its zero default when a token isn't numeric.
            }
        }
        return pkg;
    }

    /**
     * Read the {@code OB…OE} polygons of a {@code CT … CE} block starting at {@code ctIndex}
     * into {@code into}, and return the index of the closing {@code CE} line (or the last line
     * consumed when the block is unterminated). Malformed polygons are skipped rather than
     * failing the whole file — the outline is decoration, the bounding box the fallback.
     */
    private static int parseContour(List<String> lines, int ctIndex, double scale, List<ContourPolygon> into) {
        int i = ctIndex + 1;
        while (i < lines.size()) {
            String line = lines.get(i).trim();
            if (line.equals("CE")) {
                return i;
            }
            if (line.startsWith("OB")) {
                int end = i;
                while (end < lines.size() && !lines.get(end).trim().startsWith("OE")) {
                    end++;
                }
                try {
                    into.add(polygon(lines, i, end, scale));
                } catch (RuntimeException ignored) {
                    // Skip a garbled polygon; keep what the rest of the block offers.
                }
                i = end;
            }
            i++;
        }
        return lines.size() - 1;
    }

    private static ContourPolygon polygon(List<String> lines, int obIndex, int oeIndex, double scale) {
        String[] ob = lines.get(obIndex).trim().split("\\s+");
        ContourPolygon polygon = new ContourPolygon();
        polygon.setXStart(num(ob[1]) * scale);
        polygon.setYStart(num(ob[2]) * scale);
        polygon.setType(ob.length > 3 ? ContourPolygon.Type.fromString(ob[3]) : ContourPolygon.Type.ISLAND);
        for (int i = obIndex + 1; i < oeIndex; i++) {
            String[] tok = lines.get(i).trim().split("\\s+");
            ContourPolygon.PolygonPart part = new ContourPolygon.PolygonPart();
            if (tok[0].equals("OS") && tok.length >= 3) {
                part.setType(ContourPolygon.PolygonPart.Type.SEGMENT);
                part.setEndX(num(tok[1]) * scale);
                part.setEndY(num(tok[2]) * scale);
            } else if (tok[0].equals("OC") && tok.length >= 6) {
                part.setType(ContourPolygon.PolygonPart.Type.ARC);
                part.setEndX(num(tok[1]) * scale);
                part.setEndY(num(tok[2]) * scale);
                part.setXCenter(num(tok[3]) * scale);
                part.setYCenter(num(tok[4]) * scale);
                part.setClockwise("Y".equalsIgnoreCase(tok[5]));
            } else {
                continue;
            }
            polygon.getPolygonParts().add(part);
        }
        return polygon;
    }

    /** {@code RC <xl> <yl> <w> <h>} → a four-segment island. */
    private static ContourPolygon rectangle(String line, double scale) {
        String[] tok = line.split("\\s+");
        if (tok.length < 5) return null;
        try {
            double x = num(tok[1]) * scale, y = num(tok[2]) * scale;
            double w = num(tok[3]) * scale, h = num(tok[4]) * scale;
            return box(x, y, x + w, y + h);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code SQ <xc> <yc> <half_side>} → a four-segment island. */
    private static ContourPolygon square(String line, double scale) {
        String[] tok = line.split("\\s+");
        if (tok.length < 4) return null;
        try {
            double cx = num(tok[1]) * scale, cy = num(tok[2]) * scale, hs = num(tok[3]) * scale;
            return box(cx - hs, cy - hs, cx + hs, cy + hs);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code CR <xc> <yc> <r>} → an island of two half-circle arcs. */
    private static ContourPolygon circle(String line, double scale) {
        String[] tok = line.split("\\s+");
        if (tok.length < 4) return null;
        try {
            double cx = num(tok[1]) * scale, cy = num(tok[2]) * scale, r = num(tok[3]) * scale;
            ContourPolygon p = new ContourPolygon();
            p.setType(ContourPolygon.Type.ISLAND);
            p.setXStart(cx + r);
            p.setYStart(cy);
            p.getPolygonParts().add(arc(cx - r, cy, cx, cy));
            p.getPolygonParts().add(arc(cx + r, cy, cx, cy));
            return p;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ContourPolygon box(double x0, double y0, double x1, double y1) {
        ContourPolygon p = new ContourPolygon();
        p.setType(ContourPolygon.Type.ISLAND);
        p.setXStart(x0);
        p.setYStart(y0);
        p.getPolygonParts().add(segment(x1, y0));
        p.getPolygonParts().add(segment(x1, y1));
        p.getPolygonParts().add(segment(x0, y1));
        p.getPolygonParts().add(segment(x0, y0));
        return p;
    }

    private static ContourPolygon.PolygonPart segment(double x, double y) {
        ContourPolygon.PolygonPart part = new ContourPolygon.PolygonPart();
        part.setType(ContourPolygon.PolygonPart.Type.SEGMENT);
        part.setEndX(x);
        part.setEndY(y);
        return part;
    }

    private static ContourPolygon.PolygonPart arc(double x, double y, double cx, double cy) {
        ContourPolygon.PolygonPart part = new ContourPolygon.PolygonPart();
        part.setType(ContourPolygon.PolygonPart.Type.ARC);
        part.setEndX(x);
        part.setEndY(y);
        part.setXCenter(cx);
        part.setYCenter(cy);
        part.setClockwise(true);
        return part;
    }

    /** Nth whitespace-delimited field, or "" when absent. */
    private static String field(String line, int i) {
        String[] tok = line.split("\\s+");
        return i < tok.length ? tok[i] : "";
    }

    /** Parse a coordinate token, dropping any trailing {@code ;<attrs>} suffix. */
    private static double num(String token) {
        int semi = token.indexOf(';');
        if (semi >= 0) {
            token = token.substring(0, semi);
        }
        return Double.parseDouble(token);
    }
}

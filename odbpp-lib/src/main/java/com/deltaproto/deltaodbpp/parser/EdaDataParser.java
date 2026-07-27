package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.EdaData;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Parses the {@code steps/<step>/eda/data} file.
 *
 * <p>Handles {@code NET} records (name only) and {@code PKG} records. A package
 * record carries the component's <em>bounding box</em> in the package-local frame
 * (the placement origin is at {@code 0,0}):
 *
 * <pre>PKG &lt;name&gt; &lt;pitch&gt; &lt;xmin&gt; &lt;ymin&gt; &lt;xmax&gt; &lt;ymax&gt;[;&lt;attrs&gt;]</pre>
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
        EdaData edaData = new EdaData();
        edaData.setNetRecords(new ArrayList<>());
        edaData.setPackageRecords(new ArrayList<>());
        edaData.setNetRecordsByName(new HashMap<>());
        edaData.setPackageRecordsByName(new HashMap<>());

        double scale = mmScale;
        int netIndex = 0;
        int pkgIndex = 0;

        try (BufferedReader reader = Files.newBufferedReader(dataFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
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
                    EdaData.NetRecord net = new EdaData.NetRecord();
                    net.setName(field(line, 1));
                    net.setIndex(netIndex++);
                    edaData.getNetRecords().add(net);
                    edaData.getNetRecordsByName().put(net.getName(), net);
                } else if (line.startsWith("PKG ")) {
                    EdaData.PackageRecord pkg = parsePackage(line, scale, pkgIndex++);
                    edaData.getPackageRecords().add(pkg);
                    edaData.getPackageRecordsByName().put(pkg.getName(), pkg);
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

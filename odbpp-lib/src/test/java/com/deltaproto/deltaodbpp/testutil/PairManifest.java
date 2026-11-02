package com.deltaproto.deltaodbpp.testutil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * One ODB++/Gerber pair under {@link Fixtures#ODB_GERBER_PAIRS}: the exploded job in
 * {@code odb/}, the EDA tool's own Gerber/Excellon plot in {@code gerber/} with its original
 * file names, and a {@code reference.properties} manifest that maps the converter's output
 * roles onto those files:
 *
 * <pre>
 * step            = pcb                  # ODB++ step to convert
 * copper.count    = 4
 * copper.1        = Gerber/X.GTL         # relative to gerber/; top = 1
 * soldermask.top  = …    legend.bottom = …    paste.top = …
 * profile         = …
 * drill           = a.TXT, b.TXT         # every drill reference, any dialect
 * rout            = X_1-4.rou            # routed slots and cut-outs
 * cutout          = X_CUTOUT.art         # a Gerber drawing of the cut-outs
 * ncparam         = nc_param.txt         # Cadence number-format sidecar
 * skip            = reason               # the harness skips this set
 * </pre>
 *
 * An optional {@code options.properties} carries converter options for the layer pass.
 */
public final class PairManifest {

    private final Path dir;
    private final Properties props = new Properties();
    private final Properties options = new Properties();

    private PairManifest(Path dir) {
        this.dir = dir;
    }

    public static PairManifest load(Path setDir) throws IOException {
        PairManifest m = new PairManifest(setDir);
        try (InputStream in = Files.newInputStream(setDir.resolve("reference.properties"))) {
            m.props.load(in);
        }
        Path optionsFile = setDir.resolve("options.properties");
        if (Files.exists(optionsFile)) {
            try (InputStream in = Files.newInputStream(optionsFile)) {
                m.options.load(in);
            }
        }
        return m;
    }

    public static boolean isPair(Path dir) {
        return Files.isDirectory(dir.resolve("odb")) && Files.isDirectory(dir.resolve("gerber"))
                && Files.exists(dir.resolve("reference.properties"));
    }

    public String name() {
        return dir.getFileName().toString();
    }

    public Path odbDir() {
        return dir.resolve("odb");
    }

    public Path gerberDir() {
        return dir.resolve("gerber");
    }

    public String step() {
        return props.getProperty("step", "pcb").trim();
    }

    public String source() {
        return props.getProperty("source", "?").trim();
    }

    /** Why the set is skipped, or null when it is to be tested. */
    public String skipReason() {
        String s = props.getProperty("skip");
        return s == null || s.isBlank() ? null : s.trim();
    }

    public int copperCount() {
        return Integer.parseInt(props.getProperty("copper.count", "0").trim());
    }

    /** Converter options for the layer pass ({@code options.properties}). */
    public Properties options() {
        return options;
    }

    public List<Path> drillReferences() {
        return list("drill");
    }

    public List<Path> routReferences() {
        return list("rout");
    }

    public List<Path> cutoutReferences() {
        return list("cutout");
    }

    /**
     * Kinds of unmatched drill operations that are not the converter's fault, from
     * {@code drill.ignore}: {@code ref-slots}, {@code ours-slots}, {@code ref-contours}, …
     */
    public java.util.Set<String> drillIgnore() {
        String v = props.getProperty("drill.ignore", "");
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String part : v.split("\\s*,\\s*")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    /** The Cadence {@code nc_param.txt} sidecar, or null. */
    public Path ncParam() {
        List<Path> p = list("ncparam");
        return p.isEmpty() ? null : p.get(0);
    }

    /** The reference file for one layer role ({@code copper.1}, {@code soldermask.top}, …), or null. */
    public Path layer(String role) {
        String v = props.getProperty(role);
        return v == null || v.isBlank() ? null : gerberDir().resolve(v.trim());
    }

    /** Every layer role that has a reference, in a stable order. */
    public Map<String, Path> layers() {
        Map<String, Path> out = new TreeMap<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("copper.") && !key.equals("copper.count")
                    || key.startsWith("soldermask.") || key.startsWith("legend.")
                    || key.startsWith("paste.") || key.equals("profile")) {
                out.put(key, layer(key));
            }
        }
        return out;
    }

    private List<Path> list(String key) {
        String v = props.getProperty(key);
        List<Path> out = new ArrayList<>();
        if (v != null) {
            for (String part : v.split("\\s*,\\s*")) {
                if (!part.isBlank()) {
                    out.add(gerberDir().resolve(part.trim()));
                }
            }
        }
        return out;
    }
}

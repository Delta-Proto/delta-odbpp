package com.deltaproto.deltaodbpp.export.gerber;

import com.deltaproto.deltagerber.model.drill.DrillDocument;
import com.deltaproto.deltagerber.model.drill.DrillHit;
import com.deltaproto.deltagerber.model.drill.DrillOperation;
import com.deltaproto.deltagerber.model.drill.DrillSlot;
import com.deltaproto.deltagerber.model.gerber.GerberDocument;
import com.deltaproto.deltagerber.model.gerber.aperture.CircleAperture;
import com.deltaproto.deltagerber.model.gerber.attribute.FileAttribute;
import com.deltaproto.deltagerber.model.gerber.operation.Draw;
import com.deltaproto.deltagerber.model.gerber.operation.Flash;
import com.deltaproto.deltagerber.model.gerber.operation.GraphicsObject;
import com.deltaproto.deltagerber.parser.ExcellonParser;
import com.deltaproto.deltagerber.parser.GerberParser;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.parser.OdbParser;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import com.deltaproto.deltaodbpp.testutil.GerberRaster;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Converts boards that were exported as ODB++ <em>and</em> plotted as Gerber/Excellon by
 * their own EDA tool (KiCad, Altium, …) from the same project, and checks the converter's
 * output against the tool's own plot. The two exports describe the same board, so a correct
 * converter must produce, per fabrication layer, an image that inks the same area as the
 * reference Gerber, and drill files with the same holes and slots.
 *
 * <p>The comparison is geometric, not textual: both files are parsed with delta-gerber and
 * rasterised ({@link GerberRaster}); a layer passes when the area inked by only one of the two
 * images is below {@value #MAX_MISMATCH_FRACTION} of the inked union, with a one-pixel
 * tolerance band (10&nbsp;µm on small boards, coarser on large ones) for edge jitter. Drill
 * files are compared hit by hit and slot by slot over the union of all drill files of the set,
 * since tools split plated/non-plated holes differently.
 *
 * <p>The designs are private, so the data lives outside git (see
 * {@link Fixtures#GERBER_REFERENCE_SETS} for the layout); the whole class is skipped where it
 * is absent. Per set, {@code options.properties} may carry:
 * <ul>
 *   <li>{@code subtractSoldermaskFromLegend=true} — the reference legend was plotted with
 *       soldermask openings cleared out of it;</li>
 *   <li>{@code assertFlashCounts=true} — every pad of a copper/paste layer must be a flash
 *       (the reference tool flashes one aperture per pad);</li>
 *   <li>{@code profileStrokeWidthMm=<mm>} — the outline stroke width the reference tool
 *       used, when the design carries no outline drawing layer to derive it from;</li>
 *   <li>{@code skipLayers=<name>,…} — reference layers the ODB++ export cannot match
 *       (documented in the file).</li>
 * </ul>
 * Generated files go to {@code target/gerber-reference-out/<set>/}, diff images of failing
 * layers to {@code target/gerber-reference-diffs/<set>/}.
 */
class GerberReferenceTest {

    private static final Path SETS = Fixtures.GERBER_REFERENCE_SETS;
    private static final Path DIFF_DIR = Path.of("target", "gerber-reference-diffs");
    private static final Path OUT_DIR = Path.of("target", "gerber-reference-out");

    /** Raster resolution ceiling: 100 px/mm = 10 µm pixels. */
    private static final double MAX_PX_PER_MM = 100;
    /** Raster size ceiling; the resolution drops on large boards to stay under it. */
    private static final double MAX_PIXELS = 40e6;
    /** Edge jitter tolerance in pixels. */
    private static final int TOLERANCE_PX = 1;
    /** Maximum area inked by only one of the two images, as a fraction of the inked union. */
    private static final double MAX_MISMATCH_FRACTION = 0.001;
    /** Drill coordinate tolerance: references write three or four decimals. */
    private static final double DRILL_TOLERANCE_MM = 0.0015;

    /** One reference set, converted once. */
    private record ReferenceSet(String name, Properties options,
                                Map<String, String> referenceFiles,
                                OdbToGerberConverter.Result result) {
    }

    @TestFactory
    Stream<DynamicNode> referenceSets() throws IOException {
        assumeTrue(Fixtures.hasPrivate(SETS), "private reference data not present: " + SETS);
        List<Path> dirs;
        try (Stream<Path> s = Files.list(SETS)) {
            dirs = s.filter(p -> Files.isDirectory(p.resolve("odb")) && Files.isDirectory(p.resolve("gerber")))
                    .sorted().toList();
        }
        assumeTrue(!dirs.isEmpty(), "no reference sets under " + SETS);
        return dirs.stream().map(dir -> DynamicContainer.dynamicContainer(
                dir.getFileName().toString(), testsFor(dir)));
    }

    private Stream<DynamicNode> testsFor(Path dir) {
        ReferenceSet set;
        try {
            set = load(dir);
        } catch (IOException e) {
            return Stream.of(DynamicTest.dynamicTest("load", () -> {
                throw e;
            }));
        }
        List<DynamicNode> tests = new ArrayList<>();
        tests.add(DynamicTest.dynamicTest("file set matches the reference",
                () -> assertFileSet(set)));
        tests.add(DynamicTest.dynamicTest("converts without warnings",
                () -> assertTrue(set.result.warnings.isEmpty(), "conversion warnings:\n"
                        + String.join("\n", set.result.warnings))));
        List<String> skip = List.of(set.options.getProperty("skipLayers", "").split("\\s*,\\s*"));
        for (String name : set.referenceFiles.keySet()) {
            if (!isDrillReference(name) && !skip.contains(name)) {
                tests.add(DynamicTest.dynamicTest(name + " inks the same area",
                        () -> compareGerber(set, name)));
            }
        }
        tests.add(DynamicTest.dynamicTest("drill hits and slots match",
                () -> compareDrills(set)));
        return tests.stream();
    }

    private static ReferenceSet load(Path dir) throws IOException {
        Properties options = new Properties();
        Path optionsFile = dir.resolve("options.properties");
        if (Files.exists(optionsFile)) {
            try (InputStream in = Files.newInputStream(optionsFile)) {
                options.load(in);
            }
        }
        Map<String, String> referenceFiles = new TreeMap<>();
        try (Stream<Path> files = Files.list(dir.resolve("gerber"))) {
            for (Path p : files.sorted().toList()) {
                referenceFiles.put(p.getFileName().toString(),
                        Files.readString(p, StandardCharsets.UTF_8));
            }
        }
        Job job = new OdbParser().parse(dir.resolve("odb"));
        String stepName = job.getSteps().keySet().iterator().next();
        OdbToGerberConverter converter = new OdbToGerberConverter();
        converter.setSubtractSoldermaskFromLegend(
                Boolean.parseBoolean(options.getProperty("subtractSoldermaskFromLegend", "false")));
        if (options.getProperty("profileStrokeWidthMm") != null) {
            converter.setProfileStrokeWidthMm(Double.parseDouble(options.getProperty("profileStrokeWidthMm")));
        }
        OdbToGerberConverter.Result result = converter.convert(job, stepName);
        String name = dir.getFileName().toString();
        result.writeTo(OUT_DIR.resolve(name));
        return new ReferenceSet(name, options, referenceFiles, result);
    }

    // ---------------------------------------------------------------- checks

    /**
     * Drill references are Excellon ({@code *.drl}) or, when the EDA tool's NC drill file
     * does not share the Gerbers' origin, its X2 drill Gerber ({@code *.drill.gbr}: one
     * flash per hole with the hole diameter as aperture).
     */
    private static boolean isDrillReference(String name) {
        return name.endsWith(".drl") || name.endsWith(".drill.gbr");
    }

    private static void assertFileSet(ReferenceSet set) {
        List<String> reference = set.referenceFiles.keySet().stream()
                .filter(n -> !isDrillReference(n)).toList();
        List<String> ours = set.result.files.stream().map(f -> f.fileName)
                .filter(n -> !n.endsWith(".drl")).sorted().toList();
        assertEquals(reference, ours, "generated Gerber file set differs from the reference");
        assertTrue(set.result.files.stream().anyMatch(f -> f.fileName.endsWith(".drl")),
                "no drill file generated");
    }

    private static void compareGerber(ReferenceSet set, String name) throws IOException {
        OdbToGerberConverter.OutputFile ours = find(set, name);
        GerberDocument reference = new GerberParser().parse(set.referenceFiles.get(name));
        GerberDocument generated = new GerberParser().parse(ours.content);
        assertTrue(generated.getWarnings().isEmpty(),
                name + ": generated Gerber parse warnings: " + generated.getWarnings());

        List<String> refFunction = reference.getFileFunctionValues();
        if (refFunction != null && !refFunction.isEmpty()) {
            List<String> genFunction = generated.getFileFunctionValues();
            // Altium appends ",Signal"/",Plane" to copper functions; compare the spec'd part.
            int n = "Copper".equals(refFunction.get(0)) ? 3 : refFunction.size();
            assertEquals(refFunction.subList(0, Math.min(n, refFunction.size())),
                    genFunction.subList(0, Math.min(n, genFunction.size())),
                    name + ": .FileFunction");
            String refPolarity = attribute(reference, ".FilePolarity");
            if (refPolarity != null) {
                assertEquals(refPolarity, attribute(generated, ".FilePolarity"),
                        name + ": .FilePolarity");
            }
        }

        if (Boolean.parseBoolean(set.options.getProperty("assertFlashCounts", "false"))
                && (name.endsWith("Cu.gtl") || name.endsWith("Cu.gbl") || name.contains("_Cu.g")
                || name.endsWith("Paste.gtp") || name.endsWith("Paste.gbp"))) {
            assertEquals(flashes(reference), flashes(generated), name + ": flash (pad) count");
        }

        Rectangle2D window = GerberRaster.window(reference, generated, 1.0);
        if (window == null) {
            assertTrue(reference.getObjects().isEmpty() && generated.getObjects().isEmpty(),
                    name + ": one side is empty, the other is not");
            return;
        }
        double pxPerMm = Math.min(MAX_PX_PER_MM,
                Math.sqrt(MAX_PIXELS / (window.getWidth() * window.getHeight())));
        BufferedImage ref = GerberRaster.rasterise(reference, window, pxPerMm);
        BufferedImage gen = GerberRaster.rasterise(generated, window, pxPerMm);
        GerberRaster.Comparison cmp = GerberRaster.compare(ref, gen, TOLERANCE_PX);

        String summary = String.format(Locale.ROOT,
                "[%s] %s: mismatch %.4f%% at %.0f px/mm (only reference: %d px, only ours: %d px, union %d px)",
                set.name, name, cmp.mismatchFraction() * 100, pxPerMm,
                cmp.onlyA(), cmp.onlyB(), cmp.union());
        System.out.println(summary);
        if (cmp.mismatchFraction() > MAX_MISMATCH_FRACTION) {
            Path diff = DIFF_DIR.resolve(set.name).resolve(name + ".png");
            GerberRaster.writeDiff(ref, gen, TOLERANCE_PX, diff);
            throw new AssertionError(summary + " — diff written to " + diff);
        }
    }

    private static void compareDrills(ReferenceSet set) {
        List<double[]> refOps = new ArrayList<>();
        for (Map.Entry<String, String> e : set.referenceFiles.entrySet()) {
            if (e.getKey().endsWith(".drl")) {
                refOps.addAll(drillOps(new ExcellonParser().parse(e.getValue())));
            } else if (e.getKey().endsWith(".drill.gbr")) {
                refOps.addAll(drillOps(new GerberParser().parse(e.getValue())));
            }
        }
        List<double[]> genOps = new ArrayList<>();
        for (OdbToGerberConverter.OutputFile f : set.result.files) {
            if (f.fileName.endsWith(".drl")) {
                DrillDocument doc = new ExcellonParser().parse(f.content);
                assertTrue(doc.getWarnings().isEmpty(),
                        f.fileName + ": generated Excellon parse warnings: " + doc.getWarnings());
                genOps.addAll(drillOps(doc));
            }
        }
        assertEquals(refOps.size(), genOps.size(), "drill operation count");

        List<double[]> unmatched = new ArrayList<>();
        for (double[] r : refOps) {
            int match = -1;
            for (int i = 0; i < genOps.size() && match < 0; i++) {
                if (sameOp(r, genOps.get(i))) {
                    match = i;
                }
            }
            if (match < 0) {
                unmatched.add(r);
            } else {
                genOps.remove(match);
            }
        }
        assertTrue(unmatched.isEmpty(), () -> unmatched.size()
                + " reference drill operations have no counterpart, e.g. " + describe(unmatched.get(0))
                + "; unmatched ours e.g. " + (genOps.isEmpty() ? "-" : describe(genOps.get(0))));
    }

    // ---------------------------------------------------------------- helpers

    private static OdbToGerberConverter.OutputFile find(ReferenceSet set, String name) {
        return set.result.files.stream().filter(f -> f.fileName.equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no generated file named " + name
                        + "; have " + set.result.files.stream().map(f -> f.fileName).toList()));
    }

    private static String attribute(GerberDocument doc, String name) {
        FileAttribute attr = doc.getFileAttributes().get(name);
        return attr == null ? null : String.valueOf(attr.getValues());
    }

    private static long flashes(GerberDocument doc) {
        return doc.getObjects().stream().filter(o -> o instanceof Flash).count();
    }

    /**
     * Drill operations as {diameter, x1, y1, x2, y2}; a hit has x2 = x1, y2 = y1. Slots are
     * direction-agnostic (normalised so the smaller endpoint comes first).
     */
    private static List<double[]> drillOps(DrillDocument doc) {
        List<double[]> ops = new ArrayList<>();
        for (DrillOperation op : doc.getOperations()) {
            double d = op.getTool() == null ? 0 : op.getTool().getDiameter();
            if (op instanceof DrillHit hit) {
                ops.add(new double[] {d, hit.getX(), hit.getY(), hit.getX(), hit.getY()});
            } else if (op instanceof DrillSlot slot) {
                double x1 = slot.getStartX();
                double y1 = slot.getStartY();
                double x2 = slot.getEndX();
                double y2 = slot.getEndY();
                if (x1 > x2 || (x1 == x2 && y1 > y2)) {
                    ops.add(new double[] {d, x2, y2, x1, y1});
                } else {
                    ops.add(new double[] {d, x1, y1, x2, y2});
                }
            }
        }
        return ops;
    }

    /** Drill operations of an X2 drill Gerber: circle flashes are hits, circle draws slots. */
    private static List<double[]> drillOps(GerberDocument doc) {
        List<double[]> ops = new ArrayList<>();
        for (GraphicsObject obj : doc.getObjects()) {
            if (obj instanceof Flash flash && flash.getAperture() instanceof CircleAperture c) {
                ops.add(new double[] {c.getDiameter(), flash.getX(), flash.getY(), flash.getX(), flash.getY()});
            } else if (obj instanceof Draw draw && draw.getAperture() instanceof CircleAperture c) {
                double x1 = draw.getStartX();
                double y1 = draw.getStartY();
                double x2 = draw.getEndX();
                double y2 = draw.getEndY();
                if (x1 > x2 || (x1 == x2 && y1 > y2)) {
                    ops.add(new double[] {c.getDiameter(), x2, y2, x1, y1});
                } else {
                    ops.add(new double[] {c.getDiameter(), x1, y1, x2, y2});
                }
            }
        }
        return ops;
    }

    private static boolean sameOp(double[] a, double[] b) {
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > DRILL_TOLERANCE_MM) {
                return false;
            }
        }
        return true;
    }

    private static String describe(double[] op) {
        return String.format(Locale.ROOT, "d=%.3f (%.3f,%.3f)-(%.3f,%.3f)",
                op[0], op[1], op[2], op[3], op[4]);
    }
}

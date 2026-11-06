package com.deltaproto.deltaodbpp.export.gerber;

import com.deltaproto.deltagerber.model.gerber.GerberDocument;
import com.deltaproto.deltagerber.model.gerber.operation.GraphicsObject;
import com.deltaproto.deltagerber.parser.GerberParser;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.parser.OdbParser;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import com.deltaproto.deltaodbpp.testutil.LayerCompare;
import com.deltaproto.deltaodbpp.testutil.PairManifest;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Layer pass over the private ODB++/Gerber pair corpus: converts each job and compares every
 * generated fabrication layer with the EDA tool's own plot of it, as named by the set's
 * manifest ({@link PairManifest}), with the raster comparison of {@link LayerCompare}.
 *
 * <p>One dynamic test per layer, so the scoreboard is per layer:
 * {@code target/odb-gerber-pairs/layer-summary.tsv} gets one line per compared layer, diff
 * images of failing layers go to {@code target/odb-gerber-pairs/diffs/<set>/}. Per set,
 * {@code options.properties} may carry the converter options the EDA tool's plot used
 * ({@code subtractSoldermaskFromLegend}, {@code profileStrokeWidthMm}), and for the
 * comparison: {@code skipLayers=<role>,…} for layers the reference cannot match,
 * {@code maskReferenceWith=<file>,…} or {@code maskCommonReferenceInk=true} for a mechanical
 * film the reference plot merged into every layer (named, or found as the ink every reference
 * layer shares), {@code offsetMm=dx,dy} when the Gerbers and the ODB++ use different origins,
 * {@code maxMismatchPct[.<role>]=<pct>} for a documented residual — each with its reason in a
 * comment, and recorded outside the repository. Skipped entirely when the corpus is absent.
 */
class OdbGerberPairLayerTest {

    /** {@code -Dodb.gerber.pairs=<dir>} runs another corpus of the same layout. */
    private static final Path SETS = Path.of(System.getProperty("odb.gerber.pairs", Fixtures.ODB_GERBER_PAIRS.toString()));
    private static final Path OUT = Path.of(System.getProperty("odb.gerber.pairs.out", "target/odb-gerber-pairs"));
    /**
     * {@code -Dgerber.compare.raw=true} measures every layer without the per-set acceptance
     * options (masks, thresholds, skips) so the numbers can be compared with another renderer's;
     * the origin offset is kept, as it is geometry rather than acceptance.
     */
    private static final boolean RAW = Boolean.getBoolean("gerber.compare.raw");
    private static final Path SUMMARY = OUT.resolve("layer-summary.tsv");
    private static final Path DIFF_DIR = OUT.resolve("diffs");
    private static final String HEADER = String.join("\t", "set", "source", "role", "reference", "generated",
            "mismatch_pct", "px_per_mm", "only_reference_px", "only_ours_px", "status", "note");

    /** One set's conversion, done once and shared by its layer tests. */
    private static final class Converted {
        final PairManifest manifest;
        OdbToGerberConverter.Result result;
        Exception failure;
        java.awt.geom.Rectangle2D window;
        double pxPerMm;
        java.awt.image.BufferedImage commonInk;

        Converted(PairManifest manifest) {
            this.manifest = manifest;
        }

        synchronized OdbToGerberConverter.Result get() throws Exception {
            if (failure != null) {
                throw failure;
            }
            if (result == null) {
                try {
                    Job job = new OdbParser().parse(manifest.odbDir());
                    Properties options = manifest.options();
                    OdbToGerberConverter converter = new OdbToGerberConverter();
                    converter.setSubtractSoldermaskFromLegend(
                            Boolean.parseBoolean(options.getProperty("subtractSoldermaskFromLegend", "false")));
                    if (options.getProperty("profileStrokeWidthMm") != null) {
                        converter.setProfileStrokeWidthMm(Double.parseDouble(options.getProperty("profileStrokeWidthMm")));
                    }
                    result = converter.convert(job, manifest.step());
                    result.writeTo(OUT.resolve("out").resolve(manifest.name()));
                } catch (Exception e) {
                    failure = e;
                    throw e;
                }
            }
            return result;
        }

        /**
         * The ink shared by every reference layer of the set, over one window for the whole
         * set: a mechanical film (outline, title block, layer table) the EDA tool merged into
         * every plot, which the ODB++ export does not carry. Only used with
         * {@code maskCommonReferenceInk=true}.
         */
        synchronized java.awt.image.BufferedImage commonInk() throws IOException {
            if (commonInk == null) {
                List<GerberDocument> docs = new ArrayList<>();
                com.deltaproto.deltagerber.model.gerber.BoundingBox union = new com.deltaproto.deltagerber.model.gerber.BoundingBox();
                for (Path p : manifest.layers().values()) {
                    GerberDocument doc = new GerberParser().parse(Files.readString(p, StandardCharsets.ISO_8859_1));
                    var b = doc.calculateBoundingBox();
                    if (b != null && b.isValid()) {
                        union.include(b);
                        docs.add(doc);
                    }
                }
                window = new java.awt.geom.Rectangle2D.Double(union.getMinX() - 1, union.getMinY() - 1,
                        union.getWidth() + 2, union.getHeight() + 2);
                pxPerMm = LayerCompare.resolution(window);
                List<java.awt.image.BufferedImage> images = new ArrayList<>();
                for (GerberDocument doc : docs) {
                    images.add(com.deltaproto.deltaodbpp.testutil.GerberRaster.rasterise(doc, window, pxPerMm));
                }
                commonInk = com.deltaproto.deltaodbpp.testutil.GerberRaster.intersection(images);
            }
            return commonInk;
        }
    }

    @TestFactory
    Stream<DynamicNode> pairs() throws IOException {
        assumeTrue(Fixtures.hasPrivate(SETS), "private pair corpus not present: " + SETS);
        List<Path> dirs;
        try (Stream<Path> s = Files.list(SETS)) {
            dirs = s.filter(PairManifest::isPair).sorted().toList();
        }
        assumeTrue(!dirs.isEmpty(), "no pairs with a reference.properties under " + SETS);
        Files.createDirectories(OUT);
        Files.writeString(SUMMARY, HEADER + "\n", StandardCharsets.UTF_8);
        List<DynamicNode> nodes = new ArrayList<>();
        for (Path dir : dirs) {
            PairManifest m = PairManifest.load(dir);
            nodes.add(DynamicContainer.dynamicContainer(m.name(), testsFor(m)));
        }
        return nodes.stream();
    }

    private Stream<DynamicNode> testsFor(PairManifest m) {
        if (m.skipReason() != null) {
            return Stream.of(DynamicTest.dynamicTest("skipped", () -> assumeTrue(false, m.skipReason())));
        }
        Converted converted = new Converted(m);
        List<String> skip = List.of(m.options().getProperty("skipLayers", "").split("\\s*,\\s*"));
        List<DynamicNode> tests = new ArrayList<>();
        for (Map.Entry<String, Path> e : m.layers().entrySet()) {
            String role = e.getKey();
            Path reference = e.getValue();
            tests.add(DynamicTest.dynamicTest(role + " = " + reference.getFileName(), () -> {
                if (!RAW && skip.contains(role)) {
                    summary(m, role, reference, "-", null, "skipped", "skipLayers");
                    assumeTrue(false, role + " skipped by options.properties");
                }
                compareLayer(converted, role, reference);
            }));
        }
        return tests.stream();
    }

    private void compareLayer(Converted converted, String role, Path reference) throws Exception {
        PairManifest m = converted.manifest;
        OdbToGerberConverter.Result result;
        try {
            result = converted.get();
        } catch (Exception e) {
            summary(m, role, reference, "-", null, "ERROR", "conversion failed: " + e);
            throw e;
        }
        String generatedName = generatedName(role, m.copperCount());
        OdbToGerberConverter.OutputFile ours = result.files.stream()
                .filter(f -> f.fileName.equals(generatedName)).findFirst().orElse(null);
        if (ours == null) {
            summary(m, role, reference, generatedName, null, "FAIL", "no such generated file; have "
                    + result.files.stream().map(f -> f.fileName).toList());
            fail(m.name() + " " + role + ": no generated file " + generatedName);
        }

        GerberDocument ref;
        try {
            ref = new GerberParser().parse(Files.readString(reference, StandardCharsets.ISO_8859_1));
        } catch (Exception e) {
            summary(m, role, reference, generatedName, null, "ERROR", "reference does not parse: " + e);
            throw e;
        }
        GerberDocument gen = new GerberParser().parse(ours.content);
        if (!gen.getWarnings().isEmpty()) {
            summary(m, role, reference, generatedName, null, "FAIL", "generated Gerber parse warnings: " + gen.getWarnings());
            fail(m.name() + " " + generatedName + ": generated Gerber parse warnings: " + gen.getWarnings());
        }

        String functionProblem = LayerCompare.fileFunctionMismatch(ref, gen);
        Path diff = DIFF_DIR.resolve(m.name()).resolve(generatedName + ".png");
        Properties options = m.options();
        // The generated image may be shifted onto the reference's origin: the EDA tool's
        // Gerbers and its ODB++ export do not always share one (KiCad 8 vs 9, PADS).
        double[] offset = offset(options);
        if (offset != null) {
            gen = translated(gen, offset[0], offset[1]);
        }
        // Reference plots sometimes include a mechanical layer on every film (board outline,
        // layer-stack table); masking that file's ink out of both images keeps the rest verified.
        List<GerberDocument> masks = new ArrayList<>();
        for (String maskFile : RAW ? new String[0] : options.getProperty("maskReferenceWith", "").split("\\s*,\\s*")) {
            if (!maskFile.isBlank()) {
                masks.add(new GerberParser().parse(Files.readString(m.gerberDir().resolve(maskFile.trim()), StandardCharsets.ISO_8859_1)));
            }
        }
        double maxMismatch = RAW ? LayerCompare.MAX_MISMATCH_FRACTION : Double.parseDouble(options.getProperty("maxMismatchPct." + role,
                options.getProperty("maxMismatchPct", Double.toString(LayerCompare.MAX_MISMATCH_FRACTION * 100)))) / 100;
        LayerCompare.Result cmp;
        if (!RAW && Boolean.parseBoolean(options.getProperty("maskCommonReferenceInk", "false"))) {
            java.awt.image.BufferedImage common = converted.commonInk();
            List<java.awt.image.BufferedImage> maskImages = new ArrayList<>();
            maskImages.add(common);
            for (GerberDocument mask : masks) {
                maskImages.add(com.deltaproto.deltaodbpp.testutil.GerberRaster.rasterise(mask, converted.window, converted.pxPerMm));
            }
            cmp = LayerCompare.compare(ref, gen, converted.window, converted.pxPerMm, maskImages, maxMismatch, diff);
        } else {
            cmp = LayerCompare.compare(ref, gen, masks, maxMismatch, diff);
        }
        String status = cmp.mismatchFraction() <= maxMismatch && functionProblem == null ? "OK" : "FAIL";
        String note = functionProblem != null ? functionProblem
                : !ref.getWarnings().isEmpty() ? "reference parse warnings (" + ref.getWarnings().size() + "): "
                + ref.getWarnings().get(0) : "";
        if (RAW) {
            java.awt.geom.Rectangle2D w = com.deltaproto.deltaodbpp.testutil.GerberRaster.window(ref, gen, 1.0);
            note = (w == null ? "window=none" : String.format(Locale.ROOT, "window=%.3f,%.3f,%.3f,%.3f",
                    w.getX(), w.getY(), w.getWidth(), w.getHeight())) + (note.isEmpty() ? "" : "; " + note);
        }
        summary(m, role, reference, generatedName, cmp, status, note);
        assertTrue(status.equals("OK"), () -> m.name() + " " + role + " (" + generatedName + "): " + cmp.summary()
                + (functionProblem != null ? "; " + functionProblem : "")
                + (cmp.diff() != null ? " — diff written to " + cmp.diff() : ""));
    }

    /** {@code offsetMm=dx,dy} from options.properties: the shift from generated onto reference coordinates. */
    private static double[] offset(Properties options) {
        String v = options.getProperty("offsetMm");
        if (v == null || v.isBlank()) {
            return null;
        }
        String[] parts = v.split("\\s*,\\s*");
        return new double[] {Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
    }

    private static GerberDocument translated(GerberDocument doc, double dx, double dy) {
        GerberDocument out = new GerberDocument();
        for (GraphicsObject obj : doc.getObjects()) {
            out.addObject(obj.translate(dx, dy));
        }
        return out;
    }

    /** The converter's file name for a manifest role ({@code copper.3} → {@code In2_Cu.g2}). */
    static String generatedName(String role, int copperCount) {
        if (role.startsWith("copper.")) {
            int n = Integer.parseInt(role.substring("copper.".length()));
            if (n == 1) {
                return "F_Cu.gtl";
            }
            if (n == copperCount) {
                return "B_Cu.gbl";
            }
            return "In" + (n - 1) + "_Cu.g" + (n - 1);
        }
        boolean top = role.endsWith(".top");
        return switch (role.substring(0, role.indexOf('.') < 0 ? role.length() : role.indexOf('.'))) {
            case "soldermask" -> top ? "F_Mask.gts" : "B_Mask.gbs";
            case "legend" -> top ? "F_Silkscreen.gto" : "B_Silkscreen.gbo";
            case "paste" -> top ? "F_Paste.gtp" : "B_Paste.gbp";
            case "profile" -> "Edge_Cuts.gm1";
            default -> throw new IllegalArgumentException("unknown layer role " + role);
        };
    }

    private static synchronized void summary(PairManifest m, String role, Path reference, String generated,
                                             LayerCompare.Result cmp, String status, String note) throws IOException {
        String line = String.join("\t", m.name(), m.source(), role, reference.getFileName().toString(), generated,
                cmp == null ? "-" : String.format(Locale.ROOT, "%.4f", cmp.mismatchFraction() * 100),
                cmp == null ? "-" : String.format(Locale.ROOT, "%.0f", cmp.pxPerMm()),
                cmp == null ? "-" : Long.toString(cmp.onlyReference()),
                cmp == null ? "-" : Long.toString(cmp.onlyGenerated()),
                status, note.replace('\t', ' ').replace('\n', ' '));
        System.out.println(line);
        Files.writeString(SUMMARY, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }
}

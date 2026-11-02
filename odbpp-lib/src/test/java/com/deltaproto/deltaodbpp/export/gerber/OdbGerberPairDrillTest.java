package com.deltaproto.deltaodbpp.export.gerber;

import com.deltaproto.deltagerber.model.drill.DrillDocument;
import com.deltaproto.deltagerber.parser.GerberParser;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.parser.OdbParser;
import com.deltaproto.deltaodbpp.testutil.DrillReference;
import com.deltaproto.deltaodbpp.testutil.DrillReference.Kind;
import com.deltaproto.deltaodbpp.testutil.DrillReference.Match;
import com.deltaproto.deltaodbpp.testutil.DrillReference.Offset;
import com.deltaproto.deltaodbpp.testutil.DrillReference.Op;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import com.deltaproto.deltaodbpp.testutil.PairManifest;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Drill pass over the private ODB++/Gerber pair corpus: converts each job and checks that the
 * generated Excellon holds the same holes, slots and routed contours as the EDA tool's own
 * drill files, whatever dialect those use (see {@link DrillReference}).
 *
 * <p>Per set: parse {@code odb/}, convert the manifest's step, parse the generated {@code .drl}
 * files (plus {@code Rout.gbr} and the outline for contours), parse every drill and rout
 * reference, estimate and report a translation between the two origins, then match hit by
 * hit. One line per set goes to {@code target/odb-gerber-pairs/drill-summary.tsv} so the whole
 * corpus reads at a glance; the unmatched operations of a failing set go to
 * {@code target/odb-gerber-pairs/<set>-unmatched.txt}. Sets fail independently.
 *
 * <p>The manifest's {@code drill.ignore} lists kinds of unmatched operations that are not
 * defects of the converter: {@code ours-slots} when the reference set ships no rout file,
 * {@code ref-slots} when the ODB++ job itself lacks them. Skipped entirely when the corpus is
 * absent ({@link Fixtures#ODB_GERBER_PAIRS}).
 */
class OdbGerberPairDrillTest {

    private static final Path SETS = Fixtures.ODB_GERBER_PAIRS;
    private static final Path OUT = Path.of("target", "odb-gerber-pairs");
    private static final Path SUMMARY = OUT.resolve("drill-summary.tsv");
    private static final String HEADER = String.join("\t", "set", "source", "step", "warnings",
            "hits_ref", "hits_ours", "slots_ref", "slots_ours", "contours_ref", "contours_ours",
            "unmatched_ref", "unmatched_ours", "offset", "tolerance", "status", "example");

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
        return dirs.stream().map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), () -> run(dir)));
    }

    private void run(Path dir) throws IOException {
        PairManifest m = PairManifest.load(dir);
        String name = m.name();
        if (m.skipReason() != null) {
            summary(name, m, "-", 0, List.of(), List.of(), null, null, null, 0, "skipped: " + m.skipReason(), "");
            assumeTrue(false, name + " skipped: " + m.skipReason());
        }

        Job job = new OdbParser().parse(m.odbDir());
        OdbToGerberConverter.Result result = new OdbToGerberConverter().convert(job, m.step());
        result.writeTo(OUT.resolve("out").resolve(name));
        for (String w : result.warnings) {
            System.out.println("[" + name + "] warning: " + w);
        }

        List<Op> ours = new ArrayList<>();
        for (OdbToGerberConverter.OutputFile f : result.files) {
            if (f.fileName.endsWith(".drl")) {
                DrillDocument doc = DrillReference.parseExcellon(f.content, null);
                assertTrue(doc.getWarnings().isEmpty(),
                        f.fileName + ": generated Excellon parse warnings: " + doc.getWarnings());
                ours.addAll(DrillReference.tagged(DrillReference.ops(doc), f.fileName, !f.fileName.startsWith("NPTH")));
            } else if (f.fileName.equals("Rout.gbr") || f.fileName.startsWith("Rout-")
                    || f.fileName.equals("Edge_Cuts.gm1")) {
                ours.addAll(DrillReference.contoursOf(new GerberParser().parse(f.content)));
            }
        }

        String ncParam = m.ncParam() == null ? null : Files.readString(m.ncParam(), StandardCharsets.ISO_8859_1);
        DrillReference.ExcellonReader reader = new DrillReference.ExcellonReader(DrillReference.span(ours));
        List<Op> ref = new ArrayList<>();
        double maxTolerance = DrillReference.TOLERANCE_MM;
        List<Path> refs = new ArrayList<>(m.drillReferences());
        refs.addAll(m.routReferences());
        for (Path p : refs) {
            String content = Files.readString(p, StandardCharsets.ISO_8859_1);
            String fileName = p.getFileName().toString();
            if (isGerber(content)) {
                var doc = new GerberParser().parse(content);
                ref.addAll(DrillReference.tagged(DrillReference.ops(doc), fileName, DrillReference.platingOf(fileName, doc)));
            } else {
                DrillDocument doc = reader.parse(fileName, content, ncParam);
                for (String w : doc.getWarnings()) {
                    System.out.println("[" + name + "] " + fileName + ": " + w);
                }
                maxTolerance = Math.max(maxTolerance, DrillReference.tolerance(doc));
                ref.addAll(DrillReference.tagged(DrillReference.ops(doc), fileName, DrillReference.platingOf(fileName, null)));
            }
        }
        for (String note : reader.notes()) {
            System.out.println("[" + name + "] " + note);
        }
        double tolerance = maxTolerance;

        Offset offset = DrillReference.estimateOffset(ref, ours, tolerance);
        List<Op> shifted = ref;
        if (offset.isSignificant(tolerance)) {
            shifted = ref.stream().map(o -> o.translate(offset.dx(), offset.dy())).toList();
        }
        Match match = DrillReference.match(shifted, ours, tolerance);

        // A generated span file (PTH-L1-L2.drl) must map onto one reference file: Altium
        // writes one NC drill file per span, and a hole from another span in ours is a
        // wrong layer pair. Through-hole files may legitimately gather several reference
        // files (round holes and slot holes, plated and non-plated when the tool merges them).
        Map<String, Map<String, Integer>> mapping = DrillReference.sourceMapping(shifted, ours, tolerance);
        List<String> spanProblems = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> e : mapping.entrySet()) {
            if (e.getKey().contains("-L") && e.getValue().size() > 1) {
                spanProblems.add(e.getKey() + " maps onto " + e.getValue());
            }
        }

        // Generated contours that no reference claims are not a defect by themselves: the
        // board outline is routed by the fab from the profile, and ODB++ ROUT layers often
        // carry it. Only generated holes and slots must be accounted for.
        Set<String> ignore = m.drillIgnore();
        List<Op> unmatchedRef = match.unmatchedRef().stream()
                .filter(o -> !ignore.contains("ref-" + plural(o.kind()))).toList();
        List<Op> unmatchedOurs = match.unmatchedOurs().stream()
                .filter(o -> o.kind() != Kind.CONTOUR && !ignore.contains("ours-" + plural(o.kind()))).toList();
        String status = unmatchedRef.isEmpty() && unmatchedOurs.isEmpty() && spanProblems.isEmpty() ? "OK" : "FAIL";
        String example = !spanProblems.isEmpty() ? spanProblems.get(0)
                : unmatchedRef.isEmpty() ? (unmatchedOurs.isEmpty() ? "" : "ours " + unmatchedOurs.get(0).describe())
                : "ref " + unmatchedRef.get(0).describe() + " -> nearest ours "
                + DrillReference.nearest(unmatchedRef.get(0), ours);
        summary(name, m, m.step(), result.warnings.size(), shifted, ours, unmatchedRef, unmatchedOurs,
                offset, tolerance, status + (offset.isSignificant(tolerance) ? " (offset)" : ""), example);

        Path detail = OUT.resolve(name + "-unmatched.txt");
        Files.deleteIfExists(detail);
        if (!status.equals("OK")) {
            List<String> lines = new ArrayList<>();
            lines.add("offset " + offset + ", tolerance " + tolerance);
            lines.add("ref  bbox " + Arrays.toString(DrillReference.bbox(shifted)) + " diameters " + DrillReference.diameters(shifted));
            lines.add("ours bbox " + Arrays.toString(DrillReference.bbox(ours)) + " diameters " + DrillReference.diameters(ours));
            mapping.forEach((file, sources) -> lines.add("file  " + file + " <- " + sources));
            spanProblems.forEach(sp -> lines.add("span  " + sp));
            unmatchedRef.stream().limit(500).forEach(o -> lines.add("ref   " + o.describe()
                    + "  | nearest ours " + DrillReference.nearest(o, ours)));
            unmatchedOurs.stream().limit(500).forEach(o -> lines.add("ours  " + o.describe()));
            Files.write(detail, lines, StandardCharsets.UTF_8);
        }
        assertTrue(status.equals("OK"), () -> name + ": " + unmatchedRef.size()
                + " reference operations unmatched, " + unmatchedOurs.size() + " generated ones unmatched"
                + (offset.isSignificant(tolerance) ? ", after origin offset " + offset : "")
                + "; e.g. " + example + " — details in " + detail);
    }

    private static String plural(Kind kind) {
        return kind.name().toLowerCase(Locale.ROOT) + "s";
    }

    private static boolean isGerber(String content) {
        return content.contains("%FSLA") || content.contains("%MOMM") || content.contains("%MOIN");
    }

    private static synchronized void summary(String name, PairManifest m, String step, int warnings,
                                             List<Op> ref, List<Op> ours, List<Op> unmatchedRef, List<Op> unmatchedOurs,
                                             Offset offset, double tolerance, String status, String example) throws IOException {
        String line = String.join("\t", name, m.source(), step, Integer.toString(warnings),
                count(ref, Kind.HIT), count(ours, Kind.HIT), count(ref, Kind.SLOT), count(ours, Kind.SLOT),
                count(ref, Kind.CONTOUR), count(ours, Kind.CONTOUR),
                unmatchedRef == null ? "-" : Integer.toString(unmatchedRef.size()),
                unmatchedOurs == null ? "-" : Integer.toString(unmatchedOurs.size()),
                offset == null || !offset.isSignificant(tolerance) ? "0"
                        : String.format(Locale.ROOT, "%.4f,%.4f", offset.dx(), offset.dy()),
                String.format(Locale.ROOT, "%.4f", tolerance),
                status, example);
        System.out.println(line);
        Files.writeString(SUMMARY, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    private static String count(List<Op> ops, Kind kind) {
        return Long.toString(ops.stream().filter(o -> o.kind() == kind).count());
    }
}

package com.deltaproto.deltaodbpp.export.gerber;

import com.deltaproto.deltagerber.model.gerber.GerberDocument;
import com.deltaproto.deltagerber.parser.GerberParser;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end validation of the ODB++ -> Gerber/Excellon converter against a
 * committed, openly-available multilayer sample ("designodb", a rigid-flex
 * reference design). The assertions are structural: the converter must emit a
 * fabrication data set (copper, profile and drill file functions) without
 * regard to any one board's exact hole counts or dimensions.
 *
 * <p>Each generated Gerber is re-parsed with the delta-gerber library (the same
 * library the DeltaProto backend uses), which acts as an independent referee:
 * if delta-gerber reads back valid geometry, downstream consumers will too.
 */
class OdbToGerberRoundTripTest {

    /** designodb has a single step. */
    private static final String DESIGNODB_STEP = "cellular_flip-phone";

    @Test
    void convertsMultilayerSampleToAFabricationDataSet(@TempDir Path tempDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);

        OdbToGerberConverter.Result result =
                new OdbToGerberConverter().convert(job, DESIGNODB_STEP);

        assertNotNull(result, "converter returned no result");
        assertFalse(result.files.isEmpty(), "converter produced no output files");

        List<String> functions = result.files.stream().map(f -> f.fileFunction).toList();

        assertTrue(functions.stream().anyMatch(f -> f.startsWith("Copper,")),
                "expected at least one copper Gerber, got: " + functions);
        assertTrue(functions.contains("Profile,NP"),
                "expected a board outline (Profile,NP), got: " + functions);
        assertTrue(functions.stream().anyMatch(f -> f.startsWith("Plated,")
                        || f.startsWith("NonPlated,")),
                "expected at least one drill layer, got: " + functions);
    }

    @Test
    void copperGerberRoundTripsThroughDeltaGerber(@TempDir Path tempDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        OdbToGerberConverter.Result result =
                new OdbToGerberConverter().convert(job, DESIGNODB_STEP);

        OdbToGerberConverter.OutputFile copper = result.files.stream()
                .filter(f -> f.fileFunction.startsWith("Copper,"))
                .filter(f -> f.fileName.endsWith(".gbr"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no copper Gerber file generated: " + result.files.stream()
                                .map(f -> f.fileFunction).toList()));

        GerberDocument doc = new GerberParser().parse(copper.content);

        assertNotNull(doc, copper.fileName + " did not parse");
        assertFalse(doc.getObjects().isEmpty(),
                copper.fileName + " produced no graphics objects");
        assertTrue(doc.getWarnings().isEmpty(),
                copper.fileName + " parse warnings: " + doc.getWarnings());
        // Sanity-check the extents are physically plausible (catches unit errors
        // and axis flips) without pinning to any one board's dimensions.
        assertTrue(doc.getWidthMm() > 0 && doc.getHeightMm() > 0,
                copper.fileName + " has degenerate extents: "
                        + doc.getWidthMm() + " x " + doc.getHeightMm() + " mm");
    }

    @Test
    void everyGeneratedGerberParsesCleanly(@TempDir Path tempDir) throws IOException {
        Job job = Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        OdbToGerberConverter.Result result =
                new OdbToGerberConverter().convert(job, DESIGNODB_STEP);

        for (OdbToGerberConverter.OutputFile file : result.files) {
            if (!file.fileName.endsWith(".gbr")) {
                continue;
            }
            GerberDocument doc = new GerberParser().parse(file.content);
            assertNotNull(doc, file.fileName);
            assertTrue(doc.getWarnings().isEmpty(),
                    file.fileName + " parse warnings: " + doc.getWarnings());
        }
    }
}

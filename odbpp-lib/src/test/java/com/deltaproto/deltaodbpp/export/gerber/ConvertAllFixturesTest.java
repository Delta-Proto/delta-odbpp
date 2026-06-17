package com.deltaproto.deltaodbpp.export.gerber;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Broad robustness net over {@link OdbToGerberRoundTripTest}: converts every
 * committed, openly-available sample archive to Gerber/Excellon and asserts the
 * converter produces a non-empty result without throwing. Warnings are treated
 * as non-fatal (logged, not asserted on) since generic samples exercise a wide
 * range of features.
 */
class ConvertAllFixturesTest {

    /** The committed openly-available sample archives under {@code examples/}. */
    private static final List<String> SAMPLE_ARCHIVES = List.of(
            "designodb_rigidflex.tgz",
            "flat_hierarchy-odb.tgz",
            "sandbox-odb_pc4.tgz",
            "sandbox-odb_wifi.tgz",
            "video-odb.tgz");

    @TestFactory
    Stream<DynamicTest> convertEverySample(@TempDir Path tempDir) {
        return SAMPLE_ARCHIVES.stream().map(archive -> DynamicTest.dynamicTest(
                archive,
                () -> convertSample(archive, tempDir.resolve(archive.replace('.', '_')))));
    }

    private void convertSample(String archive, Path workDir) throws IOException {
        Job job = Fixtures.parseSample(archive, workDir);

        assertNotNull(job.getSteps(), archive + ": no steps parsed");
        assertFalse(job.getSteps().isEmpty(), archive + ": no steps parsed");
        String stepName = job.getSteps().keySet().iterator().next();

        OdbToGerberConverter.Result result =
                new OdbToGerberConverter().convert(job, stepName);

        assertNotNull(result, archive + ": converter returned no result");
        assertFalse(result.files.isEmpty(),
                archive + " (step " + stepName + "): converter produced no files");

        long unsupported = result.warnings.stream()
                .filter(w -> w.contains("Unsupported")).count();
        System.out.printf("[%s] step=%s files=%d warnings=%d unsupported=%d%n",
                archive, stepName, result.files.size(),
                result.warnings.size(), unsupported);

        // Every generated file must at least carry a file function and content.
        for (OdbToGerberConverter.OutputFile file : result.files) {
            assertNotNull(file.content, archive + ": null content for " + file.fileName);
            assertTrue(file.fileFunction != null && !file.fileFunction.isBlank(),
                    archive + ": missing file function for " + file.fileName);
        }
    }
}

package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Step;
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
 * Parses every committed, openly-available sample ODB++ archive (the "designodb"
 * rigid-flex reference design and generic KiCad/sandbox exports under
 * {@code examples/}) and checks the broad parser invariants the ODB++ -> Gerber
 * converter relies on: a non-null job with a matrix, at least one step, and at
 * least one layer per step.
 *
 * <p>Assertions are deliberately lenient so they hold across every generic
 * sample; board-specific details (drill-tool coverage, exact dcodes) are not
 * asserted here.
 */
class RealWorldFixturesTest {

    /** The committed openly-available sample archives under {@code examples/}. */
    private static final List<String> SAMPLE_ARCHIVES = List.of(
            "designodb_rigidflex.tgz",
            "flat_hierarchy-odb.tgz",
            "sandbox-odb_pc4.tgz",
            "sandbox-odb_wifi.tgz",
            "video-odb.tgz");

    @TestFactory
    Stream<DynamicTest> parseEverySample(@TempDir Path tempDir) {
        return SAMPLE_ARCHIVES.stream().map(archive -> DynamicTest.dynamicTest(
                archive,
                () -> parseAndValidate(archive, tempDir.resolve(archive.replace('.', '_')))));
    }

    private void parseAndValidate(String archive, Path workDir) throws IOException {
        Job job = Fixtures.parseSample(archive, workDir);

        assertNotNull(job, archive + ": parser returned null");
        assertNotNull(job.getMatrix(), archive + ": matrix missing");
        assertNotNull(job.getMatrix().getLayers(), archive + ": matrix has no layers");
        assertFalse(job.getMatrix().getLayers().isEmpty(),
                archive + ": matrix layer list is empty");

        assertNotNull(job.getSteps(), archive + ": steps missing");
        assertFalse(job.getSteps().isEmpty(), archive + ": no steps parsed");

        for (Step step : job.getSteps().values()) {
            assertNotNull(step.getLayersByName(),
                    archive + ": step has no layers: " + step.getName());
            assertFalse(step.getLayersByName().isEmpty(),
                    archive + ": step has empty layer set: " + step.getName());
        }

        System.out.printf("[%s] steps=%d matrixLayers=%d%n", archive,
                job.getSteps().size(), job.getMatrix().getLayers().size());
    }
}

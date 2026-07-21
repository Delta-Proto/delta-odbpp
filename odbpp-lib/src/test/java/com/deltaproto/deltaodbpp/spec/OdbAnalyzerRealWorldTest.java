package com.deltaproto.deltaodbpp.spec;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs {@link OdbAnalyzer} over every committed, openly-available sample archive and asserts broad
 * invariants rather than exact values: analysis never throws, boards with a profile report a size,
 * copper count is positive, and any measured min-track / min-drill falls in a physically plausible
 * range. Mirrors {@code RealWorldFixturesTest}'s lenient, cross-sample style.
 */
class OdbAnalyzerRealWorldTest {

    private static final List<String> SAMPLE_ARCHIVES = List.of(
            "designodb_rigidflex.tgz",
            "flat_hierarchy-odb.tgz",
            "sandbox-odb_pc4.tgz",
            "sandbox-odb_wifi.tgz",
            "video-odb.tgz");

    @TestFactory
    Stream<DynamicTest> analyzeEverySample(@TempDir Path tempDir) {
        return SAMPLE_ARCHIVES.stream().map(archive -> DynamicTest.dynamicTest(
                archive,
                () -> analyzeAndValidate(archive, tempDir.resolve(archive.replace('.', '_')))));
    }

    private void analyzeAndValidate(String archive, Path workDir) throws IOException {
        Job job = Fixtures.parseSample(archive, workDir);
        assertNotNull(job, archive + ": parser returned null");

        BoardSpecification spec = new OdbAnalyzer().analyze(job);
        assertNotNull(spec, archive + ": analyzer returned null");
        assertNotNull(spec.getStepName(), archive + ": no step chosen");
        assertNotNull(spec.getLayers(), archive + ": null layer list");

        if (spec.hasProfile()) {
            assertNotNull(spec.getSizeXMm(), archive + ": profile present but no size X");
            assertNotNull(spec.getSizeYMm(), archive + ": profile present but no size Y");
            assertTrue(spec.getSizeXMm() > 0 && spec.getSizeYMm() > 0,
                    archive + ": non-positive board size " + spec.getSizeXMm() + "x" + spec.getSizeYMm());
        }

        if (spec.hasCopper()) {
            assertNotNull(spec.getCopperLayerCount(), archive + ": copper present but null count");
            assertTrue(spec.getCopperLayerCount() > 0,
                    archive + ": non-positive copper layer count");
        }

        Double minTrack = spec.getMinTrackWidthUm();
        if (minTrack != null) {
            assertTrue(minTrack >= 10.0 && minTrack <= 5000.0,
                    archive + ": implausible min track " + minTrack + " µm");
        }

        Double minDrill = spec.getMinDrillDiameterMm();
        if (minDrill != null) {
            assertTrue(minDrill >= 0.05 && minDrill <= 7.0,
                    archive + ": implausible min drill " + minDrill + " mm");
        }

        // Plated/non-plated minima, when present, must agree with the overall minimum.
        if (spec.getMinPlatedDrillMm() != null && minDrill != null) {
            assertTrue(spec.getMinPlatedDrillMm() >= minDrill - 1e-9,
                    archive + ": plated min below overall min");
        }

        // via-in-pad is either determined (Boolean) or null — must never throw, count is >= 0.
        assertTrue(spec.getViaInPadCount() >= 0, archive + ": negative via-in-pad count");

        System.out.printf("[%s] step=%s size=%sx%s copper=%s minTrack=%s minDrill=%s vip=%s%n",
                archive, spec.getStepName(), spec.getSizeXMm(), spec.getSizeYMm(),
                spec.getCopperLayerCount(), minTrack, minDrill, spec.hasViaInPad());
    }
}

package com.deltaproto.deltaodbpp.testutil;

import com.deltaproto.deltaodbpp.OdbArchiveExtractor;
import com.deltaproto.deltaodbpp.parser.OdbParser;
import com.deltaproto.deltaodbpp.model.Job;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Locates the committed, non-customer test fixtures.
 *
 * <p>Two kinds of data are committed and safe to depend on:
 * <ul>
 *   <li>Synthetic minimal boards under {@code src/test/resources/odb} — hand-made,
 *       with no real-world provenance.</li>
 *   <li>Openly-available sample archives under {@code <repo>/examples} — the
 *       Siemens "designodb" reference design and generic KiCad/sandbox exports.</li>
 * </ul>
 *
 * <p>No customer data is ever committed, so every fixture reachable from here is
 * safe and traceable to nothing. Tests should depend only on these helpers.
 */
public final class Fixtures {

    private Fixtures() {
    }

    /** Committed synthetic fixtures (relative to the odbpp-lib module dir). */
    public static final Path RESOURCES = Paths.get("src", "test", "resources", "odb");
    /** Committed openly-available sample archives (repo-root/examples). */
    public static final Path EXAMPLES = Paths.get("..", "examples");

    /** Synthetic single-layer board, extracted. */
    public static final Path MINIMAL_ODB = RESOURCES.resolve("minimal-odb");
    /** Golden SVG for {@link #MINIMAL_ODB}. */
    public static final Path MINIMAL_ODB_REFERENCE = RESOURCES.resolve("minimal-odb-reference.svg");
    /** Synthetic two-layer (top + bottom) board archive. */
    public static final Path MINIMAL_TEST_ODB_ZIP = RESOURCES.resolve("minimal-test-odb.zip");

    /** Rich openly-available multilayer sample (rigid-flex, with components + EDA). */
    public static final String MULTILAYER_SAMPLE = "designodb_rigidflex.tgz";
    /** Small openly-available sample. */
    public static final String SMALL_SAMPLE = "sandbox-odb_wifi.tgz";

    /** Whether the named committed sample archive is present. */
    public static boolean hasSample(String archiveName) {
        return Files.exists(EXAMPLES.resolve(archiveName));
    }

    /**
     * Extract a committed openly-available sample archive into {@code tempDir}
     * and return its ODB root directory.
     */
    public static Path extractSample(String archiveName, Path tempDir) throws IOException {
        return new OdbArchiveExtractor().extract(EXAMPLES.resolve(archiveName), tempDir);
    }

    /** Extract and parse a committed openly-available sample archive. */
    public static Job parseSample(String archiveName, Path tempDir) throws IOException {
        return new OdbParser().parse(extractSample(archiveName, tempDir));
    }
}

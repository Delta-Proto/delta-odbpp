package com.deltaproto.deltaodbpp.testutil;

import com.deltaproto.deltaodbpp.OdbArchiveExtractor;
import com.deltaproto.deltaodbpp.parser.OdbParser;
import com.deltaproto.deltaodbpp.model.Job;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Locates the committed test fixtures.
 *
 * <p>Two kinds of data are committed:
 * <ul>
 *   <li>Boards under {@code src/test/resources/odb} — minimal hand-made designs, plus
 *       larger ones exercising specific geometry.</li>
 *   <li>Openly-available sample archives under {@code <repo>/examples} — the
 *       Siemens "designodb" reference design and generic KiCad/sandbox exports.</li>
 * </ul>
 *
 * <p>Tests should depend only on these helpers.
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
    /**
     * KiCad-style board whose {@code f.fab} layer carries dimension annotations reaching well
     * outside the board profile, while every other layer fits inside it. See
     * {@code PerLayerSvgAlignmentTest}.
     */
    public static final Path KICAD_FAB_OUTSIDE_PROFILE =
            RESOURCES.resolve("kicad-fab-outside-profile");
    /**
     * Metric board whose stackup lives where real archives keep it: per-layer {@code attrlist}
     * files carrying {@code .layer_dielectric}, {@code .dielectric_constant}, {@code .loss_tangent}
     * and the laminate name, plus {@code .board_thickness} on the product model. Includes the
     * awkward cases — an untyped DIELECTRIC row, a {@code .loss_tangent} of 0, the adjacent
     * dielectric's thickness written onto copper layers, and the unset sentinel on silk and paste.
     */
    public static final Path STACKUP_ATTRLIST_MM = RESOURCES.resolve("stackup-attrlist-mm");
    /**
     * Board whose {@code .board_thickness} follows the spec's {@code MIL_MICRON} rule literally
     * (1570 microns under {@code UNITS=MM}) rather than the base-unit form every observed archive
     * writes. See {@code StackupResolver} on how the two are told apart.
     */
    public static final Path STACKUP_BOARD_THICKNESS_MICRON =
            RESOURCES.resolve("stackup-board-thickness-micron");
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

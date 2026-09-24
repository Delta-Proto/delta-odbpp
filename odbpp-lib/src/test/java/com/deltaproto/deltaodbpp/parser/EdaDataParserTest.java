package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.EdaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EdaDataParserTest {

    private EdaDataParser parser;

    @BeforeEach
    void setUp() {
        parser = new EdaDataParser();
    }

    @Test
    void testParseNets(@TempDir Path tempDir) throws IOException {
        String content = """
            NET GND
            NET VCC
            NET CLK
            NET DATA
            """;

        Path dataFile = tempDir.resolve("data");
        Files.writeString(dataFile, content);

        EdaData edaData = parser.parse(dataFile);

        assertNotNull(edaData);
        assertEquals(4, edaData.getNetRecords().size());
        assertEquals("GND", edaData.getNetRecords().get(0).getName());
        assertEquals("VCC", edaData.getNetRecords().get(1).getName());
        assertEquals("CLK", edaData.getNetRecords().get(2).getName());
        assertEquals("DATA", edaData.getNetRecords().get(3).getName());
    }

    @Test
    void testParsePackages(@TempDir Path tempDir) throws IOException {
        String content = """
            PKG SOIC8
            PKG QFP100
            PKG BGA256
            """;

        Path dataFile = tempDir.resolve("data");
        Files.writeString(dataFile, content);

        EdaData edaData = parser.parse(dataFile);

        assertNotNull(edaData);
        assertEquals(3, edaData.getPackageRecords().size());
        assertEquals("SOIC8", edaData.getPackageRecords().get(0).getName());
        assertEquals("QFP100", edaData.getPackageRecords().get(1).getName());
        assertEquals("BGA256", edaData.getPackageRecords().get(2).getName());
    }

    @Test
    void testParsePackageBoundingBox(@TempDir Path tempDir) throws IOException {
        // Real ODB++ form: PKG <name> <pitch> <xmin> <ymin> <xmax> <ymax>[;attrs].
        // File declares MM, so coordinates pass through unscaled.
        String content = """
            UNITS=MM
            PKG BUSPCI 0.0 -63.85560 -5.947156 15.59560 4.54660;
            PKG R0402 1.0 -0.5 -0.25 0.5 0.25
            """;

        Path dataFile = tempDir.resolve("data");
        Files.writeString(dataFile, content);

        EdaData edaData = parser.parse(dataFile, 25.4); // INCH default, overridden by UNITS=MM

        assertEquals(2, edaData.getPackageRecords().size());

        EdaData.PackageRecord bus = edaData.getPackageRecords().get(0);
        assertEquals("BUSPCI", bus.getName());
        assertEquals(0, bus.getIndex());
        assertEquals(-63.85560, bus.getXMin(), 1e-6);
        assertEquals(-5.947156, bus.getYMin(), 1e-6);
        assertEquals(15.59560, bus.getXMax(), 1e-6);
        assertEquals(4.54660, bus.getYMax(), 1e-6);

        EdaData.PackageRecord r0402 = edaData.getPackageRecords().get(1);
        assertEquals(1, r0402.getIndex());
        assertEquals(1.0, r0402.getWidth(), 1e-9);   // xMax - xMin
        assertEquals(0.5, r0402.getHeight(), 1e-9);  // yMax - yMin
    }

    @Test
    void testParsePackageBoundingBoxInchScaledToMm(@TempDir Path tempDir) throws IOException {
        // No UNITS directive → the caller-supplied INCH scale (25.4) applies.
        String content = "PKG SOT23 0.95 -0.05 -0.05 0.05 0.05\n";

        Path dataFile = tempDir.resolve("data");
        Files.writeString(dataFile, content);

        EdaData edaData = parser.parse(dataFile, 25.4);

        EdaData.PackageRecord pkg = edaData.getPackageRecords().get(0);
        assertEquals(-0.05 * 25.4, pkg.getXMin(), 1e-9);
        assertEquals(0.05 * 25.4, pkg.getXMax(), 1e-9);
    }

    /**
     * The outline that follows a {@code PKG} line is the package body; each {@code PIN} has one
     * of its own, which must not be folded into the body. Coordinates scale like everything else.
     */
    @Test
    void testParsePackageOutlineContour_stopsAtFirstPin(@TempDir Path tempDir) throws IOException {
        String content = """
            UNITS=MM
            PKG R_0402 1.02 -3.0 -0.5 3.0 0.5;
            PRP Value '10k'
            CT
            OB -0.925 0.465 I
            OS 0.925 0.465
            OS 0.925 -0.465
            OS -0.925 -0.465
            OS -0.925 0.465
            OE
            CE
            PIN 1 S -0.51 0.0 0 E S
            CT
            OB -0.76 0.25 I
            OS -0.26 0.25
            OS -0.26 -0.25
            OS -0.76 -0.25
            OS -0.76 0.25
            OE
            CE
            PKG BARE
            """;
        Path dataFile = tempDir.resolve("data");
        Files.writeString(dataFile, content);

        EdaData edaData = parser.parse(dataFile, 25.4);

        EdaData.PackageRecord r0402 = edaData.getPackageRecords().get(0);
        assertTrue(r0402.hasOutline());
        assertEquals(1, r0402.getOutline().size(), "body outline only, not the pin's");
        var body = r0402.getOutline().get(0);
        assertEquals(com.deltaproto.deltaodbpp.model.ContourPolygon.Type.ISLAND, body.getType());
        assertEquals(-0.925, body.getXStart(), 1e-9);
        assertEquals(0.465, body.getYStart(), 1e-9);
        assertEquals(4, body.getPolygonParts().size());
        assertEquals(0.925, body.getPolygonParts().get(0).getEndX(), 1e-9);
        // The bounding box is still what the writer said — wider than the outline here.
        assertEquals(6.0, r0402.getWidth(), 1e-9);

        assertFalse(edaData.getPackageRecords().get(1).hasOutline(), "a bare PKG has no outline");
    }

    @Test
    void testParsePackageOutlinePrimitives_rectangleCircleSquare(@TempDir Path tempDir) throws IOException {
        // Inch file: every outline coordinate must be scaled to mm like the bounding box.
        String content = """
            PKG A 0.05 -0.1 -0.05 0.1 0.05
            RC -0.1 -0.05 0.2 0.1
            PIN 1 S 0 0 0 U U
            PKG B 0 -0.05 -0.05 0.05 0.05
            CR 0 0 0.05
            PKG C 0 -0.05 -0.05 0.05 0.05
            SQ 0.01 0.02 0.05
            """;
        Path dataFile = tempDir.resolve("data");
        Files.writeString(dataFile, content);

        EdaData edaData = parser.parse(dataFile, 25.4);

        var rc = edaData.getPackageRecordsByName().get("A").getOutline().get(0);
        assertEquals(-0.1 * 25.4, rc.getXStart(), 1e-9);
        assertEquals(-0.05 * 25.4, rc.getYStart(), 1e-9);
        assertEquals(4, rc.getPolygonParts().size());
        assertEquals(0.1 * 25.4, rc.getPolygonParts().get(1).getEndX(), 1e-9);
        assertEquals(0.05 * 25.4, rc.getPolygonParts().get(1).getEndY(), 1e-9);

        var cr = edaData.getPackageRecordsByName().get("B").getOutline().get(0);
        assertEquals(0.05 * 25.4, cr.getXStart(), 1e-9);
        assertEquals(2, cr.getPolygonParts().size());
        assertEquals(com.deltaproto.deltaodbpp.model.ContourPolygon.PolygonPart.Type.ARC,
                cr.getPolygonParts().get(0).getType());
        assertEquals(-0.05 * 25.4, cr.getPolygonParts().get(0).getEndX(), 1e-9);
        assertEquals(0.0, cr.getPolygonParts().get(0).getXCenter(), 1e-9);

        var sq = edaData.getPackageRecordsByName().get("C").getOutline().get(0);
        assertEquals((0.01 - 0.05) * 25.4, sq.getXStart(), 1e-9);
        assertEquals((0.02 - 0.05) * 25.4, sq.getYStart(), 1e-9);
        assertEquals((0.01 + 0.05) * 25.4, sq.getPolygonParts().get(1).getEndX(), 1e-9);
        assertEquals((0.02 + 0.05) * 25.4, sq.getPolygonParts().get(1).getEndY(), 1e-9);
    }

    @Test
    void testParseMixed(@TempDir Path tempDir) throws IOException {
        String content = """
            NET VCC
            NET GND
            PKG 0805
            NET CLK
            PKG 0402
            NET DATA
            """;

        Path dataFile = tempDir.resolve("data");
        Files.writeString(dataFile, content);

        EdaData edaData = parser.parse(dataFile);

        assertNotNull(edaData);
        assertEquals(4, edaData.getNetRecords().size());
        assertEquals(2, edaData.getPackageRecords().size());
    }

    @Test
    void testNetsByNameLookup(@TempDir Path tempDir) throws IOException {
        String content = """
            NET VCC
            NET GND
            NET CLK
            """;

        Path dataFile = tempDir.resolve("data");
        Files.writeString(dataFile, content);

        EdaData edaData = parser.parse(dataFile);

        assertNotNull(edaData.getNetRecordsByName());
        assertNotNull(edaData.getNetRecordsByName().get("VCC"));
        assertNotNull(edaData.getNetRecordsByName().get("GND"));
        assertNotNull(edaData.getNetRecordsByName().get("CLK"));
        assertNull(edaData.getNetRecordsByName().get("NONEXISTENT"));
    }

    @Test
    void testPackagesByNameLookup(@TempDir Path tempDir) throws IOException {
        String content = """
            PKG SOT23
            PKG QFN32
            """;

        Path dataFile = tempDir.resolve("data");
        Files.writeString(dataFile, content);

        EdaData edaData = parser.parse(dataFile);

        assertNotNull(edaData.getPackageRecordsByName());
        assertNotNull(edaData.getPackageRecordsByName().get("SOT23"));
        assertNotNull(edaData.getPackageRecordsByName().get("QFN32"));
        assertNull(edaData.getPackageRecordsByName().get("NONEXISTENT"));
    }

    @Test
    void testParseEmptyFile(@TempDir Path tempDir) throws IOException {
        Path dataFile = tempDir.resolve("data");
        Files.writeString(dataFile, "");

        EdaData edaData = parser.parse(dataFile);

        assertNotNull(edaData);
        assertNotNull(edaData.getNetRecords());
        assertNotNull(edaData.getPackageRecords());
        assertTrue(edaData.getNetRecords().isEmpty());
        assertTrue(edaData.getPackageRecords().isEmpty());
    }
}

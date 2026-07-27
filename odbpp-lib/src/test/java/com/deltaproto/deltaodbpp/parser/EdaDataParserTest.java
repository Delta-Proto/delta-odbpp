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

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

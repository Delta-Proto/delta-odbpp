package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.Zone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZonesParserTest {

    private ZonesParser parser;

    @BeforeEach
    void setUp() {
        parser = new ZonesParser();
    }

    @Test
    void testParseZones(@TempDir Path tempDir) throws IOException {
        String content = """
            ZONE1
            ZONE2
            ZONE3
            """;

        Path zonesFile = tempDir.resolve("zones");
        Files.writeString(zonesFile, content);

        List<Zone> zones = parser.parse(zonesFile);

        assertNotNull(zones);
        assertEquals(3, zones.size());
    }

    @Test
    void testParseEmptyZones(@TempDir Path tempDir) throws IOException {
        Path zonesFile = tempDir.resolve("zones");
        Files.writeString(zonesFile, "");

        List<Zone> zones = parser.parse(zonesFile);

        assertNotNull(zones);
        assertTrue(zones.isEmpty());
    }

    @Test
    void testParseSingleZone(@TempDir Path tempDir) throws IOException {
        String content = "ZONE_A\n";

        Path zonesFile = tempDir.resolve("zones");
        Files.writeString(zonesFile, content);

        List<Zone> zones = parser.parse(zonesFile);

        assertNotNull(zones);
        assertEquals(1, zones.size());
    }
}

package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.MiscInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MiscInfoParserTest {

    private MiscInfoParser parser;

    @BeforeEach
    void setUp() {
        parser = new MiscInfoParser();
    }

    @Test
    void testParseBasicMiscInfo(@TempDir Path tempDir) throws IOException {
        String content = """
            PRODUCT_MODEL_NAME=test_board
            ODB_VERSION_MAJOR=8
            ODB_VERSION_MINOR=1
            ODB_SOURCE=CADENCE
            CREATION_DATE=2024-01-15
            SAVE_DATE=2024-01-20
            SAVE_APP=Allegro
            SAVE_USER=engineer
            UNITS=INCH
            """;

        Path infoFile = tempDir.resolve("info");
        Files.writeString(infoFile, content);

        MiscInfo info = parser.parse(infoFile);

        assertNotNull(info);
        assertEquals("test_board", info.getProductModelName());
        assertEquals(8, info.getOdbVersionMajor());
        assertEquals(1, info.getOdbVersionMinor());
        assertEquals("CADENCE", info.getOdbSource());
        assertEquals("2024-01-15", info.getCreationDate());
        assertEquals("2024-01-20", info.getSaveDate());
        assertEquals("Allegro", info.getSaveApp());
        assertEquals("engineer", info.getSaveUser());
        assertEquals("INCH", info.getUnits());
    }

    @Test
    void testParseWithMaxUid(@TempDir Path tempDir) throws IOException {
        String content = """
            PRODUCT_MODEL_NAME=board_with_uid
            ODB_VERSION_MAJOR=8
            ODB_VERSION_MINOR=1
            UNITS=MM
            MAX_UID=123456789
            """;

        Path infoFile = tempDir.resolve("info");
        Files.writeString(infoFile, content);

        MiscInfo info = parser.parse(infoFile);

        assertNotNull(info);
        assertEquals("board_with_uid", info.getProductModelName());
        assertEquals(123456789L, info.getMaxUid());
        assertEquals("MM", info.getUnits());
    }

    @Test
    void testParseMinimalInfo(@TempDir Path tempDir) throws IOException {
        String content = """
            PRODUCT_MODEL_NAME=minimal
            ODB_VERSION_MAJOR=7
            ODB_VERSION_MINOR=0
            """;

        Path infoFile = tempDir.resolve("info");
        Files.writeString(infoFile, content);

        MiscInfo info = parser.parse(infoFile);

        assertNotNull(info);
        assertEquals("minimal", info.getProductModelName());
        assertEquals(7, info.getOdbVersionMajor());
        assertEquals(0, info.getOdbVersionMinor());
        assertNull(info.getOdbSource());
    }
}

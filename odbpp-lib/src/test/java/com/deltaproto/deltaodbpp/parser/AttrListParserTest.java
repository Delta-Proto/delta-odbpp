package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.AttrList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AttrListParserTest {

    private AttrListParser parser;

    @BeforeEach
    void setUp() {
        parser = new AttrListParser();
    }

    @Test
    void testParseBasicAttrList(@TempDir Path tempDir) throws IOException {
        String content = """
            UNITS=INCH
            .board_thickness=0.062
            .customer=ACME Corp
            """;

        Path attrListFile = tempDir.resolve("attrlist");
        Files.writeString(attrListFile, content);

        AttrList attrList = parser.parse(attrListFile);

        assertNotNull(attrList);
        assertEquals("INCH", attrList.getUnits());
        assertNotNull(attrList.getAttributes());
        assertEquals("0.062", attrList.getAttributes().get(".board_thickness"));
        assertEquals("ACME Corp", attrList.getAttributes().get(".customer"));
    }

    @Test
    void testParseMultipleAttributes(@TempDir Path tempDir) throws IOException {
        String content = """
            UNITS=MM
            .layer_count=4
            .min_trace_width=0.1
            .min_spacing=0.1
            .plating=ENIG
            .finish=lead_free
            """;

        Path attrListFile = tempDir.resolve("attrlist");
        Files.writeString(attrListFile, content);

        AttrList attrList = parser.parse(attrListFile);

        assertNotNull(attrList);
        assertEquals("MM", attrList.getUnits());
        assertEquals(5, attrList.getAttributes().size());
        assertEquals("4", attrList.getAttributes().get(".layer_count"));
        assertEquals("ENIG", attrList.getAttributes().get(".plating"));
    }

    @Test
    void testParseEmptyAttrList(@TempDir Path tempDir) throws IOException {
        String content = "UNITS=INCH\n";

        Path attrListFile = tempDir.resolve("attrlist");
        Files.writeString(attrListFile, content);

        AttrList attrList = parser.parse(attrListFile);

        assertNotNull(attrList);
        assertEquals("INCH", attrList.getUnits());
        assertTrue(attrList.getAttributes().isEmpty());
    }

    @Test
    void testParseWithUserAttributes(@TempDir Path tempDir) throws IOException {
        String content = """
            UNITS=INCH
            part_number=12345
            revision=A
            eco_number=ECO-2024-001
            """;

        Path attrListFile = tempDir.resolve("attrlist");
        Files.writeString(attrListFile, content);

        AttrList attrList = parser.parse(attrListFile);

        assertNotNull(attrList);
        assertEquals("INCH", attrList.getUnits());
        assertEquals("12345", attrList.getAttributes().get("part_number"));
        assertEquals("A", attrList.getAttributes().get("revision"));
    }
}

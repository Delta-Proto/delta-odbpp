package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.DCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DCodeParserTest {

    private DCodeParser parser;

    @BeforeEach
    void setUp() {
        parser = new DCodeParser();
    }

    @Test
    void testParseSingleDCode(@TempDir Path tempDir) throws IOException {
        String content = """
            dcode 10 r100 0 nomirror
            """;

        Path dcodesFile = tempDir.resolve("dcodes");
        Files.writeString(dcodesFile, content);

        List<DCode> dcodes = parser.parse(dcodesFile);

        assertNotNull(dcodes);
        assertEquals(1, dcodes.size());

        DCode dcode = dcodes.get(0);
        assertEquals(10, dcode.getCode());
        assertEquals("r100", dcode.getSymbolName());
        assertEquals(0.0, dcode.getAngle(), 0.001);
        assertFalse(dcode.isMirror());
    }

    @Test
    void testParseMultipleDCodes(@TempDir Path tempDir) throws IOException {
        String content = """
            dcode 10 r50 0 nomirror
            dcode 11 s80 45 nomirror
            dcode 12 rect100x50 90 mirror
            dcode 13 oval200x100 180 nomirror
            """;

        Path dcodesFile = tempDir.resolve("dcodes");
        Files.writeString(dcodesFile, content);

        List<DCode> dcodes = parser.parse(dcodesFile);

        assertNotNull(dcodes);
        assertEquals(4, dcodes.size());

        assertEquals(10, dcodes.get(0).getCode());
        assertEquals("r50", dcodes.get(0).getSymbolName());

        assertEquals(11, dcodes.get(1).getCode());
        assertEquals(45.0, dcodes.get(1).getAngle(), 0.001);

        assertEquals(12, dcodes.get(2).getCode());
        assertTrue(dcodes.get(2).isMirror());

        assertEquals(13, dcodes.get(3).getCode());
        assertEquals(180.0, dcodes.get(3).getAngle(), 0.001);
    }

    @Test
    void testParseEmptyFile(@TempDir Path tempDir) throws IOException {
        Path dcodesFile = tempDir.resolve("dcodes");
        Files.writeString(dcodesFile, "");

        List<DCode> dcodes = parser.parse(dcodesFile);

        assertNotNull(dcodes);
        assertTrue(dcodes.isEmpty());
    }

    @Test
    void testParseWithComments(@TempDir Path tempDir) throws IOException {
        String content = """
            # This is a comment
            dcode 10 r100 0 nomirror
            # Another comment
            dcode 11 s50 0 nomirror
            """;

        Path dcodesFile = tempDir.resolve("dcodes");
        Files.writeString(dcodesFile, content);

        List<DCode> dcodes = parser.parse(dcodesFile);

        assertNotNull(dcodes);
        assertEquals(2, dcodes.size());
    }
}

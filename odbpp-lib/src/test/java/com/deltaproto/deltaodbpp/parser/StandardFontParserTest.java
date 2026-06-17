package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.StandardFont;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StandardFontParserTest {

    private StandardFontParser parser;

    @BeforeEach
    void setUp() {
        parser = new StandardFontParser();
    }

    @Test
    void testParseFontHeader(@TempDir Path tempDir) throws IOException {
        String content = """
            XSIZE 0.070
            YSIZE 0.100
            OFFSET 0.010
            """;

        Path fontFile = tempDir.resolve("standard");
        Files.writeString(fontFile, content);

        StandardFont font = parser.parse(fontFile);

        assertNotNull(font);
        assertEquals(0.070, font.getXSize(), 0.001);
        assertEquals(0.100, font.getYSize(), 0.001);
        assertEquals(0.010, font.getOffset(), 0.001);
        assertTrue(font.getCharacters().isEmpty());
    }

    @Test
    void testParseCharacterDefinition(@TempDir Path tempDir) throws IOException {
        String content = """
            XSIZE 0.070
            YSIZE 0.100
            OFFSET 0.010
            CHAR A
            LINE 0.000 0.000 0.035 0.100 P R 0.010
            LINE 0.035 0.100 0.070 0.000 P R 0.010
            LINE 0.015 0.040 0.055 0.040 P R 0.010
            ECHAR
            """;

        Path fontFile = tempDir.resolve("standard");
        Files.writeString(fontFile, content);

        StandardFont font = parser.parse(fontFile);

        assertNotNull(font);
        assertEquals(1, font.getCharacters().size());

        StandardFont.CharacterDefinition charA = font.getCharacters().get(0);
        assertEquals('A', charA.getCharacter());
        assertEquals(3, charA.getLines().size());

        StandardFont.LineDefinition line1 = charA.getLines().get(0);
        assertEquals(0.000, line1.getXs(), 0.001);
        assertEquals(0.000, line1.getYs(), 0.001);
        assertEquals(0.035, line1.getXe(), 0.001);
        assertEquals(0.100, line1.getYe(), 0.001);
        assertEquals('P', line1.getPolarity());
        assertEquals('R', line1.getShape());
        assertEquals(0.010, line1.getWidth(), 0.001);
    }

    @Test
    void testParseMultipleCharacters(@TempDir Path tempDir) throws IOException {
        String content = """
            XSIZE 0.070
            YSIZE 0.100
            OFFSET 0.010
            CHAR A
            LINE 0.000 0.000 0.035 0.100 P R 0.010
            ECHAR
            CHAR B
            LINE 0.000 0.000 0.000 0.100 P R 0.010
            LINE 0.000 0.100 0.050 0.100 P R 0.010
            ECHAR
            CHAR C
            LINE 0.050 0.000 0.000 0.000 P R 0.010
            ECHAR
            """;

        Path fontFile = tempDir.resolve("standard");
        Files.writeString(fontFile, content);

        StandardFont font = parser.parse(fontFile);

        assertNotNull(font);
        assertEquals(3, font.getCharacters().size());
        assertEquals('A', font.getCharacters().get(0).getCharacter());
        assertEquals('B', font.getCharacters().get(1).getCharacter());
        assertEquals('C', font.getCharacters().get(2).getCharacter());
        assertEquals(1, font.getCharacters().get(0).getLines().size());
        assertEquals(2, font.getCharacters().get(1).getLines().size());
        assertEquals(1, font.getCharacters().get(2).getLines().size());
    }

    @Test
    void testParseEmptyFont(@TempDir Path tempDir) throws IOException {
        Path fontFile = tempDir.resolve("standard");
        Files.writeString(fontFile, "");

        StandardFont font = parser.parse(fontFile);

        assertNotNull(font);
        assertNotNull(font.getCharacters());
        assertTrue(font.getCharacters().isEmpty());
    }
}

package com.deltaproto.deltaodbpp.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OdbTextTest {

    @Test
    void latin1BytesFallBackToIso88591(@TempDir Path dir) throws IOException {
        // 0xF8 is 'ø' in Latin-1 and malformed UTF-8 (Altium dimension text).
        Path f = dir.resolve("data");
        Files.write(f, "A 3.2ø\r\nB µF\r\n".getBytes(StandardCharsets.ISO_8859_1));

        List<String> lines = OdbText.readAllLines(f);

        assertEquals("A 3.2ø", lines.get(0));
        assertEquals("B µF", lines.get(1));
    }

    @Test
    void wellFormedUtf8IsDecodedAsUtf8(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("data");
        Files.writeString(f, "R Ω 10k\nµF\n");

        assertEquals("R Ω 10k", OdbText.readAllLines(f).get(0));
        assertEquals("µF", OdbText.readAllLines(f).get(1));
    }

    @Test
    void crlfAndMissingTrailingNewlineAreHandled(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("data");
        Files.writeString(f, "one\r\ntwo\r\nthree");

        List<String> lines = OdbText.readAllLines(f);

        assertEquals(List.of("one", "two", "three"), lines);
        assertEquals(3, OdbText.lines(f).count());
        try (BufferedReader r = OdbText.newBufferedReader(f)) {
            assertEquals("one", r.readLine());
            assertEquals("two", r.readLine());
            assertEquals("three", r.readLine());
            assertNull(r.readLine());
        }
    }
}

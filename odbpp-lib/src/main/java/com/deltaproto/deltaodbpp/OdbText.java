package com.deltaproto.deltaodbpp;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Central place for reading ODB++ text files.
 *
 * <p>ODB++ is a byte-oriented ASCII/Latin-1 format, <em>not</em> UTF-8. Real jobs
 * routinely carry high-bit Latin-1 bytes in property strings and descriptions —
 * degree signs ({@code °} = 0xF8), micro ({@code µ}), ohm, plus/minus, accented
 * vendor names, and so on. Reading such a file with the JVM default charset (UTF-8)
 * throws {@link java.nio.charset.MalformedInputException} on the first stray byte,
 * which — because parsing is nested — silently discards an entire step or layer and
 * makes an otherwise-valid job look empty.
 *
 * <p>ISO-8859-1 (Latin-1) maps every one of the 256 byte values to a character, so
 * decoding can never fail. Always read ODB++ text through these helpers rather than
 * {@code Files.newBufferedReader}/{@code Files.lines}, whose no-charset overloads
 * default to UTF-8.
 */
public final class OdbText {

    /** The charset every ODB++ text file is read with. */
    public static final Charset CHARSET = StandardCharsets.ISO_8859_1;

    private OdbText() {}

    /** Open a reader over an ODB++ text file using the Latin-1 charset. */
    public static BufferedReader newBufferedReader(Path file) throws IOException {
        return Files.newBufferedReader(file, CHARSET);
    }

    /** Stream the lines of an ODB++ text file using the Latin-1 charset. */
    public static Stream<String> lines(Path file) throws IOException {
        return Files.lines(file, CHARSET);
    }

    /** Read all lines of an ODB++ text file using the Latin-1 charset. */
    public static List<String> readAllLines(Path file) throws IOException {
        return Files.readAllLines(file, CHARSET);
    }
}

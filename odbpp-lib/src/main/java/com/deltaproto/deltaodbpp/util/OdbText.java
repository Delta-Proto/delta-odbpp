package com.deltaproto.deltaodbpp.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Charset-tolerant reading of ODB++ text files.
 *
 * <p>The ODB++ format is nominally ASCII, but EDA tools routinely write free text
 * (dimension strings, attribute values, package names) in the platform's legacy
 * 8-bit code page. Altium Designer, for instance, emits the Latin-1 byte
 * {@code 0xF8} for a diameter sign (ø) in dimension text, which is not valid
 * UTF-8 and makes {@link Files#readAllLines(Path)} throw
 * {@code MalformedInputException}. Other tools (KiCad) write genuine UTF-8.
 *
 * <p>This helper decodes as UTF-8 when the bytes are well-formed UTF-8 and falls
 * back to ISO-8859-1 otherwise. ISO-8859-1 maps every byte to a character, so the
 * fallback can never fail; a Windows-1252 file will merely show a few control-range
 * characters for its curly quotes and the like, which is harmless in a parser.
 */
public final class OdbText {

    private OdbText() {
    }

    /** Read a whole text file into a string, decoding as described in the class comment. */
    public static String readString(Path file) throws IOException {
        return decode(Files.readAllBytes(file));
    }

    /** Read a text file as lines (split on LF, with any trailing CR removed), decoding as described in the class comment. */
    public static List<String> readAllLines(Path file) throws IOException {
        return Arrays.asList(decode(Files.readAllBytes(file)).split("\\r?\\n", -1));
    }

    /** Decode bytes as UTF-8 if well-formed, otherwise as ISO-8859-1. */
    public static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    /** Charset-tolerant replacement for {@link Files#newBufferedReader(Path)}; the file is read eagerly. */
    public static BufferedReader newBufferedReader(Path file) throws IOException {
        return new BufferedReader(new StringReader(readString(file)));
    }

    /** Charset-tolerant replacement for {@link Files#lines(Path)}; the file is read eagerly. */
    public static Stream<String> lines(Path file) throws IOException {
        return readAllLines(file).stream();
    }
}

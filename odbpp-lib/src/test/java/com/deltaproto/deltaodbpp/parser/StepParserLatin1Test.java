package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.Layer;
import com.deltaproto.deltaodbpp.model.Step;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for a job that failed to import because a high-bit Latin-1 byte
 * (a degree sign, 0xF8) lived in {@code eda/data}. ODB++ is a Latin-1 format, so
 * reading it as UTF-8 threw {@code MalformedInputException}, which discarded the
 * whole step — layers and all — and made a valid board look empty.
 */
class StepParserLatin1Test {

    @Test
    void latin1ByteInEdaDataDoesNotDropTheStep(@TempDir Path tmp) throws IOException {
        Path stepDir = Files.createDirectories(tmp.resolve("pcb"));

        Files.write(stepDir.resolve("stephdr"), "UNITS=MM\n".getBytes(StandardCharsets.ISO_8859_1));

        // eda/data with a bare 0xF8 byte inside a property string, exactly as a
        // real Altium-exported "60°" property produced. This is not valid UTF-8.
        Path edaDir = Files.createDirectories(stepDir.resolve("eda"));
        byte[] eda = new byte[]{
                'P', 'R', 'P', ' ', 's', 't', 'r', 'i', 'n', 'g', ' ',
                '\'', '6', '0', (byte) 0xF8, '\'', '\n'
        };
        Files.write(edaDir.resolve("data"), eda);

        // A minimal component layer that must survive the eda/data mishap.
        Path compDir = Files.createDirectories(stepDir.resolve("layers/comp_+_top"));
        String components = "UNITS=MM\n"
                + "CMP 0 1.0 2.0 0 N R1 0402 ;0=1\n"
                + "TOP 0 1.0 2.0 0 N 0 0 1\n";
        Files.write(compDir.resolve("components"), components.getBytes(StandardCharsets.ISO_8859_1));

        Step step = new StepParser().parse(stepDir);

        assertNotNull(step.getLayersByName(), "layers must be parsed despite the bad eda/data");
        Layer comp = step.getLayersByName().get("comp_+_top");
        assertNotNull(comp, "component layer must survive a malformed optional sub-file");
        assertNotNull(comp.getComponents(), "component layer must have parsed components");
        assertFalse(comp.getComponents().getComponents().isEmpty(),
                "the placed component must be present");
        assertEquals("R1", comp.getComponents().getComponents().get(0).getCompName());
    }
}

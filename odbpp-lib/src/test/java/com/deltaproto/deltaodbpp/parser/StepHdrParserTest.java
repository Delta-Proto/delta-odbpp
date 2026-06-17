package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.StepHdr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StepHdrParserTest {

    private StepHdrParser parser;

    @BeforeEach
    void setUp() {
        parser = new StepHdrParser();
    }

    @Test
    void testParseBasicStepHdr(@TempDir Path tempDir) throws IOException {
        String content = """
            UNITS=INCH
            X_DATUM=0.0
            Y_DATUM=0.0
            ID=1
            """;

        Path stepHdrFile = tempDir.resolve("stephdr");
        Files.writeString(stepHdrFile, content);

        StepHdr stepHdr = parser.parse(stepHdrFile);

        assertNotNull(stepHdr);
        assertEquals("INCH", stepHdr.getUnits());
        assertEquals(0.0, stepHdr.getXDatum(), 0.001);
        assertEquals(0.0, stepHdr.getYDatum(), 0.001);
        assertEquals(1, stepHdr.getId());
    }

    @Test
    void testParseWithStepRepeats(@TempDir Path tempDir) throws IOException {
        String content = """
            UNITS=MM
            X_DATUM=10.0
            Y_DATUM=20.0
            STEP-REPEAT {
               NAME=pcb
               X=0.0
               Y=0.0
            }
            STEP-REPEAT {
               NAME=pcb
               X=100.0
               Y=0.0
            }
            """;

        Path stepHdrFile = tempDir.resolve("stephdr");
        Files.writeString(stepHdrFile, content);

        StepHdr stepHdr = parser.parse(stepHdrFile);

        assertNotNull(stepHdr);
        assertEquals("MM", stepHdr.getUnits());
        assertEquals(10.0, stepHdr.getXDatum(), 0.001);
        assertNotNull(stepHdr.getStepRepeats());
        assertEquals(2, stepHdr.getStepRepeats().size());

        StepHdr.StepRepeat sr1 = stepHdr.getStepRepeats().get(0);
        assertEquals("pcb", sr1.getName());
        assertEquals(0.0, sr1.getX(), 0.001);
        assertEquals(0.0, sr1.getY(), 0.001);

        StepHdr.StepRepeat sr2 = stepHdr.getStepRepeats().get(1);
        assertEquals("pcb", sr2.getName());
        assertEquals(100.0, sr2.getX(), 0.001);
    }

    @Test
    void testParseEmptyStepHdr(@TempDir Path tempDir) throws IOException {
        Path stepHdrFile = tempDir.resolve("stephdr");
        Files.writeString(stepHdrFile, "");

        StepHdr stepHdr = parser.parse(stepHdrFile);

        assertNotNull(stepHdr);
        assertNotNull(stepHdr.getStepRepeats());
        assertTrue(stepHdr.getStepRepeats().isEmpty());
    }
}

package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.Step;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StepParserTest {

    @Test
    void unreadableEdaDataDoesNotDropTheStep(@TempDir Path tempDir) throws IOException {
        Path stepDir = tempDir.resolve("pcb");
        Files.createDirectories(stepDir.resolve("layers").resolve("top"));
        Files.writeString(stepDir.resolve("stephdr"), "UNITS=MM\nX_DATUM=0\nY_DATUM=0\n");
        Files.writeString(stepDir.resolve("layers").resolve("top").resolve("features"),
                "UNITS=MM\n$0 r100\n#\nP 1 2 0 P 0 0\n");
        // eda/data exists but cannot be read as a file.
        Files.createDirectories(stepDir.resolve("eda").resolve("data"));

        Step step = new StepParser().parse(stepDir);

        assertNotNull(step);
        assertNull(step.getEdaData());
        assertEquals("MM", step.getStepHdr().getUnits());
        assertEquals(1, step.getLayersByName().get("top").getFeatures().getFeatures().size());
    }
}

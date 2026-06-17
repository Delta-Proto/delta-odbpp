package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.Matrix;
import com.deltaproto.deltaodbpp.model.MatrixLayer;
import com.deltaproto.deltaodbpp.model.Step;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MatrixParserTest {

    private MatrixParser parser;

    @BeforeEach
    void setUp() {
        parser = new MatrixParser();
    }

    @Test
    void testParseSteps(@TempDir Path tempDir) throws IOException {
        String content = """
            STEP {
               COL=1
               ID=0
               NAME=pcb
            }
            STEP {
               COL=2
               ID=1
               NAME=panel
            }
            """;

        Path matrixFile = tempDir.resolve("matrix");
        Files.writeString(matrixFile, content);

        Matrix matrix = parser.parse(matrixFile);

        assertNotNull(matrix);
        assertEquals(2, matrix.getSteps().size());

        Step step1 = matrix.getSteps().get(0);
        assertEquals(1, step1.getCol());
        assertEquals(0, step1.getId());
        assertEquals("pcb", step1.getName());

        Step step2 = matrix.getSteps().get(1);
        assertEquals(2, step2.getCol());
        assertEquals(1, step2.getId());
        assertEquals("panel", step2.getName());
    }

    @Test
    void testParseLayers(@TempDir Path tempDir) throws IOException {
        String content = """
            LAYER {
               ROW=1
               CONTEXT=BOARD
               TYPE=SIGNAL
               NAME=top
               POLARITY=POSITIVE
            }
            LAYER {
               ROW=2
               CONTEXT=BOARD
               TYPE=POWER_GROUND
               NAME=gnd
               POLARITY=POSITIVE
               COLOR=65280
            }
            LAYER {
               ROW=3
               CONTEXT=BOARD
               TYPE=DRILL
               NAME=drill
               POLARITY=POSITIVE
               ADD_TYPE=PLATED
            }
            """;

        Path matrixFile = tempDir.resolve("matrix");
        Files.writeString(matrixFile, content);

        Matrix matrix = parser.parse(matrixFile);

        assertNotNull(matrix);
        assertEquals(3, matrix.getLayers().size());

        MatrixLayer layer1 = matrix.getLayers().get(0);
        assertEquals(1, layer1.getRow());
        assertEquals("BOARD", layer1.getContext());
        assertEquals("SIGNAL", layer1.getType());
        assertEquals("top", layer1.getName());
        assertEquals("POSITIVE", layer1.getPolarity());

        MatrixLayer layer2 = matrix.getLayers().get(1);
        assertEquals("POWER_GROUND", layer2.getType());
        assertEquals(65280, layer2.getColor());

        MatrixLayer layer3 = matrix.getLayers().get(2);
        assertEquals("DRILL", layer3.getType());
        assertEquals("PLATED", layer3.getAddType());
    }

    @Test
    void testParseMixed(@TempDir Path tempDir) throws IOException {
        String content = """
            STEP {
               COL=1
               NAME=main
            }
            LAYER {
               ROW=1
               TYPE=SIGNAL
               NAME=signal_1
            }
            LAYER {
               ROW=2
               TYPE=SOLDER_MASK
               NAME=sm_top
            }
            """;

        Path matrixFile = tempDir.resolve("matrix");
        Files.writeString(matrixFile, content);

        Matrix matrix = parser.parse(matrixFile);

        assertNotNull(matrix);
        assertEquals(1, matrix.getSteps().size());
        assertEquals(2, matrix.getLayers().size());
        assertEquals("main", matrix.getSteps().get(0).getName());
        assertEquals("signal_1", matrix.getLayers().get(0).getName());
        assertEquals("sm_top", matrix.getLayers().get(1).getName());
    }

    @Test
    void testParseEmptyMatrix(@TempDir Path tempDir) throws IOException {
        Path matrixFile = tempDir.resolve("matrix");
        Files.writeString(matrixFile, "");

        Matrix matrix = parser.parse(matrixFile);

        assertNotNull(matrix);
        assertTrue(matrix.getSteps().isEmpty());
        assertTrue(matrix.getLayers().isEmpty());
    }
}

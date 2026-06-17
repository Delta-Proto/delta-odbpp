package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.impedance.ImpedanceFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImpedanceParserTest {

    private ImpedanceParser parser;

    @BeforeEach
    void setUp() {
        parser = new ImpedanceParser();
    }

    @Test
    void testParseBasicImpedance(@TempDir Path tempDir) throws IOException {
        String content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Impedances Version="1.0" MaxImpIdValUsed="3">
            </Impedances>
            """;

        Path impedanceFile = tempDir.resolve("impedance.xml");
        Files.writeString(impedanceFile, content);

        ImpedanceFile impFile = parser.parse(impedanceFile);

        assertNotNull(impFile);
        assertEquals("1.0", impFile.getVersion());
        assertEquals(3, impFile.getMaxImpIdValUsed());
    }

    @Test
    void testParseWithDescriptor(@TempDir Path tempDir) throws IOException {
        String content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Impedances Version="2.0" MaxImpIdValUsed="5">
                <Descriptor Id="1" TraceLayerName="TOP"/>
            </Impedances>
            """;

        Path impedanceFile = tempDir.resolve("impedance.xml");
        Files.writeString(impedanceFile, content);

        ImpedanceFile impFile = parser.parse(impedanceFile);

        assertNotNull(impFile);
        assertEquals("2.0", impFile.getVersion());
        assertNotNull(impFile.getDescriptor());
        assertEquals(1, impFile.getDescriptor().size());
        assertEquals(1, impFile.getDescriptor().get(0).getId());
        assertEquals("TOP", impFile.getDescriptor().get(0).getTraceLayerName());
    }
}

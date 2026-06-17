package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.AttributeDefinition;
import com.deltaproto.deltaodbpp.model.AttributeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AttributeDefinitionParserTest {

    private AttributeDefinitionParser parser;

    @BeforeEach
    void setUp() {
        parser = new AttributeDefinitionParser();
    }

    @Test
    void testParseBooleanAttribute(@TempDir Path tempDir) throws IOException {
        String content = """
            BOOLEAN {
               NAME=.test_point
               PROMPT=Is Test Point
               ENTITY=FEATURE
               DEF=NO
            }
            """;

        Path attrFile = tempDir.resolve("sysattr.testpoint");
        Files.writeString(attrFile, content);

        Map<String, AttributeDefinition> defs = parser.parse(attrFile);

        assertNotNull(defs);
        assertEquals(1, defs.size());

        AttributeDefinition def = defs.get(".test_point");
        assertNotNull(def);
        assertEquals(AttributeType.BOOLEAN, def.getType());
        assertEquals(".test_point", def.getName());
        assertEquals("Is Test Point", def.getPrompt());
        assertEquals("NO", def.getDefaultValue());
    }

    @Test
    void testParseTextAttribute(@TempDir Path tempDir) throws IOException {
        String content = """
            TEXT {
               NAME=description
               PROMPT=Component Description
               ENTITY=COMPONENT
               MIN_LEN=1
               MAX_LEN=255
            }
            """;

        Path attrFile = tempDir.resolve("userattr");
        Files.writeString(attrFile, content);

        Map<String, AttributeDefinition> defs = parser.parse(attrFile);

        assertNotNull(defs);
        AttributeDefinition def = defs.get("description");
        assertNotNull(def);
        assertEquals(AttributeType.TEXT, def.getType());
        assertEquals(1, def.getMinLen());
        assertEquals(255, def.getMaxLen());
    }

    @Test
    void testParseIntegerAttribute(@TempDir Path tempDir) throws IOException {
        String content = """
            INTEGER {
               NAME=pin_count
               PROMPT=Number of Pins
               ENTITY=PACKAGE
               MIN_VAL=1
               MAX_VAL=2000
               DEF=2
            }
            """;

        Path attrFile = tempDir.resolve("userattr");
        Files.writeString(attrFile, content);

        Map<String, AttributeDefinition> defs = parser.parse(attrFile);

        AttributeDefinition def = defs.get("pin_count");
        assertNotNull(def);
        assertEquals(AttributeType.INTEGER, def.getType());
        assertEquals(1, def.getMinValInt());
        assertEquals(2000, def.getMaxValInt());
    }

    @Test
    void testParseFloatAttribute(@TempDir Path tempDir) throws IOException {
        String content = """
            FLOAT {
               NAME=impedance
               PROMPT=Trace Impedance
               ENTITY=NET
               MIN_VAL=0.0
               MAX_VAL=1000.0
               UNIT_TYPE=RESISTANCE
               UNITS=OHM
            }
            """;

        Path attrFile = tempDir.resolve("userattr");
        Files.writeString(attrFile, content);

        Map<String, AttributeDefinition> defs = parser.parse(attrFile);

        AttributeDefinition def = defs.get("impedance");
        assertNotNull(def);
        assertEquals(AttributeType.FLOAT, def.getType());
        assertEquals(0.0, def.getMinValFloat(), 0.001);
        assertEquals(1000.0, def.getMaxValFloat(), 0.001);
        assertEquals("OHM", def.getUnits());
    }

    @Test
    void testParseOptionAttribute(@TempDir Path tempDir) throws IOException {
        String content = """
            OPTION {
               NAME=layer_type
               PROMPT=Layer Type
               ENTITY=LAYER
               OPTIONS=SIGNAL;POWER;GROUND;MIXED
               DEF=SIGNAL
            }
            """;

        Path attrFile = tempDir.resolve("userattr");
        Files.writeString(attrFile, content);

        Map<String, AttributeDefinition> defs = parser.parse(attrFile);

        AttributeDefinition def = defs.get("layer_type");
        assertNotNull(def);
        assertEquals(AttributeType.OPTION, def.getType());
        assertNotNull(def.getOptions());
        assertEquals(4, def.getOptions().size());
        assertTrue(def.getOptions().contains("SIGNAL"));
        assertTrue(def.getOptions().contains("GROUND"));
    }

    @Test
    void testParseMultipleAttributes(@TempDir Path tempDir) throws IOException {
        String content = """
            BOOLEAN {
               NAME=.smd
               ENTITY=FEATURE
            }
            TEXT {
               NAME=part_number
               ENTITY=COMPONENT
            }
            INTEGER {
               NAME=revision
               ENTITY=JOB
            }
            """;

        Path attrFile = tempDir.resolve("sysattr");
        Files.writeString(attrFile, content);

        Map<String, AttributeDefinition> defs = parser.parse(attrFile);

        assertEquals(3, defs.size());
        assertNotNull(defs.get(".smd"));
        assertNotNull(defs.get("part_number"));
        assertNotNull(defs.get("revision"));
    }

    @Test
    void testParseWithMetadataLines(@TempDir Path tempDir) throws IOException {
        String content = """
            UNITS=INCH

            BOOLEAN {
               NAME=.fiducial
               ENTITY=FEATURE
            }
            """;

        Path attrFile = tempDir.resolve("sysattr");
        Files.writeString(attrFile, content);

        Map<String, AttributeDefinition> defs = parser.parse(attrFile);

        assertEquals(1, defs.size());
        assertNotNull(defs.get(".fiducial"));
    }
}

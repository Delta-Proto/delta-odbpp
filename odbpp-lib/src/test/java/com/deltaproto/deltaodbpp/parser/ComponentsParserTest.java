package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.Component;
import com.deltaproto.deltaodbpp.model.Components;
import com.deltaproto.deltaodbpp.model.MirrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ComponentsParserTest {

    private ComponentsParser parser;

    @BeforeEach
    void setUp() {
        parser = new ComponentsParser();
    }

    @Test
    void testParseBasicComponent(@TempDir Path tempDir) throws IOException {
        String content = """
            # Component file
            CMP 0 1.500 2.000 0.0 N U1 SOIC8
            """;

        Path componentsFile = tempDir.resolve("components");
        Files.writeString(componentsFile, content);

        Components components = parser.parse(componentsFile);

        assertNotNull(components);
        assertEquals(1, components.getComponents().size());

        Component comp = components.getComponents().get(0);
        assertEquals(0, comp.getPkgRef());
        assertEquals(1.500, comp.getX(), 0.001);
        assertEquals(2.000, comp.getY(), 0.001);
        assertEquals(0.0, comp.getRotation(), 0.001);
        assertEquals(MirrorType.NOT_MIRRORED, comp.getMirror());
        assertEquals("U1", comp.getCompName());
        assertEquals("SOIC8", comp.getPartName());
    }

    @Test
    void testParseComponentWithRotation(@TempDir Path tempDir) throws IOException {
        String content = """
            CMP 1 10.0 20.0 90.0 N R1 0805
            CMP 2 15.0 25.0 180.0 M C1 0402
            """;

        Path componentsFile = tempDir.resolve("components");
        Files.writeString(componentsFile, content);

        Components components = parser.parse(componentsFile);

        assertNotNull(components);
        assertEquals(2, components.getComponents().size());

        Component r1 = components.getComponents().get(0);
        assertEquals(90.0, r1.getRotation(), 0.001);
        assertEquals(MirrorType.NOT_MIRRORED, r1.getMirror());
        assertEquals("R1", r1.getCompName());

        Component c1 = components.getComponents().get(1);
        assertEquals(180.0, c1.getRotation(), 0.001);
        assertEquals(MirrorType.MIRRORED, c1.getMirror());
        assertEquals("C1", c1.getCompName());
    }

    @Test
    void testParseComponentWithProperties(@TempDir Path tempDir) throws IOException {
        String content = """
            CMP 0 1.0 2.0 0.0 N U1 QFP100
            PRP MANUFACTURER 'Texas Instruments'
            PRP VALUE '1.2V'
            """;

        Path componentsFile = tempDir.resolve("components");
        Files.writeString(componentsFile, content);

        Components components = parser.parse(componentsFile);

        assertNotNull(components);
        assertEquals(1, components.getComponents().size());

        Component comp = components.getComponents().get(0);
        assertNotNull(comp.getPropertyRecords());
        assertEquals(2, comp.getPropertyRecords().size());
        assertEquals("MANUFACTURER", comp.getPropertyRecords().get(0).getName());
        assertEquals("Texas Instruments", comp.getPropertyRecords().get(0).getValue());
        assertEquals("VALUE", comp.getPropertyRecords().get(1).getName());
        assertEquals("1.2V", comp.getPropertyRecords().get(1).getValue());
    }

    @Test
    void testParseMultipleComponentsWithProperties(@TempDir Path tempDir) throws IOException {
        String content = """
            CMP 0 1.0 1.0 0.0 N U1 SOT23
            PRP MANUFACTURER 'NXP'
            CMP 1 5.0 5.0 45.0 N R1 0805
            PRP VALUE '10K'
            PRP TOLERANCE '5%'
            CMP 2 10.0 10.0 90.0 M C1 0402
            """;

        Path componentsFile = tempDir.resolve("components");
        Files.writeString(componentsFile, content);

        Components components = parser.parse(componentsFile);

        assertNotNull(components);
        assertEquals(3, components.getComponents().size());

        assertEquals(1, components.getComponents().get(0).getPropertyRecords().size());
        assertEquals(2, components.getComponents().get(1).getPropertyRecords().size());
        // C1 has no properties
        assertTrue(components.getComponents().get(2).getPropertyRecords() == null ||
                   components.getComponents().get(2).getPropertyRecords().isEmpty());
    }

    @Test
    void testParseEmptyComponents(@TempDir Path tempDir) throws IOException {
        String content = """
            # Empty components file
            # No components defined
            """;

        Path componentsFile = tempDir.resolve("components");
        Files.writeString(componentsFile, content);

        Components components = parser.parse(componentsFile);

        assertNotNull(components);
        assertTrue(components.getComponents().isEmpty());
    }

    @Test
    void testParseNegativeCoordinates(@TempDir Path tempDir) throws IOException {
        String content = """
            CMP 0 -1.500 -2.000 270.0 N U1 BGA256
            """;

        Path componentsFile = tempDir.resolve("components");
        Files.writeString(componentsFile, content);

        Components components = parser.parse(componentsFile);

        assertNotNull(components);
        assertEquals(1, components.getComponents().size());

        Component comp = components.getComponents().get(0);
        assertEquals(-1.500, comp.getX(), 0.001);
        assertEquals(-2.000, comp.getY(), 0.001);
    }
}

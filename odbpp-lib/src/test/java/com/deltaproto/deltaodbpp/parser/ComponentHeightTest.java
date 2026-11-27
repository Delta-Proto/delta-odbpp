package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.AttributeType;
import com.deltaproto.deltaodbpp.model.Component;
import com.deltaproto.deltaodbpp.model.Components;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Layer;
import com.deltaproto.deltaodbpp.model.Step;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@code .comp_height} is a length in the components file's unit — its own {@code UNITS} line,
 * else the step's — and is exposed in mm as {@link Component#getHeightMm()}.
 */
class ComponentHeightTest {

    private static final String BODY = """
            @0 .comp_mount_type
            @1 .comp_height
            @2 .part_desc
            &0 resistor
            CMP 0 1.0 2.0 0 N R1 RES ;0=1,1=0.45,2=0;ID=17
            CMP 0 3.0 4.0 0 N R2 RES ;0=1,1=0
            CMP 0 5.0 6.0 0 N TP1 TESTPOINT ;0=1
            CMP 0 7.0 8.0 0 N J1 HDR;1=0.1,3;ID=9
            """;

    @Test
    void mmFile_heightIsTakenAsWritten(@TempDir Path tmp) throws IOException {
        Components c = parse(tmp, "UNITS=MM\n" + BODY, 25.4);
        assertEquals("MM", c.getUnits());
        assertEquals(0.45, comp(c, "R1").getHeightMm(), 1e-12);
        assertEquals(1.0, comp(c, "R1").getX(), 1e-12, "the file's UNITS also governs positions");
    }

    @Test
    void inchFile_heightIsConvertedToMm(@TempDir Path tmp) throws IOException {
        Components c = parse(tmp, "UNITS=INCH\n" + BODY, 1.0);
        assertEquals("INCH", c.getUnits());
        assertEquals(0.45 * 25.4, comp(c, "R1").getHeightMm(), 1e-9);
        assertEquals(2.54, comp(c, "J1").getHeightMm(), 1e-9);
    }

    @Test
    void fileWithoutUnits_followsTheStep(@TempDir Path tmp) throws IOException {
        assertEquals(0.45 * 25.4, comp(parse(tmp, BODY, 25.4), "R1").getHeightMm(), 1e-9);
        assertEquals(0.45, comp(parse(tmp, BODY, 1.0), "R1").getHeightMm(), 1e-12);
    }

    @Test
    void zeroIsKept_absentIsNull_andTheTablesAreExposed(@TempDir Path tmp) throws IOException {
        Components c = parse(tmp, "UNITS=MM\n" + BODY, 1.0);
        assertEquals(0.0, comp(c, "R2").getHeightMm());
        assertNull(comp(c, "TP1").getHeightMm());

        assertEquals(".comp_height", c.getAttributeNames().get(1));
        assertEquals("resistor", c.getAttributeTexts().get(0));
        Component r1 = comp(c, "R1");
        assertEquals("17", r1.getUniqueId());
        assertEquals("RES", r1.getPartName());
        assertEquals("0.45", r1.getAttributeValue(1));
        Component j1 = comp(c, "J1");
        assertEquals("HDR", j1.getPartName(), "attributes glued to the part name are split off");
        assertTrue(j1.getAttributes().stream()
                .anyMatch(a -> a.getAttributeIndex() == 3 && a.getType() == AttributeType.BOOLEAN));
    }

    @Test
    void inchSample_rigidFlex(@TempDir Path tmp) throws IOException {
        // Declares UNITS=INCH; C62 is written as 0.012992 in.
        assumeTrue(Fixtures.hasSample(Fixtures.MULTILAYER_SAMPLE));
        Component c62 = sampleComponent(Fixtures.parseSample(Fixtures.MULTILAYER_SAMPLE, tmp), "C62");
        assertEquals(0.33, c62.getHeightMm(), 1e-4);
    }

    @Test
    void unitlessSample_wifi_isInchByTheStep(@TempDir Path tmp) throws IOException {
        // No UNITS anywhere: inch by default. U1 is 0.0965 in, P1 is written as 0.0000.
        assumeTrue(Fixtures.hasSample(Fixtures.SMALL_SAMPLE));
        Job job = Fixtures.parseSample(Fixtures.SMALL_SAMPLE, tmp);
        assertEquals(0.0965 * 25.4, sampleComponent(job, "U1").getHeightMm(), 1e-9);
        assertEquals(0.0, sampleComponent(job, "P1").getHeightMm());
    }

    private static Components parse(Path tmp, String content, double stepScale) throws IOException {
        Path file = Files.createTempFile(tmp, "components", "");
        Files.writeString(file, content);
        return new ComponentsParser().parse(file, stepScale);
    }

    private static Component comp(Components components, String refdes) {
        return components.getComponents().stream()
                .filter(c -> refdes.equals(c.getCompName())).findFirst().orElseThrow();
    }

    private static Component sampleComponent(Job job, String refdes) {
        for (Step step : job.getSteps().values()) {
            for (Layer layer : step.getLayersByName().values()) {
                if (layer.getComponents() == null) continue;
                for (Component c : layer.getComponents().getComponents()) {
                    if (refdes.equals(c.getCompName())) return c;
                }
            }
        }
        throw new AssertionError("no component " + refdes);
    }
}

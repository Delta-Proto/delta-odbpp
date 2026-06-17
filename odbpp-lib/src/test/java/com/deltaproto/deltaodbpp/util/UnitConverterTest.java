package com.deltaproto.deltaodbpp.util;

import org.junit.jupiter.api.Test;

import static com.deltaproto.deltaodbpp.util.UnitConverter.Unit.*;
import static org.junit.jupiter.api.Assertions.*;

class UnitConverterTest {

    @Test
    void testInchToMm() {
        double result = UnitConverter.convert(1.0, INCH, MM);
        assertEquals(25.4, result, 0.001);
    }

    @Test
    void testMmToInch() {
        double result = UnitConverter.convert(25.4, MM, INCH);
        assertEquals(1.0, result, 0.001);
    }

    @Test
    void testInchToMil() {
        double result = UnitConverter.convert(1.0, INCH, MIL);
        assertEquals(1000.0, result, 0.001);
    }

    @Test
    void testMilToInch() {
        double result = UnitConverter.convert(1000.0, MIL, INCH);
        assertEquals(1.0, result, 0.001);
    }

    @Test
    void testInchToMicron() {
        double result = UnitConverter.convert(1.0, INCH, MICRON);
        assertEquals(25400.0, result, 0.001);
    }

    @Test
    void testMicronToInch() {
        double result = UnitConverter.convert(25400.0, MICRON, INCH);
        assertEquals(1.0, result, 0.001);
    }

    @Test
    void testMmToMil() {
        double result = UnitConverter.convert(25.4, MM, MIL);
        assertEquals(1000.0, result, 0.1);
    }

    @Test
    void testSameUnit() {
        double result = UnitConverter.convert(5.5, MM, MM);
        assertEquals(5.5, result, 0.001);
    }

    @Test
    void testConvertWithStringUnits() {
        double result = UnitConverter.convert(1.0, "INCH", "MM");
        assertEquals(25.4, result, 0.001);
    }

    @Test
    void testToInches() {
        assertEquals(1.0, UnitConverter.toInches(25.4, MM), 0.001);
        assertEquals(1.0, UnitConverter.toInches(1000.0, MIL), 0.001);
        assertEquals(1.0, UnitConverter.toInches(25400.0, MICRON), 0.001);
    }

    @Test
    void testFromInches() {
        assertEquals(25.4, UnitConverter.fromInches(1.0, MM), 0.001);
        assertEquals(1000.0, UnitConverter.fromInches(1.0, MIL), 0.001);
        assertEquals(25400.0, UnitConverter.fromInches(1.0, MICRON), 0.001);
    }

    @Test
    void testParseUnit() {
        assertEquals(INCH, UnitConverter.parseUnit("INCH"));
        assertEquals(INCH, UnitConverter.parseUnit("inches"));
        assertEquals(MM, UnitConverter.parseUnit("MM"));
        assertEquals(MM, UnitConverter.parseUnit("millimeter"));
        assertEquals(MIL, UnitConverter.parseUnit("MIL"));
        assertEquals(MIL, UnitConverter.parseUnit("mils"));
        assertEquals(MICRON, UnitConverter.parseUnit("MICRON"));
        assertEquals(MICRON, UnitConverter.parseUnit("UM"));
        assertEquals(INCH, UnitConverter.parseUnit(null)); // Default
        assertEquals(INCH, UnitConverter.parseUnit("UNKNOWN")); // Default
    }

    @Test
    void testNormalizeAngle() {
        assertEquals(0.0, UnitConverter.normalizeAngle(0.0), 0.001);
        assertEquals(45.0, UnitConverter.normalizeAngle(45.0), 0.001);
        assertEquals(180.0, UnitConverter.normalizeAngle(180.0), 0.001);
        assertEquals(0.0, UnitConverter.normalizeAngle(360.0), 0.001);
        assertEquals(90.0, UnitConverter.normalizeAngle(450.0), 0.001);
        assertEquals(270.0, UnitConverter.normalizeAngle(-90.0), 0.001);
        assertEquals(180.0, UnitConverter.normalizeAngle(-180.0), 0.001);
    }
}

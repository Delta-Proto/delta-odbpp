package com.deltaproto.deltaodbpp.spec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SymbolShape} name parsing — in particular the HOLE family, which drives
 * min-drill measurement on drill layers that carry no tools file.
 */
class SymbolShapeTest {

    private static final double MM = 0.001;    // UNITS=MM: symbol dims are microns
    private static final double INCH = 0.0254; // UNITS=INCH: symbol dims are mils

    @Test
    void holeParsesToRoundWithDiameterInMm_microns() {
        SymbolShape s = SymbolShape.parse("hole300", MM);
        assertNotNull(s);
        assertEquals(SymbolShape.Kind.ROUND, s.kind);
        assertTrue(s.isRound());
        assertEquals(0.300, s.width, 1e-9); // 300 microns → 0.3 mm
    }

    @Test
    void holeParsesToRoundWithDiameterInMm_mils() {
        SymbolShape s = SymbolShape.parse("hole12", INCH);
        assertNotNull(s);
        assertEquals(SymbolShape.Kind.ROUND, s.kind);
        assertEquals(12 * 0.0254, s.width, 1e-9); // 12 mils → 0.3048 mm
    }

    @Test
    void holeWithSpecFieldsUsesLeadingDiameter() {
        // Full spec form: hole<d>x<p>x<tp>x<tm> — diameter is the first field.
        SymbolShape s = SymbolShape.parse("hole300x1x0x0", MM);
        assertNotNull(s);
        assertEquals(SymbolShape.Kind.ROUND, s.kind);
        assertEquals(0.300, s.width, 1e-9);
    }

    @Test
    void holeWithVrPrefixParses() {
        SymbolShape s = SymbolShape.parse("vrhole250", MM);
        assertNotNull(s);
        assertEquals(SymbolShape.Kind.ROUND, s.kind);
        assertEquals(0.250, s.width, 1e-9);

        SymbolShape r = SymbolShape.parse("rhole250x0x0x0", MM);
        assertNotNull(r);
        assertEquals(0.250, r.width, 1e-9);
    }

    @Test
    void nonHoleFamiliesStillParse() {
        assertEquals(SymbolShape.Kind.ROUND, SymbolShape.parse("r300", MM).kind);
        assertEquals(SymbolShape.Kind.SQUARE, SymbolShape.parse("s300", MM).kind);
        assertEquals(SymbolShape.Kind.RECT, SymbolShape.parse("rect100x50", MM).kind);
    }
}

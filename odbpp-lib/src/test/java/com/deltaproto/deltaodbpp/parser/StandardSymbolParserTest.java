package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.symbol.StandardSymbol;
import com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StandardSymbolParserTest {

    private StandardSymbolParser parser;

    @BeforeEach
    void setUp() {
        parser = new StandardSymbolParser();
    }

    // Round symbols

    @Test
    void testParseRound() {
        StandardSymbol s = parser.parse("r50");
        assertNotNull(s);
        assertEquals(Type.ROUND, s.getType());
        assertEquals(50.0, s.getWidth());
        assertEquals(50.0, s.getHeight());
        assertEquals(50.0, s.getDiameter());
    }

    @Test
    void testParseRoundDecimal() {
        StandardSymbol s = parser.parse("r12.5");
        assertNotNull(s);
        assertEquals(Type.ROUND, s.getType());
        assertEquals(12.5, s.getWidth());
    }

    // Square symbols

    @Test
    void testParseSquare() {
        StandardSymbol s = parser.parse("s40");
        assertNotNull(s);
        assertEquals(Type.SQUARE, s.getType());
        assertEquals(40.0, s.getWidth());
        assertEquals(40.0, s.getHeight());
    }

    // Rectangle symbols

    @Test
    void testParseRectangle() {
        StandardSymbol s = parser.parse("rect100x50");
        assertNotNull(s);
        assertEquals(Type.RECTANGLE, s.getType());
        assertEquals(100.0, s.getWidth());
        assertEquals(50.0, s.getHeight());
    }

    @Test
    void testParseRoundedRectangle() {
        StandardSymbol s = parser.parse("rc100x50x10");
        assertNotNull(s);
        assertEquals(Type.ROUNDED_RECTANGLE, s.getType());
        assertEquals(100.0, s.getWidth());
        assertEquals(50.0, s.getHeight());
        assertEquals(10.0, s.getCornerRadius());
    }

    @Test
    void testParseChamferedRectangle() {
        StandardSymbol s = parser.parse("ch100x50x10");
        assertNotNull(s);
        assertEquals(Type.CHAMFERED_RECTANGLE, s.getType());
        assertEquals(100.0, s.getWidth());
        assertEquals(50.0, s.getHeight());
        assertEquals(10.0, s.getParam1()); // chamfer size
    }

    // Oval symbols

    @Test
    void testParseOval() {
        StandardSymbol s = parser.parse("oval80x40");
        assertNotNull(s);
        assertEquals(Type.OVAL, s.getType());
        assertEquals(80.0, s.getWidth());
        assertEquals(40.0, s.getHeight());
    }

    @Test
    void testParseEllipse() {
        StandardSymbol s = parser.parse("el80x50");
        assertNotNull(s);
        assertEquals(Type.ELLIPSE, s.getType());
        assertEquals(80.0, s.getWidth());
        assertEquals(50.0, s.getHeight());
    }

    // Polygon symbols

    @Test
    void testParseDiamond() {
        StandardSymbol s = parser.parse("di60x60");
        assertNotNull(s);
        assertEquals(Type.DIAMOND, s.getType());
        assertEquals(60.0, s.getWidth());
        assertEquals(60.0, s.getHeight());
    }

    @Test
    void testParseOctagon() {
        StandardSymbol s = parser.parse("oct100x100x20");
        assertNotNull(s);
        assertEquals(Type.OCTAGON, s.getType());
        assertEquals(100.0, s.getWidth());
        assertEquals(100.0, s.getHeight());
        assertEquals(20.0, s.getParam1()); // corner cut
    }

    @Test
    void testParseHexagonHorizontal() {
        StandardSymbol s = parser.parse("hex_l100x80x20");
        assertNotNull(s);
        assertEquals(Type.HEXAGON_L, s.getType());
        assertEquals(100.0, s.getWidth());
        assertEquals(80.0, s.getHeight());
        assertEquals(20.0, s.getParam1());
    }

    @Test
    void testParseHexagonVertical() {
        StandardSymbol s = parser.parse("hex_s80x100x20");
        assertNotNull(s);
        assertEquals(Type.HEXAGON_S, s.getType());
        assertEquals(80.0, s.getWidth());
        assertEquals(100.0, s.getHeight());
        assertEquals(20.0, s.getParam1());
    }

    @Test
    void testParseTriangle() {
        StandardSymbol s = parser.parse("tri50x40");
        assertNotNull(s);
        assertEquals(Type.TRIANGLE, s.getType());
        assertEquals(50.0, s.getWidth()); // base
        assertEquals(40.0, s.getHeight()); // height
    }

    @Test
    void testParseHalfOval() {
        StandardSymbol s = parser.parse("ho60x30");
        assertNotNull(s);
        assertEquals(Type.HALF_OVAL, s.getType());
        assertEquals(60.0, s.getWidth());
        assertEquals(30.0, s.getHeight());
    }

    // Donut symbols

    @Test
    void testParseRoundDonut() {
        StandardSymbol s = parser.parse("donut_r100x40");
        assertNotNull(s);
        assertEquals(Type.ROUND_DONUT, s.getType());
        assertEquals(100.0, s.getWidth()); // outer diameter
        assertEquals(40.0, s.getInnerDiameter());
    }

    @Test
    void testParseSquareDonut() {
        StandardSymbol s = parser.parse("donut_s100x60");
        assertNotNull(s);
        assertEquals(Type.SQUARE_DONUT, s.getType());
        assertEquals(100.0, s.getWidth()); // outer size
        assertEquals(60.0, s.getParam1()); // inner size
    }

    @Test
    void testParseOvalDonut() {
        StandardSymbol s = parser.parse("donut_o100x60x10");
        assertNotNull(s);
        assertEquals(Type.OVAL_DONUT, s.getType());
        assertEquals(100.0, s.getWidth()); // outer width
        assertEquals(60.0, s.getHeight()); // outer height
        assertEquals(10.0, s.getParam1()); // line width
    }

    // Thermal symbols

    @Test
    void testParseRoundThermal() {
        StandardSymbol s = parser.parse("thr100x40x45x4x10");
        assertNotNull(s);
        assertEquals(Type.ROUND_THERMAL, s.getType());
        assertEquals(100.0, s.getWidth()); // outer diameter
        assertEquals(40.0, s.getInnerDiameter());
        assertEquals(45.0, s.getSpokeAngle());
        assertEquals(4, s.getNumSpokes());
        assertEquals(10.0, s.getGap());
    }

    @Test
    void testParseRoundThermalSquared() {
        // ths = round thermal with squared gaps (ROUND_THERMAL_SQUARED)
        StandardSymbol s = parser.parse("ths100x40x45x4x10");
        assertNotNull(s);
        assertEquals(Type.ROUND_THERMAL_SQUARED, s.getType());
        assertEquals(100.0, s.getWidth());
        assertEquals(40.0, s.getParam1());
        assertEquals(4, s.getNumSpokes());
    }

    @Test
    void testParseSquareThermal() {
        // s_ths = square thermal (SQUARE_THERMAL)
        StandardSymbol s = parser.parse("s_ths100x40x45x4x10");
        assertNotNull(s);
        assertEquals(Type.SQUARE_THERMAL, s.getType());
        assertEquals(100.0, s.getWidth());
        assertEquals(40.0, s.getParam1());
        assertEquals(4, s.getNumSpokes());
    }

    // Butterfly symbols

    @Test
    void testParseButterflyRound() {
        StandardSymbol s = parser.parse("bfr50");
        assertNotNull(s);
        assertEquals(Type.BUTTERFLY, s.getType());
        assertEquals(50.0, s.getWidth());
        assertEquals(0.0, s.getParam1()); // round variant
    }

    @Test
    void testParseButterflySquare() {
        StandardSymbol s = parser.parse("bfs50");
        assertNotNull(s);
        assertEquals(Type.BUTTERFLY, s.getType());
        assertEquals(50.0, s.getWidth());
        assertEquals(1.0, s.getParam1()); // square variant
    }

    // Hole symbol

    @Test
    void testParseHole() {
        StandardSymbol s = parser.parse("hole50x1x0x0");
        assertNotNull(s);
        assertEquals(Type.HOLE, s.getType());
        assertEquals(50.0, s.getWidth()); // diameter
        assertTrue(s.isPlated());
    }

    @Test
    void testParseHoleNonPlated() {
        StandardSymbol s = parser.parse("hole30x0x0x0");
        assertNotNull(s);
        assertEquals(Type.HOLE, s.getType());
        assertEquals(30.0, s.getWidth());
        assertFalse(s.isPlated());
    }

    // Inverted home plate (spec format)

    @Test
    void testParseRhplate() {
        // rhplate = inverted home plate per spec
        StandardSymbol s = parser.parse("rhplate100x80x20");
        assertNotNull(s);
        assertEquals(Type.INVERTED_HOME_PLATE, s.getType());
        assertEquals(100.0, s.getWidth());
        assertEquals(80.0, s.getHeight());
        assertEquals(20.0, s.getParam1()); // cut size
    }

    // Cross with round/square ends (spec format)

    @Test
    void testParseCrossWithRoundEnds() {
        StandardSymbol s = parser.parse("cross100x80x20x15x50x50xr");
        assertNotNull(s);
        assertEquals(Type.CROSS, s.getType());
        assertEquals(100.0, s.getWidth());
        assertEquals(80.0, s.getHeight());
        assertEquals(20.0, s.getParam1()); // line box width
        assertEquals(15.0, s.getParam2()); // line box height
        assertEquals(50.0, s.getParam3()); // line box offset X
        assertEquals(50.0, s.getParam4()); // line box offset Y
        assertEquals(0.0, s.getGap()); // 0 = round ends
    }

    @Test
    void testParseCrossWithSquareEnds() {
        StandardSymbol s = parser.parse("cross100x80x20x15x50x50xs");
        assertNotNull(s);
        assertEquals(Type.CROSS, s.getType());
        assertEquals(100.0, s.getWidth());
        assertEquals(80.0, s.getHeight());
        assertEquals(1.0, s.getGap()); // 1 = square ends
    }

    // User-defined (not standard) symbols

    @Test
    void testParseUserDefinedReturnsNull() {
        assertNull(parser.parse("special0.300x0.330"));
        assertNull(parser.parse("my_custom_pad"));
        assertNull(parser.parse("sc_join0201_hd"));
    }

    @Test
    void testParseInvalidReturnsNull() {
        assertNull(parser.parse(null));
        assertNull(parser.parse(""));
        assertNull(parser.parse("invalid"));
        assertNull(parser.parse("r")); // missing dimension
        assertNull(parser.parse("rect100")); // missing height
    }

    // isStandardSymbol tests

    @Test
    void testIsStandardSymbol() {
        assertTrue(parser.isStandardSymbol("r50"));
        assertTrue(parser.isStandardSymbol("s40"));
        assertTrue(parser.isStandardSymbol("rect100x50"));
        assertTrue(parser.isStandardSymbol("oval80x40"));
        assertTrue(parser.isStandardSymbol("donut_r100x40"));

        assertFalse(parser.isStandardSymbol("special0.300x0.330"));
        assertFalse(parser.isStandardSymbol("my_custom_pad"));
        assertFalse(parser.isStandardSymbol(null));
    }

    // Utility tests

    @Test
    void testMilsToInches() {
        assertEquals(0.001, StandardSymbolParser.milsToInches(1), 0.0001);
        assertEquals(0.050, StandardSymbolParser.milsToInches(50), 0.0001);
        assertEquals(0.100, StandardSymbolParser.milsToInches(100), 0.0001);
    }
}

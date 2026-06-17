package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.symbol.StandardSymbol;
import com.deltaproto.deltaodbpp.model.symbol.StandardSymbol.Type;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for ODB++ standard symbol names.
 * Converts symbol name strings (e.g., "r50", "rect100x50") into StandardSymbol objects.
 */
public class StandardSymbolParser {

    // Patterns for different symbol types
    // All dimensions are in mils (thousandths of an inch)
    private static final String NUM = "(\\d+(?:\\.\\d+)?)";

    // Round: r<diameter> e.g., r50
    private static final Pattern ROUND = Pattern.compile("^r" + NUM + "$");

    // Square: s<size> e.g., s40
    private static final Pattern SQUARE = Pattern.compile("^s" + NUM + "$");

    // Rectangle: rect<w>x<h> e.g., rect100x50
    private static final Pattern RECTANGLE = Pattern.compile("^rect" + NUM + "x" + NUM + "$");

    // Rounded rectangle (spec format): rect<w>x<h>xr<rad> or rect<w>x<h>xr<rad>x<corners>
    private static final Pattern ROUNDED_RECT_SPEC = Pattern.compile(
            "^rect" + NUM + "x" + NUM + "xr" + NUM + "(?:x(\\d+))?$");

    // Rounded rectangle (legacy format): rc<w>x<h>x<r>
    private static final Pattern ROUNDED_RECT = Pattern.compile("^rc" + NUM + "x" + NUM + "x" + NUM + "$");

    // Chamfered rectangle (spec format): rect<w>x<h>xc<rad> or rect<w>x<h>xc<rad>x<corners>
    private static final Pattern CHAMFERED_RECT_SPEC = Pattern.compile(
            "^rect" + NUM + "x" + NUM + "xc" + NUM + "(?:x(\\d+))?$");

    // Chamfered rectangle (legacy format): ch<w>x<h>x<c>
    private static final Pattern CHAMFERED_RECT = Pattern.compile("^ch" + NUM + "x" + NUM + "x" + NUM + "$");

    // Oval: oval<w>x<h> e.g., oval80x40
    private static final Pattern OVAL = Pattern.compile("^oval" + NUM + "x" + NUM + "$");

    // Ellipse: el<w>x<h> e.g., el80x50
    private static final Pattern ELLIPSE = Pattern.compile("^el" + NUM + "x" + NUM + "$");

    // Diamond: di<w>x<h> e.g., di60x60
    private static final Pattern DIAMOND = Pattern.compile("^di" + NUM + "x" + NUM + "$");

    // Octagon: oct<w>x<h>x<c> e.g., oct100x100x20
    private static final Pattern OCTAGON = Pattern.compile("^oct" + NUM + "x" + NUM + "x" + NUM + "$");

    // Horizontal hexagon: hex_l<w>x<h>x<c>
    private static final Pattern HEXAGON_L = Pattern.compile("^hex_l" + NUM + "x" + NUM + "x" + NUM + "$");

    // Vertical hexagon: hex_s<w>x<h>x<c>
    private static final Pattern HEXAGON_S = Pattern.compile("^hex_s" + NUM + "x" + NUM + "x" + NUM + "$");

    // Triangle: tri<b>x<h>
    private static final Pattern TRIANGLE = Pattern.compile("^tri" + NUM + "x" + NUM + "$");

    // Half oval (legacy): ho<w>x<h>
    private static final Pattern HALF_OVAL = Pattern.compile("^ho" + NUM + "x" + NUM + "$");

    // Half oval (spec format): oval_h<w>x<h>
    private static final Pattern HALF_OVAL_SPEC = Pattern.compile("^oval_h" + NUM + "x" + NUM + "$");

    // Round donut: donut_r<od>x<id>
    private static final Pattern DONUT_ROUND = Pattern.compile("^donut_r" + NUM + "x" + NUM + "$");

    // Square donut: donut_s<ow>x<iw>
    private static final Pattern DONUT_SQUARE = Pattern.compile("^donut_s" + NUM + "x" + NUM + "$");

    // Square-round donut: donut_sr<od>x<id> (square outside, round inside)
    private static final Pattern DONUT_SQUARE_ROUND = Pattern.compile("^donut_sr" + NUM + "x" + NUM + "$");

    // Oval donut: donut_o<ow>x<oh>x<lw>
    private static final Pattern DONUT_OVAL = Pattern.compile("^donut_o" + NUM + "x" + NUM + "x" + NUM + "$");

    // Rectangle donut: donut_rc<ow>x<oh>x<lw>
    private static final Pattern DONUT_RECT = Pattern.compile("^donut_rc" + NUM + "x" + NUM + "x" + NUM + "$");

    // Round thermal: thr<od>x<id>x<angle>x<num>x<gap>
    private static final Pattern THERMAL_ROUND = Pattern.compile(
            "^thr" + NUM + "x" + NUM + "x" + NUM + "x(\\d+)x" + NUM + "$");

    // Round thermal (squared gaps): ths<od>x<id>x<angle>x<num>x<gap>
    private static final Pattern THERMAL_ROUND_SQUARED = Pattern.compile(
            "^ths" + NUM + "x" + NUM + "x" + NUM + "x(\\d+)x" + NUM + "$");

    // Square thermal (spec format): s_ths<os>x<is>x<angle>x<num>x<gap>
    private static final Pattern THERMAL_SQUARE_SPEC = Pattern.compile(
            "^s_ths" + NUM + "x" + NUM + "x" + NUM + "x(\\d+)x" + NUM + "$");

    // Square thermal with rounded corners: s_ths<os>x<is>x<angle>x<num>x<gap>xr<rad>
    private static final Pattern THERMAL_SQUARE_ROUNDED = Pattern.compile(
            "^s_ths" + NUM + "x" + NUM + "x" + NUM + "x(\\d+)x" + NUM + "xr" + NUM + "$");

    // Square thermal (open corners): s_tho<od>x<id>x<angle>x<num>x<gap>
    private static final Pattern THERMAL_SQUARE_OPEN = Pattern.compile(
            "^s_tho" + NUM + "x" + NUM + "x" + NUM + "x(\\d+)x" + NUM + "$");

    // Line thermal: s_thr<os>x<is>x<angle>x<num>x<gap> - rectangular spokes
    private static final Pattern THERMAL_LINE = Pattern.compile(
            "^s_thr" + NUM + "x" + NUM + "x" + NUM + "x(\\d+)x" + NUM + "$");

    // Square-round thermal: sr_ths<os>x<id>x<angle>x<num>x<gap>
    private static final Pattern THERMAL_SQUARE_ROUND = Pattern.compile(
            "^sr_ths" + NUM + "x" + NUM + "x" + NUM + "x(\\d+)x" + NUM + "$");

    // Rectangular thermal: rc_ths<w>x<h>x<angle>x<num>x<gap>x<air_gap>
    private static final Pattern THERMAL_RECT = Pattern.compile(
            "^rc_ths" + NUM + "x" + NUM + "x" + NUM + "x(\\d+)x" + NUM + "x" + NUM + "$");

    // Rectangular thermal with rounded corners: rc_ths<w>x<h>x<angle>x<num>x<gap>x<lw>xr<rad>
    private static final Pattern THERMAL_RECT_ROUNDED = Pattern.compile(
            "^rc_ths" + NUM + "x" + NUM + "x" + NUM + "x(\\d+)x" + NUM + "x" + NUM + "xr" + NUM + "$");

    // Rectangular thermal (open corners): rc_tho<w>x<h>x<angle>x<num>x<gap>x<air_gap>
    private static final Pattern THERMAL_RECT_OPEN = Pattern.compile(
            "^rc_tho" + NUM + "x" + NUM + "x" + NUM + "x(\\d+)x" + NUM + "x" + NUM + "$");

    // Oval thermal: o_ths<ow>x<oh>x<angle>x<num>x<gap>x<lw>
    private static final Pattern THERMAL_OVAL = Pattern.compile(
            "^o_ths" + NUM + "x" + NUM + "x" + NUM + "x(\\d+)x" + NUM + "x" + NUM + "$");

    // Rounded square donut: donut_s<od>x<id>xr<rad>
    private static final Pattern DONUT_SQUARE_ROUNDED = Pattern.compile(
            "^donut_s" + NUM + "x" + NUM + "xr" + NUM + "$");

    // Rounded rectangle donut: donut_rc<ow>x<oh>x<lw>xr<rad>
    private static final Pattern DONUT_RECT_ROUNDED = Pattern.compile(
            "^donut_rc" + NUM + "x" + NUM + "x" + NUM + "xr" + NUM + "$");

    // Butterfly round: bfr<d>
    private static final Pattern BUTTERFLY_ROUND = Pattern.compile("^bfr" + NUM + "$");

    // Butterfly square: bfs<d>
    private static final Pattern BUTTERFLY_SQUARE = Pattern.compile("^bfs" + NUM + "$");

    // Moire: moire<rw>x<rg>x<nr>x<lw>x<ll>x<la>
    private static final Pattern MOIRE = Pattern.compile(
            "^moire" + NUM + "x" + NUM + "x(\\d+)x" + NUM + "x" + NUM + "x" + NUM + "$");

    // Hole: hole<d>x<p>x<tp>x<tm> - p is 0/1 for non-plated/plated
    private static final Pattern HOLE = Pattern.compile("^hole" + NUM + "x(\\d+)x" + NUM + "x" + NUM + "$");

    // Home plate: hplate<w>x<h>x<c>
    private static final Pattern HOME_PLATE = Pattern.compile("^hplate" + NUM + "x" + NUM + "x" + NUM + "$");

    // Inverted home plate: inv_hplate<w>x<h>x<c>
    private static final Pattern INVERTED_HOME_PLATE = Pattern.compile("^inv_hplate" + NUM + "x" + NUM + "x" + NUM + "$");

    // Inverted home plate (spec format): rhplate<w>x<h>x<c>
    private static final Pattern RHPLATE = Pattern.compile("^rhplate" + NUM + "x" + NUM + "x" + NUM + "$");

    // Radiused inverted home plate: radiused_inv_hplate<w>x<h>x<c>x<rad>
    private static final Pattern RADIUSED_INVERTED_HOME_PLATE = Pattern.compile(
            "^radiused_inv_hplate" + NUM + "x" + NUM + "x" + NUM + "x" + NUM + "$");

    // Flat home plate: fhplate<w>x<h>x<vc>x<hc>
    private static final Pattern FLAT_HOME_PLATE = Pattern.compile(
            "^fhplate" + NUM + "x" + NUM + "x" + NUM + "x" + NUM + "$");

    // D-shape: dshape<w>x<h>x<ra>
    private static final Pattern D_SHAPE = Pattern.compile("^dshape" + NUM + "x" + NUM + "x" + NUM + "$");

    // Cross: cross<ow>x<oh>x<lbw>x<lbh>x<lbo>x<lbo>
    private static final Pattern CROSS = Pattern.compile(
            "^cross" + NUM + "x" + NUM + "x" + NUM + "x" + NUM + "x" + NUM + "x" + NUM + "$");

    // Cross with round/square ends (spec format): cross<w>x<h>x<hs>x<vs>x<hc>x<vc>x[r|s]
    private static final Pattern CROSS_ENDS = Pattern.compile(
            "^cross" + NUM + "x" + NUM + "x" + NUM + "x" + NUM + "x" + NUM + "x" + NUM + "x([rs])$");

    // Cross with radius: cross<ow>x<oh>x<lbw>x<lbh>x<lbo>x<lbo>x<rad>
    private static final Pattern CROSS_RADIUSED = Pattern.compile(
            "^cross" + NUM + "x" + NUM + "x" + NUM + "x" + NUM + "x" + NUM + "x" + NUM + "x" + NUM + "$");

    // Dogbone: dogbone<w>x<h>x<hs>x<vs>x<hc>x[r|s]
    private static final Pattern DOGBONE = Pattern.compile(
            "^dogbone" + NUM + "x" + NUM + "x" + NUM + "x" + NUM + "x" + NUM + "x([rs])$");

    // Dogbone with radius: dogbone<w>x<h>x<hs>x<vs>x<hc>x[r|s]x<ra>
    private static final Pattern DOGBONE_RADIUSED = Pattern.compile(
            "^dogbone" + NUM + "x" + NUM + "x" + NUM + "x" + NUM + "x" + NUM + "x([rs])x" + NUM + "$");

    // Oblong thermal: oblong_ths<ow>x<oh>x<angle>x<num>x<gap>x<lw>x[r|s]
    private static final Pattern OBLONG_THERMAL = Pattern.compile(
            "^oblong_ths" + NUM + "x" + NUM + "x" + NUM + "x(\\d+)x" + NUM + "x" + NUM + "x([rs])$");

    // Null symbol: null<ext>
    private static final Pattern NULL_SYMBOL = Pattern.compile("^null(\\d*)$");

    /**
     * Parse a standard symbol name into a StandardSymbol object.
     *
     * @param symbolName The symbol name (e.g., "r50", "rect100x50")
     * @return StandardSymbol with parsed parameters, or null if not a recognized standard symbol
     */
    public StandardSymbol parse(String symbolName) {
        if (symbolName == null || symbolName.isEmpty()) {
            return null;
        }

        Matcher m;

        // Round
        m = ROUND.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.ROUND);
            double d = parseDouble(m.group(1));
            s.setWidth(d);
            s.setHeight(d);
            return s;
        }

        // Square
        m = SQUARE.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.SQUARE);
            double size = parseDouble(m.group(1));
            s.setWidth(size);
            s.setHeight(size);
            return s;
        }

        // Rectangle
        m = RECTANGLE.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.RECTANGLE);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            return s;
        }

        // Rounded rectangle (spec format): rect<w>x<h>xr<rad>
        m = ROUNDED_RECT_SPEC.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.ROUNDED_RECTANGLE);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            s.setParam1(parseDouble(m.group(3))); // corner radius
            if (m.group(4) != null) {
                s.setParam2(parseDouble(m.group(4))); // corners bitmask
            }
            return s;
        }

        // Rounded rectangle (legacy format)
        m = ROUNDED_RECT.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.ROUNDED_RECTANGLE);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            s.setParam1(parseDouble(m.group(3))); // corner radius
            return s;
        }

        // Chamfered rectangle (spec format): rect<w>x<h>xc<rad>
        m = CHAMFERED_RECT_SPEC.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.CHAMFERED_RECTANGLE);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            s.setParam1(parseDouble(m.group(3))); // chamfer size
            if (m.group(4) != null) {
                s.setParam2(parseDouble(m.group(4))); // corners bitmask
            }
            return s;
        }

        // Chamfered rectangle (legacy format)
        m = CHAMFERED_RECT.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.CHAMFERED_RECTANGLE);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            s.setParam1(parseDouble(m.group(3))); // chamfer size
            return s;
        }

        // Oval
        m = OVAL.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.OVAL);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            return s;
        }

        // Ellipse
        m = ELLIPSE.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.ELLIPSE);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            return s;
        }

        // Diamond
        m = DIAMOND.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.DIAMOND);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            return s;
        }

        // Octagon
        m = OCTAGON.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.OCTAGON);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            s.setParam1(parseDouble(m.group(3))); // corner cut
            return s;
        }

        // Hexagon horizontal
        m = HEXAGON_L.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.HEXAGON_L);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            s.setParam1(parseDouble(m.group(3))); // corner cut
            return s;
        }

        // Hexagon vertical
        m = HEXAGON_S.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.HEXAGON_S);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            s.setParam1(parseDouble(m.group(3))); // corner cut
            return s;
        }

        // Triangle
        m = TRIANGLE.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.TRIANGLE);
            s.setWidth(parseDouble(m.group(1))); // base
            s.setHeight(parseDouble(m.group(2))); // height
            return s;
        }

        // Half oval (legacy format: ho<w>x<h>)
        m = HALF_OVAL.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.HALF_OVAL);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            return s;
        }

        // Half oval (spec format: oval_h<w>x<h>)
        m = HALF_OVAL_SPEC.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.HALF_OVAL);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            return s;
        }

        // Round donut
        m = DONUT_ROUND.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.ROUND_DONUT);
            s.setWidth(parseDouble(m.group(1))); // outer diameter
            s.setParam1(parseDouble(m.group(2))); // inner diameter
            s.setHeight(s.getWidth());
            return s;
        }

        // Square donut
        m = DONUT_SQUARE.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.SQUARE_DONUT);
            s.setWidth(parseDouble(m.group(1))); // outer size
            s.setParam1(parseDouble(m.group(2))); // inner size
            s.setHeight(s.getWidth());
            return s;
        }

        // Square-round donut: donut_sr<od>x<id>
        m = DONUT_SQUARE_ROUND.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.SQUARE_ROUND_DONUT);
            s.setWidth(parseDouble(m.group(1))); // outer diameter (square)
            s.setParam1(parseDouble(m.group(2))); // inner diameter (round)
            s.setHeight(s.getWidth());
            return s;
        }

        // Oval donut
        m = DONUT_OVAL.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.OVAL_DONUT);
            s.setWidth(parseDouble(m.group(1))); // outer width
            s.setHeight(parseDouble(m.group(2))); // outer height
            s.setParam1(parseDouble(m.group(3))); // line width
            return s;
        }

        // Rectangle donut: donut_rc<ow>x<oh>x<lw>
        m = DONUT_RECT.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.RECT_DONUT);
            s.setWidth(parseDouble(m.group(1))); // outer width
            s.setHeight(parseDouble(m.group(2))); // outer height
            s.setParam1(parseDouble(m.group(3))); // line width
            return s;
        }

        // Rounded square donut: donut_s<od>x<id>xr<rad>
        m = DONUT_SQUARE_ROUNDED.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.ROUNDED_SQUARE_DONUT);
            s.setWidth(parseDouble(m.group(1))); // outer size
            s.setParam1(parseDouble(m.group(2))); // inner size
            s.setParam2(parseDouble(m.group(3))); // corner radius
            s.setHeight(s.getWidth());
            return s;
        }

        // Rounded rectangle donut: donut_rc<ow>x<oh>x<lw>xr<rad>
        m = DONUT_RECT_ROUNDED.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.ROUNDED_RECT_DONUT);
            s.setWidth(parseDouble(m.group(1))); // outer width
            s.setHeight(parseDouble(m.group(2))); // outer height
            s.setParam1(parseDouble(m.group(3))); // line width
            s.setParam2(parseDouble(m.group(4))); // corner radius
            return s;
        }

        // Round thermal
        m = THERMAL_ROUND.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.ROUND_THERMAL);
            s.setWidth(parseDouble(m.group(1))); // outer diameter
            s.setParam1(parseDouble(m.group(2))); // inner diameter
            s.setSpokeAngle(parseDouble(m.group(3)));
            s.setNumSpokes(Integer.parseInt(m.group(4)));
            s.setGap(parseDouble(m.group(5)));
            s.setHeight(s.getWidth());
            return s;
        }

        // Round thermal (squared gaps): ths<od>x<id>x<angle>x<num>x<gap>
        m = THERMAL_ROUND_SQUARED.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.ROUND_THERMAL_SQUARED);
            s.setWidth(parseDouble(m.group(1))); // outer diameter
            s.setParam1(parseDouble(m.group(2))); // inner diameter
            s.setSpokeAngle(parseDouble(m.group(3)));
            s.setNumSpokes(Integer.parseInt(m.group(4)));
            s.setGap(parseDouble(m.group(5)));
            s.setHeight(s.getWidth());
            return s;
        }

        // Square thermal (spec format: s_ths<os>x<is>x<angle>x<num>x<gap>)
        m = THERMAL_SQUARE_SPEC.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.SQUARE_THERMAL);
            s.setWidth(parseDouble(m.group(1))); // outer size
            s.setParam1(parseDouble(m.group(2))); // inner size
            s.setSpokeAngle(parseDouble(m.group(3)));
            s.setNumSpokes(Integer.parseInt(m.group(4)));
            s.setGap(parseDouble(m.group(5)));
            s.setHeight(s.getWidth());
            return s;
        }

        // Square thermal with rounded corners: s_ths<os>x<is>x<angle>x<num>x<gap>xr<rad>
        m = THERMAL_SQUARE_ROUNDED.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.ROUNDED_SQUARE_THERMAL);
            s.setWidth(parseDouble(m.group(1))); // outer size
            s.setParam1(parseDouble(m.group(2))); // inner size
            s.setSpokeAngle(parseDouble(m.group(3)));
            s.setNumSpokes(Integer.parseInt(m.group(4)));
            s.setGap(parseDouble(m.group(5)));
            s.setParam2(parseDouble(m.group(6))); // corner radius
            s.setHeight(s.getWidth());
            return s;
        }

        // Square thermal (open corners): s_tho<od>x<id>x<angle>x<num>x<gap>
        m = THERMAL_SQUARE_OPEN.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.SQUARE_THERMAL_OPEN);
            s.setWidth(parseDouble(m.group(1))); // outer size
            s.setParam1(parseDouble(m.group(2))); // inner size
            s.setSpokeAngle(parseDouble(m.group(3)));
            s.setNumSpokes(Integer.parseInt(m.group(4)));
            s.setGap(parseDouble(m.group(5)));
            s.setHeight(s.getWidth());
            return s;
        }

        // Line thermal: s_thr<os>x<is>x<angle>x<num>x<gap>
        m = THERMAL_LINE.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.LINE_THERMAL);
            s.setWidth(parseDouble(m.group(1))); // outer size
            s.setParam1(parseDouble(m.group(2))); // inner size
            s.setSpokeAngle(parseDouble(m.group(3)));
            s.setNumSpokes(Integer.parseInt(m.group(4)));
            s.setGap(parseDouble(m.group(5)));
            s.setHeight(s.getWidth());
            return s;
        }

        // Square-round thermal: sr_ths<os>x<id>x<angle>x<num>x<gap>
        m = THERMAL_SQUARE_ROUND.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.SQUARE_ROUND_THERMAL);
            s.setWidth(parseDouble(m.group(1))); // outer size (square)
            s.setParam1(parseDouble(m.group(2))); // inner diameter (round)
            s.setSpokeAngle(parseDouble(m.group(3)));
            s.setNumSpokes(Integer.parseInt(m.group(4)));
            s.setGap(parseDouble(m.group(5)));
            s.setHeight(s.getWidth());
            return s;
        }

        // Rectangular thermal: rc_ths<w>x<h>x<angle>x<num>x<gap>x<air_gap>
        m = THERMAL_RECT.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.RECT_THERMAL);
            s.setWidth(parseDouble(m.group(1))); // outer width
            s.setHeight(parseDouble(m.group(2))); // outer height
            s.setSpokeAngle(parseDouble(m.group(3)));
            s.setNumSpokes(Integer.parseInt(m.group(4)));
            s.setGap(parseDouble(m.group(5)));
            s.setParam1(parseDouble(m.group(6))); // air gap / line width
            return s;
        }

        // Rectangular thermal with rounded corners: rc_ths<w>x<h>x<angle>x<num>x<gap>x<lw>xr<rad>
        m = THERMAL_RECT_ROUNDED.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.ROUNDED_RECT_THERMAL);
            s.setWidth(parseDouble(m.group(1))); // outer width
            s.setHeight(parseDouble(m.group(2))); // outer height
            s.setSpokeAngle(parseDouble(m.group(3)));
            s.setNumSpokes(Integer.parseInt(m.group(4)));
            s.setGap(parseDouble(m.group(5)));
            s.setParam1(parseDouble(m.group(6))); // line width
            s.setParam2(parseDouble(m.group(7))); // corner radius
            return s;
        }

        // Rectangular thermal (open corners): rc_tho<w>x<h>x<angle>x<num>x<gap>x<air_gap>
        m = THERMAL_RECT_OPEN.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.RECT_THERMAL_OPEN);
            s.setWidth(parseDouble(m.group(1))); // outer width
            s.setHeight(parseDouble(m.group(2))); // outer height
            s.setSpokeAngle(parseDouble(m.group(3)));
            s.setNumSpokes(Integer.parseInt(m.group(4)));
            s.setGap(parseDouble(m.group(5)));
            s.setParam1(parseDouble(m.group(6))); // air gap
            return s;
        }

        // Oval thermal (spec format: o_ths<ow>x<oh>x<angle>x<num>x<gap>x<lw>)
        m = THERMAL_OVAL.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.OVAL_THERMAL);
            s.setWidth(parseDouble(m.group(1))); // outer width
            s.setHeight(parseDouble(m.group(2))); // outer height
            s.setSpokeAngle(parseDouble(m.group(3)));
            s.setNumSpokes(Integer.parseInt(m.group(4)));
            s.setGap(parseDouble(m.group(5)));
            s.setParam1(parseDouble(m.group(6))); // line width
            return s;
        }

        // Moire: moire<rw>x<rg>x<nr>x<lw>x<ll>x<la>
        m = MOIRE.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.MOIRE);
            s.setParam1(parseDouble(m.group(1))); // ring width
            s.setParam2(parseDouble(m.group(2))); // ring gap
            s.setNumSpokes(Integer.parseInt(m.group(3))); // number of rings (reusing numSpokes field)
            s.setParam3(parseDouble(m.group(4))); // line width
            s.setParam4(parseDouble(m.group(5))); // line length
            s.setSpokeAngle(parseDouble(m.group(6))); // line angle
            // Calculate overall width based on rings
            double rings = s.getNumSpokes();
            double ringWidth = s.getParam1();
            double ringGap = s.getParam2();
            s.setWidth((rings * (ringWidth + ringGap) * 2));
            s.setHeight(s.getWidth());
            return s;
        }

        // Butterfly round
        m = BUTTERFLY_ROUND.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.BUTTERFLY);
            double d = parseDouble(m.group(1));
            s.setWidth(d);
            s.setHeight(d);
            s.setParam1(0); // 0 = round variant
            return s;
        }

        // Butterfly square
        m = BUTTERFLY_SQUARE.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.BUTTERFLY);
            double d = parseDouble(m.group(1));
            s.setWidth(d);
            s.setHeight(d);
            s.setParam1(1); // 1 = square variant
            return s;
        }

        // Hole
        m = HOLE.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.HOLE);
            s.setWidth(parseDouble(m.group(1))); // diameter
            s.setPlated(Integer.parseInt(m.group(2)) == 1);
            s.setParam1(parseDouble(m.group(3))); // type
            s.setParam2(parseDouble(m.group(4))); // mark
            s.setHeight(s.getWidth());
            return s;
        }

        // Home plate: hplate<w>x<h>x<c>
        m = HOME_PLATE.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.HOME_PLATE);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            s.setParam1(parseDouble(m.group(3))); // cut size
            return s;
        }

        // Inverted home plate: inv_hplate<w>x<h>x<c>
        m = INVERTED_HOME_PLATE.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.INVERTED_HOME_PLATE);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            s.setParam1(parseDouble(m.group(3))); // cut size
            return s;
        }

        // Inverted home plate (spec format): rhplate<w>x<h>x<c>
        m = RHPLATE.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.INVERTED_HOME_PLATE);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            s.setParam1(parseDouble(m.group(3))); // cut size
            return s;
        }

        // Radiused inverted home plate: radiused_inv_hplate<w>x<h>x<c>x<rad>
        m = RADIUSED_INVERTED_HOME_PLATE.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.RADIUSED_INVERTED_HOME_PLATE);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            s.setParam1(parseDouble(m.group(3))); // cut size
            s.setParam2(parseDouble(m.group(4))); // corner radius
            return s;
        }

        // Flat home plate: fhplate<w>x<h>x<vc>x<hc>
        m = FLAT_HOME_PLATE.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.FLAT_HOME_PLATE);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            s.setParam1(parseDouble(m.group(3))); // vertical cut
            s.setParam2(parseDouble(m.group(4))); // horizontal cut
            return s;
        }

        // D-shape: dshape<w>x<h>x<ra>
        m = D_SHAPE.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.D_SHAPE);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            s.setParam1(parseDouble(m.group(3))); // radius
            return s;
        }

        // Cross with round/square ends (spec format): cross<w>x<h>x<hs>x<vs>x<hc>x<vc>x[r|s]
        // Parse this first since it ends with 'r' or 's' (not a number)
        m = CROSS_ENDS.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.CROSS);
            s.setWidth(parseDouble(m.group(1)));  // outer width
            s.setHeight(parseDouble(m.group(2))); // outer height
            s.setParam1(parseDouble(m.group(3))); // line box width
            s.setParam2(parseDouble(m.group(4))); // line box height
            s.setParam3(parseDouble(m.group(5))); // line box offset X
            s.setParam4(parseDouble(m.group(6))); // line box offset Y
            s.setGap("r".equals(m.group(7)) ? 0 : 1); // 0=round, 1=square (reusing gap field)
            return s;
        }

        // Cross with radius: cross<ow>x<oh>x<lbw>x<lbh>x<lbo>x<lbo>x<rad>
        m = CROSS_RADIUSED.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.CROSS);
            s.setWidth(parseDouble(m.group(1)));      // outer width
            s.setHeight(parseDouble(m.group(2)));     // outer height
            s.setParam1(parseDouble(m.group(3)));     // line box width
            s.setParam2(parseDouble(m.group(4)));     // line box height
            s.setParam3(parseDouble(m.group(5)));     // line box offset X
            s.setParam4(parseDouble(m.group(6)));     // line box offset Y
            s.setSpokeAngle(parseDouble(m.group(7))); // corner radius (reusing spokeAngle)
            return s;
        }

        // Cross: cross<ow>x<oh>x<lbw>x<lbh>x<lbo>x<lbo>
        m = CROSS.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.CROSS);
            s.setWidth(parseDouble(m.group(1)));  // outer width
            s.setHeight(parseDouble(m.group(2))); // outer height
            s.setParam1(parseDouble(m.group(3))); // line box width
            s.setParam2(parseDouble(m.group(4))); // line box height
            s.setParam3(parseDouble(m.group(5))); // line box offset X
            s.setParam4(parseDouble(m.group(6))); // line box offset Y
            return s;
        }

        // Dogbone: dogbone<w>x<h>x<hs>x<vs>x<hc>x[r|s]
        m = DOGBONE.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.DOGBONE);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            s.setParam1(parseDouble(m.group(3))); // horizontal line width
            s.setParam2(parseDouble(m.group(4))); // vertical line width
            s.setParam3(parseDouble(m.group(5))); // horizontal cross point
            s.setParam4("r".equals(m.group(6)) ? 0 : 1); // 0=round, 1=square
            return s;
        }

        // Dogbone with radius: dogbone<w>x<h>x<hs>x<vs>x<hc>x[r|s]x<ra>
        m = DOGBONE_RADIUSED.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.DOGBONE);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            s.setParam1(parseDouble(m.group(3))); // horizontal line width
            s.setParam2(parseDouble(m.group(4))); // vertical line width
            s.setParam3(parseDouble(m.group(5))); // horizontal cross point
            s.setParam4("r".equals(m.group(6)) ? 0 : 1); // 0=round, 1=square
            s.setSpokeAngle(parseDouble(m.group(7))); // corner radius (reusing spokeAngle)
            return s;
        }

        // Oblong thermal: oblong_ths<ow>x<oh>x<angle>x<num>x<gap>x<lw>x[r|s]
        m = OBLONG_THERMAL.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.OBLONG_THERMAL);
            s.setWidth(parseDouble(m.group(1)));
            s.setHeight(parseDouble(m.group(2)));
            s.setSpokeAngle(parseDouble(m.group(3)));
            s.setNumSpokes(Integer.parseInt(m.group(4)));
            s.setGap(parseDouble(m.group(5)));
            s.setParam1(parseDouble(m.group(6))); // line width
            s.setParam2("r".equals(m.group(7)) ? 0 : 1); // 0=round, 1=square
            return s;
        }

        // Null symbol: null<ext>
        m = NULL_SYMBOL.matcher(symbolName);
        if (m.matches()) {
            StandardSymbol s = new StandardSymbol(symbolName, Type.NULL_SYMBOL);
            String ext = m.group(1);
            s.setWidth(0);
            s.setHeight(0);
            s.setParam1(ext.isEmpty() ? 0 : Double.parseDouble(ext)); // extension number
            return s;
        }

        // Not a recognized standard symbol
        return null;
    }

    /**
     * Check if a symbol name is a standard symbol (vs user-defined).
     */
    public boolean isStandardSymbol(String symbolName) {
        return parse(symbolName) != null;
    }

    /**
     * Convert mils to inches (ODB++ uses inches internally).
     * Standard symbol dimensions are typically in mils.
     */
    public static double milsToInches(double mils) {
        return mils / 1000.0;
    }

    private double parseDouble(String s) {
        return Double.parseDouble(s);
    }
}

package com.deltaproto.deltaodbpp.model.symbol;

/**
 * Represents a parsed ODB++ standard symbol.
 * Standard symbols are defined by their name pattern (e.g., "r50", "rect100x50").
 */
public class StandardSymbol {

    /**
     * The type of standard symbol.
     */
    public enum Type {
        ROUND,              // r<diameter>
        SQUARE,             // s<size>
        RECTANGLE,          // rect<w>x<h>
        ROUNDED_RECTANGLE,  // rc<w>x<h>x<r> or rect<w>x<h>xr<rad> (corner radius)
        CHAMFERED_RECTANGLE,// ch<w>x<h>x<c> or rect<w>x<h>xc<rad> (chamfer size)
        OVAL,               // oval<w>x<h>
        ELLIPSE,            // el<w>x<h>
        DIAMOND,            // di<w>x<h>
        OCTAGON,            // oct<w>x<h>x<c> (corner cut)
        HEXAGON_L,          // hex_l<w>x<h>x<c> (horizontal)
        HEXAGON_S,          // hex_s<w>x<h>x<c> (vertical)
        TRIANGLE,           // tri<b>x<h>
        HALF_OVAL,          // ho<w>x<h> or oval_h<w>x<h>
        ROUND_DONUT,        // donut_r<od>x<id>
        SQUARE_DONUT,       // donut_s<ow>x<iw>
        SQUARE_ROUND_DONUT, // donut_sr<od>x<id> (square outside, round inside)
        OVAL_DONUT,         // donut_o<ow>x<oh>x<lw>
        RECT_DONUT,         // donut_rc<ow>x<oh>x<lw>
        ROUNDED_SQUARE_DONUT, // donut_s<od>x<id>xr<rad> - square with rounded corners
        ROUNDED_RECT_DONUT,   // donut_rc<ow>x<oh>x<lw>xr<rad> - rect with rounded corners
        ROUND_THERMAL,      // thr<od>x<id>x<angle>x<num_spokes>x<gap> - round with rounded gaps
        ROUND_THERMAL_SQUARED, // ths<od>x<id>x<angle>x<num_spokes>x<gap> - round with squared gaps
        SQUARE_THERMAL,     // s_ths<os>x<is>x<angle>x<num_spokes>x<gap> - square thermal
        SQUARE_THERMAL_OPEN, // s_tho<od>x<id>x<angle>x<num>x<gap> - open corners
        SQUARE_ROUND_THERMAL, // sr_ths<os>x<id>x<angle>x<num>x<gap> - square outer, round inner
        RECT_THERMAL,       // rc_ths<w>x<h>x<angle>x<num>x<gap>x<air_gap>
        RECT_THERMAL_OPEN,  // rc_tho<w>x<h>x<angle>x<num>x<gap>x<air_gap> - open corners
        OVAL_THERMAL,       // tho<ow>x<oh>x<angle>x<num_spokes>x<gap>x<lw> or o_ths<ow>x<oh>x...
        LINE_THERMAL,       // s_thr<os>x<is>x<angle>x<num>x<gap> - line (rectangular) spokes
        ROUNDED_SQUARE_THERMAL, // s_ths<os>x<is>x<angle>x<num>x<gap>xr<rad> - rounded corners
        ROUNDED_RECT_THERMAL, // rc_ths<ow>x<oh>x<angle>x<num>x<gap>x<lw>xr<rad>
        BUTTERFLY,          // bfr<d> (round) or bfs<d> (square)
        MOIRE,              // moire<rw>x<rg>x<nr>x<lw>x<ll>x<la> - registration mark
        HOLE,               // hole<d>x<plated>x<type>x<mark>
        HOME_PLATE,         // hplate<w>x<h>x<c> - pentagon shape
        INVERTED_HOME_PLATE, // inv_hplate<w>x<h>x<c> - inverted pentagon
        RADIUSED_INVERTED_HOME_PLATE, // radiused_inv_hplate<w>x<h>x<c>x<rad> - radiused inverted pentagon
        FLAT_HOME_PLATE,    // fhplate<w>x<h>x<vc>x<hc> - with cuts
        D_SHAPE,            // dshape<w>x<h>x<ra> - radiused home plate
        CROSS,              // cross<ow>x<oh>x<lbw>x<lbh>x<lbo>x<lbo> (optional x<rad>) - cross shape
        DOGBONE,            // dogbone<w>x<h>x<hs>x<vs>x<hc>x[r|s] - dumbbell shape
        OBLONG_THERMAL,     // oblong_ths<ow>x<oh>x<angle>x<num>x<gap>x<lw>x[r|s]
        NULL_SYMBOL,        // null<ext> - zero-area placeholder
        USER_DEFINED        // Not a standard symbol - resolved from symbols directory
    }

    private final String originalName;
    private final Type type;

    // Primary dimensions (interpretation depends on type)
    private double width;      // Width or diameter
    private double height;     // Height (same as width for round/square)
    private double param1;     // Corner radius, inner diameter, etc.
    private double param2;     // Additional parameter
    private double param3;     // Additional parameter
    private double param4;     // Additional parameter
    private double param5;     // Additional parameter

    // For thermals
    private int numSpokes;
    private double spokeAngle;
    private double gap;

    // For hole
    private boolean plated;

    public StandardSymbol(String originalName, Type type) {
        this.originalName = originalName;
        this.type = type;
    }

    // Getters and setters

    public String getOriginalName() {
        return originalName;
    }

    public Type getType() {
        return type;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public double getParam1() {
        return param1;
    }

    public void setParam1(double param1) {
        this.param1 = param1;
    }

    public double getParam2() {
        return param2;
    }

    public void setParam2(double param2) {
        this.param2 = param2;
    }

    public double getParam3() {
        return param3;
    }

    public void setParam3(double param3) {
        this.param3 = param3;
    }

    public double getParam4() {
        return param4;
    }

    public void setParam4(double param4) {
        this.param4 = param4;
    }

    public double getParam5() {
        return param5;
    }

    public void setParam5(double param5) {
        this.param5 = param5;
    }

    public int getNumSpokes() {
        return numSpokes;
    }

    public void setNumSpokes(int numSpokes) {
        this.numSpokes = numSpokes;
    }

    public double getSpokeAngle() {
        return spokeAngle;
    }

    public void setSpokeAngle(double spokeAngle) {
        this.spokeAngle = spokeAngle;
    }

    public double getGap() {
        return gap;
    }

    public void setGap(double gap) {
        this.gap = gap;
    }

    public boolean isPlated() {
        return plated;
    }

    public void setPlated(boolean plated) {
        this.plated = plated;
    }

    /**
     * Get the effective diameter for round symbols or the max dimension for others.
     * Useful for determining line widths.
     */
    public double getDiameter() {
        if (type == Type.ROUND) {
            return width;
        }
        return Math.max(width, height);
    }

    /**
     * Get inner diameter for donut types.
     */
    public double getInnerDiameter() {
        return param1;
    }

    /**
     * Get corner radius for rounded rectangles.
     */
    public double getCornerRadius() {
        return param1;
    }

    @Override
    public String toString() {
        return "StandardSymbol{" +
                "name='" + originalName + '\'' +
                ", type=" + type +
                ", width=" + width +
                ", height=" + height +
                '}';
    }
}

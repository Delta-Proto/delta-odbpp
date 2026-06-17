package com.deltaproto.deltaodbpp.export.render;

/**
 * Renders round (circle) symbols.
 * Symbol name format: r<diameter> (e.g., r50 = 50 mil diameter circle)
 */
public class RoundRenderer extends AbstractSymbolRenderer {

    private final double diameter;

    /**
     * Create a round symbol renderer.
     *
     * @param diameter Diameter in ODB++ units (inches)
     */
    public RoundRenderer(double diameter) {
        super(diameter, diameter);
        this.diameter = diameter;
    }

    /**
     * Create from mils.
     */
    public static RoundRenderer fromMils(double diameterMils) {
        return new RoundRenderer(diameterMils / 1000.0);
    }

    @Override
    public String render(double x, double y, double rotation, boolean mirror, double scale, String color) {
        double r = (diameter / 2.0) * scale;

        // Round symbols don't need rotation/mirror transforms (circles are symmetric)
        return String.format("<circle cx=\"%s\" cy=\"%s\" r=\"%s\" fill=\"%s\"/>",
                fmt(x), fmt(y), fmt(r), color);
    }

    public double getDiameter() {
        return diameter;
    }
}

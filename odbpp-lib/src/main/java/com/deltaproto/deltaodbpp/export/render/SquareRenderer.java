package com.deltaproto.deltaodbpp.export.render;

/**
 * Renders square symbols.
 * Symbol name format: s<size> (e.g., s40 = 40 mil square)
 */
public class SquareRenderer extends AbstractSymbolRenderer {

    private final double size;

    /**
     * Create a square symbol renderer.
     *
     * @param size Size in ODB++ units (inches)
     */
    public SquareRenderer(double size) {
        super(size, size);
        this.size = size;
    }

    /**
     * Create from mils.
     */
    public static SquareRenderer fromMils(double sizeMils) {
        return new SquareRenderer(sizeMils / 1000.0);
    }

    @Override
    public String render(double x, double y, double rotation, boolean mirror, double scale, String color) {
        double s = size * scale;
        double halfSize = s / 2.0;

        // Position is center, so offset by half size
        double rx = x - halfSize;
        double ry = y - halfSize;

        String transform = buildTransform(x, y, rotation, mirror, 1.0); // scale already applied

        return String.format("<rect x=\"%s\" y=\"%s\" width=\"%s\" height=\"%s\" fill=\"%s\"%s/>",
                fmt(rx), fmt(ry), fmt(s), fmt(s), color, transform);
    }

    public double getSize() {
        return size;
    }
}

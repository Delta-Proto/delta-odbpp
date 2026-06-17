package com.deltaproto.deltaodbpp.export.render;

/**
 * Interface for rendering ODB++ symbols to SVG.
 */
public interface SymbolRenderer {

    /**
     * Render the symbol at the given position with transforms.
     *
     * @param x        Center X position in ODB++ units (inches)
     * @param y        Center Y position in ODB++ units (inches)
     * @param rotation Rotation in degrees (counterclockwise)
     * @param mirror   Whether to mirror the symbol
     * @param scale    Scale factor (from resize, 1.0 = no resize)
     * @param color    Fill/stroke color
     * @return SVG element string
     */
    String render(double x, double y, double rotation, boolean mirror, double scale, String color);

    /**
     * Get the width of this symbol in ODB++ units.
     */
    double getWidth();

    /**
     * Get the height of this symbol in ODB++ units.
     */
    double getHeight();

    /**
     * Simple bounds class for symbol dimensions.
     */
    record Bounds(double width, double height) {
        public Bounds scaled(double scale) {
            return new Bounds(width * scale, height * scale);
        }
    }

    /**
     * Get the bounding box of this symbol.
     */
    default Bounds getBounds() {
        return new Bounds(getWidth(), getHeight());
    }
}

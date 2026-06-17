package com.deltaproto.deltaodbpp.export.render;

/**
 * Renders null symbols: zero-area placeholders.
 *
 * Format: null<ext>
 * - ext: extension number (for identification)
 *
 * The null symbol has zero area and is used as a placeholder for non-graphic features.
 * It renders as a very small point (single pixel) for visibility.
 */
public class NullRenderer extends AbstractSymbolRenderer {

    private final int extension;

    /**
     * Create a null symbol.
     */
    public static NullRenderer create(int extension) {
        return new NullRenderer(extension);
    }

    /**
     * Create a null symbol with no extension.
     */
    public static NullRenderer create() {
        return new NullRenderer(0);
    }

    private NullRenderer(int extension) {
        super(0.001, 0.001); // Minimal size for bounding box
        this.extension = extension;
    }

    @Override
    public String render(double x, double y, double rotation, boolean mirror, double scale, String color) {
        // Render as a very small circle (single pixel equivalent)
        double r = 0.0005 * scale; // Very small radius
        if (r < 0.0001) r = 0.0001; // Minimum visible size

        String transform = buildTransform(x, y, rotation, mirror, 1.0);

        return String.format(java.util.Locale.US,
                "<circle cx=\"%.4f\" cy=\"%.4f\" r=\"%.4f\" fill=\"%s\"%s/>",
                x, y, r, color, transform);
    }

    public int getExtension() {
        return extension;
    }
}

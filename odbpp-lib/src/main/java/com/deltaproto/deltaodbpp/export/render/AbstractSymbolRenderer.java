package com.deltaproto.deltaodbpp.export.render;

import java.util.Locale;

/**
 * Base class for symbol renderers with common utility methods.
 */
public abstract class AbstractSymbolRenderer implements SymbolRenderer {

    protected final double width;
    protected final double height;

    protected AbstractSymbolRenderer(double width, double height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public double getWidth() {
        return width;
    }

    @Override
    public double getHeight() {
        return height;
    }

    /**
     * Build a transform attribute string for rotation and mirror.
     */
    protected String buildTransform(double x, double y, double rotation, boolean mirror, double scale) {
        StringBuilder transform = new StringBuilder();

        boolean hasTransform = rotation != 0 || mirror || scale != 1.0;
        if (!hasTransform) {
            return "";
        }

        transform.append(" transform=\"");

        // Translate to position, apply transforms, then translate back
        // This ensures rotation/mirror happen around the symbol center
        if (rotation != 0 || mirror) {
            transform.append(String.format(Locale.US, "translate(%.6f,%.6f) ", x, y));

            if (mirror) {
                transform.append("scale(-1,1) ");
            }

            if (rotation != 0) {
                transform.append(String.format(Locale.US, "rotate(%.2f) ", rotation));
            }

            if (scale != 1.0) {
                transform.append(String.format(Locale.US, "scale(%.6f) ", scale));
            }

            transform.append(String.format(Locale.US, "translate(%.6f,%.6f)", -x, -y));
        } else if (scale != 1.0) {
            // Scale only - scale around center
            transform.append(String.format(Locale.US, "translate(%.6f,%.6f) scale(%.6f) translate(%.6f,%.6f)",
                    x, y, scale, -x, -y));
        }

        transform.append("\"");
        return transform.toString();
    }

    /**
     * Format a double for SVG output (6 decimal places, trimmed).
     * Uses US locale to ensure periods as decimal separators.
     */
    protected String fmt(double value) {
        return String.format(Locale.US, "%.6f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}

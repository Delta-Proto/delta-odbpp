package com.deltaproto.deltaodbpp.export.render;

import java.util.Locale;

/**
 * Renders hole symbols: circular holes that can be plated or non-plated.
 *
 * Format: hole<d>x<p>x<type>x<mark>
 * - d: diameter
 * - p: plated (1) or non-plated (0)
 * - type: hole type
 * - mark: hole mark
 *
 * Holes are typically rendered as circles with an outline to indicate they are drill holes.
 */
public class HoleRenderer extends AbstractSymbolRenderer {

    private final boolean plated;
    private final int holeType;
    private final int holeMark;

    /**
     * Create a hole renderer.
     */
    public static HoleRenderer create(double diameter, boolean plated, int holeType, int holeMark) {
        return new HoleRenderer(diameter, plated, holeType, holeMark);
    }

    /**
     * Create a hole from mils.
     */
    public static HoleRenderer createFromMils(double diameterMils, boolean plated, int holeType, int holeMark) {
        return create(diameterMils / 1000.0, plated, holeType, holeMark);
    }

    /**
     * Create a simple plated hole.
     */
    public static HoleRenderer plated(double diameter) {
        return create(diameter, true, 0, 0);
    }

    /**
     * Create a simple non-plated hole.
     */
    public static HoleRenderer nonPlated(double diameter) {
        return create(diameter, false, 0, 0);
    }

    private HoleRenderer(double diameter, boolean plated, int holeType, int holeMark) {
        super(diameter, diameter);
        this.plated = plated;
        this.holeType = holeType;
        this.holeMark = holeMark;
    }

    @Override
    public String render(double x, double y, double rotation, boolean mirror, double scale, String color) {
        double d = width * scale;
        double radius = d / 2.0;
        String transform = buildTransform(x, y, rotation, mirror, 1.0);

        // Render hole as a circle
        // For non-plated holes, we could render with a different style (e.g., cross pattern)
        // but for simplicity we render both as circles - the context determines the meaning
        if (plated) {
            // Plated hole: filled circle
            return String.format(Locale.US,
                    "<circle cx=\"%.4f\" cy=\"%.4f\" r=\"%.4f\" fill=\"%s\"%s/>",
                    x, y, radius, color, transform);
        } else {
            // Non-plated hole: typically shown with cross or different fill
            // For SVG rendering, we show it as a circle with cross marks
            StringBuilder svg = new StringBuilder();

            // Outer circle
            svg.append(String.format(Locale.US,
                    "<circle cx=\"%.4f\" cy=\"%.4f\" r=\"%.4f\" fill=\"%s\"%s/>",
                    x, y, radius, color, transform));

            // Add cross marks to indicate non-plated hole
            double crossSize = radius * 0.7;
            double strokeWidth = radius * 0.15;
            svg.append(String.format(Locale.US,
                    "<line x1=\"%.4f\" y1=\"%.4f\" x2=\"%.4f\" y2=\"%.4f\" stroke=\"white\" stroke-width=\"%.4f\"%s/>",
                    x - crossSize, y - crossSize, x + crossSize, y + crossSize, strokeWidth, transform));
            svg.append(String.format(Locale.US,
                    "<line x1=\"%.4f\" y1=\"%.4f\" x2=\"%.4f\" y2=\"%.4f\" stroke=\"white\" stroke-width=\"%.4f\"%s/>",
                    x - crossSize, y + crossSize, x + crossSize, y - crossSize, strokeWidth, transform));

            return svg.toString();
        }
    }

    public boolean isPlated() {
        return plated;
    }

    public int getHoleType() {
        return holeType;
    }

    public int getHoleMark() {
        return holeMark;
    }
}

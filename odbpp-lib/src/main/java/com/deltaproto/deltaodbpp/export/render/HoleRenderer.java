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

        // A hole symbol is the hole itself: a plain disc. Whether it is plated says how the
        // hole is made, not what it looks like, so both kinds draw the same.
        return String.format(Locale.US,
                "<circle cx=\"%.4f\" cy=\"%.4f\" r=\"%.4f\" fill=\"%s\"%s/>",
                x, y, radius, color, transform);
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

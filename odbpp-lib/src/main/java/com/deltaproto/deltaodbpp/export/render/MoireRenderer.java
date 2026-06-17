package com.deltaproto.deltaodbpp.export.render;

import java.util.Locale;

/**
 * Renders moire symbols: concentric rings with crosshair lines.
 * Used as registration/alignment marks on PCBs.
 * Format: moire<rw>x<rg>x<nr>x<lw>x<ll>x<la>
 *   rw - ring width
 *   rg - ring gap
 *   nr - number of rings
 *   lw - line width
 *   ll - line length
 *   la - line angle (rotation)
 */
public class MoireRenderer extends AbstractSymbolRenderer {

    private final double ringWidth;
    private final double ringGap;
    private final int numRings;
    private final double lineWidth;
    private final double lineLength;
    private final double lineAngle;

    /**
     * Create a moire symbol.
     */
    public static MoireRenderer create(double ringWidth, double ringGap, int numRings,
                                       double lineWidth, double lineLength, double lineAngle) {
        // Calculate overall diameter based on rings
        double diameter = (numRings * (ringWidth + ringGap) * 2);
        return new MoireRenderer(diameter, ringWidth, ringGap, numRings, lineWidth, lineLength, lineAngle);
    }

    /**
     * Create a moire symbol from mils.
     */
    public static MoireRenderer createFromMils(double ringWidthMils, double ringGapMils, int numRings,
                                               double lineWidthMils, double lineLengthMils, double lineAngle) {
        return create(ringWidthMils / 1000.0, ringGapMils / 1000.0, numRings,
                lineWidthMils / 1000.0, lineLengthMils / 1000.0, lineAngle);
    }

    private MoireRenderer(double diameter, double ringWidth, double ringGap, int numRings,
                          double lineWidth, double lineLength, double lineAngle) {
        super(diameter, diameter);
        this.ringWidth = ringWidth;
        this.ringGap = ringGap;
        this.numRings = numRings;
        this.lineWidth = lineWidth;
        this.lineLength = lineLength;
        this.lineAngle = lineAngle;
    }

    @Override
    public String render(double x, double y, double rotation, boolean mirror, double scale, String color) {
        StringBuilder svg = new StringBuilder();
        svg.append("<g");
        String transform = buildTransform(x, y, rotation + lineAngle, mirror, 1.0);
        if (!transform.isEmpty()) {
            svg.append(transform);
        }
        svg.append(">");

        // Draw concentric rings
        double currentRadius = 0;
        for (int i = 0; i < numRings; i++) {
            double innerR = currentRadius + (i == 0 ? 0 : ringGap) * scale;
            double outerR = innerR + ringWidth * scale;

            if (innerR > 0) {
                // Ring as path with evenodd fill rule
                svg.append(String.format(Locale.US,
                        "<circle cx=\"%.4f\" cy=\"%.4f\" r=\"%.4f\" fill=\"none\" stroke=\"%s\" stroke-width=\"%.4f\"/>",
                        x, y, (innerR + outerR) / 2, color, ringWidth * scale));
            } else {
                // First ring is a filled circle
                svg.append(String.format(Locale.US,
                        "<circle cx=\"%.4f\" cy=\"%.4f\" r=\"%.4f\" fill=\"%s\"/>",
                        x, y, outerR, color));
            }

            currentRadius = outerR;
        }

        // Draw crosshair lines
        double halfLen = (lineLength / 2) * scale;
        double lw = lineWidth * scale;

        // Horizontal line
        svg.append(String.format(Locale.US,
                "<line x1=\"%.4f\" y1=\"%.4f\" x2=\"%.4f\" y2=\"%.4f\" stroke=\"%s\" stroke-width=\"%.4f\"/>",
                x - halfLen, y, x + halfLen, y, color, lw));

        // Vertical line
        svg.append(String.format(Locale.US,
                "<line x1=\"%.4f\" y1=\"%.4f\" x2=\"%.4f\" y2=\"%.4f\" stroke=\"%s\" stroke-width=\"%.4f\"/>",
                x, y - halfLen, x, y + halfLen, color, lw));

        svg.append("</g>");
        return svg.toString();
    }

    public double getRingWidth() {
        return ringWidth;
    }

    public double getRingGap() {
        return ringGap;
    }

    public int getNumRings() {
        return numRings;
    }

    public double getLineWidth() {
        return lineWidth;
    }

    public double getLineLength() {
        return lineLength;
    }

    public double getLineAngle() {
        return lineAngle;
    }
}

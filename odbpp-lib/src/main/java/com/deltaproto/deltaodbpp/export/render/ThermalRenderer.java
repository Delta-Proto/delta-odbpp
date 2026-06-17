package com.deltaproto.deltaodbpp.export.render;

import java.util.Locale;

/**
 * Renders thermal relief symbols: round and square thermals.
 * Thermal relief pads have gaps (spokes) for heat dissipation during soldering.
 */
public class ThermalRenderer extends AbstractSymbolRenderer {

    public enum Shape {
        ROUND,          // thr<od>x<id>x<angle>x<num>x<gap>
        SQUARE          // ths<od>x<id>x<angle>x<num>x<gap>
    }

    private final Shape shape;
    private final double innerDiameter;
    private final double spokeAngle;    // Starting angle for first spoke
    private final int numSpokes;        // Number of spokes (gaps)
    private final double gapWidth;      // Width of gaps between spokes

    /**
     * Create a round thermal.
     */
    public static ThermalRenderer round(double outerDiameter, double innerDiameter,
                                        double spokeAngle, int numSpokes, double gapWidth) {
        return new ThermalRenderer(outerDiameter, outerDiameter, Shape.ROUND,
                innerDiameter, spokeAngle, numSpokes, gapWidth);
    }

    /**
     * Create a round thermal from mils.
     */
    public static ThermalRenderer roundFromMils(double outerMils, double innerMils,
                                                 double spokeAngle, int numSpokes, double gapMils) {
        return round(outerMils / 1000.0, innerMils / 1000.0, spokeAngle, numSpokes, gapMils / 1000.0);
    }

    /**
     * Create a square thermal.
     */
    public static ThermalRenderer square(double outerSize, double innerSize,
                                         double spokeAngle, int numSpokes, double gapWidth) {
        return new ThermalRenderer(outerSize, outerSize, Shape.SQUARE,
                innerSize, spokeAngle, numSpokes, gapWidth);
    }

    /**
     * Create a square thermal from mils.
     */
    public static ThermalRenderer squareFromMils(double outerMils, double innerMils,
                                                  double spokeAngle, int numSpokes, double gapMils) {
        return square(outerMils / 1000.0, innerMils / 1000.0, spokeAngle, numSpokes, gapMils / 1000.0);
    }

    /**
     * Create an oval thermal.
     * For oval thermal, lineWidth defines the ring thickness.
     */
    public static ThermalRenderer oval(double outerWidth, double outerHeight, double lineWidth,
                                       double spokeAngle, int numSpokes, double gapWidth) {
        // Use round thermal rendering as approximation (oval shape controlled by width/height)
        return new ThermalRenderer(outerWidth, outerHeight, Shape.ROUND,
                Math.min(outerWidth, outerHeight) - 2 * lineWidth, spokeAngle, numSpokes, gapWidth);
    }

    private ThermalRenderer(double width, double height, Shape shape,
                            double innerDiameter, double spokeAngle, int numSpokes, double gapWidth) {
        super(width, height);
        this.shape = shape;
        this.innerDiameter = innerDiameter;
        this.spokeAngle = spokeAngle;
        this.numSpokes = numSpokes;
        this.gapWidth = gapWidth;
    }

    @Override
    public String render(double x, double y, double rotation, boolean mirror, double scale, String color) {
        double outerD = width * scale;
        double innerD = innerDiameter * scale;
        double gap = gapWidth * scale;

        String transform = buildTransform(x, y, rotation, mirror, 1.0);

        return switch (shape) {
            case ROUND -> renderRoundThermal(x, y, outerD, innerD, gap, color, transform);
            case SQUARE -> renderSquareThermal(x, y, outerD, innerD, gap, color, transform);
        };
    }

    /**
     * Round thermal: ring with gaps.
     * Creates a donut with wedge-shaped gaps cut out.
     */
    private String renderRoundThermal(double x, double y, double outerD, double innerD,
                                       double gap, String color, String transform) {
        double outerR = outerD / 2.0;
        double innerR = innerD / 2.0;

        // Calculate angle for each gap based on gap width at middle radius
        double middleR = (outerR + innerR) / 2.0;
        double gapAngle = Math.toDegrees(2 * Math.asin(gap / (2 * middleR)));
        double spokeAngleSpan = 360.0 / numSpokes;
        double arcAngle = spokeAngleSpan - gapAngle;

        StringBuilder path = new StringBuilder();

        for (int i = 0; i < numSpokes; i++) {
            double startAngle = spokeAngle + i * spokeAngleSpan + gapAngle / 2;
            double endAngle = startAngle + arcAngle;

            // Outer arc
            double startRad = Math.toRadians(startAngle);
            double endRad = Math.toRadians(endAngle);

            double outerStartX = x + outerR * Math.cos(startRad);
            double outerStartY = y + outerR * Math.sin(startRad);
            double outerEndX = x + outerR * Math.cos(endRad);
            double outerEndY = y + outerR * Math.sin(endRad);

            double innerStartX = x + innerR * Math.cos(startRad);
            double innerStartY = y + innerR * Math.sin(startRad);
            double innerEndX = x + innerR * Math.cos(endRad);
            double innerEndY = y + innerR * Math.sin(endRad);

            int largeArc = arcAngle > 180 ? 1 : 0;

            // Draw arc segment
            path.append(" M ").append(fmt(outerStartX)).append(" ").append(fmt(outerStartY));
            path.append(" A ").append(fmt(outerR)).append(" ").append(fmt(outerR));
            path.append(" 0 ").append(largeArc).append(" 1 ");
            path.append(fmt(outerEndX)).append(" ").append(fmt(outerEndY));
            path.append(" L ").append(fmt(innerEndX)).append(" ").append(fmt(innerEndY));
            path.append(" A ").append(fmt(innerR)).append(" ").append(fmt(innerR));
            path.append(" 0 ").append(largeArc).append(" 0 ");
            path.append(fmt(innerStartX)).append(" ").append(fmt(innerStartY));
            path.append(" Z");
        }

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\"%s/>",
                path.toString().trim(), color, transform);
    }

    /**
     * Square thermal: square ring with gaps.
     */
    private String renderSquareThermal(double x, double y, double outerD, double innerD,
                                        double gap, String color, String transform) {
        double halfOuter = outerD / 2.0;
        double halfInner = innerD / 2.0;
        double halfGap = gap / 2.0;

        StringBuilder path = new StringBuilder();

        // For 4 spokes at 45 degrees (typical), we draw 4 L-shaped segments
        // Each segment is in a corner of the square

        double spokeAngleSpan = 360.0 / numSpokes;

        for (int i = 0; i < numSpokes; i++) {
            double angle = spokeAngle + i * spokeAngleSpan;
            double rad = Math.toRadians(angle);

            // Determine which quadrant we're in and draw the corresponding segment
            // Simplified: draw rectangular segments with gaps

            double cos = Math.cos(rad);
            double sin = Math.sin(rad);

            // Create a segment in the direction of the spoke
            // This is a simplified version - for proper square thermal we'd need
            // to intersect with the square outline

            // Draw as rotated rectangle segment
            double segmentLength = halfOuter - halfInner;
            double segmentWidth = (Math.PI * (halfOuter + halfInner) / numSpokes) - gap;
            segmentWidth = Math.max(segmentWidth, 0.001);

            double segmentCenterR = (halfOuter + halfInner) / 2.0;
            double segmentCenterX = x + segmentCenterR * cos;
            double segmentCenterY = y + segmentCenterR * sin;

            // Perpendicular direction
            double perpCos = Math.cos(rad + Math.PI / 2);
            double perpSin = Math.sin(rad + Math.PI / 2);

            // Corners of segment
            double halfLen = segmentLength / 2.0;
            double halfWid = segmentWidth / 2.0;

            double x1 = segmentCenterX - halfLen * cos - halfWid * perpCos;
            double y1 = segmentCenterY - halfLen * sin - halfWid * perpSin;
            double x2 = segmentCenterX + halfLen * cos - halfWid * perpCos;
            double y2 = segmentCenterY + halfLen * sin - halfWid * perpSin;
            double x3 = segmentCenterX + halfLen * cos + halfWid * perpCos;
            double y3 = segmentCenterY + halfLen * sin + halfWid * perpSin;
            double x4 = segmentCenterX - halfLen * cos + halfWid * perpCos;
            double y4 = segmentCenterY - halfLen * sin + halfWid * perpSin;

            path.append(" M ").append(fmt(x1)).append(" ").append(fmt(y1));
            path.append(" L ").append(fmt(x2)).append(" ").append(fmt(y2));
            path.append(" L ").append(fmt(x3)).append(" ").append(fmt(y3));
            path.append(" L ").append(fmt(x4)).append(" ").append(fmt(y4));
            path.append(" Z");
        }

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\"%s/>",
                path.toString().trim(), color, transform);
    }

    public Shape getShape() {
        return shape;
    }

    public double getInnerDiameter() {
        return innerDiameter;
    }

    public int getNumSpokes() {
        return numSpokes;
    }

    public double getGapWidth() {
        return gapWidth;
    }
}

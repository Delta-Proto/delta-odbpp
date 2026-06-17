package com.deltaproto.deltaodbpp.export.render;

import java.util.Locale;

/**
 * Renders butterfly symbols: round and square butterfly shapes.
 * A butterfly is a bowtie-like shape with two triangular halves meeting at the center.
 *
 * Format:
 * - bfr<d> - Round butterfly with diameter d
 * - bfs<s> - Square butterfly with size s
 */
public class ButterflyRenderer extends AbstractSymbolRenderer {

    public enum Shape {
        ROUND,      // bfr<d> - inscribed in a circle
        SQUARE      // bfs<s> - inscribed in a square
    }

    private final Shape shape;

    /**
     * Create a round butterfly.
     */
    public static ButterflyRenderer round(double diameter) {
        return new ButterflyRenderer(diameter, diameter, Shape.ROUND);
    }

    /**
     * Create a round butterfly from mils.
     */
    public static ButterflyRenderer roundFromMils(double diameterMils) {
        return round(diameterMils / 1000.0);
    }

    /**
     * Create a square butterfly.
     */
    public static ButterflyRenderer square(double size) {
        return new ButterflyRenderer(size, size, Shape.SQUARE);
    }

    /**
     * Create a square butterfly from mils.
     */
    public static ButterflyRenderer squareFromMils(double sizeMils) {
        return square(sizeMils / 1000.0);
    }

    private ButterflyRenderer(double width, double height, Shape shape) {
        super(width, height);
        this.shape = shape;
    }

    @Override
    public String render(double x, double y, double rotation, boolean mirror, double scale, String color) {
        double size = width * scale;
        String transform = buildTransform(x, y, rotation, mirror, 1.0);

        return switch (shape) {
            case ROUND -> renderRoundButterfly(x, y, size, color, transform);
            case SQUARE -> renderSquareButterfly(x, y, size, color, transform);
        };
    }

    /**
     * Round butterfly: bowtie shape inscribed in a circle.
     * Two triangular halves meeting at the center, with curved outer edges.
     */
    private String renderRoundButterfly(double x, double y, double size, String color, String transform) {
        double radius = size / 2.0;

        // Draw two wedges meeting at the center
        // Each wedge spans 90 degrees, centered on the horizontal axis
        StringBuilder path = new StringBuilder();

        // Right wedge (pointing right)
        path.append("M ").append(fmt(x)).append(" ").append(fmt(y));
        // Arc from top-right to bottom-right
        double topX = x + radius * Math.cos(Math.toRadians(45));
        double topY = y - radius * Math.sin(Math.toRadians(45));
        double botX = x + radius * Math.cos(Math.toRadians(-45));
        double botY = y - radius * Math.sin(Math.toRadians(-45));
        path.append(" L ").append(fmt(topX)).append(" ").append(fmt(topY));
        path.append(" A ").append(fmt(radius)).append(" ").append(fmt(radius));
        path.append(" 0 0 1 ");
        path.append(fmt(botX)).append(" ").append(fmt(botY));
        path.append(" Z");

        // Left wedge (pointing left)
        path.append(" M ").append(fmt(x)).append(" ").append(fmt(y));
        double leftTopX = x + radius * Math.cos(Math.toRadians(135));
        double leftTopY = y - radius * Math.sin(Math.toRadians(135));
        double leftBotX = x + radius * Math.cos(Math.toRadians(-135));
        double leftBotY = y - radius * Math.sin(Math.toRadians(-135));
        path.append(" L ").append(fmt(leftTopX)).append(" ").append(fmt(leftTopY));
        path.append(" A ").append(fmt(radius)).append(" ").append(fmt(radius));
        path.append(" 0 0 0 ");
        path.append(fmt(leftBotX)).append(" ").append(fmt(leftBotY));
        path.append(" Z");

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\"%s/>",
                path.toString(), color, transform);
    }

    /**
     * Square butterfly: bowtie shape inscribed in a square.
     * Two triangular halves meeting at the center, with straight outer edges.
     */
    private String renderSquareButterfly(double x, double y, double size, String color, String transform) {
        double halfSize = size / 2.0;

        StringBuilder path = new StringBuilder();

        // Right triangle (pointing right)
        path.append("M ").append(fmt(x)).append(" ").append(fmt(y)); // center
        path.append(" L ").append(fmt(x + halfSize)).append(" ").append(fmt(y - halfSize)); // top-right
        path.append(" L ").append(fmt(x + halfSize)).append(" ").append(fmt(y + halfSize)); // bottom-right
        path.append(" Z");

        // Left triangle (pointing left)
        path.append(" M ").append(fmt(x)).append(" ").append(fmt(y)); // center
        path.append(" L ").append(fmt(x - halfSize)).append(" ").append(fmt(y - halfSize)); // top-left
        path.append(" L ").append(fmt(x - halfSize)).append(" ").append(fmt(y + halfSize)); // bottom-left
        path.append(" Z");

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\"%s/>",
                path.toString(), color, transform);
    }

    public Shape getShape() {
        return shape;
    }
}

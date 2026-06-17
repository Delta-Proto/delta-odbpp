package com.deltaproto.deltaodbpp.export.render;

import java.util.Locale;

/**
 * Renders polygon-based symbols: diamond, octagon, hexagon, triangle, half-oval.
 */
public class PolygonRenderer extends AbstractSymbolRenderer {

    public enum Shape {
        DIAMOND,        // di<w>x<h>
        OCTAGON,        // oct<w>x<h>x<c>
        HEXAGON_H,      // hex_l<w>x<h>x<c> (horizontal - points left/right)
        HEXAGON_V,      // hex_s<w>x<h>x<c> (vertical - points top/bottom)
        TRIANGLE,       // tri<b>x<h>
        HALF_OVAL       // ho<w>x<h>
    }

    private final Shape shape;
    private final double cornerParam; // corner cut for octagon/hexagon

    /**
     * Create a diamond renderer.
     */
    public static PolygonRenderer diamond(double width, double height) {
        return new PolygonRenderer(width, height, Shape.DIAMOND, 0);
    }

    /**
     * Create a diamond from mils.
     */
    public static PolygonRenderer diamondFromMils(double widthMils, double heightMils) {
        return diamond(widthMils / 1000.0, heightMils / 1000.0);
    }

    /**
     * Create an octagon renderer.
     */
    public static PolygonRenderer octagon(double width, double height, double cornerCut) {
        return new PolygonRenderer(width, height, Shape.OCTAGON, cornerCut);
    }

    /**
     * Create an octagon from mils.
     */
    public static PolygonRenderer octagonFromMils(double widthMils, double heightMils, double cornerMils) {
        return octagon(widthMils / 1000.0, heightMils / 1000.0, cornerMils / 1000.0);
    }

    /**
     * Create a horizontal hexagon (points left and right).
     */
    public static PolygonRenderer hexagonH(double width, double height, double cornerCut) {
        return new PolygonRenderer(width, height, Shape.HEXAGON_H, cornerCut);
    }

    /**
     * Create a horizontal hexagon from mils.
     */
    public static PolygonRenderer hexagonHFromMils(double widthMils, double heightMils, double cornerMils) {
        return hexagonH(widthMils / 1000.0, heightMils / 1000.0, cornerMils / 1000.0);
    }

    /**
     * Create a vertical hexagon (points top and bottom).
     */
    public static PolygonRenderer hexagonV(double width, double height, double cornerCut) {
        return new PolygonRenderer(width, height, Shape.HEXAGON_V, cornerCut);
    }

    /**
     * Create a vertical hexagon from mils.
     */
    public static PolygonRenderer hexagonVFromMils(double widthMils, double heightMils, double cornerMils) {
        return hexagonV(widthMils / 1000.0, heightMils / 1000.0, cornerMils / 1000.0);
    }

    /**
     * Create a triangle renderer (isoceles, pointing up).
     */
    public static PolygonRenderer triangle(double base, double height) {
        return new PolygonRenderer(base, height, Shape.TRIANGLE, 0);
    }

    /**
     * Create a triangle from mils.
     */
    public static PolygonRenderer triangleFromMils(double baseMils, double heightMils) {
        return triangle(baseMils / 1000.0, heightMils / 1000.0);
    }

    /**
     * Create a half-oval renderer (semicircle on one end).
     */
    public static PolygonRenderer halfOval(double width, double height) {
        return new PolygonRenderer(width, height, Shape.HALF_OVAL, 0);
    }

    /**
     * Create a half-oval from mils.
     */
    public static PolygonRenderer halfOvalFromMils(double widthMils, double heightMils) {
        return halfOval(widthMils / 1000.0, heightMils / 1000.0);
    }

    private PolygonRenderer(double width, double height, Shape shape, double cornerParam) {
        super(width, height);
        this.shape = shape;
        this.cornerParam = cornerParam;
    }

    @Override
    public String render(double x, double y, double rotation, boolean mirror, double scale, String color) {
        double w = width * scale;
        double h = height * scale;
        double c = cornerParam * scale;

        String transform = buildTransform(x, y, rotation, mirror, 1.0);

        return switch (shape) {
            case DIAMOND -> renderDiamond(x, y, w, h, color, transform);
            case OCTAGON -> renderOctagon(x, y, w, h, c, color, transform);
            case HEXAGON_H -> renderHexagonH(x, y, w, h, c, color, transform);
            case HEXAGON_V -> renderHexagonV(x, y, w, h, c, color, transform);
            case TRIANGLE -> renderTriangle(x, y, w, h, color, transform);
            case HALF_OVAL -> renderHalfOval(x, y, w, h, color, transform);
        };
    }

    /**
     * Diamond: rotated square shape.
     * Points at top, bottom, left, right.
     */
    private String renderDiamond(double x, double y, double w, double h,
                                  String color, String transform) {
        double halfW = w / 2.0;
        double halfH = h / 2.0;

        StringBuilder path = new StringBuilder();
        path.append("M ").append(fmt(x)).append(" ").append(fmt(y - halfH)); // top
        path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y)); // right
        path.append(" L ").append(fmt(x)).append(" ").append(fmt(y + halfH)); // bottom
        path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y)); // left
        path.append(" Z");

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\"%s/>", path, color, transform);
    }

    /**
     * Octagon: rectangle with corners cut off.
     */
    private String renderOctagon(double x, double y, double w, double h, double c,
                                  String color, String transform) {
        double halfW = w / 2.0;
        double halfH = h / 2.0;
        // Limit corner cut to not exceed half dimensions
        double corner = Math.min(c, Math.min(halfW, halfH));

        StringBuilder path = new StringBuilder();
        // Start at top-left + corner, go clockwise
        path.append("M ").append(fmt(x - halfW + corner)).append(" ").append(fmt(y - halfH));
        path.append(" L ").append(fmt(x + halfW - corner)).append(" ").append(fmt(y - halfH)); // top edge
        path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y - halfH + corner)); // top-right corner
        path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y + halfH - corner)); // right edge
        path.append(" L ").append(fmt(x + halfW - corner)).append(" ").append(fmt(y + halfH)); // bottom-right corner
        path.append(" L ").append(fmt(x - halfW + corner)).append(" ").append(fmt(y + halfH)); // bottom edge
        path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y + halfH - corner)); // bottom-left corner
        path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y - halfH + corner)); // left edge
        path.append(" Z");

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\"%s/>", path, color, transform);
    }

    /**
     * Horizontal hexagon: points on left and right sides.
     * Like a stretched hexagon lying flat.
     */
    private String renderHexagonH(double x, double y, double w, double h, double c,
                                   String color, String transform) {
        double halfW = w / 2.0;
        double halfH = h / 2.0;
        double corner = Math.min(c, halfW);

        StringBuilder path = new StringBuilder();
        // Start at top-left, go clockwise
        path.append("M ").append(fmt(x - halfW + corner)).append(" ").append(fmt(y - halfH)); // top-left
        path.append(" L ").append(fmt(x + halfW - corner)).append(" ").append(fmt(y - halfH)); // top-right
        path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y)); // right point
        path.append(" L ").append(fmt(x + halfW - corner)).append(" ").append(fmt(y + halfH)); // bottom-right
        path.append(" L ").append(fmt(x - halfW + corner)).append(" ").append(fmt(y + halfH)); // bottom-left
        path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y)); // left point
        path.append(" Z");

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\"%s/>", path, color, transform);
    }

    /**
     * Vertical hexagon: points on top and bottom.
     * Like a stretched hexagon standing upright.
     */
    private String renderHexagonV(double x, double y, double w, double h, double c,
                                   String color, String transform) {
        double halfW = w / 2.0;
        double halfH = h / 2.0;
        double corner = Math.min(c, halfH);

        StringBuilder path = new StringBuilder();
        // Start at top point, go clockwise
        path.append("M ").append(fmt(x)).append(" ").append(fmt(y - halfH)); // top point
        path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y - halfH + corner)); // top-right
        path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y + halfH - corner)); // bottom-right
        path.append(" L ").append(fmt(x)).append(" ").append(fmt(y + halfH)); // bottom point
        path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y + halfH - corner)); // bottom-left
        path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y - halfH + corner)); // top-left
        path.append(" Z");

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\"%s/>", path, color, transform);
    }

    /**
     * Triangle: isoceles triangle pointing up.
     */
    private String renderTriangle(double x, double y, double w, double h,
                                   String color, String transform) {
        double halfW = w / 2.0;
        double halfH = h / 2.0;

        StringBuilder path = new StringBuilder();
        path.append("M ").append(fmt(x)).append(" ").append(fmt(y - halfH)); // top point
        path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y + halfH)); // bottom-right
        path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y + halfH)); // bottom-left
        path.append(" Z");

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\"%s/>", path, color, transform);
    }

    /**
     * Half-oval: rectangle with semicircle on one end.
     * Semicircle is on the right side for horizontal, top for vertical.
     */
    private String renderHalfOval(double x, double y, double w, double h,
                                   String color, String transform) {
        double halfW = w / 2.0;
        double halfH = h / 2.0;

        boolean horizontal = w >= h;

        if (horizontal) {
            // Semicircle on the right
            double radius = h / 2.0;
            double rectWidth = w - radius;

            StringBuilder path = new StringBuilder();
            path.append("M ").append(fmt(x - halfW)).append(" ").append(fmt(y - halfH)); // top-left
            path.append(" L ").append(fmt(x - halfW + rectWidth)).append(" ").append(fmt(y - halfH)); // top edge
            // Arc to bottom
            path.append(" A ").append(fmt(radius)).append(" ").append(fmt(radius));
            path.append(" 0 0 1 ");
            path.append(fmt(x - halfW + rectWidth)).append(" ").append(fmt(y + halfH));
            path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y + halfH)); // bottom edge
            path.append(" Z");

            return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\"%s/>", path, color, transform);
        } else {
            // Semicircle on top
            double radius = w / 2.0;
            double rectHeight = h - radius;

            StringBuilder path = new StringBuilder();
            // Start at bottom-left, go clockwise
            path.append("M ").append(fmt(x - halfW)).append(" ").append(fmt(y + halfH)); // bottom-left
            path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y + halfH - rectHeight)); // left edge
            // Arc to right
            path.append(" A ").append(fmt(radius)).append(" ").append(fmt(radius));
            path.append(" 0 0 1 ");
            path.append(fmt(x + halfW)).append(" ").append(fmt(y + halfH - rectHeight));
            path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y + halfH)); // right edge
            path.append(" Z");

            return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\"%s/>", path, color, transform);
        }
    }

    public Shape getShape() {
        return shape;
    }

    public double getCornerParam() {
        return cornerParam;
    }
}

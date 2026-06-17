package com.deltaproto.deltaodbpp.export.render;

import java.util.Locale;

/**
 * Renders home plate symbols: pentagon-shaped pads used for solder stencil design.
 *
 * Variants:
 * - hplate<w>x<h>x<c> - Home plate (pentagon)
 * - rhplate<w>x<h>x<c> - Inverted home plate (reversed pentagon)
 * - fhplate<w>x<h>x<vc>x<hc> - Flat home plate (with horizontal and vertical cuts)
 * - radhplate<w>x<h>x<c>x<ra>x<ro> - Radiused inverted home plate
 * - dshape<w>x<h>x<ra> - D-shape (radiused home plate)
 */
public class HomePlateRenderer extends AbstractSymbolRenderer {

    public enum Shape {
        HOME_PLATE,           // hplate - pentagon pointing down
        INVERTED_HOME_PLATE,  // rhplate - pentagon pointing up
        FLAT_HOME_PLATE,      // fhplate - with horizontal and vertical cuts
        RADIUSED_INVERTED,    // radhplate - inverted with corner radius
        D_SHAPE               // dshape - radiused home plate (D-shape)
    }

    private final Shape shape;
    private final double cutSize;        // c - cut size
    private final double verticalCut;    // vc - for fhplate
    private final double horizontalCut;  // hc - for fhplate
    private final double radiusAcute;    // ra - radius for acute angle
    private final double radiusObtuse;   // ro - radius for obtuse angle

    /**
     * Create a home plate symbol.
     */
    public static HomePlateRenderer homePlate(double width, double height, double cutSize) {
        return new HomePlateRenderer(width, height, Shape.HOME_PLATE, cutSize, 0, 0, 0, 0);
    }

    /**
     * Create an inverted home plate symbol.
     */
    public static HomePlateRenderer invertedHomePlate(double width, double height, double cutSize) {
        return new HomePlateRenderer(width, height, Shape.INVERTED_HOME_PLATE, cutSize, 0, 0, 0, 0);
    }

    /**
     * Create a flat home plate symbol.
     */
    public static HomePlateRenderer flatHomePlate(double width, double height, double verticalCut, double horizontalCut) {
        return new HomePlateRenderer(width, height, Shape.FLAT_HOME_PLATE, 0, verticalCut, horizontalCut, 0, 0);
    }

    /**
     * Create a radiused inverted home plate symbol.
     */
    public static HomePlateRenderer radiusedInverted(double width, double height, double cutSize,
                                                      double radiusAcute, double radiusObtuse) {
        return new HomePlateRenderer(width, height, Shape.RADIUSED_INVERTED, cutSize, 0, 0, radiusAcute, radiusObtuse);
    }

    /**
     * Create a D-shape symbol (radiused home plate).
     */
    public static HomePlateRenderer dShape(double width, double height, double radius) {
        return new HomePlateRenderer(width, height, Shape.D_SHAPE, 0, 0, 0, radius, 0);
    }

    private HomePlateRenderer(double width, double height, Shape shape, double cutSize,
                              double verticalCut, double horizontalCut,
                              double radiusAcute, double radiusObtuse) {
        super(width, height);
        this.shape = shape;
        this.cutSize = cutSize;
        this.verticalCut = verticalCut;
        this.horizontalCut = horizontalCut;
        this.radiusAcute = radiusAcute;
        this.radiusObtuse = radiusObtuse;
    }

    @Override
    public String render(double x, double y, double rotation, boolean mirror, double scale, String color) {
        double w = width * scale;
        double h = height * scale;
        double c = cutSize * scale;
        double vc = verticalCut * scale;
        double hc = horizontalCut * scale;
        double ra = radiusAcute * scale;

        String transform = buildTransform(x, y, rotation, mirror, 1.0);

        return switch (shape) {
            case HOME_PLATE -> renderHomePlate(x, y, w, h, c, color, transform);
            case INVERTED_HOME_PLATE -> renderInvertedHomePlate(x, y, w, h, c, color, transform);
            case FLAT_HOME_PLATE -> renderFlatHomePlate(x, y, w, h, vc, hc, color, transform);
            case RADIUSED_INVERTED -> renderRadiusedInverted(x, y, w, h, c, ra, color, transform);
            case D_SHAPE -> renderDShape(x, y, w, h, ra, color, transform);
        };
    }

    /**
     * Home plate: pentagon shape like baseball home plate.
     * Flat top, angled sides meeting at bottom point.
     */
    private String renderHomePlate(double x, double y, double w, double h, double c,
                                    String color, String transform) {
        double halfW = w / 2.0;
        double halfH = h / 2.0;
        // c is the cut size from the bottom corners

        StringBuilder path = new StringBuilder();
        // Start at top-left, go clockwise
        path.append("M ").append(fmt(x - halfW)).append(" ").append(fmt(y - halfH)); // top-left
        path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y - halfH)); // top-right
        path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y + halfH - c)); // right side
        path.append(" L ").append(fmt(x)).append(" ").append(fmt(y + halfH)); // bottom point
        path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y + halfH - c)); // left side
        path.append(" Z");

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\"%s/>", path, color, transform);
    }

    /**
     * Inverted home plate: pentagon pointing up.
     */
    private String renderInvertedHomePlate(double x, double y, double w, double h, double c,
                                            String color, String transform) {
        double halfW = w / 2.0;
        double halfH = h / 2.0;

        StringBuilder path = new StringBuilder();
        // Start at top point, go clockwise
        path.append("M ").append(fmt(x)).append(" ").append(fmt(y - halfH)); // top point
        path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y - halfH + c)); // top-right
        path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y + halfH)); // bottom-right
        path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y + halfH)); // bottom-left
        path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y - halfH + c)); // top-left
        path.append(" Z");

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\"%s/>", path, color, transform);
    }

    /**
     * Flat home plate: with horizontal and vertical cuts.
     */
    private String renderFlatHomePlate(double x, double y, double w, double h, double vc, double hc,
                                        String color, String transform) {
        double halfW = w / 2.0;
        double halfH = h / 2.0;

        StringBuilder path = new StringBuilder();
        // Hexagon-like shape with cuts
        path.append("M ").append(fmt(x - halfW + hc)).append(" ").append(fmt(y - halfH)); // top edge start
        path.append(" L ").append(fmt(x + halfW - hc)).append(" ").append(fmt(y - halfH)); // top edge end
        path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y - halfH + vc)); // top-right cut
        path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y + halfH - vc)); // right edge
        path.append(" L ").append(fmt(x + halfW - hc)).append(" ").append(fmt(y + halfH)); // bottom-right cut
        path.append(" L ").append(fmt(x - halfW + hc)).append(" ").append(fmt(y + halfH)); // bottom edge
        path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y + halfH - vc)); // bottom-left cut
        path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y - halfH + vc)); // left edge
        path.append(" Z");

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\"%s/>", path, color, transform);
    }

    /**
     * Radiused inverted home plate: with corner radius.
     */
    private String renderRadiusedInverted(double x, double y, double w, double h, double c, double ra,
                                           String color, String transform) {
        // For now, render as regular inverted home plate
        // TODO: Add arc corners with radius ra
        return renderInvertedHomePlate(x, y, w, h, c, color, transform);
    }

    /**
     * D-shape: semicircle on one side.
     */
    private String renderDShape(double x, double y, double w, double h, double ra,
                                 String color, String transform) {
        double halfW = w / 2.0;
        double halfH = h / 2.0;
        double radius = Math.min(ra, halfH);

        StringBuilder path = new StringBuilder();
        // Rectangle with semicircle on right side
        path.append("M ").append(fmt(x - halfW)).append(" ").append(fmt(y - halfH)); // top-left
        path.append(" L ").append(fmt(x + halfW - radius)).append(" ").append(fmt(y - halfH)); // top edge
        // Arc on right side
        path.append(" A ").append(fmt(radius)).append(" ").append(fmt(halfH));
        path.append(" 0 0 1 ");
        path.append(fmt(x + halfW - radius)).append(" ").append(fmt(y + halfH));
        path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y + halfH)); // bottom edge
        path.append(" Z");

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\"%s/>", path, color, transform);
    }

    public Shape getShape() {
        return shape;
    }
}

package com.deltaproto.deltaodbpp.export.render;

import java.util.Locale;

/**
 * Renders cross and dogbone symbols used for solder stencil design.
 *
 * Variants:
 * - cross<w>x<h>x<hs>x<vs>x<hc>x<vc>x[r|s] - Cross symbol
 * - dogbone<w>x<h>x<hs>x<vs>x<hc>x[r|s] - Dogbone symbol
 *
 * Parameters:
 * - w: horizontal side (overall width)
 * - h: vertical side (overall height)
 * - hs: horizontal line width
 * - vs: vertical line width
 * - hc: horizontal cross point (percentage for cross, absolute for dogbone)
 * - vc: vertical cross point (percentage)
 * - r|s: round or square line ends
 */
public class CrossRenderer extends AbstractSymbolRenderer {

    public enum Shape {
        CROSS,      // cross - intersecting lines
        DOGBONE     // dogbone - dumbbell shape
    }

    public enum LineStyle {
        ROUND,
        SQUARE
    }

    private final Shape shape;
    private final double horizontalLineWidth;  // hs
    private final double verticalLineWidth;    // vs
    private final double horizontalCrossPoint; // hc
    private final double verticalCrossPoint;   // vc
    private final LineStyle lineStyle;
    private final double cornerRadius;         // ra - optional corner radius

    /**
     * Create a cross symbol.
     */
    public static CrossRenderer cross(double width, double height,
                                       double horizontalLineWidth, double verticalLineWidth,
                                       double horizontalCrossPoint, double verticalCrossPoint,
                                       boolean roundEnds) {
        return new CrossRenderer(width, height, Shape.CROSS,
                horizontalLineWidth, verticalLineWidth,
                horizontalCrossPoint, verticalCrossPoint,
                roundEnds ? LineStyle.ROUND : LineStyle.SQUARE, 0);
    }

    /**
     * Create a cross symbol with corner radius.
     */
    public static CrossRenderer crossWithRadius(double width, double height,
                                                 double horizontalLineWidth, double verticalLineWidth,
                                                 double horizontalCrossPoint, double verticalCrossPoint,
                                                 boolean roundEnds, double cornerRadius) {
        return new CrossRenderer(width, height, Shape.CROSS,
                horizontalLineWidth, verticalLineWidth,
                horizontalCrossPoint, verticalCrossPoint,
                roundEnds ? LineStyle.ROUND : LineStyle.SQUARE, cornerRadius);
    }

    /**
     * Create a dogbone symbol.
     */
    public static CrossRenderer dogbone(double width, double height,
                                         double horizontalLineWidth, double verticalLineWidth,
                                         double horizontalCrossPoint,
                                         boolean roundEnds) {
        return new CrossRenderer(width, height, Shape.DOGBONE,
                horizontalLineWidth, verticalLineWidth,
                horizontalCrossPoint, 50, // vc is always 50% for dogbone
                roundEnds ? LineStyle.ROUND : LineStyle.SQUARE, 0);
    }

    /**
     * Create a dogbone symbol with corner radius.
     */
    public static CrossRenderer dogboneWithRadius(double width, double height,
                                                   double horizontalLineWidth, double verticalLineWidth,
                                                   double horizontalCrossPoint,
                                                   boolean roundEnds, double cornerRadius) {
        return new CrossRenderer(width, height, Shape.DOGBONE,
                horizontalLineWidth, verticalLineWidth,
                horizontalCrossPoint, 50,
                roundEnds ? LineStyle.ROUND : LineStyle.SQUARE, cornerRadius);
    }

    private CrossRenderer(double width, double height, Shape shape,
                          double horizontalLineWidth, double verticalLineWidth,
                          double horizontalCrossPoint, double verticalCrossPoint,
                          LineStyle lineStyle, double cornerRadius) {
        super(width, height);
        this.shape = shape;
        this.horizontalLineWidth = horizontalLineWidth;
        this.verticalLineWidth = verticalLineWidth;
        this.horizontalCrossPoint = horizontalCrossPoint;
        this.verticalCrossPoint = verticalCrossPoint;
        this.lineStyle = lineStyle;
        this.cornerRadius = cornerRadius;
    }

    @Override
    public String render(double x, double y, double rotation, boolean mirror, double scale, String color) {
        double w = width * scale;
        double h = height * scale;
        double hs = horizontalLineWidth * scale;
        double vs = verticalLineWidth * scale;
        double hcp = horizontalCrossPoint * scale; // scaled bridge width for dogbone

        String transform = buildTransform(x, y, rotation, mirror, 1.0);

        return switch (shape) {
            case CROSS -> renderCross(x, y, w, h, hs, vs, color, transform);
            case DOGBONE -> renderDogbone(x, y, w, h, hs, vs, hcp, color, transform);
        };
    }

    /**
     * Cross: two intersecting orthogonal line segments.
     */
    private String renderCross(double x, double y, double w, double h,
                                double hs, double vs, String color, String transform) {
        double halfW = w / 2.0;
        double halfH = h / 2.0;
        double halfHs = hs / 2.0;  // half of horizontal line width
        double halfVs = vs / 2.0;  // half of vertical line width

        // Cross point position (as percentage)
        double cpX = x + (horizontalCrossPoint / 100.0 - 0.5) * w;
        double cpY = y + (verticalCrossPoint / 100.0 - 0.5) * h;

        StringBuilder path = new StringBuilder();

        // Draw as a complex polygon (12-sided cross)
        // Starting from top of vertical arm, going clockwise
        path.append("M ").append(fmt(cpX - halfVs)).append(" ").append(fmt(y - halfH)); // top-left of vertical arm
        path.append(" L ").append(fmt(cpX + halfVs)).append(" ").append(fmt(y - halfH)); // top-right of vertical arm
        path.append(" L ").append(fmt(cpX + halfVs)).append(" ").append(fmt(cpY - halfHs)); // inner corner
        path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(cpY - halfHs)); // right arm top
        path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(cpY + halfHs)); // right arm bottom
        path.append(" L ").append(fmt(cpX + halfVs)).append(" ").append(fmt(cpY + halfHs)); // inner corner
        path.append(" L ").append(fmt(cpX + halfVs)).append(" ").append(fmt(y + halfH)); // bottom-right of vertical arm
        path.append(" L ").append(fmt(cpX - halfVs)).append(" ").append(fmt(y + halfH)); // bottom-left of vertical arm
        path.append(" L ").append(fmt(cpX - halfVs)).append(" ").append(fmt(cpY + halfHs)); // inner corner
        path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(cpY + halfHs)); // left arm bottom
        path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(cpY - halfHs)); // left arm top
        path.append(" L ").append(fmt(cpX - halfVs)).append(" ").append(fmt(cpY - halfHs)); // inner corner
        path.append(" Z");

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\"%s/>", path, color, transform);
    }

    /**
     * Dogbone: dumbbell shape - two pads connected by a narrow bridge.
     */
    private String renderDogbone(double x, double y, double w, double h,
                                  double hs, double vs, double hcp, String color, String transform) {
        double halfW = w / 2.0;
        double halfH = h / 2.0;
        double halfHs = hs / 2.0;
        double halfVs = vs / 2.0;

        // Dogbone has pads at each end connected by a bridge
        // hcp is the scaled bridge width
        double bridgeWidth = hcp;
        double padWidth = (w - bridgeWidth) / 2.0;

        StringBuilder path = new StringBuilder();

        if (lineStyle == LineStyle.ROUND) {
            // Render with rounded ends (semicircles on the pads)
            double padRadius = halfH;

            // Left pad (semicircle + rect)
            path.append("M ").append(fmt(x - halfW + padRadius)).append(" ").append(fmt(y - halfH));
            path.append(" L ").append(fmt(x - halfW + padWidth)).append(" ").append(fmt(y - halfH)); // top of left pad
            path.append(" L ").append(fmt(x - halfW + padWidth)).append(" ").append(fmt(y - halfHs)); // bridge top-left
            path.append(" L ").append(fmt(x + halfW - padWidth)).append(" ").append(fmt(y - halfHs)); // bridge top-right
            path.append(" L ").append(fmt(x + halfW - padWidth)).append(" ").append(fmt(y - halfH)); // top of right pad
            path.append(" L ").append(fmt(x + halfW - padRadius)).append(" ").append(fmt(y - halfH));
            // Right semicircle
            path.append(" A ").append(fmt(padRadius)).append(" ").append(fmt(padRadius));
            path.append(" 0 0 1 ");
            path.append(fmt(x + halfW - padRadius)).append(" ").append(fmt(y + halfH));
            path.append(" L ").append(fmt(x + halfW - padWidth)).append(" ").append(fmt(y + halfH)); // bottom of right pad
            path.append(" L ").append(fmt(x + halfW - padWidth)).append(" ").append(fmt(y + halfHs)); // bridge bottom-right
            path.append(" L ").append(fmt(x - halfW + padWidth)).append(" ").append(fmt(y + halfHs)); // bridge bottom-left
            path.append(" L ").append(fmt(x - halfW + padWidth)).append(" ").append(fmt(y + halfH)); // bottom of left pad
            path.append(" L ").append(fmt(x - halfW + padRadius)).append(" ").append(fmt(y + halfH));
            // Left semicircle
            path.append(" A ").append(fmt(padRadius)).append(" ").append(fmt(padRadius));
            path.append(" 0 0 1 ");
            path.append(fmt(x - halfW + padRadius)).append(" ").append(fmt(y - halfH));
            path.append(" Z");
        } else {
            // Square ends
            path.append("M ").append(fmt(x - halfW)).append(" ").append(fmt(y - halfH)); // top-left
            path.append(" L ").append(fmt(x - halfW + padWidth)).append(" ").append(fmt(y - halfH)); // top of left pad
            path.append(" L ").append(fmt(x - halfW + padWidth)).append(" ").append(fmt(y - halfHs)); // bridge top-left
            path.append(" L ").append(fmt(x + halfW - padWidth)).append(" ").append(fmt(y - halfHs)); // bridge top-right
            path.append(" L ").append(fmt(x + halfW - padWidth)).append(" ").append(fmt(y - halfH)); // top of right pad
            path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y - halfH)); // top-right
            path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y + halfH)); // bottom-right
            path.append(" L ").append(fmt(x + halfW - padWidth)).append(" ").append(fmt(y + halfH)); // bottom of right pad
            path.append(" L ").append(fmt(x + halfW - padWidth)).append(" ").append(fmt(y + halfHs)); // bridge bottom-right
            path.append(" L ").append(fmt(x - halfW + padWidth)).append(" ").append(fmt(y + halfHs)); // bridge bottom-left
            path.append(" L ").append(fmt(x - halfW + padWidth)).append(" ").append(fmt(y + halfH)); // bottom of left pad
            path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y + halfH)); // bottom-left
            path.append(" Z");
        }

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\"%s/>", path, color, transform);
    }

    public Shape getShape() {
        return shape;
    }

    public LineStyle getLineStyle() {
        return lineStyle;
    }
}

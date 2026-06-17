package com.deltaproto.deltaodbpp.export.render;

import java.util.Locale;

/**
 * Renders donut (ring) symbols: round, square, and oval donuts.
 */
public class DonutRenderer extends AbstractSymbolRenderer {

    public enum Shape {
        ROUND,          // donut_r<od>x<id>
        SQUARE,         // donut_s<ow>x<iw>
        SQUARE_ROUND,   // donut_sr<od>x<id> - square outside, round inside
        OVAL,           // donut_o<ow>x<oh>x<lw>
        RECT            // donut_rc<ow>x<oh>x<lw>x<corner>
    }

    private final Shape shape;
    private final double innerDimension; // inner diameter or inner width
    private final double lineWidth;      // for oval donut
    private final double cornerRadius;   // for rect donut

    /**
     * Create a round donut (ring).
     */
    public static DonutRenderer round(double outerDiameter, double innerDiameter) {
        return new DonutRenderer(outerDiameter, outerDiameter, Shape.ROUND, innerDiameter, 0, 0);
    }

    /**
     * Create a round donut from mils.
     */
    public static DonutRenderer roundFromMils(double outerMils, double innerMils) {
        return round(outerMils / 1000.0, innerMils / 1000.0);
    }

    /**
     * Create a square donut (square ring).
     */
    public static DonutRenderer square(double outerSize, double innerSize) {
        return new DonutRenderer(outerSize, outerSize, Shape.SQUARE, innerSize, 0, 0);
    }

    /**
     * Create a square donut from mils.
     */
    public static DonutRenderer squareFromMils(double outerMils, double innerMils) {
        return square(outerMils / 1000.0, innerMils / 1000.0);
    }

    /**
     * Create a square-round donut (square outside, round inside).
     */
    public static DonutRenderer squareRound(double outerSize, double innerDiameter) {
        return new DonutRenderer(outerSize, outerSize, Shape.SQUARE_ROUND, innerDiameter, 0, 0);
    }

    /**
     * Create a square-round donut from mils.
     */
    public static DonutRenderer squareRoundFromMils(double outerMils, double innerMils) {
        return squareRound(outerMils / 1000.0, innerMils / 1000.0);
    }

    /**
     * Create an oval donut (oval ring).
     * lineWidth is the width of the ring itself.
     */
    public static DonutRenderer oval(double outerWidth, double outerHeight, double lineWidth) {
        return new DonutRenderer(outerWidth, outerHeight, Shape.OVAL, 0, lineWidth, 0);
    }

    /**
     * Create an oval donut from mils.
     */
    public static DonutRenderer ovalFromMils(double outerWidthMils, double outerHeightMils, double lineWidthMils) {
        return oval(outerWidthMils / 1000.0, outerHeightMils / 1000.0, lineWidthMils / 1000.0);
    }

    /**
     * Create a rounded rectangle donut.
     */
    public static DonutRenderer rect(double outerWidth, double outerHeight, double lineWidth, double cornerRadius) {
        return new DonutRenderer(outerWidth, outerHeight, Shape.RECT, 0, lineWidth, cornerRadius);
    }

    /**
     * Create a rounded rectangle donut from mils.
     */
    public static DonutRenderer rectFromMils(double outerWidthMils, double outerHeightMils,
                                              double lineWidthMils, double cornerMils) {
        return rect(outerWidthMils / 1000.0, outerHeightMils / 1000.0,
                lineWidthMils / 1000.0, cornerMils / 1000.0);
    }

    private DonutRenderer(double width, double height, Shape shape,
                          double innerDimension, double lineWidth, double cornerRadius) {
        super(width, height);
        this.shape = shape;
        this.innerDimension = innerDimension;
        this.lineWidth = lineWidth;
        this.cornerRadius = cornerRadius;
    }

    @Override
    public String render(double x, double y, double rotation, boolean mirror, double scale, String color) {
        double w = width * scale;
        double h = height * scale;
        double inner = innerDimension * scale;
        double lw = lineWidth * scale;
        double cr = cornerRadius * scale;

        String transform = buildTransform(x, y, rotation, mirror, 1.0);

        return switch (shape) {
            case ROUND -> renderRoundDonut(x, y, w, inner, color, transform);
            case SQUARE -> renderSquareDonut(x, y, w, inner, color, transform);
            case SQUARE_ROUND -> renderSquareRoundDonut(x, y, w, inner, color, transform);
            case OVAL -> renderOvalDonut(x, y, w, h, lw, color, transform);
            case RECT -> renderRectDonut(x, y, w, h, lw, cr, color, transform);
        };
    }

    /**
     * Round donut: two concentric circles with fill-rule evenodd.
     */
    private String renderRoundDonut(double x, double y, double outerD, double innerD,
                                     String color, String transform) {
        double outerR = outerD / 2.0;
        double innerR = innerD / 2.0;

        // Use two circles with fill-rule: evenodd to create the ring
        // Outer circle clockwise, inner circle counter-clockwise
        StringBuilder path = new StringBuilder();

        // Outer circle (two arcs to complete the circle)
        path.append("M ").append(fmt(x + outerR)).append(" ").append(fmt(y));
        path.append(" A ").append(fmt(outerR)).append(" ").append(fmt(outerR));
        path.append(" 0 1 1 ");
        path.append(fmt(x - outerR)).append(" ").append(fmt(y));
        path.append(" A ").append(fmt(outerR)).append(" ").append(fmt(outerR));
        path.append(" 0 1 1 ");
        path.append(fmt(x + outerR)).append(" ").append(fmt(y));

        // Inner circle (counter-clockwise - opposite direction)
        path.append(" M ").append(fmt(x + innerR)).append(" ").append(fmt(y));
        path.append(" A ").append(fmt(innerR)).append(" ").append(fmt(innerR));
        path.append(" 0 1 0 ");
        path.append(fmt(x - innerR)).append(" ").append(fmt(y));
        path.append(" A ").append(fmt(innerR)).append(" ").append(fmt(innerR));
        path.append(" 0 1 0 ");
        path.append(fmt(x + innerR)).append(" ").append(fmt(y));

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\" fill-rule=\"evenodd\"%s/>",
                path, color, transform);
    }

    /**
     * Square donut: two concentric squares.
     */
    private String renderSquareDonut(double x, double y, double outerSize, double innerSize,
                                      String color, String transform) {
        double halfOuter = outerSize / 2.0;
        double halfInner = innerSize / 2.0;

        StringBuilder path = new StringBuilder();

        // Outer square (clockwise)
        path.append("M ").append(fmt(x - halfOuter)).append(" ").append(fmt(y - halfOuter));
        path.append(" L ").append(fmt(x + halfOuter)).append(" ").append(fmt(y - halfOuter));
        path.append(" L ").append(fmt(x + halfOuter)).append(" ").append(fmt(y + halfOuter));
        path.append(" L ").append(fmt(x - halfOuter)).append(" ").append(fmt(y + halfOuter));
        path.append(" Z");

        // Inner square (counter-clockwise)
        path.append(" M ").append(fmt(x - halfInner)).append(" ").append(fmt(y - halfInner));
        path.append(" L ").append(fmt(x - halfInner)).append(" ").append(fmt(y + halfInner));
        path.append(" L ").append(fmt(x + halfInner)).append(" ").append(fmt(y + halfInner));
        path.append(" L ").append(fmt(x + halfInner)).append(" ").append(fmt(y - halfInner));
        path.append(" Z");

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\" fill-rule=\"evenodd\"%s/>",
                path, color, transform);
    }

    /**
     * Square-round donut: square outside, round inside.
     */
    private String renderSquareRoundDonut(double x, double y, double outerSize, double innerD,
                                          String color, String transform) {
        double halfOuter = outerSize / 2.0;
        double innerR = innerD / 2.0;

        StringBuilder path = new StringBuilder();

        // Outer square (clockwise)
        path.append("M ").append(fmt(x - halfOuter)).append(" ").append(fmt(y - halfOuter));
        path.append(" L ").append(fmt(x + halfOuter)).append(" ").append(fmt(y - halfOuter));
        path.append(" L ").append(fmt(x + halfOuter)).append(" ").append(fmt(y + halfOuter));
        path.append(" L ").append(fmt(x - halfOuter)).append(" ").append(fmt(y + halfOuter));
        path.append(" Z");

        // Inner circle (counter-clockwise)
        path.append(" M ").append(fmt(x + innerR)).append(" ").append(fmt(y));
        path.append(" A ").append(fmt(innerR)).append(" ").append(fmt(innerR));
        path.append(" 0 1 0 ");
        path.append(fmt(x - innerR)).append(" ").append(fmt(y));
        path.append(" A ").append(fmt(innerR)).append(" ").append(fmt(innerR));
        path.append(" 0 1 0 ");
        path.append(fmt(x + innerR)).append(" ").append(fmt(y));

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\" fill-rule=\"evenodd\"%s/>",
                path, color, transform);
    }

    /**
     * Oval donut: oval ring with specified line width.
     */
    private String renderOvalDonut(double x, double y, double outerW, double outerH, double lw,
                                    String color, String transform) {
        double halfOuterW = outerW / 2.0;
        double halfOuterH = outerH / 2.0;
        double halfInnerW = halfOuterW - lw;
        double halfInnerH = halfOuterH - lw;

        // Ensure inner dimensions are positive
        halfInnerW = Math.max(0.001, halfInnerW);
        halfInnerH = Math.max(0.001, halfInnerH);

        StringBuilder path = new StringBuilder();

        // Outer ellipse
        path.append("M ").append(fmt(x + halfOuterW)).append(" ").append(fmt(y));
        path.append(" A ").append(fmt(halfOuterW)).append(" ").append(fmt(halfOuterH));
        path.append(" 0 1 1 ");
        path.append(fmt(x - halfOuterW)).append(" ").append(fmt(y));
        path.append(" A ").append(fmt(halfOuterW)).append(" ").append(fmt(halfOuterH));
        path.append(" 0 1 1 ");
        path.append(fmt(x + halfOuterW)).append(" ").append(fmt(y));

        // Inner ellipse (opposite direction)
        path.append(" M ").append(fmt(x + halfInnerW)).append(" ").append(fmt(y));
        path.append(" A ").append(fmt(halfInnerW)).append(" ").append(fmt(halfInnerH));
        path.append(" 0 1 0 ");
        path.append(fmt(x - halfInnerW)).append(" ").append(fmt(y));
        path.append(" A ").append(fmt(halfInnerW)).append(" ").append(fmt(halfInnerH));
        path.append(" 0 1 0 ");
        path.append(fmt(x + halfInnerW)).append(" ").append(fmt(y));

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\" fill-rule=\"evenodd\"%s/>",
                path, color, transform);
    }

    /**
     * Rounded rectangle donut.
     */
    private String renderRectDonut(double x, double y, double outerW, double outerH,
                                    double lw, double cr, String color, String transform) {
        double halfOuterW = outerW / 2.0;
        double halfOuterH = outerH / 2.0;
        double halfInnerW = halfOuterW - lw;
        double halfInnerH = halfOuterH - lw;
        double innerCr = Math.max(0, cr - lw);

        // Outer rounded rect
        StringBuilder path = new StringBuilder();
        appendRoundedRect(path, x, y, halfOuterW, halfOuterH, cr, true);

        // Inner rounded rect (counter-clockwise)
        if (halfInnerW > 0 && halfInnerH > 0) {
            appendRoundedRect(path, x, y, halfInnerW, halfInnerH, innerCr, false);
        }

        return String.format(Locale.US, "<path d=\"%s\" fill=\"%s\" fill-rule=\"evenodd\"%s/>",
                path, color, transform);
    }

    /**
     * Append a rounded rectangle path.
     */
    private void appendRoundedRect(StringBuilder path, double x, double y,
                                   double halfW, double halfH, double r, boolean clockwise) {
        r = Math.min(r, Math.min(halfW, halfH));

        if (clockwise) {
            // Top-left corner start, go clockwise
            path.append(" M ").append(fmt(x - halfW + r)).append(" ").append(fmt(y - halfH));
            path.append(" L ").append(fmt(x + halfW - r)).append(" ").append(fmt(y - halfH));
            path.append(" A ").append(fmt(r)).append(" ").append(fmt(r)).append(" 0 0 1 ");
            path.append(fmt(x + halfW)).append(" ").append(fmt(y - halfH + r));
            path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y + halfH - r));
            path.append(" A ").append(fmt(r)).append(" ").append(fmt(r)).append(" 0 0 1 ");
            path.append(fmt(x + halfW - r)).append(" ").append(fmt(y + halfH));
            path.append(" L ").append(fmt(x - halfW + r)).append(" ").append(fmt(y + halfH));
            path.append(" A ").append(fmt(r)).append(" ").append(fmt(r)).append(" 0 0 1 ");
            path.append(fmt(x - halfW)).append(" ").append(fmt(y + halfH - r));
            path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y - halfH + r));
            path.append(" A ").append(fmt(r)).append(" ").append(fmt(r)).append(" 0 0 1 ");
            path.append(fmt(x - halfW + r)).append(" ").append(fmt(y - halfH));
            path.append(" Z");
        } else {
            // Counter-clockwise for inner hole
            path.append(" M ").append(fmt(x - halfW + r)).append(" ").append(fmt(y - halfH));
            path.append(" A ").append(fmt(r)).append(" ").append(fmt(r)).append(" 0 0 0 ");
            path.append(fmt(x - halfW)).append(" ").append(fmt(y - halfH + r));
            path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y + halfH - r));
            path.append(" A ").append(fmt(r)).append(" ").append(fmt(r)).append(" 0 0 0 ");
            path.append(fmt(x - halfW + r)).append(" ").append(fmt(y + halfH));
            path.append(" L ").append(fmt(x + halfW - r)).append(" ").append(fmt(y + halfH));
            path.append(" A ").append(fmt(r)).append(" ").append(fmt(r)).append(" 0 0 0 ");
            path.append(fmt(x + halfW)).append(" ").append(fmt(y + halfH - r));
            path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y - halfH + r));
            path.append(" A ").append(fmt(r)).append(" ").append(fmt(r)).append(" 0 0 0 ");
            path.append(fmt(x + halfW - r)).append(" ").append(fmt(y - halfH));
            path.append(" Z");
        }
    }

    public Shape getShape() {
        return shape;
    }

    public double getInnerDimension() {
        return innerDimension;
    }

    public double getLineWidth() {
        return lineWidth;
    }
}

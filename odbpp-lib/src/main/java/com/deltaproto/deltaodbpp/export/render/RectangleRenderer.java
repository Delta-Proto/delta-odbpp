package com.deltaproto.deltaodbpp.export.render;

/**
 * Renders rectangle symbols including rounded and chamfered variants.
 * Symbol formats:
 * - rect<w>x<h>: Simple rectangle (e.g., rect100x50)
 * - rc<w>x<h>x<r>: Rounded corners rectangle (e.g., rc100x50x10)
 * - ch<w>x<h>x<c>: Chamfered corners rectangle (e.g., ch100x50x10)
 */
public class RectangleRenderer extends AbstractSymbolRenderer {

    public enum Variant {
        SIMPLE,
        ROUNDED,
        CHAMFERED
    }

    private final Variant variant;
    private final double cornerParam; // radius for rounded, chamfer size for chamfered

    /**
     * Create a simple rectangle renderer.
     */
    public RectangleRenderer(double width, double height) {
        super(width, height);
        this.variant = Variant.SIMPLE;
        this.cornerParam = 0;
    }

    /**
     * Create a rectangle with corner treatment.
     */
    public RectangleRenderer(double width, double height, Variant variant, double cornerParam) {
        super(width, height);
        this.variant = variant;
        this.cornerParam = cornerParam;
    }

    /**
     * Create simple rectangle from mils.
     */
    public static RectangleRenderer fromMils(double widthMils, double heightMils) {
        return new RectangleRenderer(widthMils / 1000.0, heightMils / 1000.0);
    }

    /**
     * Create rounded rectangle from mils.
     */
    public static RectangleRenderer roundedFromMils(double widthMils, double heightMils, double radiusMils) {
        return new RectangleRenderer(widthMils / 1000.0, heightMils / 1000.0,
                Variant.ROUNDED, radiusMils / 1000.0);
    }

    /**
     * Create chamfered rectangle from mils.
     */
    public static RectangleRenderer chamferedFromMils(double widthMils, double heightMils, double chamferMils) {
        return new RectangleRenderer(widthMils / 1000.0, heightMils / 1000.0,
                Variant.CHAMFERED, chamferMils / 1000.0);
    }

    @Override
    public String render(double x, double y, double rotation, boolean mirror, double scale, String color) {
        double w = width * scale;
        double h = height * scale;
        double c = cornerParam * scale;

        double halfW = w / 2.0;
        double halfH = h / 2.0;

        String transform = buildTransform(x, y, rotation, mirror, 1.0);

        switch (variant) {
            case ROUNDED:
                return renderRounded(x, y, w, h, c, color, transform);
            case CHAMFERED:
                return renderChamfered(x, y, halfW, halfH, c, color, transform);
            default:
                return renderSimple(x, y, halfW, halfH, w, h, color, transform);
        }
    }

    private String renderSimple(double x, double y, double halfW, double halfH,
                                double w, double h, String color, String transform) {
        return String.format("<rect x=\"%s\" y=\"%s\" width=\"%s\" height=\"%s\" fill=\"%s\"%s/>",
                fmt(x - halfW), fmt(y - halfH), fmt(w), fmt(h), color, transform);
    }

    private String renderRounded(double x, double y, double w, double h,
                                 double radius, String color, String transform) {
        // SVG rect with rx/ry for rounded corners
        double halfW = w / 2.0;
        double halfH = h / 2.0;
        // Cap radius to half of smaller dimension
        double r = Math.min(radius, Math.min(halfW, halfH));

        return String.format("<rect x=\"%s\" y=\"%s\" width=\"%s\" height=\"%s\" rx=\"%s\" ry=\"%s\" fill=\"%s\"%s/>",
                fmt(x - halfW), fmt(y - halfH), fmt(w), fmt(h), fmt(r), fmt(r), color, transform);
    }

    private String renderChamfered(double x, double y, double halfW, double halfH,
                                   double chamfer, String color, String transform) {
        // Chamfered rectangle as polygon path
        // Start at top-left + chamfer, go clockwise
        double c = Math.min(chamfer, Math.min(halfW, halfH));

        StringBuilder path = new StringBuilder();
        path.append("M ").append(fmt(x - halfW + c)).append(" ").append(fmt(y - halfH));
        path.append(" L ").append(fmt(x + halfW - c)).append(" ").append(fmt(y - halfH)); // top edge
        path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y - halfH + c)); // top-right chamfer
        path.append(" L ").append(fmt(x + halfW)).append(" ").append(fmt(y + halfH - c)); // right edge
        path.append(" L ").append(fmt(x + halfW - c)).append(" ").append(fmt(y + halfH)); // bottom-right chamfer
        path.append(" L ").append(fmt(x - halfW + c)).append(" ").append(fmt(y + halfH)); // bottom edge
        path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y + halfH - c)); // bottom-left chamfer
        path.append(" L ").append(fmt(x - halfW)).append(" ").append(fmt(y - halfH + c)); // left edge
        path.append(" Z");

        return String.format("<path d=\"%s\" fill=\"%s\"%s/>", path, color, transform);
    }

    public Variant getVariant() {
        return variant;
    }

    public double getCornerParam() {
        return cornerParam;
    }
}

package com.deltaproto.deltaodbpp.export.render;

/**
 * Renders oval (oblong) and ellipse symbols.
 * Symbol formats:
 * - oval<w>x<h>: Oblong/stadium shape with semicircle ends (e.g., oval80x40)
 * - el<w>x<h>: True ellipse (e.g., el80x50)
 */
public class OvalRenderer extends AbstractSymbolRenderer {

    private final boolean isEllipse;

    /**
     * Create an oval (oblong) renderer.
     * An oval is a rectangle with semicircular ends (stadium shape).
     */
    public OvalRenderer(double width, double height) {
        this(width, height, false);
    }

    /**
     * Create an oval or ellipse renderer.
     *
     * @param width     Width in ODB++ units
     * @param height    Height in ODB++ units
     * @param isEllipse true for true ellipse, false for stadium/oblong
     */
    public OvalRenderer(double width, double height, boolean isEllipse) {
        super(width, height);
        this.isEllipse = isEllipse;
    }

    /**
     * Create oval from mils.
     */
    public static OvalRenderer fromMils(double widthMils, double heightMils) {
        return new OvalRenderer(widthMils / 1000.0, heightMils / 1000.0, false);
    }

    /**
     * Create ellipse from mils.
     */
    public static OvalRenderer ellipseFromMils(double widthMils, double heightMils) {
        return new OvalRenderer(widthMils / 1000.0, heightMils / 1000.0, true);
    }

    @Override
    public String render(double x, double y, double rotation, boolean mirror, double scale, String color) {
        double w = width * scale;
        double h = height * scale;

        String transform = buildTransform(x, y, rotation, mirror, 1.0);

        if (isEllipse) {
            return renderEllipse(x, y, w, h, color, transform);
        } else {
            return renderOval(x, y, w, h, color, transform);
        }
    }

    /**
     * Render a true ellipse.
     */
    private String renderEllipse(double x, double y, double w, double h,
                                 String color, String transform) {
        double rx = w / 2.0;
        double ry = h / 2.0;
        return String.format("<ellipse cx=\"%s\" cy=\"%s\" rx=\"%s\" ry=\"%s\" fill=\"%s\"%s/>",
                fmt(x), fmt(y), fmt(rx), fmt(ry), color, transform);
    }

    /**
     * Render an oval (oblong/stadium shape).
     * This is a rectangle with semicircular ends.
     * The ends are on the shorter dimension.
     */
    private String renderOval(double x, double y, double w, double h,
                              String color, String transform) {
        // Determine orientation
        boolean horizontal = w >= h;

        if (horizontal) {
            // Horizontal oval: semicircles on left and right
            double radius = h / 2.0;
            double rectWidth = w - h; // Width of central rectangle

            if (rectWidth <= 0) {
                // Degenerate to circle
                return String.format("<circle cx=\"%s\" cy=\"%s\" r=\"%s\" fill=\"%s\"%s/>",
                        fmt(x), fmt(y), fmt(radius), color, transform);
            }

            // Build path: left semicircle, top edge, right semicircle, bottom edge
            double left = x - w / 2.0;
            double right = x + w / 2.0;
            double top = y - h / 2.0;
            double bottom = y + h / 2.0;
            double leftCenter = left + radius;
            double rightCenter = right - radius;

            StringBuilder path = new StringBuilder();
            // Start at top of left semicircle
            path.append("M ").append(fmt(leftCenter)).append(" ").append(fmt(top));
            // Top edge to right
            path.append(" L ").append(fmt(rightCenter)).append(" ").append(fmt(top));
            // Right semicircle (clockwise = sweep-flag 1)
            path.append(" A ").append(fmt(radius)).append(" ").append(fmt(radius));
            path.append(" 0 0 1 ");
            path.append(fmt(rightCenter)).append(" ").append(fmt(bottom));
            // Bottom edge to left
            path.append(" L ").append(fmt(leftCenter)).append(" ").append(fmt(bottom));
            // Left semicircle
            path.append(" A ").append(fmt(radius)).append(" ").append(fmt(radius));
            path.append(" 0 0 1 ");
            path.append(fmt(leftCenter)).append(" ").append(fmt(top));
            path.append(" Z");

            return String.format("<path d=\"%s\" fill=\"%s\"%s/>", path, color, transform);
        } else {
            // Vertical oval: semicircles on top and bottom
            double radius = w / 2.0;
            double rectHeight = h - w;

            if (rectHeight <= 0) {
                // Degenerate to circle
                return String.format("<circle cx=\"%s\" cy=\"%s\" r=\"%s\" fill=\"%s\"%s/>",
                        fmt(x), fmt(y), fmt(radius), color, transform);
            }

            double left = x - w / 2.0;
            double right = x + w / 2.0;
            double top = y - h / 2.0;
            double bottom = y + h / 2.0;
            double topCenter = top + radius;
            double bottomCenter = bottom - radius;

            StringBuilder path = new StringBuilder();
            // Start at left of top semicircle
            path.append("M ").append(fmt(left)).append(" ").append(fmt(topCenter));
            // Top semicircle
            path.append(" A ").append(fmt(radius)).append(" ").append(fmt(radius));
            path.append(" 0 0 1 ");
            path.append(fmt(right)).append(" ").append(fmt(topCenter));
            // Right edge down
            path.append(" L ").append(fmt(right)).append(" ").append(fmt(bottomCenter));
            // Bottom semicircle
            path.append(" A ").append(fmt(radius)).append(" ").append(fmt(radius));
            path.append(" 0 0 1 ");
            path.append(fmt(left)).append(" ").append(fmt(bottomCenter));
            // Left edge up
            path.append(" L ").append(fmt(left)).append(" ").append(fmt(topCenter));
            path.append(" Z");

            return String.format("<path d=\"%s\" fill=\"%s\"%s/>", path, color, transform);
        }
    }

    public boolean isEllipse() {
        return isEllipse;
    }
}

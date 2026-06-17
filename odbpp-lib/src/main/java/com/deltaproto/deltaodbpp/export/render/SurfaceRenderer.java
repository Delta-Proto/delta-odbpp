package com.deltaproto.deltaodbpp.export.render;

import com.deltaproto.deltaodbpp.model.ContourPolygon;
import com.deltaproto.deltaodbpp.model.Polarity;
import com.deltaproto.deltaodbpp.model.Surface;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders ODB++ surfaces (contour polygons) to SVG.
 *
 * A surface consists of one or more contour polygons, where:
 * - Islands are solid areas (clockwise winding)
 * - Holes cut out from islands (counter-clockwise winding)
 *
 * The natural containment order follows these rules:
 * - Islands and holes are ordered by containment
 * - Islands precede holes that are contained in them
 * - Holes precede islands that are contained in them
 *
 * This renderer properly handles the fill-rule to ensure holes
 * are correctly cut out from their containing islands.
 */
public class SurfaceRenderer extends AbstractSymbolRenderer {

    /**
     * Represents a polygon part - either a line segment or arc.
     */
    public static class PathPart {
        public enum Type { LINE, ARC }

        private final Type type;
        private final double endX;
        private final double endY;
        private final double centerX; // For arcs only
        private final double centerY; // For arcs only
        private final boolean clockwise; // For arcs only

        public static PathPart line(double endX, double endY) {
            return new PathPart(Type.LINE, endX, endY, 0, 0, false);
        }

        public static PathPart arc(double endX, double endY, double centerX, double centerY, boolean clockwise) {
            return new PathPart(Type.ARC, endX, endY, centerX, centerY, clockwise);
        }

        private PathPart(Type type, double endX, double endY, double centerX, double centerY, boolean clockwise) {
            this.type = type;
            this.endX = endX;
            this.endY = endY;
            this.centerX = centerX;
            this.centerY = centerY;
            this.clockwise = clockwise;
        }

        public Type getType() { return type; }
        public double getEndX() { return endX; }
        public double getEndY() { return endY; }
        public double getCenterX() { return centerX; }
        public double getCenterY() { return centerY; }
        public boolean isClockwise() { return clockwise; }
    }

    /**
     * Represents a single polygon (island or hole).
     */
    public static class Polygon {
        private final ContourPolygon.Type type;
        private final double startX;
        private final double startY;
        private final List<PathPart> parts;

        public Polygon(ContourPolygon.Type type, double startX, double startY) {
            this.type = type;
            this.startX = startX;
            this.startY = startY;
            this.parts = new ArrayList<>();
        }

        public Polygon addLine(double endX, double endY) {
            parts.add(PathPart.line(endX, endY));
            return this;
        }

        public Polygon addArc(double endX, double endY, double centerX, double centerY, boolean clockwise) {
            parts.add(PathPart.arc(endX, endY, centerX, centerY, clockwise));
            return this;
        }

        public ContourPolygon.Type getType() { return type; }
        public double getStartX() { return startX; }
        public double getStartY() { return startY; }
        public List<PathPart> getParts() { return parts; }
    }

    private final List<Polygon> polygons;
    private final Polarity polarity;

    /**
     * Create a surface renderer from an ODB++ Surface model.
     */
    public static SurfaceRenderer fromSurface(Surface surface) {
        List<Polygon> polygons = new ArrayList<>();

        for (ContourPolygon cp : surface.getPolygons()) {
            Polygon polygon = new Polygon(cp.getType(), cp.getXStart(), cp.getYStart());

            for (ContourPolygon.PolygonPart part : cp.getPolygonParts()) {
                if (part.getType() == ContourPolygon.PolygonPart.Type.SEGMENT) {
                    polygon.addLine(part.getEndX(), part.getEndY());
                } else {
                    polygon.addArc(part.getEndX(), part.getEndY(),
                                   part.getXCenter(), part.getYCenter(),
                                   part.isClockwise());
                }
            }

            polygons.add(polygon);
        }

        return new SurfaceRenderer(polygons, surface.getPolarity());
    }

    /**
     * Create a surface renderer builder for programmatic construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating surfaces programmatically.
     */
    public static class Builder {
        private final List<Polygon> polygons = new ArrayList<>();
        private Polarity polarity = Polarity.POSITIVE;

        public Builder polarity(Polarity polarity) {
            this.polarity = polarity;
            return this;
        }

        public Builder addIsland(double startX, double startY, PolygonBuilder polygonBuilder) {
            Polygon polygon = new Polygon(ContourPolygon.Type.ISLAND, startX, startY);
            polygonBuilder.build(polygon);
            polygons.add(polygon);
            return this;
        }

        public Builder addHole(double startX, double startY, PolygonBuilder polygonBuilder) {
            Polygon polygon = new Polygon(ContourPolygon.Type.HOLE, startX, startY);
            polygonBuilder.build(polygon);
            polygons.add(polygon);
            return this;
        }

        public SurfaceRenderer build() {
            return new SurfaceRenderer(polygons, polarity);
        }
    }

    @FunctionalInterface
    public interface PolygonBuilder {
        void build(Polygon polygon);
    }

    private SurfaceRenderer(List<Polygon> polygons, Polarity polarity) {
        super(calculateWidth(polygons), calculateHeight(polygons));
        this.polygons = polygons;
        this.polarity = polarity;
    }

    private static double calculateWidth(List<Polygon> polygons) {
        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        for (Polygon p : polygons) {
            minX = Math.min(minX, p.startX);
            maxX = Math.max(maxX, p.startX);
            for (PathPart part : p.parts) {
                minX = Math.min(minX, part.endX);
                maxX = Math.max(maxX, part.endX);
            }
        }
        return maxX - minX;
    }

    private static double calculateHeight(List<Polygon> polygons) {
        double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
        for (Polygon p : polygons) {
            minY = Math.min(minY, p.startY);
            maxY = Math.max(maxY, p.startY);
            for (PathPart part : p.parts) {
                minY = Math.min(minY, part.endY);
                maxY = Math.max(maxY, part.endY);
            }
        }
        return maxY - minY;
    }

    @Override
    public String render(double x, double y, double rotation, boolean mirror, double scale, String color) {
        if (polygons.isEmpty()) {
            return "";
        }

        StringBuilder pathData = new StringBuilder();

        // Combine all polygons into a single path
        // Using evenodd fill-rule to properly handle holes
        for (Polygon polygon : polygons) {
            double sx = (polygon.startX + x) * scale;
            double sy = (polygon.startY + y) * scale;

            pathData.append(String.format(Locale.US, "M %.4f %.4f", sx, sy));

            double prevX = polygon.startX;
            double prevY = polygon.startY;

            for (PathPart part : polygon.parts) {
                double ex = (part.endX + x) * scale;
                double ey = (part.endY + y) * scale;

                if (part.type == PathPart.Type.LINE) {
                    pathData.append(String.format(Locale.US, " L %.4f %.4f", ex, ey));
                } else {
                    // Arc
                    double cx = (part.centerX + x) * scale;
                    double cy = (part.centerY + y) * scale;

                    // Calculate radius from center to end point
                    double radius = Math.sqrt(
                        Math.pow((part.endX - part.centerX) * scale, 2) +
                        Math.pow((part.endY - part.centerY) * scale, 2));

                    // Determine sweep direction
                    // In SVG, sweep-flag=1 means clockwise, sweep-flag=0 means counter-clockwise
                    // But SVG Y-axis is inverted compared to ODB++, so we need to flip
                    int sweepFlag = part.clockwise ? 0 : 1;

                    // Calculate if it's a large arc (> 180 degrees)
                    double startAngle = Math.atan2(prevY - part.centerY, prevX - part.centerX);
                    double endAngle = Math.atan2(part.endY - part.centerY, part.endX - part.centerX);
                    double angleDiff = endAngle - startAngle;
                    if (angleDiff < 0) angleDiff += 2 * Math.PI;
                    if (!part.clockwise && angleDiff > Math.PI) {
                        angleDiff = 2 * Math.PI - angleDiff;
                    }
                    int largeArcFlag = angleDiff > Math.PI ? 1 : 0;

                    pathData.append(String.format(Locale.US, " A %.4f %.4f 0 %d %d %.4f %.4f",
                        radius, radius, largeArcFlag, sweepFlag, ex, ey));
                }

                prevX = part.endX;
                prevY = part.endY;
            }

            pathData.append(" Z ");
        }

        String transform = buildTransform(x, y, rotation, mirror, 1.0);

        // Use evenodd fill-rule to properly handle holes
        return String.format(Locale.US,
            "<path d=\"%s\" fill=\"%s\" fill-rule=\"evenodd\" stroke=\"none\"%s/>",
            pathData.toString().trim(), color, transform);
    }

    /**
     * Render with a stroke outline in addition to fill.
     */
    public String renderWithStroke(double x, double y, double rotation, boolean mirror,
                                    double scale, String fillColor, String strokeColor, double strokeWidth) {
        if (polygons.isEmpty()) {
            return "";
        }

        StringBuilder pathData = new StringBuilder();

        for (Polygon polygon : polygons) {
            double sx = (polygon.startX + x) * scale;
            double sy = (polygon.startY + y) * scale;

            pathData.append(String.format(Locale.US, "M %.4f %.4f", sx, sy));

            for (PathPart part : polygon.parts) {
                double ex = (part.endX + x) * scale;
                double ey = (part.endY + y) * scale;

                if (part.type == PathPart.Type.LINE) {
                    pathData.append(String.format(Locale.US, " L %.4f %.4f", ex, ey));
                } else {
                    double radius = Math.sqrt(
                        Math.pow((part.endX - part.centerX) * scale, 2) +
                        Math.pow((part.endY - part.centerY) * scale, 2));
                    int sweepFlag = part.clockwise ? 0 : 1;
                    pathData.append(String.format(Locale.US, " A %.4f %.4f 0 0 %d %.4f %.4f",
                        radius, radius, sweepFlag, ex, ey));
                }
            }

            pathData.append(" Z ");
        }

        String transform = buildTransform(x, y, rotation, mirror, 1.0);

        return String.format(Locale.US,
            "<path d=\"%s\" fill=\"%s\" fill-rule=\"evenodd\" stroke=\"%s\" stroke-width=\"%.4f\"%s/>",
            pathData.toString().trim(), fillColor, strokeColor, strokeWidth, transform);
    }

    // === Factory methods for common surface shapes ===

    /**
     * Create a simple rectangular surface.
     */
    public static SurfaceRenderer rectangle(double width, double height) {
        double hw = width / 2;
        double hh = height / 2;

        return builder()
            .addIsland(-hw, -hh, p -> p
                .addLine(hw, -hh)
                .addLine(hw, hh)
                .addLine(-hw, hh)
                .addLine(-hw, -hh))
            .build();
    }

    /**
     * Create a rectangular surface with a rectangular hole.
     */
    public static SurfaceRenderer rectangleWithHole(double outerWidth, double outerHeight,
                                                     double innerWidth, double innerHeight) {
        double ow = outerWidth / 2;
        double oh = outerHeight / 2;
        double iw = innerWidth / 2;
        double ih = innerHeight / 2;

        return builder()
            // Outer island (clockwise)
            .addIsland(-ow, -oh, p -> p
                .addLine(ow, -oh)
                .addLine(ow, oh)
                .addLine(-ow, oh)
                .addLine(-ow, -oh))
            // Inner hole (counter-clockwise)
            .addHole(-iw, -ih, p -> p
                .addLine(-iw, ih)
                .addLine(iw, ih)
                .addLine(iw, -ih)
                .addLine(-iw, -ih))
            .build();
    }

    /**
     * Create a circular surface (approximated with arcs).
     */
    public static SurfaceRenderer circle(double radius) {
        return builder()
            .addIsland(radius, 0, p -> p
                .addArc(-radius, 0, 0, 0, true)
                .addArc(radius, 0, 0, 0, true))
            .build();
    }

    /**
     * Create a circular surface with a circular hole (donut shape).
     */
    public static SurfaceRenderer circleWithHole(double outerRadius, double innerRadius) {
        return builder()
            // Outer island (clockwise)
            .addIsland(outerRadius, 0, p -> p
                .addArc(-outerRadius, 0, 0, 0, true)
                .addArc(outerRadius, 0, 0, 0, true))
            // Inner hole (counter-clockwise)
            .addHole(innerRadius, 0, p -> p
                .addArc(-innerRadius, 0, 0, 0, false)
                .addArc(innerRadius, 0, 0, 0, false))
            .build();
    }

    /**
     * Create a nested surface: Island A with Hole B containing Island C.
     * This demonstrates the natural containment order.
     */
    public static SurfaceRenderer nestedExample(double outerRadius, double middleRadius, double innerRadius) {
        return builder()
            // Outermost island A (clockwise)
            .addIsland(outerRadius, 0, p -> p
                .addArc(-outerRadius, 0, 0, 0, true)
                .addArc(outerRadius, 0, 0, 0, true))
            // Hole B inside A (counter-clockwise)
            .addHole(middleRadius, 0, p -> p
                .addArc(-middleRadius, 0, 0, 0, false)
                .addArc(middleRadius, 0, 0, 0, false))
            // Island C inside B (clockwise)
            .addIsland(innerRadius, 0, p -> p
                .addArc(-innerRadius, 0, 0, 0, true)
                .addArc(innerRadius, 0, 0, 0, true))
            .build();
    }

    /**
     * Create an L-shaped surface.
     */
    public static SurfaceRenderer lShape(double width, double height, double cutWidth, double cutHeight) {
        double hw = width / 2;
        double hh = height / 2;

        return builder()
            .addIsland(-hw, -hh, p -> p
                .addLine(hw, -hh)
                .addLine(hw, -hh + cutHeight)
                .addLine(-hw + cutWidth, -hh + cutHeight)
                .addLine(-hw + cutWidth, hh)
                .addLine(-hw, hh)
                .addLine(-hw, -hh))
            .build();
    }

    /**
     * Create a polygon with custom vertices.
     */
    public static SurfaceRenderer polygon(double[] xPoints, double[] yPoints) {
        if (xPoints.length != yPoints.length || xPoints.length < 3) {
            throw new IllegalArgumentException("Need at least 3 points for a polygon");
        }

        return builder()
            .addIsland(xPoints[0], yPoints[0], p -> {
                for (int i = 1; i < xPoints.length; i++) {
                    p.addLine(xPoints[i], yPoints[i]);
                }
                // Close the polygon
                p.addLine(xPoints[0], yPoints[0]);
            })
            .build();
    }

    /**
     * Create a surface with multiple separate islands (like the D and E example).
     */
    public static SurfaceRenderer multipleIslands(double radius1, double hole1Radius,
                                                   double radius2, double hole2Radius,
                                                   double separation) {
        double offset = separation / 2 + Math.max(radius1, radius2);

        Builder builder = builder();

        // Island 1 with hole (left side)
        builder.addIsland(-offset + radius1, 0, p -> p
            .addArc(-offset - radius1, 0, -offset, 0, true)
            .addArc(-offset + radius1, 0, -offset, 0, true));

        if (hole1Radius > 0) {
            builder.addHole(-offset + hole1Radius, 0, p -> p
                .addArc(-offset - hole1Radius, 0, -offset, 0, false)
                .addArc(-offset + hole1Radius, 0, -offset, 0, false));
        }

        // Island 2 with hole (right side)
        builder.addIsland(offset + radius2, 0, p -> p
            .addArc(offset - radius2, 0, offset, 0, true)
            .addArc(offset + radius2, 0, offset, 0, true));

        if (hole2Radius > 0) {
            builder.addHole(offset + hole2Radius, 0, p -> p
                .addArc(offset - hole2Radius, 0, offset, 0, false)
                .addArc(offset + hole2Radius, 0, offset, 0, false));
        }

        return builder.build();
    }
}

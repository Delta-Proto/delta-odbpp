package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.export.render.SymbolRenderer;
import com.deltaproto.deltaodbpp.export.render.SymbolResolver;
import com.deltaproto.deltaodbpp.model.*;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Renders ODB++ layer features to SVG format.
 * Supports rendering of pads, lines, arcs, surfaces, and text elements.
 * Uses symbol resolution to render pads with their correct shapes and sizes.
 */
public class SvgRenderer {

    private static final Map<String, String> LAYER_COLORS = new HashMap<>();

    static {
        // Default color mapping for layer types
        LAYER_COLORS.put("SIGNAL", "#CC0000");      // Red for signal layers
        LAYER_COLORS.put("POWER_GROUND", "#0000CC"); // Blue for power/ground
        LAYER_COLORS.put("SOLDER_MASK", "#00CC00");  // Green for solder mask
        LAYER_COLORS.put("SILK_SCREEN", "#FFFF00");  // Yellow for silk screen
        LAYER_COLORS.put("DRILL", "#FF00FF");        // Magenta for drill
        LAYER_COLORS.put("DOCUMENT", "#888888");     // Gray for document
        LAYER_COLORS.put("DEFAULT", "#000000");      // Black default
    }

    private final SvgRenderOptions options;
    private SymbolResolver symbolResolver;

    public SvgRenderer() {
        this(new SvgRenderOptions());
    }

    public SvgRenderer(SvgRenderOptions options) {
        this.options = options;
        // SymbolResolver will be created lazily with correct unit information
        this.symbolResolver = null;
    }

    /**
     * Get or create a SymbolResolver for the given features.
     * Creates a new resolver if the unit system changes.
     */
    private SymbolResolver getSymbolResolver(Features features) {
        boolean useMillimeters = features != null && features.isMillimeters();
        if (symbolResolver == null) {
            symbolResolver = new SymbolResolver(options.getDefaultPadSize(), useMillimeters);
        }
        return symbolResolver;
    }

    /**
     * Renders a layer's features to an SVG file.
     *
     * @param layer The layer to render
     * @param outputPath The path for the output SVG file
     * @throws IOException If there's an error writing the file
     */
    public void renderLayer(Layer layer, Path outputPath) throws IOException {
        try (Writer writer = Files.newBufferedWriter(outputPath)) {
            renderLayer(layer, writer);
        }
    }

    /**
     * Renders a layer's features to a Writer.
     *
     * @param layer The layer to render
     * @param writer The writer to output SVG to
     * @throws IOException If there's an error writing
     */
    public void renderLayer(Layer layer, Writer writer) throws IOException {
        Features features = layer.getFeatures();
        if (features == null || features.getFeatures().isEmpty()) {
            writeEmptySvg(writer);
            return;
        }

        // Calculate bounds
        Bounds bounds = calculateBounds(features);

        // Write SVG header
        writeSvgHeader(writer, bounds);

        // Write features
        String color = options.getColor() != null ? options.getColor() : LAYER_COLORS.get("DEFAULT");

        for (Feature feature : features.getFeatures()) {
            if (feature instanceof Pad) {
                renderPad((Pad) feature, features, writer, color);
            } else if (feature instanceof Line) {
                renderLine((Line) feature, features, writer, color);
            } else if (feature instanceof Arc) {
                renderArc((Arc) feature, features, writer, color);
            } else if (feature instanceof Surface) {
                renderSurface((Surface) feature, writer, color);
            } else if (feature instanceof Text) {
                renderText((Text) feature, writer, color);
            } else if (feature instanceof Barcode) {
                renderBarcode((Barcode) feature, writer, color);
            }
        }

        // Write SVG footer
        writeSvgFooter(writer);
    }

    /**
     * Renders features directly to SVG.
     *
     * @param features The features to render
     * @param outputPath The path for the output SVG file
     * @throws IOException If there's an error writing the file
     */
    public void renderFeatures(Features features, Path outputPath) throws IOException {
        try (Writer writer = Files.newBufferedWriter(outputPath)) {
            renderFeatures(features, writer);
        }
    }

    /**
     * Renders features to a Writer.
     *
     * @param features The features to render
     * @param writer The writer to output SVG to
     * @throws IOException If there's an error writing
     */
    public void renderFeatures(Features features, Writer writer) throws IOException {
        if (features == null || features.getFeatures().isEmpty()) {
            writeEmptySvg(writer);
            return;
        }

        Bounds bounds = calculateBounds(features);
        writeSvgHeader(writer, bounds);

        String color = options.getColor() != null ? options.getColor() : LAYER_COLORS.get("DEFAULT");

        for (Feature feature : features.getFeatures()) {
            if (feature instanceof Pad) {
                renderPad((Pad) feature, features, writer, color);
            } else if (feature instanceof Line) {
                renderLine((Line) feature, features, writer, color);
            } else if (feature instanceof Arc) {
                renderArc((Arc) feature, features, writer, color);
            } else if (feature instanceof Surface) {
                renderSurface((Surface) feature, writer, color);
            } else if (feature instanceof Text) {
                renderText((Text) feature, writer, color);
            } else if (feature instanceof Barcode) {
                renderBarcode((Barcode) feature, writer, color);
            }
        }

        writeSvgFooter(writer);
    }

    private void writeEmptySvg(Writer writer) throws IOException {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"100\">\n");
        writer.write("  <!-- Empty layer -->\n");
        writer.write("</svg>\n");
    }

    private void writeSvgHeader(Writer writer, Bounds bounds) throws IOException {
        // Convert bounds to output units
        double minX = options.toOutputUnit(bounds.minX);
        double minY = options.toOutputUnit(bounds.minY);
        double maxX = options.toOutputUnit(bounds.maxX);
        double maxY = options.toOutputUnit(bounds.maxY);
        double padding = options.toOutputUnit(options.getPadding());

        // Calculate viewBox dimensions in output units
        double viewWidth = (maxX - minX + 2 * padding) * options.getScale();
        double viewHeight = (maxY - minY + 2 * padding) * options.getScale();

        // Convert to pixels for width/height attributes using DPI
        double dpi = options.getDpi();
        double pixelWidth, pixelHeight;
        if (options.getOutputUnit() == SvgRenderOptions.OutputUnit.INCH) {
            pixelWidth = viewWidth * dpi;
            pixelHeight = viewHeight * dpi;
        } else {
            // MM: convert to inches first, then to pixels
            pixelWidth = (viewWidth / SvgRenderOptions.INCH_TO_MM) * dpi;
            pixelHeight = (viewHeight / SvgRenderOptions.INCH_TO_MM) * dpi;
        }

        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write(String.format(Locale.US, "<svg xmlns=\"http://www.w3.org/2000/svg\" " +
                "width=\"%.2f\" height=\"%.2f\" " +
                "viewBox=\"%.4f %.4f %.4f %.4f\" " +
                "stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-width=\"0\" fill-rule=\"nonzero\">\n",
                pixelWidth, pixelHeight,
                minX - padding,
                minY - padding,
                maxX - minX + 2 * padding,
                maxY - minY + 2 * padding));

        // Add a transform to flip Y axis (SVG Y grows down, ODB++ Y grows up)
        double centerY = (maxY + minY) / 2;
        writer.write(String.format(Locale.US, "  <g transform=\"scale(1,-1) translate(0,%.4f)\">\n", -2 * centerY));

        // Add background if specified
        if (options.getBackgroundColor() != null) {
            writer.write(String.format(Locale.US, "    <rect x=\"%.4f\" y=\"%.4f\" width=\"%.4f\" height=\"%.4f\" fill=\"%s\"/>\n",
                    minX - padding,
                    minY - padding,
                    maxX - minX + 2 * padding,
                    maxY - minY + 2 * padding,
                    options.getBackgroundColor()));
        }
    }

    private void writeSvgFooter(Writer writer) throws IOException {
        writer.write("  </g>\n");
        writer.write("</svg>\n");
    }

    private void renderPad(Pad pad, Features features, Writer writer, String color) throws IOException {
        // Resolve symbol from symbol table
        SymbolRenderer renderer = getSymbolResolver(features).resolve(pad.getSymbolNumber(), features);

        // Determine rotation from orientation type
        double rotation = getRotationFromOrientationType(pad.getOrientationType(), pad.getCustomRotation());

        // Determine if mirrored
        boolean mirror = isOrientationMirrored(pad.getOrientationType());

        // Apply resize factor if present
        double scale = pad.getResizeFactor() != null ? pad.getResizeFactor() : 1.0;

        // Both positions and symbol dimensions land in mm at parse time; convert
        // through the same toOutputUnit so they end up in the configured output unit.
        double x = options.toOutputUnit(pad.getX());
        double y = options.toOutputUnit(pad.getY());
        double symbolScale = scale * options.toOutputUnit(1.0);

        // Render using the symbol renderer
        String svg = renderer.render(x, y, rotation, mirror, symbolScale, color);
        writer.write("    " + svg + "\n");
    }

    /**
     * Get rotation angle from orientation type.
     * Legacy values 0-7: 0=0°, 1=90°, 2=180°, 3=270°, 4=0°+mirror, 5=90°+mirror, 6=180°+mirror, 7=270°+mirror
     * New format: 8=any angle no mirror, 9=any angle with mirror
     */
    private double getRotationFromOrientationType(int orientationType, Double customRotation) {
        if (orientationType == 8 || orientationType == 9) {
            return customRotation != null ? customRotation : 0;
        }
        return switch (orientationType % 4) {
            case 0 -> 0;
            case 1 -> 90;
            case 2 -> 180;
            case 3 -> 270;
            default -> 0;
        };
    }

    /**
     * Check if orientation type includes mirroring.
     */
    private boolean isOrientationMirrored(int orientationType) {
        return orientationType >= 4 && orientationType <= 7 || orientationType == 9;
    }

    private void renderLine(Line line, Features features, Writer writer, String color) throws IOException {
        // Resolve symbol to get line width. SymbolResolver returns mm; convert to output unit.
        SymbolRenderer renderer = getSymbolResolver(features).resolve(line.getSymbolNumber(), features);
        double strokeWidth = options.toOutputUnit(renderer.getWidth());

        // Convert coordinates to output units
        double x1 = options.toOutputUnit(line.getXs());
        double y1 = options.toOutputUnit(line.getYs());
        double x2 = options.toOutputUnit(line.getXe());
        double y2 = options.toOutputUnit(line.getYe());

        // Use path format like reference SVG: <path d="M x1 y1 L x2 y2" .../>
        writer.write(String.format(Locale.US, "    <path d=\"M %.4f %.4f L %.4f %.4f\" " +
                        "stroke=\"%s\" stroke-width=\"%.4f\" stroke-linecap=\"round\" fill=\"none\"/>\n",
                x1, y1, x2, y2,
                color, strokeWidth));
    }

    private void renderArc(Arc arc, Features features, Writer writer, String color) throws IOException {
        // Calculate arc parameters for SVG path (in internal mm units first)
        double xs = arc.getXs();
        double ys = arc.getYs();
        double xe = arc.getXe();
        double ye = arc.getYe();
        double xc = arc.getXc();
        double yc = arc.getYc();

        // Calculate radius in internal units
        double radius = Math.sqrt(Math.pow(xs - xc, 2) + Math.pow(ys - yc, 2));

        // Determine arc direction (large arc flag and sweep flag)
        // This is simplified - full implementation would handle all cases
        int largeArcFlag = 0;
        boolean isClockwise = "Y".equalsIgnoreCase(arc.getCw());
        int sweepFlag = isClockwise ? 0 : 1;

        // Resolve symbol to get line width. SymbolResolver returns mm; convert to output unit.
        SymbolRenderer renderer = getSymbolResolver(features).resolve(arc.getSymbolNumber(), features);
        double strokeWidth = options.toOutputUnit(renderer.getWidth());

        // Convert all coordinates and radius to output units
        double x1 = options.toOutputUnit(xs);
        double y1 = options.toOutputUnit(ys);
        double x2 = options.toOutputUnit(xe);
        double y2 = options.toOutputUnit(ye);
        double r = options.toOutputUnit(radius);

        writer.write(String.format(Locale.US, "    <path d=\"M %.4f %.4f A %.4f %.4f 0 %d %d %.4f %.4f\" " +
                        "stroke=\"%s\" stroke-width=\"%.4f\" fill=\"none\" stroke-linecap=\"round\"/>\n",
                x1, y1, r, r, largeArcFlag, sweepFlag, x2, y2,
                color, strokeWidth));
    }

    private void renderSurface(Surface surface, Writer writer, String color) throws IOException {
        if (surface.getPolygons().isEmpty()) {
            return;
        }

        // Combine all polygons into a single path for proper hole handling
        // Using evenodd fill-rule to properly handle holes cutting out from islands
        StringBuilder pathData = new StringBuilder();

        for (ContourPolygon polygon : surface.getPolygons()) {
            double startX = options.toOutputUnit(polygon.getXStart());
            double startY = options.toOutputUnit(polygon.getYStart());
            pathData.append(String.format(Locale.US, "M %.4f %.4f", startX, startY));

            double prevX = polygon.getXStart();
            double prevY = polygon.getYStart();

            for (ContourPolygon.PolygonPart part : polygon.getPolygonParts()) {
                double endX = options.toOutputUnit(part.getEndX());
                double endY = options.toOutputUnit(part.getEndY());

                if (part.getType() == ContourPolygon.PolygonPart.Type.SEGMENT) {
                    pathData.append(String.format(Locale.US, " L %.4f %.4f", endX, endY));
                } else if (part.getType() == ContourPolygon.PolygonPart.Type.ARC) {
                    // Calculate radius for arc (in internal units first)
                    double radius = Math.sqrt(
                            Math.pow(part.getEndX() - part.getXCenter(), 2) +
                            Math.pow(part.getEndY() - part.getYCenter(), 2));

                    // Determine sweep direction
                    // In SVG, sweep-flag=1 means clockwise, sweep-flag=0 means counter-clockwise
                    // But SVG Y-axis is inverted, so we flip
                    int sweepFlag = part.isClockwise() ? 0 : 1;

                    // Calculate if it's a large arc (> 180 degrees)
                    double startAngle = Math.atan2(prevY - part.getYCenter(), prevX - part.getXCenter());
                    double endAngle = Math.atan2(part.getEndY() - part.getYCenter(), part.getEndX() - part.getXCenter());
                    double angleDiff = endAngle - startAngle;
                    if (angleDiff < 0) angleDiff += 2 * Math.PI;
                    if (!part.isClockwise() && angleDiff > Math.PI) {
                        angleDiff = 2 * Math.PI - angleDiff;
                    }
                    int largeArcFlag = angleDiff > Math.PI ? 1 : 0;

                    double r = options.toOutputUnit(radius);
                    pathData.append(String.format(Locale.US, " A %.4f %.4f 0 %d %d %.4f %.4f",
                            r, r, largeArcFlag, sweepFlag, endX, endY));
                }

                prevX = part.getEndX();
                prevY = part.getEndY();
            }

            pathData.append(" Z ");
        }

        String fillColor = surface.getPolarity() == Polarity.NEGATIVE ?
                options.getBackgroundColor() != null ? options.getBackgroundColor() : "#FFFFFF" :
                color;

        // Use evenodd fill-rule to properly handle holes inside islands
        writer.write(String.format(Locale.US, "    <path d=\"%s\" fill=\"%s\" fill-rule=\"evenodd\" stroke=\"none\"/>\n",
                pathData.toString().trim(), fillColor));
    }

    private void renderText(Text text, Writer writer, String color) throws IOException {
        if (text.getText() == null || text.getText().isEmpty()) {
            return;
        }

        double fontSize = options.toOutputUnit(text.getYsize() > 0 ? text.getYsize() : options.getDefaultFontSize());
        double x = options.toOutputUnit(text.getX());
        double y = options.toOutputUnit(text.getY());

        // Note: SVG text is rendered with Y growing down, but we're in a flipped coordinate system
        // So we need to apply an additional flip transform to the text
        writer.write(String.format(Locale.US, "    <text x=\"%.4f\" y=\"%.4f\" " +
                        "font-size=\"%.4f\" fill=\"%s\" transform=\"scale(1,-1) translate(0,%.4f)\">%s</text>\n",
                x, -y, fontSize, color, 0.0,
                escapeXml(text.getText())));
    }

    private void renderBarcode(Barcode barcode, Writer writer, String color) throws IOException {
        if (barcode == null) {
            return;
        }

        double x = options.toOutputUnit(barcode.getX());
        double y = options.toOutputUnit(barcode.getY());
        double width = options.toOutputUnit(barcode.getWidth() > 0 ? barcode.getWidth() : options.getDefaultPadSize() * 4);
        double height = options.toOutputUnit(barcode.getHeight() > 0 ? barcode.getHeight() : options.getDefaultPadSize() * 2);
        double strokeWidth = options.toOutputUnit(options.getDefaultLineWidth() / 2);

        // Handle rotation
        String transform = "";
        if (barcode.getOrientDefRotation() != 0) {
            transform = String.format(Locale.US, " transform=\"rotate(%.2f %.4f %.4f)\"",
                    barcode.getOrientDefRotation(), x + width / 2, y + height / 2);
        }

        // Render barcode as a rectangle with pattern (simplified)
        writer.write(String.format(Locale.US, "    <g%s>\n", transform));
        writer.write(String.format(Locale.US, "      <rect x=\"%.4f\" y=\"%.4f\" width=\"%.4f\" height=\"%.4f\" " +
                        "fill=\"%s\" stroke=\"%s\" stroke-width=\"%.4f\"/>\n",
                x, y, width, height, color, color, strokeWidth));

        // Add barcode pattern lines (simplified vertical bars)
        int numBars = 10;
        double barWidth = width / (numBars * 2);
        for (int i = 0; i < numBars; i++) {
            double barX = x + (i * 2 + 1) * barWidth;
            writer.write(String.format(Locale.US, "      <rect x=\"%.4f\" y=\"%.4f\" width=\"%.4f\" height=\"%.4f\" fill=\"white\"/>\n",
                    barX, y, barWidth, height));
        }
        writer.write("    </g>\n");
    }

    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    /**
     * Renders a board profile (outline) to an SVG file.
     *
     * @param profile The profile to render
     * @param outputPath The path for the output SVG file
     * @throws IOException If there's an error writing the file
     */
    public void renderProfile(Profile profile, Path outputPath) throws IOException {
        try (Writer writer = Files.newBufferedWriter(outputPath)) {
            renderProfile(profile, writer);
        }
    }

    /**
     * Renders a board profile (outline) to a Writer.
     *
     * @param profile The profile to render
     * @param writer The writer to output SVG to
     * @throws IOException If there's an error writing
     */
    public void renderProfile(Profile profile, Writer writer) throws IOException {
        if (profile == null || profile.getSurfaces().isEmpty()) {
            writeEmptySvg(writer);
            return;
        }

        Bounds bounds = calculateProfileBounds(profile);
        writeSvgHeader(writer, bounds);

        String strokeColor = options.getColor() != null ? options.getColor() : "#000000";

        // Render each surface in the profile
        for (Surface surface : profile.getSurfaces()) {
            renderProfileSurface(surface, writer, strokeColor);
        }

        writeSvgFooter(writer);
    }

    private void renderProfileSurface(Surface surface, Writer writer, String color) throws IOException {
        if (surface.getPolygons() == null || surface.getPolygons().isEmpty()) {
            return;
        }

        // Combine all polygons into a single path for proper rendering
        StringBuilder pathData = new StringBuilder();

        for (ContourPolygon polygon : surface.getPolygons()) {
            double startX = options.toOutputUnit(polygon.getXStart());
            double startY = options.toOutputUnit(polygon.getYStart());
            pathData.append(String.format(Locale.US, "M %.4f %.4f", startX, startY));

            double prevX = polygon.getXStart();
            double prevY = polygon.getYStart();

            for (ContourPolygon.PolygonPart part : polygon.getPolygonParts()) {
                double endX = options.toOutputUnit(part.getEndX());
                double endY = options.toOutputUnit(part.getEndY());

                if (part.getType() == ContourPolygon.PolygonPart.Type.SEGMENT) {
                    pathData.append(String.format(Locale.US, " L %.4f %.4f", endX, endY));
                } else if (part.getType() == ContourPolygon.PolygonPart.Type.ARC) {
                    double radius = Math.sqrt(
                            Math.pow(part.getEndX() - part.getXCenter(), 2) +
                            Math.pow(part.getEndY() - part.getYCenter(), 2));
                    int sweepFlag = part.isClockwise() ? 0 : 1;

                    // Calculate large arc flag
                    double startAngle = Math.atan2(prevY - part.getYCenter(), prevX - part.getXCenter());
                    double endAngle = Math.atan2(part.getEndY() - part.getYCenter(), part.getEndX() - part.getXCenter());
                    double angleDiff = endAngle - startAngle;
                    if (angleDiff < 0) angleDiff += 2 * Math.PI;
                    if (!part.isClockwise() && angleDiff > Math.PI) {
                        angleDiff = 2 * Math.PI - angleDiff;
                    }
                    int largeArcFlag = angleDiff > Math.PI ? 1 : 0;

                    double r = options.toOutputUnit(radius);
                    pathData.append(String.format(Locale.US, " A %.4f %.4f 0 %d %d %.4f %.4f",
                            r, r, largeArcFlag, sweepFlag, endX, endY));
                }

                prevX = part.getEndX();
                prevY = part.getEndY();
            }

            pathData.append(" Z ");
        }

        // Profile is typically rendered as outline only with evenodd fill-rule
        double strokeWidth = options.toOutputUnit(options.getDefaultLineWidth());
        writer.write(String.format(Locale.US, "    <path d=\"%s\" fill=\"none\" fill-rule=\"evenodd\" stroke=\"%s\" stroke-width=\"%.4f\"/>\n",
                pathData.toString().trim(), color, strokeWidth));
    }

    private Bounds calculateProfileBounds(Profile profile) {
        Bounds bounds = new Bounds();

        for (Surface surface : profile.getSurfaces()) {
            if (surface.getPolygons() != null) {
                for (ContourPolygon polygon : surface.getPolygons()) {
                    bounds.update(polygon.getXStart(), polygon.getYStart());
                    for (ContourPolygon.PolygonPart part : polygon.getPolygonParts()) {
                        bounds.update(part.getEndX(), part.getEndY());
                    }
                }
            }
        }

        if (bounds.minX == Double.MAX_VALUE) {
            bounds.minX = 0;
            bounds.minY = 0;
            bounds.maxX = 100;
            bounds.maxY = 100;
        }

        return bounds;
    }

    /**
     * Renders drill holes for a drill layer.
     *
     * @param features The drill layer features
     * @param tools The drill tools defining hole sizes
     * @param outputPath The path for the output SVG file
     * @throws IOException If there's an error writing the file
     */
    public void renderDrillLayer(Features features, java.util.List<com.deltaproto.deltaodbpp.model.Tool> tools,
                                  Path outputPath) throws IOException {
        try (Writer writer = Files.newBufferedWriter(outputPath)) {
            renderDrillLayer(features, tools, writer);
        }
    }

    /**
     * Renders drill holes to a Writer.
     */
    public void renderDrillLayer(Features features, java.util.List<com.deltaproto.deltaodbpp.model.Tool> tools,
                                  Writer writer) throws IOException {
        if (features == null || features.getFeatures().isEmpty()) {
            writeEmptySvg(writer);
            return;
        }

        // Build tool diameter lookup, keyed by tool NUM (matches feature dcode)
        Map<Integer, Double> toolDiameters = new HashMap<>();
        if (tools != null) {
            for (com.deltaproto.deltaodbpp.model.Tool tool : tools) {
                toolDiameters.put(tool.getNum(), tool.getFinishSize());
            }
        }
        // Symbol-name units: microns for MM files, mils for INCH files
        double symbolToMm = features.isMillimeters() ? 0.001 : 0.0254;

        Bounds bounds = calculateBounds(features);
        writeSvgHeader(writer, bounds);

        String holeColor = options.getColor() != null ? options.getColor() : LAYER_COLORS.get("DRILL");

        for (Feature feature : features.getFeatures()) {
            if (feature instanceof Pad) {
                Pad pad = (Pad) feature;
                // Hole size comes from the pad's own symbol (r<size>); the tools
                // file (keyed by dcode = tool NUM) is the fallback.
                double diameter = drillDiameterMm(pad, features, toolDiameters, symbolToMm);
                double radius = options.toOutputUnit(diameter / 2);
                double x = options.toOutputUnit(pad.getX());
                double y = options.toOutputUnit(pad.getY());
                double strokeWidth = options.toOutputUnit(options.getDefaultLineWidth() / 2);

                // Render drill hole as a circle with outline
                writer.write(String.format(Locale.US, "    <circle cx=\"%.4f\" cy=\"%.4f\" r=\"%.4f\" " +
                                "fill=\"white\" stroke=\"%s\" stroke-width=\"%.4f\"/>\n",
                        x, y, radius, holeColor, strokeWidth));

                // Add crosshair for drill center
                double crossSize = radius * 0.3;
                writer.write(String.format(Locale.US, "    <line x1=\"%.4f\" y1=\"%.4f\" x2=\"%.4f\" y2=\"%.4f\" " +
                                "stroke=\"%s\" stroke-width=\"%.4f\"/>\n",
                        x - crossSize, y, x + crossSize, y,
                        holeColor, strokeWidth));
                writer.write(String.format(Locale.US, "    <line x1=\"%.4f\" y1=\"%.4f\" x2=\"%.4f\" y2=\"%.4f\" " +
                                "stroke=\"%s\" stroke-width=\"%.4f\"/>\n",
                        x, y - crossSize, x, y + crossSize,
                        holeColor, strokeWidth));
            }
        }

        writeSvgFooter(writer);
    }

    /**
     * Resolves a drill hole diameter in mm: prefer the pad's round symbol
     * (r&lt;size&gt; in mils/microns), then the tools file via dcode, then the
     * configured default pad size.
     */
    private double drillDiameterMm(Pad pad, Features features,
                                   Map<Integer, Double> toolDiameters, double symbolToMm) {
        String symbolName = features.getSymbolName(pad.getSymbolNumber());
        if (symbolName != null && symbolName.matches("r[0-9.]+")) {
            return Double.parseDouble(symbolName.substring(1)) * symbolToMm;
        }
        Double bySize = toolDiameters.get(pad.getDcode());
        if (bySize != null && bySize > 0) {
            return bySize;
        }
        return options.getDefaultPadSize();
    }

    private Bounds calculateBounds(Features features) {
        Bounds bounds = new Bounds();

        for (Feature feature : features.getFeatures()) {
            if (feature instanceof Pad) {
                Pad pad = (Pad) feature;
                bounds.update(pad.getX(), pad.getY());
            } else if (feature instanceof Line) {
                Line line = (Line) feature;
                bounds.update(line.getXs(), line.getYs());
                bounds.update(line.getXe(), line.getYe());
            } else if (feature instanceof Arc) {
                Arc arc = (Arc) feature;
                bounds.update(arc.getXs(), arc.getYs());
                bounds.update(arc.getXe(), arc.getYe());
                bounds.update(arc.getXc(), arc.getYc());
            } else if (feature instanceof Surface) {
                Surface surface = (Surface) feature;
                for (ContourPolygon polygon : surface.getPolygons()) {
                    bounds.update(polygon.getXStart(), polygon.getYStart());
                    for (ContourPolygon.PolygonPart part : polygon.getPolygonParts()) {
                        bounds.update(part.getEndX(), part.getEndY());
                        if (part.getType() == ContourPolygon.PolygonPart.Type.ARC) {
                            bounds.update(part.getXCenter(), part.getYCenter());
                        }
                    }
                }
            } else if (feature instanceof Text) {
                Text text = (Text) feature;
                bounds.update(text.getX(), text.getY());
            }
        }

        // Ensure minimum size
        if (bounds.minX == Double.MAX_VALUE) {
            bounds.minX = 0;
            bounds.minY = 0;
            bounds.maxX = 100;
            bounds.maxY = 100;
        }

        return bounds;
    }

    private static class Bounds {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;

        void update(double x, double y) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
    }
}

package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.export.render.Extent;
import com.deltaproto.deltaodbpp.export.render.FeaturePainter;
import com.deltaproto.deltaodbpp.export.render.FeaturePainter.Palette;
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
    private Map<String, Symbol> userSymbols;
    private StandardFont font;
    private int maskCounter;

    public SvgRenderer() {
        this(new SvgRenderOptions());
    }

    public SvgRenderer(SvgRenderOptions options) {
        this.options = options;
    }

    /**
     * Supplies the job a layer belongs to, so that its user-defined symbols and its stroke
     * font are drawn as designed. Without a job, pads of a user-defined symbol fall back
     * to the default pad and text to a generic typeface.
     *
     * @return this renderer
     */
    public SvgRenderer forJob(Job job) {
        this.userSymbols = job != null ? job.getSymbols() : null;
        this.font = job != null ? job.getStandardFont() : null;
        return this;
    }

    /** A hairline is this fraction of the drawing's larger dimension. */
    private static final double HAIRLINES_ACROSS = 2500;

    private FeaturePainter painter() {
        return new FeaturePainter(options.toOutputUnit(1.0), options.getDefaultPadSize(), userSymbols, font)
                .fallbackFontSizeMm(options.getDefaultFontSize());
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
        renderFeatures(layer.getFeatures(), writer);
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
        // Zero-width strokes are drawn as hairlines that stay visible at this size.
        FeaturePainter painter = painter().hairlineMm(
                Math.max(bounds.maxX - bounds.minX, bounds.maxY - bounds.minY) / HAIRLINES_ACROSS);
        if (painter.hasClear(features)) {
            // Clear features erase what the layer drew before them and nothing else: the
            // layer is painted white on black into a mask and shown as a colour through it.
            String frame = frameRect(bounds);
            String maskId = "layer-mask-" + (++maskCounter);
            writer.write("    <mask id=\"" + maskId + "\" maskUnits=\"userSpaceOnUse\""
                    + " maskContentUnits=\"userSpaceOnUse\" " + frame + ">\n");
            painter.paint(features, writer, Palette.of("#ffffff", "#000000"));
            writer.write("    </mask>\n");
            writer.write("    <rect " + frame + " fill=\"" + color + "\" mask=\"url(#" + maskId + ")\"/>\n");
        } else {
            painter.paint(features, writer, Palette.of(color, color));
        }

        writeSvgFooter(writer);
    }

    /** The drawing frame — bounds plus padding — as rectangle attributes in output units. */
    private String frameRect(Bounds bounds) {
        double padding = options.toOutputUnit(options.getPadding());
        double minX = options.toOutputUnit(bounds.minX) - padding;
        double minY = options.toOutputUnit(bounds.minY) - padding;
        double maxX = options.toOutputUnit(bounds.maxX) + padding;
        double maxY = options.toOutputUnit(bounds.maxY) + padding;
        return String.format(Locale.US, "x=\"%.4f\" y=\"%.4f\" width=\"%.4f\" height=\"%.4f\"",
                minX, minY, maxX - minX, maxY - minY);
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

        Features outline = new Features();
        outline.getFeatures().addAll(profile.getSurfaces());

        Bounds bounds = calculateBounds(outline);
        writeSvgHeader(writer, bounds);

        String strokeColor = options.getColor() != null ? options.getColor() : "#000000";
        String pathData = painter().surfacePathData(outline);
        if (!pathData.isEmpty()) {
            // The outline only: islands and cut-outs are both drawn as contours.
            double strokeWidth = options.toOutputUnit(options.getDefaultLineWidth());
            writer.write(String.format(Locale.US,
                    "    <path d=\"%s\" fill=\"none\" stroke=\"%s\" stroke-width=\"%.4f\"/>\n",
                    pathData, strokeColor, strokeWidth));
        }

        writeSvgFooter(writer);
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
        Bounds bounds = calculateBounds(features);
        writeSvgHeader(writer, bounds);

        String holeColor = options.getColor() != null ? options.getColor() : LAYER_COLORS.get("DRILL");

        for (Feature feature : features.getFeatures()) {
            if (feature instanceof Pad) {
                Pad pad = (Pad) feature;
                // Hole size comes from the pad's own symbol (r<size>); the tools
                // file (keyed by dcode = tool NUM) is the fallback.
                double diameter = drillDiameterMm(pad, features, toolDiameters);
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
    private double drillDiameterMm(Pad pad, Features features, Map<Integer, Double> toolDiameters) {
        String symbolName = features.getSymbolName(pad.getSymbolNumber());
        if (symbolName != null && symbolName.matches("r[0-9.]+")) {
            // Symbol names count mils or microns, as the symbol's own entry declares.
            return Double.parseDouble(symbolName.substring(1))
                    * features.symbolUnitToMm(pad.getSymbolNumber());
        }
        Double bySize = toolDiameters.get(pad.getDcode());
        if (bySize != null && bySize > 0) {
            return bySize;
        }
        return options.getDefaultPadSize();
    }

    private Bounds calculateBounds(Features features) {
        // What the features draw, not only where they are: a pad on the edge of the
        // artwork belongs in the picture whole.
        Extent extent = new Extent();
        painter().addInk(features, extent);

        Bounds bounds = new Bounds();
        if (extent.isEmpty()) {
            bounds.minX = 0;
            bounds.minY = 0;
            bounds.maxX = 100;
            bounds.maxY = 100;
        } else {
            bounds.minX = extent.minX();
            bounds.minY = extent.minY();
            bounds.maxX = extent.maxX();
            bounds.maxY = extent.maxY();
        }
        return bounds;
    }

    private static class Bounds {
        double minX;
        double minY;
        double maxX;
        double maxY;
    }
}

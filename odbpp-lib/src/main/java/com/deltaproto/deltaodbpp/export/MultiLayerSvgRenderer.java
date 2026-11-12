package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.export.render.Extent;
import com.deltaproto.deltaodbpp.export.render.FeaturePainter;
import com.deltaproto.deltaodbpp.export.render.FeaturePainter.Palette;
import com.deltaproto.deltaodbpp.model.*;
import com.deltaproto.deltaodbpp.model.Component;
import com.deltaproto.deltaodbpp.model.MirrorType;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Locale;

/**
 * Renders multiple ODB++ layers as a composite SVG with individual layer groups.
 * Each layer is rendered as a separate SVG group that can be toggled via CSS/JS.
 */
public class MultiLayerSvgRenderer {

    // Realistic PCB rendering colors (matches typical PCB viewer rendering)
    private static final String FR4_COLOR = "#666666";           // Dark gray substrate
    private static final String COPPER_COLOR = "#cccccc";         // Silver/gray copper under soldermask
    private static final String COPPER_FINISH_COLOR = "#cc9933";  // Gold HASL/ENIG finish on exposed pads
    private static final double SOLDERMASK_OPACITY = 0.75;
    private static final String BLIND_HOLE_COLOR = "#1a1a1a";    // Shadow in a hole that ends inside the board
    private static final double BLIND_HOLE_OPACITY = 0.55;

    /** Which side of the board a realistic render / colour setter applies to. */
    public enum Side { TOP, BOTTOM }

    // Soldermask + silkscreen colors for the realistic render, held per side so a board
    // masked green on top and black underneath renders as one. Default to the realistic
    // dark green with the white legend it pairs with; override via
    // setSoldermaskColor/setSilkscreenColor. Mirrors delta-gerber's MultiLayerSVGRenderer.
    private final SideColors topColors = new SideColors();
    private final SideColors bottomColors = new SideColors();

    /**
     * The soldermask and silkscreen fills for one side of the board. A {@code null} fill
     * means that finish is not applied: the realistic render draws no mask sheet, or no
     * legend, rather than substituting a color.
     *
     * <p>Silkscreen carries three states — unset, a color, or none — so that the two
     * setters commute. Unset takes the color the mask pairs with, which is what a fab does
     * when you don't ask; once a caller names a {@link SilkscreenColor}, a later
     * {@link SoldermaskColor} re-pairs nothing and leaves the legend alone.
     */
    private static final class SideColors {
        private String mask = SoldermaskColor.DEFAULT.getMaskColor();
        private String pairedSilkscreen = SoldermaskColor.DEFAULT.getSilkscreenColor();
        private String silkscreen;
        private boolean silkscreenSet;

        void setSoldermask(SoldermaskColor color) {
            this.mask = color.getMaskColor();
            this.pairedSilkscreen = color.getSilkscreenColor();
        }

        void setSoldermask(String maskColor) {
            this.mask = maskColor;
        }

        void setSilkscreen(String color) {
            this.silkscreen = color;
            this.silkscreenSet = true;
        }

        /** The mask fill, or {@code null} when no soldermask is applied. */
        String mask() {
            return mask;
        }

        /** The legend fill, or {@code null} when no silkscreen is printed. */
        String silkscreen() {
            return silkscreenSet ? silkscreen : pairedSilkscreen;
        }
    }

    private SideColors colorsFor(Side side) {
        return side == Side.BOTTOM ? bottomColors : topColors;
    }

    private SideColors colorsFor(boolean topSide) {
        return topSide ? topColors : bottomColors;
    }

    /**
     * Set the soldermask color on both sides, for the realistic render and the PNG paths
     * built on it. Unless {@link #setSilkscreenColor} says otherwise the legend takes the
     * color this mask pairs with — black on white soldermask, white on every other. Defaults
     * to {@link SoldermaskColor#GREEN}; {@link SoldermaskColor#NONE} leaves the copper bare.
     */
    public MultiLayerSvgRenderer setSoldermaskColor(SoldermaskColor color) {
        topColors.setSoldermask(color);
        bottomColors.setSoldermask(color);
        return this;
    }

    /** As {@link #setSoldermaskColor(SoldermaskColor)}, but for one side of the board. */
    public MultiLayerSvgRenderer setSoldermaskColor(Side side, SoldermaskColor color) {
        colorsFor(side).setSoldermask(color);
        return this;
    }

    /**
     * Set explicit soldermask and silkscreen hex fills on both sides (e.g. {@code "#004200"}),
     * for colors outside the {@link SoldermaskColor} palette. Either may be {@code null} to
     * leave that finish off the board.
     */
    public MultiLayerSvgRenderer setSoldermaskColor(String maskColor, String silkscreenColor) {
        topColors.setSoldermask(maskColor);
        bottomColors.setSoldermask(maskColor);
        return setSilkscreenColor(silkscreenColor);
    }

    /**
     * Set the silkscreen color on both sides, overriding the color the soldermask pairs with.
     * {@link SilkscreenColor#NONE} prints no legend at all. Order-independent: a later
     * {@link #setSoldermaskColor} does not take this choice back.
     */
    public MultiLayerSvgRenderer setSilkscreenColor(SilkscreenColor color) {
        return setSilkscreenColor(color.getColor());
    }

    /** As {@link #setSilkscreenColor(SilkscreenColor)}, but for one side of the board. */
    public MultiLayerSvgRenderer setSilkscreenColor(Side side, SilkscreenColor color) {
        colorsFor(side).setSilkscreen(color.getColor());
        return this;
    }

    /**
     * Set an explicit silkscreen hex fill on both sides, for colors outside the
     * {@link SilkscreenColor} palette. {@code null} prints no legend.
     */
    public MultiLayerSvgRenderer setSilkscreenColor(String color) {
        topColors.setSilkscreen(color);
        bottomColors.setSilkscreen(color);
        return this;
    }

    private static final Map<String, String> LAYER_TYPE_COLORS = new LinkedHashMap<>();

    static {
        LAYER_TYPE_COLORS.put("SIGNAL", "#CC0000");
        LAYER_TYPE_COLORS.put("POWER_GROUND", "#0000CC");
        LAYER_TYPE_COLORS.put("SOLDER_MASK", "#00CC00");
        LAYER_TYPE_COLORS.put("SILK_SCREEN", "#FFFF00");
        LAYER_TYPE_COLORS.put("SOLDER_PASTE", "#FF6600");
        LAYER_TYPE_COLORS.put("DRILL", "#FF00FF");
        LAYER_TYPE_COLORS.put("ROUT", "#00FFFF");
        LAYER_TYPE_COLORS.put("DOCUMENT", "#888888");
        LAYER_TYPE_COLORS.put("COMPONENT", "#FF8800");
    }

    private final SvgRenderOptions baseOptions;
    // The job being rendered: the source of user-defined symbols and of the stroke font.
    // Null when a step is rendered without one; such a render draws standard symbols only.
    private Job currentJob;
    private FeaturePainter painter;
    private double painterUnits;
    private double globalMinX = Double.MAX_VALUE;
    private double globalMinY = Double.MAX_VALUE;
    private double globalMaxX = -Double.MAX_VALUE;
    private double globalMaxY = -Double.MAX_VALUE;
    private final Extent scratchExtent = new Extent();

    // When true (the per-layer preview path), renderFeature omits Text features so the
    // lightweight thumbnail carries only geometry — cheaper to draw and legible when small.
    private boolean previewMode = false;

    public MultiLayerSvgRenderer() {
        this(new SvgRenderOptions());
    }

    public MultiLayerSvgRenderer(SvgRenderOptions options) {
        this.baseOptions = options;
    }

    /** Takes user-defined symbols and the stroke font from this job from now on. */
    private void useJob(Job job) {
        this.currentJob = job;
        this.painter = null;
    }

    /** A hairline is this fraction of the drawing's larger dimension. */
    private static final double HAIRLINES_ACROSS = 2500;

    /** The painter for the current job and output unit. */
    private FeaturePainter painter() {
        double units = baseOptions.toOutputUnit(1.0);
        if (painter == null || units != painterUnits) {
            painter = newPainter();
            painterUnits = units;
        }
        return painter;
    }

    private FeaturePainter newPainter() {
        return new FeaturePainter(baseOptions.toOutputUnit(1.0), baseOptions.getDefaultPadSize(),
                currentJob != null ? currentJob.getSymbols() : null,
                currentJob != null ? currentJob.getStandardFont() : null);
    }

    /**
     * Creates a renderer configured for top layer view with component centroids.
     *
     * @return A renderer for top view
     */
    public static MultiLayerSvgRenderer forTopView() {
        SvgRenderOptions options = new SvgRenderOptions()
                .withComponents();
        return new MultiLayerSvgRenderer(options);
    }

    /**
     * Creates a renderer configured for bottom layer view with component centroids.
     * This applies X-axis mirroring for correct bottom view perspective.
     *
     * @return A renderer for bottom view
     */
    public static MultiLayerSvgRenderer forBottomView() {
        SvgRenderOptions options = new SvgRenderOptions()
                .forBottomView()
                .withComponents();
        return new MultiLayerSvgRenderer(options);
    }

    /**
     * Convenience method to render both top and bottom views of a Job.
     *
     * @param job           The job to render
     * @param topOutputPath Output path for top view SVG
     * @param bottomOutputPath Output path for bottom view SVG
     * @throws IOException If writing fails
     */
    public static void renderTopAndBottom(Job job, Path topOutputPath, Path bottomOutputPath) throws IOException {
        // Render top view
        MultiLayerSvgRenderer topRenderer = forTopView();
        topRenderer.renderJob(job, topOutputPath);

        // Render bottom view
        MultiLayerSvgRenderer bottomRenderer = forBottomView();
        bottomRenderer.renderJob(job, bottomOutputPath);
    }

    /**
     * Convenience method to render both top and bottom views of a Step.
     *
     * @param step          The step to render
     * @param matrix        The matrix for layer info
     * @param topOutputPath Output path for top view SVG
     * @param bottomOutputPath Output path for bottom view SVG
     * @throws IOException If writing fails
     */
    public static void renderTopAndBottom(Step step, Matrix matrix, Path topOutputPath, Path bottomOutputPath) throws IOException {
        // Render top view
        MultiLayerSvgRenderer topRenderer = forTopView();
        try (Writer writer = Files.newBufferedWriter(topOutputPath)) {
            topRenderer.renderStep(step, matrix, writer);
        }

        // Render bottom view
        MultiLayerSvgRenderer bottomRenderer = forBottomView();
        try (Writer writer = Files.newBufferedWriter(bottomOutputPath)) {
            bottomRenderer.renderStep(step, matrix, writer);
        }
    }

    /**
     * Renders all layers from a Job to a composite SVG file.
     */
    public void renderJob(Job job, Path outputPath) throws IOException {
        try (Writer writer = Files.newBufferedWriter(outputPath)) {
            renderJob(job, writer);
        }
    }

    /**
     * Renders all layers from a Job to a Writer.
     */
    public void renderJob(Job job, Writer writer) throws IOException {
        if (job == null || job.getSteps() == null || job.getSteps().isEmpty()) {
            writeEmptySvg(writer);
            return;
        }

        useJob(job);

        // Get the first step (typically "pcb")
        Step step = job.getSteps().values().iterator().next();
        renderStep(step, job.getMatrix(), writer);
    }

    /**
     * Renders all layers from a Step to a composite SVG.
     */
    public void renderStep(Step step, Matrix matrix, Writer writer) throws IOException {
        if (step == null || step.getLayersByName() == null || step.getLayersByName().isEmpty()) {
            writeEmptySvg(writer);
            return;
        }

        // Calculate global bounds from layers
        resetGlobalBounds();
        calculateGlobalBounds(step.getLayersByName().values());

        // Include profile bounds if available (profile often starts at 0,0)
        if (step.getProfile() != null) {
            calculateProfileBounds(step.getProfile());
        }

        // Build layer metadata
        List<LayerInfo> layerInfos = buildLayerInfos(step, matrix);

        // Write SVG
        writeSvgHeader(writer);
        writeStyleSection(writer, layerInfos);
        writeTransformGroup(writer);

        // Render profile first if available (profile is Features type in Step)
        if (step.getProfile() != null && !step.getProfile().getFeatures().isEmpty()) {
            renderProfileFromFeatures(step.getProfile(), writer);
        }

        // Render each layer (including empty layers for completeness)
        for (LayerInfo info : layerInfos) {
            renderLayerGroup(info, writer);
        }

        // Render components if enabled
        if (baseOptions.isRenderComponents()) {
            renderComponents(step, writer);
        }

        writeCloseTransformGroup(writer);
        writeSvgFooter(writer);
    }

    // ------------------------------------------------------------------
    // Per-layer SVG API — one matrix layer as a standalone SVG, plus a
    // lightweight preview variant for file-list thumbnails.
    // ------------------------------------------------------------------

    /** Cap on either dimension of a preview SVG's width/height attributes, in pixels. */
    private static final double PREVIEW_MAX_DIMENSION_PX = 256.0;

    /**
     * Render a single matrix layer (by name) of the Job's first step to a standalone SVG
     * string, using the same per-layer colour mapping and coordinate frame as
     * {@link #renderStep(Step, Matrix, Writer)}. The board profile is drawn as a faint
     * outline for context.
     *
     * @param job       the ODB++ job
     * @param stepName  the step to render from, or {@code null} for the first step
     * @param layerName the layer name (case-insensitive) to render
     * @return a full standalone SVG document; an empty SVG if the layer is absent/empty
     */
    public String renderLayerSvg(Job job, String stepName, String layerName) throws IOException {
        StringWriter writer = new StringWriter();
        renderLayerSvg(job, stepName, layerName, writer);
        return writer.toString();
    }

    /** As {@link #renderLayerSvg(Job, String, String)}, streaming to a {@link Writer}. */
    public void renderLayerSvg(Job job, String stepName, String layerName, Writer writer)
            throws IOException {
        renderLayer(job, stepName, layerName, false, writer);
    }

    /**
     * Render a lightweight <em>preview</em> of a single matrix layer, suitable for file-list
     * thumbnails: a smaller target size (width/height capped at {@value #PREVIEW_MAX_DIMENSION_PX}px)
     * and reduced detail — text features are skipped and no component overlays are drawn. Uses the
     * same geometry and viewBox as {@link #renderLayerSvg(Job, String, String)} so the two align.
     */
    public String renderLayerPreviewSvg(Job job, String stepName, String layerName)
            throws IOException {
        StringWriter writer = new StringWriter();
        renderLayerPreviewSvg(job, stepName, layerName, writer);
        return writer.toString();
    }

    /** As {@link #renderLayerPreviewSvg(Job, String, String)}, streaming to a {@link Writer}. */
    public void renderLayerPreviewSvg(Job job, String stepName, String layerName, Writer writer)
            throws IOException {
        renderLayer(job, stepName, layerName, true, writer);
    }

    private void renderLayer(Job job, String stepName, String layerName, boolean preview,
            Writer writer) throws IOException {
        if (job == null || job.getSteps() == null || job.getSteps().isEmpty() || layerName == null) {
            writeEmptySvg(writer);
            return;
        }
        useJob(job);
        Step step = stepName != null ? job.getSteps().get(stepName) : null;
        if (step == null) {
            step = job.getSteps().values().iterator().next();
        }
        renderLayerFromStep(step, job.getMatrix(), layerName, preview, writer);
    }

    /**
     * Render a single named layer of a step to a standalone SVG. Every matrix layer type the
     * multi-layer renderer can draw works through this path — it reuses the same
     * {@link #renderLayerGroup} + {@link #renderFeature} machinery.
     */
    public void renderLayerFromStep(Step step, Matrix matrix, String layerName, boolean preview,
            Writer writer) throws IOException {
        if (step == null || step.getLayersByName() == null || layerName == null) {
            writeEmptySvg(writer);
            return;
        }
        Map.Entry<String, Layer> hit =
                findLayerEntryCaseInsensitive(step.getLayersByName(), layerName.toLowerCase(Locale.ROOT));
        Layer layer = hit != null ? hit.getValue() : null;
        if (layer == null || layer.getFeatures() == null
                || layer.getFeatures().getFeatures().isEmpty()) {
            writeEmptySvg(writer);
            return;
        }

        LayerInfo info = makeLayerInfo(hit.getKey(), layer, matrixLayerFor(matrix, layerName), matrix);
        if (info == null) {
            writeEmptySvg(writer);
            return;
        }

        // Bounds: the whole step (every layer plus the profile), not just this layer. Using the
        // selected layer's own extent would give each layer its own viewBox, so layers whose
        // geometry reaches outside the profile — fabrication/drawing layers carrying dimension
        // annotations beyond the board outline are the usual case — would render at a different
        // scale and offset from the rest and no longer stack. Sharing the step frame keeps every
        // per-layer SVG registered with its siblings and with renderStep's composite.
        resetGlobalBounds();
        calculateGlobalBounds(step.getLayersByName().values());
        if (step.getProfile() != null) {
            calculateProfileBounds(step.getProfile());
        }

        boolean savedPreview = this.previewMode;
        this.previewMode = preview;
        try {
            if (preview) {
                writeSvgHeaderCapped(writer, PREVIEW_MAX_DIMENSION_PX);
            } else {
                writeSvgHeader(writer);
            }
            writeTransformGroup(writer);

            // Faint profile outline for context — full render only. Previews drop it as part
            // of the reduced-detail treatment (along with text), keeping thumbnails lean.
            if (!preview && step.getProfile() != null
                    && !step.getProfile().getFeatures().isEmpty()) {
                renderProfileFromFeatures(step.getProfile(), writer);
            }

            renderLayerGroup(info, writer);

            writeCloseTransformGroup(writer);
            writeSvgFooter(writer);
        } finally {
            this.previewMode = savedPreview;
        }
    }

    private static MatrixLayer matrixLayerFor(Matrix matrix, String layerName) {
        if (matrix == null || matrix.getLayers() == null) return null;
        for (MatrixLayer ml : matrix.getLayers()) {
            if (ml.getName() != null && ml.getName().equalsIgnoreCase(layerName)) return ml;
        }
        return null;
    }

    /**
     * Like {@link #writeSvgHeader}, but clamps the SVG's {@code width}/{@code height} attributes
     * so neither exceeds {@code maxDimensionPx}, preserving aspect ratio. The viewBox (and hence
     * the geometry) is unchanged — only the default rasterised/displayed size shrinks, which is
     * what a preview thumbnail wants.
     */
    private void writeSvgHeaderCapped(Writer writer, double maxDimensionPx) throws IOException {
        double minX = baseOptions.toOutputUnit(globalMinX);
        double minY = baseOptions.toOutputUnit(globalMinY);
        double maxX = baseOptions.toOutputUnit(globalMaxX);
        double maxY = baseOptions.toOutputUnit(globalMaxY);
        double padding = baseOptions.toOutputUnit(baseOptions.getPadding());

        double vbW = (maxX - minX + 2 * padding) * baseOptions.getScale();
        double vbH = (maxY - minY + 2 * padding) * baseOptions.getScale();

        double pixelWidth;
        double pixelHeight;
        double dpi = baseOptions.getDpi();
        if (baseOptions.getOutputUnit() == SvgRenderOptions.OutputUnit.INCH) {
            pixelWidth = vbW * dpi;
            pixelHeight = vbH * dpi;
        } else {
            pixelWidth = (vbW / SvgRenderOptions.INCH_TO_MM) * dpi;
            pixelHeight = (vbH / SvgRenderOptions.INCH_TO_MM) * dpi;
        }
        double longest = Math.max(pixelWidth, pixelHeight);
        if (longest > maxDimensionPx && longest > 0) {
            double factor = maxDimensionPx / longest;
            pixelWidth *= factor;
            pixelHeight *= factor;
        }

        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write(String.format(Locale.US, "<svg xmlns=\"http://www.w3.org/2000/svg\" " +
                        "width=\"%.2f\" height=\"%.2f\" " +
                        "viewBox=\"%.4f %.4f %.4f %.4f\" " +
                        "preserveAspectRatio=\"xMidYMid meet\" " +
                        "stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-width=\"0\" " +
                        "fill-rule=\"nonzero\" data-layers=\"true\" data-preview=\"true\">\n",
                pixelWidth, pixelHeight,
                minX - padding,
                minY - padding,
                maxX - minX + 2 * padding,
                maxY - minY + 2 * padding));
    }

    /**
     * Renders a realistic PCB view for one side (top or bottom).
     *
     * Requires a board profile to define the board boundary. Layers are
     * categorized by their matrix type and context (TOP/BOTTOM).
     *
     * Layer stack (bottom to top):
     * <ol>
     *   <li>FR4 substrate (dark gray, clipped to board outline)</li>
     *   <li>Copper traces/pads (silver/gray)</li>
     *   <li>Copper finish (gold HASL/ENIG, only at soldermask openings)</li>
     *   <li>Soldermask (green, semi-transparent) containing silkscreen</li>
     *   <li>Drill holes punch through all layers via mask</li>
     * </ol>
     */
    public void renderRealisticJob(Job job, boolean topSide, Writer writer) throws IOException {
        if (job == null || job.getSteps() == null || job.getSteps().isEmpty()) {
            writeEmptySvg(writer);
            return;
        }

        useJob(job);

        Step step = job.getSteps().values().iterator().next();
        renderRealisticStep(step, job.getMatrix(), topSide, writer);
    }

    /**
     * Renders a realistic PCB view for one side of a Step.
     *
     * <p>Layer classification uses {@link LayerSideClassifier} which infers
     * side from the matrix row order and name patterns rather than requiring
     * {@code CONTEXT=TOP|BOTTOM} (which real-world archives rarely set — most
     * use {@code CONTEXT=BOARD} for every physical layer).
     */
    public void renderRealisticStep(Step step, Matrix matrix, boolean topSide, Writer writer) throws IOException {
        if (step == null || step.getLayersByName() == null) {
            writeEmptySvg(writer);
            return;
        }

        // Extract board outline path from profile
        String profilePath = extractProfilePath(step.getProfile());
        if (profilePath == null || profilePath.isEmpty()) {
            writeEmptySvg(writer);
            return;
        }

        // Categorize layers by inferred side + type
        LayerSideClassifier classifier = new LayerSideClassifier(matrix);
        LayerSide targetSide = topSide ? LayerSide.TOP : LayerSide.BOTTOM;

        List<LayerInfo> copperLayers = new ArrayList<>();
        List<LayerInfo> soldermaskLayers = new ArrayList<>();
        List<LayerInfo> silkscreenLayers = new ArrayList<>();
        List<LayerInfo> pasteLayers = new ArrayList<>();
        List<LayerInfo> drillLayers = new ArrayList<>();

        Map<String, MatrixLayer> matrixMap = new HashMap<>();
        List<String> matrixOrder = new ArrayList<>();
        if (matrix != null && matrix.getLayers() != null) {
            List<MatrixLayer> sorted = new ArrayList<>(matrix.getLayers());
            sorted.sort(Comparator.comparingInt(MatrixLayer::getRow));
            for (MatrixLayer ml : sorted) {
                String key = ml.getName().toLowerCase(Locale.ROOT);
                matrixMap.put(key, ml);
                matrixOrder.add(key);
            }
        }

        // Iterate in matrix row order so composition (copper → mask → silk) is deterministic.
        // Fall back to step layer iteration for any layers not present in the matrix.
        Set<String> visited = new HashSet<>();
        for (String key : matrixOrder) {
            Layer layer = findLayerCaseInsensitive(step.getLayersByName(), key);
            if (layer == null) continue;
            visited.add(key);
            addLayerToBucket(key, layer, matrixMap.get(key), classifier, targetSide,
                    copperLayers, soldermaskLayers, silkscreenLayers, pasteLayers, drillLayers);
        }
        for (Map.Entry<String, Layer> entry : step.getLayersByName().entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (visited.contains(key)) continue;
            addLayerToBucket(key, entry.getValue(), matrixMap.get(key), classifier, targetSide,
                    copperLayers, soldermaskLayers, silkscreenLayers, pasteLayers, drillLayers);
        }

        // Need at least a profile and one copper or soldermask layer to render usefully
        if (copperLayers.isEmpty() && soldermaskLayers.isEmpty()) {
            writeEmptySvg(writer);
            return;
        }

        // Realistic view is clipped to the board outline, so only the profile's
        // extents matter for the viewBox. Skipping layer bounds avoids inflating
        // the canvas with documentation / dimension layers (e.g. Mentor Xpedition's
        // pcb_manufacturer_info, ddt) whose features sit far outside the PCB.
        resetGlobalBounds();
        if (step.getProfile() != null) {
            calculateProfileBounds(step.getProfile());
        } else {
            // No profile — fall back to layer bounds so we still produce something.
            calculateGlobalBounds(step.getLayersByName().values());
        }

        // Convert bounds to output units
        double minX = baseOptions.toOutputUnit(globalMinX);
        double minY = baseOptions.toOutputUnit(globalMinY);
        double maxX = baseOptions.toOutputUnit(globalMaxX);
        double maxY = baseOptions.toOutputUnit(globalMaxY);
        double padding = baseOptions.toOutputUnit(baseOptions.getPadding());

        double vbX = minX - padding;
        double vbY = minY - padding;
        double vbW = maxX - minX + 2 * padding;
        double vbH = maxY - minY + 2 * padding;

        // Full rect covering viewBox (used for mask backgrounds). Slight oversize to avoid
        // rounding gaps at the edge of the mask.
        String fullRect = String.format(Locale.US, "x=\"%.4f\" y=\"%.4f\" width=\"%.4f\" height=\"%.4f\"",
                vbX - 1, vbY - 1, vbW + 2, vbH + 2);

        // Compute pixel width/height for Batik / browser default sizing
        double pixelWidth;
        double pixelHeight;
        double dpi = baseOptions.getDpi();
        if (baseOptions.getOutputUnit() == SvgRenderOptions.OutputUnit.INCH) {
            pixelWidth = vbW * dpi;
            pixelHeight = vbH * dpi;
        } else {
            pixelWidth = (vbW / SvgRenderOptions.INCH_TO_MM) * dpi;
            pixelHeight = (vbH / SvgRenderOptions.INCH_TO_MM) * dpi;
        }

        // Root SVG
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write(String.format(Locale.US,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" " +
                "width=\"%.2f\" height=\"%.2f\" " +
                "viewBox=\"%.4f %.4f %.4f %.4f\" " +
                "preserveAspectRatio=\"xMidYMid meet\" " +
                "stroke-linecap=\"round\" stroke-linejoin=\"round\" " +
                "fill-rule=\"nonzero\" " +
                "data-view=\"realistic\" data-side=\"%s\">\n",
                pixelWidth, pixelHeight,
                vbX, vbY, vbW, vbH,
                topSide ? "top" : "bottom"));

        // Each layer's artwork is written once, colourless, and placed with <use> wherever
        // the stack needs it: copper is drawn twice (under the mask and as the finish in its
        // openings) and the soldermask layer cuts two masks. Dark features take the fill and
        // stroke of the use, clear ones its 'color'.
        FeaturePainter art = newPainter().compact(true);
        String side = topSide ? "top" : "bottom";
        List<Artwork> copper = artworks(copperLayers, "cu", side, art, true);
        List<Artwork> soldermask = artworks(soldermaskLayers, "sm", side, art, false);
        List<Artwork> silkscreen = artworks(silkscreenLayers, "ss", side, art, false);
        // Only a hole that goes all the way through shows what is behind the board; a blind
        // one ends on an inner layer and is seen as a dimple in its pad.
        List<LayerInfo> throughLayers = new ArrayList<>();
        List<LayerInfo> blindLayers = new ArrayList<>();
        for (LayerInfo info : drillLayers) {
            (classifier.drillThrough(info.name) ? throughLayers : blindLayers).add(info);
        }
        List<Artwork> drills = artworks(throughLayers, "drill", side, art, false);
        List<Artwork> blindDrills = artworks(blindLayers, "blind", side, art, false);

        SideColors sideColors = colorsFor(topSide);
        String smColor = sideColors.mask();
        String ssColor = sideColors.silkscreen();
        boolean hasSoldermask = !soldermask.isEmpty();
        boolean hasDrills = !drills.isEmpty();
        String smMaskId = topSide ? "sm-top-mask" : "sm-bottom-mask";
        String cfMaskId = topSide ? "cf-top-mask" : "cf-bottom-mask";

        writer.write("<defs>\n");

        // Board outline clipPath — used to constrain every composition group to the board shape.
        writer.write("  <clipPath id=\"board-outline\" clipPathUnits=\"userSpaceOnUse\">\n");
        writer.write("    <path d=\"" + profilePath + "\"/>\n");
        writer.write("  </clipPath>\n");

        for (Artwork a : copper) writeArtwork(a, art, writer);
        for (Artwork a : soldermask) writeArtwork(a, art, writer);
        for (Artwork a : drills) writeArtwork(a, art, writer);
        for (Artwork a : blindDrills) writeArtwork(a, art, writer);
        if (ssColor != null) {
            for (Artwork a : silkscreen) {
                if (a.needsMask()) writeArtwork(a, art, writer);
            }
        }

        // A layer with clear features, or one the matrix marks negative, is shown through a
        // mask of its own: white where it leaves material.
        for (Artwork a : copper) {
            if (a.needsMask()) writeLayerMask(a, fullRect, writer);
        }
        if (ssColor != null) {
            for (Artwork a : silkscreen) {
                if (a.needsMask()) writeLayerMask(a, fullRect, writer);
            }
        }

        if (hasSoldermask) {
            // sm-mask: board profile white (mask present = green visible), soldermask
            // features black (mask absent = underlying copper/gold shows through).
            writer.write("  <mask id=\"" + smMaskId + "\" maskContentUnits=\"userSpaceOnUse\">\n");
            writer.write("    <path d=\"" + profilePath + "\" fill=\"white\"/>\n");
            for (Artwork a : soldermask) {
                writer.write(useArtwork(a, "black", "white"));
            }
            writer.write("  </mask>\n");

            // cf-mask: inverse — black background + soldermask features white, so the
            // copper finish is visible only through pad openings.
            writer.write("  <mask id=\"" + cfMaskId + "\" maskContentUnits=\"userSpaceOnUse\">\n");
            writer.write("    <rect " + fullRect + " fill=\"black\"/>\n");
            for (Artwork a : soldermask) {
                writer.write(useArtwork(a, "white", "black"));
            }
            writer.write("  </mask>\n");
        }

        // mech-mask: white background punched by the through holes and routed cut-outs (black).
        if (hasDrills) {
            writer.write("  <mask id=\"mech-mask\" maskContentUnits=\"userSpaceOnUse\">\n");
            writer.write("    <rect " + fullRect + " fill=\"white\"/>\n");
            for (Artwork a : drills) {
                writer.write(useArtwork(a, "black", "white"));
            }
            writer.write("  </mask>\n");
        }

        writer.write("</defs>\n");

        // Viewport with Y-flip (and X-mirror for bottom view so we look at the board from behind).
        double centerY = (maxY + minY) / 2;
        double centerX = (maxX + minX) / 2;

        String transform;
        if (!topSide) {
            transform = String.format(Locale.US, "scale(-1,-1) translate(%.4f,%.4f)", -2 * centerX, -2 * centerY);
        } else {
            transform = String.format(Locale.US, "scale(1,-1) translate(0,%.4f)", -2 * centerY);
        }
        writer.write(String.format(Locale.US, "<g id=\"viewport\" transform=\"%s\" stroke-width=\"0\">\n", transform));

        // Board group: clip to outline, punch drill holes via mask
        String clipAttr = " clip-path=\"url(#board-outline)\"";
        if (hasDrills) {
            writer.write(String.format("  <g id=\"board\" mask=\"url(#mech-mask)\"%s>\n", clipAttr));
        } else {
            writer.write(String.format("  <g id=\"board\"%s>\n", clipAttr));
        }

        // 1. FR4 substrate background
        writer.write("    <g data-stack-layer=\"substrate\">\n");
        writer.write(String.format("      <rect %s fill=\"%s\"/>\n", fullRect, FR4_COLOR));
        writer.write("    </g>\n");

        // 2. Copper. Under a soldermask it is the bare metal seen through the mask; a side
        // without any soldermask layer is exposed everywhere and carries the finish.
        writer.write("    <g data-stack-layer=\"copper\">\n");
        writeStackLayers(copper, hasSoldermask ? COPPER_COLOR : COPPER_FINISH_COLOR, null, fullRect, writer);
        writer.write("    </g>\n");

        // 3. Copper finish (gold HASL/ENIG) — visible only where mask has openings (pad windows)
        if (hasSoldermask && !copper.isEmpty()) {
            writer.write(String.format(
                    "    <g data-stack-layer=\"copper-finish\" mask=\"url(#%s)\">\n", cfMaskId));
            writeStackLayers(copper, COPPER_FINISH_COLOR, null, fullRect, writer);
            writer.write("    </g>\n");
        }

        // Blind holes drilled from this side: the barrel is in shadow.
        if (!blindDrills.isEmpty()) {
            writer.write(String.format(Locale.US,
                    "    <g data-stack-layer=\"blind-holes\" fill=\"%s\" stroke=\"%s\" stroke-width=\"0\" opacity=\"%.2f\">\n",
                    BLIND_HOLE_COLOR, BLIND_HOLE_COLOR, BLIND_HOLE_OPACITY));
            for (Artwork a : blindDrills) {
                writer.write("      <use href=\"#" + a.id + "\"/>\n");
            }
            writer.write("    </g>\n");
        }

        // 4. Soldermask (semi-transparent, caller-configurable colour) with silkscreen nested
        // inside so silk is only visible over the mask, never over exposed pads. The mask and
        // legend colours come from the side's SideColors; either may be null to leave that
        // finish off the board (SoldermaskColor.NONE / SilkscreenColor.NONE).
        boolean hasLegend = ssColor != null && !silkscreen.isEmpty();
        if (hasSoldermask && (smColor != null || hasLegend)) {
            writer.write(String.format(
                    "    <g data-stack-layer=\"soldermask\" mask=\"url(#%s)\">\n", smMaskId));

            // Soldermask fill. The pcb-soldermask class lets a viewer recolor the mask
            // client-side without a server round-trip. Skipped for a board ordered bare.
            if (smColor != null) {
                writer.write(String.format(Locale.US,
                        "      <rect class=\"pcb-soldermask\" %s fill=\"%s\" opacity=\"%.2f\"/>\n",
                        fullRect, smColor, SOLDERMASK_OPACITY));
            }

            // Silkscreen inside the soldermask. A side ordered without a legend prints none,
            // whatever silkscreen files it ships. The pcb-silkscreen class marks the group so
            // a viewer can recolor it when the soldermask color changes.
            if (hasLegend) {
                writeStackLayers(silkscreen, ssColor, art, fullRect, writer);
            }

            writer.write("    </g>\n");
        } else if (!hasSoldermask && hasLegend) {
            // No soldermask layer on this side: the legend is printed straight onto the board.
            writeStackLayers(silkscreen, ssColor, art, fullRect, writer);
        }

        writer.write("  </g>\n"); // close board group
        writer.write("</g>\n"); // close viewport
        writer.write("</svg>\n");
    }

    /** One layer's drawing in the realistic view, defined once and referenced by id. */
    private static final class Artwork {
        LayerInfo info;
        String id;
        /** The layer draws clear features, which have to erase within the layer only. */
        boolean clear;
        /** The matrix marks the layer negative: its features are where material is absent. */
        boolean negative;

        boolean needsMask() {
            return clear || negative;
        }
    }

    private List<Artwork> artworks(List<LayerInfo> layers, String kind, String side,
                                   FeaturePainter art, boolean honourMatrixPolarity) {
        List<Artwork> result = new ArrayList<>();
        for (LayerInfo info : layers) {
            Artwork a = new Artwork();
            a.info = info;
            a.id = "art-" + side + "-" + kind + "-" + result.size();
            a.clear = art.hasClear(info.layer.getFeatures());
            a.negative = honourMatrixPolarity && info.matrixLayer != null
                    && "NEGATIVE".equalsIgnoreCase(info.matrixLayer.getPolarity());
            result.add(a);
        }
        return result;
    }

    private void writeArtwork(Artwork a, FeaturePainter art, Writer writer) throws IOException {
        writer.write("  <g id=\"" + a.id + "\">\n");
        art.paint(a.info.layer.getFeatures(), writer, Palette.INHERIT);
        writer.write("  </g>\n");
    }

    /** A reference to a layer's artwork, dark features in one colour and clear ones in another. */
    private static String useArtwork(Artwork a, String dark, String clear) {
        return "    <use href=\"#" + a.id + "\" fill=\"" + dark + "\" stroke=\"" + dark
                + "\" stroke-width=\"0\" color=\"" + clear + "\"/>\n";
    }

    private void writeLayerMask(Artwork a, String fullRect, Writer writer) throws IOException {
        writer.write("  <mask id=\"" + a.id + "-mask\" maskContentUnits=\"userSpaceOnUse\">\n");
        if (a.negative) {
            writer.write("    <rect " + fullRect + " fill=\"white\"/>\n");
            writer.write(useArtwork(a, "black", "white"));
        } else {
            writer.write(useArtwork(a, "white", "black"));
        }
        writer.write("  </mask>\n");
    }

    /**
     * Writes one group per layer in the given colour. The copper layers reference their
     * artwork; silkscreen (inline != null) carries its artwork in the group itself, tagged so
     * a viewer can recolour the legend. A layer that needs a mask is a rectangle seen
     * through it.
     */
    private void writeStackLayers(List<Artwork> layers, String color, FeaturePainter inline,
                                  String fullRect, Writer writer) throws IOException {
        for (Artwork a : layers) {
            String tag = inline != null ? "class=\"pcb-silkscreen\" data-stack-layer=\"silkscreen\" " : "";
            writer.write(String.format(
                    "      <g %sdata-layer-name=\"%s\" fill=\"%s\" stroke=\"%s\" stroke-width=\"0\">\n",
                    tag, escapeAttr(a.info.name), color, color));
            if (a.needsMask()) {
                writer.write("        <rect " + fullRect + " mask=\"url(#" + a.id + "-mask)\"/>\n");
            } else if (inline != null) {
                inline.paint(a.info.layer.getFeatures(), writer, Palette.INHERIT);
            } else {
                writer.write("        <use href=\"#" + a.id + "\"/>\n");
            }
            writer.write("      </g>\n");
        }
    }

    /**
     * Adds a step layer to the appropriate realistic-render bucket based on its matrix
     * type and inferred side. Called both for matrix-ordered iteration and for any
     * step layers that are not present in the matrix.
     */
    private void addLayerToBucket(
            String lowerName,
            Layer layer,
            MatrixLayer ml,
            LayerSideClassifier classifier,
            LayerSide targetSide,
            List<LayerInfo> copperLayers,
            List<LayerInfo> soldermaskLayers,
            List<LayerInfo> silkscreenLayers,
            List<LayerInfo> pasteLayers,
            List<LayerInfo> drillLayers) {

        if (ml == null) return;
        Features features = layer.getFeatures();
        if (features == null || features.getFeatures().isEmpty()) return;

        String type = ml.getType() == null ? "" : ml.getType().toUpperCase(Locale.ROOT);

        LayerInfo info = new LayerInfo();
        info.name = ml.getName() != null ? ml.getName() : lowerName;
        info.layer = layer;
        info.matrixLayer = ml;
        info.type = type;

        if ("DRILL".equals(type) || "ROUT".equals(type)) {
            if (classifier.drillVisibleFrom(info.name, targetSide)) {
                drillLayers.add(info);
            }
            return;
        }

        if (classifier.sideOf(info.name) != targetSide) return;

        if ("SIGNAL".equals(type) || "POWER_GROUND".equals(type) || "MIXED".equals(type)) {
            copperLayers.add(info);
        } else if ("SOLDER_MASK".equals(type)) {
            soldermaskLayers.add(info);
        } else if ("SILK_SCREEN".equals(type)) {
            silkscreenLayers.add(info);
        } else if ("SOLDER_PASTE".equals(type)) {
            pasteLayers.add(info);
        }
    }

    private static Layer findLayerCaseInsensitive(Map<String, Layer> layers, String lowerKey) {
        for (Map.Entry<String, Layer> e : layers.entrySet()) {
            if (e.getKey().toLowerCase(Locale.ROOT).equals(lowerKey)) return e.getValue();
        }
        return null;
    }

    private static String escapeAttr(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    // ------------------------------------------------------------------
    // Components view — silhouette of the board with package footprints
    // ------------------------------------------------------------------

    // Colors used by the components view
    private static final String COMP_BOARD_STROKE   = "#888888";
    private static final String COMP_BOARD_FILL     = "#1f1f1f";
    private static final String COMP_TOP_FILL       = "#6aaed6";
    private static final String COMP_TOP_STROKE     = "#2d79a3";
    private static final String COMP_BOTTOM_FILL    = "#e89654";
    private static final String COMP_BOTTOM_STROKE  = "#a9651f";
    private static final String COMP_LABEL_COLOR    = "#ffffff";

    /** Render only the components of a single side; convenience wrapper over Step/Matrix. */
    public void renderComponentsJob(Job job, boolean topSide, Writer writer) throws IOException {
        if (job == null || job.getSteps() == null || job.getSteps().isEmpty()) {
            writeEmptySvg(writer);
            return;
        }
        useJob(job);
        Step step = job.getSteps().values().iterator().next();
        renderComponentsStep(step, job.getMatrix(), topSide, writer);
    }

    /**
     * Render a top-down silhouette view of one side of the board: outline in
     * gray, component packages as rotated rectangles (from their EDA bounding
     * box), each labeled with its reference designator.
     *
     * <p>Complements {@link #renderRealisticStep(Step, Matrix, boolean, Writer)}
     * for the "Components" tab in the viewer.
     */
    public void renderComponentsStep(Step step, Matrix matrix, boolean topSide, Writer writer) throws IOException {
        if (step == null || step.getLayersByName() == null) {
            writeEmptySvg(writer);
            return;
        }
        String profilePath = extractProfilePath(step.getProfile());
        if (profilePath == null || profilePath.isEmpty()) {
            writeEmptySvg(writer);
            return;
        }

        LayerSideClassifier classifier = new LayerSideClassifier(matrix);
        LayerSide targetSide = topSide ? LayerSide.TOP : LayerSide.BOTTOM;

        // Collect components from layers on the matching side (typically comp_+_top / comp_+_bot).
        List<Component> components = new ArrayList<>();
        for (Map.Entry<String, Layer> entry : step.getLayersByName().entrySet()) {
            LayerSide side = classifier.sideOf(entry.getKey());
            if (side != targetSide) continue;
            Layer layer = entry.getValue();
            if (layer.getComponents() == null) continue;
            List<Component> list = layer.getComponents().getComponents();
            if (list != null) components.addAll(list);
        }

        // Index packages by their sequential order (Component.pkgRef is 0-based).
        List<EdaData.PackageRecord> packages = step.getEdaData() != null
                ? step.getEdaData().getPackageRecords()
                : null;

        // Components view is clipped to the board outline; use profile bounds only.
        resetGlobalBounds();
        if (step.getProfile() != null) {
            calculateProfileBounds(step.getProfile());
        } else {
            calculateGlobalBounds(step.getLayersByName().values());
        }

        double minX = baseOptions.toOutputUnit(globalMinX);
        double minY = baseOptions.toOutputUnit(globalMinY);
        double maxX = baseOptions.toOutputUnit(globalMaxX);
        double maxY = baseOptions.toOutputUnit(globalMaxY);
        double padding = baseOptions.toOutputUnit(baseOptions.getPadding());

        double vbX = minX - padding;
        double vbY = minY - padding;
        double vbW = maxX - minX + 2 * padding;
        double vbH = maxY - minY + 2 * padding;

        double dpi = baseOptions.getDpi();
        double pixelWidth = baseOptions.getOutputUnit() == SvgRenderOptions.OutputUnit.INCH
                ? vbW * dpi
                : (vbW / SvgRenderOptions.INCH_TO_MM) * dpi;
        double pixelHeight = baseOptions.getOutputUnit() == SvgRenderOptions.OutputUnit.INCH
                ? vbH * dpi
                : (vbH / SvgRenderOptions.INCH_TO_MM) * dpi;

        String fill = topSide ? COMP_TOP_FILL : COMP_BOTTOM_FILL;
        String stroke = topSide ? COMP_TOP_STROKE : COMP_BOTTOM_STROKE;

        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write(String.format(Locale.US,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" " +
                        "width=\"%.2f\" height=\"%.2f\" " +
                        "viewBox=\"%.4f %.4f %.4f %.4f\" " +
                        "preserveAspectRatio=\"xMidYMid meet\" " +
                        "data-view=\"components\" data-side=\"%s\">\n",
                pixelWidth, pixelHeight, vbX, vbY, vbW, vbH,
                topSide ? "top" : "bottom"));

        writer.write("<defs>\n");
        writer.write("  <clipPath id=\"board-outline\" clipPathUnits=\"userSpaceOnUse\">\n");
        writer.write(String.format("    <path d=\"%s\"/>\n", profilePath));
        writer.write("  </clipPath>\n");
        writer.write("</defs>\n");

        double centerX = (maxX + minX) / 2;
        double centerY = (maxY + minY) / 2;
        String transform = topSide
                ? String.format(Locale.US, "scale(1,-1) translate(0,%.4f)", -2 * centerY)
                : String.format(Locale.US, "scale(-1,-1) translate(%.4f,%.4f)", -2 * centerX, -2 * centerY);
        writer.write(String.format(Locale.US, "<g id=\"viewport\" transform=\"%s\" stroke-width=\"0\">\n", transform));

        // Board silhouette
        writer.write(String.format(Locale.US,
                "  <path d=\"%s\" fill=\"%s\" stroke=\"%s\" stroke-width=\"0.01\" " +
                        "vector-effect=\"non-scaling-stroke\" data-stack-layer=\"board-outline\"/>\n",
                profilePath, COMP_BOARD_FILL, COMP_BOARD_STROKE));

        // Components group
        writer.write("  <g data-stack-layer=\"components\">\n");

        // Label font size, scaled relative to the viewBox — smaller boards get smaller labels.
        double labelSize = Math.max(vbW, vbH) * 0.012;

        int packageFallbackCount = 0;
        for (Component c : components) {
            double cx = baseOptions.toOutputUnit(c.getX());
            double cy = baseOptions.toOutputUnit(c.getY());
            boolean mirrored = c.getMirror() == MirrorType.MIRRORED;

            // Silhouette: the EDA package outline when the writer gave one, else the package
            // bounding box at its own offsets from the placement origin, else a default stub.
            // The outline comes first because some writers (KiCad) grow the bounding box to
            // cover the footprint's reference/value text, making parts look several times
            // their real size.
            String shape;
            EdaData.PackageRecord pkg = lookupPackage(packages, c.getPkgRef());
            if (pkg != null && pkg.hasOutline()) {
                shape = String.format(Locale.US,
                        "<path d=\"%s\" fill=\"%s\" fill-rule=\"evenodd\" stroke=\"%s\" " +
                                "stroke-width=\"0.5\" vector-effect=\"non-scaling-stroke\" " +
                                "data-outline=\"contour\"/>",
                        contourPathData(pkg.getOutline()), fill, stroke);
            } else if (pkg != null && pkg.getWidth() > 0 && pkg.getHeight() > 0) {
                double x0 = baseOptions.toOutputUnit(pkg.getXMin());
                double y0 = baseOptions.toOutputUnit(pkg.getYMin());
                double w = baseOptions.toOutputUnit(pkg.getWidth());
                double h = baseOptions.toOutputUnit(pkg.getHeight());
                shape = String.format(Locale.US,
                        "<rect x=\"%.4f\" y=\"%.4f\" width=\"%.4f\" height=\"%.4f\" " +
                                "fill=\"%s\" stroke=\"%s\" stroke-width=\"0.5\" " +
                                "vector-effect=\"non-scaling-stroke\" data-outline=\"bbox\"/>",
                        x0, y0, w, h, fill, stroke);
            } else {
                double halfW = vbW * 0.01;
                double halfH = vbH * 0.012;
                packageFallbackCount++;
                shape = String.format(Locale.US,
                        "<rect x=\"%.4f\" y=\"%.4f\" width=\"%.4f\" height=\"%.4f\" " +
                                "fill=\"%s\" stroke=\"%s\" stroke-width=\"0.5\" " +
                                "vector-effect=\"non-scaling-stroke\" data-outline=\"fallback\"/>",
                        -halfW, -halfH, 2 * halfW, 2 * halfH, fill, stroke);
            }

            String compTransform = String.format(Locale.US,
                    "translate(%.4f %.4f) rotate(%.2f)%s",
                    cx, cy, c.getRotation(),
                    mirrored ? " scale(-1,1)" : "");

            writer.write(String.format(Locale.US,
                    "    <g transform=\"%s\" data-refdes=\"%s\" data-package-ref=\"%d\">\n",
                    compTransform,
                    escapeAttr(c.getCompName() == null ? "" : c.getCompName()),
                    c.getPkgRef()));
            writer.write("      " + shape + "\n");
            writer.write("    </g>\n");

            // Refdes label — separate sibling group, outside the rotation/mirror transform so
            // text stays upright. The label undoes the viewport's flip: Y only on the top side,
            // X and Y on the bottom, whose viewport also mirrors X so the board reads as seen
            // from behind. Either way the text lands on the component and reads left to right.
            String refdes = c.getCompName();
            if (refdes != null && !refdes.isEmpty()) {
                String unflip = topSide ? "scale(1,-1)" : "scale(-1,-1)";
                double lx = topSide ? cx : -cx;
                writer.write(String.format(Locale.US,
                        "    <text x=\"%.4f\" y=\"%.4f\" font-size=\"%.4f\" fill=\"%s\" " +
                                "text-anchor=\"middle\" font-family=\"sans-serif\" " +
                                "transform=\"%s\" " +
                                "data-refdes-label=\"%s\">%s</text>\n",
                        lx, -cy, labelSize, COMP_LABEL_COLOR, unflip,
                        escapeAttr(refdes), escapeAttr(refdes)));
            }
        }

        writer.write("  </g>\n"); // components
        writer.write("</g>\n"); // viewport
        writer.write("</svg>\n");

        if (packageFallbackCount > 0) {
            // Not a hard failure — EDA data is optional. Leaving a hint in the SVG's
            // comment-like log helps debugging when components look wrong.
            writer.write("<!-- components-render: " + packageFallbackCount
                    + " component(s) used fallback outline -->\n");
        }
    }

    private static EdaData.PackageRecord lookupPackage(List<EdaData.PackageRecord> packages, int pkgRef) {
        if (packages == null || pkgRef < 0 || pkgRef >= packages.size()) return null;
        return packages.get(pkgRef);
    }

    /**
     * SVG path data for a list of contour polygons in model coordinates (mm), converted to the
     * output unit. Islands and holes go into one path so an evenodd fill cuts the holes out.
     * Arcs carry a proper large-arc flag, computed from the swept angle about the centre.
     */
    private String contourPathData(List<ContourPolygon> polygons) {
        StringBuilder d = new StringBuilder();
        for (ContourPolygon polygon : polygons) {
            double px = polygon.getXStart();
            double py = polygon.getYStart();
            d.append(String.format(Locale.US, "M %.4f %.4f",
                    baseOptions.toOutputUnit(px), baseOptions.toOutputUnit(py)));
            for (ContourPolygon.PolygonPart part : polygon.getPolygonParts()) {
                double ex = part.getEndX();
                double ey = part.getEndY();
                if (part.getType() == ContourPolygon.PolygonPart.Type.ARC) {
                    double cx = part.getXCenter();
                    double cy = part.getYCenter();
                    double r = Math.hypot(ex - cx, ey - cy);
                    double a0 = Math.atan2(py - cy, px - cx);
                    double a1 = Math.atan2(ey - cy, ex - cx);
                    // Swept angle in the direction of travel (model frame, Y up).
                    double swept = part.isClockwise() ? a0 - a1 : a1 - a0;
                    swept = ((swept % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
                    int largeArc = swept > Math.PI ? 1 : 0;
                    // The viewport flips Y, so a clockwise model arc is drawn with sweep 0.
                    int sweep = part.isClockwise() ? 0 : 1;
                    if (px == ex && py == ey && r > 0) {
                        // Equal end points are a full circle: go by way of the opposite point.
                        d.append(String.format(Locale.US, " A %.4f %.4f 0 1 %d %.4f %.4f",
                                baseOptions.toOutputUnit(r), baseOptions.toOutputUnit(r), sweep,
                                baseOptions.toOutputUnit(2 * cx - px), baseOptions.toOutputUnit(2 * cy - py)));
                        largeArc = 1;
                    }
                    d.append(String.format(Locale.US, " A %.4f %.4f 0 %d %d %.4f %.4f",
                            baseOptions.toOutputUnit(r), baseOptions.toOutputUnit(r), largeArc, sweep,
                            baseOptions.toOutputUnit(ex), baseOptions.toOutputUnit(ey)));
                } else {
                    d.append(String.format(Locale.US, " L %.4f %.4f",
                            baseOptions.toOutputUnit(ex), baseOptions.toOutputUnit(ey)));
                }
                px = ex;
                py = ey;
            }
            d.append(" Z");
        }
        return d.toString();
    }

    // ------------------------------------------------------------------
    // Realistic PNG rasterisation (Apache Batik)
    // ------------------------------------------------------------------

    /**
     * Renders a realistic side view of the Job and rasterises it to PNG.
     *
     * @param job      the ODB++ job
     * @param topSide  true for the top view, false for the bottom view
     * @param widthPx  target width in pixels; pass {@code 0} to derive from height
     * @param heightPx target height in pixels; pass {@code 0} to derive from width
     * @return PNG-encoded bytes
     * @throws IllegalStateException if the job has no profile or no renderable layers
     */
    public byte[] renderRealisticSidePng(Job job, boolean topSide, int widthPx, int heightPx) throws IOException {
        StringWriter svgWriter = new StringWriter();
        renderRealisticJob(job, topSide, svgWriter);
        String svg = svgWriter.toString();
        if (svg.isBlank() || svg.contains("<!-- Empty design -->")) {
            throw new IllegalStateException(
                    "Cannot rasterise: realistic " + (topSide ? "top" : "bottom") +
                            " view produced no content");
        }
        return rasterizeSvgToPng(svg, widthPx, heightPx);
    }

    /** Width-only convenience — height is derived from the SVG's aspect ratio. */
    public byte[] renderRealisticSidePng(Job job, boolean topSide, int widthPx) throws IOException {
        return renderRealisticSidePng(job, topSide, widthPx, 0);
    }

    /**
     * A rasterised realistic-view PNG together with the geometry needed to map between its
     * pixels and the board's real-world millimetres. Field semantics mirror delta-gerber's
     * {@code MultiLayerSVGRenderer.PngWithScale} so app code can treat an ODB++ render and a
     * Gerber render uniformly.
     *
     * <p>The image covers exactly the {@linkplain #minXmm mm rectangle} {@code [minXmm, minYmm,
     * widthMm, heightMm]} — the realistic view's viewBox, i.e. the board profile plus padding.
     * Because the PNG is fitted with {@code preserveAspectRatio="xMidYMid meet"}, the board is
     * scaled uniformly by {@link #pxPerMm} and centred, occupying the pixel rectangle
     * {@code [contentOffsetXpx, contentOffsetYpx, contentWidthPx, contentHeightPx]}. For an
     * aspect-matched PNG (the usual single-dimension call) the offsets are 0 and the content
     * fills the image.
     *
     * <p>The {@code mmRect} is in <em>ODB++ board coordinate space</em> (millimetres, Y-up, the
     * native datum). Image pixels are top-left origin, Y-down; the realistic view always flips Y
     * and the bottom side additionally mirrors X — so the ODB++ datum {@code (0,0)} is not at a
     * fixed pixel. Its actual location is precomputed as {@link #originXpx}/{@link #originYpx}:
     * <pre>
     *   pxX = originXpx + gxMm * pxPerMm * (mirrored ? -1 : +1);
     *   pxY = originYpx - gyMm * pxPerMm;   // minus: ODB++ Y-up vs pixel Y-down
     * </pre>
     *
     * <p><b>Units:</b> the mm-rectangle and {@link #pxPerMm} are always true millimetres,
     * regardless of the renderer's configured {@link SvgRenderOptions.OutputUnit}. The model is
     * mm-native at parse time and the output unit only scales the SVG coordinate space; this class
     * converts the viewBox extent back to mm so the mm-named fields never carry inch values. The
     * semantics match delta-gerber's {@code PngWithScale}.
     */
    public static final class PngWithScale {
        /** PNG bytes. */
        public final byte[] png;
        /** Actual pixel dimensions of the PNG. */
        public final int widthPx, heightPx;
        /** The mm rectangle the image covers, in ODB++ board space (Y-up): profile bounds
         *  plus padding. */
        public final double minXmm, minYmm, widthMm, heightMm;
        /** Uniform scale Batik applied (pixels per millimetre). */
        public final double pxPerMm;
        /** Pixel rectangle the board content occupies inside the PNG (letterbox-aware). */
        public final double contentOffsetXpx, contentOffsetYpx, contentWidthPx, contentHeightPx;
        /** Pixel location of the ODB++ datum (0,0), Y-flip- and mirror-aware. */
        public final double originXpx, originYpx;
        /** Which board side this image shows. */
        public final Side side;
        /** Whether the X axis was mirrored (bottom shown as the real physical underside). */
        public final boolean mirrored;

        PngWithScale(byte[] png, int widthPx, int heightPx,
                     double minXmm, double minYmm, double widthMm, double heightMm,
                     Side side, boolean mirrored) {
            this.png = png;
            this.side = side;
            this.mirrored = mirrored;
            this.widthPx = widthPx;
            this.heightPx = heightPx;
            this.minXmm = minXmm;
            this.minYmm = minYmm;
            this.widthMm = widthMm;
            this.heightMm = heightMm;
            // xMidYMid meet: uniform scale is the smaller of the two axis ratios; the
            // shorter axis is letterboxed (centred) within the PNG.
            double sx = widthMm  > 0 ? widthPx  / widthMm  : 0;
            double sy = heightMm > 0 ? heightPx / heightMm : 0;
            this.pxPerMm = (sx > 0 && sy > 0) ? Math.min(sx, sy) : Math.max(sx, sy);
            this.contentWidthPx  = widthMm  * pxPerMm;
            this.contentHeightPx = heightMm * pxPerMm;
            this.contentOffsetXpx = (widthPx  - contentWidthPx)  / 2.0;
            this.contentOffsetYpx = (heightPx - contentHeightPx) / 2.0;
            // Pixel position of the ODB++ datum (0,0). The realistic view always flips Y, so
            // the image top is board maxY and pixel-Y runs downward from there; the bottom side
            // additionally mirrors X about the rect's centre.
            this.originXpx = contentOffsetXpx
                + (mirrored ? (minXmm + widthMm) : -minXmm) * pxPerMm;
            this.originYpx = contentOffsetYpx
                + (minYmm + heightMm) * pxPerMm;
        }

        /** Millimetres per pixel — convenience inverse of {@link #pxPerMm}. */
        public double mmPerPx() { return pxPerMm > 0 ? 1.0 / pxPerMm : 0; }
    }

    /**
     * Render a realistic side view to PNG and return it together with the px↔mm scale and the
     * mm rectangle it covers, so a caller can overlay real-world measurements (e.g. a 10 mm grid
     * behind the board). Same rendering as {@link #renderRealisticSidePng(Job, boolean, int, int)}.
     *
     * @param job      the ODB++ job
     * @param topSide  true for the top view, false for the bottom view
     * @param widthPx  target width in pixels, or {@code <= 0} to derive from height
     * @param heightPx target height in pixels, or {@code <= 0} to derive from width
     * @return a {@link PngWithScale}
     * @throws IllegalArgumentException if both dimensions are {@code <= 0}
     * @throws IllegalStateException    if the job has no profile or no renderable layers
     */
    public PngWithScale renderRealisticSidePngWithScale(Job job, boolean topSide,
            int widthPx, int heightPx) throws IOException {
        if (widthPx <= 0 && heightPx <= 0) {
            throw new IllegalArgumentException(
                    "At least one of widthPx/heightPx must be positive");
        }

        StringWriter svgWriter = new StringWriter();
        renderRealisticJob(job, topSide, svgWriter);
        String svg = svgWriter.toString();
        if (svg.isBlank() || svg.contains("<!-- Empty design -->")) {
            throw new IllegalStateException(
                    "Cannot rasterise: realistic " + (topSide ? "top" : "bottom") +
                            " view produced no content");
        }

        double[] vb = parseViewBox(svg);
        // Derive the missing dimension from the viewBox so the PNG's aspect ratio matches the
        // board's extent. Passing both dimensions explicitly avoids ambiguity in how Batik
        // resolves a single KEY_WIDTH/KEY_HEIGHT hint, keeping the reported scale exact.
        if (widthPx <= 0 || heightPx <= 0) {
            // Only one dimension was given; the other must be derived from the viewBox aspect. A
            // degenerate viewBox (missing, or zero width/height for an empty/point-like board)
            // leaves the missing dimension 0, which Batik rejects with an opaque error — fail fast
            // with a clear message instead.
            if (vb == null || vb[2] <= 0 || vb[3] <= 0) {
                throw new IllegalStateException("board has no renderable extent (viewBox "
                        + (vb != null ? vb[2] + "x" + vb[3] : "absent") + ")");
            }
            double aspect = vb[2] / vb[3];
            if (widthPx <= 0) {
                widthPx = (int) Math.max(1, Math.round(heightPx * aspect));
            }
            if (heightPx <= 0) {
                heightPx = (int) Math.max(1, Math.round(widthPx / aspect));
            }
        }

        byte[] png = rasterizeSvgToPng(svg, widthPx, heightPx);

        // The viewBox is expressed in the renderer's configured output unit, not necessarily
        // mm: MM output leaves board coordinates as-is, INCH output divides them by 25.4. The
        // model itself is mm-native at parse time, so we scale the viewBox extent back to true
        // millimetres per unit to keep PngWithScale's mm fields honest whatever the OutputUnit.
        double toMm = outputUnitToMm();
        double minXmm  = (vb != null ? vb[0] : 0) * toMm;
        double minYmm  = (vb != null ? vb[1] : 0) * toMm;
        double widthMm  = (vb != null ? vb[2] : 0) * toMm;
        double heightMm = (vb != null ? vb[3] : 0) * toMm;
        Side side = topSide ? Side.TOP : Side.BOTTOM;
        boolean mirrored = !topSide; // bottom view is X-mirrored (physical underside)
        PngWithScale result = new PngWithScale(png, widthPx, heightPx,
                minXmm, minYmm, widthMm, heightMm, side, mirrored);
        // Make the PNG self-describing: embed the scale (pHYs) and full mm geometry +
        // datum-origin + side (tEXt), mirroring delta-gerber so a consumer reads either
        // library's render through the identical PNG-chunk contract.
        byte[] withMeta = embedScaleMetadata(png, result.pxPerMm, minXmm, minYmm,
                widthMm, heightMm, result.contentOffsetXpx, result.contentOffsetYpx,
                result.originXpx, result.originYpx, side, mirrored);
        if (withMeta == png) return result; // not a PNG (shouldn't happen) — return as-is
        return new PngWithScale(withMeta, widthPx, heightPx,
                minXmm, minYmm, widthMm, heightMm, side, mirrored);
    }

    /**
     * Render the realistic top/bottom view as a <em>self-describing transparent PNG</em> and
     * write it to {@code out}. A drop-in for delta-gerber's realistic-PNG path: the image
     * carries its px↔mm scale and datum origin in standard PNG chunks written before the first
     * {@code IDAT}, so a consumer can draw a real-world grid and align overlays without any
     * ODB-specific handling.
     *
     * <p>The rendering (colours, layer stack, silk/mask, board extent) is identical to
     * {@link #renderRealisticJob(Job, boolean, Writer)}: the PNG covers exactly that view's
     * {@code viewBox}. The board geometry is opaque; the margin around the outline is fully
     * transparent (alpha 0).
     *
     * <p>Metadata embedded (before {@code IDAT}, matching delta-gerber):
     * <ol>
     *   <li>{@code pHYs} — unit = 1 (metre), pixelsPerUnit = {@code round(pxPerMm * 1000)};</li>
     *   <li>{@code tEXt} {@code side} — {@code "top"} or {@code "bottom"};</li>
     *   <li>{@code tEXt} {@code pxPerMm} — the px-per-mm ratio as a decimal string;</li>
     *   <li>{@code tEXt} {@code boardGeometryMm} — compact JSON with {@code mmRect} (== the SVG
     *       {@code viewBox}), {@code pxPerMm}, {@code originPx} and {@code mirrored}.</li>
     * </ol>
     *
     * @param job     the ODB++ job
     * @param topSide true for the top view, false for the bottom view (X-mirrored, {@code
     *                mirrored:true})
     * @param widthPx target width in pixels; the height follows the board's aspect ratio
     * @param out     the stream the PNG bytes are written to (not closed by this method)
     * @throws IllegalArgumentException if {@code widthPx <= 0}
     * @throws IllegalStateException    if the job has no profile or no renderable layers
     */
    public void renderRealisticJobPng(Job job, boolean topSide, int widthPx, OutputStream out)
            throws IOException {
        if (widthPx <= 0) {
            throw new IllegalArgumentException("widthPx must be positive");
        }
        PngWithScale r = renderRealisticSidePngWithScale(job, topSide, widthPx, 0);
        out.write(r.png);
    }

    /**
     * The factor that converts a value expressed in the renderer's configured output unit back
     * to millimetres: {@code 1.0} for {@code MM}, {@code 25.4} for {@code INCH}. The viewBox and
     * scale live in output units, but {@link PngWithScale}'s mm fields must always be true mm.
     */
    private double outputUnitToMm() {
        return baseOptions.getOutputUnit() == SvgRenderOptions.OutputUnit.MM
                ? 1.0
                : SvgRenderOptions.INCH_TO_MM;
    }

    /**
     * Parse the {@code viewBox="minX minY width height"} of the first {@code <svg>} tag.
     * Returns {@code null} when absent or malformed.
     */
    private static double[] parseViewBox(String svg) {
        int i = svg.indexOf("viewBox=\"");
        if (i < 0) return null;
        int start = i + "viewBox=\"".length();
        int end = svg.indexOf('"', start);
        if (end < 0) return null;
        String[] parts = svg.substring(start, end).trim().split("\\s+");
        if (parts.length != 4) return null;
        try {
            return new double[] {
                Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]), Double.parseDouble(parts[3])
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Rasterise an SVG string to a PNG. Either dimension can be {@code <= 0}
     * to mean "derive from the other via aspect ratio". The SVG's
     * {@code preserveAspectRatio} setting controls fitting behaviour when
     * both dimensions are given.
     */
    public static byte[] rasterizeSvgToPng(String svg, int widthPx, int heightPx) {
        if (widthPx <= 0 && heightPx <= 0) {
            throw new IllegalArgumentException(
                    "At least one of widthPx/heightPx must be positive");
        }
        PNGTranscoder transcoder = new PNGTranscoder();
        if (widthPx > 0) transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, (float) widthPx);
        if (heightPx > 0) transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, (float) heightPx);
        String batikSvg = makeBatikCompatible(svg);
        TranscoderInput input = new TranscoderInput(new StringReader(batikSvg));
        int bufSize = Math.max(widthPx, heightPx) * 32;
        ByteArrayOutputStream out = new ByteArrayOutputStream(bufSize > 0 ? bufSize : 16384);
        TranscoderOutput output = new TranscoderOutput(out);
        try {
            transcoder.transcode(input, output);
        } catch (TranscoderException e) {
            throw new RuntimeException("SVG→PNG rasterisation failed", e);
        }
        return out.toByteArray();
    }

    /** Width-only convenience overload — height follows aspect ratio. */
    public static byte[] rasterizeSvgToPng(String svg, int widthPx) {
        return rasterizeSvgToPng(svg, widthPx, 0);
    }

    /**
     * Rewrite the SVG so Batik (which enforces SVG 1.1) accepts our SVG 2 output:
     * declare the xlink namespace on the root and swap bare {@code href=} on
     * {@code <use>} elements to {@code xlink:href=}. Browsers accept either.
     */
    private static String makeBatikCompatible(String svg) {
        String out = svg;
        if (!out.contains("xmlns:xlink=")) {
            int svgTagStart = out.indexOf("<svg");
            if (svgTagStart >= 0) {
                int insertAt = out.indexOf(' ', svgTagStart);
                if (insertAt >= 0) {
                    out = out.substring(0, insertAt)
                            + " xmlns:xlink=\"http://www.w3.org/1999/xlink\""
                            + out.substring(insertAt);
                }
            }
        }
        out = out.replace("<use href=\"", "<use xlink:href=\"");
        return out;
    }

    // ------------------------------------------------------------------
    // Self-describing PNG metadata (pHYs + tEXt), mirroring delta-gerber
    // ------------------------------------------------------------------

    /** 8-byte PNG file signature. */
    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    /**
     * Embed the px↔mm scale into a freshly-encoded PNG so the image is self-describing:
     * <ul>
     *   <li>a standard {@code pHYs} chunk recording pixels-per-metre (uniform on both axes,
     *       unit = 1 metre) — image viewers read this as the image's resolution/DPI;</li>
     *   <li>{@code tEXt} chunks: {@code side} ("top"/"bottom"), {@code pxPerMm} (the exact
     *       scale as text), and {@code boardGeometryMm} (a JSON object with the side, mirror
     *       flag, the board-space mm rectangle the image covers, the letterbox content offset,
     *       and the pixel location of the ODB++ datum origin), for callers overlaying
     *       real-world measurements such as a 10 mm grid anchored to the origin.</li>
     * </ul>
     * The new chunks replace any existing {@code pHYs} and are spliced in before the first
     * {@code IDAT} (as the spec requires). Returns the input unchanged if it isn't a PNG. The
     * JSON layout and chunk ordering match delta-gerber's {@code embedScaleMetadata} so a
     * consumer parses either library's render identically.
     */
    private static byte[] embedScaleMetadata(byte[] png, double pxPerMm,
            double minXmm, double minYmm, double widthMm, double heightMm,
            double contentOffsetXpx, double contentOffsetYpx,
            double originXpx, double originYpx, Side side, boolean mirrored) {
        if (png == null || png.length < 8 + 25) return png;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (png[i] != PNG_SIGNATURE[i]) return png; // not a PNG — leave it alone
        }

        // pHYs: pixels-per-unit X/Y (big-endian, uint32) + unit specifier (1 = metre).
        long ppm = Math.round(pxPerMm * 1000.0); // px per mm -> px per metre
        if (ppm < 0) ppm = 0;
        byte[] phys = new byte[9];
        writeUInt32(phys, 0, ppm);
        writeUInt32(phys, 4, ppm);
        phys[8] = 1;

        String sideStr = side == Side.BOTTOM ? "bottom" : "top";
        String geometry = String.format(Locale.US,
                "{\"side\":\"%s\",\"mirrored\":%b,\"pxPerMm\":%.6f,"
                + "\"mmRect\":[%.6f,%.6f,%.6f,%.6f],"
                + "\"contentOffsetPx\":[%.3f,%.3f],\"originPx\":[%.3f,%.3f]}",
                sideStr, mirrored, pxPerMm, minXmm, minYmm, widthMm, heightMm,
                contentOffsetXpx, contentOffsetYpx, originXpx, originYpx);

        // Walk the chunk stream so we can (a) drop any pHYs Batik already wrote — leaving
        // two would make readers pick the wrong one — and (b) splice our chunks in just
        // before the first IDAT, satisfying the spec's "pHYs before IDAT" ordering.
        ByteArrayOutputStream out = new ByteArrayOutputStream(png.length + 128);
        out.write(png, 0, 8); // signature
        int pos = 8;
        boolean inserted = false;
        while (pos + 8 <= png.length) {
            long len = readUInt32(png, pos);
            int dataStart = pos + 8;
            int chunkEnd = (int) (dataStart + len + 4); // +4 CRC
            if (len < 0 || chunkEnd > png.length) break; // malformed — bail, keep original
            String type = new String(png, pos + 4, 4,
                    java.nio.charset.StandardCharsets.US_ASCII);
            if ("pHYs".equals(type)) { pos = chunkEnd; continue; } // drop existing pHYs
            if (("IDAT".equals(type) || "IEND".equals(type)) && !inserted) {
                writeChunk(out, "pHYs", phys);
                writeChunk(out, "tEXt", textChunkData("side", sideStr));
                writeChunk(out, "tEXt", textChunkData("pxPerMm",
                        String.format(Locale.US, "%.6f", pxPerMm)));
                writeChunk(out, "tEXt", textChunkData("boardGeometryMm", geometry));
                inserted = true;
            }
            out.write(png, pos, chunkEnd - pos);
            pos = chunkEnd;
        }
        if (!inserted) return png; // never found IDAT/IEND — leave original untouched
        return out.toByteArray();
    }

    private static void writeUInt32(byte[] buf, int off, long v) {
        buf[off]     = (byte) ((v >>> 24) & 0xFF);
        buf[off + 1] = (byte) ((v >>> 16) & 0xFF);
        buf[off + 2] = (byte) ((v >>> 8) & 0xFF);
        buf[off + 3] = (byte) (v & 0xFF);
    }

    private static long readUInt32(byte[] buf, int off) {
        return ((long) (buf[off] & 0xFF) << 24)
             | ((buf[off + 1] & 0xFF) << 16)
             | ((buf[off + 2] & 0xFF) << 8)
             | (buf[off + 3] & 0xFF);
    }

    /** Latin-1 keyword + NUL separator + Latin-1 text, per the PNG {@code tEXt} format. */
    private static byte[] textChunkData(String keyword, String text) {
        byte[] k = keyword.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] t = text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] data = new byte[k.length + 1 + t.length];
        System.arraycopy(k, 0, data, 0, k.length);
        data[k.length] = 0;
        System.arraycopy(t, 0, data, k.length + 1, t.length);
        return data;
    }

    /** Write a full PNG chunk: length(4) + type(4) + data + CRC32(type+data). */
    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] len = new byte[4];
        writeUInt32(len, 0, data.length);
        out.write(len, 0, 4);
        out.write(typeBytes, 0, 4);
        out.write(data, 0, data.length);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(typeBytes);
        crc.update(data);
        byte[] crcBytes = new byte[4];
        writeUInt32(crcBytes, 0, crc.getValue());
        out.write(crcBytes, 0, 4);
    }

    /**
     * The board outline as filled path data, in output units: the profile's surfaces, or
     * failing those the figure its lines and arcs trace. Used to clip the realistic and
     * components views to the board.
     */
    private String extractProfilePath(Features profile) {
        if (profile == null || profile.getFeatures().isEmpty()) return null;
        String path = painter().surfacePathData(profile);
        if (path.isEmpty()) {
            path = painter().outlinePathData(profile);
        }
        return path.isEmpty() ? null : path;
    }

    /**
     * Exports each layer as a separate SVG file.
     */
    public Map<String, Path> exportLayersToFiles(Step step, Matrix matrix, Path outputDir) throws IOException {
        Map<String, Path> exportedFiles = new LinkedHashMap<>();
        Files.createDirectories(outputDir);

        SvgRenderer renderer = new SvgRenderer(baseOptions);

        for (Map.Entry<String, Layer> entry : step.getLayersByName().entrySet()) {
            String layerName = entry.getKey();
            Layer layer = entry.getValue();

            if (layer.getFeatures() != null && !layer.getFeatures().getFeatures().isEmpty()) {
                String safeName = layerName.replaceAll("[^a-zA-Z0-9_-]", "_");
                Path layerFile = outputDir.resolve(safeName + ".svg");

                // Determine color
                String color = getLayerColor(layerName, matrix);
                SvgRenderOptions layerOptions = new SvgRenderOptions()
                        .withColor(color)
                        .withScale(baseOptions.getScale())
                        .withBackground("#FFFFFF");

                SvgRenderer layerRenderer = new SvgRenderer(layerOptions);
                layerRenderer.renderLayer(layer, layerFile);
                exportedFiles.put(layerName, layerFile);
            }
        }

        // Export profile if available (profile is Features type)
        if (step.getProfile() != null && !step.getProfile().getFeatures().isEmpty()) {
            Path profileFile = outputDir.resolve("profile.svg");
            renderer.renderFeatures(step.getProfile(), profileFile);
            exportedFiles.put("profile", profileFile);
        }

        return exportedFiles;
    }

    private void resetGlobalBounds() {
        globalMinX = Double.MAX_VALUE;
        globalMinY = Double.MAX_VALUE;
        globalMaxX = -Double.MAX_VALUE;
        globalMaxY = -Double.MAX_VALUE;
    }

    private void calculateGlobalBounds(Collection<Layer> layers) {
        for (Layer layer : layers) {
            if (layer.getFeatures() == null) continue;

            // What the layer draws: its pads and strokes whole, not only their centres.
            scratchExtent.reset();
            painter().addInk(layer.getFeatures(), scratchExtent);
            if (!scratchExtent.isEmpty()) {
                updatePoint(scratchExtent.minX(), scratchExtent.minY());
                updatePoint(scratchExtent.maxX(), scratchExtent.maxY());
            }
        }

        // Ensure minimum bounds
        if (globalMinX == Double.MAX_VALUE) {
            globalMinX = 0;
            globalMinY = 0;
            globalMaxX = 100;
            globalMaxY = 100;
        }
    }

    /**
     * Include profile bounds in the global bounds calculation.
     * The profile (board outline) often starts at (0,0) and defines the physical board boundary.
     */
    private void calculateProfileBounds(Features profile) {
        if (profile == null || profile.getFeatures().isEmpty()) {
            return;
        }
        for (Feature feature : profile.getFeatures()) {
            updateBounds(feature);
        }
    }

    private void updateBounds(Feature feature) {
        scratchExtent.reset();
        scratchExtent.add(feature);
        if (!scratchExtent.isEmpty()) {
            updatePoint(scratchExtent.minX(), scratchExtent.minY());
            updatePoint(scratchExtent.maxX(), scratchExtent.maxY());
        }
    }

    private void updatePoint(double x, double y) {
        globalMinX = Math.min(globalMinX, x);
        globalMinY = Math.min(globalMinY, y);
        globalMaxX = Math.max(globalMaxX, x);
        globalMaxY = Math.max(globalMaxY, y);
    }

    private List<LayerInfo> buildLayerInfos(Step step, Matrix matrix) {
        Map<String, MatrixLayer> matrixLayerMap = new HashMap<>();
        List<String> matrixOrder = new ArrayList<>();
        if (matrix != null && matrix.getLayers() != null) {
            List<MatrixLayer> sorted = new ArrayList<>(matrix.getLayers());
            sorted.sort(Comparator.comparingInt(MatrixLayer::getRow));
            for (MatrixLayer ml : sorted) {
                String key = ml.getName().toLowerCase(Locale.ROOT);
                matrixLayerMap.put(key, ml);
                matrixOrder.add(key);
            }
        }

        // Walk layers in matrix-row order (physical stackup: top component → paste →
        // mask → copper top..bot → bot mask → bot component → drill/rout → docs).
        // Layers absent from the matrix get appended afterwards in insertion order.
        List<LayerInfo> infos = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (String key : matrixOrder) {
            Map.Entry<String, Layer> hit = findLayerEntryCaseInsensitive(step.getLayersByName(), key);
            if (hit == null) continue;
            visited.add(key);
            LayerInfo info = makeLayerInfo(hit.getKey(), hit.getValue(), matrixLayerMap.get(key), matrix);
            if (info != null) infos.add(info);
        }
        for (Map.Entry<String, Layer> entry : step.getLayersByName().entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (visited.contains(key)) continue;
            LayerInfo info = makeLayerInfo(entry.getKey(), entry.getValue(), matrixLayerMap.get(key), matrix);
            if (info != null) infos.add(info);
        }
        return infos;
    }

    private LayerInfo makeLayerInfo(String name, Layer layer, MatrixLayer ml, Matrix matrix) {
        LayerInfo info = new LayerInfo();
        info.name = name;
        info.layer = layer;
        info.matrixLayer = ml;
        info.color = getLayerColor(name, matrix);
        info.type = ml != null ? ml.getType() : "SIGNAL";
        if ("DIELECTRIC".equalsIgnoreCase(info.type)) return null;
        return info;
    }

    private static Map.Entry<String, Layer> findLayerEntryCaseInsensitive(Map<String, Layer> layers, String lowerKey) {
        for (Map.Entry<String, Layer> e : layers.entrySet()) {
            if (e.getKey().toLowerCase(Locale.ROOT).equals(lowerKey)) return e;
        }
        return null;
    }

    private String getLayerColor(String layerName, Matrix matrix) {
        if (matrix != null && matrix.getLayers() != null) {
            for (MatrixLayer ml : matrix.getLayers()) {
                // Case-insensitive comparison
                if (ml.getName().equalsIgnoreCase(layerName)) {
                    String type = ml.getType();
                    if (LAYER_TYPE_COLORS.containsKey(type)) {
                        return LAYER_TYPE_COLORS.get(type);
                    }
                }
            }
        }

        // Fallback based on layer name patterns
        String lowerName = layerName.toLowerCase();
        if (lowerName.contains("top") || lowerName.contains("signal")) return LAYER_TYPE_COLORS.get("SIGNAL");
        if (lowerName.contains("bottom") || lowerName.contains("bot")) return "#0066CC";
        if (lowerName.contains("gnd") || lowerName.contains("ground") || lowerName.contains("power")) return LAYER_TYPE_COLORS.get("POWER_GROUND");
        if (lowerName.contains("mask") || lowerName.contains("sm_")) return LAYER_TYPE_COLORS.get("SOLDER_MASK");
        if (lowerName.contains("silk") || lowerName.contains("ss_")) return LAYER_TYPE_COLORS.get("SILK_SCREEN");
        if (lowerName.contains("paste") || lowerName.contains("sp_")) return LAYER_TYPE_COLORS.get("SOLDER_PASTE");
        if (lowerName.contains("drill")) return LAYER_TYPE_COLORS.get("DRILL");
        if (lowerName.contains("comp")) return LAYER_TYPE_COLORS.get("COMPONENT");

        return "#333333";
    }

    private void writeEmptySvg(Writer writer) throws IOException {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"100\">\n");
        writer.write("  <!-- Empty design -->\n");
        writer.write("</svg>\n");
    }

    private void writeSvgHeader(Writer writer) throws IOException {
        // Convert bounds to output units
        double minX = baseOptions.toOutputUnit(globalMinX);
        double minY = baseOptions.toOutputUnit(globalMinY);
        double maxX = baseOptions.toOutputUnit(globalMaxX);
        double maxY = baseOptions.toOutputUnit(globalMaxY);
        double padding = baseOptions.toOutputUnit(baseOptions.getPadding());

        // Calculate viewBox dimensions in output units
        double viewWidth = (maxX - minX + 2 * padding) * baseOptions.getScale();
        double viewHeight = (maxY - minY + 2 * padding) * baseOptions.getScale();

        // Convert to pixels for width/height attributes using DPI
        // When outputting inches, we need to multiply by DPI to get pixels
        // When outputting mm, we use mm-to-inch conversion then DPI
        double dpi = baseOptions.getDpi();
        double pixelWidth, pixelHeight;
        if (baseOptions.getOutputUnit() == SvgRenderOptions.OutputUnit.INCH) {
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
                        "stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-width=\"0\" fill-rule=\"nonzero\" " +
                        "data-layers=\"true\">\n",
                pixelWidth, pixelHeight,
                minX - padding,
                minY - padding,
                maxX - minX + 2 * padding,
                maxY - minY + 2 * padding));
    }

    private void writeStyleSection(Writer writer, List<LayerInfo> layerInfos) throws IOException {
        writer.write("  <style>\n");
        writer.write("    .layer { transition: opacity 0.3s; }\n");
        writer.write("    .layer.hidden { opacity: 0; pointer-events: none; }\n");
        // Profile style - uses CSS variable if available, falls back to white
        writer.write("    .profile path { stroke: var(--viewer-inverse-bg, #FFFFFF); }\n");
        for (LayerInfo info : layerInfos) {
            String safeId = info.name.replaceAll("[^a-zA-Z0-9_-]", "_");
            writer.write(String.format("    .layer-%s { fill: %s; stroke: %s; }\n",
                    safeId, info.color, info.color));
        }
        writer.write("  </style>\n");
    }

    private void writeTransformGroup(Writer writer) throws IOException {
        // Convert to output units
        double minX = baseOptions.toOutputUnit(globalMinX);
        double minY = baseOptions.toOutputUnit(globalMinY);
        double maxX = baseOptions.toOutputUnit(globalMaxX);
        double maxY = baseOptions.toOutputUnit(globalMaxY);
        double padding = baseOptions.toOutputUnit(baseOptions.getPadding());

        double centerY = (maxY + minY) / 2;
        double centerX = (maxX + minX) / 2;

        // Build transform: Y-axis flip is always applied, X-axis mirror for bottom view
        String transform;
        if (baseOptions.isMirrorX()) {
            // For bottom view: mirror X-axis and flip Y-axis
            // scale(-1,-1) mirrors both axes, then translate to put back in view
            transform = String.format(Locale.US, "scale(-1,-1) translate(%.4f,%.4f)", -2 * centerX, -2 * centerY);
        } else {
            // For top view: just flip Y-axis
            transform = String.format(Locale.US, "scale(1,-1) translate(0,%.4f)", -2 * centerY);
        }
        writer.write(String.format(Locale.US, "  <g transform=\"%s\">\n", transform));

        // Background
        if (baseOptions.getBackgroundColor() != null) {
            writer.write(String.format(Locale.US, "    <rect x=\"%.4f\" y=\"%.4f\" width=\"%.4f\" height=\"%.4f\" fill=\"%s\"/>\n",
                    minX - padding,
                    minY - padding,
                    maxX - minX + 2 * padding,
                    maxY - minY + 2 * padding,
                    baseOptions.getBackgroundColor()));
        }
    }

    private void writeCloseTransformGroup(Writer writer) throws IOException {
        writer.write("  </g>\n");
    }

    private void renderProfileFromFeatures(Features profile, Writer writer) throws IOException {
        writer.write("    <g id=\"layer-profile\" class=\"layer profile\" data-layer-name=\"profile\" data-layer-type=\"PROFILE\">\n");

        // Profile uses vector-effect="non-scaling-stroke" so it always appears the same width regardless of zoom
        // Stroke width is in pixels (2px like reference)
        String outline = painter().outlinePathData(profile);
        if (!outline.isEmpty()) {
            writer.write("      <path d=\"" + outline + "\" fill=\"none\" stroke=\"#FFFFFF\" "
                    + "stroke-width=\"2px\" vector-effect=\"non-scaling-stroke\" class=\"profile\"/>\n");
        }

        writer.write("    </g>\n");
    }

    private void renderLayerGroup(LayerInfo info, Writer writer) throws IOException {
        String safeId = info.name.replaceAll("[^a-zA-Z0-9_-]", "_");

        writer.write(String.format(Locale.US, "    <g id=\"layer-%s\" class=\"layer layer-%s\" " +
                        "data-layer-name=\"%s\" data-layer-type=\"%s\">\n",
                safeId, safeId, escapeAttr(info.name), info.type));

        Features features = info.layer.getFeatures();

        // Only render features if they exist
        if (features != null && !features.getFeatures().isEmpty()) {
            // Zero-width strokes are drawn as hairlines that stay visible at this size.
            FeaturePainter painter = painter().skipText(previewMode).hairlineMm(
                    Math.max(globalMaxX - globalMinX, globalMaxY - globalMinY) / HAIRLINES_ACROSS);
            if (painter.hasClear(features)) {
                // Negative features erase what the layer has drawn so far and nothing else:
                // paint the layer as a mask and show the layer colour through it.
                String maskId = "layer-mask-" + safeId;
                String frame = frameRect();
                writer.write("      <mask id=\"" + maskId + "\" maskUnits=\"userSpaceOnUse\" "
                        + "maskContentUnits=\"userSpaceOnUse\" " + frame + ">\n");
                painter.paint(features, writer, Palette.of("#ffffff", "#000000"));
                writer.write("      </mask>\n");
                writer.write("      <rect " + frame + " fill=\"" + info.color + "\" mask=\"url(#"
                        + maskId + ")\"/>\n");
            } else {
                painter.paint(features, writer, Palette.of(info.color, info.color));
            }
        }

        writer.write("    </g>\n");
    }

    /** The drawing frame — bounds plus padding — as rect attributes in output units. */
    private String frameRect() {
        double minX = baseOptions.toOutputUnit(globalMinX);
        double minY = baseOptions.toOutputUnit(globalMinY);
        double maxX = baseOptions.toOutputUnit(globalMaxX);
        double maxY = baseOptions.toOutputUnit(globalMaxY);
        double padding = baseOptions.toOutputUnit(baseOptions.getPadding());
        return String.format(Locale.US, "x=\"%.4f\" y=\"%.4f\" width=\"%.4f\" height=\"%.4f\"",
                minX - padding, minY - padding, maxX - minX + 2 * padding, maxY - minY + 2 * padding);
    }

    /**
     * Renders components from all layers as centroids with labels.
     */
    private void renderComponents(Step step, Writer writer) throws IOException {
        writer.write("    <g id=\"layer-components\" class=\"layer\" data-layer-name=\"components\" data-layer-type=\"COMPONENT\">\n");

        String color = baseOptions.getComponentColor();
        double markerSize = baseOptions.getComponentMarkerSize();
        double labelSize = baseOptions.getComponentLabelSize();
        double labelOffset = baseOptions.getComponentLabelOffset();

        // Collect components from all component layers
        for (Map.Entry<String, Layer> entry : step.getLayersByName().entrySet()) {
            Layer layer = entry.getValue();
            if (layer.getComponents() != null && layer.getComponents().getComponents() != null) {
                for (Component component : layer.getComponents().getComponents()) {
                    renderComponent(component, writer, color, markerSize, labelSize, labelOffset);
                }
            }
        }

        writer.write("    </g>\n");
    }

    /**
     * Renders a single component centroid with label and rotation indicator.
     */
    private void renderComponent(Component component, Writer writer, String color,
                                 double markerSize, double labelSize, double labelOffset) throws IOException {
        // Convert to output units
        double x = baseOptions.toOutputUnit(component.getX());
        double y = baseOptions.toOutputUnit(component.getY());
        double size = baseOptions.toOutputUnit(markerSize);
        double lblSize = baseOptions.toOutputUnit(labelSize);
        double lblOffset = baseOptions.toOutputUnit(labelOffset);

        double rotation = component.getRotation();
        String name = component.getCompName();

        // Adjust rotation for mirrored components
        double effectiveRotation = rotation;
        if (component.getMirror() == MirrorType.MIRRORED) {
            effectiveRotation = -rotation; // Mirror reverses rotation direction
        }

        // Convert rotation to radians for SVG transforms (note: SVG rotates clockwise with positive angles)
        // ODB++ rotation is clockwise in degrees

        // Draw centroid marker (crosshair) with rotation
        writer.write(String.format(Locale.US, "      <g transform=\"translate(%.4f,%.4f) rotate(%.2f)\">\n",
                x, y, effectiveRotation));

        // Crosshair arms
        double halfSize = size / 2;
        writer.write(String.format(Locale.US, "        <line x1=\"%.4f\" y1=\"0\" x2=\"%.4f\" y2=\"0\" stroke=\"%s\" stroke-width=\"%.4f\"/>\n",
                -halfSize, halfSize, color, size / 10));
        writer.write(String.format(Locale.US, "        <line x1=\"0\" y1=\"%.4f\" x2=\"0\" y2=\"%.4f\" stroke=\"%s\" stroke-width=\"%.4f\"/>\n",
                -halfSize, halfSize, color, size / 10));

        // Direction indicator (small arrow on positive X, showing rotation)
        double arrowSize = size / 4;
        writer.write(String.format(Locale.US, "        <polygon points=\"%.4f,0 %.4f,%.4f %.4f,%.4f\" fill=\"%s\"/>\n",
                halfSize + arrowSize, halfSize, arrowSize / 2, halfSize, -arrowSize / 2, color));

        writer.write("      </g>\n");

        // Draw component label (outside the rotation group so text stays upright)
        if (name != null && !name.isEmpty()) {
            String escaped = name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            // Note: We need to counter the Y-axis flip for text to appear correctly
            // The main transform group has scale(1,-1), so text would be upside down
            // We apply scale(1,-1) to the text to flip it back
            writer.write(String.format(Locale.US, "      <text x=\"%.4f\" y=\"%.4f\" " +
                            "font-size=\"%.4f\" fill=\"%s\" font-family=\"sans-serif\" " +
                            "transform=\"scale(1,-1) translate(0,%.4f)\">%s</text>\n",
                    x + lblOffset, -(y + lblOffset), lblSize, color, 0.0, escaped));
        }
    }

    private void writeSvgFooter(Writer writer) throws IOException {
        writer.write("</svg>\n");
    }

    private static class LayerInfo {
        String name;
        Layer layer;
        MatrixLayer matrixLayer;
        String color;
        String type;
    }
}

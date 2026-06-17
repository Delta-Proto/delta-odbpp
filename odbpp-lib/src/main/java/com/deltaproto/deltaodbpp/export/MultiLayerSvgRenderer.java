package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.export.render.SymbolRenderer;
import com.deltaproto.deltaodbpp.export.render.SymbolResolver;
import com.deltaproto.deltaodbpp.model.*;
import com.deltaproto.deltaodbpp.model.Component;
import com.deltaproto.deltaodbpp.model.MirrorType;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
    private static final String SOLDERMASK_GREEN = "#004200";     // Dark green soldermask
    private static final String SILKSCREEN_WHITE = "#ffffff";     // White silkscreen
    private static final double SOLDERMASK_OPACITY = 0.75;

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
    private SymbolResolver symbolResolver;
    private double globalMinX = Double.MAX_VALUE;
    private double globalMinY = Double.MAX_VALUE;
    private double globalMaxX = Double.MIN_VALUE;
    private double globalMaxY = Double.MIN_VALUE;

    public MultiLayerSvgRenderer() {
        this(new SvgRenderOptions());
    }

    public MultiLayerSvgRenderer(SvgRenderOptions options) {
        this.baseOptions = options;
        // SymbolResolver is created lazily when we know the unit system from Job/Step
    }

    /**
     * Creates a SymbolResolver with the appropriate unit system.
     *
     * @param useMillimeters true if ODB++ uses MM units (symbol dimensions in microns),
     *                       false if using INCH units (symbol dimensions in mils)
     */
    private void initSymbolResolver(boolean useMillimeters) {
        this.symbolResolver = new SymbolResolver(baseOptions.getDefaultPadSize(), useMillimeters);
    }

    /**
     * Decide whether the archive is MM-native for symbol-resolution purposes.
     *
     * <p>Historically we relied on {@code MiscInfo.units}, but a non-trivial
     * fraction of real archives (some Mentor Xpedition exports, for instance)
     * declare {@code UNITS=} in neither misc/info nor stephdr, only
     * inline as {@code U MM} in each features file. We therefore also look at
     * the parsed features' {@code units} field as a fallback.
     */
    private static boolean detectUseMillimeters(Job job) {
        if (job == null) return false;
        if (job.getMiscInfo() != null && "MM".equalsIgnoreCase(job.getMiscInfo().getUnits())) {
            return true;
        }
        if (job.getSteps() != null) {
            for (Step step : job.getSteps().values()) {
                if (step.getStepHdr() != null && "MM".equalsIgnoreCase(step.getStepHdr().getUnits())) {
                    return true;
                }
                if (step.getProfile() != null && "MM".equalsIgnoreCase(step.getProfile().getUnits())) {
                    return true;
                }
                if (step.getLayersByName() != null) {
                    for (Layer layer : step.getLayersByName().values()) {
                        Features f = layer.getFeatures();
                        if (f != null && "MM".equalsIgnoreCase(f.getUnits())) return true;
                    }
                }
            }
        }
        return false;
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

        initSymbolResolver(detectUseMillimeters(job));

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

        // Initialize symbol resolver if not already done (e.g., when called directly without renderJob)
        if (symbolResolver == null) {
            initSymbolResolver(false); // Default to INCH/mils if no Job context
        }

        // Calculate global bounds from layers
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

        initSymbolResolver(detectUseMillimeters(job));

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

        // Initialize symbol resolver if not already done
        if (symbolResolver == null) {
            initSymbolResolver(false);
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

        writer.write("<defs>\n");

        // Board outline clipPath — used to constrain every composition group to the board shape.
        writer.write("  <clipPath id=\"board-outline\" clipPathUnits=\"userSpaceOnUse\">\n");
        writer.write(String.format("    <path d=\"%s\"/>\n", profilePath));
        writer.write("  </clipPath>\n");

        boolean hasSoldermask = !soldermaskLayers.isEmpty();
        String smMaskId = topSide ? "sm-top-mask" : "sm-bottom-mask";
        String cfMaskId = topSide ? "cf-top-mask" : "cf-bottom-mask";

        if (hasSoldermask) {
            // sm-mask: board profile white (mask present = green visible),
            // soldermask features black (mask absent = underlying copper/gold shows through)
            writer.write(String.format("  <mask id=\"%s\" maskContentUnits=\"userSpaceOnUse\">\n", smMaskId));
            writer.write(String.format("    <path d=\"%s\" fill=\"white\"/>\n", profilePath));
            for (LayerInfo info : soldermaskLayers) {
                writer.write("    <g fill=\"black\" stroke=\"black\">\n");
                renderLayerFeaturesRealistic(info.layer.getFeatures(), writer, "black", "white");
                writer.write("    </g>\n");
            }
            writer.write("  </mask>\n");

            // cf-mask: inverse — black background + soldermask features white,
            // so gold finish is visible only through pad openings
            writer.write(String.format("  <mask id=\"%s\" maskContentUnits=\"userSpaceOnUse\">\n", cfMaskId));
            writer.write(String.format("    <rect %s fill=\"black\"/>\n", fullRect));
            for (LayerInfo info : soldermaskLayers) {
                writer.write("    <g fill=\"white\" stroke=\"white\">\n");
                renderLayerFeaturesRealistic(info.layer.getFeatures(), writer, "white", "black");
                writer.write("    </g>\n");
            }
            writer.write("  </mask>\n");
        }

        // mech-mask: white background punched by drill holes (black). Only drills visible
        // from the target side (through-holes, blind vias on this side) are included.
        boolean hasDrills = !drillLayers.isEmpty();
        if (hasDrills) {
            writer.write("  <mask id=\"mech-mask\" maskContentUnits=\"userSpaceOnUse\">\n");
            writer.write(String.format("    <rect %s fill=\"white\"/>\n", fullRect));
            for (LayerInfo info : drillLayers) {
                writer.write("    <g fill=\"black\" stroke=\"black\" stroke-width=\"0\">\n");
                renderLayerFeaturesRealistic(info.layer.getFeatures(), writer, "black", "white");
                writer.write("    </g>\n");
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
        writer.write(String.format("    <g data-stack-layer=\"substrate\">\n"));
        writer.write(String.format("      <rect %s fill=\"%s\"/>\n", fullRect, FR4_COLOR));
        writer.write("    </g>\n");

        // 2. Copper layers (silver/gray) — visible through semi-transparent mask
        writer.write("    <g data-stack-layer=\"copper\">\n");
        for (LayerInfo info : copperLayers) {
            writer.write(String.format(
                    "      <g data-layer-name=\"%s\" fill=\"%s\" stroke=\"%s\" stroke-width=\"0\">\n",
                    escapeAttr(info.name), COPPER_COLOR, COPPER_COLOR));
            renderLayerFeaturesRealistic(info.layer.getFeatures(), writer, COPPER_COLOR, FR4_COLOR);
            writer.write("      </g>\n");
        }
        writer.write("    </g>\n");

        // 3. Copper finish (gold HASL/ENIG) — visible only where mask has openings (pad windows)
        if (hasSoldermask && !copperLayers.isEmpty()) {
            writer.write(String.format(
                    "    <g data-stack-layer=\"copper-finish\" mask=\"url(#%s)\">\n", cfMaskId));
            for (LayerInfo info : copperLayers) {
                writer.write(String.format(
                        "      <g data-layer-name=\"%s\" fill=\"%s\" stroke=\"%s\" stroke-width=\"0\">\n",
                        escapeAttr(info.name), COPPER_FINISH_COLOR, COPPER_FINISH_COLOR));
                renderLayerFeaturesRealistic(info.layer.getFeatures(), writer, COPPER_FINISH_COLOR, FR4_COLOR);
                writer.write("      </g>\n");
            }
            writer.write("    </g>\n");
        }

        // 4. Soldermask (green, semi-transparent) with silkscreen nested inside so silk
        // is only visible over the mask, never over exposed pads.
        if (hasSoldermask) {
            writer.write(String.format(
                    "    <g data-stack-layer=\"soldermask\" mask=\"url(#%s)\">\n", smMaskId));
            writer.write(String.format(Locale.US, "      <rect %s fill=\"%s\" opacity=\"%.2f\"/>\n",
                    fullRect, SOLDERMASK_GREEN, SOLDERMASK_OPACITY));

            for (LayerInfo info : silkscreenLayers) {
                writer.write(String.format(
                        "      <g data-stack-layer=\"silkscreen\" data-layer-name=\"%s\" " +
                                "fill=\"%s\" stroke=\"%s\" stroke-width=\"0\">\n",
                        escapeAttr(info.name), SILKSCREEN_WHITE, SILKSCREEN_WHITE));
                renderLayerFeaturesRealistic(info.layer.getFeatures(), writer, SILKSCREEN_WHITE, SOLDERMASK_GREEN);
                writer.write("      </g>\n");
            }

            writer.write("    </g>\n");
        }

        writer.write("  </g>\n"); // close board group
        writer.write("</g>\n"); // close viewport
        writer.write("</svg>\n");
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
        initSymbolResolver(detectUseMillimeters(job));
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
        if (symbolResolver == null) initSymbolResolver(false);

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

            // Package bounds: prefer EDA record if available, else a default stub.
            double halfW;
            double halfH;
            EdaData.PackageRecord pkg = lookupPackage(packages, c.getPkgRef());
            if (pkg != null) {
                halfW = baseOptions.toOutputUnit((pkg.getXMax() - pkg.getXMin()) / 2.0);
                halfH = baseOptions.toOutputUnit((pkg.getYMax() - pkg.getYMin()) / 2.0);
                if (halfW <= 0 || halfH <= 0) {
                    halfW = Math.max(halfW, vbW * 0.01);
                    halfH = Math.max(halfH, vbH * 0.012);
                    packageFallbackCount++;
                }
            } else {
                halfW = vbW * 0.01;
                halfH = vbH * 0.012;
                packageFallbackCount++;
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
            writer.write(String.format(Locale.US,
                    "      <rect x=\"%.4f\" y=\"%.4f\" width=\"%.4f\" height=\"%.4f\" " +
                            "fill=\"%s\" stroke=\"%s\" stroke-width=\"0.5\" " +
                            "vector-effect=\"non-scaling-stroke\"/>\n",
                    -halfW, -halfH, 2 * halfW, 2 * halfH, fill, stroke));
            writer.write("    </g>\n");

            // Refdes label — separate sibling group, outside the rotation/mirror transform so
            // text stays upright. Counter-flip Y for the viewport transform.
            String refdes = c.getCompName();
            if (refdes != null && !refdes.isEmpty()) {
                writer.write(String.format(Locale.US,
                        "    <text x=\"%.4f\" y=\"%.4f\" font-size=\"%.4f\" fill=\"%s\" " +
                                "text-anchor=\"middle\" font-family=\"sans-serif\" " +
                                "transform=\"scale(1,-1) translate(0,%.4f)\" " +
                                "data-refdes-label=\"%s\">%s</text>\n",
                        cx, -cy, labelSize, COMP_LABEL_COLOR, 0.0,
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

    /**
     * Renders all features from a layer with specified colors for realistic view.
     * Temporarily overrides the background color for correct polarity handling.
     */
    private void renderLayerFeaturesRealistic(Features features, Writer writer,
            String color, String bgColor) throws IOException {
        if (features == null || features.getFeatures().isEmpty()) return;
        String savedBg = baseOptions.getBackgroundColor();
        baseOptions.withBackground(bgColor);
        try {
            for (Feature feature : features.getFeatures()) {
                renderFeature(feature, features, writer, color);
            }
        } finally {
            baseOptions.withBackground(savedBg);
        }
    }

    /**
     * Extract a filled SVG path from the board profile Features.
     * Used as clipPath for realistic rendering.
     */
    private String extractProfilePath(Features profile) {
        if (profile == null || profile.getFeatures().isEmpty()) return null;

        StringBuilder path = new StringBuilder();
        for (Feature feature : profile.getFeatures()) {
            if (feature instanceof Surface surface) {
                for (ContourPolygon polygon : surface.getPolygons()) {
                    double startX = baseOptions.toOutputUnit(polygon.getXStart());
                    double startY = baseOptions.toOutputUnit(polygon.getYStart());
                    path.append(String.format(Locale.US, "M %.4f %.4f", startX, startY));
                    for (ContourPolygon.PolygonPart part : polygon.getPolygonParts()) {
                        double endX = baseOptions.toOutputUnit(part.getEndX());
                        double endY = baseOptions.toOutputUnit(part.getEndY());
                        if (part.getType() == ContourPolygon.PolygonPart.Type.SEGMENT) {
                            path.append(String.format(Locale.US, " L %.4f %.4f", endX, endY));
                        } else if (part.getType() == ContourPolygon.PolygonPart.Type.ARC) {
                            double radius = Math.sqrt(
                                    Math.pow(part.getEndX() - part.getXCenter(), 2) +
                                    Math.pow(part.getEndY() - part.getYCenter(), 2));
                            double r = baseOptions.toOutputUnit(radius);
                            int sweep = part.isClockwise() ? 0 : 1;
                            path.append(String.format(Locale.US, " A %.4f %.4f 0 0 %d %.4f %.4f",
                                    r, r, sweep, endX, endY));
                        }
                    }
                    path.append(" Z ");
                }
            } else if (feature instanceof Line line) {
                double x1 = baseOptions.toOutputUnit(line.getXs());
                double y1 = baseOptions.toOutputUnit(line.getYs());
                double x2 = baseOptions.toOutputUnit(line.getXe());
                double y2 = baseOptions.toOutputUnit(line.getYe());
                path.append(String.format(Locale.US, "M %.4f %.4f L %.4f %.4f ", x1, y1, x2, y2));
            } else if (feature instanceof Arc arc) {
                double radius = Math.sqrt(
                        Math.pow(arc.getXs() - arc.getXc(), 2) +
                        Math.pow(arc.getYs() - arc.getYc(), 2));
                double r = baseOptions.toOutputUnit(radius);
                double xs = baseOptions.toOutputUnit(arc.getXs());
                double ys = baseOptions.toOutputUnit(arc.getYs());
                double xe = baseOptions.toOutputUnit(arc.getXe());
                double ye = baseOptions.toOutputUnit(arc.getYe());
                int sweepFlag = "Y".equalsIgnoreCase(arc.getCw()) ? 0 : 1;
                path.append(String.format(Locale.US, "M %.4f %.4f A %.4f %.4f 0 0 %d %.4f %.4f ",
                        xs, ys, r, r, sweepFlag, xe, ye));
            }
        }

        String result = path.toString().trim();
        return result.isEmpty() ? null : result;
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

            for (Feature feature : layer.getFeatures().getFeatures()) {
                updateBounds(feature);
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
            if (feature instanceof Surface surface) {
                for (ContourPolygon polygon : surface.getPolygons()) {
                    updatePoint(polygon.getXStart(), polygon.getYStart());
                    for (ContourPolygon.PolygonPart part : polygon.getPolygonParts()) {
                        updatePoint(part.getEndX(), part.getEndY());
                        if (part.getType() == ContourPolygon.PolygonPart.Type.ARC) {
                            updatePoint(part.getXCenter(), part.getYCenter());
                        }
                    }
                }
            } else if (feature instanceof Line line) {
                updatePoint(line.getXs(), line.getYs());
                updatePoint(line.getXe(), line.getYe());
            } else if (feature instanceof Arc arc) {
                updatePoint(arc.getXs(), arc.getYs());
                updatePoint(arc.getXe(), arc.getYe());
                updatePoint(arc.getXc(), arc.getYc());
            }
        }
    }

    private void updateBounds(Feature feature) {
        if (feature instanceof Pad pad) {
            updatePoint(pad.getX(), pad.getY());
        } else if (feature instanceof Line line) {
            updatePoint(line.getXs(), line.getYs());
            updatePoint(line.getXe(), line.getYe());
        } else if (feature instanceof Arc arc) {
            updatePoint(arc.getXs(), arc.getYs());
            updatePoint(arc.getXe(), arc.getYe());
            updatePoint(arc.getXc(), arc.getYc());
        } else if (feature instanceof Surface surface) {
            for (ContourPolygon polygon : surface.getPolygons()) {
                updatePoint(polygon.getXStart(), polygon.getYStart());
                for (ContourPolygon.PolygonPart part : polygon.getPolygonParts()) {
                    updatePoint(part.getEndX(), part.getEndY());
                }
            }
        } else if (feature instanceof Text text) {
            updatePoint(text.getX(), text.getY());
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
        String profileStyle = "fill=\"none\" stroke=\"#FFFFFF\" stroke-width=\"2px\" vector-effect=\"non-scaling-stroke\" class=\"profile\"";

        for (Feature feature : profile.getFeatures()) {
            if (feature instanceof Surface surface) {
                for (ContourPolygon polygon : surface.getPolygons()) {
                    StringBuilder pathData = new StringBuilder();
                    double startX = baseOptions.toOutputUnit(polygon.getXStart());
                    double startY = baseOptions.toOutputUnit(polygon.getYStart());
                    pathData.append(String.format(Locale.US, "M %.4f %.4f", startX, startY));

                    for (ContourPolygon.PolygonPart part : polygon.getPolygonParts()) {
                        double endX = baseOptions.toOutputUnit(part.getEndX());
                        double endY = baseOptions.toOutputUnit(part.getEndY());

                        if (part.getType() == ContourPolygon.PolygonPart.Type.SEGMENT) {
                            pathData.append(String.format(Locale.US, " L %.4f %.4f", endX, endY));
                        } else if (part.getType() == ContourPolygon.PolygonPart.Type.ARC) {
                            double radius = Math.sqrt(
                                    Math.pow(part.getEndX() - part.getXCenter(), 2) +
                                    Math.pow(part.getEndY() - part.getYCenter(), 2));
                            double r = baseOptions.toOutputUnit(radius);
                            int sweepFlag = part.isClockwise() ? 0 : 1;
                            pathData.append(String.format(Locale.US, " A %.4f %.4f 0 0 %d %.4f %.4f",
                                    r, r, sweepFlag, endX, endY));
                        }
                    }
                    pathData.append(" Z");

                    writer.write(String.format(Locale.US, "      <path d=\"%s\" %s/>\n",
                            pathData, profileStyle));
                }
            } else if (feature instanceof Line line) {
                double x1 = baseOptions.toOutputUnit(line.getXs());
                double y1 = baseOptions.toOutputUnit(line.getYs());
                double x2 = baseOptions.toOutputUnit(line.getXe());
                double y2 = baseOptions.toOutputUnit(line.getYe());
                writer.write(String.format(Locale.US, "      <path d=\"M %.4f %.4f L %.4f %.4f\" %s/>\n",
                        x1, y1, x2, y2, profileStyle));
            } else if (feature instanceof Arc arc) {
                double radius = Math.sqrt(Math.pow(arc.getXs() - arc.getXc(), 2) + Math.pow(arc.getYs() - arc.getYc(), 2));
                double r = baseOptions.toOutputUnit(radius);
                double xs = baseOptions.toOutputUnit(arc.getXs());
                double ys = baseOptions.toOutputUnit(arc.getYs());
                double xe = baseOptions.toOutputUnit(arc.getXe());
                double ye = baseOptions.toOutputUnit(arc.getYe());
                int sweepFlag = "Y".equalsIgnoreCase(arc.getCw()) ? 0 : 1;
                writer.write(String.format(Locale.US, "      <path d=\"M %.4f %.4f A %.4f %.4f 0 0 %d %.4f %.4f\" %s/>\n",
                        xs, ys, r, r, sweepFlag, xe, ye, profileStyle));
            }
        }

        writer.write("    </g>\n");
    }

    private void renderLayerGroup(LayerInfo info, Writer writer) throws IOException {
        String safeId = info.name.replaceAll("[^a-zA-Z0-9_-]", "_");

        writer.write(String.format(Locale.US, "    <g id=\"layer-%s\" class=\"layer layer-%s\" " +
                        "data-layer-name=\"%s\" data-layer-type=\"%s\">\n",
                safeId, safeId, info.name, info.type));

        Features features = info.layer.getFeatures();

        // Only render features if they exist
        if (features != null && !features.getFeatures().isEmpty()) {
            StringWriter layerContent = new StringWriter();
            // Render features directly (without full SVG wrapper)
            for (Feature feature : features.getFeatures()) {
                renderFeature(feature, features, layerContent, info.color);
            }
            writer.write(layerContent.toString());
        }

        writer.write("    </g>\n");
    }

    private void renderFeature(Feature feature, Features features, Writer writer, String color) throws IOException {
        if (feature instanceof Pad pad) {
            // Resolve symbol from symbol table for proper dimensions
            SymbolRenderer renderer = symbolResolver.resolve(pad.getSymbolNumber(), features);

            // Apply resize factor if present
            double scale = pad.getResizeFactor() != null ? pad.getResizeFactor() : 1.0;

            // Convert coordinates to output units
            double x = baseOptions.toOutputUnit(pad.getX());
            double y = baseOptions.toOutputUnit(pad.getY());

            // SymbolResolver returns dimensions in mm. Apply the same unit conversion
            // as positions so they end up in the configured output unit together.
            double symbolScale = scale * baseOptions.toOutputUnit(1.0);

            // Get rotation and mirror from orientation
            double rotation = getRotationFromOrientationType(pad.getOrientationType(), pad.getCustomRotation());
            boolean mirror = isOrientationMirrored(pad.getOrientationType());

            // Render using the symbol renderer
            String svg = renderer.render(x, y, rotation, mirror, symbolScale, color);
            writer.write("      " + svg + "\n");
        } else if (feature instanceof Line line) {
            // Resolve symbol from symbol table for line width
            SymbolRenderer renderer = symbolResolver.resolve(line.getSymbolNumber(), features);
            // Symbol width is mm; convert to output unit.
            double strokeWidth = baseOptions.toOutputUnit(renderer.getWidth());

            double x1 = baseOptions.toOutputUnit(line.getXs());
            double y1 = baseOptions.toOutputUnit(line.getYs());
            double x2 = baseOptions.toOutputUnit(line.getXe());
            double y2 = baseOptions.toOutputUnit(line.getYe());
            // Use path format like reference SVG: <path d="M x1 y1 L x2 y2" .../>
            writer.write(String.format(Locale.US, "      <path d=\"M %.4f %.4f L %.4f %.4f\" " +
                            "stroke=\"%s\" stroke-width=\"%.4f\" stroke-linecap=\"round\" fill=\"none\"/>\n",
                    x1, y1, x2, y2, color, strokeWidth));
        } else if (feature instanceof Arc arc) {
            // Resolve symbol from symbol table for stroke width
            SymbolRenderer renderer = symbolResolver.resolve(arc.getSymbolNumber(), features);
            // Symbol width is mm; convert to output unit.
            double strokeWidth = baseOptions.toOutputUnit(renderer.getWidth());

            double radius = Math.sqrt(Math.pow(arc.getXs() - arc.getXc(), 2) + Math.pow(arc.getYs() - arc.getYc(), 2));
            double xs = baseOptions.toOutputUnit(arc.getXs());
            double ys = baseOptions.toOutputUnit(arc.getYs());
            double xe = baseOptions.toOutputUnit(arc.getXe());
            double ye = baseOptions.toOutputUnit(arc.getYe());
            double r = baseOptions.toOutputUnit(radius);
            int sweepFlag = "Y".equalsIgnoreCase(arc.getCw()) ? 0 : 1;
            writer.write(String.format(Locale.US, "      <path d=\"M %.4f %.4f A %.4f %.4f 0 0 %d %.4f %.4f\" " +
                            "stroke=\"%s\" stroke-width=\"%.4f\" fill=\"none\" stroke-linecap=\"round\"/>\n",
                    xs, ys, r, r, sweepFlag, xe, ye, color, strokeWidth));
        } else if (feature instanceof Surface surface) {
            // Combine all polygons (islands and holes) into a single path with evenodd fill-rule
            // This ensures holes properly cut out from the islands
            StringBuilder combinedPath = new StringBuilder();

            for (ContourPolygon polygon : surface.getPolygons()) {
                double startX = baseOptions.toOutputUnit(polygon.getXStart());
                double startY = baseOptions.toOutputUnit(polygon.getYStart());
                combinedPath.append(String.format(Locale.US, "M %.4f %.4f", startX, startY));

                for (ContourPolygon.PolygonPart part : polygon.getPolygonParts()) {
                    double endX = baseOptions.toOutputUnit(part.getEndX());
                    double endY = baseOptions.toOutputUnit(part.getEndY());

                    if (part.getType() == ContourPolygon.PolygonPart.Type.SEGMENT) {
                        combinedPath.append(String.format(Locale.US, " L %.4f %.4f", endX, endY));
                    } else if (part.getType() == ContourPolygon.PolygonPart.Type.ARC) {
                        double r = Math.sqrt(
                                Math.pow(part.getEndX() - part.getXCenter(), 2) +
                                Math.pow(part.getEndY() - part.getYCenter(), 2));
                        double rConverted = baseOptions.toOutputUnit(r);
                        int sweep = part.isClockwise() ? 0 : 1;
                        combinedPath.append(String.format(Locale.US, " A %.4f %.4f 0 0 %d %.4f %.4f",
                                rConverted, rConverted, sweep, endX, endY));
                    }
                }
                combinedPath.append(" Z ");
            }

            String fillColor = surface.getPolarity() == Polarity.NEGATIVE ?
                    (baseOptions.getBackgroundColor() != null ? baseOptions.getBackgroundColor() : "#FFFFFF") :
                    color;
            writer.write(String.format(Locale.US, "      <path d=\"%s\" fill=\"%s\" fill-rule=\"evenodd\" stroke=\"none\"/>\n",
                    combinedPath.toString().trim(), fillColor));
        } else if (feature instanceof Text text) {
            if (text.getText() != null && !text.getText().isEmpty()) {
                double fontSize = baseOptions.toOutputUnit(text.getYsize() > 0 ? text.getYsize() : baseOptions.getDefaultFontSize());
                double x = baseOptions.toOutputUnit(text.getX());
                double y = baseOptions.toOutputUnit(text.getY());
                String escaped = text.getText()
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;");
                writer.write(String.format(Locale.US, "      <text x=\"%.4f\" y=\"%.4f\" " +
                                "font-size=\"%.4f\" fill=\"%s\" transform=\"scale(1,-1) translate(0,%.4f)\">%s</text>\n",
                        x, -y, fontSize, color, 0.0, escaped));
            }
        }
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

    private static class LayerInfo {
        String name;
        Layer layer;
        MatrixLayer matrixLayer;
        String color;
        String type;
    }
}

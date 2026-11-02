package com.deltaproto.deltaodbpp.export.gerber;

import com.deltaproto.deltaodbpp.model.Arc;
import com.deltaproto.deltaodbpp.model.Feature;
import com.deltaproto.deltaodbpp.model.Features;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Layer;
import com.deltaproto.deltaodbpp.model.Line;
import com.deltaproto.deltaodbpp.model.MatrixLayer;
import com.deltaproto.deltaodbpp.model.Pad;
import com.deltaproto.deltaodbpp.model.Step;
import com.deltaproto.deltaodbpp.model.Tool;
import com.deltaproto.deltaodbpp.model.Tools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Converts a parsed ODB++ {@link Job} into a set of Gerber X2 + Excellon
 * files — the deliverable format most PCB manufacturers require.
 *
 * Per matrix layer (context BOARD):
 * <ul>
 *   <li>SIGNAL / POWER_GROUND / MIXED → copper Gerber (Copper,Ln,Top/Inr/Bot)</li>
 *   <li>SOLDER_MASK / SILK_SCREEN / SOLDER_PASTE → Gerber with the matching
 *       X2 file function and Top/Bot side derived from matrix row order</li>
 *   <li>DRILL → Excellon, split into plated and non-plated files by each hole's
 *       {@code .drill} attribute or tool; slots (oval/line drill features) become G85 records</li>
 *   <li>ROUT → Gerber strokes of the milling path (Other,Rout)</li>
 *   <li>step profile → board outline Gerber (Profile,NP)</li>
 * </ul>
 *
 * <p>Files are named the way KiCad names its plots, with Protel extensions,
 * which every fab and CAM tool recognises on sight: {@code F_Cu.gtl},
 * {@code In1_Cu.g1}, {@code B_Mask.gbs}, {@code F_Silkscreen.gto},
 * {@code Edge_Cuts.gm1}, {@code PTH.drl}, {@code NPTH.drl}, … An optional
 * {@link #setFileNamePrefix prefix} (typically {@code "<project>-"}) is put in
 * front. Every fabrication layer present in the step is written, even when it
 * carries no features, so the set is complete and predictable.
 */
public class OdbToGerberConverter {

    private static final String GENERATION_SOFTWARE = "DeltaProto,odbpp-lib,1.0";
    /**
     * Outline stroke used when no outline drawing layer reveals the design's
     * own width: 1 mil, what Altium plots its profile with.
     */
    private static final double DEFAULT_PROFILE_STROKE_MM = 0.0254;
    private static final Pattern OUTLINE_LAYER_NAME =
            Pattern.compile(".*(edge|outline|profile|contour|board[_ .-]?(shape|cut)).*");

    /** One generated output file. */
    public static class OutputFile {
        public final String fileName;
        public final String content;
        public final String fileFunction;

        OutputFile(String fileName, String content, String fileFunction) {
            this.fileName = fileName;
            this.content = content;
            this.fileFunction = fileFunction;
        }
    }

    /** Conversion result: generated files plus accumulated warnings. */
    public static class Result {
        public final List<OutputFile> files = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();

        public void writeTo(Path directory) throws IOException {
            Files.createDirectories(directory);
            for (OutputFile file : files) {
                Files.writeString(directory.resolve(file.fileName), file.content,
                        StandardCharsets.UTF_8);
            }
        }

        /** Writes all files into one zip archive (the deliverable a fab expects). */
        public void writeZip(OutputStream out) throws IOException {
            try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
                for (OutputFile file : files) {
                    zip.putNextEntry(new ZipEntry(file.fileName));
                    zip.write(file.content.getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
        }

        /** The zip archive of all files as bytes. */
        public byte[] toZip() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            writeZip(bytes);
            return bytes.toByteArray();
        }
    }

    /** Converts the job's first step (a single-board job has exactly one). */
    public Result convert(Job job) {
        if (job.getSteps() == null || job.getSteps().isEmpty()) {
            Result result = new Result();
            result.warnings.add("Job has no steps");
            return result;
        }
        return convert(job, job.getSteps().keySet().iterator().next());
    }

    private boolean subtractSoldermaskFromLegend = false;
    private String fileNamePrefix = "";
    private Double profileStrokeWidthMm = null;

    /**
     * When enabled, the soldermask openings of the same side are cleared out
     * of each legend (silkscreen) Gerber, so no legend ink can land on exposed
     * copper — the same thing KiCad's "subtract soldermask from silkscreen"
     * plot option does. Off by default: ODB++ legend layers are exported as
     * drawn.
     */
    public OdbToGerberConverter setSubtractSoldermaskFromLegend(boolean enabled) {
        this.subtractSoldermaskFromLegend = enabled;
        return this;
    }

    /** Prefix for every generated file name, e.g. {@code "myboard-"}. */
    public OdbToGerberConverter setFileNamePrefix(String prefix) {
        this.fileNamePrefix = prefix == null ? "" : prefix;
        return this;
    }

    /**
     * Stroke width of the board outline Gerber. By default the width is taken
     * from the design's own outline drawing layer (a DOCUMENT/ROUT layer named
     * like {@code edge.cuts}, {@code outline}, …) when it draws with a single
     * round symbol, and is 1&nbsp;mil (0.0254&nbsp;mm) otherwise.
     */
    public OdbToGerberConverter setProfileStrokeWidthMm(Double widthMm) {
        this.profileStrokeWidthMm = widthMm;
        return this;
    }

    public Result convert(Job job, String stepName) {
        Result result = new Result();
        Step step = findStep(job, stepName);
        if (step == null) {
            result.warnings.add("Step not found: " + stepName);
            return result;
        }

        List<MatrixLayer> boardLayers = job.getMatrix().getLayers().stream()
                .filter(l -> "BOARD".equalsIgnoreCase(l.getContext()))
                .sorted(Comparator.comparingInt(MatrixLayer::getRow))
                .toList();

        // Copper layer numbering (L1 = top) from matrix row order
        Map<String, Integer> copperIndex = new LinkedHashMap<>();
        int copperCount = 0;
        int firstCopperRow = Integer.MAX_VALUE;
        int lastCopperRow = Integer.MIN_VALUE;
        for (MatrixLayer ml : boardLayers) {
            if (isCopper(ml)) {
                copperIndex.put(key(ml.getName()), ++copperCount);
                firstCopperRow = Math.min(firstCopperRow, ml.getRow());
                lastCopperRow = Math.max(lastCopperRow, ml.getRow());
            }
        }

        // Soldermask layer per side, for legend subtraction
        Map<String, Layer> maskBySide = new LinkedHashMap<>();
        for (MatrixLayer ml : boardLayers) {
            if ("SOLDER_MASK".equalsIgnoreCase(ml.getType())) {
                Layer layer = findLayer(step, ml.getName());
                if (layer != null && layer.getFeatures() != null) {
                    maskBySide.putIfAbsent(side(ml, firstCopperRow, lastCopperRow), layer);
                }
            }
        }

        Set<String> usedNames = new HashSet<>();
        for (MatrixLayer ml : boardLayers) {
            Layer layer = findLayer(step, ml.getName());
            if (layer == null || layer.getFeatures() == null) {
                continue;
            }
            String type = ml.getType() == null ? "" : ml.getType().toUpperCase(Locale.ROOT);
            switch (type) {
                case "SIGNAL", "POWER_GROUND", "MIXED" -> {
                    int index = copperIndex.get(key(ml.getName()));
                    String side = index == 1 ? "Top" : index == copperCount ? "Bot" : "Inr";
                    String function = "Copper,L" + index + "," + side;
                    String base = side.equals("Top") ? "F_Cu.gtl"
                            : side.equals("Bot") ? "B_Cu.gbl"
                            : "In" + (index - 1) + "_Cu.g" + (index - 1);
                    addGerber(result, job, step.getProfile(), layer, ml, function, unique(usedNames, base), null);
                }
                case "SOLDER_MASK" -> {
                    String side = side(ml, firstCopperRow, lastCopperRow);
                    addGerber(result, job, step.getProfile(), layer, ml, "Soldermask," + side,
                            unique(usedNames, side.equals("Top") ? "F_Mask.gts" : "B_Mask.gbs"), null);
                }
                case "SILK_SCREEN" -> {
                    String side = side(ml, firstCopperRow, lastCopperRow);
                    Layer mask = subtractSoldermaskFromLegend ? maskBySide.get(side) : null;
                    addGerber(result, job, step.getProfile(), layer, ml, "Legend," + side,
                            unique(usedNames, side.equals("Top") ? "F_Silkscreen.gto" : "B_Silkscreen.gbo"),
                            mask);
                }
                case "SOLDER_PASTE" -> {
                    String side = side(ml, firstCopperRow, lastCopperRow);
                    addGerber(result, job, step.getProfile(), layer, ml, "Paste," + side,
                            unique(usedNames, side.equals("Top") ? "F_Paste.gtp" : "B_Paste.gbp"), null);
                }
                case "DRILL" -> addExcellon(result, layer, ml, copperIndex, copperCount, usedNames);
                case "ROUT" -> addGerber(result, job, step.getProfile(), layer, ml, "Other,Rout",
                        unique(usedNames, "Rout.gbr"), null);
                default -> {
                    // COMPONENT, DOCUMENT, DIELECTRIC, MASK etc. — not part of
                    // a fabrication data set
                }
            }
        }

        addProfile(result, job, step, boardLayers, usedNames);
        return result;
    }

    private void addGerber(Result result, Job job, Features profile, Layer layer, MatrixLayer ml,
                           String fileFunction, String fileName, Layer subtractLayer) {
        GerberWriter writer = new GerberWriter();
        ApertureRegistry registry = new ApertureRegistry();
        writer.setApertureRegistry(registry);
        writer.addFileAttribute(".GenerationSoftware", GENERATION_SOFTWARE);
        writer.addFileAttribute(".FileFunction", fileFunction);
        writer.addFileAttribute(".FilePolarity", filePolarity(ml, fileFunction));

        GerberLayerExporter exporter = new GerberLayerExporter(job.getStandardFont(), job.getSymbols());
        if (!"NEGATIVE".equalsIgnoreCase(ml.getPolarity())) {
            // On a negative (plane) layer the outline stroke is the copper
            // pull-back from the board edge and must stay.
            exporter.dropStrokesOnOutline(profile);
        }
        exporter.export(layer.getFeatures(), writer, registry);
        if (subtractLayer != null) {
            exporter.exportInverted(subtractLayer.getFeatures(), writer, registry);
        }
        prefixWarnings(result, layer.getName(), exporter.getWarnings());

        result.files.add(new OutputFile(fileNamePrefix + fileName, writer.build(), fileFunction));
    }

    /**
     * A soldermask Gerber's image is the openings, i.e. where mask is absent,
     * so its polarity is Negative regardless of how the ODB++ layer is drawn.
     * The ODB++ matrix polarity flips the others.
     */
    private static String filePolarity(MatrixLayer ml, String fileFunction) {
        if (fileFunction.startsWith("Soldermask")) {
            return "Negative";
        }
        return "NEGATIVE".equalsIgnoreCase(ml.getPolarity()) ? "Negative" : "Positive";
    }

    /**
     * Writes the drill layer as Excellon, split by plating. The ODB++ layer's own plating
     * (tools file, name) is only the default: Cadence, Mentor and PADS keep plated and
     * non-plated holes in one {@code drill} layer and mark each hole with the {@code .drill}
     * feature attribute or the tool's type, and Altium before AD 22 does the same. Holes
     * marked non-plated go to {@code NPTH.drl}, the rest to {@code PTH.drl}.
     */
    private void addExcellon(Result result, Layer layer, MatrixLayer ml,
                             Map<String, Integer> copperIndex, int copperCount, Set<String> usedNames) {
        boolean layerPlated = isPlated(layer, ml);
        Features all = layer.getFeatures();
        List<Feature> plated = new ArrayList<>();
        List<Feature> nonPlated = new ArrayList<>();
        for (Feature feature : all.getFeatures()) {
            Boolean p = featurePlating(feature, all, layer.getTools());
            if (p == null ? layerPlated : p) {
                plated.add(feature);
            } else {
                nonPlated.add(feature);
            }
        }
        if (!plated.isEmpty() || nonPlated.isEmpty() && layerPlated) {
            writeExcellon(result, layer, ml, subset(all, plated), true, copperIndex, copperCount, usedNames);
        }
        if (!nonPlated.isEmpty() || plated.isEmpty() && !layerPlated) {
            writeExcellon(result, layer, ml, subset(all, nonPlated), false, copperIndex, copperCount, usedNames);
        }
    }

    private void writeExcellon(Result result, Layer layer, MatrixLayer ml, Features features, boolean plated,
                               Map<String, Integer> copperIndex, int copperCount, Set<String> usedNames) {
        int from = copperIndex.getOrDefault(key(ml.getStartName()), 1);
        int to = copperIndex.getOrDefault(key(ml.getEndName()), copperCount == 0 ? 1 : copperCount);
        if (from > to) {
            int tmp = from;
            from = to;
            to = tmp;
        }
        String kind = plated ? "PTH" : "NPTH";
        String fileFunction = drillFileFunction(plated, from, to, copperCount);
        boolean throughAll = from == 1 && to == Math.max(copperCount, 1);
        String base = throughAll ? kind + ".drl" : kind + "-L" + from + "-L" + to + ".drl";

        ExcellonWriter.Result drill = new ExcellonWriter().export(features, layer.getTools(), fileFunction);
        prefixWarnings(result, layer.getName(), drill.warnings);

        result.files.add(new OutputFile(fileNamePrefix + unique(usedNames, base),
                drill.content, fileFunction));
    }

    /** The same symbol and attribute tables over a subset of the features. */
    private static Features subset(Features all, List<Feature> features) {
        Features out = new Features();
        out.setUnits(all.getUnits());
        out.setSymbolTable(all.getSymbolTable());
        out.setSymbolMirrorFlags(all.getSymbolMirrorFlags());
        out.setAttributeNames(all.getAttributeNames());
        out.setAttributeTextStrings(all.getAttributeTextStrings());
        out.setFeatures(features);
        return out;
    }

    /**
     * Whether one drill feature is plated: from its {@code .drill} attribute
     * (plated / non_plated / via, written as the option index 0 / 1 / 2 or as the word), else
     * from the tools-file tool of its diameter, else null (the layer decides).
     */
    static Boolean featurePlating(Feature feature, Features features, Tools tools) {
        Map<Integer, String> attributes = new LinkedHashMap<>();
        int symbolNumber;
        if (feature instanceof Pad pad) {
            attributes.putAll(pad.getAttributes());
            if (pad.getAttributeNumber() != null) {
                attributes.putIfAbsent(pad.getAttributeNumber(), pad.getAttributeValue());
            }
            symbolNumber = pad.getSymbolNumber();
        } else if (feature instanceof Line line) {
            if (line.getAttributeNumber() != null) {
                attributes.put(line.getAttributeNumber(), line.getAttributeValue());
            }
            symbolNumber = line.getSymbolNumber();
        } else if (feature instanceof Arc arc) {
            if (arc.getValue() != null) {
                attributes.put(arc.getAtr(), arc.getValue());
            }
            symbolNumber = arc.getSymbolNumber();
        } else {
            return null;
        }
        for (Map.Entry<Integer, String> e : attributes.entrySet()) {
            if (".drill".equalsIgnoreCase(features.getAttributeName(e.getKey()))) {
                String v = e.getValue() == null ? "" : e.getValue().trim().toLowerCase(Locale.ROOT);
                return switch (v) {
                    case "1", "non_plated" -> false;
                    case "0", "plated", "2", "via" -> true;
                    default -> null;
                };
            }
        }
        if (tools != null && tools.getTools() != null) {
            double unitToMm = features.isMillimeters() ? 0.001 : 0.0254;
            OdbSymbolShape shape = OdbSymbolShape.parse(features.getSymbolName(symbolNumber), unitToMm);
            if (shape != null) {
                for (Tool tool : tools.getTools()) {
                    if (tool.getFinishSize() > 0 && Math.abs(tool.getFinishSize() - shape.strokeDiameter()) <= 0.005) {
                        return tool.getType() != Tool.ToolType.NON_PLATED;
                    }
                }
            }
        }
        return null;
    }

    /**
     * The X2 {@code .FileFunction} of a drill file spanning copper layers {@code from} to
     * {@code to} (1 = top): {@code Plated,1,4,PTH} through the board, {@code Blind} when one
     * end is an outer layer, {@code Buried} when neither is.
     */
    static String drillFileFunction(boolean plated, int from, int to, int copperCount) {
        int last = Math.max(copperCount, 1);
        String span = from == 1 && to == last ? (plated ? "PTH" : "NPTH")
                : from == 1 || to == last ? "Blind" : "Buried";
        return (plated ? "Plated," : "NonPlated,") + from + "," + to + "," + span;
    }

    private void addProfile(Result result, Job job, Step step, List<MatrixLayer> boardLayers,
                            Set<String> usedNames) {
        if (step.getProfile() == null || step.getProfile().getFeatures().isEmpty()) {
            return;
        }
        GerberWriter writer = new GerberWriter();
        ApertureRegistry registry = new ApertureRegistry();
        writer.setApertureRegistry(registry);
        writer.addFileAttribute(".GenerationSoftware", GENERATION_SOFTWARE);
        writer.addFileAttribute(".FileFunction", "Profile,NP");
        writer.addFileAttribute(".FilePolarity", "Positive");

        // Outline drawing layers are usually MISC context, so scan the whole matrix.
        double stroke = profileStrokeWidthMm != null
                ? profileStrokeWidthMm
                : detectOutlineStrokeWidth(step, job.getMatrix().getLayers());
        GerberLayerExporter exporter = new GerberLayerExporter(job.getStandardFont(), job.getSymbols());
        exporter.exportAsOutline(step.getProfile(), writer, registry, stroke);
        prefixWarnings(result, "profile", exporter.getWarnings());

        result.files.add(new OutputFile(fileNamePrefix + unique(usedNames, "Edge_Cuts.gm1"),
                writer.build(), "Profile,NP"));
    }

    /**
     * The stroke width the design itself uses for its outline drawing, when a
     * DOCUMENT/ROUT layer named like an outline layer draws all its lines and
     * arcs with one round symbol; {@link #DEFAULT_PROFILE_STROKE_MM} otherwise.
     */
    private double detectOutlineStrokeWidth(Step step, List<MatrixLayer> boardLayers) {
        for (MatrixLayer ml : boardLayers) {
            String type = ml.getType() == null ? "" : ml.getType().toUpperCase(Locale.ROOT);
            if (!type.equals("DOCUMENT") && !type.equals("ROUT")) {
                continue;
            }
            if (!OUTLINE_LAYER_NAME.matcher(key(ml.getName())).matches()) {
                continue;
            }
            Layer layer = findLayer(step, ml.getName());
            if (layer == null || layer.getFeatures() == null) {
                continue;
            }
            Features features = layer.getFeatures();
            double unitToMm = features.isMillimeters() ? 0.001 : 0.0254;
            Double width = null;
            for (Feature feature : features.getFeatures()) {
                int symbolNumber;
                if (feature instanceof Line line) {
                    symbolNumber = line.getSymbolNumber();
                } else if (feature instanceof Arc arc) {
                    symbolNumber = arc.getSymbolNumber();
                } else {
                    continue;
                }
                OdbSymbolShape shape = OdbSymbolShape.parse(features.getSymbolName(symbolNumber), unitToMm);
                if (shape == null || shape.kind != OdbSymbolShape.Kind.ROUND || shape.width <= 0) {
                    width = null;
                    break;
                }
                if (width == null) {
                    width = shape.width;
                } else if (Math.abs(width - shape.width) > 1e-6) {
                    width = null;
                    break;
                }
            }
            if (width != null) {
                return width;
            }
        }
        return DEFAULT_PROFILE_STROKE_MM;
    }

    /**
     * A drill layer is plated unless its tools or name say otherwise. VIA
     * tools are plated by definition.
     */
    private boolean isPlated(Layer layer, MatrixLayer ml) {
        if (layer.getTools() != null && layer.getTools().getTools() != null
                && !layer.getTools().getTools().isEmpty()) {
            long nonPlated = layer.getTools().getTools().stream()
                    .filter(t -> t.getType() == Tool.ToolType.NON_PLATED).count();
            return nonPlated * 2 < layer.getTools().getTools().size();
        }
        String name = ml.getName() == null ? "" : ml.getName().toLowerCase(Locale.ROOT);
        return !(name.contains("non-plated") || name.contains("non_plated")
                || name.contains("npth"));
    }

    private String side(MatrixLayer ml, int firstCopperRow, int lastCopperRow) {
        if (ml.getRow() < firstCopperRow) {
            return "Top";
        }
        if (ml.getRow() > lastCopperRow) {
            return "Bot";
        }
        String name = ml.getName() == null ? "" : ml.getName().toLowerCase(Locale.ROOT);
        return name.contains("bot") ? "Bot" : "Top";
    }

    private boolean isCopper(MatrixLayer ml) {
        String type = ml.getType() == null ? "" : ml.getType().toUpperCase(Locale.ROOT);
        return type.equals("SIGNAL") || type.equals("POWER_GROUND") || type.equals("MIXED");
    }

    private Step findStep(Job job, String stepName) {
        if (job.getSteps() == null) {
            return null;
        }
        for (Map.Entry<String, Step> e : job.getSteps().entrySet()) {
            if (e.getKey().equalsIgnoreCase(stepName)) {
                return e.getValue();
            }
        }
        return null;
    }

    private Layer findLayer(Step step, String name) {
        if (step.getLayersByName() == null || name == null) {
            return null;
        }
        for (Map.Entry<String, Layer> e : step.getLayersByName().entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    private void prefixWarnings(Result result, String layerName, List<String> warnings) {
        for (String warning : warnings) {
            result.warnings.add("[" + layerName + "] " + warning);
        }
    }

    /** Keeps file names unique when a design has several layers of one kind. */
    private static String unique(Set<String> used, String base) {
        String name = base;
        int n = 2;
        while (!used.add(name)) {
            int dot = base.lastIndexOf('.');
            name = base.substring(0, dot) + "-" + n++ + base.substring(dot);
        }
        return name;
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}

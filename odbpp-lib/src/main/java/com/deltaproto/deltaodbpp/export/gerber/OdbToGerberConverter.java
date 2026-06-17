package com.deltaproto.deltaodbpp.export.gerber;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Layer;
import com.deltaproto.deltaodbpp.model.MatrixLayer;
import com.deltaproto.deltaodbpp.model.Step;
import com.deltaproto.deltaodbpp.model.Tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts a parsed ODB++ {@link Job} into a set of Gerber X2 + Excellon
 * files — the deliverable format most PCB manufacturers require.
 *
 * Per matrix layer (context BOARD):
 * <ul>
 *   <li>SIGNAL / POWER_GROUND / MIXED → copper Gerber (Copper,Ln,Top/Inr/Bot)</li>
 *   <li>SOLDER_MASK / SILK_SCREEN / SOLDER_PASTE → Gerber with the matching
 *       X2 file function and Top/Bot side derived from matrix row order</li>
 *   <li>DRILL → Excellon; slots (line/arc drill features) become G85 records</li>
 *   <li>ROUT → Gerber strokes of the milling path (Other,Rout)</li>
 *   <li>step profile → board outline Gerber (Profile,NP)</li>
 * </ul>
 */
public class OdbToGerberConverter {

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

        for (MatrixLayer ml : boardLayers) {
            Layer layer = findLayer(step, ml.getName());
            if (layer == null || layer.getFeatures() == null
                    || layer.getFeatures().getFeatures().isEmpty()) {
                continue;
            }
            String type = ml.getType() == null ? "" : ml.getType().toUpperCase(Locale.ROOT);
            switch (type) {
                case "SIGNAL", "POWER_GROUND", "MIXED" -> {
                    int index = copperIndex.get(key(ml.getName()));
                    String side = index == 1 ? "Top" : index == copperCount ? "Bot" : "Inr";
                    String function = "Copper,L" + index + "," + side;
                    addGerber(result, job, layer, ml, function, ".gbr");
                }
                case "SOLDER_MASK" -> addGerber(result, job, layer, ml,
                        "Soldermask," + side(ml, firstCopperRow, lastCopperRow), ".gbr");
                case "SILK_SCREEN" -> addGerber(result, job, layer, ml,
                        "Legend," + side(ml, firstCopperRow, lastCopperRow), ".gbr");
                case "SOLDER_PASTE" -> addGerber(result, job, layer, ml,
                        "Paste," + side(ml, firstCopperRow, lastCopperRow), ".gbr");
                case "DRILL" -> addExcellon(result, layer, ml, copperIndex, copperCount);
                case "ROUT" -> addGerber(result, job, layer, ml, "Other,Rout", ".gbr");
                default -> {
                    // COMPONENT, DOCUMENT, DIELECTRIC, MASK etc. — not part of
                    // a fabrication data set
                }
            }
        }

        addProfile(result, job, step);
        return result;
    }

    private void addGerber(Result result, Job job, Layer layer, MatrixLayer ml,
                           String fileFunction, String extension) {
        GerberWriter writer = new GerberWriter();
        ApertureRegistry registry = new ApertureRegistry();
        writer.setApertureRegistry(registry);
        writer.addFileAttribute(".GenerationSoftware", "DeltaProto,odbpp-lib,1.0");
        writer.addFileAttribute(".FileFunction", fileFunction);
        writer.addFileAttribute(".FilePolarity",
                "NEGATIVE".equalsIgnoreCase(ml.getPolarity()) ? "Negative" : "Positive");

        GerberLayerExporter exporter = new GerberLayerExporter(job.getStandardFont(), job.getSymbols());
        exporter.export(layer.getFeatures(), writer, registry);
        prefixWarnings(result, layer.getName(), exporter.getWarnings());

        result.files.add(new OutputFile(
                sanitize(layer.getName()) + extension, writer.build(), fileFunction));
    }

    private void addExcellon(Result result, Layer layer, MatrixLayer ml,
                             Map<String, Integer> copperIndex, int copperCount) {
        boolean plated = isPlated(layer, ml);
        int from = copperIndex.getOrDefault(key(ml.getStartName()), 1);
        int to = copperIndex.getOrDefault(key(ml.getEndName()), copperCount == 0 ? 1 : copperCount);
        if (from > to) {
            int tmp = from;
            from = to;
            to = tmp;
        }
        String fileFunction = (plated ? "Plated," : "NonPlated,") + from + "," + to
                + (plated ? ",PTH" : ",NPTH");

        ExcellonWriter.Result drill = new ExcellonWriter().export(
                layer.getFeatures(), layer.getTools());
        prefixWarnings(result, layer.getName(), drill.warnings);

        result.files.add(new OutputFile(sanitize(layer.getName()) + ".drl",
                drill.content, fileFunction));
    }

    private void addProfile(Result result, Job job, Step step) {
        if (step.getProfile() == null || step.getProfile().getFeatures().isEmpty()) {
            return;
        }
        GerberWriter writer = new GerberWriter();
        ApertureRegistry registry = new ApertureRegistry();
        writer.setApertureRegistry(registry);
        writer.addFileAttribute(".GenerationSoftware", "DeltaProto,odbpp-lib,1.0");
        writer.addFileAttribute(".FileFunction", "Profile,NP");
        writer.addFileAttribute(".FilePolarity", "Positive");

        GerberLayerExporter exporter = new GerberLayerExporter(job.getStandardFont(), job.getSymbols());
        exporter.exportAsOutline(step.getProfile(), writer, registry, 0.15);
        prefixWarnings(result, "profile", exporter.getWarnings());

        result.files.add(new OutputFile("profile.gbr", writer.build(), "Profile,NP"));
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

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private static String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
    }
}

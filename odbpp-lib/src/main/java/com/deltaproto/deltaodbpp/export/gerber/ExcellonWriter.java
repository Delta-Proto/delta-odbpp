package com.deltaproto.deltaodbpp.export.gerber;

import com.deltaproto.deltaodbpp.model.Arc;
import com.deltaproto.deltaodbpp.model.Feature;
import com.deltaproto.deltaodbpp.model.Features;
import com.deltaproto.deltaodbpp.model.Line;
import com.deltaproto.deltaodbpp.model.Pad;
import com.deltaproto.deltaodbpp.model.Tool;
import com.deltaproto.deltaodbpp.model.Tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts an ODB++ drill layer into an Excellon (XNC-style) drill file.
 *
 * <ul>
 *   <li>Pad features become drill hits (X/Y).</li>
 *   <li>Line features are slots, emitted as canonical G85 slot records —
 *       the representation fabs and CAM tools universally accept.</li>
 *   <li>Arc features (curved milling) have no Excellon arc-slot equivalent,
 *       so they are tessellated into chains of short G85 slots with a
 *       0.01&nbsp;mm chord tolerance. A full circle (start == end) is a
 *       milled circular cutout and is tessellated likewise.</li>
 * </ul>
 *
 * Coordinates are emitted in mm with explicit decimal points, which removes
 * all leading/trailing-zero ambiguity.
 *
 * Tool numbers reuse the layer's tools file (matched by diameter) so the
 * output cross-references the ODB++ source; features whose diameter has no
 * tools-file entry get newly allocated numbers.
 */
public class ExcellonWriter {

    private static final double TOOL_MATCH_TOLERANCE_MM = 0.005;
    private static final double ARC_CHORD_TOLERANCE_MM = 0.01;

    /** Conversion result: file content plus diagnostics. */
    public static class Result {
        public final String content;
        public final List<String> warnings;
        public final int holeCount;
        public final int slotCount;

        Result(String content, List<String> warnings, int holeCount, int slotCount) {
            this.content = content;
            this.warnings = warnings;
            this.holeCount = holeCount;
            this.slotCount = slotCount;
        }
    }

    public Result export(Features features, Tools tools) {
        List<String> warnings = new ArrayList<>();
        double unitToMm = features.isMillimeters() ? 0.001 : 0.0254;

        // diameter (rounded) -> tool number and its operations
        Map<Long, Integer> toolNumberByDiameter = new LinkedHashMap<>();
        Map<Integer, Double> diameterByToolNumber = new LinkedHashMap<>();
        Map<Integer, StringBuilder> opsByToolNumber = new LinkedHashMap<>();
        int nextSynthetic = nextSyntheticToolNumber(tools);

        int holes = 0;
        int slots = 0;

        for (Feature feature : features.getFeatures()) {
            double diameter;
            if (feature instanceof Pad pad) {
                diameter = featureDiameter(features, pad.getSymbolNumber(), unitToMm, warnings);
            } else if (feature instanceof Line line) {
                diameter = featureDiameter(features, line.getSymbolNumber(), unitToMm, warnings);
            } else if (feature instanceof Arc arc) {
                diameter = featureDiameter(features, arc.getSymbolNumber(), unitToMm, warnings);
            } else {
                warnings.add("Non-drill feature " + feature.getClass().getSimpleName()
                        + " skipped in Excellon export");
                continue;
            }

            int toolNumber = toolNumberByDiameter.computeIfAbsent(diameterKey(diameter), k -> {
                Integer fromToolsFile = matchToolsFile(tools, diameter);
                int num = fromToolsFile != null ? fromToolsFile : -1;
                return num;
            });
            if (toolNumber == -1) {
                toolNumber = nextSynthetic++;
                toolNumberByDiameter.put(diameterKey(diameter), toolNumber);
                warnings.add(String.format(Locale.ROOT,
                        "No tools-file entry for diameter %.4f mm; assigned T%d",
                        diameter, toolNumber));
            }
            diameterByToolNumber.putIfAbsent(toolNumber, diameter);
            StringBuilder ops = opsByToolNumber.computeIfAbsent(toolNumber, k -> new StringBuilder());

            if (feature instanceof Pad pad) {
                ops.append('X').append(fmt(pad.getX())).append('Y').append(fmt(pad.getY())).append('\n');
                holes++;
            } else if (feature instanceof Line line) {
                appendSlot(ops, line.getXs(), line.getYs(), line.getXe(), line.getYe());
                slots++;
            } else if (feature instanceof Arc arc) {
                slots += appendArcSlots(ops, arc);
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("M48\n");
        out.append("METRIC\n");
        for (Map.Entry<Integer, Double> e : diameterByToolNumber.entrySet()) {
            out.append('T').append(e.getKey()).append('C')
                    .append(String.format(Locale.ROOT, "%.4f", e.getValue())).append('\n');
        }
        out.append("%\n");
        out.append("G90\n");
        out.append("G05\n");
        for (Map.Entry<Integer, StringBuilder> e : opsByToolNumber.entrySet()) {
            out.append('T').append(e.getKey()).append('\n');
            out.append(e.getValue());
        }
        out.append("M30\n");

        return new Result(out.toString(), warnings, holes, slots);
    }

    private void appendSlot(StringBuilder ops, double xs, double ys, double xe, double ye) {
        ops.append('X').append(fmt(xs)).append('Y').append(fmt(ys))
                .append("G85")
                .append('X').append(fmt(xe)).append('Y').append(fmt(ye)).append('\n');
    }

    /** Tessellates an arc (or full circle when start == end) into G85 slots. */
    private int appendArcSlots(StringBuilder ops, Arc arc) {
        double cx = arc.getXc();
        double cy = arc.getYc();
        double radius = Math.hypot(arc.getXs() - cx, arc.getYs() - cy);
        if (radius <= 0) {
            appendSlot(ops, arc.getXs(), arc.getYs(), arc.getXe(), arc.getYe());
            return 1;
        }
        boolean clockwise = "Y".equalsIgnoreCase(arc.getCw());
        double startAngle = Math.atan2(arc.getYs() - cy, arc.getXs() - cx);
        double endAngle = Math.atan2(arc.getYe() - cy, arc.getXe() - cx);

        double sweep;
        boolean fullCircle = Math.abs(arc.getXs() - arc.getXe()) < 1e-9
                && Math.abs(arc.getYs() - arc.getYe()) < 1e-9;
        if (fullCircle) {
            sweep = 2 * Math.PI;
        } else if (clockwise) {
            sweep = startAngle - endAngle;
            if (sweep <= 0) {
                sweep += 2 * Math.PI;
            }
        } else {
            sweep = endAngle - startAngle;
            if (sweep <= 0) {
                sweep += 2 * Math.PI;
            }
        }

        // Segment count from chord (sagitta) tolerance
        double maxStep = 2 * Math.acos(Math.max(0, 1 - ARC_CHORD_TOLERANCE_MM / radius));
        int segments = Math.max(1, (int) Math.ceil(sweep / Math.max(maxStep, 1e-3)));

        double px = arc.getXs();
        double py = arc.getYs();
        for (int i = 1; i <= segments; i++) {
            double angle = clockwise
                    ? startAngle - sweep * i / segments
                    : startAngle + sweep * i / segments;
            double nx = cx + radius * Math.cos(angle);
            double ny = cy + radius * Math.sin(angle);
            appendSlot(ops, px, py, nx, ny);
            px = nx;
            py = ny;
        }
        return segments;
    }

    private double featureDiameter(Features features, int symbolNumber,
                                   double unitToMm, List<String> warnings) {
        String symbolName = features.getSymbolName(symbolNumber);
        OdbSymbolShape shape = OdbSymbolShape.parse(symbolName, unitToMm);
        if (shape == null) {
            warnings.add("Unsupported drill symbol '" + symbolName
                    + "'; defaulting to 0.1 mm");
            return 0.1;
        }
        if (shape.kind != OdbSymbolShape.Kind.ROUND) {
            warnings.add("Non-round drill symbol '" + symbolName
                    + "' uses its stroke diameter " + shape.strokeDiameter() + " mm");
        }
        return shape.strokeDiameter();
    }

    private Integer matchToolsFile(Tools tools, double diameter) {
        if (tools == null || tools.getTools() == null) {
            return null;
        }
        for (Tool tool : tools.getTools()) {
            if (tool.getFinishSize() > 0
                    && Math.abs(tool.getFinishSize() - diameter) <= TOOL_MATCH_TOLERANCE_MM) {
                return tool.getNum();
            }
        }
        return null;
    }

    private int nextSyntheticToolNumber(Tools tools) {
        int max = 0;
        if (tools != null && tools.getTools() != null) {
            for (Tool tool : tools.getTools()) {
                max = Math.max(max, tool.getNum());
            }
        }
        return max + 1;
    }

    private static long diameterKey(double diameter) {
        return Math.round(diameter * 10_000);
    }

    private static String fmt(double mm) {
        String s = String.format(Locale.ROOT, "%.4f", mm);
        s = s.replaceAll("0+$", "");
        if (s.endsWith(".")) {
            s += "0";
        }
        return s;
    }
}

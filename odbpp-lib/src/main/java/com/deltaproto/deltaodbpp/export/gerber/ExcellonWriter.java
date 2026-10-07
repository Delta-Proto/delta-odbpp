package com.deltaproto.deltaodbpp.export.gerber;

import com.deltaproto.deltaodbpp.model.Arc;
import com.deltaproto.deltaodbpp.model.Feature;
import com.deltaproto.deltaodbpp.model.Features;
import com.deltaproto.deltaodbpp.model.Line;
import com.deltaproto.deltaodbpp.model.Pad;
import com.deltaproto.deltaodbpp.model.Tool;
import com.deltaproto.deltaodbpp.model.Tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Converts an ODB++ drill layer into an Excellon (XNC-style) drill file.
 *
 * <ul>
 *   <li>Round pad features become drill hits (X/Y).</li>
 *   <li>Oval (and, approximately, rectangular) pad features are slots: the
 *       hole is the symbol's smaller dimension and the slot runs along its
 *       longer axis, honouring the pad rotation. Line features are slots too.
 *       Slots are emitted as canonical G85 records — the representation fabs
 *       and CAM tools universally accept.</li>
 *   <li>Arc features (curved milling) have no Excellon arc-slot equivalent,
 *       so they are tessellated into chains of short G85 slots with a
 *       0.01&nbsp;mm chord tolerance. A full circle (start == end) is a
 *       milled circular cutout and is tessellated likewise.</li>
 * </ul>
 *
 * Tools are numbered T1.. in ascending diameter order, one per distinct
 * diameter, the way every EDA drill writer does it; the ODB++ tools file only
 * contributes the tool <em>kind</em> (via vs. component hole) for the X2
 * {@code TA.AperFunction} attribute comments. Coordinates are emitted in mm
 * with explicit decimal points, which removes all leading/trailing-zero
 * ambiguity.
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
        return export(features, tools, null);
    }

    /**
     * @param fileFunction the Gerber X2 style drill file function
     *                     ({@code Plated,1,2,PTH} / {@code NonPlated,1,2,NPTH}),
     *                     written as attribute comments; null omits them
     */
    public Result export(Features features, Tools tools, String fileFunction) {
        List<String> warnings = new ArrayList<>();
        double unitToMm = features.isMillimeters() ? 0.001 : 0.0254;

        // diameter key -> operations, ascending diameter
        Map<Long, StringBuilder> opsByDiameter = new TreeMap<>();
        Map<Long, Double> diameterByKey = new TreeMap<>();
        int holes = 0;
        int slots = 0;

        for (Feature feature : features.getFeatures()) {
            int symbolNumber;
            if (feature instanceof Pad pad) {
                symbolNumber = pad.getSymbolNumber();
            } else if (feature instanceof Line line) {
                symbolNumber = line.getSymbolNumber();
            } else if (feature instanceof Arc arc) {
                symbolNumber = arc.getSymbolNumber();
            } else {
                warnings.add("Non-drill feature " + feature.getClass().getSimpleName()
                        + " skipped in Excellon export");
                continue;
            }
            String symbolName = features.getSymbolName(symbolNumber);
            OdbSymbolShape shape = OdbSymbolShape.parse(symbolName, unitToMm);
            if (shape == null) {
                warnings.add("Unsupported drill symbol '" + symbolName
                        + "'; defaulting to 0.1 mm");
            }
            double diameter = shape == null ? 0.1 : shape.strokeDiameter();
            long key = diameterKey(diameter);
            diameterByKey.putIfAbsent(key, diameter);
            StringBuilder ops = opsByDiameter.computeIfAbsent(key, k -> new StringBuilder());

            if (feature instanceof Pad pad) {
                if (shape != null && isElongated(shape)) {
                    double[] ends = slotEnds(pad, shape);
                    appendSlot(ops, ends[0], ends[1], ends[2], ends[3]);
                    slots++;
                    if (shape.kind != OdbSymbolShape.Kind.OVAL) {
                        warnings.add("Non-round drill symbol '" + symbolName
                                + "' exported as a slot of width " + fmt(diameter) + " mm");
                    }
                } else {
                    if (shape != null && shape.kind != OdbSymbolShape.Kind.ROUND) {
                        warnings.add("Non-round drill symbol '" + symbolName
                                + "' exported as a " + fmt(diameter) + " mm hole");
                    }
                    ops.append('X').append(fmt(pad.getX())).append('Y').append(fmt(pad.getY())).append('\n');
                    holes++;
                }
            } else if (feature instanceof Line line) {
                appendSlot(ops, line.getXs(), line.getYs(), line.getXe(), line.getYe());
                slots++;
            } else if (feature instanceof Arc arc) {
                slots += appendArcSlots(ops, arc);
            }
        }

        boolean plated = fileFunction == null || !fileFunction.startsWith("NonPlated");

        StringBuilder out = new StringBuilder();
        out.append("M48\n");
        out.append("; #@! TF.GenerationSoftware,DeltaProto,odbpp-lib,1.0\n");
        if (fileFunction != null) {
            out.append("; #@! TF.FileFunction,").append(fileFunction).append('\n');
        }
        out.append("FMAT,2\n");
        out.append("METRIC\n");
        int toolNumber = 0;
        for (Map.Entry<Long, Double> e : diameterByKey.entrySet()) {
            toolNumber++;
            if (fileFunction != null) {
                out.append("; #@! TA.AperFunction,")
                        .append(plated ? "Plated,PTH," : "NonPlated,NPTH,")
                        .append(isVia(tools, e.getValue()) ? "ViaDrill" : "ComponentDrill")
                        .append('\n');
            }
            out.append('T').append(toolNumber).append('C')
                    .append(String.format(Locale.ROOT, "%.3f", e.getValue())).append('\n');
        }
        out.append("%\n");
        out.append("G90\n");
        out.append("G05\n");
        toolNumber = 0;
        for (Map.Entry<Long, StringBuilder> e : opsByDiameter.entrySet()) {
            toolNumber++;
            out.append('T').append(toolNumber).append('\n');
            out.append(e.getValue());
        }
        out.append("M30\n");

        return new Result(out.toString(), warnings, holes, slots);
    }

    private static boolean isElongated(OdbSymbolShape shape) {
        return (shape.kind == OdbSymbolShape.Kind.OVAL || shape.kind == OdbSymbolShape.Kind.RECT
                || shape.kind == OdbSymbolShape.Kind.ROUNDED_RECT
                || shape.kind == OdbSymbolShape.Kind.CHAMFERED_RECT)
                && Math.abs(shape.width - shape.height) > TOOL_MATCH_TOLERANCE_MM;
    }

    /** Slot end points {x1, y1, x2, y2} of an elongated pad, in its rotated frame. */
    private static double[] slotEnds(Pad pad, OdbSymbolShape shape) {
        double half = Math.abs(shape.width - shape.height) / 2;
        double lx = shape.width > shape.height ? half : 0;
        double ly = shape.width > shape.height ? 0 : half;
        double rotCw = padRotationCw(pad);
        double rad = Math.toRadians(-rotCw);
        double dx = lx * Math.cos(rad) - ly * Math.sin(rad);
        double dy = lx * Math.sin(rad) + ly * Math.cos(rad);
        return new double[] {pad.getX() - dx, pad.getY() - dy, pad.getX() + dx, pad.getY() + dy};
    }

    /** orientationType 0-7 legacy (90° steps + mirror), 8/9 free rotation. */
    private static double padRotationCw(Pad pad) {
        int type = pad.getOrientationType();
        if (type >= 8) {
            return pad.getCustomRotation() != null ? pad.getCustomRotation() : 0;
        }
        return (type % 4) * 90.0;
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

    /** Whether the tools file declares any tool of this diameter as a via. */
    private static boolean isVia(Tools tools, double diameter) {
        if (tools == null || tools.getTools() == null) {
            return false;
        }
        for (Tool tool : tools.getTools()) {
            if (tool.getType() == Tool.ToolType.VIA && tool.getFinishSize() > 0
                    && Math.abs(tool.getFinishSize() - diameter) <= TOOL_MATCH_TOLERANCE_MM) {
                return true;
            }
        }
        return false;
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

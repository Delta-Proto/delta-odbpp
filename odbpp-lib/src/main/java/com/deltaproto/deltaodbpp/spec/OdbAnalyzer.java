package com.deltaproto.deltaodbpp.spec;

import com.deltaproto.deltaodbpp.model.Arc;
import com.deltaproto.deltaodbpp.model.Component;
import com.deltaproto.deltaodbpp.model.ContourPolygon;
import com.deltaproto.deltaodbpp.model.Feature;
import com.deltaproto.deltaodbpp.model.Features;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Layer;
import com.deltaproto.deltaodbpp.model.Line;
import com.deltaproto.deltaodbpp.model.MatrixLayer;
import com.deltaproto.deltaodbpp.model.Pad;
import com.deltaproto.deltaodbpp.model.Step;
import com.deltaproto.deltaodbpp.model.Surface;
import com.deltaproto.deltaodbpp.model.Tool;
import com.deltaproto.deltaodbpp.model.impedance.Descriptor;
import com.deltaproto.deltaodbpp.model.impedance.ImpedanceFile;
import com.deltaproto.deltaodbpp.spec.dfm.DrillHole;
import com.deltaproto.deltaodbpp.spec.dfm.PastePad;
import com.deltaproto.deltaodbpp.spec.dfm.ViaInPadDetector;
import com.deltaproto.deltaodbpp.spec.dfm.ViaInPadResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Derives a {@link BoardSpecification} from a parsed ODB++ {@link Job} — the ODB++ counterpart of
 * delta-gerber's {@code PcbAnalyzer}.
 *
 * <p>ODB++ hands the analyzer far more than a folder of Gerbers does: the matrix already names every
 * layer and its type, the step carries an explicit board profile, drill layers carry typed tools
 * files, and the archive may carry stackup, impedance, component and BOM data. So classification is
 * a lookup, not a guess, and the analyzer's job is to measure and reduce.
 *
 * <pre>{@code
 * BoardSpecification spec = new OdbAnalyzer().analyze(job);
 * spec.getSizeXMm();            // 100.0
 * spec.getCopperLayerCount();   // 1
 * spec.getMinTrackWidthUm();    // 1000.0
 * }</pre>
 */
public class OdbAnalyzer {

    /**
     * Copper layers routinely carry a thin trace along the board edge that is not a track. Shrink
     * the profile by this much (mm) so that trace — whose endpoints sit on the profile — falls
     * outside the board and stops dragging the minimum down. Mirrors delta-gerber.
     */
    private static final double PROFILE_SHRINK_MM = 0.01;

    /**
     * The profile filter is only trusted when at least this fraction of a copper layer's segments
     * land inside it; below that the profile evidently does not belong (a panelised frame, a
     * mirrored export) and a minimum drawn from a sliver would be worse than no filter at all.
     */
    private static final double MIN_INSIDE_FRACTION = 0.25;

    /** Analyze the job's main board step (see {@link #chooseStep(Job)}). */
    public BoardSpecification analyze(Job job) {
        return analyze(job, chooseStep(job));
    }

    /** Analyze a named step. Returns an empty specification when the step cannot be found. */
    public BoardSpecification analyze(Job job, String stepName) {
        Step step = findStep(job, stepName);
        return analyze(job, step);
    }

    private BoardSpecification analyze(Job job, Step step) {
        if (job == null || step == null) {
            return new BoardSpecification(step == null ? null : step.getName(), null, null, null,
                    null, null, null, null, null, BoardSide.NONE, BoardSide.NONE, BoardSide.NONE,
                    false, false, false, ViaInPadResult.empty(), false, null, null, null, null,
                    null, List.of());
        }

        LayerModel model = new LayerModel(job);
        double unitToMm = unitToMm(job, step);

        // --- Board profile bounds -------------------------------------------------------------
        Bounds profile = profileBounds(step);
        boolean hasProfile = profile != null && profile.isValid();

        // --- Per-layer analysis in matrix row order -------------------------------------------
        List<AnalyzedLayer> layers = new ArrayList<>();
        Double minTrackUm = null;
        Double minPlated = null;
        Double minNonPlated = null;
        boolean hasCopper = false;
        boolean hasDrill = false;
        boolean solderMaskTop = false, solderMaskBottom = false;
        boolean silkTop = false, silkBottom = false;
        boolean pasteTop = false, pasteBottom = false;

        List<PastePad> pastePads = new ArrayList<>();
        List<DrillHole> drillHoles = new ArrayList<>();
        boolean hasPasteData = false;

        for (MatrixLayer ml : model.boardOrdered) {
            Layer layer = model.layer(step, ml.getName());
            Features features = layer == null ? null : layer.getFeatures();
            boolean geometry = features != null && !features.getFeatures().isEmpty();
            String type = upper(ml.getType());
            LayerSide side = model.sideOf(ml);

            AnalyzedLayer.Builder b = AnalyzedLayer.builder(ml.getName())
                    .matrixRow(ml.getRow())
                    .type(ml.getType())
                    .context(ml.getContext())
                    .side(side)
                    .hasGeometry(geometry)
                    .bounds(geometry ? featureBounds(features) : null);

            boolean board = isBoardContext(ml);

            if (isCopperType(type) && board) {
                hasCopper = true;
                Double t = geometry ? minTrackWidthUm(features, profile, unitToMm) : null;
                b.minTrackWidthUm(t);
                minTrackUm = min(minTrackUm, t);
            } else if ("DRILL".equals(type) || "ROUT".equals(type)) {
                hasDrill = true;
                DrillMinima dm = drillMinima(layer, features, unitToMm);
                b.minDrillDiameterMm(dm.overall());
                minPlated = min(minPlated, dm.plated());
                minNonPlated = min(minNonPlated, dm.nonPlated());
                if (features != null) {
                    collectDrillHoles(layer, features, drillHoles, unitToMm);
                }
            } else if ("SOLDER_MASK".equals(type) && board) {
                if (geometry) {
                    if (side == LayerSide.TOP) solderMaskTop = true;
                    else if (side == LayerSide.BOTTOM) solderMaskBottom = true;
                }
            } else if ("SILK_SCREEN".equals(type) && board) {
                if (geometry) {
                    if (side == LayerSide.TOP) silkTop = true;
                    else if (side == LayerSide.BOTTOM) silkBottom = true;
                }
            } else if ("SOLDER_PASTE".equals(type) && board) {
                hasPasteData = true;
                if (geometry) {
                    // Stencil is only needed where paste actually carries features.
                    if (side == LayerSide.TOP) pasteTop = true;
                    else if (side == LayerSide.BOTTOM) pasteBottom = true;
                    collectPastePads(features, side, pastePads, unitToMm);
                }
            }

            layers.add(b.build());
        }

        // --- Via in pad -----------------------------------------------------------------------
        boolean viaDetermined = hasPasteData && !pastePads.isEmpty() && !drillHoles.isEmpty();
        ViaInPadResult via = viaDetermined
                ? ViaInPadDetector.detect(pastePads, drillHoles)
                : ViaInPadResult.empty();

        // --- Reductions -----------------------------------------------------------------------
        Double minDrill = min(minPlated, minNonPlated);
        Integer copperCount = model.copperCount > 0 ? model.copperCount : null;

        int[] compCounts = componentCounts(step, model);
        Integer compTop = compCounts == null ? null : compCounts[0];
        Integer compBottom = compCounts == null ? null : compCounts[1];

        Integer bomLines = bomLineCount(step);
        Double thickness = totalThicknessMm(job, step);
        Boolean impedance = impedanceControl(step);

        return new BoardSpecification(
                step.getName(),
                hasProfile ? profile.getWidth() : null,
                hasProfile ? profile.getHeight() : null,
                hasProfile ? profile : null,
                copperCount,
                minTrackUm,
                minDrill,
                minPlated,
                minNonPlated,
                BoardSide.of(solderMaskTop, solderMaskBottom),
                BoardSide.of(silkTop, silkBottom),
                BoardSide.of(pasteTop, pasteBottom),
                hasProfile,
                hasCopper,
                hasDrill,
                via,
                viaDetermined,
                thickness,
                impedance,
                compTop,
                compBottom,
                bomLines,
                layers);
    }

    // ------------------------------------------------------------------------
    // Step selection
    // ------------------------------------------------------------------------

    /**
     * The step to analyze by default. ODB++ jobs may carry a panel step (with sub-steps) alongside
     * the board step; the analyzer wants the board. It prefers, in order: the step whose profile
     * carries geometry (a real board outline), then a step named like a board ({@code pcb},
     * {@code board}, or containing those), then the first step in the map. Returns null when the
     * job has no steps.
     */
    public String chooseStep(Job job) {
        if (job == null || job.getSteps() == null || job.getSteps().isEmpty()) {
            return null;
        }
        String firstWithProfile = null;
        String named = null;
        String first = null;
        for (Map.Entry<String, Step> e : job.getSteps().entrySet()) {
            String name = e.getKey();
            Step step = e.getValue();
            if (first == null) {
                first = name;
            }
            if (firstWithProfile == null && step != null
                    && step.getProfile() != null && !step.getProfile().getFeatures().isEmpty()) {
                firstWithProfile = name;
            }
            if (named == null) {
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.equals("pcb") || lower.equals("board") || lower.contains("pcb")
                        || lower.contains("board")) {
                    named = name;
                }
            }
        }
        if (firstWithProfile != null) {
            return firstWithProfile;
        }
        return named != null ? named : first;
    }

    private static Step findStep(Job job, String stepName) {
        if (job == null || job.getSteps() == null || stepName == null) {
            return null;
        }
        for (Map.Entry<String, Step> e : job.getSteps().entrySet()) {
            if (e.getKey().equalsIgnoreCase(stepName)) {
                return e.getValue();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------
    // Profile & feature bounds
    // ------------------------------------------------------------------------

    /** Bounding box of the step profile's surfaces/contours, in mm, or null when there is none. */
    private static Bounds profileBounds(Step step) {
        Features profile = step.getProfile();
        if (profile == null || profile.getFeatures().isEmpty()) {
            return null;
        }
        Bounds b = new Bounds();
        for (Feature f : profile.getFeatures()) {
            includeFeature(f, b);
        }
        return b.isValid() ? b : null;
    }

    /** Bounding box of a layer's features, in mm. */
    private static Bounds featureBounds(Features features) {
        Bounds b = new Bounds();
        for (Feature f : features.getFeatures()) {
            includeFeature(f, b);
        }
        return b;
    }

    private static void includeFeature(Feature f, Bounds b) {
        if (f instanceof Pad pad) {
            b.include(pad.getX(), pad.getY());
        } else if (f instanceof Line line) {
            b.include(line.getXs(), line.getYs());
            b.include(line.getXe(), line.getYe());
        } else if (f instanceof Arc arc) {
            b.include(arc.getXs(), arc.getYs());
            b.include(arc.getXe(), arc.getYe());
        } else if (f instanceof Surface surface) {
            for (ContourPolygon poly : surface.getPolygons()) {
                b.include(poly.getXStart(), poly.getYStart());
                for (ContourPolygon.PolygonPart part : poly.getPolygonParts()) {
                    b.include(part.getEndX(), part.getEndY());
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // Min track width
    // ------------------------------------------------------------------------

    /**
     * Narrowest round-symbol stroke width among Line/Arc features on a copper layer, in µm.
     *
     * <p>Only stroked lines and arcs count — a pad is a land and a surface is a pour, neither is a
     * track. Only round symbols count as a true stroke width (mirroring delta-gerber, which only
     * measures circular apertures). When a profile is given, a segment counts only when both
     * endpoints sit strictly inside the profile shrunk by {@link #PROFILE_SHRINK_MM}; if that filter
     * rejects too much (&lt;25% inside) it degrades to the unfiltered minimum.
     */
    static Double minTrackWidthUm(Features features, Bounds profile, double unitToMm) {
        double overallMin = Double.POSITIVE_INFINITY;
        double insideMin = Double.POSITIVE_INFINITY;
        int total = 0;
        int inside = 0;

        for (Feature f : features.getFeatures()) {
            double sx, sy, ex, ey;
            int symbolNumber;
            if (f instanceof Line line) {
                sx = line.getXs(); sy = line.getYs(); ex = line.getXe(); ey = line.getYe();
                symbolNumber = line.getSymbolNumber();
            } else if (f instanceof Arc arc) {
                sx = arc.getXs(); sy = arc.getYs(); ex = arc.getXe(); ey = arc.getYe();
                symbolNumber = arc.getSymbolNumber();
            } else {
                continue;
            }
            String symbolName = features.getSymbolName(symbolNumber);
            SymbolShape shape = SymbolShape.parse(symbolName, unitToMm);
            if (shape == null || !shape.isRound()) {
                continue;   // only round strokes are a true track width
            }
            double width = shape.strokeWidthMm();
            total++;
            overallMin = Math.min(overallMin, width);
            if (profile != null && isInside(sx, sy, profile) && isInside(ex, ey, profile)) {
                inside++;
                insideMin = Math.min(insideMin, width);
            }
        }

        if (total == 0) {
            return null;
        }
        if (profile != null && inside > 0 && inside >= total * MIN_INSIDE_FRACTION) {
            return insideMin * 1000.0;
        }
        return overallMin * 1000.0;
    }

    private static boolean isInside(double x, double y, Bounds p) {
        return x > p.getMinX() + PROFILE_SHRINK_MM
                && x < p.getMaxX() - PROFILE_SHRINK_MM
                && y > p.getMinY() + PROFILE_SHRINK_MM
                && y < p.getMaxY() - PROFILE_SHRINK_MM;
    }

    // ------------------------------------------------------------------------
    // Min drill
    // ------------------------------------------------------------------------

    private record DrillMinima(Double plated, Double nonPlated) {
        Double overall() {
            return min(plated, nonPlated);
        }
    }

    /**
     * Smallest drill on a drill layer, split by plating. Tools files give the exact, typed answer
     * (VIA and PLATED tools are plated; NON_PLATED are not); a drill layer with no tools file falls
     * back to the smallest round pad symbol on that layer, attributed to plated by default (ODB++
     * gives no plating signal without tools). Tool sizes are already mm (ToolsParser normalises).
     */
    private static DrillMinima drillMinima(Layer layer, Features features, double unitToMm) {
        Double plated = null;
        Double nonPlated = null;

        List<Tool> tools = layer == null || layer.getTools() == null
                ? List.of() : layer.getTools().getTools();
        boolean anyTool = false;
        for (Tool t : tools) {
            double size = toolSizeMm(t);
            if (size <= 0) {
                continue;
            }
            anyTool = true;
            if (t.getType() == Tool.ToolType.NON_PLATED) {
                nonPlated = min(nonPlated, size);
            } else {
                plated = min(plated, size);   // PLATED and VIA are plated
            }
        }

        if (!anyTool && features != null) {
            // No tools file: fall back to the smallest round pad symbol on the layer.
            Double pad = smallestRoundPad(features, unitToMm);
            plated = min(plated, pad);
        }
        return new DrillMinima(plated, nonPlated);
    }

    /** A tool's real hole size in mm: the finished size when set, else the drilled size. */
    private static double toolSizeMm(Tool t) {
        if (t.getFinishSize() > 0) {
            return t.getFinishSize();
        }
        return t.getDrillSize();
    }

    private static Double smallestRoundPad(Features features, double unitToMm) {
        Double min = null;
        for (Feature f : features.getFeatures()) {
            if (!(f instanceof Pad pad)) {
                continue;
            }
            SymbolShape shape = SymbolShape.parse(features.getSymbolName(pad.getSymbolNumber()), unitToMm);
            if (shape != null && shape.kind == SymbolShape.Kind.ROUND) {
                double d = shape.width;
                if (pad.getResizeFactor() != null && pad.getResizeFactor() > 0) {
                    d *= pad.getResizeFactor();
                }
                min = min(min, d);
            }
        }
        return min;
    }

    // ------------------------------------------------------------------------
    // Via-in-pad collection
    // ------------------------------------------------------------------------

    private static void collectPastePads(Features features, LayerSide side, List<PastePad> out,
                                         double unitToMm) {
        boolean top = side == LayerSide.TOP;
        boolean bottom = side == LayerSide.BOTTOM;
        if (!top && !bottom) {
            top = true; // a sideless paste layer is assumed top; its pads still count either way
        }
        for (Feature f : features.getFeatures()) {
            if (!(f instanceof Pad pad)) {
                continue;   // paste openings are flashed pads (or surfaces, handled by bbox below)
            }
            SymbolShape shape = SymbolShape.parse(features.getSymbolName(pad.getSymbolNumber()), unitToMm);
            double rot = pad.getCustomRotation() != null ? pad.getCustomRotation()
                    : legacyRotation(pad.getOrientationType());
            double resize = pad.getResizeFactor() != null && pad.getResizeFactor() > 0
                    ? pad.getResizeFactor() : 1.0;
            PastePad.Shape padShape;
            double w, h;
            if (shape == null) {
                // Unknown footprint: a small box around the flash centre (over-counts, never misses).
                padShape = PastePad.Shape.BOX;
                w = h = 0.5;
            } else {
                padShape = switch (shape.kind) {
                    case ROUND, DONUT -> PastePad.Shape.ROUND;
                    case OVAL -> PastePad.Shape.OVAL;
                    default -> PastePad.Shape.RECT;
                };
                w = (shape.width > 0 ? shape.width : shape.height) * resize;
                h = (shape.height > 0 ? shape.height : shape.width) * resize;
            }
            out.add(new PastePad(padShape, pad.getX(), pad.getY(), w, h, rot, top, bottom));
        }
    }

    private static void collectDrillHoles(Layer layer, Features features, List<DrillHole> out,
                                          double unitToMm) {
        // Map dcode/symbol to a diameter from tools where possible; else use the pad symbol size.
        for (Feature f : features.getFeatures()) {
            if (!(f instanceof Pad pad)) {
                continue;   // drilled holes are flashed pads on the drill layer
            }
            double diameter = 0;
            SymbolShape shape = SymbolShape.parse(features.getSymbolName(pad.getSymbolNumber()), unitToMm);
            if (shape != null) {
                diameter = shape.width;
                if (pad.getResizeFactor() != null && pad.getResizeFactor() > 0) {
                    diameter *= pad.getResizeFactor();
                }
            }
            out.add(new DrillHole(pad.getX(), pad.getY(), diameter));
        }
    }

    /** Legacy orient_def 0-3 → 0/90/180/270°; 4-7 add a mirror we ignore for rotation. */
    private static double legacyRotation(int orientationType) {
        return switch (orientationType & 3) {
            case 1 -> 90;
            case 2 -> 180;
            case 3 -> 270;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------------
    // Extras: components, BOM, thickness, impedance
    // ------------------------------------------------------------------------

    private static int[] componentCounts(Step step, LayerModel model) {
        if (step.getLayersByName() == null) {
            return null;
        }
        boolean any = false;
        int top = 0;
        int bottom = 0;
        for (Map.Entry<String, Layer> e : step.getLayersByName().entrySet()) {
            Layer layer = e.getValue();
            if (layer.getComponents() == null || layer.getComponents().getComponents() == null
                    || layer.getComponents().getComponents().isEmpty()) {
                continue;
            }
            any = true;
            LayerSide side = model.sideOfName(e.getKey());
            int n = layer.getComponents().getComponents().size();
            if (side == LayerSide.BOTTOM) {
                bottom += n;
            } else {
                top += n;   // component layer with no clear side counts as top
            }
        }
        return any ? new int[]{top, bottom} : null;
    }

    private static Integer bomLineCount(Step step) {
        if (step.getBom() == null || step.getBom().getItems() == null) {
            return null;
        }
        return step.getBom().getItems().size();
    }

    /**
     * Total finished board thickness in mm. Preferred source is a drill/rout tools file's THICKNESS
     * (already mm); a stackup file, when present, confirms the design is stack-defined but the
     * parsed stackup model carries no per-layer thickness, so it is not summed here. Null when no
     * thickness is available.
     */
    private static Double totalThicknessMm(Job job, Step step) {
        if (step.getLayersByName() != null) {
            for (Layer layer : step.getLayersByName().values()) {
                if (layer.getTools() != null && layer.getTools().getThickness() > 0) {
                    return layer.getTools().getThickness();
                }
            }
        }
        return null;
    }

    private static Boolean impedanceControl(Step step) {
        ImpedanceFile imp = step.getImpedance();
        if (imp == null) {
            return null;
        }
        if (imp.getDescriptor() == null || imp.getDescriptor().isEmpty()) {
            return false;
        }
        for (Descriptor d : imp.getDescriptor()) {
            if (d.getRequiredImpedance() != null && d.getRequiredImpedance().getValOhms() > 0) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------
    // Layer classification model
    // ------------------------------------------------------------------------

    /**
     * Board-context matrix layers in row order, plus side inference — a self-contained copy of the
     * side logic delta-gerber and the export {@code LayerSideClassifier} use, kept here so the spec
     * package does not depend on {@code export/}. The first copper layer is TOP, the last BOTTOM,
     * the rest INNER; non-copper board layers take their side from name, then from row position.
     */
    private static final class LayerModel {
        final List<MatrixLayer> boardOrdered = new ArrayList<>();
        final Map<String, LayerSide> sideByName = new LinkedHashMap<>();
        int copperCount = 0;
        private int firstCopperRow = Integer.MAX_VALUE;
        private int lastCopperRow = Integer.MIN_VALUE;

        LayerModel(Job job) {
            List<MatrixLayer> all = job.getMatrix() != null && job.getMatrix().getLayers() != null
                    ? job.getMatrix().getLayers() : List.of();
            List<MatrixLayer> sorted = new ArrayList<>(all);
            sorted.sort(Comparator.comparingInt(MatrixLayer::getRow));

            for (MatrixLayer ml : sorted) {
                if (isCopperType(upper(ml.getType())) && isBoardContext(ml)) {
                    copperCount++;
                    firstCopperRow = Math.min(firstCopperRow, ml.getRow());
                    lastCopperRow = Math.max(lastCopperRow, ml.getRow());
                }
            }
            for (MatrixLayer ml : sorted) {
                boardOrdered.add(ml);
                if (ml.getName() != null) {
                    sideByName.put(ml.getName().toLowerCase(Locale.ROOT), classify(ml));
                }
            }
        }

        LayerSide sideOf(MatrixLayer ml) {
            return ml.getName() == null ? LayerSide.NA
                    : sideByName.getOrDefault(ml.getName().toLowerCase(Locale.ROOT), LayerSide.NA);
        }

        LayerSide sideOfName(String name) {
            return name == null ? LayerSide.NA
                    : sideByName.getOrDefault(name.toLowerCase(Locale.ROOT), LayerSide.NA);
        }

        Layer layer(Step step, String name) {
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

        private LayerSide classify(MatrixLayer ml) {
            String type = upper(ml.getType());
            if ("DIELECTRIC".equals(type) || "DOCUMENT".equals(type)) {
                return LayerSide.NA;
            }
            if (isCopperType(type)) {
                if (ml.getRow() == firstCopperRow) return LayerSide.TOP;
                if (ml.getRow() == lastCopperRow) return LayerSide.BOTTOM;
                return LayerSide.INNER;
            }
            LayerSide byName = sideFromName(ml.getName() == null ? "" : ml.getName().toLowerCase(Locale.ROOT));
            if (byName != LayerSide.NA) {
                return byName;
            }
            if (firstCopperRow != Integer.MAX_VALUE) {
                if (ml.getRow() < firstCopperRow) return LayerSide.TOP;
                if (ml.getRow() > lastCopperRow) return LayerSide.BOTTOM;
            }
            return LayerSide.NA;
        }

        private static LayerSide sideFromName(String lower) {
            if (lower.startsWith("top_")) return LayerSide.TOP;
            if (lower.startsWith("bot_") || lower.startsWith("bottom_")) return LayerSide.BOTTOM;
            if (lower.endsWith("_top") || lower.endsWith(".top")) return LayerSide.TOP;
            if (lower.endsWith("_bot") || lower.endsWith(".bot")
                    || lower.endsWith("_bottom") || lower.endsWith(".bottom")) return LayerSide.BOTTOM;
            if (lower.contains("+_top")) return LayerSide.TOP;
            if (lower.contains("+_bot")) return LayerSide.BOTTOM;
            if (lower.startsWith("f.") || lower.startsWith("f_")) return LayerSide.TOP;
            if (lower.startsWith("b.") || lower.startsWith("b_")) return LayerSide.BOTTOM;
            // Common short names smt/smb, sst/ssb, spt/spb (mask/silk/paste top/bottom).
            if (lower.endsWith("t") && (lower.startsWith("sm") || lower.startsWith("ss")
                    || lower.startsWith("sp"))) return LayerSide.TOP;
            if (lower.endsWith("b") && (lower.startsWith("sm") || lower.startsWith("ss")
                    || lower.startsWith("sp"))) return LayerSide.BOTTOM;
            return LayerSide.NA;
        }
    }

    // ------------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------------

    /**
     * The factor that turns a symbol-name dimension into millimetres for this step: 0.001 for an
     * MM-native archive (symbol dims in microns) or 0.0254 for an INCH-native archive (mils).
     *
     * <p>ODB++ symbol dimensions follow the archive's native unit, which the parser reads from the
     * step header, then {@code misc/info}, and — per the spec — defaults to <strong>imperial</strong>
     * when neither declares it (many KiCad/Altium exports carry no UNITS in the features files, so
     * {@code Features.units} defaults misleadingly to MM and must not be trusted on its own).
     */
    private static double unitToMm(Job job, Step step) {
        if (step != null && step.getStepHdr() != null
                && "MM".equalsIgnoreCase(step.getStepHdr().getUnits())) {
            return 0.001;
        }
        if (step != null && step.getStepHdr() != null
                && "INCH".equalsIgnoreCase(step.getStepHdr().getUnits())) {
            return 0.0254;
        }
        if (job != null && job.getMiscInfo() != null
                && "MM".equalsIgnoreCase(job.getMiscInfo().getUnits())) {
            return 0.001;
        }
        return 0.0254; // spec default: imperial
    }

    private static boolean isCopperType(String type) {
        return "SIGNAL".equals(type) || "POWER_GROUND".equals(type) || "MIXED".equals(type);
    }

    private static boolean isBoardContext(MatrixLayer ml) {
        // ODB++ almost always sets CONTEXT=BOARD for physical layers; treat a missing context as
        // board too, and exclude only the explicit misc/document contexts.
        String ctx = upper(ml.getContext());
        return ctx.isEmpty() || "BOARD".equals(ctx);
    }

    private static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    private static Double min(Double a, Double b) {
        if (a == null) return b;
        if (b == null) return a;
        return a <= b ? a : b;
    }
}

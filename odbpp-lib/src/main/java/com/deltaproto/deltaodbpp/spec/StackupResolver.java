package com.deltaproto.deltaodbpp.spec;

import com.deltaproto.deltaodbpp.model.AttrList;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Matrix;
import com.deltaproto.deltaodbpp.model.MatrixLayer;
import com.deltaproto.deltaodbpp.model.Step;
import com.deltaproto.deltaodbpp.model.stackup.Conductor;
import com.deltaproto.deltaodbpp.model.stackup.Dielectric;
import com.deltaproto.deltaodbpp.model.stackup.Group;
import com.deltaproto.deltaodbpp.model.stackup.Layer;
import com.deltaproto.deltaodbpp.model.stackup.Material;
import com.deltaproto.deltaodbpp.model.stackup.MaterialRef;
import com.deltaproto.deltaodbpp.model.stackup.Properties;
import com.deltaproto.deltaodbpp.model.stackup.Property;
import com.deltaproto.deltaodbpp.model.stackup.Spec;
import com.deltaproto.deltaodbpp.model.stackup.SpecRef;
import com.deltaproto.deltaodbpp.model.stackup.Stackup;
import com.deltaproto.deltaodbpp.model.stackup.StackupFile;
import com.deltaproto.deltaodbpp.model.stackup.StackupUnits;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the board's physical stack — the ordered list of {@link StackupLayer}s — from the matrix
 * and, when the archive ships one, {@code matrix/stackup.xml}.
 *
 * <h2>Where the order comes from</h2>
 * The matrix, always: the spec is explicit that the matrix stays the primary source of layer order
 * and that the stackup file loses any disagreement (spec pg 52). A stackup file that lists physical
 * layers the matrix does not — most often the dielectrics, which some matrices omit — has those
 * spliced in at the position its own top-to-bottom order implies.
 *
 * <h2>Where the values come from</h2>
 * Two sources, richest first.
 *
 * <p><b>{@code matrix/stackup.xml}</b>, when present. A stackup {@code Layer} there carries no
 * thickness of its own: it names a {@code SpecRef}, which names a {@code Spec} and a
 * {@code Material} inside it, and the {@code Material} holds the thickness, the copper weight and —
 * through its frequency-dependent {@code Property} entries — the Dk and Df. This class follows that
 * chain. {@code EdaData} is preferred over {@code SupplierData}: it is the design source's own
 * statement, which the spec forbids suppliers to alter.
 *
 * <p><b>Per-layer {@code attrlist} files</b>, which is what real archives actually ship. The stackup
 * file is optional and, in practice, close to nonexistent — Altium, which writes the overwhelming
 * majority of ODB++ in the wild, does not emit it. What Altium does emit is
 * {@code steps/<step>/layers/<layer>/attrlist} carrying {@code .layer_dielectric} (thickness of
 * material — also the mask thickness on a solder mask and the stencil thickness on a paste layer),
 * {@code .dielectric_constant}, {@code .loss_tangent} and {@code .comment} (the laminate name), plus
 * {@code .board_thickness} on the product model. That is a real per-layer stackup, and
 * {@link #resolve(Job, Step)} reads it.
 *
 * <p>Anything neither source answers falls back to an industry typical, and every value carries a
 * flag saying which it was — see {@link StackupLayer} on measured versus estimated. Archives with
 * neither source therefore still produce a complete, drawable stack, entirely flagged as estimated.
 */
public final class StackupResolver {

    // --- Industry typicals, used only where the archive is silent -----------------------------
    private static final long OUTER_COPPER_PM = 35_000_000L;     // 0.035 mm, 1 oz
    private static final long INNER_COPPER_PM = 18_000_000L;     // 0.018 mm, 0.5 oz
    private static final double OUTER_COPPER_OZ = 1.0;
    private static final double INNER_COPPER_OZ = 0.5;
    private static final long DIELECTRIC_PM = 200_000_000L;      // 0.2 mm
    private static final double LAMINATE_DK = 4.3;               // FR-4
    private static final double LAMINATE_DF = 0.02;
    private static final long SOLDER_MASK_PM = 15_000_000L;      // 0.015 mm
    private static final double SOLDER_MASK_DK = 3.5;
    private static final double SOLDER_MASK_DF = 0.025;
    private static final long THIN_COATING_PM = 5_000_000L;      // 0.005 mm

    private StackupResolver() {}

    /**
     * The physical stack of a parsed job, top of board → bottom, reading per-layer attributes from
     * the job's main board step. Empty when the job has no matrix.
     */
    public static List<StackupLayer> resolve(Job job) {
        if (job == null) {
            return List.of();
        }
        return resolve(job, stepOf(job, new OdbAnalyzer().chooseStep(job)));
    }

    /**
     * The physical stack of a parsed job, reading per-layer attributes from the given step.
     *
     * @param job the parsed job; the stack is empty without it or its matrix
     * @param step the step whose layer {@code attrlist} files carry the per-layer thicknesses, or
     *     null to resolve from the matrix and stackup file alone
     */
    public static List<StackupLayer> resolve(Job job, Step step) {
        if (job == null) {
            return List.of();
        }
        return resolve(job.getMatrix(), job.getStackup(), AttrSource.of(job, step));
    }

    /**
     * The physical stack from a matrix and an optional stackup file, with no access to the per-layer
     * {@code attrlist} files. Prefer {@link #resolve(Job, Step)}, which reads them — without them
     * almost every real archive yields a fully estimated stack.
     *
     * @param matrix the layer matrix; the stack is empty without it
     * @param stackup {@code matrix/stackup.xml}, or null when the archive ships none
     */
    public static List<StackupLayer> resolve(Matrix matrix, StackupFile stackup) {
        return resolve(matrix, stackup, AttrSource.EMPTY);
    }

    private static List<StackupLayer> resolve(Matrix matrix, StackupFile stackup, AttrSource attrs) {
        if (matrix == null || matrix.getLayers() == null) {
            return List.of();
        }

        OdbAnalyzer.LayerModel sides = new OdbAnalyzer.LayerModel(matrix);
        StackupIndex index = StackupIndex.of(stackup);

        List<MatrixLayer> sorted = new ArrayList<>(matrix.getLayers());
        sorted.sort(Comparator.comparingInt(MatrixLayer::getRow));

        int firstConductorRow = Integer.MAX_VALUE;
        int lastConductorRow = Integer.MIN_VALUE;
        for (MatrixLayer ml : sorted) {
            if (isConductor(type(ml))) {
                firstConductorRow = Math.min(firstConductorRow, ml.getRow());
                lastConductorRow = Math.max(lastConductorRow, ml.getRow());
            }
        }

        List<Slot> slots = new ArrayList<>();
        for (MatrixLayer ml : sorted) {
            if (skipInStackup(type(ml))) {
                continue;
            }
            boolean outer = ml.getRow() == firstConductorRow || ml.getRow() == lastConductorRow;
            slots.add(Slot.fromMatrix(ml, sides.sideOf(ml), outer));
        }
        index.spliceMissingLayers(slots);

        List<StackupLayer> result = new ArrayList<>(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            result.add(slots.get(i).toLayer(i, index, attrs));
        }
        return List.copyOf(result);
    }

    /**
     * Total board thickness in mm as the stackup file states it, or null when it states none.
     *
     * <p>This is the file's own target thickness ({@code Stackup/@StackupThickness}), not a sum — the
     * number the design asked the fabricator to build to.
     */
    public static Double statedThicknessMm(StackupFile stackup) {
        Stackup s = preferredStackup(stackup);
        return s == null ? null : s.stackupThicknessMm(defaultUnits(stackup));
    }

    /**
     * The sum of the stack's thicknesses in mm, or null unless <em>every</em> entry's thickness came
     * from the archive.
     *
     * <p>Summing a mix of stated and invented thicknesses would produce a number that looks measured
     * and is not, so a single estimated entry disqualifies the whole sum. The sum is taken in
     * picometres and is therefore exact.
     */
    public static Double summedThicknessMm(List<StackupLayer> stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        long total = 0;
        for (StackupLayer l : stack) {
            if (!l.isThicknessMeasured()) {
                return null;
            }
            total += l.getThicknessPm();
        }
        return total / 1_000_000_000.0;
    }

    // ------------------------------------------------------------------------
    // Per-layer attrlist attributes — what real archives actually carry
    // ------------------------------------------------------------------------

    /**
     * Board thickness in mm from {@code .board_thickness}, or null when the archive states none.
     *
     * <p>The attribute lives on the product model ({@code misc/attrlist}); the spec also describes it
     * as a step attribute, so {@code steps/<step>/attrlist} is checked as a fallback. The obsolete
     * drill-tools {@code THICKNESS} field is, per the spec, derived from this — so this is the
     * original and that is its echo.
     */
    public static Double boardThicknessMm(Job job, Step step) {
        if (job == null) {
            return null;
        }
        boolean jobMm = isMm(job.getMiscInfo() == null ? null : job.getMiscInfo().getUnits(), false);
        Double fromProduct = attrLengthMm(job.getProductModelAttributes(), ".board_thickness", jobMm);
        if (fromProduct != null) {
            return fromProduct;
        }
        return step == null ? null : attrLengthMm(step.getAttrList(), ".board_thickness", jobMm);
    }

    /**
     * The largest a {@code .board_thickness} or {@code .layer_dielectric} can plausibly be, in mm.
     * The spec gives {@code .board_thickness} the range 0.0–10.0, which is the tell that these values
     * are written in the file's base unit — see {@link #attrLengthMm(double, boolean)}.
     */
    private static final double MAX_PLAUSIBLE_MM = 10.0;

    /**
     * Read a length attribute in millimetres, resolving the unit the way archives actually write it.
     *
     * <p>The spec tags {@code .board_thickness} and {@code .layer_dielectric} {@code UNITS=MIL_MICRON},
     * meaning mils under an imperial file and microns under a metric one. Real archives do not do
     * that: they write the file's <em>base</em> unit — millimetres under {@code UNITS=MM}, inches
     * otherwise. A 1.57 mm board is written {@code 1.57000448}, not {@code 1570}; the same board in
     * an archive with no {@code UNITS} line is written {@code 0.0492126}, which is inches, not mils.
     * The spec's own 0.0–10.0 range for the attribute agrees with the base-unit reading and rules out
     * the mil/micron one, a board being neither 10 mils nor 10 microns thick.
     *
     * <p>So the base unit is tried first, and the mil/micron reading is used only when the base-unit
     * one lands outside anything a board or a layer could be — which is how a spec-conforming writer,
     * should one turn up, still reads correctly. When neither reading is plausible the value is
     * refused rather than guessed: null means "not determined", and a wrong thickness is worse than
     * none.
     */
    private static Double attrLengthMm(double raw, boolean mmBase) {
        if (raw <= 0) {
            return null;
        }
        double base = mmBase ? raw : raw * 25.4;
        if (base <= MAX_PLAUSIBLE_MM) {
            return base;
        }
        double milMicron = mmBase ? raw / 1000.0 : raw * 0.0254;
        return milMicron > 0 && milMicron <= MAX_PLAUSIBLE_MM ? milMicron : null;
    }

    private static Double attrLengthMm(AttrList attrs, String key, boolean jobMm) {
        Double raw = attrDouble(attrs, key);
        return raw == null ? null : attrLengthMm(raw, isMm(attrs.getUnits(), jobMm));
    }

    private static Double attrDouble(AttrList attrs, String key) {
        if (attrs == null || attrs.getAttributes() == null) {
            return null;
        }
        String raw = attrs.getAttributes().get(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String attrText(AttrList attrs, String key) {
        if (attrs == null || attrs.getAttributes() == null) {
            return null;
        }
        String raw = attrs.getAttributes().get(key);
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    /** An archive is metric only when it says so; the ODB++ default is imperial (spec pg 23). */
    private static boolean isMm(String units, boolean fallbackMm) {
        if (units == null || units.isBlank()) {
            return fallbackMm;
        }
        return "MM".equalsIgnoreCase(units.trim());
    }

    private static Step stepOf(Job job, String stepName) {
        if (job == null || job.getSteps() == null || stepName == null) {
            return null;
        }
        return job.getSteps().get(stepName);
    }

    /**
     * The per-layer {@code attrlist} files of one step, read on demand once the layer's function is
     * known — the same attribute means different things, or nothing at all, depending on it.
     */
    private static final class AttrSource {
        static final AttrSource EMPTY = new AttrSource(Map.of(), false);

        private final Map<String, AttrList> byLayerName;
        private final boolean jobMm;

        private AttrSource(Map<String, AttrList> byLayerName, boolean jobMm) {
            this.byLayerName = byLayerName;
            this.jobMm = jobMm;
        }

        static AttrSource of(Job job, Step step) {
            if (job == null || step == null || step.getLayersByName() == null) {
                return EMPTY;
            }
            boolean jobMm = isMm(job.getMiscInfo() == null ? null : job.getMiscInfo().getUnits(), false);
            Map<String, AttrList> out = new LinkedHashMap<>();
            for (Map.Entry<String, com.deltaproto.deltaodbpp.model.Layer> e
                    : step.getLayersByName().entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue().getAttrList() == null) {
                    continue;
                }
                out.put(key(e.getKey()), e.getValue().getAttrList());
            }
            return out.isEmpty() ? EMPTY : new AttrSource(out, jobMm);
        }

        ResolvedMaterial valuesFor(String layerName, String function) {
            AttrList attrs = layerName == null ? null : byLayerName.get(key(layerName));
            if (attrs == null) {
                return null;
            }
            ResolvedMaterial m = new ResolvedMaterial();

            if (carriesMaterialThickness(function)) {
                Double mm = attrLengthMm(attrs, ".layer_dielectric", jobMm);
                if (mm != null && plausibleThicknessMm(function, mm)) {
                    m.thicknessPm = Math.round(mm * 1_000_000_000.0);
                }
            }
            if (isDielectricish(function)) {
                // Dk of 1 or less is not a material, it is an unset field: vacuum is exactly 1.
                Double dk = attrDouble(attrs, ".dielectric_constant");
                if (dk != null && dk > 1.0) {
                    m.dielectricConstant = dk;
                }
                // Likewise Df: a lossless dielectric does not exist, so a written 0 means "not set".
                Double df = attrDouble(attrs, ".loss_tangent");
                if (df != null && df > 0) {
                    m.lossTangent = df;
                }
                // Writers often park the laminate designation in the layer's free-text comment.
                // It is genuinely free text, though — it can hold a description rather than a
                // material — so the matrix's purpose-built DIELECTRIC_NAME outranks it, and this
                // only fills in where the matrix names nothing.
                m.materialName = attrText(attrs, ".comment");
            }
            if (isConductor(function)) {
                m.copperWeightOz = copperWeightOz(attrs);
            }
            return m;
        }

        /**
         * Copper weight in oz/ft² from {@code .copper_weight}, or null when it is absent or absurd.
         *
         * <p>The attribute's unit is genuinely ambiguous. The spec calls it "the weight of copper
         * according to its Units of Measurement" — a length convention applied to a weight — and
         * 8.1 renamed its display name from Copper Weight to Copper Thickness, which points the other
         * way. Two things settle it for reading purposes: the spec's own default is {@code 1.0}, and
         * observed archives write exactly {@code 1} on inner layers, which is the standard 1 oz
         * inner foil and would be an odd 1 mil (0.73 oz) under the thickness reading.
         *
         * <p>So it is read as ounces. Deliberately, no copper <em>thickness</em> is derived from it:
         * that would turn a still-uncertain unit into a number that feeds board-thickness sums, and
         * copper thickness stays an industry typical, flagged estimated, as it was before.
         */
        private static Double copperWeightOz(AttrList attrs) {
            Double oz = attrDouble(attrs, ".copper_weight");
            return oz != null && oz > 0.05 && oz <= 20.0 ? oz : null;
        }

        /**
         * {@code .layer_dielectric} is "thickness of material", but only on the layer types the spec
         * gives it a meaning for (pg 207): DIELECTRIC and SOLDER_MASK, plus SOLDER_PASTE where it is
         * the stencil thickness. On a copper layer Altium writes the <em>adjacent</em> dielectric's
         * thickness into it, which is emphatically not the copper's, so copper is excluded.
         */
        private static boolean carriesMaterialThickness(String function) {
            return "DIELECTRIC".equals(function) || "SOLDER_MASK".equals(function)
                    || "SOLDER_PASTE".equals(function);
        }

        /** Layers that are made of a dielectric and so can state a Dk, a Df and a material name. */
        private static boolean isDielectricish(String function) {
            return "DIELECTRIC".equals(function) || "SOLDER_MASK".equals(function);
        }

        /**
         * Whether a stated thickness is physically possible for this kind of layer.
         *
         * <p>Writers park a sentinel in {@code .layer_dielectric} on layers they have no real value
         * for — the same {@code 0.0001} turns up on silkscreen and paste alike, two and a half
         * microns, which is no stencil anyone ever cut. Taking that at face value would report a
         * placeholder as a measured thickness, so a value outside what the layer could physically be
         * is refused and the typical is used instead, flagged estimated.
         */
        private static boolean plausibleThicknessMm(String function, double mm) {
            switch (function) {
                case "SOLDER_PASTE":    return mm >= 0.02 && mm <= 1.0;
                case "SOLDER_MASK":     return mm >= 0.004 && mm <= 0.2;
                default:                return mm >= 0.005 && mm <= 5.0;
            }
        }
    }

    /** Overlay {@code secondary} under {@code primary}: the richer source wins field by field. */
    private static ResolvedMaterial merge(ResolvedMaterial primary, ResolvedMaterial secondary) {
        ResolvedMaterial out = new ResolvedMaterial();
        for (ResolvedMaterial src : new ResolvedMaterial[]{primary, secondary}) {
            if (src == null) {
                continue;
            }
            if (out.thicknessPm == null) out.thicknessPm = src.thicknessPm;
            if (out.materialName == null) out.materialName = src.materialName;
            if (out.dielectricConstant == null) out.dielectricConstant = src.dielectricConstant;
            if (out.lossTangent == null) out.lossTangent = src.lossTangent;
            if (out.copperWeightOz == null) out.copperWeightOz = src.copperWeightOz;
        }
        return out;
    }

    // ------------------------------------------------------------------------
    // One row of the stack, before its values are resolved
    // ------------------------------------------------------------------------

    private static final class Slot {
        String name;
        String function;
        LayerSide side;
        Integer matrixRow;
        String dielectricName;      // matrix DIELECTRIC_NAME, when it has one
        boolean outerConductor;

        static Slot fromMatrix(MatrixLayer ml, LayerSide side, boolean outerConductor) {
            Slot s = new Slot();
            s.name = ml.getName();
            s.function = type(ml);
            s.side = side;
            s.matrixRow = ml.getRow();
            s.dielectricName = ml.getDielectricName();
            s.outerConductor = isConductor(s.function) && outerConductor;
            return s;
        }

        static Slot fromStackup(Layer layer) {
            Slot s = new Slot();
            s.name = layer.getLayerName();
            s.function = upper(layer.getLayerType());
            s.side = sideOf(layer.getSide());
            return s;
        }

        private static LayerSide sideOf(String side) {
            String s = upper(side);
            switch (s) {
                case "TOP": return LayerSide.TOP;
                case "BOTTOM": return LayerSide.BOTTOM;
                case "INNER": return LayerSide.INNER;
                default: return LayerSide.NA;
            }
        }

        StackupLayer toLayer(int ordinal, StackupIndex index, AttrSource attrs) {
            StackupLayer.Builder b = StackupLayer.builder(ordinal, name)
                    .function(function)
                    .side(side)
                    .matrixRow(matrixRow)
                    .conductor(isConductor(function))
                    .dielectric("DIELECTRIC".equals(function));

            // stackup.xml is the explicit statement and wins; the layer's own attrlist fills the rest.
            ResolvedMaterial fromAttrs = attrs.valuesFor(name, function);
            ResolvedMaterial real = merge(index.materialFor(name), fromAttrs);
            // For the material name the matrix's DIELECTRIC_NAME comes before the attrlist's free
            // text: it is the field meant to carry the material, so it is the more trustworthy of
            // the two when they disagree.
            if (dielectricName != null && !dielectricName.isEmpty()
                    && (real.materialName == null
                        || (fromAttrs != null && real.materialName.equals(fromAttrs.materialName)))) {
                real.materialName = dielectricName;
            }
            applyReal(b, real);
            applyTypicals(b, real);
            return b.build();
        }

        private void applyReal(StackupLayer.Builder b, ResolvedMaterial real) {
            if (real.thicknessPm != null) {
                b.thicknessPm(real.thicknessPm, false);
            }
            if (real.materialName != null && !real.materialName.isBlank()) {
                b.material(real.materialName, false);
            }
            if (real.dielectricConstant != null) {
                b.dielectricConstant(real.dielectricConstant, false);
            }
            if (real.lossTangent != null) {
                b.lossTangent(real.lossTangent, false);
            }
            if (real.copperWeightOz != null) {
                b.copperWeightOz(real.copperWeightOz, false);
            }
        }

        /** Fill whatever the archive left unanswered with an industry typical, flagged as estimated. */
        private void applyTypicals(StackupLayer.Builder b, ResolvedMaterial real) {
            boolean haveThickness = real.thicknessPm != null;
            boolean haveMaterial = real.materialName != null && !real.materialName.isBlank();
            boolean haveDk = real.dielectricConstant != null;
            boolean haveDf = real.lossTangent != null;
            boolean haveOz = real.copperWeightOz != null;

            switch (function) {
                case "SIGNAL":
                case "POWER_GROUND":
                case "MIXED":
                    if (!haveOz) b.copperWeightOz(outerConductor ? OUTER_COPPER_OZ : INNER_COPPER_OZ, true);
                    if (!haveThickness) b.thicknessPm(outerConductor ? OUTER_COPPER_PM : INNER_COPPER_PM, true);
                    if (!haveMaterial) b.material("Copper", true);
                    break;
                case "DIELECTRIC": {
                    if (!haveMaterial) b.material("Dielectric", true);
                    if (!haveThickness) b.thicknessPm(DIELECTRIC_PM, true);
                    if (!haveDk) b.dielectricConstant(LAMINATE_DK, true);
                    if (!haveDf) b.lossTangent(LAMINATE_DF, true);
                    break;
                }
                case "SOLDER_MASK":
                    if (!haveThickness) b.thicknessPm(SOLDER_MASK_PM, true);
                    if (!haveMaterial) b.material("Solder Resist", true);
                    if (!haveDk) b.dielectricConstant(SOLDER_MASK_DK, true);
                    if (!haveDf) b.lossTangent(SOLDER_MASK_DF, true);
                    break;
                case "SILK_SCREEN":
                    if (!haveThickness) b.thicknessPm(THIN_COATING_PM, true);
                    if (!haveMaterial) b.material("Epoxy Ink", true);
                    break;
                case "SOLDER_PASTE":
                    if (!haveThickness) b.thicknessPm(THIN_COATING_PM, true);
                    if (!haveMaterial) b.material("SnPb/SAC", true);
                    break;
                case "CONDUCTIVE_PASTE":
                    if (!haveThickness) b.thicknessPm(THIN_COATING_PM, true);
                    if (!haveMaterial) b.material("Conductive Paste", true);
                    break;
                default:
                    // An unrecognised layer type gets no invented thickness: null is the honest answer.
                    break;
            }
        }
    }

    // ------------------------------------------------------------------------
    // The stackup file, indexed by layer name
    // ------------------------------------------------------------------------

    /** The physical values a stackup file states for one layer; every field nullable. */
    private static final class ResolvedMaterial {
        Long thicknessPm;
        String materialName;
        Double dielectricConstant;
        Double lossTangent;
        Double copperWeightOz;
    }

    /**
     * A {@code stackup.xml} flattened to "layer name → what the file says about it", plus the file's
     * own layer order so layers the matrix omits can be put back in the right place.
     */
    private static final class StackupIndex {
        private static final StackupIndex EMPTY = new StackupIndex();

        private final Map<String, ResolvedMaterial> byLayerName = new LinkedHashMap<>();
        private final List<Layer> orderedLayers = new ArrayList<>();

        static StackupIndex of(StackupFile file) {
            Stackup stackup = preferredStackup(file);
            if (stackup == null || stackup.getGroup() == null) {
                return EMPTY;
            }
            StackupUnits units = defaultUnits(file);
            StackupIndex index = new StackupIndex();
            for (Group group : stackup.getGroup()) {
                if (group == null || group.getLayer() == null) {
                    continue;
                }
                for (Layer layer : group.getLayer()) {
                    if (layer == null || layer.getLayerName() == null) {
                        continue;
                    }
                    index.orderedLayers.add(layer);
                    ResolvedMaterial m = resolveMaterial(file, layer, units);
                    if (m != null) {
                        index.byLayerName.put(key(layer.getLayerName()), m);
                    }
                }
            }
            return index;
        }

        ResolvedMaterial materialFor(String layerName) {
            return layerName == null ? null : byLayerName.get(key(layerName));
        }

        /**
         * Insert the physical layers the stackup file lists but the matrix does not, each just after
         * the stackup neighbour that precedes it. Does nothing unless the file's layer names overlap
         * the matrix's — non-overlapping names mean the file describes some other design.
         */
        void spliceMissingLayers(List<Slot> slots) {
            if (orderedLayers.isEmpty() || slots.isEmpty()) {
                return;
            }
            Map<String, Integer> present = new LinkedHashMap<>();
            for (int i = 0; i < slots.size(); i++) {
                present.put(key(slots.get(i).name), i);
            }
            boolean overlaps = false;
            for (Layer l : orderedLayers) {
                if (present.containsKey(key(l.getLayerName()))) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                return;
            }

            int cursor = -1;
            for (Layer layer : orderedLayers) {
                String k = key(layer.getLayerName());
                int at = indexOf(slots, k);
                if (at >= 0) {
                    cursor = at;
                    continue;
                }
                if (skipInStackup(upper(layer.getLayerType()))) {
                    continue;
                }
                slots.add(cursor + 1, Slot.fromStackup(layer));
                cursor++;
            }
        }

        private static int indexOf(List<Slot> slots, String key) {
            for (int i = 0; i < slots.size(); i++) {
                if (key.equals(key(slots.get(i).name))) {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * Follow one stackup layer's {@code SpecRef} chain to the {@link Material} that holds its
     * physical values. Returns null when the layer references nothing resolvable.
     */
    private static ResolvedMaterial resolveMaterial(
            StackupFile file,
            Layer layer,
            StackupUnits units) {

        if (layer.getSpecRef() == null || layer.getSpecRef().isEmpty()) {
            return null;
        }
        ResolvedMaterial out = new ResolvedMaterial();
        boolean any = false;

        for (SpecRef ref : layer.getSpecRef()) {
            if (ref == null || ref.getMaterial() == null) {
                continue;
            }
            MaterialRef want = ref.getMaterial();
            if (want.getMaterialName() != null && out.materialName == null) {
                out.materialName = want.getMaterialName();
                any = true;
            }
            Material material = findMaterial(file, ref.getMaterialSpecName(), want.getMaterialName());
            if (material == null) {
                continue;
            }
            any = true;

            Property property = pickProperty(material, want);
            if (property != null) {
                if (out.thicknessPm == null) {
                    out.thicknessPm = property.thicknessPm(units);
                }
                if (out.dielectricConstant == null) {
                    out.dielectricConstant = property.getDielectricConstant_Dk();
                }
                if (out.lossTangent == null) {
                    out.lossTangent = property.getLossTangent_Df();
                }
            }
            if (out.thicknessPm == null && material.getDefault_Thickness() != null) {
                out.thicknessPm = material.getDefault_Thickness().thicknessPm(units);
            }
            Conductor conductor = material.getConductor();
            if (conductor != null && out.copperWeightOz == null) {
                out.copperWeightOz = conductor.copperWeightOz();
            }
        }
        return any ? out : null;
    }

    /**
     * The {@code Property} that applies to a layer: the one whose frequency the reference asks for,
     * else the sole property of the sole property set, which the spec makes the default.
     */
    private static Property pickProperty(Material material, MaterialRef want) {
        List<Properties> sets = material.getDielectric() != null
                ? material.getDielectric().getProperties()
                : (material.getConductor() != null ? material.getConductor().getProperties() : null);
        if (sets == null || sets.isEmpty()) {
            return null;
        }
        Properties chosen = null;
        if (want.getPropertyName() != null) {
            for (Properties set : sets) {
                if (set != null && want.getPropertyName().equalsIgnoreCase(set.getPropertyName())) {
                    chosen = set;
                    break;
                }
            }
        }
        if (chosen == null) {
            chosen = sets.get(0);
        }
        if (chosen == null || chosen.getProperty() == null || chosen.getProperty().isEmpty()) {
            return null;
        }
        if (want.getFrequencyVal() != null) {
            for (Property p : chosen.getProperty()) {
                if (p != null && want.getFrequencyVal().equals(p.getFrequencyVal())) {
                    return p;
                }
            }
        }
        return chosen.getProperty().get(0);
    }

    /** The named material, looked in the named spec first and then in every other spec of the file. */
    private static Material findMaterial(StackupFile file, String specName, String materialName) {
        if (materialName == null) {
            return null;
        }
        Material fallback = null;
        for (Spec spec : allSpecs(file)) {
            if (spec.getMaterial() == null) {
                continue;
            }
            for (Material m : spec.getMaterial()) {
                if (m == null || !materialName.equalsIgnoreCase(m.getMaterialName())) {
                    continue;
                }
                if (specName == null || specName.equalsIgnoreCase(spec.getSpecName())) {
                    return m;
                }
                if (fallback == null) {
                    fallback = m;
                }
            }
        }
        return fallback;
    }

    private static List<Spec> allSpecs(StackupFile file) {
        List<Spec> specs = new ArrayList<>();
        if (file == null) {
            return specs;
        }
        if (file.getEdaData() != null && file.getEdaData().getSpecs() != null
                && file.getEdaData().getSpecs().getSpec() != null) {
            specs.addAll(file.getEdaData().getSpecs().getSpec());
        }
        if (file.getSupplierData() != null && file.getSupplierData().getSpecs() != null
                && file.getSupplierData().getSpecs().getSpec() != null) {
            specs.addAll(file.getSupplierData().getSpecs().getSpec());
        }
        specs.removeIf(s -> s == null);
        return specs;
    }

    /**
     * The stackup section to read. {@code EdaData} wins: it is the design source's own statement and
     * the spec forbids a supplier to alter it. A file carrying only supplier sections falls back to
     * the first of those.
     */
    private static Stackup preferredStackup(StackupFile file) {
        if (file == null) {
            return null;
        }
        if (file.getEdaData() != null && file.getEdaData().getStackup() != null) {
            return file.getEdaData().getStackup();
        }
        if (file.getSupplierData() != null) {
            return file.getSupplierData().getStackup();
        }
        return null;
    }

    private static StackupUnits defaultUnits(StackupFile file) {
        return file == null ? StackupUnits.DEFAULT : file.defaultUnits();
    }

    // ------------------------------------------------------------------------
    // Layer-type predicates — the same set StackupView has always used
    // ------------------------------------------------------------------------

    /** Layers that do not build the board and so never appear in the stack. */
    private static boolean skipInStackup(String type) {
        return "DRILL".equals(type) || "ROUT".equals(type)
                || "DOCUMENT".equals(type) || "COMPONENT".equals(type);
    }

    private static boolean isConductor(String type) {
        return "SIGNAL".equals(type) || "POWER_GROUND".equals(type) || "MIXED".equals(type);
    }

    private static String type(MatrixLayer ml) {
        return upper(ml.getType());
    }

    private static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}

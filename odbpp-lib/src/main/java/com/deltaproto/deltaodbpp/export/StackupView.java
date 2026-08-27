package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Matrix;
import com.deltaproto.deltaodbpp.spec.StackupLayer;
import com.deltaproto.deltaodbpp.spec.StackupResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a simplified vertical cross-section view of a board's layer stack,
 * suitable for rendering as a JSON payload to the viewer UI.
 *
 * <p>{@link StackupResolver} supplies the numbers, from a stackup.xml when the
 * archive has one and from the per-layer attrlist files when it does not — which
 * is the usual case, most writers shipping no stackup.xml at all. Where neither
 * answers, an industry typical stands in, so the UI can always draw a meaningful
 * cross-section.
 *
 * <p>The resulting list is ordered by matrix row (top → bottom of the board).
 * Layers that don't contribute to the physical stack (DRILL, ROUT, DOCUMENT,
 * COMPONENT) are skipped.
 *
 * <p>This is the JSON projection of {@link StackupLayer}; a caller that needs to
 * know which values are the archive's own and which are typicals should use
 * {@link com.deltaproto.deltaodbpp.spec.BoardSpecification#getStackup()}, whose
 * entries flag that per value rather than only for thickness.
 */
public final class StackupView {

    private StackupView() {}

    /** A single layer in the simplified stackup. Serialises cleanly to JSON via Jackson. */
    public static final class Entry {
        public String name;
        public String type;                     // SIGNAL / DIELECTRIC / SOLDER_MASK / …
        public String side;                     // TOP / BOTTOM / INNER / NEITHER
        public double thicknessMm;
        /**
         * Thickness in picometres, or null when it could not be determined. 1 mil is exactly
         * 25 400 000 pm and 1 µin exactly 25 400 pm, so both metric and imperial nominal
         * thicknesses land on whole picometres and sums of them stay exact — which
         * {@link #thicknessMm} cannot promise.
         */
        public Long thicknessPm;
        public String material;                 // e.g. "Copper", "PP-006", "Solder Resist"
        public Double dielectricConstant;       // null if not applicable
        public Double lossTangent;              // null if not applicable
        public Double copperWeightOz;           // null if not a conductor
        public boolean conductor;
        public boolean dielectric;
        /** True when {@link #thicknessMm} is not a value read from the archive. */
        public boolean estimated;
    }

    /**
     * @return a list of stack entries in physical order (top of board → bottom),
     *     or an empty list when the job has no matrix.
     */
    public static List<Entry> build(Job job) {
        if (job == null || job.getMatrix() == null) return List.of();
        return toEntries(StackupResolver.resolve(job));
    }

    /**
     * Build from a matrix alone. Without a job there is no {@code stackup.xml} to read, so every
     * physical value is an industry typical; prefer {@link #build(Job)} when a job is at hand.
     */
    public static List<Entry> build(Matrix matrix) {
        if (matrix == null || matrix.getLayers() == null) return List.of();
        return toEntries(StackupResolver.resolve(matrix, null));
    }

    /** Sum of thicknesses (convenience for UI footer / test assertions). */
    public static double totalThicknessMm(List<Entry> entries) {
        double sum = 0;
        for (Entry e : entries) sum += e.thicknessMm;
        return sum;
    }

    // ---- internals ----

    private static List<Entry> toEntries(List<StackupLayer> stack) {
        List<Entry> result = new ArrayList<>(stack.size());
        for (StackupLayer l : stack) {
            Entry e = new Entry();
            e.name = l.getName();
            e.type = l.getFunction();
            e.side = side(l);
            e.thicknessMm = l.getThicknessMm() == null ? 0.0 : l.getThicknessMm();
            e.thicknessPm = l.getThicknessPm();
            e.material = l.getMaterial() == null ? "" : l.getMaterial();
            e.dielectricConstant = l.getDielectricConstant();
            e.lossTangent = l.getLossTangent();
            e.copperWeightOz = l.getCopperWeightOz();
            e.conductor = l.isConductor();
            e.dielectric = l.isDielectric();
            e.estimated = !l.isThicknessMeasured();
            result.add(e);
        }
        return result;
    }

    /**
     * The view's own side vocabulary. The analyzer calls "not applicable" {@code NA}; this view has
     * always called it {@code NEITHER}, and the UI reads that name.
     */
    private static String side(StackupLayer layer) {
        switch (layer.getSide()) {
            case TOP: return LayerSide.TOP.name();
            case BOTTOM: return LayerSide.BOTTOM.name();
            case INNER: return LayerSide.INNER.name();
            default: return LayerSide.NEITHER.name();
        }
    }
}

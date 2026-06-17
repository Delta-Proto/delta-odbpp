package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Matrix;
import com.deltaproto.deltaodbpp.model.MatrixLayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Builds a simplified vertical cross-section view of a board's layer stack,
 * suitable for rendering as a JSON payload to the viewer UI.
 *
 * <p>Real stackup.xml files carry thicknesses, dielectric constants and loss
 * tangents. Many archives (some Altium exports, for instance) omit that
 * file entirely — the matrix still lists the layers and their
 * dielectric material name, but not their physical properties. This view fills
 * in industry-typical defaults when the real values aren't present, so the UI
 * can always draw a meaningful cross-section.
 *
 * <p>The resulting list is ordered by matrix row (top → bottom of the board).
 * Layers that don't contribute to the physical stack (DRILL, ROUT, DOCUMENT,
 * COMPONENT) are skipped.
 */
public final class StackupView {

    private StackupView() {}

    /** A single layer in the simplified stackup. Serialises cleanly to JSON via Jackson. */
    public static final class Entry {
        public String name;
        public String type;                     // SIGNAL / DIELECTRIC / SOLDER_MASK / …
        public String side;                     // TOP / BOTTOM / INNER / NEITHER
        public double thicknessMm;
        public String material;                 // e.g. "Copper", "PP-006", "Solder Resist"
        public Double dielectricConstant;       // null if not applicable
        public Double lossTangent;              // null if not applicable
        public Double copperWeightOz;           // null if not a conductor
        public boolean conductor;
        public boolean dielectric;
        public boolean estimated;               // true when thickness came from defaults
    }

    /**
     * @return a list of stack entries in physical order (top of board → bottom),
     *     or an empty list when the job has no matrix.
     */
    public static List<Entry> build(Job job) {
        if (job == null || job.getMatrix() == null) return List.of();
        return build(job.getMatrix());
    }

    public static List<Entry> build(Matrix matrix) {
        if (matrix == null || matrix.getLayers() == null) return List.of();

        LayerSideClassifier classifier = new LayerSideClassifier(matrix);

        List<MatrixLayer> sorted = new ArrayList<>(matrix.getLayers());
        sorted.sort(Comparator.comparingInt(MatrixLayer::getRow));

        // Count outer vs inner conductors so we can assign typical copper weights.
        int firstConductorRow = Integer.MAX_VALUE;
        int lastConductorRow = Integer.MIN_VALUE;
        for (MatrixLayer ml : sorted) {
            if (isConductor(ml)) {
                firstConductorRow = Math.min(firstConductorRow, ml.getRow());
                lastConductorRow = Math.max(lastConductorRow, ml.getRow());
            }
        }

        List<Entry> result = new ArrayList<>();
        for (MatrixLayer ml : sorted) {
            if (skipInStackup(ml)) continue;
            Entry e = new Entry();
            e.name = ml.getName();
            e.type = upper(ml.getType());
            e.side = classifier.sideOf(ml.getName()).name();
            e.conductor = isConductor(ml);
            e.dielectric = "DIELECTRIC".equals(e.type);
            populateDefaults(e, ml, firstConductorRow, lastConductorRow);
            result.add(e);
        }
        return result;
    }

    /** Sum of thicknesses (convenience for UI footer / test assertions). */
    public static double totalThicknessMm(List<Entry> entries) {
        double sum = 0;
        for (Entry e : entries) sum += e.thicknessMm;
        return sum;
    }

    // ---- internals ----

    private static boolean skipInStackup(MatrixLayer ml) {
        String t = upper(ml.getType());
        return "DRILL".equals(t) || "ROUT".equals(t)
                || "DOCUMENT".equals(t) || "COMPONENT".equals(t);
    }

    private static boolean isConductor(MatrixLayer ml) {
        String t = upper(ml.getType());
        return "SIGNAL".equals(t) || "POWER_GROUND".equals(t) || "MIXED".equals(t);
    }

    private static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    /** Fill in thickness/material/dk/df with either parsed values or industry typicals. */
    private static void populateDefaults(Entry e, MatrixLayer ml, int firstConductorRow, int lastConductorRow) {
        e.estimated = true; // flip to false if we ever add real data sources
        switch (e.type) {
            case "SIGNAL":
            case "POWER_GROUND":
            case "MIXED": {
                boolean outer = ml.getRow() == firstConductorRow || ml.getRow() == lastConductorRow;
                // Typical: 1 oz outers, 0.5 oz inners.
                e.copperWeightOz = outer ? 1.0 : 0.5;
                e.thicknessMm = outer ? 0.035 : 0.018;
                e.material = "Copper";
                break;
            }
            case "DIELECTRIC": {
                String dtype = upper(ml.getDielectricType());
                String dname = ml.getDielectricName();
                e.material = (dname != null && !dname.isEmpty()) ? dname : "Prepreg";
                // Core is typically thicker than a single prepreg ply.
                e.thicknessMm = "CORE".equals(dtype) ? 0.2 : 0.1;
                // FR-4 typicals; a real stackup.xml would override these.
                e.dielectricConstant = 4.3;
                e.lossTangent = 0.02;
                break;
            }
            case "SOLDER_MASK":
                e.thicknessMm = 0.015;
                e.material = "Solder Resist";
                e.dielectricConstant = 3.5;
                e.lossTangent = 0.025;
                break;
            case "SILK_SCREEN":
                e.thicknessMm = 0.005;
                e.material = "Epoxy Ink";
                break;
            case "SOLDER_PASTE":
                e.thicknessMm = 0.005;
                e.material = "SnPb/SAC";
                break;
            case "CONDUCTIVE_PASTE":
                e.thicknessMm = 0.005;
                e.material = "Conductive Paste";
                break;
            default:
                e.thicknessMm = 0.0;
                e.material = "";
                break;
        }
    }
}

package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Matrix;
import com.deltaproto.deltaodbpp.model.MatrixLayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Layer colours for the multi-layer (CAM) view, after the default theme of KiCad's PCB
 * editor: red top copper, blue bottom copper, a distinct colour for every inner layer,
 * and side-specific colours for soldermask, legend and paste. They are meant for a dark
 * canvas ({@link #CANVAS}).
 *
 * <p>The palette is opt-in. {@link MultiLayerSvgRenderer} keeps its own type-based
 * colours unless one is handed over with
 * {@link MultiLayerSvgRenderer#setLayerColors(Map)}:
 * <pre>{@code
 * Map<String, String> colors = LayerPalette.kicad(job.getMatrix());
 * new MultiLayerSvgRenderer().setLayerColors(colors).renderJob(job, writer);
 * }</pre>
 */
public final class LayerPalette {

    /** Canvas the palette is designed for: KiCad's PCB editor background. */
    public static final String CANVAS = "#001023";
    /** Grid lines on {@link #CANVAS}. */
    public static final String GRID = "#848484";

    public static final String COPPER_TOP = "#c83434";
    public static final String COPPER_BOTTOM = "#4d7fc4";
    public static final String MASK_TOP = "#d864ff";
    public static final String MASK_BOTTOM = "#02ffee";
    public static final String SILK_TOP = "#f2eda1";
    public static final String SILK_BOTTOM = "#e8b2a7";
    public static final String PASTE_TOP = "#b4a09a";
    public static final String PASTE_BOTTOM = "#00c2c2";
    public static final String COMPONENT_TOP = "#ff26e2";
    public static final String COMPONENT_BOTTOM = "#26e9ff";
    /** Board outline (Edge.Cuts). */
    public static final String OUTLINE = "#d0d2cd";
    public static final String DRILL = "#e3b72e";
    public static final String ROUT = "#1ac4d2";
    /** Documentation layers and anything the matrix does not type. */
    public static final String OTHER = "#afafaf";

    private static final String[] COPPER_INNER = {
            "#7fc87f", "#ce7d2c", "#4fcbcb", "#db628b",
            "#a7a5c6", "#28ccd9", "#b4dbd2", "#d8c852"
    };

    private LayerPalette() {
    }

    /**
     * Colour of the n-th inner copper layer, counted from the top starting at 1. The
     * eight inner-layer colours repeat on boards with more inner layers.
     */
    public static String innerCopper(int n) {
        return COPPER_INNER[Math.floorMod(n - 1, COPPER_INNER.length)];
    }

    /**
     * Assigns every layer of the matrix its colour.
     *
     * @return colours as {@code #rrggbb}, keyed by lower-case layer name, in matrix row
     *     order. Dielectric rows carry no artwork and are left out.
     */
    public static Map<String, String> kicad(Matrix matrix) {
        Map<String, String> colors = new LinkedHashMap<>();
        if (matrix == null || matrix.getLayers() == null) return colors;

        LayerSideClassifier sides = new LayerSideClassifier(matrix);
        List<MatrixLayer> rows = new ArrayList<>();
        for (MatrixLayer ml : matrix.getLayers()) {
            if (ml != null && ml.getName() != null) rows.add(ml);
        }
        rows.sort(Comparator.comparingInt(MatrixLayer::getRow));

        int inner = 0;
        for (MatrixLayer ml : rows) {
            String type = ml.getType() == null ? "" : ml.getType().toUpperCase(Locale.ROOT);
            if ("DIELECTRIC".equals(type)) continue;
            LayerSide side = sides.sideOf(ml.getName());
            String color;
            switch (type) {
                case "SIGNAL":
                case "POWER_GROUND":
                case "MIXED":
                    if (side == LayerSide.TOP) color = COPPER_TOP;
                    else if (side == LayerSide.BOTTOM) color = COPPER_BOTTOM;
                    else color = innerCopper(++inner);
                    break;
                case "SOLDER_MASK":
                    color = bySide(side, MASK_TOP, MASK_BOTTOM);
                    break;
                case "SILK_SCREEN":
                    color = bySide(side, SILK_TOP, SILK_BOTTOM);
                    break;
                case "SOLDER_PASTE":
                case "CONDUCTIVE_PASTE":
                    color = bySide(side, PASTE_TOP, PASTE_BOTTOM);
                    break;
                case "COMPONENT":
                    color = bySide(side, COMPONENT_TOP, COMPONENT_BOTTOM);
                    break;
                case "DRILL":
                    color = DRILL;
                    break;
                case "ROUT":
                    color = ROUT;
                    break;
                default:
                    color = OTHER;
            }
            colors.put(ml.getName().toLowerCase(Locale.ROOT), color);
        }
        return colors;
    }

    /** A layer that sits on neither side takes the neutral colour. */
    private static String bySide(LayerSide side, String top, String bottom) {
        if (side == LayerSide.TOP) return top;
        if (side == LayerSide.BOTTOM) return bottom;
        return OTHER;
    }
}

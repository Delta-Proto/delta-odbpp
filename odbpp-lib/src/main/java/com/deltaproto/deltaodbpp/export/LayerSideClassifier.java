package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Matrix;
import com.deltaproto.deltaodbpp.model.MatrixLayer;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Classifies ODB++ matrix layers into physical {@link LayerSide}s for realistic
 * rendering.
 *
 * <p>ODB++ archives almost always set {@code CONTEXT=BOARD} for every physical
 * layer. The side (top/bottom/inner) must therefore be inferred. This
 * classifier uses, in order:
 * <ol>
 *   <li>Conductor layers (SIGNAL/POWER_GROUND/MIXED): the first such layer in
 *       the matrix row order is TOP, the last is BOTTOM, everything between is
 *       INNER.</li>
 *   <li>Non-conductor board layers (SOLDER_MASK, SILK_SCREEN, SOLDER_PASTE,
 *       CONDUCTIVE_PASTE, COMPONENT): name prefix/suffix ({@code TOP_*},
 *       {@code _TOP}, {@code BOTTOM_*}, {@code _BOT}, {@code +_TOP},
 *       {@code +_BOT}) takes precedence; fallback to matrix row position
 *       relative to the first/last conductor row.</li>
 *   <li>Drill layers: visibility per side is derived from {@code START_NAME} /
 *       {@code END_NAME} spans. Through-holes (span from first to last
 *       conductor) are visible from both sides; blind/buried vias are visible
 *       only from the side they pierce.</li>
 * </ol>
 *
 * Dielectrics and document layers always classify as {@link LayerSide#NEITHER}.
 */
public final class LayerSideClassifier {

    private final Map<String, LayerSide> sideByLowerName = new LinkedHashMap<>();
    private final Map<String, EnumSet<LayerSide>> drillVisibilityByLowerName = new HashMap<>();
    private final int firstConductorRow;
    private final int lastConductorRow;
    private final String firstConductorName;
    private final String lastConductorName;

    public LayerSideClassifier(Matrix matrix) {
        List<MatrixLayer> layers = matrix != null && matrix.getLayers() != null
                ? matrix.getLayers()
                : List.of();

        // Pass 1: identify outer conductor rows
        int first = Integer.MAX_VALUE;
        int last = Integer.MIN_VALUE;
        String firstName = null;
        String lastName = null;
        for (MatrixLayer ml : layers) {
            if (isConductor(ml)) {
                if (ml.getRow() < first) {
                    first = ml.getRow();
                    firstName = ml.getName();
                }
                if (ml.getRow() > last) {
                    last = ml.getRow();
                    lastName = ml.getName();
                }
            }
        }
        this.firstConductorRow = first;
        this.lastConductorRow = last;
        this.firstConductorName = firstName;
        this.lastConductorName = lastName;

        // Pass 2: classify every non-drill layer
        for (MatrixLayer ml : layers) {
            if (ml == null || ml.getName() == null) continue;
            if (isDrill(ml)) continue; // drills handled separately
            LayerSide side = classify(ml);
            sideByLowerName.put(ml.getName().toLowerCase(Locale.ROOT), side);
        }

        // Pass 3: drill visibility per side based on span
        for (MatrixLayer ml : layers) {
            if (ml == null || ml.getName() == null) continue;
            if (!isDrill(ml)) continue;
            EnumSet<LayerSide> sides = computeDrillVisibility(ml);
            drillVisibilityByLowerName.put(ml.getName().toLowerCase(Locale.ROOT), sides);
        }
    }

    /**
     * @return the inferred physical side for a non-drill layer, or
     *     {@link LayerSide#NEITHER} if the layer is unknown.
     */
    public LayerSide sideOf(String layerName) {
        if (layerName == null) return LayerSide.NEITHER;
        LayerSide side = sideByLowerName.get(layerName.toLowerCase(Locale.ROOT));
        return side != null ? side : LayerSide.NEITHER;
    }

    /**
     * @return true if this drill layer is visible from the given side (i.e., it
     *     pierces the outer conductor on that side).
     */
    public boolean drillVisibleFrom(String drillLayerName, LayerSide side) {
        if (drillLayerName == null || side == null) return false;
        EnumSet<LayerSide> visible = drillVisibilityByLowerName.get(
                drillLayerName.toLowerCase(Locale.ROOT));
        return visible != null && visible.contains(side);
    }

    private LayerSide classify(MatrixLayer ml) {
        String type = normalize(ml.getType());
        String lowerName = ml.getName().toLowerCase(Locale.ROOT);
        int row = ml.getRow();

        // Document, dielectric, profile: not rendered in realistic view
        if ("DIELECTRIC".equals(type) || "DOCUMENT".equals(type)) {
            return LayerSide.NEITHER;
        }

        // Conductors: first=TOP, last=BOTTOM, middle=INNER
        if (isConductorType(type)) {
            if (row == firstConductorRow) return LayerSide.TOP;
            if (row == lastConductorRow) return LayerSide.BOTTOM;
            return LayerSide.INNER;
        }

        // Non-conductor board layers: try name first
        LayerSide byName = sideFromName(lowerName);
        if (byName != LayerSide.NEITHER) return byName;

        // Fallback: row position relative to outer conductors
        if (firstConductorRow != Integer.MAX_VALUE) {
            if (row < firstConductorRow) return LayerSide.TOP;
            if (row > lastConductorRow) return LayerSide.BOTTOM;
        }
        return LayerSide.NEITHER;
    }

    private static LayerSide sideFromName(String lowerName) {
        // Prefix forms: top_*, bot_*, bottom_*
        if (lowerName.startsWith("top_")) return LayerSide.TOP;
        if (lowerName.startsWith("bot_") || lowerName.startsWith("bottom_")) return LayerSide.BOTTOM;

        // Suffix forms: *_top, *.top, *_bot, *_bottom, *.bot, *.bottom
        if (endsWithToken(lowerName, "_top") || endsWithToken(lowerName, ".top")) return LayerSide.TOP;
        if (endsWithToken(lowerName, "_bot") || endsWithToken(lowerName, ".bot")
                || endsWithToken(lowerName, "_bottom") || endsWithToken(lowerName, ".bottom")) {
            return LayerSide.BOTTOM;
        }

        // ODB++ convention for component layers: comp_+_top / comp_+_bot
        if (lowerName.contains("+_top")) return LayerSide.TOP;
        if (lowerName.contains("+_bot")) return LayerSide.BOTTOM;

        // Short-hand tokens as word boundaries, e.g. "f.cu", "f.paste"
        if (lowerName.startsWith("f.") || lowerName.startsWith("f_")) return LayerSide.TOP;
        if (lowerName.startsWith("b.") || lowerName.startsWith("b_")) return LayerSide.BOTTOM;

        return LayerSide.NEITHER;
    }

    private static boolean endsWithToken(String name, String suffix) {
        return name.endsWith(suffix);
    }

    private EnumSet<LayerSide> computeDrillVisibility(MatrixLayer drill) {
        EnumSet<LayerSide> sides = EnumSet.noneOf(LayerSide.class);
        String start = drill.getStartName();
        String end = drill.getEndName();

        if (isBlank(start) || isBlank(end)) {
            // Through-hole by default
            sides.add(LayerSide.TOP);
            sides.add(LayerSide.BOTTOM);
            return sides;
        }

        if (matchesConductor(start, firstConductorName) || matchesConductor(end, firstConductorName)) {
            sides.add(LayerSide.TOP);
        }
        if (matchesConductor(start, lastConductorName) || matchesConductor(end, lastConductorName)) {
            sides.add(LayerSide.BOTTOM);
        }

        if (sides.isEmpty()) {
            // Buried via that touches neither outer conductor — not visible in realistic view
            return sides;
        }
        return sides;
    }

    private static boolean matchesConductor(String layerName, String conductorName) {
        return layerName != null && conductorName != null
                && layerName.equalsIgnoreCase(conductorName);
    }

    private static boolean isConductor(MatrixLayer ml) {
        return isConductorType(normalize(ml.getType()));
    }

    private static boolean isConductorType(String type) {
        return "SIGNAL".equals(type) || "POWER_GROUND".equals(type) || "MIXED".equals(type);
    }

    private static boolean isDrill(MatrixLayer ml) {
        String t = normalize(ml.getType());
        return "DRILL".equals(t) || "ROUT".equals(t);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }
}

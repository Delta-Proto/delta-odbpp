package com.deltaproto.deltaodbpp.spec.dfm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects <em>vias in pad</em>: drilled holes that fall inside a surface-mount pad.
 *
 * <p>Mirrors {@code com.deltaproto.deltagerber.dfm.ViaInPadDetector}. The pad geometry is read from
 * the <strong>solder-paste</strong> layer, the honest marker of an SMD land — paste is stencilled
 * onto exactly the pads a component reflows onto, and a through-hole pad gets none. So the rule is
 * simply: a hole whose centre lies inside a paste opening is a via in pad. This naturally includes
 * the thermal vias under a QFN/BGA pad — the classic case — and excludes an ordinary plated
 * through-hole, which has no paste around it.
 *
 * <p>The ODB++ model carries paste as {@code Pad}/{@code Surface} features and drills as
 * {@code Pad} features (or tool holes) on drill layers; the analyzer flattens those into
 * {@link PastePad} footprints and {@link DrillHole} centres — already mm-normalised and in the same
 * coordinate frame (both parsers normalise to mm at parse time) — and hands them here.
 *
 * <p>Holes are tested against pads through a uniform spatial grid so the cost stays linear in holes
 * rather than holes × pads on dense boards (a BGA is thousands of each).
 */
public final class ViaInPadDetector {

    private ViaInPadDetector() {
    }

    /**
     * Correlate paste pads with drill holes.
     *
     * @param pads  SMD pad footprints collected from the paste layers, each tagged with its side
     * @param holes drilled-hole centres to test
     * @return the vias in pad found — {@link ViaInPadResult#empty()} when either list is empty
     */
    public static ViaInPadResult detect(List<PastePad> pads, List<DrillHole> holes) {
        if (pads == null || pads.isEmpty() || holes == null || holes.isEmpty()) {
            return ViaInPadResult.empty();
        }

        PadIndex index = new PadIndex(pads);
        List<ViaInPad> hits = new ArrayList<>();
        for (DrillHole hole : holes) {
            if (hole == null) {
                continue;
            }
            boolean top = false;
            boolean bottom = false;
            for (PastePad pad : index.candidates(hole.xMm(), hole.yMm())) {
                if (pad.contains(hole.xMm(), hole.yMm())) {
                    top |= pad.top();
                    bottom |= pad.bottom();
                }
            }
            if (top || bottom) {
                hits.add(new ViaInPad(hole.xMm(), hole.yMm(), hole.diameterMm(), top, bottom));
            }
        }
        return new ViaInPadResult(hits);
    }

    // ------------------------------------------------------------------------
    // Spatial index
    // ------------------------------------------------------------------------

    /**
     * A uniform grid over the pads so each hole is tested only against the pads near it. The cell is
     * clamped to [0.5 mm, 5 mm] as in delta-gerber, so a pad spans only a handful of cells.
     */
    private static final class PadIndex {
        private final double cell;
        private final Map<Long, List<PastePad>> grid = new HashMap<>();

        PadIndex(List<PastePad> pads) {
            this.cell = cellSize(pads);
            for (PastePad pad : pads) {
                int minCx = (int) Math.floor(pad.minX() / cell);
                int maxCx = (int) Math.floor(pad.maxX() / cell);
                int minCy = (int) Math.floor(pad.minY() / cell);
                int maxCy = (int) Math.floor(pad.maxY() / cell);
                for (int cx = minCx; cx <= maxCx; cx++) {
                    for (int cy = minCy; cy <= maxCy; cy++) {
                        grid.computeIfAbsent(key(cx, cy), k -> new ArrayList<>()).add(pad);
                    }
                }
            }
        }

        List<PastePad> candidates(double x, double y) {
            List<PastePad> pads = grid.get(key((int) Math.floor(x / cell), (int) Math.floor(y / cell)));
            return pads == null ? List.of() : pads;
        }

        private static double cellSize(List<PastePad> pads) {
            double sum = 0;
            for (PastePad pad : pads) {
                sum += Math.max(pad.maxX() - pad.minX(), pad.maxY() - pad.minY());
            }
            double avg = pads.isEmpty() ? 1.0 : sum / pads.size();
            if (!Double.isFinite(avg) || avg <= 0) {
                avg = 1.0;
            }
            return Math.min(Math.max(avg, 0.5), 5.0);
        }

        private static long key(int cx, int cy) {
            return ((long) cx << 32) ^ (cy & 0xffffffffL);
        }
    }
}

package com.deltaproto.deltaodbpp.spec;

import com.deltaproto.deltaodbpp.spec.dfm.ViaInPadResult;

import java.util.List;

/**
 * What a parsed ODB++ {@link com.deltaproto.deltaodbpp.model.Job} says the board is: its size, its
 * copper stack, the processes it needs, and the two tolerances that drive fabrication cost —
 * narrowest track and smallest drill — plus ODB++ extras (stackup thickness, impedance control,
 * component and BOM counts) that the design archive makes exact.
 *
 * <p>The ODB++ counterpart of {@code com.deltaproto.deltagerber.spec.BoardSpecification}. Every
 * measurement is what the <em>archive</em> says, never what anyone ordered, and a field is
 * {@code null} (or {@link BoardSide#NONE}) when the archive does not answer that question — so a
 * caller can tell "the design has no solder mask" ({@link BoardSide#NONE}) from "could not be
 * determined" (null).
 *
 * <p>Instances come from {@link OdbAnalyzer}; the constructor is package-private.
 */
public final class BoardSpecification {

    private final String stepName;
    private final Double sizeXMm;
    private final Double sizeYMm;
    private final Bounds bounds;
    private final Integer copperLayerCount;
    private final Double minTrackWidthUm;
    private final Double minDrillDiameterMm;
    private final Double minPlatedDrillMm;
    private final Double minNonPlatedDrillMm;
    private final BoardSide solderMaskSide;
    private final BoardSide silkscreenSide;
    private final BoardSide stencilSide;
    private final boolean hasProfile;
    private final boolean hasCopper;
    private final boolean hasDrill;
    private final ViaInPadResult viaInPad;
    private final boolean viaInPadDetermined;
    private final Double totalThicknessMm;
    private final Boolean impedanceControl;
    private final Integer componentCountTop;
    private final Integer componentCountBottom;
    private final Integer bomLineCount;
    private final List<AnalyzedLayer> layers;

    BoardSpecification(String stepName, Double sizeXMm, Double sizeYMm, Bounds bounds,
                       Integer copperLayerCount, Double minTrackWidthUm, Double minDrillDiameterMm,
                       Double minPlatedDrillMm, Double minNonPlatedDrillMm,
                       BoardSide solderMaskSide, BoardSide silkscreenSide, BoardSide stencilSide,
                       boolean hasProfile, boolean hasCopper, boolean hasDrill,
                       ViaInPadResult viaInPad, boolean viaInPadDetermined,
                       Double totalThicknessMm, Boolean impedanceControl,
                       Integer componentCountTop, Integer componentCountBottom,
                       Integer bomLineCount, List<AnalyzedLayer> layers) {
        this.stepName = stepName;
        this.sizeXMm = sizeXMm;
        this.sizeYMm = sizeYMm;
        this.bounds = bounds;
        this.copperLayerCount = copperLayerCount;
        this.minTrackWidthUm = minTrackWidthUm;
        this.minDrillDiameterMm = minDrillDiameterMm;
        this.minPlatedDrillMm = minPlatedDrillMm;
        this.minNonPlatedDrillMm = minNonPlatedDrillMm;
        this.solderMaskSide = solderMaskSide;
        this.silkscreenSide = silkscreenSide;
        this.stencilSide = stencilSide;
        this.hasProfile = hasProfile;
        this.hasCopper = hasCopper;
        this.hasDrill = hasDrill;
        this.viaInPad = viaInPad;
        this.viaInPadDetermined = viaInPadDetermined;
        this.totalThicknessMm = totalThicknessMm;
        this.impedanceControl = impedanceControl;
        this.componentCountTop = componentCountTop;
        this.componentCountBottom = componentCountBottom;
        this.bomLineCount = bomLineCount;
        this.layers = List.copyOf(layers);
    }

    /** The name of the step that was analyzed. */
    public String getStepName() {
        return stepName;
    }

    /** Board width in mm from the profile bounds, or null when the step has no profile geometry. */
    public Double getSizeXMm() {
        return sizeXMm;
    }

    /** Board height in mm from the profile bounds, or null when the step has no profile geometry. */
    public Double getSizeYMm() {
        return sizeYMm;
    }

    /**
     * The board rectangle in mm, in the archive's own coordinate space (the origin is wherever the
     * CAD tool put it). Null when the step has no profile geometry.
     */
    public Bounds getBounds() {
        return bounds;
    }

    /** Copper (SIGNAL / POWER_GROUND / MIXED) layer count in board context, or null when none. */
    public Integer getCopperLayerCount() {
        return copperLayerCount;
    }

    /** Narrowest stroked track across all copper layers, in micrometres; null when none measured. */
    public Double getMinTrackWidthUm() {
        return minTrackWidthUm;
    }

    /** Smallest drill across all drill layers, in mm (plated and non-plated); null when none. */
    public Double getMinDrillDiameterMm() {
        return minDrillDiameterMm;
    }

    /** Smallest plated (PTH/via) drill, in mm; null when there is no plated drill. */
    public Double getMinPlatedDrillMm() {
        return minPlatedDrillMm;
    }

    /** Smallest non-plated (NPTH) drill, in mm; null when there is no non-plated drill. */
    public Double getMinNonPlatedDrillMm() {
        return minNonPlatedDrillMm;
    }

    /** Sides carrying solder mask; {@link BoardSide#NONE} when none, never null. */
    public BoardSide getSolderMaskSide() {
        return solderMaskSide;
    }

    /** Sides carrying silkscreen; {@link BoardSide#NONE} when none, never null. */
    public BoardSide getSilkscreenSide() {
        return silkscreenSide;
    }

    /** Sides needing an SMD stencil — paste layers that actually carry features; NONE when none. */
    public BoardSide getStencilSide() {
        return stencilSide;
    }

    /** True when the step has a board profile (outline). */
    public boolean hasProfile() {
        return hasProfile;
    }

    /** True when the board has at least one copper layer. */
    public boolean hasCopper() {
        return hasCopper;
    }

    /** True when the board has at least one drill layer. */
    public boolean hasDrill() {
        return hasDrill;
    }

    /**
     * Whether the board has any via in pad — a drilled hole inside a surface-mount pad, which forces
     * a filled-and-capped via process (IPC-4761 Type VII). {@code null} when it could not be
     * determined: the design has no paste layer or no drill.
     */
    public Boolean hasViaInPad() {
        return viaInPadDetermined ? viaInPad.hasViaInPad() : null;
    }

    /** How many vias in pad were found; 0 when there are none or it was not determined. */
    public int getViaInPadCount() {
        return viaInPad == null ? 0 : viaInPad.getCount();
    }

    /**
     * Which side(s) carry a via in pad, or {@link BoardSide#NONE} when there are none. {@code null}
     * when via-in-pad was not determined (see {@link #hasViaInPad()}).
     */
    public BoardSide getViaInPadSide() {
        return viaInPadDetermined ? BoardSide.of(viaInPad.isOnTop(), viaInPad.isOnBottom()) : null;
    }

    /** The full via-in-pad detection result, or {@code null} when detection was not run. */
    public ViaInPadResult getViaInPad() {
        return viaInPadDetermined ? viaInPad : null;
    }

    /** Total finished board thickness in mm from the stackup/tools, or null when not present. */
    public Double getTotalThicknessMm() {
        return totalThicknessMm;
    }

    /**
     * Whether the design carries controlled-impedance requirements (an impedance file with at least
     * one required-impedance descriptor). Null when no impedance file is present.
     */
    public Boolean getImpedanceControl() {
        return impedanceControl;
    }

    /** Number of components placed on the top side, or null when no component data is present. */
    public Integer getComponentCountTop() {
        return componentCountTop;
    }

    /** Number of components placed on the bottom side, or null when no component data is present. */
    public Integer getComponentCountBottom() {
        return componentCountBottom;
    }

    /** Number of BOM lines, or null when the design ships no BOM. */
    public Integer getBomLineCount() {
        return bomLineCount;
    }

    /** Every analyzed matrix layer, in matrix row order. */
    public List<AnalyzedLayer> getLayers() {
        return layers;
    }

    @Override
    public String toString() {
        return String.format(
                "BoardSpecification[step=%s, %s x %s mm, %s copper, minTrack=%sum, minDrill=%smm]",
                stepName, sizeXMm, sizeYMm, copperLayerCount, minTrackWidthUm, minDrillDiameterMm);
    }
}

package com.deltaproto.deltaodbpp.spec;

/**
 * One entry of the board's physical stack, in build order — a conductor, a dielectric, a mask, a
 * legend: everything the board is actually made of, dielectrics included.
 *
 * <p>{@link BoardSpecification#getStackup()} returns these ordered top of board → bottom with a
 * dense {@link #getOrdinal() ordinal}, so a consumer can persist the list row for row.
 *
 * <p>Two things distinguish this from {@link AnalyzedLayer}. It carries the <em>physical</em>
 * properties — thickness, material, Dk, Df, copper weight — rather than measurements of artwork; and
 * it includes layers that carry no artwork at all, which is how dielectrics get here. Layers that do
 * not build the board (DRILL, ROUT, DOCUMENT, COMPONENT) are not in this list.
 *
 * <p><b>Measured versus estimated.</b> Real archives frequently ship no {@code stackup.xml}, and one
 * that is present often states some values and not others. Every physical property therefore comes
 * with its own {@code …Estimated} flag: false means the value was read from the archive, true means
 * it is an industry typical this library invented so the stack can still be drawn and priced.
 * A caller quoting a board must check the flag — {@link #isThicknessEstimated()} in particular —
 * before treating a number as the design's own. A null value means "not determined": neither stated
 * nor guessable, never zero.
 */
public final class StackupLayer {

    private final int ordinal;
    private final String name;
    private final String function;
    private final LayerSide side;
    private final Integer matrixRow;
    private final Double thicknessMm;
    private final Long thicknessPm;
    private final String material;
    private final Double dielectricConstant;
    private final Double lossTangent;
    private final Double copperWeightOz;
    private final boolean conductor;
    private final boolean dielectric;
    private final boolean thicknessEstimated;
    private final boolean materialEstimated;
    private final boolean dielectricConstantEstimated;
    private final boolean lossTangentEstimated;
    private final boolean copperWeightEstimated;

    private StackupLayer(Builder b) {
        this.ordinal = b.ordinal;
        this.name = b.name;
        this.function = b.function;
        this.side = b.side == null ? LayerSide.NA : b.side;
        this.matrixRow = b.matrixRow;
        this.thicknessMm = b.thicknessMm;
        this.thicknessPm = b.thicknessPm;
        this.material = b.material;
        this.dielectricConstant = b.dielectricConstant;
        this.lossTangent = b.lossTangent;
        this.copperWeightOz = b.copperWeightOz;
        this.conductor = b.conductor;
        this.dielectric = b.dielectric;
        this.thicknessEstimated = b.thicknessEstimated;
        this.materialEstimated = b.materialEstimated;
        this.dielectricConstantEstimated = b.dielectricConstantEstimated;
        this.lossTangentEstimated = b.lossTangentEstimated;
        this.copperWeightEstimated = b.copperWeightEstimated;
    }

    static Builder builder(int ordinal, String name) {
        return new Builder(ordinal, name);
    }

    /** Position in the stack, 0 at the top of the board, dense and gap-free. */
    public int getOrdinal() {
        return ordinal;
    }

    /** Layer name as the matrix (or, for a layer only the stackup file knows, the stackup) spells it. */
    public String getName() {
        return name;
    }

    /**
     * What the layer is for — the ODB++ layer type, uppercased: SIGNAL, POWER_GROUND, MIXED,
     * DIELECTRIC, SOLDER_MASK, SILK_SCREEN, SOLDER_PASTE, CONDUCTIVE_PASTE, MASK. Never null.
     */
    public String getFunction() {
        return function;
    }

    /** Inferred physical side — never null ({@link LayerSide#NA} for dielectrics and the like). */
    public LayerSide getSide() {
        return side;
    }

    /** Matrix row this came from, or null for a layer that only {@code stackup.xml} lists. */
    public Integer getMatrixRow() {
        return matrixRow;
    }

    /** Thickness in millimetres, or null when not determined. See {@link #isThicknessEstimated()}. */
    public Double getThicknessMm() {
        return thicknessMm;
    }

    /**
     * Thickness in picometres, or null when not determined.
     *
     * <p>The exact form of {@link #getThicknessMm()}: 1 µin is exactly 25 400 pm and 1 mil exactly
     * 25 400 000 pm, so a nominal thickness stated in either mils or millimetres lands on a whole
     * number of picometres and a sum of them is exact.
     */
    public Long getThicknessPm() {
        return thicknessPm;
    }

    /** Material name — "Copper", a laminate designation, "Solder Resist" — or null when unknown. */
    public String getMaterial() {
        return material;
    }

    /** Dielectric constant (Dk), or null when the layer has none or it is unknown. */
    public Double getDielectricConstant() {
        return dielectricConstant;
    }

    /** Loss tangent (Df), or null when the layer has none or it is unknown. */
    public Double getLossTangent() {
        return lossTangent;
    }

    /** Copper weight in oz/ft², or null when the layer is not a conductor. */
    public Double getCopperWeightOz() {
        return copperWeightOz;
    }

    /** True when this is a copper layer (SIGNAL, POWER_GROUND, MIXED). */
    public boolean isConductor() {
        return conductor;
    }

    /** True when this is a DIELECTRIC layer. */
    public boolean isDielectric() {
        return dielectric;
    }

    /** True when {@link #getThicknessMm()} is an industry typical rather than the archive's value. */
    public boolean isThicknessEstimated() {
        return thicknessEstimated;
    }

    /** True when {@link #getMaterial()} is a generic name rather than the archive's own. */
    public boolean isMaterialEstimated() {
        return materialEstimated;
    }

    /** True when {@link #getDielectricConstant()} is a typical rather than the archive's value. */
    public boolean isDielectricConstantEstimated() {
        return dielectricConstantEstimated;
    }

    /** True when {@link #getLossTangent()} is a typical rather than the archive's value. */
    public boolean isLossTangentEstimated() {
        return lossTangentEstimated;
    }

    /** True when {@link #getCopperWeightOz()} is a typical rather than the archive's value. */
    public boolean isCopperWeightEstimated() {
        return copperWeightEstimated;
    }

    /** True when any value on this entry was invented rather than read from the archive. */
    public boolean isAnyEstimated() {
        return thicknessEstimated || materialEstimated || dielectricConstantEstimated
                || lossTangentEstimated || copperWeightEstimated;
    }

    /** True when the thickness is the archive's own — the only case a quote may bill against. */
    public boolean isThicknessMeasured() {
        return thicknessMm != null && !thicknessEstimated;
    }

    @Override
    public String toString() {
        return String.format("StackupLayer[%d %s, %s, %s mm%s]",
                ordinal, name, function, thicknessMm, thicknessEstimated ? " (est)" : "");
    }

    static final class Builder {
        private final int ordinal;
        private final String name;
        private String function = "";
        private LayerSide side;
        private Integer matrixRow;
        private Double thicknessMm;
        private Long thicknessPm;
        private String material;
        private Double dielectricConstant;
        private Double lossTangent;
        private Double copperWeightOz;
        private boolean conductor;
        private boolean dielectric;
        private boolean thicknessEstimated;
        private boolean materialEstimated;
        private boolean dielectricConstantEstimated;
        private boolean lossTangentEstimated;
        private boolean copperWeightEstimated;

        private Builder(int ordinal, String name) {
            this.ordinal = ordinal;
            this.name = name;
        }

        Builder function(String function) {
            this.function = function == null ? "" : function;
            return this;
        }

        Builder side(LayerSide side) {
            this.side = side;
            return this;
        }

        Builder matrixRow(Integer matrixRow) {
            this.matrixRow = matrixRow;
            return this;
        }

        /** Thickness in picometres; the mm form is derived from it so the two never disagree. */
        Builder thicknessPm(Long thicknessPm, boolean estimated) {
            this.thicknessPm = thicknessPm;
            this.thicknessMm = thicknessPm == null ? null : thicknessPm / 1_000_000_000.0;
            this.thicknessEstimated = thicknessPm != null && estimated;
            return this;
        }

        Builder material(String material, boolean estimated) {
            this.material = material;
            this.materialEstimated = material != null && estimated;
            return this;
        }

        Builder dielectricConstant(Double dk, boolean estimated) {
            this.dielectricConstant = dk;
            this.dielectricConstantEstimated = dk != null && estimated;
            return this;
        }

        Builder lossTangent(Double df, boolean estimated) {
            this.lossTangent = df;
            this.lossTangentEstimated = df != null && estimated;
            return this;
        }

        Builder copperWeightOz(Double oz, boolean estimated) {
            this.copperWeightOz = oz;
            this.copperWeightEstimated = oz != null && estimated;
            return this;
        }

        Builder conductor(boolean conductor) {
            this.conductor = conductor;
            return this;
        }

        Builder dielectric(boolean dielectric) {
            this.dielectric = dielectric;
            return this;
        }

        StackupLayer build() {
            return new StackupLayer(this);
        }
    }
}

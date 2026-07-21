package com.deltaproto.deltaodbpp.spec;

/**
 * One matrix layer of the analyzed step, classified and measured.
 *
 * <p>The ODB++ equivalent of {@code com.deltaproto.deltagerber.spec.AnalyzedLayer}: instead of a
 * classified file it carries the matrix row, type and context straight from the archive. Every
 * measurement is nullable — a layer may simply not be the kind the measurement applies to (there is
 * no track width on a drill layer). Null means "not determined", never "zero".
 *
 * <p>Built with the {@link #builder(String)} pattern; {@link BoardSpecification} exposes the list.
 */
public final class AnalyzedLayer {

    private final String name;
    private final Integer matrixRow;
    private final String type;
    private final String context;
    private final LayerSide side;
    private final Bounds bounds;
    private final Double minTrackWidthUm;
    private final Double minDrillDiameterMm;
    private final Boolean hasGeometry;

    private AnalyzedLayer(Builder b) {
        this.name = b.name;
        this.matrixRow = b.matrixRow;
        this.type = b.type;
        this.context = b.context;
        this.side = b.side == null ? LayerSide.NA : b.side;
        this.bounds = b.bounds;
        this.minTrackWidthUm = b.minTrackWidthUm;
        this.minDrillDiameterMm = b.minDrillDiameterMm;
        this.hasGeometry = b.hasGeometry;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** Layer name from the matrix (and the steps/&lt;step&gt;/layers directory). */
    public String getName() {
        return name;
    }

    /** Matrix row (top → bottom of the board), or null when the layer is not in the matrix. */
    public Integer getMatrixRow() {
        return matrixRow;
    }

    /** ODB++ matrix layer TYPE (SIGNAL, POWER_GROUND, MIXED, DRILL, SOLDER_MASK, …), or null. */
    public String getType() {
        return type;
    }

    /** ODB++ matrix layer CONTEXT (usually BOARD; also MISC / DOCUMENT), or null. */
    public String getContext() {
        return context;
    }

    /** Inferred physical side — never null ({@link LayerSide#NA} when not determinable). */
    public LayerSide getSide() {
        return side;
    }

    /** Extent of this layer's features in mm, or null when the layer has no measurable geometry. */
    public Bounds getBounds() {
        return bounds;
    }

    /** Narrowest track on this copper layer in micrometres, or null (copper layers only). */
    public Double getMinTrackWidthUm() {
        return minTrackWidthUm;
    }

    /** Smallest drill on this layer in millimetres, or null (drill layers only). */
    public Double getMinDrillDiameterMm() {
        return minDrillDiameterMm;
    }

    /** Whether the layer carries any feature at all; null when not determined. */
    public Boolean getHasGeometry() {
        return hasGeometry;
    }

    @Override
    public String toString() {
        return String.format("AnalyzedLayer[%s, row=%s, %s/%s, %s]",
                name, matrixRow, type, context, side);
    }

    public static final class Builder {
        private final String name;
        private Integer matrixRow;
        private String type;
        private String context;
        private LayerSide side;
        private Bounds bounds;
        private Double minTrackWidthUm;
        private Double minDrillDiameterMm;
        private Boolean hasGeometry;

        private Builder(String name) {
            this.name = name;
        }

        public Builder matrixRow(Integer matrixRow) {
            this.matrixRow = matrixRow;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder context(String context) {
            this.context = context;
            return this;
        }

        public Builder side(LayerSide side) {
            this.side = side;
            return this;
        }

        public Builder bounds(Bounds bounds) {
            this.bounds = bounds != null && bounds.isValid() ? bounds : null;
            return this;
        }

        public Builder minTrackWidthUm(Double minTrackWidthUm) {
            this.minTrackWidthUm = minTrackWidthUm;
            return this;
        }

        public Builder minDrillDiameterMm(Double minDrillDiameterMm) {
            this.minDrillDiameterMm = minDrillDiameterMm;
            return this;
        }

        public Builder hasGeometry(Boolean hasGeometry) {
            this.hasGeometry = hasGeometry;
            return this;
        }

        public AnalyzedLayer build() {
            return new AnalyzedLayer(this);
        }
    }
}

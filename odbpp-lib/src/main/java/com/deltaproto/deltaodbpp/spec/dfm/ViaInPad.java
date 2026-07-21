package com.deltaproto.deltaodbpp.spec.dfm;

/**
 * One drilled hole that lands inside a surface-mount pad — a via in pad.
 *
 * <p>Coordinates and the drill diameter are in millimetres (the model is mm-normalised at parse
 * time). The {@code top}/{@code bottom} flags record which side's paste pad the hole fell inside;
 * both can be true for a through-hole that sits under pads on both sides.
 */
public final class ViaInPad {

    private final double xMm;
    private final double yMm;
    private final double diameterMm;
    private final boolean top;
    private final boolean bottom;

    public ViaInPad(double xMm, double yMm, double diameterMm, boolean top, boolean bottom) {
        this.xMm = xMm;
        this.yMm = yMm;
        this.diameterMm = diameterMm;
        this.top = top;
        this.bottom = bottom;
    }

    public double getXMm() {
        return xMm;
    }

    public double getYMm() {
        return yMm;
    }

    /** Drill diameter in mm, or 0 when the hole's tool size was not known. */
    public double getDiameterMm() {
        return diameterMm;
    }

    public boolean isTop() {
        return top;
    }

    public boolean isBottom() {
        return bottom;
    }

    @Override
    public String toString() {
        return String.format("ViaInPad[%.3f,%.3f d=%.3f %s%s]", xMm, yMm, diameterMm,
                top ? "T" : "", bottom ? "B" : "");
    }
}

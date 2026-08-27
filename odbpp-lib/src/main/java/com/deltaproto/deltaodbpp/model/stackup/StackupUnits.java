package com.deltaproto.deltaodbpp.model.stackup;

import java.util.Locale;

/**
 * The {@code UnitsType} enumeration of the stackup schema (spec pg 509) and the conversions off it.
 *
 * <p>Every length in {@code stackup.xml} is unitless in the file; its unit comes from a {@code Units}
 * attribute alongside it, defaulting to {@link #MIL} and ultimately governed by
 * {@code StackupFile -> DefaultUnits}.
 *
 * <p>Conversion to picometres is exact for all four units — 1 mil is exactly 25 400 000 pm and
 * 1 micron exactly 1 000 000 pm — so nominal imperial and metric thicknesses both survive the trip
 * and integer sums of them stay exact.
 */
public enum StackupUnits {

    MM(1_000_000_000L),
    MICRON(1_000_000L),
    INCH(25_400_000_000L),
    MIL(25_400_000L);

    /** The schema default when no {@code Units} attribute is present anywhere up the chain. */
    public static final StackupUnits DEFAULT = MIL;

    private final long picometres;

    StackupUnits(long picometres) {
        this.picometres = picometres;
    }

    /**
     * Parse a schema {@code Units} attribute value.
     *
     * @return the matching unit, or {@code fallback} when the value is absent or unrecognised.
     */
    public static StackupUnits parse(String value, StackupUnits fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** This many picometres make one of this unit. */
    public long picometres() {
        return picometres;
    }

    /** {@code value} of this unit in millimetres. */
    public double toMm(double value) {
        return value * picometres / 1_000_000_000.0;
    }

    /** {@code value} of this unit in picometres, rounded to the nearest whole picometre. */
    public long toPicometres(double value) {
        return Math.round(value * picometres);
    }
}

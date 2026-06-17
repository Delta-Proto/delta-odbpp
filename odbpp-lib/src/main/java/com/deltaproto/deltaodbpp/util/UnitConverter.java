package com.deltaproto.deltaodbpp.util;

/**
 * Utility class for converting between ODB++ units of measurement.
 * Supported units:
 * - INCH (inches) - default
 * - MM (millimeters)
 * - MIL (thousandths of an inch)
 * - MICRON (micrometers)
 */
public class UnitConverter {

    public enum Unit {
        INCH(1.0),
        MM(25.4),
        MIL(1000.0),
        MICRON(25400.0);

        private final double toInchFactor;

        Unit(double toInchFactor) {
            this.toInchFactor = toInchFactor;
        }

        public double getToInchFactor() {
            return toInchFactor;
        }
    }

    /**
     * Convert a value from one unit to another.
     *
     * @param value The value to convert
     * @param from  The source unit
     * @param to    The target unit
     * @return The converted value
     */
    public static double convert(double value, Unit from, Unit to) {
        if (from == to) {
            return value;
        }
        // Convert to inches first, then to target unit
        double inches = value / from.getToInchFactor();
        return inches * to.getToInchFactor();
    }

    /**
     * Convert a value from one unit to another using string unit names.
     *
     * @param value    The value to convert
     * @param fromUnit The source unit name (INCH, MM, MIL, MICRON)
     * @param toUnit   The target unit name
     * @return The converted value
     */
    public static double convert(double value, String fromUnit, String toUnit) {
        Unit from = parseUnit(fromUnit);
        Unit to = parseUnit(toUnit);
        return convert(value, from, to);
    }

    /**
     * Convert a value to inches.
     *
     * @param value The value to convert
     * @param from  The source unit
     * @return The value in inches
     */
    public static double toInches(double value, Unit from) {
        return value / from.getToInchFactor();
    }

    /**
     * Convert a value to inches using string unit name.
     *
     * @param value    The value to convert
     * @param fromUnit The source unit name
     * @return The value in inches
     */
    public static double toInches(double value, String fromUnit) {
        return toInches(value, parseUnit(fromUnit));
    }

    /**
     * Convert a value from inches to the target unit.
     *
     * @param inches The value in inches
     * @param to     The target unit
     * @return The converted value
     */
    public static double fromInches(double inches, Unit to) {
        return inches * to.getToInchFactor();
    }

    /**
     * Convert a value from inches to the target unit using string unit name.
     *
     * @param inches The value in inches
     * @param toUnit The target unit name
     * @return The converted value
     */
    public static double fromInches(double inches, String toUnit) {
        return fromInches(inches, parseUnit(toUnit));
    }

    /**
     * Parse a unit string to a Unit enum.
     *
     * @param unitStr The unit string
     * @return The corresponding Unit enum value
     */
    public static Unit parseUnit(String unitStr) {
        if (unitStr == null) {
            return Unit.INCH; // Default
        }
        return switch (unitStr.toUpperCase()) {
            case "MM", "MILLIMETER", "MILLIMETERS" -> Unit.MM;
            case "MIL", "MILS" -> Unit.MIL;
            case "MICRON", "MICRONS", "UM" -> Unit.MICRON;
            default -> Unit.INCH;
        };
    }

    /**
     * Normalize an angle to the range [0, 360).
     *
     * @param angle The angle in degrees
     * @return The normalized angle
     */
    public static double normalizeAngle(double angle) {
        angle = angle % 360.0;
        if (angle < 0) {
            angle += 360.0;
        }
        return angle;
    }
}

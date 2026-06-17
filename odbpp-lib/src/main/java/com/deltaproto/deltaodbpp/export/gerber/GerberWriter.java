package com.deltaproto.deltaodbpp.export.gerber;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Low-level RS-274X (Gerber X2) emitter.
 *
 * All coordinates are millimetres; the file is written with format
 * specification FSLAX46Y46 / MOMM (six decimal places, no zero suppression
 * issues since we always emit full integers).
 *
 * Usage: construct, add file attributes, define apertures (via
 * {@link ApertureRegistry}), then emit operations. {@link #build()} produces
 * the complete file including the M02 terminator.
 */
public class GerberWriter {

    private static final double COORD_SCALE = 1_000_000.0; // mm -> 4.6 integer

    private final List<String> fileAttributes = new ArrayList<>();
    private final StringBuilder body = new StringBuilder();
    private ApertureRegistry apertureRegistry;
    private int currentAperture = -1;
    private boolean darkPolarity = true;
    private boolean inRegion = false;

    /** Adds a TF file attribute, e.g. {@code addFileAttribute(".FileFunction", "Copper,L1,Top")}. */
    public void addFileAttribute(String name, String value) {
        fileAttributes.add("%TF" + name + "," + value + "*%");
    }

    public void setApertureRegistry(ApertureRegistry registry) {
        this.apertureRegistry = registry;
    }

    /** Selects the aperture (D-code) for subsequent flash/draw operations. */
    public void selectAperture(int dcode) {
        if (dcode != currentAperture) {
            body.append('D').append(dcode).append("*\n");
            currentAperture = dcode;
        }
    }

    /** Switches between dark (LPD) and clear (LPC) polarity. */
    public void setPolarity(boolean dark) {
        if (dark != darkPolarity) {
            body.append(dark ? "%LPD*%\n" : "%LPC*%\n");
            darkPolarity = dark;
        }
    }

    public void flash(double x, double y) {
        body.append(coord(x, y)).append("D03*\n");
    }

    public void moveTo(double x, double y) {
        body.append(coord(x, y)).append("D02*\n");
    }

    public void lineTo(double x, double y) {
        body.append("G01*\n").append(coord(x, y)).append("D01*\n");
    }

    /**
     * Draws a multi-quadrant (G75) circular arc from the current point to
     * (x, y) around centre (cx, cy). Offsets I/J are relative to the start
     * point, which the caller must have established with moveTo/lineTo.
     */
    public void arcTo(double x, double y, double startX, double startY,
                      double cx, double cy, boolean clockwise) {
        body.append("G75*\n");
        body.append(clockwise ? "G02*\n" : "G03*\n");
        body.append(coord(x, y))
                .append('I').append(units(cx - startX))
                .append('J').append(units(cy - startY))
                .append("D01*\n");
        body.append("G01*\n");
    }

    public void beginRegion() {
        body.append("G36*\n");
        inRegion = true;
    }

    public void endRegion() {
        body.append("G37*\n");
        inRegion = false;
    }

    public boolean isInRegion() {
        return inRegion;
    }

    /** Assembles the complete Gerber file. */
    public String build() {
        StringBuilder out = new StringBuilder();
        for (String attr : fileAttributes) {
            out.append(attr).append('\n');
        }
        out.append("%FSLAX46Y46*%\n");
        out.append("%MOMM*%\n");
        if (apertureRegistry != null) {
            apertureRegistry.emitDefinitions(out);
        }
        out.append("G01*\n");
        out.append(body);
        out.append("M02*\n");
        return out.toString();
    }

    private static String coord(double x, double y) {
        return "X" + units(x) + "Y" + units(y);
    }

    private static String units(double mm) {
        return String.format(Locale.ROOT, "%d", Math.round(mm * COORD_SCALE));
    }
}

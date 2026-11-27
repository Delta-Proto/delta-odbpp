package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Component;
import com.deltaproto.deltaodbpp.model.ContourPolygon;
import com.deltaproto.deltaodbpp.model.EdaData;
import com.deltaproto.deltaodbpp.model.MirrorType;
import com.deltaproto.deltaodbpp.model.ToeprintRecord;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleUnaryOperator;

/**
 * Draws one EDA package the way the components and assembly views of
 * {@link MultiLayerSvgRenderer} draw it — grey body, lighter pads, a red pin-1 inlay — and
 * settles how a component places its package on the board.
 *
 * <p>The views use this class for every component, so a caller that draws packages itself
 * (one part at a time, over its own background) gets exactly the same look.
 *
 * <h2>Fragment frame</h2>
 * {@link #fragment} returns SVG elements, no {@code <svg>} wrapper and no placement transform,
 * in the package's own frame: millimetres, origin at the placement origin, <b>+y up</b> (the
 * ODB++ model frame). Wrap it in a group that flips y, e.g. inside a viewport with
 * {@code scale(1,-1)}, then place it with {@link Placement#svgTransform(double, double)}.
 * The elements, in drawing order:
 * <ol>
 *   <li>the body: {@code <path data-outline="contour">} when the package has an outline, else
 *       {@code <rect data-outline="bbox">} from its bounding box; absent when it has neither;</li>
 *   <li>the pads: one {@code <path data-pins="N">}, N the number of pins drawn; absent when no
 *       pin has an outline;</li>
 *   <li>the pin-1 inlay: {@code <circle data-pin1="name">} on the centre of pin 1's pad; absent
 *       when pin 1 has no outline.</li>
 * </ol>
 */
public final class PackageSvg {

    /** How outlines are stroked. */
    public enum Style {
        /**
         * Hairline outlines, {@code stroke-width="0.5"} with {@code vector-effect="non-scaling-stroke"}:
         * the components view, meant for interactive zooming.
         */
        COMPONENTS,
        /**
         * Outlines {@link #ASSEMBLY_STROKE_MM} wide and the body at {@link #ASSEMBLY_BODY_OPACITY}
         * fill opacity: the assembly view, drawn over a board image and rasterised alike everywhere.
         */
        ASSEMBLY
    }

    // Grey bodies with lighter pads, the same on both sides, and one accent for the pin-1 inlay.
    static final String BODY_FILL = "#6b6b6b";
    static final String BODY_STROKE = "#141414";
    static final String PIN_FILL = "#929292";
    static final String PIN_STROKE = "#555555";
    static final String PIN1_FILL = "#e5392b";

    /** Margin between a pin-1 pad's edge and its inlay, as a fraction of the pad's short side. */
    static final double PIN1_INLAY_MARGIN = 0.20;
    /**
     * Largest diameter of the pin-1 inlay in mm — about the pad of a small chip passive. On
     * bigger pads the inlay stops growing, so it marks the pin without turning a whole power
     * pad into a block of colour.
     */
    static final double PIN1_INLAY_MAX_MM = 0.45;

    /** Fill opacity of component bodies in the assembly style, so the board still reads through. */
    public static final double ASSEMBLY_BODY_OPACITY = 0.8;
    /** Outline width of bodies and pads in the assembly style, in mm. */
    public static final double ASSEMBLY_STROKE_MM = 0.03;

    private static final String NON_SCALING = "stroke-width=\"0.5\" vector-effect=\"non-scaling-stroke\"";

    private PackageSvg() {
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * One package as an SVG fragment in its own frame (mm, +y up); see the class comment for
     * the elements and their attributes. Independent of any {@link SvgRenderOptions}.
     *
     * @return the elements, one per line, or {@code ""} for a {@code null} package and for one
     *         with neither outline, usable bounding box nor pin outlines
     */
    public static String fragment(EdaData.PackageRecord pkg, Style style) {
        if (pkg == null) return "";
        StringWriter out = new StringWriter();
        try {
            writeBody(pkg, DoubleUnaryOperator.identity(), "", out);
            writePins(pkg, DoubleUnaryOperator.identity(), "", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String svg = out.toString();
        return style == Style.ASSEMBLY ? assemblyStyle(svg, ASSEMBLY_STROKE_MM) : svg;
    }

    /**
     * Pin 1 of a package: the pin named {@code 1}, else the one named {@code A1} (any case),
     * else the first pin listed.
     *
     * @return the pin, or {@code null} when the package has no pins
     */
    public static EdaData.PinRecord pinOne(EdaData.PackageRecord pkg) {
        if (pkg == null || pkg.getPinRecords() == null || pkg.getPinRecords().isEmpty()) return null;
        EdaData.PinRecord a1 = null;
        for (EdaData.PinRecord pin : pkg.getPinRecords()) {
            if ("1".equals(pin.getName())) return pin;
            if (a1 == null && "A1".equalsIgnoreCase(pin.getName())) a1 = pin;
        }
        return a1 != null ? a1 : pkg.getPinRecords().get(0);
    }

    /**
     * How a component places its package: in the model frame (+y up), the package is mirrored in
     * X when {@link #flipped()}, then turned counter-clockwise by {@link #angleDeg()} degrees,
     * then moved to the component's position.
     *
     * @param flipped  whether the package is mirrored in X
     * @param angleDeg counter-clockwise turn in degrees, applied after the mirror
     */
    public record Placement(boolean flipped, double angleDeg) {

        /**
         * The SVG {@code transform} placing a {@link #fragment} at {@code (x, y)}, for use inside
         * a group that flips y: {@code translate(x y) rotate(angle)[ scale(-1,1)]}.
         */
        public String svgTransform(double x, double y) {
            return String.format(Locale.US, "translate(%.4f %.4f) rotate(%.2f)%s",
                    x, y, angleDeg, flipped ? " scale(-1,1)" : "");
        }
    }

    /**
     * The placement of a component's package, as the components and assembly views draw it.
     * ODB++ turns packages clockwise by the component's rotation and leaves the mirroring of
     * bottom-side parts to the writer, so this settles it per component; see
     * {@link #isFlipped(Component, EdaData.PackageRecord, boolean)}.
     *
     * @param pkg     the component's package, or {@code null} when unknown
     * @param topSide whether the component sits on the top side
     */
    public static Placement placement(Component c, EdaData.PackageRecord pkg, boolean topSide) {
        boolean flip = isFlipped(c, pkg, topSide);
        return new Placement(flip, flip ? c.getRotation() : -c.getRotation());
    }

    /**
     * Whether a component's package is drawn flipped (mirrored in X, then turned
     * counter-clockwise) instead of plainly turned clockwise by its rotation.
     *
     * <p>Top-side parts are never flipped. For the bottom side writers disagree: some leave the
     * package as designed and expect the reader to flip it (mirror flag {@code N}), others
     * store the package already flipped (mirror flag {@code M}). The component's toeprints say
     * where its pins really are, so when they tell the two readings apart they decide; the
     * side and flag are only the fallback.
     */
    public static boolean isFlipped(Component c, EdaData.PackageRecord pkg, boolean topSide) {
        boolean byFlag = !topSide && c.getMirror() != MirrorType.MIRRORED;
        if (pkg == null || pkg.getPinRecords() == null || c.getToeprintRecords() == null) {
            return byFlag;
        }
        double rad = Math.toRadians(c.getRotation());
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double errPlain = 0, errFlipped = 0;
        for (ToeprintRecord top : c.getToeprintRecords()) {
            int i = top.getPinNumber();
            if (i < 0 || i >= pkg.getPinRecords().size()) continue;
            EdaData.PinRecord pin = pkg.getPinRecords().get(i);
            double px = pin.getXCenter(), py = pin.getYCenter();
            double dx = top.getX() - c.getX(), dy = top.getY() - c.getY();
            // Plain: clockwise by the rotation. Flipped: X mirrored, then counter-clockwise.
            errPlain += sq(px * cos + py * sin - dx) + sq(-px * sin + py * cos - dy);
            errFlipped += sq(-px * cos - py * sin - dx) + sq(-px * sin + py * cos - dy);
        }
        double tolerance = 1e-6;
        if (Math.abs(errPlain - errFlipped) <= tolerance) return byFlag;
        return errFlipped < errPlain;
    }

    // ------------------------------------------------------------------
    // Shared with MultiLayerSvgRenderer
    // ------------------------------------------------------------------

    /**
     * Write a package's body — its outline, else its bounding box — as one indented line.
     *
     * @param unit converts model millimetres to the output unit
     * @return the body's extent {@code [xMin, yMin, xMax, yMax]} in mm, or {@code null} when the
     *         package has neither (nothing written)
     */
    static double[] writeBody(EdaData.PackageRecord pkg, DoubleUnaryOperator unit, String indent,
                              Writer writer) throws IOException {
        // The outline comes first because some writers (KiCad) grow the bounding box to cover
        // the footprint's reference/value text, making parts look several times their real size.
        if (pkg.hasOutline()) {
            writer.write(indent + String.format(Locale.US,
                    "<path d=\"%s\" fill=\"%s\" fill-rule=\"evenodd\" stroke=\"%s\" " +
                            NON_SCALING + " data-outline=\"contour\"/>\n",
                    contourPathData(pkg.getOutline(), unit), BODY_FILL, BODY_STROKE));
            return contourBounds(pkg.getOutline());
        }
        if (pkg.getWidth() > 0 && pkg.getHeight() > 0) {
            writer.write(indent + String.format(Locale.US,
                    "<rect x=\"%.4f\" y=\"%.4f\" width=\"%.4f\" height=\"%.4f\" " +
                            "fill=\"%s\" stroke=\"%s\" " + NON_SCALING + " data-outline=\"bbox\"/>\n",
                    unit.applyAsDouble(pkg.getXMin()), unit.applyAsDouble(pkg.getYMin()),
                    unit.applyAsDouble(pkg.getWidth()), unit.applyAsDouble(pkg.getHeight()),
                    BODY_FILL, BODY_STROKE));
            return new double[] {pkg.getXMin(), pkg.getYMin(), pkg.getXMax(), pkg.getYMax()};
        }
        return null;
    }

    /** A placeholder body for a component without package geometry, sized by the caller. */
    static String fallbackBody(double x, double y, double w, double h) {
        return String.format(Locale.US,
                "<rect x=\"%.4f\" y=\"%.4f\" width=\"%.4f\" height=\"%.4f\" " +
                        "fill=\"%s\" stroke=\"%s\" " + NON_SCALING + " data-outline=\"fallback\"/>",
                x, y, w, h, BODY_FILL, BODY_STROKE);
    }

    /**
     * Write a package's pads: every pin outline in one path, and on top of pin 1's pad an inlay
     * — a circle on the centre of the pad's box, kept a margin of {@link #PIN1_INLAY_MARGIN} of
     * the pad's short side away from its edge and no wider than {@link #PIN1_INLAY_MAX_MM} —
     * that marks the pin while leaving the pad visible around it.
     */
    static void writePins(EdaData.PackageRecord pkg, DoubleUnaryOperator unit, String indent,
                          Writer writer) throws IOException {
        if (pkg == null || pkg.getPinRecords() == null || pkg.getPinRecords().isEmpty()) return;
        List<ContourPolygon> pads = new ArrayList<>();
        int drawn = 0;
        for (EdaData.PinRecord pin : pkg.getPinRecords()) {
            if (!pin.hasOutline()) continue;
            pads.addAll(pin.getOutline());
            drawn++;
        }
        if (pads.isEmpty()) return;
        writer.write(indent + String.format(Locale.US,
                "<path d=\"%s\" fill=\"%s\" fill-rule=\"evenodd\" stroke=\"%s\" " +
                        NON_SCALING + " data-pins=\"%d\"/>\n",
                contourPathData(pads, unit), PIN_FILL, PIN_STROKE, drawn));

        EdaData.PinRecord pin1 = pinOne(pkg);
        if (pin1 == null || !pin1.hasOutline()) return;
        double[] b = contourBounds(pin1.getOutline());
        double w = b[2] - b[0], h = b[3] - b[1];
        double shortSide = Math.min(w, h);
        if (!(shortSide > 0)) return;
        double diameter = Math.min(shortSide * (1 - 2 * PIN1_INLAY_MARGIN), PIN1_INLAY_MAX_MM);
        writer.write(indent + String.format(Locale.US,
                "<circle cx=\"%.4f\" cy=\"%.4f\" r=\"%.4f\" fill=\"%s\" data-pin1=\"%s\"/>\n",
                unit.applyAsDouble((b[0] + b[2]) / 2), unit.applyAsDouble((b[1] + b[3]) / 2),
                unit.applyAsDouble(diameter / 2),
                PIN1_FILL, escapeAttr(pin1.getName())));
    }

    /**
     * Turn components-style markup into the assembly style: real outline width instead of a
     * non-scaling hairline, and the body translucent.
     *
     * @param strokeWidth {@link #ASSEMBLY_STROKE_MM} in the markup's unit
     */
    static String assemblyStyle(String svg, double strokeWidth) {
        return svg
                .replace(NON_SCALING, String.format(Locale.US, "stroke-width=\"%.4f\"", strokeWidth))
                .replace(" data-outline=\"",
                        String.format(Locale.US, " fill-opacity=\"%.2f\" data-outline=\"", ASSEMBLY_BODY_OPACITY));
    }

    /**
     * Extent {@code [xMin, yMin, xMax, yMax]} of a set of contour polygons in model
     * coordinates (mm), arcs included by the part of the circle they actually sweep.
     */
    static double[] contourBounds(List<ContourPolygon> polygons) {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (ContourPolygon polygon : polygons) {
            double px = polygon.getXStart();
            double py = polygon.getYStart();
            grow(b, px, py);
            for (ContourPolygon.PolygonPart part : polygon.getPolygonParts()) {
                double ex = part.getEndX();
                double ey = part.getEndY();
                grow(b, ex, ey);
                if (part.getType() == ContourPolygon.PolygonPart.Type.ARC) {
                    double cx = part.getXCenter();
                    double cy = part.getYCenter();
                    double r = Math.hypot(ex - cx, ey - cy);
                    double a0 = Math.atan2(py - cy, px - cx);
                    double a1 = Math.atan2(ey - cy, ex - cx);
                    // Counter-clockwise sweep from 'from' to 'to'; equal end points are a full circle.
                    double from = part.isClockwise() ? a1 : a0;
                    double to = part.isClockwise() ? a0 : a1;
                    double sweep = ((to - from) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
                    if (px == ex && py == ey) sweep = 2 * Math.PI;
                    for (int q = 0; q < 4; q++) {
                        double axis = q * Math.PI / 2;
                        double offset = ((axis - from) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
                        if (offset <= sweep) grow(b, cx + r * Math.cos(axis), cy + r * Math.sin(axis));
                    }
                }
                px = ex;
                py = ey;
            }
        }
        if (b[0] > b[2]) return new double[] {0, 0, 0, 0};
        return b;
    }

    /**
     * SVG path data for a list of contour polygons in model coordinates (mm), converted by
     * {@code unit}. Islands and holes go into one path so an evenodd fill cuts the holes out.
     * Arcs carry a proper large-arc flag, computed from the swept angle about the centre.
     */
    static String contourPathData(List<ContourPolygon> polygons, DoubleUnaryOperator unit) {
        StringBuilder d = new StringBuilder();
        for (ContourPolygon polygon : polygons) {
            double px = polygon.getXStart();
            double py = polygon.getYStart();
            d.append(String.format(Locale.US, "M %.4f %.4f",
                    unit.applyAsDouble(px), unit.applyAsDouble(py)));
            for (ContourPolygon.PolygonPart part : polygon.getPolygonParts()) {
                double ex = part.getEndX();
                double ey = part.getEndY();
                if (part.getType() == ContourPolygon.PolygonPart.Type.ARC) {
                    double cx = part.getXCenter();
                    double cy = part.getYCenter();
                    double r = Math.hypot(ex - cx, ey - cy);
                    double a0 = Math.atan2(py - cy, px - cx);
                    double a1 = Math.atan2(ey - cy, ex - cx);
                    // Swept angle in the direction of travel (model frame, Y up).
                    double swept = part.isClockwise() ? a0 - a1 : a1 - a0;
                    swept = ((swept % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
                    int largeArc = swept > Math.PI ? 1 : 0;
                    // The viewport flips Y, so a clockwise model arc is drawn with sweep 0.
                    int sweep = part.isClockwise() ? 0 : 1;
                    if (px == ex && py == ey && r > 0) {
                        // Equal end points are a full circle: go by way of the opposite point.
                        d.append(String.format(Locale.US, " A %.4f %.4f 0 1 %d %.4f %.4f",
                                unit.applyAsDouble(r), unit.applyAsDouble(r), sweep,
                                unit.applyAsDouble(2 * cx - px), unit.applyAsDouble(2 * cy - py)));
                        largeArc = 1;
                    }
                    d.append(String.format(Locale.US, " A %.4f %.4f 0 %d %d %.4f %.4f",
                            unit.applyAsDouble(r), unit.applyAsDouble(r), largeArc, sweep,
                            unit.applyAsDouble(ex), unit.applyAsDouble(ey)));
                } else {
                    d.append(String.format(Locale.US, " L %.4f %.4f",
                            unit.applyAsDouble(ex), unit.applyAsDouble(ey)));
                }
                px = ex;
                py = ey;
            }
            d.append(" Z");
        }
        return d.toString();
    }

    private static void grow(double[] b, double x, double y) {
        b[0] = Math.min(b[0], x);
        b[1] = Math.min(b[1], y);
        b[2] = Math.max(b[2], x);
        b[3] = Math.max(b[3], y);
    }

    private static double sq(double v) {
        return v * v;
    }

    private static String escapeAttr(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }
}

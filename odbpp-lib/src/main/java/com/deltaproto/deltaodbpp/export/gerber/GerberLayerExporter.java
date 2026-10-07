package com.deltaproto.deltaodbpp.export.gerber;

import com.deltaproto.deltaodbpp.model.Arc;
import com.deltaproto.deltaodbpp.model.Barcode;
import com.deltaproto.deltaodbpp.model.ContourPolygon;
import com.deltaproto.deltaodbpp.model.Feature;
import com.deltaproto.deltaodbpp.model.Features;
import com.deltaproto.deltaodbpp.model.Line;
import com.deltaproto.deltaodbpp.model.Pad;
import com.deltaproto.deltaodbpp.model.Polarity;
import com.deltaproto.deltaodbpp.model.StandardFont;
import com.deltaproto.deltaodbpp.model.Surface;
import com.deltaproto.deltaodbpp.model.Symbol;
import com.deltaproto.deltaodbpp.model.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts the features of one ODB++ layer into Gerber X2 content.
 *
 * Geometry notes:
 * <ul>
 *   <li>Pads become aperture flashes; symbols Gerber cannot express as
 *       standard apertures become aperture macros (see {@link ApertureRegistry}).</li>
 *   <li>Pads referencing user-defined symbols are flattened: the symbol's own
 *       features are emitted inline, transformed to the pad position and
 *       orientation (nesting supported).</li>
 *   <li>Lines/arcs become draws with a circular aperture. Non-round stroke
 *       symbols are approximated by a circle (Gerber only defines draws for
 *       circles) and reported as a warning.</li>
 *   <li>Surfaces become G36/G37 regions; hole contours are emitted as
 *       clear-polarity (LPC) regions immediately after their islands, which
 *       can over-clear earlier features in pathological overlaps — the same
 *       trade-off every ODB-to-Gerber converter makes short of computing
 *       cut-ins.</li>
 *   <li>Text is stroked using the ODB++ standard font.</li>
 * </ul>
 */
public class GerberLayerExporter {

    private static final int MAX_SYMBOL_NESTING = 4;

    private final List<String> warnings = new ArrayList<>();
    /** Tolerance for a stroke to count as lying on the board outline. */
    private static final double OUTLINE_TOLERANCE_MM = 0.01;

    private final StandardFont font;
    /** Flattened board outline contours, or null when outline strokes are kept. */
    private List<List<double[]>> outline;

    /**
     * Drops lines and arcs that trace the board outline. Altium writes the
     * board shape onto every layer of its ODB++ export while its own Gerber
     * plots leave it out, and an outline stroke on a copper, mask or paste
     * layer is never wanted in fabrication data.
     *
     * @param profile the step profile, whose contours define the outline
     */
    public void dropStrokesOnOutline(Features profile) {
        if (profile == null) {
            this.outline = null;
            return;
        }
        List<List<double[]>> contours = new ArrayList<>();
        for (Feature feature : profile.getFeatures()) {
            if (feature instanceof Surface surface) {
                for (ContourPolygon polygon : surface.getPolygons()) {
                    RegionFracturer.Ring ring = RegionFracturer.ring(polygon, FeatureTransform.IDENTITY);
                    contours.add(RegionFracturer.flatten(ring, 0.002));
                }
            }
        }
        this.outline = contours.isEmpty() ? null : contours;
    }

    private boolean onOutline(double x, double y) {
        for (List<double[]> contour : outline) {
            int n = contour.size();
            for (int i = 0; i < n; i++) {
                double[] a = contour.get(i);
                double[] b = contour.get((i + 1) % n);
                if (pointSegmentDistance(x, y, a[0], a[1], b[0], b[1]) <= OUTLINE_TOLERANCE_MM) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean strokeOnOutline(Line line) {
        return outline != null
                && onOutline(line.getXs(), line.getYs())
                && onOutline(line.getXe(), line.getYe())
                && onOutline((line.getXs() + line.getXe()) / 2, (line.getYs() + line.getYe()) / 2);
    }

    private boolean strokeOnOutline(Arc arc) {
        if (outline == null || !onOutline(arc.getXs(), arc.getYs()) || !onOutline(arc.getXe(), arc.getYe())) {
            return false;
        }
        double r = Math.hypot(arc.getXs() - arc.getXc(), arc.getYs() - arc.getYc());
        double a0 = Math.atan2(arc.getYs() - arc.getYc(), arc.getXs() - arc.getXc());
        double a1 = Math.atan2(arc.getYe() - arc.getYc(), arc.getXe() - arc.getXc());
        double sweep = a1 - a0;
        if ("Y".equalsIgnoreCase(arc.getCw())) {
            while (sweep >= 0) {
                sweep -= 2 * Math.PI;
            }
        } else {
            while (sweep <= 0) {
                sweep += 2 * Math.PI;
            }
        }
        double mid = a0 + sweep / 2;
        return onOutline(arc.getXc() + r * Math.cos(mid), arc.getYc() + r * Math.sin(mid));
    }

    private static double pointSegmentDistance(double px, double py, double ax, double ay,
                                               double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 <= 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }
    private final Map<String, Symbol> userSymbols;

    public GerberLayerExporter(StandardFont font) {
        this(font, Map.of());
    }

    public GerberLayerExporter(StandardFont font, Map<String, Symbol> userSymbols) {
        this.font = font;
        this.userSymbols = userSymbols == null ? Map.of() : userSymbols;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    /**
     * Writes all features to the given writer.
     *
     * @param features parsed layer features (coordinates in mm)
     */
    public void export(Features features, GerberWriter writer, ApertureRegistry registry) {
        export(features, writer, registry, FeatureTransform.IDENTITY, 0, false);
        writer.setPolarity(true);
    }

    /**
     * Writes all features with their polarity inverted: dark features clear,
     * clear features paint. Used to subtract another layer's image from the
     * one being written, e.g. soldermask openings out of a legend.
     */
    public void exportInverted(Features features, GerberWriter writer, ApertureRegistry registry) {
        export(features, writer, registry, FeatureTransform.IDENTITY, 0, true);
        writer.setPolarity(true);
    }

    /**
     * Writes features as a board outline: surface contours are stroked with a
     * thin line instead of filled (the convention fabs expect for a profile
     * layer), other feature types are exported normally.
     */
    public void exportAsOutline(Features features, GerberWriter writer,
                                ApertureRegistry registry, double strokeDiameterMm) {
        double unitToMm = features.isMillimeters() ? 0.001 : 0.0254;
        for (Feature feature : features.getFeatures()) {
            if (feature instanceof Surface surface) {
                writer.setPolarity(true);
                writer.selectAperture(registry.circle(strokeDiameterMm));
                for (ContourPolygon polygon : surface.getPolygons()) {
                    writer.moveTo(polygon.getXStart(), polygon.getYStart());
                    double curX = polygon.getXStart();
                    double curY = polygon.getYStart();
                    for (ContourPolygon.PolygonPart part : polygon.getPolygonParts()) {
                        if (part.getType() == ContourPolygon.PolygonPart.Type.SEGMENT) {
                            writer.lineTo(part.getEndX(), part.getEndY());
                        } else {
                            writer.arcTo(part.getEndX(), part.getEndY(), curX, curY,
                                    part.getXCenter(), part.getYCenter(), part.isClockwise());
                        }
                        curX = part.getEndX();
                        curY = part.getEndY();
                    }
                }
            } else if (feature instanceof Line line) {
                exportLine(line, features, writer, registry, unitToMm,
                        FeatureTransform.IDENTITY, false);
            } else if (feature instanceof Arc arc) {
                exportArc(arc, features, writer, registry, unitToMm,
                        FeatureTransform.IDENTITY, false);
            }
        }
        writer.setPolarity(true);
    }

    private void export(Features features, GerberWriter writer, ApertureRegistry registry,
                        FeatureTransform transform, int depth, boolean invertPolarity) {
        double unitToMm = features.isMillimeters() ? 0.001 : 0.0254;

        for (Feature feature : features.getFeatures()) {
            if (feature instanceof Pad pad) {
                exportPad(pad, features, writer, registry, unitToMm, transform, depth, invertPolarity);
            } else if (feature instanceof Line line) {
                if (depth == 0 && strokeOnOutline(line)) {
                    continue;
                }
                exportLine(line, features, writer, registry, unitToMm, transform, invertPolarity);
            } else if (feature instanceof Arc arc) {
                if (depth == 0 && strokeOnOutline(arc)) {
                    continue;
                }
                exportArc(arc, features, writer, registry, unitToMm, transform, invertPolarity);
            } else if (feature instanceof Surface surface) {
                exportSurface(surface, writer, transform, invertPolarity);
            } else if (feature instanceof Text text) {
                exportText(text, writer, registry, transform, invertPolarity);
            } else if (feature instanceof Barcode) {
                warnings.add("Barcode feature skipped (not supported in Gerber export)");
            }
        }
    }

    private void exportPad(Pad pad, Features features, GerberWriter writer,
                           ApertureRegistry registry, double unitToMm,
                           FeatureTransform transform, int depth, boolean invertPolarity) {
        String symbolName = features.getSymbolName(pad.getSymbolNumber());
        OdbSymbolShape shape = OdbSymbolShape.parse(symbolName, unitToMm);
        boolean negative = isNegative(pad.getPolarity()) ^ invertPolarity;

        if (shape == null) {
            Symbol userSymbol = findUserSymbol(symbolName);
            if (userSymbol != null && userSymbol.getFeatures() != null) {
                if (depth >= MAX_SYMBOL_NESTING) {
                    warnings.add("Symbol nesting deeper than " + MAX_SYMBOL_NESTING
                            + " levels at '" + symbolName + "' — skipped");
                    return;
                }
                FeatureTransform placed = transform.compose(pad.getX(), pad.getY(),
                        padRotationCw(pad), padMirrored(pad));
                export(userSymbol.getFeatures(), writer, registry, placed,
                        depth + 1, negative);
                return;
            }
            warnings.add("Unsupported pad symbol '" + symbolName
                    + "' approximated by a 0.1mm circle");
            shape = OdbSymbolShape.parse(String.format(Locale.ROOT, "r%f", 0.1 / unitToMm), unitToMm);
        }

        double rotationCw = effectiveRotation(transform, padRotationCw(pad), padMirrored(pad));
        double[] position = transform.apply(pad.getX(), pad.getY());
        writer.setPolarity(!negative);
        writer.selectAperture(registry.forShape(shape, rotationCw));
        writer.flash(position[0], position[1]);
    }

    private void exportLine(Line line, Features features, GerberWriter writer,
                            ApertureRegistry registry, double unitToMm,
                            FeatureTransform transform, boolean invertPolarity) {
        double diameter = strokeDiameter(features, line.getSymbolNumber(), unitToMm);
        boolean negative = (line.getPolarity() == Polarity.NEGATIVE) ^ invertPolarity;
        writer.setPolarity(!negative);
        writer.selectAperture(registry.circle(diameter));
        double[] start = transform.apply(line.getXs(), line.getYs());
        double[] end = transform.apply(line.getXe(), line.getYe());
        writer.moveTo(start[0], start[1]);
        writer.lineTo(end[0], end[1]);
    }

    private void exportArc(Arc arc, Features features, GerberWriter writer,
                           ApertureRegistry registry, double unitToMm,
                           FeatureTransform transform, boolean invertPolarity) {
        double diameter = strokeDiameter(features, arc.getSymbolNumber(), unitToMm);
        boolean negative = (arc.getPolarity() == Polarity.NEGATIVE) ^ invertPolarity;
        writer.setPolarity(!negative);
        writer.selectAperture(registry.circle(diameter));
        double[] start = transform.apply(arc.getXs(), arc.getYs());
        double[] end = transform.apply(arc.getXe(), arc.getYe());
        double[] centre = transform.apply(arc.getXc(), arc.getYc());
        boolean clockwise = transform.transformClockwise("Y".equalsIgnoreCase(arc.getCw()));
        writer.moveTo(start[0], start[1]);
        writer.arcTo(end[0], end[1], start[0], start[1], centre[0], centre[1], clockwise);
    }

    /**
     * A surface becomes one region per island, each fractured around the
     * holes it contains (see {@link RegionFracturer}). Islands and holes are
     * matched by containment over the whole surface, not by their order in
     * the file: Altium interleaves them freely and pads the list with
     * zero-area islands. A surface without any island paints its holes with
     * the opposite polarity.
     */
    private void exportSurface(Surface surface, GerberWriter writer,
                               FeatureTransform transform, boolean invertPolarity) {
        boolean surfaceDark = (surface.getPolarity() != Polarity.NEGATIVE) ^ invertPolarity;
        List<RegionFracturer.Ring> islands = new ArrayList<>();
        List<RegionFracturer.Ring> holes = new ArrayList<>();
        for (ContourPolygon polygon : surface.getPolygons()) {
            RegionFracturer.Ring ring = RegionFracturer.ring(polygon, transform);
            if (ring.size() < 2 || Math.abs(RegionFracturer.signedArea(ring)) < 1e-9) {
                continue; // degenerate sliver, inks nothing
            }
            (polygon.getType() == ContourPolygon.Type.ISLAND ? islands : holes).add(ring);
        }
        if (islands.isEmpty()) {
            writer.setPolarity(!surfaceDark);
            for (RegionFracturer.Ring hole : holes) {
                emitRegion(hole, writer);
            }
            return;
        }
        writer.setPolarity(surfaceDark);
        for (RegionFracturer.Ring ring : RegionFracturer.fractureGroup(islands, holes)) {
            emitRegion(ring, writer);
        }
    }

    private void emitRegion(RegionFracturer.Ring ring, GerberWriter writer) {
        int n = ring.size();
        if (n == 0) {
            return;
        }
        writer.beginRegion();
        double[] start = ring.vertices.get(0);
        writer.moveTo(start[0], start[1]);
        for (int k = 0; k < n; k++) {
            double[] a = ring.vertices.get(k);
            double[] b = ring.vertices.get((k + 1) % n);
            double[] arc = ring.edges.get(k);
            if (arc == null) {
                writer.lineTo(b[0], b[1]);
            } else {
                boolean clockwise = arc[2] == 1;
                if (Math.abs(a[0] - b[0]) < 1e-7 && Math.abs(a[1] - b[1]) < 1e-7) {
                    // Full circle: two half arcs, which every reader understands.
                    double mx = 2 * arc[0] - a[0];
                    double my = 2 * arc[1] - a[1];
                    writer.arcTo(mx, my, a[0], a[1], arc[0], arc[1], clockwise);
                    writer.arcTo(b[0], b[1], mx, my, arc[0], arc[1], clockwise);
                } else {
                    writer.arcTo(b[0], b[1], a[0], a[1], arc[0], arc[1], clockwise);
                }
            }
        }
        writer.endRegion();
    }

    private void exportText(Text text, GerberWriter writer, ApertureRegistry registry,
                            FeatureTransform transform, boolean invertPolarity) {
        if (font == null || font.getCharacters() == null || text.getText() == null) {
            warnings.add("Text feature skipped (no standard font available): "
                    + text.getText());
            return;
        }
        String value = text.getText();
        if (value.contains("$$")) {
            warnings.add("Dynamic text variables not substituted: " + value);
        }

        // Stroke width: width_factor is expressed in units of 12 mils (spec).
        double strokeWidth = Math.max(text.getWidthFactor() * 12 * 0.0254, 0.01);
        double scaleX = font.getXSize() > 0 ? text.getXsize() / font.getXSize() : 1.0;
        double scaleY = font.getYSize() > 0 ? text.getYsize() / font.getYSize() : 1.0;

        TextOrientation orient = TextOrientation.parse(text.getOrientDef());
        double rad = Math.toRadians(-orient.rotationCwDeg); // CCW math below

        boolean negative = (text.getPolarity() == Polarity.NEGATIVE) ^ invertPolarity;
        writer.setPolarity(!negative);
        writer.selectAperture(registry.circle(strokeWidth));

        double advance = 0;
        for (char c : value.toCharArray()) {
            StandardFont.CharacterDefinition def = findChar(c);
            if (def != null && def.getLines() != null) {
                for (StandardFont.LineDefinition lineDef : def.getLines()) {
                    double xs = (advance + lineDef.getXs() * scaleX);
                    double ys = lineDef.getYs() * scaleY;
                    double xe = (advance + lineDef.getXe() * scaleX);
                    double ye = lineDef.getYe() * scaleY;
                    if (orient.mirrored) {
                        xs = -xs;
                        xe = -xe;
                    }
                    double rxs = xs * Math.cos(rad) - ys * Math.sin(rad);
                    double rys = xs * Math.sin(rad) + ys * Math.cos(rad);
                    double rxe = xe * Math.cos(rad) - ye * Math.sin(rad);
                    double rye = xe * Math.sin(rad) + ye * Math.cos(rad);
                    double[] start = transform.apply(text.getX() + rxs, text.getY() + rys);
                    double[] end = transform.apply(text.getX() + rxe, text.getY() + rye);
                    writer.moveTo(start[0], start[1]);
                    writer.lineTo(end[0], end[1]);
                }
            }
            advance += text.getXsize();
        }
    }

    private Symbol findUserSymbol(String symbolName) {
        if (symbolName == null) {
            return null;
        }
        Symbol direct = userSymbols.get(symbolName);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, Symbol> e : userSymbols.entrySet()) {
            if (e.getKey().equalsIgnoreCase(symbolName)) {
                return e.getValue();
            }
        }
        return null;
    }

    private StandardFont.CharacterDefinition findChar(char c) {
        for (StandardFont.CharacterDefinition def : font.getCharacters()) {
            if (def.getCharacter() == c) {
                return def;
            }
        }
        return null;
    }

    private double strokeDiameter(Features features, int symbolNumber, double unitToMm) {
        String symbolName = features.getSymbolName(symbolNumber);
        OdbSymbolShape shape = OdbSymbolShape.parse(symbolName, unitToMm);
        if (shape == null) {
            warnings.add("Unsupported stroke symbol '" + symbolName
                    + "' approximated by a 0.1mm circle");
            return 0.1;
        }
        if (shape.kind != OdbSymbolShape.Kind.ROUND) {
            warnings.add("Non-round stroke symbol '" + symbolName
                    + "' approximated by a circle (Gerber draws are circular only)");
        }
        return shape.strokeDiameter();
    }

    /**
     * Combined pad + placement rotation. All supported aperture shapes are
     * symmetric about both axes, so mirroring only negates the angle.
     */
    private double effectiveRotation(FeatureTransform transform, double padRotationCw,
                                     boolean padMirrored) {
        double local = padMirrored ? -padRotationCw : padRotationCw;
        return transform.mirrored
                ? transform.rotationCwDeg - local
                : transform.rotationCwDeg + local;
    }

    /** orientationType 0-7 legacy (90° steps + mirror), 8/9 free rotation. */
    private double padRotationCw(Pad pad) {
        int type = pad.getOrientationType();
        if (type >= 8) {
            return pad.getCustomRotation() != null ? pad.getCustomRotation() : 0;
        }
        return (type % 4) * 90.0;
    }

    private boolean padMirrored(Pad pad) {
        int type = pad.getOrientationType();
        return (type >= 4 && type <= 7) || type == 9;
    }

    private boolean isNegative(String polarity) {
        return "N".equalsIgnoreCase(polarity);
    }

    /** Parsed text orient_def: 0-7 legacy, or "8 <angle>" / "9 <angle>". */
    private record TextOrientation(double rotationCwDeg, boolean mirrored) {
        static TextOrientation parse(String orientDef) {
            if (orientDef == null || orientDef.isBlank()) {
                return new TextOrientation(0, false);
            }
            String[] parts = orientDef.trim().split("\\s+");
            int type;
            try {
                type = Integer.parseInt(parts[0]);
            } catch (NumberFormatException e) {
                return new TextOrientation(0, false);
            }
            if (type >= 8) {
                double angle = parts.length > 1 ? parseOrZero(parts[1]) : 0;
                return new TextOrientation(angle, type == 9);
            }
            return new TextOrientation((type % 4) * 90.0, type >= 4);
        }

        private static double parseOrZero(String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }
}

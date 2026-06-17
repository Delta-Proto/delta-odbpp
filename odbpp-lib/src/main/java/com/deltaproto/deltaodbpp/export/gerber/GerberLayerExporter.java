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
    private final StandardFont font;
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
                exportLine(line, features, writer, registry, unitToMm, transform, invertPolarity);
            } else if (feature instanceof Arc arc) {
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

    private void exportSurface(Surface surface, GerberWriter writer,
                               FeatureTransform transform, boolean invertPolarity) {
        boolean surfaceDark = (surface.getPolarity() != Polarity.NEGATIVE) ^ invertPolarity;
        for (ContourPolygon polygon : surface.getPolygons()) {
            boolean island = polygon.getType() == ContourPolygon.Type.ISLAND;
            writer.setPolarity(island == surfaceDark);
            writer.beginRegion();
            double[] start = transform.apply(polygon.getXStart(), polygon.getYStart());
            writer.moveTo(start[0], start[1]);
            double curX = start[0];
            double curY = start[1];
            for (ContourPolygon.PolygonPart part : polygon.getPolygonParts()) {
                double[] end = transform.apply(part.getEndX(), part.getEndY());
                if (part.getType() == ContourPolygon.PolygonPart.Type.SEGMENT) {
                    writer.lineTo(end[0], end[1]);
                } else {
                    double[] centre = transform.apply(part.getXCenter(), part.getYCenter());
                    boolean clockwise = transform.transformClockwise(part.isClockwise());
                    writer.arcTo(end[0], end[1], curX, curY, centre[0], centre[1], clockwise);
                }
                curX = end[0];
                curY = end[1];
            }
            writer.endRegion();
        }
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

package com.deltaproto.deltaodbpp.export.render;

import com.deltaproto.deltaodbpp.model.Features;
import com.deltaproto.deltaodbpp.model.symbol.StandardSymbol;
import com.deltaproto.deltaodbpp.parser.StandardSymbolParser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves symbol names to SymbolRenderer instances.
 * <p>
 * This class bridges the gap between ODB++ symbol definitions (like "r50", "rect100x50")
 * and the actual SVG rendering by parsing symbol names and creating appropriate renderers.
 * </p>
 * <p>
 * Caches renderers for performance when the same symbol is used multiple times.
 * </p>
 * <p>
 * Symbol dimensions are stored in the ODB++ file as:
 * - When UNITS=INCH: dimensions in mils (1/1000 inch)
 * - When UNITS=MM: dimensions in microns (1/1000 mm)
 * </p>
 */
public class SymbolResolver {

    private final StandardSymbolParser parser;
    private final Map<String, SymbolRenderer> rendererCache;
    private final double defaultSizeMm; // fallback size for unresolvable symbols, in mm
    private final boolean useMillimeters; // true if archive's native unit is MM (symbols in microns), false for INCH (mils)

    /** Default-everything constructor: ~0.25 mm fallback, assumes INCH-native archive. */
    public SymbolResolver() {
        this(0.254, false); // 10 mils ≈ 0.25 mm
    }

    /**
     * @param defaultSize fallback size for unknown symbols, in millimetres
     *     (the model is mm-normalised at parse time).
     */
    public SymbolResolver(double defaultSize) {
        this(defaultSize, false);
    }

    /**
     * @param defaultSize    fallback size for unresolvable symbols, in mm.
     * @param useMillimeters true if the archive's native unit is MM (symbol dimensions
     *                       come from the standard-symbol files in microns); false for
     *                       INCH-native archives (mils). Either way, the resolver
     *                       returns dimensions in <strong>millimetres</strong>.
     */
    public SymbolResolver(double defaultSize, boolean useMillimeters) {
        this.parser = new StandardSymbolParser();
        this.rendererCache = new ConcurrentHashMap<>();
        this.defaultSizeMm = defaultSize;
        this.useMillimeters = useMillimeters;
    }

    /**
     * Resolve a symbol name to a renderer.
     *
     * @param symbolName the symbol name (e.g., "r50", "rect100x50")
     * @return a SymbolRenderer, never null (returns default if symbol cannot be parsed)
     */
    public SymbolRenderer resolve(String symbolName) {
        if (symbolName == null || symbolName.isEmpty()) {
            return getDefaultRenderer();
        }

        return rendererCache.computeIfAbsent(symbolName, this::createRenderer);
    }

    /**
     * Resolve a symbol number using a Features object's symbol table.
     *
     * @param symbolNumber the symbol number reference
     * @param features     the Features object containing the symbol table
     * @return a SymbolRenderer, never null
     */
    public SymbolRenderer resolve(int symbolNumber, Features features) {
        if (features == null || features.getSymbolTable().isEmpty()) {
            return getDefaultRenderer();
        }

        String symbolName = features.getSymbolName(symbolNumber);
        return resolve(symbolName);
    }

    /**
     * Creates a renderer for the given symbol name.
     */
    private SymbolRenderer createRenderer(String symbolName) {
        StandardSymbol symbol = parser.parse(symbolName);
        if (symbol == null) {
            return getDefaultRenderer();
        }

        return createRendererFromSymbol(symbol);
    }

    /**
     * Convert a symbol dimension to millimetres based on the archive's unit system.
     * The model is normalised to mm at parse time, so the renderer treats every
     * length as mm — symbol sizes must match.
     * <ul>
     *   <li>MM units: dimensions in microns (1/1000 mm) → divide by 1000</li>
     *   <li>INCH units: dimensions in mils (1/1000 inch) → multiply by 0.0254 (mils → mm)</li>
     * </ul>
     */
    private double toMm(double dimension) {
        if (useMillimeters) {
            return dimension / 1000.0;        // microns → mm
        } else {
            return dimension * 0.0254;        // mils → mm  (= ÷1000 ×25.4)
        }
    }

    /**
     * Create a SymbolRenderer from a parsed StandardSymbol.
     */
    private SymbolRenderer createRendererFromSymbol(StandardSymbol sym) {
        // Convert symbol dimensions to millimetres so they match the rest of the
        // (mm-normalised) model.
        double w = toMm(sym.getWidth());
        double h = toMm(sym.getHeight());
        double p1 = toMm(sym.getParam1());
        double p2 = toMm(sym.getParam2());
        double p3 = toMm(sym.getParam3());
        double p4 = toMm(sym.getParam4());

        return switch (sym.getType()) {
            case ROUND -> new RoundRenderer(w);
            case SQUARE -> new SquareRenderer(w);
            case RECTANGLE -> new RectangleRenderer(w, h);
            case ROUNDED_RECTANGLE -> new RectangleRenderer(w, h, RectangleRenderer.Variant.ROUNDED, p1);
            case CHAMFERED_RECTANGLE -> new RectangleRenderer(w, h, RectangleRenderer.Variant.CHAMFERED, p1);
            case OVAL -> new OvalRenderer(w, h);
            case ELLIPSE -> new OvalRenderer(w, h, true);
            case DIAMOND -> PolygonRenderer.diamond(w, h);
            case OCTAGON -> PolygonRenderer.octagon(w, h, p1);
            case HEXAGON_L -> PolygonRenderer.hexagonH(w, h, p1);
            case HEXAGON_S -> PolygonRenderer.hexagonV(w, h, p1);
            case TRIANGLE -> PolygonRenderer.triangle(w, h);
            case HALF_OVAL -> PolygonRenderer.halfOval(w, h);
            case ROUND_DONUT -> DonutRenderer.round(w, p1); // w=outer diameter, p1=inner diameter
            case SQUARE_DONUT -> DonutRenderer.square(w, p1); // w=outer size, p1=inner size
            case SQUARE_ROUND_DONUT -> DonutRenderer.squareRound(w, p1); // w=outer (square), p1=inner (round)
            case OVAL_DONUT -> DonutRenderer.oval(w, h, p1);
            case RECT_DONUT -> DonutRenderer.rect(w, h, p1, p2);
            case ROUNDED_SQUARE_DONUT -> DonutRenderer.square(w, p1); // TODO: add rounded corners support
            case ROUNDED_RECT_DONUT -> DonutRenderer.rect(w, h, p1, p2); // TODO: add rounded corners support
            case ROUND_THERMAL -> ThermalRenderer.round(w, p1, sym.getSpokeAngle(), sym.getNumSpokes(), toMm(sym.getGap()));
            case SQUARE_THERMAL, SQUARE_THERMAL_OPEN, ROUNDED_SQUARE_THERMAL ->
                ThermalRenderer.square(w, p1, sym.getSpokeAngle(), sym.getNumSpokes(), toMm(sym.getGap()));
            case SQUARE_ROUND_THERMAL ->
                ThermalRenderer.square(w, p1, sym.getSpokeAngle(), sym.getNumSpokes(), toMm(sym.getGap())); // square outer, round inner
            case LINE_THERMAL ->
                ThermalRenderer.square(w, p1, sym.getSpokeAngle(), sym.getNumSpokes(), toMm(sym.getGap())); // line spokes
            case RECT_THERMAL, RECT_THERMAL_OPEN, ROUNDED_RECT_THERMAL ->
                ThermalRenderer.oval(w, h, p1, sym.getSpokeAngle(), sym.getNumSpokes(), toMm(sym.getGap())); // use oval as approximation
            case OVAL_THERMAL -> ThermalRenderer.oval(w, h, p1, sym.getSpokeAngle(), sym.getNumSpokes(), toMm(sym.getGap()));
            case BUTTERFLY -> {
                // param1: 0 = round, 1 = square
                if (sym.getParam1() == 0) {
                    yield ButterflyRenderer.round(w);
                } else {
                    yield ButterflyRenderer.square(w);
                }
            }
            case MOIRE -> MoireRenderer.create(p1, p2, sym.getNumSpokes(), p3, p4, sym.getSpokeAngle());
            case HOLE -> HoleRenderer.create(w, sym.isPlated(), (int) sym.getParam1(), (int) sym.getParam2());
            case HOME_PLATE -> HomePlateRenderer.homePlate(w, h, p1);
            case INVERTED_HOME_PLATE -> HomePlateRenderer.invertedHomePlate(w, h, p1);
            case FLAT_HOME_PLATE -> HomePlateRenderer.flatHomePlate(w, h, p1, p2);
            case D_SHAPE -> HomePlateRenderer.dShape(w, h, p1);
            case CROSS -> {
                boolean roundEnds = sym.getParam5() == 0;
                double cornerRadius = toMm(sym.getSpokeAngle()); // spokeAngle was reused for corner radius
                if (cornerRadius > 0) {
                    yield CrossRenderer.crossWithRadius(w, h, p1, p2, p3, p4, roundEnds, cornerRadius);
                } else {
                    yield CrossRenderer.cross(w, h, p1, p2, p3, p4, roundEnds);
                }
            }
            case DOGBONE -> {
                boolean roundEnds = sym.getParam4() == 0;
                double cornerRadius = toMm(sym.getSpokeAngle()); // spokeAngle was reused for corner radius
                if (cornerRadius > 0) {
                    yield CrossRenderer.dogboneWithRadius(w, h, p1, p2, p3, roundEnds, cornerRadius);
                } else {
                    yield CrossRenderer.dogbone(w, h, p1, p2, p3, roundEnds);
                }
            }
            case OBLONG_THERMAL -> {
                // oblong thermal is like oval thermal
                yield ThermalRenderer.oval(w, h, p1, sym.getSpokeAngle(), sym.getNumSpokes(), toMm(sym.getGap()));
            }
            case NULL_SYMBOL -> NullRenderer.create((int) sym.getParam1());
            default -> getDefaultRenderer();
        };
    }

    /**
     * Get a default renderer for unknown symbols.
     * Returns a small circle.
     */
    private SymbolRenderer getDefaultRenderer() {
        return new RoundRenderer(defaultSizeMm);
    }

    /**
     * Clear the renderer cache.
     * Useful if memory needs to be freed or for testing.
     */
    public void clearCache() {
        rendererCache.clear();
    }

    /**
     * Get the number of cached renderers.
     */
    public int getCacheSize() {
        return rendererCache.size();
    }
}

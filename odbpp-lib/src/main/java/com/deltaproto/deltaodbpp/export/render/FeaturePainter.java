package com.deltaproto.deltaodbpp.export.render;

import com.deltaproto.deltaodbpp.export.gerber.OdbSymbolShape;
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

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the features of one ODB++ features file as SVG elements.
 *
 * <p>This is the single place that knows how a feature record becomes SVG, shared by
 * every renderer. It follows the same reading of the format as the Gerber converter:
 *
 * <ul>
 *   <li>the model is millimetres; a symbol's dimensions are mils or microns by the
 *       features file's unit, or by the symbol's own {@code I|M} flag;</li>
 *   <li>a pad is rotated clockwise first, then mirrored along x, then placed;</li>
 *   <li>a user-defined symbol is drawn from its own features, nested up to four deep,
 *       and a negative pad of one inverts the polarity of everything inside it;</li>
 *   <li>an arc whose end point equals its start point is a full circle;</li>
 *   <li>a surface's holes cut its islands whatever order they are listed in, and a
 *       surface with only holes paints them in the opposite polarity;</li>
 *   <li>text is drawn with the job's stroke font.</li>
 * </ul>
 *
 * <p>The painter writes into a user space whose y axis points up (the renderers wrap
 * their content in a {@code scale(1,-1)} group), in the caller's output unit.
 *
 * <p>Negative ("clear") features cannot simply be painted in a background colour —
 * they have to erase what lies beneath them on the same layer and nothing else. The
 * caller picks how through the {@link Palette}: {@link #hasClear} tells whether a layer
 * needs it at all, and a layer that does is painted white-on-black into a
 * {@code <mask>}.
 */
public final class FeaturePainter {

    /**
     * The fill and stroke attributes for dark and clear paint.
     */
    public static final class Palette {
        /**
         * Dark paint inherits {@code fill} and {@code stroke} from the element the
         * artwork is placed in; clear paint uses its {@code color}. Lets one copy of a
         * layer's artwork, referenced with {@code <use>}, be coloured per use.
         */
        public static final Palette INHERIT =
                new Palette("", " fill=\"currentColor\"", "", " stroke=\"currentColor\"");

        final String darkFill;
        final String clearFill;
        final String darkStroke;
        final String clearStroke;

        private Palette(String darkFill, String clearFill, String darkStroke, String clearStroke) {
            this.darkFill = darkFill;
            this.clearFill = clearFill;
            this.darkStroke = darkStroke;
            this.clearStroke = clearStroke;
        }

        /** Explicit colours on every element. */
        public static Palette of(String dark, String clear) {
            return new Palette(
                    " fill=\"" + dark + "\"",
                    " fill=\"" + clear + "\"",
                    " stroke=\"" + dark + "\" stroke-linecap=\"round\"",
                    " stroke=\"" + clear + "\" stroke-linecap=\"round\"");
        }

        String fill(boolean clear) {
            return clear ? clearFill : darkFill;
        }

        String stroke(boolean clear) {
            return clear ? clearStroke : darkStroke;
        }
    }

    private static final int MAX_SYMBOL_NESTING = 4;
    /** Width used for a stroke whose symbol cannot be resolved, as in the Gerber converter. */
    private static final double FALLBACK_STROKE_MM = 0.1;
    /** Rings with less area than this (mm²) are slivers some exporters pad surfaces with. */
    private static final double MIN_RING_AREA = 1e-9;
    private static final int FLUSH_AT = 1 << 16;
    /**
     * Strokes thinner than this (mm) have no width to speak of: CAD systems draw document
     * and drawing layers with a zero or near-zero line, meaning "as thin as the device draws".
     */
    private static final double HAIRLINE_BELOW_MM = 0.001;

    private final double u;
    private final int decimals;
    private final long pow;
    private final Map<String, Symbol> userSymbols;
    private final Map<Character, StandardFont.CharacterDefinition> glyphs = new HashMap<>();
    private final double fontXSize;
    private final double fontYSize;
    private final double defaultPadSizeMm;
    private final Map<Features, Map<Integer, SymbolInfo>> symbolCache = new IdentityHashMap<>();
    private final Map<Features, Boolean> clearCache = new IdentityHashMap<>();

    private boolean skipText;
    private boolean compact;
    private double fallbackFontSizeMm = 1.27;
    private double hairlineMm;

    /**
     * @param unitsPerMm       output units per millimetre (1 for mm output, 1/25.4 for inch)
     * @param defaultPadSizeMm size of the dot drawn for a pad whose symbol is unknown
     * @param userSymbols      the job's user-defined symbols by name; may be null
     * @param font             the job's stroke font; null draws text as SVG {@code <text>}
     */
    public FeaturePainter(double unitsPerMm, double defaultPadSizeMm,
                          Map<String, Symbol> userSymbols, StandardFont font) {
        this.u = unitsPerMm;
        // Four decimals of a millimetre is 0.1 um; an inch needs one more for the same.
        this.decimals = unitsPerMm < 0.5 ? 5 : 4;
        long p = 1;
        for (int i = 0; i < decimals; i++) {
            p *= 10;
        }
        this.pow = p;
        this.userSymbols = userSymbols != null ? userSymbols : Map.of();
        this.defaultPadSizeMm = defaultPadSizeMm;
        double xs = 0;
        double ys = 0;
        if (font != null && font.getCharacters() != null) {
            for (StandardFont.CharacterDefinition def : font.getCharacters()) {
                glyphs.putIfAbsent(def.getCharacter(), def);
            }
            xs = font.getXSize();
            ys = font.getYSize();
        }
        this.fontXSize = xs;
        this.fontYSize = ys;
    }

    /** Leave text out (the fast per-layer previews do). */
    public FeaturePainter skipText(boolean skip) {
        this.skipText = skip;
        return this;
    }

    /**
     * Merge runs of strokes that share a width and polarity into one {@code <path>}.
     * Smaller output for the same picture; the features are no longer one element each.
     * With {@link Palette#INHERIT} the element the artwork is placed in must set
     * {@code stroke-linecap} and {@code stroke-linejoin} to {@code round}.
     */
    public FeaturePainter compact(boolean compact) {
        this.compact = compact;
        return this;
    }

    /**
     * Width given to lines and arcs drawn with a zero-width symbol, so that a drawing made of
     * hairlines stays visible. Zero, the default, draws them at their true width.
     */
    public FeaturePainter hairlineMm(double widthMm) {
        this.hairlineMm = Math.max(0, widthMm);
        return this;
    }

    private double strokeWidth(SymbolInfo info) {
        double mm = info.strokeMm;
        return (mm < HAIRLINE_BELOW_MM && hairlineMm > mm ? hairlineMm : mm) * u;
    }

    /** Height used for a text record that states no size and for text without a stroke font. */
    public FeaturePainter fallbackFontSizeMm(double sizeMm) {
        if (sizeMm > 0) {
            this.fallbackFontSizeMm = sizeMm;
        }
        return this;
    }

    // ------------------------------------------------------------------------------------------
    // Clear-paint detection
    // ------------------------------------------------------------------------------------------

    /**
     * Whether drawing these features involves clear paint: a negative feature at any
     * nesting level, or a surface made of holes only.
     */
    public boolean hasClear(Features features) {
        return features != null && hasClear(features, 0);
    }

    private boolean hasClear(Features features, int depth) {
        Boolean known = clearCache.get(features);
        if (known != null) {
            return known;
        }
        boolean clear = false;
        Map<Integer, SymbolInfo> symbols = symbolsOf(features);
        for (Feature feature : features.getFeatures()) {
            if (feature instanceof Pad pad) {
                if ("N".equalsIgnoreCase(pad.getPolarity())) {
                    clear = true;
                } else if (depth < MAX_SYMBOL_NESTING) {
                    SymbolInfo info = info(features, symbols, pad.getSymbolNumber());
                    if (info.user != null && info.user.getFeatures() != null
                            && hasClear(info.user.getFeatures(), depth + 1)) {
                        clear = true;
                    }
                }
            } else if (feature instanceof Line line) {
                clear = line.getPolarity() == Polarity.NEGATIVE;
            } else if (feature instanceof Arc arc) {
                clear = arc.getPolarity() == Polarity.NEGATIVE;
            } else if (feature instanceof Surface surface) {
                clear = surface.getPolarity() == Polarity.NEGATIVE || onlyHoles(rings(surface));
            } else if (feature instanceof Text text) {
                clear = text.getPolarity() == Polarity.NEGATIVE;
            } else if (feature instanceof Barcode barcode) {
                clear = barcode.getPolarity() == Polarity.NEGATIVE;
            }
            if (clear) {
                break;
            }
        }
        clearCache.put(features, clear);
        return clear;
    }

    // ------------------------------------------------------------------------------------------
    // Painting
    // ------------------------------------------------------------------------------------------

    /** Draws every feature, in file order, in the palette's colours. */
    public void paint(Features features, Appendable out, Palette palette) throws IOException {
        if (features == null) {
            return;
        }
        StringBuilder sb = new StringBuilder(1 << 12);
        paintFeatures(features, sb, out, palette, false, 0);
        out.append(sb);
    }

    private void paintFeatures(Features features, StringBuilder sb, Appendable out,
                               Palette palette, boolean invert, int depth) throws IOException {
        Map<Integer, SymbolInfo> symbols = symbolsOf(features);
        StrokeRun run = new StrokeRun();
        for (Feature feature : features.getFeatures()) {
            if (feature instanceof Line line) {
                paintLine(line, info(features, symbols, line.getSymbolNumber()), sb, palette, invert, run);
                continue;
            }
            if (feature instanceof Arc arc) {
                paintArc(arc, info(features, symbols, arc.getSymbolNumber()), sb, palette, invert, run);
                continue;
            }
            run.end(sb);
            if (feature instanceof Pad pad) {
                paintPad(pad, info(features, symbols, pad.getSymbolNumber()), sb, out, palette, invert, depth);
            } else if (feature instanceof Surface surface) {
                paintSurface(surface, sb, palette, invert);
            } else if (feature instanceof Text text) {
                paintText(text, sb, palette, invert);
            } else if (feature instanceof Barcode barcode) {
                paintBarcode(barcode, sb, palette, invert);
            }
            if (depth == 0 && sb.length() > FLUSH_AT) {
                out.append(sb);
                sb.setLength(0);
            }
        }
        run.end(sb);
    }

    // ---- extent ------------------------------------------------------------------------------

    /**
     * Adds the box of what {@link #paint} draws for these features, in mm: unlike the
     * centre lines {@link Extent} measures by itself, pads count with the size of their
     * symbol and lines and arcs with the width of their stroke.
     */
    public void addInk(Features features, Extent into) {
        addInk(features, into, 0);
    }

    private void addInk(Features features, Extent into, int depth) {
        if (features == null) {
            return;
        }
        Map<Integer, SymbolInfo> symbols = symbolsOf(features);
        Extent centre = new Extent();
        for (Feature feature : features.getFeatures()) {
            if (feature instanceof Pad pad) {
                into.add(pad.getX(), pad.getY());
                double[] box = symbolBox(info(features, symbols, pad.getSymbolNumber()), depth);
                if (box != null) {
                    addPlacedBox(into, box, pad.getX(), pad.getY(), padRotationCw(pad), padMirrored(pad),
                            pad.getResizeFactor() != null && pad.getResizeFactor() > 0
                                    ? pad.getResizeFactor() : 1.0);
                }
            } else if (feature instanceof Line || feature instanceof Arc) {
                int number = feature instanceof Line line ? line.getSymbolNumber()
                        : ((Arc) feature).getSymbolNumber();
                SymbolInfo info = info(features, symbols, number);
                double mx = info.strokeMm / 2;
                double my = mx;
                if (feature instanceof Line && info.shape != null
                        && info.shape.kind == OdbSymbolShape.Kind.RECT) {
                    mx = info.shape.width / 2;
                    my = info.shape.height / 2;
                }
                centre.reset();
                centre.add(feature);
                if (!centre.isEmpty()) {
                    into.add(centre.minX() - mx, centre.minY() - my);
                    into.add(centre.maxX() + mx, centre.maxY() + my);
                }
            } else {
                into.add(feature);
            }
        }
    }

    /** The box {minX, minY, maxX, maxY} of a symbol about its origin, in mm; null when it draws nothing. */
    private double[] symbolBox(SymbolInfo info, int depth) {
        if (info.shape != null) {
            double hw = info.shape.width / 2;
            double hh = info.shape.height > 0 ? info.shape.height / 2 : hw;
            return new double[] {-hw, -hh, hw, hh};
        }
        if (info.user != null) {
            if (info.userBox == null && depth < MAX_SYMBOL_NESTING) {
                Extent inner = new Extent();
                addInk(info.user.getFeatures(), inner, depth + 1);
                info.userBox = inner.isEmpty() ? new double[0]
                        : new double[] {inner.minX(), inner.minY(), inner.maxX(), inner.maxY()};
            }
            return info.userBox == null || info.userBox.length == 0 ? null : info.userBox;
        }
        if (info.outline != null) {
            return info.outlineBox;
        }
        double r = defaultPadSizeMm / 2;
        return r > 0 ? new double[] {-r, -r, r, r} : null;
    }

    /** A symbol's box scaled, turned clockwise, mirrored and moved the way its pad places it. */
    private static void addPlacedBox(Extent into, double[] box, double x, double y,
                                     double rotCw, boolean mirror, double resize) {
        double a = Math.toRadians(rotCw);
        double cos = Math.cos(a);
        double sin = Math.sin(a);
        for (int corner = 0; corner < 4; corner++) {
            double cx = box[corner < 2 ? 0 : 2] * resize;
            double cy = box[corner % 2 == 0 ? 1 : 3] * resize;
            double rx = cx * cos + cy * sin;
            double ry = -cx * sin + cy * cos;
            into.add(x + (mirror ? -rx : rx), y + ry);
        }
    }

    // ---- pads --------------------------------------------------------------------------------

    private void paintPad(Pad pad, SymbolInfo info, StringBuilder sb, Appendable out,
                          Palette palette, boolean invert, int depth) throws IOException {
        boolean clear = "N".equalsIgnoreCase(pad.getPolarity()) ^ invert;
        double rotCw = padRotationCw(pad);
        boolean mirror = padMirrored(pad);
        double resize = pad.getResizeFactor() != null && pad.getResizeFactor() > 0
                ? pad.getResizeFactor() : 1.0;

        if (info.shape != null) {
            paintShape(info.shape.scaled(resize), pad.getX(), pad.getY(), rotCw, mirror,
                    palette.fill(clear), sb);
            return;
        }
        if (info.user != null) {
            Features inner = info.user.getFeatures();
            if (inner == null || inner.getFeatures().isEmpty() || depth >= MAX_SYMBOL_NESTING) {
                return;
            }
            sb.append("<g");
            placement(sb, pad.getX(), pad.getY(), rotCw, mirror, resize);
            sb.append(">\n");
            paintFeatures(inner, sb, out, palette, clear, depth + 1);
            sb.append("</g>\n");
            return;
        }
        if (info.outline != null) {
            // Any other standard symbol, as an outline about its own origin.
            if (info.outline.isEmpty()) {
                return;
            }
            sb.append("<path");
            placement(sb, pad.getX(), pad.getY(), rotCw, mirror, resize);
            sb.append(" d=\"").append(info.outline).append('"');
            if (info.outlineHasHoles) {
                sb.append(" fill-rule=\"evenodd\"");
            }
            sb.append(palette.fill(clear)).append("/>\n");
            return;
        }
        // A symbol nobody knows: a dot marks the spot.
        if (defaultPadSizeMm > 0) {
            circle(sb, pad.getX() * u, pad.getY() * u, defaultPadSizeMm * resize * u / 2,
                    palette.fill(clear));
        }
    }

    /** Path data of a shape given in mm about the origin, in output units. */
    private String pathData(Shape shape) {
        StringBuilder d = new StringBuilder();
        double[] c = new double[6];
        for (PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
            int type = it.currentSegment(c);
            int points;
            switch (type) {
                case PathIterator.SEG_MOVETO -> {
                    d.append('M');
                    points = 1;
                }
                case PathIterator.SEG_LINETO -> {
                    d.append('L');
                    points = 1;
                }
                case PathIterator.SEG_QUADTO -> {
                    d.append('Q');
                    points = 2;
                }
                case PathIterator.SEG_CUBICTO -> {
                    d.append('C');
                    points = 3;
                }
                default -> {
                    d.append('Z');
                    points = 0;
                }
            }
            for (int i = 0; i < points; i++) {
                d.append(' ');
                num(d, c[2 * i] * u);
                d.append(' ');
                num(d, c[2 * i + 1] * u);
            }
            d.append(' ');
        }
        return d.toString().trim();
    }

    /**
     * A standard symbol the painter draws itself. Rotation is clockwise and applied
     * before the mirror; every shape here is drawn in absolute coordinates where the
     * resulting angle allows, so the common case carries no transform.
     */
    private void paintShape(OdbSymbolShape shape, double x, double y, double rotCw,
                            boolean mirror, String fill, StringBuilder sb) {
        // mirror(rotate(s, a)) = rotate(mirror(s), -a): fold the mirror into the shape.
        double angle = normalize(mirror ? -rotCw : rotCw);
        if (mirror) {
            shape = shape.mirrored();
        }
        double cx = x * u;
        double cy = y * u;
        switch (shape.kind) {
            case ROUND -> {
                if (shape.width > 0) {
                    circle(sb, cx, cy, shape.width * u / 2, fill);
                }
            }
            case DONUT -> {
                double ro = shape.width * u / 2;
                double ri = shape.innerDiameter * u / 2;
                if (ri >= ro) {
                    return;
                }
                sb.append("<path d=\"");
                circlePath(sb, cx, cy, ro);
                sb.append(' ');
                circlePath(sb, cx, cy, ri);
                sb.append("\" fill-rule=\"evenodd\"").append(fill).append("/>\n");
            }
            case THERMAL -> thermal(shape, cx, cy, angle, fill, sb);
            case SQUARE, RECT, OVAL -> {
                double w = shape.width * u;
                double h = (shape.kind == OdbSymbolShape.Kind.SQUARE ? shape.width : shape.height) * u;
                double r = shape.kind == OdbSymbolShape.Kind.OVAL ? Math.min(w, h) / 2 : 0;
                rect(sb, cx, cy, w, h, r, angle, fill);
            }
            case ROUNDED_RECT, CHAMFERED_RECT -> {
                double w = shape.width * u;
                double h = shape.height * u;
                double c = Math.min(shape.cornerSize * u, Math.min(w, h) / 2);
                if (c <= 0) {
                    rect(sb, cx, cy, w, h, 0, angle, fill);
                } else if (shape.kind == OdbSymbolShape.Kind.ROUNDED_RECT && shape.allCorners()) {
                    rect(sb, cx, cy, w, h, c, angle, fill);
                } else {
                    corneredRect(shape, cx, cy, w, h, c, angle, fill, sb);
                }
            }
            default -> { }
        }
    }

    private void circle(StringBuilder sb, double cx, double cy, double r, String fill) {
        sb.append("<circle cx=\"");
        num(sb, cx);
        sb.append("\" cy=\"");
        num(sb, cy);
        sb.append("\" r=\"");
        num(sb, r);
        sb.append('"').append(fill).append("/>\n");
    }

    /** A rectangle centred on (cx, cy), corners rounded by r, turned clockwise by angle. */
    private void rect(StringBuilder sb, double cx, double cy, double w, double h, double r,
                      double angle, String fill) {
        if (w <= 0 || h <= 0) {
            return;
        }
        boolean upright = angle == 0 || angle == 180;
        boolean sideways = angle == 90 || angle == 270;
        if (sideways) {
            double t = w;
            w = h;
            h = t;
        }
        boolean local = !upright && !sideways;
        sb.append("<rect x=\"");
        num(sb, (local ? 0 : cx) - w / 2);
        sb.append("\" y=\"");
        num(sb, (local ? 0 : cy) - h / 2);
        sb.append("\" width=\"");
        num(sb, w);
        sb.append("\" height=\"");
        num(sb, h);
        sb.append('"');
        if (r > 0) {
            sb.append(" rx=\"");
            num(sb, r);
            sb.append("\" ry=\"");
            num(sb, r);
            sb.append('"');
        }
        sb.append(fill);
        if (local) {
            turned(sb, cx, cy, angle);
        }
        sb.append("/>\n");
    }

    /**
     * A rectangle whose corners are individually plain, rounded or chamfered. Corners are
     * numbered counter-clockwise from the top right (1) to the bottom right (4).
     */
    private void corneredRect(OdbSymbolShape shape, double cx, double cy, double w, double h,
                              double c, double angle, String fill, StringBuilder sb) {
        boolean local = angle != 0;
        double ox = local ? 0 : cx;
        double oy = local ? 0 : cy;
        double hw = w / 2;
        double hh = h / 2;
        boolean rounded = shape.kind == OdbSymbolShape.Kind.ROUNDED_RECT;
        double c1 = shape.hasCorner(1) ? c : 0;
        double c2 = shape.hasCorner(2) ? c : 0;
        double c3 = shape.hasCorner(3) ? c : 0;
        double c4 = shape.hasCorner(4) ? c : 0;

        sb.append("<path d=\"M ");
        point(sb, ox + hw, oy - hh + c4);
        lineTo(sb, ox + hw, oy + hh - c1);
        corner(sb, rounded, c1, ox + hw - c1, oy + hh);
        lineTo(sb, ox - hw + c2, oy + hh);
        corner(sb, rounded, c2, ox - hw, oy + hh - c2);
        lineTo(sb, ox - hw, oy - hh + c3);
        corner(sb, rounded, c3, ox - hw + c3, oy - hh);
        lineTo(sb, ox + hw - c4, oy - hh);
        corner(sb, rounded, c4, ox + hw, oy - hh + c4);
        sb.append(" Z\"").append(fill);
        if (local) {
            turned(sb, cx, cy, angle);
        }
        sb.append("/>\n");
    }

    private void corner(StringBuilder sb, boolean rounded, double c, double x, double y) {
        if (c <= 0) {
            return;
        }
        if (rounded) {
            sb.append(" A ");
            num(sb, c);
            sb.append(' ');
            num(sb, c);
            sb.append(" 0 0 1 ");
            point(sb, x, y);
        } else {
            lineTo(sb, x, y);
        }
    }

    /**
     * Round thermal relief: a ring between the inner and outer diameter, cut by
     * parallel-sided gaps whose first one is centred on the gap angle (counter-clockwise
     * from the x axis).
     */
    private void thermal(OdbSymbolShape shape, double cx, double cy, double angle,
                         String fill, StringBuilder sb) {
        double ro = shape.width * u / 2;
        double ri = Math.max(shape.innerDiameter * u / 2, 0);
        if (ro <= 0 || ri >= ro) {
            return;
        }
        int n = shape.spokes;
        double halfGap = shape.gap * u / 2;
        if (n <= 0 || halfGap <= 0) {
            sb.append("<path d=\"");
            circlePath(sb, cx, cy, ro);
            if (ri > 0) {
                sb.append(' ');
                circlePath(sb, cx, cy, ri);
            }
            sb.append("\" fill-rule=\"evenodd\"").append(fill).append("/>\n");
            return;
        }
        double pitch = 2 * Math.PI / n;
        // Where the edges of two neighbouring gaps meet: nothing is left inside this radius.
        double apex = n > 1 ? halfGap / Math.sin(pitch / 2) : 0;
        if (halfGap >= ro || apex >= ro) {
            return;
        }
        double outerHalf = Math.asin(halfGap / ro);
        boolean pointed = n > 1 && ri <= apex;
        double innerHalf = pointed || ri <= halfGap ? 0 : Math.asin(halfGap / ri);
        double first = Math.toRadians(shape.gapAngle - angle);

        sb.append("<path d=\"");
        for (int k = 0; k < n; k++) {
            double from = first + k * pitch;
            double to = from + pitch;
            double sweep = pitch - 2 * outerHalf;
            if (k > 0) {
                sb.append(' ');
            }
            sb.append("M ");
            point(sb, cx + ro * Math.cos(from + outerHalf), cy + ro * Math.sin(from + outerHalf));
            arc(sb, ro, sweep > Math.PI, true,
                    cx + ro * Math.cos(to - outerHalf), cy + ro * Math.sin(to - outerHalf));
            if (pointed) {
                double mid = (from + to) / 2;
                lineTo(sb, cx + apex * Math.cos(mid), cy + apex * Math.sin(mid));
            } else if (ri <= halfGap) {
                // A single gap wider than the inner circle: its two edges, closed across.
                lineTo(sb, cx + halfGap * Math.sin(to), cy - halfGap * Math.cos(to));
                lineTo(sb, cx - halfGap * Math.sin(from), cy + halfGap * Math.cos(from));
            } else {
                lineTo(sb, cx + ri * Math.cos(to - innerHalf), cy + ri * Math.sin(to - innerHalf));
                arc(sb, ri, pitch - 2 * innerHalf > Math.PI, false,
                        cx + ri * Math.cos(from + innerHalf), cy + ri * Math.sin(from + innerHalf));
            }
            sb.append(" Z");
        }
        sb.append('"').append(fill).append("/>\n");
    }

    // ---- lines and arcs ----------------------------------------------------------------------

    private void paintLine(Line line, SymbolInfo info, StringBuilder sb, Palette palette,
                           boolean invert, StrokeRun run) {
        boolean clear = (line.getPolarity() == Polarity.NEGATIVE) ^ invert;
        double xs = line.getXs() * u;
        double ys = line.getYs() * u;
        double xe = line.getXe() * u;
        double ye = line.getYe() * u;

        OdbSymbolShape shape = info.shape;
        if (shape != null && (shape.kind == OdbSymbolShape.Kind.SQUARE
                || shape.kind == OdbSymbolShape.Kind.RECT)
                && !(hairlineMm > 0 && info.strokeMm < HAIRLINE_BELOW_MM)) {
            // A square or rectangular symbol sweeps the hull of its two end positions.
            run.end(sb);
            double hw = shape.width * u / 2;
            double hh = (shape.kind == OdbSymbolShape.Kind.SQUARE ? shape.width : shape.height) * u / 2;
            double sx = xe >= xs ? hw : -hw;
            double sy = ye >= ys ? hh : -hh;
            sb.append("<path d=\"M ");
            point(sb, xs - sx, ys - sy);
            lineTo(sb, xs + sx, ys - sy);
            lineTo(sb, xe + sx, ye - sy);
            lineTo(sb, xe + sx, ye + sy);
            lineTo(sb, xe - sx, ye + sy);
            lineTo(sb, xs - sx, ys + sy);
            sb.append(" Z\"").append(palette.fill(clear)).append("/>\n");
            return;
        }

        boolean continued = run.begin(sb, strokeWidth(info), clear, palette);
        if (continued && run.at(xs, ys)) {
            lineTo(sb, xe, ye);
        } else {
            if (continued) {
                sb.append(' ');
            }
            sb.append("M ");
            point(sb, xs, ys);
            lineTo(sb, xe, ye);
        }
        run.moveTo(xe, ye);
        if (!compact) {
            run.end(sb);
        }
    }

    private void paintArc(Arc arc, SymbolInfo info, StringBuilder sb, Palette palette,
                          boolean invert, StrokeRun run) {
        boolean clear = (arc.getPolarity() == Polarity.NEGATIVE) ^ invert;
        double xs = arc.getXs() * u;
        double ys = arc.getYs() * u;
        double xe = arc.getXe() * u;
        double ye = arc.getYe() * u;
        boolean clockwise = "Y".equalsIgnoreCase(arc.getCw());
        boolean full = arc.getXs() == arc.getXe() && arc.getYs() == arc.getYe();

        boolean continued = run.begin(sb, strokeWidth(info), clear, palette);
        if (!(continued && run.at(xs, ys))) {
            if (continued) {
                sb.append(' ');
            }
            sb.append("M ");
            point(sb, xs, ys);
        }
        arcTo(sb, xs, ys, xe, ye, arc.getXc() * u, arc.getYc() * u, clockwise, full);
        run.moveTo(xe, ye);
        if (!compact) {
            run.end(sb);
        }
    }

    /**
     * A run of strokes sharing one {@code <path>}: it stays open while consecutive
     * strokes have the same width and polarity.
     */
    private final class StrokeRun {
        private boolean open;
        private long width;
        private boolean clear;
        private long lastX;
        private long lastY;
        private String tail;

        /** @return true when the stroke continues the path that is already open */
        boolean begin(StringBuilder sb, double strokeWidth, boolean clear, Palette palette) {
            long key = Math.round(strokeWidth * pow);
            if (open && key == width && clear == this.clear) {
                return true;
            }
            end(sb);
            StringBuilder t = new StringBuilder("\"").append(palette.stroke(clear));
            if (compact && palette != Palette.INHERIT) {
                // Strokes that share a path meet in joins; apart, their round ends overlap.
                t.append(" stroke-linejoin=\"round\"");
            }
            t.append(" stroke-width=\"");
            num(t, strokeWidth);
            t.append("\" fill=\"none\"/>\n");
            tail = t.toString();
            sb.append("<path d=\"");
            open = true;
            width = key;
            this.clear = clear;
            return false;
        }

        boolean at(double x, double y) {
            return Math.round(x * pow) == lastX && Math.round(y * pow) == lastY;
        }

        void moveTo(double x, double y) {
            lastX = Math.round(x * pow);
            lastY = Math.round(y * pow);
        }

        void end(StringBuilder sb) {
            if (open) {
                sb.append(tail);
                open = false;
            }
        }
    }

    // ---- surfaces ----------------------------------------------------------------------------

    private void paintSurface(Surface surface, StringBuilder sb, Palette palette, boolean invert) {
        List<Ring> rings = rings(surface);
        if (rings.isEmpty()) {
            return;
        }
        boolean dark = (surface.getPolarity() != Polarity.NEGATIVE) ^ invert;
        boolean onlyHoles = onlyHoles(rings);
        if (onlyHoles) {
            dark = !dark;
        }
        sb.append("<path d=\"");
        ringsPath(sb, rings, onlyHoles);
        sb.append('"').append(palette.fill(!dark)).append("/>\n");
    }

    /**
     * The path data of every surface in a features file (a profile, typically): islands
     * wound one way and holes the other, so a non-zero fill or clip leaves the holes open.
     *
     * @return the path data, empty when there is no surface
     */
    public String surfacePathData(Features features) {
        StringBuilder sb = new StringBuilder();
        if (features == null) {
            return "";
        }
        for (Feature feature : features.getFeatures()) {
            if (feature instanceof Surface surface) {
                List<Ring> rings = rings(surface);
                if (rings.isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                ringsPath(sb, rings, onlyHoles(rings));
            }
        }
        return sb.toString();
    }

    /**
     * The path data of an outline: its surfaces as in {@link #surfacePathData}, followed by
     * its lines and arcs. Lines and arcs that continue one another share a subpath, so an
     * outline drawn as a chain of them comes out as one closed figure.
     *
     * @return the path data, empty when there is nothing to draw
     */
    public String outlinePathData(Features features) {
        if (features == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(surfacePathData(features));
        boolean open = false;
        double lastX = 0;
        double lastY = 0;
        for (Feature feature : features.getFeatures()) {
            double xs;
            double ys;
            double xe;
            double ye;
            Arc arc = null;
            if (feature instanceof Line line) {
                xs = line.getXs();
                ys = line.getYs();
                xe = line.getXe();
                ye = line.getYe();
            } else if (feature instanceof Arc a) {
                arc = a;
                xs = a.getXs();
                ys = a.getYs();
                xe = a.getXe();
                ye = a.getYe();
            } else {
                continue;
            }
            boolean joined = open && Math.round(xs * u * pow) == Math.round(lastX * u * pow)
                    && Math.round(ys * u * pow) == Math.round(lastY * u * pow);
            if (!joined) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append("M ");
                point(sb, xs * u, ys * u);
            }
            if (arc != null) {
                arcTo(sb, xs * u, ys * u, xe * u, ye * u, arc.getXc() * u, arc.getYc() * u,
                        "Y".equalsIgnoreCase(arc.getCw()), xs == xe && ys == ye);
            } else {
                lineTo(sb, xe * u, ye * u);
            }
            open = true;
            lastX = xe;
            lastY = ye;
        }
        return sb.toString();
    }

    private void ringsPath(StringBuilder sb, List<Ring> rings, boolean holesAsIslands) {
        boolean first = true;
        for (Ring ring : rings) {
            boolean wantCcw = holesAsIslands || !ring.hole;
            if (!first) {
                sb.append(' ');
            }
            first = false;
            ringPath(sb, ring, (ring.area > 0) != wantCcw);
        }
    }

    private static boolean onlyHoles(List<Ring> rings) {
        if (rings.isEmpty()) {
            return false;
        }
        for (Ring ring : rings) {
            if (!ring.hole) {
                return false;
            }
        }
        return true;
    }

    /** One closed contour in millimetres; segment i runs from point i to point i + 1. */
    private static final class Ring {
        boolean hole;
        double area;
        double[] x;
        double[] y;
        boolean[] isArc;
        double[] cx;
        double[] cy;
        boolean[] cw;
        int segments;
    }

    /** The contours of a surface that enclose any area. */
    private static List<Ring> rings(Surface surface) {
        List<Ring> rings = new ArrayList<>();
        if (surface.getPolygons() == null) {
            return rings;
        }
        for (ContourPolygon polygon : surface.getPolygons()) {
            List<ContourPolygon.PolygonPart> parts = polygon.getPolygonParts();
            if (parts == null || parts.isEmpty()) {
                continue;
            }
            int n = parts.size();
            Ring ring = new Ring();
            ring.hole = polygon.getType() == ContourPolygon.Type.HOLE;
            ring.segments = n;
            ring.x = new double[n + 1];
            ring.y = new double[n + 1];
            ring.isArc = new boolean[n];
            ring.cx = new double[n];
            ring.cy = new double[n];
            ring.cw = new boolean[n];
            ring.x[0] = polygon.getXStart();
            ring.y[0] = polygon.getYStart();
            double area = 0;
            for (int i = 0; i < n; i++) {
                ContourPolygon.PolygonPart part = parts.get(i);
                ring.x[i + 1] = part.getEndX();
                ring.y[i + 1] = part.getEndY();
                area += (ring.x[i] * ring.y[i + 1] - ring.x[i + 1] * ring.y[i]) / 2;
                if (part.getType() == ContourPolygon.PolygonPart.Type.ARC) {
                    ring.isArc[i] = true;
                    ring.cx[i] = part.getXCenter();
                    ring.cy[i] = part.getYCenter();
                    ring.cw[i] = part.isClockwise();
                    double r = Math.hypot(ring.x[i] - ring.cx[i], ring.y[i] - ring.cy[i]);
                    double sweep = sweep(ring.x[i], ring.y[i], ring.x[i + 1], ring.y[i + 1],
                            ring.cx[i], ring.cy[i], ring.cw[i]);
                    // The circular segment between the arc and its chord.
                    area += r * r / 2 * (sweep - Math.sin(sweep));
                }
            }
            area += (ring.x[n] * ring.y[0] - ring.x[0] * ring.y[n]) / 2;
            ring.area = area;
            if (Math.abs(area) >= MIN_RING_AREA) {
                rings.add(ring);
            }
        }
        return rings;
    }

    /** Signed sweep of an arc in radians, positive counter-clockwise; ±2π for a full circle. */
    private static double sweep(double xs, double ys, double xe, double ye,
                                double xc, double yc, boolean clockwise) {
        if (xs == xe && ys == ye) {
            return clockwise ? -2 * Math.PI : 2 * Math.PI;
        }
        double a0 = Math.atan2(ys - yc, xs - xc);
        double a1 = Math.atan2(ye - yc, xe - xc);
        double d = clockwise ? a0 - a1 : a1 - a0;
        while (d <= 0) {
            d += 2 * Math.PI;
        }
        while (d > 2 * Math.PI) {
            d -= 2 * Math.PI;
        }
        return clockwise ? -d : d;
    }

    private void ringPath(StringBuilder sb, Ring ring, boolean reversed) {
        int n = ring.segments;
        sb.append("M ");
        if (!reversed) {
            point(sb, ring.x[0] * u, ring.y[0] * u);
            for (int i = 0; i < n; i++) {
                segment(sb, ring, i, i, i + 1, ring.cw[i]);
            }
        } else {
            point(sb, ring.x[n] * u, ring.y[n] * u);
            for (int i = n - 1; i >= 0; i--) {
                segment(sb, ring, i, i + 1, i, !ring.cw[i]);
            }
        }
        sb.append(" Z");
    }

    private void segment(StringBuilder sb, Ring ring, int i, int from, int to, boolean clockwise) {
        if (ring.isArc[i]) {
            boolean full = ring.x[from] == ring.x[to] && ring.y[from] == ring.y[to];
            arcTo(sb, ring.x[from] * u, ring.y[from] * u, ring.x[to] * u, ring.y[to] * u,
                    ring.cx[i] * u, ring.cy[i] * u, clockwise, full);
        } else {
            lineTo(sb, ring.x[to] * u, ring.y[to] * u);
        }
    }

    // ---- text and barcodes -------------------------------------------------------------------

    private void paintText(Text text, StringBuilder sb, Palette palette, boolean invert) {
        String value = text.getText();
        if (skipText || value == null || value.isEmpty()) {
            return;
        }
        boolean clear = (text.getPolarity() == Polarity.NEGATIVE) ^ invert;
        double[] orient = orientation(text.getOrientDef());
        double rotCw = orient[0];
        boolean mirror = orient[1] != 0;

        boolean sized = text.getXsize() > 0 && text.getYsize() > 0;
        if (glyphs.isEmpty() || fontXSize <= 0 || fontYSize <= 0 || !sized) {
            // No stroke font to draw with: leave the string to the viewer's own font.
            double size = (text.getYsize() > 0 ? text.getYsize() : fallbackFontSizeMm) * u;
            sb.append("<text x=\"0\" y=\"0\" font-size=\"");
            num(sb, size);
            sb.append("\" font-family=\"monospace\" stroke=\"none\"").append(palette.fill(clear));
            sb.append(" transform=\"translate(");
            num(sb, text.getX() * u);
            sb.append(' ');
            num(sb, text.getY() * u);
            sb.append(") scale(").append(mirror ? "-1" : "1").append(" -1)");
            if (rotCw != 0) {
                sb.append(" rotate(").append(angle(mirror ? -rotCw : rotCw)).append(')');
            }
            sb.append("\">").append(escape(value)).append("</text>\n");
            return;
        }

        double scaleX = text.getXsize() / fontXSize * u;
        double scaleY = text.getYsize() / fontYSize * u;
        // width_factor counts in units of 12 mil.
        double strokeWidth = Math.max(text.getWidthFactor() * 12 * 0.0254, 0.01) * u;
        double advance = 0;
        boolean any = false;
        int mark = sb.length();
        sb.append("<path d=\"");
        long lastX = Long.MIN_VALUE;
        long lastY = Long.MIN_VALUE;
        for (int i = 0; i < value.length(); i++) {
            StandardFont.CharacterDefinition def = glyphs.get(value.charAt(i));
            if (def != null && def.getLines() != null) {
                for (StandardFont.LineDefinition stroke : def.getLines()) {
                    double xs = advance + stroke.getXs() * scaleX;
                    double ys = stroke.getYs() * scaleY;
                    double xe = advance + stroke.getXe() * scaleX;
                    double ye = stroke.getYe() * scaleY;
                    if (any && Math.round(xs * pow) == lastX && Math.round(ys * pow) == lastY) {
                        lineTo(sb, xe, ye);
                    } else {
                        if (any) {
                            sb.append(' ');
                        }
                        sb.append("M ");
                        point(sb, xs, ys);
                        lineTo(sb, xe, ye);
                    }
                    lastX = Math.round(xe * pow);
                    lastY = Math.round(ye * pow);
                    any = true;
                }
            }
            advance += text.getXsize() * u;
        }
        if (!any) {
            sb.setLength(mark);
            return;
        }
        sb.append('"').append(palette.stroke(clear)).append(" stroke-width=\"");
        num(sb, strokeWidth);
        sb.append("\" fill=\"none\"");
        placement(sb, text.getX(), text.getY(), rotCw, mirror, 1.0);
        sb.append("/>\n");
    }

    private static final String CODE39_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%*";
    /** Bar, space, bar, ... widths of each Code 39 character: narrow or wide. */
    private static final String[] CODE39_WIDTHS = {
        "nnnwwnwnn", "wnnwnnnnw", "nnwwnnnnw", "wnwwnnnnn", "nnnwwnnnw", "wnnwwnnnn", "nnwwwnnnn",
        "nnnwnnwnw", "wnnwnnwnn", "nnwwnnwnn", "wnnnnwnnw", "nnwnnwnnw", "wnwnnwnnn", "nnnnwwnnw",
        "wnnnwwnnn", "nnwnwwnnn", "nnnnnwwnw", "wnnnnwwnn", "nnwnnwwnn", "nnnnwwwnn", "wnnnnnnww",
        "nnwnnnnww", "wnwnnnnwn", "nnnnwnnww", "wnnnwnnwn", "nnwnwnnwn", "nnnnnnwww", "wnnnnnwwn",
        "nnwnnnwwn", "nnnnwnwwn", "wwnnnnnnw", "nwwnnnnnw", "wwwnnnnnn", "nwnnwnnnw", "wwnnwnnnn",
        "nwwnwnnnn", "nwnnnnwnw", "wwnnnnwnn", "nwwnnnwnn", "nwnwnwnnn", "nwnwnnnwn", "nwnnnwnwn",
        "nnnwnwnwn", "nwnnwnwnn"
    };
    private static final int CODE39_WIDE = 3;

    /**
     * A barcode as Code 39 bars (the only symbology ODB++ defines): the text between a
     * start and a stop character, each bar a multiple of the element width, wide elements
     * three times the narrow ones.
     */
    private void paintBarcode(Barcode barcode, StringBuilder sb, Palette palette, boolean invert) {
        double element = barcode.getWidth() * u;
        double height = barcode.getHeight() * u;
        if (element <= 0 || height <= 0) {
            return;
        }
        boolean clear = (barcode.getPolarity() == Polarity.NEGATIVE) ^ invert;
        int type = barcode.getOrientDef();
        // The angle belongs to orient_def 8 and 9; a model built by hand may set it alone.
        double rotCw = type >= 8 || barcode.getOrientDefRotation() != 0
                ? barcode.getOrientDefRotation() : (type % 4) * 90.0;
        boolean mirror = (type >= 4 && type <= 7) || type == 9;
        String text = barcode.getText() == null ? "" : barcode.getText().toUpperCase(java.util.Locale.ROOT);

        sb.append("<g");
        placement(sb, barcode.getX(), barcode.getY(), rotCw, mirror, 1.0);
        sb.append('>');
        double x = 0;
        String framed = "*" + text + "*";
        for (int i = 0; i < framed.length(); i++) {
            int index = CODE39_CHARS.indexOf(framed.charAt(i));
            if (index < 0 || (framed.charAt(i) == '*' && i > 0 && i < framed.length() - 1)) {
                continue;
            }
            String widths = CODE39_WIDTHS[index];
            for (int k = 0; k < widths.length(); k++) {
                double w = (widths.charAt(k) == 'w' ? CODE39_WIDE : 1) * element;
                if (k % 2 == 0) {
                    sb.append("<rect x=\"");
                    num(sb, x);
                    sb.append("\" y=\"0\" width=\"");
                    num(sb, w);
                    sb.append("\" height=\"");
                    num(sb, height);
                    sb.append('"').append(palette.fill(clear)).append("/>");
                }
                x += w;
            }
            x += element;
        }
        sb.append("</g>\n");
    }

    // ------------------------------------------------------------------------------------------
    // Symbols
    // ------------------------------------------------------------------------------------------

    private static final class SymbolInfo {
        /** A standard symbol the painter draws itself. */
        OdbSymbolShape shape;
        /** A user-defined symbol. */
        Symbol user;
        /** Any other standard symbol: path data of its outline; empty when it draws nothing. */
        String outline;
        boolean outlineHasHoles;
        /** The box of the outline about the symbol's origin, in mm; null when it is empty. */
        double[] outlineBox;
        /** The box of a user-defined symbol, once measured; empty when it draws nothing. */
        double[] userBox;
        /** Diameter of the stroke this symbol draws a line or arc with, in mm. */
        double strokeMm;
    }

    private Map<Integer, SymbolInfo> symbolsOf(Features features) {
        return symbolCache.computeIfAbsent(features, f -> new HashMap<>());
    }

    private SymbolInfo info(Features features, Map<Integer, SymbolInfo> cache, int symbolNumber) {
        SymbolInfo info = cache.get(symbolNumber);
        if (info != null) {
            return info;
        }
        info = new SymbolInfo();
        String name = features.getSymbolName(symbolNumber);
        info.shape = OdbSymbolShape.parse(name, features.symbolUnitToMm(symbolNumber));
        if (info.shape != null) {
            info.strokeMm = info.shape.strokeDiameter();
        } else {
            info.user = findUserSymbol(name);
            info.strokeMm = FALLBACK_STROKE_MM;
            if (info.user == null) {
                Shape outline = StandardSymbolOutline.of(name, features.symbolUnitToMm(symbolNumber));
                if (outline != null) {
                    info.outline = pathData(outline);
                    info.outlineHasHoles = info.outline.indexOf('M', 1) > 0;
                    Rectangle2D box = outline.getBounds2D();
                    if (box.getWidth() > 0) {
                        info.strokeMm = box.getWidth();
                    }
                    if (!box.isEmpty()) {
                        info.outlineBox = new double[] {box.getMinX(), box.getMinY(),
                                box.getMaxX(), box.getMaxY()};
                    }
                }
            }
        }
        cache.put(symbolNumber, info);
        return info;
    }

    private Symbol findUserSymbol(String name) {
        if (name == null || userSymbols.isEmpty()) {
            return null;
        }
        Symbol direct = userSymbols.get(name);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, Symbol> e : userSymbols.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** Clockwise rotation of a pad: orient_def 0-7 in 90° steps, 8 and 9 a free angle. */
    public static double padRotationCw(Pad pad) {
        int type = pad.getOrientationType();
        if (type >= 8) {
            return pad.getCustomRotation() != null ? pad.getCustomRotation() : 0;
        }
        return (type % 4) * 90.0;
    }

    /** Whether a pad's orient_def mirrors it: 4-7 and 9. */
    public static boolean padMirrored(Pad pad) {
        int type = pad.getOrientationType();
        return (type >= 4 && type <= 7) || type == 9;
    }

    /** A text orient_def ("0".."7", "8 angle", "9 angle") as {clockwise degrees, mirrored}. */
    static double[] orientation(String orientDef) {
        if (orientDef == null || orientDef.isBlank()) {
            return new double[] {0, 0};
        }
        String[] parts = orientDef.trim().split("\\s+");
        try {
            int type = Integer.parseInt(parts[0]);
            if (type >= 8) {
                double angle = parts.length > 1 ? Double.parseDouble(parts[1]) : 0;
                return new double[] {angle, type == 9 ? 1 : 0};
            }
            return new double[] {(type % 4) * 90.0, type >= 4 ? 1 : 0};
        } catch (NumberFormatException e) {
            return new double[] {0, 0};
        }
    }

    // ------------------------------------------------------------------------------------------
    // SVG primitives
    // ------------------------------------------------------------------------------------------

    /** Placement of a local frame: rotated clockwise, then mirrored along x, then moved. */
    private void placement(StringBuilder sb, double xMm, double yMm, double rotCw,
                           boolean mirror, double scale) {
        sb.append(" transform=\"translate(");
        num(sb, xMm * u);
        sb.append(' ');
        num(sb, yMm * u);
        sb.append(')');
        if (mirror) {
            sb.append(" scale(-1 1)");
        }
        // In the y-up user space rotate(a) turns counter-clockwise.
        if (normalize(rotCw) != 0) {
            sb.append(" rotate(").append(angle(-rotCw)).append(')');
        }
        if (scale != 1.0) {
            sb.append(" scale(").append(angle(scale)).append(')');
        }
        sb.append('"');
    }

    /** Placement of a shape drawn around the origin, turned clockwise by the angle. */
    private void turned(StringBuilder sb, double cx, double cy, double angleCw) {
        sb.append(" transform=\"translate(");
        num(sb, cx);
        sb.append(' ');
        num(sb, cy);
        sb.append(") rotate(").append(angle(-angleCw)).append(")\"");
    }

    /** A full circle as two half arcs. */
    private void circlePath(StringBuilder sb, double cx, double cy, double r) {
        sb.append("M ");
        point(sb, cx - r, cy);
        arc(sb, r, true, true, cx + r, cy);
        arc(sb, r, true, true, cx - r, cy);
        sb.append(" Z");
    }

    /** An arc from the current point (xs, ys); the flags follow from the swept angle. */
    private void arcTo(StringBuilder sb, double xs, double ys, double xe, double ye,
                       double xc, double yc, boolean clockwise, boolean full) {
        double r = (Math.hypot(xs - xc, ys - yc) + Math.hypot(xe - xc, ye - yc)) / 2;
        if (r * pow < 0.5) {
            lineTo(sb, xe, ye);
            return;
        }
        if (full) {
            arc(sb, r, true, !clockwise, 2 * xc - xs, 2 * yc - ys);
            arc(sb, r, true, !clockwise, xe, ye);
            return;
        }
        double sweep = Math.abs(sweep(xs, ys, xe, ye, xc, yc, clockwise));
        arc(sb, r, sweep > Math.PI, !clockwise, xe, ye);
    }

    private void arc(StringBuilder sb, double r, boolean large, boolean counterClockwise,
                     double x, double y) {
        sb.append(" A ");
        num(sb, r);
        sb.append(' ');
        num(sb, r);
        sb.append(large ? " 0 1 " : " 0 0 ").append(counterClockwise ? "1 " : "0 ");
        point(sb, x, y);
    }

    private void lineTo(StringBuilder sb, double x, double y) {
        sb.append(" L ");
        point(sb, x, y);
    }

    private void point(StringBuilder sb, double x, double y) {
        num(sb, x);
        sb.append(' ');
        num(sb, y);
    }

    /** Fixed-point number with trailing zeros dropped; never an exponent or a "-0". */
    private void num(StringBuilder sb, double value) {
        long scaled = Math.round(value * pow);
        if (scaled < 0) {
            sb.append('-');
            scaled = -scaled;
        }
        sb.append(scaled / pow);
        long fraction = scaled % pow;
        if (fraction != 0) {
            sb.append('.');
            long digit = pow / 10;
            while (fraction != 0) {
                sb.append((char) ('0' + fraction / digit));
                fraction %= digit;
                digit /= 10;
            }
        }
    }

    private static String angle(double value) {
        double rounded = Math.round(value * 1e6) / 1e6;
        if (rounded == Math.rint(rounded)) {
            return Long.toString((long) rounded);
        }
        return Double.toString(rounded);
    }

    private static double normalize(double degrees) {
        double a = degrees % 360;
        if (a < 0) {
            a += 360;
        }
        return a == 360 ? 0 : a;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}

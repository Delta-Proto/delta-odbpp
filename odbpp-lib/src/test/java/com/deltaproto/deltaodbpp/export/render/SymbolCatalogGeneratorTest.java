package com.deltaproto.deltaodbpp.export.render;

import com.deltaproto.deltaodbpp.model.symbol.StandardSymbol;
import com.deltaproto.deltaodbpp.parser.StandardSymbolParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generates a visual catalog of all symbol renderers for visual verification.
 * The output is an HTML file with all symbols rendered as inline SVGs.
 */
class SymbolCatalogGeneratorTest {

    // Output to root/generated folder for inclusion in git and GitHub preview
    private static final Path OUTPUT_DIR = Paths.get("..", "generated");

    @Test
    void generateSymbolCatalog() throws IOException {
        Files.createDirectories(OUTPUT_DIR);
        Path outputFile = OUTPUT_DIR.resolve("symbol-visual-test.html");

        StringBuilder html = new StringBuilder();
        html.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <title>ODB++ Symbol Visual Test</title>
                <style>
                    body { font-family: -apple-system, sans-serif; background: #1a1a2e; color: #eee; padding: 20px; }
                    h1 { color: #4fc3f7; }
                    h2 { color: #81d4fa; border-bottom: 1px solid #333; padding-bottom: 10px; margin-top: 40px; }
                    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 20px; }
                    .symbol-card { background: #16213e; border-radius: 8px; padding: 15px; text-align: center; }
                    .symbol-card svg { background: #0f0f23; border-radius: 4px; margin: 10px 0; }
                    .symbol-name { font-size: 14px; color: #4fc3f7; margin-top: 10px; }
                    .symbol-desc { font-size: 12px; color: #888; }
                    .odb-code { font-size: 11px; color: #666; font-family: monospace; margin-top: 8px; word-break: break-all; }
                </style>
            </head>
            <body>
                <h1>ODB++ Symbol Visual Test</h1>
                <p>Visual verification of all implemented symbol renderers.</p>
            """);

        // Basic Shapes
        html.append("<h2>Basic Shapes</h2><div class='grid'>");
        addSymbolCard(html, "Round (r50)", new RoundRenderer(0.050), "#FF0000");
        addSymbolCard(html, "Round (r100)", new RoundRenderer(0.100), "#FF0000");
        addSymbolCard(html, "Square (s40)", new SquareRenderer(0.040), "#00FF00");
        addSymbolCard(html, "Square (s80)", new SquareRenderer(0.080), "#00FF00");
        html.append("</div>");

        // Rectangles
        html.append("<h2>Rectangles</h2><div class='grid'>");
        addSymbolCard(html, "Rectangle (rect100x50)",
                new RectangleRenderer(0.100, 0.050), "#0000FF");
        addSymbolCard(html, "Rectangle (rect50x100)",
                new RectangleRenderer(0.050, 0.100), "#0000FF");
        addSymbolCard(html, "Rounded (rc100x50x10)",
                new RectangleRenderer(0.100, 0.050, RectangleRenderer.Variant.ROUNDED, 0.010), "#FF00FF");
        addSymbolCard(html, "Rounded (rc100x50x25)",
                new RectangleRenderer(0.100, 0.050, RectangleRenderer.Variant.ROUNDED, 0.025), "#FF00FF");
        addSymbolCard(html, "Chamfered (ch100x50x10)",
                new RectangleRenderer(0.100, 0.050, RectangleRenderer.Variant.CHAMFERED, 0.010), "#FFFF00");
        addSymbolCard(html, "Chamfered (ch100x50x20)",
                new RectangleRenderer(0.100, 0.050, RectangleRenderer.Variant.CHAMFERED, 0.020), "#FFFF00");
        html.append("</div>");

        // Ovals and Ellipses
        html.append("<h2>Ovals and Ellipses</h2><div class='grid'>");
        addSymbolCard(html, "Oval horizontal (oval80x40)",
                new OvalRenderer(0.080, 0.040), "#00FFFF");
        addSymbolCard(html, "Oval vertical (oval40x80)",
                new OvalRenderer(0.040, 0.080), "#00FFFF");
        addSymbolCard(html, "Oval (oval100x60)",
                new OvalRenderer(0.100, 0.060), "#00FFFF");
        addSymbolCard(html, "Ellipse (el80x50)",
                new OvalRenderer(0.080, 0.050, true), "#FF8800");
        addSymbolCard(html, "Ellipse (el100x40)",
                new OvalRenderer(0.100, 0.040, true), "#FF8800");
        html.append("</div>");

        // With Transforms
        html.append("<h2>Transforms</h2><div class='grid'>");
        addSymbolCardWithTransform(html, "Square rotated 45°",
                new SquareRenderer(0.060), 45, false, "#00FF00");
        addSymbolCardWithTransform(html, "Rectangle rotated 30°",
                new RectangleRenderer(0.100, 0.050), 30, false, "#0000FF");
        addSymbolCardWithTransform(html, "Square mirrored",
                new SquareRenderer(0.060), 0, true, "#00FF00");
        addSymbolCardWithTransform(html, "Rectangle rotated + mirrored",
                new RectangleRenderer(0.100, 0.050), 45, true, "#FF00FF");
        html.append("</div>");

        // Scaled
        html.append("<h2>Scaling</h2><div class='grid'>");
        addSymbolCardScaled(html, "Round 0.5x", new RoundRenderer(0.050), 0.5, "#FF0000");
        addSymbolCardScaled(html, "Round 1x", new RoundRenderer(0.050), 1.0, "#FF0000");
        addSymbolCardScaled(html, "Round 1.5x", new RoundRenderer(0.050), 1.5, "#FF0000");
        addSymbolCardScaled(html, "Round 2x", new RoundRenderer(0.050), 2.0, "#FF0000");
        html.append("</div>");

        // Parser Integration
        html.append("<h2>Parser Integration (from symbol names)</h2><div class='grid'>");
        StandardSymbolParser parser = new StandardSymbolParser();
        String[] symbolNames = {
            "r50", "s40", "rect100x50", "oval80x40", "rc100x50x10", "ch80x60x15",
            "di60x60", "oct80x80x20", "hex_l100x70x25", "hex_s70x100x25",
            "tri60x50", "ho100x50", "donut_r100x50", "donut_s80x50"
        };
        for (String name : symbolNames) {
            StandardSymbol sym = parser.parse(name);
            if (sym != null) {
                SymbolRenderer renderer = createRendererFromSymbol(sym);
                if (renderer != null) {
                    addSymbolCard(html, name, renderer, getColorForType(sym.getType()));
                }
            }
        }
        html.append("</div>");

        // Polygon Shapes
        html.append("<h2>Polygon Shapes</h2><div class='grid'>");
        addSymbolCard(html, "Diamond (di60x60)",
                PolygonRenderer.diamondFromMils(60, 60), "#FF6B6B");
        addSymbolCard(html, "Diamond (di80x50)",
                PolygonRenderer.diamondFromMils(80, 50), "#FF6B6B");
        addSymbolCard(html, "Octagon (oct80x80x15)",
                PolygonRenderer.octagonFromMils(80, 80, 15), "#4ECDC4");
        addSymbolCard(html, "Octagon (oct100x100x25)",
                PolygonRenderer.octagonFromMils(100, 100, 25), "#4ECDC4");
        addSymbolCard(html, "Hexagon H (hex_l100x70x25)",
                PolygonRenderer.hexagonHFromMils(100, 70, 25), "#45B7D1");
        addSymbolCard(html, "Hexagon V (hex_s70x100x25)",
                PolygonRenderer.hexagonVFromMils(70, 100, 25), "#45B7D1");
        addSymbolCard(html, "Triangle (tri60x50)",
                PolygonRenderer.triangleFromMils(60, 50), "#96CEB4");
        addSymbolCard(html, "Triangle (tri80x80)",
                PolygonRenderer.triangleFromMils(80, 80), "#96CEB4");
        addSymbolCard(html, "Half-Oval H (ho100x50)",
                PolygonRenderer.halfOvalFromMils(100, 50), "#FFEAA7");
        addSymbolCard(html, "Half-Oval V (ho50x100)",
                PolygonRenderer.halfOvalFromMils(50, 100), "#FFEAA7");
        html.append("</div>");

        // Donut Shapes
        html.append("<h2>Donut Shapes</h2><div class='grid'>");
        addSymbolCard(html, "Round Donut (donut_r100x50)",
                DonutRenderer.roundFromMils(100, 50), "#DDA0DD");
        addSymbolCard(html, "Round Donut (donut_r80x30)",
                DonutRenderer.roundFromMils(80, 30), "#DDA0DD");
        addSymbolCard(html, "Square Donut (donut_s100x60)",
                DonutRenderer.squareFromMils(100, 60), "#98D8C8");
        addSymbolCard(html, "Square Donut (donut_s80x40)",
                DonutRenderer.squareFromMils(80, 40), "#98D8C8");
        addSymbolCard(html, "Oval Donut (donut_o100x60x15)",
                DonutRenderer.ovalFromMils(100, 60, 15), "#F7DC6F");
        addSymbolCard(html, "Oval Donut (donut_o80x100x10)",
                DonutRenderer.ovalFromMils(80, 100, 10), "#F7DC6F");
        addSymbolCard(html, "Rect Donut (donut_rc100x80x15x10)",
                DonutRenderer.rectFromMils(100, 80, 15, 10), "#BB8FCE");
        html.append("</div>");

        // Thermal Shapes
        html.append("<h2>Thermal Shapes</h2><div class='grid'>");
        addSymbolCard(html, "Round Thermal (thr100x50x0x4x20)",
                ThermalRenderer.roundFromMils(100, 50, 0, 4, 20), "#F1948A");
        addSymbolCard(html, "Round Thermal (thr80x40x45x4x15)",
                ThermalRenderer.roundFromMils(80, 40, 45, 4, 15), "#F1948A");
        addSymbolCard(html, "Round Thermal 6-spoke",
                ThermalRenderer.roundFromMils(100, 40, 0, 6, 15), "#F1948A");
        addSymbolCard(html, "Square Thermal (ths100x50x0x4x20)",
                ThermalRenderer.squareFromMils(100, 50, 0, 4, 20), "#85C1E9");
        addSymbolCard(html, "Square Thermal (ths80x40x45x4x15)",
                ThermalRenderer.squareFromMils(80, 40, 45, 4, 15), "#85C1E9");
        html.append("</div>");

        // Polygon Transforms
        html.append("<h2>Polygon Transforms</h2><div class='grid'>");
        addSymbolCardWithTransform(html, "Diamond rotated 30°",
                PolygonRenderer.diamondFromMils(60, 60), 30, false, "#FF6B6B");
        addSymbolCardWithTransform(html, "Octagon rotated 22.5°",
                PolygonRenderer.octagonFromMils(80, 80, 20), 22.5, false, "#4ECDC4");
        addSymbolCardWithTransform(html, "Triangle rotated 180°",
                PolygonRenderer.triangleFromMils(60, 50), 180, false, "#96CEB4");
        addSymbolCardWithTransform(html, "Hexagon mirrored",
                PolygonRenderer.hexagonHFromMils(100, 70, 25), 0, true, "#45B7D1");
        html.append("</div>");

        // Home Plate Shapes
        html.append("<h2>Home Plate Shapes</h2><div class='grid'>");
        addSymbolCard(html, "Home Plate (hplate100x80x20)",
                HomePlateRenderer.homePlate(0.100, 0.080, 0.020), "#E74C3C");
        addSymbolCard(html, "Home Plate (hplate80x60x15)",
                HomePlateRenderer.homePlate(0.080, 0.060, 0.015), "#E74C3C");
        addSymbolCard(html, "Inverted Home Plate (rhplate100x80x20)",
                HomePlateRenderer.invertedHomePlate(0.100, 0.080, 0.020), "#9B59B6");
        addSymbolCard(html, "Inverted Home Plate (rhplate80x60x15)",
                HomePlateRenderer.invertedHomePlate(0.080, 0.060, 0.015), "#9B59B6");
        addSymbolCard(html, "Flat Home Plate (fhplate100x80x15x10)",
                HomePlateRenderer.flatHomePlate(0.100, 0.080, 0.015, 0.010), "#3498DB");
        addSymbolCard(html, "Flat Home Plate (fhplate80x60x10x8)",
                HomePlateRenderer.flatHomePlate(0.080, 0.060, 0.010, 0.008), "#3498DB");
        addSymbolCard(html, "D-Shape (dshape100x80x30)",
                HomePlateRenderer.dShape(0.100, 0.080, 0.030), "#1ABC9C");
        addSymbolCard(html, "D-Shape (dshape80x50x20)",
                HomePlateRenderer.dShape(0.080, 0.050, 0.020), "#1ABC9C");
        html.append("</div>");

        // Cross and Dogbone Shapes
        html.append("<h2>Cross and Dogbone Shapes</h2><div class='grid'>");
        addSymbolCard(html, "Cross Round (cross100x80x20x15x50x50xr)",
                CrossRenderer.cross(0.100, 0.080, 0.020, 0.015, 50, 50, true), "#E67E22");
        addSymbolCard(html, "Cross Square (cross100x80x20x15x50x50xs)",
                CrossRenderer.cross(0.100, 0.080, 0.020, 0.015, 50, 50, false), "#D35400");
        addSymbolCard(html, "Cross Off-center (cross100x80x25x20x30x40xr)",
                CrossRenderer.cross(0.100, 0.080, 0.025, 0.020, 30, 40, true), "#E67E22");
        addSymbolCard(html, "Dogbone Round (dogbone100x50x20x15x30xr)",
                CrossRenderer.dogbone(0.100, 0.050, 0.020, 0.015, 0.030, true), "#27AE60");
        addSymbolCard(html, "Dogbone Square (dogbone100x50x20x15x30xs)",
                CrossRenderer.dogbone(0.100, 0.050, 0.020, 0.015, 0.030, false), "#229954");
        addSymbolCard(html, "Dogbone Wide (dogbone120x40x15x10x50xr)",
                CrossRenderer.dogbone(0.120, 0.040, 0.015, 0.010, 0.050, true), "#27AE60");
        html.append("</div>");

        // Null and Special Symbols
        html.append("<h2>Special Symbols</h2><div class='grid'>");
        addSymbolCard(html, "Null Symbol (null0)",
                NullRenderer.create(0), "#95A5A6");
        addSymbolCard(html, "Null Symbol (null5)",
                NullRenderer.create(5), "#BDC3C7");
        addSymbolCard(html, "Butterfly Round (bfr50)",
                ButterflyRenderer.round(0.050), "#F39C12");
        addSymbolCard(html, "Butterfly Square (bfs50)",
                ButterflyRenderer.square(0.050), "#F1C40F");
        addSymbolCard(html, "Moire (moire10x5x3x2x50x45)",
                MoireRenderer.create(0.010, 0.005, 3, 0.002, 0.050, 45), "#8E44AD");
        addSymbolCard(html, "Hole Plated (hole50x1x0x0)",
                HoleRenderer.create(0.050, true, 0, 0), "#2ECC71");
        addSymbolCard(html, "Hole Non-plated (hole30x0x0x0)",
                HoleRenderer.create(0.030, false, 0, 0), "#E74C3C");
        html.append("</div>");

        // Surfaces (Contour Polygons)
        html.append("<h2>Surfaces (Contour Polygons)</h2>");
        html.append("<p style='color:#888; margin-bottom:20px;'>Surfaces are solid polygons that can contain non-intersecting islands with holes. " +
                "Islands are clockwise, holes are counter-clockwise.</p>");
        html.append("<div class='grid'>");

        // Simple rectangle surface
        addSymbolCard(html, "Rectangle Surface",
                SurfaceRenderer.rectangle(0.100, 0.060), "#3498DB");

        // Rectangle with rectangular hole
        addSymbolCard(html, "Rect with Hole",
                SurfaceRenderer.rectangleWithHole(0.100, 0.080, 0.060, 0.040), "#9B59B6");

        // Circle surface
        addSymbolCard(html, "Circle Surface",
                SurfaceRenderer.circle(0.040), "#E74C3C");

        // Donut (circle with hole)
        addSymbolCard(html, "Circle with Hole (Donut)",
                SurfaceRenderer.circleWithHole(0.050, 0.025), "#1ABC9C");

        // Nested example (Island A > Hole B > Island C)
        addSymbolCard(html, "Nested: A > B > C",
                SurfaceRenderer.nestedExample(0.055, 0.040, 0.020), "#E67E22");

        // L-shaped surface
        addSymbolCard(html, "L-Shape Surface",
                SurfaceRenderer.lShape(0.100, 0.080, 0.050, 0.040), "#27AE60");

        // Custom polygon (pentagon)
        addSymbolCard(html, "Pentagon Surface",
                SurfaceRenderer.polygon(
                    new double[]{0, 0.047, 0.029, -0.029, -0.047},
                    new double[]{0.050, 0.015, -0.040, -0.040, 0.015}), "#8E44AD");

        // Custom polygon (star-like)
        addSymbolCard(html, "Arrow Surface",
                SurfaceRenderer.polygon(
                    new double[]{0, 0.040, 0.020, 0.020, -0.020, -0.020, -0.040},
                    new double[]{0.050, 0, 0, -0.050, -0.050, 0, 0}), "#F39C12");

        // Multiple separate islands
        addSymbolCard(html, "Multiple Islands",
                SurfaceRenderer.multipleIslands(0.030, 0.015, 0.025, 0.010, 0.040), "#2980B9");

        html.append("</div>");

        // Surface Details section
        html.append("<h2>Surface Containment Order</h2>");
        html.append("<p style='color:#888; margin-bottom:20px;'>The natural containment order: Island A (outermost) → Hole B → Island C → etc. " +
                "Separate islands like D with Hole E are independent.</p>");
        html.append("<div class='grid'>");

        // Recreate the example from the documentation image
        // Island A with Hole B containing Island C, and separate Island D with Hole E
        addSymbolCardLarge(html, "Natural Order: A,B,C,D,E",
                createContainmentExample(), "#5D6D7E");

        html.append("</div>");

        html.append("""
                <p style="margin-top: 40px; color: #666; text-align: center;">
                    Generated by SymbolCatalogGeneratorTest - ODB++ symbol rendering library
                </p>
            </body>
            </html>
            """);

        Files.writeString(outputFile, html.toString());
        System.out.println("Symbol catalog generated: " + outputFile.toAbsolutePath());
    }

    private void addSymbolCard(StringBuilder html, String name, SymbolRenderer renderer, String color) {
        addSymbolCardFull(html, name, renderer, 0, false, 1.0, color, extractOdbCode(name));
    }

    private void addSymbolCardWithTransform(StringBuilder html, String name, SymbolRenderer renderer,
                                            double rotation, boolean mirror, String color) {
        String odbCode = extractOdbCode(name);
        if (rotation != 0) odbCode += " (rot " + (int)rotation + "°)";
        if (mirror) odbCode += " (mirror)";
        addSymbolCardFull(html, name, renderer, rotation, mirror, 1.0, color, odbCode);
    }

    private void addSymbolCardScaled(StringBuilder html, String name, SymbolRenderer renderer,
                                     double scale, String color) {
        String odbCode = extractOdbCode(name) + " (scale " + scale + "x)";
        addSymbolCardFull(html, name, renderer, 0, false, scale, color, odbCode);
    }

    private void addSymbolCardFull(StringBuilder html, String name, SymbolRenderer renderer,
                                   double rotation, boolean mirror, double scale, String color, String odbCode) {
        double size = 150;

        html.append("<div class='symbol-card'>");
        html.append("<svg width='").append(size).append("' height='").append(size).append("' viewBox='0 0 0.15 0.15'>");
        html.append("<rect width='0.15' height='0.15' fill='#0f0f23'/>");
        html.append(renderer.render(0.075, 0.075, rotation, mirror, scale, color));
        html.append("</svg>");
        html.append("<div class='symbol-name'>").append(name).append("</div>");
        html.append("<div class='symbol-desc'>").append(formatBounds(renderer)).append("</div>");
        if (odbCode != null && !odbCode.isEmpty()) {
            html.append("<div class='odb-code'>").append(odbCode).append("</div>");
        }
        html.append("</div>");
    }

    private String extractOdbCode(String name) {
        // Extract the ODB++ symbol code from the display name (e.g., "Round (r50)" -> "r50")
        int start = name.indexOf('(');
        int end = name.indexOf(')');
        if (start >= 0 && end > start) {
            return name.substring(start + 1, end);
        }
        // If no parentheses, check if the name itself looks like an ODB code
        if (name.matches("^[a-z]+\\d+.*") || name.contains("x")) {
            return name;
        }
        return "";
    }

    private String formatBounds(SymbolRenderer renderer) {
        return String.format("%.0f x %.0f mils",
                renderer.getWidth() * 1000, renderer.getHeight() * 1000);
    }

    private SymbolRenderer createRendererFromSymbol(StandardSymbol sym) {
        double w = sym.getWidth() / 1000.0; // mils to inches
        double h = sym.getHeight() / 1000.0;
        double p1 = sym.getParam1() / 1000.0;
        double p2 = sym.getParam2() / 1000.0;

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
            case OVAL_DONUT -> DonutRenderer.oval(w, h, p1);
            default -> null; // Not yet implemented
        };
    }

    private void addSymbolCardLarge(StringBuilder html, String name, SymbolRenderer renderer, String color) {
        double size = 300;

        html.append("<div class='symbol-card' style='grid-column: span 2;'>");
        html.append("<svg width='").append(size).append("' height='").append(size / 2).append("' viewBox='-0.15 -0.075 0.30 0.15'>");
        html.append("<rect x='-0.15' y='-0.075' width='0.30' height='0.15' fill='#0f0f23'/>");
        html.append(renderer.render(0, 0, 0, false, 1.0, color));
        html.append("</svg>");
        html.append("<div class='symbol-name'>").append(name).append("</div>");
        html.append("<div class='symbol-desc'>").append(formatBounds(renderer)).append("</div>");
        html.append("<div class='odb-code'>Surface containment example</div>");
        html.append("</div>");
    }

    /**
     * Creates the containment order example from the ODB++ spec:
     * Island A (outer) > Hole B > Island C (inner), and separate Island D > Hole E
     */
    private SymbolRenderer createContainmentExample() {
        return SurfaceRenderer.builder()
            // Island A (leftmost outer circle) - clockwise
            .addIsland(-0.050 + 0.055, 0, p -> p
                .addArc(-0.050 - 0.055, 0, -0.050, 0, true)
                .addArc(-0.050 + 0.055, 0, -0.050, 0, true))
            // Hole B inside A - counter-clockwise
            .addHole(-0.050 + 0.040, 0, p -> p
                .addArc(-0.050 - 0.040, 0, -0.050, 0, false)
                .addArc(-0.050 + 0.040, 0, -0.050, 0, false))
            // Island C inside B - clockwise
            .addIsland(-0.050 + 0.020, 0, p -> p
                .addArc(-0.050 - 0.020, 0, -0.050, 0, true)
                .addArc(-0.050 + 0.020, 0, -0.050, 0, true))
            // Island D (rightmost outer circle, separate from A) - clockwise
            .addIsland(0.070 + 0.050, 0, p -> p
                .addArc(0.070 - 0.050, 0, 0.070, 0, true)
                .addArc(0.070 + 0.050, 0, 0.070, 0, true))
            // Hole E inside D - counter-clockwise
            .addHole(0.070 + 0.030, 0, p -> p
                .addArc(0.070 - 0.030, 0, 0.070, 0, false)
                .addArc(0.070 + 0.030, 0, 0.070, 0, false))
            .build();
    }

    private String getColorForType(StandardSymbol.Type type) {
        return switch (type) {
            case ROUND -> "#FF0000";
            case SQUARE -> "#00FF00";
            case RECTANGLE -> "#0000FF";
            case ROUNDED_RECTANGLE -> "#FF00FF";
            case CHAMFERED_RECTANGLE -> "#FFFF00";
            case OVAL -> "#00FFFF";
            case ELLIPSE -> "#FF8800";
            case DIAMOND -> "#FF6B6B";
            case OCTAGON -> "#4ECDC4";
            case HEXAGON_L, HEXAGON_S -> "#45B7D1";
            case TRIANGLE -> "#96CEB4";
            case HALF_OVAL -> "#FFEAA7";
            case ROUND_DONUT -> "#DDA0DD";
            case SQUARE_DONUT -> "#98D8C8";
            case OVAL_DONUT -> "#F7DC6F";
            case ROUND_THERMAL, SQUARE_THERMAL -> "#F1948A";
            default -> "#FFFFFF";
        };
    }
}

package com.deltaproto.deltaodbpp.export.render;

import com.deltaproto.deltaodbpp.model.Features;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SymbolResolverTest {

    private SymbolResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SymbolResolver();
    }

    // SymbolResolver returns dimensions in mm now (the model is mm-normalised at
    // parse time). For an INCH-native archive (the default), symbol numbers are
    // mils → mils × 0.0254 mm. For example r50 = 50 mils = 1.27 mm.

    @Test
    void resolveRoundSymbol() {
        SymbolRenderer renderer = resolver.resolve("r50");

        assertNotNull(renderer);
        assertInstanceOf(RoundRenderer.class, renderer);
        assertEquals(1.270, renderer.getWidth(), 0.0001); // 50 mils → mm
    }

    @Test
    void resolveSquareSymbol() {
        SymbolRenderer renderer = resolver.resolve("s40");

        assertNotNull(renderer);
        assertInstanceOf(SquareRenderer.class, renderer);
        assertEquals(1.016, renderer.getWidth(), 0.0001); // 40 mils → mm
    }

    @Test
    void resolveRectangleSymbol() {
        SymbolRenderer renderer = resolver.resolve("rect100x50");

        assertNotNull(renderer);
        assertInstanceOf(RectangleRenderer.class, renderer);
        assertEquals(2.540, renderer.getWidth(), 0.0001);  // 100 mils → mm
        assertEquals(1.270, renderer.getHeight(), 0.0001); // 50 mils → mm
    }

    @Test
    void resolveRoundedRectangleSymbol() {
        SymbolRenderer renderer = resolver.resolve("rc100x50x10");

        assertNotNull(renderer);
        assertInstanceOf(RectangleRenderer.class, renderer);
        assertEquals(2.540, renderer.getWidth(), 0.0001);
        assertEquals(1.270, renderer.getHeight(), 0.0001);
    }

    @Test
    void resolveOvalSymbol() {
        SymbolRenderer renderer = resolver.resolve("oval80x40");

        assertNotNull(renderer);
        assertInstanceOf(OvalRenderer.class, renderer);
        assertEquals(2.032, renderer.getWidth(), 0.0001);  // 80 mils → mm
        assertEquals(1.016, renderer.getHeight(), 0.0001); // 40 mils → mm
    }

    @Test
    void resolveDiamondSymbol() {
        SymbolRenderer renderer = resolver.resolve("di60x60");

        assertNotNull(renderer);
        assertInstanceOf(PolygonRenderer.class, renderer);
    }

    @Test
    void resolveOctagonSymbol() {
        SymbolRenderer renderer = resolver.resolve("oct80x80x20");

        assertNotNull(renderer);
        assertInstanceOf(PolygonRenderer.class, renderer);
    }

    @Test
    void resolveRoundDonutSymbol() {
        SymbolRenderer renderer = resolver.resolve("donut_r100x50");

        assertNotNull(renderer);
        assertInstanceOf(DonutRenderer.class, renderer);
    }

    @Test
    void resolveUnknownSymbolReturnsDefault() {
        SymbolRenderer renderer = resolver.resolve("unknown_symbol");

        assertNotNull(renderer);
        assertInstanceOf(RoundRenderer.class, renderer);
    }

    @Test
    void resolveNullReturnsDefault() {
        SymbolRenderer renderer = resolver.resolve((String) null);

        assertNotNull(renderer);
        assertInstanceOf(RoundRenderer.class, renderer);
    }

    @Test
    void resolveEmptyStringReturnsDefault() {
        SymbolRenderer renderer = resolver.resolve("");

        assertNotNull(renderer);
        assertInstanceOf(RoundRenderer.class, renderer);
    }

    @Test
    void resolveWithFeatures() {
        Features features = new Features();
        features.getSymbolTable().put(0, "r50");
        features.getSymbolTable().put(1, "s40");
        features.getSymbolTable().put(2, "rect100x50");

        SymbolRenderer r0 = resolver.resolve(0, features);
        SymbolRenderer r1 = resolver.resolve(1, features);
        SymbolRenderer r2 = resolver.resolve(2, features);

        assertInstanceOf(RoundRenderer.class, r0);
        assertInstanceOf(SquareRenderer.class, r1);
        assertInstanceOf(RectangleRenderer.class, r2);
    }

    @Test
    void resolveWithFeaturesUnknownNumber() {
        Features features = new Features();
        features.getSymbolTable().put(0, "r50");

        SymbolRenderer renderer = resolver.resolve(99, features);

        assertNotNull(renderer);
        assertInstanceOf(RoundRenderer.class, renderer);
    }

    @Test
    void cacheWorks() {
        resolver.resolve("r50");
        resolver.resolve("s40");
        resolver.resolve("r50"); // Should hit cache

        assertEquals(2, resolver.getCacheSize());
    }

    @Test
    void clearCache() {
        resolver.resolve("r50");
        resolver.resolve("s40");

        assertEquals(2, resolver.getCacheSize());

        resolver.clearCache();

        assertEquals(0, resolver.getCacheSize());
    }

    @Test
    void renderOutput() {
        SymbolRenderer renderer = resolver.resolve("r50");
        String svg = renderer.render(0, 0, 0, false, 1.0, "#FF0000");

        assertNotNull(svg);
        assertTrue(svg.contains("circle"));
        assertTrue(svg.contains("#FF0000"));
    }
}

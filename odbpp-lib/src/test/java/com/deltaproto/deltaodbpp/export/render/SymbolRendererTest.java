package com.deltaproto.deltaodbpp.export.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SymbolRendererTest {

    // RoundRenderer tests

    @Test
    void testRoundRenderer() {
        RoundRenderer r = new RoundRenderer(0.050); // 50 mils = 0.050 inches
        String svg = r.render(1.0, 1.0, 0, false, 1.0, "#FF0000");

        assertTrue(svg.contains("<circle"), "Expected circle element in: " + svg);
        assertTrue(svg.contains("cx="), "Expected cx in: " + svg);
        assertTrue(svg.contains("cy="), "Expected cy in: " + svg);
        assertTrue(svg.contains("r="), "Expected r in: " + svg);
        assertTrue(svg.contains("fill=\"#FF0000\""), "Expected fill in: " + svg);
    }

    @Test
    void testRoundRendererFromMils() {
        RoundRenderer r = RoundRenderer.fromMils(50);
        assertEquals(0.050, r.getDiameter(), 0.0001);
        assertEquals(0.050, r.getWidth(), 0.0001);
        assertEquals(0.050, r.getHeight(), 0.0001);
    }

    @Test
    void testRoundRendererWithScale() {
        RoundRenderer r = new RoundRenderer(0.050);
        String svg = r.render(1.0, 1.0, 0, false, 2.0, "#000");

        // Scaled radius should be 0.050 * 2 / 2 = 0.050
        assertTrue(svg.contains("<circle"), "Expected circle in: " + svg);
        assertTrue(svg.contains("r="), "Expected radius in: " + svg);
    }

    // SquareRenderer tests

    @Test
    void testSquareRenderer() {
        SquareRenderer r = new SquareRenderer(0.040);
        String svg = r.render(1.0, 1.0, 0, false, 1.0, "#00FF00");

        assertTrue(svg.contains("<rect"), "Expected rect in: " + svg);
        assertTrue(svg.contains("width="), "Expected width in: " + svg);
        assertTrue(svg.contains("height="), "Expected height in: " + svg);
        assertTrue(svg.contains("fill=\"#00FF00\""), "Expected fill in: " + svg);
    }

    @Test
    void testSquareRendererFromMils() {
        SquareRenderer r = SquareRenderer.fromMils(40);
        assertEquals(0.040, r.getSize(), 0.0001);
    }

    @Test
    void testSquareRendererWithRotation() {
        SquareRenderer r = new SquareRenderer(0.040);
        String svg = r.render(1.0, 1.0, 45, false, 1.0, "#000");

        assertTrue(svg.contains("transform="), "Expected transform in: " + svg);
        assertTrue(svg.contains("rotate(45"), "Expected rotate in: " + svg);
    }

    // RectangleRenderer tests

    @Test
    void testRectangleRendererSimple() {
        RectangleRenderer r = new RectangleRenderer(0.100, 0.050);
        String svg = r.render(1.0, 1.0, 0, false, 1.0, "#0000FF");

        assertTrue(svg.contains("<rect"), "Expected rect in: " + svg);
        assertTrue(svg.contains("width="), "Expected width in: " + svg);
        assertTrue(svg.contains("height="), "Expected height in: " + svg);
        assertTrue(svg.contains("fill=\"#0000FF\""), "Expected fill in: " + svg);
    }

    @Test
    void testRectangleRendererRounded() {
        RectangleRenderer r = new RectangleRenderer(0.100, 0.050,
                RectangleRenderer.Variant.ROUNDED, 0.010);
        String svg = r.render(1.0, 1.0, 0, false, 1.0, "#000");

        assertTrue(svg.contains("<rect"), "Expected rect in: " + svg);
        assertTrue(svg.contains("rx="), "Expected rx in: " + svg);
        assertTrue(svg.contains("ry="), "Expected ry in: " + svg);
    }

    @Test
    void testRectangleRendererChamfered() {
        RectangleRenderer r = new RectangleRenderer(0.100, 0.050,
                RectangleRenderer.Variant.CHAMFERED, 0.010);
        String svg = r.render(1.0, 1.0, 0, false, 1.0, "#000");

        // Chamfered uses path
        assertTrue(svg.contains("<path"), "Expected path in: " + svg);
        assertTrue(svg.contains("d=\"M"), "Expected path data in: " + svg);
    }

    @Test
    void testRectangleRendererFromMils() {
        RectangleRenderer r = RectangleRenderer.fromMils(100, 50);
        assertEquals(0.100, r.getWidth(), 0.0001);
        assertEquals(0.050, r.getHeight(), 0.0001);
    }

    // OvalRenderer tests

    @Test
    void testOvalRendererHorizontal() {
        // Horizontal oval (width > height)
        OvalRenderer r = new OvalRenderer(0.080, 0.040);
        String svg = r.render(1.0, 1.0, 0, false, 1.0, "#FFFF00");

        assertTrue(svg.contains("<path"), "Expected path in: " + svg);
        assertTrue(svg.contains("fill=\"#FFFF00\""), "Expected fill in: " + svg);
        // Should contain arc commands for semicircular ends
        assertTrue(svg.contains(" A "), "Expected arc in: " + svg);
    }

    @Test
    void testOvalRendererVertical() {
        // Vertical oval (height > width)
        OvalRenderer r = new OvalRenderer(0.040, 0.080);
        String svg = r.render(1.0, 1.0, 0, false, 1.0, "#000");

        assertTrue(svg.contains("<path"), "Expected path in: " + svg);
        assertTrue(svg.contains(" A "), "Expected arc in: " + svg);
    }

    @Test
    void testOvalRendererSquare() {
        // When width == height, should render as circle
        OvalRenderer r = new OvalRenderer(0.050, 0.050);
        String svg = r.render(1.0, 1.0, 0, false, 1.0, "#000");

        assertTrue(svg.contains("<circle"), "Expected circle when w==h in: " + svg);
    }

    @Test
    void testEllipseRenderer() {
        OvalRenderer r = new OvalRenderer(0.080, 0.050, true); // ellipse
        String svg = r.render(1.0, 1.0, 0, false, 1.0, "#000");

        assertTrue(svg.contains("<ellipse"), "Expected ellipse in: " + svg);
        assertTrue(svg.contains("rx="), "Expected rx in: " + svg);
        assertTrue(svg.contains("ry="), "Expected ry in: " + svg);
    }

    @Test
    void testEllipseFromMils() {
        OvalRenderer r = OvalRenderer.ellipseFromMils(80, 50);
        assertTrue(r.isEllipse());
        assertEquals(0.080, r.getWidth(), 0.0001);
        assertEquals(0.050, r.getHeight(), 0.0001);
    }

    // Transform tests

    @Test
    void testMirrorTransform() {
        SquareRenderer r = new SquareRenderer(0.040);
        String svg = r.render(1.0, 1.0, 0, true, 1.0, "#000");

        assertTrue(svg.contains("transform="), "Expected transform in: " + svg);
        assertTrue(svg.contains("scale(-1,1)"), "Expected mirror scale in: " + svg);
    }

    @Test
    void testCombinedTransform() {
        SquareRenderer r = new SquareRenderer(0.040);
        String svg = r.render(1.0, 1.0, 45, true, 1.0, "#000");

        assertTrue(svg.contains("transform="), "Expected transform in: " + svg);
        assertTrue(svg.contains("scale(-1,1)"), "Expected mirror scale in: " + svg);
        assertTrue(svg.contains("rotate(45"), "Expected rotation in: " + svg);
    }

    // Bounds tests

    @Test
    void testRendererBounds() {
        RoundRenderer round = new RoundRenderer(0.050);
        assertEquals(0.050, round.getBounds().width(), 0.0001);
        assertEquals(0.050, round.getBounds().height(), 0.0001);

        RectangleRenderer rect = new RectangleRenderer(0.100, 0.050);
        assertEquals(0.100, rect.getBounds().width(), 0.0001);
        assertEquals(0.050, rect.getBounds().height(), 0.0001);
    }

    // Visual verification test - prints SVG for visual inspection
    @Test
    void testPrintSampleSvg() {
        System.out.println("=== Sample SVG Output for Visual Verification ===");

        RoundRenderer round = RoundRenderer.fromMils(50);
        System.out.println("Round r50: " + round.render(0, 0, 0, false, 1.0, "#FF0000"));

        SquareRenderer square = SquareRenderer.fromMils(40);
        System.out.println("Square s40: " + square.render(0, 0, 0, false, 1.0, "#00FF00"));

        RectangleRenderer rect = RectangleRenderer.fromMils(100, 50);
        System.out.println("Rect 100x50: " + rect.render(0, 0, 0, false, 1.0, "#0000FF"));

        RectangleRenderer rounded = RectangleRenderer.roundedFromMils(100, 50, 10);
        System.out.println("Rounded rect: " + rounded.render(0, 0, 0, false, 1.0, "#FF00FF"));

        OvalRenderer oval = OvalRenderer.fromMils(80, 40);
        System.out.println("Oval 80x40: " + oval.render(0, 0, 0, false, 1.0, "#FFFF00"));

        OvalRenderer ellipse = OvalRenderer.ellipseFromMils(80, 50);
        System.out.println("Ellipse 80x50: " + ellipse.render(0, 0, 0, false, 1.0, "#00FFFF"));
    }
}

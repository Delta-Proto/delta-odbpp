package com.deltaproto.deltaodbpp.export.render;

import com.deltaproto.deltaodbpp.export.MultiLayerSvgRenderer;
import com.deltaproto.deltaodbpp.export.render.FeaturePainter.Palette;
import com.deltaproto.deltaodbpp.model.Arc;
import com.deltaproto.deltaodbpp.model.Features;
import com.deltaproto.deltaodbpp.model.Line;
import com.deltaproto.deltaodbpp.model.Pad;
import com.deltaproto.deltaodbpp.model.Polarity;
import com.deltaproto.deltaodbpp.model.Symbol;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the painter draws, checked on the picture rather than on the markup: each test paints
 * a few features white on black into a 20 x 20 mm window and probes points of it. Coordinates
 * are millimetres with Y up, as in the model.
 */
class FeaturePainterTest {

    private static final int PX_PER_MM = 20;
    private static final int WINDOW_MM = 20;

    private static FeaturePainter painter() {
        return new FeaturePainter(1.0, 0.5, null, null);
    }

    /** A metric features file with the given symbol table. */
    private static Features features(String... symbols) {
        Features features = new Features();
        features.setUnits("MM");
        for (int i = 0; i < symbols.length; i++) {
            features.getSymbolTable().put(i, symbols[i]);
        }
        return features;
    }

    private static Pad pad(double x, double y, int symbol) {
        Pad pad = new Pad();
        pad.setX(x);
        pad.setY(y);
        pad.setSymbolNumber(symbol);
        pad.setPolarity("P");
        return pad;
    }

    private static Pad turned(Pad pad, int orientationType, Double angle) {
        pad.setOrientationType(orientationType);
        pad.setCustomRotation(angle);
        return pad;
    }

    private static Line line(double xs, double ys, double xe, double ye, int symbol) {
        Line line = new Line();
        line.setXs(xs);
        line.setYs(ys);
        line.setXe(xe);
        line.setYe(ye);
        line.setSymbolNumber(symbol);
        line.setPolarity(Polarity.POSITIVE);
        return line;
    }

    private static Arc arc(double xs, double ys, double xe, double ye, double xc, double yc,
                           boolean clockwise, int symbol) {
        Arc arc = new Arc();
        arc.setXs(xs);
        arc.setYs(ys);
        arc.setXe(xe);
        arc.setYe(ye);
        arc.setXc(xc);
        arc.setYc(yc);
        arc.setCw(clockwise ? "Y" : "N");
        arc.setSymbolNumber(symbol);
        arc.setPolarity(Polarity.POSITIVE);
        return arc;
    }

    private static BufferedImage paint(FeaturePainter painter, Features features) throws IOException {
        StringBuilder svg = new StringBuilder();
        int px = WINDOW_MM * PX_PER_MM;
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(px)
                .append("\" height=\"").append(px)
                .append("\" viewBox=\"0 0 ").append(WINDOW_MM).append(' ').append(WINDOW_MM).append("\">\n")
                .append("<rect width=\"").append(WINDOW_MM).append("\" height=\"").append(WINDOW_MM)
                .append("\" fill=\"#000000\"/>\n")
                .append("<g transform=\"scale(1,-1) translate(0,-").append(WINDOW_MM).append(")\">\n");
        painter.paint(features, svg, Palette.of("#ffffff", "#000000"));
        svg.append("</g>\n</svg>\n");
        byte[] png = MultiLayerSvgRenderer.rasterizeSvgToPng(svg.toString(), px, px);
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    private static BufferedImage paint(Features features) throws IOException {
        return paint(painter(), features);
    }

    private static boolean ink(BufferedImage image, double x, double y) {
        int px = (int) Math.floor(x * PX_PER_MM);
        int py = (int) Math.floor((WINDOW_MM - y) * PX_PER_MM);
        return (image.getRGB(px, py) & 0xff) > 127;
    }

    @Test
    void orientationsAreClockwise() throws IOException {
        Features features = features("rect6000x2000");
        features.getFeatures().add(turned(pad(10, 10, 0), 8, 30.0));
        BufferedImage image = paint(features);

        // The right end of the bar swings down, the left end up.
        assertTrue(ink(image, 10 + 2.165, 10 - 1.25));
        assertTrue(ink(image, 10 - 2.165, 10 + 1.25));
        assertFalse(ink(image, 10 + 2.165, 10 + 1.25));
        assertFalse(ink(image, 10 - 2.165, 10 - 1.25));
    }

    @Test
    void quarterTurnsAreClockwiseToo() throws IOException {
        // A half oval is flat on its left; a quarter turn clockwise puts the flat side on top.
        Features features = features("oval_h6000x4000");
        features.getFeatures().add(turned(pad(10, 10, 0), 1, null));
        BufferedImage image = paint(features);

        assertTrue(ink(image, 10 + 1.8, 10 + 2.8), "square corner at the top");
        assertFalse(ink(image, 10 + 1.8, 10 - 2.8), "round at the bottom");
    }

    @Test
    void mirroringFlipsAlongX() throws IOException {
        Features features = features("oval_h6000x4000");
        features.getFeatures().add(turned(pad(10, 10, 0), 4, null));
        BufferedImage image = paint(features);

        assertTrue(ink(image, 10 + 2.8, 10 + 1.8), "the flat side is now on the right");
        assertFalse(ink(image, 10 - 2.8, 10 + 1.8));
    }

    @Test
    void mirroringComesAfterTheTurn() throws IOException {
        Features features = features("rect6000x2000");
        features.getFeatures().add(turned(pad(10, 10, 0), 9, 30.0));
        BufferedImage image = paint(features);

        assertTrue(ink(image, 10 - 2.165, 10 - 1.25));
        assertTrue(ink(image, 10 + 2.165, 10 + 1.25));
        assertFalse(ink(image, 10 + 2.165, 10 - 1.25));
    }

    @Test
    void padOrientationTypes() {
        assertEquals(270, FeaturePainter.padRotationCw(turned(pad(0, 0, 0), 3, null)));
        assertEquals(90, FeaturePainter.padRotationCw(turned(pad(0, 0, 0), 5, null)));
        assertEquals(12.5, FeaturePainter.padRotationCw(turned(pad(0, 0, 0), 9, 12.5)));
        assertFalse(FeaturePainter.padMirrored(turned(pad(0, 0, 0), 3, null)));
        assertTrue(FeaturePainter.padMirrored(turned(pad(0, 0, 0), 5, null)));
        assertFalse(FeaturePainter.padMirrored(turned(pad(0, 0, 0), 8, 45.0)));
        assertTrue(FeaturePainter.padMirrored(turned(pad(0, 0, 0), 9, 45.0)));
    }

    @Test
    void textOrientations() {
        assertArrayEquals(new double[] {180, 0}, FeaturePainter.orientation("2"));
        assertArrayEquals(new double[] {90, 1}, FeaturePainter.orientation("5"));
        assertArrayEquals(new double[] {33.5, 0}, FeaturePainter.orientation("8 33.5"));
        assertArrayEquals(new double[] {33.5, 1}, FeaturePainter.orientation("9 33.5"));
        assertArrayEquals(new double[] {0, 0}, FeaturePainter.orientation(null));
        assertArrayEquals(new double[] {0, 0}, FeaturePainter.orientation("north"));
    }

    @Test
    void symbolUnitFlagOverridesTheUnitOfTheFile() throws IOException {
        // r100 is 0.1 mm in microns and 2.54 mm in mils; the file is metric, symbol 1 is not.
        Features features = features("r100", "r100");
        features.getSymbolMetricFlags().put(1, false);
        features.getFeatures().add(pad(5, 10, 0));
        features.getFeatures().add(pad(15, 10, 1));
        BufferedImage image = paint(features);

        assertFalse(ink(image, 5 + 1.0, 10), "metric symbol: a 0.1 mm dot");
        assertTrue(ink(image, 15 + 1.0, 10), "imperial symbol: 2.54 mm across");
        assertFalse(ink(image, 15 + 1.5, 10));
    }

    @Test
    void resizedPadScalesItsSymbol() throws IOException {
        Features features = features("r1000");
        Pad pad = pad(10, 10, 0);
        pad.setResizeFactor(4.0);
        features.getFeatures().add(pad);
        BufferedImage image = paint(features);

        assertTrue(ink(image, 10 + 1.8, 10));
        assertFalse(ink(image, 10 + 2.2, 10));
    }

    @Test
    void standardSymbolWithoutAnApertureIsDrawnFromItsOutline() throws IOException {
        Features features = features("donut_rc6000x4000x500");
        features.getFeatures().add(pad(10, 10, 0));
        BufferedImage image = paint(features);

        assertTrue(ink(image, 10 + 2.75, 10), "right bar");
        assertTrue(ink(image, 10, 10 - 1.75), "bottom bar");
        assertFalse(ink(image, 10, 10), "open in the middle");
        assertFalse(ink(image, 10 + 2.2, 10 + 1.2), "inside the ring");
    }

    @Test
    void outlineSymbolTurnsWithItsPad() throws IOException {
        Features features = features("tri4000x4000");
        features.getFeatures().add(turned(pad(10, 10, 0), 2, null));
        BufferedImage image = paint(features);

        // Upside down: the base is at the top.
        assertTrue(ink(image, 10 + 1.5, 10 + 1.8));
        assertFalse(ink(image, 10 + 1.5, 10 - 1.8));
    }

    @Test
    void unknownSymbolIsMarkedWithADot() throws IOException {
        Features features = features("no_such_symbol");
        features.getFeatures().add(pad(10, 10, 0));

        BufferedImage image = paint(new FeaturePainter(1.0, 2.0, null, null), features);
        assertTrue(ink(image, 10 + 0.8, 10));
        assertFalse(ink(image, 10 + 1.2, 10));

        BufferedImage none = paint(new FeaturePainter(1.0, 0, null, null), features);
        assertFalse(ink(none, 10, 10), "no dot when the default pad size is zero");
    }

    @Test
    void userSymbolIsPlacedTurnedAndMirrored() throws IOException {
        // The symbol: a 2 x 1 mm bar whose centre lies 3 mm right of the symbol origin.
        Features inner = features("rect2000x1000");
        inner.getFeatures().add(pad(3, 0, 0));
        Symbol symbol = new Symbol();
        symbol.setName("offset_bar");
        symbol.setFeatures(inner);
        FeaturePainter painter = new FeaturePainter(1.0, 0.5, Map.of("offset_bar", symbol), null);

        Features features = features("offset_bar");
        features.getFeatures().add(pad(10, 10, 0));
        BufferedImage plain = paint(painter, features);
        assertTrue(ink(plain, 13, 10));
        assertFalse(ink(plain, 10, 10), "nothing at the symbol origin");

        features.getFeatures().set(0, turned(pad(10, 10, 0), 1, null));
        BufferedImage quarter = paint(painter, features);
        assertTrue(ink(quarter, 10, 7), "a quarter turn clockwise swings it below the origin");
        assertTrue(ink(quarter, 10 + 0.4, 7 - 0.9), "and stands the bar upright");
        assertFalse(ink(quarter, 13, 10));

        features.getFeatures().set(0, turned(pad(10, 10, 0), 4, null));
        BufferedImage mirrored = paint(painter, features);
        assertTrue(ink(mirrored, 7, 10));
        assertFalse(ink(mirrored, 13, 10));
    }

    @Test
    void negativePadOfAUserSymbolSwapsItsPolarities() throws IOException {
        // A 4 mm disc with a 2 mm clear hole.
        Features inner = features("r4000", "r2000");
        inner.getFeatures().add(pad(0, 0, 0));
        Pad hole = pad(0, 0, 1);
        hole.setPolarity("N");
        inner.getFeatures().add(hole);
        Symbol symbol = new Symbol();
        symbol.setName("washer");
        symbol.setFeatures(inner);
        FeaturePainter painter = new FeaturePainter(1.0, 0.5, Map.of("washer", symbol), null);

        Features features = features("r8000", "washer");
        features.getFeatures().add(pad(10, 10, 1));
        assertTrue(painter.hasClear(features), "the hole inside the symbol is clear paint");
        BufferedImage positive = paint(painter, features);
        assertTrue(ink(positive, 10 + 1.5, 10));
        assertFalse(ink(positive, 10, 10));

        // Placed negative on a larger disc it clears the washer and leaves its hole dark.
        features.getFeatures().clear();
        features.getFeatures().add(pad(10, 10, 0));
        Pad negative = pad(10, 10, 1);
        negative.setPolarity("N");
        features.getFeatures().add(negative);
        BufferedImage inverted = paint(painter, features);
        assertTrue(ink(inverted, 10 + 3, 10), "the disc underneath");
        assertFalse(ink(inverted, 10 + 1.5, 10), "cleared by the washer");
        assertTrue(ink(inverted, 10, 10), "restored by the washer's hole");
    }

    @Test
    void layerWithoutNegativeFeaturesHasNoClearPaint() {
        Features features = features("r1000");
        features.getFeatures().add(pad(10, 10, 0));
        features.getFeatures().add(line(1, 1, 2, 2, 0));
        assertFalse(painter().hasClear(features));

        Line negative = line(1, 1, 2, 2, 0);
        negative.setPolarity(Polarity.NEGATIVE);
        features.getFeatures().add(negative);
        assertTrue(painter().hasClear(features));
    }

    @Test
    void fullCircleArcIsAWholeRing() throws IOException {
        Features features = features("r400");
        features.getFeatures().add(arc(13, 10, 13, 10, 10, 10, false, 0));
        BufferedImage image = paint(features);

        assertTrue(ink(image, 13, 10));
        assertTrue(ink(image, 10, 13));
        assertTrue(ink(image, 7, 10));
        assertTrue(ink(image, 10, 7));
        assertFalse(ink(image, 10, 10));
    }

    @Test
    void arcsSweepTheSideTheirDirectionSays() throws IOException {
        double d = 3 / Math.sqrt(2);

        // From east to south, counter-clockwise: three quarters of the circle, through north.
        Features wide = features("r400");
        wide.getFeatures().add(arc(13, 10, 10, 7, 10, 10, false, 0));
        BufferedImage ccw = paint(wide);
        assertTrue(ink(ccw, 10, 13));
        assertTrue(ink(ccw, 7, 10));
        assertTrue(ink(ccw, 10 - d, 10 - d));
        assertFalse(ink(ccw, 10 + d, 10 - d), "the quarter between south and east stays open");

        // The same end points clockwise: only that quarter.
        Features narrow = features("r400");
        narrow.getFeatures().add(arc(13, 10, 10, 7, 10, 10, true, 0));
        BufferedImage cw = paint(narrow);
        assertTrue(ink(cw, 10 + d, 10 - d));
        assertFalse(ink(cw, 10, 13));
        assertFalse(ink(cw, 7, 10));
    }

    @Test
    void squareSymbolDrawsALineWithSquareEnds() throws IOException {
        Features features = features("s2000", "r2000");
        features.getFeatures().add(line(5, 15, 10, 15, 0));
        features.getFeatures().add(line(5, 5, 10, 5, 1));
        BufferedImage image = paint(features);

        assertTrue(ink(image, 5 - 0.85, 15 + 0.85), "the corner of the square end");
        assertFalse(ink(image, 5 - 0.85, 5 + 0.85), "a round end has none");
        assertTrue(ink(image, 5 - 0.85, 5));
    }

    @Test
    void zeroWidthStrokesAreHairlinesOnRequest() throws IOException {
        Features features = features("r0", "r1000");
        features.getFeatures().add(line(2, 10, 18, 10, 0));
        features.getFeatures().add(line(2, 5, 18, 5, 1));

        BufferedImage trueWidth = paint(features);
        assertFalse(ink(trueWidth, 10, 10), "a line without width leaves no ink");

        BufferedImage hairlines = paint(painter().hairlineMm(0.3), features);
        assertTrue(ink(hairlines, 10, 10));
        assertFalse(ink(hairlines, 10, 10.4));
        assertTrue(ink(hairlines, 10, 5 + 0.4), "a line that has a width keeps it");
        assertFalse(ink(hairlines, 10, 5 + 0.6));
    }

    @Test
    void compactOutputDrawsTheSamePicture() throws IOException {
        Features features = features("r400", "rect3000x1000");
        features.getFeatures().add(line(2, 2, 10, 2, 0));
        features.getFeatures().add(line(10, 2, 10, 8, 0));
        features.getFeatures().add(arc(10, 8, 14, 12, 14, 8, true, 0));
        features.getFeatures().add(pad(5, 15, 1));
        features.getFeatures().add(line(14, 12, 18, 12, 0));

        BufferedImage plain = paint(features);
        BufferedImage compact = paint(painter().compact(true), features);
        int differing = 0;
        for (int y = 0; y < plain.getHeight(); y++) {
            for (int x = 0; x < plain.getWidth(); x++) {
                // Joined strokes are anti-aliased a shade differently along their edges;
                // a pixel only differs when one picture has ink where the other has none.
                if (Math.abs((plain.getRGB(x, y) & 0xff) - (compact.getRGB(x, y) & 0xff)) > 96) {
                    differing++;
                }
            }
        }
        assertEquals(0, differing);
        assertTrue(ink(plain, 10, 5));
        assertTrue(ink(plain, 16, 12));
    }
}

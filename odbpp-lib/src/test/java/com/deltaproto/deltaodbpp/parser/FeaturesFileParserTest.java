package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.*;
import com.deltaproto.deltaodbpp.testutil.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FeaturesFileParserTest {

    /**
     * Resolves the features file of a signal layer from the committed multilayer
     * sample, extracting the archive into the supplied temp dir first.
     */
    private static Path signalFeaturesFile(Path tempDir) throws Exception {
        Path odbRoot = Fixtures.extractSample(Fixtures.MULTILAYER_SAMPLE, tempDir);
        return odbRoot.resolve("steps").resolve("cellular_flip-phone")
                .resolve("layers").resolve("signal_1").resolve("features");
    }

    @Test
    public void testParse(@TempDir Path tempDir) throws Exception {
        Path path = signalFeaturesFile(tempDir);
        FeaturesFileParser parser = new FeaturesFileParser();
        Features features = parser.parse(path);
        assertNotNull(features);
        assertFalse(features.getFeatures().isEmpty(), "Signal layer should contain features");

        // A populated signal layer should yield pads, lines and surfaces.
        Pad firstPad = features.getFeatures().stream()
                .filter(f -> f instanceof Pad)
                .map(f -> (Pad) f)
                .findFirst()
                .orElseThrow();
        assertNotNull(firstPad);
        // Coordinates are normalised to millimetres at parse time.
        assertTrue(Double.isFinite(firstPad.getX()), "Pad X should be a finite coordinate");
        assertTrue(Double.isFinite(firstPad.getY()), "Pad Y should be a finite coordinate");
        assertTrue(firstPad.getSymbolNumber() >= 0, "Symbol number should be non-negative");
        assertTrue(firstPad.getOrientationType() >= 0, "Orientation type should be non-negative");

        Line firstLine = features.getFeatures().stream()
                .filter(f -> f instanceof Line)
                .map(f -> (Line) f)
                .findFirst()
                .orElseThrow();
        assertNotNull(firstLine);
        assertTrue(Double.isFinite(firstLine.getXs()));
        assertTrue(Double.isFinite(firstLine.getYs()));
        assertTrue(Double.isFinite(firstLine.getXe()));
        assertTrue(Double.isFinite(firstLine.getYe()));
        assertTrue(firstLine.getSymbolNumber() >= 0);

        Surface firstSurface = features.getFeatures().stream()
                .filter(f -> f instanceof Surface)
                .map(f -> (Surface) f)
                .findFirst()
                .orElseThrow();
        assertNotNull(firstSurface);
        assertFalse(firstSurface.getPolygons().isEmpty(), "Surface should have polygon contours");
        ContourPolygon firstPolygon = firstSurface.getPolygons().get(0);
        assertTrue(Double.isFinite(firstPolygon.getXStart()));
        assertTrue(Double.isFinite(firstPolygon.getYStart()));
    }

    @Test
    public void testParseLineRecord(@TempDir Path tempDir) throws Exception {
        // Verifies that Line records (L xs ys xe ye symbolNumber polarity rotation)
        // are parsed structurally from a real feature file.
        Path path = signalFeaturesFile(tempDir);
        FeaturesFileParser parser = new FeaturesFileParser();
        Features features = parser.parse(path);
        assertNotNull(features);
        assertFalse(features.getFeatures().isEmpty());

        Line anyLine = (Line) features.getFeatures().stream()
                .filter(f -> f instanceof Line)
                .findFirst()
                .orElse(null);

        assertNotNull(anyLine, "Should be able to parse Line records");
        assertTrue(Double.isFinite(anyLine.getXs()), "Line start X should be finite");
        assertTrue(Double.isFinite(anyLine.getYs()), "Line start Y should be finite");
        assertTrue(Double.isFinite(anyLine.getXe()), "Line end X should be finite");
        assertTrue(Double.isFinite(anyLine.getYe()), "Line end Y should be finite");
        assertTrue(anyLine.getSymbolNumber() >= 0, "Symbol number should be non-negative");
    }

    /**
     * A features file without a UNITS directive is in the step's unit, and so are its
     * symbol names: {@code r9.8425} is 9.8425 mil (0.25 mm) in an inch-native step. The
     * model's unit must follow the scale the coordinates were converted with, or symbol
     * sizes are read as microns and come out 25.4 times too small.
     */
    @Test
    public void unitsFollowTheStepScaleWhenTheFileDeclaresNone(@TempDir Path tempDir) throws Exception {
        Path undeclared = tempDir.resolve("features");
        java.nio.file.Files.writeString(undeclared, "#\n$0 r9.8425\n#\nP 1 2 0 P 0 8 0;ID=1\n");
        FeaturesFileParser parser = new FeaturesFileParser();

        Features inchStep = parser.parse(undeclared, 25.4);
        assertFalse(inchStep.isMillimeters(), "no directive in an inch step: symbols are mils");
        assertEquals(25.4, ((Pad) inchStep.getFeatures().get(0)).getX(), 1e-9);

        Features mmStep = parser.parse(undeclared, 1.0);
        assertTrue(mmStep.isMillimeters(), "no directive in a mm step: symbols are microns");
        assertEquals(1.0, ((Pad) mmStep.getFeatures().get(0)).getX(), 1e-9);

        Path declared = tempDir.resolve("features-mm");
        java.nio.file.Files.writeString(declared, "UNITS=MM\n$0 r250\nP 1 2 0 P 0 8 0;ID=1\n");
        Features overridden = parser.parse(declared, 25.4);
        assertTrue(overridden.isMillimeters(), "an inline directive wins over the step scale");
        assertEquals(1.0, ((Pad) overridden.getFeatures().get(0)).getX(), 1e-9);
    }
}

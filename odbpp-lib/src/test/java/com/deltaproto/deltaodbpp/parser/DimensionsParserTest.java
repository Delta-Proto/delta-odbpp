package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.Dimensions;
import com.deltaproto.deltaodbpp.model.Dimensions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DimensionsParserTest {

    private DimensionsParser parser;

    @BeforeEach
    void setUp() {
        parser = new DimensionsParser();
    }

    @Test
    void testParseBasicDimensions(@TempDir Path tempDir) throws IOException {
        String content = """
            VERSION=1
            UNITS=INCH
            PARAMETERS {
                ID=0
                LINE_WIDTH=0.01
                POST_DECIMAL_DIST=3
                FONT=STANDARD
                FONT_WIDTH=0.07
                FONT_HEIGHT=0.05
                SCALE=100
            }
            DIMENSION {
                TYPE=HORIZONTAL
                PARAMETERS=0
                REF1X=0.1
                REF1Y=0.2
                REF2X=1.2
                REF2Y=0.3
                LINE_PT_X=1.2
                LINE_PT_Y=0.7
                ARROW_POS=AUTOMATIC
                MAGNIFY=1
                TO_ARC_CENTER=NO
                TEXT {
                    VALUE=27.94
                    UNITS=MM
                    VIEW_UNITS=YES
                    X=0.65
                    Y=0.7
                    ANGLE=0
                }
            }
            """;

        Path dimensionsFile = tempDir.resolve("dimensions");
        Files.writeString(dimensionsFile, content);

        Dimensions dimensions = parser.parse(dimensionsFile);

        assertNotNull(dimensions);
        assertEquals(1, dimensions.getVersion());
        assertEquals("INCH", dimensions.getUnits());

        // Check parameters
        assertNotNull(dimensions.getParameters());
        assertEquals(0, dimensions.getParameters().getId());
        assertEquals(0.01, dimensions.getParameters().getLineWidth(), 0.001);
        assertEquals("STANDARD", dimensions.getParameters().getFont());
        assertEquals(100.0, dimensions.getParameters().getScale(), 0.001);

        // Check dimensions
        assertEquals(1, dimensions.getDimensions().size());
        Dimension dim = dimensions.getDimensions().get(0);
        assertEquals(DimensionType.HORIZONTAL, dim.getType());
        assertEquals(0.1, dim.getRef1X(), 0.001);
        assertEquals(0.2, dim.getRef1Y(), 0.001);
        assertEquals(1.2, dim.getRef2X(), 0.001);
        assertEquals("AUTOMATIC", dim.getArrowPos());
        assertFalse(dim.isToArcCenter());

        // Check dimension text
        assertNotNull(dim.getText());
        assertEquals("27.94", dim.getText().getValue());
        assertEquals("MM", dim.getText().getUnits());
        assertTrue(dim.getText().isViewUnits());
        assertEquals(0.65, dim.getText().getX(), 0.001);
    }

    @Test
    void testParseDimensionWithPaper(@TempDir Path tempDir) throws IOException {
        String content = """
            VERSION=1
            UNITS=MM
            PARAMETERS {
                ID=0
                SCALE=1
                PAPER {
                    ORIENTATION=PORTRAIT
                    SIZE=A4
                    WIDTH=210
                    HEIGHT=297
                    X=0
                    Y=0
                    MARGIN {
                        TOP=10
                        BOTTOM=10
                        LEFT=20
                        RIGHT=20
                    }
                    ACTIVE {
                        X00=20
                        Y00=10
                        X11=190
                        Y11=287
                    }
                    COLOR {
                        FEATURE=RED
                        DIMENS=BLUE
                    }
                }
            }
            """;

        Path dimensionsFile = tempDir.resolve("dimensions");
        Files.writeString(dimensionsFile, content);

        Dimensions dimensions = parser.parse(dimensionsFile);

        assertNotNull(dimensions);
        assertNotNull(dimensions.getParameters());
        assertNotNull(dimensions.getParameters().getPaper());

        Paper paper = dimensions.getParameters().getPaper();
        assertEquals("PORTRAIT", paper.getOrientation());
        assertEquals("A4", paper.getSize());
        assertEquals(210, paper.getWidth(), 0.001);
        assertEquals(297, paper.getHeight(), 0.001);

        assertNotNull(paper.getMargin());
        assertEquals(10, paper.getMargin().getTop(), 0.001);
        assertEquals(20, paper.getMargin().getLeft(), 0.001);

        assertNotNull(paper.getActive());
        assertEquals(20, paper.getActive().getX00(), 0.001);
        assertEquals(190, paper.getActive().getX11(), 0.001);

        assertNotNull(paper.getColor());
        assertEquals("RED", paper.getColor().getFeature());
        assertEquals("BLUE", paper.getColor().getDimens());
    }

    @Test
    void testParseMultipleDimensions(@TempDir Path tempDir) throws IOException {
        String content = """
            VERSION=1
            UNITS=INCH
            DIMENSION {
                TYPE=HORIZONTAL
                REF1X=0
                REF1Y=0
            }
            DIMENSION {
                TYPE=VERTICAL
                REF1X=1
                REF1Y=1
            }
            DIMENSION {
                TYPE=RADIAL
                REF1X=2
                REF1Y=2
            }
            """;

        Path dimensionsFile = tempDir.resolve("dimensions");
        Files.writeString(dimensionsFile, content);

        Dimensions dimensions = parser.parse(dimensionsFile);

        assertNotNull(dimensions);
        assertEquals(3, dimensions.getDimensions().size());
        assertEquals(DimensionType.HORIZONTAL, dimensions.getDimensions().get(0).getType());
        assertEquals(DimensionType.VERTICAL, dimensions.getDimensions().get(1).getType());
        assertEquals(DimensionType.RADIAL, dimensions.getDimensions().get(2).getType());
    }
}

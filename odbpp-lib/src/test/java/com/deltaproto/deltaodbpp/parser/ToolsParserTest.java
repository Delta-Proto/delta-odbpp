package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.Tool;
import com.deltaproto.deltaodbpp.model.Tools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ToolsParserTest {

    private ToolsParser parser;

    @BeforeEach
    void setUp() {
        parser = new ToolsParser();
    }

    @Test
    void testParseBasicToolsFile(@TempDir Path tempDir) throws IOException {
        String toolsData = """
            UNITS=MM
            THICKNESS=0
            USER_PARAMS=

            TOOLS {
                NUM=1
                TYPE=VIA
                MIN_TOL=0
                MAX_TOL=0
                BIT=
                FINISH_SIZE=199.9996
                DRILL_SIZE=199.9996
            }
            """;

        Path toolsFile = tempDir.resolve("tools");
        Files.write(toolsFile, toolsData.getBytes());

        Tools tools = parser.parse(toolsFile);

        assertNotNull(tools);
        assertEquals("MM", tools.getUnits());
        assertEquals(0, tools.getThickness());
        assertEquals("", tools.getUserParams());
        assertEquals(1, tools.getTools().size());

        Tool tool = tools.getTools().get(0);
        assertEquals(1, tool.getNum());
        assertEquals(Tool.ToolType.VIA, tool.getType());
        assertEquals(0, tool.getMinTol());
        assertEquals(0, tool.getMaxTol());
        assertEquals("", tool.getBit());
        assertEquals(0.1999996, tool.getFinishSize(), 1e-7); // 199.9996 microns
        assertEquals(0.1999996, tool.getDrillSize(), 1e-7);
    }

    @Test
    void testParseMultipleTools(@TempDir Path tempDir) throws IOException {
        String toolsData = """
            UNITS=INCH
            THICKNESS=62.5
            USER_PARAMS=method25

            TOOLS {
                NUM=1
                TYPE=VIA
                TYPE2=STANDARD
                MIN_TOL=0
                MAX_TOL=0
                BIT=
                FINISH_SIZE=11.5
                DRILL_SIZE=13.5
            }
            TOOLS {
                NUM=2
                TYPE=PLATED
                TYPE2=STANDARD
                MIN_TOL=0
                MAX_TOL=0
                BIT=
                FINISH_SIZE=15
                DRILL_SIZE=19
            }
            """;

        Path toolsFile = tempDir.resolve("tools");
        Files.write(toolsFile, toolsData.getBytes());

        Tools tools = parser.parse(toolsFile);

        assertNotNull(tools);
        assertEquals("INCH", tools.getUnits());
        // Tool sizes are normalised to mm at parse time. Source UNITS=INCH means
        // sizes are in mils (× 0.0254 → mm); THICKNESS is also in mils.
        assertEquals(62.5 * 0.0254, tools.getThickness(), 0.0001);
        assertEquals("method25", tools.getUserParams());
        assertEquals(2, tools.getTools().size());

        // First tool - VIA
        Tool tool1 = tools.getTools().get(0);
        assertEquals(1, tool1.getNum());
        assertEquals(Tool.ToolType.VIA, tool1.getType());
        assertEquals(Tool.ToolType2.STANDARD, tool1.getType2());
        assertEquals(11.5 * 0.0254, tool1.getFinishSize(), 0.0001);
        assertEquals(13.5 * 0.0254, tool1.getDrillSize(), 0.0001);

        // Second tool - PLATED
        Tool tool2 = tools.getTools().get(1);
        assertEquals(2, tool2.getNum());
        assertEquals(Tool.ToolType.PLATED, tool2.getType());
        assertEquals(Tool.ToolType2.STANDARD, tool2.getType2());
        assertEquals(15.0 * 0.0254, tool2.getFinishSize(), 0.0001);
        assertEquals(19.0 * 0.0254, tool2.getDrillSize(), 0.0001);
    }

    @Test
    void testParseToolTypes(@TempDir Path tempDir) throws IOException {
        String toolsData = """
            UNITS=MM
            THICKNESS=0
            USER_PARAMS=

            TOOLS {
                NUM=1
                TYPE=PLATED
                TYPE2=PRESS_FIT
            }
            TOOLS {
                NUM=2
                TYPE=NON_PLATED
            }
            TOOLS {
                NUM=3
                TYPE=VIA
                TYPE2=LASER
            }
            TOOLS {
                NUM=4
                TYPE=VIA
                TYPE2=PHOTO
            }
            """;

        Path toolsFile = tempDir.resolve("tools");
        Files.write(toolsFile, toolsData.getBytes());

        Tools tools = parser.parse(toolsFile);

        assertEquals(4, tools.getTools().size());

        // PLATED with PRESS_FIT
        Tool tool1 = tools.getTools().get(0);
        assertEquals(Tool.ToolType.PLATED, tool1.getType());
        assertEquals(Tool.ToolType2.PRESS_FIT, tool1.getType2());

        // NON_PLATED with default STANDARD
        Tool tool2 = tools.getTools().get(1);
        assertEquals(Tool.ToolType.NON_PLATED, tool2.getType());
        assertEquals(Tool.ToolType2.STANDARD, tool2.getType2());

        // VIA with LASER
        Tool tool3 = tools.getTools().get(2);
        assertEquals(Tool.ToolType.VIA, tool3.getType());
        assertEquals(Tool.ToolType2.LASER, tool3.getType2());

        // VIA with PHOTO
        Tool tool4 = tools.getTools().get(3);
        assertEquals(Tool.ToolType.VIA, tool4.getType());
        assertEquals(Tool.ToolType2.PHOTO, tool4.getType2());
    }

    @Test
    void testParseRealTestDataFormat(@TempDir Path tempDir) throws IOException {
        // Format from actual test data file
        String toolsData = """
            UNITS=MM
            THICKNESS=0
            USER_PARAMS=

            TOOLS {
                NUM=1
                TYPE=VIA
                MIN_TOL=0
                MAX_TOL=0
                BIT=
                FINISH_SIZE=199.9996
                DRILL_SIZE=199.9996
            }

            TOOLS {
                NUM=2
                TYPE=VIA
                MIN_TOL=0
                MAX_TOL=0
                BIT=
                FINISH_SIZE=249.9995
                DRILL_SIZE=249.9995
            }

            TOOLS {
                NUM=3
                TYPE=VIA
                MIN_TOL=0
                MAX_TOL=0
                BIT=
                FINISH_SIZE=299.9994
                DRILL_SIZE=299.9994
            }

            TOOLS {
                NUM=4
                TYPE=PLATED
                MIN_TOL=0
                MAX_TOL=0
                BIT=
                FINISH_SIZE=900.00074
                DRILL_SIZE=900.00074
            }
            """;

        Path toolsFile = tempDir.resolve("tools");
        Files.write(toolsFile, toolsData.getBytes());

        Tools tools = parser.parse(toolsFile);

        assertNotNull(tools);
        assertEquals("MM", tools.getUnits());
        assertEquals(4, tools.getTools().size());

        // Verify VIA tools
        Tool tool1 = tools.getTools().get(0);
        assertEquals(1, tool1.getNum());
        assertEquals(Tool.ToolType.VIA, tool1.getType());
        assertEquals(0.1999996, tool1.getFinishSize(), 1e-7); // microns -> mm

        Tool tool2 = tools.getTools().get(1);
        assertEquals(2, tool2.getNum());
        assertEquals(Tool.ToolType.VIA, tool2.getType());
        assertEquals(0.2499995, tool2.getFinishSize(), 1e-7);

        Tool tool3 = tools.getTools().get(2);
        assertEquals(3, tool3.getNum());
        assertEquals(Tool.ToolType.VIA, tool3.getType());
        assertEquals(0.2999994, tool3.getFinishSize(), 1e-7);

        // Verify PLATED tool
        Tool tool4 = tools.getTools().get(3);
        assertEquals(4, tool4.getNum());
        assertEquals(Tool.ToolType.PLATED, tool4.getType());
        assertEquals(0.90000074, tool4.getFinishSize(), 1e-7);
    }

    @Test
    void testParseEmptyFile(@TempDir Path tempDir) throws IOException {
        String toolsData = "";

        Path toolsFile = tempDir.resolve("tools");
        Files.write(toolsFile, toolsData.getBytes());

        Tools tools = parser.parse(toolsFile);

        assertNotNull(tools);
        assertTrue(tools.getTools().isEmpty());
    }

    @Test
    void testParseWithComments(@TempDir Path tempDir) throws IOException {
        String toolsData = """
            # This is a comment
            UNITS=MM
            THICKNESS=0
            USER_PARAMS=
            # Another comment
            TOOLS {
                NUM=1
                TYPE=VIA
                # Comment inside block
                FINISH_SIZE=100
            }
            """;

        Path toolsFile = tempDir.resolve("tools");
        Files.write(toolsFile, toolsData.getBytes());

        Tools tools = parser.parse(toolsFile);

        assertNotNull(tools);
        assertEquals("MM", tools.getUnits());
        assertEquals(1, tools.getTools().size());
        assertEquals(0.1, tools.getTools().get(0).getFinishSize(), 1e-7); // 100 microns -> 0.1 mm
    }
}

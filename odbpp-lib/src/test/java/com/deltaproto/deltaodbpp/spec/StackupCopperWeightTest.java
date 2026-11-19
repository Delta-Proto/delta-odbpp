package com.deltaproto.deltaodbpp.spec;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.parser.OdbParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins how a copper layer's {@code .copper_weight} becomes a stated copper thickness.
 *
 * <p>The attribute's unit follows the layer attrlist's own {@code UNITS} line: micrometres of
 * copper under {@code MM}, oz/ft² under {@code INCH} or under no line at all. Each test writes a
 * small four-copper-layer archive with the attrlists it is about, so the unit line, the value and
 * the expectation sit side by side.
 */
class StackupCopperWeightTest {

    private static final List<String> COPPER = List.of("top", "in1", "in2", "bot");

    @TempDir
    Path tempDir;

    // ------------------------------------------------------------------------
    // The unit follows the attrlist
    // ------------------------------------------------------------------------

    @Test
    void metricAttrlist_statesThicknessInMicrometres() throws IOException {
        List<StackupLayer> stack = resolve("MM",
                "UNITS=MM\n.copper_weight = 70\n",
                "UNITS=MM\n.copper_weight = 35.001\n",
                "UNITS=MM\n.copper_weight = 17.501\n",
                "UNITS=MM\n.copper_weight = 70\n");

        assertStated(stack, "top", 70_000_000L, 2.0);
        assertStated(stack, "in1", 35_001_000L, 35.001 / 35.0);
        assertStated(stack, "in2", 17_501_000L, 17.501 / 35.0);
        assertStated(stack, "bot", 70_000_000L, 2.0);
    }

    @Test
    void imperialAttrlist_statesWeightInOunces() throws IOException {
        List<StackupLayer> stack = resolve("INCH",
                "UNITS=INCH\n.copper_weight = 0.5\n",
                "UNITS=INCH\n.copper_weight = 1\n",
                "UNITS=INCH\n.copper_weight = 2\n",
                "UNITS=INCH\n.copper_weight = 0.5\n");

        assertStated(stack, "top", 17_500_000L, 0.5);
        assertStated(stack, "in1", 35_000_000L, 1.0);
        assertStated(stack, "in2", 70_000_000L, 2.0);
        assertStated(stack, "bot", 17_500_000L, 0.5);
    }

    /**
     * An attrlist with no {@code UNITS} line carries ounces whatever the job says: writers that
     * leave the line out write ounces even into an otherwise metric job.
     */
    @Test
    void attrlistWithoutUnits_statesWeightInOunces_evenInAMetricJob() throws IOException {
        List<StackupLayer> metricJob = resolve("MM",
                ".copper_weight = 1.0\n", ".copper_weight = 0.5\n",
                ".copper_weight = 0.5\n", ".copper_weight = 2.0\n");
        assertStated(metricJob, "top", 35_000_000L, 1.0);
        assertStated(metricJob, "in1", 17_500_000L, 0.5);
        assertStated(metricJob, "bot", 70_000_000L, 2.0);

        List<StackupLayer> unitlessJob = resolve(null,
                ".copper_weight = 1\n", ".copper_weight = 1\n",
                ".copper_weight = 1\n", ".copper_weight = 1\n");
        assertStated(unitlessJob, "in1", 35_000_000L, 1.0);
    }

    // ------------------------------------------------------------------------
    // A value the declared unit cannot mean
    // ------------------------------------------------------------------------

    /** 1 µm is no foil; 1 oz is the norm. The other reading is taken when the first is impossible. */
    @Test
    void valueImpossibleInTheDeclaredUnit_isReadInTheOther() throws IOException {
        List<StackupLayer> stack = resolve("MM",
                "UNITS=MM\n.copper_weight = 1\n",
                "UNITS=INCH\n.copper_weight = 35.56\n",
                ".copper_weight = 18\n",
                "UNITS=MM\n.copper_weight = 2\n");

        assertStated(stack, "top", 35_000_000L, 1.0);
        assertStated(stack, "in1", 35_560_000L, 35.56 / 35.0);
        assertStated(stack, "in2", 18_000_000L, 18.0 / 35.0);
        assertStated(stack, "bot", 70_000_000L, 2.0);
    }

    /**
     * Zero is what writers park on layers they have no value for, and a five-digit figure is no
     * copper in either unit. Neither may surface as a stated thickness.
     */
    @Test
    void sentinelAndStrayValues_fallBackToTheEstimate() throws IOException {
        List<StackupLayer> stack = resolve("MM",
                "UNITS=MM\n.copper_weight = 0\n",
                "UNITS=MM\n.copper_weight = 14100\n",
                "UNITS=INCH\n.copper_weight = 0.01\n",
                "UNITS=MM\n.copper_weight = n/a\n");

        assertEstimated(stack, "top", 35_000_000L, 1.0);
        assertEstimated(stack, "in1", 18_000_000L, 0.5);
        assertEstimated(stack, "in2", 18_000_000L, 0.5);
        assertEstimated(stack, "bot", 35_000_000L, 1.0);
    }

    @Test
    void plausibilityBound_isFiveTo420Micrometres() throws IOException {
        List<StackupLayer> stack = resolve("MM",
                "UNITS=MM\n.copper_weight = 5\n",
                "UNITS=MM\n.copper_weight = 420\n",
                "UNITS=MM\n.copper_weight = 421\n",
                "UNITS=INCH\n.copper_weight = 12\n");

        assertStated(stack, "top", 5_000_000L, 5.0 / 35.0);
        assertStated(stack, "in1", 420_000_000L, 12.0);
        assertEstimated(stack, "in2", 18_000_000L, 0.5);
        assertStated(stack, "bot", 420_000_000L, 12.0);
    }

    // ------------------------------------------------------------------------
    // No attribute at all
    // ------------------------------------------------------------------------

    @Test
    void absentAttribute_keepsTheEstimate() throws IOException {
        List<StackupLayer> stack = resolve("MM",
                "UNITS=MM\n.out_polarity = positive\n", null, null,
                "UNITS=MM\n.out_polarity = positive\n");

        assertEstimated(stack, "top", 35_000_000L, 1.0);
        assertEstimated(stack, "in1", 18_000_000L, 0.5);
        assertEstimated(stack, "in2", 18_000_000L, 0.5);
        assertEstimated(stack, "bot", 35_000_000L, 1.0);
    }

    /** One layer without the attribute does not take the others' statements down with it. */
    @Test
    void statedAndEstimatedLayers_coexist_andASumNeedsEveryLayerStated() throws IOException {
        List<StackupLayer> mixed = resolve("MM",
                "UNITS=MM\n.copper_weight = 35\n", null,
                "UNITS=MM\n.copper_weight = 35\n", "UNITS=MM\n.copper_weight = 35\n");
        assertStated(mixed, "top", 35_000_000L, 1.0);
        assertEstimated(mixed, "in1", 18_000_000L, 0.5);
        assertNull(StackupResolver.summedThicknessMm(mixed));

        List<StackupLayer> all = resolve("MM",
                "UNITS=MM\n.copper_weight = 35\n", "UNITS=MM\n.copper_weight = 18\n",
                "UNITS=MM\n.copper_weight = 18\n", "UNITS=MM\n.copper_weight = 35\n");
        assertEquals(0.106, StackupResolver.summedThicknessMm(all), 1e-12);
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private static void assertStated(List<StackupLayer> stack, String name, long pm, double oz) {
        StackupLayer l = named(stack, name);
        assertEquals(pm, l.getThicknessPm(), name + " thickness");
        assertFalse(l.isThicknessEstimated(), name + " thickness is the archive's own");
        assertEquals(oz, l.getCopperWeightOz(), 1e-9, name + " weight");
        assertFalse(l.isCopperWeightEstimated(), name + " weight is the archive's own");
    }

    private static void assertEstimated(List<StackupLayer> stack, String name, long pm, double oz) {
        StackupLayer l = named(stack, name);
        assertEquals(pm, l.getThicknessPm(), name + " thickness typical");
        assertTrue(l.isThicknessEstimated(), name + " thickness is a typical");
        assertEquals(oz, l.getCopperWeightOz(), 1e-9, name + " weight typical");
        assertTrue(l.isCopperWeightEstimated(), name + " weight is a typical");
    }

    private static StackupLayer named(List<StackupLayer> stack, String name) {
        return stack.stream()
                .filter(l -> name.equals(l.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no stackup entry named " + name));
    }

    /**
     * Write a four-copper-layer archive and resolve its stack.
     *
     * @param jobUnits the {@code UNITS} of {@code misc/info} and the geometry files, or null for none
     * @param attrlists the attrlist text of top, in1, in2 and bot; null writes no attrlist
     */
    private List<StackupLayer> resolve(String jobUnits, String... attrlists) throws IOException {
        Path root = Files.createTempDirectory(tempDir, "odb");
        String units = jobUnits == null ? "" : "UNITS=" + jobUnits + "\n";

        StringBuilder matrix = new StringBuilder("STEP {\n    COL=1\n    NAME=pcb\n}\n");
        for (int i = 0; i < COPPER.size(); i++) {
            matrix.append("\nLAYER {\n    ROW=").append(i + 1)
                    .append("\n    CONTEXT=BOARD\n    TYPE=SIGNAL\n    ID=").append(i + 1)
                    .append("\n    NAME=").append(COPPER.get(i))
                    .append("\n    POLARITY=POSITIVE\n    START_NAME=\n    END_NAME=\n    OLD_NAME=\n}\n");
        }
        write(root.resolve("matrix/matrix"), matrix.toString());
        write(root.resolve("misc/info"),
                "PRODUCT_MODEL_NAME=copper-weight\nODB_VERSION_MAJOR=8\nODB_VERSION_MINOR=1\n" + units);
        write(root.resolve("steps/pcb/stephdr"), units + "X_DATUM=0\nY_DATUM=0\n");
        write(root.resolve("steps/pcb/profile"),
                units + "S P 0\nOB 0 0 I\nOS 1 0\nOS 1 1\nOS 0 1\nOS 0 0\nOE\nSE\n");
        for (int i = 0; i < COPPER.size(); i++) {
            Path layer = root.resolve("steps/pcb/layers").resolve(COPPER.get(i));
            write(layer.resolve("features"), units + "$0 r10\nP 0.5 0.5 0 P 0 8 0\n");
            if (attrlists[i] != null) {
                write(layer.resolve("attrlist"), attrlists[i]);
            }
        }

        Job job = new OdbParser().parse(root);
        return new OdbAnalyzer().analyze(job).getStackup();
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}

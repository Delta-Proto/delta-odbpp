package com.deltaproto.deltaodbpp.spec;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Matrix;
import com.deltaproto.deltaodbpp.model.MatrixLayer;
import com.deltaproto.deltaodbpp.model.Step;
import com.deltaproto.deltaodbpp.model.stackup.StackupFile;
import com.deltaproto.deltaodbpp.model.stackup.StackupUnits;
import com.deltaproto.deltaodbpp.testutil.StackupFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the physical stack as the analysis path exposes it: {@link StackupResolver}, the
 * {@link StackupLayer} entries it produces, and what {@link BoardSpecification} makes of them.
 */
class StackupResolverTest {

    private static Job jobWithStep(Matrix matrix, StackupFile stackup) {
        Job job = StackupFixtures.job(matrix, stackup);
        Step step = new Step();
        step.setName("pcb");
        step.setLayersByName(new LinkedHashMap<>());
        job.setSteps(new LinkedHashMap<>(java.util.Map.of("pcb", step)));
        return job;
    }

    private static StackupLayer named(List<StackupLayer> stack, String name) {
        return stack.stream()
                .filter(l -> name.equals(l.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no stackup entry named " + name));
    }

    // ------------------------------------------------------------------------
    // Ordering and shape
    // ------------------------------------------------------------------------

    @Test
    void stack_isDenselyOrderedTopToBottomWithoutNonPhysicalLayers() {
        List<StackupLayer> stack = StackupResolver.resolve(
                StackupFixtures.job(StackupFixtures.fourLayerMatrix(), null));

        assertFalse(stack.isEmpty());
        for (int i = 0; i < stack.size(); i++) {
            assertEquals(i, stack.get(i).getOrdinal(), "ordinals must be dense and start at 0");
        }
        assertEquals("sst", stack.get(0).getName(), "the top of the stack comes first");
        assertEquals("ssb", stack.get(stack.size() - 1).getName());
        for (StackupLayer l : stack) {
            assertNotEquals("DRILL", l.getFunction(), l.getName() + " is not part of the build");
        }
        assertEquals(LayerSide.TOP, named(stack, "top").getSide());
        assertEquals(LayerSide.BOTTOM, named(stack, "bot").getSide());
        assertEquals(LayerSide.INNER, named(stack, "gnd").getSide());
    }

    @Test
    void stack_carriesDielectricsWhichTheAnalyzedLayersAlsoList() {
        BoardSpecification spec = new OdbAnalyzer().analyze(
                jobWithStep(StackupFixtures.fourLayerMatrix(), null));

        assertEquals(3, spec.getStackup().stream().filter(StackupLayer::isDielectric).count());
        assertEquals(4, spec.getStackup().stream().filter(StackupLayer::isConductor).count());
        assertEquals(4, spec.getCopperLayerCount());
        assertTrue(spec.getLayers().size() > spec.getStackup().size(),
                "the analyzed layers include the drill row the stack drops");
    }

    /**
     * Some matrices list no dielectric rows at all while the stackup file does. The stack must still
     * come out complete, with the file's layers in the place its own order puts them.
     */
    @Test
    void stack_splicesInLayersOnlyTheStackupFileKnows(@TempDir Path tempDir) throws IOException {
        Matrix matrix = new Matrix();
        List<MatrixLayer> rows = new ArrayList<>();
        rows.add(matrixLayer(1, "SIGNAL", "top"));
        rows.add(matrixLayer(2, "SIGNAL", "bot"));
        matrix.setLayers(rows);

        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <StackupFile Version="8.1" DefaultUnits="MM">
              <EdaData CompanyName="Design Source">
                <Specs>
                  <Spec SpecName="Main">
                    <Material MaterialName="Core">
                      <Dielectric DielectricType="CORE"/>
                      <Default_Thickness Thickness="1.5"/>
                    </Material>
                  </Spec>
                </Specs>
                <Stackup StackupName="pcb">
                  <Group GroupName="Design">
                    <Layer LayerName="top" LayerType="SIGNAL" Side="TOP"/>
                    <Layer LayerName="core_1" LayerType="DIELECTRIC" LayerSubType="CORE" Side="INNER">
                      <SpecRef MaterialSpecName="Main"><Material MaterialName="Core"/></SpecRef>
                    </Layer>
                    <Layer LayerName="bot" LayerType="SIGNAL" Side="BOTTOM"/>
                  </Group>
                </Stackup>
              </EdaData>
            </StackupFile>
            """;
        StackupFile stackup = StackupFixtures.parseStackup(xml, tempDir);
        List<StackupLayer> stack = StackupResolver.resolve(StackupFixtures.job(matrix, stackup));

        assertEquals(List.of("top", "core_1", "bot"),
                stack.stream().map(StackupLayer::getName).toList());
        StackupLayer core = named(stack, "core_1");
        assertNull(core.getMatrixRow(), "a layer the matrix never listed has no matrix row");
        assertTrue(core.isDielectric());
        assertEquals(1_500_000_000L, core.getThicknessPm());
        assertFalse(core.isThicknessEstimated());
    }

    // ------------------------------------------------------------------------
    // Measured versus estimated, per value
    // ------------------------------------------------------------------------

    @Test
    void partialStackup_flagsEachValueSeparately(@TempDir Path tempDir) throws IOException {
        StackupFile stackup =
                StackupFixtures.parseStackup(StackupFixtures.partialStackupXml(), tempDir);
        List<StackupLayer> stack =
                StackupResolver.resolve(StackupFixtures.job(StackupFixtures.fourLayerMatrix(), stackup));

        StackupLayer core = named(stack, "dielectric_2");
        assertTrue(core.isThicknessEstimated(), "the file states no thickness for the core");
        assertFalse(core.isDielectricConstantEstimated(), "but it does state a Dk");
        assertFalse(core.isLossTangentEstimated());
        assertFalse(core.isMaterialEstimated());
        assertTrue(core.isAnyEstimated());
        assertFalse(core.isThicknessMeasured());

        StackupLayer top = named(stack, "top");
        assertTrue(top.isThicknessMeasured());
        assertFalse(top.isAnyEstimated());

        // The matrix names the prepreg even though the stackup file never mentions the layer, so the
        // material is the archive's word while everything physical about it is a guess.
        StackupLayer prepreg = named(stack, "dielectric_1");
        assertEquals("PP-1080", prepreg.getMaterial());
        assertFalse(prepreg.isMaterialEstimated());
        assertTrue(prepreg.isThicknessEstimated());
    }

    @Test
    void noStackupFile_leavesEveryPhysicalValueEstimated() {
        for (StackupLayer l : StackupResolver.resolve(
                StackupFixtures.job(StackupFixtures.fourLayerMatrix(), null))) {
            assertFalse(l.isThicknessMeasured(), l.getName() + " cannot have a measured thickness");
            assertTrue(l.isAnyEstimated(), l.getName() + " is entirely invented");
        }
    }

    // ------------------------------------------------------------------------
    // Total thickness
    // ------------------------------------------------------------------------

    @Test
    void totalThickness_prefersTheStackupFilesOwnTarget(@TempDir Path tempDir) throws IOException {
        StackupFile stackup = StackupFixtures.parseStackup(StackupFixtures.fullStackupXml(), tempDir);
        BoardSpecification spec = new OdbAnalyzer().analyze(
                jobWithStep(StackupFixtures.fourLayerMatrix(), stackup));

        // 23.4 mil, stated by the file.
        assertEquals(0.59436, spec.getTotalThicknessMm(), 1e-9);
        // The fixture is self-consistent, so summing the stack agrees to the picometre.
        assertEquals(spec.getTotalThicknessMm(),
                StackupResolver.summedThicknessMm(spec.getStackup()), 1e-12);
    }

    @Test
    void summedThickness_refusesToAddInventedNumbers(@TempDir Path tempDir) throws IOException {
        List<StackupLayer> allEstimated =
                StackupResolver.resolve(StackupFixtures.job(StackupFixtures.fourLayerMatrix(), null));
        assertNull(StackupResolver.summedThicknessMm(allEstimated));

        StackupFile partial =
                StackupFixtures.parseStackup(StackupFixtures.partialStackupXml(), tempDir);
        assertNull(StackupResolver.summedThicknessMm(
                StackupResolver.resolve(StackupFixtures.job(StackupFixtures.fourLayerMatrix(), partial))),
                "one estimated entry disqualifies the whole sum");
    }

    @Test
    void totalThickness_isNullWhenNothingStatesOne() {
        BoardSpecification spec = new OdbAnalyzer().analyze(
                jobWithStep(StackupFixtures.fourLayerMatrix(), null));
        assertNull(spec.getTotalThicknessMm());
    }

    // ------------------------------------------------------------------------
    // Units
    // ------------------------------------------------------------------------

    @Test
    void units_convertExactlyToPicometres() {
        assertEquals(25_400_000L, StackupUnits.MIL.toPicometres(1));
        assertEquals(25_400_000_000L, StackupUnits.INCH.toPicometres(1));
        assertEquals(1_000_000_000L, StackupUnits.MM.toPicometres(1));
        assertEquals(1_000_000L, StackupUnits.MICRON.toPicometres(1));
        assertEquals(StackupUnits.MIL, StackupUnits.parse(null, StackupUnits.DEFAULT));
        assertEquals(StackupUnits.MM, StackupUnits.parse("mm", StackupUnits.DEFAULT));
        assertEquals(StackupUnits.MIL, StackupUnits.parse("furlong", StackupUnits.DEFAULT));
    }

    // ------------------------------------------------------------------------
    // Drill spans
    // ------------------------------------------------------------------------

    @Test
    void analyzedLayer_carriesTheDrillSpanAsLayerReferences() {
        BoardSpecification spec = new OdbAnalyzer().analyze(
                jobWithStep(StackupFixtures.fourLayerMatrix(), null));

        AnalyzedLayer drill = spec.getLayers().stream()
                .filter(l -> "DRILL".equals(l.getType()))
                .findFirst()
                .orElseThrow();
        assertEquals("top", drill.getStartName());
        assertEquals("bot", drill.getEndName());

        AnalyzedLayer copper = spec.getLayers().stream()
                .filter(l -> "top".equals(l.getName()))
                .findFirst()
                .orElseThrow();
        assertNull(copper.getStartName(), "a copper layer spans nothing");
        assertNull(copper.getEndName());
    }

    private static MatrixLayer matrixLayer(int row, String type, String name) {
        MatrixLayer ml = new MatrixLayer();
        ml.setRow(row);
        ml.setContext("BOARD");
        ml.setType(type);
        ml.setName(name);
        ml.setPolarity("POSITIVE");
        return ml;
    }
}

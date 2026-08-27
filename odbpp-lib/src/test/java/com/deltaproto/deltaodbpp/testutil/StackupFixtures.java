package com.deltaproto.deltaodbpp.testutil;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Matrix;
import com.deltaproto.deltaodbpp.model.MatrixLayer;
import com.deltaproto.deltaodbpp.model.stackup.StackupFile;
import com.deltaproto.deltaodbpp.parser.StackupParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-built jobs for the stackup tests.
 *
 * <p>No committed archive ships a {@code matrix/stackup.xml} — the file is optional and rare — so
 * these assemble the matrix in code and parse the stackup from an XML string, which keeps the
 * fixture next to the assertion that reads it and still exercises the real
 * {@link StackupParser} binding.
 */
public final class StackupFixtures {

    private StackupFixtures() {
    }

    /**
     * A four-layer board's matrix: mask / silk over top copper, three dielectrics between four
     * copper layers, mask / silk under bottom copper, plus a drill row that must never reach the
     * stack.
     */
    public static Matrix fourLayerMatrix() {
        List<MatrixLayer> layers = new ArrayList<>();
        int row = 1;
        layers.add(matrixLayer(row++, "SILK_SCREEN", "sst", null, null));
        layers.add(matrixLayer(row++, "SOLDER_MASK", "smt", null, null));
        layers.add(matrixLayer(row++, "SIGNAL", "top", null, null));
        layers.add(matrixLayer(row++, "DIELECTRIC", "dielectric_1", "PREPREG", "PP-1080"));
        layers.add(matrixLayer(row++, "POWER_GROUND", "gnd", null, null));
        layers.add(matrixLayer(row++, "DIELECTRIC", "dielectric_2", "CORE", "FR4 Core"));
        layers.add(matrixLayer(row++, "SIGNAL", "pwr", null, null));
        layers.add(matrixLayer(row++, "DIELECTRIC", "dielectric_3", "PREPREG", "PP-1080"));
        layers.add(matrixLayer(row++, "SIGNAL", "bot", null, null));
        layers.add(matrixLayer(row++, "SOLDER_MASK", "smb", null, null));
        layers.add(matrixLayer(row++, "SILK_SCREEN", "ssb", null, null));
        MatrixLayer drill = matrixLayer(row, "DRILL", "drill_top_bot", null, null);
        drill.setStartName("top");
        drill.setEndName("bot");
        layers.add(drill);

        Matrix matrix = new Matrix();
        matrix.setLayers(layers);
        return matrix;
    }

    /** A job carrying {@link #fourLayerMatrix()} and, when non-null, the given parsed stackup. */
    public static Job job(Matrix matrix, StackupFile stackup) {
        Job job = new Job();
        job.setMatrix(matrix);
        job.setStackup(stackup);
        return job;
    }

    /** Parse a stackup.xml document from a string through the real parser. */
    public static StackupFile parseStackup(String xml, Path dir) throws IOException {
        Path file = dir.resolve("stackup.xml");
        Files.writeString(file, xml);
        return new StackupParser().parse(file);
    }

    /**
     * A complete stackup for {@link #fourLayerMatrix()}: every physical layer referenced, every
     * thickness, Dk, Df and copper weight stated. Lengths are in mils, the schema default, and are
     * chosen to convert to exact picometre counts.
     */
    public static String fullStackupXml() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <StackupFile Version="8.1" DefaultUnits="MIL">
              <EdaData CompanyName="Design Source">
                <Specs>
                  <Spec SpecName="Main">
                    <Material MaterialName="Solder Resist">
                      <Dielectric DielectricType="OTHER" OtherSubType="SOLDER_MASK">
                        <Properties PropertyName="default">
                          <Property DielectricConstant_Dk="3.9" LossTangent_Df="0.019"/>
                        </Properties>
                      </Dielectric>
                      <Default_Thickness Thickness="0.6"/>
                    </Material>
                    <Material MaterialName="Legend Ink">
                      <Dielectric DielectricType="OTHER" OtherSubType="OTHER"/>
                      <Default_Thickness Thickness="0.4"/>
                    </Material>
                    <Material MaterialName="1.0 oz CU">
                      <Conductor ConductorType="COPPER" CopperWeight_oz_ft2="1.0"/>
                      <Default_Thickness Thickness="1.4"/>
                    </Material>
                    <Material MaterialName="2.0 oz CU">
                      <Conductor ConductorType="COPPER" CopperWeight_oz_ft2="2.0"/>
                      <Default_Thickness Thickness="2.8"/>
                    </Material>
                    <Material MaterialName="PP-1080">
                      <Dielectric DielectricType="PREPREG" GlassStyle_Construction="1080">
                        <Properties PropertyName="default">
                          <Property FrequencyVal="1" Units="GHz"
                                    DielectricConstant_Dk="3.8" LossTangent_Df="0.011"/>
                        </Properties>
                      </Dielectric>
                      <Default_Thickness Thickness="2.5"/>
                    </Material>
                    <Material MaterialName="FR4 Core">
                      <Dielectric DielectricType="CORE">
                        <Properties PropertyName="default">
                          <Property FrequencyVal="1" Units="GHz"
                                    DielectricConstant_Dk="4.5" LossTangent_Df="0.017"/>
                        </Properties>
                      </Dielectric>
                      <Default_Thickness Thickness="8"/>
                    </Material>
                  </Spec>
                </Specs>
                <Stackup StackupName="pcb" StackupThickness="23.4" Units="MIL">
                  <Group GroupName="Design">
                    <Layer LayerName="sst" LayerType="SILK_SCREEN" Side="TOP">
                      <SpecRef MaterialSpecName="Main"><Material MaterialName="Legend Ink"/></SpecRef>
                    </Layer>
                    <Layer LayerName="smt" LayerType="SOLDER_MASK" Side="TOP">
                      <SpecRef MaterialSpecName="Main"><Material MaterialName="Solder Resist"/></SpecRef>
                    </Layer>
                    <Layer LayerName="top" LayerType="SIGNAL" Side="TOP">
                      <SpecRef MaterialSpecName="Main">
                        <Material MaterialName="1.0 oz CU" CopperAreaPrecent="42"/>
                      </SpecRef>
                    </Layer>
                    <Layer LayerName="dielectric_1" LayerType="DIELECTRIC" LayerSubType="PREPREG" Side="INNER">
                      <SpecRef MaterialSpecName="Main"><Material MaterialName="PP-1080"/></SpecRef>
                    </Layer>
                    <Layer LayerName="gnd" LayerType="POWER_GROUND" Side="INNER">
                      <SpecRef MaterialSpecName="Main"><Material MaterialName="2.0 oz CU"/></SpecRef>
                    </Layer>
                    <Layer LayerName="dielectric_2" LayerType="DIELECTRIC" LayerSubType="CORE" Side="INNER">
                      <SpecRef MaterialSpecName="Main"><Material MaterialName="FR4 Core"/></SpecRef>
                    </Layer>
                    <Layer LayerName="pwr" LayerType="SIGNAL" Side="INNER">
                      <SpecRef MaterialSpecName="Main"><Material MaterialName="2.0 oz CU"/></SpecRef>
                    </Layer>
                    <Layer LayerName="dielectric_3" LayerType="DIELECTRIC" LayerSubType="PREPREG" Side="INNER">
                      <SpecRef MaterialSpecName="Main"><Material MaterialName="PP-1080"/></SpecRef>
                    </Layer>
                    <Layer LayerName="bot" LayerType="SIGNAL" Side="BOTTOM">
                      <SpecRef MaterialSpecName="Main"><Material MaterialName="1.0 oz CU"/></SpecRef>
                    </Layer>
                    <Layer LayerName="smb" LayerType="SOLDER_MASK" Side="BOTTOM">
                      <SpecRef MaterialSpecName="Main"><Material MaterialName="Solder Resist"/></SpecRef>
                    </Layer>
                    <Layer LayerName="ssb" LayerType="SILK_SCREEN" Side="BOTTOM">
                      <SpecRef MaterialSpecName="Main"><Material MaterialName="Legend Ink"/></SpecRef>
                    </Layer>
                  </Group>
                </Stackup>
              </EdaData>
            </StackupFile>
            """;
    }

    /**
     * A stackup that answers only some questions: the two outer copper layers get a thickness and a
     * weight, the core gets a Dk and Df but no thickness of its own, and no other layer is mentioned
     * at all. It also states no board thickness.
     */
    public static String partialStackupXml() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <StackupFile Version="8.1" DefaultUnits="MM">
              <EdaData CompanyName="Design Source">
                <Specs>
                  <Spec SpecName="Main">
                    <Material MaterialName="Foil 1oz">
                      <Conductor ConductorType="COPPER" CopperWeight_oz_ft2="1.0"/>
                      <Default_Thickness Thickness="0.0348"/>
                    </Material>
                    <Material MaterialName="Core 370HR">
                      <Dielectric DielectricType="CORE">
                        <Properties PropertyName="default">
                          <Property DielectricConstant_Dk="4.06" LossTangent_Df="0.021"/>
                        </Properties>
                      </Dielectric>
                    </Material>
                  </Spec>
                </Specs>
                <Stackup StackupName="pcb">
                  <Group GroupName="Design">
                    <Layer LayerName="top" LayerType="SIGNAL" Side="TOP">
                      <SpecRef MaterialSpecName="Main"><Material MaterialName="Foil 1oz"/></SpecRef>
                    </Layer>
                    <Layer LayerName="dielectric_2" LayerType="DIELECTRIC" LayerSubType="CORE" Side="INNER">
                      <SpecRef MaterialSpecName="Main"><Material MaterialName="Core 370HR"/></SpecRef>
                    </Layer>
                    <Layer LayerName="bot" LayerType="SIGNAL" Side="BOTTOM">
                      <SpecRef MaterialSpecName="Main"><Material MaterialName="Foil 1oz"/></SpecRef>
                    </Layer>
                  </Group>
                </Stackup>
              </EdaData>
            </StackupFile>
            """;
    }

    private static MatrixLayer matrixLayer(int row, String type, String name,
                                           String dielectricType, String dielectricName) {
        MatrixLayer ml = new MatrixLayer();
        ml.setRow(row);
        ml.setContext("BOARD");
        ml.setType(type);
        ml.setName(name);
        ml.setPolarity("POSITIVE");
        ml.setDielectricType(dielectricType);
        ml.setDielectricName(dielectricName);
        return ml;
    }
}

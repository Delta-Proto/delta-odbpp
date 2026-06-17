package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

public class StepParser {
    private static final Logger logger = LoggerFactory.getLogger(StepParser.class);
    private final StepHdrParser stepHdrParser = new StepHdrParser();
    private final AttrListParser attrListParser = new AttrListParser();
    private final EdaDataParser edaDataParser = new EdaDataParser();
    private final BomParser bomParser = new BomParser();
    private final FeaturesFileParser featuresFileParser = new FeaturesFileParser();
    private final ImpedanceParser impedanceParser = new ImpedanceParser();
    private final ZonesParser zonesParser = new ZonesParser();
    private final LayerParser layerParser = new LayerParser();

    public Step parse(Path stepDir) throws IOException {
        Step step = new Step();
        step.setName(stepDir.getFileName().toString());

        Path stepHdrFile = stepDir.resolve("stephdr");
        if (Files.exists(stepHdrFile)) {
            step.setStepHdr(stepHdrParser.parse(stepHdrFile));
        }

        // Determine the mm-conversion scale once for the entire step. ODB++
        // defaults to INCH when no UNITS directive is present.
        double mmScale = 25.4;
        if (step.getStepHdr() != null && "MM".equalsIgnoreCase(step.getStepHdr().getUnits())) {
            mmScale = 1.0;
        }

        Path attrlistFile = stepDir.resolve("attrlist");
        if (Files.exists(attrlistFile)) {
            step.setAttrList(attrListParser.parse(attrlistFile));
        }

        Path edaDir = stepDir.resolve("eda");
        if (Files.exists(edaDir)) {
            Path dataFile = edaDir.resolve("data");
            if (Files.exists(dataFile)) {
                step.setEdaData(edaDataParser.parse(dataFile));
            }
        }

        Path bomsDir = stepDir.resolve("boms");
        if (Files.exists(bomsDir)) {
            // Simplified: assumes one bom per step
            try (var stream = Files.list(bomsDir)) {
                stream.filter(Files::isDirectory).findFirst().ifPresent(bomDir -> {
                    try {
                        step.setBom(bomParser.parse(bomDir.resolve("bom")));
                    } catch (IOException e) {
                        // Handle exception
                    }
                });
            }
        }

        Path profileFile = stepDir.resolve("profile");
        if (Files.exists(profileFile)) {
            step.setProfile(featuresFileParser.parse(profileFile, mmScale));
        }

        Path impedanceFile = stepDir.resolve("impedance.xml");
        if (Files.exists(impedanceFile)) {
            step.setImpedance(impedanceParser.parse(impedanceFile));
        }

        Path zonesFile = stepDir.resolve("zones");
        if (Files.exists(zonesFile)) {
            step.setZones(zonesParser.parse(zonesFile));
        }

        Path layersDir = stepDir.resolve("layers");
        if (Files.exists(layersDir)) {
            step.setLayersByName(new HashMap<>());
            final double layerScale = mmScale;
            try (var stream = Files.list(layersDir)) {
                stream.filter(Files::isDirectory).forEach(layerDir -> {
                    try {
                        step.getLayersByName().put(layerDir.getFileName().toString(),
                                layerParser.parse(layerDir, layerScale));
                    } catch (Exception e) {
                        logger.warn("Failed to parse layer {}: {}",
                                layerDir.getFileName(), e.getMessage());
                    }
                });
            }
        }

        return step;
    }
}

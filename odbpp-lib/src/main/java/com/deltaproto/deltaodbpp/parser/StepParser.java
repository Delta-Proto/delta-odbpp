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

        // Each optional sub-file is parsed defensively: a single malformed or
        // unreadable section must not discard the whole step (which would drop
        // the component layers and make a valid job look empty). The layers loop
        // below already follows this pattern.
        Path stepHdrFile = stepDir.resolve("stephdr");
        if (Files.exists(stepHdrFile)) {
            try {
                step.setStepHdr(stepHdrParser.parse(stepHdrFile));
            } catch (Exception e) {
                logger.warn("Failed to parse stephdr for step {}: {}", step.getName(), e.getMessage());
            }
        }

        // Determine the mm-conversion scale once for the entire step. ODB++
        // defaults to INCH when no UNITS directive is present.
        double mmScale = 25.4;
        if (step.getStepHdr() != null && "MM".equalsIgnoreCase(step.getStepHdr().getUnits())) {
            mmScale = 1.0;
        }

        Path attrlistFile = stepDir.resolve("attrlist");
        if (Files.exists(attrlistFile)) {
            try {
                step.setAttrList(attrListParser.parse(attrlistFile));
            } catch (Exception e) {
                logger.warn("Failed to parse attrlist for step {}: {}", step.getName(), e.getMessage());
            }
        }

        Path edaDir = stepDir.resolve("eda");
        if (Files.exists(edaDir)) {
            Path dataFile = edaDir.resolve("data");
            if (Files.exists(dataFile)) {
                try {
                    step.setEdaData(edaDataParser.parse(dataFile));
                } catch (Exception e) {
                    logger.warn("Failed to parse eda/data for step {}: {}", step.getName(), e.getMessage());
                }
            }
        }

        Path bomsDir = stepDir.resolve("boms");
        if (Files.exists(bomsDir)) {
            // Simplified: assumes one bom per step
            try (var stream = Files.list(bomsDir)) {
                stream.filter(Files::isDirectory).findFirst().ifPresent(bomDir -> {
                    try {
                        step.setBom(bomParser.parse(bomDir.resolve("bom")));
                    } catch (Exception e) {
                        logger.warn("Failed to parse bom for step {}: {}", step.getName(), e.getMessage());
                    }
                });
            }
        }

        Path profileFile = stepDir.resolve("profile");
        if (Files.exists(profileFile)) {
            try {
                step.setProfile(featuresFileParser.parse(profileFile, mmScale));
            } catch (Exception e) {
                logger.warn("Failed to parse profile for step {}: {}", step.getName(), e.getMessage());
            }
        }

        Path impedanceFile = stepDir.resolve("impedance.xml");
        if (Files.exists(impedanceFile)) {
            try {
                step.setImpedance(impedanceParser.parse(impedanceFile));
            } catch (Exception e) {
                logger.warn("Failed to parse impedance for step {}: {}", step.getName(), e.getMessage());
            }
        }

        Path zonesFile = stepDir.resolve("zones");
        if (Files.exists(zonesFile)) {
            try {
                step.setZones(zonesParser.parse(zonesFile));
            } catch (Exception e) {
                logger.warn("Failed to parse zones for step {}: {}", step.getName(), e.getMessage());
            }
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

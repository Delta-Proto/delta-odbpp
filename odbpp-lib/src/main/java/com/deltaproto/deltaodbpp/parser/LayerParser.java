package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.Layer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LayerParser {
    private final AttrListParser attrListParser = new AttrListParser();
    private final FeaturesFileParser featuresFileParser = new FeaturesFileParser();
    private final ComponentsParser componentsParser = new ComponentsParser();
    private final ProfileParser profileParser = new ProfileParser();
    private final ToolsParser toolsParser = new ToolsParser();
    private final DimensionsParser dimensionsParser = new DimensionsParser();
    private final NotesParser notesParser = new NotesParser();

    /** Default entry: assume coords are already in mm. */
    public Layer parse(Path layerDir) throws IOException {
        return parse(layerDir, 1.0);
    }

    /**
     * Parse a layer directory, threading the step-level mm-conversion scale to
     * every coordinate-bearing sub-parser so the layer's features, components,
     * profile and tools all land in millimetres.
     */
    public Layer parse(Path layerDir, double mmScale) throws IOException {
        Layer layer = new Layer();
        layer.setName(layerDir.getFileName().toString());

        Path attrlistFile = layerDir.resolve("attrlist");
        if (Files.exists(attrlistFile)) {
            layer.setAttrList(attrListParser.parse(attrlistFile));
        }

        Path featuresFile = layerDir.resolve("features");
        if (Files.exists(featuresFile)) {
            layer.setFeatures(featuresFileParser.parse(featuresFile, mmScale));
        }

        Path componentsFile = layerDir.resolve("components");
        if (Files.exists(componentsFile)) {
            layer.setComponents(componentsParser.parse(componentsFile, mmScale));
        }

        Path profileFile = layerDir.resolve("profile");
        if (Files.exists(profileFile)) {
            layer.setProfile(profileParser.parse(profileFile, mmScale));
        }

        Path toolsFile = layerDir.resolve("tools");
        if (Files.exists(toolsFile)) {
            layer.setTools(toolsParser.parse(toolsFile, mmScale));
        }

        Path dimensionsFile = layerDir.resolve("dimensions");
        if (Files.exists(dimensionsFile)) {
            layer.setDimensions(dimensionsParser.parse(dimensionsFile));
        }

        Path notesFile = layerDir.resolve("notes");
        if (Files.exists(notesFile)) {
            layer.setNotes(notesParser.parse(notesFile));
        }

        return layer;
    }
}

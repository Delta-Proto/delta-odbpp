package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.Matrix;
import com.deltaproto.deltaodbpp.model.MatrixLayer;
import com.deltaproto.deltaodbpp.model.Step;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MatrixParser {
    public Matrix parse(Path matrixFile) throws IOException {
        Matrix matrix = new Matrix();
        matrix.setLayers(new ArrayList<>());
        matrix.setSteps(new ArrayList<>());

        try (BufferedReader reader = Files.newBufferedReader(matrixFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("STEP {")) {
                    matrix.getSteps().add(parseStepBlock(reader));
                } else if (line.startsWith("LAYER {")) {
                    matrix.getLayers().add(parseLayerBlock(reader));
                }
            }
        }
        return matrix;
    }

    private Step parseStepBlock(BufferedReader reader) throws IOException {
        Step step = new Step();
        Map<String, String> data = parseBlock(reader);
        if (data.containsKey("COL")) step.setCol(Integer.parseInt(data.get("COL")));
        if (data.containsKey("ID")) step.setId(Integer.parseInt(data.get("ID")));
        step.setName(data.get("NAME"));
        return step;
    }

    private MatrixLayer parseLayerBlock(BufferedReader reader) throws IOException {
        MatrixLayer layer = new MatrixLayer();
        Map<String, String> data = parseBlock(reader);
        if (data.containsKey("ROW")) layer.setRow(Integer.parseInt(data.get("ROW")));
        layer.setContext(data.get("CONTEXT"));
        layer.setType(data.get("TYPE"));
        layer.setName(data.get("NAME"));
        layer.setPolarity(data.get("POLARITY"));
        layer.setStartName(data.get("START_NAME"));
        layer.setEndName(data.get("END_NAME"));
        // Set optional fields
        if (data.containsKey("OLD_NAME")) layer.setOldName(data.get("OLD_NAME"));
        if (data.containsKey("ADD_TYPE")) layer.setAddType(data.get("ADD_TYPE"));
        if (data.containsKey("COLOR")) layer.setColor(Integer.parseInt(data.get("COLOR")));
        if (data.containsKey("ID")) layer.setId(Integer.parseInt(data.get("ID")));
        if (data.containsKey("DIELECTRIC_TYPE")) layer.setDielectricType(data.get("DIELECTRIC_TYPE"));
        if (data.containsKey("DIELECTRIC_NAME")) layer.setDielectricName(data.get("DIELECTRIC_NAME"));
        if (data.containsKey("FORM")) layer.setForm(data.get("FORM"));
        if (data.containsKey("CU_TOP")) {
            try { layer.setCuTop(Integer.parseInt(data.get("CU_TOP"))); } catch (NumberFormatException ignored) {}
        }
        if (data.containsKey("CU_BOTTOM")) {
            try { layer.setCuBottom(Integer.parseInt(data.get("CU_BOTTOM"))); } catch (NumberFormatException ignored) {}
        }
        if (data.containsKey("REF")) {
            try { layer.setRef(Integer.parseInt(data.get("REF"))); } catch (NumberFormatException ignored) {}
        }
        return layer;
    }

    private Map<String, String> parseBlock(BufferedReader reader) throws IOException {
        Map<String, String> data = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.equals("}")) {
                break;
            }
            String[] parts = line.split("=", 2);
            if (parts.length == 2) {
                data.put(parts[0].trim(), parts[1].trim());
            }
        }
        return data;
    }
}

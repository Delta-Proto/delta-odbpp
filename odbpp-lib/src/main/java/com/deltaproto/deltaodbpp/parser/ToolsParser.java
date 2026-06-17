package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.Tool;
import com.deltaproto.deltaodbpp.model.Tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for ODB++ tools files.
 * The tools file defines drill tool characteristics for drill and rout layers.
 */
public class ToolsParser {
    private static final Pattern UNITS_PATTERN = Pattern.compile(
            "^(?:UNITS=|U\\s+)(.+)$");
    private static final Pattern THICKNESS_PATTERN = Pattern.compile("^THICKNESS=([\\d.]+)$");
    private static final Pattern USER_PARAMS_PATTERN = Pattern.compile("^USER_PARAMS=(.*)$");
    private static final Pattern TOOLS_START_PATTERN = Pattern.compile("^TOOLS\\s*\\{\\s*$");
    private static final Pattern TOOLS_END_PATTERN = Pattern.compile("^}\\s*$");
    private static final Pattern FIELD_PATTERN = Pattern.compile("^\\s*([A-Z_0-9]+)=(.*)$");

    /** Default entry: derive scaling from the file's own UNITS directive (assume MM if absent). */
    public Tools parse(Path toolsFile) throws IOException {
        return parse(toolsFile, 1.0);
    }

    /**
     * Parse a tools file, converting all sizes to millimetres.
     *
     * Per the ODB++ spec, tool sizes (MIN_TOL, MAX_TOL, FINISH_SIZE,
     * DRILL_SIZE) are in <b>mils or microns</b> — not inches or mm — while the
     * obsolete THICKNESS field is in mils or mm. The file's own {@code UNITS=}
     * directive decides which (it is normally present); the step-level
     * {@code mmScale} (25.4 for INCH jobs, 1.0 for MM jobs) is the fallback.
     */
    public Tools parse(Path toolsFile, double mmScale) throws IOException {
        Tools tools = new Tools();
        List<String> lines = Files.readAllLines(toolsFile, StandardCharsets.ISO_8859_1);

        Tool currentTool = null;
        boolean inToolsBlock = false;
        // mils->mm or microns->mm for tool sizes; mils->mm or identity for thickness
        double sizeScale = mmScale / 1000.0;
        double thicknessScale = mmScale == 1.0 ? 1.0 : 0.0254;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // Parse header fields. The UNITS line, if present, overrides the step-level scale.
            Matcher unitsMatcher = UNITS_PATTERN.matcher(line);
            if (unitsMatcher.matches()) {
                String units = unitsMatcher.group(1);
                tools.setUnits(units);
                boolean mm = "MM".equalsIgnoreCase(units);
                sizeScale = mm ? 0.001 : 0.0254;
                thicknessScale = mm ? 1.0 : 0.0254;
                continue;
            }

            Matcher thicknessMatcher = THICKNESS_PATTERN.matcher(line);
            if (thicknessMatcher.matches()) {
                tools.setThickness(Double.parseDouble(thicknessMatcher.group(1)) * thicknessScale);
                continue;
            }

            Matcher userParamsMatcher = USER_PARAMS_PATTERN.matcher(line);
            if (userParamsMatcher.matches()) {
                tools.setUserParams(userParamsMatcher.group(1));
                continue;
            }

            // Handle TOOLS block start
            if (TOOLS_START_PATTERN.matcher(line).matches() || line.equals("TOOLS {")) {
                inToolsBlock = true;
                currentTool = new Tool();
                continue;
            }

            // Handle TOOLS block end
            if (TOOLS_END_PATTERN.matcher(line).matches()) {
                if (currentTool != null) {
                    tools.getTools().add(currentTool);
                    currentTool = null;
                }
                inToolsBlock = false;
                continue;
            }

            // Parse fields within TOOLS block
            if (inToolsBlock) {
                Matcher fieldMatcher = FIELD_PATTERN.matcher(line);
                if (fieldMatcher.matches()) {
                    String fieldName = fieldMatcher.group(1);
                    String fieldValue = fieldMatcher.group(2).trim();
                    parseToolField(currentTool, fieldName, fieldValue, sizeScale);
                }
            }
        }

        return tools;
    }

    private void parseToolField(Tool tool, String fieldName, String fieldValue, double mmScale) {
        switch (fieldName) {
            case "NUM":
                tool.setNum(Integer.parseInt(fieldValue));
                break;
            case "TYPE":
                tool.setType(parseToolType(fieldValue));
                break;
            case "TYPE2":
                tool.setType2(parseToolType2(fieldValue));
                break;
            case "MIN_TOL":
                tool.setMinTol(scaleIfPresent(parseDouble(fieldValue), mmScale));
                break;
            case "MAX_TOL":
                tool.setMaxTol(scaleIfPresent(parseDouble(fieldValue), mmScale));
                break;
            case "BIT":
                tool.setBit(fieldValue);
                break;
            case "FINISH_SIZE":
                tool.setFinishSize(scaleIfPresent(parseDouble(fieldValue), mmScale));
                break;
            case "DRILL_SIZE":
                tool.setDrillSize(scaleIfPresent(parseDouble(fieldValue), mmScale));
                break;
        }
    }

    /** {@link #parseDouble} returns -1 as a sentinel for missing values; preserve that. */
    private static double scaleIfPresent(double value, double mmScale) {
        return value < 0 ? value : value * mmScale;
    }

    private Tool.ToolType parseToolType(String value) {
        return switch (value.toUpperCase()) {
            case "PLATED" -> Tool.ToolType.PLATED;
            case "NON_PLATED" -> Tool.ToolType.NON_PLATED;
            case "VIA" -> Tool.ToolType.VIA;
            default -> Tool.ToolType.PLATED;
        };
    }

    private Tool.ToolType2 parseToolType2(String value) {
        return switch (value.toUpperCase()) {
            case "PRESS_FIT" -> Tool.ToolType2.PRESS_FIT;
            case "PHOTO" -> Tool.ToolType2.PHOTO;
            case "LASER" -> Tool.ToolType2.LASER;
            default -> Tool.ToolType2.STANDARD;
        };
    }

    private double parseDouble(String value) {
        if (value == null || value.isEmpty()) {
            return -1;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

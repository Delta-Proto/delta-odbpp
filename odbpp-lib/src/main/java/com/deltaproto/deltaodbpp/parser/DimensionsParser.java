package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.Dimensions;
import com.deltaproto.deltaodbpp.model.Dimensions.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Parser for ODB++ dimensions files.
 * Parses measurement annotations from layer dimensions files.
 */
public class DimensionsParser {

    public Dimensions parse(Path dimensionsFile) throws IOException {
        Dimensions dimensions = new Dimensions();

        try (BufferedReader reader = Files.newBufferedReader(dimensionsFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("VERSION=")) {
                    dimensions.setVersion(Integer.parseInt(line.substring(8)));
                } else if (line.startsWith("UNITS=")) {
                    dimensions.setUnits(line.substring(6));
                } else if (line.startsWith("PARAMETERS {")) {
                    dimensions.setParameters(parseParameters(reader));
                } else if (line.startsWith("DIMENSION {")) {
                    dimensions.getDimensions().add(parseDimension(reader));
                }
            }
        }
        return dimensions;
    }

    private DimensionParameters parseParameters(BufferedReader reader) throws IOException {
        DimensionParameters params = new DimensionParameters();
        Map<String, String> data = new HashMap<>();
        String line;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.equals("}")) {
                break;
            }
            if (line.startsWith("PAPER {")) {
                params.setPaper(parsePaper(reader));
            } else if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                data.put(parts[0].trim(), parts[1].trim());
            }
        }

        // Set parameters from data map
        if (data.containsKey("ID")) params.setId(Integer.parseInt(data.get("ID")));
        if (data.containsKey("LINE_WIDTH")) params.setLineWidth(Double.parseDouble(data.get("LINE_WIDTH")));
        if (data.containsKey("POST_DECIMAL_DIST")) params.setPostDecimalDist(Integer.parseInt(data.get("POST_DECIMAL_DIST")));
        if (data.containsKey("POST_DECIMAL_POS")) params.setPostDecimalPos(Integer.parseInt(data.get("POST_DECIMAL_POS")));
        if (data.containsKey("POST_DECIMAL_ANGLE")) params.setPostDecimalAngle(Integer.parseInt(data.get("POST_DECIMAL_ANGLE")));
        if (data.containsKey("FONT")) params.setFont(data.get("FONT"));
        if (data.containsKey("FONT_WIDTH")) params.setFontWidth(Double.parseDouble(data.get("FONT_WIDTH")));
        if (data.containsKey("FONT_HEIGHT")) params.setFontHeight(Double.parseDouble(data.get("FONT_HEIGHT")));
        if (data.containsKey("EXT_OVERLEN")) params.setExtOverlen(Double.parseDouble(data.get("EXT_OVERLEN")));
        if (data.containsKey("EXT_OFFSET")) params.setExtOffset(Double.parseDouble(data.get("EXT_OFFSET")));
        if (data.containsKey("CENTER_MARKER_LEN")) params.setCenterMarkerLen(Double.parseDouble(data.get("CENTER_MARKER_LEN")));
        if (data.containsKey("BASELINE_SPACING")) params.setBaselineSpacing(Double.parseDouble(data.get("BASELINE_SPACING")));
        if (data.containsKey("ORIGIN_X")) params.setOriginX(Double.parseDouble(data.get("ORIGIN_X")));
        if (data.containsKey("ORIGIN_Y")) params.setOriginY(Double.parseDouble(data.get("ORIGIN_Y")));
        if (data.containsKey("SCALE")) params.setScale(Double.parseDouble(data.get("SCALE")));

        return params;
    }

    private Paper parsePaper(BufferedReader reader) throws IOException {
        Paper paper = new Paper();
        Map<String, String> data = new HashMap<>();
        String line;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.equals("}")) {
                break;
            }
            if (line.startsWith("MARGIN {")) {
                paper.setMargin(parseMargin(reader));
            } else if (line.startsWith("ACTIVE {")) {
                paper.setActive(parseActiveArea(reader));
            } else if (line.startsWith("COLOR {")) {
                paper.setColor(parseColors(reader));
            } else if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                data.put(parts[0].trim(), parts[1].trim());
            }
        }

        if (data.containsKey("ORIENTATION")) paper.setOrientation(data.get("ORIENTATION"));
        if (data.containsKey("SIZE")) paper.setSize(data.get("SIZE"));
        if (data.containsKey("WIDTH")) paper.setWidth(Double.parseDouble(data.get("WIDTH")));
        if (data.containsKey("HEIGHT")) paper.setHeight(Double.parseDouble(data.get("HEIGHT")));
        if (data.containsKey("X")) paper.setX(Double.parseDouble(data.get("X")));
        if (data.containsKey("Y")) paper.setY(Double.parseDouble(data.get("Y")));

        return paper;
    }

    private Margin parseMargin(BufferedReader reader) throws IOException {
        Margin margin = new Margin();
        Map<String, String> data = parseBlock(reader);
        if (data.containsKey("TOP")) margin.setTop(Double.parseDouble(data.get("TOP")));
        if (data.containsKey("BOTTOM")) margin.setBottom(Double.parseDouble(data.get("BOTTOM")));
        if (data.containsKey("LEFT")) margin.setLeft(Double.parseDouble(data.get("LEFT")));
        if (data.containsKey("RIGHT")) margin.setRight(Double.parseDouble(data.get("RIGHT")));
        return margin;
    }

    private ActiveArea parseActiveArea(BufferedReader reader) throws IOException {
        ActiveArea active = new ActiveArea();
        Map<String, String> data = parseBlock(reader);
        if (data.containsKey("X00")) active.setX00(Double.parseDouble(data.get("X00")));
        if (data.containsKey("Y00")) active.setY00(Double.parseDouble(data.get("Y00")));
        if (data.containsKey("X11")) active.setX11(Double.parseDouble(data.get("X11")));
        if (data.containsKey("Y11")) active.setY11(Double.parseDouble(data.get("Y11")));
        return active;
    }

    private Colors parseColors(BufferedReader reader) throws IOException {
        Colors colors = new Colors();
        Map<String, String> data = parseBlock(reader);
        if (data.containsKey("FEATURE")) colors.setFeature(data.get("FEATURE"));
        if (data.containsKey("DIMENS")) colors.setDimens(data.get("DIMENS"));
        if (data.containsKey("DIMENS_TEXT")) colors.setDimensText(data.get("DIMENS_TEXT"));
        if (data.containsKey("PROFILE")) colors.setProfile(data.get("PROFILE"));
        if (data.containsKey("TEMPLATE")) colors.setTemplate(data.get("TEMPLATE"));
        return colors;
    }

    private Dimension parseDimension(BufferedReader reader) throws IOException {
        Dimension dim = new Dimension();
        Map<String, String> data = new HashMap<>();
        String line;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.equals("}")) {
                break;
            }
            if (line.startsWith("TEXT {")) {
                dim.setText(parseDimensionText(reader));
            } else if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                data.put(parts[0].trim(), parts[1].trim());
            }
        }

        if (data.containsKey("TYPE")) dim.setType(DimensionType.valueOf(data.get("TYPE")));
        if (data.containsKey("PARAMETERS")) dim.setParametersId(Integer.parseInt(data.get("PARAMETERS")));
        if (data.containsKey("REF1X")) dim.setRef1X(Double.parseDouble(data.get("REF1X")));
        if (data.containsKey("REF1Y")) dim.setRef1Y(Double.parseDouble(data.get("REF1Y")));
        if (data.containsKey("REF2X")) dim.setRef2X(Double.parseDouble(data.get("REF2X")));
        if (data.containsKey("REF2Y")) dim.setRef2Y(Double.parseDouble(data.get("REF2Y")));
        if (data.containsKey("REF3X")) dim.setRef3X(Double.parseDouble(data.get("REF3X")));
        if (data.containsKey("REF3Y")) dim.setRef3Y(Double.parseDouble(data.get("REF3Y")));
        if (data.containsKey("LINE_PT_X")) dim.setLinePtX(Double.parseDouble(data.get("LINE_PT_X")));
        if (data.containsKey("LINE_PT_Y")) dim.setLinePtY(Double.parseDouble(data.get("LINE_PT_Y")));
        if (data.containsKey("OFFSET")) dim.setOffset(Double.parseDouble(data.get("OFFSET")));
        if (data.containsKey("ARROW_POS")) dim.setArrowPos(data.get("ARROW_POS"));
        if (data.containsKey("MAGNIFY")) dim.setMagnify(Double.parseDouble(data.get("MAGNIFY")));
        if (data.containsKey("TO_ARC_CENTER")) dim.setToArcCenter("YES".equals(data.get("TO_ARC_CENTER")));
        if (data.containsKey("TWO_SIDED_DIAM")) dim.setTwoSidedDiam("YES".equals(data.get("TWO_SIDED_DIAM")));

        return dim;
    }

    private DimensionText parseDimensionText(BufferedReader reader) throws IOException {
        DimensionText text = new DimensionText();
        Map<String, String> data = parseBlock(reader);

        if (data.containsKey("VALUE")) text.setValue(data.get("VALUE"));
        if (data.containsKey("PREFIX")) text.setPrefix(data.get("PREFIX"));
        if (data.containsKey("SUFFIX")) text.setSuffix(data.get("SUFFIX"));
        if (data.containsKey("NOTE")) text.setNote(data.get("NOTE"));
        if (data.containsKey("UNITS")) text.setUnits(data.get("UNITS"));
        if (data.containsKey("VIEW_UNITS")) text.setViewUnits("YES".equals(data.get("VIEW_UNITS")));
        if (data.containsKey("OUTSIDE")) text.setOutside("YES".equals(data.get("OUTSIDE")));
        if (data.containsKey("UNDERLINE")) text.setUnderline("YES".equals(data.get("UNDERLINE")));
        if (data.containsKey("TOL_UP")) text.setTolUp(data.get("TOL_UP"));
        if (data.containsKey("TOL_DOWN")) text.setTolDown(data.get("TOL_DOWN"));
        if (data.containsKey("MERGE_TOL")) text.setMergeTol("YES".equals(data.get("MERGE_TOL")));
        if (data.containsKey("X")) text.setX(Double.parseDouble(data.get("X")));
        if (data.containsKey("Y")) text.setY(Double.parseDouble(data.get("Y")));
        if (data.containsKey("ANGLE")) text.setAngle(Double.parseDouble(data.get("ANGLE")));

        return text;
    }

    private Map<String, String> parseBlock(BufferedReader reader) throws IOException {
        Map<String, String> data = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.equals("}")) {
                break;
            }
            if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                data.put(parts[0].trim(), parts[1].trim());
            }
        }
        return data;
    }
}

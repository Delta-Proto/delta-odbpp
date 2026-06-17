package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FeaturesFileParser {
    // Coordinate pattern that supports negative numbers
    private static final String COORD = "(-?[\\d.]+)";

    // Pad format: P x y apt_def [P|N] orient [dcode] [;attr=val...]
    // Extra fields may be present, so don't require $ at end
    private static final Pattern PAD_PATTERN = Pattern.compile(
            "^P\\s+" + COORD + "\\s+" + COORD + "\\s+(\\d+)\\s+([PN])\\s+(\\d+)(?:\\s+(\\d+))?(?:.*?;(.*))?");
    // Line format: L xs ys xe ye apt_def [P|N] [dcode] [;attr=val...]
    private static final Pattern LINE_PATTERN = Pattern.compile(
            "^L\\s+" + COORD + "\\s+" + COORD + "\\s+" + COORD + "\\s+" + COORD + "\\s+(\\d+)(?:\\s+([PN]))?(?:\\s+(\\d+))?(?:.*?;(.*))?");
    // Arc format: A xs ys xe ye xc yc apt_def [P|N] [dcode] [Y|N] [;attr=val...]
    private static final Pattern ARC_PATTERN = Pattern.compile(
            "^A\\s+" + COORD + "\\s+" + COORD + "\\s+" + COORD + "\\s+" + COORD + "\\s+" + COORD + "\\s+" + COORD + "\\s+(\\d+)(?:\\s+([PN]))?(?:\\s+(\\d+))?(?:\\s+([YN]))?(?:.*?;(.*))?");
    private static final Pattern TEXT_PATTERN = Pattern.compile(
            "^T\\s+" + COORD + "\\s+" + COORD + "\\s+'(.*)'\\s+(\\d+)\\s+(\\d+)\\s+([YN])(?:\\s*;(.*))?\\s*$");
    private static final Pattern BARCODE_PATTERN = Pattern.compile(
            "^B\\s+" + COORD + "\\s+" + COORD + "\\s+(\\S+)\\s+(\\S+)\\s+([PN])\\s+(\\d+)(?:\\s+([\\d.]+))?\\s+E\\s+" + COORD + "\\s+" + COORD + "\\s+([YN])\\s+([YN])\\s+([YN])\\s+([YN])\\s+([TB])\\s+'(.*?)'(?:;(\\d+)=([^;]*))?(?:;\\d+=[^;]*)*(?:;ID=(.*?))?\\s*$");
    private static final Pattern SURFACE_PATTERN = Pattern.compile(
            "^S\\s+([PN])\\s+(\\d+)\\s*(?:;(.*))?\\s*$");

    // Symbol definition: $<num> <name> [M|I]
    private static final Pattern SYMBOL_DEF_PATTERN = Pattern.compile(
            "^\\$(\\d+)\\s+(\\S+)(?:\\s+([MI]))?\\s*$");

    // Feature attribute name: @<num> <name>
    private static final Pattern ATTR_NAME_PATTERN = Pattern.compile(
            "^@(\\d+)\\s+(.+)\\s*$");

    // Feature attribute text string: &<num> <text>
    private static final Pattern ATTR_TEXT_PATTERN = Pattern.compile(
            "^&(\\d+)\\s+(.+)\\s*$");

    // Attribute parsing pattern for ;attr=value;attr=value;ID=xxx
    private static final Pattern ATTR_VALUE_PATTERN = Pattern.compile(
            "(\\d+)=([^;,]+)|ID=([^;,]+)");

    // Inline units directive in a features file:
    //   UNITS=MM   (Altium / KiCad style)
    //   U MM       (Mentor Xpedition style — bare "U <units>")
    private static final Pattern UNITS_PATTERN = Pattern.compile(
            "^(?:UNITS\\s*=\\s*|U\\s+)(MM|INCH)\\s*$", Pattern.CASE_INSENSITIVE);

    private final SurfaceParser surfaceParser = new SurfaceParser();

    /**
     * Scale factor applied to every coordinate during the current parse pass:
     * 25.4 for an INCH-native step, 1.0 for an MM-native step. Set per call
     * via {@link #parse(Path, double)} so the model lands in millimetres
     * regardless of the archive's native unit.
     */
    private double mmScale = 1.0;

    /** Default entry: assume coords are already in mm (no conversion). */
    public Features parse(Path featuresFile) throws IOException {
        return parse(featuresFile, 1.0);
    }

    /**
     * Parse a features file, multiplying every coordinate by {@code mmScale}
     * so the in-memory model is millimetres throughout.
     *
     * @param mmScale 25.4 if the step is INCH-native, 1.0 if MM-native.
     */
    public Features parse(Path featuresFile, double mmScale) throws IOException {
        this.mmScale = mmScale;
        Features features = new Features();
        List<String> lines = Files.readAllLines(featuresFile, StandardCharsets.ISO_8859_1);

        // First pass: honour an inline UNITS / "U MM" directive if present, so it
        // overrides the step-level scale before any coordinate-bearing line is
        // processed. ODB++ archives sometimes (e.g. Mentor Xpedition exports) carry
        // no UNITS in stephdr but declare them per-features-file.
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            Matcher m = UNITS_PATTERN.matcher(trimmed);
            if (m.matches()) {
                String unit = m.group(1).toUpperCase();
                features.setUnits(unit);
                this.mmScale = "MM".equalsIgnoreCase(unit) ? 1.0 : 25.4;
                break; // assume only one UNITS directive per file
            }
            // Stop scanning once we hit a feature/symbol line — UNITS is always
            // declared near the top of the file.
            char c = trimmed.charAt(0);
            if (c == '$' || c == '@' || c == '&' || c == 'P' || c == 'L'
                    || c == 'A' || c == 'S' || c == 'T' || c == 'B') break;
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("UNITS") || line.startsWith("U ")) {
                parseUnits(line, features);
            } else if (line.startsWith("$")) {
                parseSymbolDefinition(line, features);
            } else if (line.startsWith("@")) {
                parseAttributeName(line, features);
            } else if (line.startsWith("&")) {
                parseAttributeTextString(line, features);
            } else if (line.startsWith("P")) {
                parsePad(line, features);
            } else if (line.startsWith("L")) {
                parseLine(line, features);
            } else if (line.startsWith("A")) {
                parseArc(line, features);
            } else if (line.startsWith("S")) {
                parseSurface(line, features, lines, i);
            } else if (line.startsWith("T")) {
                parseText(line, features);
            } else if (line.startsWith("B")) {
                parseBarcode(line, features);
            }
        }
        return features;
    }

    private void parseUnits(String line, Features features) {
        Matcher matcher = UNITS_PATTERN.matcher(line);
        if (matcher.find()) {
            features.setUnits(matcher.group(1).toUpperCase());
        }
    }

    private void parseSymbolDefinition(String line, Features features) {
        Matcher matcher = SYMBOL_DEF_PATTERN.matcher(line);
        if (matcher.find()) {
            int symbolNumber = Integer.parseInt(matcher.group(1));
            String symbolName = matcher.group(2);
            String mirrorFlag = matcher.group(3);

            features.getSymbolTable().put(symbolNumber, symbolName);

            // M = mirrored, I = not mirrored (default)
            if ("M".equals(mirrorFlag)) {
                features.getSymbolMirrorFlags().put(symbolNumber, true);
            }
        } else {
            // Fallback to simple parsing for edge cases
            String[] parts = line.substring(1).split("\\s+", 2);
            if (parts.length >= 2) {
                features.getSymbolTable().put(Integer.parseInt(parts[0]), parts[1]);
            }
        }
    }

    private void parseAttributeName(String line, Features features) {
        Matcher matcher = ATTR_NAME_PATTERN.matcher(line);
        if (matcher.find()) {
            int attrNumber = Integer.parseInt(matcher.group(1));
            String attrName = matcher.group(2).trim();
            features.getAttributeNames().put(attrNumber, attrName);
        }
    }

    private void parseAttributeTextString(String line, Features features) {
        Matcher matcher = ATTR_TEXT_PATTERN.matcher(line);
        if (matcher.find()) {
            int stringNumber = Integer.parseInt(matcher.group(1));
            String textValue = matcher.group(2).trim();
            features.getAttributeTextStrings().put(stringNumber, textValue);
        }
    }

    /**
     * Parse attribute string like "3=2,4=0;ID=123456" or "1,3=0"
     */
    private void parseAttributes(String attrString, Map<Integer, String> attributes, StringBuilder uniqueId) {
        if (attrString == null || attrString.isEmpty()) {
            return;
        }

        Matcher matcher = ATTR_VALUE_PATTERN.matcher(attrString);
        while (matcher.find()) {
            if (matcher.group(3) != null) {
                // ID=xxx
                uniqueId.append(matcher.group(3));
            } else if (matcher.group(1) != null && matcher.group(2) != null) {
                // num=value
                int attrNum = Integer.parseInt(matcher.group(1));
                String attrVal = matcher.group(2);
                attributes.put(attrNum, attrVal);
            }
        }

        // Also handle bare attribute numbers like ";1,3=0" meaning attr 1 is set (boolean true)
        String[] parts = attrString.split("[;,]");
        for (String part : parts) {
            part = part.trim();
            if (part.matches("\\d+") && !part.isEmpty()) {
                // Bare number = boolean attribute set to true
                attributes.put(Integer.parseInt(part), "true");
            }
        }
    }

    private void parsePad(String line, Features features) {
        Matcher matcher = PAD_PATTERN.matcher(line);
        if (matcher.find()) {
            Pad pad = new Pad();
            pad.setX(Double.parseDouble(matcher.group(1)) * mmScale);
            pad.setY(Double.parseDouble(matcher.group(2)) * mmScale);
            pad.setSymbolNumber(Integer.parseInt(matcher.group(3)));
            // Polarity is in group 4
            String polarity = matcher.group(4);
            pad.setPolarity(polarity);
            // Orientation is in group 5 - store as orientationType (0-7) or custom rotation (8/9)
            double rotation = Double.parseDouble(matcher.group(5));
            if (rotation < 8) {
                pad.setOrientationType((int) rotation);
            } else {
                pad.setOrientationType((int) rotation);
                pad.setCustomRotation(rotation);
            }
            // Dcode in group 6 (optional)
            if (matcher.group(6) != null) {
                pad.setDcode(Integer.parseInt(matcher.group(6)));
            }
            // Attributes in group 7 (optional)
            if (matcher.group(7) != null) {
                StringBuilder uniqueId = new StringBuilder();
                parseAttributes(matcher.group(7), pad.getAttributes(), uniqueId);
                if (uniqueId.length() > 0) {
                    pad.setUniqueId(uniqueId.toString());
                }
            }
            features.getFeatures().add(pad);
        }
    }

    private void parseLine(String line, Features features) {
        Matcher matcher = LINE_PATTERN.matcher(line);
        if (matcher.find()) {
            Line lineFeature = new Line();
            lineFeature.setXs(Double.parseDouble(matcher.group(1)) * mmScale);
            lineFeature.setYs(Double.parseDouble(matcher.group(2)) * mmScale);
            lineFeature.setXe(Double.parseDouble(matcher.group(3)) * mmScale);
            lineFeature.setYe(Double.parseDouble(matcher.group(4)) * mmScale);
            lineFeature.setSymbolNumber(Integer.parseInt(matcher.group(5)));
            // Polarity in group 6 (optional)
            if (matcher.group(6) != null) {
                lineFeature.setPolarity(Polarity.fromString(matcher.group(6)));
            }
            // Dcode in group 7 (optional)
            if (matcher.group(7) != null) {
                lineFeature.setDcode(Integer.parseInt(matcher.group(7)));
            }
            // Attributes in group 8 (optional)
            if (matcher.group(8) != null) {
                StringBuilder uniqueId = new StringBuilder();
                Map<Integer, String> attrs = new HashMap<>();
                parseAttributes(matcher.group(8), attrs, uniqueId);
                if (uniqueId.length() > 0) {
                    lineFeature.setUniqueId(uniqueId.toString());
                }
                if (!attrs.isEmpty()) {
                    lineFeature.setAttributeNumber(attrs.keySet().iterator().next());
                    lineFeature.setAttributeValue(attrs.values().iterator().next());
                }
            }
            features.getFeatures().add(lineFeature);
        }
    }

    private void parseArc(String line, Features features) {
        Matcher matcher = ARC_PATTERN.matcher(line);
        if (matcher.find()) {
            Arc arc = new Arc();
            arc.setXs(Double.parseDouble(matcher.group(1)) * mmScale);
            arc.setYs(Double.parseDouble(matcher.group(2)) * mmScale);
            arc.setXe(Double.parseDouble(matcher.group(3)) * mmScale);
            arc.setYe(Double.parseDouble(matcher.group(4)) * mmScale);
            arc.setXc(Double.parseDouble(matcher.group(5)) * mmScale);
            arc.setYc(Double.parseDouble(matcher.group(6)) * mmScale);
            arc.setSymbolNumber(Integer.parseInt(matcher.group(7)));
            // Polarity in group 8 (optional)
            if (matcher.group(8) != null) {
                arc.setPolarity(Polarity.fromString(matcher.group(8)));
            }
            // Dcode in group 9 (optional)
            if (matcher.group(9) != null) {
                arc.setDcode(Integer.parseInt(matcher.group(9)));
            }
            // Clockwise in group 10 (optional)
            if (matcher.group(10) != null) {
                arc.setCw(matcher.group(10));
            }
            // Attributes in group 11 (optional)
            if (matcher.group(11) != null) {
                StringBuilder uniqueId = new StringBuilder();
                Map<Integer, String> attrs = new HashMap<>();
                parseAttributes(matcher.group(11), attrs, uniqueId);
                if (uniqueId.length() > 0) {
                    arc.setUniqueId(uniqueId.toString());
                }
            }
            features.getFeatures().add(arc);
        }
    }

    private void parseSurface(String line, Features features, List<String> lines, int currentIndex) throws IOException {
        Matcher matcher = SURFACE_PATTERN.matcher(line);
        if (matcher.find()) {
            Surface surface = new Surface();

            // Parse polarity
            String polarityStr = matcher.group(1);
            surface.setPolarity(Polarity.fromString(polarityStr));

            // Parse dcode
            int dcode = Integer.parseInt(matcher.group(2));
            surface.setDcode(dcode);

            // Parse attributes if present (group 3)
            if (matcher.group(3) != null) {
                StringBuilder uniqueId = new StringBuilder();
                parseAttributes(matcher.group(3), surface.getAttributes(), uniqueId);
                if (uniqueId.length() > 0) {
                    surface.setUniqueId(uniqueId.toString());
                }
            }

            features.getFeatures().add(surface);
            surfaceParser.parse(lines, currentIndex, surface, mmScale);
        }
    }

    private void parseText(String line, Features features) {
        Matcher matcher = TEXT_PATTERN.matcher(line);
        if (matcher.find()) {
            Text text = new Text();
            text.setX(Double.parseDouble(matcher.group(1)) * mmScale);
            text.setY(Double.parseDouble(matcher.group(2)) * mmScale);
            text.setText(matcher.group(3));
            // Store font from group 4 (if present)
            if (matcher.groupCount() >= 4 && matcher.group(4) != null) {
                text.setFont(matcher.group(4));
            }
            // Store orientation as orientDef string
            if (matcher.groupCount() >= 5 && matcher.group(5) != null) {
                text.setOrientDef(matcher.group(5));
            }
            features.getFeatures().add(text);
        }
    }

    private void parseBarcode(String line, Features features) {
        Matcher matcher = BARCODE_PATTERN.matcher(line);
        if (matcher.find()) {
            Barcode barcode = new Barcode();
            
            // Parse x, y coordinates
            barcode.setX(Double.parseDouble(matcher.group(1)) * mmScale);
            barcode.setY(Double.parseDouble(matcher.group(2)) * mmScale);
            
            // Parse barcode name
            barcode.setBarcodeName(matcher.group(3));
            
            // Parse font
            barcode.setFont(matcher.group(4));
            
            // Parse polarity
            String polarityStr = matcher.group(5);
            barcode.setPolarity(Polarity.fromString(polarityStr));
            
            // Parse orientation definition
            barcode.setOrientDef(Integer.parseInt(matcher.group(6)));
            
            // Parse rotation if present (for orient_def 8 or 9)
            if (matcher.group(7) != null) {
                barcode.setOrientDefRotation(Double.parseDouble(matcher.group(7)));
            }
            
            // Parse width and height (physical dimensions)
            barcode.setWidth(Double.parseDouble(matcher.group(8)) * mmScale);
            barcode.setHeight(Double.parseDouble(matcher.group(9)) * mmScale);
            
            // Parse flags
            barcode.setFullAscii(matcher.group(10));
            barcode.setChecksum(matcher.group(11));
            barcode.setBackground(matcher.group(12));
            barcode.setAdditionalString(matcher.group(13));
            
            // Parse additional string position
            barcode.setAdditionalStringPosition(matcher.group(14));
            
            // Parse text string
            barcode.setText(matcher.group(15));
            
            // Parse attributes if present
            if (matcher.group(16) != null && matcher.group(17) != null) {
                int attrNumber = Integer.parseInt(matcher.group(16));
                String attrValue = matcher.group(17);
                barcode.setAtr(attrNumber);
                barcode.setValue(attrValue);
            }
            
            // Parse unique ID if present
            if (matcher.group(18) != null) {
                barcode.setUniqueId(matcher.group(18));
            }
            
            features.getFeatures().add(barcode);
        }
    }
}
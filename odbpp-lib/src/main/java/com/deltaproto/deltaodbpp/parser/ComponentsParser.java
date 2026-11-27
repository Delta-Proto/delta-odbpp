package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.Component;
import com.deltaproto.deltaodbpp.model.ComponentAttribute;
import com.deltaproto.deltaodbpp.model.Components;
import com.deltaproto.deltaodbpp.model.MirrorType;
import com.deltaproto.deltaodbpp.model.PropertyRecord;
import com.deltaproto.deltaodbpp.model.ToeprintRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ComponentsParser {
    // CMP pattern: CMP <pkg_ref> <x> <y> <rot> <mirror> <comp_name> <part_name>
    private static final Pattern CMP_PATTERN = Pattern.compile(
            "^CMP\\s+(\\d+)\\s+([\\d.-]+)\\s+([\\d.-]+)\\s+([\\d.-]+)\\s+([NM])\\s+(\\S+)\\s+(\\S+)");
    private static final Pattern UNITS_PATTERN = Pattern.compile(
            "^(?:UNITS\\s*=\\s*|U\\s+)(MM|INCH)\\s*$", Pattern.CASE_INSENSITIVE);
    /** {@code @n name} (attribute name) and {@code &n text} (attribute text) table lines. */
    private static final Pattern TABLE_PATTERN = Pattern.compile("^([@&])(\\d+)\\s+(.*)$");
    private static final Pattern PRP_PATTERN = Pattern.compile("^PRP\\s+(\\S+)\\s+'(.*)'");

    /** Default entry: assume coords are already in mm. */
    public Components parse(Path componentsFile) throws IOException {
        return parse(componentsFile, 1.0);
    }

    /**
     * @param mmScale the step's unit as a multiplier to mm (25.4 for INCH, 1 for MM); the file's
     *                own {@code UNITS} line, when it has one, takes precedence
     */
    public Components parse(Path componentsFile, double mmScale) throws IOException {
        Components components = new Components();
        List<String> lines = Files.readAllLines(componentsFile, StandardCharsets.ISO_8859_1);
        Component currentComponent = null;

        // The file may declare its own unit, as features files do; it covers positions and
        // length attributes such as .comp_height alike.
        for (String line : lines) {
            Matcher m = UNITS_PATTERN.matcher(line.trim());
            if (m.matches()) {
                mmScale = "MM".equalsIgnoreCase(m.group(1)) ? 1.0 : 25.4;
                break;
            }
            if (line.startsWith("CMP")) break;
        }
        components.setUnits(mmScale == 1.0 ? "MM" : "INCH");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            Matcher tableMatcher = TABLE_PATTERN.matcher(line);
            if (tableMatcher.matches()) {
                (tableMatcher.group(1).equals("@") ? components.getAttributeNames() : components.getAttributeTexts())
                        .put(Integer.parseInt(tableMatcher.group(2)), tableMatcher.group(3).trim());
                continue;
            }

            Matcher cmpMatcher = CMP_PATTERN.matcher(line);
            if (cmpMatcher.find()) {
                currentComponent = new Component();
                currentComponent.setPkgRef(Integer.parseInt(cmpMatcher.group(1)));
                currentComponent.setX(Double.parseDouble(cmpMatcher.group(2)) * mmScale);
                currentComponent.setY(Double.parseDouble(cmpMatcher.group(3)) * mmScale);
                currentComponent.setRotation(Double.parseDouble(cmpMatcher.group(4)));
                currentComponent.setMirror(MirrorType.fromString(cmpMatcher.group(5)));
                currentComponent.setCompName(cmpMatcher.group(6));
                String partName = cmpMatcher.group(7);
                int semi = partName.indexOf(';');
                currentComponent.setPartName(semi >= 0 ? partName.substring(0, semi) : partName);
                int attrStart = line.indexOf(';', cmpMatcher.start(7));
                if (attrStart >= 0) attributes(line.substring(attrStart + 1), currentComponent);
                components.getComponents().add(currentComponent);
                continue;
            }

            Matcher prpMatcher = PRP_PATTERN.matcher(line);
            if (prpMatcher.find() && currentComponent != null) {
                PropertyRecord prp = new PropertyRecord();
                prp.setName(prpMatcher.group(1));
                prp.setValue(prpMatcher.group(2));
                currentComponent.addPropertyRecord(prp);
                continue;
            }

            if (line.startsWith("TOP ") && currentComponent != null) {
                ToeprintRecord top = toeprint(line, mmScale);
                if (top != null) currentComponent.addToeprintRecord(top);
            }
        }
        resolveHeights(components, mmScale);
        return components;
    }

    /**
     * The tail of a CMP line after its first {@code ;}: comma-separated attribute assignments
     * ({@code n} or {@code n=value}), then optionally {@code ;ID=<id>}.
     */
    private static void attributes(String tail, Component component) {
        for (String section : tail.split(";")) {
            section = section.trim();
            if (section.isEmpty()) continue;
            if (section.startsWith("ID=")) {
                component.setUniqueId(section.substring(3).trim());
                continue;
            }
            for (String assignment : section.split(",")) {
                assignment = assignment.trim();
                int eq = assignment.indexOf('=');
                try {
                    if (eq < 0) {
                        component.addAttribute(ComponentAttribute.createBoolean(Integer.parseInt(assignment)));
                    } else {
                        ComponentAttribute attribute = new ComponentAttribute();
                        attribute.setAttributeIndex(Integer.parseInt(assignment.substring(0, eq).trim()));
                        attribute.setValue(assignment.substring(eq + 1).trim());
                        component.addAttribute(attribute);
                    }
                } catch (NumberFormatException e) {
                    // Not an attribute assignment; skip it.
                }
            }
        }
    }

    /** {@code .comp_height} is a length in the file's unit; convert it to mm per component. */
    private static void resolveHeights(Components components, double mmScale) {
        Integer index = null;
        for (Map.Entry<Integer, String> e : components.getAttributeNames().entrySet()) {
            if (".comp_height".equalsIgnoreCase(e.getValue())) index = e.getKey();
        }
        if (index == null) return;
        for (Component c : components.getComponents()) {
            String value = c.getAttributeValue(index);
            if (value == null) continue;
            try {
                c.setHeightMm(Double.parseDouble(value) * mmScale);
            } catch (NumberFormatException e) {
                // Garbled value: leave the height unknown.
            }
        }
    }

    /**
     * {@code TOP <pin_num> <x> <y> <rot> <mirror> <net_num> <subnet_num> <toeprint_name>} — where
     * one pin of the component landed on the board. {@code pin_num} is the 0-based index of the
     * pin in the component's package. A garbled record is skipped.
     */
    private static ToeprintRecord toeprint(String line, double mmScale) {
        int semi = line.indexOf(';');
        String[] tok = (semi >= 0 ? line.substring(0, semi) : line).trim().split("\\s+");
        if (tok.length < 4) return null;
        try {
            ToeprintRecord top = new ToeprintRecord();
            top.setPinNumber(Integer.parseInt(tok[1]));
            top.setX(Double.parseDouble(tok[2]) * mmScale);
            top.setY(Double.parseDouble(tok[3]) * mmScale);
            if (tok.length > 4) top.setRotation(Double.parseDouble(tok[4]));
            if (tok.length > 5) top.setMirror(MirrorType.fromString(tok[5]));
            if (tok.length > 6) top.setNetNumber(Integer.parseInt(tok[6]));
            if (tok.length > 7) top.setSubnetNumber(Integer.parseInt(tok[7]));
            if (tok.length > 8) top.setName(tok[8]);
            return top;
        } catch (RuntimeException e) {
            return null;
        }
    }
}

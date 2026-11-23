package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.Component;
import com.deltaproto.deltaodbpp.model.Components;
import com.deltaproto.deltaodbpp.model.MirrorType;
import com.deltaproto.deltaodbpp.model.PropertyRecord;
import com.deltaproto.deltaodbpp.model.ToeprintRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ComponentsParser {
    // CMP pattern: CMP <pkg_ref> <x> <y> <rot> <mirror> <comp_name> <part_name>
    private static final Pattern CMP_PATTERN = Pattern.compile(
            "^CMP\\s+(\\d+)\\s+([\\d.-]+)\\s+([\\d.-]+)\\s+([\\d.-]+)\\s+([NM])\\s+(\\S+)\\s+(\\S+)");
    private static final Pattern PRP_PATTERN = Pattern.compile("^PRP\\s+(\\S+)\\s+'(.*)'");

    /** Default entry: assume coords are already in mm. */
    public Components parse(Path componentsFile) throws IOException {
        return parse(componentsFile, 1.0);
    }

    public Components parse(Path componentsFile, double mmScale) throws IOException {
        Components components = new Components();
        List<String> lines = Files.readAllLines(componentsFile, StandardCharsets.ISO_8859_1);
        Component currentComponent = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
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
                currentComponent.setPartName(cmpMatcher.group(7));
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
        return components;
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

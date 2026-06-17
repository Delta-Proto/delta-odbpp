package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.Component;
import com.deltaproto.deltaodbpp.model.Components;
import com.deltaproto.deltaodbpp.model.MirrorType;
import com.deltaproto.deltaodbpp.model.PropertyRecord;

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
            }
        }
        return components;
    }
}

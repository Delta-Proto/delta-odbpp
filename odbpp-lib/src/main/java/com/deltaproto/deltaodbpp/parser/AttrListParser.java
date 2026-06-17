package com.deltaproto.deltaodbpp.parser;

import com.deltaproto.deltaodbpp.model.AttrList;
import com.deltaproto.deltaodbpp.StructuredTextParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class AttrListParser {
    private final StructuredTextParser structuredTextParser = new StructuredTextParser();

    public AttrList parse(Path attrlistFile) throws IOException {
        Map<String, String> data = structuredTextParser.parse(attrlistFile);
        AttrList attrList = new AttrList();
        attrList.setUnits(data.remove("UNITS"));
        attrList.setAttributes(data);
        return attrList;
    }
}

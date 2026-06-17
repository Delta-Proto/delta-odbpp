package com.deltaproto.deltaodbpp.parser;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.deltaproto.deltaodbpp.model.stackup.StackupFile;
import java.io.IOException;
import java.nio.file.Path;

public class StackupParser {
    private final XmlMapper xmlMapper;

    public StackupParser() {
        // Be lenient about unknown / vendor-specific XML fields so one typo
        // doesn't break the whole parse.
        this.xmlMapper = new XmlMapper();
        this.xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public StackupFile parse(Path stackupFile) throws IOException {
        return xmlMapper.readValue(stackupFile.toFile(), StackupFile.class);
    }
}

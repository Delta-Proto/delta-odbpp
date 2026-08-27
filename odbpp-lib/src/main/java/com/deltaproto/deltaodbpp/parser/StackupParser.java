package com.deltaproto.deltaodbpp.parser;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
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
        // The stackup model binds XML names straight onto its fields (LayerName,
        // Default_Thickness, ...). Lombok's getters mangle those to camelCase, which
        // matches nothing in the file, so bind by field and ignore the accessors.
        this.xmlMapper.setVisibility(this.xmlMapper.getSerializationConfig()
                .getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
    }

    public StackupFile parse(Path stackupFile) throws IOException {
        return xmlMapper.readValue(stackupFile.toFile(), StackupFile.class);
    }
}

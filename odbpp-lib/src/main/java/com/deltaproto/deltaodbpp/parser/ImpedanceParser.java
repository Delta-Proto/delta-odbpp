package com.deltaproto.deltaodbpp.parser;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.deltaproto.deltaodbpp.model.impedance.ImpedanceFile;
import java.io.IOException;
import java.nio.file.Path;

public class ImpedanceParser {
    private final XmlMapper xmlMapper;

    public ImpedanceParser() {
        // Real-world archives sometimes carry undocumented or misspelt fields
        // (one carried "MaxImpdIdValUsed" — an extra 'd' — instead of
        // "MaxImpIdValUsed"). Be lenient: ignore unknown properties so a single
        // typo in impedance.xml doesn't sink the whole step parse.
        this.xmlMapper = new XmlMapper();
        this.xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ImpedanceFile parse(Path impedanceFile) throws IOException {
        return xmlMapper.readValue(impedanceFile.toFile(), ImpedanceFile.class);
    }
}

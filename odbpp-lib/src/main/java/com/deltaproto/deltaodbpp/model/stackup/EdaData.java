package com.deltaproto.deltaodbpp.model.stackup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/**
 * The stackup as delivered by the design source (spec pg 315). Suppliers must not alter it, so this
 * is the section to trust for what the designer asked for.
 */
@Data
public class EdaData {
    @JacksonXmlProperty(isAttribute = true)
    private String CompanyName;
    @JacksonXmlProperty(isAttribute = true)
    private String ContactName;
    @JacksonXmlProperty(isAttribute = true)
    private String Address;
    /** Spelled {@code PnoneNumber} in the schema (spec pg 315) — the typo is normative. */
    @JacksonXmlProperty(isAttribute = true)
    private String PnoneNumber;
    @JacksonXmlProperty(isAttribute = true)
    private String Comment;

    private Specs Specs;
    private Stackup Stackup;
}

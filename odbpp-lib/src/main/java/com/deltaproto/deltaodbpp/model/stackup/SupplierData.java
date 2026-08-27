package com.deltaproto.deltaodbpp.model.stackup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/**
 * A manufacturing stackup as offered by one supplier (spec pg 377) — the final materials and the
 * impedance calculation based on them.
 */
@Data
public class SupplierData {
    @JacksonXmlProperty(isAttribute = true)
    private String CompanyName;
    @JacksonXmlProperty(isAttribute = true)
    private String ContactName;
    @JacksonXmlProperty(isAttribute = true)
    private String Address;
    /** Spelled {@code PnoneNumber} in the schema (spec pg 377) — the typo is normative. */
    @JacksonXmlProperty(isAttribute = true)
    private String PnoneNumber;
    @JacksonXmlProperty(isAttribute = true)
    private String Comment;

    private Specs Specs;
    private Stackup Stackup;
}

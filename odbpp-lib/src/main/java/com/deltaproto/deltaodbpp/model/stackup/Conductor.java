package com.deltaproto.deltaodbpp.model.stackup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import java.util.List;

/**
 * A specific piece of conductive material (spec pg 325).
 */
@Data
public class Conductor {
    /** COPPER or OTHER. Required. */
    @JacksonXmlProperty(isAttribute = true)
    private String ConductorType;

    /**
     * Copper weight in oz/ft², the historical way of stating copper thickness. 0 when the file omits
     * it — the thickness itself comes from {@link Material#getDefault_Thickness()}.
     */
    @JacksonXmlProperty(isAttribute = true)
    private double CopperWeight_oz_ft2;

    @JacksonXmlProperty(isAttribute = true)
    private String Description;

    @JacksonXmlProperty(isAttribute = true)
    private String Comment;

    /** Property sets carrying conductivity, surface roughness and overriding thicknesses. */
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Properties")
    private List<Properties> properties;

    /** The stated copper weight in oz/ft², or null when the file omits it. */
    public Double copperWeightOz() {
        return CopperWeight_oz_ft2 > 0 ? CopperWeight_oz_ft2 : null;
    }
}

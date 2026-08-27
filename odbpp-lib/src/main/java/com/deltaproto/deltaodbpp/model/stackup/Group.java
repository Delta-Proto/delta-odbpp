package com.deltaproto.deltaodbpp.model.stackup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import java.util.List;

/**
 * A collection of materials whose conductors are tied to copper layers of the design (spec pg 369).
 * Layers within a group run top of the stack to bottom.
 */
@Data
public class Group {
    @JacksonXmlProperty(isAttribute = true)
    private String GroupName;

    /** Total thickness of this group of layers in {@link #getUnits()}; null when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private Double GroupThickness;

    /** Unit for {@link #getGroupThickness()}; MIL when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private String Units;

    /** Lamination cycles this group is subjected to; null when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private Integer NumberOfLaminateCycles;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Layer")
    private List<Layer> layer;
}

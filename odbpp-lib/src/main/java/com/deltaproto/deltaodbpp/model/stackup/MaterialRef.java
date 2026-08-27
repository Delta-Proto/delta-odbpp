package com.deltaproto.deltaodbpp.model.stackup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/**
 * A {@link SpecRef}'s material requirement for one layer (spec pg 374) — the layer's end of the
 * reference. The physical properties sit on the {@link Material} it names.
 */
@Data
public class MaterialRef {
    /** The {@link Material#getMaterialName()} within the referenced spec. Required. */
    @JacksonXmlProperty(isAttribute = true)
    private String MaterialName;

    /**
     * Which {@link Properties} set of that material applies; null when the material defines a
     * single set, which is then the default.
     */
    @JacksonXmlProperty(isAttribute = true)
    private String PropertyName;

    /** Signal frequency selecting a {@link Property} within the property set; null when unset. */
    @JacksonXmlProperty(isAttribute = true)
    private Double FrequencyVal;

    /** Copper area of the layer as a percentage of the laminate area; null when unset. */
    @JacksonXmlProperty(isAttribute = true)
    private Double CopperAreaPrecent;

    /** Etched trace width at the top of the trapezoid, in {@link #getUnits_TraceWidth()}. */
    @JacksonXmlProperty(isAttribute = true)
    private Double TraceWidthTop;

    /** Etched trace width at the bottom of the trapezoid, in {@link #getUnits_TraceWidth()}. */
    @JacksonXmlProperty(isAttribute = true)
    private Double TraceWidthBottom;

    /** Unit for the trace-width attributes; MIL when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private String Units_TraceWidth;
}

package com.deltaproto.deltaodbpp.model.stackup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/**
 * A {@link SpecRef}'s impedance requirement for one layer (spec pg 376) — which {@link Impedance}
 * of the referenced spec applies, and in which structure.
 */
@Data
public class ImpedanceRef {
    /** The {@link Impedance#getImpName()} within the referenced spec. Required. */
    @JacksonXmlProperty(isAttribute = true)
    private String ImpName;

    /**
     * SINGLE_ENDED / DIFFERENTIAL_EDGE_COUPLE / DIFFERENTIAL_COPLANAR_WAVEGUIDED /
     * SINGLE_ENDED_COPLANAR_WAVEGUIDED / DIFFERENTIAL_BROADSIDE_COUPLED. Required.
     */
    @JacksonXmlProperty(isAttribute = true)
    private String ImpTypeName;
}

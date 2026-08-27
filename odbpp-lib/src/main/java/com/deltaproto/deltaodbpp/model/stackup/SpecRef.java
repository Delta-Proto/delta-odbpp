package com.deltaproto.deltaodbpp.model.stackup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/**
 * A stackup {@link Layer}'s reference into the {@link Specs} section (spec pg 373): which
 * {@link Spec} holds its material, and which {@link Material} within it.
 */
@Data
public class SpecRef {
    /** The {@link Spec#getSpecName()} holding the referenced material. Required. */
    @JacksonXmlProperty(isAttribute = true)
    private String MaterialSpecName;

    /** The {@link Spec#getSpecName()} holding the referenced impedance; null when none. */
    @JacksonXmlProperty(isAttribute = true)
    private String ImpSpecName;

    /** The material requirement for this layer; null when the reference names no material. */
    @JacksonXmlProperty(localName = "Material")
    private MaterialRef Material;

    /** The impedance requirement for this layer; null when the layer has none. */
    @JacksonXmlProperty(localName = "Impedance")
    private ImpedanceRef Impedance;
}

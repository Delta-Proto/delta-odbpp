package com.deltaproto.deltaodbpp.model.stackup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import java.util.List;

/**
 * One layer of a stackup {@link Group} (spec pg 371).
 *
 * <p>A stackup layer carries no thickness or dielectric constant of its own: it names its materials
 * through {@link SpecRef}, and the physical values live on the matching {@link Material} in the
 * {@link Specs} section. {@link com.deltaproto.deltaodbpp.spec.StackupResolver} follows that
 * reference.
 */
@Data
public class Layer {
    /** The name of the layer as used in the design — matches a matrix layer name. Required. */
    @JacksonXmlProperty(isAttribute = true)
    private String LayerName;

    /** SIGNAL / POWER_GROUND / MIXED / DIELECTRIC / SOLDER_MASK / DRILL / …; DOCUMENT when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private String LayerType;

    /** Refines {@link #getLayerType()} — CORE or PREPREG under DIELECTRIC, BACKDRILL under DRILL, … */
    @JacksonXmlProperty(isAttribute = true)
    private String LayerSubType;

    /** For DRILL / ROUT layers: the layer where the material is first penetrated; null otherwise. */
    @JacksonXmlProperty(isAttribute = true)
    private String MechStartLayerName;

    /** For DRILL / ROUT layers: the layer where penetration ends; null otherwise. */
    @JacksonXmlProperty(isAttribute = true)
    private String MechEndLayerName;

    /** TOP / BOTTOM / INNER / NONE — position in the stackup; NONE when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private String Side;

    @JacksonXmlProperty(isAttribute = true)
    private String Description;

    @JacksonXmlProperty(isAttribute = true)
    private String Comment;

    /** The material and impedance specs this layer draws on; null or empty when it names none. */
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "SpecRef")
    private List<SpecRef> specRef;

    /** True when {@link #getLayerType()} is a copper layer type. */
    public boolean isConductorLayer() {
        return "SIGNAL".equalsIgnoreCase(LayerType)
                || "POWER_GROUND".equalsIgnoreCase(LayerType)
                || "MIXED".equalsIgnoreCase(LayerType)
                || "POWER".equalsIgnoreCase(LayerType)
                || "GROUND".equalsIgnoreCase(LayerType);
    }

    /** True when {@link #getLayerType()} is DIELECTRIC. */
    public boolean isDielectricLayer() {
        return "DIELECTRIC".equalsIgnoreCase(LayerType);
    }
}

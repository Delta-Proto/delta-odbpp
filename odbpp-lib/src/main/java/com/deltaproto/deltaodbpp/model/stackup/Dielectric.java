package com.deltaproto.deltaodbpp.model.stackup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import java.util.List;

/**
 * A specific piece of dielectric material (spec pg 320). Its electrical characteristics vary with
 * frequency, so Dk and Df live on the {@link Property} entries below rather than here.
 */
@Data
public class Dielectric {
    /** PREPREG / CORE / OTHER / UNDEFINED. Required. */
    @JacksonXmlProperty(isAttribute = true)
    private String DielectricType;

    /** Refines {@code OTHER}: COVERLAY, STIFFENER, SOLDER_MASK, … null when not OTHER. */
    @JacksonXmlProperty(isAttribute = true)
    private String OtherSubType;

    /** The material supplier's own reference name, free text; null when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private String MaterialReference;

    /** Free text describing the prepreg glass or core construction; null when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private String GlassStyle_Construction;

    /** Percentage of resin in the dielectric; null when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private Double ResinContent_Percent;

    @JacksonXmlProperty(isAttribute = true)
    private String Description;

    @JacksonXmlProperty(isAttribute = true)
    private String Comment;

    /** Frequency-dependent property sets; null or empty when the file states none. */
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Properties")
    private List<Properties> properties;
}

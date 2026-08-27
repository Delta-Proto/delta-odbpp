package com.deltaproto.deltaodbpp.model.stackup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import java.util.List;

/**
 * A named set of material {@link Property} entries (spec pg 322 for dielectrics, pg 326 for
 * conductors). A {@link MaterialRef} picks one by {@code PropertyName}; when a material defines only
 * one set, that set is the default.
 */
@Data
public class Properties {
    @JacksonXmlProperty(isAttribute = true)
    private String PropertyName;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Property")
    private List<Property> property;
}

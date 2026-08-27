package com.deltaproto.deltaodbpp.model.stackup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/**
 * A material definition within a {@link Spec} (spec pg 319) — either a dielectric or a conductor,
 * plus the thickness it defaults to.
 *
 * <p>A {@link Layer} reaches this through {@link SpecRef} / {@link MaterialRef}, so this is where a
 * layer's real thickness, Dk and Df come from.
 */
@Data
public class Material {
    @JacksonXmlProperty(isAttribute = true)
    private String MaterialName;

    private Dielectric Dielectric;
    private Conductor Conductor;
    private DefaultThickness Default_Thickness;

    /** True when this material is a dielectric (has a {@code Dielectric} child). */
    public boolean isDielectricMaterial() {
        return Dielectric != null;
    }

    /** True when this material is a conductor (has a {@code Conductor} child). */
    public boolean isConductorMaterial() {
        return Conductor != null;
    }
}

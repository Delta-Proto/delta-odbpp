package com.deltaproto.deltaodbpp.model.stackup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/**
 * An impedance specification within a {@link Spec} (spec pg 330) — the target impedance and its
 * tolerance. A {@link Layer} selects one through {@link ImpedanceRef}.
 *
 * <p>The per-structure children ({@code Single_Ended}, {@code Differential_Edge_Coupled} and the
 * three waveguide forms) carry the calculated geometry for one named layer. They are not modelled
 * here: the analysis path only needs to know that impedance control exists and at what target, and
 * {@code steps/<step>/impedance} — see {@link com.deltaproto.deltaodbpp.model.impedance} — is the
 * per-layer source the parser already reads.
 */
@Data
public class Impedance {
    @JacksonXmlProperty(isAttribute = true)
    private String ImpName;

    /** Single-ended target in ohms; null when this spec states no single-ended target. */
    @JacksonXmlProperty(isAttribute = true)
    private Double ZoValOhms;

    /** Positive tolerance on {@link #getZoValOhms()}; null when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private Double ZoPlusVal;

    /** Negative tolerance on {@link #getZoValOhms()}, stated positive; null when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private Double ZoMinusVal;

    /** True (the default) when the single-ended tolerances are percentages rather than ohms. */
    @JacksonXmlProperty(isAttribute = true)
    private Boolean ZoValPercent;

    /** Differential target in ohms; null when this spec states no differential target. */
    @JacksonXmlProperty(isAttribute = true)
    private Double ZDiffValOhms;

    /** Positive tolerance on {@link #getZDiffValOhms()}; null when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private Double ZDiffPlusVal;

    /** Negative tolerance on {@link #getZDiffValOhms()}, stated positive; null when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private Double ZDiffMinusVal;

    /** True (the default) when the differential tolerances are percentages rather than ohms. */
    @JacksonXmlProperty(isAttribute = true)
    private Boolean ZDiffValPercent;
}

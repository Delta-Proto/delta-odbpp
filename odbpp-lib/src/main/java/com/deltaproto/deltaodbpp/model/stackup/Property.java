package com.deltaproto.deltaodbpp.model.stackup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/**
 * One material characteristic, optionally at a given signal frequency (spec pg 323 for dielectrics,
 * pg 327 for conductors).
 *
 * <p>The two schemas share the frequency, Dk/Df and thickness attributes and differ only in the
 * conductor-only conductivity and roughness values, so both bind here; the conductor-only fields are
 * simply null on a dielectric property and vice versa.
 */
@Data
public class Property {
    /** The frequency this property is stated at, in {@link #getUnits()}; null when unset. */
    @JacksonXmlProperty(isAttribute = true)
    private Double FrequencyVal;

    /** Unit of {@link #getFrequencyVal()}: Hz / MHz / GHz. GHz when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private String Units;

    /** Dielectric constant (Dk) at {@link #getFrequencyVal()}; null when unset. */
    @JacksonXmlProperty(isAttribute = true)
    private Double DielectricConstant_Dk;

    /** Loss tangent (Df) at {@link #getFrequencyVal()}; null when unset. */
    @JacksonXmlProperty(isAttribute = true)
    private Double LossTangent_Df;

    /** Thickness overriding the material default, in {@link #getUnits_Thickness()}; null when unset. */
    @JacksonXmlProperty(isAttribute = true)
    private Double Thickness;

    /** Finished thickness overriding the material default; null when unset. */
    @JacksonXmlProperty(isAttribute = true)
    private Double Finished_Thickness;

    /** Unit for the two thickness attributes; MIL when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private String Units_Thickness;

    /** Conductor only: conductivity of copper in MHO/CM; null when unset. */
    @JacksonXmlProperty(isAttribute = true)
    private Double Conductivity_mho_cm;

    /** Conductor only: surface roughness facing the core, Rz; null when unset. */
    @JacksonXmlProperty(isAttribute = true)
    private Double SurfaceRoughnessFacingCore_Rz;

    /** Conductor only: surface roughness facing the prepreg, Rz; null when unset. */
    @JacksonXmlProperty(isAttribute = true)
    private Double SurfaceRoughnessFacingPrepreg_Rz;

    /** Conductor only: surface roughness of the foil or sheet, Rz; null when unset. */
    @JacksonXmlProperty(isAttribute = true)
    private Double SurfaceRoughnessFoil_Rz;

    /**
     * The overriding thickness in picometres, preferring {@code Finished_Thickness} over
     * {@code Thickness}, or null when this property states neither.
     */
    public Long thicknessPm(StackupUnits fileDefault) {
        Double raw = Finished_Thickness != null ? Finished_Thickness : Thickness;
        if (raw == null) {
            return null;
        }
        return StackupUnits.parse(Units_Thickness, fileDefault == null ? StackupUnits.DEFAULT : fileDefault)
                .toPicometres(raw);
    }

    /**
     * The thickness in mm, preferring {@code Finished_Thickness} over {@code Thickness}, or null when
     * this property states neither.
     */
    public Double thicknessMm(StackupUnits fileDefault) {
        Double raw = Finished_Thickness != null ? Finished_Thickness : Thickness;
        if (raw == null) {
            return null;
        }
        return StackupUnits.parse(Units_Thickness, fileDefault == null ? StackupUnits.DEFAULT : fileDefault)
                .toMm(raw);
    }
}

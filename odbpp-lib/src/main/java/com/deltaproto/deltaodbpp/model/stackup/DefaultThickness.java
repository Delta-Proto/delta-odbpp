package com.deltaproto.deltaodbpp.model.stackup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/**
 * The thickness of a {@link Material} (spec pg 329), in {@link #getUnits()} — the value a layer
 * gets when no {@link Property} overrides it.
 */
@Data
public class DefaultThickness {
    /** Required per spec; 0 only when the file omits it. */
    @JacksonXmlProperty(isAttribute = true)
    private double Thickness;

    /** The finished (post-lamination) thickness; null when the file states none. */
    @JacksonXmlProperty(isAttribute = true)
    private Double Finished_Thickness;

    /** Unit for both thickness attributes; MIL when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private String Units;

    /**
     * The thickness in picometres, preferring {@code Finished_Thickness} over {@code Thickness}, or
     * null when this element states neither. Exact for both metric and imperial nominal values.
     */
    public Long thicknessPm(StackupUnits fileDefault) {
        Double raw = Finished_Thickness != null ? Finished_Thickness : rawThickness();
        if (raw == null) {
            return null;
        }
        return StackupUnits.parse(Units, fileDefault == null ? StackupUnits.DEFAULT : fileDefault)
                .toPicometres(raw);
    }

    /**
     * The thickness in mm, preferring {@code Finished_Thickness} over {@code Thickness}, or null when
     * this element states neither.
     */
    public Double thicknessMm(StackupUnits fileDefault) {
        Double raw = Finished_Thickness != null ? Finished_Thickness : rawThickness();
        if (raw == null) {
            return null;
        }
        return StackupUnits.parse(Units, fileDefault == null ? StackupUnits.DEFAULT : fileDefault)
                .toMm(raw);
    }

    /** {@code Thickness} as a nullable — the schema's required attribute reads 0 when it is absent. */
    private Double rawThickness() {
        return Thickness > 0 ? Thickness : null;
    }
}

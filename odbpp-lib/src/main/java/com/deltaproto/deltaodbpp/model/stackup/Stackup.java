package com.deltaproto.deltaodbpp.model.stackup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import java.util.List;

/**
 * One stackup representation — the ordered groups that make up the board (spec pg 367).
 */
@Data
public class Stackup {
    @JacksonXmlProperty(isAttribute = true)
    private String StackupName;

    /** Target board thickness in {@link #getUnits()}; null when the file omits it. */
    @JacksonXmlProperty(isAttribute = true)
    private Double StackupThickness;

    /** Positive tolerance to add to {@link #getStackupThickness()}; null when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private Double PlusTol;

    /** Positive tolerance to subtract from {@link #getStackupThickness()}; null when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private Double MinusTol;

    /** Unit for the thickness and tolerance attributes; MIL when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private String Units;

    /** Where the target thickness is measured: LAMINATE / METAL / MASK / OTHER. METAL when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private String WhereMeasured;

    /** Lamination cycles needed to complete booking; null when absent. */
    @JacksonXmlProperty(isAttribute = true)
    private Integer TotalLaminationCycles;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Group")
    private List<Group> group;

    /** Target board thickness in mm, or null when the file does not state one. */
    public Double stackupThicknessMm(StackupUnits fileDefault) {
        return StackupThickness == null ? null
                : StackupUnits.parse(Units, fileDefault == null ? StackupUnits.DEFAULT : fileDefault)
                        .toMm(StackupThickness);
    }
}

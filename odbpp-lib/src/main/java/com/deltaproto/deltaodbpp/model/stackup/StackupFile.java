package com.deltaproto.deltaodbpp.model.stackup;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

/**
 * {@code matrix/stackup.xml} — the material information for the whole manufacturing process
 * (spec pg 52, schema pg 314).
 *
 * <p>Optional: the matrix file remains the primary source of layer order, and many archives ship
 * no stackup at all.
 */
@Data
@JacksonXmlRootElement(localName = "StackupFile")
public class StackupFile {
    /** The ODB++ version of the stackup.xml file. Required per spec. */
    @JacksonXmlProperty(isAttribute = true)
    private String Version;

    /**
     * The unit governing every length in the file that does not carry its own {@code Units}.
     * {@code MIL} when absent — see {@link StackupUnits#DEFAULT}.
     */
    @JacksonXmlProperty(isAttribute = true)
    private String DefaultUnits;

    // Optional per spec, pg 54
    private EdaData EdaData;
    // Optional per spec, pg 54
    private SupplierData SupplierData;

    /** The file-wide default unit, resolved; {@link StackupUnits#MIL} when unset or unrecognised. */
    public StackupUnits defaultUnits() {
        return StackupUnits.parse(DefaultUnits, StackupUnits.DEFAULT);
    }
}

package com.deltaproto.deltaodbpp.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class Features {
    private List<Feature> features = new ArrayList<>();

    /**
     * Units of measurement from the UNITS directive.
     * Either "MM" (millimeters) or "INCH" (inches).
     * This affects how symbol dimensions are interpreted:
     * - MM: symbol dimensions are in microns (1/1000 mm)
     * - INCH: symbol dimensions are in mils (1/1000 inch)
     */
    private String units = "MM";  // Default to MM as it's more common

    /**
     * Symbol table mapping symbol number to symbol name.
     * Parsed from $<num> <name> lines in features file.
     * Example: $0 r50 means symbol 0 is a round pad with 50 mil diameter.
     */
    private Map<Integer, String> symbolTable = new HashMap<>();

    /**
     * Symbol mirror flags mapping symbol number to mirror flag.
     * Parsed from $<num> <name> [M|I] lines.
     * M = mirrored, I = not mirrored (default if not specified)
     */
    private Map<Integer, Boolean> symbolMirrorFlags = new HashMap<>();

    /**
     * Feature attribute names mapping attribute number to attribute name.
     * Parsed from @<num> <name> lines in features file.
     * Example: @0 .smd means attribute 0 is named ".smd"
     */
    private Map<Integer, String> attributeNames = new HashMap<>();

    /**
     * Feature attribute text strings mapping string number to text value.
     * Parsed from &<num> <text> lines in features file.
     * Example: &0 9796334 means text string 0 has value "9796334"
     */
    private Map<Integer, String> attributeTextStrings = new HashMap<>();

    /**
     * Get symbol name by symbol number.
     * @param symbolNumber the symbol reference number
     * @return the symbol name or null if not found
     */
    public String getSymbolName(int symbolNumber) {
        return symbolTable.get(symbolNumber);
    }

    /**
     * Check if a symbol is mirrored.
     * @param symbolNumber the symbol reference number
     * @return true if mirrored (M flag), false if not mirrored or not specified
     */
    public boolean isSymbolMirrored(int symbolNumber) {
        return symbolMirrorFlags.getOrDefault(symbolNumber, false);
    }

    /**
     * Get attribute name by attribute number.
     * @param attributeNumber the attribute reference number
     * @return the attribute name or null if not found
     */
    public String getAttributeName(int attributeNumber) {
        return attributeNames.get(attributeNumber);
    }

    /**
     * Get attribute text string by string number.
     * @param stringNumber the text string reference number
     * @return the text string value or null if not found
     */
    public String getAttributeTextString(int stringNumber) {
        return attributeTextStrings.get(stringNumber);
    }

    /**
     * Check if this features file uses millimeter units (symbol dimensions in microns).
     * @return true if UNITS=MM, false if UNITS=INCH
     */
    public boolean isMillimeters() {
        return "MM".equalsIgnoreCase(units);
    }
}
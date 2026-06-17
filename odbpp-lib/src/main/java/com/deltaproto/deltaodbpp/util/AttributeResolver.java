package com.deltaproto.deltaodbpp.util;

import com.deltaproto.deltaodbpp.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for resolving attribute values and looking up attributes.
 * ODB++ attribute syntax:
 * - @n=value - attribute by index (0-based)
 * - &name=value - attribute by name
 * - ;n=value - shorthand for attribute by index
 */
public class AttributeResolver {

    private final Map<String, AttributeDefinition> systemAttributes;
    private final Map<String, AttributeDefinition> userAttributes;
    private final Map<Integer, String> attributeIndexToName;
    private final Map<String, Integer> attributeNameToIndex;

    public AttributeResolver() {
        this.systemAttributes = new HashMap<>();
        this.userAttributes = new HashMap<>();
        this.attributeIndexToName = new HashMap<>();
        this.attributeNameToIndex = new HashMap<>();
    }

    public AttributeResolver(Map<String, AttributeDefinition> systemAttributes,
                            Map<String, AttributeDefinition> userAttributes) {
        this.systemAttributes = systemAttributes != null ? systemAttributes : new HashMap<>();
        this.userAttributes = userAttributes != null ? userAttributes : new HashMap<>();
        this.attributeIndexToName = new HashMap<>();
        this.attributeNameToIndex = new HashMap<>();
        buildIndexMaps();
    }

    private void buildIndexMaps() {
        int index = 0;
        for (String name : systemAttributes.keySet()) {
            attributeIndexToName.put(index, name);
            attributeNameToIndex.put(name, index);
            index++;
        }
        for (String name : userAttributes.keySet()) {
            attributeIndexToName.put(index, name);
            attributeNameToIndex.put(name, index);
            index++;
        }
    }

    /**
     * Get an attribute definition by name.
     *
     * @param name The attribute name (may start with '.' for system attributes)
     * @return The attribute definition, or null if not found
     */
    public AttributeDefinition getDefinition(String name) {
        if (name == null) {
            return null;
        }
        if (name.startsWith(".")) {
            return systemAttributes.get(name);
        }
        AttributeDefinition def = userAttributes.get(name);
        if (def == null) {
            def = systemAttributes.get(name);
        }
        return def;
    }

    /**
     * Get an attribute definition by index.
     *
     * @param index The attribute index (0-based)
     * @return The attribute definition, or null if not found
     */
    public AttributeDefinition getDefinitionByIndex(int index) {
        String name = attributeIndexToName.get(index);
        return name != null ? getDefinition(name) : null;
    }

    /**
     * Get the attribute name by index.
     *
     * @param index The attribute index (0-based)
     * @return The attribute name, or null if not found
     */
    public String getAttributeName(int index) {
        return attributeIndexToName.get(index);
    }

    /**
     * Get the attribute index by name.
     *
     * @param name The attribute name
     * @return The attribute index, or -1 if not found
     */
    public int getAttributeIndex(String name) {
        Integer index = attributeNameToIndex.get(name);
        return index != null ? index : -1;
    }

    /**
     * Parse an attribute value according to its definition type.
     *
     * @param def   The attribute definition
     * @param value The string value to parse
     * @return The parsed value (appropriate type based on definition)
     */
    public Object parseValue(AttributeDefinition def, String value) {
        if (def == null || value == null) {
            return value;
        }

        switch (def.getType()) {
            case BOOLEAN:
                return "YES".equalsIgnoreCase(value) || "Y".equalsIgnoreCase(value) || "1".equals(value);
            case INTEGER:
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return 0;
                }
            case FLOAT:
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            case OPTION:
                // Return the option index or the value itself
                if (def.getOptions() != null) {
                    int idx = def.getOptions().indexOf(value);
                    return idx >= 0 ? idx : value;
                }
                return value;
            case TEXT:
            default:
                return value;
        }
    }

    /**
     * Validate an attribute value against its definition.
     *
     * @param def   The attribute definition
     * @param value The value to validate
     * @return True if the value is valid, false otherwise
     */
    public boolean validateValue(AttributeDefinition def, String value) {
        if (def == null) {
            return true; // No definition, can't validate
        }
        if (value == null) {
            return def.getDefaultValue() != null; // Null is ok if there's a default
        }

        switch (def.getType()) {
            case BOOLEAN:
                return "YES".equalsIgnoreCase(value) || "NO".equalsIgnoreCase(value) ||
                       "Y".equalsIgnoreCase(value) || "N".equalsIgnoreCase(value) ||
                       "1".equals(value) || "0".equals(value);
            case INTEGER:
                try {
                    int intVal = Integer.parseInt(value);
                    if (def.getMinValInt() != null && intVal < def.getMinValInt()) return false;
                    return def.getMaxValInt() == null || intVal <= def.getMaxValInt();
                } catch (NumberFormatException e) {
                    return false;
                }
            case FLOAT:
                try {
                    double floatVal = Double.parseDouble(value);
                    if (def.getMinValFloat() != null && floatVal < def.getMinValFloat()) return false;
                    return def.getMaxValFloat() == null || floatVal <= def.getMaxValFloat();
                } catch (NumberFormatException e) {
                    return false;
                }
            case TEXT:
                int len = value.length();
                if (def.getMinLen() != null && len < def.getMinLen()) return false;
                return def.getMaxLen() == null || len <= def.getMaxLen();
            case OPTION:
                if (def.getOptions() == null) return true;
                return def.getOptions().contains(value);
            default:
                return true;
        }
    }

    /**
     * Create an Attribute object with resolved name and value.
     *
     * @param index The attribute index from the file
     * @param value The attribute value
     * @return The Attribute object with name and value
     */
    public Attribute createAttribute(int index, String value) {
        Attribute attr = new Attribute();
        String name = getAttributeName(index);
        attr.setName(name != null ? name : String.valueOf(index));
        attr.setValue(value);
        return attr;
    }
}

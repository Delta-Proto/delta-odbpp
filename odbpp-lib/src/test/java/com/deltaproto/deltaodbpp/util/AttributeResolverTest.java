package com.deltaproto.deltaodbpp.util;

import com.deltaproto.deltaodbpp.model.Attribute;
import com.deltaproto.deltaodbpp.model.AttributeDefinition;
import com.deltaproto.deltaodbpp.model.AttributeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AttributeResolverTest {

    private AttributeResolver resolver;

    @BeforeEach
    void setUp() {
        Map<String, AttributeDefinition> systemAttrs = new HashMap<>();
        Map<String, AttributeDefinition> userAttrs = new HashMap<>();

        // Create system attribute
        AttributeDefinition sysDef = new AttributeDefinition();
        sysDef.setName(".test_point");
        sysDef.setType(AttributeType.BOOLEAN);
        systemAttrs.put(".test_point", sysDef);

        // Create user attributes
        AttributeDefinition textDef = new AttributeDefinition();
        textDef.setName("description");
        textDef.setType(AttributeType.TEXT);
        textDef.setMinLen(1);
        textDef.setMaxLen(100);
        userAttrs.put("description", textDef);

        AttributeDefinition intDef = new AttributeDefinition();
        intDef.setName("count");
        intDef.setType(AttributeType.INTEGER);
        intDef.setMinValInt(0);
        intDef.setMaxValInt(1000);
        userAttrs.put("count", intDef);

        AttributeDefinition floatDef = new AttributeDefinition();
        floatDef.setName("value");
        floatDef.setType(AttributeType.FLOAT);
        floatDef.setMinValFloat(0.0);
        floatDef.setMaxValFloat(100.0);
        userAttrs.put("value", floatDef);

        AttributeDefinition optionDef = new AttributeDefinition();
        optionDef.setName("layer_type");
        optionDef.setType(AttributeType.OPTION);
        optionDef.setOptions(Arrays.asList("SIGNAL", "POWER", "GROUND"));
        userAttrs.put("layer_type", optionDef);

        resolver = new AttributeResolver(systemAttrs, userAttrs);
    }

    @Test
    void testGetDefinitionByName() {
        assertNotNull(resolver.getDefinition(".test_point"));
        assertNotNull(resolver.getDefinition("description"));
        assertNotNull(resolver.getDefinition("count"));
        assertNull(resolver.getDefinition("nonexistent"));
    }

    @Test
    void testGetDefinitionByIndex() {
        // Index 0 should be the system attribute
        assertNotNull(resolver.getDefinitionByIndex(0));
        assertEquals(".test_point", resolver.getDefinitionByIndex(0).getName());

        // Index 1-4 should be user attributes
        assertNotNull(resolver.getDefinitionByIndex(1));
    }

    @Test
    void testGetAttributeName() {
        assertEquals(".test_point", resolver.getAttributeName(0));
        assertNotNull(resolver.getAttributeName(1)); // First user attr
    }

    @Test
    void testGetAttributeIndex() {
        assertEquals(0, resolver.getAttributeIndex(".test_point"));
        assertTrue(resolver.getAttributeIndex("description") >= 0);
        assertEquals(-1, resolver.getAttributeIndex("nonexistent"));
    }

    @Test
    void testParseBooleanValue() {
        AttributeDefinition def = resolver.getDefinition(".test_point");
        assertTrue((Boolean) resolver.parseValue(def, "YES"));
        assertTrue((Boolean) resolver.parseValue(def, "Y"));
        assertTrue((Boolean) resolver.parseValue(def, "1"));
        assertFalse((Boolean) resolver.parseValue(def, "NO"));
        assertFalse((Boolean) resolver.parseValue(def, "N"));
        assertFalse((Boolean) resolver.parseValue(def, "0"));
    }

    @Test
    void testParseIntegerValue() {
        AttributeDefinition def = resolver.getDefinition("count");
        assertEquals(42, resolver.parseValue(def, "42"));
        assertEquals(0, resolver.parseValue(def, "invalid"));
    }

    @Test
    void testParseFloatValue() {
        AttributeDefinition def = resolver.getDefinition("value");
        assertEquals(3.14, (Double) resolver.parseValue(def, "3.14"), 0.001);
        assertEquals(0.0, (Double) resolver.parseValue(def, "invalid"), 0.001);
    }

    @Test
    void testParseOptionValue() {
        AttributeDefinition def = resolver.getDefinition("layer_type");
        assertEquals(0, resolver.parseValue(def, "SIGNAL")); // Index 0
        assertEquals(1, resolver.parseValue(def, "POWER"));  // Index 1
        assertEquals("UNKNOWN", resolver.parseValue(def, "UNKNOWN")); // Not found, return value
    }

    @Test
    void testParseTextValue() {
        AttributeDefinition def = resolver.getDefinition("description");
        assertEquals("Hello World", resolver.parseValue(def, "Hello World"));
    }

    @Test
    void testValidateBooleanValue() {
        AttributeDefinition def = resolver.getDefinition(".test_point");
        assertTrue(resolver.validateValue(def, "YES"));
        assertTrue(resolver.validateValue(def, "NO"));
        assertTrue(resolver.validateValue(def, "Y"));
        assertTrue(resolver.validateValue(def, "N"));
        assertTrue(resolver.validateValue(def, "1"));
        assertTrue(resolver.validateValue(def, "0"));
        assertFalse(resolver.validateValue(def, "MAYBE"));
    }

    @Test
    void testValidateIntegerValue() {
        AttributeDefinition def = resolver.getDefinition("count");
        assertTrue(resolver.validateValue(def, "0"));
        assertTrue(resolver.validateValue(def, "500"));
        assertTrue(resolver.validateValue(def, "1000"));
        assertFalse(resolver.validateValue(def, "-1"));
        assertFalse(resolver.validateValue(def, "1001"));
        assertFalse(resolver.validateValue(def, "not a number"));
    }

    @Test
    void testValidateFloatValue() {
        AttributeDefinition def = resolver.getDefinition("value");
        assertTrue(resolver.validateValue(def, "0.0"));
        assertTrue(resolver.validateValue(def, "50.5"));
        assertTrue(resolver.validateValue(def, "100.0"));
        assertFalse(resolver.validateValue(def, "-0.1"));
        assertFalse(resolver.validateValue(def, "100.1"));
        assertFalse(resolver.validateValue(def, "not a float"));
    }

    @Test
    void testValidateTextValue() {
        AttributeDefinition def = resolver.getDefinition("description");
        assertTrue(resolver.validateValue(def, "Valid text"));
        assertFalse(resolver.validateValue(def, "")); // Min length is 1
    }

    @Test
    void testValidateOptionValue() {
        AttributeDefinition def = resolver.getDefinition("layer_type");
        assertTrue(resolver.validateValue(def, "SIGNAL"));
        assertTrue(resolver.validateValue(def, "POWER"));
        assertTrue(resolver.validateValue(def, "GROUND"));
        assertFalse(resolver.validateValue(def, "INVALID"));
    }

    @Test
    void testCreateAttribute() {
        Attribute attr = resolver.createAttribute(0, "YES");
        assertEquals(".test_point", attr.getName());
        assertEquals("YES", attr.getValue());
    }

    @Test
    void testEmptyResolver() {
        AttributeResolver emptyResolver = new AttributeResolver();
        assertNull(emptyResolver.getDefinition("anything"));
        assertNull(emptyResolver.getDefinitionByIndex(0));
    }
}

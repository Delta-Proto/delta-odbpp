package com.deltaproto.deltaodbpp.export.gerber;

import com.deltaproto.deltaodbpp.model.Features;
import com.deltaproto.deltaodbpp.model.Line;
import com.deltaproto.deltaodbpp.model.Pad;
import com.deltaproto.deltaodbpp.model.Tool;
import com.deltaproto.deltaodbpp.model.Tools;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OdbToGerberConverterTest {

    /** Spans that stop short of an outer layer are Blind; spans inside the board are Buried. */
    @Test
    void drillFileFunctionNamesTheSpanKind() {
        assertEquals("Plated,1,6,PTH", OdbToGerberConverter.drillFileFunction(true, 1, 6, 6));
        assertEquals("NonPlated,1,6,NPTH", OdbToGerberConverter.drillFileFunction(false, 1, 6, 6));
        assertEquals("Plated,1,2,Blind", OdbToGerberConverter.drillFileFunction(true, 1, 2, 6));
        assertEquals("Plated,5,6,Blind", OdbToGerberConverter.drillFileFunction(true, 5, 6, 6));
        assertEquals("Plated,2,7,Buried", OdbToGerberConverter.drillFileFunction(true, 2, 7, 8));
        assertEquals("NonPlated,3,4,Buried", OdbToGerberConverter.drillFileFunction(false, 3, 4, 6));
        assertEquals("Plated,1,1,PTH", OdbToGerberConverter.drillFileFunction(true, 1, 1, 0));
    }

    private static Pad pad(int symbol, Integer attr, String value) {
        Pad pad = new Pad();
        pad.setSymbolNumber(symbol);
        if (attr != null) {
            pad.getAttributes().put(attr, value);
        }
        return pad;
    }

    /**
     * One ODB++ drill layer may mix plated and non-plated holes: the {@code .drill} attribute
     * says which (as the option index or the word), the tools file is the fallback, and a hole
     * with neither leaves the decision to the layer.
     */
    @Test
    void featurePlatingComesFromTheDrillAttributeThenTheTool() {
        Features features = new Features();
        features.setUnits("MM");
        features.getSymbolTable().put(0, "r300");
        features.getSymbolTable().put(1, "r2250");
        features.getSymbolTable().put(2, "r999");
        features.getAttributeNames().put(1, ".drill");
        features.getAttributeNames().put(2, ".geometry");
        Tools tools = new Tools();
        Tool via = new Tool();
        via.setType(Tool.ToolType.VIA);
        via.setFinishSize(0.3);
        Tool mounting = new Tool();
        mounting.setType(Tool.ToolType.NON_PLATED);
        mounting.setFinishSize(2.25);
        tools.getTools().add(via);
        tools.getTools().add(mounting);

        assertEquals(Boolean.FALSE, OdbToGerberConverter.featurePlating(pad(0, 1, "1"), features, tools));
        assertEquals(Boolean.TRUE, OdbToGerberConverter.featurePlating(pad(1, 1, "0"), features, tools));
        assertEquals(Boolean.TRUE, OdbToGerberConverter.featurePlating(pad(1, 1, "2"), features, tools));
        assertEquals(Boolean.FALSE, OdbToGerberConverter.featurePlating(pad(0, 1, "NON_PLATED"), features, tools));
        assertEquals(Boolean.TRUE, OdbToGerberConverter.featurePlating(pad(0, 2, "x"), features, tools), "tool: via");
        assertEquals(Boolean.FALSE, OdbToGerberConverter.featurePlating(pad(1, null, null), features, tools), "tool: non-plated");
        assertEquals(null, OdbToGerberConverter.featurePlating(pad(2, null, null), features, tools), "no tool of that size");
        assertEquals(null, OdbToGerberConverter.featurePlating(pad(0, null, null), features, null), "no tools file");

        Line slot = new Line();
        slot.setSymbolNumber(0);
        slot.setAttributeNumber(1);
        slot.setAttributeValue("1");
        assertEquals(Boolean.FALSE, OdbToGerberConverter.featurePlating(slot, features, tools));
    }
}

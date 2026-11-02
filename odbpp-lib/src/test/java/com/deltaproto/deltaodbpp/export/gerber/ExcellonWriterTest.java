package com.deltaproto.deltaodbpp.export.gerber;

import com.deltaproto.deltaodbpp.model.Features;
import com.deltaproto.deltaodbpp.model.Line;
import com.deltaproto.deltaodbpp.model.Pad;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcellonWriterTest {

    private static Features drillLayer(String units, String symbol) {
        Features features = new Features();
        features.setUnits(units);
        features.getSymbolTable().put(0, symbol);
        Pad pad = new Pad();
        pad.setX(10);
        pad.setY(20);
        pad.setSymbolNumber(0);
        features.getFeatures().add(pad);
        return features;
    }

    /** Symbol names are mils in an INCH feature file and microns in an MM one. */
    @Test
    void toolDiameterFollowsTheFeatureFileUnit() {
        String inch = new ExcellonWriter().export(drillLayer("INCH", "r9.8425"), null).content;
        assertTrue(inch.contains("T1C0.250\n"), inch);
        String mm = new ExcellonWriter().export(drillLayer("MM", "r250"), null).content;
        assertTrue(mm.contains("T1C0.250\n"), mm);
    }

    @Test
    void lineBecomesASlotOfTheSymbolWidth() {
        Features features = new Features();
        features.setUnits("MM");
        features.getSymbolTable().put(0, "r600");
        Line line = new Line();
        line.setXs(1);
        line.setYs(2);
        line.setXe(3);
        line.setYe(2);
        line.setSymbolNumber(0);
        features.getFeatures().add(line);
        ExcellonWriter.Result result = new ExcellonWriter().export(features, null);
        assertTrue(result.content.contains("T1C0.600\n"), result.content);
        assertTrue(result.content.contains("X1.0Y2.0G85X3.0Y2.0\n"), result.content);
    }
}

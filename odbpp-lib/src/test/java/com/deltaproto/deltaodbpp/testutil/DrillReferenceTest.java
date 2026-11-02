package com.deltaproto.deltaodbpp.testutil;

import com.deltaproto.deltagerber.model.drill.DrillDocument;
import com.deltaproto.deltagerber.parser.GerberParser;
import com.deltaproto.deltaodbpp.testutil.DrillReference.ExcellonReader;
import com.deltaproto.deltaodbpp.testutil.DrillReference.Format;
import com.deltaproto.deltaodbpp.testutil.DrillReference.Kind;
import com.deltaproto.deltaodbpp.testutil.DrillReference.Match;
import com.deltaproto.deltaodbpp.testutil.DrillReference.Offset;
import com.deltaproto.deltaodbpp.testutil.DrillReference.Op;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The drill-reference reader on synthetic input, one case per Excellon/Gerber dialect it handles. */
class DrillReferenceTest {

    private static final double EPS = 1e-6;
    private static final double TOL = DrillReference.TOLERANCE_MM;

    @Test
    void headerlessCadenceFileTakesItsFormatFromNcParam() {
        String drill = String.join("\n",
                ";LEADER: 12",
                ";   Holesize 1. = 0.300000 Tolerance = +0.000000/-0.000000 PLATED MM Quantity = 2",
                ";   Holesize 2. = 1.000000 Tolerance = +0.000000/-0.000000 PLATED MM Quantity = 1",
                "%", "G90",
                "X1250000Y2000000",      // 2.5 format, no suppression: 12.5, 20.0
                "X9900000Y0500000",
                "M00",
                "X3000000Y3000000",
                "M30", "");
        String ncParam = String.join("\n",
                "INTEGER-PLACES         2",
                "DECIMAL-PLACES         5",
                "OUTPUT-UNITS           METRIC",
                "SUPPRESS-LEAD-ZEROES   NO",
                "SUPPRESS-TRAIL-ZEROES  NO", "");
        DrillDocument doc = DrillReference.parseExcellon(drill, ncParam);
        List<Op> ops = DrillReference.ops(doc);
        assertEquals(3, ops.size());
        assertEquals(Kind.HIT, ops.get(0).kind());
        assertEquals(0.3, ops.get(0).diameter(), EPS);
        assertEquals(12.5, ops.get(0).x1(), EPS);
        assertEquals(20.0, ops.get(0).y1(), EPS);
        assertEquals(99.0, ops.get(1).x1(), EPS);
        assertEquals(5.0, ops.get(1).y1(), EPS);
        assertEquals(1.0, ops.get(2).diameter(), EPS);
        assertEquals(30.0, ops.get(2).x1(), EPS);
    }

    @Test
    void headerlessInchFileWithSuppressedLeadingZeros() {
        String drill = String.join("\n",
                ";   Holesize 1. = 0.040000 Tolerance = +0.000000/-0.000000 PLATED INCH Quantity = 1",
                "%",
                "X15000Y25000",          // 2.4 with leading zeros suppressed: 1.5 in, 2.5 in
                "M30", "");
        String ncParam = "INTEGER-PLACES 2\nDECIMAL-PLACES 4\nOUTPUT-UNITS INCHES\nSUPPRESS-LEAD-ZEROES YES\n";
        List<Op> ops = DrillReference.ops(DrillReference.parseExcellon(drill, ncParam));
        assertEquals(1, ops.size());
        assertEquals(1.5 * 25.4, ops.get(0).x1(), 1e-4);
        assertEquals(2.5 * 25.4, ops.get(0).y1(), 1e-4);
        assertEquals(0.04 * 25.4, ops.get(0).diameter(), 1e-4);
    }

    @Test
    void bodyToolRedefinitionSelectsTheTool() {
        String drill = String.join("\n",
                "M48", ";FILE_FORMAT=2:4", "INCH,LZ", "T1C.01", "T2C.02", "%", "M72", "G90",
                "T1C.01F0S0",
                "X010000Y020000",
                "T2C.02F0S0",
                "X030000Y040000",
                "M30", "");
        DrillDocument doc = DrillReference.parseExcellon(drill, null);
        assertTrue(doc.getWarnings().isEmpty(), doc.getWarnings().toString());
        List<Op> ops = DrillReference.ops(doc);
        assertEquals(2, ops.size());
        assertEquals(0.254, ops.get(0).diameter(), EPS);
        assertEquals(25.4, ops.get(0).x1(), EPS);
        assertEquals(0.508, ops.get(1).diameter(), EPS);
        assertEquals(3 * 25.4, ops.get(1).x1(), EPS);
    }

    /** A PADS file whose header was cut off starts at "%" and defines its tools in the body. */
    @Test
    void cutOffHeaderIsRebuiltFromBodyToolDefinitions() {
        String drill = String.join("\n",
                "%",
                "T1C.00984F0S0",
                "X003711Y000807",
                "T2C.03937F0S0",
                "X010000Y020000",
                "M30", "");
        DrillDocument doc = DrillReference.parseExcellon(drill, null);
        assertTrue(doc.getWarnings().isEmpty(), doc.getWarnings().toString());
        List<Op> ops = DrillReference.ops(doc);
        assertEquals(2, ops.size());
        assertEquals(0.25, ops.get(0).diameter(), 1e-3);
        assertEquals(0.3711 * 25.4, ops.get(0).x1(), 1e-4);
        assertEquals(1.0, ops.get(1).diameter(), 1e-3);
        assertEquals(25.4, ops.get(1).x1(), 1e-4);
    }

    @Test
    void routedPathsBecomeSlotsOrContours() {
        String rou = String.join("\n",
                "M48", "METRIC", ";FILE_FORMAT=3:4", "T01C1.0", "%", "G90",
                "T01",
                "G00X0100000Y0100000", "M15", "G01X0200000Y0100000", "M16",        // one segment: slot
                "G00X0500000Y0500000", "G41", "M15",
                "G01X0600000Y0500000", "G01X0600000Y0700000", "G01X0500000Y0700000", "G01X0500000Y0500000",
                "M16",                                                              // four segments: contour
                "M30", "");
        List<Op> ops = DrillReference.ops(DrillReference.parseExcellon(rou, null));
        assertEquals(2, ops.size());
        Op slot = ops.get(0);
        assertEquals(Kind.SLOT, slot.kind());
        assertEquals(10.0, slot.x1(), EPS);
        assertEquals(20.0, slot.x2(), EPS);
        Op contour = ops.get(1);
        assertEquals(Kind.CONTOUR, contour.kind());
        assertEquals(4, contour.segments());
        assertEquals(50.0, contour.bbox()[0], EPS);
        assertEquals(60.0, contour.bbox()[2], EPS);
        assertEquals(70.0, contour.bbox()[3], EPS);
    }

    /** Fixed-width coordinates with no declared format: the digit count gives the candidates. */
    @Test
    void formatCandidatesComeFromTheWidestWord() {
        String fixed = "M48\nMETRIC\nT01C.25\n%\nT01\nX02215000Y01202000\nX00151000Y00086000\nM30\n";
        assertFalse(DrillReference.hasExplicitFormat(fixed));
        List<Format> c = DrillReference.formatCandidates(fixed);
        assertEquals(List.of(new Format(2, 6, true), new Format(3, 5, true), new Format(4, 4, true)), c);

        String varying = "M48\nINCH\nT01C.009\n%\nT01\nX1597835Y497047\nX14764Y14764\nM30\n";
        List<Format> v = DrillReference.formatCandidates(varying);
        assertEquals(6, v.size(), "both zero modes for each integer width: " + v);

        assertTrue(DrillReference.hasExplicitFormat("M48\n;FILE_FORMAT=4:4\nMETRIC,LZ\n%\nX1Y2\n"));
        assertTrue(DrillReference.hasExplicitFormat("M48\nMETRIC\n%\nX10.5Y2\n"));
        assertTrue(DrillReference.formatCandidates("M48\nMETRIC\n%\nX10.5Y2\n").isEmpty());
    }

    /** The candidate whose extent matches the generated drill extent is the one used. */
    @Test
    void undeclaredFormatIsChosenByExtentAndRememberedForSiblings() {
        StringBuilder metric = new StringBuilder("M48\nMETRIC\nT01C.25\n%\nT01\n");
        List<Op> ours = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double x = 15.1 + i * 18.76;      // 4.4 format: 221.5 mm wide board
            double y = 8.6 + i * 10.15;
            metric.append(String.format(Locale.ROOT, "X%08dY%08d\n", Math.round(x * 10000), Math.round(y * 10000)));
            ours.add(Op.hit(0.25, x, y));
        }
        metric.append("M30\n");
        ExcellonReader reader = new ExcellonReader(DrillReference.span(ours));
        List<Op> ref = DrillReference.ops(reader.parse("a.drl", metric.toString(), null));
        assertEquals(12, ref.size());
        assertEquals(15.1, ref.get(0).x1(), EPS);
        assertEquals(221.46, ref.get(11).x1(), 1e-9);
        assertTrue(reader.notes().get(0).contains("4.4 LZ"), reader.notes().toString());

        // A sibling route file with two words cannot be judged by extent: it inherits 4.4.
        String rou = "M48\nMETRIC\nT01C2.\n%\nT01\nG00X00429990Y01315500\nM15\nG01X00489990Y01315500\nM16\nM30\n";
        List<Op> slots = DrillReference.ops(reader.parse("a.rou", rou, null));
        assertEquals(1, slots.size());
        assertEquals(42.999, slots.get(0).x1(), EPS);
        assertEquals(48.999, slots.get(0).x2(), EPS);
        assertEquals(131.55, slots.get(0).y1(), EPS);

        // Inch 3.5 with a 7-digit word among 8-digit ones: trailing-zero reading loses.
        StringBuilder inch = new StringBuilder("M48\nINCH\nT01C.009\n%\nT01\n");
        List<Op> oursInch = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double xIn = 0.14764 + i * 1.3;
            double yIn = 0.14764 + i * 0.4;
            inch.append(String.format(Locale.ROOT, "X%08dY%08d\n", Math.round(xIn * 100000), Math.round(yIn * 100000)));
            oursInch.add(Op.hit(0.2286, xIn * 25.4, yIn * 25.4));
        }
        inch.append("M30\n");
        ExcellonReader inchReader = new ExcellonReader(DrillReference.span(oursInch));
        List<Op> refInch = DrillReference.ops(inchReader.parse("b.drl", inch.toString(), null));
        assertEquals(0.14764 * 25.4, refInch.get(0).x1(), 1e-6);
        assertEquals((0.14764 + 11 * 1.3) * 25.4, refInch.get(11).x1(), 1e-6);
    }

    @Test
    void toleranceFollowsTheReferenceResolution() {
        DrillDocument inch24 = DrillReference.parseExcellon("M48\n;FILE_FORMAT=2:4\nINCH,LZ\nT1C.01\n%\nT1\nX010000Y020000\nM30\n", null);
        assertEquals(TOL + 0.00254 / 2, DrillReference.tolerance(inch24), 1e-9);
        DrillDocument mm42 = DrillReference.parseExcellon("M48\n;FILE_FORMAT=4:2\nMETRIC,TZ\nT1C0.3\n%\nT1\nX1000Y2000\nM30\n", null);
        assertEquals(TOL + 0.005, DrillReference.tolerance(mm42), 1e-9);
        DrillDocument decimal = DrillReference.parseExcellon("M48\nFMAT,2\nMETRIC\nT1C0.3\n%\nT1\nX10.5Y20.25\nM30\n", null);
        assertEquals(TOL + 0.0005, DrillReference.tolerance(decimal), 1e-9);
    }

    @Test
    void obroundFlashesAreSlotsAlongTheirLongAxis() {
        String gbr = String.join("\n",
                "%TF.FileFunction,Plated,1,2,PTH,Drill*%", "%FSLAX44Y44*%", "%MOMM*%",
                "%ADD10C,0.80000*%", "%ADD11O,2.50000X0.60000*%", "%ADD12O,0.60000X2.50000*%",
                "D10*", "X100000Y100000D03*",
                "D11*", "X200000Y100000D03*",
                "D12*", "X300000Y100000D03*",
                "%LR90*%", "D11*", "X400000Y100000D03*",
                "M02*", "");
        List<Op> ops = DrillReference.ops(new GerberParser().parse(gbr));
        assertEquals(4, ops.size());
        assertEquals(Kind.HIT, ops.get(0).kind());
        assertEquals(0.8, ops.get(0).diameter(), EPS);

        Op alongX = ops.get(1);
        assertEquals(Kind.SLOT, alongX.kind());
        assertEquals(0.6, alongX.diameter(), EPS);
        assertEquals(20 - 0.95, alongX.x1(), EPS);
        assertEquals(20 + 0.95, alongX.x2(), EPS);
        assertEquals(10, alongX.y1(), EPS);

        Op alongY = ops.get(2);
        assertEquals(30, alongY.x1(), EPS);
        assertEquals(10 - 0.95, alongY.y1(), EPS);
        assertEquals(10 + 0.95, alongY.y2(), EPS);

        Op rotated = ops.get(3);
        assertEquals(40, rotated.x1(), 1e-6);
        assertEquals(10 - 0.95, rotated.y1(), 1e-6);
        assertEquals(10 + 0.95, rotated.y2(), 1e-6);
    }

    @Test
    void offsetIsEstimatedFromTheCommonestToolAndAppliedToMatch() {
        List<Op> ours = List.of(Op.hit(0.3, 1, 1), Op.hit(0.3, 5, 2), Op.hit(0.3, 9, 7), Op.hit(1.0, 4, 4),
                Op.slot(0.5, 10, 10, 12, 10));
        double dx = 189.586, dy = 112.446;
        List<Op> ref = ours.stream().map(o -> o.translate(-dx, -dy)).toList();
        Offset offset = DrillReference.estimateOffset(ref, ours, TOL);
        assertTrue(offset.isSignificant(TOL));
        assertEquals(dx, offset.dx(), 1e-9);
        assertEquals(dy, offset.dy(), 1e-9);
        assertEquals(3, offset.supporting());

        Match raw = DrillReference.match(ref, ours, TOL);
        assertFalse(raw.isExact());
        Match shifted = DrillReference.match(ref.stream().map(o -> o.translate(offset.dx(), offset.dy())).toList(), ours, TOL);
        assertTrue(shifted.isExact(), () -> shifted.unmatchedRef().toString());
        assertEquals(4, shifted.matchedHits());
        assertEquals(1, shifted.matchedSlots());
    }

    /** A dense via grid shifted by many pitches: nearest neighbours lie, bounding boxes do not. */
    @Test
    void offsetIsFoundOnADenseGridShiftedFarAway() {
        List<Op> ours = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            for (int j = 0; j < 20; j++) {
                ours.add(Op.hit(0.2, 200 + i * 0.5, -90 + j * 0.5));
            }
        }
        ours.add(Op.hit(0.2, 211.3, -80.7));   // one hit the reference does not have
        List<Op> ref = ours.subList(0, 400).stream().map(o -> o.translate(-203.5, 89)).toList();
        Offset offset = DrillReference.estimateOffset(ref, ours, TOL);
        assertEquals(203.5, offset.dx(), 1e-9);
        assertEquals(-89, offset.dy(), 1e-9);
        assertEquals(400, offset.supporting());
    }

    @Test
    void platingMustAgreeWhenBothSidesStateIt() {
        Op refPlated = Op.hit(1.0, 5, 5).from("X-RoundHoles-Plated.TXT", DrillReference.platingOf("X-RoundHoles-Plated.TXT", null));
        Op oursNonPlated = Op.hit(1.0, 5, 5).from("NPTH.drl", false);
        Op oursPlated = Op.hit(1.0, 5, 5).from("PTH.drl", true);
        Op oursUnknown = Op.hit(1.0, 5, 5);
        assertFalse(DrillReference.match(List.of(refPlated), List.of(oursNonPlated), TOL).isExact());
        assertTrue(DrillReference.match(List.of(refPlated), List.of(oursPlated), TOL).isExact());
        assertTrue(DrillReference.match(List.of(refPlated), List.of(oursUnknown), TOL).isExact());
        assertEquals(Boolean.FALSE, DrillReference.platingOf("board-NPTH.drl", null));
        assertEquals(Boolean.FALSE, DrillReference.platingOf("ThruHoleNonPlated.ncd", null));
        assertEquals(Boolean.FALSE, DrillReference.platingOf("job-1-4-np.drl", null));
        assertEquals(Boolean.FALSE, DrillReference.platingOf("Talisman.uth", null));
        assertEquals(Boolean.TRUE, DrillReference.platingOf("NCDrillPlated.drl", null));
        assertEquals(Boolean.TRUE, DrillReference.platingOf("Talisman.psh", null));
        assertEquals(null, DrillReference.platingOf("job-1-4.drl", null));
        assertEquals(null, DrillReference.platingOf("Panel-RectHoles.TXT", null));
    }

    @Test
    void noOffsetWhenHitsDoNotLineUp() {
        List<Op> ours = List.of(Op.hit(0.3, 1, 1), Op.hit(0.3, 5, 2), Op.hit(0.3, 9, 7));
        List<Op> ref = List.of(Op.hit(0.3, 1.5, 1), Op.hit(0.3, 5, 2.7), Op.hit(0.3, 9.9, 7));
        Offset offset = DrillReference.estimateOffset(ref, ours, TOL);
        assertFalse(offset.isSignificant(TOL));
    }

    @Test
    void contourMatchesTheSlotItRoutsAroundAndACentrelineContourWithinToolRadius() {
        // A 1.9 x 2.7 rectangle routed with a 0.52 tool and G41 (path = cut edge) is the
        // ODB++ slot of width 1.9 from (0,-0.4) to (0,0.4).
        Op routed = Op.contour(0.52, new double[] {-0.95, -1.35, 0.95, 1.35}, 4);
        Op slot = Op.slot(1.9, 0, -0.4, 0, 0.4);
        assertTrue(DrillReference.match(List.of(routed), List.of(slot), TOL).isExact());
        // The same cut-out drawn as a centreline in Rout.gbr sits a tool radius inside.
        Op centreline = Op.contour(0.52, new double[] {-0.69, -1.09, 0.69, 1.09}, 4);
        assertTrue(DrillReference.match(List.of(routed), List.of(centreline), TOL).isExact());
        // A board-edge route runs a tool radius plus a kerf allowance outside the profile.
        Op edgeRoute = Op.contour(5.08, new double[] {-2.554, -2.554, 50.285, 56.285}, 190);
        Op profile = Op.contour(0.025, new double[] {0, 0, 47.738, 53.738}, 152);
        assertTrue(DrillReference.match(List.of(edgeRoute), List.of(profile), TOL).isExact());
        Op elsewhere = Op.contour(0.52, new double[] {5, 5, 7, 8}, 4);
        assertFalse(DrillReference.match(List.of(routed), List.of(elsewhere), TOL).isExact());
    }

    /** A reference slot the converter wrote as a rout-layer path still matches by extent. */
    @Test
    void referenceSlotMatchesAGeneratedRoutPath() {
        Op refSlot = Op.slot(1.0, 10, 10, 14, 10);
        Op routPath = Op.contour(1.0, new double[] {10, 10, 14, 10}, 1);
        assertTrue(DrillReference.match(List.of(refSlot), List.of(routPath), TOL).isExact());
    }

    @Test
    void generatedGerberPathsChainIntoContours() {
        String gbr = String.join("\n",
                "%FSLAX46Y46*%", "%MOMM*%", "%ADD10C,0.100000*%", "D10*",
                "X0Y0D02*", "X10000000Y0D01*", "X10000000Y5000000D01*", "X0Y5000000D01*", "X0Y0D01*",
                "X20000000Y0D02*", "X30000000Y0D01*",
                "G75*", "G02*", "X40000000Y0D02*", "X40000000Y0I-5000000J0D01*", "G01*",
                "M02*", "");
        List<Op> ops = DrillReference.contoursOf(new GerberParser().parse(gbr));
        assertEquals(3, ops.size());
        assertEquals(Kind.CONTOUR, ops.get(0).kind());
        assertEquals(4, ops.get(0).segments());
        assertEquals(10, ops.get(0).bbox()[2], EPS);
        assertEquals(5, ops.get(0).bbox()[3], EPS);
        assertEquals(Kind.CONTOUR, ops.get(1).kind(), "a rout-layer path is milled, never a drilled slot");
        assertEquals(1, ops.get(1).segments());
        Op circle = ops.get(2);
        assertEquals(Kind.CONTOUR, circle.kind());
        assertEquals(30, circle.bbox()[0], 1e-6);
        assertEquals(-5, circle.bbox()[1], 1e-6);
        assertEquals(40, circle.bbox()[2], 1e-6);
        assertEquals(5, circle.bbox()[3], 1e-6);
    }
}

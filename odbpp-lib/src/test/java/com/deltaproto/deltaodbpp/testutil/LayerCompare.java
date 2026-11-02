package com.deltaproto.deltaodbpp.testutil;

import com.deltaproto.deltagerber.model.gerber.GerberDocument;
import com.deltaproto.deltagerber.model.gerber.attribute.FileAttribute;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Compares a generated Gerber layer with a reference Gerber of the same layer the way
 * {@code GerberReferenceTest} and {@code OdbGerberPairLayerTest} both need it: both documents
 * are rasterised over a common window ({@link GerberRaster}) and the area inked by only one of
 * them is measured against the inked union, with a one-pixel tolerance band for edge jitter.
 * A layer passes below {@link #MAX_MISMATCH_FRACTION}. The X2 file function and polarity are
 * checked separately when the reference declares them.
 *
 * <p>{@code -Dgerber.compare.maxPixels=<n>} lowers the raster size ceiling (and so the
 * resolution on large boards) for a quicker pass over a big corpus.
 */
public final class LayerCompare {

    /** Raster resolution ceiling: 100 px/mm = 10 µm pixels. */
    public static final double MAX_PX_PER_MM = 100;
    /** Raster size ceiling; the resolution drops on large boards to stay under it. */
    public static final double MAX_PIXELS = Double.parseDouble(System.getProperty("gerber.compare.maxPixels", "40e6"));
    /** Edge jitter tolerance in pixels. */
    public static final int TOLERANCE_PX = 1;
    /** Maximum area inked by only one of the two images, as a fraction of the inked union. */
    public static final double MAX_MISMATCH_FRACTION = 0.001;

    private LayerCompare() {
    }

    /** Outcome of {@link #compare}. Pixel counts; {@code diff} is set when one was written. */
    public record Result(double mismatchFraction, double pxPerMm, long onlyReference, long onlyGenerated,
                         long union, boolean bothEmpty, Path diff) {
        public boolean passes() {
            return mismatchFraction <= MAX_MISMATCH_FRACTION;
        }

        public String summary() {
            if (bothEmpty) {
                return "both empty";
            }
            return String.format(Locale.ROOT,
                    "mismatch %.4f%% at %.0f px/mm (only reference: %d px, only ours: %d px, union %d px)",
                    mismatchFraction * 100, pxPerMm, onlyReference, onlyGenerated, union);
        }
    }

    /**
     * Why the generated file's X2 function or polarity disagrees with the reference's, or null
     * when they agree or the reference declares none. Altium appends {@code ,Signal} /
     * {@code ,Plane} to copper functions; only the specified part is compared.
     */
    public static String fileFunctionMismatch(GerberDocument reference, GerberDocument generated) {
        List<String> refFunction = reference.getFileFunctionValues();
        if (refFunction == null || refFunction.isEmpty()) {
            return null;
        }
        List<String> genFunction = generated.getFileFunctionValues();
        int n = "Copper".equals(refFunction.get(0)) ? 3 : refFunction.size();
        List<String> expected = refFunction.subList(0, Math.min(n, refFunction.size()));
        List<String> actual = genFunction == null ? List.of() : genFunction.subList(0, Math.min(n, genFunction.size()));
        if (!expected.equals(actual)) {
            return ".FileFunction " + actual + " differs from the reference's " + expected;
        }
        String refPolarity = attribute(reference, ".FilePolarity");
        String genPolarity = attribute(generated, ".FilePolarity");
        if (refPolarity != null && !refPolarity.equals(genPolarity)) {
            return ".FilePolarity " + genPolarity + " differs from the reference's " + refPolarity;
        }
        return null;
    }

    private static String attribute(GerberDocument doc, String name) {
        FileAttribute attr = doc.getFileAttributes().get(name);
        return attr == null ? null : String.valueOf(attr.getValues());
    }

    /**
     * Rasterises and compares the two documents. When the comparison fails and
     * {@code diffIfFailing} is given, a diff image is written there.
     */
    public static Result compare(GerberDocument reference, GerberDocument generated, Path diffIfFailing)
            throws IOException {
        return compare(reference, generated, List.of(), MAX_MISMATCH_FRACTION, diffIfFailing);
    }

    /**
     * As {@link #compare(GerberDocument, GerberDocument, Path)}, with everything the
     * {@code masks} ink (dilated by the tolerance) cleared from both images first, and a diff
     * written when the mismatch exceeds {@code maxMismatch}.
     */
    public static Result compare(GerberDocument reference, GerberDocument generated, List<GerberDocument> masks,
                                 double maxMismatch, Path diffIfFailing) throws IOException {
        Rectangle2D window = GerberRaster.window(reference, generated, 1.0);
        if (window == null) {
            return new Result(0, 0, 0, 0, 0, true, null);
        }
        double pxPerMm = resolution(window);
        List<BufferedImage> maskImages = new ArrayList<>();
        for (GerberDocument mask : masks) {
            maskImages.add(GerberRaster.rasterise(mask, window, pxPerMm));
        }
        return compare(reference, generated, window, pxPerMm, maskImages, maxMismatch, diffIfFailing);
    }

    /** The resolution the raster ceiling allows for a window. */
    public static double resolution(Rectangle2D window) {
        return Math.min(MAX_PX_PER_MM, Math.sqrt(MAX_PIXELS / (window.getWidth() * window.getHeight())));
    }

    /**
     * Compares over a given window and resolution, with the inked pixels of each mask image
     * (dilated by the tolerance) cleared from both sides first.
     */
    public static Result compare(GerberDocument reference, GerberDocument generated, Rectangle2D window,
                                 double pxPerMm, List<BufferedImage> masks, double maxMismatch, Path diffIfFailing)
            throws IOException {
        BufferedImage ref = GerberRaster.rasterise(reference, window, pxPerMm);
        BufferedImage gen = GerberRaster.rasterise(generated, window, pxPerMm);
        for (BufferedImage m : masks) {
            GerberRaster.clear(ref, m, TOLERANCE_PX + 1);
            GerberRaster.clear(gen, m, TOLERANCE_PX + 1);
        }
        GerberRaster.Comparison cmp = GerberRaster.compare(ref, gen, TOLERANCE_PX);
        Path diff = null;
        if (cmp.mismatchFraction() > maxMismatch && diffIfFailing != null) {
            GerberRaster.writeDiff(ref, gen, TOLERANCE_PX, diffIfFailing);
            diff = diffIfFailing;
        }
        return new Result(cmp.mismatchFraction(), pxPerMm, cmp.onlyA(), cmp.onlyB(), cmp.union(), false, diff);
    }
}

package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.parser.OdbParser;
import com.deltaproto.deltaodbpp.testutil.Fixtures;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility to (re)generate the committed minimal-odb golden reference SVG from the
 * synthetic, committed minimal-odb fixture.
 *
 * <p>Run this only when an intentional rendering change requires the golden file
 * {@link Fixtures#MINIMAL_ODB_REFERENCE} to be refreshed. The golden is the single
 * source of truth for the exact-match comparison in the SVG export tests.
 */
public class SvgReferenceGenerator {

    public static void main(String[] args) throws IOException {
        OdbParser parser = new OdbParser();

        // Regenerate ONLY the committed minimal-odb golden reference.
        generateReference(parser, Fixtures.MINIMAL_ODB, Fixtures.MINIMAL_ODB_REFERENCE);

        System.out.println("minimal-odb reference SVG generated successfully!");
    }

    private static void generateReference(OdbParser parser, Path odbPath, Path outputPath) throws IOException {
        if (!Files.exists(odbPath)) {
            System.out.println("Skipping " + odbPath + " - not found");
            return;
        }

        System.out.println("Generating: " + outputPath);
        Job job = parser.parse(odbPath);
        StringWriter writer = new StringWriter();
        // Create a new renderer to avoid stale global bounds.
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer();
        renderer.renderJob(job, writer);
        Files.writeString(outputPath, writer.toString());
        System.out.println("  Written: " + Files.size(outputPath) + " bytes");
    }
}

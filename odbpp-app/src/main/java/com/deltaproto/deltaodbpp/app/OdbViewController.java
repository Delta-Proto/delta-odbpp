package com.deltaproto.deltaodbpp.app;

import com.deltaproto.deltaodbpp.OdbArchiveExtractor;
import com.deltaproto.deltaodbpp.export.BomView;
import com.deltaproto.deltaodbpp.export.MultiLayerSvgRenderer;
import com.deltaproto.deltaodbpp.export.StackupView;
import com.deltaproto.deltaodbpp.export.SvgRenderOptions;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Matrix;
import com.deltaproto.deltaodbpp.model.MatrixLayer;
import com.deltaproto.deltaodbpp.model.Step;
import com.deltaproto.deltaodbpp.parser.OdbParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Controller for ODB++ file upload and SVG rendering.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /                      — serves the HTML viewer app</li>
 *   <li>POST /api/odbpp/render     — receives ODB++ archive, returns multi-layer + realistic SVGs</li>
 *   <li>POST /api/odbpp/thumbnail  — receives ODB++ archive, returns a rasterised PNG
 *       of one realistic side view (query: side=top|bottom, width, height)</li>
 * </ul>
 *
 * <p>API endpoints carry {@code @CrossOrigin} so the UI can be served from the
 * dp website while the Java service lives on a different origin (same pattern
 * used by the sibling gerber project).
 */
@Controller
@CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.POST},
        allowedHeaders = "*", maxAge = 3600)
public class OdbViewController {

    private static final Logger logger = LoggerFactory.getLogger(OdbViewController.class);

    private final OdbArchiveExtractor extractor = new OdbArchiveExtractor();
    private final OdbParser parser = new OdbParser();

    @GetMapping("/")
    public String index() {
        return "index";
    }

    /**
     * Main render endpoint. Accepts an ODB++ archive and returns:
     * - Combined multi-layer SVG (all layers, for layer toggling)
     * - Realistic top-side SVG (or null)
     * - Realistic bottom-side SVG (or null)
     * - Layer metadata for the sidebar
     */
    @PostMapping("/api/odbpp/render")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> render(
            @RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        if (file.isEmpty()) {
            response.put("error", "Please select a file to upload");
            return ResponseEntity.badRequest().body(response);
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !isValidArchive(filename)) {
            response.put("error", "Please upload a valid ODB++ archive (.zip, .tgz, or .tar.gz)");
            return ResponseEntity.badRequest().body(response);
        }

        Path tempFile = null;
        Path extractDir = null;
        try {
            long startTime = System.currentTimeMillis();

            // Save uploaded file to temp location
            tempFile = Files.createTempFile("odbpp_upload_", getExtension(filename));
            file.transferTo(tempFile.toFile());

            // Extract the archive
            extractDir = Files.createTempDirectory("odbpp_extracted_");
            Path odbRoot = extractor.extract(tempFile, extractDir);

            // Parse the ODB++
            Job job = parser.parse(odbRoot);

            // Render combined view (all layers, top perspective). Components only
            // appear via the dedicated Components Top/Bottom tabs, never as an overlay
            // on the layer/realistic views.
            SvgRenderOptions options = new SvgRenderOptions()
                    .withOutputUnit(SvgRenderOptions.OutputUnit.MM);
            MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer(options);
            StringWriter svgWriter = new StringWriter();
            renderer.renderJob(job, svgWriter);

            // Render realistic views
            String realisticTopSvg = renderRealisticSide(job, true);
            String realisticBottomSvg = renderRealisticSide(job, false);

            // Render component silhouette views
            String componentsTopSvg = renderComponentsSide(job, true);
            String componentsBottomSvg = renderComponentsSide(job, false);

            // Stackup cross-section data
            List<StackupView.Entry> stackup = StackupView.build(job);

            // Combined BOM + centroid data
            List<BomView.Row> bill = BomView.build(job);

            // Build layer metadata
            List<Map<String, String>> layers = buildLayerMetadata(job);

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Rendered {} in {}ms ({} layers, realistic: top={} bottom={}, components: top={} bottom={})",
                    filename, elapsed, layers.size(),
                    realisticTopSvg != null, realisticBottomSvg != null,
                    componentsTopSvg != null, componentsBottomSvg != null);

            response.put("svg", svgWriter.toString());
            response.put("realisticTopSvg", realisticTopSvg);
            response.put("realisticBottomSvg", realisticBottomSvg);
            response.put("componentsTopSvg", componentsTopSvg);
            response.put("componentsBottomSvg", componentsBottomSvg);
            response.put("stackup", stackup);
            response.put("stackupTotalMm", StackupView.totalThicknessMm(stackup));
            response.put("bill", bill);
            response.put("filename", filename);
            response.put("stepCount", job.getSteps().size());
            response.put("layers", layers);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error processing ODB++ file", e);
            response.put("error", "Error processing file: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        } finally {
            cleanup(tempFile);
            cleanup(extractDir);
        }
    }

    /**
     * PNG thumbnail endpoint. Rasterises the realistic top or bottom view of an
     * uploaded archive via Apache Batik. Matches the shape of gerber's
     * {@code /api/gerber/thumbnail} endpoint so dp website integration is
     * consistent across the two viewers.
     *
     * @param file   the archive (same formats accepted by /render)
     * @param side   "top" or "bottom"
     * @param width  target width in pixels (0..4000, 0 = derive from height)
     * @param height target height in pixels (0..4000, 0 = derive from width)
     */
    @PostMapping(value = "/api/odbpp/thumbnail", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> thumbnail(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "side", defaultValue = "top") String side,
            @RequestParam(value = "width", defaultValue = "800") int width,
            @RequestParam(value = "height", defaultValue = "0") int height) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("Please select a file to upload".getBytes());
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !isValidArchive(filename)) {
            return ResponseEntity.badRequest()
                    .body("Invalid archive format".getBytes());
        }
        boolean topSide = !"bottom".equalsIgnoreCase(side);
        int clampedWidth = Math.max(0, Math.min(width, 4000));
        int clampedHeight = Math.max(0, Math.min(height, 4000));
        if (clampedWidth == 0 && clampedHeight == 0) {
            clampedWidth = 800;
        }

        Path tempFile = null;
        Path extractDir = null;
        try {
            long startTime = System.currentTimeMillis();
            tempFile = Files.createTempFile("odbpp_thumb_", getExtension(filename));
            file.transferTo(tempFile.toFile());
            extractDir = Files.createTempDirectory("odbpp_thumb_ext_");
            Path odbRoot = extractor.extract(tempFile, extractDir);
            Job job = parser.parse(odbRoot);

            SvgRenderOptions options = new SvgRenderOptions()
                    .withOutputUnit(SvgRenderOptions.OutputUnit.MM);
            MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer(options);
            byte[] png = renderer.renderRealisticSidePng(job, topSide, clampedWidth, clampedHeight);

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Thumbnail {} {}x{} for {} in {}ms ({} bytes)",
                    topSide ? "top" : "bottom", clampedWidth, clampedHeight,
                    filename, elapsed, png.length);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setCacheControl("no-store");
            return ResponseEntity.ok().headers(headers).body(png);

        } catch (Exception e) {
            logger.error("Thumbnail generation failed for {}", filename, e);
            return ResponseEntity.internalServerError()
                    .body(("Thumbnail failed: " + e.getMessage()).getBytes());
        } finally {
            cleanup(tempFile);
            cleanup(extractDir);
        }
    }

    /**
     * Serves the bundled example ODB++ archive. The UI's "Try Example" button
     * fetches this, then hands the blob to the render endpoint. Mirrors the
     * gerber viewer's {@code /api/gerber/arduino-uno-example.zip} endpoint.
     */
    @GetMapping("/api/odbpp/example.zip")
    @ResponseBody
    public ResponseEntity<byte[]> getExampleArchive() throws IOException {
        try (var is = getClass().getResourceAsStream("/static/examples/odbpp-sample.zip")) {
            if (is == null) {
                return ResponseEntity.notFound().build();
            }
            byte[] bytes = is.readAllBytes();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "odbpp-sample.zip");
            headers.setCacheControl("public, max-age=3600");
            return ResponseEntity.ok().headers(headers).body(bytes);
        }
    }

    @GetMapping(value = "/sample-svg", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String getSampleSvg() throws IOException {
        StringWriter writer = new StringWriter();
        Job emptyJob = new Job();
        SvgRenderOptions options = new SvgRenderOptions()
                .withOutputUnit(SvgRenderOptions.OutputUnit.MM);
        MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer(options);
        renderer.renderJob(emptyJob, writer);
        return writer.toString();
    }

    private String renderRealisticSide(Job job, boolean topSide) {
        try {
            SvgRenderOptions options = new SvgRenderOptions()
                    .withOutputUnit(SvgRenderOptions.OutputUnit.MM);
            MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer(options);
            StringWriter writer = new StringWriter();
            renderer.renderRealisticJob(job, topSide, writer);
            String svg = writer.toString();
            // Check if it's just an empty SVG
            if (svg.contains("<!-- Empty design -->") || svg.length() < 200) {
                return null;
            }
            return svg;
        } catch (Exception e) {
            logger.warn("Failed to render realistic {} side: {}",
                    topSide ? "top" : "bottom", e.getMessage());
            return null;
        }
    }

    private String renderComponentsSide(Job job, boolean topSide) {
        try {
            SvgRenderOptions options = new SvgRenderOptions()
                    .withOutputUnit(SvgRenderOptions.OutputUnit.MM);
            MultiLayerSvgRenderer renderer = new MultiLayerSvgRenderer(options);
            StringWriter writer = new StringWriter();
            renderer.renderComponentsJob(job, topSide, writer);
            String svg = writer.toString();
            if (svg.contains("<!-- Empty design -->") || svg.length() < 200) return null;
            // Also treat "no components rendered" as null — profile but no comp silhouettes isn't useful.
            if (!svg.contains("data-refdes=")) return null;
            return svg;
        } catch (Exception e) {
            logger.warn("Failed to render components {} side: {}",
                    topSide ? "top" : "bottom", e.getMessage());
            return null;
        }
    }

    private List<Map<String, String>> buildLayerMetadata(Job job) {
        List<Map<String, String>> layers = new ArrayList<>();
        if (job.getSteps() == null || job.getSteps().isEmpty()) return layers;

        Step step = job.getSteps().values().iterator().next();
        Matrix matrix = job.getMatrix();

        Map<String, MatrixLayer> matrixMap = new HashMap<>();
        List<String> matrixOrder = new ArrayList<>();
        if (matrix != null && matrix.getLayers() != null) {
            List<MatrixLayer> sorted = new ArrayList<>(matrix.getLayers());
            sorted.sort(Comparator.comparingInt(MatrixLayer::getRow));
            for (MatrixLayer ml : sorted) {
                String key = ml.getName().toLowerCase(Locale.ROOT);
                matrixMap.put(key, ml);
                matrixOrder.add(key);
            }
        }

        // Emit layers in matrix-row order — the spec-defined physical stackup
        // (top-side → copper top..bot → bottom-side → drill/rout → docs).
        // Anything not declared in the matrix gets appended afterwards.
        Map<String, String> stepLayerKeys = new LinkedHashMap<>();
        for (String name : step.getLayersByName().keySet()) {
            stepLayerKeys.put(name.toLowerCase(Locale.ROOT), name);
        }
        Set<String> emitted = new HashSet<>();
        for (String key : matrixOrder) {
            String realName = stepLayerKeys.get(key);
            if (realName == null) continue;
            layers.add(layerMeta(realName, matrixMap.get(key)));
            emitted.add(key);
        }
        for (Map.Entry<String, String> entry : stepLayerKeys.entrySet()) {
            if (emitted.contains(entry.getKey())) continue;
            layers.add(layerMeta(entry.getValue(), null));
        }

        return layers;
    }

    private Map<String, String> layerMeta(String layerName, MatrixLayer ml) {
        Map<String, String> meta = new HashMap<>();
        meta.put("name", layerName);
        meta.put("id", "layer-" + layerName.replaceAll("[^a-zA-Z0-9_-]", "_"));
        meta.put("type", ml != null && ml.getType() != null ? ml.getType() : "DOCUMENT");
        meta.put("context", ml != null && ml.getContext() != null ? ml.getContext() : "");
        return meta;
    }

    private boolean isValidArchive(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".zip") ||
               lower.endsWith(".tgz") ||
               lower.endsWith(".tar.gz");
    }

    private String getExtension(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".tar.gz")) return ".tar.gz";
        if (lower.endsWith(".tgz")) return ".tgz";
        if (lower.endsWith(".zip")) return ".zip";
        return "";
    }

    private void cleanup(Path path) {
        if (path == null) return;
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.walk(path)) {
                    stream.sorted((a, b) -> b.compareTo(a))
                          .forEach(p -> {
                              try {
                                  Files.deleteIfExists(p);
                              } catch (IOException ignored) {}
                          });
                }
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {}
    }
}

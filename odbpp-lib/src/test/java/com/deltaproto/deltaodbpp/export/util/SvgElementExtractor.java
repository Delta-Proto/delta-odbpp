package com.deltaproto.deltaodbpp.export.util;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Utility class for extracting SVG elements from an SVG file for comparison testing.
 * Parses circles, lines, paths and groups them by layer.
 */
public class SvgElementExtractor {

    /** Conversion factor from inches to mm */
    public static final double INCH_TO_MM = 25.4;

    private final String svgContent;
    private final double scaleFactor;
    private List<CircleElement> circles;
    private List<LineElement> lines;
    private List<PathElement> paths;
    private List<LayerGroup> layers;
    private List<RectElement> rects;

    public SvgElementExtractor(String svgContent) {
        this(svgContent, 1.0);
    }

    public SvgElementExtractor(String svgContent, double scaleFactor) {
        this.svgContent = svgContent;
        this.scaleFactor = scaleFactor;
        parseElements();
    }

    public static SvgElementExtractor fromFile(Path file) throws IOException {
        return new SvgElementExtractor(Files.readString(file));
    }

    /**
     * Parse from file with coordinate scaling.
     * Use INCH_TO_MM to convert inch coordinates to mm.
     */
    public static SvgElementExtractor fromFile(Path file, double scaleFactor) throws IOException {
        return new SvgElementExtractor(Files.readString(file), scaleFactor);
    }

    public static SvgElementExtractor fromString(String svg) {
        return new SvgElementExtractor(svg);
    }

    /**
     * Parse from string with coordinate scaling.
     * Use INCH_TO_MM to convert inch coordinates to mm.
     */
    public static SvgElementExtractor fromString(String svg, double scaleFactor) {
        return new SvgElementExtractor(svg, scaleFactor);
    }

    private void parseElements() {
        circles = new ArrayList<>();
        lines = new ArrayList<>();
        paths = new ArrayList<>();
        layers = new ArrayList<>();
        rects = new ArrayList<>();

        // Parse using regex for simplicity and robustness with various SVG formats
        parseCircles();
        parseLines();
        parsePaths();
        parseLayers();
        parseRects();
    }

    private void parseCircles() {
        // Match circle elements: <circle cx="..." cy="..." r="..." ...>
        Pattern circlePattern = Pattern.compile(
            "<circle[^>]*\\scx=[\"']([^\"']+)[\"'][^>]*\\scy=[\"']([^\"']+)[\"'][^>]*\\sr=[\"']([^\"']+)[\"'][^>]*>",
            Pattern.DOTALL);

        Matcher matcher = circlePattern.matcher(svgContent);
        while (matcher.find()) {
            try {
                double cx = parseDouble(matcher.group(1)) * scaleFactor;
                double cy = parseDouble(matcher.group(2)) * scaleFactor;
                double r = parseDouble(matcher.group(3)) * scaleFactor;
                circles.add(new CircleElement(cx, cy, r));
            } catch (NumberFormatException e) {
                // Skip malformed elements
            }
        }

        // Also try alternate attribute order (r before cx/cy)
        Pattern circlePattern2 = Pattern.compile(
            "<circle[^>]*\\sr=[\"']([^\"']+)[\"'][^>]*\\scx=[\"']([^\"']+)[\"'][^>]*\\scy=[\"']([^\"']+)[\"'][^>]*>",
            Pattern.DOTALL);

        matcher = circlePattern2.matcher(svgContent);
        while (matcher.find()) {
            try {
                double r = parseDouble(matcher.group(1)) * scaleFactor;
                double cx = parseDouble(matcher.group(2)) * scaleFactor;
                double cy = parseDouble(matcher.group(3)) * scaleFactor;
                // Avoid duplicates
                CircleElement circle = new CircleElement(cx, cy, r);
                if (!containsCircle(circles, circle, 0.0001 * scaleFactor)) {
                    circles.add(circle);
                }
            } catch (NumberFormatException e) {
                // Skip malformed elements
            }
        }
    }

    private boolean containsCircle(List<CircleElement> list, CircleElement circle, double tolerance) {
        for (CircleElement c : list) {
            if (Math.abs(c.cx - circle.cx) < tolerance &&
                Math.abs(c.cy - circle.cy) < tolerance &&
                Math.abs(c.r - circle.r) < tolerance) {
                return true;
            }
        }
        return false;
    }

    private void parseLines() {
        // Match line elements: <line x1="..." y1="..." x2="..." y2="..." ...>
        Pattern linePattern = Pattern.compile(
            "<line[^>]*\\sx1=[\"']([^\"']+)[\"'][^>]*\\sy1=[\"']([^\"']+)[\"'][^>]*\\sx2=[\"']([^\"']+)[\"'][^>]*\\sy2=[\"']([^\"']+)[\"'][^>]*>",
            Pattern.DOTALL);

        Matcher matcher = linePattern.matcher(svgContent);
        while (matcher.find()) {
            try {
                double x1 = parseDouble(matcher.group(1)) * scaleFactor;
                double y1 = parseDouble(matcher.group(2)) * scaleFactor;
                double x2 = parseDouble(matcher.group(3)) * scaleFactor;
                double y2 = parseDouble(matcher.group(4)) * scaleFactor;
                lines.add(new LineElement(x1, y1, x2, y2));
            } catch (NumberFormatException e) {
                // Skip malformed elements
            }
        }

        // Also parse lines from path elements with simple "M x1 y1 L x2 y2" pattern
        // This allows treating <path d="M x1 y1 L x2 y2"> as equivalent to <line>
        Pattern pathLinePattern = Pattern.compile(
            "<path[^>]*\\sd=[\"']M\\s*([\\d.e+-]+)\\s+([\\d.e+-]+)\\s+(?:L\\s*)?([\\d.e+-]+)\\s+([\\d.e+-]+)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);

        matcher = pathLinePattern.matcher(svgContent);
        while (matcher.find()) {
            try {
                double x1 = parseDouble(matcher.group(1)) * scaleFactor;
                double y1 = parseDouble(matcher.group(2)) * scaleFactor;
                double x2 = parseDouble(matcher.group(3)) * scaleFactor;
                double y2 = parseDouble(matcher.group(4)) * scaleFactor;
                lines.add(new LineElement(x1, y1, x2, y2));
            } catch (NumberFormatException e) {
                // Skip malformed elements
            }
        }
    }

    private void parsePaths() {
        // Match path elements: <path d="..." ...>
        Pattern pathPattern = Pattern.compile(
            "<path[^>]*\\sd=[\"']([^\"']+)[\"'][^>]*>",
            Pattern.DOTALL);

        Matcher matcher = pathPattern.matcher(svgContent);
        while (matcher.find()) {
            String pathData = matcher.group(1);
            paths.add(new PathElement(pathData));
        }
    }

    private void parseLayers() {
        // Match layer groups: <g class="layer" id="..." ...> or <g ... class="layer layer-..." ...>
        Pattern layerPattern = Pattern.compile(
            "<g[^>]*\\sclass=[\"'][^\"']*layer[^\"']*[\"'][^>]*\\sid=[\"']([^\"']+)[\"'][^>]*>",
            Pattern.DOTALL);

        Matcher matcher = layerPattern.matcher(svgContent);
        while (matcher.find()) {
            String id = matcher.group(1);
            layers.add(new LayerGroup(id));
        }

        // Also try alternate format with id before class
        Pattern layerPattern2 = Pattern.compile(
            "<g[^>]*\\sid=[\"']([^\"']+)[\"'][^>]*\\sclass=[\"'][^\"']*layer[^\"']*[\"'][^>]*>",
            Pattern.DOTALL);

        matcher = layerPattern2.matcher(svgContent);
        while (matcher.find()) {
            String id = matcher.group(1);
            if (!containsLayer(id)) {
                layers.add(new LayerGroup(id));
            }
        }
    }

    private boolean containsLayer(String id) {
        for (LayerGroup layer : layers) {
            if (layer.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private void parseRects() {
        // Match rect elements: <rect x="..." y="..." width="..." height="..." ...>
        Pattern rectPattern = Pattern.compile(
            "<rect[^>]*\\sx=[\"']([^\"']+)[\"'][^>]*\\sy=[\"']([^\"']+)[\"'][^>]*" +
            "\\swidth=[\"']([^\"']+)[\"'][^>]*\\sheight=[\"']([^\"']+)[\"'][^>]*>",
            Pattern.DOTALL);

        Matcher matcher = rectPattern.matcher(svgContent);
        while (matcher.find()) {
            try {
                double x = parseDouble(matcher.group(1)) * scaleFactor;
                double y = parseDouble(matcher.group(2)) * scaleFactor;
                double width = parseDouble(matcher.group(3)) * scaleFactor;
                double height = parseDouble(matcher.group(4)) * scaleFactor;
                rects.add(new RectElement(x, y, width, height));
            } catch (NumberFormatException e) {
                // Skip malformed elements
            }
        }
    }

    private double parseDouble(String value) {
        // Handle scientific notation
        return Double.parseDouble(value.trim());
    }

    // Getters
    public List<CircleElement> getCircles() {
        return circles;
    }

    public List<LineElement> getLines() {
        return lines;
    }

    public List<PathElement> getPaths() {
        return paths;
    }

    public List<LayerGroup> getLayers() {
        return layers;
    }

    public List<RectElement> getRects() {
        return rects;
    }

    public int getCircleCount() {
        return circles.size();
    }

    public int getLineCount() {
        return lines.size();
    }

    public int getPathCount() {
        return paths.size();
    }

    public int getRectCount() {
        return rects.size();
    }

    /**
     * Find a circle at the given position within tolerance.
     */
    public Optional<CircleElement> findCircleAt(double x, double y, double tolerance) {
        for (CircleElement circle : circles) {
            if (Math.abs(circle.cx - x) <= tolerance && Math.abs(circle.cy - y) <= tolerance) {
                return Optional.of(circle);
            }
        }
        return Optional.empty();
    }

    /**
     * Find circles with the given radius within tolerance.
     */
    public List<CircleElement> findCirclesWithRadius(double radius, double tolerance) {
        List<CircleElement> result = new ArrayList<>();
        for (CircleElement circle : circles) {
            if (Math.abs(circle.r - radius) <= tolerance) {
                result.add(circle);
            }
        }
        return result;
    }

    /**
     * Count paths containing a specific command (M, L, A, Z, etc.)
     */
    public int countPathsWithCommand(String command) {
        int count = 0;
        for (PathElement path : paths) {
            if (path.containsCommand(command)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Find a line at the given start/end position within tolerance.
     */
    public Optional<LineElement> findLineAt(double x1, double y1, double x2, double y2, double tolerance) {
        for (LineElement line : lines) {
            // Check both directions (line could be defined either way)
            boolean matchForward = Math.abs(line.x1 - x1) <= tolerance && Math.abs(line.y1 - y1) <= tolerance &&
                                   Math.abs(line.x2 - x2) <= tolerance && Math.abs(line.y2 - y2) <= tolerance;
            boolean matchBackward = Math.abs(line.x1 - x2) <= tolerance && Math.abs(line.y1 - y2) <= tolerance &&
                                    Math.abs(line.x2 - x1) <= tolerance && Math.abs(line.y2 - y1) <= tolerance;
            if (matchForward || matchBackward) {
                return Optional.of(line);
            }
        }
        return Optional.empty();
    }

    /**
     * Find lines with a given start point within tolerance.
     */
    public List<LineElement> findLinesStartingAt(double x, double y, double tolerance) {
        List<LineElement> result = new ArrayList<>();
        for (LineElement line : lines) {
            if ((Math.abs(line.x1 - x) <= tolerance && Math.abs(line.y1 - y) <= tolerance) ||
                (Math.abs(line.x2 - x) <= tolerance && Math.abs(line.y2 - y) <= tolerance)) {
                result.add(line);
            }
        }
        return result;
    }

    /**
     * Find a rectangle at the given position within tolerance.
     */
    public Optional<RectElement> findRectAt(double x, double y, double tolerance) {
        for (RectElement rect : rects) {
            if (Math.abs(rect.x - x) <= tolerance && Math.abs(rect.y - y) <= tolerance) {
                return Optional.of(rect);
            }
        }
        return Optional.empty();
    }

    /**
     * Get all paths containing arcs (A command).
     */
    public List<PathElement> getArcPaths() {
        List<PathElement> result = new ArrayList<>();
        for (PathElement path : paths) {
            if (path.hasArc()) {
                result.add(path);
            }
        }
        return result;
    }

    /**
     * Get all closed paths (paths with Z command).
     */
    public List<PathElement> getClosedPaths() {
        List<PathElement> result = new ArrayList<>();
        for (PathElement path : paths) {
            if (path.hasClosePath()) {
                result.add(path);
            }
        }
        return result;
    }

    // Element classes
    public static class CircleElement {
        public final double cx;
        public final double cy;
        public final double r;

        public CircleElement(double cx, double cy, double r) {
            this.cx = cx;
            this.cy = cy;
            this.r = r;
        }

        @Override
        public String toString() {
            return String.format("Circle(cx=%.6f, cy=%.6f, r=%.6f)", cx, cy, r);
        }
    }

    public static class LineElement {
        public final double x1;
        public final double y1;
        public final double x2;
        public final double y2;

        public LineElement(double x1, double y1, double x2, double y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        @Override
        public String toString() {
            return String.format("Line(x1=%.6f, y1=%.6f, x2=%.6f, y2=%.6f)", x1, y1, x2, y2);
        }
    }

    public static class PathElement {
        public final String data;

        public PathElement(String data) {
            this.data = data;
        }

        public boolean containsCommand(String command) {
            // Check for command letter (case insensitive)
            return data.toUpperCase().contains(command.toUpperCase());
        }

        public boolean hasClosePath() {
            return containsCommand("Z");
        }

        public boolean hasArc() {
            return containsCommand("A");
        }

        public boolean hasLine() {
            return containsCommand("L");
        }

        @Override
        public String toString() {
            String preview = data.length() > 50 ? data.substring(0, 50) + "..." : data;
            return String.format("Path(%s)", preview);
        }
    }

    public static class LayerGroup {
        public final String id;

        public LayerGroup(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return String.format("Layer(id=%s)", id);
        }
    }

    public static class RectElement {
        public final double x;
        public final double y;
        public final double width;
        public final double height;

        public RectElement(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        @Override
        public String toString() {
            return String.format("Rect(x=%.6f, y=%.6f, w=%.6f, h=%.6f)", x, y, width, height);
        }
    }
}

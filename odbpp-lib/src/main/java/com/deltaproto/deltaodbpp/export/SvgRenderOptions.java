package com.deltaproto.deltaodbpp.export;

import lombok.Data;

/**
 * Configuration options for SVG rendering of ODB++ layers.
 */
@Data
public class SvgRenderOptions {

    /**
     * Output unit for SVG coordinates.
     */
    public enum OutputUnit {
        /** Output coordinates in millimeters */
        MM,
        /** Output coordinates in inches (ODB++ native unit) */
        INCH
    }

    /**
     * Output unit for SVG coordinates.
     * Default: INCH (matches reference SVG format)
     */
    private OutputUnit outputUnit = OutputUnit.INCH;

    /**
     * Conversion factor from inches to mm.
     */
    public static final double INCH_TO_MM = 25.4;

    /**
     * Scale factor for converting ODB++ units to SVG pixels.
     * Default: 1.0 (coordinates are in output units)
     */
    private double scale = 1.0;

    /**
     * DPI (dots per inch) for converting output units to pixels.
     * Used for SVG width/height attributes.
     * Default: 96 (standard web DPI)
     */
    private double dpi = 96.0;

    /**
     * Padding around the rendered content in ODB++ units.
     * Default: 0.1 inches
     */
    private double padding = 0.1;

    /**
     * Default line width for lines without symbol reference.
     * In ODB++ units.
     */
    private double defaultLineWidth = 0.2;

    /**
     * Default pad size for pads without symbol reference.
     * In ODB++ units (diameter).
     */
    private double defaultPadSize = 0.05;

    /**
     * Default font size for text elements.
     * In ODB++ units.
     */
    private double defaultFontSize = 0.05;

    /**
     * Override color for all features.
     * If null, uses default layer type colors.
     */
    private String color = null;

    /**
     * Background color for the SVG.
     * If null, no background is drawn.
     */
    private String backgroundColor = null;

    /**
     * Whether to flip the Y axis for bottom layer view.
     * Default: false
     */
    private boolean flipY = false;

    /**
     * Whether to mirror the X axis.
     * Default: false
     */
    private boolean mirrorX = false;

    /**
     * Whether to render component centroids.
     * Default: false
     */
    private boolean renderComponents = false;

    /**
     * Size of component centroid markers in ODB++ units.
     * Default: 1.0 inches
     */
    private double componentMarkerSize = 1.0;

    /**
     * Font size for component labels in ODB++ units.
     * Default: 0.5 inches
     */
    private double componentLabelSize = 0.5;

    /**
     * Color for component centroids and labels.
     * Default: red
     */
    private String componentColor = "#FF0000";

    /**
     * Offset for component labels from centroid in ODB++ units.
     * Default: 0.2 inches
     */
    private double componentLabelOffset = 0.2;

    /**
     * Creates default render options.
     */
    public SvgRenderOptions() {
    }

    /**
     * Creates render options with custom color.
     *
     * @param color The color to use for all features
     * @return The options instance for chaining
     */
    public SvgRenderOptions withColor(String color) {
        this.color = color;
        return this;
    }

    /**
     * Creates render options with custom scale.
     *
     * @param scale The scale factor
     * @return The options instance for chaining
     */
    public SvgRenderOptions withScale(double scale) {
        this.scale = scale;
        return this;
    }

    /**
     * Creates render options with background color.
     *
     * @param backgroundColor The background color
     * @return The options instance for chaining
     */
    public SvgRenderOptions withBackground(String backgroundColor) {
        this.backgroundColor = backgroundColor;
        return this;
    }

    /**
     * Creates render options for bottom layer view (flipped).
     *
     * @return The options instance for chaining
     */
    public SvgRenderOptions forBottomView() {
        this.flipY = true;
        this.mirrorX = true;
        return this;
    }

    /**
     * Enables component rendering with centroids and labels.
     *
     * @return The options instance for chaining
     */
    public SvgRenderOptions withComponents() {
        this.renderComponents = true;
        return this;
    }

    /**
     * Sets the component marker size.
     *
     * @param size The marker size in ODB++ units
     * @return The options instance for chaining
     */
    public SvgRenderOptions withComponentMarkerSize(double size) {
        this.componentMarkerSize = size;
        return this;
    }

    /**
     * Sets the component label size.
     *
     * @param size The label font size in ODB++ units
     * @return The options instance for chaining
     */
    public SvgRenderOptions withComponentLabelSize(double size) {
        this.componentLabelSize = size;
        return this;
    }

    /**
     * Sets the component color for markers and labels.
     *
     * @param color The color in hex format (e.g., "#FF0000")
     * @return The options instance for chaining
     */
    public SvgRenderOptions withComponentColor(String color) {
        this.componentColor = color;
        return this;
    }

    /**
     * Sets mirrorX for X-axis mirroring (used for bottom view).
     *
     * @param mirror Whether to mirror the X axis
     * @return The options instance for chaining
     */
    public SvgRenderOptions withMirrorX(boolean mirror) {
        this.mirrorX = mirror;
        return this;
    }

    /**
     * Sets the output unit for SVG coordinates.
     *
     * @param unit The output unit (MM or INCH)
     * @return The options instance for chaining
     */
    public SvgRenderOptions withOutputUnit(OutputUnit unit) {
        this.outputUnit = unit;
        return this;
    }

    /**
     * Converts a value from internal units (mm) to the configured output unit.
     * Internal coordinates are stored in mm.
     *
     * @param valueInMm The value in mm (internal coordinate system)
     * @return The value in the configured output unit
     */
    public double toOutputUnit(double valueInMm) {
        return outputUnit == OutputUnit.MM ? valueInMm : valueInMm / INCH_TO_MM;
    }

    /**
     * Gets the unit suffix for formatting (e.g., for comments or debugging).
     *
     * @return "mm" or "in" depending on output unit
     */
    public String getUnitSuffix() {
        return outputUnit == OutputUnit.MM ? "mm" : "in";
    }
}

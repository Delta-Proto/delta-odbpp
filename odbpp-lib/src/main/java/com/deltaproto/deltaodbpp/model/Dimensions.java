package com.deltaproto.deltaodbpp.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the dimensions file from a layer.
 * Contains measurement annotations added to indicate measurements of objects on the layer.
 */
@Data
public class Dimensions {
    private int version;
    private String units;
    private DimensionParameters parameters;
    private List<Dimension> dimensions = new ArrayList<>();

    @Data
    public static class DimensionParameters {
        private int id;
        private double lineWidth;
        private int postDecimalDist;
        private int postDecimalPos;
        private int postDecimalAngle;
        private String font;
        private double fontWidth;
        private double fontHeight;
        private double extOverlen;
        private double extOffset;
        private double centerMarkerLen;
        private double baselineSpacing;
        private double originX;
        private double originY;
        private double scale;
        private Paper paper;
    }

    @Data
    public static class Paper {
        private String orientation;
        private String size;
        private double width;
        private double height;
        private double x;
        private double y;
        private Margin margin;
        private ActiveArea active;
        private Colors color;
    }

    @Data
    public static class Margin {
        private double top;
        private double bottom;
        private double left;
        private double right;
    }

    @Data
    public static class ActiveArea {
        private double x00;
        private double y00;
        private double x11;
        private double y11;
    }

    @Data
    public static class Colors {
        private String feature;
        private String dimens;
        private String dimensText;
        private String profile;
        private String template;
    }

    @Data
    public static class Dimension {
        private DimensionType type;
        private int parametersId;
        private double ref1X;
        private double ref1Y;
        private double ref2X;
        private double ref2Y;
        private double ref3X;
        private double ref3Y;
        private double linePtX;
        private double linePtY;
        private double offset;
        private String arrowPos;
        private double magnify;
        private boolean toArcCenter;
        private boolean twoSidedDiam;
        private DimensionText text;
    }

    public enum DimensionType {
        HORIZONTAL, VERTICAL, PARALLEL, RADIAL, DIAMETER, ANGLE, LEADER, ORDINATE, ARC_LENGTH
    }

    @Data
    public static class DimensionText {
        private String value;
        private String prefix;
        private String suffix;
        private String note;
        private String units;
        private boolean viewUnits;
        private boolean outside;
        private boolean underline;
        private String tolUp;
        private String tolDown;
        private boolean mergeTol;
        private double x;
        private double y;
        private double angle;
    }
}

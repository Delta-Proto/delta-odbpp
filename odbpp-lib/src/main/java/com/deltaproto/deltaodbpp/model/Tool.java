package com.deltaproto.deltaodbpp.model;

import lombok.Data;

/**
 * Represents a drill tool definition in ODB++.
 * Tools are used for drill and rout layers to define the drill bit characteristics.
 */
@Data
public class Tool {
    /**
     * Tool number (1-based index)
     */
    private int num;

    /**
     * Tool type: PLATED, NON_PLATED, or VIA
     */
    private ToolType type;

    /**
     * Tool subtype depending on TYPE:
     * - PLATED: STANDARD or PRESS_FIT
     * - NON_PLATED: STANDARD
     * - VIA: STANDARD, PHOTO, or LASER
     * Default is STANDARD.
     */
    private ToolType2 type2 = ToolType2.STANDARD;

    /**
     * Minimum tolerance (in mils or microns depending on UNITS)
     */
    private double minTol;

    /**
     * Maximum tolerance (in mils or microns depending on UNITS)
     */
    private double maxTol;

    /**
     * Drill bit string identifier
     */
    private String bit;

    /**
     * Required drill size in the finished board (in mils or microns).
     * Value of -1 indicates not set.
     */
    private double finishSize = -1;

    /**
     * Drill tool size used by the drilling machine (in mils or microns).
     * Optional field.
     */
    private double drillSize = -1;

    public enum ToolType {
        PLATED,
        NON_PLATED,
        VIA
    }

    public enum ToolType2 {
        STANDARD,
        PRESS_FIT,
        PHOTO,
        LASER
    }
}

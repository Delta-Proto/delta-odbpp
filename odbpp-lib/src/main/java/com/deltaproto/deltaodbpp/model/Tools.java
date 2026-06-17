package com.deltaproto.deltaodbpp.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Container for drill tool definitions in a layer.
 * The tools file defines drill tool characteristics for drill and rout layers.
 */
@Data
public class Tools {
    /**
     * Units of measurement: INCH or MM
     */
    private String units;

    /**
     * Board thickness
     */
    private double thickness;

    /**
     * User-defined parameters string
     */
    private String userParams;

    /**
     * List of tool definitions
     */
    private List<Tool> tools = new ArrayList<>();
}

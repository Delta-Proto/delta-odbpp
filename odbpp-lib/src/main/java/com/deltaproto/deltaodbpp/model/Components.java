package com.deltaproto.deltaodbpp.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The parsed {@code components} file of a component layer. */
@Data
public class Components {
    private List<Component> components = new ArrayList<>();

    /**
     * Unit the file is written in, {@code "MM"} or {@code "INCH"}: its own {@code UNITS} line,
     * else the step's unit. Positions are converted to mm at parse time; raw attribute values
     * ({@link Component#getAttributes()}) are still in this unit.
     */
    private String units;

    /** The file's attribute name table ({@code @n .name} lines): attribute index to name. */
    private Map<Integer, String> attributeNames = new LinkedHashMap<>();

    /** The file's attribute text table ({@code &n text} lines): text index to string. */
    private Map<Integer, String> attributeTexts = new LinkedHashMap<>();
}

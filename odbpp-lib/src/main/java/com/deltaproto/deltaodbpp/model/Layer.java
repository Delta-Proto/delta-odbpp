package com.deltaproto.deltaodbpp.model;

import lombok.Data;

@Data
public class Layer {
    private String name;
    private String path;
    private Components components;
    private Features features;
    private AttrList attrList;
    private Profile profile;
    private Tools tools;
    private Dimensions dimensions;
    private Notes notes;
}

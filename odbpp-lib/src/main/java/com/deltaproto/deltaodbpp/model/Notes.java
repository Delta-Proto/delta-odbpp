package com.deltaproto.deltaodbpp.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the notes file from a layer.
 * Contains electronic notes/annotations added to the layer.
 */
@Data
public class Notes {
    private String units;
    private List<Note> notes = new ArrayList<>();

    @Data
    public static class Note {
        private int id;
        private double x;
        private double y;
        private double width;
        private double height;
        private String text;
        private String font;
        private double fontSize;
        private String alignment;
        private double angle;
        private boolean mirror;
        private Polarity polarity;
    }
}

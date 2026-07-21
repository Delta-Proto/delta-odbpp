package com.deltaproto.deltaodbpp.spec;

/**
 * Which side(s) of the board a process applies to — solder mask, silkscreen, stencil, via in pad.
 *
 * <p>Mirrors {@code com.deltaproto.deltagerber.spec.BoardSide} so the two libraries expose the same
 * shape. {@link #NONE} means "the design has this process on no side"; a {@code null} side (returned
 * by {@link BoardSpecification}) means "not determined".
 */
public enum BoardSide {
    NONE,
    TOP,
    BOTTOM,
    BOTH;

    public static BoardSide of(boolean top, boolean bottom) {
        if (top && bottom) {
            return BOTH;
        } else if (top) {
            return TOP;
        } else if (bottom) {
            return BOTTOM;
        }
        return NONE;
    }
}

package com.deltaproto.deltaodbpp.spec;

/**
 * The physical side of a single ODB++ matrix layer, as inferred by the analyzer.
 *
 * <p>ODB++ almost never records side in the matrix ({@code CONTEXT} is usually {@code BOARD} for
 * every physical layer), so side is derived from matrix row order relative to the copper stack and
 * from layer-name conventions. {@link #NA} is "not applicable / not determined".
 */
public enum LayerSide {
    TOP,
    BOTTOM,
    INNER,
    NA
}

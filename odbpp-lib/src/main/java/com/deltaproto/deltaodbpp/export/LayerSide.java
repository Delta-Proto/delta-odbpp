package com.deltaproto.deltaodbpp.export;

/**
 * Physical side of a PCB layer for realistic rendering.
 *
 * <p>ODB++ does not encode side in the matrix layer {@code CONTEXT} field
 * (which is usually {@code BOARD} for all physical layers). Side is inferred
 * from stackup position and layer name — see {@link LayerSideClassifier}.
 */
public enum LayerSide {
    /** Visible from the top-side realistic view. */
    TOP,
    /** Visible from the bottom-side realistic view. */
    BOTTOM,
    /** Inner conductor/dielectric — not visible from either side. */
    INNER,
    /** Documentation, out-of-band, or unresolvable layer — not rendered. */
    NEITHER
}

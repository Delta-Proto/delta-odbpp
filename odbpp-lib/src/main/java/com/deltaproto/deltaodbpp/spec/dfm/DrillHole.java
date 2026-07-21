package com.deltaproto.deltaodbpp.spec.dfm;

/**
 * A candidate via: the centre of a drilled hole, in millimetres, with its diameter (0 when the
 * tool size is not known). Fed to {@link ViaInPadDetector#detect}.
 */
public record DrillHole(double xMm, double yMm, double diameterMm) {
}

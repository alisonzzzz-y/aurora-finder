package com.aurora.observation.dto;

/** NOAA geomagnetic storm category. NOAA rounds Kp to a whole K index first, so 4.67 (5-) is already G1. */
public enum NoaaGeomagneticStormScale {
    BELOW_G1, G1, G2, G3, G4, G5;

    public static NoaaGeomagneticStormScale fromKp(double kp) {
        if (!Double.isFinite(kp) || kp < 0 || kp > 9) {
            throw new IllegalArgumentException("Kp must be between 0 and 9");
        }
        long k = Math.round(kp);
        if (k < 5) return BELOW_G1;
        if (k == 5) return G1;
        if (k == 6) return G2;
        if (k == 7) return G3;
        if (k == 8) return G4;
        return G5;
    }
}

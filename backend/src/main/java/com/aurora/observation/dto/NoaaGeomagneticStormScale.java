package com.aurora.observation.dto;

/** NOAA geomagnetic storm category derived from the planetary Kp threshold. */
public enum NoaaGeomagneticStormScale {
    BELOW_G1, G1, G2, G3, G4, G5;

    public static NoaaGeomagneticStormScale fromKp(double kp) {
        if (!Double.isFinite(kp) || kp < 0 || kp > 9) {
            throw new IllegalArgumentException("Kp must be between 0 and 9");
        }
        if (kp < 5) return BELOW_G1;
        if (kp < 6) return G1;
        if (kp < 7) return G2;
        if (kp < 8) return G3;
        if (kp < 9) return G4;
        return G5;
    }
}

package com.aurora.observation.dto;

import java.time.LocalDate;

/** NOAA's predicted maximum geomagnetic storm category for a UTC forecast day. */
public record GeomagneticStormWatchDay(LocalDate date, String noaaScale) {}

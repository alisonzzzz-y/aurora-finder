package com.aurora.observation.dto;

import java.time.LocalDate;

public record GeomagneticStormDay(LocalDate date, int activeChancePercent, int minorStormChancePercent,
                                  int moderateStormChancePercent, int strongExtremeStormChancePercent) {}

package com.aurora.observation.dto;

import java.time.LocalDate;

public record SolarNightFact(LocalDate localDate, SolarDarkness solarDarkness) {}

package com.aurora.observation.dto;

public record Location(long id, String name, String region, String subregion, String country,
                       double latitude, double longitude, String timezone) {}

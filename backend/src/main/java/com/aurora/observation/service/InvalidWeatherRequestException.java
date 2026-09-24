package com.aurora.observation.service;

public class InvalidWeatherRequestException extends RuntimeException {
    public InvalidWeatherRequestException(String message) {
        super(message);
    }
}

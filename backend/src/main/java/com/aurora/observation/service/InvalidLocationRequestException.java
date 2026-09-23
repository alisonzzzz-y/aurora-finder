package com.aurora.observation.service;

public class InvalidLocationRequestException extends RuntimeException {
    public InvalidLocationRequestException(String message) {
        super(message);
    }
}

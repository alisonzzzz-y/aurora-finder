package com.aurora.observation.service;

public class LocationNotFoundException extends RuntimeException {
    public LocationNotFoundException(long id) { super("Location " + id + " was not found"); }
}

package com.aurora.observation.service;

import java.time.LocalDate;
import java.util.List;

/** Signals that an explicitly requested local night is outside the current outlook window. */
public class UnsupportedNightDateException extends RuntimeException {
    private final LocalDate requestedDate;
    private final List<LocalDate> availableDates;

    public UnsupportedNightDateException(LocalDate requestedDate, List<LocalDate> availableDates) {
        super("Requested local date is outside the available outlook window.");
        this.requestedDate = requestedDate;
        this.availableDates = List.copyOf(availableDates);
    }

    public LocalDate requestedDate() {
        return requestedDate;
    }

    public List<LocalDate> availableDates() {
        return availableDates;
    }
}

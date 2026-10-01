package com.aurora.observation.record;

public class RunRecordUnavailableException extends RuntimeException {
    public RunRecordUnavailableException(Throwable cause) {
        super("The run record database is unavailable.", cause);
    }
}

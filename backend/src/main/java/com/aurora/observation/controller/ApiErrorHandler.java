package com.aurora.observation.controller;

import com.aurora.observation.provider.ProviderUnavailableException;
import com.aurora.observation.provider.ProviderFailure;
import com.aurora.observation.service.InvalidLocationRequestException;
import com.aurora.observation.service.LocationNotFoundException;
import com.aurora.observation.service.InvalidWeatherRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(LocationNotFoundException.class)
    public ProblemDetail notFound(LocationNotFoundException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler(ProviderUnavailableException.class)
    public ProblemDetail providerUnavailable(ProviderUnavailableException error) {
        if (error.failure() == ProviderFailure.INVALID_RESPONSE) {
            log.error("External data provider returned invalid data: {}", error.getMessage(), error);
        } else {
            log.warn("External data provider failure [{}]: {}", error.failure(), error.getMessage());
        }
        HttpStatus status = switch (error.failure()) {
            case INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        String detail = switch (error.failure()) {
            case RATE_LIMITED -> "The data source is temporarily rate limited. Please try again later.";
            case FORBIDDEN -> "The weather source rejected this request. Check its request identification settings.";
            case TIMEOUT -> "The data source timed out. Please try again later.";
            case INVALID_RESPONSE -> "The data source returned invalid data. Please try again later.";
            default -> "The data source is unavailable. Please try again later.";
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", error.failure() == ProviderFailure.FORBIDDEN
                ? "WEATHER_FORBIDDEN" : "GEOCODING_" + error.failure().name());
        return problem;
    }

    @ExceptionHandler(InvalidLocationRequestException.class)
    public ProblemDetail invalidQuery(InvalidLocationRequestException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(InvalidWeatherRequestException.class)
    public ProblemDetail invalidWeatherRequest(InvalidWeatherRequestException error) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
        problem.setProperty("code", "WEATHER_INVALID_COORDINATES");
        return problem;
    }
}

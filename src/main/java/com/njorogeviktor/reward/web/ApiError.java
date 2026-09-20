package com.njorogeviktor.reward.web;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape every endpoint returns.
 *
 * @param error     stable machine-readable code
 * @param message   human-readable summary
 * @param details   specific problems, where there are several
 * @param timestamp when the error was produced
 */
public record ApiError(String error, String message, List<String> details, Instant timestamp) {

    public static ApiError of(String error, String message, List<String> details) {
        return new ApiError(error, message, details, Instant.now());
    }

    public static ApiError of(String error, String message) {
        return of(error, message, List.of());
    }
}

package com.njorogeviktor.reward.web;

import com.njorogeviktor.reward.scoring.ScoringException;
import com.njorogeviktor.reward.web.ScoringController.BatchTooLargeException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into the {@link ApiError} shape.
 *
 * <p>Centralised so every endpoint returns identically structured errors and a
 * client needs one code path, and so a raw stack trace never leaks to a caller.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** A rubric/score mismatch: the request was understood but cannot be satisfied. */
    @ExceptionHandler(ScoringException.class)
    public ResponseEntity<ApiError> handleScoring(ScoringException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(ApiError.of("scoring_failed", "the item could not be scored", ex.problems()));
    }

    /** Bean-validation failures on the request body. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> "%s %s".formatted(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.of("invalid_request", "request validation failed", details));
    }

    /** A malformed JSON body, or one violating a record's compact constructor. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        String message = rootCauseMessage(ex);
        return ResponseEntity.badRequest().body(ApiError.of("malformed_request", message));
    }

    @ExceptionHandler(BatchTooLargeException.class)
    public ResponseEntity<ApiError> handleBatchTooLarge(BatchTooLargeException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of("batch_too_large", ex.getMessage()));
    }

    /**
     * Surfaces the innermost cause message. Record compact constructors throw
     * {@link IllegalArgumentException}, which Jackson wraps; without unwrapping,
     * the caller sees a generic deserialisation error instead of the actual
     * "max must exceed min".
     */
    private static String rootCauseMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null ? "request body could not be read" : message;
    }
}

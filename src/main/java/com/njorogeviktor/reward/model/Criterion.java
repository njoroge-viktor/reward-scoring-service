package com.njorogeviktor.reward.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * One weighted scoring dimension of a rubric.
 *
 * @param name   stable identifier, referenced by submitted scores
 * @param weight relative contribution to the weighted total; must be positive
 * @param min    inclusive lower bound of the raw score scale
 * @param max    inclusive upper bound of the raw score scale
 */
public record Criterion(
        @NotBlank String name,
        @Positive double weight,
        @Min(0) int min,
        int max) {

    /**
     * Compact constructor validating the scale is well-formed.
     *
     * <p>Validation lives here rather than only in bean-validation annotations
     * so the invariant holds however a {@code Criterion} is constructed —
     * including directly in tests and internal code that never crosses the
     * web layer.
     */
    public Criterion {
        if (max <= min) {
            throw new IllegalArgumentException(
                    "criterion '%s': max (%d) must exceed min (%d)".formatted(name, max, min));
        }
        if (weight <= 0) {
            throw new IllegalArgumentException(
                    "criterion '%s': weight must be positive, got %s".formatted(name, weight));
        }
    }

    /** Normalises a raw score onto {@code [0, 1]} within this criterion's scale. */
    public double normalize(double rawScore) {
        return (rawScore - min) / (double) (max - min);
    }

    /** Whether {@code rawScore} falls within {@code [min, max]}. */
    public boolean accepts(double rawScore) {
        return rawScore >= min && rawScore <= max;
    }
}

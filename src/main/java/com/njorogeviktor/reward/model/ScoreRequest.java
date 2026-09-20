package com.njorogeviktor.reward.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * A request to score one item against a rubric.
 *
 * @param itemId  identifier echoed back in the response
 * @param rubric  the rubric to apply
 * @param scores  raw per-criterion scores, keyed by criterion name
 * @param lenient when true, missing criteria are skipped and weighting is
 *                renormalised over those supplied, rather than rejected
 */
public record ScoreRequest(
        @NotBlank String itemId,
        @NotNull @Valid Rubric rubric,
        @NotEmpty Map<String, Double> scores,
        boolean lenient) {
}

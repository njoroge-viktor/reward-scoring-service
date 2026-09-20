package com.njorogeviktor.reward.model;

import java.util.Map;

/**
 * The result of scoring one item.
 *
 * @param itemId        echoed from the request
 * @param weightedScore sum of normalised-score times weight
 * @param normalized    weighted score divided by applied weight, in {@code [0, 1]}
 * @param perCriterion  normalised score for each criterion applied
 * @param appliedWeight total weight of the criteria actually scored
 */
public record ScoreResponse(
        String itemId,
        double weightedScore,
        double normalized,
        Map<String, Double> perCriterion,
        double appliedWeight) {
}

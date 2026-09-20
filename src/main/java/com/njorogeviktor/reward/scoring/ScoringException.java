package com.njorogeviktor.reward.scoring;

import java.util.List;

/**
 * Thrown when a score request cannot be satisfied.
 *
 * <p>Carries every problem rather than only the first, so a malformed batch
 * can be corrected in one pass.
 */
public class ScoringException extends RuntimeException {

    private final transient List<String> problems;

    public ScoringException(List<String> problems) {
        super("scoring failed: " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}

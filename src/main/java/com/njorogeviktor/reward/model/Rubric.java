package com.njorogeviktor.reward.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A weighted set of scoring criteria.
 *
 * @param name     rubric identifier
 * @param criteria the dimensions to score, at least one
 */
public record Rubric(
        String name,
        @NotEmpty @Valid List<Criterion> criteria) {

    /** Canonicalises the criteria list to be immutable and rejects duplicates. */
    public Rubric {
        if (criteria != null) {
            var names = criteria.stream().map(Criterion::name).collect(Collectors.toSet());
            if (names.size() != criteria.size()) {
                throw new IllegalArgumentException(
                        "rubric '%s' has duplicate criterion names".formatted(name));
            }
            criteria = List.copyOf(criteria);
        }
    }

    /** Sum of all criterion weights. */
    public double totalWeight() {
        return criteria.stream().mapToDouble(Criterion::weight).sum();
    }

    /** Criteria indexed by name, for lookup during scoring. */
    public Map<String, Criterion> byName() {
        return criteria.stream().collect(Collectors.toMap(Criterion::name, Function.identity()));
    }
}

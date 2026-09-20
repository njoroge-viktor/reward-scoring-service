package com.njorogeviktor.reward.scoring;

import com.njorogeviktor.reward.model.Criterion;
import com.njorogeviktor.reward.model.Rubric;
import com.njorogeviktor.reward.model.ScoreRequest;
import com.njorogeviktor.reward.model.ScoreResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Applies a rubric to raw scores, normalising onto {@code [0, 1]}.
 *
 * <p>Stateless and therefore trivially thread-safe: a single instance serves
 * every concurrent request, which is why it is a Spring singleton rather than
 * something pooled.
 */
@Service
public class ScoringService {

    /**
     * Scores one item against its rubric.
     *
     * @throws ScoringException if unknown criteria are supplied, a score falls
     *     outside its scale, or (in strict mode) a criterion is missing. Every
     *     such problem is collected and reported together.
     */
    public ScoreResponse score(ScoreRequest request) {
        Rubric rubric = request.rubric();
        Map<String, Criterion> known = rubric.byName();
        Map<String, Double> submitted = request.scores();
        List<String> problems = new ArrayList<>();

        // Unknown criteria are always an error: silently ignoring a typo'd
        // criterion name would drop a score the caller believed was counted.
        for (String name : submitted.keySet()) {
            if (!known.containsKey(name)) {
                problems.add("unknown criterion '%s'".formatted(name));
            }
        }

        if (!request.lenient()) {
            for (String name : known.keySet()) {
                if (!submitted.containsKey(name)) {
                    problems.add("missing criterion '%s'".formatted(name));
                }
            }
        }

        double weightedScore = 0.0;
        double appliedWeight = 0.0;
        Map<String, Double> perCriterion = new LinkedHashMap<>();

        for (Map.Entry<String, Double> entry : submitted.entrySet()) {
            Criterion criterion = known.get(entry.getKey());
            if (criterion == null) {
                continue; // already reported as unknown
            }

            double raw = entry.getValue();
            if (!criterion.accepts(raw)) {
                problems.add("criterion '%s': score %s outside [%d, %d]"
                        .formatted(criterion.name(), raw, criterion.min(), criterion.max()));
                continue;
            }

            double unit = criterion.normalize(raw);
            weightedScore += unit * criterion.weight();
            appliedWeight += criterion.weight();
            perCriterion.put(criterion.name(), unit);
        }

        if (!problems.isEmpty()) {
            throw new ScoringException(problems);
        }

        if (appliedWeight == 0.0) {
            // Only reachable in lenient mode with an empty-but-non-null score
            // map that matched no criteria; strict mode fails earlier.
            throw new ScoringException(List.of("no known criteria were scored"));
        }

        return new ScoreResponse(
                request.itemId(),
                weightedScore,
                weightedScore / appliedWeight,
                Map.copyOf(perCriterion),
                appliedWeight);
    }
}

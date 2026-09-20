package com.njorogeviktor.reward.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.njorogeviktor.reward.model.Criterion;
import com.njorogeviktor.reward.model.Rubric;
import com.njorogeviktor.reward.model.ScoreRequest;
import com.njorogeviktor.reward.model.ScoreResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ScoringServiceTest {

    private final ScoringService service = new ScoringService();

    private static Rubric codeQualityRubric() {
        return new Rubric(
                "code-quality",
                List.of(
                        new Criterion("correctness", 3.0, 1, 5),
                        new Criterion("clarity", 1.0, 1, 5)));
    }

    private static ScoreRequest request(Map<String, Double> scores, boolean lenient) {
        return new ScoreRequest("item-1", codeQualityRubric(), scores, lenient);
    }

    @Nested
    @DisplayName("normalisation")
    class Normalisation {

        @Test
        void topMarksNormaliseToOne() {
            ScoreResponse response =
                    service.score(request(Map.of("correctness", 5.0, "clarity", 5.0), false));
            assertThat(response.normalized()).isCloseTo(1.0, within(1e-9));
        }

        @Test
        void bottomMarksNormaliseToZero() {
            ScoreResponse response =
                    service.score(request(Map.of("correctness", 1.0, "clarity", 1.0), false));
            assertThat(response.normalized()).isCloseTo(0.0, within(1e-9));
        }

        @Test
        void weightingIsHandComputed() {
            // correctness 4 on [1,5] -> unit 0.75, weight 3 -> 2.25
            // clarity     2 on [1,5] -> unit 0.25, weight 1 -> 0.25
            // weighted = 2.50; normalised = 2.50 / 4 = 0.625
            ScoreResponse response =
                    service.score(request(Map.of("correctness", 4.0, "clarity", 2.0), false));
            assertThat(response.weightedScore()).isCloseTo(2.5, within(1e-9));
            assertThat(response.normalized()).isCloseTo(0.625, within(1e-9));
        }

        @Test
        void theHeavierCriterionMovesTheScoreMore() {
            ScoreResponse strongWhereItCounts =
                    service.score(request(Map.of("correctness", 5.0, "clarity", 1.0), false));
            ScoreResponse strongWhereItDoesNot =
                    service.score(request(Map.of("correctness", 1.0, "clarity", 5.0), false));
            assertThat(strongWhereItCounts.normalized())
                    .isGreaterThan(strongWhereItDoesNot.normalized());
        }

        @Test
        void reportsPerCriterionUnits() {
            ScoreResponse response =
                    service.score(request(Map.of("correctness", 4.0, "clarity", 2.0), false));
            assertThat(response.perCriterion())
                    .containsEntry("correctness", 0.75)
                    .containsEntry("clarity", 0.25);
        }
    }

    @Nested
    @DisplayName("strict vs lenient")
    class Modes {

        @Test
        void strictModeRejectsAMissingCriterion() {
            assertThatThrownBy(() -> service.score(request(Map.of("correctness", 4.0), false)))
                    .isInstanceOf(ScoringException.class)
                    .satisfies(ex -> assertThat(((ScoringException) ex).problems())
                            .anyMatch(p -> p.contains("missing criterion 'clarity'")));
        }

        @Test
        void lenientModeRenormalisesOverSuppliedCriteria() {
            ScoreResponse response = service.score(request(Map.of("correctness", 4.0), true));
            // Only correctness applied: 0.75 * 3 = 2.25, renormalised over weight 3.
            assertThat(response.weightedScore()).isCloseTo(2.25, within(1e-9));
            assertThat(response.normalized()).isCloseTo(0.75, within(1e-9));
            assertThat(response.appliedWeight()).isCloseTo(3.0, within(1e-9));
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        void rejectsUnknownCriteria() {
            assertThatThrownBy(() ->
                            service.score(request(
                                    Map.of("correctness", 4.0, "clarity", 3.0, "vibes", 5.0), false)))
                    .isInstanceOf(ScoringException.class)
                    .hasMessageContaining("unknown criterion 'vibes'");
        }

        @Test
        void rejectsAScoreAboveTheScale() {
            assertThatThrownBy(() ->
                            service.score(request(Map.of("correctness", 9.0, "clarity", 3.0), false)))
                    .isInstanceOf(ScoringException.class)
                    .hasMessageContaining("outside [1, 5]");
        }

        @Test
        void rejectsAScoreBelowTheScale() {
            assertThatThrownBy(() ->
                            service.score(request(Map.of("correctness", 0.0, "clarity", 3.0), false)))
                    .isInstanceOf(ScoringException.class)
                    .hasMessageContaining("outside [1, 5]");
        }

        @Test
        void collectsEveryProblemAtOnce() {
            // Unknown criterion AND an out-of-range score AND a missing one.
            assertThatThrownBy(() ->
                            service.score(request(Map.of("correctness", 9.0, "vibes", 1.0), false)))
                    .isInstanceOf(ScoringException.class)
                    .satisfies(ex -> assertThat(((ScoringException) ex).problems()).hasSizeGreaterThanOrEqualTo(3));
        }

        @Test
        void acceptsScoresAtTheScaleBoundaries() {
            ScoreResponse low = service.score(request(Map.of("correctness", 1.0, "clarity", 1.0), false));
            ScoreResponse high = service.score(request(Map.of("correctness", 5.0, "clarity", 5.0), false));
            assertThat(low.normalized()).isCloseTo(0.0, within(1e-9));
            assertThat(high.normalized()).isCloseTo(1.0, within(1e-9));
        }
    }

    @Nested
    @DisplayName("criteria with different scales combine fairly")
    class MixedScales {

        @Test
        void aWideScaleDoesNotDominateANarrowOne() {
            Rubric mixed = new Rubric(
                    "mixed",
                    List.of(new Criterion("wide", 1.0, 1, 10), new Criterion("narrow", 1.0, 1, 5)));
            ScoreResponse response = service.score(
                    new ScoreRequest("i", mixed, Map.of("wide", 10.0, "narrow", 5.0), false));
            assertThat(response.normalized()).isCloseTo(1.0, within(1e-9));
        }
    }
}

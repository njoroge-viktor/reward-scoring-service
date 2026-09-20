package com.njorogeviktor.reward.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class RubricTest {

    @Test
    void criterionRejectsAnInvertedScale() {
        assertThatThrownBy(() -> new Criterion("x", 1.0, 5, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max");
    }

    @Test
    void criterionRejectsNonPositiveWeight() {
        assertThatThrownBy(() -> new Criterion("x", 0.0, 1, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weight");
    }

    @Test
    void criterionNormalisesWithinItsScale() {
        Criterion c = new Criterion("x", 1.0, 1, 5);
        assertThat(c.normalize(1)).isCloseTo(0.0, within(1e-9));
        assertThat(c.normalize(3)).isCloseTo(0.5, within(1e-9));
        assertThat(c.normalize(5)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void criterionAcceptsInclusiveBounds() {
        Criterion c = new Criterion("x", 1.0, 1, 5);
        assertThat(c.accepts(1)).isTrue();
        assertThat(c.accepts(5)).isTrue();
        assertThat(c.accepts(0)).isFalse();
        assertThat(c.accepts(6)).isFalse();
    }

    @Test
    void rubricRejectsDuplicateCriterionNames() {
        assertThatThrownBy(() -> new Rubric(
                        "dupes",
                        List.of(new Criterion("a", 1.0, 1, 5), new Criterion("a", 2.0, 1, 5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void rubricSumsWeights() {
        Rubric r = new Rubric(
                "r", List.of(new Criterion("a", 3.0, 1, 5), new Criterion("b", 1.0, 1, 5)));
        assertThat(r.totalWeight()).isCloseTo(4.0, within(1e-9));
    }

    @Test
    void rubricCriteriaAreImmutable() {
        var mutable = new java.util.ArrayList<>(List.of(new Criterion("a", 1.0, 1, 5)));
        Rubric r = new Rubric("r", mutable);
        assertThatThrownBy(() -> r.criteria().add(new Criterion("b", 1.0, 1, 5)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rubricIndexesByName() {
        Rubric r = new Rubric(
                "r", List.of(new Criterion("a", 1.0, 1, 5), new Criterion("b", 1.0, 1, 5)));
        assertThat(r.byName()).containsOnlyKeys("a", "b");
    }
}

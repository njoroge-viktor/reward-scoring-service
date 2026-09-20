package com.njorogeviktor.reward;

import static org.assertj.core.api.Assertions.assertThat;

import com.njorogeviktor.reward.scoring.ScoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies the application context wires together. If a bean is missing or a
 * dependency cannot be satisfied, this fails at startup rather than in
 * production.
 */
@SpringBootTest
class ApplicationSmokeTest {

    @Autowired
    private ScoringService scoringService;

    @Test
    void contextLoads() {
        assertThat(scoringService).isNotNull();
    }
}

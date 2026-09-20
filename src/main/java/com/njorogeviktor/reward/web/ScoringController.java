package com.njorogeviktor.reward.web;

import com.njorogeviktor.reward.model.ScoreRequest;
import com.njorogeviktor.reward.model.ScoreResponse;
import com.njorogeviktor.reward.scoring.ScoringService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP interface to the scoring service. */
@RestController
@RequestMapping("/v1/scores")
public class ScoringController {

    /**
     * A batch is capped so one request cannot pin a worker thread or exhaust
     * heap. Callers with more items page through in chunks.
     */
    private static final int MAX_BATCH = 1_000;

    private final ScoringService scoringService;

    public ScoringController(ScoringService scoringService) {
        this.scoringService = scoringService;
    }

    /** Scores a single item. */
    @PostMapping
    public ScoreResponse score(@Valid @RequestBody ScoreRequest request) {
        return scoringService.score(request);
    }

    /** Scores a batch, preserving input order. */
    @PostMapping("/batch")
    public ResponseEntity<List<ScoreResponse>> scoreBatch(
            @Valid @RequestBody List<@Valid ScoreRequest> requests) {
        if (requests.size() > MAX_BATCH) {
            throw new BatchTooLargeException(requests.size(), MAX_BATCH);
        }
        List<ScoreResponse> responses = requests.stream().map(scoringService::score).toList();
        return ResponseEntity.ok(responses);
    }

    /** Raised when a batch exceeds {@link #MAX_BATCH}. The message carries the detail. */
    public static class BatchTooLargeException extends RuntimeException {
        BatchTooLargeException(int size, int max) {
            super("batch of %d exceeds the maximum of %d".formatted(size, max));
        }
    }
}

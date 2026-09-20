# reward-scoring-service

[![CI](https://github.com/njoroge-viktor/reward-scoring-service/actions/workflows/ci.yml/badge.svg)](https://github.com/njoroge-viktor/reward-scoring-service/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/java-21-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/spring--boot-3.3-green)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/license-MIT-green)](LICENSE)

A banking-grade REST service for applying weighted rubrics to reward-model
scores. Spring Boot 3, Java 21, records throughout, one error shape.

```bash
./gradlew bootRun
curl -X POST localhost:8080/v1/scores -H 'content-type: application/json' -d '{
  "itemId": "task-1",
  "rubric": {"name": "code-quality", "criteria": [
    {"name": "correctness", "weight": 3.0, "min": 1, "max": 5},
    {"name": "clarity",     "weight": 1.0, "min": 1, "max": 5}]},
  "scores": {"correctness": 4.0, "clarity": 2.0}
}'
# {"itemId":"task-1","weightedScore":2.5,"normalized":0.625,
#  "perCriterion":{"correctness":0.75,"clarity":0.25},"appliedWeight":4.0}
```

## What it does

Raw rubric scores are not comparable: a 4/5 on a heavily weighted correctness
criterion means more than a 4/5 on clarity, and two rubrics with different
scales cannot be averaged. This service normalises each score onto `[0, 1]`
within its own scale, applies the criterion weights, and returns a single
comparable number.

The arithmetic is pinned in the tests, not left to trust: correctness 4 on
`[1,5]` is unit 0.75, times weight 3 is 2.25; clarity 2 is unit 0.25, times
weight 1 is 0.25; weighted 2.5 over total weight 4 is **0.625**.

## Strict and lenient modes

By default every rubric criterion must be scored — a missing one is a `422`.
Set `"lenient": true` to score a partial review, renormalising over just the
criteria supplied:

```json
{"itemId": "t", "rubric": {"...": "..."}, "scores": {"correctness": 4.0}, "lenient": true}
// normalized 0.75 - correctness alone, its weight the whole denominator
```

An **unknown** criterion is always an error, in both modes. Silently ignoring a
typo'd criterion name would drop a score the caller believed was counted.

## Errors have one shape

Every endpoint returns the same envelope, so a client needs one code path:

```json
{"error": "scoring_failed", "message": "the item could not be scored",
 "details": ["criterion 'correctness': score 9.0 outside [1, 5]"],
 "timestamp": "2026-09-20T06:41:43Z"}
```

| Status | `error` | Cause |
| --- | --- | --- |
| `400` | `invalid_request` | Bean-validation failure (e.g. missing `itemId`) |
| `400` | `malformed_request` | Bad JSON, or a rubric violating its own invariants |
| `422` | `scoring_failed` | Understood but unsatisfiable: out-of-range or missing scores |
| `413` | `batch_too_large` | Batch over 1,000 items |

Every scoring problem in a request is collected and returned together, so a
malformed batch is fixed in one pass rather than one error per round trip.

### Validation lives in the domain, not just the edge

`Criterion` and `Rubric` are records whose compact constructors enforce their
invariants — `max > min`, positive weight, no duplicate criterion names — so
the invariant holds however the object is built, including in internal code
that never crosses the web layer. When such a constructor rejects a request
body, the handler unwraps Jackson's wrapper so the caller sees the real reason
("max must exceed min"), not a generic deserialisation error.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/v1/scores` | Score one item |
| `POST` | `/v1/scores/batch` | Score up to 1,000 items, order preserved |
| `GET` | `/actuator/health` | Liveness and readiness probes |
| `GET` | `/actuator/metrics` | Micrometer metrics |

The batch cap is deliberate: without it one request could pin a worker thread
and exhaust heap. Callers with more items page through in chunks.

## Design notes

- **`ScoringService` is stateless**, so a single Spring singleton serves every
  concurrent request with no locking. Thread safety falls out of having no
  mutable state rather than being bolted on.
- **Records everywhere.** The domain is immutable data; `Rubric` even copies
  its criteria list to `List.copyOf` so a caller cannot mutate it after
  construction.
- **The web slice test imports the real service** rather than mocking it — it
  is stateless and cheap, so exercising the true scoring path through HTTP is
  worth more than asserting against a mock.

## Building and testing

```bash
./gradlew check      # test + jacoco line-coverage gate (85% bundle, 80% per class)
./gradlew bootRun
./gradlew bootJar    # runnable jar in build/libs
```

28 tests across the domain, service and web layers; 95% line coverage. The
coverage gate runs as part of `check`, so a drop fails the build rather than a
review.

The Gradle wrapper is committed, so a clean checkout builds with `./gradlew`
and no local Gradle install.

## License

MIT — see [LICENSE](LICENSE).

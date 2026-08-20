# Project Context

Hands-on learning project — Java 21 + Spring Boot distributed order-processing platform, built in phases (Postgres, Redis, Kafka, Elasticsearch, Kubernetes).

## How to work in this repo

- Generate code in small, reviewable increments — one class, one config file, or one closely related pair (e.g. entity + its migration) at a time. Do not implement a whole feature or multiple files in one pass unless explicitly asked.
- Every non-trivial piece of code must include comments explaining *why*, not just what — especially anything Spring/JPA/Kafka/Redis-specific that isn't obvious from the code alone (e.g. why `ddl-auto: validate` instead of `update`, why a particular Kafka consumer config matters).
- After each increment, briefly summarize what was added and why, then stop and wait for confirmation before continuing to the next piece.
- Do not refactor unrelated code or add scope beyond what was asked.
- When there's a real design trade-off (cache-aside vs write-through, at-least-once vs exactly-once, etc.), explain the options rather than silently picking one.

## Pacing (added after Phase 2 caching work)

- For well-understood, low-risk work — things following a pattern already established and reviewed earlier in this project, straightforward config, CRUD-style code — batch multiple closely-related files into one increment instead of one class at a time. Passing `./gradlew build`/`test` is sufficient proof; exhaustive manual Postman/redis-cli verification of every case is no longer required by default.
- Reserve the original slow pace (one piece at a time, full explanation, hands-on verification of each step) specifically for genuinely new distributed-systems concepts — Kafka consumer-group/rebalance/offset mechanics in Phase 3, and Kubernetes scheduling/networking concepts in Phase 8 are the two known ones ahead. Deep understanding there is worth the extra time; it's exactly what's likely to get probed hard in an interview.
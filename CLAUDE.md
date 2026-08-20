# Project Context

Hands-on learning project — Java 21 + Spring Boot distributed order-processing platform, built in phases (Postgres, Redis, Kafka, Elasticsearch, Kubernetes).

## How to work in this repo

- Generate code in small, reviewable increments — one class, one config file, or one closely related pair (e.g. entity + its migration) at a time. Do not implement a whole feature or multiple files in one pass unless explicitly asked.
- Every non-trivial piece of code must include comments explaining *why*, not just what — especially anything Spring/JPA/Kafka/Redis-specific that isn't obvious from the code alone (e.g. why `ddl-auto: validate` instead of `update`, why a particular Kafka consumer config matters).
- After each increment, briefly summarize what was added and why, then stop and wait for confirmation before continuing to the next piece.
- Do not refactor unrelated code or add scope beyond what was asked.
- When there's a real design trade-off (cache-aside vs write-through, at-least-once vs exactly-once, etc.), explain the options rather than silently picking one.
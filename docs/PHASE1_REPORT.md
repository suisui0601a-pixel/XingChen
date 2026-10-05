# Phase 1 report

## Implemented

- Gradle Kotlin DSL Java 21 / Spring Boot application skeleton and health endpoint.
- Platform-neutral event identities, in-memory identity registry, membership and alias model.
- Relationship term model and explicit scope/precedence resolver.
- Memory DTO, mandatory provenance repository contract, scoped retrieval and SQLite FTS adapter.
- Context budgets, scope filtering, lifecycle states and handoff DTO/interface.
- Separate prompt layers, version records, history and rollback repository.
- Closed-by-default agent capability policy and typed externalized configuration.
- SQLite Flyway V1 schema and persistence adapter; architecture and coverage documentation.

## Deliberate Phase 1 limits

No real platform/API integration, production Console, QQ/NapCat/SnowLuma/DSH/DeepSeek connectivity, prompt UI, memory summarization, embedding search, or deployment. Legacy features are enumerated in `LEGACY_FEATURE_MATRIX.md`; vague or source-ambiguous entries stay `NEEDS_REVIEW`.

## Acceptance record

- Environment: Temurin JDK `21.0.12.1+1` LTS; Gradle Wrapper `9.8.0`; Spring Boot `3.5.15`.
- `gradlew.bat clean test`: passed, 20 tests across 3 suites, 0 failures/errors.
- `gradlew.bat bootJar`: passed; `build/libs/xingchen-core-0.1.0-SNAPSHOT.jar` generated.
- Local runtime smoke test: Flyway V1 and SQLite initialized; `GET http://127.0.0.1:3200/health` returned HTTP 200 `{"status":"UP","application":"xingchen-core"}`. Temporary local process was stopped after the check.
- SQLite startup contract confirms WAL mode, `foreign_keys=1`, `memories`, and FTS5 table.
- Feature matrix: 110 rows (15 partially represented by Phase 1 primitives; 94 planned for later implementation; 1 reserved-mode behavior is `NEEDS_REVIEW`). Partial representation is not a claim of end-user feature parity.
- Production isolation: no SSH, no server/production/NapCat/SnowLuma/QQ/DSH/DeepSeek/Zetu activity.
- Git commit: see final task report.

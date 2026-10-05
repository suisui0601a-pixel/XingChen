# Phase 4C-1 — Closure Report

**Status: COMPLETE. A/B/C/D/E and Final Audit PASS. Phase 4C-2 NOT STARTED.**

## Current evidence

- Tested runtime source: `fb8e70860ab457fa09e334ed69ba4f9a1f50b8f3`.
- Official Dockerfile multi-stage build PASS, without prebuilt jar substitution.
- Image digest `sha256:5764a4606d3571a98f1f1fc8959628db4e5460bbbe17b1a9f0aa7e8b2a67cec6`; 144,357,613 bytes.
- Final Linux regression: backend 339/339, frontend 38/38, typecheck/build/bootJar PASS.
- Fresh volume ownership/Flyway, actual non-root/read-only/tmpfs/capabilities/nnp/loopback/health PASS.
- Docker restart and same-volume remove/recreate PASS for all requested persisted state, rotated credentials, bootstrap precedence and Secret CLEAR.
- Real HTTP Prompt contention: 20 rounds, one success/one conflict each, no 500.
- Isolated fake Gateway HTTP/WS, disconnection/no production fallback PASS.
- SIGTERM graceful completion, standard exit 143, DB integrity and healthy recovery PASS.
- Read-only /data fail-fast/no fallback PASS.
- Two real-container browser cases PASS, with initial failed selector evidence retained and exact-title correction rechecked.
- No live integration or production secret used.

## Source corrections made during E

Recreate npm CLI symlink in copied build tooling; own all fresh-volume persistent directory ancestors; remove Prompt JVM/SQLite lock inversion while preserving transactional CAS; load the exact packaged SQLite native library from immutable image directory instead of noexec /tmp. No dependency version change, security relaxation, shim or prebuilt jar workaround.

## Resource / production result

The user revoked the 20 GiB recommendation. The 12 GiB floor and 14 GiB warning were enforced with five-second samples. Root start 17.615 GiB, lowest 14.195 GiB, finish 17.605 GiB. Minimum available memory 1.696 GiB; no OOM; swap NONE.

All staging resources and dedicated builder/cache removed after local evidence capture. No broad prune. Zetu API/Web healthy, Caddy running without a healthcheck; exact container IDs and restartCount=0 unchanged. Retired QQ archives/secrets remain untouched.

## Audit and scope

See PARTITION_A_REPORT through PARTITION_E_REPORT and PHASE4C1_EVIDENCE_AUDIT. Matrix rows 122–125 are now TESTED; remaining legacy gaps stay unclaimed. Phase completion is validation evidence, not a production rollout.

Normalized evidence: `docs/evidence/phase4c1-e-summary.json`. Raw archive and selective browser log locations/hashes are recorded in PARTITION_E_REPORT. Historical C/D and initial resource-blocked findings remain in Git/history and saved evidence; the withdrawn 20 GiB recommendation is not the current disposition.

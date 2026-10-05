# Partition E — Real Docker Runtime

**Status: PASS — RESOURCE-CONSTRAINED CONTROLLED ATTEMPT completed on 2026-10-04.**
The user revoked the recommended 20 GiB entry gate. The absolute 12 GiB floor was NOT lowered. No Phase 4C-2 work was performed.

## Source and build

- Runtime source: clean Git archive of `fb8e70860ab457fa09e334ed69ba4f9a1f50b8f3`.
- Ubuntu 22.04.5 LTS, amd64; Docker 29.7.2, Compose v5.5.0, Buildx v0.36.1.
- Actual official multi-stage Dockerfile built frontend and bootJar; no prebuilt jar substitution or dependency-version change.
- Node 24.19.0 / npm 11.17.0; Temurin JDK/JRE 21.0.12.1; Gradle 9.8.0.
- Image digest: `sha256:5764a4606d3571a98f1f1fc8959628db4e5460bbbe17b1a9f0aa7e8b2a67cec6`; inspect RepoDigest: `xingchen-c4c1-core@sha256:5764a4606d3571a98f1f1fc8959628db4e5460bbbe17b1a9f0aa7e8b2a67cec6`.
- Image size: 144,357,613 bytes. Packaged jar: 44,681,397 bytes, SHA256 `382e68796e846879337553857552f0907fc6f793a5dd25a16f2ea695d0b519ef`.
- Full Linux revalidation of this runtime source: backend **339/339**, failures/errors/skips 0; frontend **38/38**, typecheck/build/bootJar PASS.

## Actual runtime evidence

| Gate | Result | Observed evidence |
|---|---|---|
| Image audit | PASS | Actual saved layers and packaged jar scanned; frontend assets present; no Git/Gradle/toolchain/node_modules/SSH/local database/secret payloads in runtime. |
| Fresh volume / Flyway | PASS | Empty named volume populated with all ancestors UID/GID 10001; 27 migrations applied; DB under /data/db, no fallback. |
| Runtime security | PASS | Effective UID/GID 10001; rootfs read-only; /tmp tmpfs noexec/nosuid/nodev; writable /data volume; CapEff=0; NoNewPrivs=1; not privileged; no Node/npm/Gradle runtime tool. |
| Host publication / health | PASS | Actual NetworkSettings.Ports and ss: only 127.0.0.1:13200; real HTTP and healthy probe. No public staging port. |
| Representative state | PASS | API-created Memory, active PERSONA/SIMULATION Prompt IDs, Sticker metadata/byte SHA, Access rules/revision and exact 18-decimal Pricing. |
| Concurrent Prompt writes | PASS | 20 real HTTP rounds: exactly one winner and one 409; one new history row each, no 500. |
| Docker restart | PASS | All representative state preserved; rotated password works, bootstrap rejected; OneBot and DeepSeek CLEAR remain cleared. |
| Same-volume remove/recreate | PASS | New container ID, same volume, all state preserved; old and changed bootstrap passwords rejected; CLEAR not revived. |
| Isolated fake Gateway | PASS | Real HTTP + WS to fake-only internal network; no revived authorization; disconnect reflected; endpoints remain fake, no production fallback. |
| SIGTERM | PASS | Java PID 1 exits 143 after Spring graceful-start and graceful-complete messages; no OOM, database integrity_check=ok; healthy recovery with state intact. |
| Explicit-empty public URL | PASS | Empty persisted URL survives restart despite unchanged bootstrap, correct cookie/security posture. |
| Unwritable /data | PASS | Separate non-root, network-none container with read-only data mount and valid writable /tmp fails with Persistent data directory not writable, nonzero exit/no OOM/no fallback. |
| Real-container browser | PASS | Two case-specific passes using existing Windows Chrome through strict SSH tunnel: login/Overview/Memory/Operations/reload/logout; auth/CSRF/404/safe error bodies/cookies/storage and browser error audit. |

Browser evidence consists of the API/security case passing in the initial two-case run, followed by the corrected page case passing in a selective one-case run. This is **two verified cases**, not a claim that the initial run passed 2/2.

## Findings and corrections

Production-source fixes, each committed and revalidated:
1. Docker COPY dereferenced npm's CLI symlink; recreate the symlink in build stage.
2. Fresh volume parent ownership: own /data and /data/assets, not only leaf directories.
3. Redundant Prompt JVM lock inverted SQLite IMMEDIATE transaction locking; remove it while retaining transactional/CAS conflict protection. Twenty-round concurrency assertions passed in Linux and real HTTP.
4. SQLite JDBC could not load a native library extracted into noexec /tmp. Extract the exact packaged JDBC native library during the multi-stage build into immutable /app/native; keep /tmp noexec and read-only rootfs.

Test-fixture corrections (not production workarounds):
- Registry access used the dedicated builder's host network; no host DNS/daemon change.
- Internal-only Core test networking had no actual host publication. Fake Gateway remains on an internal network; only Core additionally joins a dedicated staging console bridge. Neither network contains production services.
- Accept standard SIGTERM 143 only with graceful-completion logs, no OOM and DB integrity; no wrapper to fabricate exit 0.
- Unwritable-data fixture must use the official /tmp mode 1777 so failure genuinely exercises /data.
- Memory's exact visible heading is Long-term memory / 长期记忆, not its navigation label. Corrected test selector without changing UI/image.

Failed attempt logs and summaries remain in the raw evidence archive; they are not counted as PASS.

## Resources and cleanup

- Root free: preflight **18,913,869,824 bytes (17.615 GiB)**; lowest observed **15,242,158,080 (14.195 GiB)**; final df after temporary evidence-file cleanup **18,902,851,584 (17.605 GiB)**. The preceding cleanup checkpoint was 18,902,769,664 bytes.
- Five-second df -B1 / and MemAvailable sampling across build/runtime stages. Neither <=14 GiB warning nor <=12 GiB hard stop was triggered.
- Lowest available memory: **1,821,413,376 bytes (1.696 GiB)**. Builder memory.events: oom=0 / oom_kill=0; kernel OOM search returned no matches. No Linux OOM event observed.
- Swap: **NONE**. No fstab, swappiness, drop_caches, daemon/firewall/DNS/SSH trust changes.
- Dedicated builder/cache removed after build/regression; no broad prune.
- All task-owned containers, both staging networks, volume, tagged/untagged attempt images, builder image, staging directory and temporary SSH tunnel removed. Raw evidence copied before deletion.
- Final Docker baseline: 10 images, 5 total/3 active containers, 4 existing volumes, Build Cache 0B. No xingchen-c4c1 resources remain.
- Zetu API/Web healthy; Caddy running (no healthcheck); all three exact IDs unchanged and restartCount=0. Legacy dsh-qq-main and snowluma remain absent; preserved QQ archives/secrets untouched.

## Evidence

Tracked normalized evidence: `docs/evidence/phase4c1-e-summary.json`.
Raw archive retained locally at `C:/Users/win/Documents/Codex/2026-09-29/files-pasted-by-the-user-0/work/xingchen-c4c1-e-evidence.tgz`,
SHA256 `10836743151d6b71513bf88a07497d2a04d53a7fcb1aba918e752518712b2190`.
Browser logs: same work directory, `browser-smoke.log` and `browser-page-recheck.log`.
The raw archive includes drivers, resource samples, build/regression logs, inspect records and failed attempts; runtime fixtures contain only artificial credentials, never production secrets.

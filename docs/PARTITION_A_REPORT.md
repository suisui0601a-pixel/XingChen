# Partition A — Packaging / Static Audit

**Status: PASS — packaging, real Compose parsing and actual image audit.** Final runtime source `fb8e70860ab457fa09e334ed69ba4f9a1f50b8f3` was built using the official multi-stage Dockerfile and audited in Partition E. npm CLI symlink, persistent parent ownership and immutable SQLite native extraction corrections are revalidated. Runtime also contains `/app/native/libsqlitejdbc.so` from its own packaged JDBC dependency; no executable temporary mount is needed. See PARTITION_E_REPORT for digest/layers/security evidence.

## Historical static record (before final E; superseded runtime-unverified statements)

- Audited commit: `e0ca8493151a2efa7cff4bccb2f97e830ba9d579` (worktree clean before this evidence report).
- `Dockerfile` is multi-stage: pinned Node 24.19.0 frontend tools (base image digest pinned), Temurin JDK 21.0.12.1 build, Temurin JRE 21.0.12.1 runtime. Build compiles frontend and `bootJar`.
- Runtime creates `/data/{db,config,prompts,logs,assets/stickers}` and `/tmp`; fixed UID/GID 10001, no home, non-login shell. Entrypoint is exec-form Java. Healthcheck is a JRE-only Java HTTP probe.
- Compose declares a named volume at `/data`, read-only root, bounded `/tmp` tmpfs, all capabilities dropped, `no-new-privileges`, and host publication `127.0.0.1:3200:3200`. No privileged mode, host PID/network, host-root mount, or Docker socket is declared.
- Container profile's `0.0.0.0` is the in-container bind; the documented host publication remains loopback-only and the explicit remote-bind guard requires opt-in/bootstrap credentials.
- SnowLuma/QQ/proprietary Gateway are not copied into the image or declared as services. Runtime stage contains only the app JAR and health probe; no Node/npm/Gradle/SSH runtime tools.
- `.dockerignore` excludes Git history, Gradle/build caches, `.toolchain`, `node_modules`, local data, secrets/keys, logs, and sticker/archive/compressed-archive patterns. `.env.example` is a placeholder template. Tracked-file scan found no database, secret/key material, cache, sticker archive, or vendored package tree.
- `gradlew` is tracked with mode `100755`.
- Closure on the real Ubuntu Docker host: `docker compose -p xingchen-c4c1-static -f /opt/xingchen-c4c1-d/source/compose.yml config --quiet` passed with Compose v5.5.0. This validates Compose parsing, not runtime enforcement.
- `.gitattributes` now fixes shell entrypoints to LF; Windows `git archive` with `core.autocrlf=true` was tested and Linux `./gradlew --version` succeeded. Source fixes are recorded in commits `8a520a0` and `7ad879a`.

**Evidence:** static reads of `Dockerfile`, `compose.yml`, `.dockerignore`, `src/main/resources/application-container.yml`, `docs/DEPLOYMENT.md`, `docs/DATA_LAYOUT.md`; tracked-file/mode inspection. The executable Compose/Docker gate remains in Partition E.

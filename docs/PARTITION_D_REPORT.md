# Partition D — Fresh Linux Build

**Status: PASS — TESTED on real Linux.**

## Final runtime-source revalidation

Clean archived source `fb8e70860ab457fa09e334ed69ba4f9a1f50b8f3` compiled in the official Linux multi-stage Docker build, then the full suite ran in the same build-stage environment using isolated fixture directories. Backend **339/339**, frontend **38/38**, typecheck/build/bootJar PASS; 0 backend failures/errors/skips. Node 24.19.0, npm 11.17.0, Java 21.0.12.1, Gradle 9.8.0 unchanged. Current runtime jar: 44,681,397 bytes, SHA256 `382e68796e846879337553857552f0907fc6f793a5dd25a16f2ea695d0b519ef`. Packaging/JDBC-native and strengthened 20-round Prompt concurrency contracts are included. See PARTITION_E_REPORT and `docs/evidence/phase4c1-e-summary.json`.

## Historical native-host D validation (original source/artifact; former 20 GiB recommendation now revoked)

- Source commit: `7ad879afedfef4204f4def246d8d18ce222e9a29`, archived from clean Git and extracted onto Ubuntu's native filesystem at `/opt/xingchen-c4c1-d/source`.
- Ubuntu 22.04.5 LTS, kernel 5.15.0-190-generic, amd64.
- `gradlew` Git mode: `100755`; extracted executable mode `0775`; Unix shebang and LF were verified and `./gradlew --version` succeeded.
- Official runtime downloads were SHA256-verified: Node `v24.19.0`, npm `11.17.0`, Temurin JDK `21.0.12.1+1`; Gradle Wrapper verified its pinned `9.8.0` distribution checksum.
- Toolchains, dependency caches, source and all default test data/secret/sticker directories were explicitly scoped to the D staging tree. No live integrations or production secrets were supplied. The opt-in paid `liveTest` task was not run.

Actual build command:

```sh
./gradlew --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=512m' \
  -PXINGCHEN_GIT_COMMIT=7ad879afedfef4204f4def246d8d18ce222e9a29 \
  clean test frontendTypecheck frontendTest bootJar
```

Result: `BUILD SUCCESSFUL in 4m 23s`; backend **336/336**, 0 failures/errors/skips; frontend **38/38** across 10 test files; typecheck, frontend build and bootJar passed. JAR size **44,682,628 bytes**; SHA256 `b53ac83f0efa7cdb6ba0c6ffe2692d727bfe816ba8f65f574ef074ed3fb61c0b`. Machine-readable evidence: `docs/evidence/phase4c1-linux-summary.json`.

## Linux findings fixed in the repository

1. Windows `git archive` converted the executable wrapper to CRLF despite its stored LF bytes. Added `.gitattributes` shell LF rules in commit `8a520a0`; a Windows archive under `core.autocrlf=true` and real Linux wrapper execution both verified the fix.
2. Initial Linux full regression had 335 passes/1 failure: the fake DSH server sent mux `end` and immediately closed TCP, creating a WebSocket race. Commit `7ad879a` keeps the fixture transport alive until the client consumes end/closes, and strengthens completion/event/failure assertions. Local targeted contracts passed 25/25; the final full Linux regression passed 336/336. No production adapter patch or timeout inflation was used.

The first build attempt was deliberately interrupted to place default fixture paths under staging; its newly created empty `/data` directories were removed with nonrecursive `rmdir`. Final tests used only the explicit staging paths.

No Linux Playwright browser was provisioned. Browser E2E remains assigned to the real-container smoke gate in E. Earlier Windows browser results are not container evidence.

The resource watcher stayed above the 12 GiB hard floor (approximately 16.30 GiB minimum during the final build). Temporary swap was not needed. After evidence was saved, the entire D staging/toolchain/build/cache tree and temporary helper scripts were removed. Final root free space was 18,914,435,072 bytes (17.62 GiB), still below E's 20 GiB entry gate.

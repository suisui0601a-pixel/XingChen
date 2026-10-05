# Partition B — Container Security Contract

**Status: PASS — DECLARED, STATICALLY_TESTED and RUNTIME_VERIFIED.** Final Linux regression on runtime source `fb8e70860ab457fa09e334ed69ba4f9a1f50b8f3` passes all four packaging contracts within 339 backend tests. Actual Docker inspection and probes verify UID/GID 10001, read-only rootfs, noexec/nosuid/nodev `/tmp` tmpfs, CapEff=0, NoNewPrivs=1, not privileged, loopback-only publication and writable named `/data`. See PARTITION_E_REPORT and normalized security evidence. None of these are inferred solely from YAML.

## Historical declared/static-only record (before E; the No runtime column below is historical)

Ran on audited commit `e0ca8493151a2efa7cff4bccb2f97e830ba9d579` (as two offline Gradle invocations):

```text
./gradlew.bat test --offline --tests '*ContainerPackagingContractTest'
./gradlew.bat test --offline --tests '*DataPathResolverTest'
BUILD SUCCESSFUL
```

`ContainerPackagingContractTest`: 1 test, 0 failures/errors/skips. `DataPathResolverTest` also passed. The contract test checks declarations including non-root `USER`, `read_only`, `/tmp` tmpfs, `cap_drop: ALL`, `no-new-privileges`, no privileged mode, loopback host bind, persistent `/data` volume, bridge network, container profile/integrations, healthcheck, exec entrypoint, and absence of Node/npm/Gradle/socket in runtime.

| Contract | Declared | Statically tested | Runtime verified |
|---|---|---|---|
| Non-root UID/GID | Yes (`10001:10001`) | Yes | No |
| Read-only root filesystem | Yes | Yes | No |
| `/tmp` tmpfs | Yes | Yes | No |
| Drop all capabilities | Yes | Yes | No |
| `no-new-privileges` | Yes | Yes | No |
| `privileged: false` / absent | Absent | Yes | No |
| Host bind loopback only | Yes (`127.0.0.1:3200`) | Yes | No |
| Persistent `/data` | Named volume declared | Yes | No |

No claim is made about effective container UID, mount flags, capabilities, Docker publication, or persistence across container recreation. Those require Partition E.

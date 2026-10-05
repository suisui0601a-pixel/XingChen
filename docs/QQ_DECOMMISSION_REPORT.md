# Legacy QQ production decommission

**Status: DECOMMISSIONED.** The user explicitly authorized permanent retirement without restoring the old QQ service after validation.

## Ownership and archive

All active and stopped Docker containers were inspected before deletion. The two QQ images had no non-QQ container references; the bridge volume had no non-QQ references; `snowluma-docker_default` had only SnowLuma as an endpoint. QQ bind paths were under `/opt/dsh-qq` and did not overlap Zetu mounts. The builtin host network and all Zetu resources were preserved.

- Historical backup retained: `/opt/dsh-qq/backups/pre-final-clean-20260930-050509`; all 47 manifest checksums verified.
- New restricted archive directory: `/opt/dsh-qq/backups/decommission-20261004-110913`.
- `QQ_DECOMMISSION_MANIFEST.json` records versions, exact image IDs/refs/digests, Compose ownership, mount/network topology, state locations, stop/start order and restoration notes without secret values.
- `latest-persistent-state.tar.gz`: 587,928,327 bytes, 19,002 members; refreshed with both QQ containers stopped and verified before deletion.
- The archive includes current DSH/Bridge bind state, SnowLuma/QQ bind state, build/Compose recipes and the full Bridge config/source volume. Archive and metadata have owner-only access and a SHA256 manifest.
- Image binaries were not exported; exact identities and original recipes are recorded. Restoring may require reacquiring/rebuilding those revisions. Saved QQ sessions do not guarantee scan-free login.
- Bind directories `/opt/dsh-qq/current`, `/opt/dsh-qq/snowluma-docker`, production secrets, and historical backups remain as restoration material. No activity is attached to the retired QQ containers.

## Precisely retired resources

- Containers: `dsh-qq-main`, `snowluma`.
- Volume: `dsh-qq-bridge-root-v017-61b7e2e`.
- Network: `snowluma-docker_default`.
- Image: `sha256:2d5e4d41c79caff37b95e1ae557c850f5f115704b9a27cab20ae734152663be2` (`dsh-qq-main:0.1.7-rc.2-bridge-0.1.7`).
- Image: `sha256:85ce555ec0025a72c3bad08bbc70c00225ef483fb5f2f6eccf739444780d9f75` (`snowluma-official:v1.14.20-framework-fa52eb7`).

No broad prune was run. Build Cache was 0B, so no builder prune was necessary. Unproven/shared resources, including unrelated unused images and the two stopped Zetu load-test containers, were retained.

## Production and resources

Zetu container IDs, running/health states and restart counts matched the saved baseline after retirement. API and Web remain healthy with restart count 0; Caddy remains running without a defined healthcheck and restart count 0. Port 80/443 listeners are unchanged. The Web loopback endpoint returns its expected HTTP 302 response.

Root free space: 15,527,325,696 bytes before retirement -> 18,919,489,536 bytes after retirement. This is approximately 14.46 -> 17.62 GiB, below the 20 GiB Partition E startup gate. Temporary swap: NONE.

An initial combined retirement script was rejected by automatic approval review. Execution proceeded through separately verified ownership/backup, stop, stopped-snapshot refresh, and exact resource deletion. All deletion occurred only after verified archival; no rejected script was executed.

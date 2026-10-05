# Persistent data layout

The container profile uses one root: `/data`. The Docker Compose example mounts one named volume at that path. Paths below describe the current runtime, not a backup or restore implementation.

| Path | Current use | User data / secret | Backup importance |
| --- | --- | --- | --- |
| `/data/db/xingchen.db` | SQLite, Flyway schema, admin credential hashes, configuration, Memory, identity/relationship, Prompt versions, Usage/Pricing/Access and audit records | Yes; contains user and operational data | Required |
| `/data/db/xingchen.db-wal`, `-shm` | SQLite WAL sidecars while the database is open | Database state / transient coordination | Do not copy while running; include the SQLite database using a consistent SQLite-aware method in the future backup phase |
| `/data/config` | Owner-only credential files and `.cleared` tombstones used by `FileSecretStore` | Yes; contains credentials or explicit secret-clear state | Required |
| `/data/prompts` | Reserved for a future external Prompt-file feature; Prompt content currently lives in SQLite | No current runtime dependency | Not currently required |
| `/data/assets/stickers` | Uploaded and managed Sticker image files | Yes; user media | Required when Sticker assets are in use |
| `/data/logs` | Reserved directory; application currently logs to stdout/stderr and keeps a bounded operational event ring in memory | No persistent app-log files currently | Docker/runtime owns stdout retention |
| `/tmp` | Multipart upload and JVM temporary files, supplied by Compose tmpfs | No durable state | Recreated at container start |

The database, configuration, and Sticker files must remain on a local filesystem or Docker local named volume. NFS/SMB is not a supported default for SQLite WAL. The `prompts` and `logs` directories are created and write-checked for a consistent DataRoot layout; current runtime features do not pretend to read/write external Prompt files or durable app log files there.

Credentials in `/data/config` use owner-only file permissions. The directory belongs to container UID/GID `10001`; do not relax it to world-writable. SQLite contains hashed administrator credentials and persisted configuration. It does not contain the raw Console password or FileSecretStore credential values.

This document describes what a future Phase 4C-2 backup must consider. It does not claim a backup/restore mechanism or live SQLite file-copy safety.

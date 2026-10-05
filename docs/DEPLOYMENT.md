# XingChen Core container deployment

**当前生产流程**：见下文 `PRODUCTION OPERATIONS` 与
[浏览器首次初始化](ADMIN_INITIALIZATION.md)。下述 Phase 4C 示例仍保留为开发/
隔离验证参考；本次正式部署不把管理员明文写入 `.env`，不保留 bootstrap 密码，
不直接发布 Core 端口。

Phase 4C-2 recovery/deployment references: [data model](BACKUP_DATA_MODEL.md),
[offline backup/restore and new-host recovery](BACKUP_RESTORE.md),
[limited legacy mapping](LEGACY_MIGRATION_MAPPING.md), [migration workflow](LEGACY_MIGRATION.md),
and [HTTPS/Caddy trust boundary](HTTPS_CADDY.md). The recovery CLI requires canonical
container data paths and Linux POSIX locking. Never copy a running SQLite file or
remove the active volume as an upgrade shortcut. Public ACME is documented only.

## Architecture and trust boundary

The Core image contains the XingChen Spring Boot application, its static Console, and a JRE. It does not contain SnowLuma, QQ, DSH, proprietary Gateway files, Node.js, npm, Gradle, SSH tools, local credentials, or Docker socket access. SnowLuma is a separately acquired and operated external OneBot Gateway; follow its own official terms. No SnowLuma image is pulled or redistributed by these files.

The Core listens on `0.0.0.0:3200` inside its container so Docker networking can reach it. Compose publishes only `127.0.0.1:3200:3200` on the host. The container profile keeps the existing remote-bind guard: `XINGCHEN_CONSOLE_ALLOW_REMOTE=true` and valid bootstrap administrator credentials must both be present or startup fails. The sample `.env` explicitly opts into this container-network bind; the published host port remains loopback. Other containers attached to `xingchen-core-net` can reach the service, so attach only trusted services. The Operations/Security `bindAddress` reports the application's in-container bind address; host publication is controlled by Compose.

## Build and run

Requirements: Docker Engine/Desktop with Compose v2 and outbound access to the official Node/Eclipse Temurin images, Gradle distribution, Maven Central, and npm registry on the first build. The build stages use pinned Node 24.19.0 and Temurin JDK/JRE 21.0.12.1 tags; the Node image index is digest-pinned. Gradle Wrapper builds the frontend and bootJar inside the build stage. No host Java installation is required.

PowerShell from the repository root:

```powershell
Copy-Item .env.example .env
# Edit .env: set a unique administrator name and a strong 14–72 UTF-8-byte password.
$env:XINGCHEN_GIT_COMMIT = (git rev-parse HEAD).Trim()
docker compose config
docker compose build xingchen-core
docker compose up -d xingchen-core
docker compose ps
Invoke-WebRequest http://127.0.0.1:3200/health
```

Compose configuration parses with unset variables, but the container's existing bind guard intentionally refuses to start until the administrator credentials are valid and `XINGCHEN_CONSOLE_ALLOW_REMOTE=true` is explicitly selected in `.env`. Open `http://127.0.0.1:3200`. Never publish port 3200 on `0.0.0.0` as a shortcut. A future reverse proxy must authenticate and terminate HTTPS; configure secure cookies and trusted-proxy/public-URL settings deliberately before exposure.

For a named release tag, set `XINGCHEN_VERSION` in `.env`; do not rely on `latest`. `XINGCHEN_GIT_COMMIT` is passed to the Gradle build and OCI revision label because `.git` is excluded from build context. The image embeds this revision in build metadata where available.

## Data and bootstrap

Compose uses one named volume at `/data`; on an empty named volume Docker initializes it from the image's `/data` directory owned by UID/GID `10001`. DataPathResolver creates and write-checks required directories at startup and fails immediately if a path is a file, missing, or unwritable. It never falls back to `/tmp`. SQLite/Flyway use `/data/db`; FileSecretStore uses `/data/config`; Sticker assets use `/data/assets/stickers`. See [DATA_LAYOUT.md](DATA_LAYOUT.md) for user-data and secret contents.

The Console username/password and provider/OneBot credentials from environment initialize empty persistent state only. SQLite and FileSecretStore then govern effective configuration. Changing the Console password in the UI persists it; a retained bootstrap password does not replace an existing credential. A FileSecretStore `.cleared` tombstone takes precedence over environment bootstrap after restart. Keep the administrator bootstrap values available to pass the existing non-loopback application bind guard on container starts; they are not used to overwrite a persisted password. Rotate and protect `.env`, exclude it from version control, and prefer a restricted environment file or a later dedicated secret mechanism. Do not put values into Dockerfile, image build args, or committed Compose YAML.

For Windows/local development, `XINGCHEN_DATA_DIR` defaults to `./data`, keeping the database's historical `data/db/xingchen.db` location. Existing explicit `XINGCHEN_DB_PATH`, `XINGCHEN_SECRET_DIR`, and `XINGCHEN_STICKER_ROOT` overrides remain available for test and advanced local setups. Compose sets only the unified container root; its profile derives the production database, secret, and Sticker paths from `/data`.

`/data/prompts` is reserved: active Prompt versions currently live in SQLite. `/data/logs` is also reserved: application output goes to stdout/stderr and operational events are a bounded in-memory ring. Docker/runtime controls stdout retention. The container uses `/tmp` tmpfs for uploads and JVM temporary files.

## Bind mounts, gateway connectivity, and updates

The named volume is the default and avoids host-path ownership surprises. For a Linux bind mount, create the host directory and grant only the container identity access, for example `sudo install -d -o 10001 -g 10001 -m 0750 ./data`; configure the Compose volume as `./data:/data`. Do not use `chmod 777`. Windows Docker Desktop bind permissions vary by filesystem; verify a fresh start and write test before using a bind mount.

Inside the container `127.0.0.1` means Core itself. OneBot URLs remain disabled for actual connection by default. When an independently operated Gateway joins `xingchen-core-net`, set `ONEBOT_WS_URL=ws://<gateway-service-name>:3001` and its HTTP endpoint to the corresponding service DNS name. A Gateway on the host may be reachable as `host.docker.internal` on Docker Desktop; Linux Engine may require an explicit `host-gateway` mapping. Never copy a production host address into this example. No Gateway service or image is defined here.

Core and Gateway are separate products. To add an independently obtained SnowLuma container, have its operator join `xingchen-core-net` and configure the Core's OneBot endpoint to that container's service name. Do not add its proprietary files to this build context or XingChen image.

To update, first stop Core and verify a consistent pre-upgrade backup, then build a new explicit version/revision and recreate Core with the same `xingchen-data` volume. Current Flyway migrations run at startup and are forward-only. Never attach a forward-migrated database to an older image as an in-place downgrade. Rollback means restoring the verified pre-upgrade backup into a **different** data volume and starting its matching old image. Format 1 recovery accepts schema 28 only; a schema 27 pre-upgrade backup needs a separately reviewed compatible procedure, not a falsified format 1 manifest. Do not remove the original named volume when replacing the container. Keep SQLite on local Docker storage, not NFS/SMB.

## Runtime security and troubleshooting

SQLite JDBC's native library is extracted during the multi-stage build from the exact dependency inside bootJar into root-owned `/app/native`. Java loads it with `-Dorg.sqlite.lib.path=/app/native`; the read-only image protects it. Keep `/tmp` `noexec,nosuid,nodev` with writable mode 1777; do not remove noexec or grant root to work around native-library loading.

For an explicitly reviewed non-loopback Gateway service, its existing endpoint guard also requires `--xingchen.onebot.allow-non-loopback=true` as an application argument. This is separate from the Console bind opt-in; external integrations remain disabled by default. In the E fixture, only fake Gateway and Core share the internal test network; Core additionally uses a dedicated console bridge for loopback publication. Do not copy test endpoints/credentials or attach production services to a fixture network.

The image runs as UID/GID `10001`, drops all Linux capabilities, sets `no-new-privileges`, and is designed for a read-only root filesystem. Compose enables `read_only`, gives `/tmp` a bounded 64 MiB tmpfs, and allows 30 seconds for graceful SIGTERM shutdown. Spring Boot gracefully stops HTTP handling with a 20-second phase budget; Java runs as the exec-form container process. There is no privileged mode, host network/PID, host-root mount, shell wrapper, SSH client, or Docker socket.

Docker health invokes a tiny JRE-only Java HTTP probe against unauthenticated `/health` and requires both HTTP 200 and `status=UP`; it does not read an authenticated management API or a secret. The endpoint checks database readiness. Integrations are not required for health.

- Bind guard refuses startup: verify the explicit allow-remote setting and valid bootstrap administrator variables. This guard is required for the in-container all-interface bind.
- Health is down or startup exits: inspect `docker compose logs xingchen-core`; check volume ownership and that `/data` is writable by UID 10001. Do not work around this by switching database paths to `/tmp`.
- Console cannot be reached remotely: the default host port is loopback-only. Use a local browser or a separately reviewed authenticated HTTPS reverse proxy.
- Existing credentials do not change after environment edits: this is intentional. Persisted SQLite/FileSecretStore state wins; environment values seed only uninitialized state.
- Flyway rejects a schema: preserve the volume and logs. Do not delete the database or try a destructive reset. Fix the candidate image, or restore a verified pre-upgrade backup into a new volume with its matching image; forward migrations do not imply in-place downgrade support.

Real Docker validation passed on the authorized Linux worker through strict SSH, including image audit, effective security, restart/recreate persistence, fake transport, graceful shutdown, data permissions and real-container browser smoke. See `PARTITION_E_REPORT.md`. All staging resources were removed; this validation is not a production deployment. Windows Docker CLI remains unnecessary for the remote validation workflow.

## PRODUCTION OPERATIONS

生产拓扑：既有 Caddy → `xingchen-prod-edge` → `xingchen-core:3200`，没有
Docker ports 映射。Caddy 保留原网络，只新增此专用网络；Zetu API/Web 不接入该网络。
`deploy/compose.production.yml` 使用精确 image ID、外部 `xingchen-prod-data` 的
`live` 子目录，non-root 10001、只读 rootfs、64 MiB `/tmp`、ALL caps dropped、
no-new-privileges、768 MiB 上限/256 MiB reservation、1 CPU、日志轮转。

首次初始化是独立阶段：准备空卷与 `live` 子目录后，使用
`deploy/compose.initialize.yml` 仅监听服务器 loopback 3200；经受信任 SSH tunnel
手动访问 `/initialize`。初始化后停止并备份 Core，再用同一 image/卷启动正式
Compose。正式环境关闭初始化、显式启用 persisted-admin bind guard、Secure Cookie
和 canonical HTTPS URL。数据库没有管理员时 remote bind 会失败，不要绕过守卫。

### 当前部署的启停与健康

服务器运行目录 `/opt/xingchen-production`。`deployment.env` **仅**包含精确
`XINGCHEN_IMAGE`，不含真实用户名/密码/API/Gateway key。实际应用代码 image revision
见 PHASE5_PRODUCTION_GO_LIVE.md；后续文档/config commit 不假称已进入 image。

```sh
cd /opt/xingchen-production
docker compose --env-file deployment.env -f compose.production.yml config -q
docker compose --env-file deployment.env -f compose.production.yml up -d --no-build xingchen-core
docker compose --env-file deployment.env -f compose.production.yml stop xingchen-core
docker restart -t 30 xingchen-core
docker inspect -f '{{.State.Health.Status}}' xingchen-core
docker logs --tail 100 xingchen-core
```

日志也属于受保护运维数据，不要把原始日志或完整 inspect/env 粘贴到公开 issue。
不要执行 `compose down -v`；不能删除持久卷作为修复手段。重启后会话可能失效，
管理员必须手动重新登录，自动化不得提交其真实密码。

### 备份与 verify

使用 `tools/recovery.py` 格式 1、schema 28。必须先停止 Core；在线裸拷 SQLite
不构成有效备份。运行工具的 UID 必须拥有输入目录，工具会检查 POSIX 锁与权限。
本部署在停止 Core 后创建受保护的 root-owned offline-input 副本，然后使用
**未修改**的恢复工具 backup/verify；原始卷的 10001 权限不变。仅在验证 bundle
成功后删除该离线输入副本。目录/文件分别 700/600，不输出任何内容或密码哈希。

```sh
python3 tools/recovery.py backup --data <PRIVATE_OFFLINE_INPUT> \
  --destination <PRIVATE_NEW_BUNDLE> --application-version <VERSION> --revision <IMAGE_REVISION>
python3 tools/recovery.py verify --bundle <PRIVATE_BUNDLE>
```

实际首份备份位置见上线证据。备份不在 live data 卷内；包含敏感 DB/SecretStore，
校验和不代表加密。**OFF-HOST COPY REQUIRED**：当前没有异机副本，不宣称异地恢复。
不要对正式数据执行 destructive restore；先在新卷按 BACKUP_RESTORE.md 演练。

### Caddy 持久化与安全 reload

既有 Caddy 的源文件 `/opt/zetu/deployment/Caddyfile`，域名覆盖文件
`/opt/zetu/deployment/compose.domain.yml` 仅为 Caddy 增加外部 edge network。
Compose 语义对比已确认 API/Web 配置不变。Caddy 加网使用 `docker network connect`，
不重建/重启；未来 recreate 使用原两份 Compose 时同样保持 edge network。

配置只替换 XingChen virtual host，并在更改前备份。`Caddyfile` 是单文件 bind，
宿主原子替换后，运行容器的旧挂载 inode 可能仍含旧文本。因此 validate/reload
读取宿主候选文件，经 stdin 送入 Caddy；不能错误地 reload 旧挂载内容。

```sh
docker exec -i zetu-phase4c-caddy-1 caddy validate \
  --config /dev/stdin --adapter caddyfile < /opt/zetu/deployment/Caddyfile
docker exec -i zetu-phase4c-caddy-1 caddy reload \
  --config /dev/stdin --adapter caddyfile --address 127.0.0.1:2019 \
  < /opt/zetu/deployment/Caddyfile
```

admin 2019 仅在 Caddy 容器内 loopback，没有 host 映射。证书 `/data`、运行配置
`/config` 仍使用原持久卷。保留公网 80/443 的续期路径；标准 TLS client 校验
hostname/chain，绝不以 `curl -k` 验收。证书异常先检查 DNS、到期日、时间、存储
权限和脱敏 ACME 诊断，不删除证书私钥或改 Zetu 路由。

### 升级、回滚与 Gateway

新 image 在隔离数据上验证后，先停止自己的 Core、验证升级前备份，再同卷升级。
Schema rollback 必须用旧备份恢复到**新卷**并匹配旧 image，禁止原地降级数据库。
路由回滚只恢复已验证 Caddy 备份并 hot reload，不重启 Zetu。

Gateway 独立部署且不在 Core image 内。QQ 登录使用 SnowLuma 官方 WebUI/noVNC/
QQ GUI；XingChen Console **没有**原生二维码获取/刷新或程序化 QQ 登录能力。
Gateway/Model/OWNER 通过现有后台的受支持设置维护；外部集成仍默认关闭，只有
完成账号/端点/权限配置后才显式开启。不要恢复旧 DSH/qq-bridge 中间层。

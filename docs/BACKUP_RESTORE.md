# Linux 离线备份与灾难恢复

## 安全模型与格式

先停止自己的 XingChen Core，保留数据卷；不要 `down -v`。`DataPathResolver` 与工具在 `db/.xingchen-xingchen.db.lock` 使用相同 POSIX byte-range 锁。运行中的 Core 会令工具拒绝操作。其他绕过该锁的脚本不得同时写入。

format 1 只接受 schema 28，未知 future format/schema、旧格式均拒绝；没有 silent best-effort upgrade。恢复后使用与备份相同的经过验证的 image/revision，再另行规划升级。应用版本与 revision 是操作者传入的明确构建元数据，必须从实际 image label 核对，不是工具自行探测。

Bundle 是目录，不是任意 tar 解包：`manifest.json`、`checksums.sha256`、`data/db/xingchen.db`、`data/config`、`data/assets` 等。payload 与 manifest 全部 SHA-256，完整覆盖、大小、DB integrity/FK 和 schema 都校验。checksums 文件不自签名；外部可信校验/加密是运营责任。

备份先写同一父目录的 `.incomplete-*`，fsync/close/verify 完成后才 rename 为正式 destination。目的路径必须不存在，不覆盖历史。失败删除 staging，不产生 COMPLETE 目录。只在支持持久化 fsync/atomic rename 的本地 Linux 文件系统使用；不承诺 NFS/SMB。

## 标准步骤

以下路径、image 和账号全部是 PLACEHOLDER；在自己的部署上替换，不对其他服务运行。

```sh
docker compose stop xingchen-core
# 明确确认 exited，而非仅 unhealthy。不要停止其他项目。
docker compose ps -a
# /srv/xingchen/live 是已停止 Core 的普通 bind data directory；属于 UID 10001。
# 工具必须以数据所有者运行。backup parent 同样属于该用户且 mode 0700。
sudo -u '#10001' python3 tools/recovery.py backup \
  --data /srv/xingchen/live --destination /srv/xingchen/backups/PLACEHOLDER-UNIQUE \
  --application-version PLACEHOLDER --revision PLACEHOLDER
sudo -u '#10001' python3 tools/recovery.py verify \
  --bundle /srv/xingchen/backups/PLACEHOLDER-UNIQUE
docker compose start xingchen-core
curl --fail http://127.0.0.1:3200/health
```

工具不会打印 DB 内容、API key、密码、token。CLI 校验错误是固定、不含路径或值的安全诊断；不会把可能含敏感路径的 driver exception 暴露到终端。

## Restore 与 rollback

```sh
docker compose stop xingchen-core
sudo -u '#10001' python3 tools/recovery.py restore \
  --bundle /srv/xingchen/backups/PLACEHOLDER-UNIQUE \
  --destination /srv/xingchen/live
docker compose start xingchen-core
curl --fail http://127.0.0.1:3200/health
```

工具拒绝 root filesystem、symlink path、unsafe payload、world/group writable input、不同所有者及正在使用的数据。先 validate bundle，复制到相邻 `.restore-*`，保护权限并校验，再将已有 destination rename 为唯一 `.rollback-*`，最后 stage rename 为 destination。每次目录 rename 都原子，但这两次操作**不是一个 atomic exchange**：中间 crash 可能留下缺失的 live 路径和完整 rollback。此时不要启动 Core（否则可能初始化空目录）；先核对 staging/rollback，并在离线状态将完整 rollback rename 回 live。第二次 rename 异常时工具自动恢复 rollback；成功后仍保留 rollback，直到启动健康、登录和数据核验完成。不要自动删除 rollback。

目录本身是 Docker volume mount point 时不能被 rename；工具明确拒绝，不绕过 EBUSY。恢复到**新卷的普通子目录**，验证后在离线情况下改变自己的 Compose volume source/subpath：

1. 创建新 named volume；用同版本 Core image 在 `/data` 初始化卷目录的 UID/GID（不启动应用，例如 entrypoint `/bin/true`）。
2. 临时 helper 只挂新卷到 `/target`、备份只读挂到 `/backup`、工具只读挂到 `/tools`。使用 Python 官方固定版本 image，以 `10001:10001`、无网络、cap_drop ALL、no-new-privileges、read-only rootfs 运行 `python /tools/recovery.py restore --bundle /backup --destination /target/restored`。不要在 helper 中装 Gateway 或挂 Docker socket。
3. Core 的必要数据挂载使用新卷 `volume.subpath: restored` 到 `/data`。旧卷完整保留以供 rollback，不能覆盖旧 mount point。
4. 启动 Core、健康、管理员登录、Memory/Person/Prompt/access/pricing/assets/CLEAR 核验；失败则停 Core 并恢复旧卷引用。

## 原服务器损坏：新 Linux 主机恢复

1. 安装受支持的 Docker Engine + Compose v2，核对官方安装说明与主机架构；准备足够磁盘/RAM，默认后台只发布 loopback。
2. 从可信 registry 获得记录的精确 image/digest，或从记录 revision 使用正式 multi-stage Dockerfile 构建；不得依赖仍存活的原服务器。
3. 从离机加密备份恢复 bundle 到受保护目录，验证可信外部摘要后使用 `verify`；确认所需 schema/image 匹配。
4. 创建新的受保护 bind directory 或 named volume，执行上面的 offline restore。数据/备份 UID/GID 10001，目录 0700、文件 0600；不要 chmod 777。
5. 独立准备 `.env` 的 PLACEHOLDER bootstrap 管理员/安全设置，不能从旧 host shell history 找密码。恢复的 admin hash 和 CLEAR state 优先于 bootstrap；不要把已清除的 API key 自动重新填入。
6. 启动，要求 healthy、`/health` UP、原管理员登录成功；逐项核对身份、scope、active prompt、配置、资产内容和 secret configured/cleared **状态**，不打印值。
7. 配置新 HTTPS 入口前确认 publicBaseUrl，旧域名可能需要在维护阶段调整；没有正确证书时只用 SSH tunnel，不暴露裸后台。
8. Gateway 是独立产品，不包含在 backup/image；按其自身官方条款独立管理，明确连接地址/凭据后再启用集成。

升级前先测试 restore 到隔离实例；不要把 Flyway forward migration 当作可逆数据库升级。

## Named volume 的可执行恢复模板

下面是 Phase 4C-3 演练所用的普通子目录模式。变量是 PLACEHOLDER，不包含凭据；只操作自己的已停止 Core。Engine 必须支持 `volume-subpath`，Compose 使用对应 `volume.subpath`。不能把整个 `/target` mount point 当作 restore destination。

```sh
CORE_IMAGE='PLACEHOLDER-exact-image-or-digest'
PYTHON_IMAGE='PLACEHOLDER-reviewed-official-python-image-or-digest'
RECOVERY_VOLUME='PLACEHOLDER-new-volume'
BUNDLE_DIR='/PLACEHOLDER/private/verified-bundle'
TOOLS_DIR='/PLACEHOLDER/verified-source/tools'
docker volume create "$RECOVERY_VOLUME"
# 使用同版本 Core image 的 /data copy-up 初始化 UID/GID 10001；不启动 JVM。
docker run --rm --network none --read-only --cap-drop ALL \
  --security-opt no-new-privileges:true \
  --mount "type=volume,source=$RECOVERY_VOLUME,target=/data" \
  --entrypoint /bin/true "$CORE_IMAGE"
docker run --rm --network none --user 10001:10001 --read-only \
  --cap-drop ALL --security-opt no-new-privileges:true \
  --tmpfs /tmp:rw,noexec,nosuid,nodev,size=16m \
  --mount "type=volume,source=$RECOVERY_VOLUME,target=/target" \
  --mount "type=bind,source=$BUNDLE_DIR,target=/backup,readonly" \
  --mount "type=bind,source=$TOOLS_DIR,target=/tools,readonly" \
  "$PYTHON_IMAGE" python /tools/recovery.py restore \
  --bundle /backup --destination /target/live
# Core 后续挂载：type=volume,source=$RECOVERY_VOLUME,target=/data,volume-subpath=live
# 保持原有非 root/read-only/cap_drop/loopback/health/受保护 bootstrap 配置。
# 必须重新创建 Core，不能对仍挂着旧目录 inode 的容器只做 start。
```

现有部署若直接将 named volume 根挂到 `/data`，应恢复到新卷的 `live` 子目录再重建 Core；不能原地 rename volume mount point。已采用子目录模式时，也必须停止 Core，挂整个父卷给 helper，在 helper 的普通 `/target/live` 上恢复，随后移除旧容器并按相同子目录重建。备份目录及源文件须属于 UID 10001、目录 0700/文件 0600；工具镜像不包含业务凭据。

schema 27 的旧版本没有 format 1 工具。本轮升级兼容备份使用离线 SQLite Backup API 加私有文件快照和逐文件摘要；它是明确的旧版回滚证据，不是新增通用旧格式导入能力。不得修改 manifest 伪装为 schema 28。SIGKILL 演练证明的是进程非优雅退出后已提交 WAL 的恢复，不是断电/磁盘损坏或未提交事务的持久性保证。

# XingChen Phase 4C-3 最终证据审计

日期：2026-10-05。结论：**Phase 4C-3 COMPLETE**。这是隔离验收，不是生产部署；不进入 Phase 5。

起始 clean HEAD：`7203e45ca53dd7a790b48782eb34bbfdc0213f99`。实际候选源码：`6559c89e48fd229b11a9027226733c21fa848c0d`；该提交只修正浏览器 Persona 标题的 exact selector，没有修改产品代码。旧版源于 pristine Git archive：`b67cf7a59cf0792ebc02a1c2aae7c1207413d3e1`。最终封板提交只包含文档/证据，不把它冒充为已构建 image revision。

## A–J 验收

| Gate | 结果 | 实际证据与范围 |
| --- | --- | --- |
| A Current Docker | PASS / TESTED | 正式 multi-stage Dockerfile，两版均在 Linux 构建；没有复制本地 jar。当前空 named volume 首启、28 个 Flyway migrations、healthy、管理员登录和实际镜像层审计通过。 |
| B Application / Gateway | PASS / TESTED | 真实 API 创建 Memory、Persona/Simulation active versions、access、十进制 pricing、managed PNG；离线受控 fixture 创建稳定 Person/Relationship。真实 HTTPS 浏览器加载全部要求页面。独立 fake OneBot WS 入站→identity/conversation→SocialRuntime→真实 provider HTTP/SSE 解析→OneBot HTTP 出站：owner 私聊 1 次、member 群聊 1 次、模型请求恰好 2 次，用量持久化。 |
| C Real upgrade | PASS / TESTED | old schema 27→current schema 28，同一 upgrade volume；所选权威表摘要、Person/Relationship、Memory/active Prompt/access/pricing/admin hash/assets/CLEAR 保留。restart 与同卷 remove/recreate 摘要不变。补充演练明确持久化 URL，并提供不同 bootstrap URL/password，原值仍优先。 |
| C Rollback | PASS / TESTED | old 不支持 format 1；使用停止后的 SQLite Backup API + 私有文件快照/逐文件 SHA。恢复到新的 rollback volume 后启动 old，摘要与 old 一致。没有把升级后 DB 挂给 old，没有宣称 in-place downgrade。 |
| D SIGKILL | PASS / TESTED | 近期 Memory 由应用 API 提交；只读 SQLite snapshot holder 防止其 WAL 提前 checkpoint，不修改应用/PRAGMA。真正 docker kill：137、非 OOM；kill 前后 WAL 都为 57,712 bytes，integrity/FK/schema/摘要通过，重启 healthy 且原记忆可读。不是主机断电或未提交事务保证。 |
| E Disaster restore | PASS / TESTED | 正式 format 1/schema 28 backup+verify；staging 中改 Memory、active Prompt、access、pricing，删除 asset。应用确实读到修改后 Memory 和 asset404。离线正式 restore 保留 rollback，重建容器后 BACKUP=RESTORED，MUTATED 不同。另做显式 URL 的正式备份/破坏/恢复及不同 bootstrap 优先级验证。 |
| E Corrupt rejection | PASS / TESTED | 仅在备份副本 DB 追加字节；实际拒绝原因为 `missing or truncated payload`（大小校验先于 SHA），正常目标摘要未变。原 bundle 的 SHA/coverage/SQLite 校验通过；单元测试另覆盖 checksum corruption。 |
| F Fresh recovery | PASS / TESTED | 新 recovery network、新 named volume；helper 未挂原 current-data，仅使用正式 config-point backup、当前工具/image 和 bootstrap。恢复摘要一致，healthy、原管理员登录、Memory/Prompt/access/pricing/assets/显式 URL/CLEAR 通过，故意不同的 bootstrap 未覆盖恢复状态。 |
| G HTTPS / spoof | PASS / TESTED | 独立 Caddy internal TLS，loopback13320/13321；标准 TLS client 验证 chain/hostname。health200、匿名401、真实 CSRF、login/session/logout、Secure/HttpOnly/Strict、nosniff/DENY/no-referrer、308 redirect 通过。代理替换恶意 Forwarded；直接非可信 HTTP 路径也不能降级 Secure Cookie。 |
| H Security | PASS / TESTED | 实际 Core/Caddy inspect：10001:10001、read-only、cap_drop ALL、nnp、非 privileged、仅127.0.0.1 publication；Core `/tmp` noexec tmpfs、named `/data`、effective CapEff=0/NoNewPrivs=1、健康探针。backup/restore/helper 同样非 root，无网络/无 socket。实际 bundle UID10001、700/600、相对 payload 路径，无 host/container/volume 依赖。 |
| I Isolation / cleanup | PASS / TESTED | 所有 xingchen-c4c3 containers/networks/volumes/images=0，build cache0B，旧源码/临时备份/损坏副本/TLS/隧道清理。Zetu IDs/images/status/health/restarts/mounts/networks 全等；旧 QQ 保持 absent，归档4文件的基线 SHA 与 mtime 一致。 |
| J Independent review | PASS | 重新以审计视角交叉读取实际 source/tests/commits/运行字段/stdout/浏览器报告/清理对照，而非只信旧报告。详见 PHASE4C_FINAL_AUDIT。 |

## 构建与性能

- Docker Engine `29.7.2`，Compose `5.5.0`。Compose static config 成功；实际测试的端口和卷均有任务前缀/loopback 高端口，不创建生产 Compose 资源。
- current image ID：`sha256:b5dc5741a55e873f2e2eb6dda4caaf64900e512a65dc9b8115c34d0538144534`；144,358,944 bytes；构建779.60s。
- old image ID：`sha256:41eab5457437cb4cc302f304613c7157ef57153f3038740195a72175ef80e013`；144,357,519 bytes；构建563.28s。
- current jar：44,682,715 bytes，SHA256 `b719dbc70e3984753dc252de2e0844df1e9329975ed0bc8abde1ec3f4cc26310`。实际 runtime layers 不包含 `.git`、开发 toolchain/cache、node_modules、SSH key 或本地配置；image credential env keys=0。此审计不是通用秘密检测器的完整认证。
- 首次 current application startup83.641s、health-ready106.47s；old54.216s/69.02s；upgrade49.006s/100.39s；fresh recovery52.71s/102.71s。隔离 Core 有1 CPU/768MiB限制，不以此作为生产性能承诺。idle188.6MiB。
- 正式 disaster backup3.40s、restore3.52s；config backup2.82s、restore3.40s。计时包含 helper container 启动，不只是 Python 函数时间。
- 697 个约5秒资源样本：root17.260→**14.118**→17.255GiB；最低 available memory **1.746GiB**。14GiB warning 与12GiB hard stop 均0次；swap NONE，无观察到的 OOM。最终 Docker storage 回到11 images/5 containers（3 active）/4 volumes/cache0B；不删除既有两个停止的 Zetu load-test 容器。

## 回归与失败历史

| 验证 | 正式通过证据 |
| --- | --- |
| Backend | fresh serial 48 suites，341/341，0 failures/errors/skipped；不是 UP-TO-DATE 结果 |
| Frontend | 38/38；typecheck、bootJar PASS |
| Linux tools | 19/19：deployment4 + recovery/migration15 |
| Regular browser | 修正后完整17 passed +2 opt-in skipped |
| Real HTTPS Docker browser | 3/3，50.0s：既有 ContainerRuntime 两项 + all-required-pages 一项；非预期 console.error/pageerror/unhandledrejection/request failure=0 |

合计20个不同 browser case 有通过证据，**不是单次20/20 run**。原来跳过的两项已经在真实 HTTPS Docker 中补齐。页面包括 Login、Overview、People、Relationships、Memory、Persona、Simulation、Social Settings、Models、Stickers、Slang、Voice、Usage、Access、Security、Operations、Logs、Logout；不以页面加载证明尚未实现的 Voice 等能力。

失败没有删除或重标为成功：

1. 第一次 fresh backend 与浏览器并行时，出现 Windows SQLite fixture cleanup 失败及两项有界条件等待超时。三个受影响 class 单独通过；随后不并行运行浏览器的全量 fresh serial341/341通过。未证实根因，没有放宽 timeout 或修改产品来隐藏；仍作为非阻断测试稳定性风险记录。
2. 首次 browser 的 Persona 匹配同时命中 Persona/Personality；exact selector 修复后完整 regular suite 通过并提交6559c89。
3. runtime fixture 首次把价格字符串与 float 比较；只修复脚本，从已 seeded 状态继续，不重建镜像。
4. 首次 kill 时 WAL 已 checkpoint 为0字节，不能作为未 checkpoint 恢复证据。保留记录后，只重试受影响 crash gate，以只读 snapshot holder 得到实际57,712-byte WAL 和成功恢复。
5. 初次系统表中 URL 来自 bootstrap，不能冒充显式持久化覆盖证据；因此补充了不同 bootstrap 的真正 old→current、restore 和 fresh recovery 检查。

## 文档、秘密与证据组合

BACKUP_RESTORE 增加实际 named-volume 普通子目录/helper/recreate 模板；DEPLOYMENT 修正升级前备份、schema27兼容快照与禁止原地降级；HTTPS_CADDY 说明13320/13321、持久化 URL 启动快照、公开 CA 与精确 leaf SPKI。没有把恢复 mount point 的 EBUSY 绕过成直接覆盖 DB。

没有读取/使用生产 API key 或生产环境变量值。实际旧 archive 只读流式取 bounded config 到新的600私有临时文件，dry-run `imported=0`、`SECRET_SKIP=3`，目标 DB 字节和所选状态摘要不变；临时文件删除。真实凭据未进入 image、manifest/report、日志、浏览器诊断或本提交。备份本身可含持久化 SecretStore，因此仍须离机加密和可信外部摘要，SHA不等于签名。

汇总：[evidence/phase4c3-summary.json](evidence/phase4c3-summary.json)。服务器 `/opt/xingchen-c4c3-current` 只保留正式 source、验证脚本和安全报告；原始 runtime-summary 仍保留阶段边界 `PASS_PENDING_BROWSER_AUDIT_CLEANUP`，主清单7项，后段8项实际 PASS 由 completed tail stdout/运行字段补证。最终表组合 browser 与 cleanup，未篡改原始阶段状态来冒充完整验收。临时完整业务备份/CA keys/旧源码已删除。

真实 QQ/SnowLuma/DSH/DeepSeek、公网ACME、完整 legacy social semantic migration、断电持久性和CSP不因本阶段升格 TESTED。**无本阶段剩余 blocker；STOP，不进入 Phase 5。**

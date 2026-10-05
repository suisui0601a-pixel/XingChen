# Phase 4C-2 final evidence audit

结论：**Phase 4C-2 COMPLETE**。基线 b67cf7a；实现 e052720，安全修正 a0ff26f / ef8caf2 / c6b1dfc。不进入 Phase 4C-3。

## 证据矩阵

| 范围 | 状态 | 实际证据与边界 |
| --- | --- | --- |
| A SQLite consistent backup | TESTED | tools/recovery.py 使用 SQLite Online Backup API；受控离线、与 Java 相同 byte-range lease，运行中拒绝；WAL committed snapshot 单元测试通过。 |
| A atomic finalize / integrity | TESTED | 同父目录 staging、fsync、SHA-256 与 manifest 全覆盖校验后 rename；历史目的地不覆盖；DB/asset/manifest 损坏均 fail-closed。 |
| B restore roundtrip | TESTED | 正式 Docker Core 中创建 Person/Relationship/Memory/active Prompt/Access/Pricing/Sticker/config/admin 状态；停应用备份、修改 Memory/删除 asset、restore、移除并重建容器。BEFORE 与 RESTORED 状态/asset 摘要相同。 |
| B recovery safety | TESTED | 未知 format/schema、traversal、symlink、hardlink、错误 ownership、group/world writable、非法 manifest 类型和复制期间 payload 变化拒绝；已有目录保留 rollback。 |
| B restored HTTP state / CLEAR | TESTED | healthy 后认证 API 读取 Memory/current Prompt/asset 内容；非空假 bootstrap key 下 CLEAR 仍 configured=false；publicBaseUrl 与管理员凭据保持。 |
| C forensic inventory | TESTED | 只读流式清点 14,456 文件、27 DB、14 JSON 结构；SQLite 仅读取 schema，不解释个人内容。映射见 LEGACY_MIGRATION_MAPPING.md。 |
| C actual archive dry-run | TESTED | authoritative Bridge config 在独立私有临时目录解析，DRY_RUN_PASS：discovered=2/importable=2/imported=0；目标 DB 字节摘要未变；临时 config 已删除。 |
| C actual import fixture / idempotency | TESTED | Linux 测试实际事务插入稳定 Person/access；重复 source no-op、已存在冲突不覆盖、异常事务 rollback、reviewed fingerprint gate。真实旧归档没有 apply。 |
| C broader social semantic migration | FOUNDATION | 仅 ownerQQ 与明确 allow/deny stable IDs 可自动导入；Memory/Relationship/Prompt/roles/chat logs 未证实语义，跳过，不能宣称全量 legacy parity。 |
| D actual Caddy HTTPS proxy | TESTED | 专用 network，Core 127.0.0.1:13210、HTTPS 13220、HTTP redirect 13221；internal CA 验证 hostname/chain，/health、Console、API、login/logout、CSRF、Secure/HttpOnly/SameSite cookies、安全 headers 均通过。 |
| D proxy spoof resistance | TESTED | Core forward-headers-strategy=none；Caddy 固定 Host/XFH/XFP、移除外来 Forwarded；恶意转发头不改变有效 publicBaseUrl 或 TLS Cookie 假设。 |
| D HTTPS browser smoke | TESTED | 既有 ContainerRuntime 两个 case 实际通过：登录/Overview/Memory/Operations/reload/logout，以及 API auth/CSRF/404/redacted errors。Node 使用公开 CA；Chrome 使用精确 leaf SPKI pin，不是 blanket ignoreHTTPSErrors。未修改系统 trust store。 |
| D actual public ACME | DOCUMENTED | 没有申请公网证书，没有占用 80/443、改变 DNS/firewall；部署条件/持久化/renewal 已文档化。 |
| D CSP | FOUNDATION | 保持 deferred，未临时加入破坏 Console 的 CSP。internal staging 没有 HSTS；production 示例单独配置。 |
| E deployment / disaster recovery | DOCUMENTED | DEPLOYMENT、BACKUP_DATA_MODEL、BACKUP_RESTORE、LEGACY_MIGRATION、HTTPS_CADDY 覆盖权限、offline restore/new-host DR、rollback、review、Gateway 分离。示例均 placeholders。 |
| F regressions | TESTED | backend 341/341（0 failed/skip）、frontend 38/38、typecheck、bootJar；Linux recovery/migration/deployment 19/19；正式 browser suite 17 passed + 2 opt-in skipped，再在真实 HTTPS Docker 上补齐 2/2。合计 19 个 case 有通过证据，非单次 19/19 run。 |
| F isolation / cleanup | TESTED | 三个 Zetu ID/status/health/restarts 前后相同；API/web healthy、Caddy 无 healthcheck，三者 restart=0。所有 xingchen-c4c2 containers/networks/volumes/test images 为零；临时数据、legacy config、CA、隧道已清理。 |
| F old archive | TESTED | 归档文件未写入；主 tar 与 baseline/manifest/SHA256SUMS 四个基线摘要前后相同。没有原地解压或 migration marker。 |

## 构建与原始证据

- Core 使用正式 multi-stage Dockerfile 从 e052720 源码构建，没有预构建 jar 替代。Core image ID：`sha256:f5d8a0e925f635663af5f329c4817e503c2f2047e849dbce29ebe3b959610eb8`；导出 manifest：`sha256:ea181d313f2496929ec880df9853f5ba0f906779fa8cbfb5a9487c729fef5992`。
- 后续 Python recovery/import 工具在独立 helper 中测试，版本 c6b1dfc；没有把旧 Core image revision 冒充为最终 Git revision。Java/runtime artifact 在这些修正中未改变。
- Caddy 高端口 hardened image ID：`sha256:9fe294a17c06932364618ac7a2ff54f5a8de41e6fab539ce374373df836b773a`。上游二进制 file capability 在 cap_drop ALL 下导致 EPERM；构建期去除不需要的 capability 后重新验证，未增加 privileged/capability。
- 无敏感值的资源与恢复摘要：`docs/evidence/phase4c2-summary.json`。服务器私有 source/reports 留存 build-summary、resource JSONL、runtime-summary、失败尝试摘要和 final-summary；临时完整业务备份已随 staging 清除。

## 独立审计复核

实现后重新以审计角色阅读 source/tests/config/docs/reports，而非把测试绿灯作为唯一依据。

1. 没有 cp live DB；显式停 Core、同 inode byte-range lease 和 SQLite API，避免 WAL/文件组件跨时间点。绕过 lease 的同用户脚本必须禁止并行写入。
2. 路径 normalize 后拒绝 filesystem root；manifest 类型严格验证；复制结果与**校验时捕获**的摘要对比，不能复制后重算源摘要掩盖变化。CLI 不打印 driver exception/秘密内容。
3. Bundle 包含 secret 持久化文件，**并非自动加密**；700/600 与私有 ownership 不能替代离机加密。SHA-256 完整性不等于签名真实性。文档明确运营者须可信外部摘要/加密。
4. Restore 两次 directory rename **不是 atomic exchange**；crash gap 中不得启动空 Core，完整 rollback 保留，文档给出离线回退。不能直接替换 Docker mount point；新卷普通 subdirectory 切换是文档化替代，不伪称本轮实际测试 named-volume DR。
5. Importer 不按 nickname 合并，不猜 scope/roles/prompt，默认不开放 empty allowlist；既有冲突 skip，事务+ledger 幂等。未知字段按 field/section 计数，不伪称逐消息计数。
6. 初始失败包括 Caddy file capability 及手写 fixture 的错误 Memory enum、非 UUID ID、非 managed-library asset 路径/MIME。修正夹具后重新完成真实 roundtrip/API，没有放宽产品校验或修改产品以接受非法资产。失败摘要保留，不将失败 run 标为成功。
7. HTTPS 浏览器信任采用精确 SPKI pin，先前 HTTP client 已真实 CA/hostname 验证；不宣称公网证书或 OS trust-store 部署验证。应用忽略 forwarded IP 的限流聚合边界也已说明。
8. Core 10001:10001、read-only rootfs、noexec tmpfs、cap_drop ALL、no-new-privileges、非 privileged、loopback publication 均保留。没有 SnowLuma/QQ/proprietary binary 或生产 secret 注入。
9. Feature Matrix 原有 128 行、79 TESTED/31 FOUNDATION/18 PLANNED 的范围未扩大。成功的有限 migration 不提升未实现 legacy social parity。

## 资源和清场

Root free（GiB）：**17.60 → 14.72 → 17.26**。字节：18,893,520,896 → 15,804,817,408 → 18,533,744,640。5 秒采样；没有触发 14 GiB warning / 12 GiB hard stop。

最低 available RAM 1,838,559,232 bytes（1.71 GiB）；无 swap 创建、无观测 OOM、无 production restart。专用 Buildx worker/cache 精准回收，没有 broad prune。最终 staging inventory 全空，源和无敏感值报告保留。

**A PASS + B PASS + C PASS（明确 limited scope）+ D PASS + E PASS + F PASS = COMPLETE。Remaining blockers: NONE。**

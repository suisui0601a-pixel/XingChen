# Agent deployment manual / 给 AI Agent 的双语部署说明

This document is intentionally procedural. It is written for Codex, Claude, ChatGPT, Grok, local agents, and human operators who need a bounded deployment sequence without relying on private chat history.

本文刻意采用“状态机式”写法，供 Codex、Claude、ChatGPT、Grok、本地 Agent 与人工操作者使用，使部署不依赖任何私人聊天上下文。

> **Credential boundary / 凭据边界**
>
> Never ask the operator to paste production passwords, API keys, OneBot tokens, QQ identifiers that are treated as private, or private prompt content into chat. Pause and ask the operator to enter secrets into the local Console/terminal.
>
> 不要要求操作者把生产密码、API Key、OneBot Token、需要保密的 QQ 标识或私人 Prompt 正文发到聊天里。遇到凭据步骤必须暂停，由操作者在本机 Console/终端输入。

## 0. Deployment modes / 部署模式

Choose one mode before changing production.

部署前先选择一种明确模式。

### Mode A — Native social runtime / 模式 A：原生社交 Runtime

Ordinary QQ social turns use:

```text
Social=true
OneBot=true
Model=true
DSH=false
```

DSH is not required.

普通 QQ 社交回合使用 Social + OneBot + Model，DSH 不作为前置依赖。

### Mode B — Native social runtime + DSH integration / 模式 B：原生社交 + DSH 集成

Use this only when the deployment needs the documented DSH interaction integration.

```text
Social=true
OneBot=true
Model=true
DSH=true
```

DSH is additive. It does not replace OneBot for QQ transport and does not justify creating a second OneBot consumer.

仅在确实需要 DSH interaction integration 时开启。DSH 是附加集成，不替代 OneBot，也不能因此创建第二个 OneBot 消费者。

Before Mode B, read:

```text
docs/research/DSH_RC2_CONTRACT.md
docs/LEGACY_MIGRATION.md
```

Do not infer wire contracts from a different DSH/Bridge version.

不要拿不同版本的 DSH/Bridge 文档猜当前 wire contract。

## 1. PRECHECK / 预检查

Agent must record only non-secret facts:

```text
OS / architecture
Docker Engine version
Compose v2 availability
available disk / inode space
current image reference (if upgrading)
current Core health
current database schema
backup path ownership/mode
intended mode A or B
```

Agent must stop if:

- the target repository/revision is ambiguous;
- disk space is critically low;
- an upgrade has no rollback image;
- the current database cannot be backed up;
- a second OneBot consumer would be created.

Agent 只记录非敏感事实。仓库/revision 不明确、磁盘不足、没有回滚镜像、数据库无法备份、或将产生双 OneBot consumer 时必须停止。

## 2. BUILD / 构建

Use an explicit revision/tag. Do not deploy `latest` as an unaudited moving target.

使用明确 revision/tag，不要把 `latest` 当作可审计生产版本。

The repository Dockerfile performs the Java/frontend multi-stage build. Normal CI/tests must not require production provider credentials.

仓库 Dockerfile 负责 Java + 前端多阶段构建；普通 CI/测试不应依赖生产模型凭据。

Record:

```text
image ID/digest
application version
git revision
build result
test result
```

Do not bake passwords or tokens into image layers.

不要把密码/Token 烧进镜像层。

## 3. FIRST_ADMIN / 首个管理员

There is no public default password.

不存在公开默认管理员密码。

Choose exactly one supported creation path:

### Path 1 — Local browser initialization / 路径 1：本机浏览器初始化

Follow `docs/ADMIN_INITIALIZATION.md`.

This is preferred when a human operator is available and should own the first credential.

有人值守时优先使用 `docs/ADMIN_INITIALIZATION.md` 的受限本机初始化流程。

### Path 2 — Empty-database bootstrap / 路径 2：空库 bootstrap

On an empty credential table only:

```text
XINGCHEN_CONSOLE_ADMIN_USER
XINGCHEN_CONSOLE_ADMIN_PASSWORD
```

The operator must supply these locally. The agent must never invent them.

仅空凭据表可使用。账号/密码由操作者本地提供，Agent 不得自行生成并悄悄保存生产密码。

After creation, the password is represented only by a BCrypt hash. See `docs/ADMIN_CREDENTIALS.md`.

创建后只有 BCrypt 哈希，详见 `docs/ADMIN_CREDENTIALS.md`。

## 4. DATA / 数据目录

Canonical container root:

```text
/data
```

Important paths include:

```text
/data/db
/data/config or configured SecretStore directory
/data/assets/stickers
```

Use a persistent local Docker volume. Do not use `down -v` as an upgrade shortcut.

使用持久化本地 Docker volume，不要用 `down -v` 当升级手段。

Keep UID/GID and modes compatible with the non-root runtime (production owner UID 10001 in the documented deployment). Do not chmod 777.

保持 non-root 运行时所需 UID/GID 与权限；不要 chmod 777。

## 5. BACKUP_GATE / 备份门槛

Before production writes, upgrades, bulk imports, or migrations:

1. stop only the XingChen Core if the recovery procedure requires it;
2. create a unique backup;
3. verify the bundle;
4. record schema and verification result;
5. restart Core and confirm health before continuing, unless the next step is an intentional offline cutover.

任何生产写入、升级、批量导入、迁移前，都必须先备份并 verify。

### Docker named-volume parent trap / Docker 卷父目录陷阱

`tools/recovery.py` validates parent paths while running as the data owner. Docker's host-side volume parent may not be traversable by UID 10001.

Do **not** weaken Docker storage permissions.

Use a temporary reviewed bind-mount/helper path that exposes the data and backup parent through paths the recovery UID can traverse.

`recovery.py` 会以数据所有者身份检查父路径。宿主机 Docker 卷父目录可能无法被 UID 10001 遍历。不要因此放宽 Docker 存储权限；应使用临时、经过审核的 bind mount/helper 路径。

Backup failure = STOP.

备份失败 = 停止。

## 6. MODEL / 模型

Configure the provider through supported configuration/SecretStore mechanisms.

通过正式配置/SecretStore 设置模型 Provider。

Never print the API key. Connection diagnostics should return status only.

不得打印 API Key；连接诊断只返回状态。

Normal tests should continue to use fixtures unless an explicit live-test procedure is being run with a dedicated test credential.

普通测试默认使用 fixture；只有显式 live test 才使用专用测试凭据。

## 7. ONEBOT / QQ Gateway

The gateway is independently deployed and owns QQ login.

Gateway 独立部署，并负责 QQ 登录。

Core needs reviewed HTTP/WS endpoints. In Docker, do not use Core's `127.0.0.1` to reach another container.

Core 需要经过审核的 HTTP/WS 地址；跨容器不要使用 Core 自己的 `127.0.0.1`。

HTTP and WS credentials are independent:

```text
HTTP action credential
WS subscription credential
```

Do not echo them in status pages or logs.

HTTP action 与 WS subscription 使用独立 Token，状态页/日志不得回显。

A production runtime should have one intended Core OneBot consumer. Do not create a second diagnostic subscription merely to “check status.”

生产环境应只有一个预期的 Core OneBot consumer。不要为了查状态额外建立第二条生产 WS 订阅。

## 8. ACCESS / 访问控制

XingChen is default-deny.

XingChen 默认拒绝。

Private allow:

```text
scope=PRIVATE
stable_id=user/platform ID
```

Group allow:

```text
scope=GROUP
stable_id=group/conversation ID
```

Do not confuse a group member's user ID with the group ID.

不要把群成员 QQ 号当作群号。

Before blaming wake logic, use the effective-policy preview. Expected allowed group result:

```text
GROUP_ALLOW
```

Access-rule writes replace the full rule set. When a UI/client changes rules, it must preserve the current full list and revision. Clearing all rules should require explicit confirmation.

访问规则写操作是整表替换；客户端必须保存完整规则集与 revision。清空全部规则应有额外确认。

## 9. DSH_OPTIONAL / 可选 DSH

Only for Mode B.

仅模式 B 使用。

Use the pinned/verified contract in `docs/research/DSH_RC2_CONTRACT.md`. Do not assume another DSH release has identical RPC envelopes, mux behavior, approval/question outcomes, or auth semantics.

必须以已核验的 pinned contract 为准，不能假设不同 DSH 版本协议一致。

Keep DSH credentials separate from Console, model-provider and OneBot credentials.

DSH 凭据与 Console、模型、OneBot 凭据分开管理。

If DSH is not configured, leave it disabled. Ordinary SocialRuntime must not be blocked merely because DSH is false.

没配置 DSH 就保持关闭；DSH=false 不应阻塞普通 SocialRuntime。

## 10. START / 启动

Start only the intended Core instance.

只启动一个预期 Core 实例。

Verify:

```text
Core health = UP
restart count stable
database schema expected
Model integration expected
OneBot integration expected
SocialRuntime state expected
DSH state matches selected mode
```

Do not “fix” a failed start by deleting the database or data volume.

启动失败时不要删库/删卷“重来”。

## 11. QQ_SMOKE / QQ 实际验收

Minimal smoke:

1. owner/private message;
2. allowed group `@bot`;
3. reply-to-bot in allowed group;
4. verify an unallowed identity/group is denied by policy preview;
5. verify no duplicate reply.

最小实测：Owner 私聊、允许群 @、允许群回复、未允许对象的策略拒绝、无重复回复。

Random wake is probabilistic. Do not set it to 100% merely to make acceptance deterministic.

随机唤醒本来就是概率行为，不要为验收临时改成 100%。

## 12. STICKERS_OPTIONAL / 可选表情包

For a large curated pack, use `docs/STICKER_BULK_IMPORT.md`.

大规模整理包使用 `docs/STICKER_BULK_IMPORT.md`。

Do not direct-copy managed library files or write the Sticker table by hand.

不要手工写 library 或 Sticker 表。

## 13. ROLLBACK / 回滚

Rollback requires:

```text
previous image
verified pre-change backup
documented restore path
```

If the schema migrated forward, do not attach the migrated live DB directly to an older image. Restore the matching backup into a separate/new data location, then start the matching old image.

如果数据库已经向前迁移，不能让旧镜像直接连迁移后的 live DB。应恢复对应备份，再启动匹配的旧镜像。

## 14. FINAL_REPORT / 最终报告

Every agent should finish with a concise, secret-free report:

```text
Mode:
Revision:
Image:
Backup: PASS/FAIL
Backup verify: PASS/FAIL
Schema:
Core health:
Restart count:
Model:
OneBot HTTP:
OneBot WS:
Unique consumer:
Access rules:
Owner private smoke:
Allowed-group mention:
Allowed-group reply:
DSH:
Sticker import:
Secrets exposed: NO
Rollback: NOT REQUIRED / PERFORMED
Status: COMPLETE / PARTIAL / BLOCKED
```

Do not claim COMPLETE when a required gate remains untested.

任何必需门槛未验收时，不得写 COMPLETE。

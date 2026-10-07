# FAQ / 常见问题

This FAQ focuses on deployment and production-operation mistakes that have been observed during XingChen integration work. It contains no production secrets, account IDs, private prompts, or infrastructure endpoints.

本文集中记录 XingChen 部署与生产接入中最容易踩的坑，不包含生产凭据、账号 ID、私人 Prompt 或实际基础设施地址。

## Console / 管理后台

### What is the default administrator password? / 默认管理员密码是什么？

There is none. See `ADMIN_CREDENTIALS.md`.

没有默认密码。详见 `ADMIN_CREDENTIALS.md`。

### I forgot the password. Can I read it from SQLite? / 忘记密码能从 SQLite 找出来吗？

No. The database stores a BCrypt hash, not the original password. Do not patch the hash or delete the administrator row on a live database.

不能。数据库保存的是 BCrypt 哈希，不是原密码。不要在生产库里直接改哈希或删除管理员行。

### Why did my Console session disappear after a password change or Core restart? / 为什么改密码或重启后登录失效？

Password changes invalidate the authenticated generation. Embedded HTTP sessions also do not survive a Core restart. Re-authenticate normally.

密码修改会使旧认证 generation 失效；Core 重启后嵌入式 HTTP Session 也不会继续存在。重新正常登录即可。

## Access control / 访问控制

### Private chat works, but group users cannot wake the bot. / 私聊正常，但群里叫不出机器人

Check access control before wake rules.

Group access matches:

```text
scope = GROUP
platform = QQ
stable_id = QQ group ID
```

It does **not** match the sender's QQ user ID. A user's private allow rule does not authorize every group they join.

先查访问控制，再查唤醒规则。

群聊允许规则匹配：

```text
scope = GROUP
platform = QQ
stable_id = QQ 群号
```

不是群成员的 QQ 用户号。某人的私聊白名单也不会自动授权他所在的所有群。

Use the Console policy preview with the real normalized event values. A healthy allowed group should return `GROUP_ALLOW`.

可用 Console 的“有效策略预览”按真实事件的 platform / actor / scope / conversation ID 检查。允许群应返回 `GROUP_ALLOW`。

### What does DEFAULT_DENY mean? / DEFAULT_DENY 是故障吗？

Not by itself. XingChen is fail-closed. If no owner recovery identity, explicit allow, or group allow matches, the event is rejected with `DEFAULT_DENY`.

不一定。XingChen 默认 fail-closed。没有命中 Owner、明确允许或群允许规则时，事件会以 `DEFAULT_DENY` 被拒绝。

### Why does saving access rules matter so much? / 为什么保存白名单需要额外保护？

The access-rule write API replaces the full rule set. Clients must therefore load the current revision and submit the complete intended list. Clearing all rules should require a conspicuous confirmation because it returns ordinary traffic to Default Deny.

访问规则写接口采用“整表替换”。客户端必须先读取当前 revision，并提交完整目标规则集。清空全部规则会让普通流量回到 Default Deny，因此前端应做醒目的二次确认。

## OneBot / QQ

### Why does `127.0.0.1` fail between containers? / 为什么容器间不能用 127.0.0.1？

Inside Core, `127.0.0.1` means the Core container itself. Use the gateway service DNS name or reviewed network alias when the OneBot gateway is another container.

Core 容器里的 `127.0.0.1` 只指 Core 自己。OneBot Gateway 在另一个容器时，应使用服务 DNS 名或经过审核的网络别名。

### Why are HTTP and WebSocket tokens separate? / 为什么 HTTP 与 WS Token 分开？

HTTP actions and the forward WebSocket subscription are independent transports. XingChen stores and applies their credentials independently. Clearing or replacing one must not silently change the other.

HTTP action 与正向 WebSocket 订阅是两套独立传输。XingChen 分别保存、应用凭据；修改一个不应隐式影响另一个。

### OneBot is connected but the bot still does not reply. / OneBot 显示已连接但仍不回复

Check, in order:

1. Access decision (`OWNER`, `EXPLICIT_ALLOW`, `GROUP_ALLOW`, or deny reason).
2. Event normalization (`PRIVATE` vs `GROUP`, actor ID, conversation/group ID).
3. Wake decision.
4. Model integration status.
5. Outbound ledger / send result.

Do not start by changing random-wake probability or credentials.

依次检查：访问决策 → 事件归一化 → 唤醒判断 → 模型集成 → 出站发送。不要一上来就改随机唤醒概率或 Token。

## DSH

### Is DSH required for normal QQ social chat? / 普通 QQ 社交聊天必须依赖 DSH 吗？

No. Current SocialRuntime gating is:

```text
Social=true
OneBot=true
Model=true
```

DSH is an independent/additive integration.

不需要。普通 SocialRuntime 的门控是 Social + OneBot + Model。DSH 是独立/附加集成。

### Can I enable both native SocialRuntime and DSH? / 可以同时启用原生 SocialRuntime 和 DSH 吗？

Only when you understand the event ownership and the documented DSH contract. Do not create duplicate OneBot consumers or duplicate outbound paths. Follow `AGENT_DEPLOYMENT_BILINGUAL.md` and `research/DSH_RC2_CONTRACT.md`.

可以，但必须明确事件归属与 DSH 契约，不能产生双 OneBot 消费者或重复出站链路。

## Backup and restore / 备份恢复

### recovery.py fails on a Docker volume host path with PermissionError. / recovery.py 直接读 Docker 卷宿主机路径失败

Do not chmod/chown the whole Docker storage tree.

The recovery tool intentionally runs as the data owner (UID 10001) and validates every parent path. Docker's host-side volume parent directories may not be traversable by that UID even when the data itself is correctly owned.

Use a temporary, reviewed bind mount/helper path that exposes the live data and backup parent through paths UID 10001 can legally traverse. Keep the real volume permissions unchanged.

不要给 Docker 存储树整体 chmod/chown。

恢复工具会以数据所有者 UID 10001 运行，并检查所有父路径。Docker 卷的宿主机父目录可能阻止该 UID 遍历，即使卷内数据权限完全正确。

应通过临时、经过审核的 bind mount/helper 路径，把 live data 与备份目录暴露为 UID 10001 可遍历的路径，而不是修改 Docker 卷权限。

### Why is there no sqlite3 CLI in the Core image? / 为什么 Core 镜像没有 sqlite3 命令？

The recovery utility uses Python's standard-library `sqlite3` module. The absence of the sqlite3 CLI is not by itself a backup failure.

恢复工具使用 Python 标准库 `sqlite3`，Core 镜像没有 sqlite3 CLI 本身不是备份失败原因。

### Can I copy `xingchen.db` while Core is running? / 可以在线直接复制数据库文件吗？

Do not use a raw file copy as a production backup. SQLite WAL state and application writes require the documented recovery workflow.

不要把运行中数据库文件的裸复制当成正式备份。请使用项目规定的一致性备份流程。

## Stickers / 表情包

### Can I copy image files directly into `/data/assets/stickers/library`? / 能直接把图片复制进 library 吗？

No. Use the supported Sticker service/API. The runtime manages SHA-256 deduplication, metadata, validation, and the managed library path.

不能。应通过正式 Sticker Service/API，让运行时完成 SHA-256 去重、元数据、图片校验和受管目录写入。

### Why not use one giant scan for hundreds of categorized files? / 几百张分类表情为什么不直接一次 scan？

`scan()` is bounded and does not carry a manifest's classification tags. For large curated packs, use the authenticated upload endpoint (or another supported service path) and pass tags explicitly. See `STICKER_BULK_IMPORT.md`.

`scan()` 有文件数/字节数边界，而且无法携带 manifest 分类标签。大规模整理包应使用受认证上传入口并显式传 tags。

## Security / 安全

### Should an AI agent receive production passwords or tokens in chat? / 可以把生产账号密码发给 AI Agent 吗？

No. Agents should stop at the credential boundary and instruct the operator to type credentials into a local terminal or browser. Logs and reports should record only configured/not-configured status.

不应。Agent 遇到凭据边界时应暂停，由操作者在本机终端/浏览器输入。报告只记录“已配置/未配置”，不记录凭据值。

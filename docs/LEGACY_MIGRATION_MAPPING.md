# Legacy forensic mapping

本轮先对管理员指定的 decommission 归档只读流式清点，再实现 importer。归档包括 Bridge 0.1.7 的两个 config 副本、mode、DSH profile bundles、sessions/storages/attachments、SnowLuma/QQ DB、源码及依赖。清点共 14,456 普通文件、27 个 DB、14 个指定 JSON 结构。报告只保存字段形状、大小、包版本和摘要，不保存 JSON 值。源摘要验证记录见本轮 evidence；未在源目录创建 marker。

当前 importer 只支持经核验的 **qq-bridge 0.1.7 config.json**，不是“任意旧 DB 通用迁移器”。选择归档内实际运行的 bridge-volume/config.json；另一个副本只做对照，不能两份混合后猜测优先级。

| Legacy field / component | 分类 | target / 处理 |
| --- | --- | --- |
| ownerQQ（数字 stable ID） | EXACT | QQ Person OWNER；已有 Person 不升级/覆盖其显式角色 |
| allow.private / deny.private | EXACT | console_access_rules PRIVATE + QQ stable ID |
| allow.groups / deny.groups | EXACT | console_access_rules GROUP + QQ stable ID |
| allow.private/deny.private 中未建 Person 的 ID | DERIVABLE | 仅创建 QQ stable Person，不使用 nickname，不创建未经证实 membership |
| allow 与 deny 对同一 ID 冲突 | AMBIGUOUS | SKIPPED_AMBIGUOUS；不猜 precedence |
| allowAllWhenEmpty | UNSUPPORTED | 不开启 open，保持新系统 fail-closed；报告 openAccessSkipped |
| dsh provider/model/auth、SnowLuma endpoints/token、consoleToken | SECRET_SKIP | 整节排除，旧凭据不复制；新连接由用户重新配置 |
| role.maxInjectChars、agentPreset、sessionCwd、workspaceTitle、mode | UNSUPPORTED | DSH-specific runtime semantics 不转换 |
| pricing.models | AMBIGUOUS | 未证明 provider/单位/币种与新 exact decimal 定价相同，不导入 |
| social/socialV2/slang/security/voice/sticker settings | UNSUPPORTED | 开关/工具授权不等于新 Core social model，不导入 |
| 其他未知 config field | UNSUPPORTED | 计数报告，不写目标 |
| DSH sessions/storages/attachments / logs | AMBIGUOUS | 未建立正式稳定 identity/scope/provenance schema；不变成长时 Memory |
| persona/simulation/system content | AMBIGUOUS | 未证明旧字段语义及 active/version 链；当前不导入，不覆盖 active/history |
| nickname / group card / address terms | AMBIGUOUS | 不能仅按同名合并 Person，不能提升为 global Relationship |
| SnowLuma message/media/reaction/identity DB | UNSUPPORTED | 不直接复制到 Core；业务表结构与权限模型未证实映射 |
| QQ login/message/cache DB 与 cookie/session credential | SECRET_SKIP | 不读取/迁移凭据；不启动 QQ |
| npm/pnpm/cache/source binaries | UNSUPPORTED | 可重建/非应用迁移数据 |

记录 provenance：migration ledger 保存 source type、SHA-256 fingerprint、UTC、计数；access updated_by 标记 legacy migration/source type/fingerprint。Identity 仅用 `(QQ, platformUserId)`。不导入 Relationship/Memory/Prompt，因此不会提升 scope 或覆盖 active version。未来若要支持这些部分，必须补齐实际 schema 证据、逐记录来源和范围映射后再开发，不能把当前 LIMITED importer 宣称为全量语义迁移。

补充只读 schema 核验：messages.db 的 messages 包含 message_hash/is_group/session_id/sequence/event_name/private_direction/data；snowluma_identity.db 的 users 包含 uid/uin/nickname/source，groups 包含 group_id/group_name/active，group_members 包含 group_id/uid/uin/card/role/active。这里不读取个人内容；仅有列名不足以证明角色枚举、缺失 UIN 回退、归档 WAL 一致性和 Core scope/provenance，故本轮 DB 直接导入仍 UNSUPPORTED。DSH storage 是 version/record.identity/record.rows 下的 ver/seq/val 运行事件快照，不等价于关系或长时 Memory。报告中的 skipped 计数按 config 顶层字段/section，不伪称逐消息统计。

# 闲置主动冒泡

这是没有新消息时，由 Core 的计时器发起的一次可选文字问候。它与 `ordinaryMessageProbability`（普通群消息入站后的随机参与）相互独立。不增加 OneBot 消费者，不修改 Persona、Simulation 或访问控制，不需要外部记忆服务。

## 配置

在 Console「社交设置」设置全局默认，或从会话详情进入会话级覆盖。建议先保持全局关闭，仅对一个已授权会话启用。所有保存沿用管理员认证、CSRF、revision 冲突检查和持久化审计；表单不会把概率误存为百分数。

| API 字段 | 默认 | 范围 / 含义 |
|---|---|---|
| `proactivePrivateEnabled` | false | 私聊独立开关 |
| `proactiveGroupEnabled` | false | 群聊独立开关 |
| `proactiveIdleMinutes` | 30 | 5–1440 分钟，距离最近真实入站或普通回复的闲置时间 |
| `proactiveIntervalMinutes` | 30 | 5–1440 分钟，已检查后至少隔多久再抽样 |
| `proactiveCooldownMinutes` | 180 | 30–10080 分钟，准入模型尝试后的冷却 |
| `proactiveProbability` | 0.1 | 0–1，界面显示 0–100%；不是每分钟抽样 |
| `proactiveMaxPerDay` | 2 | 1–6，每会话每天最多准入次数，按配置时区 |
| `proactiveQuietStartHour` | 23 | 0–23，安静时段含开始时刻 |
| `proactiveQuietEndHour` | 8 | 0–23，不含结束时刻；与开始相同时全天允许 |
| `proactiveZoneId` | Asia/Shanghai | Java 支持的时区 ID |

会话覆盖传 `null` 表示继承。已有配置缺少这些字段时自动合并默认值，不会自动启用。普通消息概率、显式唤醒和 Owner 原有规则不变。

## 运行路径

每分钟计时器扫描最多 100 个候选会话，按游标循环；仅处理最近 24 小时有真实、已完成入站消息的 QQ 会话。会话繁忙、WAIT、休眠、CLOSED_AGENT、重置处理中、没有重置后新消息或网关断线时跳过。空白、不可见字符、自身消息和系统通知不能独立建立冒泡资格。安静时段检查与权限检查会在出站前再次执行。

群聊必须通过群规则本身，不能借用最后发言人的 Owner 恢复权限；私聊仍按现有收件人规则。开启开关不创建任何 ALLOW 规则。

通过原有会话串行队列执行，不启动第二个生产 QQ / OneBot 消费者。计时意图明确标为 `IDLE_TIMER`，不是新用户消息，不插入 `messages`、不移动读取游标、不更新成员最后活动时间。

生成使用现有 Provider、Prompt 装配和作用域内记忆。群聊使用会话级记忆身份，不向模型借出最后发言人的 PRIVATE、PERSON_GLOBAL 或 OWNER_GLOBAL 记忆，也不使用私人称呼。短期上下文受重置 generation 边界限制；只将已确认发送的消息段作为机器人历史。

首版一次主动尝试只允许一条最终文字；模型可选择 NO_REPLY。主动回合不开放 Agent 工具，不能写记忆、改称呼、进入 WAIT、戳人或通过工具另行发送。原有正常聊天工具能力保留。多段模型输出仅采用第一条非空文字，并沿用出站长度限制。

## 预算、取消和恢复

- 全实例最多每小时准入 4 个主动模型回合，最多 4 个执行中任务；不使用无限队列。
- `proactive_attempts` 中的原子 INSERT/SELECT 同时检查抽样间隔、冷却、日限额和全局小时限额。并发扫描或重启不会通过内存 Set 绕过预算。
- 概率未命中记录 `SKIPPED_RANDOM`，不调用模型、不消耗日模型额度，但保持抽样间隔。
- 已准入后，NO_REPLY、取消、失败和 UNKNOWN 均消耗额度与冷却，防止错误或不回复导致高频重试。
- 新入站在进入队列时取消对应的闲置意图；Reset、停止 Runtime 或会话 generation 变化也阻止旧回调发送。已经进入传输的消息无法撤回。
- 最终文字通过原 `OutboundExecutionService` 和共享 `ReplyDispatcher`，保留速率保护及持久化发送键。限流明确记录 `RATE_LIMITED`，UNKNOWN 不盲目重试。
- 重启时不重放定时任务。未完成准入标记为 INTERRUPTED；若已有持久化出站证据，则保留 SENT / UNKNOWN。准入后崩溃可能丢失本次回复，这是有意选择的 at-most-once 安全边界，不是端到端 exactly-once。
- 需要保持单个 Core 实例消费生产 QQ。此功能不是多副本生产协调器。
- 每次扫描最多回收 500 条超过 8 天、结果明确的本功能日志（超过最大 7 天冷却）。UNKNOWN、INTERRUPTED 和执行中记录保留；不触碰既有聊天、记忆或 Ledger。

正常聊天可读取本会话最后一条确认发送的冒泡文字及时间，作为注明来源的历史数据。该关联在重启后仍可用，并服从会话重置边界；不会将其当成新用户消息或系统指令。

## 诊断与隐私

`GET /api/social-settings/proactive-status` 使用既有管理认证且禁止缓存，只返回计时器状态、执行中数量、结果统计和最近 20 条脱敏元数据。Console 每 30 秒有限同步，页面离开后取消请求和计时器；状态未知不会显示为成功。

结果区分 SENT、NO_REPLY、SKIPPED_RANDOM、CANCELLED、FAILED、UNKNOWN、INTERRUPTED 和 RATE_LIMITED。计时器运行不等于 QQ / Provider 全链路健康。诊断不返回聊天正文、Prompt、输出 JSON 或密钥。回复正文仅保存在受保护的运行数据库中，用于同会话上下文，不进入公开仓库。

## 验证与升级

隔离回归使用 Mock Provider、Fake OneBot 和测试时钟；包括真实 WS 入站、Agent、权限、定时调度、HTTP 出站、并发准入、取消、限额、重启、会话重置及记忆隔离。浏览器验证管理员接口与配置持久化，不代表真实 QQ 已验收。

V29 只增加主动任务日志，不改写现有 Prompt、记忆、身份、访问规则或 Ledger。恢复工具目标 Schema 同步为 29；升级前应使用旧版本恢复工具对停止写入的数据创建并验证 V28 备份。升级后使用新恢复工具创建 V29 备份。不要把 V29 数据库直接交给旧镜像来假装回滚。

如需回退，先停用主动开关；代码回退必须按既有离线恢复流程恢复匹配旧镜像的已验证升级前备份。保留原始卷和备份，不手工删除迁移记录，不以删表替代恢复。

本功能交付和隔离测试不授权操作真实生产、QQ 或模型服务。

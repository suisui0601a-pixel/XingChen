# 当前会话目标与工具发送回执

## 修复内容

模型请求现在显式携带服务端解析的 platform、conversationType、currentTargetKind 和 currentTargetId。Identity 的 actorId 是发言者，不是群聊的发送目标。历史中出现另一种聊天类型不改变当前会话。工具后的上下文重建保留同一份类型信息；与实际 actor 不一致时，在模型或工具执行前拒绝。

权限、工具 canonical names 和 OneBot 路由不变。不存在跨会话发送或读取的新授权。

普通回复优先使用最终文字，由 Runtime 投递。发送工具已经提交主要回复时，模型应以 NO_REPLY 结束，避免再生成“已发送/ACCEPTED”的聊天报告；只读工具后仍需要正常生成最终答复，有实际新内容的最终跟进仍可发送。这里是模型交付契约，不是对所有工具后的文字一律屏蔽，也不承诺模型永远不会重复措辞。

工具回执的 target 现在表示经过校验的实际目的地，kind/conversationType 表示其类型；成功发送返回的 messageId 单独表示提供方消息编号。不同文字/图片编号不能用来推断发往不同会话。网关原有 SendReceipt 和 Ledger providerMessageId 的存储语义保持不变。失败和 UNKNOWN 不补写为成功、不自动重试。

## 回归验证

ConversationPromptContextContractTest 验证群聊/私聊、相同目标值的类型隔离、转义、绑定校验、legacy 无类型上下文及预算计数。

MossReplyReliabilityTest 使用 MockModelProvider、FakeOneBot 和隔离 SQLite，验证工具前后模型都收到真实会话目标、越目标请求拒绝、工具已发送后 NO_REPLY 无第二条报告，以及既有读工具后最终文字、合法额外回复、重复入站与重启后 UNKNOWN 不重放。

StickerTurnLifecycleContractTest 验证工具结果的真实目的地与提供方消息编号分离；不改变表情的每回合限制、冷却或 UNKNOWN 处理。

## 后续持久化上下文修复

双向历史、当前输入去重、重启读取、reset 边界与出站证据已由后续修复补齐，详见 [双向持久化会话上下文](DURABLE_CONVERSATION_CONTEXT.md)。历史迁移和自动语义摘要不在其中；更换 Persona/Simulation 不会自动迁移旧服务记忆。

# 双向持久化会话上下文

普通回复不再依赖 ConversationWindow 的进程内发言列表。ConversationHistoryStore 读取已有入站记录和确认出站证据，按真实会话 UUID 隔离，取配置允许的最近消息（最多 100 条），交由 ContextBuilder 按 token 预算再次裁剪。

## 模型实际看到什么

- 历史用户发言使用 USER 角色，带转义的发言者标识、当时显示名、时间和平台消息标识。
- 本机器人确认发送成功的文字使用 ASSISTANT 角色；表情使用已发送资源的文字描述，不假装进行了图片理解。
- 当前入站不进入自动历史；只在最后一条当前 USER 消息出现一次。工具主动读取当前消息时，其结果仍可合法出现在 TOOL 消息中。
- 已经持久化但尚在当前事件之后的输入不提前泄入当前请求。notice、自身事件、迟到归档、空白和 reset 命令不作为普通聊天背景。
- 经过原有 Access Control 接受的普通群聊，即使未触发模型，也可成为下一次被唤醒时的背景。权限和唤醒概率不变。
- 每次工具轮次用重新裁剪后的上下文构建请求，再保留本轮 assistant tool_calls 和 TOOL 结果，避免预算已缩减而实际请求仍带整段旧历史。

历史只是数据，不定义当前人格、系统指令或工具权限。

## 出站证据与幂等

V31 给原 Outbound Ledger 增加 context_text / context_kind。在同一准入事务中保存有界的待发内容，仅当现有 Ledger 成为 SUCCESS 时才作为已发送历史读取。UNKNOWN、PENDING、FAILED 均不成为已确认的 ASSISTANT 回复；最近 UNKNOWN 以不含正文的诊断提示告知模型，禁止把“不确定”当作“没发送”而自动补发。

内容记账不执行网络操作。没有新消费者、重发队列或工具重放。重启后仍以原 execution key、generation、工具 claim 和 Outbound Ledger 为权威；恢复历史不能触发再次发送。

普通最终回复、qq.send、qq.reply、qq.sendMultiple 的各子消息及 sticker.send 均使用该证据路径。旧版已确认的最终文字可从原 journal 按已成功发送的分段只读恢复；旧版仅通过工具发送而未保存正文的消息不会凭空补写。没有足够证据的旧记录保持缺口。

## reset 与滚动

入站保存当时的 context_generation；读取使用持久化 reset 下界。协调式 reset 和旧 reset 路径均保留该下界，清除短上下文后不会因重启再次读回旧短历史。长期记忆、原始聊天记录和 Ledger 不因此删除。

滚动交接保存真实消息 ID 作为下一窗口起点，不以时间猜测、不伪造平台消息号。起点不存在时拒绝回退到更早历史。现有摘要仍是确定性摘录/交接，不宣称已经实现语义压缩或自动长期记忆提取。

## 长期记忆

长期记忆与聊天历史分开。已有 memory.remember / memory.update 继续经过 MemoryPolicy、当前身份和作用域校验；成功写入才可声称已保存。新增回归验证显式记忆写入后重启召回、跨群不可见，以及 reset 不删除长期记忆。

本修复不会把全部聊天自动当成事实，也不会迁移旧 DSH 数据。旧历史迁移需要可靠的 QQ 会话映射与单独审查，不按昵称或内容相似度合并。

## 升级与回滚

Schema 从 30 升至 31，增加上下文字段及查询索引；不修改 Persona、Simulation、Identity、Memory 正文、Access 或 OneBot 凭据。V1–V30 migration 保持原样。

升级前必须停 Core，用与旧 Schema 匹配的 recovery.py 创建并验证一致性完整备份，保留旧镜像、配置及旧恢复工具。升级后使用 Schema 31 的恢复工具进行新备份。

如需回滚，停止新 Core，以旧恢复工具将旧备份恢复到隔离目录并核验，再将匹配的旧数据和旧镜像一起恢复。禁止把 Schema 31 数据直接交给旧镜像；禁止只拷在线 SQLite 主文件，或为清空短上下文删除原始聊天与 Ledger。

## 验证范围

DurableConversationContextTest 覆盖私聊/群聊双向历史、当前输入单份、未唤醒背景、会话隔离、工具出站后重启、UNKNOWN、不重放、并发准入、窗口长度、未来事件、旧 journal 回退、角色不可伪造、30→31 数据保留和显式记忆召回。既有唤醒、WAIT、重启、表情、Access 及前端测试继续执行。

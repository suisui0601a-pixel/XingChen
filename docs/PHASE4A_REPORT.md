# XingChen Phase 4A — final closure and evidence seal

日期：2026-10-02。仅本地工作树、自动化测试与 loopback fake 范围；未连接 SSH、真实 QQ、SnowLuma、真实 DSH、DeepSeek API 或 Zetu。Phase 4B/4C 未开始。下方旧日期条目是历史快照，当前结论以 2026-10-02 seal 记录为准。

## 2026-10-02 — current final matrix audit

本节是当前状态，优先于下方保留的历史快照。此次从 `10c80f1` 开始；已先将压力/并发运行时修复保存为中间 checkpoint `fbb8e6c3f29b36f105f8eef8ada6d0a40f220b5e`（`fix: harden social runtime under concurrent stress`）。该提交不是 Phase 4A final commit。

- 已按顺序重新审计 `docs/LEGACY_FEATURE_MATRIX.md` 的 110 个 feature 行，检查 legacy 行为摘要、当前代码路径、行为闭环与特定自动化证据；把只有元数据的 OneBot image/forward/@mention 行降为 FOUNDATION，把 prompt diff 与贴图策略部分能力由 PLANNED 调整为 FOUNDATION，把经集成测试闭环的 owner-private `/reset` 调整为 TESTED。没有为变绿而新增产品功能。
- 最终计数由逐行解析器计算：34 TESTED、47 FOUNDATION、29 PLANNED、0 NEEDS_REVIEW，总计 110。每个 TESTED 行有测试引用；显式 `Class.method` 引用校验 66 项、无无效引用。`none` 表示缺少能证明该项用户行为的专项测试。矩阵证据索引列明 shorthand ID 对应方法。
- 六批审计：1–20、21–40、41–60、61–80、81–100、101–110；分层复核 21 行，其中 6 TESTED、10 FOUNDATION、5 PLANNED。行数、编号连续性、状态枚举和总和经程序校验。
- Phase 4A 必需能力没有发现 PLANNED 缺口：Identity/Relationship/Memory/Context/Prompt/Safe Agent、OneBot/模型/DSH 本地协议适配、reserved2 wake/sleep/WAIT/NO_REPLY、delivery certainty、restart recovery、reset、并发/压力与只读 diagnostics 均有相应实现及测试证据。矩阵里的未来 Console/voice/production/deployment 项保持 FOUNDATION 或 PLANNED，不阻止 Phase 4A acceptance。
- `Thread.sleep` 当前共 22 次：5 fake delay、16 个有 deadline 的条件轮询、1 cleanup retry；固定 correctness wait=0、unsafe wait=0。
- 最终第一轮 `clean test bootJar`：BUILD SUCCESSFUL；JUnit XML 汇总 256 tests、0 failures、0 errors、0 skipped（7 tasks executed）。
- 最终完整 `test bootJar --rerun-tasks` 验收通过：256 tests、0 failures、0 errors、0 skipped（6 tasks executed，非增量重跑）。其间一次同命令全量尝试曾有 `DshRc2AdapterContractTest.dsh022_followCompletionIsObservableAndDistinctFromDisconnect` 单项失败，错误为 fake WebSocket 关闭竞争下 `DSH remote.mux disconnected`；该项单独非增量复跑通过，随后整个全量非增量套件通过。此偶发记录保留；没有为此改产品代码/测试代码。R2STRESS、RESET、R2FINAL 与 integrated regression 均在最终两轮绿色 suite 中执行。
- Stress 记录：同会话 24 个顺序 mixed events（另包含一次 duplicate 与一次 late event），同会话 provider 最大并发 1、队列归零、duplicate turn/send/tool/memory/relationship side effect 为 0；跨会话测试为 5 conversations/30 events，阻塞模型/工具时仍有跨会话并行（provider max concurrency ≥2），最终各 lane/queue 归零、无死锁。
- R2FINAL-001..010：最新完整 256 项 suite 通过；覆盖 wake sources、WAIT/NO_REPLY、memory provenance/retrieval、identity/relationship restart 与后续 context。
- RESET-001..006：最新完整 suite 通过；active turn interrupt、generation fence、durable reset cleanup/restart、OUTPUT 与 TOOL side-effect fences、prompt active version continuation 均有本地自动化。Social wake scheduler callback：N/A（当前 runtime 图中不存在此 callback；reserved timer 仅变更 mode，不触发 Agent turn）。
- Thread.sleep：22 处 = 5 fake delays + 16 bounded predicate polling + 1 cleanup retry；correctness fixed wait=0，unsafe=0。
- 最新 bootJar loopback smoke：`/health`、`/api/status`、`/api/runtime` 均为 HTTP 200；`netstat` 仅见 `127.0.0.1:3200` LISTEN，runtime 报告 DSH/OneBot/model/social/live API 全关闭；敏感字段名检查为 0。Smoke PID 已退出，3200 LISTEN 已释放，独立临时 SQLite/log 目录已删除；`gradlew --stop` 确认无 daemon 运行。
- `git diff --check` 通过；最终工作树仅包含本阶段文档更新，无临时 audit CSV、smoke DB、日志或 secret。所有实现与测试变更属于 stress checkpoint `fbb8e6c3f29b36f105f8eef8ada6d0a40f220b5e`；最终 seal 只提交审计与状态文档。
- 最终范围检查：Phase 4A 必需能力无 PLANNED blocker；矩阵中的后续 Console、voice、production operations 等合法保留 FOUNDATION/PLANNED。没有启动真实连接或生产操作。**Phase 4A COMPLETE**；本轮不启动 Phase 4B/4C。

## Baseline and history

本轮起始基线为干净的 `1239d0e feat: complete durable social runtime recovery`。Phase 4A 历史提交链包括：

- `55bbf8b` — Phase 4 runtime foundations
- `8ee4068` — DSH RC.2 transport and runtime graphs
- `6462d31` — DSH RC.2 interaction runtime
- `02318e4` — reserved2 WAIT 与 outbound safety foundations
- `1239d0e` — durable social runtime recovery

这些为阶段中间提交，不代表本轮最终验收完成。本轮 final commit 尚未创建。

## 本轮已完成的有限改动

- reset 采用两阶段：lane 外 control interrupt 原子递增 generation、取消 active turn token、写入 durable `RESETTING`；同 conversation FIFO lane 执行 WAIT/transient cleanup、旧 DSH session stop/archive、mapping/pending interaction retire；启动时重排未完成 ticket。
- owner 私聊 `/reset` 才会创建 reset ticket；非 owner 或非私聊不执行 reset。旧 generation 的模型完成、tool proposal/execution、Memory write、journal output、WAIT 与 outbound callback 有 generation fence。
- `/api/runtime` 输出运行开关、活动/等待/排队计数、UNKNOWN outbound、interrupted/recoverable turn 数量；不输出 model 配置值、secret、cookie、token、Prompt 或 Memory 正文。
- 增加 reset generation/WAIT、持久 ticket/启动恢复、active model cancellation 和 diagnostics 契约断言；定向 `ConversationResetInterruptTest`、`SqliteStartupContractTest`、`ConsoleReadApiContractTest` 成功。
- 增加 dispatcher 层确定性并发测试：同一会话 24 项严格 FIFO/最大并发为 1；会话 A 被 barrier 阻塞时会话 B 可先完成。该测试不覆盖完整混合 OneBot 事件语义。
- 更新架构、运行时、上下文生命周期和 feature matrix 说明。

## 未满足的 Phase 4A Final Gate

以下仍未完成，因此不得标记 `Phase 4A COMPLETE`，不得创建最终提交：

- 24+ 混合事件同会话压力测试及 5 会话压力测试尚未建立。
- A 阻塞、B 完成的跨会话 barrier 并行性测试尚未建立。
- R2STRESS-001 的重复发送/Memory/Relationship/Tool side effect、排序、最终 drain、无死锁等完整断言未完成。
- RESET-002 的 AgentExecutor barrier 单元测试已覆盖“模型在途、reset 后旧文本与 tool proposal 均不产生工具副作用”；但完整 SocialRuntime → persistence → reset event → same-lane cleanup 的端到端竞态、RESET-002B 已 durable output 状态、Relationship/scheduler/continuation 全回调矩阵仍未完成。
- RESET-001..006 全部状态矩阵、闭合 Agent 的 Question/Approval 旧 session 状态验证尚未完成。
- R2FINAL-001..010 和 Memory/Identity 全部最终 integrated regressions 尚未建立。
- Feature matrix 虽保留 110 行并调整了已确认陈旧项，但本轮没有逐项重新核对全部 110 项证据，当前汇总不能作为最终重算结果。
- 最新改动后的第一轮 `clean test bootJar` 曾发现 4 项重启回归失败，原因是注册 active turn 使用了 read-cursor 更新前的旧 generation；已改为重新读取持久 generation，并且 `SocialRuntimeWaitRestartE2ETest` 9 项通过。该修复后的两轮全量验证及 smoke 尚未重跑。

## 验证记录

- 本轮定向测试：`ConversationResetInterruptTest`、`SqliteStartupContractTest`、`ConsoleReadApiContractTest` 成功；generation 修复后的 `SocialRuntimeWaitRestartE2ETest` 9 项通过。
- 曾有一次全量测试因新注册的 `@Transactional` reset service 为 `final` 而失败；已移除 `final` 并完成定向回归。此失败记录保留，不抹去。
- 第一轮最终构建：clean test bootJar 成功；241 tests、0 failures、0 errors、0 skipped。
- 第二轮非增量构建：test bootJar --rerun-tasks 成功；241 tests、0 failures、0 errors、0 skipped。
- `git diff --check` 通过。
- 本地 loopback smoke：只监听 `127.0.0.1:3200`，外部集成开关关闭；`/health`、`/api/status`、`/api/runtime` 均 HTTP 200。`/api/runtime` 返回 active/waiting/queued/unknown/interrupted 计数为 0，未发现敏感字段。Smoke JVM 停止，3200 已释放，临时 SQLite 与日志已清理。
- 检查点 `18082a7` 之后的最新工作树另行执行：修复 generation 快照后 `clean test bootJar` 成功，243 tests、0 failures/errors/skipped；`test bootJar --rerun-tasks` 再次全量执行成功，243 tests、0 failures/errors/skipped。最终 smoke 三个端点均 HTTP 200，`/api/runtime` 显示 loopback 与所有外部集成关闭；JVM 已退出、3200 已释放、临时数据库/日志已清理，`gradlew --stop` 停止 1 个 daemon。
- 最新 `git diff --check` 通过；Git 仅提示工作文件 LF 将在后续 Git 写入时转换为 CRLF，不是 whitespace error。
- 上述验证不能替代尚未建立的压力、并行和 reset race 测试；当前不创建最终 Git commit。

## 当前状态

Phase 4A-2 和 Phase 4A-3a 的历史基线保持原有状态；Phase 4A-3b final closure 尚未完成。下一步仅应补足上列本地自动化门槛并重新执行验证，不进入 Phase 4B，也不触碰任何外部或生产系统。

## 2026-10-02 — Phase 4A-3b-2 final closure continuation

本节优先于上文 2026-10-01 的历史快照。起始 HEAD 为 `10c80f1 test: complete final social runtime integration evidence`，起始工作树干净。当前工作树含有未提交修改；本轮尚未创建 final commit，Phase 4A 仍 **未完成**。

### 本轮压力与回归证据

- `SocialRuntimeStressE2ETest.r2stress001_sameConversationTwentyFourMixedEventsDrainExactlyOnce` 已通过完整 Fake OneBot → normalize → SQLite/identity → SocialRuntime → wake/ContextBuilder/Agent → tool/outbound → Fake OneBot 路径。覆盖 24 个递增唯一输入、一个 duplicate 传递、一个 late 入站；断言 FIFO 持久顺序、late 不调用模型、duplicate 无第二 logical turn、最大并发 provider call 为 1、Tool/Memory/Relationship/OneBot side-effect 各一次、NO_REPLY 不额外发包、WAIT continuation 一次、cursor/generation 单调、simulation idle、队列归零。
- `SocialRuntimeStressE2ETest.r2stress002_003_fiveConversationRuntimeParallelismWithBlockedModelAndTool` 已通过。5 个 conversation、每个 6 个事件，共 30 个事件；A 的模型 barrier 与 B 的工具 barrier 同时保持时，其余会话可完成；B 在 A 释放前完成。各 conversation 内 FIFO，模型调用最大并行数至少 2，无等待会话、无重复 turn/发送/tool/memory 副作用，最终所有 lane/queue 清零。
- 压测首次暴露 SQLite WAL 的 deferred transaction read→write snapshot upgrade 在并发会话下出现 `SQLITE_BUSY`。最小修正为 `SqlitePragmaInitializer` 使用 `TransactionMode.IMMEDIATE`，在事务首次读取前争用单写者锁；长耗时模型/工具不在数据库事务内，conversation worker 仍可并行。`SqliteStartupContractTest` 全类及完整回归已通过。
- 压测也证明 OneBot 归一化事件自身不携带可信 owner 标记，导致 runtime `/wake` 看不到身份仓储中的 owner。`SocialRuntime` 现在仅为 wake-policy 评估构造带解析后 self/owner flag 的事件视图；持久化原始事件不变。E2E 对管理员 owner snapshot 和 `manual-wake` reason 有断言。
- 全量 `test` 回归：256 tests，0 failures，0 errors，0 skipped（本轮当前源码版本）。定向 stress 两用例均通过。
- 最终 `Thread.sleep` 扫描共 22 次：5 个 fake delay（Fake DSH 3、Fake OneBot 1、DeepSeek fake 1），16 个带 deadline 且断言谓词的 bounded poll，1 个 SQLite 临时文件 cleanup retry；correctness-dependent fixed wait=0，unsafe=0。本轮 stress helper 只加入一个有界条件轮询。

### 仍未满足的 final gates

- 已对矩阵抽样 10 行（#1/#20/#31/#38/#43/#58/#67/#84/#107，加 5 项计划功能）复核实现/测试/状态；但 110/110 全行逐项 evidence remap 尚未完成。矩阵 syntax count 为 37 TESTED / 23 FOUNDATION / 50 PLANNED / 0 NEEDS_REVIEW（总和 110），仍标为 provisional，不能作为逐行审计完成证明。
- `ARCHITECTURE.md`、`AGENT_RUNTIME.md`、`CONTEXT_LIFECYCLE.md` 已补上本轮 concurrency/identity/stress 状态。R2FINAL/RESET 既有自动化用例包含在下列 256 项完整测试中并全绿。
- 最终源码修正后的 `clean test bootJar`：BUILD SUCCESSFUL；256 tests，0 failures/errors/skipped，7 tasks executed。
- 同一最终源码修正后的 `test bootJar --rerun-tasks`：BUILD SUCCESSFUL；256 tests，0 failures/errors/skipped，6 tasks executed，无 `UP-TO-DATE`。之前一次 rerun 暴露的 WAIT test observation race 已改为等待 `COMPLETED`，上述两轮均包含修正。
- 最新 loopback smoke 使用本轮 bootJar、独立临时 SQLite、绑定 `127.0.0.1:3200`，显式关闭 DSH/OneBot/model/social/live API。`/health`、`/api/status`、`/api/runtime` 均 HTTP 200；运行状态显示外部连接关闭且响应字段无 secret/token/cookie/API key/prompt/memory。PID 11580 已退出，3200 已无 LISTENING，临时数据库目录已删除，`gradlew --stop` 停止 1 个 daemon。
- 最新 `git diff --check` 通过；已读取 `git status --short`、`git diff --stat`、`git diff --name-only`、`git log --oneline -15`。仅有本轮 10 个 tracked 文件修改和新 stress 测试；未创建 final commit。110 行逐项证据审计仍未完成，故 Phase 4A gate 仍未满足。

因此本次不写 **Phase 4A COMPLETE**，不创建最终提交，不进入 Phase 4B/4C。外部集成、真实 QQ/DSH/DeepSeek、Zetu、SSH/Docker 均未接触。

## 2026-10-01 — Phase 4A-3b-1 reset/evidence closure 增补

本增补对应实际基线 `9d6c0ed feat: interrupt active turns on conversation reset`（起始工作区 clean）。本轮新增 SocialRuntime 级阻塞模型/reset 竞态测试，并修复该测试发现的旧 turn 状态覆盖问题。它**不代表**下列 completion gate 全部完成；不得据此标记 4A-3b-1 COMPLETE 或创建 checkpoint commit。

### 本轮可定位的证据

| Gate | 证据 | 状态/边界 |
|---|---|---|
| RESET-001 generation N→N+1 / stale callback | `SocialRuntimeWaitRestartE2ETest.resetControlInterruptsActiveSocialRuntimeModelBeforeRelease` | 部分：验证旧模型结果被丢弃及无 side effect；未注入旧 WAIT continuation 与 scheduler callback。 |
| RESET-002 SocialRuntime active-model race | 同上 | 已验证：Fake OneBot→正式 `SocialRuntime`→阻塞 provider；reset control 在 barrier 释放前完成；旧输出无 outbound/tool claim/Memory/Relationship；新代消息随后成功。 |
| RESET-003 WAIT retire / stale continuation | `SqliteStartupContractTest.resetAdvancesGenerationRetiresWaitAndPreservesWakePolicy`；`SocialRuntimeWaitRestartE2ETest.waitPersistsAcrossRealContextRestartAndNextFakeOneBotEventContinuesOnce` | 部分：分别覆盖 reset retire 与正常重启续接；缺少 reset 后手动触发旧 continuation/scheduler 的一体化断言。 |
| RESET-004 UNKNOWN reset 保留事实 | `SocialRuntimeWaitRestartE2ETest.unknownOutboundSurvivesRestartWithoutReplayingTurnOrResending`；`SqliteStartupContractTest.outboundLedgerIsStableAndUnconfirmedDeliveryCannotBeRetried` | 部分：UNKNOWN/restart/fail-closed 已覆盖；未证明 reset 前后 UNKNOWN 保持且无重新 reserve/dispatch。 |
| RESET-005 identity/alias/membership/relationship/memory + 下轮 ContextBuilder | `SqliteStartupContractTest.reset101_shortContextResetRetiresSessionButPreservesLongTermMemory`；`IdentityResolverContractTest.id103_renameUpdatesDisplayAndRetainsAliases`；`id102_samePersonGetsDifferentMembershipsAcrossGroups`；`rel101_relationshipAddressIsAttachedToIdentityAndAppearsInActorContext` | 部分：基础证据分开存在；缺少 reset 后正式 SocialRuntime turn 的 ContextBuilder 联合断言。 |
| RESET-006 prompt versions/history reset 不变 | `ContextPromptSecurityContractTest` 版本/rollback 契约；`ContextPipelineContractTest.ctx101_runtimePromptEngineKeepsLayerVersionsAndTextSeparate` | 未闭环：没有 reset 前后 active versions/history 不变及下一 ContextBuilder 使用同版本的集成断言。 |
| RESET-OUTPUT-001 durable output 后、dispatch 前 reset | — | 未建立 outbound dispatch barrier 集成测试。 |
| TOOL_PROPOSED 后 reset | — | 未建立 proposal 已产生但 execute 尚未开始时 reset 的测试；本轮阻塞模型返回前的测试不能替代该阶段。 |
| TOOL_EXECUTED 后 reset | `SocialRuntimeWaitRestartE2ETest.toolEffectClaimedBeforeCrashIsFailClosedWithoutWholeTurnReplay` | 有 claim 后 crash/restart 的部分回归；没有 reset-specific execution-count=1。 |
| Memory/Relationship stale callback | `SocialRuntimeWaitRestartE2ETest.resetControlInterruptsActiveSocialRuntimeModelBeforeRelease` | 已验证阻塞旧模型所携带的 stale Memory 与 `memory.setAddress` 未落库；未覆盖 reset 完成后独立延迟 callback。 |
| scheduler stale callback | — | 未建立 controllable scheduler + reset 后手动触发旧任务测试。 |
| R2FINAL-001..010 | 本报告下方摘要 | 仅局部旧用例可映射；没有逐项完整 gate 证据。 |
| Identity final evidence | `IdentityResolverContractTest.id103_renameUpdatesDisplayAndRetainsAliases`、`id102_samePersonGetsDifferentMembershipsAcrossGroups` | 部分：未覆盖题述稳定 ID、昵称两次变化、群名片变化的 SQLite final integration 组合。 |
| Memory isolation final evidence | `IdentityRelationshipMemoryContractTest.mem003_conversationMemoryIsInvisibleInAnotherGroup`、`mem004_personMemoryVisibleOnlyToSubjectOrOwner`、`rel003_conversationTermDoesNotLeakToAnotherGroup` | repository/policy 契约存在；缺少 Person A/B 的最终 ContextBuilder 输入和 PROJECT scope 整合证明。 |

R2FINAL 子项核对：001 sleeping ordinary message 仅有部分 cursor/sleep 持久化；002 sleeping mention wake→reply 未映射；003 poke 未映射；004 reply-to-bot 未映射；005 configured speaker wake 未映射；006 NO_REPLY/cursor 部分见 `sleepingAndNoReplyCursorSurviveContextRestarts`，但 duplicate 惰性断言仍用固定 sleep；007 WAIT restart 有 `waitPersistsAcrossRealContextRestartAndNextFakeOneBotEventContinuesOnce`；008 UNKNOWN restart 有 `unknownOutboundSurvivesRestartWithoutReplayingTurnOrResending`；009 正式 remember tool→policy/provenance→下轮 ContextBuilder retrieval 未映射；010 Relationship term 在重启后的下一正式 ContextBuilder 输入未映射。

### Thread.sleep 清点

`rg -n 'Thread\\.sleep\\(' src/test/java` 本次匹配 30 处。分类如下（分类不等同于已消除）：

- 测试假服务传输延迟：`FakeDshRc2Server`、`FakeOneBotServer`、`DeepSeekProviderContractTest`；不作为完成判定。
- 有 deadline 的条件轮询：fake WS/request/持久状态等待及 `await(...)` helpers；条件会被断言，属于有界等待，但轮询仍使用 `Thread.sleep`。
- correctness 依赖需替换：`SocialRuntimeWaitRestartE2ETest` 重启后固定 300 ms、重复事件后固定 250 ms；`SqliteStartupContractTest` UNKNOWN 后固定 1200 ms、foreign/malformed 输入后固定 500 ms；`DshPendingInteractionRestartTest` expiry 固定 2100 ms；`OneBotV11GatewayContractTest` disconnect 和发送事件前固定等待。尚未全部替换为 latch/barrier/clock/scheduler，故 Thread.sleep audit 未通过。

### 本轮验证边界

- 已执行 `SocialRuntimeWaitRestartE2ETest` 整类：9 tests，BUILD SUCCESSFUL。新测试时序为 `MODEL_STARTED`、`RESET_REQUESTED`、`GENERATION_INVALIDATED`、`CANCEL_SIGNALLED`、`RESET_CONTROL_COMPLETED`、`MODEL_RELEASED`、`OLD_MODEL_RETURNED`、`OLD_RESULT_DROPPED`。
- 本轮改动 `SocialRuntime` runtime wiring 后，两轮完整验证均已重新执行：`clean test bootJar` 与 `test bootJar --rerun-tasks` 都成功，分别为 244 tests、0 failures、0 errors、0 skipped；`git diff --check` 通过（仅有 CRLF 转换提示）。
- 本轮 loopback smoke 在本地 JVM、临时 SQLite、`127.0.0.1:3200` 执行；外部 DSH/OneBot/模型和 live API 均关闭。`/health`、`/api/status`、`/api/runtime` 均 HTTP 200，runtime 回报 loopback、3200、外部 adapters false。JVM 已停止、临时数据/日志已清除，确认 3200 listener 已释放。
- 当前工作树中三个 Java 文件有未提交修改；不创建 Phase 4A-3b-1 checkpoint。未触碰任何禁止的外部系统。

## 2026-10-01 — Phase 4A-3b-1a 增补

### 中间 checkpoint

- 已将此前验证过的生产逻辑修复、阻塞竞态测试和上轮证据报告提交为 `72e400a fix: preserve reset-cancelled turn state`。这是用户指定的 intermediate checkpoint，不是 Phase 4A 完成提交。
- checkpoint 后工作区重新开始本轮测试改动；本轮尚未提交。

### 本轮新增/复核证据

- `SqliteStartupContractTest.resetWaitStale001_oldContinuationAfterResetCleanupCannotReviveWait`：创建 durable WAIT，走 reset interrupt → generation N+1 → cleanup，之后显式调用旧 generation 的 continuation CAS；确认拒绝、状态/游标保持 reset 后值、无 outbound。它验证 service/repository fencing，不是 SocialRuntime model-count 级测试。
- `SocialRuntimeWaitRestartE2ETest.reset006_activePromptSectionsRemainInNextSocialRuntimeContext`：通过正式 SocialRuntime，配置 active persona/simulation prompt version 2，跑 reset 前和 reset 后 turn，确认后续 ModelRequest 的 ContextBuilder 内容仍使用相同的 active prompt 文本/版本。
- Prompt history/rollback 元数据在当前运行图中没有与 `PromptSections` 绑定的持久化 repository；本测试不能证明未实现的历史记录保留，RESET-006 仍部分未闭环。
- 对 scheduler 边界做了实现核对：`SimulationStateService` 没有安排 scheduler wake callback；WAIT 仅由之后的入站事件通过 `claimWait(conversation,generation)` 恢复。已接线的 `ReservedModeMachine` timer 只将全局 mode 转为 IDLE，不会发起 turn 或 wake；`ReservedV1Machine` 没有接入 `SocialRuntime`，且不是 reset ticket 的 generation。故不存在可以诚实地在当前 runtime 中触发的“旧 generation scheduler wake”。本轮不造假的测试替代生产 callback。RESET-SCHED-001 仍未满足，需要先有明确接入当前运行图的 scheduler wake 语义。
- 已移除 reset/restart 用例中的两个固定 300 ms 等待，改为等待 durable `STALE`/`INTERRUPTED`；三处重复事件固定 250 ms 改为同会话 sentinel 事件并等待 cursor 前进；OneBot `waitForMessages` 用例不再预睡；disconnect 用例删除固定 150 ms 等待；两个 UNKNOWN 固定 1200 ms 静默等待已删除；expiry 用例不再真实等待 2.1 秒，而是确认 restart 保持原 deadline 后立即测试 due-expiry 转移。
- 两个 DSH invalid-input 用例的 500 ms 等待分别改为有界状态条件：foreign session 等待 event stream 重连证明帧已投递处理；malformed waterfall 等待订阅进入关闭状态。
- 受影响测试定向运行成功：SocialRuntime reset/wait/restart 类、旧 WAIT CAS、DSH foreign/malformed 两用例、interaction expiry restart、OneBot gateway contract。

### Thread.sleep 全量结果

当前扫描：`rg -o 'Thread\\.sleep\\(' src/test/java` 为 20 次调用。

- A 模拟假组件延迟：5 次，位于 `FakeDshRc2Server`（3）、`FakeOneBotServer`（1）、`DeepSeekProviderContractTest`（1）。这些 sleep 是被测 slow/timeout 输入本身，不用来判定测试完成。
- B 有界条件轮询：15 次，位于 `DshRc2AdapterContractTest`、`SqliteStartupContractTest`、`OneBotV11GatewayContractTest`、`DshPendingInteractionRestartTest`、`SocialRuntimeWaitRestartE2ETest`、`TestSqliteDatabase`。均有显式谓词与 deadline/有限重试，并最终断言条件；轮询 sleep 只控制采样间隔。
- C 固定等待后直接断言：0 次。
- 本轮从 checkpoint 以来替换/移除 correctness-dependent 固定等待 11 处；remaining correctness-dependent fixed sleep = 0。

### 尚未满足的 4A-3b-1a gate

- RESET-WAIT-STALE-001 当前是持久状态/CAS 测试，尚非 SocialRuntime 中无模型调用/无新 turn 的完整 E2E。
- RESET-SCHED-001：已按最终规则分类为 NOT APPLICABLE，理由及 future invariant 见本报告纠正段及 `docs/AGENT_RUNTIME.md`。
- RESET-OUTPUT-001、RESET-TOOL-001、RESET-TOOL-002 与 RESET-006 closure evidence 已在下方 2026-10-01 Final Closure Pass 增补。
- 下方最终验收记录优先于本节早期的“尚未满足”历史快照；历史快照保留仅用于记录上一工作阶段状态。

### 纠正：scheduler gate 分类

- 按本阶段最终 closure 指令，RESET-SCHED-001 分类为 **NOT APPLICABLE**，不是未完成 gate：当前 SocialRuntime 没有 scheduled wake callback。WAIT 只由后续入站事件按持久化 generation CAS 恢复；reserved-mode timer 只切换 mode，不会启动 social turn。故本次不构造与生产运行图无关的模拟 callback。
- 若未来增加 scheduler wake，必须把创建时的 origin generation 随 callback 保存，并在 callback 执行时重新校验 generation；不能依赖调度时校验或只检查本地取消标记。
- 本报告前文提到 scheduler 测试缺口的历史记录，以上述当前分类为准；这不解除 RESET-OUTPUT-001、RESET-TOOL-001、TOOL_EXECUTED reset-specific 与 RESET-006 history/rollback 等实际未闭环项。

## 2026-10-01 — Phase 4A-3b-1a Final Closure Pass

### Deterministic reset side-effect evidence

- 新增 `TurnBoundaryObserver`，生产默认实现是 no-op；只在三个真实执行边界提供观察点，不替代 token fence、durable journal、tool ledger、tool executor 或 OneBot transport。测试用 one-shot latch 注入 reset，不通过伪造 send 失败模拟。
- RESET-OUTPUT-001：`resetOutput001_durableTextBeforeDispatchIsSuppressedAfterReset` 经过 Fake OneBot → SocialRuntime → MockModelProvider → durable journal。barrier 到达时已确认 `OUTPUT_EMITTED` 和序列化旧文本，Fake OneBot send count=0；reset generation 前进后才放开 barrier。最终由独立 observer 计得 outbound dispatch invocation=0，Fake HTTP send=0，outbound execution 行为 0，model requests=1，旧 turn 为 `STALE`/`INTERRUPTED`，inbound 未被标记 `COMPLETED`。
- RESET-TOOL-001：`resetTool001_parsedProposalCannotClaimOrExecuteAfterReset` 在解析完整的 `memory.setAddress` proposal 后、generation-fenced ledger claim 前暂停。reset generation 前进并释放后，tool claim=0、Relationship/Memory 副作用=0、OneBot send=0，旧 turn stale/interrupted，inbound 非 `COMPLETED`，模型只调用一次。
- RESET-TOOL-002：`resetTool002_executedEffectIsNotReplayedAndOldReplyIsSuppressed` 在真实 `memory.setAddress` 成功且 durable tool state 已到 `TOOL_EXECUTED` 后暂停。reset 后 effect 记录保持恰好 1 条、tool claim 恰好 1 条、provider request 仍为 1、Fake OneBot send=0；没有 whole-turn replay/旧 generation 回复，old turn stale/interrupted 且 inbound 非 `COMPLETED`。
- 上述三条均覆盖 reset-cancelled turn 不会被外层完成路径覆盖为 `COMPLETED`；reset cleanup 在 barrier 释放后按同会话 lane 完成。

### RESET-006 Prompt history / rollback

- `reset006_activePromptHistorySurvivesAndRollbackBuildsV2Context` 以 Persona v1/v2/v3、Simulation s1/s2/s3 建立 SQLite prompt profile/history；reset 前后版本 id、layer、version、checksum 与历史顺序完全一致，profile 当前文本和活动 `PromptSections` 仍为 v3/s3；reset 后真实 SocialRuntime 的下一次 ModelRequest 继续使用 v3/s3。
- 随后对两层分别执行正常 rollback v3→v2。回滚创建新的 v4 历史版本，旧版本保留；`PromptEngine` 生成 rollback 后 sections，下一次 `ContextBuilder` 构建内容为 Persona v2 / Simulation s2。
- 当前正式 SocialRuntime 的 prompt sections 仍由启动配置装配；该测试验证 reset 不改其 active selection、history rows 不被 reset 清除，并验证 PromptService 的 rollback 与 ContextBuilder 消费，不声称 live runtime 在不重载配置时动态切换 prompt。

### Final validation

- Intermediate evidence commit：`9ffabaa test: add reset wait and prompt persistence evidence`。
- 第一轮 `clean test bootJar`：249 tests，0 failures、0 errors、0 skipped；`bootJar` 成功。
- 第二轮 `test bootJar --rerun-tasks`：249 tests，0 failures、0 errors、0 skipped；所有任务实际重跑，无依赖 `UP-TO-DATE`。
- `git diff --check` 通过。Thread.sleep 全量扫描 20 处：模拟假组件延迟 5 处、有界条件轮询 15 处、固定等待 correctness 依赖 0、remaining unsafe 0。
- 因 production composition 增加了默认 no-op observer，已重新做 loopback smoke：JDK 21、本机临时 SQLite，只监听 `127.0.0.1:3200`，`/health`、`/api/status`、`/api/runtime` 均 HTTP 200；runtime 显示 DSH/OneBot/model/live API 全关闭。JVM 停止、临时目录清理、3200 listener=0。
- 未 SSH、未连接真实 QQ/SnowLuma/DSH/DeepSeek，未触碰 Zetu。未开始 R2FINAL、stress、Feature Matrix、Phase 4B。
- 最终 closure commit：`test: close reset side-effect race evidence`。该 commit 仅代表 Phase 4A-3b-1a checkpoint，不代表 Phase 4A completion。

## 2026-10-02 — Phase 4A-3b-1b Final Social Runtime / Identity / Memory Evidence

- Thread.sleep 分类精确计数：21 处中，5 处是假组件延迟，15 处是有 deadline 的条件轮询，1 处是 `TestSqliteDatabase` 临时 SQLite 文件清理的有限重试；固定等待后直接断言为 0。

本节针对基线 `e30af6d test: close reset side-effect race evidence`，只补测试与证据映射；不包含生产运行时代码更改。本阶段仅在满足全部指定 gate 与两轮验证后提交 4A-3b-1b checkpoint，不等于 Phase 4A 最终完成。

### R2FINAL 与相关集成证据映射

| Gate | 精确证据 | 结论边界 |
|---|---|---|
| R2FINAL-001 sleeping ordinary | `SocialRuntimeWaitRestartE2ETest.sleepingAndNoReplyCursorSurviveContextRestarts` | Fake OneBot 普通群消息经过真实 SocialRuntime；inbound 持久化并完成，睡眠保持，模型调用与 outbound 均为 0，durable turn 为 `NOT_STARTED`（不是模型执行 turn），cursor 前进；重启后状态恢复。 |
| R2FINAL-002 mention wake + reply | `SocialRuntimeFinalEvidenceE2ETest.r2final002_003_004_005_wakeSourcesResolveOneTurnAndSpeakerUsesStableId` | 同一事件同时含 mention/question；wake reason 同时记录但只产生一个 durable turn、一次模型请求与一次 SUCCESS send。 |
| R2FINAL-003 poke | 同上 | OneBot poke notice 经 normalize/persist/resolve/wake；reason 含 `poke`，一个模型调用，NO_REPLY 情况零 send、无重复 turn。 |
| R2FINAL-004 reply-to-bot | 同上 | 睡眠会话收到引用 bot 的消息后 reason 含 `reply-to-bot`，只跑一轮并成功发送一条回复。 |
| R2FINAL-005 configured speaker | 同上 | speaker X 按稳定 platform user ID 唤醒；昵称恰好等于 speaker ID 的 Y 不唤醒。两条消息都只有 `NOT_STARTED` durable 占位、无模型请求或 outbound。 |
| R2FINAL-006 NO_REPLY / duplicate restart | `SocialRuntimeWaitRestartE2ETest.sleepingAndNoReplyCursorSurviveContextRestarts` | NO_REPLY durable terminal、inbound 完成、cursor 消耗、无 outbound record；同一 inbound 重启后不重跑模型。 |
| R2FINAL-007 WAIT restart | `SocialRuntimeWaitRestartE2ETest.waitPersistsAcrossRealContextRestartAndNextFakeOneBotEventContinuesOnce` | 同 SQLite 中恢复 WAIT；event 2 只续接一次，wake reason 含 `WAIT_CONTINUATION`，origin 不重放；续接仍经过正常 ContextBuilder memory scope 检查。 |
| R2FINAL-008 UNKNOWN restart | `SocialRuntimeWaitRestartE2ETest.unknownOutboundSurvivesRestartWithoutReplayingTurnOrResending` | Fake OneBot 已应用发送但响应超时；restart/重复 inbound 不重新模型、reserve 或 dispatch，UNKNOWN 保持，应用发送总数为 1。 |
| R2FINAL-009 memory write → retrieval | `SocialRuntimeFinalEvidenceE2ETest.r2final006_009_021_memoryToolNoReplyPersistsProvenanceAndNextTurnRetrievesAfterRestart` | 正式 Agent tool `memory.remember` 写 PERSON_GLOBAL memory 后返回 NO_REPLY；SQLite tool claim、source actor/conversation/message/timestamp 与单条记忆得到断言；重启重复源事件不重写；下一真实 SocialRuntime turn 的 ModelRequest ContextPackage 含该 memory。 |
| R2FINAL-010 relationship restart | `SocialRuntimeFinalEvidenceE2ETest.identFinal001_and_r2final010_personAliasesMembershipAndRelationshipSurviveRestart` | SQLite term “姐姐”在 group A 两次 nickname/card 更新时优先于全局“朋友”；group B/private 使用全局项；关闭并重开 Spring Context 后 group A 下一 ContextPackage 仍为“姐姐”，stable person ID 不变。 |
| IDENT-FINAL-001 / membership / alias | 同上 | 真实 Fake OneBot→SocialRuntime→SQLite 路径覆盖 QQ `123456` 的 Alice→Alicia、group A card 小A→小A2、group B card B卡、private display；Person 行数为 1、stable UUID 不变、alias history 保留、membership 按两群和 private conversation 分开，group membership 仅两条。 |
| Relationship scope / priority | 上述集成方法；`IdentityRelationshipMemoryContractTest.rel003_conversationTermDoesNotLeakToAnotherGroup`、`rel004_globalTermAppliesAcrossConversations`、`rel005_localExplicitAddressWinsGlobalExplicitAndLocalInferredCandidates` | 既验证实际 SQLite/ContextBuilder 的 conversation-vs-global 地址，也验证其它群不泄漏；纯域契约再验证 local explicit 排在 global explicit 与 local inferred 前。 |
| MEMISO-FINAL-001 PRIVATE | `IdentityRelationshipMemoryContractTest.mem005_006_007_contextBuilderOnlyReceivesScopeAuthorizedMemory` | Person B 的 ContextPackage 不含 Person A PRIVATE/PERSON_GLOBAL memory；owner-private 可读按现行 `MemoryVisibility` 规则单独确认。 |
| MEMISO-FINAL-002 CONVERSATION | 同上 | Conversation B 的 ContextPackage 不含仅 Conversation A 可见的记忆。 |
| MEMISO-FINAL-003 PROJECT | 同上 | Project X context 可见 X；Project Y 不可见 X。 |
| OWNER_GLOBAL / PERSON_GLOBAL | 同上 | 普通 MEMBER 不见 OWNER_GLOBAL；owner 可见；PERSON_GLOBAL 只对 subject stable person 可见，owner 身份本身不扩大读取范围。 |
| WAIT memory isolation | `SocialRuntimeWaitRestartE2ETest.waitPersistsAcrossRealContextRestartAndNextFakeOneBotEventContinuesOnce` | WAIT 前写入 unrelated PROJECT scope memory；restart 后续接 ContextPackage 仍不包含该项，但正常 PERSON_GLOBAL memory 与 relationship term 仍在。 |
| Context envelope / hard security | `SocialRuntimeFinalEvidenceE2ETest.r2final002_003_004_005_wakeSourcesResolveOneTurnAndSpeakerUsesStableId`；`SocialRuntimeFinalEvidenceE2ETest.identFinal001_and_r2final010_personAliasesMembershipAndRelationshipSurviveRestart`；`SocialRuntimeFinalEvidenceE2ETest.r2final006_009_021_memoryToolNoReplyPersistsProvenanceAndNextTurnRetrievesAfterRestart`；`AgentExecutorContractTest.audit_hardSecurityPolicyIsFirstTrustedSystemMessage` | 正式 wiring 的 ModelRequest 断言 simulation/persona、actor/display、conversation、current/recent text、relationship、retrieved memory、wake reason；另有既有 hard policy 首条 trusted system message 回归。 |

### 阶段范围与验证

- 本次只修改上述测试文件和本报告；未改 `src/main` 生产运行时代码。之前 smoke 继续适用（previous smoke remains applicable）。
- 未做 24+ mixed stress、5-conversation stress、110-row feature matrix audit；未开始 Phase 4A final closure 或 Phase 4B；未 SSH、未接真实 QQ/SnowLuma/DSH/DeepSeek，未改 Zetu。
- `Thread.sleep` 扫描：21 处；5 处属于 Fake DSH/OneBot/DeepSeek 可控延迟，16 处为带 deadline 且最终断言谓词的条件轮询；本次无新增固定等待后直接断言。逐条命中见 `rg -n 'Thread\.sleep\(' src/test/java`。
- 第一轮 `clean test bootJar`：BUILD SUCCESSFUL；254 tests，0 failures、0 errors、0 skipped；7 tasks executed。
- 第一轮后 `git diff --check`：通过；仅有 Git 的 LF→CRLF 提示。
- 第二轮 `test bootJar --rerun-tasks`：BUILD SUCCESSFUL；254 tests，0 failures、0 errors、0 skipped；6 tasks executed，无 `UP-TO-DATE`。
- `git diff --check`：定向测试后已通过；只有 Git 的 LF→CRLF 提示。
- 最终 checkpoint commit：`test: complete final social runtime integration evidence`；仅代表 Phase 4A-3b-1b COMPLETE，不代表 Phase 4A COMPLETE。
